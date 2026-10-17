package ru.anjeib.margin

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.anjeib.margin.data.Task
import ru.anjeib.margin.data.topicOn
import java.time.LocalDate

class TopicDaysTest {
    private val d = LocalDate.of(2026, 10, 16)
    private fun topic(created: String = "2026-10-10", start: String? = null, due: String? = null, done: Boolean = false, doneAt: String? = null) =
        Task(id = "t", title = "Тема", topic = true, createdAt = created, start = start, due = due, done = done, doneAt = doneAt)

    @Test fun fromTheDayItWasMade() {
        val t = topic(created = "2026-10-12")
        assertFalse(t.topicOn(LocalDate.of(2026, 10, 11)))
        assertTrue(t.topicOn(LocalDate.of(2026, 10, 12)))
        assertTrue(t.topicOn(LocalDate.of(2026, 12, 1)))
    }

    @Test fun aPeriodBoundsIt() {
        val t = topic(created = "2026-10-01", start = "2026-10-14", due = "2026-10-20")
        assertFalse(t.topicOn(LocalDate.of(2026, 10, 13)))
        assertTrue(t.topicOn(LocalDate.of(2026, 10, 14)))
        assertTrue(t.topicOn(LocalDate.of(2026, 10, 20)))
        assertFalse(t.topicOn(LocalDate.of(2026, 10, 21)))
    }

    @Test fun aDeadlineAloneStaysLate() {
        val t = topic(created = "2026-10-01", due = "2026-10-05")
        assertTrue(t.topicOn(d))
    }

    @Test fun goneOnceDone() {
        val t = topic(created = "2026-10-01", done = true, doneAt = "2026-10-15T18:00")
        assertTrue(t.topicOn(LocalDate.of(2026, 10, 14)))
        assertFalse(t.topicOn(LocalDate.of(2026, 10, 15)))
        assertFalse(t.topicOn(d))
    }
}
