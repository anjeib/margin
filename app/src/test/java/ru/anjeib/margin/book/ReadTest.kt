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

    @Test fun aListUnderALineIsItsItems() {
        assertEquals(listOf("молоко", "хлеб"), Read.items("- молоко\n- хлеб"))
        assertEquals(listOf("смета", "договор"), Read.items("1. смета\n2) договор"))
        assertEquals(listOf("молоко", "хлеб", "яйца"), Read.items("купить: молоко, хлеб, яйца"))
        assertEquals(listOf("молоко", "хлеб", "яйца"), Read.items("молоко, хлеб и яйца"))
        assertEquals(listOf("мыло", "шампунь", "паста"), Read.items("мыло, шампунь, паста"))
        // A comment stays one, commas and all.
        assertEquals(1, Read.items("прочитал, понравилось, но дорого").size)
        assertEquals(1, Read.items("позвонить Олегу, если не ответит").size)
    }

    @Test fun aListBecomesStepsWithCount() {
        val b = book.addChild("ex-lamp", "молоко", Kind.Todo).addChild("ex-lamp", "хлеб", Kind.Todo)
        val kids = b.children("ex-lamp")
        assertEquals(2, kids.size)
        val done = b.toggle(kids.first().id, today)
        assertEquals(1 to 2, done.steps("ex-lamp"))
    }

    @Test fun aLongNoteStaysWhole() {
        val meeting = "Встреча с Олегом по упаковке. Обсудили три варианта коробки, он склоняется ко второму, но хочет зерно чуть круглее и цвет теплее. " +
            "Типография просит макеты до пятницы, в среду нужен счёт. Бюджет на тираж 120 тысяч, можно поднять до 140, если бумага будет крафтовая."
        val l = read(meeting).lines.single()
        assertEquals(Kind.Note, l.kind)
        assertEquals(today.toString(), l.day)
        assertTrue("no word is taken out", l.text.contains("до пятницы") && l.text.contains("ко второму"))
        assertTrue(l.who.any { it.name == "Олег" })
        val call = "Итоги созвона\nКлиент хочет запуск к 1 ноября\nНужны три баннера и лендинг, бюджет 300 тыс\nОлег пришлёт тексты в понедельник"
        assertEquals(call, read(call).lines.single().text)
        assertTrue(Read.stepsIn(meeting).isEmpty())
    }

    @Test fun thingsToDoInANoteAreOffered() {
        val trip = "Мысли про отпуск: хочется в горы, но не в сезон дождей. Может быть Алтай в сентябре или Грузия в мае. Взять палатку, проверить билеты заранее, спросить Машу про маршрут."
        assertEquals(Kind.Note, read(trip).lines.single().kind)
        assertEquals(listOf("Взять палатку", "Проверить билеты заранее", "Спросить Машу про маршрут"), Read.stepsIn(trip))
    }

    @Test fun manyThingsToDoStayToDos() {
        val dump = "Завтра в 10 позвонить Олегу, потом заехать в типографию забрать пробники, в среду встреча с Машей в 15, и не забыть оплатить интернет до пятницы, а ещё купить корм коту и записаться к врачу"
        assertTrue(read(dump).lines.size > 1)
        assertTrue(read("- позвонить Олегу насчёт упаковки и макетов\n- купить молоко, хлеб и яйца на завтра\n- оплатить интернет до пятницы обязательно").lines.size == 3)
        assertEquals(1, Read.items("Олег выбрал второй вариант, но просит сделать зерно круглее.\nТипография ждёт макеты до пятницы, а бумагу привезут только в среду, так что успеваем впритык.").size)
    }

    @Test fun aListInAMessageIsItsSteps() {
        val msg = "Олег Самокат:\nТимур, привет! Глянули лендинг, в целом ок, но хотим:\n1) крупнее фото кофе на первом экране\n2) кнопку заказа поменять на терракотовую\n3) добавить блок с отзывами\nСможешь до понедельника?"
        assertEquals(Kind.Note, read(msg).lines.single().kind)
        assertEquals(listOf("Крупнее фото кофе на первом экране", "Кнопку заказа поменять на терракотовую", "Добавить блок с отзывами"), Read.stepsIn(msg))
    }

    @Test fun wordsNamingAThreadGoIntoIt() {
        val b = book.copy(threads = book.threads + Thread(id = "sam", title = "самокат"))
        assertEquals("sam", Read.draft("созвон с самокатом в 15 по лендингу", b, today, today, now = null).lines.single().threadId)
        assertEquals("ex-coffee", Read.draft("отправить счёт Зерну за упаковку", b, today, today, now = null).lines.single().threadId)
        assertNull("«Идеи» is too like a word", Read.draft("есть идеи для подарка маме", b, today, today, now = null).lines.single().threadId)
    }

    @Test fun aListIsOneLineWithItsItems() {
        val shop = read("список в магазин:\nхлеб\nмолоко\nсыр\nяйца")
        assertEquals("Список в магазин", shop.lines.single().text)
        assertEquals(listOf("Хлеб", "Молоко", "Сыр", "Яйца"), shop.under.map { it.text })
        assertTrue(shop.under.all { it.parentId == shop.lines.single().id && it.kind == Kind.Todo })
        val mom = read("купить маме таблетки, хлеб, молоко и корм коту")
        assertEquals("Купить маме таблетки, хлеб, молоко и корм коту", mom.lines.single().text)
        assertEquals(listOf("Маме таблетки", "Хлеб", "Молоко", "Корм коту"), mom.under.map { it.text })
        val sat = read("в понедельник купить: хлеб, молоко, сыр")
        assertEquals(next(DayOfWeek.MONDAY).toString(), sat.lines.single().day)
        assertEquals(3, sat.under.size)
        // Things to do, not things to buy: separate lines, as before.
        assertTrue(read("позвонить Олегу, написать Маше и забрать посылку").under.isEmpty())
    }

    @Test fun notesOfAMeetingStayWholeWithTheirSteps() {
        val minutes = "Совещание с завучами 13.10\nПо олимпиадам сроки сдвинули, школьный этап до 25 октября\nНиколай Иванович обещал отдать ключи от актового зала к четвергу\nНужно подготовить расписание замен на ноябрь и согласовать с директором\nПроверить журналы 7-х классов, напомнить классным руководителям про родительские собрания"
        val l = read(minutes).lines.single()
        assertEquals(Kind.Note, l.kind)
        assertTrue("nothing is lost", l.text.contains("до 25 октября"))
        val steps = Read.stepsIn(minutes)
        assertTrue(steps.any { it.startsWith("Николай Иванович обещал") })
        assertTrue(steps.any { it.startsWith("Подготовить расписание") })
        assertTrue(steps.any { it.startsWith("Проверить журналы") })
        val keys = Read.draft(steps.first { it.startsWith("Николай") }, book, today, today, now = null, lists = false).lines.single()
        assertTrue(keys.who.any { it.dir == Dir.From && it.name.startsWith("Николай") })
    }

    @Test fun aThoughtSaidAloudStaysWhole() {
        val t = "думала про каникулы хорошо бы съездить к маминой сестре в Тверь но Даня хочет в лагерь надо посчитать бюджет и узнать у Лизы про кружок по рисованию может совместить поездку с выходными в ноябре"
        val l = read(t).lines.single()
        assertEquals(Kind.Note, l.kind)
        assertTrue(l.text.contains("Даня хочет в лагерь"))
    }

    @Test fun aNameWithItsPatronymicIsOnePerson() {
        assertEquals(listOf(Who("Ирина Петровна", Dir.From)), read("Ирина Петровна сдаст отчёт по олимпиаде до пятницы").lines.single().who)
        val keys = Read.draft("Николай Иванович обещал отдать ключи от актового зала к четвергу", book, today, today, now = null, lists = false).lines.single()
        assertEquals(listOf(Who("Николай Иванович", Dir.From)), keys.who)
    }

    @Test fun remindingSomeoneIsTheThingToDo() {
        assertEquals("Напомнить классным руководителям про родительские собрания", read("напомнить классным руководителям про родительские собрания").lines.single().text)
        assertEquals("Купить хлеб", read("напомни купить хлеб").lines.single().text)
    }
}
