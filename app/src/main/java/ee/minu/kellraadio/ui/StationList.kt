package ee.minu.kellraadio.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import ee.minu.kellraadio.RadioStation

@OptIn(ExperimentalMaterial3Api::class)
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
    onRefresh: () -> Unit,
    onStationSelect: (RadioStation) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    Column(modifier = modifier) {
        // 1. KATEGOORIAD
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Box(modifier = Modifier.weight(1f)) {
                if (categories.isNotEmpty()) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(end = 8.dp)
                    ) {
                        // PARANDUS: Kasutame 'items(count)' ja võtame elemendi indeksi järgi.
                        // See väldib konflikti LazyRow ja LazyVerticalGrid importide vahel.
                        items(categories.size) { index ->
                            val category = categories[index]

                            FilterChip(
                                selected = (selectedCategory == category),
                                onClick = { onCategorySelect(category) },
                                label = { Text(category) },
                                leadingIcon = if (selectedCategory == category) {
                                    { Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp)) }
                                } else null,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = (selectedCategory == category),
                                    borderColor = Color.Gray,
                                    selectedBorderColor = Color.Transparent
                                )
                            )
                        }
                    }
                }
            }

            // Värskendamise nupp
            IconButton(onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onRefresh()
            }, enabled = !isRefreshing) {
                if (isRefreshing) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else Icon(Icons.Default.Refresh, "Uuenda", tint = MaterialTheme.colorScheme.primary)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 2. RUUDUSTIK
        if (stations.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Laadin jaamu...", color = Color.Gray)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Siin kasutame 'lazy.grid.items', mis on imporditud ja töötab korrektselt
                items(filteredStations, key = { it.id }) { station ->
                    val isSelected = station.id == selectedStationId
                    val isPlayingStation = playerStatus.contains("Mängib")

                    StationGridItem(
                        station = station,
                        isSelected = isSelected,
                        isPlaying = isPlayingStation,
                        onClick = { onStationSelect(station) }
                    )
                }
            }
        }
    }
}