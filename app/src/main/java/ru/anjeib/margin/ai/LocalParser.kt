package ru.anjeib.margin.ai

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import ru.anjeib.margin.data.AppData
import ru.anjeib.margin.data.dative
import ru.anjeib.margin.data.next
import ru.anjeib.margin.data.dayMonthShort
import ru.anjeib.margin.data.dowShort
import ru.anjeib.margin.data.genitive
import ru.anjeib.margin.data.sentence
import ru.anjeib.margin.data.withNames
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters

/**
 * Russian phrases into matters without any AI — on the phone, offline, at once.
 *
 * What it understands: the kind (from «/» tags or «идея:»), days and hours («завтра в 15», «в пятницу», «через неделю»,
 * «12 октября», «к среде», «вечером»), several days («в пн и чт»), repeats («каждый вторник в 10», «первого числа каждый
 * месяц»), people (contacts and common first names in any case, even typed in lowercase), «Дима пришлёт макет» (we wait),
 * «обещал Анне показать» (promised), an address («на Ленина 5»), and several matters in one message (lines, «;», «ещё»,
 * a new verb after a comma). What it leaves to the AI: reports («созвонились…»), edits of what exists («перенеси…»),
 * long dictation — then [Result.sure] is false and, with a network, the AI is asked instead.
 *
 * The result goes the AI's way: [plan] builds the same actions, [Assistant.apply] checks and applies them.
 */
object LocalParser {

    data class Item(
        val kind: String = "task", // task | topic | idea
        val title: String,
        val due: LocalDate? = null,
        val start: LocalDate? = null,
        val time: String? = null,
        val part: String? = null,
        val days: List<Pair<LocalDate, String?>> = emptyList(),
        val repeat: Triple<String, Int, List<Int>>? = null, // freq, interval, weekdays
        val people: List<String> = emptyList(),
        val waitingFor: String? = null,
        val promisedTo: String? = null,
        val place: String? = null,
        val sphere: String? = null,
    )

    data class Result(val items: List<Item>, val sure: Boolean, val why: String? = null)

    // ---------------- words ----------------

    private const val L = "\\p{L}"
    private const val WD = "(понедельник$L*|вторник$L*|сред[ауеы]|средам|четверг$L*|пятниц$L*|пятн|субб?от$L*|воскресень$L*|пн|вт|ср|чт|пт|сб|вс)"
    private const val MON = "(январ$L*|феврал$L*|март$L*|апрел$L*|ма[йяе]|июн$L*|июл$L*|август$L*|сентябр$L*|октябр$L*|ноябр$L*|декабр$L*)"
    private const val B = "(?<!$L)"
    private const val E = "(?!$L)"

    private fun weekday(w: String): Int? {
        val x = w.lowercase()
        return when {
            x.startsWith("понедельник") || x == "пн" -> 1
            x.startsWith("вторник") || x == "вт" -> 2
            x.startsWith("сред") || x == "ср" -> 3
            x.startsWith("четверг") || x == "чт" -> 4
            x.startsWith("пятн") || x == "пт" -> 5
            x.startsWith("суббот") || x.startsWith("субот") || x == "сб" -> 6
            x.startsWith("воскресен") || x == "вс" -> 7
            else -> null
        }
    }

    private fun month(w: String): Int? = listOf("янв", "фев", "мар", "апр", "ма", "июн", "июл", "авг", "сен", "окт", "ноя", "дек")
        .indexOfFirst { w.lowercase().startsWith(it) }.takeIf { it >= 0 }?.plus(1)

    private val ordinals = listOf("перв", "втор", "трет", "четвёрт|четверт", "пят", "шест", "седьм", "восьм", "девят", "десят",
        "одиннадцат", "двенадцат", "тринадцат", "четырнадцат", "пятнадцат", "шестнадцат", "семнадцат", "восемнадцат", "девятнадцат", "двадцат")
    private const val ORD = "((?:двадцать\\s+|тридцать\\s+)?(?:перв|втор|трет|четв[её]рт|пят|шест|седьм|восьм|девят|десят|одиннадцат|двенадцат|тринадцат|четырнадцат|пятнадцат|шестнадцат|семнадцат|восемнадцат|девятнадцат|двадцат|тридцат)(?:ого|ому|ье|ьего|ьему|ое))"
    private fun ordinal(w: String): Int? {
        val x = w.lowercase().replace('ё', 'е')
        if (x.startsWith("двадцать")) return 20 + (ordinal(x.substringAfter(' ')) ?: return null)
        if (x.startsWith("тридцать")) return 31
        if (x.startsWith("тридцат")) return 30
        return ordinals.indexOfFirst { p -> p.split('|').any { x.startsWith(it.replace('ё', 'е')) } }.takeIf { it >= 0 }?.plus(1)
    }

    private val numberWords = mapOf("один" to 1, "одну" to 1, "два" to 2, "две" to 2, "пару" to 2, "три" to 3, "четыре" to 4, "пять" to 5, "шесть" to 6, "семь" to 7, "десять" to 10, "полчаса" to 0,
        "одного" to 1, "одной" to 1, "двух" to 2, "трёх" to 3, "трех" to 3, "четырёх" to 4, "четырех" to 4, "пяти" to 5, "шести" to 6, "семи" to 7, "десяти" to 10)

    /** Common first names (and family words) — so lowercase dictation still finds people. Words that are also ordinary nouns are left out. */
    private val commonNames = ("Александр Алексей Андрей Антон Артём Артем Борис Вадим Валентин Василий Виктор Виталий Владимир Владислав Вячеслав Георгий Глеб " +
        "Григорий Даниил Денис Дмитрий Евгений Егор Иван Игорь Илья Кирилл Константин Леонид Максим Матвей Михаил Никита Николай Олег Павел Пётр Руслан " +
        "Сергей Станислав Степан Тимур Тимофей Фёдор Юрий Ярослав Саша Лёша Леша Дима Миша Женя Коля Паша Петя Серёжа Сережа Костя Витя Вова Гоша Юра Вася Петя Гена " +
        "Даня Ваня Стёпа Федя Боря Гриша Артур Эдик Алёна Алина Алла Анастасия Анна Валентина Валерия Вероника Виктория Галина Дарья Диана Екатерина Елена " +
        "Елизавета Жанна Зоя Инна Ирина Карина Кира Ксения Лариса Лидия Людмила Маргарита Марина Мария Наталья Наталия Нина Оксана Ольга Полина Светлана " +
        "София Софья Таисия Тамара Татьяна Ульяна Юлия Яна Аня Катя Лена Маша Наташа Оля Света Таня Юля Настя Даша Ксюша Лиза Соня Вика Галя Надя Ира Люба Лера Рита Майя Ева Варя Тоня Валя Слава Рома Лёня Толя Стас Мама Папа").split(' ')

    /** Every case of a name we care about → the name: «Анне», «Анной», «Анну», «Анны» → «Анна». */
    private fun forms(name: String): Set<String> {
        val n = name.lowercase()
        val out = mutableSetOf(n, name.dative().lowercase(), name.genitive().lowercase())
        val hush = n.length > 1 && n[n.length - 2] in "жшчщц"
        when {
            n.endsWith("ия") -> { out += n.dropLast(1) + "ей"; out += n.dropLast(1) + "ю" }
            n.endsWith("а") -> { out += n.dropLast(1) + (if (hush) "ей" else "ой"); out += n.dropLast(1) + "у" }
            n.endsWith("я") -> { out += n.dropLast(1) + "ей"; out += n.dropLast(1) + "ю" }
            n.endsWith("й") -> { out += n.dropLast(1) + "ем" }
            n.endsWith("ь") -> { out += n.dropLast(1) + "ем"; out += n.dropLast(1) + "ью" }
            n.last() in "бвгдзклмнпрстфх" -> { out += n + "ом"; out += n + "а"; out += n + "у"; out += n + "е" }
            n.last() in "жшчщц" -> out += n + "ем"
        }
        return out.map { it.replace('ё', 'е') }.toSet()
    }

