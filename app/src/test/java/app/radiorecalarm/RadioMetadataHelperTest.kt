package app.radiorecalarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RadioMetadataHelperTest {

    private lateinit var helper: RadioMetadataHelper

    @Before
    fun setUp() {
        helper = RadioMetadataHelper(null, "Otseeeter")
    }

    @Test
    fun testNormalArtistAndTitle() {
        val result = helper.parse("Queen - Bohemian Rhapsody", "Rock FM")
        assertEquals("Queen", result.artist)
        assertEquals("Bohemian Rhapsody", result.title)
        assertEquals("", result.extra)
    }

    @Test
    fun testTitleWithParenthesesStrippedNotBecomingBlank() {
        // Enne muudatust: sulgude eemaldamisel jäi pealkiri tühjaks ""
        val result = helper.parse("Queen - (Live)", "Rock FM")
        assertEquals("Queen", result.artist)
        assertEquals("Live", result.title)
        assertFalse(result.title.isBlank())
    }

    @Test
    fun testTitleWithYearOnlyInParentheses() {
        val result = helper.parse("Artist - (2024)", "MyHits")
        assertEquals("Artist", result.artist)
        assertEquals("2024", result.title)
        assertFalse(result.title.isBlank())
    }

    @Test
    fun testTitleWithYearStarPattern() {
        val result = helper.parse("Tracy Chapman - Fast Car * 1988", "Retro FM")
        assertEquals("Tracy Chapman", result.artist)
        assertEquals("Fast Car", result.title)
        assertEquals("1988", result.extra)
    }

    @Test
    fun testControlCharactersSanitization() {
        // Äärealal striimi katkendlikud kontrollbaidid (\u0000, \u0007, \u001F)
        val result = helper.parse("Queen\u0000 - Bohemian\u0007 Rhapsody\u001F", "Rock FM")
        assertEquals("Queen", result.artist)
        assertEquals("Bohemian Rhapsody", result.title)
    }

    @Test
    fun testOnlyStationNameSent() {
        // Kui striim saadab ainult jaama enda nime
        val result = helper.parse("Kuku Raadio", "Kuku Raadio")
        assertEquals("Otseeeter", result.artist)
        assertEquals("Kuku Raadio", result.title)
    }

    @Test
    fun testLiveStreamStringSent() {
        val result = helper.parse("Live Stream", "Raadio 2")
        assertEquals("Otseeeter", result.artist)
        assertEquals("Raadio 2", result.title)
    }

    @Test
    fun testGarbageInputsReturnSafeDefaults() {
        val inputs = listOf("", " ", "-", " - ", " . ", "•", "_", "---", "{+info: stream metadata}")
        for (input in inputs) {
            val result = helper.parse(input, "Vikerraadio")
            assertEquals("Otseeeter", result.artist)
            assertEquals("Vikerraadio", result.title)
            assertFalse(result.artist.isBlank())
            assertFalse(result.title.isBlank())
        }
    }

    @Test
    fun testEmptyStationNameFallback() {
        val result = helper.parse("", "")
        assertEquals("Otseeeter", result.artist)
        assertEquals("Radio", result.title)
        assertFalse(result.title.isBlank())
    }

    @Test
    fun testStarFmReversedLogicPreserved() {
        // Star FM 80s / 90s puhul on striimis esmalt Title ja siis Artist
        val result = helper.parse("The Final Countdown - Europe", "Star FM 80s")
        assertEquals("Europe", result.artist)
        assertEquals("The Final Countdown", result.title)
    }

    @Test
    fun testFeaturingArtistSplitPreserved() {
        val result = helper.parse("David Guetta [+] Bebe Rexha - I'm Good (Blue)", "Power Hit Radio")
        assertEquals("David Guetta", result.artist)
        assertEquals("I'm Good", result.title)
        assertTrue(result.extra.contains("Bebe Rexha"))
        assertTrue(result.extra.contains("Blue"))
    }

    @Test
    fun testIHeartSpotBlockEndReturnsLiveBroadcast() {
        val raw = """ - text="Spot Block End" amgTrackId="9876543" length="00:00:00""""
        val result = helper.parse(raw, "102.7 KIIS FM")
        assertEquals("Otseeeter", result.artist)
        assertEquals("102.7 KIIS FM", result.title)
        assertEquals("", helper.sanitizeRawInput(raw))
    }

    @Test
    fun testIHeartTalkSpotReturnsLiveBroadcast() {
        val raw = """NY's #1 Hit Music Station - text="Z100" song_spot="T" MediaBaseId="0" itunesTrackId="0" amgTrackId="0" length="00:00:00""""
        val result = helper.parse(raw, "Z100 New York")
        assertEquals("Otseeeter", result.artist)
        assertEquals("Z100 New York", result.title)
        assertEquals("", helper.sanitizeRawInput(raw))
    }

    @Test
    fun testIHeartStructuredSongMetadataExtractsArtistAndTitle() {
        val raw = """title="One More Night",artist="MAROON 5",url="song_spot="F" MediaBaseId="0" itunesTrackId="0" amgTrackId="-1" length="00:03:38""""
        val result = helper.parse(raw, "102.7 KIIS FM")
        assertEquals("MAROON 5", result.artist)
        assertEquals("One More Night", result.title)
        assertEquals("MAROON 5 - One More Night", helper.sanitizeRawInput(raw))
    }

    @Test
    fun testIHeartStructuredSongWithCommaInTitle() {
        val raw = """title="Hello, Goodbye",artist="The Beatles",url="song_spot="M" amgTrackId="123" length="00:03:27""""
        val result = helper.parse(raw, "Z100 New York")
        assertEquals("The Beatles", result.artist)
        assertEquals("Hello, Goodbye", result.title)
    }

    @Test
    fun testIHeartDashTextMusicMetadataExtractsArtistAndTitle() {
        val raw = """MAROON 5 - text="One More Night" song_spot="M" MediaBaseId="123" length="00:03:38""""
        val result = helper.parse(raw, "102.7 KIIS FM")
        assertEquals("MAROON 5", result.artist)
        assertEquals("One More Night", result.title)
        assertEquals("MAROON 5 - One More Night", helper.sanitizeRawInput(raw))
    }
}
