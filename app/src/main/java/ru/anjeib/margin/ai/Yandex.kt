package ru.anjeib.margin.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import ru.anjeib.margin.data.AppJson
import java.net.HttpURLConnection
import java.net.URL

/** Token usage as Yandex reports it. */
data class Usage(val input: Int, val cached: Int, val output: Int)

data class YaReply(val content: String, val usage: Usage, val rub: Double)

/**
 * Yandex AI Studio through its OpenAI-compatible endpoint. Models are addressed as gpt://<folder>/<name>/latest.
 * Prices: rub per 1000 tokens incl. VAT (input, cached, output) — official page, October 2026.
 */
/**
 * Every request goes with «x-data-logging-enabled: false»: Yandex then keeps nothing of what was sent
 * (by default AI Studio logs requests). The user's tasks, notes and names are personal data.
 */
object Yandex {
    private const val BASE = "https://llm.api.cloud.yandex.net/v1"

    /**
     * Where a request goes. With a key (the developer's debug build) — straight to Yandex. Without one — through
     * Margin's own door in Yandex Cloud (server/proxy), let in by the user's invite code; the folder is filled in there.
     */
    class Door(val key: String, val folder: String, val invite: String = "", val device: String = "") {
        val proxy get() = key.isBlank() && invite.isNotBlank() && ru.anjeib.margin.BuildConfig.PROXY_URL.isNotBlank()
        val modelFolder get() = if (proxy) "-" else folder
        fun url(op: String, direct: String) = if (proxy) ru.anjeib.margin.BuildConfig.PROXY_URL + "?op=" + op else direct
        fun sign(c: HttpURLConnection) {
            if (proxy) { c.setRequestProperty("X-Margin-Code", invite.trim()); if (device.isNotBlank()) c.setRequestProperty("X-Margin-Device", device) }
            else { c.setRequestProperty("Authorization", "Api-Key $key"); c.setRequestProperty("OpenAI-Project", folder); c.setRequestProperty("x-folder-id", folder) }
            c.setRequestProperty("x-data-logging-enabled", "false")
        }
        /** The door said no: the code is wrong or switched off. */
        fun refused(code: Int) = proxy && code == 401
    }

    val prices = mapOf(
        "aliceai-llm-flash" to Triple(0.1, 0.025, 0.2),
        "aliceai-llm" to Triple(0.5, 0.5, 1.2),
        "qwen3.6-35b-a3b" to Triple(0.2, 0.05, 0.3),
        "deepseek-v4.1-flash" to Triple(0.3, 0.075, 0.5),
        "gpt-oss-20b" to Triple(0.1, 0.1, 0.1),
        "gpt-oss-120b" to Triple(0.3, 0.3, 0.3),
        "yandexgpt-lite" to Triple(0.2, 0.2, 0.2),
        "yandexgpt-5.1" to Triple(0.8, 0.8, 0.8),
    )

    fun cost(model: String, u: Usage): Double {
        val (i, c, o) = prices[model] ?: return 0.0
        return ((u.input - u.cached).coerceAtLeast(0) * i + u.cached * c + u.output * o) / 1000.0
    }

    suspend fun chat(apiKey: String, folder: String, model: String, system: String, messages: List<Pair<String, String>>, json: Boolean = true): YaReply =
        chat(Door(apiKey, folder), model, system, messages, json)

    suspend fun chat(door: Door, model: String, system: String, messages: List<Pair<String, String>>, json: Boolean = true): YaReply =
        withContext(Dispatchers.IO) {
            fun body(withFormat: Boolean) = buildJsonObject {
                put("model", "gpt://${door.modelFolder}/$model/latest")
                put("messages", buildJsonArray {
                    add(buildJsonObject { put("role", "system"); put("content", system) })
                    messages.forEach { (r, c) -> add(buildJsonObject { put("role", r); put("content", c) }) }
                })
                put("temperature", 0.2)
                put("max_tokens", 1500)
                if (withFormat) put("response_format", buildJsonObject { put("type", "json_object") })
            }.toString()
            var (code, text) = try { post(door, body(json)) } catch (e: java.io.IOException) { throw AiException(ru.anjeib.margin.i18n.L.s.errNet) }
            if (json && code == 400) { val r = try { post(door, body(false)) } catch (e: java.io.IOException) { throw AiException(ru.anjeib.margin.i18n.L.s.errNet) }; code = r.first; text = r.second }
            if (door.refused(code)) throw AiException(ru.anjeib.margin.i18n.L.s.errInvite)
            if (code !in 200..299) { android.util.Log.w("Margin", "Yandex $code: ${text.take(300)}"); throw AiException(ru.anjeib.margin.i18n.L.s.errService) }
            val root = AppJson.parseToJsonElement(text).jsonObject
            val content = root["choices"]?.jsonArray?.firstOrNull()?.jsonObject?.get("message")?.jsonObject
                ?.get("content")?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: throw AiException(EMPTY)
            val u = root["usage"] as? JsonObject
            val usage = Usage(
                input = u?.get("prompt_tokens")?.jsonPrimitive?.intOrNull ?: 0,
                cached = (u?.get("prompt_tokens_details") as? JsonObject)?.get("cached_tokens")?.jsonPrimitive?.intOrNull ?: 0,
                output = u?.get("completion_tokens")?.jsonPrimitive?.intOrNull ?: 0,
            )
            YaReply(content, usage, cost(model, usage))
        }

