package app.radiorecalarm.spotify

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import app.radiorecalarm.AppConfig
import app.radiorecalarm.BuildConfig
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import okhttp3.ConnectionSpec
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import retrofit2.HttpException
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

data class SpotifyUiState(
    val hasClientId: Boolean = false,
    val isUsingCustomClientId: Boolean = false,
    val customClientId: String = "",
    val isConnected: Boolean = false,
    val isConnecting: Boolean = false,
    val userDisplayName: String? = null,
    val saveTarget: SpotifySaveTarget = SpotifySaveTarget.LIKED_SONGS,
    val selectedPlaylistId: String? = null,
    val selectedPlaylistName: String? = null,
    val playlists: List<SpotifyPlaylistItem> = emptyList(),
    val isLoadingPlaylists: Boolean = false,
    val savedTrackIds: Set<String> = emptySet(),
    val savingTrackIds: Set<String> = emptySet(),
    val lastError: String? = null
)

enum class SaveTrackResult {
    SAVED,
    ALREADY_SAVED,
    NOT_CONNECTED,
    PLAYLIST_NOT_SELECTED,
    ERROR
}

sealed class SpotifyAuthEvent {
    data object Connected : SpotifyAuthEvent()
    data class Failed(val reason: String) : SpotifyAuthEvent()
}

object SpotifyRepository {
    private const val TAG = "SpotifyRepo"
    private const val MAX_SEARCH_CACHE_SIZE = 100
    private const val MAX_PLAYLIST_PAGES = 5

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val tokenMutex = Mutex()

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
    private val contentType = "application/json".toMediaType()

