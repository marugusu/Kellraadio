package app.radiorecalarm.backup

import android.content.SharedPreferences
import app.radiorecalarm.Alarm
import app.radiorecalarm.AppConfig
import app.radiorecalarm.RadioStation
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.util.Calendar
import java.util.UUID

@Serializable
data class BackupData(
    val schemaVersion: Int = BackupCodec.CURRENT_SCHEMA_VERSION,
    val exportedAt: Long? = null,
    val customStations: List<BackupStation> = emptyList(),
    val favoriteUrls: List<BackupFavorite> = emptyList(),
    val alarms: List<BackupAlarm> = emptyList(),
    val settings: BackupSettings? = null
)

@Serializable
data class BackupAlarm(
    val hour: Int,
    val minute: Int,
    val days: List<Int> = emptyList(),
    val stationName: String,
    val stationUrl: String,
    val isEnabled: Boolean = true,
    val recordAudio: Boolean = false
)

@Serializable
data class BackupStation(
    val name: String,
    val url: String,
    val category: String = "",
    val countryCode: String = "",
    val isFavorite: Boolean = true,
    val favoriteOrder: Int = 0,
    val iconUrl: String = ""
)

@Serializable
data class BackupFavorite(
    val url: String,
    val favoriteOrder: Int = 0,
    val stationId: Int? = null,
    val stationName: String? = null
)

@Serializable
data class BackupSettings(
    val colsPortrait: Int? = null,
    val colsLandscape: Int? = null,
    val showFlags: Boolean? = null,
    val hideRemoteStations: Boolean? = null,
    val widgetTransparency: Float? = null,
    val continuousRecording: Boolean? = null,
    val minTrackDurationSeconds: Long? = null,
    val preRollSeconds: Long? = null,
    val postRollSeconds: Long? = null
)

data class SanitizedBackupSettings(
    val colsPortrait: Int,
    val colsLandscape: Int,
    val showFlags: Boolean,
    val hideRemoteStations: Boolean,
    val widgetTransparency: Float,
    val continuousRecording: Boolean,
    val minTrackDurationSeconds: Long,
    val preRollSeconds: Long,
    val postRollSeconds: Long
)

data class PendingBackupImport(
    val data: BackupData
) {
    val customStationCount: Int get() = data.customStations.size
    val favoriteCount: Int get() = data.favoriteUrls.size
    val alarmCount: Int get() = data.alarms.size
    val hasSettings: Boolean get() = data.settings != null
    val exportedAt: Long? get() = data.exportedAt
}

object BackupCodec {
    const val CURRENT_SCHEMA_VERSION = 1

    private const val MIN_COLS = 1
    private const val MAX_COLS = 6
    private const val DEFAULT_COLS = 3
    private const val DEFAULT_WIDGET_TRANSPARENCY = 0.25f

    private val RECOGNIZED_ROOT_KEYS = setOf(
        "schemaVersion",
        "exportedAt",
        "customStations",
        "favoriteUrls",
        "alarms",
        "settings"
    )

    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    fun encode(data: BackupData): String = json.encodeToString(BackupData.serializer(), data)

    fun decode(jsonText: String): BackupData {
        if (jsonText.isBlank()) {
            throw IllegalArgumentException("Varundusfail on tühi")
        }
        val element = json.parseToJsonElement(jsonText)
        val obj = element as? JsonObject
            ?: throw IllegalArgumentException("Vigane varundusfaili vorming")
        if (obj.keys.none { it in RECOGNIZED_ROOT_KEYS }) {
            throw IllegalArgumentException("Fail ei ole Kellraadio varundus")
        }
        return json.decodeFromString(BackupData.serializer(), jsonText)
    }

