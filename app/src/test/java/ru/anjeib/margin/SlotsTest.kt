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
import ru.anjeib.margin.data.Slot
import ru.anjeib.margin.data.Subtask
import ru.anjeib.margin.data.Task
import ru.anjeib.margin.data.Topics
import ru.anjeib.margin.data.dayOf
import ru.anjeib.margin.data.listTasks
import ru.anjeib.margin.data.slotRef
import ru.anjeib.margin.data.syncSlots
import ru.anjeib.margin.data.toggleSlot
import java.time.LocalDate

/** One matter on several days («в пн и чт»): each day its own row and tick, the date follows the next open day. Offline. */
class SlotsTest {
    private val today = LocalDate.now()
    private val d1 = today.plusDays(1).toString()
    private val d2 = today.plusDays(4).toString()
    private fun o(vararg p: Pair<String, Any>): JsonObject = JsonObject(p.associate { (k, v) ->
        k to when (v) { is List<*> -> JsonArray(v.map { it as JsonObject }); else -> JsonPrimitive(v.toString()) }
    })

    @Test fun dateFollowsNextOpenDay() {
        val t = Task(title = "Тренировка", slots = listOf(Slot(id = "b", date = d2, time = "19:00"), Slot(id = "a", date = d1))).syncSlots()
        assertEquals(d1, t.due)
        assertEquals(listOf("a", "b"), t.slots.map { it.id })
        val one = t.toggleSlot("a")
        assertEquals(d2, one.due)
        assertEquals("19:00", one.time)
        assertFalse(one.done)
        val both = one.toggleSlot("b")
        assertTrue(both.done)
        assertFalse(both.toggleSlot("b").done)
    }

    @Test fun eachOpenDayIsARow() {
        val t = Task(id = "t", title = "Тренировка", slots = listOf(Slot(id = "a", date = d1, done = true), Slot(id = "b", date = d2))).syncSlots()
        val topic = Task(id = "p", title = "Ремонт", topic = true, subtasks = listOf(Subtask(id = "q", title = "Замер", slots = listOf(Slot(id = "x", date = d1), Slot(id = "y", date = d2))).syncSlots()))
        val rows = AppData(tasks = listOf(t, topic)).listTasks()
        assertEquals(listOf("t@b", "q:p:q@x", "q:p:q@y"), rows.map { it.id })
        assertEquals(d2, rows[0].due)
        assertEquals(2 to 2, rows[0].dayOf())
        assertEquals("q:p:q" to "y", slotRef(rows[2].id))
    }

    @Test fun aiDatesBecomeDays() {
        val d = AppData()
        val plan = AiPlan(null, listOf(o("type" to "create_task", "title" to "Тренировка", "dates" to listOf(o("date" to d1), o("date" to d2, "time" to "19:00")))), null)
        val out = Topics.track(d, Assistant.apply(d, plan, "тренировка в пн и чт", strict = true).data)
        val t = out.tasks.single()
        assertEquals(2, t.slots.size)
        assertEquals(d1, t.due)
        assertEquals("19:00", t.slots.last().time)
        // A single date stays a plain due date.
        val single = AiPlan(null, listOf(o("type" to "create_task", "title" to "Звонок", "dates" to listOf(o("date" to d1)), "due" to d1)), null)
        assertTrue(Assistant.apply(d, single, "звонок завтра", strict = true).data.tasks.single().slots.isEmpty())
    }
}
