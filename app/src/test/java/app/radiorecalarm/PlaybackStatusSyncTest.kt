package app.radiorecalarm

import androidx.media3.common.C
import androidx.media3.common.DataReader
import androidx.media3.extractor.DefaultExtractorInput
import androidx.media3.extractor.DummyTrackOutput
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.PositionHolder
import androidx.media3.extractor.SeekMap
import androidx.media3.extractor.TrackOutput
import androidx.media3.extractor.mp3.Mp3Extractor
import androidx.media3.extractor.ts.AdtsExtractor
import app.radiorecalarm.ui.MainUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackStatusSyncTest {

    @Test
    fun `playing local recording emits local station changed and playback progress`() {
        val snapshot = PlaybackStatusSync.resolveServiceStatus(
            stationName = "Vikerraadio",
            streamUrl = "file:///storage/emulated/0/Recordings/Broadcasts/Vikerraadio.aac",
            isPlayerInitialized = true,
            isPlaying = true,
            hasLoadedMedia = true
        )

        assertTrue(snapshot.isLocal)
        assertTrue(snapshot.sendStationChanged)
        assertTrue(snapshot.isPlaying)
        assertTrue(snapshot.sendBitrateAndTimer)
        assertTrue(snapshot.sendPlaybackProgress)
        assertEquals(PlaybackStatusAction.NONE, snapshot.terminalAction)
    }

    @Test
    fun `paused local recording preserves local status and emits paused action instead of stopped`() {
        val snapshot = PlaybackStatusSync.resolveServiceStatus(
            stationName = "Vikerraadio",
            streamUrl = "file:///storage/emulated/0/Recordings/Broadcasts/Vikerraadio.aac",
            isPlayerInitialized = true,
            isPlaying = false,
            hasLoadedMedia = true
        )

        assertTrue(snapshot.isLocal)
        assertTrue(snapshot.sendStationChanged)
        assertFalse(snapshot.isPlaying)
        assertFalse(snapshot.sendBitrateAndTimer)
        assertTrue(snapshot.sendPlaybackProgress)
        assertEquals(PlaybackStatusAction.PAUSED, snapshot.terminalAction)
    }

    @Test
    fun `idle or stopped local recording emits stopped without station changed`() {
        val snapshot = PlaybackStatusSync.resolveServiceStatus(
            stationName = "Vikerraadio",
            streamUrl = "file:///storage/emulated/0/Recordings/Broadcasts/Vikerraadio.aac",
            isPlayerInitialized = true,
            isPlaying = false,
            hasLoadedMedia = false
        )

        assertTrue(snapshot.isLocal)
        assertFalse(snapshot.sendStationChanged)
        assertFalse(snapshot.isPlaying)
        assertFalse(snapshot.sendPlaybackProgress)
        assertEquals(PlaybackStatusAction.STOPPED, snapshot.terminalAction)
    }

    @Test
    fun `live radio playback status behaves unchanged`() {
        val playing = PlaybackStatusSync.resolveServiceStatus(
            stationName = "Vikerraadio",
            streamUrl = "https://icecast.err.ee/vikerraadio.mp3",
            isPlayerInitialized = true,
            isPlaying = true,
            hasLoadedMedia = true
        )
        assertFalse(playing.isLocal)
        assertTrue(playing.sendStationChanged)
        assertTrue(playing.isPlaying)
        assertTrue(playing.sendBitrateAndTimer)
        assertFalse(playing.sendPlaybackProgress)
        assertEquals(PlaybackStatusAction.NONE, playing.terminalAction)

        val stopped = PlaybackStatusSync.resolveServiceStatus(
            stationName = "Vikerraadio",
            streamUrl = "https://icecast.err.ee/vikerraadio.mp3",
            isPlayerInitialized = true,
            isPlaying = false,
            hasLoadedMedia = false
        )
        assertFalse(stopped.isLocal)
        assertTrue(stopped.sendStationChanged)
        assertFalse(stopped.isPlaying)
        assertEquals(PlaybackStatusAction.STOPPED, stopped.terminalAction)
    }

    @Test
    fun `applyLocalStationChanged preserves file uri and track metadata across rotation`() {
        val fileUri = "file:///storage/emulated/0/Recordings/Broadcasts/Vikerraadio.aac"
        val initial = MainUiState(
            activeStationName = "Vikerraadio",
            activeStreamUrl = fileUri,
            selectedStationId = -1,
            parsedTitle = "Vikerraadio",
            parsedArtist = "Uudised",
            parsedExtra = "",
            isPlaying = true,
            playerStatus = "Playing",
            playbackPosition = 49_000L,
            playbackDuration = 267_000L
        )

        val afterPlayingSync = PlaybackStatusSync.applyLocalStationChanged(
            current = initial.copy(activeStreamUrl = "", selectedStationId = 42),
            stationName = "Vikerraadio",
            streamUrl = fileUri,
            isPlaying = true,
            playingStatusText = "Playing",
            pausedStatusText = "Stopped"
        )

        assertEquals(fileUri, afterPlayingSync.activeStreamUrl)
        assertEquals(-1, afterPlayingSync.selectedStationId)
        assertTrue(afterPlayingSync.isPlaying)
        assertEquals("Playing", afterPlayingSync.playerStatus)
        assertEquals("Uudised", afterPlayingSync.parsedArtist)

        val afterPausedSync = PlaybackStatusSync.applyLocalStationChanged(
            current = afterPlayingSync,
            stationName = "Vikerraadio",
            streamUrl = null,
            isPlaying = false,
            playingStatusText = "Playing",
            pausedStatusText = "Stopped"
        )

        assertEquals(fileUri, afterPausedSync.activeStreamUrl)
        assertEquals(-1, afterPausedSync.selectedStationId)
        assertFalse(afterPausedSync.isPlaying)
        assertEquals("Stopped", afterPausedSync.playerStatus)
    }

    @Test
    fun `clampSeekPosition clamps within known duration and handles unknown duration`() {
        assertEquals(0L, PlaybackStatusSync.clampSeekPosition(-500L, 120_000L))
        assertEquals(45_000L, PlaybackStatusSync.clampSeekPosition(45_000L, 120_000L))
        assertEquals(120_000L, PlaybackStatusSync.clampSeekPosition(300_000L, 120_000L))
        assertEquals(0L, PlaybackStatusSync.clampSeekPosition(-10L, 0L))
        assertEquals(50_000L, PlaybackStatusSync.clampSeekPosition(50_000L, 0L))
    }

    @Test
    fun `resolveEffectiveDurationMs prefers player duration and falls back to sidecar seconds`() {
        assertEquals(185_000L, PlaybackStatusSync.resolveEffectiveDurationMs(185_000L, 180L))
        assertEquals(180_000L, PlaybackStatusSync.resolveEffectiveDurationMs(0L, 180L))
        assertEquals(0L, PlaybackStatusSync.resolveEffectiveDurationMs(0L, 0L))
    }

    @Test
    fun `extractorsFactory enables seeking and real duration for local ADTS AAC recordings`() {
        val frame = AudioFrameAnalyzerTest.buildAdtsFrame(256)
        val frameCount = 1300 // ~30.18s
        val streamBytes = ByteArray(frame.size * frameCount)
        for (i in 0 until frameCount) {
            System.arraycopy(frame, 0, streamBytes, i * frame.size, frame.size)
        }

        // Default flags (0) reproduce the bug: unseekable + TIME_UNSET
        val defaultAdtsSeekMap = extractSeekMap(
            AdtsExtractor(0),
            streamBytes,
            contentLength = streamBytes.size.toLong()
        )
        assertNotNull(defaultAdtsSeekMap)
        assertFalse(defaultAdtsSeekMap!!.isSeekable)
        assertEquals(C.TIME_UNSET, defaultAdtsSeekMap.durationUs)

        // Configured flags fix both duration and seeking
        assertNotNull(PlaybackStatusSync.createExtractorsFactory())
        val seekMap = extractSeekMap(
            AdtsExtractor(PlaybackStatusSync.ADTS_EXTRACTOR_FLAGS),
            streamBytes,
            contentLength = streamBytes.size.toLong()
        )
        assertNotNull(seekMap)
        assertTrue(seekMap!!.isSeekable)
        assertTrue(seekMap.durationUs in 29_000_000L..31_000_000L)

        val seekTargetUs = (seekMap.durationUs * 3L) / 4L
        val seekPoints = seekMap.getSeekPoints(seekTargetUs)
        assertTrue(seekPoints.first.timeUs > 0L)
        assertTrue(seekPoints.first.position > streamBytes.size / 2L)
    }

    @Test
    fun `extractorsFactory keeps unknown-length live ADTS streams unseekable`() {
        val frame = AudioFrameAnalyzerTest.buildAdtsFrame(256)
        val frameCount = 100
        val streamBytes = ByteArray(frame.size * frameCount)
        for (i in 0 until frameCount) {
            System.arraycopy(frame, 0, streamBytes, i * frame.size, frame.size)
        }

        val seekMap = extractSeekMap(
            AdtsExtractor(PlaybackStatusSync.ADTS_EXTRACTOR_FLAGS),
            streamBytes,
            contentLength = C.LENGTH_UNSET.toLong()
        )
        assertNotNull(seekMap)
        assertFalse(seekMap!!.isSeekable)
        assertEquals(C.TIME_UNSET, seekMap.durationUs)
    }

    @Test
    fun `extractorsFactory supports seeking and duration for headerless local MP3 recordings`() {
        val frame = AudioFrameAnalyzerTest.buildMp3Frame()
        val frameCount = 1150 // ~30.04s
        val streamBytes = ByteArray(frame.size * frameCount)
        for (i in 0 until frameCount) {
            System.arraycopy(frame, 0, streamBytes, i * frame.size, frame.size)
        }

        val seekMap = extractSeekMap(
            Mp3Extractor(PlaybackStatusSync.MP3_EXTRACTOR_FLAGS),
            streamBytes,
            contentLength = streamBytes.size.toLong()
        )
        assertNotNull(seekMap)
        assertTrue(seekMap!!.isSeekable)
        assertTrue(seekMap.durationUs in 29_000_000L..31_000_000L)

        val seekTargetUs = (seekMap.durationUs * 3L) / 4L
        val seekPoints = seekMap.getSeekPoints(seekTargetUs)
        assertTrue(seekPoints.first.timeUs > 0L)
        assertTrue(seekPoints.first.position > streamBytes.size / 2L)
    }

    private fun extractSeekMap(
        extractor: Extractor,
        bytes: ByteArray,
        contentLength: Long
    ): SeekMap? {
        var capturedSeekMap: SeekMap? = null
        val output = object : ExtractorOutput {
            override fun track(id: Int, type: Int): TrackOutput = DummyTrackOutput()
            override fun endTracks() = Unit
            override fun seekMap(seekMap: SeekMap) {
                capturedSeekMap = seekMap
            }
        }
        extractor.init(output)

        var readOffset = 0
        val dataReader = DataReader { buffer, offset, length ->
            if (readOffset >= bytes.size) return@DataReader C.RESULT_END_OF_INPUT
            val toCopy = minOf(length, bytes.size - readOffset)
            System.arraycopy(bytes, readOffset, buffer, offset, toCopy)
            readOffset += toCopy
            toCopy
        }
        val input = DefaultExtractorInput(dataReader, 0L, contentLength)
        val positionHolder = PositionHolder()
        var iterations = 0
        while (capturedSeekMap == null && iterations++ < 200) {
            val result = extractor.read(input, positionHolder)
            if (result == Extractor.RESULT_END_OF_INPUT || result == Extractor.RESULT_SEEK) break
        }
        return capturedSeekMap
    }
}
