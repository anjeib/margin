package ru.anjeib.margin.data

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/*
 * What the buttons and gestures do to matters and topics, as plain (AppData, args) -> AppData — testable without Android.
 * MainVm passes them to Store.update (which writes the topic feed lines) and adds the undo, toasts and the rest.
 * «Today» is a parameter only so the tests can fix it; the app always uses the real date.
 */

/** Changes one task and writes what changed into its history ([by]: "you" | "ai"). */
fun AppData.editTask(id: String, by: String = "you", f: (Task) -> Task): AppData =
    copy(tasks = tasks.map { if (it.id == id) f(it).logged(it, by) else it })

/** A timed task reminds at its time unless the user asked for something else. */
fun Task.autoReminded(): Task {
    if (remindAt != null || time == null) return this
    val day = dueDate ?: startDate ?: return this
    return copy(remindAt = "${day}T$time")
}

/** Done or open again — a task, or a topic's matter «q:<topic>:<matter>». A repeating task leaves its next occurrence behind. */
fun AppData.toggleTask(id: String, today: LocalDate = LocalDate.now()): AppData {
    questionRef(id)?.let { (tid, qid) ->
        return copy(tasks = tasks.map { t -> if (t.id != tid) t else t.copy(subtasks = t.subtasks.mapTree(qid) { val x = !it.done; it.withDone(x).copy(doneAt = if (x) today.toString() else null) }) })
    }
    val t = tasks.find { it.id == id } ?: return this
    return if (t.done) copy(tasks = tasks.map { if (it.id == id) it.copy(done = false, doneAt = null, dropped = false).logged(it, "you") else it })
    else copy(tasks = tasks.map { if (it.id == id) it.copy(done = true, doneAt = today.toString()).logged(it, "you") else it } + listOfNotNull(t.nextOccurrence(today)))
}

/** «Не буду делать»: closed without doing it. A repeating one comes back as usual. */
fun AppData.dropTask(id: String, today: LocalDate = LocalDate.now()): AppData {
    val t = tasks.find { it.id == id && !it.done } ?: return this
    return copy(tasks = tasks.map { if (it.id == id) it.copy(done = true, dropped = true, doneAt = today.toString()).logged(it, "you") else it } + listOfNotNull(t.nextOccurrence(today)))
}

/**
 * «Сделать заметкой»: the task's words join its project's ideas note (titled «… [ideas]»), or the note it came from,
 * else become a note of their own; its steps stay with them as a list. Returns the data and the note the words went to.
 */
fun AppData.taskToNote(id: String, ideas: String): Pair<AppData, String>? {
    val t = tasks.find { it.id == id } ?: return null
    val text = (listOf(t.title) + t.entries.map { it.text }.filter { it.isNotBlank() }).joinToString("\n")
    val home = t.project?.let { p -> notes.find { it.project == p && it.title.endsWith(ideas) } } ?: t.noteId?.let { nid -> notes.find { it.id == nid } }
    fun flat(l: List<Subtask>): List<Subtask> = l.flatMap { listOf(it.copy(children = emptyList())) + flat(it.children) }
    val steps = flat(t.subtasks).takeIf { it.isNotEmpty() }?.let { Entry(isList = true, items = it) }
    val rest = tasks.filterNot { it.id == id }
    if (home != null) return copy(tasks = rest, notes = notes.map { if (it.id == home.id) it.copy(entries = it.entries + Entry(text = text) + listOfNotNull(steps), updatedAt = nowStamp()) else it }) to home.id
    val fresh = Note(title = t.title, project = t.project, people = t.people, place = t.place,
        entries = t.entries.filter { it.text.isNotBlank() || it.attachments.isNotEmpty() } + listOfNotNull(steps))
    return copy(tasks = rest, notes = listOf(fresh) + notes) to fresh.id
}

fun AppData.toggleSubtask(taskId: String, subId: String, today: LocalDate = LocalDate.now()) = editTask(taskId) { t ->
    t.copy(subtasks = t.subtasks.mapTree(subId) { val d = !it.done; it.withDone(d).copy(doneAt = if (d) today.toString() else null) })
}

