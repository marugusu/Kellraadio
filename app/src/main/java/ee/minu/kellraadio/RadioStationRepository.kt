package ee.minu.kellraadio

import android.util.Log
import kotlinx.coroutines.flow.Flow

class RadioStationRepository(
    private val apiService: StationApiService,
    private val stationDao: RadioStationDao,
    private val historyDao: HistoryDao? = null // UUS: Võib olla null esialgu, et mitte lõhkuda vana koodi
) {
    val allStations: Flow<List<RadioStation>> = stationDao.getAllActiveStations()

    // UUS: Ajaloo voog (Flow)
    val historyItems: Flow<List<HistoryItem>> = historyDao?.getAllHistory() ?: kotlinx.coroutines.flow.emptyFlow()

    suspend fun toggleFavorite(station: RadioStation) {
        stationDao.updateFavoriteStatus(station.id, !station.isFavorite)
    }
    suspend fun clearHistory() {
        historyDao?.clearAll()
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