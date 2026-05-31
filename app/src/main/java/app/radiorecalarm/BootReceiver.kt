package app.radiorecalarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Kontrollime, kas tegemist on süsteemi käivitumise sündmusega
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("BootReceiver", "Telefon käivitus, taastame äratused...")

            // Kasutame Coroutine'i, et lugeda andmebaasist taustal
            val scope = CoroutineScope(Dispatchers.IO)
            scope.launch {
                try {
                    val db = AppDatabase.getDatabase(context)
                    // Võtame kõik äratused, mis on sisse lülitatud
                    val enabledAlarms = db.alarmDao().getAllEnabledAlarms()

                    enabledAlarms.forEach { alarm ->
                        // Igale äratusele arvutame uue järgmise käivitusaja ja seadistame AlarmManageris
                        AlarmUtils.reScheduleRepeatingAlarm(context, alarm)
                        Log.d("BootReceiver", "Äratus taastatud: ${alarm.hour}:${alarm.minute} (ID: ${alarm.id})")
                    }
                } catch (e: Exception) {
                    Log.e("BootReceiver", "Viga äratuste taastamisel: ${e.message}")
                }
            }
        }
    }
}
