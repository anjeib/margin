package ru.anjeib.margin

import org.junit.Assert.assertTrue
import org.junit.Test
import ru.anjeib.margin.ai.LocalParser
import ru.anjeib.margin.data.AppData
import ru.anjeib.margin.data.Person
import java.time.LocalDate
import java.time.LocalTime

/**
 * An independent measurement of [LocalParser] — phrases written apart from ParseCorpusTest, expectations from what the
 * words mean (as a careful human assistant would read them), not from what the regexes handle. Never tune the parser
 * against this file: it is a holdout. Base: Thursday 8 Oct 2026, 10:00. It prints a score and never fails on a low one.
 */
class ParseHoldoutTest {

    private data class Rep(val freq: String, val wds: List<Int>? = emptyList(), val every: Int = 1)

    private data class M(
        val due: LocalDate? = null, val time: String? = null, val part: String? = null, val rep: Rep? = null,
        val days: List<LocalDate> = emptyList(), val wait: String? = null, val promise: String? = null,
        val who: List<String> = emptyList(), val idea: Boolean = false, val place: String? = null,
    )

    /** [ai] — should be left to the AI (only `sure == false` is checked). [ms] — one expectation per matter, in order. */
    private data class Case(val text: String, val ms: List<M> = emptyList(), val ai: Boolean = false)

    private fun d(day: Int) = LocalDate.of(2026, 10, day)
    private fun nov(day: Int) = LocalDate.of(2026, 11, day)
    private val today = d(8)

    private fun c(text: String, vararg ms: M) = Case(text, ms.toList())
    private fun ai(text: String) = Case(text, ai = true)

