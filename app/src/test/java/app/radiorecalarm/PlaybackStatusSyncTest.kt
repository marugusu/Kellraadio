package app.radiorecalarm

import app.radiorecalarm.ui.MainUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
}
