package ee.minu.kellraadio.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import java.util.Locale
import kotlin.math.absoluteValue

object StationArtworkUtils {

    private val NOISE_WORDS = listOf(
        "raadio", "radio", "fm", "eesti", "tallinn", "onair", "channel","klara",
        "live", "suomi", "est", "fin", "the", "hits", "love", "saami", "duo","elmari","elmar"
    )

    fun getStationInitials(stationName: String): String {
        var clean = stationName.trim()
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

        // 2. Numbrite ja Ajastute maagia
        val numberRegex = "(\\d+)".toRegex()
        val numberMatch = numberRegex.find(clean)
        if (numberMatch != null) {
            val num = numberMatch.value

            // Leiame brändi nime enne numbrit
            val parts = clean.split(" ")
            var brandPrefix = ""

            for (part in parts) {
                if (part.contains(num)) break
                if (part.isNotEmpty()) {
                    brandPrefix = part
                    // Kui leidsime "BBC" või "Star" (mitte müra), siis see ongi see
                    if (!NOISE_WORDS.contains(part.lowercase())) {
                        break
                    }
                }
            }

            // Tagavara: kui brändi ei leitud, võta esimene täht
            if (brandPrefix.isEmpty()) brandPrefix = clean.take(1)

            val result = when {
                // UUS REEGEL: BBC Radio 4 -> BBC + 4 -> BBC4
                // Kui bränd on täpselt 3 tähte ja number on 1 number, pane kokku.
                brandPrefix.length == 3 && num.length == 1 -> {
                    brandPrefix + num
                }

                // Star 80 -> ST + 80 -> ST80
                num.length >= 2 -> {
                    brandPrefix.take(2) + num
                }

                // Raadio 2 -> R + 2 -> R2
                else -> {
                    brandPrefix.take(1) + num
                }
            }

            return formatToFourChars(result.uppercase())
        }

        // ... Ülejäänud loogika jääb samaks ...
        val parts = clean.split("\\s+".toRegex())
            .filter { !NOISE_WORDS.contains(it.lowercase()) }
            .filter { it.length > 1 || it == "X" }

        val activeParts = if (parts.isEmpty()) clean.split(" ") else parts
        val firstWord = activeParts[0]

        val result = when {
            // Kahesõnalised (Duo Rock -> DURO)
            activeParts.size >= 2 -> {
                val w1 = activeParts[0]
                val w2 = activeParts[1]
                (w1.take(2) + w2.take(2))
            }
            // Üks sõna
            else -> {
                firstWord.take(4)
            }
        }

        return formatToFourChars(result.uppercase())
    }

    // Teeb kindlaks, et tulemus on täpselt 4 tähte pikk
    private fun formatToFourChars(input: String): String {
        return when (input.length) {
            4 -> input
            3 -> input + input.last() // VIK -> VIKK (et täita ruum)
            2 -> input + input        // R2 -> R2R2
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

        paint.textSize = size / 2.0f

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