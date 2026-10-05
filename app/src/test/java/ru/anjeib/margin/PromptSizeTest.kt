package ru.anjeib.margin

import org.junit.Test
import ru.anjeib.margin.ai.Assistant
import ru.anjeib.margin.data.AppData
import ru.anjeib.margin.data.Entry
import ru.anjeib.margin.data.Note
import ru.anjeib.margin.data.Person
import ru.anjeib.margin.data.Settings
import ru.anjeib.margin.data.Subtask
import ru.anjeib.margin.data.Task
import ru.anjeib.margin.i18n.L
import ru.anjeib.margin.i18n.Lang

/** Rough prompt size (chars/3.5 ≈ tokens for mixed RU/EN) for empty vs. a heavy user. */
class PromptSizeTest {
    @Test
    fun sizes() {
        L.lang = Lang.Ru
        val s = Settings()
        fun tok(x: String) = x.length / 3.5
        val empty = Assistant.systemPrompt(AppData(), s)
        val heavy = AppData(
            tasks = (1..300).map { i -> Task(title = "Задача номер $i про проект и детали", due = "2026-10-${10 + i % 20}", project = "проект${i % 6}",
                people = listOf("Дима"), subtasks = (1..3).map { Subtask(title = "Шаг $it задачи $i") }, entries = listOf(Entry(text = "Запись к задаче $i с подробностями"))) },
            notes = (1..60).map { Note(title = "Заметка $it", entries = listOf(Entry(text = "Мысль про что-то важное и длинное описание идеи $it"))) },
            persons = (1..30).map { Person(name = "Человек$it", about = "коллега, делает дизайн и тексты") },
        )
        val full = Assistant.systemPrompt(heavy, s)
        val focused = Assistant.systemPrompt(heavy, s, query = "для проект3 ещё купить домен и спросить у Человек5 про макет")
        val simple = Assistant.systemPrompt(heavy, s, query = "завтра в 18:00 стоматолог")
        println("EMPTY    ~${tok(empty).toInt()} tokens")
        println("HEAVY full overview      ~${tok(full).toInt()} tokens")
        println("HEAVY focused (related)  ~${tok(focused).toInt()} tokens")
        println("HEAVY focused (simple)   ~${tok(simple).toInt()} tokens")
    }
}
