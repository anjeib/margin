package ru.anjeib.margin.book

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

/** What stands on a page and what the evening asks for — over the designer's examples (Saturday, 10 Oct 2026). */
class PagesTest {
    private val today = LocalDate.of(2026, 10, 10)
    private val book = Examples.build(today)

    @Test fun todayHasItsTailsFirstThenHoursThenTheRest() {
        val p = book.page(today, today)
        assertEquals(listOf("ex-figma"), p.tails.map { it.id })
        assertEquals(listOf("ex-c2", "ex-call", "ex-milk"), p.timed.map { it.id })
        assertTrue(p.rest.any { it.id == "ex-lamp" })
    }

    @Test fun aRepeatShowsOnItsComingPages() {
        val gym = book.line("ex-gym")!!
        val next = gym.repeat!!.next(gym.date!!)
        assertTrue(book.page(next, today).all.any { it.id == "ex-gym@$next" })
    }

    @Test fun theEveningAsksOnlyWhatIsDueNow() {
        // A soft window still open rides on by itself.
        val withWindow = book.add(listOf(Line(id = "w", text = "Обновить презентацию", day = today.toString(), until = today.plusDays(4).toString())))
        val ids = withWindow.toDecide(today).map { it.id }
        assertTrue("ex-figma" in ids && "ex-lamp" in ids && "ex-milk" in ids)
        assertFalse("w" in ids)
        // «Всё остальное — на завтра» moves exactly those.
        val carried = withWindow.carryAll(today, today.plusDays(1), today)
        assertTrue(carried.toDecide(today).isEmpty())
        assertEquals(today.toString(), carried.line("w")!!.day)
    }

    @Test fun aClosedDayKeepsItsHourAndWhatCameOfIt() {
        val asked = book.toDecide(today).map { it.id }.toSet()
        val b = book.carryAll(today, today.plusDays(1), today).closeDay(today, LocalTime.of(21, 40), asked)
        val c = b.closed[today.toString()]!!
        assertEquals("21:40", c.at)
        assertEquals(1, c.done) // «Сдать макеты лендинга»
        assertEquals(asked.size, c.moved)
    }

    @Test fun atNightTheEveningIsYesterdays() {
        assertEquals(today.minusDays(1), ritualDay(today.atTime(1, 50)))
        assertEquals(today, ritualDay(today.atTime(21, 0)))
    }
}
