package app.radiorecalarm

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin

@RunWith(AndroidJUnit4::class)
class MediaCodecAudioBoundaryDetectorTest {

    @Test
    fun detectsSilenceGapInRealAacAdtsStreamViaMediaCodec() {
        val sampleRate = 44100
        val channels = 2
        val totalSeconds = 8
        val gapStartMs = 5200
        val gapEndMs = 5450

        val adtsBytes = encodePcmToAdtsAac(
            sampleRate = sampleRate,
            channels = channels,
            durationMs = totalSeconds * 1000,
            amplitudeAtMs = { tMs -> if (tMs in gapStartMs..gapEndMs) 0.0 else 0.6 }
        )

        val frames = parseAdtsFrames(adtsBytes)
        assertTrue("Expected encoded ADTS frames (> 250), got ${frames.size}", frames.size > 250)

        val detector = MediaCodecAudioBoundaryDetector()
        val splitIdx = detector.findSplitFrameIndex(frames, hasFlushedPrefix = true)
        val splitTimeMs = frames.take(splitIdx).sumOf { it.durationMicros } / 1000L

        assertTrue(
            "Expected MediaCodec split near silence gap (${gapStartMs - 150}..${gapEndMs + 150}ms), got ${splitTimeMs}ms (splitIdx=$splitIdx/${frames.size})",
            splitTimeMs in (gapStartMs - 150L)..(gapEndMs + 150L)
        )
    }

    @Test
    fun detectsCrossfadeEnergyDipInRealAacAdtsStreamViaMediaCodec() {
        val sampleRate = 44100
        val channels = 2
        val totalSeconds = 8
        val dipStartMs = 5400
        val dipEndMs = 5600

        val adtsBytes = encodePcmToAdtsAac(
            sampleRate = sampleRate,
            channels = channels,
            durationMs = totalSeconds * 1000,
            amplitudeAtMs = { tMs -> if (tMs in dipStartMs..dipEndMs) 0.10 else 0.65 }
        )

        val frames = parseAdtsFrames(adtsBytes)
        assertTrue("Expected encoded ADTS frames (> 250), got ${frames.size}", frames.size > 250)

        val detector = MediaCodecAudioBoundaryDetector()
        val splitIdx = detector.findSplitFrameIndex(frames, hasFlushedPrefix = true)
        val splitTimeMs = frames.take(splitIdx).sumOf { it.durationMicros } / 1000L

        assertTrue(
            "Expected MediaCodec split near crossfade dip (${dipStartMs - 150}..${dipEndMs + 150}ms), got ${splitTimeMs}ms (splitIdx=$splitIdx/${frames.size})",
            splitTimeMs in (dipStartMs - 150L)..(dipEndMs + 150L)
        )
    }

    private fun parseAdtsFrames(adtsBytes: ByteArray): List<AudioFrame> {
        val frames = mutableListOf<AudioFrame>()
        val accumulator = AudioFrameAccumulator(preferAac = true)
        accumulator.push(
            adtsBytes,
            0,
            adtsBytes.size,
            onFrame = { frames.add(it) },
            onRawFallback = { _, _, _ -> }
        )
        return frames
    }

    private fun encodePcmToAdtsAac(
        sampleRate: Int,
        channels: Int,
        durationMs: Int,
        amplitudeAtMs: (Long) -> Double
    ): ByteArray {
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channels).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, 128000)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
        }
        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        encoder.start()

        val out = java.io.ByteArrayOutputStream()
        try {
            val totalPcmFrames = (sampleRate.toLong() * durationMs / 1000L).toInt()
            var pcmPos = 0
            val chunkPcmFrames = 1024
            var inputDone = false
            var outputDone = false
            val info = MediaCodec.BufferInfo()
            val freq = 440.0

            while (!outputDone) {
                if (!inputDone) {
                    val inIdx = encoder.dequeueInputBuffer(5000L)
                    if (inIdx >= 0) {
                        val inBuf = encoder.getInputBuffer(inIdx)!!
                        inBuf.clear()
                        if (pcmPos >= totalPcmFrames) {
                            encoder.queueInputBuffer(
                                inIdx,
                                0,
                                0,
                                pcmPos * 1_000_000L / sampleRate,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            inputDone = true
                        } else {
                            val count = minOf(chunkPcmFrames, totalPcmFrames - pcmPos)
                            val byteBuf = ByteBuffer.allocate(count * channels * 2).order(ByteOrder.LITTLE_ENDIAN)
                            for (i in 0 until count) {
                                val sampleIdx = pcmPos + i
                                val tMs = sampleIdx * 1000L / sampleRate
                                val amp = amplitudeAtMs(tMs)
                                val sample = (sin(2.0 * PI * freq * sampleIdx / sampleRate) * amp * 32767.0)
                                    .toInt().coerceIn(-32768, 32767).toShort()
                                repeat(channels) { byteBuf.putShort(sample) }
                            }
                            inBuf.put(byteBuf.array())
                            encoder.queueInputBuffer(
                                inIdx,
                                0,
                                byteBuf.capacity(),
                                pcmPos * 1_000_000L / sampleRate,
                                0
                            )
                            pcmPos += count
                        }
                    }
                }

                val outIdx = encoder.dequeueOutputBuffer(info, 5000L)
                if (outIdx >= 0) {
                    if (info.size > 0 && (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                        val outBuf = encoder.getOutputBuffer(outIdx)!!
                        val raw = ByteArray(info.size)
                        outBuf.position(info.offset)
                        outBuf.get(raw)
                        out.write(buildAdtsHeader(raw.size, sampleRateIndex = 4, channelConfig = channels))
                        out.write(raw)
                    }
                    encoder.releaseOutputBuffer(outIdx, false)
                    if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        outputDone = true
                    }
                }
            }
        } finally {
            encoder.stop()
            encoder.release()
        }
        return out.toByteArray()
    }

    private fun buildAdtsHeader(aacPayloadLen: Int, sampleRateIndex: Int, channelConfig: Int): ByteArray {
        val frameLen = aacPayloadLen + 7
        return byteArrayOf(
            0xFF.toByte(),
            0xF1.toByte(),
            ((1 shl 6) or (sampleRateIndex shl 2) or (channelConfig shr 2)).toByte(),
            (((channelConfig and 0x03) shl 6) or (frameLen shr 11)).toByte(),
            ((frameLen shr 3) and 0xFF).toByte(),
            (((frameLen and 0x07) shl 5) or 0x1F).toByte(),
            0xFC.toByte()
        )
    }
}
