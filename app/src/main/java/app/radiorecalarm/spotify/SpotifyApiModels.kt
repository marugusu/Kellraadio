package app.radiorecalarm.spotify

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SpotifyTokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("token_type") val tokenType: String? = null,
    @SerialName("expires_in") val expiresIn: Long = 3600L,
    @SerialName("refresh_token") val refreshToken: String? = null,
    val scope: String? = null
)

@Serializable
data class SpotifyUserProfile(
    val id: String,
    @SerialName("display_name") val displayName: String? = null
)

@Serializable
data class SpotifySearchResponse(
    val tracks: SpotifyTracksPage? = null
)

@Serializable
data class SpotifyTracksPage(
    val items: List<SpotifyTrack> = emptyList()
)

@Serializable
data class SpotifyTrack(
    val id: String,
    val uri: String,
    val name: String = "",
    @SerialName("duration_ms") val durationMs: Long? = null,
    @SerialName("track_number") val trackNumber: Int? = null,
    val album: SpotifyAlbum? = null,
    val artists: List<SpotifyArtist> = emptyList()
)

@Serializable
data class SpotifyAlbum(
    val name: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    val images: List<SpotifyImage> = emptyList()
)

@Serializable
data class SpotifyImage(
    val url: String,
    val width: Int? = null,
    val height: Int? = null
)

@Serializable
data class SpotifyArtist(
    val id: String? = null,
    val name: String = ""
)

@Serializable
data class SpotifyPlaylistsResponse(
    val items: List<SpotifyPlaylistItem> = emptyList(),
    val next: String? = null
)

@Serializable
data class SpotifyPlaylistItem(
    val id: String,
    val name: String = "",
    val collaborative: Boolean = false,
    val owner: SpotifyPlaylistOwner? = null,
    val tracks: SpotifyPlaylistTracksInfo? = null
)

@Serializable
data class SpotifyPlaylistOwner(
    val id: String? = null,
    @SerialName("display_name") val displayName: String? = null
)

@Serializable
data class SpotifyPlaylistTracksInfo(
    val total: Int = 0
)

@Serializable
data class SpotifyPlaylistTracksResponse(
    val items: List<SpotifyPlaylistTrackEntry> = emptyList(),
    val next: String? = null
)

@Serializable
data class SpotifyPlaylistTrackEntry(
    val item: SpotifyPlaylistTrackId? = null,
    val track: SpotifyPlaylistTrackId? = null
) {
    fun resolveTrackId(): String? {
        return item?.id
            ?: track?.id
            ?: item?.uri?.substringAfterLast(":")
            ?: track?.uri?.substringAfterLast(":")
    }
}

@Serializable
data class SpotifyPlaylistTrackId(
    val id: String? = null,
    val uri: String? = null
)

@Serializable
data class SpotifyAddTracksRequest(
    val uris: List<String>
)
