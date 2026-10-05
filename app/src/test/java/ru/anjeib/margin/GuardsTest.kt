package ru.anjeib.margin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.anjeib.margin.ai.Assistant
import ru.anjeib.margin.data.AppData
import ru.anjeib.margin.data.Repeat
import ru.anjeib.margin.data.Task
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
}
