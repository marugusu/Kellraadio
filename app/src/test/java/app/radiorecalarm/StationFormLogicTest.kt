package app.radiorecalarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StationFormLogicTest {

    private val sampleStations = listOf(
        RadioStation(
            id = 1,
            name = "Vikerraadio",
            url = "https://icecast.err.ee/vikerraadio.mp3",
            category = "ERR",
            countryCode = "EE"
        ),
        RadioStation(
            id = 2,
            name = "Rock FM",
            url = "https://stream.skymedia.ee/live/RockFM/",
            category = "Sky",
            countryCode = "EE"
        ),
        RadioStation(
            id = 3,
            name = "Yle Radio 1",
            url = "http://yle.example/radio1.mp3",
            category = "News",
            countryCode = "FI"
        ),
        RadioStation(
            id = 4,
            name = "My Legacy Station",
            url = "https://example.com/legacy.mp3",
            category = "My",
            isUserStation = true,
            countryCode = "EE"
        )
    )

    @Test
    fun isValidStreamUrl_validatesSchemeHostAndWhitespace() {
        assertTrue(StationFormLogic.isValidStreamUrl("https://icecast.err.ee/vikerraadio.mp3"))
        assertTrue(StationFormLogic.isValidStreamUrl("  HTTP://192.168.1.5:8000/stream  "))
        assertFalse(StationFormLogic.isValidStreamUrl(""))
        assertFalse(StationFormLogic.isValidStreamUrl("https://"))
        assertFalse(StationFormLogic.isValidStreamUrl("https:///stream"))
        assertFalse(StationFormLogic.isValidStreamUrl("icecast.err.ee/vikerraadio.mp3"))
        assertFalse(StationFormLogic.isValidStreamUrl("https://bad url/stream"))
    }

    @Test
    fun missingSchemeQuickFix_detectsAndPrependsHttps() {
        assertTrue(StationFormLogic.shouldShowMissingSchemeQuickFix("stream.skymedia.ee/live/RockFM"))
        assertFalse(StationFormLogic.shouldShowMissingSchemeQuickFix("https://stream.skymedia.ee/live/RockFM"))
        assertFalse(StationFormLogic.shouldShowMissingSchemeQuickFix("ab"))

        assertEquals(
            "https://stream.skymedia.ee/live/RockFM",
            StationFormLogic.ensureHttpsPrefix("  stream.skymedia.ee/live/RockFM ")
        )
        assertEquals(
            "http://example.com/live",
            StationFormLogic.ensureHttpsPrefix("http://example.com/live")
        )
    }

    @Test
    fun findDuplicateStationByUrl_normalizesTrailingSlashAndExcludesEditingId() {
        val duplicate = StationFormLogic.findDuplicateStationByUrl(
            url = " HTTPS://stream.skymedia.ee/live/RockFM ",
            existingStations = sampleStations
        )
        assertNotNull(duplicate)
        assertEquals(2, duplicate?.id)

        val selfEdit = StationFormLogic.findDuplicateStationByUrl(
            url = "https://stream.skymedia.ee/live/RockFM",
            existingStations = sampleStations,
            editingStationId = 2
        )
        assertNull(selfEdit)
    }

    @Test
    fun buildCountryPickerItems_worksOfflineAndPrioritizesEstoniaAndLocalCountries() {
        val items = StationFormLogic.buildCountryPickerItems(
            remoteCountries = listOf(
                RadioFilterItem(name = "Germany", stationCount = 5000, isoCode = "DE"),
                RadioFilterItem(name = "Russia", stationCount = 999, isoCode = "RU")
            ),
            existingStations = sampleStations,
            includeAllIsoFallback = true,
            displayNameForCode = { code ->
                when (code) {
                    "EE" -> "Estonia"
                    "FI" -> "Finland"
                    "DE" -> "Germany"
                    else -> code
                }
            }
        )

        assertFalse(items.any { it.isoCode == "RU" })
        assertEquals("EE", items.first().isoCode)
        assertEquals("FI", items[1].isoCode)
        assertTrue(items.size > 100)
        assertEquals(5000, items.first { it.isoCode == "DE" }.stationCount)
    }

    @Test
    fun sortAndFilterSubgroups_prioritizesSelectedCountrySubgroupsAndIgnoresLegacyMy() {
        val sortedForEe = StationFormLogic.sortSubgroupsForCountry(
            allSubgroups = listOf("Pop", "News", "ERR", "Sky", "My"),
            countryCode = "EE",
            existingStations = sampleStations
        )
        assertEquals(listOf("ERR", "Sky", "News", "Pop"), sortedForEe)

        val filtered = StationFormLogic.filterSubgroups(sortedForEe, "sk")
        assertEquals(listOf("Sky"), filtered)
    }
}
