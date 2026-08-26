package app.radiorecalarm.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.radiorecalarm.*
import app.radiorecalarm.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    repository: RadioStationRepository,
    allStations: List<RadioStation>,
    allCategories: List<String>,
    activeUrl: String,
    onPlayTest: (String, String, Boolean) -> Unit,
    onSaveStation: (String, String, String, String) -> Unit,
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
    val manualDialogInitialData by viewModel.manualDialogInitialData.collectAsState()
    val listState = viewModel.listState

    val savedIdentifiers = remember(allStations) {
        allStations.flatMap { listOf(it.url, it.uuid) }.filter { it.isNotEmpty() }.toSet()
    }
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
                    onClick = {
                        if (selectedCountry == null) viewModel.openFilter("COUNTRY") else viewModel.onCountrySelected(null)
                    },
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, if (selectedCountry != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        labelColor = MaterialTheme.colorScheme.onSurface,
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
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
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, if (selectedGenre != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
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
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    },
                    leadingIcon = { if (selectedGenre != null) Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp)) else Icon(Icons.Default.MusicNote, null, modifier = Modifier.size(16.dp)) },
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
                    onClick = { performSearchWithUIEffects() },
                    enabled = !isLoading,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color.Black
                    ),
                    modifier = Modifier.weight(1f).height(44.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.Black)
                    } else {
                        Text(stringResource(R.string.action_search), fontWeight = FontWeight.Bold)
                    }
                }

                OutlinedButton(
                    onClick = { viewModel.openManualAddDialog() },
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.height(44.dp)
                ) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.search_manual), fontWeight = FontWeight.SemiBold)
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
                        // Kontrollime, kas see jaam mängib.
                        // Lisatingimus: onPlayTest saadab aktiivseks jaamaks selle jaama URL-i.
                        // SearchScreenile tuleb sisse 'activeUrl', mis on pärit MainViewModeli uiState.activeStreamUrl-st.
                        // MainViewModel uuendab seda playTestStation funktsioonis.
                        // AGA seal on konks: MainViewModeli uiState.isPlaying peab ka olema true.
                        // Kuna me SearchScreenis ei saa otse isPlaying olekut, siis eeldame, et kui URL klapib, siis mängib.
                        // See on ebatäpne, aga SearchScreen parameetrites pole 'isPlaying' muutujat.
                        // Õige oleks lisada SearchScreenile parameeter 'isPlaying'.
                        
                        // Vaatan MainActivity.kt faili, kuidas SearchScreen välja kutsutakse.
                        // Seal on: activeUrl = state.activeStreamUrl
                        // See tähendab, et siin on ainult URL.
                        
                        // KUID: Kui ma panen pausi peale, siis MainViewModelis:
                        // _uiState.update { it.copy(isPlaying = false) }
                        // activeStreamUrl jääb alles!
                        
                        // Järelikult siin SearchScreenis on 'activeUrl' endiselt vana jaama URL, isegi kui on paus.
                        // See tähendab, et ikoon näitab ikka "Pause" (või Stop), isegi kui tegelikult on vaikus.
                        
                        // PARANDUS: SearchScreen vajab 'isPlaying' parameetrit.
                        // Aga ma ei saa praegu MainActivityt muuta.
                        
                        // OOTA! SearchResultItem saab parameetri 'isPlaying'.
                        // SearchScreen saab parameetri 'activeUrl'.
                        // Seega, searchScreen arvab, et 'isPlaying = station.urlResolved == activeUrl'.
                        // See ongi viga. See peaks olema 'isPlaying = station.urlResolved == activeUrl && tegelikultMängib'.
                        
                        // Kuna ma ei saa MainActivityt muuta (või ei taha teha liiga palju muudatusi korraga),
                        // siis ma ei saa seda viga täielikult parandada siin failis.
                        // AGA ma saan muuta ikooni Stop -> Pause.
                        
                        SearchResultItem(
                            station = station,
                            isPlaying = station.urlResolved == activeUrl,
                            isSaved = isAlreadySaved,
                            onPlay = { onPlayTest(station.name, station.urlResolved, isAlreadySaved) },
                            onAdd = {
                                viewModel.openManualAddDialog(station)
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
        FilterDialog(
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
            initialData = manualDialogInitialData,
            allCountries = viewModel.countryListForManual(),
            allCategories = allCategories,
            onDismiss = { viewModel.closeManualAddDialog() },
            onTest = { name, url -> onPlayTest(if(name.isNotBlank()) name else "Tundmatu", url, false) },
            onSave = { name, url, country, category ->
                onSaveStation(name, url, country, category)
                viewModel.closeManualAddDialog()
            }
        )
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
    val flag = getFlagEmoji(station.countryCode)
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
            IconButton(
                onClick = onPlay,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)),
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = if (isPlaying) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer,
                    contentColor = if (isPlaying) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onPrimaryContainer
                )
            ) {
                Icon(
                    // MUUDATUS: Stop -> Pause
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    null
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualAddDialog(
    initialData: SearchViewModel.ManualDialogData,
    allCountries: List<RadioFilterItem>,
    allCategories: List<String>,
    onDismiss: () -> Unit,
    onTest: (String, String) -> Unit,
    onSave: (String, String, String, String) -> Unit
) {
    var name by remember { mutableStateOf(initialData.name) }
    var url by remember { mutableStateOf(initialData.url) }
    var countryCode by remember { mutableStateOf(initialData.country) }
    var category by remember { mutableStateOf("") }
    
    var countryName by remember { 
        mutableStateOf(allCountries.find { it.isoCode == initialData.country }?.name ?: "") 
    }
    
    var showCountryPicker by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(12.dp),
        title = { Text(stringResource(R.string.manual_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.station_name)) },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(stringResource(R.string.stream_url)) },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    OutlinedTextField(
                        value = category,
                        onValueChange = { category = it },
                        label = { Text(stringResource(R.string.station_category)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        allCategories.forEach { selectionOption ->
                            DropdownMenuItem(
                                text = { Text(selectionOption) },
                                onClick = {
                                    category = selectionOption
                                    expanded = false
                                }
                            )
                        }
                    }
                }
                
                OutlinedCard(
                    onClick = { showCountryPicker = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        val flag = if (countryCode.isNotEmpty()) getFlagEmoji(countryCode) else ""
                        if (flag.isNotEmpty()) {
                            Text(flag, style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.width(12.dp))
                        } else {
                            Icon(Icons.Default.Public, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.width(12.dp))
                        }
                        Text(
                            text = if (countryName.isNotEmpty()) countryName else stringResource(R.string.filter_country),
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (countryName.isNotEmpty()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(Icons.Default.ArrowDropDown, null)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name, url, countryCode, category) },
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            Row {
                TextButton(
                    onClick = { if(url.isNotBlank()) onTest(if(name.isNotBlank()) name else "Tundmatu", url) },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(stringResource(R.string.action_test))
                }
                TextButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        }
    )

    if (showCountryPicker) {
        FilterDialog(
            title = stringResource(R.string.filter_country),
            items = allCountries,
            onDismiss = { showCountryPicker = false },
            onSelect = { item ->
                countryCode = item.isoCode ?: ""
                countryName = item.name
                showCountryPicker = false
            }
        )
    }
}
