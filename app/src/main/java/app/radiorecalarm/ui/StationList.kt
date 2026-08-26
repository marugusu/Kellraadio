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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.radiorecalarm.R
import app.radiorecalarm.RadioStation
import app.radiorecalarm.getFlagEmoji
import app.radiorecalarm.getCachedCountryDisplayName
import java.util.Locale

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
    playerStatus: String,
    isRefreshing: Boolean,
    columnCountPortrait: Int,
    columnCountLandscape: Int,
    showFlags: Boolean,
    onCategorySelect: (String) -> Unit,
    onSubCategoryToggle: (String) -> Unit,
    onRefresh: () -> Unit,
    onStationSelect: (RadioStation) -> Unit,
    onStationLongClick: (RadioStation) -> Unit,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    val favoritesLabel = stringResource(R.string.cat_favorites)
    val myStationsLabel = stringResource(R.string.cat_my_stations)
    val allLabel = stringResource(R.string.cat_all)
    val myFilterLabel = stringResource(R.string.filter_my)
    val playingStatusText = stringResource(R.string.status_playing)

    fun getCategoryDisplayName(categoryId: String): String {
        return when {
            categoryId == "Favorites" -> favoritesLabel
            categoryId == "My" -> myStationsLabel
            categoryId == "All" -> allLabel
            categoryId.length == 2 -> getCachedCountryDisplayName(categoryId)
            else -> categoryId
        }
    }

    if (stations.isEmpty() || categories.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        return
    }

    val pagerState = rememberPagerState(initialPage = 0) { categories.size }

    LaunchedEffect(selectedCategory) {
        val targetIndex = categories.indexOf(selectedCategory)
        if (targetIndex >= 0 && pagerState.currentPage != targetIndex) {
            pagerState.scrollToPage(targetIndex)
        }
    }

    LaunchedEffect(pagerState.currentPage) {
        val categoryOnPage = categories.getOrNull(pagerState.currentPage)
        if (categoryOnPage != null && categoryOnPage != selectedCategory) {
            onCategorySelect(categoryOnPage)
        }
    }

    val topRowListState = androidx.compose.foundation.lazy.rememberLazyListState()
    LaunchedEffect(selectedCategory) {
        val index = categories.indexOf(selectedCategory)
        if (index >= 0) {
            topRowListState.animateScrollToItem(index, scrollOffset = -400)
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
                                
                                val displayLabel = if (sub == "My") myFilterLabel else sub

                                FilterChip(
                                    selected = isSelected,
                                    onClick = { onSubCategoryToggle(sub) },
                                    label = { Text(displayLabel, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium) },
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
                    when (pageCategory) {
                        // PARANDUS: Ühtne sorteerimine (favoriteOrder, siis priority, siis nimi)
                        "Favorites" -> stations.filter { it.isFavorite }
                            .sortedWith(compareBy<RadioStation> { it.favoriteOrder }.thenBy { it.priority }.thenBy { it.name })
                        "My" -> stations.filter { it.isUserStation }
                        "All" -> stations
                        else -> stations.filter { it.countryCode == pageCategory }
                    }
                }
            }

            val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
            var hasScrolledInitial by remember { mutableStateOf(false) }
            LaunchedEffect(selectedStationId, stationsForThisPage) {
                val index = stationsForThisPage.indexOfFirst { it.id == selectedStationId }
                if (index >= 0) {
                    if (!hasScrolledInitial) {
                        gridState.scrollToItem(index = index, scrollOffset = -400)
                        hasScrolledInitial = true
                    } else {
                        gridState.animateScrollToItem(index = index, scrollOffset = -400)
                    }
                }
            }

            if (stationsForThisPage.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.search_no_results), color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        val shouldShowFlagInThisCategory = pageCategory == "Favorites" || 
                                                           pageCategory == "My" || 
                                                           pageCategory == "All"
                        StationGridItem(
                            station = station,
                            isSelected = isSelected,
                            isPlaying = playerStatus.contains(playingStatusText),
                            onClick = { onStationSelect(station) },
                            onLongClick = { onStationLongClick(station) },
                            showFavoriteIcon = pageCategory != "Favorites",
                            showFlag = showFlags && shouldShowFlagInThisCategory
                        )
                    }
                }
            }
        }
    }
}
