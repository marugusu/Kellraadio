package ee.minu.kellraadio

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
        private const val BASE_URL = "https://gist.githubusercontent.com/marugusu/e886795e2e2ae5df7b9573bd3f84333b/raw/"

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