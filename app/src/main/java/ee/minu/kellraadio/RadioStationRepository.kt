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

    // UUS: Salvesta ajalugu
    suspend fun addToHistory(stationName: String, artist: String, title: String) {
        if (historyDao == null) return

        // Lihtne filtreerimine: ära salvesta "Otseeeter" või tühja infot
        if (artist.equals("Otseeeter", ignoreCase = true) || title.equals(stationName, ignoreCase = true) || artist.isBlank()) {
            return
        }

        // Kontrolli duplikaati
        val lastItem = historyDao.getLatestItem()
        if (lastItem != null && lastItem.artist == artist && lastItem.title == title) {
            return // Sama lugu, ära salvesta uuesti
        }

        val newItem = HistoryItem(
            stationName = stationName,
            artist = artist,
            title = title,
            timestamp = System.currentTimeMillis()
        )

        historyDao.insert(newItem)
        historyDao.cleanOldHistory() // Kustuta vanad
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