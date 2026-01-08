package ee.minu.kellraadio

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface RadioStationDao {

    @Query("SELECT * FROM stations WHERE isActive = 1 ORDER BY priority ASC, name ASC")
    fun getAllActiveStations(): Flow<List<RadioStation>>

    // UUS: Vajalik Next/Prev loogika jaoks teenuses
    @Query("SELECT * FROM stations WHERE isActive = 1 ORDER BY priority ASC, name ASC")
    suspend fun getAllActiveStationsSync(): List<RadioStation>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(stations: List<RadioStation>)

    @Query("DELETE FROM stations WHERE id NOT IN (:ids)")
    suspend fun deleteMissing(ids: List<Int>)
}