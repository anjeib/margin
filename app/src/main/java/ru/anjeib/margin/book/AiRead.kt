package ru.anjeib.margin.book

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalDate
import java.time.LocalDateTime

/*
 * The AI's two jobs in «Поля» (DESIGN.md §6), as plain text in and plain data out — the call itself is the screen's:
 *  • re-reading a phrase the phone was not sure of: the lines are already on the page; what the AI reads differently
 *    becomes a correction Margin made («~~чт~~ пт» on the margin), with a way back;
 *  • answering a question the phone could not: a short sentence and the lines it is about.
 * The AI never changes the notebook itself — [merge] does, line by line, and skips whatever the person touched meanwhile.
 */
object AiRead {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun norm(s: String) = s.lowercase().replace('ё', 'е')

    /** What the AI is shown: the lines that matter for these words first, then the near days; never the whole book. */
    fun context(book: Book, words: String, today: LocalDate, max: Int = 50): String {
        val stems = Regex("\\p{L}{4,}").findAll(norm(words)).map { it.value.take(5) }.toSet()
        fun score(l: Line): Int = stems.count { s -> norm(l.text).contains(s) || l.who.any { norm(it.name).startsWith(s.take(4)) } }
        val window = book.lines.filter { l ->
            l.parentId == null && (l.open || l.doneAt?.let { runCatching { LocalDateTime.parse(it).toLocalDate() }.getOrNull() }?.isAfter(today.minusDays(8)) == true) &&
                (l.lastDay()?.let { !it.isBefore(today.minusDays(30)) && !it.isAfter(today.plusDays(60)) } ?: true)
        }
        val picked = window.sortedWith(compareByDescending<Line> { score(it) }.thenBy { it.lastDay()?.let { d -> kotlin.math.abs(daysBetween(today, d)) } ?: 99L }).take(max)
        return picked.joinToString("\n") { l ->
            listOf(
                l.id,
                if (l.open) l.kind.name.lowercase() else "done",
                listOfNotNull(l.day, l.time, l.until?.let { "until $it" }).joinToString(" ").ifBlank { "no day" },
                l.text,
                l.who.joinToString(" ") { w -> when (w.dir) { Dir.To -> "promised to ${w.name}"; Dir.From -> "expected from ${w.name}"; Dir.With -> "with ${w.name}" } },
                book.thread(l.threadId)?.title?.let { "thread: $it" } ?: "",
            ).filter { it.isNotBlank() }.joinToString(" | ")
        }
    }

    private fun today(today: LocalDate) = "$today, ${today.dayOfWeek.name.lowercase()}"

