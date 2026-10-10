package ru.anjeib.margin.book

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/*
 * What stands on a page, computed in one place. The page itself, the month, the widget and the notifications all ask
 * these functions — so a number can never differ between two screens (DESIGN.md, rule 4).
 */

/** One day: tails first (today only), then by the hour, then the rest as written; and what left the page. */
data class Page(
    val date: LocalDate,
    /** Open to-dos of earlier pages — what asks for a decision first. Only on today. */
    val tails: List<Line>,
    /** Lines with an hour, by the hour. */
    val timed: List<Line>,
    /** Everything else, in the order it was written: to-dos and notes together, as in a notebook. */
    val rest: List<Line>,
    /** Lines moved away from this page: they stay as a trace «› → пт» — the past is not rewritten. */
    val traces: List<Line>,
) {
    val all: List<Line> get() = tails + timed + rest
    val open: List<Line> get() = all.filter { it.isTodo && it.open }
}

/** A repeat's next date strictly after [from]. */
fun Repeat.next(from: LocalDate): LocalDate {
    val n = interval.coerceAtLeast(1).toLong()
    return when (freq) {
        "daily" -> from.plusDays(n)
        "weekly" -> if (days.isEmpty()) from.plusWeeks(n) else {
            val later = days.sorted().firstOrNull { it > from.dayOfWeek.value }
            if (later != null) from.plusDays((later - from.dayOfWeek.value).toLong())
            else from.with(java.time.DayOfWeek.MONDAY).plusWeeks(n).plusDays((days.min() - 1).toLong())
        }
        "monthly" -> from.plusMonths(n)
        "yearly" -> from.plusYears(n)
        else -> from.plusDays(n)
    }
}

/** The dates of a repeat after [anchor] up to [to] — its future pages. */
fun Repeat.between(anchor: LocalDate, to: LocalDate): Sequence<LocalDate> =
    generateSequence(next(anchor)) { next(it) }.takeWhile { !it.isAfter(to) }

/** Where a line stands: its first page and its last (a soft window «до пт» spans several). */
private fun Line.span(): Pair<LocalDate, LocalDate>? {
    val start = date ?: until?.let { written.toLocalDate() } ?: return null
    val end = untilDate ?: start
    return start to maxOf(start, end)
}

/** The page a to-do is due on at the latest: past it, it is a tail. */
fun Line.lastDay(): LocalDate? = span()?.second

/** A repeat's coming occurrence shown on a future page: the same line, its id marked with the day. */
fun Line.onDay(d: LocalDate): Line = copy(id = "$id@$d", day = d.toString(), until = null)

/** «<id>@<date>» → the line and the day of its coming occurrence. */
fun splitOccurrence(id: String): Pair<String, LocalDate>? = id.lastIndexOf('@').takeIf { it > 0 }?.let { i ->
    runCatching { id.substring(0, i) to LocalDate.parse(id.substring(i + 1)) }.getOrNull()
}

/** Lines that belong to [date] (not tails): own day, a soft window over it, or a repeat's coming occurrence. */
fun Book.on(date: LocalDate, today: LocalDate): List<Line> {
    val out = ArrayList<Line>()
    for (l in lines) {
        if (l.parentId != null) continue
        val s = l.span()
        if (s != null && !date.isBefore(s.first) && !date.isAfter(s.second)) {
            // A soft window («до пт») stands where it was written, on its last day, and on today while it is open — not on
            // every day between: a week of «до пт» lines would be a wall of the same words.
            if (l.until == null || date == s.first || date == s.second || date == today) out += l
            continue
        }
        // A repeat shows on its future pages; past ones are its tail.
        val r = l.repeat
        val anchor = l.date
        if (r != null && anchor != null && l.open && date.isAfter(anchor) && date.isAfter(today) &&
            r.between(maxOf(anchor, today), date).any { it == date }) out += l.onDay(date)
    }
    return out
}

/** The page of [date]. */
fun Book.page(date: LocalDate, today: LocalDate = LocalDate.now()): Page {
    val here = on(date, today)
    val tails = if (date == today) lines.filter { l ->
        l.parentId == null && l.isTodo && l.open && (l.lastDay()?.isBefore(today) == true)
    }.sortedBy { it.lastDay() } else emptyList()
    val timed = here.filter { it.time != null }.sortedBy { it.time }
    val rest = here.filter { it.time == null }.sortedBy { it.writtenAt }
    val traces = if (date.isAfter(today)) emptyList() else lines.filter { l ->
        l.parentId == null && l.day != date.toString() && l.trail.any { it.from == date.toString() }
    }
    return Page(date, tails, timed, rest, traces)
}

/**
 * What the evening asks a decision for: open to-dos whose last day is [today] or before. A soft window still open
 * («до ср») rides on by itself; a repeat's coming occurrence is not a line yet.
 */
fun Book.toDecide(day: LocalDate = ritualDay()): List<Line> =
    lines.filter { l -> l.parentId == null && l.isTodo && l.open && l.lastDay()?.let { !it.isAfter(day) } == true }
        .sortedWith(compareBy({ it.lastDay() }, { it.time ?: "99" }, { it.writtenAt }))

/**
 * The day the evening closes. Until four at night it is still yesterday: at 01:50 on Saturday the person is finishing
 * Friday, and «завтра» for them is Saturday.
 */
