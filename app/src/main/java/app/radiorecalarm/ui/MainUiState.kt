package app.radiorecalarm.ui

import app.radiorecalarm.Alarm
import app.radiorecalarm.RadioFilterItem
import app.radiorecalarm.RadioStation
import app.radiorecalarm.SongAdditionalInfo
import app.radiorecalarm.update.UpdateUiState

data class MainUiState(
    // --- RAADIO MÄNGIJA OLEK ---
    val isPlaying: Boolean = false,
    val playerStatus: String = "",
    val bitrate: String = "",
    val activeStationName: String = "",
    val activeStreamUrl: String = "",
    val playbackPosition: Long = 0L,
    val playbackDuration: Long = 0L,
    val isRecording: Boolean = false,
    val recordingDuration: Long = 0L,
    val recordingStation: String = "",

    // --- METAANDMED ---
    val parsedArtist: String = "",
    val parsedTitle: String = "",
    val parsedExtra: String = "",
    val songInfo: SongAdditionalInfo? = null,

    // --- ANDMED ---
    val stations: List<RadioStation> = emptyList(),
    val alarms: List<Alarm> = emptyList(),
    val countries: List<RadioFilterItem> = emptyList(),
    val allCategories: List<String> = emptyList(),

    // --- UI KONFIGURATSIOON ---
    val isRefreshing: Boolean = false,
    val isCountriesLoading: Boolean = false,
    val sleepTimerRemaining: Long = 0L,
    val showFlags: Boolean = true,
    val colsPortrait: Int = 3,
    val colsLandscape: Int = 3,
    val widgetTransparency: Float = 0.25f,
    val hideRemoteStations: Boolean = false,

    // --- NAVIGATSIOON JA VALIKUD ---
    val currentTab: Int = 0,
    val selectedCategory: String = "Favorites",
    val selectedSubCategories: Set<String> = emptySet(),
    val selectedStationId: Int = -1,
    val categoryToSelectOnTabChange: String? = null,

    // --- DIALOOGIDE OLEKUD ---
    val showSleepDialog: Boolean = false,
    val showAlarmDialog: Boolean = false,
    val alarmToEdit: Alarm? = null,
    val showActionSheetForStation: RadioStation? = null,
    val showSongInfoSheet: Boolean = false,
    val stationToDelete: RadioStation? = null,
    val stationToEdit: RadioStation? = null,
    val showResetOrderDialog: Boolean = false,

    // --- UUENDUSTE OLEK ---
    val updateState: UpdateUiState = UpdateUiState.Idle,
    val hasAvailableUpdate: Boolean = false
)
