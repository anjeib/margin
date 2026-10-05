package ru.anjeib.margin

import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import ru.anjeib.margin.ai.Assistant
import ru.anjeib.margin.ai.OpenRouter
import ru.anjeib.margin.data.AppData
import ru.anjeib.margin.data.Settings
import ru.anjeib.margin.data.Subtask
import ru.anjeib.margin.i18n.L
import ru.anjeib.margin.i18n.Lang

/** Runs real phrases through OpenRouter. Skipped unless MARGIN_LIVE=1 (costs a few cents). */
class AssistantLiveTest {
    private val key = System.getenv("OPENROUTER_KEY") ?: ""

    private fun dump(d: AppData) = buildString {
        fun sub(l: List<Subtask>, depth: Int) { l.forEach { appendLine("  ".repeat(depth + 2) + "- ${it.title}" + (it.assignee?.let { a -> " ($a)" } ?: "")); sub(it.children, depth + 1) } }
        d.tasks.forEach { t ->
            appendLine("  • ${t.title} | ${t.start}..${t.due} ${t.time ?: ""} | #${t.project} | ${t.people} | wait=${t.waitingFor} | note=${t.noteId} | " + t.entries.joinToString { it.text })
            sub(t.subtasks, 0)
        }
        d.notes.forEach { n -> appendLine("  ✎ ${n.title} #${n.project}: " + n.entries.joinToString(" / ") { it.text.take(100) }) }
    }

    @Test
    fun phrases() = runBlocking {
        assumeTrue(System.getenv("MARGIN_LIVE") == "1" && key.isNotBlank())
        L.lang = Lang.Ru
        val s = Settings(apiKey = key, fastModel = System.getenv("MODEL") ?: Settings.DEFAULT_FAST)
        var d = AppData()
        val phrases = listOf(
            "На этой неделе разобраться с vuzburo и запустить. Дима сделает дизайн, на мне тексты и оплата.",
            "для vuzburo ещё купить домен и настроить почту",
            "Саша обещал прислать смету, через пару дней напомнить. Ещё почитать про новый Claude, купить корм коту и подумать над главной vuzburo — мне кажется, нужен калькулятор стоимости и отзывы",
            "созвониться с Анной по договору",
            "завтра в 18:00 стоматолог",
            "Дима должен прислать макет в среду",
            "Дима — наш дизайнер, его телефон +7 999 123-45-67",
            "идея: на главной vuzburo сделать блок с ценами без «от»",
            "Добавь в заметку: зайти в Бахчисарай в среду.",
        )
        for (p in phrases) {
            val t0 = System.currentTimeMillis()
            val r = OpenRouter.chat(key, s.fastModel, Assistant.systemPrompt(d, s), listOf("user" to p), quick = true)
            val plan = Assistant.parsePlan(r.content)
            val applied = Assistant.apply(d, plan, p)
            d = applied.data
            println("\n>>> $p   [${System.currentTimeMillis() - t0} ms, $${"%.5f".format(r.costUsd)}]")
            println("caption=${applied.caption} reply=${plan.reply} question=${plan.question}")
            plan.actions.forEach { println("  action: ${it["type"]}") }
        }
        // Inside one task: info becomes a record, dates change, steps stay the user's.
        val dent = d.tasks.first { it.title.contains("томатолог") }
        val scope = ru.anjeib.margin.ai.Scope.InTask(dent.id)
        for (p in listOf("это у доктора Иванова на Ленина 5, не забыть полис", "перенеси на пятницу в 15:00", "разбей на шаги: позвонить подтвердить, взять полис, доехать")) {
            val r = OpenRouter.chat(key, s.fastModel, Assistant.systemPrompt(d, s, scope), listOf("user" to p), quick = true)
            val a = Assistant.apply(d, Assistant.parsePlan(r.content)); d = a.data
            println("\n[in task] >>> $p → ${a.caption}")
        }
        println("\n==== FINAL STATE ====\n" + dump(d))
        println("people: " + d.persons.joinToString { "${it.name} (${it.about}, ${it.phone})" })
    }
}
