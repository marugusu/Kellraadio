package app.radiorecalarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioFrameAnalyzerTest {

    companion object {
        /**
         * Builds a valid MPEG-1 Layer III 128 kbps 44.1 kHz frame (417 bytes, ~26.122 ms).
         * Header: 0xFF 0xFB 0x90 0x00
         */
        fun buildMp3Frame(fillByte: Byte = 0x11): ByteArray {
            val frame = ByteArray(417) { fillByte }
            frame[0] = 0xFF.toByte()
            frame[1] = 0xFB.toByte()
            frame[2] = 0x90.toByte()
            frame[3] = 0x00.toByte()
            return frame
        }

        /**
         * Builds a valid AAC-LC ADTS 44.1 kHz stereo frame of [frameLength] bytes (~23.22 ms).
         */
        fun buildAdtsFrame(frameLength: Int = 256, fillByte: Byte = 0x22): ByteArray {
            val frame = ByteArray(frameLength) { fillByte }
            frame[0] = 0xFF.toByte()
            frame[1] = 0xF1.toByte() // MPEG-4, Layer 00, no CRC
            frame[2] = 0x50.toByte() // AAC-LC (01), 44.1 kHz (0100), private 0, ch high 0
            frame[3] = (0x80 or ((frameLength shr 11) and 0x03)).toByte() // ch=2 (10), frameLen top 2 bits
            frame[4] = ((frameLength shr 3) and 0xFF).toByte()
            frame[5] = (((frameLength and 0x07) shl 5) or 0x1F).toByte()
            frame[6] = 0xFC.toByte() // 1 raw data block (00)
            return frame
        }
    }

    @Test
    fun `parses valid MP3 frame header`() {
        val frame = buildMp3Frame()
        val parsed = AudioFrameParser.parseMp3At(frame, 0, frame.size)
        assertNotNull(parsed)
        assertEquals(417, parsed!!.frameSize)
        assertEquals(26122L, parsed.durationMicros)
        assertEquals(false, parsed.isAac)
    }

    @Test
    fun `parses valid ADTS AAC frame header`() {
        val frame = buildAdtsFrame(300)
        val parsed = AudioFrameParser.parseAdtsAt(frame, 0, frame.size)
        assertNotNull(parsed)
        assertEquals(300, parsed!!.frameSize)
        assertEquals(23219L, parsed.durationMicros)
        assertEquals(true, parsed.isAac)
    }

    @Test
    fun `rejects invalid sync and reserved MP3 header bits`() {
        val garbage = byteArrayOf(0xFF.toByte(), 0xFB.toByte(), 0xFC.toByte(), 0x00) // reserved sample rate 3
        assertNull(AudioFrameParser.parseMp3At(garbage, 0, garbage.size))
    }

    @Test
    fun `tracks MP3 duration accurately across arbitrary chunk splits`() {
        val frame = buildMp3Frame()
        val totalFrames = 1200 // 1200 * 26.122ms = ~31.34 seconds
        val stream = ByteArray(frame.size * totalFrames)
        for (i in 0 until totalFrames) {
            System.arraycopy(frame, 0, stream, i * frame.size, frame.size)
        }

        val tracker = AudioDurationTracker(preferAac = false)
        // Feed in odd-sized chunks (e.g., 137 bytes) so headers split across chunks
        var pos = 0
        while (pos < stream.size) {
            val chunk = minOf(137, stream.size - pos)
            tracker.consume(stream, pos, chunk)
            pos += chunk
        }

        assertEquals(totalFrames.toLong(), tracker.validFrameCount)
        val durationSec = tracker.totalDurationMicros / 1_000_000L
        assertEquals(31L, durationSec)
    }

    @Test
    fun `tracks ADTS AAC duration accurately across arbitrary chunk splits`() {
        val frame = buildAdtsFrame(256)
        val totalFrames = 1350 // 1350 * 23.219ms = ~31.34 seconds
        val stream = ByteArray(frame.size * totalFrames)
        for (i in 0 until totalFrames) {
            System.arraycopy(frame, 0, stream, i * frame.size, frame.size)
        }

        val tracker = AudioDurationTracker(preferAac = true)
        var pos = 0
        while (pos < stream.size) {
            val chunk = minOf(101, stream.size - pos)
            tracker.consume(stream, pos, chunk)
            pos += chunk
        }

        assertEquals(totalFrames.toLong(), tracker.validFrameCount)
        val durationSec = tracker.totalDurationMicros / 1_000_000L
        assertEquals(31L, durationSec)
    }

    @Test
    fun `ignores isolated random 0xFF payload bytes without two consecutive valid frames`() {
        val noise = ByteArray(1024) { 0x55 }
        // Single fake header followed by non-audio bytes
        val fake = buildMp3Frame()
        System.arraycopy(fake, 0, noise, 100, 4)

        val tracker = AudioDurationTracker(preferAac = false)
        tracker.consume(noise, 0, noise.size)

        assertEquals(0L, tracker.validFrameCount)
        assertEquals(0L, tracker.totalDurationMicros)
        assertTrue(tracker.totalBytes == 1024L)
    }
}
