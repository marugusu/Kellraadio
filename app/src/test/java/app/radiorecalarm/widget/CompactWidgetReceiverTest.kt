package app.radiorecalarm.widget

import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class CompactWidgetReceiverTest {

    @Test
    fun `launchWidgetUpdate completes safely when goAsync returns null`() {
        val receiver = CompactWidgetReceiver()
        val latch = CountDownLatch(1)
        var uncaughtError: Throwable? = null
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { _, e -> uncaughtError = e }

        try {
            val mockContext = android.content.ContextWrapper(null)
            receiver.launchWidgetUpdate(mockContext) {
                latch.countDown()
            }
            assertTrue("Widget update block did not run", latch.await(3, TimeUnit.SECONDS))
            Thread.sleep(50)
            assertTrue("Unexpected uncaught exception: $uncaughtError", uncaughtError == null)
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(previousHandler)
        }
    }
}
