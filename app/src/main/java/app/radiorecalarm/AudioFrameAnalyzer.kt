package app.radiorecalarm

/**
 * Parsed MPEG audio (MP3/MP2/MP1) or AAC ADTS frame header metadata.
 */
data class AudioFrameHeader(
    val frameSize: Int,
    val durationMicros: Long,
    val isAac: Boolean,
    val sampleRate: Int = 44100,
    val channelCount: Int = 2
)

/**
 * Complete compressed MP3 or AAC ADTS frame with its parsed duration.
 */
class AudioFrame(
    val bytes: ByteArray,
    val durationMicros: Long,
    val isAac: Boolean
)

/**
 * Stateless parser for MP3 (MPEG-1/2/2.5 Layer I/II/III) and AAC ADTS frame headers.
 * Used both for clean frame-boundary track splitting and accurate audio duration tracking.
 */
object AudioFrameParser {

    private val MPEG1_BITRATES_L1 = intArrayOf(0, 32, 64, 96, 128, 160, 192, 224, 256, 288, 320, 352, 384, 416, 448, 0)
    private val MPEG1_BITRATES_L2 = intArrayOf(0, 32, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 384, 0)
    private val MPEG1_BITRATES_L3 = intArrayOf(0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 0)
    private val MPEG2_BITRATES_L1 = intArrayOf(0, 32, 48, 56, 64, 80, 96, 112, 128, 144, 160, 176, 192, 224, 256, 0)
    private val MPEG2_BITRATES_L23 = intArrayOf(0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160, 0)

    private val MPEG1_SAMPLE_RATES = intArrayOf(44100, 48000, 32000)
    private val MPEG2_SAMPLE_RATES = intArrayOf(22050, 24000, 16000)
    private val MPEG25_SAMPLE_RATES = intArrayOf(11025, 12000, 8000)

    private val ADTS_SAMPLE_RATES = intArrayOf(
        96000, 88200, 64000, 48000, 44100, 32000, 24000, 22050, 16000, 12000, 11025, 8000, 7350
    )

    const val MP3_HEADER_SIZE = 4
    const val ADTS_HEADER_SIZE = 7
    const val MAX_ADTS_FRAME_SIZE = 8192

    fun parseHeaderAt(buffer: ByteArray, offset: Int, available: Int, preferAac: Boolean): AudioFrameHeader? {
        return if (preferAac) {
            parseAdtsAt(buffer, offset, available) ?: parseMp3At(buffer, offset, available)
        } else {
            parseMp3At(buffer, offset, available) ?: parseAdtsAt(buffer, offset, available)
        }
    }

    fun parseMp3At(buffer: ByteArray, offset: Int, available: Int): AudioFrameHeader? {
        if (available < MP3_HEADER_SIZE) return null
        val b0 = buffer[offset].toInt() and 0xFF
        val b1 = buffer[offset + 1].toInt() and 0xFF
        val b2 = buffer[offset + 2].toInt() and 0xFF
        val b3 = buffer[offset + 3].toInt() and 0xFF
        val channelMode = (b3 ushr 6) and 0x03
        val header = parseMp3Header(b0, b1, b2) ?: return null
        return if (channelMode == 0x03) header.copy(channelCount = 1) else header
    }

