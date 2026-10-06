package ru.anjeib.margin

import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import ru.anjeib.margin.ai.Assistant
import ru.anjeib.margin.ai.Forward
import ru.anjeib.margin.ai.Yandex
import ru.anjeib.margin.data.AppData
import ru.anjeib.margin.data.Queued
import ru.anjeib.margin.data.Settings
import ru.anjeib.margin.i18n.L
import ru.anjeib.margin.i18n.Lang
import java.io.File

/**
 * Forwarded messages: someone else's words must become «ждём» / the user's own task, never the user's promise by mistake.
 * YA_LIVE=1, same key files as YandexCompareTest; forwards always go to DeepSeek (as in the app). RUNS=n.
 */
class ForwardLiveTest {
    private val home = System.getProperty("user.home")
    private val key = runCatching { File("$home/.yc_margin_key").readText().trim() }.getOrDefault("")
    private val folder = runCatching { File("$home/.yc_margin_folder").readText().trim() }.getOrDefault("")
    private val today = java.time.LocalDate.now()
    private fun next(d: java.time.DayOfWeek) = today.with(java.time.temporal.TemporalAdjusters.next(d)).toString()
    private val wednesday = next(java.time.DayOfWeek.WEDNESDAY)
    private val friday = next(java.time.DayOfWeek.FRIDAY)
    private val tenth = (if (today.dayOfMonth < 10) today.withDayOfMonth(10) else today.withDayOfMonth(10).plusMonths(1)).toString()
    private val now = java.time.LocalDateTime.now().withSecond(0).withNano(0).toString()

    data class Case(val name: String, val q: Queued, val check: (AppData, AppData) -> Boolean)

    private val cases = listOf(
        Case("Марина пришлёт → ждём", Queued(text = "Привет! Правки по макету пришлю до среды, ок?", via = "telegram", from = "Марина Петрова", sentAt = now)) { _, d ->
            d.tasks.any { it.waitingFor?.contains("Марин") == true && it.due == wednesday && it.promisedTo == null }
        },
        Case("Виктор ждёт оплату → моя задача", Queued(text = "Добрый день. Напоминаю, оплату за октябрь жду до 10-го числа.", via = "telegram", from = "Виктор Аренда", sentAt = now)) { _, d ->
            d.tasks.any { it.title.contains("оплат", true) && it.due == tenth && it.waitingFor == null }
        },
        Case("Мой ответ в переписке → обещание", Queued(text = "Марина Петрова: Когда будут тексты для сайта?\nЯ: Пришлю в пятницу", via = "telegram", from = "Марина Петрова", sentAt = now)) { _, d ->
            d.tasks.any { it.promisedTo?.contains("Марин") == true && it.due == friday && it.waitingFor == null }
        },
        Case("Счёт из почты → оплатить до пятницы", Queued(text = "Добрый день! Направляем счёт на оплату упаковки, срок оплаты — до пятницы. Реквизиты во вложении.\n\nС уважением, ООО «ПакетПро»", via = "mail", from = "ООО ПакетПро", subject = "Счёт №45", sentAt = now)) { _, d ->
            d.tasks.any { it.title.contains("оплат", true) && it.due == friday && it.waitingFor == null }
        },
        Case("Просто сведения → без задачи", Queued(text = "Адрес офиса: Садовая, 12, 3 этаж, код домофона 45", via = "telegram", from = "Ирина Соколова", sentAt = now)) { b, d ->
            d.tasks.size == b.tasks.size && (d.notes.isNotEmpty() || d.persons.any { it.address != null || it.name.contains("Ирин") })
        },
        Case("Комментарий называет автора", Queued(text = "Скину график смен до среды", via = "copy", comment = "это Лена", sentAt = now)) { _, d ->
            d.tasks.any { it.waitingFor?.contains("Лен") == true && it.due == wednesday }
        },
        Case("Скопированное без автора → не выдумывать", Queued(text = "Созвон по проекту перенесли на пятницу 15:00", via = "copy", sentAt = now)) { _, d ->
            d.tasks.any { it.due == friday && it.time == "15:00" && it.waitingFor == null && it.promisedTo == null }
        },
    )

    @Test
    fun forwards() = runBlocking {
        assumeTrue(System.getenv("YA_LIVE") == "1" && key.isNotBlank() && folder.isNotBlank())
        L.lang = Lang.Ru
        val s = Settings(apiKey = "x")
        val runs = System.getenv("RUNS")?.toIntOrNull() ?: 2
        var pass = 0; var total = 0
        repeat(runs) {
            cases.forEach { c ->
                val b = AppData()
                val ok = try {
                    val sys = Assistant.systemPrompt(b, s.copy(askWhenUnclear = false), query = c.q.text)
                    var r = Yandex.chat(key, folder, "deepseek-v4.1-flash", sys, listOf("user" to Assistant.userMessage(Forward.prompt(c.q))))
                    if (Assistant.isBroken(r.content)) r = Yandex.chat(key, folder, "deepseek-v4.1-flash", sys, listOf("user" to Assistant.userMessage(Forward.prompt(c.q))))
                    val d = Assistant.apply(b, Assistant.parsePlan(r.content).copy(question = null), Forward.source(c.q), strict = true).data
                    val good = runCatching { c.check(b, d) }.getOrDefault(false)
                    if (!good) println("  FAIL ${c.name}: " + r.content.replace("\n", " ").take(600))
                    good
                } catch (e: Exception) { println("  ${c.name} error: ${e.message?.take(200)}"); false }
                total++; if (ok) pass++
                println((if (ok) "ok   " else "FAIL ") + c.name)
            }
        }
        println("\n===== FORWARDS $pass/$total =====")
    }
}
