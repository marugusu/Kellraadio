package ee.minu.kellraadio.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import ee.minu.kellraadio.RadioStation
import ee.minu.kellraadio.R // IMPORDI RESSURSID
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun StationList(
    stations: List<RadioStation>,
    filteredStations: List<RadioStation>,
    categories: List<String>,
    selectedCategory: String,
    selectedStationId: Int,
    playerStatus: String,
    isRefreshing: Boolean,
    columnCountPortrait: Int,
    columnCountLandscape: Int,
    showFlags: Boolean,
    onCategorySelect: (String) -> Unit,
    onRefresh: () -> Unit,
    onStationSelect: (RadioStation) -> Unit,
    onStationLongClick: (RadioStation) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val context = LocalContext.current

    // ABIFUNKTSIOON: Tõlgib süsteemsed ID-d loetavaks tekstiks
    fun getCategoryDisplayName(categoryId: String): String {
        return when (categoryId) {
            "Favorites" -> context.getString(R.string.cat_favorites)
            "My" -> context.getString(R.string.cat_my_stations)
            "All" -> context.getString(R.string.cat_all)
            // Kui on serveri kategooria (ERR, Sky), siis näita otse
            else -> categoryId
        }
    }

    if (stations.isEmpty() || categories.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(context.getString(R.string.status_buffering), color = Color.Gray)
        }
        return
    }

    val initialIndex = remember(categories, selectedCategory) {
        categories.indexOf(selectedCategory).coerceAtLeast(0)
    }

    val pagerState = rememberPagerState(initialPage = initialIndex) {
        categories.size
    }

    LaunchedEffect(selectedCategory, categories) {
        if (!isLandscape) {
            val targetIndex = categories.indexOf(selectedCategory)
            if (targetIndex >= 0 && pagerState.currentPage != targetIndex) {
                pagerState.scrollToPage(targetIndex)
            }
        }
    }

    val currentCategories by rememberUpdatedState(categories)
    val currentSelectedCategory by rememberUpdatedState(selectedCategory)

    LaunchedEffect(pagerState) {
        if (!isLandscape) {
            snapshotFlow { pagerState.currentPage }
                .distinctUntilChanged()
                .collectLatest { page ->
                    if (currentCategories.size > 1) {
                        val categoryOnPage = currentCategories.getOrNull(page)
                        if (categoryOnPage != null && categoryOnPage != currentSelectedCategory) {
                            onCategorySelect(categoryOnPage)
                        }
                    }
                }
        }
    }

    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val offsetDivisor = if (isLandscape) 6 else 3
    val scrollOffsetPx = with(density) { -(configuration.screenWidthDp / offsetDivisor).dp.toPx() }.toInt()

    LaunchedEffect(selectedCategory) {
        val index = categories.indexOf(selectedCategory)
        if (index >= 0) {
            listState.animateScrollToItem(index, scrollOffset = scrollOffsetPx)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = if (isLandscape) 8.dp else 16.dp, end = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // --- KATEGOORIATE RIDA ---
        androidx.compose.foundation.lazy.LazyRow(
            state = listState,
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 0.dp)
        ) {
            items(categories.size) { index ->
                val categoryId = categories[index]
                val isSelected = (selectedCategory == categoryId)

                // Tuvastame eritooni vajavad nupud
                val isFavoritesChip = categoryId == "Favorites"
                val isMyStationsChip = categoryId == "My"
                val isAllChip = categoryId == "All" // UUS: Tuvastame "Kõik" nupu

                FilterChip(
                    selected = isSelected,
                    onClick = { onCategorySelect(categoryId) },
                    label = { Text(getCategoryDisplayName(categoryId)) },
                    leadingIcon = if (isSelected) {
                        { Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp)) }
                    } else null,
                    colors = FilterChipDefaults.filterChipColors(
                        // 1. KUI ON VALITUD (Taustad)
                        selectedContainerColor = when {
                            isFavoritesChip -> MaterialTheme.colorScheme.onSecondary.copy(alpha = 0.2f) // Kuldne
                            isMyStationsChip -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f)   // Roosakas
                            isAllChip -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)         // Rohekassinine (UUS)
                            else -> MaterialTheme.colorScheme.primary                                   // Lilla
                        },
                        // 2. KUI ON VALITUD (Tekst ja Ikoon)
                        selectedLabelColor = when {
                            isFavoritesChip -> MaterialTheme.colorScheme.onSecondary
                            isMyStationsChip -> MaterialTheme.colorScheme.tertiary
                            isAllChip -> MaterialTheme.colorScheme.secondary // UUS
                            else -> MaterialTheme.colorScheme.onPrimary
                        },
                        selectedLeadingIconColor = when {
                            isFavoritesChip -> MaterialTheme.colorScheme.onSecondary
                            isMyStationsChip -> MaterialTheme.colorScheme.tertiary
                            isAllChip -> MaterialTheme.colorScheme.secondary // UUS
                            else -> MaterialTheme.colorScheme.onPrimary
                        },

                        // 3. KUI EI OLE VALITUD
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        }

        // --- SISU ---
        if (isLandscape) {
            if (filteredStations.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(context.getString(R.string.search_no_results), color = Color.Gray)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(if (isLandscape) columnCountLandscape else columnCountPortrait),
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredStations, key = { it.id }) { station ->
                        StationGridItem(
                            station = station,
                            isSelected = station.id == selectedStationId,
                            isPlaying = playerStatus.contains(context.getString(R.string.status_playing)),
                            onClick = { onStationSelect(station) },
                            onLongClick = { onStationLongClick(station) },
                            showFavoriteIcon = selectedCategory != "Favorites", // UUS ID
                            showFlag = showFlags
                        )
                    }
                }
            }
        } else {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth().weight(1f),
                pageSpacing = 16.dp,
                verticalAlignment = Alignment.Top
            ) { pageIndex ->
                val pageCategory = categories.getOrElse(pageIndex) { "" }

                val stationsForPage = remember(pageCategory, stations) {
                    when (pageCategory) {
                        "Favorites" -> stations.filter { it.isFavorite }
                        "All" -> stations
                        else -> stations.filter { it.category == pageCategory }
                    }
                }

                if (stationsForPage.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(context.getString(R.string.search_no_results), color = Color.Gray)
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(if (isLandscape) columnCountLandscape else columnCountPortrait),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(stationsForPage, key = { it.id }) { station ->
                            StationGridItem(
                                station = station,
                                isSelected = station.id == selectedStationId,
                                isPlaying = playerStatus.contains(context.getString(R.string.status_playing)),
                                onClick = { onStationSelect(station) },
                                onLongClick = { onStationLongClick(station) },
                                showFavoriteIcon = pageCategory != "Favorites",
                                showFlag = showFlags
                            )
                        }
                    }
                }
            }
        }
    }
}