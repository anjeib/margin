package ru.anjeib.margin.book

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** What touches do to the notebook: done, repeats, moves, corrections — over the designer's examples. */
class BookOpsTest {
    private val today = LocalDate.of(2026, 10, 10) // Saturday
    private val book = Examples.build(today)

    @Test fun aRepeatDoneLeavesItsNextLine() {
        val gym = book.line("ex-gym")!!
        val b = book.toggle("ex-gym", today)
        assertEquals(State.Done, b.line("ex-gym")!!.state)
        val next = b.lines.single { it.text == gym.text && it.open }
        assertEquals(gym.repeat!!.next(gym.date!!).toString(), next.day)
    }

    @Test fun aComingOccurrenceIsDoneOnItsOwnPage() {
        val gym = book.line("ex-gym")!!
        val d = gym.repeat!!.next(gym.date!!)
        val b = book.toggle("ex-gym@$d", today)
        assertEquals(d.toString(), b.line("ex-gym")!!.day)
        assertEquals(State.Done, b.line("ex-gym")!!.state)
    }

    @Test fun aMoveLeavesATraceAndCountsOnlyPuttingOff() {
        val b1 = book.move("ex-lamp", today.plusDays(1), today)
        assertEquals(today.plusDays(1).toString(), b1.line("ex-lamp")!!.day)
        assertTrue(b1.page(today, today).traces.any { it.id == "ex-lamp" })
        assertEquals(1, b1.line("ex-lamp")!!.moves())
        // Back where it was: not a put-off any more.
        val b2 = b1.move("ex-lamp", today, today)
        assertEquals(0, b2.line("ex-lamp")!!.moves())
        // «Потом» has no day.
        assertNull(book.move("ex-lamp", null, today).line("ex-lamp")!!.day)
    }

    @Test fun notDoingAndBack() {
        val b = book.drop("ex-milk")
        assertEquals(State.Dropped, b.line("ex-milk")!!.state)
        assertEquals(State.Open, b.reopen("ex-milk").line("ex-milk")!!.state)
    }

    @Test fun aChangedWhenShowsWhatItWas() {
        val b = book.setWhen("ex-milk", today, "20:00", today = today)
        val l = b.line("ex-milk")!!
        assertEquals("20:00", l.time)
        val e = l.edits.last()
        assertEquals("when", e.what)
        assertEquals("$today 19:30", e.old)
    }

    @Test fun aPromiseNamesAPerson() {
        val b = book.setWho("ex-lamp", "Петров", Dir.From)
        assertEquals(Who("Петров", Dir.From), b.line("ex-lamp")!!.who.single())
        assertNotNull(b.person("Петров"))
    }

    @Test fun aThreadIsMadeByItsTitle() {
        val b = book.setThread("ex-lamp", "Ремонт")
        val t = b.threads.single { it.title == "Ремонт" }
        assertEquals(t.id, b.line("ex-lamp")!!.threadId)
        // The same title again goes into the same thread.
        assertEquals(t.id, b.setThread("ex-milk", "ремонт").line("ex-milk")!!.threadId)
    }

    @Test fun deletingTakesWhatWasWrittenUnder() {
        val b = book.addChild("ex-lamp", "посмотреть в Леруа", Kind.Note)
        assertEquals(1, b.children("ex-lamp").size)
        assertTrue(b.delete("ex-lamp").lines.none { it.parentId == "ex-lamp" || it.id == "ex-lamp" })
    }

    @Test fun aRepeatIsLetGoOnlyThisTime() {
        val gym = book.line("ex-gym")!!
        val d = gym.date!!
        // Its own day: this one is dropped, the next one comes.
        val b = book.drop("ex-gym")
        assertEquals(State.Dropped, b.line("ex-gym")!!.state)
        assertTrue(b.lines.any { it.text == gym.text && it.open && it.date == gym.repeat!!.next(d) })
        // A coming day: skipped, the series stays.
        val next = gym.repeat!!.next(d)
        val s = book.dropOccurrence("ex-gym", next)
        assertEquals(State.Open, s.line("ex-gym")!!.state)
        assertTrue(s.page(next, today).all.none { it.id.startsWith("ex-gym") })
        assertTrue(s.page(gym.repeat!!.next(next), today).all.any { it.id.startsWith("ex-gym") })
    }

    @Test fun aRepeatWrittenOnItsDayBeginsToday() {
        val thu = LocalDate.of(2026, 10, 15)
        val l = Line(text = "Падел", day = LocalDate.of(2026, 10, 20).toString(), time = "20:00", repeat = Repeat("weekly", 1, listOf(2, 4)))
        assertEquals(thu.toString(), l.beginToday(thu, java.time.LocalTime.of(11, 44)).day)
        assertEquals(l.day, l.beginToday(thu, java.time.LocalTime.of(21, 0)).day)
    }

    @Test fun aNoteWithOpenStepsIsATail() {
        val b = book.add(listOf(Line(id = "n", text = "Запись про отпуск", kind = Kind.Note, day = today.minusDays(1).toString())))
            .addChild("n", "взять палатку", Kind.Todo)
        assertTrue(b.page(today, today).tails.any { it.id == "n" })
        assertTrue(b.toDecide(today).any { it.id == "n" })
        val done = b.toggle("n", today)
        assertTrue(done.children("n").all { it.state == State.Done })
        assertTrue(done.page(today, today).tails.none { it.id == "n" })
    }
}