    fun parseMp3Header(b0: Int, b1: Int, b2: Int): AudioFrameHeader? {
        if (b0 != 0xFF || (b1 and 0xE0) != 0xE0) return null

        val versionBits = (b1 ushr 3) and 0x03
        val layerBits = (b1 ushr 1) and 0x03
        if (versionBits == 0x01 || layerBits == 0x00) return null

        val bitrateIndex = (b2 ushr 4) and 0x0F
        val sampleRateIndex = (b2 ushr 2) and 0x03
        val padding = (b2 ushr 1) and 0x01
        if (bitrateIndex == 0 || bitrateIndex == 15 || sampleRateIndex == 3) return null

        val sampleRate = when (versionBits) {
            0x03 -> MPEG1_SAMPLE_RATES[sampleRateIndex]
            0x02 -> MPEG2_SAMPLE_RATES[sampleRateIndex]
            0x00 -> MPEG25_SAMPLE_RATES[sampleRateIndex]
            else -> return null
        }

        val bitrateKbps = when (versionBits) {
            0x03 -> when (layerBits) {
                0x03 -> MPEG1_BITRATES_L1[bitrateIndex]
                0x02 -> MPEG1_BITRATES_L2[bitrateIndex]
                0x01 -> MPEG1_BITRATES_L3[bitrateIndex]
                else -> 0
            }
            else -> when (layerBits) {
                0x03 -> MPEG2_BITRATES_L1[bitrateIndex]
                0x02, 0x01 -> MPEG2_BITRATES_L23[bitrateIndex]
                else -> 0
            }
        }
        if (bitrateKbps <= 0) return null

        val bitrateBps = bitrateKbps * 1000
        val samplesPerFrame = when {
            layerBits == 0x03 -> 384
            versionBits == 0x03 -> 1152
            layerBits == 0x02 -> 1152
            else -> 576
        }

        val frameSize = when (layerBits) {
            0x03 -> ((12 * bitrateBps / sampleRate) + padding) * 4
            else -> {
                val factor = if (versionBits == 0x03 || layerBits == 0x02) 144 else 72
                (factor * bitrateBps / sampleRate) + padding
            }
        }
        if (frameSize < MP3_HEADER_SIZE) return null

        val durationMicros = (samplesPerFrame.toLong() * 1_000_000L) / sampleRate
        return AudioFrameHeader(
            frameSize = frameSize,
            durationMicros = durationMicros,
            isAac = false,
            sampleRate = sampleRate,
            channelCount = 2
        )
    }

    fun parseAdtsAt(buffer: ByteArray, offset: Int, available: Int): AudioFrameHeader? {
        if (available < ADTS_HEADER_SIZE) return null
        val b0 = buffer[offset].toInt() and 0xFF
        val b1 = buffer[offset + 1].toInt() and 0xFF
        val b2 = buffer[offset + 2].toInt() and 0xFF
        val b3 = buffer[offset + 3].toInt() and 0xFF
        val b4 = buffer[offset + 4].toInt() and 0xFF
        val b5 = buffer[offset + 5].toInt() and 0xFF
        val b6 = buffer[offset + 6].toInt() and 0xFF
        return parseAdtsHeader(b0, b1, b2, b3, b4, b5, b6)
    }

    fun parseAdtsHeader(
        b0: Int,
        b1: Int,
        b2: Int,
        b3: Int,
        b4: Int,
        b5: Int,
        b6: Int
    ): AudioFrameHeader? {
        // 12-bit sync word 0xFFF and MPEG layer bits == 00
        if (b0 != 0xFF || (b1 and 0xF6) != 0xF0) return null

        val sampleRateIndex = (b2 ushr 2) and 0x0F
        if (sampleRateIndex >= ADTS_SAMPLE_RATES.size) return null

        val frameLength = ((b3 and 0x03) shl 11) or ((b4 and 0xFF) shl 3) or ((b5 ushr 5) and 0x07)
        if (frameLength < ADTS_HEADER_SIZE || frameLength > MAX_ADTS_FRAME_SIZE) return null

        val sampleRate = ADTS_SAMPLE_RATES[sampleRateIndex]
        val channelConfig = ((b2 and 0x01) shl 2) or ((b3 ushr 6) and 0x03)
        val channelCount = if (channelConfig in 1..6) channelConfig else 2
        val numRawDataBlocks = b6 and 0x03
        val samplesPerFrame = (numRawDataBlocks + 1) * 1024L
        val durationMicros = (samplesPerFrame * 1_000_000L) / sampleRate
        return AudioFrameHeader(
            frameSize = frameLength,
            durationMicros = durationMicros,
            isAac = true,
            sampleRate = sampleRate,
            channelCount = channelCount
        )
    }

    /**
     * Derives the 2-byte AudioSpecificConfig (`csd-0`) from a 7-byte ADTS frame header.
     */
    fun buildAacCsd0FromAdts(adtsFrame: ByteArray): ByteArray? {
        if (adtsFrame.size < ADTS_HEADER_SIZE) return null
        val b2 = adtsFrame[2].toInt() and 0xFF
        val b3 = adtsFrame[3].toInt() and 0xFF
        val profile = (b2 ushr 6) and 0x03
        val aot = profile + 1
        val sampleRateIndex = (b2 ushr 2) and 0x0F
        if (sampleRateIndex >= ADTS_SAMPLE_RATES.size) return null
        val channelConfig = ((b2 and 0x01) shl 2) or ((b3 ushr 6) and 0x03)
        val byte0 = ((aot shl 3) or (sampleRateIndex ushr 1)).toByte()
        val byte1 = (((sampleRateIndex and 0x01) shl 7) or (channelConfig shl 3)).toByte()
        return byteArrayOf(byte0, byte1)
    }

