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
            clock = { 1000000L },
            minDurationSeconds = 0L
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

        val files = baseDir.walkTopDown().filter { it.isFile && it.name.endsWith(".mp3") }.toList()
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

        val files = baseDir.walkTopDown().filter { it.isFile && it.name.endsWith(".aac") }.toList()
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
    fun `skips and discards duplicate track when identical complete track is recorded again`() {
        // Start mid-song with Metallica so that subsequent tracks are complete
        coordinator.startRecording(
            stationName = "Rock FM",
            streamUrl = "https://icecast.err.ee/raadio2.mp3",
            initialRawMetadata = "Metallica - Enter Sandman"
        )
        coordinator.onStreamBytes("MetChunk".toByteArray(), 0, 8)

        // Queen begins cleanly at boundary (complete track)
        coordinator.onMetadataChanged("Queen - Bohemian Rhapsody")
        coordinator.onStreamBytes("Audio1".toByteArray(), 0, 6)

        // Transition to next song (finalizes Queen as complete)
        coordinator.onMetadataChanged("ACDC - Back in Black")
        coordinator.onStreamBytes("Audio2".toByteArray(), 0, 6)

        // Queen played again at boundary (complete duplicate)
        coordinator.onMetadataChanged("Queen - Bohemian Rhapsody")
        coordinator.onStreamBytes("Audio3".toByteArray(), 0, 6)

        coordinator.stopRecording()

        val albumDir = File(baseDir, "Muusika/Queen/Teadmata album")
        val files = albumDir.listFiles()?.filter { it.isFile && it.name.endsWith(".mp3") }?.sortedBy { it.name } ?: emptyList()

        // Smart duplicate protection should discard the second copy
        assertEquals(1, files.size)
        assertEquals("01 - Queen - Bohemian Rhapsody.mp3", files[0].name)
    }

    @Test
    fun `replaces partial track when complete track of the same song is recorded later`() {
        // Start mid-song with Queen (partial track)
        coordinator.startRecording(
            stationName = "Rock FM",
            streamUrl = "https://icecast.err.ee/raadio2.mp3",
            initialRawMetadata = "Queen - Bohemian Rhapsody"
        )
        coordinator.onStreamBytes("PartialAudio".toByteArray(), 0, 12)

        // Transition to next song (Queen partial track is finalized with partial=true)
        coordinator.onMetadataChanged("Metallica - Enter Sandman")
        coordinator.onStreamBytes("MetAudio".toByteArray(), 0, 8)

        val albumDir = File(baseDir, "Muusika/Queen/Teadmata album")
        val jsonBefore = File(albumDir, "01 - Queen - Bohemian Rhapsody.json")
        assertTrue(jsonBefore.exists())
        assertTrue(jsonBefore.readText().contains("\"partial\": true"))

        // Queen plays again at boundary (complete track)
        coordinator.onMetadataChanged("Queen - Bohemian Rhapsody")
        coordinator.onStreamBytes("CompleteAudio".toByteArray(), 0, 13)

        coordinator.stopRecording()

        val audioFiles = albumDir.listFiles()?.filter { it.isFile && it.name.endsWith(".mp3") } ?: emptyList()
        assertEquals(1, audioFiles.size)
        assertEquals("01 - Queen - Bohemian Rhapsody.mp3", audioFiles[0].name)
        assertEquals("CompleteAudio", audioFiles[0].readText())

        val jsonAfter = File(albumDir, "01 - Queen - Bohemian Rhapsody.json")
        assertTrue(jsonAfter.exists())
        assertTrue(jsonAfter.readText().contains("\"partial\": false"))
    }

    @Test
    fun `uses official trackNumber from metadata fetcher when available`() {
        val fakeFetcher = MetadataFetcher { artist, title ->
            SongAdditionalInfo(
                album = "A Night at the Opera",
                year = "1975",
                genre = "Classic Rock",
                trackNumber = 11,
                coverArtUrl = "https://example.com/cover.jpg"
            )
        }
        val fakeDownloader = CoverArtDownloader { _, targetFile ->
            targetFile.writeText("fake-jpeg")
            true
        }

        coordinator = StreamRecordingCoordinator(
            baseDirProvider = { baseDir },
            clock = { 1000000L },
            metadataFetcher = fakeFetcher,
            coverArtDownloader = fakeDownloader,
            minDurationSeconds = 0L
        )

        coordinator.startRecording(
            stationName = "Rock FM",
            streamUrl = "https://icecast.err.ee/raadio2.mp3",
            initialRawMetadata = "Queen - Bohemian Rhapsody"
        )
        coordinator.onStreamBytes("TrackBytes".toByteArray(), 0, 10)
        coordinator.stopRecording()

        val albumDir = File(baseDir, "Muusika/Queen/A Night at the Opera")
        assertTrue(albumDir.exists())

        val audioFile = File(albumDir, "11 - Queen - Bohemian Rhapsody.mp3")
        assertTrue("Expected 11 - Queen - Bohemian Rhapsody.mp3 to exist", audioFile.exists())

        val jsonFile = File(albumDir, "11 - Queen - Bohemian Rhapsody.json")
        assertTrue(jsonFile.exists())
        assertTrue(jsonFile.readText().contains("\"trackNumber\": 11"))
    }

    @Test
    fun `generates JSON sidecar with enriched metadata and downloads cover art`() {
        val fakeFetcher = MetadataFetcher { artist, title ->
            SongAdditionalInfo(
                album = "A Night at the Opera",
                year = "1975",
                genre = "Classic Rock",
                lyrics = "Is this the real life?",
                coverArtUrl = "https://example.com/cover.jpg"
            )
        }
        val fakeDownloader = CoverArtDownloader { url, targetFile ->
            targetFile.writeText("fake-jpeg-bytes")
            true
        }

        coordinator = StreamRecordingCoordinator(
            baseDirProvider = { baseDir },
            clock = { 1000000L },
            metadataFetcher = fakeFetcher,
            coverArtDownloader = fakeDownloader,
            minDurationSeconds = 0L
        )

        coordinator.startRecording(
            stationName = "Rock FM",
            streamUrl = "https://icecast.err.ee/raadio2.mp3",
            initialRawMetadata = "Queen - Bohemian Rhapsody"
        )
        val chunk = "QueenAudioStreamBytes".toByteArray()
        coordinator.onStreamBytes(chunk, 0, chunk.size)
        coordinator.stopRecording()

        val albumDir = File(baseDir, "Muusika/Queen/A Night at the Opera")
        assertTrue(albumDir.exists())

        val audioFile = File(albumDir, "01 - Queen - Bohemian Rhapsody.mp3")
        assertTrue(audioFile.exists())
        assertEquals("QueenAudioStreamBytes", audioFile.readText())

        val jsonFile = File(albumDir, "01 - Queen - Bohemian Rhapsody.json")
        assertTrue(jsonFile.exists())
        val jsonContent = jsonFile.readText()
        assertTrue(jsonContent.contains("\"title\": \"Bohemian Rhapsody\""))
        assertTrue(jsonContent.contains("\"artist\": \"Queen\""))
        assertTrue(jsonContent.contains("\"album\": \"A Night at the Opera\""))
        assertTrue(jsonContent.contains("\"year\": \"1975\""))
        assertTrue(jsonContent.contains("\"genre\": \"Classic Rock\""))
        assertTrue(jsonContent.contains("\"lyrics\": \"Is this the real life?\""))
        assertTrue(jsonContent.contains("\"codec\": \"mp3\""))
        assertTrue(jsonContent.contains("\"coverArtFile\": \"cover.jpg\""))

        val coverFile = File(albumDir, "cover.jpg")
        assertTrue(coverFile.exists())
        assertEquals("fake-jpeg-bytes", coverFile.readText())
    }

    @Test
    fun `writes null fields in JSON sidecar when metadata fetch returns null`() {
        coordinator = StreamRecordingCoordinator(
            baseDirProvider = { baseDir },
            clock = { 1000000L },
            metadataFetcher = { _, _ -> null },
            minDurationSeconds = 0L
        )

        coordinator.startRecording(
            stationName = "Raadio 2",
            streamUrl = "https://icecast.err.ee/raadio2.mp3",
            initialRawMetadata = "Tundmatu Esitaja - Tundmatu Lugu"
        )
        val chunk = "SomeBytes".toByteArray()
        coordinator.onStreamBytes(chunk, 0, chunk.size)
        coordinator.stopRecording()

        val targetDir = File(baseDir, "Muusika/Tundmatu Esitaja/Teadmata album")
        val jsonFile = File(targetDir, "01 - Tundmatu Esitaja - Tundmatu Lugu.json")
        assertTrue(jsonFile.exists())
        val jsonContent = jsonFile.readText()
        assertTrue(jsonContent.contains("\"album\": null"))
        assertTrue(jsonContent.contains("\"year\": null"))
        assertTrue(jsonContent.contains("\"coverArtFile\": null"))
    }

    @Test
    fun `prunes and deletes tracks lasting less than 30 seconds`() {
        var currentTime = 1000000L
        coordinator = StreamRecordingCoordinator(
            baseDirProvider = { baseDir },
            clock = { currentTime }
        )

        coordinator.startRecording(
            stationName = "Rock FM",
            streamUrl = "https://icecast.err.ee/raadio2.mp3",
            initialRawMetadata = "Station - Jingle"
        )
        coordinator.onStreamBytes("ShortJingleBytes".toByteArray(), 0, 16)

        // Advance time by only 10 seconds (10s < 30s)
        currentTime += 10000L

        coordinator.stopRecording()

        val files = baseDir.walkTopDown().filter { it.isFile }.toList()
        assertEquals(0, files.size)
    }

    @Test
    fun `preserves tracks lasting 30 seconds or longer`() {
        var currentTime = 1000000L
        coordinator = StreamRecordingCoordinator(
            baseDirProvider = { baseDir },
            clock = { currentTime }
        )

        coordinator.startRecording(
            stationName = "Rock FM",
            streamUrl = "https://icecast.err.ee/raadio2.mp3",
            initialRawMetadata = "Queen - Bohemian Rhapsody"
        )
        coordinator.onStreamBytes("AudioBytes".toByteArray(), 0, 10)

        // Advance time by 35 seconds (35s >= 30s)
        currentTime += 35000L

        coordinator.stopRecording()

        val audioFiles = baseDir.walkTopDown().filter { it.isFile && it.name.endsWith(".mp3") }.toList()
        assertEquals(1, audioFiles.size)
        val jsonFiles = baseDir.walkTopDown().filter { it.isFile && it.name.endsWith(".json") }.toList()
        assertEquals(1, jsonFiles.size)
        assertTrue(jsonFiles.first().readText().contains("\"durationSeconds\": 35"))
    }

    @Test
    fun `flags mid-stream started track as partial and subsequent track as non-partial`() {
        var currentTime = 1000000L
        coordinator = StreamRecordingCoordinator(
            baseDirProvider = { baseDir },
            clock = { currentTime }
        )

        // Mid-stream start
        coordinator.startRecording(
            stationName = "Rock FM",
            streamUrl = "https://icecast.err.ee/raadio2.mp3",
            initialRawMetadata = "Artist 1 - Track 1"
        )
        coordinator.onStreamBytes("Audio1".toByteArray(), 0, 6)
        currentTime += 40000L // 40s duration

        // Transition to next track at boundary
        coordinator.onMetadataChanged("Artist 2 - Track 2")
        coordinator.onStreamBytes("Audio2".toByteArray(), 0, 6)
        currentTime += 50000L // 50s duration

        coordinator.stopRecording()

        val jsonFiles = baseDir.walkTopDown().filter { it.isFile && it.name.endsWith(".json") }.sortedBy { it.name }.toList()
        assertEquals(2, jsonFiles.size)

        val json1 = jsonFiles[0].readText()
        assertTrue(json1.contains("\"partial\": true"))

        val json2 = jsonFiles[1].readText()
        assertTrue(json2.contains("\"partial\": false"))
    }

    @Test
    fun `continues recording to same file across reconnect without metadata change`() {
        var currentTime = 1000000L
        coordinator = StreamRecordingCoordinator(
            baseDirProvider = { baseDir },
            clock = { currentTime }
        )

        coordinator.startRecording(
            stationName = "Rock FM",
            streamUrl = "https://icecast.err.ee/raadio2.mp3",
            initialRawMetadata = "Queen - Bohemian Rhapsody"
        )
        coordinator.onStreamBytes("Part1".toByteArray(), 0, 5)

        // Reconnect notification with same metadata
        currentTime += 2000L
        coordinator.onMetadataChanged("Queen - Bohemian Rhapsody")

        coordinator.onStreamBytes("Part2".toByteArray(), 0, 5)
        currentTime += 35000L

        coordinator.stopRecording()

        val audioFiles = baseDir.walkTopDown().filter { it.isFile && it.name.endsWith(".mp3") }.toList()
        assertEquals(1, audioFiles.size)
        assertEquals("Part1Part2", audioFiles.first().readText())
    }

    @Test
    fun `refuses to start recording and triggers onLowStorage when storage is below 500 MB`() {
        var lowStorageTriggered = false
        val lowSpaceCoordinator = StreamRecordingCoordinator(
            baseDirProvider = { baseDir },
            clock = { 1000000L },
            minDurationSeconds = 0L,
            storageSpaceChecker = { 400L * 1024L * 1024L }, // 400 MB (below 500 MB)
            onLowStorage = { lowStorageTriggered = true }
        )

        lowSpaceCoordinator.startRecording(
            stationName = "Raadio 2",
            streamUrl = "https://icecast.err.ee/raadio2.mp3",
            initialRawMetadata = "Queen - Radio Ga Ga"
        )

        assertFalse("Recording should not start when storage is below 500 MB", lowSpaceCoordinator.isRecording)
        assertTrue("onLowStorage callback should be invoked", lowStorageTriggered)
    }

    @Test
    fun `halts active recording and triggers onLowStorage when storage drops below 500 MB on track transition`() {
        var lowStorageTriggered = false
        var availableSpace = 600L * 1024L * 1024L // 600 MB initially
        var currentTime = 1000000L

        val dynamicSpaceCoordinator = StreamRecordingCoordinator(
            baseDirProvider = { baseDir },
            clock = { currentTime },
            minDurationSeconds = 0L,
            storageSpaceChecker = { availableSpace },
            onLowStorage = { lowStorageTriggered = true }
        )

        dynamicSpaceCoordinator.startRecording(
            stationName = "Raadio 2",
            streamUrl = "https://icecast.err.ee/raadio2.mp3",
            initialRawMetadata = "Queen - Radio Ga Ga"
        )
        assertTrue(dynamicSpaceCoordinator.isRecording)

        // Drop storage below 500 MB
        availableSpace = 300L * 1024L * 1024L
        currentTime += 35000L

        // Track transitions to Song B
        dynamicSpaceCoordinator.onMetadataChanged("Queen - We Will Rock You")

        assertFalse("Recording should halt when storage drops below 500 MB", dynamicSpaceCoordinator.isRecording)
        assertTrue("onLowStorage callback should be invoked on track transition", lowStorageTriggered)
    }
}

