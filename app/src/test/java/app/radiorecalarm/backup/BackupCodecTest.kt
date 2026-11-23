package app.radiorecalarm.backup

import app.radiorecalarm.Alarm
import app.radiorecalarm.AppConfig
import app.radiorecalarm.RadioStation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class BackupCodecTest {

    @Test
    fun `encode and decode roundtrip preserves stations favorites alarms with recordAudio and settings`() {
        val alarms = listOf(
            Alarm(
                id = 7,
                hour = 7,
                minute = 30,
                days = setOf(Calendar.MONDAY, Calendar.FRIDAY),
                stationName = "Vikerraadio",
                stationUrl = "https://icecast.err.ee/vikerraadio.mp3",
                isEnabled = true,
                recordAudio = true
            ),
            Alarm(
                id = 8,
                hour = 9,
                minute = 0,
                days = emptySet(),
                stationName = "Raadio 2",
                stationUrl = "https://icecast.err.ee/raadio2.mp3",
                isEnabled = false,
                recordAudio = false
            )
        )
        val stations = listOf(
            RadioStation(
                id = 1,
                name = "Vikerraadio",
                url = "https://icecast.err.ee/vikerraadio.mp3",
                isFavorite = true,
                isUserStation = false,
                favoriteOrder = 1
            ),
            RadioStation(
                id = 10000,
                name = "Minu Jazz",
                url = "https://example.com/jazz.mp3",
                category = "Jazz",
                countryCode = "EE",
                isFavorite = true,
                isUserStation = true,
                favoriteOrder = 2
            )
        )
        val settings = BackupSettings(
            colsPortrait = 4,
            colsLandscape = 5,
            showFlags = false,
            hideRemoteStations = true,
            widgetTransparency = 0.6f,
            continuousRecording = true,
            minTrackDurationSeconds = 20L,
            preRollSeconds = 12L,
            postRollSeconds = 8L
        )

        val created = BackupCodec.createBackupData(
            alarms = alarms,
            stations = stations,
            settings = settings,
            exportedAt = 1763890000000L
        )
        val encoded = BackupCodec.encode(created)
        val decoded = BackupCodec.decode(encoded)

        assertEquals(BackupCodec.CURRENT_SCHEMA_VERSION, decoded.schemaVersion)
        assertEquals(1763890000000L, decoded.exportedAt)
        assertEquals(1, decoded.customStations.size)
        assertEquals("Minu Jazz", decoded.customStations.first().name)
        assertEquals(1, decoded.favoriteUrls.size)
        assertEquals(1, decoded.favoriteUrls.first().stationId)
        assertEquals("Vikerraadio", decoded.favoriteUrls.first().stationName)
        assertEquals(2, decoded.alarms.size)
        assertTrue(decoded.alarms[0].recordAudio)
        assertFalse(decoded.alarms[1].recordAudio)
        assertEquals(settings, decoded.settings)
    }

    @Test
    fun `decode supports legacy backup without schemaVersion settings recordAudio or favorite metadata`() {
        val legacyJson = """
            {
              "customStations": [
                {
                  "name": "Legacy Custom",
                  "url": "https://example.com/stream",
                  "category": "Rock",
                  "countryCode": "EE",
                  "favoriteOrder": 1
                }
              ],
              "favoriteUrls": [
                {
                  "url": "https://icecast.err.ee/vikerraadio.mp3",
                  "favoriteOrder": 2
                }
              ],
              "alarms": [
                {
                  "hour": 6,
                  "minute": 45,
                  "days": [2, 3],
                  "stationName": "Vikerraadio",
                  "stationUrl": "https://icecast.err.ee/vikerraadio.mp3",
                  "isEnabled": true
                }
              ]
            }
        """.trimIndent()

        val decoded = BackupCodec.decode(legacyJson)

        assertEquals(1, decoded.customStations.size)
        assertTrue(decoded.customStations.first().isFavorite)
        assertEquals(1, decoded.favoriteUrls.size)
        assertNull(decoded.favoriteUrls.first().stationId)
        assertNull(decoded.favoriteUrls.first().stationName)
        assertEquals(1, decoded.alarms.size)
        assertFalse(decoded.alarms.first().recordAudio)
        assertNull(decoded.settings)
        assertNull(decoded.exportedAt)
    }

    @Test
    fun `decode ignores unknown future keys`() {
        val futureJson = """
            {
              "schemaVersion": 99,
              "futureFeatureFlag": true,
              "customStations": [],
              "favoriteUrls": [],
              "alarms": []
            }
        """.trimIndent()

        val decoded = BackupCodec.decode(futureJson)
        assertEquals(99, decoded.schemaVersion)
        assertTrue(decoded.customStations.isEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `decode rejects blank input`() {
        BackupCodec.decode("   ")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `decode rejects unrelated json object without backup keys`() {
        BackupCodec.decode("""{"foo": "bar"}""")
    }

    @Test
    fun `findCuratedFavoriteMatch prefers id then url then exact name and skips user stations`() {
        val curatedById = RadioStation(
            id = 5,
            name = "Vikerraadio",
            url = "https://new-url.err.ee/viker.mp3",
            isUserStation = false
        )
        val curatedByUrl = RadioStation(
            id = 6,
            name = "Raadio 2 New Name",
            url = "https://icecast.err.ee/r2.mp3",
            isUserStation = false
        )
        val curatedByName = RadioStation(
            id = 7,
            name = "Klassikaraadio",
            url = "https://new-cdn.err.ee/klassika.mp3",
            isUserStation = false
        )
        val userStationSameName = RadioStation(
            id = 10000,
            name = "OnlyUser",
            url = "https://user.example/stream",
            isUserStation = true
        )
        val all = listOf(curatedById, curatedByUrl, curatedByName, userStationSameName)

        // 1. Match by ID even if URL and name changed
        val matchId = BackupCodec.findCuratedFavoriteMatch(
            BackupFavorite(url = "https://old-url.err.ee/viker.mp3", stationId = 5, stationName = "Old"),
            all
        )
        assertEquals(curatedById, matchId)

        // 2. Match by URL when ID doesn't match
        val matchUrl = BackupCodec.findCuratedFavoriteMatch(
            BackupFavorite(url = "https://icecast.err.ee/r2.mp3", stationId = 999, stationName = "Raadio 2"),
            all
        )
        assertEquals(curatedByUrl, matchUrl)

        // 3. Match by exact name when ID absent and URL changed
        val matchName = BackupCodec.findCuratedFavoriteMatch(
            BackupFavorite(url = "https://old-cdn.err.ee/klassika.mp3", stationId = null, stationName = "Klassikaraadio"),
            all
        )
        assertEquals(curatedByName, matchName)

        // 4. Never match user stations
        val matchUser = BackupCodec.findCuratedFavoriteMatch(
            BackupFavorite(url = "https://user.example/stream", stationId = 10000, stationName = "OnlyUser"),
            all
        )
        assertNull(matchUser)
    }

    @Test
    fun `buildRestoredUserStations assigns sequential ids starting at 10000 and matching priorities`() {
        val restored = BackupCodec.buildRestoredUserStations(
            customStations = listOf(
                BackupStation(name = "A", url = "https://a.test/1"),
                BackupStation(name = "B", url = "https://b.test/2")
            ),
            maxExistingId = 120,
            uuidProvider = { "fixed-uuid" }
        )

        assertEquals(2, restored.size)
        assertEquals(10000, restored[0].id)
        assertEquals(10000, restored[0].priority)
        assertEquals(10001, restored[1].id)
        assertEquals(10001, restored[1].priority)
        assertTrue(restored.all { it.isUserStation && it.uuid == "fixed-uuid" })
    }

    @Test
    fun `buildRestoredAlarms preserves recordAudio and sanitizes time and days`() {
        val restored = BackupCodec.buildRestoredAlarms(
            listOf(
                BackupAlarm(
                    hour = 25,
                    minute = -3,
                    days = listOf(Calendar.MONDAY, 99, Calendar.SUNDAY),
                    stationName = "Test",
                    stationUrl = "https://test.ee/stream",
                    isEnabled = true,
                    recordAudio = true
                )
            )
        )

        assertEquals(1, restored.size)
        assertEquals(23, restored[0].hour)
        assertEquals(0, restored[0].minute)
        assertEquals(setOf(Calendar.MONDAY, Calendar.SUNDAY), restored[0].days)
        assertTrue(restored[0].recordAudio)
    }

    @Test
    fun `sanitizeSettings clamps invalid bounds and preserves fallback for nulls`() {
        val fallback = BackupSettings(
            colsPortrait = 3,
            colsLandscape = 4,
            showFlags = true,
            hideRemoteStations = false,
            widgetTransparency = 0.25f,
            continuousRecording = false,
            minTrackDurationSeconds = 15L,
            preRollSeconds = 10L,
            postRollSeconds = 5L
        )
        val raw = BackupSettings(
            colsPortrait = 99,
            colsLandscape = 0,
            showFlags = null,
            hideRemoteStations = true,
            widgetTransparency = Float.NaN,
            continuousRecording = true,
            minTrackDurationSeconds = 999L,
            preRollSeconds = -5L,
            postRollSeconds = null
        )

        val sanitized = BackupCodec.sanitizeSettings(raw, fallback)

        assertEquals(6, sanitized.colsPortrait)
        assertEquals(1, sanitized.colsLandscape)
        assertTrue(sanitized.showFlags)
        assertTrue(sanitized.hideRemoteStations)
        assertEquals(0.25f, sanitized.widgetTransparency, 0.0001f)
        assertTrue(sanitized.continuousRecording)
        assertEquals(AppConfig.Recording.MIN_DURATION_MAX_SECONDS, sanitized.minTrackDurationSeconds)
        assertEquals(0L, sanitized.preRollSeconds)
        assertEquals(5L, sanitized.postRollSeconds)
    }

    @Test
    fun `resolveSelectedStationAfterRestore matches shifted user station by url and name or returns null`() {
        val previousUser = RadioStation(
            id = 10005,
            name = "My Station",
            url = "https://my.stream/live",
            isUserStation = true
        )
        val shiftedUser = previousUser.copy(id = 10000)
        val curated = RadioStation(id = 1, name = "Vikerraadio", url = "https://err.ee/viker")

        val resolved = BackupCodec.resolveSelectedStationAfterRestore(
            previousSelectedStation = previousUser,
            stationsAfterRestore = listOf(curated, shiftedUser)
        )
        assertNotNull(resolved)
        assertEquals(10000, resolved?.id)

        val missing = BackupCodec.resolveSelectedStationAfterRestore(
            previousSelectedStation = previousUser,
            stationsAfterRestore = listOf(curated)
        )
        assertNull(missing)
    }
}
