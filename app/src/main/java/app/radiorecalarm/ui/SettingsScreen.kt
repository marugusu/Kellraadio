package app.radiorecalarm.ui

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import app.radiorecalarm.LogExporter
import app.radiorecalarm.R
import app.radiorecalarm.update.GitHubRelease
import app.radiorecalarm.update.GitHubReleaseAsset
import app.radiorecalarm.update.UpdateUiState
import java.io.File
import kotlin.math.roundToInt

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
    hideRemoteStations: Boolean,
    widgetTransparency: Float,
    onToggleShowFlags: (Boolean) -> Unit,
    onToggleHideRemoteStations: (Boolean) -> Unit,
    onColsPortraitChange: (Int) -> Unit,
    onColsLandscapeChange: (Int) -> Unit,
    onRefresh: () -> Unit,
    onResetOrder: () -> Unit,
    onWidgetTransparencyChange: (Float) -> Unit,
    onExportData: () -> Unit,
    onImportData: () -> Unit,
    updateState: UpdateUiState = UpdateUiState.Idle,
    onCheckForUpdates: (String) -> Unit = {},
    onDownloadUpdate: (GitHubRelease, GitHubReleaseAsset) -> Unit = { _, _ -> },
    onInstallUpdate: (File) -> Unit = {},
    onDismissUpdateDialog: () -> Unit = {},
    canInstallPackages: Boolean = true,
    onRequestInstallPermission: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isLandscape = LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val appVersion = getAppVersionName(context)
    val scrollState = rememberScrollState()

    var showLanguageDialog by remember { mutableStateOf(false) }

    val currentLocales = AppCompatDelegate.getApplicationLocales()
    val manualLang = if (!currentLocales.isEmpty) currentLocales.get(0)?.language else null
    val systemLang = java.util.Locale.getDefault().language
    val displayLangCode = manualLang ?: if (SUPPORTED_LANGUAGES.any { it.code == systemLang }) systemLang else "en"

    val currentLanguageObj = SUPPORTED_LANGUAGES.find { it.code == displayLangCode }
        ?: SUPPORTED_LANGUAGES.find { it.code == "en" }!!

    Column(modifier = modifier.fillMaxSize()) {
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
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.primary
            )
        }

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
            // GRUPP 1: ÜLDINE
            SettingsSection(title = stringResource(R.string.settings_group_general)) {
                SettingsRow(
                    headline = stringResource(R.string.settings_language),
                    supporting = stringResource(R.string.settings_language_desc),
                    icon = Icons.Default.Language,
                    onClick = { showLanguageDialog = true },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${currentLanguageObj.flag} ${currentLanguageObj.name}",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                )
            }

            // GRUPP 2: RAADIO JA KANALID
            SettingsSection(title = stringResource(R.string.settings_group_radio)) {
                SettingsRow(
                    headline = stringResource(R.string.settings_refresh),
                    supporting = stringResource(R.string.settings_refresh_desc),
                    icon = Icons.Default.Refresh,
                    isLoading = isRefreshing,
                    onClick = onRefresh
                )
                SettingsDivider()
                SettingsRow(
                    headline = stringResource(R.string.settings_hide_remote),
                    supporting = stringResource(R.string.settings_hide_remote_desc),
                    icon = Icons.Default.VisibilityOff,
                    onClick = { onToggleHideRemoteStations(!hideRemoteStations) },
                    trailingContent = {
                        Switch(
                            checked = hideRemoteStations,
                            onCheckedChange = { onToggleHideRemoteStations(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.primary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                            )
                        )
                    }
                )
                SettingsDivider()
                SettingsRow(
                    headline = stringResource(R.string.settings_reset_order),
                    supporting = stringResource(R.string.settings_reset_order_desc),
                    icon = Icons.Default.SortByAlpha,
                    onClick = onResetOrder
                )
            }

            // GRUPP 3: VÄLIMUS JA PAIGUTUS
            SettingsSection(title = stringResource(R.string.settings_group_appearance)) {
                // Kanalite ruudustiku päis
                SettingsRow(
                    headline = stringResource(R.string.settings_grid_layout),
                    supporting = null,
                    icon = Icons.Default.ViewModule
                )
                // Püstine vaade (Portrait)
                PrecisionColumnSelector(
                    title = stringResource(R.string.settings_portrait),
                    selectedCount = colsPortrait,
                    onSelected = onColsPortraitChange
                )
                // Külili vaade (Landscape)
                PrecisionColumnSelector(
                    title = stringResource(R.string.settings_landscape),
                    selectedCount = colsLandscape,
                    onSelected = onColsLandscapeChange
                )

                SettingsDivider()

                // Näita riigitunnust (EE, LV...)
                SettingsRow(
                    headline = stringResource(R.string.settings_show_flags),
                    supporting = stringResource(R.string.settings_show_flags_desc),
                    icon = Icons.Default.Flag,
                    onClick = { onToggleShowFlags(!showFlags) },
                    trailingContent = {
                        Switch(
                            checked = showFlags,
                            onCheckedChange = { onToggleShowFlags(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.primary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                            )
                        )
                    }
                )

                SettingsDivider()

                // Vidina läbipaistvus
                SettingsWidgetTransparencyRow(
                    transparency = widgetTransparency,
                    onValueChange = onWidgetTransparencyChange
                )
            }

            // GRUPP 4: VARUNDUS JA TAASTAMINE
            SettingsSection(title = stringResource(R.string.settings_group_backup)) {
                SettingsRow(
                    headline = stringResource(R.string.settings_export),
                    supporting = stringResource(R.string.settings_export_desc),
                    icon = Icons.Default.Backup,
                    onClick = onExportData
                )
                SettingsDivider()
                SettingsRow(
                    headline = stringResource(R.string.settings_import),
                    supporting = stringResource(R.string.settings_import_desc),
                    icon = Icons.Default.Restore,
                    onClick = onImportData
                )
            }

            // GRUPP 5: ABI JA DIAGNOSTIKA
            SettingsSection(title = stringResource(R.string.settings_group_help)) {
                SettingsRow(
                    headline = stringResource(R.string.settings_check_updates),
                    supporting = when (updateState) {
                        is UpdateUiState.Checking -> stringResource(R.string.settings_checking_updates)
                        is UpdateUiState.UpdateAvailable -> stringResource(R.string.update_dialog_version, updateState.release.tagName)
                        is UpdateUiState.UpToDate -> stringResource(R.string.update_already_latest, updateState.currentVersion)
                        else -> stringResource(R.string.settings_check_updates_desc)
                    },
                    icon = Icons.Default.SystemUpdate,
                    isLoading = updateState is UpdateUiState.Checking,
                    onClick = { onCheckForUpdates(appVersion) }
                )
                SettingsDivider()
                SettingsRow(
                    headline = stringResource(R.string.settings_send_log),
                    supporting = stringResource(R.string.settings_send_log_desc),
                    icon = Icons.Default.BugReport,
                    onClick = { LogExporter.exportAndShareLog(context) }
                )
            }

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

    if (showLanguageDialog) {
        Dialog(onDismissRequest = { showLanguageDialog = false }) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.settings_language),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 16.dp, start = 4.dp)
                    )

                    SUPPORTED_LANGUAGES.forEach { lang ->
                        val isSelected = lang.code == displayLangCode
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent)
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f) else Color.Transparent,
                                    shape = RoundedCornerShape(6.dp)
                                )
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
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
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
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = { showLanguageDialog = false },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(stringResource(R.string.action_cancel))
                        }
                    }
                }
            }
        }
    }

    UpdateDialog(
        state = updateState,
        canInstallPackages = canInstallPackages,
        onDismiss = onDismissUpdateDialog,
        onDownload = onDownloadUpdate,
        onInstall = onInstallUpdate,
        onRequestPermission = onRequestInstallPermission
    )
}

