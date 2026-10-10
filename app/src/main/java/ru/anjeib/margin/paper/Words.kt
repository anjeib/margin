package ru.anjeib.margin.paper

import ru.anjeib.margin.book.Repeat
import ru.anjeib.margin.book.Summary
import ru.anjeib.margin.book.Ask
import ru.anjeib.margin.book.Kind
import ru.anjeib.margin.book.Line
import ru.anjeib.margin.book.daysBetween
import ru.anjeib.margin.data.dative
import ru.anjeib.margin.data.genitive
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
    /** A line's words short enough for one line of Margin's answer. */
    fun short(text: String): String = if (text.length <= 32) text else text.take(30).trimEnd() + "…"
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
    /** Promises in words, not arrows: «вы обещали 2 · ждёте 1». */
    fun owes(promised: Int, waited: Int): String?
    val composerHint: String
    fun writtenOn(d: LocalDate?, today: LocalDate): String
    fun writtenN(n: Int): String
    fun movedOn(d: LocalDate?, today: LocalDate): String
    fun linesN(n: Int): String
    val deleted: String
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
    val markKind: String
    val deleteForGood: String
    val backToWork: String
    fun remindWho(name: String): String
    fun ofDone(done: Int, total: Int): String
    /** A line changed by words: «Стоматолог» → пт 15:00 / → потом; «Figma» — не делаем; «Лампа» — сделано. */
    fun changedTo(text: String, day: LocalDate?, time: String?, today: LocalDate): String
    fun changedDropped(text: String): String
    val editing: String
    val doneWord: String
    fun changedDone(text: String): String
    fun movedAll(n: Int, to: LocalDate?, today: LocalDate): String
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
    val bellLines: String
    val bellDay: String
    val today: String
    val bellDone: String
    val bellHour: String
    fun morningLine(n: Int, tails: Int): String
    /** An answer's sentence around its link (a name, a thread, a day): before, the link, after. */
    fun answer(a: ru.anjeib.margin.book.Ask.Answer, today: LocalDate): Triple<String, String?, String>
    val thinking: String
    val needNet: String
    fun more(n: Int): String
    val close: String
    val rereading: String
    val reread: String
    fun markedDone(n: Int): String
    /** Margin asks before closing lines your words seem to report: (text, not doing). */
    fun closeAsk(lines: List<Pair<String, Boolean>>): String
    fun closed(n: Int): String
    val yes: String
    val no: String
    val attachPhoto: String
    val attachCamera: String
    val attachFile: String
    val attachContact: String
    val listening: String
    val heardNothing: String
    val doneReply: String
    val transcribing: String
    val voiceOff: String
    val micDenied: String
    val stop: String
    fun personAdded(name: String): String
    val handedTitle: String
    fun handedFrom(app: String): String
    val openInMargin: String
    val propFile: String
    val share: String
    val removeFile: String
    val fileOpen: String
    val fileShare: String
    val fileRemoved: String
    fun stepsInNote(n: Int): String
    fun stepsAdded(n: Int): String
    val whole: String
    val longNote: String
    val welcomeLine: String
    /** The title page's rows: the note on the margin, the mark, the words. */
    fun welcomeRows(): List<Triple<String?, String, String>>
    val welcomeTry: String
    val welcomeTried: String
    val welcomeExamples: String
    val welcomeClean: String
    val welcomeBegin: String
    val voiceSetting: String
    val voiceNow: String
    val voiceField: String
    val offlineVoice: String
    val offlineReady: String
    val offlineRetry: String
    fun offlineGet(mb: Int): String
    val lockSetting: String
    val backupSetting: String
    val on: String
    val off: String
    val bellSetting: String
    val bellOn: String
    val bellOff: String
    val inviteSetting: String
    val inviteOwn: String
    val inviteShared: String
    val inviteNone: String
    val inviteHint: String
    fun peopleLine(people: Int, orgs: Int): String
    fun allThreads(n: Int): String
    fun allPeople(n: Int): String
    val addressBook: String
    val findPerson: String
    val filterAll: String
    val filesTitle: String
    fun filesLine(photos: Int, docs: Int): String
    val filesSub: String
    val filesEmpty: String
    val documents: String
    val filterPeople: String
    val filterOrgs: String
    val company: String
    val markOrg: String
    val markPerson: String
    val call: String
    val message: String
    val mail: String
    val weekTitle: String
    fun weekLine(open: Int, done: Int, events: Int): String
    val weekFree: String
    val weekSub: String
    val weekLink: String
    /** Under a page just closed: what the next one holds. */
    fun nextPage(lines: List<Line>): String
    val propWhoTo: String
    val propWhoFrom: String
    val offerToday: String
    val threadClosed: String
    val threadOpened: String
    val closeThread: String
    val reopenThread: String
    val remind: String
    fun remindText(name: String, what: String): String
    val calendarSetting: String
    val calendarOn: String
    val calendarOff: String
    val stampTitle: String
    fun stampLine(at: String, s: ru.anjeib.margin.book.DayStats): String
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
    override fun to(name: String) = "→\u00A0" + name.dative()
    override fun from(name: String) = "←\u00A0$name"
    override fun with(name: String) = "с\u00A0" + name.instrumental()
    override fun moveNo(n: Int) = "$n " + plural(n, "перенос", "переноса", "переносов")
    override fun ask(what: String) = when (what) { "when" -> "когда?"; "who" -> "кто это?"; "thread" -> "в нить?"; else -> "?" }
    override fun owes(promised: Int, waited: Int) = listOfNotNull(
        promised.takeIf { it > 0 }?.let { "вы обещали $it" }, waited.takeIf { it > 0 }?.let { "ждёте $it" },
    ).joinToString(" · ").ifEmpty { null }
    override fun summary(s: Summary) = listOfNotNull(
        owes(s.promised, s.waited),
        s.questions.takeIf { it > 0 }?.let { "$it " + plural(it, "вопрос", "вопроса", "вопросов") },
        s.tails.takeIf { it > 0 }?.let { "$it с прошлых дней" },
    ).joinToString(" · ").let { if (it.isEmpty()) "" else "на полях: $it" }
    override val composerHint = "Пишите как думаете…"
    override fun writtenOn(d: LocalDate?, today: LocalDate) = "записано " + (d?.let { "на " + near(it, today) } ?: "на потом")
    override fun writtenN(n: Int) = "записано $n " + plural(n, "строка", "строки", "строк")
    override fun movedOn(d: LocalDate?, today: LocalDate) = "перенесено " + (d?.let { "на " + near(it, today) } ?: "на потом")
    override val deleted = "строка удалена"
    override fun linesN(n: Int) = "$n " + plural(n, "строка", "строки", "строк")
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
    override val writeUnder = "дописать…"
    override val markKind = "Пункт или запись"
    override val deleteForGood = "удалить совсем"
    override val backToWork = "вернуть в дела"
    override fun remindWho(name: String) = "напомнить " + name.dative()
    override fun ofDone(done: Int, total: Int) = "$done из $total"
    override fun changedTo(text: String, day: LocalDate?, time: String?, today: LocalDate) =
        "«${short(text)}» → " + (listOfNotNull(day?.let { near(it, today) }, time).joinToString(" ").ifEmpty { "потом" })
    override fun changedDropped(text: String) = "«${short(text)}» — не делаем"
    override val editing = "правка"
    override val doneWord = "сделано"
    override fun changedDone(text: String) = "«${short(text)}» — сделано"
    override fun movedAll(n: Int, to: LocalDate?, today: LocalDate) = "$n " + plural(n, "строка", "строки", "строк") + " → " + (to?.let { near(it, today) } ?: "потом")
    override val pickDay = "другой день…"
    override val addTime = "+ время"
    override val noTime = "без времени"
    override val ok = "Готово"
    override val cancel = "Отмена"
    override val noRepeat = "не повторять"
    override val whoHint = "Имя"
    override val dirFrom = "← ждёте от"
    override val dirTo = "→ вы обещали"
    override val dirWith = "вместе"
    override val newThread = "Новая нить…"
    override val outOfThread = "Убрать из нити"
    override val indexTitle = "Оглавление"
    override fun indexLine(threads: Int, people: Int) = "$threads " + plural(threads, "нить", "нити", "нитей") + " · $people " + plural(people, "человек", "человека", "человек")
    override val threads = "Нити"
    override val noThreads = "Нить появится, когда напишете «#Название» или сделаете нитью строку."
    override fun threadLine(st: ru.anjeib.margin.book.ThreadStats) = listOfNotNull(
        st.open.takeIf { it > 0 }?.let { "$it " + plural(it, "дело", "дела", "дел") + " открыто" },
        st.waiting.takeIf { it > 0 }?.let { "ждёте $it" },
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
    override fun ritualLine(n: Int) = if (n == 1) "1 строка открыта — решите её" else "$n " + plural(n, "строка открыта", "строки открыты", "строк открыто") + " — решите каждую"
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
        Triple("Показать логотип", "Слева Margin пишет когда: день, час. Под словами — с кем, где, в какой нити.", "time"),
        Triple("Спортзал", "↻ — повтор. Сделали — следующий появится на своей странице.", "repeat"),
        Triple("Продлить подписку", "Красной ручкой — дело с прошлых дней: не сделано вовремя. Решите, что с ним.", "tail"),
        Triple("Исходники баннеров", "→ Лёше — вы обещали. ← Дима — обещали вам.", "to"),
    )
    override val helpTitle = "Как читать поля"
    override val helpLine = "слева — когда, на линии — знак, справа — ваши слова и пометки Margin под ними"
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
    override val bellLines = "Строки со временем"
    override val bellDay = "Утро и вечер"
    override val today = "сегодня"
    override val bellDone = "Сделано"
    override val bellHour = "Через час"
    override fun morningLine(n: Int, tails: Int) = "$n " + plural(n, "строка", "строки", "строк") + " на сегодня" +
        (if (tails > 0) ", из них $tails с прошлых дней" else "")
    override fun answer(a: Ask.Answer, today: LocalDate): Triple<String, String?, String> {
        val n = a.lines.size
        val t = things(a.lines)
        val name = a.name
        return when (a.about) {
            Ask.About.Promised -> when {
                name == null -> Triple(if (n > 0) "Обещано: $t" else "Открытых обещаний нет", null, "")
                n > 0 -> Triple("", name.dative(), " обещано: $t")
                else -> Triple("", name.dative(), " ничего не обещано")
            }
            Ask.About.Waited -> when {
                name == null -> Triple(if (n > 0) "Ждём: $t" else "Ничего не ждём", null, "")
                n > 0 -> Triple("От ", name.genitive(), " ждём: $t")
                else -> Triple("От ", name.genitive(), " ничего не ждём")
            }
            Ask.About.Person -> Triple("", name ?: "", if (n > 0) ": открыто $t" else ": открытого нет")
            Ask.About.Thread -> Triple("", name ?: "", if (n > 0) ": открыто $t" else ": всё сделано")
            Ask.About.Day -> {
                val d0 = a.from ?: today; val d1 = a.to ?: d0
                if (d0 == d1) Triple("", dayName(d0, today), if (n > 0) " — $t" else " — пусто")
                else Triple((if (!d0.isAfter(today)) "На этой неделе" else "На следующей неделе") + if (n > 0) " — $t" else " — пусто", null, "")
            }
            Ask.About.Tails -> Triple(if (n > 0) "Горит: $t" else "Ничего не горит", null, "")
            Ask.About.Done -> {
                val span = when { a.from == today && a.to == today -> "сегодня"; a.from == a.to -> "вчера"; (a.from?.let { daysBetween(it, today) } ?: 0) <= 7 -> "за неделю"; else -> "за месяц" }
                Triple(if (n > 0) "Сделано $span: $t" else span.replaceFirstChar { it.uppercase() } + " ничего не сделано", null, "")
            }
            Ask.About.Found -> Triple(if (n > 0) "Нашлось: $t" else "Не нашлось", null, "")
            Ask.About.Unknown -> Triple(a.said ?: needNet, null, "")
            Ask.About.Margin -> Triple(if (n > 0) "Обещания и дела с прошлых дней — $t" else "На полях пусто", null, "")
        }
    }
    private fun things(lines: List<Line>): String = listOfNotNull(
        lines.count { it.kind == Kind.Todo }.takeIf { it > 0 }?.let { "$it " + plural(it, "дело", "дела", "дел") },
        lines.count { it.kind == Kind.Event }.takeIf { it > 0 }?.let { "$it " + plural(it, "встреча", "встречи", "встреч") },
        lines.count { it.kind == Kind.Note }.takeIf { it > 0 }?.let { "$it " + plural(it, "запись", "записи", "записей") },
    ).joinToString(", ")
    private fun dayName(d: LocalDate, today: LocalDate) = when {
        d == today -> "Сегодня"
        d == today.plusDays(1) -> "Завтра"
        d == today.minusDays(1) -> "Вчера"
        d.isAfter(today) && d.isBefore(today.plusDays(7)) -> weekday(d)
        else -> dayMonth(d)
    }
    override val thinking = "читаю записи…"
    override val needNet = "Такой вопрос понимает только ИИ — нужна сеть."
    override fun more(n: Int) = "ещё $n ›"
    override val close = "Закрыть"
    override val rereading = "перечитываю…"
    override val reread = "перечитано"
    override fun markedDone(n: Int) = "перечитано, закрыто: $n"
    override fun closeAsk(lines: List<Pair<String, Boolean>>) = when {
        lines.size == 1 && lines[0].second -> "больше не нужно «${lines[0].first}»?"
        lines.size == 1 -> "сделано «${lines[0].first}»?"
        else -> "закрыть " + lines.joinToString(", ") { "«${it.first}»" } + "?"
    }
    override fun closed(n: Int) = "закрыто: $n"
    override val yes = "да"
    override val no = "нет"
    override val attachPhoto = "Фото"
    override val attachCamera = "Камера"
    override val attachFile = "Файл"
    override val attachContact = "Контакт"
    override val listening = "слушаю…"
    override val heardNothing = "ничего не слышно — ещё раз?"
    override val doneReply = "× сделано"
    override val transcribing = "разбираю…"
    override val voiceOff = "Голос сейчас недоступен — нужна сеть."
    override val micDenied = "Без микрофона диктовать нельзя."
    override val stop = "Остановить"
    override fun personAdded(name: String) = "$name — в людях"
    override val handedTitle = "В Margin"
    // «из Telegram», but «из «Файлы»»: a Russian name in quotes keeps «из» from bending it.
    override fun handedFrom(app: String) = if (app.any { it in 'а'..'я' || it in 'А'..'Я' }) "из «$app»" else "из $app"
    override val openInMargin = "открыть в Margin ›"
    override val propFile = "файл"
    override val share = "Поделиться"
    override val removeFile = "убрать из строки"
    override val fileOpen = "открыть"
    override val fileShare = "поделиться"
    override val fileRemoved = "файл убран из строки"
    override fun stepsInNote(n: Int) = "в записи $n " + plural(n, "дело", "дела", "дел") + " — вынести шагами?"
    override fun stepsAdded(n: Int) = "$n " + plural(n, "шаг", "шага", "шагов") + " под записью"
    override val whole = "целиком ›"
    override val longNote = "одной записью"
    override val welcomeLine = "ежедневник, который пишет на полях"
    override fun welcomeRows() = listOf(
        Triple("Margin", "none", "На полях Margin пишет когда, а под вашими словами — кто и к чему относится."),
        Triple(null, "note", "Справа — ваши слова. Пишите как думаете, в одно поле внизу."),
        Triple("завтра", "open", "Точка на линии — дело. Нажмите на неё."),
        Triple("→\u00A0Лёше", "none", "Стрелка — обещание: → вы обещали, ← обещали вам."),
        Triple("17:00", "event", "Кружок — встреча, тире — запись."),
        Triple("→ пн", "moved", "Вечером перелистните день: у каждой открытой строки — своё решение."),
        Triple("?", "none", "Спросите «что я обещал Лёше?» — ответ появится над полем."),
    )
    override val welcomeTry = "Нажмите на точку — дело сделано. Ещё раз — вернётся."
    override val welcomeTried = "Так и устроено. Остальное — так же просто."
    override val welcomeExamples = "посмотреть на примере ›"
    override val welcomeClean = "начать с чистой страницы ›"
    override val welcomeBegin = "начать ›"
    override val voiceSetting = "Голос"
    override val voiceNow = "сразу записывать"
    override val voiceField = "сначала в поле"
    override val offlineVoice = "Голос без интернета"
    override val offlineReady = "готов"
    override val offlineRetry = "ещё раз"
    override fun offlineGet(mb: Int) = "скачать, $mb МБ"
    override val lockSetting = "Вход по отпечатку"
    override val backupSetting = "Копия в Google"
    override val on = "вкл"
    override val off = "выкл"
    override val bellSetting = "Напоминания"
    override val bellOn = "разрешены"
    override val bellOff = "разрешить"
    override val inviteSetting = "Доступ к ИИ"
    override val inviteOwn = "свой код"
    override val inviteShared = "общий код"
    override val inviteNone = "нет"
    override val inviteHint = "ИИ перечитывает сложные фразы, отвечает на трудные вопросы и расшифровывает голос. Если вам дали свой код — введите его; пусто — общий."
    override fun nextPage(lines: List<Line>) = "завтра — " + things(lines).ifBlank { "пусто" } + " ›"
    override fun peopleLine(people: Int, orgs: Int) = listOfNotNull(
        "$people " + plural(people, "человек", "человека", "человек"),
        orgs.takeIf { it > 0 }?.let { "$it " + plural(it, "компания", "компании", "компаний") },
    ).joinToString(" · ")
    override fun allThreads(n: Int) = "все нити · $n ›"
    override fun allPeople(n: Int) = "все люди и компании · $n ›"
    override val addressBook = "Люди и компании"
    override val findPerson = "Имя, компания, кто это…"
    override val filterAll = "все"
    override val filesTitle = "Файлы"
    override fun filesLine(photos: Int, docs: Int) = listOfNotNull(
        photos.takeIf { it > 0 }?.let { "$it фото" }, docs.takeIf { it > 0 }?.let { "$it " + plural(it, "документ", "документа", "документов") },
    ).joinToString(" · ").ifEmpty { "пока ничего" }
    override val filesSub = "фото и документы со строк"
    override val filesEmpty = "Файлов пока нет. Прикрепите к строке — скрепкой в её листе или «+» у поля."
    override val documents = "документы"
    override val filterPeople = "люди"
    override val filterOrgs = "компании"
    override val company = "компания"
    override val markOrg = "это компания ›"
    override val markPerson = "это человек ›"
    override val call = "позвонить"
    override val message = "написать"
    override val mail = "письмо"
    override val weekTitle = "Неделя"
    override fun weekLine(open: Int, done: Int, events: Int) = listOfNotNull(
        open.takeIf { it > 0 }?.let { "$it " + plural(it, "дело", "дела", "дел") + " впереди" },
        done.takeIf { it > 0 }?.let { "$it сделано" },
        events.takeIf { it > 0 }?.let { "$it " + plural(it, "встреча", "встречи", "встреч") },
    ).joinToString(" · ").ifBlank { "пустая неделя" }
    override val weekFree = "свободно"
    override val weekSub = "семь дней на одной странице"
    override val weekLink = "неделя ›"
    override val propWhoTo = "вы обещали"
    override val propWhoFrom = "ждёте"
    override val offerToday = "› сегодня"
    override val threadClosed = "нить закрыта"
    override val threadOpened = "нить открыта снова"
    override val closeThread = "закрыть нить ›"
    override val reopenThread = "открыть нить снова ›"
    override val remind = "напомнить"
    override fun remindText(name: String, what: String) = "Добрый день! Напоминаю: ${what.replaceFirstChar { it.lowercase() }}."
    override val calendarSetting = "Календарь телефона"
    override val calendarOn = "на страницах"
    override val calendarOff = "не показывать"
    override val stampTitle = "день закрыт"
    override fun stampLine(at: String, s: ru.anjeib.margin.book.DayStats) = listOfNotNull(
        at,
        s.done.takeIf { it > 0 }?.let { "сделано $it" },
        s.moved.takeIf { it > 0 }?.let { "перенесено $it" },
        s.dropped.takeIf { it > 0 }?.let { "не делаем $it" },
    ).joinToString(" · ")
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
    override fun to(name: String) = "→\u00A0$name"
    override fun from(name: String) = "←\u00A0$name"
    override fun with(name: String) = "with\u00A0$name"
    override fun moveNo(n: Int) = "moved ${n}×"
    override fun ask(what: String) = when (what) { "when" -> "when?"; "who" -> "who?"; "thread" -> "a thread?"; else -> "?" }
    override fun owes(promised: Int, waited: Int) = listOfNotNull(
        promised.takeIf { it > 0 }?.let { "you promised $it" }, waited.takeIf { it > 0 }?.let { "waiting for $it" },
    ).joinToString(" · ").ifEmpty { null }
    override fun summary(s: Summary) = listOfNotNull(
        owes(s.promised, s.waited),
        s.questions.takeIf { it > 0 }?.let { "$it question" + if (it == 1) "" else "s" },
        s.tails.takeIf { it > 0 }?.let { "$it left over" },
    ).joinToString(" · ").let { if (it.isEmpty()) "" else "on the margin: $it" }
    override val composerHint = "Write as you think…"
    override fun writtenOn(d: LocalDate?, today: LocalDate) = "written for " + (d?.let { near(it, today) } ?: "later")
    override fun writtenN(n: Int) = "$n line" + if (n == 1) " written" else "s written"
    override fun movedOn(d: LocalDate?, today: LocalDate) = "moved to " + (d?.let { near(it, today) } ?: "later")
    override val deleted = "line deleted"
    override fun linesN(n: Int) = "$n line" + if (n == 1) "" else "s"
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
    override val writeUnder = "write on…"
    override val markKind = "Step or note"
    override val deleteForGood = "delete for good"
    override val backToWork = "bring it back"
    override fun remindWho(name: String) = "remind $name"
    override fun ofDone(done: Int, total: Int) = "$done of $total"
    override fun changedTo(text: String, day: LocalDate?, time: String?, today: LocalDate) =
        "«${short(text)}» → " + (listOfNotNull(day?.let { near(it, today) }, time).joinToString(" ").ifEmpty { "later" })
    override fun changedDropped(text: String) = "«${short(text)}» — not doing"
    override val editing = "a change"
    override val doneWord = "done"
    override fun changedDone(text: String) = "«${short(text)}» — done"
    override fun movedAll(n: Int, to: LocalDate?, today: LocalDate) = "$n line" + (if (n == 1) "" else "s") + " → " + (to?.let { near(it, today) } ?: "later")
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
    override fun ritualLine(n: Int) = if (n == 1) "1 line is open — decide it" else "$n lines are open — decide each"
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
        Triple("Show the logo", "On the left Margin writes when: the day, the hour. Under the words — with whom, where, in which thread.", "time"),
        Triple("Gym", "↻ — a repeat. Done — the next one appears on its page.", "repeat"),
        Triple("Renew the subscription", "In red pen — left over: not done in time. Decide what to do.", "tail"),
        Triple("Banner sources", "→ Lyosha — you promised. ← Dima — promised to you.", "to"),
    )
    override val helpTitle = "How to read the margin"
    override val helpLine = "when on the left, the mark on the rule, your words on the right with Margin's notes under them"
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
    override val bellLines = "Lines with a time"
    override val bellDay = "Morning and evening"
    override val today = "today"
    override val bellDone = "Done"
    override val bellHour = "In an hour"
    override fun morningLine(n: Int, tails: Int) = "$n line" + (if (n == 1) "" else "s") + " for today" + (if (tails > 0) ", $tails left over" else "")
    override fun answer(a: Ask.Answer, today: LocalDate): Triple<String, String?, String> {
        val n = a.lines.size
        val t = things(a.lines)
        val name = a.name
        return when (a.about) {
            Ask.About.Promised -> when {
                name == null -> Triple(if (n > 0) "Promised: $t" else "No open promises", null, "")
                n > 0 -> Triple("Promised to ", name, ": $t")
                else -> Triple("Nothing promised to ", name, "")
            }
            Ask.About.Waited -> when {
                name == null -> Triple(if (n > 0) "Expected: $t" else "Waiting for nothing", null, "")
                n > 0 -> Triple("Expected from ", name, ": $t")
                else -> Triple("Nothing expected from ", name, "")
            }
            Ask.About.Person -> Triple("", name ?: "", if (n > 0) ": $t open" else ": nothing open")
            Ask.About.Thread -> Triple("", name ?: "", if (n > 0) ": $t open" else ": all done")
            Ask.About.Day -> {
                val d0 = a.from ?: today; val d1 = a.to ?: d0
                if (d0 == d1) Triple("", dayName(d0, today), if (n > 0) " — $t" else " — empty")
                else Triple((if (!d0.isAfter(today)) "This week" else "Next week") + if (n > 0) " — $t" else " — empty", null, "")
            }
            Ask.About.Tails -> Triple(if (n > 0) "Burning: $t" else "Nothing is burning", null, "")
            Ask.About.Done -> {
                val span = when { a.from == today && a.to == today -> "today"; a.from == a.to -> "yesterday"; (a.from?.let { daysBetween(it, today) } ?: 0) <= 7 -> "this week"; else -> "this month" }
                Triple(if (n > 0) "Done $span: $t" else "Nothing done $span", null, "")
            }
            Ask.About.Found -> Triple(if (n > 0) "Found: $t" else "Nothing found", null, "")
            Ask.About.Unknown -> Triple(a.said ?: needNet, null, "")
            Ask.About.Margin -> Triple(if (n > 0) "Promises and lines left over — $t" else "Nothing on the margin", null, "")
        }
    }
    private fun things(lines: List<Line>): String = listOfNotNull(
        lines.count { it.kind == Kind.Todo }.takeIf { it > 0 }?.let { "$it to-do" + if (it == 1) "" else "s" },
        lines.count { it.kind == Kind.Event }.takeIf { it > 0 }?.let { "$it meeting" + if (it == 1) "" else "s" },
        lines.count { it.kind == Kind.Note }.takeIf { it > 0 }?.let { "$it note" + if (it == 1) "" else "s" },
    ).joinToString(", ")
    private fun dayName(d: LocalDate, today: LocalDate) = when {
        d == today -> "Today"
        d == today.plusDays(1) -> "Tomorrow"
        d == today.minusDays(1) -> "Yesterday"
        d.isAfter(today) && d.isBefore(today.plusDays(7)) -> weekday(d)
        else -> dayMonth(d)
    }
    override val thinking = "reading the notebook…"
    override val needNet = "Only the AI understands this question — it needs a network."
    override fun more(n: Int) = "$n more ›"
    override val close = "Close"
    override val rereading = "re-reading…"
    override val reread = "re-read"
    override fun markedDone(n: Int) = "re-read, closed: $n"
    override fun closeAsk(lines: List<Pair<String, Boolean>>) = when {
        lines.size == 1 && lines[0].second -> "no longer needed: «${lines[0].first}»?"
        lines.size == 1 -> "done: «${lines[0].first}»?"
        else -> "close " + lines.joinToString(", ") { "«${it.first}»" } + "?"
    }
    override fun closed(n: Int) = "closed: $n"
    override val yes = "yes"
    override val no = "no"
    override val attachPhoto = "Photo"
    override val attachCamera = "Camera"
    override val attachFile = "File"
    override val attachContact = "Contact"
    override val listening = "listening…"
    override val heardNothing = "nothing heard — try again?"
    override val doneReply = "× done"
    override val transcribing = "reading…"
    override val voiceOff = "Voice needs a network right now."
    override val micDenied = "Dictation needs the microphone."
    override val stop = "Stop"
    override fun personAdded(name: String) = "$name — in people"
    override val handedTitle = "To Margin"
    override fun handedFrom(app: String) = "from $app"
    override val openInMargin = "open in Margin ›"
    override val propFile = "file"
    override val share = "Share"
    override val removeFile = "remove from the line"
    override val fileOpen = "open"
    override val fileShare = "share"
    override val fileRemoved = "file removed from the line"
    override fun stepsInNote(n: Int) = "$n to-do" + (if (n == 1) "" else "s") + " in the note — make them steps?"
    override fun stepsAdded(n: Int) = "$n step" + (if (n == 1) "" else "s") + " under the note"
    override val whole = "read in full ›"
    override val longNote = "one note"
    override val welcomeLine = "a planner that writes in the margin"
    override fun welcomeRows() = listOf(
        Triple("Margin", "none", "On the margin Margin writes when; under your words — who and what it belongs to."),
        Triple(null, "note", "On the right — your words. Write as you think, in one field below."),
        Triple("tomorrow", "open", "A dot on the rule is a to-do. Tap it."),
        Triple("→\u00A0Alex", "none", "An arrow is a promise: → you promised, ← promised to you."),
        Triple("17:00", "event", "A ring is a meeting, a dash is a note."),
        Triple("→ Mon", "moved", "In the evening turn the page: every open line gets its decision."),
        Triple("?", "none", "Ask «what did I promise Alex?» — the answer appears over the field."),
    )
    override val welcomeTry = "Tap the dot — done. Tap again — it comes back."
    override val welcomeTried = "That's how it works. The rest is as simple."
    override val welcomeExamples = "see an example ›"
    override val welcomeClean = "start with a clean page ›"
    override val welcomeBegin = "begin ›"
    override val voiceSetting = "Voice"
    override val voiceNow = "write at once"
    override val voiceField = "into the field first"
    override val offlineVoice = "Voice offline"
    override val offlineReady = "ready"
    override val offlineRetry = "try again"
    override fun offlineGet(mb: Int) = "download, $mb MB"
    override val lockSetting = "Fingerprint lock"
    override val backupSetting = "Google backup"
    override val on = "on"
    override val off = "off"
    override val bellSetting = "Reminders"
    override val bellOn = "allowed"
    override val bellOff = "allow"
    override val inviteSetting = "AI access"
    override val inviteOwn = "your code"
    override val inviteShared = "shared code"
    override val inviteNone = "none"
    override val inviteHint = "The AI re-reads hard phrases, answers difficult questions and transcribes voice. If you were given your own code, enter it; empty — the shared one."
    override fun nextPage(lines: List<Line>) = "tomorrow — " + things(lines).ifBlank { "nothing yet" } + " ›"
    override fun peopleLine(people: Int, orgs: Int) = listOfNotNull(
        "$people " + if (people == 1) "person" else "people",
        orgs.takeIf { it > 0 }?.let { "$it compan" + if (it == 1) "y" else "ies" },
    ).joinToString(" · ")
    override fun allThreads(n: Int) = "all threads · $n ›"
    override fun allPeople(n: Int) = "all people and companies · $n ›"
    override val addressBook = "People and companies"
    override val findPerson = "Name, company, who…"
    override val filterAll = "all"
    override val filesTitle = "Files"
    override fun filesLine(photos: Int, docs: Int) = listOfNotNull(
        photos.takeIf { it > 0 }?.let { "$it photo" + if (it == 1) "" else "s" }, docs.takeIf { it > 0 }?.let { "$it document" + if (it == 1) "" else "s" },
    ).joinToString(" · ").ifEmpty { "nothing yet" }
    override val filesSub = "photos and documents from your lines"
    override val filesEmpty = "No files yet. Attach one to a line — the clip in its sheet, or «+» by the field."
    override val documents = "documents"
    override val filterPeople = "people"
    override val filterOrgs = "companies"
    override val company = "company"
    override val markOrg = "this is a company ›"
    override val markPerson = "this is a person ›"
    override val call = "call"
    override val message = "message"
    override val mail = "email"
    override val weekTitle = "Week"
    override fun weekLine(open: Int, done: Int, events: Int) = listOfNotNull(
        open.takeIf { it > 0 }?.let { "$it ahead" },
        done.takeIf { it > 0 }?.let { "$it done" },
        events.takeIf { it > 0 }?.let { "$it meeting" + if (it == 1) "" else "s" },
    ).joinToString(" · ").ifBlank { "an empty week" }
    override val weekFree = "free"
    override val weekSub = "seven days on one page"
    override val weekLink = "week ›"
    override val propWhoTo = "you promised"
    override val propWhoFrom = "waiting for"
    override val offerToday = "› today"
    override val threadClosed = "thread closed"
    override val threadOpened = "thread open again"
    override val closeThread = "close the thread ›"
    override val reopenThread = "open the thread again ›"
    override val remind = "remind"
    override fun remindText(name: String, what: String) = "Hello! A reminder: ${what.replaceFirstChar { it.lowercase() }}."
    override val calendarSetting = "Phone calendar"
    override val calendarOn = "on the pages"
    override val calendarOff = "hidden"
    override val stampTitle = "day closed"
    override fun stampLine(at: String, s: ru.anjeib.margin.book.DayStats) = listOfNotNull(
        at,
        s.done.takeIf { it > 0 }?.let { "$it done" },
        s.moved.takeIf { it > 0 }?.let { "$it moved on" },
        s.dropped.takeIf { it > 0 }?.let { "$it not doing" },
    ).joinToString(" · ")
}
