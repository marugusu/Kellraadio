package app.radiorecalarm.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.radiorecalarm.R
import app.radiorecalarm.ui.theme.KellraadioTheme
import app.radiorecalarm.update.GitHubRelease
import app.radiorecalarm.update.GitHubReleaseAsset
import app.radiorecalarm.update.UpdateUiState
import java.io.File
import java.util.Locale

@Composable
fun UpdateSheet(
    state: UpdateUiState,
    canInstallPackages: Boolean,
    onDismiss: () -> Unit,
    onDownload: (GitHubRelease, GitHubReleaseAsset) -> Unit,
    onInstall: (File) -> Unit,
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (state is UpdateUiState.Idle || state is UpdateUiState.Checking) {
        return
    }

    AdaptiveModalSheet(
        onDismiss = onDismiss,
        dialogMaxWidth = 480.dp,
        dialogWidthFraction = 0.92f,
        dialogMaxHeightFraction = 0.9f,
        dismissible = state !is UpdateUiState.Downloading
    ) { isDialogMode ->
        UpdateSheetContent(
            state = state,
            canInstallPackages = canInstallPackages,
            onDismiss = onDismiss,
            onDownload = onDownload,
            onInstall = onInstall,
            onRequestPermission = onRequestPermission,
            modifier = modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(
                    start = 20.dp,
                    end = 20.dp,
                    top = if (isDialogMode) 20.dp else 4.dp,
                    bottom = 20.dp
                )
        )
    }
}

@Composable
private fun UpdateSheetContent(
    state: UpdateUiState,
    canInstallPackages: Boolean,
    onDismiss: () -> Unit,
    onDownload: (GitHubRelease, GitHubReleaseAsset) -> Unit,
    onInstall: (File) -> Unit,
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        when (state) {
            is UpdateUiState.UpToDate -> {
                UpToDateContent(
                    version = state.currentVersion,
                    onDismiss = onDismiss
                )
            }
            is UpdateUiState.UpdateAvailable -> {
                UpdateAvailableContent(
                    release = state.release,
                    asset = state.apkAsset,
                    onDismiss = onDismiss,
                    onDownload = { onDownload(state.release, state.apkAsset) }
                )
            }
            is UpdateUiState.Downloading -> {
                DownloadingContent(
                    progress = state.progress,
                    downloadedBytes = state.downloadedBytes,
                    totalBytes = state.totalBytes,
                    onCancel = onDismiss
                )
            }
            is UpdateUiState.ReadyToInstall -> {
                ReadyToInstallContent(
                    release = state.release,
                    canInstallPackages = canInstallPackages,
                    onDismiss = onDismiss,
                    onInstall = { onInstall(state.apkFile) },
                    onRequestPermission = onRequestPermission
                )
            }
            is UpdateUiState.Error -> {
                UpdateErrorContent(
                    errorMessage = state.message,
                    onDismiss = onDismiss
                )
            }
            else -> Unit
        }
    }
}

