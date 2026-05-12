package ws.ct.radiow

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import java.util.Locale

fun getFlagEmoji(countryCode: String): String {
    if (countryCode.length != 2) return ""
    return countryCode
        .uppercase(Locale.US)
        .map { char ->
            Character.codePointAt(char.toString(), 0) + 0x1F1A5
        }
        .map { codePoint ->
            String(Character.toChars(codePoint))
        }
        .joinToString("")
}

fun isTv(context: Context): Boolean {
    val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
    if (uiModeManager.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION) {
        return true
    }
    if (context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)) {
        return true
    }
    return false
}