/**
 * Ühtne grupeeritud sektsioon-paneel (Braun / Dieter Rams Hi-Fi esteetika)
 */
@Composable
fun SettingsSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp
            ),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 6.dp)
        )
        Surface(
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                content()
            }
        }
    }
}

/**
 * Seaderida paneeli sees
 */
@Composable
fun SettingsRow(
    headline: String,
    supporting: String? = null,
    icon: ImageVector? = null,
    isLoading: Boolean = false,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    trailingContent: @Composable (() -> Unit)? = null
) {
    val clickableModifier = if (onClick != null && !isLoading) {
        Modifier.clickable(onClick = onClick)
    } else {
        Modifier
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(clickableModifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(16.dp))
        } else if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = headline,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (!supporting.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = supporting,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }

        if (trailingContent != null) {
            Spacer(modifier = Modifier.width(12.dp))
            trailingContent()
        }
    }
}

/**
 * Diskreetne 1px horisontaalne vahejoon sektsiooni ridade vahel
 */
@Composable
fun SettingsDivider() {
    HorizontalDivider(
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
        modifier = Modifier.padding(horizontal = 16.dp)
    )
}

/**
 * Täppis-segmentnupud veergude valikuks (Dieter Rams / Braun stiil)
 * Kaotatud ovaalsed kapsel-servad ja linnukesed, asendatud puhta lülitiribaga.
 */
@Composable
fun PrecisionColumnSelector(
    title: String,
    selectedCount: Int,
    options: List<Int> = listOf(2, 3, 4, 5, 6),
    onSelected: (Int) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 54.dp, end = 16.dp, top = 4.dp, bottom = 12.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.6f))
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                .padding(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            options.forEachIndexed { index, count ->
                val isSelected = (count == selectedCount)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                        .clickable {
                            if (!isSelected) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSelected(count)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = count.toString(),
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (index < options.size - 1 && !isSelected && options[index + 1] != selectedCount) {
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(16.dp)
                            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    )
                }
            }
        }
    }
}

/**
 * Avakuva vidina läbipaistvuse liugur
 */
@Composable
fun SettingsWidgetTransparencyRow(
    transparency: Float,
    onValueChange: (Float) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Widgets,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.settings_widget_transparency),
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.settings_widget_transparency_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                modifier = Modifier.padding(start = 4.dp)
            ) {
                Text(
                    text = "${(transparency * 100).roundToInt()}%",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Slider(
            value = transparency,
            onValueChange = onValueChange,
            valueRange = 0f..1f,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 38.dp)
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