    /**
     * [book] is the notebook before the phrase was written — the phone's own lines are not shown, or the AI «closes» them.
     * The examples carry real dates from [today]: a model copies the shape it is shown.
     */
    fun readPrompt(book: Book, words: String, page: LocalDate, today: LocalDate, ru: Boolean): String {
        val wed = today.with(java.time.temporal.TemporalAdjusters.next(java.time.DayOfWeek.WEDNESDAY))
        val fri = today.with(java.time.temporal.TemporalAdjusters.nextOrSame(java.time.DayOfWeek.FRIDAY))
        val tomorrow = today.plusDays(1)
        val mon = today.with(java.time.temporal.TemporalAdjusters.next(java.time.DayOfWeek.MONDAY))
        return """
You turn what a person wrote in their paper notebook into lines. Today is ${today(today)}. The page open now: $page.
Reply with JSON only: {"lines":[...],"done":["id",...],"dropped":["id",...]}. Leave out any field that is null or empty.
A line: {"text":"...","kind":"todo|event|note","day":"YYYY-MM-DD","time":"HH:MM","part":"morning|day|evening","until":"YYYY-MM-DD",
"repeat":{"freq":"daily|weekly|monthly|yearly","interval":1,"days":[1-7]},"who":[{"name":"...","dir":"to|from|with"}],"place":"...","thread":"..."}
Rules:
- "text" is the person's own words without the words of time and day (they go to the margin) and without fillers (надо, нужно, не забыть). First letter capital. Do not rephrase.
- One line per thing. A meeting or call at a set time is an event. A thought or a fact with nothing to do is a note.
- "who": "to" — the person promised it to someone; "from" — someone will send, bring, pay or do it for the person: then "text" names the thing expected («Смета по упаковке»), not who sends it. "with" — just involves them. Names in the nominative.
- "day": the day named in the words, else the open page. "until": a soft deadline (до пятницы, на этой неделе) — then "day" is the open page.
- «утром», «днём», «вечером» without an hour are "part"; never invent an hour.
- When the words say that a listed line itself has happened (оплатил интернет → «Оплатить интернет»; прислали макет → «Макет»), put that line's id into "done"; when it is no longer needed or cancelled (уже не нужно, отменили) — into "dropped". Do not write such a line again. The same person is not enough: «созвонились с Олегом» does not close «Показать логотип Олегу». Whatever new is said becomes new lines. Only ids from the list below.
- Each thing keeps its own day: in «он пришлёт смету в четверг, а мне до пятницы обновить презентацию» the estimate is on Thursday, the presentation is until Friday.
- "thread": a title from the thread list, only when the words are clearly about it.
- Never invent people, days or things to do. ${if (ru) "The words are Russian — keep them Russian." else "Keep the person's language."}
Examples:
«Дима пришлёт макет в среду» → {"lines":[{"text":"Макет","kind":"todo","day":"$wed","who":[{"name":"Дима","dir":"from"}]}]}
«обещал Лёше скинуть фото до пятницы» → {"lines":[{"text":"Скинуть фото","kind":"todo","day":"$page","until":"$fri","who":[{"name":"Лёша","dir":"to"}]}]}
«завтра в 15 встреча с Анной» → {"lines":[{"text":"Встреча с Анной","kind":"event","day":"$tomorrow","time":"15:00","who":[{"name":"Анна","dir":"with"}]}]}
«оплатил интернет, а вечером позвонить маме» with the line «a1b2 | todo | ${today.minusDays(1)} | Оплатить интернет» → {"lines":[{"text":"Позвонить маме","kind":"todo","day":"$page","part":"evening"}],"done":["a1b2"]}
«поговорили с Димой, он вернёт книгу в понедельник, а мне до среды сдать отчёт» with the line «c3d4 | todo | $page | Отправить Диме договор» → {"lines":[{"text":"Книга","kind":"todo","day":"$mon","who":[{"name":"Дима","dir":"from"}]},{"text":"Сдать отчёт","kind":"todo","day":"$page","until":"$wed"}]}
Threads: ${book.threads.filter { it.closedAt == null }.joinToString("; ") { it.title }.ifBlank { "none" }}
People: ${book.persons.joinToString(", ") { it.name }.ifBlank { "none" }}
Open lines (id | kind | day | text | people | thread):
${context(book, words, today)}
""".trimIndent()
    }

    fun askPrompt(book: Book, question: String, today: LocalDate, ru: Boolean): String = """
You answer a question about a person's notebook. Today is ${today(today)}, the time now is ${java.time.LocalTime.now().let { "%02d:%02d".format(it.hour, it.minute) }}: what was earlier today is past.
Reply with JSON only: {"say":"...","ids":["id",...]} — "say" is the answer in ${if (ru) "Russian" else "English"}: one or two short calm sentences, no exclamation marks, no emoji, never about yourself; "ids" are the lines the answer is about (at most 6), in the order that helps.
If the notebook does not hold the answer, say so plainly and give no ids. Do not invent anything. A repeating line repeats on its days — say so.
When asked what is coming, name what the person waits for from others too ("expected from").
Lines (id | kind or done | day | text | people | thread):
${context(book, question, today, max = 70)}
""".trimIndent()

    /** The AI's reading: new lines (not yet in the book), the ids it found reported as done and as not needed any more. */
    data class Reading(val lines: List<Line>, val done: List<String>, val dropped: List<String> = emptyList())

