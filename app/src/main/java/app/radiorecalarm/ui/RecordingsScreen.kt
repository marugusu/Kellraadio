package app.radiorecalarm.ui

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.radiorecalarm.AppConfig
import app.radiorecalarm.HistoryItem
import app.radiorecalarm.MusicInfoRepository
import app.radiorecalarm.R
import app.radiorecalarm.RadioStationRepository
import app.radiorecalarm.RecordingEntry
import app.radiorecalarm.RecordingFilter
import app.radiorecalarm.RecordingKind
import app.radiorecalarm.RecordingLibrary
import app.radiorecalarm.SongAdditionalInfo
import app.radiorecalarm.ui.theme.KellraadioTheme
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun RecordingsScreen(
    repository: RadioStationRepository,
    onPlayStationByName: (String) -> Unit,
    onPlayRecording: (RecordingEntry) -> Unit,
    onDeleteRecording: (File) -> Unit,
    activeStreamUrl: String = "",
    isPlaying: Boolean = false,
    playbackPosition: Long = 0L,
    playbackDuration: Long = 0L,
    onSeek: (Long) -> Unit = {},
    isRecording: Boolean = false,
    recordingDuration: Long = 0L,
    recordingStation: String = "",
    recordingTrack: String = "",
    recordingCount: Int = 0,
    activeRecordingFilePath: String = "",
    onStopRecording: () -> Unit = {},
    onClearHistory: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val historyItems by repository.historyItems.collectAsState(initial = emptyList())
    val spotifyState by app.radiorecalarm.spotify.SpotifyRepository.uiState.collectAsState()
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val scope = rememberCoroutineScope()

    var activeSubTab by rememberSaveable { mutableStateOf(0) }

    // --- Salvestiste olek ---
    var allRecordings by remember { mutableStateOf<List<RecordingEntry>>(emptyList()) }
    var recordingsFilter by rememberSaveable { mutableStateOf(RecordingFilter.ALL) }
    var showSearchBar by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedRecordingDateMillis by rememberSaveable { mutableStateOf<Long?>(null) }
    var recordingToDelete by remember { mutableStateOf<RecordingEntry?>(null) }

    // --- Laulude ajaloo olek ---
    var loadingItemId by remember { mutableStateOf<Long?>(null) }
    var selectedHistoryDateMillis by rememberSaveable { mutableStateOf<Long?>(null) }
    var showClearHistoryDialog by rememberSaveable { mutableStateOf(false) }

    // --- Ühine SongInfoSheet + DatePicker ---
    var sheetArtist by remember { mutableStateOf("") }
    var sheetTitle by remember { mutableStateOf("") }
    var sheetStation by remember { mutableStateOf("") }
    var sheetBitrate by remember { mutableStateOf("") }
    var sheetSongInfo by remember { mutableStateOf<SongAdditionalInfo?>(null) }
    var isLyricsLoading by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

    val reloadRecordings: suspend () -> Unit = {
        val rootDir = File(context.getExternalFilesDir(null), AppConfig.Recording.DEFAULT_AUDIO_DIR)
        val excludePath = activeRecordingFilePath.ifBlank { null }
        val scanned = withContext(Dispatchers.IO) {
            RecordingLibrary.scanRecordings(rootDir, excludeFilePath = excludePath)
        }
        allRecordings = scanned
    }

    LaunchedEffect(recordingCount, isRecording, activeRecordingFilePath) {
        if (recordingCount > 0 || !isRecording) {
            // Anname failisüsteemile hetke, et StreamRecordingCoordinator jõuaks .json ja cover.jpg sulgeda
            delay(300)
        }
        reloadRecordings()
    }

    val activeRecordingDatesUTC = remember(allRecordings) {
        computeUtcDaySet(allRecordings.map { it.timestampMs })
    }

    val filteredRecordings = remember(allRecordings, recordingsFilter, searchQuery, selectedRecordingDateMillis) {
        RecordingLibrary.filterRecordings(
            entries = allRecordings,
            kindFilter = recordingsFilter,
            query = searchQuery,
            selectedUtcDateMillis = selectedRecordingDateMillis
        )
    }

    val groupedRecordings = remember(filteredRecordings, context) {
        filteredRecordings.groupBy { entry -> getDateHeader(context, entry.timestampMs) }
    }

    val activeHistoryDatesUTC = remember(historyItems) {
        computeUtcDaySet(historyItems.map { it.timestamp })
    }

    val filteredHistoryItems = remember(historyItems, selectedHistoryDateMillis) {
        if (selectedHistoryDateMillis == null) {
            historyItems
        } else {
            val (startOfDay, endOfDay) = RecordingLibrary.localDayBoundsFromUtcSelection(selectedHistoryDateMillis!!)
            historyItems.filter { it.timestamp in startOfDay..endOfDay }
        }
    }

    val groupedHistory = remember(filteredHistoryItems, context) {
        filteredHistoryItems.groupBy { item -> getDateHeader(context, item.timestamp) }
    }

    Column(modifier = modifier.fillMaxSize()) {
        RecordingsHeader(
            activeSubTab = activeSubTab,
            isLandscape = isLandscape,
            showSearchBar = showSearchBar,
            hasHistory = historyItems.isNotEmpty(),
            hasClearHistoryAction = onClearHistory != null,
            hasActiveDateFilter = if (activeSubTab == 0) selectedRecordingDateMillis != null else selectedHistoryDateMillis != null,
            onToggleSearch = {
                showSearchBar = !showSearchBar
                if (!showSearchBar) searchQuery = ""
            },
            onOpenDatePicker = { showDatePicker = true },
            onClearHistoryClick = { showClearHistoryDialog = true }
        )

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
                text = {
                    Text(
                        text = stringResource(R.string.history_subtab_recordings),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            )
            Tab(
                selected = activeSubTab == 1,
                onClick = { activeSubTab = 1 },
                text = {
                    Text(
                        text = stringResource(R.string.history_subtab_songs),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = if (isLandscape) 8.dp else 16.dp, end = 16.dp)
        ) {
            if (activeSubTab == 0) {
                AnimatedVisibility(
                    visible = showSearchBar,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = {
                            Text(
                                text = stringResource(R.string.recordings_search_hint),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_close))
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    )
                }

                if (selectedRecordingDateMillis != null) {
                    DateFilterChip(
                        utcMillis = selectedRecordingDateMillis!!,
                        onClear = { selectedRecordingDateMillis = null }
                    )
                }

                AnimatedVisibility(
                    visible = isRecording,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    ActiveRecordingBanner(
                        recordingTrack = recordingTrack,
                        recordingStation = recordingStation,
                        recordingDurationMs = recordingDuration,
                        onStopClick = onStopRecording,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                val trackCount = remember(allRecordings) { allRecordings.count { it.kind == RecordingKind.TRACK } }
                val broadcastCount = remember(allRecordings) { allRecordings.count { it.kind == RecordingKind.BROADCAST } }

                if (allRecordings.isNotEmpty()) {
                    RecordingFilterChips(
                        selectedFilter = recordingsFilter,
                        totalCount = allRecordings.size,
                        trackCount = trackCount,
                        broadcastCount = broadcastCount,
                        onFilterSelect = { recordingsFilter = it },
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                if (filteredRecordings.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = if (allRecordings.isEmpty()) {
                                stringResource(R.string.recording_empty)
                            } else {
                                stringResource(R.string.recordings_empty_filtered)
                            },
                            color = Color.Gray
                        )
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 80.dp)
                    ) {
                        groupedRecordings.forEach { (dateHeader, itemsInGroup) ->
                            stickyHeader {
                                DateStickyHeader(dateHeader)
                            }
                            items(itemsInGroup, key = { it.audioFile.absolutePath }) { entry ->
                                val fileUri = Uri.fromFile(entry.audioFile).toString()
                                val isCurrent = activeStreamUrl == fileUri
                                RecordingRow(
                                    entry = entry,
                                    onPlay = { onPlayRecording(entry) },
                                    onInfoClick = {
                                        val initialInfo = RecordingLibrary.toSongAdditionalInfo(entry)
                                        sheetArtist = entry.artist
                                        sheetTitle = entry.title
                                        sheetStation = entry.stationName
                                        sheetBitrate = entry.bitrate ?: ""
                                        sheetSongInfo = initialInfo

                                        if (entry.kind == RecordingKind.TRACK &&
                                            entry.artist.isNotBlank() &&
                                            (entry.lyrics.isNullOrBlank() || entry.coverArtFile == null || (spotifyState.isConnected && initialInfo.spotifyTrackId == null))
                                        ) {
                                            scope.launch {
                                                isLyricsLoading = true
                                                MusicInfoRepository.fetchInfo(entry.artist, entry.title).collect { fetched ->
                                                    if (sheetTitle == entry.title && sheetArtist == entry.artist) {
                                                        sheetSongInfo = SongAdditionalInfo(
                                                            lyrics = fetched.lyrics ?: initialInfo.lyrics,
                                                            coverArtUrl = initialInfo.coverArtUrl ?: fetched.coverArtUrl,
                                                            album = initialInfo.album ?: fetched.album,
                                                            year = initialInfo.year ?: fetched.year,
                                                            genre = initialInfo.genre ?: fetched.genre,
                                                            trackNumber = initialInfo.trackNumber ?: fetched.trackNumber,
                                                            durationMs = initialInfo.durationMs ?: fetched.durationMs,
                                                            spotifyTrackId = fetched.spotifyTrackId ?: initialInfo.spotifyTrackId,
                                                            spotifyTrackUri = fetched.spotifyTrackUri ?: initialInfo.spotifyTrackUri
                                                        )
                                                    }
                                                }
                                                isLyricsLoading = false
                                            }
                                        }
                                    },
                                    onShare = { shareFile(context, entry.audioFile) },
                                    onDelete = { recordingToDelete = entry },
                                    isCurrent = isCurrent,
                                    isPlaying = isPlaying && isCurrent,
                                    playbackPosition = if (isCurrent) playbackPosition else 0L,
                                    playbackDuration = if (isCurrent) playbackDuration else 0L,
                                    onSeek = onSeek
                                )
                            }
                        }
                    }
                }
            } else {
                if (selectedHistoryDateMillis != null) {
                    DateFilterChip(
                        utcMillis = selectedHistoryDateMillis!!,
                        onClear = { selectedHistoryDateMillis = null }
                    )
                }

                if (filteredHistoryItems.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            if (selectedHistoryDateMillis != null) stringResource(R.string.history_empty_day)
                            else stringResource(R.string.history_empty),
                            color = Color.Gray
                        )
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 80.dp)
                    ) {
                        groupedHistory.forEach { (dateHeader, itemsInGroup) ->
                            stickyHeader {
                                DateStickyHeader(dateHeader)
                            }
                            items(itemsInGroup, key = { it.id }) { item ->
                                HistoryRow(
                                    item = item,
                                    isLoading = (loadingItemId == item.id),
                                    onPlayStationClick = { onPlayStationByName(item.stationName) },
                                    onInfoClick = {
                                        scope.launch {
                                            loadingItemId = item.id
                                            isLyricsLoading = true
                                            var foundAny = false
                                            MusicInfoRepository.fetchInfo(item.artist, item.title).collect { info ->
                                                foundAny = true
                                                sheetArtist = item.artist
                                                sheetTitle = item.title
                                                sheetStation = item.stationName
                                                sheetBitrate = ""
                                                sheetSongInfo = info
                                                loadingItemId = null
                                            }
                                            isLyricsLoading = false
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
            }
        }
    }

    if (sheetSongInfo != null) {
        val currentTrackId = sheetSongInfo?.spotifyTrackId
        LaunchedEffect(currentTrackId, spotifyState.isConnected) {
            if (spotifyState.isConnected && !currentTrackId.isNullOrBlank()) {
                app.radiorecalarm.spotify.SpotifyRepository.checkTrackSaved(currentTrackId)
            }
        }
        SongInfoModalSheet(
            artist = sheetArtist,
            title = sheetTitle,
            stationName = sheetStation,
            bitrate = sheetBitrate,
            streamUrl = "",
            info = sheetSongInfo!!,
            isLoadingLyrics = isLyricsLoading,
            showSpotifySaveButton = spotifyState.isConnected && !currentTrackId.isNullOrBlank(),
            isSpotifyTrackSaved = currentTrackId != null && spotifyState.savedTrackIds.contains(currentTrackId),
            isSpotifySaveInProgress = currentTrackId != null && spotifyState.savingTrackIds.contains(currentTrackId),
            onSaveToSpotify = {
                scope.launch {
                    saveTrackToSpotifyWithFeedback(context, currentTrackId)
                }
            },
            onDismiss = { sheetSongInfo = null }
        )
    }

    if (showDatePicker) {
        val activeDatesForTab = if (activeSubTab == 0) activeRecordingDatesUTC else activeHistoryDatesUTC
        val datePickerState = rememberDatePickerState(
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean = activeDatesForTab.contains(utcTimeMillis)
                override fun isSelectableYear(year: Int): Boolean = year >= 2024
            }
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (activeSubTab == 0) {
                            selectedRecordingDateMillis = datePickerState.selectedDateMillis
                        } else {
                            selectedHistoryDateMillis = datePickerState.selectedDateMillis
                        }
                        showDatePicker = false
                    }
                ) {
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
                    Text(dateText, modifier = Modifier.padding(padding), style = MaterialTheme.typography.titleLarge)
                }
            )
        }
    }

    if (recordingToDelete != null) {
        val target = recordingToDelete!!
        AlertDialog(
            onDismissRequest = { recordingToDelete = null },
            shape = RoundedCornerShape(8.dp),
            title = { Text(stringResource(R.string.recording_delete_confirm_title)) },
            text = { Text(stringResource(R.string.recording_delete_confirm_text, target.title)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteRecording(target.audioFile)
                        recordingToDelete = null
                        scope.launch { reloadRecordings() }
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

    if (showClearHistoryDialog && onClearHistory != null) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            shape = RoundedCornerShape(8.dp),
            title = { Text(stringResource(R.string.history_delete_confirm_title)) },
            text = { Text(stringResource(R.string.history_delete_confirm_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClearHistory()
                        showClearHistoryDialog = false
                    },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showClearHistoryDialog = false },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun RecordingsHeader(
    activeSubTab: Int,
    isLandscape: Boolean,
    showSearchBar: Boolean,
    hasHistory: Boolean,
    hasClearHistoryAction: Boolean,
    hasActiveDateFilter: Boolean,
    onToggleSearch: () -> Unit,
    onOpenDatePicker: () -> Unit,
    onClearHistoryClick: () -> Unit
) {
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
            Icon(Icons.Default.LibraryMusic, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.recordings_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            if (activeSubTab == 0) {
                IconButton(onClick = onToggleSearch) {
                    Icon(
                        imageVector = if (showSearchBar) Icons.Default.Close else Icons.Default.Search,
                        contentDescription = stringResource(R.string.action_search),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            } else if (hasClearHistoryAction && hasHistory) {
                IconButton(onClick = onClearHistoryClick) {
                    Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = stringResource(R.string.settings_clear_history),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            IconButton(onClick = onOpenDatePicker) {
                Icon(
                    imageVector = Icons.Default.Event,
                    contentDescription = stringResource(R.string.date_picker_title),
                    tint = if (hasActiveDateFilter) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun DateFilterChip(
    utcMillis: Long,
    onClear: () -> Unit
) {
    val dateStr = remember(utcMillis) {
        SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date(utcMillis))
    }
    InputChip(
        selected = true,
        onClick = onClear,
        label = { Text(dateStr) },
        trailingIcon = { Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp)) },
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
private fun DateStickyHeader(dateHeader: String) {
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

@Composable
private fun RecordingFilterChips(
    selectedFilter: RecordingFilter,
    totalCount: Int,
    trackCount: Int,
    broadcastCount: Int,
    onFilterSelect: (RecordingFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val chipColors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
        FilterChip(
            selected = selectedFilter == RecordingFilter.ALL,
            onClick = { onFilterSelect(RecordingFilter.ALL) },
            label = { Text(stringResource(R.string.recordings_filter_all, totalCount)) },
            colors = chipColors
        )
        FilterChip(
            selected = selectedFilter == RecordingFilter.TRACKS,
            onClick = { onFilterSelect(RecordingFilter.TRACKS) },
            label = { Text(stringResource(R.string.recordings_filter_tracks, trackCount)) },
            colors = chipColors
        )
        FilterChip(
            selected = selectedFilter == RecordingFilter.BROADCASTS,
            onClick = { onFilterSelect(RecordingFilter.BROADCASTS) },
            label = { Text(stringResource(R.string.recordings_filter_broadcasts, broadcastCount)) },
            colors = chipColors
        )
    }
}

@Composable
fun ActiveRecordingBanner(
    recordingTrack: String,
    recordingStation: String,
    recordingDurationMs: Long,
    onStopClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "active_rec_dot")
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(550, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "active_rec_alpha"
    )
    val subtitle = listOf(recordingStation, recordingTrack)
        .filter { it.isNotBlank() }
        .joinToString(" • ")
        .ifBlank { stringResource(R.string.recording_default_station) }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f)),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .alpha(dotAlpha)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.error)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${stringResource(R.string.recording_active_banner_title)} • ${formatTime(recordingDurationMs)}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            FilledTonalButton(
                onClick = onStopClick,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.2f),
                    contentColor = MaterialTheme.colorScheme.error
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.action_stop), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
fun RecordingRow(
    entry: RecordingEntry,
    onPlay: () -> Unit,
    onInfoClick: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    isCurrent: Boolean,
    isPlaying: Boolean,
    playbackPosition: Long,
    playbackDuration: Long,
    onSeek: (Long) -> Unit
) {
    val view = LocalView.current
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }

    val timeStr = remember(entry.timestampMs) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(entry.timestampMs))
    }
    val sizeStr = remember(entry.fileSizeBytes) {
        String.format(Locale.US, "%.1f MB", entry.fileSizeBytes.toDouble() / (1024 * 1024))
    }
    val durationStr = remember(entry.durationSeconds) {
        if (entry.durationSeconds > 0L) formatTime(entry.durationSeconds * 1000L) else ""
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrent && isPlaying) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.18f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        border = if (isCurrent && isPlaying) {
            BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        },
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                view.performHapticTap()
                onPlay()
            }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RecordingThumbnail(
                    coverArtFile = entry.coverArtFile,
                    kind = entry.kind,
                    isCurrent = isCurrent,
                    isPlaying = isPlaying,
                    context = context
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (isCurrent && isPlaying) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )

                    val subtitle = when (entry.kind) {
                        RecordingKind.TRACK -> listOfNotNull(
                            entry.artist.takeIf { it.isNotBlank() },
                            entry.album?.takeIf { it.isNotBlank() }
                        ).joinToString(" • ")
                        RecordingKind.BROADCAST -> entry.stationName.takeIf {
                            it.isNotBlank() && !it.equals(entry.title, ignoreCase = true)
                        } ?: ""
                    }
                    if (subtitle.isNotBlank()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (entry.kind == RecordingKind.BROADCAST) {
                            SmallMetaBadge(
                                text = stringResource(R.string.recording_badge_broadcast),
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                        if (entry.partial) {
                            SmallMetaBadge(
                                text = stringResource(R.string.recording_badge_partial),
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }

                        val metaParts = buildList {
                            if (entry.stationName.isNotBlank()) add(entry.stationName)
                            if (durationStr.isNotBlank()) add(durationStr)
                            add(sizeStr)
                            add(timeStr)
                        }
                        Text(
                            text = metaParts.joinToString(" • "),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(4.dp))

                IconButton(onClick = onInfoClick, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = stringResource(R.string.action_song_info),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Box {
                    IconButton(onClick = { showMenu = true }, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.action_more),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_share)) },
                            leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                onShare()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            },
                            onClick = {
                                showMenu = false
                                onDelete()
                            }
                        )
                    }
                }
            }

            if (isCurrent && (isPlaying || playbackPosition > 0L)) {
                var sliderPosition by remember { mutableStateOf<Float?>(null) }
                val effectiveDuration = app.radiorecalarm.PlaybackStatusSync.resolveEffectiveDurationMs(
                    playbackDurationMs = playbackDuration,
                    metadataDurationSeconds = entry.durationSeconds
                )
                val currentPosition = sliderPosition ?: playbackPosition.toFloat()
                val safeDuration = if (effectiveDuration > 0L) effectiveDuration else 1L
                val finalSliderValue = currentPosition.coerceIn(0f, safeDuration.toFloat())

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 12.dp)
                ) {
                    Slider(
                        value = finalSliderValue,
                        onValueChange = { sliderPosition = it },
                        onValueChangeFinished = {
                            sliderPosition?.let { onSeek(it.toLong()) }
                            sliderPosition = null
                        },
                        enabled = effectiveDuration > 0L,
                        valueRange = 0f..safeDuration.toFloat(),
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(24.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatTime(finalSliderValue.toLong()),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = formatTime(effectiveDuration),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecordingThumbnail(
    coverArtFile: File?,
    kind: RecordingKind,
    isCurrent: Boolean,
    isPlaying: Boolean,
    context: Context
) {
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center
    ) {
        if (coverArtFile != null) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(coverArtFile)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = if (isCurrent) 0.45f else 0.25f))
            )
        } else {
            Icon(
                imageVector = if (kind == RecordingKind.BROADCAST) Icons.Default.Podcasts else Icons.Default.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                modifier = Modifier.size(34.dp)
            )
        }

        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(
                    if (isCurrent && isPlaying) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        Color.Black.copy(alpha = 0.55f)
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isCurrent && isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isCurrent && isPlaying) {
                    stringResource(R.string.action_pause)
                } else {
                    stringResource(R.string.action_play)
                },
                tint = if (isCurrent && isPlaying) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    Color.White
                },
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun SmallMetaBadge(
    text: String,
    containerColor: Color,
    contentColor: Color
) {
    Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(4.dp)
    ) {
        Text(
            text = text,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
            maxLines = 1
        )
    }
}

@Composable
fun HistoryRow(
    item: HistoryItem,
    isLoading: Boolean,
    onPlayStationClick: () -> Unit,
    onInfoClick: () -> Unit
) {
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val timeStr = timeFormat.format(Date(item.timestamp))

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onInfoClick() }
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
                if (isLoading) {
                    Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                } else {
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        )
    }
}

private fun computeUtcDaySet(timestamps: List<Long>): Set<Long> {
    val localFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val utcFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    return timestamps.map { ts ->
        val dateStr = localFormat.format(Date(ts))
        utcFormat.parse(dateStr)?.time ?: 0L
    }.toSet()
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
            "Vīenakū", "Kīonkū", "Kūokõnkū", "Sullõkū",
            "Lēdõkū", "Jāņkū", "Hāinkū", "Eijõkū",
            "Sīgžkū", "Vīmkū", "Külmkū", "Tālõvkū"
        )
        return "$day. ${livMonths[monthIndex]} $year"
    }

    return SimpleDateFormat("dd. MMMM yyyy", Locale.getDefault()).format(Date(timestamp))
}

private fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    return if (hours > 0) {
        String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}

private fun shareFile(context: Context, file: File) {
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

@Preview(showBackground = true, backgroundColor = 0xFF0A0C12)
@Composable
private fun RecordingRowTrackPreview() {
    KellraadioTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            RecordingRow(
                entry = RecordingEntry(
                    audioFile = File("01 - Above My Head - No Empathy.mp3"),
                    sidecarFile = null,
                    coverArtFile = null,
                    kind = RecordingKind.TRACK,
                    title = "No Empathy",
                    artist = "ABOVE MY HEAD",
                    album = "Above My Head",
                    stationName = "Rock FM",
                    codec = "mp3",
                    durationSeconds = 222L,
                    fileSizeBytes = 8_800_000L,
                    timestampMs = System.currentTimeMillis(),
                    partial = true,
                    hasSidecar = true
                ),
                onPlay = {},
                onInfoClick = {},
                onShare = {},
                onDelete = {},
                isCurrent = true,
                isPlaying = true,
                playbackPosition = 95_000L,
                playbackDuration = 222_000L,
                onSeek = {}
            )
            RecordingRow(
                entry = RecordingEntry(
                    audioFile = File("Vikerraadio_20261120_080000.aac"),
                    sidecarFile = null,
                    coverArtFile = null,
                    kind = RecordingKind.BROADCAST,
                    title = "Vikerhommik",
                    artist = "Vikerraadio",
                    stationName = "Vikerraadio",
                    codec = "aac",
                    durationSeconds = 3610L,
                    fileSizeBytes = 42_500_000L,
                    timestampMs = System.currentTimeMillis(),
                    partial = false,
                    hasSidecar = true
                ),
                onPlay = {},
                onInfoClick = {},
                onShare = {},
                onDelete = {},
                isCurrent = false,
                isPlaying = false,
                playbackPosition = 0L,
                playbackDuration = 0L,
                onSeek = {}
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0C12)
@Composable
private fun ActiveRecordingBannerPreview() {
    KellraadioTheme {
        ActiveRecordingBanner(
            recordingTrack = "Above My Head - No Empathy",
            recordingStation = "Rock FM",
            recordingDurationMs = 75_000L,
            onStopClick = {},
            modifier = Modifier.padding(16.dp)
        )
    }
}
