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
import ru.anjeib.margin.book.closeDay
import ru.anjeib.margin.book.Ask
import ru.anjeib.margin.book.AiRead
import ru.anjeib.margin.book.Line
import ru.anjeib.margin.book.Person
import ru.anjeib.margin.data.Attachment
import ru.anjeib.margin.data.ContactInfo
import ru.anjeib.margin.data.Prefs
import ru.anjeib.margin.ai.Llm
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

    /** The lines the last words became — the sheet over another app shows them. */
    private val _written = MutableStateFlow<List<String>>(emptyList())
    val written: StateFlow<List<String>> = _written

    /** Lines just written: they appear with the pen on the page. */
    private val _fresh = MutableStateFlow<Set<String>>(emptySet())
    val fresh: StateFlow<Set<String>> = _fresh

    var words: Words = WordsRu

    /** Words handed in from outside («Поделиться», a shortcut): they wait in the field for the person to send them. */
    private val _prefill = MutableStateFlow<String?>(null)
    val prefill: StateFlow<String?> = _prefill
    fun handIn(text: String?) { if (!text.isNullOrBlank()) _prefill.value = text.trim() }

    /** Opened from a notification or a shortcut: «line:<id>», «ritual», «today». */
    private val _launch = MutableStateFlow<String?>(null)
    val launch: StateFlow<String?> = _launch
    fun launchWith(what: String?) { _launch.value = what }
    fun launchTaken() { _launch.value = null }
    fun prefillTaken() { _prefill.value = null }

    /** Files handed in from outside («Поделиться» a photo): they wait over the field with the words. */
    private val _handedFiles = MutableStateFlow<List<Attachment>>(emptyList())
    val handedFiles: StateFlow<List<Attachment>> = _handedFiles
    fun handInFiles(f: List<Attachment>) { if (f.isNotEmpty()) _handedFiles.value = _handedFiles.value + f }
    fun handedFilesTaken() { _handedFiles.value = emptyList() }

    /** «Записать» from the tile or a shortcut: the field takes the keyboard. */
    private val _focus = MutableStateFlow<Long?>(null)
    val focus: StateFlow<Long?> = _focus
    fun focusNow() { _focus.value = System.nanoTime() }
    fun focusTaken() { _focus.value = null }

    /** «Надиктовать» from a shortcut: the field starts listening. */
    private val _dictate = MutableStateFlow<Long?>(null)
    val dictate: StateFlow<Long?> = _dictate
    fun dictateNow() { _dictate.value = System.nanoTime() }
    fun dictateTaken() { _dictate.value = null }

    /** A short word from Margin with nothing to undo («нужна сеть»). */
    fun tell(text: String) = say(text)

    init {
        // A new notebook opens with «Примеры» (Russian only) — once.
        val prefs = app.getSharedPreferences("polya", android.content.Context.MODE_PRIVATE)
        if (!prefs.getBoolean("examplesAdded", false)) {
            prefs.edit().putBoolean("examplesAdded", true).apply()
            if (store.book.value.lines.isEmpty() && java.util.Locale.getDefault().language == "ru") store.update { it.withSamples(Examples.build()) }
        }
    }

    fun refreshToday() { _today.value = LocalDate.now(); refreshCalendar() }

    /**
     * Meetings from the phone's calendar, read only, as ○ lines on their pages (never stored in the notebook): from a week
     * back to a month ahead. Shown when the person allowed it in the settings.
     */
    private val _calendar = MutableStateFlow<Map<LocalDate, List<Line>>>(emptyMap())
    val calendar: StateFlow<Map<LocalDate, List<Line>>> = _calendar

    fun refreshCalendar() {
        val ctx = getApplication<Application>()
        if (!prefs.settings.value.calendar || !ru.anjeib.margin.data.PhoneCalendar.allowed(ctx)) { _calendar.value = emptyMap(); return }
        viewModelScope.launch {
            val from = LocalDate.now().minusDays(7)
            val events = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { ru.anjeib.margin.data.PhoneCalendar.events(ctx, from, 38) }
            _calendar.value = events.map { e ->
                val d = e.start.toLocalDate()
                d to Line(id = "cal:" + (e.title + e.start).hashCode().toUInt().toString(36), text = e.title, kind = ru.anjeib.margin.book.Kind.Event,
                    day = d.toString(), time = if (e.allDay) null else "%02d:%02d".format(e.start.hour, e.start.minute), place = e.location, source = "calendar",
                    writtenAt = e.start.withSecond(0).withNano(0).toString())
            }.groupBy({ it.first }, { it.second })
        }
    }

    fun setCalendar(on: Boolean) {
        prefs.update { it.copy(calendar = on) }
        refreshCalendar()
    }

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

    /** The words become lines on the target (or on their own day); Margin says where they went if not here. A question gets an answer. */
    fun write(text: String, t: Target, files: List<Attachment> = emptyList()): Boolean {
        val page = t.page
        val d0 = if (text.isBlank() && files.isNotEmpty()) null else draft(text, t)
        if (d0?.question == true) { ask(text); return true }
        // Files alone are a note on the page, named by what they are.
        val d = d0 ?: Read.Draft(listOf(Line(text = fileWord(files), kind = ru.anjeib.margin.book.Kind.Note,
            day = if (t.later || t.threadId != null) null else page.toString(), threadId = t.threadId)), true, false, null)
        if (d.lines.isEmpty()) return false
        val lines = if (files.isEmpty()) d.lines else d.lines.mapIndexed { i, l -> if (i == 0) l.copy(files = l.files + files) else l }
        val before = book.value
        store.update { b ->
            var x = b.add(lines)
            d.threadTitle?.let { tt -> lines.forEach { l -> x = x.setThread(l.id, tt) } }
            x
        }
        _fresh.value = lines.map { it.id }.toSet()
        _written.value = lines.map { it.id }
        viewModelScope.launch { delay(1200); _fresh.value = emptySet() }
        // Margin says where the words went only when they left the page in front of you.
        val here = t.threadId != null || t.person != null
        val away = if (here) emptyList() else lines.filter { if (t.later) it.date != null else it.date != page }
        when {
            lines.size > 1 -> say(words.writtenN(lines.size), before)
            away.isNotEmpty() -> say(words.writtenOn(away.first().date, today.value), before)
        }
        // Not sure of the words and the AI is at hand: it re-reads them, and what it reads differently shows as a correction.
        if (!d.sure && text.isNotBlank() && aiReady()) reread(text, t, store.book.value.lines.filter { l -> lines.any { it.id == l.id } })
        return true
    }

    private fun fileWord(files: List<Attachment>) = when {
        files.all { it.kind == "photo" || it.kind == "video" } -> words.photo
        files.all { it.kind == "audio" } -> words.voice
        files.size == 1 -> files[0].name.substringBeforeLast('.').ifBlank { words.file }
        else -> words.file
    }.replaceFirstChar { it.uppercase() }

    // ---------------- the AI ----------------

    private val prefs = Prefs.get(app)
    private val polya = app.getSharedPreferences("polya", android.content.Context.MODE_PRIVATE)
    private fun online() = ru.anjeib.margin.ai.Processor.online(getApplication())
    /** The AI can be asked: a key or an invite code, and a network. */
    fun aiReady() = Llm.ready(prefs.settings.value) && online()

    private fun reread(text: String, t: Target, phone: List<Line>) {
        if (phone.isEmpty()) return
        val bookNow = book.value
        // The AI sees the notebook as it was before these words — else it «closes» the lines it is asked to read.
        val ids = phone.map { it.id }.toSet()
        val before0 = bookNow.copy(lines = bookNow.lines.filterNot { it.id in ids })
        say(words.rereading)
        viewModelScope.launch {
            val prompt = AiRead.readPrompt(before0, text, t.page, today.value, words.ru)
            val reading = complete(prompt, text, strong = false) { AiRead.parseRead(it, before0, t.page, text.trim(), t.threadId, noDay = t.later) }
            if (reading == null) { if (_said.value?.text == words.rereading) hush(); return@launch }
            val before = book.value
            store.update { b -> AiRead.merge(b, phone, reading, today.value) }
            val after = book.value
            if (after != before) {
                val done = (reading.done + reading.dropped).count { id -> before.line(id)?.open == true && after.line(id)?.open == false }
                _fresh.value = after.lines.filter { l -> before.line(l.id) == null }.map { it.id }.toSet()
                if (_fresh.value.isNotEmpty()) _written.value = _fresh.value.toList()
                viewModelScope.launch { delay(1200); _fresh.value = emptySet() }
                say(if (done > 0) words.markedDone(done) else words.reread, before)
            } else if (_said.value?.text == words.rereading) hush()
        }
    }

    /**
     * One request to the AI, read by [read]. An empty or broken reply is asked again once, then the quick model has a go —
     * the strong one sometimes spends its whole answer thinking. Null when nothing came of it.
     */
    private suspend fun <T> complete(system: String, user: String, strong: Boolean, read: (String) -> T?): T? {
        val s = prefs.settings.value
        // [strong]: the thinking model first (answers), otherwise the quick one first (re-reading must land in a second) —
        // and the other one when the first gives nothing usable.
        val order = if (strong) listOf(true, true, false) else listOf(false, false, true)
        for (smart in order) {
            val r = runCatching { Llm.complete(s, system, listOf("user" to user), smart = smart, complex = smart, json = true, maxTokens = if (smart) 4000 else 1500) }
                .onFailure { android.util.Log.w("polya", "ai: ${it.message}") }.getOrNull() ?: continue
            prefs.addSpend(r.rub)
            read(r.content)?.let { return it }
        }
        return null
    }

    /** A question and its answer over the field; null — nothing asked. [thinking] while the AI reads. */
    data class Asked(val q: String, val answer: Ask.Answer?, val thinking: Boolean = false)
    private val _asked = MutableStateFlow<Asked?>(null)
    val asked: StateFlow<Asked?> = _asked
    fun closeAnswer() { _asked.value = null }

    fun ask(q: String) {
        val a = Ask.answer(q, book.value, today.value)
        if ((a.about != Ask.About.Unknown && !a.weak) || !aiReady()) { _asked.value = Asked(q.trim(), a); return }
        _asked.value = Asked(q.trim(), null, thinking = true)
        val b = book.value
        viewModelScope.launch {
            val said = complete(AiRead.askPrompt(b, q, today.value, words.ru), q, strong = true) { AiRead.parseAsk(it, b) }
            if (_asked.value?.q != q.trim()) return@launch
            // No answer from the AI: what the phone found by the words is still better than nothing.
            _asked.value = Asked(q.trim(), if (said == null && a.about != Ask.About.Unknown) a
                else Ask.Answer(Ask.About.Unknown, lines = said?.ids?.mapNotNull { book.value.line(it) } ?: emptyList(), said = said?.say ?: words.needNet))
        }
    }

    // ---------------- voice and files ----------------

    /** Words from a recording: SpeechKit through the AI door when online, Whisper on the phone otherwise. */
    fun canHear(): Boolean = aiReady() || ru.anjeib.margin.voice.LocalWhisper.ready(getApplication())

    fun transcribe(audio: Attachment, onText: (String?) -> Unit) {
        val ctx = getApplication<Application>()
        val s = prefs.settings.value
        viewModelScope.launch {
            val text = runCatching {
                val f = ru.anjeib.margin.data.Files.file(ctx, audio) ?: return@runCatching null
                val hints = book.value.persons.map { it.name }.take(40)
                if (aiReady() && !s.whisperAlways) {
                    val bytes = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { f.readBytes() }
                    Llm.transcribe(s, bytes, hints).also { prefs.addSpend(it.rub) }.content
                } else ru.anjeib.margin.voice.LocalWhisper.transcribe(ctx, f, if (words.ru) "ru" else "en", hints)
            }.getOrNull()
            ru.anjeib.margin.data.Files.delete(ctx, audio)
            onText(text?.trim()?.ifBlank { null })
        }
    }

    /** A contact from the phone becomes a person of the notebook (kept, with the phone and the mail). */
    fun addContact(c: ContactInfo): String {
        val name = c.name.trim()
        store.update { b ->
            val p = b.person(name)
            if (p != null) b.copy(persons = b.persons.map { if (it.id == p.id) it.copy(kept = true, phone = it.phone ?: c.phone, email = it.email ?: c.email) else it })
            else b.copy(persons = b.persons + Person(name = name, phone = c.phone, email = c.email, kept = true))
        }
        say(words.personAdded(name))
        return name
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

    /** The day just closed with the ritual — its page shows the stamp being pressed. */
    private val _stamping = MutableStateFlow<LocalDate?>(null)
    val stamping: StateFlow<LocalDate?> = _stamping

    fun closeDay(date: LocalDate) {
        store.update { it.closeDay(date) }
        _stamping.value = date
        viewModelScope.launch { delay(2500); if (_stamping.value == date) _stamping.value = null }
    }

    /** «Остальное → завтра» in the evening ritual. */
    fun carryAll(date: LocalDate, to: LocalDate) {
        val before = book.value
        store.update { it.carryAll(date, to, today.value) }
        say(words.movedOn(to, today.value), before)
    }

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

    /** The title page is seen once; «с чистой страницы» takes the examples away. */
    private val _welcomed = MutableStateFlow(polya.getBoolean("welcomed", false))
    val welcomed: StateFlow<Boolean> = _welcomed
    fun welcome(keepExamples: Boolean) {
        if (!keepExamples && samplesShown()) store.update { it.withoutSamples() }
        polya.edit().putBoolean("welcomed", true).apply()
        _welcomed.value = true
    }
}
