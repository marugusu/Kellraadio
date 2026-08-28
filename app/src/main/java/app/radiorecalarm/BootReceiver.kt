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
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            Log.d("BootReceiver", "Taastame äratused (sündmus: ${intent.action})...")

            val pendingResult = goAsync()
            val scope = CoroutineScope(Dispatchers.IO)
            scope.launch {
                try {
                    val db = AppDatabase.getDatabase(context)
                    val enabledAlarms = db.alarmDao().getAllEnabledAlarms()

                    enabledAlarms.forEach { alarm ->
                        AlarmUtils.reScheduleRepeatingAlarm(context, alarm)
                        Log.d("BootReceiver", "Äratus taastatud: ${alarm.hour}:${alarm.minute} (ID: ${alarm.id})")
                    }
                } catch (e: Exception) {
                    Log.e("BootReceiver", "Viga äratuste taastamisel: ${e.message}")
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
