package ru.anjeib.margin

import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import ru.anjeib.margin.ai.Assistant
import ru.anjeib.margin.ai.Scope
import ru.anjeib.margin.ai.Yandex
import ru.anjeib.margin.data.AppData
import ru.anjeib.margin.data.Settings
import ru.anjeib.margin.data.findSub
import ru.anjeib.margin.i18n.L
import ru.anjeib.margin.i18n.Lang
import java.io.File

/**
 * Same scenario on several Yandex models, each step checked automatically.
 * YA_LIVE=1, key/folder from ~/.yc_margin_key and ~/.yc_margin_folder; MODELS=a,b,c; RUNS=n. Costs a few roubles.
 */
class YandexCompareTest {
    private val home = System.getProperty("user.home")
    private val key = runCatching { File("$home/.yc_margin_key").readText().trim() }.getOrDefault("")
    private val orKey = System.getenv("OPENROUTER_KEY") ?: ""
    private val folder = runCatching { File("$home/.yc_margin_folder").readText().trim() }.getOrDefault("")

    data class Step(val text: String, val scopeTask: String? = null, val check: (AppData, AppData) -> Boolean)

    private val steps = listOf(
        Step("На этой неделе разобраться с vuzburo и запустить. Дима сделает дизайн, на мне тексты и оплата.") { _, d ->
            val t = d.tasks.firstOrNull { it.project == "vuzburo" }
            t != null && t.due == "2026-10-11" && t.subtasks.size >= 2 && t.people.any { it.contains("Дим") }
        },
        Step("для vuzburo ещё купить домен и настроить почту") { b, d ->
            d.tasks.size == b.tasks.size && d.tasks.first { it.project == "vuzburo" }.subtasks.size >= b.tasks.first { it.project == "vuzburo" }.subtasks.size + 2
        },
        Step("Саша обещал прислать смету, через пару дней напомнить. Ещё почитать про новый Claude, купить корм коту и подумать над главной vuzburo — мне кажется, нужен калькулятор стоимости и отзывы") { b, d ->
            val new = d.tasks.filter { t -> b.tasks.none { it.id == t.id } }
            new.size >= 3 && new.any { it.waitingFor?.contains("Саш") == true } && d.notes.any { it.project == "vuzburo" }
        },
        Step("созвониться с Анной по договору") { b, d -> d.tasks.size == b.tasks.size + 1 && d.tasks.last().let { t -> t.people.any { it.contains("Анн") } || t.title.contains("Анн") } },
        Step("завтра в 18:00 стоматолог") { _, d -> d.tasks.any { it.title.contains("томатолог", true) && it.due == "2026-10-06" && it.time == "18:00" } },
        Step("Дима должен прислать макет в среду") { b, d ->
            val new = d.tasks.filter { t -> b.tasks.none { it.id == t.id } }
            new.any { it.waitingFor?.contains("Дим") == true && it.due == "2026-10-07" } && d.tasks.none { it.project == "vuzburo" && it.subtasks.size >= 2 && it.waitingFor != null }
        },
        Step("Дима — наш дизайнер, его телефон +7 999 123-45-67") { _, d -> d.persons.any { it.name.contains("Дим") && it.phone?.contains("999") == true && it.about != null } },
        Step("идея: на главной vuzburo сделать блок с ценами без «от»") { b, d ->
            d.tasks.size == b.tasks.size && d.notes.any { n -> n.project == "vuzburo" && n.entries.any { it.text.contains("цен", true) } }
        },
        Step("Добавь в заметку: зайти в Бахчисарай в среду.") { _, d ->
            d.notes.any { n -> n.project != "vuzburo" && (n.title + n.entries.joinToString { it.text }).contains("Бахчисара", true) }
        },
        Step("Обещал Анне показать калькулятор до пятницы") { _, d ->
            d.tasks.any { it.promisedTo?.contains("Анн") == true && it.due == "2026-10-09" && it.waitingFor == null }
        },
        Step("Каждый вторник в 10:00 планёрка по vuzburo") { _, d ->
            d.tasks.any { it.repeat?.freq == "weekly" && it.repeat.days == listOf(2) && it.time == "10:00" }
        },
        Step("это у доктора Иванова на Ленина 5, не забыть полис", scopeTask = "томатолог") { _, d ->
            d.tasks.first { it.title.contains("томатолог", true) }.let { t -> (t.entries.any { it.text.contains("Ленина") } || t.place?.contains("Ленина") == true) && t.entries.any { it.text.contains("полис", true) } }
        },
        Step("перенеси на пятницу в 15:00", scopeTask = "томатолог") { _, d ->
            d.tasks.first { it.title.contains("томатолог", true) }.let { it.due == "2026-10-09" && it.time == "15:00" }
        },
        Step("разбей на шаги: позвонить подтвердить, взять полис, доехать", scopeTask = "томатолог") { _, d ->
            val subs = d.tasks.first { it.title.contains("томатолог", true) }.subtasks.map { it.title.lowercase() }
            subs.any { "позвон" in it } && subs.any { "полис" in it } && subs.any { "доех" in it }
        },
    )

