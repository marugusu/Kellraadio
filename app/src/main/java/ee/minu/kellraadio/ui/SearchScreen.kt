package ee.minu.kellraadio.ui

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import ee.minu.kellraadio.RadioBrowserStation
import ee.minu.kellraadio.RadioFilterItem
import ee.minu.kellraadio.RadioStationRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    repository: RadioStationRepository,
    allStations: List<ee.minu.kellraadio.RadioStation>,
    activeUrl: String,
    onPlayTest: (String, String) -> Unit,
    onStationAdded: () -> Unit,
    modifier: Modifier = Modifier
)  {
    val savedIdentifiers = remember(allStations) {
        allStations.flatMap { listOf(it.url, it.uuid) }.filter { it.isNotEmpty() }.toSet()
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val isLandscape = LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<RadioBrowserStation>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var hasSearched by remember { mutableStateOf(false) }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()


    // FILTRITE OLEKUD
    var selectedCountry by remember { mutableStateOf<String?>(null) }
    var selectedGenre by remember { mutableStateOf<String?>(null) }

    // SHEET OLEKUD
    var showFilterSheet by remember { mutableStateOf(false) }
    var filterType by remember { mutableStateOf("") } // "COUNTRY" või "GENRE"
    var filterItems by remember { mutableStateOf<List<RadioFilterItem>>(emptyList()) }

    var showManualDialog by remember { mutableStateOf(false) }

    LaunchedEffect(results) {
        if (results.isNotEmpty()) {
            listState.scrollToItem(0)
        }
    }

    fun performSearch() {
        // Lubame otsida ka tühja nimega, kui filter on valitud
        if (query.trim().length < 2 && selectedCountry == null && selectedGenre == null) {
            Toast.makeText(context, "Sisesta nimi või vali filter", Toast.LENGTH_SHORT).show()
            return
        }
        keyboardController?.hide()
        focusManager.clearFocus()
        isLoading = true
        hasSearched = true

        scope.launch {
            results = repository.searchStations(query, selectedCountry, selectedGenre)
            isLoading = false
            if (results.isEmpty()) {
                Toast.makeText(context, "Ei leidnud midagi", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Kui filter muutub, otsime automaatselt (kui on midagi otsida)
    LaunchedEffect(selectedCountry, selectedGenre) {
        if (query.length >= 2 || selectedCountry != null || selectedGenre != null) {
            performSearch()
        }
    }

    fun openFilter(type: String) {
        scope.launch {
            isLoading = true
            filterItems = if (type == "COUNTRY") repository.getCountries() else repository.getTags()
            filterType = type
            isLoading = false
            showFilterSheet = true
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // --- PÄIS ---
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
                text = "Lisa kanal",
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

            // OTSINGURIBA
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Otsi jaama...") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = ""; performSearch() }) {
                            Icon(Icons.Default.Close, null)
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { performSearch() }),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // FILTRITE RIDA
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // RIIK (50% laiusest)
                FilterChip(
                    selected = selectedCountry != null,
                    onClick = {
                        if (selectedCountry == null) openFilter("COUNTRY") else selectedCountry = null
                    },
                    // Lühendame teksti, kui liiga pikk
                    label = {
                        Text(
                            text = selectedCountry ?: "Kõik riigid",
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    },
                    leadingIcon = {
                        if (selectedCountry != null) Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp))
                        else Icon(Icons.Default.Public, null, modifier = Modifier.size(16.dp))
                    },
                    modifier = Modifier.weight(1f) // Jagab ruumi võrdselt
                )

                // ŽANR (50% laiusest)
                FilterChip(
                    selected = selectedGenre != null,
                    onClick = {
                        if (selectedGenre == null) openFilter("GENRE") else selectedGenre = null
                    },
                    // Lühendame teksti
                    label = {
                        Text(
                            text = selectedGenre ?: "Kõik žanrid",
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    },
                    leadingIcon = {
                        if (selectedGenre != null) Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp))
                        else Icon(Icons.Default.MusicNote, null, modifier = Modifier.size(16.dp))
                    },
                    modifier = Modifier.weight(1f) // Jagab ruumi võrdselt
                )
            }

            // NUPUD
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { performSearch() },
                    enabled = !isLoading,
                    modifier = Modifier.weight(1f)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Text("Otsi")
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                TextButton(onClick = { showManualDialog = true }) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Lisa käsitsi")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // TULEMUSED
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
                            onPlay = { onPlayTest(station.name, station.urlResolved) },
                            onAdd = {
                                scope.launch {
                                    repository.saveUserStation(station.name, station.urlResolved, station.countryCode)
                                    Toast.makeText(context, "Lisatud: ${station.name}", Toast.LENGTH_SHORT).show()
                                    onStationAdded()
                                }
                            }
                        )
                    }
                }
            } else if (hasSearched && !isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Tulemusi ei leitud.", color = Color.Gray)
                }
            }
        }
    }

    // FILTRI MENÜÜ (SHEET)
    if (showFilterSheet) {
        FilterSheet(
            title = if (filterType == "COUNTRY") "Vali riik" else "Vali žanr",
            items = filterItems,
            onDismiss = { showFilterSheet = false },
            onSelect = { item ->
                if (filterType == "COUNTRY") selectedCountry = item.name else selectedGenre = item.name
                showFilterSheet = false
            }
        )
    }

    if (showManualDialog) {
        ManualAddDialog(
            onDismiss = { showManualDialog = false },
            onTest = onPlayTest,
            onSave = { name, url ->
                scope.launch {
                    repository.saveUserStation(name, url)
                    Toast.makeText(context, "Lisatud: $name", Toast.LENGTH_SHORT).show()
                    showManualDialog = false
                    onStationAdded()
                }
            }
        )
    }
}

