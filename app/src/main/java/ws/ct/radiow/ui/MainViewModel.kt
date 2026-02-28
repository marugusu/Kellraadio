package ws.ct.radiow.ui

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
import ws.ct.radiow.Alarm
import ws.ct.radiow.AlarmUtils
import ws.ct.radiow.AppDatabase
import ws.ct.radiow.R
import ws.ct.radiow.RadioBrowserApiService
import ws.ct.radiow.RadioFilterItem
import ws.ct.radiow.RadioService
import ws.ct.radiow.RadioStation
import ws.ct.radiow.RadioStationRepository
import ws.ct.radiow.StationApiService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
                    var newId = _uiState.value.selectedStationId
                    var newCat = _uiState.value.selectedCategory
                    val foundStation = _uiState.value.stations.find { it.name == name }
                    if (foundStation != null) {
                        newId = foundStation.id
                        if (newCat != "Favorites" && newCat != "My" && newCat != "All" && newCat != foundStation.countryCode) {
                            newCat = if (foundStation.countryCode.isEmpty()) "All" else foundStation.countryCode
                            onCategorySelected(newCat)
                        }
                        prefs.edit().putInt("last_selected_id", newId).apply()
                    }
                    _uiState.update { it.copy(
                        isPlaying = true,
                        playerStatus = getString(R.string.status_playing),
                        activeStationName = name,
                        // UUS: Kui leiame jaama, uuendame ka URL-i, et test-nupud teaksid
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
                    _uiState.update { it.copy(isPlaying = false, playerStatus = getString(R.string.status_error), bitrate = "") }
                    Toast.makeText(context, getString(R.string.error_station_not_found), Toast.LENGTH_LONG).show()
                }
                RadioService.ACTION_PLAYER_STOPPED -> {
                    _uiState.update { it.copy(
                        isPlaying = false,
                        playerStatus = getString(R.string.status_stopped),
                        bitrate = "",
                        activeStreamUrl = ""
                    )}
                }
                RadioService.ACTION_TIMER_TICK -> {
                    val remaining = intent.getLongExtra("REMAINING_MILLIS", 0L)
                    _uiState.update { it.copy(sleepTimerRemaining = remaining) }
                }
                RadioService.ACTION_STATION_SELECTED_BY_SERVICE -> {
                    val stationId = intent.getIntExtra("STATION_ID", -1)
                    if (stationId != -1) updateSelectedStationLocal(stationId)
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
            // UUS: Uuendame ka URL-i, et otsinguvaade teaks, mis mängib
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
            val station = _uiState.value.stations.find { it.id == _uiState.value.selectedStationId }
            if (station != null) {
                startRadioService(station)
            } else {
                Toast.makeText(context, getString(R.string.select_station), Toast.LENGTH_SHORT).show()
            }
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
                // UUS: Uuendame URL-i ka siin
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
                prefs.edit().putInt("last_selected_id", newId).putString("last_selected_name", name).apply()
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
            // UUS: Saadame aktiivse kategooria konteksti
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

        // PARANDUS: Kontrollime olekut ENNE uuendamist
        val isAlreadyPlayingThis = url == _uiState.value.activeStreamUrl && _uiState.value.isPlaying

        if (isAlreadyPlayingThis) {
            // Kui juba mängib, siis pausile (kasutades startService)
            _uiState.update { it.copy(isPlaying = false) }
            val i = Intent(context, RadioService::class.java).apply { action = RadioService.ACTION_PAUSE }
            context.startService(i)
        } else {
            // Kui ei mängi, siis mängima (kasutades startForegroundService)
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
}
