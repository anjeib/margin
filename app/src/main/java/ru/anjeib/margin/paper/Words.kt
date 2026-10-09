package ru.anjeib.margin.paper

import ru.anjeib.margin.book.Repeat
import ru.anjeib.margin.book.Summary
import ru.anjeib.margin.data.dative
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/*
 * Every word the app says, in Russian or in English — never mixed (DESIGN.md §15). Margin speaks short and calm,
 * in the past tense of what it did («записано на пт»), never «я».
 */
interface Words {
    val ru: Boolean
    fun weekday(d: LocalDate): String
    fun dow(d: LocalDate): String
    fun mon(month: Int): String
    fun monthName(m: YearMonth): String
    fun dayMonth(d: LocalDate): String = "${d.dayOfMonth} ${mon(d.monthValue)}"
    /** A day as the margin says it, seen from [today]: «завтра», «пт», «12 ноя». */
    fun near(d: LocalDate, today: LocalDate): String
    fun tailSince(d: LocalDate): String
    fun until(d: LocalDate, today: LocalDate): String
    fun movedTo(d: LocalDate?, today: LocalDate): String
    fun repeat(r: Repeat): String
    fun part(p: String): String
    fun to(name: String): String
    fun from(name: String): String
    fun with(name: String): String
    fun moveNo(n: Int): String
    fun ask(what: String): String
    fun summary(s: Summary): String
    val composerHint: String
    fun writtenOn(d: LocalDate?, today: LocalDate): String
    fun writtenN(n: Int): String
    fun movedOn(d: LocalDate?, today: LocalDate): String
    fun linesN(n: Int): String
    val deleted: String
    val askLater: String
    val undo: String
    val toToday: String
    fun doneMore(n: Int): String
    val emptyToday: String
    val emptyDay: String
    val calendar: String
    val dropped: String
    val photo: String
    val file: String
    val voice: String
    val later: String
    val threadPrefix: String
    val turnDay: String
    val a11yIndex: String
    val a11ySearch: String
    val a11yAttach: String
    val a11yMic: String
    val a11ySend: String
    fun a11yMark(done: Boolean): String
    val propWhen: String
    val propRepeat: String
    val propWho: String
    val propPlace: String
    val propThread: String
    val offerTomorrow: String
    val offerLater: String
    val offerDelete: String
    fun stamp(at: java.time.LocalDateTime, today: LocalDate): String
    val writeUnder: String
    val pickDay: String
    val addTime: String
    val noTime: String
    val ok: String
    val cancel: String
    val noRepeat: String
    val whoHint: String
    val dirFrom: String
    val dirTo: String
    val dirWith: String
    val newThread: String
    val outOfThread: String
}

private fun plural(n: Int, one: String, few: String, many: String): String {
    val m10 = n % 10; val m100 = n % 100
    return when {
        m10 == 1 && m100 != 11 -> one
        m10 in 2..4 && m100 !in 12..14 -> few
        else -> many
    }
}

/** «Олег» → «Олегом», «Маша» → «Машей», «Анна» → «Анной»: «с Олегом» on the margin. */
private fun String.instrumental(): String {
    val w = trim()
    if (w.contains(' ') || w.length < 2 || !w.first().isUpperCase()) return w
    val low = w.lowercase()
    val hush = "жшчщц"
    return when {
        low.endsWith("ия") -> w.dropLast(1) + "ей"
        low.endsWith("а") && low.getOrNull(low.length - 2)?.let { it in hush } == true -> w.dropLast(1) + "ей"
        low.endsWith("а") -> w.dropLast(1) + "ой"
        low.endsWith("я") -> w.dropLast(1) + "ей"
        low.endsWith("й") || low.endsWith("ь") -> w.dropLast(1) + "ем"
        low.last() in "бвгдзклмнпрстфхжшчщц" -> w + "ом"
        else -> w
    }
}

