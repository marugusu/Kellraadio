package app.radiorecalarm

import android.media.MediaCodec
import android.media.MediaFormat
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.log10

/**
 * Rolling in-memory queue of complete compressed audio frames held back before committing
 * to disk so track splits can be adjusted backwards when ICY metadata arrives late.
 */
class LookbackFrameBuffer(private val maxDurationMicros: Long) {

    private val frames = ArrayDeque<AudioFrame>()

    var totalDurationMicros: Long = 0L
        private set

    var totalBytes: Long = 0L
        private set

    fun isEmpty(): Boolean = frames.isEmpty()

    fun snapshotFrames(): List<AudioFrame> = frames.toList()

    fun push(frame: AudioFrame, onOverflow: (AudioFrame) -> Unit) {
        frames.addLast(frame)
        totalDurationMicros += frame.durationMicros
        totalBytes += frame.bytes.size.toLong()

        while (totalDurationMicros > maxDurationMicros && frames.size > 1) {
            onOverflow(popFirstFrame())
        }
    }

    fun popPrefix(count: Int, onPopped: (AudioFrame) -> Unit) {
        val limit = count.coerceIn(0, frames.size)
        repeat(limit) {
            onPopped(popFirstFrame())
        }
    }

    fun drainAll(onFrame: (AudioFrame) -> Unit) {
        while (frames.isNotEmpty()) {
            onFrame(popFirstFrame())
        }
    }

    private fun popFirstFrame(): AudioFrame {
        val frame = frames.removeFirst()
        totalDurationMicros -= frame.durationMicros
        totalBytes -= frame.bytes.size.toLong()
        return frame
    }

    fun clear() {
        frames.clear()
        totalDurationMicros = 0L
        totalBytes = 0L
    }
}

fun interface AudioBoundaryDetector {
    /**
     * Given the buffered frames of the closing segment (oldest -> newest, ending at the metadata
     * change point), returns the frame index `0..frames.size` where the closing segment should end
     * and the new segment should begin.
     */
    fun findSplitFrameIndex(frames: List<AudioFrame>, hasFlushedPrefix: Boolean): Int
}

/**
 * Pure Kotlin boundary scoring over per-frame energy (dBFS) curves.
 * Finds silence gaps or crossfade energy dips in the metadata lag window, falling back to a
 * conservative time-shift offset when no clear acoustic dip exists.
 */
object AudioBoundaryScorer {

    private const val SILENCE_THRESHOLD_DB = -42.0
    private const val STRONG_CONTRAST_DB = 12.0
    private const val MIN_DIP_CONTRAST_DB = 6.0
    private const val SMOOTH_HALF_WINDOW_FRAMES = 2
    private const val CONTEXT_WINDOW_MS = 1000L
    private const val PREFERRED_LAG_MS = 2500L

