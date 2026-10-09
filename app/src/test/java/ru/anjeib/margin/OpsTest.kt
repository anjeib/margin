package ru.anjeib.margin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.anjeib.margin.data.AppData
import ru.anjeib.margin.data.Entry
import ru.anjeib.margin.data.Note
import ru.anjeib.margin.data.Person
import ru.anjeib.margin.data.Repeat
import ru.anjeib.margin.data.Slot
import ru.anjeib.margin.data.Subtask
import ru.anjeib.margin.data.Task
import ru.anjeib.margin.data.Topics
import ru.anjeib.margin.data.addRecord
import ru.anjeib.margin.data.addSlot
import ru.anjeib.margin.data.addDealPeople
import ru.anjeib.margin.data.listTasks
import ru.anjeib.margin.data.questionRow
import ru.anjeib.margin.data.setDealPlace
import ru.anjeib.margin.data.setDealRepeat
import ru.anjeib.margin.data.archivePerson
import ru.anjeib.margin.data.archiveTopic
import ru.anjeib.margin.data.dropTask
import ru.anjeib.margin.data.editEntryText
import ru.anjeib.margin.data.inboxToTopic
import ru.anjeib.margin.data.matterFor
import ru.anjeib.margin.data.mergePersons
import ru.anjeib.margin.data.moveOverdue
import ru.anjeib.margin.data.postponeTo
import ru.anjeib.margin.data.purgeDeal
import ru.anjeib.margin.data.removeDeal
import ru.anjeib.margin.data.removeEntry
import ru.anjeib.margin.data.removeRecord
import ru.anjeib.margin.data.removeSlot
import ru.anjeib.margin.data.renamePerson
import ru.anjeib.margin.data.renameSubtask
import ru.anjeib.margin.data.restoreDeal
import ru.anjeib.margin.data.restoreRecord
import ru.anjeib.margin.data.reviveTopic
import ru.anjeib.margin.data.setTime
import ru.anjeib.margin.data.syncSlots
import ru.anjeib.margin.data.taskToNote
import ru.anjeib.margin.data.toggleSlot
import ru.anjeib.margin.data.toggleSubtask
import ru.anjeib.margin.data.toggleTask
import ru.anjeib.margin.data.topicFor
import java.time.LocalDate

/** What the buttons and gestures do to the data (MatterOps, EntryOps, PeopleOps, Slots in data/) — the same path as Store.update. Offline. */
class OpsTest {
    private val today = LocalDate.now()
    private fun day(n: Long) = today.plusDays(n).toString()

    /** As Store.update does it: the change, then the topic feed lines. */
    private fun AppData.step(f: (AppData) -> AppData): AppData = Topics.track(this, f(this))
    private fun AppData.task(id: String) = tasks.single { it.id == id }
    private fun AppData.sub(topic: String, id: String) = task(topic).subtasks.single { it.id == id }

    @Test fun repeatingMatterLeavesNextOccurrence() {
        val t = Task(id = "r", title = "Полить цветы", due = today.toString(), repeat = Repeat("weekly"))
        val d = AppData(tasks = listOf(t)).step { it.toggleTask("r", today) }
        assertTrue(d.task("r").done)
        assertEquals(today.toString(), d.task("r").doneAt)
        val next = d.tasks.single { it.id != "r" }
        assertFalse(next.done)
        assertEquals(day(7), next.due)
        assertEquals("Полить цветы", next.title)
        assertFalse(next.topic)
        // Opened again: it is open, the next one stays where it is.
        val back = d.step { it.toggleTask("r", today) }
        assertFalse(back.task("r").done)
        assertNull(back.task("r").doneAt)
        assertEquals(2, back.tasks.size)
    }

