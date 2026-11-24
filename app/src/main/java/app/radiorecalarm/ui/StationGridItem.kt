package app.radiorecalarm.ui

import android.view.KeyEvent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.radiorecalarm.R
import app.radiorecalarm.RadioStation
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class StationPlaybackState {
    IDLE,
    BUFFERING,
    PLAYING,
    ERROR
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StationGridItem(
    station: RadioStation,
    isSelected: Boolean,
    playbackState: StationPlaybackState,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    showFavoriteIcon: Boolean,
    showFlag: Boolean,
    columnCount: Int = 3,
    isReorderMode: Boolean = false,
    isDragging: Boolean = false,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    var isLongPressDetected by remember { mutableStateOf(false) }
    var pressJob by remember { mutableStateOf<Job?>(null) }

    val activeState = if (isSelected) playbackState else StationPlaybackState.IDLE
    val isRowMode = columnCount <= 2
    val isTwoColumn = columnCount == 2
    val isCompact = columnCount >= 4

    val pulseAlpha = if (activeState == StationPlaybackState.BUFFERING) {
        val infiniteTransition = rememberInfiniteTransition(label = "station_buffering")
        infiniteTransition.animateFloat(
            initialValue = 0.35f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 700),
                repeatMode = RepeatMode.Reverse
            ),
            label = "buffering_border_alpha"
        ).value
    } else {
        1f
    }

    val containerColor = when {
        isDragging -> MaterialTheme.colorScheme.primaryContainer
        activeState == StationPlaybackState.ERROR -> MaterialTheme.colorScheme.errorContainer
        activeState == StationPlaybackState.PLAYING -> MaterialTheme.colorScheme.primaryContainer
        isFocused -> MaterialTheme.colorScheme.secondaryContainer
        activeState == StationPlaybackState.BUFFERING -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    val contentColor = when {
        isDragging -> MaterialTheme.colorScheme.onPrimaryContainer
        activeState == StationPlaybackState.ERROR -> MaterialTheme.colorScheme.onErrorContainer
        activeState == StationPlaybackState.PLAYING -> MaterialTheme.colorScheme.onPrimaryContainer
        isFocused -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }

    val borderStroke = when {
        isDragging -> BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        isReorderMode -> BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
        activeState == StationPlaybackState.ERROR -> BorderStroke(1.5.dp, MaterialTheme.colorScheme.error)
        activeState == StationPlaybackState.BUFFERING -> BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = pulseAlpha))
        isFocused || activeState == StationPlaybackState.PLAYING -> BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
        isSelected -> BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
        else -> BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    }

    val cardShape = RoundedCornerShape(if (isCompact) 8.dp else 10.dp)
    val cardHeight = when {
        isRowMode -> 64.dp
        isCompact -> 60.dp
        else -> 70.dp
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(cardHeight)
            .clip(cardShape)
            .onKeyEvent { event ->
                val isEnter = event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                    event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_ENTER ||
                    event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER

                if (!isEnter) return@onKeyEvent false

                if (event.type == KeyEventType.KeyDown) {
                    if (event.nativeKeyEvent.repeatCount == 0) {
                        isLongPressDetected = false
                        pressJob?.cancel()
                        scope.launch { interactionSource.emit(PressInteraction.Press(Offset.Zero)) }
                        pressJob = scope.launch {
                            delay(500)
                            isLongPressDetected = true
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                    }
                    return@onKeyEvent true
                } else if (event.type == KeyEventType.KeyUp) {
                    pressJob?.cancel()
                    scope.launch { interactionSource.emit(PressInteraction.Release(PressInteraction.Press(Offset.Zero))) }
                    if (isLongPressDetected || isReorderMode) {
                        onLongClick()
                    } else {
                        view.performHapticTap()
                        onClick()
                    }
                    isLongPressDetected = false
                    return@onKeyEvent true
                }
                false
            }
            .then(
                if (isReorderMode) {
                    Modifier
                } else {
                    Modifier.pointerInput(Unit) {
                        detectTapGestures(
                            onLongPress = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onLongClick()
                            },
                            onTap = {
                                view.performHapticTap()
                                onClick()
                            },
                            onPress = {
                                val press = PressInteraction.Press(it)
                                interactionSource.emit(press)
                                tryAwaitRelease()
                                interactionSource.emit(PressInteraction.Release(press))
                            }
                        )
                    }
                }
            )
            .indication(interactionSource, androidx.compose.foundation.LocalIndication.current)
            .hoverable(interactionSource)
            .focusable(interactionSource = interactionSource),
        shape = cardShape,
        colors = CardDefaults.cardColors(containerColor = containerColor, contentColor = contentColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = borderStroke
    ) {
        if (isRowMode) {
            StationListRowContent(
                station = station,
                activeState = activeState,
                contentColor = contentColor,
                showFavoriteIcon = showFavoriteIcon,
                showFlag = showFlag,
                isTwoColumn = isTwoColumn,
                isReorderMode = isReorderMode,
                onMoreClick = {
                    view.performHapticTap()
                    onLongClick()
                }
            )
        } else {
            StationGridCardContent(
                station = station,
                activeState = activeState,
                pulseAlpha = pulseAlpha,
                contentColor = contentColor,
                showFavoriteIcon = showFavoriteIcon,
                showFlag = showFlag,
                isCompact = isCompact,
                isReorderMode = isReorderMode
            )
        }
    }
}

