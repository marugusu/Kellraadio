package ws.ct.radiow.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ws.ct.radiow.MusicInfoRepository
import ws.ct.radiow.SongAdditionalInfo
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PlayerViewModel : ViewModel() {

    // Hoiab infot laulu kohta (see, mida UI kuulab)
    private val _songInfo = MutableStateFlow<SongAdditionalInfo?>(null)
    val songInfo = _songInfo.asStateFlow()

    // Hoiab meeles viimast päringut, et saaksime tühistada, kui laul muutub
    private var fetchJob: Job? = null

    // Hoiab meeles, mis laulu me viimati otsisime (et mitte korrata)
    private var lastArtist = ""
    private var lastTitle = ""

    fun fetchSongInfo(artist: String, title: String) {
        // 1. Kontrollime, kas on üldse vaja otsida
        if (artist.isBlank() || title.isBlank()) {
            _songInfo.value = null
            return
        }

        // Kui laul on sama, mis juba ees, ära tee midagi (stabiilsus)
        if (artist == lastArtist && title == lastTitle) return

        // 2. Uus laul -> Salvestame ja tühistame eelmise otsingu
        lastArtist = artist
        lastTitle = title
        fetchJob?.cancel()

        // Nullime vana info kohe, et ei näitaks eelmise laulu pilti
        _songInfo.value = null

        // 3. Käivitame uue otsingu stabiilses alas (viewModelScope)
        fetchJob = viewModelScope.launch {
            // Väike viivitus (500ms), et mitte koormata API-t,
            // kui kasutaja klõpsib kiiresti lugusid edasi-tagasi
            delay(500)

            val info = MusicInfoRepository.fetchInfo(artist, title)
            _songInfo.value = info
        }
    }
}