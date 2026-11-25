package app.radiorecalarm.ui

import app.radiorecalarm.SongAdditionalInfo
import java.util.Locale

internal fun formatTrackDuration(durationMs: Long?): String? {
    if (durationMs == null || durationMs <= 0L) return null
    val totalSeconds = durationMs / 1000L
    if (totalSeconds <= 0L) return null
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}

internal fun buildTrackMetadataPills(info: SongAdditionalInfo): List<String> {
    val pills = mutableListOf<String>()
    info.year?.trim()?.takeIf { it.isNotEmpty() }?.let { pills.add(it) }
    info.genre?.trim()?.takeIf { it.isNotEmpty() }?.let { pills.add(it) }
    val trackPart = info.trackNumber?.takeIf { it > 0 }?.let { "#$it" }
    val durationPart = formatTrackDuration(info.durationMs)
    when {
        trackPart != null && durationPart != null -> pills.add("$trackPart • $durationPart")
        durationPart != null -> pills.add(durationPart)
        trackPart != null -> pills.add(trackPart)
    }
    return pills
}

internal fun formatSourceLabel(stationName: String, bitrate: String): String? {
    val cleanStation = stationName.trim()
    val cleanBitrate = bitrate.trim()
    return when {
        cleanStation.isNotEmpty() && cleanBitrate.isNotEmpty() -> "$cleanStation • $cleanBitrate"
        cleanStation.isNotEmpty() -> cleanStation
        cleanBitrate.isNotEmpty() -> cleanBitrate
        else -> null
    }
}

internal fun buildTeaserPrimaryText(
    info: SongAdditionalInfo?,
    artist: String,
    title: String
): String {
    val album = info?.album?.trim().orEmpty()
    if (album.isNotEmpty()) return album
    val cleanArtist = artist.trim()
    val cleanTitle = title.trim()
    return listOf(cleanArtist, cleanTitle)
        .filter { it.isNotEmpty() }
        .joinToString(" – ")
}

internal fun buildTeaserSecondaryText(
    info: SongAdditionalInfo?,
    artist: String,
    title: String,
    stationName: String
): String? {
    if (info != null) {
        val parts = buildList {
            info.year?.trim()?.takeIf { it.isNotEmpty() }?.let { add(it) }
            info.genre?.trim()?.takeIf { it.isNotEmpty() }?.let { add(it) }
            formatTrackDuration(info.durationMs)?.let { add(it) }
        }
        if (parts.isNotEmpty()) return parts.joinToString(" • ")

        if (!info.album.isNullOrBlank()) {
            val fallbackTrack = listOf(artist.trim(), title.trim())
                .filter { it.isNotEmpty() }
                .joinToString(" – ")
            if (fallbackTrack.isNotEmpty()) return fallbackTrack
        }
    }
    return stationName.trim().takeIf { it.isNotEmpty() }
}

internal fun buildLyricsWebSearchQuery(artist: String, title: String): String {
    return listOf(artist.trim(), title.trim(), "lyrics")
        .filter { it.isNotEmpty() }
        .joinToString(" ")
}