@Composable
private fun StationListRowContent(
    station: RadioStation,
    activeState: StationPlaybackState,
    contentColor: Color,
    showFavoriteIcon: Boolean,
    showFlag: Boolean,
    isTwoColumn: Boolean,
    isReorderMode: Boolean,
    onMoreClick: () -> Unit
) {
    val badgeBg = when (activeState) {
        StationPlaybackState.PLAYING -> MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
        StationPlaybackState.ERROR -> MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
        else -> MaterialTheme.colorScheme.surface
    }
    val badgeBorder = when (activeState) {
        StationPlaybackState.PLAYING -> MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
        StationPlaybackState.ERROR -> MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
        else -> MaterialTheme.colorScheme.outline
    }
    val subtitleParts = buildList {
        if (showFlag && station.countryCode.isNotEmpty()) {
            add(station.countryCode.uppercase())
        }
        val cleanCategory = station.category.trim()
        if (cleanCategory.isNotEmpty() && !(station.isUserStation && cleanCategory.equals("My", ignoreCase = true))) {
            add(cleanCategory)
        }
    }

    val startPadding = if (isTwoColumn) 8.dp else 12.dp
    val endPadding = if (isTwoColumn) 2.dp else 6.dp
    val logoSize = if (isTwoColumn) 36.dp else 40.dp
    val logoCornerRadius = if (isTwoColumn) 7.dp else 8.dp
    val logoInnerPadding = if (isTwoColumn) 3.dp else 4.dp
    val logoSpacer = if (isTwoColumn) 8.dp else 12.dp
    val actionSlotWidth = if (isTwoColumn) 28.dp else 36.dp

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = startPadding, end = endPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StationLogoBadge(
            stationName = station.name,
            iconUrl = station.iconUrl,
            size = logoSize,
            cornerRadius = logoCornerRadius,
            innerPadding = logoInnerPadding,
            borderColor = badgeBorder,
            fallbackBackground = badgeBg,
            fallbackTextColor = if (activeState == StationPlaybackState.PLAYING) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            fallbackFontSize = if (isTwoColumn) 10.sp else 11.sp
        )

        Spacer(modifier = Modifier.width(logoSpacer))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = station.name,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = if (isTwoColumn) 13.sp else 15.sp,
                    lineHeight = if (isTwoColumn) 15.sp else 19.sp
                ),
                fontWeight = if (activeState == StationPlaybackState.PLAYING) FontWeight.Bold else FontWeight.SemiBold,
                color = contentColor,
                maxLines = if (isTwoColumn) 2 else 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitleParts.isNotEmpty()) {
                Text(
                    text = subtitleParts.joinToString(" • "),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = if (isTwoColumn) 10.sp else 11.sp
                    ),
                    color = contentColor.copy(alpha = 0.65f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (activeState == StationPlaybackState.PLAYING) {
            LiveSignalBars(
                color = MaterialTheme.colorScheme.primary,
                barWidth = if (isTwoColumn) 2.dp else 2.5.dp,
                maxHeight = if (isTwoColumn) 12.dp else 14.dp,
                modifier = Modifier.padding(horizontal = if (isTwoColumn) 3.dp else 6.dp)
            )
        } else if (activeState == StationPlaybackState.ERROR) {
            Icon(
                imageVector = Icons.Default.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .padding(horizontal = if (isTwoColumn) 3.dp else 6.dp)
                    .size(if (isTwoColumn) 14.dp else 16.dp)
            )
        }

        if (station.isFavorite && showFavoriteIcon) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondary,
                modifier = Modifier
                    .padding(horizontal = if (isTwoColumn) 2.dp else 4.dp)
                    .size(if (isTwoColumn) 13.dp else 15.dp)
            )
        }

        if (isReorderMode) {
            Box(
                modifier = Modifier.size(width = actionSlotWidth, height = 36.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.DragIndicator,
                    contentDescription = stringResource(R.string.action_reorder),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(if (isTwoColumn) 18.dp else 22.dp)
                )
            }
        } else {
            IconButton(
                onClick = onMoreClick,
                modifier = Modifier
                    .size(width = actionSlotWidth, height = 36.dp)
                    .focusProperties { canFocus = false }
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.action_more),
                    tint = contentColor.copy(alpha = 0.7f),
                    modifier = Modifier.size(if (isTwoColumn) 16.dp else 18.dp)
                )
            }
        }
    }
}

