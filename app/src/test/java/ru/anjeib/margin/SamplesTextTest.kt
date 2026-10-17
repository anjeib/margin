package ru.anjeib.margin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import ru.anjeib.margin.data.Samples
import ru.anjeib.margin.data.Subtask
import ru.anjeib.margin.data.Task
import ru.anjeib.margin.data.retextSamples
import java.time.LocalDate

/** The examples speak of the one screen (17 окт): no «Сегодня» / «Заметки» pages; old words are renewed only where untouched. */
class SamplesTextTest {
    @Test fun theExamplesKnowNoOldPages() {
        val d = Samples.build(LocalDate.of(2026, 10, 17))
        val words = d.tasks.flatMap { t -> listOf(t.title) + t.entries.map { it.text } + t.subtasks.map { it.title } + t.comments.map { it.text } }
        words.forEach { w ->
            assertFalse(w, w.contains("«Сегодня»"))
            assertFalse(w, w.contains("странице «Заметки»"))
        }
    }

    @Test fun oldWordsAreRenewedOnlyWhereUntouched() {
        val old = Samples.build().let { d ->
            d.copy(tasks = d.tasks.map { t ->
                when (t.id) {
                    "ex-nodate" -> t.copy(title = "Без даты — тоже нормально: такие дела ждут внизу «Сегодня»")
                    "ex-course" -> t.copy(subtasks = t.subtasks.map { if (it.id == "ex-c3") it.copy(title = "Урок 2 · Домашнее задание от Кати — то, что ждём, видно сверху «Сегодня»") else it })
                    else -> t
                }
            } + Task(id = "ex-mine", title = "Без даты — моя правка"))
        }
        val now = old.retextSamples()
        assertEquals("Без даты — тоже нормально: такие дела стоят под сегодняшним днём, пока не сделаны", now.tasks.first { it.id == "ex-nodate" }.title)
        assertEquals("Урок 2 · Домашнее задание от Кати — то, что ждём, помечено «ждём» под делом",
            now.tasks.first { it.id == "ex-course" }.subtasks.first { it.id == "ex-c3" }.title)
        assertEquals("Без даты — моя правка", now.tasks.first { it.id == "ex-mine" }.title)
        assertEquals(now, now.retextSamples())
    }
}
