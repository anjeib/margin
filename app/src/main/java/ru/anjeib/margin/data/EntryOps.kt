package ru.anjeib.margin.data

/*
 * Records (blocks) of a note or a task and the notes themselves, as plain (AppData, args) -> AppData.
 * Files on disk are not touched here: MainVm deletes them around these calls.
 * A record's owner is «note:<id>» or «task:<id>».
 */

fun AppData.editNote(id: String, f: (Note) -> Note) = copy(notes = notes.map { if (it.id == id) f(it) else it })

fun AppData.entriesOf(owner: String): List<Entry> {
    val id = owner.substringAfter(':')
    return if (owner.startsWith("note:")) notes.find { it.id == id }?.entries.orEmpty() else tasks.find { it.id == id }?.entries.orEmpty()
}

/** Changes the records of [owner]: a note is marked updated, a task writes it into its history. */
fun AppData.editEntries(owner: String, f: (List<Entry>) -> List<Entry>): AppData {
    val id = owner.substringAfter(':')
    return if (owner.startsWith("note:")) editNote(id) { it.copy(entries = f(it.entries), updatedAt = nowStamp()) }
    else editTask(id) { it.copy(entries = f(it.entries)) }
}

/** Puts [e] right after [afterId] (at the end when null or gone). */
fun AppData.insertEntry(owner: String, afterId: String?, e: Entry) = editEntries(owner) { l ->
    val i = l.indexOfFirst { it.id == afterId }
    if (i < 0) l + e else l.take(i + 1) + e + l.drop(i + 1)
}

fun AppData.moveEntry(owner: String, id: String, delta: Int) = editEntries(owner) { l ->
    val i = l.indexOfFirst { it.id == id }
    val j = i + delta
    if (i < 0 || j !in l.indices) l else l.toMutableList().apply { add(j, removeAt(i)) }
}

fun AppData.removeEntry(owner: String, id: String) = editEntries(owner) { l -> l.filterNot { it.id == id } }

/** A blank text removes a block that has nothing else (no files, not a list). */
fun AppData.editEntryText(owner: String, id: String, text: String) = editEntries(owner) { l ->
    l.mapNotNull { if (it.id != id) it else if (text.isBlank() && it.attachments.isEmpty() && !it.isList) null else it.copy(text = text.sentence()) }
}

fun AppData.editAttachment(owner: String, entryId: String, attId: String, f: (Attachment) -> Attachment) = editEntries(owner) { l ->
    l.map { e -> if (e.id != entryId) e else e.copy(attachments = e.attachments.map { if (it.id == attId) f(it) else it }) }
}

/** One photo or file out of its block; a block left with nothing in it goes too. The file itself is the caller's business. */
fun AppData.dropAttachment(owner: String, entryId: String, attId: String) = editEntries(owner) { l ->
    l.mapNotNull { e ->
        if (e.id != entryId) e
        else e.copy(attachments = e.attachments.filterNot { it.id == attId }).takeIf { it.text.isNotBlank() || it.attachments.isNotEmpty() || it.items.isNotEmpty() }
    }
}

fun AppData.setCaption(owner: String, entryId: String, attId: String, text: String) =
    editAttachment(owner, entryId, attId) { it.copy(caption = text.trim().ifBlank { null }?.sentence()) }

/** An address found in a block's text, with its page title, once. */
fun AppData.addLink(owner: String, entryId: String, url: String, title: String) = editEntries(owner) { l ->
    l.map { x -> if (x.id == entryId && x.attachments.none { it.url == url }) x.copy(attachments = x.attachments + Attachment(kind = "link", name = title, url = url)) else x }
}

/** [items] as said (already split); blank ones are dropped. */
private fun itemsOf(items: List<String>) = items.filter { it.isNotBlank() }.map { Subtask(title = it.sentence()) }

fun AppData.addListItems(owner: String, entryId: String, items: List<String>) = editEntries(owner) { l ->
    val add = itemsOf(items)
    l.map { if (it.id == entryId) it.copy(items = it.items + add) else it }
}

fun AppData.toggleItem(owner: String, entryId: String, itemId: String) = editEntries(owner) { l ->
    l.map { e -> if (e.id != entryId) e else e.copy(items = e.items.map { if (it.id == itemId) it.copy(done = !it.done) else it }) }
}

