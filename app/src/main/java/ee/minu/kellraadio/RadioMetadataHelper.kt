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
        val cleaned = rawMetadata.trim().replace("\n", " - ")  // reavahetused -> " - "

        if (cleaned.isEmpty() || cleaned == "-" || cleaned == "." || cleaned == " -") {
            return ParsedMetadata(context.getString(R.string.live_broadcast), stationName, "")
        }

        val parts = cleaned.split(" - ", limit = 4)
            .map { it.trim() }
            .filter { it.isNotBlank() }

        if (parts.size < 2) {
            return ParsedMetadata(parts.getOrNull(0) ?: cleaned, stationName, "")
        }

        // STANDARD: esimene = artist, teine = title
        var artist = parts[0]
        var title = parts[1]

        var extra = ""
        if (parts.size >= 3) {
            val potentialExtra = parts[2]

            // Kui on "Esitaja (instrument)"
            val instrRegex = Regex("""^(.*?)\s*\(([^()]+)\)\s*$""")
            val match = instrRegex.matchEntire(potentialExtra)

            if (match != null) {
                extra = match.groupValues[2].trim()               // instrument
                val performer = match.groupValues[1].trim()
                if (performer.isNotBlank()) {
                    extra = "$performer • $extra"
                }
            } else {
                extra = potentialExtra
            }
        }

        // Neljas osa (kui on) → extra lõppu
        if (parts.size >= 4) {
            val add = parts[3].trim()
            extra = if (extra.isNotBlank()) "$extra • $add" else add
        }

        // Pealkirjast eemalda trailing (album/film info)
        val titleExtraRegex = Regex("""^(.*?)\s*\(([^()"]+(?:"[^"]*"[^()"]*)*)\)\s*$""")
        val titleMatch = titleExtraRegex.matchEntire(title)
        if (titleMatch != null) {
            title = titleMatch.groupValues[1].trim()
            val titleExtra = titleMatch.groupValues[2].trim()
            extra = if (extra.isNotBlank()) "$titleExtra • $extra" else titleExtra
        }

        // reversed jaamade jaoks (kui vaja) – lisa tagasi sinu vana loogika
        val isReversed = AppConfig.Metadata.REVERSED_STATIONS.contains(stationName)
        if (isReversed) {
            artist = parts[1]
            title = parts[0]
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