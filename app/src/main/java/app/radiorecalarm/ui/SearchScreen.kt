package app.radiorecalarm.ui

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.radiorecalarm.R
import app.radiorecalarm.RadioBrowserStation
import app.radiorecalarm.RadioFilterItem
import app.radiorecalarm.RadioStation
import app.radiorecalarm.RadioStationRepository
import app.radiorecalarm.getCachedCountryDisplayName
import app.radiorecalarm.getFlagEmoji
import app.radiorecalarm.ui.theme.KellraadioTheme

private data class QuickBrowsePreset(
    val label: String,
    val countryCode: String? = null,
    val genre: String? = null
)

@Composable
fun SearchScreen(
    repository: RadioStationRepository,
    allStations: List<RadioStation>,
    allCategories: List<String>,
    activeUrl: String,
    isPlayerPlaying: Boolean,
    playbackState: StationPlaybackState = if (isPlayerPlaying) StationPlaybackState.PLAYING else StationPlaybackState.IDLE,
    countries: List<RadioFilterItem> = emptyList(),
    onPlayTest: (String, String, Boolean) -> Unit,
    onStopPlayback: () -> Unit = {},
    onSaveStation: (String, String, String, String, String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = viewModel(factory = SearchViewModelFactory(repository))
) {
    val query by viewModel.query.collectAsState()
    val results by viewModel.results.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val hasSearched by viewModel.hasSearched.collectAsState()
    val searchError by viewModel.searchError.collectAsState()
    val queryTooShortHint by viewModel.queryTooShortHint.collectAsState()
    val selectedCountry by viewModel.selectedCountry.collectAsState()
    val selectedGenre by viewModel.selectedGenre.collectAsState()
    val showFilterSheet by viewModel.showFilterSheet.collectAsState()
    val filterType by viewModel.filterType.collectAsState()
    val filterItems by viewModel.filterItems.collectAsState()
    val showManualDialog by viewModel.showManualDialog.collectAsState()
    val manualDialogInitialData by viewModel.manualDialogInitialData.collectAsState()
    val listState = viewModel.listState

    val savedIdentifiers = remember(allStations) {
        allStations.flatMap { listOf(it.url, it.uuid) }.filter { it.isNotEmpty() }.toSet()
    }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    fun performSearchWithUIEffects() {
        keyboardController?.hide()
        focusManager.clearFocus()
        viewModel.performSearch()
    }

    SearchScreenContent(
        query = query,
        onQueryChange = viewModel::onQueryChange,
        onClearQuery = {
            viewModel.onQueryChange("")
            performSearchWithUIEffects()
        },
        onSearchClick = { performSearchWithUIEffects() },
        onManualAddClick = { viewModel.openManualAddDialog() },
        selectedCountry = selectedCountry,
        selectedGenre = selectedGenre,
        onOpenCountryFilter = { viewModel.openFilter("COUNTRY") },
        onClearCountryFilter = { viewModel.onCountrySelected(null) },
        onOpenGenreFilter = { viewModel.openFilter("GENRE") },
        onClearGenreFilter = { viewModel.onGenreSelected(null) },
        onQuickFilterClick = { countryCode, genre ->
            keyboardController?.hide()
            focusManager.clearFocus()
            viewModel.applyQuickFilter(countryCode = countryCode, genre = genre)
        },
        onRetrySearch = { viewModel.retrySearch() },
        results = results,
        savedIdentifiers = savedIdentifiers,
        isLoading = isLoading,
        hasSearched = hasSearched,
        searchError = searchError,
        queryTooShortHint = queryTooShortHint,
        activeUrl = activeUrl,
        playbackState = playbackState,
        listState = listState,
        onPlayResult = { station, isSaved ->
            onPlayTest(station.name, station.urlResolved, isSaved)
        },
        onAddResult = { station ->
            viewModel.openManualAddDialog(station)
        },
        modifier = modifier
    )

    if (showFilterSheet) {
        FilterSheet(
            title = if (filterType == "COUNTRY") stringResource(R.string.filter_country) else stringResource(R.string.filter_genre),
            items = filterItems,
            selectedIsoCode = if (filterType == "COUNTRY") selectedCountry?.isoCode else null,
            onDismiss = { viewModel.closeFilterSheet() },
            onSelect = { item ->
                if (filterType == "COUNTRY") {
                    viewModel.onCountrySelected(item)
                } else {
                    viewModel.onGenreSelected(item.name)
                }
                viewModel.closeFilterSheet()
            }
        )
    }

    if (showManualDialog) {
        val unknownName = stringResource(R.string.unknown_station_name)
        val mergedCountries = remember(countries, viewModel.countryListForManual()) {
            viewModel.countryListForManual().ifEmpty { countries }
        }
        StationEditorSheet(
            title = stringResource(R.string.manual_dialog_title),
            initialName = manualDialogInitialData.name,
            initialUrl = manualDialogInitialData.url,
            initialCountryCode = manualDialogInitialData.country,
            initialSubgroup = "",
            initialIconUrl = manualDialogInitialData.iconUrl,
            allCountries = mergedCountries,
            allSubgroups = allCategories,
            existingStations = allStations,
            activeStreamUrl = activeUrl,
            playbackState = playbackState,
            onDismiss = { viewModel.closeManualAddDialog() },
            onTest = { name, url -> onPlayTest(name.ifBlank { unknownName }, url, false) },
            onStopTest = onStopPlayback,
            onSave = { name, url, country, category, iconUrl ->
                onSaveStation(name, url, country, category, iconUrl)
                viewModel.closeManualAddDialog()
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SearchScreenContent(
    query: String,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    onSearchClick: () -> Unit,
    onManualAddClick: () -> Unit,
    selectedCountry: RadioFilterItem?,
    selectedGenre: String?,
    onOpenCountryFilter: () -> Unit,
    onClearCountryFilter: () -> Unit,
    onOpenGenreFilter: () -> Unit,
    onClearGenreFilter: () -> Unit,
    onQuickFilterClick: (countryCode: String?, genre: String?) -> Unit,
    onRetrySearch: () -> Unit,
    results: List<RadioBrowserStation>,
    savedIdentifiers: Set<String>,
    isLoading: Boolean,
    hasSearched: Boolean,
    searchError: Boolean,
    queryTooShortHint: Boolean,
    activeUrl: String,
    playbackState: StationPlaybackState,
    listState: LazyListState,
    onPlayResult: (RadioBrowserStation, Boolean) -> Unit,
    onAddResult: (RadioBrowserStation) -> Unit,
    modifier: Modifier = Modifier
) {
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val estoniaName = remember { getCachedCountryDisplayName("EE").ifBlank { "Estonia" } }
    val quickPresets = remember(estoniaName) {
        listOf(
            QuickBrowsePreset(label = "${getFlagEmoji("EE")} $estoniaName", countryCode = "EE"),
            QuickBrowsePreset(label = "Pop", genre = "pop"),
            QuickBrowsePreset(label = "Rock", genre = "rock"),
            QuickBrowsePreset(label = "News", genre = "news"),
            QuickBrowsePreset(label = "Jazz", genre = "jazz"),
            QuickBrowsePreset(label = "Classical", genre = "classical"),
            QuickBrowsePreset(label = "Electronic", genre = "electronic")
        )
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(start = if (isLandscape) 8.dp else 16.dp, end = 16.dp)
                .padding(top = if (isLandscape) 12.dp else 0.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.search_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = if (isLandscape) 8.dp else 16.dp)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text(stringResource(R.string.search_placeholder)) },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = onClearQuery) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_clear))
                        }
                    }
                },
                isError = queryTooShortHint,
                supportingText = if (queryTooShortHint) {
                    {
                        Text(
                            text = stringResource(R.string.search_query_too_short),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                } else {
                    null
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearchClick() }),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedCountry != null,
                    onClick = onOpenCountryFilter,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(
                        1.dp,
                        if (selectedCountry != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                    ),
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        labelColor = MaterialTheme.colorScheme.onSurface,
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    label = {
                        Text(
                            text = selectedCountry?.let {
                                val flag = it.isoCode?.let(::getFlagEmoji).orEmpty()
                                if (flag.isNotEmpty()) "$flag ${it.name}" else it.name
                            } ?: stringResource(R.string.filter_all_countries),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = if (selectedCountry != null) Icons.Default.Check else Icons.Default.Public,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    trailingIcon = if (selectedCountry != null) {
                        {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.search_clear_filter),
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable(onClick = onClearCountryFilter)
                            )
                        }
                    } else {
                        null
                    },
                    modifier = Modifier.weight(1f)
                )

                FilterChip(
                    selected = selectedGenre != null,
                    onClick = onOpenGenreFilter,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(
                        1.dp,
                        if (selectedGenre != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                    ),
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        labelColor = MaterialTheme.colorScheme.onSurface,
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    label = {
                        Text(
                            text = selectedGenre ?: stringResource(R.string.filter_all_genres),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = if (selectedGenre != null) Icons.Default.Check else Icons.Default.MusicNote,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    trailingIcon = if (selectedGenre != null) {
                        {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.search_clear_filter),
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable(onClick = onClearGenreFilter)
                            )
                        }
                    } else {
                        null
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onSearchClick,
                    enabled = !isLoading,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color.Black
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.Black, strokeWidth = 2.dp)
                    } else {
                        Text(stringResource(R.string.action_search), fontWeight = FontWeight.Bold)
                    }
                }

                OutlinedButton(
                    onClick = onManualAddClick,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.search_manual), fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            when {
                searchError && !isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.WifiOff,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(36.dp)
                            )
                            Text(
                                text = stringResource(R.string.search_network_error),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            OutlinedButton(
                                onClick = onRetrySearch,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Text(stringResource(R.string.action_retry))
                            }
                        }
                    }
                }
                results.isNotEmpty() -> {
                    LazyColumn(
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 80.dp)
                    ) {
                        items(results, key = { it.stationUuid }) { station ->
                            val isAlreadySaved = savedIdentifiers.contains(station.stationUuid) ||
                                savedIdentifiers.contains(station.urlResolved)
                            val isCurrentResult = station.urlResolved == activeUrl
                            val itemPlaybackState = if (isCurrentResult) playbackState else StationPlaybackState.IDLE

                            SearchResultItem(
                                station = station,
                                playbackState = itemPlaybackState,
                                isSaved = isAlreadySaved,
                                onPlay = { onPlayResult(station, isAlreadySaved) },
                                onAdd = { onAddResult(station) }
                            )
                        }
                    }
                }
                hasSearched && !isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Radio,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(36.dp)
                            )
                            Text(
                                text = stringResource(R.string.search_no_results),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            OutlinedButton(
                                onClick = onManualAddClick,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.search_manual))
                            }
                        }
                    }
                }
                else -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(bottom = 80.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = stringResource(R.string.search_quick_browse),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                quickPresets.forEach { preset ->
                                    AssistChip(
                                        onClick = { onQuickFilterClick(preset.countryCode, preset.genre) },
                                        label = { Text(preset.label) },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = AssistChipDefaults.assistChipColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                            labelColor = MaterialTheme.colorScheme.onSurface
                                        ),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                                    )
                                }
                            }
                        }

                        OutlinedCard(
                            onClick = onManualAddClick,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            colors = CardDefaults.outlinedCardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AddCircleOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.search_manual_hint_title),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = stringResource(R.string.search_manual_hint_desc),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SearchResultItem(
    station: RadioBrowserStation,
    playbackState: StationPlaybackState,
    isSaved: Boolean,
    onPlay: () -> Unit,
    onAdd: () -> Unit
) {
    val flag = getFlagEmoji(station.countryCode)
    val isActive = playbackState == StationPlaybackState.PLAYING || playbackState == StationPlaybackState.BUFFERING
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StationLogoBadge(
                stationName = station.name,
                iconUrl = station.favicon,
                size = 40.dp,
                cornerRadius = 8.dp,
                borderColor = if (isActive) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outline
                },
                loadedBorderColor = if (isActive) {
                    MaterialTheme.colorScheme.primary
                } else {
                    Color.Transparent
                },
                fallbackBackground = MaterialTheme.colorScheme.surface,
                fallbackTextColor = if (isActive) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = station.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val infoText = buildString {
                    if (flag.isNotEmpty()) append("$flag ")
                    append(station.country)
                    append(" • ${station.bitrate} kbps")
                    if (station.votes > 0) append(" • 👍 ${station.votes}")
                }
                Text(
                    text = infoText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(
                onClick = onPlay,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)),
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = if (isActive) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer,
                    contentColor = if (isActive) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onPrimaryContainer
                )
            ) {
                when (playbackState) {
                    StationPlaybackState.BUFFERING -> CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                    StationPlaybackState.PLAYING -> Icon(Icons.Default.Pause, null)
                    else -> Icon(Icons.Default.PlayArrow, null)
                }
            }
            Spacer(modifier = Modifier.width(4.dp))
            IconButton(
                onClick = {
                    if (!isSaved) {
                        onAdd()
                    }
                },
                enabled = !isSaved
            ) {
                if (isSaved) {
                    Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                } else {
                    Icon(Icons.Default.AddCircleOutline, null, tint = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0C10)
@Composable
private fun SearchScreenInitialPreview() {
    KellraadioTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            SearchScreenContent(
                query = "",
                onQueryChange = {},
                onClearQuery = {},
                onSearchClick = {},
                onManualAddClick = {},
                selectedCountry = null,
                selectedGenre = null,
                onOpenCountryFilter = {},
                onClearCountryFilter = {},
                onOpenGenreFilter = {},
                onClearGenreFilter = {},
                onQuickFilterClick = { _, _ -> },
                onRetrySearch = {},
                results = emptyList(),
                savedIdentifiers = emptySet(),
                isLoading = false,
                hasSearched = false,
                searchError = false,
                queryTooShortHint = false,
                activeUrl = "",
                playbackState = StationPlaybackState.IDLE,
                listState = rememberLazyListState(),
                onPlayResult = { _, _ -> },
                onAddResult = {}
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0C10)
@Composable
private fun SearchScreenResultsPreview() {
    KellraadioTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            SearchScreenContent(
                query = "Rock",
                onQueryChange = {},
                onClearQuery = {},
                onSearchClick = {},
                onManualAddClick = {},
                selectedCountry = RadioFilterItem(name = "Estonia", stationCount = 42, isoCode = "EE"),
                selectedGenre = "rock",
                onOpenCountryFilter = {},
                onClearCountryFilter = {},
                onOpenGenreFilter = {},
                onClearGenreFilter = {},
                onQuickFilterClick = { _, _ -> },
                onRetrySearch = {},
                results = listOf(
                    RadioBrowserStation(
                        stationUuid = "1",
                        name = "Rock FM",
                        url = "https://stream.skymedia.ee/live/RockFM",
                        urlResolved = "https://stream.skymedia.ee/live/RockFM",
                        homepage = "",
                        country = "Estonia",
                        countryCode = "EE",
                        language = "estonian",
                        bitrate = 128,
                        votes = 210,
                        clickcount = 1000
                    )
                ),
                savedIdentifiers = emptySet(),
                isLoading = false,
                hasSearched = true,
                searchError = false,
                queryTooShortHint = false,
                activeUrl = "https://stream.skymedia.ee/live/RockFM",
                playbackState = StationPlaybackState.PLAYING,
                listState = rememberLazyListState(),
                onPlayResult = { _, _ -> },
                onAddResult = {}
            )
        }
    }
}
