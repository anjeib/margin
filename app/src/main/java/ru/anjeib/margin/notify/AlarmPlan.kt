package ru.anjeib.margin.notify

import ru.anjeib.margin.data.Note
import ru.anjeib.margin.data.Task
import ru.anjeib.margin.data.findSub
import ru.anjeib.margin.data.ownReminder
import ru.anjeib.margin.data.questionRef
import ru.anjeib.margin.data.slotRef
import ru.anjeib.margin.data.slotRows
import ru.anjeib.margin.data.whenLabel
import ru.anjeib.margin.i18n.L
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/*
 * What should ring and what it says — plain functions over the data, so the tests see the same plan the phone sets.
 * Keys: a matter's id; «<id>@<day>» for one of its several days; «q:<topic>:<matter>» (and «…@<day>») for a topic's
 * matter; «note:<id>» for a note. The same keys open the matter (App.openId) and answer its buttons (Reminders.fire).
 */

private fun dt(s: String?) = s?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() }
private fun day(s: String?) = s?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
private fun hour(s: String?) = s?.let { runCatching { LocalTime.parse(it) }.getOrNull() }

/** Someone else owes it, or the user promised it to someone: such a matter without an hour rings on its day's morning. */
private val Task.owed get() = waitingFor != null || promisedTo != null

/**
 * Every future reminder: [morning] is the morning summary's hour. A matter rings at its explicit reminder, else at its
 * own day and hour (so an old stale reminder never silences it), else — waited for or promised, without an hour —
 * on the morning of its day. Each open day of several rings on its own.
 */
fun alarmPlan(tasks: List<Task>, notes: List<Note>, now: LocalDateTime, morning: LocalTime): Map<String, LocalDateTime> {
    val out = LinkedHashMap<String, LocalDateTime>()
    fun put(key: String, at: LocalDateTime?) { if (at != null && !at.isBefore(now)) out[key] = at }
    fun at(date: String?, time: String?, owed: Boolean) = day(date)?.let { d -> (hour(time) ?: morning.takeIf { owed })?.let { d.atTime(it) } }
    for (t in tasks) {
        if (t.done) continue
        // The course's lessons never ring (ChatGPT's audit of 0.89: a newcomer got notifications from the examples).
        if (t.id.startsWith("ex-")) continue
        // A meeting from the phone calendar: the calendar itself rings for it — not twice (0.94); «напомни в…» still does.
        if (t.calEvent != null && t.remindAt == null) continue
        if (t.topic) {
            put(t.id, dt(t.remindAt)) // a thought keeps the reminder its note had
            for (q in t.subtasks) {
                if (q.done) continue
                val key = "q:${t.id}:${q.id}"
                if (q.slots.isEmpty()) put(key, at(q.due, q.time, q.from != null))
                else q.slots.filter { !it.done }.forEach { s -> put("$key@${s.id}", at(s.date, s.time, q.from != null)) }
            }
            continue
        }
        if (t.slots.isNotEmpty()) {
            t.slots.filter { !it.done }.forEach { s -> put("${t.id}@${s.id}", at(s.date, s.time, t.owed)) }
            // «Через час» on one of the days: a reminder of its own, apart from the days.
            if (t.remindAt != t.ownReminder()) put(t.id, dt(t.remindAt))
            continue
        }
        val explicit = dt(t.remindAt)?.takeIf { !it.isBefore(now) }
        val own = dt(t.ownReminder())?.takeIf { !it.isBefore(now) }
        put(t.id, explicit ?: own ?: if (t.time == null && t.owed) t.dueDate?.atTime(morning) else null)
    }
    notes.forEach { n -> put("note:${n.id}", dt(n.remindAt)) }
    return out
}

/** Which buttons a reminder has: «Готово · Через час»; without an hour «Готово · Завтра»; something awaited «Получено · Завтра». */
enum class Buttons { Hour, Tomorrow, Wait }

/** What a reminder says: [open] — what a tap opens. */
data class Ring(val title: String, val text: String, val buttons: Buttons, val open: String)

/** The reminder for [key] as it should read now; null when its matter is gone or done. */
fun ringOf(tasks: List<Task>, key: String, today: LocalDate = LocalDate.now()): Ring? {
    val (base, sid) = slotRef(key) ?: (key to null)
    val t0 = today.toString()
    questionRef(base)?.let { (tid, qid) ->
        val t = tasks.find { it.id == tid && !it.done } ?: return null
        val q = t.subtasks.findSub(qid)?.takeIf { !it.done } ?: return null
        val s = sid?.let { id -> q.slots.find { it.id == id && !it.done } ?: return null }
        val date = s?.date ?: q.due
        val time = if (s != null) s.time else q.time
        val title = when {
            q.from != null && time == null && date == t0 -> L.s.todayWaitFrom(q.from, q.title)
            q.from != null -> L.s.waitPrefix(q.from) + " " + q.title
            else -> q.title
        }
        val buttons = when { q.from != null -> Buttons.Wait; time == null -> Buttons.Tomorrow; else -> Buttons.Hour }
        return Ring(title, "↳ " + t.title, buttons, key)
    }
    val t = tasks.find { it.id == base && !it.done } ?: return null
    val row = if (sid != null) t.slotRows().find { it.id == key } ?: return null else t
    // The morning of the day something is owed: say who and what, in one line.
    val owedToday = row.time == null && row.due == t0
    val title = when {
        owedToday && t.waitingFor != null -> L.s.todayWaitFrom(t.waitingFor, t.title)
        owedToday && t.promisedTo != null -> L.s.todayPromisedTo(t.promisedTo, t.title)
        else -> t.title
    }
    val text = if (title != t.title) "" else listOfNotNull(row.whenLabel(today), t.waitingFor?.let { L.s.waitingFor(it) }).joinToString(" · ")
    val buttons = when { t.waitingFor != null -> Buttons.Wait; row.time == null && t.remindAt == null -> Buttons.Tomorrow; else -> Buttons.Hour }
    return Ring(title, text, buttons, key)
}
