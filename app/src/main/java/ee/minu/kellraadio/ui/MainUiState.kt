package ee.minu.kellraadio.ui

import ee.minu.kellraadio.Alarm
import ee.minu.kellraadio.RadioStation
import ee.minu.kellraadio.SongAdditionalInfo

data class MainUiState(
    // --- RAADIO MÄNGIJA OLEK ---
    val isPlaying: Boolean = false,
    val playerStatus: String = "",
    val bitrate: String = "",
    val activeStationName: String = "",

    // --- METAANDMED ---
    val parsedArtist: String = "",
    val parsedTitle: String = "",
    val parsedExtra: String = "",
    val songInfo: SongAdditionalInfo? = null,

    // --- ANDMED ---
    val stations: List<RadioStation> = emptyList(),
    val alarms: List<Alarm> = emptyList(),

    // --- UI KONFIGURATSIOON ---
    val isRefreshing: Boolean = false,
    val sleepTimerRemaining: Long = 0L,
    val showFlags: Boolean = true,
    val colsPortrait: Int = 3,
    val colsLandscape: Int = 3,

    // --- NAVIGATSIOON JA VALIKUD ---
    val currentTab: Int = 0,
    val selectedCategory: String = "ERR",
    val selectedStationId: Int = -1,

    // --- DIALOOGIDE OLEKUD (UUS) ---
    val showSleepDialog: Boolean = false,
    val showAlarmDialog: Boolean = false,
    val alarmToEdit: Alarm? = null, // Kui null, siis lisame uue. Kui olemas, siis muudame.
    val showActionSheetForStation: RadioStation? = null, // Millise jaama menüü on lahti?
    val showSongInfoSheet: Boolean = false,
    val stationToDelete: RadioStation? = null // Kustutamise kinnitusaken
)