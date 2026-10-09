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

    /** What the field holds, read on the phone — for the live margin next to it. */
    fun preview(text: String, page: LocalDate): Read.Draft = Read.draft(text, book.value, page, today.value)

    /** The words become lines on [page] (or on their own day); Margin says where they went if not here. */
    fun write(text: String, page: LocalDate, threadId: String? = null): Boolean {
        val d = Read.draft(text, book.value, page, today.value, threadId = threadId)
        if (d.question) { say(words.askLater); return false }
        if (d.lines.isEmpty()) return false
        val before = book.value
        store.update { b ->
            var x = b.add(d.lines)
            d.threadTitle?.let { t -> d.lines.forEach { l -> x = x.setThread(l.id, t) } }
            x
        }
        _fresh.value = d.lines.map { it.id }.toSet()
        viewModelScope.launch { delay(1200); _fresh.value = emptySet() }
        val away = d.lines.filter { it.date != page }
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

    fun move(id: String, to: LocalDate?) {
        val before = book.value
        store.update { it.move(id, to, today.value) }
        say(words.movedOn(to, today.value), before)
    }

    fun drop(id: String) {
        val before = book.value
        store.update { it.drop(id) }
        say(words.dropped, before)
    }

    fun delete(id: String) {
        val before = book.value
        store.update { it.delete(id) }
        say(words.deleted, before)
    }

    fun samplesShown() = book.value.hasSamples()
}
