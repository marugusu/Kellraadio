package ws.ct.radiow

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
