package ws.ct.radiowakes

import android.content.Context
import android.os.Bundle
import androidx.media3.common.MediaMetadata
import ws.ct.radiowakes.R

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
        }

        // 5. PEALKIRJA PUHASTUS

        // a) UUS LISA: Kontrollime " * Aasta" mustrit (nt "Fast Car * 1988")
        // Otsime tärni, mille ees ja järel on tühik, ning võtame kõik, mis järgneb.
        val starRegex = Regex("""\s+\*\s+(.*)$""")
        val starMatch = starRegex.find(title)
        if (starMatch != null) {
            val content = starMatch.groupValues[1].trim()
            // Lisame listi (lõppu või algusesse, siin pole vahet, sest see on tavaliselt ainus lisa)
            extrasList.add(0, content)
            // Eemaldame selle osa pealkirjast
            title = title.substring(0, starMatch.range.first).trim()
        }

        // b) Kontrollime sulgudes lisasid tsükliga (Jonas Brothers fix)
        val titleEndRegex = Regex("""\s*\(([^()]+(?:\([^()]*\)[^()]*)*)\)\s*$""")

        while (true) {
            val match = titleEndRegex.find(title) ?: break
            val content = match.groupValues[1].trim()
            extrasList.add(0, content) // Lisame ettepoole, et "Remix * 1988" järjekord oleks ilus
            title = title.substring(0, match.range.first).trim()
        }

        // 6. EXTRA LÕPLIK VORMISTUS
        var extra = extrasList.filter { it.isNotBlank() }.joinToString(" • ")

        // Asendame "Esitaja (Pill)" -> "Esitaja • Pill"
        if (extra.isNotBlank() && !isSoundtrackFormat) {
            extra = extra.replace(Regex("""\s*\(([^()]+)\)"""), " • $1")
        }

        // 7. REVERSED STATIONS
        if (AppConfig.Metadata.REVERSED_STATIONS.contains(stationName) && !isSoundtrackFormat) {
            val temp = artist
            artist = title
            title = temp
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
        val uniqueId = "radiowakes_${(title + artist).hashCode()}"

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
            .setArtworkData(artworkData, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
            .build()
    }
}