package app.radiorecalarm

import android.util.Log

/**
 * Koordinaator, mis juhib äratuse käivitumise elutsüklit unerežiimis:
 * - Tagab protsessori ärkveloleku (WakeLock turvalimiidiga 30s)
 * - Pärib andmebaasist jaama info
 * - Käivitab meediateenuse äratuse parameetritega (TRIGGERED_BY = "ALARM")
 * - Tagab WakeLocki vabastamise lõpetamisel või tõrke korral (finally plokis)
 */
class AlarmTriggerCoordinator(
    private val wakeLock: WakeLockHandle,
    private val alarmDao: AlarmDao,
    private val playbackLauncher: AlarmPlaybackLauncher,
    private val onAlarmRescheduled: (suspend () -> Unit)? = null
) {
    /**
     * Haarab koheselt osalise WakeLocki turvalimiidiga enne asünkroonse töö alustamist.
     */
    fun acquireWakeLock(timeoutMs: Long = AppConfig.Alarm.WAKELOCK_TIMEOUT_MS) {
        wakeLock.acquire(timeoutMs)
    }

    /**
     * Käivitab äratuse elutsükli:
     * 1. Kontrollib alarmId kehtivust
     * 2. Loeb andmebaasist jaama andmed
     * 3. Uuendab ühekordse äratuse staatust ja planeerib järgmise äratuse
     * 4. Käivitab raadioteenuse äratuse parameetritega
     * 5. Tagab WakeLocki vabastamise finally plokis
     */
    suspend fun processAlarm(alarmId: Int) {
        try {
            if (alarmId == -1) {
                Log.e(TAG, "ALARM_ID puudub või on vigane (-1).")
                return
            }

            val alarm = alarmDao.getAlarmById(alarmId)
            if (alarm == null) {
                Log.e(TAG, "Andmebaasist ei leitud äratust ID-ga $alarmId")
                return
            }

            if (alarm.days.isEmpty()) {
                val updatedAlarm = alarm.copy(isEnabled = false)
                alarmDao.update(updatedAlarm)
                Log.d(TAG, "Ühekordne äratus (ID: $alarmId) deaktiveeritud.")
            }

            onAlarmRescheduled?.invoke()

            playbackLauncher.launchPlaybackService(alarm.stationUrl, alarm.stationName)
        } catch (e: Exception) {
            Log.e(TAG, "Viga äratuse töötlemisel: ${e.message}", e)
            throw e
        } finally {
            try {
                if (wakeLock.isHeld) {
                    wakeLock.release()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Viga WakeLocki vabastamisel: ${e.message}", e)
            }
        }
    }

    companion object {
        private const val TAG = "AlarmTriggerCoordinator"
    }
}
