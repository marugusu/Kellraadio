package ee.minu.kellraadio

import android.util.Log
import kotlinx.coroutines.flow.Flow

class RadioStationRepository(
    private val apiService: StationApiService,
    private val stationDao: RadioStationDao
) {
    val allStations: Flow<List<RadioStation>> = stationDao.getAllActiveStations()

    suspend fun refreshStations() {
        try {
            Log.d("RADIO_DEBUG", "Alustan jaamade värskendamist...")

            // Saadame kaasa praeguse aja, et server ei annaks vana vahemälu versiooni
            val newStations = apiService.getStations(System.currentTimeMillis())

            Log.d("RADIO_DEBUG", "Internetist saadi ${newStations.size} jaama.")

            if (newStations.isNotEmpty()) {
                // Salvestame andmebaasi
                stationDao.insertAll(newStations)

                // Kustutame need, mida enam nimekirjas pole
                val newIds = newStations.map { it.id }
                stationDao.deleteMissing(newIds)

                Log.d("RADIO_DEBUG", "Andmebaas uuendatud edukalt.")
            }
        } catch (e: Exception) {
            Log.e("RADIO_DEBUG", "Viga värskendamisel: ${e.message}")
            e.printStackTrace()
        }
    }
}