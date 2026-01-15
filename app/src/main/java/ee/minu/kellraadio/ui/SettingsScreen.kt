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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ee.minu.kellraadio.LogExporter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    isRefreshing: Boolean,
    colsPortrait: Int,                    // UUS
    colsLandscape: Int,                   // UUS
    onColsPortraitChange: (Int) -> Unit,   // UUS
    onColsLandscapeChange: (Int) -> Unit,  // UUS
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(start = if (isLandscape) 8.dp else 16.dp, end = 16.dp)
                .padding(top = if (isLandscape) 12.dp else 0.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Settings, null, tint = MaterialTheme.colorScheme.primary)
            spacer(modifier = Modifier.width(8.dp))
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
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

            // SEKTSIOON 2: PAIGUTUS (UUS)
            SettingsGroup(title = "Välimus ja Paigutus") {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // Portree vaate tulpade arv
                        Text(
                            text = "Tulpade arv (Püsti)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            val portraitOptions = listOf(2, 3, 4, 5)
                            portraitOptions.forEachIndexed { index, count ->
                                SegmentedButton(
                                    selected = colsPortrait == count,
                                    onClick = { onColsPortraitChange(count) },
                                    shape = SegmentedButtonDefaults.itemShape(index = index, count = portraitOptions.size)
                                ) {
                                    Text(count.toString())
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Landscape vaate tulpade arv
                        Text(
                            text = "Tulpade arv (Külili)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            val landscapeOptions = listOf(2, 3, 4, 5, 6)
                            landscapeOptions.forEachIndexed { index, count ->
                                SegmentedButton(
                                    selected = colsLandscape == count,
                                    onClick = { onColsLandscapeChange(count) },
                                    shape = SegmentedButtonDefaults.itemShape(index = index, count = landscapeOptions.size)
                                ) {
                                    Text(count.toString())
                                }
                            }
                        }
                    }
                }
            }

            // SEKTSIOON 3: DIAGNOSTIKA
            SettingsGroup(title = "Abi ja Diagnostika") {
                SettingsCardItem(
                    headline = "Saada logi",
                    supporting = "Jaga tehnilist infot arendajaga",
                    icon = Icons.Default.BugReport,
                    onClick = { LogExporter.exportAndShareLog(context) }
                )

                Spacer(modifier = Modifier.height(4.dp))

                SettingsCardItem(
                    headline = "Testi ajalugu",
                    supporting = "Lisa andmebaasi prooviandmeid",
                    icon = Icons.Default.Science,
                    onClick = onAddTestData,
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.2f)
                )
            }

            // JALUS
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
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        modifier = Modifier.fillMaxWidth()
    ) {
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
                containerColor = Color.Transparent
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

@Composable
fun spacer(modifier: Modifier) {
    Spacer(modifier = modifier)
}