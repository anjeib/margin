package ru.anjeib.margin.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import ru.anjeib.margin.data.AppJson
import ru.anjeib.margin.i18n.L
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class AiException(message: String) : Exception(message)

/** The model answered with nothing — worth one more try, never worth showing as is. */
const val EMPTY = "empty"

data class AiReply(val content: String, val costUsd: Double)

data class ModelInfo(val id: String, val name: String, val promptPerM: Double, val completionPerM: Double, val audio: Boolean = false)

object OpenRouter {
    private const val BASE = "https://openrouter.ai/api/v1"

    suspend fun chat(
        apiKey: String,
        model: String,
        system: String,
        messages: List<Pair<String, String>>, // role to content
        json: Boolean = true,
        /** Quick parsing: ask reasoning models to think as little as possible. */
        quick: Boolean = false,
        /** Data URLs of images attached to the last user message. */
        images: List<String> = emptyList(),
    ): AiReply = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) throw AiException(L.s.errNoKey)
        fun body(withFormat: Boolean) = buildJsonObject {
            put("model", model)
            put("messages", buildJsonArray {
                add(buildJsonObject { put("role", "system"); put("content", system) })
                messages.forEachIndexed { i, (r, c) ->
                    add(buildJsonObject {
                        put("role", r)
                        if (i == messages.lastIndex && images.isNotEmpty()) put("content", buildJsonArray {
                            add(buildJsonObject { put("type", "text"); put("text", c) })
                            images.forEach { url ->
                                add(buildJsonObject { put("type", "image_url"); put("image_url", buildJsonObject { put("url", url) }) })
                            }
                        }) else put("content", c)
                    })
                }
            })
            put("temperature", 0.2)
            if (withFormat) put("response_format", buildJsonObject { put("type", "json_object") })
            put("usage", buildJsonObject { put("include", true) })
            if (quick) put("reasoning", buildJsonObject { put("effort", "minimal"); put("exclude", true) })
        }.toString()

        var (code, text) = post("$BASE/chat/completions", apiKey, body(json))
        // Some models don't accept response_format; the prompt alone asks for JSON, so retry without it.
        if (json && code == 400 && text.contains("response_format", ignoreCase = true)) {
            val r = post("$BASE/chat/completions", apiKey, body(false)); code = r.first; text = r.second
        }
        if (code !in 200..299) throw AiException(errorMessage(code, text))
        val root = runCatching { AppJson.parseToJsonElement(text).jsonObject }.getOrNull()
            ?: throw AiException(L.s.errWeird)
        root["error"]?.let { throw AiException(errorMessage(code, text)) }
        val content = root["choices"]?.jsonArray?.firstOrNull()?.jsonObject
            ?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.contentOrNull
            ?: throw AiException(L.s.errEmpty)
        val cost = (root["usage"] as? JsonObject)?.get("cost")?.jsonPrimitive?.doubleOrNull ?: 0.0
        AiReply(content, cost)
    }

    /** Speech to text: sends a WAV recording to an audio-capable model, with known names as spelling hints. */
    suspend fun transcribe(apiKey: String, model: String, wav: ByteArray, hints: List<String>): AiReply = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) throw AiException(L.s.errNoKey)
        val sys = "Transcribe the user's voice note verbatim in the language spoken. Add punctuation, drop filler sounds (э-э, ну-у), keep the meaning exactly. " +
            "Write numbers and times as digits (18:00). Reply with the transcript only, no quotes, no comments." +
            if (hints.isNotEmpty()) " The speaker often mentions these names and project names: ${hints.joinToString()}. Whenever you hear something that sounds like one of them (even transliterated, e.g. «вузбюро» for vuzburo), write it exactly in this spelling." else ""
        val body = buildJsonObject {
            put("model", model)
            put("messages", buildJsonArray {
                add(buildJsonObject { put("role", "system"); put("content", sys) })
                add(buildJsonObject {
                    put("role", "user")
                    put("content", buildJsonArray {
                        add(buildJsonObject {
                            put("type", "input_audio")
                            put("input_audio", buildJsonObject {
                                put("data", android.util.Base64.encodeToString(wav, android.util.Base64.NO_WRAP))
                                put("format", "wav")
                            })
                        })
                    })
                })
            })
            put("temperature", 0)
            put("usage", buildJsonObject { put("include", true) })
        }.toString()
        val (code, text) = post("$BASE/chat/completions", apiKey, body)
        if (code !in 200..299) throw AiException(errorMessage(code, text))
        val root = AppJson.parseToJsonElement(text).jsonObject
        val content = root["choices"]?.jsonArray?.firstOrNull()?.jsonObject
            ?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.contentOrNull?.trim()
            ?: throw AiException(L.s.errEmpty)
        AiReply(content.trim('"', '«', '»', ' '), (root["usage"] as? JsonObject)?.get("cost")?.jsonPrimitive?.doubleOrNull ?: 0.0)
    }

    /** Checks the key; returns a short human description of the account limit/usage. */
    suspend fun checkKey(apiKey: String): String = withContext(Dispatchers.IO) {
        val (code, text) = get("$BASE/key", apiKey)
        if (code !in 200..299) throw AiException(errorMessage(code, text))
        val d = AppJson.parseToJsonElement(text).jsonObject["data"]?.jsonObject
        val usage = d?.get("usage")?.jsonPrimitive?.doubleOrNull
        val limit = d?.get("limit")?.jsonPrimitive?.doubleOrNull
        L.s.keyStatus(usage, limit)
    }

    suspend fun models(): List<ModelInfo> = withContext(Dispatchers.IO) {
        val (code, text) = get("$BASE/models", null)
        if (code !in 200..299) throw AiException(L.s.errNet)
        val arr = AppJson.parseToJsonElement(text).jsonObject["data"] as? JsonArray ?: return@withContext emptyList()
        arr.mapNotNull { e ->
            val o = e.jsonObject
            val id = o["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            if (id.endsWith(":batch")) return@mapNotNull null
            val outputs = o["architecture"]?.jsonObject?.get("output_modalities")?.jsonArray
                ?.mapNotNull { it.jsonPrimitive.contentOrNull }
            if (outputs != null && "text" !in outputs) return@mapNotNull null
            val inputs = o["architecture"]?.jsonObject?.get("input_modalities")?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
            val p = o["pricing"]?.jsonObject
            ModelInfo(
                id = id,
                name = o["name"]?.jsonPrimitive?.contentOrNull ?: id,
                promptPerM = (p?.get("prompt")?.jsonPrimitive?.contentOrNull?.toDoubleOrNull() ?: 0.0) * 1e6,
                completionPerM = (p?.get("completion")?.jsonPrimitive?.contentOrNull?.toDoubleOrNull() ?: 0.0) * 1e6,
                audio = "audio" in inputs,
            )
        }
    }

    private fun errorMessage(code: Int, text: String): String {
        val msg = runCatching {
            AppJson.parseToJsonElement(text).jsonObject["error"]?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull
        }.getOrNull()
        return when (code) {
            401 -> L.s.errBadKey
            402 -> L.s.errNoMoney
            429 -> L.s.errRate
            else -> msg ?: L.s.errGeneric(code)
        }
    }

    private fun open(url: String, apiKey: String?): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = Cloud.CONNECT_MS
            readTimeout = 90_000
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("HTTP-Referer", "https://margin.app")
            setRequestProperty("X-Title", "Margin")
            if (apiKey != null) setRequestProperty("Authorization", "Bearer $apiKey")
        }

    private fun post(url: String, apiKey: String, body: String): Pair<Int, String> = try {
        val c = open(url, apiKey)
        c.requestMethod = "POST"; c.doOutput = true
        c.outputStream.use { it.write(body.toByteArray()) }
        Cloud.watch(Long.MAX_VALUE) { read(c) }
    } catch (e: IOException) {
        throw AiException(L.s.errNet)
    }

    private fun get(url: String, apiKey: String?): Pair<Int, String> = try {
        Cloud.watch { read(open(url, apiKey)) }
    } catch (e: IOException) {
        throw AiException(L.s.errNet)
    }

    private fun read(c: HttpURLConnection): Pair<Int, String> {
        val code = c.responseCode
        val stream = if (code in 200..299) c.inputStream else c.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
        c.disconnect()
        return code to text
    }
}