    @Test fun topicsRepeatingMatterLeavesTheNextOne() {
        val topic = Task(id = "t", title = "Дача", topic = true, subtasks = listOf(Subtask(id = "q", title = "Полить цветы", due = today.toString())))
        val d0 = AppData(tasks = listOf(topic)).step { it.setDealRepeat("t", "q", Repeat("weekly")) }
        assertEquals(Repeat("weekly"), d0.sub("t", "q").repeat)
        val d = d0.step { it.toggleTask("q:t:q", today) }
        assertTrue(d.sub("t", "q").done)
        val next = d.task("t").subtasks.single { it.id != "q" }
        assertEquals(day(7), next.due)
        assertFalse(next.done)
        assertEquals(Repeat("weekly"), next.repeat)
        // Opened and ticked again: still one next matter, not two.
        val again = d.step { it.toggleTask("q:t:q", today) }.step { it.toggleTask("q:t:q", today) }
        assertEquals(2, again.task("t").subtasks.size)
    }

    @Test fun topicsMatterTakesAnotherDayPlaceAndPeople() {
        val topic = Task(id = "t", title = "Ремонт", topic = true, subtasks = listOf(Subtask(id = "q", title = "Встретить мастера", due = day(1), time = "10:00")))
        val d = AppData(tasks = listOf(topic)).step { it.addSlot("q:t:q", day(3)) }
            .step { it.setDealPlace("t", "q", " Ленина, 5 ") }.step { it.addDealPeople("t", "q", listOf("Олег", "Маша", "Олег")) }
        val q = d.sub("t", "q")
        assertEquals(listOf(day(1), day(3)), q.slots.map { it.date })
        assertEquals(day(1), q.due)
        assertEquals("Ленина, 5", q.place)
        assertEquals(listOf("Олег", "Маша"), q.people)
        // The day lists see them the way they see a simple matter's.
        val row = d.task("t").questionRow(q)
        assertEquals("Ленина, 5", row.place)
        assertEquals(listOf("Олег", "Маша"), row.people)
        assertEquals(2, d.listTasks().count { it.id.startsWith("q:t:q@") })
        // One day less: a plain date again.
        val one = d.step { it.removeSlot("q:t:q", q.slots.last().id) }
        assertTrue(one.sub("t", "q").slots.isEmpty())
        assertEquals(day(1), one.sub("t", "q").due)
    }

    @Test fun droppedRepeatingMatterComesBack() {
        val t = Task(id = "r", title = "Отчёт", due = today.toString(), repeat = Repeat("monthly"))
        val d = AppData(tasks = listOf(t)).step { it.dropTask("r", today) }
        assertTrue(d.task("r").done && d.task("r").dropped)
        assertEquals(today.plusMonths(1).toString(), d.tasks.single { it.id != "r" }.due)
        // Already closed: nothing to drop.
        assertSame(d, d.dropTask("r", today))
    }

    @Test fun topicMatterDoneAndReceived() {
        val topic = Task(id = "p", title = "Ремонт", topic = true, subtasks = listOf(
            Subtask(id = "a", title = "Купить плитку", due = day(1)),
            Subtask(id = "w", title = "Смета", from = "Олег", due = day(2)),
        ))
        var d = AppData(tasks = listOf(topic)).step { it.toggleTask("q:p:a", today) }
        assertTrue(d.sub("p", "a").done)
        assertEquals(today.toString(), d.sub("p", "a").doneAt)
        assertFalse(d.task("p").done)
        assertEquals("solved" to "a", d.task("p").events.last().let { it.kind to it.ref })
        // «Ждём от Олега» closes as received.
        d = d.step { it.toggleTask("q:p:w", today) }
        assertEquals("got" to "w", d.task("p").events.last().let { it.kind to it.ref })
        // Ticked again — open, without the day it was settled.
        d = d.step { it.toggleTask("q:p:a", today) }
        assertFalse(d.sub("p", "a").done)
        assertNull(d.sub("p", "a").doneAt)
        // The same from the card.
        d = d.step { it.toggleSubtask("p", "a", today) }
        assertTrue(d.sub("p", "a").done)
    }

