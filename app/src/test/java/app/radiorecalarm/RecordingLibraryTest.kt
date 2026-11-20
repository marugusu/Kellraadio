package app.radiorecalarm

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class RecordingLibraryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val json = Json { prettyPrint = true; encodeDefaults = true }

    @Test
    fun `scanRecordings discovers Music Broadcasts and legacy root files with sidecar and cover`() {
        val root = tempFolder.newFolder("Recordings")

        // 1. Track with sidecar + cover.jpg
        val albumDir = File(root, "Music/Above My Head/Above My Head").apply { mkdirs() }
        val trackFile = File(albumDir, "01 - Above My Head - No Empathy.mp3").apply {
            writeBytes(ByteArray(1024) { 1 })
        }
        val coverFile = File(albumDir, "cover.jpg").apply { writeBytes(ByteArray(64) { 2 }) }
        val trackSidecar = SidecarMetadata(
            title = "No Empathy",
            artist = "Above My Head",
            album = "Above My Head",
            year = "2024",
            genre = "Metal",
            trackNumber = 1,
            lyrics = "Sample lyrics",
            station = "Rock FM",
            stationUrl = "https://example.com/rockfm",
            recordedAt = "2026-11-20T07:11:00.000Z",
            durationSeconds = 215L,
            fileSize = 1024L,
            codec = "mp3",
            bitrate = "320 kbps",
            audioFile = trackFile.name,
            coverArtFile = "cover.jpg",
            partial = true
        )
        File(albumDir, "01 - Above My Head - No Empathy.json").writeText(json.encodeToString(trackSidecar))

        // 2. Broadcast with sidecar
        val broadcastDir = File(root, "Broadcasts/Vikerraadio").apply { mkdirs() }
        val broadcastFile = File(broadcastDir, "Vikerraadio_20261120_080000.aac").apply {
            writeBytes(ByteArray(2048) { 3 })
        }
        val broadcastSidecar = SidecarMetadata(
            title = "Vikerhommik",
            artist = "Saade",
            station = "Vikerraadio",
            stationUrl = "https://example.com/viker",
            recordedAt = "2026-11-20T06:00:00.000Z",
            durationSeconds = 1800L,
            fileSize = 2048L,
            codec = "aac",
            audioFile = broadcastFile.name
        )
        File(broadcastDir, "Vikerraadio_20261120_080000.json").writeText(json.encodeToString(broadcastSidecar))

        // 3. Legacy root recording without sidecar
        val legacyFile = File(root, "Recording_Retro_FM_20261119_213000.mp3").apply {
            writeBytes(ByteArray(512) { 4 })
        }

        // 4. Zero-byte and excluded active file should be skipped
        File(albumDir, "02 - Empty.mp3").writeBytes(ByteArray(0))
        val activeFile = File(albumDir, "03 - Active - Recording.mp3").apply {
            writeBytes(ByteArray(256) { 5 })
        }

        val entries = RecordingLibrary.scanRecordings(root, excludeFilePath = activeFile.absolutePath)

        assertEquals(3, entries.size)
        // Sorted newest first: track (07:11Z), broadcast (06:00Z), legacy (2026-11-19)
        assertEquals("No Empathy", entries[0].title)
        assertEquals(RecordingKind.TRACK, entries[0].kind)
        assertEquals("Rock FM", entries[0].stationName)
        assertTrue(entries[0].partial)
        assertEquals(coverFile.absolutePath, entries[0].coverArtFile?.absolutePath)
        assertEquals("Sample lyrics", entries[0].lyrics)

        assertEquals("Vikerhommik", entries[1].title)
        assertEquals(RecordingKind.BROADCAST, entries[1].kind)
        assertEquals(1800L, entries[1].durationSeconds)

        assertEquals("Retro FM", entries[2].title)
        assertEquals(RecordingKind.BROADCAST, entries[2].kind)
        assertFalse(entries[2].hasSidecar)
    }

    @Test
    fun `filterRecordings filters by kind and search query`() {
        val track = RecordingEntry(
            audioFile = File("t.mp3"),
            sidecarFile = null,
            coverArtFile = null,
            kind = RecordingKind.TRACK,
            title = "Kings of Metal",
            artist = "Manowar",
            album = "Kings of Metal",
            stationName = "Rock FM",
            timestampMs = 1_700_000_000_000L
        )
        val show = RecordingEntry(
            audioFile = File("s.aac"),
            sidecarFile = null,
            coverArtFile = null,
            kind = RecordingKind.BROADCAST,
            title = "Vikerhommik",
            artist = "Saade",
            stationName = "Vikerraadio",
            timestampMs = 1_700_000_000_000L
        )

        val tracksOnly = RecordingLibrary.filterRecordings(
            listOf(track, show),
            kindFilter = RecordingFilter.TRACKS,
            query = "",
            selectedUtcDateMillis = null
        )
        assertEquals(listOf(track), tracksOnly)

        val byStation = RecordingLibrary.filterRecordings(
            listOf(track, show),
            kindFilter = RecordingFilter.ALL,
            query = "viker",
            selectedUtcDateMillis = null
        )
        assertEquals(listOf(show), byStation)
    }

    @Test
    fun `deleteRecordingWithCleanup keeps shared cover while sibling exists and prunes when empty`() {
        val root = tempFolder.newFolder("Recordings")
        val albumDir = File(root, "Music/Artist/Album").apply { mkdirs() }
        val file1 = File(albumDir, "01 - Artist - One.mp3").apply { writeBytes(ByteArray(100) { 1 }) }
        val json1 = File(albumDir, "01 - Artist - One.json").apply { writeText("{}") }
        val file2 = File(albumDir, "02 - Artist - Two.mp3").apply { writeBytes(ByteArray(100) { 2 }) }
        val json2 = File(albumDir, "02 - Artist - Two.json").apply { writeText("{}") }
        val cover = File(albumDir, "cover.jpg").apply { writeBytes(ByteArray(32) { 3 }) }

        // Delete first track -> cover.jpg and file2 must remain
        assertTrue(RecordingLibrary.deleteRecordingWithCleanup(file1, root))
        assertFalse(file1.exists())
        assertFalse(json1.exists())
        assertTrue(file2.exists())
        assertTrue(cover.exists())
        assertTrue(albumDir.exists())

        // Delete last track -> cover.jpg and empty Album/Artist/Music dirs are pruned, root stays
        assertTrue(RecordingLibrary.deleteRecordingWithCleanup(file2, root))
        assertFalse(file2.exists())
        assertFalse(json2.exists())
        assertFalse(cover.exists())
        assertFalse(albumDir.exists())
        assertTrue(root.exists())
    }

    @Test
    fun `toSongAdditionalInfo maps local cover URI and sidecar fields`() {
        val root = tempFolder.newFolder("Recordings")
        val cover = File(root, "cover.jpg").apply { writeBytes(ByteArray(16) { 1 }) }
        val entry = RecordingEntry(
            audioFile = File(root, "song.mp3"),
            sidecarFile = null,
            coverArtFile = cover,
            kind = RecordingKind.TRACK,
            title = "Title",
            artist = "Artist",
            album = "Album",
            year = "2025",
            genre = "Rock",
            trackNumber = 3,
            lyrics = "La la la"
        )

        val info = RecordingLibrary.toSongAdditionalInfo(entry)
        assertNotNull(info.coverArtUrl)
        assertTrue(info.coverArtUrl!!.startsWith("file:"))
        assertEquals("Album", info.album)
        assertEquals("2025", info.year)
        assertEquals("Rock", info.genre)
        assertEquals(3, info.trackNumber)
        assertEquals("La la la", info.lyrics)

        val noCoverInfo = RecordingLibrary.toSongAdditionalInfo(entry.copy(coverArtFile = null))
        assertNull(noCoverInfo.coverArtUrl)
    }
}
