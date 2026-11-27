package app.radiorecalarm

import android.media.AudioManager
import app.radiorecalarm.RecordingInterruptionPolicy.InterruptionAction
import app.radiorecalarm.RecordingInterruptionPolicy.MuteReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RecordingInterruptionPolicyTest {

    private lateinit var policy: RecordingInterruptionPolicy

    @Before
    fun setup() {
        policy = RecordingInterruptionPolicy()
    }

    @Test
    fun `noisy event while not recording pauses playback and invalidates socket`() {
        val action = policy.onAudioBecomingNoisy(isRecording = false, isPlaybackActive = true)

        assertEquals(
            InterruptionAction.PausePlayback(invalidateSocket = true, abandonAudioFocus = true),
            action
        )
        assertFalse(policy.isMutedRecording)
    }

    @Test
    fun `noisy event while recording mutes playback without pausing and stays muted until user unmutes`() {
        val action = policy.onAudioBecomingNoisy(isRecording = true)

        assertEquals(InterruptionAction.SetVolume(0f, isMutedRecording = true), action)
        assertTrue(policy.isMutedRecording)
        assertEquals(MuteReason.NOISY, policy.muteReason)

        // Focus gain alone must not unmute after Bluetooth/headphone disconnect
        val gainAction = policy.onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN, isRecording = true)
        assertEquals(InterruptionAction.None, gainAction)
        assertTrue(policy.isMutedRecording)

        val unmuteAction = policy.onUserUnmuteOrResume()
        assertEquals(InterruptionAction.SetVolume(1.0f, isMutedRecording = false), unmuteAction)
        assertFalse(policy.isMutedRecording)
    }

    @Test
    fun `permanent focus loss while recording mutes playback and stays muted until user unmutes`() {
        val action = policy.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS, isRecording = true)

        assertEquals(InterruptionAction.SetVolume(0f, isMutedRecording = true), action)
        assertEquals(MuteReason.FOCUS_LOSS, policy.muteReason)

        assertEquals(InterruptionAction.None, policy.onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN, isRecording = true))
        assertTrue(policy.isMutedRecording)
    }

    @Test
    fun `permanent focus loss while not recording pauses playback`() {
        val action = policy.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS, isRecording = false)

        assertEquals(InterruptionAction.PausePlayback(invalidateSocket = true), action)
        assertFalse(policy.isMutedRecording)
    }

    @Test
    fun `transient call loss while recording mutes playback and auto-unmutes when call ends`() {
        val callStart = policy.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT, isRecording = true)
        assertEquals(InterruptionAction.SetVolume(0f, isMutedRecording = true), callStart)
        assertEquals(MuteReason.CALL, policy.muteReason)

        val callEnd = policy.onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN, isRecording = true)
        assertEquals(InterruptionAction.UnmuteAfterCall, callEnd)
        assertFalse(policy.isMutedRecording)
        assertNull(policy.muteReason)
    }

    @Test
    fun `noisy disconnect during call while recording prevents auto-unmute when call ends`() {
        policy.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT, isRecording = true)
        assertEquals(MuteReason.CALL, policy.muteReason)

        policy.onAudioBecomingNoisy(isRecording = true)
        assertEquals(MuteReason.NOISY, policy.muteReason)

        val callEnd = policy.onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN, isRecording = true)
        assertEquals(InterruptionAction.None, callEnd)
        assertTrue(policy.isMutedRecording)

        val userResume = policy.onUserUnmuteOrResume()
        assertEquals(InterruptionAction.SetVolume(1.0f, isMutedRecording = false), userResume)
        assertFalse(policy.isMutedRecording)
        assertNull(policy.muteReason)
    }

    @Test
    fun `transient call loss while not recording preserves suppression and recovery`() {
        val callStart = policy.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT, isRecording = false)
        assertEquals(InterruptionAction.SuppressForCall, callStart)
        assertTrue(policy.wasSuppressedByTransientLoss)

        policy.onAudioBecomingNoisy(isRecording = false)
        assertTrue(policy.wasNoisyDuringCall)

        val callEnd = policy.onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN, isRecording = false)
        assertEquals(InterruptionAction.ResumeAfterCall(restorePlayWhenReady = true), callEnd)
        assertFalse(policy.wasSuppressedByTransientLoss)
        assertFalse(policy.wasNoisyDuringCall)
    }

    @Test
    fun `stopping recording while muted by interruption pauses silent background playback`() {
        policy.onAudioBecomingNoisy(isRecording = true)
        assertTrue(policy.isMutedRecording)

        val stopAction = policy.onRecordingStopped()
        assertEquals(InterruptionAction.PausePlayback(invalidateSocket = true), stopAction)
        assertFalse(policy.isMutedRecording)
    }

    @Test
    fun `stopping recording while audible does not pause playback`() {
        val stopAction = policy.onRecordingStopped()
        assertEquals(InterruptionAction.None, stopAction)
    }

    @Test
    fun `ducking lowers volume and restores it on focus gain when not muted`() {
        val duck = policy.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK, isRecording = true)
        assertEquals(InterruptionAction.SetVolume(0.2f, isMutedRecording = false), duck)
        assertTrue(policy.isDucked)

        val restore = policy.onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN, isRecording = true)
        assertEquals(InterruptionAction.SetVolume(1.0f, isMutedRecording = false), restore)
        assertFalse(policy.isDucked)
    }

    @Test
    fun `transient call loss and gain while paused after noisy disconnect does not resume playback`() {
        val noisyPause = policy.onAudioBecomingNoisy(isRecording = false, isPlaybackActive = true)
        assertEquals(
            InterruptionAction.PausePlayback(invalidateSocket = true, abandonAudioFocus = true),
            noisyPause
        )

        val callStart = policy.onAudioFocusChange(
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            isRecording = false,
            isPlaybackActive = false
        )
        assertEquals(InterruptionAction.None, callStart)
        assertFalse(policy.wasSuppressedByTransientLoss)

        val callEnd = policy.onAudioFocusChange(
            AudioManager.AUDIOFOCUS_GAIN,
            isRecording = false,
            isPlaybackActive = false
        )
        assertEquals(InterruptionAction.None, callEnd)
        assertFalse(policy.wasSuppressedByTransientLoss)
    }

    @Test
    fun `noisy, duck, and focus loss while paused and not recording are ignored`() {
        assertEquals(
            InterruptionAction.None,
            policy.onAudioBecomingNoisy(isRecording = false, isPlaybackActive = false)
        )
        assertEquals(
            InterruptionAction.None,
            policy.onAudioFocusChange(
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK,
                isRecording = false,
                isPlaybackActive = false
            )
        )
        assertFalse(policy.isDucked)
        assertEquals(
            InterruptionAction.None,
            policy.onAudioFocusChange(
                AudioManager.AUDIOFOCUS_LOSS,
                isRecording = false,
                isPlaybackActive = false
            )
        )
    }

    @Test
    fun `stopping muted recording abandons audio focus`() {
        policy.onAudioBecomingNoisy(isRecording = true, isPlaybackActive = true)
        val stopAction = policy.onRecordingStopped()
        assertEquals(
            InterruptionAction.PausePlayback(invalidateSocket = true, abandonAudioFocus = true),
            stopAction
        )
    }
}
