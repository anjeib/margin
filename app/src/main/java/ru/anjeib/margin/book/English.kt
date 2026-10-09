package ru.anjeib.margin.book

import ru.anjeib.margin.ai.LocalParser
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters

/*
 * English phrases into lines on the phone, the same way LocalParser reads Russian ones: the words of time leave the text
 * («tomorrow at 9», «every Monday», «by Friday») and become margin notes; people found by name; «promised Alex» → →,
 * «Dima will send» → ←. The result has LocalParser's shape, so Read.draft makes lines of it the same way.
 */
object English {
    private const val W = "(?<![\\p{L}\\d])"
    private const val E = "(?![\\p{L}\\d])"
    private val days = listOf("mon(?:day)?", "tue(?:s|sday)?", "wed(?:nesday)?", "thu(?:r|rs|rsday)?", "fri(?:day)?", "sat(?:urday)?", "sun(?:day)?")
    private val dayGroup = "(" + days.joinToString("|") + ")"
    private val months = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")
    private val monGroup = "(jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|june?|july?|aug(?:ust)?|sep(?:t|tember)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?)"

    private fun dow(w: String): Int = days.indexOfFirst { Regex("^$it$", RegexOption.IGNORE_CASE).matches(w) } + 1
    private fun mon(w: String): Int = months.indexOfFirst { w.lowercase().startsWith(it) } + 1

    /** Mostly Latin letters: an English phrase. */
    fun isEnglish(text: String): Boolean {
        val letters = text.filter { it.isLetter() }
        if (letters.isEmpty()) return false
        return letters.count { it in 'a'..'z' || it in 'A'..'Z' } * 10 >= letters.length * 7
    }

    private val asked = Regex("\\?\\s*$|^what'?s burning", RegexOption.IGNORE_CASE)
    private val filler = Regex("^(?:(?:i\\s+)?(?:need|have|got|want)\\s+to|don'?t forget to|remember to|must|gotta|please|todo:?)\\s+", RegexOption.IGNORE_CASE)
    private val giving = Regex("$W(?:send|give|return|bring|show|pay|hand|pass|deliver)(?:s|ing)?$E", RegexOption.IGNORE_CASE)
    private val theyWill = Regex("^([A-Z][\\p{L}'-]+)\\s+(?:will|is going to|should|promised to)\\s+(send|bring|give|return|pay|do|make|prepare|call|share)\\b(.*)$")

    fun parse(text0: String, known: List<Pair<String, List<String>>>, today: LocalDate, now: LocalTime? = null): LocalParser.Result {
        val text = text0.trim()
        if (text.isEmpty()) return LocalParser.Result(emptyList(), true)
        if (asked.containsMatchIn(text)) return LocalParser.Result(emptyList(), false, "question")
        val chunks = text.split(Regex("\\n+|;\\s*|(?<=[.!])\\s+(?=[A-Z])")).map { it.trim() }.filter { it.isNotEmpty() }
        val items = chunks.mapNotNull { one(it, known, today, now) }
        return if (items.isEmpty()) LocalParser.Result(emptyList(), false, "empty") else LocalParser.Result(items, true)
    }

