package ru.anjeib.margin.data

import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.util.UUID

fun newId(): String = UUID.randomUUID().toString().take(8)

@Serializable
data class Subtask(
    val id: String = newId(),
    val title: String,
    val done: Boolean = false,
    val assignee: String? = null,
    val children: List<Subtask> = emptyList(),
)

fun nowStamp(): String = java.time.LocalDateTime.now().withNano(0).withSecond(0).toString()

@Serializable
data class ContactInfo(val name: String, val phone: String? = null, val email: String? = null)

@Serializable
data class Attachment(
    val id: String = newId(),
    /** photo | video | file | audio | contact | link */
    val kind: String,
    /** File name inside filesDir/att (null for contacts). */
    val file: String? = null,
    val name: String = "",
    val mime: String? = null,
    val size: Long = 0,
    /** Seconds, for audio/video. */
    val duration: Int = 0,
    val transcript: String? = null,
    val contact: ContactInfo? = null,
    /** The user's words under a photo or video. */
    val caption: String? = null,
    /** Text recognised on a photo (a business card, a receipt, a whiteboard) — searchable, seen by the AI. */
    val ocr: String? = null,
    /** For «link»: the address; [name] holds the page title. */
    val url: String? = null,
)

/** A dated record: a line of a note's stream or of a task's log, with optional attachments. */
@Serializable
data class Entry(
    val id: String = newId(),
    val at: String = nowStamp(),
    val text: String = "",
    val attachments: List<Attachment> = emptyList(),
    /** One visit to the note: blocks written in the same visit sit under one date line. */
    val session: String? = null,
    /** A «Список» block in a note: items with ticks (the same shape as subtasks). */
    val items: List<Subtask> = emptyList(),
    val isList: Boolean = false,
)

@Serializable
data class Person(
    val id: String = newId(),
    val name: String,
    val aliases: List<String> = emptyList(),
    /** Short AI-maintained description: who they are, what they do with the user. */
    val about: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val updatedAt: String? = null,
    /** True when [about] came from the user's own words (a person action or a manual edit), not an AI guess. */
    val aboutFromUser: Boolean = false,
    /** «Свежая сводка»: what's going on with this person now (AI), and when it was written. */
    val summary: String? = null,
    val summaryAt: String? = null,
    /** What the summary was written from — when their tasks change, it is out of date. */
    val summaryFp: String? = null,
    val company: String? = null,
    val address: String? = null,
    val telegram: String? = null,
    /** MAX messenger: a username or a phone. */
    val max: String? = null,
    val archived: Boolean = false,
    /** «Личная информация»: short facts the user told about them (birthday, family, what they like). */
    val personal: List<String> = emptyList(),
) {
    fun matches(n: String) = name.equals(n, true) || aliases.any { it.equals(n, true) }
}

/** How a task comes back: every [interval] days/weeks/months/years; weekly may pin weekdays (1=Mon…7=Sun). */
@Serializable
data class Repeat(
    val freq: String, // daily | weekly | monthly | yearly
    val interval: Int = 1,
    val days: List<Int> = emptyList(),
)

/**
 * One line of a task's history. Stored as data, not text, so it reads in whatever language the app is in.
 * [kind]: created, date, time, repeat, waiting, promised, place, done, reopened, dropped. [by]: you, ai, swipe.
 */
@Serializable
data class Change(
    val at: String = nowStamp(),
    val kind: String,
    val value: String? = null,
    val by: String = "you",
)

/** [this] after an edit of [old]: the history gains a line per thing that really changed. */
fun Task.logged(old: Task?, by: String): Task {
    if (old == null) return if (history.isEmpty()) copy(history = listOf(Change(kind = "created", value = if (noteId != null) "idea" else if (source != null) "chat" else null, by = by))) else this
    val lines = buildList {
        if (due != old.due || start != old.start) add(Change(kind = "date", value = due ?: start, by = by))
        if (time != old.time) add(Change(kind = "time", value = time, by = by))
        if (repeat != old.repeat) add(Change(kind = "repeat", value = repeat?.let { r -> r.freq + ":" + r.interval + ":" + r.days.joinToString(",") }, by = by))
        if (waitingFor != old.waitingFor) add(Change(kind = "waiting", value = waitingFor, by = by))
        if (promisedTo != old.promisedTo) add(Change(kind = "promised", value = promisedTo, by = by))
        if (place != old.place) add(Change(kind = "place", value = place, by = by))
        if (done != old.done) add(Change(kind = if (!done) "reopened" else if (dropped) "dropped" else "done", by = by))
    }
    return if (lines.isEmpty()) this else copy(history = (old.history + lines).takeLast(40))
}

/** Something typed while offline, waiting to be sorted by the AI. */
@Serializable
data class Queued(
    val id: String = newId(),
    val text: String,
    val scope: String = "main", // main | notes | task:<id>
    val files: List<Attachment> = emptyList(),
    val at: String = nowStamp(),
)

