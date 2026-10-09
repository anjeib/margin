package ru.anjeib.margin.book

import ru.anjeib.margin.ai.LocalParser
import java.time.LocalDate
import java.time.LocalTime

/*
 * A phrase into lines, on the phone, at once. The words of time («завтра в 15», «каждый вторник») leave the text and
 * become margin notes; fillers («надо», «не забыть») become the mark •; everything else stays the person's own words.
 */
object Read {

    /** Kinship words are not names: «позвонить маме» keeps its small letter even when «Мама» is a person. */
    private val kin = setOf("мама", "маме", "маму", "мамой", "мамы", "папа", "папе", "папу", "папой", "папы", "бабушка", "бабушке", "бабушку",
        "дедушка", "дедушке", "дедушку", "сестра", "сестре", "сестру", "брат", "брату", "жена", "жене", "жену", "муж", "мужу",
        "сын", "сыну", "дочь", "дочке", "дочери", "тётя", "тёте", "дядя", "дяде", "начальник", "начальнику", "шеф", "шефу")

    private val someday = Regex("^\\s*(?:когда[- ]нибудь|потом|при случае|как[- ]нибудь)\\s*[:,—-]?\\s*", RegexOption.IGNORE_CASE)
    private val eventCue = Regex("^\\s*(?:встреча|созвон|при[её]м|собеседование|звонок с|концерт|спектакль|день рождения|свадьба|вебинар|лекция|урок|тренировка)(?!\\p{L})", RegexOption.IGNORE_CASE)
    private val noteCue = Regex("^\\s*(?:запись|заметка|мысль|идея|на заметку)\\s*[:—–-]\\s*", RegexOption.IGNORE_CASE)
    private val threadTag = Regex("(?:^|\\s)#([\\p{L}\\d][\\p{L}\\d_-]*)")

    /** What a phrase will become — for the live margin next to the field and for writing it down. */
    data class Draft(val lines: List<Line>, val sure: Boolean, val question: Boolean, val threadTitle: String?)

    /**
     * Reads [text] written on the page of [page]. Lines without a day of their own land on that page;
     * «потом …» — on «Потом»; «#Зерно» puts them in the thread.
     */
    fun draft(text: String, book: Book, page: LocalDate, today: LocalDate = LocalDate.now(), now: LocalTime? = LocalTime.now().takeIf { page == today },
              threadId: String? = null,
              /** Written on «Потом»: lines without a day of their own get none. */
              noDay: Boolean = false): Draft {
        var t = text.trim()
        if (t.isEmpty()) return Draft(emptyList(), true, false, null)
        val tag = threadTag.find(t)?.groupValues?.get(1)
        if (tag != null) t = t.replace(threadTag, " ").trim()
        val later = someday.containsMatchIn(t)
        if (later) t = t.replace(someday, "")
        val note = noteCue.containsMatchIn(t)
        if (note) t = t.replace(noteCue, "")
        val known = book.persons.map { it.name to it.aliases }
        val r = LocalParser.parse(t, known, today = page, now = now, keepWords = true)
        if (r.why == "question") return Draft(emptyList(), false, true, tag)
        val lines = r.items.flatMap { i ->
            val kind = when {
                note || i.kind == "idea" -> Kind.Note
                eventCue.containsMatchIn(i.title) && (i.time != null || i.due != null) -> Kind.Event
                else -> Kind.Todo
            }
            val who = listOfNotNull(i.promisedTo?.let { Who(it, Dir.To) }, i.waitingFor?.let { Who(it, Dir.From) }) + i.people.map { Who(it, Dir.With) }
            val repeat = i.repeat?.let { (f, n, d) -> Repeat(f, n, d) }
            val words = calm(i.title, text)
            val base = Line(text = words, kind = kind, who = who, place = i.place, repeat = repeat, part = i.part,
                time = i.time, source = text.trim(), threadId = threadId)
            when {
                // «в пн и чт» without «каждый» — one line on each of those pages.
                i.days.size >= 2 -> i.days.map { (d, h) -> base.copy(id = newId(), day = d.toString(), time = h ?: i.time) }
                (later || noDay) && i.due == null && i.start == null && i.time == null -> listOf(base.copy(day = null))
                // A thread's line written without a day stays in the thread — on its page, not on a day's.
                threadId != null && tag == null && i.due == null && i.start == null && i.time == null && repeat == null -> listOf(base.copy(day = null))
                i.start != null && i.due != null && i.start != i.due -> listOf(base.copy(day = i.start.toString(), until = i.due.toString()))
                else -> listOf(base.copy(day = (i.due ?: i.start ?: page).toString()))
            }
        }
        return Draft(lines, r.sure, false, tag)
    }

    private val stepCue = Regex("^\\s*(?:\\p{L}+(?:ть|ти|чь|ться|тись)|позвон\\p{L}*|напиш\\p{L}*|купи|сходи|сделай|отправь|забери|заплати|оплати|закажи|запишись|проверь|найди|уточни|спроси|скинь|пришли|подготовь|договорись)(?!\\p{L})",
        RegexOption.IGNORE_CASE)

    /** Words under a line: a step if they begin with what to do («позвонить», «купи»), otherwise a comment. */
    fun isStep(text: String): Boolean = stepCue.containsMatchIn(text)

    /** First letter a capital; kinship words keep the small letter they were written with. */
    private fun calm(title: String, said: String): String {
        val small = Regex("\\p{L}+").findAll(said).map { it.value }.filter { w -> w.first().isLowerCase() && w.lowercase() in kin }.map { it.lowercase() }.toSet()
        var first = true
        val out = Regex("\\p{L}+").replace(title) { m ->
            val w = m.value
            val res = if (!first && w.lowercase() in small) w.lowercase() else w
            first = false
            res
        }
        return out.replaceFirstChar { it.uppercase() }
    }
}
