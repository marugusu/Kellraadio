package app.radiorecalarm

import android.os.PowerManager

/**
 * Õmblus (seam) protsessori ärkvelolekuluku (WakeLock) haldamiseks.
 * Võimaldab riistvaraluku asendamist testides ja komponentide vahel.
 */
interface WakeLockHandle {
    val isHeld: Boolean
    fun acquire(timeoutMs: Long)
    fun release()
}

class AndroidWakeLockHandle(private val wakeLock: PowerManager.WakeLock) : WakeLockHandle {
    override val isHeld: Boolean get() = wakeLock.isHeld
    override fun acquire(timeoutMs: Long) = wakeLock.acquire(timeoutMs)
    override fun release() {
        if (wakeLock.isHeld) {
            wakeLock.release()
        }
    }
}

object NoOpWakeLockHandle : WakeLockHandle {
    override val isHeld: Boolean get() = false
    override fun acquire(timeoutMs: Long) {}
    override fun release() {}
}
