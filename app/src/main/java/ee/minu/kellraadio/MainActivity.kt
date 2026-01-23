package ee.minu.kellraadio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow

import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import ee.minu.kellraadio.ui.AlarmDialog
import ee.minu.kellraadio.ui.AlarmsScreen
import ee.minu.kellraadio.ui.HistoryScreen
import ee.minu.kellraadio.ui.PlayerControls
import ee.minu.kellraadio.ui.SearchScreen
import ee.minu.kellraadio.ui.SettingsScreen
import ee.minu.kellraadio.ui.SleepTimerDialog
import ee.minu.kellraadio.ui.StationActionSheet
import ee.minu.kellraadio.ui.EditStationDialog
import ee.minu.kellraadio.ui.StationList
import androidx.lifecycle.viewmodel.compose.viewModel
import ee.minu.kellraadio.ui.SearchViewModel
import ee.minu.kellraadio.ui.SearchViewModelFactory
import ee.minu.kellraadio.MusicInfoRepository
import ee.minu.kellraadio.SongAdditionalInfo
import ee.minu.kellraadio.ui.SongInfoSheet
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import ee.minu.kellraadio.ui.SongInfoTeaser
import ee.minu.kellraadio.ui.PlayerViewModel

import androidx.compose.ui.Alignment
import androidx.compose.material.icons.filled.*

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val darkColors = darkColorScheme(
                primary = Color(0xFFBB86FC),
                onPrimary = Color.Black,
                secondary = Color(0xFF03DAC6),
                tertiary = Color(0xFFFE7879),
                onSecondary = Color(0xFFCB7E1F),

                // 1. TAUST: Jääb täiesti mustaks (nagu soovisid)
                background = Color.Black,

                // 2. PINNAD (Menüüd jms): Väga tume hall (peaaegu must)
                surface = Color(0xFF121212),

                // 3. KAARDID (Kanalid, Player): Tumehall
                // See oli enne liiga tume (#121212), tõstame heledamaks (#252525),
                // et ruudud oleksid mustal taustal selgelt näha.
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
fun RaadioEkraan() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val prefs = remember { context.getSharedPreferences("RaadioPrefs", Context.MODE_PRIVATE) }
    val database = remember { AppDatabase.getDatabase(context) }

    val config = LocalConfiguration.current
    val isLandscape = config.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val screenWidth = config.screenWidthDp
    val playerWeight = if (screenWidth < 1000) 0.5f else 0.4f

    var colsPortrait by rememberSaveable { mutableIntStateOf(prefs.getInt("cols_portrait", 3)) }
    var colsLandscape by rememberSaveable { mutableIntStateOf(prefs.getInt("cols_landscape", 3)) }

    val onColsPortraitChange: (Int) -> Unit = {
        colsPortrait = it
        prefs.edit().putInt("cols_portrait", it).apply()
    }
    val onColsLandscapeChange: (Int) -> Unit = {
        colsLandscape = it
        prefs.edit().putInt("cols_landscape", it).apply()
    }

    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val stationRepository = remember {
        RadioStationRepository(
            StationApiService.create(),
            RadioBrowserApiService.create(),
            database.radioStationDao(),
            database.historyDao()
        )
    }

    val searchViewModel: SearchViewModel =
        viewModel(factory = SearchViewModelFactory(stationRepository))
    val stations by stationRepository.allStations.collectAsState(initial = emptyList())
    val alarmDao = remember { database.alarmDao() }
    val alarms by alarmDao.getAllAlarms().collectAsState(initial = emptyList())

    val nextAlarmInfo: Pair<Long, Alarm>? = remember(alarms) {
        alarms.filter { it.isEnabled }.map { alarm ->
            Pair(AlarmUtils.findNextAlarmTime(alarm.hour, alarm.minute, alarm.days), alarm)
        }.minByOrNull { it.first }
    }
    var stationToUpdate by remember { mutableStateOf<RadioStation?>(null) }

    var selectedStationId by rememberSaveable {
        mutableIntStateOf(
            prefs.getInt(
                "last_selected_id",
                -1
            )
        )
    }
    var selectedStationName by rememberSaveable {
        mutableStateOf(
            prefs.getString(
                "last_selected_name",
                ""
            ) ?: ""
        )
    }

    val selectedStation = stations.find { it.id == selectedStationId }

    LaunchedEffect(selectedStation) {
        if (selectedStation != null) {
            selectedStationName = selectedStation.name
            prefs.edit().putString("last_selected_name", selectedStation.name).apply()
        }
    }

    var playingStationName by rememberSaveable { mutableStateOf("") }
    var playingStationUrl by rememberSaveable { mutableStateOf("") }
    // --- TAGASI LISATUD MUUTUJA ---
    var syncedStationName by rememberSaveable { mutableStateOf("") }
    // ------------------------------
    var showFlags by rememberSaveable { mutableStateOf(prefs.getBoolean("show_flags", true)) }
    var isPlayingState by rememberSaveable { mutableStateOf(false) }

    // Siin on OK kasutada context.getString, sest see on Composable'i initsialiseerimise ajal
    var playerStatus by rememberSaveable { mutableStateOf(context.getString(R.string.status_stopped)) }
    var bitrateInfo by rememberSaveable { mutableStateOf("") }
    var parsedTitle by rememberSaveable { mutableStateOf("") }
    var parsedArtist by rememberSaveable { mutableStateOf("") }
    var parsedExtra by rememberSaveable { mutableStateOf("") }

    var showSongInfoSheet by remember { mutableStateOf(false) }
    val playerViewModel: PlayerViewModel = viewModel()

// Kuulame infot (StateFlow)
    val songInfo by playerViewModel.songInfo.collectAsState()

// Anname ViewModelile teada, kui laul muutub.
// See on ikka LaunchedEffect, aga nüüd ta ainult annab käsu, mitte ei tee rasket tööd.
    LaunchedEffect(parsedArtist, parsedTitle) {
        playerViewModel.fetchSongInfo(parsedArtist, parsedTitle)
    }
    var isRefreshing by rememberSaveable { mutableStateOf(false) }
    var previousPlayingStationName by rememberSaveable { mutableStateOf<String?>(null) }
    var sleepTimerMillis by rememberSaveable { mutableLongStateOf(0L) }
    var showSleepDialog by rememberSaveable { mutableStateOf(false) }
    var showAlarmDialog by rememberSaveable { mutableStateOf(false) }
    var alarmToEdit by remember { mutableStateOf<Alarm?>(null) }
    var hasFetchedStations by rememberSaveable { mutableStateOf(false) }

    var showDeleteConfirmDialog by remember { mutableStateOf<RadioStation?>(null) }
    var stationForActionSheet by remember { mutableStateOf<RadioStation?>(null) }
    var showActionSheet by remember { mutableStateOf(false) }

    var currentTab by rememberSaveable { mutableIntStateOf(0) }

    val desiredOrder = listOf("Eesti", "ERR", "Duo", "Sky", "Muu Eesti", "Välis")
    var selectedCategory by rememberSaveable {
        mutableStateOf(
            prefs.getString(
                "last_category",
                "ERR"
            ) ?: "ERR"
        )
    }

    val categoriesData = remember(stations) {
        val favs = stations.filter { it.isFavorite }
        // Võtame kõik kategooriad, mis serverist või baasist tulevad
        val base = stations.map { it.category }.distinct().toMutableList()

        // Lisame süsteemsed kategooriad (Inglise ID-d)
        if (favs.isNotEmpty()) {
            if (base.contains("Favorites")) base.remove("Favorites") // Igaks juhuks väldi duplikaate
            base.add(0, "Favorites")
        }

        val userStations = stations.filter { it.isUserStation }
        // Kontrollime "My" kategooriat
        if (userStations.isNotEmpty() && !base.contains("My")) {
            val insertIndex = if (favs.isNotEmpty()) 1 else 0
            base.add(insertIndex, "My")
        }

        // "All" läheb lõppu
        if (base.contains("All")) base.remove("All")
        base.add("All")

        // Sorteerimine (Inglise ID-de järgi)
        val sorted = base.sortedWith(compareBy<String> {
            when (it) {
                "Favorites" -> -1
                "My" -> 0
                "All" -> Int.MAX_VALUE
                else -> {
                    // Serveri kategooriad (ERR, Sky jne) hoiame kindlas järjekorras
                    val index = desiredOrder.indexOf(it)
                    if (index != -1) index + 1 else Int.MAX_VALUE - 1
                }
            }
        }.thenBy { it })

        Pair(favs, sorted)
    }

    val favoriteStations = categoriesData.first
    val finalCategories = categoriesData.second

    val filteredStations = remember(selectedCategory, stations, favoriteStations) {
        when (selectedCategory) {
            "Favorites" -> favoriteStations // UUS ID
            "All" -> stations               // UUS ID
            else -> stations.filter { it.category == selectedCategory }
        }
    }

    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val isPlaying = isPlayingState

    LaunchedEffect(isPlaying) {
        val w = (context as? android.app.Activity)?.window
        if (isPlaying) w?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else w?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
    LaunchedEffect(lifecycleState) {
        if (lifecycleState == androidx.lifecycle.Lifecycle.State.RESUMED) LocalBroadcastManager.getInstance(
            context
        ).sendBroadcast(Intent(RadioService.ACTION_GET_STATUS))
    }
    LaunchedEffect(Unit) {
        LocalBroadcastManager.getInstance(context)
            .sendBroadcast(Intent(RadioService.ACTION_GET_STATUS))
        scope.launch {
            if (!hasFetchedStations) {
                stationRepository.refreshStations(); hasFetchedStations = true
            }
        }
    }

    LaunchedEffect(playingStationName, stations) {
        if (playingStationName.isNotEmpty() && playingStationName != previousPlayingStationName) {
            val actualStation = stations.find { it.name == playingStationName }

            if (actualStation != null) {
                if (selectedStationId != actualStation.id) {
                    selectedStationId = actualStation.id
                    selectedStationName = actualStation.name

                    val userWasInFavorites = (selectedCategory == "Favorites")
                    val isStationFavorite = actualStation.isFavorite
                    val shouldChangeCategory =
                        (!userWasInFavorites && selectedCategory != actualStation.category) || (userWasInFavorites && !isStationFavorite)

                    if (shouldChangeCategory) {
                        selectedCategory = actualStation.category
                        prefs.edit().putString("last_category", actualStation.category).apply()
                    }
                    prefs.edit().putInt("last_selected_id", actualStation.id)
                        .putString("last_selected_name", actualStation.name).apply()
                }
            } else {
                selectedStationId = -1
                selectedStationName = playingStationName
            }
            previousPlayingStationName = playingStationName
        }
    }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    RadioService.ACTION_STATION_SELECTED_BY_SERVICE -> {
                        val stationId = intent.getIntExtra("STATION_ID", -1)
                        if (stationId != -1) {
                            selectedStationId = stationId
                            val station = stations.find { it.id == stationId }
                            if (station != null && selectedCategory != "Favorites" && selectedCategory != station.category) {
                                selectedCategory = station.category
                            }
                        }
                    }

                    RadioService.ACTION_STATION_CHANGED -> {
                        playingStationName = intent.getStringExtra("STATION_NAME") ?: ""
                        playerStatus = context.getString(R.string.status_playing)
                        isPlayingState = true
                    }

                    RadioService.ACTION_METADATA_UPDATED -> {
                        playerStatus = context.getString(R.string.status_playing)
                        parsedTitle = intent.getStringExtra("PARSED_TITLE") ?: ""
                        parsedArtist = intent.getStringExtra("PARSED_ARTIST") ?: ""
                        parsedExtra = intent.getStringExtra("PARSED_EXTRA") ?: ""
                        isPlayingState = true
                    }

                    RadioService.ACTION_BITRATE_UPDATED -> bitrateInfo =
                        intent.getStringExtra("BITRATE_INFO") ?: ""

                    RadioService.ACTION_PLAYER_ERROR -> {
                        playerStatus = context.getString(R.string.status_error)
                        bitrateInfo = ""
                        isPlayingState = false
                        Toast.makeText(
                            context,
                            context.getString(R.string.error_station_not_found),
                            Toast.LENGTH_LONG
                        ).show()
                    }

                    RadioService.ACTION_PLAYER_STOPPED -> {
                        playerStatus = context.getString(R.string.status_stopped)
                        bitrateInfo = ""
                        playingStationUrl = ""
                        isPlayingState = false
                    }

                    RadioService.ACTION_TIMER_TICK -> sleepTimerMillis =
                        intent.getLongExtra("REMAINING_MILLIS", 0L)
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(RadioService.ACTION_STATION_SELECTED_BY_SERVICE)
            addAction(RadioService.ACTION_STATION_CHANGED); addAction(RadioService.ACTION_METADATA_UPDATED); addAction(
            RadioService.ACTION_BITRATE_UPDATED
        ); addAction(RadioService.ACTION_PLAYER_ERROR); addAction(RadioService.ACTION_PLAYER_STOPPED); addAction(
            RadioService.ACTION_TIMER_TICK
        ); addAction(RadioService.ACTION_GET_STATUS)
        }
        LocalBroadcastManager.getInstance(context).registerReceiver(receiver, filter)
        onDispose { LocalBroadcastManager.getInstance(context).unregisterReceiver(receiver) }
    }

    fun playRadio(station: RadioStation) {
        val i = Intent(context, RadioService::class.java).apply {
            putExtra(
                "STREAM_URL",
                station.url
            ); putExtra("STATION_NAME", station.name); putExtra(
            "TRIGGERED_BY",
            "USER"
        ); putExtra("CATEGORY_NAME", selectedCategory)
        }
        context.startForegroundService(i)
    }

    val alarmInfoForUI = nextAlarmInfo?.let { Pair(it.first, it.second.stationName) }
    val alarmDaysForUI = nextAlarmInfo?.second?.days ?: emptySet()

    // Menüü tekstid muutujatena (siin on OK kasutada stringResource)
    val navRadioTitle = stringResource(R.string.nav_radio)
    val navAlarmsTitle = stringResource(R.string.nav_alarms)
    val navHistoryTitle = stringResource(R.string.nav_history)
    val navAddTitle = stringResource(R.string.nav_add_channel)
    val navSettingsTitle = stringResource(R.string.nav_settings)

    if (isLandscape) {
        Row(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            NavigationRail(
                containerColor = Color.Black, // MUUDATUS: Must taust
                contentColor = Color.White
            ) {
                Spacer(modifier = Modifier.weight(1f))

                NavigationRailItem(
                    selected = currentTab == 0,
                    onClick = { currentTab = 0 },
                    icon = { Icon(Icons.Default.Radio, null) },
                    label = { Text(navRadioTitle) },
                    colors = NavigationRailItemDefaults.colors(
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        unselectedIconColor = Color.Gray,
                        unselectedTextColor = Color.Gray,
                        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedTextColor = Color.White
                    )
                )
                NavigationRailItem(
                    selected = currentTab == 1,
                    onClick = { currentTab = 1 },
                    icon = { Icon(Icons.Default.Alarm, null) },
                    label = { Text(navAlarmsTitle) },
                    colors = NavigationRailItemDefaults.colors(
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        unselectedIconColor = Color.Gray,
                        unselectedTextColor = Color.Gray,
                        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedTextColor = Color.White
                    )
                )
                NavigationRailItem(
                    selected = currentTab == 2,
                    onClick = { currentTab = 2 },
                    icon = { Icon(Icons.Default.History, null) },
                    label = { Text(navHistoryTitle) },
                    colors = NavigationRailItemDefaults.colors(
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        unselectedIconColor = Color.Gray,
                        unselectedTextColor = Color.Gray,
                        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedTextColor = Color.White
                    )
                )
                NavigationRailItem(
                    selected = currentTab == 3,
                    onClick = { currentTab = 3 },
                    icon = { Icon(Icons.Default.AddCircleOutline, null) },
                    label = { Text(navAddTitle) },
                    colors = NavigationRailItemDefaults.colors(
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        unselectedIconColor = Color.Gray,
                        unselectedTextColor = Color.Gray,
                        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedTextColor = Color.White
                    )
                )
                NavigationRailItem(
                    selected = currentTab == 4,
                    onClick = { currentTab = 4 },
                    icon = { Icon(Icons.Default.Settings, null) },
                    label = { Text(navSettingsTitle) },
                    colors = NavigationRailItemDefaults.colors(
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        unselectedIconColor = Color.Gray,
                        unselectedTextColor = Color.Gray,
                        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedTextColor = Color.White
                    )
                )

                Spacer(modifier = Modifier.weight(1f))
            }
            VerticalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.surfaceVariant)

            // --- VASAK POOL (PLAYER + INFO RIBA) ---
            Column(
                modifier = Modifier
                    .weight(playerWeight)
                    .fillMaxHeight()
                    // Siin on välimised ääred
                    .padding(start = 16.dp, top = 16.dp, bottom = 16.dp, end = 8.dp)

            ) {
                // 1. PLAYER (Monoliit + Nupud)
                // Paneme sellele weight(1f), et ta lükkaks info riba alla,
                // VÕI kui tahad, et nad oleks tihedalt koos, võta weight ära.
                // Hetkel eemaldasin weighti, et nad oleksid üksteise all kindla vahega.
                Box(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    PlayerControls(
                        selectedStation = selectedStation,
                        activeStationName = selectedStationName,
                        isPlaying = isPlaying,
                        parsedTitle = parsedTitle,
                        parsedArtist = parsedArtist,
                        parsedExtra = parsedExtra,
                        playerStatus = playerStatus,
                        bitrateInfo = bitrateInfo,
                        alarmInfo = alarmInfoForUI,
                        alarmDays = alarmDaysForUI,
                        sleepTimerMillis = sleepTimerMillis,
                        isFavorite = selectedStation?.isFavorite ?: false,
                        songInfo = songInfo,
                        onInfoClick = { showSongInfoSheet = true },
                        onPlayPause = { val i = Intent(context, RadioService::class.java).apply { action = RadioService.ACTION_PAUSE }; context.startService(i) },
                        onPlayStation = { station -> selectedStationId = station.id; selectedStationName = station.name; playRadio(station) },
                        onSleepClick = { showSleepDialog = true },
                        onAlarmClick = {
                            if (currentTab == 3) { Toast.makeText(context, context.getString(R.string.error_station_not_found), Toast.LENGTH_SHORT).show() }
                            else if (alarms.isNotEmpty()) { currentTab = 1 }
                            else { if (selectedStationId != -1) { alarmToEdit = null; showAlarmDialog = true } else { Toast.makeText(context, context.getString(R.string.select_station), Toast.LENGTH_SHORT).show() } }
                        },
                        onAlarmLongClick = { nextAlarmInfo?.second?.let { AlarmUtils.deleteAlarm(context, it) } },
                        onToggleFavorite = { selectedStation?.let { scope.launch { stationRepository.toggleFavorite(it) } } },

                        // Eemalda vertikaalne kerimine siit, kui see pole vajalik,
                        // et layout ei läheks katki
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // 2. INFO RIBA (Eraldi plokk allpool)
                androidx.compose.animation.AnimatedVisibility(
                    visible = songInfo != null && !showSongInfoSheet,
                    enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(),
                    exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut()
                ) {
                    songInfo?.let { info ->
                        // EEMALDASIME SIIT Spacer-i ja panime vahe otse komponendi külge

                        SongInfoTeaser(
                            info = info,
                            artist = parsedArtist,
                            title = parsedTitle,
                            stationName = selectedStationName,
                            onClick = { showSongInfoSheet = true },
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp),

                            // --- UUS: GRADIENT ---
                            // Kasutame sama loogikat mis playeris: Tumehallist Mustani
                            backgroundBrush = androidx.compose.ui.graphics.Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFF252525), // Üleval heledam
                                    Color.Black        // All must
                                )
                            )
                            // ---------------------
                        )
                    }
                }

                // Kui on vaja, et sisu oleks vertikaalselt keskel, võid siia lõppu lisada:
                // Spacer(modifier = Modifier.weight(1f))
            }
            // --- VASAK POOL LÕPP ---
            Box(modifier = Modifier.weight(1f - playerWeight).fillMaxHeight()) {
                when (currentTab) {
                    0 -> StationList(
                        stations,
                        filteredStations,
                        finalCategories,
                        selectedCategory,
                        selectedStationId,
                        playerStatus,
                        isRefreshing,
                        columnCountPortrait = colsPortrait,
                        showFlags = showFlags,
                        columnCountLandscape = colsLandscape,
                        onCategorySelect = { cat ->
                            selectedCategory = cat; prefs.edit().putString("last_category", cat)
                            .apply()
                        },
                        onRefresh = {
                            scope.launch {
                                isRefreshing = true; try {
                                stationRepository.refreshStations(); Toast.makeText(
                                    context,
                                    context.getString(R.string.toast_updated),
                                    Toast.LENGTH_SHORT
                                ).show()
                            } catch (e: Exception) {
                            } finally {
                                isRefreshing = false
                            }
                            }
                        },
                        onStationSelect = { station ->
                            selectedStationId = station.id
                            selectedStationName = station.name
                            prefs.edit().putInt("last_selected_id", station.id)
                                .putString("last_selected_name", station.name).apply()
                            playRadio(station)
                        },
                        onStationLongClick = { station ->
                            stationForActionSheet = station
                            showActionSheet = true
                        }
                    )

                    1 -> AlarmsScreen(
                        alarms,
                        onAddAlarm = { alarmToEdit = null; showAlarmDialog = true },
                        onToggleAlarm = { alarm ->
                            AlarmUtils.saveOrUpdateAlarm(
                                context,
                                alarm.copy(isEnabled = !alarm.isEnabled),
                                showToast = false
                            )
                        },
                        onEditAlarm = { alarm -> alarmToEdit = alarm; showAlarmDialog = true })

                    2 -> HistoryScreen(
                        repository = stationRepository,
                        // onClearHistory on siit EEMALDATUD
                        onPlayStationByName = { stationName ->
                            val stationToPlay = stations.find { it.name == stationName }
                            if (stationToPlay != null) {
                                selectedStationId = stationToPlay.id
                                selectedStationName = stationToPlay.name
                                syncedStationName = stationToPlay.name
                                prefs.edit().putInt("last_selected_id", stationToPlay.id).apply()
                                if (selectedCategory != "Favorites" && selectedCategory != stationToPlay.category) {
                                    selectedCategory = stationToPlay.category
                                    prefs.edit().putString("last_category", stationToPlay.category)
                                        .apply()
                                }
                                playRadio(stationToPlay)
                                currentTab = 0
                            } else {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.error_station_not_found),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    )

                    3 -> SearchScreen(
                        repository = stationRepository,
                        allStations = stations,
                        activeUrl = playingStationUrl,
                        onPlayTest = { name, url, isSaved ->
                            if (url == playingStationUrl && isPlaying) {
                                val i = Intent(context, RadioService::class.java).apply {
                                    action = RadioService.ACTION_STOP
                                }
                                context.startService(i)
                                playingStationUrl = ""
                            } else {
                                playingStationUrl = url
                                val displayName =
                                    if (isSaved) name else "$name (${context.getString(R.string.action_test)})"
                                val i = Intent(context, RadioService::class.java).apply {
                                    putExtra("STREAM_URL", url)
                                    putExtra("STATION_NAME", displayName)
                                    putExtra("TRIGGERED_BY", "USER")
                                }
                                context.startForegroundService(i)
                            }
                        },
                        onStationAdded = {
                            // KASUTAME UUT ID-d "My"
                            selectedCategory = "My"
                            prefs.edit().putString("last_category", "My").apply()
                        },
                        viewModel = searchViewModel
                    )

                    4 -> SettingsScreen(
                        isRefreshing = isRefreshing,
                        colsPortrait = colsPortrait,
                        colsLandscape = colsLandscape,
                        showFlags = showFlags,
                        onToggleShowFlags = {
                            showFlags = it
                            prefs.edit().putBoolean("show_flags", it).apply()
                        },
                        onColsPortraitChange = onColsPortraitChange,
                        onColsLandscapeChange = onColsLandscapeChange,
                        onRefresh = {
                            scope.launch {
                                isRefreshing = true
                                try {
                                    stationRepository.refreshStations()
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.toast_updated),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } catch (e: Exception) {
                                } finally {
                                    isRefreshing = false
                                }
                            }
                        },
                        // UUS RIDA: Ühendame kustutamise funktsiooni
                        onClearHistory = { scope.launch { stationRepository.clearHistory() } },
                        onAddTestData = {
                            scope.launch {
                                stationRepository.insertTestHistory(); Toast.makeText(
                                context,
                                context.getString(R.string.toast_updated),
                                Toast.LENGTH_SHORT
                            ).show()
                            }
                        }
                    )
                }
            }
        }
    } else {
        // ... (Scaffoldi algus) ...
        Scaffold(
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
            bottomBar = {
                // Kasutame Columni, et laduda asjad üksteise peale
                Column(
                    modifier = Modifier.background(Color.Black) // Veendume, et taust on must
                ) {
                    // 1. TEASER RIBA (Animeeritud ilmumine)
                    // Näita ainult Raadio vaates (0) ja kui info on olemas
                    androidx.compose.animation.AnimatedVisibility(
                        visible = currentTab == 0 && songInfo != null && !showSongInfoSheet,
                        enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(),
                        exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut()
                    ) {
                        songInfo?.let { info ->
                            SongInfoTeaser(
                                info = info,
                                artist = parsedArtist,
                                title = parsedTitle,
                                stationName = selectedStationName,
                                onClick = { showSongInfoSheet = true }
                            )
                        }
                    }

                    // 2. EFFEKTNE VAHEJOON
                    // See joon on nähtav alati, või ainult siis kui teaser on?
                    // Teeme nii, et ta on alati, eraldab sisu menüüst.
                    // Gradient joon: läbipaistev -> primaarvärv -> läbipaistev
                    if (currentTab == 0 && songInfo != null && !showSongInfoSheet) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(
                                    brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), // Hõõguv joon
                                            Color.Transparent
                                        )
                                    )
                                )
                        )
                    } else {
                        // Tavaline diskreetne eraldaja, kui teaserit pole
                        HorizontalDivider(
                            thickness = 1.dp,
                            color = Color(0xFF222222) // Väga tume hall
                        )
                    }

                    // 3. NAVIGATSIOONIRIBA (Täiesti must)
                    NavigationBar(
                        containerColor = Color.Black, // SUNNIME MUSTAKS
                        contentColor = Color.White,
                        tonalElevation = 0.dp // Eemaldame vaikimisi heleda kihi
                    ) {
                        // ... Nupud jäävad samaks, kopeeri siia oma vana NavigationBarItem kood ...
                        // (Näide ühest nupust:)
                        NavigationBarItem(
                            selected = currentTab == 0,
                            onClick = { currentTab = 0 },
                            icon = { Icon(Icons.Default.Radio, null) },
                            label = { Text(navRadioTitle) },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                unselectedIconColor = Color.Gray,
                                unselectedTextColor = Color.Gray,
                                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                selectedTextColor = Color.White
                            )
                        )
                        // ... Korda teiste nuppude jaoks ...
                        NavigationBarItem(selected = currentTab == 1, onClick = { currentTab = 1 }, icon = { Icon(Icons.Default.Alarm, null) }, label = { Text(navAlarmsTitle) }, colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primaryContainer, unselectedIconColor = Color.Gray, unselectedTextColor = Color.Gray, selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer, selectedTextColor = Color.White))
                        NavigationBarItem(selected = currentTab == 2, onClick = { currentTab = 2 }, icon = { Icon(Icons.Default.History, null) }, label = { Text(navHistoryTitle) }, colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primaryContainer, unselectedIconColor = Color.Gray, unselectedTextColor = Color.Gray, selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer, selectedTextColor = Color.White))
                        NavigationBarItem(selected = currentTab == 3, onClick = { currentTab = 3 }, icon = { Icon(Icons.Default.AddCircleOutline, null) }, label = { Text(navAddTitle) }, colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primaryContainer, unselectedIconColor = Color.Gray, unselectedTextColor = Color.Gray, selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer, selectedTextColor = Color.White))
                        NavigationBarItem(selected = currentTab == 4, onClick = { currentTab = 4 }, icon = { Icon(Icons.Default.Settings, null) }, label = { Text(navSettingsTitle) }, colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primaryContainer, unselectedIconColor = Color.Gray, unselectedTextColor = Color.Gray, selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer, selectedTextColor = Color.White))
                    }
                }
            },
            // --- UUS: DÜNAAMILINE FAB ---