@Serializable
data class Task(
    val id: String = newId(),
    val title: String,
    /** ISO dates (yyyy-MM-dd). A range when both are set. */
    val start: String? = null,
    val due: String? = null,
    /** HH:mm — the task happens / should be reminded at this time on [due] (or [start]). */
    val time: String? = null,
    val remindAt: String? = null, // ISO date-time yyyy-MM-ddTHH:mm
    val people: List<String> = emptyList(),
    val project: String? = null,
    /** Legacy single comment — migrated into [entries]. */
    val comment: String? = null,
    /** Dated records about the task (things said, files, photos). */
    val entries: List<Entry> = emptyList(),
    /** The note this task came from / belongs to. */
    val noteId: String? = null,
    /** Someone else owes something here: the task is a follow-up, not work for the user. */
    val waitingFor: String? = null,
    /** The user owes this to someone («обещал Анне показать до пт»). */
    val promisedTo: String? = null,
    /** What the user actually said when the task was born — the «из разговора» origin. */
    val source: String? = null,
    /** The note entry (idea) this task grew from, when it came from one. */
    val sourceEntryId: String? = null,
    /** «утром / днём / вечером» said instead of a time: kept as words (morning, day, afternoon, evening), never turned into 09:00. */
    val dayPart: String? = null,
    /** [source] was already tidied up (spelling, capitals) for the card. */
    val sourceFixed: Boolean = false,
    /** Where it happens: an address or a place («Ленина 5», «кафе Зерно»). */
    val place: String? = null,
    val subtasks: List<Subtask> = emptyList(),
    val repeat: Repeat? = null,
    /** How many times the task was pushed to a later day — lets reviews spot what never happens. */
    val postponed: Int = 0,
    val done: Boolean = false,
    val doneAt: String? = null,
    /** Closed without doing it («Не буду делать»): done for the lists, but honest in reviews. */
    val dropped: Boolean = false,
    /** What changed and who changed it — shown folded in the card, so an AI edit is never a mystery. */
    val history: List<Change> = emptyList(),
    val createdAt: String = LocalDate.now().toString(),
) {
    val startDate: LocalDate? get() = start?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    val dueDate: LocalDate? get() = due?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    /** The day the task belongs to when it has only one date. */
    val anchor: LocalDate? get() = dueDate ?: startDate
}

@Serializable
data class Note(
    val id: String = newId(),
    val title: String,
    /** Legacy free text — migrated into [entries]. */
    val body: String = "",
    val entries: List<Entry> = emptyList(),
    val project: String? = null,
    val aiSummary: String? = null,
    /** Put away: out of the lists and the AI's view, back from «Архив». */
    val archived: Boolean = false,
    /** The same properties a task has, minus «done»: who, where, and a reminder. */
    val people: List<String> = emptyList(),
    val place: String? = null,
    val remindAt: String? = null,
    /** A note that is all title had its spelling and capitals tidied once («Посмотреть «Одиссею» Нолана»). */
    val titleFixed: Boolean = false,
    val extractedTasks: Int = 0,
    val updatedAt: String = java.time.LocalDateTime.now().withNano(0).toString(),
)

@Serializable
data class ChatMsg(
    val id: String = newId(),
    val fromUser: Boolean,
    val text: String,
    /** Ids of tasks the assistant created / changed with this message — shown as cards. */
    val taskIds: List<String> = emptyList(),
    val caption: String? = null,
    val options: List<String> = emptyList(),
    val error: Boolean = false,
    val at: String = java.time.LocalTime.now().withNano(0).withSecond(0).toString(),
)

@Serializable
data class ProjectSummary(val text: String, val at: String, val fp: String? = null)

@Serializable
data class Brief(
    val date: String, val text: String, val focus: String? = null, val lang: String? = null, val headline: String? = null,
    /** The task the headline is about — when it's done or moved, the brief is rebuilt. */
    val mainTaskId: String? = null,
    /** HH:mm of the last rebuild, shown as «обновлено 14:20». */
    val at: String? = null,
    /** Today's open task ids at build time: a different set means the brief may be stale. */
    val fingerprint: String? = null,
    val revisions: Int = 0,
)

