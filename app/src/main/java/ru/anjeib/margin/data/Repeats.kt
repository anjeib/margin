package ru.anjeib.margin.data

import ru.anjeib.margin.i18n.L
import ru.anjeib.margin.i18n.Lang
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Next occurrence strictly after [from]. */
fun Repeat.next(from: LocalDate): LocalDate {
    val n = interval.coerceAtLeast(1).toLong()
    return when (freq) {
        "daily" -> from.plusDays(n)
        "weekly" -> if (days.isEmpty()) from.plusWeeks(n) else {
            // Next pinned weekday; after the last one of the week, jump [interval] weeks.
            val later = days.sorted().firstOrNull { it > from.dayOfWeek.value }
            if (later != null) from.plusDays((later - from.dayOfWeek.value).toLong())
            else from.with(java.time.DayOfWeek.MONDAY).plusWeeks(n).plusDays((days.min() - 1).toLong())
        }
        "monthly" -> if (days.isEmpty()) from.plusMonths(n) else {
            // Pinned days of the month («10 и 25 числа»): the next one after [from]; a day past the month's end lands on its last day.
            fun on(ym: java.time.YearMonth, d: Int) = ym.atDay(d.coerceAtMost(ym.lengthOfMonth()))
            val sorted = days.sorted()
            val thisMonth = java.time.YearMonth.from(from)
            sorted.map { on(thisMonth, it) }.firstOrNull { it.isAfter(from) } ?: on(thisMonth.plusMonths(n), sorted.first())
        }
        "yearly" -> from.plusYears(n)
        else -> from.plusDays(n)
    }
}

/** When a repeating task is done, the next one appears: same task, fresh subtasks, new dates. */
fun Task.nextOccurrence(today: LocalDate = LocalDate.now()): Task? {
    val r = repeat ?: return null
    val base = dueDate ?: startDate ?: today
    var nextDue = r.next(base)
    while (nextDue.isBefore(today)) nextDue = r.next(nextDue) // skip missed ones
    val span = if (startDate != null && dueDate != null) ChronoUnit.DAYS.between(startDate, dueDate) else null
    return copy(
        id = newId(),
        due = nextDue.toString(),
        start = span?.let { nextDue.minusDays(it).toString() },
        remindAt = null,
        done = false, doneAt = null, postponed = 0, dropped = false,
        history = listOf(Change(kind = "created", value = "repeat", by = "you")),
        subtasks = subtasks.map { it.withDone(false) },
        entries = emptyList(),
        // The notes stay with the occurrence they were written in (17 окт: copied, the same note stood under two matters
        // with one id — and the diary's list fell over the twice-used key).
        comments = emptyList(), removedComments = emptyList(),
        createdAt = today.toString(),
    ).autoReminded() // «каждый пн в 10» reminds every Monday, not only the first
}

/** A topic's repeating matter, done: the next one, on its next day, with nothing of the old one's notes. */
fun Subtask.nextOccurrence(today: LocalDate = LocalDate.now()): Subtask? {
    val r = repeat ?: return null
    var next = r.next(due?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: today)
    while (next.isBefore(today)) next = r.next(next)
    return copy(id = newId(), due = next.toString(), done = false, doneAt = null, children = emptyList(), slots = emptyList(), comments = emptyList(), note = null)
}

private val dayNamesRu = listOf("пн", "вт", "ср", "чт", "пт", "сб", "вс")
private val dayNamesEn = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

fun Repeat.label(): String {
    val ru = L.lang == Lang.Ru
    val n = interval.coerceAtLeast(1)
    return when (freq) {
        "daily" -> if (n == 1) (if (ru) "каждый день" else "every day") else (if (ru) "раз в $n дн." else "every $n days")
        "weekly" -> {
            val sorted = days.sorted()
            val d = sorted.joinToString(", ") { (if (ru) dayNamesRu else dayNamesEn)[(it - 1).coerceIn(0, 6)] }
            val base = if (n == 1) (if (ru) "каждую неделю" else "weekly") else (if (ru) "раз в $n нед." else "every $n weeks")
            // «каждая пт», «каждый пн», «каждое вс», «каждые пн, чт» (owner's wording), «по будням».
            val every = when {
                !ru -> "every $d"
                sorted == listOf(1, 2, 3, 4, 5) -> "по будням"
                sorted.size > 1 -> "каждые $d"
                else -> when (sorted.single()) { 3, 5, 6 -> "каждая"; 7 -> "каждое"; else -> "каждый" } + " " + d
            }
            if (d.isEmpty()) base else if (n == 1) every else "$base: $d"
        }
        "monthly" -> when {
            days.isNotEmpty() -> if (ru) days.sorted().joinToString(" и ") + " числа" else "on the " + days.sorted().joinToString(", ") { "$it" + when (it % 10) { 1 -> "st"; 2 -> "nd"; 3 -> "rd"; else -> "th" } }
            n == 1 -> if (ru) "каждый месяц" else "monthly"
            else -> if (ru) "раз в $n мес." else "every $n months"
        }
        "yearly" -> if (ru) "каждый год" else "yearly"
        else -> ""
    }
}

/** Where «перенести на день» puts a task: overdue/undated → tomorrow, otherwise one day later. Ranges keep their length. */
fun postponed(t: Task, days: Long, today: LocalDate = LocalDate.now()): Task {
    val due = t.dueDate; val start = t.startDate
    val moved = when {
        due != null -> {
            val newDue = (if (due.isAfter(today)) due else today).plusDays(days)
            val shift = ChronoUnit.DAYS.between(due, newDue)
            t.copy(due = newDue.toString(), start = start?.plusDays(shift)?.toString())
        }
        start != null -> t.copy(start = (if (start.isAfter(today)) start else today).plusDays(days).toString())
        else -> t.copy(due = today.plusDays(days).toString())
    }
    val withReminder = if (moved.time != null) moved.copy(remindAt = "${moved.dueDate ?: moved.startDate}T${moved.time}") else moved.copy(remindAt = null)
    return withReminder.copy(postponed = t.postponed + 1)
}

/**
 * Before 0.77 a repeating matter carried its notes into its next occurrence with the same ids — one note under several matters,
 * which «В архив» could never put away (the next copy took its place). Each note stays with the first matter that has it, in
 * its comments or its archive; the copies after it go. Nothing else is touched; run on every start, it finds nothing twice.
 */
fun AppData.withoutCopiedNotes(): AppData {
    val seen = HashSet<String>()
    var changed = false
    val kept = HashMap<String, Task>()
    for (t in tasks.sortedBy { it.createdAt }) {
        // The archive first: a copy archived by hand in the first matter takes the later copies with it.
        val removed = t.removedComments.filter { seen.add(it.id) }
        val live = t.comments.filter { seen.add(it.id) }
        if (removed.size != t.removedComments.size || live.size != t.comments.size) { changed = true; kept[t.id] = t.copy(comments = live, removedComments = removed) }
    }
    return if (!changed) this else copy(tasks = tasks.map { kept[it.id] ?: it })
}