    /**
     * Scans [buffer] in `[offset, offset + length)` for a valid frame header.
     * When enough bytes follow the candidate frame, verifies that the next frame also starts
     * with a valid header of the same codec to avoid false sync matches on payload bytes.
     */
    fun findSyncOffset(buffer: ByteArray, offset: Int, length: Int, preferAac: Boolean): Int {
        val end = offset + length
        val minHeader = if (preferAac) ADTS_HEADER_SIZE else MP3_HEADER_SIZE
        var i = offset
        while (i <= end - minHeader) {
            if ((buffer[i].toInt() and 0xFF) == 0xFF) {
                val available = end - i
                val header = parseHeaderAt(buffer, i, available, preferAac)
                if (header != null) {
                    val nextOffset = i + header.frameSize
                    val nextMinHeader = if (header.isAac) ADTS_HEADER_SIZE else MP3_HEADER_SIZE
                    if (nextOffset + nextMinHeader <= end) {
                        val nextHeader = if (header.isAac) {
                            parseAdtsAt(buffer, nextOffset, end - nextOffset)
                        } else {
                            parseMp3At(buffer, nextOffset, end - nextOffset)
                        }
                        if (nextHeader != null) {
                            return i
                        }
                    } else {
                        return i
                    }
                }
            }
            i++
        }
        return -1
    }
}

/**
 * Streaming MP3/ADTS frame duration accumulator.
 * Locks onto two consecutive valid frames before counting, so non-audio bytes or
 * random 0xFF payload patterns are never miscounted as audio duration.
 */
class AudioDurationTracker(private val preferAac: Boolean) {

    private enum class State {
        SEARCHING,
        VERIFY_NEXT_HEADER,
        LOCKED
    }

    private var state = State.SEARCHING
    private val headerBuf = ByteArray(AudioFrameParser.ADTS_HEADER_SIZE)
    private var headerLen = 0
    private var expectedHeaderLen = if (preferAac) AudioFrameParser.ADTS_HEADER_SIZE else AudioFrameParser.MP3_HEADER_SIZE
    private var skipPayloadRemaining = 0
    private var pendingFirstFrame: AudioFrameHeader? = null

    var validFrameCount: Long = 0L
        private set
    var totalDurationMicros: Long = 0L
        private set
    var totalBytes: Long = 0L
        private set