    private fun one(chunk: String, known: List<Pair<String, List<String>>>, today: LocalDate, now: LocalTime?): LocalParser.Item? {
        var s = chunk
        fun cut(r: Regex, f: (MatchResult) -> Unit): Boolean {
            val m = r.find(s) ?: return false
            f(m); s = s.removeRange(m.range).replace(Regex("\\s{2,}"), " ").trim()
            return true
        }
        var due: LocalDate? = null
        var start: LocalDate? = null
        var time: String? = null
        var part: String? = null
        var repeat: Triple<String, Int, List<Int>>? = null
        var deadline = false

        // Repeats first: «every Monday and Thursday», «every day», «weekdays», «monthly».
        cut(Regex("${W}every\\s+$dayGroup(?:\\s*(?:,|and|&)\\s*$dayGroup)*$E", RegexOption.IGNORE_CASE)) { m ->
            val ds = Regex(dayGroup, RegexOption.IGNORE_CASE).findAll(m.value).map { dow(it.value) }.filter { it > 0 }.distinct().toList()
            repeat = Triple("weekly", 1, ds)
        }
        if (repeat == null) cut(Regex("${W}(every\\s+day|daily|every\\s+week|weekly|every\\s+month|monthly|every\\s+year|yearly|on\\s+weekdays|weekdays)$E", RegexOption.IGNORE_CASE)) { m ->
            val v = m.value.lowercase()
            repeat = when {
                "weekday" in v -> Triple("weekly", 1, listOf(1, 2, 3, 4, 5))
                "day" in v || "daily" in v -> Triple("daily", 1, emptyList())
                "week" in v -> Triple("weekly", 1, emptyList())
                "month" in v -> Triple("monthly", 1, emptyList())
                else -> Triple("yearly", 1, emptyList())
            }
        }
        // Deadlines: «by Friday», «until the 12th», «before Monday».
        cut(Regex("${W}(?:by|until|till|before)\\s+(?:next\\s+)?$dayGroup$E", RegexOption.IGNORE_CASE)) { m ->
            val d = dow(m.groupValues[1]); if (d > 0) { due = today.with(TemporalAdjusters.next(DayOfWeek.of(d))); deadline = true }
        }
        if (due == null) cut(Regex("${W}(?:by|until|till|before)\\s+(?:the\\s+)?(?:end of (?:the )?week|weekend)$E", RegexOption.IGNORE_CASE)) {
            due = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)); deadline = true
        }
        if (due == null) cut(Regex("${W}(?:by|until|till|before)\\s+tomorrow$E", RegexOption.IGNORE_CASE)) { due = today.plusDays(1); deadline = true }
        // Days.
        if (due == null) cut(Regex("${W}(?:the\\s+)?day after tomorrow$E", RegexOption.IGNORE_CASE)) { due = today.plusDays(2) }
        if (due == null) cut(Regex("${W}tomorrow$E", RegexOption.IGNORE_CASE)) { due = today.plusDays(1) }
        if (due == null) cut(Regex("${W}tonight$E", RegexOption.IGNORE_CASE)) { due = today; part = "evening" }
        if (due == null) cut(Regex("${W}today$E", RegexOption.IGNORE_CASE)) { due = today }
        if (due == null) cut(Regex("${W}in\\s+(\\d{1,2}|a|one|two|three)\\s+(day|week|month)s?$E", RegexOption.IGNORE_CASE)) { m ->
            val n = when (m.groupValues[1].lowercase()) { "a", "one" -> 1L; "two" -> 2L; "three" -> 3L; else -> m.groupValues[1].toLong() }
            due = when (m.groupValues[2].lowercase()) { "day" -> today.plusDays(n); "week" -> today.plusWeeks(n); else -> today.plusMonths(n) }
        }
        if (due == null) cut(Regex("${W}(?:on\\s+)?(next\\s+)?$dayGroup$E", RegexOption.IGNORE_CASE)) { m ->
            val d = dow(m.groupValues[2])
            if (d > 0) due = if (m.groupValues[1].isNotBlank()) today.with(TemporalAdjusters.next(DayOfWeek.MONDAY)).with(TemporalAdjusters.nextOrSame(DayOfWeek.of(d)))
                else today.with(TemporalAdjusters.next(DayOfWeek.of(d)))
        }
        if (due == null) cut(Regex("${W}(?:on\\s+)?$monGroup\\.?\\s+(\\d{1,2})(?:st|nd|rd|th)?$E", RegexOption.IGNORE_CASE)) { m ->
            due = date(today, mon(m.groupValues[1]), m.groupValues[2].toInt())
        }
        if (due == null) cut(Regex("${W}(?:on\\s+)?(?:the\\s+)?(\\d{1,2})(?:st|nd|rd|th)?\\s+(?:of\\s+)?$monGroup$E", RegexOption.IGNORE_CASE)) { m ->
            due = date(today, mon(m.groupValues[2]), m.groupValues[1].toInt())
        }
        // Times: «at 9», «at 9:30», «9am», «at 3 pm», «15:00», «noon».
        cut(Regex("${W}(?:at\\s+)?(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm|a\\.m\\.|p\\.m\\.)$E", RegexOption.IGNORE_CASE)) { m ->
            var h = m.groupValues[1].toInt(); val mm = m.groupValues[2].ifBlank { "0" }.toInt()
            val pm = m.groupValues[3].lowercase().startsWith("p")
            if (pm && h < 12) h += 12; if (!pm && h == 12) h = 0
            if (h in 0..23 && mm in 0..59) time = "%02d:%02d".format(h, mm)
        }
        if (time == null) cut(Regex("${W}(?:at\\s+)?([01]?\\d|2[0-3]):([0-5]\\d)$E")) { m -> time = "%02d:%s".format(m.groupValues[1].toInt(), m.groupValues[2]) }
        if (time == null) cut(Regex("${W}at\\s+(\\d{1,2})$E", RegexOption.IGNORE_CASE)) { m ->
            var h = m.groupValues[1].toInt()
            // «at 3» means the afternoon, «at 9» the morning — as people say it.
            if (h in 1..7) h += 12
            if (h in 0..23) time = "%02d:00".format(h)
        }
        if (time == null) cut(Regex("${W}(?:at\\s+)?noon$E", RegexOption.IGNORE_CASE)) { time = "12:00" }
        if (time == null && part == null) cut(Regex("${W}(?:in the\\s+|this\\s+)?(morning|afternoon|evening)$E", RegexOption.IGNORE_CASE)) { m -> part = m.groupValues[1].lowercase() }
        // An hour already gone today, with no day said: the next time it comes round.
        if (due == null && time != null && now != null && repeat == null && LocalTime.parse(time).isBefore(now)) due = today.plusDays(1)

        // People: the person's own, then names after to / with / from / for.
        val people = ArrayList<String>()
        var promisedTo: String? = null
        var waitingFor: String? = null
        theyWill.find(s)?.let { m -> waitingFor = m.groupValues[1]; s = (m.groupValues[2] + m.groupValues[3]).trim() }
        Regex("${W}promised(?:\\s+to)?\\s+([A-Z][\\p{L}'-]+)").find(s)?.let { m -> promisedTo = m.groupValues[1]; s = s.removeRange(m.range).trim() }
        known.forEach { (n, aliases) ->
            (listOf(n) + aliases).firstOrNull { a -> Regex("$W${Regex.escape(a)}(?:'s)?$E", RegexOption.IGNORE_CASE).containsMatchIn(s) }?.let { if (n != promisedTo && n != waitingFor) people += n }
        }
        Regex("${W}(?:to|with|for|from)\\s+([A-Z][\\p{L}'-]+)").findAll(s).forEach { m ->
            val n = m.groupValues[1]
            if (n !in people && n != promisedTo && n != waitingFor && n.lowercase() !in setOf("the", "a", "my", "monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday")) people += n
        }
        // «send the deck to Alex» — what you owe them.
        if (promisedTo == null && giving.containsMatchIn(s)) Regex("${W}to\\s+([A-Z][\\p{L}'-]+)").find(s)?.let { m -> promisedTo = m.groupValues[1]; people -= m.groupValues[1] }

        var title = s.replace(filler, "").replace(Regex("\\s{2,}"), " ").trim().trim(',', '.', ':', ';', '-', '—', ' ')
        title = title.replace(Regex("\\s+(?:on|at|by|in)$", RegexOption.IGNORE_CASE), "").trim()
        if (title.isBlank()) return null
        title = title.replaceFirstChar { it.uppercase() }
        return LocalParser.Item(
            title = title,
            due = if (deadline) due else due,
            start = if (deadline) today else null,
            time = time, part = part, repeat = repeat, people = people.distinct(),
            waitingFor = waitingFor, promisedTo = promisedTo,
        )
    }

    private fun date(today: LocalDate, month: Int, day: Int): LocalDate? = runCatching {
        val d = LocalDate.of(today.year, month, day)
        if (d.isBefore(today)) d.plusYears(1) else d
    }.getOrNull()
}
