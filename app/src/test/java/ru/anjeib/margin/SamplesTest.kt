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

/**
 * The first page (owner, 16 окт): «Курс освоения Margin» — examples that tell how Margin works and cover topics, matters, steps,
 * notes, people, a company and files; ordinary records, not marked as examples.
 */
class SamplesTest {
    private val set = Samples.build()

    @Test fun aCourseThatShowsEachThing() {
        val t = set.tasks
        assertTrue("ordinary records", t.none { it.sample } && set.persons.none { it.sample })
        val topic = t.single { it.topic && !it.thought }
        assertEquals("Курс освоения Margin", topic.title)
        assertTrue("a wait in the topic", topic.subtasks.any { it.from != null })
        assertTrue("a matter under a matter", topic.subtasks.any { q -> q.parent != null && topic.subtasks.any { it.id == q.parent } })
        assertTrue("a matter from a note", topic.subtasks.any { q -> q.parent != null && topic.entries.any { it.id == q.parent } })
        assertTrue("a topic matter for today", topic.subtasks.any { it.due == java.time.LocalDate.now().toString() && it.time != null })
        assertTrue("notes in the topic", topic.entries.size >= 3)
        assertEquals(1, t.count { it.thought })
        val simple = t.filter { !it.topic }
        assertTrue("an hour", simple.any { it.time != null && it.repeat == null })
        assertTrue("late", simple.any { !it.done && it.dueDate?.isBefore(java.time.LocalDate.now()) == true })
        assertTrue("a repeat with a place", simple.any { it.repeat != null && it.place != null })
        assertTrue("several days", simple.any { it.slots.size >= 2 })
        assertTrue("a window", simple.any { it.startDate != null && it.dueDate != null && it.startDate!!.isBefore(it.dueDate) })
        assertTrue("a promise", simple.any { it.promisedTo != null })
        assertTrue("a wait", simple.any { it.waitingFor != null })
        assertTrue("no date", simple.any { it.dueDate == null && it.startDate == null && it.slots.isEmpty() && !it.done })
        assertTrue("done", simple.any { it.done && !it.dropped })
        assertTrue("not doing it", simple.any { it.dropped })
        assertTrue("a contact, a company, the mentioned", set.persons.any { !it.org && it.isContact } && set.persons.any { it.org } && set.persons.any { it.draft })
        assertTrue(simple.size <= 10)
        // The day lists see the course's lesson for today.
        assertTrue(set.listTasks().any { it.id == "q:ex-course:ex-c2" })
        // Files land in the notes when there are any.
        val f = ru.anjeib.margin.data.Attachment(kind = "file", name = "x.pdf")
        assertTrue(Samples.build(sheet = f, plan = f, photo = f).tasks.single { it.topic && !it.thought }.entries.count { it.attachments.isNotEmpty() } == 3)
    }

    @Test fun theCourseDoesNotDoubleTheUsersOwn() {
        L.lang = Lang.Ru
        val mine = Task(id = "m", title = "Купить хлеб")
        val myKatya = Person(name = "Катя", phone = "+7 900 000-00-00", kept = true)
        val d = AppData(tasks = listOf(mine), persons = listOf(myKatya)).withSamples(set)
        assertEquals(1, d.persons.count { it.matches("Катя") })
        assertTrue(d.tasks.contains(mine))
        // Old marked examples are replaced by the course once; the course itself is not marked.
        val old = AppData(tasks = listOf(Task(id = "old", title = "Пример", sample = true))).withSamples(set)
        assertTrue(old.tasks.none { it.id == "old" } && !old.hasSamples())
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
