package ee.minu.kellraadio.ui

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ee.minu.kellraadio.LogExporter

@Composable
fun SettingsScreen(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onAddTestData: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isLandscape = androidx.compose.ui.platform.LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val appVersion = getAppVersionName(context)
    val scrollState = rememberScrollState()

    Column(modifier = modifier.fillMaxSize()) {
        // --- PÄIS ---
        // Järgib täpselt teiste ekraanide (Info, Ajalugu) paigutust
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(start = if (isLandscape) 8.dp else 16.dp, end = 16.dp)
                .padding(top = if (isLandscape) 12.dp else 0.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Settings, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Seaded",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.primary
            )
        }

        // --- SISU ---
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(
                    start = if (isLandscape) 8.dp else 16.dp,
                    end = 16.dp,
                    top = if (isLandscape) 4.dp else 0.dp
                ),
            verticalArrangement = Arrangement.spacedBy(16.dp) // Material 3 standard vahe
        ) {

            // SEKTSIOON 1: KANALID
            SettingsGroup(title = "Raadio ja Kanalid") {
                SettingsCardItem(
                    headline = "Värskenda jaamu",
                    supporting = "Lae serverist uusim kanalite nimekiri",
                    icon = Icons.Default.Refresh,
                    isLoading = isRefreshing,
                    onClick = onRefresh
                )
            }

            // SEKTSIOON 2: DIAGNOSTIKA
            SettingsGroup(title = "Abi ja Diagnostika") {
                SettingsCardItem(
                    headline = "Saada logi",
                    supporting = "Jaga tehnilist infot arendajaga",
                    icon = Icons.Default.BugReport,
                    onClick = { LogExporter.exportAndShareLog(context) }
                )

                // Eraldusjoon kaartide vahel, kui soovid neid ühte gruppi panna,
                // või eraldi kaart nagu siin:
                Spacer(modifier = Modifier.height(0.dp))

                SettingsCardItem(
                    headline = "Testi ajalugu",
                    supporting = "Lisa andmebaasi prooviandmeid",
                    icon = Icons.Default.Science,
                    onClick = onAddTestData,
                    // Testimise asi võiks olla visuaalselt natuke teistsugune
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.2f)
                )
            }

            // JALUS: VERSIOON
            Spacer(modifier = Modifier.height(16.dp))
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Versioon $appVersion",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

// --- MATERIAL 3 ABIKOMPONENDID ---

@Composable
fun SettingsGroup(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
        )
        content()
    }
}

@Composable
fun SettingsCardItem(
    headline: String,
    supporting: String,
    icon: ImageVector,
    isLoading: Boolean = false,
    onClick: () -> Unit,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant
) {
    Card(
        shape = RoundedCornerShape(12.dp), // M3 Medium shape
        colors = CardDefaults.cardColors(containerColor = containerColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        // ListItem on Material 3 standardkomponent nimekirjade jaoks.
        // Me paneme selle Cardi sisse, et saada sinu äpi stiili.
        ListItem(
            headlineContent = {
                Text(
                    text = headline,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
            },
            supportingContent = {
                Text(
                    text = supporting,
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            leadingContent = {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            },
            colors = ListItemDefaults.colors(
                containerColor = Color.Transparent // Läbipaistev, et Cardi värv paistaks
            ),
            modifier = Modifier.clickable(enabled = !isLoading, onClick = onClick)
        )
    }
}

private fun getAppVersionName(context: Context): String {
    return try {
        val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0)
        }
        packageInfo.versionName ?: "1.0"
    } catch (e: Exception) { "1.0" }
}