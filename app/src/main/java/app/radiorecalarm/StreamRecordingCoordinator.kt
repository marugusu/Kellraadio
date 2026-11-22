package app.radiorecalarm

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.atomic.AtomicInteger

fun interface MetadataFetcher {
    suspend fun fetch(artist: String, title: String): SongAdditionalInfo?
}

fun interface CoverArtDownloader {
    suspend fun download(url: String, targetFile: File): Boolean
}

@Serializable
data class SidecarMetadata(
    val title: String,
    val artist: String,
    val album: String? = null,
    val year: String? = null,
    val genre: String? = null,
    val trackNumber: Int? = null,
    val lyrics: String? = null,
    val station: String,
    val stationUrl: String,
    val recordedAt: String,
    val durationSeconds: Long,
    val fileSize: Long,
    val codec: String,
    val bitrate: String? = null,
    val audioFile: String,
    val coverArtFile: String? = null,
    val partial: Boolean = false
)

fun interface StorageSpaceChecker {
    fun getAvailableBytes(dir: File): Long
}

class AndroidStorageSpaceChecker : StorageSpaceChecker {
    override fun getAvailableBytes(dir: File): Long {
        return try {
            if (!dir.exists()) dir.mkdirs()
            val space = dir.usableSpace
            if (space > 0L) {
                space
            } else {
                android.os.StatFs(dir.absolutePath).availableBytes
            }
        } catch (e: Throwable) {
            Long.MAX_VALUE
        }
    }
}

