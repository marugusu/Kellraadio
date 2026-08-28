package app.radiorecalarm

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import java.util.UUID

class RadioStationRepository(
    private val apiService: StationApiService,
    private val searchApiService: RadioBrowserApiService,
    private val stationDao: RadioStationDao,
    private val historyDao: HistoryDao? = null,
    private var countriesCache: List<RadioFilterItem>? = null,
    private var tagsCache: List<RadioFilterItem>? = null
) {
    val allStations: Flow<List<RadioStation>> = stationDao.getAllActiveStations()

    val historyItems: Flow<List<HistoryItem>> = historyDao?.getAllHistory() ?: kotlinx.coroutines.flow.emptyFlow()

    suspend fun toggleFavorite(station: RadioStation) {
        val willBeFavorite = !station.isFavorite
        if (willBeFavorite) {
            val maxOrder = stationDao.getMaxFavoriteOrder() ?: 0
            stationDao.update(station.copy(isFavorite = true, favoriteOrder = maxOrder + 1))
        } else {
            stationDao.updateFavoriteStatus(station.id, false)
        }
    }

    suspend fun deleteStation(station: RadioStation) {
        stationDao.delete(station)
    }

    suspend fun saveUserStation(name: String, url: String, countryCode: String, category: String): Int {
        val maxId = stationDao.getMaxId() ?: 9999
        val newId = if (maxId < 10000) 10000 else maxId + 1

        val stationCategory = category.ifBlank { "My" }

        val newStation = RadioStation(
            id = newId,
            name = name,
            url = url,
            category = stationCategory,
            priority = 10000 + (newId - 10000),
            isUserStation = true,
            uuid = UUID.randomUUID().toString(),
            countryCode = countryCode,
            isFavorite = false
        )
        stationDao.insert(newStation)
        return newId
    }

    suspend fun updateUserStation(station: RadioStation, newName: String, newUrl: String, newCountryCode: String, newCategory: String) {
        val stationCategory = newCategory.ifBlank { "My" }
        val updatedStation = station.copy(name = newName, url = newUrl, countryCode = newCountryCode, category = stationCategory)
        stationDao.update(updatedStation)
    }

    private suspend fun ensureNormalized(favorites: List<RadioStation>): List<RadioStation> {
        val needsNormalization = favorites.map { it.favoriteOrder }.distinct().size != favorites.size ||
                                 favorites.any { it.favoriteOrder == 0 }

        return if (needsNormalization) {
            val normalized = favorites.mapIndexed { index, station ->
                station.copy(favoriteOrder = index + 1)
            }
            normalized.forEach { stationDao.update(it) }
            normalized
        } else {
            favorites
        }
    }

    suspend fun moveStationUp(station: RadioStation, currentFavorites: List<RadioStation>) {
        val favorites = ensureNormalized(currentFavorites)
        val index = favorites.indexOfFirst { it.id == station.id }
        if (index > 0) {
            val current = favorites[index]
            val prev = favorites[index - 1]
            stationDao.update(current.copy(favoriteOrder = prev.favoriteOrder))
            stationDao.update(prev.copy(favoriteOrder = current.favoriteOrder))
        }
    }

    suspend fun moveStationDown(station: RadioStation, currentFavorites: List<RadioStation>) {
        val favorites = ensureNormalized(currentFavorites)
        val index = favorites.indexOfFirst { it.id == station.id }
        if (index != -1 && index < favorites.size - 1) {
            val current = favorites[index]
            val next = favorites[index + 1]
            stationDao.update(current.copy(favoriteOrder = next.favoriteOrder))
            stationDao.update(next.copy(favoriteOrder = current.favoriteOrder))
        }
    }

    suspend fun moveStationToTop(station: RadioStation, currentFavorites: List<RadioStation>) {
        if (currentFavorites.isEmpty()) return
        val minOrder = currentFavorites.minOfOrNull { it.favoriteOrder } ?: 0
        stationDao.update(station.copy(favoriteOrder = minOrder - 1))
    }

    suspend fun moveStationToBottom(station: RadioStation, currentFavorites: List<RadioStation>) {
        if (currentFavorites.isEmpty()) return
        val maxOrder = currentFavorites.maxOfOrNull { it.favoriteOrder } ?: 0
        stationDao.update(station.copy(favoriteOrder = maxOrder + 1))
    }

    suspend fun resetFavoriteOrder() {
        stationDao.resetFavoriteOrders()
    }

    suspend fun clearHistory() {
        historyDao?.clearAll()
    }

    suspend fun insertTestHistory() {
        val now = System.currentTimeMillis()
        val oneDay = 24 * 60 * 60 * 1000L
        historyDao?.let { dao ->
            dao.insert(HistoryItem(stationName = "Test Raadio", artist = "Eilne Esitaja", title = "Eilne Lugu", timestamp = now - (oneDay + 3600000)))
            dao.insert(HistoryItem(stationName = "Test Raadio", artist = "Üleeilne Esitaja", title = "Üleeilne Lugu", timestamp = now - (2 * oneDay + 3600000)))
        }
    }

    suspend fun loadOfflineStationsIfEmpty(context: Context) {
        try {
            val existingStations = stationDao.getAllActiveStationsSync()
            if (existingStations.isEmpty()) {
                context.assets.open("stations.json").use { inputStream ->
                    val jsonText = inputStream.bufferedReader().readText()
                    val offlineStations = json.decodeFromString<List<RadioStation>>(jsonText)

                    if (offlineStations.isNotEmpty()) {
                        stationDao.insertAll(offlineStations)
                        Log.d("RADIO_DEBUG", "Laaditud ${offlineStations.size} jaama kohalikust varukoopiast (assets).")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("RADIO_DEBUG", "Viga kohalike jaamade laadimisel: ${e.message}")
        }
    }

    suspend fun refreshStations() {
        try {
            Log.d("RADIO_DEBUG", "Alustan jaamade värskendamist...")
            val existingStations = stationDao.getAllActiveStationsSync()
            val favoriteById = existingStations.filter { it.isFavorite }.associate { it.id to it.favoriteOrder }
            val favoriteByUrl = existingStations.filter { it.isFavorite }.associate { it.url to it.favoriteOrder }
            val remoteStations = apiService.getStations(System.currentTimeMillis())

            if (remoteStations.isNotEmpty()) {
                val updatedStations = remoteStations.map { remote ->
                    val isFav = favoriteById.containsKey(remote.id) || favoriteByUrl.containsKey(remote.url)
                    val favOrder = favoriteById[remote.id] ?: favoriteByUrl[remote.url] ?: 0
                    remote.copy(
                        isFavorite = isFav,
                        favoriteOrder = favOrder,
                        isUserStation = false,
                        uuid = ""
                    )
                }
                stationDao.insertAll(updatedStations)
                stationDao.deleteMissing(updatedStations.map { it.id })
                Log.d("RADIO_DEBUG", "Jaamade nimekiri edukalt uuendatud.")
            }
        } catch (e: Exception) {
            Log.e("RADIO_DEBUG", "Viga värskendamisel: ${e.message}")
        }
    }

    suspend fun getCountries(): List<RadioFilterItem> {
        if (countriesCache != null) return countriesCache!!
        return try {
            val list = searchApiService.getCountries()
                .filter { it.stationCount > 10 }
                .filter { !it.name.contains("Russia", ignoreCase = true) && !it.name.contains("Soviet", ignoreCase = true) }
            countriesCache = list
            list
        } catch (e: Exception) { emptyList() }
    }

    suspend fun getTags(): List<RadioFilterItem> {
        if (tagsCache != null) return tagsCache!!
        return try {
            val list = searchApiService.getTags().filter { it.stationCount > 10 }
            tagsCache = list
            list
        } catch (e: Exception) { emptyList() }
    }

    suspend fun searchStations(query: String, countryCode: String?, tag: String?): List<RadioBrowserStation> {
        return try {
            if (query.length < 2 && countryCode == null && tag == null) return emptyList()
            val rawResults = searchApiService.advancedSearch(
                name = query,
                countryCode = countryCode,
                tag = tag
            )
            rawResults.filter { station ->
                station.countryCode != "RU" && !station.country.contains("Russia", ignoreCase = true)
            }
        } catch (e: Exception) {
            android.util.Log.e("RADIO_DEBUG", "Otsingu viga: ${e.message}")
            emptyList()
        }
    }

    companion object {
        private val json = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
        }
    }
}
