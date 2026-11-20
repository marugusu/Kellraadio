package app.radiorecalarm

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
}
