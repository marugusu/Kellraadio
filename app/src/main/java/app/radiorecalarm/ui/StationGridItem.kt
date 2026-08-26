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
    
    val borderStroke = when {
        isFocused || (isSelected && isPlaying) -> BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
        isSelected -> BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
        else -> BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(66.dp)
            .clip(RoundedCornerShape(8.dp))
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

        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor, contentColor = contentColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = borderStroke
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            // Tsoon 1: Ülemine mikroriba (Top Status Track)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (showFlag && station.countryCode.isNotEmpty()) {
                    Text(
                        text = station.countryCode.uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 9.sp,
                            letterSpacing = 0.5.sp
                        ),
                        color = if (isSelected && isPlaying) {
                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        },
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                Spacer(modifier = Modifier.weight(1f))

                if (station.isFavorite && showFavoriteIcon) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondary,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }

            // Tsoon 2: Põhiline nimeala (Main Name Stage)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = station.name,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 13.sp,
                        lineHeight = 16.sp,
                        letterSpacing = 0.sp
                    ),
                    fontWeight = if (isSelected && isPlaying) FontWeight.Bold else FontWeight.SemiBold,
                    color = contentColor,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
