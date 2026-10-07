package app.radiorecalarm.widget

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import app.radiorecalarm.AlarmUtils
import app.radiorecalarm.AppDatabase
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
        isPlaying: Boolean? = null
    ) {
        val prefs = context.getSharedPreferences("RaadioPrefs", Context.MODE_PRIVATE)
        val bgTransparency = prefs.getFloat("widget_transparency", 0.25f)

        val resolvedStationName = stationName ?: prefs.getString("last_name", context.getString(R.string.app_name)) ?: context.getString(R.string.app_name)
        val resolvedTitle = title ?: prefs.getString("last_title", "") ?: ""
        val resolvedArtist = artist ?: prefs.getString("last_artist", "") ?: ""
        val resolvedExtra = extra ?: ""
        val resolvedBitrate = bitrate ?: ""
        val resolvedIsPlaying = isPlaying ?: RadioService.isCurrentlyPlaying

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

        try {
            val manager = GlanceAppWidgetManager(context)
            val widget = HomeWidget()
            val glanceIds = manager.getGlanceIds(widget.javaClass)

            glanceIds.forEach { glanceId ->
                updateAppWidgetState(context, glanceId) { glancePrefs ->
                    glancePrefs[HomeWidget.Prefs.stationName] = resolvedStationName
                    glancePrefs[HomeWidget.Prefs.title] = resolvedTitle
                    glancePrefs[HomeWidget.Prefs.artist] = resolvedArtist
                    glancePrefs[HomeWidget.Prefs.bitrate] = resolvedBitrate
                    val statusText = if (resolvedIsPlaying) context.getString(R.string.status_playing) else context.getString(R.string.status_stopped)
                    val extraInfo = if (resolvedExtra.isNotBlank()) "$resolvedExtra • $statusText" else statusText
                    glancePrefs[HomeWidget.Prefs.status] = extraInfo
                    glancePrefs[HomeWidget.Prefs.alarm] = nextAlarmString
                    glancePrefs[HomeWidget.Prefs.isPlaying] = resolvedIsPlaying
                    glancePrefs[HomeWidget.Prefs.bgTransparency] = bgTransparency
                }
                widget.update(context, glanceId)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Vidinat ei saanud uuendada: ${e.message}")
        }
    }
}
