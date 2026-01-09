package ee.minu.kellraadio.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import ee.minu.kellraadio.RadioStation

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun StationList(
    stations: List<RadioStation>,
    filteredStations: List<RadioStation>,
    categories: List<String>,
    selectedCategory: String,
    selectedStationId: Int,
    playerStatus: String,
    isRefreshing: Boolean,
    onCategorySelect: (String) -> Unit,
    onRefresh: () -> Unit, // Jätame selle parameetri alles, et mitte MainActivityt lõhkuda, aga ei kasuta
    onStationSelect: (RadioStation) -> Unit,
    onStationLongClick: (RadioStation) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    Column(modifier = modifier) {
        // KATEGOORIATE RIDA (FlowRow - murrab ridu)
        if (categories.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                categories.forEach { category ->
                    val isSelected = (selectedCategory == category)

                    // Eriline stiil "Lemmikud" kiibile
                    val isFavoritesChip = category == "Lemmikud"

                    FilterChip(
                        selected = isSelected,
                        onClick = { onCategorySelect(category) },
                        label = { Text(category) },
                        leadingIcon = if (isSelected) {
                            { Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp)) }
                        } else null,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = if (isFavoritesChip) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primary,
                            selectedLabelColor = if (isFavoritesChip) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onPrimary,
                            selectedLeadingIconColor = if (isFavoritesChip) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onPrimary,
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = if (isFavoritesChip && !isSelected) MaterialTheme.colorScheme.tertiary else Color.Gray,
                            selectedBorderColor = Color.Transparent
                        )
                    )
                }
            }
        }

        if (stations.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Laadin jaamu...", color = Color.Gray)
            }
        } else if (filteredStations.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Selles kategoorias pole jaamu.", color = Color.Gray)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredStations, key = { it.id }) { station ->
                    val isSelected = station.id == selectedStationId
                    val isPlayingStation = playerStatus.contains("Mängib")

                    StationGridItem(
                        station = station,
                        isSelected = isSelected,
                        isPlaying = isPlayingStation,
                        onClick = { onStationSelect(station) },
                        onLongClick = { onStationLongClick(station) }
                    )
                }
            }
        }
    }
}