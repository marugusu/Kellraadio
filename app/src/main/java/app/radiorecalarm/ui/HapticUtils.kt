package app.radiorecalarm.ui

import android.view.HapticFeedbackConstants
import android.view.View

/**
 * Teostab pehme ja diskreetse mikrotiksu tavavajutusel.
 * Kasutab CLOCK_TICK (peen mehaaniline kellaratta tikk) ja tagavaraks
 * pehmet KEYBOARD_TAP, vältides rasket VIRTUAL_KEY põmmu.
 */
fun View.performHapticTap() {
    val handled = performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    if (!handled) {
        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }
}
