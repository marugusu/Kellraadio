package app.radiorecalarm

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.InternalSerializationApi
import java.util.UUID

@OptIn(InternalSerializationApi::class)
@Serializable
@Entity(tableName = "stations")
data class RadioStation(
    @PrimaryKey
    val id: Int,
    val name: String,
    val url: String,
    val isActive: Boolean = true,
    val priority: Int = 999,
    val category: String = "Eesti",
    val isFavorite: Boolean = false,

    val isUserStation: Boolean = false,
    val uuid: String = "",
    @SerialName("countrycode")
    val countryCode: String = "",
    
    // UUS: Järjekord ainult lemmikute vaates
    val favoriteOrder: Int = 0
)
