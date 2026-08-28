package app.radiorecalarm

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface RadioStationDao {

    @Query("SELECT * FROM stations WHERE isActive = 1 ORDER BY priority ASC, name ASC")
    fun getAllActiveStations(): Flow<List<RadioStation>>

    @Query("SELECT * FROM stations WHERE isActive = 1 ORDER BY priority ASC, name ASC")
    suspend fun getAllActiveStationsSync(): List<RadioStation>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(stations: List<RadioStation>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(station: RadioStation)

    @Delete
    suspend fun delete(station: RadioStation)

    @Query("UPDATE stations SET isFavorite = :isFav WHERE id = :stationId")
    suspend fun updateFavoriteStatus(stationId: Int, isFav: Boolean)

    @Query("SELECT id FROM stations WHERE isFavorite = 1")
    suspend fun getFavoriteIds(): List<Int>

    @Query("DELETE FROM stations WHERE isUserStation = 0 AND id NOT IN (:ids)")
    suspend fun deleteMissing(ids: List<Int>)

    @Query("SELECT MAX(id) FROM stations")
    suspend fun getMaxId(): Int?

    @Update
    suspend fun update(station: RadioStation)

    @Query("SELECT * FROM stations WHERE name = :name LIMIT 1")
    suspend fun getStationByName(name: String): RadioStation?

    @Query("SELECT MAX(favoriteOrder) FROM stations WHERE isFavorite = 1")
    suspend fun getMaxFavoriteOrder(): Int?

    // UUS: Nulli lemmikute järjekord
    @Query("UPDATE stations SET favoriteOrder = 0")
    suspend fun resetFavoriteOrders()

    @Query("DELETE FROM stations WHERE isUserStation = 1")
    suspend fun deleteAllUserStations()

    @Query("UPDATE stations SET isFavorite = 0, favoriteOrder = 0")
    suspend fun resetAllFavorites()

    @Query("UPDATE stations SET isUserStation = 1 WHERE id >= 80")
    suspend fun restoreUserStations()
}
