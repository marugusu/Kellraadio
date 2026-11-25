package app.radiorecalarm.ui

import app.radiorecalarm.SongAdditionalInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SongInfoFormattingTest {

    @Test
    fun formatTrackDuration_handlesNullZeroMinutesAndHours() {
        assertNull(formatTrackDuration(null))
        assertNull(formatTrackDuration(0L))
        assertNull(formatTrackDuration(500L))
        assertEquals("3:42", formatTrackDuration(222_000L))
        assertEquals("1:05:09", formatTrackDuration(3_909_000L))
    }

    @Test
    fun buildTrackMetadataPills_formatsYearGenreTrackAndDuration() {
        val fullInfo = SongAdditionalInfo(
            year = "2017",
            genre = "Rock",
            trackNumber = 3,
            durationMs = 219_000L
        )
        assertEquals(listOf("2017", "Rock", "#3 • 3:39"), buildTrackMetadataPills(fullInfo))

        val durationOnly = SongAdditionalInfo(durationMs = 185_000L)
        assertEquals(listOf("3:05"), buildTrackMetadataPills(durationOnly))
    }

    @Test
    fun formatSourceLabel_combinesStationAndBitrate() {
        assertEquals("Retro FM • 128 kbps", formatSourceLabel("Retro FM", "128 kbps"))
        assertEquals("Retro FM", formatSourceLabel("Retro FM", ""))
        assertEquals("128 kbps", formatSourceLabel("", "128 kbps"))
        assertNull(formatSourceLabel("  ", ""))
    }

    @Test
    fun teaserTextBuilders_prioritizeAlbumAndMetadataSummary() {
        val infoWithMeta = SongAdditionalInfo(
            album = "Christmas Christmas",
            year = "2017",
            genre = "Rock",
            durationMs = 219_000L
        )
        assertEquals(
            "Christmas Christmas",
            buildTeaserPrimaryText(infoWithMeta, "CHEAP TRICK", "Merry Christmas Darlings")
        )
        assertEquals(
            "2017 • Rock • 3:39",
            buildTeaserSecondaryText(infoWithMeta, "CHEAP TRICK", "Merry Christmas Darlings", "Retro FM")
        )

        val infoAlbumOnly = SongAdditionalInfo(album = "Üle ilma")
        assertEquals(
            "Curly Strings – Kauges külas",
            buildTeaserSecondaryText(infoAlbumOnly, "Curly Strings", "Kauges külas", "Raadio Elmar")
        )
        assertEquals(
            "Curly Strings – Kauges külas",
            buildTeaserPrimaryText(null, "Curly Strings", "Kauges külas")
        )
        assertEquals(
            "Raadio Elmar",
            buildTeaserSecondaryText(null, "Curly Strings", "Kauges külas", "Raadio Elmar")
        )
    }

    @Test
    fun buildLyricsWebSearchQuery_trimsBlankParts() {
        assertEquals("Anne Veski Viimne pilet lyrics", buildLyricsWebSearchQuery("Anne Veski", "Viimne pilet"))
        assertEquals("Viimne pilet lyrics", buildLyricsWebSearchQuery("", "Viimne pilet"))
    }
}
