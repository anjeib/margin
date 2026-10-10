package ru.anjeib.margin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.anjeib.margin.ai.Commands
import ru.anjeib.margin.ai.LocalParser
import ru.anjeib.margin.data.AppData
import ru.anjeib.margin.data.Repeat
import ru.anjeib.margin.data.Slot
import ru.anjeib.margin.data.Subtask
import ru.anjeib.margin.data.Task
import ru.anjeib.margin.data.Topics
import ru.anjeib.margin.data.autoReminded
import ru.anjeib.margin.data.doneFromReminder
import ru.anjeib.margin.data.dropTask
import ru.anjeib.margin.data.eveningCounts
import ru.anjeib.margin.data.listTasks
import ru.anjeib.margin.data.nextOccurrence
import ru.anjeib.margin.data.postponeTo
import ru.anjeib.margin.data.snoozeFromReminder
import ru.anjeib.margin.data.syncSlots
import ru.anjeib.margin.data.toggleSlot
import ru.anjeib.margin.data.toggleTask
import ru.anjeib.margin.data.todayToTomorrow
import ru.anjeib.margin.i18n.L
import ru.anjeib.margin.i18n.Lang
import ru.anjeib.margin.notify.Buttons
import ru.anjeib.margin.notify.alarmPlan
import ru.anjeib.margin.notify.ringOf
import java.time.LocalDate
import java.time.LocalTime

/**
 * Reminders follow the matter whoever moves it (Topics.track → reminderAfter), and the alarm plan the phone sets
 * (notify/AlarmPlan) rings each day of several, the next time of a repeat, and the morning something is owed. Offline.
 */
class RemindersTest {
    private val today = LocalDate.now()
    private fun day(n: Long) = today.plusDays(n).toString()
    private val nine = LocalTime.of(9, 0)

    /** As Store.update does it: the change, then the feed lines and the reminders. */
    private fun AppData.step(f: (AppData) -> AppData): AppData = Topics.track(this, f(this))
    private fun AppData.task(id: String) = tasks.single { it.id == id }
    private fun AppData.plan(at: LocalTime = LocalTime.of(0, 1), on: LocalDate = today) = alarmPlan(tasks, notes, on.atTime(at), nine)

    @Before fun ru() { L.lang = Lang.Ru }

    @Test fun repeatWithTimeRemindsNextTime() {
        val r = Task(id = "r", title = "Планёрка", due = today.toString(), time = "10:00", repeat = Repeat("weekly")).autoReminded()
        // Ticked off (swipe, the ring), dropped, «Готово» from the notification — the next one reminds on its own day.
        for (f in listOf<(AppData) -> AppData>({ it.toggleTask("r", today) }, { it.dropTask("r", today) }, { it.doneFromReminder("r", today) })) {
            val d = AppData(tasks = listOf(r)).step(f)
            val next = d.tasks.single { !it.done }
            assertEquals(day(7), next.due)
            assertEquals("${day(7)}T10:00", next.remindAt)
            assertEquals(today.plusDays(7).atTime(10, 0), d.plan()[next.id])
        }
        // Even without the store: the next occurrence carries its reminder.
        assertEquals("${day(7)}T10:00", r.nextOccurrence(today)!!.remindAt)
    }

    @Test fun twoDaysRemindOnTheSecondAfterTheFirst() {
        val s = Task(id = "s", title = "Тренировка", slots = listOf(Slot(id = "a", date = day(0), time = "19:00"), Slot(id = "b", date = day(2), time = "19:00")))
            .syncSlots().autoReminded()
        var d = AppData(tasks = listOf(s))
        assertEquals(today.atTime(19, 0), d.plan()["s@a"])
        assertEquals(today.plusDays(2).atTime(19, 0), d.plan()["s@b"])
        d = d.step { it.toggleSlot("s", "a") }
        // The stored reminder moved with the next open day, and the first day rings no more.
        assertEquals("${day(2)}T19:00", d.task("s").remindAt)
        assertNull(d.plan()["s@a"])
        assertEquals(today.plusDays(2).atTime(19, 0), d.plan()["s@b"])
        // A day left unticked in the past does not silence the next one.
        val late = Task(id = "l", title = "Звонить маме", slots = listOf(Slot(id = "a", date = day(-1), time = "10:00"), Slot(id = "b", date = day(1), time = "10:00"))).syncSlots()
        assertEquals(today.plusDays(1).atTime(10, 0), AppData(tasks = listOf(late)).plan()["l@b"])
    }

