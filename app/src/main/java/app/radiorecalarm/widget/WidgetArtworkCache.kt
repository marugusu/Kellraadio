package app.radiorecalarm.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import app.radiorecalarm.AppDatabase
import app.radiorecalarm.ui.StationArtworkUtils
import java.io.File
import java.io.FileOutputStream

object WidgetArtworkCache {
    private const val TAG = "WidgetArtworkCache"
    private const val ARTWORK_SIZE_PX = 160
    private const val ARTWORK_FILE = "compact_widget_art.png"
    private const val ARTWORK_KEY_FILE = "compact_widget_art_key.txt"

    @Volatile
    private var cachedKey: String? = null

    @Volatile
    private var cachedBitmap: Bitmap? = null

    suspend fun prepareArtwork(
        context: Context,
        stationName: String,
        streamUrl: String,
        isLocalStream: Boolean
    ): String {
        val normalizedStation = stationName.trim().ifBlank { "Radio" }
        val key = if (isLocalStream) {
            "v2|local|$normalizedStation"
        } else {
            "v2|$normalizedStation|${streamUrl.trim()}"
        }

        if (cachedKey == key && cachedBitmap != null) {
            return key
        }

        val diskKeyFile = File(context.cacheDir, ARTWORK_KEY_FILE)
        val diskArtFile = File(context.cacheDir, ARTWORK_FILE)
        if (diskKeyFile.exists() && diskArtFile.exists()) {
            val savedKey = runCatching { diskKeyFile.readText() }.getOrNull()
            if (savedKey == key) {
                val decoded = runCatching { BitmapFactory.decodeFile(diskArtFile.absolutePath) }.getOrNull()
                if (decoded != null) {
                    cachedKey = key
                    cachedBitmap = decoded
                    return key
                }
            }
        }

        val loaded = if (isLocalStream) {
            StationArtworkUtils.generateDarkStationBitmap(normalizedStation)
        } else {
            val iconUrl = resolveStationIconUrl(context, normalizedStation, streamUrl)
            StationArtworkUtils.loadStationNotificationBitmap(
                context = context,
                stationName = normalizedStation,
                iconUrl = iconUrl
            )
        }

        val scaled = scaleForWidget(loaded)
        cachedKey = key
        cachedBitmap = scaled
        persistToDisk(diskArtFile, diskKeyFile, key, scaled)
        return key
    }

    fun getCachedOrFallback(context: Context, artworkKey: String, stationName: String): Bitmap {
        if (artworkKey.isNotBlank() && cachedKey == artworkKey) {
            cachedBitmap?.let { return it }
        }

        val diskKeyFile = File(context.cacheDir, ARTWORK_KEY_FILE)
        val diskArtFile = File(context.cacheDir, ARTWORK_FILE)
        if (artworkKey.isNotBlank() && diskKeyFile.exists() && diskArtFile.exists()) {
            val savedKey = runCatching { diskKeyFile.readText() }.getOrNull()
            if (savedKey == artworkKey) {
                val decoded = runCatching { BitmapFactory.decodeFile(diskArtFile.absolutePath) }.getOrNull()
                if (decoded != null) {
                    cachedKey = artworkKey
                    cachedBitmap = decoded
                    return decoded
                }
            }
        }

        return scaleForWidget(StationArtworkUtils.generateDarkStationBitmap(stationName.ifBlank { "Radio" }))
    }

    private suspend fun resolveStationIconUrl(
        context: Context,
        stationName: String,
        streamUrl: String
    ): String? {
        return try {
            val dao = AppDatabase.getDatabase(context).radioStationDao()
            val byName = dao.getStationByName(stationName)
            if (!byName?.iconUrl.isNullOrBlank()) {
                byName?.iconUrl
            } else if (streamUrl.isNotBlank()) {
                dao.getAllActiveStationsSync().firstOrNull { it.url == streamUrl }?.iconUrl
            } else {
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Jaama logo URL-i lugemine ebaõnnestus: ${e.message}")
            null
        }
    }

    private fun scaleForWidget(source: Bitmap): Bitmap {
        if (source.width == ARTWORK_SIZE_PX && source.height == ARTWORK_SIZE_PX) return source
        return Bitmap.createScaledBitmap(source, ARTWORK_SIZE_PX, ARTWORK_SIZE_PX, true)
    }

    private fun persistToDisk(artFile: File, keyFile: File, key: String, bitmap: Bitmap) {
        try {
            FileOutputStream(artFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
            }
            keyFile.writeText(key)
        } catch (e: Exception) {
            Log.w(TAG, "Vidina pildi vahemällu salvestamine ebaõnnestus: ${e.message}")
        }
    }
}
