package ws.ct.radiow.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ws.ct.radiow.RadioStation
import ws.ct.radiow.R
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
    val myFilterLabel = stringResource(R.string.filter_my) // UUS
    val playingStatusText = stringResource(R.string.status_playing)

    fun getCategoryDisplayName(categoryId: String): String {
        return when {
            categoryId == "Favorites" -> favoritesLabel
            categoryId == "My" -> myStationsLabel
            categoryId == "All" -> allLabel
            categoryId.length == 2 -> Locale("", categoryId).displayCountry
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
            topRowListState.animateScrollToItem(index, scrollOffset = -200)
        }
    }

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(modifier = Modifier.height(8.dp))

        // RIDA 1: PEAGRUPID
        androidx.compose.foundation.lazy.LazyRow(
            state = topRowListState,
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                    label = { Text(getCategoryDisplayName(categoryId)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = when {
                            isFavoritesChip -> MaterialTheme.colorScheme.onSecondary.copy(alpha = 0.2f)
                            isMyStationsChip -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f)
                            isAllChip -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)
                            else -> MaterialTheme.colorScheme.primaryContainer
                        },
                        selectedLabelColor = when {
                            isFavoritesChip -> MaterialTheme.colorScheme.onSecondary
                            isMyStationsChip -> MaterialTheme.colorScheme.tertiary
                            isAllChip -> MaterialTheme.colorScheme.secondary
                            else -> MaterialTheme.colorScheme.onPrimaryContainer
                        }
                    )
                )
            }
        }

        // RIDA 2: ALAMFILTRID (Tõlgitud "My")
        AnimatedVisibility(
            visible = subCategories.isNotEmpty(),
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            androidx.compose.foundation.lazy.LazyRow(
                modifier = Modifier.fillMaxWidth().padding(top = 0.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                item {
                    Icon(
                        imageVector = Icons.Default.FilterAlt,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                    )
                }
                items(subCategories.size) { index ->
                    val sub = subCategories[index]
                    val isSelected = selectedSubCategories.contains(sub)
                    
                    // TÕLGE: Kui tunnuseks on "My", kasuta tõlget, muidu jäta nagu on
                    val displayLabel = if (sub == "My") myFilterLabel else sub

                    FilterChip(
                        selected = isSelected,
                        onClick = { onSubCategoryToggle(sub) },
                        label = { Text(displayLabel, fontSize = 12.sp) },
                        leadingIcon = if (isSelected) {
                            { Icon(Icons.Default.Check, null, modifier = Modifier.size(12.dp)) }
                        } else null,
                        shape = RoundedCornerShape(8.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

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
                        "Favorites" -> stations.filter { it.isFavorite }.sortedBy { it.favoriteOrder }
                        "My" -> stations.filter { it.isUserStation }
                        "All" -> stations
                        else -> stations.filter { it.countryCode == pageCategory }
                    }
                }
            }

            val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
            LaunchedEffect(selectedStationId, stationsForThisPage) {
                val index = stationsForThisPage.indexOfFirst { it.id == selectedStationId }
                if (index >= 0) {
                    gridState.animateScrollToItem(index = index, scrollOffset = -200)
                }
            }

            if (stationsForThisPage.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.search_no_results), color = Color.Gray)
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