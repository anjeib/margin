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

/**
 * «Похоже, это к теме Зерно»: the open topic these words most likely belong to — by its people (a name counts three)
 * and its words (title, matters, last records). Nothing when no topic stands out clearly.
 */
fun AppData.likelyTopic(words: String, people: Collection<String> = emptyList(), except: Set<String> = emptySet()): Task? {
    fun norm(t: String) = t.lowercase().replace('ё', 'е')
    fun stems(t: String) = Regex("\\p{L}{4,}").findAll(norm(t)).map { it.value.take(5) }.toSet()
    // A capital inside a sentence is a name; the first word of a sentence is just a word.
    val names = people + Regex("(?<=[^.!?\\s] )\\p{Lu}\\p{L}{2,}").findAll(words).map { it.value }
    val who = names.map { norm(it).take(3) }.toSet()
    // A name alone is not enough (Маша is in a trip and in a book): the words must meet too, names apart.
    val said = stems(words).filter { st -> who.none { st.startsWith(it) } }.toSet()
    val scored = tasks.filter { it.topic && !it.done && it.id !in except }.map { t ->
        val theirPeople = (t.people + t.subtasks.mapNotNull { it.from } + listOfNotNull(t.waitingFor, t.promisedTo)).map { norm(it).take(3) }.toSet()
        val theirs = stems(t.title + " " + t.subtasks.joinToString(" ") { it.title } + " " + t.entries.takeLast(5).joinToString(" ") { it.text })
        val common = (said intersect theirs).size
        Triple(t, common, common + 3 * (who intersect theirPeople).size)
    }.filter { it.second > 0 && it.third >= 3 }.sortedByDescending { it.third }
    val best = scored.firstOrNull() ?: return null
    return best.first.takeIf { scored.getOrNull(1)?.third != best.third }
}

/** The last day anything happened in a topic: a record, an event, a matter added or settled. */
fun Task.lastActivity(): LocalDate? = (entries.map { it.at } + events.map { it.at } + subtasks.mapNotNull { it.doneAt } + listOf(createdAt))
    .mapNotNull { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() }.maxOrNull()

/** The week on Sunday, gathered by code: what got done, who owes whom, which topics stood still for a week. */
data class WeekReview(val done: Int, val owedToYou: Int, val youOwe: Int, val still: List<Task>)

fun AppData.weekReview(today: LocalDate): WeekReview {
    val monday = today.minusDays(today.dayOfWeek.value - 1L)
    fun inWeek(d: String?) = d?.take(10)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }?.let { !it.isBefore(monday) && !it.isAfter(today) } == true
    val done = tasks.count { !it.topic && it.done && inWeek(it.doneAt) } + tasks.filter { it.topic }.sumOf { t -> t.subtasks.count { it.done && inWeek(it.doneAt) } }
    val open = tasks.filter { !it.done }
    val owedToYou = open.count { it.waitingFor != null } + open.filter { it.topic }.sumOf { t -> t.subtasks.count { !it.done && it.from != null } }
    val youOwe = open.count { it.promisedTo != null }
    val still = open.filter { it.topic && !it.thought && it.subtasks.any { q -> !q.done } && (it.lastActivity()?.isBefore(today.minusDays(7)) ?: true) }
    return WeekReview(done, owedToYou, youOwe, still)
}

/** Ids of question rows in the lists: «q:<topic>:<question>». */
fun questionRef(id: String): Pair<String, String>? =
    if (id.startsWith("q:")) id.removePrefix("q:").split(':').takeIf { it.size == 2 }?.let { it[0] to it[1] } else null

fun Task.questionRow(q: Subtask) = Task(
    id = "q:$id:${q.id}", title = q.title, due = q.due, time = q.time, waitingFor = q.from, note = q.note,
    people = listOfNotNull(q.assignee), project = project, sphere = sphere, topicId = id, topicTitle = title,
    // The day the matter itself appeared, not the topic — «лежит с 3 окт».
    createdAt = events.firstOrNull { it.ref == q.id && (it.kind == "q" || it.kind == "wait") }?.at?.take(10) ?: createdAt,
)

