package ru.anjeib.margin

import org.junit.Assert.assertTrue
import org.junit.Test
import ru.anjeib.margin.ai.LocalParser
import ru.anjeib.margin.data.AppData
import ru.anjeib.margin.data.Person
import java.time.LocalDate
import java.time.LocalTime

/**
 * A second independent holdout for [LocalParser]: 100 phrases written without looking at its rules, expectations taken
 * from what the words mean. Base: Thursday 2026-10-08, 10:00. Prints the score and every miss; never fails on a low score.
 */
class ParseHoldout2Test {
    private val today = LocalDate.of(2026, 10, 8)
    private val now = LocalTime.of(10, 0)
    private val data = AppData(persons = listOf(
        Person(name = "Олег", kept = true), Person(name = "Маша", kept = true), Person(name = "Дима", kept = true),
        Person(name = "Анна", kept = true), Person(name = "Лёша", kept = true), Person(name = "ЖЭК", org = true, kept = true),
    ))

    private fun o(day: Int) = LocalDate.of(2026, 10, day)
    private fun n(day: Int) = LocalDate.of(2026, 11, day)

    data class Rep(val freq: String, val weekdays: List<Int> = emptyList(), val interval: Int = 1)

    /** One expected matter. Unnamed fields must come back empty (due is free with a repeat or days, time with days). */
    data class M(
        val due: LocalDate? = null, val time: String? = null, val part: String? = null, val rep: Rep? = null,
        val days: List<LocalDate>? = null, val wait: String? = null, val prom: String? = null,
        val who: List<String> = emptyList(), val idea: Boolean = false, val place: String? = null,
        /** Finds the matter by a word of its title instead of by position. */
        val key: String? = null,
    )

    data class C(val text: String, val ms: List<M>, val ai: Boolean = false)

    private fun c(text: String, vararg m: M) = C(text, m.toList())
    private fun ai(text: String) = C(text, emptyList(), ai = true)
    private fun w(vararg d: Int) = Rep("weekly", d.toList())

