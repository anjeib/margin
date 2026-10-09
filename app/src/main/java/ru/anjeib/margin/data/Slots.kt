package ru.anjeib.margin.data

import java.time.LocalDate

/*
 * A matter done on several days («тренировка в пн и чт», «звонить маме 12 и 15»): one task, its days as [Slot]s.
 * Each day shows as its own row in the day lists («<id>@<slot>») with its own tick; the matter is done when all days are.
 * [Task.due]/[Task.time] (and the same on a topic's matter) always follow the next open day, so sorting, «просрочено»,
 * reminders and the AI keep working on the one date they know.
 */

/** «<row id>@<slot id>» → the row it belongs to and the day. */
fun slotRef(id: String): Pair<String, String>? = id.lastIndexOf('@').takeIf { it > 0 }?.let { id.substring(0, it) to id.substring(it + 1) }

private fun List<Slot>.tidy() = distinctBy { it.date + "|" + it.time }.sortedBy { it.date + (it.time ?: "") }

fun Task.syncSlots(): Task {
    if (slots.isEmpty()) return this
    val s = slots.tidy()
    val n = s.firstOrNull { !it.done } ?: s.last()
    return if (s == slots && due == n.date && time == n.time && start == null) this else copy(slots = s, due = n.date, start = null, time = n.time)
}

fun Subtask.syncSlots(): Subtask {
    if (slots.isEmpty()) return this
    val s = slots.tidy()
    val n = s.firstOrNull { !it.done } ?: s.last()
    return if (s == slots && due == n.date && time == n.time) this else copy(slots = s, due = n.date, time = n.time)
}

/** Ticks one day; all days ticked — the matter is done, one opened again — it is open. */
fun Task.toggleSlot(slotId: String): Task {
    val l = slots.map { if (it.id == slotId) it.copy(done = !it.done) else it }
    val all = l.isNotEmpty() && l.all { it.done }
    return copy(slots = l, done = all, doneAt = if (all) LocalDate.now().toString() else null).syncSlots()
}

fun Subtask.toggleSlot(slotId: String): Subtask {
    val l = slots.map { if (it.id == slotId) it.copy(done = !it.done) else it }
    val all = l.isNotEmpty() && l.all { it.done }
    return copy(slots = l, done = all, doneAt = if (all) LocalDate.now().toString() else null).syncSlots()
}

/** One row per open day (the row keeps all days, to say «день 1 из 2»); without days — the task itself. */
fun Task.slotRows(): List<Task> =
    if (slots.isEmpty() || done) listOf(this) else slots.filter { !it.done }.map { s -> copy(id = "$id@${s.id}", due = s.date, start = null, time = s.time) }

/** Which of its days a row is: (number, of how many). */
fun Task.dayOf(): Pair<Int, Int>? = slotRef(id)?.second?.let { sid -> slots.indexOfFirst { it.id == sid }.takeIf { it >= 0 }?.let { it + 1 to slots.size } }
