package ee.minu.kellraadio.ui

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
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import ee.minu.kellraadio.R
import ee.minu.kellraadio.SongAdditionalInfo

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
    val hasUrl = !info.coverArtUrl.isNullOrEmpty()
    val painter = rememberAsyncImagePainter(
        model = ImageRequest.Builder(LocalContext.current)
            .data(info.coverArtUrl)
            .crossfade(true)
            .build()
    )
    val isImageLoaded = painter.state is coil.compose.AsyncImagePainter.State.Success
    val isBlurSupported = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.90f)
    ) {
        // --- KIHT 1: TAUST ---
        // Android 12 (S) ja uuemad toetavad riistvaralist blur-i.
        // Vanematel telefonidel on parem näidata gradienti kui teravat pilti (mis segab teksti).
        val isBlurSupported = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S

        if (isImageLoaded && isBlurSupported) {
            // UUS TELEFON: Näita udust pilti
            AsyncImage(
                model = info.coverArtUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().blur(radius = 30.dp)
            )
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)))
        } else {
            // VANA TELEFON (või pilt puudub): Näita ilusat gradienti
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
            Spacer(modifier = Modifier.height(32.dp)) // Rohkem ruumi üles

            // 1. PILDIPESA (Stack)
            // Siin on trikk: Me laome asjad üksteise peale.
            Box(
                modifier = Modifier
                    .size(280.dp)
                    .shadow(elevation = 24.dp, shape = RoundedCornerShape(16.dp))
                    .clip(RoundedCornerShape(16.dp))
                    .background(stationColor), // Taustaks jaama värv
                contentAlignment = Alignment.Center
            ) {
                // A) LOGO (Alati all)
                Text(
                    text = stationInitials,
                    fontSize = 80.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White.copy(alpha = 0.3f)
                )

                // B) PILT (Kui on URL, joonistatakse see logo peale)
                if (hasUrl) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(info.coverArtUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Album Art",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
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