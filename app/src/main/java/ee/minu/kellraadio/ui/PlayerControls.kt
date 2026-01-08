package ee.minu.kellraadio.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ee.minu.kellraadio.AlarmUtils
import ee.minu.kellraadio.RadioStation

@Composable
fun PlayerControls(
    selectedStation: RadioStation?,
    activeStationName: String, // UUS: Kindel nimi mälust
    isPlaying: Boolean,
    parsedTitle: String,
    parsedArtist: String,
    parsedExtra: String,
    playerStatus: String,
    bitrateInfo: String,
    alarmInfo: Pair<Long, String>?,
    alarmDays: Set<Int>,
    sleepTimerMillis: Long,
    onPlayPause: () -> Unit,
    onPlayStation: (RadioStation) -> Unit,
    onSleepClick: () -> Unit,
    onAlarmCancel: () -> Unit,
    onAlarmSet: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    // Värvid
    val artistColor = if (isPlaying) MaterialTheme.colorScheme.primary else Color.Gray
    val titleColor = if (isPlaying) MaterialTheme.colorScheme.secondary else Color.Gray
    val extraColor = if (isPlaying) MaterialTheme.colorScheme.onSecondary else Color.Gray

    Column(modifier = modifier) {
        // 1. INFO KAART
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {

                // Pealkiri
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

                Spacer(Modifier.height(4.dp))

                // Esitaja / Jaama nimi (Kasutame nüüd activeStationName varuvariandina)
                val displayName = if (parsedArtist.isNotBlank()) parsedArtist
                else if (parsedTitle.isNotBlank()) "Otseeeter"
                else if (activeStationName.isNotEmpty()) activeStationName
                else "Vali jaam"

                Text(
                    text = displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = artistColor,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 24.sp
                )

                // Lisa info
                if (parsedExtra.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = parsedExtra,
                        style = MaterialTheme.typography.titleMedium,
                        color = extraColor,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 24.sp
                    )
                }

                Spacer(Modifier.height(12.dp))

                // Tehniline info (Status Row) - Kasutame ka siin kindlat nime
                val stationPrefix = if (activeStationName.isNotEmpty()) "$activeStationName • " else ""
                val statusText = "$stationPrefix$playerStatus" + if (bitrateInfo.isNotBlank()) " • $bitrateInfo" else ""
                Text(text = statusText, style = MaterialTheme.typography.labelLarge, color = Color.Gray)

                // Äratus ja Taimer info
                if (alarmInfo != null || sleepTimerMillis > 0) {
                    Spacer(Modifier.height(12.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (alarmInfo != null) {
                            val prettyTime = AlarmUtils.getAlarmText(alarmInfo.first, alarmDays)
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
                                Icon(Icons.Default.Bedtime, null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(text = timeStr, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // 2. NUPUD
        val buttonShape = RoundedCornerShape(16.dp)

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val buttonModifier = Modifier.weight(1f).height(64.dp)

            // Play / Pause
            if (isPlaying) {
                FilledIconButton(onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onPlayPause()
                }, modifier = buttonModifier, shape = buttonShape) {
                    Icon(Icons.Default.Pause, "Paus", modifier = Modifier.size(32.dp))
                }
            } else {
                FilledTonalIconButton(onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    selectedStation?.let { onPlayStation(it) }
                }, enabled = selectedStation != null, modifier = buttonModifier, shape = buttonShape) {
                    Icon(Icons.Default.PlayArrow, "Mängi", modifier = Modifier.size(32.dp))
                }
            }

            // Unetaimer
            val isTimerSet = sleepTimerMillis > 0
            FilledTonalIconButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onSleepClick()
                },
                enabled = isPlaying || isTimerSet,
                modifier = buttonModifier,
                shape = buttonShape,
                colors = if (isTimerSet) IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary) else IconButtonDefaults.filledTonalIconButtonColors()
            ) {
                Icon(Icons.Default.Bedtime, "Unetaimer", modifier = Modifier.size(28.dp))
            }

            // Äratus
            if (alarmInfo != null) {
                OutlinedIconButton(onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onAlarmCancel()
                }, modifier = buttonModifier, shape = buttonShape,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary),
                    colors = IconButtonDefaults.outlinedIconButtonColors(contentColor = MaterialTheme.colorScheme.tertiary)) {
                    Icon(Icons.Default.AlarmOff, "Tühista äratus", modifier = Modifier.size(28.dp))
                }
            } else {
                FilledTonalIconButton(onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onAlarmSet()
                }, enabled = selectedStation != null, modifier = buttonModifier, shape = buttonShape) {
                    Icon(Icons.Default.AlarmAdd, "Sea äratus", modifier = Modifier.size(28.dp))
                }
            }
        }
    }
}