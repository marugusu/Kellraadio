package ee.minu.kellraadio

import android.util.Log
import kotlinx.coroutines.flow.Flow

class RadioStationRepository(
    private val apiService: StationApiService,
    private val stationDao: RadioStationDao
) {
    val allStations: Flow<List<RadioStation>> = stationDao.getAllActiveStations()

    // UUS: Lemmiku lülitamine
    suspend fun toggleFavorite(station: RadioStation) {
        stationDao.updateFavoriteStatus(station.id, !station.isFavorite)
    }

    suspend fun refreshStations() {
        try {
            Log.d("RADIO_DEBUG", "Alustan jaamade värskendamist...")

            // 1. Jäta meelde praegused lemmikud
            val favoriteIds = stationDao.getFavoriteIds()

            // 2. Tõmba internetist uued
            val remoteStations = apiService.getStations(System.currentTimeMillis())

            if (remoteStations.isNotEmpty()) {
                // 3. Kopeeri lemmiku staatus uude nimekirja neile, mis on alles
                val updatedStations = remoteStations.map { remote ->
                    remote.copy(isFavorite = favoriteIds.contains(remote.id))
                }

                // 4. Salvesta
                stationDao.insertAll(updatedStations)
                stationDao.deleteMissing(updatedStations.map { it.id })
                Log.d("RADIO_DEBUG", "Uuendatud. Lemmikud säilitatud.")
            }
        } catch (e: Exception) {
            Log.e("RADIO_DEBUG", "Viga värskendamisel: ${e.message}")
        }
    }
}