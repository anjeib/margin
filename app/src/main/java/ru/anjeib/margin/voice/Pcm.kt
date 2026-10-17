package ru.anjeib.margin.voice

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Sound as the speech model wants it — 16 kHz mono floats — made from whatever came in, without Android: the parts
 * here are plain Kotlin, tested on the computer (PcmTest). Decoding compressed files is AudioDecoder's job.
 */
object Pcm {
    const val RATE = 16_000

    /** What a WAV header says: [format] 1 — integer PCM, 3 — float; where the samples start and how many bytes they take. */
    data class WavInfo(val format: Int, val channels: Int, val rate: Int, val bits: Int, val dataOffset: Long, val dataLength: Long) {
        /** Our own recordings: nothing to convert. */
        val isModelReady get() = format == 1 && channels == 1 && rate == RATE && bits == 16
        val readable get() = channels in 1..8 && rate in 4_000..384_000 && (format == 1 && bits in listOf(8, 16, 24, 32) || format == 3 && bits == 32)
    }

    /**
     * Reads the chunks of a RIFF/WAVE header (not just «44 bytes»: other writers put LIST and other chunks before the data).
     * [fileLength] bounds the data: a recording cut off before its header was finished says 0 or too much.
     * Null when this is not a WAV.
     */
    fun wavInfo(head: ByteArray, fileLength: Long = head.size.toLong()): WavInfo? {
        if (head.size < 12 || String(head, 0, 4, Charsets.US_ASCII) != "RIFF" || String(head, 8, 4, Charsets.US_ASCII) != "WAVE") return null
        val b = ByteBuffer.wrap(head).order(ByteOrder.LITTLE_ENDIAN)
        var pos = 12
        var format = -1; var channels = 0; var rate = 0; var bits = 0
        while (pos + 8 <= head.size) {
            val id = String(head, pos, 4, Charsets.US_ASCII)
            val size = b.getInt(pos + 4).toLong() and 0xffffffffL
            val body = pos + 8
            if (id == "fmt " && body + 16 <= head.size) {
                format = b.getShort(body).toInt() and 0xffff
                channels = b.getShort(body + 2).toInt() and 0xffff
                rate = b.getInt(body + 4)
                bits = b.getShort(body + 14).toInt() and 0xffff
                // WAVE_FORMAT_EXTENSIBLE: the real format is the first two bytes of the sub-format GUID.
                if (format == 0xFFFE && size >= 40 && body + 26 <= head.size) format = b.getShort(body + 24).toInt() and 0xffff
            }
            if (id == "data") {
                if (format < 0) return null
                val left = fileLength - body
                val len = if (size == 0L || size > left) left else size
                return WavInfo(format, channels, rate, bits, body.toLong(), len.coerceAtLeast(0))
            }
            pos = body + (size + (size and 1)).toInt().coerceAtLeast(0)
            if (size > Int.MAX_VALUE) return null
        }
        return null
    }

