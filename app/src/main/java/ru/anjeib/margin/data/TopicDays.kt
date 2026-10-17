package ru.anjeib.margin.data

import java.time.LocalDate

/**
 * Whether a topic stands on the page «Темы» of [day] (owner, 16 окт: topics turn by days too). It stands from the start of
 * its period — or, without one, from the day it was made — and goes with the end of its period or once it is done. A topic
 * with only a deadline stays past it, late, until it is done (a deadline is not the end of the topic).
 */
fun Task.topicOn(day: LocalDate): Boolean {
    val made = runCatching { LocalDate.parse(createdAt.take(10)) }.getOrNull()
    val from = startDate ?: made
    if (from != null && day.isBefore(from)) return false
    val periodEnd = dueDate?.takeIf { startDate != null }
    if (periodEnd != null && day.isAfter(periodEnd)) return false
    if (done) {
        val closed = doneAt?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() } ?: return false
        if (!day.isBefore(closed)) return false
    }
    return true
}
