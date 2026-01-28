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
        val cleaned = rawMetadata.trim()

        // 1. Tühja info käsitlemine
        if (cleaned.isEmpty() || cleaned == "-" || cleaned == "." || cleaned == " -") {
            return ParsedMetadata(
                artist = context.getString(R.string.live_broadcast),
                title = stationName,
                extra = ""
            )
        }

        // Eemaldame trailing sulgudes osa, kui see on olemas
        val titleWithExtraRegex = Regex("""^(.*?)\s*(\([^()]+?\))\s*$""")

        val match = titleWithExtraRegex.matchEntire(cleaned)

        val mainPart = match?.groupValues?.get(1)?.trim() ?: cleaned
        var extra = match?.groupValues?.get(2)?.trim() ?: ""

        // Jagame põhiosa artist-titleks
        val parts = mainPart.split(" - ", limit = 3)
        var artist: String
        var title: String

        if (parts.size >= 2) {
            val isReversed = AppConfig.Metadata.REVERSED_STATIONS.contains(stationName)

            val part1 = parts[0].trim()
            val part2 = parts[1].trim()

            if (isReversed) {
                artist = part2
                val rawTitle = part1
                title = if (rawTitle.equals(artist, ignoreCase = true) || rawTitle.isBlank()) stationName else rawTitle
            } else {
                artist = part1
                val rawTitle = part2
                title = if (rawTitle.equals(artist, ignoreCase = true) || rawTitle.isBlank()) stationName else rawTitle
            }

            // Kui oli kolmas osa (harva, aga võimalik), lisame extra hulka
            if (parts.size == 3) {
                extra = parts[2].trim().let {
                    if (extra.isNotBlank()) "$extra • $it" else it
                }
            }
        } else {
            // Ainult üks tükk → artist = tekst, title = jaama nimi
            artist = cleaned
            title = stationName
        }

        // Väike puhastus extra jaoks (kui soovid)
        extra = extra
            .removePrefix("(")
            .removeSuffix(")")
            .trim()

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