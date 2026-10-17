package ru.anjeib.margin.voice

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteOrder

/** The file holds no sound the phone can read (a damaged file, a format Android has no decoder for). */
class Unreadable(message: String) : Exception(message)

/**
 * Any voice file → 16 kHz mono floats, handed over piece by piece ([sink]) so an hour of audio never sits in memory.
 * WAV is read here (our own recordings exactly as they are; other rates and channels converted); everything else —
 * Telegram and WhatsApp voices (Ogg/Opus), m4a/aac, mp3, flac, amr — through Android's own MediaExtractor + MediaCodec.
 */
object AudioDecoder {
    /** [progress] 0..1 by the position in the file; [alive] false stops it (the coroutine was cancelled). */
    fun decode(f: File, sink: (FloatArray) -> Unit, progress: (Float) -> Unit = {}, alive: () -> Boolean = { true }) {
        val head = ByteArray(minOf(f.length(), 4096L).toInt())
        RandomAccessFile(f, "r").use { it.readFully(head) }
        val wav = Pcm.wavInfo(head, f.length())
        if (wav != null && wav.readable) readWav(f, wav, sink, progress, alive) else decodeMedia(f, sink, progress, alive)
    }

    /** The whole file as a 16 kHz mono 16-bit WAV — for the cloud; our own recordings go as they are. */
    fun wav16k(f: File): ByteArray {
        val head = ByteArray(minOf(f.length(), 4096L).toInt())
        RandomAccessFile(f, "r").use { it.readFully(head) }
        if (Pcm.wavInfo(head, f.length())?.isModelReady == true) return f.readBytes()
        val pcm = java.io.ByteArrayOutputStream()
        decode(f, sink = { x ->
            val b = java.nio.ByteBuffer.allocate(x.size * 2).order(ByteOrder.LITTLE_ENDIAN)
            for (v in x) b.putShort((v.coerceIn(-1f, 1f) * 32767).toInt().toShort())
            pcm.write(b.array())
        })
        return Pcm.wavHeader(pcm.size().toLong()) + pcm.toByteArray()
    }

    private fun readWav(f: File, w: Pcm.WavInfo, sink: (FloatArray) -> Unit, progress: (Float) -> Unit, alive: () -> Boolean) {
        val frame = w.channels * w.bits / 8
        val rs = Pcm.Resampler(w.rate)
        RandomAccessFile(f, "r").use { raf ->
            raf.seek(w.dataOffset)
            val buf = ByteArray(frame * (w.rate / 2)) // half a second
            var left = w.dataLength - w.dataLength % frame
            while (left > 0 && alive()) {
                val n = raf.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
                if (n <= 0) break
                val whole = n - n % frame
                left -= n
                sink(rs.push(Pcm.toMono(buf, whole, w.channels, w.bits, w.format == 3)))
                progress(1f - left.toFloat() / w.dataLength)
            }
        }
        sink(rs.finish())
    }

    /** What the reader of the sound throws is its own business, not «can't read the file». */
    private class Passing(val error: Throwable) : RuntimeException()

    private fun decodeMedia(f: File, give: (FloatArray) -> Unit, progress: (Float) -> Unit, alive: () -> Boolean) {
        val sink: (FloatArray) -> Unit = { try { give(it) } catch (t: Throwable) { throw Passing(t) } }
        val ex = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            runCatching { ex.setDataSource(f.absolutePath) }.onFailure { throw Unreadable("no container: ${it.message}") }
            val track = (0 until ex.trackCount).firstOrNull { ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
                ?: throw Unreadable("no audio track")
            ex.selectTrack(track)
            val fmt = ex.getTrackFormat(track)
            val mime = fmt.getString(MediaFormat.KEY_MIME)!!
            val total = if (fmt.containsKey(MediaFormat.KEY_DURATION)) fmt.getLong(MediaFormat.KEY_DURATION) else 0L
            codec = runCatching { MediaCodec.createDecoderByType(mime).apply { configure(fmt, null, null, 0); start() } }
                .getOrElse { throw Unreadable("no decoder for $mime") }
            var rate = fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var float = false
            var rs = Pcm.Resampler(rate)
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var bytes = ByteArray(0)
            var heard = false
            while (alive()) {
                if (!inputDone) {
                    val i = codec.dequeueInputBuffer(10_000)
                    if (i >= 0) {
                        val n = ex.readSampleData(codec.getInputBuffer(i)!!, 0)
                        if (n < 0) { codec.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inputDone = true }
                        else { codec.queueInputBuffer(i, 0, n, ex.sampleTime, 0); ex.advance() }
                    }
                }
                val o = codec.dequeueOutputBuffer(info, 10_000)
                when {
                    o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val out = codec.outputFormat
                        val r = out.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channels = out.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        float = out.containsKey(MediaFormat.KEY_PCM_ENCODING) && out.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                        if (r != rate) { sink(rs.finish()); rate = r; rs = Pcm.Resampler(rate) }
                    }
                    o >= 0 -> {
                        if (info.size > 0) {
                            val b = codec.getOutputBuffer(o)!!.order(ByteOrder.LITTLE_ENDIAN)
                            b.position(info.offset); b.limit(info.offset + info.size)
                            if (bytes.size < info.size) bytes = ByteArray(info.size)
                            b.get(bytes, 0, info.size)
                            sink(rs.push(Pcm.toMono(bytes, info.size, channels, if (float) 32 else 16, float)))
                            heard = true
                            if (total > 0) progress((info.presentationTimeUs.toFloat() / total).coerceIn(0f, 1f))
                        }
                        codec.releaseOutputBuffer(o, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                    }
                }
            }
            sink(rs.finish())
            if (!heard && alive()) throw Unreadable("no sound decoded")
        } catch (e: Unreadable) {
            throw e
        } catch (e: Passing) {
            throw e.error
        } catch (e: Exception) {
            // MediaCodec says «IllegalStateException» about a damaged stream; for the user it is the same: can't read it.
            throw Unreadable(e.message ?: e.toString())
        } finally {
            runCatching { codec?.stop() }; runCatching { codec?.release() }
            ex.release()
        }
    }
}
