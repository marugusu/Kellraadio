package ee.minu.kellraadio

import android.util.Log
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class RadioStationRepository(
    private val apiService: StationApiService,
    private val searchApiService: RadioBrowserApiService, // Veendu, et see on siin
    private val stationDao: RadioStationDao,
    private val historyDao: HistoryDao? = null,
    private var countriesCache: List<RadioFilterItem>? = null,
    private var tagsCache: List<RadioFilterItem>? = null
) {
    val allStations: Flow<List<RadioStation>> = stationDao.getAllActiveStations()

    val historyItems: Flow<List<HistoryItem>> = historyDao?.getAllHistory() ?: kotlinx.coroutines.flow.emptyFlow()

    suspend fun toggleFavorite(station: RadioStation) {
        stationDao.updateFavoriteStatus(station.id, !station.isFavorite)
    }

    // Kasutaja jaama kustutamine
    suspend fun deleteStation(station: RadioStation) {
        stationDao.delete(station)
    }

    // Kasutaja jaama salvestamine (UUS)
    suspend fun saveUserStation(name: String, url: String, countryCode: String = "") {
        val maxId = stationDao.getMaxId() ?: 9999
        val newId = if (maxId < 10000) 10000 else maxId + 1

        val newStation = RadioStation(
            id = newId,
            name = name,
            url = url,
            category = "Minu",
            priority = 10000 + (newId - 10000),
            isUserStation = true,
            uuid = UUID.randomUUID().toString(),
            countryCode = countryCode // <--- SALVESTAME
        )
        stationDao.insert(newStation)
    }

    // Kasutaja jaama muutmine (UUS)
    suspend fun updateUserStation(station: RadioStation, newName: String, newUrl: String) {
        val updatedStation = station.copy(name = newName, url = newUrl)
        stationDao.update(updatedStation)
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

    suspend fun refreshStations() {
        try {
            Log.d("RADIO_DEBUG", "Alustan jaamade värskendamist...")
            val favoriteIds = stationDao.getFavoriteIds()
            val remoteStations = apiService.getStations(System.currentTimeMillis())

            if (remoteStations.isNotEmpty()) {
                val updatedStations = remoteStations.map { remote ->
                    // Siin teeme koopia API-st saadud objektist
                    remote.copy(
                        // Taastame lemmiku staatuse (sest API-st tuleb see false)
                        isFavorite = favoriteIds.contains(remote.id),
                        // Märgime, et see on süsteemne jaam
                        isUserStation = false,
                        uuid = ""
                        // NB! countryCode välja me siin ei puutu,
                        // see tähendab, et see jääb selliseks, nagu see API-st (remote) tuli.
                        // See ongi see, mida me tahame.
                    )
                }
                stationDao.insertAll(updatedStations)
                stationDao.deleteMissing(updatedStations.map { it.id })
                Log.d("RADIO_DEBUG", "Uuendatud.")
            }
        } catch (e: Exception) {
            Log.e("RADIO_DEBUG", "Viga värskendamisel: ${e.message}")
        }
    }

    // --- SEE ON NÜÜD AINUS OTSINGU FUNKTSIOON ---
    suspend fun searchStations(query: String): List<RadioBrowserStation> {
        return try {
            if (query.length < 2) return emptyList()

            // 1. Teeme päringu (kasutame advancedSearch)
            val rawResults = searchApiService.advancedSearch(
                name = query
            )

            // 2. TURVAFILTER: Blokeerime Venemaa (RU)
            return rawResults.filter { station ->
                station.countryCode != "RU" && !station.country.contains("Russia", ignoreCase = true)
            }
        } catch (e: Exception) {
            Log.e("RADIO_DEBUG", "Otsingu viga: ${e.message}")
            emptyList()
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

    // UUENDATUD OTSING
    suspend fun searchStations(query: String, country: String?, tag: String?): List<RadioBrowserStation> {
        return try {
            // Otsing peab olema võimalik ka ilma nimeta, kui on valitud filter
            // Aga API nõuab midagi. Kui nimi on tühi, aga filter on, saadame nimeks "" (tühi).
            // Kui kõik on tühjad, tagastame tühja listi.
            if (query.length < 2 && country == null && tag == null) return emptyList()

            val rawResults = searchApiService.advancedSearch(
                name = query,
                country = country,
                tag = tag
            )

            // Turvafilter (Venemaa)
            rawResults.filter { station ->
                station.countryCode != "RU" && !station.country.contains("Russia", ignoreCase = true)
            }
        } catch (e: Exception) {
            Log.e("RADIO_DEBUG", "Otsingu viga: ${e.message}")
            emptyList()
        }
    }
}