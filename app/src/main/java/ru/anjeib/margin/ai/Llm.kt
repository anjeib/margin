package ru.anjeib.margin.ai

import ru.anjeib.margin.data.Settings
import ru.anjeib.margin.i18n.L

data class LlmReply(val content: String, val rub: Double)

/**
 * One door to the AI. Yandex AI Studio by default (Russian cloud): Alice AI LLM Flash for simple phrases,
 * DeepSeek V4.1 Flash for complex ones and for smart requests; OpenRouter as the alternative provider.
 */
object Llm {
    /** Only for showing OpenRouter spend in roubles. */
    const val USD_RUB = 100.0
    const val YA_FAST = "aliceai-llm-flash"
    const val YA_STRONG = "deepseek-v4.1-flash"

    fun yandex(s: Settings) = s.provider == "yandex"
    fun door(s: Settings) = Yandex.Door(s.yaKey, s.yaFolder, s.invite, s.device)
    /** With a key (developer builds) or, in what people install, with an invite code for Margin's own door. */
    fun ready(s: Settings) = if (yandex(s)) (s.yaKey.isNotBlank() && s.yaFolder.isNotBlank()) || door(s).proxy else s.apiKey.isNotBlank()

    /** Which Yandex model answers: smart requests and complex phrases → DeepSeek, the rest per mode. */
    fun yaModel(s: Settings, smart: Boolean, complex: Boolean) = when {
        smart -> YA_STRONG
        s.yaMode == "economy" -> YA_FAST
        s.yaMode == "precise" -> YA_STRONG
        complex -> YA_STRONG
        else -> YA_FAST
    }

    suspend fun complete(
        s: Settings,
        system: String,
        messages: List<Pair<String, String>>,
        smart: Boolean = false,
        complex: Boolean = false,
        json: Boolean = true,
        images: List<String> = emptyList(),
    ): LlmReply {
        if (!ready(s)) throw AiException(L.s.errNoKey)
        return if (yandex(s)) {
            // Yandex text models don't read images; the words still go through.
            val r = Yandex.chat(door(s), yaModel(s, smart, complex), system, messages, json, quick = !smart)
            LlmReply(r.content, r.rub)
        } else {
            val r = OpenRouter.chat(s.apiKey, if (smart) s.mainModel else s.fastModel, system, messages, json = json, quick = !smart, images = images)
            LlmReply(r.content, r.costUsd * USD_RUB)
        }
    }

    /** Speech → text: SpeechKit on Yandex, an audio model on OpenRouter. */
    suspend fun transcribe(s: Settings, wav: ByteArray, hints: List<String>): LlmReply =
        if (yandex(s)) Yandex.stt(door(s), wav, if (ru.anjeib.margin.i18n.L.lang == ru.anjeib.margin.i18n.Lang.Ru) "ru-RU" else "en-US")
        else OpenRouter.transcribe(s.apiKey, s.voiceModel, wav, hints).let { LlmReply(it.content, it.costUsd * USD_RUB) }

    /** Text on a photo: Yandex Vision, or a vision model on OpenRouter. Empty when there is no text. */
    suspend fun ocr(s: Settings, jpeg: ByteArray, dataUrl: String): LlmReply =
        if (yandex(s)) Yandex.ocr(door(s), jpeg)
        else OpenRouter.chat(s.apiKey, s.fastModel, "Copy all readable text from the image verbatim, line by line. No comments. If there is no text, reply with nothing.",
            listOf("user" to "text?"), json = false, quick = true, images = listOf(dataUrl)).let { LlmReply(it.content.trim(), it.costUsd * USD_RUB) }

    suspend fun checkKey(s: Settings): String =
        if (yandex(s)) Yandex.check(door(s)) else OpenRouter.checkKey(s.apiKey)
}
