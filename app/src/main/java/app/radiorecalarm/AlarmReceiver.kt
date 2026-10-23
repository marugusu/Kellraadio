package app.radiorecalarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmReceiver(
    private val coordinatorFactory: ((Context, WakeLockHandle) -> AlarmTriggerCoordinator)? = null
) : BroadcastReceiver() {

    // Sekundaarne 0-parameetriga konstruktor Androidi süsteemse komponendi loomiseks
    constructor() : this(null)

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Äratus käivitus!")

        // 1. Haara koheselt osaline protsessori ärkvelolekulukk (PARTIAL_WAKE_LOCK)
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val rawWakeLock = powerManager?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            AppConfig.Alarm.WAKELOCK_TAG
        )?.apply {
            setReferenceCounted(false)
        }
        val wakeLockHandle: WakeLockHandle = rawWakeLock?.let { AndroidWakeLockHandle(it) } ?: NoOpWakeLockHandle

        val coordinator = coordinatorFactory?.invoke(context, wakeLockHandle) ?: run {
            val db = AppDatabase.getDatabase(context)
            val launcher = AndroidAlarmPlaybackLauncher(context)
            AlarmTriggerCoordinator(
                wakeLock = wakeLockHandle,
                alarmDao = db.alarmDao(),
                playbackLauncher = launcher,
                onAlarmRescheduled = {
                    AlarmUtils.scheduleNextAlarmInternal(context)
                    app.radiorecalarm.widget.HomeWidgetUpdater.update(context)
                    val uiUpdateIntent = Intent("app.radiorecalarm.ALARMS_CHANGED")
                    LocalBroadcastManager.getInstance(context).sendBroadcast(uiUpdateIntent)
                }
            )
        }

        // Haara WakeLock turvalimiidiga enne asünkroonse korutiini käivitamist
        coordinator.acquireWakeLock()

        // 2. Hangi äratuse ID intent'ist
        val alarmId = intent.getIntExtra("ALARM_ID", -1)
        if (alarmId == -1) {
            Log.e(TAG, "ALARM_ID puudub, vabastame luku ja lõpetame.")
            if (wakeLockHandle.isHeld) {
                wakeLockHandle.release()
            }
            return
        }

        // 3. Käivita asünkroonne töö goAsync abil, hoides WakeLocki kuni teenuse käivituseni
        val pendingResult = try {
            goAsync()
        } catch (e: Exception) {
            null
        }

        val scope = CoroutineScope(Dispatchers.IO)
        scope.launch {
            try {
                coordinator.processAlarm(alarmId)
            } catch (e: Exception) {
                Log.e(TAG, "Viga äratuse käivitamisel: ${e.message}", e)
            } finally {
                pendingResult?.finish()
            }
        }
    }

    companion object {
        private const val TAG = "AlarmReceiver"
    }
}

