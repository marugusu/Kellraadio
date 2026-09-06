package app.radiorecalarm

import android.content.Context
import android.os.Bundle
import androidx.media3.common.MediaMetadata
import app.radiorecalarm.R

// Lihtne andmeklass tulemuse hoidmiseks, et ei peaks kasutama segast Triple<String, String, String>
data class ParsedMetadata(
    val artist: String,
    val title: String,
    val extra: String
)

class RadioMetadataHelper(
    private val context: Context? = null,
    private val defaultLiveBroadcast: String = "Otseeeter"
) {

    private fun getLiveBroadcastString(): String {
        return try {
            context?.getString(R.string.live_broadcast) ?: defaultLiveBroadcast
        } catch (e: Exception) {
            defaultLiveBroadcast
        }
    }

    /**
     * Puhastab toorest tekstist mitteprinditavad kontrollsümbolid (nt \u0000 kuni \u001F ja DEL)
     * ning tehnilise prügi (nt {+info: ...}), asendades reavahetused viisakate eraldajatega.
     */
    fun sanitizeRawInput(rawMetadata: String): String {
        return rawMetadata
            .replace(Regex("""[\u0000-\u0008\u000B\u000C\u000E-\u001F\u007F]"""), "")
            .replace(Regex("""\s*\{.*\}\s*$"""), "")
            .trim()
            .replace("\r\n", " - ")
            .replace("\n", " - ")
            .replace("\r", " - ")
    }

    /**
     * See funktsioon teeb toorest striimi tekstist (nt "Queen - Bohemian Rhapsody")
     * korralikud väljad (Artist, Title).
     * Siin asub ka loogika "Reversed" jaamade (Star FM) ja tühjade stringide jaoks.
     */
    fun parse(rawMetadata: String, stationName: String): ParsedMetadata {
        val liveBroadcastStr = getLiveBroadcastString()
        val safeStation = if (stationName.isNotBlank()) stationName.trim() else "Radio"

        // 1. Eemalda tehniline prügi, kontrollsümbolid ja reavahetused
        val cleaned = sanitizeRawInput(rawMetadata)

        val isGarbage = cleaned.isEmpty() ||
                cleaned == "-" ||
                cleaned == "." ||
                cleaned == " -" ||
                cleaned == "- " ||
                cleaned == " - " ||
                cleaned == "_" ||
                cleaned == "•" ||
                cleaned.all { it == '-' || it == ' ' || it == '.' || it == '_' || it == '•' }

        if (isGarbage) {
            return ParsedMetadata(liveBroadcastStr, safeStation, "")
        }

        // 2. Esialgne tükeldamine
        val rawParts = cleaned.split(" - ").map { it.trim() }.filter { it.isNotBlank() }

        // 3. "SMART MERGE": Parandame kohad, kus " - " oli sulgude sees (klassikaline muusika)
        val parts = mutableListOf<String>()
        var buffer = ""

        for (part in rawParts) {
            if (buffer.isNotEmpty()) {
                buffer += " - $part"
                if (buffer.count { it == '(' } <= buffer.count { it == ')' }) {
                    parts.add(buffer)
                    buffer = ""
                }
            } else {
                if (part.contains('(') && part.count { it == '(' } > part.count { it == ')' }) {
                    buffer = part
                } else {
                    parts.add(part)
                }
            }
        }
        if (buffer.isNotEmpty()) parts.add(buffer)

        if (parts.size < 2) {
            val single = (parts.getOrNull(0) ?: cleaned).trim()
            val isStationOrLive = single.isEmpty() ||
                    single.equals(safeStation, ignoreCase = true) ||
                    single.equals(liveBroadcastStr, ignoreCase = true) ||
                    single.equals("Live Stream", ignoreCase = true) ||
                    single.equals("Otseeeter", ignoreCase = true)

            return if (isStationOrLive) {
                ParsedMetadata(liveBroadcastStr, safeStation, "")
            } else {
                ParsedMetadata(single, safeStation, "")
            }
        }

        // 4. STRATEEGIA VALIK (Soundtrack vs Tavaline)
        var artist = parts[0]
        var title = ""
        val extrasList = mutableListOf<String>()

        // Filmimuusika kontroll: 3 osa ja keskmises on aastaarv ", 2021"
        val yearRegex = Regex(""",\s*(19|20)\d{2}""")
        val isSoundtrackFormat = parts.size == 3 && yearRegex.containsMatchIn(parts[1])

        if (isSoundtrackFormat) {
            extrasList.add(parts[1]) // Album läheb extrasse
            title = parts[2]
        } else {
            // Tavaline
            title = parts[1]
            if (parts.size >= 3) {
                extrasList.add(parts.subList(2, parts.size).joinToString(" • "))
            }

            // REVERSED STATIONS (Star FM) - Teeme vahetuse kohe siin, enne puhastamist
            if (AppConfig.Metadata.REVERSED_STATIONS.contains(stationName)) {
                val temp = artist
                artist = title
                title = temp
            }
        }

        // NÜÜD RAKENDAME PUHASTUST REAALSETELE VÄLJADELE (Olenemata jaamast)

        // a) Poolitame esitaja, kui seal on [+] märk
        if (artist.contains("[+]")) {
            val artistParts = artist.split("[+]").map { it.trim() }
            if (artistParts.size >= 2) {
                artist = artistParts[0]
                // Lisame kaas-esitajad lisainfosse (extra) esimeseks
                extrasList.add(0, artistParts.subList(1, artistParts.size).joinToString(" • "))
            }
        }

        // b) Kontrollime pealkirjas " * Aasta" mustrit (nt "Fast Car * 1988")
        val starRegex = Regex("""\s+\*\s+(.*)$""")
        val starMatch = starRegex.find(title)
        if (starMatch != null) {
            val content = starMatch.groupValues[1].trim()
            if (content.isNotBlank()) extrasList.add(content)
            val remaining = title.substring(0, starMatch.range.first).trim()
            if (remaining.isNotBlank()) {
                title = remaining
            }
        }

        // c) Kontrollime sulgudes lisasid pealkirja lõpus tsükliga
        val titleEndRegex = Regex("""\s*\(([^()]+(?:\([^()]*\)[^()]*)*)\)\s*$""")
        while (true) {
            val match = titleEndRegex.find(title) ?: break
            val content = match.groupValues[1].trim()
            val remaining = title.substring(0, match.range.first).trim()
            if (remaining.isNotBlank()) {
                if (content.isNotBlank()) extrasList.add(0, content)
                title = remaining
            } else {
                // Kui sulgude eemaldamisel jääks pealkiri tühjaks (nt terve pealkiri oli "(Live)"),
                // eemaldame ainult sulud, aga säilitame sisu pealkirjana
                title = content.ifBlank { title.removeSurrounding("(", ")").trim() }
                break
            }
        }

        // d) Eemaldame pealkirja ümbritsevad ülakomad (nt 'La Lucina')
        title = title.removeSurrounding("'").trim()

        // 5. FAIL-SAFE FALLBACK (Kindlustus tühjade väärtuste vastu)
        var finalArtist = artist.trim()
        var finalTitle = title.trim()

        if (finalTitle.isBlank()) {
            finalTitle = if (extrasList.isNotEmpty()) extrasList.removeAt(0) else safeStation
        }
        if (finalArtist.isBlank()) {
            finalArtist = liveBroadcastStr
        }
        if (finalArtist.equals(safeStation, ignoreCase = true) && finalTitle.equals(safeStation, ignoreCase = true)) {
            finalArtist = liveBroadcastStr
        }

        // 6. EXTRA LÕPLIK VORMISTUS
        var extra = extrasList.filter { it.isNotBlank() }.joinToString(" • ")

        // Asendame "Esitaja (Pill)" -> "Esitaja • Pill"
        if (extra.isNotBlank() && !isSoundtrackFormat) {
            extra = extra.replace(Regex("""\s*\(([^()]+)\)"""), " • $1")
        }

        return ParsedMetadata(finalArtist, finalTitle, extra.trim())
    }

    /**
     * Ehitab valmis MediaMetadata objekti, mida ExoPlayer ja Bluetooth vajavad.
     * Siin on peidus ka see "Skoda fix" 5-minuti kestuse info (Bundle sees).
     * Garanteerib, et ükski väli ei ole tühi ega sisalda mitteprinditavaid märke.
     */
    fun buildMediaMetadata(
        title: String,
        artist: String,
        stationName: String,
        artworkData: ByteArray?
    ): MediaMetadata {
        val safeStation = if (stationName.isNotBlank()) stationName.trim() else "Radio"
        val cleanTitle = title.replace(Regex("""[\u0000-\u001F\u007F]"""), "").trim()
        val cleanArtist = artist.replace(Regex("""[\u0000-\u001F\u007F]"""), "").trim()

        val finalTitle = if (cleanTitle.isNotBlank()) cleanTitle else safeStation
        val finalArtist = if (cleanArtist.isNotBlank()) cleanArtist else getLiveBroadcastString()

        // Unikaalne ID aitab autol aru saada, et lugu muutus
        val uniqueId = "radiow_${(finalTitle + finalArtist).hashCode()}"

        val extras = Bundle().apply {
            putString("android.media.metadata.MEDIA_ID", uniqueId)
            putLong("android.media.metadata.DURATION", 300000L) // 5 minutit (Skoda fix)
        }

        return MediaMetadata.Builder()
            .setTitle(finalTitle)
            .setArtist(finalArtist)
            .setAlbumTitle(safeStation)
            .setTrackNumber(1)
            .setTotalTrackCount(1)
            .setIsPlayable(true)
            .setExtras(extras)
            .setArtworkData(null, MediaMetadata.PICTURE_TYPE_FRONT_COVER) // Pilt asendatud NULL-iga
            .build()
    }
}