    private class Names(d: AppData) {
        val byForm = HashMap<String, String>()
        /** The user's own contacts as they spelled them — for «ЖЭК», «МТС» written right in titles. */
        val contacts: List<String> = d.persons.filter { !it.archived }.map { it.name }
        init {
            commonNames.forEach { n -> forms(n).forEach { f -> byForm.putIfAbsent(f, n.replace("Артем", "Артём")) } }
            // The user's own people win over the dictionary («Маша» their friend, «ЖЭК» their office).
            d.persons.filter { !it.archived }.forEach { p -> (listOf(p.name) + p.aliases).forEach { n -> forms(n).forEach { f -> byForm[f] = p.name } } }
        }
        fun of(word: String): String? = byForm[key(word)]
        /** Only the name as it is («Дима»), not «Диме» — the one who does or gives something. */
        fun subject(word: String): String? = of(word)?.takeIf { n -> key(n) == key(word) }
        private fun key(w: String) = w.lowercase().replace('ё', 'е').trim(',', '.', ':', '!', '?', '«', '»')
    }

    // ---------------- cues ----------------

    // «идея: …», «у меня идея …», «есть идея, …», «запиши идею …», «новая мысль — …»: said any way, it's an idea (owner, 12 окт).
    private const val IDEA = "(?:(?:(?:у\\s+меня|есть|запиши|записать|добавь|новая|новую)\\s+){0,3}(?:идея|идею|мысль|заметк\\p{L}*)(?:\\s+такая)?|на заметку|подумать о|подумать над)\\s*[:—–,.-]?\\s*"
    // «дело: …», «новое дело …», «задача …», «запиши дело …» — the word says what it is and leaves the title; «дело в том…» is no matter.
    private const val TASK = "(?:(?:новое|новую|новая|добавь|добавить|запиши|записать|создай|создать)\\s+)?(?:дело|задач[ауи])(?!\\s+в\\s+том)\\s*[:—–,.-]?\\s*"
    private val ideaCue = Regex("^$IDEA", RegexOption.IGNORE_CASE)
    /** Reports and edits need what exists and what it means — the AI's work. */
    // Only what is plainly a question: «…?» at the end, or «что горит» — «Когда приедет Дима, отдать ключи» stays a matter.
    private val asked = Regex("\\?\\s*$|^что горит", RegexOption.IGNORE_CASE)
    private val aiCue = Regex("${B}(перенес$L*|перенеси|перенос$L*|сдвин$L*|измени$L*|исправ$L*|переимен$L*|поменя$L*|удали$L*|убери|отмени$L*|не надо|уже не|созвонил$L*|договорил$L*|сказал$L*|ответил$L*|готово|сделал$L*|выполнил$L*|получил$L*|разбей$L*|мне кажется|" +
        // A report of what happened: «встречались, обсудили», «клиент отказался», «согласовали».
        "встречал$L*|обсудил$L*|обсуждал$L*|поговорил$L*|списал$L*ись|решил$L*|отказал$L*|согласовал$L*|утвердил$L*|передумал$L*)$E", RegexOption.IGNORE_CASE)
    private val filler = Regex("^(?:(?:надо|нужно|не забыть|не забудь|напомни(?:ть)?(?: мне)?|мне|я|хочу|пора|обязательно|плиз|пожалуйста)[,\\s]+)+", RegexOption.IGNORE_CASE)
    /** A waiting-for that points at the person named in the previous matter; parse() resolves it. */
    private const val PREV = "\u0000prev"
    /** Words that never name who gives something. */
    private val notSubjects = setOf("я", "мы", "мне", "нам", "ты", "вы", "вам", "тебе", "ему", "ей", "им", "кто", "что", "когда", "если", "надо", "нужно",
        "пусть", "завтра", "сегодня", "потом", "скоро", "уже", "ещё", "еще", "как", "так", "не", "ни", "а", "и", "но", "это", "этот", "эта", "то", "там",
        "тут", "здесь", "может", "можно", "вроде", "точно", "просто", "срочно", "обязательно", "сначала", "пока", "чтобы", "чтоб", "хочу", "хотел",
        "хотела", "планирую", "собираюсь", "буду", "нужен", "нужна", "нужны", "должен", "должна", "должны")
    private val verbStart = Regex("^(?:$L+(?:ть|ти|чь|ться|тись)|позвон$L*|напис$L*|купи|сходи|сделай|отправь|забери|заплати|оплати|закажи|запишись)$E", RegexOption.IGNORE_CASE)
    private val waitVerbs = "(пришл[её]т|пришлют|прислать|присылает|скин[еу]т|скинуть|отправит|отправить|верн[её]т|вернуть|сда[её]т|сдаст|сдать|сдаёт|сдает|даст|ответит|подготовит|сделает|принес[её]т|передаст|подпишет|привез[её]т|оплатит)"
    /** «она пришлёт…», «он обещал скинуть…»: the one just named in the words before. */
    private val pronounGives = Regex("^(?:он|она|они)\\s+(?:обещал$L*\\s+|должн$L*\\s+)?$waitVerbs", RegexOption.IGNORE_CASE)

    private val leftoverDay = Regex("${B}(сегодня|завтра|послезавтра|понедельник$L*|вторник$L*|сред[ауеы]|четверг$L*|пятниц$L*|суббот$L*|воскресень$L*|\\d{1,2}\\s+$MON|\\d{1,2}-?(?:го|му|ому|ого))$E", RegexOption.IGNORE_CASE)

    // ---------------- parsing ----------------

    fun parse(text0: String, d: AppData, today: LocalDate = LocalDate.now(), kind: String? = null, sphere: String? = null,
              people: List<String> = emptyList(), waitFor: String? = null, promiseTo: String? = null,
              /** «/жду», «/обещаю» without «@»: the other side is the person named in the words («/обещаю Маше вернуть книгу»). */
              wait: Boolean = false, promise: Boolean = false,
              /** The hour now — a weekday or a repeat whose hour has already passed today starts next time. */
              now: LocalTime? = LocalTime.now().takeIf { today == LocalDate.now() }): Result {
        // Times and days said in words («в пятнадцать», «двадцатого октября») read as digits — dictation writes them so.
        // «Идея. Блог о дизайне» — speech puts a full stop after the word that says what it is: read it as «Идея: …».
        val text = SpokenNumbers.normalize(text0.trim()).replace(Regex("^\\s*(идея|мысль|заметка|дело|задача)\\s*[.!]\\s+", RegexOption.IGNORE_CASE), "$1: ")
        if (text.isEmpty()) return Result(emptyList(), true)
        val names = Names(d)
        var sure = true
        var why: String? = null
        // The phone reads Russian only: «Call mom tomorrow at 5pm» would become a matter without its day — the AI's, when there is one.
        if (!Regex("[А-Яа-яЁё]").containsMatchIn(text) && Regex("[A-Za-z]").containsMatchIn(text)) { sure = false; why = "lang" }
        if (aiCue.containsMatchIn(text)) { sure = false; why = why ?: "report-or-edit" }
        // A question to the assistant («что горит на этой неделе?») is not a matter: only the AI answers it.
        if (asked.containsMatchIn(text.trim())) return Result(emptyList(), false, "question")
        if (sure && reported(text, names)) { sure = false; why = "report" }
        // A phone or an e-mail is about a person — the AI files it on their card.
        if (Regex("\\+?\\d[\\d\\s()-]{8,}\\d|\\S+@\\S+\\.\\p{L}{2,}").containsMatchIn(text)) { sure = false; why = why ?: "contact" }

        val chunks = split(text)
        // A long stream with no punctuation is dictation — the AI splits it better.
        if (!Regex("[\\n.;,!?]").containsMatchIn(text) && text.split(Regex("\\s+")).size > 16) { sure = false; why = why ?: "long" }
        // «@Маша», «/жду», «/обещал» are said outright: they win over what the words seem to say.
        val items0 = chunks.mapNotNull { one(it, names, today, kind, sphere, now) }.map { i0 ->
            val named = i0.people.firstOrNull()
            val i = when {
                waitFor == null && wait && i0.waitingFor == null && named != null -> i0.copy(waitingFor = named, promisedTo = null, people = i0.people - named)
                promiseTo == null && promise && i0.promisedTo == null && named != null -> i0.copy(promisedTo = named, waitingFor = null, people = i0.people - named)
                else -> i0
            }
            if (people.isEmpty() && waitFor == null && promiseTo == null) i
            else i.copy(people = (i.people + people).distinct().filter { it != (waitFor ?: i.waitingFor) && it != (promiseTo ?: i.promisedTo) },
                waitingFor = waitFor ?: i.waitingFor.takeIf { promiseTo == null }, promisedTo = promiseTo ?: i.promisedTo.takeIf { waitFor == null },
                title = if (waitFor != null && i.waitingFor == null) i.title else i.title)
        }
        // «она пришлёт…»: the person named in the matter before («созвон с Мариной» → we wait for Марина).
        var lastPerson: String? = null
        val items = items0.map { i ->
            val r = if (i.waitingFor == PREV) {
                val who = lastPerson
                if (who == null) i.copy(waitingFor = null)
                else i.copy(waitingFor = who, people = i.people - who,
                    title = ("Получить " + i.title.midCase() + " от " + who.genitive()).sentence().withNames(listOf(who)))
            } else i
            (r.people.firstOrNull() ?: r.waitingFor ?: r.promisedTo)?.let { lastPerson = it }
            r
        }
        if (items.isEmpty()) return Result(emptyList(), false, "empty")
        // What someone owes, with more after a comma («Дима сделает дизайн, на мне тексты») is two matters — the AI's.
        if (items.any { it.waitingFor != null && it.title.contains(',') }) { sure = false; why = why ?: "wait-and-more" }
        // A day left in a title means two matters with their own days ran together («Катя к четвергу, Ирина до среды»).
        if (items.any { leftoverDay.containsMatchIn(it.title) }) { sure = false; why = why ?: "two-days" }
        return Result(items, sure, why)
    }

