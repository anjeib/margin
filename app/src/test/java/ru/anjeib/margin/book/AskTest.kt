package ru.anjeib.margin.book

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Questions to the notebook answered on the phone — over the designer's examples (a Saturday, 10 Oct 2026). */
class AskTest {
    private val today = LocalDate.of(2026, 10, 10)
    private val book = Examples.build(today)
    private fun ask(q: String) = Ask.answer(q, book, today)
    private fun texts(a: Ask.Answer) = a.lines.map { it.text }

    @Test fun promisedToAPerson() {
        val a = ask("Что я обещал Лёше?")
        assertEquals(Ask.About.Promised, a.about)
        assertEquals("Лёша", a.name)
        assertEquals(listOf("Исходники баннеров"), texts(a))
    }

    @Test fun waitedFromAPerson() {
        listOf("Что ждём от Маши?", "что мне должна Маша?", "Что Маша обещала?").forEach { q ->
            val a = ask(q)
            assertEquals(q, Ask.About.Waited, a.about)
            assertEquals(q, listOf("3 000 ₽ за билеты"), texts(a))
        }
    }

    @Test fun allPromisesAndWaits() {
        assertEquals(listOf("Исходники баннеров"), texts(ask("Кому я что обещал?")))
        assertTrue(texts(ask("Чего я жду?")).containsAll(listOf("Фидбек по упаковке", "3 000 ₽ за билеты")))
    }

    @Test fun aPersonWithoutDirection() {
        val a = ask("Что с Олегом?")
        assertEquals(Ask.About.Person, a.about)
        assertTrue(texts(a).containsAll(listOf("Показать финальный логотип Олегу", "Фидбек по упаковке")))
    }

    @Test fun aDay() {
        val a = ask("Что у меня завтра?")
        assertEquals(Ask.About.Day, a.about)
        assertEquals(today.plusDays(1), a.from)
        assertTrue(texts(a).containsAll(listOf("Исходники баннеров", "Английский с репетитором", "Фидбек по упаковке")))
        assertTrue("today's lines are not tomorrow's", "Купить молоко и хлеб" !in texts(a))
    }

    @Test fun whatIsBurning() {
        val a = ask("Что горит?")
        assertEquals(Ask.About.Tails, a.about)
        assertEquals("Продлить подписку Figma", texts(a).first())
    }

    @Test fun aThreadByAnyOfItsWords() {
        val a = ask("Что по кофейне?")
        assertEquals(Ask.About.Thread, a.about)
        assertEquals("ex-coffee", a.threadId)
        assertTrue(texts(a).contains("Макет упаковки для типографии"))
    }

    @Test fun whenIsSomething() {
        val a = ask("Когда барбер?")
        assertEquals(Ask.About.Found, a.about)
        assertEquals(listOf("Записаться к барберу"), texts(a))
    }

    @Test fun doneToday() {
        val a = ask("Что я сделал сегодня?")
        assertEquals(Ask.About.Done, a.about)
        assertEquals(listOf("Сдать макеты лендинга"), texts(a))
    }

    @Test fun aStrangerIsNotEveryone() {
        val a = ask("Что мне должен Иванов?")
        assertEquals(Ask.About.Waited, a.about)
        assertEquals("Иванов", a.name)
        assertTrue(a.lines.isEmpty())
    }

    @Test fun theWeek() {
        val a = ask("Что на неделе?")
        assertEquals(Ask.About.Day, a.about)
        assertTrue(a.to!!.isAfter(a.from))
    }

    @Test fun whatThePhoneCannotReadGoesToTheAi() {
        assertEquals(Ask.About.Unknown, ask("Сколько я потратил на кофе в сентябре?").about)
        // A name with more words is not «что с Олегом?».
        val a = ask("О чём мы договорились с Олегом по логотипу?")
        assertTrue(a.about == Ask.About.Unknown || (a.about == Ask.About.Found && a.weak))
    }

    @Test fun aPromiseInAStepIsOwedToo() {
        val b = book.add(listOf(Line(id = "n", text = "Совещание", kind = Kind.Note, day = today.toString())))
            .let { it.add(listOf(Line(id = "k", text = "Отдать ключи от зала", parentId = "n", who = listOf(Who("Николай Иванович", Dir.From)), day = today.toString(), until = today.plusDays(2).toString()))) }
        assertTrue(Ask.answer("что мне должны?", b, today).lines.any { it.id == "k" })
        assertTrue(b.personLines("Николай Иванович").waited.any { it.id == "k" })
    }
}