    fun createBackupData(
        alarms: List<Alarm>,
        stations: List<RadioStation>,
        settings: BackupSettings,
        exportedAt: Long = System.currentTimeMillis()
    ): BackupData {
        val backupAlarms = alarms.map { alarm ->
            BackupAlarm(
                hour = alarm.hour,
                minute = alarm.minute,
                days = alarm.days.toList().sorted(),
                stationName = alarm.stationName,
                stationUrl = alarm.stationUrl,
                isEnabled = alarm.isEnabled,
                recordAudio = alarm.recordAudio
            )
        }
        val customStations = stations.filter { it.isUserStation }.map { station ->
            BackupStation(
                name = station.name,
                url = station.url,
                category = station.category,
                countryCode = station.countryCode,
                isFavorite = station.isFavorite,
                favoriteOrder = station.favoriteOrder,
                iconUrl = station.iconUrl
            )
        }
        val favoriteUrls = stations.filter { it.isFavorite && !it.isUserStation }.map { station ->
            BackupFavorite(
                url = station.url,
                favoriteOrder = station.favoriteOrder,
                stationId = station.id,
                stationName = station.name
            )
        }
        return BackupData(
            schemaVersion = CURRENT_SCHEMA_VERSION,
            exportedAt = exportedAt,
            customStations = customStations,
            favoriteUrls = favoriteUrls,
            alarms = backupAlarms,
            settings = settings
        )
    }

    fun readSettingsFromPrefs(prefs: SharedPreferences): BackupSettings {
        return BackupSettings(
            colsPortrait = prefs.getInt("cols_portrait", DEFAULT_COLS).coerceIn(MIN_COLS, MAX_COLS),
            colsLandscape = prefs.getInt("cols_landscape", DEFAULT_COLS).coerceIn(MIN_COLS, MAX_COLS),
            showFlags = prefs.getBoolean("show_flags", true),
            hideRemoteStations = prefs.getBoolean("hide_remote_stations", false),
            widgetTransparency = prefs.getFloat("widget_transparency", DEFAULT_WIDGET_TRANSPARENCY)
                .let { if (it.isNaN()) DEFAULT_WIDGET_TRANSPARENCY else it.coerceIn(0f, 1f) },
            continuousRecording = prefs.getBoolean(AppConfig.Recording.PREF_CONTINUOUS_RECORDING, false),
            minTrackDurationSeconds = prefs.getLong(
                AppConfig.Recording.PREF_MIN_DURATION_SECONDS,
                AppConfig.Recording.MIN_DURATION_SECONDS
            ).coerceIn(0L, AppConfig.Recording.MIN_DURATION_MAX_SECONDS),
            preRollSeconds = AppConfig.Recording.resolvePreRollSeconds(prefs),
            postRollSeconds = AppConfig.Recording.resolvePostRollSeconds(prefs)
        )
    }

    fun sanitizeSettings(raw: BackupSettings, fallback: BackupSettings): SanitizedBackupSettings {
        val rawTransparency = raw.widgetTransparency ?: fallback.widgetTransparency ?: DEFAULT_WIDGET_TRANSPARENCY
        val safeTransparency = if (rawTransparency.isNaN()) DEFAULT_WIDGET_TRANSPARENCY else rawTransparency.coerceIn(0f, 1f)

        return SanitizedBackupSettings(
            colsPortrait = (raw.colsPortrait ?: fallback.colsPortrait ?: DEFAULT_COLS).coerceIn(MIN_COLS, MAX_COLS),
            colsLandscape = (raw.colsLandscape ?: fallback.colsLandscape ?: DEFAULT_COLS).coerceIn(MIN_COLS, MAX_COLS),
            showFlags = raw.showFlags ?: fallback.showFlags ?: true,
            hideRemoteStations = raw.hideRemoteStations ?: fallback.hideRemoteStations ?: false,
            widgetTransparency = safeTransparency,
            continuousRecording = raw.continuousRecording ?: fallback.continuousRecording ?: false,
            minTrackDurationSeconds = (raw.minTrackDurationSeconds ?: fallback.minTrackDurationSeconds ?: AppConfig.Recording.MIN_DURATION_SECONDS)
                .coerceIn(0L, AppConfig.Recording.MIN_DURATION_MAX_SECONDS),
            preRollSeconds = (raw.preRollSeconds ?: fallback.preRollSeconds ?: AppConfig.Recording.PRE_ROLL_SECONDS)
                .coerceIn(0L, AppConfig.Recording.PRE_ROLL_MAX_SECONDS),
            postRollSeconds = (raw.postRollSeconds ?: fallback.postRollSeconds ?: AppConfig.Recording.POST_ROLL_SECONDS)
                .coerceIn(0L, AppConfig.Recording.POST_ROLL_MAX_SECONDS)
        )
    }

