package ru.anjeib.margin.book

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** The AI's reading laid over the phone's: corrections in place, the person's hand first. Offline, no AI called. */
class AiReadTest {
    private val today = LocalDate.of(2026, 10, 10)
    private val friday = LocalDate.of(2026, 10, 16)
    private val book = Examples.build(today)

    private fun written(text: String): Pair<Book, List<Line>> {
        val d = Read.draft(text, book, today, today, now = null)
        return book.add(d.lines) to d.lines
    }

    @Test fun aCorrectedDayIsAnEditByMarginAndNoTrace() {
        val said = "созвонились с Олегом, он пришлёт смету в чт"
        val (b, phone) = written(said)
        val reply = """```json
            {"lines":[{"text":"Смета","kind":"todo","day":"2026-10-16","time":null,"who":[{"name":"Олег","dir":"from"}]}],"done":[]}
            ```"""
        val r = AiRead.parseRead(reply, b, today, said, null)
        assertNotNull(r)
        val after = AiRead.merge(b, phone, r!!, today)
        val l = after.line(phone.single().id)!!
        assertEquals("Смета", l.text)
        assertEquals("2026-10-16", l.day)
        assertEquals(Dir.From, l.who.single().dir)
        assertTrue(l.edits.any { it.what == "when" && it.by == "margin" })
        assertTrue("a correction is not a put-off", l.trail.isEmpty())
    }

    @Test fun reportedDoneClosesTheListedLine() {
        val said = "Олег прислал фидбек по упаковке"
        val (b, phone) = written(said)
        val reply = """{"lines":[],"done":["ex-c3"]}"""
        val r = AiRead.parseRead(reply, b, today, said, null)!!
        val after = AiRead.merge(b, phone, r, today)
        // Margin asks before closing a line: nothing is closed by the merge itself.
        assertEquals(State.Open, after.line("ex-c3")!!.state)
        assertEquals(listOf("ex-c3" to State.Done), AiRead.proposals(after, phone, r))
        assertEquals("the report stays as a record", Kind.Note, after.line(phone.single().id)!!.kind)
    }

    @Test fun moreLinesReplaceThePhonesOnes() {
        val (b, phone) = written("в пятницу отвезти маме лекарства и забрать посылку")
        val reply = """{"lines":[{"text":"Отвезти маме лекарства","day":"$friday"},{"text":"Забрать посылку","day":"$friday"}]}"""
        val r = AiRead.parseRead(reply, b, today, "…", null)!!
        val after = AiRead.merge(b, phone, r, today)
        if (phone.size != 2) {
            phone.forEach { assertNull(after.line(it.id)) }
            assertEquals(2, after.lines.count { it.day == friday.toString() && it.text in setOf("Отвезти маме лекарства", "Забрать посылку") })
        }
    }

    @Test fun theAiBringsNoPersonTheWordsDoNotName() {
        val said = "созвонились с Петровым, он пришлёт чертежи в среду, а нам до пятницы посчитать смету"
        val (b, _) = written(said)
        val r = AiRead.parseRead("""{"lines":[{"text":"Посчитать смету","day":"$today","until":"$friday","who":[{"name":"Сергей","dir":"to"}]},
            {"text":"Чертежи","day":"2026-10-14","who":[{"name":"Петров","dir":"from"}]}]}""", b, today, said, null)!!
        assertTrue(r.lines[0].who.isEmpty())
        assertEquals(Who("Петров", Dir.From), r.lines[1].who.single())
    }

    @Test fun aSurnameIsNotACommonWord() {
        val visit = Line(id = "v", text = "Приедет Петров за образцами", day = today.plusDays(1).toString())
        assertTrue(!AiRead.plausiblyDone(visit, "созвонились с Петровым, он пришлёт чертежи в среду", book.add(listOf(visit))))
    }

    @Test fun thePersonsHandWins() {
        val (b, phone) = written("позвонить в банк")
        val touched = b.retext(phone.single().id, "Позвонить в банк про карту")
        val r = AiRead.parseRead("""{"lines":[{"text":"Позвонить в банк","day":"$friday"}]}""", touched, today, "…", null)!!
        assertSame(touched, AiRead.merge(touched, phone, r, today))
    }

    @Test fun anAnswerKeepsOnlyRealLines() {
        val a = AiRead.parseAsk("""{"say":"Олегу нужно показать логотип в 15:00.","ids":["ex-c2","nope"]}""", book)!!
        assertEquals(listOf("ex-c2"), a.ids)
        assertEquals("Олегу нужно показать логотип в 15:00.", a.say)
    }

    @Test fun thePromptsHoldTheRightLines() {
        val p = AiRead.askPrompt(book, "о чём договорились с Олегом по логотипу?", today, true)
        assertTrue(p.contains("ex-c2 | todo | 2026-10-10 15:00 | Показать финальный логотип Олегу"))
        assertTrue(AiRead.readPrompt(book, "купить хлеб", today, today, true).contains("Threads: Кофейня «Зерно»; Идеи"))
    }

    @Test fun aDoneNeedsTheWordsToBackIt() {
        fun l(id: String) = book.line(id)!!
        // The same person is not enough.
        assertTrue(!AiRead.plausiblyDone(l("ex-c2"), "созвонились с Олегом, он пришлёт смету по упаковке в четверг", book))
        // A word of the line itself, even across alphabets.
        assertTrue(AiRead.plausiblyDone(l("ex-figma"), "оплатил фигму", book))
        assertTrue(AiRead.plausiblyDone(l("ex-banners"), "Лёша сказал что исходники баннеров уже не нужны", book))
        // The one we waited for gave it.
        assertTrue(AiRead.plausiblyDone(l("ex-money"), "Маша вернула деньги", book))
        assertTrue(!AiRead.plausiblyDone(l("ex-money"), "видел Машу", book))
    }

    @Test fun brokenRepliesAreIgnored() {
        assertNull(AiRead.parseRead("not json", book, today, "…", null))
        assertNull(AiRead.parseRead("""{"lines":[{"kind":"todo"}]}""", book, today, "…", null))
    }
}
