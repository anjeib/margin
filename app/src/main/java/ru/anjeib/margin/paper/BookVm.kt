package ru.anjeib.margin.paper

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import ru.anjeib.margin.book.Book
import ru.anjeib.margin.book.BookStore
import ru.anjeib.margin.book.Examples
import ru.anjeib.margin.book.Read
import ru.anjeib.margin.book.add
import ru.anjeib.margin.book.addChild
import ru.anjeib.margin.book.delete
import ru.anjeib.margin.book.drop
import ru.anjeib.margin.book.hasSamples
import ru.anjeib.margin.book.move
import ru.anjeib.margin.book.setThread
import ru.anjeib.margin.book.toggle
import ru.anjeib.margin.book.withSamples
import ru.anjeib.margin.book.withoutSamples
import ru.anjeib.margin.book.carryAll
import java.time.LocalDate

/**
 * The notebook on screen: what is written, which day is today, and Margin's one short sentence over the field
 * («записано на пт · вернуть»). Every change goes through [BookStore.update]; the sentence carries its own undo.
 */
class BookVm(app: Application) : AndroidViewModel(app) {
    private val store = BookStore.get(app)
    val book: StateFlow<Book> = store.book

    private val _today = MutableStateFlow(LocalDate.now())
    val today: StateFlow<LocalDate> = _today

    /** Margin's sentence over the field; [undo] puts the notebook back as it was before the change. */
    data class Said(val text: String, val undo: Book? = null, val id: Long = System.nanoTime())
    private val _said = MutableStateFlow<Said?>(null)
    val said: StateFlow<Said?> = _said
    private var sayJob: Job? = null

    /** Lines just written: they appear with the pen on the page. */
    private val _fresh = MutableStateFlow<Set<String>>(emptySet())
    val fresh: StateFlow<Set<String>> = _fresh

    var words: Words = WordsRu

    /** Words handed in from outside («Поделиться», a shortcut): they wait in the field for the person to send them. */
    private val _prefill = MutableStateFlow<String?>(null)
    val prefill: StateFlow<String?> = _prefill
    fun handIn(text: String?) { if (!text.isNullOrBlank()) _prefill.value = text.trim() }
    fun prefillTaken() { _prefill.value = null }

    init {
        // A new notebook opens with «Примеры» (Russian only) — once.
        val prefs = app.getSharedPreferences("polya", android.content.Context.MODE_PRIVATE)
        if (!prefs.getBoolean("examplesAdded", false)) {
            prefs.edit().putBoolean("examplesAdded", true).apply()
            if (store.book.value.lines.isEmpty() && java.util.Locale.getDefault().language == "ru") store.update { it.withSamples(Examples.build()) }
        }
    }

    fun refreshToday() { _today.value = LocalDate.now() }

    private fun say(text: String, undo: Book? = null) {
        val s = Said(text, undo)
        _said.value = s
        sayJob?.cancel()
        sayJob = viewModelScope.launch { delay(6000); if (_said.value?.id == s.id) _said.value = null }
    }

    fun hush() { _said.value = null }

    fun undo() {
        val s = _said.value ?: return
        s.undo?.let { b -> store.update { b } }
        _said.value = null
    }

    /** Where the field writes: a day's page, a thread, a person's page or «Потом». */
    data class Target(val page: LocalDate, val threadId: String? = null, val person: String? = null, val later: Boolean = false)

    /** What the field holds, read on the phone — for the live margin next to it. */
    fun preview(text: String, t: Target): Read.Draft = draft(text, t)

    private fun draft(text: String, t: Target): Read.Draft {
        val d = Read.draft(text, book.value, t.page, today.value, threadId = t.threadId, noDay = t.later)
        if (t.person == null) return d
        // Written on a person's page: the person is on every line, unless the words already name them another way.
        return d.copy(lines = d.lines.map { l -> if (l.who.any { it.name.equals(t.person, true) }) l else l.copy(who = l.who + ru.anjeib.margin.book.Who(t.person)) })
    }

    /** The words become lines on the target (or on their own day); Margin says where they went if not here. */
    fun write(text: String, t: Target): Boolean {
        val page = t.page
        val d = draft(text, t)
        if (d.question) { say(words.askLater); return false }
        if (d.lines.isEmpty()) return false
        val before = book.value
        store.update { b ->
            var x = b.add(d.lines)
            d.threadTitle?.let { tt -> d.lines.forEach { l -> x = x.setThread(l.id, tt) } }
            x
        }
        _fresh.value = d.lines.map { it.id }.toSet()
        viewModelScope.launch { delay(1200); _fresh.value = emptySet() }
        // Margin says where the words went only when they left the page in front of you.
        val here = t.threadId != null || t.person != null
        val away = if (here) emptyList() else d.lines.filter { if (t.later) it.date != null else it.date != page }
        when {
            d.lines.size > 1 -> say(words.writtenN(d.lines.size), before)
            away.isNotEmpty() -> say(words.writtenOn(away.first().date, today.value), before)
        }
        return true
    }

    fun toggle(id: String) = store.update { it.toggle(id, today.value) }

    /** A change by hand from a sheet: no sentence over the field — the change is seen where it was made. */
    fun edit(f: (Book) -> Book) = store.update(f)

    /** Words written under a line: a step when they say what to do, otherwise a comment. */
    fun writeUnder(id: String, text: String) {
        if (text.isBlank()) return
        store.update { it.addChild(id, text, if (Read.isStep(text)) ru.anjeib.margin.book.Kind.Todo else ru.anjeib.margin.book.Kind.Note) }
    }

    fun move(id: String, to: LocalDate?, quiet: Boolean = false) {
        val before = book.value
        store.update { it.move(id, to, today.value) }
        if (!quiet) say(words.movedOn(to, today.value), before)
    }

    fun drop(id: String, quiet: Boolean = false) {
        val before = book.value
        store.update { it.drop(id) }
        if (!quiet) say(words.dropped, before)
    }

    /** «Остальное → завтра» in the evening ritual. */
    fun carryAll(date: LocalDate, to: LocalDate) {
        val before = book.value
        store.update { it.carryAll(date, to, today.value) }
        say(words.movedOn(to, today.value), before)
    }

    private val polya = app.getSharedPreferences("polya", android.content.Context.MODE_PRIVATE)
    /** The hour of «перелистнуть день»: the link on the page appears from it, the reminder rings at it. */
    fun eveningHour(): Int = polya.getInt("eveningHour", 21)
    fun setEveningHour(h: Int) = polya.edit().putInt("eveningHour", h.coerceIn(17, 23)).apply()

    /** «Примеры» on and off. */
    fun toggleExamples() = store.update { if (it.hasSamples()) it.withoutSamples() else it.withSamples(Examples.build(today.value)) }

    fun delete(id: String) {
        val before = book.value
        store.update { it.delete(id) }
        say(words.deleted, before)
    }

    fun samplesShown() = book.value.hasSamples()
}
