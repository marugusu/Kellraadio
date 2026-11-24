package app.radiorecalarm.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompactWidgetLayoutSpecTest {

    @Test
    fun `2x1 size resolves to MINI_2X1 without artwork or skip buttons`() {
        val cfg = CompactWidgetLayoutSpec.resolve(
            widthDp = 140f,
            heightDp = 64f,
            isPlaying = true,
            isRecording = false,
            isLocalStream = false,
            hasAlarm = true,
            hasStatus = true
        )
        assertEquals(CompactWidgetLayoutTier.MINI_2X1, cfg.tier)
        assertFalse(cfg.showArtwork)
        assertFalse(cfg.showPrevNextButtons)
        assertFalse(cfg.showRecordButton)
        assertFalse(cfg.showAlarmLine)
    }

    @Test
    fun `2x1 shows recording dot while recording`() {
        val cfg = CompactWidgetLayoutSpec.resolve(
            widthDp = 140f,
            heightDp = 64f,
            isPlaying = true,
            isRecording = true,
            isLocalStream = false,
            hasAlarm = false,
            hasStatus = true
        )
        assertEquals(CompactWidgetLayoutTier.MINI_2X1, cfg.tier)
        assertTrue(cfg.showRecordingDot)
        assertFalse(cfg.showRecordButton)
    }

    @Test
    fun `3x1 medium width resolves to WIDE_BAR with artwork`() {
        val cfg = CompactWidgetLayoutSpec.resolve(
            widthDp = 205f,
            heightDp = 68f,
            isPlaying = true,
            isRecording = false,
            isLocalStream = false,
            hasAlarm = false,
            hasStatus = true
        )
        assertEquals(CompactWidgetLayoutTier.WIDE_BAR, cfg.tier)
        assertTrue(cfg.showArtwork)
        assertFalse(cfg.showPrevNextButtons)
    }

    @Test
    fun `4x1 wide bar shows skip buttons and record button when playing stream`() {
        val cfg = CompactWidgetLayoutSpec.resolve(
            widthDp = 290f,
            heightDp = 70f,
            isPlaying = true,
            isRecording = false,
            isLocalStream = false,
            hasAlarm = true,
            hasStatus = true
        )
        assertEquals(CompactWidgetLayoutTier.FULL_BAR, cfg.tier)
        assertTrue(cfg.showArtwork)
        assertTrue(cfg.showPrevNextButtons)
        assertTrue(cfg.showRecordButton)
        assertFalse(cfg.showAlarmLine)
    }

    @Test
    fun `record button is hidden when stopped or playing local file`() {
        val stoppedCfg = CompactWidgetLayoutSpec.resolve(
            widthDp = 300f,
            heightDp = 150f,
            isPlaying = false,
            isRecording = false,
            isLocalStream = false,
            hasAlarm = true,
            hasStatus = true
        )
        assertEquals(CompactWidgetLayoutTier.EXPANDED_CARD, stoppedCfg.tier)
        assertFalse(stoppedCfg.showRecordButton)

        val localCfg = CompactWidgetLayoutSpec.resolve(
            widthDp = 300f,
            heightDp = 150f,
            isPlaying = true,
            isRecording = false,
            isLocalStream = true,
            hasAlarm = true,
            hasStatus = true
        )
        assertEquals(CompactWidgetLayoutTier.EXPANDED_CARD, localCfg.tier)
        assertFalse(localCfg.showRecordButton)
    }

    @Test
    fun `2x2 tall narrow size resolves to TALL_COMPACT with skip buttons`() {
        val cfg = CompactWidgetLayoutSpec.resolve(
            widthDp = 150f,
            heightDp = 150f,
            isPlaying = true,
            isRecording = false,
            isLocalStream = false,
            hasAlarm = true,
            hasStatus = true
        )
        assertEquals(CompactWidgetLayoutTier.TALL_COMPACT, cfg.tier)
        assertTrue(cfg.showArtwork)
        assertTrue(cfg.showPrevNextButtons)
        assertTrue(cfg.showAlarmLine)
    }

    @Test
    fun `4x3 expanded card enables status, artwork, alarm, and record button`() {
        val cfg = CompactWidgetLayoutSpec.resolve(
            widthDp = 310f,
            heightDp = 200f,
            isPlaying = true,
            isRecording = true,
            isLocalStream = false,
            hasAlarm = true,
            hasStatus = true
        )
        assertEquals(CompactWidgetLayoutTier.EXPANDED_CARD, cfg.tier)
        assertTrue(cfg.showArtwork)
        assertEquals(64, cfg.artworkSizeDp)
        assertTrue(cfg.showPrevNextButtons)
        assertTrue(cfg.showRecordButton)
        assertTrue(cfg.showStatusLine)
        assertTrue(cfg.showAlarmLine)
        assertEquals(2, cfg.titleMaxLines)
    }

    @Test
    fun `formatTextLines filters live broadcast placeholder and uses status when stopped`() {
        val playingLive = CompactWidgetLayoutSpec.formatTextLines(
            stationName = "Vikerraadio",
            title = "Pärastlõunased uudised",
            artist = "Otseeeter",
            extra = "",
            status = "Playing",
            bitrate = "320 kbps",
            isPlaying = true,
            isRecording = false,
            liveBroadcastLabel = "Otseeeter"
        )
        assertEquals("Pärastlõunased uudised", playingLive.primaryLine)
        assertEquals("", playingLive.secondaryLine)
        assertEquals("Playing • 320 kbps", playingLive.statusLine)

        val stopped = CompactWidgetLayoutSpec.formatTextLines(
            stationName = "Vikerraadio",
            title = "Pärastlõunased uudised",
            artist = "ERR",
            extra = "",
            status = "Stopped",
            bitrate = "320 kbps",
            isPlaying = false,
            isRecording = false,
            liveBroadcastLabel = "Otseeeter"
        )
        assertEquals("Stopped", stopped.primaryLine)
        assertEquals("Pärastlõunased uudised", stopped.secondaryLine)
        assertEquals("Stopped", stopped.statusLine)
    }
}
