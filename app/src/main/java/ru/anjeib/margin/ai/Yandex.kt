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
object Yandex {
    private const val BASE = "https://llm.api.cloud.yandex.net/v1"

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
        withContext(Dispatchers.IO) {
            fun body(withFormat: Boolean) = buildJsonObject {
                put("model", "gpt://$folder/$model/latest")
                put("messages", buildJsonArray {
                    add(buildJsonObject { put("role", "system"); put("content", system) })
                    messages.forEach { (r, c) -> add(buildJsonObject { put("role", r); put("content", c) }) }
                })
                put("temperature", 0.2)
                put("max_tokens", 1500)
                if (withFormat) put("response_format", buildJsonObject { put("type", "json_object") })
            }.toString()
            var (code, text) = try { post(apiKey, folder, body(json)) } catch (e: java.io.IOException) { throw AiException(ru.anjeib.margin.i18n.L.s.errNet) }
            if (json && code == 400) { val r = try { post(apiKey, folder, body(false)) } catch (e: java.io.IOException) { throw AiException(ru.anjeib.margin.i18n.L.s.errNet) }; code = r.first; text = r.second }
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
    suspend fun stt(apiKey: String, folder: String, wav: ByteArray, lang: String): LlmReply = withContext(Dispatchers.IO) {
        val pcm = if (wav.size > 44) wav.copyOfRange(44, wav.size) else wav
        val url = URL("https://stt.api.cloud.yandex.net/speech/v1/stt:recognize?folderId=$folder&lang=$lang&format=lpcm&sampleRateHertz=16000")
        val c = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; doOutput = true; connectTimeout = 20_000; readTimeout = 60_000
            setRequestProperty("Authorization", "Api-Key $apiKey")
        }
        try {
            c.outputStream.use { it.write(pcm) }
            val code = c.responseCode
            val text = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) throw AiException("SpeechKit $code")
            val result = AppJson.parseToJsonElement(text).jsonObject["result"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val seconds = pcm.size / 32_000.0
            LlmReply(result.replaceFirstChar { it.uppercase() }, kotlin.math.ceil(seconds / 15.0).coerceAtLeast(1.0) * 0.1626)
        } catch (e: java.io.IOException) {
            throw AiException(ru.anjeib.margin.i18n.L.s.errNet)
        } finally { c.disconnect() }
    }

    /** Key check: the model list is free. */
    suspend fun check(apiKey: String, folder: String): String = withContext(Dispatchers.IO) {
        val c = (URL("$BASE/models").openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000; readTimeout = 30_000
            setRequestProperty("Authorization", "Api-Key $apiKey"); setRequestProperty("OpenAI-Project", folder)
        }
        try {
            val code = c.responseCode
            if (code !in 200..299) throw AiException(if (code == 401 || code == 403) ru.anjeib.margin.i18n.L.s.errBadKey else "Yandex $code")
            ru.anjeib.margin.i18n.L.s.connected + " · Yandex AI Studio"
        } catch (e: java.io.IOException) {
            throw AiException(ru.anjeib.margin.i18n.L.s.errNet)
        } finally { c.disconnect() }
    }

    private fun post(key: String, folder: String, body: String): Pair<Int, String> {
        val c = (URL("$BASE/chat/completions").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; doOutput = true; connectTimeout = 20_000; readTimeout = 120_000
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Api-Key $key")
            setRequestProperty("OpenAI-Project", folder)
        }
        c.outputStream.use { it.write(body.toByteArray()) }
        val code = c.responseCode
        val text = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
        c.disconnect()
        return code to text
    }
}