    fun selectSplitIndex(
        frameEnergiesDb: DoubleArray,
        frameDurationsMs: LongArray,
        hasFlushedPrefix: Boolean,
        searchWindowMs: Long = AppConfig.Recording.SPLIT_SEARCH_WINDOW_MS,
        fallbackOffsetMs: Long = AppConfig.Recording.SPLIT_FALLBACK_OFFSET_MS,
        minSegmentGuardMs: Long = AppConfig.Recording.SPLIT_MIN_SEGMENT_GUARD_MS
    ): Int {
        val n = frameEnergiesDb.size
        if (n == 0 || n != frameDurationsMs.size) return n
        if (n < 12) return n

        val cumulativeEndMs = LongArray(n)
        var runningMs = 0L
        for (i in 0 until n) {
            runningMs += frameDurationsMs[i].coerceAtLeast(1L)
            cumulativeEndMs[i] = runningMs
        }
        val totalMs = runningMs

        val minIndex = if (hasFlushedPrefix || totalMs <= minSegmentGuardMs * 2L) {
            0
        } else {
            val guardIdx = cumulativeEndMs.indexOfFirst { it >= minSegmentGuardMs }
            if (guardIdx >= 0) guardIdx else 0
        }

        val searchStartMs = (totalMs - searchWindowMs).coerceAtLeast(0L)
        val searchStartIdx = maxOf(
            minIndex,
            cumulativeEndMs.indexOfFirst { it >= searchStartMs }.let { if (it < 0) 0 else it }
        )

        if (searchStartIdx >= n - 2) return n

        // Smooth short frame-level ripple with a ~100 ms centered window
        val smoothed = DoubleArray(n)
        for (i in 0 until n) {
            val lo = (i - SMOOTH_HALF_WINDOW_FRAMES).coerceAtLeast(0)
            val hi = (i + SMOOTH_HALF_WINDOW_FRAMES).coerceAtMost(n - 1)
            var sum = 0.0
            for (j in lo..hi) sum += frameEnergiesDb[j]
            smoothed[i] = sum / (hi - lo + 1)
        }

        // Prefix sums for fast O(1) 1-second context averages before/after a candidate index
        val prefixSum = DoubleArray(n + 1)
        for (i in 0 until n) {
            prefixSum[i + 1] = prefixSum[i] + smoothed[i]
        }

        fun avgEnergyInTimeRange(fromMs: Long, toMs: Long): Double? {
            if (toMs <= fromMs) return null
            val startIdx = cumulativeEndMs.indexOfFirst { it > fromMs }.let { if (it < 0) return null else it }
            val endIdx = cumulativeEndMs.indexOfLast { it <= toMs }.let { if (it < startIdx) startIdx else it }
            val count = endIdx - startIdx + 1
            if (count <= 0) return null
            return (prefixSum[endIdx + 1] - prefixSum[startIdx]) / count
        }

        var bestIndex = -1
        var bestScore = Double.NEGATIVE_INFINITY

        for (i in searchStartIdx until n) {
            val centerMs = cumulativeEndMs[i] - (frameDurationsMs[i] / 2L)
            val beforeAvg = avgEnergyInTimeRange((centerMs - CONTEXT_WINDOW_MS).coerceAtLeast(0L), centerMs - 120L)
                ?: continue
            val afterAvg = avgEnergyInTimeRange(centerMs + 120L, (centerMs + CONTEXT_WINDOW_MS).coerceAtMost(totalMs))
                ?: avgEnergyInTimeRange(centerMs + 40L, totalMs)
                ?: continue

            val troughDb = smoothed[i]
            val contrastDb = minOf(beforeAvg, afterAvg) - troughDb
            val isSilence = troughDb <= SILENCE_THRESHOLD_DB && maxOf(beforeAvg, afterAvg) - troughDb >= 8.0

            if (!isSilence && contrastDb < MIN_DIP_CONTRAST_DB) {
                continue
            }

            val lagMs = totalMs - cumulativeEndMs[i]
            // Gentle prior toward typical Icecast metadata lag (~2.5s) so equal dips prefer realistic lag
            val lagPenalty = abs(lagMs - PREFERRED_LAG_MS) / 1000.0 * 0.8
            val silenceBonus = if (isSilence) 12.0 else 0.0
            val strongBonus = if (contrastDb >= STRONG_CONTRAST_DB) 4.0 else 0.0
            val score = contrastDb + silenceBonus + strongBonus - lagPenalty

            if (score > bestScore) {
                bestScore = score
                bestIndex = i
            }
        }

        if (bestIndex != -1) {
            // If inside a wide flat silence gap, cut near the end of the silence (just before audio resumes)
            val troughDb = smoothed[bestIndex]
            var splitIdx = bestIndex
            if (troughDb <= SILENCE_THRESHOLD_DB) {
                while (splitIdx + 1 < n && smoothed[splitIdx + 1] <= SILENCE_THRESHOLD_DB + 3.0) {
                    splitIdx++
                }
            }
            return splitIdx.coerceIn(minIndex, n)
        }

        // Conservative fallback time-shift when no acoustic dip exists (e.g. continuous talk-over/crossfade)
        if (fallbackOffsetMs <= 0L) return n
        val fallbackTargetMs = (totalMs - fallbackOffsetMs).coerceAtLeast(0L)
        val fallbackIdx = cumulativeEndMs.indexOfFirst { it > fallbackTargetMs }
        return if (fallbackIdx < 0) n else fallbackIdx.coerceIn(minIndex, n)
    }
}

