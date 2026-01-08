package ee.minu.kellraadio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            Log.d("BootReceiver", "Telefon käivitus, taastan äratuse...")

            val stationName = AlarmState.getAlarmStationName(context)
            val stationUrl = AlarmState.getAlarmStationUrl(context)
            val days = AlarmState.getAlarmDays(context)
            val storedHour = AlarmState.getAlarmHour(context)
            val storedMinute = AlarmState.getAlarmMinute(context)

            // Kontrollime, kas oli aktiivne äratus (kellaaeg on salvestatud)
            if (stationName.isNotEmpty() && storedHour != -1) {

                // Kasutame AlarmUtils-i, et taastada äratus täpselt samade parameetritega.
                // showNotification = false, sest me ei taha restardi ajal kasutajat segada.
                AlarmUtils.setAlarm(
                    context,
                    storedHour,
                    storedMinute,
                    stationName,
                    stationUrl,
                    days,
                    showNotification = false
                )

                Log.d("BootReceiver", "Äratus taastatud edukalt.")
            }
        }
    }
}