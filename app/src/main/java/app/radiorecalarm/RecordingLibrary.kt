package app.radiorecalarm

import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

enum class RecordingKind {
    TRACK,
    BROADCAST
}

enum class RecordingFilter {
    ALL,
    TRACKS,
    BROADCASTS
}

data class RecordingEntry(
    val audioFile: File,
    val sidecarFile: File?,
    val coverArtFile: File?,
    val kind: RecordingKind,
    val title: String,
    val artist: String,
    val album: String? = null,
    val year: String? = null,
    val genre: String? = null,
    val trackNumber: Int? = null,
    val lyrics: String? = null,
    val stationName: String = "",
    val bitrate: String? = null,
    val codec: String = "",
    val durationSeconds: Long = 0L,
    val fileSizeBytes: Long = 0L,
    val timestampMs: Long = 0L,
    val partial: Boolean = false,
    val hasSidecar: Boolean = false
)

/**
 * Pure filesystem scanner, metadata mapper, filter, and cleanup helper for Kellraadio recordings.
 */
object RecordingLibrary {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private val audioExtensions = setOf("mp3", "aac")
    private val filenameTimestampRegex = Regex("""(\d{8}_\d{6})""")
    private val numberedTrackRegex = Regex("""^\d{2}\s*-\s*(.+?)\s*-\s*(.+)$""")

    fun isAudioFile(file: File): Boolean =
        file.isFile && file.extension.lowercase(Locale.US) in audioExtensions

    fun sidecarFileFor(audioFile: File): File =
        File(audioFile.parentFile, "${audioFile.nameWithoutExtension}.json")

    fun loadSidecarForFile(audioFile: File): SidecarMetadata? {
        val sidecarFile = sidecarFileFor(audioFile)
        if (!sidecarFile.isFile) return null
        return try {
            json.decodeFromString<SidecarMetadata>(sidecarFile.readText())
        } catch (_: Exception) {
            null
        }
    }

    fun scanRecordings(rootDir: File, excludeFilePath: String? = null): List<RecordingEntry> {
        if (!rootDir.exists() || !rootDir.isDirectory) return emptyList()

        val excludedCanonical = excludeFilePath?.let { safeCanonicalPath(File(it)) }
        val musicRootCanonical = safeCanonicalPath(File(rootDir, AppConfig.Recording.DIR_MUSIC))
        val broadcastsRootCanonical = safeCanonicalPath(File(rootDir, AppConfig.Recording.DIR_BROADCASTS))

        return rootDir.walkTopDown()
            .filter { file ->
                isAudioFile(file) &&
                    file.length() > 0L &&
                    (excludedCanonical == null || safeCanonicalPath(file) != excludedCanonical)
            }
            .map { file -> buildEntry(file, musicRootCanonical, broadcastsRootCanonical) }
            .sortedByDescending { it.timestampMs }
            .toList()
    }

