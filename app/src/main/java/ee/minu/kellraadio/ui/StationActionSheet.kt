package ee.minu.kellraadio.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import ee.minu.kellraadio.RadioStation

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StationActionSheet(
    station: RadioStation,
    onDismiss: () -> Unit,
    onToggleFavorite: () -> Unit,
    onSetAlarm: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() } // Standardne, puhas lahendus
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            // Päis
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 16.dp), // Top 0, et olla lähedal
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Radio,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text = station.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
            }

            Divider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 0.5.dp)

            // ... (ülejäänud nupud ActionItem jäävad täpselt samaks) ...

            ActionItem(
                icon = if (station.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                text = if (station.isFavorite) "Eemalda lemmikutest" else "Lisa lemmikuks",
                onClick = { onToggleFavorite(); onDismiss() }
            )

            ActionItem(
                icon = Icons.Default.Alarm,
                text = "Sea äratus",
                onClick = { onSetAlarm(); onDismiss() }
            )

            if (station.isUserStation) {
                Divider(modifier = Modifier.padding(vertical = 4.dp), thickness = 0.5.dp)

                ActionItem(
                    icon = Icons.Default.Edit,
                    text = "Muuda jaama",
                    onClick = { onEdit(); onDismiss() }
                )

                ActionItem(
                    icon = Icons.Default.Delete,
                    text = "Kustuta jaam",
                    textColor = MaterialTheme.colorScheme.error,
                    iconColor = MaterialTheme.colorScheme.error,
                    onClick = { onDelete(); onDismiss() }
                )
            }
        }
    }
}

@Composable
fun ActionItem(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
    textColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    iconColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = textColor
        )
    }
}