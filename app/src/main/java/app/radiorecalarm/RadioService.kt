package app.radiorecalarm

import android.app.Service
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.annotation.OptIn
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import app.radiorecalarm.widget.HomeWidget
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Metadata
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.extractor.metadata.icy.IcyHeaders
import androidx.media3.extractor.metadata.icy.IcyInfo
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import java.util.concurrent.CopyOnWriteArraySet
import app.radiorecalarm.R
import android.widget.Toast

@Suppress("DEPRECATION")
class RadioService : Service() {

    private lateinit var player: SkodaAwarePlayer

    private var mediaSession: MediaSession? = null
    private var currentStationName: String = "Radio"
    private var currentStationBitmap: android.graphics.Bitmap? = null

    private var isChangingStation = false
    private var isInitialStationPlayback = false
    private var currentCategory: String = ""
    private var lastBitrateInfo: String = ""

    private var currentArtist: String = ""
    private var currentTitle: String = ""
    private var currentExtra: String = ""

    private var lastSentArtist: String = ""
    private var lastSentTitle: String = ""
    private var lastSentTime: Long = 0

    private var isAlarmMode: Boolean = false
    private var currentStreamUrl: String = ""

    private var metadataPushJob: kotlinx.coroutines.Job? = null
    private var pendingMetadataJob: kotlinx.coroutines.Job? = null
    private var bufferingWatchdogJob: kotlinx.coroutines.Job? = null
    private var activeDataSource: RetryingHttpDataSource? = null

    private var lastDefaultNetwork: android.net.Network? = null
    private var consecutiveErrorCount = 0
    private var lastErrorTime = 0L
    private var hasSuccessfullyStartedPlaying = false

    private var isRecording = false
    private var recordingFile: java.io.File? = null
    private var recordingOutputStream: java.io.FileOutputStream? = null
    private var recordingStartMillis = 0L
    private var recordingTimerJob: kotlinx.coroutines.Job? = null
    private var progressJob: kotlinx.coroutines.Job? = null

    private val sessionScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val notificationManager by lazy { RadioNotificationManager(this) }
    private val metadataHelper by lazy { RadioMetadataHelper(this) }
    private val prefs: SharedPreferences by lazy { getSharedPreferences("RaadioPrefs", Context.MODE_PRIVATE) }

    private var sleepTimerJob: kotlinx.coroutines.Job? = null
    private var sleepTimerRemainingMillis: Long = 0L

    private val listeners = CopyOnWriteArraySet<Player.Listener>()
    private var wakeLock: android.os.PowerManager.WakeLock? = null

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            Log.d(TAG, "VÕRK: Default ühendus saadaval ($network)")
            if (lastDefaultNetwork != null && lastDefaultNetwork != network) {
                Log.w(TAG, "VÕRK: Vaikimisi võrk muutus: $lastDefaultNetwork -> $network. Tühistame aktiivse ühenduse.")
                activeDataSource?.invalidateConnection()
            }
            lastDefaultNetwork = network
            
