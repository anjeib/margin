package ru.anjeib.margin.data

import kotlinx.serialization.Serializable

/** A separate draft lets Back/Cancel leave the saved note and its files intact. */
@Serializable
data class DiaryCardDraft(
    val taskId: String,
    val title: String,
    val tags: String,
    val entries: List<Entry>,
    val baseTitle: String,
    val baseEntries: List<Entry>,
) {
    companion object {
        fun of(t: Task) = DiaryCardDraft(t.id, t.title, t.entries.flatMap { it.tags }.distinct().joinToString(", "),
            t.entries.ifEmpty { listOf(Entry()) }, t.title, t.entries)
    }
    val valid: Boolean get() = entries.flatMap { it.attachments }.filter { it.kind == "link" }.all { SavedMaterials.site(it.url.orEmpty()) != null }
}

/** Keep previews/transcripts that finished while the editor was open, and records added elsewhere. */
fun AppData.saveDiaryCard(draft: DiaryCardDraft): AppData {
    if (!draft.valid) return this
    return copy(tasks = tasks.map { t ->
        if (t.id != draft.taskId || !t.thought) t else {
            val base = draft.baseEntries.associateBy { it.id }
            val edits = draft.entries.associateBy { it.id }
            val oldSites = draft.baseEntries.flatMap { it.attachments }.mapNotNull { it.url?.let(SavedMaterials::site) }.toSet()
            val newSites = draft.entries.flatMap { it.attachments }.mapNotNull { it.url?.let(SavedMaterials::site) }.toSet()
            val tags = (SavedMaterials.tags(draft.tags).filterNot { it in oldSites && it !in newSites } + (newSites - oldSites)).distinct()
            val tagsChanged = tags != draft.baseEntries.flatMap { it.tags }.distinct()
            val merged = t.entries.map { current ->
                val edit = edits[current.id] ?: return@map current
                val before = base[current.id] ?: return@map current
                current.copy(
                    text = if (edit.text != before.text) edit.text.trim() else current.text,
                    description = if (edit.description != before.description) edit.description.trim() else current.description,
                    attachments = edit.attachments.mapNotNull { a ->
                        val previous = before.attachments.find { it.id == a.id }
                        when {
                            a == previous -> current.attachments.find { it.id == a.id }
                            a.kind == "link" && a.url != previous?.url -> a.copy(id = newId(), file = null, previewLoaded = false)
                            a.kind == "link" && previous != null && a.url == previous.url ->
                                (current.attachments.find { it.id == a.id } ?: a).copy(name = a.name, titleFromUser = a.titleFromUser)
                            else -> a
                        }
                    } + current.attachments.filter { a -> before.attachments.none { it.id == a.id } && edit.attachments.none { it.id == a.id } },
                )
            } + draft.entries.filter { it.id !in base && t.entries.none { old -> old.id == it.id } }
            val withTags = if (!tagsChanged) merged else merged.mapIndexed { i, e -> e.copy(tags = if (i == 0) tags else emptyList()) }
            t.copy(title = if (draft.title != draft.baseTitle) draft.title.trim().ifBlank { "Заметка" } else t.title,
                entries = withTags)
        }
    })
}

/** Changing the URL invalidates the old preview and gives the new request a separate identity. */
fun Entry.changeSource(attachmentId: String, address: String): Entry {
    val old = attachments.find { it.id == attachmentId && it.kind == "link" } ?: return this
    if (old.url == address) return this
    return copy(text = old.url?.takeIf { it.isNotBlank() }?.let { text.replace(it, address) } ?: text,
        attachments = attachments.map { if (it.id != attachmentId) it else Attachment(id = old.id, kind = "link", url = address, name = if (old.titleFromUser) old.name else SavedMaterials.site(address).orEmpty(), siteName = SavedMaterials.site(address), titleFromUser = old.titleFromUser) })
}

fun Entry.removeCardAttachment(id: String): Entry {
    val a = attachments.find { it.id == id } ?: return this
    return copy(attachments = attachments.filterNot { it.id == id },
        text = a.url?.takeIf { a.kind == "link" && it.isNotBlank() }?.let { text.replace(it, "").trim() } ?: text)
}

/** One writing surface: existing descriptions and source addresses are readable in the same text. */
fun DiaryCardDraft.mainText(): String = entries.joinToString("\n\n") { e ->
    val words = listOf(e.text, e.description).filter { it.isNotBlank() }.joinToString("\n\n")
    val sources = e.attachments.filter { it.kind == "link" }.mapNotNull { it.url }.filter { it !in words }
    (listOf(words).filter { it.isNotBlank() } + sources).joinToString("\n")
}.trim()

/** Convert plain writing back to records only when it changed; files and original record dates stay intact. */
fun DiaryCardDraft.withMainText(value: String): DiaryCardDraft {
    if (value == mainText()) return this
    val tagPattern = Regex("(?<!\\S)#([\\p{L}\\p{N}_][\\p{L}\\p{N}_.-]*)")
    val addedTags = tagPattern.findAll(value).map { it.groupValues[1].trimEnd('.') }.toList()
    val body = tagPattern.replace(value, "").trim()
    val addresses = SavedMaterials.links(body)
    val existingLinks = entries.flatMap { it.attachments }.filter { it.kind == "link" }.associateBy { it.url }
    val links = addresses.map { url -> existingLinks[url] ?: Attachment(kind = "link", url = url, name = SavedMaterials.site(url).orEmpty(), siteName = SavedMaterials.site(url)) }
    val first = entries.firstOrNull() ?: Entry()
    val edited = entries.ifEmpty { listOf(first) }.mapIndexed { i, e -> e.copy(text = if (i == 0) body else "", description = "",
        attachments = e.attachments.filterNot { it.kind == "link" } + if (i == 0) links else emptyList()) }
    return copy(entries = edited, tags = (SavedMaterials.tags(tags) + addedTags).distinct().joinToString(", "))
}

/** Inline edits never discard a note or its other rows and attachments. */
fun Entry.replaceNoteLine(index: Int, value: String?): Entry {
    val lines = text.split('\n').toMutableList()
    if (index !in lines.indices) return this
    if (value == null) lines.removeAt(index) else lines[index] = value
    return copy(text = lines.joinToString("\n"))
}
fun numberedNoteLine(value: String): String = if (Regex("^\\s*\\d+[.)]\\s+").containsMatchIn(value)) value else "1. $value"
