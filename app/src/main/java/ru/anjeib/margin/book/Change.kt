package ru.anjeib.margin.book

import java.time.LocalDate

/*
 * Words that change a line already written, read on the phone: «перенеси стоматолога на пятницу в 15», «отложи лампу»,
 * «перенеси всё на завтра», «отмени Figma», «готово: лампа». The line is found by its words and the people it names;
 * nothing found — the words are written as a new line, as before. Every change comes with «вернуть».
 */
object Change {
    enum class What { Move, MoveAll, Drop, Done }

    data class Plan(val what: What, val id: String?, val day: LocalDate? = null, val time: String? = null, val later: Boolean = false,
                    /** False when another line fits the words just as well: Margin asks before changing. */
                    val sure: Boolean = true,
                    /** «готово: кабель» on «Купить кабель usb-c и коврик»: the part done — the line becomes its items, this one ticked. */
                    val part: String? = null)

    private val i = setOf(RegexOption.IGNORE_CASE)
    private val moveCue = Regex("^(перенеси(?:те)?|перенести|переносим|сдвинь(?:те)?|сдвинуть|передвинь(?:те)?|отложи(?:те)?|отложить|move|reschedule|postpone|push)\\s+(.+)$", i)
    // «стоматолог переносится на пятницу в 16», «собрание перенесли на четверг»: the line first, then the move.
    private val moveTail = Regex("^(.+?)\\s+(переносится|перенесли|переносим|перенесён|перенесена|перенесено|переезжает|сдвигается|сдвинули|откладывается|отложили|is moved|moved)\\s+(.+)$", i)
    private val dropCue = Regex("^(?:отмени(?:те)?|отменить|вычеркни(?:те)?|вычеркнуть|cancel|scrap)\\s+(.+)$", i)
    private val dropTail = Regex("^(.+?)\\s*[—–-]?\\s*(?:отменяется|отменили|отменён|отменена|не делаем|cancelled|canceled)$", i)
    private val doneCue = Regex("^(?:готово|сделано|done)\\s*[:—–-]?\\s*(.+)$", i)
    private val doneTail = Regex("^(.+?)\\s*[—–-]\\s*(?:готово|сделано|done)$", i)
    private val laterWord = Regex("(?:(?<!\\p{L})на\\s+)?(?<!\\p{L})(?:потом|later)(?!\\p{L})", i)
    private val everything = Regex("^(?:всё|все|все дела|всё остальное|everything|all)$", i)

    fun read(text: String, book: Book, today: LocalDate): Plan? {
        val t = text.trim().trimEnd('.', '!')
        if (t.isEmpty() || t.contains('\n') || t.endsWith('?')) return null
        (moveCue.find(t)?.let { it.groupValues[1] to it.groupValues[2] }
            ?: moveTail.find(t)?.let { it.groupValues[2] to (it.groupValues[1] + " " + it.groupValues[3]) })?.let { (cue, rest) ->
            var later = laterWord.containsMatchIn(rest)
            val bare = rest.replace(laterWord, " ").replace(Regex("\\s+"), " ").trim()
            // A day or an hour was said when reading took words away («лампу на пятницу» → «Лампу»): the page's own day
            // is not a day said.
            val a = Read.draft(bare, book, today, today, now = null).lines.firstOrNull()
            val said = a != null && words(a.text) < words(bare)
            val day = a?.day?.takeIf { said }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            val time = a?.time
            // «отложи лампу» — put off, with no day: to «Потом».
            if (day == null && time == null && cue.lowercase().let { it.startsWith("отлож") || it == "postpone" }) later = true
            if (!later && day == null && time == null) return null
            // What is left is what to move; a preposition left behind by the date goes too («всё на»).
            val what = (a?.text?.takeIf { it.isNotBlank() } ?: bare).replace(Regex("\\s+(?:на|в|во|к|до|с|on|to|at|by|for)$", i), "").trim()
            if (everything.matches(what)) return Plan(What.MoveAll, null, day, time, later)
            return find(what, book, today)?.let { Plan(What.Move, it.first, day, time, later, sure = it.second) }
        }
        (dropCue.find(t)?.groupValues?.get(1) ?: dropTail.find(t)?.groupValues?.get(1))?.let { w ->
            // «отмени падел в чт»: a day said — only that day of a repeat is let go.
            val (what, day) = said(w, book, today)
            return find(what, book, today)?.let { Plan(What.Drop, it.first, day, sure = it.second) }
        }
        (doneCue.find(t)?.groupValues?.get(1) ?: doneTail.find(t)?.groupValues?.get(1))?.let { w ->
            // «готово: кабель» on «Купить кабель usb-c и коврик»: the words name only part of what the line holds — asked.
            return find(w, book, today)?.let { (id, sure) ->
                val l = book.line(id)
                val part = l?.takeIf { book.children(id).isEmpty() }?.let { partNamed(w, it.text) }
                Plan(What.Done, id, sure = sure && part == null && !partOf(w, l?.text.orEmpty()), part = part)
            }
        }
        return null
    }

    private fun words(t: String) = t.split(Regex("\\s+")).count { it.isNotBlank() }