            serviceScope.launch(Dispatchers.Main) {
                if (::player.isInitialized && player.playWhenReady && !player.isPlaying && !isChangingStation) {
                    Log.d(TAG, "VÕRK: Ühendus taastus ja raadio ei mängi. Taastan taasesituse ilma metaandmeid nullimata...")
                    retryPlaybackWithoutMetadataWipe("NETWORK_RESTORED")
                }
            }
        }
        override fun onLost(network: Network) {
            Log.w(TAG, "VÕRK: Ühendus kadunud! ($network)")
            if (lastDefaultNetwork == network) {
                lastDefaultNetwork = null
            }
        }
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            val hasInternet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            val isCellular = caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
            Log.d(TAG, "VÕRK: Parameetrid muutusid ($network). Mobiilne=$isCellular, Internet=$hasInternet")
        }
    }

    companion object {
        const val ACTION_UPDATE_STATION_NAME = "app.radiorecalarm.UPDATE_NAME"
        const val ACTION_FORCE_WIDGET_UPDATE = "app.radiorecalarm.FORCE_WIDGET_UPDATE"

        const val ACTION_STATION_SELECTED_BY_SERVICE = "app.radiorecalarm.STATION_SELECTED"
        const val ACTION_START_RECORDING = "app.radiorecalarm.ACTION_START_RECORDING"
        const val ACTION_STOP_RECORDING = "app.radiorecalarm.ACTION_STOP_RECORDING"
        const val ACTION_RECORDING_STATUS = "app.radiorecalarm.RECORDING_STATUS"
        const val ACTION_PAUSE = "app.radiorecalarm.ACTION_PAUSE"
        const val ACTION_RESUME = "app.radiorecalarm.ACTION_RESUME"
        const val ACTION_STOP = "app.radiorecalarm.ACTION_STOP"
        const val ACTION_SKIP_NEXT = "app.radiorecalarm.ACTION_SKIP_NEXT"
        const val ACTION_SKIP_PREVIOUS = "app.radiorecalarm.ACTION_SKIP_PREVIOUS"
        const val ACTION_STATION_CHANGED = "app.radiorecalarm.STATION_CHANGED"
        const val ACTION_BITRATE_UPDATED = "app.radiorecalarm.BITRATE_UPDATED"
        const val ACTION_METADATA_UPDATED = "app.radiorecalarm.METADATA_UPDATED"
        const val ACTION_PLAYER_STOPPED = "app.radiorecalarm.PLAYER_STOPPED"
        const val ACTION_PLAYER_PAUSED = "app.radiorecalarm.PLAYER_PAUSED"
        const val ACTION_PLAYER_ERROR = "app.radiorecalarm.PLAYER_ERROR"
        const val ACTION_GET_STATUS = "app.radiorecalarm.GET_STATUS"
        const val TAG = "RadioService"
        const val ACTION_SET_TIMER = "app.radiorecalarm.SET_TIMER"
        const val ACTION_TIMER_TICK = "app.radiorecalarm.TIMER_TICK"
        const val EXTRA_TIMER_DURATION = "TIMER_DURATION_MINUTES"
        const val ACTION_PLAY_PAUSE_TOGGLE = "app.radiorecalarm.ACTION_PLAY_PAUSE_TOGGLE"
        const val ACTION_PLAYBACK_PROGRESS = "app.radiorecalarm.ACTION_PLAYBACK_PROGRESS"
        const val ACTION_SEEK = "app.radiorecalarm.ACTION_SEEK"
        const val EXTRA_SEEK_POSITION = "SEEK_POSITION"

    }

    private val mediaSessionCallback = object : MediaSession.Callback {
        override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
            val result = MediaSession.ConnectionResult.AcceptedResultBuilder(session).build()
            
            // Kui auto ühendub, saadame talle kohe jõuga viimati teadaolevad andmed (tervitus)
            if (currentTitle.isNotEmpty() && currentStationName.isNotEmpty()) {
                Log.d(TAG, "onConnect: Auto ühendus! Saadame ekraanile info: Title='$currentTitle', Artist='$currentArtist'")
                
                // Nullime kaitsed samamoodi nagu uue jaama laadimisel, et info kindlasti läbi läheks
                lastSentTitle = ""
                lastSentArtist = ""
                lastSentTime = 0
                updateExternalDevices(currentTitle, currentArtist)
            }
            
            return result
        }

        override fun onPlayerCommandRequest(session: MediaSession, controller: MediaSession.ControllerInfo, playerCommand: Int): Int {
            when (playerCommand) {
                Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> {
                    changeStation(1)
                    return SessionResult.RESULT_SUCCESS
                }
                Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> {
                    changeStation(-1)
                    return SessionResult.RESULT_SUCCESS
                }
                Player.COMMAND_PLAY_PAUSE -> {
                    if (player.isPlaying) {
                        player.pause()
                    } else {
                        if (currentStreamUrl.isNotEmpty()) {
                            playStation(currentStreamUrl, currentStationName, "USER_RESUME")
                        }
                    }
                    return SessionResult.RESULT_SUCCESS
                }
            }
            return super.onPlayerCommandRequest(session, controller, playerCommand)
        }
    }

    private fun changeStation(offset: Int) {
        isChangingStation = true
        
        currentCategory = prefs.getString("last_category", currentCategory) ?: currentCategory
        
        serviceScope.launch {
            val db = AppDatabase.getDatabase(applicationContext)
            val dao = db.radioStationDao()
            val allStations = dao.getAllActiveStationsSync()
            if (allStations.isEmpty()) return@launch

            val navigationList = when (currentCategory) {
                "Favorites" -> allStations.filter { it.isFavorite }
                    .sortedWith(compareBy<RadioStation> { it.favoriteOrder }.thenBy { it.priority }.thenBy { it.name })
                "My" -> allStations.filter { it.isUserStation }
                "All", "" -> allStations
                else -> {
                    allStations.filter { it.category == currentCategory || it.countryCode == currentCategory }
                }
            }

            val finalNavList = if (navigationList.isEmpty() || navigationList.none { it.name == currentStationName }) {
                allStations
            } else {
                navigationList
            }

            val currentIndex = finalNavList.indexOfFirst { it.name == currentStationName }
            val baseIndex = if (currentIndex == -1) 0 else currentIndex
            val nextIndex = (baseIndex + offset + finalNavList.size) % finalNavList.size
            val nextStation = finalNavList[nextIndex]

            LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(
                Intent(ACTION_STATION_SELECTED_BY_SERVICE).apply {
                    putExtra("STATION_ID", nextStation.id)
                }
            )

            withContext(Dispatchers.Main) {
                isAlarmMode = false
                playStation(nextStation.url, nextStation.name, "USER")
            }
        }
    }

    private fun updatePlayerMetadata(trackTitleFromStream: String?) {
        val parsed = metadataHelper.parse(trackTitleFromStream ?: "", currentStationName)
        
        if (parsed.artist == currentArtist && parsed.title == currentTitle) {
            return
        }

        Log.d(TAG, "updatePlayerMetadata: RAW='$trackTitleFromStream' -> NEW PARSED Title='${parsed.title}' Artist='${parsed.artist}'")

        saveToHistory(parsed.artist, parsed.title)
        
        player.streamStartTime = SystemClock.elapsedRealtime()

        currentArtist = parsed.artist
        currentTitle = parsed.title
        currentExtra = parsed.extra
        
        prefs.edit()
            .putString("last_artist", currentArtist)
            .putString("last_title", currentTitle)
            .apply()
        
        updateExternalDevices(currentTitle, currentArtist)
        sendMetadataUpdate(currentTitle, currentArtist, currentExtra)
        updateNotification()
        updateWidget()
    }

    private fun sendBitrateUpdate() {
        LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(Intent(ACTION_BITRATE_UPDATED).apply { putExtra("BITRATE_INFO", lastBitrateInfo) })
    }

    private fun sendMetadataUpdate(parsedTitle: String, parsedArtist: String, parsedExtra: String = "") {
        if (parsedTitle == "..." || (parsedTitle == currentStationName && parsedArtist == "")) return
        LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(
            Intent(ACTION_METADATA_UPDATED).apply {
                putExtra("PARSED_TITLE", parsedTitle)
                putExtra("PARSED_ARTIST", parsedArtist)
                putExtra("PARSED_EXTRA", parsedExtra)
            }
        )
    }

    private fun retryPlaybackWithoutMetadataWipe(reason: String) {
        if (!::player.isInitialized || !player.playWhenReady || currentStreamUrl.isEmpty()) return
        Log.d(TAG, "retryPlaybackWithoutMetadataWipe: $reason. Reconnecting without wiping metadata (Title='$currentTitle', Artist='$currentArtist')...")
        activeDataSource?.invalidateConnection()
        player.seekToDefaultPosition()
        player.prepare()
        player.play()
    }

    private fun updateExternalDevices(title: String, artist: String) {
        if (!::player.isInitialized) return

        val currentTime = SystemClock.elapsedRealtime()
        val timeSinceLast = currentTime - lastSentTime

        if (timeSinceLast < 500) {
            Log.d(TAG, "updateExternalDevices: Debouncing update ($timeSinceLast ms since last) for Title='$title', Artist='$artist'")
            pendingMetadataJob?.cancel()
            pendingMetadataJob = sessionScope.launch {
                delay(500 - timeSinceLast)
                updateExternalDevices(title, artist)
            }
            return
        }

        if (title == lastSentTitle && artist == lastSentArtist) {
            return
        }

        pendingMetadataJob?.cancel()
        pendingMetadataJob = null

        Log.d(TAG, "updateExternalDevices: SENDING TO CAR -> Title='$title', Artist='$artist', Album='$currentStationName'")

        lastSentTitle = title
        lastSentArtist = artist
        lastSentTime = currentTime

        val newMetadata = metadataHelper.buildMediaMetadata(
            title = title,
            artist = artist,
            stationName = currentStationName,
            artworkData = null
        )

        player.updateTrackMetadata(newMetadata)
    }

    private val httpTransferListener = object : TransferListener {
        override fun onTransferStart(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) {
            if (source is HttpDataSource) {
                val headers = source.responseHeaders
                val icyBr = headers["icy-br"]?.firstOrNull()
                val icyDesc = headers["icy-description"]?.firstOrNull()
                if (!icyBr.isNullOrBlank()) { lastBitrateInfo = "$icyBr kbps"; sendBitrateUpdate() }
                else if (!icyDesc.isNullOrBlank() && icyDesc.contains("kbps")) {
                    "(\\d+)\\s*kbps".toRegex().find(icyDesc)?.let { lastBitrateInfo = it.value; sendBitrateUpdate() }
                }
            }
        }
        override fun onBytesTransferred(s: DataSource, d: DataSpec, n: Boolean, bytes: Int) {}
        override fun onTransferEnd(s: DataSource, d: DataSpec, n: Boolean) {}
        override fun onTransferInitializing(s: DataSource, d: DataSpec, n: Boolean) {}
    }

    private val playerListener = object : Player.Listener {
        override fun onMetadata(metadata: Metadata) {
            for (i in 0 until metadata.length()) {
                val entry = metadata.get(i)
                if (entry is IcyInfo) {
                    updatePlayerMetadata(entry.title)
                }
                if (entry is IcyHeaders && entry.bitrate != C.RATE_UNSET_INT) {
                    lastBitrateInfo = "${entry.bitrate} kbps"; sendBitrateUpdate()
                }
            }
        }

        override fun onTracksChanged(tracks: Tracks) {
            val t = player.currentTracks
            for (group in t.groups) {
                if (group.type == C.TRACK_TYPE_AUDIO && group.isSelected) {
                    val format = group.getTrackFormat(0)
                    if (format.bitrate != C.RATE_UNSET_INT && format.bitrate > 0) {
                        lastBitrateInfo = "${format.bitrate / 1000} kbps"; sendBitrateUpdate()
                    }
                }
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            val stateName = when(playbackState) {
                Player.STATE_IDLE -> "IDLE"
                Player.STATE_BUFFERING -> "BUFFERING"
                Player.STATE_READY -> "READY"
                Player.STATE_ENDED -> "ENDED"
                else -> "UNKNOWN"
            }
            Log.d(TAG, "Player olek muutus: $stateName (playWhenReady=${player.playWhenReady})")

            if (playbackState == Player.STATE_READY) {
                hasSuccessfullyStartedPlaying = true
                consecutiveErrorCount = 0
            }
            
            // LAHENDUS: Kui striim saab otsa (server paneb toru ära), proovi kiiresti uuesti valmistada (seek + prepare)
            if (playbackState == Player.STATE_ENDED && player.playWhenReady) {
                val isLocal = currentStreamUrl.startsWith("file://") || currentStreamUrl.startsWith("file:/")
                if (isLocal) {
                    stopRadio()
                } else {
                    Log.w(TAG, "Striim lõppes ootamatult (ENDED). Teeme kiire kordusettevalmistuse...")
                    player.seekToDefaultPosition()
                    player.prepare()
                }
            }

            // Watchdog: Kui jääb pikalt puhverdama, tee taaskäivitus
            if (playbackState == Player.STATE_BUFFERING && player.playWhenReady) {
                if (bufferingWatchdogJob == null || bufferingWatchdogJob?.isActive == false) {
                    Log.d(TAG, "onPlaybackStateChanged: Alustan puhverdamise valvurit (15s)...")
                    bufferingWatchdogJob = serviceScope.launch(Dispatchers.Main) {
                        delay(15000)
                        if (player.playbackState == Player.STATE_BUFFERING && player.playWhenReady) {
                            Log.w(TAG, "Valvur: Mängija on kestnud BUFFERING olekus üle 15 sekundi. Taaskäivitan ühenduse ilma metaandmeid nullimata...")
                            retryPlaybackWithoutMetadataWipe("WATCHDOG_TIMEOUT")
                        }
                    }
                }
            } else {
                if (bufferingWatchdogJob != null) {
                    Log.d(TAG, "onPlaybackStateChanged: Peatan puhverdamise valvuri (olek=$stateName).")
                    bufferingWatchdogJob?.cancel()
                    bufferingWatchdogJob = null
                }
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            updateNotification()
            updateWidget()

            if (isPlaying) {
                isChangingStation = false
                saveToHistory(currentArtist, currentTitle)
                LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(
                    Intent(ACTION_STATION_CHANGED).apply {
                        putExtra("STATION_NAME", currentStationName)
                        putExtra("CATEGORY_NAME", currentCategory)
                    }
                )
                sendMetadataUpdate(currentTitle, currentArtist, currentExtra)
                
                if (isInitialStationPlayback) {
                    isInitialStationPlayback = false
                    lastSentTitle = ""
                    lastSentArtist = ""
                    lastSentTime = 0
                    player.streamStartTime = SystemClock.elapsedRealtime()
                    updateExternalDevices(currentTitle, currentArtist)

                    metadataPushJob?.cancel()
                    metadataPushJob = sessionScope.launch {
                        delay(5000)
                        Log.d(TAG, "metadataPushJob: Initial station connection 5s refresh")
                        if (player.isPlaying && !isChangingStation) {
                            updateExternalDevices(currentTitle, currentArtist)
                        }
                    }
                } else {
                    // Striimi taastumine võrgukatkestusest või lühiajalisest puhverdamisest:
                    // Uuendame autot ainult siis, kui laul on vahepeal päriselt muutunud!
                    updateExternalDevices(currentTitle, currentArtist)
                }

                startProgressTracker()
            } else {
                if (!player.playWhenReady && !isChangingStation) {
                    val isLocal = currentStreamUrl.startsWith("file://") || currentStreamUrl.startsWith("file:/")
                    val action = if (isLocal) ACTION_PLAYER_PAUSED else ACTION_PLAYER_STOPPED
                    LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(Intent(action))
                }
                metadataPushJob?.cancel()
                pendingMetadataJob?.cancel()
                pendingMetadataJob = null
                stopProgressTracker()
            }
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (playWhenReady) {
                val notification = notificationManager.buildNotification(mediaSession!!, currentStationName, currentTitle, currentArtist, currentStationBitmap, isAlarmMode, player.isPlaying)
                if (Build.VERSION.SDK_INT >= 34) {
                    startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
                } else {
                    startForeground(1, notification)
                }
            }
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            Log.e(TAG, "PLAYER VIGA: ${error.message}", error)
            Log.e(TAG, "Vea tüüp: ${error.errorCodeName} (kood: ${error.errorCode})")
            
            LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(Intent(ACTION_PLAYER_ERROR))
            val cause = error.cause
            Log.e(TAG, "Vea põhjus (cause): ${cause?.message}")
            
            val isFatalError = when {
                cause is androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException -> cause.responseCode in 400..499
                cause is androidx.media3.exoplayer.source.UnrecognizedInputFormatException -> true
                error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED -> true
                else -> false
            }
            if (isFatalError) {
                Log.e(TAG, "Kriitiline viga, peatame raadio.")
                stopRadio(isError = true)
                return
            }
            isChangingStation = false

            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val activeNetwork = cm.activeNetwork
            val caps = cm.getNetworkCapabilities(activeNetwork)
            val hasInternet = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

            if (!hasInternet) {
                Log.w(TAG, "Mängija viga, kuid internet puudub. Ootame võrgu taastumist.")
                return
            }

            val now = SystemClock.elapsedRealtime()
            if (now - lastErrorTime > 10000) {
                consecutiveErrorCount = 0
            }
            consecutiveErrorCount++
            lastErrorTime = now

            if (consecutiveErrorCount > 5) {
                Log.e(TAG, "Liiga palju järjestikuseid vigu ($consecutiveErrorCount). Peatame raadio (Circuit Breaker).")
                consecutiveErrorCount = 0
                stopRadio(isError = true)
                return
            }

            serviceScope.launch {
                val delayTime = if (consecutiveErrorCount > 3) {
                    Log.w(TAG, "Mitu järjestikust viga ($consecutiveErrorCount). Ootame 5 sekundit enne kordusühendust...")
                    5000L
                } else {
                    Log.d(TAG, "Viga tuvastatud ($consecutiveErrorCount). Teeme korduskatse 500ms pärast...")
                    500L
                }
                delay(delayTime)
                withContext(Dispatchers.Main) {
                    if (currentStreamUrl.isNotEmpty() && player.playWhenReady) {
                        player.seekToDefaultPosition()
                        player.prepare()
                    }
                }
            }
        }
    }

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (currentStationName != "Radio" && currentStationName.isNotEmpty()) {
                 LocalBroadcastManager.getInstance(this@RadioService).sendBroadcast(
                    Intent(ACTION_STATION_CHANGED).apply { 
                        putExtra("STATION_NAME", currentStationName)
                        putExtra("CATEGORY_NAME", currentCategory)
                    }
                )
            }

            if (currentTitle.isNotEmpty() || currentArtist.isNotEmpty()) {
                sendMetadataUpdate(currentTitle, currentArtist, currentExtra)
            }

            if (player.isPlaying) {
                sendBitrateUpdate()
                sendTimerTick(sleepTimerRemainingMillis)
            } else { 
                LocalBroadcastManager.getInstance(this@RadioService).sendBroadcast(Intent(ACTION_PLAYER_STOPPED)) 
            }
            sendRecordingStatus()
        }
    }

    private fun sendRecordingStatus() {
        val intent = Intent(ACTION_RECORDING_STATUS).apply {
            putExtra("IS_RECORDING", isRecording)
            putExtra("RECORDING_DURATION", if (isRecording) android.os.SystemClock.elapsedRealtime() - recordingStartMillis else 0L)
            putExtra("RECORDING_STATION", currentStationName)
        }
        LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(intent)
    }

    private fun startProgressTracker() {
        stopProgressTracker()
        if (currentStreamUrl.startsWith("file://") || currentStreamUrl.startsWith("file:/")) {
            progressJob = sessionScope.launch {
                while (player.isPlaying) {
                    val pos = player.currentPosition
                    val dur = player.duration
                    broadcastPlaybackProgress(pos, dur)
                    delay(250)
                }
            }
        }
    }

    private fun stopProgressTracker() {
        progressJob?.cancel()
        progressJob = null
        broadcastPlaybackProgress(0L, 0L)
    }

    private fun broadcastPlaybackProgress(position: Long, duration: Long) {
        val intent = Intent(ACTION_PLAYBACK_PROGRESS).apply {
            putExtra("PLAYBACK_POSITION", position)
            putExtra("PLAYBACK_DURATION", duration)
        }
        LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(intent)
    }

    private fun startRecording() {
        if (isRecording || !::player.isInitialized || !player.isPlaying || currentStreamUrl.isEmpty() || currentStreamUrl.startsWith("file:")) {
            return
        }

        try {
            val folder = java.io.File(getExternalFilesDir(null), "Recordings")
            if (!folder.exists()) {
                folder.mkdirs()
            }

            val cleanStation = currentStationName.replace("[\\\\/:*?\"<>|]".toRegex(), "_")
            
            val artistTitlePart = if (currentArtist.isNotEmpty() && currentArtist != getString(R.string.live_broadcast) && currentArtist != currentStationName) {
                val cleanArtist = currentArtist.replace("[\\\\/:*?\"<>|]".toRegex(), "_").trim()
                val cleanTitle = currentTitle.replace("[\\\\/:*?\"<>|]".toRegex(), "_").trim()
                "_${cleanArtist}_$cleanTitle"
            } else {
                ""
            }

            val timestamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date())
            
            val ext = if (currentStreamUrl.contains(".aac", ignoreCase = true) || currentStreamUrl.contains("aac", ignoreCase = true)) {
                "aac"
            } else {
                "mp3"
            }

            val filename = "Recording_${cleanStation}${artistTitlePart}_$timestamp.$ext"
            val file = java.io.File(folder, filename)
            
            recordingOutputStream = java.io.FileOutputStream(file)
            recordingFile = file
            recordingStartMillis = android.os.SystemClock.elapsedRealtime()
            isRecording = true
            
            Log.i(TAG, "Alustati salvestamist faili: ${file.absolutePath}")
            sendRecordingStatus()

            recordingTimerJob?.cancel()
            recordingTimerJob = serviceScope.launch(Dispatchers.Main) {
                while (isRecording) {
                    delay(1000)
                    sendRecordingStatus()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Salvestamise alustamise viga: ${e.message}", e)
            Toast.makeText(applicationContext, getString(R.string.recording_failed), Toast.LENGTH_SHORT).show()
            stopRecording(failed = true)
        }
    }

    private fun stopRecording(failed: Boolean = false) {
        if (!isRecording) return

        isRecording = false
        recordingTimerJob?.cancel()
        recordingTimerJob = null

        val fileToSave = recordingFile
        try {
            recordingOutputStream?.flush()
            recordingOutputStream?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Salvestise sulgemise viga: ${e.message}")
        }
        recordingOutputStream = null
        recordingFile = null

        Log.i(TAG, "Salvestamine peatatud. Fail: ${fileToSave?.absolutePath}")

        if (fileToSave != null) {
            if (failed || fileToSave.length() < 10240) {
                Log.w(TAG, "Salvestis on liiga väike (${fileToSave.length()} baiti) või ebaõnnestus, kustutame.")
                fileToSave.delete()
            } else {
                serviceScope.launch(Dispatchers.Main) {
                    Toast.makeText(applicationContext, getString(R.string.recording_toast_saved, fileToSave.name), Toast.LENGTH_LONG).show()
                }
            }
        }

        sendRecordingStatus()
    }

    override fun onCreate() {
        super.onCreate()
        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("ExoPlayer/2.19.1 (Linux;Android 14)") // Standardne ja anonüümne tunnuse
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(10000) // Suurendatud 2s -> 10s mobiilivõrgu toeks
            .setReadTimeoutMs(5000)     // Vähendatud 10s -> 5s kiiremaks hangumise tuvastamiseks
            .setTransferListener(httpTransferListener)

        // Suurem puhver, et elada üle võrgu kõikumised
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                30_000, // Min puhver (30s)
                60_000, // Max puhver (60s)
                2_000,  // Kiire käivitus (2s)
                2_000   // Taaskäivitus (2s)
            )
            .setPrioritizeTimeOverSizeThresholds(true) // Prioritiseeri aega, mitte mahtu
            .build()

        val trackSelector = DefaultTrackSelector(this).apply { setParameters(buildUponParameters().setForceHighestSupportedBitrate(true)) }
        
        // Kohandatud kordusviivituse poliitika koos eksponentsiaalse kasvuga
        val loadErrorHandlingPolicy = object : DefaultLoadErrorHandlingPolicy() {
            override fun getMinimumLoadableRetryCount(dataType: Int): Int = 99
            override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
                if (hasSuccessfullyStartedPlaying) {
                    Log.w(TAG, "Võrgu viga keset esitust. Katkestame kohe taustal taastamise, et teha puhas reprepare.")
                    return C.TIME_UNSET
                }
                val retryCount = loadErrorInfo.errorCount
                return when {
                    retryCount <= 1 -> 1000L   // 1s
                    retryCount <= 2 -> 2000L   // 2s
                    retryCount <= 3 -> 5000L   // 5s
                    else -> 10000L             // Max 10s
                }
            }
        }

        val customDataSourceFactory = DataSource.Factory {
            val ds = RetryingHttpDataSource(dataSourceFactory.createDataSource()) { buffer, offset, length ->
                if (isRecording) {
                    try {
                        recordingOutputStream?.write(buffer, offset, length)
                    } catch (e: Exception) {
                        Log.e(TAG, "Kirjutamise viga salvestamisel: ${e.message}")
                        serviceScope.launch(Dispatchers.Main) {
                            stopRecording(failed = true)
                        }
                    }
                }
            }
            activeDataSource = ds
            ds
        }

        val defaultDataSourceFactory = androidx.media3.datasource.DefaultDataSource.Factory(this, customDataSourceFactory)

        val realPlayer = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(this)
                .setDataSourceFactory(defaultDataSourceFactory)
                .setLoadErrorHandlingPolicy(loadErrorHandlingPolicy)
            )
            .setTrackSelector(trackSelector)
            .setLoadControl(loadControl)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
            .setHandleAudioBecomingNoisy(true).build()

        realPlayer.addListener(playerListener)
        player = SkodaAwarePlayer(realPlayer, listeners)
        mediaSession = MediaSession.Builder(this, player).setSessionActivity(PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)).setCallback(mediaSessionCallback).build()
        LocalBroadcastManager.getInstance(this).registerReceiver(statusReceiver, IntentFilter(ACTION_GET_STATUS))
        val powerManager = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        wakeLock = powerManager.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "radiow::RadioWakeLock")
        wakeLock?.setReferenceCounted(false)
        
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        try {
            connectivityManager.registerDefaultNetworkCallback(networkCallback)
        } catch (e: Exception) {
            Log.e(TAG, "DefaultNetworkCallback registreerimise viga: ${e.message}")
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            connectivityManager.registerNetworkCallback(request, networkCallback)
        }

        restoreLastState()
    }
    
    private fun restoreLastState() {
        val lastId = prefs.getInt("last_selected_id", -1)
        val lastName = prefs.getString("last_name", "Radio") ?: "Radio"
        val lastUrl = prefs.getString("last_url", "") ?: ""
        val lastCat = prefs.getString("last_category", "") ?: ""
        
        val lastArtist = prefs.getString("last_artist", getString(R.string.live_broadcast)) ?: getString(R.string.live_broadcast)
        val lastTitle = prefs.getString("last_title", lastName) ?: lastName

        currentStationName = lastName
        currentStreamUrl = lastUrl
        currentCategory = lastCat
        currentArtist = lastArtist
        currentTitle = lastTitle
        currentStationBitmap = app.radiorecalarm.ui.StationArtworkUtils.generateDarkStationBitmap(currentStationName)

        if (currentStreamUrl.isNotEmpty()) {
            val initialMeta = metadataHelper.buildMediaMetadata(
                title = currentTitle,
                artist = currentArtist,
                stationName = currentStationName,
                artworkData = null
            )
            
            player.updateTrackMetadata(initialMeta)
            player.setMediaItem(
                MediaItem.Builder()
                    .setUri(currentStreamUrl)
                    .setMediaId("Raadio")
                    .setMediaMetadata(initialMeta)
                    .build()
            )
        }

        LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(
            Intent(ACTION_STATION_CHANGED).apply {
                putExtra("STATION_NAME", currentStationName)
                putExtra("CATEGORY_NAME", currentCategory)
            }
        )
        sendMetadataUpdate(currentTitle, currentArtist, "")
        
        // PARANDUS: Uuenda vidinat kohe pärast seisu taastamist
        updateWidget()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        wakeLock?.acquire(10 * 60 * 1000L)
        val action = intent?.action

        when(action) {
            ACTION_START_RECORDING -> {
                startRecording()
                return START_STICKY
            }
            ACTION_STOP_RECORDING -> {
                stopRecording()
                return START_STICKY
            }
            ACTION_PAUSE -> {
                player.pause()
                if (wakeLock?.isHeld == true) wakeLock?.release()
                metadataPushJob?.cancel()
                updateNotification()
                return START_STICKY
            }
            ACTION_RESUME -> {
                if (!player.isPlaying && currentStreamUrl.isNotEmpty()) {
                    val isLocal = currentStreamUrl.startsWith("file://") || currentStreamUrl.startsWith("file:/")
                    if (isLocal && player.playbackState != Player.STATE_IDLE) {
                        player.play()
                    } else {
                        playStation(currentStreamUrl, currentStationName, "USER_RESUME")
                    }
                }
                return START_STICKY
            }
            ACTION_STOP -> {
                stopRadio()
                return START_NOT_STICKY
            }
            ACTION_SKIP_NEXT -> { changeStation(1); return START_STICKY }
            ACTION_SKIP_PREVIOUS -> { changeStation(-1); return START_STICKY }
            ACTION_PLAY_PAUSE_TOGGLE -> {
                if (player.isPlaying) {
                    player.pause()
                    updateNotification()
                } else {
                    if (currentStreamUrl.isNotEmpty()) {
                        playStation(currentStreamUrl, currentStationName, "USER_RESUME")
                    }
                }
                return START_STICKY
            }
            ACTION_UPDATE_STATION_NAME -> {
                val newName = intent.getStringExtra("STATION_NAME")
                if (!newName.isNullOrEmpty() && newName != currentStationName) {
                    currentStationName = newName
                    currentStationBitmap = app.radiorecalarm.ui.StationArtworkUtils.generateDarkStationBitmap(currentStationName)
                    updateNotification()
                    updateExternalDevices(currentTitle, currentArtist)
                }
                return START_STICKY
            }
            ACTION_SET_TIMER -> {
                val duration = intent.getIntExtra(EXTRA_TIMER_DURATION, 0)
                startSleepTimer(duration)
                return START_STICKY
            }
            ACTION_FORCE_WIDGET_UPDATE -> {
                updateWidget()
                return START_STICKY
            }
            ACTION_SEEK -> {
                val positionMs = intent.getLongExtra(EXTRA_SEEK_POSITION, 0L)
                if (::player.isInitialized) {
                    player.seekTo(positionMs)
                }
                return START_STICKY
            }
        }

        val streamUrl = intent?.getStringExtra("STREAM_URL")
        if (streamUrl != null) {
            val stationName = intent.getStringExtra("STATION_NAME")
            val triggeredBy = intent.getStringExtra("TRIGGERED_BY")
            val category = intent.getStringExtra("CATEGORY_NAME")
            if (category != null) {
                currentCategory = category
                prefs.edit().putString("last_category", category).apply()
            }

            if (currentStreamUrl == streamUrl && player.isPlaying && triggeredBy != "USER_RESUME") {
                if (triggeredBy == "ALARM") {
                    isAlarmMode = true
                    val alarmNotification = notificationManager.createAlarmNotification(currentStationName)
                    notificationManager.notify(RadioNotificationManager.ALARM_NOTIFICATION_ID, alarmNotification)
                }
                updateNotification()
            } else {
                playStation(streamUrl, stationName, triggeredBy)
            }
        }
        return START_STICKY
    }

    private fun playStation(streamUrl: String, stationName: String?, triggeredBy: String?) {
        Log.d(TAG, "playStation: STARTING '$stationName' url='$streamUrl' triggeredBy='$triggeredBy'")

        stopRecording()
        hasSuccessfullyStartedPlaying = false
        consecutiveErrorCount = 0
        isChangingStation = true
        isInitialStationPlayback = true
        lastBitrateInfo = ""
        sendBitrateUpdate()
        wakeLock?.acquire(10 * 60 * 1000L)

        updateNotification()

        currentStreamUrl = streamUrl
        currentStationName = stationName ?: "Radio"
        isAlarmMode = triggeredBy == "ALARM"
        currentStationBitmap = app.radiorecalarm.ui.StationArtworkUtils.generateDarkStationBitmap(currentStationName)

        currentArtist = getString(R.string.live_broadcast)
        currentTitle = currentStationName
        currentExtra = ""
        
        lastSentTitle = ""
        lastSentArtist = ""
        lastSentTime = 0

        serviceScope.launch {
            val db = AppDatabase.getDatabase(applicationContext)
            val station = db.radioStationDao().getStationByName(currentStationName)
            if (station != null) {
                prefs.edit()
                    .putInt("last_selected_id", station.id)
                    .putString("last_category", currentCategory)
                    .putString("last_name", currentStationName)
                    .putString("last_url", currentStreamUrl)
                    .apply()
            } else {
                prefs.edit()
                    .putInt("last_selected_id", -1)
                    .putString("last_url", currentStreamUrl)
                    .putString("last_name", currentStationName)
                    .putString("last_category", currentCategory)
                    .apply()
            }
        }

        if (isAlarmMode) {
            val alarmNotification = notificationManager.createAlarmNotification(currentStationName)
            notificationManager.notify(RadioNotificationManager.ALARM_NOTIFICATION_ID, alarmNotification)
        }

        updateNotification()

        if (player.isPlaying) player.stop()

        val initialMeta = metadataHelper.buildMediaMetadata(
            title = currentTitle,
            artist = currentArtist,
            stationName = currentStationName,
            artworkData = null
        )

        Log.d(TAG, "playStation: Sending INITIAL metadata: Title='$currentTitle', Artist='$currentArtist', Album='$currentStationName'")

        player.setMediaItem(
            MediaItem.Builder()
                .setUri(streamUrl)
                .setMediaId("Raadio")
                .setMediaMetadata(initialMeta)
                .build()
        )

        player.updateTrackMetadata(initialMeta)
        player.streamStartTime = SystemClock.elapsedRealtime()

        player.prepare()
        player.play()
        updateExternalDevices(currentTitle, currentArtist)
    }

    private fun updateNotification() {
        val notification = notificationManager.buildNotification(mediaSession!!, currentStationName, currentTitle, currentArtist, currentStationBitmap, isAlarmMode, player.isPlaying)
        if (player.isPlaying || isAlarmMode || isChangingStation) {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            } else {
                startForeground(1, notification)
            }
        } else {
            stopForeground(false)
            notificationManager.notify(RadioNotificationManager.NOTIFICATION_ID, notification)
        }
    }

    private fun stopRadio(isError: Boolean = false) {
        if (wakeLock?.isHeld == true) wakeLock?.release()
        stopSleepTimer()
        stopRecording()
        stopProgressTracker()
        metadataPushJob?.cancel()
        pendingMetadataJob?.cancel()
        pendingMetadataJob = null
        bufferingWatchdogJob?.cancel()
        bufferingWatchdogJob = null
        player.playWhenReady = false
        player.stop()
        player.clearMediaItems()
        isAlarmMode = false
        notificationManager.cancel(RadioNotificationManager.ALARM_NOTIFICATION_ID)
        player.streamStartTime = 0L
        if (!isError) LocalBroadcastManager.getInstance(this).sendBroadcast(Intent(ACTION_PLAYER_STOPPED))
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startSleepTimer(minutes: Int) {
        stopSleepTimer()
        if (minutes <= 0) { sendTimerTick(0); return }
        sleepTimerRemainingMillis = minutes * 60 * 1000L
        sleepTimerJob = serviceScope.launch(Dispatchers.Main) {
            while (sleepTimerRemainingMillis > 0) {
                sendTimerTick(sleepTimerRemainingMillis)
                delay(1000)
                sleepTimerRemainingMillis -= 1000
            }
            sendTimerTick(0)
            player.pause()
            metadataPushJob?.cancel()
            updateNotification()
        }
    }

    private fun stopSleepTimer() { sleepTimerJob?.cancel(); sleepTimerJob = null; sleepTimerRemainingMillis = 0; sendTimerTick(0) }

    private fun sendTimerTick(remainingMillis: Long) {
        LocalBroadcastManager.getInstance(this).sendBroadcast(Intent(ACTION_TIMER_TICK).apply { putExtra("REMAINING_MILLIS", remainingMillis) })
    }

    private fun saveToHistory(artist: String, title: String) {
        if (artist.isBlank() && title.isBlank()) return
        serviceScope.launch {
            try {
                val db = AppDatabase.getDatabase(applicationContext)
                val historyDao = db.historyDao()
                val lastItem = historyDao.getLatestItem()
                if (lastItem != null && lastItem.artist == artist && lastItem.title == title) {
                    return@launch
                }
                historyDao.insert(app.radiorecalarm.HistoryItem(stationName = currentStationName, artist = artist, title = title, timestamp = System.currentTimeMillis()))
                historyDao.cleanOldHistory()
            } catch (e: Exception) {
                Log.e(TAG, "History viga: ${e.message}")
            }
        }
    }

    private fun updateWidget() {
        val bgTransparency = prefs.getFloat("widget_transparency", 0.25f)
        
        // PARANDUS: Kui player on null või initialize-imata, siis isPlaying = false
        val isPlaying = if (::player.isInitialized) player.isPlaying else false
        val stationName = currentStationName
        val title = currentTitle
        val artist = currentArtist
        val extra = currentExtra
        val currentBitrate = lastBitrateInfo

        serviceScope.launch {
            var nextAlarmString = ""
            try {
                val db = AppDatabase.getDatabase(applicationContext)
                val enabledAlarms = db.alarmDao().getAllEnabledAlarms()
                if (enabledAlarms.isNotEmpty()) {
                    val nextAlarm = enabledAlarms.map {
                        it to AlarmUtils.findNextAlarmTime(it.hour, it.minute, it.days)
                    }.minByOrNull { it.second }

                    if (nextAlarm != null) {
                        val timeAndDays = AlarmUtils.getAlarmText(this@RadioService, nextAlarm.first.hour, nextAlarm.first.minute, nextAlarm.first.days)
                        nextAlarmString = "$timeAndDays • ${nextAlarm.first.stationName}"
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Äratuse lugemise viga: ${e.message}")
            }

            try {
                val context = applicationContext
                val manager = GlanceAppWidgetManager(context)
                val widget = app.radiorecalarm.widget.HomeWidget()
                val glanceIds = manager.getGlanceIds(widget.javaClass)

                glanceIds.forEach { glanceId ->
                    updateAppWidgetState(context, glanceId) { prefs ->
                        prefs[app.radiorecalarm.widget.HomeWidget.Prefs.stationName] = stationName
                        prefs[app.radiorecalarm.widget.HomeWidget.Prefs.title] = title
                        prefs[app.radiorecalarm.widget.HomeWidget.Prefs.artist] = artist
                        prefs[app.radiorecalarm.widget.HomeWidget.Prefs.bitrate] = currentBitrate
                        val statusText = if (isPlaying) getString(R.string.status_playing) else getString(R.string.status_stopped)
                        val extraInfo = if (extra.isNotBlank()) "$extra • $statusText" else statusText
                        prefs[app.radiorecalarm.widget.HomeWidget.Prefs.status] = extraInfo
                        prefs[app.radiorecalarm.widget.HomeWidget.Prefs.alarm] = nextAlarmString
                        prefs[app.radiorecalarm.widget.HomeWidget.Prefs.isPlaying] = isPlaying
                        prefs[app.radiorecalarm.widget.HomeWidget.Prefs.bgTransparency] = bgTransparency
                    }
                    widget.update(context, glanceId)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Vidinat ei saanud uuendada: ${e.message}")
            }
        }
    }

    override fun onDestroy() {
        if (wakeLock?.isHeld == true) wakeLock?.release()
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        try { connectivityManager.unregisterNetworkCallback(networkCallback) } catch (e: Exception) {}
        LocalBroadcastManager.getInstance(this).unregisterReceiver(statusReceiver)
        stopRecording()
        activeDataSource = null
        player.release(); mediaSession?.release(); super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
}

@androidx.annotation.OptIn(UnstableApi::class)
private class RetryingHttpDataSource(
    private val delegate: HttpDataSource,
    private val onBytesRead: (ByteArray, Int, Int) -> Unit
) : HttpDataSource by delegate {

    private var metaInt: Int = -1
    private var bytesUntilMeta = -1
    private var metaLengthBytesRemaining = 0

    fun invalidateConnection() {
        Log.w("RetryingDataSource", "Tühistan aktiivse ühenduse sokli sulgemisega...")
        try {
            delegate.close()
        } catch (e: Exception) {
            // ignore
        }
    }

    override fun open(dataSpec: DataSpec): Long {
        val result = delegate.open(dataSpec)
        val headers = delegate.responseHeaders
        var metaIntStr: String? = null
        for ((key, value) in headers) {
            if (key.equals("icy-metaint", ignoreCase = true)) {
                metaIntStr = value.firstOrNull()
                break
            }
        }
        metaInt = metaIntStr?.toIntOrNull() ?: -1
        bytesUntilMeta = metaInt
        metaLengthBytesRemaining = 0
        Log.i("RetryingDataSource", "Ühendus avatud. icy-metaint: $metaInt")
        return result
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val result = delegate.read(buffer, offset, length)
        if (result > 0) {
            feedStrippedBytes(buffer, offset, result)
        }
        return result
    }

    private fun feedStrippedBytes(buffer: ByteArray, offset: Int, length: Int) {
        if (metaInt <= 0) {
            onBytesRead(buffer, offset, length)
            return
        }

        var curr = offset
        val end = offset + length

        while (curr < end) {
            if (metaLengthBytesRemaining > 0) {
                val chunk = Math.min(end - curr, metaLengthBytesRemaining)
                curr += chunk
                metaLengthBytesRemaining -= chunk
            } else if (bytesUntilMeta == 0) {
                val lengthByte = buffer[curr].toInt() and 0xFF
                curr++
                val metaLength = lengthByte * 16
                if (metaLength > 0) {
                    metaLengthBytesRemaining = metaLength
                }
                bytesUntilMeta = metaInt
            } else {
                val chunk = Math.min(end - curr, bytesUntilMeta)
                onBytesRead(buffer, curr, chunk)
                curr += chunk
                bytesUntilMeta -= chunk
            }
        }
    }
}