    private val cases = listOf(
        // teacher
        c("проверить контрольные 7б до понедельника", M(due = o(12))),
        c("родительское собрание в среду в 18 30", M(due = o(14), time = "18:30")),
        c("каждый понедельник в 8 15 планёрка у завуча", M(rep = w(1), time = "08:15")),
        c("завтра написать анне про замену уроков", M(due = o(9), who = listOf("Анна"))),
        c("по вторникам и четвергам кружок робототехники в 16", M(rep = w(2, 4), time = "16:00")),
        c("подготовить презентацию к уроку к 15 октября", M(due = o(15))),
        c("мысль: устроить классу экскурсию в планетарий", M(idea = true)),
        // parent
        c("записать сына к ортодонту", M()),
        c("в субботу в 11 день рождения у кати", M(due = o(10), time = "11:00", who = listOf("Катя"))),
        c("забрать машу из садика в 17", M(due = o(8), time = "17:00", who = listOf("Маша"))),
        c("купить подарок олегу на др 20 октября", M(due = o(20), who = listOf("Олег"))),
        c("оплатить кружок по плаванию до 10-го", M(due = o(10))),
        c("вечерм проверить у сына домашку", M(due = o(8), part = "evening")),
        c("послезавтра прививка коту в 12", M(due = o(10), time = "12:00")),
        c("отвезти кота к ветеринару на мира 20 в субботу утром", M(due = o(10), part = "morning", place = "мира 20")),
        c("каждый день в 21 таблетки маме", M(rep = Rep("daily"), time = "21:00", who = listOf("Мама"))),
        c("в пятницу собрание в школе в 19", M(due = o(9), time = "19:00")),
        // small shop
        c("заказать у поставщика сахар и муку", M()),
        c("олег привезёт товар в понедельник", M(due = o(12), wait = "Олег")),
        c("ежемесячно 10-го платить аренду", M(rep = Rep("monthly"))),
        c("раз в квартал сдавать отчётность", M(rep = Rep("monthly", interval = 3))),
        c("маша должна скинуть накладные до пятницы", M(due = o(9), wait = "Маша")),
        c("сделать ревизию на складе в воскресенье", M(due = o(11))),
        c("каждую среду вывозить мусор", M(rep = w(3))),
        c("до 25 октября продлить договор с офд", M(due = o(25))),
        c("идея: продавать кофе навынос", M(idea = true)),
        c("встреча с бухгалтером в 16 на садовой 3", M(due = o(8), time = "16:00", place = "садовой 3")),
        c("обещал анне отложить два пакета корма до субботы", M(due = o(10), prom = "Анна")),
        // developer
        c("созвон с клинетом завтра в 11", M(due = o(9), time = "11:00")),
        c("дима пришлёт апи доку в среду", M(due = o(14), wait = "Дима")),
        c("задеплоить релиз в пятницу до обеда", M(due = o(9), part = "morning")),
        c("по средам в 12 дейли с командой", M(rep = w(3), time = "12:00")),
        c("выставить счет клиенту до конца месяца", M(due = o(31))),
        c("обещал олегу ревью пулреквеста сегодня", M(due = o(8), prom = "Олег")),
        c("в 9 проверить логи", M(due = o(9), time = "09:00")),
        c("через 2 недели продлить домен", M(due = o(22))),
        c("каждые две недели по пятницам ретро в 17", M(rep = Rep("weekly", listOf(5), 2), time = "17:00")),
        c("лёша скинет доступы к серверу завтра", M(due = o(9), wait = "Лёша")),
        c("мысль: переписать авторизацию на oauth", M(idea = true)),
        c("1 ноября продлить подписку на хостинг", M(due = n(1))),
        // student
        c("сдать курсовую до 20-го", M(due = o(20))),
        c("в понедельник и среду в 19 английский", M(days = listOf(o(12), o(14)))),
        c("экзамен по матану 5 января", M(due = LocalDate.of(2027, 1, 5))),
        c("завтра утром в библиотку", M(due = o(9), part = "morning")),
        c("обещала лёше вернуть дрель до воскресенья", M(due = o(11), prom = "Лёша")),
        c("анна обещала дать конспект в понедельник", M(due = o(12), wait = "Анна")),
        c("черз неделю зачёт по истории", M(due = o(15))),
        c("оплатить общагу ежемесячно 25-го", M(rep = Rep("monthly"))),
        c("днём зайти в деканат", M(due = o(8), part = "day")),
        // bills, doctors, car
        c("оплатить комуналку до 15 числа", M(due = o(15))),
        c("заплатить за интернет 12 октября", M(due = o(12))),
        c("записаться к терапевту на следующей неделе", M(due = o(18))),
        c("к стоматологу в 15 30", M(due = o(8), time = "15:30")),
        c("поменять резину на зимнюю до ноября", M(due = o(31))),
        c("техосмотр 3 ноября в 10 на заводской 7", M(due = n(3), time = "10:00", place = "заводской 7")),
        c("жэк пришлёт сантехника завтра", M(due = o(9), wait = "ЖЭК")),
        c("позвонить в жэк насчёт батарей", M(who = listOf("ЖЭК"))),
        c("каждый год 12 декабря продлевать страховку на машину", M(rep = Rep("yearly"))),
        c("в 7 вечера тренировка", M(due = o(8), time = "19:00")),
        c("ночью забрать лёшу из аэропорта", M(due = o(8), part = "night", who = listOf("Лёша"))),
        // pets, travel, birthdays
        c("погулять с собакой после обеда", M(due = o(8), part = "afternoon")),
        c("24 октября поезд в казань в 7 40", M(due = o(24), time = "07:40")),
        c("поздравить катю с днюхой 14 октября", M(due = o(14), who = listOf("Катя"))),
        c("забронировать отель в сочи до среды", M(due = o(14))),
        c("завтра в 8 утра анализы в поликлинике на ул гагарина 15", M(due = o(9), time = "08:00", place = "гагарина 15")),
        c("покупать корм коту каждую субботу", M(rep = w(6))),
        c("в субботу и воскресенье на дачу", M(days = listOf(o(10), o(11)))),
        c("в полдень обед с анной", M(due = o(8), time = "12:00", who = listOf("Анна"))),
        // one or several matters
        c("купить молоко хлеб и яйца", M()),
        c("завтра в 10 позвонить в банк а в субботу отвезти кота к ветеринару",
            M(due = o(9), time = "10:00", key = "банк"), M(due = o(10), key = "ветерин")),
        c("оплатить свет\nзаписаться на шиномонтаж в пятницу", M(key = "свет"), M(due = o(9), key = "шином")),
        c("позвонить олегу; написать маше", M(who = listOf("Олег"), key = "олег"), M(who = listOf("Маша"), key = "маш")),
        c("в понедельник отправить счёт диме а во вторник забрать справку из поликлиники",
            M(due = o(12), who = listOf("Дима"), key = "счёт"), M(due = o(13), key = "справк")),
        c("1. купить краски\n2. распечатать прописи\n3. позвонить анне",
            M(key = "краск"), M(key = "пропис"), M(who = listOf("Анна"), key = "позвон")),
        c("сегодня в 18 забрать заказ, завтра в 9 отвезти документы в налоговую",
            M(due = o(8), time = "18:00", key = "заказ"), M(due = o(9), time = "09:00", key = "налог")),
        // more people, waits, places
        c("мама приедет в воскресенье в 14", M(due = o(11), time = "14:00", who = listOf("Мама"))),
        c("встреча с олегом в кафе на пушкина 10 в пятницу в 13", M(due = o(9), time = "13:00", who = listOf("Олег"), place = "пушкина 10")),
        c("отправить диме инвойс за сентябрь", M(who = listOf("Дима"))),
        c("сергей петрович должен оплатить счёт до 12 октября", M(due = o(12), wait = "Сергей")),
        c("по будням в 7 зарядка", M(rep = w(1, 2, 3, 4, 5), time = "07:00")),

        // for the AI: reports, edits, cancellations, contacts, dictation
        ai("вася прислал счёт за ремонт"),
        ai("отвёз машину в сервис"),
        ai("перенеси стоматолога на пятницу"),
        ai("сдвинь встречу с олегом на час позже"),
        ai("поменяй время планёрки на 11"),
        ai("удали задачу про шины"),
        ai("отмени тренировку в субботу"),
        ai("созвонились с машей договорились на вторник"),
        ai("телефон сантехника 8 916 123 45 67"),
        ai("почта бухгалтера ivanova.buh@mail.ru"),
        ai("значит так завтра с утра надо заехать в налоговую потом забрать документы у нотариуса и ещё не забыть купить корм коту и позвонить маме насчёт выходных"),
        ai("дима уже отправил макет"),
        ai("оплатила интернет"),
        ai("клиент отказался от заказа"),
        ai("не надо больше напоминать про витамины"),
        ai("встреча с анной отменилась"),
        ai("переименуй тему ремонт в ремонт кухни"),
        ai("запиши номер олега +7 903 555 12 34"),
        ai("лёша вернул долг"),
        ai("поставщик привёз товар но не хватает двух коробок"),
    )

