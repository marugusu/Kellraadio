package app.radiorecalarm

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.lastOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

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

class StreamRecordingCoordinator(
    private val baseDirProvider: () -> File,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val metadataHelper: RadioMetadataHelper = RadioMetadataHelper(),
    private val metadataFetcher: MetadataFetcher = MetadataFetcher { _, _ -> null },
    private val coverArtDownloader: CoverArtDownloader = CoverArtDownloader { _, _ -> false },
    private val minDurationSeconds: Long = AppConfig.Recording.MIN_DURATION_SECONDS
) {
    @Volatile
    var isRecording: Boolean = false
        private set

    var completedTrackCount: Int = 0
        private set

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

    private var currentOutputStream: OutputStream? = null
    var currentFile: File? = null
        private set

    private var trackStartTimeMs: Long = 0L
    private var isPartialTrack: Boolean = false

    private var enrichmentJob: Job? = null
    private var currentEnrichedInfo: SongAdditionalInfo? = null

    private val coordinatorScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    private val json = Json { prettyPrint = true; encodeDefaults = true }

    fun setBitrate(bitrate: String?) {
        synchronized(lock) {
            currentBitrate = bitrate
        }
    }

    fun startRecording(
        stationName: String,
        streamUrl: String,
        initialRawMetadata: String = "",
        bitrate: String? = null
    ) {
        synchronized(lock) {
            if (isRecording) return

            currentStationName = stationName
            currentStreamUrl = streamUrl
            currentBitrate = bitrate
            isRecording = true
            completedTrackCount = 0
            isPartialTrack = true // First track started mid-stream

            openNewTrack(initialRawMetadata)
        }
    }

    fun stopRecording() {
        synchronized(lock) {
            if (!isRecording) return
            isRecording = false

            closeCurrentTrack()
        }
    }

    fun onStreamBytes(buffer: ByteArray, offset: Int, length: Int) {
        if (!isRecording || length <= 0) return

        synchronized(lock) {
            if (!isRecording) return
            try {
                currentOutputStream?.write(buffer, offset, length)
            } catch (e: Exception) {
                // Ignore or handle write error
            }
        }
    }

    fun onMetadataChanged(rawMetadata: String) {
        synchronized(lock) {
            if (!isRecording) return

            val cleaned = metadataHelper.sanitizeRawInput(rawMetadata)
            if (cleaned == currentRawMetadata) {
                return
            }

            closeCurrentTrack()
            isPartialTrack = false // Subsequent tracks started at the track transition boundary
            openNewTrack(rawMetadata)
        }
    }

    private fun sanitize(input: String): String {
        return input.replace("[\\\\/:*?\"<>|]".toRegex(), "_").trim().ifBlank { "Unknown" }
    }

    private fun isShow(artist: String, title: String, stationName: String): Boolean {
        val cleanArtist = artist.trim()
        val cleanTitle = title.trim()
        val safeStation = stationName.trim()

        if (cleanArtist.isBlank() || cleanTitle.isBlank()) return true
        if (cleanArtist.equals("Otseeeter", ignoreCase = true) || cleanArtist.equals("Live Broadcast", ignoreCase = true)) return true
        if (cleanArtist.equals(safeStation, ignoreCase = true)) return true
        return false
    }

    private fun openNewTrack(rawMetadata: String) {
        val cleaned = metadataHelper.sanitizeRawInput(rawMetadata)
        currentRawMetadata = cleaned

        val parsed = metadataHelper.parse(rawMetadata, currentStationName)
        currentArtist = parsed.artist
        currentTitle = parsed.title
        trackStartTimeMs = clock()
        currentEnrichedInfo = null

        isCurrentShow = isShow(currentArtist, currentTitle, currentStationName)

        val rootDir = baseDirProvider()
        val ext = if (currentStreamUrl.contains("aac", ignoreCase = true)) "aac" else "mp3"

        val file: File
        if (isCurrentShow) {
            val safeStation = sanitize(currentStationName.ifBlank { "Radio" })
            val targetDir = File(File(rootDir, AppConfig.Recording.DIR_SHOWS), safeStation)
            if (!targetDir.exists()) targetDir.mkdirs()

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(clock()))
            val baseName = "${safeStation}_$timestamp"
            var candidate = File(targetDir, "$baseName.$ext")
            var counter = 1
            while (candidate.exists()) {
                candidate = File(targetDir, "${baseName}_%02d.$ext".format(counter++))
            }
            file = candidate
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

            // Launch background metadata query
            val artistForQuery = currentArtist
            val titleForQuery = currentTitle
            enrichmentJob = coordinatorScope.launch {
                try {
                    val info = metadataFetcher.fetch(artistForQuery, titleForQuery)
                    currentEnrichedInfo = info
                } catch (e: Exception) {
                    currentEnrichedInfo = null
                }
            }
        }

        currentFile = file
        currentOutputStream = FileOutputStream(file)
    }

    private fun closeCurrentTrack() {
        val fileToFinalize = currentFile
        try {
            currentOutputStream?.flush()
            currentOutputStream?.close()
        } catch (e: Exception) {
            // Ignore close error
        } finally {
            currentOutputStream = null
            currentFile = null
        }

        if (fileToFinalize == null || !fileToFinalize.exists()) return

        // Await background enrichment with short timeout
        runBlocking {
            withTimeoutOrNull(AppConfig.Recording.ENRICHMENT_TIMEOUT_MS) {
                enrichmentJob?.join()
            }
        }
        enrichmentJob = null

        val rootDir = baseDirProvider()
        val ext = if (currentStreamUrl.contains("aac", ignoreCase = true)) "aac" else "mp3"
        val durationSec = Math.max(0L, (clock() - trackStartTimeMs) / 1000L)

        // Pruning filter: clips under minDurationSeconds (ads, jingles, glitches) are deleted
        if (durationSec < minDurationSeconds) {
            fileToFinalize.delete()
            val oldParent = fileToFinalize.parentFile
            if (oldParent != null && oldParent.listFiles().isNullOrEmpty()) {
                oldParent.delete()
            }
            return
        }

        completedTrackCount++
        val isoTimestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date(trackStartTimeMs))

        if (isCurrentShow) {
            val safeStation = sanitize(currentStationName.ifBlank { "Radio" })
            val sidecar = SidecarMetadata(
                title = currentTitle.ifBlank { "Otseeeter" },
                artist = currentArtist.ifBlank { safeStation },
                album = null,
                year = null,
                genre = null,
                trackNumber = null,
                lyrics = null,
                station = currentStationName,
                stationUrl = currentStreamUrl,
                recordedAt = isoTimestamp,
                durationSeconds = durationSec,
                fileSize = fileToFinalize.length(),
                codec = ext,
                bitrate = currentBitrate,
                audioFile = fileToFinalize.name,
                coverArtFile = null,
                partial = isPartialTrack
            )
            val jsonFile = File(fileToFinalize.parentFile, "${fileToFinalize.nameWithoutExtension}.json")
            jsonFile.writeText(json.encodeToString(sidecar))
        } else {
            val enriched = currentEnrichedInfo
            val discoveredAlbum = enriched?.album?.trim()?.ifBlank { null }
            val safeArtist = sanitize(currentArtist)

            var targetAudioFile = fileToFinalize
            var targetDir = fileToFinalize.parentFile ?: rootDir

            // If an album was discovered, move file to the correct album directory and recalculate track number
            if (discoveredAlbum != null && targetDir.name == AppConfig.Recording.DIR_UNKNOWN_ALBUM) {
                val safeAlbum = sanitize(discoveredAlbum)
                val newAlbumDir = File(File(File(rootDir, AppConfig.Recording.DIR_MUSIC), safeArtist), safeAlbum)
                if (!newAlbumDir.exists()) newAlbumDir.mkdirs()

                val existingInTarget = newAlbumDir.listFiles()?.mapNotNull { f ->
                    if (f.isFile && (f.name.endsWith(".mp3") || f.name.endsWith(".aac"))) {
                        Regex("""^(\d{2})\s*-""").find(f.name)?.groupValues?.get(1)?.toIntOrNull()
                    } else null
                } ?: emptyList()

                val nextNumInTarget = if (existingInTarget.isEmpty()) 1 else (existingInTarget.maxOrNull() ?: 0) + 1
                val targetTrackNumStr = "%02d".format(nextNumInTarget)
                val targetBaseName = "$targetTrackNumStr - $safeArtist - ${sanitize(currentTitle)}"
                var targetCandidate = File(newAlbumDir, "$targetBaseName.$ext")
                var dup = 1
                while (targetCandidate.exists()) {
                    targetCandidate = File(newAlbumDir, "${targetBaseName}_%02d.$ext".format(dup++))
                }

                if (fileToFinalize.renameTo(targetCandidate)) {
                    targetAudioFile = targetCandidate
                    val oldDir = fileToFinalize.parentFile
                    targetDir = newAlbumDir

                    // Clean up empty Teadmata album folder if empty
                    if (oldDir != null && oldDir.listFiles().isNullOrEmpty()) {
                        oldDir.delete()
                    }
                }
            }

            // Download cover art if present and not yet downloaded
            var coverArtFileName: String? = null
            if (!enriched?.coverArtUrl.isNullOrBlank()) {
                val coverFile = File(targetDir, "cover.jpg")
                if (!coverFile.exists()) {
                    runBlocking {
                        coverArtDownloader.download(enriched!!.coverArtUrl!!, coverFile)
                    }
                }
                if (coverFile.exists()) {
                    coverArtFileName = "cover.jpg"
                }
            }

            val sidecar = SidecarMetadata(
                title = currentTitle,
                artist = currentArtist,
                album = discoveredAlbum,
                year = enriched?.year,
                genre = enriched?.genre,
                trackNumber = Regex("""^(\d{2})\s*-""").find(targetAudioFile.name)?.groupValues?.get(1)?.toIntOrNull(),
                lyrics = enriched?.lyrics,
                station = currentStationName,
                stationUrl = currentStreamUrl,
                recordedAt = isoTimestamp,
                durationSeconds = durationSec,
                fileSize = targetAudioFile.length(),
                codec = ext,
                bitrate = currentBitrate,
                audioFile = targetAudioFile.name,
                coverArtFile = coverArtFileName,
                partial = isPartialTrack
            )

            val jsonFile = File(targetDir, "${targetAudioFile.nameWithoutExtension}.json")
            jsonFile.writeText(json.encodeToString(sidecar))
        }
    }
}
