package app.radiorecalarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class AudioBoundarySplitTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `AudioFrameAccumulator splits arbitrarily chunked MP3 stream into discrete frames`() {
        val frame1 = AudioFrameAnalyzerTest.buildMp3Frame(0x11)
        val frame2 = AudioFrameAnalyzerTest.buildMp3Frame(0x22)
        val frame3 = AudioFrameAnalyzerTest.buildMp3Frame(0x33)
        val stream = frame1 + frame2 + frame3

        val acc = AudioFrameAccumulator(preferAac = false)
        val frames = mutableListOf<AudioFrame>()
        val rawFallbacks = mutableListOf<ByteArray>()

        var pos = 0
        while (pos < stream.size) {
            val chunk = minOf(100, stream.size - pos)
            acc.push(stream, pos, chunk, onFrame = { frames.add(it) }, onRawFallback = { b, o, l ->
                rawFallbacks.add(b.copyOfRange(o, o + l))
            })
            pos += chunk
        }
        acc.drainUnparsed { b, o, l -> rawFallbacks.add(b.copyOfRange(o, o + l)) }

        assertEquals(3, frames.size)
        assertTrue(rawFallbacks.isEmpty())
        assertTrue(frames[0].bytes.contentEquals(frame1))
        assertTrue(frames[1].bytes.contentEquals(frame2))
        assertTrue(frames[2].bytes.contentEquals(frame3))
    }

    @Test
    fun `AudioFrameAccumulator skips leading partial garbage before locking onto valid ADTS frames`() {
        val junk = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05)
        val adts1 = AudioFrameAnalyzerTest.buildAdtsFrame(256, 0x22)
        val adts2 = AudioFrameAnalyzerTest.buildAdtsFrame(256, 0x33)
        val stream = junk + adts1 + adts2

        val acc = AudioFrameAccumulator(preferAac = true)
        val frames = mutableListOf<AudioFrame>()
        acc.push(stream, 0, stream.size, onFrame = { frames.add(it) }, onRawFallback = { _, _, _ -> })

        assertEquals(2, frames.size)
        assertTrue(frames[0].bytes.contentEquals(adts1))
        assertTrue(frames[1].bytes.contentEquals(adts2))
    }

    @Test
    fun `LookbackFrameBuffer retains up to maxDurationMicros and overflows oldest frames`() {
        val lb = LookbackFrameBuffer(maxDurationMicros = 100_000L) // 100 ms
        val overflowed = mutableListOf<Byte>()

        repeat(6) { idx ->
            val f = AudioFrame(byteArrayOf(idx.toByte()), durationMicros = 25_000L, isAac = true)
            lb.push(f) { overflow -> overflowed.add(overflow.bytes[0]) }
        }

        // 4 frames fit in 100 ms; first 2 frames overflowed
        assertEquals(listOf<Byte>(0, 1), overflowed)
        assertEquals(100_000L, lb.totalDurationMicros)
        assertEquals(listOf<Byte>(2, 3, 4, 5), lb.snapshotFrames().map { it.bytes[0] })
    }

    @Test
    fun `AudioBoundaryScorer detects silence gap at 2_8s before metadata arrival`() {
        // 320 frames @ 25ms = 8000ms total; silence gap around 5050..5250ms (lag ~2.8s before end)
        val n = 320
        val durations = LongArray(n) { 25L }
        val energies = DoubleArray(n) { idx ->
            val tMs = idx * 25L
            when {
                tMs in 5050L..5250L -> -62.0 // clean silence gap
                tMs in 4700L until 5050L -> -24.0 // quick fade out
                else -> -12.0 // song & jingle audio
            }
        }

        val splitIdx = AudioBoundaryScorer.selectSplitIndex(
            frameEnergiesDb = energies,
            frameDurationsMs = durations,
            hasFlushedPrefix = true
        )

        val splitTimeMs = splitIdx * 25L
        assertTrue("Expected split inside silence gap (~5050..5275ms), got ${splitTimeMs}ms", splitTimeMs in 5050L..5300L)
    }

    @Test
    fun `AudioBoundaryScorer detects crossfade energy dip without true silence`() {
        val n = 320 // 8000ms
        val durations = LongArray(n) { 25L }
        val energies = DoubleArray(n) { idx ->
            val tMs = idx * 25L
            when {
                tMs in 5400L..5550L -> -24.0 // 12 dB crossfade dip (~2.5s before end)
                else -> -12.0
            }
        }

        val splitIdx = AudioBoundaryScorer.selectSplitIndex(
            frameEnergiesDb = energies,
            frameDurationsMs = durations,
            hasFlushedPrefix = true
        )

        val splitTimeMs = splitIdx * 25L
        assertTrue("Expected split near crossfade dip (~5400..5600ms), got ${splitTimeMs}ms", splitTimeMs in 5375L..5600L)
    }

    @Test
    fun `AudioBoundaryScorer uses conservative fallback offset when audio is flat loud`() {
        val n = 320 // 8000ms
        val durations = LongArray(n) { 25L }
        val energies = DoubleArray(n) { -10.0 } // completely flat loud audio

        val splitIdx = AudioBoundaryScorer.selectSplitIndex(
            frameEnergiesDb = energies,
            frameDurationsMs = durations,
            hasFlushedPrefix = true,
            fallbackOffsetMs = 2000L
        )

        val splitTimeMs = splitIdx * 25L
        assertEquals(6000L, splitTimeMs)
    }

    @Test
    fun `AudioBoundaryScorer respects minSegmentGuardMs when segment has no flushed prefix`() {
        // Short 4-second jingle buffered since previous split; silence appears at t=0..150ms AND t=2000..2150ms
        val n = 160 // 4000ms
        val durations = LongArray(n) { 25L }
        val energies = DoubleArray(n) { idx ->
            val tMs = idx * 25L
            when {
                tMs < 150L -> -70.0 // tail of previous split silence!
                tMs in 2000L..2150L -> -65.0 // real end of jingle!
                else -> -12.0
            }
        }

        val splitIdx = AudioBoundaryScorer.selectSplitIndex(
            frameEnergiesDb = energies,
            frameDurationsMs = durations,
            hasFlushedPrefix = false,
            minSegmentGuardMs = 400L
        )

        val splitTimeMs = splitIdx * 25L
        assertTrue("Expected split at ~2000..2200ms (skipping guarded start), got ${splitTimeMs}ms", splitTimeMs in 2000L..2200L)
    }

    @Test
    fun `lookback buffer recovers next song intro across pruned short jingle and removes jingle tail from previous song`() {
        val baseDir = tempFolder.newFolder("rec")
        var currentTime = 1_000_000L

        // Tag frames with distinct payload byte:
        // 0x11 = Song 1 (1400 frames = ~32.5s)
        // 0x22 = Jingle (170 frames = ~3.95s, < 30s -> should be pruned!)
        // 0x33 = Song 2 (1400 frames = ~32.5s)
        val detector = AudioBoundaryDetector { frames, hasFlushedPrefix ->
            // Simulate Acoustic detector finding the exact transition from 0x11 -> 0x22 or 0x22 -> 0x33!
            val startIdx = if (hasFlushedPrefix) 0 else 15
            var found = frames.size
            for (i in maxOf(1, startIdx) until frames.size) {
                if (frames[i].bytes[10] != frames[i - 1].bytes[10]) {
                    found = i
                    break
                }
            }
            found
        }

        val coordinator = StreamRecordingCoordinator(
            baseDirProvider = { baseDir },
            clock = { currentTime },
            minDurationSeconds = 30L,
            lookbackDurationSeconds = 8L,
            audioBoundaryDetector = detector
        )

        fun buildTaggedAdts(tag: Byte): ByteArray = AudioFrameAnalyzerTest.buildAdtsFrame(256, tag)

        coordinator.startRecording(
            stationName = "Elmar Kuld",
            streamUrl = "https://example.com/elmarikuld.aac",
            initialRawMetadata = "Krõll - Lucille"
        )

        // 1. Song 1 plays for 1400 frames (~32.5s)
        val song1Frame = buildTaggedAdts(0x11)
        repeat(1400) {
            coordinator.onStreamBytes(song1Frame, 0, song1Frame.size)
        }
        // 2. Jingle starts in audio BEFORE Icecast sends metadata! (110 frames = ~2.55s late)
        val jingleFrame = buildTaggedAdts(0x22)
        repeat(110) {
            coordinator.onStreamBytes(jingleFrame, 0, jingleFrame.size)
        }
        currentTime += 35_000L

        // Icecast emits late jingle metadata!
        coordinator.onMetadataChanged("Elmar Kuld")

        // 3. Remaining 60 frames (~1.4s) of jingle arrive, then Song 2 starts BEFORE metadata! (110 frames = ~2.55s late)
        repeat(60) {
            coordinator.onStreamBytes(jingleFrame, 0, jingleFrame.size)
        }
        val song2Frame = buildTaggedAdts(0x33)
        repeat(110) {
            coordinator.onStreamBytes(song2Frame, 0, song2Frame.size)
        }
        currentTime += 4_000L

        // Icecast emits late Song 2 metadata!
        coordinator.onMetadataChanged("Ahti Nurmis - Nüüd astub hobu omapead")

        // 4. Rest of Song 2 (1290 frames) arrives
        repeat(1290) {
            coordinator.onStreamBytes(song2Frame, 0, song2Frame.size)
        }
        currentTime += 30_000L

        coordinator.stopRecording()
        coordinator.awaitPendingFinalizations()

        val song1File = File(baseDir, "Music/Krõll/Unknown Album/01 - Krõll - Lucille.aac")
        val song2File = File(baseDir, "Music/Ahti Nurmis/Unknown Album/01 - Ahti Nurmis - Nüüd astub hobu omapead.aac")
        val showDir = File(baseDir, "Broadcasts")

        assertTrue("Song 1 file should exist", song1File.exists())
        assertTrue("Song 2 file should exist", song2File.exists())
        assertFalse("Pruned jingle should leave no Broadcasts files", showDir.walkTopDown().any { it.isFile })

        // Song 1 must contain ONLY 1400 Song 1 frames (zero jingle bytes!)
        val s1Bytes = song1File.readBytes()
        assertEquals(1400 * 256, s1Bytes.size)
        assertTrue((0 until 1400).all { idx -> s1Bytes[idx * 256 + 10] == 0x11.toByte() })

        // Song 2 must contain ALL 1400 Song 2 frames (including the 110 intro frames recovered from before metadata!)
        val s2Bytes = song2File.readBytes()
        assertEquals(1400 * 256, s2Bytes.size)
        assertTrue((0 until 1400).all { idx -> s2Bytes[idx * 256 + 10] == 0x33.toByte() })
    }
}
