package ru.anjeib.margin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import ru.anjeib.margin.data.AppData
import ru.anjeib.margin.data.Comment
import ru.anjeib.margin.data.Task
import ru.anjeib.margin.data.withoutCopiedNotes

/** A note copied into later occurrences of a repeating matter (before 0.77) stays only with the first. */
class CopiedNotesTest {
    private val note = Comment(id = "n", at = "2026-09-26T09:00", text = "Каждый понедельник…")

    @Test fun copiesGo() {
        val d = AppData(tasks = listOf(
            Task(id = "a", title = "Занятие", createdAt = "2026-09-26", comments = listOf(note)),
            Task(id = "b", title = "Занятие", createdAt = "2026-10-05", comments = listOf(note)),
            Task(id = "c", title = "Занятие", createdAt = "2026-10-12", comments = listOf(note, Comment(id = "own", text = "своя"))),
        ))
        val now = d.withoutCopiedNotes()
        assertEquals(listOf("n"), now.tasks.first { it.id == "a" }.comments.map { it.id })
        assertEquals(emptyList<String>(), now.tasks.first { it.id == "b" }.comments.map { it.id })
        assertEquals(listOf("own"), now.tasks.first { it.id == "c" }.comments.map { it.id })
        assertSame(now, now.withoutCopiedNotes())
    }

    @Test fun archivedFirstTakesTheCopies() {
        val d = AppData(tasks = listOf(
            Task(id = "a", title = "Занятие", createdAt = "2026-09-26", removedComments = listOf(note)),
            Task(id = "b", title = "Занятие", createdAt = "2026-10-05", comments = listOf(note)),
        ))
        val now = d.withoutCopiedNotes()
        assertEquals(emptyList<String>(), now.tasks.first { it.id == "b" }.comments.map { it.id })
        assertEquals(listOf("n"), now.tasks.first { it.id == "a" }.removedComments.map { it.id })
    }
}
