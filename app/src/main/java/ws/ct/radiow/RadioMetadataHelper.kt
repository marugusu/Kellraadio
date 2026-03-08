package ws.ct.radiow

import android.content.Context
import android.os.Bundle
import androidx.media3.common.MediaMetadata
import ws.ct.radiow.R

// Lihtne andmeklass tulemuse hoidmiseks, et ei peaks kasutama segast Triple<String, String, String>
data class ParsedMetadata(
    val artist: String,
    val title: String,
    val extra: String
)

class RadioMetadataHelper(private val context: Context) {

    /**
     * See funktsioon teeb toorest striimi tekstist (nt "Queen - Bohemian Rhapsody")
     * korralikud väljad (Artist, Title).
     * Siin asub ka loogika "Reversed" jaamade (Star FM) ja tühjade stringide jaoks.
     */
    fun parse(rawMetadata: String, stationName: String): ParsedMetadata {
        // 1. Eemalda tehniline prügi (nt {+info: ...}) ja reavahetused
        var cleaned = rawMetadata.replace(Regex("""\s*\{.*\}\s*$"""), "")
            .trim()
            .replace("\n", " - ")

        if (cleaned.isEmpty() || cleaned == "-" || cleaned == "." || cleaned == " -" || cleaned == "_") {
            return ParsedMetadata(context.getString(R.string.live_broadcast), stationName, "")
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
            return ParsedMetadata(parts.getOrNull(0) ?: cleaned, stationName, "")
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
            extrasList.add(content)
            title = title.substring(0, starMatch.range.first).trim()
        }

        // c) Kontrollime sulgudes lisasid pealkirja lõpus tsükliga
        val titleEndRegex = Regex("""\s*\(([^()]+(?:\([^()]*\)[^()]*)*)\)\s*$""")
        while (true) {
            val match = titleEndRegex.find(title) ?: break
            val content = match.groupValues[1].trim()
            extrasList.add(0, content) 
            title = title.substring(0, match.range.first).trim()
        }

        // d) Eemaldame pealkirja ümbritsevad ülakomad (nt 'La Lucina')
        title = title.removeSurrounding("'")

        // 5. EXTRA LÕPLIK VORMISTUS
        var extra = extrasList.filter { it.isNotBlank() }.joinToString(" • ")

        // Asendame "Esitaja (Pill)" -> "Esitaja • Pill"
        if (extra.isNotBlank() && !isSoundtrackFormat) {
            extra = extra.replace(Regex("""\s*\(([^()]+)\)"""), " • $1")
        }

        return ParsedMetadata(artist.trim(), title.trim(), extra.trim())
    }

    /**
     * Ehitab valmis MediaMetadata objekti, mida ExoPlayer ja Bluetooth vajavad.
     * Siin on peidus ka see "Skoda fix" 5-minuti kestuse info (Bundle sees).
     */
    fun buildMediaMetadata(
        title: String,
        artist: String,
        stationName: String,
        artworkData: ByteArray?
    ): MediaMetadata {
        // Unikaalne ID aitab autol aru saada, et lugu muutus
        val uniqueId = "radiow_${(title + artist).hashCode()}"

        val extras = Bundle().apply {
            putString("android.media.metadata.MEDIA_ID", uniqueId)
            putLong("android.media.metadata.DURATION", 300000L) // 5 minutit (Skoda fix)
        }

        return MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(stationName)
            .setTrackNumber(1)
            .setTotalTrackCount(1)
            .setIsPlayable(true)
            .setExtras(extras)
            .setArtworkData(null, MediaMetadata.PICTURE_TYPE_FRONT_COVER) // Pilt asendatud NULL-iga
            .build()
    }
}