package app.radiorecalarm

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import app.radiorecalarm.ui.*
import app.radiorecalarm.ui.theme.KellraadioTheme

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        volumeControlStream = android.media.AudioManager.STREAM_MUSIC
        setContent {
            KellraadioTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    RaadioEkraan()
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Kui on TV ja äpp läheb taustale, peatame raadio
        if (isTv(this) && !isChangingConfigurations) {
            val intent = Intent(this, RadioService::class.java).apply {
                action = RadioService.ACTION_STOP
            }
            startService(intent)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun RaadioEkraan(
    mainViewModel: MainViewModel = viewModel(),
    playerViewModel: PlayerViewModel = viewModel()
) {
    val context = LocalContext.current
    val config = LocalConfiguration.current
    val isLandscape = config.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val screenWidth = config.screenWidthDp
    val playerWeight = if (screenWidth < AppConfig.UI.Layout.WIDTH_THRESHOLD_WIDE_SCREEN_DP)
        AppConfig.UI.Layout.PLAYER_WEIGHT_NORMAL
    else
        AppConfig.UI.Layout.PLAYER_WEIGHT_WIDE

    val state by mainViewModel.uiState.collectAsState()
    val songInfo by playerViewModel.songInfo.collectAsState()
    val isFetchingInfo by playerViewModel.isFetching.collectAsState()

    // **PARANDATUD LOOGIKA**
    // Teaser on nähtav, kui songInfo objektis on reaalset sisu (pilt või album)
    val showTeaser = songInfo?.coverArtUrl != null || songInfo?.album != null

    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()

    // Päri lauluinfot (kaanepilt, laulusõnad) VAID siis, kui äpp on ekraanil nähtav (RESUMED)
    // See hoiab ära telefoni aku ja modemi kurnamise taustal esitamisel.
    LaunchedEffect(state.parsedArtist, state.parsedTitle, lifecycleState) {
        if (lifecycleState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) {
            playerViewModel.fetchSongInfo(state.parsedArtist, state.parsedTitle)
        } else {
            playerViewModel.cancelFetch()
        }
    }

    LaunchedEffect(lifecycleState) {
        if (lifecycleState == androidx.lifecycle.Lifecycle.State.RESUMED) {
            LocalBroadcastManager.getInstance(context).sendBroadcast(Intent(RadioService.ACTION_GET_STATUS))
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(state.isPlaying) {
        val w = (context as? android.app.Activity)?.window
        if (state.isPlaying) w?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else w?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    val displayStations = remember(state.stations, state.hideRemoteStations) {
        StationListFilter.visibleStations(state.stations, state.hideRemoteStations)
    }

    val favoriteStations = remember(displayStations) {
        StationListFilter.filterAndSortStations(displayStations, StationListFilter.CATEGORY_FAVORITES)
    }

    val mainCategories = remember(displayStations, state.hideRemoteStations) {
        StationListFilter.buildMainCategories(displayStations, state.hideRemoteStations) {
            getCachedCountryDisplayName(it)
        }
    }

    LaunchedEffect(mainCategories, state.selectedCategory) {
        if (mainCategories.isNotEmpty()) {
            val validCategory = StationListFilter.resolveValidCategory(state.selectedCategory, mainCategories)
            if (validCategory != state.selectedCategory) {
                mainViewModel.onCategorySelected(validCategory)
            }
        }
    }

    val currentSubCategories = remember(state.selectedCategory, displayStations) {
        StationListFilter.buildSubCategories(state.selectedCategory, displayStations)
    }

    val filteredStations = remember(state.selectedCategory, state.selectedSubCategories, displayStations) {
        StationListFilter.filterAndSortStations(displayStations, state.selectedCategory, state.selectedSubCategories)
    }

    val nextAlarmInfo = remember(state.alarms) {
        state.alarms.filter { it.isEnabled }.map { alarm ->
            Pair(AlarmUtils.findNextAlarmTime(alarm.hour, alarm.minute, alarm.days), alarm)
        }.minByOrNull { it.first }
    }
    val alarmInfoForUI = nextAlarmInfo?.let { Pair(it.first, it.second.stationName) }
    val alarmDaysForUI = nextAlarmInfo?.second?.days ?: emptySet()

    val navRadioTitle = stringResource(R.string.nav_radio)
    val navAlarmsTitle = stringResource(R.string.nav_alarms)
    val navHistoryTitle = stringResource(R.string.nav_history)
    val navAddTitle = stringResource(R.string.nav_add_channel)
    val navSettingsTitle = stringResource(R.string.nav_settings)

    if (isLandscape) {
        val isLargeScreenHeight = config.screenHeightDp >= AppConfig.UI.Layout.HEIGHT_THRESHOLD_LARGE_LANDSCAPE_DP

        Row(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            NavigationRail(containerColor = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onSurface) {
                val railItemColors = NavigationRailItemDefaults.colors(
                    indicatorColor = Color.Transparent,
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )

                Spacer(modifier = Modifier.weight(1f))
                NavigationRailItem(selected = state.currentTab == 0, onClick = { mainViewModel.onTabSelected(0) }, icon = { Icon(Icons.Default.Radio, null) }, label = { Text(navRadioTitle) }, colors = railItemColors)
                NavigationRailItem(selected = state.currentTab == 1, onClick = { mainViewModel.onTabSelected(1) }, icon = { Icon(Icons.Default.Alarm, null) }, label = { Text(navAlarmsTitle) }, colors = railItemColors)
                NavigationRailItem(selected = state.currentTab == 2, onClick = { mainViewModel.onTabSelected(2) }, icon = { Icon(Icons.Default.History, null) }, label = { Text(navHistoryTitle) }, colors = railItemColors)
                NavigationRailItem(selected = state.currentTab == 3, onClick = { mainViewModel.onTabSelected(3) }, icon = { Icon(Icons.Default.AddCircleOutline, null) }, label = { Text(navAddTitle) }, colors = railItemColors)
                NavigationRailItem(
                    selected = state.currentTab == 4,
                    onClick = { mainViewModel.onTabSelected(4) },
                    icon = {
                        BadgedBox(badge = { if (state.hasAvailableUpdate) Badge() }) {
                            Icon(Icons.Default.Settings, null)
                        }
                    },
                    label = { Text(navSettingsTitle) },
                    colors = railItemColors
                )
                Spacer(modifier = Modifier.weight(1f))
            }
            VerticalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)

            Column(modifier = Modifier.weight(playerWeight).fillMaxHeight().padding(start = 12.dp, top = 12.dp, bottom = 16.dp, end = 12.dp)) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    PlayerControls(
                        selectedStation = displayStations.find { it.id == state.selectedStationId },
                        activeStationName = state.activeStationName,
                        isPlaying = state.isPlaying,
                        parsedTitle = state.parsedTitle,
                        parsedArtist = state.parsedArtist,
                        parsedExtra = state.parsedExtra,
                        playerStatus = state.playerStatus,
                        bitrateInfo = state.bitrate,
                        alarmInfo = alarmInfoForUI,
                        alarmDays = alarmDaysForUI,
                        sleepTimerMillis = state.sleepTimerRemaining,
                        isFavorite = displayStations.find { it.id == state.selectedStationId }?.isFavorite ?: false,
                        songInfo = songInfo,
                        isRecording = state.isRecording,
                        recordingDuration = state.recordingDuration,
                        onInfoClick = mainViewModel::openSongInfo,
                        onPlayPause = mainViewModel::onPlayPauseClicked,
                        onPlayStation = mainViewModel::onStationClicked,
                        onSleepClick = mainViewModel::openSleepTimerDialog,
                        onAlarmClick = {
                            if (state.currentTab == 3) Toast.makeText(context, context.getString(R.string.error_station_not_found), Toast.LENGTH_SHORT).show()
                            else if (state.alarms.isNotEmpty()) mainViewModel.onTabSelected(1)
                            else mainViewModel.openAlarmDialog(null)
                        },
                        onAlarmLongClick = { nextAlarmInfo?.second?.let { mainViewModel.openAlarmDialog(it) } },
                        onToggleFavorite = {
                            val currentStation = displayStations.find { it.id == state.selectedStationId }
                            if (currentStation != null) { mainViewModel.onToggleFavorite(currentStation) }
                        },
                        onRecordClick = mainViewModel::onRecordClicked,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (showTeaser) {
                    if (isLargeScreenHeight) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(top = 12.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                                .background(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(
                                            MaterialTheme.colorScheme.surface,
                                            Color(0xFF0C0E14)
                                        )
                                    )
                                )
                        ) {
                            SongInfoContentLandscape(artist = state.parsedArtist, title = state.parsedTitle, stationName = state.activeStationName, info = songInfo!!)
                        }
                    } else {
                        AnimatedVisibility(visible = !state.showSongInfoSheet, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                            SongInfoTeaser(info = songInfo, isLoading = isFetchingInfo, artist = state.parsedArtist, title = state.parsedTitle, stationName = state.activeStationName, onClick = mainViewModel::openSongInfo, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 12.dp), backgroundBrush = Brush.verticalGradient(colors = listOf(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.background)))
                        }
                    }
                }
            }

            Box(modifier = Modifier.weight(1f - playerWeight).fillMaxHeight()) {
                ContentScreens(
                    state = state,
                    allStations = displayStations,
                    filteredStations = filteredStations,
                    mainCategories = mainCategories,
                    subCategories = currentSubCategories,
                    viewModel = mainViewModel,
                    context = context
                )
            }
        }
    } else {
        Scaffold(
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
            bottomBar = {
                Column(modifier = Modifier.background(MaterialTheme.colorScheme.background)) {
                    AnimatedVisibility(visible = state.currentTab == 0 && showTeaser && !state.showSongInfoSheet, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                        SongInfoTeaser(info = songInfo, isLoading = isFetchingInfo, artist = state.parsedArtist, title = state.parsedTitle, stationName = state.activeStationName, onClick = mainViewModel::openSongInfo)
                    }
                    if (state.currentTab == 0 && showTeaser && !state.showSongInfoSheet) {
                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(brush = Brush.horizontalGradient(colors = listOf(Color.Transparent, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), Color.Transparent))))
                    } else {
                        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
                    }
                    NavigationBar(containerColor = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onSurface, tonalElevation = 0.dp) {
                        val navItemColors = NavigationBarItemDefaults.colors(
                            indicatorColor = Color.Transparent,
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                        NavigationBarItem(selected = state.currentTab == 0, onClick = { mainViewModel.onTabSelected(0) }, icon = { Icon(Icons.Default.Radio, null) }, label = { Text(navRadioTitle) }, colors = navItemColors)
                        NavigationBarItem(selected = state.currentTab == 1, onClick = { mainViewModel.onTabSelected(1) }, icon = { Icon(Icons.Default.Alarm, null) }, label = { Text(navAlarmsTitle) }, colors = navItemColors)
                        NavigationBarItem(selected = state.currentTab == 2, onClick = { mainViewModel.onTabSelected(2) }, icon = { Icon(Icons.Default.History, null) }, label = { Text(navHistoryTitle) }, colors = navItemColors)
                        NavigationBarItem(selected = state.currentTab == 3, onClick = { mainViewModel.onTabSelected(3) }, icon = { Icon(Icons.Default.AddCircleOutline, null) }, label = { Text(navAddTitle) }, colors = navItemColors)
                        NavigationBarItem(
                            selected = state.currentTab == 4,
                            onClick = { mainViewModel.onTabSelected(4) },
                            icon = {
                                BadgedBox(badge = { if (state.hasAvailableUpdate) Badge() }) {
                                    Icon(Icons.Default.Settings, null)
                                }
                            },
                            label = { Text(navSettingsTitle) },
                            colors = navItemColors
                        )
                    }
                }
            }
        ) { innerPadding ->
            Box(modifier = Modifier.fillMaxSize().padding(bottom = innerPadding.calculateBottomPadding())) {
                Column(modifier = Modifier.fillMaxSize()) {
                    PlayerControls(
                        selectedStation = displayStations.find { it.id == state.selectedStationId },
                        activeStationName = state.activeStationName,
                        isPlaying = state.isPlaying,
                        parsedTitle = state.parsedTitle,
                        parsedArtist = state.parsedArtist,
                        parsedExtra = state.parsedExtra,
                        playerStatus = state.playerStatus,
                        bitrateInfo = state.bitrate,
                        alarmInfo = alarmInfoForUI,
                        alarmDays = alarmDaysForUI,
                        sleepTimerMillis = state.sleepTimerRemaining,
                        isFavorite = displayStations.find { it.id == state.selectedStationId }?.isFavorite ?: false,
                        songInfo = songInfo,
                        isRecording = state.isRecording,
                        recordingDuration = state.recordingDuration,
                        onInfoClick = mainViewModel::openSongInfo,
                        onPlayPause = mainViewModel::onPlayPauseClicked,
                        onPlayStation = mainViewModel::onStationClicked,
                        onSleepClick = mainViewModel::openSleepTimerDialog,
                        onAlarmClick = {
                            if (state.currentTab == 3) Toast.makeText(context, context.getString(R.string.error_station_not_found), Toast.LENGTH_SHORT).show()
                            else if (state.alarms.isNotEmpty()) mainViewModel.onTabSelected(1)
                            else mainViewModel.openAlarmDialog(null)
                        },
                        onAlarmLongClick = { nextAlarmInfo?.second?.let { mainViewModel.openAlarmDialog(it) } },
                        onToggleFavorite = {
                            val currentStation = displayStations.find { it.id == state.selectedStationId }
                            if (currentStation != null) { mainViewModel.onToggleFavorite(currentStation) }
                        },
                        onRecordClick = mainViewModel::onRecordClicked,
                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 0.dp)
                    )
                    ContentScreens(
                        state = state,
                        allStations = displayStations,
                        filteredStations = filteredStations,
                        mainCategories = mainCategories,
                        subCategories = currentSubCategories,
                        viewModel = mainViewModel,
                        context = context
                    )
                }
            }
        }
    }

    if (state.showSleepDialog) { SleepTimerDialog(initialMillis = state.sleepTimerRemaining, onDismiss = mainViewModel::closeSleepTimerDialog) }
    if (state.showAlarmDialog) {
        val currentAlarmToEdit = state.alarmToEdit
        val stationForDialog = if (currentAlarmToEdit != null) { displayStations.find { it.name == currentAlarmToEdit.stationName } ?: RadioStation(0, currentAlarmToEdit.stationName, currentAlarmToEdit.stationUrl) } else { displayStations.find { it.id == state.selectedStationId } }
        AlarmDialog(selectedStation = stationForDialog, initialHour = currentAlarmToEdit?.hour, initialMinute = currentAlarmToEdit?.minute, initialDays = currentAlarmToEdit?.days ?: emptySet(), onDismiss = mainViewModel::closeAlarmDialog, onDelete = if (currentAlarmToEdit != null && currentAlarmToEdit.id != 0) { { mainViewModel.deleteAlarm(currentAlarmToEdit) } } else null, onAlarmSaved = mainViewModel::saveAlarm)
    }
    if (state.showActionSheetForStation != null) {
        val liveStation = displayStations.find { it.id == state.showActionSheetForStation!!.id } ?: state.showActionSheetForStation!!
        val index = favoriteStations.indexOfFirst { it.id == liveStation.id }
        val displayOrder = if (index != -1) index + 1 else 0
        StationActionSheet(
            station = liveStation,
            favoritePosition = displayOrder,
            totalFavorites = favoriteStations.size,
            onDismiss = mainViewModel::closeStationActionSheet,
            onToggleFavorite = { mainViewModel.onToggleFavorite(liveStation); mainViewModel.closeStationActionSheet() },
            onSetAlarm = { mainViewModel.openAlarmDialog(Alarm(hour = 7, minute = 0, days = emptySet(), stationName = liveStation.name, stationUrl = liveStation.url)) },
            onEdit = { mainViewModel.openEditStationDialog(liveStation) },
            onDelete = { mainViewModel.confirmDeleteStation(liveStation) },
            onMoveUp = { mainViewModel.moveStationUp(liveStation) },
            onMoveDown = { mainViewModel.moveStationDown(liveStation) },
            onMoveToTop = { mainViewModel.moveStationToTop(liveStation) },
            onMoveToBottom = { mainViewModel.moveStationToBottom(liveStation) }
        )
    }
    val stationToDelete = state.stationToDelete
    if (stationToDelete != null) { AlertDialog(onDismissRequest = mainViewModel::cancelDeleteStation, title = { Text(stringResource(R.string.delete_station_confirm_title)) }, text = { Text(stringResource(R.string.delete_station_confirm_text, stationToDelete.name)) }, confirmButton = { TextButton(onClick = mainViewModel::deleteUserStation, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text(stringResource(R.string.action_delete)) } }, dismissButton = { TextButton(onClick = mainViewModel::cancelDeleteStation) { Text(stringResource(R.string.action_cancel)) } }) }
    if (state.showResetOrderDialog) { AlertDialog(onDismissRequest = mainViewModel::closeResetOrderDialog, title = { Text(stringResource(R.string.reset_order_confirm_title)) }, text = { Text(stringResource(R.string.reset_order_confirm_text)) }, confirmButton = { TextButton(onClick = mainViewModel::resetFavoriteOrder) { Text(stringResource(R.string.action_reset)) } }, dismissButton = { TextButton(onClick = mainViewModel::closeResetOrderDialog) { Text(stringResource(R.string.action_cancel)) } }) }
    val stationToEdit = state.stationToEdit
    if (stationToEdit != null) {
        val initialSubgroup = if (stationToEdit.isUserStation && stationToEdit.category.equals(StationListFilter.CATEGORY_MY, ignoreCase = true)) {
            ""
        } else {
            stationToEdit.category
        }
        StationEditorDialog(
            title = stringResource(R.string.action_edit_station),
            initialName = stationToEdit.name,
            initialUrl = stationToEdit.url,
            initialCountryCode = stationToEdit.countryCode,
            initialSubgroup = initialSubgroup,
            allCountries = state.countries,
            allSubgroups = state.allCategories,
            onDismiss = mainViewModel::closeEditStationDialog,
            onTest = { name, url -> mainViewModel.playTestStation("$name (${context.getString(R.string.action_test)})", url) },
            onSave = { newName, newUrl, newCountry, newCategory -> mainViewModel.updateUserStation(newName, newUrl, newCountry, newCategory) }
        )
    }
    if (state.showSongInfoSheet && songInfo != null) { ModalBottomSheet(onDismissRequest = mainViewModel::closeSongInfo, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface) { SongInfoSheet(artist = state.parsedArtist, title = state.parsedTitle, stationName = state.activeStationName, bitrate = state.bitrate, streamUrl = displayStations.find { it.name == state.activeStationName }?.url ?: "", info = songInfo!!, onDismiss = mainViewModel::closeSongInfo) } }
}

