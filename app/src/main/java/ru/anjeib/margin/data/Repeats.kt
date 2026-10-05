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
        "monthly" -> from.plusMonths(n)
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
        done = false, doneAt = null, postponed = 0,
        subtasks = subtasks.map { it.withDone(false) },
        entries = emptyList(),
        createdAt = today.toString(),
    )
}

private val dayNamesRu = listOf("пн", "вт", "ср", "чт", "пт", "сб", "вс")
private val dayNamesEn = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

fun Repeat.label(): String {
    val ru = L.lang == Lang.Ru
    val n = interval.coerceAtLeast(1)
    return when (freq) {
        "daily" -> if (n == 1) (if (ru) "каждый день" else "every day") else (if (ru) "раз в $n дн." else "every $n days")
        "weekly" -> {
            val d = days.sorted().joinToString(", ") { (if (ru) dayNamesRu else dayNamesEn)[(it - 1).coerceIn(0, 6)] }
            val base = if (n == 1) (if (ru) "каждую неделю" else "weekly") else (if (ru) "раз в $n нед." else "every $n weeks")
            if (d.isEmpty()) base else if (n == 1) (if (ru) "по $d" else "every $d") else "$base: $d"
        }
        "monthly" -> if (n == 1) (if (ru) "каждый месяц" else "monthly") else (if (ru) "раз в $n мес." else "every $n months")
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
