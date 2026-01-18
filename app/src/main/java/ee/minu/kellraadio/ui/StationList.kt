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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import ee.minu.kellraadio.RadioStation
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun StationList(
    stations: List<RadioStation>,
    filteredStations: List<RadioStation>, // Landscape'i jaoks
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

    // --- 1. KAITSEKIHT: Ära tee midagi, enne kui andmed on olemas ---
    // See on kõige tähtsam rida. See välistab "Kõik kanalid" käivitamise vea.
    if (stations.isEmpty() || categories.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Laadin jaamu...", color = Color.Gray)
        }
        return
    }

    // --- Pageri loogika (ainult Portrait) ---
    val initialIndex = remember(categories, selectedCategory) {
        categories.indexOf(selectedCategory).coerceAtLeast(0)
    }

    val pagerState = rememberPagerState(initialPage = initialIndex) {
        categories.size
    }

    // --- SÜNKRONISEERIMINE 1: Kategooria muutus -> Liiguta Pagerit ---
    // Kui vajutad nuppu või äratus vahetab kategooriat, liigub Pager kaasa.
    LaunchedEffect(selectedCategory, categories) {
        if (!isLandscape) {
            val targetIndex = categories.indexOf(selectedCategory)
            // Kontrollime, et me ei liigutaks, kui juba oleme seal (väldib värelust)
            if (targetIndex >= 0 && pagerState.currentPage != targetIndex) {
                pagerState.scrollToPage(targetIndex)
            }
        }
    }

    // --- SÜNKRONISEERIMINE 2: Pageri viipamine -> Muuda kategooriat ---
    // Jälgime Pageri lehe muutust reaalajas.
    val currentCategories by rememberUpdatedState(categories)
    val currentSelectedCategory by rememberUpdatedState(selectedCategory)

    LaunchedEffect(pagerState) {
        if (!isLandscape) {
            snapshotFlow { pagerState.currentPage }
                .distinctUntilChanged()
                .collectLatest { page ->
                    // Kontrollime otse currentCategories pealt
                    if (currentCategories.size > 1) {
                        val categoryOnPage = currentCategories.getOrNull(page)
                        if (categoryOnPage != null && categoryOnPage != currentSelectedCategory) {
                            onCategorySelect(categoryOnPage)
                        }
                    }
                }
        }
    }

    // --- Ülemise nupurea (Chips) kerimine ---
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()

    // PARANDUS: Arvutame nihke vastavalt sellele, kui palju nimekiri ekraanil ruumi võtab.
    // Portrait: Nimekiri on 100% lai, nihe on 1/3.
    // Landscape: Nimekiri on u 50-60% lai (sest pleier on kõrval), seega nihe peab olema poole väiksem (1/6).
    val offsetDivisor = if (isLandscape) 6 else 3
    val scrollOffsetPx = with(density) { -(configuration.screenWidthDp / offsetDivisor).dp.toPx() }.toInt()

    LaunchedEffect(selectedCategory) {
        val index = categories.indexOf(selectedCategory)
        if (index >= 0) {
            // animateScrollToItem viib nupu õigesse kohta sujuva liikumisega
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
                val category = categories[index]
                val isSelected = (selectedCategory == category)
                val isFavoritesChip = category == "Lemmikud"

                FilterChip(
                    selected = isSelected,
                    onClick = { onCategorySelect(category) },
                    label = { Text(category) },
                    leadingIcon = if (isSelected) {
                        { Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp)) }
                    } else null,
                    colors = FilterChipDefaults.filterChipColors(
                        // MUUDATUS: Lemmikute taust on 20% läbipaistvusega oranž, tekst on täisoranž
                        selectedContainerColor = if (isFavoritesChip) MaterialTheme.colorScheme.onSecondary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.primary,
                        selectedLabelColor = if (isFavoritesChip) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onPrimary,

                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        }

        // --- SISU ---
        if (isLandscape) {
            // Landscape: Üks Grid
            if (filteredStations.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Selles kategoorias pole jaamu.", color = Color.Gray)
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
                            isPlaying = playerStatus.contains("Mängib"),
                            onClick = { onStationSelect(station) },
                            onLongClick = { onStationLongClick(station) },
                            showFavoriteIcon = selectedCategory != "Lemmikud",
                            showFlag = showFlags
                        )
                    }
                }
            }
        } else {
            // Portrait: Pager
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth().weight(1f),
                pageSpacing = 16.dp,
                verticalAlignment = Alignment.Top
            ) { pageIndex ->
                val pageCategory = categories.getOrElse(pageIndex) { "" }

                val stationsForPage = remember(pageCategory, stations) {
                    when (pageCategory) {
                        "Lemmikud" -> stations.filter { it.isFavorite }
                        "Kõik" -> stations
                        else -> stations.filter { it.category == pageCategory }
                    }
                }

                if (stationsForPage.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Selles kategoorias pole jaamu.", color = Color.Gray)
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
                                isPlaying = playerStatus.contains("Mängib"),
                                onClick = { onStationSelect(station) },
                                onLongClick = { onStationLongClick(station) },
                                showFavoriteIcon = pageCategory != "Lemmikud",
                                showFlag = showFlags
                            )
                        }
                    }
                }
            }
        }
    }
}