    private val cases = listOf(
        // ---- days, times, parts of day ----
        c("завтра в 11 созвон с олегом", M(due = d(9), time = "11:00", who = listOf("Олег"))),
        c("утром в пн отвезти машину в сервис", M(due = d(12), part = "morning")),
        c("в след пятницу сдать отчёт по проекту", M(due = d(16))),
        c("до конца недели скинуть маше фотки с праздника", M(due = d(11), who = listOf("Маша"))),
        c("днюха у димы в субботу купить подарок", M(due = d(10), who = listOf("Дима"))),
        c("позвонить в жэк насчёт протечки", M(who = listOf("ЖЭК"))),
        c("встреча с анной в 15:30 на тверской 12", M(due = d(8), time = "15:30", who = listOf("Анна"), place = "тверская 12")),
        c("забрать посылку завтра после обеда", M(due = d(9), part = "afternoon")),
        c("сегодня вечером позвонить маме", M(due = d(8), part = "evening", who = listOf("Мама"))),
        c("через неделю продлить страховку", M(due = d(15))),
        c("послезавтра в 9:30 стоматолог", M(due = d(10), time = "09:30")),
        c("напомни через час проверить духовку", M(due = d(8), time = "11:00")),
        c("15 октября подать декларацию", M(due = d(15))),
        c("до 20-го закрыть акты с заказчиком", M(due = d(20))),
        c("к понедельнику подготовить презентацию для анны", M(due = d(12), who = listOf("Анна"))),
        c("в субботу в 12 детский праздник у кати", M(due = d(10), time = "12:00", who = listOf("Катя"))),
        c("1 ноября заплатить налог на машину", M(due = nov(1))),
        c("скинуть олегу реквизиты", M(who = listOf("Олег"))),
        c("написать ирине про сроки по логотипу", M(who = listOf("Ирина"))),
        c("забрать у лёши перфоратор в воскресенье", M(due = d(11), who = listOf("Лёша"))),
        c("в четверг в 9 планёрка", M(due = d(15), time = "09:00")),
        c("в 17 созвон с заказчиком", M(due = d(8), time = "17:00")),
        c("через 2 дня проверить оплату от клиента", M(due = d(10))),
        c("в конце месяца выставить счета", M(due = d(31))),
        c("в среду днём заехать в банк", M(due = d(14), part = "day")),
        c("в 19:30 ужин с лёшей и машей", M(due = d(8), time = "19:30", who = listOf("Лёша", "Маша"))),
        c("встретиться с костей в пт в обед", M(due = d(9), part = "day", who = listOf("Костя"))),
        c("завтра утром сдать анализы", M(due = d(9), part = "morning")),
        c("в пятницу вечером кино с машей", M(due = d(9), part = "evening", who = listOf("Маша"))),
        c("заказать торт к днюхе димы в субботу", M(due = d(10), who = listOf("Дима"))),
        c("скинуть сергею договор сегодня", M(due = d(8), who = listOf("Сергей"))),
        c("через месяц напомнить про продление домена", M(due = nov(8))),
        c("в 3 дня забрать дочку с танцев", M(due = d(8), time = "15:00")),
        c("оплатить кружок до 10 числа", M(due = d(10))),
        c("в следующий вторник обед с анной", M(due = d(13), who = listOf("Анна"))),
        c("уточнить у жэка график отключения воды", M(who = listOf("ЖЭК"))),
        c("постричься в воскресенье днём", M(due = d(11), part = "day")),
        c("к 14:00 подготовить смету для олега", M(due = d(8), time = "14:00", who = listOf("Олег"))),
        c("отправить резюме сегодня до 18", M(due = d(8), time = "18:00")),
        c("перезвонить диме через 2 часа", M(due = d(8), time = "12:00", who = listOf("Дима"))),

        // ---- waiting ----
        c("дима пришлёт макеты до среды", M(due = d(14), wait = "Дима")),
        c("маша должна скинуть счёт за аренду", M(wait = "Маша")),
        c("жду от анны правки до пт", M(due = d(9), wait = "Анна")),
        c("лёша вернёт деньги в субботу", M(due = d(10), wait = "Лёша")),
        c("сергей скинет фотографии объекта завтра", M(due = d(9), wait = "Сергей")),
        c("бухгалтер должна прислать акт сверки до 15-го", M(due = d(15), wait = "бухгалтер")),
        c("олег должен мне 5000 с прошлого месяца", M(wait = "Олег")),

        // ---- promises ----
        c("обещал олегу показать черновик в понедельник", M(due = d(12), promise = "Олег")),
        c("должен лёше 2000 вернуть до зарплаты", M(promise = "Лёша")),
        c("обещала свете рецепт пирога скинуть", M(promise = "Света")),
        c("пообещал диме помочь с переездом в сб", M(due = d(10), promise = "Дима")),

        // ---- repeats ----
        c("каждый вторник в 19 бассейн", M(time = "19:00", rep = Rep("weekly", listOf(2)))),
        c("по средам и пятницам английский в 18", M(time = "18:00", rep = Rep("weekly", listOf(3, 5)))),
        c("каждое утро пить витамины", M(part = "morning", rep = Rep("daily"))),
        c("по будням в 8:15 отвезти сына в сад", M(time = "08:15", rep = Rep("weekly", listOf(1, 2, 3, 4, 5)))),
        c("платить за интернет каждый месяц 5 числа", M(rep = Rep("monthly"))),
        c("каждый день в 22 читать ребёнку", M(time = "22:00", rep = Rep("daily"))),
        c("по выходным звонить родителям", M(rep = Rep("weekly", listOf(6, 7)))),
        c("раз в две недели менять фильтр", M(rep = Rep("weekly", null, every = 2))),
        c("каждый год 3 марта днюха у анны", M(rep = Rep("yearly"), who = listOf("Анна"))),
        c("каждый понедельник утром планирование недели", M(part = "morning", rep = Rep("weekly", listOf(1)))),
        c("каждые 3 дня поливать цветы", M(rep = Rep("daily", every = 3))),
        c("по пн ср пт пробежка утром", M(part = "morning", rep = Rep("weekly", listOf(1, 3, 5)))),

        // ---- several days ----
        c("в пн и чт тренировка в 7", M(days = listOf(d(12), d(15)))),
        c("в понедельник в 10 и в среду в 15 онлайн-урок у сына", M(days = listOf(d(12), d(14)))),
        c("12 и 14 октября съёмки у клиента", M(days = listOf(d(12), d(14)))),

        // ---- ideas ----
        c("идея: сделать рассылку для старых клиентов", M(idea = true)),
        c("идея — записать курс по фигме", M(idea = true)),
        c("идея: открыть второй пункт выдачи", M(idea = true)),
        c("мысль: поднять цены с нового года", M(idea = true)),
        c("идея для блога: как фрилансеру считать налоги", M(idea = true)),

        // ---- addresses ----
        c("заехать в мфц на профсоюзной 56 в понедельник", M(due = d(12), place = "профсоюзная 56")),
        c("забрать ключи по адресу ленина 14 кв 8 завтра", M(due = d(9), place = "ленина 14")),

        // ---- one matter or several ----
        c("купить молоко хлеб и яйца", M()),
        c("позвонить маше и договориться о встрече", M(who = listOf("Маша"))),
        c("купить корм коту, оплатить свет и записать сына к стоматологу", M(), M(), M()),
        c("завтра в 10 созвон с димой\nв пятницу отправить анне договор",
            M(due = d(9), time = "10:00", who = listOf("Дима")), M(due = d(9), who = listOf("Анна"))),
        c("1. купить краску\n2. позвонить олегу\n3. забрать заказ в пятницу", M(), M(who = listOf("Олег")), M(due = d(9))),
        c("в пятницу созвон с маркетологом в 12, а ещё оплатить хостинг", M(due = d(9), time = "12:00"), M()),
        c("завтра забрать документы у анны; в понедельник отвезти их в налоговую", M(due = d(9), who = listOf("Анна")), M(due = d(12))),

        // ---- for the AI: reports, edits, cancellations, contacts, long dictation ----
        ai("созвонились с олегом, он согласен на 50 тысяч"),
        ai("перенеси стоматолога на среду"),
        ai("отмени бассейн в эту субботу"),
        ai("встречу с машей можно удалить"),
        ai("анна уже прислала договор, можно закрывать"),
        ai("номер лёши +7 916 123-45-67"),
        ai("почта димы dima.k@mail.ru"),
        ai("поменяй время созвона с заказчиком на 16"),
        ai("клиент отказался от второго этапа"),
        ai("значит так завтра надо съездить к клиенту потом заехать в офис забрать документы вечером позвонить маме и не забыть купить продукты на неделю"),
        ai("всё, макет отправил заказчику"),
        ai("дима больше не работает над проектом, убери его"),
        ai("встреча с олегом не в пятницу а в субботу"),
        ai("маша заболела, занятие в четверг не состоится"),
        ai("сделал отчёт, отметь"),
        ai("лёша отдал долг"),
        ai("оплатил интернет за октябрь"),
        ai("передвинь все дела с пятницы на понедельник"),
        ai("запиши телефон сантехника 89031234567 его зовут виктор"),
        ai("вчера был у врача, сказали пить таблетки две недели и прийти на приём 22 октября"),
    )

