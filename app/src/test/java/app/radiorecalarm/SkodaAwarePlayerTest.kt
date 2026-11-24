package app.radiorecalarm

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

class SkodaAwarePlayerTest {

    private class FakeDelegateState(
        var mediaItem: MediaItem,
        var durationMs: Long = 120_000L,
        var positionMs: Long = 45_000L
    ) {
        val seekCalls = mutableListOf<Long>()
        var seekBackCalls = 0
        var seekForwardCalls = 0
        var seekDefaultCalls = 0
    }

    private fun createDelegate(state: FakeDelegateState): Player {
        val fullCommands = Player.Commands.Builder()
            .addAllCommands()
            .build()

        return Proxy.newProxyInstance(
            Player::class.java.classLoader,
            arrayOf(Player::class.java)
        ) { _, method, args ->
            when (method.name) {
                "getCurrentMediaItem" -> state.mediaItem
                "getAvailableCommands" -> fullCommands
                "isCommandAvailable" -> true
                "getDuration" -> state.durationMs
                "getCurrentPosition" -> state.positionMs
                "isPlaying" -> true
                "seekTo" -> {
                    val pos = args?.lastOrNull() as? Long ?: 0L
                    state.seekCalls.add(pos)
                    Unit
                }
                "seekBack" -> {
                    state.seekBackCalls++
                    Unit
                }
                "seekForward" -> {
                    state.seekForwardCalls++
                    Unit
                }
                "seekToDefaultPosition" -> {
                    state.seekDefaultCalls++
                    Unit
                }
                else -> defaultValue(method.returnType)
            }
        } as Player
    }

    private fun defaultValue(returnType: Class<*>): Any? {
        return when (returnType) {
            Boolean::class.javaPrimitiveType -> false
            Int::class.javaPrimitiveType -> 0
            Long::class.javaPrimitiveType -> 0L
            Float::class.javaPrimitiveType -> 0f
            Double::class.javaPrimitiveType -> 0.0
            else -> null
        }
    }

    @Test
    fun `live stream blocks seek commands and preserves 5 minute Skoda duration`() {
        val state = FakeDelegateState(MediaItem.EMPTY)
        val wrapper = SkodaAwarePlayer(createDelegate(state), isLocalPlaybackProvider = { false })

        assertEquals(300_000L, wrapper.duration)
        assertFalse(wrapper.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM))
        assertFalse(wrapper.isCommandAvailable(Player.COMMAND_SEEK_BACK))
        assertFalse(wrapper.isCommandAvailable(Player.COMMAND_SEEK_FORWARD))
        assertFalse(wrapper.isCommandAvailable(Player.COMMAND_GET_TIMELINE))

        wrapper.seekTo(12_000L)
        wrapper.seekTo(0, 24_000L)
        wrapper.seekBack()
        wrapper.seekForward()

        assertTrue(state.seekCalls.isEmpty())
        assertEquals(0, state.seekBackCalls)
        assertEquals(0, state.seekForwardCalls)

        // Internal reconnects still use seekToDefaultPosition()
        wrapper.seekToDefaultPosition()
        assertEquals(1, state.seekDefaultCalls)
    }

    @Test
    fun `local recording preserves real duration and seeking`() {
        val state = FakeDelegateState(
            mediaItem = MediaItem.EMPTY,
            durationMs = 180_000L,
            positionMs = 30_000L
        )
        val wrapper = SkodaAwarePlayer(createDelegate(state), isLocalPlaybackProvider = { true })

        assertEquals(180_000L, wrapper.duration)
        assertEquals(30_000L, wrapper.currentPosition)
        assertTrue(wrapper.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM))
        assertTrue(wrapper.isCommandAvailable(Player.COMMAND_SEEK_BACK))
        assertTrue(wrapper.isCommandAvailable(Player.COMMAND_SEEK_FORWARD))
        assertFalse(wrapper.isCommandAvailable(Player.COMMAND_GET_TIMELINE))

        wrapper.seekTo(60_000L)
        wrapper.seekBack()
        wrapper.seekForward()

        assertEquals(listOf(60_000L), state.seekCalls)
        assertEquals(1, state.seekBackCalls)
        assertEquals(1, state.seekForwardCalls)
    }
}
