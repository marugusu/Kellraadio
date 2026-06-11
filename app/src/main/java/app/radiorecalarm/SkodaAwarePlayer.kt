package app.radiorecalarm

import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import java.util.concurrent.CopyOnWriteArraySet

/**
 * See on spetsiaalne mängija (Wrapper), mis lahendab Skoda/VW/Audi Bluetoothi probleemid.
 * Probleem: Autod arvavad, et striim on "Live" ja ei uuenda metaandmeid (laulu nime),
 * kui kestus on teadmata või 0.
 *
 * Lahendus: Me "valetame" autole, et see on 5-minutiline fail (300000ms) ja arvutame
 * ise jooksva aja (Fake Progress). See sunnib autot ekraani uuendama.
 */
@OptIn(UnstableApi::class)
class SkodaAwarePlayer(
    player: Player,
    private val internalListeners: CopyOnWriteArraySet<Player.Listener>
) : ForwardingPlayer(player) {

    private var positionAtPause: Long = 0L

    // Seda muutujat muudab RadioService, kui uus jaam algab
    var streamStartTime: Long = 0L
        set(value) {
            field = value
            // NULLIME ASUKOHA UUE JAAMA LAADIMISEL, 
            // et auto saaks aru: algas täiesti uus lugu!
            positionAtPause = 0L
        }

    override fun addListener(listener: Player.Listener) {
        internalListeners.add(listener)
        super.addListener(listener)
    }

    override fun removeListener(listener: Player.Listener) {
        internalListeners.remove(listener)
        super.removeListener(listener)
    }

    override fun getAvailableCommands(): Player.Commands {
        return super.getAvailableCommands().buildUpon()
            .add(Player.COMMAND_SEEK_TO_NEXT)
            .add(Player.COMMAND_SEEK_TO_PREVIOUS)
            .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
            .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            .add(Player.COMMAND_PLAY_PAUSE)
            .build()
    }

    override fun isCommandAvailable(command: Int): Boolean {
        return when (command) {
            Player.COMMAND_SEEK_TO_NEXT,
            Player.COMMAND_SEEK_TO_PREVIOUS,
            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
            Player.COMMAND_PLAY_PAUSE -> true
            else -> super.isCommandAvailable(command)
        }
    }

    // --- SKODA FIX 1: Fikseeritud kestus (5 minutit) ---
    override fun getDuration(): Long {
        if (isLocalPlayback()) {
            val realDuration = super.getDuration()
            if (realDuration != androidx.media3.common.C.TIME_UNSET) {
                return realDuration
            }
        }
        return 300000L
    }

    // --- SKODA FIX 2: Võlts-progress ---
    override fun getCurrentPosition(): Long {
        if (isLocalPlayback()) {
            return super.getCurrentPosition()
        }
        if (super.isPlaying()) {
            val elapsed = SystemClock.elapsedRealtime() - streamStartTime
            positionAtPause = if (streamStartTime > 0) elapsed % 300000L else 0L
            return positionAtPause
        }
        return positionAtPause
    }

    private fun isLocalPlayback(): Boolean {
        val uri = super.getCurrentMediaItem()?.localConfiguration?.uri ?: return false
        return uri.scheme == "file" || uri.scheme == "content"
    }

    override fun getMediaMetadata(): MediaMetadata {
        return super.getMediaMetadata()
    }
}
