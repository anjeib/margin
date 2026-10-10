package ru.anjeib.margin.book

import java.time.LocalDate

/*
 * Words that change a line already written, read on the phone: «перенеси стоматолога на пятницу в 15», «отложи лампу»,
 * «перенеси всё на завтра», «отмени Figma», «готово: лампа». The line is found by its words and the people it names;
 * nothing found — the words are written as a new line, as before. Every change comes with «вернуть».
 */
object Change {
    enum class What { Move, MoveAll, Drop, Done }

    data class Plan(val what: What, val id: String?, val day: LocalDate? = null, val time: String? = null, val later: Boolean = false)

    private val i = setOf(RegexOption.IGNORE_CASE)
    private val moveCue = Regex("^(перенеси(?:те)?|перенести|переносим|сдвинь(?:те)?|сдвинуть|передвинь(?:те)?|отложи(?:те)?|отложить|move|reschedule|postpone|push)\\s+(.+)$", i)
    private val dropCue = Regex("^(?:отмени(?:те)?|отменить|вычеркни(?:те)?|вычеркнуть|cancel|scrap)\\s+(.+)$", i)
    private val dropTail = Regex("^(.+?)\\s*[—–-]?\\s*(?:отменяется|отменили|отменён|отменена|не делаем|cancelled|canceled)$", i)
    private val doneCue = Regex("^(?:готово|сделано|done)\\s*[:—–-]?\\s*(.+)$", i)
    private val doneTail = Regex("^(.+?)\\s*[—–-]\\s*(?:готово|сделано|done)$", i)
    private val laterWord = Regex("(?:(?<!\\p{L})на\\s+)?(?<!\\p{L})(?:потом|later)(?!\\p{L})", i)
    private val everything = Regex("^(?:всё|все|все дела|всё остальное|everything|all)$", i)

    fun read(text: String, book: Book, today: LocalDate): Plan? {
        val t = text.trim().trimEnd('.', '!')
        if (t.isEmpty() || t.contains('\n') || t.endsWith('?')) return null
        moveCue.find(t)?.let { m ->
            val rest = m.groupValues[2]
            var later = laterWord.containsMatchIn(rest)
            val bare = rest.replace(laterWord, " ").replace(Regex("\\s+"), " ").trim()
            // A day or an hour was said when reading took words away («лампу на пятницу» → «Лампу»): the page's own day
            // is not a day said.
            val a = Read.draft(bare, book, today, today, now = null).lines.firstOrNull()
            val said = a != null && words(a.text) < words(bare)
            val day = a?.day?.takeIf { said }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            val time = a?.time
            // «отложи лампу» — put off, with no day: to «Потом».
            if (day == null && time == null && m.groupValues[1].lowercase().let { it.startsWith("отлож") || it == "postpone" }) later = true
            if (!later && day == null && time == null) return null
            // What is left is what to move; a preposition left behind by the date goes too («всё на»).
            val what = (a?.text?.takeIf { it.isNotBlank() } ?: bare).replace(Regex("\\s+(?:на|в|во|к|до|с|on|to|at|by|for)$", i), "").trim()
            if (everything.matches(what)) return Plan(What.MoveAll, null, day, time, later)
            return find(what, book, today)?.let { Plan(What.Move, it, day, time, later) }
        }
        (dropCue.find(t)?.groupValues?.get(1) ?: dropTail.find(t)?.groupValues?.get(1))?.let { w ->
            return find(w, book, today)?.let { Plan(What.Drop, it) }
        }
        (doneCue.find(t)?.groupValues?.get(1) ?: doneTail.find(t)?.groupValues?.get(1))?.let { w ->
            return find(w, book, today)?.let { Plan(What.Done, it) }
        }
        return null
    }

    private fun words(t: String) = t.split(Regex("\\s+")).count { it.isNotBlank() }

    // Roots short enough for Russian endings: «йогу» and «йога» → «йог», «лампу» and «лампа» → «лам», «стоматолога» → «стома».
    private val small = setOf("для", "что", "это", "как", "или", "все", "всё", "надо", "нужно", "with", "that", "this", "from")
    private fun roots(t: String): List<String> = Regex("\\p{L}{4,}").findAll(t.lowercase().replace('ё', 'е')).map { it.value }
        .filter { it !in small }.map { w -> w.take(if (w.length <= 5) 3 else minOf(5, w.length - 2)) }.toList()
    private fun same(a: String, b: String) = a.length >= 3 && b.length >= 3 && (a.startsWith(b) || b.startsWith(a))

    /**
     * The open line the words are about: the most words in common (people counted too); a tie goes to a to-do,
     * then to the one nearest today. Null when nothing shares a word.
     */
    fun find(words: String, book: Book, today: LocalDate): String? {
        val names = book.persons.flatMap { listOf(it.name) + it.aliases }
        val named = book.persons.filter { p -> (listOf(p.name) + p.aliases).any { AiRead.mentions(words, it) } }.map { it.name }
        val want = roots(words).filterNot { r -> named.any { AiRead.mentions(r, it) } }
        if (want.isEmpty() && named.isEmpty()) return null
        fun score(l: Line): Int = roots(l.text).count { r -> want.any { w -> same(r, w) } } * 2 +
            named.count { n -> l.who.any { it.name.equals(n, true) } || AiRead.mentions(l.text, n) }
        return book.lines.asSequence().filter { it.state == State.Open }
            .map { it to score(it) }.filter { it.second > 0 }
            // A tie goes to what stands on today's page (today's and the tails) — what one is looking at.
            .sortedWith(compareByDescending<Pair<Line, Int>> { it.second }.thenBy { if (it.first.lastDay()?.isAfter(today) == false) 0 else 1 }
                .thenBy { if (it.first.kind == Kind.Todo) 0 else 1 }
                .thenBy { p -> p.first.lastDay()?.let { kotlin.math.abs(java.time.temporal.ChronoUnit.DAYS.between(today, it)) } ?: Long.MAX_VALUE })
            .firstOrNull()?.first?.id
    }
}
