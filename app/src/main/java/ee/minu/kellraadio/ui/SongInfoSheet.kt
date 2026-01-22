package ee.minu.kellraadio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.Icons
import coil.compose.AsyncImage
import coil.request.ImageRequest
import ee.minu.kellraadio.R
import ee.minu.kellraadio.SongAdditionalInfo

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SongInfoSheet(
    artist: String,
    title: String,
    info: SongAdditionalInfo,
    onDismiss: () -> Unit // Jätame alles, ehkki hetkel ei kasuta (hea tava)
) {
    val scrollState = rememberScrollState()

    // Juurkonteiner (Box), et saaksime panna taustapildi ja sisu üksteise peale
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.90f) // Natuke kõrgem
    ) {
        // --- KIHT 1: ATMOSFÄÄRILINE TAUST ---
        if (!info.coverArtUrl.isNullOrEmpty()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(info.coverArtUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(radius = 30.dp) // Tugev udu (töötab Android 12+, vanematel lihtsalt tume)
            )
            // Tume kiht udu peal, et tekst oleks loetav
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f))
            )
        } else {
            // Kui pilti pole, siis ilus tume gradient
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color(0xFF2B2B2B), Color.Black)
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
            // Jätame ainult natuke ruumi üles, sest ModalBottomSheet joonistab ise oma "sanga"
            Spacer(modifier = Modifier.height(16.dp))

            // 1. ALBUMI KAANEPILT (Varjuga)
            if (!info.coverArtUrl.isNullOrEmpty()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(info.coverArtUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = "Album Art",
                    modifier = Modifier
                        .size(280.dp)
                        .shadow(elevation = 24.dp, shape = RoundedCornerShape(16.dp)) // Vari
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.DarkGray),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.height(32.dp))
            }

            // 2. INFO (Suur pealkiri)
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

            // --- LISAINFO MÄRGID (Chips) ---
            if (info.album != null || info.year != null || info.genre != null) {
                Spacer(modifier = Modifier.height(24.dp))

                // FlowRow paigutab märgid ritta, ja kui ruumi vähe, siis uuele reale
                FlowRow(
                    horizontalArrangement = Arrangement.Center,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (!info.year.isNullOrEmpty()) {
                        InfoChip(text = info.year)
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    if (!info.genre.isNullOrEmpty()) {
                        InfoChip(text = info.genre)
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    if (!info.album.isNullOrEmpty()) {
                        // Album võib olla pikk, paneme eraldi reale või lõppu
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
                Text(
                    text = stringResource(R.string.info_not_found),
                    color = Color.Gray
                )
            }

            Spacer(modifier = Modifier.height(64.dp))
        }
    }
}

// Stiilne "Märk" (Chip) info jaoks
@Composable
fun InfoChip(text: String, icon: Boolean = false) {
    Surface(
        color = Color.White.copy(alpha = 0.1f), // Pool-läbipaistev taust
        shape = RoundedCornerShape(50),         // Täiesti ümar
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon) {
                // Siia võiks panna albumi ikooni, kui tahad
                Icon(
                    imageVector = Icons.Default.Album,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                fontWeight = FontWeight.Medium
            )
        }
    }
}