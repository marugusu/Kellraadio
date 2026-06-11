package app.radiorecalarm.ui

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val prefs = context.getSharedPreferences("RaadioPrefs", Context.MODE_PRIVATE)

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
                    val dur = intent.getLongExtra("RECORDING_DURATION", 0L)
                    val station = intent.getStringExtra("RECORDING_STATION") ?: ""
                    _uiState.update { it.copy(
                        isRecording = isRec,
                        recordingDuration = dur,
                        recordingStation = station
                    )}
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

        viewModelScope.launch {
            stationRepository.allStations.collect { stations ->
                val allCategories = stations
                    .filter { !it.isUserStation && it.category.isNotBlank() }
                    .map { it.category }
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

        viewModelScope.launch {
            val stations = stationRepository.allStations.first()
            val lastUpdate = prefs.getLong("last_update_time", 0L)
            val oneDayMillis = 24 * 60 * 60 * 1000L
            val isExpired = (System.currentTimeMillis() - lastUpdate) > oneDayMillis
            if (stations.isEmpty() || isExpired) {
                refreshStations()
            }
        }
        updateCountries()
    }

    private fun loadPreferences() {
        _uiState.update { it.copy(
            selectedCategory = prefs.getString("last_category", "Favorites") ?: "Favorites",
            selectedStationId = prefs.getInt("last_selected_id", -1),
            colsPortrait = prefs.getInt("cols_portrait", 3),
            colsLandscape = prefs.getInt("cols_landscape", 3),
            showFlags = prefs.getBoolean("show_flags", true),
            widgetTransparency = prefs.getFloat("widget_transparency", 0.25f),
            hideRemoteStations = prefs.getBoolean("hide_remote_stations", false)
        )}
    }

    fun updateCountries() {
        viewModelScope.launch {
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
            _uiState.update { it.copy(currentTab = index, categoryToSelectOnTabChange = null) }
        } else {
            _uiState.update { it.copy(currentTab = index) }
        }
    }

    fun onCategorySelected(category: String) {
        _uiState.update { it.copy(
            selectedCategory = category,
            selectedSubCategories = emptySet()
        )}
        prefs.edit().putString("last_category", category).apply()
    }

    fun toggleSubCategory(sub: String) {
        _uiState.update { currentState ->
            val newSet = if (currentState.selectedSubCategories.contains(sub)) {
                currentState.selectedSubCategories - sub
            } else {
                currentState.selectedSubCategories + sub
            }
            currentState.copy(selectedSubCategories = newSet)
        }
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
        } else {
            val isLocal = _uiState.value.activeStreamUrl.startsWith("file://") || _uiState.value.activeStreamUrl.startsWith("file:/")
            if (isLocal) {
                val i = Intent(context, RadioService::class.java).apply { action = RadioService.ACTION_RESUME }
                context.startService(i)
            } else {
                val station = _uiState.value.stations.find { it.id == _uiState.value.selectedStationId }
                if (station != null) {
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

    fun onPlayRecordingClicked(file: java.io.File) {
        val fileUri = android.net.Uri.fromFile(file).toString()
        val displayName = file.name.removePrefix("Recording_").substringBeforeLast("_202").replace("_", " ")
        val finalName = if (displayName.isNotBlank()) "Salvestis: $displayName" else "Salvestis"
        _uiState.update { it.copy(
            activeStationName = finalName,
            activeStreamUrl = fileUri,
            selectedStationId = -1,
            isPlaying = true,
            playerStatus = getString(R.string.status_buffering)
        )}
        val i = Intent(context, RadioService::class.java).apply {
            putExtra("STREAM_URL", fileUri)
            putExtra("STATION_NAME", finalName)
            putExtra("TRIGGERED_BY", "USER")
        }
        context.startForegroundService(i)
    }

    fun seekTo(positionMs: Long) {
        _uiState.update { it.copy(playbackPosition = positionMs) }
        val i = Intent(context, RadioService::class.java).apply {
            action = RadioService.ACTION_SEEK
            putExtra(RadioService.EXTRA_SEEK_POSITION, positionMs)
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
                playbackPosition = 0L,
                playbackDuration = 0L
            )}
        }
        try {
            if (file.exists()) {
                file.delete()
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

    fun saveAlarm(hour: Int, minute: Int, days: Set<Int>) {
        val alarmToEdit = _uiState.value.alarmToEdit
        val station = if (alarmToEdit != null) {
            _uiState.value.stations.find { it.name == alarmToEdit.stationName }
        } else {
            _uiState.value.stations.find { it.id == _uiState.value.selectedStationId }
        } ?: return

        val alarm = alarmToEdit?.copy(
            hour = hour, minute = minute, days = days,
            stationName = station.name, stationUrl = station.url, isEnabled = true
        ) ?: Alarm(
            hour = hour, minute = minute, days = days,
            stationName = station.name, stationUrl = station.url
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

    fun saveUserStation(name: String, url: String, countryCode: String, category: String) {
        if (!isValidUrl(url)) {
            Toast.makeText(context, getString(R.string.error_invalid_url), Toast.LENGTH_SHORT).show()
            return
        }
        
        viewModelScope.launch {
            val newId = stationRepository.saveUserStation(name, url, countryCode, category)
            Toast.makeText(context, context.getString(R.string.station_added, name), Toast.LENGTH_SHORT).show()
            _uiState.update { it.copy(categoryToSelectOnTabChange = "My") }

            if (_uiState.value.activeStreamUrl == url && _uiState.value.isPlaying) {
                _uiState.update { it.copy(
                    selectedStationId = newId,
                    activeStationName = name
                )}
                prefs.edit().putInt("last_selected_id", newId).apply()
                val i = Intent(RadioService.ACTION_STATION_CHANGED).apply {
                    putExtra("STATION_NAME", name)
                }
                LocalBroadcastManager.getInstance(context).sendBroadcast(i)
            }
        }
    }

    fun refreshStations() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            try {
                stationRepository.refreshStations()
                prefs.edit().putLong("last_update_time", System.currentTimeMillis()).apply()
                Toast.makeText(context, getString(R.string.toast_updated), Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                // Ignore
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
        val i = Intent(context, RadioService::class.java).apply {
            action = RadioService.ACTION_FORCE_WIDGET_UPDATE
        }
        context.startService(i)
    }

    fun clearHistory() {
        viewModelScope.launch { stationRepository.clearHistory() }
    }

    fun updateUserStation(newName: String, newUrl: String, newCountryCode: String, newCategory: String) {
        if (!isValidUrl(newUrl)) {
            Toast.makeText(context, getString(R.string.error_invalid_url), Toast.LENGTH_SHORT).show()
            return
        }

        val stationToUpdate = _uiState.value.stationToEdit
        if (stationToUpdate != null) {
            viewModelScope.launch {
                stationRepository.updateUserStation(stationToUpdate, newName, newUrl, newCountryCode, newCategory)
                if (stationToUpdate.id == _uiState.value.selectedStationId) {
                    _uiState.update { it.copy(activeStationName = newName) }
                    val serviceIntent = Intent(context, RadioService::class.java).apply {
                        action = RadioService.ACTION_UPDATE_STATION_NAME
                        putExtra("STATION_NAME", newName)
                    }
                    context.startService(serviceIntent)
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
                // 1. Fetch alarms
                val alarms = alarmDao.getAllAlarmsList().map {
                    BackupAlarm(
                        hour = it.hour,
                        minute = it.minute,
                        days = it.days.toList(),
                        stationName = it.stationName,
                        stationUrl = it.stationUrl,
                        isEnabled = it.isEnabled
                    )
                }

                // 2. Fetch stations
                val allStations = database.radioStationDao().getAllActiveStationsSync()
                val customStations = allStations.filter { it.isUserStation }.map {
                    BackupStation(
                        name = it.name,
                        url = it.url,
                        category = it.category,
                        countryCode = it.countryCode,
                        isFavorite = it.isFavorite,
                        favoriteOrder = it.favoriteOrder
                    )
                }
                val favoriteUrls = allStations.filter { it.isFavorite && !it.isUserStation }.map {
                    BackupFavorite(
                        url = it.url,
                        favoriteOrder = it.favoriteOrder
                    )
                }

                // 3. Serialize
                val backupData = BackupData(customStations = customStations, favoriteUrls = favoriteUrls, alarms = alarms)
                val jsonText = Json.encodeToString(BackupData.serializer(), backupData)

                // 4. Write to URI
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(jsonText.toByteArray(Charsets.UTF_8))
                }

                viewModelScope.launch(Dispatchers.Main) {
                    Toast.makeText(context, getString(R.string.backup_success), Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "Backup export error: ${e.message}", e)
                viewModelScope.launch(Dispatchers.Main) {
                    Toast.makeText(context, getString(R.string.backup_failed).format(e.message ?: "Unknown"), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun importDataFromUri(uri: android.net.Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 1. Read from URI
                val jsonText = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    inputStream.bufferedReader().readText()
                } ?: throw Exception("Fail on tühi või kättesaamatu")

                // 2. Deserialize
                val backupData = Json.decodeFromString(BackupData.serializer(), jsonText)

                val stationDao = database.radioStationDao()
                
                database.withTransaction {
                    // 3. Reset standard favorites
                    stationDao.resetAllFavorites()

                    // 4. Delete all existing user/custom stations
                    stationDao.deleteAllUserStations()

                    // 5. Restore custom stations
                    val maxId = stationDao.getMaxId() ?: 9999
                    var nextCustomId = if (maxId < 10000) 10000 else maxId + 1
                    backupData.customStations.forEach { backupStation ->
                        val newStation = RadioStation(
                            id = nextCustomId,
                            name = backupStation.name,
                            url = backupStation.url,
                            category = backupStation.category,
                            countryCode = backupStation.countryCode,
                            isFavorite = backupStation.isFavorite,
                            isUserStation = true,
                            favoriteOrder = backupStation.favoriteOrder,
                            uuid = java.util.UUID.randomUUID().toString()
                        )
                        stationDao.insert(newStation)
                        nextCustomId++
                    }

                    // 6. Restore standard favorites
                    val existingStations = stationDao.getAllActiveStationsSync()
                    backupData.favoriteUrls.forEach { backupFavorite ->
                        val station = existingStations.find { it.url == backupFavorite.url }
                        if (station != null) {
                            stationDao.update(station.copy(isFavorite = true, favoriteOrder = backupFavorite.favoriteOrder))
                        }
                    }

                    // 7. Restore alarms
                    // First cancel existing scheduled system alarms and delete them from DB
                    val existingAlarms = alarmDao.getAllAlarmsList()
                    AlarmUtils.cancelAlarms(context, existingAlarms)
                    alarmDao.deleteAllAlarms()

                    // Then restore imported alarms
                    backupData.alarms.forEach { backupAlarm ->
                        val newAlarm = Alarm(
                            hour = backupAlarm.hour,
                            minute = backupAlarm.minute,
                            days = backupAlarm.days.toSet(),
                            stationName = backupAlarm.stationName,
                            stationUrl = backupAlarm.stationUrl,
                            isEnabled = backupAlarm.isEnabled
                        )
                        val newId = alarmDao.insert(newAlarm).toInt()
                        if (newAlarm.isEnabled) {
                            AlarmUtils.reScheduleRepeatingAlarm(context, newAlarm.copy(id = newId))
                        }
                    }
                }

                viewModelScope.launch(Dispatchers.Main) {
                    Toast.makeText(context, getString(R.string.restore_success), Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "Backup restore error: ${e.message}", e)
                viewModelScope.launch(Dispatchers.Main) {
                    Toast.makeText(context, getString(R.string.restore_failed).format(e.message ?: "Unknown"), Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}

@Serializable
data class BackupData(
    val customStations: List<BackupStation>,
    val favoriteUrls: List<BackupFavorite>,
    val alarms: List<BackupAlarm> = emptyList()
)

@Serializable
data class BackupAlarm(
    val hour: Int,
    val minute: Int,
    val days: List<Int>,
    val stationName: String,
    val stationUrl: String,
    val isEnabled: Boolean
)

@Serializable
data class BackupStation(
    val name: String,
    val url: String,
    val category: String,
    val countryCode: String,
    val isFavorite: Boolean = true,
    val favoriteOrder: Int
)

@Serializable
data class BackupFavorite(
    val url: String,
    val favoriteOrder: Int
)
