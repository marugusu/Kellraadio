package ee.minu.kellraadio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            Log.d("BootReceiver", "Telefon käivitus, taastan äratused...")

            // Käivitame Coroutine'i, et teha andmebaasi päring taustalõimes.
            // goAsync() on vajalik, et Android ei tapaks meie BroadcastReceiver'it ära enne,
            // kui asünkroonne töö (andmebaasi lugemine) on lõppenud.
            val pendingResult: PendingResult = goAsync()
            val scope = CoroutineScope(Dispatchers.IO)

            scope.launch {
                try {
                    val db = AppDatabase.getDatabase(context)
                    // 1. Hangi andmebaasist KÕIK äratused, mis on aktiivsed.
                    val enabledAlarms = db.alarmDao().getAllEnabledAlarms()

                    if (enabledAlarms.isEmpty()) {
                        Log.d("BootReceiver", "Aktiivseid äratusi ei leitud, midagi ei taastata.")
                    } else {
                        Log.d("BootReceiver", "Leiti ${enabledAlarms.size} aktiivset äratust. Taastan need.")
                        // 2. Käi kõik aktiivsed äratused läbi ja seadista need uuesti.
                        enabledAlarms.forEach { alarm ->
                            val timeInMillis = AlarmUtils.findNextAlarmTime(alarm.hour, alarm.minute, alarm.days)
                            // Kasutame privaatset 'setSystemAlarm' funktsiooni, kuna me ei taha siin
                            // andmebaasi uuesti kirjutada. 'showToast' vms pole ka vaja.
                            // Selleks peame setSystemAlarm muutma 'internal' nähtavusega.
                            // Teeme selle ajutiselt lihtsamaks ja kasutame olemasolevat loogikat.

                            // Kutsume siin sama loogikat, mis on AlarmUtils sees.
                            // See on natuke koodi dubleerimine, aga hoiab ära vajaduse
                            // AlarmUtils'i liiga keeruliseks ajada.

                            // Parandatud lähenemine: loome spetsiaalse funktsiooni AlarmUtils'isse.
                            AlarmUtils.saveOrUpdateAlarm(context, alarm, showToast = false)
                        }
                        Log.d("BootReceiver", "Kõik aktiivsed äratused taastatud.")
                    }
                } finally {
                    // 3. Anna süsteemile teada, et meie töö on valmis.
                    pendingResult.finish()
                }
            }
        }
    }
}