/** The hour of a task, of one of its days («…@<day>») or of a topic's matter («q:…»); without a day it is today. */
fun AppData.setTime(id: String, time: String?, today: LocalDate = LocalDate.now()): AppData {
    slotRef(id)?.let { (base, sid) -> return moveSlot(base, sid, null, time, true) }
    questionRef(id)?.let { (tid, qid) ->
        return editTask(tid) { t -> t.copy(subtasks = t.subtasks.map { if (it.id == qid) it.copy(time = time, due = it.due ?: if (time != null) today.toString() else null) else it }) }
    }
    return editTask(id) { t -> t.copy(time = time, due = t.due ?: if (time != null && t.start == null) today.toString() else t.due, remindAt = null).autoReminded() }
}

fun AppData.setQuestionDue(taskId: String, subId: String, due: String?, time: String? = null) = editTask(taskId) { t ->
    t.copy(subtasks = t.subtasks.mapTree(subId) { it.copy(due = due, time = if (due == null) null else time ?: it.time) })
}

/** «Решено · в архив»: the topic with its materials leaves the lists; news in it brings it back. */
fun AppData.archiveTopic(id: String, today: LocalDate = LocalDate.now()) =
    copy(tasks = tasks.map { if (it.id == id) it.copy(done = true, doneAt = today.toString()) else it })

fun AppData.reviveTopic(id: String) = copy(tasks = tasks.map { if (it.id == id) it.copy(done = false, doneAt = null) else it })

/** «+ шаг» on a matter: the step becomes its first question, the matter grows into a topic. */
fun AppData.addStep(id: String, text: String): AppData {
    val t = text.trim().takeIf { it.isNotEmpty() } ?: return this
    return editTask(id) { m ->
        // The first step takes the matter's day and hour — they were about doing it.
        val first = m.subtasks.isEmpty()
        m.copy(topic = true, subtasks = m.subtasks + Subtask(title = t.sentence(), due = if (first) m.due ?: m.start else null, time = if (first) m.time else null))
    }
}

/** A record at the end of a task's feed (the words after «/готово …:»). */
fun AppData.addRecord(taskId: String, text: String) = editTask(taskId) { it.copy(entries = it.entries + Entry(text = text.sentence())) }

/** «+» on a line of the feed: a new matter under it, shown indented. */
fun AppData.addChildDeal(topicId: String, parentId: String, text: String): AppData {
    val t = text.trim().takeIf { it.isNotEmpty() } ?: return this
    return editTask(topicId) { it.copy(subtasks = it.subtasks + Subtask(title = t.sentence(), parent = parentId)) }
}

/** «−» on a matter: it leaves the counts and the lists but stays in the feed struck through (with what hangs under it). */
fun AppData.removeDeal(topicId: String, subId: String) = editTask(topicId) { t ->
    fun under(id: String): List<String> = listOf(id) + t.subtasks.filter { it.parent == id }.flatMap { under(it.id) }
    val ids = under(subId).toSet()
    t.copy(subtasks = t.subtasks.filterNot { it.id in ids }, removed = t.removed + t.subtasks.filter { it.id in ids })
}

fun AppData.restoreDeal(topicId: String, subId: String) = editTask(topicId) { t ->
    val back = t.removed.filter { it.id == subId || it.parent == subId }
    t.copy(removed = t.removed - back.toSet(), subtasks = t.subtasks + back)
}

fun AppData.purgeDeal(topicId: String, subId: String) = editTask(topicId) { t -> t.copy(removed = t.removed.filterNot { it.id == subId || it.parent == subId }) }

/** «−» on a record of the feed: struck through, its files kept until removed for good. */
fun AppData.removeRecord(topicId: String, entryId: String) = editTask(topicId) { t ->
    val e = t.entries.find { it.id == entryId } ?: return@editTask t
    t.copy(entries = t.entries - e, removedEntries = t.removedEntries + e)
}