    /** The words without the day they name, and that day if one was named. */
    private fun said(w: String, book: Book, today: LocalDate): Pair<String, LocalDate?> {
        val a = Read.draft(w, book, today, today, now = null).lines.firstOrNull() ?: return w to null
        if (words(a.text) >= words(w)) return w to null
        val what = a.text.replace(Regex("\\s+(?:на|в|во|к|до|с|on|to|at|by|for)$", i), "").trim()
        return what to a.day?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    }

    // Roots short enough for Russian endings: «йогу» and «йога» → «йог», «лампу» and «лампа» → «лам», «стоматолога» → «стома».
    private val small = setOf("для", "что", "это", "как", "или", "все", "всё", "надо", "нужно", "with", "that", "this", "from")
    private fun roots(t: String): List<String> = Regex("\\p{L}{4,}").findAll(t.lowercase().replace('ё', 'е')).map { it.value }
        .filter { it !in small }.map { w -> w.take(if (w.length <= 5) 3 else minOf(5, w.length - 2)) }.toList()
    /** The line holds several things («…, хлеб, молоко и корм», «кабель и коврик») and the words name only some of them. */
    private fun partOf(words: String, line: String): Boolean {
        val parts = line.split(Regex(",\\s*|\\s+и\\s+")).map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.size < 2) return false
        val want = roots(words)
        return parts.any { p -> roots(p).none { r -> want.any { w -> same(r, w) } } }
    }

    /** A line's things as items: «Купить кабель usb-c и коврик» → «Кабель usb-c», «Коврик» (the verb stays with the line). */
    fun itemsOf(line: String): List<String> {
        val parts = line.split(Regex(",\\s*|\\s+и\\s+")).map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
        if (parts.size < 2) return emptyList()
        if (Read.isStep(parts[0])) parts[0] = parts[0].split(Regex("\\s+"), 2).getOrNull(1).orEmpty()
        return parts.filter { it.isNotBlank() }.map { it.replaceFirstChar { c -> c.uppercaseChar() } }.takeIf { it.size >= 2 }.orEmpty()
    }

    /** The one item of a line of several things the words name, if exactly one. */
    private fun partNamed(words: String, line: String): String? {
        val want = roots(words)
        return itemsOf(line).filter { p -> roots(p).any { r -> want.any { w -> same(r, w) } } }.singleOrNull()
    }

    /** Two lines say the same thing: the same words, an ending aside («напомнить классным руководителям…» twice). */
    fun alike(a: String, b: String): Boolean {
        if (a.trim().equals(b.trim(), true)) return true
        val x = roots(a).toSet(); val y = roots(b).toSet()
        if (x.size < 2 || y.size < 2) return false
        val common = x.count { r -> y.any { same(r, it) } }
        return common >= 0.8 * maxOf(x.size, y.size)
    }

    private fun same(a: String, b: String) = a.length >= 3 && b.length >= 3 && (a.startsWith(b) || b.startsWith(a))

    /**
     * The open line the words are about, and whether it is the only one: the most words in common (people counted too);
     * then the line the words cover best («собрание» is all of «Родительское собрание 9Б», little of «Напомнить классным
     * руководителям про родительские собрания»); then what stands on today's page, a to-do, the nearest. When the
     * second fits as well as the first, it is not sure — Margin asks.
     */
    fun find(words: String, book: Book, today: LocalDate): Pair<String, Boolean>? {
        val named = book.persons.filter { p -> (listOf(p.name) + p.aliases).any { AiRead.mentions(words, it) } }.map { it.name }
        val want = roots(words).filterNot { r -> named.any { AiRead.mentions(r, it) } }
        if (want.isEmpty() && named.isEmpty()) return null
        data class Fit(val l: Line, val score: Int, val cover: Double)
        fun fit(l: Line): Fit {
            val own = roots(l.text)
            val hit = own.count { r -> want.any { w -> same(r, w) } }
            val people = named.count { n -> l.who.any { it.name.equals(n, true) } || AiRead.mentions(l.text, n) }
            return Fit(l, hit * 2 + people, if (own.isEmpty()) 0.0 else hit.toDouble() / own.size)
        }
        val fits = book.lines.asSequence().filter { it.state == State.Open }.map(::fit).filter { it.score > 0 }
            .sortedWith(compareByDescending<Fit> { it.score }.thenByDescending { it.cover }
                .thenBy { if (it.l.lastDay()?.isAfter(today) == false) 0 else 1 }
                .thenBy { if (it.l.kind == Kind.Todo) 0 else 1 }
                .thenBy { f -> f.l.lastDay()?.let { kotlin.math.abs(java.time.temporal.ChronoUnit.DAYS.between(today, it)) } ?: Long.MAX_VALUE }
                // Two lists with «молоко»: the one written last is the one in hand.
                .thenByDescending { f -> f.l.parentId?.let { book.line(it)?.writtenAt } ?: f.l.writtenAt }
                .thenByDescending { f -> (f.l.parentId ?: f.l.id).let { id -> book.lines.indexOfFirst { it.id == id } } })
            .toList()
        val first = fits.firstOrNull() ?: return null
        val second = fits.getOrNull(1)
        val sure = second == null || second.score < first.score || first.cover - second.cover >= 0.15
        return first.l.id to sure
    }
}
