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
        assertEquals("ex-figma", read("отмени подписку Figma")!!.id)
        assertEquals("ex-lamp", read("готово: лампа")!!.id)
        assertEquals("ex-milk", read("молоко и хлеб — готово")!!.id)
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

    @Test fun oneDayOfARepeatIsLetGo() {
        val p = read("отмени спортзал в пн")!!
        assertEquals(Change.What.Drop, p.what)
        assertEquals("ex-gym", p.id)
        assertEquals(today.with(TemporalAdjusters.next(DayOfWeek.MONDAY)), p.day)
    }

    @Test fun theLineTheWordsCoverBestWins() {
        val b = book.add(listOf(
            Line(id = "meet", text = "Родительское собрание 9Б", day = today.plusDays(1).toString(), time = "18:00"),
            Line(id = "remind", text = "Напомнить классным руководителям про родительские собрания", day = today.toString()),
        ))
        val p = Change.read("перенеси собрание на четверг", b, today)!!
        assertEquals("meet", p.id)
        assertTrue(p.sure)
    }

    @Test fun aMoveSaidAfterTheLine() {
        val b = book.add(listOf(Line(id = "dent", text = "Лиза к стоматологу", day = today.plusDays(5).toString(), time = "15:30")))
        val p = Change.read("стоматолог переносится на пятницу в 16", b, today)!!
        assertEquals("dent", p.id)
        assertEquals("16:00", p.time)
        assertEquals(friday, p.day)
    }

    @Test fun twoLinesAlikeAreAskedAbout() {
        val b = book.add(listOf(
            Line(id = "y1", text = "Йога", day = today.toString()),
            Line(id = "y2", text = "Йога", day = today.toString()),
        ))
        assertTrue(!Change.read("перенеси йогу на пятницу", b, today)!!.sure)
    }
}
