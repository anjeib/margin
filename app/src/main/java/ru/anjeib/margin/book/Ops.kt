package ru.anjeib.margin.book

import java.time.LocalDate
import java.time.LocalDateTime

/*
 * What touches and words do to the notebook — plain (Book, args) -> Book, testable without Android.
 * Every change of what Margin knows about a line is kept as an [Edit] — the margin shows it as a correction.
 */

private fun Book.edit(id: String, f: (Line) -> Line): Book = copy(lines = lines.map { if (it.id == id) f(it) else it })

/** New lines (and the people and threads they name). */
fun Book.add(new: List<Line>): Book = copy(lines = lines + new).withPeopleOf(new)

/** Everyone named on the new lines becomes a person (not yet «kept» — the index shows them from the second line on). */
private fun Book.withPeopleOf(new: List<Line>): Book {
    val names = new.flatMap { l -> l.who.map { it.name } }.distinct().filter { n -> persons.none { it.matches(n) } }
    return if (names.isEmpty()) this else copy(persons = persons + names.map { Person(name = it) })
}

/**
 * Done or open again. A repeating to-do, done, leaves its next occurrence behind (a new line on the next page).
 * [id] may be «<line>@<date>» — a repeat's coming occurrence: it is done by moving the line there first.
 */
fun Book.toggle(id: String, today: LocalDate = LocalDate.now()): Book {
    splitOccurrence(id)?.let { (base, d) -> return edit(base) { it.copy(day = d.toString()) }.toggle(base, today) }
    val l = line(id) ?: return this
    if (!l.isTodo) return this
    return if (l.state == State.Open) {
        val done = edit(id) { it.copy(state = State.Done, doneAt = LocalDateTime.now().withSecond(0).withNano(0).toString(), ask = emptyList()) }
        val r = l.repeat
        val anchor = l.date
        if (r == null || anchor == null) done else {
            var next = r.next(anchor)
            while (next.isBefore(today)) next = r.next(next)
            done.copy(lines = done.lines + l.copy(id = newId(), day = next.toString(), state = State.Open, doneAt = null,
                edits = emptyList(), trail = emptyList(), writtenAt = nowStamp(), ask = emptyList()))
        }
    } else edit(id) { it.copy(state = State.Open, doneAt = null) }
}

/** «Не делаем»: closed without doing it; the line stays on its page struck through. */
fun Book.drop(id: String): Book = edit(id) { it.copy(state = State.Dropped, doneAt = nowStamp(), ask = emptyList()) }

/** Back from «не делаем». */
fun Book.reopen(id: String): Book = edit(id) { it.copy(state = State.Open, doneAt = null) }

/**
 * To another page ([to] = null — «потом»). The old page keeps a trace «› → пт»; the margin counts the moves
 * («3-й перенос» — a hint that it may belong to «потом»).
 */
fun Book.move(id: String, to: LocalDate?, today: LocalDate = LocalDate.now()): Book {
    val l = line(id) ?: return this
    val from = (l.lastDay()?.takeIf { it.isBefore(today) } ?: l.date ?: today).toString()
    return edit(id) {
        it.copy(day = to?.toString(), until = null, ask = it.ask - "when",
            trail = it.trail + Move(from = from, to = to?.toString()),
            edits = it.edits + Edit("day", it.day, to?.toString()))
    }
}

/** The day and the hour, set on the margin; the old value stays as a correction. */
fun Book.setWhen(id: String, day: LocalDate?, time: String?, by: String = "you", today: LocalDate = LocalDate.now()): Book = edit(id) { l ->
    // A day is never cleared here — «потом» is a move ([move] with null); an hour alone lands on the line's day or today.
    val newDay = day?.toString() ?: l.day ?: today.toString()
    val old = listOfNotNull(l.day, l.time).joinToString(" ").ifBlank { null }
    val new = listOfNotNull(newDay, time).joinToString(" ")
    if (old == new) l else l.copy(day = newDay, time = time, until = null, part = if (time != null) null else l.part,
        ask = l.ask - "when", edits = l.edits + Edit("when", old, new, by),
        trail = if (l.day != null && l.day != newDay) l.trail + Move(from = l.day, to = newDay) else l.trail)
}

fun Book.setUntil(id: String, until: LocalDate?): Book = edit(id) { l ->
    l.copy(until = until?.toString(), day = l.day ?: if (until != null) LocalDate.now().toString() else null, ask = l.ask - "when",
        edits = l.edits + Edit("until", l.until, until?.toString()))
}

fun Book.setRepeat(id: String, r: Repeat?): Book = edit(id) { l ->
    l.copy(repeat = r, day = l.day ?: if (r != null) LocalDate.now().toString() else null,
        edits = l.edits + Edit("repeat", l.repeat?.freq, r?.freq))
}

fun Book.setPlace(id: String, place: String?): Book = edit(id) { l ->
    val p = place?.trim()?.ifBlank { null }
    l.copy(place = p, edits = l.edits + Edit("place", l.place, p))
}