class StreamRecordingCoordinator(
    private val baseDirProvider: () -> File,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val metadataHelper: RadioMetadataHelper = RadioMetadataHelper(),
    private val metadataFetcher: MetadataFetcher = MetadataFetcher { _, _ -> null },
    private val coverArtDownloader: CoverArtDownloader = CoverArtDownloader { _, _ -> false },
    private val minDurationSeconds: Long = AppConfig.Recording.MIN_DURATION_SECONDS,
    private val maxTrackDurationSeconds: Long = AppConfig.Recording.MAX_TRACK_DURATION_SECONDS,
    private val preRollSeconds: Long = AppConfig.Recording.PRE_ROLL_SECONDS,
    private val postRollSeconds: Long = AppConfig.Recording.POST_ROLL_SECONDS,
    private val minDurationSecondsProvider: (() -> Long)? = null,
    private val preRollSecondsProvider: (() -> Long)? = null,
    private val postRollSecondsProvider: (() -> Long)? = null,
    private val storageSpaceChecker: StorageSpaceChecker = AndroidStorageSpaceChecker(),
    private val onLowStorage: (() -> Unit)? = null
) {
    private class RecordingSession(val completedCount: AtomicInteger = AtomicInteger(0))

    private class ActiveTrack(
        val session: RecordingSession,
        val file: File,
        val outputStream: OutputStream,
        val enrichmentDeferred: Deferred<SongAdditionalInfo?>?,
        val stationName: String,
        val streamUrl: String,
        val artist: String,
        val title: String,
        val bitrate: String?,
        val ext: String,
        val isShow: Boolean,
        var isPartial: Boolean,
        val trackStartTimeMs: Long,
        val coreStartTimeMs: Long,
        val coreDurationTracker: AudioDurationTracker,
        val totalDurationTracker: AudioDurationTracker
    ) {
        var coreEndTimeMs: Long? = null
        var coreBytesWritten: Long = 0L
        var totalBytesWritten: Long = 0L
        var postRollRemainingMicros: Long = 0L
        var postRollTotalMicros: Long = 0L

        fun writePreRollFrame(frame: AudioFrame) {
            writeToStream(frame.bytes, 0, frame.bytes.size, countAsCore = false)
        }

        fun writeCoreFrame(frame: AudioFrame) {
            writeToStream(frame.bytes, 0, frame.bytes.size, countAsCore = true)
        }

        fun writeCoreBytes(buffer: ByteArray, offset: Int, length: Int) {
            writeToStream(buffer, offset, length, countAsCore = true)
        }

        fun writePostRollFrame(frame: AudioFrame) {
            writeToStream(frame.bytes, 0, frame.bytes.size, countAsCore = false)
            postRollRemainingMicros -= frame.durationMicros
        }

        fun writePostRollRaw(buffer: ByteArray, offset: Int, length: Int, estimatedMicros: Long) {
            writeToStream(buffer, offset, length, countAsCore = false)
            postRollRemainingMicros -= estimatedMicros
        }

        private fun writeToStream(buffer: ByteArray, offset: Int, length: Int, countAsCore: Boolean) {
            if (length <= 0) return
            try {
                outputStream.write(buffer, offset, length)
                totalDurationTracker.consume(buffer, offset, length)
                totalBytesWritten += length.toLong()
                if (countAsCore) {
                    coreDurationTracker.consume(buffer, offset, length)
                    coreBytesWritten += length.toLong()
                }
            } catch (_: Exception) {
                // Ignore write error; safeguard/close handles cleanup
            }
        }

        fun closeStream() {
            try {
                outputStream.flush()
                outputStream.close()
            } catch (_: Exception) {
                // Ignore close error
            }
        }
    }

    private data class PendingTrackSnapshot(
        val session: RecordingSession,
        val fileToFinalize: File,
        val enrichmentDeferred: Deferred<SongAdditionalInfo?>?,
        val stationName: String,
        val streamUrl: String,
        val artist: String,
        val title: String,
        val bitrate: String?,
        val ext: String,
        val isShow: Boolean,
        val isPartial: Boolean,
        val trackStartTimeMs: Long,
        val closedAtMs: Long,
        val coreStartTimeMs: Long,
        val coreEndTimeMs: Long,
        val coreBytesWritten: Long,
        val coreDurationTracker: AudioDurationTracker,
        val totalBytesWritten: Long,
        val totalDurationTracker: AudioDurationTracker
    )

    @Volatile
    var isRecording: Boolean = false
        private set

    @Volatile
    var isUnsplitShowMode: Boolean = false
        private set

    val completedTrackCount: Int
        get() = (activeSession ?: lastSession)?.completedCount?.get() ?: 0

    var currentStationName: String = ""
        private set
    var currentStreamUrl: String = ""
        private set
    var currentRawMetadata: String = ""
        private set
    var currentArtist: String = ""
        private set
    var currentTitle: String = ""
        private set
    var currentBitrate: String? = null
        private set
    var isCurrentShow: Boolean = false
        private set

    private var isCurrentStreamAac: Boolean = false
    private var currentTrack: ActiveTrack? = null
    private val postRollTracks = mutableListOf<ActiveTrack>()

    val currentFile: File?
        get() = synchronized(lock) { currentTrack?.file }

    val activeRecordingFilePath: String?
        get() = synchronized(lock) { if (isRecording) currentTrack?.file?.absolutePath else null }

    private var frameAccumulator: AudioFrameAccumulator? = null
    private var preRollBuffer: PreRollFrameBuffer? = null

    private var activeSession: RecordingSession? = null
    private var lastSession: RecordingSession? = null
    private var lastFinalizationJob: Job? = null

    private val coordinatorScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    private val json = Json { prettyPrint = true; encodeDefaults = true }

    private fun currentMinDurationSeconds(): Long {
        return (minDurationSecondsProvider?.invoke() ?: minDurationSeconds).coerceAtLeast(0L)
    }

    private fun currentPreRollSeconds(): Long {
        return (preRollSecondsProvider?.invoke() ?: preRollSeconds).coerceAtLeast(0L)
    }

    private fun currentPostRollSeconds(): Long {
        return (postRollSecondsProvider?.invoke() ?: postRollSeconds).coerceAtLeast(0L)
    }

    fun setBitrate(bitrate: String?) {
        synchronized(lock) {
            currentBitrate = bitrate
        }
    }

    /**
     * Resets compressed frame sync state after an HTTP reconnect on the same recording session
     * so trailing incomplete bytes from the dropped socket cannot fuse with the new stream.
     */
    fun resetFrameAlignment() {
        synchronized(lock) {
            if (isRecording) {
                frameAccumulator?.reset()
            }
        }
    }

    fun checkStorageSafeguard(): Boolean {
        synchronized(lock) {
            val baseDir = baseDirProvider()
            val availableBytes = storageSpaceChecker.getAvailableBytes(baseDir)
            if (availableBytes < AppConfig.Recording.MIN_FREE_STORAGE_BYTES) {
                if (isRecording) {
                    haltRecordingImmediateLocked(markLastTrackPartial = true, onSessionFinalized = null)
                }
                onLowStorage?.invoke()
                return false
            }
            return true
        }
    }

    fun startRecording(
        stationName: String,
        streamUrl: String,
        initialRawMetadata: String = "",
        bitrate: String? = null,
        isAacHint: Boolean? = null,
        forceUnsplitShow: Boolean = false
    ) {
        synchronized(lock) {
            if (isRecording) return
            if (!checkStorageSafeguard()) return

            val session = RecordingSession()
            activeSession = session
            lastSession = session

            currentStationName = stationName
            currentStreamUrl = streamUrl
            currentBitrate = bitrate
            isCurrentStreamAac = isAacHint ?: streamUrl.contains("aac", ignoreCase = true)
            isUnsplitShowMode = forceUnsplitShow
            isRecording = true

            if (!isUnsplitShowMode) {
                frameAccumulator = AudioFrameAccumulator(preferAac = isCurrentStreamAac)
                preRollBuffer = PreRollFrameBuffer(maxDurationMicros = currentPreRollSeconds() * 1_000_000L)
            } else {
                frameAccumulator = null
                preRollBuffer = null
            }

            currentTrack = openNewTrackLocked(
                rawMetadata = initialRawMetadata,
                isPartial = true, // First track started mid-stream
                preRollFrames = emptyList()
            )
        }
    }

    fun stopRecording(onAllFinalized: ((Int) -> Unit)? = null) {
        synchronized(lock) {
            if (!isRecording) {
                isUnsplitShowMode = false
                if (onAllFinalized != null) {
                    val session = lastSession
                    val prevJob = lastFinalizationJob
                    val job = coordinatorScope.launch {
                        prevJob?.join()
                        onAllFinalized(session?.completedCount?.get() ?: 0)
                    }
                    lastFinalizationJob = job
                }
                return
            }
            haltRecordingImmediateLocked(markLastTrackPartial = true, onSessionFinalized = onAllFinalized)
        }
    }

    /**
     * Blocks until all queued track finalizations have completed. Intended for deterministic tests.
     */
    fun awaitPendingFinalizations() {
        val job = synchronized(lock) { lastFinalizationJob }
        if (job != null) {
            runBlocking { job.join() }
        }
    }

    fun onStreamBytes(buffer: ByteArray, offset: Int, length: Int) {
        if (!isRecording || length <= 0) return

        synchronized(lock) {
            if (!isRecording) return
            val active = currentTrack ?: return
            val acc = frameAccumulator
            val pr = preRollBuffer
            if (acc != null && pr != null && !isUnsplitShowMode) {
                acc.push(
                    input = buffer,
                    offset = offset,
                    length = length,
                    onFrame = { frame ->
                        pr.push(frame)
                        active.writeCoreFrame(frame)
                        advancePostRollFramesLocked(frame)
                    },
                    onRawFallback = { rawBuf, rawOff, rawLen ->
                        active.writeCoreBytes(rawBuf, rawOff, rawLen)
                        advancePostRollRawLocked(rawBuf, rawOff, rawLen)
                    }
                )
            } else {
                active.writeCoreBytes(buffer, offset, length)
            }
        }
    }

    fun onMetadataChanged(rawMetadata: String) {
        synchronized(lock) {
            if (!isRecording || isUnsplitShowMode) return

            val cleaned = metadataHelper.sanitizeRawInput(rawMetadata)
            if (cleaned == currentRawMetadata) {
                return
            }

            if (!checkStorageSafeguard()) return

            val active = currentTrack
            frameAccumulator?.drainUnparsed { rawBuf, rawOff, rawLen ->
                active?.writeCoreBytes(rawBuf, rawOff, rawLen)
            }

            val activePreRollSeconds = currentPreRollSeconds()
            val activePostRollSeconds = currentPostRollSeconds()
            preRollBuffer?.updateCapacity(activePreRollSeconds * 1_000_000L)

            val nowMs = clock()
            if (active != null) {
                active.coreEndTimeMs = nowMs
                if (!active.isShow && activePostRollSeconds > 0L) {
                    val marginMicros = activePostRollSeconds * 1_000_000L
                    active.postRollTotalMicros = marginMicros
                    active.postRollRemainingMicros = marginMicros
                    postRollTracks.add(active)
                } else {
                    finalizeActiveTrackNowLocked(active, closedAtMs = nowMs)
                }
                currentTrack = null
            }

            val preRollFrames = if (activePreRollSeconds > 0L) {
                preRollBuffer?.snapshotFrames().orEmpty()
            } else {
                emptyList()
            }

            currentTrack = openNewTrackLocked(
                rawMetadata = rawMetadata,
                isPartial = false,
                preRollFrames = preRollFrames
            )
        }
    }

    private fun haltRecordingImmediateLocked(
        markLastTrackPartial: Boolean,
        onSessionFinalized: ((Int) -> Unit)?
    ) {
        isRecording = false
        isUnsplitShowMode = false

        val nowMs = clock()

        if (postRollTracks.isNotEmpty()) {
            val pendingTrailing = postRollTracks.toList()
            postRollTracks.clear()
            for (trailing in pendingTrailing) {
                finalizeActiveTrackNowLocked(trailing, closedAtMs = nowMs)
            }
        }

        val active = currentTrack
        if (active != null) {
            frameAccumulator?.drainUnparsed { rawBuf, rawOff, rawLen ->
                active.writeCoreBytes(rawBuf, rawOff, rawLen)
            }
            if (markLastTrackPartial && !active.isShow) {
                active.isPartial = true
            }
            active.coreEndTimeMs = nowMs
            finalizeActiveTrackNowLocked(active, closedAtMs = nowMs, onSessionFinalized = onSessionFinalized)
            currentTrack = null
        } else if (onSessionFinalized != null) {
            val session = activeSession ?: lastSession
            val prevJob = lastFinalizationJob
            val job = coordinatorScope.launch {
                prevJob?.join()
                onSessionFinalized(session?.completedCount?.get() ?: 0)
            }
            lastFinalizationJob = job
        }

        frameAccumulator = null
        preRollBuffer?.clear()
        preRollBuffer = null
        activeSession = null
    }

    private fun advancePostRollFramesLocked(frame: AudioFrame) {
        if (postRollTracks.isEmpty()) return
        val nowMs = clock()
        val iterator = postRollTracks.iterator()
        while (iterator.hasNext()) {
            val trailing = iterator.next()
            trailing.writePostRollFrame(frame)
            if (trailing.postRollRemainingMicros <= 0L) {
                iterator.remove()
                finalizeActiveTrackNowLocked(trailing, closedAtMs = nowMs)
            }
        }
    }

    private fun advancePostRollRawLocked(buffer: ByteArray, offset: Int, length: Int) {
        if (postRollTracks.isEmpty() || length <= 0) return
        val nowMs = clock()
        val iterator = postRollTracks.iterator()
        while (iterator.hasNext()) {
            val trailing = iterator.next()
            val bitrateBps = parseBitrateBps(trailing.bitrate)
            val estimatedMicros = if (bitrateBps > 0L) {
                (length.toLong() * 8L * 1_000_000L) / bitrateBps
            } else {
                0L
            }
            if (estimatedMicros > 0L) {
                trailing.writePostRollRaw(buffer, offset, length, estimatedMicros)
            }
            val elapsedMs = (nowMs - (trailing.coreEndTimeMs ?: nowMs)).coerceAtLeast(0L)
            val marginMs = trailing.postRollTotalMicros / 1000L
            if (trailing.postRollRemainingMicros <= 0L || elapsedMs >= marginMs) {
                iterator.remove()
                finalizeActiveTrackNowLocked(trailing, closedAtMs = nowMs)
            }
        }
    }

    private fun sanitize(input: String): String {
        return input.replace("[\\\\/:*?\"<>|]".toRegex(), "_").trim().ifBlank { "Unknown" }
    }

    private fun isShow(rawMetadata: String, artist: String, title: String, stationName: String): Boolean {
        if (isUnsplitShowMode) return true
        val cleanArtist = artist.trim()
        val cleanTitle = title.trim()
        val safeStation = stationName.trim()

        if (cleanArtist.isBlank() || cleanTitle.isBlank()) return true
        if (cleanArtist.equals("Otseeeter", ignoreCase = true) ||
            cleanArtist.equals("Live Broadcast", ignoreCase = true) ||
            cleanArtist.equals("Live Stream", ignoreCase = true)
        ) return true
        if (cleanArtist.equals(safeStation, ignoreCase = true)) return true

        val sanitizedRaw = metadataHelper.sanitizeRawInput(rawMetadata)
        if (!sanitizedRaw.contains(" - ")) return true

        if (AppConfig.Metadata.IGNORE_TERMS.any { term ->
                cleanTitle.contains(term, ignoreCase = true) || cleanArtist.contains(term, ignoreCase = true)
            }
        ) return true

        return false
    }

    private fun createUniqueShowFile(rootDir: File, stationName: String, startTimeMs: Long, ext: String): File {
        val safeStation = sanitize(stationName.ifBlank { "Radio" })
        val targetDir = File(File(rootDir, AppConfig.Recording.DIR_BROADCASTS), safeStation)
        if (!targetDir.exists()) targetDir.mkdirs()

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(startTimeMs))
        val baseName = "${safeStation}_$timestamp"
        var candidate = File(targetDir, "$baseName.$ext")
        var counter = 1
        while (candidate.exists()) {
            candidate = File(targetDir, "${baseName}_%02d.$ext".format(counter++))
        }
        return candidate
    }

    private fun cleanupEmptyDirs(startDir: File?, stopAtDir: File) {
        var dir = startDir
        val stopCanonical = stopAtDir.canonicalPath
        while (dir != null && dir.canonicalPath != stopCanonical) {
            if (!dir.exists() || !dir.isDirectory || !dir.listFiles().isNullOrEmpty()) {
                break
            }
            val parent = dir.parentFile
            if (!dir.delete()) {
                break
            }
            dir = parent
        }
    }

    private fun openNewTrackLocked(
        rawMetadata: String,
        isPartial: Boolean,
        preRollFrames: List<AudioFrame>
    ): ActiveTrack {
        val cleaned = metadataHelper.sanitizeRawInput(rawMetadata)
        currentRawMetadata = cleaned

        val parsed = metadataHelper.parse(rawMetadata, currentStationName)
        currentArtist = parsed.artist
        currentTitle = parsed.title
        isCurrentShow = isShow(rawMetadata, currentArtist, currentTitle, currentStationName)

        val coreStartMs = clock()
        val appliedPreRoll = if (!isCurrentShow) preRollFrames else emptyList()
        val preRollDurationMs = appliedPreRoll.sumOf { it.durationMicros } / 1000L
        val trackStartMs = coreStartMs - preRollDurationMs

        val rootDir = baseDirProvider()
        val ext = if (isCurrentStreamAac) "aac" else "mp3"

        val file: File
        var enrichmentDeferred: Deferred<SongAdditionalInfo?>? = null
        if (isCurrentShow) {
            file = createUniqueShowFile(rootDir, currentStationName, trackStartMs, ext)
        } else {
            val safeArtist = sanitize(currentArtist)
            val safeAlbum = AppConfig.Recording.DIR_UNKNOWN_ALBUM
            val safeTitle = sanitize(currentTitle)

            val targetDir = File(File(File(rootDir, AppConfig.Recording.DIR_MUSIC), safeArtist), safeAlbum)
            if (!targetDir.exists()) targetDir.mkdirs()

            val existingNumbers = targetDir.listFiles()?.mapNotNull { f ->
                if (f.isFile && (f.name.endsWith(".mp3") || f.name.endsWith(".aac"))) {
                    Regex("""^(\d{2})\s*-""").find(f.name)?.groupValues?.get(1)?.toIntOrNull()
                } else null
            } ?: emptyList()

            val nextTrackNum = if (existingNumbers.isEmpty()) 1 else (existingNumbers.maxOrNull() ?: 0) + 1

            val existingSongFile = targetDir.listFiles()?.firstOrNull { f ->
                f.isFile && f.name.contains(" - $safeArtist - $safeTitle")
            }
            val trackNumStr = if (existingSongFile != null) {
                Regex("""^(\d{2})\s*-""").find(existingSongFile.name)?.groupValues?.get(1) ?: "%02d".format(nextTrackNum)
            } else {
                "%02d".format(nextTrackNum)
            }

            val baseName = "$trackNumStr - $safeArtist - $safeTitle"
            var candidate = File(targetDir, "$baseName.$ext")
            var duplicateCounter = 1
            while (candidate.exists()) {
                candidate = File(targetDir, "${baseName}_%02d.$ext".format(duplicateCounter++))
            }
            file = candidate

            val artistForQuery = currentArtist
            val titleForQuery = currentTitle
            enrichmentDeferred = coordinatorScope.async {
                try {
                    metadataFetcher.fetch(artistForQuery, titleForQuery)
                } catch (_: Exception) {
                    null
                }
            }
        }

        val session = activeSession ?: lastSession ?: RecordingSession()
        val active = ActiveTrack(
            session = session,
            file = file,
            outputStream = FileOutputStream(file),
            enrichmentDeferred = enrichmentDeferred,
            stationName = currentStationName,
            streamUrl = currentStreamUrl,
            artist = currentArtist,
            title = currentTitle,
            bitrate = currentBitrate,
            ext = ext,
            isShow = isCurrentShow,
            isPartial = isPartial,
            trackStartTimeMs = trackStartMs,
            coreStartTimeMs = coreStartMs,
            coreDurationTracker = AudioDurationTracker(preferAac = isCurrentStreamAac),
            totalDurationTracker = AudioDurationTracker(preferAac = isCurrentStreamAac)
        )

        for (frame in appliedPreRoll) {
            active.writePreRollFrame(frame)
        }
        return active
    }

    private fun finalizeActiveTrackNowLocked(
        track: ActiveTrack,
        closedAtMs: Long,
        onSessionFinalized: ((Int) -> Unit)? = null
    ) {
        track.closeStream()

        val snapshot = if (track.file.exists()) {
            PendingTrackSnapshot(
                session = track.session,
                fileToFinalize = track.file,
                enrichmentDeferred = track.enrichmentDeferred,
                stationName = track.stationName,
                streamUrl = track.streamUrl,
                artist = track.artist,
                title = track.title,
                bitrate = track.bitrate,
                ext = track.ext,
                isShow = track.isShow,
                isPartial = track.isPartial,
                trackStartTimeMs = track.trackStartTimeMs,
                closedAtMs = closedAtMs,
                coreStartTimeMs = track.coreStartTimeMs,
                coreEndTimeMs = track.coreEndTimeMs ?: closedAtMs,
                coreBytesWritten = track.coreBytesWritten,
                coreDurationTracker = track.coreDurationTracker,
                totalBytesWritten = track.totalBytesWritten,
                totalDurationTracker = track.totalDurationTracker
            )
        } else {
            null
        }

        if (snapshot == null && onSessionFinalized == null) return

        val previousJob = lastFinalizationJob
        val job = coordinatorScope.launch {
            previousJob?.join()
            if (snapshot != null) {
                finalizeTrack(snapshot)
            }
            if (onSessionFinalized != null) {
                onSessionFinalized(track.session.completedCount.get())
            }
        }
        lastFinalizationJob = job
    }

    private fun nextSequentialTrackNumStr(targetDir: File, excludeFile: File): String {
        val existingNumbers = targetDir.listFiles()?.mapNotNull { f ->
            if (f.isFile && f.canonicalPath != excludeFile.canonicalPath &&
                (f.name.endsWith(".mp3") || f.name.endsWith(".aac"))
            ) {
                Regex("""^(\d{2})\s*-""").find(f.name)?.groupValues?.get(1)?.toIntOrNull()
            } else null
        } ?: emptyList()
        val nextNum = if (existingNumbers.isEmpty()) 1 else (existingNumbers.maxOrNull() ?: 0) + 1
        return "%02d".format(nextNum)
    }

    private fun parseBitrateBps(bitrate: String?): Long {
        if (bitrate.isNullOrBlank()) return 0L
        val kbps = Regex("""(\d+)""").find(bitrate)?.groupValues?.get(1)?.toLongOrNull() ?: return 0L
        return if (kbps > 0L) kbps * 1000L else 0L
    }

    private fun computeDurationSeconds(
        tracker: AudioDurationTracker,
        bytesWritten: Long,
        bitrate: String?,
        startMs: Long,
        endMs: Long
    ): Long {
        if (tracker.validFrameCount >= 2L) {
            return (tracker.totalDurationMicros / 1_000_000L).coerceAtLeast(0L)
        }
        val bitrateBps = parseBitrateBps(bitrate)
        if (bitrateBps > 0L && bytesWritten > 0L) {
            return ((bytesWritten * 8L) / bitrateBps).coerceAtLeast(0L)
        }
        return ((endMs - startMs) / 1000L).coerceAtLeast(0L)
    }

    private suspend fun finalizeTrack(snapshot: PendingTrackSnapshot) {
        val fileToFinalize = snapshot.fileToFinalize
        if (!fileToFinalize.exists()) return

        val rootDir = baseDirProvider()
        val ext = snapshot.ext
        val coreDurationSec = computeDurationSeconds(
            tracker = snapshot.coreDurationTracker,
            bytesWritten = snapshot.coreBytesWritten,
            bitrate = snapshot.bitrate,
            startMs = snapshot.coreStartTimeMs,
            endMs = snapshot.coreEndTimeMs
        )
        val fileDurationSec = computeDurationSeconds(
            tracker = snapshot.totalDurationTracker,
            bytesWritten = snapshot.totalBytesWritten,
            bitrate = snapshot.bitrate,
            startMs = snapshot.trackStartTimeMs,
            endMs = snapshot.closedAtMs
        )

        // Pruning filter evaluates core metadata duration (without overlap margins)
        val activeMinDurationSec = currentMinDurationSeconds()
        if (coreDurationSec < activeMinDurationSec) {
            snapshot.enrichmentDeferred?.cancel()
            val oldParent = fileToFinalize.parentFile
            fileToFinalize.delete()
            cleanupEmptyDirs(oldParent, rootDir)
            return
        }

        val effectiveIsShow = snapshot.isShow || coreDurationSec > maxTrackDurationSeconds
        val isoTimestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date(snapshot.trackStartTimeMs))

        if (effectiveIsShow) {
            snapshot.enrichmentDeferred?.cancel()
            val targetShowFile = if (snapshot.isShow) {
                fileToFinalize
            } else {
                val oldParent = fileToFinalize.parentFile
                val destination = createUniqueShowFile(rootDir, snapshot.stationName, snapshot.trackStartTimeMs, ext)
                val moved = if (fileToFinalize.renameTo(destination)) {
                    destination
                } else {
                    fileToFinalize.copyTo(destination, overwrite = true)
                    fileToFinalize.delete()
                    destination
                }
                cleanupEmptyDirs(oldParent, rootDir)
                moved
            }

            snapshot.session.completedCount.incrementAndGet()
            val safeStation = sanitize(snapshot.stationName.ifBlank { "Radio" })
            val sidecar = SidecarMetadata(
                title = snapshot.title.ifBlank { "Otseeeter" },
                artist = snapshot.artist.ifBlank { safeStation },
                album = null,
                year = null,
                genre = null,
                trackNumber = null,
                lyrics = null,
                station = snapshot.stationName,
                stationUrl = snapshot.streamUrl,
                recordedAt = isoTimestamp,
                durationSeconds = fileDurationSec,
                fileSize = targetShowFile.length(),
                codec = ext,
                bitrate = snapshot.bitrate,
                audioFile = targetShowFile.name,
                coverArtFile = null,
                partial = snapshot.isPartial
            )
            val jsonFile = File(targetShowFile.parentFile, "${targetShowFile.nameWithoutExtension}.json")
            jsonFile.writeText(json.encodeToString(sidecar))
        } else {
            val enriched = withTimeoutOrNull(AppConfig.Recording.ENRICHMENT_TIMEOUT_MS) {
                snapshot.enrichmentDeferred?.await()
            }
            val discoveredAlbum = enriched?.album?.trim()?.ifBlank { null }
            val officialTrackNum = enriched?.trackNumber?.takeIf { it > 0 }
            val safeArtist = sanitize(snapshot.artist)
            val safeTitle = sanitize(snapshot.title)

            val targetAlbumName = discoveredAlbum ?: AppConfig.Recording.DIR_UNKNOWN_ALBUM
            val targetDir = File(File(File(rootDir, AppConfig.Recording.DIR_MUSIC), safeArtist), sanitize(targetAlbumName))
            if (!targetDir.exists()) targetDir.mkdirs()

            // Duplicate detection: never delete an existing longer recording in favor of a shorter one
            val existingSongFile = targetDir.listFiles()?.firstOrNull { f ->
                f.isFile && (f.name.endsWith(".mp3") || f.name.endsWith(".aac")) &&
                    f.canonicalPath != fileToFinalize.canonicalPath &&
                    f.name.contains(" - $safeArtist - $safeTitle")
            }

            var replacedExisting = false
            if (existingSongFile != null) {
                val existingJsonFile = File(targetDir, "${existingSongFile.nameWithoutExtension}.json")
                val existingMeta = if (existingJsonFile.exists()) {
                    try {
                        json.decodeFromString<SidecarMetadata>(existingJsonFile.readText())
                    } catch (_: Exception) {
                        null
                    }
                } else {
                    null
                }
                val existingIsPartial = existingMeta?.partial ?: false
                val existingDurationSec = existingMeta?.durationSeconds ?: 0L
                val newReplacesPartial = existingIsPartial && !snapshot.isPartial && (
                    fileDurationSec > existingDurationSec ||
                        (fileDurationSec == existingDurationSec && fileToFinalize.length() >= existingSongFile.length())
                    )

                if (newReplacesPartial) {
                    existingSongFile.delete()
                    if (existingJsonFile.exists()) existingJsonFile.delete()
                    replacedExisting = true
                }
            }

            snapshot.session.completedCount.incrementAndGet()

            val alreadyInTargetDir = fileToFinalize.parentFile?.canonicalPath == targetDir.canonicalPath
            val trackNumStr = if (officialTrackNum != null) {
                "%02d".format(officialTrackNum)
            } else if (existingSongFile != null && !replacedExisting) {
                Regex("""^(\d{2})\s*-""").find(existingSongFile.name)?.groupValues?.get(1)
                    ?: nextSequentialTrackNumStr(targetDir, fileToFinalize)
            } else if (alreadyInTargetDir) {
                Regex("""^(\d{2})\s*-""").find(fileToFinalize.name)?.groupValues?.get(1)
                    ?: nextSequentialTrackNumStr(targetDir, fileToFinalize)
            } else {
                nextSequentialTrackNumStr(targetDir, fileToFinalize)
            }

            val targetBaseName = "$trackNumStr - $safeArtist - $safeTitle"
            var targetCandidate = File(targetDir, "$targetBaseName.$ext")
            var dup = 1
            while (targetCandidate.exists() && targetCandidate.canonicalPath != fileToFinalize.canonicalPath) {
                targetCandidate = File(targetDir, "${targetBaseName}_%02d.$ext".format(dup++))
            }

            var targetAudioFile = fileToFinalize
            if (fileToFinalize.canonicalPath != targetCandidate.canonicalPath) {
                val oldDir = fileToFinalize.parentFile
                if (fileToFinalize.renameTo(targetCandidate)) {
                    targetAudioFile = targetCandidate
                    if (oldDir != null && oldDir.canonicalPath != targetDir.canonicalPath) {
                        cleanupEmptyDirs(oldDir, rootDir)
                    }
                }
            }

            var coverArtFileName: String? = null
            if (!enriched?.coverArtUrl.isNullOrBlank()) {
                val coverFile = File(targetDir, "cover.jpg")
                if (!coverFile.exists()) {
                    coverArtDownloader.download(enriched!!.coverArtUrl!!, coverFile)
                }
                if (coverFile.exists()) {
                    coverArtFileName = "cover.jpg"
                }
            }

            val sidecar = SidecarMetadata(
                title = snapshot.title,
                artist = snapshot.artist,
                album = discoveredAlbum,
                year = enriched?.year,
                genre = enriched?.genre,
                trackNumber = officialTrackNum ?: Regex("""^(\d{2})\s*-""").find(targetAudioFile.name)?.groupValues?.get(1)?.toIntOrNull(),
                lyrics = enriched?.lyrics,
                station = snapshot.stationName,
                stationUrl = snapshot.streamUrl,
                recordedAt = isoTimestamp,
                durationSeconds = fileDurationSec,
                fileSize = targetAudioFile.length(),
                codec = ext,
                bitrate = snapshot.bitrate,
                audioFile = targetAudioFile.name,
                coverArtFile = coverArtFileName,
                partial = snapshot.isPartial
            )

            val jsonFile = File(targetDir, "${targetAudioFile.nameWithoutExtension}.json")
            jsonFile.writeText(json.encodeToString(sidecar))
        }
    }
}
