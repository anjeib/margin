package ru.anjeib.margin.book

import ru.anjeib.margin.ai.LocalParser
import java.time.LocalDate
import java.time.LocalDateTime

/*
 * Questions to the notebook (DESIGN.md §6): «что я обещал Лёше?», «что завтра?», «что горит?», «когда стрижка?» —
 * answered on the phone from what is written, at once and without a network. The answer is a sentence on the margin
 * and the lines it is about; what the phone cannot read ([About.Unknown]) goes to the AI.
 */
object Ask {
    enum class About { Promised, Waited, Person, Day, Tails, Thread, Done, Found, Unknown }

    data class Answer(
        val about: About,
        val lines: List<Line> = emptyList(),
        /** The person, the thread or the days the answer is about — the link in its sentence. */
        val name: String? = null,
        val threadId: String? = null,
        val from: LocalDate? = null,
        val to: LocalDate? = null,
        /** The AI's own sentence, when it answered. */
        val said: String? = null,
        /** Found only by shared words: the AI, when at hand, answers better. */
        val weak: Boolean = false,
    )

    private fun norm(s: String) = s.lowercase().replace('ё', 'е')

    private const val W = "(?<!\\p{L})"
    private const val E = "(?!\\p{L})"
    private val toCue = Regex("$W(?:я|мы)\\s+(?:\\p{L}+\\s+)?(?:обещал\\p{L}*|должн?\\p{L}*|задолжал\\p{L}*)$E|${W}кому\\s+(?:я\\s+)?(?:обещал|должен|должна)|${W}обещано$E|" +
        "${W}мои обещания$E|${W}i\\s+(?:promised|owe)$E|${W}did i promise$E|${W}do i owe$E|${W}my promises$E")
    private val fromCue = Regex("${W}(?:жд[еу]м?|ждать|ожида\\p{L}*)$E|$W(?:мне|нам)\\s+(?:\\p{L}+\\s+)?(?:обещал\\p{L}*|должн?\\p{L}*|пришл\\p{L}*|скин\\p{L}*|верн\\p{L}*)|" +
        "${W}обещал\\p{L}*\\s+(?:мне|нам)$E|${W}долж\\p{L}+\\s+(?:мне|нам)$E|${W}waiting\\s+(?:for|on)$E|${W}owes?\\s+me$E|${W}promised\\s+me$E|${W}send(?:ing)?\\s+me$E")
    private val promiseWord = Regex("${W}(?:обещ\\p{L}*|promis\\p{L}*)")
    private val tailsCue = Regex("${W}(?:горит|горят|гор[ия]щ\\p{L}*|просроч\\p{L}*|хвост\\p{L}*|висит|висят|не сделан\\p{L}*|забыл\\p{L}*|overdue|late|behind|on fire|burning|forgot\\p{L}*)$E")
    private val doneCue = Regex("${W}(?:сделал\\p{L}*|сделано|успел\\p{L}*|закрыл\\p{L}*|итог\\p{L}*|did i do|done|finished|accomplished|got done)$E")
    private val whenCue = Regex("^\\s*(?:когда|в какой день|во сколько|when|what time)$E")
    private val weekCue = Regex("${W}(?:на (?:этой )?неделе|за неделю|эта неделя|this week|the week)$E")
    private val nextWeekCue = Regex("${W}(?:на следующей неделе|next week)$E")
    private val monthCue = Regex("${W}(?:за месяц|в этом месяце|this month)$E")
    private val yesterdayCue = Regex("${W}(?:вчера|yesterday)$E")
    private val stop = setOf("что", "чем", "чего", "кому", "кого", "у", "меня", "мне", "мои", "мой", "моя", "я", "мы", "нас", "нам", "по", "с", "со", "на", "в", "во", "про", "о", "об",
        "открыто", "открытые", "осталось", "сейчас", "вообще", "нибудь", "такое", "там", "тут", "ними", "ним", "ней", "him", "her", "them", "now", "left",
        "ли", "есть", "там", "как", "какие", "какой", "какая", "где", "когда", "кто", "это", "был", "была", "было", "были", "сегодня", "завтра", "вчера",
        "дела", "дело", "делах", "надо", "нужно", "ещё", "еще", "всё", "все", "the", "a", "an", "what", "when", "is", "are", "do", "did", "i", "my", "me",
        "on", "in", "about", "with", "for", "have", "has", "to", "of", "at", "any", "there")

