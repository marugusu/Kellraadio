package app.radiorecalarm.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.radiorecalarm.R
import app.radiorecalarm.SongAdditionalInfo
import app.radiorecalarm.ui.theme.KellraadioTheme
import coil.compose.AsyncImage
import coil.request.ImageRequest

/**
 * Compact teaser bar shown above navigation or under player controls when track info is available.
 */
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
    backgroundBrush: Brush? = null,
    stationIconUrl: String = ""
) {
    val primaryText = if (info == null && isLoading) {
        stringResource(R.string.info_searching)
    } else {
        buildTeaserPrimaryText(info = info, artist = artist, title = title)
    }
    val secondaryText = if (info == null && isLoading) {
        stationName.trim().takeIf { it.isNotEmpty() }
    } else {
        buildTeaserSecondaryText(info = info, artist = artist, title = title, stationName = stationName)
    }
    val hasLyrics = !info?.lyrics.isNullOrBlank()
    val stationColor = StationArtworkUtils.getStationColor(stationName)
    val bgBrush = backgroundBrush ?: SolidColor(MaterialTheme.colorScheme.surface)
    val borderModifier = if (shape != RectangleShape) {
        Modifier.border(1.dp, MaterialTheme.colorScheme.outline, shape)
    } else {
        Modifier
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(shape)
            .then(borderModifier)
            .clickable(enabled = info != null || isLoading, onClick = onClick),
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = shape
    ) {
        Row(
            modifier = Modifier
                .background(bgBrush)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                StationLogoBadge(
                    stationName = stationName,
                    iconUrl = stationIconUrl,
                    size = 46.dp,
                    cornerRadius = 10.dp,
                    borderWidth = 0.dp,
                    borderColor = Color.Transparent,
                    fallbackBackground = stationColor,
                    fallbackTextColor = Color.White.copy(alpha = 0.55f),
                    fallbackFontSize = 14.sp
                )

                if (!info?.coverArtUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(info?.coverArtUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = stringResource(R.string.cd_album_art),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = primaryText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.SemiBold
                )
                if (!secondaryText.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = secondaryText,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                when {
                    isLoading -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    hasLyrics -> {
                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_lyrics),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = stringResource(R.string.info_lyrics_badge),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                if (info != null) {
                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun rememberVectorPainter(image: androidx.compose.ui.graphics.vector.ImageVector) =
    androidx.compose.ui.graphics.vector.rememberVectorPainter(image)

@Preview(showBackground = true, backgroundColor = 0xFF0A0C10)
@Composable
private fun SongInfoTeaserWithLyricsPreview() {
    KellraadioTheme {
        SongInfoTeaser(
            info = SongAdditionalInfo(
                album = "Christmas Christmas",
                year = "2017",
                genre = "Rock",
                durationMs = 219_000L,
                lyrics = "It lifts me up this time of year"
            ),
            isLoading = false,
            artist = "CHEAP TRICK",
            title = "Merry Christmas Darlings",
            stationName = "Retro FM Jõulud",
            onClick = {},
            shape = RoundedCornerShape(8.dp)
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0C10)
@Composable
private fun SongInfoTeaserSearchingPreview() {
    KellraadioTheme {
        SongInfoTeaser(
            info = SongAdditionalInfo(
                album = "Üle ilma",
                year = "2014",
                genre = "Folk"
            ),
            isLoading = true,
            artist = "Curly Strings",
            title = "Kauges külas",
            stationName = "Raadio Elmar",
            onClick = {}
        )
    }
}
