package ru.anjeib.margin.ui

import ru.anjeib.margin.data.AppData
import ru.anjeib.margin.data.Entry
import ru.anjeib.margin.data.Note
import ru.anjeib.margin.data.Person
import ru.anjeib.margin.data.Task
import ru.anjeib.margin.data.isIn
import ru.anjeib.margin.data.dayMonth
import ru.anjeib.margin.data.shortLabel
import ru.anjeib.margin.data.text
import ru.anjeib.margin.i18n.L
import java.time.LocalDate
import java.time.LocalDateTime

/** The «thought → task → person» web, computed on the phone from what is already stored. */

private val word = Regex("[\\p{L}\\p{Nd}]{4,}")
fun stemsOf(text: String): Set<String> =
    word.findAll(text.lowercase()).map { m -> val w = m.value; w.take(if (w.length >= 7) 5 else w.length - 1) }.toSet()

fun dateOf(stamp: String?): LocalDate? = stamp?.let { runCatching { LocalDateTime.parse(it.take(16)).toLocalDate() }.getOrNull() ?: runCatching { LocalDate.parse(it.take(10)) }.getOrNull() }

fun Person.involvedIn(t: Task) = t.people.any { isIn(it, t.who) } || t.waitingFor?.let { isIn(it, t.who) } == true ||
    t.promisedTo?.let { isIn(it, t.who) } == true || t.subtasks.any { s -> s.assignee?.let { isIn(it, s.who) } == true || s.from?.let { isIn(it, s.who) } == true }

/** «↳ из заметки «vuzburo · идеи» · 5 окт» or «↳ из разговора · 5 окт». Null when the idea row in «Связи» already says it. */
fun originLine(t: Task, d: AppData): String? {
    if (ideaOf(t, d) != null) return null
    val note = t.noteId?.let { id -> d.notes.find { it.id == id } }
    val created = LocalDate.parse(t.createdAt).dayMonth()
    return when {
        note != null -> L.s.fromNote(note.title.ifBlank { L.s.untitled }, (note.entries.find { it.id == t.sourceEntryId }?.at?.let { dateOf(it)?.dayMonth() } ?: created))
        t.source != null -> L.s.fromChat(created)
        else -> null
    }
}

/** The idea a task came from: the exact entry when known, else the entry that shares most words with the task. */
fun ideaOf(t: Task, d: AppData): Pair<Note, Entry>? {
    val note = t.noteId?.let { id -> d.notes.find { it.id == id } } ?: return null
    note.entries.find { it.id == t.sourceEntryId }?.let { return note to it }
    val q = stemsOf(t.title + " " + t.source.orEmpty())
    // Only an entry that really shares words — never «the least unrelated» one.
    return note.entries.map { it to (stemsOf(it.text) intersect q).size }.filter { it.second > 0 }.maxByOrNull { it.second }?.let { note to it.first }
}

data class TaskLink(val kind: String, val title: String, val meta: String, val taskId: String? = null, val noteId: String? = null)

/**
 * «Откуда» in a task card: only where the matter came from — the note it grew out of (owner, 18 окт). Open waits and promises
 * with the same people are not shown here: sharing a person is no link between two matters, and they are on the person's card.
 */
fun taskLinks(t: Task, d: AppData): List<TaskLink> = buildList {
    ideaOf(t, d)?.let { (n, e) -> add(TaskLink("idea", L.s.linkIdea + " · " + (dateOf(e.at)?.dayMonth() ?: ""), "«" + e.text.take(90) + "»", noteId = n.id)) }
}

/** A dated history of a person, each line saying where it came from. */
fun personHistory(p: Person, d: AppData): List<Triple<LocalDate, String, String>> {
    val names = listOf(p.name) + p.aliases
    fun mentions(text: String) = names.any { n -> n.length >= 3 && text.lowercase().contains(n.lowercase().take(n.length - 1)) }
    val out = mutableListOf<Triple<LocalDate, String, String>>()
    d.tasks.filter { p.involvedIn(it) }.forEach { t ->
        // A record of only a photo or a file says what it holds; the source names the topic or the matter it is in.
        t.entries.filter { mentions(it.text) || p.involvedIn(t) }.takeLast(2).forEach { e ->
            val text = e.text.ifBlank { e.attachments.joinToString(", ") { a -> a.caption ?: a.name } }.take(80)
            if (text.isNotBlank()) dateOf(e.at)?.let { out += Triple(it, text, if (t.topic) L.s.srcTopic(t.title) else L.s.srcMatter(t.title)) }
        }
    }
    d.notes.forEach { n -> n.entries.filter { mentions(it.text) }.forEach { e -> dateOf(e.at)?.let { out += Triple(it, e.text.take(80), L.s.srcNote + " «${n.title}»") } } }
    return out.distinctBy { it.second }.sortedByDescending { it.first }.take(8)
}

/** Tasks an idea (a note entry) became: the exact link when known, else older tasks of the note sharing its words. */
fun tasksOfEntry(n: Note, e: Entry, d: AppData): List<Task> {
    val q = stemsOf(e.text)
    if (q.isEmpty()) return d.tasks.filter { it.sourceEntryId == e.id }
    return d.tasks.filter { t -> t.sourceEntryId == e.id || (t.sourceEntryId == null && t.noteId == n.id && (stemsOf(t.title) intersect q).size >= minOf(2, q.size)) }
}

/** Each idea of a project with the tasks it became; the page shows only those that are still just ideas. */
data class Chain(val note: Note, val entry: Entry, val tasks: List<Task>)

fun ideaChains(project: String, d: AppData): List<Chain> =
    d.notes.filter { it.project == project }.flatMap { n -> n.entries.filter { it.text.isNotBlank() }.map { n to it } }
        .map { (n, e) -> Chain(n, e, tasksOfEntry(n, e, d)) }
        .sortedByDescending { it.entry.at }.take(8)
