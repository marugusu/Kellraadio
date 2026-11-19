package app.radiorecalarm

import android.media.AudioManager

/**
 * Pure state machine governing how audio interruptions (Bluetooth/headphone disconnect,
 * phone calls, and competing media apps) affect playback vs active stream recording (ADR-0006).
 */
class RecordingInterruptionPolicy {

    enum class MuteReason {
        NOISY,
        FOCUS_LOSS,
        CALL
    }

    sealed class InterruptionAction {
        data object None : InterruptionAction()
        data class SetVolume(val volume: Float, val isMutedRecording: Boolean) : InterruptionAction()
        data class PausePlayback(val invalidateSocket: Boolean) : InterruptionAction()
        data object SuppressForCall : InterruptionAction()
        data class ResumeAfterCall(val restorePlayWhenReady: Boolean) : InterruptionAction()
        data object UnmuteAfterCall : InterruptionAction()
    }

    var muteReason: MuteReason? = null
        private set

    var isDucked: Boolean = false
        private set

    var wasSuppressedByTransientLoss: Boolean = false
        private set

    var wasNoisyDuringCall: Boolean = false
        private set

    val isMutedRecording: Boolean
        get() = muteReason != null

    fun onAudioBecomingNoisy(isRecording: Boolean): InterruptionAction {
        if (isRecording) {
            // Upgrade CALL -> NOISY if output disconnected during a call, so call end won't unmute on speaker
            if (muteReason == null || muteReason == MuteReason.CALL) {
                muteReason = MuteReason.NOISY
            }
            isDucked = false
            return InterruptionAction.SetVolume(volume = 0f, isMutedRecording = true)
        }

        if (wasSuppressedByTransientLoss) {
            wasNoisyDuringCall = true
            return InterruptionAction.PausePlayback(invalidateSocket = false)
        }
        return InterruptionAction.PausePlayback(invalidateSocket = true)
    }

    fun onAudioFocusChange(focusChange: Int, isRecording: Boolean): InterruptionAction {
        return when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                if (isRecording) {
                    if (muteReason != MuteReason.NOISY) {
                        muteReason = MuteReason.FOCUS_LOSS
                    }
                    isDucked = false
                    InterruptionAction.SetVolume(volume = 0f, isMutedRecording = true)
                } else {
                    wasSuppressedByTransientLoss = false
                    wasNoisyDuringCall = false
                    isDucked = false
                    InterruptionAction.PausePlayback(invalidateSocket = true)
                }
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                if (isRecording) {
                    if (muteReason == null) {
                        muteReason = MuteReason.CALL
                    }
                    isDucked = false
                    InterruptionAction.SetVolume(volume = 0f, isMutedRecording = true)
                } else {
                    wasSuppressedByTransientLoss = true
                    wasNoisyDuringCall = false
                    isDucked = false
                    InterruptionAction.SuppressForCall
                }
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                if (muteReason == null) {
                    isDucked = true
                    InterruptionAction.SetVolume(volume = 0.2f, isMutedRecording = false)
                } else {
                    InterruptionAction.None
                }
            }

            AudioManager.AUDIOFOCUS_GAIN -> {
                if (isRecording) {
                    when (muteReason) {
                        MuteReason.CALL -> {
                            muteReason = null
                            isDucked = false
                            InterruptionAction.UnmuteAfterCall
                        }
                        MuteReason.NOISY, MuteReason.FOCUS_LOSS -> {
                            // Remain muted until user explicitly taps Play/Unmute
                            InterruptionAction.None
                        }
                        null -> {
                            if (isDucked) {
                                isDucked = false
                                InterruptionAction.SetVolume(volume = 1.0f, isMutedRecording = false)
                            } else {
                                InterruptionAction.None
                            }
                        }
                    }
                } else if (wasSuppressedByTransientLoss) {
                    val restorePlayWhenReady = wasNoisyDuringCall
                    wasSuppressedByTransientLoss = false
                    wasNoisyDuringCall = false
                    isDucked = false
                    InterruptionAction.ResumeAfterCall(restorePlayWhenReady = restorePlayWhenReady)
                } else if (isDucked) {
                    isDucked = false
                    InterruptionAction.SetVolume(volume = 1.0f, isMutedRecording = false)
                } else {
                    InterruptionAction.None
                }
            }

            else -> InterruptionAction.None
        }
    }

    fun onUserUnmuteOrResume(): InterruptionAction {
        val wasMuted = muteReason != null || isDucked
        muteReason = null
        isDucked = false
        wasSuppressedByTransientLoss = false
        wasNoisyDuringCall = false
        return if (wasMuted) {
            InterruptionAction.SetVolume(volume = 1.0f, isMutedRecording = false)
        } else {
            InterruptionAction.None
        }
    }

    fun onRecordingStopped(): InterruptionAction {
        val wasMuted = muteReason != null
        muteReason = null
        isDucked = false
        return if (wasMuted) {
            InterruptionAction.PausePlayback(invalidateSocket = true)
        } else {
            InterruptionAction.None
        }
    }

    fun onUserPauseOrStop() {
        muteReason = null
        isDucked = false
        wasSuppressedByTransientLoss = false
        wasNoisyDuringCall = false
    }
}
