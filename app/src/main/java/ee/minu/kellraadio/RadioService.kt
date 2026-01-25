@file:OptIn(UnstableApi::class)
package ee.minu.kellraadio

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

@Suppress("DEPRECATION")
class RadioService : Service() {

    private lateinit var player: SkodaAwarePlayer

    private var mediaSession: MediaSession? = null
    private var currentStationName: String = "Raadio"
    private var currentStationBitmap: android.graphics.Bitmap? = null

    private var isChangingStation = false
    private var currentCategory: String = ""
    private var lastBitrateInfo: String = ""

    // Hoiame meeles jooksvat infot
    private var currentArtist: String = ""
    private var currentTitle: String = ""
    private var currentExtra: String = ""

    // Et vältida topelt uuendusi autole
    private var lastSentArtist: String = ""
    private var lastSentTitle: String = ""

    private var isAlarmMode: Boolean = false
    private var currentStreamUrl: String = ""

    private var metadataPushJob: kotlinx.coroutines.Job? = null

    private val sessionScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // --- ABILISED ---
    private val notificationManager by lazy { RadioNotificationManager(this) }
    private val metadataHelper by lazy { RadioMetadataHelper(this) } // UUS
    private val prefs: SharedPreferences by lazy { getSharedPreferences("RadioServicePrefs", Context.MODE_PRIVATE) }

    private var sleepTimerJob: kotlinx.coroutines.Job? = null
    private var sleepTimerRemainingMillis: Long = 0L

    private val listeners = CopyOnWriteArraySet<Player.Listener>()
    private var wakeLock: android.os.PowerManager.WakeLock? = null