    private fun stem(s: String?) = s?.lowercase()?.replace('ё', 'е')?.trim()?.take(3)

    private fun tokens(s: String) = Regex("[\\p{L}\\d]+").findAll(s.lowercase().replace('ё', 'е'))
        .map { if (it.value[0].isDigit()) it.value else it.value.take(5) }.toSet()

    private fun show(i: LocalParser.Item) = buildString {
        append("{").append(i.kind).append(" «").append(i.title).append("»")
        i.due?.let { append(" due=$it") }; i.time?.let { append(" time=$it") }; i.part?.let { append(" part=$it") }
        i.repeat?.let { append(" repeat=${it.first}/${it.second}/${it.third}") }
        if (i.days.isNotEmpty()) append(" days=${i.days.map { it.first }}")
        if (i.people.isNotEmpty()) append(" people=${i.people}")
        i.waitingFor?.let { append(" wait=$it") }; i.promisedTo?.let { append(" prom=$it") }; i.place?.let { append(" place=$it") }
        append("}")
    }

    private fun diff(i: LocalParser.Item, m: M): List<String> {
        val bad = mutableListOf<String>()
        if (m.rep == null && m.days == null || m.due != null) if (i.due != m.due) bad += "due ${m.due}"
        if (m.days == null && i.time != m.time) bad += "time ${m.time}"
        if (i.part != m.part) bad += "part ${m.part}"
        val r = i.repeat
        if (m.rep == null) { if (r != null) bad += "no repeat" }
        else if (r == null || r.first != m.rep.freq || r.third.sorted() != m.rep.weekdays.sorted() || (m.rep.interval > 1 && r.second != m.rep.interval))
            bad += "repeat ${m.rep}"
        if (m.days == null) { if (i.days.isNotEmpty()) bad += "no days" }
        else if (i.days.map { it.first }.toSet() != m.days.toSet()) bad += "days ${m.days}"
        if (stem(i.waitingFor) != stem(m.wait)) bad += "wait ${m.wait}"
        if (stem(i.promisedTo) != stem(m.prom)) bad += "prom ${m.prom}"
        val wantWho = (m.who + listOfNotNull(m.wait, m.prom)).map { stem(it) }.toSet()
        val gotWho = (i.people + listOfNotNull(i.waitingFor, i.promisedTo)).map { stem(it) }.toSet()
        if (wantWho != gotWho) bad += "people ${m.who + listOfNotNull(m.wait, m.prom)}"
        if ((i.kind == "idea") != m.idea) bad += if (m.idea) "idea" else "not idea"
        if (m.place == null) { if (!i.place.isNullOrBlank()) bad += "no place" }
        else if (i.place.isNullOrBlank() || !tokens(i.place!!).containsAll(tokens(m.place))) bad += "place ${m.place}"
        return bad
    }

