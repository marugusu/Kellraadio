package app.radiorecalarm.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.radiorecalarm.RadioBrowserStation
import app.radiorecalarm.RadioFilterItem
import app.radiorecalarm.RadioStationRepository
import app.radiorecalarm.StationFormLogic
import app.radiorecalarm.getCachedCountryDisplayName
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SearchViewModel(private val repository: RadioStationRepository) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query = _query.asStateFlow()

    private val _results = MutableStateFlow<List<RadioBrowserStation>>(emptyList())
    val results = _results.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _hasSearched = MutableStateFlow(false)
    val hasSearched = _hasSearched.asStateFlow()

    private val _searchError = MutableStateFlow(false)
    val searchError = _searchError.asStateFlow()

    private val _queryTooShortHint = MutableStateFlow(false)
    val queryTooShortHint = _queryTooShortHint.asStateFlow()

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

    data class ManualDialogData(
        val name: String = "",
        val url: String = "",
        val country: String = "",
        val iconUrl: String = ""
    )
    private val _manualDialogInitialData = MutableStateFlow(ManualDialogData())
    val manualDialogInitialData = _manualDialogInitialData.asStateFlow()

    private var cachedCountries: List<RadioFilterItem> = emptyList()

    val listState = LazyListState()

    fun onQueryChange(newQuery: String) {
        _query.value = newQuery
        _queryTooShortHint.value = false
    }

    fun performSearch() {
        val trimmedQuery = _query.value.trim()
        if (trimmedQuery.length < 2 && _selectedCountry.value == null && _selectedGenre.value == null) {
            _queryTooShortHint.value = true
            return
        }
        _queryTooShortHint.value = false
        _searchError.value = false
        _isLoading.value = true
        _hasSearched.value = true

        viewModelScope.launch {
            val result = repository.searchStationsResult(
                query = trimmedQuery,
                countryCode = _selectedCountry.value?.isoCode,
                tag = _selectedGenre.value
            )
            result.onSuccess { searchResults ->
                _results.value = searchResults
                _searchError.value = false
                if (searchResults.isNotEmpty()) {
                    listState.scrollToItem(0)
                }
            }.onFailure {
                _results.value = emptyList()
                _searchError.value = true
            }
            _isLoading.value = false
        }
    }

    fun retrySearch() {
        performSearch()
    }

    fun applyQuickFilter(countryCode: String? = null, genre: String? = null) {
        _query.value = ""
        _queryTooShortHint.value = false
        _selectedCountry.value = countryCode?.let { code ->
            val upper = code.uppercase()
            cachedCountries.find { it.isoCode.equals(upper, ignoreCase = true) }
                ?: RadioFilterItem(
                    name = getCachedCountryDisplayName(upper).ifBlank { upper },
                    stationCount = 0,
                    isoCode = upper
                )
        }
        _selectedGenre.value = genre
        performSearch()
    }

    fun onCountrySelected(country: RadioFilterItem?) {
        _selectedCountry.value = country
        _queryTooShortHint.value = false
        if (_query.value.trim().length >= 2 || country != null || _selectedGenre.value != null) {
            performSearch()
        }
    }

    fun onGenreSelected(genreName: String?) {
        _selectedGenre.value = genreName
        _queryTooShortHint.value = false
        if (_query.value.trim().length >= 2 || _selectedCountry.value != null || genreName != null) {
            performSearch()
        }
    }

    fun openFilter(type: String) {
        viewModelScope.launch {
            _isLoading.value = true
            var items = if (type == "COUNTRY") repository.getCountries() else repository.getTags()

            if (type == "COUNTRY") {
                items = if (items.isEmpty()) {
                    StationFormLogic.buildCountryPickerItems(emptyList(), emptyList())
                } else {
                    items.sortedBy {
                        if (it.isoCode == "EE" || it.name.equals("Estonia", ignoreCase = true)) 0 else 1
                    }
                }
                cachedCountries = items
            }

            _filterItems.value = items
            _filterType.value = type
            _isLoading.value = false
            _showFilterSheet.value = true
        }
    }

    fun closeFilterSheet() {
        _showFilterSheet.value = false
    }

    fun openManualAddDialog(station: RadioBrowserStation? = null) {
        if (station != null) {
            _manualDialogInitialData.value = ManualDialogData(
                name = station.name,
                url = station.urlResolved,
                country = station.countryCode,
                iconUrl = station.favicon
            )
        } else {
            _manualDialogInitialData.value = ManualDialogData()
        }

        _showManualDialog.value = true

        if (cachedCountries.isEmpty()) {
            viewModelScope.launch {
                val remote = repository.getCountries()
                if (remote.isNotEmpty()) {
                    cachedCountries = remote.sortedBy {
                        if (it.isoCode == "EE" || it.name.equals("Estonia", ignoreCase = true)) 0 else 1
                    }
                }
            }
        }
    }

    fun closeManualAddDialog() {
        _showManualDialog.value = false
    }

    fun countryListForManual(): List<RadioFilterItem> = cachedCountries
}
