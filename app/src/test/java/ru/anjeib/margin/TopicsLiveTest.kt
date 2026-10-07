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

/**
 * v2 «Темы» on the live hybrid (Alice / DeepSeek): the call that becomes a topic, news routed to it from the main screen,
 * a question written on the questions page. YA_LIVE=1 RUNS=n. A few roubles.
 */
class TopicsLiveTest {
    private val home = System.getProperty("user.home")
    private val key = runCatching { File("$home/.yc_margin_key").readText().trim() }.getOrDefault("")
    private val folder = runCatching { File("$home/.yc_margin_folder").readText().trim() }.getOrDefault("")
    private val today = LocalDate.now()

    private fun ask(d: AppData, text: String, scope: Scope, asQuestion: Boolean = false): AppData = runBlocking {
        val s = Settings(apiKey = "x")
        val sys = Assistant.systemPrompt(d, s, scope, query = text)
        val model = if (Assistant.isComplex(text, scope)) "deepseek-v4.1-flash" else "aliceai-llm-flash"
        val msg = Assistant.userMessage(text) + if (asQuestion) "\n[Written on this topic's questions page: a new question → add_subtasks (due/time/from only when said), no add_entry.]" else ""
        var r = Yandex.chat(key, folder, model, sys, listOf("user" to msg))
        if (Assistant.isBroken(r.content)) r = Yandex.chat(key, folder, model, sys, listOf("user" to msg))
        if (System.getenv("DEBUG") != null) println("  [$model] ${r.content.take(700)}")
        Topics.track(d, Assistant.apply(d, Assistant.parsePlan(r.content).copy(question = null), text, strict = true, focus = (scope as? Scope.InTask)?.taskId).data)
    }

    private fun noise() = listOf(
        Task(title = "Купить корм коту", due = today.toString()),
        Task(title = "Созвониться с Анной по договору", people = listOf("Анна"), due = today.plusDays(2).toString()),
        Task(title = "Стоматолог", due = today.plusDays(1).toString(), time = "18:00"),
        Task(title = "Позвонить в ЖЭК насчёт счётчиков", due = today.toString()),
    )

    @Test fun topics() {
        assumeTrue(System.getenv("YA_LIVE") != null && key.isNotBlank())
        L.lang = Lang.Ru
        val runs = System.getenv("RUNS")?.toIntOrNull() ?: 1
        var ok = 0; var all = 0
        fun check(name: String, pass: Boolean) { all++; if (pass) ok++; println((if (pass) "  ok   " else "  FAIL ") + name) }
        repeat(runs) { run ->
            println("run ${run + 1}")
            val call = Task(title = "Позвонить Игорю насчёт приказа о зачислении", due = today.toString(), people = listOf("Игорь"))
            var d = AppData(tasks = noise() + call)

            d = ask(d, "Созвонились. Приказ о зачислении в работе, будет через 2 недели. Нужно отправить документы об оплате в бухгалтерию.", Scope.InTask(call.id))
            val t1 = d.tasks.first { it.id == call.id }
            val wait = t1.subtasks.firstOrNull { it.from?.contains("Игор") == true }
            println("  title=«${t1.title}» subs=" + t1.subtasks.map { "${it.title}|done=${it.done}|from=${it.from}|due=${it.due}" })
            check("call → topic", t1.topic && !t1.done)
            check("call settled", t1.subtasks.firstOrNull()?.done == true)
            check("topic named by the matter", !t1.title.startsWith("Позвонить"))
            check("wait from Igor ~2 weeks", wait != null && wait.due?.let { kotlin.math.abs(java.time.temporal.ChronoUnit.DAYS.between(today.plusDays(14), LocalDate.parse(it))) <= 1 } == true)
            check("question about payment documents", t1.subtasks.any { it.from == null && !it.done && it.title.contains("бухгалтер", true) })
            check("no new tasks", d.tasks.size == 5)

            d = ask(d, "Игорь сказал, приказ подписан", Scope.Main)
            val t2 = d.tasks.first { it.id == call.id }
            check("news routed: wait received", t2.subtasks.firstOrNull { it.from != null }?.done == true)
            check("news routed: words in the feed", t2.entries.size >= 2)
            check("news routed: no new task", d.tasks.size == 5)

            d = ask(d, "Документы в бухгалтерию отправил", Scope.Main)
            val t3 = d.tasks.first { it.id == call.id }
            check("documents settled from main", t3.subtasks.first { it.title.contains("бухгалтер", true) }.done)

            d = ask(d, "Уточнить, нужен ли оригинал аттестата", Scope.InTask(call.id), asQuestion = true)
            val t4 = d.tasks.first { it.id == call.id }
            check("question page adds a question", t4.subtasks.any { !it.done && it.title.contains("аттестат", true) })

            val zhek = d.tasks.first { it.title.contains("ЖЭК") }
            d = ask(d, "Сказали прийти с паспортом во вторник до 13:00", Scope.InTask(zhek.id))
            val z = d.tasks.first { it.id == zhek.id }
            val tue = today.with(java.time.temporal.TemporalAdjusters.next(java.time.DayOfWeek.TUESDAY)).toString()
            println("  zhek title=«${z.title}» subs=" + z.subtasks.map { "${it.title}|done=${it.done}|due=${it.due}|${it.time}" })
            check("zhek → topic with a Tuesday question", z.topic && z.subtasks.any { !it.done && it.due == tue })
            check("zhek call settled", z.subtasks.first().done)
            val before = d.tasks.size
            d = ask(d, "Ремонт ванной: купить плитку до пятницы и вызвать плиточника", Scope.Main)
            val rem = d.tasks.drop(before)
            println("  remont: " + rem.map { "${it.title}|topic=${it.topic}|subs=${it.subtasks.map { s -> s.title + "@" + s.due }}" })
            check("matter with steps → one topic", rem.size == 1 && rem[0].topic && rem[0].subtasks.size >= 2)
        }
        // The quick model names a matter when the main one didn't.
        listOf("Позвонить в ЖЭК насчёт счётчиков" to "Сказали прийти с паспортом во вторник до 13:00", "Позвонить Игорю насчёт приказа о зачислении" to "Созвонились, приказ будет через 2 недели").forEach { (task, news) ->
            val r = runBlocking { Yandex.chat(key, folder, "aliceai-llm-flash", Assistant.topicNamePrompt(), listOf("user" to "Task: $task\nNews: $news")) }
            val name = Assistant.field(Assistant.parseObject(r.content), "title").orEmpty()
            println("  name: $task → «$name»")
            check("named as a matter: $name", name.length in 3..48 && !name.lowercase().startsWith("позвон"))
        }
        println("TOTAL $ok/$all")
    }
}
