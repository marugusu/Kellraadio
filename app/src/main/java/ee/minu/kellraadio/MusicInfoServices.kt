@file:OptIn(kotlinx.serialization.InternalSerializationApi::class)

package ee.minu.kellraadio

import android.util.Log
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Query

// --- 1. ANDMEMUDELID ---

data class SongAdditionalInfo(
    val lyrics: String? = null,
    val coverArtUrl: String? = null,
    val album: String? = null,
    val year: String? = null,
    val genre: String? = null
)

// LRCLIB vastus
@Serializable
data class LrcLibResponse(
    val id: Int,
    val name: String,
    val artistName: String,
    val plainLyrics: String? = null,
    val syncedLyrics: String? = null
)

// iTunes vastus
@Serializable
data class ItunesResponse(
    val resultCount: Int,
    val results: List<ItunesResult>
)

@Serializable
data class ItunesResult(
    val artistName: String,
    val trackName: String,
    @SerialName("artworkUrl100") val artworkUrl100: String? = null,
    val collectionName: String? = null,
    val releaseDate: String? = null,
    val primaryGenreName: String? = null
)

// --- 2. API LIIDESED ---

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

// --- 3. REPOSITOORIUM ---

object MusicInfoRepository {
    private val json = Json { ignoreUnknownKeys = true }
    private val contentType = "application/json".toMediaType()

    private val lrcApi = Retrofit.Builder()
        .baseUrl("https://lrclib.net/")
        .addConverterFactory(json.asConverterFactory(contentType))
        .build()
        .create(LrcLibApi::class.java)

    private val itunesApi = Retrofit.Builder()
        .baseUrl("https://itunes.apple.com/")
        .addConverterFactory(json.asConverterFactory(contentType))
        .build()
        .create(ItunesApi::class.java)

    suspend fun fetchInfo(artist: String, title: String): SongAdditionalInfo? {
        // Kontrollime, et poleks tühi ega "Otseeeter"
        if (artist.isBlank() || title.isBlank() || title.contains("Otseeeter")) return null

        var foundLyrics: String? = null
        var foundCover: String? = null
        var foundAlbum: String? = null
        var foundYear: String? = null
        var foundGenre: String? = null

        // 1. Proovime LRCLIB (Sõnad)
        try {
            val response = lrcApi.getLyrics(artist, title)
            if (!response.plainLyrics.isNullOrBlank()) {
                foundLyrics = response.plainLyrics
            }
        } catch (_: Exception) {
            // Viga (nt 404 Not Found), ignoreerime vaikselt
        }

        // 2. Proovime iTunes (Metaandmed)
        try {
            val query = "$artist $title"
            val response = itunesApi.search(query)

            if (response.results.isNotEmpty()) {
                val item = response.results[0]
                // Võtame suurema pildi (600x600 või 1000x1000)
                foundCover = item.artworkUrl100?.replace("100x100bb", "600x600bb")

                foundAlbum = item.collectionName
                foundGenre = item.primaryGenreName
                foundYear = item.releaseDate?.take(4)
            }
        } catch (_: Exception) {
            // Viga, ignoreerime vaikselt
        }

        if (foundLyrics == null && foundCover == null) return null

        return SongAdditionalInfo(
            lyrics = foundLyrics,
            coverArtUrl = foundCover,
            album = foundAlbum,
            year = foundYear,
            genre = foundGenre
        )
    }
}