    @Test fun postponeMovesMatterRangeAndTopicMatter() {
        val range = Task(id = "t", title = "Отчёт", start = day(-1), due = day(1), time = "10:00")
        val undated = Task(id = "u", title = "Разобрать шкаф")
        val topic = Task(id = "p", title = "Ремонт", topic = true, subtasks = listOf(Subtask(id = "a", title = "Замер", due = day(1), time = "09:00")))
        val target = today.plusDays(5)
        var d = AppData(tasks = listOf(range, undated, topic)).step { it.postponeTo("t", target, today) }
        // A range keeps its length.
        assertEquals(target.toString(), d.task("t").due)
        assertEquals(day(3), d.task("t").start)
        assertEquals("${target}T10:00", d.task("t").remindAt)
        assertEquals(1, d.task("t").postponed)
        d = d.step { it.postponeTo("u", target, today) }
        assertEquals(target.toString(), d.task("u").due)
        assertNull(d.task("u").start)
        // A topic's matter keeps its hour.
        d = d.step { it.postponeTo("q:p:a", target, today) }
        assertEquals(target.toString(), d.sub("p", "a").due)
        assertEquals("09:00", d.sub("p", "a").time)
        // Gone — nothing changes.
        assertSame(d, d.postponeTo("nope", target, today))
    }

    @Test fun setTimeGivesADay() {
        val t = Task(id = "t", title = "Позвонить")
        val p = Task(id = "p", title = "Ремонт", topic = true, subtasks = listOf(Subtask(id = "a", title = "Замер")))
        val d = AppData(tasks = listOf(t, p)).step { it.setTime("t", "15:00", today) }.step { it.setTime("q:p:a", "11:00", today) }
        assertEquals(today.toString(), d.task("t").due)
        assertEquals("15:00", d.task("t").time)
        assertEquals("${today}T15:00", d.task("t").remindAt)
        assertFalse(d.task("t").topic)
        assertEquals(today.toString(), d.sub("p", "a").due)
        assertEquals("11:00", d.sub("p", "a").time)
    }

    @Test fun overdueMovesToToday() {
        val late = Task(id = "l", title = "Счёт", due = day(-3), time = "12:00")
        val soon = Task(id = "s", title = "Звонок", due = day(2))
        val d = AppData(tasks = listOf(late, soon)).step { it.moveOverdue(today) }
        assertEquals(today.toString(), d.task("l").due)
        assertEquals("${today}T12:00", d.task("l").remindAt)
        assertEquals(1, d.task("l").postponed)
        assertEquals(day(2), d.task("s").due)
    }

    @Test fun slotsOfAMatterAndOfATopicMatter() {
        val t = Task(id = "t", title = "Тренировка", slots = listOf(Slot(id = "a", date = day(1)), Slot(id = "b", date = day(4), time = "19:00"))).syncSlots()
        val topic = Task(id = "p", title = "Ремонт", topic = true,
            subtasks = listOf(Subtask(id = "q", title = "Замер", slots = listOf(Slot(id = "x", date = day(1)), Slot(id = "y", date = day(2)))).syncSlots()))
        var d = AppData(tasks = listOf(t, topic)).step { it.toggleSlot("t", "a") }
        assertEquals(day(4), d.task("t").due)
        assertEquals("19:00", d.task("t").time)
        assertFalse(d.task("t").done)
        d = d.step { it.toggleSlot("t", "b") }
        assertTrue(d.task("t").done)
        // A topic's matter: the row is «q:<topic>:<matter>».
        d = d.step { it.toggleSlot("q:p:q", "x") }
        assertEquals(day(2), d.sub("p", "q").due)
        assertFalse(d.sub("p", "q").done)
        // One day moved, then its hour set — the matter follows its next open day.
        d = d.step { it.postponeTo("q:p:q@y", today.plusDays(6), today) }
        assertEquals(day(6), d.sub("p", "q").due)
        d = d.step { it.setTime("q:p:q@y", "08:30", today) }
        assertEquals("08:30", d.sub("p", "q").time)
        // «+ день» turns the single date into the first day; removing down to one makes it a plain date again.
        var e = AppData(tasks = listOf(Task(id = "s", title = "Звонок", due = day(1), time = "12:00"))).step { it.addSlot("s", day(3)) }
        assertEquals(listOf(day(1), day(3)), e.task("s").slots.map { it.date })
        assertEquals("12:00", e.task("s").slots.first().time)
        assertEquals(day(1), e.task("s").due)
        e = e.step { it.removeSlot("s", e.task("s").slots.first().id) }
        assertTrue(e.task("s").slots.isEmpty())
        assertEquals(day(3), e.task("s").due)
    }