    /** The words mid-sentence: the first letter lowered — unless the first word is an abbreviation («КП», «6-НДФЛ»). */
    private fun String.midCase(): String { val w = substringBefore(' '); return if (w.length >= 2 && w.none { it.isLowerCase() }) this else replaceFirstChar { it.lowercase() } }
    private val danglingTail = Regex("\\s+(?:до|с|со|к|ко|в|во|на|по|от|за|из|у|при|после|перед|через|о|об|для|под)$", RegexOption.IGNORE_CASE)
    private val pastVerb = Regex("$L{2,}(?:[аеёиоуяыю]л|[её]з|[её]с)(?:а|и|о|ся|ась|ись|ось)?", RegexOption.IGNORE_CASE)
    private val notPast = setOf("недели", "неделе", "дела", "дело", "цели", "школа", "модели", "мебели", "канал", "журнал", "сериал", "материал",
        "вокзал", "финал", "сигнал", "оригинал", "персонал", "интервал", "капитал", "стола", "пола", "села", "тела", "мыла", "пчела", "стрела",
        "через", "адрес", "интерес", "процесс", "прогресс", "бизнес", "конгресс", "навес", "отвес")
    private val calm = setOf("уже", "тоже", "наконец", "сегодня", "вчера", "только", "что", "всё", "все", "сам", "сама")
    private val subjects = setOf("я", "мы", "он", "она", "они", "клиент", "клиентка", "заказчик", "заказчица", "бухгалтер", "мастер", "курьер")

    /**
     * A report of what has happened — «Анна уже прислала договор», «оплатил интернет», «Маша заболела»: a verb in the past
     * tense at the start of a clause or right after who did it. Such words change what exists: the AI's work, never a new matter.
     */
    /** What happened, said after the fact («Созвонились с Катей: лендинг переносим…», «клиент согласовал макет»): a record, not a matter. */
    private val happened = Regex("${B}(созвонил$L*|договорил$L*|сказал$L*|ответил$L*|встречал$L*|встретил$L*|обсудил$L*|обсуждал$L*|поговорил$L*|списал$L*ись|решил$L*|отказал$L*|согласовал$L*|утвердил$L*|передумал$L*|получил$L*|выполнил$L*|сделал$L*)$E", RegexOption.IGNORE_CASE)
    fun isReport(text: String, d: AppData): Boolean {
        val t = text.trim()
        // An edit asked of what exists («перенеси…», «удали…») is not a report.
        if (Regex("^(?:перенес$L*|перенеси|сдвин$L*|измени$L*|исправ$L*|переимен$L*|удали$L*|убери|отмени$L*)$E", RegexOption.IGNORE_CASE).containsMatchIn(t)) return false
        return happened.containsMatchIn(t) || reported(t, Names(d))
    }

    private fun reported(text: String, names: Names): Boolean {
        if (Regex("^\\s*(?:всё|все)\\s*[,!.—-]|${B}(?:не\\s+состоится|отменяется|отменили)$E", RegexOption.IGNORE_CASE).containsMatchIn(text)) return true
        text.split(Regex("[,.;:!?\\n]+")).forEach { clause ->
            val w = clause.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.map { it.lowercase().trim('«', '»', '"', '(', ')') }
            var i = 0
            while (i < w.size && w[i] in calm) i++
            // «обещал Анне…», «хотел купить…» — past in form, a plan in meaning.
            fun past(x: String?) = x != null && x.length >= 4 && x !in notPast && pastVerb.matches(x) && names.of(x) == null &&
                !Regex("^(?:по)?обещал|^хотел|^собирал|^планировал|^думал").containsMatchIn(x)
            if (past(w.getOrNull(i))) return true
            // Who, then (maybe «уже»), then what they did.
            val who = w.getOrNull(i)
            if (who != null && (who in subjects || names.of(who) != null)) {
                var j = i + 1
                while (j < w.size && w[j] in calm) j++
                if (past(w.getOrNull(j))) return true
            }
        }
        return false
    }

    /** Several matters in one message: lines, «;», sentences, «ещё», a new verb after a comma or «и». */
    /** The matters a text holds, each its own words — for a caller that files them one by one. */
    fun pieces(text: String): List<String> = split(text)

    private fun split(t0: String): List<String> {
        // «1. …», «- …», «• …» at line starts: a list typed by hand — the marks go before anything else splits the text.
        val t = t0.lines().joinToString("\n") { it.replace(Regex("^\\s*(\\d{1,2}[.)]|[-–—•*])\\s+"), "") }
        // «ещё» starts a new matter only after a whole one («купить хлеб, ещё позвонить»), not after «для vuzburo ещё…».
        // A full stop after «ул.», «д.», «г.» is an abbreviation, not the end of a matter.
        val big = t.split(Regex("\\n+|;\\s*|(?<=[.!?])(?<!(?<!$L)(?:ул|д|г|пр|кв|пер|стр|им)\\.)\\s+(?=\\p{Lu})|(?<=$L{1,30}\\s$L{1,30}\\s$L{1,30}),?\\s+(?:а ещё|а еще|и ещё|и еще|а также|ещё|еще|плюс)\\s+|" +
            // «купить хлеб, ещё позвонить в ЖЭК»: after a comma «ещё» with a verb is a new matter, however short the first.
            ",\\s*(?:а\\s+|и\\s+)?(?:ещё|еще)\\s+(?=$L+(?:ть|ти|чь|ться|тись)$E)", RegexOption.IGNORE_CASE))
            // «1. …», «- …», «• …»: a list typed by hand — each item one matter, without its mark.
            .map { it.replace(Regex("^\\s*(\\d{1,2}[.)]|[-–—•*])\\s+"), "").trim().trim('.', ',', ' ') }.filter { it.isNotBlank() }
        return big.flatMap { part ->
            if (ideaCue.containsMatchIn(part)) return@flatMap listOf(part)
            val pieces = part.split(Regex(",\\s+|\\s+и\\s+"))
            val out = mutableListOf<String>()
            pieces.forEach { p ->
                val first = p.trim().split(' ').firstOrNull().orEmpty()
                // A piece that starts with its own verb is a matter of its own; anything else belongs to the previous one.
                // One word is enough when it is a whole matter in itself: «…, заправиться и купить омывайку».
                if (out.isNotEmpty() && verbStart.matches(first) && (p.trim().split(' ').size >= 2 || Regex("$L{4,}(?:ться|тись)").matches(first))) out += p.trim()
                // «…созвон с Мариной, она пришлёт правки до среды» (the tour's own phrase; ChatGPT, 15 окт): what the person just
                // named will give is a matter of its own — we wait for it.
                else if (out.isNotEmpty() && pronounGives.containsMatchIn(p.trim())) out += p.trim()
                else if (out.isEmpty()) out += p.trim()
                else out[out.lastIndex] = out.last() + (if (part.contains(out.last() + ", " + p.trim())) ", " else " и ") + p.trim()
            }
            out
        }
    }