    fun consume(buffer: ByteArray, offset: Int, length: Int) {
        if (length <= 0) return
        totalBytes += length.toLong()

        var i = offset
        val end = offset + length
        while (i < end) {
            if (skipPayloadRemaining > 0) {
                val skip = minOf(skipPayloadRemaining, end - i)
                skipPayloadRemaining -= skip
                i += skip
                continue
            }

            val b = buffer[i].toInt() and 0xFF
            when (state) {
                State.SEARCHING -> {
                    if (headerLen == 0) {
                        if (b == 0xFF) {
                            headerBuf[0] = buffer[i]
                            headerLen = 1
                        }
                        i++
                    } else {
                        headerBuf[headerLen++] = buffer[i]
                        i++
                        if (headerLen == 2) {
                            val b1 = headerBuf[1].toInt() and 0xFF
                            val looksAdts = (b1 and 0xF6) == 0xF0
                            val looksMp3 = (b1 and 0xE0) == 0xE0 && (b1 and 0x06) != 0
                            expectedHeaderLen = when {
                                looksAdts -> AudioFrameParser.ADTS_HEADER_SIZE
                                looksMp3 -> AudioFrameParser.MP3_HEADER_SIZE
                                else -> {
                                    resetHeaderSearchKeepTail()
                                    continue
                                }
                            }
                        }
                        if (headerLen >= expectedHeaderLen) {
                            val parsed = if (expectedHeaderLen == AudioFrameParser.ADTS_HEADER_SIZE) {
                                AudioFrameParser.parseAdtsAt(headerBuf, 0, headerLen)
                            } else {
                                AudioFrameParser.parseMp3At(headerBuf, 0, headerLen)
                            }
                            if (parsed != null) {
                                pendingFirstFrame = parsed
                                skipPayloadRemaining = parsed.frameSize - expectedHeaderLen
                                headerLen = 0
                                state = State.VERIFY_NEXT_HEADER
                            } else {
                                resetHeaderSearchKeepTail()
                                recheckBufferedSearchCandidate()
                            }
                        }
                    }
                }

                State.VERIFY_NEXT_HEADER, State.LOCKED -> {
                    if (headerLen == 0 && b != 0xFF) {
                        // Lost sync: drop back to SEARCHING
                        pendingFirstFrame = null
                        state = State.SEARCHING
                        i++
                        continue
                    }
                    headerBuf[headerLen++] = buffer[i]
                    i++
                    if (headerLen >= expectedHeaderLen) {
                        val parsed = if (expectedHeaderLen == AudioFrameParser.ADTS_HEADER_SIZE) {
                            AudioFrameParser.parseAdtsAt(headerBuf, 0, headerLen)
                        } else {
                            AudioFrameParser.parseMp3At(headerBuf, 0, headerLen)
                        }
                        if (parsed != null) {
                            if (state == State.VERIFY_NEXT_HEADER) {
                                val first = pendingFirstFrame!!
                                validFrameCount += 2
                                totalDurationMicros += first.durationMicros + parsed.durationMicros
                                pendingFirstFrame = null
                                state = State.LOCKED
                            } else {
                                validFrameCount += 1
                                totalDurationMicros += parsed.durationMicros
                            }
                            skipPayloadRemaining = parsed.frameSize - expectedHeaderLen
                            headerLen = 0
                        } else {
                            pendingFirstFrame = null
                            state = State.SEARCHING
                            resetHeaderSearchKeepTail()
                            recheckBufferedSearchCandidate()
                        }
                    }
                }
            }
        }
    }

    private fun recheckBufferedSearchCandidate() {
        while (state == State.SEARCHING && headerLen >= expectedHeaderLen) {
            val parsed = if (expectedHeaderLen == AudioFrameParser.ADTS_HEADER_SIZE) {
                AudioFrameParser.parseAdtsAt(headerBuf, 0, headerLen)
            } else {
                AudioFrameParser.parseMp3At(headerBuf, 0, headerLen)
            }
            if (parsed != null) {
                val extraBytes = headerLen - expectedHeaderLen
                pendingFirstFrame = parsed
                val payloadNeeded = parsed.frameSize - expectedHeaderLen
                if (extraBytes <= payloadNeeded) {
                    skipPayloadRemaining = payloadNeeded - extraBytes
                    headerLen = 0
                    state = State.VERIFY_NEXT_HEADER
                } else {
                    // Unlikely, but keep leftover bytes safely
                    val overflow = extraBytes - payloadNeeded
                    System.arraycopy(headerBuf, expectedHeaderLen + payloadNeeded, headerBuf, 0, overflow)
                    skipPayloadRemaining = 0
                    headerLen = overflow
                    state = State.VERIFY_NEXT_HEADER
                }
                return
            } else {
                resetHeaderSearchKeepTail()
            }
        }
    }

    private fun resetHeaderSearchKeepTail() {
        // Shift if a 0xFF byte appeared inside the failed header candidate
        var nextSync = -1
        for (j in 1 until headerLen) {
            if ((headerBuf[j].toInt() and 0xFF) == 0xFF) {
                nextSync = j
                break
            }
        }
        if (nextSync != -1) {
            val remaining = headerLen - nextSync
            System.arraycopy(headerBuf, nextSync, headerBuf, 0, remaining)
            headerLen = remaining
            if (headerLen >= 2) {
                val b1 = headerBuf[1].toInt() and 0xFF
                expectedHeaderLen = when {
                    (b1 and 0xF6) == 0xF0 -> AudioFrameParser.ADTS_HEADER_SIZE
                    (b1 and 0xE0) == 0xE0 && (b1 and 0x06) != 0 -> AudioFrameParser.MP3_HEADER_SIZE
                    else -> {
                        headerLen = 0
                        if (preferAac) AudioFrameParser.ADTS_HEADER_SIZE else AudioFrameParser.MP3_HEADER_SIZE
                    }
                }
            }
        } else {
            headerLen = 0
        }
    }
}