//            floatingActionButton = {
//                if (currentTab == 0) {
//                    androidx.compose.animation.AnimatedVisibility(
//                        visible = songInfo != null && !showSongInfoSheet,
//                        // Muudame animatsiooni ka lihtsamaks (lihtsalt suureneb sisse)
//                        enter = androidx.compose.animation.scaleIn(),
//                        exit = androidx.compose.animation.scaleOut()
//                    ) {
//                        songInfo?.let { info ->
//                            // Kasutame tavalist ümmargust FloatingActionButton-it
//                            FloatingActionButton(
//                                onClick = { showSongInfoSheet = true },
//                                // Kasutame 'tertiary' värvi (tavaliselt soe toon), et eristuda
//                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
//                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
//                                shape = CircleShape // See on vaikimisi, aga võid lisada, kui tahad kindel olla
//                            ) {
////                                if (!info.lyrics.isNullOrEmpty()) {
////                                    // Kui on sõnad -> Kasuta sinu uut faili
////                                    Icon(
////                                        painter = androidx.compose.ui.res.painterResource(R.drawable.ic_lyrics),
////                                        contentDescription = null
////                                    )
////                                } else {
////                                    // Kui on ainult pilt -> Kasuta süsteemset pildi ikooni
////                                    Icon(
////                                        imageVector = Icons.Default.Image,
////                                        contentDescription = null
////                                    )
////                                }
//
//                                // Ikooni loogika
//                                val icon = if (!info.lyrics.isNullOrEmpty()) Icons.Default.MusicNote else Icons.Default.Image
//                               Icon(icon, contentDescription = stringResource(R.string.info_available))
//                            }
//                        }
//                    }
//                }
//            }
//            // ---------------------------
        ) { innerPadding ->
            // Sisu (Box ja Column) jääb samaks, aga EEMALDA siit seest see vana "Hõljuv Info-riba" Column
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = innerPadding.calculateBottomPadding())
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // ... (PlayerControls ja tabide loogika jääb samaks) ...
                    PlayerControls(
                        selectedStation = selectedStation,
                        activeStationName = selectedStationName,
                        isPlaying = isPlaying,
                        parsedTitle = parsedTitle,
                        parsedArtist = parsedArtist,
                        parsedExtra = parsedExtra,
                        playerStatus = playerStatus,
                        bitrateInfo = bitrateInfo,
                        alarmInfo = alarmInfoForUI,
                        alarmDays = alarmDaysForUI,
                        sleepTimerMillis = sleepTimerMillis,
                        isFavorite = selectedStation?.isFavorite ?: false,
                        songInfo = songInfo,
                        onInfoClick = { showSongInfoSheet = true },
                        onPlayPause = { val i = Intent(context, RadioService::class.java).apply { action = RadioService.ACTION_PAUSE }; context.startService(i) },
                        onPlayStation = { station -> selectedStationId = station.id; selectedStationName = station.name; playRadio(station) },
                        onSleepClick = { showSleepDialog = true },
                        onAlarmClick = {
                            // ... (Sama loogika) ...
                            if (currentTab == 3) { Toast.makeText(context, context.getString(R.string.error_station_not_found), Toast.LENGTH_SHORT).show() }
                            else if (alarms.isNotEmpty()) { currentTab = 1 }
                            else { if (selectedStationId != -1) { alarmToEdit = null; showAlarmDialog = true } else { Toast.makeText(context, context.getString(R.string.select_station), Toast.LENGTH_SHORT).show() } }
                        },
                        onAlarmLongClick = { nextAlarmInfo?.second?.let { AlarmUtils.deleteAlarm(context, it) } },
                        onToggleFavorite = { selectedStation?.let { scope.launch { stationRepository.toggleFavorite(it) } } },
                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 0.dp)
                    )

                    when (currentTab) {
                        0 -> StationList(
                            stations, filteredStations, finalCategories, selectedCategory, selectedStationId, playerStatus, isRefreshing, columnCountPortrait = colsPortrait, showFlags = showFlags, columnCountLandscape = colsLandscape,
                            onCategorySelect = { cat -> selectedCategory = cat; prefs.edit().putString("last_category", cat).apply() },
                            onRefresh = { scope.launch { isRefreshing = true; try { stationRepository.refreshStations(); Toast.makeText(context, context.getString(R.string.toast_updated), Toast.LENGTH_SHORT).show() } catch (e: Exception) { } finally { isRefreshing = false } } },
                            onStationSelect = { station -> selectedStationId = station.id; selectedStationName = station.name; prefs.edit().putInt("last_selected_id", station.id).putString("last_selected_name", station.name).apply(); playRadio(station) },
                            onStationLongClick = { station -> stationForActionSheet = station; showActionSheet = true }
                        )
                        1 -> AlarmsScreen(alarms, onAddAlarm = { alarmToEdit = null; showAlarmDialog = true }, onToggleAlarm = { alarm -> AlarmUtils.saveOrUpdateAlarm(context, alarm.copy(isEnabled = !alarm.isEnabled), showToast = false) }, onEditAlarm = { alarm -> alarmToEdit = alarm; showAlarmDialog = true })
                        2 -> HistoryScreen(repository = stationRepository, onPlayStationByName = { stationName -> /* ... */ })
                        3 -> SearchScreen(repository = stationRepository, allStations = stations, activeUrl = playingStationUrl, onPlayTest = { name, url, isSaved -> /* ... */ }, onStationAdded = { selectedCategory = "My"; prefs.edit().putString("last_category", "My").apply() }, viewModel = searchViewModel)
                        4 -> SettingsScreen(isRefreshing = isRefreshing, colsPortrait = colsPortrait, colsLandscape = colsLandscape, showFlags = showFlags, onToggleShowFlags = { showFlags = it; prefs.edit().putBoolean("show_flags", it).apply() }, onColsPortraitChange = onColsPortraitChange, onColsLandscapeChange = onColsLandscapeChange, onRefresh = { /* ... */ }, onClearHistory = { scope.launch { stationRepository.clearHistory() } }, onAddTestData = { scope.launch { stationRepository.insertTestHistory(); Toast.makeText(context, context.getString(R.string.toast_updated), Toast.LENGTH_SHORT).show() } })
                    }
                }
                // SIIT EEMALDASIN SELLE VANA AnimatedVisibility BLOKI
            }
        }

        if (showSleepDialog) {
            SleepTimerDialog(
                initialMillis = sleepTimerMillis,
                onDismiss = { showSleepDialog = false })
        }
        if (showAlarmDialog) {
            val stationForDialog = if (alarmToEdit != null) {
                stations.find { it.name == alarmToEdit!!.stationName }
                    ?: RadioStation(0, alarmToEdit!!.stationName, alarmToEdit!!.stationUrl)
            } else {
                selectedStation
            }

            AlarmDialog(
                selectedStation = stationForDialog,
                initialHour = alarmToEdit?.hour,
                initialMinute = alarmToEdit?.minute,
                initialDays = alarmToEdit?.days ?: emptySet(),
                onDismiss = { showAlarmDialog = false },
                onDelete = if (alarmToEdit != null && alarmToEdit!!.id != 0) {
                    { alarmToEdit?.let { AlarmUtils.deleteAlarm(context, it) } }
                } else null,
                onAlarmSaved = { hour, minute, days ->
                    stationForDialog?.let { station ->
                        val alarm = alarmToEdit?.copy(
                            hour = hour, minute = minute, days = days,
                            stationName = station.name, stationUrl = station.url, isEnabled = true
                        ) ?: Alarm(
                            hour = hour, minute = minute, days = days,
                            stationName = station.name, stationUrl = station.url
                        )
                        AlarmUtils.saveOrUpdateAlarm(context, alarm)
                    }
                }
            )
        }

        if (showActionSheet && stationForActionSheet != null) {
            val liveStation =
                stations.find { it.id == stationForActionSheet!!.id } ?: stationForActionSheet!!
            StationActionSheet(
                station = liveStation,
                onDismiss = { showActionSheet = false },
                onToggleFavorite = {
                    scope.launch { stationRepository.toggleFavorite(liveStation) }
                },
                onSetAlarm = {
                    alarmToEdit = Alarm(
                        hour = 7, minute = 0, days = emptySet(),
                        stationName = liveStation.name,
                        stationUrl = liveStation.url
                    )
                    showAlarmDialog = true
                    currentTab = 1
                },
                onEdit = { stationToUpdate = liveStation },
                onDelete = { showDeleteConfirmDialog = liveStation }
            )
        }

        if (showDeleteConfirmDialog != null) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirmDialog = null },
                title = { Text(stringResource(R.string.delete_station_confirm_title)) },
                text = {
                    Text(
                        stringResource(
                            R.string.delete_station_confirm_text,
                            showDeleteConfirmDialog?.name ?: ""
                        )
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val stationToDelete = showDeleteConfirmDialog
                            if (stationToDelete != null) {
                                scope.launch {
                                    stationRepository.deleteStation(stationToDelete)
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.alarm_toast_deleted),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                            showDeleteConfirmDialog = null
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) { Text(stringResource(R.string.action_delete)) }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showDeleteConfirmDialog = null
                    }) { Text(stringResource(R.string.action_cancel)) }
                }
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
                        putExtra(
                            "STATION_NAME",
                            "$name (${context.getString(R.string.action_test)})"
                        )
                        putExtra("TRIGGERED_BY", "USER")
                    }
                    context.startForegroundService(i)
                },
                onSave = { newName, newUrl ->
                    scope.launch {
                        stationRepository.updateUserStation(stationToUpdate!!, newName, newUrl)
                        Toast.makeText(
                            context,
                            context.getString(R.string.toast_updated),
                            Toast.LENGTH_SHORT
                        ).show()
                        stationToUpdate = null
                    }
                }
            )
        }


    }
    // --- UUS: Laulusõnade ja pildi vaade ---
    if (showSongInfoSheet && songInfo != null) {
        ModalBottomSheet(
            onDismissRequest = { showSongInfoSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            SongInfoSheet(
                artist = parsedArtist,
                title = parsedTitle,
                stationName = selectedStationName,
                info = songInfo!!,
                onDismiss = { showSongInfoSheet = false }
            )
        }
    }
}