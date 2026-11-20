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
 * to disk so track splits can be adjusted backwards when ICY metadata arrives late
 * and short interstitial jingles can be excised cleanly.
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

    fun discardPrefix(count: Int) {
        val limit = count.coerceIn(0, frames.size)
        repeat(limit) {
            popFirstFrame()
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

/**
 * Result of boundary framing over buffered frames (`0..frames.size`).
 * Frames `0 until closeEndIndex` belong to the closing track; frames `closeEndIndex until nextStartIndex`
 * (when non-empty) are an excised interstitial jingle; frames `nextStartIndex until frames.size`
 * belong to the incoming track.
 */
data class SplitRange(
    val closeEndIndex: Int,
    val nextStartIndex: Int = closeEndIndex,
    val strategy: String = "exact"
)

data class BoundaryAnalysisRequest(
    val frames: List<AudioFrame>,
    val hasFlushedPrefix: Boolean,
    val flushedDurationMs: Long = 0L,
    val expectedTrackDurationMs: Long? = null
)

fun interface AudioBoundaryDetector {
    fun findSplitRange(request: BoundaryAnalysisRequest): SplitRange
}

/**
 * Pure Kotlin boundary scoring over per-frame energy (dBFS) curves.
 * Detects silence gaps, crossfade energy dips, quiet-to-loud step-up transitions, and
 * dual-boundary interstitial jingles, falling back deterministically when no clear boundary exists.
 */
object AudioBoundaryScorer {

    private const val SILENCE_THRESHOLD_DB = -42.0
    private const val STRONG_CONTRAST_DB = 12.0
    private const val MIN_DIP_CONTRAST_DB = 6.0
    private const val STEP_UP_CONTEXT_MIN_DB = 10.0
    private const val STEP_UP_LOCAL_MIN_DB = 9.0
    private const val STEP_UP_MAX_BEFORE_DB = -22.0
    private const val SMOOTH_HALF_WINDOW_FRAMES = 2
    private const val CONTEXT_WINDOW_MS = 1000L
    private const val LOCAL_WINDOW_MS = 350L
    private const val PREFERRED_LAG_MS = 3000L
    private const val CLUSTER_MERGE_MS = 800L
    private const val JINGLE_EXCISION_MAX_LAG_MS = 7500L

    private enum class BoundaryType(val label: String) {
        SILENCE("silence"),
        DIP("dip"),
        STEP_UP("step-up")
    }

    private data class ScoredPeak(
        val index: Int,
        val timeMs: Long,
        val type: BoundaryType,
        val troughDb: Double,
        val strengthDb: Double,
        val score: Double,
        val closeIdx: Int,
        val resumeIdx: Int
    ) {
        val isStrongForJingleEnd: Boolean
            get() = type == BoundaryType.SILENCE || (type == BoundaryType.DIP && strengthDb >= 10.0)

        val isStrongForNextStart: Boolean
            get() = type == BoundaryType.SILENCE ||
                (type == BoundaryType.DIP && strengthDb >= 10.0) ||
                (type == BoundaryType.STEP_UP && strengthDb >= 12.0)
    }

    fun fallbackSplitRange(
        frameDurationsMs: LongArray,
        hasFlushedPrefix: Boolean,
        fallbackOffsetMs: Long = AppConfig.Recording.SPLIT_FALLBACK_OFFSET_MS,
        minSegmentGuardMs: Long = AppConfig.Recording.SPLIT_MIN_SEGMENT_GUARD_MS
    ): SplitRange {
        val n = frameDurationsMs.size
        if (n == 0) return SplitRange(0, 0, "empty")
        if (fallbackOffsetMs <= 0L) return SplitRange(n, n, "fallback-end")

        val cumulativeEndMs = buildCumulativeEndMs(frameDurationsMs)
        val totalMs = cumulativeEndMs[n - 1]
        val minIndex = computeMinIndex(cumulativeEndMs, totalMs, hasFlushedPrefix, minSegmentGuardMs)

        val fallbackTargetMs = (totalMs - fallbackOffsetMs).coerceAtLeast(0L)
        val fallbackIdx = cumulativeEndMs.indexOfFirst { it > fallbackTargetMs }
        val clamped = if (fallbackIdx < 0) n else fallbackIdx.coerceIn(minIndex, n)
        return SplitRange(clamped, clamped, "fallback")
    }

    fun selectSplitIndex(
        frameEnergiesDb: DoubleArray,
        frameDurationsMs: LongArray,
        hasFlushedPrefix: Boolean,
        searchWindowMs: Long = AppConfig.Recording.SPLIT_SEARCH_WINDOW_MS,
        fallbackOffsetMs: Long = AppConfig.Recording.SPLIT_FALLBACK_OFFSET_MS,
        minSegmentGuardMs: Long = AppConfig.Recording.SPLIT_MIN_SEGMENT_GUARD_MS,
        flushedDurationMs: Long = 0L,
        expectedTrackDurationMs: Long? = null
    ): Int {
        return selectSplitRange(
            frameEnergiesDb = frameEnergiesDb,
            frameDurationsMs = frameDurationsMs,
            hasFlushedPrefix = hasFlushedPrefix,
            searchWindowMs = searchWindowMs,
            fallbackOffsetMs = fallbackOffsetMs,
            minSegmentGuardMs = minSegmentGuardMs,
            flushedDurationMs = flushedDurationMs,
            expectedTrackDurationMs = expectedTrackDurationMs
        ).closeEndIndex
    }

    fun selectSplitRange(
        frameEnergiesDb: DoubleArray,
        frameDurationsMs: LongArray,
        hasFlushedPrefix: Boolean,
        searchWindowMs: Long = AppConfig.Recording.SPLIT_SEARCH_WINDOW_MS,
        fallbackOffsetMs: Long = AppConfig.Recording.SPLIT_FALLBACK_OFFSET_MS,
        minSegmentGuardMs: Long = AppConfig.Recording.SPLIT_MIN_SEGMENT_GUARD_MS,
        flushedDurationMs: Long = 0L,
        expectedTrackDurationMs: Long? = null,
        jingleMinGapMs: Long = AppConfig.Recording.JINGLE_MIN_GAP_MS,
        jingleMaxGapMs: Long = AppConfig.Recording.JINGLE_MAX_GAP_MS,
        durationToleranceMs: Long = AppConfig.Recording.DURATION_ANCHOR_TOLERANCE_MS
    ): SplitRange {
        val n = frameEnergiesDb.size
        if (n == 0 || n != frameDurationsMs.size) return SplitRange(n, n, "empty")
        if (n < 12) {
            return fallbackSplitRange(frameDurationsMs, hasFlushedPrefix, fallbackOffsetMs, minSegmentGuardMs)
        }

        val cumulativeEndMs = buildCumulativeEndMs(frameDurationsMs)
        val totalMs = cumulativeEndMs[n - 1]
        val minIndex = computeMinIndex(cumulativeEndMs, totalMs, hasFlushedPrefix, minSegmentGuardMs)

        val searchStartMs = (totalMs - searchWindowMs).coerceAtLeast(0L)
        val searchStartIdx = maxOf(
            minIndex,
            cumulativeEndMs.indexOfFirst { it >= searchStartMs }.let { if (it < 0) 0 else it }
        )

        if (searchStartIdx >= n - 2) {
            return fallbackSplitRange(frameDurationsMs, hasFlushedPrefix, fallbackOffsetMs, minSegmentGuardMs)
        }

        // Smooth short frame-level ripple with a ~100 ms centered window
        val smoothed = DoubleArray(n)
        for (i in 0 until n) {
            val lo = (i - SMOOTH_HALF_WINDOW_FRAMES).coerceAtLeast(0)
            val hi = (i + SMOOTH_HALF_WINDOW_FRAMES).coerceAtMost(n - 1)
            var sum = 0.0
            for (j in lo..hi) sum += frameEnergiesDb[j]
            smoothed[i] = sum / (hi - lo + 1)
        }

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

        val rawCandidates = mutableListOf<ScoredPeak>()

        for (i in searchStartIdx until n) {
            val centerMs = cumulativeEndMs[i] - (frameDurationsMs[i].coerceAtLeast(1L) / 2L)
            val beforeAvg = avgEnergyInTimeRange((centerMs - CONTEXT_WINDOW_MS).coerceAtLeast(0L), centerMs - 120L)
                ?: continue
            val afterAvg = avgEnergyInTimeRange(centerMs + 120L, (centerMs + CONTEXT_WINDOW_MS).coerceAtMost(totalMs))
                ?: avgEnergyInTimeRange(centerMs + 40L, totalMs)
                ?: continue

            val troughDb = smoothed[i]
            val dipContrastDb = minOf(beforeAvg, afterAvg) - troughDb
            val isSilence = troughDb <= SILENCE_THRESHOLD_DB && maxOf(beforeAvg, afterAvg) - troughDb >= 8.0
            val isDip = !isSilence && dipContrastDb >= MIN_DIP_CONTRAST_DB

            val localBeforeAvg = avgEnergyInTimeRange((centerMs - LOCAL_WINDOW_MS).coerceAtLeast(0L), centerMs - 40L)
                ?: beforeAvg
            val localAfterAvg = avgEnergyInTimeRange(centerMs + 40L, (centerMs + LOCAL_WINDOW_MS).coerceAtMost(totalMs))
                ?: afterAvg
            val contextGain = afterAvg - beforeAvg
            val localGain = localAfterAvg - localBeforeAvg
            val isStepUp = !isSilence && !isDip &&
                localBeforeAvg <= STEP_UP_MAX_BEFORE_DB &&
                contextGain >= STEP_UP_CONTEXT_MIN_DB &&
                localGain >= STEP_UP_LOCAL_MIN_DB &&
                troughDb <= ((localBeforeAvg + localAfterAvg) / 2.0) + 2.0

            if (!isSilence && !isDip && !isStepUp) {
                continue
            }

            val type = when {
                isSilence -> BoundaryType.SILENCE
                isDip -> BoundaryType.DIP
                else -> BoundaryType.STEP_UP
            }
            val strengthDb = when (type) {
                BoundaryType.SILENCE -> maxOf(beforeAvg, afterAvg) - troughDb
                BoundaryType.DIP -> dipContrastDb
                BoundaryType.STEP_UP -> minOf(contextGain, localGain)
            }

            val lagMs = totalMs - cumulativeEndMs[i]
            val lagPenalty = abs(lagMs - PREFERRED_LAG_MS) / 1000.0 * 0.5
            val silenceBonus = if (type == BoundaryType.SILENCE) 12.0 else 0.0
            val strongBonus = if (type == BoundaryType.DIP && strengthDb >= STRONG_CONTRAST_DB) 4.0 else 0.0
            val stepBonus = if (type == BoundaryType.STEP_UP) 2.0 else 0.0
            val durationBonus = if (expectedTrackDurationMs != null && expectedTrackDurationMs > 0L) {
                val candidateDurationMs = flushedDurationMs + cumulativeEndMs[i]
                val diffMs = abs(candidateDurationMs - expectedTrackDurationMs)
                if (diffMs <= durationToleranceMs) {
                    6.0 * (1.0 - (diffMs.toDouble() / durationToleranceMs.toDouble()))
                } else {
                    0.0
                }
            } else {
                0.0
            }

            val score = strengthDb + silenceBonus + strongBonus + stepBonus + durationBonus - lagPenalty

            var closeIdx = i.coerceIn(minIndex, n)
            var resumeIdx = i.coerceIn(minIndex, n)
            if (type == BoundaryType.SILENCE) {
                while (closeIdx > minIndex && smoothed[closeIdx - 1] <= SILENCE_THRESHOLD_DB + 3.0) {
                    closeIdx--
                }
                while (resumeIdx + 1 < n && smoothed[resumeIdx + 1] <= SILENCE_THRESHOLD_DB + 3.0) {
                    resumeIdx++
                }
            }

            rawCandidates.add(
                ScoredPeak(
                    index = i,
                    timeMs = cumulativeEndMs[i],
                    type = type,
                    troughDb = troughDb,
                    strengthDb = strengthDb,
                    score = score,
                    closeIdx = closeIdx,
                    resumeIdx = resumeIdx
                )
            )
        }

        if (rawCandidates.isEmpty()) {
            return fallbackSplitRange(frameDurationsMs, hasFlushedPrefix, fallbackOffsetMs, minSegmentGuardMs)
        }

        val clusters = clusterCandidates(rawCandidates)

        // Check for an untagged interstitial jingle bounded by two strong acoustic transitions
        val jinglePair = findBestJinglePair(
            clusters = clusters,
            totalMs = totalMs,
            flushedDurationMs = flushedDurationMs,
            expectedTrackDurationMs = expectedTrackDurationMs,
            jingleMinGapMs = jingleMinGapMs,
            jingleMaxGapMs = jingleMaxGapMs,
            durationToleranceMs = durationToleranceMs,
            avgEnergyInTimeRange = ::avgEnergyInTimeRange
        )
        if (jinglePair != null) {
            val (b1, b2) = jinglePair
            if (b2.resumeIdx > b1.closeIdx) {
                return SplitRange(
                    closeEndIndex = b1.closeIdx.coerceIn(minIndex, n),
                    nextStartIndex = b2.resumeIdx.coerceIn(minIndex, n),
                    strategy = "jingle-excise"
                )
            }
        }

        val best = clusters.maxByOrNull { it.score }
            ?: return fallbackSplitRange(frameDurationsMs, hasFlushedPrefix, fallbackOffsetMs, minSegmentGuardMs)

        val splitIdx = best.resumeIdx.coerceIn(minIndex, n)
        return SplitRange(splitIdx, splitIdx, best.type.label)
    }

    private fun clusterCandidates(candidates: List<ScoredPeak>): List<ScoredPeak> {
        val sorted = candidates.sortedBy { it.timeMs }
        val clusters = mutableListOf<ScoredPeak>()
        var currentBest = sorted.first()
        var clusterAnchorMs = currentBest.timeMs

        for (k in 1 until sorted.size) {
            val candidate = sorted[k]
            if (candidate.timeMs - clusterAnchorMs <= CLUSTER_MERGE_MS) {
                if (candidate.score > currentBest.score) {
                    currentBest = candidate
                }
                clusterAnchorMs = candidate.timeMs
            } else {
                clusters.add(currentBest)
                currentBest = candidate
                clusterAnchorMs = candidate.timeMs
            }
        }
        clusters.add(currentBest)
        return clusters
    }

    private fun findBestJinglePair(
        clusters: List<ScoredPeak>,
        totalMs: Long,
        flushedDurationMs: Long,
        expectedTrackDurationMs: Long?,
        jingleMinGapMs: Long,
        jingleMaxGapMs: Long,
        durationToleranceMs: Long,
        avgEnergyInTimeRange: (Long, Long) -> Double?
    ): Pair<ScoredPeak, ScoredPeak>? {
        if (clusters.size < 2) return null

        var bestPair: Pair<ScoredPeak, ScoredPeak>? = null
        var bestPairScore = Double.NEGATIVE_INFINITY

        for (i in 0 until clusters.size - 1) {
            val b1 = clusters[i]
            if (!b1.isStrongForJingleEnd) continue

            for (j in i + 1 until clusters.size) {
                val b2 = clusters[j]
                if (!b2.isStrongForNextStart) continue

                val gapMs = b2.timeMs - b1.timeMs
                if (gapMs < jingleMinGapMs || gapMs > jingleMaxGapMs) continue

                val lagB2Ms = totalMs - b2.timeMs
                if (lagB2Ms > JINGLE_EXCISION_MAX_LAG_MS) continue

                // Verify the segment between b1 and b2 is genuinely active jingle audio (not a quiet fade-out tail)
                val midAvg = avgEnergyInTimeRange(b1.timeMs + 250L, b2.timeMs - 250L) ?: continue
                if (midAvg < -26.0 || midAvg < b1.troughDb + 6.0) continue
                if (b2.type != BoundaryType.STEP_UP && midAvg < b2.troughDb + 6.0) continue

                // If official duration strongly confirms the track actually ran to b2, do not excise b1..b2
                if (expectedTrackDurationMs != null && expectedTrackDurationMs > 0L) {
                    val errB1 = abs((flushedDurationMs + b1.timeMs) - expectedTrackDurationMs)
                    val errB2 = abs((flushedDurationMs + b2.timeMs) - expectedTrackDurationMs)
                    if (errB2 <= durationToleranceMs && errB2 + 1500L < errB1) {
                        continue
                    }
                }

                val pairScore = b1.score + b2.score
                if (pairScore > bestPairScore) {
                    bestPairScore = pairScore
                    bestPair = b1 to b2
                }
            }
        }
        return bestPair
    }

    private fun buildCumulativeEndMs(frameDurationsMs: LongArray): LongArray {
        val n = frameDurationsMs.size
        val cumulativeEndMs = LongArray(n)
        var runningMs = 0L
        for (i in 0 until n) {
            runningMs += frameDurationsMs[i].coerceAtLeast(1L)
            cumulativeEndMs[i] = runningMs
        }
        return cumulativeEndMs
    }

    private fun computeMinIndex(
        cumulativeEndMs: LongArray,
        totalMs: Long,
        hasFlushedPrefix: Boolean,
        minSegmentGuardMs: Long
    ): Int {
        return if (hasFlushedPrefix || totalMs <= minSegmentGuardMs * 2L) {
            0
        } else {
            val guardIdx = cumulativeEndMs.indexOfFirst { it >= minSegmentGuardMs }
            if (guardIdx >= 0) guardIdx else 0
        }
    }
}

/**
 * On-demand Android [MediaCodec] acoustic boundary detector.
 * Decodes only the buffered compressed MP3/AAC frames around a metadata transition into PCM energy
 * in memory, without modifying or re-encoding the original stream frames.
 * Always falls back deterministically to [AudioBoundaryScorer.fallbackSplitRange] if decoding fails.
 */
class MediaCodecAudioBoundaryDetector : AudioBoundaryDetector {

    companion object {
        private const val TAG = "AudioBoundaryDetector"
        private const val DECODE_BUDGET_MS = 1500L
        private const val MIN_SEARCH_DECODE_COVERAGE = 0.80
    }

    fun findSplitFrameIndex(frames: List<AudioFrame>, hasFlushedPrefix: Boolean): Int {
        return findSplitRange(BoundaryAnalysisRequest(frames, hasFlushedPrefix)).closeEndIndex
    }

    override fun findSplitRange(request: BoundaryAnalysisRequest): SplitRange {
        val frames = request.frames
        if (frames.isEmpty()) return SplitRange(0, 0, "empty")

        val durationsMs = LongArray(frames.size) { idx -> (frames[idx].durationMicros / 1000L).coerceAtLeast(1L) }
        val totalMs = durationsMs.sum()
        val fallback = { reason: String ->
            val fb = AudioBoundaryScorer.fallbackSplitRange(durationsMs, request.hasFlushedPrefix)
            val closeLagMs = computeLagMs(durationsMs, fb.closeEndIndex)
            Log.i(TAG, "Split [$reason -> fallback]: frames=${frames.size}, totalMs=$totalMs, closeLagMs=$closeLagMs")
            fb
        }

        if (frames.size < 12) {
            return fallback("too-few-frames")
        }

        val sampleFrame = frames.first()
        val parsedHeader = AudioFrameParser.parseHeaderAt(
            sampleFrame.bytes,
            0,
            sampleFrame.bytes.size,
            preferAac = sampleFrame.isAac
        ) ?: return fallback("header-unparsed")

        val mime = if (parsedHeader.isAac) MediaFormat.MIMETYPE_AUDIO_AAC else MediaFormat.MIMETYPE_AUDIO_MPEG
        val format = MediaFormat.createAudioFormat(mime, parsedHeader.sampleRate, parsedHeader.channelCount)
        if (parsedHeader.isAac) {
            val csd0 = AudioFrameParser.buildAacCsd0FromAdts(sampleFrame.bytes)
                ?: return fallback("aac-csd0-missing")
            format.setInteger(MediaFormat.KEY_IS_ADTS, 1)
            format.setByteBuffer("csd-0", ByteBuffer.wrap(csd0))
        }

        val startMs = System.currentTimeMillis()
        var codec: MediaCodec? = null
        return try {
            codec = MediaCodec.createDecoderByType(mime) ?: return fallback("codec-unavailable")
            codec.configure(format, null, null, 0)
            codec.start()

            val energiesDb = decodeFrameEnergiesDb(
                codec = codec,
                frames = frames,
                durationsMs = durationsMs,
                initialSampleRate = parsedHeader.sampleRate,
                initialChannels = parsedHeader.channelCount
            ) ?: return fallback("decode-incomplete")

            val result = AudioBoundaryScorer.selectSplitRange(
                frameEnergiesDb = energiesDb,
                frameDurationsMs = durationsMs,
                hasFlushedPrefix = request.hasFlushedPrefix,
                flushedDurationMs = request.flushedDurationMs,
                expectedTrackDurationMs = request.expectedTrackDurationMs
            )

            val elapsedMs = System.currentTimeMillis() - startMs
            val closeLagMs = computeLagMs(durationsMs, result.closeEndIndex)
            val excisedMs = computeSliceMs(durationsMs, result.closeEndIndex, result.nextStartIndex)
            Log.i(
                TAG,
                "Split [${result.strategy}]: frames=${frames.size}, totalMs=$totalMs, decodeMs=$elapsedMs, " +
                    "closeLagMs=$closeLagMs, excisedMs=$excisedMs, expectedMs=${request.expectedTrackDurationMs}"
            )
            result
        } catch (t: Throwable) {
            Log.w(TAG, "MediaCodec boundary analysis failed (${t.message}), using fallback")
            fallback("exception")
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

    private fun computeLagMs(durationsMs: LongArray, index: Int): Long {
        var lag = 0L
        for (i in index.coerceIn(0, durationsMs.size) until durationsMs.size) {
            lag += durationsMs[i]
        }
        return lag
    }

    private fun computeSliceMs(durationsMs: LongArray, fromIdx: Int, toIdx: Int): Long {
        var sum = 0L
        val lo = fromIdx.coerceIn(0, durationsMs.size)
        val hi = toIdx.coerceIn(lo, durationsMs.size)
        for (i in lo until hi) sum += durationsMs[i]
        return sum
    }

    private fun decodeFrameEnergiesDb(
        codec: MediaCodec,
        frames: List<AudioFrame>,
        durationsMs: LongArray,
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
            // Drain all available input buffers so codec throughput isn't throttled by output waits
            while (!inputDone) {
                val inBufIdx = codec.dequeueInputBuffer(0L)
                if (inBufIdx < 0) break
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

            // Drain all available output buffers; wait briefly only on the first probe per iteration
            var waitUs = 2000L
            while (!outputDone) {
                val outBufIdx = codec.dequeueOutputBuffer(info, waitUs)
                waitUs = 0L
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
                    else -> break
                }
            }
        }

        val totalDecodedSamples = frameSampleCount.sum()
        if (totalDecodedSamples == 0) return null

        // Verify the trailing search window actually decoded instead of silently flattening un-decoded frames
        val totalMs = durationsMs.sum()
        val searchStartMs = (totalMs - AppConfig.Recording.SPLIT_SEARCH_WINDOW_MS).coerceAtLeast(0L)
        var runningMs = 0L
        var searchFrames = 0
        var decodedSearchFrames = 0
        for (i in 0 until n) {
            runningMs += durationsMs[i]
            if (runningMs >= searchStartMs) {
                searchFrames++
                if (frameSampleCount[i] > 0) decodedSearchFrames++
            }
        }
        if (searchFrames > 0 && decodedSearchFrames.toDouble() / searchFrames.toDouble() < MIN_SEARCH_DECODE_COVERAGE) {
            return null
        }

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
