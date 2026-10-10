package ru.anjeib.margin.book

import kotlinx.serialization.Serializable
import ru.anjeib.margin.data.Attachment
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/*
 * «Поля» (docs/polya/DESIGN.md): everything is a line. Your words stay right of the margin rule, the mark on the rule
 * says what the line is and where it stands, and what Margin understood lives on the margin as notes.
 * Four things exist: a page (a day — not stored, it is a view), a line, a thread, a person.
 */

/** What a line is. The mark on the rule: • to-do, ○ event, – note. */
@Serializable
enum class Kind { Todo, Event, Note }

/** Where a to-do stands. Done — ×; dropped — × faint with «не делаем». Notes and events stay Open. */
@Serializable
enum class State { Open, Done, Dropped }

/** «→ Лёше» — you promised; «← Дима» — you wait for it; «с Олегом» — just together. */
@Serializable
enum class Dir { To, From, With }

@Serializable
data class Who(val name: String, val dir: Dir = Dir.With)

/** A repeat: daily | weekly (days 1..7) | monthly (the same date) | yearly. */
@Serializable
data class Repeat(val freq: String, val interval: Int = 1, val days: List<Int> = emptyList())

/** A correction seen on the margin as «~~чт~~ пт»: what changed, from what to what, by whom. */
@Serializable
data class Edit(val what: String, val old: String? = null, val new: String? = null, val by: String = "you", val at: String = nowStamp())

/** A move of a line from one page to another: the old page keeps a trace «› → пт». */
@Serializable
data class Move(val from: String, val to: String? = null, val at: String = nowStamp())

@Serializable
data class Line(
    val id: String = newId(),
    /** Your words — as written, only the first letter made a capital; words of time went to the margin. */
    val text: String,
    val kind: Kind = Kind.Todo,
    val state: State = State.Open,
    /** The page (ISO date). Null — «потом», or a thread's line without a day. */
    val day: String? = null,
    /** The hour, «15:00». */
    val time: String? = null,
    /** A soft deadline: the line stays on every page from [day] up to this date, «до пт». */
    val until: String? = null,
    /** «утром», «вечером» without an hour: morning | day | afternoon | evening | night. */
    val part: String? = null,
    val repeat: Repeat? = null,
    val threadId: String? = null,
    /** A continuation of another line: its step (to-do) or its comment (note). */
    val parentId: String? = null,
    val who: List<Who> = emptyList(),
    val place: String? = null,
    val files: List<Attachment> = emptyList(),
    /** Questions Margin asks on the margin: "when" | "who" | "thread". */
    val ask: List<String> = emptyList(),
    val edits: List<Edit> = emptyList(),
    val trail: List<Move> = emptyList(),
    val writtenAt: String = nowStamp(),
    val doneAt: String? = null,
    /** The phrase as it was typed or said. */
    val source: String? = null,
    /** One of «Примеры»: leaves with «Убрать примеры». */
    val sample: Boolean = false,
) {
    val date: LocalDate? get() = day?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    val untilDate: LocalDate? get() = until?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    val hour: LocalTime? get() = time?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
    val written: LocalDateTime get() = runCatching { LocalDateTime.parse(writtenAt) }.getOrElse { LocalDateTime.now() }
    val isTodo get() = kind == Kind.Todo
    val open get() = state == State.Open
    fun to() = who.firstOrNull { it.dir == Dir.To }?.name
    fun from() = who.firstOrNull { it.dir == Dir.From }?.name
}

@Serializable
data class Thread(
    val id: String = newId(),
    val title: String,
    /** personal | work — the thread's, never a badge on each line. */
    val sphere: String? = null,
    val createdAt: String = nowStamp(),
    val closedAt: String? = null,
    val sample: Boolean = false,
)

@Serializable
data class Person(
    val id: String = newId(),
    val name: String,
    val aliases: List<String> = emptyList(),
    val about: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val telegram: String? = null,
    /** Added by the user (a contact, «+ контакт»): stays in the index even with no lines. */
    val kept: Boolean = false,
    val sample: Boolean = false,
    /** A business, not a person: «Типография «Оттиск»», «ЖЭК» — in the address book with the companies. */
    val org: Boolean = false,
) {
    fun matches(n: String) = name.equals(n, true) || aliases.any { it.equals(n, true) }
}

/** A closed day's stamp: the hour, and what came of the day — done, moved on, not doing (the evening's decisions too). */
@Serializable
data class DayClose(val at: String, val done: Int = 0, val moved: Int = 0, val dropped: Int = 0)

/** The whole notebook. [v] grows with every change an older build would read wrongly. */
@Serializable
data class Book(
    val v: Int = 1,
    val lines: List<Line> = emptyList(),
    val threads: List<Thread> = emptyList(),
    val persons: List<Person> = emptyList(),
    /** Days closed with «перелистнуть день»: the date → the stamp on its page. */
    val closed: Map<String, DayClose> = emptyMap(),
) {
    fun line(id: String) = lines.find { it.id == id }
    fun thread(id: String?) = id?.let { i -> threads.find { it.id == i } }
    fun person(name: String) = persons.find { it.matches(name) }
    fun children(id: String) = lines.filter { it.parentId == id }

    /** A list under a line: steps done and steps in all («2 из 5»); null when nothing to do is written under it. */
    fun steps(id: String): Pair<Int, Int>? = children(id).filter { it.kind == Kind.Todo && it.state != State.Dropped }
        .takeIf { it.isNotEmpty() }?.let { s -> s.count { it.state == State.Done } to s.size }
}

fun newId(): String = java.util.UUID.randomUUID().toString().take(12)
fun nowStamp(): String = LocalDateTime.now().withSecond(0).withNano(0).toString()
