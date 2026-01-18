package ee.minu.kellraadio

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Query

// Jaama mudel (jääb samaks)
@Serializable
data class RadioBrowserStation(
    @SerialName("stationuuid") val stationUuid: String,
    val name: String,
    val url: String,
    @SerialName("url_resolved") val urlResolved: String,
    val homepage: String,
    val country: String,
    @SerialName("countrycode") val countryCode: String,
    val language: String,
    val bitrate: Int,
    val votes: Int,
    val clickcount: Int
)

// UUS: Mudel riigi ja žanri nimekirja jaoks
@Serializable
data class RadioFilterItem(
    val name: String,
    @SerialName("stationcount") val stationCount: Int,
    @SerialName("iso_3166_1") val isoCode: String? = null // UUS VÄLI (võib olla null žanrite puhul)
)

interface RadioBrowserApiService {

    @GET("json/stations/search")
    suspend fun advancedSearch(
        @Query("name") name: String,
        @Query("country") country: String? = null,
        @Query("tag") tag: String? = null, // UUS: Žanr
        @Query("hidebroken") hideBroken: Boolean = true,
        @Query("limit") limit: Int = 100,
        @Query("order") order: String = "clickcount",
        @Query("reverse") reverse: Boolean = true
    ): List<RadioBrowserStation>

    // UUS: Riikide nimekiri (sorteeritud jaamade arvu järgi)
    @GET("json/countries?order=stationcount&reverse=true")
    suspend fun getCountries(): List<RadioFilterItem>

    // UUS: Žanrite (tags) nimekiri
    @GET("json/tags?order=stationcount&reverse=true")
    suspend fun getTags(): List<RadioFilterItem>

    companion object {
        private const val BASE_URL = "https://de1.api.radio-browser.info/"
        private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

        fun create(): RadioBrowserApiService {
            val contentType = "application/json".toMediaType()
            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(json.asConverterFactory(contentType))
                .build()
                .create(RadioBrowserApiService::class.java)
        }
    }
}