    private val data = AppData(persons = listOf(
        Person(name = "Олег", kept = true), Person(name = "Маша", kept = true), Person(name = "Дима", kept = true),
        Person(name = "Анна", kept = true), Person(name = "Лёша", kept = true), Person(name = "ЖЭК", org = true, kept = true),
    ))

    private fun norm(s: String) = s.lowercase().replace('ё', 'е').trim()

    /** Address words by their first five letters and numbers as they are: «Тверской, 12» = «тверская 12». */
    private fun placeKeys(s: String) = Regex("[\\p{L}\\d]+").findAll(norm(s)).map { it.value.take(5) }.toSet()

    private fun check(e: M, i: LocalParser.Item): List<String> {
        val bad = mutableListOf<String>()
        if (e.rep == null && e.days.isEmpty() && i.due != e.due) bad += "due"
        if (e.days.isEmpty() && i.time != e.time) bad += "time"
        if (i.part != e.part) bad += "part"
        val r = i.repeat
        if (e.rep == null) { if (r != null) bad += "repeat" } else if (r == null || r.first != e.rep.freq || r.second != e.rep.every ||
            (e.rep.wds != null && r.third.sorted() != e.rep.wds.sorted())) bad += "repeat"
        if (i.days.map { it.first }.sorted() != e.days.sorted()) bad += "days"
        if (i.waitingFor?.let(::norm) != e.wait?.let(::norm)) bad += "wait"
        if (i.promisedTo?.let(::norm) != e.promise?.let(::norm)) bad += "promise"
        val got = (i.people + listOfNotNull(i.waitingFor, i.promisedTo)).map(::norm).toSet()
        val want = (e.who + listOfNotNull(e.wait, e.promise)).map(::norm).toSet()
        if (got != want) bad += "people"
        if ((i.kind == "idea") != e.idea) bad += "kind"
        if (e.place == null) { if (i.place != null) bad += "place" }
        else if (i.place == null || !placeKeys(i.place!!).containsAll(placeKeys(e.place))) bad += "place"
        return bad
    }