/**
 * Splits a continuous byte stream into discrete complete MP3 or AAC ADTS [AudioFrame]s.
 * If incoming bytes are not valid MP3/ADTS frames (e.g. unsupported codec or synthetic test data),
 * falls back to emitting raw chunks safely without data loss.
 */
class AudioFrameAccumulator(private val preferAac: Boolean) {

    companion object {
        private const val INITIAL_CAPACITY = 16 * 1024
        private const val MAX_UNSYNCED_BYTES = 16 * 1024
    }

    private var buf = ByteArray(INITIAL_CAPACITY)
    private var len = 0
    private var locked = false

    fun push(
        input: ByteArray,
        offset: Int,
        length: Int,
        onFrame: (AudioFrame) -> Unit,
        onRawFallback: (ByteArray, Int, Int) -> Unit
    ) {
        if (length <= 0) return

        val required = len + length
        if (required > buf.size) {
            buf = buf.copyOf(maxOf(buf.size * 2, required))
        }
        System.arraycopy(input, offset, buf, len, length)
        len += length

        var consumed = 0
        var scanPos = 0

        while (scanPos < len) {
            if (!locked) {
                while (scanPos < len && (buf[scanPos].toInt() and 0xFF) != 0xFF) {
                    scanPos++
                }
                val minHeader = if (preferAac) AudioFrameParser.ADTS_HEADER_SIZE else AudioFrameParser.MP3_HEADER_SIZE
                if (len - scanPos < minHeader) break

                val header = AudioFrameParser.parseHeaderAt(buf, scanPos, len - scanPos, preferAac)
                if (header == null) {
                    scanPos++
                    continue
                }

                val nextOffset = scanPos + header.frameSize
                val nextMinHeader = if (header.isAac) AudioFrameParser.ADTS_HEADER_SIZE else AudioFrameParser.MP3_HEADER_SIZE
                if (nextOffset + nextMinHeader > len) break

                val nextHeader = if (header.isAac) {
                    AudioFrameParser.parseAdtsAt(buf, nextOffset, len - nextOffset)
                } else {
                    AudioFrameParser.parseMp3At(buf, nextOffset, len - nextOffset)
                }
                if (nextHeader != null) {
                    locked = true
                    onFrame(AudioFrame(buf.copyOfRange(scanPos, nextOffset), header.durationMicros, header.isAac))
                    consumed = nextOffset
                    scanPos = nextOffset
                } else {
                    scanPos++
                }
            } else {
                if ((buf[scanPos].toInt() and 0xFF) != 0xFF) {
                    locked = false
                    consumed = scanPos
                    continue
                }
                val minHeader = if (preferAac) AudioFrameParser.ADTS_HEADER_SIZE else AudioFrameParser.MP3_HEADER_SIZE
                if (len - scanPos < minHeader) break

                val header = AudioFrameParser.parseHeaderAt(buf, scanPos, len - scanPos, preferAac)
                if (header == null) {
                    locked = false
                    consumed = scanPos
                    scanPos++
                    continue
                }
                if (scanPos + header.frameSize > len) break

                val nextOffset = scanPos + header.frameSize
                onFrame(AudioFrame(buf.copyOfRange(scanPos, nextOffset), header.durationMicros, header.isAac))
                consumed = nextOffset
                scanPos = nextOffset
            }
        }

        if (consumed > 0) {
            val remaining = len - consumed
            if (remaining > 0) {
                System.arraycopy(buf, consumed, buf, 0, remaining)
            }
            len = remaining
        }

        if (!locked && len >= MAX_UNSYNCED_BYTES) {
            onRawFallback(buf, 0, len)
            len = 0
        }
    }

    fun drainUnparsed(onRawFallback: (ByteArray, Int, Int) -> Unit) {
        if (!locked && len > 0) {
            onRawFallback(buf, 0, len)
            len = 0
        } else if (locked) {
            // Drop at most one incomplete trailing frame on stream close/split
            len = 0
        }
    }

    fun reset() {
        len = 0
        locked = false
    }
}
