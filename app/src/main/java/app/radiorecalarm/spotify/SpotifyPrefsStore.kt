package app.radiorecalarm.spotify

import android.content.Context
import android.content.SharedPreferences
import app.radiorecalarm.AppConfig
import app.radiorecalarm.BuildConfig

enum class SpotifySaveTarget {
    LIKED_SONGS,
    PLAYLIST
}

/**
 * Isolated credential and preference storage for Spotify integration.
 * Uses a dedicated SharedPreferences file excluded from BackupCodec and Android backup.
 */
class SpotifyPrefsStore(context: Context) {
    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(
        AppConfig.Api.SPOTIFY_PREFS_NAME,
        Context.MODE_PRIVATE
    )

    fun resolveClientId(defaultBuildClientId: String = BuildConfig.SPOTIFY_CLIENT_ID): String {
        return getCustomClientId().ifEmpty { defaultBuildClientId.trim() }
    }

    fun getCustomClientId(): String = prefs.getString(KEY_CUSTOM_CLIENT_ID, "")?.trim().orEmpty()

    fun setCustomClientId(clientId: String) {
        prefs.edit().putString(KEY_CUSTOM_CLIENT_ID, clientId.trim()).apply()
    }

    fun getAccessToken(): String? = prefs.getString(KEY_ACCESS_TOKEN, null)
    fun getRefreshToken(): String? = prefs.getString(KEY_REFRESH_TOKEN, null)
    fun getExpiresAtMs(): Long = prefs.getLong(KEY_EXPIRES_AT_MS, 0L)

    fun saveTokens(accessToken: String, refreshToken: String?, expiresAtMs: Long) {
        prefs.edit().apply {
            putString(KEY_ACCESS_TOKEN, accessToken)
            if (!refreshToken.isNullOrBlank()) {
                putString(KEY_REFRESH_TOKEN, refreshToken)
            }
            putLong(KEY_EXPIRES_AT_MS, expiresAtMs)
        }.apply()
    }

    fun savePendingAuth(codeVerifier: String, state: String, clientId: String) {
        prefs.edit()
            .putString(KEY_PENDING_CODE_VERIFIER, codeVerifier)
            .putString(KEY_PENDING_STATE, state)
            .putString(KEY_PENDING_CLIENT_ID, clientId)
            .apply()
    }

    fun getPendingCodeVerifier(): String? = prefs.getString(KEY_PENDING_CODE_VERIFIER, null)
    fun getPendingState(): String? = prefs.getString(KEY_PENDING_STATE, null)
    fun getPendingClientId(): String? = prefs.getString(KEY_PENDING_CLIENT_ID, null)

    fun clearPendingAuth() {
        prefs.edit()
            .remove(KEY_PENDING_CODE_VERIFIER)
            .remove(KEY_PENDING_STATE)
            .remove(KEY_PENDING_CLIENT_ID)
            .apply()
    }

    fun getUserId(): String? = prefs.getString(KEY_USER_ID, null)
    fun getUserDisplayName(): String? = prefs.getString(KEY_USER_DISPLAY_NAME, null)

    fun saveUserProfile(userId: String, displayName: String?) {
        prefs.edit()
            .putString(KEY_USER_ID, userId)
            .putString(KEY_USER_DISPLAY_NAME, displayName)
            .apply()
    }

    fun getSaveTarget(): SpotifySaveTarget {
        val raw = prefs.getString(KEY_SAVE_TARGET, SpotifySaveTarget.LIKED_SONGS.name)
        return runCatching { SpotifySaveTarget.valueOf(raw ?: "") }
            .getOrDefault(SpotifySaveTarget.LIKED_SONGS)
    }

    fun setSaveTarget(target: SpotifySaveTarget) {
        prefs.edit().putString(KEY_SAVE_TARGET, target.name).apply()
    }

    fun getSelectedPlaylistId(): String? = prefs.getString(KEY_SELECTED_PLAYLIST_ID, null)
    fun getSelectedPlaylistName(): String? = prefs.getString(KEY_SELECTED_PLAYLIST_NAME, null)

    fun setSelectedPlaylist(id: String?, name: String?) {
        prefs.edit().apply {
            if (id.isNullOrBlank()) {
                remove(KEY_SELECTED_PLAYLIST_ID)
                remove(KEY_SELECTED_PLAYLIST_NAME)
            } else {
                putString(KEY_SELECTED_PLAYLIST_ID, id)
                putString(KEY_SELECTED_PLAYLIST_NAME, name)
            }
        }.apply()
    }

    fun clearConnection() {
        prefs.edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .remove(KEY_EXPIRES_AT_MS)
            .remove(KEY_USER_ID)
            .remove(KEY_USER_DISPLAY_NAME)
            .remove(KEY_PENDING_CODE_VERIFIER)
            .remove(KEY_PENDING_STATE)
            .remove(KEY_PENDING_CLIENT_ID)
            .apply()
    }

    companion object {
        private const val KEY_CUSTOM_CLIENT_ID = "custom_client_id"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_EXPIRES_AT_MS = "expires_at_ms"
        private const val KEY_PENDING_CODE_VERIFIER = "pending_code_verifier"
        private const val KEY_PENDING_STATE = "pending_state"
        private const val KEY_PENDING_CLIENT_ID = "pending_client_id"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USER_DISPLAY_NAME = "user_display_name"
        private const val KEY_SAVE_TARGET = "save_target"
        private const val KEY_SELECTED_PLAYLIST_ID = "selected_playlist_id"
        private const val KEY_SELECTED_PLAYLIST_NAME = "selected_playlist_name"
    }
}
