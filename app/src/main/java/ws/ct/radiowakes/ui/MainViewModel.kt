package ws.ct.radiowakes.ui

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import kotlinx.coroutines.flow.first

import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import ws.ct.radiowakes.Alarm
import ws.ct.radiowakes.AlarmUtils
import ws.ct.radiowakes.AppDatabase
import ws.ct.radiowakes.R
import ws.ct.radiowakes.RadioBrowserApiService
import ws.ct.radiowakes.RadioService
import ws.ct.radiowakes.RadioStation
import ws.ct.radiowakes.RadioStationRepository
import ws.ct.radiowakes.StationApiService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val prefs = context.getSharedPreferences("RaadioPrefs", Context.MODE_PRIVATE)

    // --- REPOSITOORIUMID ---
    private val database = AppDatabase.getDatabase(context)
    val stationRepository = RadioStationRepository(
        StationApiService.create(),
        RadioBrowserApiService.create(),
        database.radioStationDao(),
        database.historyDao()
    )
    private val alarmDao = database.alarmDao()

    // --- UI OLEK ---
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState = _uiState.asStateFlow()

    // --- BROADCAST RECEIVER (Raadio sündmused) ---
    private val radioReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                RadioService.ACTION_STATION_CHANGED -> {
                    val name = intent.getStringExtra("STATION_NAME") ?: ""
                    // Sünkroniseerime UI jaama nime järgi ---
                    var newId = _uiState.value.selectedStationId
                    var newCat = _uiState.value.selectedCategory
                    // Otsime jaama praegusest nimekirjast nime järgi
                    val foundStation = _uiState.value.stations.find { it.name == name }
                    if (foundStation != null) {
                        newId = foundStation.id
                        // Kui praegune kategooria pole "Favorites" ega "All" ja on vale, siis vahetame
                        if (newCat != "Favorites" && newCat != "All" && newCat != foundStation.category) {
                            newCat = foundStation.category
                            prefs.edit().putString("last_category", newCat).apply()
                        }
                        // Salvestame valiku mällu
                        prefs.edit().putInt("last_selected_id", newId).apply()
                    }
                    _uiState.update { it.copy(
                        isPlaying = true,
                        playerStatus = getString(R.string.status_playing),
                        activeStationName = name,
                        // Uuendame ka valikut ja kategooriat
                        selectedStationId = newId,
                        selectedCategory = newCat
                    )}
                }
                RadioService.ACTION_METADATA_UPDATED -> {
                    val title = intent.getStringExtra("PARSED_TITLE") ?: ""
                    val artist = intent.getStringExtra("PARSED_ARTIST") ?: ""
                    val extra = intent.getStringExtra("PARSED_EXTRA") ?: ""
                    _uiState.update { it.copy(isPlaying = true, playerStatus = getString(R.string.status_playing), parsedTitle = title, parsedArtist = artist, parsedExtra = extra) }
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
        // Registreeri kuulaja
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

        // 1. JÄLGIJA: Uuendab UI-d, kui andmebaas muutub (aga ei käivita enam värskendust)
        // Flow vaatleja
        viewModelScope.launch {
            stationRepository.allStations.collect { stations ->
                // 1. Uuendame nimekirja
                _uiState.update { currentState ->
                    // --- PARANDUS: SÜNKRONISEERIMINE ---
                    // Vaatame, kas hetkel mängiv jaam (nimi) on selles uues nimekirjas olemas.
                    // Kui on, siis sunnime UI valima selle jaama ID-d.
                    val activeName = currentState.activeStationName
                    val correctStation = stations.find { it.name == activeName }

                    val correctedId = correctStation?.id ?: currentState.selectedStationId
                    val correctedCategory = correctStation?.category ?: currentState.selectedCategory

                    currentState.copy(
                        stations = stations,
                        selectedStationId = correctedId,
                        selectedCategory = if (correctedId != -1 && currentState.selectedCategory != "Favorites" && currentState.selectedCategory != "All") correctedCategory else currentState.selectedCategory
                    )
                }
            }
        }

        viewModelScope.launch {
            alarmDao.getAllAlarms().collect { alarms -> _uiState.update { it.copy(alarms = alarms) } }
        }

        LocalBroadcastManager.getInstance(context).sendBroadcast(Intent(RadioService.ACTION_GET_STATUS))

        // 2. ÜHEKORDNE KONTROLL KÄIVITAMISEL
        viewModelScope.launch {
            // Ootame ära esimese andmebaasi vastuse (see on kiire)
            val stations = stationRepository.allStations.first()

            val lastUpdate = prefs.getLong("last_update_time", 0L)
            val oneDayMillis = 24 * 60 * 60 * 1000L
            val isExpired = (System.currentTimeMillis() - lastUpdate) > oneDayMillis

            // Kui andmebaas on tühi VÕI aegunud -> Värskenda
            if (stations.isEmpty() || isExpired) {
                refreshStations()
            }

        }

        // Laadi riigid kohe sisse
        loadCountries()
    }

    private fun loadPreferences() {
        _uiState.update { it.copy(
            selectedCategory = prefs.getString("last_category", "ERR") ?: "ERR",
            selectedStationId = prefs.getInt("last_selected_id", -1),
            colsPortrait = prefs.getInt("cols_portrait", 3),
            colsLandscape = prefs.getInt("cols_landscape", 3),
            showFlags = prefs.getBoolean("show_flags", true)
        )}
    }

    private fun loadCountries() {
        viewModelScope.launch {
            _uiState.update { it.copy(isCountriesLoading = true) }
            val countries = stationRepository.getCountries()
            _uiState.update { it.copy(countries = countries, isCountriesLoading = false) }
        }
    }

    // --- KASUTAJA TEGEVUSED (EVENTS) ---

    fun onTabSelected(index: Int) {
        _uiState.update { it.copy(currentTab = index) }
    }

    fun onCategorySelected(category: String) {
        _uiState.update { it.copy(selectedCategory = category) }
        prefs.edit().putString("last_category", category).apply()
    }

    fun onStationClicked(station: RadioStation) {
        // --- PARANDUS: ÄRA USALDA SISENDOBJEKTI ANDMEID, VAID AINULT SELLE ID-d ---
        // 1. Leia kõige värskem jaama info UI olekust, kasutades klikitud jaama ID-d.
        val freshStation = _uiState.value.stations.find { it.id == station.id }

        // 2. Kui mingil põhjusel jaama ei leita (ei tohiks juhtuda), kasuta fallbackina vana objekti.
        val stationToPlay = freshStation ?: station

        // 3. Jätka loogikaga, aga kasuta nüüd GARANTEERITULT värsket "stationToPlay" objekti.
        updateSelectedStationLocal(stationToPlay.id)
        _uiState.update { it.copy(
            activeStationName = stationToPlay.name, // Kasutame värsket nime
            isPlaying = true,
            playerStatus = getString(R.string.status_buffering)
        )}
        startRadioService(stationToPlay) // Saadame teenusele värske objekti
    }

    fun onPlayPauseClicked() {
        if (_uiState.value.isPlaying) {
            val i = Intent(context, RadioService::class.java).apply { action = RadioService.ACTION_PAUSE }
            context.startService(i)
        } else {
            // Kui on valitud jaam, mängi seda.
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
            if (_uiState.value.selectedCategory != "Favorites" && _uiState.value.selectedCategory != station.category) {
                onCategorySelected(station.category)
            }
            _uiState.update { it.copy(
                activeStationName = station.name,
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

    // --- DIALOOGIDE HALDUS ---

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
        // Sulgeme esmalt menüü, et vältida visuaalseid konflikte
        closeStationActionSheet()
        _uiState.update { it.copy(stationToEdit = station) }
    }
    fun closeEditStationDialog() { _uiState.update { it.copy(stationToEdit = null) } }



    // --- ÄRILINE LOOGIKA ---

    fun saveAlarm(hour: Int, minute: Int, days: Set<Int>) {
        val alarmToEdit = _uiState.value.alarmToEdit
        val station = if (alarmToEdit != null) {
            // Kui muudame, siis jaam on juba alarmis kirjas, aga võime ka praegust aktiivset kasutada, kui tahame
            // Siin hoiame lihtsuse mõttes alarmi enda jaama nime, või kui on uus, siis valitud jaama
            _uiState.value.stations.find { it.name == alarmToEdit.stationName }
        } else {
            _uiState.value.stations.find { it.id == _uiState.value.selectedStationId }
        } ?: return // Ei tohiks juhtuda

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
                // MUUDATUS: Kasutame nüüd spetsiaalset jaama kustutamise teadet
                Toast.makeText(context, getString(R.string.station_toast_deleted), Toast.LENGTH_SHORT).show()
                cancelDeleteStation()
                closeStationActionSheet()
            }
        }
    }

    fun saveUserStation(name: String, url: String, countryCode: String = "") {
        if (!isValidUrl(url)) {
            Toast.makeText(context, getString(R.string.error_invalid_url), Toast.LENGTH_SHORT).show()
            return
        }
        
        viewModelScope.launch {
            // 1. Salvestame andmebaasi ja saame uue ID
            val newId = stationRepository.saveUserStation(name, url, countryCode)

            // 2. Anname kasutajale teada
            Toast.makeText(context, context.getString(R.string.station_added, name), Toast.LENGTH_SHORT).show()

            // 3. Vahetame kategooriat ("My Stations")
            onCategorySelected("My")

            // 4. "TARK" OSA: Kas me kuulame praegu sedasama jaama?
            // Kui URL on sama ja raadio mängib, siis see pole enam test!
            if (_uiState.value.activeStreamUrl == url && _uiState.value.isPlaying) {
                // Uuendame UI olekut: seame õige ID (täht läheb kollaseks) ja nime
                _uiState.update { it.copy(
                    selectedStationId = newId,
                    activeStationName = name // Eemaldame "(Test)" liite visuaalselt
                )}

                // Uuendame mälus valikut
                prefs.edit().putInt("last_selected_id", newId).putString("last_selected_name", name).apply()

                // Saadame Service'ile signaali, et ta uuendaks teavitust (võtaks "(Test)" nime tagant ära)
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

    fun clearHistory() {
        viewModelScope.launch { stationRepository.clearHistory() }
    }

    fun addTestData() {
        viewModelScope.launch {
            stationRepository.insertTestHistory()
            Toast.makeText(context, getString(R.string.toast_updated), Toast.LENGTH_SHORT).show()
        }
    }

    fun updateUserStation(newName: String, newUrl: String, newCountryCode: String = "") {
        if (!isValidUrl(newUrl)) {
            Toast.makeText(context, getString(R.string.error_invalid_url), Toast.LENGTH_SHORT).show()
            return
        }

        val stationToUpdate = _uiState.value.stationToEdit
        if (stationToUpdate != null) {
            viewModelScope.launch {
                // 1. Uuenda andmebaas
                stationRepository.updateUserStation(stationToUpdate, newName, newUrl, newCountryCode)

                // 2. Kontrolli, kas muudetud jaam on hetkel aktiivne
                if (stationToUpdate.id == _uiState.value.selectedStationId) {
                    // 2a. Uuenda UI-s kohe nähtav nimi
                    _uiState.update { it.copy(activeStationName = newName) }

                    // 2b. PARANDUS: Saada otse teenusele käsk nime uuendamiseks
                    val serviceIntent = Intent(context, RadioService::class.java).apply {
                        action = RadioService.ACTION_UPDATE_STATION_NAME
                        putExtra("STATION_NAME", newName)
                    }
                    context.startService(serviceIntent)
                }

                // 3. Sulge dialoog
                closeEditStationDialog()
            }
        }
    }

    // --- ABIFUNKTSIOONID ---

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

        // Automaatne kategooria vahetus (sünkroniseerimine)
        val station = _uiState.value.stations.find { it.id == stationId }
        if (station != null) {
            val currentCat = _uiState.value.selectedCategory
            if (currentCat != "Favorites" && currentCat != "All" && currentCat != station.category) {
                onCategorySelected(station.category)
            }
        }
    }

    fun playTestStation(name: String, url: String) {
        if (!isValidUrl(url)) {
            Toast.makeText(context, getString(R.string.error_invalid_url), Toast.LENGTH_SHORT).show()
            return
        }

        // 1. Uuendame olekut kohe
        _uiState.update { it.copy(
            activeStationName = name, // Näita nime kohe
            activeStreamUrl = url,    // Et SearchScreen Stop nupp töötaks
            selectedStationId = -1,   // TÄHTIS: See pole andmebaasi jaam -> Lemmiku täht tühjaks
            isPlaying = true
        )}

        // 2. Käivitame teenuse
        val i = Intent(context, RadioService::class.java).apply {
            putExtra("STREAM_URL", url)
            putExtra("STATION_NAME", name)
            putExtra("TRIGGERED_BY", "USER")
        }
        context.startForegroundService(i)
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