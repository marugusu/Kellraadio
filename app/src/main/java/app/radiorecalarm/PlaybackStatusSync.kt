package app.radiorecalarm

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.mp3.Mp3Extractor
import androidx.media3.extractor.ts.AdtsExtractor
import app.radiorecalarm.ui.MainUiState

internal enum class PlaybackStatusAction {
    NONE,
    PAUSED,
    STOPPED
}

internal data class ServicePlaybackStatusSnapshot(
    val isLocal: Boolean,
    val sendStationChanged: Boolean,
    val isPlaying: Boolean,
    val sendBitrateAndTimer: Boolean,
    val sendPlaybackProgress: Boolean,
    val terminalAction: PlaybackStatusAction
)

/**
 * Pure state-sync helpers for keeping local recording playback state intact across
 * activity recreations (e.g. portrait <-> landscape rotation) and ACTION_GET_STATUS queries.
 */
internal object PlaybackStatusSync {

    fun isLocalStreamUri(url: String): Boolean =
        url.startsWith("file://") || url.startsWith("file:/")

    fun resolveServiceStatus(
        stationName: String,
        streamUrl: String,
        isPlayerInitialized: Boolean,
        isPlaying: Boolean,
        hasLoadedMedia: Boolean
    ): ServicePlaybackStatusSnapshot {
        val isLocal = isLocalStreamUri(streamUrl)
        val hasNamedStation = stationName != "Radio" && stationName.isNotEmpty()
        val isActivePlaying = isPlayerInitialized && isPlaying
        val isPausedLocal = !isActivePlaying && isLocal && hasLoadedMedia

        val terminalAction = when {
            isActivePlaying -> PlaybackStatusAction.NONE
            isPausedLocal -> PlaybackStatusAction.PAUSED
            else -> PlaybackStatusAction.STOPPED
        }

        val sendStationChanged = when {
            isLocal -> isActivePlaying || isPausedLocal
            else -> hasNamedStation
        }

        return ServicePlaybackStatusSnapshot(
            isLocal = isLocal,
            sendStationChanged = sendStationChanged,
            isPlaying = isActivePlaying,
            sendBitrateAndTimer = isActivePlaying,
            sendPlaybackProgress = isLocal && (isActivePlaying || isPausedLocal),
            terminalAction = terminalAction
        )
    }

    fun applyLocalStationChanged(
        current: MainUiState,
        stationName: String,
        streamUrl: String?,
        isPlaying: Boolean,
        playingStatusText: String,
        pausedStatusText: String
    ): MainUiState {
        val resolvedUrl = streamUrl?.takeIf { it.isNotBlank() } ?: current.activeStreamUrl
        return current.copy(
            isPlaying = isPlaying,
            playerStatus = if (isPlaying) playingStatusText else pausedStatusText,
            activeStationName = stationName.ifBlank { current.activeStationName },
            activeStreamUrl = resolvedUrl,
            selectedStationId = -1
        )
    }

    fun clampSeekPosition(positionMs: Long, durationMs: Long): Long {
        return if (durationMs > 0L) {
            positionMs.coerceIn(0L, durationMs)
        } else {
            positionMs.coerceAtLeast(0L)
        }
    }

    fun resolveEffectiveDurationMs(playbackDurationMs: Long, metadataDurationSeconds: Long): Long {
        return if (playbackDurationMs > 0L) {
            playbackDurationMs
        } else {
            (metadataDurationSeconds * 1000L).coerceAtLeast(0L)
        }
    }

    @OptIn(UnstableApi::class)
    const val ADTS_EXTRACTOR_FLAGS: Int = AdtsExtractor.FLAG_ENABLE_CONSTANT_BITRATE_SEEKING

    @OptIn(UnstableApi::class)
    const val MP3_EXTRACTOR_FLAGS: Int = Mp3Extractor.FLAG_ENABLE_CONSTANT_BITRATE_SEEKING

    /**
     * Enables constant-bitrate seeking for local raw ADTS (.aac) and headerless (.mp3) recordings
     * so ExoPlayer reports real durations and supports seekTo() instead of jumping to 0.
     * Live streams (unknown content length) remain unseekable.
     */
    @OptIn(UnstableApi::class)
    fun createExtractorsFactory(): DefaultExtractorsFactory {
        return DefaultExtractorsFactory()
            .setConstantBitrateSeekingEnabled(true)
            .setAdtsExtractorFlags(ADTS_EXTRACTOR_FLAGS)
            .setMp3ExtractorFlags(MP3_EXTRACTOR_FLAGS)
    }
}
