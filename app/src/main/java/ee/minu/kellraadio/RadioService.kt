@file:OptIn(UnstableApi::class)
package ee.minu.kellraadio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
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
import androidx.media3.session.MediaStyleNotificationHelper
import androidx.media3.session.SessionResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import java.util.UUID
import java.util.concurrent.CopyOnWriteArraySet

@Suppress("DEPRECATION")
class RadioService : Service() {
    private lateinit var player: Player
    private var mediaSession: MediaSession? = null
    private var currentStationName: String = "Raadio"
    private var currentCategory: String = "" // UUS
    private var lastBitrateInfo: String = ""
    private var currentTrackTitle: String = ""

    private var currentArtist: String = ""
    private var currentTitle: String = ""
    private var currentExtra: String = ""
    private var currentExtraInfo: String = ""
    private var isAlarmMode: Boolean = false
    private var currentStreamUrl: String = ""

    private var metadataPushJob: kotlinx.coroutines.Job? = null

    // SKODA FIX: Hoiame seda alati 1 peal, aga muudame UUID-d
    private var trackVersion: Int = 1

    // Muutuja "võlts-aja" arvutamiseks
    private var streamStartTime: Long = 0L

    private val sessionScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val notificationManager by lazy { getSystemService(NOTIFICATION_SERVICE) as NotificationManager }
    private val prefs: SharedPreferences by lazy { getSharedPreferences("RadioServicePrefs", Context.MODE_PRIVATE) }

    private var sleepTimerJob: kotlinx.coroutines.Job? = null
    private var sleepTimerRemainingMillis: Long = 0L

    private val listeners = CopyOnWriteArraySet<Player.Listener>()

    private var wakeLock: android.os.PowerManager.WakeLock? = null

    companion object {
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

    // private val mediaSessionCallback = object : MediaSession.Callback {
    //     override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
    //         val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
    //             .add(Player.COMMAND_SEEK_TO_NEXT).add(Player.COMMAND_SEEK_TO_PREVIOUS)
    //             .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM).add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
    //             .add(Player.COMMAND_GET_METADATA).add(Player.COMMAND_PLAY_PAUSE)
    //             .build()
    //         return MediaSession.ConnectionResult.AcceptedResultBuilder(session).setAvailableSessionCommands(sessionCommands).build()
    //     }
        private val mediaSessionCallback = object : MediaSession.Callback {
            override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
                // PARANDUS: Kuna sa kommenteerisid lisakäsud välja, siis pole vaja "sessionCommands" muutujat üldse luua.
                // Lihtsalt aktsepteerime ühenduse vaikimisi seadetega.
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
                            startRadio(currentStreamUrl, currentStationName)
                        } else {
                            val savedUrl = prefs.getString("LAST_URL", null)
                            val savedName = prefs.getString("LAST_NAME", "Raadio")
                            if (savedUrl != null) {
                                startRadio(savedUrl, savedName ?: "Raadio")
                            }
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
            currentTrackTitle = ""
            currentExtraInfo = ""
            trackVersion = 1
            // Nullime aja, et autole tunduks, et uus lugu algas 00:00-st
            streamStartTime = SystemClock.elapsedRealtime()

            // UI uuendus
            sendMetadataUpdate(name, "Otseeeter")

            // VÄLISED SEADMED
            updateExternalDevices(name, "Otseeeter")

            onStartCommand(Intent(this@RadioService, RadioService::class.java).apply {
                putExtra("STREAM_URL", url)
                putExtra("STATION_NAME", name)
                putExtra("TRIGGERED_BY", triggeredBy)
            }, 0, 0)
        }
    }