    private fun buildEntry(
        audioFile: File,
        musicRootCanonical: String,
        broadcastsRootCanonical: String
    ): RecordingEntry {
        val sidecarFile = sidecarFileFor(audioFile).takeIf { it.isFile }
        val sidecar = if (sidecarFile != null) loadSidecarForFile(audioFile) else null

        val canonical = safeCanonicalPath(audioFile)
        val kind = when {
            canonical.startsWith(broadcastsRootCanonical + File.separator) -> RecordingKind.BROADCAST
            canonical.startsWith(musicRootCanonical + File.separator) -> RecordingKind.TRACK
            sidecar != null && (sidecar.durationSeconds > AppConfig.Recording.MAX_TRACK_DURATION_SECONDS ||
                sidecar.artist.equals("Saade", ignoreCase = true) ||
                sidecar.artist.equals("Otseeeter", ignoreCase = true)) -> RecordingKind.BROADCAST
            sidecar == null && audioFile.name.startsWith("Recording_") -> RecordingKind.BROADCAST
            else -> RecordingKind.TRACK
        }

        val coverFile = resolveCoverArtFile(audioFile, sidecar)
        val fallback = parseFallbackFromFilename(audioFile, kind)

        val title = sidecar?.title?.takeIf { it.isNotBlank() } ?: fallback.title
        val artist = sidecar?.artist?.takeIf { it.isNotBlank() } ?: fallback.artist
        val stationName = sidecar?.station?.takeIf { it.isNotBlank() } ?: fallback.stationName
        val timestampMs = parseIsoUtcMillis(sidecar?.recordedAt)
            ?: parseFilenameTimestampMillis(audioFile.name)
            ?: audioFile.lastModified()

        return RecordingEntry(
            audioFile = audioFile,
            sidecarFile = sidecarFile,
            coverArtFile = coverFile,
            kind = kind,
            title = title,
            artist = artist,
            album = sidecar?.album?.takeIf { it.isNotBlank() },
            year = sidecar?.year?.takeIf { it.isNotBlank() },
            genre = sidecar?.genre?.takeIf { it.isNotBlank() },
            trackNumber = sidecar?.trackNumber,
            lyrics = sidecar?.lyrics?.takeIf { it.isNotBlank() },
            stationName = stationName,
            bitrate = sidecar?.bitrate?.takeIf { it.isNotBlank() },
            codec = sidecar?.codec?.takeIf { it.isNotBlank() } ?: audioFile.extension.lowercase(Locale.US),
            durationSeconds = sidecar?.durationSeconds ?: 0L,
            fileSizeBytes = sidecar?.fileSize?.takeIf { it > 0L } ?: audioFile.length(),
            timestampMs = timestampMs,
            partial = sidecar?.partial ?: false,
            hasSidecar = sidecar != null
        )
    }

    private fun resolveCoverArtFile(audioFile: File, sidecar: SidecarMetadata?): File? {
        val parent = audioFile.parentFile ?: return null
        val fromSidecar = sidecar?.coverArtFile
            ?.takeIf { it.isNotBlank() }
            ?.let { File(parent, it) }
            ?.takeIf { it.isFile }
        if (fromSidecar != null) return fromSidecar
        return File(parent, "cover.jpg").takeIf { it.isFile }
    }

    private data class FilenameFallback(
        val title: String,
        val artist: String,
        val stationName: String
    )

    private fun parseFallbackFromFilename(audioFile: File, kind: RecordingKind): FilenameFallback {
        val rawName = audioFile.nameWithoutExtension
        val numberedMatch = numberedTrackRegex.find(rawName)
        if (numberedMatch != null) {
            return FilenameFallback(
                title = numberedMatch.groupValues[2].trim(),
                artist = numberedMatch.groupValues[1].trim(),
                stationName = ""
            )
        }

        val withoutPrefix = rawName.removePrefix("Recording_")
        val tsMatch = filenameTimestampRegex.find(withoutPrefix)
        val cleanBase = if (tsMatch != null && tsMatch.range.first > 0) {
            withoutPrefix.substring(0, tsMatch.range.first).trimEnd('_', ' ').replace('_', ' ')
        } else {
            withoutPrefix.replace('_', ' ')
        }

        return if (kind == RecordingKind.BROADCAST) {
            FilenameFallback(
                title = cleanBase.ifBlank { rawName },
                artist = "",
                stationName = cleanBase
            )
        } else {
            FilenameFallback(
                title = cleanBase.ifBlank { rawName },
                artist = "",
                stationName = ""
            )
        }
    }

    fun filterRecordings(
        entries: List<RecordingEntry>,
        kindFilter: RecordingFilter,
        query: String,
        selectedUtcDateMillis: Long?
    ): List<RecordingEntry> {
        val trimmedQuery = query.trim().lowercase(Locale.getDefault())
        val dayRange = selectedUtcDateMillis?.let { localDayBoundsFromUtcSelection(it) }

        return entries.filter { entry ->
            val matchesKind = when (kindFilter) {
                RecordingFilter.ALL -> true
                RecordingFilter.TRACKS -> entry.kind == RecordingKind.TRACK
                RecordingFilter.BROADCASTS -> entry.kind == RecordingKind.BROADCAST
            }
            if (!matchesKind) return@filter false

            if (dayRange != null && entry.timestampMs !in dayRange.first..dayRange.second) {
                return@filter false
            }

            if (trimmedQuery.isNotEmpty()) {
                val matchesText = entry.title.lowercase(Locale.getDefault()).contains(trimmedQuery) ||
                    entry.artist.lowercase(Locale.getDefault()).contains(trimmedQuery) ||
                    (entry.album?.lowercase(Locale.getDefault())?.contains(trimmedQuery) == true) ||
                    entry.stationName.lowercase(Locale.getDefault()).contains(trimmedQuery)
                if (!matchesText) return@filter false
            }

            true
        }
    }

