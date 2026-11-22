package app.radiorecalarm.ui

import app.radiorecalarm.AppConfig
import org.junit.Assert.assertEquals
import org.junit.Test

class RecordingSettingsSliderTest {

    @Test
    fun clampAndRoundSeconds_supportsOneSecondPrecisionAndBounds() {
        assertEquals(0L, clampAndRoundSeconds(-5f, 60L))
        assertEquals(0L, clampAndRoundSeconds(Float.NaN, 60L))
        assertEquals(1L, clampAndRoundSeconds(1.4f, 60L))
        assertEquals(2L, clampAndRoundSeconds(1.6f, 60L))
        assertEquals(17L, clampAndRoundSeconds(17.2f, 60L))
        assertEquals(30L, clampAndRoundSeconds(29.9f, 30L))
        assertEquals(30L, clampAndRoundSeconds(45f, 30L))
    }

    @Test
    fun recordingSliderMaxBounds_matchConfiguredRanges() {
        assertEquals(60L, AppConfig.Recording.MIN_DURATION_MAX_SECONDS)
        assertEquals(30L, AppConfig.Recording.OVERLAP_MARGIN_MAX_SECONDS)
    }
}