    private fun nextDow(today: LocalDate, dow: Int, nextWeek: Boolean = false): LocalDate {
        val d = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.of(dow)))
        return if (nextWeek) today.with(TemporalAdjusters.next(DayOfWeek.MONDAY)).with(TemporalAdjusters.nextOrSame(DayOfWeek.of(dow))) else d
    }

    private fun dayOfMonth(today: LocalDate, n: Int): LocalDate? {
        if (n !in 1..31) return null
        var m = today.withDayOfMonth(1)
        repeat(3) {
            if (n <= m.lengthOfMonth()) { val d = m.withDayOfMonth(n); if (!d.isBefore(today)) return d }
            m = m.plusMonths(1)
        }
        return null
    }

    private fun dateOf(today: LocalDate, day: Int, mon: Int): LocalDate? = runCatching {
        LocalDate.of(today.year, mon, day).let { if (it.isBefore(today.minusDays(1))) it.plusYears(1) else it }
    }.getOrNull()

    private fun one(chunk: String, names: Names, today: LocalDate, kind0: String?, sphere: String?, now: LocalTime? = null): Item? {
        var s = " $chunk "
        // Takes a matched phrase out of the words (a space stays in its place); [cutIf] only when the match is accepted.
        fun cutIf(r: Regex, f: (MatchResult) -> Boolean): Boolean {
            val m = r.find(s) ?: return false
            if (!f(m)) return false
            s = s.substring(0, m.range.first) + " " + s.substring(m.range.last + 1)
            return true
        }
        fun cut(r: Regex, f: (MatchResult) -> Unit): Boolean = cutIf(r) { f(it); true }
        var kind = kind0 ?: "task"
        cut(Regex("^\\s*$IDEA", RegexOption.IGNORE_CASE)) { kind = "idea" }
        if (kind == "task") cut(Regex("^\\s*$TASK", RegexOption.IGNORE_CASE)) { }
        // «в идеи …», «в заметки …», «слеш дело …» — the rest of the ways of saying it.
        if (kind0 == null) { val (m, rest) = marks(s); if (m.isNotEmpty()) { m.firstOrNull { it == "idea" }?.let { kind = "idea" }; s = " $rest " } }

        var due: LocalDate? = null; var start: LocalDate? = null; var time: String? = null; var part: String? = null
        var days = emptyList<Pair<LocalDate, String?>>()
        var repeat: Triple<String, Int, List<Int>>? = null
        // A date that came from a weekday («в четверг», «по пн и чт»), not from «сегодня»: it may move a week on.
        var byWeekday = false
        val ic = RegexOption.IGNORE_CASE

        // «с понедельника по среду» — a window, before «по средам» can read as a repeat.
        cut(Regex("${B}(?:с|со)\\s+$WD\\s+(?:по|до)\\s+$WD$E", ic)) { m ->
            val a = weekday(m.groupValues[1]); val b = weekday(m.groupValues[2])
            if (a != null && b != null) { val st = nextDow(today, a); start = st; due = st.with(TemporalAdjusters.nextOrSame(DayOfWeek.of(b))); byWeekday = true }
        }
        // Repeats first: «каждый вторник», «по понедельникам и четвергам», «каждый день», «первого числа каждый месяц».
        // «каждые две недели (по средам)»: the interval, then the days if said.
        var every = 1
        cut(Regex("${B}(?:кажд$L*|раз\\s+в)\\s+(\\d+|$L+)\\s+недел$L*$E", ic)) { m -> every = (m.groupValues[1].toIntOrNull() ?: numberWords[m.groupValues[1].lowercase()] ?: 1).coerceIn(1, 52) }
        var everyDays = 0
        cut(Regex("${B}(?:кажд$L*|раз\\s+в)\\s+(\\d+|$L+)\\s+(?:дн$L*|дня)$E", ic)) { m -> everyDays = (m.groupValues[1].toIntOrNull() ?: numberWords[m.groupValues[1].lowercase()] ?: 1).coerceIn(1, 365) }
        if (everyDays > 1) { repeat = Triple("daily", everyDays, emptyList()); due = today }
        if (repeat == null) cut(Regex("${B}(?:кажд$L*|по)\\s+$WD((?:\\s*(?:,|и)\\s*$WD|\\s+$WD)*)$E", ic)) { m ->
            val wds = Regex(WD, ic).findAll(m.value).mapNotNull { weekday(it.value) }.distinct().sorted().toList()
            if (wds.isNotEmpty()) { repeat = Triple("weekly", every, wds); due = wds.map { nextDow(today, it) }.minOrNull() }
        }
        if (repeat == null && every > 1) { repeat = Triple("weekly", every, listOf(today.dayOfWeek.value)); due = today }
        if (repeat == null) cut(Regex("${B}по\\s+будням$E", ic)) { repeat = Triple("weekly", 1, listOf(1, 2, 3, 4, 5)); due = (0L..6L).map { today.plusDays(it) }.first { it.dayOfWeek.value <= 5 } }
        if (repeat == null) cut(Regex("${B}по\\s+выходным$E", ic)) { repeat = Triple("weekly", 1, listOf(6, 7)); due = nextDow(today, 6) }
        if (repeat == null) cut(Regex("${B}(?:кажд$L*\\s+(?:день|утро|вечер)|ежедневно|раз\\s+в\\s+день)$E", ic)) { m ->
            repeat = Triple("daily", 1, emptyList()); due = today
            // «каждое утро зарядка»: the part of the day comes with it.
            when { m.value.lowercase().endsWith("утро") -> part = "morning"; m.value.lowercase().endsWith("вечер") -> part = "evening" }
        }
        if (repeat == null) cut(Regex("${B}(?:кажд$L*\\s+недел$L*|еженедельно|раз\\s+в\\s+неделю)$E", ic)) { repeat = Triple("weekly", 1, listOf(today.dayOfWeek.value)); due = today }
        if (repeat == null) cut(Regex("${B}(?:кажд$L*\\s+месяц|ежемесячно|раз\\s+в\\s+месяц)$E", ic)) { repeat = Triple("monthly", 1, emptyList()) }
        if (repeat == null) cut(Regex("${B}(?:кажд$L*\\s+квартал|ежеквартально|раз\\s+в\\s+квартал)$E", ic)) { repeat = Triple("monthly", 3, emptyList()) }
        if (repeat == null) cut(Regex("${B}(?:кажд$L*\\s+год|ежегодно)$E", ic)) { repeat = Triple("yearly", 1, emptyList()) }

        // A window said outright (owner, 13 окт): «с 9 по 11 октября», «9–11 октября», «с 28 сентября по 3 октября»,
        // «с понедельника по среду», «с 9 по 11» (without a month only with «по» — «с 9 до 11» are hours), «с 9.10 по 11.10».
        if (repeat == null) {
            // A window that has already begun («с 10 по 17 октября» said on the 15th): dateOf pushed its first day a year on —
            // pull it back to the year of its last day (Fable, 15 окт: the matter landed in 2027 and vanished from the page).
            fun span(a: LocalDate?, b: LocalDate?) { if (a != null && b != null) { start = if (a.isAfter(b)) a.minusYears(1) else a; due = b } }
            val windows: List<Pair<Regex, (MatchResult) -> Unit>> = listOf(
                Regex("${B}(?:с|со)\\s+(\\d{1,2})(?:-?го)?(?:\\s+$MON)?\\s+(?:по|до)\\s+(\\d{1,2})(?:-?(?:е|го))?\\s+$MON$E", ic) to { m ->
                    val mb = month(m.groupValues[4]); val ma = m.groupValues[2].takeIf { it.isNotBlank() }?.let { month(it) } ?: mb
                    if (ma != null && mb != null) span(dateOf(today, m.groupValues[1].toInt(), ma), dateOf(today, m.groupValues[3].toInt(), mb))
                },
                Regex("${B}(\\d{1,2})\\s*[-–—]\\s*(\\d{1,2})\\s+$MON$E", ic) to { m ->
                    month(m.groupValues[3])?.let { mo -> span(dateOf(today, m.groupValues[1].toInt(), mo), dateOf(today, m.groupValues[2].toInt(), mo)) }
                },
                Regex("${B}(?:с|со)\\s+(\\d{1,2})[./](\\d{1,2})\\s+(?:по|до)\\s+(\\d{1,2})[./](\\d{1,2})(?![\\d./])", ic) to { m ->
                    val (a, am, b, bm) = m.groupValues.drop(1).map { it.toInt() }
                    if (am in 1..12 && bm in 1..12) span(dateOf(today, a, am), dateOf(today, b, bm))
                },
                Regex("${B}(?:с|со)\\s+(\\d{1,2})(?:-?го)?\\s+по\\s+(\\d{1,2})(?:-?(?:е|го))?(?:\\s+числ$L*)?(?![\\d./:])$E", ic) to { m ->
                    val a = dayOfMonth(today, m.groupValues[1].toInt()); val n = m.groupValues[2].toInt()
                    if (a != null && n in 1..31) span(a, runCatching { a.withDayOfMonth(n) }.getOrNull()?.let { if (it.isBefore(a)) it.plusMonths(1) else it })
                },
            )
            for ((r, f) in windows) if (due == null && cut(r, f)) Unit
        }

        // «зарплата 10 и 25 числа», «15-го числа каждого месяца» (persona test, 15 окт): a monthly repeat on those days of the month.
        if (repeat == null && due == null) cut(Regex("${B}(\\d{1,2})(?:-?го)?((?:\\s*(?:,|и)\\s*\\d{1,2}(?:-?го)?)*)\\s+числ$L*(?:\\s+кажд$L*\\s+месяц$L*)?$E", ic)) { m ->
            val ds = Regex("\\d{1,2}").findAll(m.value).map { it.value.toInt() }.filter { it in 1..31 }.distinct().sorted().toList()
            if (ds.isNotEmpty()) { repeat = Triple("monthly", 1, ds); due = with(ru.anjeib.margin.data.Repeat("monthly", 1, ds)) { next(today.minusDays(1)) } }
        }
        // Several days: «в пн и чт», «во вторник и в пятницу», «12, 15 и 18 октября».
        // Bare «вт и чт 17:30» with no «в» before (persona test, 15 окт: the family's pool) is the week's rhythm — a weekly repeat.
        if (repeat == null && due == null) cut(Regex("${B}((?:в|во|на|к|до)\\s*)?$WD((?:\\s*(?:,|и)\\s*(?:в|во)?\\s*$WD|\\s+$WD)+)$E", ic)) { m ->
            val wds = Regex(WD, ic).findAll(m.value).mapNotNull { weekday(it.value) }.distinct().sorted().toList()
            if (m.groupValues[1].isBlank() && wds.size >= 2) { repeat = Triple("weekly", 1, wds); due = wds.map { nextDow(today, it) }.minOrNull() }
            else { days = wds.map { nextDow(today, it) to null }.sortedBy { it.first }.toList(); byWeekday = true }
        }
        if (days.isEmpty() && due == null) cut(Regex("${B}(\\d{1,2})((?:\\s*(?:,|и)\\s*\\d{1,2})+)\\s+$MON$E", ic)) { m ->
            val mon = month(m.groupValues[3]) ?: return@cut
            days = Regex("\\d{1,2}").findAll(m.value).mapNotNull { dateOf(today, it.value.toInt(), mon) }.distinct().sorted().map { it to null }.toList()
        }

        // One day.
        if (days.isEmpty()) {
            val endOfWeek = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
            val endOfMonth = today.with(TemporalAdjusters.lastDayOfMonth())
            fun within(n: Long, unit: String) = when { unit.startsWith("нед") -> today.plusWeeks(n); unit.startsWith("мес") -> today.plusMonths(n); else -> today.plusDays(n) }
            val dayRules: List<Pair<Regex, (MatchResult) -> Unit>> = listOf(
                // Windows, not their last day (owner, 13 окт): «до конца месяца» is today..the 31st.
                Regex("${B}(?:в\\s+течение|в\\s+ближайш$L*)\\s+(\\d+|$L+)?\\s*(дн$L*|день|недел$L*|месяц$L*)$E", ic) to { m ->
                    val n = m.groupValues[1].let { it.toIntOrNull() ?: numberWords[it.lowercase()] ?: 1 }.toLong()
                    start = today; due = within(n, m.groupValues[2].lowercase())
                },
                Regex("${B}(?:на\\s+этой\\s+неделе|(?:до|к)\\s+конц[ау]\\s+(?:этой\\s+)?недели|в\\s+течение\\s+(?:этой\\s+)?недели)$E", ic) to { _ -> start = today; due = endOfWeek },
                Regex("${B}в\\s+конце\\s+(?:этой\\s+)?недели$E", ic) to { _ -> start = maxOf(today, today.with(TemporalAdjusters.nextOrSame(DayOfWeek.FRIDAY)).let { if (it.isAfter(endOfWeek)) today else it }); due = endOfWeek },
                Regex("${B}(?:(?:до|к)\\s+конц[ау]\\s+(?:этого\\s+)?месяца|в\\s+этом\\s+месяце|в\\s+течение\\s+(?:этого\\s+)?месяца)$E", ic) to { _ -> start = today; due = endOfMonth },
                Regex("${B}в\\s+конце\\s+(?:этого\\s+)?месяца$E", ic) to { _ -> start = maxOf(today, endOfMonth.minusDays(6)); due = endOfMonth },
                Regex("${B}(?:в\\s+следующем\\s+месяце|в\\s+(?:течение\\s+)?следующего\\s+месяца)$E", ic) to { _ ->
                    val first = today.plusMonths(1).withDayOfMonth(1); start = first; due = first.with(TemporalAdjusters.lastDayOfMonth()) },
                Regex("${B}(?:(?:до|к)\\s+конц[ау]\\s+(?:этого\\s+)?года|в\\s+этом\\s+году)$E", ic) to { _ -> start = today; due = today.withDayOfYear(today.lengthOfYear()) },
                Regex("${B}(?:в|на)\\s+выходн$L*$E", ic) to { _ ->
                    val sat = if (today.dayOfWeek.value >= 6) today else nextDow(today, 6); start = sat; due = endOfWeek.let { if (it.isBefore(sat)) sat.plusDays(1) else it } },
                // «в ноябре» — the whole month (what is left of it, when it is this one).
                Regex("${B}в\\s+(январе|феврале|марте|апреле|мае|июне|июле|августе|сентябре|октябре|ноябре|декабре)$E", ic) to { m ->
                    month(m.groupValues[1])?.let { mo ->
                        var first = LocalDate.of(today.year, mo, 1); if (first.with(TemporalAdjusters.lastDayOfMonth()).isBefore(today)) first = first.plusYears(1)
                        start = maxOf(today, first); due = first.with(TemporalAdjusters.lastDayOfMonth())
                    } },
                Regex("${B}послезав(?:тр|р|т)а$E", ic) to { _ -> due = today.plusDays(2) },
                Regex("${B}завтра$E", ic) to { _ -> due = today.plusDays(1) },
                Regex("${B}сегодня$E", ic) to { _ -> due = today },
                Regex("${B}через\\s+(\\d+|$L+)?\\s*(дн$L*|день|недел$L*|месяц$L*)$E", ic) to { m ->
                    val n = m.groupValues[1].let { it.toIntOrNull() ?: numberWords[it.lowercase()] ?: 1 }
                    val u = m.groupValues[2].lowercase()
                    due = when { u.startsWith("нед") -> today.plusWeeks(n.toLong()); u.startsWith("мес") -> today.plusMonths(n.toLong()); else -> today.plusDays(n.toLong()) }
                },
                Regex("${B}(?:на\\s+следующей\\s+неделе)$E", ic) to { _ -> start = today.with(TemporalAdjusters.next(DayOfWeek.MONDAY)); due = start!!.plusDays(6) },
                // «в пятницу 16-го»: the number is exact, it wins over the weekday said with it.
                Regex("${B}(?:(?:в|во|на|к|до)\\s+)?$WD\\s*,?\\s+(\\d{1,2})-?(?:го|е)$E", ic) to { m -> due = dayOfMonth(today, m.groupValues[2].toInt()) },
                Regex("${B}(?:(в|во|на|к|до|по)\\s+)?((?:следующ$L*|след\\.?)\\s+)?$WD$E", ic) to { m -> weekday(m.groupValues[3])?.let {
                    due = nextDow(today, it, m.groupValues[2].isNotBlank()); byWeekday = true
                    // «до четверга» said on a Thursday is the next one: a deadline is never «today, by today».
                    if (due == today && m.groupValues[1].lowercase() in setOf("до", "к")) due = today.plusWeeks(1)
                } },
                Regex("${B}(\\d{1,2})(?:-?го)?\\s+$MON(?:\\s+(\\d{4}))?$E", ic) to { m -> month(m.groupValues[2])?.let { mo -> due = dateOf(today, m.groupValues[1].toInt(), mo) } },
                Regex("(?<![\\d:])(\\d{1,2})[./](\\d{1,2})(?:[./](\\d{2,4}))?(?![\\d:])", ic) to { m ->
                    val a = m.groupValues[1].toInt(); val b = m.groupValues[2].toInt()
                    if (b in 1..12 && !Regex("(?:^|\\s)(?:в|к|с)\\s*$").containsMatchIn(s.substring(0, m.range.first))) due = dateOf(today, a, b)
                },
                // An ordinal is a day of the month after «до/к» or before «числа», or standing alone — «второго этапа» is not a date.
                Regex("${B}(?:(?:до|к)\\s+$ORD(?:\\s+числ$L*)?|$ORD\\s+числ$L*|$ORD(?=\\s*(?:$|[,.;]|в\\s|к\\s|\\d)))$E", ic) to { m ->
                    (m.groupValues[1].ifBlank { m.groupValues[2] }.ifBlank { m.groupValues[3] }).let { ordinal(it) }?.let { due = dayOfMonth(today, it) } },
                Regex("${B}(?:до|к)\\s+(\\d{1,2})(?:-?(?:го|му|ому|ого)(?:\\s+числ$L*)?|\\s+числ$L*)$E", ic) to { m -> due = dayOfMonth(today, m.groupValues[1].toInt()) },
                Regex("${B}(\\d{1,2})-?го\\s+числа$E", ic) to { m -> due = dayOfMonth(today, m.groupValues[1].toInt()) },
            )
            for ((r, f) in dayRules) if (due == null && cut(r, f)) Unit
        } else due = null
        // A monthly repeat on a numbered day: «первого числа каждый месяц».
        if (repeat?.first == "monthly" && due == null) cut(Regex("${B}(?:$ORD|(\\d{1,2})-?(?:го)?)(?:\\s+числа)?$E", ic)) { m ->
            (ordinal(m.groupValues[1]) ?: m.groupValues[2].toIntOrNull())?.let { due = dayOfMonth(today, it) }
        }

        // The hour: «в 15», «в 15:30», «к 18.00», «в 9 утра», «в 7 вечера», «в полдень»; the part of the day otherwise.
        cut(Regex("${B}(?:в|к|с|до)?\\s*(\\d{1,2})[:.](\\d{2})(?![./]\\d)(?:\\s*(утра|дня|вечера|ночи))?", ic)) { m ->
            var h = m.groupValues[1].toInt(); val mi = m.groupValues[2].toInt()
            when (m.groupValues[3].lowercase()) { "вечера", "дня" -> if (h in 1..11) h += 12 }
            if (h in 0..23 && mi in 0..59) time = "%02d:%02d".format(h, mi)
        }
        // «через час», «через 2 часа», «через 30 минут»: from now, today.
        if (time == null) cut(Regex("${B}через\\s+(?:(\\d+|$L+)\\s+)?(час$L*|минут$L*|полчаса)$E", ic)) { m ->
            val n = m.groupValues[1].let { it.toIntOrNull() ?: numberWords[it.lowercase()] ?: 1 }
            val unit = m.groupValues[2].lowercase()
            val mins = when { unit.startsWith("полчас") -> 30L; unit.startsWith("мин") -> n.toLong(); else -> 60L * n }
            val at = LocalDateTime.of(today, now ?: LocalTime.now()).plusMinutes(mins)
            val r = at.withMinute(at.minute / 5 * 5)
            due = r.toLocalDate(); time = "%02d:%02d".format(r.hour, r.minute)
        }
        // «в 11 30»: hours and minutes said apart.
        if (time == null) cut(Regex("${B}(?:в|к)\\s+(\\d{1,2})\\s+([0-5]\\d)(?![\\d./:])", ic)) { m ->
            val h = m.groupValues[1].toInt(); if (h in 0..23) time = "%02d:%s".format(h, m.groupValues[2])
        }
        // «на 19» at the very end (a pizza for seven pm), never «на 5 человек».
        if (time == null) cut(Regex("${B}на\\s+(\\d{1,2})(?=\\s*$)", ic)) { m -> m.groupValues[1].toInt().takeIf { it in 7..23 }?.let { time = "%02d:00".format(it) } }
        if (time == null) cut(Regex("${B}(?:в|к|до)\\s+(\\d{1,2})(?:\\s*(?:час$L*|ч))?(?:\\s+(утра|дня|вечера|ночи))?(?![\\d./:]|\\s*$MON|\\s*-?го|\\s*-?му)$E", ic)) { m ->
            var h = m.groupValues[1].toInt()
            when (m.groupValues[2].lowercase()) { "вечера" -> if (h in 1..11) h += 12; "дня" -> if (h in 1..6) h += 12; "ночи" -> if (h == 12) h = 0 }
            if (h in 0..23) time = "%02d:00".format(h)
        }
        if (time == null) cut(Regex("${B}в\\s+полдень$E", ic)) { time = "12:00" }
        // «с днём рождения» is a wish, not the time of day.
        if (time == null && part == null) cut(Regex("${B}(утром|с утра|утречком|(?<!с\\s)днём|(?<!с\\s)днем|в обед|до обеда|после обеда|вечером|к вечеру|вечерком|ночью)$E", ic)) { m ->
            part = when (m.value.lowercase()) { "утром", "с утра", "утречком", "до обеда" -> "morning"; "после обеда" -> "afternoon"; "вечером", "к вечеру", "вечерком" -> "evening"; "ночью" -> "night"; else -> "day" }
        }
        if (days.isNotEmpty() && time != null) days = days.map { it.first to time }.also { time = null }
        fun passed(t: String?) = now != null && t != null && runCatching { !LocalTime.parse(t).isAfter(now) }.getOrDefault(false)

        if (time != null && due == null && start == null && days.isEmpty() && repeat == null) due = if (passed(time)) today.plusDays(1) else today

        // Said on Thursday at 23:00, «в четверг в 8» / «по пн и чт в 8» / «каждый день в 8» mean the next time, not this morning.
        val rep = repeat
        if (due == today && passed(time)) when {
            rep?.first == "daily" -> due = today.plusDays(1)
            rep?.first == "weekly" -> due = rep.third.ifEmpty { listOf(today.dayOfWeek.value) }
                .map { today.with(TemporalAdjusters.next(DayOfWeek.of(it))) }.min()
            rep == null && byWeekday -> due = today.plusWeeks(1)
        }
        if (byWeekday && days.any { it.first == today && passed(it.second) })
            days = days.map { if (it.first == today && passed(it.second)) it.first.plusWeeks(1) to it.second else it }.sortedBy { it.first }

        // An address: «на Ленина 5», «ул. Садовая, 12».
        var place: String? = null
        cut(Regex("${B}(?:на|ул\\.?|улиц$L*|по адресу)\\s+(\\p{Lu}$L+(?:\\s\\p{Lu}$L+)?)\\s*,?\\s*(\\d+$L?)$E")) { m -> place = "${m.groupValues[1]}, ${m.groupValues[2]}" }
        // In lowercase only a street-like word counts («профсоюзной», «ленина»), never «на неделе 2».
        if (place == null) cut(Regex("${B}(?:на|ул\\.?|улиц$L*|по адресу)\\s+($L+(?:ой|ая|ую|ого|ина|ына|ова|ева|ёва|ская|ской))\\s*,?\\s*(\\d+$L?)(?:\\s*(?:кв\\.?|квартира)\\s*(\\d+))?$E", ic)) { m ->
            place = m.groupValues[1].replaceFirstChar { it.uppercase() } + ", " + m.groupValues[2] + (m.groupValues[3].takeIf { it.isNotBlank() }?.let { ", кв. $it" } ?: "")
        }

        // Who: «ждём от Маши», «Дима пришлёт макет», «обещал Анне показать».
        var waitingFor: String? = null; var promisedTo: String? = null
        var titleOverride: String? = null
        val words = s.trim().split(Regex("\\s+"))
        cutIf(Regex("${B}(?:жд[уеё]м?|ожида$L*)\\s+(?:от\\s+)?(\\p{L}+)\\s+(.+)$", ic)) { m ->
            names.of(m.groupValues[1])?.let { n -> waitingFor = n; titleOverride = m.groupValues[2]; true } ?: false
        }
        // «жду ответа от Олега по смете»: what first, then from whom.
        if (waitingFor == null) cutIf(Regex("${B}(?:жд[уеё]м?|ожида$L*)\\s+(.+?)\\s+от\\s+(\\p{L}+)(.*)$", ic)) { m ->
            names.of(m.groupValues[2])?.let { n -> waitingFor = n; titleOverride = (m.groupValues[1] + m.groupValues[3]).trim(); true } ?: false
        }
        // «Дима (должен) пришлёт макет»: a person, then (a few words), then a verb of giving — not «заказать у Тимура».
        if (waitingFor == null) cutIf(Regex("${B}(\\p{L}+)\\s+(?:\\p{L}+\\s+){0,2}?(?:должн$L*\\s+|обещал$L*\\s+)?$waitVerbs\\s+(.+)$", ic)) { m ->
            names.subject(m.groupValues[1])?.let { n -> waitingFor = n; titleOverride = m.groupValues[3]; true } ?: false
        }
        // «она пришлёт правки до среды» after «созвон с Мариной»: the pronoun is the person just named — parse() fills it in.
        if (waitingFor == null) cutIf(Regex("^\\s*(?:он|она|они)\\s+(?:обещал$L*\\s+|должн$L*\\s+)?$waitVerbs\\s+(.+)$", ic)) { m ->
            waitingFor = PREV; titleOverride = m.groupValues[2]; true
        }
        // «ип петров пришлёт акты», «серёга обещал прислать КП» (persona test, 15 окт): a subject the dictionary doesn't know, at the
        // start of the words, before a verb of giving — still someone we wait for. The name as said, words capitalised, short ones
        // as initials («ИП Петров»). An infinitive alone («маме отправить») is not it: that needs «обещал» or «должен» before it.
        if (waitingFor == null) cutIf(Regex("^\\s*((?:\\p{L}+\\s+){0,2}?\\p{L}+)\\s+((?:обещал$L*|должн$L*)\\s+)?$waitVerbs\\s+(.+)$", ic)) { m ->
            val ws = m.groupValues[1].trim().split(Regex("\\s+"))
            val verb = m.groupValues[3].lowercase()
            val ok = ws.none { it.lowercase() in notSubjects || verbStart.matches(it) || names.of(it) != null || it.any { ch -> ch.isDigit() } } &&
                (m.groupValues[2].isNotBlank() || !verb.endsWith("ть"))
            if (!ok) false else {
                waitingFor = ws.joinToString(" ") { w -> if (w.length <= 3) w.uppercase() else w.replaceFirstChar { it.uppercaseChar() } }
                titleOverride = m.groupValues[4]; true
            }
        }
        // «Олег должен мне 5000»: he owes it — we wait for it.
        if (waitingFor == null) cutIf(Regex("${B}(\\p{L}+)\\s+(?:должн$L*|должен)\\s+мне\\s+(.+)$", ic)) { m ->
            names.subject(m.groupValues[1])?.let { n -> waitingFor = n; titleOverride = m.groupValues[2]; true } ?: false
        }
        // «Анна должна подписать акт»: someone must do something — we wait for it, the words stay as they are.
        var asSaid = false
        if (waitingFor == null) cutIf(Regex("${B}(\\p{L}+)\\s+должн$L*\\s+($L+(?:ть|ти))\\s+(.+)$", ic)) { m ->
            names.subject(m.groupValues[1])?.let { n -> waitingFor = n; titleOverride = m.groupValues[2] + " " + m.groupValues[3]; asSaid = true; true } ?: false
        }
        if (waitingFor == null) cutIf(Regex("${B}(?:я\\s+)?(?:обещал$L*|пообещал$L*|должн[аы]?|должен)\\s+(\\p{L}+)\\s+(.+)$", ic)) { m ->
            names.of(m.groupValues[1])?.let { n -> promisedTo = n; titleOverride = m.groupValues[2]; true } ?: false
        }
        val people = words.mapNotNull { names.of(it) }.distinct().filter { it != waitingFor && it != promisedTo }

        // The title: what's left, without fillers, starting with a capital; names capitalised.
        var title = (titleOverride ?: s).replace(Regex("\\s+"), " ").trim().trim(',', '.', '—', '–', '-', ':', ' ')
        title = title.replace(filler, "").trim().trim(',', '.', '—', '-', ':', ' ')
        // «Сдать 6-НДФЛ до [25 октября]», «отпуск с [1 ноября]»: the date is cut out, its preposition must go with it (persona test, 15 окт).
        repeat(2) { title = title.replace(danglingTail, "").trim() }
        if (waitingFor != null && waitingFor != PREV && !asSaid) title = "Получить ${title.midCase()} от ${waitingFor!!.genitive()}"
        if (title.isBlank()) title = chunk.trim()
        title = title.sentence().withNames(listOfNotNull(waitingFor, promisedTo) + people).properNames(names.contacts, listOfNotNull(waitingFor, promisedTo) + people)

        return Item(kind = kind, title = title, due = due, start = start, time = time, part = part, days = days, repeat = repeat,
            people = people, waitingFor = waitingFor, promisedTo = promisedTo, place = place, sphere = sphere)
    }

    // ---------------- into actions ----------------

    private fun o(vararg p: Pair<String, Any?>): JsonObject = JsonObject(p.filter { it.second != null }.associate { (k, v) ->
        k to when (v) {
            is JsonObject -> v
            is List<*> -> JsonArray(v.map { x -> if (x is JsonObject) x else JsonPrimitive(x.toString()) })
            else -> JsonPrimitive(v.toString())
        }
    })

    private fun Item.fields(): Array<Pair<String, Any?>> = arrayOf(
        "due" to due?.toString(), "start" to start?.toString(), "time" to time, "part" to part,
        "dates" to days.takeIf { it.size >= 2 }?.map { (d, t) -> o("date" to d.toString(), "time" to t) },
        "repeat" to repeat?.let { (f, i, ds) -> o("freq" to f, "interval" to i, "days" to ds.takeIf { it.isNotEmpty() }) },
        "people" to people.takeIf { it.isNotEmpty() }, "waiting_for" to waitingFor, "promised_to" to promisedTo, "place" to place, "sphere" to sphere,
    )

    /** The same actions the AI would send, for [scope]; inside a topic words are its record and dated ones also its matter. */
    fun plan(r: Result, scope: Scope, asQuestion: Boolean = false, source: String = ""): AiPlan {
        val actions = when (scope) {
            is Scope.InTask -> {
                val deals = r.items.filter { asQuestion || it.kind == "task" && (it.due != null || it.time != null || it.days.isNotEmpty() || it.waitingFor != null) }
                listOfNotNull(
                    if (asQuestion) null else o("type" to "add_entry", "task_id" to scope.taskId, "text" to source.ifBlank { r.items.joinToString(". ") { it.title } }),
                    deals.takeIf { it.isNotEmpty() }?.let { ds ->
                        o("type" to "add_subtasks", "task_id" to scope.taskId, "subtasks" to ds.map { i ->
                            o("title" to (if (i.waitingFor != null) i.title.removePrefix("Получить ").substringBeforeLast(" от ").sentence() else i.title),
                                "due" to (i.due ?: i.days.firstOrNull()?.first)?.toString(), "time" to (i.time ?: i.days.firstOrNull()?.second), "from" to i.waitingFor,
                                "dates" to i.days.takeIf { it.size >= 2 }?.map { (d, t) -> o("date" to d.toString(), "time" to t) })
                        })
                    },
                )
            }
            else -> r.items.map { i ->
                if (i.kind == "idea") o("type" to "create_note", "text" to i.title)
                else o("type" to "create_task", "title" to i.title, "topic" to (if (i.kind == "topic") "true" else null), *i.fields())
            }
        }
        return AiPlan(null, actions, null, local = true)
    }

    /** One quiet line for the preview under the field: «дело · завтра 15:00 · Маша · работа». */
    fun describe(r: Result, today: LocalDate = LocalDate.now()): String? {
        val i = r.items.firstOrNull() ?: return null
        val rus = ru.anjeib.margin.i18n.L.lang == ru.anjeib.margin.i18n.Lang.Ru
        fun day(d: LocalDate) = when (d) {
            today -> if (rus) "сегодня" else "today"
            today.plusDays(1) -> if (rus) "завтра" else "tomorrow"
            else -> d.dowShort().lowercase() + " " + d.dayMonthShort()
        }
        val parts = listOfNotNull(
            if (r.items.size > 1) (if (rus) "${r.items.size} дела" else "${r.items.size} to-dos") else when (i.kind) { "idea" -> if (rus) "идея" else "idea"; "topic" -> if (rus) "тема" else "topic"; else -> if (rus) "дело" else "to-do" },
            i.repeat?.let { (f, n, ds) -> if (rus) "повтор " + when {
                f == "daily" && n > 1 -> "раз в $n дн."
                f == "daily" -> "каждый день"; f == "monthly" -> "каждый месяц"; f == "yearly" -> "каждый год"
                else -> (if (n > 1) "раз в $n нед., " else "") + ds.joinToString(", ") { LocalDate.of(2024, 1, it).dowShort().lowercase() }
            } else "repeats" },
            i.days.takeIf { it.isNotEmpty() }?.joinToString(", ") { day(it.first) + (it.second?.let { t -> " $t" } ?: "") },
            listOfNotNull(i.due?.let { d -> i.start?.takeIf { it.isBefore(d) }?.let { "${day(it)} – ${day(d)}" } ?: day(d) }, i.time).joinToString(" ").ifBlank { null },
            i.part?.let { if (rus) mapOf("morning" to "утром", "day" to "днём", "afternoon" to "после обеда", "evening" to "вечером", "night" to "ночью")[it] else it },
            i.waitingFor?.let { if (rus) "ждём от ${it.genitive()}" else "from $it" },
            i.promisedTo?.let { if (rus) "обещано ${it.dative()}" else "promised to $it" },
            i.people.takeIf { it.isNotEmpty() }?.joinToString(", "),
            i.place,
            i.sphere?.let { if (rus) (if (it == "work") "работа" else "личное") else it },
        )
        return parts.joinToString("  ·  ")
    }

    /**
     * Names written as they are: a contact's own spelling («жэк», «Мтс» → «ЖЭК», «МТС»), and a surname after a known first
     * name («с Иваном петровым» → «с Иваном Петровым») — dictation and quick typing leave them small.
     */
    /**
     * The word that says what it is, at the start: «Идея. Блог о дизайне» → ("idea", "Блог о дизайне"), «дело купить хлеб» →
     * ("task", "купить хлеб"); (null, text) when there is none. For the marks shown after dictation (owner, 13 окт).
     */
    fun lead(text: String): Pair<String?, String> {
        val (m, rest) = marks(text)
        return m.firstOrNull { it == "idea" || it == "task" }?.let { it to rest } ?: (null to text)
    }

    // «/идея», «слеш идея», «в идеи», «в заметки», «идее» (a slip of dictation for «идеи»), «на заметку» — an idea.
    private const val SAID_SLASH = "(?:/\\s*|сл[еэ]ш\\p{L}*\\s+|флеш\\s+)?"
    private val leadIdea = Regex("^\\s*$SAID_SLASH(?:(?:в|во|к)\\s+)?(?:иде[яюиейё]\\p{L}{0,2}|и\\s+дея|мысл[ьи]|заметк\\p{L}*|на\\s+заметку)(?![\\p{L}\\d])\\s*[:—–,.!-]?\\s*", RegexOption.IGNORE_CASE)
    // «/дело», «в дела», «в задачи», «в список дел» — a matter; «дело в том…» is not.
    private val leadTask = Regex("^\\s*$SAID_SLASH(?:(?:в|во)\\s+(?:дела|задачи|список\\s+дел)|(?:дело|задача)(?!\\s+в\\s+том))(?![\\p{L}\\d])\\s*[:—–,.!-]?\\s*", RegexOption.IGNORE_CASE)
    // «по работе», «работа:», «/работа»; «работа над сайтом» stays words — bare «работа» needs a stop after it.
    private val leadWork = Regex("^\\s*(?:$SAID_SLASH(?:по\\s+работе|для\\s+работы)(?![\\p{L}\\d])\\s*[:—–,.!-]?|(?:/\\s*|сл[еэ]ш\\p{L}*\\s+)работа(?![\\p{L}\\d])\\s*[:—–,.!-]?|(?:работа|рабочее)\\s*[:—–.!])\\s*", RegexOption.IGNORE_CASE)
    private val leadPersonal = Regex("^\\s*$SAID_SLASH(?:личное|лично|для\\s+себя|по\\s+личному)(?![\\p{L}\\d])\\s*[:—–,.!-]?\\s*", RegexOption.IGNORE_CASE)

    /**
     * Every word at the start that says what it is or where it belongs, typed or dictated however it came out (owner, 13 окт):
     * «В идеи посмотреть кино» → (["idea"], "посмотреть кино"); «по работе, дело: отчёт» → (["work", "task"], "отчёт").
     * One of each; nothing taken when nothing is left after them.
     */
    fun marks(text: String): Pair<List<String>, String> {
        var t = text.trim().replace(Regex("^\\s*(идея|мысль|заметка|дело|задача)\\s*[.!]\\s+", RegexOption.IGNORE_CASE), "$1: ")
        val got = mutableListOf<String>()
        repeat(3) {
            val hit = listOf("idea" to leadIdea, "task" to leadTask, "work" to leadWork, "personal" to leadPersonal)
                .filter { (k, _) -> k !in got && !(k in listOf("idea", "task") && got.any { it == "idea" || it == "task" }) && !(k in listOf("work", "personal") && got.any { it == "work" || it == "personal" }) }
                .firstNotNullOfOrNull { (k, r) -> r.find(t)?.takeIf { it.value.isNotBlank() }?.let { k to it } } ?: return@repeat
            val rest = t.substring(hit.second.range.last + 1).trim()
            if (rest.isBlank()) return@repeat
            got += hit.first; t = rest
        }
        return if (got.isEmpty()) emptyList<String>() to text else got to t
    }

    internal fun String.properNames(contacts: List<String>, people: List<String>): String {
        var t = this
        contacts.flatMap { it.split(Regex("\\s+")) }.filter { w -> w.length >= 2 && w.drop(1).any { it.isUpperCase() } }.distinct().forEach { w ->
            t = Regex("(?<![\\p{L}\\d])" + Regex.escape(w) + "(?![\\p{L}\\d])", RegexOption.IGNORE_CASE).replace(t, w)
        }
        val firsts = people.map { it.substringBefore(' ').lowercase() }.filter { it.length >= 3 }.map { it.take((it.length - 1).coerceAtLeast(3)) }
        if (firsts.isEmpty()) return t
        return Regex("(\\p{Lu}\\p{Ll}+)(\\s+)(\\p{Ll}+(?:ов|ев|ёв|ин|ын|ск|цк)\\p{Ll}{0,3})(?![\\p{L}\\d])").replace(t) { m ->
            if (firsts.any { m.groupValues[1].lowercase().startsWith(it) }) m.groupValues[1] + m.groupValues[2] + m.groupValues[3].replaceFirstChar { it.uppercaseChar() } else m.value
        }
    }
}
