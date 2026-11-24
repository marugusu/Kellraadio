package app.radiorecalarm.ui

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.first

import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import app.radiorecalarm.Alarm
import app.radiorecalarm.AlarmUtils
import app.radiorecalarm.AppDatabase
import app.radiorecalarm.R
import app.radiorecalarm.RadioBrowserApiService
import androidx.room.withTransaction
import app.radiorecalarm.RadioFilterItem
import app.radiorecalarm.RadioService
import app.radiorecalarm.RadioStation
import app.radiorecalarm.RadioStationRepository
import app.radiorecalarm.StationApiService
import app.radiorecalarm.StationListFilter
import app.radiorecalarm.backup.BackupCodec
import app.radiorecalarm.backup.BackupData
import app.radiorecalarm.backup.PendingBackupImport
import app.radiorecalarm.update.AppUpdateManager
import app.radiorecalarm.update.GitHubRelease
import app.radiorecalarm.update.GitHubReleaseAsset
import app.radiorecalarm.update.UpdateUiState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val prefs = context.getSharedPreferences("RaadioPrefs", Context.MODE_PRIVATE)

    val appVersion: String by lazy {
        try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            packageInfo.versionName ?: "1.0.0"
        } catch (e: Exception) {
            "1.0.0"
        }
    }

    val updateManager = AppUpdateManager(context)
    private var downloadJob: Job? = null
    private var cachedUpdateResult: UpdateUiState? = null
    private var hasShownAutoUpdatePopup = false

    private val database = AppDatabase.getDatabase(context)
    val stationRepository = RadioStationRepository(
        StationApiService.create(),
        RadioBrowserApiService.create(),
        database.radioStationDao(),
        database.historyDao()
    )
    private val alarmDao = database.alarmDao()

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState = _uiState.asStateFlow()

    private val radioReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                RadioService.ACTION_STATION_CHANGED -> {
                    val name = intent.getStringExtra("STATION_NAME") ?: ""
                    val serviceCategory = intent.getStringExtra("CATEGORY_NAME")
                    val isLocal = intent.getBooleanExtra("IS_LOCAL", false)
                    if (isLocal) {
                        val streamUrl = intent.getStringExtra("STREAM_URL")
                        val isPlayingExtra = if (intent.hasExtra("IS_PLAYING")) {
                            intent.getBooleanExtra("IS_PLAYING", true)
                        } else {
                            true
                        }
                        _uiState.update {
                            app.radiorecalarm.PlaybackStatusSync.applyLocalStationChanged(
                                current = it,
                                stationName = name,
                                streamUrl = streamUrl,
                                isPlaying = isPlayingExtra,
                                playingStatusText = getString(R.string.status_playing),
                                pausedStatusText = getString(R.string.status_stopped)
                            )
                        }
                        return
                    }
                    
                    var newId = _uiState.value.selectedStationId
                    var newCat = _uiState.value.selectedCategory
                    
                    val foundStation = _uiState.value.stations.find { it.name == name }
                    if (foundStation != null) {
                        newId = foundStation.id
                        
                        // PARANDUS: Kui service ütleb, mis kategoorias me oleme, siis usume seda.
                        // See lahendab vea, kus äpp "unustas" et me olime Favorites all.
                        if (!serviceCategory.isNullOrEmpty()) {
                            newCat = serviceCategory
                        } else {
                            // Tagavaravariant, kui service ei saatnud kategooriat (nt vana kood)
                            if (newCat != "Favorites" && newCat != "My" && newCat != "All" && newCat != foundStation.countryCode) {
                                newCat = if (foundStation.countryCode.isEmpty()) "All" else foundStation.countryCode
                            }
                        }
                        
                        // Kui kategooria muutus, kutsume esile ka vajalikud laadimised
                        if (newCat != _uiState.value.selectedCategory) {
                            onCategorySelected(newCat)
                        }
                        
                        prefs.edit().putInt("last_selected_id", newId).apply()
                    }
                    
                    _uiState.update { it.copy(
                        isPlaying = true,
                        playerStatus = getString(R.string.status_playing),
                        activeStationName = name,
                        activeStreamUrl = foundStation?.url ?: it.activeStreamUrl,
                        selectedStationId = newId,
                        selectedCategory = newCat
                    )}
                }
                RadioService.ACTION_METADATA_UPDATED -> {
                    val title = intent.getStringExtra("PARSED_TITLE") ?: ""
                    val artist = intent.getStringExtra("PARSED_ARTIST") ?: ""
                    val extra = intent.getStringExtra("PARSED_EXTRA") ?: ""
                    _uiState.update { it.copy(parsedTitle = title, parsedArtist = artist, parsedExtra = extra) }
                }
                RadioService.ACTION_BITRATE_UPDATED -> {
                    val bitrate = intent.getStringExtra("BITRATE_INFO") ?: ""
                    _uiState.update { it.copy(bitrate = bitrate) }
                }
                RadioService.ACTION_PLAYER_ERROR -> {
                    _uiState.update { it.copy(
                        isPlaying = false,
                        playerStatus = getString(R.string.status_error),
                        bitrate = "",
                        playbackPosition = 0L,
                        playbackDuration = 0L
                    ) }
                    Toast.makeText(context, getString(R.string.error_station_not_found), Toast.LENGTH_LONG).show()
                }
                RadioService.ACTION_PLAYER_STOPPED -> {
                    _uiState.update { it.copy(
                        isPlaying = false,
                        isMutedRecording = false,
                        playerStatus = getString(R.string.status_stopped),
                        bitrate = "",
                        activeStreamUrl = "",
                        playbackPosition = 0L,
                        playbackDuration = 0L
                    )}
                }
                RadioService.ACTION_PLAYER_PAUSED -> {
                    _uiState.update { it.copy(
                        isPlaying = false,
                        isMutedRecording = false,
                        playerStatus = getString(R.string.status_stopped)
                    )}
                }
                RadioService.ACTION_PLAYBACK_PROGRESS -> {
                    val position = intent.getLongExtra("PLAYBACK_POSITION", 0L)
                    val duration = intent.getLongExtra("PLAYBACK_DURATION", 0L)
                    _uiState.update { it.copy(playbackPosition = position, playbackDuration = duration) }
                }
                RadioService.ACTION_TIMER_TICK -> {
                    val remaining = intent.getLongExtra("REMAINING_MILLIS", 0L)
                    _uiState.update { it.copy(sleepTimerRemaining = remaining) }
                }
                RadioService.ACTION_STATION_SELECTED_BY_SERVICE -> {
                    val stationId = intent.getIntExtra("STATION_ID", -1)
                    if (stationId != -1) updateSelectedStationLocal(stationId)
                }
                "app.radiorecalarm.RECORDING_STATUS" -> {
                    val isRec = intent.getBooleanExtra("IS_RECORDING", false)
                    val isMutedRec = intent.getBooleanExtra("IS_MUTED_RECORDING", false)
                    val dur = intent.getLongExtra("RECORDING_DURATION", 0L)
                    val station = intent.getStringExtra("RECORDING_STATION") ?: ""
                    val track = intent.getStringExtra("RECORDING_TRACK") ?: ""
                    val count = intent.getIntExtra("RECORDING_COUNT", 0)
                    val activeFile = intent.getStringExtra("RECORDING_ACTIVE_FILE") ?: ""
                    _uiState.update { current ->
                        val wasMutedRec = current.isMutedRecording
                        val nextIsPlaying = when {
                            isMutedRec -> false
                            wasMutedRec && !isMutedRec && current.activeStreamUrl.isNotEmpty() -> true
                            else -> current.isPlaying
                        }
                        val nextStatus = when {
                            isMutedRec -> getString(R.string.status_recording_muted_background)
                            wasMutedRec && !isMutedRec && nextIsPlaying -> getString(R.string.status_playing)
                            else -> current.playerStatus
                        }
                        current.copy(
                            isRecording = isRec,
                            isMutedRecording = isMutedRec,
                            isPlaying = nextIsPlaying,
                            playerStatus = nextStatus,
                            recordingDuration = dur,
                            recordingStation = station,
                            recordingTrack = track,
                            recordingCount = count,
                            activeRecordingFilePath = activeFile
                        )
                    }
                }
            }
        }
    }

    init {
        val filter = IntentFilter().apply {
            addAction(RadioService.ACTION_STATION_SELECTED_BY_SERVICE)
            addAction(RadioService.ACTION_STATION_CHANGED)
            addAction(RadioService.ACTION_METADATA_UPDATED)
            addAction(RadioService.ACTION_BITRATE_UPDATED)
            addAction(RadioService.ACTION_PLAYER_ERROR)
            addAction(RadioService.ACTION_PLAYER_STOPPED)
            addAction(RadioService.ACTION_TIMER_TICK)
            addAction("app.radiorecalarm.RECORDING_STATUS")
            addAction(RadioService.ACTION_PLAYBACK_PROGRESS)
            addAction(RadioService.ACTION_PLAYER_PAUSED)
        }
        LocalBroadcastManager.getInstance(context).registerReceiver(radioReceiver, filter)

        loadPreferences()

        viewModelScope.launch(Dispatchers.Default) {
            stationRepository.allStations.collect { stations ->
                val allCategories = stations
                    .filter { !(it.isUserStation && it.category.trim().equals(StationListFilter.CATEGORY_MY, ignoreCase = true)) }
                    .map { it.category.trim() }
                    .filter { it.isNotBlank() }
                    .distinct()
                    .sorted()
                _uiState.update { currentState ->
                    currentState.copy(stations = stations, allCategories = allCategories)
                }
                updateCountries()
            }
        }

        viewModelScope.launch {
            alarmDao.getAllAlarms().collect { alarms -> _uiState.update { it.copy(alarms = alarms) } }
        }

        LocalBroadcastManager.getInstance(context).sendBroadcast(Intent(RadioService.ACTION_GET_STATUS))

        healAlarmSchedule()

        viewModelScope.launch(Dispatchers.IO) {
            stationRepository.loadOfflineStationsIfEmpty(context)
            val stations = stationRepository.allStations.first()
            val lastUpdate = prefs.getLong("last_update_time", 0L)
            val oneDayMillis = 24 * 60 * 60 * 1000L
            val isExpired = (System.currentTimeMillis() - lastUpdate) > oneDayMillis
            if (stations.isEmpty() || isExpired) {
                refreshStations(isManual = false)
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                Log.d("MainViewModel", "Käivitan taustal uuenduste kontrolli: appVersion=$appVersion")
                val updateResult = updateManager.checkForUpdate(appVersion)
                Log.d("MainViewModel", "Taustal uuenduste kontrolli tulemus: $updateResult")
                if (updateResult is UpdateUiState.UpdateAvailable || updateResult is UpdateUiState.ReadyToInstall) {
                    cachedUpdateResult = updateResult
                    _uiState.update { current ->
                        val shouldShowDialog = current.currentTab == 4 && !hasShownAutoUpdatePopup
                        if (shouldShowDialog) {
                            hasShownAutoUpdatePopup = true
                        }
                        current.copy(
                            hasAvailableUpdate = true,
                            updateState = if (shouldShowDialog) updateResult else current.updateState
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Viga taustal uuenduste kontrollimisel", e)
            }
        }
    }

    /**
     * Rakenduse käivitamisel tehtav vaikne isetervenemine: loeb andmebaasist aktiivsed äratused,
     * registreerib järgmise äratuse uuesti süsteemses AlarmManageris ja uuendab avakuva vidina.
     * Nii kaob igasugune lahknevus süsteemi ja andmebaasi seisu vahel (nt kui OEM-i akuhaldur
     * on registreeringu kustutanud). Töötab taustalõimes, ei blokeeri UI-d ega näita Toaste.
     */
    private fun healAlarmSchedule() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                AlarmUtils.scheduleNextAlarmInternal(context)
                app.radiorecalarm.widget.HomeWidgetUpdater.update(context)
                Log.d("MainViewModel", "Äratuste isetervenemine käivitusel lõpetatud.")
            } catch (e: Exception) {
                Log.e("MainViewModel", "Viga äratuste isetervenemisel käivitusel", e)
            }
        }
    }

    private fun loadPreferences() {
        _uiState.update { it.copy(
            selectedCategory = prefs.getString("last_category", "Favorites") ?: "Favorites",
            selectedSubCategories = prefs.getStringSet("last_sub_categories", emptySet())?.toSet() ?: emptySet(),
            selectedStationId = prefs.getInt("last_selected_id", -1),
            colsPortrait = prefs.getInt("cols_portrait", 3),
            colsLandscape = prefs.getInt("cols_landscape", 3),
            showFlags = prefs.getBoolean("show_flags", true),
            widgetTransparency = prefs.getFloat("widget_transparency", 0.25f),
            hideRemoteStations = prefs.getBoolean("hide_remote_stations", false),
            continuousRecording = prefs.getBoolean(app.radiorecalarm.AppConfig.Recording.PREF_CONTINUOUS_RECORDING, false),
            minTrackDurationSeconds = prefs.getLong(
                app.radiorecalarm.AppConfig.Recording.PREF_MIN_DURATION_SECONDS,
                app.radiorecalarm.AppConfig.Recording.MIN_DURATION_SECONDS
            ),
            preRollSeconds = app.radiorecalarm.AppConfig.Recording.resolvePreRollSeconds(prefs),
            postRollSeconds = app.radiorecalarm.AppConfig.Recording.resolvePostRollSeconds(prefs)
        )}
    }

    fun updateCountries() {
        viewModelScope.launch(Dispatchers.Default) {
            _uiState.update { it.copy(isCountriesLoading = true) }
            val remoteCountries = stationRepository.getCountries().toMutableList()
            val localCountryCodes = _uiState.value.stations
                .map { it.countryCode }
                .filter { it.isNotBlank() }
                .distinct()

            var listWasModified = false
            localCountryCodes.forEach { code ->
                if (remoteCountries.none { it.name.equals(code, ignoreCase = true) || (it.isoCode != null && it.isoCode.equals(code, ignoreCase = true)) }) {
                    val upperCaseCode = code.uppercase()
                    remoteCountries.add(RadioFilterItem(name = upperCaseCode, stationCount = 1, isoCode = upperCaseCode))
                    listWasModified = true
                }
            }

            if (listWasModified) {
                remoteCountries.sortBy { it.name }
            }

            _uiState.update { it.copy(countries = remoteCountries, isCountriesLoading = false) }
        }
    }

    fun onTabSelected(index: Int) {
        val pendingCategory = _uiState.value.categoryToSelectOnTabChange
        if (index == 0 && pendingCategory != null) {
            onCategorySelected(pendingCategory)
            _uiState.update { it.copy(currentTab = index, categoryToSelectOnTabChange = null, isReorderMode = false) }
        } else {
            _uiState.update { it.copy(currentTab = index, isReorderMode = if (index == 0) it.isReorderMode else false) }
        }
        if (index == 4 && _uiState.value.hasAvailableUpdate && !hasShownAutoUpdatePopup && _uiState.value.updateState is UpdateUiState.Idle) {
            hasShownAutoUpdatePopup = true
            cachedUpdateResult?.let { updateState ->
                _uiState.update { it.copy(updateState = updateState) }
            }
        }
    }

    fun onCategorySelected(category: String) {
        _uiState.update { it.copy(
            selectedCategory = category,
            selectedSubCategories = emptySet(),
            isReorderMode = if (category == StationListFilter.CATEGORY_FAVORITES) it.isReorderMode else false
        )}
        prefs.edit()
            .putString("last_category", category)
            .putStringSet("last_sub_categories", emptySet())
            .apply()
    }

    fun toggleSubCategory(sub: String) {
        var updatedSet = emptySet<String>()
        _uiState.update { currentState ->
            val newSet = if (currentState.selectedSubCategories.contains(sub)) {
                currentState.selectedSubCategories - sub
            } else {
                currentState.selectedSubCategories + sub
            }
            updatedSet = newSet
            currentState.copy(selectedSubCategories = newSet)
        }
        prefs.edit().putStringSet("last_sub_categories", updatedSet).apply()
    }

    fun setSubCategories(subCategories: Set<String>) {
        if (_uiState.value.selectedSubCategories == subCategories) return
        _uiState.update { it.copy(selectedSubCategories = subCategories) }
        prefs.edit().putStringSet("last_sub_categories", subCategories).apply()
    }

    fun onStationClicked(station: RadioStation) {
        val freshStation = _uiState.value.stations.find { it.id == station.id } ?: station
        updateSelectedStationLocal(freshStation.id)
        _uiState.update { it.copy(
            activeStationName = freshStation.name,
            activeStreamUrl = freshStation.url,
            isPlaying = true,
            playerStatus = getString(R.string.status_buffering)
        )}
        startRadioService(freshStation)
    }

    fun onPlayPauseClicked() {
        if (_uiState.value.isPlaying) {
            val i = Intent(context, RadioService::class.java).apply { action = RadioService.ACTION_PAUSE }
            context.startService(i)
        } else if (_uiState.value.isMutedRecording) {
            _uiState.update { it.copy(isPlaying = true, isMutedRecording = false, playerStatus = getString(R.string.status_playing)) }
            val i = Intent(context, RadioService::class.java).apply { action = RadioService.ACTION_RESUME }
            context.startService(i)
        } else {
            val isLocal = app.radiorecalarm.PlaybackStatusSync.isLocalStreamUri(_uiState.value.activeStreamUrl)
            if (isLocal) {
                _uiState.update { it.copy(isPlaying = true, playerStatus = getString(R.string.status_buffering)) }
                val i = Intent(context, RadioService::class.java).apply { action = RadioService.ACTION_RESUME }
                context.startService(i)
            } else {
                val station = _uiState.value.stations.find { it.id == _uiState.value.selectedStationId }
                if (station != null) {
                    _uiState.update { it.copy(
                        activeStationName = station.name,
                        activeStreamUrl = station.url,
                        isPlaying = true,
                        playerStatus = getString(R.string.status_buffering)
                    )}
                    startRadioService(station)
                } else {
                    Toast.makeText(context, getString(R.string.select_station), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun onRecordClicked() {
        val action = if (_uiState.value.isRecording) {
            "app.radiorecalarm.ACTION_STOP_RECORDING"
        } else {
            "app.radiorecalarm.ACTION_START_RECORDING"
        }
        val i = Intent(context, RadioService::class.java).apply { this.action = action }
        context.startService(i)
    }

    fun onPlayRecordingClicked(entry: app.radiorecalarm.RecordingEntry) {
        val fileUri = android.net.Uri.fromFile(entry.audioFile).toString()
        val displayStation = entry.stationName.ifBlank { getString(R.string.recording_default_station) }
        val displayTitle = entry.title.ifBlank { entry.audioFile.nameWithoutExtension }
        val displayArtist = entry.artist
        val displayExtra = entry.album ?: ""
        _uiState.update { it.copy(
            activeStationName = displayStation,
            activeStreamUrl = fileUri,
            selectedStationId = -1,
            parsedTitle = displayTitle,
            parsedArtist = displayArtist,
            parsedExtra = displayExtra,
            isPlaying = true,
            playerStatus = getString(R.string.status_buffering),
            playbackPosition = 0L,
            playbackDuration = (entry.durationSeconds * 1000L).coerceAtLeast(0L)
        )}
        val i = Intent(context, RadioService::class.java).apply {
            putExtra("STREAM_URL", fileUri)
            putExtra("STATION_NAME", displayStation)
            putExtra(RadioService.EXTRA_TRACK_TITLE, displayTitle)
            putExtra(RadioService.EXTRA_TRACK_ARTIST, displayArtist)
            putExtra("TRIGGERED_BY", "USER")
        }
        context.startForegroundService(i)
    }

    fun seekTo(positionMs: Long) {
        val clampedPositionMs = app.radiorecalarm.PlaybackStatusSync.clampSeekPosition(
            positionMs = positionMs,
            durationMs = _uiState.value.playbackDuration
        )
        _uiState.update { it.copy(playbackPosition = clampedPositionMs) }
        val i = Intent(context, RadioService::class.java).apply {
            action = RadioService.ACTION_SEEK
            putExtra(RadioService.EXTRA_SEEK_POSITION, clampedPositionMs)
        }
        context.startService(i)
    }

    fun onDeleteRecording(file: java.io.File) {
        val fileUri = android.net.Uri.fromFile(file).toString()
        if (_uiState.value.activeStreamUrl == fileUri) {
            val i = Intent(context, RadioService::class.java).apply { action = RadioService.ACTION_STOP }
            context.startService(i)
            _uiState.update { it.copy(
                isPlaying = false,
                playerStatus = getString(R.string.status_stopped),
                bitrate = "",
                activeStreamUrl = "",
                parsedTitle = "",
                parsedArtist = "",
                parsedExtra = "",
                playbackPosition = 0L,
                playbackDuration = 0L
            )}
        }
        try {
            val rootDir = java.io.File(context.getExternalFilesDir(null), app.radiorecalarm.AppConfig.Recording.DEFAULT_AUDIO_DIR)
            if (app.radiorecalarm.RecordingLibrary.deleteRecordingWithCleanup(file, rootDir)) {
                Toast.makeText(context, getString(R.string.recording_toast_deleted), Toast.LENGTH_SHORT).show()
            }
        } catch (e: java.lang.Exception) {
            android.util.Log.e("MainViewModel", "Faili kustutamise viga: ${e.message}")
        }
    }

    fun onHistoryStationClicked(stationName: String) {
        val station = _uiState.value.stations.find { it.name == stationName }
        if (station != null) {
            updateSelectedStationLocal(station.id)
            if (_uiState.value.selectedCategory != "Favorites" && _uiState.value.selectedCategory != "My" && _uiState.value.selectedCategory != station.countryCode) {
                onCategorySelected(if (station.countryCode.isEmpty()) "All" else station.countryCode)
            }
            _uiState.update { it.copy(
                activeStationName = station.name,
                activeStreamUrl = station.url,
                isPlaying = true,
                playerStatus = getString(R.string.status_buffering)
            )}
            startRadioService(station)
            onTabSelected(0)
        } else {
            Toast.makeText(context, getString(R.string.error_station_not_found), Toast.LENGTH_SHORT).show()
        }
    }

    fun onToggleFavorite(station: RadioStation) {
        viewModelScope.launch {
            stationRepository.toggleFavorite(station)
        }
    }

    fun moveStationUp(station: RadioStation) {
        viewModelScope.launch {
            val favorites = _uiState.value.stations.filter { it.isFavorite }.sortedWith(compareBy<RadioStation> { it.favoriteOrder }.thenBy { it.priority }.thenBy { it.name })
            stationRepository.moveStationUp(station, favorites)
        }
    }

    fun moveStationDown(station: RadioStation) {
        viewModelScope.launch {
            val favorites = _uiState.value.stations.filter { it.isFavorite }.sortedWith(compareBy<RadioStation> { it.favoriteOrder }.thenBy { it.priority }.thenBy { it.name })
            stationRepository.moveStationDown(station, favorites)
        }
    }

    fun moveStationToTop(station: RadioStation) {
        viewModelScope.launch {
            val favorites = _uiState.value.stations.filter { it.isFavorite }.sortedWith(compareBy<RadioStation> { it.favoriteOrder }.thenBy { it.priority }.thenBy { it.name })
            stationRepository.moveStationToTop(station, favorites)
        }
    }

    fun moveStationToBottom(station: RadioStation) {
        viewModelScope.launch {
            val favorites = _uiState.value.stations.filter { it.isFavorite }.sortedWith(compareBy<RadioStation> { it.favoriteOrder }.thenBy { it.priority }.thenBy { it.name })
            stationRepository.moveStationToBottom(station, favorites)
        }
    }

    fun enterReorderMode() {
        _uiState.update {
            it.copy(
                selectedCategory = StationListFilter.CATEGORY_FAVORITES,
                selectedSubCategories = emptySet(),
                isReorderMode = true,
                showActionSheetForStation = null
            )
        }
        prefs.edit()
            .putString("last_category", StationListFilter.CATEGORY_FAVORITES)
            .putStringSet("last_sub_categories", emptySet())
            .apply()
    }

    fun exitReorderMode() {
        _uiState.update { it.copy(isReorderMode = false) }
    }

    fun saveFavoritesOrder(orderedFavorites: List<RadioStation>) {
        viewModelScope.launch {
            stationRepository.saveFavoritesOrder(orderedFavorites)
        }
    }

    fun openResetOrderDialog() { _uiState.update { it.copy(showResetOrderDialog = true) } }
    fun closeResetOrderDialog() { _uiState.update { it.copy(showResetOrderDialog = false) } }
    
    fun resetFavoriteOrder() {
        viewModelScope.launch {
            stationRepository.resetFavoriteOrder()
            Toast.makeText(context, getString(R.string.toast_order_reset), Toast.LENGTH_SHORT).show()
            closeResetOrderDialog()
        }
    }

    fun openSleepTimerDialog() { _uiState.update { it.copy(showSleepDialog = true) } }
    fun closeSleepTimerDialog() { _uiState.update { it.copy(showSleepDialog = false) } }

    fun openAlarmDialog(alarm: Alarm? = null) {
        if (alarm == null && _uiState.value.selectedStationId == -1) {
            Toast.makeText(context, getString(R.string.select_station), Toast.LENGTH_SHORT).show()
            return
        }
        _uiState.update { it.copy(showAlarmDialog = true, alarmToEdit = alarm) }
    }
    fun closeAlarmDialog() { _uiState.update { it.copy(showAlarmDialog = false, alarmToEdit = null) } }

    fun openStationActionSheet(station: RadioStation) { _uiState.update { it.copy(showActionSheetForStation = station) } }
    fun closeStationActionSheet() { _uiState.update { it.copy(showActionSheetForStation = null) } }

    fun openSongInfo() { _uiState.update { it.copy(showSongInfoSheet = true) } }
    fun closeSongInfo() { _uiState.update { it.copy(showSongInfoSheet = false) } }

    fun confirmDeleteStation(station: RadioStation) { _uiState.update { it.copy(stationToDelete = station) } }
    fun cancelDeleteStation() { _uiState.update { it.copy(stationToDelete = null) } }

    fun openEditStationDialog(station: RadioStation) {
        closeStationActionSheet()
        _uiState.update { it.copy(stationToEdit = station) }
    }
    fun closeEditStationDialog() { _uiState.update { it.copy(stationToEdit = null) } }

    fun saveAlarm(hour: Int, minute: Int, days: Set<Int>, recordAudio: Boolean = false) {
        val alarmToEdit = _uiState.value.alarmToEdit
        val station = if (alarmToEdit != null) {
            _uiState.value.stations.find { it.name == alarmToEdit.stationName }
        } else {
            _uiState.value.stations.find { it.id == _uiState.value.selectedStationId }
        } ?: return

        val alarm = alarmToEdit?.copy(
            hour = hour, minute = minute, days = days,
            stationName = station.name, stationUrl = station.url, isEnabled = true,
            recordAudio = recordAudio
        ) ?: Alarm(
            hour = hour, minute = minute, days = days,
            stationName = station.name, stationUrl = station.url,
            recordAudio = recordAudio
        )

        AlarmUtils.saveOrUpdateAlarm(context, alarm)
        closeAlarmDialog()
    }

    fun toggleAlarm(alarm: Alarm) {
        AlarmUtils.saveOrUpdateAlarm(context, alarm.copy(isEnabled = !alarm.isEnabled), showToast = false)
    }

    fun deleteAlarm(alarm: Alarm) {
        AlarmUtils.deleteAlarm(context, alarm)
        closeAlarmDialog()
    }

    fun deleteUserStation() {
        val station = _uiState.value.stationToDelete
        if (station != null) {
            viewModelScope.launch {
                stationRepository.deleteStation(station)
                Toast.makeText(context, getString(R.string.station_toast_deleted), Toast.LENGTH_SHORT).show()
                cancelDeleteStation()
                closeStationActionSheet()
            }
        }
    }

    fun saveUserStation(name: String, url: String, countryCode: String, category: String, iconUrl: String = "") {
        val trimmedName = name.trim()
        val trimmedUrl = url.trim()
        val trimmedCountry = countryCode.trim()
        val trimmedCategory = category.trim()
        val trimmedIconUrl = iconUrl.trim()

        if (!isValidUrl(trimmedUrl)) {
            Toast.makeText(context, getString(R.string.error_invalid_url), Toast.LENGTH_SHORT).show()
            return
        }
        
        viewModelScope.launch {
            val newId = stationRepository.saveUserStation(trimmedName, trimmedUrl, trimmedCountry, trimmedCategory, trimmedIconUrl)
            Toast.makeText(context, context.getString(R.string.station_added, trimmedName), Toast.LENGTH_SHORT).show()

            val currentCat = _uiState.value.selectedCategory
            val currentSubCategories = _uiState.value.selectedSubCategories
            val staysVisibleInCurrentTab = currentCat == StationListFilter.CATEGORY_ALL ||
                currentCat == StationListFilter.CATEGORY_MY ||
                (trimmedCountry.isNotEmpty() && currentCat == trimmedCountry)
            val visibleUnderSubFilter = currentSubCategories.isEmpty() ||
                (trimmedCategory.isNotEmpty() && currentSubCategories.contains(trimmedCategory))
            if (!staysVisibleInCurrentTab || !visibleUnderSubFilter) {
                _uiState.update { it.copy(categoryToSelectOnTabChange = StationListFilter.CATEGORY_MY) }
            }

            if (_uiState.value.activeStreamUrl == trimmedUrl && _uiState.value.isPlaying) {
                _uiState.update { it.copy(
                    selectedStationId = newId,
                    activeStationName = trimmedName
                )}
                prefs.edit().putInt("last_selected_id", newId).putString("last_name", trimmedName).apply()
                val i = Intent(RadioService.ACTION_STATION_CHANGED).apply {
                    putExtra("STATION_NAME", trimmedName)
                }
                LocalBroadcastManager.getInstance(context).sendBroadcast(i)
            }
        }
    }

    fun refreshStations(isManual: Boolean = true) {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            try {
                val result = stationRepository.refreshStations()
                if (result.isSuccess) {
                    prefs.edit().putLong("last_update_time", System.currentTimeMillis()).apply()
                    val message = context.getString(R.string.toast_stations_updated, result.serverCount, result.userCount)
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                } else if (isManual) {
                    val errorMsg = context.getString(R.string.toast_stations_update_failed)
                    Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                if (isManual) {
                    val errorMsg = context.getString(R.string.toast_stations_update_failed)
                    Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
                }
            } finally {
                _uiState.update { it.copy(isRefreshing = false) }
            }
        }
    }

    fun setColsPortrait(cols: Int) {
        _uiState.update { it.copy(colsPortrait = cols) }
        prefs.edit().putInt("cols_portrait", cols).apply()
    }

    fun setColsLandscape(cols: Int) {
        _uiState.update { it.copy(colsLandscape = cols) }
        prefs.edit().putInt("cols_landscape", cols).apply()
    }

    fun toggleFlags(show: Boolean) {
        _uiState.update { it.copy(showFlags = show) }
        prefs.edit().putBoolean("show_flags", show).apply()
    }

    fun toggleHideRemoteStations(hide: Boolean) {
        _uiState.update { it.copy(hideRemoteStations = hide) }
        prefs.edit().putBoolean("hide_remote_stations", hide).apply()
    }

    fun setWidgetTransparency(value: Float) {
        _uiState.update { it.copy(widgetTransparency = value) }
        prefs.edit().putFloat("widget_transparency", value).apply()
        viewModelScope.launch(Dispatchers.IO) {
            app.radiorecalarm.widget.HomeWidgetUpdater.update(context)
        }
    }

    fun setContinuousRecording(enabled: Boolean) {
        _uiState.update { it.copy(continuousRecording = enabled) }
        prefs.edit().putBoolean(app.radiorecalarm.AppConfig.Recording.PREF_CONTINUOUS_RECORDING, enabled).apply()
    }

    fun setMinTrackDurationSeconds(seconds: Long) {
        val safeSeconds = seconds.coerceIn(0L, app.radiorecalarm.AppConfig.Recording.MIN_DURATION_MAX_SECONDS)
        _uiState.update { it.copy(minTrackDurationSeconds = safeSeconds) }
        prefs.edit().putLong(app.radiorecalarm.AppConfig.Recording.PREF_MIN_DURATION_SECONDS, safeSeconds).apply()
    }

    fun setPreRollSeconds(seconds: Long) {
        val safeSeconds = seconds.coerceIn(0L, app.radiorecalarm.AppConfig.Recording.PRE_ROLL_MAX_SECONDS)
        _uiState.update { it.copy(preRollSeconds = safeSeconds) }
        prefs.edit().putLong(app.radiorecalarm.AppConfig.Recording.PREF_PRE_ROLL_SECONDS, safeSeconds).apply()
    }

    fun setPostRollSeconds(seconds: Long) {
        val safeSeconds = seconds.coerceIn(0L, app.radiorecalarm.AppConfig.Recording.POST_ROLL_MAX_SECONDS)
        _uiState.update { it.copy(postRollSeconds = safeSeconds) }
        prefs.edit().putLong(app.radiorecalarm.AppConfig.Recording.PREF_POST_ROLL_SECONDS, safeSeconds).apply()
    }

    fun clearHistory() {
        viewModelScope.launch { stationRepository.clearHistory() }
    }

    fun updateUserStation(newName: String, newUrl: String, newCountryCode: String, newCategory: String, newIconUrl: String = "") {
        val trimmedName = newName.trim()
        val trimmedUrl = newUrl.trim()
        val trimmedCountryCode = newCountryCode.trim()
        val trimmedCategory = newCategory.trim()
        val trimmedIconUrl = newIconUrl.trim()

        if (!isValidUrl(trimmedUrl)) {
            Toast.makeText(context, getString(R.string.error_invalid_url), Toast.LENGTH_SHORT).show()
            return
        }

        val stationToUpdate = _uiState.value.stationToEdit
        if (stationToUpdate != null) {
            viewModelScope.launch {
                val oldName = stationToUpdate.name
                val oldUrl = stationToUpdate.url
                stationRepository.updateUserStation(stationToUpdate, trimmedName, trimmedUrl, trimmedCountryCode, trimmedCategory, trimmedIconUrl)

                val identityChanged = trimmedName != oldName || trimmedUrl != oldUrl
                if (identityChanged) {
                    val matchingAlarms = alarmDao.findAlarmsForStation(oldName, oldUrl)
                    if (matchingAlarms.isNotEmpty()) {
                        matchingAlarms.forEach { alarm ->
                            alarmDao.update(alarm.copy(stationName = trimmedName, stationUrl = trimmedUrl))
                        }
                        AlarmUtils.scheduleNextAlarmInternal(context)
                    }
                }

                val isSelectedStation = stationToUpdate.id == _uiState.value.selectedStationId ||
                    (_uiState.value.activeStreamUrl == oldUrl && oldUrl.isNotEmpty())
                if (isSelectedStation) {
                    if (prefs.getInt("last_selected_id", -1) == stationToUpdate.id) {
                        prefs.edit()
                            .putString("last_name", trimmedName)
                            .putString("last_url", trimmedUrl)
                            .apply()
                    }
                    if (trimmedUrl != oldUrl && _uiState.value.isPlaying) {
                        onStationClicked(
                            stationToUpdate.copy(
                                name = trimmedName,
                                url = trimmedUrl,
                                countryCode = trimmedCountryCode,
                                category = trimmedCategory
                            )
                        )
                    } else {
                        _uiState.update { it.copy(
                            activeStationName = trimmedName,
                            activeStreamUrl = if (it.activeStreamUrl == oldUrl) trimmedUrl else it.activeStreamUrl
                        )}
                        if (_uiState.value.isPlaying) {
                            val serviceIntent = Intent(context, RadioService::class.java).apply {
                                action = RadioService.ACTION_UPDATE_STATION_NAME
                                putExtra("STATION_NAME", trimmedName)
                                putExtra("STREAM_URL", trimmedUrl)
                            }
                            context.startService(serviceIntent)
                        } else {
                            app.radiorecalarm.widget.HomeWidgetUpdater.update(context)
                        }
                    }
                } else if (identityChanged) {
                    app.radiorecalarm.widget.HomeWidgetUpdater.update(context)
                }

                closeEditStationDialog()
            }
        }
    }

    private fun startRadioService(station: RadioStation) {
        val i = Intent(context, RadioService::class.java).apply {
            putExtra("STREAM_URL", station.url)
            putExtra("STATION_NAME", station.name)
            putExtra("TRIGGERED_BY", "USER")
            putExtra("CATEGORY_NAME", _uiState.value.selectedCategory)
        }
        context.startForegroundService(i)
    }

    private fun updateSelectedStationLocal(stationId: Int) {
        _uiState.update { it.copy(selectedStationId = stationId) }
        prefs.edit().putInt("last_selected_id", stationId).apply()

        val station = _uiState.value.stations.find { it.id == stationId }
        if (station != null) {
            val currentCat = _uiState.value.selectedCategory
            if (currentCat != "Favorites" && currentCat != "My" && currentCat != "All" && currentCat != station.countryCode) {
                onCategorySelected(if (station.countryCode.isEmpty()) "All" else station.countryCode)
            }
        }
    }

    fun playTestStation(name: String, url: String) {
        if (!isValidUrl(url)) {
            Toast.makeText(context, getString(R.string.error_invalid_url), Toast.LENGTH_SHORT).show()
            return
        }

        val isAlreadyPlayingThis = url == _uiState.value.activeStreamUrl && _uiState.value.isPlaying

        if (isAlreadyPlayingThis) {
            _uiState.update { it.copy(isPlaying = false) }
            val i = Intent(context, RadioService::class.java).apply { action = RadioService.ACTION_PAUSE }
            context.startService(i)
        } else {
            _uiState.update { it.copy(
                activeStationName = name,
                activeStreamUrl = url,
                selectedStationId = -1,
                isPlaying = true
            )}
            val i = Intent(context, RadioService::class.java).apply {
                putExtra("STREAM_URL", url)
                putExtra("STATION_NAME", name)
                putExtra("TRIGGERED_BY", "USER")
            }
            context.startForegroundService(i)
        }
    }

    private fun isValidUrl(url: String): Boolean {
        val trimmed = url.trim().lowercase()
        return trimmed.startsWith("http://") || trimmed.startsWith("https://")
    }

    override fun onCleared() {
        super.onCleared()
        LocalBroadcastManager.getInstance(context).unregisterReceiver(radioReceiver)
    }

    private fun getString(resId: Int): String {
        return getApplication<Application>().getString(resId)
    }

    fun exportDataToUri(uri: android.net.Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val alarms = alarmDao.getAllAlarmsList()
                val allStations = database.radioStationDao().getAllActiveStationsSync()
                val settings = BackupCodec.readSettingsFromPrefs(prefs)
                val backupData = BackupCodec.createBackupData(
                    alarms = alarms,
                    stations = allStations,
                    settings = settings
                )
                val jsonText = BackupCodec.encode(backupData)

                val outputStream = context.contentResolver.openOutputStream(uri)
                    ?: throw IllegalStateException("Sihtfaili kirjutamine ebaõnnestus")
                outputStream.use { stream ->
                    stream.write(jsonText.toByteArray(Charsets.UTF_8))
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, getString(R.string.backup_success), Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Backup export error: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, context.getString(R.string.backup_failed, e.message ?: "Unknown"), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun prepareImportFromUri(uri: android.net.Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val jsonText = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    inputStream.bufferedReader().readText()
                } ?: throw IllegalStateException("Fail on tühi või kättesaamatu")

                val backupData = BackupCodec.decode(jsonText)
                _uiState.update { it.copy(pendingBackupImport = PendingBackupImport(backupData)) }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Backup parse error: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, context.getString(R.string.restore_failed, e.message ?: "Unknown"), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun cancelPendingImport() {
        _uiState.update { it.copy(pendingBackupImport = null) }
    }

    fun confirmPendingImport() {
        val pending = _uiState.value.pendingBackupImport ?: return
        _uiState.update { it.copy(pendingBackupImport = null) }
        restoreBackupData(pending.data)
    }

    private fun restoreBackupData(backupData: BackupData) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val stationDao = database.radioStationDao()
                val previousSelectedId = prefs.getInt("last_selected_id", -1)
                var missingFavoritesCount = 0

                database.withTransaction {
                    val stationsBeforeRestore = stationDao.getAllActiveStationsSync()
                    val previousSelectedStation = stationsBeforeRestore.find { it.id == previousSelectedId }

                    // 1. Reset curated favorites and delete existing user stations
                    stationDao.resetAllFavorites()
                    stationDao.deleteAllUserStations()

                    // 2. Restore user stations with consistent priority
                    val restoredUserStations = BackupCodec.buildRestoredUserStations(
                        customStations = backupData.customStations,
                        maxExistingId = stationDao.getMaxId()
                    )
                    restoredUserStations.forEach { stationDao.insert(it) }

                    // 3. Restore curated favorites (match id -> url -> name)
                    val stationsAfterUserRestore = stationDao.getAllActiveStationsSync()
                    val matchedCuratedIds = mutableSetOf<Int>()
                    backupData.favoriteUrls.forEach { backupFavorite ->
                        val matched = BackupCodec.findCuratedFavoriteMatch(backupFavorite, stationsAfterUserRestore)
                        if (matched != null) {
                            if (matchedCuratedIds.add(matched.id)) {
                                stationDao.update(
                                    matched.copy(
                                        isFavorite = true,
                                        favoriteOrder = backupFavorite.favoriteOrder
                                    )
                                )
                            }
                        } else {
                            missingFavoritesCount++
                        }
                    }

                    // 4. Replace alarms
                    val existingAlarms = alarmDao.getAllAlarmsList()
                    AlarmUtils.cancelAlarms(context, existingAlarms)
                    alarmDao.deleteAllAlarms()

                    BackupCodec.buildRestoredAlarms(backupData.alarms).forEach { newAlarm ->
                        alarmDao.insert(newAlarm)
                    }

                    // 5. Reconcile selected station ID if previously selected user station got a new ID or disappeared
                    val stationsAfterRestore = stationDao.getAllActiveStationsSync()
                    val resolvedSelection = BackupCodec.resolveSelectedStationAfterRestore(
                        previousSelectedStation = previousSelectedStation,
                        stationsAfterRestore = stationsAfterRestore
                    )
                    val newSelectedId = resolvedSelection?.id ?: -1
                    prefs.edit().apply {
                        putInt("last_selected_id", newSelectedId)
                        if (resolvedSelection != null) {
                            putString("last_name", resolvedSelection.name)
                            putString("last_url", resolvedSelection.url)
                        }
                    }.apply()
                    _uiState.update { it.copy(selectedStationId = newSelectedId) }
                }

                // 6. Restore persisted settings if present in backup
                backupData.settings?.let { rawSettings ->
                    val currentSettings = BackupCodec.readSettingsFromPrefs(prefs)
                    val sanitized = BackupCodec.sanitizeSettings(rawSettings, currentSettings)
                    BackupCodec.writeSettingsToPrefs(prefs, sanitized)
                    _uiState.update {
                        it.copy(
                            colsPortrait = sanitized.colsPortrait,
                            colsLandscape = sanitized.colsLandscape,
                            showFlags = sanitized.showFlags,
                            hideRemoteStations = sanitized.hideRemoteStations,
                            widgetTransparency = sanitized.widgetTransparency,
                            continuousRecording = sanitized.continuousRecording,
                            minTrackDurationSeconds = sanitized.minTrackDurationSeconds,
                            preRollSeconds = sanitized.preRollSeconds,
                            postRollSeconds = sanitized.postRollSeconds
                        )
                    }
                }

                AlarmUtils.scheduleNextAlarmInternal(context)
                app.radiorecalarm.widget.HomeWidgetUpdater.update(context)

                withContext(Dispatchers.Main) {
                    val toastText = if (missingFavoritesCount > 0) {
                        context.getString(R.string.restore_success_missing_favorites, missingFavoritesCount)
                    } else {
                        getString(R.string.restore_success)
                    }
                    Toast.makeText(context, toastText, Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Backup restore error: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, context.getString(R.string.restore_failed, e.message ?: "Unknown"), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // --- ÄPISISENE UUENDUS (IN-APP UPDATER) ---
    fun checkForUpdates(currentVersion: String) {
        _uiState.update { it.copy(updateState = UpdateUiState.Checking) }
        viewModelScope.launch {
            val result = updateManager.checkForUpdate(currentVersion)
            if (result is UpdateUiState.UpdateAvailable || result is UpdateUiState.ReadyToInstall) {
                cachedUpdateResult = result
                _uiState.update { it.copy(updateState = result, hasAvailableUpdate = true) }
            } else {
                _uiState.update { it.copy(updateState = result, hasAvailableUpdate = false) }
            }
        }
    }

    fun downloadUpdate(release: GitHubRelease, asset: GitHubReleaseAsset) {
        downloadJob?.cancel()
        downloadJob = viewModelScope.launch {
            updateManager.downloadApk(release, asset).collect { state ->
                if (state is UpdateUiState.ReadyToInstall) {
                    cachedUpdateResult = state
                }
                _uiState.update { it.copy(updateState = state) }
            }
        }
    }

    fun installUpdate(apkFile: File) {
        updateManager.installApk(apkFile)
    }

    fun dismissUpdateDialog() {
        downloadJob?.cancel()
        downloadJob = null
        _uiState.update { it.copy(updateState = UpdateUiState.Idle) }
    }

    fun canInstallPackages(): Boolean = updateManager.canInstallPackages()

    fun openInstallPermissionSettings() = updateManager.openInstallPermissionSettings()
}