    @Test fun doneFromNotificationTicksOneDay() {
        val s = Task(id = "s", title = "Тренировка", slots = listOf(Slot(id = "a", date = day(0), time = "19:00"), Slot(id = "b", date = day(2), time = "19:00"))).syncSlots()
        val p = Task(id = "p", title = "Ремонт", topic = true, subtasks = listOf(Subtask(id = "m", title = "Замер",
            slots = listOf(Slot(id = "x", date = day(0), time = "11:00"), Slot(id = "y", date = day(1), time = "11:00"))).syncSlots()))
        var d = AppData(tasks = listOf(s, p)).step { it.doneFromReminder("s@a", today) }
        assertFalse(d.task("s").done)
        assertEquals(listOf(true, false), d.task("s").slots.map { it.done })
        assertTrue(d.listTasks().any { it.id == "s@b" })
        // The old key (the matter itself) ticks the first open day, not the whole matter.
        d = d.step { it.doneFromReminder("s", today) }
        assertTrue(d.task("s").done)
        // A topic's matter of several days: just that day.
        d = d.step { it.doneFromReminder("q:p:m@x", today) }
        val m = d.task("p").subtasks.single()
        assertFalse(m.done)
        assertEquals(listOf(true, false), m.slots.map { it.done })
        assertEquals(day(1), m.due)
        // «Через час» on a topic's day moves that day's hour, and it sticks.
        val at = today.atTime(15, 0)
        d = d.step { it.snoozeFromReminder("q:p:m@y", at) }
        assertEquals(listOf(day(0), day(0)), d.task("p").subtasks.single().slots.map { it.date })
    }

    @Test fun tomorrowMovesTheReminderToo() {
        val t = Task(id = "t", title = "Позвонить в банк", due = today.toString(), time = "22:00").autoReminded()
        // «Завтра» on the notification.
        val d = AppData(tasks = listOf(t)).step { it.postponeTo("t", today.plusDays(1), today) }
        assertEquals("${day(1)}T22:00", d.task("t").remindAt)
        // The evening's «Перенести на завтра»: the reminder goes too — no ring tonight for a matter moved to tomorrow.
        val e = AppData(tasks = listOf(t)).step { it.todayToTomorrow(today) }
        assertEquals(day(1), e.task("t").due)
        assertEquals("${day(1)}T22:00", e.task("t").remindAt)
        assertEquals(today.plusDays(1).atTime(22, 0), e.plan()["t"])
        // Any path that moves the date (here: a bare copy) takes the reminder along; a reminder set on purpose stays.
        val moved = AppData(tasks = listOf(t)).step { x -> x.copy(tasks = x.tasks.map { it.copy(due = day(3)) }) }
        assertEquals("${day(3)}T22:00", moved.task("t").remindAt)
        val snoozed = AppData(tasks = listOf(t)).step { it.snoozeFromReminder("t", today.atTime(23, 0)) }
        assertEquals("${today}T23:00", snoozed.task("t").remindAt)
        assertEquals("${today}T23:00", snoozed.step { x -> x.copy(tasks = x.tasks.map { it.copy(title = "Позвонить в банк про карту") }) }.task("t").remindAt)
    }

    @Test fun eveningMoveLeavesWaitsAndPromises() {
        val t0 = today.toString()
        val mine = Task(id = "mine", title = "Купить лампу", due = t0)
        val wait = Task(id = "wait", title = "Макет", due = t0, waitingFor = "Дима")
        val promise = Task(id = "prom", title = "Исходники", due = t0, promisedTo = "Лёша")
        val rep = Task(id = "rep", title = "Полив", due = t0, repeat = Repeat("daily"))
        val late = Task(id = "late", title = "Счёт", due = day(-2))
        val topic = Task(id = "p", title = "Сайт", topic = true, subtasks = listOf(
            Subtask(id = "own", title = "Тексты", due = t0), Subtask(id = "got", title = "Фото", due = t0, from = "Аня")))
        val d = AppData(tasks = listOf(mine, wait, promise, rep, late, topic)).step { it.todayToTomorrow(today) }
        assertEquals(day(1), d.task("mine").due)
        assertEquals(t0, d.task("wait").due)
        assertEquals(t0, d.task("prom").due)
        assertEquals(t0, d.task("rep").due)
        assertEquals(day(-2), d.task("late").due)
        assertEquals(day(1), d.task("p").subtasks.single { it.id == "own" }.due)
        assertEquals(t0, d.task("p").subtasks.single { it.id == "got" }.due)
        // Only waits and promises left: nothing to move, the button is not offered.
        val owed = AppData(tasks = listOf(wait, promise))
        assertEquals(owed, owed.todayToTomorrow(today))
    }

    @Test fun eveningCountsTopicsMattersAsTheMorningDoes() {
        val topic = Task(id = "p", title = "Сайт", topic = true, subtasks = listOf(
            Subtask(id = "a", title = "Тексты", due = today.toString()), Subtask(id = "b", title = "Фото", done = true, doneAt = today.toString())))
        val simple = Task(id = "s", title = "Лампа", done = true, doneAt = today.toString())
        assertEquals(2 to 1, AppData(tasks = listOf(topic, simple)).eveningCounts(today))
    }

