package app.radiorecalarm

import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class StreamRecordingCoordinator(
    private val baseDirProvider: () -> File,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val metadataHelper: RadioMetadataHelper = RadioMetadataHelper()
) {
    @Volatile
    var isRecording: Boolean = false
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
    var currentAlbum: String = ""
        private set
    var isCurrentShow: Boolean = false
        private set

    private var currentOutputStream: OutputStream? = null
    var currentFile: File? = null
        private set

    private val lock = Any()

    fun startRecording(
        stationName: String,
        streamUrl: String,
        initialRawMetadata: String = ""
    ) {
        synchronized(lock) {
            if (isRecording) return

            currentStationName = stationName
            currentStreamUrl = streamUrl
            isRecording = true

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

        isCurrentShow = isShow(currentArtist, currentTitle, currentStationName)

        val rootDir = baseDirProvider()
        val ext = if (currentStreamUrl.contains("aac", ignoreCase = true)) "aac" else "mp3"

        val file: File
        if (isCurrentShow) {
            val safeStation = sanitize(currentStationName.ifBlank { "Radio" })
            val targetDir = File(File(rootDir, "Saated"), safeStation)
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
            val albumName = currentAlbum.ifBlank { "Teadmata album" }
            val safeAlbum = sanitize(albumName)
            val safeTitle = sanitize(currentTitle)

            val targetDir = File(File(File(rootDir, "Muusika"), safeArtist), safeAlbum)
            if (!targetDir.exists()) targetDir.mkdirs()

            // Find existing track numbers in this album directory to assign next 01, 02...
            val existingNumbers = targetDir.listFiles()?.mapNotNull { f ->
                if (f.isFile && (f.name.endsWith(".mp3") || f.name.endsWith(".aac"))) {
                    Regex("""^(\d{2})\s*-""").find(f.name)?.groupValues?.get(1)?.toIntOrNull()
                } else null
            } ?: emptyList()

            val nextTrackNum = if (existingNumbers.isEmpty()) 1 else (existingNumbers.maxOrNull() ?: 0) + 1

            // If this exact song (artist + title) was already recorded in this album, reuse its track number so duplicate suffix attaches to the same track
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
        }

        currentFile = file
        currentOutputStream = FileOutputStream(file)
    }

    private fun closeCurrentTrack() {
        try {
            currentOutputStream?.flush()
            currentOutputStream?.close()
        } catch (e: Exception) {
            // Ignore close error
        } finally {
            currentOutputStream = null
            currentFile = null
        }
    }
}
