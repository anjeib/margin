package ru.anjeib.margin.book

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** A phrase into lines on the phone: the words of time leave for the margin, the rest stays the person's own. */
class ReadTest {
    private val today = LocalDate.of(2026, 10, 10) // Saturday
    private val book = Examples.build(today)
    private fun read(t: String, page: LocalDate = today, threadId: String? = null, noDay: Boolean = false) =
        Read.draft(t, book, page, today, now = null, threadId = threadId, noDay = noDay)
    private fun next(d: DayOfWeek) = today.with(TemporalAdjusters.next(d))

    @Test fun timeWordsGoToTheMargin() {
        val l = read("завтра в 15 позвонить Олегу").lines.single()
        assertEquals("Позвонить Олегу", l.text)
        assertEquals(today.plusDays(1).toString(), l.day)
        assertEquals("15:00", l.time)
    }

    @Test fun aLineWithoutADayStaysOnTheOpenPage() {
        val friday = next(DayOfWeek.FRIDAY)
        assertEquals(friday.toString(), read("купить батарейки", page = friday).lines.single().day)
    }

    @Test fun laterHasNoDay() {
        val l = read("потом разобрать фото с отпуска").lines.single()
        assertNull(l.day)
        assertEquals("Разобрать фото с отпуска", l.text)
    }

    @Test fun aMeetingIsAnEventAndARecordIsANote() {
        val m = read("встреча с Анной в пятницу в 12").lines.single()
        assertEquals(Kind.Event, m.kind)
        assertEquals("12:00", m.time)
        assertEquals(Kind.Note, read("запись: Олег хочет зерно круглее").lines.single().kind)
    }

    @Test fun promisesEitherWay() {
        assertEquals(Who("Дима", Dir.From), read("Дима пришлёт макет в среду").lines.single().who.first())
        assertTrue(read("обещал Лёше скинуть фото до пятницы").lines.single().who.any { it.name == "Лёша" && it.dir == Dir.To })
    }

    @Test fun severalDaysAreSeveralLinesAndARepeatIsOne() {
        assertEquals(2, read("в пн и чт бассейн").lines.size)
        val r = read("каждый вторник в 10 английский").lines.single()
        assertEquals(listOf(2), r.repeat!!.days)
        assertEquals("10:00", r.time)
    }

    @Test fun aThreadTagAndAThreadPage() {
        val d = read("#Зерно макет вывески")
        assertEquals("Зерно", d.threadTitle)
        // On a thread's page a line without a day stays in the thread, off the days.
        assertNull(read("подобрать шрифт", threadId = "ex-coffee").lines.single().day)
    }

    @Test fun aDeadlineIsAWindowFromTheOpenPage() {
        val l = read("обещала Диме прислать бриф до пятницы").lines.single()
        assertEquals(today.toString(), l.day)
        assertEquals(next(DayOfWeek.FRIDAY).toString(), l.until)
        assertEquals(today.toString(), read("к среде подготовить смету").lines.single().day)
        // «в пятницу» is a day, not a deadline.
        assertEquals(next(DayOfWeek.FRIDAY).toString(), read("в пятницу купить вино").lines.single().day)
    }

    @Test fun givingToSomeoneIsWhatYouOweThem() {
        assertEquals(listOf(Who("Дима", Dir.To)), read("прислать бриф Диме").lines.single().who)
        // Buying a present for mum is not a promise, and not «с мамой» either.
        assertTrue(read("купить подарок маме").lines.single().who.none { it.dir == Dir.To })
        assertEquals(Dir.With, read("встреча с Олегом в пятницу в 12").lines.single().who.single().dir)
    }

    @Test fun surnamesArePeopleAndDebtsAreWaits() {
        val debt = read("Иванов должен 50 тыс за кухню до конца месяца").lines.single()
        assertEquals(Who("Иванов", Dir.From), debt.who.single())
        assertEquals(today.toString(), debt.day)
        assertEquals("2026-10-31", debt.until)
        assertEquals("Петров", read("завтра в 10 приедет Петров за образцами").lines.single().who.single().name)
        assertEquals("Петров", read("созвониться с Петровым").lines.single().who.single().name)
        // A street is not a person.
        assertTrue(read("забрать заказ на Ленина 5").lines.single().who.isEmpty())
    }

    @Test fun aVisitIsAMeetingAndAStreetIsAPlace() {
        val l = read("Саня в чт в 9 на замер, Ленина 5").lines.single()
        assertEquals(Kind.Event, l.kind)
        assertEquals("09:00", l.time)
        assertEquals("Ленина 5", l.place)
        assertTrue(l.who.any { it.name == "Саня" })
        assertEquals(Kind.Event, read("завтра в 10 приедет Петров за образцами").lines.single().kind)
    }

    @Test fun kinshipWordsKeepTheirSmallLetter() {
        assertEquals("Позвонить маме", read("позвонить маме вечером").lines.single().text)
    }

    @Test fun aQuestionIsNotALine() {
        val d = read("что я обещал Лёше?")
        assertTrue(d.question)
        assertTrue(d.lines.isEmpty())
    }
}
