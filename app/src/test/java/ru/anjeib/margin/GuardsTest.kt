package ru.anjeib.margin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.anjeib.margin.ai.Assistant
import ru.anjeib.margin.data.AppData
import ru.anjeib.margin.data.Repeat
import ru.anjeib.margin.data.Task
import ru.anjeib.margin.data.address
import ru.anjeib.margin.data.sentence
import ru.anjeib.margin.data.withNames
import ru.anjeib.margin.i18n.L
import ru.anjeib.margin.i18n.Lang
import java.time.DayOfWeek
import java.time.LocalDate

/** The code-side guards around model replies — the mistakes the AI testers ran into, replayed without a model. */
class GuardsTest {
    init { L.lang = Lang.Ru }
    private val today = LocalDate.now()

    private fun run(d: AppData, json: String, text: String, focus: String? = null) =
        Assistant.apply(d, Assistant.parsePlan(json), text, strict = true, focus = focus).data

    @Test fun unaskedTaskStaysAsItWas() {
        val logo = Task(id = "logo", title = "Логотип для кофейни", due = "2026-10-09")
        val igor = Task(id = "igor", title = "Ответить Игорю", due = today.plusDays(1).toString())
        val d = run(AppData(tasks = listOf(logo, igor)),
            """{"actions":[{"type":"add_subtasks","task_id":"logo","subtasks":[{"title":"Собрать мудборд"}]},{"type":"update_task","task_id":"igor","due":"$today","time":"14:00"}]}""",
            "подзадачи: собрать мудборд", focus = "logo")
        assertEquals(igor, d.tasks.first { it.id == "igor" })
        assertEquals(1, d.tasks.first { it.id == "logo" }.subtasks.size)
    }

    @Test fun moveKeepsTime() {
        val t = Task(id = "m", title = "Вызвать мастера", due = today.plusDays(1).toString(), time = "09:00")
        val d = run(AppData(tasks = listOf(t)), """{"actions":[{"type":"update_task","task_id":"m","due":"${today.plusDays(2)}","time":"14:00"}]}""", "перенеси мастера на послезавтра")
        assertEquals(today.plusDays(2).toString(), d.tasks[0].due)
        assertEquals("09:00", d.tasks[0].time)
    }

    @Test fun monthlyNeverStartsInThePast() {
        val d = run(AppData(), """{"actions":[{"type":"create_task","title":"Оплатить аренду","due":"${today.withDayOfMonth(1)}","repeat":{"freq":"monthly"}}]}""", "аренда первого числа каждый месяц")
        val want = if (today.dayOfMonth == 1) today else today.withDayOfMonth(1).plusMonths(1)
        assertEquals(want.toString(), d.tasks[0].due)
    }

    @Test fun monthlyWithoutDateTakesTheNamedDay() {
        val d = run(AppData(), """{"actions":[{"type":"create_task","title":"Оплатить аренду","repeat":{"freq":"monthly"}}]}""", "аренда первого числа каждый месяц")
        val want = if (today.dayOfMonth == 1) today else today.withDayOfMonth(1).plusMonths(1)
        assertEquals(want.toString(), d.tasks[0].due)
    }

    @Test fun weeklyGetsItsNearestDay() {
        val d = run(AppData(), """{"actions":[{"type":"create_task","title":"Заказать муку","repeat":{"freq":"weekly","days":[1]}}]}""", "каждый понедельник заказать муку")
        val due = LocalDate.parse(d.tasks[0].due)
        assertEquals(DayOfWeek.MONDAY, due.dayOfWeek)
        assertTrue(!due.isBefore(today) && due.isBefore(today.plusDays(7)))
    }

    @Test fun errandIsNotWaiting() {
        val d = run(AppData(), """{"actions":[{"type":"create_task","title":"Заказать муку","waiting_for":"тимур"}]}""", "заказать муку у тимура")
        assertNull(d.tasks[0].waitingFor)
        assertEquals(listOf("Тимур"), d.tasks[0].people)
        assertTrue(d.persons.any { it.name == "Тимур" })
    }

