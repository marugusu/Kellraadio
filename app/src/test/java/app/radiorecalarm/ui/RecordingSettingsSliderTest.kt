package app.radiorecalarm.ui

import app.radiorecalarm.AppConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingSettingsSliderTest {

    @Test
    fun snapSecondsToStep_clampsAndRoundsToNearestStep() {
        assertEquals(0L, snapSecondsToStep(-10f, 60L, 5L))
        assertEquals(0L, snapSecondsToStep(2.4f, 60L, 5L))
        assertEquals(5L, snapSecondsToStep(2.6f, 60L, 5L))
        assertEquals(15L, snapSecondsToStep(14.99f, 60L, 5L))
        assertEquals(30L, snapSecondsToStep(29.99f, 30L, 5L))
        assertEquals(30L, snapSecondsToStep(45f, 30L, 5L))
    }

    @Test
    fun recordingSliderPresets_alignWithConfiguredBoundsAndStep() {
        val step = AppConfig.Recording.SLIDER_STEP_SECONDS
        assertTrue(step > 0L)
        assertTrue(AppConfig.Recording.MIN_DURATION_PRESETS.all {
            it in 0L..AppConfig.Recording.MIN_DURATION_MAX_SECONDS && it % step == 0L
        })
        assertTrue(AppConfig.Recording.OVERLAP_MARGIN_PRESETS.all {
            it in 0L..AppConfig.Recording.OVERLAP_MARGIN_MAX_SECONDS && it % step == 0L
        })
    }
}
