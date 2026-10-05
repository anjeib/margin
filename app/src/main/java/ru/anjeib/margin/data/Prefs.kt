package ru.anjeib.margin.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class ThemeMode { Light, Dark, Auto }
enum class LangMode { Auto, Ru, En }

data class Settings(
    val apiKey: String = "",
    val mainModel: String = DEFAULT_MAIN,
    val fastModel: String = DEFAULT_FAST,
    val autoSubtasks: Boolean = true,
    val autoConfirm: Boolean = true,
    val askWhenUnclear: Boolean = true,
    val morningBrief: Boolean = true,
    val morningTime: String = "08:30",
    val eveningReview: Boolean = false,
    val eveningTime: String = "21:00",
    val theme: ThemeMode = ThemeMode.Auto,
    val lang: LangMode = LangMode.Auto,
    val userName: String = "",
    /** Dictation through an AI model (better names/punctuation) instead of the system recognizer. */
    val voiceAi: Boolean = true,
    val voiceModel: String = DEFAULT_VOICE,
    /** Send dictated text right away instead of leaving it in the composer. */
    val voiceSendNow: Boolean = true,
    /** Android Auto Backup to the Google account (see MarginBackupAgent). */
    val googleBackup: Boolean = true,
    /** Use the on-device Whisper model even when online. */
    val whisperAlways: Boolean = false,
    /** Read the phone calendar so plans see meetings. */
    val calendar: Boolean = false,
    val onboarded: Boolean = false,
    /** A document (e.g. on Google Drive) the app keeps overwriting with a full backup. */
    /** Brief collapsed / hidden for this date (yyyy-MM-dd). */
    val briefCollapsed: String = "",
    val briefHidden: String = "",
    val spentMonth: String = "",
    /** AI spend this month, roubles (OpenRouter dollars converted at Llm.USD_RUB). */
    val spentRub: Double = 0.0,
    /** "yandex" (Yandex AI Studio, default when a key is built in) or "openrouter". */
    val provider: String = "yandex",
    /** Yandex parsing: "hybrid" (Alice Flash, DeepSeek for complex phrases), "economy" (Alice only), "precise" (DeepSeek only). */
    val yaMode: String = "hybrid",
    val yaKey: String = "",
    val yaFolder: String = "",
    /** Hidden developer section (providers, keys, models, spend) — long-press the version to toggle. */
    val devMode: Boolean = false,
) {
    companion object {
        const val DEFAULT_MAIN = "anthropic/claude-sonnet-5.5"
        const val DEFAULT_FAST = "google/gemini-3.5-flash-lite"
        const val DEFAULT_VOICE = "openai/gpt-audio-mini"
    }
}

class Prefs private constructor(context: Context) {
    private val sp = context.getSharedPreferences("margin", Context.MODE_PRIVATE)
    private val _s = MutableStateFlow(read())
    val settings: StateFlow<Settings> = _s

    private fun read() = Settings(
        apiKey = sp.getString("apiKey", null) ?: ru.anjeib.margin.BuildConfig.OPENROUTER_KEY,
        mainModel = sp.getString("mainModel", null) ?: Settings.DEFAULT_MAIN,
        fastModel = sp.getString("fastModel", null) ?: Settings.DEFAULT_FAST,
        autoSubtasks = sp.getBoolean("autoSubtasks", true),
        autoConfirm = sp.getBoolean("autoConfirm", true),
        askWhenUnclear = sp.getBoolean("askWhenUnclear", true),
        morningBrief = sp.getBoolean("morningBrief", true),
        morningTime = sp.getString("morningTime", null) ?: "08:30",
        eveningReview = sp.getBoolean("eveningReview", false),
        eveningTime = sp.getString("eveningTime", null) ?: "21:00",
        theme = runCatching { ThemeMode.valueOf(sp.getString("theme", "Auto")!!) }.getOrDefault(ThemeMode.Auto),
        lang = runCatching { LangMode.valueOf(sp.getString("lang", "Auto")!!) }.getOrDefault(LangMode.Auto),
        userName = sp.getString("userName", "") ?: "",
        voiceAi = sp.getBoolean("voiceAi", true),
        voiceModel = sp.getString("voiceModel", null) ?: Settings.DEFAULT_VOICE,
        voiceSendNow = sp.getBoolean("voiceAutoSend", true),
        googleBackup = sp.getBoolean("googleBackup", true),
        whisperAlways = sp.getBoolean("whisperAlways", false),
        calendar = sp.getBoolean("calendar", false),
        onboarded = sp.getBoolean("onboarded", false),
        briefCollapsed = sp.getString("briefCollapsed", "") ?: "",
        briefHidden = sp.getString("briefHidden", "") ?: "",
        spentMonth = sp.getString("spentMonth", "") ?: "",
        spentRub = sp.getFloat("spentRub", 0f).toDouble(),
        provider = sp.getString("provider", null) ?: if (ru.anjeib.margin.BuildConfig.YC_KEY.isNotBlank()) "yandex" else "openrouter",
        yaMode = sp.getString("yaMode", null) ?: "hybrid",
        yaKey = sp.getString("yaKey", null) ?: ru.anjeib.margin.BuildConfig.YC_KEY,
        yaFolder = sp.getString("yaFolder", null) ?: ru.anjeib.margin.BuildConfig.YC_FOLDER,
        devMode = sp.getBoolean("devMode", false),
    )

    fun update(f: (Settings) -> Settings) {
        val s = f(_s.value)
        sp.edit {
            putString("apiKey", s.apiKey.trim())
            putString("mainModel", s.mainModel)
            putString("fastModel", s.fastModel)
            putBoolean("autoSubtasks", s.autoSubtasks)
            putBoolean("autoConfirm", s.autoConfirm)
            putBoolean("askWhenUnclear", s.askWhenUnclear)
            putBoolean("morningBrief", s.morningBrief)
            putString("morningTime", s.morningTime)
            putBoolean("eveningReview", s.eveningReview)
            putString("eveningTime", s.eveningTime)
            putString("theme", s.theme.name)
            putString("lang", s.lang.name)
            putString("userName", s.userName)
            putBoolean("voiceAi", s.voiceAi)
            putString("voiceModel", s.voiceModel)
            putBoolean("voiceAutoSend", s.voiceSendNow)
            putBoolean("googleBackup", s.googleBackup)
            putBoolean("whisperAlways", s.whisperAlways)
            putBoolean("calendar", s.calendar)
            putBoolean("onboarded", s.onboarded)
            putString("briefCollapsed", s.briefCollapsed)
            putString("briefHidden", s.briefHidden)
            putString("spentMonth", s.spentMonth)
            putFloat("spentRub", s.spentRub.toFloat())
            putString("provider", s.provider)
            putString("yaMode", s.yaMode)
            putString("yaKey", s.yaKey.trim())
            putString("yaFolder", s.yaFolder.trim())
            putBoolean("devMode", s.devMode)
        }
        _s.value = s.copy(apiKey = s.apiKey.trim())
    }

    /** Adds the cost of one AI call (roubles) to this month's total. */
    fun addSpend(rub: Double) {
        if (rub <= 0) return
        val month = java.time.YearMonth.now().toString()
        update { if (it.spentMonth == month) it.copy(spentRub = it.spentRub + rub) else it.copy(spentMonth = month, spentRub = rub) }
    }

    companion object {
        @Volatile private var instance: Prefs? = null
        fun get(context: Context): Prefs =
            instance ?: synchronized(this) { instance ?: Prefs(context.applicationContext).also { instance = it } }
    }
}