    @Test fun realWaitingStays() {
        val d = run(AppData(), """{"actions":[{"type":"create_task","title":"Получить макет","waiting_for":"Дима","due":"2026-10-07"}]}""", "Дима должен прислать макет в среду")
        assertEquals("Дима", d.tasks[0].waitingFor)
    }

    @Test fun promiseDoesNotSwallowRecurringTask() {
        val rent = Task(id = "rent", title = "Оплатить аренду", due = "2026-11-01", repeat = Repeat("monthly"))
        val d = run(AppData(tasks = listOf(rent)), """{"actions":[{"type":"update_task","task_id":"rent","promised_to":"Виктор","due":"2026-10-10"}]}""", "обещал виктору арендодателю оплату аренды до десятого")
        assertEquals(rent, d.tasks.first { it.id == "rent" })
        val promise = d.tasks.first { it.id != "rent" }
        assertEquals("Виктор", promise.promisedTo); assertEquals("2026-10-10", promise.due); assertNull(promise.repeat)
        assertTrue(d.persons.any { it.name == "Виктор" })
    }

    @Test fun unaskedTimeIsNotInvented() {
        val d = run(AppData(), """{"actions":[{"type":"create_task","title":"Купить кассовую ленту","due":"${today.plusDays(1)}","time":"18:00"}]}""", "завтра купить кассовую ленту")
        assertNull(d.tasks[0].time)
    }

    @Test fun duplicateIsNotCreated() {
        val t = Task(id = "x", title = "Отправить Марине мудборд")
        val d = run(AppData(tasks = listOf(t)), """{"actions":[{"type":"create_task","title":"отправить Марине мудборд","project":"портфолио"}]}""", "мудборд для Марины")
        assertEquals(1, d.tasks.size)
    }

    @Test fun bulkRequestsStillWork() {
        val a = Task(id = "a", title = "Позвонить в банк", due = today.minusDays(1).toString())
        val b = Task(id = "b", title = "Оплатить свет", due = today.minusDays(2).toString())
        val d = run(AppData(tasks = listOf(a, b)),
            """{"actions":[{"type":"update_task","task_id":"a","due":"${today.plusDays(1)}"},{"type":"update_task","task_id":"b","due":"${today.plusDays(1)}"}]}""", "перенеси всё просроченное на завтра")
        assertTrue(d.tasks.all { it.due == today.plusDays(1).toString() })
    }

    @Test fun sameWordsWithAPromiseIsNew() {
        val rent = Task(id = "rent", title = "Оплатить аренду", due = "2026-11-01", repeat = Repeat("monthly"))
        val d = run(AppData(tasks = listOf(rent)), """{"actions":[{"type":"create_task","title":"Оплатить аренду","due":"2026-10-10","promised_to":"Виктор"}]}""", "обещал виктору оплату до десятого")
        assertEquals(2, d.tasks.size)
    }

    @Test fun mangledTypeKeyIsRead() {
        val t = Task(id = "logo", title = "Логотип")
        val d = run(AppData(tasks = listOf(t)), """{"actions":[{".type":"add_subtasks","task_id":"logo","subtasks":[{"title":"Мудборд"}]}]}""", "подзадача мудборд", focus = "logo")
        assertEquals(1, d.tasks[0].subtasks.size)
    }

    @Test fun recordsStartWithACapitalAndNamesToo() {
        val d = run(AppData(), """{"actions":[{"type":"create_task","title":"заказать муку у тимура","people":["тимур"]}]}""", "заказать муку у тимура")
        assertEquals("Заказать муку у Тимура", d.tasks[0].title)
        assertEquals("Вёрстка главной", "вёрстка главной".sentence().withNames(listOf("Вера")))
        assertEquals("Позвонить Марине", "позвонить марине".sentence().withNames(listOf("Марина")))
    }

    @Test fun oldLowercaseRecordsAreFixed() {
        val d = AppData(tasks = listOf(Task(title = "созвон с мариной", people = listOf("марина"))), persons = listOf(ru.anjeib.margin.data.Person(name = "марина"))).migrated()
        assertEquals("Созвон с Мариной", d.tasks[0].title)
        assertEquals("Марина", d.persons[0].name)
        assertEquals(listOf("Марина"), d.tasks[0].people)
    }

