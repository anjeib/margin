package ru.anjeib.margin.book

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** Words that change a line already written — found by its words, else nothing changes and the words are a new line. */
class ChangeTest {
    private val today = LocalDate.of(2026, 10, 10) // Saturday
    private val book = Examples.build(today)
    private fun read(t: String) = Change.read(t, book, today)
    private val friday = today.with(TemporalAdjusters.next(DayOfWeek.FRIDAY))

    @Test fun aMoveFindsItsLine() {
        val p = read("перенеси лампу на пятницу")!!
        assertEquals(Change.What.Move, p.what)
        assertEquals("ex-lamp", p.id)
        assertEquals(friday, p.day)
        val t = read("перенеси молоко на завтра в 21")!!
        assertEquals("ex-milk", t.id)
        assertEquals(today.plusDays(1), t.day)
        assertEquals("21:00", t.time)
    }

    @Test fun putOffWithoutADayIsLater() {
        val p = read("отложи барбера")!!
        assertEquals("ex-barber", p.id)
        assertTrue(p.later)
        assertTrue(read("перенеси фото с отпуска на потом")!!.later)
    }

    @Test fun shortWordsFindTheirLinesInAnyCase() {
        val b = book.add(listOf(Line(id = "yoga", text = "Йога: шаг 1", day = today.minusDays(3).toString())))
        assertEquals("yoga", Change.read("перенеси йогу на пятницу в 18", b, today)!!.id)
    }

    @Test fun peopleFindTheirLines() {
        assertEquals("ex-c2", read("перенеси показ Олегу на понедельник")!!.id)
    }

    @Test fun everythingGoesTogether() {
        assertEquals(Change.What.MoveAll, read("перенеси всё на завтра")!!.what)
    }

    @Test fun dropAndDone() {
        assertEquals(Change.Plan(Change.What.Drop, "ex-figma"), read("отмени подписку Figma"))
        assertEquals(Change.Plan(Change.What.Done, "ex-lamp"), read("готово: лампа"))
        assertEquals(Change.Plan(Change.What.Done, "ex-milk"), read("молоко и хлеб — готово"))
    }

    @Test fun otherWordsStayNewLines() {
        // A carry, not a move: no day, or nothing written about it.
        assertNull(read("перенести шкаф на дачу"))
        assertNull(read("перенести шкаф на дачу в субботу"))
        assertNull(read("купить лампу в коридор"))
        assertNull(read("перенести встречу?"))
        // A done or dropped line is not changed again.
        assertNull(read("отмени Blender"))
    }
}
