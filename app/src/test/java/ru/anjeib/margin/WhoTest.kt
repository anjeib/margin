package ru.anjeib.margin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import ru.anjeib.margin.data.*
import java.time.LocalDate
import java.time.LocalDateTime

/** Tester, 26 окт: two different Машас; owner: meetings of the calendar become matters by themselves. */
class WhoTest {
    private val a = Person(id = "a", name = "Маша", kept = true, phone = "+7 900")
    private val b = Person(id = "b", name = "Маша", kept = true, company = "Зерно")

    @Test fun oneMashaIsTiedAtOnce() {
        val d = AppData(tasks = listOf(Task(id = "t", title = "Позвонить", people = listOf("Маша"))), persons = listOf(a)).tieNames()
        assertEquals("a", d.tasks[0].who["маша"])
        assertNull(d.whoQuestion())
    }

    @Test fun twoMashasAreAskedAndTheAnswerHolds() {
        val d0 = AppData(tasks = listOf(Task(id = "t", title = "Позвонить", people = listOf("Маша"))), persons = listOf(a, b)).tieNames()
        val ask = d0.whoQuestion()
        assertNotNull(ask)
        val d = d0.tieName(ask!!.ref, ask.name, "b")
        assertNull(d.whoQuestion())
        assertEquals("b", d.personIn("Маша", d.tasks[0].who)?.id)
        // The other Маша doesn't see the matter as hers.
        assertEquals(false, a.involvedInTie(d.tasks[0]))
        assertEquals(true, b.involvedInTie(d.tasks[0]))
    }

    @Test fun sameNamedContactsSurviveLoading() {
        assertEquals(2, AppData(persons = listOf(a, b)).migrated().persons.size)
        // A mere mention still folds into the contact.
        assertEquals(1, AppData(persons = listOf(a, Person(id = "c", name = "Маша", draft = true))).migrated().persons.size)
    }

    @Test fun meetingsBecomeMattersOnceAndMove() {
        val today = LocalDate.of(2026, 10, 26)
        val e = CalEvent("встреча с Олегом", today.atTime(16, 0), today.atTime(17, 0), false, "Zoom", id = 7, from = "Google Календарь")
        val d1 = AppData().withCalendar(listOf(e), today)
        assertEquals(1, d1.tasks.size)
        assertEquals("16:00", d1.tasks[0].time)
        assertEquals("Google Календарь", d1.tasks[0].calFrom)
        assertEquals(d1, d1.withCalendar(listOf(e), today))
        // Moved in the calendar — the matter follows.
        val d2 = d1.withCalendar(listOf(e.copy(start = today.atTime(17, 0), end = today.atTime(18, 0))), today)
        assertEquals("17:00", d2.tasks[0].time)
        // Deleted here — it doesn't come back.
        assertEquals(0, d2.copy(tasks = emptyList()).withCalendar(listOf(e), today).tasks.size)
        // Changed here — the calendar no longer moves it.
        val mine = d1.copy(tasks = d1.tasks.map { it.copy(time = "12:00") })
        assertEquals("12:00", mine.withCalendar(listOf(e.copy(start = LocalDateTime.of(2026, 10, 26, 18, 0))), today).tasks[0].time)
    }
}

private fun Person.involvedInTie(t: Task) = t.people.any { isIn(it, t.who) }