object WordsRu : Words {
    override val ru = true
    private val days = listOf("Понедельник", "Вторник", "Среда", "Четверг", "Пятница", "Суббота", "Воскресенье")
    private val dows = listOf("пн", "вт", "ср", "чт", "пт", "сб", "вс")
    private val mons = listOf("янв", "фев", "мар", "апр", "мая", "июн", "июл", "авг", "сен", "окт", "ноя", "дек")
    private val months = listOf("Январь", "Февраль", "Март", "Апрель", "Май", "Июнь", "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь")
    override fun weekday(d: LocalDate) = days[d.dayOfWeek.value - 1]
    override fun dow(d: LocalDate) = dows[d.dayOfWeek.value - 1]
    override fun mon(month: Int) = mons[month - 1]
    override fun monthName(m: YearMonth) = months[m.monthValue - 1] + if (m.year != LocalDate.now().year) " ${m.year}" else ""
    override fun near(d: LocalDate, today: LocalDate) = when {
        d == today -> "сегодня"
        d == today.plusDays(1) -> "завтра"
        d == today.minusDays(1) -> "вчера"
        d.isAfter(today) && d.isBefore(today.plusDays(7)) -> dow(d)
        else -> dayMonth(d)
    }
    override fun tailSince(d: LocalDate) = "с " + dayMonth(d)
    override fun until(d: LocalDate, today: LocalDate) = when {
        d == today -> "до вечера"
        d.dayOfWeek == DayOfWeek.SUNDAY && d.isBefore(today.plusDays(7)) -> "на неделе"
        else -> "до " + near(d, today)
    }
    override fun movedTo(d: LocalDate?, today: LocalDate) = "→ " + (d?.let { near(it, today) } ?: "потом")
    override fun repeat(r: Repeat): String {
        val n = r.interval.coerceAtLeast(1)
        return "↻ " + when (r.freq) {
            "daily" -> if (n == 1) "каждый день" else "раз в $n дн."
            "weekly" -> when {
                r.days.sorted() == listOf(1, 2, 3, 4, 5) -> "по будням"
                r.days.sorted() == listOf(6, 7) -> "по выходным"
                r.days.isEmpty() -> if (n == 1) "раз в неделю" else "раз в $n нед."
                else -> (if (n > 1) "раз в $n нед. " else "") + r.days.sorted().joinToString(" ") { dows[(it - 1).coerceIn(0, 6)] }
            }
            "monthly" -> if (n == 1) "каждый месяц" else "раз в $n мес."
            "yearly" -> "каждый год"
            else -> ""
        }
    }
    override fun part(p: String) = mapOf("morning" to "утром", "day" to "днём", "afternoon" to "после обеда", "evening" to "вечером", "night" to "ночью")[p] ?: p
    override fun to(name: String) = "→ " + name.dative()
    override fun from(name: String) = "← $name"
    override fun with(name: String) = "с " + name.instrumental()
    override fun moveNo(n: Int) = "$n " + plural(n, "перенос", "переноса", "переносов")
    override fun ask(what: String) = when (what) { "when" -> "когда?"; "who" -> "кто это?"; "thread" -> "в нить?"; else -> "?" }
    override fun summary(s: Summary) = listOfNotNull(
        s.promises.takeIf { it > 0 }?.let { "$it " + plural(it, "обещание", "обещания", "обещаний") },
        s.questions.takeIf { it > 0 }?.let { "$it " + plural(it, "вопрос", "вопроса", "вопросов") },
        s.tails.takeIf { it > 0 }?.let { "$it " + plural(it, "хвост", "хвоста", "хвостов") },
    ).joinToString(" · ").let { if (it.isEmpty()) "" else "на полях: $it" }
    override val composerHint = "Пишите как думаете…"
    override fun writtenOn(d: LocalDate?, today: LocalDate) = "записано " + (d?.let { "на " + near(it, today) } ?: "на потом")
    override fun writtenN(n: Int) = "записано $n " + plural(n, "строка", "строки", "строк")
    override fun movedOn(d: LocalDate?, today: LocalDate) = "перенесено " + (d?.let { "на " + near(it, today) } ?: "на потом")
    override val deleted = "строка удалена"
    override fun linesN(n: Int) = "$n " + plural(n, "строка", "строки", "строк")
    override val askLater = "Вопросы Margin пока не понимает — напишите, что сделать."
    override val undo = "вернуть"
    override val toToday = "к сегодня ›"
    override fun doneMore(n: Int) = "× ещё $n " + plural(n, "сделано", "сделано", "сделано")
    override val emptyToday = "Чистая страница. Напишите, что на уме — Margin разложит."
    override val emptyDay = "Пока пусто."
    override val calendar = "календарь"
    override val dropped = "не делаем"
    override val photo = "фото"
    override val file = "файл"
    override val voice = "голос"
    override val later = "потом"
    override val threadPrefix = "≡ "
    override val turnDay = "перелистнуть день ›"
    override val a11yIndex = "Оглавление"
    override val a11ySearch = "Поиск"
    override val a11yAttach = "Прикрепить"
    override val a11yMic = "Надиктовать"
    override val a11ySend = "Записать"
    override fun a11yMark(done: Boolean) = if (done) "Сделано, вернуть" else "Отметить сделанным"
    override val propWhen = "когда"
    override val propRepeat = "повтор"
    override val propWho = "кто"
    override val propPlace = "где"
    override val propThread = "нить"
    override val offerTomorrow = "› завтра"
    override val offerLater = "› потом"
    override val offerDelete = "удалить"
    override fun stamp(at: java.time.LocalDateTime, today: LocalDate) =
        (if (at.toLocalDate() == today) "" else dayMonth(at.toLocalDate()) + " ") + at.toLocalTime().toString().take(5)
    override val writeUnder = "Дописать…"
    override val pickDay = "другой день…"
    override val addTime = "+ время"
    override val noTime = "без времени"
    override val ok = "Готово"
    override val cancel = "Отмена"
    override val noRepeat = "не повторять"
    override val whoHint = "Имя"
    override val dirFrom = "← жду от"
    override val dirTo = "→ обещал(а)"
    override val dirWith = "вместе"
    override val newThread = "Новая нить…"
    override val outOfThread = "Убрать из нити"
}

