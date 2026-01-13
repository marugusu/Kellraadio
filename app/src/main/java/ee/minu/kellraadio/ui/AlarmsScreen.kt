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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding())
        ) {
            Row(
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
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // PARANDUS: Eemaldasime siit Spacer(modifier = Modifier.height(8.dp))

            if (alarms.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(horizontal = if (isLandscape) 8.dp else 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Äratusi pole lisatud", color = Color.Gray)
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(
                        start = if (isLandscape) 8.dp else 16.dp,
                        end = if (isLandscape) 8.dp else 16.dp,
                        top = 0.dp, // PARANDUS: 8.dp -> 0.dp
                        bottom = 80.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
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
                Text(
                    text = String.format("%02d:%02d", alarm.hour, alarm.minute),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )

                Spacer(modifier = Modifier.height(2.dp))

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