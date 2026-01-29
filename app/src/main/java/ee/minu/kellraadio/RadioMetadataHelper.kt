package ee.minu.kellraadio

import android.content.Context
import android.os.Bundle
import androidx.media3.common.MediaMetadata

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

        if (cleaned.isEmpty() || cleaned == "-" || cleaned == "." || cleaned == " -") {
            return ParsedMetadata(context.getString(R.string.live_broadcast), stationName, "")
        }

        // 2. Esialgne tükeldamine
        val rawParts = cleaned.split(" - ").map { it.trim() }.filter { it.isNotBlank() }

        // 3. "SMART MERGE": Parandame kohad, kus " - " oli sulgude sees (klassikaline muusika)
        val parts = mutableListOf<String>()
        var buffer = ""

        for (part in rawParts) {
            if (buffer.isNotEmpty()) {
                // Kui meil on poolik sulg ees, liidame järgmise tüki otsa
                buffer += " - $part"
                // Kontrollime, kas sulud on nüüd tasakaalus
                if (buffer.count { it == '(' } <= buffer.count { it == ')' }) {
                    parts.add(buffer)
                    buffer = ""
                }
            } else {
                // Kontrollime, kas selles tükis on lahtine sulg ilma kinniseta
                if (part.contains('(') && part.count { it == '(' } > part.count { it == ')' }) {
                    buffer = part // Alustame puhverdamist
                } else {
                    parts.add(part) // Tavaline tükk, lisa kohe
                }
            }
        }
        if (buffer.isNotEmpty()) parts.add(buffer) // Juhuks kui string oli vigane

        if (parts.size < 2) {
            return ParsedMetadata(parts.getOrNull(0) ?: cleaned, stationName, "")
        }

        // 4. STRATEEGIA VALIK (Soundtrack vs Tavaline)
        var artist = parts[0]
        var title = ""
        val extrasList = mutableListOf<String>() // Kogume kõik lisad siia listi

        // Filmimuusika kontroll: 3 osa ja keskmises on aastaarv ", 2021"
        val yearRegex = Regex(""",\s*(19|20)\d{2}""")
        val isSoundtrackFormat = parts.size == 3 && yearRegex.containsMatchIn(parts[1])

        if (isSoundtrackFormat) {
            // [0] Artist
            // [1] Album/Info -> läheb extrasse
            // [2] Title
            extrasList.add(parts[1])
            title = parts[2]
        } else {
            // Tavaline: [0] Artist, [1] Title, [2..n] Extra
            title = parts[1]
            if (parts.size >= 3) {
                // Kõik ülejäänud osad lisame listi
                extrasList.add(parts.subList(2, parts.size).joinToString(" • "))
            }
        }

        // 5. PEALKIRJA PUHASTUS TSÜKLIGA (Jonas Brothers fix)
        // Koorime lõpust maha kõik sulgudes plokid ükshaaval
        // Regex: leiab viimase sulgudes oleva osa, arvestab ka pesastatud sulge
        val titleEndRegex = Regex("""\s*\(([^()]+(?:\([^()]*\)[^()]*)*)\)\s*$""")

        while (true) {
            val match = titleEndRegex.find(title) ?: break
            val content = match.groupValues[1].trim()

            // Lisame listi algusesse (index 0), et järjekord oleks loogiline
            // Nt "Lugu (A) (B)" -> leiame B, siis A. Tulemus peab olema "A • B".
            extrasList.add(0, content)

            // Eemaldame leiu pealkirjast
            title = title.substring(0, match.range.first).trim()
        }

        // 6. EXTRA LÕPLIK VORMISTUS
        // a) Ühendame kõik leitud tükid
        var extra = extrasList.filter { it.isNotBlank() }.joinToString(" • ")

        // b) "Esitaja (Pill)" vormistus -> "Esitaja • Pill"
        // Asendame kõik sulud bulletiga (v.a. soundtracki albumi infos, kus aasta on oluline)
        if (extra.isNotBlank() && !isSoundtrackFormat) {
            extra = extra.replace(Regex("""\s*\(([^()]+)\)"""), " • $1")
        }

        // 7. REVERSED STATIONS (Star FM)
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
        val uniqueId = "Raadio_${(title + artist).hashCode()}"

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