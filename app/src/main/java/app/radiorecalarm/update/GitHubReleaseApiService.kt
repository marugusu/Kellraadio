package app.radiorecalarm.update

import app.radiorecalarm.AppConfig
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Path
import java.util.concurrent.TimeUnit

interface GitHubReleaseApiService {

    @GET("repos/{owner}/{repo}/releases/latest")
    suspend fun getLatestRelease(
        @Path("owner") owner: String = AppConfig.Api.GITHUB_REPO_OWNER,
        @Path("repo") repo: String = AppConfig.Api.GITHUB_REPO_NAME
    ): GitHubRelease

    companion object {
        private val json = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
            isLenient = true
        }

        fun create(okHttpClient: OkHttpClient? = null): GitHubReleaseApiService {
            val client = okHttpClient ?: OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .addInterceptor(Interceptor { chain ->
                    val request = chain.request().newBuilder()
                        .header("User-Agent", AppConfig.Api.USER_AGENT)
                        .header("Accept", "application/vnd.github.v3+json")
                        .build()
                    chain.proceed(request)
                })
                .build()

            val contentType = "application/json".toMediaType()
            return Retrofit.Builder()
                .baseUrl(AppConfig.Api.GITHUB_API_BASE_URL)
                .client(client)
                .addConverterFactory(json.asConverterFactory(contentType))
                .build()
                .create(GitHubReleaseApiService::class.java)
        }
    }
}