    /** The words worth looking for: «когда стрижка?» → «стриж». */
    private fun stems(q: String): List<String> = Regex("\\p{L}{3,}").findAll(norm(q)).map { it.value }.filter { it !in stop }
        .map { if (it.length > 5) it.take(it.length - 2) else it }.distinct().toList()

    private fun matches(l: Line, stems: List<String>) = stems.count { s ->
        Regex("\\p{L}+").findAll(norm(l.text + " " + (l.place ?: "") + " " + l.who.joinToString(" ") { it.name })).any { it.value.startsWith(s) }
    }

    private fun byWhen(l: Line) = (l.lastDay()?.toString() ?: "9999") + (l.time ?: "99")

    fun answer(q0: String, book: Book, today: LocalDate = LocalDate.now()): Answer {
        val q = norm(q0.trim().trimEnd('?', ' ', '.', '!'))
        val known = book.persons.map { it.name to it.aliases }
        val plain = q0.trim().trimEnd('?', ' ')
        val read = runCatching {
            if (English.isEnglish(plain)) English.parse(plain, known, today) else LocalParser.parse(plain, known, today = today, now = null, keepWords = true)
        }.getOrNull()
        val item = read?.items?.firstOrNull()
        val names = (listOfNotNull(item?.promisedTo, item?.waitingFor) + (item?.people ?: emptyList()))
            .map { n -> book.person(n)?.name ?: n }.distinct()
        val name = names.firstOrNull()
        fun mine(w: Who) = name != null && (book.person(name)?.matches(w.name) ?: w.name.equals(name, true))
        val top = book.lines.filter { it.parentId == null }
        // What the question says beyond a name, a thread and a day: «о чём договорились с Олегом по логотипу» is not «что с Олегом».
        val words = Regex("\\p{L}+").findAll(q).map { it.value }.toList()
        val thread = book.threads.filter { it.closedAt == null }.firstOrNull { t ->
            Regex("\\p{L}{4,}").findAll(norm(t.title)).any { tw -> val stem = tw.value.take(maxOf(4, tw.value.length - 2)); words.any { it.startsWith(stem) } }
        }
        val nameStems = names.flatMap { n -> (listOf(n) + (book.person(n)?.aliases ?: emptyList())).map { norm(it).take(4) } }
        val threadStems = thread?.let { t -> Regex("\\p{L}{4,}").findAll(norm(t.title)).map { it.value.take(4) }.toList() } ?: emptyList()
        val rest = stems(item?.title ?: q).filter { s -> nameStems.none { s.startsWith(it) || it.startsWith(s) } && threadStems.none { s.startsWith(it) } &&
            !promiseWord.containsMatchIn(s) && !Regex("^(?:жд|ожида|долж|wait|owe)").containsMatchIn(s) }

        // What is burning: tails, today's open lines and soft windows closing within two days.
        if (tailsCue.containsMatchIn(q)) {
            val p = book.page(today, today)
            val soon = top.filter { it.isTodo && it.open && it.until != null && it.lastDay()?.let { d -> !d.isAfter(today.plusDays(2)) && !d.isBefore(today) } == true }
            return Answer(About.Tails, (p.tails + p.open.filter { it !in p.tails } + soon).distinctBy { it.id })
        }

        // What was done: today by default, yesterday, the week, the month.
        if (doneCue.containsMatchIn(q) && !toCue.containsMatchIn(q) && !fromCue.containsMatchIn(q)) {
            val (from, to) = when {
                yesterdayCue.containsMatchIn(q) -> today.minusDays(1) to today.minusDays(1)
                weekCue.containsMatchIn(q) -> today.minusDays(6) to today
                monthCue.containsMatchIn(q) -> today.minusDays(29) to today
                else -> today to today
            }
            val done = book.lines.filter { l ->
                l.state == State.Done && l.doneAt?.let { runCatching { LocalDateTime.parse(it).toLocalDate() }.getOrNull() }?.let { !it.isBefore(from) && !it.isAfter(to) } == true
            }.sortedByDescending { it.doneAt }
            return Answer(About.Done, done, from = from, to = to)
        }

        // Promises — to someone, from someone; with a name or all of them.
        val to = toCue.containsMatchIn(q)
        val from = fromCue.containsMatchIn(q) || (!to && name != null && promiseWord.containsMatchIn(q))
        if (to || from) {
            val dir = if (to) Dir.To else Dir.From
            val lines = top.filter { l -> l.open && l.who.any { it.dir == dir && (name == null || mine(it)) } }.sortedBy(::byWhen)
            return Answer(if (to) About.Promised else About.Waited, lines, name = name)
        }

        // A person: everything open with them.
        if (name != null && book.person(name) != null && rest.isEmpty()) {
            val p = book.personLines(name)
            val with = p.other.filter { it.open }
            return Answer(About.Person, (p.promised + p.waited + with).sortedBy(::byWhen), name = book.person(name)?.name ?: name)
        }

        // A thread named by any of its words: «что по кофейне?», «что с Зерном?».
        if (thread != null && rest.isEmpty()) return Answer(About.Thread, top.filter { it.threadId == thread.id && it.open }.sortedBy(::byWhen), threadId = thread.id, name = thread.title)

        val s = stems(q)
        // «Когда стрижка?» — the line itself, its day on the margin.
        if (whenCue.containsMatchIn(q) && s.isNotEmpty()) {
            val found = top.map { it to matches(it, s) }.filter { it.second > 0 }
            val best = found.maxOfOrNull { it.second } ?: 0
            return Answer(About.Found, found.filter { it.second == best }.map { it.first }.sortedWith(compareBy({ !it.open }, ::byWhen)).take(6))
        }

        // A day or a week: «что завтра?», «что у меня в пятницу?», «что на неделе?».
        val (d0, d1) = when {
            nextWeekCue.containsMatchIn(q) -> today.with(java.time.DayOfWeek.MONDAY).plusWeeks(1).let { it to it.plusDays(6) }
            weekCue.containsMatchIn(q) -> today to today.with(java.time.DayOfWeek.SUNDAY)
            item?.start != null && item.due != null && item.start != item.due -> item.start to item.due
            item?.due != null || item?.start != null || Regex("${W}(?:сегодня|today)$E").containsMatchIn(q) -> (item?.due ?: item?.start ?: today).let { it to it }
            else -> null to null
        }
        if (d0 != null && d1 != null && rest.isEmpty()) {
            val lines = generateSequence(d0) { it.plusDays(1) }.takeWhile { !it.isAfter(d1) }.take(31).flatMap { d ->
                val p = book.page(d, today)
                (if (d == today) p.tails else emptyList()) + p.timed + p.rest
            }.filter { it.open || it.kind == Kind.Event }.distinctBy { it.id }.toList()
            return Answer(About.Day, lines, from = d0, to = d1)
        }

        // Anything else: the lines that share its words; nothing — the AI's question.
        if (s.isNotEmpty()) {
            val found = top.map { it to matches(it, s) }.filter { it.second > 0 }
            val best = found.maxOfOrNull { it.second } ?: 0
            if (best > 0 && (best >= minOf(2, s.size))) return Answer(About.Found,
                found.filter { it.second == best }.map { it.first }.sortedWith(compareBy({ !it.open }, ::byWhen)).take(6), weak = true)
        }
        return Answer(About.Unknown)
    }
}