    @Test
    fun holdout2() {
        assertTrue(cases.isNotEmpty())
        var correct = 0; var confident = 0; var needless = 0
        val misses = mutableListOf<String>()
        for (k in cases) {
            val r = LocalParser.parse(k.text, data, today = today, now = now)
            val got = "sure=${r.sure}${r.why?.let { " ($it)" } ?: ""} ${r.items.map { show(it) }}"
            val phrase = k.text.replace("\n", " / ")
            if (k.ai) {
                if (!r.sure) correct++ else { confident++; misses += "MISS [$phrase] expected AI (sure=false) got $got" }
                continue
            }
            val bad = mutableListOf<String>()
            if (r.items.size != k.ms.size) bad += "${k.ms.size} matter(s)"
            k.ms.forEachIndexed { idx, m ->
                val item = if (m.key != null) r.items.firstOrNull { it.title.lowercase().replace('ё', 'е').contains(m.key.replace('ё', 'е')) }
                           else r.items.getOrNull(idx)
                if (item == null) bad += "matter #${idx + 1}${m.key?.let { " «$it»" } ?: ""} missing"
                else diff(item, m).forEach { bad += if (k.ms.size > 1) "#${idx + 1}: $it" else it }
            }
            when {
                !r.sure -> { needless++; misses += "MISS [$phrase] expected local ${k.ms} got $got${if (bad.isNotEmpty()) " | would also miss: $bad" else ""} [unnecessary AI]" }
                bad.isEmpty() -> correct++
                else -> { confident++; misses += "MISS [$phrase] expected $bad got $got" }
            }
        }
        println("holdout2: $correct/${cases.size} correct, $confident confident errors, $needless unnecessary AI")
        misses.forEach { println(it) }
    }
}
