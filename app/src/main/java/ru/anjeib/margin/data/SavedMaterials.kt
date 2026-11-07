package ru.anjeib.margin.data

import java.net.URI

/** Source identity is derived from the address, never guessed from the sender app. */
object SavedMaterials {
    private val urls = Regex("https?://[^\\s<>\\\"\\u0000-\\u001f]+", RegexOption.IGNORE_CASE)
    fun links(text: String): List<String> = urls.findAll(text).map { it.value.trimEnd('.', ',', ';', '!', '?', ')', ']', '}') }
        .filter { site(it) != null }.distinct().take(8).toList()
    fun site(url: String): String? = runCatching {
        val u = URI(url)
        if (u.scheme.lowercase() !in setOf("http", "https") || u.rawUserInfo != null) null
        else u.host?.lowercase()?.removePrefix("www.")?.takeIf { it.isNotBlank() }
    }.getOrNull()
    fun tags(text: String): List<String> = text.split(',', '\n').map { it.trim().removePrefix("#").take(80) }
        .filter { it.isNotBlank() }.distinctBy { it.lowercase() }.take(30)

    fun prepare(e: Entry): Entry {
        val existing = e.attachments.mapNotNull { it.url }.toSet()
        val links = links(e.text + "\n" + e.attachments.mapNotNull { it.ocr }.joinToString("\n"))
            .filter { it !in existing }.map { Attachment(kind = "link", url = it, name = site(it).orEmpty(), siteName = site(it)) }
        val all = e.attachments + links
        return e.copy(attachments = all, tags = (e.tags + all.mapNotNull { it.url?.let(::site) }).distinct())
    }
    fun title(e: Entry): String = e.attachments.firstOrNull { it.kind == "link" }?.name
        ?: e.text.lineSequence().firstOrNull { it.isNotBlank() }?.take(90)
        ?: "Сохранённое изображение"

    /** Kept as an ordinary diary note: backup, archive and topic moves use the same record. */
    fun add(d: AppData, e: Entry): AppData = d.copy(tasks = d.tasks + Task(title = title(e), topic = true, thought = true,
        entries = listOf(e), history = listOf(Change(kind = "created", by = "you"))))
}

/** The address has its own card; don't repeat a bare URL as the note's body. */
val Entry.materialBody: String get() {
    val addresses = attachments.filter { it.kind == "link" }.mapNotNull { it.url }.toSet()
    return text.lineSequence().filterNot { it.trim() in addresses }.joinToString("\n").trim()
}
