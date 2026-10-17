package ru.anjeib.margin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.anjeib.margin.voice.Pcm
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

/** Voice files → what the speech model reads: the WAV header, mixing, resampling, cutting at pauses. Offline. */
class PcmTest {
    private fun wav(rate: Int, channels: Int, bits: Int, frames: Int, format: Int = 1, extra: ByteArray = ByteArray(0)): ByteArray {
        val data = frames * channels * bits / 8
        val b = ByteBuffer.allocate(44 + extra.size + data).order(ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray()); b.putInt(36 + extra.size + data); b.put("WAVE".toByteArray())
        b.put("fmt ".toByteArray()); b.putInt(16); b.putShort(format.toShort()); b.putShort(channels.toShort()); b.putInt(rate)
        b.putInt(rate * channels * bits / 8); b.putShort((channels * bits / 8).toShort()); b.putShort(bits.toShort())
        b.put(extra)
        b.put("data".toByteArray()); b.putInt(data)
        return b.array()
    }

    @Test fun ourOwnRecordingIsReadAsItIs() {
        val w = Pcm.wavInfo(wav(16_000, 1, 16, 1600))!!
        assertTrue(w.isModelReady)
        assertEquals(44L, w.dataOffset)
        assertEquals(3200L, w.dataLength)
        // The header the app writes for the cloud is the same kind.
        assertTrue(Pcm.wavInfo(Pcm.wavHeader(3200) + ByteArray(3200))!!.isModelReady)
    }

    @Test fun otherWavsAreConvertedNotTakenAsIs() {
        val w = Pcm.wavInfo(wav(44_100, 2, 16, 441))!!
        assertFalse(w.isModelReady)
        assertTrue(w.readable)
        assertEquals(44_100, w.rate); assertEquals(2, w.channels)
        val f = Pcm.wavInfo(wav(48_000, 1, 32, 10, format = 3))!!
        assertTrue(f.readable); assertFalse(f.isModelReady)
    }

    @Test fun chunksBeforeTheDataAreSkipped() {
        // A LIST chunk (8 + 26 bytes; odd size padded to 26) between «fmt » and «data», as many recorders write.
        val list = ByteBuffer.allocate(34).order(ByteOrder.LITTLE_ENDIAN).apply { put("LIST".toByteArray()); putInt(25); put(ByteArray(26)) }.array()
        val w = Pcm.wavInfo(wav(16_000, 1, 16, 100, extra = list))!!
        assertEquals(44L + 34, w.dataOffset)
        assertEquals(200L, w.dataLength)
    }

    @Test fun notAWavIsNotAWav() {
        assertNull(Pcm.wavInfo("OggS\u0000\u0002".toByteArray() + ByteArray(60)))
        assertNull(Pcm.wavInfo(ByteArray(0)))
        // ftyp — an m4a
        assertNull(Pcm.wavInfo(byteArrayOf(0, 0, 0, 0x20) + "ftypM4A ".toByteArray() + ByteArray(40)))
    }

