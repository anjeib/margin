package ru.anjeib.margin.data

import java.time.LocalDate

/*
 * What is left of Margin 0.29's topics: reading an old file. Its notes became «thoughts» (topics without questions) when
 * it was loaded; [AppData.migrated] still does that, so «Поля» imports every old file the same way (book/Import).
 */
object Topics {
    /** The «Входящие» note (files without words) stays a note in both languages; every other note is a thought. */
    private val inboxTitles = setOf("Входящие", "Inbox")
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
}