fun ritualDay(now: java.time.LocalDateTime = java.time.LocalDateTime.now()): LocalDate =
    if (now.hour < 4) now.toLocalDate().minusDays(1) else now.toLocalDate()

/** How many open to-dos the day holds (with tails on today) — the one number for every screen. */
fun Book.openCount(date: LocalDate, today: LocalDate = LocalDate.now()): Int = page(date, today).open.size

/** What the margin says on a page: promises either way, questions, tails. */
data class Summary(val promised: Int, val waited: Int, val questions: Int, val tails: Int) {
    val empty get() = promised == 0 && waited == 0 && questions == 0 && tails == 0
}

fun Book.summary(date: LocalDate, today: LocalDate = LocalDate.now()): Summary {
    val p = page(date, today)
    val open = p.all.filter { it.open }
    return Summary(
        promised = open.count { l -> l.who.any { it.dir == Dir.To } },
        waited = open.count { l -> l.who.any { it.dir == Dir.From } },
        questions = open.count { it.ask.isNotEmpty() },
        tails = p.tails.size,
    )
}

/** A day in the month: open to-dos, events, and whether old to-dos still hang on it. */
data class DayMarks(val open: Int, val events: Int, val tail: Boolean, val closed: Boolean)

fun Book.marks(date: LocalDate, today: LocalDate = LocalDate.now()): DayMarks {
    val p = page(date, today)
    val mine = p.timed + p.rest
    return DayMarks(
        open = if (date.isBefore(today)) 0 else p.open.size,
        events = mine.count { it.kind == Kind.Event },
        tail = date.isBefore(today) && mine.any { it.isTodo && it.open },
        closed = date.isBefore(today) && mine.none { it.isTodo && it.open } && (mine.isNotEmpty() || p.traces.isNotEmpty()),
    )
}

/** «Потом»: to-dos without a day, outside any thread — put off on purpose, not forgotten. */
fun Book.later(): List<Line> = lines.filter { it.parentId == null && it.day == null && it.until == null && it.threadId == null && it.open }
    .sortedBy { it.writtenAt }

/** A thread's lines by day; its lines without a day come last. */
fun Book.threadLines(threadId: String): List<Pair<LocalDate?, List<Line>>> {
    val mine = lines.filter { it.threadId == threadId && it.parentId == null }
    val dated = mine.filter { it.date != null || it.until != null }.groupBy { it.date ?: it.written.toLocalDate() }.toSortedMap()
    return dated.map { (d, l) -> d to l.sortedWith(compareBy({ it.time ?: "99" }, { it.writtenAt })) } +
        listOfNotNull(mine.filter { it.date == null && it.until == null }.takeIf { it.isNotEmpty() }?.let { null to it.sortedBy { l -> l.writtenAt } })
}

/** A thread's quiet line: how many open, waited for, notes, files; since when, and the last day something happened. */
data class ThreadStats(val open: Int, val waiting: Int, val notes: Int, val files: Int, val since: LocalDate, val last: LocalDate)

fun Book.threadStats(threadId: String, today: LocalDate = LocalDate.now()): ThreadStats {
    val mine = lines.filter { it.threadId == threadId }
    val t = thread(threadId)
    val since = t?.createdAt?.let { runCatching { java.time.LocalDateTime.parse(it).toLocalDate() }.getOrNull() } ?: today
    // The last thing that happened — written or done; a day still ahead is not activity.
    val last = mine.flatMap { l -> listOfNotNull(l.written.toLocalDate(), l.doneAt?.let { d -> runCatching { java.time.LocalDateTime.parse(d).toLocalDate() }.getOrNull() }) }
        .filter { !it.isAfter(today) }.maxOrNull() ?: since
    return ThreadStats(
        open = mine.count { it.isTodo && it.open && it.from() == null },
        waiting = mine.count { it.open && it.from() != null },
        notes = mine.count { it.kind == Kind.Note },
        files = mine.sumOf { it.files.size },
        since = since, last = last,
    )
}

/** A person's lines: what you promised them, what they promised you, and the rest. */
data class PersonLines(val promised: List<Line>, val waited: List<Line>, val other: List<Line>)

fun Book.personLines(name: String): PersonLines {
    val p = person(name)
    fun mine(w: Who) = p?.matches(w.name) ?: w.name.equals(name, true)
    val all = lines.filter { l -> l.parentId == null && l.who.any(::mine) }
    val promised = all.filter { l -> l.open && l.who.any { mine(it) && it.dir == Dir.To } }
    val waited = all.filter { l -> l.open && l.who.any { mine(it) && it.dir == Dir.From } }
    return PersonLines(promised, waited, (all - promised.toSet() - waited.toSet()).sortedByDescending { it.writtenAt })
}

/** What a closed day came to: done, not doing, moved on — as its stamp keeps it. */
data class DayStats(val done: Int, val dropped: Int, val moved: Int)

fun Book.dayStats(date: LocalDate): DayStats? = closed[date.toString()]?.let { DayStats(it.done, it.dropped, it.moved) }

/** How many days between two dates — for «3-й перенос» and the age of a tail. */
fun daysBetween(a: LocalDate, b: LocalDate) = ChronoUnit.DAYS.between(a, b)
