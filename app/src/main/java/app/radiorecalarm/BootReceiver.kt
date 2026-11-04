package app.radiorecalarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Taastab järgmise äratuse süsteemses AlarmManageris pärast sündmusi, mis muudavad
 * olemasoleva registreeringu kehtetuks või kaotavad selle:
 *  - seadme taaskäivitus (BOOT_COMPLETED),
 *  - äpi uuendamine (MY_PACKAGE_REPLACED),
 *  - süsteemikella käsitsi muutmine (TIME_SET),
 *  - ajavööndi / suve- ja talveaja vahetus (TIMEZONE_CHANGED).
 *
 * Kõik need on Android 8+ kaudsete broadcast'ide piirangutest vabastatud,
 * seega saab neid manifestis registreerida.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (!shouldReschedule(action)) return

        Log.d(TAG, "Taastame äratused (sündmus: $action)...")

        val pendingResult = goAsync()
        val scope = CoroutineScope(Dispatchers.IO)
        scope.launch {
            try {
                AlarmUtils.scheduleNextAlarmInternal(context)
                app.radiorecalarm.widget.HomeWidgetUpdater.update(context)
                Log.d(TAG, "Järgmine äratus edukalt taastatud (sündmus: $action).")
            } catch (e: Exception) {
                Log.e(TAG, "Viga äratuste taastamisel (sündmus: $action): ${e.message}", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "BootReceiver"

        /** Sündmused, mille korral tuleb järgmine äratus uuesti arvutada ja registreerida. */
        val RESCHEDULE_ACTIONS: Set<String> = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,     // "android.intent.action.TIME_SET"
            Intent.ACTION_TIMEZONE_CHANGED, // "android.intent.action.TIMEZONE_CHANGED"
        )

        fun shouldReschedule(action: String?): Boolean = action != null && action in RESCHEDULE_ACTIONS
    }
}
