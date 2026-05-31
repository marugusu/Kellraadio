package app.radiorecalarm.ui

import android.view.KeyEvent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.radiorecalarm.RadioStation
import kotlinx.coroutines.Job
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
    showFlag: Boolean
) {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    var isLongPressDetected by remember { mutableStateOf(false) }
    var pressJob by remember { mutableStateOf<Job?>(null) }

    val containerColor = when {
        isSelected && isPlaying -> MaterialTheme.colorScheme.primaryContainer
        isFocused -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    
    val contentColor = if (isSelected && isPlaying) {
        MaterialTheme.colorScheme.onPrimaryContainer 
    } else if (isFocused) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    
    val borderStroke = if (isFocused || (isSelected && isPlaying)) {
        BorderStroke(1.dp, MaterialTheme.colorScheme.primary) // TAASTATUD: 2dp
    } else if (isSelected) {
        BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
    } else null

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(12.dp))
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
                }
                else if (event.type == KeyEventType.KeyUp) {
                    pressJob?.cancel()
                    scope.launch { interactionSource.emit(PressInteraction.Release(PressInteraction.Press(Offset.Zero))) }
                    if (isLongPressDetected) {
                        onLongClick()
                    } else {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onClick()
                    }
                    isLongPressDetected = false
                    return@onKeyEvent true
                }
                false
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLongClick()
                    },
                    onTap = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
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
            .indication(interactionSource, androidx.compose.foundation.LocalIndication.current)
            .hoverable(interactionSource)
            .focusable(interactionSource = interactionSource),

        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor, contentColor = contentColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isFocused || (isSelected && isPlaying)) 8.dp else 2.dp),
        border = borderStroke
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (showFlag && station.countryCode.isNotEmpty()) {
                Text(
                    text = getFlagEmoji(station.countryCode),
                    style = androidx.compose.ui.text.TextStyle(fontSize = 10.sp),
                    modifier = Modifier.align(Alignment.TopStart).padding(start = 8.dp, top = 4.dp)
                )
            }
            Text(
                text = station.name,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Normal,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(top = 8.dp, start = 4.dp, end = 4.dp)
            )
            if (station.isFavorite && showFavoriteIcon) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondary,
                    modifier = Modifier.size(16.dp).align(Alignment.TopEnd).padding(top = 4.dp, end = 8.dp)
                )
            }
        }
    }
}