@Composable
private fun UpToDateContent(
    version: String,
    onDismiss: () -> Unit
) {
    SheetHeader(
        icon = Icons.Default.CheckCircle,
        iconTint = MaterialTheme.colorScheme.primary,
        title = stringResource(R.string.update_already_latest, version),
        subtitle = null
    )

    Spacer(modifier = Modifier.height(20.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        Button(
            onClick = onDismiss,
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(stringResource(R.string.action_close))
        }
    }
}

@Composable
private fun UpdateAvailableContent(
    release: GitHubRelease,
    asset: GitHubReleaseAsset,
    onDismiss: () -> Unit,
    onDownload: () -> Unit
) {
    val sizeMb = if (asset.size > 0L) {
        String.format(Locale.US, " • %.1f MB", asset.size / (1024f * 1024f))
    } else ""

    SheetHeader(
        icon = Icons.Default.SystemUpdate,
        iconTint = MaterialTheme.colorScheme.primary,
        title = stringResource(R.string.update_dialog_title),
        subtitle = "${stringResource(R.string.update_dialog_version, release.tagName)}$sizeMb"
    )

    if (!release.body.isNullOrBlank()) {
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = stringResource(R.string.update_dialog_changelog),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 60.dp, max = 180.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                .padding(12.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = release.body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }

    Spacer(modifier = Modifier.height(20.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
    ) {
        OutlinedButton(
            onClick = onDismiss,
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(stringResource(R.string.action_cancel))
        }
        Button(
            onClick = onDownload,
            shape = RoundedCornerShape(8.dp)
        ) {
            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(stringResource(R.string.update_action_download))
        }
    }
}

@Composable
private fun DownloadingContent(
    progress: Float,
    downloadedBytes: Long,
    totalBytes: Long,
    onCancel: () -> Unit
) {
    val percent = (progress * 100f).toInt()
    val downloadedMb = downloadedBytes / (1024f * 1024f)
    val totalMb = if (totalBytes > 0L) totalBytes / (1024f * 1024f) else 0f
    val progressText = if (totalBytes > 0L) {
        String.format(Locale.US, "%.1f MB / %.1f MB (%d%%)", downloadedMb, totalMb, percent)
    } else {
        String.format(Locale.US, "%.1f MB", downloadedMb)
    }

    SheetHeader(
        icon = Icons.Default.CloudDownload,
        iconTint = MaterialTheme.colorScheme.primary,
        title = stringResource(R.string.update_downloading, percent),
        subtitle = progressText
    )

    Spacer(modifier = Modifier.height(16.dp))

    LinearProgressIndicator(
        progress = { progress },
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp)),
        color = MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.surfaceVariant
    )

    Spacer(modifier = Modifier.height(20.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        TextButton(
            onClick = onCancel,
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(stringResource(R.string.action_cancel))
        }
    }
}

@Composable
private fun ReadyToInstallContent(
    release: GitHubRelease,
    canInstallPackages: Boolean,
    onDismiss: () -> Unit,
    onInstall: () -> Unit,
    onRequestPermission: () -> Unit
) {
    SheetHeader(
        icon = Icons.Default.CheckCircle,
        iconTint = MaterialTheme.colorScheme.primary,
        title = stringResource(R.string.update_action_install),
        subtitle = stringResource(R.string.update_dialog_version, release.tagName)
    )

    Spacer(modifier = Modifier.height(14.dp))

    if (!canInstallPackages) {
        Text(
            text = stringResource(R.string.update_permission_required),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
    } else {
        Text(
            text = "Fail on allalaaditud ja valmis paigaldamiseks.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    Spacer(modifier = Modifier.height(20.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
    ) {
        OutlinedButton(
            onClick = onDismiss,
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(stringResource(R.string.action_cancel))
        }

        if (!canInstallPackages) {
            Button(
                onClick = onRequestPermission,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(stringResource(R.string.update_open_settings))
            }
        } else {
            Button(
                onClick = onInstall,
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.InstallMobile, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.update_action_install))
            }
        }
    }
}

@Composable
private fun UpdateErrorContent(
    errorMessage: String,
    onDismiss: () -> Unit
) {
    SheetHeader(
        icon = Icons.Default.Warning,
        iconTint = MaterialTheme.colorScheme.error,
        title = "Viga uuenduse kontrollimisel",
        subtitle = errorMessage
    )

    Spacer(modifier = Modifier.height(20.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        Button(
            onClick = onDismiss,
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(stringResource(R.string.action_close))
        }
    }
}

@Composable
private fun SheetHeader(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String?
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(32.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0C10)
@Composable
private fun UpdateSheetPreview() {
    KellraadioTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            UpdateSheetContent(
                state = UpdateUiState.UpdateAvailable(
                    release = GitHubRelease(
                        tagName = "v1.1.0",
                        name = "Kellraadio 1.1",
                        body = "- Äpisisene uuendus\n- Bluetooth stabiilsuse parandused\n- Parem raadiojaamade laadimine"
                    ),
                    apkAsset = GitHubReleaseAsset(
                        name = "Kellraadio-v1.1.0.apk",
                        size = 15728640L,
                        browserDownloadUrl = "https://github.com"
                    ),
                    currentVersion = "1.0.0"
                ),
                canInstallPackages = true,
                onDismiss = {},
                onDownload = { _, _ -> },
                onInstall = {},
                onRequestPermission = {},
                modifier = Modifier.padding(20.dp)
            )
        }
    }
}
