package ee.minu.kellraadio.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ee.minu.kellraadio.RadioStation

@Composable
fun StationGridItem(
    station: RadioStation,
    isSelected: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current

    // 1. TAUST
    val containerColor = if (isSelected && isPlaying) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }

    // 2. SISU VÄRV
    val contentColor = if (isSelected && isPlaying) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else if (isSelected) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    // 3. ÄÄRIS
    val borderStroke = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null

    // 4. KÕRGUS / VARJU EFEKT
    val elevation = if (isSelected && isPlaying) 8.dp else 2.dp

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor, contentColor = contentColor),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation),
        border = borderStroke
    ) {
        Box(
            modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = station.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}