package app.radiorecalarm.ui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import app.radiorecalarm.AppConfig
import app.radiorecalarm.LogExporter
import app.radiorecalarm.R
import app.radiorecalarm.backup.BackupData
import app.radiorecalarm.backup.BackupSettings
import app.radiorecalarm.backup.PendingBackupImport
import app.radiorecalarm.spotify.SpotifyPlaylistItem
import app.radiorecalarm.spotify.SpotifySaveTarget
import app.radiorecalarm.spotify.SpotifyUiState
import app.radiorecalarm.update.GitHubRelease
import app.radiorecalarm.update.GitHubReleaseAsset
import app.radiorecalarm.update.UpdateUiState
import java.io.File
import java.text.DateFormat
import java.util.Date
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
    continuousRecording: Boolean = false,
    onToggleContinuousRecording: (Boolean) -> Unit = {},
    minTrackDurationSeconds: Long = AppConfig.Recording.MIN_DURATION_SECONDS,
    onMinTrackDurationChange: (Long) -> Unit = {},
    preRollSeconds: Long = AppConfig.Recording.PRE_ROLL_SECONDS,
    onPreRollChange: (Long) -> Unit = {},
    postRollSeconds: Long = AppConfig.Recording.POST_ROLL_SECONDS,
    onPostRollChange: (Long) -> Unit = {},
    onToggleShowFlags: (Boolean) -> Unit,
    onToggleHideRemoteStations: (Boolean) -> Unit,
    onColsPortraitChange: (Int) -> Unit,
    onColsLandscapeChange: (Int) -> Unit,
    onRefresh: () -> Unit,
    onResetOrder: () -> Unit,
    onWidgetTransparencyChange: (Float) -> Unit,
    onExportData: () -> Unit,
    onImportData: () -> Unit,
    pendingBackupImport: PendingBackupImport? = null,
    onConfirmImportData: () -> Unit = {},
    onDismissImportData: () -> Unit = {},
    updateState: UpdateUiState = UpdateUiState.Idle,
    onCheckForUpdates: (String) -> Unit = {},
    onDownloadUpdate: (GitHubRelease, GitHubReleaseAsset) -> Unit = { _, _ -> },
    onInstallUpdate: (File) -> Unit = {},
    onDismissUpdateDialog: () -> Unit = {},
    canInstallPackages: Boolean = true,
    onRequestInstallPermission: () -> Unit = {},
    spotifyState: SpotifyUiState = SpotifyUiState(),
    onConnectSpotify: () -> Unit = {},
    onDisconnectSpotify: () -> Unit = {},
    onSpotifySaveTargetChange: (SpotifySaveTarget) -> Unit = {},
    onSpotifyPlaylistSelect: (SpotifyPlaylistItem) -> Unit = {},
    onRefreshSpotifyPlaylists: () -> Unit = {},
    onSaveSpotifyClientId: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isLandscape = LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val appVersion = getAppVersionName(context)
    val scrollState = rememberScrollState()

    var showLanguageDialog by remember { mutableStateOf(false) }
    var showMinDurationDialog by remember { mutableStateOf(false) }
    var showPreRollDialog by remember { mutableStateOf(false) }
    var showPostRollDialog by remember { mutableStateOf(false) }
    var showSpotifyDestinationDialog by remember { mutableStateOf(false) }
    var showSpotifyClientIdDialog by remember { mutableStateOf(false) }

    var hasInstallPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.packageManager.canRequestPackageInstalls()
            } else true
        )
    }
    var awaitingInstallPermission by remember { mutableStateOf(false) }

    fun checkAndHandlePermissionReturn() {
        val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else true
        hasInstallPermission = granted

        if (awaitingInstallPermission) {
            awaitingInstallPermission = false
            if (granted && updateState is UpdateUiState.ReadyToInstall) {
                onInstallUpdate(updateState.apkFile)
            }
        }
    }

    val installPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        checkAndHandlePermissionReturn()
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                checkAndHandlePermissionReturn()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

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

            // GRUPP: SALVESTAMINE (ADR-0005)
            SettingsSection(title = stringResource(R.string.settings_group_recording)) {
                SettingsRow(
                    headline = stringResource(R.string.settings_continuous_recording),
                    supporting = stringResource(R.string.settings_continuous_recording_desc),
                    icon = Icons.Default.FiberManualRecord,
                    onClick = { onToggleContinuousRecording(!continuousRecording) },
                    trailingContent = {
                        Switch(
                            checked = continuousRecording,
                            onCheckedChange = { onToggleContinuousRecording(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = MaterialTheme.colorScheme.error
                            )
                        )
                    }
                )

                SettingsDivider()

                SettingsRow(
                    headline = stringResource(R.string.settings_min_track_duration),
                    supporting = stringResource(R.string.settings_min_track_duration_desc),
                    icon = Icons.Default.Timer,
                    onClick = { showMinDurationDialog = true },
                    trailingContent = {
                        Text(
                            text = formatSecondsOptionLabel(minTrackDurationSeconds),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                )

                SettingsDivider()

                SettingsRow(
                    headline = stringResource(R.string.settings_pre_roll_margin),
                    supporting = stringResource(R.string.settings_pre_roll_margin_desc),
                    icon = Icons.Default.FastRewind,
                    onClick = { showPreRollDialog = true },
                    trailingContent = {
                        Text(
                            text = formatSecondsOptionLabel(preRollSeconds),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                )

                SettingsDivider()

                SettingsRow(
                    headline = stringResource(R.string.settings_post_roll_margin),
                    supporting = stringResource(R.string.settings_post_roll_margin_desc),
                    icon = Icons.Default.FastForward,
                    onClick = { showPostRollDialog = true },
                    trailingContent = {
                        Text(
                            text = formatSecondsOptionLabel(postRollSeconds),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                )

                SettingsDivider()

                SettingsRow(
                    headline = stringResource(R.string.settings_storage_safeguard),
                    supporting = stringResource(R.string.settings_storage_safeguard_desc),
                    icon = Icons.Default.SdStorage,
                    trailingContent = {
                        Text(
                            text = stringResource(R.string.storage_500mb),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                )

                SettingsDivider()

                SettingsRow(
                    headline = stringResource(R.string.settings_recording_folder),
                    supporting = "Android/data/${context.packageName}/files/${AppConfig.Recording.DEFAULT_AUDIO_DIR}",
                    icon = Icons.Default.Folder,
                    onClick = null
                )
            }

            // GRUPP: SPOTIFY
            SettingsSection(title = stringResource(R.string.settings_group_spotify)) {
                val spotifyHeadline = when {
                    spotifyState.isConnecting -> stringResource(R.string.spotify_connecting)
                    spotifyState.isConnected && !spotifyState.userDisplayName.isNullOrBlank() ->
                        stringResource(R.string.spotify_connected_as, spotifyState.userDisplayName!!)
                    spotifyState.isConnected -> stringResource(R.string.spotify_connected)
                    else -> stringResource(R.string.spotify_connect)
                }
                val spotifySupporting = when {
                    !spotifyState.hasClientId -> stringResource(R.string.spotify_client_id_missing)
                    spotifyState.isConnected -> null
                    else -> stringResource(R.string.spotify_connect_desc)
                }

                SettingsRow(
                    headline = spotifyHeadline,
                    supporting = spotifySupporting,
                    icon = Icons.Default.MusicNote,
                    isLoading = spotifyState.isConnecting,
                    onClick = {
                        when {
                            spotifyState.isConnected -> Unit
                            !spotifyState.hasClientId -> showSpotifyClientIdDialog = true
                            else -> onConnectSpotify()
                        }
                    },
                    trailingContent = if (spotifyState.isConnected) {
                        {
                            TextButton(
                                onClick = onDisconnectSpotify,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.spotify_disconnect),
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    } else null
                )

                if (spotifyState.isConnected) {
                    SettingsDivider()
                    val destinationLabel = when {
                        spotifyState.saveTarget == SpotifySaveTarget.PLAYLIST && !spotifyState.selectedPlaylistName.isNullOrBlank() ->
                            stringResource(R.string.spotify_playlist_selected, spotifyState.selectedPlaylistName!!)
                        else -> stringResource(R.string.spotify_liked_songs)
                    }
                    SettingsRow(
                        headline = stringResource(R.string.spotify_save_destination),
                        supporting = destinationLabel,
                        icon = Icons.AutoMirrored.Filled.PlaylistAdd,
                        onClick = {
                            if (spotifyState.playlists.isEmpty() && !spotifyState.isLoadingPlaylists) {
                                onRefreshSpotifyPlaylists()
                            }
                            showSpotifyDestinationDialog = true
                        }
                    )
                }

                SettingsDivider()
                SettingsRow(
                    headline = stringResource(R.string.spotify_advanced_client_id),
                    supporting = when {
                        spotifyState.isUsingCustomClientId -> stringResource(R.string.spotify_custom_client_id_active)
                        spotifyState.hasClientId -> stringResource(R.string.spotify_default_client_id_active)
                        else -> stringResource(R.string.spotify_client_id_missing)
                    },
                    icon = Icons.Default.Key,
                    onClick = { showSpotifyClientIdDialog = true }
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
        LanguagePickerSheet(
            selectedCode = displayLangCode,
            onSelectLanguage = { langCode ->
                val appLocale = LocaleListCompat.forLanguageTags(langCode)
                AppCompatDelegate.setApplicationLocales(appLocale)
                showLanguageDialog = false
            },
            onDismiss = { showLanguageDialog = false }
        )
    }

    if (showMinDurationDialog) {
        SecondsSliderSheet(
            title = stringResource(R.string.settings_min_track_duration),
            description = stringResource(R.string.settings_min_track_duration_desc),
            icon = Icons.Default.Timer,
            initialSeconds = minTrackDurationSeconds,
            maxSeconds = AppConfig.Recording.MIN_DURATION_MAX_SECONDS,
            onSave = { selected ->
                onMinTrackDurationChange(selected)
                showMinDurationDialog = false
            },
            onDismiss = { showMinDurationDialog = false }
        )
    }

    if (showPreRollDialog) {
        SecondsSliderSheet(
            title = stringResource(R.string.settings_pre_roll_margin),
            description = stringResource(R.string.settings_pre_roll_margin_desc),
            icon = Icons.Default.FastRewind,
            initialSeconds = preRollSeconds,
            maxSeconds = AppConfig.Recording.PRE_ROLL_MAX_SECONDS,
            onSave = { selected ->
                onPreRollChange(selected)
                showPreRollDialog = false
            },
            onDismiss = { showPreRollDialog = false }
        )
    }

    if (showPostRollDialog) {
        SecondsSliderSheet(
            title = stringResource(R.string.settings_post_roll_margin),
            description = stringResource(R.string.settings_post_roll_margin_desc),
            icon = Icons.Default.FastForward,
            initialSeconds = postRollSeconds,
            maxSeconds = AppConfig.Recording.POST_ROLL_MAX_SECONDS,
            onSave = { selected ->
                onPostRollChange(selected)
                showPostRollDialog = false
            },
            onDismiss = { showPostRollDialog = false }
        )
    }

    if (showSpotifyDestinationDialog) {
        SpotifyDestinationSheet(
            spotifyState = spotifyState,
            onSelectLikedSongs = {
                onSpotifySaveTargetChange(SpotifySaveTarget.LIKED_SONGS)
                showSpotifyDestinationDialog = false
            },
            onSelectPlaylist = { playlist ->
                onSpotifyPlaylistSelect(playlist)
                showSpotifyDestinationDialog = false
            },
            onRefreshPlaylists = onRefreshSpotifyPlaylists,
            onDismiss = { showSpotifyDestinationDialog = false }
        )
    }

    if (showSpotifyClientIdDialog) {
        SpotifyClientIdSheet(
            initialClientId = spotifyState.customClientId,
            onSave = { entered ->
                onSaveSpotifyClientId(entered)
                showSpotifyClientIdDialog = false
            },
            onDismiss = { showSpotifyClientIdDialog = false }
        )
    }

    if (pendingBackupImport != null) {
        RestoreConfirmSheet(
            pendingImport = pendingBackupImport,
            onConfirm = onConfirmImportData,
            onDismiss = onDismissImportData
        )
    }

    UpdateSheet(
        state = updateState,
        canInstallPackages = hasInstallPermission,
        onDismiss = onDismissUpdateDialog,
        onDownload = onDownloadUpdate,
        onInstall = onInstallUpdate,
        onRequestPermission = {
            awaitingInstallPermission = true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
                installPermissionLauncher.launch(intent)
            } else {
                onRequestInstallPermission()
            }
        }
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
    options: List<Int> = listOf(1, 2, 3, 4, 5, 6),
    onSelected: (Int) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val view = LocalView.current
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
                                view.performHapticTap()
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

@Composable
private fun formatSecondsOptionLabel(seconds: Long): String {
    return if (seconds <= 0L) {
        stringResource(R.string.settings_option_off)
    } else {
        stringResource(R.string.settings_seconds_value, seconds.toInt())
    }
}

internal fun clampAndRoundSeconds(
    rawValue: Float,
    maxSeconds: Long
): Long {
    val safeMax = maxSeconds.coerceAtLeast(0L)
    if (rawValue.isNaN()) return 0L
    return rawValue.coerceIn(0f, safeMax.toFloat()).roundToInt().toLong().coerceIn(0L, safeMax)
}

@Composable
private fun LanguagePickerSheet(
    selectedCode: String,
    onSelectLanguage: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AdaptiveModalSheet(
        onDismiss = onDismiss,
        dialogMaxWidth = 420.dp,
        dialogWidthFraction = 0.9f,
        dialogMaxHeightFraction = 0.85f
    ) { isDialogMode ->
        LanguagePickerSheetContent(
            selectedCode = selectedCode,
            onSelectLanguage = onSelectLanguage,
            onDismiss = onDismiss,
            modifier = Modifier.padding(
                start = 20.dp,
                end = 20.dp,
                top = if (isDialogMode) 16.dp else 4.dp,
                bottom = 16.dp
            )
        )
    }
}

@Composable
private fun LanguagePickerSheetContent(
    selectedCode: String,
    onSelectLanguage: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = stringResource(R.string.settings_language),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 16.dp, start = 4.dp)
        )

        SUPPORTED_LANGUAGES.forEach { lang ->
            val isSelected = lang.code == selectedCode
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
                    .clickable { onSelectLanguage(lang.code) }
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
                onClick = onDismiss,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    }
}

/**
 * Modal sheet for configuring recording second thresholds with 1-second precision.
 */
@Composable
private fun SecondsSliderSheet(
    title: String,
    description: String,
    icon: ImageVector,
    initialSeconds: Long,
    maxSeconds: Long,
    onSave: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val view = LocalView.current
    var selectedSeconds by remember(initialSeconds, maxSeconds) {
        mutableLongStateOf(clampAndRoundSeconds(initialSeconds.toFloat(), maxSeconds))
    }

    AdaptiveModalSheet(
        onDismiss = onDismiss,
        dialogMaxWidth = 440.dp,
        dialogWidthFraction = 0.9f,
        dialogMaxHeightFraction = 0.9f
    ) { isDialogMode ->
        SecondsSliderSheetContent(
            title = title,
            description = description,
            icon = icon,
            selectedSeconds = selectedSeconds,
            maxSeconds = maxSeconds,
            onSliderChange = { raw ->
                val nextSeconds = clampAndRoundSeconds(raw, maxSeconds)
                if (nextSeconds != selectedSeconds) {
                    view.performHapticTap()
                    selectedSeconds = nextSeconds
                }
            },
            onSave = {
                view.performHapticTap()
                onSave(selectedSeconds)
            },
            onDismiss = {
                view.performHapticTap()
                onDismiss()
            },
            modifier = Modifier.padding(
                start = 20.dp,
                end = 20.dp,
                top = if (isDialogMode) 20.dp else 4.dp,
                bottom = 20.dp
            )
        )
    }
}

@Composable
private fun SecondsSliderSheetContent(
    title: String,
    description: String,
    icon: ImageVector,
    selectedSeconds: Long,
    maxSeconds: Long,
    onSliderChange: (Float) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = formatSecondsOptionLabel(selectedSeconds),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(20.dp))
        Slider(
            value = selectedSeconds.toFloat(),
            onValueChange = onSliderChange,
            valueRange = 0f..maxSeconds.toFloat(),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary
            )
        )
        Spacer(modifier = Modifier.height(20.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)
        ) {
            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.action_cancel),
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
            Button(
                onClick = onSave,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(stringResource(R.string.action_save))
            }
        }
    }
}

@Composable
private fun RestoreConfirmSheet(
    pendingImport: PendingBackupImport,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val view = LocalView.current

    AdaptiveModalSheet(
        onDismiss = onDismiss,
        dialogMaxWidth = 460.dp,
        dialogWidthFraction = 0.9f,
        dialogMaxHeightFraction = 0.9f
    ) { isDialogMode ->
        RestoreConfirmSheetContent(
            pendingImport = pendingImport,
            onConfirm = {
                view.performHapticTap()
                onConfirm()
            },
            onDismiss = {
                view.performHapticTap()
                onDismiss()
            },
            modifier = Modifier.padding(
                start = 20.dp,
                end = 20.dp,
                top = if (isDialogMode) 20.dp else 4.dp,
                bottom = 20.dp
            )
        )
    }
}

@Composable
private fun RestoreConfirmSheetContent(
    pendingImport: PendingBackupImport,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val formattedDate = remember(pendingImport.exportedAt) {
        pendingImport.exportedAt?.let { timestamp ->
            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(timestamp))
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Restore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .size(28.dp)
        )
        Text(
            text = stringResource(R.string.restore_confirm_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(6.dp))
        if (formattedDate != null) {
            Text(
                text = stringResource(R.string.restore_confirm_exported_at, formattedDate),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = stringResource(R.string.restore_confirm_custom_stations, pendingImport.customStationCount),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = stringResource(R.string.restore_confirm_favorites, pendingImport.favoriteCount),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = stringResource(R.string.restore_confirm_alarms, pendingImport.alarmCount),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = if (pendingImport.hasSettings) {
                stringResource(R.string.restore_confirm_settings_included)
            } else {
                stringResource(R.string.restore_confirm_settings_none)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.restore_confirm_warning),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)
        ) {
            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.action_cancel),
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
            Button(
                onClick = onConfirm,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(stringResource(R.string.action_restore))
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0C12)
@Composable
private fun LanguagePickerSheetPreview() {
    MaterialTheme(colorScheme = darkColorScheme()) {
        Surface(color = MaterialTheme.colorScheme.surface) {
            LanguagePickerSheetContent(
                selectedCode = "et",
                onSelectLanguage = {},
                onDismiss = {},
                modifier = Modifier.padding(20.dp)
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0C12)
@Composable
private fun RestoreConfirmSheetPreview() {
    MaterialTheme(colorScheme = darkColorScheme()) {
        Surface(color = MaterialTheme.colorScheme.surface) {
            RestoreConfirmSheetContent(
                pendingImport = PendingBackupImport(
                    BackupData(
                        exportedAt = 1763890000000L,
                        settings = BackupSettings(colsPortrait = 3)
                    )
                ),
                onConfirm = {},
                onDismiss = {},
                modifier = Modifier.padding(20.dp)
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0C12)
@Composable
private fun SecondsSliderSheetPreview() {
    MaterialTheme(colorScheme = darkColorScheme()) {
        Surface(color = MaterialTheme.colorScheme.surface) {
            SecondsSliderSheetContent(
                title = "Minimum clip length",
                description = "Delete shorter clips such as jingles and ads",
                icon = Icons.Default.Timer,
                selectedSeconds = 15L,
                maxSeconds = AppConfig.Recording.MIN_DURATION_MAX_SECONDS,
                onSliderChange = {},
                onSave = {},
                onDismiss = {},
                modifier = Modifier.padding(20.dp)
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0C12)
@Composable
private fun SettingsScreenPreview() {
    MaterialTheme(colorScheme = darkColorScheme()) {
        SettingsScreen(
            isRefreshing = false,
            colsPortrait = 3,
            colsLandscape = 3,
            showFlags = true,
            hideRemoteStations = false,
            widgetTransparency = 0.25f,
            continuousRecording = false,
            minTrackDurationSeconds = 15L,
            preRollSeconds = 10L,
            postRollSeconds = 5L,
            onToggleShowFlags = {},
            onToggleHideRemoteStations = {},
            onColsPortraitChange = {},
            onColsLandscapeChange = {},
            onRefresh = {},
            onResetOrder = {},
            onWidgetTransparencyChange = {},
            onExportData = {},
            onImportData = {}
        )
    }
}

@Composable
private fun SpotifyDestinationSheet(
    spotifyState: SpotifyUiState,
    onSelectLikedSongs: () -> Unit,
    onSelectPlaylist: (SpotifyPlaylistItem) -> Unit,
    onRefreshPlaylists: () -> Unit,
    onDismiss: () -> Unit
) {
    AdaptiveModalSheet(
        onDismiss = onDismiss,
        dialogMaxWidth = 460.dp,
        dialogWidthFraction = 0.9f,
        dialogMaxHeightFraction = 0.85f,
        sheetHeightFraction = 0.72f
    ) { isDialogMode ->
        SpotifyDestinationSheetContent(
            spotifyState = spotifyState,
            onSelectLikedSongs = onSelectLikedSongs,
            onSelectPlaylist = onSelectPlaylist,
            onRefreshPlaylists = onRefreshPlaylists,
            onDismiss = onDismiss,
            fillListWeight = !isDialogMode,
            modifier = Modifier
                .fillMaxWidth()
                .then(if (isDialogMode) Modifier.heightIn(min = 240.dp) else Modifier.fillMaxHeight())
                .padding(
                    start = 16.dp,
                    end = 16.dp,
                    top = if (isDialogMode) 16.dp else 4.dp,
                    bottom = 16.dp
                )
        )
    }
}

@Composable
private fun SpotifyDestinationSheetContent(
    spotifyState: SpotifyUiState,
    onSelectLikedSongs: () -> Unit,
    onSelectPlaylist: (SpotifyPlaylistItem) -> Unit,
    onRefreshPlaylists: () -> Unit,
    onDismiss: () -> Unit,
    fillListWeight: Boolean,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(R.string.spotify_choose_destination),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onRefreshPlaylists, enabled = !spotifyState.isLoadingPlaylists) {
                if (spotifyState.isLoadingPlaylists) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.spotify_refresh_playlists),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        val likedSelected = spotifyState.saveTarget == SpotifySaveTarget.LIKED_SONGS
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(if (likedSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else Color.Transparent)
                .clickable(onClick = onSelectLikedSongs)
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = likedSelected, onClick = onSelectLikedSongs)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.spotify_liked_songs),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (likedSelected) FontWeight.Bold else FontWeight.Normal
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        if (spotifyState.isLoadingPlaylists && spotifyState.playlists.isEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(10.dp))
                Text(stringResource(R.string.spotify_loading_playlists), style = MaterialTheme.typography.bodyMedium)
            }
        } else if (spotifyState.playlists.isEmpty()) {
            Text(
                text = stringResource(R.string.spotify_no_editable_playlists),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (fillListWeight) Modifier.weight(1f)
                        else Modifier.heightIn(max = 260.dp)
                    )
            ) {
                items(spotifyState.playlists, key = { it.id }) { playlist ->
                    val isSelected = spotifyState.saveTarget == SpotifySaveTarget.PLAYLIST &&
                        spotifyState.selectedPlaylistId == playlist.id
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else Color.Transparent)
                            .clickable { onSelectPlaylist(playlist) }
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = isSelected, onClick = { onSelectPlaylist(playlist) })
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = playlist.name,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDismiss, shape = RoundedCornerShape(8.dp)) {
                Text(stringResource(R.string.action_close))
            }
        }
    }
}

@Composable
private fun SpotifyClientIdSheet(
    initialClientId: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var text by remember(initialClientId) { mutableStateOf(initialClientId) }

    AdaptiveModalSheet(
        onDismiss = onDismiss,
        dialogMaxWidth = 460.dp,
        dialogWidthFraction = 0.9f,
        dialogMaxHeightFraction = 0.9f
    ) { isDialogMode ->
        SpotifyClientIdSheetContent(
            clientId = text,
            onClientIdChange = { text = it },
            onCopyRedirectUri = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clipboard.setPrimaryClip(
                    android.content.ClipData.newPlainText(
                        "Spotify Redirect URI",
                        AppConfig.Api.SPOTIFY_REDIRECT_URI
                    )
                )
                android.widget.Toast.makeText(
                    context,
                    context.getString(R.string.spotify_redirect_uri_copied),
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            },
            onSave = { onSave(text) },
            onDismiss = onDismiss,
            modifier = Modifier.padding(
                start = 20.dp,
                end = 20.dp,
                top = if (isDialogMode) 16.dp else 4.dp,
                bottom = 16.dp
            )
        )
    }
}

@Composable
private fun SpotifyClientIdSheetContent(
    clientId: String,
    onClientIdChange: (String) -> Unit,
    onCopyRedirectUri: () -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = stringResource(R.string.spotify_client_id_dialog_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = clientId,
            onValueChange = onClientIdChange,
            label = { Text(stringResource(R.string.spotify_client_id_hint)) },
            singleLine = true,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.spotify_redirect_uri_label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = AppConfig.Api.SPOTIFY_REDIRECT_URI,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onCopyRedirectUri) {
                Text(stringResource(R.string.spotify_copy_redirect_uri))
            }
        }
        Spacer(modifier = Modifier.height(14.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onDismiss, shape = RoundedCornerShape(8.dp)) {
                Text(stringResource(R.string.action_cancel))
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = onSave, shape = RoundedCornerShape(8.dp)) {
                Text(stringResource(R.string.action_save))
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0C12)
@Composable
private fun SpotifyDestinationSheetPreview() {
    MaterialTheme(colorScheme = darkColorScheme()) {
        Surface(color = MaterialTheme.colorScheme.surface) {
            SpotifyDestinationSheetContent(
                spotifyState = SpotifyUiState(
                    isConnected = true,
                    playlists = listOf(
                        SpotifyPlaylistItem(id = "1", name = "Kellraadio leiud"),
                        SpotifyPlaylistItem(id = "2", name = "Hommikuraadio")
                    )
                ),
                onSelectLikedSongs = {},
                onSelectPlaylist = {},
                onRefreshPlaylists = {},
                onDismiss = {},
                fillListWeight = false,
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0C12)
@Composable
private fun SpotifyClientIdSheetPreview() {
    MaterialTheme(colorScheme = darkColorScheme()) {
        Surface(color = MaterialTheme.colorScheme.surface) {
            SpotifyClientIdSheetContent(
                clientId = "abc123",
                onClientIdChange = {},
                onCopyRedirectUri = {},
                onSave = {},
                onDismiss = {},
                modifier = Modifier.padding(20.dp)
            )
        }
    }
}
