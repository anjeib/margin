package ru.anjeib.margin.voice

import android.content.Context
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineTransducerModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import ru.anjeib.margin.data.AppJson
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

/** One file of the speech model: where it is, how big, and its SHA-256 — a file that doesn't match is fetched again. */
@Serializable
data class SpeechFile(val name: String, val url: String, val size: Long, val sha256: String)

/** Which speech model the phones should have, as Margin's door says (?op=speech). */
@Serializable
data class SpeechManifest(val version: String, val files: List<SpeechFile>)

/**
 * Speech on the phone: GigaAM v3 (Sber, MIT), a model made for Russian, run by sherpa-onnx. It replaced Whisper in 0.42:
 * on Margin's own phrases (12 окт 2026) it read 23 of 25 the way the phone's parser reads what was said, Whisper small 17,
 * with half the errors under noise and ~18× faster; it writes numbers as digits («в 15») and puts the punctuation the
 * parser splits matters by. Russian only.
 *
 * Which model: Margin's door publishes it (?op=speech) — a new version only after it was measured on Margin's phrases.
 * The phone checks weekly over Wi-Fi (ModelWorker), fetches a new version next to the old one, checks every file's
 * SHA-256, and only then switches; meanwhile the old one keeps reading. Without the door, the version built in is used.
 * Each version lives in filesDir/models/speech/<version>; «current» names the one in use.
 */
object LocalSpeech {
    private const val HF = "https://huggingface.co/csukuangfj/sherpa-onnx-nemo-transducer-punct-giga-am-v3-russian-2025-12-16/resolve/main/"
    /** The model this build knows: used when the door can't be asked (first start offline, the door down). */
    val builtIn = SpeechManifest("gigaam-v3-rnnt-punct-2025-12-16", listOf(
        SpeechFile("tokens.txt", HF + "tokens.txt", 13_354, "39abae20e692998290c574e606f11a9edef2902a1995463fcff63d1490cf22b7"),
        SpeechFile("decoder.onnx", HF + "decoder.onnx", 4_600_132, "38fc7475443ea2a26f63211ca350f73ac50fff824ab7a3876ee2bd610c53bbc4"),
        SpeechFile("joiner.onnx", HF + "joiner.onnx", 2_712_896, "602ff7017a93311aad34df1437c8d7f49911353c13d6eae7a6ee7b041339465c"),
        SpeechFile("encoder.int8.onnx", HF + "encoder.int8.onnx", 224_570_820, "369f35a71bf288d3b8e0391fabd8dba5f2314088d440bca474056b7b4b6e66bf"),
    ))
    const val MB = 232

    sealed interface State {
        data object Missing : State
        data class Downloading(val progress: Float) : State
        data object Ready : State
        data class Failed(val message: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Missing)
    val state: StateFlow<State> = _state
    /** A newer version on its way while the current one keeps reading: its progress, or null. */
    private val _updating = MutableStateFlow<Float?>(null)
    val updating: StateFlow<Float?> = _updating
    private val lock = Mutex()
    private val fetching = Mutex()
    private var recognizer: OfflineRecognizer? = null
    private var recognizerOf: String? = null
    @Volatile private var cancel = false

    /** The engine is built for arm64 phones only (the app ships arm64-v8a). */
    val supported get() = android.os.Build.SUPPORTED_ABIS.contains("arm64-v8a")

    private fun root(c: Context) = File(c.filesDir, "models/speech").apply { mkdirs() }
    private fun dirOf(c: Context, version: String) = File(root(c), version)
    private fun current(c: Context): String? = File(root(c), "current").takeIf { it.exists() }?.readText()?.trim()?.ifBlank { null }
    /** The version in use, when all its files are there (its own manifest.json lists them). */
    private fun installed(c: Context): Pair<String, File>? {
        val v = current(c) ?: return null
        val d = dirOf(c, v)
        val m = runCatching { AppJson.decodeFromString(SpeechManifest.serializer(), File(d, "manifest.json").readText()) }.getOrNull() ?: return null
        return if (m.files.all { File(d, it.name).length() == it.size }) v to d else null
    }

    fun refresh(c: Context) {
        // What came before: Whisper's models (before 0.42) and the first layout of this one — space given back.
        File(c.filesDir, "models").listFiles { f -> f.name.startsWith("ggml-") || f.name == "gigaam-v3" }?.forEach { it.deleteRecursively() }
        if (_state.value is State.Downloading) return
        _state.value = if (installed(c) != null) State.Ready else if (_state.value is State.Failed) _state.value else State.Missing
    }

    fun ready(c: Context): Boolean { refresh(c); return supported && _state.value is State.Ready }

    /** The door's word on the model; null when it can't be asked now. */
    private fun fetchManifest(): SpeechManifest? {
        val door = ru.anjeib.margin.BuildConfig.PROXY_URL.ifBlank { return null }
        return runCatching {
            val c = (URL("$door?op=speech").openConnection() as HttpURLConnection).apply { connectTimeout = 8_000; readTimeout = 8_000 }
            try {
                if (c.responseCode != 200) null
                else AppJson.decodeFromString(SpeechManifest.serializer(), c.inputStream.bufferedReader().readText()).takeIf { it.files.isNotEmpty() }
            } finally { c.disconnect() }
        }.getOrNull()
    }

