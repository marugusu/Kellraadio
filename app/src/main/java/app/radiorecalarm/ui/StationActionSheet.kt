package app.radiorecalarm.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import app.radiorecalarm.R
import app.radiorecalarm.RadioStation

@Composable
fun StationActionSheet(
    station: RadioStation,
    favoritePosition: Int,
    totalFavorites: Int,
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
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
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
                        val canMoveUp = favoritePosition > 1
                        val canMoveDown = favoritePosition in 1 until totalFavorites
                        VerticalDivider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 1.dp)

                        Column(
                            modifier = Modifier
                                .width(76.dp)
                                .fillMaxHeight()
                                .padding(vertical = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.SpaceEvenly
                        ) {
                            SortButton(
                                icon = Icons.Default.KeyboardDoubleArrowUp,
                                contentDescription = stringResource(R.string.cd_move_top),
                                enabled = canMoveUp,
                                onClick = onMoveToTop
                            )
                            SortButton(
                                icon = Icons.Default.KeyboardArrowUp,
                                contentDescription = stringResource(R.string.cd_move_up),
                                enabled = canMoveUp,
                                onClick = onMoveUp
                            )

                            // JÄRJEKORRA NUMBER (X/N badge)
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.defaultMinSize(minWidth = 36.dp, minHeight = 28.dp)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = if (totalFavorites > 0) "$favoritePosition/$totalFavorites" else favoritePosition.toString(),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }

                            SortButton(
                                icon = Icons.Default.KeyboardArrowDown,
                                contentDescription = stringResource(R.string.cd_move_down),
                                enabled = canMoveDown,
                                onClick = onMoveDown
                            )
                            SortButton(
                                icon = Icons.Default.KeyboardDoubleArrowDown,
                                contentDescription = stringResource(R.string.cd_move_bottom),
                                enabled = canMoveDown,
                                onClick = onMoveToBottom
                            )
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
fun SortButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(48.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
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

@Preview(showBackground = true)
@Composable
private fun StationActionSheetPreview() {
    MaterialTheme(colorScheme = darkColorScheme()) {
        StationActionSheet(
            station = RadioStation(
                id = 1,
                name = "Rock FM",
                url = "https://stream.skymedia.ee/live/RockFM",
                countryCode = "EE",
                isFavorite = true,
                isUserStation = true
            ),
            favoritePosition = 1,
            totalFavorites = 6,
            onDismiss = {},
            onToggleFavorite = {},
            onSetAlarm = {},
            onEdit = {},
            onDelete = {},
            onMoveUp = {},
            onMoveDown = {},
            onMoveToTop = {},
            onMoveToBottom = {}
        )
    }
}
