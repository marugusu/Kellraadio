package ee.minu.kellraadio

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    // Võtame ajaloo, uuemad eespool
    @Query("SELECT * FROM history ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<HistoryItem>>

    // Võtame viimase lisatud loo (et vältida duplikaate)
    @Query("SELECT * FROM history ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestItem(): HistoryItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: HistoryItem)

    // Kustuta kõik peale viimase 10000 kirje (hoiame ajaloo puhtana)
    @Query("DELETE FROM history WHERE id NOT IN (SELECT id FROM history ORDER BY timestamp DESC LIMIT 10000)")
    suspend fun cleanOldHistory()

    @Query("DELETE FROM history")
    suspend fun clearAll()
}