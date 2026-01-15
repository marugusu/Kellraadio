package ee.minu.kellraadio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import ee.minu.kellraadio.ui.AlarmDialog
import ee.minu.kellraadio.ui.AlarmsScreen
import ee.minu.kellraadio.ui.HistoryScreen
import ee.minu.kellraadio.ui.PlayerControls
import ee.minu.kellraadio.ui.SettingsScreen
import ee.minu.kellraadio.ui.SleepTimerDialog
import ee.minu.kellraadio.ui.StationList
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val darkColors = darkColorScheme(
                primary = Color(0xFFBB86FC),
                onPrimary = Color.Black,
                secondary = Color(0xFF03DAC6),
                tertiary = Color(0xFFFE7879),
                onSecondary = Color(0xFFCB7E1F),
                background = Color(0xFF121212),
                surface = Color(0xFF1E1E1E),
                onSurface = Color(0xFFE0E0E0),
                surfaceVariant = Color(0xFF2C2C2C),
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

    val config = LocalConfiguration.current
    val isLandscape = config.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val screenWidth = config.screenWidthDp
    val playerWeight = if (screenWidth < 1000) 0.5f else 0.4f

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val prefs = remember { context.getSharedPreferences("RaadioPrefs", Context.MODE_PRIVATE) }
    val database = remember { AppDatabase.getDatabase(context) }
    val stationRepository = remember { RadioStationRepository(StationApiService.create(), database.radioStationDao(), database.historyDao()) }
    val stations by stationRepository.allStations.collectAsState(initial = emptyList())
    val alarmDao = remember { database.alarmDao() }
    val alarms by alarmDao.getAllAlarms().collectAsState(initial = emptyList())

    val nextAlarmInfo: Pair<Long, Alarm>? = remember(alarms) {
        alarms.filter { it.isEnabled }.map { alarm ->
            Pair(AlarmUtils.findNextAlarmTime(alarm.hour, alarm.minute, alarm.days), alarm)
        }.minByOrNull { it.first }
    }

    var selectedStationId by rememberSaveable { mutableIntStateOf(prefs.getInt("last_selected_id", -1)) }
    // Hoiame nime ka mälus, et see säiliks taaskäivitamisel
    var selectedStationName by rememberSaveable { mutableStateOf(prefs.getString("last_selected_name", "") ?: "") }

    val selectedStation = stations.find { it.id == selectedStationId }

    // Efekt, mis sünkroniseerib nime, kui jaamade nimekiri muutub või ID muutub
    LaunchedEffect(selectedStation) {
        if (selectedStation != null) {
            selectedStationName = selectedStation.name
            // Salvestame ka SharedPreferences'i, et see oleks olemas järgmisel käivitusel
            prefs.edit().putString("last_selected_name", selectedStation.name).apply()
        }
    }

    var playingStationName by rememberSaveable { mutableStateOf("") }
    var syncedStationName by rememberSaveable { mutableStateOf("") }
    var playerStatus by rememberSaveable { mutableStateOf("Peatatud") }
    var bitrateInfo by rememberSaveable { mutableStateOf("") }
    var parsedTitle by rememberSaveable { mutableStateOf("") }
    var parsedArtist by rememberSaveable { mutableStateOf("") }
    var parsedExtra by rememberSaveable { mutableStateOf("") }
    var isRefreshing by rememberSaveable { mutableStateOf(false) }
    var sleepTimerMillis by rememberSaveable { mutableLongStateOf(0L) }
    var showSleepDialog by rememberSaveable { mutableStateOf(false) }
    var showAlarmDialog by rememberSaveable { mutableStateOf(false) }
    var alarmToEdit by remember { mutableStateOf<Alarm?>(null) }
    var hasFetchedStations by rememberSaveable { mutableStateOf(false) }

    var currentTab by rememberSaveable { mutableIntStateOf(0) }

    val desiredOrder = listOf("ERR","Duo Media","Sky Media","All Media","Muu Eesti", "Eesti", "Välis")
    var selectedCategory by rememberSaveable { mutableStateOf(prefs.getString("last_category", "ERR") ?: "ERR") }
    val favoriteStations = stations.filter { it.isFavorite }
    val baseCategories = stations.map { it.category }.distinct().toMutableList()
    if (favoriteStations.isNotEmpty()) { baseCategories.add(0, "Lemmikud") }
    baseCategories.add("Kõik kanalid")
    val finalCategories = baseCategories.sortedWith(compareBy<String> {
        if (it == "Lemmikud") -1 else { val index = desiredOrder.indexOf(it); if (index != -1) index else Int.MAX_VALUE }
    }.thenBy { it })
    val filteredStations = remember(selectedCategory, stations, favoriteStations) {
        when (selectedCategory) {
            "Lemmikud" -> favoriteStations
            "Kõik kanalid" -> stations // Näita kõiki jaamu
            else -> stations.filter { it.category == selectedCategory }
        }
    }

    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val isPlaying = playerStatus.contains("Mängib")

    LaunchedEffect(isPlaying) {
        val w = (context as? android.app.Activity)?.window
        if (isPlaying) w?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else w?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
    LaunchedEffect(lifecycleState) {
        if (lifecycleState == androidx.lifecycle.Lifecycle.State.RESUMED) LocalBroadcastManager.getInstance(context).sendBroadcast(Intent(RadioService.ACTION_GET_STATUS))
    }
    LaunchedEffect(Unit) {
        LocalBroadcastManager.getInstance(context).sendBroadcast(Intent(RadioService.ACTION_GET_STATUS))
        scope.launch { if (!hasFetchedStations) { stationRepository.refreshStations(); hasFetchedStations = true } }
    }

    LaunchedEffect(playingStationName, stations) {
        if (playingStationName.isNotEmpty()) {
            val actualStation = stations.find { it.name == playingStationName }
            // Kui leidsime jaama ja see pole hetkel valitud, siis valime selle
            if (actualStation != null && selectedStationId != actualStation.id) {
                selectedStationId = actualStation.id
                selectedStationName = actualStation.name

                val userWasInFavorites = (selectedCategory == "Lemmikud")
                val isStationFavorite = actualStation.isFavorite

                // Vahetame kategooriat kahel juhul:
                // 1. Kasutaja EI OLNUD "Lemmikutes" JA jaama kategooria on teine.
                // 2. Kasutaja OLI "Lemmikutes", aga see jaam POLE lemmik (ehk pole seal nimekirjas).
                val shouldChangeCategory = (!userWasInFavorites && selectedCategory != actualStation.category) ||
                        (userWasInFavorites && !isStationFavorite)

                if (shouldChangeCategory) {
                    selectedCategory = actualStation.category
                    prefs.edit().putString("last_category", actualStation.category).apply()
                }

                // Salvestame uue seisu mällu
                prefs.edit()
                    .putInt("last_selected_id", actualStation.id)
                    .putString("last_selected_name", actualStation.name)
                    .apply()
            }
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

                            // Uuendame ka kategooriat, et UI oleks sünkroonis
                            val station = stations.find { it.id == stationId }
                            if (station != null && selectedCategory != "Lemmikud" && selectedCategory != station.category) {
                                selectedCategory = station.category
                            }
                        }
                    }
                    RadioService.ACTION_STATION_CHANGED -> {
                        playingStationName = intent.getStringExtra("STATION_NAME") ?: ""
                        playerStatus = "Mängib"
                        // --- PARANDUS 2: Eemaldame siit "Otseeeter" üle kirjutamise ---
                        // parsedArtist = playingStationName
                        // parsedTitle = "Otseeeter"
                        // parsedExtra = ""
                    }
                    RadioService.ACTION_METADATA_UPDATED -> { playerStatus = "Mängib"; parsedTitle = intent.getStringExtra("PARSED_TITLE") ?: ""; parsedArtist = intent.getStringExtra("PARSED_ARTIST") ?: ""; parsedExtra = intent.getStringExtra("PARSED_EXTRA") ?: "" }
                    RadioService.ACTION_BITRATE_UPDATED -> bitrateInfo = intent.getStringExtra("BITRATE_INFO") ?: ""
                    RadioService.ACTION_PLAYER_ERROR -> { playerStatus = "Viga ühendusega"; bitrateInfo = "" }
                    RadioService.ACTION_PLAYER_STOPPED -> { playerStatus = "Peatatud"; bitrateInfo = "" }
                    RadioService.ACTION_TIMER_TICK -> sleepTimerMillis = intent.getLongExtra("REMAINING_MILLIS", 0L)
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(RadioService.ACTION_STATION_SELECTED_BY_SERVICE)
            addAction(RadioService.ACTION_STATION_CHANGED); addAction(RadioService.ACTION_METADATA_UPDATED); addAction(RadioService.ACTION_BITRATE_UPDATED); addAction(RadioService.ACTION_PLAYER_ERROR); addAction(RadioService.ACTION_PLAYER_STOPPED); addAction(RadioService.ACTION_TIMER_TICK); addAction(RadioService.ACTION_GET_STATUS) }
        LocalBroadcastManager.getInstance(context).registerReceiver(receiver, filter)
        onDispose { LocalBroadcastManager.getInstance(context).unregisterReceiver(receiver) }
    }

    fun playRadio(station: RadioStation) {
        val i = Intent(context, RadioService::class.java).apply { putExtra("STREAM_URL", station.url); putExtra("STATION_NAME", station.name); putExtra("TRIGGERED_BY", "USER"); putExtra("CATEGORY_NAME", selectedCategory) }
        context.startForegroundService(i)
    }

    val alarmInfoForUI = nextAlarmInfo?.let { Pair(it.first, it.second.stationName) }
    val alarmDaysForUI = nextAlarmInfo?.second?.days ?: emptySet()

    if (isLandscape) {
        Row(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            NavigationRail {
                Spacer(modifier = Modifier.weight(1f))
                NavigationRailItem(selected = currentTab == 0, onClick = { currentTab = 0 }, icon = { Icon(Icons.Default.Radio, null) }, label = { Text("Raadio") })
                NavigationRailItem(selected = currentTab == 1, onClick = { currentTab = 1 }, icon = { Icon(Icons.Default.Alarm, null) }, label = { Text("Äratused") })
                NavigationRailItem(selected = currentTab == 2, onClick = { currentTab = 2 }, icon = { Icon(Icons.Default.History, null) }, label = { Text("Ajalugu") })
                NavigationRailItem(selected = currentTab == 3, onClick = { currentTab = 3 }, icon = { Icon(Icons.Default.Settings, null) }, label = { Text("Seaded") })
                NavigationRailItem(selected = currentTab == 4, onClick = { currentTab = 4 }, icon = { Icon(Icons.Default.Info, null) }, label = { Text("Info") })
                Spacer(modifier = Modifier.weight(1f))
            }
            VerticalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.surfaceVariant)
            Box(modifier = Modifier.weight(playerWeight).padding(start = 16.dp, top = 16.dp, bottom = 16.dp, end = 8.dp)) {
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
                    onPlayPause = { val i = Intent(context, RadioService::class.java).apply { action = RadioService.ACTION_PAUSE }; context.startService(i) },
                    onPlayStation = { station -> selectedStationId = station.id; selectedStationName = station.name; playRadio(station) },
                    onSleepClick = { showSleepDialog = true },
                    onAlarmClick = { if (alarms.isEmpty()) { alarmToEdit = null; showAlarmDialog = true } else { currentTab = 1 } },
                    onAlarmLongClick = { nextAlarmInfo?.second?.let { AlarmUtils.deleteAlarm(context, it) } },
                    onToggleFavorite = { selectedStation?.let { scope.launch { stationRepository.toggleFavorite(it) } } },
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                )
            }
            Box(modifier = Modifier.weight(1f - playerWeight).fillMaxHeight()) {
                when (currentTab) {
                    0 -> StationList(stations, filteredStations, finalCategories, selectedCategory, selectedStationId, playerStatus, isRefreshing, onCategorySelect = { cat -> selectedCategory = cat; prefs.edit().putString("last_category", cat).apply() }, onRefresh = { scope.launch { isRefreshing = true; try { stationRepository.refreshStations(); Toast.makeText(context, "Uuendatud!", Toast.LENGTH_SHORT).show() } catch (e: Exception) { } finally { isRefreshing = false } } },
                        onStationSelect = { station ->
                            selectedStationId = station.id
                            selectedStationName = station.name // <<-- Lisame selle rea
                            prefs.edit()
                                .putInt("last_selected_id", station.id)
                                .putString("last_selected_name", station.name) // <<-- Lisame selle rea
                                .apply()
                            playRadio(station)
                        },
                        onStationLongClick = { station -> scope.launch { stationRepository.toggleFavorite(station) } })
                    1 -> AlarmsScreen(alarms, onAddAlarm = { alarmToEdit = null; showAlarmDialog = true }, onToggleAlarm = { alarm -> AlarmUtils.saveOrUpdateAlarm(context, alarm.copy(isEnabled = !alarm.isEnabled), showToast = false) }, onEditAlarm = { alarm -> alarmToEdit = alarm; showAlarmDialog = true })
                    2 -> HistoryScreen(repository = stationRepository, onClearHistory = { scope.launch { stationRepository.clearHistory() } }, onPlayStationByName = { stationName -> val stationToPlay = stations.find { it.name == stationName }; if (stationToPlay != null) { selectedStationId = stationToPlay.id; selectedStationName = stationToPlay.name; syncedStationName = stationToPlay.name; prefs.edit().putInt("last_selected_id", stationToPlay.id).apply(); if (selectedCategory != "Lemmikud" && selectedCategory != stationToPlay.category) { selectedCategory = stationToPlay.category; prefs.edit().putString("last_category", stationToPlay.category).apply() }; playRadio(stationToPlay); currentTab = 0 } else { Toast.makeText(context, "Jaama '$stationName' ei leitud!", Toast.LENGTH_SHORT).show() } })
                    3 -> SettingsScreen(isRefreshing = isRefreshing, onRefresh = { scope.launch { isRefreshing = true; try { stationRepository.refreshStations(); Toast.makeText(context, "Uuendatud!", Toast.LENGTH_SHORT).show() } catch (e: Exception) { } finally { isRefreshing = false } } }, onAddTestData = { scope.launch { stationRepository.insertTestHistory(); Toast.makeText(context, "Testandmed lisatud!", Toast.LENGTH_SHORT).show() } })
                    4 -> ee.minu.kellraadio.ui.InfoScreen()
                }
            }
        }
    } else {
        Scaffold(modifier = Modifier.fillMaxSize().statusBarsPadding(), bottomBar = {
            NavigationBar {
                NavigationBarItem(selected = currentTab == 0, onClick = { currentTab = 0 }, icon = { Icon(Icons.Default.Radio, contentDescription = null) }, label = { Text("Raadio") })
                NavigationBarItem(selected = currentTab == 1, onClick = { currentTab = 1 }, icon = { Icon(Icons.Default.Alarm, contentDescription = null) }, label = { Text("Äratused") })
                NavigationBarItem(selected = currentTab == 2, onClick = { currentTab = 2 }, icon = { Icon(Icons.Default.History, contentDescription = null) }, label = { Text("Ajalugu") })
                NavigationBarItem(selected = currentTab == 3, onClick = { currentTab = 3 }, icon = { Icon(Icons.Default.Settings, contentDescription = null) }, label = { Text("Seaded") })
                NavigationBarItem(selected = currentTab == 4, onClick = { currentTab = 4 }, icon = { Icon(Icons.Default.Info, contentDescription = null) }, label = { Text("Info") })
            }
        }) { innerPadding ->
            Column(modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding())) {
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
                    onPlayPause = { val i = Intent(context, RadioService::class.java).apply { action = RadioService.ACTION_PAUSE }; context.startService(i) },
                    onPlayStation = { station -> selectedStationId = station.id; selectedStationName = station.name; playRadio(station) },
                    onSleepClick = { showSleepDialog = true },
                    onAlarmClick = { if (alarms.isEmpty()) { alarmToEdit = null; showAlarmDialog = true } else { currentTab = 1 } },
                    onAlarmLongClick = { nextAlarmInfo?.second?.let { AlarmUtils.deleteAlarm(context, it) } },
                    onToggleFavorite = { selectedStation?.let { scope.launch { stationRepository.toggleFavorite(it) } } },
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 0.dp)
                )
                when (currentTab) {
                    0 -> StationList(stations, filteredStations, finalCategories, selectedCategory, selectedStationId, playerStatus, isRefreshing, onCategorySelect = { cat -> selectedCategory = cat; prefs.edit().putString("last_category", cat).apply() }, onRefresh = { scope.launch { isRefreshing = true; try { stationRepository.refreshStations(); Toast.makeText(context, "Uuendatud!", Toast.LENGTH_SHORT).show() } catch (e: Exception) { } finally { isRefreshing = false } } },
                        onStationSelect = { station ->
                            selectedStationId = station.id
                            selectedStationName = station.name // <<-- Lisame selle rea
                            prefs.edit()
                                .putInt("last_selected_id", station.id)
                                .putString("last_selected_name", station.name) // <<-- Lisame selle rea
                                .apply()
                            playRadio(station)
                        },
                        onStationLongClick = { station -> scope.launch { stationRepository.toggleFavorite(station) } })
                    1 -> AlarmsScreen(alarms, onAddAlarm = { alarmToEdit = null; showAlarmDialog = true }, onToggleAlarm = { alarm -> AlarmUtils.saveOrUpdateAlarm(context, alarm.copy(isEnabled = !alarm.isEnabled), showToast = false) }, onEditAlarm = { alarm -> alarmToEdit = alarm; showAlarmDialog = true })
                    2 -> HistoryScreen(repository = stationRepository, onClearHistory = { scope.launch { stationRepository.clearHistory() } }, onPlayStationByName = { stationName -> val stationToPlay = stations.find { it.name == stationName }; if (stationToPlay != null) { selectedStationId = stationToPlay.id; selectedStationName = stationToPlay.name; syncedStationName = stationToPlay.name; prefs.edit().putInt("last_selected_id", stationToPlay.id).apply(); if (selectedCategory != "Lemmikud" && selectedCategory != stationToPlay.category) { selectedCategory = stationToPlay.category; prefs.edit().putString("last_category", stationToPlay.category).apply() }; playRadio(stationToPlay); currentTab = 0 } else { Toast.makeText(context, "Jaama '$stationName' ei leitud!", Toast.LENGTH_SHORT).show() } })
                    3 -> SettingsScreen(isRefreshing = isRefreshing, onRefresh = { scope.launch { isRefreshing = true; try { stationRepository.refreshStations(); Toast.makeText(context, "Uuendatud!", Toast.LENGTH_SHORT).show() } catch (e: Exception) { } finally { isRefreshing = false } } }, onAddTestData = { scope.launch { stationRepository.insertTestHistory(); Toast.makeText(context, "Testandmed lisatud!", Toast.LENGTH_SHORT).show() } })
                    4 -> ee.minu.kellraadio.ui.InfoScreen()
                }
            }
        }
    }

    if (showSleepDialog) { SleepTimerDialog(initialMillis = sleepTimerMillis, onDismiss = { showSleepDialog = false }) }
    if (showAlarmDialog) {
        AlarmDialog(selectedStation = selectedStation, initialHour = alarmToEdit?.hour, initialMinute = alarmToEdit?.minute, initialDays = alarmToEdit?.days ?: emptySet(), onDismiss = { showAlarmDialog = false }, onDelete = if (alarmToEdit != null) { { alarmToEdit?.let { AlarmUtils.deleteAlarm(context, it) } } } else null, onAlarmSaved = { hour, minute, days -> selectedStation?.let { station -> val alarm = alarmToEdit?.copy(hour = hour, minute = minute, days = days, stationName = station.name, stationUrl = station.url, isEnabled = true) ?: Alarm(hour = hour, minute = minute, days = days, stationName = station.name, stationUrl = station.url); AlarmUtils.saveOrUpdateAlarm(context, alarm) } })
    }
}