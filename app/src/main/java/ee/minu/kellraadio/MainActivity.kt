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
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import ee.minu.kellraadio.ui.AlarmDialog
import ee.minu.kellraadio.ui.HistoryScreen
import ee.minu.kellraadio.ui.PlayerControls
import ee.minu.kellraadio.ui.SettingsScreen
import ee.minu.kellraadio.ui.SleepTimerDialog
import ee.minu.kellraadio.ui.StationList
import kotlinx.coroutines.launch
import java.util.Calendar

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

    // 1. Orientatsioon ja Load
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

    // 2. Andmebaas ja Repo
    val prefs = remember { context.getSharedPreferences("RaadioPrefs", Context.MODE_PRIVATE) }
    val database = remember { AppDatabase.getDatabase(context) }
    val repository = remember { RadioStationRepository(StationApiService.create(), database.radioStationDao(), database.historyDao()) }
    val stations by repository.allStations.collectAsState(initial = emptyList())

    // 3. UI Staatus (State)
    var selectedStationId by rememberSaveable {
        mutableIntStateOf(prefs.getInt("last_selected_id", -1))
    }
    var selectedStationName by rememberSaveable { mutableStateOf("") }
    val selectedStation = stations.find { it.id == selectedStationId }

    var playingStationName by rememberSaveable { mutableStateOf("") }
    var syncedStationName by rememberSaveable { mutableStateOf("") }

    // Äratuse info
    var alarmTime by rememberSaveable { mutableLongStateOf(0L) }
    var alarmStationName by rememberSaveable { mutableStateOf("") }
    val alarmInfo = if (alarmTime > 0L && alarmStationName.isNotEmpty()) Pair(alarmTime, alarmStationName) else null
    var alarmDaysList by rememberSaveable { mutableStateOf<List<Int>>(emptyList()) }
    val alarmDays = alarmDaysList.toSet()

    // Mängija info
    var playerStatus by rememberSaveable { mutableStateOf("Peatatud") }
    var bitrateInfo by rememberSaveable { mutableStateOf("") }
    var parsedTitle by rememberSaveable { mutableStateOf("") }
    var parsedArtist by rememberSaveable { mutableStateOf("") }
    var parsedExtra by rememberSaveable { mutableStateOf("") }
    var isRefreshing by rememberSaveable { mutableStateOf(false) }
    var sleepTimerMillis by rememberSaveable { mutableLongStateOf(0L) }

    var showSleepDialog by rememberSaveable { mutableStateOf(false) }
    var showAlarmDialog by rememberSaveable { mutableStateOf(false) }
    var hasFetchedStations by rememberSaveable { mutableStateOf(false) }

    // --- NAVIGATION STATE ---
    // 0=Raadio, 1=Ajalugu, 2=Seaded, 3=Info
    var currentTab by rememberSaveable { mutableIntStateOf(0) }

    // --- KATEGOORIATE LOOGIKA ---
    val desiredOrder = listOf("Eesti", "Välis")
    var selectedCategory by rememberSaveable { mutableStateOf(prefs.getString("last_category", "Eesti") ?: "Eesti") }

    val favoriteStations = stations.filter { it.isFavorite }
    val baseCategories = stations.map { it.category }.distinct().toMutableList()

    if (favoriteStations.isNotEmpty()) {
        baseCategories.add(0, "Lemmikud")
    }

    val finalCategories = baseCategories.sortedWith(compareBy<String> {
        if (it == "Lemmikud") -1
        else {
            val index = desiredOrder.indexOf(it)
            if (index != -1) index else Int.MAX_VALUE
        }
    }.thenBy { it })

    // --- FILTREERIMINE ---
    val filteredStations = remember<List<RadioStation>>(selectedCategory, stations, favoriteStations) {
        if (selectedCategory == "Lemmikud") {
            favoriteStations
        } else {
            stations.filter { it.category == selectedCategory }
        }
    }

    // --- UI ELUTSÜKKEL JA BROADCAST ---
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
        scope.launch {
            if (!hasFetchedStations) {
                repository.refreshStations()
                hasFetchedStations = true
            }
        }
    }

    // Uuendame valikut kui jaam vahetub
    LaunchedEffect(stations, playingStationName) {
        if (stations.isNotEmpty() && playingStationName.isNotEmpty()) {
            val found = stations.find { it.name == playingStationName }
            if (found != null) {
                selectedStationId = found.id
                selectedStationName = found.name

                if (playingStationName != syncedStationName && selectedCategory != "Lemmikud") {
                    selectedCategory = found.category
                    syncedStationName = playingStationName
                }
            }
        }
    }

    // Broadcast Receiver
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    RadioService.ACTION_STATION_CHANGED -> {
                        playingStationName = intent.getStringExtra("STATION_NAME") ?: ""
                        playerStatus = "Mängib"; parsedArtist = playingStationName; parsedTitle = "Otseeeter"; parsedExtra = ""
                    }
                    RadioService.ACTION_METADATA_UPDATED -> {
                        playerStatus = "Mängib"
                        parsedTitle = intent.getStringExtra("PARSED_TITLE") ?: ""
                        parsedArtist = intent.getStringExtra("PARSED_ARTIST") ?: ""
                        parsedExtra = intent.getStringExtra("PARSED_EXTRA") ?: ""
                    }
                    RadioService.ACTION_BITRATE_UPDATED -> bitrateInfo = intent.getStringExtra("BITRATE_INFO") ?: ""
                    RadioService.ACTION_PLAYER_ERROR -> { playerStatus = "Viga ühendusega"; bitrateInfo = "" }
                    RadioService.ACTION_PLAYER_STOPPED -> { playerStatus = "Peatatud"; bitrateInfo = "" }
                    RadioService.ACTION_TIMER_TICK -> sleepTimerMillis = intent.getLongExtra("REMAINING_MILLIS", 0L)
                    "ee.minu.kellraadio.ALARM_TRIGGERED" -> {
                        playerStatus = "Mängib (ÄRATUS)"
                        val nextTime = AlarmState.getAlarmTime(context)
                        val nextName = AlarmState.getAlarmStationName(context)
                        if (nextTime > System.currentTimeMillis() && nextName.isNotEmpty()) {
                            alarmTime = nextTime; alarmStationName = nextName
                            alarmDaysList = AlarmState.getAlarmDays(context).toList()
                        } else {
                            alarmTime = 0L; alarmStationName = ""; alarmDaysList = emptyList()
                        }
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(RadioService.ACTION_STATION_CHANGED); addAction(RadioService.ACTION_METADATA_UPDATED)
            addAction(RadioService.ACTION_BITRATE_UPDATED); addAction(RadioService.ACTION_PLAYER_ERROR)
            addAction(RadioService.ACTION_PLAYER_STOPPED); addAction(RadioService.ACTION_TIMER_TICK)
            addAction(RadioService.ACTION_GET_STATUS); addAction("ee.minu.kellraadio.ALARM_TRIGGERED")
        }
        LocalBroadcastManager.getInstance(context).registerReceiver(receiver, filter)
        onDispose { LocalBroadcastManager.getInstance(context).unregisterReceiver(receiver) }
    }

    LaunchedEffect(Unit) {
        if (alarmStationName.isEmpty()) {
            val t = AlarmState.getAlarmTime(context)
            val n = AlarmState.getAlarmStationName(context)
            val d = AlarmState.getAlarmDays(context)
            if (n.isNotEmpty() && (t > System.currentTimeMillis() || d.isNotEmpty())) {
                alarmTime = t; alarmStationName = n; alarmDaysList = d.toList()
            }
        }
    }

    fun playRadio(station: RadioStation) {
        val i = Intent(context, RadioService::class.java).apply {
            putExtra("STREAM_URL", station.url)
            putExtra("STATION_NAME", station.name)
            putExtra("TRIGGERED_BY", "USER")
            // UUS: Saadame kaasa valitud kategooria
            putExtra("CATEGORY_NAME", selectedCategory)
        }
        context.startForegroundService(i)
    }

    // --- UI SISU ---

    if (isLandscape) {
        // LANDSCAPE (RÕHTPAIGUTUS)
        Row(
            modifier = Modifier.fillMaxSize().statusBarsPadding()
        ) {
            NavigationRail {
                Spacer(modifier = Modifier.weight(1f))
                NavigationRailItem(
                    selected = currentTab == 0,
                    onClick = { currentTab = 0 },
                    icon = { Icon(Icons.Default.Radio, null) },
                    label = { Text("Raadio") }
                )
                NavigationRailItem(
                    selected = currentTab == 1,
                    onClick = { currentTab = 1 },
                    icon = { Icon(Icons.Default.History, null) }, // UUS
                    label = { Text("Ajalugu") }
                )
                NavigationRailItem(
                    selected = currentTab == 2,
                    onClick = { currentTab = 2 },
                    icon = { Icon(Icons.Default.Settings, null) },
                    label = { Text("Seaded") }
                )
                NavigationRailItem(
                    selected = currentTab == 3,
                    onClick = { currentTab = 3 },
                    icon = { Icon(Icons.Default.Info, null) },
                    label = { Text("Info") }
                )
                Spacer(modifier = Modifier.weight(1f))
            }

            VerticalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.surfaceVariant)

            Box(
                modifier = Modifier
                    .weight(playerWeight)
                    .padding(start = 16.dp, top = 16.dp, bottom = 16.dp, end = 8.dp)
            ) {
                PlayerControls(
                    selectedStation, selectedStationName, isPlaying, parsedTitle, parsedArtist, parsedExtra, playerStatus, bitrateInfo, alarmInfo, alarmDays, sleepTimerMillis,
                    isFavorite = selectedStation?.isFavorite ?: false, // <--- UUS
                    onPlayPause = { val i = Intent(context, RadioService::class.java).apply { action = RadioService.ACTION_PAUSE }; context.startService(i) },
                    onPlayStation = { station -> selectedStationId = station.id; selectedStationName = station.name; playRadio(station) },
                    onSleepClick = { showSleepDialog = true },
                    onAlarmClick = { showAlarmDialog = true },
                    onAlarmLongClick = { AlarmUtils.cancelAlarm(context); alarmTime = 0L; alarmStationName = "" },
                    onToggleFavorite = { selectedStation?.let { scope.launch { repository.toggleFavorite(it) } } }, // <--- UUS
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f - playerWeight)
                    .fillMaxHeight()
                //.padding(top = 4.dp)
            ) {
                when (currentTab) {
                    0 -> { // RAADIO
                        StationList(
                            stations = stations,
                            filteredStations = filteredStations,
                            categories = finalCategories,
                            selectedCategory = selectedCategory,
                            selectedStationId = selectedStationId,
                            playerStatus = playerStatus,
                            isRefreshing = isRefreshing,
                            onCategorySelect = { cat -> selectedCategory = cat; prefs.edit().putString("last_category", cat).apply() },
                            onRefresh = { scope.launch { isRefreshing = true; try { repository.refreshStations(); Toast.makeText(context, "Uuendatud!", Toast.LENGTH_SHORT).show() } catch (e: Exception) { } finally { isRefreshing = false } } },
                            onStationSelect = { station ->
                                selectedStationId = station.id
                                selectedStationName = station.name
                                syncedStationName = station.name
                                prefs.edit().putInt("last_selected_id", station.id).apply()
                                playRadio(station)
                            },
                            onStationLongClick = { station -> scope.launch { repository.toggleFavorite(station) } },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    1 -> { // AJALUGU (LANDSCAPE)
                        HistoryScreen(
                            repository = repository,
                            onClearHistory = { scope.launch { repository.clearHistory() } },
                            onPlayStationByName = { stationName ->
                                // See kood on täpselt sama, mis püstvaates
                                val stationToPlay = stations.find { it.name == stationName }

                                if (stationToPlay != null) {
                                    selectedStationId = stationToPlay.id
                                    selectedStationName = stationToPlay.name
                                    syncedStationName = stationToPlay.name
                                    prefs.edit().putInt("last_selected_id", stationToPlay.id).apply()

                                    if (selectedCategory != "Lemmikud" && selectedCategory != stationToPlay.category) {
                                        selectedCategory = stationToPlay.category
                                        prefs.edit().putString("last_category", stationToPlay.category).apply()
                                    }

                                    playRadio(stationToPlay)
                                    currentTab = 0 // Viime kasutaja tagasi vasakule "Raadio" vaatesse
                                } else {
                                    Toast.makeText(context, "Jaama '$stationName' ei leitud!", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                    2 -> { // SEADED
                        SettingsScreen(
                            isRefreshing = isRefreshing,
                            onRefresh = { scope.launch { isRefreshing = true; try { repository.refreshStations(); Toast.makeText(context, "Uuendatud!", Toast.LENGTH_SHORT).show() } catch (e: Exception) { } finally { isRefreshing = false } } },
                            onAddTestData = { // LISA SEE BLOKK
                                scope.launch {
                                    repository.insertTestHistory()
                                    Toast.makeText(context, "Testandmed lisatud!", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                    3 -> { // INFO
                        ee.minu.kellraadio.ui.InfoScreen()
                    }
                }
            }
        }
    } else {
        // PORTRAIT (PÜSTPAIGUTUS)
        Scaffold(
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = currentTab == 0,
                        onClick = { currentTab = 0 },
                        icon = { Icon(Icons.Default.Radio, contentDescription = null) },
                        label = { Text("Raadio") }
                    )
                    NavigationBarItem(
                        selected = currentTab == 1,
                        onClick = { currentTab = 1 },
                        icon = { Icon(Icons.Default.History, contentDescription = null) }, // UUS
                        label = { Text("Ajalugu") }
                    )
                    NavigationBarItem(
                        selected = currentTab == 2,
                        onClick = { currentTab = 2 },
                        icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                        label = { Text("Seaded") }
                    )
                    NavigationBarItem(
                        selected = currentTab == 3,
                        onClick = { currentTab = 3 },
                        icon = { Icon(Icons.Default.Info, contentDescription = null) },
                        label = { Text("Info") }
                    )
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .padding(bottom = innerPadding.calculateBottomPadding()) // Ainult alumine äär
                // EEMALDATUD: .padding(innerPadding) ja .padding(horizontal = 16.dp)
            ) {
                PlayerControls(
                    selectedStation, selectedStationName, isPlaying, parsedTitle, parsedArtist, parsedExtra, playerStatus, bitrateInfo, alarmInfo, alarmDays, sleepTimerMillis,
                    isFavorite = selectedStation?.isFavorite ?: false, // <--- UUS
                    onPlayPause = { val i = Intent(context, RadioService::class.java).apply { action = RadioService.ACTION_PAUSE }; context.startService(i) },
                    onPlayStation = { station -> selectedStationId = station.id; selectedStationName = station.name; playRadio(station) },
                    onSleepClick = { showSleepDialog = true },
                    onAlarmClick = { showAlarmDialog = true },
                    onAlarmLongClick = { AlarmUtils.cancelAlarm(context); alarmTime = 0L; alarmStationName = "" },
                    onToggleFavorite = { selectedStation?.let { scope.launch { repository.toggleFavorite(it) } } }, // <--- UUS
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 0.dp)
                )

                //Spacer(Modifier.height(8.dp))

                when (currentTab) {
                    0 -> { // RAADIO
                        StationList(
                            stations = stations,
                            filteredStations = filteredStations,
                            categories = finalCategories,
                            selectedCategory = selectedCategory,
                            selectedStationId = selectedStationId,
                            playerStatus = playerStatus,
                            isRefreshing = isRefreshing,
                            onCategorySelect = { cat -> selectedCategory = cat; prefs.edit().putString("last_category", cat).apply() },
                            onRefresh = { scope.launch { isRefreshing = true; try { repository.refreshStations(); Toast.makeText(context, "Uuendatud!", Toast.LENGTH_SHORT).show() } catch (e: Exception) { } finally { isRefreshing = false } } },
                            onStationSelect = { station ->
                                selectedStationId = station.id
                                selectedStationName = station.name
                                syncedStationName = station.name
                                prefs.edit().putInt("last_selected_id", station.id).apply()
                                playRadio(station)
                            },
                            onStationLongClick = { station -> scope.launch { repository.toggleFavorite(station) } },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    1 -> { // AJALUGU (UUENDATUD)
                        HistoryScreen(
                            repository = repository,
                            onClearHistory = { scope.launch { repository.clearHistory() } },
                            onPlayStationByName = { stationName ->
                                // 1. Otsime jaama nime järgi mälust
                                val stationToPlay = stations.find { it.name == stationName }

                                if (stationToPlay != null) {
                                    // 2. Valime selle jaama aktiivseks
                                    selectedStationId = stationToPlay.id
                                    selectedStationName = stationToPlay.name
                                    syncedStationName = stationToPlay.name
                                    prefs.edit().putInt("last_selected_id", stationToPlay.id).apply()

                                    // 3. Kui jaam on teises kategoorias (ja pole Lemmikutes), vahetame kategooriat
                                    // See on vajalik, et jaam oleks nimekirjas nähtav, kui sinna tagasi minna
                                    if (selectedCategory != "Lemmikud" && selectedCategory != stationToPlay.category) {
                                        selectedCategory = stationToPlay.category
                                        prefs.edit().putString("last_category", stationToPlay.category).apply()
                                    }

                                    // 4. Käivitame raadio
                                    playRadio(stationToPlay)

                                    // 5. Suuname kasutaja automaatselt tagasi "Raadio" vaatesse
                                    currentTab = 0
                                } else {
                                    // Haruldane juhus: jaam on vahepeal serverist kustutatud
                                    Toast.makeText(context, "Jaama '$stationName' ei leitud!", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                    2 -> { // SEADED
                        SettingsScreen(
                            isRefreshing = isRefreshing,
                            onRefresh = { scope.launch { isRefreshing = true; try { repository.refreshStations(); Toast.makeText(context, "Uuendatud!", Toast.LENGTH_SHORT).show() } catch (e: Exception) { } finally { isRefreshing = false } } },
                            onAddTestData = { // LISA SEE BLOKK
                                scope.launch {
                                    repository.insertTestHistory()
                                    Toast.makeText(context, "Testandmed lisatud!", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                    3 -> { // INFO
                        ee.minu.kellraadio.ui.InfoScreen()
                    }
                }
            }
        }
    }

    if (showSleepDialog) { SleepTimerDialog(initialMillis = sleepTimerMillis, onDismiss = { showSleepDialog = false }) }

    if (showAlarmDialog) {
        val (initHour, initMinute) = if (alarmInfo != null) {
            val cal = Calendar.getInstance().apply { timeInMillis = alarmInfo.first }
            Pair(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
        } else {
            Pair(null, null)
        }

        AlarmDialog(
            selectedStation = selectedStation,
            initialHour = initHour,
            initialMinute = initMinute,
            initialDays = alarmDays,
            onDismiss = { showAlarmDialog = false },
            onDelete = if (alarmInfo != null) {
                { AlarmUtils.cancelAlarm(context); alarmTime = 0L; alarmStationName = "" }
            } else null,
            onAlarmSaved = { time, days ->
                alarmTime = time; alarmStationName = selectedStation?.name ?: ""; alarmDaysList = days.toList()
            }
        )
    }
}