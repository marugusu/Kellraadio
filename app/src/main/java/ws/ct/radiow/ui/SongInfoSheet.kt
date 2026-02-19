package ws.ct.radiow.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clipToBounds
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
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)))
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(stationColor.copy(alpha = 0.3f), Color.Black)
                        )
                    )
            )
        }

        SongInfoContent(
            artist = artist,
            title = title,
            stationName = stationName,
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
    info: SongAdditionalInfo,
    modifier: Modifier = Modifier,
    onImageStateChange: ((AsyncImagePainter.State) -> Unit)? = null
) {
    val scrollState = rememberScrollState()
    val stationColor = StationArtworkUtils.getStationColor(stationName)
    val stationInitials = StationArtworkUtils.getStationInitials(stationName)

    var localImageState by remember { mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty) }
    val isImageLoaded = localImageState is AsyncImagePainter.State.Success

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        Box(
            modifier = Modifier
                .size(280.dp)
                .shadow(elevation = 24.dp, shape = RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp))
                .background(if (isImageLoaded) Color.DarkGray else stationColor),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stationInitials,
                fontSize = 80.sp,
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

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.secondary
        )
        Text(
            text = artist,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 8.dp)
        )

        if (info.album != null || info.year != null || info.genre != null) {
            Spacer(modifier = Modifier.height(24.dp))
            FlowRow(
                horizontalArrangement = Arrangement.Center,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
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

        Spacer(modifier = Modifier.height(40.dp))

        if (!info.lyrics.isNullOrEmpty()) {
            Text(
                text = stringResource(R.string.info_lyrics),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.5f),
                letterSpacing = 2.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = info.lyrics,
                style = MaterialTheme.typography.bodyLarge,
                lineHeight = 32.sp,
                color = Color.White.copy(alpha = 0.9f),
                textAlign = TextAlign.Center
            )
        } else if (info.coverArtUrl.isNullOrEmpty() || localImageState is AsyncImagePainter.State.Error) {
            Text(
                text = stringResource(R.string.info_not_found),
                color = Color.Gray
            )
        }

        Spacer(modifier = Modifier.height(64.dp))
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
    val scrollState = rememberScrollState()
    val stationColor = StationArtworkUtils.getStationColor(stationName)
    val stationInitials = StationArtworkUtils.getStationInitials(stationName)

    var localImageState by remember { mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty) }
    val isImageLoaded = localImageState is AsyncImagePainter.State.Success

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(0.dp)
            .verticalScroll(scrollState)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(0.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(240.dp)
                    .shadow(elevation = 12.dp, shape = RoundedCornerShape(16.dp))
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isImageLoaded) Color.DarkGray else stationColor),
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
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        onState = { state -> localImageState = state }
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Top,
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.secondary
                )
                Text(
                    text = artist,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 8.dp)
                )

                if (info.album != null || info.year != null || info.genre != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.Start,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
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
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        if (!info.lyrics.isNullOrEmpty()) {
            Text(
                text = stringResource(R.string.info_lyrics),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.5f),
                letterSpacing = 2.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = info.lyrics,
                style = MaterialTheme.typography.bodyLarge,
                lineHeight = 32.sp,
                color = Color.White.copy(alpha = 0.9f),
                textAlign = TextAlign.Start
            )
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
fun InfoChip(text: String, icon: Boolean = false) {
    Surface(
        color = Color.White.copy(alpha = 0.1f),
        shape = RoundedCornerShape(50),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon) {
                Icon(imageVector = Icons.Default.Album, contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(text = text, style = MaterialTheme.typography.labelLarge, color = Color.White, fontWeight = FontWeight.Medium)
        }
    }
}