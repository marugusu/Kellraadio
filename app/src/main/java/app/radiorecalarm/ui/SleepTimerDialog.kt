package app.radiorecalarm.ui

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.radiorecalarm.RadioService
import app.radiorecalarm.R
import java.util.Calendar

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SleepTimerDialog(
    initialMillis: Long,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    var sliderValue by remember {
        mutableFloatStateOf(if (initialMillis > 0) (initialMillis / 60000f).coerceIn(5f, 120f) else 30f)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Timer, null, tint = MaterialTheme.colorScheme.secondary) },
        title = { Text(stringResource(R.string.timer_dialog_title), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.timer_minutes, sliderValue.toInt()),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                val calendar = Calendar.getInstance().apply { add(Calendar.MINUTE, sliderValue.toInt()) }
                val timeString = String.format("%02d:%02d", calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE))

                Text(
                    text = stringResource(R.string.timer_until, timeString),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray
                )

                Spacer(modifier = Modifier.height(24.dp))

                Slider(
                    value = sliderValue,
                    onValueChange = { sliderValue = it },
                    valueRange = 5f..120f,
                    steps = 22,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    listOf(15, 30, 45, 60, 90, 120).forEach { min ->
                        SuggestionChip(
                            onClick = { sliderValue = min.toFloat() },
                            label = { Text("$min min") },
                            shape = RoundedCornerShape(12.dp),
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = if (sliderValue.toInt() == min) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                            )
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    val intent = Intent(context, RadioService::class.java).apply {
                        action = RadioService.ACTION_SET_TIMER
                        putExtra(RadioService.EXTRA_TIMER_DURATION, sliderValue.toInt())
                    }
                    context.startService(intent)
                    onDismiss()
                },
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(stringResource(R.string.timer_start))
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    val intent = Intent(context, RadioService::class.java).apply {
                        action = RadioService.ACTION_SET_TIMER
                        putExtra(RadioService.EXTRA_TIMER_DURATION, 0)
                    }
                    context.startService(intent)
                    onDismiss()
                }
            ) {
                Text(stringResource(R.string.action_cancel), color = MaterialTheme.colorScheme.tertiary)
            }
        }
    )
}
