package ee.minu.kellraadio.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import ee.minu.kellraadio.RadioStationRepository

class SearchViewModelFactory(private val repository: RadioStationRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SearchViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SearchViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}