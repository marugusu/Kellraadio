package ee.minu.kellraadio

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "history")
data class HistoryItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val stationName: String,
    val artist: String,
    val title: String,
    val timestamp: Long
)