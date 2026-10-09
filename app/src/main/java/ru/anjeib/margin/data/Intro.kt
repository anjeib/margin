package ru.anjeib.margin.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * «Знакомство с Margin»: one example of each thing the app does, put in on the first launch instead of someone else's
 * two months of life (that stays in the demo build). The words of each example say what to try on it. All of them are
 * marked [Task.sample] and leave with «Убрать примеры» — nothing of them stays among the user's own.
 */
object Intro {
    data class Texts(
        val topic: String, val topicNote: String, val topicOpen: String, val topicFile: String,
        val waitWho: String, val topicWait: String,
        val swipeRight: String, val swipeLeft: String, val hold: String,
        val ask: String, val askMove: String, val flowers: String,
        val promise: String, val noDate: String,
        val idea: String, val ideaNote: String,
    )

    /** Examples whose words only say what to try — kept out of the AI's view (the flowers, the promise, the topic stay in). */
    private val teaching = setOf("intro-right", "intro-left", "intro-hold", "intro-ask", "intro-move", "intro-nodate")
    fun teachesOnly(t: Task) = t.sample && t.id in teaching

    fun build(t: Texts, today: LocalDate = LocalDate.now()): List<Task> {
        fun d(n: Long) = today.plusDays(n).toString()
        val sat = today.with(TemporalAdjusters.next(DayOfWeek.SATURDAY)).toString()
        val topic = Task(
            id = "intro-topic", title = t.topic, topic = true, sphere = "personal", createdAt = d(0), sample = true,
            entries = listOf(Entry(id = "intro-e1", at = "${d(0)}T09:00", text = t.topicNote)),
            subtasks = listOf(
                Subtask(id = "intro-q1", title = t.topicOpen, due = d(0)),
                Subtask(id = "intro-q2", title = t.topicWait, due = d(2), from = t.waitWho),
                Subtask(id = "intro-q3", title = t.topicFile, due = d(1)),
            ),
        )
        fun deal(id: String, title: String, due: String?, time: String? = null) =
            Task(id = "intro-$id", title = title, due = due, time = time, sphere = "personal", createdAt = d(0), sample = true)
        return listOf(
            topic,
            deal("right", t.swipeRight, d(0)),
            deal("left", t.swipeLeft, d(0)),
            deal("hold", t.hold, d(0), "18:00"),
            deal("ask", t.ask, d(0)),
            deal("move", t.askMove, d(1)),
            deal("flowers", t.flowers, sat).copy(repeat = Repeat("weekly")),
            deal("promise", t.promise, d(3)).copy(promisedTo = t.waitWho),
            deal("nodate", t.noDate, null),
            Task(id = "intro-idea", title = t.idea, topic = true, thought = true, sphere = "personal", createdAt = d(0), sample = true,
                entries = listOf(Entry(id = "intro-e2", at = "${d(0)}T09:05", text = t.ideaNote))),
        )
    }
}

/** The examples are in the app (even ticked off). */
fun AppData.hasSamples() = tasks.any { it.sample }

/** «Убрать примеры»: every example goes, the user's own stay as they are. */
fun AppData.withoutSamples() = copy(tasks = tasks.filterNot { it.sample })

/** «Вернуть примеры»: fresh ones, dated from today, in place of any left. */
fun AppData.withSamples(t: Intro.Texts, today: LocalDate = LocalDate.now()) = copy(tasks = Intro.build(t, today) + tasks.filterNot { it.sample })
