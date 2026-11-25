package app.radiorecalarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar

class AlarmUtilsTest {

    private fun calMillis(
        year: Int,
        month: Int,
        dayOfMonth: Int,
        hour: Int,
        minute: Int,
        second: Int = 0,
        millisecond: Int = 0
    ): Long = Calendar.getInstance().apply {
        set(Calendar.YEAR, year)
        set(Calendar.MONTH, month)
        set(Calendar.DAY_OF_MONTH, dayOfMonth)
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, second)
        set(Calendar.MILLISECOND, millisecond)
    }.timeInMillis

    @Test
    fun `rescheduling 4 seconds before alarm does not skip upcoming alarm`() {
        // Reproduces 2026-11-25 (Wednesday): alarms at 06:30, 06:33, 06:38 (Mon-Fri), 15:00 (Wed)
        val workDays = setOf(
            Calendar.MONDAY,
            Calendar.TUESDAY,
            Calendar.WEDNESDAY,
            Calendar.THURSDAY,
            Calendar.FRIDAY
        )
        val alarms = listOf(
            Alarm(id = 1, hour = 6, minute = 30, days = workDays, stationName = "Vikerraadio", stationUrl = "u1"),
            Alarm(id = 2, hour = 6, minute = 33, days = workDays, stationName = "Klassikaraadio", stationUrl = "u2"),
            Alarm(id = 3, hour = 6, minute = 38, days = workDays, stationName = "Vikerraadio", stationUrl = "u1"),
            Alarm(id = 4, hour = 15, minute = 0, days = setOf(Calendar.WEDNESDAY), stationName = "Legendaarne", stationUrl = "u3")
        )

        val now063756 = calMillis(2026, Calendar.NOVEMBER, 25, 6, 37, 56)
        val expected063800 = calMillis(2026, Calendar.NOVEMBER, 25, 6, 38, 0)

        val next = AlarmUtils.selectNextAlarm(alarms, nowMillis = now063756)

        assertNotNull(next)
        assertEquals(3, next!!.first.id)
        assertEquals(expected063800, next.second)
    }

    @Test
    fun `one-time alarm scheduled less than 15 seconds ahead still triggers today`() {
        val now063750 = calMillis(2026, Calendar.NOVEMBER, 25, 6, 37, 50)
        val expected063800 = calMillis(2026, Calendar.NOVEMBER, 25, 6, 38, 0)

        val triggerTime = AlarmUtils.findNextAlarmTime(
            hour = 6,
            minute = 38,
            days = emptySet(),
            nowMillis = now063750
        )

        assertEquals(expected063800, triggerTime)
    }

    @Test
    fun `triggered alarm firing 1 second early does not refire on the same day`() {
        val workDays = setOf(
            Calendar.MONDAY,
            Calendar.TUESDAY,
            Calendar.WEDNESDAY,
            Calendar.THURSDAY,
            Calendar.FRIDAY
        )
        val alarms = listOf(
            Alarm(id = 1, hour = 6, minute = 30, days = workDays, stationName = "Vikerraadio", stationUrl = "u1"),
            Alarm(id = 2, hour = 6, minute = 33, days = workDays, stationName = "Klassikaraadio", stationUrl = "u2")
        )

        // Alarm 1 fires slightly early at 06:29:59; Alarm 2 at 06:33 must be selected, not Alarm 1 again.
        val now062959 = calMillis(2026, Calendar.NOVEMBER, 25, 6, 29, 59)
        val expected063300 = calMillis(2026, Calendar.NOVEMBER, 25, 6, 33, 0)

        val nextAfterEarlyTrigger = AlarmUtils.selectNextAlarm(
            alarms,
            nowMillis = now062959,
            triggeredAlarmId = 1
        )
        assertNotNull(nextAfterEarlyTrigger)
        assertEquals(2, nextAfterEarlyTrigger!!.first.id)
        assertEquals(expected063300, nextAfterEarlyTrigger.second)

        // Single repeating alarm firing 1 second early must schedule for next day, not 1 second later.
        val singleAlarmOnly = listOf(alarms.first())
        val expectedTomorrow063000 = calMillis(2026, Calendar.NOVEMBER, 26, 6, 30, 0)
        val nextSingle = AlarmUtils.selectNextAlarm(
            singleAlarmOnly,
            nowMillis = now062959,
            triggeredAlarmId = 1
        )
        assertNotNull(nextSingle)
        assertEquals(expectedTomorrow063000, nextSingle!!.second)
    }

    @Test
    fun `empty alarm list returns null`() {
        assertNull(AlarmUtils.selectNextAlarm(emptyList(), nowMillis = 0L))
    }

    @Test
    fun `computeTimeUntilAlarm calculates hours and minutes accurately`() {
        val now083100 = calMillis(2026, Calendar.NOVEMBER, 25, 8, 31, 0)
        val resultSameDay = AlarmUtils.computeTimeUntilAlarm(
            hour = 15,
            minute = 0,
            days = setOf(Calendar.WEDNESDAY),
            nowMillis = now083100
        )
        assertEquals(0, resultSameDay.days)
        assertEquals(6, resultSameDay.hours)
        assertEquals(29, resultSameDay.minutes)

        val now083115 = calMillis(2026, Calendar.NOVEMBER, 25, 8, 31, 15)
        val resultSubMinute = AlarmUtils.computeTimeUntilAlarm(
            hour = 8,
            minute = 32,
            days = emptySet(),
            nowMillis = now083115
        )
        assertEquals(0, resultSubMinute.days)
        assertEquals(0, resultSubMinute.hours)
        assertEquals(1, resultSubMinute.minutes)
    }

    @Test
    fun `computeTimeUntilAlarm handles multi-day future alarm`() {
        val nowWednesday = calMillis(2026, Calendar.NOVEMBER, 25, 10, 0, 0)
        val resultSaturday = AlarmUtils.computeTimeUntilAlarm(
            hour = 15,
            minute = 4,
            days = setOf(Calendar.SATURDAY),
            nowMillis = nowWednesday
        )
        assertEquals(3, resultSaturday.days)
        assertEquals(5, resultSaturday.hours)
        assertEquals(4, resultSaturday.minutes)
    }
}
