package ru.anjeib.margin.data

import ru.anjeib.margin.i18n.L
import ru.anjeib.margin.i18n.Lang
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

private val monthsGenRu = listOf(
    "января", "февраля", "марта", "апреля", "мая", "июня",
    "июля", "августа", "сентября", "октября", "ноября", "декабря",
)
private val monthsShortRu = listOf("янв", "фев", "мар", "апр", "мая", "июн", "июл", "авг", "сен", "окт", "ноя", "дек")
private val monthsEn = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December",
)

private val locale: Locale get() = if (L.lang == Lang.Ru) Locale.forLanguageTag("ru") else Locale.ENGLISH
private val ru get() = L.lang == Lang.Ru

fun LocalDate.weekStart(): LocalDate = with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
fun LocalDate.weekEnd(): LocalDate = with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))

fun LocalDate.dowShort(): String = dayOfWeek.getDisplayName(TextStyle.SHORT, locale).replaceFirstChar { it.uppercase() }
fun LocalDate.dowFull(): String = dayOfWeek.getDisplayName(TextStyle.FULL_STANDALONE, locale).replaceFirstChar { it.uppercase() }

/** «Понедельник, 5 октября» / «Monday, October 5» */
fun LocalDate.longTitle(): String = "${dowFull()}, ${dayMonth()}"

/** «5 октября» / «October 5» */
fun LocalDate.dayMonth(): String =
    if (ru) "$dayOfMonth ${monthsGenRu[monthValue - 1]}" else "${monthsEn[monthValue - 1]} $dayOfMonth"

fun LocalDate.dayMonthShort(): String =
    if (ru) "$dayOfMonth ${monthsShortRu[monthValue - 1]}" else "${monthsEn[monthValue - 1].take(3)} $dayOfMonth"

/** «Чт 8 окт» / «Thu Oct 8», or Today / Tomorrow / Yesterday near today. */
fun LocalDate.shortLabel(today: LocalDate = LocalDate.now()): String = when (this) {
    today -> L.s.today
    today.plusDays(1) -> L.s.tomorrow
    today.minusDays(1) -> L.s.yesterday
    else -> "${dowShort()} ${dayMonthShort()}"
}

/** «5–11 октября» / «October 5–11» for a week. */
fun rangeLabel(a: LocalDate, b: LocalDate): String = when {
    a.month != b.month -> "${a.dayMonth()} – ${b.dayMonth()}"
    ru -> "${a.dayOfMonth}–${b.dayOfMonth} ${monthsGenRu[b.monthValue - 1]}"
    else -> "${monthsEn[b.monthValue - 1]} ${a.dayOfMonth}–${b.dayOfMonth}"
}

/** Human meta line for a task: dates and time. */
fun Task.whenLabel(today: LocalDate = LocalDate.now()): String? {
    val s = startDate; val d = dueDate
    val ws = today.weekStart(); val we = today.weekEnd()
    val dates = when {
        // Soft windows read as words, not as false precision.
        s != null && d == we && !s.isAfter(today) -> L.s.thisWeekSoft
        d == we.plusWeeks(1) && s == ws.plusWeeks(1) -> L.s.nextWeekSoft
        s != null && d != null && s != d -> "${s.shortLabel(today)} — ${d.shortLabel(today)}"
        d != null -> d.shortLabel(today)
        s != null -> L.s.fromDate(s.shortLabel(today))
        else -> null
    }
    return listOfNotNull(dates, time ?: dayPart?.let { L.s.dayPart(it) }).joinToString(" · ").ifEmpty { null }
}

/** Past its day — or today, past its time (a 14:00 call at 21:00 is late too). */
fun Task.isOverdue(today: LocalDate = LocalDate.now()): Boolean = !done && (dueDate?.isBefore(today) == true ||
    (dueDate == today && today == LocalDate.now() && time?.let { runCatching { java.time.LocalTime.parse(it).isBefore(java.time.LocalTime.now()) }.getOrNull() } == true))

fun pluralRu(n: Int, one: String, few: String, many: String): String {
    val m10 = n % 10; val m100 = n % 100
    return when {
        m10 == 1 && m100 != 11 -> one
        m10 in 2..4 && m100 !in 12..14 -> few
        else -> many
    }
}
