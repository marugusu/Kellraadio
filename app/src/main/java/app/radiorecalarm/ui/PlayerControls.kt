package app.radiorecalarm.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.radiorecalarm.AlarmUtils
import app.radiorecalarm.R
import app.radiorecalarm.RadioStation
import app.radiorecalarm.SongAdditionalInfo
import app.radiorecalarm.ui.theme.KellraadioTheme
import java.util.Calendar

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlayerControls(
    selectedStation: RadioStation?,
    activeStationName: String,
    isPlaying: Boolean,
    parsedTitle: String,
    parsedArtist: String,
    parsedExtra: String,
    playerStatus: String,
    bitrateInfo: String,
    alarmInfo: Pair<Long, String>?,
    alarmDays: Set<Int>,
    sleepTimerMillis: Long,
    isFavorite: Boolean,
    songInfo: SongAdditionalInfo?,
    isRecording: Boolean,
    recordingDuration: Long,
    recordingCount: Int = 0,
    onInfoClick: () -> Unit,
    onPlayPause: () -> Unit,
    onPlayStation: (RadioStation) -> Unit,
    onSleepClick: () -> Unit,
    onAlarmClick: () -> Unit,
    onAlarmLongClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onRecordClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val view = LocalView.current

    val titleColor = if (isPlaying) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    val artistColor = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    val extraColor = if (isPlaying) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)

    Column(modifier = modifier) {

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 104.dp)
                .animateContentSize()
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surface,
                            Color(0xFF0C0E14)
                        )
                    )
                )
                .clickable(
                    enabled = songInfo != null,
                    onClick = {
                        view.performHapticTap()
                        onInfoClick()
                    }
                )
        ) {
            if (activeStationName.isNotEmpty()) {
                Text(
                    text = StationArtworkUtils.getStationInitials(activeStationName),
                    fontSize = 150.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
                    maxLines = 1, softWrap = false, overflow = TextOverflow.Visible,
                    modifier = Modifier
                        .matchParentSize()
                        .wrapContentSize(align = Alignment.TopEnd, unbounded = true)
                        .offset(x = 30.dp, y = (-40).dp)
                )
            }

            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (parsedTitle.isNotBlank()) {
                        Text(
                            text = parsedTitle,
                            style = MaterialTheme.typography.titleLarge,
                            color = titleColor,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = 24.sp
                        )
                    }

                    val displayName = if (parsedArtist.isNotBlank()) parsedArtist
                    else if (parsedTitle.isNotBlank()) stringResource(R.string.live_broadcast)
                    else if (activeStationName.isNotEmpty()) activeStationName
                    else selectedStation?.name ?: stringResource(R.string.select_station)

                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = artistColor,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 24.sp
                    )

                    if (parsedExtra.isNotBlank()) {
                        Text(
                            text = parsedExtra,
                            style = MaterialTheme.typography.titleMedium,
                            color = extraColor,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = 24.sp
                        )
                    }
                }

                val stationPrefix = if (activeStationName.isNotEmpty()) "$activeStationName • " else ""
                var statusText = "$stationPrefix$playerStatus" + if (bitrateInfo.isNotBlank()) " • $bitrateInfo" else ""
                if (isRecording) {
                    val context = androidx.compose.ui.platform.LocalContext.current
                    val recMin = (recordingDuration / 1000) / 60
                    val recSec = (recordingDuration / 1000) % 60
                    val recTimeStr = String.format("%02d:%02d", recMin, recSec)
                    val countSuffix = if (recordingCount > 0) " " + context.getString(R.string.recording_saved_count, recordingCount) else ""
                    statusText += " • REC $recTimeStr$countSuffix"
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val activeIconUrl = selectedStation?.iconUrl.orEmpty()
                    if (selectedStation != null || activeStationName.isNotEmpty()) {
                        StationLogoBadge(
                            stationName = activeStationName.ifEmpty { selectedStation?.name.orEmpty() },
                            iconUrl = activeIconUrl,
                            size = 20.dp,
                            cornerRadius = 4.dp,
                            innerPadding = 1.dp,
                            borderWidth = 0.5.dp,
                            borderColor = if (isPlaying) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
                            } else {
                                MaterialTheme.colorScheme.outline
                            },
                            fallbackBackground = MaterialTheme.colorScheme.surfaceVariant,
                            fallbackTextColor = if (isPlaying) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            fallbackFontSize = 7.sp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    if (isRecording) {
                        val infiniteTransition = rememberInfiniteTransition(label = "rec_dot")
                        val alpha by infiniteTransition.animateFloat(
                            initialValue = 0.2f,
                            targetValue = 1f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(500, easing = LinearEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "rec_dot_alpha"
                        )
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .background(MaterialTheme.colorScheme.error.copy(alpha = alpha))
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (songInfo != null) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }


                if (alarmInfo != null || sleepTimerMillis > 0) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (alarmInfo != null) {
                            val cal = Calendar.getInstance().apply { timeInMillis = alarmInfo.first }
                            val context = androidx.compose.ui.platform.LocalContext.current
                            val prettyTime = AlarmUtils.getAlarmText(context, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), alarmDays)
                            val infoStr = "$prettyTime (${alarmInfo.second})"

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.AlarmOn, null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(text = infoStr, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
                            }
                        }
                        if (sleepTimerMillis > 0) {
                            val minutes = (sleepTimerMillis / 1000) / 60
                            val seconds = (sleepTimerMillis / 1000) % 60
                            val timeStr = String.format("%02d:%02d", minutes, seconds)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Bedtime, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(text = timeStr, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                            }
                        }
                    }
                }
            }
        }

        // Gap 1: Nüüd täpselt 12dp
        Spacer(modifier = Modifier.height(12.dp))
        val buttonShape = RoundedCornerShape(12.dp)
        val buttonHeight = 54.dp
        val isTimerSet = sleepTimerMillis > 0
        val isAlarmSet = alarmInfo != null

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. Favorite (vasakul)
            PlayerActionTile(
                icon = if (isFavorite) Icons.Default.Star else Icons.Outlined.StarBorder,
                contentDescription = null,
                enabled = selectedStation != null,
                isActive = isFavorite,
                activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                activeContentColor = MaterialTheme.colorScheme.onSecondary,
                activeBorderColor = MaterialTheme.colorScheme.onSecondary.copy(alpha = 0.65f),
                shape = buttonShape,
                modifier = Modifier.weight(1f).height(buttonHeight),
                onClick = { view.performHapticTap(); onToggleFavorite() }
            )

            // 2. Record (Play kõrval vasakul)
            PlayerActionTile(
                icon = if (isRecording) Icons.Default.Stop else Icons.Default.FiberManualRecord,
                contentDescription = stringResource(R.string.action_record),
                enabled = isPlaying && selectedStation != null,
                isActive = isRecording,
                activeContainerColor = MaterialTheme.colorScheme.error,
                activeContentColor = Color.White,
                activeBorderColor = MaterialTheme.colorScheme.error,
                inactiveContentColor = MaterialTheme.colorScheme.error,
                shape = buttonShape,
                modifier = Modifier.weight(1f).height(buttonHeight),
                onClick = { view.performHapticTap(); onRecordClick() }
            )

            // 3. Play / Pause (keskmine laiem põhinupp)
            PlayerActionTile(
                icon = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = stringResource(if (isPlaying) R.string.action_pause else R.string.action_play),
                enabled = isPlaying || selectedStation != null,
                isActive = isPlaying,
                activeContainerColor = MaterialTheme.colorScheme.primary,
                activeContentColor = Color.Black,
                activeBorderColor = MaterialTheme.colorScheme.primary,
                inactiveContentColor = MaterialTheme.colorScheme.primary,
                inactiveBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                inactiveBorderWidth = 1.5.dp,
                iconSize = 30.dp,
                shape = buttonShape,
                modifier = Modifier.weight(1.4f).height(buttonHeight),
                onClick = {
                    view.performHapticTap()
                    if (isPlaying) onPlayPause() else selectedStation?.let { onPlayStation(it) }
                }
            )

            // 4. Sleep timer (paremal)
            PlayerActionTile(
                icon = Icons.Default.Bedtime,
                contentDescription = stringResource(R.string.timer_dialog_title),
                enabled = isPlaying || isTimerSet,
                isActive = isTimerSet,
                activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                activeContentColor = MaterialTheme.colorScheme.secondary,
                activeBorderColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.65f),
                shape = buttonShape,
                modifier = Modifier.weight(1f).height(buttonHeight),
                onClick = { view.performHapticTap(); onSleepClick() }
            )

            // 5. Alarm (äärmine parem)
            PlayerActionTile(
                icon = if (isAlarmSet) Icons.Default.AlarmOn else Icons.Default.AlarmAdd,
                contentDescription = stringResource(R.string.alarm_title),
                enabled = true,
                isActive = isAlarmSet,
                activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                activeContentColor = MaterialTheme.colorScheme.tertiary,
                activeBorderColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.65f),
                shape = buttonShape,
                modifier = Modifier.weight(1f).height(buttonHeight),
                onClick = { view.performHapticTap(); onAlarmClick() },
                onLongClick = if (isAlarmSet) {
                    {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onAlarmLongClick()
                    }
                } else null
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlayerActionTile(
    icon: ImageVector,
    contentDescription: String?,
    enabled: Boolean,
    isActive: Boolean,
    activeContainerColor: Color,
    activeContentColor: Color,
    activeBorderColor: Color,
    shape: Shape,
    modifier: Modifier = Modifier,
    inactiveContainerColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    inactiveContentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    inactiveBorderColor: Color = MaterialTheme.colorScheme.outline,
    inactiveBorderWidth: Dp = 1.dp,
    iconSize: Dp = 26.dp,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    val containerColor = when {
        isActive -> activeContainerColor
        enabled -> inactiveContainerColor
        else -> inactiveContainerColor.copy(alpha = 0.5f)
    }
    val contentColor = when {
        isActive -> activeContentColor
        enabled -> inactiveContentColor
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
    }
    val borderColor = when {
        isActive -> activeBorderColor
        enabled -> inactiveBorderColor
        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
    }
    val borderWidth = if (isActive) 1.5.dp else if (enabled) inactiveBorderWidth else 1.dp
    val interactionSource = remember { MutableInteractionSource() }

    Surface(
        modifier = modifier
            .clip(shape)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                enabled = enabled,
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = shape,
        border = BorderStroke(borderWidth, borderColor),
        color = containerColor,
        contentColor = contentColor
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(iconSize)
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0C10)
@Composable
private fun PlayerControlsPlayingPreview() {
    KellraadioTheme {
        PlayerControls(
            selectedStation = RadioStation(
                id = 1,
                name = "Vikerraadio",
                url = "",
                countryCode = "EE",
                category = "ERR",
                isFavorite = true
            ),
            activeStationName = "Vikerraadio",
            isPlaying = true,
            parsedTitle = "Mirko Ojakivi, Arp Müller",
            parsedArtist = "Stuudios on peaminister",
            parsedExtra = "",
            playerStatus = "Playing",
            bitrateInfo = "320 kbps",
            alarmInfo = System.currentTimeMillis() to "Vikerraadio",
            alarmDays = setOf(Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY),
            sleepTimerMillis = 0L,
            isFavorite = true,
            songInfo = SongAdditionalInfo(),
            isRecording = false,
            recordingDuration = 0L,
            onInfoClick = {},
            onPlayPause = {},
            onPlayStation = {},
            onSleepClick = {},
            onAlarmClick = {},
            onAlarmLongClick = {},
            onToggleFavorite = {},
            onRecordClick = {},
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0C10)
@Composable
private fun PlayerControlsPausedPreview() {
    KellraadioTheme {
        PlayerControls(
            selectedStation = RadioStation(
                id = 1,
                name = "Rock FM",
                url = "",
                countryCode = "EE",
                category = "Sky"
            ),
            activeStationName = "Rock FM",
            isPlaying = false,
            parsedTitle = "",
            parsedArtist = "",
            parsedExtra = "",
            playerStatus = "Paused",
            bitrateInfo = "",
            alarmInfo = null,
            alarmDays = emptySet(),
            sleepTimerMillis = 0L,
            isFavorite = false,
            songInfo = null,
            isRecording = false,
            recordingDuration = 0L,
            onInfoClick = {},
            onPlayPause = {},
            onPlayStation = {},
            onSleepClick = {},
            onAlarmClick = {},
            onAlarmLongClick = {},
            onToggleFavorite = {},
            onRecordClick = {},
            modifier = Modifier.padding(16.dp)
        )
    }
}
