package app.radiorecalarm.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import app.radiorecalarm.R
import app.radiorecalarm.SongAdditionalInfo
import app.radiorecalarm.spotify.SaveTrackResult
import app.radiorecalarm.spotify.SpotifyRepository

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

fun openSpotifyTrack(context: Context, trackId: String?, fallbackQuery: String) {
    if (trackId.isNullOrBlank()) {
        openSpotifySearch(context, fallbackQuery)
        return
    }
    val encodedId = Uri.encode(trackId)
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("spotify:track:$encodedId")).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://open.spotify.com/track/$encodedId")))
        } catch (e2: Exception) {
            openSpotifySearch(context, fallbackQuery)
        }
    }
}

suspend fun saveTrackToSpotifyWithFeedback(context: Context, trackId: String?) {
    if (trackId.isNullOrBlank()) return
    val messageRes = when (SpotifyRepository.saveTrack(trackId)) {
        SaveTrackResult.SAVED -> R.string.spotify_toast_saved
        SaveTrackResult.ALREADY_SAVED -> R.string.spotify_toast_already_saved
        SaveTrackResult.PLAYLIST_NOT_SELECTED -> R.string.spotify_toast_playlist_missing
        SaveTrackResult.NOT_CONNECTED, SaveTrackResult.ERROR -> R.string.spotify_toast_save_failed
    }
    Toast.makeText(context, context.getString(messageRes), Toast.LENGTH_SHORT).show()
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