    private fun JsonObject.str(k: String) = this[k]?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }?.trim()?.ifBlank { null }?.takeIf { it != "null" }

    /** The JSON object in a reply, even wrapped in ```json fences or a word of preface. */
    private fun body(text: String): JsonObject? {
        val a = text.indexOf('{'); val b = text.lastIndexOf('}')
        if (a < 0 || b <= a) return null
        return runCatching { json.parseToJsonElement(text.substring(a, b + 1)).jsonObject }.getOrNull()
    }

    private val hhmm = Regex("^(\\d{1,2})[:.](\\d{2})$")
    private fun time(s: String?) = s?.let { hhmm.find(it) }?.let { m ->
        val h = m.groupValues[1].toInt(); val mm = m.groupValues[2].toInt()
        if (h in 0..23 && mm in 0..59) "%02d:%02d".format(h, mm) else null
    }
    private fun date(s: String?) = s?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() }

    fun parseRead(reply: String, book: Book, page: LocalDate, source: String, threadId: String?, noDay: Boolean = false): Reading? {
        val o = body(reply) ?: return null
        val arr = runCatching { o["lines"]?.jsonArray }.getOrNull() ?: JsonArray(emptyList())
        val lines = arr.mapNotNull { e ->
            val x = runCatching { e.jsonObject }.getOrNull() ?: return@mapNotNull null
            val text = x.str("text")?.replaceFirstChar { it.uppercase() } ?: return@mapNotNull null
            val kind = when (x.str("kind")) { "event" -> Kind.Event; "note" -> Kind.Note; else -> Kind.Todo }
            val day = date(x.str("day"))
            val until = date(x.str("until"))
            val rep = runCatching { x["repeat"]?.jsonObject }.getOrNull()?.let { r ->
                val f = r.str("freq")?.takeIf { it in setOf("daily", "weekly", "monthly", "yearly") } ?: return@let null
                Repeat(f, r["interval"]?.let { runCatching { it.jsonPrimitive.intOrNull }.getOrNull() } ?: 1,
                    runCatching { r["days"]?.jsonArray?.mapNotNull { d -> d.jsonPrimitive.intOrNull?.takeIf { it in 1..7 } } }.getOrNull() ?: emptyList())
            }
            val who = runCatching { x["who"]?.jsonArray }.getOrNull()?.mapNotNull { w ->
                val wo = runCatching { w.jsonObject }.getOrNull() ?: return@mapNotNull null
                val n = wo.str("name") ?: return@mapNotNull null
                Who(book.person(n)?.name ?: n, when (wo.str("dir")) { "to" -> Dir.To; "from" -> Dir.From; else -> Dir.With })
            }?.filter { mentions(source, it.name) } ?: emptyList()
            val thread = x.str("thread")?.let { t -> book.threads.find { it.closedAt == null && it.title.equals(t, true) }?.id }
            val hm = time(x.str("time"))
            Line(text = text, kind = kind,
                day = (day ?: if (noDay || (threadId != null && until == null && rep == null && hm == null)) null else page)?.toString(),
                time = hm, part = x.str("part")?.takeIf { hm == null && it in setOf("morning", "day", "afternoon", "evening", "night") },
                until = until?.toString(), repeat = rep, who = who, place = x.str("place"),
                threadId = thread ?: threadId, source = source)
        }
        val done = runCatching { o["done"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } }.getOrNull()
            ?.filter { id -> book.line(id)?.let { plausiblyDone(it, source, book) } == true } ?: emptyList()
        val dropped = runCatching { o["dropped"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } }.getOrNull()
            ?.filter { id -> id !in done && book.line(id)?.let { plausiblyDone(it, source, book) } == true } ?: emptyList()
        if (lines.isEmpty() && done.isEmpty() && dropped.isEmpty()) return null
        return Reading(lines, done, dropped)
    }

    // ---- a line closed by words: the AI suggests, the words must back it ----

    private val latin = mapOf('a' to "а", 'b' to "б", 'c' to "к", 'd' to "д", 'e' to "е", 'f' to "ф", 'g' to "г", 'h' to "х", 'i' to "и", 'j' to "дж",
        'k' to "к", 'l' to "л", 'm' to "м", 'n' to "н", 'o' to "о", 'p' to "п", 'q' to "к", 'r' to "р", 's' to "с", 't' to "т", 'u' to "у", 'v' to "в",
        'w' to "в", 'x' to "кс", 'y' to "й", 'z' to "з")
    /** Common verbs say nothing about which line: «позвонил маме» must not close «Позвонить в банк». */
    private val verbs = setOf("позв", "напи", "купи", "сдел", "отпр", "забр", "запл", "опла", "зака", "прис", "скин", "верн", "подг", "найт", "узна",
        "спро", "пров", "сход", "запи", "отве", "полу", "пере", "обсу", "встр", "созв", "дого", "пого", "сказ", "обещ", "нужн", "надо")
    private val gave = Regex("(?<!\\p{L})(?:прислал\\p{L}*|отправил\\p{L}*|скинул\\p{L}*|вернул\\p{L}*|отдал\\p{L}*|передал\\p{L}*|принес\\p{L}*|принёс|" +
        "оплатил\\p{L}*|сделал\\p{L}*|подписал\\p{L}*|ответил\\p{L}*|sent|returned|paid|gave|signed|replied)", RegexOption.IGNORE_CASE)

    internal fun stems(text: String, names: List<String>): Set<String> {
        val nameStems = names.map { norm(it).take(4) }
        // A Russian word with a capital inside the words is a name or a surname («Петров», «Олегу»): it says who, not what.
        val proper = Regex("(?<=\\s)[А-ЯЁ][а-яё]{2,}").findAll(text).map { norm(it.value).take(4) }.toSet()
        return Regex("\\p{L}{4,}").findAll(norm(text)).map { m -> m.value.map { ch -> latin[ch] ?: ch.toString() }.joinToString("").take(4) }
            .filter { st -> st !in verbs && st !in proper && nameStems.none { st.startsWith(it.take(3)) } }.toSet()
    }

    /** The words name this person (any case, any alphabet): the AI may not bring a person the phrase does not mention. */
    fun mentions(words: String, name: String): Boolean {
        val n = norm(name).map { ch -> latin[ch] ?: ch.toString() }.joinToString("")
        val stem = n.take(maxOf(3, minOf(5, n.length - 1)))
        val w = norm(words).map { ch -> latin[ch] ?: ch.toString() }.joinToString("")
        return stem.length >= 3 && Regex("(?<!\\p{L})" + Regex.escape(stem)).containsMatchIn(w)
    }

    /** The words back the AI's «done»: a word of the line itself, or the one we waited for gave it. */
    fun plausiblyDone(l: Line, words: String, book: Book): Boolean {
        val names = book.persons.flatMap { listOf(it.name) + it.aliases } + l.who.map { it.name }
        if (stems(l.text, names).intersect(stems(words, names)).isNotEmpty()) return true
        val from = l.who.filter { it.dir == Dir.From }.map { it.name }
        return from.any { n -> Regex("(?<!\\p{L})" + Regex.escape(norm(n).take(4))).containsMatchIn(norm(words)) } && gave.containsMatchIn(words)
    }

    data class Answer(val say: String, val ids: List<String>)

    fun parseAsk(reply: String, book: Book): Answer? {
        val o = body(reply) ?: return reply.trim().takeIf { it.isNotEmpty() && !it.startsWith("{") }?.let { Answer(it, emptyList()) }
        val say = o.str("say") ?: return null
        val ids = runCatching { o["ids"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } }.getOrNull()?.filter { book.line(it) != null }?.take(6) ?: emptyList()
        return Answer(say, ids)
    }

    /**
     * The AI's reading laid over what the phone wrote ([phone], as it was written). Line for line when the counts agree —
     * each difference an [Edit] by «margin»; otherwise the phone's lines give way to the AI's. Lines the person changed
     * since are left alone. Returns the book unchanged when there is nothing to correct.
     */
    fun merge(book: Book, phone: List<Line>, r: Reading, today: LocalDate): Book {
        val untouched = phone.filter { p -> book.line(p.id) == p }
        if (untouched.size != phone.size) return book // the person is already at it — their hand wins
        var b = book
        if (r.lines.size == phone.size && r.lines.isNotEmpty()) {
            phone.zip(r.lines).forEach { (p, a) ->
                b = b.correct(p, a, today)
            }
        } else if (r.lines.isNotEmpty()) {
            val ids = phone.map { it.id }.toSet()
            b = b.copy(lines = b.lines.filterNot { it.id in ids } + r.lines.map { it.copy(edits = listOf(Edit("read", null, null, "margin"))) })
            b = r.lines.fold(b) { x, l -> x.setPeople(l) }
        }
        // Lines the words seem to close are not closed here: the person is asked (see [proposals]).
        val closing = proposals(b, phone, r)
        // Only a report («Олег прислал фидбек»): the words stay on the page as a record of what happened, not as a to-do.
        if (r.lines.isEmpty() && closing.isNotEmpty()) phone.forEach { p ->
            b = b.copy(lines = b.lines.map { if (it.id == p.id && it.kind == Kind.Todo) it.copy(kind = Kind.Note, edits = it.edits + Edit("kind", "Todo", "Note", "margin")) else it })
        }
        return b
    }

    /** What the words seem to close among the lines already written (done, or not needed): still open, not the phone's own. */
    fun proposals(book: Book, phone: List<Line>, r: Reading): List<Pair<String, State>> =
        (r.done.map { it to State.Done } + r.dropped.map { it to State.Dropped })
            .filter { (id, _) -> phone.none { it.id == id } && book.line(id)?.open == true }
            .distinctBy { it.first }

    private fun Book.setPeople(l: Line): Book {
        val names = l.who.map { it.name }.filter { n -> persons.none { it.matches(n) } }
        return if (names.isEmpty()) this else copy(persons = persons + names.distinct().map { Person(name = it) })
    }

    /** One line corrected in place: only what differs, each change kept as Margin's edit. */
    private fun Book.correct(p: Line, a: Line, today: LocalDate): Book {
        var x = this
        val id = p.id
        fun edit(f: (Line) -> Line) { x = x.copy(lines = x.lines.map { if (it.id == id) f(it) else it }) }
        if (a.text != p.text && a.text.isNotBlank()) edit { it.copy(text = a.text, edits = it.edits + Edit("text", p.text, a.text, "margin")) }
        if (a.kind != p.kind) edit { it.copy(kind = a.kind, edits = it.edits + Edit("kind", p.kind.name, a.kind.name, "margin")) }
        val aDay = a.date
        if ((aDay != null && a.day != p.day) || a.time != p.time) {
            x = x.setWhen(id, aDay ?: p.date, a.time, by = "margin", today = today)
            // A correction is not a put-off: no trace on the page the phone chose a moment ago.
            edit { it.copy(trail = it.trail.filterNot { m -> m.from == p.day && m.to == a.day && p.trail.none { o -> o == m } }) }
        }
        if (a.part != p.part && a.time == null) edit { it.copy(part = a.part) }
        if (a.until != p.until) edit { it.copy(until = a.until, edits = it.edits + Edit("until", p.until, a.until, "margin")) }
        if (a.repeat != p.repeat) edit { it.copy(repeat = a.repeat, edits = it.edits + Edit("repeat", p.repeat?.freq, a.repeat?.freq, "margin")) }
        if (a.who.toSet() != p.who.toSet()) { edit { it.copy(who = a.who, edits = it.edits + Edit("who", p.who.joinToString { w -> w.name }, a.who.joinToString { w -> w.name }, "margin")) }; x = x.setPeople(a) }
        if (a.place != p.place && a.place != null) edit { it.copy(place = a.place, edits = it.edits + Edit("place", p.place, a.place, "margin")) }
        if (a.threadId != p.threadId && a.threadId != null) edit { it.copy(threadId = a.threadId) }
        return x
    }
}