    /** A lived-in planner: 120 unrelated tasks, 30 notes, 15 people — the right items must still be found. */
    private fun noise(): AppData {
        val projects = listOf("дом", "здоровье", "учёба", "машина", "дача")
        val names = listOf("Олег", "Ирина", "Пётр", "Марина", "Кирилл", "Светлана", "Георгий", "Вера", "Тимур", "Лена", "Роман", "Ольга", "Фёдор", "Нина", "Глеб")
        val verbs = listOf("Позвонить", "Оплатить", "Проверить", "Заказать", "Починить", "Записаться", "Отправить", "Подготовить")
        val things = listOf("счёт за свет", "шины", "отчёт по учёбе", "кран на кухне", "страховку", "документы для дачи", "подарок Ирине", "встречу с Олегом", "анализы", "забор")
        val tasks = (0 until 120).map { i ->
            ru.anjeib.margin.data.Task(title = "${verbs[i % verbs.size]} ${things[i % things.size]} ${i / 10 + 1}", project = projects[i % projects.size],
                due = java.time.LocalDate.of(2026, 10, 5).plusDays((i % 40).toLong()).toString(), people = listOf(names[i % names.size]),
                subtasks = listOf(ru.anjeib.margin.data.Subtask(title = "Шаг ${i}")))
        }
        val notes = (0 until 30).map { i -> ru.anjeib.margin.data.Note(title = "${projects[i % 5]} · заметка $i", project = projects[i % 5],
            entries = listOf(ru.anjeib.margin.data.Entry(text = "Мысль про ${things[i % things.size]}"))) }
        return AppData(tasks = tasks, notes = notes, persons = names.map { ru.anjeib.margin.data.Person(name = it) }, projects = projects)
    }

    @Test
    fun compare() = runBlocking {
        assumeTrue(System.getenv("YA_LIVE") == "1" && key.isNotBlank() && folder.isNotBlank())
        L.lang = Lang.Ru
        val models = (System.getenv("MODELS") ?: "aliceai-llm-flash,qwen3.6-35b-a3b,deepseek-v4.1-flash,gpt-oss-20b").split(',')
        val runs = System.getenv("RUNS")?.toIntOrNull() ?: 2
        val s = Settings(apiKey = "x")
        val report = StringBuilder()
        for (m in models) {
            var pass = 0; var total = 0; var ms = 0L; var rub = 0.0; var inTok = 0; var cached = 0; var calls = 0; var errors = 0; var outTok = 0; var retries = 0; var complex = 0
            val fails = mutableMapOf<Int, Int>()
            repeat(runs) {
                var d = if (System.getenv("NOISE") == "1") noise() else AppData()
                steps.forEachIndexed { i, st ->
                    val before = d
                    val tasksBefore = d.tasks.size
                    val scope = st.scopeTask?.let { name -> d.tasks.firstOrNull { it.title.contains(name, true) }?.let { Scope.InTask(it.id) } } ?: Scope.Main
                    val ok = try {
                        val t0 = System.currentTimeMillis()
                        val content: String
                        if (m.startsWith("or:")) {
                            // Baseline: the current OpenRouter model, cost converted at ~100 ₽/$.
                            val r = ru.anjeib.margin.ai.OpenRouter.chat(orKey, m.removePrefix("or:"), Assistant.systemPrompt(d, s, scope, query = st.text), listOf("user" to Assistant.userMessage(st.text)), quick = true)
                            content = r.content; rub += r.costUsd * 100
                        } else {
                            val sys = Assistant.systemPrompt(d, s, scope, query = st.text)
                            // "hybrid": simple phrases → Alice Flash, complex ones → DeepSeek (decided locally).
                            val mm = if (m == "hybrid") (if (Assistant.isComplex(st.text, scope)) "deepseek-v4.1-flash" else "aliceai-llm-flash").also { if (it.startsWith("deep")) complex++ } else m
                            var r = Yandex.chat(key, folder, mm, sys, listOf("user" to Assistant.userMessage(st.text)))
                            // Same rule as the app: an unusable reply is asked once more.
                            if (Assistant.isBroken(r.content)) { rub += r.rub; r = Yandex.chat(key, folder, mm, sys, listOf("user" to Assistant.userMessage(st.text))); retries++ }
                            content = r.content; rub += r.rub; inTok += r.usage.input; cached += r.usage.cached; outTok += r.usage.output
                        }
                        ms += System.currentTimeMillis() - t0; calls++
                        d = Assistant.apply(d, Assistant.parsePlan(content).copy(question = null), st.text, strict = true, focus = (scope as? Scope.InTask)?.taskId).data
                        val good = runCatching { st.check(before, d) }.getOrDefault(false)
                        if (!good && System.getenv("DEBUG") == "1") println("  FAIL $m step ${i + 1}: " + content.replace("\n", " ").take(500))
                        good
                    } catch (e: Exception) { errors++; println("  $m step ${i + 1} error: ${e.message?.take(200)}"); false }
                    total++; if (ok) pass++ else fails[i + 1] = (fails[i + 1] ?: 0) + 1
                }
            }
            val line = "%-22s %3d/%-3d  %5.1f s/msg  %6.3f ₽/msg  in %5d (cached %4d) out %4d tok  retries %d  errors %d  failed: %s".format(
                m, pass, total, ms / 1000.0 / calls.coerceAtLeast(1), rub / calls.coerceAtLeast(1),
                inTok / calls.coerceAtLeast(1), cached / calls.coerceAtLeast(1), outTok / calls.coerceAtLeast(1), retries, errors, fails.toSortedMap())
            val full = line + if (m == "hybrid") "  → DeepSeek for $complex of $calls" else ""
            println(full); report.appendLine(full)
        }
        println("\n===== SUMMARY =====\n$report")
    }

