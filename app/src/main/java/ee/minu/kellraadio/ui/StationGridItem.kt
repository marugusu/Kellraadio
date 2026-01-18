package ee.minu.kellraadio.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import ee.minu.kellraadio.RadioStation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StationGridItem(
    station: RadioStation,
    isSelected: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    showFavoriteIcon: Boolean,
    showFlag: Boolean // UUS PARAMEETER
) {
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    var isLongClickPerformed by remember { mutableStateOf(false) }

    LaunchedEffect(interactionSource) {
        var pressJob: kotlinx.coroutines.Job? = null

        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> {
                    isLongClickPerformed = false
                    pressJob = launch {
                        delay(700)
                        isLongClickPerformed = true
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLongClick()
                    }
                }
                is PressInteraction.Release -> {
                    pressJob?.cancel()
                }
                is PressInteraction.Cancel -> {
                    pressJob?.cancel()
                }
            }
        }
    }

    val containerColor = if (isSelected && isPlaying) {
        MaterialTheme.colorScheme.primaryContainer
    } else if (isFocused) {
        MaterialTheme.colorScheme.surfaceVariant
    } else {
        MaterialTheme.colorScheme.surface
    }

    val borderStroke = if (isFocused) {
        BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
    } else if (isSelected) {
        BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
    } else {
        null
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .clip(RoundedCornerShape(8.dp))
            .combinedClickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = {
                    if (!isLongClickPerformed) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onClick()
                    }
                },
                onLongClick = { }
            ),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = if (isSelected && isPlaying) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isFocused || (isSelected && isPlaying)) 8.dp else 2.dp
        ),
        border = borderStroke
    ) {
        Box(modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp)) {

            // --- LIPP ---
            if (showFlag && station.countryCode.isNotEmpty()) {
                Text(
                    text = getFlagEmoji(station.countryCode),
                    //style = MaterialTheme.typography.labelSmall,
                    style = androidx.compose.ui.text.TextStyle(fontSize = 9.sp),

                            modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 4.dp, top = 4.dp)
                )
            }
            // ------------

            Text(
                text = station.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Normal,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.Center)
            )

            if (station.isFavorite && showFavoriteIcon) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondary, // Oranžikas
                    modifier = Modifier
                        .size(16.dp)
                        .align(Alignment.TopEnd)
                        .padding(top = 4.dp, end = 4.dp)
                )
            }
        }
    }
}