/**
 * On-demand Android [MediaCodec] acoustic boundary detector.
 * Decodes only the buffered compressed MP3/AAC frames around a metadata transition into PCM energy
 * in memory (~10–25 ms), without modifying or re-encoding the original stream frames.
 */
class MediaCodecAudioBoundaryDetector : AudioBoundaryDetector {

    companion object {
        private const val TAG = "AudioBoundaryDetector"
        private const val DECODE_BUDGET_MS = 500L
    }

    override fun findSplitFrameIndex(frames: List<AudioFrame>, hasFlushedPrefix: Boolean): Int {
        if (frames.size < 12) return frames.size

        val sampleFrame = frames.first()
        val parsedHeader = AudioFrameParser.parseHeaderAt(
            sampleFrame.bytes,
            0,
            sampleFrame.bytes.size,
            preferAac = sampleFrame.isAac
        ) ?: return frames.size

        val mime = if (parsedHeader.isAac) MediaFormat.MIMETYPE_AUDIO_AAC else MediaFormat.MIMETYPE_AUDIO_MPEG
        val format = MediaFormat.createAudioFormat(mime, parsedHeader.sampleRate, parsedHeader.channelCount)
        if (parsedHeader.isAac) {
            val csd0 = AudioFrameParser.buildAacCsd0FromAdts(sampleFrame.bytes) ?: return frames.size
            format.setInteger(MediaFormat.KEY_IS_ADTS, 1)
            format.setByteBuffer("csd-0", ByteBuffer.wrap(csd0))
        }

        var codec: MediaCodec? = null
        return try {
            codec = MediaCodec.createDecoderByType(mime) ?: return frames.size
            codec.configure(format, null, null, 0)
            codec.start()

            val energiesDb = decodeFrameEnergiesDb(codec, frames, parsedHeader.sampleRate, parsedHeader.channelCount)
                ?: return frames.size
            val durationsMs = LongArray(frames.size) { idx -> (frames[idx].durationMicros / 1000L).coerceAtLeast(1L) }

            AudioBoundaryScorer.selectSplitIndex(
                frameEnergiesDb = energiesDb,
                frameDurationsMs = durationsMs,
                hasFlushedPrefix = hasFlushedPrefix
            )
        } catch (t: Throwable) {
            Log.w(TAG, "MediaCodec boundary analysis skipped: ${t.message}")
            frames.size
        } finally {
            try {
                codec?.stop()
            } catch (_: Throwable) {
            }
            try {
                codec?.release()
            } catch (_: Throwable) {
            }
        }
    }

