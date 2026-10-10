package ru.anjeib.margin.book

import ru.anjeib.margin.ai.LocalParser
import ru.anjeib.margin.data.dative
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

    private val someday = Regex("^\\s*(?:когда[- ]нибудь|потом|при случае|как[- ]нибудь|later|someday|some day|eventually)\\s*[:,—-]?\\s*", RegexOption.IGNORE_CASE)
    private val eventCue = Regex("^\\s*(?:встреча|созвон|при[её]м|собеседование|звонок с|концерт|спектакль|день рождения|свадьба|вебинар|лекция|урок|тренировка|" +
        "приедет|приедут|придёт|придет|заедет|зайдёт|зайдет|визит|замер|" +
        "meeting|call with|appointment|interview|lunch with|dinner with|concert|webinar|lecture|lesson|class|training|birthday|wedding)(?!\\p{L})", RegexOption.IGNORE_CASE)
    private val noteCue = Regex("^\\s*(?:запись|заметка|мысль|идея|на заметку|note|idea|thought)\\s*[:—–-]\\s*", RegexOption.IGNORE_CASE)
    private val threadTag = Regex("(?:^|\\s)#([\\p{L}\\d][\\p{L}\\d_-]*)")
    /** Someone comes at an hour: a meeting, wherever the words put it («в 10 приедет Петров», «Саня в чт на замер»). */
    private val visit = Regex("(?<!\\p{L})(?:приедет|приедут|придёт|придет|заедет|зайдёт|зайдет|на замер|на встречу|созвон|" +
        // A meeting by what it is or where one goes: a school meeting, a doctor, a hairdresser.
        "собрание|совещание|педсовет|планёрка|планерка|линейка|приём у|прием у|" +
        "к (?:стоматологу|зубному|врачу|терапевту|педиатру|окулисту|ортодонту|лору|неврологу|гинекологу|парикмахеру|барберу|мастеру|нотариусу|юристу)|" +
        "в (?:поликлинику|больницу|театр|кино|консерваторию))(?!\\p{L})", RegexOption.IGNORE_CASE)
    /** «…, Ленина 5» at the end: a place, not words of the line. */
    private val street = Regex(",\\s*((?:ул\\.?\\s*)?[А-ЯЁ][а-яё]+(?:\\s+[А-ЯЁ]?[а-яё]+)?\\s*,?\\s*\\d+[а-яё]?(?:\\s*/\\s*\\d+)?)\\s*$")

    /** «до пятницы», «к среде», «до 12-го»: a deadline — the line stands on the page from now until that day. */
    private val deadline = Regex("(?<!\\p{L})(?:до|к|ко)\\s+(?:понедельник\\p{L}*|вторник\\p{L}*|сред\\p{L}*|четверг\\p{L}*|пятниц\\p{L}*|суббот\\p{L}*|воскресень\\p{L}*|" +
        "пн|вт|ср|чт|пт|сб|вс|послезавтр\\p{L}*|конц\\p{L}*\\s+(?:месяца|недели|года)|\\d{1,2}(?:-?го|-?е)?(?:\\s+\\p{L}+)?)(?!\\p{L})", RegexOption.IGNORE_CASE)

    /**
     * A surname inside the words («приедет Петров», «созвонились с Петровым», «Иванов должен…»): a person, even one the
     * notebook has not met yet. Not after «на / в / по / ул.» and not before a house number — that is a street.
     */
    private val surname = Regex("(?<![\\p{L}.])(?<!(?:на|в|во|по|ул|улица|проспект|пр)\\s)([А-ЯЁ][а-яё]{2,}(?:ов|ев|ёв|ин|ын|ский|ская|цкий|цкая|ич|енко|ко|ук|юк|ян)" +
        "(?:а|у|ым|ом|е|ой|ую|ого|ому|им)?)(?!\\p{L})(?!\\s*,?\\s*\\d)")
    /** «Иванов должен…», «Петров пришлёт…»: the one who owes. */
    private val owes = Regex("^\\s*(?:должен|должна|должны|обещал\\p{L}*|пришл[её]т|пришлют|верн[её]т|отдаст|заплатит|переведёт|переведет|привез[её]т|принес[её]т|сделает|подготовит|скинет|отправит)(?!\\p{L})",
        RegexOption.IGNORE_CASE)

    /** «Петровым» → «Петров», «Ивановой» → «Иванова»: the name as it stands in the index. */
    private fun nominative(w: String): String {
        Regex("^(.+(?:ов|ев|ёв|ин|ын))(ой|ую)$").find(w)?.let { return it.groupValues[1] + "а" }
        Regex("^(.+(?:ов|ев|ёв|ин|ын))(ым|ом|у|а|е)$").find(w)?.let { return it.groupValues[1] }
        Regex("^(.+)(ского|скому|ским)$").find(w)?.let { return it.groupValues[1] + "ский" }
        return w
    }
    /** Giving something to someone: «прислать бриф Диме», «вернуть книгу Маше» — what you owe them (→). */
    private val giving = Regex("(?<!\\p{L})(?:присл|пришл|отправ|скин|показ|покаж|перед|верн|отда|подар|принес|принёс|привез|привёз|занес|занёс|заплат|переве)\\p{L}*" +
        "(?:ть|ти|ю|у|ешь|ет|ем|ете|ут|ют|ит|им|ите|ат|ят|л|ла|ли|ло|й|йте)(?!\\p{L})", RegexOption.IGNORE_CASE)

    /** What a phrase will become — for the live margin next to the field and for writing it down. */
    data class Draft(val lines: List<Line>, val sure: Boolean, val question: Boolean, val threadTitle: String?,
                     /** A list written at once: its items, under the first line. */
                     val under: List<Line> = emptyList())

    /**
     * Reads [text] written on the page of [page]. Lines without a day of their own land on that page;
     * «потом …» — on «Потом»; «#Зерно» puts them in the thread.
     */
    fun draft(text: String, book: Book, page: LocalDate, today: LocalDate = LocalDate.now(), now: LocalTime? = LocalTime.now().takeIf { page == today },
              threadId: String? = null,
              /** Written on «Потом»: lines without a day of their own get none. */
              noDay: Boolean = false, lists: Boolean = true): Draft {
        var t = text.trim()
        if (t.isEmpty()) return Draft(emptyList(), true, false, null)
        if (findCue.containsMatchIn(t) && !t.contains('\n') && t.length < 80) return Draft(emptyList(), true, true, null)
        val tag = threadTag.find(t)?.groupValues?.get(1)
        if (tag != null) t = t.replace(threadTag, " ").trim()
        val later = someday.containsMatchIn(t)
        if (later) t = t.replace(someday, "")
        val note = noteCue.containsMatchIn(t)
        if (note) t = t.replace(noteCue, "")
        // A list written at once — one line with its items to tick, «0 из 4» beside it.
        if (lists && !note) listOf(t).firstNotNullOfOrNull { list(it) }?.let { (head, items) ->
            val first = draft(head, book, page, today, now, threadId, noDay, lists = false).lines.firstOrNull()
            val parent = (first ?: Line(text = head.replaceFirstChar { it.uppercaseChar() }, day = if (noDay) null else page.toString(), threadId = threadId))
                .copy(kind = Kind.Todo, threadId = first?.threadId ?: threadId)
            val kids = items.map { Line(text = it.replaceFirstChar { c -> c.uppercaseChar() }, kind = Kind.Todo, parentId = parent.id, threadId = parent.threadId) }
            return Draft(listOf(parent), true, false, tag, under = kids)
        }
        // A long note stays whole: one line with its paragraphs; no day or name is taken out of its words.
        if (isLongNote(t)) {
            // People by their own word forms: «маме» is Мама, «маминой сестре» is not.
            val people = book.persons.filter { p -> (listOf(p.name) + p.aliases).any { formOf(t, it) } }.map { Who(it.name, Dir.With) }
            val body = t.lines().joinToString("\n") { it.trim() }.replace(Regex("\n{3,}"), "\n\n").trim().replaceFirstChar { it.uppercaseChar() }
            val day = if (later || noDay || (threadId != null && tag == null)) null else page.toString()
            val named = if (tag == null && threadId == null) threadNamed(t, book) else null
            return Draft(listOf(Line(text = body, kind = Kind.Note, who = people.take(3), day = day, threadId = threadId ?: named)), true, false, tag)
        }
        val known = book.persons.map { it.name to it.aliases }
        val r = if (English.isEnglish(t)) English.parse(t, known, today = page, now = now)
            else LocalParser.parse(t, known, today = page, now = now, keepWords = true)
        if (r.why == "question") return Draft(emptyList(), false, true, tag)
        val lines = r.items.flatMap { i ->
            val kind = when {
                note || i.kind == "idea" -> Kind.Note
                (eventCue.containsMatchIn(i.title) || visit.containsMatchIn(i.title)) && (i.time != null || i.due != null) -> Kind.Event
                else -> Kind.Todo
            }
            // A name in the dative after a verb of giving is someone you owe: «прислать бриф Диме» → «→ Диме», not «с Димой».
            val owed = if (giving.containsMatchIn(i.title)) i.people.filter { n -> said(text, n.dative()) && !together(text, n) } else emptyList()
            val who0 = listOfNotNull(i.promisedTo?.let { Who(it, Dir.To) }, i.waitingFor?.let { Who(it, Dir.From) }) +
                owed.filter { it != i.promisedTo }.map { Who(it, Dir.To) } + (i.people - owed.toSet()).map { Who(it, Dir.With) }
            // Surnames the parser does not know: a person; «Иванов должен…» — the one you wait for.
            val known0 = who0.map { it.name.lowercase().take(4) }
            val found = surname.findAll(i.title).filter { m -> m.range.first > 0 || owes.containsMatchIn(i.title.substring(m.range.last + 1)) }.mapNotNull { m ->
                val n = book.person(nominative(m.value))?.name ?: nominative(m.value)
                if (n.lowercase().take(4) in known0) null
                else Who(n, if (owes.containsMatchIn(i.title.substring(m.range.last + 1))) Dir.From else Dir.With)
            }.distinctBy { it.name }.toList()
            val who = withPatronymic(who0 + found, i.title)
            val repeat = i.repeat?.let { (f, n, d) -> Repeat(f, n, d) }
            // «…, Ленина 5» at the end is where: it goes to the margin.
            val at = if (i.place == null) street.find(i.title) else null
            val words = calm(at?.let { i.title.removeRange(it.range).trim() } ?: i.title, text)
            val base = Line(text = words, kind = kind, who = who, place = i.place ?: at?.groupValues?.get(1)?.trim(), repeat = repeat, part = i.part,
                time = i.time, source = text.trim(), threadId = threadId)
            when {
                // «в пн и чт» without «каждый» — one line on each of those pages.
                i.days.size >= 2 -> i.days.map { (d, h) -> base.copy(id = newId(), day = d.toString(), time = h ?: i.time) }
                (later || noDay) && i.due == null && i.start == null && i.time == null -> listOf(base.copy(day = null))
                // A thread's line written without a day stays in the thread — on its page, not on a day's.
                threadId != null && tag == null && i.due == null && i.start == null && i.time == null && repeat == null -> listOf(base.copy(day = null))
                i.start != null && i.due != null && i.start != i.due -> listOf(base.copy(day = i.start.toString(), until = i.due.toString()))
                // «до пятницы»: from the page it was written on until that day — seen every day, not only on Friday.
                i.due != null && i.start == null && i.time == null && repeat == null && i.due.isAfter(page) && deadline.containsMatchIn(text) ->
                    listOf(base.copy(day = page.toString(), until = i.due.toString()))
                else -> listOf(base.copy(day = (i.due ?: i.start ?: page).toString()))
            }
        }
        // The words name an open thread («созвон с самокатом» — thread «самокат»): the lines go into it.
        val named = if (tag == null && threadId == null) threadNamed(t, book) else null
        return Draft(if (named != null) lines.map { it.copy(threadId = named) } else lines, r.sure, false, tag)
    }

    /**
     * An open thread the words name: its name in quotes («Зерно» in «Кофейня «Зерно»»), or every word of a short name
     * («самокат» in «созвон с самокатом», any ending). Names shorter than five letters are too much like ordinary words.
     */
    fun threadNamed(text: String, book: Book): String? =
        book.threads.filter { it.closedAt == null }.firstOrNull { names(text, it.title) }?.id

    /** The words name a thread by its title (see [threadNamed]). */
    fun names(text: String, title: String): Boolean {
        val words = Regex("\\p{L}+").findAll(text.lowercase().replace('ё', 'е')).map { it.value }.toList()
        fun has(w: String): Boolean {
            val n = w.lowercase().replace('ё', 'е')
            if (n.length < 5) return false
            val stem = n.take(maxOf(4, n.length - 2))
            return words.any { it.startsWith(stem) }
        }
        val quoted = Regex("«([^»]+)»").find(title)?.groupValues?.get(1)
        return if (quoted != null) Regex("\\p{L}+").findAll(quoted).map { it.value }.toList().let { q -> q.isNotEmpty() && q.all(::has) }
        else Regex("\\p{L}+").findAll(title).map { it.value }.toList().let { t -> t.size in 1..2 && t.all(::has) }
    }

    // «найди лендинг», «где бриф», «покажи файлы Олега» — asking Margin to look; «найти репетитора» is a thing to do.
    private val findCue = Regex("(?iu)^\\s*(?:найди|поищи|покажи|где|find|search|show me|where(?:'s| is)?)(?!\\p{L})")

    private val patronymicName = Regex("([А-ЯЁ][а-яё]+)\\s+([А-ЯЁ][а-яё]+(?:ович|евич|ович[ау]|евич[ау]|овн[аеы]|евн[аеы]|ичн[аеы]|ич))(?!\\p{L})")

    /**
     * «Николай Иванович», «Ирина Петровна» are one person, not a name and a surname: the name takes its patronymic
     * (in its first form — «Ирине Петровне» → «Ирина Петровна»), and the direction of either goes to both.
     */
    private fun withPatronymic(who: List<Who>, text: String): List<Who> {
        var out = who
        patronymicName.findAll(text).forEach { m ->
            val first = m.groupValues[1]; val pat = m.groupValues[2]
            val patN = pat.replace(Regex("ович[ау]$"), "ович").replace(Regex("евич[ау]$"), "евич")
                .replace(Regex("овн[еы]$"), "овна").replace(Regex("евн[еы]$"), "евна").replace(Regex("ичн[еы]$"), "ична")
            val a = out.firstOrNull { it.name.lowercase().take(3) == first.lowercase().take(3) } ?: return@forEach
            val b = out.firstOrNull { it !== a && it.name.lowercase().take(4) == patN.lowercase().take(4) }
            val dir = listOfNotNull(a.dir, b?.dir).firstOrNull { it != Dir.With } ?: a.dir
            val full = Who(a.name.split(' ').first() + " " + patN, dir)
            out = out.filter { it !== a && it !== b }.toMutableList().also { it.add(out.indexOf(a).coerceAtMost(it.size), full) }
        }
        return out.distinctBy { it.name }
    }

    /** A form of the name itself (any case), not a word made from it: «маме», «мамой» — yes; «маминой» — no. */
    private fun formOf(text: String, name: String): Boolean {
        val n = name.split(' ').first().lowercase().replace('ё', 'е')
        if (n.length < 3) return false
        val stem = n.take(maxOf(3, n.length - 1))
        return Regex("\\p{L}+").findAll(text.lowercase().replace('ё', 'е')).any { w -> w.value.startsWith(stem) && w.value.length <= n.length + 2 }
    }

    /** The words hold this form of a word (case and «ё» aside). */
    private fun said(text: String, form: String): Boolean {
        val f = form.lowercase().replace('ё', 'е').trim()
        if (f.isEmpty()) return false
        return Regex("(?<!\\p{L})" + Regex.escape(f) + "(?!\\p{L})").containsMatchIn(text.lowercase().replace('ё', 'е'))
    }

    /** «с Олегом», «со Славой»: together with them, whatever else the words say. */
    private fun together(text: String, name: String): Boolean {
        val stem = name.lowercase().replace('ё', 'е').take(3)
        return stem.length == 3 && Regex("(?<!\\p{L})(?:с|со)\\s+" + Regex.escape(stem)).containsMatchIn(text.lowercase().replace('ё', 'е'))
    }

    private val stepCue = Regex("^\\s*(?:\\p{L}+(?:ть|ти|чь|ться|тись)|позвон\\p{L}*|напиш\\p{L}*|купи|сходи|сделай|отправь|забери|заплати|оплати|закажи|запишись|проверь|найди|уточни|спроси|скинь|пришли|подготовь|договорись)(?!\\p{L})",
        RegexOption.IGNORE_CASE)

    /** Words under a line: a step if they begin with what to do («позвонить», «купи»), otherwise a comment. */
    fun isStep(text: String): Boolean = stepCue.containsMatchIn(text)

    private val bullet = Regex("^\\s*(?:[-–—•*·]|\\d{1,2}[.)])\\s+")

    // A verb of doing anywhere in a clause: an infinitive («позвонить», «забрать»; not «часть», «радость», «может быть») or a request.
    private val doingWord = Regex("(?iu)(?<!\\p{L})(?!быть(?!\\p{L}))(?:\\p{L}{2,}(?<!с)(?:ть|ти|чь|ться|тись)|купи|сходи|сделай|отправь|забери|заплати|оплати|закажи|запишись|проверь|найди|уточни|спроси|скинь|пришли|подготовь|договорись|позвони|напиши)(?!\\p{L})")
    // Dictation has no stops: a thought also breaks before «надо», «может», «хорошо бы», «но» («…в лагерь надо посчитать
    // бюджет и узнать у Лизы… может совместить…»).
    private val clauseBreak = Regex("(?iu)[.!?;…\\n]+|,\\s+|\\s+(?:и|а|а ещё|а также|потом|затем)\\s+|\\s+(?=(?:надо|нужно|не забыть|может быть|может|можно|хорошо бы|но|зато)\\s)")
    private val clauseLead = Regex("(?iu)^(?:(?:и|а|но|ещё|еще|потом|затем|также|не забыть|надо|нужно|не забудь|хорошо бы|думала про \\p{L}+|думал про \\p{L}+)\\s+)+")
    private fun clauses(t: String) = t.split(clauseBreak).map { it.trim() }.filter { it.length >= 3 }

    /**
     * A long note: written to keep, not a list of things to do — a meeting's notes, a thought, a brief. It stays one line
     * (–) with its paragraphs. Long: 200 letters and more, or 120 and more in several lines or sentences. Not when it is
     * a list (every line a bullet, or short lines that each say what to do) or mostly things to do («завтра позвонить
     * Олегу, потом заехать в типографию, оплатить интернет…» — those stay to-dos).
     */
    fun isLongNote(text: String): Boolean {
        val t = text.trim()
        val rows = t.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
        if (rows.size >= 2 && rows.all { bullet.containsMatchIn(it) }) return false
        if (rows.size >= 2 && rows.all { it.length <= 60 && isStep(it.replace(bullet, "")) }) return false
        val sentences = Regex("[.!?…](?:\\s|$)").findAll(t).count() + 1
        // A thought said aloud («думала про каникулы…») is kept whole from a hundred letters on — dictation has no full stops.
        if (t.length >= 100 && thinking.containsMatchIn(t)) return true
        if (!(t.length >= 200 || (t.length >= 120 && (rows.size >= 2 || sentences >= 3)))) return false
        // Notes under a heading («Совещание с завучами 13.10⏎…») are kept whole whatever is to be done in them — the things
        // to do are offered as its steps, nothing is lost.
        if (rows.size >= 3 && rows[0].length <= 60 && !doingWord.containsMatchIn(rows[0]) && !bullet.containsMatchIn(rows[0])) return true
        val cl = clauses(t)
        return cl.isEmpty() || cl.count { doingWord.containsMatchIn(it) } * 10 <= cl.size * 6
    }

    private val thinking = Regex("(?iu)^\\s*(?:думал[аи]?|думаю|подумал[аи]?|мысли|мысль|хорошо бы|интересно|кажется|вспомнил[аи]?|размышля\\p{L}*|идея)(?!\\p{L})")
    // Someone else's promise inside a note — a thing to wait for, offered as a step too.
    private val theirs = Regex("(?iu)(?<!\\p{L})(?:обещал[аи]?|пришл[её]т|пришлют|отдаст|отдадут|вернёт|вернут|скинет|отправит|сделает|подготовит)(?!\\p{L})")

    /**
     * A list written at once: a heading with a colon and its items on the lines below («список в магазин:⏎хлеб⏎молоко»),
     * items after a colon («купить: хлеб, молоко, сыр»), or what to do with three short things or more («купить маме
     * таблетки, хлеб, молоко и корм коту» — the line stays as written, its items under it).
     */
    private fun list(t: String): Pair<String, List<String>>? {
        val rows = t.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
        // Items are short and plain: a message («Тимур, привет! …», «Сможешь до понедельника?») is not a list.
        if (rows.size >= 3 && (rows[0].endsWith(':') || rows.drop(1).all { bullet.containsMatchIn(it) } && !bullet.containsMatchIn(rows[0])) &&
            rows.drop(1).all { it.length <= 50 && !it.contains('?') && !it.contains('!') && it.count { c -> c == ',' } <= 1 })
            return rows[0].trimEnd(':').trim() to rows.drop(1).map { it.replace(bullet, "").trim() }
        if (rows.size != 1) return null
        val one = rows[0]
        val colon = one.indexOf(':')
        if (colon in 2 until one.length - 1) {
            val items = enumeration(one.substring(colon + 1))
            if (items.size >= 2 && items.all { it.split(' ').size <= 4 }) return one.substring(0, colon).trim() to items
        }
        val parts = enumeration(one)
        if (parts.size >= 3 && isStep(parts[0]) && parts.drop(1).all { it.split(' ').size <= 3 && !doingWord.containsMatchIn(it) }) {
            val first = parts[0].split(Regex("\\s+")).drop(1).joinToString(" ")
            if (first.isNotBlank()) return one to (listOf(first) + parts.drop(1))
        }
        return null
    }

    /** Things to do inside a long note («…взять палатку, проверить билеты, спросить Машу») — offered to become its steps. */
    fun stepsIn(text: String): List<String> {
        // A list inside the note («1) крупнее фото…», «- кнопку поменять…») is a list of things to do, verb or not.
        val listed = text.lines().filter { bullet.containsMatchIn(it) }.map { it.replace(bullet, "").trim().trimEnd('.', ';', ',') }
            .filter { it.length in 3..120 }
        val rest = text.lines().filterNot { bullet.containsMatchIn(it) }.joinToString("\n")
        val said = clauses(rest).map { it.replace(clauseLead, "").trim() }.filter { it.length in 4..110 && (isStep(it) || theirs.containsMatchIn(it)) }
        return (listed + said).map { it.replaceFirstChar { c -> c.uppercaseChar() } }.distinct().take(8)
    }

    /**
     * Words written under a line as the items they are: a line each («молоко⏎хлеб»), or a list after a colon
     * («купить: молоко, хлеб, яйца»), bullets and numbers off. One item stays one, commas and all.
     */
    fun items(text: String): List<String> {
        // A long note written under a line stays one, paragraphs and all.
        if (isLongNote(text)) return listOf(text.trim())
        val rows = text.split('\n').map { it.replace(bullet, "").trim() }.filter { it.isNotEmpty() }
        if (rows.size != 1) return rows
        val one = rows.single()
        val colon = one.indexOf(':')
        if (colon in 1 until one.length - 1) {
            val parts = enumeration(one.substring(colon + 1))
            if (parts.size >= 2) return parts
        }
        // Without a colon only a plain enumeration is a list: three short things or more («молоко, хлеб и яйца»).
        val parts = enumeration(one)
        if (parts.size >= 3 && parts.all { it.split(' ').size <= 3 } && !told.containsMatchIn(one)) return parts
        return rows
    }

    // What was done or felt is told, not listed: «прочитал, понравилось, но дорого» stays one comment.
    private val told = Regex("(?iu)(?:^|[\\s,])(?:но|а|зато|хотя|потому)\\s|\\p{L}[аеиоуыя](?:л|ли|лся|лись|лось)(?=[\\s,.;]|$)")

    /** «молоко, хлеб и яйца» → молоко · хлеб · яйца: commas, semicolons and the last «и / and». */
    private fun enumeration(text: String): List<String> {
        val parts = text.trim().removeSuffix(".").split(Regex("[,;]")).map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
        if (parts.size >= 2) {
            val last = parts.removeAt(parts.size - 1)
            parts += last.split(Regex("\\s+(?:и|and)\\s+", RegexOption.IGNORE_CASE)).map { it.trim() }.filter { it.isNotEmpty() }
        }
        return parts
    }

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