@Composable
private fun StationGridCardContent(
    station: RadioStation,
    activeState: StationPlaybackState,
    pulseAlpha: Float,
    contentColor: Color,
    showFavoriteIcon: Boolean,
    showFlag: Boolean,
    isCompact: Boolean,
    isReorderMode: Boolean
) {
    val horizontalPad = if (isCompact) 6.dp else 8.dp
    val verticalPad = if (isCompact) 5.dp else 6.dp
    val microTrackHeight = if (isCompact) 12.dp else 14.dp

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = horizontalPad, vertical = verticalPad)
    ) {
        // Ülemine mikroriba (riigikood, elav signaal, lemmik)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(microTrackHeight),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showFlag && station.countryCode.isNotEmpty()) {
                Text(
                    text = station.countryCode.uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = if (isCompact) 8.sp else 9.sp,
                        letterSpacing = 0.5.sp
                    ),
                    color = when (activeState) {
                        StationPlaybackState.ERROR -> MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.75f)
                        StationPlaybackState.PLAYING -> MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                    },
                    fontWeight = FontWeight.Bold
                )
            } else {
                Spacer(modifier = Modifier.width(1.dp))
            }

            Spacer(modifier = Modifier.weight(1f))

            if (activeState == StationPlaybackState.PLAYING) {
                LiveSignalBars(
                    color = MaterialTheme.colorScheme.primary,
                    barWidth = if (isCompact) 1.5.dp else 2.dp,
                    maxHeight = if (isCompact) 9.dp else 11.dp
                )
                if (station.isFavorite && showFavoriteIcon) {
                    Spacer(modifier = Modifier.width(4.dp))
                }
            } else if (activeState == StationPlaybackState.ERROR) {
                Icon(
                    imageVector = Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(if (isCompact) 10.dp else 12.dp)
                )
                if (station.isFavorite && showFavoriteIcon) {
                    Spacer(modifier = Modifier.width(4.dp))
                }
            }

            if (isReorderMode) {
                Icon(
                    imageVector = Icons.Default.DragIndicator,
                    contentDescription = stringResource(R.string.action_reorder),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(if (isCompact) 11.dp else 13.dp)
                )
            } else if (station.isFavorite && showFavoriteIcon) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondary,
                    modifier = Modifier.size(if (isCompact) 10.dp else 12.dp)
                )
            }
        }

        // Põhiline nimeala
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = station.name,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = if (isCompact) 11.5.sp else 13.5.sp,
                    lineHeight = if (isCompact) 14.sp else 17.sp,
                    letterSpacing = 0.sp
                ),
                fontWeight = if (activeState == StationPlaybackState.PLAYING) FontWeight.Bold else FontWeight.SemiBold,
                color = contentColor,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Alumine tuuneri-indikaatori triip aktiivsel jaamal
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp),
            contentAlignment = Alignment.Center
        ) {
            val barColor = when (activeState) {
                StationPlaybackState.PLAYING -> MaterialTheme.colorScheme.primary
                StationPlaybackState.BUFFERING -> MaterialTheme.colorScheme.primary.copy(alpha = pulseAlpha)
                StationPlaybackState.ERROR -> MaterialTheme.colorScheme.error
                StationPlaybackState.IDLE -> Color.Transparent
            }
            if (barColor != Color.Transparent) {
                Box(
                    modifier = Modifier
                        .width(if (isCompact) 18.dp else 28.dp)
                        .height(2.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(barColor)
                )
            }
        }
    }
}