    @Test fun morningStaysAWord() {
        val d = run(AppData(), """{"actions":[{"type":"create_task","title":"Позвонить Наталье","due":"${today.plusDays(1)}","time":"09:00"}]}""", "завтра утром позвонить наталье")
        assertNull(d.tasks[0].time)
        assertEquals("morning", d.tasks[0].dayPart)
        val e = run(AppData(), """{"actions":[{"type":"create_task","title":"Позвонить","due":"${today.plusDays(1)}","time":"09:00"}]}""", "завтра в 9 утра позвонить")
        assertEquals("09:00", e.tasks[0].time)
        assertNull(e.tasks[0].dayPart)
    }

    @Test fun stepTalkDoesNotRenameTheTask() {
        val t = Task(id = "x", title = "Сайт", subtasks = listOf(ru.anjeib.margin.data.Subtask(id = "s1", title = "Тексты"), ru.anjeib.margin.data.Subtask(id = "s2", title = "Макет")))
        val d = run(AppData(tasks = listOf(t)), """{"actions":[{"type":"update_task","task_id":"x","title":"Макет главной"},{"type":"update_subtask","task_id":"x","subtask_id":"s2","title":"макет главной"}]}""",
            "измени вторую подзадачу на макет главной", focus = "x")
        assertEquals("Сайт", d.tasks[0].title)
        assertEquals("Макет главной", d.tasks[0].subtasks[1].title)
        assertEquals("sub_edit", ru.anjeib.margin.ai.Commands.numbered("измени подзадачу 2 на макет главной")?.what)
        assertEquals("sub_delete", ru.anjeib.margin.ai.Commands.numbered("удали подзадачу 3")?.what)
        assertEquals("sub_done", ru.anjeib.margin.ai.Commands.numbered("подзадача 1 готова")?.what)
    }

    @Test fun saidWeekdayWins() {
        val wed = today.with(java.time.temporal.TemporalAdjusters.nextOrSame(DayOfWeek.WEDNESDAY))
        val d = run(AppData(), """{"actions":[{"type":"create_task","title":"Заказать упаковку","due":"${wed.plusDays(2)}"}]}""", "заказать упаковку до среды")
        assertEquals(wed.toString(), d.tasks[0].due)
    }

    @Test fun addressGetsItsComma() {
        assertEquals("Ленина, 5", "ленина 5".address())
        assertEquals("Ул. Мира, 12к2", "ул. Мира 12к2".address())
        assertEquals("Симферополь, Ленина, 5", "Симферополь, Ленина 5".address())
        assertEquals("Тверская, 12", "Тверская, 12".address())
        assertEquals("Кафе Зерно", "кафе Зерно".address())
    }

    /** Fable, 0.35: one phrase about two clients — nothing of the other client moves, nothing of it lands in this topic's feed, «к четвергу» holds. */
    @Test fun twoClientsInOnePhrase() {
        val fb = ru.anjeib.margin.data.Subtask(id = "fb", title = "Фидбек по упаковке", from = "Олег", due = today.plusDays(1).toString())
        val zerno = Task(id = "zerno", title = "Айдентика для кофейни «Зерно»", topic = true, people = listOf("Олег"), subtasks = listOf(fb))
        val mon = today.with(java.time.temporal.TemporalAdjusters.next(DayOfWeek.MONDAY))
        val thu = today.with(java.time.temporal.TemporalAdjusters.nextOrSame(DayOfWeek.THURSDAY))
        val said = "Завтра в 14 созвон с Катей по лендингу для Авиасейлс, она пришлёт фидбек к четвергу. И надо в пн показать Олегу мудборд"
        val json = """{"actions":[
            {"type":"create_task","title":"Созвон с Катей по лендингу Авиасейлс","due":"${today.plusDays(1)}","time":"14:00","people":["Катя"],"project":"Авиасейлс"},
            {"type":"create_task","title":"Фидбек по лендингу от Кати","waiting_for":"Катя","due":"${today.plusDays(1)}"},
            {"type":"add_subtasks","task_id":"zerno","subtasks":[{"title":"Показать Олегу мудборд","due":"$mon"}]}]}"""
        val d = Assistant.apply(AppData(tasks = listOf(zerno)), Assistant.parsePlan(json), said, strict = true, recent = setOf("zerno")).data
        val z = d.tasks.first { it.id == "zerno" }
        // Олег's own matter is not touched by «показать Олегу».
        assertEquals(today.plusDays(1).toString(), z.subtasks.first { it.id == "fb" }.due)
        // Only the words about this topic go into its feed.
        assertTrue(z.entries.none { it.text.contains("Авиасейлс") })
        assertTrue(z.entries.any { it.text.contains("мудборд") })
        // «она пришлёт фидбек к четвергу»: the wait is on Thursday, not on the phrase's first date.
        assertEquals(thu.toString(), d.tasks.first { it.waitingFor == "Катя" }.due)
        assertEquals(today.plusDays(1).toString(), d.tasks.first { it.title.startsWith("Созвон") }.due)
    }

