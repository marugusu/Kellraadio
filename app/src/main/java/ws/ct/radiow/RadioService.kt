package ws.ct.radiow

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
import ws.ct.radiow.widget.HomeWidget
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
import java.util.concurrent.CopyOnWriteArraySet
import ws.ct.radiow.R

@Suppress("DEPRECATION")
class RadioService : Service() {

    private lateinit var player: SkodaAwarePlayer

    private var mediaSession: MediaSession? = null
    private var currentStationName: String = "Radio"
    private var currentStationBitmap: android.graphics.Bitmap? = null

    private var isChangingStation = false
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

    private val sessionScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val notificationManager by lazy { RadioNotificationManager(this) }
    private val metadataHelper by lazy { RadioMetadataHelper(this) }
    private val prefs: SharedPreferences by lazy { getSharedPreferences("RaadioPrefs", Context.MODE_PRIVATE) }

    private var sleepTimerJob: kotlinx.coroutines.Job? = null
    private var sleepTimerRemainingMillis: Long = 0L

    private val listeners = CopyOnWriteArraySet<Player.Listener>()
    private var wakeLock: android.os.PowerManager.WakeLock? = null

    companion object {
        const val ACTION_UPDATE_STATION_NAME = "ws.ct.radiow.UPDATE_NAME"
        const val ACTION_FORCE_WIDGET_UPDATE = "ws.ct.radiow.FORCE_WIDGET_UPDATE"

        const val ACTION_STATION_SELECTED_BY_SERVICE = "ws.ct.radiow.STATION_SELECTED"
        const val ACTION_PAUSE = "ws.ct.radiow.ACTION_PAUSE"
        const val ACTION_RESUME = "ws.ct.radiow.ACTION_RESUME"
        const val ACTION_STOP = "ws.ct.radiow.ACTION_STOP"
        const val ACTION_SKIP_NEXT = "ws.ct.radiow.ACTION_SKIP_NEXT"
        const val ACTION_SKIP_PREVIOUS = "ws.ct.radiow.ACTION_SKIP_PREVIOUS"
        const val ACTION_STATION_CHANGED = "ws.ct.radiow.STATION_CHANGED"
        const val ACTION_BITRATE_UPDATED = "ws.ct.radiow.BITRATE_UPDATED"
        const val ACTION_METADATA_UPDATED = "ws.ct.radiow.METADATA_UPDATED"
        const val ACTION_PLAYER_STOPPED = "ws.ct.radiow.PLAYER_STOPPED"
        const val ACTION_PLAYER_ERROR = "ws.ct.radiow.PLAYER_ERROR"
        const val ACTION_GET_STATUS = "ws.ct.radiow.GET_STATUS"
        const val TAG = "RadioService"
        const val ACTION_SET_TIMER = "ws.ct.radiow.SET_TIMER"
        const val ACTION_TIMER_TICK = "ws.ct.radiow.TIMER_TICK"
        const val EXTRA_TIMER_DURATION = "TIMER_DURATION_MINUTES"
        const val ACTION_PLAY_PAUSE_TOGGLE = "ws.ct.radiow.ACTION_PLAY_PAUSE_TOGGLE"

    }

    private val mediaSessionCallback = object : MediaSession.Callback {
        override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session).build()
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

