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
    private val lookbackDurationSeconds: Long = AppConfig.Recording.LOOKBACK_BUFFER_SECONDS,
    private val audioBoundaryDetector: AudioBoundaryDetector = AudioBoundaryDetector { frames, _ -> frames.size },
    private val storageSpaceChecker: StorageSpaceChecker = AndroidStorageSpaceChecker(),
    private val onLowStorage: (() -> Unit)? = null
) {
    private class RecordingSession(val completedCount: AtomicInteger = AtomicInteger(0))

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
        val bytesWritten: Long,
        val durationTracker: AudioDurationTracker?
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
    private var currentOutputStream: OutputStream? = null
    var currentFile: File? = null
        private set

    val activeRecordingFilePath: String?
        get() = synchronized(lock) { if (isRecording) currentFile?.absolutePath else null }

    private var trackStartTimeMs: Long = 0L
    private var isPartialTrack: Boolean = false
    private var currentBytesWritten: Long = 0L
    private var currentDurationTracker: AudioDurationTracker? = null
    private var currentEnrichmentDeferred: Deferred<SongAdditionalInfo?>? = null
    private var frameAccumulator: AudioFrameAccumulator? = null
    private var lookbackBuffer: LookbackFrameBuffer? = null

    private var activeSession: RecordingSession? = null
    private var lastSession: RecordingSession? = null
    private var lastFinalizationJob: Job? = null

    private val coordinatorScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    private val json = Json { prettyPrint = true; encodeDefaults = true }

    fun setBitrate(bitrate: String?) {
        synchronized(lock) {
            currentBitrate = bitrate
        }
    }

    fun checkStorageSafeguard(): Boolean {
        synchronized(lock) {
            val baseDir = baseDirProvider()
            val availableBytes = storageSpaceChecker.getAvailableBytes(baseDir)
            if (availableBytes < AppConfig.Recording.MIN_FREE_STORAGE_BYTES) {
                if (isRecording) {
                    isRecording = false
                    drainLookbackToCurrentTrackLocked(clearPipeline = true)
                    closeCurrentTrackLocked()
                    activeSession = null
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
            isPartialTrack = true // First track started mid-stream

            if (!isUnsplitShowMode && lookbackDurationSeconds > 0L) {
                frameAccumulator = AudioFrameAccumulator(preferAac = isCurrentStreamAac)
                lookbackBuffer = LookbackFrameBuffer(maxDurationMicros = lookbackDurationSeconds * 1_000_000L)
            } else {
                frameAccumulator = null
                lookbackBuffer = null
            }

            openNewTrackLocked(initialRawMetadata)
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
            isRecording = false
            isUnsplitShowMode = false
            drainLookbackToCurrentTrackLocked(clearPipeline = true)
            closeCurrentTrackLocked(onSessionFinalized = onAllFinalized)
            activeSession = null
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
            val acc = frameAccumulator
            val lb = lookbackBuffer
            if (acc != null && lb != null && !isUnsplitShowMode) {
                acc.push(
                    input = buffer,
                    offset = offset,
                    length = length,
                    onFrame = { frame ->
                        lb.push(frame) { overflow ->
                            writeFrameToCurrentTrackLocked(overflow)
                        }
                    },
                    onRawFallback = { rawBuf, rawOff, rawLen ->
                        writeBytesToCurrentTrackLocked(rawBuf, rawOff, rawLen)
                    }
                )
            } else {
                writeBytesToCurrentTrackLocked(buffer, offset, length)
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

            frameAccumulator?.drainUnparsed { rawBuf, rawOff, rawLen ->
                writeBytesToCurrentTrackLocked(rawBuf, rawOff, rawLen)
            }
            val lb = lookbackBuffer
            if (lb != null && !lb.isEmpty()) {
                val buffered = lb.snapshotFrames()
                val hasFlushedPrefix = currentBytesWritten > 0L
                val splitIdx = audioBoundaryDetector.findSplitFrameIndex(buffered, hasFlushedPrefix)
                    .coerceIn(0, buffered.size)
                lb.popPrefix(splitIdx) { frame ->
                    writeFrameToCurrentTrackLocked(frame)
                }
            }

            closeCurrentTrackLocked()
            isPartialTrack = false // Subsequent tracks started at the track transition boundary
            openNewTrackLocked(rawMetadata)
        }
    }

    private fun writeBytesToCurrentTrackLocked(buffer: ByteArray, offset: Int, length: Int) {
        if (length <= 0) return
        try {
            currentOutputStream?.write(buffer, offset, length)
            currentDurationTracker?.consume(buffer, offset, length)
            currentBytesWritten += length.toLong()
        } catch (_: Exception) {
            // Ignore or handle write error
        }
    }

    private fun writeFrameToCurrentTrackLocked(frame: AudioFrame) {
        try {
            currentOutputStream?.write(frame.bytes)
            currentDurationTracker?.consume(frame.bytes, 0, frame.bytes.size)
            currentBytesWritten += frame.bytes.size.toLong()
        } catch (_: Exception) {
            // Ignore or handle write error
        }
    }

    private fun drainLookbackToCurrentTrackLocked(clearPipeline: Boolean) {
        frameAccumulator?.drainUnparsed { rawBuf, rawOff, rawLen ->
            writeBytesToCurrentTrackLocked(rawBuf, rawOff, rawLen)
        }
        lookbackBuffer?.drainAll { frame ->
            writeFrameToCurrentTrackLocked(frame)
        }
        if (clearPipeline) {
            frameAccumulator = null
            lookbackBuffer = null
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

    private fun openNewTrackLocked(rawMetadata: String) {
        val cleaned = metadataHelper.sanitizeRawInput(rawMetadata)
        currentRawMetadata = cleaned

        val parsed = metadataHelper.parse(rawMetadata, currentStationName)
        currentArtist = parsed.artist
        currentTitle = parsed.title
        val retainedMicros = lookbackBuffer?.totalDurationMicros ?: 0L
        trackStartTimeMs = clock() - (retainedMicros / 1000L)
        currentBytesWritten = 0L
        currentDurationTracker = AudioDurationTracker(preferAac = isCurrentStreamAac)
        currentEnrichmentDeferred = null

        isCurrentShow = isShow(rawMetadata, currentArtist, currentTitle, currentStationName)

        val rootDir = baseDirProvider()
        val ext = if (isCurrentStreamAac) "aac" else "mp3"

        val file: File
        if (isCurrentShow) {
            file = createUniqueShowFile(rootDir, currentStationName, trackStartTimeMs, ext)
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
            currentEnrichmentDeferred = coordinatorScope.async {
                try {
                    metadataFetcher.fetch(artistForQuery, titleForQuery)
                } catch (_: Exception) {
                    null
                }
            }
        }

        currentFile = file
        currentOutputStream = FileOutputStream(file)
    }

    private fun closeCurrentTrackLocked(onSessionFinalized: ((Int) -> Unit)? = null) {
        val fileToFinalize = currentFile
        val enrichmentDeferred = currentEnrichmentDeferred
        val durationTracker = currentDurationTracker
        val bytesWritten = currentBytesWritten
        val session = activeSession ?: lastSession

        try {
            currentOutputStream?.flush()
            currentOutputStream?.close()
        } catch (_: Exception) {
            // Ignore close error
        } finally {
            currentOutputStream = null
            currentFile = null
            currentEnrichmentDeferred = null
            currentDurationTracker = null
            currentBytesWritten = 0L
        }

        val snapshot = if (fileToFinalize != null && fileToFinalize.exists() && session != null) {
            PendingTrackSnapshot(
                session = session,
                fileToFinalize = fileToFinalize,
                enrichmentDeferred = enrichmentDeferred,
                stationName = currentStationName,
                streamUrl = currentStreamUrl,
                artist = currentArtist,
                title = currentTitle,
                bitrate = currentBitrate,
                ext = if (isCurrentStreamAac) "aac" else "mp3",
                isShow = isCurrentShow,
                isPartial = isPartialTrack,
                trackStartTimeMs = trackStartTimeMs,
                closedAtMs = clock(),
                bytesWritten = bytesWritten,
                durationTracker = durationTracker
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
                onSessionFinalized(session?.completedCount?.get() ?: 0)
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

    private fun computeDurationSeconds(snapshot: PendingTrackSnapshot): Long {
        val tracker = snapshot.durationTracker
        if (tracker != null && tracker.validFrameCount >= 2L) {
            return (tracker.totalDurationMicros / 1_000_000L).coerceAtLeast(0L)
        }
        val bitrateBps = parseBitrateBps(snapshot.bitrate)
        if (bitrateBps > 0L && snapshot.bytesWritten > 0L) {
            return ((snapshot.bytesWritten * 8L) / bitrateBps).coerceAtLeast(0L)
        }
        return ((snapshot.closedAtMs - snapshot.trackStartTimeMs) / 1000L).coerceAtLeast(0L)
    }

    private suspend fun finalizeTrack(snapshot: PendingTrackSnapshot) {
        val fileToFinalize = snapshot.fileToFinalize
        if (!fileToFinalize.exists()) return

        val rootDir = baseDirProvider()
        val ext = snapshot.ext
        val durationSec = computeDurationSeconds(snapshot)

        // Pruning filter: clips under minDurationSeconds (ads, jingles, glitches) are deleted
        if (durationSec < minDurationSeconds) {
            snapshot.enrichmentDeferred?.cancel()
            val oldParent = fileToFinalize.parentFile
            fileToFinalize.delete()
            cleanupEmptyDirs(oldParent, rootDir)
            return
        }

        val effectiveIsShow = snapshot.isShow || durationSec > maxTrackDurationSeconds
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
                durationSeconds = durationSec,
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
                    durationSec > existingDurationSec ||
                        (durationSec == existingDurationSec && fileToFinalize.length() >= existingSongFile.length())
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
                durationSeconds = durationSec,
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
