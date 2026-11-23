package app.radiorecalarm.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import app.radiorecalarm.R
import app.radiorecalarm.RadioStation
import app.radiorecalarm.StationListFilter
import app.radiorecalarm.getCachedCountryDisplayName
import app.radiorecalarm.getFlagEmoji

@OptIn(ExperimentalFoundationApi::class)
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
    onColumnCountChange: (Int) -> Unit = {},
    isReorderMode: Boolean = false,
    onEnterReorderMode: () -> Unit = {},
    onExitReorderMode: () -> Unit = {},
    onSaveFavoritesOrder: (List<RadioStation>) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val currentCols = (if (isLandscape) columnCountLandscape else columnCountPortrait).coerceIn(1, 6)
    val view = LocalView.current
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current

    BackHandler(enabled = isReorderMode) {
        onExitReorderMode()
    }

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

    val topRowListState = rememberLazyListState()
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
    var showColumnMenu by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize().padding(listPadding)) {
        Spacer(modifier = Modifier.height(10.dp))

        // RIDA 1: Ühtne tuuneri-riba (või järjestusrežiimi banner)
        val activeReorderMode = isReorderMode && selectedCategory == StationListFilter.CATEGORY_FAVORITES
        if (activeReorderMode) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                    .padding(start = 10.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.DragIndicator,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.reorder_mode_hint),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    modifier = Modifier
                        .height(26.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .clickable {
                            view.performHapticTap()
                            onExitReorderMode()
                        },
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.primary
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.action_done),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LazyRow(
                    state = topRowListState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(categories.size) { index ->
                        val categoryId = categories[index]
                        val isSelected = (selectedCategory == categoryId)
                        TunerBandTab(
                            label = getCategoryDisplayName(categoryId),
                            categoryId = categoryId,
                            isSelected = isSelected,
                            onClick = {
                                view.performHapticTap()
                                onCategorySelect(categoryId)
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(18.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
                Spacer(modifier = Modifier.width(6.dp))

                if (selectedCategory == StationListFilter.CATEGORY_FAVORITES && stations.count { it.isFavorite } > 1) {
                    val reorderInteraction = remember { MutableInteractionSource() }
                    val reorderFocused by reorderInteraction.collectIsFocusedAsState()
                    Surface(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .focusable(interactionSource = reorderInteraction)
                            .clickable {
                                view.performHapticTap()
                                onEnterReorderMode()
                            },
                        shape = RoundedCornerShape(8.dp),
                        color = if (reorderFocused) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                        border = BorderStroke(
                            1.dp,
                            if (reorderFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        )
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.SwapVert,
                                contentDescription = stringResource(R.string.action_reorder),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                }

                Box {
                    val switcherInteraction = remember { MutableInteractionSource() }
                    val switcherFocused by switcherInteraction.collectIsFocusedAsState()
                    Surface(
                        modifier = Modifier
                            .height(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .focusable(interactionSource = switcherInteraction)
                            .clickable {
                                view.performHapticTap()
                                showColumnMenu = true
                            },
                        shape = RoundedCornerShape(8.dp),
                        color = if (switcherFocused) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                        border = BorderStroke(
                            1.dp,
                            if (switcherFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = if (currentCols == 1) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
                                contentDescription = stringResource(R.string.cd_layout_view),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = currentCols.toString(),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = showColumnMenu,
                        onDismissRequest = { showColumnMenu = false },
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        (1..6).forEach { count ->
                            val isOptionSelected = count == currentCols
                            val optionText = if (count == 1) {
                                stringResource(R.string.view_mode_list)
                            } else {
                                stringResource(R.string.view_columns_option, count)
                            }
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = optionText,
                                        fontWeight = if (isOptionSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isOptionSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (count == 1) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
                                        contentDescription = null,
                                        tint = if (isOptionSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                trailingIcon = if (isOptionSelected) {
                                    {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                } else null,
                                onClick = {
                                    view.performHapticTap()
                                    onColumnCountChange(count)
                                    showColumnMenu = false
                                }
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider(
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
        )

        // RIDA 2: Kerged alamfiltri kapslid (selge "Kõik" nupuga)
        AnimatedVisibility(
            visible = subCategories.isNotEmpty(),
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column {
                Spacer(modifier = Modifier.height(8.dp))

                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(28.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    item {
                        val isCountryCategory = StationListFilter.isCountryCategory(selectedCategory)
                        val flagPrefix = if (isCountryCategory) "${getFlagEmoji(selectedCategory)} " else ""
                        SubFilterPill(
                            label = "$flagPrefix$allLabel",
                            isSelected = selectedSubCategories.isEmpty(),
                            showCheckIcon = false,
                            onClick = {
                                view.performHapticTap()
                                onCategorySelect(selectedCategory)
                            }
                        )
                    }
                    items(subCategories.size) { index ->
                        val sub = subCategories[index]
                        SubFilterPill(
                            label = sub,
                            isSelected = selectedSubCategories.contains(sub),
                            showCheckIcon = true,
                            onClick = {
                                view.performHapticTap()
                                onSubCategoryToggle(sub)
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            pageSpacing = 16.dp,
            userScrollEnabled = !activeReorderMode
        ) { pageIndex ->
            val pageCategory = categories.getOrNull(pageIndex) ?: ""
            val isPageReorderActive = activeReorderMode && pageCategory == StationListFilter.CATEGORY_FAVORITES
            val stationsForThisPage = remember(pageCategory, selectedCategory, filteredStations, stations) {
                if (pageCategory == selectedCategory) {
                    filteredStations
                } else {
                    StationListFilter.filterAndSortStations(stations, pageCategory)
                }
            }

            var workingStations by remember(pageCategory) { mutableStateOf(stationsForThisPage) }
            val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
            val dragDropState = rememberGridDragDropState(
                gridState = gridState,
                itemsProvider = { workingStations.map { it.id } },
                onMove = { fromIndex, toIndex ->
                    if (fromIndex in workingStations.indices && toIndex in workingStations.indices && fromIndex != toIndex) {
                        val updated = workingStations.toMutableList()
                        val moved = updated.removeAt(fromIndex)
                        updated.add(toIndex, moved)
                        workingStations = updated
                    }
                },
                onSwapFeedback = { view.performHapticTap() },
                onDragEnd = { onSaveFavoritesOrder(workingStations) }
            )

            LaunchedEffect(stationsForThisPage, isPageReorderActive, dragDropState.draggedItemId) {
                if (dragDropState.draggedItemId == null) {
                    workingStations = stationsForThisPage
                }
            }

            val itemsToDisplay = if (isPageReorderActive) workingStations else stationsForThisPage

            var hasScrolledInitial by remember(pageCategory) { mutableStateOf(false) }
            LaunchedEffect(selectedStationId, stationsForThisPage, isPageReorderActive) {
                if (isPageReorderActive) return@LaunchedEffect
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

            if (itemsToDisplay.isEmpty()) {
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
                val handleZoneWidthPx = with(density) { if (currentCols == 1) 56.dp.toPx() else 36.dp.toPx() }
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Fixed(currentCols),
                    modifier = Modifier
                        .fillMaxSize()
                        .reorderGridDragContainer(
                            dragDropState = dragDropState,
                            enabled = isPageReorderActive,
                            handleZoneWidthPx = handleZoneWidthPx,
                            onDragStartFeedback = { haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
                        ),
                    contentPadding = PaddingValues(bottom = 80.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(if (currentCols == 1) 6.dp else 8.dp)
                ) {
                    items(itemsToDisplay, key = { it.id }) { station ->
                        val isSelected = station.id == selectedStationId
                        val isDragging = isPageReorderActive && dragDropState.draggedItemId == station.id
                        val shouldShowFlagInThisCategory = pageCategory == StationListFilter.CATEGORY_FAVORITES ||
                            pageCategory == StationListFilter.CATEGORY_MY ||
                            pageCategory == StationListFilter.CATEGORY_ALL

                        val itemModifier = Modifier
                            .zIndex(if (isDragging) 1f else 0f)
                            .then(if (isPageReorderActive && !isDragging) Modifier.animateItem() else Modifier)
                            .graphicsLayer {
                                if (isDragging) {
                                    val translation = dragDropState.draggedItemTranslation
                                    translationX = translation.x
                                    translationY = translation.y
                                    scaleX = 1.03f
                                    scaleY = 1.03f
                                    shadowElevation = 16f
                                }
                            }

                        StationGridItem(
                            station = station,
                            isSelected = isSelected,
                            playbackState = playbackState,
                            onClick = { onStationSelect(station) },
                            onLongClick = { onStationLongClick(station) },
                            showFavoriteIcon = pageCategory != StationListFilter.CATEGORY_FAVORITES,
                            showFlag = showFlags && shouldShowFlagInThisCategory,
                            columnCount = currentCols,
                            isReorderMode = isPageReorderActive,
                            isDragging = isDragging,
                            modifier = itemModifier
                        )
                    }
                }
            }
        }
    }
}

/**
 * Primary tuner band selector tab with unified Titanium & Amber styling.
 */
@Composable
private fun TunerBandTab(
    label: String,
    categoryId: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val containerColor = when {
        isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
        isFocused -> MaterialTheme.colorScheme.surfaceVariant
        else -> Color.Transparent
    }
    val border = when {
        isSelected -> BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.75f))
        isFocused -> BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
        else -> null
    }
    val textColor = when {
        isSelected -> MaterialTheme.colorScheme.primary
        isFocused -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val leadingIcon = when (categoryId) {
        StationListFilter.CATEGORY_FAVORITES -> Icons.Default.Star
        StationListFilter.CATEGORY_MY -> Icons.Default.Bookmark
        else -> null
    }

    Surface(
        modifier = Modifier
            .height(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .focusable(interactionSource = interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = containerColor,
        border = border
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = if (isSelected && categoryId == StationListFilter.CATEGORY_FAVORITES) {
                        MaterialTheme.colorScheme.onSecondary
                    } else {
                        textColor.copy(alpha = 0.85f)
                    },
                    modifier = Modifier.size(13.dp)
                )
            }
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                letterSpacing = 0.2.sp,
                color = textColor,
                maxLines = 1
            )
        }
    }
}

/**
 * Secondary pill filter visually distinct from the rectangular station preset cards below.
 */
@Composable
private fun SubFilterPill(
    label: String,
    isSelected: Boolean,
    showCheckIcon: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val containerColor = when {
        isSelected -> MaterialTheme.colorScheme.secondaryContainer
        isFocused -> MaterialTheme.colorScheme.surfaceVariant
        else -> MaterialTheme.colorScheme.surface
    }
    val borderColor = when {
        isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
        isFocused -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)
    }
    val textColor = when {
        isSelected -> MaterialTheme.colorScheme.primary
        isFocused -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        modifier = Modifier
            .height(28.dp)
            .clip(CircleShape)
            .focusable(interactionSource = interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        shape = CircleShape,
        color = containerColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (isSelected && showCheckIcon) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = textColor,
                    modifier = Modifier.size(12.dp)
                )
            }
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = textColor,
                maxLines = 1
            )
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
        RadioStation(id = 1, name = "Vikerraadio", url = "https://icecast.err.ee/vikerraadio.mp3", countryCode = "EE", category = "ERR", isFavorite = true),
        RadioStation(id = 2, name = "Rock FM", url = "https://stream.skymedia.ee/live/RockFM", countryCode = "EE", category = "Sky", isFavorite = false),
        RadioStation(id = 3, name = "Minu Raadio", url = "https://example.com/my.mp3", countryCode = "EE", isUserStation = true)
    )
    MaterialTheme(colorScheme = darkColorScheme()) {
        StationList(
            stations = sampleStations,
            filteredStations = sampleStations,
            categories = listOf("Favorites", "My", "EE", "All"),
            subCategories = listOf("ERR", "Sky"),
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
