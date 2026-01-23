package ee.minu.kellraadio.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest
import ee.minu.kellraadio.R
import ee.minu.kellraadio.SongAdditionalInfo

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SongInfoSheet(
    artist: String,
    title: String,
    stationName: String,
    info: SongAdditionalInfo,
    onDismiss: () -> Unit
) {
    val scrollState = rememberScrollState()
    val stationColor = StationArtworkUtils.getStationColor(stationName)
    val stationInitials = StationArtworkUtils.getStationInitials(stationName)

    // Jälgime pildi laadimise olekut
    var imageState by remember { mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty) }

    // Kas pilt on edukalt laetud?
    val isImageLoaded = imageState is AsyncImagePainter.State.Success
    // Kas telefon on piisavalt uus (Android 12+), et teha bluri?
    val isBlurSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.90f)
    ) {
        // --- KIHT 1: TAUST ---
        // Kui pilt on olemas JA telefon toetab, näita udu.
        // Muul juhul näita gradienti.
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
            // Tume loor udu peal
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)))
        } else {
            // Gradient taust (kui pilt laeb, on katki või vana telefon)
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

        // --- KIHT 2: SISU ---
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // 1. ALBUMI KAANEPILT (VÕI LOGO)
            Box(
                modifier = Modifier
                    .size(280.dp)
                    .shadow(elevation = 24.dp, shape = RoundedCornerShape(16.dp))
                    .clip(RoundedCornerShape(16.dp))
                    // Kui pilt on laetud, on taust tumehall (et pilt oleks puhas).
                    // Kui pilt laeb või puudub, on taust jaama värvi (logo jaoks).
                    .background(if (isImageLoaded) Color.DarkGray else stationColor),
                contentAlignment = Alignment.Center
            ) {
                // A) LOGO (Alati all, näha siis kui pilti pole)
                Text(
                    text = stationInitials,
                    fontSize = 80.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White.copy(alpha = 0.3f)
                )

                // B) PILT
                // Paneme selle ALATI siia, et ta hakkaks laadima.
                // Kui ta laeb ära, katab ta logo kinni ja uuendab 'imageState'-i,
                // mis omakorda lülitab sisse tausta bluri.
                if (!info.coverArtUrl.isNullOrEmpty()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(info.coverArtUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Album Art",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        // SIIN ON VÕTI: Uuendame olekut, kui midagi juhtub
                        onState = { state -> imageState = state }
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // 2. INFO
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

            // LISAINFO MÄRGID
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

            // 3. LAULUSÕNAD
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
            } else if (info.coverArtUrl.isNullOrEmpty()) {
                // Kui polnud URL-i (ehk me isegi ei proovinud laadida)
                Text(
                    text = stringResource(R.string.info_not_found),
                    color = Color.Gray
                )
            } else if (imageState is AsyncImagePainter.State.Error) {
                // Kui URL oli, aga laadimine ebaõnnestus
                Text(
                    text = stringResource(R.string.info_not_found),
                    color = Color.Gray
                )
            }

            Spacer(modifier = Modifier.height(64.dp))
        }
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