@Composable
fun ContentScreens(
    state: MainUiState,
    allStations: List<RadioStation>,
    filteredStations: List<RadioStation>,
    mainCategories: List<String>,
    subCategories: List<String>,
    viewModel: MainViewModel,
    context: android.content.Context
) {
    val searchViewModel: SearchViewModel = viewModel(factory = SearchViewModelFactory(viewModel.stationRepository))
    val bufferingStatusText = stringResource(R.string.status_buffering)
    val errorStatusText = stringResource(R.string.status_error)
    val playbackState = when {
        state.playerStatus == errorStatusText -> StationPlaybackState.ERROR
        state.playerStatus == bufferingStatusText -> StationPlaybackState.BUFFERING
        state.isPlaying -> StationPlaybackState.PLAYING
        else -> StationPlaybackState.IDLE
    }
    when (state.currentTab) {
        0 -> StationList(
            stations = allStations,
            filteredStations = filteredStations,
            categories = mainCategories,
            subCategories = subCategories,
            selectedCategory = state.selectedCategory,
            selectedSubCategories = state.selectedSubCategories,
            selectedStationId = state.selectedStationId,
            playbackState = playbackState,
            isInitiallyLoading = state.isRefreshing,
            hideCuratedStations = state.hideRemoteStations,
            columnCountPortrait = state.colsPortrait,
            columnCountLandscape = state.colsLandscape,
            showFlags = state.showFlags,
            onCategorySelect = viewModel::onCategorySelected,
            onSubCategoryToggle = viewModel::toggleSubCategory,
            onStationSelect = viewModel::onStationClicked,
            onStationLongClick = viewModel::openStationActionSheet,
            onAddStationClick = { viewModel.onTabSelected(3) },
            onShowCuratedClick = { viewModel.toggleHideRemoteStations(false) }
        )
        1 -> AlarmsScreen(alarms = state.alarms, onAddAlarm = { viewModel.openAlarmDialog(null) }, onToggleAlarm = viewModel::toggleAlarm, onEditAlarm = { alarm -> viewModel.openAlarmDialog(alarm) })
        2 -> HistoryScreen(
            repository = viewModel.stationRepository,
            onPlayStationByName = viewModel::onHistoryStationClicked,
            onPlayRecording = { file ->
                val fileUri = android.net.Uri.fromFile(file).toString()
                if (state.activeStreamUrl == fileUri) {
                    viewModel.onPlayPauseClicked()
                } else {
                    viewModel.onPlayRecordingClicked(file)
                }
            },
            onDeleteRecording = viewModel::onDeleteRecording,
            activeStreamUrl = state.activeStreamUrl,
            isPlaying = state.isPlaying,
            playbackPosition = state.playbackPosition,
            playbackDuration = state.playbackDuration,
            onSeek = viewModel::seekTo,
            onClearHistory = viewModel::clearHistory
        )
        3 -> SearchScreen(
            repository = viewModel.stationRepository,
            allStations = allStations,
            allCategories = state.allCategories,
            activeUrl = state.activeStreamUrl,
            isPlayerPlaying = state.isPlaying,
            onPlayTest = { name, url, isSaved ->
                if (url == state.activeStreamUrl && state.isPlaying) {
                    val i = Intent(context, RadioService::class.java).apply { action = RadioService.ACTION_STOP }
                    context.startService(i)
                } else {
                    val displayName = if (isSaved) name else "$name (${context.getString(R.string.action_test)})"
                    viewModel.playTestStation(displayName, url)
                }
            },
            onSaveStation = { name, url, country, category -> viewModel.saveUserStation(name, url, country, category) },
            viewModel = searchViewModel
        )
        4 -> {
            val exportLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.CreateDocument("application/json")
            ) { uri ->
                if (uri != null) {
                    viewModel.exportDataToUri(uri)
                }
            }
            val importLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocument()
            ) { uri ->
                if (uri != null) {
                    viewModel.importDataFromUri(uri)
                }
            }

            SettingsScreen(
                isRefreshing = state.isRefreshing,
                colsPortrait = state.colsPortrait,
                colsLandscape = state.colsLandscape,
                showFlags = state.showFlags,
                hideRemoteStations = state.hideRemoteStations,
                widgetTransparency = state.widgetTransparency,
                onToggleShowFlags = viewModel::toggleFlags,
                onToggleHideRemoteStations = viewModel::toggleHideRemoteStations,
                onColsPortraitChange = viewModel::setColsPortrait,
                onColsLandscapeChange = viewModel::setColsLandscape,
                onRefresh = viewModel::refreshStations,
                onResetOrder = viewModel::openResetOrderDialog,
                onWidgetTransparencyChange = viewModel::setWidgetTransparency,
                onExportData = {
                    val timestamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date())
                    exportLauncher.launch("Kellraadio_Backup_$timestamp.json")
                },
                onImportData = {
                    importLauncher.launch(arrayOf("application/json", "application/octet-stream", "*/*"))
                },
                updateState = state.updateState,
                onCheckForUpdates = viewModel::checkForUpdates,
                onDownloadUpdate = viewModel::downloadUpdate,
                onInstallUpdate = viewModel::installUpdate,
                onDismissUpdateDialog = viewModel::dismissUpdateDialog,
                canInstallPackages = viewModel.canInstallPackages(),
                onRequestInstallPermission = viewModel::openInstallPermissionSettings
            )
        }
    }
}

