package app.radiorecalarm.ui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import app.radiorecalarm.R
import app.radiorecalarm.SongAdditionalInfo

@Composable
fun SongInfoTeaser(
    info: SongAdditionalInfo?,
    isLoading: Boolean,
    artist: String,
    title: String,
    stationName: String,
    onClick: () -> Unit,
    shape: Shape = RectangleShape,
    modifier: Modifier = Modifier,
    backgroundBrush: Brush? = null
) {
    val infoParts = if (info != null) {
        listOfNotNull(info.album, info.year, info.genre).filter { it.isNotEmpty() }
    } else emptyList()

    val displayText = if (infoParts.isNotEmpty()) {
        infoParts.joinToString(" • ")
    } else {
        "$artist - $title"
    }

    val stationColor = StationArtworkUtils.getStationColor(stationName)
    val stationInitials = StationArtworkUtils.getStationInitials(stationName)
    val bgBrush = backgroundBrush ?: SolidColor(Color.Black)

    Surface(
        modifier = modifier
            .height(64.dp)
            .clickable(enabled = info != null || isLoading, onClick = onClick),
        color = Color.Transparent,
        contentColor = Color.White,
        shape = shape
    ) {
        Row(
            modifier = Modifier
                .background(bgBrush)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(stationColor),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stationInitials,
                    color = Color.White.copy(alpha = 0.5f),
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp
                )

                if (info?.coverArtUrl != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(info.coverArtUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = if (info == null && isLoading) stringResource(R.string.info_searching) else displayText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = if (isLoading) stringResource(R.string.info_searching) else stringResource(R.string.info_available),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isLoading) Color.Gray else MaterialTheme.colorScheme.primary,
                    maxLines = 1
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                    )
                } else {
                    val icon = if (info?.lyrics != null) {
                        painterResource(R.drawable.ic_lyrics)
                    } else {
                        rememberVectorPainter(Icons.Default.Image)
                    }
                    Icon(
                        painter = icon,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

@Composable
fun rememberVectorPainter(image: androidx.compose.ui.graphics.vector.ImageVector) =
    androidx.compose.ui.graphics.vector.rememberVectorPainter(image)
