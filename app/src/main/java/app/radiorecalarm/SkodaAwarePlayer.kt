package app.radiorecalarm

import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Spetsiaalne mängija (ForwardingPlayer wrapper) raadiovoo ja auto (sh Skoda/VW/Audi) Bluetoothi toeks.
 *
 * 1. Metaandmete dünaamiline uuendamine:
 *    ExoPlayer ei võimalda otse reaalajas striimi keskel MediaItemi metaandmeid muuta ilma striimi taaskäivitamata.
 *    See wrapper kirjutab üle meetodi `getMediaMetadata()` ja teavitab kuulajaid puhta `onMediaMetadataChanged` kaudu.
 *
 * 2. Bluetooth AVRCP sünkroonimise parandus:
 *    Eemaldame `COMMAND_GET_TIMELINE`, mis keelab MediaSessionil 1-elemendilise vananenud esitusjärjekorra (Queue)
 *    loomise. See kaotab Androidi Bluetooth AVRCP pinus (`MediaPlayerWrapper.java`) tekkinud 2000 ms viivituse.
 *
 * 3. Roolinuppude (Next / Previous) delegeerimine:
 *    Delegeerib käsud `seekToNext` ja `seekToPrevious` otse raadiojaama vahetusele, vältides ExoPlayeri
 *    vaikimisi käitumist, mis teeks reaalajas voole seek(0) ja rikuks puhvri.
 *
 * 4. Pardaarvuti progressi ja režiimi tugi:
 *    Raadiovoo puhul tagastatakse kindel kestus (300000ms ehk 5 min) ja jooksva aja arvutus, et auto pardaarvuti
 *    (eriti VAG MIB2/MIB3) ei lülituks "tühja AUX režiimi", kus lauluinfo uuendamine keelatakse.
 *    Salvestiste (kohalikud failid) puhul kasutatakse reaalset faili kestust ja positsiooni.
 */
@OptIn(UnstableApi::class)
class SkodaAwarePlayer(
    player: Player,
    private val internalListeners: CopyOnWriteArraySet<Player.Listener> = CopyOnWriteArraySet()
) : ForwardingPlayer(player) {

    private var positionAtPause: Long = 0L

    var onSkipNext: (() -> Unit)? = null
    var onSkipPrevious: (() -> Unit)? = null

    // Seda muutujat muudab RadioService, kui uus jaam algab
    var streamStartTime: Long = 0L
        set(value) {
            field = value
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
            // Eemaldame GET_TIMELINE, et MediaSession ei looks vale Queue'd,
            // mis viib Androidi Bluetooth AVRCP pinus 2-sekundilise viivituseni.
            .remove(Player.COMMAND_GET_TIMELINE)
            .add(Player.COMMAND_SEEK_TO_NEXT)
            .add(Player.COMMAND_SEEK_TO_PREVIOUS)
            .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
            .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            .add(Player.COMMAND_PLAY_PAUSE)
            .build()
    }

    override fun isCommandAvailable(command: Int): Boolean {
        return when (command) {
            Player.COMMAND_GET_TIMELINE -> false
            Player.COMMAND_SEEK_TO_NEXT,
            Player.COMMAND_SEEK_TO_PREVIOUS,
            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
            Player.COMMAND_PLAY_PAUSE -> true
            else -> super.isCommandAvailable(command)
        }
    }

    override fun seekToNext() {
        if (onSkipNext != null) {
            onSkipNext?.invoke()
        } else {
            super.seekToNext()
        }
    }

    override fun seekToPrevious() {
        if (onSkipPrevious != null) {
            onSkipPrevious?.invoke()
        } else {
            super.seekToPrevious()
        }
    }

    override fun seekToNextMediaItem() {
        if (onSkipNext != null) {
            onSkipNext?.invoke()
        } else {
            super.seekToNextMediaItem()
        }
    }

    override fun seekToPreviousMediaItem() {
        if (onSkipPrevious != null) {
            onSkipPrevious?.invoke()
        } else {
            super.seekToPreviousMediaItem()
        }
    }

    // --- Kestus: 5 minutit raadiovoole, reaalne kestus kohalikule failile ---
    override fun getDuration(): Long {
        if (isLocalPlayback()) {
            val realDuration = super.getDuration()
            if (realDuration != C.TIME_UNSET) {
                return realDuration
            }
        }
        return 300000L
    }

    // --- Positsioon: auto progressiriba tugi ---
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

    private var currentTrackMetadata: MediaMetadata? = null

    fun updateTrackMetadata(metadata: MediaMetadata) {
        currentTrackMetadata = metadata
        // Teavitame kuulajaid (sh MediaSession) ametliku onMediaMetadataChanged kaudu.
        // Eemaldatud kunstlik onMediaItemTransition, mis tekitas tarbetut olekumuutuste müra.
        internalListeners.forEach { listener ->
            try {
                listener.onMediaMetadataChanged(metadata)
            } catch (e: Exception) {
                // Ignore listener exceptions
            }
        }
    }

    override fun getCurrentMediaItem(): MediaItem? {
        val item = super.getCurrentMediaItem() ?: return null
        val metadata = currentTrackMetadata ?: item.mediaMetadata
        val uniqueMediaId = metadata.extras?.getString("android.media.metadata.MEDIA_ID") ?: item.mediaId
        return item.buildUpon()
            .setMediaId(uniqueMediaId)
            .setMediaMetadata(metadata)
            .build()
    }

    override fun getMediaMetadata(): MediaMetadata {
        return currentTrackMetadata ?: super.getMediaMetadata()
    }

    override fun getPlaylistMetadata(): MediaMetadata {
        return currentTrackMetadata ?: super.getPlaylistMetadata()
    }
}
