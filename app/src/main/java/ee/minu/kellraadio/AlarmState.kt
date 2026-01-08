package ee.minu.kellraadio

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

object AlarmState {
    private const val PREFS_NAME = "KellraadioPrefs"

    private const val KEY_ALARM_TIME = "alarm_time_millis"
    private const val KEY_ALARM_HOUR = "alarm_hour"
    private const val KEY_ALARM_MINUTE = "alarm_minute"
    private const val KEY_ALARM_DAYS_CSV = "alarm_days_csv"
    private const val KEY_ALARM_STATION_NAME = "alarm_station_name"
    private const val KEY_ALARM_STATION_URL = "alarm_station_url"

    private const val TAG = "DEBUG_ALARM_STATE"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun saveAlarm(
        context: Context,
        nextAlarmTimeMillis: Long,
        hour: Int,
        minute: Int,
        days: Set<Int>,
        stationName: String,
        stationUrl: String
    ) {
        //Log.e(TAG, "KES KUTSUS SAVE ALARMI?", Exception("Stack Trace"))
        val daysString = days.joinToString(",")
        Log.d(TAG, "SALVESTAN: Kell $hour:$minute, Päevad(CSV): '$daysString', Nimi: $stationName")

        val success = getPrefs(context).edit()
            .putLong(KEY_ALARM_TIME, nextAlarmTimeMillis)
            .putInt(KEY_ALARM_HOUR, hour)
            .putInt(KEY_ALARM_MINUTE, minute)
            .putString(KEY_ALARM_DAYS_CSV, daysString)
            .putString(KEY_ALARM_STATION_NAME, stationName)
            .putString(KEY_ALARM_STATION_URL, stationUrl)
            .commit() // KASUTAME COMMIT(), et sundida kohest salvestust kettale

        Log.d(TAG, "Salvestamine õnnestus: $success")
    }

    fun getAlarmTime(context: Context): Long {
        return getPrefs(context).getLong(KEY_ALARM_TIME, 0L)
    }

    fun getAlarmHour(context: Context): Int = getPrefs(context).getInt(KEY_ALARM_HOUR, -1)
    fun getAlarmMinute(context: Context): Int = getPrefs(context).getInt(KEY_ALARM_MINUTE, -1)

    fun getAlarmDays(context: Context): Set<Int> {
        val rawString = getPrefs(context).getString(KEY_ALARM_DAYS_CSV, "") ?: ""
        Log.d(TAG, "LOEN PÄEVI: Toorestring on '$rawString'")

        if (rawString.isBlank()) return emptySet()

        return try {
            val result = rawString.split(",").mapNotNull { it.toIntOrNull() }.toSet()
            Log.d(TAG, "LOEN PÄEVI: Parsitud tulemus on $result")
            result
        } catch (e: Exception) {
            Log.e(TAG, "Viga päevade lugemisel: ${e.message}")
            emptySet()
        }
    }

    fun getAlarmStationName(context: Context): String {
        return getPrefs(context).getString(KEY_ALARM_STATION_NAME, "") ?: ""
    }

    fun getAlarmStationUrl(context: Context): String {
        return getPrefs(context).getString(KEY_ALARM_STATION_URL, "") ?: ""
    }

    fun clearAlarm(context: Context) {
        Log.d(TAG, "KUSTUTAN ÄRATUSE ANDMED")
        getPrefs(context).edit()
            .remove(KEY_ALARM_TIME)
            .remove(KEY_ALARM_HOUR)
            .remove(KEY_ALARM_MINUTE)
            .remove(KEY_ALARM_DAYS_CSV)
            .remove(KEY_ALARM_STATION_NAME)
            .commit() // Ka siin commit
    }
}