// UUS KOMPONENT: Filtri valik (otsinguga nimekiri)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterSheet(
    title: String,
    items: List<RadioFilterItem>,
    onDismiss: () -> Unit,
    onSelect: (RadioFilterItem) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    // Filtreerime nimekirja lokaalselt
    val filteredItems = remember(items, searchQuery) {
        if (searchQuery.isBlank()) items else items.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxHeight(0.8f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(16.dp)
            )

            // Kiire otsing nimekirja sees
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Otsi nimekirjast...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn {
                items(filteredItems) { item ->
                    // Genereerime lipu, kui kood on olemas
                    val flag = if (item.isoCode != null) ee.minu.kellraadio.ui.getFlagEmoji(item.isoCode) else ""

                    ListItem(
                        headlineContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (flag.isNotEmpty()) {
                                    Text(flag, modifier = Modifier.padding(end = 8.dp))
                                }
                                Text(item.name)
                            }
                        },
                        supportingContent = { Text("${item.stationCount} jaama") },
                        modifier = Modifier.clickable { onSelect(item) }
                    )
                    Divider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 0.5.dp)
                }
            }
        }
    }
}

// See on juba olemas (SearchResultItem), aga peab olema siin failis (või importida)
@Composable
fun SearchResultItem(
    station: RadioBrowserStation,
    isPlaying: Boolean,
    isSaved: Boolean,
    onPlay: () -> Unit,
    onAdd: () -> Unit
) {
    val flag = ee.minu.kellraadio.ui.getFlagEmoji(station.countryCode)

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. PLAY/STOP NUPP (VASAKUL)
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

            // 2. INFO (KESKEL)
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

            // 3. LISA NUPP (PAREMAL)
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

// ... ManualAddDialog ja EditStationDialog jäävad faili lõppu nagu enne ...
// (Lühendasin siin ruumi säästmiseks, aga sinu failis peavad need alles jääma)
@Composable
fun ManualAddDialog(onDismiss: () -> Unit, onTest: (String, String) -> Unit, onSave: (String, String) -> Unit) {
    // ... (sama sisu mis enne) ...
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text("Lisa jaam käsitsi") },
        text = { Column { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Jaama nimi") }, modifier = Modifier.fillMaxWidth()); OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text("Striimi URL") }, modifier = Modifier.fillMaxWidth()) } },
        confirmButton = { Button(onClick = { onSave(name, url) }) { Text("Salvesta") } },
        dismissButton = { Row { TextButton(onClick = { if(url.isNotBlank()) onTest(if(name.isNotBlank()) name else "Tundmatu", url) }) { Text("Testi") }; TextButton(onClick = onDismiss) { Text("Loobu") } } }
    )
}

@Composable
fun EditStationDialog(stationName: String, stationUrl: String, onDismiss: () -> Unit, onTest: (String, String) -> Unit, onSave: (String, String) -> Unit) {
    // ... (sama sisu mis enne) ...
    var name by remember { mutableStateOf(stationName) }
    var url by remember { mutableStateOf(stationUrl) }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text("Muuda jaama") },
        text = { Column { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Jaama nimi") }, modifier = Modifier.fillMaxWidth()); OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text("Striimi URL") }, modifier = Modifier.fillMaxWidth()) } },
        confirmButton = { Button(onClick = { onSave(name, url) }) { Text("Salvesta") } },
        dismissButton = { Row { TextButton(onClick = { if(url.isNotBlank()) onTest(if(name.isNotBlank()) name else "Tundmatu", url) }) { Text("Testi") }; TextButton(onClick = onDismiss) { Text("Loobu") } } }
    )
}