    /** A 44-byte header of a 16 kHz mono 16-bit WAV with [dataLength] bytes of samples. */
    fun wavHeader(dataLength: Long): ByteArray {
        val b = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray()); b.putInt((36 + dataLength).toInt()); b.put("WAVE".toByteArray())
        b.put("fmt ".toByteArray()); b.putInt(16); b.putShort(1); b.putShort(1); b.putInt(RATE); b.putInt(RATE * 2); b.putShort(2); b.putShort(16)
        b.put("data".toByteArray()); b.putInt(dataLength.toInt())
        return b.array()
    }

    /** Interleaved samples of [bits]/[format] as floats in -1..1, all channels averaged into one. */
    fun toMono(bytes: ByteArray, length: Int, channels: Int, bits: Int, float: Boolean): FloatArray {
        val step = bits / 8
        val frames = length / (step * channels)
        val b = ByteBuffer.wrap(bytes, 0, length).order(ByteOrder.LITTLE_ENDIAN)
        val out = FloatArray(frames)
        for (i in 0 until frames) {
            var sum = 0f
            for (ch in 0 until channels) {
                val p = (i * channels + ch) * step
                sum += when {
                    float -> b.getFloat(p)
                    bits == 16 -> b.getShort(p) / 32768f
                    bits == 8 -> ((bytes[p].toInt() and 0xff) - 128) / 128f
                    bits == 24 -> ((bytes[p].toInt() and 0xff) or ((bytes[p + 1].toInt() and 0xff) shl 8) or (bytes[p + 2].toInt() shl 16)) / 8388608f
                    else -> b.getInt(p) / 2147483648f
                }
            }
            out[i] = sum / channels
        }
        return out
    }

    /**
     * From any rate to 16 kHz, piece by piece as the decoder hands them over. A windowed-sinc filter (cut a little below the
     * new Nyquist) so that a 48 kHz Telegram voice doesn't fold its highs into the speech band; going up it only interpolates.
     */
    class Resampler(private val from: Int, private val to: Int = RATE) {
        private val ratio = from.toDouble() / to
        private val cutoff = min(1.0, to.toDouble() / from) * 0.92
        private val half = if (from == to) 0 else (12 / cutoff).toInt().coerceAtMost(256)
        private var buf = FloatArray(0)
        /** Position of the next output sample, in input samples counted from buf[0]. */
        private var t = 0.0
        private var started = false

        fun push(x: FloatArray): FloatArray {
            if (from == to) return x
            // The filter looks [half] samples back: the stream starts with that much silence in front.
            buf = if (!started) FloatArray(half) + x else buf + x
            if (!started) { t = half.toDouble(); started = true }
            return drain()
        }

        fun finish(): FloatArray {
            if (from == to || !started) return FloatArray(0)
            // The last samples get their filter's right half from silence; output stops where the input did.
            val last = buf.size - 1e-9
            buf += FloatArray(half + 1)
            return drain(last).also { buf = FloatArray(0) }
        }

        private fun drain(end: Double = (buf.size - half - 1).toDouble()): FloatArray {
            val n = if (t > end) 0 else (floor((end - t) / ratio).toInt() + 1)
            val out = FloatArray(n)
            val taps = 2 * half
            for (k in 0 until n) {
                val c = t + k * ratio
                val i0 = floor(c).toInt()
                val w = table[((c - i0) * PHASES).roundToInt()] // 0..PHASES: the last set is the next sample's first
                var acc = 0f
                val first = i0 - half + 1
                for (m in 0 until taps) acc += buf[first + m] * w[m]
                out[k] = acc
            }
            t += n * ratio
            // Keep only what the next samples still need.
            val drop = (floor(t).toInt() - half).coerceAtLeast(0)
            if (drop > 0) { buf = buf.copyOfRange(drop, buf.size); t -= drop }
            return out
        }

        /**
         * The filter's weights for [PHASES] positions between two input samples, each set summing to 1: computed once,
         * so an hour of a voice is resampled in seconds (a sine per tap per sample would take minutes on a phone).
         */
        private val table: Array<FloatArray> = Array(PHASES + 1) { ph ->
            val frac = ph.toDouble() / PHASES
            val w = DoubleArray(2 * half) { m -> kernel(m - half + 1 - frac) }
            val sum = w.sum().takeIf { it != 0.0 } ?: 1.0
            FloatArray(w.size) { (w[it] / sum).toFloat() }
        }

        private fun kernel(d: Double): Double {
            if (abs(d) >= half) return 0.0
            val x = PI * d * cutoff
            val sinc = if (abs(x) < 1e-9) 1.0 else sin(x) / x
            val win = 0.5 + 0.5 * cos(PI * d / half) // Hann
            return sinc * win
        }

        private companion object { const val PHASES = 256 }
    }

    /**
     * Cuts a long recording into pieces the model reads well (≤ [maxSec]), at the quietest place between [minSec] and
     * [maxSec] — a pause between phrases, judged by energy over ~0.3 s; samples are never lost or doubled.
     */
    class Chunker(private val maxSec: Float = 25f, private val minSec: Float = 12f, private val rate: Int = RATE) {
        private var buf = FloatArray(0)
        private var size = 0

        fun push(x: FloatArray): List<FloatArray> {
            if (size + x.size > buf.size) buf = buf.copyOf(maxOf(buf.size * 2, size + x.size, rate))
            System.arraycopy(x, 0, buf, size, x.size); size += x.size
            val out = mutableListOf<FloatArray>()
            val max = (maxSec * rate).toInt()
            while (size > max) {
                val cut = pauseIn((minSec * rate).toInt(), max)
                out += buf.copyOfRange(0, cut)
                System.arraycopy(buf, cut, buf, 0, size - cut); size -= cut
            }
            return out
        }

        fun finish(): List<FloatArray> = if (size == 0) emptyList() else listOf(buf.copyOfRange(0, size)).also { size = 0 }

        /** The middle of the quietest stretch in [from, to). */
        private fun pauseIn(from: Int, to: Int): Int {
            val frame = rate / 100 // 10 ms
            val frames = to / frame
            val e = DoubleArray(frames) { f -> var s = 0.0; for (i in f * frame until (f + 1) * frame) s += buf[i] * buf[i].toDouble(); s }
            val win = 30 // 0.3 s
            var best = -1; var bestE = Double.MAX_VALUE
            var sum = 0.0
            for (f in 0 until frames) {
                sum += e[f]; if (f >= win) sum -= e[f - win]
                val mid = f - win / 2
                if (f >= win - 1 && mid * frame >= from && sum < bestE) { bestE = sum; best = mid }
            }
            return if (best < 0) to else (best * frame).coerceIn(from, to)
        }
    }

    /** Is there anything to hear: a piece of pure silence isn't given to the model. */
    fun silent(x: FloatArray): Boolean {
        var s = 0.0
        for (v in x) s += v * v.toDouble()
        return x.isEmpty() || sqrt(s / x.size) < 0.002
    }
}
