package app.radiorecalarm.update

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateManagerTest {

    @Test
    fun testIsNewerVersion_newerRemoteVersion_returnsTrue() {
        assertTrue(AppUpdateManager.isNewerVersion("1.0", "1.1"))
        assertTrue(AppUpdateManager.isNewerVersion("1.0", "1.0.1"))
        assertTrue(AppUpdateManager.isNewerVersion("1.0.0", "1.0.1"))
        assertTrue(AppUpdateManager.isNewerVersion("1.9.9", "2.0.0"))
        assertTrue(AppUpdateManager.isNewerVersion("v1.0", "v1.1"))
        assertTrue(AppUpdateManager.isNewerVersion("V1.0", "V1.0.1"))
        assertTrue(AppUpdateManager.isNewerVersion("1.0", "v1.0.1"))
        assertTrue(AppUpdateManager.isNewerVersion("1.0.0-alpha", "1.1.0"))
    }

    @Test
    fun testIsNewerVersion_olderOrEqualVersion_returnsFalse() {
        assertFalse(AppUpdateManager.isNewerVersion("1.0", "1.0"))
        assertFalse(AppUpdateManager.isNewerVersion("v1.0", "1.0"))
        assertFalse(AppUpdateManager.isNewerVersion("1.0.0", "1.0"))
        assertFalse(AppUpdateManager.isNewerVersion("1.1", "1.0"))
        assertFalse(AppUpdateManager.isNewerVersion("2.0.0", "1.9.9"))
        assertFalse(AppUpdateManager.isNewerVersion("1.0.5", "1.0.4"))
        assertFalse(AppUpdateManager.isNewerVersion("1.0", ""))
    }

    @Test
    fun testGitHubReleaseJsonParsing() {
        val jsonString = """
            {
              "tag_name": "v1.1.0",
              "name": "Kellraadio 1.1",
              "body": "Uuendused ja parandused",
              "published_at": "2026-09-06T15:00:00Z",
              "prerelease": false,
              "draft": false,
              "assets": [
                {
                  "name": "Kellraadio-v1.1.0.apk",
                  "size": 16428000,
                  "browser_download_url": "https://github.com/marugusu/Kellraadio/releases/download/v1.1.0/Kellraadio-v1.1.0.apk",
                  "content_type": "application/vnd.android.package-archive"
                }
              ]
            }
        """.trimIndent()

        val json = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
        }

        val release = json.decodeFromString<GitHubRelease>(jsonString)
        assertEquals("v1.1.0", release.tagName)
        assertEquals("Kellraadio 1.1", release.name)
        assertEquals("Uuendused ja parandused", release.body)
        assertEquals(1, release.assets.size)

        val asset = release.assets.first()
        assertEquals("Kellraadio-v1.1.0.apk", asset.name)
        assertEquals(16428000L, asset.size)
        assertTrue(asset.name.endsWith(".apk"))
    }
}
