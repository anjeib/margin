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

    /** Decodes basic HTML entities and strips tags into clean text (works identically on JVM and Android). */
    fun cleanHtmlText(raw: String): String = raw
        .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("<[^>]+>"), " ")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&apos;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&mdash;", "—")
        .replace("&ndash;", "–")
        .replace("&laquo;", "«")
        .replace("&raquo;", "»")
        .replace(Regex("&#(\\d+);")) { m -> m.groupValues[1].toIntOrNull()?.toChar()?.toString() ?: "" }
        .replace(Regex("&#x([0-9a-fA-F]+);")) { m -> m.groupValues[1].toIntOrNull(16)?.toChar()?.toString() ?: "" }
        .replace(Regex("[ \\t\\x0B\\f\\r]+"), " ")
        .trim()

    /**
     * Extracts readable article paragraphs from HTML for offline reading and full-text search in the Diary.
     */
    fun extractArticle(html: String): String? {
        val stripped = html
            .replace(Regex("<(script|style|nav|header|footer|aside|noscript|svg|form|iframe)\\b[^>]*>.*?</\\1>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), " ")
            .replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), " ")
        val scope = Regex("<(article|main)\\b[^>]*>(.*?)</\\1>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .find(stripped)?.groupValues?.get(2) ?: stripped
        val blocks = Regex("<(p|h2|h3|blockquote|li)\\b[^>]*>(.*?)</\\1>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .findAll(scope)
            .mapNotNull { m ->
                val tag = m.groupValues[1].lowercase()
                val txt = cleanHtmlText(m.groupValues[2]).replace(Regex("\\s+"), " ").trim()
                val minLen = if (tag.startsWith("h")) 12 else 35
                if (txt.length < minLen) null
                else when (tag) {
                    "blockquote" -> "«$txt»"
                    "li" -> "• $txt"
                    else -> txt
                }
            }
            .distinct()
            .toList()
        if (blocks.isEmpty()) return null
        val joined = blocks.joinToString("\n\n").take(14000).trim()
        return joined.takeIf { it.length >= 80 }
    }

    /** Estimated reading time in minutes (at ~180 words per minute), or 0 when too short. */
    fun readingMinutes(text: String?): Int {
        if (text.isNullOrBlank()) return 0
        val words = text.split(Regex("\\s+")).count { it.isNotBlank() }
        return if (words < 35) 0 else ((words + 179) / 180).coerceAtLeast(1)
    }

    /** Category of a diary note for the Fabric-style smart collections bar («Все · Статьи · Снимки · Файлы · Мысли»). */
    fun categoryOf(note: Task): String {
        val atts = note.entries.flatMap { it.attachments }
        return when {
            atts.any { it.kind == "link" } -> "links"
            atts.any { it.kind == "photo" || it.kind == "video" } -> "media"
            atts.any { it.kind == "audio" || it.kind == "file" } -> "files"
            else -> "notes"
        }
    }

    /**
     * Finds active topics related to this diary note by matching topic title words or #tags (purely on-device).
     */
    fun relatedTopics(note: Task, allTasks: List<Task>): List<Task> {
        val text = (note.title + " " + note.entries.joinToString(" ") { it.fullText }).lowercase()
        val noteTags = note.entries.flatMap { it.tags }.map { it.lowercase() }.toSet()
        val words = Regex("[\\p{L}\\p{N}]{4,}").findAll(text).map { it.value }.toSet()
        return allTasks.filter { t ->
            t.topic && !t.thought && !t.done && t.id != note.id && run {
                val titleLower = t.title.lowercase().trim()
                if (titleLower.length >= 3 && (titleLower in noteTags || text.contains(titleLower))) true
                else {
                    val topicWords = Regex("[\\p{L}\\p{N}]{4,}").findAll(titleLower).map { it.value.take(5) }.toList()
                    topicWords.isNotEmpty() && topicWords.all { stem -> words.any { w -> w.startsWith(stem) } }
                }
            }
        }.take(2)
    }
}

/** The address has its own card; don't repeat a bare URL as the note's body. */
val Entry.materialBody: String get() {
    val addresses = attachments.filter { it.kind == "link" }.mapNotNull { it.url }.toSet()
    return text.lineSequence().filterNot { it.trim() in addresses }.joinToString("\n").trim()
}
