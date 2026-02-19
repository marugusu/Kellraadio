package ws.ct.radiow.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import ws.ct.radiow.R
import ws.ct.radiow.RadioStation

@Composable
fun StationActionSheet(
    station: RadioStation,
    favoritePosition: Int, // UUS: Järjekorranumber lemmikute seas
    onDismiss: () -> Unit,
    onToggleFavorite: () -> Unit,
    onSetAlarm: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onMoveToTop: () -> Unit,
    onMoveToBottom: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .wrapContentHeight()
        ) {
            Column {
                // PEALKIRI
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Radio,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = station.name,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 1.dp)

                // SISU
                Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max)) {
                    // VASAK POOL: TEGEVUSED
                    Column(modifier = Modifier.weight(1f).padding(vertical = 8.dp)) {
                        ActionItem(
                            icon = if (station.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                            text = if (station.isFavorite) stringResource(R.string.action_remove_favorite) else stringResource(R.string.action_add_favorite),
                            onClick = { onToggleFavorite(); onDismiss() }
                        )

                        ActionItem(
                            icon = Icons.Default.Alarm,
                            text = stringResource(R.string.action_set_alarm),
                            onClick = { onSetAlarm(); onDismiss() }
                        )

                        if (station.isUserStation) {
                            ActionItem(
                                icon = Icons.Default.Edit,
                                text = stringResource(R.string.action_edit_station),
                                onClick = { onEdit(); onDismiss() }
                            )

                            ActionItem(
                                icon = Icons.Default.Delete,
                                text = stringResource(R.string.action_delete_station),
                                textColor = MaterialTheme.colorScheme.error,
                                iconColor = MaterialTheme.colorScheme.error,
                                onClick = { onDelete(); onDismiss() }
                            )
                        }
                    }

                    // PAREM POOL: SORTEERIMISE NUPUD (Ainult lemmikute puhul)
                    if (station.isFavorite) {
                        VerticalDivider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 1.dp)
                        
                        Column(
                            modifier = Modifier
                                .width(72.dp)
                                .fillMaxHeight(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.SpaceEvenly
                        ) {
                            SortButton(icon = Icons.Default.KeyboardDoubleArrowUp, onClick = onMoveToTop)
                            SortButton(icon = Icons.Default.KeyboardArrowUp, onClick = onMoveUp)
                            
                            // JÄRJEKORRA NUMBER (Badge stiilis)
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = CircleShape,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = favoritePosition.toString(),
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                            
                            SortButton(icon = Icons.Default.KeyboardArrowDown, onClick = onMoveDown)
                            SortButton(icon = Icons.Default.KeyboardDoubleArrowDown, onClick = onMoveToBottom)
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 1.dp)

                // SULGE NUPP
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(
                            text = stringResource(R.string.action_close).uppercase(), 
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SortButton(icon: ImageVector, onClick: () -> Unit) {
    IconButton(
        onClick = onClick, 
        modifier = Modifier.size(48.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(32.dp)
        )
    }
}

@Composable
fun ActionItem(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    iconColor: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 16.dp),
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
            fontWeight = FontWeight.Medium,
            color = textColor
        )
    }
}