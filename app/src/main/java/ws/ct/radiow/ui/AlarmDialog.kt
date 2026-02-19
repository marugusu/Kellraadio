package ws.ct.radiow.ui

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
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.unit.dp
import ws.ct.radiow.RadioStation
import ws.ct.radiow.R
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
        Calendar.MONDAY to stringResource(R.string.day_mon),
        Calendar.TUESDAY to stringResource(R.string.day_tue),
        Calendar.WEDNESDAY to stringResource(R.string.day_wed),
        Calendar.THURSDAY to stringResource(R.string.day_thu),
        Calendar.FRIDAY to stringResource(R.string.day_fri),
        Calendar.SATURDAY to stringResource(R.string.day_sat),
        Calendar.SUNDAY to stringResource(R.string.day_sun)
    )

    val baseTitle = if (onDelete != null) stringResource(R.string.alarm_edit_title) else stringResource(R.string.alarm_set_title)
    val stationName = selectedStation?.name

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.Alarm,
                null,
                tint = MaterialTheme.colorScheme.tertiary
            )
        },
        title = {
            val styledTitle = buildAnnotatedString {
                append(baseTitle)

                if (!stationName.isNullOrEmpty()) {
                    append(": ")
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
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            if (onDelete != null) {
                TextButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onDelete()
                        onDismiss()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            }

            TextButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onDismiss()
                }
            ) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}