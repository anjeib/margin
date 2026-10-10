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
import ru.anjeib.margin.book.gatherInto
import ru.anjeib.margin.book.notebookDay
import ru.anjeib.margin.book.dropOccurrence
import ru.anjeib.margin.book.beginToday
import ru.anjeib.margin.book.removeFile
import ru.anjeib.margin.book.Change.What
import ru.anjeib.margin.book.setWhen
import ru.anjeib.margin.book.toDecide
import ru.anjeib.margin.book.hasSamples
import ru.anjeib.margin.book.move
import ru.anjeib.margin.book.setThread
import ru.anjeib.margin.book.toggle
import ru.anjeib.margin.book.withSamples
import ru.anjeib.margin.book.withoutSamples
import ru.anjeib.margin.book.carryAll
import ru.anjeib.margin.book.closeDay
import ru.anjeib.margin.book.closeThread
import ru.anjeib.margin.book.reopenThread
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

    private val _today = MutableStateFlow(store.book.value.notebookDay())
    val today: StateFlow<LocalDate> = _today

    /** Margin's sentence over the field; [undo] puts the notebook back as it was before the change. */
    /** Lines that just left the page for another day: the calendar sign at the top pulses, and shows them when opened. */
    data class Flight(val day: LocalDate, val ids: List<String>, val id: Long = System.nanoTime())
    private val _flight = MutableStateFlow<Flight?>(null)
    val flight: StateFlow<Flight?> = _flight
    fun landed(f: Flight) { if (_flight.value?.id == f.id) _flight.value = null }

    data class Said(val text: String, val undo: Book? = null, val id: Long = System.nanoTime(), val day: LocalDate? = null,
                    /** Lines the words seem to close, waiting for «да» — Margin never closes your lines by itself. */
                    val ask: List<Pair<String, ru.anjeib.margin.book.State>> = emptyList(),
                    /** Things to do found in a long note, waiting for «да» to become its steps. */
                    val steps: List<String> = emptyList(), val stepsOf: String? = null,
                    /** A change by words Margin is not sure about (another line fits as well): done on «да». */
                    val change: ru.anjeib.margin.book.Change.Plan? = null) {
        val asks get() = ask.isNotEmpty() || steps.isNotEmpty() || change != null
    }
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
            val ru = ru.anjeib.margin.i18n.resolveLang(Prefs.get(app).settings.value.lang) == ru.anjeib.margin.i18n.Lang.Ru
            if (store.book.value.lines.isEmpty()) store.update { it.withSamples(Examples.build(ru = ru)) }
        }
    }

    fun refreshToday() { _today.value = book.value.notebookDay(); refreshCalendar() }
    /** Today as the notebook counts it now (the night still belongs to the day not yet turned). */
    fun notebookDay(): LocalDate = book.value.notebookDay()

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

    private fun say(text: String, undo: Book? = null, day: LocalDate? = null, ask: List<Pair<String, ru.anjeib.margin.book.State>> = emptyList(),
                    steps: List<String> = emptyList(), stepsOf: String? = null, change: ru.anjeib.margin.book.Change.Plan? = null) {
        val s = Said(text, undo, day = day, ask = ask, steps = steps, stepsOf = stepsOf, change = change)
        _said.value = s
        sayJob?.cancel()
        // Long enough to read and reach «вернуть»; a question waits for its answer a good while longer.
        // Long enough to read it and change one's mind: «вернуть» stays half a minute, a question a minute.
        sayJob = viewModelScope.launch { delay(if (!s.asks) 30_000 else 60_000); if (_said.value?.id == s.id) _said.value = null }
    }

    /** «да» to Margin's question: the lines it named are closed now (with a way back). */
    fun confirm() {
        val s = _said.value ?: return
        s.change?.let { apply(it); return }
        val before = book.value
        // Things to do found in a long note: they become its steps, under it.
        if (s.steps.isNotEmpty() && s.stepsOf != null) {
            // Each step read as a line of its own — «Николай Иванович обещал отдать ключи к четвергу» keeps its person and
            // its day (a step with a day stands on that day's page too); the note's own day is not given to the others.
            val note = before.line(s.stepsOf) ?: return
            val page = note.date ?: today.value
            // «Сможешь до понедельника?» — a deadline asked of you: steps without a day of their own get it as a window.
            val asked = Regex("[^.!?\n]*\\?").findAll(note.text).map { it.value }.firstNotNullOfOrNull { q ->
                Regex("(?iu)(?:^|\\s)(?:до|к)\\s+\\p{L}*\\s*[\\p{L}\\d.]+").find(q)?.value?.let { m ->
                    Read.draft("сделать " + m.trim(), before, page, today.value, now = null, lists = false).lines.firstOrNull()
                        ?.let { it.until ?: it.day }?.takeIf { it > page.toString() }
                }
            }
            // A message from someone («Олег Самокат:⏎…хотим: 1) … 2) …») — what it asks is what you owe them.
            val sender = Regex("^\\s*([А-ЯЁA-Z][\\p{L}-]+)(?:\\s+[«\"]?[\\p{L}-]+[»\"]?)?\\s*:\\s*\\n").find(note.text)?.groupValues?.get(1)
                ?.let { n -> before.person(n)?.name ?: n }
            val kids = s.steps.map { t ->
                val l = Read.draft(t, before, page, today.value, now = null, lists = false).lines.firstOrNull()?.let { x ->
                    if (sender != null && x.who.isEmpty()) x.copy(who = listOf(ru.anjeib.margin.book.Who(sender, ru.anjeib.margin.book.Dir.To))) else x
                }
                val day = l?.day?.takeIf { it != page.toString() || l.time != null || l.until != null }
                Line(text = l?.text ?: t, kind = ru.anjeib.margin.book.Kind.Todo, parentId = note.id, threadId = note.threadId,
                    who = l?.who.orEmpty(), day = day ?: asked?.let { page.toString() }, time = l?.time?.takeIf { day != null },
                    until = if (day != null) l?.until else asked)
            }
            // A step already written as a line of its own («напомнить классным руководителям…») is that line: it goes under
            // the note (keeping its day) — one «сделано» closes it, not two copies.
            val open = before.lines.filter { it.open && it.parentId == null && it.id != note.id }
            val same = kids.mapNotNull { k -> open.firstOrNull { ru.anjeib.margin.book.Change.alike(it.text, k.text) }?.let { k to it } }
            val fresh = kids - same.map { it.first }.toSet()
            store.update { b -> b.add(fresh).let { x -> x.copy(lines = x.lines.map { l -> if (same.any { it.second.id == l.id }) l.copy(parentId = note.id) else l }) } }
            say(if (same.isEmpty()) words.stepsAdded(s.steps.size) else words.stepsJoined(s.steps.size, same.size), before)
            return
        }
        if (s.ask.isEmpty()) return
        store.update { b -> s.ask.fold(b) { x, (id, st) -> if (x.line(id)?.open != true) x else if (st == ru.anjeib.margin.book.State.Dropped) x.drop(id) else x.toggle(id, today.value) } }
        say(words.closed(s.ask.size), before)
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
    /** The words would change a line already written — what and how, for the field's margin. */
    fun previewChange(text: String): ru.anjeib.margin.book.Change.Plan? =
        runCatching { ru.anjeib.margin.book.Change.read(text, book.value, today.value) }.getOrNull()

    private fun draft(text: String, t: Target): Read.Draft {
        val d = Read.draft(text, book.value, t.page, today.value, threadId = t.threadId, noDay = t.later)
        if (t.person == null) return d
        // Written on a person's page: the person is on every line, unless the words already name them another way.
        return d.copy(lines = d.lines.map { l -> if (l.who.any { it.name.equals(t.person, true) }) l else l.copy(who = l.who + ru.anjeib.margin.book.Who(t.person)) })
    }

    /** The words become lines on the target (or on their own day); Margin says where they went if not here. A question gets an answer. */
    fun write(text: String, t: Target, files: List<Attachment> = emptyList(),
              /** Said, not typed: the phone's reading is a draft — the AI always reads it again, as in 0.29. */
              spoken: Boolean = false): Boolean {
        val page = t.page
        // Words that change a line already written («перенеси стоматолога на пятницу») change it — with «вернуть».
        if (files.isEmpty() && change(text)) return true
        val d0 = if (text.isBlank() && files.isNotEmpty()) null else draft(text, t)
        if (d0?.question == true) { ask(text); return true }
        // Files alone are a note on the page, named by what they are.
        val d = d0 ?: Read.Draft(listOf(Line(text = fileWord(files), kind = ru.anjeib.margin.book.Kind.Note,
            day = if (t.later || t.threadId != null) null else page.toString(), threadId = t.threadId)), true, false, null)
        if (d.lines.isEmpty()) return false
        val lines = (if (files.isEmpty()) d.lines else d.lines.mapIndexed { i, l -> if (i == 0) l.copy(files = l.files + files) else l })
            // A repeat written on one of its days before its hour starts today.
            .map { if (page == today.value) it.beginToday(today.value, java.time.LocalTime.now()) else it }
        val before = book.value
        // A thread started by these words («#самокат») gathers the lines written before that name it.
        val newThread = d.threadTitle?.takeIf { tt -> before.threads.none { it.title.equals(tt, true) } }
        var gathered = 0
        store.update { b ->
            var x = b.add(lines + d.under)
            d.threadTitle?.let { tt -> lines.forEach { l -> x = x.setThread(l.id, tt) } }
            if (newThread != null) x.threads.firstOrNull { it.title.equals(newThread, true) }?.let { th ->
                val (y, n) = x.gatherInto(th.id); x = y; gathered = n
            }
            x
        }
        _fresh.value = lines.map { it.id }.toSet()
        _written.value = lines.map { it.id }
        viewModelScope.launch { delay(1200); _fresh.value = emptySet() }
        // Margin says where the words went only when they left the page in front of you.
        val here = t.threadId != null || t.person != null
        val away = if (here) emptyList() else lines.filter { if (t.later) it.date != null else it.date != page }
        // A long note with things to do in it: Margin offers to make them its steps — it does not take them out by itself.
        val steps = lines.singleOrNull()?.takeIf { it.kind == ru.anjeib.margin.book.Kind.Note && Read.isLongNote(it.text) }?.let { Read.stepsIn(it.text) }.orEmpty()
        away.firstOrNull()?.date?.let { d -> _flight.value = Flight(d, away.filter { it.date == d }.map { it.id }) }
        when {
            steps.isNotEmpty() -> say(words.stepsInNote(steps.size), before, steps = steps, stepsOf = lines.single().id)
            gathered > 0 && newThread != null -> say(words.gathered(gathered, newThread), before)
            lines.size > 1 -> say(words.writtenN(lines.size), before)
            away.isNotEmpty() -> say(words.writtenOn(away.first().date, today.value), before, day = away.first().date)
        }
        // Not sure of the words and the AI is at hand: it re-reads them, and what it reads differently shows as a correction.
        // Not sure of the words, or they were said aloud (no punctuation, one long breath) — the AI re-reads them; what it reads
        // differently shows as a correction.
        if ((!d.sure || spoken) && text.isNotBlank() && aiReady())
            // The quick model: on live dictation it reads a long breath right in a second and a half; the thinking one spent
            // half a minute and came back empty.
            reread(text, t, store.book.value.lines.filter { l -> lines.any { it.id == l.id } })
        return true
    }

    /** A file taken off its line — with «вернуть»; the file itself stays until the notebook is tidied. */
    fun removeFile(lineId: String, fileId: String) {
        val before = book.value
        store.update { it.removeFile(lineId, fileId) }
        say(words.fileRemoved, before)
    }

    private fun change(text: String): Boolean {
        val td = today.value
        val p = runCatching { ru.anjeib.margin.book.Change.read(text, book.value, td) }.getOrNull() ?: return false
        // Another line fits the words as well: ask about the likelier one instead of changing the wrong one.
        if (!p.sure && p.id != null) {
            val l = book.value.line(p.id) ?: return false
            say(if (p.part != null) words.changeAsk(p.part, p, td, of = l.text) else words.changeAsk(l.text, p, td, of = l.parentId?.let { book.value.line(it)?.text }), change = p)
            return true
        }
        return apply(p)
    }

    private fun apply(p: ru.anjeib.margin.book.Change.Plan): Boolean {
        val td = today.value
        val before = book.value
        if (p.what == What.MoveAll) {
            val to = if (p.later) null else p.day ?: td.plusDays(1)
            val n = before.toDecide(td).size
            if (n == 0) return false
            store.update { b -> before.toDecide(td).fold(b) { x, l -> x.move(l.id, to, td) } }
            say(words.movedAll(n, to, td), before, day = to)
            return true
        }
        val id = p.id ?: return false
        val l = before.line(id) ?: return false
        store.update { b ->
            when (p.what) {
                What.Move -> when {
                    p.later -> b.move(id, null, td)
                    p.time != null -> b.setWhen(id, p.day ?: l.date ?: td, p.time, today = td)
                    else -> b.move(id, p.day, td)
                }
                What.Drop -> if (p.day != null && l.repeat != null) b.dropOccurrence(id, p.day) else b.drop(id)
                else -> p.part?.let { part ->
                    // A line of several things, one of them done: it becomes its items, that one ticked.
                    val items = ru.anjeib.margin.book.Change.itemsOf(l.text)
                    val withItems = items.fold(b) { x, t -> x.addChild(id, t, ru.anjeib.margin.book.Kind.Todo) }
                    withItems.children(id).firstOrNull { it.text.equals(part, true) }?.let { withItems.toggle(it.id, td) } ?: withItems
                } ?: b.toggle(id, td)
            }
        }
        val now = book.value.line(id)
        // Moved to another day: it flies to the calendar like a new line would.
        if (p.what == What.Move && now?.date != null && now.date != l.date) _flight.value = Flight(now.date!!, listOf(id))
        say(when (p.what) {
            What.Move -> words.changedTo(l.text, if (p.later) null else now?.date, now?.time.takeIf { p.time != null }, td)
            What.Drop -> words.changedDropped(l.text, p.day?.takeIf { l.repeat != null }, td)
            else -> words.changedDone(p.part?.let { "$it» в «${words.short(l.text)}" }
                ?: l.parentId?.let { pid -> before.line(pid)?.let { "${l.text}» в «${words.short(it.text)}" } } ?: l.text)
        }, before, day = now?.date?.takeIf { p.what == What.Move })
        _fresh.value = setOf(id)
        viewModelScope.launch { delay(1200); _fresh.value = emptySet() }
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
    private fun online() = ru.anjeib.margin.ai.Net.online(getApplication())
    /** The AI can be asked: a key or an invite code, and a network. */
    fun aiReady() = Llm.ready(prefs.settings.value) && online()

    private fun reread(text: String, t: Target, phone: List<Line>, strong: Boolean = false) {
        if (phone.isEmpty()) return
        val bookNow = book.value
        // The AI sees the notebook as it was before these words — else it «closes» the lines it is asked to read.
        val ids = phone.map { it.id }.toSet()
        val before0 = bookNow.copy(lines = bookNow.lines.filterNot { it.id in ids })
        say(words.rereading)
        viewModelScope.launch {
            val prompt = AiRead.readPrompt(before0, text, t.page, today.value, words.ru)
            val reading = complete(prompt, text, strong = strong) { AiRead.parseRead(it, before0, t.page, text.trim(), t.threadId, noDay = t.later) }
            if (reading == null) { if (_said.value?.text == words.rereading) hush(); return@launch }
            val before = book.value
            store.update { b ->
                val m = AiRead.merge(b, phone, reading, today.value)
                // A repeat the AI set on its next day while today is one of its days: it begins today, as on the phone.
                if (t.page != today.value) m else m.copy(lines = m.lines.map { l ->
                    if (l.id in ids || b.line(l.id) == null) l.beginToday(today.value, java.time.LocalTime.now()) else l })
            }
            val after = book.value
            // What the words seem to close is asked, never done silently: «сделано: «Приедет Петров»? · да · нет».
            val asking = AiRead.proposals(after, phone, reading)
            if (after != before) {
                _fresh.value = after.lines.filter { l -> before.line(l.id) == null }.map { it.id }.toSet()
                if (_fresh.value.isNotEmpty()) _written.value = _fresh.value.toList()
                viewModelScope.launch { delay(1200); _fresh.value = emptySet() }
            }
            when {
                asking.isNotEmpty() -> say(words.closeAsk(asking.mapNotNull { (id, st) -> after.line(id)?.let { it.text to (st == ru.anjeib.margin.book.State.Dropped) } }),
                    if (after != before) before else null, ask = asking)
                after != before -> {
                    // Where the corrected words now stand, if not on the page in front of you: «Margin уточнил: на вт ›».
                    val away = after.lines.filter { l -> (l.id in ids || before.line(l.id) == null) && l.parentId == null }
                        .mapNotNull { it.date }.firstOrNull { it != t.page }
                    if (away != null && t.threadId == null && t.person == null) {
                        _flight.value = Flight(away, after.lines.filter { it.date == away && (it.id in ids || before.line(it.id) == null) }.map { it.id })
                        say(words.rereadOn(away, today.value), before, day = away)
                    } else say(words.reread, before)
                }
                _said.value?.text == words.rereading -> hush()
            }
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

    /** The page's «на полях: …» opened over the field: its promises and what is left from before. */
    fun explain(date: LocalDate) {
        val b = book.value
        _asked.value = Asked(words.weekday(date), Ask.margin(b, date, today.value))
    }

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

    /** The speech model for words without a network (60 MB, once). */
    fun downloadWhisper() { viewModelScope.launch { runCatching { ru.anjeib.margin.voice.LocalWhisper.download(getApplication(), ru.anjeib.margin.voice.LocalWhisper.Model.Base) } } }

    /** Words from a recording: SpeechKit through the AI door when online, Whisper on the phone otherwise. */
    fun canHear(): Boolean = aiReady() || ru.anjeib.margin.voice.LocalWhisper.ready(getApplication())

    fun transcribe(audio: Attachment, onText: (String?) -> Unit) {
        val ctx = getApplication<Application>()
        val s = prefs.settings.value
        viewModelScope.launch {
            val text = runCatching {
                val f = ru.anjeib.margin.data.Files.file(ctx, audio) ?: return@runCatching null
                if (!aiReady() && !ru.anjeib.margin.voice.LocalWhisper.ready(ctx)) throw IllegalStateException(words.voiceNoNet)
                val hints = book.value.persons.map { it.name }.take(40)
                if (aiReady() && !s.whisperAlways) {
                    val bytes = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { f.readBytes() }
                    Llm.transcribe(s, bytes, hints).also { prefs.addSpend(it.rub) }.content
                } else ru.anjeib.margin.voice.LocalWhisper.transcribe(ctx, f, if (words.ru) "ru" else "en", hints)
            }.onFailure { e -> say(words.voiceFailed(e.message)) }.getOrNull()
            ru.anjeib.margin.data.Files.delete(ctx, audio)
            if (text == null) { onText(""); return@launch }
            onText(text.trim().ifBlank { null })
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
    /** Words under a line: each item of a list on its own row; [kind] — the mark chosen for them (• a step, – a note). */
    fun writeUnder(id: String, text: String, kind: ru.anjeib.margin.book.Kind? = null) {
        val items = Read.items(text)
        if (items.isEmpty()) return
        store.update { b ->
            items.fold(b) { x, t -> x.addChild(id, t, kind ?: if (items.size > 1 || Read.isStep(t)) ru.anjeib.margin.book.Kind.Todo else ru.anjeib.margin.book.Kind.Note) }
        }
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

    fun closeDay(date: LocalDate, decided: Set<String> = emptySet()) {
        store.update { it.closeDay(date, decided = decided) }
        _stamping.value = date
        // A day closed at night: once the stamp has landed, the notebook's today is the calendar's.
        viewModelScope.launch { delay(4500); if (_stamping.value == date) _stamping.value = null; refreshToday() }
    }

    /** A thread closed (to the end of the index) or opened again. */
    fun closeThread(id: String, close: Boolean) {
        val before = book.value
        store.update { if (close) it.closeThread(id) else it.reopenThread(id) }
        say(if (close) words.threadClosed else words.threadOpened, before)
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
    fun toggleExamples() = store.update { if (it.hasSamples()) it.withoutSamples() else it.withSamples(Examples.build(today.value, words.ru)) }

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
