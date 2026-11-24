package app.radiorecalarm.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.ImageRequest

/**
 * Ühtne Coil ImageLoader kettavahemälu toega jaamaikoonidele (toetab nii lokaalseid
 * `file:///android_asset/logos/...` faile kui ka väliseid URL-e).
 */
object StationImageLoader {
    @Volatile
    private var instance: ImageLoader? = null

    fun get(context: Context): ImageLoader {
        return instance ?: synchronized(this) {
            instance ?: ImageLoader.Builder(context.applicationContext)
                .memoryCache {
                    MemoryCache.Builder(context.applicationContext)
                        .maxSizePercent(0.15)
                        .build()
                }
                .diskCache {
                    DiskCache.Builder()
                        .directory(context.applicationContext.cacheDir.resolve("station_logos_cache"))
                        .maxSizeBytes(32L * 1024L * 1024L)
                        .build()
                }
                .crossfade(true)
                .build()
                .also { instance = it }
        }
    }
}

fun isFullBleedStationLogo(iconUrl: String): Boolean {
    val trimmed = iconUrl.trim()
    return trimmed.contains("/err_")
}

/**
 * Kuvab raadiojaama logo puhta raamita brändimärgina (nii pakitud `file:///android_asset/logos/...`
 * kui ka väliste `https://...` URL-ide korral), langedes puuduva/vigase allika korral
 * sujuvalt initsiaalimärgile.
 */
@Composable
fun StationLogoBadge(
    stationName: String,
    iconUrl: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    cornerRadius: Dp = 8.dp,
    innerPadding: Dp = 0.dp,
    borderColor: Color = MaterialTheme.colorScheme.outline,
    loadedBorderColor: Color = Color.Transparent,
    borderWidth: Dp = 1.dp,
    fallbackBackground: Color = MaterialTheme.colorScheme.surface,
    fallbackTextColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    fallbackFontSize: TextUnit = 11.sp,
    shape: Shape = RoundedCornerShape(cornerRadius)
) {
    val context = LocalContext.current
    val trimmedUrl = iconUrl.trim()
    val fullBleed = remember(trimmedUrl) { isFullBleedStationLogo(trimmedUrl) }
    val imageLoader = remember(context) { StationImageLoader.get(context) }
    var imageState by remember(trimmedUrl) {
        mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty)
    }
    val isLogoReady = trimmedUrl.isNotEmpty() && imageState is AsyncImagePainter.State.Success
    val effectiveBorderColor = if (isLogoReady) loadedBorderColor else borderColor

    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(if (isLogoReady) Color.Transparent else fallbackBackground)
            .then(
                if (borderWidth > 0.dp && effectiveBorderColor != Color.Transparent) {
                    Modifier.border(borderWidth, effectiveBorderColor, shape)
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        if (!isLogoReady) {
            Text(
                text = StationArtworkUtils.getStationInitials(stationName),
                fontSize = fallbackFontSize,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.5.sp,
                color = fallbackTextColor,
                maxLines = 1
            )
        }

        if (trimmedUrl.isNotEmpty()) {
            val request = remember(trimmedUrl, context) {
                ImageRequest.Builder(context)
                    .data(trimmedUrl)
                    .crossfade(true)
                    .build()
            }
            AsyncImage(
                model = request,
                imageLoader = imageLoader,
                contentDescription = null,
                contentScale = if (fullBleed) ContentScale.Crop else ContentScale.Fit,
                onState = { imageState = it },
                modifier = if (innerPadding > 0.dp && !fullBleed) {
                    Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                } else {
                    Modifier.fillMaxSize()
                }
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun StationLogoBadgeFallbackPreview() {
    MaterialTheme(colorScheme = darkColorScheme()) {
        Row(modifier = Modifier.padding(12.dp)) {
            StationLogoBadge(
                stationName = "Vikerraadio",
                iconUrl = ""
            )
            Spacer(modifier = Modifier.width(12.dp))
            StationLogoBadge(
                stationName = "Raadio 2",
                iconUrl = "",
                borderColor = Color(0xFFFFA726),
                fallbackTextColor = Color(0xFFFFA726)
            )
        }
    }
}
