package ru.anjeib.margin.voice

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder

internal object WhisperNative {
    val loaded: Boolean = runCatching { System.loadLibrary("margin_whisper") }.isSuccess
    external fun init(modelPath: String): Long
    external fun free(ptr: Long)
    external fun transcribe(ptr: Long, samples: FloatArray, lang: String, prompt: String, threads: Int): String
}

/** Offline speech-to-text: a whisper.cpp model downloaded on demand into filesDir/models. */
object LocalWhisper {
    enum class Model(val file: String, val mb: Int) { Base("ggml-base-q5_1.bin", 60), Small("ggml-small-q5_1.bin", 190) }

    sealed interface State {
        data object Missing : State
        data class Downloading(val model: Model, val progress: Float) : State
        data class Ready(val model: Model) : State
        data class Failed(val message: String) : State
    }

    private const val BASE_URL = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/"
    private val _state = MutableStateFlow<State>(State.Missing)
    val state: StateFlow<State> = _state
    private val lock = Mutex()
    private var ctx = 0L
    private var ctxModel: Model? = null
    @Volatile private var cancel = false

    val supported get() = WhisperNative.loaded

    private fun dir(c: Context) = File(c.filesDir, "models").apply { mkdirs() }
    private fun file(c: Context, m: Model) = File(dir(c), m.file)

    fun refresh(c: Context) {
        if (_state.value is State.Downloading) return
        _state.value = Model.entries.reversed().firstOrNull { file(c, it).exists() }?.let { State.Ready(it) } ?: State.Missing
    }

    suspend fun download(c: Context, m: Model) = withContext(Dispatchers.IO) {
        cancel = false
        val target = file(c, m)
        val part = File(target.path + ".part")
        try {
            _state.value = State.Downloading(m, 0f)
            var url = URL(BASE_URL + m.file)
            var conn: HttpURLConnection
            // Hugging Face redirects to a CDN; follow manually across hosts.
            while (true) {
                conn = (url.openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = false; connectTimeout = 20_000; readTimeout = 60_000
                    if (part.exists()) setRequestProperty("Range", "bytes=${part.length()}-")
                }
                if (conn.responseCode in 301..308) { url = URL(url, conn.getHeaderField("Location")); conn.disconnect(); continue }
                break
            }
            val resumed = conn.responseCode == 206
            if (conn.responseCode !in listOf(200, 206)) error("HTTP ${conn.responseCode}")
            val total = conn.getHeaderFieldLong("Content-Length", -1L) + if (resumed) part.length() else 0
            var done = if (resumed) part.length() else 0L
            conn.inputStream.use { input ->
                java.io.FileOutputStream(part, resumed).use { out ->
                    val buf = ByteArray(1 shl 16)
                    while (true) {
                        if (cancel) error("cancelled")
                        val n = input.read(buf); if (n < 0) break
                        out.write(buf, 0, n); done += n
                        if (total > 0) _state.value = State.Downloading(m, done.toFloat() / total)
                    }
                }
            }
            part.renameTo(target)
            Model.entries.filter { it != m }.forEach { file(c, it).delete() } // keep one model
            _state.value = State.Ready(m)
        } catch (e: Exception) {
            _state.value = if (cancel) State.Missing else State.Failed(e.message ?: e.toString())
        }
    }

    fun cancelDownload() { cancel = true }

    fun delete(c: Context) {
        Model.entries.forEach { file(c, it).delete(); File(file(c, it).path + ".part").delete() }
        if (ctx != 0L) { WhisperNative.free(ctx); ctx = 0; ctxModel = null }
        _state.value = State.Missing
    }

    fun ready(c: Context): Boolean { refresh(c); return supported && _state.value is State.Ready }

    /** Transcribes a 16 kHz mono 16-bit WAV. [hints] — names/projects to bias spelling. */
    suspend fun transcribe(c: Context, wav: File, lang: String, hints: List<String>): String = withContext(Dispatchers.Default) {
        lock.withLock {
            val m = (_state.value as? State.Ready)?.model ?: error("model not ready")
            if (ctx == 0L || ctxModel != m) {
                if (ctx != 0L) WhisperNative.free(ctx)
                ctx = WhisperNative.init(file(c, m).absolutePath); ctxModel = m
                if (ctx == 0L) error("model failed to load")
            }
            val pcm = readWav(wav)
            val threads = Runtime.getRuntime().availableProcessors().coerceIn(2, 6)
            WhisperNative.transcribe(ctx, pcm, lang, hints.joinToString(", "), threads).trim()
        }
    }

    private fun readWav(f: File): FloatArray {
        RandomAccessFile(f, "r").use { raf ->
            val bytes = ByteArray((raf.length() - 44).toInt().coerceAtLeast(0))
            raf.seek(44); raf.readFully(bytes)
            val sb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
            return FloatArray(sb.remaining()) { sb.get(it) / 32768f }
        }
    }
}
