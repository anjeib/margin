package ru.anjeib.margin.ui

import ru.anjeib.margin.data.AppData
import ru.anjeib.margin.data.Entry
import ru.anjeib.margin.data.Note
import ru.anjeib.margin.data.Person
import ru.anjeib.margin.data.Task
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

fun Person.involvedIn(t: Task) = t.people.any { matches(it) } || t.waitingFor?.let { matches(it) } == true ||
    t.promisedTo?.let { matches(it) } == true || t.subtasks.any { s -> s.assignee?.let { matches(it) } == true }

/** «↳ из идеи «калькулятор стоимости»», «↳ из заметки …» or «↳ из разговора · 5 окт». */
fun originLine(t: Task, d: AppData): String? {
    val note = t.noteId?.let { id -> d.notes.find { it.id == id } }
    val created = LocalDate.parse(t.createdAt).dayMonth()
    val idea = ideaOf(t, d)?.second
    return when {
        idea != null -> L.s.fromIdea(idea.text.trim().let { if (it.length > 40) it.take(38).trimEnd() + "…" else it })
        note != null -> L.s.fromNote(note.title.ifBlank { L.s.untitled }, created)
        t.source != null -> L.s.fromChat(created)
        else -> null
    }
}

/** The idea a task came from: the exact entry when known, else the entry that shares most words with the task. */
fun ideaOf(t: Task, d: AppData): Pair<Note, Entry>? {
    val note = t.noteId?.let { id -> d.notes.find { it.id == id } } ?: return null
    note.entries.find { it.id == t.sourceEntryId }?.let { return note to it }
    val q = stemsOf(t.title + " " + t.source.orEmpty())
    return note.entries.maxByOrNull { (stemsOf(it.text) intersect q).size }?.let { note to it }
}

/** A dated history of a person, each line saying where it came from. */
fun personHistory(p: Person, d: AppData): List<Triple<LocalDate, String, String>> {
    val names = listOf(p.name) + p.aliases
    fun mentions(text: String) = names.any { n -> n.length >= 3 && text.lowercase().contains(n.lowercase().take(n.length - 1)) }
    val out = mutableListOf<Triple<LocalDate, String, String>>()
    d.tasks.filter { p.involvedIn(it) }.forEach { t ->
        t.entries.filter { mentions(it.text) || p.involvedIn(t) }.takeLast(2).forEach { e -> dateOf(e.at)?.let { out += Triple(it, e.text.take(80), L.s.srcTaskEntry) } }
        t.source?.takeIf { t.done && mentions(it) }?.let { out += Triple(LocalDate.parse(t.createdAt), it.take(80), L.s.srcChat) }
    }
    d.notes.forEach { n -> n.entries.filter { mentions(it.text) }.forEach { e -> dateOf(e.at)?.let { out += Triple(it, e.text.take(80), L.s.srcNote + " «${n.title}»") } } }
    return out.distinctBy { it.second }.sortedByDescending { it.first }.take(8)
}

/** Each idea of a project with the tasks it became; the page shows only those that are still just ideas. */
data class Chain(val note: Note, val entry: Entry, val tasks: List<Task>)

fun ideaChains(project: String, d: AppData): List<Chain> =
    d.notes.filter { it.project == project }.flatMap { n -> n.entries.filter { it.text.isNotBlank() }.map { n to it } }.map { (n, e) ->
        val q = stemsOf(e.text)
        val tasks = d.tasks.filter { t -> t.sourceEntryId == e.id || (t.sourceEntryId == null && t.noteId == n.id && (stemsOf(t.title) intersect q).isNotEmpty()) }
        Chain(n, e, tasks)
    }.sortedByDescending { it.entry.at }.take(8)
