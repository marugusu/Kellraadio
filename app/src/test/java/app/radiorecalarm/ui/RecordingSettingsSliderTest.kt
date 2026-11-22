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
        assertEquals(30L, AppConfig.Recording.PRE_ROLL_MAX_SECONDS)
        assertEquals(30L, AppConfig.Recording.POST_ROLL_MAX_SECONDS)
        assertEquals(10L, AppConfig.Recording.PRE_ROLL_SECONDS)
        assertEquals(5L, AppConfig.Recording.POST_ROLL_SECONDS)
    }

    @Test
    fun resolveMarginSeconds_migratesLegacyPreferenceAndRespectsExplicitValues() {
        val emptyPrefs = emptyMap<String, Long>()
        assertEquals(
            10L,
            AppConfig.Recording.resolveMarginSeconds(
                key = AppConfig.Recording.PREF_PRE_ROLL_SECONDS,
                defaultSeconds = AppConfig.Recording.PRE_ROLL_SECONDS,
                maxSeconds = AppConfig.Recording.PRE_ROLL_MAX_SECONDS,
                readLongOrNull = emptyPrefs::get
            )
        )
        assertEquals(
            5L,
            AppConfig.Recording.resolveMarginSeconds(
                key = AppConfig.Recording.PREF_POST_ROLL_SECONDS,
                defaultSeconds = AppConfig.Recording.POST_ROLL_SECONDS,
                maxSeconds = AppConfig.Recording.POST_ROLL_MAX_SECONDS,
                readLongOrNull = emptyPrefs::get
            )
        )

        val legacyOnlyPrefs = mapOf(AppConfig.Recording.PREF_LEGACY_OVERLAP_MARGIN_SECONDS to 20L)
        assertEquals(
            20L,
            AppConfig.Recording.resolveMarginSeconds(
                key = AppConfig.Recording.PREF_PRE_ROLL_SECONDS,
                defaultSeconds = AppConfig.Recording.PRE_ROLL_SECONDS,
                maxSeconds = AppConfig.Recording.PRE_ROLL_MAX_SECONDS,
                readLongOrNull = legacyOnlyPrefs::get
            )
        )
        assertEquals(
            20L,
            AppConfig.Recording.resolveMarginSeconds(
                key = AppConfig.Recording.PREF_POST_ROLL_SECONDS,
                defaultSeconds = AppConfig.Recording.POST_ROLL_SECONDS,
                maxSeconds = AppConfig.Recording.POST_ROLL_MAX_SECONDS,
                readLongOrNull = legacyOnlyPrefs::get
            )
        )

        val partiallyMigratedPrefs = mapOf(
            AppConfig.Recording.PREF_LEGACY_OVERLAP_MARGIN_SECONDS to 20L,
            AppConfig.Recording.PREF_PRE_ROLL_SECONDS to 12L
        )
        assertEquals(
            12L,
            AppConfig.Recording.resolveMarginSeconds(
                key = AppConfig.Recording.PREF_PRE_ROLL_SECONDS,
                defaultSeconds = AppConfig.Recording.PRE_ROLL_SECONDS,
                maxSeconds = AppConfig.Recording.PRE_ROLL_MAX_SECONDS,
                readLongOrNull = partiallyMigratedPrefs::get
            )
        )
        assertEquals(
            20L,
            AppConfig.Recording.resolveMarginSeconds(
                key = AppConfig.Recording.PREF_POST_ROLL_SECONDS,
                defaultSeconds = AppConfig.Recording.POST_ROLL_SECONDS,
                maxSeconds = AppConfig.Recording.POST_ROLL_MAX_SECONDS,
                readLongOrNull = partiallyMigratedPrefs::get
            )
        )
    }
}