    /** Phrases the AI testers tripped on (October 2026), each from its own small seeded state. */
    data class Case(val name: String, val text: String, val seed: List<ru.anjeib.margin.data.Task>, val scopeTask: String? = null, val check: (AppData, AppData) -> Boolean)

    private fun task(title: String, due: String? = null, time: String? = null, project: String? = null, people: List<String> = emptyList(),
                     repeat: ru.anjeib.margin.data.Repeat? = null) =
        ru.anjeib.margin.data.Task(title = title, due = due, time = time, project = project, people = people, repeat = repeat)

    /** Nothing but [touched] changed: same count, same dates/times/people/promises on every other task. */
    private fun untouched(b: AppData, d: AppData, vararg touched: String): Boolean {
        fun sig(x: ru.anjeib.margin.data.Task) = listOf(x.title, x.due, x.time, x.repeat, x.waitingFor, x.promisedTo, x.project, x.people)
        val keep = b.tasks.filter { t -> touched.none { t.title.contains(it, true) } }
        return keep.all { o -> d.tasks.find { it.id == o.id }?.let { sig(it) == sig(o) } == true }
    }

    private val cases = listOf(
        Case("мука: повтор, не ожидание", "кажд понедельник заказать муку у тимура", emptyList()) { b, d ->
            val t = d.tasks.firstOrNull { it.title.contains("мук", true) }
            t != null && t.waitingFor == null && t.repeat?.freq == "weekly" && t.repeat.days == listOf(1) && d.tasks.size == b.tasks.size + 1
        },
        Case("аренда: первое число — в будущем", "аренда первого числа каждый месяц", emptyList()) { _, d ->
            val t = d.tasks.firstOrNull { it.title.contains("аренд", true) }
            t != null && t.repeat?.freq == "monthly" && t.due == "2026-11-01"
        },
        Case("надиктовка: не сливать с арендой",
            "лена до пятницы сдаёт график смен я жду ещё обещал виктору арендодателю оплату до десятого сломалась тестомесилка на садовой вызвать мастера завтра утром купить кассовую ленту",
            listOf(task("Оплатить аренду", due = "2026-11-01", repeat = ru.anjeib.margin.data.Repeat("monthly", days = listOf(1))))) { b, d ->
            untouched(b, d) && d.tasks.any { it.promisedTo?.contains("Виктор", true) == true && it.due == "2026-10-10" } &&
                d.tasks.any { it.waitingFor?.contains("Лен", true) == true && it.due == "2026-10-09" } &&
                d.tasks.any { it.title.contains("мастер", true) && it.due == "2026-10-06" }
        },
        Case("перенос: время сохраняется", "перенеси мастера на послезавтра",
            listOf(task("Вызвать мастера по тестомесилке", due = "2026-10-06", time = "09:00", project = "садовая"), task("Купить кассовую ленту", due = "2026-10-06"))) { b, d ->
            untouched(b, d, "мастер") && d.tasks.first { it.title.contains("мастер", true) }.let { it.due == "2026-10-07" && it.time == "09:00" }
        },
        Case("подзадачи в открытой карточке", "подзадачи: собрать мудборд, 3 варианта эскизов, финальная презентация", olga(), scopeTask = "логотип") { b, d ->
            untouched(b, d, "логотип") && d.tasks.size == b.tasks.size && d.tasks.first { it.title.contains("логотип", true) }.subtasks.size == 3
        },
        Case("подзадачи из главного поля", "к логотипу для кофейни подзадачи: собрать мудборд, 3 варианта эскизов, финальная презентация", olga()) { b, d ->
            untouched(b, d, "логотип") && d.tasks.size == b.tasks.size && d.tasks.first { it.title.contains("логотип", true) }.subtasks.size == 3
        },
        Case("срок у дела человека", "Дента+: Катя делает макеты главной к четвергу, Артём верстает после макетов, Ирина должна прислать тексты до среды", emptyList()) { _, d ->
            // Katya's deadline must survive somewhere: as a task (subtasks have no dates).
            d.tasks.any { (it.title.contains("макет", true) || it.people.any { p -> p.contains("Кат") }) && it.due == "2026-10-08" } &&
                d.tasks.any { it.waitingFor?.contains("Ирин") == true && it.due == "2026-10-07" }
        },
        Case("заказ у поставщика — не ожидание", "заказать упаковку у ПакетПро до среды", emptyList()) { _, d ->
            d.tasks.any { it.title.contains("упаков", true) && it.waitingFor == null && it.due == "2026-10-07" }
        },
    )

