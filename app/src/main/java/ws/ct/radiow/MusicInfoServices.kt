package ws.ct.radiow

import android.util.Log
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

// --- 1. LÕPLIK ANDMEMUDEL ---

data class SongAdditionalInfo(
    val lyrics: String? = null,
    val coverArtUrl: String? = null,
    val album: String? = null,
    val year: String? = null,
    val genre: String? = null
)

// --- 2. API VASTUSTE MUDELID ---

@Serializable
data class LrcLibResponse(
    val plainLyrics: String? = null
)

@Serializable
data class ItunesResponse(
    val results: List<ItunesResult>
)

@Serializable
data class ItunesResult(
    @SerialName("artworkUrl100") val artworkUrl100: String? = null,
    val collectionName: String? = null,
    val releaseDate: String? = null,
    val primaryGenreName: String? = null
)

@Serializable
data class MbResponse(
    val recordings: List<MbRecording> = emptyList()
)

@Serializable
data class MbRecording(
    val id: String,
    val releases: List<MbRelease> = emptyList()
)

@Serializable
data class MbRelease(
    val id: String,
    val title: String,
    val date: String? = null,
    val status: String? = null
)

// --- 3. API LIIDESED ---

interface LrcLibApi {
    @GET("api/get")
    suspend fun getLyrics(
        @Query("artist_name") artist: String,
        @Query("track_name") track: String
    ): LrcLibResponse
}

interface ItunesApi {
    @GET("search")
    suspend fun search(
        @Query("term") term: String,
        @Query("media") media: String = "music",
        @Query("entity") entity: String = "song",
        @Query("limit") limit: Int = 1
    ): ItunesResponse
}

interface MusicBrainzApi {
    @GET("recording/")
    suspend fun searchRecording(
        @Query("query") query: String,
        @Query("fmt") format: String = "json"
    ): MbResponse
}

// --- 4. REPOSITOORIUM (LOOGIKA) ---

object MusicInfoRepository {
    private const val TAG = "MusicInfoRepo"

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
    private val contentType = "application/json".toMediaType()

    // Brauseri User-Agent, et serverid ei blokeeriks meid robotina
    private const val BROWSER_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36"

    private val commonInterceptor = Interceptor { chain ->
        val request = chain.request().newBuilder()
            .header("User-Agent", BROWSER_USER_AGENT)
            .header("Accept", "application/json")
            .build()
        chain.proceed(request)
    }

    // Standardne klient iTunes ja MusicBrainz jaoks
    private val fastClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .addInterceptor(commonInterceptor)
        .build()

    // Spetsiaalne klient LRCLIB jaoks (lisatud HTTP/1.1 sundimine)
    private val slowClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .protocols(listOf(Protocol.HTTP_1_1)) // Sundime HTTP/1.1, et vältida HTTP/2 probleeme
        .addInterceptor(commonInterceptor)
        .retryOnConnectionFailure(true)
        .build()

    private val lrcApi = Retrofit.Builder()
        .baseUrl(AppConfig.Api.LRCLIB_BASE_URL)
        .client(slowClient)
        .addConverterFactory(json.asConverterFactory(contentType))
        .build()
        .create(LrcLibApi::class.java)

    private val itunesApi = Retrofit.Builder()
        .baseUrl(AppConfig.Api.ITUNES_BASE_URL)
        .client(fastClient)
        .addConverterFactory(json.asConverterFactory(contentType))
        .build()
        .create(ItunesApi::class.java)

    private val mbApi = Retrofit.Builder()
        .baseUrl(AppConfig.Api.MUSICBRAINZ_BASE_URL)
        .client(fastClient)
        .addConverterFactory(json.asConverterFactory(contentType))
        .build()
        .create(MusicBrainzApi::class.java)

    fun fetchInfo(artist: String, title: String): Flow<SongAdditionalInfo> = flow {
        val shouldIgnore = AppConfig.Metadata.IGNORE_TERMS.any { term ->
            title.contains(term, ignoreCase = true) || artist.contains(term, ignoreCase = true)
        }

        if (artist.isBlank() || title.isBlank() || shouldIgnore) return@flow

        Log.d(TAG, "🔍 ALUSTAN OTSINGUT: '$artist' - '$title'")

        var foundCover: String? = null
        var foundAlbum: String? = null
        var foundYear: String? = null
        var foundGenre: String? = null

        // --- 1. METAANDMED ---
        try {
            Log.d(TAG, "--> 1. Küsin iTunes...")
            val response = itunesApi.search("$artist $title")
            if (response.results.isNotEmpty()) {
                val item = response.results[0]
                foundCover = item.artworkUrl100?.replace("100x100bb", "600x600bb")
                foundAlbum = item.collectionName
                foundGenre = item.primaryGenreName
                foundYear = item.releaseDate?.take(4)
                Log.d(TAG, "✅ iTunes: OK")
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e(TAG, "❌ iTunes viga: ${e.message}")
        }

        if (foundCover == null) {
            try {
                Log.d(TAG, "--> 2. Küsin MusicBrainz...")
                val query = "artist:\"$artist\" AND recording:\"$title\""
                val mbResponse = mbApi.searchRecording(query)
                if (mbResponse.recordings.isNotEmpty()) {
                    val recording = mbResponse.recordings[0]
                    val bestRelease = recording.releases.firstOrNull { it.status == "Official" } ?: recording.releases.firstOrNull()
                    if (bestRelease != null) {
                        foundCover = "https://coverartarchive.org/release/${bestRelease.id}/front"
                        foundAlbum = bestRelease.title
                        foundYear = bestRelease.date?.take(4)
                        Log.d(TAG, "✅ MusicBrainz: OK")
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e(TAG, "❌ MusicBrainz viga: ${e.message}")
            }
        }

        var currentInfo = SongAdditionalInfo(
            lyrics = null,
            coverArtUrl = foundCover,
            album = foundAlbum,
            year = foundYear,
            genre = foundGenre
        )
        
        // Saada kätteolev info (pilt/album) kohe välja
        if (foundCover != null || foundAlbum != null) {
            emit(currentInfo)
        }

        // --- 2. SÕNAD (LRCLIB) - KOOS RETRY LOOGIKAGA ---
        var foundLyrics: String? = null
        var lrcAttempt = 0
        while (lrcAttempt < 2 && foundLyrics == null) {
            try {
                Log.d(TAG, "--> 3. Küsin LRCLIB (Katse ${lrcAttempt + 1})...")
                val response = lrcApi.getLyrics(artist, title)
                foundLyrics = response.plainLyrics
                if (foundLyrics != null) {
                    Log.d(TAG, "✅ LRCLIB: OK")
                    currentInfo = currentInfo.copy(lyrics = foundLyrics)
                    emit(currentInfo)
                }
                break
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e(TAG, "❌ LRCLIB viga (Katse ${lrcAttempt + 1}): ${e.message}")
                lrcAttempt++
                if (lrcAttempt < 2) delay(1000) // Ootame sekund enne uut katset
            }
        }
    }
}