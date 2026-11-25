package app.radiorecalarm.ui

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.ZoomOutMap
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.radiorecalarm.R
import app.radiorecalarm.SongAdditionalInfo
import app.radiorecalarm.ui.theme.KellraadioTheme
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest

private val SpotifyBrandColor = Color(0xFF1DB954)
private val YouTubeBrandColor = Color(0xFFFF4D4D)

/**
 * Shared ModalBottomSheet host with a seamless drag handle embedded inside the sheet backdrop.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongInfoModalSheet(
    artist: String,
    title: String,
    stationName: String,
    stationIconUrl: String = "",
    bitrate: String,
    streamUrl: String,
    info: SongAdditionalInfo,
    isLoadingLyrics: Boolean = false,
    showSpotifySaveButton: Boolean = false,
    isSpotifyTrackSaved: Boolean = false,
    isSpotifySaveInProgress: Boolean = false,
    onSaveToSpotify: () -> Unit = {},
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        SongInfoSheet(
            artist = artist,
            title = title,
            stationName = stationName,
            stationIconUrl = stationIconUrl,
            bitrate = bitrate,
            streamUrl = streamUrl,
            info = info,
            isLoadingLyrics = isLoadingLyrics,
            showSpotifySaveButton = showSpotifySaveButton,
            isSpotifyTrackSaved = isSpotifyTrackSaved,
            isSpotifySaveInProgress = isSpotifySaveInProgress,
            onSaveToSpotify = onSaveToSpotify,
            onDismiss = onDismiss
        )
    }
}

@Composable
fun SongInfoSheet(
    artist: String,
    title: String,
    stationName: String,
    stationIconUrl: String = "",
    bitrate: String,
    streamUrl: String,
    info: SongAdditionalInfo,
    isLoadingLyrics: Boolean = false,
    showSpotifySaveButton: Boolean = false,
    isSpotifyTrackSaved: Boolean = false,
    isSpotifySaveInProgress: Boolean = false,
    onSaveToSpotify: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val config = LocalConfiguration.current
    val maxSheetHeight = (config.screenHeightDp * 0.90f).dp
    val isWideSheet = config.orientation == Configuration.ORIENTATION_LANDSCAPE &&
        config.screenWidthDp >= 520

    val stationColor = StationArtworkUtils.getStationColor(stationName)
    var imageState by remember(info.coverArtUrl) {
        mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty)
    }
    var showArtworkLightbox by remember { mutableStateOf(false) }

    val isImageLoaded = imageState is AsyncImagePainter.State.Success
    val isBlurSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val surfaceColor = MaterialTheme.colorScheme.surface

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .heightIn(max = maxSheetHeight)
            .background(surfaceColor)
    ) {
        // Full-bleed ambient artwork/station glow fading seamlessly into TitaniumSurface
        SongInfoAmbientBackdrop(
            coverArtUrl = info.coverArtUrl,
            isImageLoaded = isImageLoaded,
            isBlurSupported = isBlurSupported,
            stationColor = stationColor,
            surfaceColor = surfaceColor,
            modifier = Modifier.matchParentSize()
        )

        // Content layer
        if (isWideSheet) {
            SongInfoLandscapeModalContent(
                artist = artist,
                title = title,
                stationName = stationName,
                stationIconUrl = stationIconUrl,
                bitrate = bitrate,
                streamUrl = streamUrl,
                info = info,
                isLoadingLyrics = isLoadingLyrics,
                showSpotifySaveButton = showSpotifySaveButton,
                isSpotifyTrackSaved = isSpotifyTrackSaved,
                isSpotifySaveInProgress = isSpotifySaveInProgress,
                onSaveToSpotify = onSaveToSpotify,
                onImageStateChange = { imageState = it },
                onArtworkClick = { if (isImageLoaded) showArtworkLightbox = true }
            )
        } else {
            SongInfoContent(
                artist = artist,
                title = title,
                stationName = stationName,
                stationIconUrl = stationIconUrl,
                bitrate = bitrate,
                streamUrl = streamUrl,
                info = info,
                isLoadingLyrics = isLoadingLyrics,
                showSpotifySaveButton = showSpotifySaveButton,
                isSpotifyTrackSaved = isSpotifyTrackSaved,
                isSpotifySaveInProgress = isSpotifySaveInProgress,
                onSaveToSpotify = onSaveToSpotify,
                onImageStateChange = { imageState = it },
                onArtworkClick = { if (isImageLoaded) showArtworkLightbox = true }
            )
        }

        // Seamless top drag handle + close button integrated inside the backdrop
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp, start = 12.dp, end = 8.dp)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 4.dp)
                    .width(38.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.28f))
            )

            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.action_close),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }

    if (showArtworkLightbox && !info.coverArtUrl.isNullOrBlank()) {
        ArtworkLightboxDialog(
            coverArtUrl = info.coverArtUrl,
            title = title,
            artist = artist,
            onDismiss = { showArtworkLightbox = false }
        )
    }
}

@Composable
private fun SongInfoAmbientBackdrop(
    coverArtUrl: String?,
    isImageLoaded: Boolean,
    isBlurSupported: Boolean,
    stationColor: Color,
    surfaceColor: Color,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        if (isImageLoaded && isBlurSupported && !coverArtUrl.isNullOrBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(coverArtUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(radius = 48.dp)
                    .alpha(0.45f)
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.0f to Color.Black.copy(alpha = 0.28f),
                            0.42f to surfaceColor.copy(alpha = 0.82f),
                            0.78f to surfaceColor
                        )
                    )
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.0f to stationColor.copy(alpha = 0.28f),
                            0.52f to surfaceColor.copy(alpha = 0.94f),
                            1.0f to surfaceColor
                        )
                    )
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SongInfoContent(
    artist: String,
    title: String,
    stationName: String,
    stationIconUrl: String = "",
    bitrate: String,
    streamUrl: String,
    info: SongAdditionalInfo,
    isLoadingLyrics: Boolean = false,
    showSpotifySaveButton: Boolean = false,
    isSpotifyTrackSaved: Boolean = false,
    isSpotifySaveInProgress: Boolean = false,
    onSaveToSpotify: () -> Unit = {},
    modifier: Modifier = Modifier,
    onImageStateChange: ((AsyncImagePainter.State) -> Unit)? = null,
    onArtworkClick: (() -> Unit)? = null
) {
    val config = LocalConfiguration.current
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val stationColor = StationArtworkUtils.getStationColor(stationName)

    var localImageState by remember(info.coverArtUrl) {
        mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty)
    }
    val isImageLoaded = localImageState is AsyncImagePainter.State.Success
    val heroArtworkSize = min(232.dp, (config.screenWidthDp * 0.56f).dp)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(horizontal = 24.dp)
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(34.dp))

        // 1. HERO COVER ART
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(heroArtworkSize + 28.dp)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                (if (isImageLoaded) MaterialTheme.colorScheme.primary else stationColor)
                                    .copy(alpha = 0.16f),
                                Color.Transparent
                            )
                        )
                    )
            )

            HeroArtworkCard(
                coverArtUrl = info.coverArtUrl,
                stationName = stationName,
                stationIconUrl = stationIconUrl,
                size = heroArtworkSize,
                cornerRadius = 16.dp,
                isImageLoaded = isImageLoaded,
                onState = { state ->
                    localImageState = state
                    onImageStateChange?.invoke(state)
                },
                onClick = if (isImageLoaded) onArtworkClick else null
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 2. CENTERED TRACK IDENTITY
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )

        if (artist.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = artist,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (!info.album.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = info.album,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 3. METADATA & SOURCE PILLS
        MetadataAndSourcePills(
            info = info,
            stationName = stationName,
            bitrate = bitrate,
            centered = true
        )

        Spacer(modifier = Modifier.height(20.dp))

        // 4. ACTION BAR
        SongActionRow(
            artist = artist,
            title = title,
            stationName = stationName,
            bitrate = bitrate,
            streamUrl = streamUrl,
            info = info,
            showSpotifySaveButton = showSpotifySaveButton,
            isSpotifyTrackSaved = isSpotifyTrackSaved,
            isSpotifySaveInProgress = isSpotifySaveInProgress,
            onSaveToSpotify = onSaveToSpotify,
            context = context
        )

        Spacer(modifier = Modifier.height(18.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(modifier = Modifier.height(16.dp))

        // 5. LYRICS / STATUS SECTION
        LyricsStatusSection(
            lyrics = info.lyrics,
            isLoadingLyrics = isLoadingLyrics,
            artist = artist,
            title = title,
            onCopyLyrics = { lyricsText -> copyLyricsWithFeedback(context, lyricsText) },
            onSearchLyricsOnline = { openLyricsWebSearch(context, artist, title) }
        )

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun SongInfoLandscapeModalContent(
    artist: String,
    title: String,
    stationName: String,
    stationIconUrl: String,
    bitrate: String,
    streamUrl: String,
    info: SongAdditionalInfo,
    isLoadingLyrics: Boolean,
    showSpotifySaveButton: Boolean,
    isSpotifyTrackSaved: Boolean,
    isSpotifySaveInProgress: Boolean,
    onSaveToSpotify: () -> Unit,
    onImageStateChange: (AsyncImagePainter.State) -> Unit,
    onArtworkClick: () -> Unit
) {
    val context = LocalContext.current
    val hasLyricsSection = !info.lyrics.isNullOrBlank() || isLoadingLyrics

    var localImageState by remember(info.coverArtUrl) {
        mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty)
    }
    val isImageLoaded = localImageState is AsyncImagePainter.State.Success

    if (!hasLyricsSection) {
        // Compact balanced two-column card when no lyrics are present
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, top = 36.dp, bottom = 24.dp)
                .navigationBarsPadding(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            HeroArtworkCard(
                coverArtUrl = info.coverArtUrl,
                stationName = stationName,
                stationIconUrl = stationIconUrl,
                size = 168.dp,
                cornerRadius = 14.dp,
                isImageLoaded = isImageLoaded,
                onState = { state ->
                    localImageState = state
                    onImageStateChange(state)
                },
                onClick = if (isImageLoaded) onArtworkClick else null
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                TrackIdentityBlock(
                    title = title,
                    artist = artist,
                    album = info.album,
                    centered = true
                )
                Spacer(modifier = Modifier.height(10.dp))
                MetadataAndSourcePills(
                    info = info,
                    stationName = stationName,
                    bitrate = bitrate,
                    centered = true
                )
                Spacer(modifier = Modifier.height(14.dp))
                SongActionRow(
                    artist = artist,
                    title = title,
                    stationName = stationName,
                    bitrate = bitrate,
                    streamUrl = streamUrl,
                    info = info,
                    showSpotifySaveButton = showSpotifySaveButton,
                    isSpotifyTrackSaved = isSpotifyTrackSaved,
                    isSpotifySaveInProgress = isSpotifySaveInProgress,
                    onSaveToSpotify = onSaveToSpotify,
                    context = context,
                    compact = true
                )
                Spacer(modifier = Modifier.height(12.dp))
                LyricsNotFoundRow(
                    onSearchLyricsOnline = { openLyricsWebSearch(context, artist, title) }
                )
            }
        }
    } else {
        // Two-pane split when lyrics or loading state exist
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, top = 36.dp, bottom = 20.dp)
                .navigationBarsPadding(),
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .weight(0.46f)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                HeroArtworkCard(
                    coverArtUrl = info.coverArtUrl,
                    stationName = stationName,
                    stationIconUrl = stationIconUrl,
                    size = 136.dp,
                    cornerRadius = 14.dp,
                    isImageLoaded = isImageLoaded,
                    onState = { state ->
                        localImageState = state
                        onImageStateChange(state)
                    },
                    onClick = if (isImageLoaded) onArtworkClick else null
                )
                Spacer(modifier = Modifier.height(12.dp))
                TrackIdentityBlock(
                    title = title,
                    artist = artist,
                    album = info.album,
                    centered = true
                )
                Spacer(modifier = Modifier.height(10.dp))
                MetadataAndSourcePills(
                    info = info,
                    stationName = stationName,
                    bitrate = bitrate,
                    centered = true
                )
                Spacer(modifier = Modifier.height(14.dp))
                SongActionRow(
                    artist = artist,
                    title = title,
                    stationName = stationName,
                    bitrate = bitrate,
                    streamUrl = streamUrl,
                    info = info,
                    showSpotifySaveButton = showSpotifySaveButton,
                    isSpotifyTrackSaved = isSpotifyTrackSaved,
                    isSpotifySaveInProgress = isSpotifySaveInProgress,
                    onSaveToSpotify = onSaveToSpotify,
                    context = context,
                    compact = true
                )
            }

            Column(
                modifier = Modifier
                    .weight(0.54f)
                    .verticalScroll(rememberScrollState())
            ) {
                LyricsStatusSection(
                    lyrics = info.lyrics,
                    isLoadingLyrics = isLoadingLyrics,
                    artist = artist,
                    title = title,
                    onCopyLyrics = { lyricsText -> copyLyricsWithFeedback(context, lyricsText) },
                    onSearchLyricsOnline = { openLyricsWebSearch(context, artist, title) }
                )
            }
        }
    }
}

@Composable
private fun HeroArtworkCard(
    coverArtUrl: String?,
    stationName: String,
    stationIconUrl: String,
    size: Dp,
    cornerRadius: Dp,
    isImageLoaded: Boolean,
    onState: (AsyncImagePainter.State) -> Unit,
    onClick: (() -> Unit)?
) {
    val stationColor = StationArtworkUtils.getStationColor(stationName)
    val shape = RoundedCornerShape(cornerRadius)
    val clickModifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier

    Box(
        modifier = Modifier
            .size(size)
            .shadow(elevation = 18.dp, shape = shape)
            .clip(shape)
            .background(if (isImageLoaded) MaterialTheme.colorScheme.surfaceVariant else stationColor)
            .border(1.dp, Color.White.copy(alpha = 0.16f), shape)
            .then(clickModifier),
        contentAlignment = Alignment.Center
    ) {
        StationLogoBadge(
            stationName = stationName,
            iconUrl = stationIconUrl,
            innerPadding = 18.dp,
            cornerRadius = cornerRadius,
            borderWidth = 0.dp,
            borderColor = Color.Transparent,
            fallbackBackground = stationColor,
            fallbackTextColor = Color.White.copy(alpha = 0.35f),
            fallbackFontSize = 38.sp,
            modifier = Modifier.fillMaxSize()
        )

        if (!coverArtUrl.isNullOrBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(coverArtUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = stringResource(R.string.cd_album_art),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                onState = onState
            )
        }

        if (isImageLoaded && onClick != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f))
                    .border(1.dp, Color.White.copy(alpha = 0.22f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.ZoomOutMap,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
private fun TrackIdentityBlock(
    title: String,
    artist: String,
    album: String?,
    centered: Boolean
) {
    val alignment = if (centered) TextAlign.Center else TextAlign.Start
    Column(
        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = alignment,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (artist.isNotBlank()) {
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = artist,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                textAlign = alignment,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (!album.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = album,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
                textAlign = alignment,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MetadataAndSourcePills(
    info: SongAdditionalInfo,
    stationName: String,
    bitrate: String,
    centered: Boolean
) {
    val metaPills = remember(info) { buildTrackMetadataPills(info) }
    val sourceLabel = remember(stationName, bitrate) { formatSourceLabel(stationName, bitrate) }
    if (metaPills.isEmpty() && sourceLabel == null) return

    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(
            8.dp,
            if (centered) Alignment.CenterHorizontally else Alignment.Start
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        metaPills.forEach { text ->
            InfoChip(text = text)
        }
        if (sourceLabel != null) {
            InfoChip(
                text = sourceLabel,
                leadingIcon = Icons.Rounded.Radio
            )
        }
    }
}

@Composable
private fun SongActionRow(
    artist: String,
    title: String,
    stationName: String,
    bitrate: String,
    streamUrl: String,
    info: SongAdditionalInfo,
    showSpotifySaveButton: Boolean,
    isSpotifyTrackSaved: Boolean,
    isSpotifySaveInProgress: Boolean,
    onSaveToSpotify: () -> Unit,
    context: Context,
    compact: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ActionButton(
            icon = Icons.Default.MusicNote,
            label = "Spotify",
            color = SpotifyBrandColor,
            compact = compact,
            modifier = Modifier.weight(1f),
            onClick = { openSpotifyTrack(context, info.spotifyTrackId, "$artist $title") }
        )
        if (showSpotifySaveButton) {
            ActionButton(
                icon = if (isSpotifyTrackSaved) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                label = if (isSpotifyTrackSaved) {
                    stringResource(R.string.spotify_saved_action)
                } else {
                    stringResource(R.string.spotify_save_action)
                },
                color = SpotifyBrandColor,
                isHighlighted = isSpotifyTrackSaved,
                isLoading = isSpotifySaveInProgress,
                compact = compact,
                modifier = Modifier.weight(1f),
                onClick = onSaveToSpotify
            )
        }
        ActionButton(
            icon = Icons.Default.PlayCircleOutline,
            label = "YouTube",
            color = YouTubeBrandColor,
            compact = compact,
            modifier = Modifier.weight(1f),
            onClick = { openYoutubeSearch(context, "$artist $title") }
        )
        ActionButton(
            icon = Icons.Default.Share,
            label = stringResource(R.string.action_share),
            color = MaterialTheme.colorScheme.primary,
            compact = compact,
            modifier = Modifier.weight(1f),
            onClick = { shareSongInfo(context, artist, title, stationName, bitrate, streamUrl, info) }
        )
    }
}

@Composable
private fun LyricsStatusSection(
    lyrics: String?,
    isLoadingLyrics: Boolean,
    artist: String,
    title: String,
    onCopyLyrics: (String) -> Unit,
    onSearchLyricsOnline: () -> Unit
) {
    when {
        !lyrics.isNullOrBlank() -> {
            Column(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.info_lyrics),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 2.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Surface(
                        onClick = { onCopyLyrics(lyrics) },
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .heightIn(min = 36.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ContentCopy,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = stringResource(R.string.info_copy_lyrics),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = lyrics.trim(),
                        style = MaterialTheme.typography.bodyLarge,
                        lineHeight = 28.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.92f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 20.dp)
                    )
                }
            }
        }

        isLoadingLyrics -> {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.info_searching_lyrics),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        else -> {
            LyricsNotFoundRow(onSearchLyricsOnline = onSearchLyricsOnline)
        }
    }
}

@Composable
private fun LyricsNotFoundRow(
    onSearchLyricsOnline: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_lyrics),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = stringResource(R.string.info_lyrics_not_found),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Surface(
                onClick = onSearchLyricsOnline,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.38f)),
                modifier = Modifier.heightIn(min = 36.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = stringResource(R.string.info_search_lyrics_online),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun ArtworkLightboxDialog(
    coverArtUrl: String,
    title: String,
    artist: String,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.92f))
                .clickable(onClick = onDismiss)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.action_close),
                    tint = Color.White
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(coverArtUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = stringResource(R.string.cd_album_art),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .aspectRatio(1f, matchHeightConstraintsFirst = false)
                        .clip(RoundedCornerShape(16.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(16.dp))
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (artist.isNotBlank()) {
                    Text(
                        text = artist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun ActionButton(
    icon: ImageVector,
    label: String,
    color: Color,
    isLoading: Boolean = false,
    isHighlighted: Boolean = false,
    compact: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val containerAlpha = if (isHighlighted) 0.24f else 0.12f
    val borderAlpha = if (isHighlighted) 0.65f else 0.34f
    val minHeight = if (compact) 48.dp else 54.dp

    Surface(
        onClick = { if (!isLoading) onClick() },
        enabled = !isLoading,
        color = color.copy(alpha = containerAlpha),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, color.copy(alpha = borderAlpha)),
        modifier = modifier.heightIn(min = minHeight)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    color = color,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(18.dp)
                )
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = color,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (isHighlighted) color else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun InfoChip(
    text: String,
    icon: Boolean = false,
    leadingIcon: ImageVector? = null
) {
    val resolvedIcon = leadingIcon ?: if (icon) Icons.Rounded.OpenInFull else null
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (resolvedIcon != null) {
                Icon(
                    imageVector = resolvedIcon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Companion track-info panel embedded below PlayerControls on tall landscape / tablet screens.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SongInfoContentLandscape(
    artist: String,
    title: String,
    stationName: String,
    stationIconUrl: String = "",
    bitrate: String = "",
    streamUrl: String = "",
    info: SongAdditionalInfo,
    isLoadingLyrics: Boolean = false,
    showSpotifySaveButton: Boolean = false,
    isSpotifyTrackSaved: Boolean = false,
    isSpotifySaveInProgress: Boolean = false,
    onSaveToSpotify: () -> Unit = {},
    onOpenFullSheet: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var localImageState by remember(info.coverArtUrl) {
        mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty)
    }
    var showArtworkLightbox by remember { mutableStateOf(false) }
    val isImageLoaded = localImageState is AsyncImagePainter.State.Success

    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left column: artwork + metadata/source pills
        Column(
            modifier = Modifier
                .weight(0.40f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            HeroArtworkCard(
                coverArtUrl = info.coverArtUrl,
                stationName = stationName,
                stationIconUrl = stationIconUrl,
                size = 148.dp,
                cornerRadius = 12.dp,
                isImageLoaded = isImageLoaded,
                onState = { localImageState = it },
                onClick = if (isImageLoaded) ({ showArtworkLightbox = true }) else null
            )

            if (!info.album.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = info.album,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            MetadataAndSourcePills(
                info = info,
                stationName = stationName,
                bitrate = bitrate,
                centered = true
            )
        }

        // Right column: lyrics preview / status + full-view trigger + action buttons
        Column(
            modifier = Modifier
                .weight(0.60f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.info_lyrics),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 1.5.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Surface(
                            onClick = onOpenFullSheet,
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
                                    imageVector = Icons.Rounded.OpenInFull,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = stringResource(R.string.info_open_full),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    when {
                        !info.lyrics.isNullOrBlank() -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .verticalScroll(rememberScrollState()),
                                contentAlignment = Alignment.TopCenter
                            ) {
                                Text(
                                    text = info.lyrics.trim(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    lineHeight = 22.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                        isLoadingLyrics -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = stringResource(R.string.info_searching_lyrics),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        else -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                LyricsNotFoundRow(
                                    onSearchLyricsOnline = { openLyricsWebSearch(context, artist, title) }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            SongActionRow(
                artist = artist,
                title = title,
                stationName = stationName,
                bitrate = bitrate,
                streamUrl = streamUrl,
                info = info,
                showSpotifySaveButton = showSpotifySaveButton,
                isSpotifyTrackSaved = isSpotifyTrackSaved,
                isSpotifySaveInProgress = isSpotifySaveInProgress,
                onSaveToSpotify = onSaveToSpotify,
                context = context,
                compact = true
            )
        }
    }

    if (showArtworkLightbox && !info.coverArtUrl.isNullOrBlank()) {
        ArtworkLightboxDialog(
            coverArtUrl = info.coverArtUrl,
            title = title,
            artist = artist,
            onDismiss = { showArtworkLightbox = false }
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF12151E)
@Composable
private fun SongInfoContentPreview() {
    KellraadioTheme {
        SongInfoContent(
            artist = "CHEAP TRICK",
            title = "Merry Christmas Darlings",
            stationName = "Retro FM Jõulud",
            bitrate = "128 kbps",
            streamUrl = "https://example.com/stream",
            info = SongAdditionalInfo(
                album = "Christmas Christmas",
                year = "2017",
                genre = "Rock",
                trackNumber = 3,
                durationMs = 219_000L,
                lyrics = "It lifts me up this time of year\nWhen hearts are full of yuletide cheer\nSo Merry Christmas darlings",
                spotifyTrackId = "12345"
            ),
            showSpotifySaveButton = true,
            isSpotifyTrackSaved = false
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF12151E)
@Composable
private fun SongInfoContentNoLyricsPreview() {
    KellraadioTheme {
        SongInfoContent(
            artist = "Anne Veski",
            title = "Viimne pilet",
            stationName = "Elmari Kuld",
            bitrate = "128 kbps",
            streamUrl = "https://example.com/stream",
            info = SongAdditionalInfo(
                album = "Remixid 2025 (Remix) - EP",
                year = "2025",
                genre = "Pop",
                durationMs = 198_000L,
                spotifyTrackId = "12345"
            ),
            showSpotifySaveButton = true,
            isSpotifyTrackSaved = true
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF12151E, widthDp = 640, heightDp = 340)
@Composable
private fun SongInfoContentLandscapePreview() {
    KellraadioTheme {
        SongInfoContentLandscape(
            artist = "Curly Strings",
            title = "Kauges külas",
            stationName = "Raadio Elmar",
            bitrate = "128 kbps",
            info = SongAdditionalInfo(
                album = "Üle ilma",
                year = "2014",
                genre = "Folk",
                durationMs = 230_000L,
                lyrics = "Kauges külas, vanaema juures\nelas kord üks väike poiss...",
                spotifyTrackId = "12345"
            ),
            showSpotifySaveButton = true,
            isSpotifyTrackSaved = true
        )
    }
}