    fun localDayBoundsFromUtcSelection(utcTimeMillis: Long): Pair<Long, Long> {
        val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = utcTimeMillis
        }
        val localCal = Calendar.getInstance().apply {
            set(
                utcCal.get(Calendar.YEAR),
                utcCal.get(Calendar.MONTH),
                utcCal.get(Calendar.DAY_OF_MONTH),
                0,
                0,
                0
            )
            set(Calendar.MILLISECOND, 0)
        }
        val startOfDay = localCal.timeInMillis
        localCal.set(Calendar.HOUR_OF_DAY, 23)
        localCal.set(Calendar.MINUTE, 59)
        localCal.set(Calendar.SECOND, 59)
        localCal.set(Calendar.MILLISECOND, 999)
        return startOfDay to localCal.timeInMillis
    }

    fun toSongAdditionalInfo(entry: RecordingEntry): SongAdditionalInfo = SongAdditionalInfo(
        lyrics = entry.lyrics,
        coverArtUrl = entry.coverArtFile?.toURI()?.toString(),
        album = entry.album,
        year = entry.year,
        genre = entry.genre,
        trackNumber = entry.trackNumber
    )

    /**
     * Deletes [audioFile] and its sibling `.json` sidecar. If no other audio files remain in the
     * parent directory (inside [rootDir]), also removes `cover.jpg` and prunes empty directories
     * up to [rootDir].
     */
    fun deleteRecordingWithCleanup(audioFile: File, rootDir: File): Boolean {
        val parentDir = audioFile.parentFile
        val deletedAudio = if (audioFile.exists()) audioFile.delete() else false

        val sidecarFile = sidecarFileFor(audioFile)
        if (sidecarFile.exists()) {
            sidecarFile.delete()
        }

        val rootCanonical = safeCanonicalPath(rootDir)
        if (parentDir != null && parentDir.exists() && safeCanonicalPath(parentDir) != rootCanonical) {
            val remainingAudio = parentDir.listFiles()?.any { isAudioFile(it) } == true
            if (!remainingAudio) {
                val coverFile = File(parentDir, "cover.jpg")
                if (coverFile.isFile) {
                    coverFile.delete()
                }
            }
            cleanupEmptyDirs(parentDir, rootCanonical)
        }

        return deletedAudio
    }

    private fun cleanupEmptyDirs(startDir: File?, rootCanonical: String) {
        var dir = startDir
        while (dir != null && safeCanonicalPath(dir) != rootCanonical) {
            if (!dir.exists() || !dir.isDirectory || !dir.listFiles().isNullOrEmpty()) {
                break
            }
            val parent = dir.parentFile
            if (!dir.delete()) break
            dir = parent
        }
    }

    private fun safeCanonicalPath(file: File): String = try {
        file.canonicalPath
    } catch (_: Exception) {
        file.absolutePath
    }

    private fun parseIsoUtcMillis(iso: String?): Long? {
        if (iso.isNullOrBlank()) return null
        val patterns = listOf("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", "yyyy-MM-dd'T'HH:mm:ss'Z'")
        for (pattern in patterns) {
            try {
                val fmt = SimpleDateFormat(pattern, Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                val parsed = fmt.parse(iso)
                if (parsed != null) return parsed.time
            } catch (_: Exception) {
                // Try next pattern
            }
        }
        return null
    }

    private fun parseFilenameTimestampMillis(fileName: String): Long? {
        val raw = filenameTimestampRegex.find(fileName)?.groupValues?.get(1) ?: return null
        return try {
            SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).parse(raw)?.time
        } catch (_: Exception) {
            null
        }
    }
}
