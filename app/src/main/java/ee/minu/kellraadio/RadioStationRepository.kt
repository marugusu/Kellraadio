package ee.minu.kellraadio

import android.util.Log
import kotlinx.coroutines.flow.Flow

class RadioStationRepository(
    private val apiService: StationApiService,
    private val stationDao: RadioStationDao,
    private val historyDao: HistoryDao? = null
) {
    val allStations: Flow<List<RadioStation>> = stationDao.getAllActiveStations()

    val historyItems: Flow<List<HistoryItem>> = historyDao?.getAllHistory() ?: kotlinx.coroutines.flow.emptyFlow()

    suspend fun toggleFavorite(station: RadioStation) {
        stationDao.updateFavoriteStatus(station.id, !station.isFavorite)
    }

    suspend fun clearHistory() {
        historyDao?.clearAll()
    }

    // SEE ON SEE FUNKTSIOON, MIS OLI PUUDU VÕI VALES KOHAS
    suspend fun insertTestHistory() {
        val now = System.currentTimeMillis()
        val oneDay = 24 * 60 * 60 * 1000L

        historyDao?.let { dao ->
            // Eilne kirje (25h tagasi)
            dao.insert(HistoryItem(stationName = "Test Raadio", artist = "Eilne Esitaja", title = "Eilne Lugu", timestamp = now - (oneDay + 3600000)))

            // Üleeilne kirje (49h tagasi)
            dao.insert(HistoryItem(stationName = "Test Raadio", artist = "Üleeilne Esitaja", title = "Üleeilne Lugu", timestamp = now - (2 * oneDay + 3600000)))

            // 5 päeva tagune kirje
            dao.insert(HistoryItem(stationName = "Test Raadio", artist = "Vana Esitaja", title = "Vana Lugu", timestamp = now - (5 * oneDay)))
        }
    }

    suspend fun refreshStations() {
        try {
            Log.d("RADIO_DEBUG", "Alustan jaamade värskendamist...")
            val favoriteIds = stationDao.getFavoriteIds()
            val remoteStations = apiService.getStations(System.currentTimeMillis())

            if (remoteStations.isNotEmpty()) {
                val updatedStations = remoteStations.map { remote ->
                    remote.copy(isFavorite = favoriteIds.contains(remote.id))
                }
                stationDao.insertAll(updatedStations)
                stationDao.deleteMissing(updatedStations.map { it.id })
                Log.d("RADIO_DEBUG", "Uuendatud. Lemmikud säilitatud.")
            }
        } catch (e: Exception) {
            Log.e("RADIO_DEBUG", "Viga värskendamisel: ${e.message}")
        }
    }
}