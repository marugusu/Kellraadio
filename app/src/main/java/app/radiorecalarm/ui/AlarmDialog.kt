package app.radiorecalarm.ui

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.radiorecalarm.AlarmUtils
import app.radiorecalarm.R
import app.radiorecalarm.RadioStation
import app.radiorecalarm.isTv
import app.radiorecalarm.ui.theme.KellraadioTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar
import kotlin.math.abs

private val WORK_DAYS = setOf(
    Calendar.MONDAY,
    Calendar.TUESDAY,
    Calendar.WEDNESDAY,
    Calendar.THURSDAY,
    Calendar.FRIDAY
)
private val WEEKEND_DAYS = setOf(Calendar.SATURDAY, Calendar.SUNDAY)
private val ALL_DAYS = WORK_DAYS + WEEKEND_DAYS

/**
 * Kohanduv äratuse loomise ja muutmise vorm.
 * Püstasendis kuvatakse `ModalBottomSheet`ina, rõhtasendis ja TV-s 2-veerulise `Dialog`ina.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmDialog(
    selectedStation: RadioStation?,
    stations: List<RadioStation>,
    initialHour: Int? = null,
    initialMinute: Int? = null,
    initialDays: Set<Int> = emptySet(),
    initialRecordAudio: Boolean = false,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null,
    onAlarmSaved: (hour: Int, minute: Int, days: Set<Int>, recordAudio: Boolean, station: RadioStation) -> Unit
) {
    val configuration = LocalConfiguration.current
    val context = LocalContext.current
    val view = LocalView.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val useDialogFrame = isLandscape || isTv(context)

    val nowCal = remember { Calendar.getInstance() }
    var hour by remember { mutableIntStateOf(initialHour ?: nowCal.get(Calendar.HOUR_OF_DAY)) }
    var minute by remember { mutableIntStateOf(initialMinute ?: nowCal.get(Calendar.MINUTE)) }
    var days by remember { mutableStateOf(initialDays) }
    var recordAudio by remember { mutableStateOf(initialRecordAudio) }
    var isKeyboardMode by remember { mutableStateOf(false) }
    var showStationPicker by remember { mutableStateOf(false) }

    val resolvedInitialStation = remember(selectedStation, stations) {
        selectedStation
            ?: stations.firstOrNull { it.isFavorite }
            ?: stations.firstOrNull()
    }
    var currentStation by remember(resolvedInitialStation) {
        mutableStateOf(resolvedInitialStation)
    }

    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(15_000L)
            nowMillis = System.currentTimeMillis()
        }
    }

    val timeUntil = remember(hour, minute, days, nowMillis) {
        AlarmUtils.computeTimeUntilAlarm(hour, minute, days, nowMillis = nowMillis)
    }
    val countdownText = AlarmUtils.formatTimeUntilAlarm(context, timeUntil)

    val titleText = if (onDelete != null) {
        stringResource(R.string.alarm_edit_title)
    } else {
        stringResource(R.string.alarm_set_title)
    }

    if (useDialogFrame) {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxWidth(0.92f)
                    .heightIn(max = (configuration.screenHeightDp * 0.92f).dp)
            ) {
                AlarmFormLandscapeLayout(
                    title = titleText,
                    hour = hour,
                    minute = minute,
                    isKeyboardMode = isKeyboardMode,
                    countdownText = countdownText,
                    station = currentStation,
                    days = days,
                    recordAudio = recordAudio,
                    canSave = currentStation != null,
                    showDelete = onDelete != null,
                    onToggleInputMode = {
                        view.performHapticTap()
                        isKeyboardMode = !isKeyboardMode
                    },
                    onHourChange = { hour = it },
                    onMinuteChange = { minute = it },
                    onOpenStationPicker = {
                        view.performHapticTap()
                        showStationPicker = true
                    },
                    onDaysChange = { days = it },
                    onRecordAudioChange = { recordAudio = it },
                    onDelete = onDelete?.let { deleteAction ->
                        {
                            view.performHapticTap()
                            deleteAction()
                        }
                    },
                    onDismiss = {
                        view.performHapticTap()
                        onDismiss()
                    },
                    onSave = {
                        val stationToSave = currentStation ?: return@AlarmFormLandscapeLayout
                        view.performHapticTap()
                        onAlarmSaved(hour, minute, days, recordAudio, stationToSave)
                    }
                )
            }
        }
    } else {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 0.dp,
            shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
            dragHandle = {
                BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.outline)
            }
        ) {
            AlarmFormPortraitLayout(
                title = titleText,
                hour = hour,
                minute = minute,
                isKeyboardMode = isKeyboardMode,
                countdownText = countdownText,
                station = currentStation,
                days = days,
                recordAudio = recordAudio,
                canSave = currentStation != null,
                showDelete = onDelete != null,
                onToggleInputMode = {
                    view.performHapticTap()
                    isKeyboardMode = !isKeyboardMode
                },
                onHourChange = { hour = it },
                onMinuteChange = { minute = it },
                onOpenStationPicker = {
                    view.performHapticTap()
                    showStationPicker = true
                },
                onDaysChange = { days = it },
                onRecordAudioChange = { recordAudio = it },
                onDelete = onDelete?.let { deleteAction ->
                    {
                        view.performHapticTap()
                        deleteAction()
                    }
                },
                onDismiss = {
                    view.performHapticTap()
                    onDismiss()
                },
                onSave = {
                    val stationToSave = currentStation ?: return@AlarmFormPortraitLayout
                    view.performHapticTap()
                    onAlarmSaved(hour, minute, days, recordAudio, stationToSave)
                }
            )
        }
    }

    if (showStationPicker) {
        AlarmStationPickerDialog(
            stations = stations,
            selectedStation = currentStation,
            onDismiss = { showStationPicker = false },
            onSelect = { picked ->
                currentStation = picked
                showStationPicker = false
            }
        )
    }
}

@Composable
private fun AlarmFormPortraitLayout(
    title: String,
    hour: Int,
    minute: Int,
    isKeyboardMode: Boolean,
    countdownText: String,
    station: RadioStation?,
    days: Set<Int>,
    recordAudio: Boolean,
    canSave: Boolean,
    showDelete: Boolean,
    onToggleInputMode: () -> Unit,
    onHourChange: (Int) -> Unit,
    onMinuteChange: (Int) -> Unit,
    onOpenStationPicker: () -> Unit,
    onDaysChange: (Set<Int>) -> Unit,
    onRecordAudioChange: (Boolean) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
    ) {
        AlarmFormHeader(
            title = title,
            isKeyboardMode = isKeyboardMode,
            showDelete = showDelete,
            onToggleInputMode = onToggleInputMode,
            onDelete = onDelete,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            AlarmTimeSection(
                hour = hour,
                minute = minute,
                isKeyboardMode = isKeyboardMode,
                countdownText = countdownText,
                onHourChange = onHourChange,
                onMinuteChange = onMinuteChange
            )

            AlarmStationCard(
                station = station,
                onClick = onOpenStationPicker
            )

            AlarmRepeatSection(
                days = days,
                onDaysChange = onDaysChange
            )

            AlarmRecordToggleCard(
                recordAudio = recordAudio,
                onRecordAudioChange = onRecordAudioChange
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp)
            ) {
                Text(stringResource(R.string.action_cancel), fontWeight = FontWeight.SemiBold)
            }
            Button(
                onClick = onSave,
                enabled = canSave,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .weight(1.35f)
                    .height(46.dp)
            ) {
                Text(stringResource(R.string.action_save), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun AlarmFormLandscapeLayout(
    title: String,
    hour: Int,
    minute: Int,
    isKeyboardMode: Boolean,
    countdownText: String,
    station: RadioStation?,
    days: Set<Int>,
    recordAudio: Boolean,
    canSave: Boolean,
    showDelete: Boolean,
    onToggleInputMode: () -> Unit,
    onHourChange: (Int) -> Unit,
    onMinuteChange: (Int) -> Unit,
    onOpenStationPicker: () -> Unit,
    onDaysChange: (Set<Int>) -> Unit,
    onRecordAudioChange: (Boolean) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        AlarmFormHeader(
            title = title,
            isKeyboardMode = isKeyboardMode,
            showDelete = false,
            onToggleInputMode = onToggleInputMode,
            onDelete = null,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(0.9f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AlarmTimeSection(
                    hour = hour,
                    minute = minute,
                    isKeyboardMode = isKeyboardMode,
                    countdownText = countdownText,
                    onHourChange = onHourChange,
                    onMinuteChange = onMinuteChange
                )
            }

            Column(
                modifier = Modifier
                    .weight(1.1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AlarmStationCard(
                    station = station,
                    onClick = onOpenStationPicker
                )

                AlarmRepeatSection(
                    days = days,
                    onDaysChange = onDaysChange
                )

                AlarmRecordToggleCard(
                    recordAudio = recordAudio,
                    onRecordAudioChange = onRecordAudioChange
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showDelete && onDelete != null) {
                TextButton(
                    onClick = onDelete,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.action_delete))
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(stringResource(R.string.action_cancel))
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onSave,
                enabled = canSave,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(stringResource(R.string.action_save), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun AlarmFormHeader(
    title: String,
    isKeyboardMode: Boolean,
    showDelete: Boolean,
    onToggleInputMode: () -> Unit,
    onDelete: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Alarm,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onToggleInputMode) {
            Icon(
                imageVector = if (isKeyboardMode) Icons.Default.Dialpad else Icons.Default.Keyboard,
                contentDescription = stringResource(
                    if (isKeyboardMode) R.string.alarm_time_picker_wheel else R.string.alarm_time_picker_keyboard
                ),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (showDelete && onDelete != null) {
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = stringResource(R.string.action_delete),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun AlarmTimeSection(
    hour: Int,
    minute: Int,
    isKeyboardMode: Boolean,
    countdownText: String,
    onHourChange: (Int) -> Unit,
    onMinuteChange: (Int) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (isKeyboardMode) {
            KeyboardTimeInput(
                hour = hour,
                minute = minute,
                onHourChange = onHourChange,
                onMinuteChange = onMinuteChange
            )
        } else {
            CircularTimeWheelPicker(
                hour = hour,
                minute = minute,
                onHourChange = onHourChange,
                onMinuteChange = onMinuteChange
            )
        }

        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Alarm,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = countdownText,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
private fun CircularTimeWheelPicker(
    hour: Int,
    minute: Int,
    onHourChange: (Int) -> Unit,
    onMinuteChange: (Int) -> Unit
) {
    val itemHeight = 44.dp

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(itemHeight * 3),
        contentAlignment = Alignment.Center
    ) {
        // Keskmise valitud rea merevaigutoonis taustariba
        Box(
            modifier = Modifier
                .fillMaxWidth(0.74f)
                .height(itemHeight)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f))
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(12.dp)
                )
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            CircularWheelColumn(
                value = hour,
                range = 24,
                itemHeight = itemHeight,
                onValueChange = onHourChange,
                modifier = Modifier.width(84.dp)
            )

            Text(
                text = ":",
                style = TextStyle(
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    fontFeatureSettings = "tnum"
                ),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )

            CircularWheelColumn(
                value = minute,
                range = 60,
                itemHeight = itemHeight,
                onValueChange = onMinuteChange,
                modifier = Modifier.width(84.dp)
            )
        }
    }
}

@Composable
private fun CircularWheelColumn(
    value: Int,
    range: Int,
    itemHeight: androidx.compose.ui.unit.Dp,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val repeatCycles = 400
    val totalCount = range * repeatCycles
    val midBase = (repeatCycles / 2) * range
    val initialIndex = remember { midBase + value.coerceIn(0, range - 1) }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val snapBehavior = rememberSnapFlingBehavior(lazyListState = listState)

    val centeredIndex by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val visibleItems = layoutInfo.visibleItemsInfo
            if (visibleItems.isEmpty()) {
                listState.firstVisibleItemIndex
            } else {
                val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
                visibleItems.minByOrNull { abs((it.offset + it.size / 2) - viewportCenter) }?.index
                    ?: listState.firstVisibleItemIndex
            }
        }
    }

    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentValue by rememberUpdatedState(value)
    var skipInitial by remember { mutableStateOf(true) }

    LaunchedEffect(centeredIndex) {
        if (skipInitial) {
            skipInitial = false
            return@LaunchedEffect
        }
        val mapped = centeredIndex % range
        if (mapped != currentValue) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            currentOnValueChange(mapped)
        }
    }

    LazyColumn(
        state = listState,
        flingBehavior = snapBehavior,
        contentPadding = PaddingValues(vertical = itemHeight),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.height(itemHeight * 3)
    ) {
        items(totalCount) { index ->
            val displayNum = index % range
            val isSelected = index == centeredIndex

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(itemHeight)
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null
                    ) {
                        if (!isSelected) {
                            scope.launch {
                                listState.animateScrollToItem(index)
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = String.format("%02d", displayNum),
                    style = TextStyle(
                        fontSize = if (isSelected) 32.sp else 20.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        fontFeatureSettings = "tnum"
                    ),
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    },
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun KeyboardTimeInput(
    hour: Int,
    minute: Int,
    onHourChange: (Int) -> Unit,
    onMinuteChange: (Int) -> Unit
) {
    var hourText by remember(hour) { mutableStateOf(String.format("%02d", hour)) }
    var minuteText by remember(minute) { mutableStateOf(String.format("%02d", minute)) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = hourText,
            onValueChange = { raw ->
                val filtered = raw.filter { it.isDigit() }.take(2)
                hourText = filtered
                filtered.toIntOrNull()?.let { parsed ->
                    if (parsed in 0..23) onHourChange(parsed)
                }
            },
            label = { Text(stringResource(R.string.alarm_hour_label)) },
            singleLine = true,
            textStyle = TextStyle(
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                fontFeatureSettings = "tnum"
            ),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.width(96.dp)
        )

        Text(
            text = ":",
            style = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 10.dp)
        )

        OutlinedTextField(
            value = minuteText,
            onValueChange = { raw ->
                val filtered = raw.filter { it.isDigit() }.take(2)
                minuteText = filtered
                filtered.toIntOrNull()?.let { parsed ->
                    if (parsed in 0..59) onMinuteChange(parsed)
                }
            },
            label = { Text(stringResource(R.string.alarm_minute_label)) },
            singleLine = true,
            textStyle = TextStyle(
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                fontFeatureSettings = "tnum"
            ),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.width(96.dp)
        )
    }
}

@Composable
private fun AlarmStationCard(
    station: RadioStation?,
    onClick: () -> Unit
) {
    val subtitleParts = remember(station) {
        if (station == null) emptyList()
        else buildList {
            if (station.countryCode.isNotBlank()) add(station.countryCode.uppercase())
            val cat = station.category.trim()
            if (cat.isNotBlank() && !(station.isUserStation && cat.equals("My", ignoreCase = true))) {
                add(cat)
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringResource(R.string.alarm_station_label),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (station != null) {
                    StationLogoBadge(
                        stationName = station.name,
                        iconUrl = station.iconUrl,
                        size = 38.dp,
                        cornerRadius = 8.dp
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Radio,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = station?.name ?: stringResource(R.string.select_station),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (subtitleParts.isNotEmpty()) {
                        Text(
                            text = subtitleParts.joinToString(" • "),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = stringResource(R.string.alarm_station_change),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AlarmRepeatSection(
    days: Set<Int>,
    onDaysChange: (Set<Int>) -> Unit
) {
    val view = LocalView.current
    val weekDays = listOf(
        Calendar.MONDAY to stringResource(R.string.day_mon),
        Calendar.TUESDAY to stringResource(R.string.day_tue),
        Calendar.WEDNESDAY to stringResource(R.string.day_wed),
        Calendar.THURSDAY to stringResource(R.string.day_thu),
        Calendar.FRIDAY to stringResource(R.string.day_fri),
        Calendar.SATURDAY to stringResource(R.string.day_sat),
        Calendar.SUNDAY to stringResource(R.string.day_sun)
    )

    val presets = listOf(
        stringResource(R.string.alarm_preset_once) to emptySet(),
        stringResource(R.string.alarm_preset_weekdays) to WORK_DAYS,
        stringResource(R.string.alarm_preset_weekend) to WEEKEND_DAYS,
        stringResource(R.string.alarm_preset_everyday) to ALL_DAYS
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.alarm_repeat_label),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            presets.forEach { (label, presetDays) ->
                val isSelected = days == presetDays
                Surface(
                    onClick = {
                        view.performHapticTap()
                        onDaysChange(presetDays)
                    },
                    shape = RoundedCornerShape(8.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                    )
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            weekDays.forEach { (dayId, label) ->
                val isSelected = days.contains(dayId)
                val bgColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                val contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(bgColor)
                        .border(1.dp, borderColor, RoundedCornerShape(8.dp))
                        .clickable {
                            view.performHapticTap()
                            val updated = if (isSelected) days - dayId else days + dayId
                            onDaysChange(updated)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = contentColor,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun AlarmRecordToggleCard(
    recordAudio: Boolean,
    onRecordAudioChange: (Boolean) -> Unit
) {
    Surface(
        onClick = { onRecordAudioChange(!recordAudio) },
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(
            width = 1.dp,
            color = if (recordAudio) MaterialTheme.colorScheme.error.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outline
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(
                        if (recordAudio) MaterialTheme.colorScheme.error.copy(alpha = 0.16f)
                        else MaterialTheme.colorScheme.surface
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.FiberManualRecord,
                    contentDescription = null,
                    tint = if (recordAudio) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.alarm_record_audio_title),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.alarm_record_audio_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Switch(
                checked = recordAudio,
                onCheckedChange = onRecordAudioChange,
                modifier = Modifier.scale(0.85f),
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = MaterialTheme.colorScheme.error,
                    uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surface,
                    uncheckedBorderColor = MaterialTheme.colorScheme.outline
                )
            )
        }
    }
}

@Composable
private fun AlarmStationPickerDialog(
    stations: List<RadioStation>,
    selectedStation: RadioStation?,
    onDismiss: () -> Unit,
    onSelect: (RadioStation) -> Unit
) {
    val configuration = LocalConfiguration.current
    var query by remember { mutableStateOf("") }

    val favoriteStations = remember(stations) {
        stations.filter { it.isFavorite }
            .sortedWith(compareBy<RadioStation> { if (it.favoriteOrder > 0) it.favoriteOrder else Int.MAX_VALUE }.thenBy { it.name })
    }
    val otherStations = remember(stations) {
        stations.filterNot { it.isFavorite }
            .sortedWith(compareBy<RadioStation> { it.priority }.thenBy { it.name })
    }

    val filteredFavorites = remember(favoriteStations, query) {
        if (query.isBlank()) favoriteStations
        else favoriteStations.filter {
            it.name.contains(query, ignoreCase = true) || it.category.contains(query, ignoreCase = true)
        }
    }
    val filteredOthers = remember(otherStations, query) {
        if (query.isBlank()) otherStations
        else otherStations.filter {
            it.name.contains(query, ignoreCase = true) || it.category.contains(query, ignoreCase = true)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth(0.92f)
                .heightIn(max = (configuration.screenHeightDp * 0.8f).dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 18.dp, end = 8.dp, top = 12.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.alarm_station_pick_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_close))
                    }
                }

                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(stringResource(R.string.search_placeholder)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Default.Close, contentDescription = null)
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                if (filteredFavorites.isEmpty() && filteredOthers.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.search_no_results),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                    ) {
                        if (filteredFavorites.isNotEmpty()) {
                            item(key = "header_fav") {
                                Text(
                                    text = stringResource(R.string.alarm_section_favorites),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }
                            items(filteredFavorites, key = { "fav_${it.id}_${it.url}" }) { station ->
                                StationPickerRow(
                                    station = station,
                                    isSelected = selectedStation?.url == station.url && selectedStation.name == station.name,
                                    onClick = { onSelect(station) }
                                )
                            }
                        }

                        if (filteredOthers.isNotEmpty()) {
                            item(key = "header_all") {
                                Text(
                                    text = stringResource(R.string.alarm_section_all_stations),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = if (filteredFavorites.isNotEmpty()) 8.dp else 4.dp, bottom = 4.dp)
                                )
                            }
                            items(filteredOthers, key = { "all_${it.id}_${it.url}" }) { station ->
                                StationPickerRow(
                                    station = station,
                                    isSelected = selectedStation?.url == station.url && selectedStation.name == station.name,
                                    onClick = { onSelect(station) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StationPickerRow(
    station: RadioStation,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val subtitleParts = remember(station) {
        buildList {
            if (station.countryCode.isNotBlank()) add(station.countryCode.uppercase())
            val cat = station.category.trim()
            if (cat.isNotBlank() && !(station.isUserStation && cat.equals("My", ignoreCase = true))) {
                add(cat)
            }
        }
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StationLogoBadge(
                stationName = station.name,
                iconUrl = station.iconUrl,
                size = 36.dp,
                cornerRadius = 8.dp
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = station.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitleParts.isNotEmpty()) {
                    Text(
                        text = subtitleParts.joinToString(" • "),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0C10)
@Composable
private fun AlarmFormPortraitPreview() {
    KellraadioTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            AlarmFormPortraitLayout(
                title = "Muuda äratust",
                hour = 6,
                minute = 30,
                isKeyboardMode = false,
                countdownText = "Heliseb 7 t 42 min pärast",
                station = RadioStation(
                    id = 1,
                    name = "Vikerraadio",
                    url = "https://icecast.err.ee/vikerraadio.mp3",
                    category = "Uudised",
                    countryCode = "EE",
                    isFavorite = true
                ),
                days = WORK_DAYS,
                recordAudio = true,
                canSave = true,
                showDelete = true,
                onToggleInputMode = {},
                onHourChange = {},
                onMinuteChange = {},
                onOpenStationPicker = {},
                onDaysChange = {},
                onRecordAudioChange = {},
                onDelete = {},
                onDismiss = {},
                onSave = {}
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 720, heightDp = 380, backgroundColor = 0xFF0A0C10)
@Composable
private fun AlarmFormLandscapePreview() {
    KellraadioTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            AlarmFormLandscapeLayout(
                title = "Sea äratus",
                hour = 15,
                minute = 0,
                isKeyboardMode = false,
                countdownText = "Heliseb 6 t 28 min pärast",
                station = RadioStation(
                    id = 2,
                    name = "Legendaarne",
                    url = "https://stream.legendaarne.ee/live",
                    category = "Pop",
                    countryCode = "EE",
                    isFavorite = true
                ),
                days = setOf(Calendar.WEDNESDAY),
                recordAudio = false,
                canSave = true,
                showDelete = false,
                onToggleInputMode = {},
                onHourChange = {},
                onMinuteChange = {},
                onOpenStationPicker = {},
                onDaysChange = {},
                onRecordAudioChange = {},
                onDelete = null,
                onDismiss = {},
                onSave = {}
            )
        }
    }
}