/**
 * Animated 3-bar audio/signal meter shown on the actively playing station card.
 */
@Composable
private fun LiveSignalBars(
    color: Color,
    barWidth: Dp,
    maxHeight: Dp,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "live_signal_bars")
    val bar1 by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 380, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "signal_bar_1"
    )
    val bar2 by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 460, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "signal_bar_2"
    )
    val bar3 by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 320, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "signal_bar_3"
    )

    Row(
        modifier = modifier.height(maxHeight),
        horizontalArrangement = Arrangement.spacedBy(1.5.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        listOf(bar1, bar2, bar3).forEach { fraction ->
            Box(
                modifier = Modifier
                    .width(barWidth)
                    .fillMaxHeight(fraction.coerceIn(0.25f, 1f))
                    .clip(RoundedCornerShape(1.dp))
                    .background(color)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun StationGridItemStatesPreview() {
    val sample = RadioStation(
        id = 1,
        name = "Rock FM",
        url = "https://stream.skymedia.ee/live/RockFM",
        countryCode = "EE",
        category = "Sky",
        isFavorite = true
    )
    MaterialTheme(colorScheme = darkColorScheme()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StationPlaybackState.entries.forEach { state ->
                    Box(modifier = Modifier.weight(1f)) {
                        StationGridItem(
                            station = sample,
                            isSelected = state != StationPlaybackState.IDLE,
                            playbackState = state,
                            onClick = {},
                            onLongClick = {},
                            showFavoriteIcon = true,
                            showFlag = true,
                            columnCount = 3
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(modifier = Modifier.weight(1f)) {
                    StationGridItem(
                        station = sample,
                        isSelected = true,
                        playbackState = StationPlaybackState.PLAYING,
                        onClick = {},
                        onLongClick = {},
                        showFavoriteIcon = true,
                        showFlag = true,
                        columnCount = 2
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    StationGridItem(
                        station = sample.copy(name = "Klassikaraadio"),
                        isSelected = false,
                        playbackState = StationPlaybackState.IDLE,
                        onClick = {},
                        onLongClick = {},
                        showFavoriteIcon = false,
                        showFlag = true,
                        columnCount = 2
                    )
                }
            }
            StationGridItem(
                station = sample,
                isSelected = true,
                playbackState = StationPlaybackState.PLAYING,
                onClick = {},
                onLongClick = {},
                showFavoriteIcon = true,
                showFlag = true,
                columnCount = 1
            )
        }
    }
}
