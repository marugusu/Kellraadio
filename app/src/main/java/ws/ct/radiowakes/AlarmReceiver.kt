package ws.ct.radiowakes

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d("AlarmReceiver", "Äratus käivitus!")

        // 1. Hangi äratuse ID intent'ist. Kui seda pole, ei saa midagi teha.
        val alarmId = intent.getIntExtra("ALARM_ID", -1)
        if (alarmId == -1) {
            Log.e("AlarmReceiver", "ALARM_ID puudub, ei saa jätkata.")
            return
        }

        // 2. Käivita Coroutine, et teha andmebaasi päring taustalõimes
        val scope = CoroutineScope(Dispatchers.IO)
        scope.launch {
            val db = AppDatabase.getDatabase(context)
            val alarm = db.alarmDao().getAlarmById(alarmId)

            if (alarm == null) {
                Log.e("AlarmReceiver", "Andmebaasist ei leitud äratust ID-ga $alarmId")
                return@launch
            }

            // 3. Kontrolli, kas äratus on korduv või ühekordne
            if (alarm.days.isNotEmpty()) {
                // KORDUV: Seadistame kohe järgmise äratuse taustal sama ID-ga
                AlarmUtils.reScheduleRepeatingAlarm(context, alarm)
            } else {
                // ÜHEKORDNE: Märgime äratuse andmebaasis mitteaktiivseks
                val updatedAlarm = alarm.copy(isEnabled = false)
                db.alarmDao().update(updatedAlarm)
                Log.d("AlarmReceiver", "Ühekordne äratus (ID: $alarmId) deaktiveeritud.")
            }

            // 4. Teavita UI-d (kui see on avatud), et see saaks oma olekut uuendada
            val uiUpdateIntent = Intent("ws.ct.radiowakes.ALARMS_CHANGED")
            LocalBroadcastManager.getInstance(context).sendBroadcast(uiUpdateIntent)

            // 5. Käivita raadio (Foreground Service)
            // Kasutame andmeid otse 'alarm' objektist, mis on alati ajakohane.
            val serviceIntent = Intent(context, RadioService::class.java).apply {
                putExtra("STREAM_URL", alarm.stationUrl)
                putExtra("STATION_NAME", alarm.stationName)
                putExtra("TRIGGERED_BY", "ALARM")
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        }
    }
}