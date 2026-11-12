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
}