fun AppData.restoreRecord(topicId: String, entryId: String) = editTask(topicId) { t ->
    val e = t.removedEntries.find { it.id == entryId } ?: return@editTask t
    t.copy(removedEntries = t.removedEntries - e, entries = (t.entries + e).sortedBy { it.at })
}

fun AppData.purgeRecord(topicId: String, entryId: String) = editTask(topicId) { t -> t.copy(removedEntries = t.removedEntries.filterNot { it.id == entryId }) }

fun AppData.setSphere(id: String, sphere: String?) = editTask(id) { it.copy(sphere = sphere) }

fun AppData.setTopicLabel(id: String, label: String?): AppData {
    val p = label?.trim()?.removePrefix("#")?.lowercase()?.ifBlank { null }
    return copy(tasks = tasks.map { if (it.id == id) it.copy(project = p) else it }, projects = if (p != null && p !in projects) projects + p else projects)
}

/** A step of a task (under [parent] when given); the task is open again. */
fun AppData.addSubtask(taskId: String, title: String, parent: String? = null): AppData {
    val st = Subtask(title = title.sentence()).takeIf { it.title.isNotEmpty() } ?: return this
    return editTask(taskId) { t ->
        if (parent == null) t.copy(subtasks = t.subtasks + st, done = false)
        else t.copy(subtasks = t.subtasks.mapTree(parent) { it.copy(children = it.children + st, done = false) })
    }
}

/** A blank title removes the step. */
fun AppData.renameSubtask(taskId: String, subId: String, title: String) = editTask(taskId) { t ->
    t.copy(subtasks = t.subtasks.mapTree(subId) { if (title.isBlank()) null else it.copy(title = title.sentence()) })
}

fun AppData.deleteSubtask(taskId: String, subId: String) = editTask(taskId) { t -> t.copy(subtasks = t.subtasks.mapTree(subId) { null }) }

fun AppData.deleteTask(id: String) = copy(tasks = tasks.filterNot { it.id == id })

/** A blank text removes a record without files. */
fun AppData.editTaskEntry(taskId: String, entryId: String, text: String) = editTask(taskId) { t ->
    t.copy(entries = t.entries.mapNotNull { if (it.id != entryId) it else if (text.isBlank() && it.attachments.isEmpty()) null else it.copy(text = text.sentence()) })
}

fun AppData.deleteTaskEntry(taskId: String, entryId: String) = editTask(taskId) { t -> t.copy(entries = t.entries.filterNot { it.id == entryId }) }

/** «Отложить» to a chosen day: a task moves there whole (a range keeps its length); one of its days or a topic's matter — just that. */
fun AppData.postponeTo(id: String, day: LocalDate, today: LocalDate = LocalDate.now()): AppData {
    slotRef(id)?.let { (base, sid) -> return moveSlot(base, sid, day.toString(), null, false) }
    questionRef(id)?.let { (tid, qid) -> return setQuestionDue(tid, qid, day.toString()) }
    val t = tasks.find { it.id == id } ?: return this
    val base = t.dueDate ?: t.startDate ?: today
    val shift = ChronoUnit.DAYS.between(base, day)
    val moved = t.copy(due = (t.dueDate?.plusDays(shift) ?: day).toString(), start = t.startDate?.plusDays(shift)?.toString()?.takeIf { t.dueDate != null },
        remindAt = t.time?.let { "${day}T$it" }, postponed = t.postponed + 1)
    return editTask(id) { moved }
}

fun AppData.overdueCount(today: LocalDate = LocalDate.now()) = tasks.count { !it.done && it.dueDate?.isBefore(today) == true }

/** «Перенести на сегодня»: everything overdue moves to today. */
fun AppData.moveOverdue(today: LocalDate = LocalDate.now()) = copy(tasks = tasks.map { t ->
    if (!t.done && t.dueDate?.isBefore(today) == true)
        t.copy(due = today.toString(), start = null, remindAt = null, postponed = t.postponed + 1).autoReminded().logged(t, "you")
    else t
})

