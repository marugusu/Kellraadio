package ee.minu.kellraadio.ui

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ee.minu.kellraadio.HistoryItem
import ee.minu.kellraadio.RadioStationRepository
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    repository: RadioStationRepository,
    onClearHistory: () -> Unit,
    onPlayStationByName: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val historyItems by repository.historyItems.collectAsState(initial = emptyList())
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    // OLEKUD
    var selectedDateMillis by remember { mutableStateOf<Long?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) } // UUS: Hoiatuse olek

    // 1. AKTIIVSED KUUPÄEVAD KALENDRI JAOKS
    val activeDatesUTC = remember(historyItems) {
        val localFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val utcFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        historyItems.map { item ->
            val dateStr = localFormat.format(Date(item.timestamp))
            utcFormat.parse(dateStr)?.time ?: 0L
        }.toSet()
    }

    // 2. FILTREERIMISE LOOGIKA
    val filteredItems = remember(historyItems, selectedDateMillis) {
        if (selectedDateMillis == null) {
            historyItems
        } else {
            val cal = Calendar.getInstance().apply {
                timeZone = TimeZone.getTimeZone("UTC")
                timeInMillis = selectedDateMillis!!
            }
            val year = cal.get(Calendar.YEAR)
            val month = cal.get(Calendar.MONTH)
            val day = cal.get(Calendar.DAY_OF_MONTH)

            val localCal = Calendar.getInstance().apply {
                set(year, month, day, 0, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val startOfDay = localCal.timeInMillis
            localCal.set(Calendar.HOUR_OF_DAY, 23)
            localCal.set(Calendar.MINUTE, 59)
            localCal.set(Calendar.SECOND, 59)
            val endOfDay = localCal.timeInMillis

            historyItems.filter { it.timestamp in startOfDay..endOfDay }
        }
    }

    val groupedHistory = remember(filteredItems) {
        filteredItems.groupBy { item -> getDateHeader(item.timestamp) }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(
                    start = if (isLandscape) 8.dp else 16.dp,
                    end = 16.dp,
                    top = if (isLandscape) 4.dp else 0.dp
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.History, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Ajalugu",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Row {
                // Kalendri ikoon (nüüd püsivalt Primary värvi)
                IconButton(onClick = { showDatePicker = true }) {
                    Icon(
                        Icons.Default.Event,
                        null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                // Kustutamise nupp (nüüd on värv mahedam, kooskõlas disainiga)
                if (historyItems.isNotEmpty()) {
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(
                            Icons.Default.DeleteSweep,
                            null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // SISU OSA
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = if (isLandscape) 8.dp else 16.dp, end = 16.dp)
        ) {
            if (selectedDateMillis != null) {
                InputChip(
                    selected = true,
                    onClick = { selectedDateMillis = null },
                    label = {
                        val dateStr = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).apply {
                            timeZone = TimeZone.getTimeZone("UTC")
                        }.format(Date(selectedDateMillis!!))
                        Text("Kuupäev: $dateStr")
                    },
                    trailingIcon = { Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            if (filteredItems.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(if (selectedDateMillis != null) "Sellel päeval kuulamisi polnud." else "Ajalugu on tühi.", color = Color.Gray)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 80.dp)) {
                    groupedHistory.forEach { (dateHeader, itemsInGroup) ->
                        stickyHeader {
                            Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.background) {
                                Text(text = dateHeader, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp))
                            }
                        }
                        items(itemsInGroup, key = { it.id }) { item ->
                            HistoryRow(
                                item = item,
                                onSearchClick = { openSearch(context, "${item.artist} ${item.title}") },
                                onSpotifyClick = { openSpotify(context, "${item.artist} ${item.title}") },
                                onPlayStationClick = { onPlayStationByName(item.stationName) }
                            )
                        }
                    }
                }
            }
        }
    }

    // --- KUSTUTAMISE HOIATUSAKEN (DIALOG) ---
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Ajaloo kustutamine") },
            text = { Text("Kas oled kindel, et soovid kogu kuulamisajaloo jäädavalt kustutada?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClearHistory()
                        showDeleteConfirm = false
                    }
                ) {
                    Text("Kustuta", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Loobu")
                }
            }
        )
    }

    // --- KALENDRI DIALOOG ---
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean = activeDatesUTC.contains(utcTimeMillis)
                override fun isSelectableYear(year: Int): Boolean = year >= 2024
            }
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = { TextButton(onClick = { selectedDateMillis = datePickerState.selectedDateMillis; showDatePicker = false }) { Text("Vali") } },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Tühista") } }
        ) {
            DatePicker(
                state = datePickerState,
                title = if (isLandscape) null else { { Text("Vali kuupäev", modifier = Modifier.padding(16.dp)) } },
                headline = if (isLandscape) null else { {
                    val formatter = SimpleDateFormat("EEE, MMM d", Locale.getDefault())
                    val dateText = datePickerState.selectedDateMillis?.let { formatter.format(Date(it)) } ?: "Vali päev"
                    Text(dateText, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                } }
            )
        }
    }
}

// HistoryRow ja muud abifunktsioonid jäävad samaks
@Composable
fun HistoryRow(item: HistoryItem, onSearchClick: () -> Unit, onSpotifyClick: () -> Unit, onPlayStationClick: () -> Unit) {
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val timeStr = timeFormat.format(Date(item.timestamp))
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.width(50.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = timeStr, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Box(modifier = Modifier.height(32.dp).width(2.dp).background(MaterialTheme.colorScheme.surface, CircleShape))
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = item.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(text = item.artist, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { onPlayStationClick() }.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.PlayArrow, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = item.stationName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
                Row {
                    IconButton(onClick = onSearchClick, modifier = Modifier.size(36.dp)) { Icon(Icons.Default.Search, "YouTube", tint = Color.Red.copy(alpha = 0.8f), modifier = Modifier.size(20.dp)) }
                    IconButton(onClick = onSpotifyClick, modifier = Modifier.size(36.dp)) { Icon(Icons.Default.MusicNote, "Spotify", tint = Color(0xFF1DB954), modifier = Modifier.size(20.dp)) }
                }
            }
        }
    }
}

private fun getDateHeader(timestamp: Long): String {
    val now = Calendar.getInstance(); val time = Calendar.getInstance().apply { timeInMillis = timestamp }
    return when {
        now.get(Calendar.YEAR) == time.get(Calendar.YEAR) && now.get(Calendar.DAY_OF_YEAR) == time.get(Calendar.DAY_OF_YEAR) -> "Täna"
        now.get(Calendar.YEAR) == time.get(Calendar.YEAR) && now.get(Calendar.DAY_OF_YEAR) - 1 == time.get(Calendar.DAY_OF_YEAR) -> "Eile"
        else -> SimpleDateFormat("dd. MMMM yyyy", Locale.getDefault()).format(Date(timestamp))
    }
}

private fun openSearch(context: Context, query: String) {
    try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=$query"))) } catch (e: Exception) {}
}

private fun openSpotify(context: Context, query: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("spotify:search:$query")).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
        context.startActivity(intent)
    } catch (e: Exception) {
        try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://open.spotify.com/search/$query"))) } catch (e2: Exception) {}
    }
}