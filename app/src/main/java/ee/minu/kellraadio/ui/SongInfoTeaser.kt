package ee.minu.kellraadio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MusicNote
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
import ee.minu.kellraadio.R
import ee.minu.kellraadio.SongAdditionalInfo

@Composable
fun SongInfoTeaser(
    info: SongAdditionalInfo,
    artist: String,
    title: String,
    stationName: String,
    onClick: () -> Unit,
    shape: Shape = RectangleShape,
    modifier: Modifier = Modifier,
    backgroundBrush: Brush? = null
) {
    val infoParts = listOfNotNull(info.album, info.year, info.genre).filter { it.isNotEmpty() }
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
            .clickable(onClick = onClick),
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
            // 1. VÄIKE PILT VÕI LOGO (KIHILINE)
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(stationColor), // ALATI jaama värv all
                contentAlignment = Alignment.Center
            ) {
                // A) LOGO (Alati olemas)
                Text(
                    text = stationInitials,
                    color = Color.White.copy(alpha = 0.5f),
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp
                )

                // B) PILT (Kui on olemas, tuleb logo peale)
                if (!info.coverArtUrl.isNullOrEmpty()) {
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

            // 2. TEKST
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = displayText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = stringResource(R.string.info_available),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // 3. IKOON
            val icon = if (!info.lyrics.isNullOrEmpty()) {
                painterResource(R.drawable.ic_lyrics)
            } else {
                rememberVectorPainter(Icons.Default.Image)
            }

            Icon(
                painter = icon,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
fun rememberVectorPainter(image: androidx.compose.ui.graphics.vector.ImageVector) =
    androidx.compose.ui.graphics.vector.rememberVectorPainter(image)