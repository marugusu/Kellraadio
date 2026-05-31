package app.radiorecalarm

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RadioFilterItem(
    val name: String,
    @SerialName("stationcount") val stationCount: Int,
    @SerialName("iso_3166_1") val isoCode: String? = null
)
