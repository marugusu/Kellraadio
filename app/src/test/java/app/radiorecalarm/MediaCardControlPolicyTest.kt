package app.radiorecalarm

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaCardControlPolicyTest {

    @Test
    fun `nextSleepTimerMinutes cycles off through 60 minutes in 15 minute steps`() {
        assertEquals(15, MediaCardControlPolicy.nextSleepTimerMinutes(0L))
        assertEquals(15, MediaCardControlPolicy.nextSleepTimerMinutes(-1000L))
        assertEquals(30, MediaCardControlPolicy.nextSleepTimerMinutes(15 * 60_000L))
        assertEquals(30, MediaCardControlPolicy.nextSleepTimerMinutes(13 * 60_000L))
        assertEquals(45, MediaCardControlPolicy.nextSleepTimerMinutes(30 * 60_000L))
        assertEquals(45, MediaCardControlPolicy.nextSleepTimerMinutes(28 * 60_000L))
        assertEquals(60, MediaCardControlPolicy.nextSleepTimerMinutes(45 * 60_000L))
        assertEquals(60, MediaCardControlPolicy.nextSleepTimerMinutes(43 * 60_000L))
        assertEquals(0, MediaCardControlPolicy.nextSleepTimerMinutes(60 * 60_000L))
        assertEquals(0, MediaCardControlPolicy.nextSleepTimerMinutes(58 * 60_000L))
    }

    @Test
    fun `remainingSleepTimerMinutesCeil rounds partial minutes up`() {
        assertEquals(0, MediaCardControlPolicy.remainingSleepTimerMinutesCeil(0L))
        assertEquals(1, MediaCardControlPolicy.remainingSleepTimerMinutesCeil(1_000L))
        assertEquals(15, MediaCardControlPolicy.remainingSleepTimerMinutesCeil(14 * 60_000L + 1_000L))
        assertEquals(15, MediaCardControlPolicy.remainingSleepTimerMinutesCeil(15 * 60_000L))
    }

    @Test
    fun `alarm mode and muted recording return no custom actions`() {
        assertEquals(
            emptyList<MediaCardAction>(),
            MediaCardControlPolicy.resolveCustomActions(
                isAlarmMode = true,
                isMutedRecording = false,
                isPlaying = true,
                playWhenReady = true,
                isLocalStream = false,
                isRecording = false
            )
        )
        assertEquals(
            emptyList<MediaCardAction>(),
            MediaCardControlPolicy.resolveCustomActions(
                isAlarmMode = false,
                isMutedRecording = true,
                isPlaying = false,
                playWhenReady = false,
                isLocalStream = false,
                isRecording = true
            )
        )
    }

    @Test
    fun `paused normal playback returns close stop action`() {
        assertEquals(
            listOf(MediaCardAction.STOP_CLOSE),
            MediaCardControlPolicy.resolveCustomActions(
                isAlarmMode = false,
                isMutedRecording = false,
                isPlaying = false,
                playWhenReady = false,
                isLocalStream = false,
                isRecording = false
            )
        )
    }

    @Test
    fun `active live stream returns record toggle followed by sleep timer`() {
        assertEquals(
            listOf(MediaCardAction.RECORD_START, MediaCardAction.SLEEP_TIMER),
            MediaCardControlPolicy.resolveCustomActions(
                isAlarmMode = false,
                isMutedRecording = false,
                isPlaying = true,
                playWhenReady = true,
                isLocalStream = false,
                isRecording = false
            )
        )
        assertEquals(
            listOf(MediaCardAction.RECORD_STOP, MediaCardAction.SLEEP_TIMER),
            MediaCardControlPolicy.resolveCustomActions(
                isAlarmMode = false,
                isMutedRecording = false,
                isPlaying = true,
                playWhenReady = true,
                isLocalStream = false,
                isRecording = true
            )
        )
    }

    @Test
    fun `active local recording returns sleep timer only`() {
        assertEquals(
            listOf(MediaCardAction.SLEEP_TIMER),
            MediaCardControlPolicy.resolveCustomActions(
                isAlarmMode = false,
                isMutedRecording = false,
                isPlaying = true,
                playWhenReady = true,
                isLocalStream = true,
                isRecording = false
            )
        )
    }
}
