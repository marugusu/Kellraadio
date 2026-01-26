package ee.minu.kellraadio

import android.content.Context
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import ee.minu.kellraadio.ui.*
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val darkColors = darkColorScheme(
                primary = Color(0xFFBB86FC),
                //primaryContainer = Color(0xFF3700B3),
                primaryContainer = Color(0xFF4F378B),
                onPrimaryContainer = Color(0xFFEADDFF),
                onPrimary = Color.Black,
                secondary = Color(0xFF03DAC6),
                tertiary = Color(0xFFFE7879),
                onSecondary = Color(0xFFCB7E1F),
                background = Color.Black,
                surface = Color(0xFF121212),
                surfaceVariant = Color(0xFF252525),
                onSurface = Color(0xFFE0E0E0),
                onSurfaceVariant = Color(0xFFB0B0B0)
            )

            MaterialTheme(colorScheme = darkColors) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    RaadioEkraan()
                }
            }
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

    // --- 1. JÄLGIME OLEKUT (STATE) ---
    val state by mainViewModel.uiState.collectAsState()

    val songInfo by playerViewModel.songInfo.collectAsState()

    LaunchedEffect(state.parsedArtist, state.parsedTitle) {
        playerViewModel.fetchSongInfo(state.parsedArtist, state.parsedTitle)
    }

    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
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

    // --- 2. KATEGOORIATE ARVUTAMINE ---
    val desiredOrder = listOf("Eesti", "ERR", "Duo", "Sky", "Muu Eesti", "Välis")
    val categoriesData = remember(state.stations) {
        val favs = state.stations.filter { it.isFavorite }
        val base = state.stations.map { it.category }.distinct().toMutableList()

        if (favs.isNotEmpty()) {
            if (base.contains("Favorites")) base.remove("Favorites")
            base.add(0, "Favorites")
        }
        val userStations = state.stations.filter { it.isUserStation }
        if (userStations.isNotEmpty() && !base.contains("My")) {
            val insertIndex = if (favs.isNotEmpty()) 1 else 0
            base.add(insertIndex, "My")
        }
        if (base.contains("All")) base.remove("All")
        base.add("All")

        val sorted = base.sortedWith(compareBy<String> {
            when (it) {
                "Favorites" -> -1
                "My" -> 0
                "All" -> Int.MAX_VALUE
                else -> {
                    val index = desiredOrder.indexOf(it)
                    if (index != -1) index + 1 else Int.MAX_VALUE - 1
                }
            }
        }.thenBy { it })
        Pair(favs, sorted)
    }
    val favoriteStations = categoriesData.first
    val finalCategories = categoriesData.second

    val filteredStations = remember(state.selectedCategory, state.stations, favoriteStations) {
        when (state.selectedCategory) {
            "Favorites" -> favoriteStations
            "All" -> state.stations
            else -> state.stations.filter { it.category == state.selectedCategory }
        }
    }

    val nextAlarmInfo = remember(state.alarms) {
        state.alarms.filter { it.isEnabled }.map { alarm ->
            Pair(AlarmUtils.findNextAlarmTime(alarm.hour, alarm.minute, alarm.days), alarm)
        }.minByOrNull { it.first }
    }
    val alarmInfoForUI = nextAlarmInfo?.let { Pair(it.first, it.second.stationName) }
    val alarmDaysForUI = nextAlarmInfo?.second?.days ?: emptySet()


    // --- 3. UI STRUKTUUR ---

    val navRadioTitle = stringResource(R.string.nav_radio)
    val navAlarmsTitle = stringResource(R.string.nav_alarms)
    val navHistoryTitle = stringResource(R.string.nav_history)
    val navAddTitle = stringResource(R.string.nav_add_channel)
    val navSettingsTitle = stringResource(R.string.nav_settings)

    if (isLandscape) {
        val isLargeScreenHeight = config.screenHeightDp >= AppConfig.UI.Layout.HEIGHT_THRESHOLD_LARGE_LANDSCAPE_DP

        Row(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            NavigationRail(containerColor = Color.Black, contentColor = Color.White) {
                // 1. Defineerime värvid (täpselt nagu portraitis)
                val railItemColors = NavigationRailItemDefaults.colors(
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedTextColor = Color.White,
                    unselectedIconColor = Color.Gray,
                    unselectedTextColor = Color.Gray
                )

                Spacer(modifier = Modifier.weight(1f))

                // 2. Rakendame värvid (colors = railItemColors)
                NavigationRailItem(
                    selected = state.currentTab == 0,
                    onClick = { mainViewModel.onTabSelected(0) },
                    icon = { Icon(Icons.Default.Radio, null) },
                    label = { Text(navRadioTitle) },
                    colors = railItemColors
                )
                NavigationRailItem(
                    selected = state.currentTab == 1,
                    onClick = { mainViewModel.onTabSelected(1) },
                    icon = { Icon(Icons.Default.Alarm, null) },
                    label = { Text(navAlarmsTitle) },
                    colors = railItemColors
                )
                NavigationRailItem(
                    selected = state.currentTab == 2,
                    onClick = { mainViewModel.onTabSelected(2) },
                    icon = { Icon(Icons.Default.History, null) },
                    label = { Text(navHistoryTitle) },
                    colors = railItemColors
                )
                NavigationRailItem(
                    selected = state.currentTab == 3,
                    onClick = { mainViewModel.onTabSelected(3) },
                    icon = { Icon(Icons.Default.AddCircleOutline, null) },
                    label = { Text(navAddTitle) },
                    colors = railItemColors
                )
                NavigationRailItem(
                    selected = state.currentTab == 4,
                    onClick = { mainViewModel.onTabSelected(4) },
                    icon = { Icon(Icons.Default.Settings, null) },
                    label = { Text(navSettingsTitle) },
                    colors = railItemColors
                )

                Spacer(modifier = Modifier.weight(1f))
            }
            VerticalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.surfaceVariant)

            Column(modifier = Modifier.weight(playerWeight).fillMaxHeight().padding(start = 16.dp, top = 16.dp, bottom = 16.dp, end = 8.dp)) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    PlayerControls(
                        selectedStation = state.stations.find { it.id == state.selectedStationId },
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
                        isFavorite = state.stations.find { it.id == state.selectedStationId }?.isFavorite ?: false,
                        songInfo = songInfo,
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
                            // Võtame praegu valitud jaama ja saadame selle funktsiooni
                            val currentStation = state.stations.find { it.id == state.selectedStationId }
                            if (currentStation != null) {
                                mainViewModel.onToggleFavorite(currentStation)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (songInfo != null) {
                    if (isLargeScreenHeight) {
                        // TAHVEL/TV: Näita suurt infot kohe siin all
                        // Teeme tausta natuke ilusaks (gradient), nagu Teaseril
                        val stationColor = ee.minu.kellraadio.ui.StationArtworkUtils.getStationColor(state.activeStationName)

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(top = 16.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(stationColor.copy(alpha = 0.15f), Color.Black)
                                    )
                                )
                        ) {
                            SongInfoContent(
                                artist = state.parsedArtist,
                                title = state.parsedTitle,
                                stationName = state.activeStationName,
                                info = songInfo!!
                            )
                        }
                    } else {
                        // TELEFON LANDSCAPE: Näita vana head Teaserit (nuppu)
                        AnimatedVisibility(
                            visible = !state.showSongInfoSheet,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            SongInfoTeaser(
                                info = songInfo!!,
                                artist = state.parsedArtist,
                                title = state.parsedTitle,
                                stationName = state.activeStationName,
                                onClick = mainViewModel::openSongInfo,
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                                backgroundBrush = Brush.verticalGradient(colors = listOf(Color(0xFF252525), Color.Black))
                            )
                        }
                    }
                }
            }

            Box(modifier = Modifier.weight(1f - playerWeight).fillMaxHeight()) {
                ContentScreens(
                    state = state,
                    filteredStations = filteredStations,
                    categories = finalCategories,
                    viewModel = mainViewModel,
                    context = context
                )
            }
        }
    } else {
        Scaffold(
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
            bottomBar = {
                Column(modifier = Modifier.background(Color.Black)) {
                    AnimatedVisibility(
                        visible = state.currentTab == 0 && songInfo != null && !state.showSongInfoSheet,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        songInfo?.let { info ->
                            SongInfoTeaser(
                                info = info,
                                artist = state.parsedArtist,
                                title = state.parsedTitle,
                                stationName = state.activeStationName,
                                onClick = mainViewModel::openSongInfo
                            )
                        }
                    }

                    if (state.currentTab == 0 && songInfo != null && !state.showSongInfoSheet) {
                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(brush = Brush.horizontalGradient(colors = listOf(Color.Transparent, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), Color.Transparent))))
                    } else {
                        HorizontalDivider(thickness = 1.dp, color = Color(0xFF222222))
                    }

                    NavigationBar(containerColor = Color.Black, contentColor = Color.White, tonalElevation = 0.dp) {
                        // 1. Defineerime värvid, et kasutada teema primaryContainerit (Lilla)
                        val navItemColors = NavigationBarItemDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedTextColor = Color.White,
                            unselectedIconColor = Color.Gray,
                            unselectedTextColor = Color.Gray
                        )

                        // 2. Rakendame värvid igale nupule
                        NavigationBarItem(
                            selected = state.currentTab == 0,
                            onClick = { mainViewModel.onTabSelected(0) },
                            icon = { Icon(Icons.Default.Radio, null) },
                            label = { Text(navRadioTitle) },
                            colors = navItemColors // <--- SEE OLI PUUDU
                        )
                        NavigationBarItem(
                            selected = state.currentTab == 1,
                            onClick = { mainViewModel.onTabSelected(1) },
                            icon = { Icon(Icons.Default.Alarm, null) },
                            label = { Text(navAlarmsTitle) },
                            colors = navItemColors
                        )
                        NavigationBarItem(
                            selected = state.currentTab == 2,
                            onClick = { mainViewModel.onTabSelected(2) },
                            icon = { Icon(Icons.Default.History, null) },
                            label = { Text(navHistoryTitle) },
                            colors = navItemColors
                        )
                        NavigationBarItem(
                            selected = state.currentTab == 3,
                            onClick = { mainViewModel.onTabSelected(3) },
                            icon = { Icon(Icons.Default.AddCircleOutline, null) },
                            label = { Text(navAddTitle) },
                            colors = navItemColors
                        )
                        NavigationBarItem(
                            selected = state.currentTab == 4,
                            onClick = { mainViewModel.onTabSelected(4) },
                            icon = { Icon(Icons.Default.Settings, null) },
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
                        selectedStation = state.stations.find { it.id == state.selectedStationId },
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
                        isFavorite = state.stations.find { it.id == state.selectedStationId }?.isFavorite ?: false,
                        songInfo = songInfo,
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
                            // Leiame hetkel aktiivse jaama ja saadame selle lemmikuks märkimiseks
                            val currentStation = state.stations.find { it.id == state.selectedStationId }
                            if (currentStation != null) {
                                mainViewModel.onToggleFavorite(currentStation)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 0.dp)
                    )

                    ContentScreens(
                        state = state,
                        filteredStations = filteredStations,
                        categories = finalCategories,
                        viewModel = mainViewModel,
                        context = context
                    )
                }
            }
        }
    }

    // --- DIALOOGID ---

    if (state.showSleepDialog) {
        SleepTimerDialog(
            initialMillis = state.sleepTimerRemaining,
            onDismiss = mainViewModel::closeSleepTimerDialog
        )
    }

    if (state.showAlarmDialog) {
        // PARANDUS 1: Lokaalne muutuja smart casti jaoks
        val currentAlarmToEdit = state.alarmToEdit

        val stationForDialog = if (currentAlarmToEdit != null) {
            state.stations.find { it.name == currentAlarmToEdit.stationName }
                ?: RadioStation(0, currentAlarmToEdit.stationName, currentAlarmToEdit.stationUrl)
        } else {
            state.stations.find { it.id == state.selectedStationId }
        }

        AlarmDialog(
            selectedStation = stationForDialog,
            initialHour = currentAlarmToEdit?.hour,
            initialMinute = currentAlarmToEdit?.minute,
            initialDays = currentAlarmToEdit?.days ?: emptySet(),
            onDismiss = mainViewModel::closeAlarmDialog,
            // PARANDUS: Kasutame lokaalset muutujat
            onDelete = if (currentAlarmToEdit != null && currentAlarmToEdit.id != 0) {
                { mainViewModel.deleteAlarm(currentAlarmToEdit) }
            } else null,
            onAlarmSaved = mainViewModel::saveAlarm
        )
    }

    if (state.showActionSheetForStation != null) {
        val liveStation = state.stations.find { it.id == state.showActionSheetForStation!!.id } ?: state.showActionSheetForStation!!
        StationActionSheet(
            station = liveStation,
            onDismiss = mainViewModel::closeStationActionSheet,
            onToggleFavorite = {
                mainViewModel.onToggleFavorite(liveStation)
                mainViewModel.closeStationActionSheet()
            },
            onSetAlarm = {
                mainViewModel.openAlarmDialog(Alarm(hour = 7, minute = 0, days = emptySet(), stationName = liveStation.name, stationUrl = liveStation.url))
            },
            onEdit = {
                mainViewModel.closeStationActionSheet()
            },
            onDelete = { mainViewModel.confirmDeleteStation(liveStation) }
        )
    }

    // PARANDUS 1: Lokaalne muutuja
    val stationToDelete = state.stationToDelete
    if (stationToDelete != null) {
        AlertDialog(
            onDismissRequest = mainViewModel::cancelDeleteStation,
            title = { Text(stringResource(R.string.delete_station_confirm_title)) },
            text = { Text(stringResource(R.string.delete_station_confirm_text, stationToDelete.name)) },
            confirmButton = {
                TextButton(
                    onClick = mainViewModel::deleteUserStation,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = mainViewModel::cancelDeleteStation) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }

    if (state.showSongInfoSheet && songInfo != null) {
        ModalBottomSheet(
            onDismissRequest = mainViewModel::closeSongInfo,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            SongInfoSheet(
                artist = state.parsedArtist,
                title = state.parsedTitle,
                stationName = state.activeStationName,
                info = songInfo!!,
                onDismiss = mainViewModel::closeSongInfo
            )
        }
    }
}

@Composable
fun ContentScreens(
    state: MainUiState,
    filteredStations: List<RadioStation>,
    categories: List<String>,
    viewModel: MainViewModel,
    context: Context
) {
    val searchViewModel: SearchViewModel = viewModel(factory = SearchViewModelFactory(viewModel.stationRepository))

    // Lokaalne olek on OK, sest see on vaid ajutine UI dialoog
    var stationToUpdate by remember { mutableStateOf<RadioStation?>(null) }

    // PARANDUS 2: Coroutine Scope siia
    val scope = rememberCoroutineScope()


    when (state.currentTab) {
        0 -> StationList(
            stations = state.stations,
            filteredStations = filteredStations,
            categories = categories,
            selectedCategory = state.selectedCategory,
            selectedStationId = state.selectedStationId,
            playerStatus = state.playerStatus,
            isRefreshing = state.isRefreshing,
            columnCountPortrait = state.colsPortrait,
            columnCountLandscape = state.colsLandscape,
            showFlags = state.showFlags,
            onCategorySelect = viewModel::onCategorySelected,
            onRefresh = viewModel::refreshStations,
            onStationSelect = viewModel::onStationClicked,
            onStationLongClick = viewModel::openStationActionSheet
        )

        1 -> AlarmsScreen(
            alarms = state.alarms,
            onAddAlarm = { viewModel.openAlarmDialog(null) },
            onToggleAlarm = viewModel::toggleAlarm,
            onEditAlarm = { alarm -> viewModel.openAlarmDialog(alarm) }
        )

        2 -> HistoryScreen(
            repository = viewModel.stationRepository,
            onPlayStationByName = viewModel::onHistoryStationClicked
        )

        3 -> SearchScreen(
            repository = viewModel.stationRepository,
            allStations = state.stations,
            activeUrl = state.activeStreamUrl,
            onPlayTest = { name, url, isSaved ->
                if (url == state.activeStreamUrl && state.isPlaying) {
                    val i = Intent(context, RadioService::class.java).apply { action = RadioService.ACTION_STOP }
                    context.startService(i)
                } else {
                    val displayName = if (isSaved) name else "$name (${context.getString(R.string.action_test)})"
                    viewModel.playTestStation(displayName, url)
                }
            },
            onSaveStation = { name, url, country ->
                viewModel.saveUserStation(name, url, country)
            },
            viewModel = searchViewModel
        )

        4 -> SettingsScreen(
            isRefreshing = state.isRefreshing,
            colsPortrait = state.colsPortrait,
            colsLandscape = state.colsLandscape,
            showFlags = state.showFlags,
            onToggleShowFlags = viewModel::toggleFlags,
            onColsPortraitChange = viewModel::setColsPortrait,
            onColsLandscapeChange = viewModel::setColsLandscape,
            onRefresh = viewModel::refreshStations,
            onClearHistory = viewModel::clearHistory,
            onAddTestData = viewModel::addTestData
        )
    }

    if (stationToUpdate != null) {
        EditStationDialog(
            stationName = stationToUpdate!!.name,
            stationUrl = stationToUpdate!!.url,
            onDismiss = { stationToUpdate = null },
            onTest = { name, url ->
                val i = Intent(context, RadioService::class.java).apply {
                    putExtra("STREAM_URL", url)
                    putExtra("STATION_NAME", "$name (${context.getString(R.string.action_test)})")
                    putExtra("TRIGGERED_BY", "USER")
                }
                context.startForegroundService(i)
            },
            onSave = { newName, newUrl ->
                // PARANDUS 2: Kasutame scope.launch ja lokaalset muutujat
                val station = stationToUpdate
                if (station != null) {
                    scope.launch {
                        viewModel.stationRepository.updateUserStation(station, newName, newUrl)
                        stationToUpdate = null
                    }
                }
            }
        )
    }
}