/** Open matters of topics that have no day yet — they wait at the end of today, with their age. */
fun AppData.undatedTopicMatters(): List<Task> =
    tasks.filter { it.topic && !it.done && !it.thought }.flatMap { t -> t.subtasks.filter { !it.done && it.due == null && !it.isWait && it.parent == null }.map { t.questionRow(it) } }

/**
 * What the day lists show: simple tasks as they are, and for each open topic its dated questions —
 * the next steps, each one linked back to its topic.
 */
fun AppData.listTasks(): List<Task> =
    tasks.filter { !it.topic }.flatMap { it.slotRows() } + tasks.filter { it.topic && !it.done }.flatMap { t ->
        t.subtasks.filter { !it.done && it.due != null }.flatMap { q ->
            val row = t.questionRow(q)
            if (q.slots.isEmpty()) listOf(row) else q.slots.filter { !it.done }.map { s -> row.copy(id = row.id + "@" + s.id, due = s.date, time = s.time, slots = q.slots) }
        }
    }

object Topics {
    /** The «Входящие» note (files without words) stays a note in both languages; every other note is a thought. */
    private val inboxTitles = setOf(ru.anjeib.margin.i18n.Ru.inboxNote, ru.anjeib.margin.i18n.En.inboxNote)
    fun isInbox(n: Note) = n.title in inboxTitles

    /** Notes → thoughts: the same id, so links to «the note it came from» keep working. */
    fun thoughts(d: AppData, base: AppData = d): AppData {
        val moving = d.notes.filter { !isInbox(it) }
        if (moving.isEmpty()) return d
        val back = moving.map { n ->
            val o = base.tasks.find { it.id == n.id }
            val born = n.entries.minOfOrNull { it.at }?.take(10) ?: n.updatedAt.take(10)
            (o ?: Task(id = n.id, title = n.title, topic = true, thought = true, createdAt = born, done = n.archived, doneAt = if (n.archived) LocalDate.now().toString() else null))
                .copy(title = n.title.ifBlank { n.entries.firstOrNull { it.text.isNotBlank() }?.text?.take(60) ?: "" }, entries = n.entries, project = n.project,
                    people = n.people, place = n.place, remindAt = n.remindAt, titleFixed = n.titleFixed, noteId = null)
        }
        val ids = back.map { it.id }.toSet()
        return d.copy(notes = d.notes - moving.toSet(), tasks = d.tasks.filterNot { it.id in ids } + back)
    }

    /** For the AI, which knows notes: open thoughts shown as notes (all but [keep] — the one the user is inside). */
    fun asNotes(d: AppData, keep: String? = null): AppData {
        val th = d.tasks.filter { it.thought && !it.done && it.id != keep && it.subtasks.isEmpty() }
        if (th.isEmpty()) return d
        return d.copy(tasks = d.tasks - th.toSet(), notes = d.notes + th.map { t ->
            Note(id = t.id, title = t.title, entries = t.entries, project = t.project, people = t.people, place = t.place, remindAt = t.remindAt,
                titleFixed = t.titleFixed, updatedAt = t.entries.maxOfOrNull { it.at } ?: (t.createdAt + "T00:00"))
        })
    }

    /** The feed lines for [new] compared with [old]; a task with something new to say about it becomes a topic. */
    fun track(old: AppData, new0: AppData): AppData {
        val new = if (new0.notes.any { !isInbox(it) }) thoughts(new0, old) else new0
        if (old.tasks === new.tasks) return new
        var changed = false
        val tasks = new.tasks.map { n0 ->
            // Days of a matter keep its date on the next open one, whoever changed them.
            val n = n0.syncSlots().let { t -> if (t.subtasks.any { it.slots.isNotEmpty() }) t.copy(subtasks = t.subtasks.map { it.syncSlots() }) else t }
            if (n != n0) changed = true
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