    private fun changeStation(offset: Int) {
        serviceScope.launch {
            val db = AppDatabase.getDatabase(applicationContext)
            val dao = db.radioStationDao()

            // 1. Võtame kõik aktiivsed jaamad
            val allStations = dao.getAllActiveStationsSync()
            if (allStations.isEmpty()) return@launch

            // 2. Filtreerime nimekirja vastavalt salvestatud kategooriale
            val navigationList = when (currentCategory) {
                "Lemmikud" -> allStations.filter { it.isFavorite }
                "" -> allStations // Kui kategooriat pole määratud (nt äratus), võta kõik
                else -> allStations.filter { it.category == currentCategory }
            }

            // Turvavõrk: Kui filtreerimine andis tühja tulemuse (nt kategooria muutus),
            // või jaama pole selles kategoorias, kasutame kõiki jaamu.
            val finalNavList = if (navigationList.isEmpty() || navigationList.none { it.name == currentStationName }) {
                allStations
            } else {
                navigationList
            }

            // 3. Leiame uue jaama indeksi
            val currentIndex = finalNavList.indexOfFirst { it.name == currentStationName }
            val baseIndex = if (currentIndex == -1) 0 else currentIndex
            val nextIndex = (baseIndex + offset + finalNavList.size) % finalNavList.size
            val nextStation = finalNavList[nextIndex]

            withContext(Dispatchers.Main) {
                isAlarmMode = false
                startRadio(nextStation.url, nextStation.name)
            }
        }
    }

