package ru.anjeib.margin

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.anjeib.margin.ai.AiPlan
import ru.anjeib.margin.ai.Assistant
import ru.anjeib.margin.data.AppData
import ru.anjeib.margin.data.likelyTopic
import ru.anjeib.margin.data.weekReview
import ru.anjeib.margin.data.Entry
import ru.anjeib.margin.data.Task
import ru.anjeib.margin.data.Topics
import ru.anjeib.margin.data.listTasks
import ru.anjeib.margin.data.noNextStep
import java.time.LocalDate

/** v2 «Темы»: a task grows into a topic on its second event, and the feed lines are written by the app. Offline. */
class TopicsTest {
    private val today = LocalDate.now()
    private fun o(vararg p: Pair<String, Any>): JsonObject = JsonObject(p.associate { (k, v) ->
        k to when (v) { is List<*> -> JsonArray(v.map { it as JsonObject }); else -> JsonPrimitive(v.toString()) }
    })
    private fun step(d: AppData, text: String, focus: String, vararg actions: JsonObject): AppData =
        Topics.track(d, Assistant.apply(d, AiPlan(null, actions.toList(), null), text, strict = true, focus = focus).data)

    @Test fun callBecomesTopic() {
        val call = Task(title = "Позвонить Игорю насчёт приказа о зачислении", due = today.plusDays(1).toString(), people = listOf("Игорь"))
        var d = AppData(tasks = listOf(call))
        val said = "Созвонились. Приказ о зачислении в работе, будет через 2 недели. Нужно отправить документы об оплате в бухгалтерию."
        d = step(d, said, call.id,
            o("type" to "add_entry", "task_id" to call.id, "text" to said),
            o("type" to "complete_task", "task_id" to call.id),
            o("type" to "add_subtasks", "task_id" to call.id, "subtasks" to listOf(
                o("title" to "Отправить документы об оплате в бухгалтерию"),
                o("title" to "Приказ о зачислении", "from" to "Игорь", "due" to today.plusDays(14).toString()),
            )),
            o("type" to "update_task", "task_id" to call.id, "topic_title" to "Приказ о зачислении"),
        )
        val t = d.tasks.single()
        assertTrue(t.topic)
        assertFalse(t.done)
        assertEquals("Приказ о зачислении", t.title)
        assertEquals(null, t.due)
        assertEquals(3, t.subtasks.size)
        val first = t.subtasks.first()
        assertEquals("Позвонить Игорю насчёт приказа о зачислении", first.title)
        assertTrue(first.done)
        assertEquals("Игорь", t.subtasks.last().from)
        val kinds = t.events.map { it.kind }
        assertTrue(kinds.containsAll(listOf("q", "became", "solved", "wait")))
        // Under the words: became, settled, the new question, the wait.
        assertTrue(t.events.filter { it.kind != "q" || it.ref != first.id }.all { it.entry == t.entries.single().id })
        // In the lists: the dated wait is a row linked to its topic; the topic itself isn't.
        val rows = d.listTasks()
        assertEquals(1, rows.size)
        assertEquals(t.id, rows.single().topicId)
        assertEquals("Игорь", rows.single().waitingFor)
    }

    @Test fun lastQuestionSettledOffersArchive() {
        val t0 = Task(title = "Приказ о зачислении", topic = true, subtasks = listOf(ru.anjeib.margin.data.Subtask(title = "Документы", done = false)))
        var d = AppData(tasks = listOf(t0))
        d = step(d, "документы отправил", t0.id, o("type" to "complete_subtask", "task_id" to t0.id, "subtask_id" to t0.subtasks[0].id))
        val t = d.tasks.single()
        assertTrue(t.noNextStep)
        assertEquals("solved", t.events.last().kind)
    }

    @Test fun movingATopicMovesItsQuestion() {
        val q = ru.anjeib.margin.data.Subtask(title = "Стоматолог", due = today.toString(), time = "18:00")
        val t0 = Task(title = "Зубы", topic = true, subtasks = listOf(q))
        val fri = today.with(java.time.temporal.TemporalAdjusters.next(java.time.DayOfWeek.FRIDAY)).toString()
        val d = step(AppData(tasks = listOf(t0)), "перенеси на пятницу в 15:00", t0.id, o("type" to "update_task", "task_id" to t0.id, "due" to fri, "time" to "15:00"))
        val t = d.tasks.single()
        assertEquals(null, t.due)
        assertEquals(fri, t.subtasks.single().due)
        assertEquals("15:00", t.subtasks.single().time)
    }

