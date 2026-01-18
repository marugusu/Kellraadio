package ee.minu.kellraadio.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import ee.minu.kellraadio.RadioStation

@Composable
fun StationActionSheet(
    station: RadioStation,
    onDismiss: () -> Unit,
    onToggleFavorite: () -> Unit,
    onSetAlarm: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    // MUUDATUS: Kasutame Dialogi ModalBottomSheet asemel
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth(0.9f) // Laius 90% ekraanist (nii tel kui tv)
                .wrapContentHeight()
        ) {
            Column(
                modifier = Modifier.padding(vertical = 16.dp)
            ) {
                // PÄIS
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Radio,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = station.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                }

                Divider(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.padding(vertical = 8.dp))

                // MENÜÜ PUNKTID
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
                    Divider(modifier = Modifier.padding(vertical = 8.dp, horizontal = 16.dp), thickness = 0.5.dp)

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

                // SULGEMISE NUPP (Valikuline, aga telekas hea)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp, end = 16.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Sulge")
                    }
                }
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
            .padding(horizontal = 24.dp, vertical = 12.dp),
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