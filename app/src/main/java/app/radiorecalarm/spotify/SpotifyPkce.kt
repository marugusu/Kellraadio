package app.radiorecalarm.spotify

import app.radiorecalarm.MetadataTrackMatcher
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * Pure Kotlin helpers for Spotify OAuth 2.0 Authorization Code with PKCE,
 * track matching, and playlist filtering.
 */
object SpotifyPkce {
    private const val CODE_VERIFIER_CHARSET =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"
    private const val STATE_CHARSET =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"

    sealed class AuthRedirectResult {
        data class Code(val code: String) : AuthRedirectResult()
        data class Error(val error: String) : AuthRedirectResult()
        data object Ignored : AuthRedirectResult()
    }

    fun generateCodeVerifier(random: SecureRandom = SecureRandom(), length: Int = 64): String {
        val safeLength = length.coerceIn(43, 128)
        return buildString(safeLength) {
            repeat(safeLength) {
                append(CODE_VERIFIER_CHARSET[random.nextInt(CODE_VERIFIER_CHARSET.length)])
            }
        }
    }

    fun generateCodeChallenge(codeVerifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(codeVerifier.toByteArray(Charsets.US_ASCII))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }

    fun generateState(random: SecureRandom = SecureRandom(), length: Int = 32): String {
        return buildString(length) {
            repeat(length) {
                append(STATE_CHARSET[random.nextInt(STATE_CHARSET.length)])
            }
        }
    }

    fun buildAuthorizeUrl(
        clientId: String,
        redirectUri: String,
        codeChallenge: String,
        state: String,
        scopes: String
    ): String {
        val params = listOf(
            "client_id" to clientId.trim(),
            "response_type" to "code",
            "redirect_uri" to redirectUri,
            "code_challenge_method" to "S256",
            "code_challenge" to codeChallenge,
            "state" to state,
            "scope" to scopes
        ).joinToString("&") { (k, v) ->
            "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v, "UTF-8")}"
        }
        return "https://accounts.spotify.com/authorize?$params"
    }

    fun parseRedirectUri(
        uriString: String,
        expectedRedirectUri: String,
        expectedState: String?
    ): AuthRedirectResult {
        val parsed = runCatching { URI(uriString) }.getOrNull() ?: return AuthRedirectResult.Ignored
        val expected = runCatching { URI(expectedRedirectUri) }.getOrNull() ?: return AuthRedirectResult.Ignored
        if (!parsed.scheme.equals(expected.scheme, ignoreCase = true) ||
            !parsed.host.equals(expected.host, ignoreCase = true)
        ) {
            return AuthRedirectResult.Ignored
        }

        val queryParams = parseQueryParams(parsed.rawQuery.orEmpty())
        val actualState = queryParams["state"]
        if (!expectedState.isNullOrEmpty() && actualState != expectedState) {
            return AuthRedirectResult.Error("state_mismatch")
        }

        val error = queryParams["error"]
        if (!error.isNullOrBlank()) {
            return AuthRedirectResult.Error(error)
        }

        val code = queryParams["code"]
        return if (!code.isNullOrBlank()) {
            AuthRedirectResult.Code(code)
        } else {
            AuthRedirectResult.Error("missing_code")
        }
    }

    fun selectBestTrack(
        items: List<SpotifyTrack>,
        artist: String,
        title: String
    ): SpotifyTrack? {
        return MetadataTrackMatcher.selectBest(
            items = items,
            queryArtist = artist,
            queryTitle = title,
            candidateArtists = { track -> track.artists.map { it.name } },
            candidateTitle = { track -> track.name },
            tieBreaker = { track -> if (!track.album?.images.isNullOrEmpty()) 5 else 0 }
        )
    }

    fun filterEditablePlaylists(
        playlists: List<SpotifyPlaylistItem>,
        currentUserId: String?
    ): List<SpotifyPlaylistItem> {
        return playlists.filter { item ->
            item.id.isNotBlank() &&
                (item.collaborative || (!currentUserId.isNullOrBlank() && item.owner?.id == currentUserId))
        }
    }

    private fun parseQueryParams(rawQuery: String): Map<String, String> {
        if (rawQuery.isBlank()) return emptyMap()
        return rawQuery.split("&")
            .mapNotNull { part ->
                if (part.isBlank()) return@mapNotNull null
                val eqIndex = part.indexOf('=')
                if (eqIndex <= 0) return@mapNotNull null
                val key = URLDecoder.decode(part.substring(0, eqIndex), "UTF-8")
                val value = URLDecoder.decode(part.substring(eqIndex + 1), "UTF-8")
                key to value
            }
            .toMap()
    }
}