    private fun olga() = listOf(
        task("Логотип для кофейни «Зерно»", due = "2026-10-09", project = "зерно"),
        task("Ответить Игорю по эскизам", due = "2026-10-06"),
        task("Созвон с Мариной", due = "2026-10-06", time = "11:00", people = listOf("Марина")),
        task("Отправить Марине мудборд", project = "портфолио"),
    )

    @Test
    fun testerCases() = runBlocking {
        assumeTrue(System.getenv("YA_LIVE") == "1" && key.isNotBlank() && folder.isNotBlank())
        L.lang = Lang.Ru
        val models = (System.getenv("MODELS") ?: "hybrid,aliceai-llm-flash,deepseek-v4.1-flash").split(',')
        val runs = System.getenv("RUNS")?.toIntOrNull() ?: 2
        val s = Settings(apiKey = "x")
        val report = StringBuilder()
        for (m in models) {
            var pass = 0; var total = 0; var rub = 0.0
            val fails = mutableMapOf<String, Int>()
            repeat(runs) {
                cases.forEach { c ->
                    val base = noise()
                    val b = base.copy(tasks = base.tasks + c.seed)
                    val scope = c.scopeTask?.let { n -> b.tasks.firstOrNull { it.title.contains(n, true) }?.let { Scope.InTask(it.id) } } ?: Scope.Main
                    val ok = try {
                        val sys = Assistant.systemPrompt(b, s, scope, query = c.text)
                        val mm = if (m == "hybrid") (if (Assistant.isComplex(c.text, scope)) "deepseek-v4.1-flash" else "aliceai-llm-flash") else m
                        var r = Yandex.chat(key, folder, mm, sys, listOf("user" to Assistant.userMessage(c.text)))
                        if (Assistant.isBroken(r.content)) { rub += r.rub; r = Yandex.chat(key, folder, mm, sys, listOf("user" to Assistant.userMessage(c.text))) }
                        rub += r.rub
                        val d = Assistant.apply(b, Assistant.parsePlan(r.content).copy(question = null), c.text, strict = true, focus = (scope as? Scope.InTask)?.taskId).data
                        val good = runCatching { c.check(b, d) }.getOrDefault(false)
                        if (!good) println("  FAIL $m [${c.name}] via $mm: " + r.content.replace("\n", " ").take(600))
                        good
                    } catch (e: Exception) { println("  $m [${c.name}] error: ${e.message?.take(200)}"); false }
                    total++; if (ok) pass++ else fails[c.name] = (fails[c.name] ?: 0) + 1
                }
            }
            val line = "%-22s %3d/%-3d  %6.3f ₽/msg  failed: %s".format(m, pass, total, rub / total.coerceAtLeast(1), fails)
            println(line); report.appendLine(line)
        }
        println("\n===== TESTER CASES =====\n$report")
    }
}
