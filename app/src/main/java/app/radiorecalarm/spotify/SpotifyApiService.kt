package app.radiorecalarm.spotify

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface SpotifyAccountsApi {
    @FormUrlEncoded
    @POST("api/token")
    suspend fun exchangeCode(
        @Field("grant_type") grantType: String = "authorization_code",
        @Field("code") code: String,
        @Field("redirect_uri") redirectUri: String,
        @Field("client_id") clientId: String,
        @Field("code_verifier") codeVerifier: String
    ): SpotifyTokenResponse

    @FormUrlEncoded
    @POST("api/token")
    suspend fun refreshToken(
        @Field("grant_type") grantType: String = "refresh_token",
        @Field("refresh_token") refreshToken: String,
        @Field("client_id") clientId: String
    ): SpotifyTokenResponse
}

interface SpotifyWebApi {
    @GET("me")
    suspend fun getCurrentUser(
        @Header("Authorization") authorization: String
    ): SpotifyUserProfile

    @GET("search")
    suspend fun searchTracks(
        @Header("Authorization") authorization: String,
        @Query("q") query: String,
        @Query("type") type: String = "track",
        @Query("limit") limit: Int = 5
    ): SpotifySearchResponse

    @GET("me/library/contains")
    suspend fun checkSavedLibraryItems(
        @Header("Authorization") authorization: String,
        @Query("uris") uris: String
    ): List<Boolean>

    @PUT("me/library")
    suspend fun saveLibraryItems(
        @Header("Authorization") authorization: String,
        @Query("uris") uris: String
    ): Response<Unit>

    @GET("me/playlists")
    suspend fun getMyPlaylists(
        @Header("Authorization") authorization: String,
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0
    ): SpotifyPlaylistsResponse

    @GET("playlists/{playlist_id}/items")
    suspend fun getPlaylistItems(
        @Header("Authorization") authorization: String,
        @Path("playlist_id") playlistId: String,
        @Query("limit") limit: Int = 100,
        @Query("offset") offset: Int = 0
    ): SpotifyPlaylistTracksResponse

    @POST("playlists/{playlist_id}/items")
    suspend fun addItemsToPlaylist(
        @Header("Authorization") authorization: String,
        @Path("playlist_id") playlistId: String,
        @Body body: SpotifyAddTracksRequest
    ): Response<Unit>
}