/** Who: a promise either way or just together. Setting a promise replaces the other direction. */
fun Book.setWho(id: String, name: String, dir: Dir): Book = edit(id) { l ->
    val n = name.trim()
    val rest = l.who.filterNot { it.name.equals(n, true) || (dir != Dir.With && it.dir != Dir.With) }
    l.copy(who = rest + Who(n, dir), ask = l.ask - "who", edits = l.edits + Edit("who", null, "${dir.name}:$n"))
}.let { b -> b.line(id)?.let { b.withPeopleOf(listOf(it)) } ?: b }

fun Book.removeWho(id: String, name: String): Book = edit(id) { l -> l.copy(who = l.who.filterNot { it.name.equals(name, true) }) }

/** Into a thread by its title (made if it is new); null — out of any thread. */
fun Book.setThread(id: String, title: String?): Book {
    val t = title?.trim()?.ifBlank { null }
    if (t == null) return edit(id) { it.copy(threadId = null, ask = it.ask - "thread") }
    val existing = threads.find { it.title.equals(t, true) && it.closedAt == null }
    val thread = existing ?: Thread(title = t)
    val b = if (existing == null) copy(threads = threads + thread) else this
    return b.edit(id) { it.copy(threadId = thread.id, ask = it.ask - "thread", edits = it.edits + Edit("thread", null, thread.title)) }
}

/** Your words changed in place: kept as you typed them. */
fun Book.retext(id: String, text: String): Book {
    val t = text.trim()
    if (t.isEmpty()) return this
    return edit(id) { if (it.text == t) it else it.copy(text = t) }
}

/** A continuation under a line: a step (to-do) or a comment (note), written now. */
fun Book.addChild(parentId: String, text: String, kind: Kind): Book {
    val p = line(parentId) ?: return this
    val t = text.trim().takeIf { it.isNotEmpty() } ?: return this
    // An address or a link stays as typed («zoom.us/j/123»); words get their capital.
    val link = Regex("^(?:https?://|www\\.|[\\w-]+\\.[a-z]{2,}(?:/|$))", RegexOption.IGNORE_CASE).containsMatchIn(t)
    return copy(lines = lines + Line(text = if (link) t else t.replaceFirstChar { it.uppercase() }, kind = kind, parentId = p.id, threadId = p.threadId))
}

/** «Сделать нитью»: the line names a new thread and goes into it with everything written under it. */
fun Book.makeThread(id: String, title: String? = null): Book {
    val l = line(id) ?: return this
    val t = Thread(title = (title ?: l.text).trim())
    val ids = setOf(id) + children(id).map { it.id }
    return copy(threads = threads + t, lines = lines.map { if (it.id in ids) it.copy(threadId = t.id) else it })
}

fun Book.closeThread(id: String): Book = copy(threads = threads.map { if (it.id == id) it.copy(closedAt = nowStamp()) else it })
fun Book.reopenThread(id: String): Book = copy(threads = threads.map { if (it.id == id) it.copy(closedAt = null) else it })
fun Book.renameThread(id: String, title: String): Book =
    title.trim().takeIf { it.isNotEmpty() }?.let { t -> copy(threads = threads.map { if (it.id == id) it.copy(title = t) else it }) } ?: this

/** Gone for good, with what was written under it. */
fun Book.delete(id: String): Book {
    val ids = setOf(id) + children(id).map { it.id }
    return copy(lines = lines.filterNot { it.id in ids })
}

/** Files kept with a line (a photo of the receipt, the contract). */
fun Book.addFiles(id: String, files: List<ru.anjeib.margin.data.Attachment>): Book = edit(id) { it.copy(files = it.files + files) }
fun Book.removeFile(id: String, fileId: String): Book = edit(id) { l -> l.copy(files = l.files.filterNot { it.id == fileId }) }

/** A question on the margin answered «no matter»: it leaves the margin. */
fun Book.dismissAsk(id: String, what: String): Book = edit(id) { it.copy(ask = it.ask - what) }

/** «Перелистнуть день»: every open to-do of the day that is not decided yet goes to [to] (usually tomorrow). */
fun Book.carryAll(date: LocalDate, to: LocalDate, today: LocalDate = LocalDate.now()): Book =
    toDecide(date).fold(this) { b, l -> b.move(l.id, to, today) }

/** The day is closed: every open line has its decision. The page keeps the stamp with the hour. */
fun Book.closeDay(date: java.time.LocalDate, at: java.time.LocalTime = java.time.LocalTime.now(), decided: Set<String> = emptySet()): Book {
    val day = date.toString()
    fun on(s: String?) = s?.take(10) == day
    // What came of the day: what was done on it, and what the evening decided about the lines it asked for.
    val done = lines.count { it.isTodo && it.state == State.Done && (on(it.doneAt) || it.id in decided) }
    val dropped = lines.count { it.state == State.Dropped && (on(it.doneAt) || it.id in decided) }
    val moved = lines.count { it.id in decided && it.state == State.Open }
    return copy(closed = closed + (day to DayClose("%02d:%02d".format(at.hour, at.minute), done, moved, dropped)))
}

/**
 * How many times a line has been put off — the margin says «3 переноса» from the third on. A move back (to an earlier
 * day, or back where it was) takes one away: changing your mind is not putting off.
 */
fun Line.moves(): Int = trail.fold(0) { n, m ->
    val to = m.to
    when {
        to == null -> n + 1
        to > m.from -> n + 1
        else -> (n - 1).coerceAtLeast(0)
    }
}
