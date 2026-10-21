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
                    AlarmUtils.scheduleNextAlarmInternal(context)
                    app.radiorecalarm.widget.HomeWidgetUpdater.update(context)
                    Log.d("BootReceiver", "Järgmine äratus edukalt taastatud.")
                } catch (e: Exception) {
                    Log.e("BootReceiver", "Viga äratuste taastamisel: ${e.message}")
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
