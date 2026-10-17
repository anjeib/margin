package ru.anjeib.margin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.anjeib.margin.data.Comment
import ru.anjeib.margin.data.Repeat
import ru.anjeib.margin.data.Task
import ru.anjeib.margin.data.nextOccurrence
import java.time.LocalDate

/** 0.76 fell over a note copied into the next occurrence of a repeating matter: the notes stay with their own occurrence. */
class RepeatNotesTest {
    @Test fun theNextOccurrenceTakesNoNotes() {
        val t = Task(id = "class", title = "Занятие", due = "2026-10-16", repeat = Repeat("weekly", days = listOf(1, 3, 5)),
            comments = listOf(Comment(id = "n", text = "Час стоит на полях")), removedComments = listOf(Comment(id = "old", text = "в архиве")))
        val next = t.nextOccurrence(LocalDate.of(2026, 10, 16))!!
        assertTrue(next.id != t.id)
        assertEquals(emptyList<Comment>(), next.comments)
        assertEquals(emptyList<Comment>(), next.removedComments)
    }
}