    @Test fun aRecordingCutOffBeforeItsHeaderWasFinishedIsReadToTheEnd() {
        val bytes = wav(16_000, 1, 16, 1000)
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).putInt(40, 0)
        assertEquals(2000L, Pcm.wavInfo(bytes, bytes.size.toLong())!!.dataLength)
        // And a header claiming more than the file holds.
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).putInt(40, 1_000_000)
        assertEquals(2000L, Pcm.wavInfo(bytes, bytes.size.toLong())!!.dataLength)
    }

    @Test fun stereoIsAveraged() {
        val b = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putShort(16384).putShort(0).putShort(-32768).putShort(-32768).array()
        val m = Pcm.toMono(b, 8, 2, 16, false)
        assertEquals(2, m.size)
        assertEquals(0.25f, m[0], 1e-4f)
        assertEquals(-1f, m[1], 1e-4f)
    }

    private fun tone(hz: Double, rate: Int, secs: Double, amp: Double = 0.5) = FloatArray((rate * secs).toInt()) { (amp * sin(2 * PI * hz * it / rate)).toFloat() }
    private fun rms(x: FloatArray, from: Int = 0, to: Int = x.size) = sqrt((from until to).sumOf { x[it] * x[it].toDouble() } / (to - from))

    /** Resampled in uneven pieces, as a decoder hands them over. */
    private fun resample(x: FloatArray, from: Int): FloatArray {
        val r = Pcm.Resampler(from)
        val out = mutableListOf<Float>()
        var i = 0; var step = 997
        while (i < x.size) { val n = minOf(step, x.size - i); out += r.push(x.copyOfRange(i, i + n)).toList(); i += n; step = if (step == 997) 4096 else 997 }
        out += r.finish().toList()
        return out.toFloatArray()
    }

    @Test fun telegramsFortyEightKilohertzBecomesSixteen() {
        val y = resample(tone(440.0, 48_000, 2.0), 48_000)
        assertEquals(32_000.0, y.size.toDouble(), 2.0)
        // The speech band stays as loud as it was…
        assertEquals(0.5 / sqrt(2.0), rms(y, 2000, 30_000), 0.01)
        // …and at the same pitch: the zero crossings of 440 Hz over one second.
        var crossings = 0
        for (i in 8001 until 24_000) if ((y[i - 1] < 0) != (y[i] < 0)) crossings++
        assertEquals(880.0, crossings.toDouble(), 4.0)
    }

    @Test fun highsAboveTheNewNyquistDontFoldIntoSpeech() {
        // 12 kHz would land at 4 kHz without the filter.
        val y = resample(tone(12_000.0, 48_000, 1.0), 48_000)
        assertTrue("aliasing ${rms(y, 1000, 15_000)}", rms(y, 1000, 15_000) < 0.02)
    }

    @Test fun otherRatesUpAndDown() {
        for (rate in listOf(8_000, 22_050, 44_100)) {
            val y = resample(tone(300.0, rate, 1.5), rate)
            assertEquals("length at $rate", 24_000.0, y.size.toDouble(), 2.0)
            assertEquals("level at $rate", 0.5 / sqrt(2.0), rms(y, 2000, 22_000), 0.015)
        }
        val same = FloatArray(100) { it / 100f }
        assertTrue(same.contentEquals(Pcm.Resampler(16_000).push(same)))
    }

    /** Speech-like bursts with pauses at known places. */
    private fun speech(phrases: List<Double>, pause: Double): Pair<FloatArray, List<IntRange>> {
        val parts = mutableListOf<FloatArray>(); val pauses = mutableListOf<IntRange>()
        var at = 0
        phrases.forEachIndexed { i, secs ->
            val p = tone(220.0 + 40 * i, 16_000, secs, 0.3); parts += p; at += p.size
            val q = FloatArray((16_000 * pause).toInt()) { ((it * 7919) % 13 - 6) / 6000f } // a little room noise
            pauses += at until at + q.size; parts += q; at += q.size
        }
        val all = FloatArray(at); var k = 0
        parts.forEach { System.arraycopy(it, 0, all, k, it.size); k += it.size }
        return all to pauses
    }

    @Test fun aLongVoiceIsCutAtPausesIntoShortPieces() {
        val (x, pauses) = speech(listOf(6.0, 9.0, 7.0, 8.0, 5.0, 11.0, 4.0, 9.0, 3.0), pause = 0.6)
        val c = Pcm.Chunker()
        val pieces = mutableListOf<FloatArray>()
        var i = 0
        while (i < x.size) { val n = minOf(8000, x.size - i); pieces += c.push(x.copyOfRange(i, i + n)); i += n }
        pieces += c.finish()
        assertTrue(pieces.size >= 3)
        pieces.forEach { assertTrue("piece of ${it.size / 16_000f} s", it.size <= 25 * 16_000) }
        // Nothing lost or doubled.
        val joined = pieces.flatMap { it.toList() }.toFloatArray()
        assertTrue(joined.contentEquals(x))
        // Every cut is inside a pause, not in the middle of a phrase.
        var end = 0
        pieces.dropLast(1).forEach { p ->
            end += p.size
            assertTrue("cut at ${end / 16_000f} s", pauses.any { end in it })
        }
    }

    @Test fun withoutPausesItStillCutsAtTheLimit() {
        val x = tone(300.0, 16_000, 60.0)
        val c = Pcm.Chunker()
        val pieces = c.push(x) + c.finish()
        pieces.forEach { assertTrue(it.size <= 25 * 16_000) }
        assertEquals(x.size, pieces.sumOf { it.size })
    }

    @Test fun aShortVoiceIsOnePiece() {
        val x = tone(300.0, 16_000, 7.0)
        val c = Pcm.Chunker()
        val pieces = c.push(x) + c.finish()
        assertEquals(1, pieces.size)
        assertNotNull(pieces.single())
        assertTrue(Pcm.silent(FloatArray(16_000)))
        assertFalse(Pcm.silent(x))
        assertTrue(abs(rms(x) - 0.5 / sqrt(2.0)) < 0.01)
    }
}
