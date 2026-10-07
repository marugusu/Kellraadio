package app.radiorecalarm.ui

import android.view.HapticFeedbackConstants
import android.view.View

/**
 * Teostab selge ja lühikese füüsilise vibratsiooniklõpsu tavavajutusel.
 * Kasutab VIRTUAL_KEY ja tagavaraks KEYBOARD_TAP, et tagada
 * kindel ja karge tagasiside eri tootjate seadmetel (Samsung, Pixel, Xiaomi jne).
 */
fun View.performHapticTap() {
    val handled = performHapticFeedback(
        HapticFeedbackConstants.VIRTUAL_KEY,
        HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
    )
    if (!handled) {
        performHapticFeedback(
            HapticFeedbackConstants.KEYBOARD_TAP,
            HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
        )
    }
}