    private fun updateExternalDevices(title: String, artist: String) {
        if (!::player.isInitialized) return

        val currentTime = SystemClock.elapsedRealtime()
        if (currentTime - lastSentTime < 500) {
            Log.d(TAG, "updateExternalDevices: SKIPPED (Too fast updates)")
            return
        }

        if (title == lastSentTitle && artist == lastSentArtist) {
            return
        }
        
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

        player.playlistMetadata = newMetadata

        val currentItem = player.currentMediaItem
        if (currentItem != null) {
             val newItem = currentItem.buildUpon()
                .setMediaMetadata(newMetadata)
                .build()
            
            player.replaceMediaItem(player.currentMediaItemIndex, newItem)
        }

        serviceScope.launch(Dispatchers.Main) {
            listeners.forEach { listener ->
                try { listener.onMediaMetadataChanged(newMetadata) } catch (e: Exception) { }
            }
        }
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
            if (playbackState == Player.STATE_IDLE && player.playWhenReady) {
                player.prepare()
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
                
                metadataPushJob?.cancel()
                metadataPushJob = sessionScope.launch {
                    delay(5000)
                    Log.d(TAG, "metadataPushJob: Forcing 5s metadata push")
                    lastSentTitle = ""
                    lastSentArtist = ""
                    lastSentTime = 0
                    player.streamStartTime = SystemClock.elapsedRealtime()
                    updateExternalDevices(currentTitle, currentArtist)
                }

            } else {
                if (!player.playWhenReady && !isChangingStation) {
                    LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(Intent(ACTION_PLAYER_STOPPED))
                }
                metadataPushJob?.cancel()
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
            LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(Intent(ACTION_PLAYER_ERROR))
            val cause = error.cause
            val isFatalError = when {
                cause is androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException -> cause.responseCode in 400..499
                cause is androidx.media3.exoplayer.source.UnrecognizedInputFormatException -> true
                error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED -> true
                else -> false
            }
            if (isFatalError) {
                stopRadio(isError = true)
                return
            }
            isChangingStation = false
            serviceScope.launch {
                delay(2000)
                withContext(Dispatchers.Main) {
                    if (currentStreamUrl.isNotEmpty()) {
                        playStation(currentStreamUrl, currentStationName, "RECONNECT")
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
        }
    }

    override fun onCreate() {
        super.onCreate()
        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0")
            .setAllowCrossProtocolRedirects(false)
            .setTransferListener(httpTransferListener)

        val trackSelector = DefaultTrackSelector(this).apply { setParameters(buildUponParameters().setForceHighestSupportedBitrate(true)) }
        val realPlayer = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(this).setDataSourceFactory(dataSourceFactory))
            .setTrackSelector(trackSelector)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
            .setHandleAudioBecomingNoisy(true).build()

        realPlayer.addListener(playerListener)
        player = SkodaAwarePlayer(realPlayer, listeners)
        mediaSession = MediaSession.Builder(this, player).setSessionActivity(PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)).setCallback(mediaSessionCallback).build()
        LocalBroadcastManager.getInstance(this).registerReceiver(statusReceiver, IntentFilter(ACTION_GET_STATUS))
        val powerManager = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        wakeLock = powerManager.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "radiow::RadioWakeLock")
        wakeLock?.setReferenceCounted(false)
        
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
        currentStationBitmap = ws.ct.radiow.ui.StationArtworkUtils.generateDarkStationBitmap(currentStationName)

        if (currentStreamUrl.isNotEmpty()) {
            val initialMeta = metadataHelper.buildMediaMetadata(
                title = currentTitle,
                artist = currentArtist,
                stationName = currentStationName,
                artworkData = null
            )
            
            player.playlistMetadata = initialMeta
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
            ACTION_PAUSE -> {
                player.pause()
                if (wakeLock?.isHeld == true) wakeLock?.release()
                metadataPushJob?.cancel()
                updateNotification()
                return START_STICKY
            }
            ACTION_RESUME -> {
                if (!player.isPlaying && currentStreamUrl.isNotEmpty()) {
                    playStation(currentStreamUrl, currentStationName, "USER_RESUME")
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
                    currentStationBitmap = ws.ct.radiow.ui.StationArtworkUtils.generateDarkStationBitmap(currentStationName)
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

        isChangingStation = true
        lastBitrateInfo = ""
        sendBitrateUpdate()
        wakeLock?.acquire(10 * 60 * 1000L)

        updateNotification()

        currentStreamUrl = streamUrl
        currentStationName = stationName ?: "Radio"
        isAlarmMode = triggeredBy == "ALARM"
        currentStationBitmap = ws.ct.radiow.ui.StationArtworkUtils.generateDarkStationBitmap(currentStationName)

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

        player.playlistMetadata = initialMeta
        player.streamStartTime = SystemClock.elapsedRealtime()

        player.prepare()
        player.play()
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
        metadataPushJob?.cancel()
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
                historyDao.insert(ws.ct.radiow.HistoryItem(stationName = currentStationName, artist = artist, title = title, timestamp = System.currentTimeMillis()))
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
                val widget = ws.ct.radiow.widget.HomeWidget()
                val glanceIds = manager.getGlanceIds(widget.javaClass)

                glanceIds.forEach { glanceId ->
                    updateAppWidgetState(context, glanceId) { prefs ->
                        prefs[ws.ct.radiow.widget.HomeWidget.Prefs.stationName] = stationName
                        prefs[ws.ct.radiow.widget.HomeWidget.Prefs.title] = title
                        prefs[ws.ct.radiow.widget.HomeWidget.Prefs.artist] = artist
                        prefs[ws.ct.radiow.widget.HomeWidget.Prefs.bitrate] = currentBitrate
                        val statusText = if (isPlaying) getString(R.string.status_playing) else getString(R.string.status_stopped)
                        val extraInfo = if (extra.isNotBlank()) "$extra • $statusText" else statusText
                        prefs[ws.ct.radiow.widget.HomeWidget.Prefs.status] = extraInfo
                        prefs[ws.ct.radiow.widget.HomeWidget.Prefs.alarm] = nextAlarmString
                        prefs[ws.ct.radiow.widget.HomeWidget.Prefs.isPlaying] = isPlaying
                        prefs[ws.ct.radiow.widget.HomeWidget.Prefs.bgTransparency] = bgTransparency
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
        LocalBroadcastManager.getInstance(this).unregisterReceiver(statusReceiver)
        player.release(); mediaSession?.release(); super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
}