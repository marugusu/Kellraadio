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
            minDurationSeconds = 0L,
            overlapMarginSeconds = 0L
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
    fun `routes music tracks to Music folder with album and two-digit number`() {
        coordinator.startRecording(
            stationName = "Rock FM",
            streamUrl = "https://icecast.err.ee/raadio2.mp3",
            initialRawMetadata = "Queen - Bohemian Rhapsody"
        )
        val chunk = "QueenAudio".toByteArray()
        coordinator.onStreamBytes(chunk, 0, chunk.size)
        coordinator.stopRecording()

        val musicFiles = File(baseDir, "Music").walkTopDown().filter { it.isFile && it.name.endsWith(".mp3") }.toList()
        assertEquals(1, musicFiles.size)
        val file = musicFiles.first()
        // Should be inside Music/Queen/Unknown Album/
        assertTrue(file.parentFile?.path?.contains("Music") == true)
        assertTrue(file.parentFile?.path?.contains("Queen") == true)
        assertTrue(file.parentFile?.path?.contains("Unknown Album") == true)
        assertEquals("01 - Queen - Bohemian Rhapsody.mp3", file.name)
        assertEquals("QueenAudio", file.readText())
    }

    @Test
    fun `routes broadcast to Broadcasts folder when metadata is live show or station name`() {
        coordinator.startRecording(
            stationName = "Vikerraadio",
            streamUrl = "https://icecast.err.ee/vikerraadio.mp3",
            initialRawMetadata = "Otseeeter"
        )
        val chunk = "TalkShowAudio".toByteArray()
        coordinator.onStreamBytes(chunk, 0, chunk.size)
        coordinator.stopRecording()

        val showFiles = File(baseDir, "Broadcasts").walkTopDown().filter { it.isFile && it.name.endsWith(".mp3") }.toList()
        assertEquals(1, showFiles.size)
        val file = showFiles.first()
        assertTrue(file.parentFile?.path?.contains("Broadcasts") == true)
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
        coordinator.awaitPendingFinalizations()

        val albumDir = File(baseDir, "Music/Queen/Unknown Album")
        val files = albumDir.listFiles()?.filter { it.isFile && it.name.endsWith(".mp3") }?.sortedBy { it.name } ?: emptyList()

        assertEquals(2, files.size)
        assertEquals("01 - Queen - Bohemian Rhapsody.mp3", files[0].name)
        assertEquals("02 - Queen - Love of My Life.mp3", files[1].name)
    }

    @Test
    fun `keeps both recordings with sequential suffix when identical complete track is recorded again`() {
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

        // Queen played again at boundary (complete repeat)
        coordinator.onMetadataChanged("Queen - Bohemian Rhapsody")
        coordinator.onStreamBytes("Audio3".toByteArray(), 0, 6)

        coordinator.stopRecording()
        coordinator.awaitPendingFinalizations()

        val albumDir = File(baseDir, "Music/Queen/Unknown Album")
        val files = albumDir.listFiles()?.filter { it.isFile && it.name.endsWith(".mp3") }?.sortedBy { it.name } ?: emptyList()

        // Non-destructive duplicate handling preserves both recordings
        assertEquals(2, files.size)
        assertEquals("01 - Queen - Bohemian Rhapsody.mp3", files[0].name)
        assertEquals("01 - Queen - Bohemian Rhapsody_01.mp3", files[1].name)
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
        coordinator.awaitPendingFinalizations()

        val albumDir = File(baseDir, "Music/Queen/Unknown Album")
        val jsonBefore = File(albumDir, "01 - Queen - Bohemian Rhapsody.json")
        assertTrue(jsonBefore.exists())
        assertTrue(jsonBefore.readText().contains("\"partial\": true"))

        // Queen plays again at boundary (complete track closed cleanly at next boundary)
        coordinator.onMetadataChanged("Queen - Bohemian Rhapsody")
        coordinator.onStreamBytes("CompleteAudio".toByteArray(), 0, 13)
        coordinator.onMetadataChanged("ACDC - Thunderstruck")
        coordinator.onStreamBytes("NextTrack".toByteArray(), 0, 9)

        coordinator.stopRecording()
        coordinator.awaitPendingFinalizations()

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
        coordinator.awaitPendingFinalizations()

        val albumDir = File(baseDir, "Music/Queen/A Night at the Opera")
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
        coordinator.awaitPendingFinalizations()

        val albumDir = File(baseDir, "Music/Queen/A Night at the Opera")
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
        coordinator.awaitPendingFinalizations()

        val targetDir = File(baseDir, "Music/Tundmatu Esitaja/Unknown Album")
        val jsonFile = File(targetDir, "01 - Tundmatu Esitaja - Tundmatu Lugu.json")
        assertTrue(jsonFile.exists())
        val jsonContent = jsonFile.readText()
        assertTrue(jsonContent.contains("\"album\": null"))
        assertTrue(jsonContent.contains("\"year\": null"))
        assertTrue(jsonContent.contains("\"coverArtFile\": null"))
    }

    @Test
    fun `prunes and deletes tracks lasting less than 15 seconds`() {
        var currentTime = 1000000L
        coordinator = StreamRecordingCoordinator(
            baseDirProvider = { baseDir },
            clock = { currentTime },
            overlapMarginSeconds = 0L
        )

        coordinator.startRecording(
            stationName = "Rock FM",
            streamUrl = "https://icecast.err.ee/raadio2.mp3",
            initialRawMetadata = "Station - Jingle"
        )
        coordinator.onStreamBytes("ShortJingleBytes".toByteArray(), 0, 16)

        // Advance time by only 10 seconds (10s < 15s default)
        currentTime += 10000L

        coordinator.stopRecording()
        coordinator.awaitPendingFinalizations()

        val files = baseDir.walkTopDown().filter { it.isFile }.toList()
        assertEquals(0, files.size)
    }

    @Test
    fun `preserves tracks lasting 15 seconds or longer`() {
        var currentTime = 1000000L
        coordinator = StreamRecordingCoordinator(
            baseDirProvider = { baseDir },
            clock = { currentTime },
            overlapMarginSeconds = 0L
        )

        coordinator.startRecording(
            stationName = "Rock FM",
            streamUrl = "https://icecast.err.ee/raadio2.mp3",
            initialRawMetadata = "Queen - Bohemian Rhapsody"
        )
        coordinator.onStreamBytes("AudioBytes".toByteArray(), 0, 10)

        // Advance time by 20 seconds (20s >= 15s default)
        currentTime += 20000L

        coordinator.stopRecording()
        coordinator.awaitPendingFinalizations()

        val audioFiles = baseDir.walkTopDown().filter { it.isFile && it.name.endsWith(".mp3") }.toList()
        assertEquals(1, audioFiles.size)
        val jsonFiles = baseDir.walkTopDown().filter { it.isFile && it.name.endsWith(".json") }.toList()
        assertEquals(1, jsonFiles.size)
        assertTrue(jsonFiles.first().readText().contains("\"durationSeconds\": 20"))
    }

    @Test
    fun `flags mid-stream start and manual stop as partial while boundary-closed middle track is complete`() {
        var currentTime = 1000000L
        coordinator = StreamRecordingCoordinator(
            baseDirProvider = { baseDir },
            clock = { currentTime },
            overlapMarginSeconds = 0L
        )

        // Mid-stream start
        coordinator.startRecording(
            stationName = "Rock FM",
            streamUrl = "https://icecast.err.ee/raadio2.mp3",
            initialRawMetadata = "Artist 1 - Track 1"
        )
        coordinator.onStreamBytes("Audio1".toByteArray(), 0, 6)
        currentTime += 40000L

        // Middle track opens and closes cleanly at metadata boundaries
        coordinator.onMetadataChanged("Artist 2 - Track 2")
        coordinator.onStreamBytes("Audio2".toByteArray(), 0, 6)
        currentTime += 50000L

        // Final track opened at boundary, then manually stopped before closing boundary
        coordinator.onMetadataChanged("Artist 3 - Track 3")
        coordinator.onStreamBytes("Audio3".toByteArray(), 0, 6)
        currentTime += 30000L

        coordinator.stopRecording()
        coordinator.awaitPendingFinalizations()

        val json1 = File(baseDir, "Music/Artist 1/Unknown Album/01 - Artist 1 - Track 1.json")
        val json2 = File(baseDir, "Music/Artist 2/Unknown Album/01 - Artist 2 - Track 2.json")
        val json3 = File(baseDir, "Music/Artist 3/Unknown Album/01 - Artist 3 - Track 3.json")

        assertTrue(json1.readText().contains("\"partial\": true"))
        assertTrue(json2.readText().contains("\"partial\": false"))
        assertTrue(json3.readText().contains("\"partial\": true"))
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

    @Test
    fun `onMetadataChanged and stopRecording return immediately without blocking on slow metadata enrichment`() {
        val slowFetcher = MetadataFetcher { _, _ ->
            kotlinx.coroutines.delay(1500)
            SongAdditionalInfo(album = "Slow Album")
        }
        var currentTime = 1000000L
        val asyncCoordinator = StreamRecordingCoordinator(
            baseDirProvider = { baseDir },
            clock = { currentTime },
            metadataFetcher = slowFetcher,
            minDurationSeconds = 0L
        )

        asyncCoordinator.startRecording(
            stationName = "Rock FM",
            streamUrl = "https://icecast.err.ee/raadio2.mp3",
            initialRawMetadata = "Queen - Radio Ga Ga"
        )
        asyncCoordinator.onStreamBytes("Bytes1".toByteArray(), 0, 6)
        currentTime += 35000L

        val transitionStart = System.nanoTime()
        asyncCoordinator.onMetadataChanged("Queen - Killer Queen")
        val transitionElapsedMs = (System.nanoTime() - transitionStart) / 1_000_000L
        assertTrue("onMetadataChanged should not block (took ${transitionElapsedMs}ms)", transitionElapsedMs < 500L)

        asyncCoordinator.onStreamBytes("Bytes2".toByteArray(), 0, 6)
        currentTime += 35000L

        var finalizedCount = -1
        val latch = java.util.concurrent.CountDownLatch(1)
        val stopStart = System.nanoTime()
        asyncCoordinator.stopRecording { count ->
            finalizedCount = count
            latch.countDown()
        }
        val stopElapsedMs = (System.nanoTime() - stopStart) / 1_000_000L
        assertTrue("stopRecording should not block (took ${stopElapsedMs}ms)", stopElapsedMs < 500L)

        assertTrue(latch.await(5, java.util.concurrent.TimeUnit.SECONDS))
        assertEquals(2, finalizedCount)
        assertEquals(2, asyncCoordinator.completedTrackCount)
    }

    @Test
    fun `uses audio frame duration instead of wall clock so burst-buffered tracks over 30s are preserved`() {
        var currentTime = 1000000L
        val frameCoordinator = StreamRecordingCoordinator(
            baseDirProvider = { baseDir },
            clock = { currentTime } // default minDurationSeconds = 30s
        )

        frameCoordinator.startRecording(
            stationName = "Rock FM",
            streamUrl = "https://icecast.err.ee/raadio2.mp3",
            initialRawMetadata = "Queen - Bohemian Rhapsody"
        )

        // Write 1200 valid MP3 frames (~31.3s of audio) while wall clock only advances by 2s (initial buffer burst)
        val frame = AudioFrameAnalyzerTest.buildMp3Frame()
        repeat(1200) {
            frameCoordinator.onStreamBytes(frame, 0, frame.size)
        }
        currentTime += 2000L

        frameCoordinator.stopRecording()
        frameCoordinator.awaitPendingFinalizations()

        val jsonFiles = baseDir.walkTopDown().filter { it.isFile && it.name.endsWith(".json") }.toList()
        assertEquals(1, jsonFiles.size)
        assertTrue(jsonFiles.first().readText().contains("\"durationSeconds\": 31"))
    }

    @Test
    fun `prunes short audio frames even when wall clock stalled beyond 30 seconds`() {
        var currentTime = 1000000L
        val frameCoordinator = StreamRecordingCoordinator(
            baseDirProvider = { baseDir },
            clock = { currentTime } // default minDurationSeconds = 30s
        )

        frameCoordinator.startRecording(
            stationName = "Rock FM",
            streamUrl = "https://icecast.err.ee/raadio2.mp3",
            initialRawMetadata = "Station - Short Jingle"
        )

        // Write only ~5s of valid MP3 frames (200 frames) while wall clock advances 40s (network stall)
        val frame = AudioFrameAnalyzerTest.buildMp3Frame()
        repeat(200) {
            frameCoordinator.onStreamBytes(frame, 0, frame.size)
        }
        currentTime += 40000L

        frameCoordinator.stopRecording()
        frameCoordinator.awaitPendingFinalizations()

        val remainingFiles = baseDir.walkTopDown().filter { it.isFile }.toList()
        assertEquals(0, remainingFiles.size)
    }

    @Test
    fun `falls back to bitrate-based duration when valid frame headers are absent`() {
        var currentTime = 1000000L
        val bitrateCoordinator = StreamRecordingCoordinator(
            baseDirProvider = { baseDir },
            clock = { currentTime } // default minDurationSeconds = 30s
        )

        bitrateCoordinator.startRecording(
            stationName = "Rock FM",
            streamUrl = "https://icecast.err.ee/raadio2.mp3",
            initialRawMetadata = "Queen - We Will Rock You",
            bitrate = "128 kbps"
        )

        // 128 kbps = 16,000 bytes/sec -> 512,000 bytes = 32s, even if wall clock only advances 1s
        val rawPayload = ByteArray(16_000) { 0x11 }
        repeat(32) {
            bitrateCoordinator.onStreamBytes(rawPayload, 0, rawPayload.size)
        }
        currentTime += 1000L

        bitrateCoordinator.stopRecording()
        bitrateCoordinator.awaitPendingFinalizations()

        val jsonFiles = baseDir.walkTopDown().filter { it.isFile && it.name.endsWith(".json") }.toList()
        assertEquals(1, jsonFiles.size)
        assertTrue(jsonFiles.first().readText().contains("\"durationSeconds\": 32"))
    }

    @Test
    fun `routes single-part metadata and ignore terms to Broadcasts without enrichment`() {
        var fetchCount = 0
        val countingCoordinator = StreamRecordingCoordinator(
            baseDirProvider = { baseDir },
            clock = { 1000000L },
            metadataFetcher = { _, _ ->
                fetchCount++
                SongAdditionalInfo(album = "ShouldNotBeFetched")
            },
            minDurationSeconds = 0L
        )

        countingCoordinator.startRecording(
            stationName = "Vikerraadio",
            streamUrl = "https://icecast.err.ee/vikerraadio.mp3",
            initialRawMetadata = "Vikerhommik"
        )
        countingCoordinator.onStreamBytes("ShowPart1".toByteArray(), 0, 9)

        countingCoordinator.onMetadataChanged("ERR - Uudised")
        countingCoordinator.onStreamBytes("NewsPart2".toByteArray(), 0, 9)

        countingCoordinator.stopRecording()
        countingCoordinator.awaitPendingFinalizations()

        assertEquals(0, fetchCount)
        val showFiles = File(baseDir, "Broadcasts/Vikerraadio").listFiles()?.filter { it.name.endsWith(".mp3") } ?: emptyList()
        assertEquals(2, showFiles.size)
        val musicFiles = File(baseDir, "Music").walkTopDown().filter { it.isFile }.toList()
        assertEquals(0, musicFiles.size)
    }

    @Test
    fun `relocates track exceeding 10 minutes from Music to Broadcasts on finalization`() {
        var currentTime = 1_000_000L
        val tenMinCoordinator = StreamRecordingCoordinator(
            baseDirProvider = { baseDir },
            clock = { currentTime },
            metadataFetcher = { _, _ -> SongAdditionalInfo(album = "ShouldNotMoveHere") }
        )

        tenMinCoordinator.startRecording(
            stationName = "Raadio 2",
            streamUrl = "https://icecast.err.ee/raadio2.mp3",
            initialRawMetadata = "Saatejuht - Pikk Saade"
        )
        tenMinCoordinator.onStreamBytes("LongShowAudio".toByteArray(), 0, 13)

        // Advance clock past 10 minutes (605s > 600s)
        currentTime += 605_000L
        tenMinCoordinator.stopRecording()
        tenMinCoordinator.awaitPendingFinalizations()

        val musicFiles = File(baseDir, "Music").walkTopDown().filter { it.isFile }.toList()
        assertEquals(0, musicFiles.size)
        assertFalse(File(baseDir, "Music/Saatejuht").exists())

        val showFiles = File(baseDir, "Broadcasts/Raadio 2").listFiles()?.filter { it.name.endsWith(".mp3") } ?: emptyList()
        assertEquals(1, showFiles.size)
        assertEquals("LongShowAudio", showFiles.first().readText())

        val jsonFiles = File(baseDir, "Broadcasts/Raadio 2").listFiles()?.filter { it.name.endsWith(".json") } ?: emptyList()
        assertEquals(1, jsonFiles.size)
        assertTrue(jsonFiles.first().readText().contains("\"durationSeconds\": 605"))
        assertTrue(jsonFiles.first().readText().contains("\"album\": null"))
    }

    @Test
    fun `does not overwrite longer partial recording when shorter complete recording arrives`() {
        var currentTime = 1_000_000L
        val safeCoordinator = StreamRecordingCoordinator(
            baseDirProvider = { baseDir },
            clock = { currentTime }
        )

        // First recording (45 min, partial = true)
        safeCoordinator.startRecording(
            stationName = "Vikerraadio",
            streamUrl = "https://icecast.err.ee/vikerraadio.mp3",
            initialRawMetadata = "Artist - ShowTitle"
        )
        safeCoordinator.onStreamBytes("Long45MinContent".toByteArray(), 0, 16)
        currentTime += 500_000L // 500s <= 600s so it stays in Music to exercise duplicate check
        safeCoordinator.stopRecording()
        safeCoordinator.awaitPendingFinalizations()

        // Second recording after restart: transition to another title closes it as partial = false, but only 100s long
        safeCoordinator.startRecording(
            stationName = "Vikerraadio",
            streamUrl = "https://icecast.err.ee/vikerraadio.mp3",
            initialRawMetadata = "Jingle - Intro"
        )
        safeCoordinator.onStreamBytes("Intro".toByteArray(), 0, 5)
        currentTime += 40_000L

        safeCoordinator.onMetadataChanged("Artist - ShowTitle")
        safeCoordinator.onStreamBytes("Short5MinContent".toByteArray(), 0, 16)
        currentTime += 100_000L
        safeCoordinator.onMetadataChanged("Outro - End")
        safeCoordinator.onStreamBytes("Outro".toByteArray(), 0, 5)
        currentTime += 20_000L
        safeCoordinator.stopRecording()
        safeCoordinator.awaitPendingFinalizations()

        val albumDir = File(baseDir, "Music/Artist/Unknown Album")
        val mp3Files = albumDir.listFiles()?.filter { it.name.endsWith(".mp3") }?.sortedBy { it.name } ?: emptyList()
        assertEquals(2, mp3Files.size)
        assertEquals("01 - Artist - ShowTitle.mp3", mp3Files[0].name)
        assertEquals("Long45MinContent", mp3Files[0].readText())
        assertEquals("01 - Artist - ShowTitle_01.mp3", mp3Files[1].name)
        assertEquals("Short5MinContent", mp3Files[1].readText())
    }

    @Test
    fun `unsplit show mode records entire session into one Broadcasts file ignoring metadata transitions`() {
        var currentTime = 1_000_000L
        val alarmCoordinator = StreamRecordingCoordinator(
            baseDirProvider = { baseDir },
            clock = { currentTime }
        )

        alarmCoordinator.startRecording(
            stationName = "Vikerraadio",
            streamUrl = "https://icecast.err.ee/vikerraadio.mp3",
            initialRawMetadata = "Vikerraadio",
            forceUnsplitShow = true
        )
        assertTrue(alarmCoordinator.isUnsplitShowMode)
        alarmCoordinator.onStreamBytes("ShowStart-".toByteArray(), 0, 10)
        currentTime += 60_000L

        // Songs & news played during the alarm broadcast must not split the file
        alarmCoordinator.onMetadataChanged("Queen - Bohemian Rhapsody")
        alarmCoordinator.onStreamBytes("MiddleSong-".toByteArray(), 0, 11)
        currentTime += 200_000L

        alarmCoordinator.onMetadataChanged("ERR - Uudised")
        alarmCoordinator.onStreamBytes("ShowEnd".toByteArray(), 0, 7)
        currentTime += 120_000L

        alarmCoordinator.stopRecording()
        alarmCoordinator.awaitPendingFinalizations()
        assertFalse(alarmCoordinator.isUnsplitShowMode)

        val musicFiles = File(baseDir, "Music").walkTopDown().filter { it.isFile }.toList()
        assertEquals(0, musicFiles.size)

        val showFiles = File(baseDir, "Broadcasts/Vikerraadio").listFiles()?.filter { it.name.endsWith(".mp3") } ?: emptyList()
        assertEquals(1, showFiles.size)
        assertEquals("ShowStart-MiddleSong-ShowEnd", showFiles.first().readText())
    }

    @Test
    fun `PreRollFrameBuffer retains up to maxDurationMicros and respects capacity updates`() {
        val frame = AudioFrame(ByteArray(100), durationMicros = 25_000L, isAac = false) // 25 ms
        val pr = PreRollFrameBuffer(maxDurationMicros = 100_000L) // 100 ms -> max 4 frames
        repeat(10) { pr.push(frame) }
        assertEquals(4, pr.snapshotFrames().size)
        assertEquals(100_000L, pr.totalDurationMicros)

        pr.updateCapacity(50_000L) // 50 ms -> trims to 2 frames
        assertEquals(2, pr.snapshotFrames().size)
        assertEquals(50_000L, pr.totalDurationMicros)

        pr.updateCapacity(0L)
        assertEquals(0, pr.snapshotFrames().size)
        assertEquals(0L, pr.totalDurationMicros)
    }

    @Test
    fun `applies symmetric pre-roll and post-roll overlap frames across track boundary`() {
        var currentTime = 1_000_000L
        val overlapCoordinator = StreamRecordingCoordinator(
            baseDirProvider = { baseDir },
            clock = { currentTime },
            minDurationSeconds = 15L,
            overlapMarginSeconds = 10L
        )

        val frameA = AudioFrameAnalyzerTest.buildMp3Frame(fillByte = 0x1A)
        val frameB = AudioFrameAnalyzerTest.buildMp3Frame(fillByte = 0x2B)
        // ~26.122 ms per frame -> 800 frames = ~20.9 s
        overlapCoordinator.startRecording(
            stationName = "Raadio 2",
            streamUrl = "https://icecast.err.ee/raadio2.mp3",
            initialRawMetadata = "Artist A - Song A"
        )
        repeat(800) {
            overlapCoordinator.onStreamBytes(frameA, 0, frameA.size)
        }
        currentTime += 21_000L

        overlapCoordinator.onMetadataChanged("Artist B - Song B")
        repeat(800) {
            overlapCoordinator.onStreamBytes(frameB, 0, frameB.size)
        }
        currentTime += 21_000L

        // Close Track B cleanly at next transition, write 450 frames (~11.7 s) so Track B finishes its 10s post-roll
        overlapCoordinator.onMetadataChanged("Artist C - Song C")
        repeat(450) {
            overlapCoordinator.onStreamBytes(frameB, 0, frameB.size)
        }
        currentTime += 12_000L

        overlapCoordinator.stopRecording()
        overlapCoordinator.awaitPendingFinalizations()

        val fileA = File(baseDir, "Music/Artist A/Unknown Album/01 - Artist A - Song A.mp3")
        val fileB = File(baseDir, "Music/Artist B/Unknown Album/01 - Artist B - Song B.mp3")
        val jsonA = File(baseDir, "Music/Artist A/Unknown Album/01 - Artist A - Song A.json")
        val jsonB = File(baseDir, "Music/Artist B/Unknown Album/01 - Artist B - Song B.json")

        assertTrue(fileA.exists())
        assertTrue(fileB.exists())

        // Track A: 800 core frames + ~10s post-roll (~383 frames of B)
        val framesInA = (fileA.length() / frameA.size).toInt()
        assertTrue("Expected Track A to include ~10s post-roll (>1150 frames), got $framesInA", framesInA in 1170..1190)

        // Track B: ~10s pre-roll (~383 frames of A) + 800 core frames + ~10s post-roll (~383 frames)
        val framesInB = (fileB.length() / frameB.size).toInt()
        assertTrue("Expected Track B to include pre-roll and post-roll (~1566 frames), got $framesInB", framesInB in 1550..1585)

        // Verify Track B starts with frameA (pre-roll) and ends with frameB
        val bytesB = fileB.readBytes()
        assertEquals(0x1A.toByte(), bytesB[10])
        assertEquals(0x2B.toByte(), bytesB[bytesB.size - 10])

        assertTrue(jsonA.readText().contains("\"durationSeconds\": 30") || jsonA.readText().contains("\"durationSeconds\": 31"))
        assertTrue(jsonB.readText().contains("\"durationSeconds\": 40") || jsonB.readText().contains("\"durationSeconds\": 41"))
    }

    @Test
    fun `prunes short interstitial jingle by core duration even when overlap inflates file duration`() {
        var currentTime = 1_000_000L
        val overlapCoordinator = StreamRecordingCoordinator(
            baseDirProvider = { baseDir },
            clock = { currentTime },
            minDurationSeconds = 15L,
            overlapMarginSeconds = 10L
        )

        val frameA = AudioFrameAnalyzerTest.buildMp3Frame(fillByte = 0x11)
        val frameJingle = AudioFrameAnalyzerTest.buildMp3Frame(fillByte = 0x55)
        val frameB = AudioFrameAnalyzerTest.buildMp3Frame(fillByte = 0x22)

        overlapCoordinator.startRecording(
            stationName = "Sky Plus",
            streamUrl = "https://stream.sky.ee/skyplus.mp3",
            initialRawMetadata = "Artist A - Song A"
        )
        // Track A: 700 frames (~18.3 s core >= 15s)
        repeat(700) { overlapCoordinator.onStreamBytes(frameA, 0, frameA.size) }
        currentTime += 18_300L

        // 10s tagged jingle: 380 frames (~9.9 s core < 15s, even though with 10s pre+10s post its file is ~30s!)
        overlapCoordinator.onMetadataChanged("Station - Jingle")
        repeat(380) { overlapCoordinator.onStreamBytes(frameJingle, 0, frameJingle.size) }
        currentTime += 10_000L

        // Track B: 700 frames (~18.3 s core >= 15s)
        overlapCoordinator.onMetadataChanged("Artist B - Song B")
        repeat(700) { overlapCoordinator.onStreamBytes(frameB, 0, frameB.size) }
        currentTime += 18_300L

        overlapCoordinator.stopRecording()
        overlapCoordinator.awaitPendingFinalizations()

        val jingleDir = File(baseDir, "Music/Station")
        assertFalse("10s jingle must be pruned by core duration despite 30s overlapped file", jingleDir.exists())

        val fileA = File(baseDir, "Music/Artist A/Unknown Album/01 - Artist A - Song A.mp3")
        val fileB = File(baseDir, "Music/Artist B/Unknown Album/01 - Artist B - Song B.mp3")
        assertTrue(fileA.exists())
        assertTrue(fileB.exists())

        // Concurrent post-roll ensured Track A got full ~10s past jingle boundary (380 jingle frames + >=1 frame of B)
        val framesInA = (fileA.length() / frameA.size).toInt()
        assertTrue("Track A should keep full ~10s post-roll across jingle (~1083 frames), got $framesInA", framesInA >= 1080)
        // Track B got 10s pre-roll covering the entire jingle
        val framesInB = (fileB.length() / frameB.size).toInt()
        assertTrue("Track B should keep full ~10s pre-roll (~1083 frames), got $framesInB", framesInB >= 1080)
    }

    @Test
    fun `keeps track under 10 minutes core duration in Music even when overlap exceeds 600s total`() {
        var currentTime = 1_000_000L
        val overlapCoordinator = StreamRecordingCoordinator(
            baseDirProvider = { baseDir },
            clock = { currentTime },
            minDurationSeconds = 15L,
            overlapMarginSeconds = 10L
        )
        val frame = AudioFrameAnalyzerTest.buildMp3Frame()

        overlapCoordinator.startRecording(
            stationName = "Raadio 2",
            streamUrl = "https://icecast.err.ee/raadio2.mp3",
            initialRawMetadata = "Intro - Track"
        )
        // ~11s pre-roll source
        repeat(420) { overlapCoordinator.onStreamBytes(frame, 0, frame.size) }
        currentTime += 11_000L

        // Track A: 22,780 frames = 595.06 s core (<= 600s), plus 10s pre-roll + 10s post-roll = ~615s total file!
        overlapCoordinator.onMetadataChanged("Artist - Long Song")
        repeat(22_780) { overlapCoordinator.onStreamBytes(frame, 0, frame.size) }
        currentTime += 595_000L

        overlapCoordinator.onMetadataChanged("Outro - Track")
        repeat(420) { overlapCoordinator.onStreamBytes(frame, 0, frame.size) }
        currentTime += 11_000L

        overlapCoordinator.stopRecording()
        overlapCoordinator.awaitPendingFinalizations()

        val musicFile = File(baseDir, "Music/Artist/Unknown Album/01 - Artist - Long Song.mp3")
        val musicJson = File(baseDir, "Music/Artist/Unknown Album/01 - Artist - Long Song.json")
        assertTrue("595s core track must stay in Music despite >600s overlapped file", musicFile.exists())
        assertTrue(musicJson.readText().contains("\"durationSeconds\": 615"))
    }

    @Test
    fun `applies pre-roll and post-roll only to tracks across Track-Show-Track transitions`() {
        var currentTime = 1_000_000L
        val overlapCoordinator = StreamRecordingCoordinator(
            baseDirProvider = { baseDir },
            clock = { currentTime },
            minDurationSeconds = 15L,
            overlapMarginSeconds = 10L
        )
        val frame = AudioFrameAnalyzerTest.buildMp3Frame()

        overlapCoordinator.startRecording(
            stationName = "Raadio 2",
            streamUrl = "https://icecast.err.ee/raadio2.mp3",
            initialRawMetadata = "Artist A - Song A"
        )
        // Song A: 700 frames (~18.3 s)
        repeat(700) { overlapCoordinator.onStreamBytes(frame, 0, frame.size) }
        currentTime += 18_300L

        // Transition to Show (Uudised): 700 frames (~18.3 s), Show itself must NOT get pre-roll or post-roll
        overlapCoordinator.onMetadataChanged("ERR - Uudised")
        repeat(700) { overlapCoordinator.onStreamBytes(frame, 0, frame.size) }
        currentTime += 18_300L

        // Transition to Song B: 700 frames (~18.3 s), Song B MUST get ~10s pre-roll from Uudised tail
        overlapCoordinator.onMetadataChanged("Artist B - Song B")
        repeat(700) { overlapCoordinator.onStreamBytes(frame, 0, frame.size) }
        currentTime += 18_300L

        overlapCoordinator.stopRecording()
        overlapCoordinator.awaitPendingFinalizations()

        val fileA = File(baseDir, "Music/Artist A/Unknown Album/01 - Artist A - Song A.mp3")
        val showFiles = File(baseDir, "Broadcasts/Raadio 2").listFiles()?.filter { it.name.endsWith(".mp3") } ?: emptyList()
        val fileB = File(baseDir, "Music/Artist B/Unknown Album/01 - Artist B - Song B.mp3")

        assertEquals(1, showFiles.size)
        // Show gets exact 700 frames (no pre-roll, no post-roll)
        assertEquals(700L * frame.size, showFiles.first().length())
        // Song A got ~10s post-roll (~383 frames)
        assertTrue((fileA.length() / frame.size) > 1050)
        // Song B got ~10s pre-roll (~383 frames)
        assertTrue((fileB.length() / frame.size) > 1050)
    }

    @Test
    fun `resetFrameAlignment drops partial reconnect tail without splitting active track`() {
        var currentTime = 1_000_000L
        val reconnectCoordinator = StreamRecordingCoordinator(
            baseDirProvider = { baseDir },
            clock = { currentTime },
            minDurationSeconds = 15L,
            overlapMarginSeconds = 10L
        )
        val frame = AudioFrameAnalyzerTest.buildMp3Frame()

        reconnectCoordinator.startRecording(
            stationName = "Raadio 2",
            streamUrl = "https://icecast.err.ee/raadio2.mp3",
            initialRawMetadata = "Queen - Radio Ga Ga"
        )
        // Push 400 complete frames + 100 bytes of a truncated frame before socket drop
        repeat(400) { reconnectCoordinator.onStreamBytes(frame, 0, frame.size) }
        reconnectCoordinator.onStreamBytes(frame, 0, 100)

        // Reconnect drops incomplete parser buffer without splitting the file
        reconnectCoordinator.resetFrameAlignment()
        repeat(400) { reconnectCoordinator.onStreamBytes(frame, 0, frame.size) }
        currentTime += 21_000L

        reconnectCoordinator.stopRecording()
        reconnectCoordinator.awaitPendingFinalizations()

        val files = File(baseDir, "Music/Queen/Unknown Album").listFiles()?.filter { it.name.endsWith(".mp3") } ?: emptyList()
        assertEquals(1, files.size)
        // Exactly 800 complete frames written cleanly without the 100-byte truncated tail
        assertEquals(800L * frame.size, files.first().length())
    }
}

