package app.radiorecalarm.ui

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.radiorecalarm.R
import app.radiorecalarm.RadioService
import app.radiorecalarm.ui.theme.KellraadioTheme
import java.util.Calendar
import java.util.Locale

@Composable
fun SleepTimerSheet(
    initialMillis: Long,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current

    var sliderValue by remember {
        mutableFloatStateOf(if (initialMillis > 0) (initialMillis / 60000f).coerceIn(5f, 120f) else 30f)
    }

    AdaptiveModalSheet(
        onDismiss = onDismiss,
        dialogMaxWidth = 440.dp,
        dialogWidthFraction = 0.9f,
        dialogMaxHeightFraction = 0.9f
    ) { isDialogMode ->
        SleepTimerSheetContent(
            minutes = sliderValue.toInt(),
            sliderValue = sliderValue,
            onSliderChange = { sliderValue = it },
            onStart = {
                view.performHapticTap()
                val intent = Intent(context, RadioService::class.java).apply {
                    action = RadioService.ACTION_SET_TIMER
                    putExtra(RadioService.EXTRA_TIMER_DURATION, sliderValue.toInt())
                }
                context.startService(intent)
                onDismiss()
            },
            onCancelTimer = {
                view.performHapticTap()
                val intent = Intent(context, RadioService::class.java).apply {
                    action = RadioService.ACTION_SET_TIMER
                    putExtra(RadioService.EXTRA_TIMER_DURATION, 0)
                }
                context.startService(intent)
                onDismiss()
            },
            modifier = Modifier.padding(
                start = 20.dp,
                end = 20.dp,
                top = if (isDialogMode) 20.dp else 4.dp,
                bottom = 20.dp
            )
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SleepTimerSheetContent(
    minutes: Int,
    sliderValue: Float,
    onSliderChange: (Float) -> Unit,
    onStart: () -> Unit,
    onCancelTimer: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
    ) {
        Icon(
            imageVector = Icons.Default.Timer,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.timer_dialog_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.timer_minutes, minutes),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        val calendar = Calendar.getInstance().apply { add(Calendar.MINUTE, minutes) }
        val timeString = String.format(
            Locale.getDefault(),
            "%02d:%02d",
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE)
        )

        Text(
            text = stringResource(R.string.timer_until, timeString),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(20.dp))

        Slider(
            value = sliderValue,
            onValueChange = onSliderChange,
            valueRange = 5f..120f,
            steps = 22,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary
            )
        )

        Spacer(modifier = Modifier.height(14.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            listOf(15, 30, 45, 60, 90, 120).forEach { min ->
                val isSelected = minutes == min
                SuggestionChip(
                    onClick = { onSliderChange(min.toFloat()) },
                    label = { Text("$min min") },
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(
                        1.dp,
                        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                    ),
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                        labelColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = onCancelTimer,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(stringResource(R.string.action_cancel), color = MaterialTheme.colorScheme.tertiary)
            }
            Button(
                onClick = onStart,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(stringResource(R.string.timer_start))
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0C10)
@Composable
private fun SleepTimerSheetPreview() {
    KellraadioTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            SleepTimerSheetContent(
                minutes = 30,
                sliderValue = 30f,
                onSliderChange = {},
                onStart = {},
                onCancelTimer = {},
                modifier = Modifier.padding(20.dp)
            )
        }
    }
}
