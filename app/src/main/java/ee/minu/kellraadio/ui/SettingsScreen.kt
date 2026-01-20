package ee.minu.kellraadio.ui

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import ee.minu.kellraadio.LogExporter
import ee.minu.kellraadio.R

// Defineerime toetatud keeled ühes kohas
data class AppLanguage(val code: String, val flag: String, val name: String)

val SUPPORTED_LANGUAGES = listOf(
    AppLanguage("et", "🇪🇪", "Eesti"),
    AppLanguage("en", "🇬🇧", "English"),
    AppLanguage("liv", "\uD83D\uDFE2", "Līvõ kēļ"),
    AppLanguage("ko", "🇰🇷", "한국어")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    isRefreshing: Boolean,
    colsPortrait: Int,
    colsLandscape: Int,
    showFlags: Boolean,
    onToggleShowFlags: (Boolean) -> Unit,
    onColsPortraitChange: (Int) -> Unit,
    onColsLandscapeChange: (Int) -> Unit,
    onRefresh: () -> Unit,
    onAddTestData: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isLandscape = androidx.compose.ui.platform.LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val appVersion = getAppVersionName(context)
    val scrollState = rememberScrollState()

    // Olek dialoogi avamiseks
    var showLanguageDialog by remember { mutableStateOf(false) }

    // KEELE LOOGIKA
    val currentLocales = AppCompatDelegate.getApplicationLocales()
    val appLang = if (!currentLocales.isEmpty) currentLocales.get(0)?.language else "et" // Vaikimisi Eesti, kui pole määratud

    // Leiame praeguse keele objekti kuvamiseks
    val currentLanguageObj = SUPPORTED_LANGUAGES.find { it.code == appLang }
        ?: SUPPORTED_LANGUAGES.find { it.code == "et" }!!

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
                text = stringResource(R.string.settings_title),
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

            // SEKTSIOON 0: ÜLDINE (KEEL)
            SettingsGroup(title = stringResource(R.string.settings_group_general)) {
                SettingsCardItem(
                    headline = stringResource(R.string.settings_language),
                    supporting = stringResource(R.string.settings_language_desc),
                    icon = Icons.Default.Language,
                    onClick = { showLanguageDialog = true }, // Avab dialoogi
                    trailingContent = {
                        Text(
                            text = "${currentLanguageObj.flag} ${currentLanguageObj.name}",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                )
            }

            // SEKTSIOON 1: KANALID
            SettingsGroup(title = stringResource(R.string.settings_group_radio)) {
                SettingsCardItem(
                    headline = stringResource(R.string.settings_refresh),
                    supporting = stringResource(R.string.settings_refresh_desc),
                    icon = Icons.Default.Refresh,
                    isLoading = isRefreshing,
                    onClick = onRefresh
                )
            }

            // SEKTSIOON 2: PAIGUTUS
            SettingsGroup(title = stringResource(R.string.settings_group_appearance)) {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.ViewModule,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = stringResource(R.string.settings_grid_layout),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = stringResource(R.string.settings_portrait),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            val portraitOptions = listOf(2, 3, 4, 5, 6)
                            portraitOptions.forEachIndexed { index, count ->
                                SegmentedButton(
                                    selected = colsPortrait == count,
                                    onClick = { onColsPortraitChange(count) },
                                    shape = SegmentedButtonDefaults.itemShape(index = index, count = portraitOptions.size),
                                    colors = SegmentedButtonDefaults.colors(
                                        activeContainerColor = MaterialTheme.colorScheme.primary,
                                        activeContentColor = MaterialTheme.colorScheme.onPrimary,
                                        inactiveContainerColor = Color.Transparent
                                    )
                                ) {
                                    Text(count.toString())
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = stringResource(R.string.settings_landscape),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            val landscapeOptions = listOf(2, 3, 4, 5, 6)
                            landscapeOptions.forEachIndexed { index, count ->
                                SegmentedButton(
                                    selected = colsLandscape == count,
                                    onClick = { onColsLandscapeChange(count) },
                                    shape = SegmentedButtonDefaults.itemShape(index = index, count = landscapeOptions.size),
                                    colors = SegmentedButtonDefaults.colors(
                                        activeContainerColor = MaterialTheme.colorScheme.primary,
                                        activeContentColor = MaterialTheme.colorScheme.onPrimary,
                                        inactiveContainerColor = Color.Transparent
                                    )
                                ) {
                                    Text(count.toString())
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Divider(color = MaterialTheme.colorScheme.surface)
                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.settings_show_flags),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = stringResource(R.string.settings_show_flags_desc),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = showFlags,
                                onCheckedChange = onToggleShowFlags
                            )
                        }
                    }
                }
            }

            // SEKTSIOON 3: DIAGNOSTIKA
            SettingsGroup(title = stringResource(R.string.settings_group_help)) {
                SettingsCardItem(
                    headline = stringResource(R.string.settings_send_log),
                    supporting = stringResource(R.string.settings_send_log_desc),
                    icon = Icons.Default.BugReport,
                    onClick = { LogExporter.exportAndShareLog(context) }
                )

                Spacer(modifier = Modifier.height(4.dp))

                SettingsCardItem(
                    headline = stringResource(R.string.settings_test_data),
                    supporting = stringResource(R.string.settings_test_data_desc),
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
                    text = stringResource(R.string.app_version, appVersion),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // --- KEELEVALIKU DIALOOG ---
    if (showLanguageDialog) {
        Dialog(onDismissRequest = { showLanguageDialog = false }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.settings_language),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 16.dp, start = 8.dp)
                    )

                    SUPPORTED_LANGUAGES.forEach { lang ->
                        val isSelected = lang.code == appLang
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                .clickable {
                                    val appLocale = LocaleListCompat.forLanguageTags(lang.code)
                                    AppCompatDelegate.setApplicationLocales(appLocale)
                                    showLanguageDialog = false
                                }
                                .padding(vertical = 12.dp, horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = lang.flag,
                                style = MaterialTheme.typography.headlineSmall
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = lang.name,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                            )
                            if (isSelected) {
                                Spacer(modifier = Modifier.weight(1f))
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showLanguageDialog = false }) {
                            Text(stringResource(R.string.action_cancel))
                        }
                    }
                }
            }
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
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    trailingContent: @Composable (() -> Unit)? = null
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
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
            trailingContent = trailingContent,
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