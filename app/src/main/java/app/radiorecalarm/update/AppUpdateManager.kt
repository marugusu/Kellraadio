package app.radiorecalarm.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import app.radiorecalarm.AppConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit

sealed interface UpdateUiState {
    object Idle : UpdateUiState
    object Checking : UpdateUiState
    data class UpToDate(val currentVersion: String) : UpdateUiState
    data class UpdateAvailable(
        val release: GitHubRelease,
        val apkAsset: GitHubReleaseAsset,
        val currentVersion: String
    ) : UpdateUiState
    data class Downloading(
        val release: GitHubRelease,
        val apkAsset: GitHubReleaseAsset,
        val progress: Float, // 0.0f .. 1.0f
        val downloadedBytes: Long,
        val totalBytes: Long
    ) : UpdateUiState
    data class ReadyToInstall(
        val release: GitHubRelease,
        val apkAsset: GitHubReleaseAsset,
        val apkFile: File
    ) : UpdateUiState
    data class Error(val message: String) : UpdateUiState
}

class AppUpdateManager(
    private val context: Context,
    private val apiService: GitHubReleaseApiService = GitHubReleaseApiService.create(),
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
) {
    companion object {
        private const val TAG = "AppUpdateManager"
        private const val UPDATE_DIR_NAME = "updates"

        /**
         * Tagastab tõeväärtuse, kas remoteVersion on suurem kui currentVersion.
         * Toetab semantilisi versioone nagu "1.0", "1.0.1", "v1.2", "2.0.0-rc1" jne.
         */
        fun isNewerVersion(currentVersion: String, remoteVersion: String): Boolean {
            val cleanCurrent = cleanVersionString(currentVersion)
            val cleanRemote = cleanVersionString(remoteVersion)

            val currentParts = parseVersionParts(cleanCurrent)
            val remoteParts = parseVersionParts(cleanRemote)

            val maxLength = maxOf(currentParts.size, remoteParts.size)
            for (i in 0 until maxLength) {
                val currentPart = currentParts.getOrElse(i) { 0 }
                val remotePart = remoteParts.getOrElse(i) { 0 }
                if (remotePart > currentPart) return true
                if (remotePart < currentPart) return false
            }
            return false
        }

        private fun cleanVersionString(version: String): String {
            return version.trim().removePrefix("v").removePrefix("V")
        }

        private fun parseVersionParts(version: String): List<Int> {
            val mainPart = version.split("-", "+").firstOrNull() ?: version
            return mainPart.split(".")
                .mapNotNull { it.trim().toIntOrNull() }
        }
    }

    /**
     * Kontrollib GitHubist, kas on olemas uuem versioon.
     */
    suspend fun checkForUpdate(currentVersion: String): UpdateUiState = withContext(Dispatchers.IO) {
        try {
            val release = apiService.getLatestRelease()
            val apkAsset = release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }

            if (apkAsset == null) {
                Log.w(TAG, "Latest release found (${release.tagName}), but no .apk asset attached.")
                return@withContext UpdateUiState.UpToDate(currentVersion)
            }

            if (isNewerVersion(currentVersion, release.tagName)) {
                // Kontrollime, kas fail on juba vahemälus täielikult alla laaditud
                val cachedApk = getTargetApkFile(release.tagName)
                if (cachedApk.exists() && cachedApk.length() == apkAsset.size && apkAsset.size > 0L) {
                    UpdateUiState.ReadyToInstall(release, apkAsset, cachedApk)
                } else {
                    UpdateUiState.UpdateAvailable(release, apkAsset, currentVersion)
                }
            } else {
                UpdateUiState.UpToDate(currentVersion)
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e(TAG, "Error checking for updates", e)
            UpdateUiState.Error(e.localizedMessage ?: "Võrguviga uuenduste kontrollimisel")
        }
    }

    /**
     * Laadib APK faili alla ja väljastab edenemise olekud vooluna (Flow).
     */
    fun downloadApk(release: GitHubRelease, asset: GitHubReleaseAsset): Flow<UpdateUiState> = flow {
        val targetFile = getTargetApkFile(release.tagName)

        // Kui fail on juba olemas sama suurusega, saame kohe paigaldada
        if (targetFile.exists() && targetFile.length() == asset.size && asset.size > 0L) {
            emit(UpdateUiState.ReadyToInstall(release, asset, targetFile))
            return@flow
        }

        val request = Request.Builder()
            .url(asset.browserDownloadUrl)
            .header("User-Agent", AppConfig.Api.USER_AGENT)
            .build()

        var downloadedBytes = 0L
        val totalBytes = if (asset.size > 0L) asset.size else -1L

        targetFile.parentFile?.mkdirs()
        val tempFile = File(targetFile.parentFile, "${targetFile.name}.tmp")

        try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    emit(UpdateUiState.Error("Allalaadimine ebaõnnestus (kood: ${response.code})"))
                    return@flow
                }

                val body = response.body ?: run {
                    emit(UpdateUiState.Error("Serveri vastus oli tühi"))
                    return@flow
                }

                val expectedLength = if (body.contentLength() > 0L) body.contentLength() else totalBytes

                body.byteStream().use { input: InputStream ->
                    FileOutputStream(tempFile).use { output: FileOutputStream ->
                        val buffer = ByteArray(8 * 1024)
                        var bytesRead: Int
                        var lastEmitTime = 0L

                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead

                            val now = System.currentTimeMillis()
                            if (now - lastEmitTime > 150L || downloadedBytes == expectedLength) {
                                lastEmitTime = now
                                val progress = if (expectedLength > 0L) {
                                    (downloadedBytes.toFloat() / expectedLength.toFloat()).coerceIn(0f, 1f)
                                } else 0f
                                emit(UpdateUiState.Downloading(release, asset, progress, downloadedBytes, expectedLength))
                            }
                        }
                        output.flush()
                    }
                }
            }

            // Asenda ajutine fail lõpliku failiga
            if (tempFile.exists()) {
                if (targetFile.exists()) targetFile.delete()
                tempFile.renameTo(targetFile)
            }

            emit(UpdateUiState.ReadyToInstall(release, asset, targetFile))
        } catch (e: Exception) {
            if (tempFile.exists()) tempFile.delete()
            if (e is CancellationException) throw e
            Log.e(TAG, "Download failed", e)
            emit(UpdateUiState.Error(e.localizedMessage ?: "Allalaadimine ebaõnnestus"))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Kontrollib, kas äpil on luba paigaldada APK-sid (Android 8.0+).
     */
    fun canInstallPackages(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    /**
     * Suunab kasutaja süsteemiseadetesse "Luba tundmatutest allikatest".
     */
    fun openInstallPermissionSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    /**
     * Käivitab süsteemse APK paigalduse FileProvideri kaudu.
     */
    fun installApk(apkFile: File) {
        if (!apkFile.exists()) {
            Log.e(TAG, "Install failed: APK file does not exist at ${apkFile.absolutePath}")
            return
        }

        val apkUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun getTargetApkFile(tagName: String): File {
        val cleanTag = tagName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val updateDir = File(context.cacheDir, UPDATE_DIR_NAME)
        return File(updateDir, "Kellraadio-$cleanTag.apk")
    }
}
