package ru.anjeib.margin.data

import java.time.LocalDate

/*
 * v2 «Темы»: a task that keeps developing becomes a topic — a feed of what happened, with questions (its subtasks)
 * and materials (its attachments). The feed's own lines («Вопрос: …», «Решено: …», «Ждём от …») are written here,
 * by comparing the data before and after any change, so the AI, the buttons and the commands all leave the same trace.
 */

/** A topic's question that someone else owes («ждём от Игоря»). */
val Subtask.isWait get() = from != null

val Task.openQuestions get() = subtasks.filter { !it.done && !it.isWait }
val Task.openWaits get() = subtasks.filter { !it.done && it.isWait }

/** A topic with nothing left to do or to wait for: the app quietly offers the archive. */
val Task.noNextStep get() = topic && !done && subtasks.isNotEmpty() && subtasks.none { !it.done }

/** All attachments of a topic, newest first, with the record they came in. */
fun Task.materials(): List<Pair<Entry, Attachment>> = entries.flatMap { e -> e.attachments.map { e to it } }.reversed()

/** Ids of question rows in the lists: «q:<topic>:<question>». */
fun questionRef(id: String): Pair<String, String>? =
    if (id.startsWith("q:")) id.removePrefix("q:").split(':').takeIf { it.size == 2 }?.let { it[0] to it[1] } else null

fun Task.questionRow(q: Subtask) = Task(
    id = "q:$id:${q.id}", title = q.title, due = q.due, time = q.time, waitingFor = q.from,
    people = listOfNotNull(q.assignee), project = project, createdAt = createdAt, topicId = id, topicTitle = title,
)

/**
 * What the day lists show: simple tasks as they are, and for each open topic its dated questions —
 * the next steps, each one linked back to its topic.
 */
fun AppData.listTasks(): List<Task> =
    tasks.filter { !it.topic } + tasks.filter { it.topic && !it.done }.flatMap { t -> t.subtasks.filter { !it.done && it.due != null }.map { t.questionRow(it) } }

object Topics {
    /** The feed lines for [new] compared with [old]; a task with something new to say about it becomes a topic. */
    fun track(old: AppData, new: AppData): AppData {
        if (old.tasks === new.tasks) return new
        var changed = false
        val tasks = new.tasks.map { n ->
            val o = old.tasks.find { it.id == n.id }
            val t = if (o == null) born(n) else grown(o, n)
            if (t !== n) changed = true
            t
        }
        return if (changed) new.copy(tasks = tasks) else new
    }

    /** A new task that already has steps is a topic from the start: its title names the matter. */
    private fun born(n: Task): Task {
        if (n.topic || n.subtasks.isEmpty() || n.repeat != null) return n
        val entry = n.entries.lastOrNull()?.id
        return n.copy(topic = true, events = n.events + n.subtasks.map { TopicEvent(kind = if (it.isWait) "wait" else "q", ref = it.id, entry = entry) })
    }

    private fun grown(o: Task, n: Task): Task {
        val newEntries = n.entries.filter { e -> o.entries.none { it.id == e.id } }
        val entry = newEntries.lastOrNull { it.text.isNotBlank() }?.id ?: newEntries.lastOrNull()?.id
        val added = n.subtasks.filter { s -> o.subtasks.none { it.id == s.id } }
        val settled = n.subtasks.filter { s -> s.done && o.subtasks.find { it.id == s.id }?.done == false }
        val grew = newEntries.isNotEmpty() || added.isNotEmpty() || settled.isNotEmpty()
        var t = n
        val evs = mutableListOf<TopicEvent>()
        if (!o.topic && !n.topic && grew && n.repeat == null && !o.done) {
            // The second event: the task becomes a topic. What it said to do becomes its first question,
            // settled at once when the same words said it was done («Созвонились…»).
            val doneNow = n.done
            val first = if (o.subtasks.isEmpty()) Subtask(
                title = o.title, due = o.due ?: o.start, time = o.time, from = o.waitingFor,
                done = doneNow, doneAt = if (doneNow) LocalDate.now().toString() else null,
            ) else null
            val bornAt = o.history.firstOrNull { it.kind == "created" }?.at ?: (o.createdAt + "T00:00")
            t = n.copy(
                topic = true, done = false, doneAt = null, start = null, due = null, time = null, remindAt = null, dayPart = null,
                waitingFor = null, subtasks = listOfNotNull(first) + n.subtasks,
            )
            first?.let {
                evs += TopicEvent(at = bornAt, kind = if (it.isWait) "wait" else "q", ref = it.id)
                evs += TopicEvent(kind = "became", entry = entry)
                if (it.done) evs += TopicEvent(kind = if (it.isWait) "got" else "solved", ref = it.id, entry = entry)
            } ?: run { evs += TopicEvent(kind = "became", entry = entry) }
        }
        if (t.topic) {
            added.forEach { evs += TopicEvent(kind = if (it.isWait) "wait" else "q", ref = it.id, entry = entry) }
            settled.forEach { evs += TopicEvent(kind = if (it.isWait) "got" else "solved", ref = it.id, entry = entry) }
            // News in an archived topic brings it back.
            if (o.topic && o.done && grew) { t = t.copy(done = false, doneAt = null); evs += TopicEvent(kind = "revived") }
            else if (o.topic && o.done && !n.done) evs += TopicEvent(kind = "revived")
            // A question ticked off by hand keeps the day it was settled.
            if (settled.any { it.doneAt == null }) t = t.copy(subtasks = t.subtasks.map { s -> if (settled.any { it.id == s.id } && s.doneAt == null) s.copy(doneAt = LocalDate.now().toString()) else s })
            if (n.subtasks.any { s -> !s.done && s.doneAt != null }) t = t.copy(subtasks = t.subtasks.map { if (!it.done && it.doneAt != null) it.copy(doneAt = null) else it })
        }
        return if (evs.isEmpty() && t === n) n else t.copy(events = t.events + evs)
    }
}