object WordsEn : Words {
    override val ru = false
    private val dows = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    private val mons = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    override fun weekday(d: LocalDate) = d.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH)
    override fun dow(d: LocalDate) = dows[d.dayOfWeek.value - 1]
    override fun mon(month: Int) = mons[month - 1]
    override fun monthName(m: YearMonth) = m.month.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH) +
        if (m.year != LocalDate.now().year) " ${m.year}" else ""
    override fun dayMonth(d: LocalDate) = "${mon(d.monthValue)} ${d.dayOfMonth}"
    override fun near(d: LocalDate, today: LocalDate) = when {
        d == today -> "today"
        d == today.plusDays(1) -> "tomorrow"
        d == today.minusDays(1) -> "yesterday"
        d.isAfter(today) && d.isBefore(today.plusDays(7)) -> dow(d)
        else -> dayMonth(d)
    }
    override fun tailSince(d: LocalDate) = "since " + dayMonth(d)
    override fun until(d: LocalDate, today: LocalDate) = when {
        d == today -> "by tonight"
        d.dayOfWeek == DayOfWeek.SUNDAY && d.isBefore(today.plusDays(7)) -> "this week"
        else -> "by " + near(d, today)
    }
    override fun movedTo(d: LocalDate?, today: LocalDate) = "→ " + (d?.let { near(it, today) } ?: "later")
    override fun repeat(r: Repeat): String {
        val n = r.interval.coerceAtLeast(1)
        return "↻ " + when (r.freq) {
            "daily" -> if (n == 1) "daily" else "every $n days"
            "weekly" -> when {
                r.days.sorted() == listOf(1, 2, 3, 4, 5) -> "weekdays"
                r.days.sorted() == listOf(6, 7) -> "weekends"
                r.days.isEmpty() -> if (n == 1) "weekly" else "every $n wks"
                else -> r.days.sorted().joinToString(" ") { dows[(it - 1).coerceIn(0, 6)] }
            }
            "monthly" -> if (n == 1) "monthly" else "every $n mo"
            "yearly" -> "yearly"
            else -> ""
        }
    }
    override fun part(p: String) = mapOf("morning" to "morning", "day" to "daytime", "afternoon" to "afternoon", "evening" to "evening", "night" to "night")[p] ?: p
    override fun to(name: String) = "→ $name"
    override fun from(name: String) = "← $name"
    override fun with(name: String) = "with $name"
    override fun moveNo(n: Int) = "moved ${n}×"
    override fun ask(what: String) = when (what) { "when" -> "when?"; "who" -> "who?"; "thread" -> "a thread?"; else -> "?" }
    override fun summary(s: Summary) = listOfNotNull(
        s.promises.takeIf { it > 0 }?.let { "$it promise" + if (it == 1) "" else "s" },
        s.questions.takeIf { it > 0 }?.let { "$it question" + if (it == 1) "" else "s" },
        s.tails.takeIf { it > 0 }?.let { "$it left over" },
    ).joinToString(" · ").let { if (it.isEmpty()) "" else "on the margin: $it" }
    override val composerHint = "Write as you think…"
    override fun writtenOn(d: LocalDate?, today: LocalDate) = "written for " + (d?.let { near(it, today) } ?: "later")
    override fun writtenN(n: Int) = "$n line" + if (n == 1) " written" else "s written"
    override fun movedOn(d: LocalDate?, today: LocalDate) = "moved to " + (d?.let { near(it, today) } ?: "later")
    override val deleted = "line deleted"
    override fun linesN(n: Int) = "$n line" + if (n == 1) "" else "s"
    override val askLater = "Margin doesn't answer questions yet — write what to do."
    override val undo = "undo"
    override val toToday = "to today ›"
    override fun doneMore(n: Int) = "× $n more done"
    override val emptyToday = "A clean page. Write what's on your mind — Margin will sort it."
    override val emptyDay = "Nothing yet."
    override val calendar = "calendar"
    override val dropped = "not doing"
    override val photo = "photo"
    override val file = "file"
    override val voice = "voice"
    override val later = "later"
    override val threadPrefix = "≡ "
    override val turnDay = "turn the page ›"
    override val a11yIndex = "Contents"
    override val a11ySearch = "Search"
    override val a11yAttach = "Attach"
    override val a11yMic = "Dictate"
    override val a11ySend = "Write"
    override fun a11yMark(done: Boolean) = if (done) "Done, undo" else "Mark done"
    override val propWhen = "when"
    override val propRepeat = "repeat"
    override val propWho = "who"
    override val propPlace = "where"
    override val propThread = "thread"
    override val offerTomorrow = "› tomorrow"
    override val offerLater = "› later"
    override val offerDelete = "delete"
    override fun stamp(at: java.time.LocalDateTime, today: LocalDate) =
        (if (at.toLocalDate() == today) "" else dayMonth(at.toLocalDate()) + " ") + at.toLocalTime().toString().take(5)
    override val writeUnder = "Write under it…"
    override val pickDay = "another day…"
    override val addTime = "+ time"
    override val noTime = "no time"
    override val ok = "Done"
    override val cancel = "Cancel"
    override val noRepeat = "doesn't repeat"
    override val whoHint = "Name"
    override val dirFrom = "← waiting for"
    override val dirTo = "→ promised"
    override val dirWith = "together"
    override val newThread = "New thread…"
    override val outOfThread = "Out of the thread"
}
