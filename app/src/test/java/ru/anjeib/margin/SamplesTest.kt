package ru.anjeib.margin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.anjeib.margin.ai.LocalParser
import ru.anjeib.margin.data.AppData
import ru.anjeib.margin.data.Person
import ru.anjeib.margin.data.Samples
import ru.anjeib.margin.data.Task
import ru.anjeib.margin.data.hasSamples
import ru.anjeib.margin.data.likelyTopic
import ru.anjeib.margin.data.listTasks
import ru.anjeib.margin.data.withSamples
import ru.anjeib.margin.data.withoutSamples
import ru.anjeib.margin.i18n.Lang
import ru.anjeib.margin.i18n.L

/** «Примеры» from the designer's demo: one of each thing, come in marked, leave in one go, never mix with the user's own. */
class SamplesTest {
    private val set = Samples.build()

    @Test fun oneExampleOfEachThing() {
        val t = set.tasks
        assertTrue(t.all { it.sample } && set.persons.all { it.sample })
        val topic = t.single { it.topic && !it.thought }
        assertTrue("a wait in the topic", topic.subtasks.any { it.from != null })
        assertTrue("a matter under a matter", topic.subtasks.any { q -> q.parent != null && topic.subtasks.any { it.id == q.parent } })
        assertTrue("a matter from a record", topic.subtasks.any { q -> q.parent != null && topic.entries.any { it.id == q.parent } })
        assertTrue("a topic matter for today", topic.subtasks.any { it.due == java.time.LocalDate.now().toString() && it.time != null })
        assertEquals(1, t.count { it.thought })
        val simple = t.filter { !it.topic }
        assertTrue("an hour", simple.any { it.time != null && it.repeat == null })
        assertTrue("late", simple.any { !it.done && it.dueDate?.isBefore(java.time.LocalDate.now()) == true })
        assertTrue("a repeat with a place", simple.any { it.repeat != null && it.place != null })
        assertTrue("several days", simple.any { it.slots.size >= 2 })
        assertTrue("a promise", simple.any { it.promisedTo != null })
        assertTrue("a wait", simple.any { it.waitingFor != null })
        assertTrue("no date", simple.any { it.dueDate == null && it.startDate == null && it.slots.isEmpty() && !it.done })
        assertTrue("done", simple.any { it.done && !it.dropped })
        assertTrue("not doing it", simple.any { it.dropped })
        assertTrue("a person and a business", set.persons.any { !it.org } && set.persons.any { it.org })
        // Each thing once: no two simple matters of the same kind of example.
        assertEquals(simple.size, simple.map { it.id }.toSet().size)
        assertTrue(simple.size <= 10)
        // The day lists see them: the topic's matter for today, the matter with an hour.
        assertTrue(set.listTasks().any { it.id == "q:ex-coffee:ex-c2" })
    }

    @Test fun examplesComeAndGoWithoutTouchingTheUsers() {
        L.lang = Lang.Ru
        val mine = Task(id = "m", title = "Купить хлеб")
        val myOleg = Person(name = "Олег", phone = "+7 900 000-00-00", kept = true)
        val d = AppData(tasks = listOf(mine), persons = listOf(myOleg)).withSamples(set)
        assertTrue(d.hasSamples())
        // The user's own Олег is not doubled by the example one.
        assertEquals(1, d.persons.count { it.matches("Олег") })
        // Back again: a fresh set in place of the old, never twice.
        assertEquals(d.tasks.size, d.withSamples(Samples.build()).tasks.size)
        assertEquals(d.persons.size, d.withSamples(Samples.build()).persons.size)
        val gone = d.withoutSamples()
        assertFalse(gone.hasSamples())
        assertEquals(listOf(mine), gone.tasks)
        assertEquals(listOf(myOleg), gone.persons)
        // «к теме …?» never points into the examples.
        assertNull(d.likelyTopic("Олег прислал фидбек по упаковке для кофейни", listOf("Олег")))
    }

    /** «что я обещал Лёше?» — the promise reaches the AI (examples are ordinary matters to it). */
    @Test fun aPersonsPromisesReachTheAssistant() {
        L.lang = Lang.Ru
        val d = AppData().withSamples(set)
        val ctx = ru.anjeib.margin.ai.Assistant.context(d, ru.anjeib.margin.data.Settings(), query = "что я обещал Лёше?")
        assertTrue(ctx, ctx.contains("Исходники баннеров") && ctx.contains("promised to Лёша"))
    }

    @Test fun aQuestionIsNotAMatter() {
        L.lang = Lang.Ru
        val d = AppData()
        val q = LocalParser.parse("что горит на этой неделе?", d)
        assertEquals("question", q.why); assertTrue(q.items.isEmpty())
        assertEquals("question", LocalParser.parse("Когда я обещал Лёше макет?", d).why)
        // An order stays an order, even the one about a contract.
        assertEquals(1, LocalParser.parse("Подготовь шаблон договора", d).items.size)
        LocalParser.parse("Когда приедет Дима, отдать ключи", d).let { assertTrue(it.items.isNotEmpty() && it.why != "question") }
    }

    @Test fun noIdsInWhatTheAssistantSays() {
        assertEquals("1) «Список гостей» ждёт ответа от Анны, срок 11 октября.",
            ru.anjeib.margin.ai.Assistant.clean("1) «Список гостей» (subtask_id=intro-q2) ждёт ответа от Анны, срок 11 октября."))
        assertEquals("Перенесла «Стоматолог».", ru.anjeib.margin.ai.Assistant.clean("Перенесла «Стоматолог» [task_id: demo-d7]."))
    }
}