@Serializable
data class AppData(
    val tasks: List<Task> = emptyList(),
    val notes: List<Note> = emptyList(),
    val chat: List<ChatMsg> = emptyList(),
    val brief: Brief? = null,
    /** Legacy names list — migrated into [persons]. */
    val people: List<String> = emptyList(),
    val persons: List<Person> = emptyList(),
    val projects: List<String> = emptyList(),
    val queue: List<Queued> = emptyList(),
    /** Per-project «сводка недели», keyed by project tag. */
    val projectSummaries: Map<String, ProjectSummary> = emptyMap(),
) {
    fun person(name: String): Person? = persons.find { it.matches(name) }

    /** Brings files written by older versions to the current shape. */
    fun migrated(): AppData {
        var d = this
        if (d.people.isNotEmpty()) {
            val extra = d.people.distinctBy { it.lowercase() }.filter { n -> d.persons.none { it.matches(n) } }.map { Person(name = it) }
            d = d.copy(persons = d.persons + extra, people = emptyList())
        }
        // Short descriptions saved before AI summaries existed came from the user's words («наш дизайнер»): keep them.
        if (d.persons.any { !it.aboutFromUser && it.about != null && it.summary == null && it.about.length <= 40 })
            d = d.copy(persons = d.persons.map { if (!it.aboutFromUser && it.about != null && it.summary == null && it.about.length <= 40) it.copy(aboutFromUser = true) else it })
        // Same name twice (older versions could do that) → one person with everything known.
        val byName = d.persons.groupBy { it.name.lowercase() }
        if (byName.values.any { it.size > 1 })
            d = d.copy(persons = byName.values.map { g ->
                g.reduce { a, b -> a.copy(about = a.about ?: b.about, phone = a.phone ?: b.phone, email = a.email ?: b.email, aliases = (a.aliases + b.aliases).distinct()) }
            })
        if (d.tasks.any { it.comment != null })
            d = d.copy(tasks = d.tasks.map { t ->
                if (t.comment == null) t else t.copy(comment = null, entries = listOf(Entry(at = t.createdAt + "T00:00", text = t.comment)) + t.entries)
            })
        if (d.notes.any { it.body.isNotBlank() })
            d = d.copy(notes = d.notes.map { n ->
                if (n.body.isBlank()) n else n.copy(body = "", entries = listOf(Entry(at = n.updatedAt.take(16), text = n.body.trim())) + n.entries)
            })
        // Records written before 3.10 could start with a small letter («купить хлеб», «тимур»).
        fun subs(l: List<Subtask>, names: List<String>): List<Subtask> = l.map { it.copy(title = it.title.sentence().withNames(names), children = subs(it.children, names)) }
        val names = d.persons.map { it.name.properName() }
        val fixed = d.copy(
            persons = d.persons.map { it.copy(name = it.name.properName(), address = it.address?.address()) },
            tasks = d.tasks.map { t ->
                val who = t.people + listOfNotNull(t.waitingFor, t.promisedTo)
                t.copy(title = t.title.sentence().withNames(who + names), place = t.place?.address(), people = t.people.map { if (it.length > 1) it.properName() else it },
                    waitingFor = t.waitingFor?.properName(), promisedTo = t.promisedTo?.properName(), subtasks = subs(t.subtasks, who + names),
                    entries = t.entries.map { e -> e.copy(text = e.text.sentence()) })
            },
            notes = d.notes.map { n -> n.copy(title = n.title.sentence(), place = n.place?.address(), entries = n.entries.map { e -> e.copy(text = e.text.sentence()) }) },
        )
        if (fixed != d) d = fixed
        return d
    }
}

/** Plain text of a note: all entries, for search and AI context. */
val Note.text: String get() = entries.joinToString("\n") { it.fullText }

/**
 * Everything an entry says, not only its typed words: list items, captions, voice transcripts, text on photos,
 * link titles, contact names. Search and the AI read this, so a decision on a whiteboard photo is found too.
 */
val Entry.fullText: String get() = listOfNotNull(
    text.takeIf { it.isNotBlank() },
    items.takeIf { it.isNotEmpty() }?.joinToString("; ") { it.title },
    *attachments.flatMap { a -> listOfNotNull(a.caption, a.transcript, a.ocr, if (a.kind == "link") a.name else null, a.contact?.let { c -> listOfNotNull(c.name, c.phone).joinToString(" ") }) }.toTypedArray(),
).joinToString(" · ")

// ---- subtask tree helpers ----

/** Progress over top-level subtasks: done to total. */
fun List<Subtask>.progress(): Pair<Int, Int> = count { it.done } to size

fun List<Subtask>.mapTree(id: String, f: (Subtask) -> Subtask?): List<Subtask> = mapNotNull { s ->
    if (s.id == id) f(s)
    else {
        val kids = s.children.mapTree(id, f)
        // A parent is done exactly when all of its children are.
        if (kids === s.children || kids == s.children) s
        else s.copy(children = kids, done = kids.isNotEmpty() && kids.all { it.done })
    }
}

fun List<Subtask>.flatten(depth: Int = 0): List<Pair<Subtask, Int>> =
    flatMap { listOf(it to depth) + it.children.flatten(depth + 1) }

fun Subtask.withDone(done: Boolean): Subtask = copy(done = done, children = children.map { it.withDone(done) })

fun List<Subtask>.findSub(id: String): Subtask? {
    for (s in this) { if (s.id == id) return s; s.children.findSub(id)?.let { return it } }
    return null
}