    companion object {
        const val ACTION_STATION_SELECTED_BY_SERVICE = "ee.minu.kellraadio.STATION_SELECTED"
        const val ACTION_PAUSE = "ee.minu.kellraadio.ACTION_PAUSE"
        const val ACTION_STOP = "ee.minu.kellraadio.ACTION_STOP"
        const val ACTION_STATION_CHANGED = "ee.minu.kellraadio.STATION_CHANGED"
        const val ACTION_BITRATE_UPDATED = "ee.minu.kellraadio.BITRATE_UPDATED"
        const val ACTION_METADATA_UPDATED = "ee.minu.kellraadio.METADATA_UPDATED"
        const val ACTION_PLAYER_STOPPED = "ee.minu.kellraadio.PLAYER_STOPPED"
        const val ACTION_PLAYER_ERROR = "ee.minu.kellraadio.PLAYER_ERROR"
        const val ACTION_GET_STATUS = "ee.minu.kellraadio.GET_STATUS"
        const val TAG = "RadioService"
        const val ACTION_SET_TIMER = "ee.minu.kellraadio.SET_TIMER"
        const val ACTION_TIMER_TICK = "ee.minu.kellraadio.TIMER_TICK"
        const val EXTRA_TIMER_DURATION = "TIMER_DURATION_MINUTES"
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
                    if (player.isPlaying) player.pause()
                    else {
                        if (currentStreamUrl.isNotEmpty()) startRadio(currentStreamUrl, currentStationName)
                        else {
                            val savedUrl = prefs.getString("LAST_URL", null)
                            val savedName = prefs.getString("LAST_NAME", "Raadio")
                            if (savedUrl != null) startRadio(savedUrl, savedName ?: "Raadio")
                        }
                    }
                    return SessionResult.RESULT_SUCCESS
                }
            }
            return super.onPlayerCommandRequest(session, controller, playerCommand)
        }
    }

    private fun startRadio(url: String, name: String, triggeredBy: String? = null) {
        metadataPushJob?.cancel()

        serviceScope.launch(Dispatchers.Main) {
            currentStationName = name
            currentStationBitmap = ee.minu.kellraadio.ui.StationArtworkUtils.generateDarkStationBitmap(name)

            // Nullime algseisu
            currentArtist = getString(R.string.live_broadcast)
            currentTitle = currentStationName
            currentExtra = ""
            lastSentArtist = ""
            lastSentTitle = ""

            player.streamStartTime = SystemClock.elapsedRealtime()

            sendMetadataUpdate(currentTitle, currentArtist)
            onStartCommand(Intent(this@RadioService, RadioService::class.java).apply {
                putExtra("STREAM_URL", url)
                putExtra("STATION_NAME", name)
                putExtra("TRIGGERED_BY", triggeredBy)
            }, 0, 0)
        }
    }

    private fun changeStation(offset: Int) {
        isChangingStation = true
        serviceScope.launch {
            val db = AppDatabase.getDatabase(applicationContext)
            val dao = db.radioStationDao()
            val allStations = dao.getAllActiveStationsSync()
            if (allStations.isEmpty()) return@launch

            val navigationList = when (currentCategory) {
                "Favorites" -> allStations.filter { it.isFavorite }
                "" -> allStations
                else -> allStations.filter { it.category == currentCategory }
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
                startRadio(nextStation.url, nextStation.name)
            }
        }
    }

    private fun updatePlayerMetadata(trackTitleFromStream: String?) {
        Log.i(TAG, "[METADATA_RAW] Striimist tuli: '$trackTitleFromStream'")

        // MUUDATUS: Kasutame helperit parsimiseks
        val parsed = metadataHelper.parse(trackTitleFromStream ?: "", currentStationName)

        if (parsed.artist == currentArtist && parsed.title == currentTitle) return

        saveToHistory(parsed.artist, parsed.title)

        player.streamStartTime = SystemClock.elapsedRealtime()

        currentArtist = parsed.artist
        currentTitle = parsed.title
        currentExtra = parsed.extra

        Log.d(TAG, "[METADATA_FINAL] -> Artist: '$currentArtist' | Title: '$currentTitle'")

        updateExternalDevices(currentTitle, currentArtist)
        sendMetadataUpdate(currentTitle, currentArtist, currentExtra)
        updateNotification()
        metadataPushJob?.cancel()
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
        if (title == lastSentTitle && artist == lastSentArtist) return

        lastSentTitle = title
        lastSentArtist = artist

        // MUUDATUS: Kasutame helperit metaandmete ehitamiseks
        val newMetadata = metadataHelper.buildMediaMetadata(
            title = title,
            artist = artist,
            stationName = currentStationName,
            artworkData = getArtworkBytes()
        )

        player.playlistMetadata = newMetadata

        val currentItem = player.currentMediaItem
        if (currentItem != null) {
            val uniqueId = newMetadata.extras?.getString("android.media.metadata.MEDIA_ID") ?: "Raadio"
            val newItem = currentItem.buildUpon()
                .setMediaMetadata(newMetadata)
                .setMediaId(uniqueId)
                .build()
            player.replaceMediaItem(0, newItem)
        }

        serviceScope.launch(Dispatchers.Main) {
            listeners.forEach { listener ->
                try { listener.onMediaMetadataChanged(newMetadata) } catch (e: Exception) { }
            }
        }
        // Logime ID, et näha, kas muutub (Helper genereerib selle)
        val id = newMetadata.extras?.getString("android.media.metadata.MEDIA_ID")
        Log.i(TAG, "AUTOLE: '$title' - '$artist' (ID: $id)")
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
                if (entry is IcyInfo) updatePlayerMetadata(entry.title)
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

            if (isPlaying) {
                isChangingStation = false
                saveToHistory(currentArtist, currentTitle)
                LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(
                    Intent(ACTION_STATION_CHANGED).apply {
                        putExtra("STATION_NAME", currentStationName)
                    }
                )
                sendMetadataUpdate(currentTitle, currentArtist, currentExtra)
                updateExternalDevices(currentTitle, currentArtist)
                metadataPushJob?.cancel()

            } else {
                if (!isChangingStation) {
                    LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(Intent(ACTION_PLAYER_STOPPED))
                }
                metadataPushJob?.cancel()
            }
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (playWhenReady) {
                val notification = notificationManager.buildNotification(mediaSession!!, currentStationName, currentTitle, currentArtist, currentStationBitmap, isAlarmMode)
                if (Build.VERSION.SDK_INT >= 34) {
                    startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
                } else {
                    startForeground(1, notification)
                }
            }
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            Log.e(TAG, "Player Error: ${error.message}")
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
                        val mediaItem = MediaItem.Builder()
                            .setUri(currentStreamUrl)
                            .setMediaId("Raadio")
                            .setMediaMetadata(player.playlistMetadata)
                            .build()
                        player.setMediaItem(mediaItem)
                        player.prepare()
                        player.play()
                    }
                }
            }
        }
    }

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (player.isPlaying) {
                LocalBroadcastManager.getInstance(this@RadioService).sendBroadcast(
                    Intent(ACTION_STATION_CHANGED).apply { putExtra("STATION_NAME", currentStationName) }
                )
                sendMetadataUpdate(currentTitle, currentArtist, currentExtra)
                sendBitrateUpdate()
                sendTimerTick(sleepTimerRemainingMillis)
            } else { LocalBroadcastManager.getInstance(this@RadioService).sendBroadcast(Intent(ACTION_PLAYER_STOPPED)) }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Teenus loodud")
        val dataSourceFactory = DefaultHttpDataSource.Factory().setUserAgent("Mozilla/5.0").setAllowCrossProtocolRedirects(true).setTransferListener(httpTransferListener)
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
        wakeLock = powerManager.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "Kellraadio::RadioWakeLock")
        wakeLock?.setReferenceCounted(false)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        wakeLock?.acquire(10 * 60 * 1000L)

        val action = intent?.action

        if (action == ACTION_PAUSE) {
            player.pause()
            if (wakeLock?.isHeld == true) wakeLock?.release()
            metadataPushJob?.cancel()
            updateNotification()
            return START_STICKY
        }

        if (action == ACTION_STOP) {
            stopRadio()
            return START_NOT_STICKY
        }

        if (action == ACTION_SET_TIMER) {
            val duration = intent.getIntExtra(EXTRA_TIMER_DURATION, 0)
            startSleepTimer(duration)
            return START_STICKY
        }

        val streamUrl = intent?.getStringExtra("STREAM_URL")
        val stationName = intent?.getStringExtra("STATION_NAME")
        val triggeredBy = intent?.getStringExtra("TRIGGERED_BY")
        val categoryParam = intent?.getStringExtra("CATEGORY_NAME")
        if (categoryParam != null) {
            currentCategory = categoryParam
        }

        if (streamUrl != null) {
            if (currentStreamUrl == streamUrl && player.isPlaying) {
                Log.i(TAG, "See jaam juba mängib, ei restardi: $stationName")
                currentStationName = stationName ?: currentStationName
                updateNotification()
                return START_STICKY
            }
            isChangingStation = true
            lastBitrateInfo = ""
            sendBitrateUpdate()
            wakeLock?.acquire(10 * 60 * 1000L)

            Log.i(TAG, "Alustan jaama (onStartCommand): $stationName")

            currentStreamUrl = streamUrl
            currentStationName = stationName ?: "Raadio"
            isAlarmMode = triggeredBy == "ALARM"
            currentStationBitmap = ee.minu.kellraadio.ui.StationArtworkUtils.generateDarkStationBitmap(currentStationName)

            // MUUDATUS: Kasutame siin ka stringResource, aga see peab olema helperis?
            // Või võtame otse context.getString, kuna oleme Service'is.
            // Aga et olla järjepidev, kasutame helperi loogikat? Ei, startRadio puhul on lihtsam otse määrata.
            // Aga kuna tahame vältida hardcoded stringe, kasutame resource'i.
            currentArtist = getString(R.string.live_broadcast)
            currentTitle = currentStationName
            currentExtra = ""

            if (isAlarmMode) {
                val alarmNotification = notificationManager.createAlarmNotification(currentStationName)
                notificationManager.notify(RadioNotificationManager.ALARM_NOTIFICATION_ID, alarmNotification)
            } else {
                prefs.edit().putString("LAST_URL", currentStreamUrl).putString("LAST_NAME", currentStationName).apply()
            }

            updateNotification()

            if (player.isPlaying) player.stop()
            player.clearMediaItems()

            // MUUDATUS: Kasutame helperit ka siin algseisu loomiseks
            val initialMeta = metadataHelper.buildMediaMetadata(
                title = currentTitle,
                artist = currentArtist,
                stationName = currentStationName,
                artworkData = getArtworkBytes()
            )

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

        return START_STICKY
    }

    private fun updateNotification() {
        val notification = notificationManager.buildNotification(mediaSession!!, currentStationName, currentTitle, currentArtist, currentStationBitmap, isAlarmMode)

        if (player.isPlaying || isAlarmMode) {
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

                historyDao.insert(ee.minu.kellraadio.HistoryItem(stationName = currentStationName, artist = artist, title = title, timestamp = System.currentTimeMillis()))
                historyDao.cleanOldHistory()
            } catch (e: Exception) {
                Log.e(TAG, "History viga: ${e.message}")
            }
        }
    }

    private fun getArtworkBytes(): ByteArray? {
        return currentStationBitmap?.let { bmp ->
            val stream = java.io.ByteArrayOutputStream()
            bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)
            stream.toByteArray()
        }
    }

    override fun onDestroy() {
        if (wakeLock?.isHeld == true) wakeLock?.release()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(statusReceiver)
        player.release(); mediaSession?.release(); super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
}