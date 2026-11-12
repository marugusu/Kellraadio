package app.radiorecalarm.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.radiorecalarm.R
import app.radiorecalarm.RadioStation
import app.radiorecalarm.StationListFilter
import app.radiorecalarm.getFlagEmoji
import app.radiorecalarm.getCachedCountryDisplayName

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun StationList(
    stations: List<RadioStation>,
    filteredStations: List<RadioStation>,
    categories: List<String>,
    subCategories: List<String>,
    selectedCategory: String,
    selectedSubCategories: Set<String>,
    selectedStationId: Int,
    playbackState: StationPlaybackState,
    isInitiallyLoading: Boolean,
    hideCuratedStations: Boolean,
    columnCountPortrait: Int,
    columnCountLandscape: Int,
    showFlags: Boolean,
    onCategorySelect: (String) -> Unit,
    onSubCategoryToggle: (String) -> Unit,
    onStationSelect: (RadioStation) -> Unit,
    onStationLongClick: (RadioStation) -> Unit,
    onAddStationClick: () -> Unit,
    onShowCuratedClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    val favoritesLabel = stringResource(R.string.cat_favorites)
    val myStationsLabel = stringResource(R.string.cat_my_stations)
    val allLabel = stringResource(R.string.cat_all)

    fun getCategoryDisplayName(categoryId: String): String {
        return when {
            categoryId == StationListFilter.CATEGORY_FAVORITES -> favoritesLabel
            categoryId == StationListFilter.CATEGORY_MY -> myStationsLabel
            categoryId == StationListFilter.CATEGORY_ALL -> allLabel
            categoryId.length == 2 -> getCachedCountryDisplayName(categoryId)
            else -> categoryId
        }
    }

    if (isInitiallyLoading && stations.isEmpty() && !hideCuratedStations) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        return
    }

    if (stations.isEmpty() || categories.isEmpty()) {
        StationListEmptyState(
            hideCuratedStations = hideCuratedStations,
            onAddStationClick = onAddStationClick,
            onShowCuratedClick = onShowCuratedClick,
            modifier = modifier
        )
        return
    }

    val initialPage = remember(categories) {
        categories.indexOf(selectedCategory).coerceAtLeast(0)
    }
    val pagerState = rememberPagerState(initialPage = initialPage) { categories.size }

    LaunchedEffect(selectedCategory, categories) {
        val targetIndex = categories.indexOf(selectedCategory)
        if (targetIndex >= 0 && pagerState.currentPage != targetIndex && !pagerState.isScrollInProgress) {
            pagerState.animateScrollToPage(targetIndex)
        }
    }

    LaunchedEffect(pagerState.settledPage, categories) {
        val categoryOnPage = categories.getOrNull(pagerState.settledPage)
        if (categoryOnPage != null && categoryOnPage != selectedCategory) {
            onCategorySelect(categoryOnPage)
        }
    }

    val topRowListState = androidx.compose.foundation.lazy.rememberLazyListState()
    LaunchedEffect(selectedCategory, categories) {
        val index = categories.indexOf(selectedCategory)
        if (index >= 0) {
            val layoutInfo = topRowListState.layoutInfo
            val visibleItem = layoutInfo.visibleItemsInfo.find { it.index == index }
            val isFullyVisible = visibleItem != null &&
                visibleItem.offset >= layoutInfo.viewportStartOffset &&
                (visibleItem.offset + visibleItem.size) <= layoutInfo.viewportEndOffset
            if (!isFullyVisible) {
                val viewportWidth = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
                val targetOffset = if (viewportWidth > 0) -(viewportWidth / 3) else 0
                topRowListState.animateScrollToItem(index, scrollOffset = targetOffset)
            }
        }
    }

    // Landscape: start = 0.dp, kuna MainActivity-s on juba end = 12.dp. Kokku 12.dp.
    val listPadding = if (isLandscape) PaddingValues(start = 0.dp, end = 12.dp) else PaddingValues(horizontal = 16.dp)

    Column(modifier = modifier.fillMaxSize().padding(listPadding)) {
        // ÜLEMINE VAHE: Täpselt 12dp, et eraldada kiibid ülapaneelist
        Spacer(modifier = Modifier.height(12.dp))
        
        CompositionLocalProvider(LocalMinimumInteractiveComponentEnforcement provides false) {
            Column {
                // RIDA 1: PEAGRUPID - Fikseeritud kõrgus 32dp
                androidx.compose.foundation.lazy.LazyRow(
                    state = topRowListState,
                    modifier = Modifier.fillMaxWidth().height(32.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(categories.size) { index ->
                        val categoryId = categories[index]
                        val isSelected = (selectedCategory == categoryId)
                        val isFavoritesChip = categoryId == "Favorites"
                        val isMyStationsChip = categoryId == "My"
                        val isAllChip = categoryId == "All"

                        FilterChip(
                            selected = isSelected,
                            onClick = { onCategorySelect(categoryId) },
                            label = { 
                                Text(
                                    getCategoryDisplayName(categoryId),
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    letterSpacing = 0.2.sp
                                ) 
                            },
                            modifier = Modifier.height(32.dp),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                selectedContainerColor = when {
                                    isFavoritesChip -> MaterialTheme.colorScheme.onSecondary.copy(alpha = 0.25f)
                                    isMyStationsChip -> MaterialTheme.colorScheme.secondaryContainer
                                    else -> MaterialTheme.colorScheme.primaryContainer
                                },
                                selectedLabelColor = when {
                                    isFavoritesChip -> MaterialTheme.colorScheme.onSecondary
                                    isMyStationsChip -> MaterialTheme.colorScheme.secondary
                                    else -> MaterialTheme.colorScheme.onPrimaryContainer
                                }
                            )
                        )
                    }
                }

                // RIDA 2: ALAMFILTRID
                AnimatedVisibility(
                    visible = subCategories.isNotEmpty(),
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    Column {
                        // VAHE RIDADE VAHEL: Täpselt 12dp
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        androidx.compose.foundation.lazy.LazyRow(
                            modifier = Modifier.fillMaxWidth().height(32.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .clickable { onCategorySelect(selectedCategory) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    val isCountryCategory = selectedCategory.length == 2
                                    if (isCountryCategory) {
                                        Text(text = getFlagEmoji(selectedCategory), fontSize = 16.sp)
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.FilterAlt,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                            items(subCategories.size) { index ->
                                val sub = subCategories[index]
                                val isSelected = selectedSubCategories.contains(sub)

                                FilterChip(
                                    selected = isSelected,
                                    onClick = { onSubCategoryToggle(sub) },
                                    label = { Text(sub, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium) },
                                    modifier = Modifier.height(32.dp),
                                    leadingIcon = if (isSelected) {
                                        { Icon(Icons.Default.Check, null, modifier = Modifier.size(12.dp)) }
                                    } else null,
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
                                    colors = FilterChipDefaults.filterChipColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        // VAHE RUUDUSTIKUNI: Täpselt 12dp
        Spacer(modifier = Modifier.height(12.dp))

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth().weight(1f),
            pageSpacing = 16.dp,
            userScrollEnabled = true
        ) { pageIndex ->
            val pageCategory = categories.getOrNull(pageIndex) ?: ""
            val stationsForThisPage = remember(pageCategory, selectedCategory, filteredStations, stations) {
                if (pageCategory == selectedCategory) {
                    filteredStations
                } else {
                    StationListFilter.filterAndSortStations(stations, pageCategory)
                }
            }

            val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
            var hasScrolledInitial by remember(pageCategory) { mutableStateOf(false) }
            LaunchedEffect(selectedStationId, stationsForThisPage) {
                val index = stationsForThisPage.indexOfFirst { it.id == selectedStationId }
                if (index >= 0) {
                    val layoutInfo = gridState.layoutInfo
                    val visibleItem = layoutInfo.visibleItemsInfo.find { it.index == index }
                    if (!hasScrolledInitial) {
                        if (visibleItem == null) {
                            gridState.scrollToItem(index = index)
                        }
                        hasScrolledInitial = true
                    } else {
                        val isFullyVisible = visibleItem != null &&
                            visibleItem.offset.y >= layoutInfo.viewportStartOffset &&
                            (visibleItem.offset.y + visibleItem.size.height) <= layoutInfo.viewportEndOffset
                        if (!isFullyVisible) {
                            gridState.animateScrollToItem(index = index)
                        }
                    }
                }
            }

            if (stationsForThisPage.isEmpty()) {
                val emptyText = when (pageCategory) {
                    StationListFilter.CATEGORY_FAVORITES -> stringResource(R.string.empty_favorites)
                    StationListFilter.CATEGORY_MY -> stringResource(R.string.empty_my_stations)
                    else -> stringResource(R.string.search_no_results)
                }
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = emptyText,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
            } else {
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Fixed(if (isLandscape) columnCountLandscape else columnCountPortrait),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 80.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(stationsForThisPage, key = { it.id }) { station ->
                        val isSelected = station.id == selectedStationId
                        val shouldShowFlagInThisCategory = pageCategory == StationListFilter.CATEGORY_FAVORITES ||
                            pageCategory == StationListFilter.CATEGORY_MY ||
                            pageCategory == StationListFilter.CATEGORY_ALL
                        StationGridItem(
                            station = station,
                            isSelected = isSelected,
                            playbackState = playbackState,
                            onClick = { onStationSelect(station) },
                            onLongClick = { onStationLongClick(station) },
                            showFavoriteIcon = pageCategory != StationListFilter.CATEGORY_FAVORITES,
                            showFlag = showFlags && shouldShowFlagInThisCategory
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StationListEmptyState(
    hideCuratedStations: Boolean,
    onAddStationClick: () -> Unit,
    onShowCuratedClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.empty_stations_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(
                if (hideCuratedStations) R.string.empty_stations_hidden_desc else R.string.empty_stations_desc
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(20.dp))
        Button(
            onClick = onAddStationClick,
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(stringResource(R.string.add_station))
        }
        if (hideCuratedStations) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = onShowCuratedClick,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(stringResource(R.string.action_show_curated))
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun StationListPreview() {
    val sampleStations = listOf(
        RadioStation(id = 1, name = "Vikerraadio", url = "https://icecast.err.ee/vikerraadio.mp3", countryCode = "EE", category = "Jutusaated", isFavorite = true),
        RadioStation(id = 2, name = "Rock FM", url = "https://stream.skymedia.ee/live/RockFM", countryCode = "EE", category = "Rock", isFavorite = false),
        RadioStation(id = 3, name = "Minu Raadio", url = "https://example.com/my.mp3", countryCode = "EE", isUserStation = true)
    )
    MaterialTheme(colorScheme = darkColorScheme()) {
        StationList(
            stations = sampleStations,
            filteredStations = sampleStations,
            categories = listOf("Favorites", "My", "EE", "All"),
            subCategories = listOf("Jutusaated", "Rock"),
            selectedCategory = "EE",
            selectedSubCategories = emptySet(),
            selectedStationId = 2,
            playbackState = StationPlaybackState.PLAYING,
            isInitiallyLoading = false,
            hideCuratedStations = false,
            columnCountPortrait = 3,
            columnCountLandscape = 3,
            showFlags = true,
            onCategorySelect = {},
            onSubCategoryToggle = {},
            onStationSelect = {},
            onStationLongClick = {},
            onAddStationClick = {},
            onShowCuratedClick = {}
        )
    }
}
