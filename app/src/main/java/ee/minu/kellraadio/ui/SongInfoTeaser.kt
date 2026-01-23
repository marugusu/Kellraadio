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
import androidx.compose.ui.graphics.Color
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
    artist: String, // Lisame artisti ja pealkirja, et oleks mida näidata, kui albumit pole
    title: String,
    onClick: () -> Unit
) {
    // Ehitame infostringi (Album • 2024 • Rock)
    val infoParts = listOfNotNull(info.album, info.year, info.genre).filter { it.isNotEmpty() }
    val displayText = if (infoParts.isNotEmpty()) {
        infoParts.joinToString(" • ")
    } else {
        "$artist - $title" // Fallback
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp) // Paras kõrgus, et oleks mugav vajutada
            .background(Color.Black) // Täiesti must taust, sulandub menüüga
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. VÄIKE PILT VASAKUL
        if (!info.coverArtUrl.isNullOrEmpty()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(info.coverArtUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp)) // Veidi ümarad nurgad
                    .background(Color.DarkGray)
            )
        } else {
            // Kui pilti pole, näitame genereeritud logo või ikooni
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.MusicNote, null, tint = Color.Gray)
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        // 2. TEKST KESKEL
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            // Peamine info (Album jne)
            Text(
                text = displayText,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Medium
            )

            // Väike vihje all
            Text(
                text = stringResource(R.string.info_available), // "Lisainfo saadaval"
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary, // Värviline, et tõmbaks tähelepanu
                maxLines = 1
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        // 3. IKOON PAREMAL
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

// Abifunktsioon VectorPainterile (lisa faili lõppu või kasuta otse)
@Composable
fun rememberVectorPainter(image: androidx.compose.ui.graphics.vector.ImageVector) =
    androidx.compose.ui.graphics.vector.rememberVectorPainter(image)