package app.radiorecalarm.widget

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import app.radiorecalarm.AlarmUtils
import app.radiorecalarm.AppDatabase
import app.radiorecalarm.PlaybackStatusSync
import app.radiorecalarm.R
import app.radiorecalarm.RadioService

object HomeWidgetUpdater {
    private const val TAG = "HomeWidgetUpdater"

    suspend fun update(
        context: Context,
        stationName: String? = null,
        title: String? = null,
        artist: String? = null,
        extra: String? = null,
        bitrate: String? = null,
        isPlaying: Boolean? = null,
        isRecording: Boolean? = null,
        isLocalStream: Boolean? = null,
        streamUrl: String? = null
    ) {
        val prefs = context.getSharedPreferences("RaadioPrefs", Context.MODE_PRIVATE)
        val bgTransparency = prefs.getFloat("widget_transparency", 0.25f)

        val resolvedStationName = stationName ?: prefs.getString("last_name", context.getString(R.string.app_name)) ?: context.getString(R.string.app_name)
        val resolvedTitle = title ?: prefs.getString("last_title", "") ?: ""
        val resolvedArtist = artist ?: prefs.getString("last_artist", "") ?: ""
        val resolvedExtra = extra ?: ""
        val resolvedBitrate = bitrate ?: ""
        val resolvedIsPlaying = isPlaying ?: RadioService.isCurrentlyPlaying
        val resolvedIsRecording = isRecording ?: RadioService.isCurrentlyRecording
        val resolvedStreamUrl = streamUrl
            ?: RadioService.currentActiveStreamUrl.ifBlank { prefs.getString("last_url", "") ?: "" }
        val resolvedIsLocal = isLocalStream ?: PlaybackStatusSync.isLocalStreamUri(resolvedStreamUrl)

        var nextAlarmString = ""
        try {
            val db = AppDatabase.getDatabase(context)
            val enabledAlarms = db.alarmDao().getAllEnabledAlarms()
            if (enabledAlarms.isNotEmpty()) {
                val nextAlarm = enabledAlarms.map {
                    it to AlarmUtils.findNextAlarmTime(it.hour, it.minute, it.days)
                }.minByOrNull { it.second }

                if (nextAlarm != null) {
                    val timeAndDays = AlarmUtils.getAlarmText(context, nextAlarm.first.hour, nextAlarm.first.minute, nextAlarm.first.days)
                    nextAlarmString = "$timeAndDays • ${nextAlarm.first.stationName}"
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Äratuse lugemise viga vidina jaoks: ${e.message}")
        }

        val statusText = if (resolvedIsPlaying) {
            context.getString(R.string.status_playing)
        } else {
            context.getString(R.string.status_stopped)
        }

        try {
            val manager = GlanceAppWidgetManager(context)
            val homeWidget = HomeWidget()
            val homeGlanceIds = manager.getGlanceIds(homeWidget.javaClass)

            homeGlanceIds.forEach { glanceId ->
                updateAppWidgetState(context, glanceId) { glancePrefs ->
                    glancePrefs[HomeWidget.Prefs.stationName] = resolvedStationName
                    glancePrefs[HomeWidget.Prefs.title] = resolvedTitle
                    glancePrefs[HomeWidget.Prefs.artist] = resolvedArtist
                    glancePrefs[HomeWidget.Prefs.bitrate] = resolvedBitrate
                    val extraInfo = if (resolvedExtra.isNotBlank()) "$resolvedExtra • $statusText" else statusText
                    glancePrefs[HomeWidget.Prefs.status] = extraInfo
                    glancePrefs[HomeWidget.Prefs.alarm] = nextAlarmString
                    glancePrefs[HomeWidget.Prefs.isPlaying] = resolvedIsPlaying
                    glancePrefs[HomeWidget.Prefs.bgTransparency] = bgTransparency
                }
                homeWidget.update(context, glanceId)
            }

            val compactWidget = CompactWidget()
            val compactGlanceIds = manager.getGlanceIds(compactWidget.javaClass)
            if (compactGlanceIds.isNotEmpty()) {
                val artworkKey = WidgetArtworkCache.prepareArtwork(
                    context = context,
                    stationName = resolvedStationName,
                    streamUrl = resolvedStreamUrl,
                    isLocalStream = resolvedIsLocal
                )
                compactGlanceIds.forEach { glanceId ->
                    updateAppWidgetState(context, glanceId) { glancePrefs ->
                        glancePrefs[CompactWidget.Prefs.stationName] = resolvedStationName
                        glancePrefs[CompactWidget.Prefs.title] = resolvedTitle
                        glancePrefs[CompactWidget.Prefs.artist] = resolvedArtist
                        glancePrefs[CompactWidget.Prefs.extra] = resolvedExtra
                        glancePrefs[CompactWidget.Prefs.status] = statusText
                        glancePrefs[CompactWidget.Prefs.bitrate] = resolvedBitrate
                        glancePrefs[CompactWidget.Prefs.alarm] = nextAlarmString
                        glancePrefs[CompactWidget.Prefs.isPlaying] = resolvedIsPlaying
                        glancePrefs[CompactWidget.Prefs.isRecording] = resolvedIsRecording
                        glancePrefs[CompactWidget.Prefs.isLocalStream] = resolvedIsLocal
                        glancePrefs[CompactWidget.Prefs.bgTransparency] = bgTransparency
                        glancePrefs[CompactWidget.Prefs.artworkKey] = artworkKey
                    }
                    compactWidget.update(context, glanceId)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Vidinat ei saanud uuendada: ${e.message}")
        }
    }
}
