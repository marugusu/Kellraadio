package app.radiorecalarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class IcyStreamProcessorTest {

    private fun buildIcyBlock(text: String): ByteArray {
        val textBytes = text.toByteArray(Charsets.UTF_8)
        val paddedBlocks = (textBytes.size + 15) / 16
        val block = ByteArray(1 + paddedBlocks * 16)
        block[0] = paddedBlocks.toByte()
        System.arraycopy(textBytes, 0, block, 1, textBytes.size)
        return block
    }

    private fun buildIcyWireStream(
        metaInt: Int,
        audio: ByteArray,
        metadataAtAudioOffset: Map<Int, String>
    ): ByteArray {
        val out = ByteArrayOutputStream()
        var pos = 0
        while (pos < audio.size) {
            val chunk = minOf(metaInt, audio.size - pos)
            out.write(audio, pos, chunk)
            pos += chunk
            if (chunk == metaInt && pos <= audio.size) {
                val meta = metadataAtAudioOffset[pos]
                if (meta != null) {
                    out.write(buildIcyBlock(meta))
                } else {
                    out.write(0) // lengthByte = 0
                }
            }
        }
        return out.toByteArray()
    }

    @Test
    fun `parseStreamTitle handles apostrophes inside artist and title`() {
        val raw = "StreamTitle='Guns N' Roses - Sweet Child O' Mine';StreamUrl='https://example.com';"
        assertEquals("Guns N' Roses - Sweet Child O' Mine", IcyStreamProcessor.parseStreamTitle(raw))
    }

    @Test
    fun `parseStreamTitle returns empty string when explicitly cleared and null when absent`() {
        assertEquals("", IcyStreamProcessor.parseStreamTitle("StreamTitle='';StreamUrl='';"))
        assertNull(IcyStreamProcessor.parseStreamTitle("StreamUrl='https://example.com';"))
    }

    @Test
    fun `splits audio cleanly at next MP3 frame sync after ICY metadata change across tiny reads`() {
        val metaInt = 500
        val mp3Frame = AudioFrameAnalyzerTest.buildMp3Frame(0x33)

        // Track 1: 3 full frames (1251 bytes)
        val track1Raw = ByteArrayOutputStream().apply {
            repeat(3) { write(mp3Frame) }
        }.toByteArray()

        // Track 2: 3 full frames (1251 bytes)
        val track2Raw = ByteArrayOutputStream().apply {
            repeat(3) { write(AudioFrameAnalyzerTest.buildMp3Frame(0x44)) }
        }.toByteArray()

        // Combined raw audio stream: track1 + track2 (total 2502 bytes)
        // ICY block for Track 2 is inserted at byte 1000 (right inside Track 1's 3rd frame, which spans 834..1251!)
        val combinedAudio = track1Raw + track2Raw
        val wireBytes = buildIcyWireStream(
            metaInt = metaInt,
            audio = combinedAudio,
            metadataAtAudioOffset = mapOf(
                500 to "StreamTitle='Artist One - Song One';",
                1000 to "StreamTitle='Artist Two - Song Two';"
            )
        )
        val tracks = mutableListOf<Pair<String, ByteArrayOutputStream>>()
        tracks.add("INITIAL" to ByteArrayOutputStream())

        val processor = IcyStreamProcessor(
            metaInt = metaInt,
            preferAac = false,
            onAudioBytes = { buf, off, len -> tracks.last().second.write(buf, off, len) },
            onMetadataBoundary = { newTitle -> tracks.add(newTitle to ByteArrayOutputStream()) }
        )

        // Feed in 73-byte chunks so ICY blocks and frame headers split across read boundaries
        var pos = 0
        while (pos < wireBytes.size) {
            val chunk = minOf(73, wireBytes.size - pos)
            processor.process(wireBytes, pos, chunk)
            pos += chunk
        }
        processor.flush()

        assertEquals(3, tracks.size)
        assertEquals("Artist One - Song One", tracks[1].first)
        assertEquals("Artist Two - Song Two", tracks[2].first)

        // Song One starts at byte 834 (3rd MP3 frame, because ICY block arrived at byte 500 mid-2nd-frame!)
        // and ends cleanly at byte 1251 (start of Track 2's 1st MP3 frame!)
        val songOneBytes = tracks[1].second.toByteArray()
        val songTwoBytes = tracks[2].second.toByteArray()

        // Verify Song Two starts cleanly on an MP3 frame header (0xFF 0xFB) and contains all 3 frames of Track 2
        assertEquals(0xFF, songTwoBytes[0].toInt() and 0xFF)
        assertEquals(0xFB, songTwoBytes[1].toInt() and 0xFF)
        assertEquals(track2Raw.size, songTwoBytes.size)
        assertTrue(songOneBytes.isNotEmpty())
        assertEquals(0xFF, songOneBytes[0].toInt() and 0xFF)
        assertEquals(0xFB, songOneBytes[1].toInt() and 0xFF)
    }

    @Test
    fun `falls back cleanly when no valid frame sync is found within safety cap`() {
        val metaInt = 100
        val audio = ByteArray(100 + IcyStreamProcessor.MAX_SYNC_SCAN_BYTES + 100) { idx ->
            if (idx < 100) 0x11 else 0x22
        }
        val wireBytes = buildIcyWireStream(
            metaInt = metaInt,
            audio = audio,
            metadataAtAudioOffset = mapOf(100 to "StreamTitle='New - Track';")
        )
        val splits = mutableListOf<String>()
        val totalReceived = ByteArrayOutputStream()

        val processor = IcyStreamProcessor(
            metaInt = metaInt,
            preferAac = false,
            onAudioBytes = { buf, off, len -> totalReceived.write(buf, off, len) },
            onMetadataBoundary = { splits.add(it) }
        )

        processor.process(wireBytes, 0, wireBytes.size)
        processor.flush()

        assertEquals(listOf("New - Track"), splits)
        assertEquals(100 + IcyStreamProcessor.MAX_SYNC_SCAN_BYTES + 100, totalReceived.size())
    }

    @Test
    fun `ignores duplicate consecutive StreamTitle and empty lengthByte zero blocks`() {
        val metaInt = 64
        val frame = AudioFrameAnalyzerTest.buildMp3Frame()
        val wireStream = ByteArrayOutputStream()
        wireStream.write(frame, 0, 64)
        wireStream.write(buildIcyBlock("StreamTitle='Same - Song';"))
        wireStream.write(frame, 64, 64)
        wireStream.write(byteArrayOf(0x00)) // lengthByte = 0 (no metadata)
        wireStream.write(frame, 128, 64)
        wireStream.write(buildIcyBlock("StreamTitle='Same - Song';")) // repeated title
        wireStream.write(frame, 192, frame.size - 192)

        val splits = mutableListOf<String>()
        val processor = IcyStreamProcessor(
            metaInt = metaInt,
            preferAac = false,
            onAudioBytes = { _, _, _ -> },
            onMetadataBoundary = { splits.add(it) }
        )

        val wire = wireStream.toByteArray()
        processor.process(wire, 0, wire.size)
        processor.flush()

        assertEquals(listOf("Same - Song"), splits)
    }
}
