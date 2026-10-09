package ru.anjeib.margin.book

import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import ru.anjeib.margin.ai.Yandex
import java.io.File
import java.time.LocalDate

/**
 * The AI re-reading phrases the phone was not sure of, on the live models — and what the merge makes of it.
 * YA_LIVE=1, key/folder from ~/.yc_margin_key and ~/.yc_margin_folder; MODELS=a,b. Costs a few roubles.
 */
class AiReadLiveTest {
    private val home = System.getProperty("user.home")
    private val key = runCatching { File("$home/.yc_margin_key").readText().trim() }.getOrDefault("")
    private val folder = runCatching { File("$home/.yc_margin_folder").readText().trim() }.getOrDefault("")
    private val today = LocalDate.of(2026, 10, 10)
    private val book = Examples.build(today)

    private val phrases = listOf(
        "созвонились с Олегом, он пришлёт смету по упаковке в четверг, а мне надо до пятницы обновить презентацию",
        "Лёша сказал что исходники баннеров уже не нужны, зато в понедельник в 11 созвон с его командой",
        "оплатил фигму. маме позвонить вечером",
    )

    @Test fun reread() = runBlocking {
        assumeTrue(System.getenv("YA_LIVE") == "1" && key.isNotBlank())
        val models = (System.getenv("MODELS") ?: "deepseek-v4.1-flash,aliceai-llm-flash").split(',')
        for (m in models) for (p in phrases) {
            val d = Read.draft(p, book, today, today, now = null)
            val b = book.add(d.lines)
            val t0 = System.currentTimeMillis()
            val r = runCatching { Yandex.chat(key, folder, m, AiRead.readPrompt(book, p, today, today, true), listOf("user" to p), json = true, maxTokens = 4000) }
            val ms = System.currentTimeMillis() - t0
            println("\n=== $m · ${ms}ms · $p")
            r.onFailure { println("  FAILED: ${it.message}") }
            r.getOrNull()?.let { reply ->
                println("  reply: ${reply.content.take(700)}  (${"%.3f".format(reply.rub)} ₽)")
                val reading = AiRead.parseRead(reply.content, book, today, p, null)
                if (reading == null) println("  parse: null") else {
                    val after = AiRead.merge(b, d.lines, reading, today)
                    after.lines.filter { l -> book.line(l.id) == null }.forEach { l -> println("  + ${l.kind} ${l.day} ${l.time ?: l.part ?: ""} ${l.until?.let { "до $it" } ?: ""} «${l.text}» ${l.who}") }
                    after.lines.filter { l -> book.line(l.id)?.let { it.state != l.state } == true }.forEach { l -> println("  ✓ ${l.text} → ${l.state}") }
                }
            }
        }
    }
}