    private fun decodeFrameEnergiesDb(
        codec: MediaCodec,
        frames: List<AudioFrame>,
        initialSampleRate: Int,
        initialChannels: Int
    ): DoubleArray? {
        val n = frames.size
        val frameStartUs = LongArray(n)
        var cumulativeUs = 0L
        for (i in 0 until n) {
            frameStartUs[i] = cumulativeUs
            cumulativeUs += frames[i].durationMicros
        }

        val frameSqSum = DoubleArray(n)
        val frameSampleCount = IntArray(n)

        var outputSampleRate = initialSampleRate.coerceAtLeast(8000)
        var outputChannels = initialChannels.coerceIn(1, 8)
        var inputIdx = 0
        var inputDone = false
        var outputDone = false
        val info = MediaCodec.BufferInfo()
        val deadlineMs = System.currentTimeMillis() + DECODE_BUDGET_MS

        while (!outputDone && System.currentTimeMillis() < deadlineMs) {
            if (!inputDone) {
                val inBufIdx = codec.dequeueInputBuffer(0L)
                if (inBufIdx >= 0) {
                    val inBuf = codec.getInputBuffer(inBufIdx)
                    if (inBuf != null && inputIdx < n) {
                        inBuf.clear()
                        val frame = frames[inputIdx]
                        if (frame.bytes.size <= inBuf.capacity()) {
                            inBuf.put(frame.bytes)
                            codec.queueInputBuffer(
                                inBufIdx,
                                0,
                                frame.bytes.size,
                                frameStartUs[inputIdx],
                                0
                            )
                        } else {
                            codec.queueInputBuffer(inBufIdx, 0, 0, frameStartUs[inputIdx], 0)
                        }
                        inputIdx++
                    } else {
                        codec.queueInputBuffer(inBufIdx, 0, 0, cumulativeUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    }
                }
            }

            val outBufIdx = codec.dequeueOutputBuffer(info, 2000L)
            when {
                outBufIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    val outFmt = codec.outputFormat
                    if (outFmt.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                        outputSampleRate = outFmt.getInteger(MediaFormat.KEY_SAMPLE_RATE).coerceAtLeast(8000)
                    }
                    if (outFmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                        outputChannels = outFmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceIn(1, 8)
                    }
                }
                outBufIdx >= 0 -> {
                    if (info.size > 0 && (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                        val outBuf = codec.getOutputBuffer(outBufIdx)
                        if (outBuf != null) {
                            accumulatePcmEnergy(
                                outBuf = outBuf,
                                offset = info.offset,
                                size = info.size,
                                basePtsUs = info.presentationTimeUs,
                                sampleRate = outputSampleRate,
                                channels = outputChannels,
                                frameStartUs = frameStartUs,
                                frameSqSum = frameSqSum,
                                frameSampleCount = frameSampleCount
                            )
                        }
                    }
                    codec.releaseOutputBuffer(outBufIdx, false)
                    if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        outputDone = true
                    }
                }
            }
        }

        val totalDecodedSamples = frameSampleCount.sum()
        if (totalDecodedSamples == 0) return null

        val energiesDb = DoubleArray(n) { -90.0 }
        var lastValidDb = -90.0
        for (i in 0 until n) {
            val count = frameSampleCount[i]
            if (count > 0) {
                val meanSq = frameSqSum[i] / count
                val db = 10.0 * log10(meanSq + 1e-12)
                energiesDb[i] = db
                lastValidDb = db
            } else {
                energiesDb[i] = lastValidDb
            }
        }
        return energiesDb
    }

    private fun accumulatePcmEnergy(
        outBuf: ByteBuffer,
        offset: Int,
        size: Int,
        basePtsUs: Long,
        sampleRate: Int,
        channels: Int,
        frameStartUs: LongArray,
        frameSqSum: DoubleArray,
        frameSampleCount: IntArray
    ) {
        val dup = outBuf.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        dup.position(offset)
        dup.limit(offset + size)
        val shortBuf = dup.asShortBuffer()
        val totalShorts = shortBuf.remaining()
        val totalPcmFrames = totalShorts / channels
        if (totalPcmFrames <= 0) return

        var cachedFrameIdx = findFrameIndexForPts(frameStartUs, basePtsUs)
        val usPerSample = 1_000_000.0 / sampleRate.toDouble()

        for (p in 0 until totalPcmFrames) {
            val samplePtsUs = basePtsUs + (p * usPerSample).toLong()
            while (cachedFrameIdx + 1 < frameStartUs.size && frameStartUs[cachedFrameIdx + 1] <= samplePtsUs) {
                cachedFrameIdx++
            }
            var sumSq = 0.0
            for (ch in 0 until channels) {
                val norm = shortBuf.get().toDouble() / 32768.0
                sumSq += norm * norm
            }
            frameSqSum[cachedFrameIdx] += sumSq / channels
            frameSampleCount[cachedFrameIdx] += 1
        }
    }

    private fun findFrameIndexForPts(frameStartUs: LongArray, ptsUs: Long): Int {
        var lo = 0
        var hi = frameStartUs.size - 1
        var ans = 0
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (frameStartUs[mid] <= ptsUs) {
                ans = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        return ans
    }
}
