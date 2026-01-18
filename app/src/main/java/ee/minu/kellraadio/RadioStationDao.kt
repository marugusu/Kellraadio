package ee.minu.kellraadio

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

    // UUS: Ühe jaama lisamiseks (kasutaja oma)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(station: RadioStation)

    // UUS: Jaama kustutamine (kasutaja oma)
    @Delete
    suspend fun delete(station: RadioStation)

    @Query("UPDATE stations SET isFavorite = :isFav WHERE id = :stationId")
    suspend fun updateFavoriteStatus(stationId: Int, isFav: Boolean)

    @Query("SELECT id FROM stations WHERE isFavorite = 1")
    suspend fun getFavoriteIds(): List<Int>

    // --- KRITILINE MUUDATUS ---
    // Kustutame AINULT neid jaamu, mis on süsteemsed (isUserStation = 0)
    // ja mida pole enam uues nimekirjas (:ids).
    // Kasutaja lisatud jaamu (isUserStation = 1) see rida EI PUUTU.
    @Query("DELETE FROM stations WHERE isUserStation = 0 AND id NOT IN (:ids)")
    suspend fun deleteMissing(ids: List<Int>)

    // UUS ABIFUNKTSIOON
    // Leiame suurima ID, et saaksime uuele jaamale anda unikaalse ID (nt 10001)
    @Query("SELECT MAX(id) FROM stations")
    suspend fun getMaxId(): Int?

    @Update
    suspend fun update(station: RadioStation)
}