    /** Fable, 0.35: «/идея …, можно продавать на принтах» — the title stopped at 60 letters with a comma, the record repeated it all. */
    @Test fun longIdeaSplitsAtItsFirstPause() {
        val said = "Серия постеров про московское метро в стиле конструктивизма, можно продавать на принтах"
        val d = Assistant.apply(AppData(), Assistant.parsePlan("""{"actions":[{"type":"create_note","text":"$said"}]}"""), "/идея $said", strict = true).data
        val idea = d.tasks.single { it.thought }
        assertEquals("Серия постеров про московское метро в стиле конструктивизма", idea.title)
        assertEquals(listOf("Можно продавать на принтах"), idea.entries.map { it.text })
    }

    // GigaChat, 12 окт: who owes whom was lost — «Напомнить Саше о смете» instead of «ждём от Саши». The phone reads it outright.
    @Test fun aLostWaitIsFoundAgain() {
        val said = "Саша обещал прислать смету, через пару дней напомнить."
        val d = run(AppData(), """{"actions":[{"type":"create_task","title":"Напомнить Саше о смете","due":"${today.plusDays(2)}","people":["Саша"]}]}""", said)
        val t = d.tasks.single()
        assertEquals("Саша", t.waitingFor)
        assertTrue(t.title, !t.title.startsWith("Напомнить") && t.title.contains("смет", true))
        assertTrue(t.people.none { it == "Саша" })
    }

    @Test fun aLostPromiseIsFoundAgain() {
        val said = "Обещал Анне показать калькулятор до пятницы"
        val d = run(AppData(), """{"actions":[{"type":"create_task","title":"Показать калькулятор Анне","people":["Анна"]}]}""", said)
        assertEquals("Анна", d.tasks.single().promisedTo)
        assertEquals(null, d.tasks.single().waitingFor)
    }

    @Test fun anErrandStaysAnErrand() {
        val d = run(AppData(), """{"actions":[{"type":"create_task","title":"Заказать муку у Тимура","people":["Тимур"]}]}""", "заказать муку у Тимура в понедельник")
        assertEquals(null, d.tasks.single().waitingFor)
        assertEquals(null, d.tasks.single().promisedTo)
    }

    @Test fun theModelsOwnWaitIsKept() {
        val d = run(AppData(), """{"actions":[{"type":"create_task","title":"Смета","waiting_for":"Саша"}]}""", "Саша обещал прислать смету")
        assertEquals("Саша", d.tasks.single().waitingFor)
    }

    @Test fun aLostWaitInALongPhrase() {
        val said = "Саша обещал прислать смету, через пару дней напомнить. Ещё почитать про новый Claude, купить корм коту и подумать над главной vuzburo — мне кажется, нужен калькулятор стоимости и отзывы"
        val d = run(AppData(), """{"actions":[{"type":"create_task","title":"Напомнить Саше о смете","start":"${today.plusDays(2)}","due":"${today.plusDays(2)}","people":["Саша"],"entry":"Саша обещал прислать смету, напомнить через пару дней."},{"type":"create_task","title":"Почитать про новый Claude"},{"type":"create_task","title":"Купить корм коту"}]}""", said)
        val t = d.tasks.first { it.title.contains("смет", true) }
        assertEquals("Саша", t.waitingFor)
        assertEquals("Получить смету", t.title)
    }
}