    private fun show(e: M) = listOfNotNull(e.due?.let { "due=$it" }, e.time?.let { "time=$it" }, e.part?.let { "part=$it" },
        e.rep?.let { "repeat=${it.freq}/${it.every}/${it.wds ?: "*"}" }, e.days.takeIf { it.isNotEmpty() }?.let { "days=$it" },
        e.wait?.let { "wait=$it" }, e.promise?.let { "promise=$it" }, e.who.takeIf { it.isNotEmpty() }?.let { "who=$it" },
        "idea".takeIf { e.idea }, e.place?.let { "place=$it" }).joinToString(" ").ifEmpty { "plain" }

    private fun show(i: LocalParser.Item) = listOfNotNull("«${i.title}»", i.kind.takeIf { it != "task" }, i.due?.let { "due=$it" },
        i.time?.let { "time=$it" }, i.part?.let { "part=$it" }, i.repeat?.let { "repeat=${it.first}/${it.second}/${it.third}" },
        i.days.takeIf { it.isNotEmpty() }?.let { "days=${it.map { p -> p.first }}" }, i.waitingFor?.let { "wait=$it" },
        i.promisedTo?.let { "promise=$it" }, i.people.takeIf { it.isNotEmpty() }?.let { "who=$it" }, i.place?.let { "place=$it" })
        .joinToString(" ")

    @Test fun holdout() {
        assertTrue(cases.isNotEmpty())
        var correct = 0; var confident = 0; var needless = 0
        val misses = mutableListOf<String>()
        for (k in cases) {
            val r = LocalParser.parse(k.text, data, today = today, now = LocalTime.of(10, 0))
            val got = "sure=${r.sure}${r.why?.let { " ($it)" } ?: ""} " + r.items.joinToString(" | ") { show(it) }
            val text = k.text.replace("\n", " / ")
            if (k.ai) {
                if (!r.sure) correct++ else { confident++; misses += "MISS [$text] expected AI got $got" }
                continue
            }
            val want = k.ms.joinToString(" | ") { show(it) }
            val bad = if (r.items.size != k.ms.size) listOf("count ${r.items.size}≠${k.ms.size}")
                else k.ms.zip(r.items).flatMapIndexed { n, (e, i) -> check(e, i).map { if (k.ms.size > 1) "#${n + 1} $it" else it } }
            when {
                !r.sure -> { needless++; misses += "MISS [$text] expected local $want got $got" }
                bad.isEmpty() -> correct++
                else -> { confident++; misses += "MISS [$text] expected $want got $got — wrong: ${bad.joinToString(", ")}" }
            }
        }
        println("holdout: $correct/${cases.size} correct, $confident confident errors, $needless unnecessary AI")
        misses.forEach(::println)
    }
}
