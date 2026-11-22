package app.radiorecalarm

import android.app.Service
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.media.AudioAttributes as PlatformAudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import app.radiorecalarm.RecordingInterruptionPolicy.InterruptionAction
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
import kotlinx.coroutines.flow.lastOrNull
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
    private var cachedStations: List<RadioStation> = emptyList()

    private var currentArtist: String = ""
    private var currentTitle: String = ""
    private var currentExtra: String = ""

    private var lastSentArtist: String = ""
    private var lastSentTitle: String = ""
    private var lastSentStationName: String = ""
    private var lastSentTime: Long = 0

    private var isAlarmMode: Boolean = false
    private var currentStreamUrl: String = ""

    private var metadataPushJob: kotlinx.coroutines.Job? = null
    private var pendingMetadataJob: kotlinx.coroutines.Job? = null
    private var bufferingWatchdogJob: kotlinx.coroutines.Job? = null
    private var idleTimeoutJob: kotlinx.coroutines.Job? = null
    private var activeDataSource: RetryingHttpDataSource? = null

    private var lastDefaultNetwork: android.net.Network? = null
    private var consecutiveErrorCount = 0
    private var lastErrorTime = 0L
    private var hasSuccessfullyStartedPlaying = false

    private var lastFocusGainTime: Long = 0L
    private val interruptionPolicy = RecordingInterruptionPolicy()
    private lateinit var audioManager: AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null

    private val recordingCoordinator by lazy {
        StreamRecordingCoordinator(
            baseDirProvider = {
                java.io.File(getExternalFilesDir(null), AppConfig.Recording.DEFAULT_AUDIO_DIR).apply { if (!exists()) mkdirs() }
            },
            metadataHelper = metadataHelper,
            minDurationSecondsProvider = {
                prefs.getLong(
                    AppConfig.Recording.PREF_MIN_DURATION_SECONDS,
                    AppConfig.Recording.MIN_DURATION_SECONDS
                )
            },
            overlapMarginSecondsProvider = {
                prefs.getLong(
                    AppConfig.Recording.PREF_OVERLAP_MARGIN_SECONDS,
                    AppConfig.Recording.OVERLAP_MARGIN_SECONDS
                )
            },
            metadataFetcher = { artist, title ->
                MusicInfoRepository.fetchInfo(artist, title).lastOrNull()
            },
            coverArtDownloader = { urlStr, targetFile ->
                try {
                    val url = java.net.URL(urlStr)
                    url.openStream().use { input ->
                        java.io.FileOutputStream(targetFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    true
                } catch (e: Exception) {
                    false
                }
            },
            onLowStorage = {
                serviceScope.launch(Dispatchers.Main) {
                    applyInterruptionAction(interruptionPolicy.onRecordingStopped())
                    notificationManager.showLowStorageNotification()
                    Toast.makeText(applicationContext, getString(R.string.recording_low_storage_warning), Toast.LENGTH_LONG).show()
                    sendRecordingStatus()
                    updateNotification()
                }
            }
        )
    }
    private val isRecording: Boolean get() = recordingCoordinator.isRecording
    private var recordingStartMillis = 0L
    private var recordingTimerJob: kotlinx.coroutines.Job? = null
    @Volatile private var pendingAutoRecord: Boolean = false
    @Volatile private var unsplitShowModeForCurrentRecording: Boolean = false
    @Volatile private var userManuallyStoppedRecordingForCurrentStation: Boolean = false
    @Volatile private var autoRecordArmedForStreamUrl: String? = null
    @Volatile private var latestWireStreamTitle: String? = null
    @Volatile private var latestWireIsAac: Boolean = false
    @Volatile private var latestWireMetaInt: Int = -1
    private val recordingControlLock = Any()
    private fun isContinuousRecordingEnabled(): Boolean = prefs.getBoolean(AppConfig.Recording.PREF_CONTINUOUS_RECORDING, false)
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
        @Volatile
        var isCurrentlyPlaying: Boolean = false
            private set

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
        const val EXTRA_RECORD = "app.radiorecalarm.EXTRA_RECORD"
        const val EXTRA_TRACK_TITLE = "TRACK_TITLE"
        const val EXTRA_TRACK_ARTIST = "TRACK_ARTIST"

    }

    private val mediaSessionCallback = object : MediaSession.Callback {
        override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
            Log.d(TAG, "onConnect: Kontroller ühendus (${controller.packageName})")
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session).build()
        }

        override fun onPlayerCommandRequest(session: MediaSession, controller: MediaSession.ControllerInfo, playerCommand: Int): Int {
            when (playerCommand) {
                Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> {
                    changeStation(1)
                    return SessionResult.RESULT_INFO_SKIPPED
                }
                Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> {
                    changeStation(-1)
                    return SessionResult.RESULT_INFO_SKIPPED
                }
                Player.COMMAND_PLAY_PAUSE -> {
                    val now = SystemClock.elapsedRealtime()
                    if (now - lastFocusGainTime < 1500L) {
                        Log.i(TAG, "Ignoreerime auto automaatset PLAY_PAUSE käsku vahetult pärast kõne lõppu (race condition kaitse).")
                        return SessionResult.RESULT_INFO_SKIPPED
                    }
                    if (unmuteFromUserAction()) {
                        return SessionResult.RESULT_INFO_SKIPPED
                    }
                    if (player.isPlaying) {
                        interruptionPolicy.onUserPauseOrStop()
                        player.volume = 1.0f
                        abandonAudioFocus()
                        player.pause()
                    } else {
                        if (currentStreamUrl.isNotEmpty()) {
                            playStation(currentStreamUrl, currentStationName, "USER_RESUME")
                        }
                    }
                    return SessionResult.RESULT_INFO_SKIPPED
                }
            }
            return super.onPlayerCommandRequest(session, controller, playerCommand)
        }
    }

    private fun changeStation(offset: Int) {
        isChangingStation = true
        
        currentCategory = prefs.getString("last_category", currentCategory) ?: currentCategory
        val hideCuratedStations = prefs.getBoolean("hide_remote_stations", false)
        val selectedSubCategories = prefs.getStringSet("last_sub_categories", emptySet()) ?: emptySet()
        
        val allStations = if (cachedStations.isNotEmpty()) {
            cachedStations
        } else {
            val db = AppDatabase.getDatabase(applicationContext)
            kotlinx.coroutines.runBlocking(Dispatchers.IO) {
                try {
                    db.radioStationDao().getAllActiveStationsSync().also { cachedStations = it }
                } catch (e: Exception) {
                    emptyList()
                }
            }
        }
        val visibleStations = StationListFilter.visibleStations(allStations, hideCuratedStations)
        if (visibleStations.isEmpty()) return

        val navigationList = StationListFilter.filterAndSortStations(
            stations = visibleStations,
            category = currentCategory,
            subCategories = selectedSubCategories
        )

        val finalNavList = if (navigationList.isEmpty() || navigationList.none { it.name == currentStationName }) {
            visibleStations
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

        isAlarmMode = false
        playStation(nextStation.url, nextStation.name, "USER")
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
        // Kui striim ei kasuta ICY-metaint päist (metaInt <= 0), kasutame varuteena Player.Listener sündmust.
        if (recordingCoordinator.isRecording && latestWireMetaInt <= 0) {
            val raw = trackTitleFromStream ?: ""
            serviceScope.launch(Dispatchers.IO) {
                recordingCoordinator.onMetadataChanged(raw)
            }
        }
        updateNotification()
        updateWidget()
    }

    private fun sendBitrateUpdate() {
        recordingCoordinator.setBitrate(lastBitrateInfo)
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
            val targetStation = currentStationName
            pendingMetadataJob = sessionScope.launch {
                delay(500 - timeSinceLast)
                if (targetStation == currentStationName) {
                    updateExternalDevices(title, artist)
                }
            }
            return
        }

        if (title == lastSentTitle && artist == lastSentArtist && currentStationName == lastSentStationName) {
            return
        }

        pendingMetadataJob?.cancel()
        pendingMetadataJob = null

        Log.d(TAG, "updateExternalDevices: SENDING TO CAR -> Title='$title', Artist='$artist', Album='$currentStationName'")

        lastSentTitle = title
        lastSentArtist = artist
        lastSentStationName = currentStationName
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
                if (PlaybackStatusSync.isLocalStreamUri(currentStreamUrl)) {
                    broadcastPlaybackProgress(player.currentPosition, player.duration.coerceAtLeast(0L))
                }
            }
            
            // LAHENDUS: Kui striim saab otsa (server paneb toru ära), proovi kiiresti uuesti valmistada (seek + prepare)
            if (playbackState == Player.STATE_ENDED && player.playWhenReady) {
                val isLocal = PlaybackStatusSync.isLocalStreamUri(currentStreamUrl)
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
            isCurrentlyPlaying = isPlaying
            if (isPlaying && wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
            updateNotification()
            updateWidget()

            if (isPlaying) {
                val isLocal = PlaybackStatusSync.isLocalStreamUri(currentStreamUrl)
                if (!isLocal && (pendingAutoRecord || isContinuousRecordingEnabled()) && !userManuallyStoppedRecordingForCurrentStation && !isRecording) {
                    startRecording(forceUnsplitShow = unsplitShowModeForCurrentRecording)
                }
                isChangingStation = false
                if (!isLocal) {
                    saveToHistory(currentArtist, currentTitle)
                }
                sendStationChangedBroadcast(isLocal = isLocal, isPlaying = true)
                sendMetadataUpdate(currentTitle, currentArtist, currentExtra)
                
                if (isInitialStationPlayback) {
                    isInitialStationPlayback = false
                    lastSentTitle = ""
                    lastSentArtist = ""
                    lastSentTime = 0
                    player.streamStartTime = SystemClock.elapsedRealtime()
                    updateExternalDevices(currentTitle, currentArtist)

                } else {
                    // Striimi taastumine võrgukatkestusest või lühiajalisest puhverdamisest:
                    // Uuendame autot ainult siis, kui laul on vahepeal päriselt muutunud!
                    updateExternalDevices(currentTitle, currentArtist)
                }

                startProgressTracker()
            } else {
                val isLocal = PlaybackStatusSync.isLocalStreamUri(currentStreamUrl)
                val isPausedLocal = isLocal && !isChangingStation &&
                    player.playbackState != Player.STATE_IDLE &&
                    player.playbackState != Player.STATE_ENDED
                if (!player.playWhenReady && !isChangingStation && !interruptionPolicy.wasSuppressedByTransientLoss) {
                    val action = if (isLocal) ACTION_PLAYER_PAUSED else ACTION_PLAYER_STOPPED
                    LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(Intent(action))
                    autoRecordArmedForStreamUrl = null
                    unsplitShowModeForCurrentRecording = false
                    stopRecording()
                    pendingAutoRecord = false
                }
                metadataPushJob?.cancel()
                pendingMetadataJob?.cancel()
                pendingMetadataJob = null
                stopProgressTracker(resetToZero = !isPausedLocal)
                if (isPausedLocal) {
                    broadcastPlaybackProgress(player.currentPosition, player.duration.coerceAtLeast(0L))
                }
            }
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (playWhenReady) {
                cancelIdleTimeout()
                val notification = notificationManager.buildNotification(
                    mediaSession = mediaSession!!,
                    stationName = currentStationName,
                    trackTitle = currentTitle,
                    trackArtist = currentArtist,
                    bitmap = currentStationBitmap,
                    isAlarmMode = isAlarmMode,
                    isPlaying = player.isPlaying,
                    isRecording = isRecording,
                    isMutedRecording = interruptionPolicy.isMutedRecording
                )
                if (Build.VERSION.SDK_INT >= 34) {
                    startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
                } else {
                    startForeground(1, notification)
                }
            } else {
                if (wakeLock?.isHeld == true) wakeLock?.release()
                updateNotification()
                if (!interruptionPolicy.wasSuppressedByTransientLoss) {
                    startIdleTimeout()
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

            // Kui viga tekkis kõne / ajutise fookusekaotuse ajal, ei kasvatata vealoendurit ega peata raadiot
            if (interruptionPolicy.wasSuppressedByTransientLoss) {
                Log.w(TAG, "Viga tekkis kõne / fookusekaotuse ajal. Ootame kõne lõppu ja taastumist.")
                return
            }

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

            val isCodecError = error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_DECODING_FAILED ||
                    cause is androidx.media3.exoplayer.mediacodec.MediaCodecDecoderException

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
                        if (isCodecError) {
                            Log.w(TAG, "Koodeki viga tuvastatud: lähtestame mängija meediaüksuse uue koodeki instantsi loomiseks.")
                            player.stop()
                            val initialMeta = metadataHelper.buildMediaMetadata(
                                title = currentTitle,
                                artist = currentArtist,
                                stationName = currentStationName,
                                artworkData = null
                            )
                            val uniqueMediaId = initialMeta.extras?.getString("android.media.metadata.MEDIA_ID") ?: "station_recovery"
                            player.setMediaItem(
                                MediaItem.Builder()
                                    .setUri(currentStreamUrl)
                                    .setMediaId(uniqueMediaId)
                                    .setMediaMetadata(initialMeta)
                                    .build()
                            )
                        } else {
                            player.seekToDefaultPosition()
                        }
                        player.prepare()
                    }
                }
            }
        }
    }

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val isPlayerReady = ::player.isInitialized
            val hasLoadedMedia = isPlayerReady &&
                player.playbackState != Player.STATE_IDLE &&
                player.playbackState != Player.STATE_ENDED
            val snapshot = PlaybackStatusSync.resolveServiceStatus(
                stationName = currentStationName,
                streamUrl = currentStreamUrl,
                isPlayerInitialized = isPlayerReady,
                isPlaying = isPlayerReady && player.isPlaying,
                hasLoadedMedia = hasLoadedMedia
            )

            if (snapshot.sendStationChanged) {
                sendStationChangedBroadcast(isLocal = snapshot.isLocal, isPlaying = snapshot.isPlaying)
            }

            if (currentTitle.isNotEmpty() || currentArtist.isNotEmpty()) {
                sendMetadataUpdate(currentTitle, currentArtist, currentExtra)
            }

            if (snapshot.sendBitrateAndTimer) {
                sendBitrateUpdate()
                sendTimerTick(sleepTimerRemainingMillis)
            }

            when (snapshot.terminalAction) {
                PlaybackStatusAction.NONE -> Unit
                PlaybackStatusAction.PAUSED -> {
                    LocalBroadcastManager.getInstance(this@RadioService).sendBroadcast(Intent(ACTION_PLAYER_PAUSED))
                }
                PlaybackStatusAction.STOPPED -> {
                    LocalBroadcastManager.getInstance(this@RadioService).sendBroadcast(Intent(ACTION_PLAYER_STOPPED))
                }
            }

            if (snapshot.sendPlaybackProgress && isPlayerReady) {
                broadcastPlaybackProgress(player.currentPosition, player.duration.coerceAtLeast(0L))
            }

            sendRecordingStatus()
        }
    }

    private val audioFocusListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        val action = interruptionPolicy.onAudioFocusChange(focusChange, isRecording)
        applyInterruptionAction(action)
    }

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                Log.w(TAG, "AUDIO_BECOMING_NOISY tuvastatud (isRecording=$isRecording)")
                val action = interruptionPolicy.onAudioBecomingNoisy(isRecording)
                applyInterruptionAction(action)
            }
        }
    }

    private fun requestAudioFocus() {
        if (!::audioManager.isInitialized) return
        val req = audioFocusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                PlatformAudioAttributes.Builder()
                    .setUsage(PlatformAudioAttributes.USAGE_MEDIA)
                    .setContentType(PlatformAudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setOnAudioFocusChangeListener(audioFocusListener, Handler(Looper.getMainLooper()))
            .build()
            .also { audioFocusRequest = it }
        audioManager.requestAudioFocus(req)
    }

    private fun abandonAudioFocus() {
        if (!::audioManager.isInitialized) return
        audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
    }

    private fun applyInterruptionAction(action: InterruptionAction) {
        if (!::player.isInitialized) return
        when (action) {
            InterruptionAction.None -> Unit
            is InterruptionAction.SetVolume -> {
                player.volume = action.volume
                sendRecordingStatus()
                updateNotification()
            }
            is InterruptionAction.PausePlayback -> {
                if (action.invalidateSocket) {
                    activeDataSource?.invalidateConnection()
                }
                player.volume = 1.0f
                player.pause()
                if (wakeLock?.isHeld == true) wakeLock?.release()
                sendRecordingStatus()
                updateNotification()
            }
            InterruptionAction.SuppressForCall -> {
                activeDataSource?.invalidateConnection()
                bufferingWatchdogJob?.cancel()
                bufferingWatchdogJob = null
                cancelIdleTimeout()
                player.playWhenReady = false
                updateNotification()
            }
            is InterruptionAction.ResumeAfterCall -> {
                cancelIdleTimeout()
                lastFocusGainTime = SystemClock.elapsedRealtime()
                consecutiveErrorCount = 0
                serviceScope.launch(Dispatchers.Main) {
                    delay(300)
                    if (::player.isInitialized && currentStreamUrl.isNotEmpty() && !interruptionPolicy.wasSuppressedByTransientLoss) {
                        if (action.restorePlayWhenReady || !player.playWhenReady) {
                            player.playWhenReady = true
                        }
                        requestAudioFocus()
                        retryPlaybackWithoutMetadataWipe("CALL_ENDED_FOCUS_RESTORED")
                        updateExternalDevices(currentTitle, currentArtist)
                    }
                }
            }
            InterruptionAction.UnmuteAfterCall -> {
                lastFocusGainTime = SystemClock.elapsedRealtime()
                serviceScope.launch(Dispatchers.Main) {
                    delay(300)
                    if (::player.isInitialized && !interruptionPolicy.isMutedRecording) {
                        player.volume = 1.0f
                        requestAudioFocus()
                        sendRecordingStatus()
                        updateNotification()
                    }
                }
            }
        }
    }

    private fun unmuteFromUserAction(): Boolean {
        if (!interruptionPolicy.isMutedRecording) return false
        applyInterruptionAction(interruptionPolicy.onUserUnmuteOrResume())
        requestAudioFocus()
        if (!player.playWhenReady && currentStreamUrl.isNotEmpty()) {
            player.playWhenReady = true
            player.play()
        }
        return true
    }

    private fun sendRecordingStatus() {
        val intent = Intent(ACTION_RECORDING_STATUS).apply {
            putExtra("IS_RECORDING", isRecording)
            putExtra("IS_MUTED_RECORDING", isRecording && interruptionPolicy.isMutedRecording)
            putExtra("RECORDING_DURATION", if (isRecording) android.os.SystemClock.elapsedRealtime() - recordingStartMillis else 0L)
            putExtra("RECORDING_STATION", currentStationName)
            putExtra("RECORDING_TRACK", recordingCoordinator.currentTitle)
            putExtra("RECORDING_ARTIST", recordingCoordinator.currentArtist)
            putExtra("RECORDING_COUNT", recordingCoordinator.completedTrackCount)
            putExtra("RECORDING_ACTIVE_FILE", recordingCoordinator.activeRecordingFilePath ?: "")
        }
        LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(intent)
    }

    private fun sendStationChangedBroadcast(isLocal: Boolean, isPlaying: Boolean) {
        LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(
            Intent(ACTION_STATION_CHANGED).apply {
                putExtra("STATION_NAME", currentStationName)
                putExtra("CATEGORY_NAME", currentCategory)
                putExtra("IS_LOCAL", isLocal)
                if (isLocal) {
                    putExtra("STREAM_URL", currentStreamUrl)
                    putExtra("IS_PLAYING", isPlaying)
                }
            }
        )
    }

    private fun startProgressTracker() {
        stopProgressTracker(resetToZero = false)
        if (PlaybackStatusSync.isLocalStreamUri(currentStreamUrl)) {
            progressJob = sessionScope.launch {
                while (player.isPlaying) {
                    val pos = player.currentPosition
                    val dur = player.duration.coerceAtLeast(0L)
                    broadcastPlaybackProgress(pos, dur)
                    delay(250)
                }
            }
        }
    }

    private fun stopProgressTracker(resetToZero: Boolean = true) {
        progressJob?.cancel()
        progressJob = null
        if (resetToZero) {
            broadcastPlaybackProgress(0L, 0L)
        }
    }

    private fun broadcastPlaybackProgress(position: Long, duration: Long) {
        val intent = Intent(ACTION_PLAYBACK_PROGRESS).apply {
            putExtra("PLAYBACK_POSITION", position)
            putExtra("PLAYBACK_DURATION", duration)
        }
        LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(intent)
    }

    private fun maybeStartAutoRecordingFromStream(openedUrl: String) {
        if (isRecording) return
        if (autoRecordArmedForStreamUrl != openedUrl) return
        if (userManuallyStoppedRecordingForCurrentStation) return
        if (!pendingAutoRecord && !isContinuousRecordingEnabled()) return

        startRecording(
            fromStreamThread = true,
            streamUrlOverride = openedUrl,
            forceUnsplitShow = unsplitShowModeForCurrentRecording
        )
    }

    private fun startRecording(
        fromStreamThread: Boolean = false,
        streamUrlOverride: String? = null,
        forceUnsplitShow: Boolean = false
    ) {
        val targetUrl = streamUrlOverride ?: currentStreamUrl
        if (targetUrl.isEmpty() || targetUrl.startsWith("file:")) {
            return
        }
        if (!fromStreamThread && (!::player.isInitialized || !player.isPlaying)) {
            return
        }

        val started = synchronized(recordingControlLock) {
            if (isRecording) return@synchronized false

            try {
                val fallbackMeta = if (currentArtist.isNotEmpty() && currentTitle.isNotEmpty()) {
                    "$currentArtist - $currentTitle"
                } else {
                    currentStationName
                }
                val initialMeta = latestWireStreamTitle ?: fallbackMeta
                val isAacHint = latestWireIsAac || targetUrl.contains("aac", ignoreCase = true)

                recordingCoordinator.startRecording(
                    stationName = currentStationName,
                    streamUrl = targetUrl,
                    initialRawMetadata = initialMeta,
                    bitrate = lastBitrateInfo.ifBlank { null },
                    isAacHint = isAacHint,
                    forceUnsplitShow = forceUnsplitShow
                )

                if (!isRecording) {
                    Log.w(TAG, "startRecording: Salvestamist ei alustatud (nt vaba kettamahu puudumise tõttu).")
                    sessionScope.launch {
                        sendRecordingStatus()
                        updateNotification()
                    }
                    return@synchronized false
                }

                recordingStartMillis = android.os.SystemClock.elapsedRealtime()
                Log.i(TAG, "Alustati salvestamist: jaam=$currentStationName (fromStreamThread=$fromStreamThread)")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Salvestamise alustamise viga: ${e.message}", e)
                sessionScope.launch {
                    Toast.makeText(applicationContext, getString(R.string.recording_failed), Toast.LENGTH_SHORT).show()
                }
                stopRecording(failed = true)
                false
            }
        }

        if (!started) return

        sessionScope.launch {
            if (!isRecording) return@launch
            sendRecordingStatus()
            updateNotification()

            recordingTimerJob?.cancel()
            recordingTimerJob = serviceScope.launch(Dispatchers.Main) {
                var lastSafeguardCheck = android.os.SystemClock.elapsedRealtime()
                while (isRecording) {
                    delay(1000)
                    sendRecordingStatus()
                    val now = android.os.SystemClock.elapsedRealtime()
                    if (now - lastSafeguardCheck >= 30000L) {
                        lastSafeguardCheck = now
                        withContext(Dispatchers.IO) {
                            recordingCoordinator.checkStorageSafeguard()
                        }
                    }
                }
            }
        }
    }

    private fun stopRecording(failed: Boolean = false) {
        synchronized(recordingControlLock) {
            if (!isRecording) return

            recordingTimerJob?.cancel()
            recordingTimerJob = null

            recordingCoordinator.stopRecording { finalCount ->
                Log.i(TAG, "Salvestamine peatatud. Kokku salvestatud $finalCount pala.")
                sessionScope.launch {
                    sendRecordingStatus()
                    if (!failed && finalCount > 0) {
                        val tracksStr = getString(R.string.recording_tracks_saved, finalCount)
                        Toast.makeText(applicationContext, getString(R.string.recording_toast_saved, tracksStr), Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

        val postStopAction = interruptionPolicy.onRecordingStopped()
        val refreshStoppedUi = {
            if (postStopAction !is InterruptionAction.None) {
                applyInterruptionAction(postStopAction)
                abandonAudioFocus()
            }
            sendRecordingStatus()
            updateNotification()
        }
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            refreshStoppedUi()
        } else {
            sessionScope.launch { refreshStoppedUi() }
        }
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
            val ds = RetryingHttpDataSource(
                delegate = dataSourceFactory.createDataSource(),
                initialTitleProvider = { latestWireStreamTitle },
                shouldAlignToFrame = { isRecording && !recordingCoordinator.isUnsplitShowMode },
                onStreamOpened = { metaInt, isAac, icyBr ->
                    latestWireMetaInt = metaInt
                    latestWireIsAac = isAac
                    if (isRecording) {
                        recordingCoordinator.resetFrameAlignment()
                    }
                    if (!icyBr.isNullOrBlank()) {
                        lastBitrateInfo = "$icyBr kbps"
                        recordingCoordinator.setBitrate(lastBitrateInfo)
                    }
                },
                onMetadataBoundary = { title ->
                    latestWireStreamTitle = title
                    if (isRecording) {
                        recordingCoordinator.onMetadataChanged(title)
                    }
                },
                onBytesRead = { openedUrl, buffer, offset, length ->
                    maybeStartAutoRecordingFromStream(openedUrl)
                    if (isRecording) {
                        try {
                            recordingCoordinator.onStreamBytes(buffer, offset, length)
                        } catch (e: Exception) {
                            Log.e(TAG, "Kirjutamise viga salvestamisel: ${e.message}")
                            serviceScope.launch(Dispatchers.Main) {
                                stopRecording(failed = true)
                            }
                        }
                    }
                }
            )
            activeDataSource = ds
            ds
        }

        val defaultDataSourceFactory = androidx.media3.datasource.DefaultDataSource.Factory(this, customDataSourceFactory)
        val extractorsFactory = PlaybackStatusSync.createExtractorsFactory()

        val realPlayer = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(defaultDataSourceFactory, extractorsFactory)
                .setLoadErrorHandlingPolicy(loadErrorHandlingPolicy)
            )
            .setTrackSelector(trackSelector)
            .setLoadControl(loadControl)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), false)
            .setHandleAudioBecomingNoisy(false).build()

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        ContextCompat.registerReceiver(
            this,
            noisyReceiver,
            IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        realPlayer.addListener(playerListener)
        player = SkodaAwarePlayer(realPlayer, listeners).apply {
            onSkipNext = { changeStation(1) }
            onSkipPrevious = { changeStation(-1) }
        }
        mediaSession = MediaSession.Builder(this, player).setSessionActivity(PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)).setCallback(mediaSessionCallback).build()
        LocalBroadcastManager.getInstance(this).registerReceiver(statusReceiver, IntentFilter(ACTION_GET_STATUS))
        val powerManager = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        wakeLock = powerManager.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "kellraadio::RadioWakeLock")
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

        serviceScope.launch {
            try {
                val db = AppDatabase.getDatabase(applicationContext)
                db.radioStationDao().getAllActiveStations().collect { stations ->
                    cachedStations = stations
                }
            } catch (e: Exception) {
                Log.e(TAG, "Viga jaamade vahemälu laadimisel: ${e.message}")
            }
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
            val uniqueMediaId = initialMeta.extras?.getString("android.media.metadata.MEDIA_ID") ?: "station_initial"
            player.setMediaItem(
                MediaItem.Builder()
                    .setUri(currentStreamUrl)
                    .setMediaId(uniqueMediaId)
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
        if (intent?.getStringExtra("TRIGGERED_BY") == "ALARM") {
            // Äratuse käivitumisel hoiame CPU-d ärkvel kuni helivoo avanemiseni (kuni 30s)
            wakeLock?.acquire(AppConfig.Alarm.WAKELOCK_TIMEOUT_MS)
        }
        val action = intent?.action

        when(action) {
            ACTION_START_RECORDING -> {
                userManuallyStoppedRecordingForCurrentStation = false
                startRecording()
                return START_STICKY
            }
            ACTION_STOP_RECORDING -> {
                userManuallyStoppedRecordingForCurrentStation = true
                pendingAutoRecord = false
                autoRecordArmedForStreamUrl = null
                stopRecording()
                return START_STICKY
            }
            ACTION_PAUSE -> {
                autoRecordArmedForStreamUrl = null
                unsplitShowModeForCurrentRecording = false
                interruptionPolicy.onUserPauseOrStop()
                player.volume = 1.0f
                abandonAudioFocus()
                player.pause()
                if (wakeLock?.isHeld == true) wakeLock?.release()
                metadataPushJob?.cancel()
                if (isAlarmMode) {
                    isAlarmMode = false
                    notificationManager.cancel(RadioNotificationManager.ALARM_NOTIFICATION_ID)
                }
                updateNotification()
                return START_STICKY
            }
            ACTION_RESUME -> {
                if (unmuteFromUserAction()) {
                    return START_STICKY
                }
                if (!player.isPlaying && currentStreamUrl.isNotEmpty()) {
                    val isLocal = PlaybackStatusSync.isLocalStreamUri(currentStreamUrl)
                    if (isLocal && player.playbackState != Player.STATE_IDLE) {
                        requestAudioFocus()
                        player.volume = 1.0f
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
                val now = SystemClock.elapsedRealtime()
                if (now - lastFocusGainTime < 1500L) {
                    Log.i(TAG, "Ignoreerime ACTION_PLAY_PAUSE_TOGGLE vahetult pärast kõne lõppu.")
                    return START_STICKY
                }
                if (unmuteFromUserAction()) {
                    return START_STICKY
                }
                if (player.isPlaying) {
                    interruptionPolicy.onUserPauseOrStop()
                    player.volume = 1.0f
                    abandonAudioFocus()
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
                val newUrl = intent.getStringExtra("STREAM_URL")
                var changed = false
                if (!newName.isNullOrEmpty() && newName != currentStationName) {
                    currentStationName = newName
                    currentStationBitmap = app.radiorecalarm.ui.StationArtworkUtils.generateDarkStationBitmap(currentStationName)
                    prefs.edit().putString("last_name", newName).apply()
                    changed = true
                }
                if (!newUrl.isNullOrEmpty() && newUrl != currentStreamUrl) {
                    currentStreamUrl = newUrl
                    prefs.edit().putString("last_url", newUrl).apply()
                    changed = true
                }
                if (changed) {
                    updateNotification()
                    updateExternalDevices(currentTitle, currentArtist)
                    updateWidget()
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
                if (::player.isInitialized && !player.isPlaying && !player.playWhenReady) {
                    stopRadio()
                }
                return START_NOT_STICKY
            }
            ACTION_SEEK -> {
                val positionMs = intent.getLongExtra(EXTRA_SEEK_POSITION, 0L)
                if (::player.isInitialized && PlaybackStatusSync.isLocalStreamUri(currentStreamUrl)) {
                    val dur = player.duration.coerceAtLeast(0L)
                    val clampedPositionMs = PlaybackStatusSync.clampSeekPosition(positionMs, dur)
                    player.seekTo(clampedPositionMs)
                    broadcastPlaybackProgress(player.currentPosition, dur)
                }
                return START_STICKY
            }
        }

        val streamUrl = intent?.getStringExtra("STREAM_URL")
        if (streamUrl != null) {
            val stationName = intent.getStringExtra("STATION_NAME")
            val triggeredBy = intent.getStringExtra("TRIGGERED_BY")
            val category = intent.getStringExtra("CATEGORY_NAME")
            val extraRecord = intent.getBooleanExtra(EXTRA_RECORD, false)
            val trackTitle = intent.getStringExtra(EXTRA_TRACK_TITLE)
            val trackArtist = intent.getStringExtra(EXTRA_TRACK_ARTIST)
            if (category != null) {
                currentCategory = category
                prefs.edit().putString("last_category", category).apply()
            }

            if (currentStreamUrl == streamUrl && player.isPlaying && triggeredBy != "USER_RESUME" && triggeredBy != "ALARM") {
                if (isAlarmMode) {
                    isAlarmMode = false
                    notificationManager.cancel(RadioNotificationManager.ALARM_NOTIFICATION_ID)
                }
                updateNotification()
                if (PlaybackStatusSync.isLocalStreamUri(streamUrl)) {
                    sendStationChangedBroadcast(isLocal = true, isPlaying = true)
                    broadcastPlaybackProgress(player.currentPosition, player.duration.coerceAtLeast(0L))
                }
            } else {
                playStation(
                    streamUrl = streamUrl,
                    stationName = stationName,
                    triggeredBy = triggeredBy,
                    shouldRecord = extraRecord,
                    trackTitleOverride = trackTitle,
                    trackArtistOverride = trackArtist
                )
            }
        }
        return START_STICKY
    }

    private fun playStation(
        streamUrl: String,
        stationName: String?,
        triggeredBy: String?,
        shouldRecord: Boolean = false,
        trackTitleOverride: String? = null,
        trackArtistOverride: String? = null
    ) {
        Log.d(TAG, "playStation: STARTING '$stationName' url='$streamUrl' triggeredBy='$triggeredBy' shouldRecord=$shouldRecord")

        pendingMetadataJob?.cancel()
        pendingMetadataJob = null
        metadataPushJob?.cancel()
        metadataPushJob = null

        autoRecordArmedForStreamUrl = null
        latestWireStreamTitle = null
        latestWireMetaInt = -1
        latestWireIsAac = false

        val isLocal = PlaybackStatusSync.isLocalStreamUri(streamUrl)
        val wasRecordingBeforeSwitch = isRecording
        interruptionPolicy.onUserUnmuteOrResume()
        player.volume = 1.0f
        stopRecording()
        userManuallyStoppedRecordingForCurrentStation = false
        if (!isLocal && (wasRecordingBeforeSwitch || shouldRecord || isContinuousRecordingEnabled())) {
            pendingAutoRecord = true
        } else {
            pendingAutoRecord = false
        }
        isAlarmMode = triggeredBy == "ALARM"
        unsplitShowModeForCurrentRecording = isAlarmMode && pendingAutoRecord
        hasSuccessfullyStartedPlaying = false
        consecutiveErrorCount = 0
        isChangingStation = true
        isInitialStationPlayback = true
        lastBitrateInfo = ""
        sendBitrateUpdate()
        if (isAlarmMode) {
            wakeLock?.acquire(AppConfig.Alarm.WAKELOCK_TIMEOUT_MS)
        }

        currentStreamUrl = streamUrl
        currentStationName = stationName ?: "Radio"
        if (!isAlarmMode) {
            notificationManager.cancel(RadioNotificationManager.ALARM_NOTIFICATION_ID)
        }
        currentStationBitmap = app.radiorecalarm.ui.StationArtworkUtils.generateDarkStationBitmap(currentStationName)

        currentArtist = if (isLocal && trackArtistOverride != null) trackArtistOverride else getString(R.string.live_broadcast)
        currentTitle = if (isLocal && !trackTitleOverride.isNullOrBlank()) trackTitleOverride else currentStationName
        currentExtra = ""
        
        lastSentTitle = ""
        lastSentArtist = ""
        lastSentStationName = ""
        lastSentTime = 0

        if (!isLocal) {
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
        }

        if (isAlarmMode) {
            val alarmNotification = notificationManager.createAlarmNotification(currentStationName)
            notificationManager.notify(RadioNotificationManager.ALARM_NOTIFICATION_ID, alarmNotification)
        }

        updateNotification()

        player.stop()

        val initialMeta = metadataHelper.buildMediaMetadata(
            title = currentTitle,
            artist = currentArtist,
            stationName = currentStationName,
            artworkData = null
        )

        Log.d(TAG, "playStation: Sending INITIAL metadata: Title='$currentTitle', Artist='$currentArtist', Album='$currentStationName'")

        val uniqueMediaId = initialMeta.extras?.getString("android.media.metadata.MEDIA_ID") ?: "station_${SystemClock.elapsedRealtime()}"
        player.setMediaItem(
            MediaItem.Builder()
                .setUri(streamUrl)
                .setMediaId(uniqueMediaId)
                .setMediaMetadata(initialMeta)
                .build()
        )

        player.updateTrackMetadata(initialMeta)
        player.streamStartTime = SystemClock.elapsedRealtime()

        if (!isLocal && (pendingAutoRecord || isContinuousRecordingEnabled())) {
            autoRecordArmedForStreamUrl = streamUrl
        }
        requestAudioFocus()
        player.prepare()
        player.play()
        updateExternalDevices(currentTitle, currentArtist)
    }

    private fun updateNotification() {
        if (!::player.isInitialized || mediaSession == null) return
        val isSuppressed = player.playbackSuppressionReason != Player.PLAYBACK_SUPPRESSION_REASON_NONE
        val shouldKeepForeground = player.isPlaying || isAlarmMode || isChangingStation || isRecording ||
            (player.playWhenReady && (isSuppressed || interruptionPolicy.wasSuppressedByTransientLoss))

        val notification = notificationManager.buildNotification(
            mediaSession = mediaSession!!,
            stationName = currentStationName,
            trackTitle = currentTitle,
            trackArtist = currentArtist,
            bitmap = currentStationBitmap,
            isAlarmMode = isAlarmMode,
            isPlaying = player.isPlaying,
            isRecording = isRecording,
            isMutedRecording = isRecording && interruptionPolicy.isMutedRecording
        )
        if (shouldKeepForeground) {
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
        isCurrentlyPlaying = false
        cancelIdleTimeout()
        if (wakeLock?.isHeld == true) wakeLock?.release()
        interruptionPolicy.onUserPauseOrStop()
        player.volume = 1.0f
        abandonAudioFocus()
        stopSleepTimer()
        pendingAutoRecord = false
        unsplitShowModeForCurrentRecording = false
        autoRecordArmedForStreamUrl = null
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

    private fun startIdleTimeout() {
        cancelIdleTimeout()
        Log.d(TAG, "startIdleTimeout: Alustan ootetaimerit (${AppConfig.Player.IDLE_TIMEOUT_MS / 1000}s)...")
        idleTimeoutJob = serviceScope.launch(Dispatchers.Main) {
            delay(AppConfig.Player.IDLE_TIMEOUT_MS)
            if (::player.isInitialized && !player.isPlaying && !player.playWhenReady && !interruptionPolicy.wasSuppressedByTransientLoss && !isRecording) {
                Log.i(TAG, "startIdleTimeout: Taimer (${AppConfig.Player.IDLE_TIMEOUT_MS / 1000}s) aegus. Peatame raadio ja vabastame Bluetoothi ning helikanalid.")
                stopRadio()
            }
        }
    }

    private fun cancelIdleTimeout() {
        if (idleTimeoutJob != null) {
            Log.d(TAG, "cancelIdleTimeout: Taimer tühistatud.")
            idleTimeoutJob?.cancel()
            idleTimeoutJob = null
        }
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
            interruptionPolicy.onUserPauseOrStop()
            player.volume = 1.0f
            abandonAudioFocus()
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
        val isPlaying = if (::player.isInitialized) player.isPlaying else false
        val stationName = currentStationName
        val title = currentTitle
        val artist = currentArtist
        val extra = currentExtra
        val currentBitrate = lastBitrateInfo

        serviceScope.launch {
            app.radiorecalarm.widget.HomeWidgetUpdater.update(
                context = applicationContext,
                stationName = stationName,
                title = title,
                artist = artist,
                extra = extra,
                bitrate = currentBitrate,
                isPlaying = isPlaying
            )
        }
    }

    override fun onDestroy() {
        isCurrentlyPlaying = false
        cancelIdleTimeout()
        if (wakeLock?.isHeld == true) wakeLock?.release()
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        try { connectivityManager.unregisterNetworkCallback(networkCallback) } catch (e: Exception) {}
        LocalBroadcastManager.getInstance(this).unregisterReceiver(statusReceiver)
        try { unregisterReceiver(noisyReceiver) } catch (_: Exception) {}
        abandonAudioFocus()
        stopRecording()
        activeDataSource = null
        player.release(); mediaSession?.release(); super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
}

@androidx.annotation.OptIn(UnstableApi::class)
private class RetryingHttpDataSource(
    private val delegate: HttpDataSource,
    private val initialTitleProvider: () -> String?,
    private val shouldAlignToFrame: () -> Boolean,
    private val onStreamOpened: (metaInt: Int, isAac: Boolean, icyBr: String?) -> Unit,
    private val onMetadataBoundary: (String) -> Unit,
    private val onBytesRead: (String, ByteArray, Int, Int) -> Unit
) : HttpDataSource by delegate {

    private var streamProcessor: IcyStreamProcessor? = null
    private var openedUrl: String = ""

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
        openedUrl = dataSpec.uri.toString()
        val headers = delegate.responseHeaders

        var metaIntStr: String? = null
        var contentType: String? = null
        var icyBr: String? = null
        for ((key, value) in headers) {
            when {
                key.equals("icy-metaint", ignoreCase = true) -> metaIntStr = value.firstOrNull()
                key.equals("content-type", ignoreCase = true) -> contentType = value.firstOrNull()
                key.equals("icy-br", ignoreCase = true) -> icyBr = value.firstOrNull()
            }
        }

        val metaInt = metaIntStr?.toIntOrNull() ?: -1
        val isAac = openedUrl.contains("aac", ignoreCase = true) ||
            (contentType?.contains("aac", ignoreCase = true) == true) ||
            (contentType?.contains("mp4", ignoreCase = true) == true)

        streamProcessor = IcyStreamProcessor(
            metaInt = metaInt,
            preferAac = isAac,
            initialTitle = initialTitleProvider(),
            shouldAlignToFrame = shouldAlignToFrame,
            onAudioBytes = { buf, off, len -> onBytesRead(openedUrl, buf, off, len) },
            onMetadataBoundary = onMetadataBoundary
        )

        onStreamOpened(metaInt, isAac, icyBr)
        Log.i("RetryingDataSource", "Ühendus avatud. icy-metaint: $metaInt, isAac: $isAac")
        return result
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val result = delegate.read(buffer, offset, length)
        if (result > 0) {
            streamProcessor?.process(buffer, offset, result)
        }
        return result
    }

    override fun close() {
        try {
            streamProcessor?.flush()
        } finally {
            streamProcessor = null
            delegate.close()
        }
    }
}
