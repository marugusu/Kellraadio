package ee.minu.kellraadio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ee.minu.kellraadio.AlarmUtils
import ee.minu.kellraadio.RadioStation
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmDialog(
    selectedStation: RadioStation?,
    onDismiss: () -> Unit,
    onAlarmSaved: (Long, Set<Int>) -> Unit // Tagastab aja ja päevad
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val isLandscape = LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    val currentTime = Calendar.getInstance()
    val timePickerState = rememberTimePickerState(
        initialHour = currentTime.get(Calendar.HOUR_OF_DAY),
        initialMinute = currentTime.get(Calendar.MINUTE),
        is24Hour = true
    )

    // Mäletame valitud päevi
    val days = remember { mutableStateListOf<Int>() }

    val weekDays = listOf(
        Calendar.MONDAY to "E", Calendar.TUESDAY to "T", Calendar.WEDNESDAY to "K",
        Calendar.THURSDAY to "N", Calendar.FRIDAY to "R", Calendar.SATURDAY to "L", Calendar.SUNDAY to "P"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Alarm, null) },
        title = { Text("Sea äratus") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Kell (Landscape vs Portrait)
                if (isLandscape) {
                    TimeInput(state = timePickerState)
                } else {
                    TimePicker(state = timePickerState)
                }

                Spacer(Modifier.height(16.dp))

                // Kordus (Päevade valik)
                Text("Korda", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))

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

                if (days.isEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text("(Ühekordne)", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                selectedStation?.let { station ->
                    val finalDays = days.toSet()
                    val time = AlarmUtils.setAlarm(context, timePickerState.hour, timePickerState.minute, station, finalDays)
                    if (time != null) {
                        onAlarmSaved(time, finalDays)
                    }
                }
                onDismiss()
            }) { Text("Salvesta") }
        },
        dismissButton = {
            TextButton(onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onDismiss()
            }) { Text("Loobu") }
        }
    )
}