    fun writeSettingsToPrefs(prefs: SharedPreferences, settings: SanitizedBackupSettings) {
        prefs.edit()
            .putInt("cols_portrait", settings.colsPortrait)
            .putInt("cols_landscape", settings.colsLandscape)
            .putBoolean("show_flags", settings.showFlags)
            .putBoolean("hide_remote_stations", settings.hideRemoteStations)
            .putFloat("widget_transparency", settings.widgetTransparency)
            .putBoolean(AppConfig.Recording.PREF_CONTINUOUS_RECORDING, settings.continuousRecording)
            .putLong(AppConfig.Recording.PREF_MIN_DURATION_SECONDS, settings.minTrackDurationSeconds)
            .putLong(AppConfig.Recording.PREF_PRE_ROLL_SECONDS, settings.preRollSeconds)
            .putLong(AppConfig.Recording.PREF_POST_ROLL_SECONDS, settings.postRollSeconds)
            .apply()
    }

    fun findCuratedFavoriteMatch(
        backupFavorite: BackupFavorite,
        curatedStations: List<RadioStation>
    ): RadioStation? {
        val candidates = curatedStations.filter { !it.isUserStation }
        if (backupFavorite.stationId != null) {
            candidates.find { it.id == backupFavorite.stationId }?.let { return it }
        }
        if (backupFavorite.url.isNotBlank()) {
            candidates.find { it.url == backupFavorite.url }?.let { return it }
        }
        val targetName = backupFavorite.stationName
        if (!targetName.isNullOrBlank()) {
            candidates.find { it.name == targetName }?.let { return it }
        }
        return null
    }

    fun buildRestoredUserStations(
        customStations: List<BackupStation>,
        maxExistingId: Int?,
        uuidProvider: () -> String = { UUID.randomUUID().toString() }
    ): List<RadioStation> {
        val baseMax = maxExistingId ?: 9999
        var nextId = if (baseMax < 10000) 10000 else baseMax + 1
        return customStations.map { station ->
            val assignedId = nextId++
            RadioStation(
                id = assignedId,
                name = station.name,
                url = station.url,
                category = station.category,
                countryCode = station.countryCode,
                priority = 10000 + (assignedId - 10000),
                isFavorite = station.isFavorite,
                isUserStation = true,
                favoriteOrder = station.favoriteOrder,
                uuid = uuidProvider(),
                iconUrl = station.iconUrl
            )
        }
    }

    fun buildRestoredAlarms(alarms: List<BackupAlarm>): List<Alarm> {
        return alarms.map { alarm ->
            Alarm(
                hour = alarm.hour.coerceIn(0, 23),
                minute = alarm.minute.coerceIn(0, 59),
                days = alarm.days.filter { it in Calendar.SUNDAY..Calendar.SATURDAY }.toSet(),
                stationName = alarm.stationName,
                stationUrl = alarm.stationUrl,
                isEnabled = alarm.isEnabled,
                recordAudio = alarm.recordAudio
            )
        }
    }

    fun resolveSelectedStationAfterRestore(
        previousSelectedStation: RadioStation?,
        stationsAfterRestore: List<RadioStation>
    ): RadioStation? {
        if (previousSelectedStation == null) return null
        if (!previousSelectedStation.isUserStation) {
            stationsAfterRestore.find { !it.isUserStation && it.id == previousSelectedStation.id }?.let { return it }
        }
        stationsAfterRestore.find {
            it.url == previousSelectedStation.url && it.name == previousSelectedStation.name
        }?.let { return it }
        return stationsAfterRestore.find { it.url == previousSelectedStation.url }
    }
}