    @Test fun owedRingsOnTheMorningOfItsDay() {
        val wait = Task(id = "w", title = "Макет", due = day(1), waitingFor = "Дима")
        val promise = Task(id = "o", title = "Исходники", due = day(1), promisedTo = "Лёша")
        val timed = Task(id = "c", title = "Созвон", due = day(1), time = "15:00", waitingFor = "Дима")
        val plain = Task(id = "x", title = "Шкаф", due = day(1))
        val topic = Task(id = "p", title = "Сайт", topic = true, subtasks = listOf(Subtask(id = "f", title = "Фото", due = day(1), from = "Аня")))
        val d = AppData(tasks = listOf(wait, promise, timed, plain, topic))
        val plan = d.plan(LocalTime.NOON)
        assertEquals(today.plusDays(1).atTime(9, 0), plan["w"])
        assertEquals(today.plusDays(1).atTime(9, 0), plan["o"])
        assertEquals(today.plusDays(1).atTime(15, 0), plan["c"])
        assertNull(plan["x"])
        assertEquals(today.plusDays(1).atTime(9, 0), plan["q:p:f"])
        // What it says that morning, and its buttons: «Получено · Завтра» for a wait, no «Через час».
        val tomorrow = today.plusDays(1)
        val w = ringOf(d.tasks, "w", tomorrow)!!
        assertEquals("Сегодня ждём от Димы: Макет", w.title)
        assertEquals(Buttons.Wait, w.buttons)
        assertEquals("w", w.open)
        val o = ringOf(d.tasks, "o", tomorrow)!!
        assertEquals("Сегодня обещали Лёше: Исходники", o.title)
        assertEquals(Buttons.Tomorrow, o.buttons)
        assertEquals("Сегодня ждём от Ани: Фото", ringOf(d.tasks, "q:p:f", tomorrow)!!.title)
        // A timed wait keeps its hour in the words; still «Получено», not «Готово».
        val c = ringOf(d.tasks, "c", tomorrow)!!
        assertEquals("Созвон", c.title)
        assertEquals(Buttons.Wait, c.buttons)
        // «Завтра» on it: the next morning instead.
        val later = d.step { it.postponeTo("w", today.plusDays(2), tomorrow) }
        assertEquals(today.plusDays(2).atTime(9, 0), later.plan(LocalTime.NOON)["w"])
        // Received — it rings no more.
        assertNull(d.step { it.doneFromReminder("w", tomorrow) }.plan(LocalTime.NOON)["w"])
    }

    @Test fun staleReminderDoesNotSilenceTheMatter() {
        // Saved by an older version: the reminder stayed on the day before the matter moved.
        val t = Task(id = "t", title = "Отчёт", due = day(1), time = "10:00", remindAt = "${day(-1)}T10:00")
        assertEquals(today.plusDays(1).atTime(10, 0), AppData(tasks = listOf(t)).plan()["t"])
        // A timed matter that never got a reminder rings at its hour.
        assertNotNull(AppData(tasks = listOf(t.copy(remindAt = null))).plan()["t"])
    }

    @Test fun dailySummariesWithoutTheAi() {
        fun v(s: String) = Commands.parse(s)?.takeIf { it.what == "settings" }?.value
        assertEquals("evening=21:00", v("итог дня в 21"))
        assertEquals("evening=21:00", v("Итог дня в 9"))
        assertEquals("evening=21:30", v("присылай вечерний итог в 21:30"))
        assertEquals("evening=off", v("вечером итог не нужен"))
        assertEquals("evening=off", v("Вечерний итог не нужен"))
        assertEquals("evening=off", v("выключи итог дня"))
        assertEquals("morning=09:00", v("утреннюю сводку в 9"))
        assertEquals("morning=07:30", v("сводку в 7:30"))
        assertEquals("morning=off", v("утренняя сводка не нужна"))
        assertEquals("evening=20:00", v("evening summary at 8pm"))
        // As «Помощь» says them.
        assertEquals("evening=off", v("итог дня не нужен"))
        assertEquals("morning=09:00", v("сводку в 9"))
        assertEquals("morning=09:00", v("morning summary at 9"))
        assertEquals("evening=off", v("no evening summary"))
        // A matter about a summary stays a matter.
        assertNull(v("Подвести итог дня в 18"))
        assertNull(v("Отправить сводку в 9 Диме"))
    }

    @Test fun phraseWithoutCyrillicIsNotSure() {
        val r = LocalParser.parse("Call mom tomorrow at 5pm", AppData(), today)
        assertFalse(r.sure)
        assertEquals("lang", r.why)
        // Russian with a Latin word is still the phone's.
        assertTrue(LocalParser.parse("Созвон по API завтра в 15", AppData(), today).sure)
    }
}
