@file:SuppressLint("RestrictedApi")
package ws.ct.radiowakes.widget

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.glance.layout.*
import ws.ct.radiowakes.MainActivity
import ws.ct.radiowakes.R
import ws.ct.radiowakes.RadioService

class HomeWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme {
                WidgetContent(context)
            }
        }
    }

    @Composable
    private fun WidgetContent(context: Context) {
        val prefs = currentState<Preferences>()
        val stationName = prefs[Prefs.stationName] ?: context.getString(R.string.app_name)
        val title = prefs[Prefs.title] ?: ""
        val artist = prefs[Prefs.artist] ?: ""

        val combinedStatus = prefs[Prefs.status] ?: ""
        val parts = combinedStatus.split(" • ")
        val statusText = if (parts.isNotEmpty()) parts.last() else ""
        val bitrate = prefs[Prefs.bitrate] ?: ""
        val extraText = if (parts.size > 1) parts.dropLast(1).joinToString(" • ") else ""

        val alarmText = prefs[Prefs.alarm] ?: ""

        val isPlaying = prefs[Prefs.isPlaying] ?: false
        val size = LocalSize.current
        val isNarrow = true

        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(ColorProvider(Color.Black.copy(alpha = 0.25f)))
                .padding(8.dp)
                .clickable(actionStartActivity<MainActivity>())
        ) {
            if (isNarrow) {
                Column(
                    modifier = GlanceModifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = GlanceModifier.defaultWeight().fillMaxWidth(),
                        verticalAlignment = Alignment.Top,
                        horizontalAlignment = Alignment.Start
                    ) {
                        AllInfoText(
                            stationName,
                            title,
                            artist,
                            extraText,
                            if (bitrate.isNotBlank()) "$statusText • $bitrate" else statusText,
                            alarmText,
                            centered = false,
                            isPlaying = isPlaying
                        )
                    }
                    Spacer(GlanceModifier.height(4.dp))
                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ControlButtons(isPlaying)
                    }
                }
            } else {
                Row(
                    modifier = GlanceModifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AllInfoText(
                            stationName,
                            title,
                            artist,
                            extraText,
                            if (bitrate.isNotBlank()) "$statusText • $bitrate" else statusText,
                            alarmText,
                            centered = false
                        )
                    }
                    Spacer(GlanceModifier.width(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ControlButtons(isPlaying)
                    }
                }
            }
        }
    }

    @Composable
    private fun AllInfoText(station: String, title: String, artist: String, extra: String, status: String, alarm: String, centered: Boolean, isPlaying: Boolean = false) {
        val textAlign = if (centered) TextAlign.Center else TextAlign.Start

        val colorDim     = ColorProvider(Color(0xFFB0B0B0))
        val colorStatus = ColorProvider(Color(0xFFEADDFF))
        val colorStation = ColorProvider(Color(0xFFEADDFF))
        val colorTitle = if (isPlaying) ColorProvider(Color(0xFF03DAC6)) else colorDim
        val colorExtra   = if (isPlaying) ColorProvider(Color(0xFFCB7E1F)) else colorDim
        val colorText    = if (isPlaying)  ColorProvider(Color(0xFFBB86FC)) else colorDim
        val colorAlarm   = ColorProvider(Color(0xFFFE7879))

        Column(
            modifier = GlanceModifier.fillMaxHeight(),
            horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start
        ) {

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start
            ) {
                Image(
                    provider = ImageProvider(R.drawable.ic_radio_notification),
                    contentDescription = null,
                    modifier = GlanceModifier.size(16.dp),
                    colorFilter = ColorFilter.tint(colorStation)
                )
                Spacer(GlanceModifier.width(6.dp))
                Text(
                    text = station,
                    style = TextStyle(
                        color = colorStation,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        textAlign = textAlign
                    ),
                    maxLines = 1
                )
            }

            val displayTitle = if (title.isBlank() && artist.isBlank()) "..." else title
            if (displayTitle.isNotBlank()) {
                Text(
                    text = displayTitle,
                    modifier = GlanceModifier.padding(start = 22.dp),
                    style = TextStyle(color = colorTitle, fontSize = 14.sp, fontWeight = FontWeight.Medium, textAlign = textAlign),
                    maxLines = 1
                )
            }

            if (artist.isNotBlank() && artist != "Otseeeter") {
                Text(
                    text = artist,
                    modifier = GlanceModifier.padding(start = 22.dp),
                    style = TextStyle(color = colorText, fontSize = 13.sp, textAlign = textAlign),
                    maxLines = 1
                )
            }

            if (extra.isNotBlank()) {
                Text(
                    text = extra,
                    modifier = GlanceModifier.padding(start = 22.dp),
                    style = TextStyle(color = colorExtra, fontSize = 12.sp, textAlign = textAlign),
                    maxLines = 1
                )
            }

            if (status.isNotBlank()) {
                Text(
                    text = status,
                    modifier = GlanceModifier.padding(start = 22.dp),
                    style = TextStyle(color = colorStatus, fontSize = 11.sp, textAlign = textAlign),
                    maxLines = 1
                )
            }

            Spacer(modifier = GlanceModifier.defaultWeight())

            if (alarm.isNotBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        provider = ImageProvider(android.R.drawable.ic_lock_idle_alarm),
                        contentDescription = "Alarm",
                        modifier = GlanceModifier.size(14.dp),
                        colorFilter = ColorFilter.tint(colorAlarm)
                    )
                    Spacer(GlanceModifier.width(8.dp))
                    Text(
                        text = alarm,
                        style = TextStyle(color = colorAlarm, fontSize = 13.sp),
                        maxLines = 1
                    )
                }
            }
        }
    }

    @Composable
    private fun ControlButtons(isPlaying: Boolean) {
        val playIcon = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow
        val whiteFilter = ColorFilter.tint(ColorProvider(Color.White))

        Box(
            modifier = GlanceModifier.size(48.dp).clickable(actionRunCallback<PrevAction>()),
            contentAlignment = Alignment.Center
        ) {
            Image(
                provider = ImageProvider(R.drawable.ic_skip_previous),
                contentDescription = "Eelmine",
                modifier = GlanceModifier.size(32.dp),
                colorFilter = whiteFilter
            )
        }

        Box(
            modifier = GlanceModifier.size(48.dp).clickable(actionRunCallback<PlayPauseAction>()),
            contentAlignment = Alignment.Center
        ) {
            Image(
                provider = ImageProvider(playIcon),
                contentDescription = "Play",
                modifier = GlanceModifier.size(40.dp),
                colorFilter = whiteFilter
            )
        }

        Box(
            modifier = GlanceModifier.size(48.dp).clickable(actionRunCallback<NextAction>()),
            contentAlignment = Alignment.Center
        ) {
            Image(
                provider = ImageProvider(R.drawable.ic_skip_next),
                contentDescription = "Järgmine",
                modifier = GlanceModifier.size(32.dp),
                colorFilter = whiteFilter
            )
        }
    }


    object Prefs {
        val stationName = stringPreferencesKey("station_name")
        val title = stringPreferencesKey("title")
        val artist = stringPreferencesKey("artist")
        val status = stringPreferencesKey("status")
        val bitrate = stringPreferencesKey("bitrate")
        val alarm = stringPreferencesKey("alarm")
        val isPlaying = booleanPreferencesKey("is_playing")
    }
}

class PrevAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val intent = android.content.Intent(context, RadioService::class.java).apply {
            action = RadioService.ACTION_SKIP_PREVIOUS
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent)
    }
}

class NextAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val intent = android.content.Intent(context, RadioService::class.java).apply {
            action = RadioService.ACTION_SKIP_NEXT
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent)
    }
}

class PlayPauseAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val intent = android.content.Intent(context, RadioService::class.java).apply {
            action = RadioService.ACTION_PLAY_PAUSE_TOGGLE
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent)
    }
}