    /** SpeechKit v1 synchronous recognition of a 16 kHz mono WAV (≤30 s). Billed per started 15 s. */
    suspend fun stt(door: Door, wav: ByteArray, lang: String): LlmReply = withContext(Dispatchers.IO) {
        val pcm = if (wav.size > 44) wav.copyOfRange(44, wav.size) else wav
        val url = URL(door.url("stt&lang=$lang", "https://stt.api.cloud.yandex.net/speech/v1/stt:recognize?folderId=${door.folder}&lang=$lang&format=lpcm&sampleRateHertz=16000"))
        val c = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; doOutput = true; connectTimeout = 20_000; readTimeout = 60_000
            door.sign(this); setRequestProperty("Content-Type", "application/octet-stream")
        }
        try {
            c.outputStream.use { it.write(pcm) }
            val code = c.responseCode
            val text = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
            if (door.refused(code)) throw AiException(ru.anjeib.margin.i18n.L.s.errInvite)
            if (code !in 200..299) throw AiException("SpeechKit $code")
            val result = AppJson.parseToJsonElement(text).jsonObject["result"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val seconds = pcm.size / 32_000.0
            LlmReply(result.replaceFirstChar { it.uppercase() }, kotlin.math.ceil(seconds / 15.0).coerceAtLeast(1.0) * 0.1626)
        } catch (e: java.io.IOException) {
            throw AiException(ru.anjeib.margin.i18n.L.s.errNet)
        } finally { c.disconnect() }
    }

    /**
     * Text on a photo: Yandex Vision OCR (Russian and English). Needs the «ai.vision.user» role on the service account;
     * without it the call fails and the photo simply stays without recognised text.
     */
    suspend fun ocr(door: Door, jpeg: ByteArray): LlmReply = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("mimeType", "JPEG"); put("model", "page")
            put("languageCodes", buildJsonArray { add(kotlinx.serialization.json.JsonPrimitive("ru")); add(kotlinx.serialization.json.JsonPrimitive("en")) })
            put("content", android.util.Base64.encodeToString(jpeg, android.util.Base64.NO_WRAP))
        }.toString()
        val c = (URL(door.url("ocr", "https://ocr.api.cloud.yandex.net/ocr/v1/recognizeText")).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; doOutput = true; connectTimeout = 20_000; readTimeout = 60_000
            door.sign(this); setRequestProperty("Content-Type", "application/json")
        }
        try {
            c.outputStream.use { it.write(body.toByteArray()) }
            val code = c.responseCode
            val text = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
            if (door.refused(code)) throw AiException(ru.anjeib.margin.i18n.L.s.errInvite)
            if (code !in 200..299) throw AiException("Vision $code")
            val full = AppJson.parseToJsonElement(text).jsonObject["result"]?.jsonObject?.get("textAnnotation")?.jsonObject
                ?.get("fullText")?.jsonPrimitive?.contentOrNull.orEmpty()
            LlmReply(full.trim(), 0.13)
        } catch (e: java.io.IOException) {
            throw AiException(ru.anjeib.margin.i18n.L.s.errNet)
        } finally { c.disconnect() }
    }

    /** Key check: the model list is free. */
    suspend fun check(door: Door): String = withContext(Dispatchers.IO) {
        val c = (URL(door.url("models", "$BASE/models")).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000; readTimeout = 30_000
            door.sign(this)
        }
        try {
            val code = c.responseCode
            if (door.refused(code)) throw AiException(ru.anjeib.margin.i18n.L.s.errInvite)
            if (code !in 200..299) throw AiException(if (code == 401 || code == 403) ru.anjeib.margin.i18n.L.s.errBadKey else "Yandex $code")
            ru.anjeib.margin.i18n.L.s.connected + " · Yandex AI Studio"
        } catch (e: java.io.IOException) {
            throw AiException(ru.anjeib.margin.i18n.L.s.errNet)
        } finally { c.disconnect() }
    }

    private fun post(door: Door, body: String): Pair<Int, String> {
        val c = (URL(door.url("chat", "$BASE/chat/completions")).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; doOutput = true; connectTimeout = 20_000; readTimeout = 120_000
            setRequestProperty("Content-Type", "application/json")
            door.sign(this)
        }
        c.outputStream.use { it.write(body.toByteArray()) }
        val code = c.responseCode
        val text = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
        c.disconnect()
        return code to text
    }
}
