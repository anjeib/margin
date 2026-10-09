package ru.anjeib.margin.book

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** English phrases read on the phone, into the same lines as Russian ones. */
class EnglishTest {
    private val today = LocalDate.of(2026, 10, 10) // Saturday
    private val book = Examples.build(today, ru = false)
    private fun read(t: String) = Read.draft(t, book, today, today, now = null)
    private fun next(d: DayOfWeek) = today.with(TemporalAdjusters.next(d))

    @Test fun dayAndHourGoToTheMargin() {
        val l = read("call the bank tomorrow at 9").lines.single()
        assertEquals("Call the bank", l.text)
        assertEquals(today.plusDays(1).toString(), l.day)
        assertEquals("09:00", l.time)
        assertEquals("15:30", read("dentist on Friday at 3:30 pm").lines.single().time)
        assertEquals(next(DayOfWeek.FRIDAY).toString(), read("dentist on Friday at 3:30 pm").lines.single().day)
    }

    @Test fun aDeadlineIsAWindow() {
        val l = read("finish the slides by Friday").lines.single()
        assertEquals(today.toString(), l.day)
        assertEquals(next(DayOfWeek.FRIDAY).toString(), l.until)
    }

    @Test fun repeatsLaterAndMeetings() {
        assertEquals(listOf(1, 4), read("pool every Monday and Thursday at 8").lines.single().repeat!!.days)
        assertNull(read("later: sort the holiday photos").lines.single().day)
        assertEquals(Kind.Event, read("meeting with Oliver tomorrow at 11").lines.single().kind)
    }

    @Test fun promisesEitherWay() {
        assertTrue(read("send the deck to Alex by Monday").lines.single().who.any { it.name == "Alex" && it.dir == Dir.To })
        assertTrue(read("Mary will send the tickets on Tuesday").lines.single().who.any { it.name == "Mary" && it.dir == Dir.From })
    }

    @Test fun questionsAreAnswered() {
        assertTrue(read("what did I promise Alex?").question)
        val a = Ask.answer("what did I promise Alex?", book, today)
        assertEquals(Ask.About.Promised, a.about)
        assertEquals(listOf("Banner source files"), a.lines.map { it.text })
        assertEquals(Ask.About.Day, Ask.answer("what's tomorrow?", book, today).about)
    }
}
