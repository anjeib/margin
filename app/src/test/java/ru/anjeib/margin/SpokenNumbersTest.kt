package ru.anjeib.margin

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.anjeib.margin.ai.LocalParser
import ru.anjeib.margin.ai.SpokenNumbers
import ru.anjeib.margin.data.AppData
import java.time.LocalDate

/** Dictated times and days in words read like digits (speech benchmark, 12 окт). */
class SpokenNumbersTest {
    private fun n(s: String) = SpokenNumbers.normalize(s)

    @Test fun clockTimes() {
        assertEquals("Завтра в 15 созвон", n("Завтра в пятнадцать созвон"))
        assertEquals("стоматолог в пятницу в 11.", n("стоматолог в пятницу в одиннадцать."))
        assertEquals("Встреча в 14:30", n("Встреча в 14 часов, 30 минут"))
        assertEquals("в 9:30 планёрка", n("в девять тридцать планёрка"))
        assertEquals("к 3 часам дня", n("к трём часам дня"))
        assertEquals("в 23 часа", n("в двадцать три часа"))
        assertEquals("в 7:15 вечера", n("в семь часов пятнадцать минут вечера"))
        assertEquals("в 1 дня", n("в час дня"))
    }

    @Test fun daysOfMonth() {
        assertEquals("20 октября", n("двадцатого октября"))
        assertEquals("25 марта", n("двадцать пятого марта"))
        assertEquals("аренда 1 числа", n("аренда первого числа"))
    }

    @Test fun otherNumbersStay() {
        assertEquals("купить две пачки молока", n("купить две пачки молока"))
        assertEquals("разбить на два этапа", n("разбить на два этапа"))
        assertEquals("пять минут подумать", n("пять минут подумать"))
    }

    @Test fun theParserReadsThem() {
        val r = LocalParser.parse("стоматолог в пятницу в одиннадцать", AppData()).items.single()
        assertEquals("11:00", r.time)
        val m = LocalParser.parse("Встреча с Ириной 20 октября в 14 часов, 30 минут", AppData()).items.single()
        assertEquals("14:30", m.time)
        val today = LocalDate.now()
        val d = LocalParser.parse("отчёт двадцатого октября", AppData(), today = LocalDate.of(2026, 10, 12)).items.single()
        assertEquals(LocalDate.of(2026, 10, 20), d.due)
    }
}
