package ru.anjeib.margin.paper

import ru.anjeib.margin.book.Book
import ru.anjeib.margin.book.Dir
import ru.anjeib.margin.book.Kind
import ru.anjeib.margin.book.Line
import ru.anjeib.margin.book.State
import ru.anjeib.margin.book.lastDay
import ru.anjeib.margin.book.moves
import java.time.LocalDate
import java.time.LocalDateTime

/*
 * What the margin says about a line (DESIGN.md §5), in the order of what matters most:
 * a question → a tail → when → a repeated put-off → repeat → who → thread → place → files.
 * Nothing the page itself already says is repeated: on its own page a line shows no date, only the hour.
 */

enum class Seen { Page, Tail, Trace, Sheet }

fun notesOf(l: Line, page: LocalDate?, today: LocalDate, book: Book, w: Words, seen: Seen = Seen.Page): List<Note> {
    val out = ArrayList<Note>()
    l.ask.firstOrNull()?.let { if (l.state == State.Open) out += Note(w.ask(it), Tone.Attention) }
    if (l.state == State.Dropped) out += Note(w.dropped, Tone.Gone)
    when (seen) {
        Seen.Tail -> l.lastDay()?.let { out += Note(w.tailSince(it), Tone.Attention) }
        Seen.Trace -> out += Note(w.movedTo(l.trail.lastOrNull()?.to?.let { runCatching { LocalDate.parse(it) }.getOrNull() }, today), Tone.Quiet)
        else -> {}
    }
    // When — the day (only if it is not the page's own) and the hour on lines of their own: the margin is narrow.
    val dayText = listOfNotNull(
        l.date?.takeIf { it != page && l.until == null && seen != Seen.Tail && seen != Seen.Trace }?.let { w.near(it, today) },
        l.untilDate?.takeIf { seen != Seen.Trace }?.let { w.until(it, today) },
    ).joinToString(" ").ifBlank { null }
    val hourText = l.time ?: l.part?.let { w.part(it) }
    if (dayText != null || hourText != null) {
        // A correction made today shows what it was («~~чт~~ пт»): seen once, gone tomorrow.
        val e = l.edits.lastOrNull { it.what == "when" || it.what == "day" }
        val fresh = e?.let { runCatching { LocalDateTime.parse(it.at).toLocalDate() == today }.getOrDefault(false) } == true && e?.old != null
        val was = if (fresh) e?.old?.let { old -> shortWhen(old, page, today, w) }?.takeIf { it.isNotBlank() && it != listOfNotNull(dayText, hourText).joinToString(" ") } else null
        when {
            dayText != null && hourText != null -> { out += Note(dayText, Tone.Quiet, struck = was); out += Note(hourText, Tone.Quiet) }
            else -> out += Note(dayText ?: hourText!!, Tone.Quiet, struck = was)
        }
    }
    if (l.isTodo && l.state == State.Open && l.moves() >= 3 && seen != Seen.Trace) out += Note(w.moveNo(l.moves()), Tone.Attention)
    l.repeat?.let { out += Note(w.repeat(it), Tone.Quiet) }
    l.who.sortedBy { it.dir.ordinal }.forEach { who ->
        // «с Олегом» is not repeated when the words already name him; a promise always shows its direction.
        if (who.dir == Dir.With && named(l.text, who.name)) return@forEach
        out += Note(when (who.dir) { Dir.To -> w.to(who.name); Dir.From -> w.from(who.name); Dir.With -> w.with(who.name) }, Tone.Link)
    }
    if (seen != Seen.Sheet) book.thread(l.threadId)?.let { out += Note(w.threadPrefix + shortTitle(it.title), Tone.Link) }
    l.place?.let { out += Note(it, Tone.Link) }
    if (l.files.isNotEmpty()) out += Note(when {
        l.files.any { it.kind == "photo" || it.kind == "video" } -> w.photo
        l.files.any { it.kind == "audio" } -> w.voice
        else -> w.file
    }, Tone.Quiet)
    if (l.kind == Kind.Event && l.source == "calendar") out += Note(w.calendar, Tone.Quiet)
    return out
}

/** The words name the person already («позвонить Олегу» — Олег): the stem is enough. */
private fun named(text: String, name: String): Boolean {
    val stem = name.lowercase().replace('ё', 'е').take(4)
    return stem.length >= 3 && Regex("\\p{L}+").findAll(text.lowercase().replace('ё', 'е')).any { it.value.startsWith(stem) }
}

/** A thread's name short enough for the margin: the word in quotes («Зерно»), else a proper name, else the first word. */
fun shortTitle(title: String): String {
    Regex("«([^»]+)»").find(title)?.let { return it.groupValues[1].take(12) }
    val words = title.split(Regex("\\s+")).filter { it.isNotBlank() }
    words.drop(1).firstOrNull { it.first().isUpperCase() }?.let { return it.trim(',', '.', ':').take(12) }
    return (words.firstOrNull() ?: title).trim(',', '.', ':').take(12)
}

/** An old value of «when» as the margin would have said it: «2026-10-15 15:00» → «чт 15:00». */
private fun shortWhen(v: String, page: LocalDate?, today: LocalDate, w: Words): String {
    val parts = v.split(' ')
    val d = parts.firstOrNull()?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    val t = parts.firstOrNull { it.contains(':') }
    return listOfNotNull(d?.takeIf { it != page }?.let { w.near(it, today) }, t).joinToString(" ")
}

/** The mark a line wears on a page. */
fun markOf(l: Line, trace: Boolean = false): MarkKind = when {
    trace -> MarkKind.Moved
    l.kind == Kind.Event -> MarkKind.Event
    l.kind == Kind.Note -> MarkKind.Note
    l.state == State.Done -> MarkKind.Done
    l.state == State.Dropped -> MarkKind.Dropped
    else -> MarkKind.Open
}
