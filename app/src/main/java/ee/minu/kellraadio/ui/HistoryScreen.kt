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
import androidx.compose.ui.res.stringResource // UUS IMPORT
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ee.minu.kellraadio.HistoryItem
import ee.minu.kellraadio.RadioStationRepository
import ee.minu.kellraadio.R // UUS IMPORT
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
    var showDeleteConfirm by remember { mutableStateOf(false) }

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

    // 3. GRUPEERIMINE (Kasutame nüüd context-i tõlgete jaoks)
    val groupedHistory = remember(filteredItems, context) {
        filteredItems.groupBy { item -> getDateHeader(context, item.timestamp) }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(start = if (isLandscape) 8.dp else 16.dp, end = 16.dp)
                .padding(top = if (isLandscape) 12.dp else 0.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.History, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    // TÕLGITUD
                    text = stringResource(R.string.history_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Row {
                IconButton(onClick = { showDatePicker = true }) {
                    Icon(
                        Icons.Default.Event,
                        null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

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
                        // TÕLGE: Eemaldasin eesliite "Kuupäev: ", näitame lihtsalt kuupäeva
                        Text(dateStr)
                    },
                    trailingIcon = { Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            if (filteredItems.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    // TÕLGITUD
                    Text(
                        if (selectedDateMillis != null) stringResource(R.string.history_empty_day)
                        else stringResource(R.string.history_empty),
                        color = Color.Gray
                    )
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 80.dp)) {
                    groupedHistory.forEach { (dateHeader, itemsInGroup) ->
                        stickyHeader {
                            Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.background) {
                                Text(
                                    text = dateHeader,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(top = 0.dp, bottom = 8.dp)
                                )
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
            // TÕLGITUD
            title = { Text(stringResource(R.string.history_delete_confirm_title)) },
            text = { Text(stringResource(R.string.history_delete_confirm_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClearHistory()
                        showDeleteConfirm = false
                    }
                ) {
                    // TÕLGITUD
                    Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    // TÕLGITUD
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean = activeDatesUTC.contains(utcTimeMillis)
                override fun isSelectableYear(year: Int): Boolean = year >= 2024
            }
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = { selectedDateMillis = datePickerState.selectedDateMillis; showDatePicker = false }) {
                    Text("OK") // Androidi standardnupp, jätame nii või kasutame "Vali"
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        ) {
            DatePicker(
                state = datePickerState,
                title = if (isLandscape) null else {
                    {
                        Text(
                            // TÕLGITUD
                            stringResource(R.string.date_picker_title),
                            modifier = Modifier.padding(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 12.dp),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                headline = {
                    val formatter = SimpleDateFormat("EEE, MMM d", Locale.getDefault())
                    // TÕLGITUD (varuvariant)
                    val dateText = datePickerState.selectedDateMillis?.let { formatter.format(Date(it)) }
                        ?: stringResource(R.string.date_picker_title)

                    val padding = if (isLandscape) {
                        PaddingValues(start = 24.dp, top = 16.dp, end = 24.dp)
                    } else {
                        PaddingValues(start = 24.dp, bottom = 16.dp)
                    }

                    Text(
                        dateText,
                        modifier = Modifier.padding(padding),
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            )
        }
    }
}

@Composable
fun HistoryRow(item: HistoryItem, onSearchClick: () -> Unit, onSpotifyClick: () -> Unit, onPlayStationClick: () -> Unit) {
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val timeStr = timeFormat.format(Date(item.timestamp))

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .width(70.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { onPlayStationClick() }
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = timeStr,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = item.stationName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Box(
                modifier = Modifier
                    .height(32.dp)
                    .width(2.dp)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f), CircleShape)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = item.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row {
                IconButton(onClick = onSearchClick, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Search, "YouTube", tint = Color.Red.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onSpotifyClick, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.MusicNote, "Spotify", tint = Color(0xFF1DB954).copy(alpha = 0.8f), modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

// TÕLGITUD ABIFUNKTSIOON
private fun getDateHeader(context: Context, timestamp: Long): String {
    val now = Calendar.getInstance(); val time = Calendar.getInstance().apply { timeInMillis = timestamp }
    return when {
        now.get(Calendar.YEAR) == time.get(Calendar.YEAR) && now.get(Calendar.DAY_OF_YEAR) == time.get(Calendar.DAY_OF_YEAR) ->
            context.getString(R.string.history_today) // TÕLGE

        now.get(Calendar.YEAR) == time.get(Calendar.YEAR) && now.get(Calendar.DAY_OF_YEAR) - 1 == time.get(Calendar.DAY_OF_YEAR) ->
            context.getString(R.string.history_yesterday) // TÕLGE

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