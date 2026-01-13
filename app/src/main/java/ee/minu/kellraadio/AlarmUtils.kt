package ee.minu.kellraadio

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

// Muudame selle klassi objektist tavaliseks klassiks, et saaksime konstruktoris
// andmebaasi kätte anda. Kuid lihtsuse huvides hoiame staatilist lähenemist ja
// loome andmebaasi ühenduse vajadusel. Hoiame selle siiski objektina, et mitte
// tervet äppi katki teha.

object AlarmUtils {

    private const val TAG = "AlarmUtils"

    /**
     * Seab süsteemi füüsilise äratuse AlarmManageris.
     * See funktsioon ei tegele andmebaasi salvestamisega, vaid ainult süsteemile käsu andmisega.
     * Vajalik on äratuse unikaalne ID.
     */
    private fun setSystemAlarm(context: Context, alarm: Alarm, timeInMillis: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // Android 12+ täpse äratuse loa kontroll
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            Log.w(TAG, "Täpse äratuse seadmise luba puudub.")
            // Rakendus peab olema esiplaanil, et seadete aken avada.
            // context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
            //     flags = Intent.FLAG_ACTIVITY_NEW_TASK
            // })
            return
        }

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            // Lisame ID, et AlarmReceiver teaks, milline äratus käivitus
            putExtra("ALARM_ID", alarm.id)
            putExtra("STREAM_URL", alarm.stationUrl)
            putExtra("STATION_NAME", alarm.stationName)
        }

        // Kasutame äratuse ID-d, et luua unikaalne PendingIntent. See on mitme äratuse võti!
        val pi = PendingIntent.getBroadcast(
            context,
            alarm.id, // Unikaalne ID
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Seame süsteemi äratuse
        val info = AlarmManager.AlarmClockInfo(
            timeInMillis,
            PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        )
        alarmManager.setAlarmClock(info, pi)
        Log.d(TAG, "Süsteemi äratus (ID: ${alarm.id}) seatud ajale ${timeInMillis}")
    }

    /**
     * Tühistab süsteemi füüsilise äratuse AlarmManageris.
     */
    private fun cancelSystemAlarm(context: Context, alarm: Alarm) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java)
        val pi = PendingIntent.getBroadcast(
            context,
            alarm.id, // Kasutame sama ID-d, et tühistada õige äratus
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pi)
        Log.d(TAG, "Süsteemi äratus (ID: ${alarm.id}) tühistatud.")
    }

    /**
     * UUS PEAMINE FUNKTSIOON ÄRATUSE LISAMISEKS VÕI MUUTMISEKS.
     * Salvestab äratuse andmebaasi ja kui see on aktiivne, seab ka süsteemi äratuse.
     */
    fun saveOrUpdateAlarm(
        context: Context,
        alarmToSave: Alarm,
        showToast: Boolean = true
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            val db = AppDatabase.getDatabase(context)
            val alarmDao = db.alarmDao()

            // Kui 'id' on 0, siis see on uus äratus. Insert tagastab uue ID.
            if (alarmToSave.id == 0) {
                val newId = alarmDao.insert(alarmToSave)
                val newAlarm = alarmToSave.copy(id = newId.toInt())
                // Kui äratus on aktiivne, seame selle ka süsteemis
                if (newAlarm.isEnabled) {
                    val timeInMillis = findNextAlarmTime(newAlarm.hour, newAlarm.minute, newAlarm.days)
                    setSystemAlarm(context, newAlarm, timeInMillis)
                }
            } else {
                // Tegemist on olemasoleva äratuse uuendamisega
                alarmDao.update(alarmToSave)
                // Kõigepealt tühistame vana süsteemi äratuse, et vältida duplikaate
                cancelSystemAlarm(context, alarmToSave)
                // Kui see on (uuesti) sisse lülitatud, seame uue äratuse
                if (alarmToSave.isEnabled) {
                    val timeInMillis = findNextAlarmTime(alarmToSave.hour, alarmToSave.minute, alarmToSave.days)
                    setSystemAlarm(context, alarmToSave, timeInMillis)
                }
            }
            if (showToast) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Äratus salvestatud!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }


    /**
     * UUS FUNKTSIOON ÄRATUSE KUSTUTAMISEKS.
     * Tühistab süsteemi äratuse ja kustutab kirje andmebaasist.
     */
    fun deleteAlarm(context: Context, alarm: Alarm) {
        CoroutineScope(Dispatchers.IO).launch {
            // 1. Tühistame AlarmManageris
            cancelSystemAlarm(context, alarm)

            // 2. Kustutame andmebaasist
            val db = AppDatabase.getDatabase(context)
            db.alarmDao().delete(alarm)

            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Äratus kustutatud!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * See funktsioon on mõeldud korduvate äratuste uuesti seadmiseks,
     * kui üks on just helisenud.
     */
    fun reScheduleRepeatingAlarm(context: Context, alarm: Alarm) {
        val timeInMillis = findNextAlarmTime(alarm.hour, alarm.minute, alarm.days)
        setSystemAlarm(context, alarm, timeInMillis)
        Log.d(TAG, "Korduv äratus (ID: ${alarm.id}) uuesti ajastatud.")
    }

    /**
     * Arvutab järgmise äratuse aja millisekundites.
     * Loogika on sama, mis enne.
     */
    fun findNextAlarmTime(hour: Int, minute: Int, days: Set<Int>): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        // Kui on ühekordne äratus (days on tühi)
        if (days.isEmpty()) {
            // Kui äratuse aeg on täna juba möödas, pane see homseks
            if (target.timeInMillis <= now.timeInMillis) {
                target.add(Calendar.DAY_OF_YEAR, 1)
            }
            return target.timeInMillis
        }

        // Kui on korduv äratus, otsime järgmist sobivat päeva
        val sortedDays = days.sorted() // E-P -> 2,3,4,5,6,7,1

        // Alustame tänasest päevast
        val today = now.get(Calendar.DAY_OF_WEEK)

        // Otsime järgmist sobivat päeva
        for (i in 0..7) {
            val checkCal = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, i) }
            val dayOfWeek = checkCal.get(Calendar.DAY_OF_WEEK)

            if (days.contains(dayOfWeek)) {
                // Leidsime sobiva päeva. Kontrollime, kas kellaaeg on juba möödas.
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
        // Fallback: kui midagi ei leita, tagastab aja nädala pärast
        return target.apply { add(Calendar.DAY_OF_YEAR, 7) }.timeInMillis
    }

    // See abifunktsioon jääb samaks, et UI-s ilusaid tekste kuvada.
    fun getAlarmText(hour: Int, minute: Int, days: Set<Int>): String {
        val timeStr = String.format("%02d:%02d", hour, minute)
        if (days.isEmpty()) return "$timeStr (Ühekordne)"

        val workDays = setOf(Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY)
        val weekend = setOf(Calendar.SATURDAY, Calendar.SUNDAY)
        val allDays = workDays + weekend

        val dayStr = when (days) {
            allDays -> "Iga päev"
            workDays -> "E-R"
            weekend -> "L-P"
            else -> {
                // Sorteerime nii, et esmaspäev on esimene
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
}