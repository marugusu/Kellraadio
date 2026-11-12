package app.radiorecalarm

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmTriggerCoordinatorTest {

    private class FakeWakeLockHandle : WakeLockHandle {
        var acquireCallCount = 0
        var releaseCallCount = 0
        var lastTimeout: Long = 0L
        private var held = false

        override val isHeld: Boolean get() = held

        override fun acquire(timeoutMs: Long) {
            acquireCallCount++
            lastTimeout = timeoutMs
            held = true
        }

        override fun release() {
            releaseCallCount++
            held = false
        }
    }

    private class FakeAlarmDao(
        initialAlarms: List<Alarm> = emptyList(),
        private val onUpdateCallback: (() -> Unit)? = null
    ) : AlarmDao {
        val alarms = initialAlarms.associateBy { it.id }.toMutableMap()
        val updatedAlarms = mutableListOf<Alarm>()

        override suspend fun insert(alarm: Alarm): Long {
            alarms[alarm.id] = alarm
            return alarm.id.toLong()
        }

        override suspend fun update(alarm: Alarm) {
            alarms[alarm.id] = alarm
            updatedAlarms.add(alarm)
            onUpdateCallback?.invoke()
        }

        override suspend fun delete(alarm: Alarm) {
            alarms.remove(alarm.id)
        }

        override fun getAllAlarms(): Flow<List<Alarm>> = emptyFlow()

        override suspend fun getAlarmById(id: Int): Alarm? = alarms[id]

        override suspend fun getAllEnabledAlarms(): List<Alarm> = alarms.values.filter { it.isEnabled }

        override suspend fun getAllAlarmsList(): List<Alarm> = alarms.values.toList()

        override suspend fun deleteAllAlarms() {
            alarms.clear()
        }

        override suspend fun findAlarmsForStation(stationName: String, stationUrl: String): List<Alarm> =
            alarms.values.filter { it.stationName == stationName || it.stationUrl == stationUrl }
    }

    private class FakePlaybackLauncher(
        private val onLaunchCallback: (() -> Unit)? = null
    ) : AlarmPlaybackLauncher {
        var launchedUrl: String? = null
        var launchedStation: String? = null
        var launchedRecordAudio: Boolean = false
        var shouldThrow = false

        override fun launchPlaybackService(streamUrl: String, stationName: String, recordAudio: Boolean) {
            onLaunchCallback?.invoke()
            if (shouldThrow) {
                throw RuntimeException("Simulated service start failure")
            }
            launchedUrl = streamUrl
            launchedStation = stationName
            launchedRecordAudio = recordAudio
        }
    }

    @Test
    fun testWakeLockAcquiredAndHeldUntilServiceStartsAndReleasedInFinally() = runBlocking {
        val fakeWakeLock = FakeWakeLockHandle()
        val testAlarm = Alarm(
            id = 42,
            hour = 7,
            minute = 30,
            days = setOf(2, 3), // Korduv äratus
            stationName = "Retro FM",
            stationUrl = "https://stream.retro.ee/retro",
            isEnabled = true
        )
        val fakeDao = FakeAlarmDao(listOf(testAlarm))
        val fakeLauncher = FakePlaybackLauncher()

        val coordinator = AlarmTriggerCoordinator(
            wakeLock = fakeWakeLock,
            alarmDao = fakeDao,
            playbackLauncher = fakeLauncher
        )

        // Enne käivitamist haaratakse WakeLock
        coordinator.acquireWakeLock()
        assertTrue("WakeLock peab olema haaratud", fakeWakeLock.isHeld)
        assertEquals(1, fakeWakeLock.acquireCallCount)
        assertEquals(AppConfig.Alarm.WAKELOCK_TIMEOUT_MS, fakeWakeLock.lastTimeout)

        // Käivitame äratuse töötlemise
        coordinator.processAlarm(42)

        // Pärast edukat käivitust peab teenus olema käivitatud ja WakeLock vabastatud
        assertEquals("Retro FM", fakeLauncher.launchedStation)
        assertEquals("https://stream.retro.ee/retro", fakeLauncher.launchedUrl)
        assertFalse("WakeLock peab olema vabastatud finally plokis", fakeWakeLock.isHeld)
        assertEquals(1, fakeWakeLock.releaseCallCount)
    }

    @Test
    fun testWakeLockReleasedInFinallyWhenServiceLaunchThrows() = runBlocking {
        val fakeWakeLock = FakeWakeLockHandle()
        val testAlarm = Alarm(
            id = 10,
            hour = 8,
            minute = 0,
            days = setOf(1),
            stationName = "Raadio 2",
            stationUrl = "https://stream.err.ee/r2",
            isEnabled = true
        )
        val fakeDao = FakeAlarmDao(listOf(testAlarm))
        val fakeLauncher = FakePlaybackLauncher().apply { shouldThrow = true }

        val coordinator = AlarmTriggerCoordinator(
            wakeLock = fakeWakeLock,
            alarmDao = fakeDao,
            playbackLauncher = fakeLauncher
        )

        coordinator.acquireWakeLock()
        assertTrue("WakeLock peab olema algselt haaratud", fakeWakeLock.isHeld)

        // Koordinaator käsitleb esitustõrke ise (logib), erand ei tohi ahelat katkestada
        coordinator.processAlarm(10)

        assertFalse("WakeLock peab olema vabastatud ka vea korral", fakeWakeLock.isHeld)
        assertEquals(1, fakeWakeLock.releaseCallCount)
    }

    @Test
    fun testOneTimeAlarmDeactivatedOnExecution() = runBlocking {
        val fakeWakeLock = FakeWakeLockHandle()
        val oneTimeAlarm = Alarm(
            id = 99,
            hour = 6,
            minute = 45,
            days = emptySet(), // Ühekordne äratus
            stationName = "Kuku",
            stationUrl = "https://stream.kuku.ee/kuku",
            isEnabled = true
        )
        val fakeDao = FakeAlarmDao(listOf(oneTimeAlarm))
        val fakeLauncher = FakePlaybackLauncher()

        val coordinator = AlarmTriggerCoordinator(
            wakeLock = fakeWakeLock,
            alarmDao = fakeDao,
            playbackLauncher = fakeLauncher
        )

        coordinator.acquireWakeLock()
        coordinator.processAlarm(99)

        val updated = fakeDao.updatedAlarms.firstOrNull { it.id == 99 }
        assertTrue("Ühekordne äratus peab olema andmebaasis uuendatud", updated != null)
        assertFalse("Ühekordne äratus peab olema deaktiveeritud (isEnabled = false)", updated!!.isEnabled)
        assertFalse(fakeWakeLock.isHeld)
    }

    @Test
    fun testRecurringAlarmKeepsIsEnabledTrue() = runBlocking {
        val fakeWakeLock = FakeWakeLockHandle()
        val recurringAlarm = Alarm(
            id = 55,
            hour = 7,
            minute = 0,
            days = setOf(1, 2, 3, 4, 5), // Tööpäevad
            stationName = "Vikerraadio",
            stationUrl = "https://stream.err.ee/viker",
            isEnabled = true
        )
        val fakeDao = FakeAlarmDao(listOf(recurringAlarm))
        val fakeLauncher = FakePlaybackLauncher()

        val coordinator = AlarmTriggerCoordinator(
            wakeLock = fakeWakeLock,
            alarmDao = fakeDao,
            playbackLauncher = fakeLauncher
        )

        coordinator.acquireWakeLock()
        coordinator.processAlarm(55)

        assertTrue("Korduvat äratust ei tohi märkida mitteaktiivseks", fakeDao.updatedAlarms.none { it.id == 55 })
        assertEquals("Vikerraadio", fakeLauncher.launchedStation)
        assertFalse(fakeWakeLock.isHeld)
    }

    @Test
    fun testNonExistentAlarmReleasesWakeLockImmediately() = runBlocking {
        val fakeWakeLock = FakeWakeLockHandle()
        val fakeDao = FakeAlarmDao(emptyList()) // Tühi andmebaas
        val fakeLauncher = FakePlaybackLauncher()

        val coordinator = AlarmTriggerCoordinator(
            wakeLock = fakeWakeLock,
            alarmDao = fakeDao,
            playbackLauncher = fakeLauncher
        )

        coordinator.acquireWakeLock()
        coordinator.processAlarm(12345)

        assertEquals("Teenust ei tohi käivitada kui jaama ei leitud", null, fakeLauncher.launchedStation)
        assertFalse("WakeLock peab olema vabastatud", fakeWakeLock.isHeld)
        assertEquals(1, fakeWakeLock.releaseCallCount)
    }

    @Test
    fun testInvalidAlarmIdReleasesWakeLockImmediately() = runBlocking {
        val fakeWakeLock = FakeWakeLockHandle()
        val fakeDao = FakeAlarmDao(emptyList())
        val fakeLauncher = FakePlaybackLauncher()

        val coordinator = AlarmTriggerCoordinator(
            wakeLock = fakeWakeLock,
            alarmDao = fakeDao,
            playbackLauncher = fakeLauncher
        )

        coordinator.acquireWakeLock()
        coordinator.processAlarm(-1)

        assertEquals("Teenust ei tohi käivitada vigase ID korral", null, fakeLauncher.launchedStation)
        assertFalse("WakeLock peab olema vabastatud", fakeWakeLock.isHeld)
        assertEquals(1, fakeWakeLock.releaseCallCount)
    }

    @Test
    fun testOneTimeAlarmDeactivatedAndRescheduledEvenWhenServiceLaunchThrows() = runBlocking {
        val fakeWakeLock = FakeWakeLockHandle()
        val oneTimeAlarm = Alarm(
            id = 101,
            hour = 6,
            minute = 30,
            days = emptySet(),
            stationName = "Retro FM",
            stationUrl = "https://stream.retro.ee/retro",
            isEnabled = true
        )
        val fakeDao = FakeAlarmDao(listOf(oneTimeAlarm))
        val fakeLauncher = FakePlaybackLauncher().apply { shouldThrow = true }
        var rescheduleCount = 0

        val coordinator = AlarmTriggerCoordinator(
            wakeLock = fakeWakeLock,
            alarmDao = fakeDao,
            playbackLauncher = fakeLauncher,
            onAlarmRescheduled = { rescheduleCount++ }
        )

        coordinator.acquireWakeLock()
        coordinator.processAlarm(101)

        assertEquals(
            "Ühekordne äratus peab olema deaktiveeritud ka esitustõrke korral",
            false,
            fakeDao.getAlarmById(101)?.isEnabled
        )
        assertEquals("Järgmine äratus peab olema ajastatud ka esitustõrke korral", 1, rescheduleCount)
        assertFalse("WakeLock peab olema vabastatud", fakeWakeLock.isHeld)
        assertEquals(1, fakeWakeLock.releaseCallCount)
    }

    @Test
    fun testRecurringAlarmRescheduledOnSuccessfulLaunch() = runBlocking {
        val fakeWakeLock = FakeWakeLockHandle()
        val recurringAlarm = Alarm(
            id = 103,
            hour = 7,
            minute = 0,
            days = setOf(1, 2, 3, 4, 5),
            stationName = "Vikerraadio",
            stationUrl = "https://stream.err.ee/viker",
            isEnabled = true
        )
        val fakeDao = FakeAlarmDao(listOf(recurringAlarm))
        var rescheduleCount = 0

        val coordinator = AlarmTriggerCoordinator(
            wakeLock = fakeWakeLock,
            alarmDao = fakeDao,
            playbackLauncher = FakePlaybackLauncher(),
            onAlarmRescheduled = { rescheduleCount++ }
        )

        coordinator.acquireWakeLock()
        coordinator.processAlarm(103)

        assertEquals("Järgmine äratus peab olema ajastatud", 1, rescheduleCount)
        assertFalse(fakeWakeLock.isHeld)
    }

    @Test
    fun testRecurringAlarmRescheduledAndKeptEnabledWhenServiceLaunchThrows() = runBlocking {
        val fakeWakeLock = FakeWakeLockHandle()
        val recurringAlarm = Alarm(
            id = 104,
            hour = 7,
            minute = 0,
            days = setOf(1, 2, 3, 4, 5),
            stationName = "Vikerraadio",
            stationUrl = "https://stream.err.ee/viker",
            isEnabled = true
        )
        val fakeDao = FakeAlarmDao(listOf(recurringAlarm))
        var rescheduleCount = 0

        val coordinator = AlarmTriggerCoordinator(
            wakeLock = fakeWakeLock,
            alarmDao = fakeDao,
            playbackLauncher = FakePlaybackLauncher().apply { shouldThrow = true },
            onAlarmRescheduled = { rescheduleCount++ }
        )

        coordinator.acquireWakeLock()
        coordinator.processAlarm(104)

        assertEquals("Korduv äratus peab jääma aktiivseks", true, fakeDao.getAlarmById(104)?.isEnabled)
        assertEquals("Järgmine äratus peab olema ajastatud ka esitustõrke korral", 1, rescheduleCount)
        assertFalse(fakeWakeLock.isHeld)
    }

    @Test
    fun testWakeLockReleasedWhenReschedulingThrows() = runBlocking {
        val fakeWakeLock = FakeWakeLockHandle()
        val oneTimeAlarm = Alarm(
            id = 105,
            hour = 6,
            minute = 0,
            days = emptySet(),
            stationName = "Kuku",
            stationUrl = "https://stream.kuku.ee/kuku",
            isEnabled = true
        )
        val fakeDao = FakeAlarmDao(listOf(oneTimeAlarm))

        val coordinator = AlarmTriggerCoordinator(
            wakeLock = fakeWakeLock,
            alarmDao = fakeDao,
            playbackLauncher = FakePlaybackLauncher(),
            onAlarmRescheduled = { throw IllegalStateException("Simulated AlarmManager failure") }
        )

        coordinator.acquireWakeLock()
        coordinator.processAlarm(105)

        assertEquals(false, fakeDao.getAlarmById(105)?.isEnabled)
        assertFalse("WakeLock peab olema vabastatud ka ajastamise tõrke korral", fakeWakeLock.isHeld)
    }

    @Test
    fun testOneTimeAlarmExecutionOrderServiceBeforeDeactivationAndReschedule() = runBlocking {
        val executionOrder = mutableListOf<String>()
        val fakeWakeLock = FakeWakeLockHandle()
        val oneTimeAlarm = Alarm(
            id = 102,
            hour = 7,
            minute = 15,
            days = emptySet(),
            stationName = "Star FM",
            stationUrl = "https://stream.starfm.ee/star",
            isEnabled = true
        )
        val fakeDao = FakeAlarmDao(listOf(oneTimeAlarm), onUpdateCallback = {
            executionOrder.add("alarm_deactivated")
        })
        val fakeLauncher = FakePlaybackLauncher(onLaunchCallback = {
            executionOrder.add("service_launched")
        })

        val coordinator = AlarmTriggerCoordinator(
            wakeLock = fakeWakeLock,
            alarmDao = fakeDao,
            playbackLauncher = fakeLauncher,
            onAlarmRescheduled = {
                executionOrder.add("alarm_rescheduled")
            }
        )

        coordinator.acquireWakeLock()
        coordinator.processAlarm(102)

        assertEquals(
            listOf("service_launched", "alarm_deactivated", "alarm_rescheduled"),
            executionOrder
        )
        assertEquals(false, fakeDao.getAlarmById(102)?.isEnabled)
        assertFalse(fakeWakeLock.isHeld)
    }

    @Test
    fun processAlarm_whenAlarmHasRecordAudioTrue_passesRecordAudioTrueToLauncher() = runBlocking {
        val fakeWakeLock = FakeWakeLockHandle()
        val alarmWithRecording = Alarm(
            id = 201,
            hour = 6,
            minute = 45,
            days = setOf(1, 2, 3),
            stationName = "Vikerraadio",
            stationUrl = "https://otse.err.ee/vikerraadio",
            isEnabled = true,
            recordAudio = true
        )
        val fakeDao = FakeAlarmDao(listOf(alarmWithRecording))
        val fakeLauncher = FakePlaybackLauncher()

        val coordinator = AlarmTriggerCoordinator(
            wakeLock = fakeWakeLock,
            alarmDao = fakeDao,
            playbackLauncher = fakeLauncher
        )

        coordinator.processAlarm(201)

        assertEquals("https://otse.err.ee/vikerraadio", fakeLauncher.launchedUrl)
        assertEquals("Vikerraadio", fakeLauncher.launchedStation)
        assertTrue("Launcher should receive recordAudio = true", fakeLauncher.launchedRecordAudio)
    }

    @Test
    fun processAlarm_whenAlarmHasRecordAudioFalse_passesRecordAudioFalseToLauncher() = runBlocking {
        val fakeWakeLock = FakeWakeLockHandle()
        val alarmWithoutRecording = Alarm(
            id = 202,
            hour = 8,
            minute = 0,
            days = emptySet(),
            stationName = "Raadio 2",
            stationUrl = "https://otse.err.ee/raadio2",
            isEnabled = true,
            recordAudio = false
        )
        val fakeDao = FakeAlarmDao(listOf(alarmWithoutRecording))
        val fakeLauncher = FakePlaybackLauncher()

        val coordinator = AlarmTriggerCoordinator(
            wakeLock = fakeWakeLock,
            alarmDao = fakeDao,
            playbackLauncher = fakeLauncher
        )

        coordinator.processAlarm(202)

        assertEquals("https://otse.err.ee/raadio2", fakeLauncher.launchedUrl)
        assertEquals("Raadio 2", fakeLauncher.launchedStation)
        assertFalse("Launcher should receive recordAudio = false", fakeLauncher.launchedRecordAudio)
    }
}