    /**
     * Makes sure the phone has the model the door names (or, without the door, has one at all). Fetches a missing or
     * newer version; the first download shows as [State.Downloading], an update as [updating] while the old one reads on.
     */
    suspend fun ensure(c: Context) = withContext(Dispatchers.IO) {
        if (!supported) return@withContext
        fetching.withLock {
            refresh(c)
            val have = installed(c)
            val want = fetchManifest() ?: if (have != null) return@withLock else builtIn
            if (have?.first == want.version) return@withLock
            val update = have != null
            cancel = false
            try {
                if (update) _updating.value = 0f else _state.value = State.Downloading(0f)
                fetch(c, want) { p -> if (update) _updating.value = p else _state.value = State.Downloading(p) }
                // Switched only now, whole and checked; the reader is rebuilt on the next recording.
                File(root(c), "current").writeText(want.version)
                lock.withLock { recognizer?.release(); recognizer = null; recognizerOf = null }
                root(c).listFiles { f -> f.isDirectory && f.name != want.version }?.forEach { it.deleteRecursively() }
                _state.value = State.Ready
            } catch (e: Exception) {
                android.util.Log.w("MarginSpeech", "model download failed", e)
                if (!update) _state.value = if (cancel) State.Missing else State.Failed(e.message ?: e.toString())
            } finally {
                _updating.value = null
            }
        }
    }

    /** Downloads [m] into its own folder, resuming broken files, then checks each SHA-256. */
    private fun fetch(c: Context, m: SpeechManifest, progress: (Float) -> Unit) {
        val d = dirOf(c, m.version).apply { mkdirs() }
        val total = m.files.sumOf { it.size }.toFloat()
        var before = 0L
        for (f in m.files) {
            val target = File(d, f.name)
            if (target.length() == f.size && sha256(target) == f.sha256) { before += f.size; continue }
            target.delete()
            val part = File(target.path + ".part")
            var url = URL(f.url)
            var conn: HttpURLConnection
            // Hugging Face (or any host) may redirect to its CDN: followed by hand across hosts.
            while (true) {
                conn = (url.openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = false; connectTimeout = 20_000; readTimeout = 60_000
                    if (part.exists()) setRequestProperty("Range", "bytes=${part.length()}-")
                }
                if (conn.responseCode in 301..308) { url = URL(url, conn.getHeaderField("Location")); conn.disconnect(); continue }
                break
            }
            android.util.Log.i("MarginSpeech", "${f.name}: HTTP ${conn.responseCode} from ${url.host}")
            val resumed = conn.responseCode == 206
            if (conn.responseCode !in listOf(200, 206)) error("HTTP ${conn.responseCode}")
            var done = if (resumed) part.length() else 0L
            conn.inputStream.use { input ->
                java.io.FileOutputStream(part, resumed).use { out ->
                    val buf = ByteArray(1 shl 16)
                    while (true) {
                        if (cancel) error("cancelled")
                        val n = input.read(buf); if (n < 0) break
                        out.write(buf, 0, n); done += n
                        progress(((before + done) / total).coerceAtMost(0.99f))
                    }
                }
            }
            if (part.length() != f.size || sha256(part) != f.sha256) { part.delete(); error("${f.name}: damaged") }
            if (!part.renameTo(target)) error("rename")
            before += f.size
        }
        File(d, "manifest.json").writeText(AppJson.encodeToString(SpeechManifest.serializer(), m))
        progress(1f)
    }

    private fun sha256(f: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { i -> val buf = ByteArray(1 shl 16); while (true) { val n = i.read(buf); if (n < 0) break; md.update(buf, 0, n) } }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    fun cancelDownload() { cancel = true }

    fun delete(c: Context) {
        root(c).deleteRecursively()
        recognizer?.release(); recognizer = null; recognizerOf = null
        _state.value = State.Missing
    }

    private fun open(c: Context): OfflineRecognizer {
        val (version, dir) = installed(c) ?: error("model not ready")
        recognizer?.let { if (recognizerOf == version) return it else it.release() }
        val d = dir.absolutePath + "/"
        val threads = Runtime.getRuntime().availableProcessors().coerceIn(2, 4)
        val config = OfflineRecognizerConfig(
            featConfig = FeatureConfig(sampleRate = 16000, featureDim = 64, dither = 0f),
            modelConfig = OfflineModelConfig(
                transducer = OfflineTransducerModelConfig(encoder = d + "encoder.int8.onnx", decoder = d + "decoder.onnx", joiner = d + "joiner.onnx"),
                tokens = d + "tokens.txt", numThreads = threads, modelType = "nemo_transducer", debug = false,
            ),
            decodingMethod = "greedy_search",
        )
        return OfflineRecognizer(assetManager = null, config = config).also { recognizer = it; recognizerOf = version }
    }

    /** A 16 kHz mono 16-bit WAV → the words, with punctuation. [lang] other than Russian isn't read here. */
    suspend fun transcribe(c: Context, wav: File, lang: String): String = withContext(Dispatchers.Default) {
        require(lang == "ru") { "on-phone speech is Russian only" }
        lock.withLock {
            val rec = open(c)
            val stream = rec.createStream()
            try {
                stream.acceptWaveform(readWav(wav), 16000)
                rec.decode(stream)
                rec.getResult(stream).text.trim()
            } finally { stream.release() }
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
