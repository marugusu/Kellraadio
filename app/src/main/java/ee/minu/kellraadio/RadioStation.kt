package ee.minu.kellraadio

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "stations")
data class RadioStation(
    @PrimaryKey
    val id: Int,
    val name: String,
    val url: String,
    val isActive: Boolean = true,
    val priority: Int = 999,
    val category: String = "Eesti" // UUS VÄLI
)