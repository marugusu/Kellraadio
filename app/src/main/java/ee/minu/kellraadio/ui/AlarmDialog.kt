package ee.minu.kellraadio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.unit.dp
import ee.minu.kellraadio.RadioStation
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmDialog(
    selectedStation: RadioStation?,
    initialHour: Int? = null,
    initialMinute: Int? = null,
    initialDays: Set<Int> = emptySet(),
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null,
    onAlarmSaved: (hour: Int, minute: Int, days: Set<Int>) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val isLandscape = LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE


    val currentTime = Calendar.getInstance()
    val startHour = initialHour ?: currentTime.get(Calendar.HOUR_OF_DAY)
    val startMinute = initialMinute ?: currentTime.get(Calendar.MINUTE)

    val timePickerState = rememberTimePickerState(
        initialHour = startHour,
        initialMinute = startMinute,
        is24Hour = true
    )

    val days = remember { mutableStateListOf<Int>().apply { addAll(initialDays) } }

    val weekDays = listOf(
        Calendar.MONDAY to "E", Calendar.TUESDAY to "T", Calendar.WEDNESDAY to "K",
        Calendar.THURSDAY to "N", Calendar.FRIDAY to "R", Calendar.SATURDAY to "L", Calendar.SUNDAY to "P"
    )

    // --- PARANDUS ALGAB SIIT ---
    val baseTitle = if (onDelete != null) "Muuda äratust" else "Sea äratus"
    val stationName = selectedStation?.name
    val finalTitle = if (!stationName.isNullOrEmpty()) "$baseTitle: $stationName" else baseTitle
    // --- PARANDUS LÕPPEB ---

    AlertDialog(
        onDismissRequest = onDismiss,
        // 1. Ikoon värviliseks (tertiary)
        icon = {
            Icon(
                Icons.Default.Alarm,
                null,
                tint = MaterialTheme.colorScheme.tertiary
            )
        },
        // 2. Pealkiri: "Sea äratus" on tavaline, "Kanali Nimi" on värviline
        title = {
            val styledTitle = buildAnnotatedString {
                append(baseTitle) // "Sea äratus" või "Muuda äratust"

                if (!stationName.isNullOrEmpty()) {
                    append(": ")
                    // See plokk muudab järgneva teksti värvi
                    withStyle(style = SpanStyle(color = MaterialTheme.colorScheme.tertiary)) {
                        append(stationName)
                    }
                }
            }

            Text(
                text = styledTitle,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (isLandscape) {
                    TimeInput(state = timePickerState)
                } else {
                    TimePicker(state = timePickerState)
                }

                Spacer(Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    weekDays.forEach { (dayId, label) ->
                        val isSelected = days.contains(dayId)
                        val bgColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                        val contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface

                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(bgColor)
                                .border(1.dp, if(isSelected) Color.Transparent else Color.Gray, CircleShape)
                                .clickable {
                                    if (isSelected) days.remove(dayId) else days.add(dayId)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = label, color = contentColor, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = selectedStation != null,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onAlarmSaved(timePickerState.hour, timePickerState.minute, days.toSet())
                    onDismiss()
                }
            ) { Text("Salvesta") }
        },
        dismissButton = {
            if (onDelete != null) {
                // Kui muudame, näitame "Kustuta" nuppu
                TextButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onDelete()
                        onDismiss()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Kustuta")
                }
            }

            // "Loobu" nupp on alati olemas
            TextButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onDismiss()
                }
            ) { Text("Loobu") }
        }
    )
}