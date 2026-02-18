package ws.ct.radiowakes.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import ws.ct.radiowakes.RadioBrowserStation
import ws.ct.radiowakes.RadioFilterItem
import ws.ct.radiowakes.RadioStationRepository
import ws.ct.radiowakes.RadioStation
import ws.ct.radiowakes.R
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    repository: RadioStationRepository,
    allStations: List<RadioStation>,
    activeUrl: String,
    onPlayTest: (String, String, Boolean) -> Unit,
    onSaveStation: (String, String, String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = viewModel(factory = SearchViewModelFactory(repository))
) {
    val query by viewModel.query.collectAsState()
    val results by viewModel.results.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val hasSearched by viewModel.hasSearched.collectAsState()
    val selectedCountry by viewModel.selectedCountry.collectAsState()
    val selectedGenre by viewModel.selectedGenre.collectAsState()
    val showFilterSheet by viewModel.showFilterSheet.collectAsState()
    val filterType by viewModel.filterType.collectAsState()
    val filterItems by viewModel.filterItems.collectAsState()
    val showManualDialog by viewModel.showManualDialog.collectAsState()
    val listState = viewModel.listState

    val savedIdentifiers = remember(allStations) {
        allStations.flatMap { listOf(it.url, it.uuid) }.filter { it.isNotEmpty() }.toSet()
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val isLandscape = LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    fun performSearchWithUIEffects() {
        keyboardController?.hide()
        focusManager.clearFocus()
        viewModel.performSearch()
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
            Spacer(modifier = Modifier.height(0.dp))

            OutlinedTextField(
                value = query,
                onValueChange = { viewModel.onQueryChange(it) },
                placeholder = { Text(stringResource(R.string.search_placeholder)) },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = {
                            viewModel.onQueryChange("")
                            performSearchWithUIEffects()
                        }) {
                            Icon(Icons.Default.Close, null)
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { performSearchWithUIEffects() }),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedCountry != null,
                    onClick = {
                        if (selectedCountry == null) viewModel.openFilter("COUNTRY") else viewModel.onCountrySelected(null)
                    },
                    label = {
                        Text(
                            text = selectedCountry?.name ?: stringResource(R.string.filter_all_countries),
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    },
                    leadingIcon = { if (selectedCountry != null) Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp)) else Icon(Icons.Default.Public, null, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.weight(1f)
                )

                FilterChip(
                    selected = selectedGenre != null,
                    onClick = {
                        if (selectedGenre == null) viewModel.openFilter("GENRE") else viewModel.onGenreSelected(null)
                    },
                    label = {
                        Text(
                            text = selectedGenre ?: stringResource(R.string.filter_all_genres),
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    },
                    leadingIcon = { if (selectedGenre != null) Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp)) else Icon(Icons.Default.MusicNote, null, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { performSearchWithUIEffects() },
                    enabled = !isLoading,
                    modifier = Modifier.weight(1f)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Text(stringResource(R.string.action_search))
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                TextButton(onClick = { viewModel.openManualAddDialog() }) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.search_manual))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (results.isNotEmpty()) {
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(results, key = { it.stationUuid }) { station ->
                        val isAlreadySaved = savedIdentifiers.contains(station.stationUuid) ||
                                savedIdentifiers.contains(station.urlResolved)
                        SearchResultItem(
                            station = station,
                            isPlaying = station.urlResolved == activeUrl,
                            isSaved = isAlreadySaved,
                            onPlay = { onPlayTest(station.name, station.urlResolved, isAlreadySaved) },
                            onAdd = {
                                onSaveStation(station.name, station.urlResolved, station.countryCode)
                            }
                        )
                    }
                }
            } else if (hasSearched && !isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.search_no_results), color = Color.Gray)
                }
            }
        }
    }

    if (showFilterSheet) {
        FilterSheet(
            title = if (filterType == "COUNTRY") stringResource(R.string.filter_country) else stringResource(R.string.filter_genre),
            items = filterItems,
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
        ManualAddDialog(
            onDismiss = { viewModel.closeManualAddDialog() },
            onTest = { name, url -> onPlayTest(if(name.isNotBlank()) name else "Tundmatu", url, false) },
            onSave = { name, url ->
                onSaveStation(name, url, "")
                viewModel.closeManualAddDialog()
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterSheet(
    title: String,
    items: List<RadioFilterItem>,
    onDismiss: () -> Unit,
    onSelect: (RadioFilterItem) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }

    val filteredItems = remember(items, searchQuery) {
        if (searchQuery.isBlank()) items else items.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
        ) {
            Column(modifier = Modifier.padding(top = 24.dp, start = 16.dp, end = 16.dp, bottom = 0.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 16.dp, start = 4.dp)
                )
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(stringResource(R.string.search_placeholder)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    leadingIcon = { Icon(Icons.Default.Search, null) }
                )
                Spacer(modifier = Modifier.height(4.dp))
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    items(filteredItems) { item ->
                        val flag = if (item.isoCode != null) ws.ct.radiowakes.ui.getFlagEmoji(item.isoCode) else ""
                        ListItem(
                            headlineContent = {
                                Text(
                                    text = item.name,
                                    fontWeight = FontWeight.Normal,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            },
                            supportingContent = {
                                Text(
                                    text = "${item.stationCount}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            leadingContent = {
                                if (flag.isNotEmpty()) {
                                    Text(
                                        text = flag,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                }
                            },
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onSelect(item) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SearchResultItem(
    station: RadioBrowserStation,
    isPlaying: Boolean,
    isSaved: Boolean,
    onPlay: () -> Unit,
    onAdd: () -> Unit
) {
    val flag = ws.ct.radiowakes.ui.getFlagEmoji(station.countryCode)
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onPlay,
                modifier = Modifier
                    .size(40.dp)
                    .background(if (isPlaying) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer, CircleShape)
            ) {
                Icon(
                    if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                    null,
                    tint = if (isPlaying) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = station.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                val infoText = buildString {
                    if (flag.isNotEmpty()) append("$flag ")
                    append(station.country)
                    append(" • ${station.bitrate} kbps")
                    if (station.votes > 0) append(" • 👍 ${station.votes}")
                    if (station.clickcount > 0) append(" • 👥 ${station.clickcount}")
                }
                Text(text = infoText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
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

@Composable
fun ManualAddDialog(onDismiss: () -> Unit, onTest: (String, String) -> Unit, onSave: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.manual_dialog_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.station_name)) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(stringResource(R.string.stream_url)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = { Button(onClick = { onSave(name, url) }) { Text(stringResource(R.string.action_save)) } },
        dismissButton = {
            Row {
                TextButton(onClick = { if(url.isNotBlank()) onTest(if(name.isNotBlank()) name else "Tundmatu", url) }) {
                    Text(stringResource(R.string.action_test))
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        }
    )
}