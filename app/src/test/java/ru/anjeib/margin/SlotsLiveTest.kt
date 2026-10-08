package ru.anjeib.margin

import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import ru.anjeib.margin.ai.Assistant
import ru.anjeib.margin.ai.Scope
import ru.anjeib.margin.ai.Yandex
import ru.anjeib.margin.data.AppData
import ru.anjeib.margin.data.Settings
import ru.anjeib.margin.data.Task
import ru.anjeib.margin.data.Topics
import ru.anjeib.margin.i18n.L
import ru.anjeib.margin.i18n.Lang
import java.io.File
import java.time.LocalDate

/** Several days of one matter on the live hybrid: «в пн и чт» → one task with days; «каждый пн и чт» → a repeat. YA_LIVE=1 RUNS=n. */
class SlotsLiveTest {
    private val home = System.getProperty("user.home")
    private val key = runCatching { File("$home/.yc_margin_key").readText().trim() }.getOrDefault("")
    private val folder = runCatching { File("$home/.yc_margin_folder").readText().trim() }.getOrDefault("")
    private val today = LocalDate.now()

    private fun ask(d: AppData, text: String): AppData = runBlocking {
        val sys = Assistant.systemPrompt(d, Settings(apiKey = "x"), Scope.Main, query = text)
        val model = if (Assistant.isComplex(text, Scope.Main)) "deepseek-v4.1-flash" else "aliceai-llm-flash"
        var r = Yandex.chat(key, folder, model, sys, listOf("user" to Assistant.userMessage(text)))
        if (Assistant.isBroken(r.content)) r = Yandex.chat(key, folder, model, sys, listOf("user" to Assistant.userMessage(text)))
        if (System.getenv("DEBUG") != null) println("  [$model] ${r.content.take(500)}")
        Topics.track(d, Assistant.apply(d, Assistant.parsePlan(r.content).copy(question = null), text, strict = true).data)
    }

    @Test fun days() {
        assumeTrue(System.getenv("YA_LIVE") != null && key.isNotBlank())
        L.lang = Lang.Ru
        val runs = System.getenv("RUNS")?.toIntOrNull() ?: 1
        var ok = 0; var all = 0
        fun check(name: String, pass: Boolean, info: String) { all++; if (pass) ok++; println((if (pass) "  ok   " else "  FAIL ") + name + "  " + info) }
        val noise = listOf(Task(title = "Купить корм коту", due = today.toString()), Task(title = "Стоматолог", due = today.plusDays(1).toString(), time = "18:00"))
        repeat(runs) {
            fun new(d: AppData) = d.tasks.filter { t -> noise.none { it.id == t.id } }
            listOf(
                "Тренировка в понедельник и четверг в 19:00" to 2,
                "Позвонить маме во вторник и в пятницу" to 2,
                "Полить цветы 12, 15 и 18 октября" to 3,
            ).forEach { (text, n) ->
                val t = new(ask(AppData(tasks = noise), text))
                check(text, t.size == 1 && t[0].slots.size == n && t[0].repeat == null, t.map { "${it.title} days=${it.slots.map { s -> s.date + (s.time?.let { h -> " $h" } ?: "") }} repeat=${it.repeat}" }.toString())
            }
            val r = new(ask(AppData(tasks = noise), "Каждый понедельник и четверг бассейн в 8 утра"))
            check("каждый пн и чт → повтор", r.size == 1 && r[0].repeat != null && r[0].slots.isEmpty(), r.map { "${it.title} days=${it.slots.size} repeat=${it.repeat}" }.toString())
            val one = new(ask(AppData(tasks = noise), "Завтра в 10 созвон с бухгалтерией"))
            check("один день → обычная дата", one.size == 1 && one[0].slots.isEmpty() && one[0].due != null, one.map { "${it.title} due=${it.due} days=${it.slots.size}" }.toString())
        }
        println("ИТОГ: $ok/$all")
    }
}
