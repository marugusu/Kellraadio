package ee.minu.kellraadio.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ee.minu.kellraadio.Alarm
import ee.minu.kellraadio.AlarmUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmsScreen(
    alarms: List<Alarm>,
    onAddAlarm: () -> Unit,
    onToggleAlarm: (Alarm) -> Unit,
    onEditAlarm: (Alarm) -> Unit,
    modifier: Modifier = Modifier
) {
    val isLandscape = LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            FloatingActionButton(onClick = onAddAlarm) {
                Icon(Icons.Default.Add, contentDescription = "Lisa äratus")
            }
        }
    ) { innerPadding ->
        // PARANDUS 1: Eemaldame siit horisontaalse paddingu, et vältida topelt-paddingut.
        // Kasutame ainult alumist paddingut, et sisu ei jääks navigeerimisriba alla.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding())
        ) {
            // Päise rida
            Row(
                // PARANDUS 2: Lisame fikseeritud kõrguse ja standardse paddingu, et see oleks identne teiste ekraanidega.
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(horizontal = if (isLandscape) 8.dp else 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Alarm, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Äratused",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (alarms.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Äratusi pole lisatud", color = Color.Gray)
                }
            } else {
                LazyColumn(
                    // PARANDUS 3: Anname LazyColumnile endale horisontaalse paddingu.
                    // Lisame ka suurema alumise paddingu, et sisu ei jääks FAB-nupu alla.
                    contentPadding = PaddingValues(
                        start = if (isLandscape) 8.dp else 16.dp,
                        end = if (isLandscape) 8.dp else 16.dp,
                        top = 8.dp,
                        bottom = 80.dp // See tagab, et viimane element on keritav FAB-nupu kohale
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(alarms, key = { it.id }) { alarm ->
                        AlarmItem(
                            alarm = alarm,
                            onToggle = { onToggleAlarm(alarm) },
                            onClick = { onEditAlarm(alarm) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AlarmItem(
    alarm: Alarm,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit
) {
    val textColor = if (alarm.isEnabled) MaterialTheme.colorScheme.onSurface else Color.Gray

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (alarm.isEnabled) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                // Kellaaeg
                Text(
                    text = String.format("%02d:%02d", alarm.hour, alarm.minute),
                    style = MaterialTheme.typography.headlineLarge, // MUUDETUD: displaySmall -> headlineLarge
                    color = textColor,
                    lineHeight = 40.sp
                )
                // Päevad ja jaama nimi
                val alarmTextParts = AlarmUtils.getAlarmText(alarm.hour, alarm.minute, alarm.days).split("•")
                val daysText = if(alarmTextParts.size > 1) alarmTextParts[1].trim() else alarmTextParts[0].trim()

                Text(
                    text = "$daysText • ${alarm.stationName}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColor
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Switch(
                checked = alarm.isEnabled,
                onCheckedChange = onToggle
            )
        }
    }
}