@file:OptIn(kotlinx.serialization.InternalSerializationApi::class)

package ws.ct.radiow

import android.util.Log
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Query

// --- 1. LÕPLIK ANDMEMUDEL ---

data class SongAdditionalInfo(
    val lyrics: String? = null,
    val coverArtUrl: String? = null,
    val album: String? = null,
    val year: String? = null,
    val genre: String? = null
)

// --- 2. API VASTUSTE MUDELID ---

// LRCLIB
@Serializable
data class LrcLibResponse(
    val plainLyrics: String? = null
)

// iTunes
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

// MusicBrainz
@Serializable
data class MbResponse(
    val recordings: List<MbRecording> = emptyList()
)

@Serializable
data class MbRecording(
    val id: String,
    val recordings: List<MbRecording>? = null, // Some responses might have this
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
    @Headers("User-Agent: radiow/1.0 ( minumeil@example.com )")
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

    private val lrcApi = Retrofit.Builder()
        .baseUrl(AppConfig.Api.LRCLIB_BASE_URL) // KASUTAME KONFIGURATSIOONI
        .addConverterFactory(json.asConverterFactory(contentType))
        .build()
        .create(LrcLibApi::class.java)

    private val itunesApi = Retrofit.Builder()
        .baseUrl(AppConfig.Api.ITUNES_BASE_URL) // KASUTAME KONFIGURATSIOONI
        .addConverterFactory(json.asConverterFactory(contentType))
        .build()
        .create(ItunesApi::class.java)

    private val mbApi = Retrofit.Builder()
        .baseUrl(AppConfig.Api.MUSICBRAINZ_BASE_URL) // KASUTAME KONFIGURATSIOONI
        .addConverterFactory(json.asConverterFactory(contentType))
        .build()
        .create(MusicBrainzApi::class.java)

    suspend fun fetchInfo(artist: String, title: String): SongAdditionalInfo? {
        // KASUTAME UUT NIMEKIRJA AppConfig FAILIST
        val shouldIgnore = AppConfig.Metadata.IGNORE_TERMS.any { term ->
            title.contains(term, ignoreCase = true) || artist.contains(term, ignoreCase = true)
        }

        if (artist.isBlank() || title.isBlank() || shouldIgnore) {
            return null
        }

        Log.d(TAG, "🔍 ALUSTAN OTSINGUT: '$artist' - '$title'")

        // --- 1. PARALLEELNE: SÕNAD (LRCLIB) ---
        var foundLyrics: String? = null
        try {
            Log.d(TAG, "--> 1. Küsin LRCLIB (Sõnad)...")
            val response = lrcApi.getLyrics(artist, title)
            foundLyrics = response.plainLyrics
            if (foundLyrics != null) Log.d(TAG, "✅ LRCLIB: Sõnad leitud!") else Log.d(TAG, "❌ LRCLIB: Sõnu ei leitud.")
        } catch (e: Exception) {
            Log.e(TAG, "❌ LRCLIB viga: ${e.message}")
        }

        // --- 2. JÄRJESTIKUNE: METAANDMED (ITUNES -> MUSICBRAINZ) ---
        var foundCover: String? = null
        var foundAlbum: String? = null
        var foundYear: String? = null
        var foundGenre: String? = null

        // SAMM A: Proovi iTunes (Kiireim)
        try {
            Log.d(TAG, "--> 2. Küsin iTunes (Meta)...")
            val response = itunesApi.search("$artist $title")
            if (response.results.isNotEmpty()) {
                val item = response.results[0]
                foundCover = item.artworkUrl100?.replace("100x100bb", "600x600bb")
                foundAlbum = item.collectionName
                foundGenre = item.primaryGenreName
                foundYear = item.releaseDate?.take(4)
                Log.d(TAG, "✅ iTunes: Pilt ja info leitud! Album: $foundAlbum, Aasta: $foundYear")
            } else {
                Log.d(TAG, "❌ iTunes: Tulemusi polnud.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ iTunes viga: ${e.message}")
        }

        // SAMM B: Kui iTunes ei leidnud pilti, proovi MusicBrainz (Fallback)
        if (foundCover == null) {
            Log.d(TAG, "⚠️ iTunes ei leidnud pilti. --> 3. Küsin MusicBrainz...")
            try {
                // Otsing: artist:"Ruja" AND recording:"Eile nägin ma Eestimaad"
                val query = "artist:\"$artist\" AND recording:\"$title\""
                val mbResponse = mbApi.searchRecording(query)

                if (mbResponse.recordings.isNotEmpty()) {
                    val recording = mbResponse.recordings[0]
                    Log.d(TAG, "✅ MusicBrainz: Leidsin loo ID: ${recording.id}")

                    // Leiame parima albumi (eelistame "Official" reliise)
                    val bestRelease = recording.releases.firstOrNull { it.status == "Official" }
                        ?: recording.releases.firstOrNull()

                    if (bestRelease != null) {
                        Log.d(TAG, "✅ MusicBrainz: Leidsin release'i: ${bestRelease.title} (ID: ${bestRelease.id})")

                        // Ehitame Cover Art Archive URL-i
                        foundCover = "https://coverartarchive.org/release/${bestRelease.id}/front"
                        foundAlbum = bestRelease.title
                        foundYear = bestRelease.date?.take(4)
                        Log.d(TAG, "🔗 MusicBrainz Pildi URL: $foundCover")
                    } else {
                        Log.d(TAG, "❌ MusicBrainz: Lugu leiti, aga release'i (albumit) pole.")
                    }
                } else {
                    Log.d(TAG, "❌ MusicBrainz: Ei leidnud lugu.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ MusicBrainz viga: ${e.message}")
            }
        }

        if (foundLyrics == null && foundCover == null) {
            Log.d(TAG, "🏁 LÕPP: Mitte midagi ei leitud.")
            return null
        }

        Log.d(TAG, "🏁 LÕPP. Tulemus -> Pilt: ${if(foundCover!=null) "JAH" else "EI"}, Sõnad: ${if(foundLyrics!=null) "JAH" else "EI"}")

        return SongAdditionalInfo(
            lyrics = foundLyrics,
            coverArtUrl = foundCover,
            album = foundAlbum,
            year = foundYear,
            genre = foundGenre
        )
    }
}