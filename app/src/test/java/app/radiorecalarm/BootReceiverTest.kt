package app.radiorecalarm

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BootReceiverTest {

    @Test
    fun `reschedules on boot and package update`() {
        assertTrue(BootReceiver.shouldReschedule("android.intent.action.BOOT_COMPLETED"))
        assertTrue(BootReceiver.shouldReschedule("android.intent.action.MY_PACKAGE_REPLACED"))
    }

    @Test
    fun `reschedules on manual system time change`() {
        assertTrue(BootReceiver.shouldReschedule("android.intent.action.TIME_SET"))
    }

    @Test
    fun `reschedules on time zone and DST change`() {
        assertTrue(BootReceiver.shouldReschedule("android.intent.action.TIMEZONE_CHANGED"))
    }

    @Test
    fun `ignores unrelated or missing actions`() {
        assertFalse(BootReceiver.shouldReschedule(null))
        assertFalse(BootReceiver.shouldReschedule("android.intent.action.TIME_TICK"))
        assertFalse(BootReceiver.shouldReschedule("android.intent.action.DATE_CHANGED"))
    }
}

