package app.radiorecalarm.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import app.radiorecalarm.R
import app.radiorecalarm.SongAdditionalInfo

// Avalik funktsioon, mida saavad kasutada kõik UI failid
fun getFlagEmoji(countryCode: String): String {
    if (countryCode.length != 2) return ""
    val firstLetter = Character.codePointAt(countryCode.uppercase(), 0) - 0x41 + 0x1F1E6
    val secondLetter = Character.codePointAt(countryCode.uppercase(), 1) - 0x41 + 0x1F1E6
    return String(Character.toChars(firstLetter)) + String(Character.toChars(secondLetter))
}

fun openYoutubeSearch(context: Context, query: String) {
    val encodedQuery = Uri.encode(query)
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=$encodedQuery")))
    } catch (e: Exception) {}
}

fun openSpotifySearch(context: Context, query: String) {
    val encodedQuery = Uri.encode(query)
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("spotify:search:$encodedQuery")).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://open.spotify.com/search/$encodedQuery")))
        } catch (e2: Exception) {}
    }
}

fun shareSongInfo(
    context: Context,
    artist: String,
    title: String,
    stationName: String,
    bitrate: String,
    streamUrl: String,
    info: SongAdditionalInfo
) {
    val sb = StringBuilder()
    sb.append("${context.getString(R.string.share_artist)}: $artist\n")
    sb.append("${context.getString(R.string.share_track)}: $title\n")

    if (!info.album.isNullOrEmpty()) {
        val yearSuffix = if (!info.year.isNullOrEmpty()) " (${info.year})" else ""
        sb.append("${context.getString(R.string.share_album)}: ${info.album}$yearSuffix\n")
    }

    if (!info.genre.isNullOrEmpty()) {
        sb.append("${context.getString(R.string.share_genre)}: ${info.genre}\n")
    }

    val bitrateSuffix = if (bitrate.isNotBlank()) " ($bitrate)" else ""
    sb.append("${context.getString(R.string.share_source)}: $stationName$bitrateSuffix\n")

    if (streamUrl.isNotBlank()) {
        sb.append("${context.getString(R.string.share_listen)}: $streamUrl")
    }

    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, sb.toString().trim())
    }
    context.startActivity(Intent.createChooser(intent, context.getString(R.string.share_title)))
}
