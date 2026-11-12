package ru.anjeib.margin

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import ru.anjeib.margin.data.*
import java.io.File
import java.time.LocalDate

class TopicCompletionTest {
    @get:Rule val tmp = TemporaryFolder()
    private val today = LocalDate.now().toString()
    private fun change(before: Task, after: Task): Task = Topics.track(
        AppData(tasks = listOf(before)), AppData(tasks = listOf(after)),
    ).tasks.single()

    @Test fun lastMatterCompletesTopicAndPersists() = runBlocking {
        val store = Store(File(tmp.root, "margin.json"))
        val q = Subtask(title = "Отправить договор")
        val t = Task(title = "Договор", topic = true, subtasks = listOf(q))
        store.update { it.copy(tasks = listOf(t)) }
        store.update { it.editTask(t.id) { it.copy(subtasks = listOf(q.copy(done = true))) } }
        store.flush()
        val saved = Store(File(tmp.root, "margin.json")).data.value.tasks.single()
        assertTrue(saved.done)
        assertEquals(today, saved.doneAt)
        assertEquals(today, saved.subtasks.single().doneAt)
        assertEquals("solved", saved.events.last().kind)
    }

    @Test fun waitingForSomeoneKeepsTopicOpen() {
        val q = Subtask(title = "Отправить договор")
        val wait = Subtask(title = "Получить подпись", from = "Анна")
        val t = Task(title = "Договор", topic = true, subtasks = listOf(q, wait))
        val pending = change(t, t.copy(subtasks = listOf(q.copy(done = true), wait)))
        assertFalse(pending.done)
        val finished = change(pending, pending.copy(subtasks = pending.subtasks.map { it.copy(done = true) }))
        assertTrue(finished.done)
        assertEquals("got", finished.events.last().kind)
    }

    @Test fun repeatKeepsTopicOpen() {
        val q = Subtask(title = "Проверить почту", due = today, repeat = Repeat("daily"))
        val t = Task(title = "Работа", topic = true, subtasks = listOf(q))
        val result = change(t, t.copy(subtasks = listOf(q.copy(done = true))))
        assertFalse(result.done)
        assertEquals(1, result.subtasks.count { !it.done })
        assertEquals(LocalDate.now().plusDays(1).toString(), result.subtasks.last().due)
    }

    @Test fun reopeningMatterReopensTopic() {
        val q = Subtask(title = "Отправить договор", done = true, doneAt = today)
        val t = Task(title = "Договор", topic = true, done = true, doneAt = today, subtasks = listOf(q))
        val result = change(t, t.copy(subtasks = listOf(q.copy(done = false))))
        assertFalse(result.done)
        assertNull(result.doneAt)
        assertNull(result.subtasks.single().doneAt)
        assertEquals("revived", result.events.last().kind)
    }

    @Test fun newMatterReopensCompletedTopic() {
        val t = Task(title = "Договор", topic = true, done = true, doneAt = today,
            subtasks = listOf(Subtask(title = "Отправить", done = true)))
        val result = change(t, t.copy(subtasks = t.subtasks + Subtask(title = "Получить подпись")))
        assertFalse(result.done)
        assertNull(result.doneAt)
    }

    @Test fun emptyTopicAndUnrelatedEditsStayOpen() {
        val empty = Task(title = "Ремонт", topic = true)
        assertFalse(change(empty, empty.copy(title = "Ремонт кухни")).done)
        val t = empty.copy(subtasks = listOf(Subtask(title = "Выбрать плитку", done = true)))
        assertFalse(change(t, t.copy(entries = listOf(Entry(text = "Новая идея")))).done)
    }

    @Test fun removingOpenMatterDoesNotCompleteTopic() {
        val done = Subtask(title = "Отправить", done = true)
        val t = Task(title = "Договор", topic = true, subtasks = listOf(done, Subtask(title = "Позвонить")))
        assertFalse(change(t, t.copy(subtasks = listOf(done))).done)
    }

    @Test fun nestedStepKeepsTopicOpenUntilCompleted() {
        val child = Subtask(title = "Проверить вложение")
        val parent = Subtask(title = "Отправить договор", children = listOf(child))
        val t = Task(title = "Договор", topic = true, subtasks = listOf(parent))
        val pending = change(t, t.copy(subtasks = listOf(parent.copy(done = true))))
        assertFalse(pending.done)
        val result = change(pending, pending.copy(subtasks = listOf(parent.copy(done = true, children = listOf(child.copy(done = true))))))
        assertTrue(result.done)
    }

    @Test fun everyScheduledDayMustBeCompleted() {
        val a = Slot(date = today)
        val b = Slot(date = LocalDate.now().plusDays(1).toString())
        val q = Subtask(title = "Позвонить", slots = listOf(a, b))
        val t = Task(title = "Созвоны", topic = true, subtasks = listOf(q))
        val pending = change(t, t.copy(subtasks = listOf(q.toggleSlot(a.id))))
        assertFalse(pending.done)
        val result = change(pending, pending.copy(subtasks = listOf(pending.subtasks.single().toggleSlot(b.id))))
        assertTrue(result.done)
    }
}
