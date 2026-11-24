@file:SuppressLint("RestrictedApi")

package app.radiorecalarm.widget

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
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
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import app.radiorecalarm.MainActivity
import app.radiorecalarm.R
import app.radiorecalarm.RadioService

class CompactWidget : GlanceAppWidget() {

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
        val extra = prefs[Prefs.extra] ?: ""
        val status = prefs[Prefs.status] ?: ""
        val bitrate = prefs[Prefs.bitrate] ?: ""
        val alarmText = prefs[Prefs.alarm] ?: ""
        val isPlaying = prefs[Prefs.isPlaying] ?: false
        val isRecording = prefs[Prefs.isRecording] ?: false
        val isLocalStream = prefs[Prefs.isLocalStream] ?: false
        val bgTransparency = prefs[Prefs.bgTransparency] ?: 0.25f
        val artworkKey = prefs[Prefs.artworkKey] ?: ""

        val size = LocalSize.current
        val layoutConfig = CompactWidgetLayoutSpec.resolve(
            widthDp = size.width.value,
            heightDp = size.height.value,
            isPlaying = isPlaying,
            isRecording = isRecording,
            isLocalStream = isLocalStream,
            hasAlarm = alarmText.isNotBlank(),
            hasStatus = status.isNotBlank()
        )
        val textLines = CompactWidgetLayoutSpec.formatTextLines(
            stationName = stationName,
            title = title,
            artist = artist,
            extra = extra,
            status = status,
            bitrate = bitrate,
            isPlaying = isPlaying,
            isRecording = isRecording,
            liveBroadcastLabel = context.getString(R.string.live_broadcast)
        )

