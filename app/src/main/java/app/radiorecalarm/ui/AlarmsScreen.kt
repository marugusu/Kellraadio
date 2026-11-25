package app.radiorecalarm.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.radiorecalarm.Alarm
import app.radiorecalarm.AlarmUtils
import app.radiorecalarm.R
import app.radiorecalarm.RadioStation
import app.radiorecalarm.ui.theme.KellraadioTheme
import kotlinx.coroutines.delay
import java.util.Calendar

/**
 * Äratuste haldamise vaade: päiserea `+ Lisa` nupuga (ei kattu kunagi kaartide lülititega),
 * jaamalogodega ühtlustatud 64.dp ridade ja järgmise äratuse esiletõstmisega.
 */
@Composable
fun AlarmsScreen(
    alarms: List<Alarm>,
    stations: List<RadioStation> = emptyList(),
    onAddAlarm: () -> Unit,
    onToggleAlarm: (Alarm) -> Unit,
    onEditAlarm: (Alarm) -> Unit,
    modifier: Modifier = Modifier
) {
    val isLandscape = LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val view = LocalView.current
    val context = LocalContext.current

    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000L)
            nowMillis = System.currentTimeMillis()
        }
    }

    val nextAlarm = remember(alarms, nowMillis) {
        AlarmUtils.selectNextAlarm(alarms.filter { it.isEnabled }, nowMillis = nowMillis)
    }
    val nextAlarmId = nextAlarm?.first?.id

    val stationLookup = remember(stations) {
        stations.associateBy { it.url to it.name } to stations.associateBy { it.name }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = if (isLandscape) 8.dp else 16.dp)
                .padding(top = if (isLandscape) 12.dp else 0.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Alarm, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.alarm_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Surface(
                onClick = {
                    view.performHapticTap()
                    onAddAlarm()
                },
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.height(32.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = stringResource(R.string.alarm_add_short),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        if (alarms.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = if (isLandscape) 16.dp else 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Alarm,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(44.dp)
                    )
                    Text(
                        text = stringResource(R.string.alarm_no_alarms),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.alarm_empty_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(
                    start = if (isLandscape) 8.dp else 16.dp,
                    end = if (isLandscape) 8.dp else 16.dp,
                    top = 4.dp,
                    bottom = 16.dp
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(alarms, key = { it.id }) { alarm ->
                    val matchedStation = stationLookup.first[alarm.stationUrl to alarm.stationName]
                        ?: stationLookup.second[alarm.stationName]
                    val isNext = alarm.isEnabled && alarm.id == nextAlarmId
                    val nextCountdownText = if (isNext) {
                        val until = AlarmUtils.computeTimeUntilAlarm(
                            alarm.hour,
                            alarm.minute,
                            alarm.days,
                            nowMillis = nowMillis
                        )
                        AlarmUtils.formatTimeUntilAlarm(context, until)
                    } else null

                    AlarmItem(
                        alarm = alarm,
                        stationIconUrl = matchedStation?.iconUrl.orEmpty(),
                        isNextAlarm = isNext,
                        nextCountdownText = nextCountdownText,
                        onToggle = {
                            view.performHapticTap()
                            onToggleAlarm(alarm)
                        },
                        onClick = {
                            view.performHapticTap()
                            onEditAlarm(alarm)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun AlarmItem(
    alarm: Alarm,
    stationIconUrl: String = "",
    isNextAlarm: Boolean = false,
    nextCountdownText: String? = null,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val repeatText = remember(alarm.days, context) {
        AlarmUtils.getRepeatLabel(context, alarm.days)
    }

    val containerColor = when {
        isNextAlarm -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
        alarm.isEnabled -> MaterialTheme.colorScheme.surfaceVariant
        else -> MaterialTheme.colorScheme.surface
    }
    val borderStroke = when {
        isNextAlarm -> BorderStroke(1.25.dp, MaterialTheme.colorScheme.primary)
        alarm.isEnabled -> BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        else -> BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
    }
    val primaryTextColor = when {
        isNextAlarm -> MaterialTheme.colorScheme.onPrimaryContainer
        alarm.isEnabled -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    }
    val secondaryTextColor = when {
        isNextAlarm -> MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
        alarm.isEnabled -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        border = borderStroke,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 12.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StationLogoBadge(
                stationName = alarm.stationName,
                iconUrl = stationIconUrl,
                size = 40.dp,
                cornerRadius = 8.dp,
                borderColor = if (isNextAlarm) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                } else {
                    MaterialTheme.colorScheme.outline
                },
                loadedBorderColor = if (isNextAlarm) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                } else {
                    Color.Transparent
                },
                modifier = Modifier.alpha(if (alarm.isEnabled) 1f else 0.45f)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = String.format("%02d:%02d", alarm.hour, alarm.minute),
                        style = TextStyle(
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            fontFeatureSettings = "tnum"
                        ),
                        color = primaryTextColor
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = alarm.stationName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = primaryTextColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    if (alarm.recordAudio) {
                        Spacer(modifier = Modifier.width(6.dp))
                        AlarmRecBadge(isEnabled = alarm.isEnabled)
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                val subtitle = if (isNextAlarm && !nextCountdownText.isNullOrBlank()) {
                    "$repeatText • $nextCountdownText"
                } else {
                    repeatText
                }

                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.5.sp),
                    color = if (isNextAlarm) MaterialTheme.colorScheme.primary else secondaryTextColor,
                    fontWeight = if (isNextAlarm) FontWeight.Medium else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            Switch(
                checked = alarm.isEnabled,
                onCheckedChange = onToggle,
                modifier = Modifier.scale(0.82f),
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                    uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surface,
                    uncheckedBorderColor = MaterialTheme.colorScheme.outline
                )
            )
        }
    }
}

@Composable
private fun AlarmRecBadge(isEnabled: Boolean) {
    val accent = if (isEnabled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = accent.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(accent)
            )
            Text(
                text = stringResource(R.string.alarm_rec_badge),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.4.sp
                ),
                color = accent
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0C10)
@Composable
private fun AlarmsScreenPreview() {
    val workDays = setOf(
        Calendar.MONDAY,
        Calendar.TUESDAY,
        Calendar.WEDNESDAY,
        Calendar.THURSDAY,
        Calendar.FRIDAY
    )
    val previewStations = listOf(
        RadioStation(1, "Vikerraadio", "u1"),
        RadioStation(2, "Klassikaraadio", "u2"),
        RadioStation(3, "Legendaarne", "u3")
    )
    val previewAlarms = listOf(
        Alarm(1, 6, 30, workDays, "Vikerraadio", "u1", isEnabled = true),
        Alarm(2, 6, 33, workDays, "Klassikaraadio", "u2", isEnabled = false),
        Alarm(3, 15, 0, setOf(Calendar.WEDNESDAY), "Legendaarne", "u3", isEnabled = true, recordAudio = true),
        Alarm(4, 15, 4, emptySet(), "Vikerraadio", "u1", isEnabled = true, recordAudio = true)
    )
    KellraadioTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            AlarmsScreen(
                alarms = previewAlarms,
                stations = previewStations,
                onAddAlarm = {},
                onToggleAlarm = {},
                onEditAlarm = {}
            )
        }
    }
}