/** «Входящие» → «В тему…»: the block leaves the inbox note [inboxId] and lands in the topic's feed, at its own time. */
fun AppData.inboxToTopic(inboxId: String, entryId: String, topicId: String): AppData {
    val e = notes.find { it.id == inboxId }?.entries?.find { it.id == entryId } ?: return this
    return copy(
        notes = notes.map { if (it.id == inboxId) it.copy(entries = it.entries.filterNot { x -> x.id == entryId }, updatedAt = nowStamp()) else it },
        tasks = tasks.map { if (it.id == topicId) it.copy(entries = (it.entries + e.copy(session = null)).sortedBy { x -> x.at }) else it },
    )
}

/** «#ремонт» → the open topic whose title has a word starting so; «#[id]» — chosen from the window. */
fun AppData.topicFor(token: String): Task? {
    val open = tasks.filter { it.topic && !it.done }
    if (token.startsWith("[")) return open.find { it.id == token.trim('[', ']') }
    val q = token.lowercase().replace('_', ' ').replace('ё', 'е')
    fun words(t: Task) = t.title.lowercase().replace('ё', 'е').split(Regex("[^\\p{L}\\d]+")).filter { it.isNotBlank() }
    return open.firstOrNull { it.title.lowercase().replace('ё', 'е') == q } ?: open.filter { t -> words(t).any { it.startsWith(q) } || t.title.lowercase().replace('ё', 'е').contains(q) }
        .maxByOrNull { t -> maxOf(t.entries.maxOfOrNull { it.at } ?: "", t.createdAt) }
}

/** «/готово [#тема] слова[: что сказать]» → the open matter these words name best (row id to title), or null. */
private val joinWords = setOf("для", "под", "над", "при", "про", "без", "или", "что", "как", "это", "все", "уже", "еще", "она", "они", "оно", "его", "мне",
    "так", "там", "где", "кто", "чем", "нам", "вам", "мой", "моя", "наш", "тот", "эта", "эти", "раз", "the", "and", "for", "with")

fun AppData.matterFor(words: String, topicId: String?): Pair<String, String>? {
    // Short words count («API», «НПД», «кот»), the little ones that join words don't: «подарок для Кати» is not «макет для типографии».
    fun stems(x: String) = x.lowercase().replace('ё', 'е').split(Regex("[^\\p{L}\\d]+")).filter { it.length >= 3 && it !in joinWords }.map { it.take(5) }.toSet()
    val q = stems(words)
    if (q.isEmpty()) return null
    val cands = buildList {
        tasks.filter { it.topic && !it.done && (topicId == null || it.id == topicId) }.forEach { t -> t.subtasks.filter { !it.done }.forEach { add("q:${t.id}:${it.id}" to it.title) } }
        if (topicId == null) tasks.filter { !it.topic && !it.done }.forEach { add(it.id to it.title) }
    }
    return cands.map { it to (stems(it.second) intersect q).size }.filter { it.second > 0 }.maxByOrNull { it.second }?.first
}

/** The comments of a matter or of a topic's matter («q:<topic>:<matter>»), changed by [f]. */
private fun AppData.editComments(ref: String, f: (List<Comment>) -> List<Comment>): AppData {
    questionRef(slotRef(ref)?.first ?: ref)?.let { (tid, qid) ->
        return copy(tasks = tasks.map { t -> if (t.id != tid) t else t.copy(subtasks = t.subtasks.mapTree(qid) { it.copy(comments = f(it.comments)) }) })
    }
    return copy(tasks = tasks.map { if (it.id == ref) it.copy(comments = f(it.comments)) else it })
}

/** A new comment, stamped with when its writing began. */
fun AppData.addComment(ref: String, text: String, at: String = nowStamp()): AppData =
    text.trim().takeIf { it.isNotEmpty() }?.let { v -> editComments(ref) { it + Comment(at = at, text = v) } } ?: this

/** A comment changed in place; emptied — gone. */
fun AppData.editComment(ref: String, id: String, text: String): AppData = editComments(ref) { l ->
    val v = text.trim()
    if (v.isEmpty()) l.filterNot { it.id == id } else l.map { if (it.id == id) it.copy(text = v) else it }
}

