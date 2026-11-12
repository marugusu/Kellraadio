package app.radiorecalarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StationListFilterTest {

    private val sampleStations = listOf(
        RadioStation(
            id = 1,
            name = "Vikerraadio",
            url = "https://icecast.err.ee/vikerraadio.mp3",
            priority = 2,
            category = "ERR",
            isFavorite = true,
            isUserStation = false,
            countryCode = "EE",
            favoriteOrder = 2
        ),
        RadioStation(
            id = 2,
            name = "Rock FM",
            url = "https://stream.skymedia.ee/live/RockFM",
            priority = 1,
            category = "Sky",
            isFavorite = true,
            isUserStation = false,
            countryCode = "EE",
            favoriteOrder = 1
        ),
        RadioStation(
            id = 3,
            name = "Yle Radio Suomi",
            url = "https://yle.example/suomi.mp3",
            priority = 1,
            category = "Uudised",
            isFavorite = false,
            isUserStation = false,
            countryCode = "FI"
        ),
        RadioStation(
            id = 4,
            name = "Minu Legacy Jaam",
            url = "https://example.com/legacy.mp3",
            category = "My",
            isFavorite = false,
            isUserStation = true,
            countryCode = "EE"
        ),
        RadioStation(
            id = 5,
            name = "Minu Jazz",
            url = "https://example.com/jazz.mp3",
            category = "Jazz",
            isFavorite = true,
            isUserStation = true,
            countryCode = "EE",
            favoriteOrder = 1
        )
    )

    @Test
    fun visibleStations_respectsHideCuratedFlag() {
        val allVisible = StationListFilter.visibleStations(sampleStations, hideCuratedStations = false)
        assertEquals(5, allVisible.size)

        val userOnly = StationListFilter.visibleStations(sampleStations, hideCuratedStations = true)
        assertEquals(listOf(4, 5), userOnly.map { it.id })
    }

    @Test
    fun buildMainCategories_handlesEmptyFavoritesMyCountriesAndHideCurated() {
        assertEquals(emptyList<String>(), StationListFilter.buildMainCategories(emptyList(), false))

        val categoriesAll = StationListFilter.buildMainCategories(sampleStations, hideCuratedStations = false)
        assertEquals(listOf("Favorites", "My", "EE", "FI", "All"), categoriesAll)

        val userOnlyStations = StationListFilter.visibleStations(sampleStations, hideCuratedStations = true)
        val categoriesHidden = StationListFilter.buildMainCategories(userOnlyStations, hideCuratedStations = true)
        assertEquals(listOf("Favorites", "My", "EE"), categoriesHidden)
        assertFalse(categoriesHidden.contains("All"))

        val curatedNoFavs = sampleStations.filter { !it.isUserStation }.map { it.copy(isFavorite = false) }
        val categoriesCurated = StationListFilter.buildMainCategories(curatedNoFavs, hideCuratedStations = false)
        assertEquals(listOf("EE", "FI", "All"), categoriesCurated)
    }

    @Test
    fun resolveValidCategory_switchesWhenSelectedTabDisappears() {
        val available = listOf("EE", "FI", "All")
        assertEquals("EE", StationListFilter.resolveValidCategory("Favorites", available))
        assertEquals("EE", StationListFilter.resolveValidCategory("My", available))
        assertEquals("FI", StationListFilter.resolveValidCategory("FI", available))
        assertEquals("Favorites", StationListFilter.resolveValidCategory("Favorites", emptyList()))
    }

    @Test
    fun buildSubCategories_ignoresLegacyMyOnUserStationsAndRespectsCustomOrder() {
        assertTrue(StationListFilter.buildSubCategories("Favorites", sampleStations).isEmpty())
        assertTrue(StationListFilter.buildSubCategories("My", sampleStations).isEmpty())

        val eeSubs = StationListFilter.buildSubCategories("EE", sampleStations)
        assertFalse(eeSubs.contains("My"))
        assertEquals(listOf("ERR", "Sky", "Jazz"), eeSubs)

        val allSubs = StationListFilter.buildSubCategories("All", sampleStations)
        assertFalse(allSubs.contains("My"))
        assertEquals(listOf("ERR", "Jazz", "Sky", "Uudised"), allSubs)
    }

    @Test
    fun filterAndSortStations_ordersFavoritesAndAppliesSubCategoryFilters() {
        val favs = StationListFilter.filterAndSortStations(
            stations = sampleStations,
            category = StationListFilter.CATEGORY_FAVORITES,
            subCategories = setOf("Sky")
        )
        // Favorites ignores subgroup filter and sorts by favoriteOrder -> priority -> name
        assertEquals(listOf(2, 5, 1), favs.map { it.id })

        val eeFiltered = StationListFilter.filterAndSortStations(
            stations = sampleStations,
            category = "EE",
            subCategories = setOf("Jazz", "Sky")
        )
        assertEquals(listOf(2, 5), eeFiltered.map { it.id })

        val myStations = StationListFilter.filterAndSortStations(
            stations = sampleStations,
            category = StationListFilter.CATEGORY_MY,
            subCategories = setOf("Jazz")
        )
        assertEquals(listOf(4, 5), myStations.map { it.id })
    }
}
