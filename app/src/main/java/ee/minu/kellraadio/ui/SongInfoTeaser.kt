package ee.minu.kellraadio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ee.minu.kellraadio.R
import ee.minu.kellraadio.SongAdditionalInfo

@Composable
fun SongInfoTeaser(
    info: SongAdditionalInfo,
    onClick: () -> Unit
) {
    // Tume taust, mis sobib "Monoliidi" stiiliga (0xFF252525)
    Surface(
        modifier = Modifier
            .fillMaxWidth(0.95f) // Ei ole päris servast servani (hõljuv pill)
            .height(50.dp)       // Kompaktne kõrgus
            // VIIPAMISE TUVASTUS (SWIPE UP)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    // Kui lohistatakse üles (negatiivne Y)
                    if (dragAmount.y < -10) {
                        onClick()
                    }
                }
            }
            // KLIKITAV
            .clip(RoundedCornerShape(12.dp)) // SINU SOOVITUD 12dp
            .clickable { onClick() },
        color = Color(0xFF252525), // Tumehall, eristub mustast taustast
        shape = RoundedCornerShape(12.dp),
        shadowElevation = 8.dp // Vari, et "hõljuks"
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // VASAKUL: IKOON + TEKST
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Ikoon vastavalt sisule
                val icon = if (!info.lyrics.isNullOrEmpty()) Icons.Default.MusicNote else Icons.Default.Image
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary, // Ikoon on äpi põhivärvi
                    modifier = Modifier.size(20.dp)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text = stringResource(R.string.info_available), // "Lisainfo saadaval"
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }

            // PAREMAL: NOOL ÜLES
            Icon(
                imageVector = Icons.Default.KeyboardArrowUp,
                contentDescription = null,
                tint = Color.Gray
            )
        }
    }
}