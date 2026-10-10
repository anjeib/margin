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
 * The side margin keeps «when» (a tail, a day, an hour, a window); the rest is written under the words ([Note.under]).
 */

enum class Seen { Page, Tail, Trace, Sheet }

fun notesOf(l: Line, page: LocalDate?, today: LocalDate, book: Book, w: Words, seen: Seen = Seen.Page,
            /** On a thread's own page its name is not repeated; on a person's page — their own arrow. */
            hideThread: String? = null, hidePerson: String? = null): List<Note> {
    val out = ArrayList<Note>()
    l.ask.firstOrNull()?.let { if (l.state == State.Open) out += Note(w.ask(it), Tone.Attention, under = it != "when") }
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
        // A move is not a correction: its trace stays on the page it left; only a changed «when» shows the old value.
        val e = l.edits.lastOrNull { it.what == "when" || it.what == "day" }?.takeIf { it.what == "when" }
        val fresh = e?.let { runCatching { LocalDateTime.parse(it.at).toLocalDate() == today }.getOrDefault(false) } == true && e?.old != null
        val was = if (fresh) e?.old?.let { old -> shortWhen(old, page, today, w) }?.takeIf { it.isNotBlank() && it != listOfNotNull(dayText, hourText).joinToString(" ") } else null
        when {
            dayText != null && hourText != null -> { out += Note(dayText, Tone.Quiet, struck = was); out += Note(hourText, Tone.Quiet) }
            else -> out += Note(dayText ?: hourText!!, Tone.Quiet, struck = was)
        }
    }
    // A step standing on a page says whose step it is: «↳ Совещание с завучами».
    if (seen != Seen.Sheet) l.parentId?.let { book.line(it) }?.let { p -> out += Note("↳ " + w.short(p.text.lineSequence().first()), Tone.Quiet, under = true) }
    // A list under the line: how far it has gone.
    if (seen != Seen.Sheet) book.steps(l.id)?.let { (done, all) -> out += Note(w.ofDone(done, all), Tone.Quiet, under = true) }
    if (l.isTodo && l.state == State.Open && l.moves() >= 3 && seen != Seen.Trace) out += Note(w.moveNo(l.moves()), Tone.Attention, under = true)
    l.repeat?.let { out += Note(w.repeat(it), Tone.Quiet, under = true) }
    l.who.sortedBy { it.dir.ordinal }.forEach { who ->
        // «с Олегом» is not repeated when the words already name him; a promise always shows its direction.
        if (who.dir == Dir.With && named(l.text, who.name)) return@forEach
        if (hidePerson != null && who.name.equals(hidePerson, true)) return@forEach
        out += Note(when (who.dir) { Dir.To -> w.to(who.name); Dir.From -> w.from(who.name); Dir.With -> w.with(who.name) }, Tone.Link, under = true)
    }
    // Under the words there is room for the thread's whole name — unless the words already say it («Йога: шаг 1»).
    if (seen != Seen.Sheet && l.threadId != hideThread) book.thread(l.threadId)?.takeIf { !l.text.contains(it.title, ignoreCase = true) }
        ?.let { out += Note(w.threadPrefix + it.title, Tone.Link, under = true) }
    l.place?.let { out += Note(it, Tone.Link, under = true) }
    // What is kept with the line — unless the line is that very thing («Фото» needs no «фото» beside it).
    if (l.files.isNotEmpty()) when {
        l.files.any { it.kind == "photo" || it.kind == "video" } -> w.photo
        l.files.any { it.kind == "audio" } -> w.voice
        else -> w.file
    }.takeIf { !it.equals(l.text, ignoreCase = true) }?.let { out += Note(it, Tone.Quiet, under = true) }
    if (l.kind == Kind.Event && l.source == "calendar") out += Note(w.calendar, Tone.Quiet, under = true)
    return out
}

/** The words name the person already («позвонить Олегу» — Олег): the stem is enough. */
private fun named(text: String, name: String): Boolean {
    // «Мама» in «маме», «Олег» in «Олегу»: the name less its ending.
    val n = name.lowercase().replace('ё', 'е')
    val stem = n.take(maxOf(3, n.length - if (n.length > 5) 2 else 1))
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
