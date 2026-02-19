package ws.ct.radiow.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ws.ct.radiow.RadioBrowserStation
import ws.ct.radiow.RadioFilterItem
import ws.ct.radiow.RadioStationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SearchViewModel(private val repository: RadioStationRepository) : ViewModel() {

    // --- Oleku (State) hoidjad ---
    // Kasutame StateFlow'd, mis on loodud spetsiaalselt ViewModeli ja UI vaheliseks suhtluseks.
    // Private _muutuja hoiab tegelikku väärtust, public muutuja on ainult lugemiseks.

    private val _query = MutableStateFlow("")
    val query = _query.asStateFlow()

    private val _results = MutableStateFlow<List<RadioBrowserStation>>(emptyList())
    val results = _results.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _hasSearched = MutableStateFlow(false)
    val hasSearched = _hasSearched.asStateFlow()

    private val _selectedCountry = MutableStateFlow<RadioFilterItem?>(null)
    val selectedCountry = _selectedCountry.asStateFlow()

    private val _selectedGenre = MutableStateFlow<String?>(null)
    val selectedGenre = _selectedGenre.asStateFlow()

    private val _showFilterSheet = MutableStateFlow(false)
    val showFilterSheet = _showFilterSheet.asStateFlow()

    private val _filterType = MutableStateFlow("")
    val filterType = _filterType.asStateFlow()

    private val _filterItems = MutableStateFlow<List<RadioFilterItem>>(emptyList())
    val filterItems = _filterItems.asStateFlow()

    private val _showManualDialog = MutableStateFlow(false)
    val showManualDialog = _showManualDialog.asStateFlow()

    private var cachedCountries: List<RadioFilterItem> = emptyList()

    // Kerimise asukoha meelespidamiseks
    val listState = LazyListState()


    // --- Sündmuste (Events) käsitlejad ---
    // UI kutsub neid funktsioone, kui kasutaja midagi teeb.

    fun onQueryChange(newQuery: String) {
        _query.value = newQuery
    }

    fun performSearch() {
        if (_query.value.trim().length < 2 && _selectedCountry.value == null && _selectedGenre.value == null) {
            // Siia võiks lisada vea näitamise UI-s, aga hoiame esialgu lihtsana.
            return
        }
        _isLoading.value = true
        _hasSearched.value = true

        viewModelScope.launch {
            val searchResults = repository.searchStations(_query.value, _selectedCountry.value?.isoCode, _selectedGenre.value)
            _results.value = searchResults
            _isLoading.value = false
            if (searchResults.isNotEmpty()) {
                listState.scrollToItem(0)
            }
        }
    }

    fun onCountrySelected(country: RadioFilterItem?) {
        _selectedCountry.value = country
        // Otsime automaatselt, kui filter muutub
        if (_query.value.length >= 2 || country != null || _selectedGenre.value != null) {
            performSearch()
        }
    }

    fun onGenreSelected(genreName: String?) {
        _selectedGenre.value = genreName
        if (_query.value.length >= 2 || _selectedCountry.value != null || genreName != null) {
            performSearch()
        }
    }

    fun openFilter(type: String) {
        viewModelScope.launch {
            _isLoading.value = true
            val items = if (type == "COUNTRY") repository.getCountries() else repository.getTags()
            if (type == "COUNTRY") cachedCountries = items
            _filterItems.value = items
            _filterType.value = type
            _isLoading.value = false
            _showFilterSheet.value = true
        }
    }

    fun closeFilterSheet() {
        _showFilterSheet.value = false
    }

    fun openManualAddDialog() {
        _showManualDialog.value = true
        // Tagame, et riigid on olemas
        if (cachedCountries.isEmpty()) {
            viewModelScope.launch {
                cachedCountries = repository.getCountries()
            }
        }
    }

    fun closeManualAddDialog() {
        _showManualDialog.value = false
    }

    fun countryListForManual(): List<RadioFilterItem> = cachedCountries
}