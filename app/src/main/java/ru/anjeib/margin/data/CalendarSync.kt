package ru.anjeib.margin.data

import java.time.LocalDate

/* The phone's meetings become matters by themselves (owner, 26 окт): «все дела из календаря — делами в Margin». */

/** What a meeting is now — compared with what it was when its matter was made. */
private val CalEvent.sig get() = listOf(day, hour, title.trim().replaceFirstChar { it.uppercaseChar() }, location?.trim() ?: "").joinToString("|")
private val CalEvent.day get() = start.toLocalDate().toString()
private val CalEvent.hour get() = if (allDay) "" else start.toLocalTime().toString().take(5)
private val CalEvent.lastDay: LocalDate get() = if (allDay) end.toLocalDate().minusDays(1) else end.toLocalDate()
private val Task.sig get() = listOf(due ?: "", time ?: "", title, place ?: "").joinToString("|")

/** A matter of the meeting's day, hour and place; a several-day one (a trip, a holiday) — its window. */
fun CalEvent.toTask(): Task {
    val several = allDay && lastDay.isAfter(start.toLocalDate())
    return Task(title = title.trim().replaceFirstChar { it.uppercaseChar() }, start = if (several) day else null,
        due = if (several) lastDay.toString() else day, time = hour.ifEmpty { null },
        place = location?.trim()?.takeIf { it.isNotEmpty() }, calEvent = key, calFrom = from)
}

/**
 * Each meeting becomes a matter once — taken in the archive or deleted, it doesn't come back. A meeting moved or renamed in the calendar
 * moves its matter too, unless the matter was already changed here (the user's word wins) or is done.
 */
fun AppData.withCalendar(events: List<CalEvent>, today: LocalDate): AppData {
    var ts = tasks
    val seen = calSeen.toMutableMap()
    for (e in events) {
        if (e.lastDay.isBefore(today) || e.title.isBlank()) continue
        val was = seen[e.key]
        if (was == null) {
            if (ts.none { it.calEvent == e.key } && archived.none { it.calEvent == e.key }) ts = ts + e.toTask()
        } else if (was != e.sig) {
            val now = e.toTask()
            ts = ts.map { t -> if (t.calEvent == e.key && !t.done && t.sig == was) t.copy(title = now.title, start = now.start, due = now.due, time = now.time, place = now.place) else t }
        }
        seen[e.key] = e.sig
    }
    return if (ts === tasks && seen == calSeen) this else copy(tasks = ts, calSeen = seen)
}

/** Meetings already standing as matters are not shown a second time. */
fun AppData.meetingsLeft(events: List<CalEvent>): List<CalEvent> {
    if (events.isEmpty() || (calSeen.isEmpty() && tasks.none { it.calEvent != null })) return events
    val made = HashSet(calSeen.keys).apply { tasks.mapNotNullTo(this) { it.calEvent } }
    return events.filterNot { it.key in made }
}
