package app.radiorecalarm.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import app.radiorecalarm.AppConfig
import kotlin.math.absoluteValue

object StationArtworkUtils {

    fun getStationInitials(stationName: String): String {
        val cleanName = stationName.trim()
        val lowerName = cleanName.lowercase()

        // --- 1. KONTROLLIME ERANDEID ---
        if (AppConfig.UI.STATION_INITIALS_EXCEPTIONS.containsKey(lowerName)) {
            return AppConfig.UI.STATION_INITIALS_EXCEPTIONS[lowerName]!!.uppercase()
        }

        val clean = cleanName
            .replace("-", " ")
            .replace("'", "")
            .replace("?", "")

        // 1. R2 alamkanalid (R2 Chill -> R2CH)
        if (clean.uppercase().startsWith("R2 ") && clean.length > 3) {
            val parts = clean.split(" ")
            if (parts.size >= 2) {
                return ("R2" + parts[1].take(2)).uppercase()
            }
        }

        // 2. Numbrid
        val numberRegex = "(\\d+)".toRegex()
        val numberMatch = numberRegex.find(clean)
        if (numberMatch != null) {
            val num = numberMatch.value
            val parts = clean.split(" ")
            var brandPrefix = ""

            for (part in parts) {
                if (part.contains(num)) break
                if (part.isNotEmpty()) {
                    brandPrefix = part
                    if (!AppConfig.UI.NOISE_WORDS.contains(part.lowercase())) break
                }
            }
            if (brandPrefix.isEmpty()) brandPrefix = clean.take(1)

            val result = when {
                brandPrefix.length == 3 && num.length == 1 -> brandPrefix + num // BBC4
                num.length >= 2 -> brandPrefix.take(2) + num // ST80
                else -> brandPrefix.take(1) + num // R2
            }
            return formatToFourChars(result.uppercase())
        }

        val parts = clean.split("\\s+".toRegex())
            .filter { !AppConfig.UI.NOISE_WORDS.contains(it.lowercase()) }
            .filter { it.length > 1 || it == "X" }

        val activeParts = if (parts.isEmpty()) clean.split(" ") else parts
        val firstWord = activeParts[0]

        val result = when {
            activeParts.size >= 2 -> {
                val w1 = activeParts[0]
                val w2 = activeParts[1]
                (w1.take(2) + w2.take(2))
            }
            else -> firstWord.take(4)
        }

        return formatToFourChars(result.uppercase())
    }

    private fun formatToFourChars(input: String): String {
        return when (input.length) {
            4 -> input
            3 -> input + input.last()
            2 -> input + input
            1 -> input + input + input + input
            0 -> "RADI"
            else -> input.take(4)
        }
    }

    // --- BITMAP GENERATOR ---
    fun generateDarkStationBitmap(stationName: String): Bitmap {
        val size = 512
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val darkBackground = Color(0xFF1C1B1F).toArgb()
        val paint = Paint()
        paint.color = darkBackground
        paint.style = Paint.Style.FILL
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)

        val initials = getStationInitials(stationName)

        paint.color = android.graphics.Color.WHITE
        paint.alpha = 30
        paint.isAntiAlias = true
        paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        paint.textAlign = Paint.Align.CENTER

        // --- DÜNAAMILINE SUURUS ---
        val textSizeFactor = when (initials.length) {
            in 0..2 -> 1.5f // Väga suur (R2)
            3 -> 1.8f       // Keskmine (SKY)
            else -> 2.0f    // Tavaline (VIKE)
        }
        paint.textSize = size / textSizeFactor

        val bounds = Rect()
        paint.getTextBounds(initials, 0, initials.length, bounds)
        val x = size / 2f
        val y = (size / 2f) - bounds.exactCenterY()

        canvas.drawText(initials, x, y, paint)

        return bitmap
    }

    fun getStationColor(stationName: String): Color {
        if (stationName.isEmpty()) return Color.DarkGray
        val hash = stationName.hashCode()
        val hue = (hash.absoluteValue % 360).toFloat()
        return Color.hsv(hue, 0.6f, 0.5f)
    }
}
