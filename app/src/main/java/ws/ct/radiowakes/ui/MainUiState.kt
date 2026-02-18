package ws.ct.radiowakes.ui

import ws.ct.radiowakes.Alarm
import ws.ct.radiowakes.RadioFilterItem
import ws.ct.radiowakes.RadioStation
import ws.ct.radiowakes.SongAdditionalInfo

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
    val categoryToSelectOnTabChange: String? = null, // UUS

    // --- DIALOOGIDE OLEKUD (UUS) ---
    val showSleepDialog: Boolean = false,
    val showAlarmDialog: Boolean = false,
    val alarmToEdit: Alarm? = null, // Kui null, siis lisame uue. Kui olemas, siis muudame.
    val showActionSheetForStation: RadioStation? = null, // Millise jaama menüü on lahti?
    val showSongInfoSheet: Boolean = false,
    val stationToDelete: RadioStation? = null, // Kustutamise kinnitusaken
    val stationToEdit: RadioStation? = null
)