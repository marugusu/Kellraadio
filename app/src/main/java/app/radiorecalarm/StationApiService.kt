package app.radiorecalarm

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Query

interface StationApiService {

    // Lisasime timestampi parameetri, et vältida vahemälu (Caching)
    @GET("stations.json")
    suspend fun getStations(@Query("t") timestamp: Long): List<RadioStation>

    companion object {
        private const val BASE_URL = AppConfig.Api.STATIONS_LIST_BASE_URL

        private val json = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
        }

        fun create(): StationApiService {
            val contentType = "application/json".toMediaType()
            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(json.asConverterFactory(contentType))
                .build()
                .create(StationApiService::class.java)
        }
    }
}
