package app.radiorecalarm.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.grid.LazyGridItemInfo
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Coordinates drag-and-drop reordering over a [LazyGridState] using viewport-relative finger
 * coordinates so item swaps and edge auto-scrolling remain visually stable across 1..6 columns.
 */
class GridDragDropState(
    val gridState: LazyGridState,
    private val itemsProvider: () -> List<Int>,
    private val onMove: (fromIndex: Int, toIndex: Int) -> Unit,
    private val onSwapFeedback: () -> Unit,
    private val onDragEnd: () -> Unit
) {
    var draggedItemId by mutableStateOf<Int?>(null)
        private set

    var fingerPosition by mutableStateOf(Offset.Zero)
        private set

    private var fingerOffsetInItem by mutableStateOf(Offset.Zero)

    val draggedItemTranslation: Offset
        get() {
            val id = draggedItemId ?: return Offset.Zero
            val info = gridState.layoutInfo.visibleItemsInfo.find { it.key == id } ?: return Offset.Zero
            val desiredTopLeft = fingerPosition - fingerOffsetInItem
            return Offset(
                x = desiredTopLeft.x - info.offset.x.toFloat(),
                y = desiredTopLeft.y - info.offset.y.toFloat()
            )
        }

    fun startDrag(itemInfo: LazyGridItemInfo, startPointerPosition: Offset) {
        val id = itemInfo.key as? Int ?: return
        draggedItemId = id
        fingerPosition = startPointerPosition
        fingerOffsetInItem = Offset(
            x = startPointerPosition.x - itemInfo.offset.x.toFloat(),
            y = startPointerPosition.y - itemInfo.offset.y.toFloat()
        )
    }

    fun dragBy(delta: Offset) {
        val id = draggedItemId ?: return
        fingerPosition += delta

        val currentIds = itemsProvider()
        val currentIndex = currentIds.indexOf(id)
        if (currentIndex < 0) return

        val draggedInfo = gridState.layoutInfo.visibleItemsInfo.find { it.key == id } ?: return
        // Wait until LazyVerticalGrid has laid out the previous swap before attempting another.
        if (draggedInfo.index != currentIndex) return

        val desiredTopLeft = fingerPosition - fingerOffsetInItem
        val desiredCenter = Offset(
            x = desiredTopLeft.x + draggedInfo.size.width / 2f,
            y = desiredTopLeft.y + draggedInfo.size.height / 2f
        )

        val targetInfo = gridState.layoutInfo.visibleItemsInfo.firstOrNull { item ->
            item.key != id &&
                desiredCenter.x >= item.offset.x &&
                desiredCenter.x <= item.offset.x + item.size.width &&
                desiredCenter.y >= item.offset.y &&
                desiredCenter.y <= item.offset.y + item.size.height
        }

        if (targetInfo != null && targetInfo.index in currentIds.indices && targetInfo.index != currentIndex) {
            onMove(currentIndex, targetInfo.index)
            onSwapFeedback()
        }
    }

    fun endDrag() {
        if (draggedItemId != null) {
            draggedItemId = null
            fingerPosition = Offset.Zero
            fingerOffsetInItem = Offset.Zero
            onDragEnd()
        }
    }

    fun computeOverscrollDelta(edgeThresholdPx: Float, maxStepPx: Float): Float {
        val id = draggedItemId ?: return 0f
        val draggedInfo = gridState.layoutInfo.visibleItemsInfo.find { it.key == id } ?: return 0f
        val desiredTop = fingerPosition.y - fingerOffsetInItem.y
        val desiredBottom = desiredTop + draggedInfo.size.height
        val viewportStart = gridState.layoutInfo.viewportStartOffset.toFloat()
        val viewportEnd = gridState.layoutInfo.viewportEndOffset.toFloat()

        return when {
            desiredTop < viewportStart + edgeThresholdPx -> {
                val ratio = ((viewportStart + edgeThresholdPx - desiredTop) / edgeThresholdPx).coerceIn(0f, 1f)
                -maxStepPx * ratio
            }
            desiredBottom > viewportEnd - edgeThresholdPx -> {
                val ratio = ((desiredBottom - (viewportEnd - edgeThresholdPx)) / edgeThresholdPx).coerceIn(0f, 1f)
                maxStepPx * ratio
            }
            else -> 0f
        }
    }
}

@Composable
fun rememberGridDragDropState(
    gridState: LazyGridState,
    itemsProvider: () -> List<Int>,
    onMove: (fromIndex: Int, toIndex: Int) -> Unit,
    onSwapFeedback: () -> Unit,
    onDragEnd: () -> Unit
): GridDragDropState {
    val currentItemsProvider by rememberUpdatedState(itemsProvider)
    val currentOnMove by rememberUpdatedState(onMove)
    val currentOnSwapFeedback by rememberUpdatedState(onSwapFeedback)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)

    val state = remember(gridState) {
        GridDragDropState(
            gridState = gridState,
            itemsProvider = { currentItemsProvider() },
            onMove = { from, to -> currentOnMove(from, to) },
            onSwapFeedback = { currentOnSwapFeedback() },
            onDragEnd = { currentOnDragEnd() }
        )
    }

    val density = LocalDensity.current
    val edgeThresholdPx = with(density) { 56.dp.toPx() }
    val maxStepPx = with(density) { 14.dp.toPx() }

    LaunchedEffect(state.draggedItemId) {
        while (state.draggedItemId != null) {
            val delta = state.computeOverscrollDelta(edgeThresholdPx, maxStepPx)
            if (delta != 0f) {
                gridState.scrollBy(delta)
            }
            delay(16L)
        }
    }

    return state
}

/**
 * Enables drag-and-drop in reorder mode: touching the right-side handle starts dragging immediately
 * after touch slop, while touching elsewhere on a card preserves scrolling and drags on long-press.
 */
fun Modifier.reorderGridDragContainer(
    dragDropState: GridDragDropState,
    enabled: Boolean,
    handleZoneWidthPx: Float,
    onDragStartFeedback: () -> Unit
): Modifier {
    if (!enabled) return this
    return this.pointerInput(dragDropState, handleZoneWidthPx) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val hitItem = dragDropState.gridState.layoutInfo.visibleItemsInfo.firstOrNull { item ->
                down.position.x >= item.offset.x &&
                    down.position.x <= item.offset.x + item.size.width &&
                    down.position.y >= item.offset.y &&
                    down.position.y <= item.offset.y + item.size.height
            } ?: return@awaitEachGesture

            val touchedHandle = down.position.x >= (hitItem.offset.x + hitItem.size.width - handleZoneWidthPx)
            val startChange = if (touchedHandle) {
                awaitTouchSlopOrCancellation(down.id) { change, _ ->
                    change.consume()
                }
            } else {
                awaitLongPressOrCancellation(down.id)
            }

            if (startChange != null) {
                dragDropState.startDrag(hitItem, startChange.position)
                onDragStartFeedback()
                val draggedToEnd = drag(startChange.id) { change ->
                    val delta = change.positionChange()
                    change.consume()
                    dragDropState.dragBy(delta)
                }
                if (draggedToEnd || dragDropState.draggedItemId != null) {
                    dragDropState.endDrag()
                }
            }
        }
    }
}
