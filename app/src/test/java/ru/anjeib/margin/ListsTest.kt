package ru.anjeib.margin

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Test
import ru.anjeib.margin.ai.LocalParser
import ru.anjeib.margin.ui.continueList

/** A list goes on by itself from its first marker, wherever Enter is pressed (owner, 13 окт); the word that says what it is. */
class ListsTest {
    /** Types Enter at [at] in [text]; returns the text and where the cursor ends up. */
    private fun enter(text: String, at: Int = text.length): Pair<String, Int> {
        val old = TextFieldValue(text, TextRange(at))
        val new = TextFieldValue(text.substring(0, at) + "\n" + text.substring(at), TextRange(at + 1))
        return continueList(old, new).let { it.text to it.selection.start }
    }

    @Test fun numbersLettersAndDashes() {
        assertEquals("1. хлеб\n2. ", enter("1. хлеб").first)
        assertEquals("1) хлеб\n2) ", enter("1) хлеб").first)
        assertEquals("а) хлеб\nб) ", enter("а) хлеб").first)
        assertEquals("a. bread\nb. ", enter("a. bread").first)
        assertEquals("— хлеб\n— ", enter("— хлеб").first)
        assertEquals("• хлеб\n• ", enter("• хлеб").first)
        assertEquals("9. хлеб\n10. ", enter("9. хлеб").first)
    }

    @Test fun inTheMiddleToo() {
        val (t, cursor) = enter("1. хлеб\n2. молоко", at = 7)
        assertEquals("1. хлеб\n2. \n2. молоко", t)
        assertEquals(11, cursor)
    }

    @Test fun anEmptyMarkerEndsTheList() {
        assertEquals("1. хлеб\n", enter("1. хлеб\n2. ").first)
    }

    @Test fun plainTextStaysPlain() {
        assertEquals("купить хлеб\n", enter("купить хлеб").first)
    }

    @Test fun theLeadingWord() {
        assertEquals("idea" to "Блог о дизайне", LocalParser.lead("Идея. Блог о дизайне"))
        assertEquals("task" to "купить хлеб", LocalParser.lead("дело купить хлеб"))
        assertEquals(null to "Купить хлеб", LocalParser.lead("Купить хлеб"))
    }

    // Owner, 13 окт: the keyword said any way, even misheard, is the mark itself.
    @Test fun keywordsSaidAnyWay() {
        assertEquals(listOf("idea") to "посмотреть кино", LocalParser.marks("В идеи посмотреть кино"))
        assertEquals(listOf("idea") to "посмотреть кино", LocalParser.marks("В идее посмотреть кино"))
        assertEquals(listOf("idea") to "посмотреть кино", LocalParser.marks("В заметки посмотреть кино"))
        assertEquals(listOf("idea") to "посмотреть кино", LocalParser.marks("слеш идея посмотреть кино"))
        assertEquals(listOf("idea") to "посмотреть кино", LocalParser.marks("/ идея посмотреть кино"))
        assertEquals(listOf("task") to "купить хлеб", LocalParser.marks("в дела купить хлеб"))
        assertEquals(listOf("work", "task") to "отчёт", LocalParser.marks("по работе, дело: отчёт"))
        assertEquals(listOf("personal") to "купить подарок", LocalParser.marks("личное: купить подарок"))
        assertEquals(emptyList<String>() to "Работа над сайтом", LocalParser.marks("Работа над сайтом"))
        assertEquals(emptyList<String>() to "Идеальный день", LocalParser.marks("Идеальный день"))
        assertEquals(emptyList<String>() to "Дело в том, что Дима занят", LocalParser.marks("Дело в том, что Дима занят"))
        assertEquals("idea", LocalParser.parse("В идеи посмотреть кино", ru.anjeib.margin.data.AppData()).items.single().kind)
    }

    // Owner, 13 окт: a period is a window start..due, not its last day.
    @Test fun periodsAreWindows() {
        val d = ru.anjeib.margin.data.AppData()
        val thu = java.time.LocalDate.of(2026, 10, 8)
        fun w(t: String) = LocalParser.parse(t, d, today = thu).items.single().let { it.start?.toString() to it.due?.toString() }
        assertEquals("2026-10-09" to "2026-10-11", w("с 9 по 11 октября сдать отчёт"))
        assertEquals("2026-10-09" to "2026-10-11", w("9–11 октября сдать отчёт"))
        assertEquals("2026-10-28" to "2026-11-03", w("с 28 октября по 3 ноября отпуск"))
        assertEquals("2026-10-08" to "2026-10-31", w("до конца месяца сдать отчёт"))
        assertEquals("2026-10-08" to "2026-10-11", w("на этой неделе позвонить в банк"))
        assertEquals("2026-10-12" to "2026-10-18", w("на следующей неделе позвонить в банк"))
        assertEquals("2026-10-08" to "2026-10-22", w("в течение двух недель подготовить смету"))
        assertEquals("2026-11-01" to "2026-11-30", w("в ноябре поменять резину"))
        assertEquals("2026-10-12" to "2026-10-14", w("с понедельника по среду ремонт"))
        assertEquals("2026-10-09" to "2026-10-11", w("с 9 по 11 сдать отчёт"))
        // Hours stay hours.
        assertEquals(null to "2026-10-08", LocalParser.parse("с 9 до 11 уборка", d, today = thu).items.single().let { it.start?.toString() to it.due?.toString() })
    }

    // Owner, 13 окт: a window stands on every day of it from today on — «Сегодня» and the calendar — until done.
    @Test fun windowStandsOnToday() {
        val thu = java.time.LocalDate.of(2026, 10, 8)
        val t = ru.anjeib.margin.data.Task(title = "Смета", start = "2026-10-05", due = "2026-10-31")
        val d = ru.anjeib.margin.data.AppData(tasks = listOf(t))
        assertEquals(listOf("Смета"), ru.anjeib.margin.ui.dayPlan(d, thu, thu, emptyList()).deals.map { it.title })
        assertEquals(listOf("Смета"), ru.anjeib.margin.ui.dayPlan(d, thu.plusDays(1), thu, emptyList()).deals.map { it.title })
        assertEquals(emptyList<String>(), ru.anjeib.margin.ui.dayPlan(d, java.time.LocalDate.of(2026, 11, 1), thu, emptyList()).deals.map { it.title })
        assertEquals(emptyList<String>(), ru.anjeib.margin.ui.dayPlan(d, thu.minusDays(1), thu, emptyList()).deals.map { it.title })
        assertEquals(listOf("Смета"), ru.anjeib.margin.ui.dayPlan(d, java.time.LocalDate.of(2026, 10, 31), thu, emptyList()).deals.map { it.title })
    }

    // ChatGPT's review, owner, 14 окт: a report of what happened is a record, not a matter with an empty circle.
    @Test fun reportsAreRecords() {
        val d = ru.anjeib.margin.data.AppData()
        org.junit.Assert.assertTrue(LocalParser.isReport("Созвонились с Катей: лендинг переносим на следующую неделю", d))
        org.junit.Assert.assertTrue(LocalParser.isReport("клиент согласовал макет", d))
        org.junit.Assert.assertFalse(LocalParser.isReport("Позвонить маме завтра в 12", d))
        org.junit.Assert.assertFalse(LocalParser.isReport("перенеси созвон на пятницу", d))
        org.junit.Assert.assertFalse(LocalParser.isReport("Купить молоко и хлеб", d))
    }
}
