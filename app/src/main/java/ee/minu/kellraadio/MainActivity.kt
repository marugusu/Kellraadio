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
import ee.minu.kellraadio.ui.PlayerControls
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RaadioEkraan() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 1. Orientatsioon ja Load
    val isLandscape = LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // 2. Andmebaas ja Repo
    val prefs = remember { context.getSharedPreferences("RaadioPrefs", Context.MODE_PRIVATE) }
    val database = remember { AppDatabase.getDatabase(context) }
    val repository = remember { RadioStationRepository(StationApiService.create(), database.radioStationDao()) }
    val stations by repository.allStations.collectAsState(initial = emptyList())

    // 3. UI Staatus (State)

    // Raadiojaama info
    var selectedStationId by rememberSaveable { mutableIntStateOf(-1) }
    var selectedStationName by rememberSaveable { mutableStateOf("") }

    // Leiame objekti (kui nimekiri on laetud)
    val selectedStation = stations.find { it.id == selectedStationId }

    var playingStationName by rememberSaveable { mutableStateOf("") }
    var syncedStationName by rememberSaveable { mutableStateOf("") }

    // Äratuse info
    var alarmTime by rememberSaveable { mutableLongStateOf(0L) }
    var alarmStationName by rememberSaveable { mutableStateOf("") }
    val alarmInfo = if (alarmTime > 0L && alarmStationName.isNotEmpty()) Pair(alarmTime, alarmStationName) else null

    var alarmDaysList by rememberSaveable { mutableStateOf<List<Int>>(emptyList()) }
    val alarmDays = alarmDaysList.toSet()

    var playerStatus by rememberSaveable { mutableStateOf("Peatatud") }
    var bitrateInfo by rememberSaveable { mutableStateOf("") }
    var parsedTitle by rememberSaveable { mutableStateOf("") }
    var parsedArtist by rememberSaveable { mutableStateOf("") }
    var parsedExtra by rememberSaveable { mutableStateOf("") }
    var isRefreshing by rememberSaveable { mutableStateOf(false) }
    var sleepTimerMillis by rememberSaveable { mutableLongStateOf(0L) }

    var showSleepDialog by rememberSaveable { mutableStateOf(false) }
    var showAlarmDialog by rememberSaveable { mutableStateOf(false) }

    // UUS MUUTUJA: Kas oleme jaamu juba laadinud?
    var hasFetchedStations by rememberSaveable { mutableStateOf(false) }

    // --- LEMMIKUTE JA KATEGOORIATE LOOGIKA (Uuendatud) ---
    val desiredOrder = listOf("Eesti", "Välis")
    var selectedCategory by rememberSaveable { mutableStateOf(prefs.getString("last_category", "Eesti") ?: "Eesti") }

    val favoriteStations = stations.filter { it.isFavorite }
    val baseCategories = stations.map { it.category }.distinct().toMutableList()

    if (favoriteStations.isNotEmpty()) {
        baseCategories.add(0, "Lemmikud")
    }

    val finalCategories = baseCategories.sortedWith(compareBy<String> { category ->
        if (category == "Lemmikud") -1
        else {
            val index = desiredOrder.indexOf(category)
            if (index != -1) index else Int.MAX_VALUE
        }
    }.thenBy { it })

    val filteredStations = if (selectedCategory == "Lemmikud") {
        favoriteStations
    } else {
        stations.filter { it.category == selectedCategory }
    }
    // -------------------------------------------------------

    // Elutsükkel
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

    // ALG-LAADIMINE
    LaunchedEffect(Unit) {
        LocalBroadcastManager.getInstance(context).sendBroadcast(Intent(RadioService.ACTION_GET_STATUS))

        scope.launch {
            if (!hasFetchedStations) {
                repository.refreshStations()
                hasFetchedStations = true
            }
        }
    }

    // Uuendame ID-d ja kategooriat, kui jaam vahetub
    LaunchedEffect(stations, playingStationName) {
        if (stations.isNotEmpty()) {
            if (playingStationName.isNotEmpty()) {
                val found = stations.find { it.name == playingStationName }
                if (found != null) {
                    selectedStationId = found.id
                    selectedStationName = found.name

                    if (playingStationName != syncedStationName) {
                        if (selectedCategory != "Lemmikud") {
                            selectedCategory = found.category
                        }
                        syncedStationName = playingStationName
                    }
                }
            } else if (selectedStationId == -1 && !isPlaying) {
                val defaultCat = if (favoriteStations.isNotEmpty()) "Lemmikud" else "Eesti"
                stations.firstOrNull { it.category == defaultCat }?.let {
                    selectedStationId = it.id
                    selectedStationName = it.name
                    selectedCategory = it.category
                }
            }
        }
    }

    // Broadcast
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

    // Kontrollime salvestatud äratust
    LaunchedEffect(Unit) {
        if (alarmStationName.isEmpty()) {
            val t = AlarmState.getAlarmTime(context)
            val n = AlarmState.getAlarmStationName(context)
            val d = AlarmState.getAlarmDays(context)

            if (n.isNotEmpty() && (t > System.currentTimeMillis() || d.isNotEmpty())) {
                alarmTime = t
                alarmStationName = n
                alarmDaysList = d.toList()
            }
        }
    }

    fun playRadio(station: RadioStation) {
        val i = Intent(context, RadioService::class.java).apply { putExtra("STREAM_URL", station.url); putExtra("STATION_NAME", station.name); putExtra("TRIGGERED_BY", "USER") }
        context.startForegroundService(i)
    }

    // --- UI SISU ---
    if (isLandscape) {
        Row(modifier = Modifier.fillMaxSize().padding(16.dp).statusBarsPadding(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            PlayerControls(
                selectedStation, selectedStationName, isPlaying, parsedTitle, parsedArtist, parsedExtra, playerStatus, bitrateInfo, alarmInfo, alarmDays, sleepTimerMillis,
                onPlayPause = { val i = Intent(context, RadioService::class.java).apply { action = RadioService.ACTION_PAUSE }; context.startService(i) },
                onPlayStation = { station ->
                    selectedStationId = station.id; selectedStationName = station.name
                    playRadio(station)
                },
                onSleepClick = { showSleepDialog = true },
                onAlarmCancel = { AlarmUtils.cancelAlarm(context); alarmTime = 0L; alarmStationName = "" },
                onAlarmSet = { showAlarmDialog = true },
                modifier = Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())
            )
            StationList(
                stations = stations,
                filteredStations = filteredStations,
                categories = finalCategories,
                selectedCategory = selectedCategory,
                selectedStationId = selectedStationId,
                playerStatus = playerStatus,
                isRefreshing = isRefreshing,
                onCategorySelect = { cat ->
                    selectedCategory = cat
                    prefs.edit().putString("last_category", cat).apply()
                },
                onRefresh = {
                    scope.launch {
                        isRefreshing = true
                        try {
                            repository.refreshStations()
                            // TAASTATUD TEADE:
                            Toast.makeText(context, "Jaamad uuendatud!", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(context, "Viga uuendamisel!", Toast.LENGTH_SHORT).show()
                        } finally {
                            isRefreshing = false
                        }
                    }
                },
                onStationSelect = { station ->
                    selectedStationId = station.id
                    selectedStationName = station.name
                    syncedStationName = station.name
                    playRadio(station)
                },
                onStationLongClick = { station ->
                    scope.launch { repository.toggleFavorite(station) }
                },
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
        }
    } else {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp).statusBarsPadding()) {
            PlayerControls(
                selectedStation, selectedStationName, isPlaying, parsedTitle, parsedArtist, parsedExtra, playerStatus, bitrateInfo, alarmInfo, alarmDays, sleepTimerMillis,
                onPlayPause = { val i = Intent(context, RadioService::class.java).apply { action = RadioService.ACTION_PAUSE }; context.startService(i) },
                onPlayStation = { station ->
                    selectedStationId = station.id; selectedStationName = station.name
                    playRadio(station)
                },
                onSleepClick = { showSleepDialog = true },
                onAlarmCancel = { AlarmUtils.cancelAlarm(context); alarmTime = 0L; alarmStationName = "" },
                onAlarmSet = { showAlarmDialog = true },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))
            StationList(
                stations = stations,
                filteredStations = filteredStations,
                categories = finalCategories,
                selectedCategory = selectedCategory,
                selectedStationId = selectedStationId,
                playerStatus = playerStatus,
                isRefreshing = isRefreshing,
                onCategorySelect = { cat ->
                    selectedCategory = cat
                    prefs.edit().putString("last_category", cat).apply()
                },
                onRefresh = {
                    scope.launch {
                        isRefreshing = true
                        try {
                            repository.refreshStations()
                            // TAASTATUD TEADE:
                            Toast.makeText(context, "Jaamad uuendatud!", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(context, "Viga uuendamisel!", Toast.LENGTH_SHORT).show()
                        } finally {
                            isRefreshing = false
                        }
                    }
                },
                onStationSelect = { station ->
                    selectedStationId = station.id
                    selectedStationName = station.name
                    syncedStationName = station.name
                    playRadio(station)
                },
                onStationLongClick = { station ->
                    scope.launch { repository.toggleFavorite(station) }
                },
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
        }
    }

    if (showSleepDialog) { SleepTimerDialog(initialMillis = sleepTimerMillis, onDismiss = { showSleepDialog = false }) }
    if (showAlarmDialog) {
        AlarmDialog(
            selectedStation = selectedStation,
            onDismiss = { showAlarmDialog = false },
            onAlarmSaved = { time, days ->
                alarmTime = time; alarmStationName = selectedStation?.name ?: ""
                alarmDaysList = days.toList()
            }
        )
    }
}