// ---------------- notes ----------------

/** «Список»: a new list block at the end of the note; [items] — what was already said («молоко, хлеб»). */
fun AppData.addNoteList(id: String, items: List<String> = emptyList()) = editNote(id) { n ->
    n.copy(entries = n.entries + Entry(isList = true, items = items.map { Subtask(title = it.sentence()) }), updatedAt = nowStamp())
}

/** Items go to the last list of the note (a new one if there is none). */
fun AppData.addNoteItems(id: String, items: List<String>) = editNote(id) { n ->
    val add = itemsOf(items)
    val last = n.entries.lastOrNull()
    if (last?.isList == true) n.copy(entries = n.entries.dropLast(1) + last.copy(items = last.items + add), updatedAt = nowStamp())
    else n.copy(entries = n.entries + Entry(isList = true, items = add), updatedAt = nowStamp())
}

fun AppData.toggleNoteItem(noteId: String, entryId: String, itemId: String) = editNote(noteId) { n ->
    n.copy(entries = n.entries.map { e -> if (e.id != entryId) e else e.copy(items = e.items.map { if (it.id == itemId) it.copy(done = !it.done) else it }) })
}

fun AppData.renameNote(id: String, title: String) = editNote(id) { val x = title.sentence(); if (it.title == x) it else it.copy(title = x) }

fun AppData.setNoteProject(id: String, project: String?): AppData {
    val p = project?.trim()?.removePrefix("#")?.lowercase()?.ifBlank { null }
    return copy(notes = notes.map { if (it.id == id) it.copy(project = p) else it },
        projects = projects + listOfNotNull(p?.takeIf { x -> projects.none { it == x } }))
}

/** A dated record at the end of a note; the note moves to the top of the list. */
fun AppData.appendToNote(id: String, e: Entry): AppData {
    val n = notes.find { it.id == id } ?: return this
    val upd = n.copy(entries = n.entries + e, updatedAt = nowStamp())
    return copy(notes = listOf(upd) + notes.filterNot { it.id == id })
}

fun AppData.editNoteEntry(noteId: String, entryId: String, text: String) = editNote(noteId) { n ->
    n.copy(entries = n.entries.mapNotNull { if (it.id != entryId) it else if (text.isBlank() && it.attachments.isEmpty()) null else it.copy(text = text.trim()) })
}

fun AppData.deleteNoteEntry(noteId: String, entryId: String) = editNote(noteId) { n -> n.copy(entries = n.entries.filterNot { it.id == entryId }) }

fun AppData.archiveNote(id: String, on: Boolean = true) = copy(notes = notes.map { if (it.id == id) it.copy(archived = on) else it })

/** The note goes; tasks that grew out of it forget where from. */
fun AppData.removeNote(id: String) = copy(notes = notes.filterNot { it.id == id }, tasks = tasks.map { if (it.noteId == id) it.copy(noteId = null) else it })

/** A note left completely empty (e.g. "new note" opened and closed). */
fun AppData.dropEmptyNote(id: String) = copy(notes = notes.filterNot { it.id == id && it.title.isBlank() && it.entries.isEmpty() })

fun AppData.dropQueued(id: String) = copy(queue = queue.filterNot { it.id == id })

/** Text recognised on a photo (or corrected by the user), wherever that photo lives — a matter, a topic, a note. */
fun AppData.setOcr(attachmentId: String, text: String?): AppData {
    val v = text?.trim()?.ifBlank { null }
    fun List<Entry>.fix() = map { e -> if (e.attachments.none { it.id == attachmentId }) e else e.copy(attachments = e.attachments.map { if (it.id == attachmentId) it.copy(ocr = v) else it }) }
    return copy(tasks = tasks.map { t -> if (t.entries.none { e -> e.attachments.any { it.id == attachmentId } }) t else t.copy(entries = t.entries.fix()) },
        notes = notes.map { n -> if (n.entries.none { e -> e.attachments.any { it.id == attachmentId } }) n else n.copy(entries = n.entries.fix()) })
}

/** The attachment as it is now in the data (its recognised text may have arrived after it was opened). */
fun AppData.attachment(id: String): Attachment? =
    (tasks.asSequence().flatMap { it.entries } + notes.asSequence().flatMap { it.entries }).flatMap { it.attachments }.firstOrNull { it.id == id }

