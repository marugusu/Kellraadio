package ee.minu.kellraadio.widget

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
import ee.minu.kellraadio.MainActivity
import ee.minu.kellraadio.R
import ee.minu.kellraadio.RadioService

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

        // PARANDUS: Tükeldame 'status' välja, et saada kätte Extra info ja Staatus eraldi
        val combinedStatus = prefs[Prefs.status] ?: ""
        val parts = combinedStatus.split(" • ")
        val statusText = if (parts.isNotEmpty()) parts.last() else ""
        val extraText = if (parts.size > 1) parts.dropLast(1).joinToString(" • ") else ""

        val isPlaying = prefs[Prefs.isPlaying] ?: false

        val size = LocalSize.current
        //val isNarrow = size.width < 260.dp
        val isNarrow = true

        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(ColorProvider(Color.Black.copy(alpha = 0.7f)))
                .padding(8.dp)
                .clickable(actionStartActivity<MainActivity>())
        ) {
            if (isNarrow) {
                // PÜSTINE PAIGUTUS (2x2)
                Column(
                    modifier = GlanceModifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = GlanceModifier.defaultWeight().fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Siin saadame nüüd eraldi extraText ja statusText
                        AllInfoText(stationName, title, artist, extraText, statusText, centered = true)
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
                // RÕHTNE PAIGUTUS (4x1)
                Row(
                    modifier = GlanceModifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Siin saadame nüüd eraldi extraText ja statusText
                        AllInfoText(stationName, title, artist, extraText, statusText, centered = false)
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
    private fun AllInfoText(station: String, title: String, artist: String, extra: String, status: String, centered: Boolean) {
        val textAlign = if (centered) TextAlign.Center else TextAlign.Start

        // SINU ÄPI VÄRVID
        val colorStation = ColorProvider(Color(0xFFEADDFF))
        val colorTitle   = ColorProvider(Color(0xFF03DAC6))
        val colorExtra   = ColorProvider(Color(0xFFCB7E1F))
        val colorText    = ColorProvider(Color(0xFFBB86FC))
        val colorDim     = ColorProvider(Color(0xFFB0B0B0))

        // 1. JAAM
        Text(
            text = station,
            style = TextStyle(color = colorStation, fontWeight = FontWeight.Bold, fontSize = 16.sp, textAlign = textAlign),
            maxLines = 1
        )

        // 2. PEALKIRI
        val displayTitle = if (title.isBlank() && artist.isBlank()) "..." else title
        if (displayTitle.isNotBlank()) {
            Text(
                text = displayTitle,
                style = TextStyle(color = colorTitle, fontSize = 15.sp, fontWeight = FontWeight.Medium, textAlign = textAlign),
                maxLines = 1
            )
        }

        // 3. ARTIST
        if (artist.isNotBlank() && artist != "Otseeeter") {
            Text(
                text = artist,
                style = TextStyle(color = colorText, fontSize = 14.sp, textAlign = textAlign),
                maxLines = 1
            )
        }

        // 4. EXTRA (UUS RIDA)
        if (extra.isNotBlank()) {
            Text(
                text = extra,
                style = TextStyle(color = colorExtra, fontSize = 13.sp, textAlign = textAlign),
                maxLines = 1
            )
        }

        // 5. STAATUS (UUS RIDA)
        if (status.isNotBlank()) {
            Text(
                text = status,
                style = TextStyle(color = colorDim, fontSize = 12.sp, textAlign = textAlign),
                maxLines = 1
            )
        }
    }

    @Composable
    private fun ControlButtons(isPlaying: Boolean) {
        val playIcon = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow
        val whiteFilter = ColorFilter.tint(ColorProvider(Color.White))

        // EELMINE
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

        // PLAY/PAUSE
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

        // JÄRGMINE
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
        val isPlaying = booleanPreferencesKey("is_playing")
    }
}

// ACTIONID
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