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

    private class FakeAlarmDao(initialAlarms: List<Alarm> = emptyList()) : AlarmDao {
        val alarms = initialAlarms.associateBy { it.id }.toMutableMap()
        val updatedAlarms = mutableListOf<Alarm>()

        override suspend fun insert(alarm: Alarm): Long {
            alarms[alarm.id] = alarm
            return alarm.id.toLong()
        }

        override suspend fun update(alarm: Alarm) {
            alarms[alarm.id] = alarm
            updatedAlarms.add(alarm)
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
    }

    private class FakePlaybackLauncher : AlarmPlaybackLauncher {
        var launchedUrl: String? = null
        var launchedStation: String? = null
        var shouldThrow = false

        override fun launchPlaybackService(streamUrl: String, stationName: String) {
            if (shouldThrow) {
                throw RuntimeException("Simulated service start failure")
            }
            launchedUrl = streamUrl
            launchedStation = stationName
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

        var thrown = false
        try {
            coordinator.processAlarm(10)
        } catch (e: Exception) {
            thrown = true
        }

        assertTrue("Teenuse käivitamise viga pidi edasi levima", thrown)
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
}

