package ws.ct.radiow.ui

import ws.ct.radiow.Alarm
import ws.ct.radiow.RadioFilterItem
import ws.ct.radiow.RadioStation
import ws.ct.radiow.SongAdditionalInfo

data class MainUiState(
    // --- RAADIO MÄNGIJA OLEK ---
    val isPlaying: Boolean = false,
    val playerStatus: String = "",
    val bitrate: String = "",
    val activeStationName: String = "",
    val activeStreamUrl: String = "",

    // --- METAANDMED ---
    val parsedArtist: String = "",
    val parsedTitle: String = "",
    val parsedExtra: String = "",
    val songInfo: SongAdditionalInfo? = null,

    // --- ANDMED ---
    val stations: List<RadioStation> = emptyList(),
    val alarms: List<Alarm> = emptyList(),
    val countries: List<RadioFilterItem> = emptyList(),

    // --- UI KONFIGURATSIOON ---
    val isRefreshing: Boolean = false,
    val isCountriesLoading: Boolean = false,
    val sleepTimerRemaining: Long = 0L,
    val showFlags: Boolean = true,
    val colsPortrait: Int = 3,
    val colsLandscape: Int = 3,

    // --- NAVIGATSIOON JA VALIKUD ---
    val currentTab: Int = 0,
    val selectedCategory: String = "ERR",
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
    val showResetOrderDialog: Boolean = false // UUS
)