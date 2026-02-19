package ws.ct.radiow

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

object AlarmUtils {

    private const val TAG = "AlarmUtils"

    private fun setSystemAlarm(context: Context, alarm: Alarm, timeInMillis: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            Log.w(TAG, "Täpse äratuse seadmise luba puudub.")
            return
        }

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("ALARM_ID", alarm.id)
            putExtra("STREAM_URL", alarm.stationUrl)
            putExtra("STATION_NAME", alarm.stationName)
        }

        val pi = PendingIntent.getBroadcast(
            context,
            alarm.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val info = AlarmManager.AlarmClockInfo(
            timeInMillis,
            PendingIntent.getActivity(context, 0,
                Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        )
        alarmManager.setAlarmClock(info, pi)
    }

    private fun cancelSystemAlarm(context: Context, alarm: Alarm) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java)
        val pi = PendingIntent.getBroadcast(
            context,
            alarm.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pi)
    }

    fun saveOrUpdateAlarm(
        context: Context,
        alarmToSave: Alarm,
        showToast: Boolean = true
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            val db = AppDatabase.getDatabase(context)
            val alarmDao = db.alarmDao()

            if (alarmToSave.id == 0) {
                val newId = alarmDao.insert(alarmToSave)
                val newAlarm = alarmToSave.copy(id = newId.toInt())
                if (newAlarm.isEnabled) {
                    val timeInMillis = findNextAlarmTime(newAlarm.hour, newAlarm.minute, newAlarm.days)
                    setSystemAlarm(context, newAlarm, timeInMillis)
                }
            } else {
                alarmDao.update(alarmToSave)
                cancelSystemAlarm(context, alarmToSave)
                if (alarmToSave.isEnabled) {
                    val timeInMillis = findNextAlarmTime(alarmToSave.hour, alarmToSave.minute, alarmToSave.days)
                    setSystemAlarm(context, alarmToSave, timeInMillis)
                }
            }
            if (showToast) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.alarm_toast_saved),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    fun deleteAlarm(context: Context, alarm: Alarm) {
        CoroutineScope(Dispatchers.IO).launch {
            cancelSystemAlarm(context, alarm)
            val db = AppDatabase.getDatabase(context)
            db.alarmDao().delete(alarm)

            withContext(Dispatchers.Main) {
                Toast.makeText(
                    context,
                    context.getString(R.string.alarm_toast_deleted),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    fun reScheduleRepeatingAlarm(context: Context, alarm: Alarm) {
        val timeInMillis = findNextAlarmTime(alarm.hour, alarm.minute, alarm.days)
        setSystemAlarm(context, alarm, timeInMillis)
    }

    fun findNextAlarmTime(hour: Int, minute: Int, days: Set<Int>): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        if (days.isEmpty()) {
            if (target.timeInMillis <= now.timeInMillis) {
                target.add(Calendar.DAY_OF_YEAR, 1)
            }
            return target.timeInMillis
        }

        val sortedDays = days.sorted()

        val today = now.get(Calendar.DAY_OF_WEEK)

        for (i in 0..7) {
            val checkCal = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, i) }
            val dayOfWeek = checkCal.get(Calendar.DAY_OF_WEEK)

            if (days.contains(dayOfWeek)) {
                val potentialAlarmTime = Calendar.getInstance().apply {
                    timeInMillis = checkCal.timeInMillis
                    set(Calendar.HOUR_OF_DAY, hour)
                    set(Calendar.MINUTE, minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }

                if (potentialAlarmTime.timeInMillis > now.timeInMillis) {
                    return potentialAlarmTime.timeInMillis
                }
            }
        }
        return target.apply { add(Calendar.DAY_OF_YEAR, 7) }.timeInMillis
    }

    fun getAlarmText(context: Context, hour: Int, minute: Int, days: Set<Int>): String {
        val timeStr = String.format("%02d:%02d", hour, minute)
        if (days.isEmpty()) return "$timeStr (${context.getString(R.string.alarm_once)})"

        val workDays = setOf(Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY)
        val weekend = setOf(Calendar.SATURDAY, Calendar.SUNDAY)
        val allDays = workDays + weekend

        val dayStr = when (days) {
            allDays -> context.getString(R.string.days_every_day)
            workDays -> context.getString(R.string.days_weekdays)
            weekend -> context.getString(R.string.days_weekend)
            else -> {
                days.sortedBy { if (it == Calendar.SUNDAY) 8 else it }
                    .joinToString(" ") { dayId ->
                        when (dayId) {
                            Calendar.MONDAY -> context.getString(R.string.day_mon)
                            Calendar.TUESDAY -> context.getString(R.string.day_tue)
                            Calendar.WEDNESDAY -> context.getString(R.string.day_wed)
                            Calendar.THURSDAY -> context.getString(R.string.day_thu)
                            Calendar.FRIDAY -> context.getString(R.string.day_fri)
                            Calendar.SATURDAY -> context.getString(R.string.day_sat)
                            Calendar.SUNDAY -> context.getString(R.string.day_sun)
                            else -> ""
                        }
                    }
            }
        }
        return "$timeStr • $dayStr"
    }
}