    @Test fun plainNoteDoesNotTurnNewTaskIntoTopic() {
        val d0 = AppData()
        val d = Topics.track(d0, d0.copy(tasks = listOf(Task(title = "Купить молоко", entries = listOf(Entry(text = "2,5%"))))))
        assertFalse(d.tasks.single().topic)
    }

    @Test fun archivedTopicComesBackWithNews() {
        val t0 = Task(title = "Приказ", topic = true, done = true, doneAt = today.toString(), subtasks = listOf(ru.anjeib.margin.data.Subtask(title = "x", done = true)))
        val d0 = AppData(tasks = listOf(t0))
        val d = Topics.track(d0, d0.copy(tasks = listOf(t0.copy(entries = listOf(Entry(text = "Игорь перезвонил"))))))
        assertFalse(d.tasks.single().done)
        assertEquals("revived", d.tasks.single().events.last().kind)
    }

    @Test fun notesBecomeThoughts() {
        val d = AppData(notes = listOf(ru.anjeib.margin.data.Note(title = "Посмотреть «Одиссею» Нолана", entries = listOf(Entry(text = "Дима: только в IMAX"))))).migrated()
        assertTrue(d.notes.isEmpty())
        val t = d.tasks.single()
        assertTrue(t.thought && t.topic)
        assertEquals("Посмотреть «Одиссею» Нолана", t.title)
        assertEquals(0, d.listTasks().size)
    }

    @Test fun aiNoteActionsLandInThoughts() {
        val th = Task(title = "Идеи для сайта", topic = true, thought = true, project = "сайт", entries = listOf(Entry(text = "Калькулятор на главной")))
        var d = AppData(tasks = listOf(th))
        d = Topics.track(d, Assistant.apply(d, AiPlan(null, listOf(o("type" to "append_note", "note_id" to th.id, "text" to "Отзывы с фото")), null), "идея для сайта: отзывы с фото", strict = true).data)
        assertEquals(2, d.tasks.single().entries.size)
        d = Topics.track(d, Assistant.apply(d, AiPlan(null, listOf(o("type" to "create_note", "text" to "Посмотреть Одиссею")), null), "запиши: посмотреть одиссею", strict = true).data)
        assertTrue(d.notes.isEmpty())
        assertEquals(2, d.tasks.count { it.thought })
    }

    @Test fun reportClosesTheCall() {
        val call = Task(title = "Позвонить в ЖЭК насчёт счётчиков", due = today.toString())
        val said = "Сказали прийти с паспортом во вторник до 13:00"
        val d = step(AppData(tasks = listOf(call)), said, call.id,
            o("type" to "add_entry", "task_id" to call.id, "text" to said),
            o("type" to "add_subtasks", "task_id" to call.id, "subtasks" to listOf(o("title" to "Прийти в ЖЭК с паспортом", "due" to today.plusDays(3).toString(), "time" to "13:00"))))
        val t = d.tasks.single()
        assertTrue(t.topic && !t.done)
        assertTrue(t.subtasks.first().done)
        assertEquals("13:00", t.subtasks.last().time)
    }

    @Test fun renamedAndDoneWithoutWordsStillBecomesTopic() {
        val call = Task(title = "Позвонить Игорю по приказу о зачислении", due = today.plusDays(1).toString())
        val said = "Созвонились, приказ будет через 2 недели"
        val d = step(AppData(tasks = listOf(call)), said, call.id,
            o("type" to "update_task", "task_id" to call.id, "topic_title" to "Приказ о зачислении"),
            o("type" to "complete_task", "task_id" to call.id))
        val t = d.tasks.single()
        assertTrue(t.topic && !t.done)
        assertEquals("Приказ о зачислении", t.title)
        assertEquals(said, t.entries.single().text)
        assertTrue(t.subtasks.single().done)
    }

    @Test fun matterWithStepsIsOneTopic() {
        val tomorrow = today.plusDays(1).toString()
        val d = Topics.track(AppData(), Assistant.apply(AppData(), AiPlan(null, listOf(
            o("type" to "create_task", "title" to "Позвонить Игорю", "due" to tomorrow),
            o("type" to "create_task", "title" to "Отправить документы об оплате в бухгалтерию"),
        ), null), "Приказ о зачислении: позвонить Игорю завтра и отправить документы об оплате в бухгалтерию", strict = true).data)
        val t = d.tasks.single()
        assertEquals("Приказ о зачислении", t.title)
        assertTrue(t.topic)
        assertEquals(2, t.subtasks.size)
        assertEquals(tomorrow, t.subtasks.first().due)
    }