    @Test fun archiveAndReviveTopic() {
        val p = Task(id = "p", title = "Ремонт", topic = true, subtasks = listOf(Subtask(id = "a", title = "Замер", done = true)))
        var d = AppData(tasks = listOf(p)).step { it.archiveTopic("p", today) }
        assertTrue(d.task("p").done)
        assertEquals(today.toString(), d.task("p").doneAt)
        d = d.step { it.reviveTopic("p") }
        assertFalse(d.task("p").done)
        assertNull(d.task("p").doneAt)
        assertEquals("revived", d.task("p").events.last().kind)
        // News in an archived topic brings it back by itself.
        d = d.step { it.archiveTopic("p", today) }.step { it.addRecord("p", "новость по смете") }
        assertFalse(d.task("p").done)
        assertEquals("Новость по смете", d.task("p").entries.last().text)
    }

    @Test fun removeAndRestoreRecordsAndMatters() {
        val e1 = Entry(id = "e1", at = "${day(-2)}T10:00", text = "Первая")
        val e2 = Entry(id = "e2", at = "${day(-1)}T10:00", text = "Вторая")
        val p = Task(id = "p", title = "Ремонт", topic = true, entries = listOf(e1, e2), subtasks = listOf(
            Subtask(id = "a", title = "Замер"), Subtask(id = "b", title = "Позвонить замерщику", parent = "a"), Subtask(id = "c", title = "Плитка"),
        ))
        var d = AppData(tasks = listOf(p)).step { it.removeRecord("p", "e1") }
        assertEquals(listOf("e2"), d.task("p").entries.map { it.id })
        assertEquals(listOf("e1"), d.task("p").removedEntries.map { it.id })
        // Back in its place by time.
        d = d.step { it.restoreRecord("p", "e1") }
        assertEquals(listOf("e1", "e2"), d.task("p").entries.map { it.id })
        assertTrue(d.task("p").removedEntries.isEmpty())
        // A matter goes with what hangs under it, and comes back the same way.
        d = d.step { it.removeDeal("p", "a") }
        assertEquals(listOf("c"), d.task("p").subtasks.map { it.id })
        assertEquals(setOf("a", "b"), d.task("p").removed.map { it.id }.toSet())
        d = d.step { it.restoreDeal("p", "a") }
        assertEquals(setOf("a", "b", "c"), d.task("p").subtasks.map { it.id }.toSet())
        assertTrue(d.task("p").removed.isEmpty())
        d = d.step { it.removeDeal("p", "c") }.step { it.purgeDeal("p", "c") }
        assertTrue(d.task("p").removed.isEmpty())
        assertEquals(setOf("a", "b"), d.task("p").subtasks.map { it.id }.toSet())
        // A blank title removes a step.
        d = d.step { it.renameSubtask("p", "b", " ") }
        assertEquals(listOf("a"), d.task("p").subtasks.map { it.id })
    }

    @Test fun blocksOfATaskAndANote() {
        val note = Note(id = "n", title = "Идеи", entries = listOf(Entry(id = "x", text = "Раз"), Entry(id = "y", text = "Два")))
        val t = Task(id = "t", title = "Отчёт", entries = listOf(Entry(id = "z", text = "Черновик")))
        var d = AppData(tasks = listOf(t), notes = listOf(note))
        d = d.editEntryText("task:t", "z", "готово к отправке")
        assertEquals("Готово к отправке", d.task("t").entries.single().text)
        // Blank text, no files: the block goes.
        d = d.editEntryText("task:t", "z", "")
        assertTrue(d.task("t").entries.isEmpty())
        d = d.removeEntry("note:n", "x")
        assertEquals(listOf("y"), d.notes.single().entries.map { it.id })
    }

