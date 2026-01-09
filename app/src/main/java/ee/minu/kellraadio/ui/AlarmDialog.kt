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
    initialHour: Int? = null,    // UUS: Algne tund (muutmisel)
    initialMinute: Int? = null,  // UUS: Algne minut
    initialDays: Set<Int> = emptySet(), // UUS: Algsed päevad
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null, // UUS: Kustutamise funktsioon (kui on null, siis nuppu ei näita)
    onAlarmSaved: (Long, Set<Int>) -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val isLandscape = LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    // Kui algset aega pole, võta praegune aeg
    val currentTime = Calendar.getInstance()
    val startHour = initialHour ?: currentTime.get(Calendar.HOUR_OF_DAY)
    val startMinute = initialMinute ?: currentTime.get(Calendar.MINUTE)

    val timePickerState = rememberTimePickerState(
        initialHour = startHour,
        initialMinute = startMinute,
        is24Hour = true
    )

    // Mäletame valitud päevi (alustame initialDays väärtusega)
    val days = remember { mutableStateListOf<Int>().apply { addAll(initialDays) } }

    val weekDays = listOf(
        Calendar.MONDAY to "E", Calendar.TUESDAY to "T", Calendar.WEDNESDAY to "K",
        Calendar.THURSDAY to "N", Calendar.FRIDAY to "R", Calendar.SATURDAY to "L", Calendar.SUNDAY to "P"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Alarm, null) },
        title = { Text(if (onDelete != null) "Muuda äratust" else "Sea äratus") },
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
            // Siin on nüüd Row, et mahutada "Kustuta" ja "Loobu" nupud
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (onDelete != null) {
                    TextButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onDelete()
                            onDismiss()
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.Delete, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Kustuta")
                    }
                } else {
                    Spacer(Modifier.width(8.dp)) // Tühi ruum, kui nuppu pole
                }

                TextButton(onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onDismiss()
                }) { Text("Loobu") }
            }
        }
    )
}