        val padH = if (layoutConfig.tier == CompactWidgetLayoutTier.MINI_2X1) 8.dp else 10.dp
        val padV = if (layoutConfig.tier == CompactWidgetLayoutTier.MINI_2X1) 6.dp else 8.dp

        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .appWidgetBackground()
                .cornerRadius(16.dp)
                .background(ColorProvider(Color.Black.copy(alpha = bgTransparency)))
                .clickable(actionStartActivity<MainActivity>())
                .padding(horizontal = padH, vertical = padV),
            contentAlignment = Alignment.Center
        ) {
            when (layoutConfig.tier) {
                CompactWidgetLayoutTier.MINI_2X1,
                CompactWidgetLayoutTier.WIDE_BAR,
                CompactWidgetLayoutTier.FULL_BAR -> HorizontalBarLayout(
                    context = context,
                    config = layoutConfig,
                    stationName = stationName,
                    artworkKey = artworkKey,
                    textLines = textLines,
                    isPlaying = isPlaying,
                    isRecording = isRecording
                )

                CompactWidgetLayoutTier.TALL_COMPACT -> TallCompactLayout(
                    context = context,
                    config = layoutConfig,
                    stationName = stationName,
                    artworkKey = artworkKey,
                    textLines = textLines,
                    alarmText = alarmText,
                    isPlaying = isPlaying,
                    isRecording = isRecording
                )

                CompactWidgetLayoutTier.EXPANDED_CARD -> ExpandedCardLayout(
                    context = context,
                    config = layoutConfig,
                    stationName = stationName,
                    artworkKey = artworkKey,
                    textLines = textLines,
                    alarmText = alarmText,
                    isPlaying = isPlaying,
                    isRecording = isRecording
                )
            }
        }
    }

    @Composable
    private fun HorizontalBarLayout(
        context: Context,
        config: CompactWidgetLayoutConfig,
        stationName: String,
        artworkKey: String,
        textLines: CompactWidgetTextLines,
        isPlaying: Boolean,
        isRecording: Boolean
    ) {
        val titleColor = if (isPlaying || isRecording) ColorTitle else ColorDim

        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (config.showArtwork) {
                StationArtworkThumb(context, artworkKey, stationName, config.artworkSizeDp)
                Spacer(GlanceModifier.width(8.dp))
            }

            Column(
                modifier = GlanceModifier.defaultWeight(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.Start
            ) {
                StationHeaderRow(
                    stationName = stationName,
                    showRecordingDot = config.showRecordingDot,
                    showRecordingPill = false,
                    fontSizeSp = if (config.tier == CompactWidgetLayoutTier.MINI_2X1) 13 else 14
                )
                Text(
                    text = textLines.primaryLine,
                    style = TextStyle(
                        color = titleColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    maxLines = 1
                )
                if (config.showSecondaryLine && textLines.secondaryLine.isNotBlank()) {
                    Text(
                        text = textLines.secondaryLine,
                        style = TextStyle(
                            color = if (isPlaying) ColorArtist else ColorDim,
                            fontSize = 11.sp
                        ),
                        maxLines = 1
                    )
                }
            }

            Spacer(GlanceModifier.width(4.dp))
            ControlCluster(
                config = config,
                isPlaying = isPlaying,
                isRecording = isRecording
            )
        }
    }

    @Composable
    private fun TallCompactLayout(
        context: Context,
        config: CompactWidgetLayoutConfig,
        stationName: String,
        artworkKey: String,
        textLines: CompactWidgetTextLines,
        alarmText: String,
        isPlaying: Boolean,
        isRecording: Boolean
    ) {
        val titleColor = if (isPlaying || isRecording) ColorTitle else ColorDim

        Column(
            modifier = GlanceModifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (config.showArtwork) {
                    StationArtworkThumb(context, artworkKey, stationName, config.artworkSizeDp)
                    Spacer(GlanceModifier.width(8.dp))
                }
                Column(
                    modifier = GlanceModifier.defaultWeight(),
                    horizontalAlignment = Alignment.Start
                ) {
                    StationHeaderRow(
                        stationName = stationName,
                        showRecordingDot = config.showRecordingDot,
                        showRecordingPill = false,
                        fontSizeSp = 13
                    )
                    Text(
                        text = textLines.primaryLine,
                        style = TextStyle(
                            color = titleColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        maxLines = 1
                    )
                }
            }

            if (config.showSecondaryLine && textLines.secondaryLine.isNotBlank()) {
                Spacer(GlanceModifier.height(3.dp))
                Text(
                    text = textLines.secondaryLine,
                    modifier = GlanceModifier.fillMaxWidth(),
                    style = TextStyle(
                        color = if (isPlaying) ColorArtist else ColorDim,
                        fontSize = 11.sp
                    ),
                    maxLines = 1
                )
            }

            if (config.showAlarmLine && alarmText.isNotBlank()) {
                Spacer(GlanceModifier.height(4.dp))
                AlarmLineRow(alarmText)
            }

            Spacer(GlanceModifier.height(6.dp))
            ControlCluster(
                config = config,
                isPlaying = isPlaying,
                isRecording = isRecording
            )
        }
    }

    @Composable
    private fun ExpandedCardLayout(
        context: Context,
        config: CompactWidgetLayoutConfig,
        stationName: String,
        artworkKey: String,
        textLines: CompactWidgetTextLines,
        alarmText: String,
        isPlaying: Boolean,
        isRecording: Boolean
    ) {
        val titleColor = if (isPlaying || isRecording) ColorTitle else ColorDim

        Column(
            modifier = GlanceModifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StationArtworkThumb(context, artworkKey, stationName, config.artworkSizeDp)
                Spacer(GlanceModifier.width(10.dp))
                Column(
                    modifier = GlanceModifier.defaultWeight(),
                    horizontalAlignment = Alignment.Start
                ) {
                    StationHeaderRow(
                        stationName = stationName,
                        showRecordingDot = false,
                        showRecordingPill = isRecording && !config.showRecordButton,
                        fontSizeSp = 15
                    )
                    if (config.showStatusLine && textLines.statusLine.isNotBlank()) {
                        Text(
                            text = textLines.statusLine,
                            style = TextStyle(color = ColorStatus, fontSize = 11.sp),
                            maxLines = 1
                        )
                    }
                    Text(
                        text = textLines.primaryLine,
                        style = TextStyle(
                            color = titleColor,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        maxLines = config.titleMaxLines
                    )
                    if (config.showSecondaryLine && textLines.secondaryLine.isNotBlank()) {
                        Text(
                            text = textLines.secondaryLine,
                            style = TextStyle(
                                color = if (isPlaying) ColorArtist else ColorDim,
                                fontSize = 12.sp
                            ),
                            maxLines = 1
                        )
                    }
                }
            }

            if (config.showAlarmLine && alarmText.isNotBlank()) {
                Spacer(GlanceModifier.height(6.dp))
                AlarmLineRow(alarmText)
            }

            Spacer(GlanceModifier.height(6.dp))
            ControlCluster(
                config = config,
                isPlaying = isPlaying,
                isRecording = isRecording
            )
        }
    }

    @Composable
    private fun StationArtworkThumb(
        context: Context,
        artworkKey: String,
        stationName: String,
        sizeDp: Int
    ) {
        val bitmap = WidgetArtworkCache.getCachedOrFallback(context, artworkKey, stationName)
        val radius = if (sizeDp >= 52) 12.dp else 8.dp
        Image(
            provider = ImageProvider(bitmap),
            contentDescription = stationName,
            contentScale = ContentScale.Crop,
            modifier = GlanceModifier
                .size(sizeDp.dp)
                .cornerRadius(radius)
        )
    }

    @Composable
    private fun StationHeaderRow(
        stationName: String,
        showRecordingDot: Boolean,
        showRecordingPill: Boolean,
        fontSizeSp: Int
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (showRecordingDot) {
                Box(
                    modifier = GlanceModifier
                        .size(7.dp)
                        .cornerRadius(4.dp)
                        .background(ColorRecord)
                ) {}
                Spacer(GlanceModifier.width(5.dp))
            }
            Text(
                text = stationName,
                style = TextStyle(
                    color = ColorStation,
                    fontWeight = FontWeight.Bold,
                    fontSize = fontSizeSp.sp
                ),
                maxLines = 1
            )
            if (showRecordingPill) {
                Spacer(GlanceModifier.width(6.dp))
                Box(
                    modifier = GlanceModifier
                        .cornerRadius(4.dp)
                        .background(ColorRecord)
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = "REC",
                        style = TextStyle(
                            color = ColorProvider(Color.White),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 1
                    )
                }
            }
        }
    }

    @Composable
    private fun AlarmLineRow(alarmText: String) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                provider = ImageProvider(android.R.drawable.ic_lock_idle_alarm),
                contentDescription = "Alarm",
                modifier = GlanceModifier.size(13.dp),
                colorFilter = ColorFilter.tint(ColorAlarm)
            )
            Spacer(GlanceModifier.width(6.dp))
            Text(
                text = alarmText,
                style = TextStyle(color = ColorAlarm, fontSize = 12.sp),
                maxLines = 1
            )
        }
    }

    @Composable
    private fun ControlCluster(
        config: CompactWidgetLayoutConfig,
        isPlaying: Boolean,
        isRecording: Boolean
    ) {
        val playIcon = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow
        val whiteFilter = ColorFilter.tint(ColorProvider(Color.White))
        val btnSize = config.controlButtonSizeDp.dp
        val iconSize = config.controlIconSizeDp.dp

        Row(verticalAlignment = Alignment.CenterVertically) {
            if (config.showPrevNextButtons) {
                Box(
                    modifier = GlanceModifier.size(btnSize).clickable(actionRunCallback<PrevAction>()),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        provider = ImageProvider(R.drawable.ic_skip_previous),
                        contentDescription = "Eelmine",
                        modifier = GlanceModifier.size(iconSize),
                        colorFilter = whiteFilter
                    )
                }
            }

            Box(
                modifier = GlanceModifier.size(btnSize).clickable(actionRunCallback<PlayPauseAction>()),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    provider = ImageProvider(playIcon),
                    contentDescription = "Play",
                    modifier = GlanceModifier.size((config.controlIconSizeDp + 4).dp),
                    colorFilter = whiteFilter
                )
            }

            if (config.showPrevNextButtons) {
                Box(
                    modifier = GlanceModifier.size(btnSize).clickable(actionRunCallback<NextAction>()),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        provider = ImageProvider(R.drawable.ic_skip_next),
                        contentDescription = "Järgmine",
                        modifier = GlanceModifier.size(iconSize),
                        colorFilter = whiteFilter
                    )
                }
            }

            if (config.showRecordButton) {
                val recIcon = if (isRecording) R.drawable.ic_record_stop else R.drawable.ic_record_start
                val recTint = if (isRecording) ColorRecord else ColorRecIdle
                Box(
                    modifier = GlanceModifier.size(btnSize).clickable(actionRunCallback<ToggleRecordAction>()),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        provider = ImageProvider(recIcon),
                        contentDescription = "Salvesta",
                        modifier = GlanceModifier.size((config.controlIconSizeDp - 2).dp),
                        colorFilter = ColorFilter.tint(recTint)
                    )
                }
            }
        }
    }

    object Prefs {
        val stationName = stringPreferencesKey("station_name")
        val title = stringPreferencesKey("title")
        val artist = stringPreferencesKey("artist")
        val extra = stringPreferencesKey("extra")
        val status = stringPreferencesKey("status")
        val bitrate = stringPreferencesKey("bitrate")
        val alarm = stringPreferencesKey("alarm")
        val isPlaying = booleanPreferencesKey("is_playing")
        val isRecording = booleanPreferencesKey("is_recording")
        val isLocalStream = booleanPreferencesKey("is_local_stream")
        val bgTransparency = floatPreferencesKey("bg_transparency")
        val artworkKey = stringPreferencesKey("artwork_key")
    }

    companion object {
        private val ColorDim = ColorProvider(Color(0xFF94A3B8))
        private val ColorStatus = ColorProvider(Color(0xFFFFA726))
        private val ColorStation = ColorProvider(Color(0xFFFFA726))
        private val ColorTitle = ColorProvider(Color(0xFFF8FAFC))
        private val ColorArtist = ColorProvider(Color(0xFFFFB74D))
        private val ColorAlarm = ColorProvider(Color(0xFFFF8A65))
        private val ColorRecord = ColorProvider(Color(0xFFEF4444))
        private val ColorRecIdle = ColorProvider(Color(0xFFCBD5E1))
    }
}

class ToggleRecordAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val intent = Intent(context, RadioService::class.java).apply {
            action = RadioService.ACTION_TOGGLE_RECORDING
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }
}
