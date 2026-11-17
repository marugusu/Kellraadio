package app.radiorecalarm

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

/**
 * Strips inline ICY metadata blocks (`icy-metaint`) from an HTTP audio byte stream,
 * detects `StreamTitle` changes at the exact wire position, and aligns track splits
 * to the next valid MP3 or AAC ADTS frame boundary.
 */
class IcyStreamProcessor(
    private val metaInt: Int,
    private val preferAac: Boolean,
    initialTitle: String? = null,
    private val shouldAlignToFrame: () -> Boolean = { true },
    private val onAudioBytes: (ByteArray, Int, Int) -> Unit,
    private val onMetadataBoundary: (String) -> Unit
) {
    companion object {
        private const val MAX_ICY_METADATA_BYTES = 255 * 16
        const val MAX_SYNC_SCAN_BYTES = 8192

        private val STREAM_TITLE_KEY_REGEX = Regex("""StreamTitle\s*=\s*'""", RegexOption.IGNORE_CASE)
        private val STREAM_TITLE_END_REGEX = Regex("""'\s*;\s*(?:[A-Za-z0-9_]+\s*=|$)""")

        fun decodeMetadataBytes(bytes: ByteArray, length: Int): String {
            if (length <= 0) return ""
            val decoder = Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            val decoded = try {
                decoder.decode(ByteBuffer.wrap(bytes, 0, length)).toString()
            } catch (_: CharacterCodingException) {
                String(bytes, 0, length, Charsets.ISO_8859_1)
            }
            return decoded.trimEnd('\u0000')
        }

        fun parseStreamTitle(rawMetadata: String): String? {
            val match = STREAM_TITLE_KEY_REGEX.find(rawMetadata) ?: return null
            val valueStart = match.range.last + 1
            val endMatch = STREAM_TITLE_END_REGEX.find(rawMetadata, startIndex = valueStart)
            if (endMatch != null) {
                return rawMetadata.substring(valueStart, endMatch.range.first)
            }
            val lastQuote = rawMetadata.lastIndexOf('\'')
            return if (lastQuote >= valueStart) {
                rawMetadata.substring(valueStart, lastQuote)
            } else {
                rawMetadata.substring(valueStart).trimEnd(';', ' ')
            }
        }
    }

    private var bytesUntilMeta = metaInt
    private var metaLengthBytesRemaining = 0
    private val metaBuffer = ByteArray(MAX_ICY_METADATA_BYTES)
    private var metaBufferPos = 0

    @Volatile
    var latestStreamTitle: String? = initialTitle
        private set

    private var pendingSplitTitle: String? = null
    private var pendingScanBytes = 0
    private val tailBuffer = ByteArray(AudioFrameParser.ADTS_HEADER_SIZE)
    private var tailLen = 0
    private val bridgeBuffer = ByteArray(AudioFrameParser.ADTS_HEADER_SIZE + 16)

    fun process(buffer: ByteArray, offset: Int, length: Int) {
        if (length <= 0) return

        if (metaInt <= 0) {
            onAudioBytes(buffer, offset, length)
            return
        }

        var curr = offset
        val end = offset + length

        while (curr < end) {
            if (metaLengthBytesRemaining > 0) {
                val chunk = minOf(end - curr, metaLengthBytesRemaining)
                if (metaBufferPos + chunk <= metaBuffer.size) {
                    System.arraycopy(buffer, curr, metaBuffer, metaBufferPos, chunk)
                }
                metaBufferPos += chunk
                curr += chunk
                metaLengthBytesRemaining -= chunk

                if (metaLengthBytesRemaining == 0) {
                    handleCompletedMetadataBlock()
                }
            } else if (bytesUntilMeta == 0) {
                val lengthByte = buffer[curr].toInt() and 0xFF
                curr++
                val metaLength = lengthByte * 16
                if (metaLength > 0) {
                    metaLengthBytesRemaining = metaLength
                    metaBufferPos = 0
                }
                bytesUntilMeta = metaInt
            } else {
                val chunk = minOf(end - curr, bytesUntilMeta)
                emitAudioChunk(buffer, curr, chunk)
                curr += chunk
                bytesUntilMeta -= chunk
            }
        }
    }

    fun flush() {
        if (tailLen > 0) {
            onAudioBytes(tailBuffer, 0, tailLen)
            tailLen = 0
        }
        val pending = pendingSplitTitle
        if (pending != null) {
            pendingSplitTitle = null
            pendingScanBytes = 0
            onMetadataBoundary(pending)
        }
    }

    private fun handleCompletedMetadataBlock() {
        val raw = decodeMetadataBytes(metaBuffer, minOf(metaBufferPos, metaBuffer.size))
        metaBufferPos = 0
        val parsedTitle = parseStreamTitle(raw) ?: return

        if (parsedTitle == latestStreamTitle) {
            return
        }

        latestStreamTitle = parsedTitle

        if (!shouldAlignToFrame()) {
            pendingSplitTitle = null
            pendingScanBytes = 0
            if (tailLen > 0) {
                onAudioBytes(tailBuffer, 0, tailLen)
                tailLen = 0
            }
            onMetadataBoundary(parsedTitle)
            return
        }

        // If a previous split was still waiting for a sync header, commit it before queuing the new one
        val previousPending = pendingSplitTitle
        if (previousPending != null) {
            if (tailLen > 0) {
                onAudioBytes(tailBuffer, 0, tailLen)
                tailLen = 0
            }
            onMetadataBoundary(previousPending)
        }

        pendingSplitTitle = parsedTitle
        pendingScanBytes = 0
    }

    private fun emitAudioChunk(buffer: ByteArray, offset: Int, length: Int) {
        if (length <= 0) return

        val pending = pendingSplitTitle
        if (pending == null) {
            onAudioBytes(buffer, offset, length)
            return
        }

        val minHeader = if (preferAac) AudioFrameParser.ADTS_HEADER_SIZE else AudioFrameParser.MP3_HEADER_SIZE

        // Check if a frame header started in the saved tail from the previous chunk
        if (tailLen > 0) {
            if (tailLen + length < minHeader) {
                System.arraycopy(buffer, offset, tailBuffer, tailLen, length)
                tailLen += length
                return
            }
            val headCopy = minOf(length, bridgeBuffer.size - tailLen)
            System.arraycopy(tailBuffer, 0, bridgeBuffer, 0, tailLen)
            System.arraycopy(buffer, offset, bridgeBuffer, tailLen, headCopy)
            val bridgeLen = tailLen + headCopy

            val syncInBridge = AudioFrameParser.findSyncOffset(bridgeBuffer, 0, bridgeLen, preferAac)
            if (syncInBridge in 0 until tailLen) {
                if (syncInBridge > 0) {
                    onAudioBytes(tailBuffer, 0, syncInBridge)
                }
                pendingSplitTitle = null
                pendingScanBytes = 0
                onMetadataBoundary(pending)
                val remainingTail = tailLen - syncInBridge
                if (remainingTail > 0) {
                    onAudioBytes(tailBuffer, syncInBridge, remainingTail)
                }
                tailLen = 0
                onAudioBytes(buffer, offset, length)
                return
            }

            // No frame started inside tailBuffer; flush it to the old track
            onAudioBytes(tailBuffer, 0, tailLen)
            tailLen = 0
        }

        val syncOffset = AudioFrameParser.findSyncOffset(buffer, offset, length, preferAac)
        if (syncOffset != -1) {
            if (syncOffset > offset) {
                onAudioBytes(buffer, offset, syncOffset - offset)
            }
            pendingSplitTitle = null
            pendingScanBytes = 0
            onMetadataBoundary(pending)
            val remaining = (offset + length) - syncOffset
            if (remaining > 0) {
                onAudioBytes(buffer, syncOffset, remaining)
            }
            return
        }

        pendingScanBytes += length
        if (pendingScanBytes >= MAX_SYNC_SCAN_BYTES) {
            onAudioBytes(buffer, offset, length)
            pendingSplitTitle = null
            pendingScanBytes = 0
            onMetadataBoundary(pending)
            return
        }

        // Keep at most (minHeader - 1) trailing bytes starting at the earliest 0xFF near the end
        val maxKeep = (if (preferAac) AudioFrameParser.ADTS_HEADER_SIZE else AudioFrameParser.MP3_HEADER_SIZE) - 1
        val end = offset + length
        val searchStart = maxOf(offset, end - maxKeep)
        var firstSyncInTail = -1
        for (i in searchStart until end) {
            if ((buffer[i].toInt() and 0xFF) == 0xFF) {
                firstSyncInTail = i
                break
            }
        }

        if (firstSyncInTail != -1) {
            val emitBefore = firstSyncInTail - offset
            if (emitBefore > 0) {
                onAudioBytes(buffer, offset, emitBefore)
            }
            val keepCount = end - firstSyncInTail
            System.arraycopy(buffer, firstSyncInTail, tailBuffer, 0, keepCount)
            tailLen = keepCount
        } else {
            onAudioBytes(buffer, offset, length)
        }
    }
}
