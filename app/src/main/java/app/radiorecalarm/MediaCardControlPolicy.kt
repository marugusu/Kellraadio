package app.radiorecalarm

internal enum class MediaCardAction {
    RECORD_START,
    RECORD_STOP,
    SLEEP_TIMER,
    STOP_CLOSE
}

/**
 * Pure policy helpers for system media card actions and sleep timer cycling.
 */
internal object MediaCardControlPolicy {

    private const val STEP_MINUTES = 15
    private const val MAX_CYCLE_MINUTES = 60

    fun remainingSleepTimerMinutesCeil(remainingMillis: Long): Int {
        if (remainingMillis <= 0L) return 0
        return ((remainingMillis + 59_999L) / 60_000L).toInt()
    }

    /**
     * Cycles sleep timer duration in 15-minute increments: Off -> 15 -> 30 -> 45 -> 60 -> Off.
     */
    fun nextSleepTimerMinutes(remainingMillis: Long): Int {
        val currentMinutes = remainingSleepTimerMinutesCeil(remainingMillis)
        return when {
            currentMinutes <= 0 -> STEP_MINUTES
            currentMinutes <= 15 -> 30
            currentMinutes <= 30 -> 45
            currentMinutes <= MAX_CYCLE_MINUTES - STEP_MINUTES -> MAX_CYCLE_MINUTES
            else -> 0
        }
    }

    /**
     * Resolves the ordered custom actions for the system media card and expanded notification.
     * Alarm mode and muted background recording retain their dedicated layouts.
     */
    fun resolveCustomActions(
        isAlarmMode: Boolean,
        isMutedRecording: Boolean,
        isPlaying: Boolean,
        playWhenReady: Boolean,
        isLocalStream: Boolean,
        isRecording: Boolean
    ): List<MediaCardAction> {
        if (isAlarmMode) return emptyList()
        if (isRecording && isMutedRecording) return emptyList()
        if (!isPlaying && !playWhenReady) return listOf(MediaCardAction.STOP_CLOSE)
        if (isLocalStream) return listOf(MediaCardAction.SLEEP_TIMER)
        val recordAction = if (isRecording) MediaCardAction.RECORD_STOP else MediaCardAction.RECORD_START
        return listOf(recordAction, MediaCardAction.SLEEP_TIMER)
    }
}
