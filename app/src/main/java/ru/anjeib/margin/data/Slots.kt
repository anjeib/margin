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

/** Applies [task] / [sub] to the row [base]: a task id or a topic's matter «q:<topic>:<matter>». */
private fun AppData.onSlotOwner(base: String, task: (Task) -> Task, sub: (Subtask) -> Subtask): AppData {
    questionRef(base)?.let { (tid, qid) -> return editTask(tid) { t -> t.copy(subtasks = t.subtasks.map { if (it.id == qid) sub(it) else it }) } }
    return editTask(base, f = task)
}

/** Ticks one day of the row [base] (a task or a topic's matter); all ticked — the matter is done. */
fun AppData.toggleSlot(base: String, slotId: String) = onSlotOwner(base, { it.toggleSlot(slotId) }, { it.toggleSlot(slotId) })

/** «+ день»: one more day for the matter; its single date becomes the first of the days. */
fun AppData.addSlot(taskId: String, date: String): AppData {
    questionRef(taskId)?.let { (tid, qid) ->
        return editDeal(tid, qid) { q -> q.copy(slots = (if (q.slots.isEmpty()) listOfNotNull(q.due?.let { Slot(date = it, time = q.time) }) else q.slots) + Slot(date = date)).syncSlots() }
    }
    return addTaskSlot(taskId, date)
}

private fun AppData.addTaskSlot(taskId: String, date: String) = editTask(taskId) { t ->
    val base = if (t.slots.isEmpty()) listOfNotNull((t.due ?: t.start)?.let { Slot(date = it, time = t.time) }) else t.slots
    t.copy(slots = base + Slot(date = date)).syncSlots()
}

/** One day less; the last one left becomes a plain date again. */
fun AppData.removeSlot(taskId: String, slotId: String): AppData {
    questionRef(taskId)?.let { (tid, qid) ->
        return editDeal(tid, qid) { q ->
            val l = q.slots.filterNot { it.id == slotId }
            if (l.size <= 1) q.copy(slots = emptyList(), due = l.firstOrNull()?.date ?: q.due, time = l.firstOrNull()?.time ?: q.time) else q.copy(slots = l).syncSlots()
        }
    }
    return removeTaskSlot(taskId, slotId)
}

private fun AppData.removeTaskSlot(taskId: String, slotId: String) = editTask(taskId) { t ->
    val l = t.slots.filterNot { it.id == slotId }
    if (l.size <= 1) t.copy(slots = emptyList(), due = l.firstOrNull()?.date ?: t.due, time = l.firstOrNull()?.time ?: t.time) else t.copy(slots = l).syncSlots()
}

/** Moves one day of the row [base] to [date] (null — stays) and, when [timeToo], sets its hour. */
fun AppData.moveSlot(base: String, slotId: String, date: String?, time: String?, timeToo: Boolean): AppData {
    fun f(l: List<Slot>) = l.map { if (it.id != slotId) it else it.copy(date = date ?: it.date, time = if (timeToo) time else it.time) }
    return onSlotOwner(base, { it.copy(slots = f(it.slots)).syncSlots() }, { it.copy(slots = f(it.slots)).syncSlots() })
}
