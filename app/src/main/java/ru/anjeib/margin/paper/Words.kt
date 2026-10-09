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
    val indexTitle: String
    fun indexLine(threads: Int, people: Int): String
    val threads: String
    val noThreads: String
    fun threadLine(st: ru.anjeib.margin.book.ThreadStats): String
    val people: String
    val noPeople: String
    val laterTitle: String
    val laterLine: String
    val closedThreads: String
    val search: String
    val settings: String
    val help: String
    val feedback: String
    fun since(d: LocalDate): String
    val noDay: String
    val emptyThread: String
    val youPromised: String
    val theyPromised: String
    val withThem: String
    val emptyPerson: String
    val emptyLater: String
    val ritualTitle: String
    fun ritualLine(n: Int): String
    val ritualDone: String
    val restTomorrow: String
    val pickDayShort: String
    val searchHint: String
    val nothingFound: String
    val back: String
    fun helpRows(): List<Triple<String, String, String>>
    val helpTitle: String
    val helpLine: String
    val helpWriting: String
    val helpWritingText: String
    val helpEvening: String
    val helpEveningText: String
    val settingsTitle: String
    val theme: String
    val themeLight: String
    val themeDark: String
    val themeAuto: String
    val language: String
    val eveningSetting: String
    val examples: String
    val examplesRemove: String
    val examplesBack: String
    val settingsNote: String
    fun feedbackSubject(version: String): String
    fun feedbackBody(version: String, device: String): String
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
    override val indexTitle = "Оглавление"
    override fun indexLine(threads: Int, people: Int) = "$threads " + plural(threads, "нить", "нити", "нитей") + " · $people " + plural(people, "человек", "человека", "человек")
    override val threads = "Нити"
    override val noThreads = "Нить появится, когда напишете «#Название» или сделаете нитью строку."
    override fun threadLine(st: ru.anjeib.margin.book.ThreadStats) = listOfNotNull(
        st.open.takeIf { it > 0 }?.let { "$it " + plural(it, "дело", "дела", "дел") + " открыто" },
        st.waiting.takeIf { it > 0 }?.let { "жду $it" },
        st.notes.takeIf { it > 0 }?.let { "$it " + plural(it, "запись", "записи", "записей") },
        st.files.takeIf { it > 0 }?.let { "$it " + plural(it, "файл", "файла", "файлов") },
    ).joinToString(" · ")
    override val people = "Люди"
    override val noPeople = "Люди появятся, когда вы их назовёте."
    override val laterTitle = "Потом"
    override val laterLine = "отложено без дня — вернётся, когда скажете"
    override val closedThreads = "Закрытые нити"
    override val search = "поиск"
    override val settings = "настройки"
    override val help = "как читать поля"
    override val feedback = "отзыв"
    override fun since(d: LocalDate) = "с " + dayMonth(d)
    override val noDay = "без дня"
    override val emptyThread = "Пока пусто. Напишите в поле — строка ляжет в нить."
    override val youPromised = "Вы обещали"
    override val theyPromised = "Вам обещали"
    override val withThem = "Вместе"
    override val emptyPerson = "Строк с этим человеком пока нет."
    override val emptyLater = "Ничего не отложено."
    override val ritualTitle = "Перелистнуть день"
    override fun ritualLine(n: Int) = "$n " + plural(n, "строка открыта", "строки открыты", "строк открыто") + " — решите каждую"
    override val ritualDone = "День закрыт."
    override val restTomorrow = "всё остальное — на завтра"
    override val pickDayShort = "› день…"
    override val searchHint = "Что найти?"
    override val nothingFound = "Ничего не нашлось."
    override val back = "Назад"
    override fun helpRows() = listOf(
        Triple("Купить хлеб", "Точка на линии — дело. Нажмите на неё — сделано.", "todo"),
        Triple("Сдать отчёт", "Крест — сделано. Нажмите ещё раз — вернётся.", "done"),
        Triple("Созвон со студией", "Кружок — встреча: её не делают, на неё приходят.", "event"),
        Triple("Олег просит зерно круглее", "Тире — запись: мысль, факт, заметка.", "note"),
        Triple("Показать логотип", "Слева — пометки Margin: время, повтор, люди, нить, место.", "time"),
        Triple("Спортзал", "↻ — повтор. Сделали — следующий появится на своей странице.", "repeat"),
        Triple("Продлить подписку", "Красной ручкой — хвост: не сделано вовремя. Решите, что с ним.", "tail"),
        Triple("Исходники баннеров", "→ Лёше — вы обещали. ← Дима — обещали вам.", "to"),
        Triple("Купить лампу", "«когда?» — Margin не уверен. Нажмите строку и ответьте.", "ask"),
    )
    override val helpTitle = "Как читать поля"
    override val helpLine = "справа ваши слова, на линии знак, слева пометки Margin"
    override val helpWriting = "Как писать"
    override val helpWritingText = "Пишите как думаете: «завтра в 15 позвонить Олегу», «Дима пришлёт смету до пт», «каждый вторник бассейн». Слова времени уйдут на поля, остальное останется вашим. «#Зерно» — в нить, «потом» — без дня."
    override val helpEvening = "Вечером"
    override val helpEveningText = "Перелистните день: у каждой открытой строки — решение. Завтра, другой день, потом или «не делаем». Ничего не копится."
    override val settingsTitle = "Настройки"
    override val theme = "Оформление"
    override val themeLight = "светлое"
    override val themeDark = "тёмное"
    override val themeAuto = "как в системе"
    override val language = "Язык"
    override val eveningSetting = "Перелистнуть день в"
    override val examples = "Примеры"
    override val examplesRemove = "убрать"
    override val examplesBack = "вернуть"
    override val settingsNote = "Ваши записи хранятся на телефоне и в резервной копии Google."
    override fun feedbackSubject(version: String) = "Margin «Поля» $version — отзыв"
    override fun feedbackBody(version: String, device: String) = "Что понравилось, что мешает:\n\n\n— \nMargin $version · $device"
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
    override val indexTitle = "Contents"
    override fun indexLine(threads: Int, people: Int) = "$threads thread" + (if (threads == 1) "" else "s") + " · $people " + if (people == 1) "person" else "people"
    override val threads = "Threads"
    override val noThreads = "A thread appears when you write «#Name» or turn a line into one."
    override fun threadLine(st: ru.anjeib.margin.book.ThreadStats) = listOfNotNull(
        st.open.takeIf { it > 0 }?.let { "$it open" },
        st.waiting.takeIf { it > 0 }?.let { "waiting $it" },
        st.notes.takeIf { it > 0 }?.let { "$it note" + if (it == 1) "" else "s" },
        st.files.takeIf { it > 0 }?.let { "$it file" + if (it == 1) "" else "s" },
    ).joinToString(" · ")
    override val people = "People"
    override val noPeople = "People appear when you name them."
    override val laterTitle = "Later"
    override val laterLine = "put off with no day — back when you say"
    override val closedThreads = "Closed threads"
    override val search = "search"
    override val settings = "settings"
    override val help = "how to read the margin"
    override val feedback = "feedback"
    override fun since(d: LocalDate) = "since " + dayMonth(d)
    override val noDay = "no day"
    override val emptyThread = "Nothing yet. Write in the field — the line goes into the thread."
    override val youPromised = "You promised"
    override val theyPromised = "Promised to you"
    override val withThem = "Together"
    override val emptyPerson = "No lines with them yet."
    override val emptyLater = "Nothing put off."
    override val ritualTitle = "Turn the page"
    override fun ritualLine(n: Int) = "$n line" + (if (n == 1) " is" else "s are") + " open — decide each"
    override val ritualDone = "The day is closed."
    override val restTomorrow = "all the rest — tomorrow"
    override val pickDayShort = "› a day…"
    override val searchHint = "Find…"
    override val nothingFound = "Nothing found."
    override val back = "Back"
    override fun helpRows() = listOf(
        Triple("Buy bread", "A dot on the rule is a to-do. Tap it — done.", "todo"),
        Triple("Send the report", "A cross — done. Tap again to undo.", "done"),
        Triple("Call with the studio", "A ring — an event: you attend it, you don't do it.", "event"),
        Triple("Oleg wants the bean rounder", "A dash — a note: a thought, a fact.", "note"),
        Triple("Show the logo", "On the left — Margin's notes: time, repeat, people, thread, place.", "time"),
        Triple("Gym", "↻ — a repeat. Done — the next one appears on its page.", "repeat"),
        Triple("Renew the subscription", "In red pen — left over: not done in time. Decide what to do.", "tail"),
        Triple("Banner sources", "→ Lyosha — you promised. ← Dima — promised to you.", "to"),
        Triple("Buy a lamp", "«when?» — Margin isn't sure. Tap the line and answer.", "ask"),
    )
    override val helpTitle = "How to read the margin"
    override val helpLine = "your words on the right, the mark on the rule, Margin's notes on the left"
    override val helpWriting = "How to write"
    override val helpWritingText = "Write as you think: «call Oleg tomorrow at 3», «Dima sends the estimate by Fri», «pool every Tuesday». Words of time go to the margin, the rest stays yours. «#Name» — into a thread, «later» — no day."
    override val helpEvening = "In the evening"
    override val helpEveningText = "Turn the page: every open line gets a decision — tomorrow, another day, later or not doing. Nothing piles up."
    override val settingsTitle = "Settings"
    override val theme = "Theme"
    override val themeLight = "light"
    override val themeDark = "dark"
    override val themeAuto = "as the system"
    override val language = "Language"
    override val eveningSetting = "Turn the page at"
    override val examples = "Examples"
    override val examplesRemove = "remove"
    override val examplesBack = "bring back"
    override val settingsNote = "Your notes live on the phone and in the Google backup."
    override fun feedbackSubject(version: String) = "Margin «Pages» $version — feedback"
    override fun feedbackBody(version: String, device: String) = "What works, what gets in the way:\n\n\n— \nMargin $version · $device"
}