    private fun updatePlayerMetadata(trackTitleFromStream: String?) {
        Log.i(TAG, "[METADATA_RAW] Striimist tuli: '$trackTitleFromStream'")
        val (finalArtist, finalTitle, finalExtra) = splitMetadata(trackTitleFromStream ?: "")
        saveToHistory(finalArtist, finalTitle)
        if (finalArtist + finalTitle + finalExtra == currentArtist + currentTitle + currentExtra) return // Väldime asjatut tööd
// Salvestame hetke seisud (et teised funktsioonid saaksid neid kasutada)




        streamStartTime = SystemClock.elapsedRealtime()
        currentArtist = finalArtist
        currentTitle = finalTitle
        currentExtra = finalExtra

        Log.d(TAG, "[METADATA_FINAL] Äppi läheb -> Artist: '$finalArtist' | Title: '$finalTitle' | Extra: '$finalExtra'")

        // 1. Kohene uuendus
        updateExternalDevices(finalTitle, finalArtist)
        //sendMetadataUpdate(finalTitle, finalArtist)
        sendMetadataUpdate(finalTitle, finalArtist, finalExtra)
        updateNotification()

        // 2. Kordussaatmine
        val stationAtTheMoment = currentStationName
        metadataPushJob?.cancel()
        metadataPushJob = serviceScope.launch {
            delay(4000)
            if (currentStationName == stationAtTheMoment) {
                Log.d(TAG, "[METADATA] Kordussaatmine (4s)")
                withContext(Dispatchers.Main) { updateExternalDevices(finalTitle, finalArtist) }
            }

            delay(15000)
            if (currentStationName == stationAtTheMoment) {
                Log.d(TAG, "[METADATA] Kordussaatmine (15s)")
                withContext(Dispatchers.Main) { updateExternalDevices(finalTitle, finalArtist) }
            }
        }
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
                putExtra("PARSED_EXTRA", parsedExtra) // See läheb ainult äpi UI-sse
            }
        )
    }

    // --- KESKNE FUNKTSIOON VÄLISTE SEADMETE JAOKS ---
    private fun updateExternalDevices(title: String, artist: String) {
        if (!::player.isInitialized) return

        // 1. Track Number: ALATI 1 (See parandab "No track list" vea)
        trackVersion = 1
        // 1. Paneme loenduri käima! (Enne oli see 1)
        //trackVersion++
        // if (trackVersion > 9999) trackVersion = 1

        // 2. UUID: Kasutame fikseeritud ID-d "Raadio", et vältida Timeout viga
        // (Seda rida ei saa tagasi UUID.randomUUID()-ks muuta, muidu tuleb viga tagasi)
        val uniqueId = "Raadio"

        val extras = Bundle()
        extras.putString("android.media.metadata.MEDIA_ID", uniqueId)
        // 3. DURATION: 5 minutit (Vajalik "Bluetooth audio" vea vältimiseks)
        extras.putLong("android.media.metadata.DURATION", 300000L)

        val newMetadata = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(currentStationName)
            .setTrackNumber(1) // ALATI 1
            .setTotalTrackCount(1) // ALATI 1
            // MUUDATUS: Kasutame muutuvat numbrit
            //.setTrackNumber(trackVersion)
            // MUUDATUS: Ütleme, et koguarv on sama mis praegune (N / N)
            // .setTotalTrackCount(trackVersion)
            .setExtras(extras)
            .build()

        player.playlistMetadata = newMetadata

        // --- TEHNILINE PARANDUS (Seda rida oli vaja vea parandamiseks) ---
        val currentItem = player.currentMediaItem
        if (currentItem != null) {
            val newItem = currentItem.buildUpon()
                .setMediaMetadata(newMetadata)
                .setMediaId(uniqueId) // Seome ID-d kokku
                .build()
            player.replaceMediaItem(0, newItem)
        }
        // ----------------------------------------------------------------

        serviceScope.launch(Dispatchers.Main) {
            listeners.forEach { listener ->
                try {
                    listener.onMediaMetadataChanged(newMetadata)
                } catch (e: Exception) {
                    Log.e(TAG, "Viga kuulaja teavitamisel: ${e.message}")
                }
            }
        }

        // --- SINU ORIGINAALNE LOGIRIDA ---
        Log.i(TAG, """AUTOLE: Pealkiri: '$title' Esitaja: '$artist' Album:'$currentStationName' Lugu:$trackVersion / $trackVersion (Kokku)  Kestus: 300000ms UUID: ${extras.getString("android.media.metadata.MEDIA_ID")}""".trimIndent())
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
            // Kui mängija on "IDLE" olekus (tavaliselt pärast viga või pikka pausi)
            // ja ta peaks tegelikult mängima, siis kutsume prepare()
            if (playbackState == Player.STATE_IDLE && player.playWhenReady) {
                Log.i(TAG, "Mängija oli IDLE olekus, valmistame uuesti ette...")
                player.prepare()
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            updateNotification()


            if (isPlaying) {

                Log.i(TAG, "Player olek: MÄNGIB")

                saveToHistory(currentArtist, currentTitle)


                // 1. Teavitame äppi, et mängimine algas
                LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(
                    Intent(ACTION_STATION_CHANGED).apply {
                        putExtra(
                            "STATION_NAME",
                            currentStationName
                        )
                    }
                )

                // 2. Saadame kohe info äpile ja autole (kasutame globaalseid muutujaid)
                sendMetadataUpdate(currentTitle, currentArtist, currentExtra)
                updateExternalDevices(currentTitle, currentArtist)

                // 3. Kordussaatmine (Bluetoothi parandus)
                val stationAtTheMoment = currentStationName
                metadataPushJob?.cancel()
                metadataPushJob = serviceScope.launch {
                    delay(4000)
                    if (currentStationName == stationAtTheMoment) {
                        // Kasutame lihtsalt globaalseid muutujaid, mis on mälus olemas
                        withContext(Dispatchers.Main) {
                            updateExternalDevices(
                                currentTitle,
                                currentArtist
                            )
                        }
                    }

                    delay(11000)
                    if (currentStationName == stationAtTheMoment) {
                        withContext(Dispatchers.Main) {
                            updateExternalDevices(
                                currentTitle,
                                currentArtist
                            )
                        }
                    }
                }

            } else {
                Log.i(TAG, "Player olek: PEATATUD")
                LocalBroadcastManager.getInstance(applicationContext)
                    .sendBroadcast(Intent(ACTION_PLAYER_STOPPED))
                metadataPushJob?.cancel()
            }
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (playWhenReady) {
                // Kõne lõppes ja ExoPlayer tahab mängima hakata.
                // Sunnime teenuse kiiresti Foregroundi, et Android lubaks heli käivitada.
                val notification = buildPlayingNotification()
                if (Build.VERSION.SDK_INT >= 34) {
                    startForeground(
                        1,
                        notification,
                        android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                    )
                } else {
                    startForeground(1, notification)
                }
            }
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            Log.e(TAG, "Player Error: ${error.message} (kood: ${error.errorCode})")

            // PARANDUS: Automaatne taastumine kriitilistest vigadest (nt Bluetoothi konflikt / viga -38).
            // Tavaline player.prepare() siin ei aita, sest helikanal on "surnud".
            // Lahendus: Loome uue MediaItemi, mis sunnib Androidi avama uue puhta helikanali.
            serviceScope.launch {
                Log.i(TAG, "Viga tuvastatud. Ootan 2 sekundit ja laen striimi uuesti...")

                // Anname süsteemile aega (nt Bluetoothi ühendumiseks või võrgu taastumiseks)
                delay(2000)

                withContext(Dispatchers.Main) {
                    if (currentStreamUrl.isNotEmpty()) {
                        // Loome uue MediaItemi (Reload)
                        val mediaItem = MediaItem.Builder()
                            .setUri(currentStreamUrl)
                            .setMediaId("Raadio")
                            .setMediaMetadata(player.playlistMetadata) // Hoiame ekraani info alles
                            .build()

                        // Asendame vana katkise itemi uuega ja käivitame
                        player.setMediaItem(mediaItem)
                        player.prepare()
                        player.play()
                    } else {
                        // Varutvariant: proovime lihtsalt jätkata, kui URL puudub
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

        player = object : ForwardingPlayer(realPlayer) {
            override fun addListener(listener: Player.Listener) { listeners.add(listener); super.addListener(listener) }
            override fun removeListener(listener: Player.Listener) { listeners.remove(listener); super.removeListener(listener) }

            override fun getAvailableCommands(): Player.Commands {
                return super.getAvailableCommands().buildUpon()
                    .add(Player.COMMAND_SEEK_TO_NEXT).add(Player.COMMAND_SEEK_TO_PREVIOUS)
                    .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM).add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                    .add(Player.COMMAND_PLAY_PAUSE)
                    .build()
            }
            override fun isCommandAvailable(command: Int): Boolean {
                return when (command) {
                    Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_PREVIOUS,
                    Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                    Player.COMMAND_PLAY_PAUSE -> true
                    else -> super.isCommandAvailable(command)
                }
            }
            override fun getMediaMetadata(): MediaMetadata { return playlistMetadata }

            // --- SKODA BLUETOOTH AUDIO FIX 1: DURATION ---
            override fun getDuration(): Long { return 300000L } // 5 minutit

            // --- SKODA BLUETOOTH AUDIO FIX 2: FAKE PROGRESS ---
            // See on ülioluline! Auto peab nägema, et aeg jookseb.
            // Kui aeg seisab 0:00, siis arvab auto, et striim on katki.
            override fun getCurrentPosition(): Long {
                // Arvutame aja, mis on möödunud loo algusest
                val elapsed = SystemClock.elapsedRealtime() - streamStartTime
                // Teeme nii, et aeg jookseb 0..5min ringiratast
                return if (streamStartTime > 0) elapsed % 300000L else 0L
            }
            // --------------------------------------------------
        }

        mediaSession = MediaSession.Builder(this, player).setSessionActivity(PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)).setCallback(mediaSessionCallback).build()
        LocalBroadcastManager.getInstance(this).registerReceiver(statusReceiver, IntentFilter(ACTION_GET_STATUS))
        val powerManager = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        wakeLock = powerManager.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "Kellraadio::RadioWakeLock")
        wakeLock?.setReferenceCounted(false)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 1. Turvavõrk: Hangi WakeLock kohe alguses.
        // See tagab, et Android ei paneks protsessorit magama ajal, mil me alles otsustame, mida teha.
        // See on kriitiline äratuse (AlarmReceiver) toimimiseks sügava une (Doze mode) ajal.
        wakeLock?.acquire(10 * 60 * 1000L) // 10 minutit

        val action = intent?.action

        // --- PAUSI KÄSITLUS ---
        if (action == ACTION_PAUSE) {
            player.pause()

            // Vabastame WakeLocki, sest pausil olles pole vaja akut kulutada.
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }

            // Tühistame metaandmete kordussaatmise (et pausil olles ei tuleks uuendusi ja auto ei läheks segadusse).
            metadataPushJob?.cancel()

            // Uuendame teavitust (Notification), et seal ilmuks "Play" nupp "Pause" asemel.
            updateNotification()

            // START_STICKY tagab, et kui Android tapab teenuse mälu puuduses,
            // siis see luuakse uuesti (aga ei hakka ise mängima).
            return START_STICKY
        }

        // --- STOPP KÄSITLUS ---
        if (action == ACTION_STOP) {
            stopRadio()
            return START_NOT_STICKY // Teenust ei taastata automaatselt
        }

        // --- UNETAIMERI SEADMINE ---
        if (action == ACTION_SET_TIMER) {
            val duration = intent.getIntExtra(EXTRA_TIMER_DURATION, 0)
            startSleepTimer(duration)
            return START_STICKY
        }

        // --- UUE JAAMA KÄIVITAMINE ---
        val streamUrl = intent?.getStringExtra("STREAM_URL")
        val stationName = intent?.getStringExtra("STATION_NAME")
        val triggeredBy = intent?.getStringExtra("TRIGGERED_BY")
        val categoryParam = intent?.getStringExtra("CATEGORY_NAME")
        if (categoryParam != null) {
            currentCategory = categoryParam
        }

        if (streamUrl != null) {
            // Hangi WakeLock uuesti striimi laadimise ajaks.
            // Isegi kui eelmine lukk on veel peal, pikendab see kindlustunnet, et puhverdamise ajal levi ei kaoks.
            wakeLock?.acquire(10 * 60 * 1000L)

            Log.i(TAG, "Alustan jaama (onStartCommand): $stationName")

            // Salvestame globaalsed muutujad
            currentStreamUrl = streamUrl
            currentStationName = stationName ?: "Raadio"
            isAlarmMode = triggeredBy == "ALARM"

            // --- ÕIGE PARANDUS (BLUETOOTH FIX) ---
            // Määrame kohe alguses vaikeväärtused. See imiteerib olukorda,
            // nagu striim oleks juba saatnud tühja signaali.
            // See väldib olukorda, kus autos on vana laulu nimi.
            currentArtist = "Otseeeter"          // Title rida
            currentTitle = currentStationName    // Artist rida
            currentExtra = ""

            // Nullime loo versiooni loenduri uue jaama jaoks
            trackVersion = 1

            // Salvestame jaama ajalukku (kui pole äratus) või näitame äratuse teavitust
            if (isAlarmMode) {
                showSeparateAlarmNotification(currentStationName)
            } else {
                prefs.edit()
                    .putString("LAST_URL", currentStreamUrl)
                    .putString("LAST_NAME", currentStationName)
                    .apply()
            }

            currentTrackTitle = ""
            updateNotification() // Uuendab teavitust ("Mängib: Jaama Nimi")

            // Kui midagi juba mängis, siis peatame ja puhastame
            if (player.isPlaying) player.stop()
            player.clearMediaItems()

            // Valmistame ette metaandmed Playeri jaoks
            val extras = Bundle()
            // See UUID on siin extras sees pigem infoks, aga Queue sünkroonimiseks kasutame allpool kindlat ID-d.
            //extras.putString("android.media.metadata.MEDIA_ID", UUID.randomUUID().toString())
            extras.putString("android.media.metadata.MEDIA_ID", "Raadio")
            extras.putLong("android.media.metadata.DURATION", 300000L) // 5 minutit (Skoda fix)

            val initialMeta = MediaMetadata.Builder()
                .setTitle(currentStationName)
                .setDisplayTitle(currentStationName)
                .setArtist("Otseeeter")
                .setAlbumTitle(currentStationName)
                .setTrackNumber(1)
                .setTotalTrackCount(1) // ALATI 1, et auto ei näitaks "1/999"
                .setIsPlayable(true)
                .setExtras(extras)
                .build()

            // --- KRIITILINE PARANDUS (ID: RAADIO) ---
            // Kasutame MediaItemi loomisel fikseeritud ID-d "Raadio".
            // See tagab, et Bluetooth Queue ja Metadata ID-d klapivad alati.
            // See parandas vea: "Timeout while waiting for metadata to sync".
            player.setMediaItem(
                MediaItem.Builder()
                    .setUri(streamUrl)
                    .setMediaId("Raadio") // <--- SIIN ON VÕTI
                    .setMediaMetadata(initialMeta)
                    .build()
            )

            // Uuendame ka üldist playlisti infot
            player.playlistMetadata = initialMeta

            // Märgime aja alguse (et auto näeks progressi 0:00 -> ...)
            streamStartTime = SystemClock.elapsedRealtime()

            // Logime info, et näha, mis ID-d ja andmed autole lähevad
            Log.i(TAG, """AUTOLE (StartCommand): Pealkiri:'$currentStationName' Esitaja:'Otseeeter' Lugu: $trackVersion / 1 (Algseis) UUID:${extras.getString("android.media.metadata.MEDIA_ID")}""".trimIndent())

            // Käivitame heli
            player.prepare()
            player.play()

        }

        return START_STICKY
    }

    private fun updateNotification() {
        val notification = buildPlayingNotification()

        // MUUDATUS: Kui on äratus (isAlarmMode) VÕI raadio mängib, sunnime esiplaanile.
        // Äratuse puhul ei tohi me stopForeground teha, muidu Android tapab teenuse.
        if (player.isPlaying || isAlarmMode) {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            } else {
                startForeground(1, notification)
            }
        } else {
            // Ainult siis lõpetame esiplaani, kui EI OLE äratus ja raadio on pausil
            stopForeground(false)
            notificationManager.notify(1, notification)
        }
    }

    private fun stopRadio(isError: Boolean = false) {
        if (wakeLock?.isHeld == true) wakeLock?.release()
        stopSleepTimer()
        metadataPushJob?.cancel()
        player.stop()
        player.clearMediaItems()
        isAlarmMode = false
        notificationManager.cancel(100)
        streamStartTime = 0L // Nullime aja
        if (!isError) LocalBroadcastManager.getInstance(this).sendBroadcast(Intent(ACTION_PLAYER_STOPPED))
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildPlayingNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(NotificationChannel("PLAYING_RADIO_CHANNEL_v21", "Raadio", NotificationManager.IMPORTANCE_LOW))
        }
        val stopPendingIntent = PendingIntent.getService(this, 2, Intent(this, RadioService::class.java).apply { action = ACTION_STOP }, PendingIntent.FLAG_IMMUTABLE)
        //val title = if (isAlarmMode) "Äratus!" else (if (currentTrackTitle.isNotBlank()) currentTrackTitle else currentStationName)
        val title = if (isAlarmMode) "Äratus!" else (if (currentTitle.isNotBlank()) currentTitle else currentStationName)
        //val text = if (isAlarmMode) "Mängib $currentStationName" else currentStationName
        val text = if (isAlarmMode) "Mängib $currentStationName" else (if (currentArtist.isNotBlank()) currentArtist else currentStationName)
        val priority = if (isAlarmMode) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_LOW
        val icon = if (isAlarmMode) android.R.drawable.ic_lock_idle_alarm else R.drawable.ic_radio_notification

        return NotificationCompat.Builder(this, "PLAYING_RADIO_CHANNEL_v21")
            .setSmallIcon(icon).setContentTitle(title).setContentText(text)
            .setOngoing(true).setCategory(NotificationCompat.CATEGORY_ALARM).setPriority(priority)
            .setDefaults(if (isAlarmMode) Notification.DEFAULT_ALL else 0)
            .setContentIntent(mediaSession!!.sessionActivity)
            .setStyle(MediaStyleNotificationHelper.MediaStyle(mediaSession!!).setShowActionsInCompactView(0, 1, 2))
            .addAction(R.drawable.ic_skip_previous, "Eelmine", null)
            .addAction(R.drawable.ic_stop, "Stopp", stopPendingIntent)
            .addAction(R.drawable.ic_skip_next, "Järgmine", null)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC).build()
    }

    private fun showSeparateAlarmNotification(stationName: String) {
        val alarmChannelId = "ALARM_ALERT_CHANNEL_v21"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(NotificationChannel(alarmChannelId, "Äratuse märguanne", NotificationManager.IMPORTANCE_HIGH))
        }
        val openAppIntent = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val alarmNotification = NotificationCompat.Builder(this, alarmChannelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm).setContentTitle("Äratus!").setContentText("Mängib: $stationName")
            .setPriority(NotificationCompat.PRIORITY_HIGH).setCategory(NotificationCompat.CATEGORY_ALARM).setAutoCancel(true)
            .setContentIntent(openAppIntent).build()
        notificationManager.notify(100, alarmNotification)
    }

    private fun startSleepTimer(minutes: Int) {
        stopSleepTimer()
        if (minutes <= 0) { sendTimerTick(0); return }
        sleepTimerRemainingMillis = minutes * 60 * 1000L
        Log.i(TAG, "Unetaimer käivitatud: $minutes minutit")
        sleepTimerJob = serviceScope.launch(Dispatchers.Main) {
            while (sleepTimerRemainingMillis > 0) {
                sendTimerTick(sleepTimerRemainingMillis)
                delay(1000)
                sleepTimerRemainingMillis -= 1000
            }
            //sendTimerTick(0); stopRadio()
            Log.i(TAG, "Unetaimer lõppes. Panen pausile.")
            sendTimerTick(0)
            player.pause()            // Paneme vaikseks
            metadataPushJob?.cancel() // Lõpetame auto uuendamise
            updateNotification()      // Uuendame teavitust (ikoon muutub Play-ks)
            // Me EI kutsu stopRadio(), seega teenus ja teavitus jäävad alles
        }
    }

    private fun stopSleepTimer() { sleepTimerJob?.cancel(); sleepTimerJob = null; sleepTimerRemainingMillis = 0; sendTimerTick(0) }
    private fun sendTimerTick(remainingMillis: Long) {
        LocalBroadcastManager.getInstance(this).sendBroadcast(Intent(ACTION_TIMER_TICK).apply { putExtra("REMAINING_MILLIS", remainingMillis) })
    }

    private fun splitMetadata(raw: String): Triple<String, String, String> {
        val cleaned = raw.trim()

        // 1. Tühja info puhul (nagu Raadio Kadi algus)
        if (cleaned.isEmpty() || cleaned == "-" || cleaned == "." || cleaned == " -") {
            // TAGASTAB: (Artist, Title, Extra)
            // Artist = "Otseeeter", Title = Jaama nimi
            return Triple("Otseeeter", currentStationName, "")
        }

        val parts = cleaned.split(" - ", limit = 3)
        val artist: String
        val title: String
        val extra = if (parts.size >= 3) parts[2].trim() else ""

        if (parts.size >= 2) {
            artist = parts[0].trim()
            val t = parts[1].trim()
            // 2. Kui pealkiri on vigane, kasutame jaama nime
            title = if (t.equals(artist, ignoreCase = true) || t.isBlank()) currentStationName else t
        } else {
            // 3. Meil on ainult üks osa (nt saate nimi "Tarkade klubi")
            artist = cleaned
            // Paneme pealkirjaks jaama nime, et see oleks alati näha
            title = currentStationName
        }

        return Triple(artist, title, extra)
    }

    private fun saveToHistory(artist: String, title: String) {
        // Kontrollime ainult, et andmed poleks päris tühjad
        if (artist.isBlank() && title.isBlank()) return

        serviceScope.launch {
            try {
                val db = AppDatabase.getDatabase(applicationContext)
                val historyDao = db.historyDao()

                // Võtame viimase kirje, et vältida täpselt sama rea salvestamist topelt
                val lastItem = historyDao.getLatestItem()

                if (lastItem != null && lastItem.artist == artist && lastItem.title == title) {
                    return@launch // See on sama lugu, mis juba kirjas, ei salvesta uuesti
                }

                historyDao.insert(
                    HistoryItem(
                        stationName = currentStationName,
                        artist = artist,
                        title = title,
                        timestamp = System.currentTimeMillis()
                    )
                )
                historyDao.cleanOldHistory()
                Log.d(TAG, "History: Salvestatud ajalukku: $artist - $title")
            } catch (e: Exception) {
                Log.e(TAG, "History viga: ${e.message}")
            }
        }
    }

    override fun onDestroy() {
        if (wakeLock?.isHeld == true) wakeLock?.release() // Vabasta lukk
        Log.i(TAG, "Teenus suletud")
        LocalBroadcastManager.getInstance(this).unregisterReceiver(statusReceiver)
        player.release(); mediaSession?.release(); super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
}