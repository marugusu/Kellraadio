package ee.minu.kellraadio

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d("AlarmReceiver", "Äratus käivitus!")

        // 1. Andmed
        val days = AlarmState.getAlarmDays(context)
        val storedHour = AlarmState.getAlarmHour(context)
        val storedMinute = AlarmState.getAlarmMinute(context)
        val stationName = intent.getStringExtra("STATION_NAME") ?: AlarmState.getAlarmStationName(context)
        val streamUrl = intent.getStringExtra("STREAM_URL") ?: AlarmState.getAlarmStationUrl(context)

        // 2. Korduv vs Ühekordne
        if (days.isNotEmpty() && storedHour != -1) {
            // KORDUV: Seadistame kohe järgmise äratuse taustal
            AlarmUtils.setAlarm(
                context,
                storedHour,
                storedMinute,
                stationName,
                streamUrl,
                days,
                showNotification = false // Ei taha teavitust, kui äratus alles heliseb
            )
        } else {
            // ÜHEKORDNE: Puhastame andmed
            AlarmState.clearAlarm(context)
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(2)
        }

        // 3. UI Uuendamine (kui äpp on lahti, eemaldab kella ikooni ühekordse puhul)
        val clearUiIntent = Intent("ee.minu.kellraadio.ALARM_TRIGGERED")
        LocalBroadcastManager.getInstance(context).sendBroadcast(clearUiIntent)

        // 4. Käivitame raadio (Foreground Service)
        val serviceIntent = Intent(context, RadioService::class.java).apply {
            putExtra("STREAM_URL", streamUrl)
            putExtra("STATION_NAME", stationName)
            putExtra("TRIGGERED_BY", "ALARM")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }
}