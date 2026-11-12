package app.radiorecalarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class StreamRecordingCoordinatorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var baseDir: File
    private lateinit var coordinator: StreamRecordingCoordinator

    @Before
    fun setup() {
        baseDir = tempFolder.newFolder("recordings")
        coordinator = StreamRecordingCoordinator(
            baseDirProvider = { baseDir },
            clock = { 1000000L }
        )
    }

    @Test
    fun `starts recording and writes bytes to active file`() {
        assertFalse(coordinator.isRecording)

        coordinator.startRecording(
            stationName = "Rock FM",
            streamUrl = "https://icecast.err.ee/raadio2.mp3",
            initialRawMetadata = "Queen - Bohemian Rhapsody"
        )

        assertTrue(coordinator.isRecording)

        val bytes = "AudioChunk1".toByteArray()
        coordinator.onStreamBytes(bytes, 0, bytes.size)

        coordinator.stopRecording()
        assertFalse(coordinator.isRecording)

        val files = baseDir.walkTopDown().filter { it.isFile }.toList()
        assertEquals(1, files.size)
        val file = files.first()
        assertTrue(file.name.endsWith(".mp3"))
        assertEquals("AudioChunk1", file.readText())
    }

    @Test
    fun `splits file at exact byte boundary when metadata changes`() {
        coordinator.startRecording(
            stationName = "Rock FM",
            streamUrl = "https://icecast.err.ee/raadio2.mp3",
            initialRawMetadata = "Artist One - Track One"
        )

        val chunk1 = "ChunkOneContent".toByteArray()
        coordinator.onStreamBytes(chunk1, 0, chunk1.size)

        // Metadata change arrives from the stream
        coordinator.onMetadataChanged("Artist Two - Track Two")

        val chunk2 = "ChunkTwoContent".toByteArray()
        coordinator.onStreamBytes(chunk2, 0, chunk2.size)

        coordinator.stopRecording()

        val files = baseDir.walkTopDown().filter { it.isFile && it.name.endsWith(".mp3") }.sortedBy { it.name }.toList()
        assertEquals(2, files.size)
        assertEquals("ChunkOneContent", files[0].readText())
        assertEquals("ChunkTwoContent", files[1].readText())
    }

    @Test
    fun `does not split when metadata is unchanged or empty duplicates`() {
        coordinator.startRecording(
            stationName = "Rock FM",
            streamUrl = "https://icecast.err.ee/raadio2.mp3",
            initialRawMetadata = "Artist One - Track One"
        )

        coordinator.onStreamBytes("Part1".toByteArray(), 0, 5)
        // Repeat identical metadata
        coordinator.onMetadataChanged("Artist One - Track One")
        coordinator.onStreamBytes("Part2".toByteArray(), 0, 5)

        coordinator.stopRecording()

        val files = baseDir.walkTopDown().filter { it.isFile && it.name.endsWith(".mp3") }.toList()
        assertEquals(1, files.size)
        assertEquals("Part1Part2", files.first().readText())
    }

    @Test
    fun `correctly selects aac extension for aac streams`() {
        coordinator.startRecording(
            stationName = "Sky Plus",
            streamUrl = "https://stream.sky.ee/skyplus.aac",
            initialRawMetadata = "Artist - Track"
        )

        val chunk = "AacBytes".toByteArray()
        coordinator.onStreamBytes(chunk, 0, chunk.size)
        coordinator.stopRecording()

        val files = baseDir.walkTopDown().filter { it.isFile }.toList()
        assertEquals(1, files.size)
        assertTrue(files.first().name.endsWith(".aac"))
    }

    @Test
    fun `routes music tracks to Muusika folder with album and two-digit number`() {
        coordinator.startRecording(
            stationName = "Rock FM",
            streamUrl = "https://icecast.err.ee/raadio2.mp3",
            initialRawMetadata = "Queen - Bohemian Rhapsody"
        )
        val chunk = "QueenAudio".toByteArray()
        coordinator.onStreamBytes(chunk, 0, chunk.size)
        coordinator.stopRecording()

        val musicFiles = File(baseDir, "Muusika").walkTopDown().filter { it.isFile && it.name.endsWith(".mp3") }.toList()
        assertEquals(1, musicFiles.size)
        val file = musicFiles.first()
        // Should be inside Muusika/Queen/Teadmata album/
        assertTrue(file.parentFile?.path?.contains("Muusika") == true)
        assertTrue(file.parentFile?.path?.contains("Queen") == true)
        assertTrue(file.parentFile?.path?.contains("Teadmata album") == true)
        assertEquals("01 - Queen - Bohemian Rhapsody.mp3", file.name)
        assertEquals("QueenAudio", file.readText())
    }

    @Test
    fun `routes broadcast to Saated folder when metadata is live show or station name`() {
        coordinator.startRecording(
            stationName = "Vikerraadio",
            streamUrl = "https://icecast.err.ee/vikerraadio.mp3",
            initialRawMetadata = "Otseeeter"
        )
        val chunk = "TalkShowAudio".toByteArray()
        coordinator.onStreamBytes(chunk, 0, chunk.size)
        coordinator.stopRecording()

        val showFiles = File(baseDir, "Saated").walkTopDown().filter { it.isFile && it.name.endsWith(".mp3") }.toList()
        assertEquals(1, showFiles.size)
        val file = showFiles.first()
        assertTrue(file.parentFile?.path?.contains("Saated") == true)
        assertTrue(file.parentFile?.path?.contains("Vikerraadio") == true)
        assertTrue(file.name.startsWith("Vikerraadio_"))
        assertEquals("TalkShowAudio", file.readText())
    }

    @Test
    fun `increments two-digit track number for subsequent tracks in the same album`() {
        coordinator.startRecording(
            stationName = "Rock FM",
            streamUrl = "https://icecast.err.ee/raadio2.mp3",
            initialRawMetadata = "Queen - Bohemian Rhapsody"
        )
        coordinator.onStreamBytes("Audio1".toByteArray(), 0, 6)

        coordinator.onMetadataChanged("Queen - Love of My Life")
        coordinator.onStreamBytes("Audio2".toByteArray(), 0, 6)

        coordinator.stopRecording()

        val albumDir = File(baseDir, "Muusika/Queen/Teadmata album")
        val files = albumDir.listFiles()?.filter { it.isFile && it.name.endsWith(".mp3") }?.sortedBy { it.name } ?: emptyList()

        assertEquals(2, files.size)
        assertEquals("01 - Queen - Bohemian Rhapsody.mp3", files[0].name)
        assertEquals("02 - Queen - Love of My Life.mp3", files[1].name)
    }

    @Test
    fun `appends duplicate index when identical track is recorded again`() {
        coordinator.startRecording(
            stationName = "Rock FM",
            streamUrl = "https://icecast.err.ee/raadio2.mp3",
            initialRawMetadata = "Queen - Bohemian Rhapsody"
        )
        coordinator.onStreamBytes("Audio1".toByteArray(), 0, 6)

        // Another song played
        coordinator.onMetadataChanged("Metallica - Enter Sandman")
        coordinator.onStreamBytes("Audio2".toByteArray(), 0, 6)

        // Bohemian Rhapsody played again
        coordinator.onMetadataChanged("Queen - Bohemian Rhapsody")
        coordinator.onStreamBytes("Audio3".toByteArray(), 0, 6)

        coordinator.stopRecording()

        val albumDir = File(baseDir, "Muusika/Queen/Teadmata album")
        val files = albumDir.listFiles()?.filter { it.isFile && it.name.endsWith(".mp3") }?.sortedBy { it.name } ?: emptyList()

        assertEquals(2, files.size)
        assertEquals("01 - Queen - Bohemian Rhapsody.mp3", files[0].name)
        assertEquals("01 - Queen - Bohemian Rhapsody_01.mp3", files[1].name)
    }
}

