package app.radiorecalarm

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: HistoryItem)

    // Sorteerime nii, et värskeim on eespool
    @Query("SELECT * FROM history ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<HistoryItem>>

    @Query("SELECT * FROM history ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestItem(): HistoryItem?

    @Query("DELETE FROM history")
    suspend fun clearAll()

    // Valikuline: Piirame ajaloo mahtu (nt 10 000 viimast kirjet), et andmebaas ei paisuks lõputult
    @Query("DELETE FROM history WHERE id NOT IN (SELECT id FROM history ORDER BY timestamp DESC LIMIT 10000)")
    suspend fun cleanOldHistory()
}
