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

    private var currentOutputStream: OutputStream? = null
    private var currentFile: File? = null

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

    private fun openNewTrack(rawMetadata: String) {
        val cleaned = metadataHelper.sanitizeRawInput(rawMetadata)
        currentRawMetadata = cleaned

        val parsed = metadataHelper.parse(rawMetadata, currentStationName)
        currentArtist = parsed.artist
        currentTitle = parsed.title

        val targetDir = baseDirProvider()
        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }

        val ext = if (currentStreamUrl.contains("aac", ignoreCase = true)) "aac" else "mp3"
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date(clock()))
        val safeStation = currentStationName.replace("[\\\\/:*?\"<>|]".toRegex(), "_")
        val safeArtist = currentArtist.replace("[\\\\/:*?\"<>|]".toRegex(), "_")
        val safeTitle = currentTitle.replace("[\\\\/:*?\"<>|]".toRegex(), "_")

        val fileName = if (safeArtist.isNotBlank() && safeTitle.isNotBlank()) {
            "${safeArtist}_${safeTitle}_$timestamp.$ext"
        } else {
            "${safeStation}_$timestamp.$ext"
        }

        val file = File(targetDir, fileName)
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

