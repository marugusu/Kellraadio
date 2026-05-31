package app.radiorecalarm.ui

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.widget.Toast
import androidx.compose.material.icons.outlined.Info
import app.radiorecalarm.MusicInfoRepository
import app.radiorecalarm.SongAdditionalInfo
import app.radiorecalarm.HistoryItem
import kotlinx.coroutines.launch
import app.radiorecalarm.RadioStationRepository
import app.radiorecalarm.R
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    repository: RadioStationRepository,
    onPlayStationByName: (String) -> Unit,
    onPlayRecording: (java.io.File) -> Unit,
    onDeleteRecording: (java.io.File) -> Unit,
    modifier: Modifier = Modifier
) {
    val historyItems by repository.historyItems.collectAsState(initial = emptyList())
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val scope = rememberCoroutineScope()
    var loadingItemId by remember { mutableStateOf<Long?>(null) }
    var selectedSongInfo by remember { mutableStateOf<SongAdditionalInfo?>(null) }
    var selectedHistoryItem by remember { mutableStateOf<HistoryItem?>(null) }
    var selectedDateMillis by remember { mutableStateOf<Long?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    var activeSubTab by remember { mutableStateOf(0) }

    val activeDatesUTC = remember(historyItems) {
        val localFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val utcFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        historyItems.map { item ->
            val dateStr = localFormat.format(Date(item.timestamp))
            utcFormat.parse(dateStr)?.time ?: 0L
        }.toSet()
    }

    val filteredItems = remember(historyItems, selectedDateMillis) {
        if (selectedDateMillis == null) {
            historyItems
        } else {
            val cal = Calendar.getInstance().apply {
                timeZone = TimeZone.getTimeZone("UTC")
                timeInMillis = selectedDateMillis!!
            }
            val year = cal.get(Calendar.YEAR)
            val month = cal.get(Calendar.MONTH)
            val day = cal.get(Calendar.DAY_OF_MONTH)

            val localCal = Calendar.getInstance().apply {
                set(year, month, day, 0, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val startOfDay = localCal.timeInMillis
            localCal.set(Calendar.HOUR_OF_DAY, 23)
            localCal.set(Calendar.MINUTE, 59)
            localCal.set(Calendar.SECOND, 59)
            val endOfDay = localCal.timeInMillis

            historyItems.filter { it.timestamp in startOfDay..endOfDay }
        }
    }

    val groupedHistory = remember(filteredItems, context) {
        filteredItems.groupBy { item -> getDateHeader(context, item.timestamp) }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(start = if (isLandscape) 8.dp else 16.dp, end = 16.dp)
                .padding(top = if (isLandscape) 12.dp else 0.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.History, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.history_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Row {
                if (activeSubTab == 0) {
                    IconButton(onClick = { showDatePicker = true }) {
                        Icon(
                            Icons.Default.Event,
                            null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        TabRow(
            selectedTabIndex = activeSubTab,
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (isLandscape) 8.dp else 16.dp)
        ) {
            Tab(
                selected = activeSubTab == 0,
                onClick = { activeSubTab = 0 },
                text = { Text(stringResource(R.string.history_subtab_songs)) }
            )
            Tab(
                selected = activeSubTab == 1,
                onClick = { activeSubTab = 1 },
                text = { Text(stringResource(R.string.history_subtab_recordings)) }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = if (isLandscape) 8.dp else 16.dp, end = 16.dp)
        ) {
            if (activeSubTab == 0) {
                if (selectedDateMillis != null) {
                    InputChip(
                        selected = true,
                        onClick = { selectedDateMillis = null },
                        label = {
                            val dateStr = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).apply {
                                timeZone = TimeZone.getTimeZone("UTC")
                            }.format(Date(selectedDateMillis!!))
                            Text(dateStr)
                        },
                        trailingIcon = { Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp)) },
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                if (filteredItems.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            if (selectedDateMillis != null) stringResource(R.string.history_empty_day)
                            else stringResource(R.string.history_empty),
                            color = Color.Gray
                        )
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 80.dp)) {
                        groupedHistory.forEach { (dateHeader, itemsInGroup) ->
                            stickyHeader {
                                Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.background) {
                                    Text(
                                        text = dateHeader,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(top = 0.dp, bottom = 8.dp)
                                    )
                                }
                            }
                            items(itemsInGroup, key = { it.id }) { item ->
                                HistoryRow(
                                    item = item,
                                    isLoading = (loadingItemId == item.id),
                                    onSearchClick = { openSearch(context, "${item.artist} ${item.title}") },
                                    onSpotifyClick = { openSpotify(context, "${item.artist} ${item.title}") },
                                    onPlayStationClick = { onPlayStationByName(item.stationName) },
                                    onInfoClick = {
                                        scope.launch {
                                            loadingItemId = item.id
                                            var foundAny = false
                                            MusicInfoRepository.fetchInfo(item.artist, item.title).collect { info ->
                                                foundAny = true
                                                selectedSongInfo = info
                                                selectedHistoryItem = item
                                                loadingItemId = null
                                            }
                                            if (!foundAny) {
                                                loadingItemId = null
                                                Toast.makeText(context, context.getString(R.string.info_not_found), Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            } else {
                RecordingsView(
                    context = context,
                    onPlayRecording = onPlayRecording,
                    onDeleteRecording = onDeleteRecording
                )
            }
        }
    }

    if (selectedSongInfo != null && selectedHistoryItem != null) {
        ModalBottomSheet(
            onDismissRequest = {
                selectedSongInfo = null
                selectedHistoryItem = null
            },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            SongInfoSheet(
                artist = selectedHistoryItem!!.artist,
                title = selectedHistoryItem!!.title,
                stationName = selectedHistoryItem!!.stationName,
                bitrate = "",
                streamUrl = "",
                info = selectedSongInfo!!,
                onDismiss = {
                    selectedSongInfo = null
                    selectedHistoryItem = null
                }
            )
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean = activeDatesUTC.contains(utcTimeMillis)
                override fun isSelectableYear(year: Int): Boolean = year >= 2024
            }
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = { selectedDateMillis = datePickerState.selectedDateMillis; showDatePicker = false }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        ) {
            DatePicker(
                state = datePickerState,
                title = if (isLandscape) null else {
                    {
                        Text(
                            stringResource(R.string.date_picker_title),
                            modifier = Modifier.padding(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 12.dp),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                headline = {
                    val formatter = SimpleDateFormat("EEE, MMM d", Locale.getDefault())
                    val dateText = datePickerState.selectedDateMillis?.let { formatter.format(Date(it)) }
                        ?: stringResource(R.string.date_picker_title)

                    val padding = if (isLandscape) {
                        PaddingValues(start = 24.dp, top = 16.dp, end = 24.dp)
                    } else {
                        PaddingValues(start = 24.dp, bottom = 16.dp)
                    }

                    Text(
                        dateText,
                        modifier = Modifier.padding(padding),
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            )
        }
    }
}

@Composable
fun HistoryRow(
    item: HistoryItem,
    isLoading: Boolean,
    onSearchClick: () -> Unit,
    onSpotifyClick: () -> Unit,
    onPlayStationClick: () -> Unit,
    onInfoClick: () -> Unit
) {
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val timeStr = timeFormat.format(Date(item.timestamp))

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            leadingContent = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .width(60.dp)
                        .clickable { onPlayStationClick() }
                ) {
                    Text(
                        text = timeStr,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = item.stationName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Medium
                    )
                }
            },
            headlineContent = {
                Text(
                    text = item.title,
                    fontWeight = FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            supportingContent = {
                Text(
                    text = item.artist,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isLoading) {
                        Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        }
                    } else {
                        IconButton(onClick = onInfoClick, modifier = Modifier.size(36.dp)) { 
                            Icon(
                                imageVector = Icons.Outlined.Info,
                                contentDescription = "Info",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    IconButton(onClick = onSearchClick, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Search, "YouTube", tint = Color.Red.copy(alpha = 0.7f), modifier = Modifier.size(22.dp))
                    }
                    IconButton(onClick = onSpotifyClick, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.MusicNote, "Spotify", tint = Color(0xFF1DB954).copy(alpha = 0.8f), modifier = Modifier.size(22.dp))
                    }
                }
            }
        )
    }
}

private fun getDateHeader(context: Context, timestamp: Long): String {
    val now = Calendar.getInstance()
    val time = Calendar.getInstance().apply { timeInMillis = timestamp }

    if (now.get(Calendar.YEAR) == time.get(Calendar.YEAR) && now.get(Calendar.DAY_OF_YEAR) == time.get(Calendar.DAY_OF_YEAR)) {
        return context.getString(R.string.history_today)
    }
    if (now.get(Calendar.YEAR) == time.get(Calendar.YEAR) && now.get(Calendar.DAY_OF_YEAR) - 1 == time.get(Calendar.DAY_OF_YEAR)) {
        return context.getString(R.string.history_yesterday)
    }

    val currentLang = context.resources.configuration.locales[0].language

    if (currentLang == "liv") {
        val day = time.get(Calendar.DAY_OF_MONTH)
        val year = time.get(Calendar.YEAR)
        val monthIndex = time.get(Calendar.MONTH)

        val livMonths = listOf(
            "Vīenakū",
            "Kīonkū",
            "Kūokõnkū",
            "Sullõkū",
            "Lēdõkū",
            "Jāņkū",
            "Hāinkū",
            "Eijõkū",
            "Sīgžkū",
            "Vīmkū",
            "Külmkū",
            "Tālõvkū"
        )

        return "$day. ${livMonths[monthIndex]} $year"
    }

    return SimpleDateFormat("dd. MMMM yyyy", Locale.getDefault()).format(Date(timestamp))
}

private fun openSearch(context: Context, query: String) {
    val encodedQuery = Uri.encode(query)
    try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=$encodedQuery"))) } catch (e: Exception) {}
}

private fun openSpotify(context: Context, query: String) {
    val encodedQuery = Uri.encode(query)
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("spotify:search:$encodedQuery")).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
        context.startActivity(intent)
    } catch (e: Exception) {
        try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://open.spotify.com/search/$encodedQuery"))) } catch (e2: Exception) {}
    }
}

@Composable
fun RecordingsView(
    context: Context,
    onPlayRecording: (java.io.File) -> Unit,
    onDeleteRecording: (java.io.File) -> Unit,
    modifier: Modifier = Modifier
) {
    var fileList by remember { mutableStateOf<List<java.io.File>>(emptyList()) }
    var recordingToDelete by remember { mutableStateOf<java.io.File?>(null) }
    
    val reloadRecordings = {
        val folder = java.io.File(context.getExternalFilesDir(null), "Recordings")
        val files = folder.listFiles { file ->
            file.isFile && (file.extension.equals("mp3", ignoreCase = true) || file.extension.equals("aac", ignoreCase = true))
        }?.sortedByDescending { it.lastModified() } ?: emptyList()
        fileList = files
    }

    LaunchedEffect(Unit) {
        reloadRecordings()
    }

    if (fileList.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.recording_empty), color = Color.Gray)
        }
    } else {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            items(fileList, key = { it.absolutePath }) { file ->
                RecordingRow(
                    file = file,
                    onPlay = { onPlayRecording(file) },
                    onDelete = { recordingToDelete = file },
                    onShare = { shareFile(context, file) }
                )
            }
        }
    }

    if (recordingToDelete != null) {
        AlertDialog(
            onDismissRequest = { recordingToDelete = null },
            title = { Text(stringResource(R.string.recording_delete_confirm_title)) },
            text = { Text(stringResource(R.string.recording_delete_confirm_text, recordingToDelete!!.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteRecording(recordingToDelete!!)
                        recordingToDelete = null
                        reloadRecordings()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { recordingToDelete = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
fun RecordingRow(
    file: java.io.File,
    onPlay: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit
) {
    val nameWithoutPrefix = file.name.removePrefix("Recording_")
    val extension = file.extension
    
    val timestampIndex = nameWithoutPrefix.indexOf("_202")
    
    val displayName = if (timestampIndex != -1) {
        nameWithoutPrefix.substring(0, timestampIndex).replace("_", " ")
    } else {
        nameWithoutPrefix.substringBeforeLast(".").replace("_", " ")
    }

    val timestampStr = if (timestampIndex != -1 && timestampIndex + 16 <= nameWithoutPrefix.length) {
        val rawTime = nameWithoutPrefix.substring(timestampIndex + 1, timestampIndex + 16)
        try {
            val parser = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
            val formatter = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
            parser.parse(rawTime)?.let { formatter.format(it) } ?: rawTime
        } catch (e: Exception) {
            rawTime
        }
    } else {
        SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(file.lastModified()))
    }

    val sizeInMb = file.length().toDouble() / (1024 * 1024)
    val sizeStr = String.format("%.2f MB", sizeInMb)

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            leadingContent = {
                IconButton(onClick = onPlay, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = stringResource(R.string.action_play),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                }
            },
            headlineContent = {
                Text(
                    text = displayName,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            supportingContent = {
                Text(
                    text = "$timestampStr • $sizeStr • ${extension.uppercase()}",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = Color.Gray
                )
            },
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onShare, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = stringResource(R.string.action_share),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = stringResource(R.string.action_delete),
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        )
    }
}

private fun shareFile(context: Context, file: java.io.File) {
    try {
        val uri: Uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = if (file.extension.equals("aac", ignoreCase = true)) "audio/aac" else "audio/mpeg"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooserTitle = context.getString(R.string.share_title)
        val chooser = Intent.createChooser(intent, chooserTitle).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    } catch (e: Exception) {
        Toast.makeText(context, "Jagamise viga: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}
