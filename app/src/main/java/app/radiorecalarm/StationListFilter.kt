package app.radiorecalarm

/**
 * Ühine tõeallikas jaamade nähtavuse, peagruppide, alamgruppide ja filtreeritud järjekorra jaoks.
 * Kasutavad nii UI (`MainActivity`, `StationList`) kui ka taustateenus (`RadioService`).
 */
object StationListFilter {

    const val CATEGORY_FAVORITES = "Favorites"
    const val CATEGORY_MY = "My"
    const val CATEGORY_ALL = "All"

    fun visibleStations(allStations: List<RadioStation>, hideCuratedStations: Boolean): List<RadioStation> {
        return if (hideCuratedStations) {
            allStations.filter { it.isUserStation }
        } else {
            allStations
        }
    }

    fun buildMainCategories(
        stations: List<RadioStation>,
        hideCuratedStations: Boolean,
        countrySortKey: (String) -> String = { it }
    ): List<String> {
        if (stations.isEmpty()) return emptyList()

        val groups = mutableListOf<String>()
        if (stations.any { it.isFavorite }) groups.add(CATEGORY_FAVORITES)
        if (stations.any { it.isUserStation }) groups.add(CATEGORY_MY)

        val sortedCountries = stations
            .map { it.countryCode }
            .filter { it.isNotEmpty() }
            .distinct()
            .sortedWith(
                compareBy<String> { code ->
                    val index = AppConfig.UI.PREFERRED_COUNTRY_ORDER.indexOf(code)
                    if (index != -1) index else Int.MAX_VALUE
                }.thenBy { countrySortKey(it) }
            )

        groups.addAll(sortedCountries)
        if (!hideCuratedStations) {
            groups.add(CATEGORY_ALL)
        }
        return groups
    }

    fun resolveValidCategory(selectedCategory: String, availableCategories: List<String>): String {
        if (availableCategories.isEmpty()) return selectedCategory
        return if (availableCategories.contains(selectedCategory)) {
            selectedCategory
        } else {
            availableCategories.first()
        }
    }

    fun buildSubCategories(selectedCategory: String, stations: List<RadioStation>): List<String> {
        if (selectedCategory.length != 2 && selectedCategory != CATEGORY_ALL) {
            return emptyList()
        }

        val stationsInGroup = if (selectedCategory == CATEGORY_ALL) {
            stations
        } else {
            stations.filter { it.countryCode == selectedCategory }
        }

        // Ignoreerime ajaloolist kunstlikku "My" väärtust kasutaja enda jaamadel,
        // sest kasutaja jaamade jaoks on peagruppides olemas eraldi "Minu jaamad" vaheleht.
        val subGroups = stationsInGroup
            .filter { !(it.isUserStation && it.category.equals(CATEGORY_MY, ignoreCase = true)) }
            .map { it.category.trim() }
            .filter { it.isNotBlank() }
            .distinct()

        val customOrder = if (selectedCategory == "EE") AppConfig.UI.ESTONIAN_SUB_CATEGORY_ORDER else emptyList()

        return subGroups.sortedWith(
            compareBy<String> { sub ->
                val index = customOrder.indexOf(sub)
                if (index != -1) index else Int.MAX_VALUE
            }.thenBy { it }
        )
    }

    fun filterAndSortStations(
        stations: List<RadioStation>,
        category: String,
        subCategories: Set<String> = emptySet()
    ): List<RadioStation> {
        val baseList = when (category) {
            CATEGORY_FAVORITES -> stations
                .filter { it.isFavorite }
                .sortedWith(compareBy<RadioStation> { it.favoriteOrder }.thenBy { it.priority }.thenBy { it.name })
            CATEGORY_MY -> stations.filter { it.isUserStation }
            CATEGORY_ALL, "" -> stations
            else -> stations.filter { it.countryCode == category }
        }

        val supportsSubCategories = category.length == 2 || category == CATEGORY_ALL
        return if (!supportsSubCategories || subCategories.isEmpty()) {
            baseList
        } else {
            baseList.filter { station -> subCategories.contains(station.category.trim()) }
        }
    }
}
