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
     * Käivitab äratuse elutsükli tõrkekindlalt (ADR-0002):
     * 1. Kontrollib alarmId kehtivust
     * 2. Loeb andmebaasist jaama andmed
     * 3. Käivitab raadioteenuse äratuse parameetritega (tõrge logitakse, ei katkesta ahelat)
     * 4. Deaktiveerib ühekordse äratuse andmebaasis sõltumata esituse õnnestumisest
     * 5. Planeerib järgmise äratuse ja uuendab vidina/UI sõltumata esituse õnnestumisest
     * 6. Tagab WakeLocki vabastamise finally plokis
     *
     * Iga samm on isoleeritud, et ühe sammu tõrge ei takistaks järgmisi. Erandeid välja ei visata.
     */
    suspend fun processAlarm(alarmId: Int) {
        try {
            if (alarmId == -1) {
                Log.e(TAG, "ALARM_ID puudub või on vigane (-1).")
                return
            }

            val alarm = try {
                alarmDao.getAlarmById(alarmId)
            } catch (e: Exception) {
                Log.e(TAG, "Viga äratuse (ID: $alarmId) lugemisel andmebaasist: ${e.message}", e)
                null
            }

            if (alarm == null) {
                Log.e(TAG, "Andmebaasist ei leitud äratust ID-ga $alarmId")
            } else {
                // 1. Käivita raadioteenus; tõrge ei tohi katkestada äratuste ahelat
                try {
                    playbackLauncher.launchPlaybackService(alarm.stationUrl, alarm.stationName, alarm.recordAudio)
                } catch (e: Exception) {
                    Log.e(TAG, "Raadioteenuse käivitamine ebaõnnestus (ID: $alarmId): ${e.message}", e)
                }

                // 2. Deaktiveeri ühekordne äratus, et vigane äratus homseks ei korduks
                if (alarm.days.isEmpty()) {
                    try {
                        alarmDao.update(alarm.copy(isEnabled = false))
                        Log.d(TAG, "Ühekordne äratus (ID: $alarmId) deaktiveeritud.")
                    } catch (e: Exception) {
                        Log.e(TAG, "Ühekordse äratuse (ID: $alarmId) deaktiveerimine ebaõnnestus: ${e.message}", e)
                    }
                }
            }

            // 3. Planeeri järgmine äratus ja teavita komponente (ka siis, kui äratust ei leitud)
            try {
                onAlarmRescheduled?.invoke()
            } catch (e: Exception) {
                Log.e(TAG, "Järgmise äratuse ajastamine ebaõnnestus: ${e.message}", e)
            }
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
