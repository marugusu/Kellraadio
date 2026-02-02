package ee.minu.kellraadio.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ee.minu.kellraadio.AlarmUtils
import ee.minu.kellraadio.R
import ee.minu.kellraadio.RadioStation
import ee.minu.kellraadio.SongAdditionalInfo
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
    onInfoClick: () -> Unit,
    onPlayPause: () -> Unit,
    onPlayStation: (RadioStation) -> Unit,
    onSleepClick: () -> Unit,
    onAlarmClick: () -> Unit,
    onAlarmLongClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    val artistColor = if (isPlaying) MaterialTheme.colorScheme.primary else Color.Gray
    val titleColor = if (isPlaying) MaterialTheme.colorScheme.secondary else Color.Gray
    val extraColor = if (isPlaying) MaterialTheme.colorScheme.onSecondary else Color.Gray

    Column(modifier = modifier) {

        // --- MONOLIIT ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 140.dp)
                .animateContentSize()
                .clip(RoundedCornerShape(12.dp))
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF252525),
                            Color.Black
                        )
                    )
                )
                .clickable(
                    enabled = songInfo != null,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onInfoClick()
                    }
                )
        ) {
            // KIHT 1: Tausta Vesimärk
            if (activeStationName.isNotEmpty()) {
                Text(
                    text = StationArtworkUtils.getStationInitials(activeStationName),
                    fontSize = 150.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White.copy(alpha = 0.05f),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Visible,
                    modifier = Modifier
                        .matchParentSize()
                        .wrapContentSize(align = Alignment.TopEnd, unbounded = true)
                        .offset(x = 30.dp, y = (-40).dp)
                )
            }

            // KIHT 2: Sisu
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (parsedTitle.isNotBlank()) {
                    Text(text = parsedTitle, style = MaterialTheme.typography.titleLarge, color = titleColor, maxLines = 4, overflow = TextOverflow.Ellipsis, lineHeight = 24.sp)
                }

                val displayName = if (parsedArtist.isNotBlank()) parsedArtist
                else if (parsedTitle.isNotBlank()) stringResource(R.string.live_broadcast)
                else if (activeStationName.isNotEmpty()) activeStationName
                else selectedStation?.name ?: stringResource(R.string.select_station)

                Text(text = displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = artistColor, maxLines = 4, overflow = TextOverflow.Ellipsis, lineHeight = 24.sp)

                if (parsedExtra.isNotBlank()) {
                    Text(text = parsedExtra, style = MaterialTheme.typography.titleMedium, color = extraColor, maxLines = 4, overflow = TextOverflow.Ellipsis, lineHeight = 24.sp)
                }

                Spacer(Modifier.height(2.dp))

                val stationPrefix = if (activeStationName.isNotEmpty()) "$activeStationName • " else ""
                val statusText = "$stationPrefix$playerStatus" + if (bitrateInfo.isNotBlank()) " • $bitrateInfo" else ""
                Text(text = statusText, style = MaterialTheme.typography.labelLarge, color = Color.Gray)

                if (alarmInfo != null || sleepTimerMillis > 0) {
                    Spacer(Modifier.height(2.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (alarmInfo != null) {
                            val cal = Calendar.getInstance().apply { timeInMillis = alarmInfo.first }
                            val context = androidx.compose.ui.platform.LocalContext.current
                            val prettyTime = AlarmUtils.getAlarmText(context, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), alarmDays)
                            val infoStr = "$prettyTime (${alarmInfo.second})"

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.AlarmOn, null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(text = infoStr, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
                            }
                        }
                        if (sleepTimerMillis > 0) {
                            val minutes = (sleepTimerMillis / 1000) / 60
                            val seconds = (sleepTimerMillis / 1000) % 60
                            val timeStr = String.format("%02d:%02d", minutes, seconds)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Bedtime, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(text = timeStr, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        val buttonShape = RoundedCornerShape(12.dp)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val buttonModifier = Modifier.weight(1f).height(64.dp)
            if (isPlaying) {
                FilledIconButton(onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onPlayPause() }, modifier = buttonModifier, shape = buttonShape) { Icon(Icons.Default.Pause, "Paus", modifier = Modifier.size(32.dp)) }
            } else {
                FilledTonalIconButton(onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); selectedStation?.let { onPlayStation(it) } }, enabled = selectedStation != null, modifier = buttonModifier, shape = buttonShape) { Icon(Icons.Default.PlayArrow, "Mängi", modifier = Modifier.size(32.dp)) }
            }
            FilledTonalIconButton(onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onToggleFavorite() }, enabled = selectedStation != null, modifier = buttonModifier, shape = buttonShape,
                colors = if (isFavorite) { IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.onSecondary, contentColor = Color.Black) } else { IconButtonDefaults.filledTonalIconButtonColors() }) { Icon(if (isFavorite) Icons.Default.Star else Icons.Outlined.StarBorder, "Lemmik", modifier = Modifier.size(28.dp)) }
            val isTimerSet = sleepTimerMillis > 0
            FilledTonalIconButton(
                onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onSleepClick() },
                enabled = isPlaying || isTimerSet,
                modifier = buttonModifier,
                shape = buttonShape,
                colors = if (isTimerSet) IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.secondary, contentColor = Color.Black) else IconButtonDefaults.filledTonalIconButtonColors()
            ) { Icon(Icons.Default.Bedtime, "Unetaimer", modifier = Modifier.size(28.dp)) }
            val isAlarmSet = alarmInfo != null
            val interactionSource = remember { MutableInteractionSource() }
            Surface(modifier = buttonModifier.clip(buttonShape).combinedClickable(interactionSource = interactionSource, indication = LocalIndication.current, onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onAlarmClick() }, onLongClick = { if (isAlarmSet) { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onAlarmLongClick() } }), shape = buttonShape, color = if (isAlarmSet) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondaryContainer, contentColor = if (isAlarmSet) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onSecondaryContainer) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) { Icon(if (isAlarmSet) Icons.Default.AlarmOn else Icons.Default.AlarmAdd, contentDescription = "Äratus", modifier = Modifier.size(28.dp)) }
            }
        }
    }
}