    @Test fun commandColonIsNotAMatter() {
        val d = Assistant.apply(AppData(), AiPlan(null, listOf(
            o("type" to "create_task", "title" to "Купить хлеб"), o("type" to "create_task", "title" to "Купить молоко"),
        ), null), "Завтра в 15: купить хлеб и молоко", strict = true).data
        assertEquals(2, d.tasks.size)
    }

    /** «Похоже, это к теме Зерно»: by the topic's people and words; nothing when no topic stands out. */
    @Test fun likelyTopic() {
        val zerno = Task(id = "z", title = "Айдентика для кофейни «Зерно»", topic = true, people = listOf("Олег"),
            subtasks = listOf(ru.anjeib.margin.data.Subtask(title = "Макет упаковки для типографии")))
        val flat = Task(id = "f", title = "Квартира поближе к студии", topic = true, people = listOf("Ирина"))
        val d = AppData(tasks = listOf(zerno, flat))
        assertEquals("z", d.likelyTopic("Олегу отправить счёт за упаковку", listOf("Олег"))?.id)
        assertEquals("z", d.likelyTopic("распечатать макет упаковки для типографии")?.id)
        assertEquals("f", d.likelyTopic("Ирина пришлёт варианты квартир", listOf("Ирина"))?.id)
        assertEquals(null, d.likelyTopic("купить хлеб"))
        assertEquals(null, d.likelyTopic("Олегу отправить счёт за упаковку", listOf("Олег"), except = setOf("z")))
        // Fable, 9 окт: Маша is in the trip, the book is not — a name alone suggests nothing.
        val trip = Task(id = "t", title = "Поездка в Питер на выходные", topic = true, people = listOf("Маша"))
        assertEquals(null, AppData(tasks = listOf(trip)).likelyTopic("Маше вернуть книгу в субботу", listOf("Маша")))
        assertEquals("t", AppData(tasks = listOf(trip)).likelyTopic("Маша купит билеты на поездку", listOf("Маша"))?.id)
    }

    /** Sunday's card: done this week, who owes whom, topics that stood still for a week. */
    @Test fun weekReview() {
        val sun = java.time.LocalDate.of(2026, 10, 11)
        val d = AppData(tasks = listOf(
            Task(title = "Купить хлеб", done = true, doneAt = "2026-10-07"),
            Task(title = "Старое", done = true, doneAt = "2026-10-01"),
            Task(title = "Получить макет от Димы", waitingFor = "Дима"),
            Task(title = "Отправить счёт", promisedTo = "Олег"),
            Task(id = "z", title = "Ремонт", topic = true, createdAt = "2026-09-20", subtasks = listOf(
                ru.anjeib.margin.data.Subtask(title = "Плитка", done = true, doneAt = "2026-10-06"),
                ru.anjeib.margin.data.Subtask(title = "Мастер"),
                ru.anjeib.margin.data.Subtask(title = "Смета", from = "Олег"))),
        ))
        val w = d.weekReview(sun)
        assertEquals(2, w.done)
        assertEquals(2, w.owedToYou)
        assertEquals(1, w.youOwe)
        assertEquals(listOf<String>(), w.still.map { it.id })
        assertEquals(listOf("z"), d.weekReview(sun.plusDays(7)).still.map { it.id })
    }

    // Owner, 12 окт: «Сделай тему Правки к приложению» became a matter; a step written in it made a topic that held
    // the same matter again. An undated name growing by steps names the topic only.
    @Test fun aNameGrowingBySteps() {
        val name = Task(title = "Правки к приложению")
        val d0 = AppData(tasks = listOf(name))
        val d = step(d0, "проверить тексты", name.id, o("type" to "add_subtasks", "task_id" to name.id, "subtasks" to listOf(o("title" to "Проверить тексты"))))
        val t = d.tasks.single { it.id == name.id }
        assertTrue(t.topic)
        assertEquals(listOf("Проверить тексты"), t.subtasks.map { it.title })
    }

    @Test fun aDatedMatterStaysTheFirstStep() {
        val call = Task(title = "Позвонить в банк", due = today.plusDays(1).toString())
        val d0 = AppData(tasks = listOf(call))
        val d = step(d0, "уточнить лимит", call.id, o("type" to "add_subtasks", "task_id" to call.id, "subtasks" to listOf(o("title" to "Уточнить лимит"))))
        assertEquals(listOf("Позвонить в банк", "Уточнить лимит"), d.tasks.single { it.id == call.id }.subtasks.map { it.title })
    }
}
