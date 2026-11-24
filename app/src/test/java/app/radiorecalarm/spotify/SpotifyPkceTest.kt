package app.radiorecalarm.spotify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI

class SpotifyPkceTest {

    @Test
    fun `generateCodeVerifier produces RFC-compliant verifier`() {
        val verifier = SpotifyPkce.generateCodeVerifier()
        assertTrue(verifier.length in 43..128)
        assertTrue(verifier.all { it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' || it in "-._~" })
    }

    @Test
    fun `generateCodeChallenge matches RFC 7636 test vector`() {
        val verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"
        val expectedChallenge = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM"
        assertEquals(expectedChallenge, SpotifyPkce.generateCodeChallenge(verifier))
    }

    @Test
    fun `buildAuthorizeUrl includes required PKCE parameters`() {
        val url = SpotifyPkce.buildAuthorizeUrl(
            clientId = "abc123",
            redirectUri = "kellraadio://spotify-callback",
            codeChallenge = "challengeXYZ",
            state = "state42",
            scopes = "user-library-read user-library-modify"
        )
        val uri = URI(url)
        assertEquals("accounts.spotify.com", uri.host)
        assertEquals("/authorize", uri.path)
        assertTrue(url.contains("client_id=abc123"))
        assertTrue(url.contains("response_type=code"))
        assertTrue(url.contains("code_challenge_method=S256"))
        assertTrue(url.contains("code_challenge=challengeXYZ"))
        assertTrue(url.contains("state=state42"))
    }

    @Test
    fun `parseRedirectUri extracts authorization code when state matches`() {
        val result = SpotifyPkce.parseRedirectUri(
            uriString = "kellraadio://spotify-callback?code=auth_code_123&state=state42",
            expectedRedirectUri = "kellraadio://spotify-callback",
            expectedState = "state42"
        )
        assertEquals(SpotifyPkce.AuthRedirectResult.Code("auth_code_123"), result)
    }

    @Test
    fun `parseRedirectUri rejects mismatched state`() {
        val result = SpotifyPkce.parseRedirectUri(
            uriString = "kellraadio://spotify-callback?code=auth_code_123&state=wrong",
            expectedRedirectUri = "kellraadio://spotify-callback",
            expectedState = "state42"
        )
        assertEquals(SpotifyPkce.AuthRedirectResult.Error("state_mismatch"), result)
    }

    @Test
    fun `parseRedirectUri returns error when access denied`() {
        val result = SpotifyPkce.parseRedirectUri(
            uriString = "kellraadio://spotify-callback?error=access_denied&state=state42",
            expectedRedirectUri = "kellraadio://spotify-callback",
            expectedState = "state42"
        )
        assertEquals(SpotifyPkce.AuthRedirectResult.Error("access_denied"), result)
    }

    @Test
    fun `parseRedirectUri ignores unrelated uris`() {
        val result = SpotifyPkce.parseRedirectUri(
            uriString = "https://example.com/other?code=123",
            expectedRedirectUri = "kellraadio://spotify-callback",
            expectedState = "state42"
        )
        assertEquals(SpotifyPkce.AuthRedirectResult.Ignored, result)
    }

    @Test
    fun `selectBestTrack prefers exact artist and title match`() {
        val tribute = SpotifyTrack(
            id = "tribute",
            uri = "spotify:track:tribute",
            name = "Kauges külas (Karaoke)",
            artists = listOf(SpotifyArtist(name = "Karaoke Band"))
        )
        val exact = SpotifyTrack(
            id = "exact",
            uri = "spotify:track:exact",
            name = "Kauges külas",
            artists = listOf(SpotifyArtist(name = "Curly Strings")),
            album = SpotifyAlbum(images = listOf(SpotifyImage(url = "https://img/1.jpg", width = 640)))
        )

        val picked = SpotifyPkce.selectBestTrack(
            items = listOf(tribute, exact),
            artist = "Curly Strings",
            title = "Kauges külas"
        )
        assertNotNull(picked)
        assertEquals("exact", picked?.id)
        assertNull(SpotifyPkce.selectBestTrack(emptyList(), "A", "B"))
    }

    @Test
    fun `selectBestTrack rejects unrelated fallback candidates even when they have cover images`() {
        val wrongTrack = SpotifyTrack(
            id = "ultra",
            uri = "spotify:track:ultra",
            name = "Ultra",
            artists = listOf(SpotifyArtist(name = "Laura")),
            album = SpotifyAlbum(
                name = "Ultra",
                releaseDate = "2009",
                images = listOf(SpotifyImage(url = "https://img/ultra.jpg", width = 640))
            )
        )

        val picked = SpotifyPkce.selectBestTrack(
            items = listOf(wrongTrack),
            artist = "Ludmilla Issakova",
            title = "Uruguai naljalaul"
        )
        assertNull(picked)
    }

    @Test
    fun `filterEditablePlaylists keeps owned and collaborative playlists only`() {
        val playlists = listOf(
            SpotifyPlaylistItem(id = "1", name = "Mine", owner = SpotifyPlaylistOwner(id = "user1")),
            SpotifyPlaylistItem(id = "2", name = "Collab", collaborative = true, owner = SpotifyPlaylistOwner(id = "other")),
            SpotifyPlaylistItem(id = "3", name = "Someone Else", collaborative = false, owner = SpotifyPlaylistOwner(id = "other"))
        )

        val editable = SpotifyPkce.filterEditablePlaylists(playlists, "user1")
        assertEquals(listOf("1", "2"), editable.map { it.id })
    }

    @Test
    fun `playlist track entry resolves id from both Feb 2026 item and legacy track fields`() {
        val modernEntry = SpotifyPlaylistTrackEntry(
            item = SpotifyPlaylistTrackId(id = "modern123", uri = "spotify:track:modern123")
        )
        val legacyUriOnlyEntry = SpotifyPlaylistTrackEntry(
            track = SpotifyPlaylistTrackId(id = null, uri = "spotify:track:legacy456")
        )
        assertEquals("modern123", modernEntry.resolveTrackId())
        assertEquals("legacy456", legacyUriOnlyEntry.resolveTrackId())
    }
}
