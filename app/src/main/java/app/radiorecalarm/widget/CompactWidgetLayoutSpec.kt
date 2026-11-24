package app.radiorecalarm.widget

enum class CompactWidgetLayoutTier {
    MINI_2X1,
    WIDE_BAR,
    FULL_BAR,
    TALL_COMPACT,
    EXPANDED_CARD
}

data class CompactWidgetLayoutConfig(
    val tier: CompactWidgetLayoutTier,
    val showArtwork: Boolean,
    val artworkSizeDp: Int,
    val showPrevNextButtons: Boolean,
    val showRecordButton: Boolean,
    val showRecordingDot: Boolean,
    val showStatusLine: Boolean,
    val showSecondaryLine: Boolean,
    val showAlarmLine: Boolean,
    val titleMaxLines: Int,
    val controlButtonSizeDp: Int,
    val controlIconSizeDp: Int
)

data class CompactWidgetTextLines(
    val primaryLine: String,
    val secondaryLine: String,
    val statusLine: String
)

object CompactWidgetLayoutSpec {

    fun resolve(
        widthDp: Float,
        heightDp: Float,
        isPlaying: Boolean,
        isRecording: Boolean,
        isLocalStream: Boolean,
        hasAlarm: Boolean,
        hasStatus: Boolean
    ): CompactWidgetLayoutConfig {
        val isTall = heightDp >= 105f
        val canRecord = !isLocalStream && (isPlaying || isRecording)

        if (!isTall) {
            return when {
                widthDp < 180f -> CompactWidgetLayoutConfig(
                    tier = CompactWidgetLayoutTier.MINI_2X1,
                    showArtwork = false,
                    artworkSizeDp = 0,
                    showPrevNextButtons = false,
                    showRecordButton = false,
                    showRecordingDot = isRecording,
                    showStatusLine = false,
                    showSecondaryLine = false,
                    showAlarmLine = false,
                    titleMaxLines = 1,
                    controlButtonSizeDp = 40,
                    controlIconSizeDp = 28
                )
                widthDp < 245f -> {
                    val showSkip = widthDp >= 220f
                    CompactWidgetLayoutConfig(
                        tier = CompactWidgetLayoutTier.WIDE_BAR,
                        showArtwork = true,
                        artworkSizeDp = 36,
                        showPrevNextButtons = showSkip,
                        showRecordButton = false,
                        showRecordingDot = isRecording,
                        showStatusLine = false,
                        showSecondaryLine = false,
                        showAlarmLine = false,
                        titleMaxLines = 1,
                        controlButtonSizeDp = if (showSkip) 34 else 40,
                        controlIconSizeDp = if (showSkip) 24 else 28
                    )
                }
                else -> CompactWidgetLayoutConfig(
                    tier = CompactWidgetLayoutTier.FULL_BAR,
                    showArtwork = true,
                    artworkSizeDp = 42,
                    showPrevNextButtons = true,
                    showRecordButton = canRecord && widthDp >= 275f,
                    showRecordingDot = isRecording && !(canRecord && widthDp >= 275f),
                    showStatusLine = false,
                    showSecondaryLine = heightDp >= 74f,
                    showAlarmLine = false,
                    titleMaxLines = 1,
                    controlButtonSizeDp = 36,
                    controlIconSizeDp = 24
                )
            }
        }

        if (widthDp < 210f) {
            val showRecBtn = canRecord && widthDp >= 175f
            return CompactWidgetLayoutConfig(
                tier = CompactWidgetLayoutTier.TALL_COMPACT,
                showArtwork = true,
                artworkSizeDp = 36,
                showPrevNextButtons = true,
                showRecordButton = showRecBtn,
                showRecordingDot = isRecording && !showRecBtn,
                showStatusLine = false,
                showSecondaryLine = heightDp >= 135f,
                showAlarmLine = hasAlarm && heightDp >= 145f,
                titleMaxLines = 1,
                controlButtonSizeDp = 36,
                controlIconSizeDp = 24
            )
        }

        val largeHeight = heightDp >= 160f
        return CompactWidgetLayoutConfig(
            tier = CompactWidgetLayoutTier.EXPANDED_CARD,
            showArtwork = true,
            artworkSizeDp = if (largeHeight) 64 else 52,
            showPrevNextButtons = true,
            showRecordButton = canRecord,
            showRecordingDot = false,
            showStatusLine = hasStatus,
            showSecondaryLine = true,
            showAlarmLine = hasAlarm,
            titleMaxLines = if (largeHeight) 2 else 1,
            controlButtonSizeDp = 42,
            controlIconSizeDp = 28
        )
    }

    fun formatTextLines(
        stationName: String,
        title: String,
        artist: String,
        extra: String,
        status: String,
        bitrate: String,
        isPlaying: Boolean,
        isRecording: Boolean,
        liveBroadcastLabel: String
    ): CompactWidgetTextLines {
        val cleanTitle = title.trim()
        val cleanArtist = artist.trim()
        val cleanExtra = extra.trim()

        val filteredArtist = if (
            cleanArtist.isBlank() ||
            cleanArtist.equals("Otseeeter", ignoreCase = true) ||
            (liveBroadcastLabel.isNotBlank() && cleanArtist.equals(liveBroadcastLabel, ignoreCase = true))
        ) "" else cleanArtist

        val statusLine = if (bitrate.isNotBlank() && isPlaying) {
            "$status • $bitrate"
        } else {
            status
        }

        if (!isPlaying && !isRecording) {
            val stoppedDetail = when {
                cleanTitle.isNotBlank() && !cleanTitle.equals(stationName.trim(), ignoreCase = true) -> cleanTitle
                else -> ""
            }
            return CompactWidgetTextLines(
                primaryLine = statusLine.ifBlank { "..." },
                secondaryLine = stoppedDetail,
                statusLine = statusLine
            )
        }

        val primary = when {
            cleanTitle.isNotBlank() && !cleanTitle.equals(stationName.trim(), ignoreCase = true) -> cleanTitle
            filteredArtist.isNotBlank() -> filteredArtist
            cleanExtra.isNotBlank() -> cleanExtra
            else -> statusLine.ifBlank { "..." }
        }

        val secondary = when {
            filteredArtist.isNotBlank() && !filteredArtist.equals(primary, ignoreCase = true) -> filteredArtist
            cleanExtra.isNotBlank() && !cleanExtra.equals(primary, ignoreCase = true) -> cleanExtra
            else -> ""
        }

        return CompactWidgetTextLines(
            primaryLine = primary,
            secondaryLine = secondary,
            statusLine = statusLine
        )
    }
}
