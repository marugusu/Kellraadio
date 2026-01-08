package ee.minu.kellraadio

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.core.app.NotificationCompat
import java.util.Calendar

object AlarmUtils {

    /**
     * Seab äratuse, arvutab aja, salvestab oleku ja vajadusel näitab teavitust.
     * See on universaalne funktsioon, mida kasutavad nii MainActivity, BootReceiver kui AlarmReceiver.
     */
    fun setAlarm(
        context: Context,
        hour: Int,
        minute: Int,
        stationName: String,
        stationUrl: String,
        days: Set<Int> = emptySet(),
        showNotification: Boolean = true
    ): Long? {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // Android 12+ täpse äratuse loa kontroll
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            // Kui on UI tegevus (showNotification = true), siis suuname seadetesse.
            // Taustal (Boot) ei saa seda teha.
            if (showNotification) {
                context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                })
            }
            return null
        }

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("STREAM_URL", stationUrl)
            putExtra("STATION_NAME", stationName)
            putExtra("TRIGGERED_BY", "ALARM")
        }

        val pi = PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // ARVUTAME AJA (Siia toodud loogika)
        val timeInMillis = findNextAlarmTime(hour, minute, days)

        // Salvestame
        AlarmState.saveAlarm(context, timeInMillis, hour, minute, days, stationName, stationUrl)

        // Seame süsteemi äratuse
        val info = AlarmManager.AlarmClockInfo(
            timeInMillis,
            PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        )
        alarmManager.setAlarmClock(info, pi)

        if (showNotification) {
            Toast.makeText(context, "Äratus seatud!", Toast.LENGTH_SHORT).show()
            val timeStr = String.format("%02d:%02d", hour, minute)
            val dayStr = if (days.isEmpty()) "" else " (Korduv)"
            sendAlarmNotification(context, "$timeStr$dayStr", stationName)
        }

        return timeInMillis
    }

    // Ülekoormus (Overload) funktsioon MainActivity jaoks (kasutab RadioStation objekti)
    fun setAlarm(context: Context, hour: Int, minute: Int, station: RadioStation, days: Set<Int> = emptySet()): Long? {
        return setAlarm(context, hour, minute, station.name, station.url, days, true)
    }

    fun cancelAlarm(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = PendingIntent.getBroadcast(
            context, 0, Intent(context, AlarmReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        am.cancel(pi)
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(2)
        AlarmState.clearAlarm(context)
        Toast.makeText(context, "Äratus tühistatud!", Toast.LENGTH_SHORT).show()
    }

    /**
     * Arvutab järgmise äratuse aja.
     * Kui days on tühi -> ühekordne (täna või homme).
     * Kui days pole tühi -> leiab järgmise sobiva nädalapäeva.
     */
    private fun findNextAlarmTime(hour: Int, minute: Int, days: Set<Int>): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        // Kui on ühekordne äratus (days tühi)
        if (days.isEmpty()) {
            if (target.timeInMillis <= now.timeInMillis) {
                target.add(Calendar.DAY_OF_YEAR, 1) // Homme
            }
            return target.timeInMillis
        }

        // Kui on korduv äratus
        for (i in 0..7) {
            if (i == 0 && target.timeInMillis <= now.timeInMillis) {
                target.add(Calendar.DAY_OF_YEAR, 1)
                continue
            }
            // Calendar.SUNDAY = 1 ... SATURDAY = 7
            val dayOfWeek = target.get(Calendar.DAY_OF_WEEK)
            if (days.contains(dayOfWeek)) {
                return target.timeInMillis
            }
            target.add(Calendar.DAY_OF_YEAR, 1)
        }
        return now.timeInMillis + 86400000L // Fallback
    }

    fun getAlarmText(timeInMillis: Long, days: Set<Int>): String {
        val cal = Calendar.getInstance().apply { this.timeInMillis = timeInMillis }
        val timeStr = String.format("%02d:%02d", cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
        if (days.isEmpty()) return timeStr

        val workDays = setOf(Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY)
        val weekend = setOf(Calendar.SATURDAY, Calendar.SUNDAY)
        val allDays = setOf(Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY)

        val dayStr = when (days) {
            allDays -> "Iga päev"
            workDays -> "E-R"
            weekend -> "L-P"
            else -> {
                days.sortedBy { if (it == Calendar.SUNDAY) 8 else it }
                    .joinToString(" ") { dayId ->
                        when (dayId) {
                            Calendar.MONDAY -> "E"; Calendar.TUESDAY -> "T"; Calendar.WEDNESDAY -> "K"
                            Calendar.THURSDAY -> "N"; Calendar.FRIDAY -> "R"; Calendar.SATURDAY -> "L"; Calendar.SUNDAY -> "P"
                            else -> ""
                        }
                    }
            }
        }
        return "$timeStr • $dayStr"
    }

    private fun sendAlarmNotification(context: Context, time: String, stationName: String) {
        val channelId = "ALARM_CONFIRMATION_CHANNEL_v21"
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(NotificationChannel(channelId, "Äratuse kinnitus", NotificationManager.IMPORTANCE_DEFAULT))
        }
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Äratus seatud: $time")
            .setContentText("Jaam: $stationName")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        manager.notify(2, notification)
    }
}