package ru.anjeib.margin.voice

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.anjeib.margin.data.AppData
import ru.anjeib.margin.data.Attachment
import ru.anjeib.margin.data.Files
import ru.anjeib.margin.data.Store
import ru.anjeib.margin.data.sentence
import ru.anjeib.margin.data.setTranscript
import ru.anjeib.margin.data.unheardAudio

/**
 * Voice notes that came in without words — a Telegram or WhatsApp voice shared or forwarded into «Входящие», a file
 * attached to a note, or anything left while the model was still downloading — are read on the phone one by one, in the
 * background, for the whole process (the share sheet may close long before a long voice is read). The words go into the
 * data wherever the note lives; what was read once is never read again (an empty "" says «nothing to hear»).
 */
object Transcripts {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val queue = Channel<Pair<Context, Attachment>>(Channel.UNLIMITED)
    private val taken = mutableSetOf<String>()

    private val _waiting = MutableStateFlow<Set<String>>(emptySet())
    /** Voice notes waiting or being read now: the voice block says «Распознаю…». */
    val waiting: StateFlow<Set<String>> = _waiting
    private val _progress = MutableStateFlow<Map<String, Float>>(emptyMap())
    /** How far a reading is, once it has taken more than a couple of seconds. */
    val progress: StateFlow<Map<String, Float>> = _progress
    private val _unreadable = MutableStateFlow<Set<String>>(emptySet())
    /** Files the phone couldn't decode in this run (afterwards their transcript is simply empty). */
    val unreadable: StateFlow<Set<String>> = _unreadable

    init {
        scope.launch { for ((c, a) in queue) read(c, a) }
    }

    /** Can the phone read voice notes now: Russian and the model here. */
    fun canRead(c: Context) = ru.anjeib.margin.i18n.L.lang == ru.anjeib.margin.i18n.Lang.Ru && LocalSpeech.ready(c)

    /** Queues every voice note in [d] that has no words yet. */
    fun sweep(c: Context, d: AppData) {
        if (!canRead(c)) return
        d.unheardAudio().forEach { hear(c, it) }
    }

    /** Queues one voice note (once; a second call while it waits does nothing). */
    fun hear(c: Context, a: Attachment) {
        if (synchronized(taken) { !taken.add(a.id) }) return
        _waiting.update { it + a.id }
        queue.trySend(c.applicationContext to a)
    }

    private suspend fun read(c: Context, a: Attachment) {
        val store = Store.get(c)
        val t0 = System.currentTimeMillis()
        try {
            // Deleted or already read meanwhile (by the old path, by hand) — nothing to do.
            val now = store.data.value.let { d -> d.unheardAudio().find { it.id == a.id } } ?: return
            val f = Files.file(c, now)?.takeIf { it.exists() } ?: return
            var shown = -1
            val text = LocalSpeech.transcribe(c, f, "ru") { p ->
                // The decoder reports every few milliseconds; the screen hears only whole percents.
                val pct = (p * 100).toInt()
                if (pct != shown && System.currentTimeMillis() - t0 > 2_000) { shown = pct; _progress.update { it + (a.id to pct / 100f) } }
            }
            store.update { it.setTranscript(a.id, text.sentence()) }
        } catch (e: Unreadable) {
            android.util.Log.w("MarginSpeech", "${a.name}: can't decode (${e.message})")
            _unreadable.update { it + a.id }
            store.update { it.setTranscript(a.id, "") }
        } catch (e: Exception) {
            // Not the file's fault (the model went away, memory): tried again on the next start.
            android.util.Log.w("MarginSpeech", "${a.name}: not read", e)
        } finally {
            _progress.update { it - a.id }
            // Stays «taken» for the rest of the run: a failure isn't retried in a loop, only on the next start.
            _waiting.update { it - a.id }
        }
    }
}
