package app.radiorecalarm.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    onMoveToBottom: () -> Unit,
    onEnterReorderMode: () -> Unit = {}
) {
    AdaptiveModalSheet(
        onDismiss = onDismiss,
        dialogMaxWidth = 460.dp,
        dialogWidthFraction = 0.9f,
        dialogMaxHeightFraction = 0.9f
    ) { isDialogMode ->
        StationActionSheetContent(
            station = station,
            favoritePosition = favoritePosition,
            totalFavorites = totalFavorites,
            showCloseButton = isDialogMode,
            onDismiss = onDismiss,
            onToggleFavorite = onToggleFavorite,
            onSetAlarm = onSetAlarm,
            onEdit = onEdit,
            onDelete = onDelete,
            onMoveUp = onMoveUp,
            onMoveDown = onMoveDown,
            onMoveToTop = onMoveToTop,
            onMoveToBottom = onMoveToBottom,
            onEnterReorderMode = onEnterReorderMode,
            modifier = Modifier.padding(bottom = if (isDialogMode) 12.dp else 16.dp)
        )
    }
}

@Composable
private fun StationActionSheetContent(
    station: RadioStation,
    favoritePosition: Int,
    totalFavorites: Int,
    showCloseButton: Boolean,
    onDismiss: () -> Unit,
    onToggleFavorite: () -> Unit,
    onSetAlarm: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onMoveToTop: () -> Unit,
    onMoveToBottom: () -> Unit,
    onEnterReorderMode: () -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val subtitleParts = buildList {
        if (station.countryCode.isNotBlank()) add(station.countryCode.uppercase())
        val cat = station.category.trim()
        if (cat.isNotBlank() && !(station.isUserStation && cat.equals("My", ignoreCase = true))) {
            add(cat)
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // PÄIS: jaama monogramm + nimi + kategooria
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StationLogoBadge(
                stationName = station.name,
                iconUrl = station.iconUrl,
                size = 44.dp,
                cornerRadius = 10.dp,
                borderColor = if (station.isFavorite) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                } else {
                    MaterialTheme.colorScheme.outline
                },
                loadedBorderColor = if (station.isFavorite) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                } else {
                    Color.Transparent
                },
                fallbackBackground = MaterialTheme.colorScheme.surfaceVariant,
                fallbackTextColor = MaterialTheme.colorScheme.primary,
                fallbackFontSize = 12.sp
            )

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = station.name,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 18.sp,
                        lineHeight = 22.sp
                    ),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitleParts.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitleParts.joinToString(" • "),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (showCloseButton) {
                IconButton(
                    onClick = {
                        view.performHapticTap()
                        onDismiss()
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.action_close),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            ActionItem(
                icon = if (station.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                text = if (station.isFavorite) stringResource(R.string.action_remove_favorite) else stringResource(R.string.action_add_favorite),
                iconColor = if (station.isFavorite) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurfaceVariant,
                onClick = {
                    view.performHapticTap()
                    onToggleFavorite()
                    onDismiss()
                }
            )

            ActionItem(
                icon = Icons.Default.Alarm,
                text = stringResource(R.string.action_set_alarm),
                onClick = {
                    view.performHapticTap()
                    onSetAlarm()
                    onDismiss()
                }
            )

            if (station.isFavorite) {
                ActionItem(
                    icon = Icons.Default.SwapVert,
                    text = stringResource(R.string.action_reorder),
                    iconColor = MaterialTheme.colorScheme.primary,
                    onClick = {
                        view.performHapticTap()
                        onEnterReorderMode()
                    }
                )
            }

            if (station.isUserStation) {
                ActionItem(
                    icon = Icons.Default.Edit,
                    text = stringResource(R.string.action_edit_station),
                    onClick = {
                        view.performHapticTap()
                        onEdit()
                        onDismiss()
                    }
                )

                ActionItem(
                    icon = Icons.Default.Delete,
                    text = stringResource(R.string.action_delete_station),
                    textColor = MaterialTheme.colorScheme.error,
                    iconColor = MaterialTheme.colorScheme.error,
                    onClick = {
                        view.performHapticTap()
                        onDelete()
                        onDismiss()
                    }
                )
            }

            if (station.isFavorite) {
                val canMoveUp = favoritePosition > 1
                val canMoveDown = favoritePosition in 1 until totalFavorites
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
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

                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                            modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 28.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = if (totalFavorites > 0) "$favoritePosition / $totalFavorites" else favoritePosition.toString(),
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
        modifier = Modifier.size(44.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
            modifier = Modifier.size(26.dp)
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
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
            fontWeight = FontWeight.Medium,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun StationActionSheetPreview() {
    MaterialTheme(colorScheme = darkColorScheme()) {
        Surface(color = MaterialTheme.colorScheme.surface) {
            StationActionSheetContent(
                station = RadioStation(
                    id = 1,
                    name = "Rock FM",
                    url = "https://stream.skymedia.ee/live/RockFM",
                    countryCode = "EE",
                    category = "Sky",
                    isFavorite = true,
                    isUserStation = true
                ),
                favoritePosition = 2,
                totalFavorites = 12,
                showCloseButton = true,
                onDismiss = {},
                onToggleFavorite = {},
                onSetAlarm = {},
                onEdit = {},
                onDelete = {},
                onMoveUp = {},
                onMoveDown = {},
                onMoveToTop = {},
                onMoveToBottom = {},
                onEnterReorderMode = {}
            )
        }
    }
}