    private val commonInterceptor = Interceptor { chain ->
        val request = chain.request().newBuilder()
            .header("User-Agent", AppConfig.Api.USER_AGENT)
            .header("Accept", "application/json")
            .build()
        chain.proceed(request)
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .addInterceptor(commonInterceptor)
        .protocols(listOf(Protocol.HTTP_1_1))
        .connectionSpecs(listOf(ConnectionSpec.COMPATIBLE_TLS, ConnectionSpec.MODERN_TLS))
        .build()

    private val accountsApi: SpotifyAccountsApi by lazy {
        Retrofit.Builder()
            .baseUrl(AppConfig.Api.SPOTIFY_ACCOUNTS_BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(SpotifyAccountsApi::class.java)
    }

    private val webApi: SpotifyWebApi by lazy {
        Retrofit.Builder()
            .baseUrl(AppConfig.Api.SPOTIFY_API_BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(SpotifyWebApi::class.java)
    }

    @Volatile
    private var prefsStore: SpotifyPrefsStore? = null

    private val _uiState = MutableStateFlow(SpotifyUiState())
    val uiState = _uiState.asStateFlow()

    private val _authEvents = MutableSharedFlow<SpotifyAuthEvent>(extraBufferCapacity = 4)
    val authEvents = _authEvents.asSharedFlow()

    private val searchCache = object : LinkedHashMap<String, SpotifyTrack?>(MAX_SEARCH_CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, SpotifyTrack?>?): Boolean {
            return size > MAX_SEARCH_CACHE_SIZE
        }
    }

    private var cachedPlaylistTrackIds: Pair<String, MutableSet<String>>? = null

    fun init(context: Context) {
        if (prefsStore != null) return
        synchronized(this) {
            if (prefsStore == null) {
                val store = SpotifyPrefsStore(context.applicationContext)
                prefsStore = store
                syncStateFromStore(store)
            }
        }
    }

    fun isConnected(): Boolean = _uiState.value.isConnected

    private fun syncStateFromStore(store: SpotifyPrefsStore) {
        val customId = store.getCustomClientId()
        val effectiveId = store.resolveClientId(BuildConfig.SPOTIFY_CLIENT_ID)
        val hasTokens = !store.getRefreshToken().isNullOrBlank() || !store.getAccessToken().isNullOrBlank()
        _uiState.update {
            it.copy(
                hasClientId = effectiveId.isNotEmpty(),
                isUsingCustomClientId = customId.isNotEmpty(),
                customClientId = customId,
                isConnected = hasTokens && effectiveId.isNotEmpty(),
                userDisplayName = store.getUserDisplayName(),
                saveTarget = store.getSaveTarget(),
                selectedPlaylistId = store.getSelectedPlaylistId(),
                selectedPlaylistName = store.getSelectedPlaylistName()
            )
        }
    }

    fun setCustomClientId(context: Context, clientId: String) {
        init(context)
        val store = prefsStore ?: return
        val previousEffective = store.resolveClientId(BuildConfig.SPOTIFY_CLIENT_ID)
        store.setCustomClientId(clientId)
        val nextEffective = store.resolveClientId(BuildConfig.SPOTIFY_CLIENT_ID)
        if (previousEffective != nextEffective && store.getRefreshToken() != null) {
            // Switching Client ID invalidates tokens issued for the previous app.
            store.clearConnection()
            clearInMemoryCaches()
        }
        syncStateFromStore(store)
    }

    fun startAuth(context: Context): Boolean {
        init(context)
        val store = prefsStore ?: return false
        val clientId = store.resolveClientId(BuildConfig.SPOTIFY_CLIENT_ID)
        if (clientId.isEmpty()) {
            _uiState.update { it.copy(lastError = "missing_client_id") }
            return false
        }

        val verifier = SpotifyPkce.generateCodeVerifier()
        val challenge = SpotifyPkce.generateCodeChallenge(verifier)
        val state = SpotifyPkce.generateState()
        store.savePendingAuth(verifier, state, clientId)

        val authUrl = SpotifyPkce.buildAuthorizeUrl(
            clientId = clientId,
            redirectUri = AppConfig.Api.SPOTIFY_REDIRECT_URI,
            codeChallenge = challenge,
            state = state,
            scopes = AppConfig.Api.SPOTIFY_SCOPES
        )

        _uiState.update { it.copy(isConnecting = true, lastError = null) }
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(authUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open Spotify auth URL: ${e.message}", e)
            store.clearPendingAuth()
            _uiState.update { it.copy(isConnecting = false, lastError = e.message ?: "browser_error") }
            false
        }
    }

    fun handleRedirectUri(context: Context, uri: Uri?) {
        val uriString = uri?.toString() ?: return
        init(context)
        val store = prefsStore ?: return
        val expectedState = store.getPendingState()
        val result = SpotifyPkce.parseRedirectUri(
            uriString = uriString,
            expectedRedirectUri = AppConfig.Api.SPOTIFY_REDIRECT_URI,
            expectedState = expectedState
        )
        when (result) {
            SpotifyPkce.AuthRedirectResult.Ignored -> return
            is SpotifyPkce.AuthRedirectResult.Error -> {
                store.clearPendingAuth()
                _uiState.update { it.copy(isConnecting = false, lastError = result.error) }
                _authEvents.tryEmit(SpotifyAuthEvent.Failed(result.error))
            }
            is SpotifyPkce.AuthRedirectResult.Code -> {
                val verifier = store.getPendingCodeVerifier()
                val clientId = store.getPendingClientId() ?: store.resolveClientId(BuildConfig.SPOTIFY_CLIENT_ID)
                if (verifier.isNullOrBlank() || clientId.isBlank()) {
                    store.clearPendingAuth()
                    _uiState.update { it.copy(isConnecting = false, lastError = "missing_verifier") }
                    _authEvents.tryEmit(SpotifyAuthEvent.Failed("missing_verifier"))
                    return
                }
                _uiState.update { it.copy(isConnecting = true, lastError = null) }
                scope.launch {
                    exchangeAuthCode(store, result.code, clientId, verifier)
                }
            }
        }
    }

    private suspend fun exchangeAuthCode(
        store: SpotifyPrefsStore,
        code: String,
        clientId: String,
        verifier: String
    ) {
        try {
            val token = accountsApi.exchangeCode(
                code = code,
                redirectUri = AppConfig.Api.SPOTIFY_REDIRECT_URI,
                clientId = clientId,
                codeVerifier = verifier
            )
            val expiresAt = System.currentTimeMillis() + (token.expiresIn * 1000L)
            store.saveTokens(token.accessToken, token.refreshToken, expiresAt)
            store.clearPendingAuth()

            val profile = runCatching { webApi.getCurrentUser("Bearer ${token.accessToken}") }.getOrNull()
            if (profile != null) {
                store.saveUserProfile(profile.id, profile.displayName ?: profile.id)
            }
            clearInMemoryCaches()
            _uiState.update {
                it.copy(
                    isConnected = true,
                    isConnecting = false,
                    userDisplayName = profile?.displayName ?: profile?.id ?: store.getUserDisplayName(),
                    lastError = null
                )
            }
            _authEvents.tryEmit(SpotifyAuthEvent.Connected)
            refreshPlaylists()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e(TAG, "Spotify code exchange failed: ${e.message}", e)
            store.clearPendingAuth()
            val reason = e.message ?: "token_exchange_failed"
            _uiState.update {
                it.copy(
                    isConnecting = false,
                    lastError = reason
                )
            }
            _authEvents.tryEmit(SpotifyAuthEvent.Failed(reason))
        }
    }

    fun disconnect(context: Context) {
        init(context)
        prefsStore?.clearConnection()
        clearInMemoryCaches()
        _uiState.update {
            it.copy(
                isConnected = false,
                isConnecting = false,
                userDisplayName = null,
                playlists = emptyList(),
                savedTrackIds = emptySet(),
                savingTrackIds = emptySet(),
                lastError = null
            )
        }
    }

    fun setSaveTarget(context: Context, target: SpotifySaveTarget) {
        init(context)
        prefsStore?.setSaveTarget(target)
        cachedPlaylistTrackIds = null
        _uiState.update { it.copy(saveTarget = target, savedTrackIds = emptySet()) }
    }

    fun selectPlaylist(context: Context, playlist: SpotifyPlaylistItem) {
        init(context)
        prefsStore?.setSelectedPlaylist(playlist.id, playlist.name)
        prefsStore?.setSaveTarget(SpotifySaveTarget.PLAYLIST)
        cachedPlaylistTrackIds = null
        _uiState.update {
            it.copy(
                saveTarget = SpotifySaveTarget.PLAYLIST,
                selectedPlaylistId = playlist.id,
                selectedPlaylistName = playlist.name,
                savedTrackIds = emptySet()
            )
        }
    }

    fun refreshPlaylists() {
        val store = prefsStore ?: return
        if (!isConnected()) return
        scope.launch {
            _uiState.update { it.copy(isLoadingPlaylists = true) }
            try {
                val playlists = withAuthorizedCall(store) { authHeader ->
                    val userId = store.getUserId() ?: webApi.getCurrentUser(authHeader).also {
                        store.saveUserProfile(it.id, it.displayName ?: it.id)
                    }.id

                    val collected = mutableListOf<SpotifyPlaylistItem>()
                    var offset = 0
                    var page = 0
                    while (page < MAX_PLAYLIST_PAGES) {
                        val response = webApi.getMyPlaylists(authHeader, limit = 50, offset = offset)
                        collected += response.items
                        if (response.next == null || response.items.isEmpty()) break
                        offset += response.items.size
                        page++
                    }
                    SpotifyPkce.filterEditablePlaylists(collected, userId)
                }
                _uiState.update { it.copy(playlists = playlists, isLoadingPlaylists = false) }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.w(TAG, "Failed to load Spotify playlists: ${e.message}")
                _uiState.update { it.copy(isLoadingPlaylists = false) }
            }
        }
    }

    suspend fun searchTrack(artist: String, title: String): SpotifyTrack? {
        val store = prefsStore ?: return null
        if (!isConnected() || artist.isBlank() || title.isBlank()) return null

        val cacheKey = "${artist.trim().lowercase()}|${title.trim().lowercase()}"
        synchronized(searchCache) {
            if (searchCache.containsKey(cacheKey)) {
                return searchCache[cacheKey]
            }
        }

        val query = "track:\"${title.trim()}\" artist:\"${artist.trim()}\""
        val fallbackQuery = "${artist.trim()} ${title.trim()}"
        return try {
            val found = withAuthorizedCall(store) { authHeader ->
                val exactItems = webApi.searchTracks(authHeader, query = query, limit = 5)
                    .tracks?.items.orEmpty()
                val candidates = exactItems.ifEmpty {
                    webApi.searchTracks(authHeader, query = fallbackQuery, limit = 5)
                        .tracks?.items.orEmpty()
                }
                SpotifyPkce.selectBestTrack(candidates, artist, title)
            }
            synchronized(searchCache) {
                searchCache[cacheKey] = found
            }
            found
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "Spotify search failed: ${e.message}")
            null
        }
    }

    suspend fun checkTrackSaved(trackId: String): Boolean {
        val store = prefsStore ?: return false
        if (!isConnected() || trackId.isBlank()) return false

        val trackUri = "spotify:track:$trackId"
        return try {
            val isSaved = withAuthorizedCall(store) { authHeader ->
                when (store.getSaveTarget()) {
                    SpotifySaveTarget.LIKED_SONGS -> {
                        webApi.checkSavedLibraryItems(authHeader, trackUri).firstOrNull() ?: false
                    }
                    SpotifySaveTarget.PLAYLIST -> {
                        val playlistId = store.getSelectedPlaylistId()
                        if (playlistId.isNullOrBlank()) false
                        else isTrackInPlaylist(authHeader, playlistId, trackId)
                    }
                }
            }
            _uiState.update { current ->
                val nextSet = if (isSaved) current.savedTrackIds + trackId else current.savedTrackIds - trackId
                current.copy(savedTrackIds = nextSet)
            }
            isSaved
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            val details = if (e is HttpException) {
                "HTTP ${e.code()}: ${runCatching { e.response()?.errorBody()?.string() }.getOrNull()}"
            } else {
                e.message
            }
            Log.w(TAG, "checkTrackSaved failed: $details")
            false
        }
    }

    suspend fun saveTrack(trackId: String): SaveTrackResult {
        val store = prefsStore ?: return SaveTrackResult.NOT_CONNECTED
        if (!isConnected() || trackId.isBlank()) return SaveTrackResult.NOT_CONNECTED

        val trackUri = "spotify:track:$trackId"
        _uiState.update { it.copy(savingTrackIds = it.savingTrackIds + trackId) }
        return try {
            withAuthorizedCall(store) { authHeader ->
                when (store.getSaveTarget()) {
                    SpotifySaveTarget.LIKED_SONGS -> {
                        val alreadySaved = webApi.checkSavedLibraryItems(authHeader, trackUri).firstOrNull() ?: false
                        if (alreadySaved) {
                            markTrackSaved(trackId)
                            SaveTrackResult.ALREADY_SAVED
                        } else {
                            val response = webApi.saveLibraryItems(authHeader, trackUri)
                            if (response.isSuccessful) {
                                markTrackSaved(trackId)
                                SaveTrackResult.SAVED
                            } else {
                                val errBody = runCatching { response.errorBody()?.string() }.getOrNull()
                                Log.w(TAG, "Spotify saveLibraryItems failed with HTTP ${response.code()}: $errBody")
                                SaveTrackResult.ERROR
                            }
                        }
                    }
                    SpotifySaveTarget.PLAYLIST -> {
                        val playlistId = store.getSelectedPlaylistId()
                        if (playlistId.isNullOrBlank()) {
                            SaveTrackResult.PLAYLIST_NOT_SELECTED
                        } else {
                            val alreadyInPlaylist = isTrackInPlaylist(authHeader, playlistId, trackId, forceRefresh = true)
                            if (alreadyInPlaylist) {
                                markTrackSaved(trackId)
                                SaveTrackResult.ALREADY_SAVED
                            } else {
                                val response = webApi.addItemsToPlaylist(
                                    authorization = authHeader,
                                    playlistId = playlistId,
                                    body = SpotifyAddTracksRequest(listOf(trackUri))
                                )
                                if (response.isSuccessful) {
                                    cachedPlaylistTrackIds?.takeIf { it.first == playlistId }?.second?.add(trackId)
                                    markTrackSaved(trackId)
                                    SaveTrackResult.SAVED
                                } else {
                                    val errBody = runCatching { response.errorBody()?.string() }.getOrNull()
                                    Log.w(TAG, "Spotify addItemsToPlaylist failed with HTTP ${response.code()}: $errBody")
                                    SaveTrackResult.ERROR
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            val details = if (e is HttpException) {
                "HTTP ${e.code()}: ${runCatching { e.response()?.errorBody()?.string() }.getOrNull()}"
            } else {
                e.message
            }
            Log.w(TAG, "saveTrack failed: $details")
            SaveTrackResult.ERROR
        } finally {
            _uiState.update { it.copy(savingTrackIds = it.savingTrackIds - trackId) }
        }
    }

    private fun markTrackSaved(trackId: String) {
        _uiState.update { it.copy(savedTrackIds = it.savedTrackIds + trackId) }
    }

    private suspend fun isTrackInPlaylist(
        authHeader: String,
        playlistId: String,
        trackId: String,
        forceRefresh: Boolean = false
    ): Boolean {
        if (!forceRefresh) {
            cachedPlaylistTrackIds?.let { (cachedId, ids) ->
                if (cachedId == playlistId) {
                    return ids.contains(trackId)
                }
            }
        }

        val trackIds = mutableSetOf<String>()
        var offset = 0
        var page = 0
        while (page < MAX_PLAYLIST_PAGES) {
            val response = webApi.getPlaylistItems(
                authorization = authHeader,
                playlistId = playlistId,
                limit = 100,
                offset = offset
            )
            response.items.forEach { entry ->
                val id = entry.resolveTrackId()
                if (!id.isNullOrBlank()) {
                    trackIds.add(id)
                }
            }
            if (response.next == null || response.items.isEmpty()) break
            offset += response.items.size
            page++
        }
        cachedPlaylistTrackIds = playlistId to trackIds
        return trackIds.contains(trackId)
    }

    private suspend fun <T> withAuthorizedCall(
        store: SpotifyPrefsStore,
        block: suspend (String) -> T
    ): T {
        val token = getValidAccessToken(store, forceRefresh = false)
            ?: throw IllegalStateException("Spotify not authenticated")
        return try {
            block("Bearer $token")
        } catch (e: HttpException) {
            if (e.code() == 401) {
                val refreshed = getValidAccessToken(store, forceRefresh = true)
                    ?: throw e
                block("Bearer $refreshed")
            } else {
                throw e
            }
        }
    }

    private suspend fun getValidAccessToken(store: SpotifyPrefsStore, forceRefresh: Boolean): String? {
        return tokenMutex.withLock {
            val currentToken = store.getAccessToken()
            val expiresAt = store.getExpiresAtMs()
            val isStillFresh = !currentToken.isNullOrBlank() && (expiresAt - System.currentTimeMillis() > 60_000L)
            if (!forceRefresh && isStillFresh) {
                return@withLock currentToken
            }

            val refreshToken = store.getRefreshToken()
            val clientId = store.resolveClientId(BuildConfig.SPOTIFY_CLIENT_ID)
            if (refreshToken.isNullOrBlank() || clientId.isBlank()) {
                return@withLock null
            }

            try {
                val refreshed = accountsApi.refreshToken(
                    refreshToken = refreshToken,
                    clientId = clientId
                )
                val nextExpiresAt = System.currentTimeMillis() + (refreshed.expiresIn * 1000L)
                store.saveTokens(
                    accessToken = refreshed.accessToken,
                    refreshToken = refreshed.refreshToken ?: refreshToken,
                    expiresAtMs = nextExpiresAt
                )
                refreshed.accessToken
            } catch (e: HttpException) {
                if (e.code() == 400 || e.code() == 401) {
                    Log.w(TAG, "Spotify refresh token revoked/expired (${e.code()}), disconnecting")
                    store.clearConnection()
                    clearInMemoryCaches()
                    _uiState.update {
                        it.copy(
                            isConnected = false,
                            userDisplayName = null,
                            playlists = emptyList(),
                            savedTrackIds = emptySet()
                        )
                    }
                }
                null
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.w(TAG, "Spotify token refresh error: ${e.message}")
                null
            }
        }
    }

    private fun clearInMemoryCaches() {
        synchronized(searchCache) {
            searchCache.clear()
        }
        cachedPlaylistTrackIds = null
    }
}
