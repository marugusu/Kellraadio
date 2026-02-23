package ws.ct.radiow.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest
import ws.ct.radiow.R
import ws.ct.radiow.SongAdditionalInfo

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SongInfoSheet(
    artist: String,
    title: String,
    stationName: String,
    bitrate: String,      // UUS
    streamUrl: String,    // UUS
    info: SongAdditionalInfo,
    onDismiss: () -> Unit
) {
    val stationColor = StationArtworkUtils.getStationColor(stationName)

    var imageState by remember { mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty) }
    val isImageLoaded = imageState is AsyncImagePainter.State.Success
    val isBlurSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.90f)
    ) {
        // --- KIHT 1: TAUST ---
        if (isImageLoaded && isBlurSupported) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(info.coverArtUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().blur(radius = 30.dp)
            )
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.75f)))
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(stationColor.copy(alpha = 0.4f), Color.Black)
                        )
                    )
            )
        }

        // --- KIHT 2: SISU ---
        SongInfoContent(
            artist = artist,
            title = title,
            stationName = stationName,
            bitrate = bitrate,
            streamUrl = streamUrl,
            info = info,
            onImageStateChange = { state -> imageState = state }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SongInfoContent(
    artist: String,
    title: String,
    stationName: String,
    bitrate: String,
    streamUrl: String,
    info: SongAdditionalInfo,
    modifier: Modifier = Modifier,
    onImageStateChange: ((AsyncImagePainter.State) -> Unit)? = null
) {
    val scrollState = rememberScrollState()
    val stationColor = StationArtworkUtils.getStationColor(stationName)
    val stationInitials = StationArtworkUtils.getStationInitials(stationName)
    val context = LocalContext.current

    var localImageState by remember { mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty) }
    val isImageLoaded = localImageState is AsyncImagePainter.State.Success

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        // 1. PÄIS (Pilt + Tekst koondatult)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ALBUMI KAAS (50% ekraani laiusest)
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.5f)
                    .aspectRatio(1f)
                    .shadow(elevation = 12.dp, shape = RoundedCornerShape(12.dp))
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isImageLoaded) Color.DarkGray else stationColor),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stationInitials,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White.copy(alpha = 0.3f)
                )

                if (!info.coverArtUrl.isNullOrEmpty()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(info.coverArtUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Album Art",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        onState = { state ->
                            localImageState = state
                            onImageStateChange?.invoke(state)
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.width(20.dp))

            // PEALKIRI, ESITAJA JA METAANDMED
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp)
                )

                if (!info.album.isNullOrEmpty()) {
                    Text(
                        text = info.album,
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.8f),
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))

                // METAANDMED (CHIPS)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (!info.year.isNullOrEmpty()) InfoChip(text = info.year)
                    if (!info.genre.isNullOrEmpty()) InfoChip(text = info.genre)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 3. TEGEVUSNUPUD
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            ActionButton(
                icon = Icons.Default.MusicNote,
                label = "Spotify",
                color = Color(0xFF1DB954),
                onClick = { openSpotifySearch(context, "$artist $title") }
            )
            ActionButton(
                icon = Icons.Default.PlayCircleOutline,
                label = "YouTube",
                color = Color(0xFFFF0000),
                onClick = { openYoutubeSearch(context, "$artist $title") }
            )
            ActionButton(
                icon = Icons.Default.Share,
                label = stringResource(R.string.action_share),
                color = MaterialTheme.colorScheme.primary,
                onClick = { shareSongInfo(context, artist, title, stationName, bitrate, streamUrl, info) }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
        Spacer(modifier = Modifier.height(16.dp))

        // 4. LAULUSÕNAD
        if (!info.lyrics.isNullOrEmpty()) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.info_lyrics),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 2.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))
                
                Surface(
                    color = Color.White.copy(alpha = 0.05f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = info.lyrics,
                        style = MaterialTheme.typography.bodyLarge,
                        lineHeight = 28.sp,
                        color = Color.White.copy(alpha = 0.9f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        } else if (info.coverArtUrl.isNullOrEmpty() || localImageState is AsyncImagePainter.State.Error) {
            Text(
                text = stringResource(R.string.info_not_found),
                color = Color.Gray
            )
        }

        Spacer(modifier = Modifier.height(64.dp))
    }
}

@Composable
fun ActionButton(
    icon: ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Surface(
            color = color.copy(alpha = 0.2f),
            shape = CircleShape,
            modifier = Modifier.size(40.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.5f))
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(imageVector = icon, contentDescription = label, tint = color, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
    }
}

@Composable
fun InfoChip(text: String, icon: Boolean = false) {
    Surface(
        color = Color.White.copy(alpha = 0.1f),
        shape = RoundedCornerShape(50),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            if (icon) {
                Icon(imageVector = Icons.Default.Album, contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(text = text, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.8f), fontWeight = FontWeight.Medium)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SongInfoContentLandscape(
    artist: String,
    title: String,
    stationName: String,
    info: SongAdditionalInfo,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val stationInitials = StationArtworkUtils.getStationInitials(stationName)

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black)
                .padding(0.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // A) PILT (50% laiust)
            Box(
                modifier = Modifier
                    .weight(0.5f)
                    .aspectRatio(1f)
                    .shadow(elevation = 12.dp, shape = RoundedCornerShape(16.dp))
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stationInitials,
                    fontSize = 70.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White.copy(alpha = 0.25f)
                )

                if (!info.coverArtUrl.isNullOrEmpty()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(info.coverArtUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Album Art",
                        modifier = Modifier.matchParentSize(),
                        contentScale = ContentScale.Fit
                    )
                }
            }

            // B) INFO JA NUPUD (50% laiust)
            Column(
                modifier = Modifier
                    .weight(0.5f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.Top,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (info.album != null || info.year != null || info.genre != null) {
                    FlowRow(
                        horizontalArrangement = Arrangement.Center,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
                    ) {
                        if (!info.year.isNullOrEmpty()) InfoChip(text = info.year)
                        if (!info.genre.isNullOrEmpty()) {
                            Spacer(modifier = Modifier.width(8.dp))
                            InfoChip(text = info.genre)
                        }
                        if (!info.album.isNullOrEmpty()) {
                            Spacer(modifier = Modifier.width(8.dp))
                            InfoChip(text = info.album, icon = true)
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Uued nupud, joondatud keskele
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ActionButton(Icons.Default.MusicNote, "Spotify", Color(0xFF1DB954)) { openSpotifySearch(context, "$artist $title") }
                    Spacer(modifier = Modifier.width(24.dp))
                    ActionButton(Icons.Default.PlayCircleOutline, "YouTube", Color(0xFFFF0000)) { openYoutubeSearch(context, "$artist $title") }
                    Spacer(modifier = Modifier.width(24.dp))
                    ActionButton(Icons.Default.Share, stringResource(R.string.action_share), MaterialTheme.colorScheme.primary) { shareSongInfo(context, artist, title, stationName, "", "", info) }
                }
            }
        }
    }
}