    @Test fun inboxBlockGoesToTopic() {
        val inbox = Note(id = "in", title = ru.anjeib.margin.i18n.Ru.inboxNote,
            entries = listOf(Entry(id = "x", at = "${day(-1)}T09:00", text = "Фото сметы", session = "s1")))
        val p = Task(id = "p", title = "Ремонт", topic = true, entries = listOf(Entry(id = "e", at = "${today}T10:00", text = "Созвонились")))
        val d = AppData(tasks = listOf(p), notes = listOf(inbox)).step { it.inboxToTopic("in", "x", "p") }
        assertTrue(d.notes.single().entries.isEmpty())
        assertEquals(listOf("x", "e"), d.task("p").entries.map { it.id })
        assertNull(d.task("p").entries.first().session)
    }

    @Test fun taskBecomesANoteWithItsSteps() {
        val t = Task(id = "t", title = "Сделать сайт", entries = listOf(Entry(text = "На Тильде")), subtasks = listOf(Subtask(title = "Макет")))
        val (d, noteId) = AppData(tasks = listOf(t)).taskToNote("t", "идеи")!!
        assertTrue(d.tasks.isEmpty())
        val n = d.notes.single { it.id == noteId }
        assertEquals("Сделать сайт", n.title)
        assertEquals(listOf("Макет"), n.entries.last().items.map { it.title })
    }

    @Test fun mergeRenameAndArchivePeople() {
        val a = Person(id = "a", name = "Олег", phone = "+7 900")
        val b = Person(id = "b", name = "Олег Петров", email = "o@x.ru", aliases = listOf("Петров"))
        val t = Task(id = "t", title = "Смета", people = listOf("Олег Петров"), waitingFor = "Петров")
        var d = AppData(tasks = listOf(t), persons = listOf(a, b)).step { it.mergePersons("b", "a") }
        val p = d.persons.single()
        assertEquals("Олег", p.name)
        assertEquals(listOf("Олег Петров", "Петров"), p.aliases)
        assertEquals("+7 900" to "o@x.ru", p.phone to p.email)
        assertEquals(listOf("Олег"), d.task("t").people)
        assertEquals("Олег", d.task("t").waitingFor)
        d = d.step { it.renamePerson("a", "Олег Сидоров") }
        assertEquals("Олег Сидоров", d.persons.single().name)
        assertTrue("Олег" in d.persons.single().aliases)
        assertEquals("Олег Сидоров", d.task("t").waitingFor)
        d = d.step { it.archivePerson("a") }
        assertTrue(d.persons.single().archived)
        d = d.step { it.archivePerson("a", on = false) }
        assertFalse(d.persons.single().archived)
    }

    @Test fun fileWithTheOldBriefStillLoads() {
        val json = """{"tasks":[{"id":"t","title":"Отчёт"}],"brief":{"date":"2026-10-01","text":"Главное — отчёт","revisions":2},""" +
            """"projectSummaries":{"сайт":{"text":"Ждём макет","at":"2026-10-01T09:00"}},"v":1}"""
        val d = ru.anjeib.margin.data.AppJson.decodeFromString(AppData.serializer(), json)
        assertEquals("Отчёт", d.task("t").title)
        assertEquals(1, d.v)
    }

    @Test fun marksFindTopicAndMatter() {
        val p = Task(id = "p", title = "Ремонт кухни", topic = true, subtasks = listOf(Subtask(id = "a", title = "Купить плитку")))
        val call = Task(id = "c", title = "Позвонить в банк")
        val d = AppData(tasks = listOf(p, call))
        assertEquals("p", d.topicFor("ремонт")?.id)
        assertEquals("p", d.topicFor("[p]")?.id)
        assertEquals("q:p:a" to "Купить плитку", d.matterFor("плитку купили", null))
        assertEquals("c", d.matterFor("позвонил в банк", null)?.first)
        assertNull(d.matterFor("банк", "p"))
        // EmbeddingGemma test, 9 окт: «для» alone sent «подарок для Кати» to «Макет упаковки для типографии».
        val gift = AppData(tasks = listOf(Task(id = "m", title = "Макет упаковки для типографии"), Task(id = "g", title = "Подарок Кате на день рождения")))
        assertEquals("g", gift.matterFor("подарок для Кати купила", null)?.first)
        assertNull(gift.matterFor("для", null))
    }
}
