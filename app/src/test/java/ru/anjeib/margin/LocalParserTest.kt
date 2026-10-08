package ru.anjeib.margin

import org.junit.Assert.assertTrue
import org.junit.Test
import ru.anjeib.margin.ai.Assistant
import ru.anjeib.margin.ai.LocalParser
import ru.anjeib.margin.ai.Scope
import ru.anjeib.margin.data.AppData
import ru.anjeib.margin.data.Person
import ru.anjeib.margin.data.Task
import ru.anjeib.margin.data.Topics
import ru.anjeib.margin.i18n.L
import ru.anjeib.margin.i18n.Lang
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * How much of everyday writing the phone understands with no AI: each phrase goes parse → plan → Assistant.apply,
 * and the result is checked like the live AI tests check the model. Prints the share and every miss. Offline, free.
 */
class LocalParserTest {
    private val today = LocalDate.now()
    private fun next(d: DayOfWeek) = today.with(TemporalAdjusters.nextOrSame(d))
    private val tomorrow = today.plusDays(1)
    private fun dom(n: Int): LocalDate { var m = today.withDayOfMonth(1); while (true) { if (n <= m.lengthOfMonth() && !m.withDayOfMonth(n).isBefore(today)) return m.withDayOfMonth(n); m = m.plusMonths(1) } }
    private fun on(day: Int, mon: Int) = LocalDate.of(today.year, mon, day).let { if (it.isBefore(today.minusDays(1))) it.plusYears(1) else it }

    private val base = AppData(persons = listOf(Person(name = "Олег", kept = true), Person(name = "ЖЭК", org = true, kept = true), Person(name = "Лёша", kept = true)))

    data class Case(val text: String, val check: (List<Task>) -> Boolean)

    private val cases = listOf(
        Case("завтра в 18:00 стоматолог") { n -> n.single().let { it.title.contains("томатолог", true) && it.dueDate == tomorrow && it.time == "18:00" } },
        Case("купить хлеб") { n -> n.single().let { it.title == "Купить хлеб" && it.due == null } },
        Case("позвонить маме в пятницу") { n -> n.single().let { it.dueDate == next(DayOfWeek.FRIDAY) && "Мама" in it.people } },
        Case("созвониться с Анной по договору") { n -> n.single().people.contains("Анна") },
        Case("Дима должен прислать макет в среду") { n -> n.single().let { it.waitingFor == "Дима" && it.dueDate == next(DayOfWeek.WEDNESDAY) } },
        Case("Обещал Анне показать калькулятор до пятницы") { n -> n.single().let { it.promisedTo == "Анна" && it.dueDate == next(DayOfWeek.FRIDAY) && it.waitingFor == null } },
        Case("Каждый вторник в 10:00 планёрка") { n -> n.single().let { it.repeat?.days == listOf(2) && it.time == "10:00" } },
        Case("кажд понедельник заказать муку у тимура") { n -> n.single().let { it.repeat?.days == listOf(1) && it.waitingFor == null } },
        Case("аренда первого числа каждый месяц") { n -> n.single().let { it.repeat?.freq == "monthly" && it.dueDate == dom(1) } },
        Case("заказать упаковку у ПакетПро до среды") { n -> n.single().let { it.dueDate == next(DayOfWeek.WEDNESDAY) && it.waitingFor == null } },
        Case("тренировка в понедельник и четверг в 19:00") { n -> n.single().let { it.slots.size == 2 && it.slots.all { s -> s.time == "19:00" } && it.repeat == null } },
        Case("полить цветы 12, 15 и 18 октября") { n -> n.single().slots.size == 3 },
        Case("через неделю записаться к врачу") { n -> n.single().dueDate == today.plusDays(7) },
        Case("через пару дней напомнить про смету") { n -> n.single().dueDate == today.plusDays(2) },
        Case("на этой неделе разобрать почту") { n -> n.single().dueDate == next(DayOfWeek.SUNDAY) },
        Case("в 9 утра йога") { n -> n.single().time == "09:00" },
        Case("встреча с Олегом в 7 вечера") { n -> n.single().let { it.time == "19:00" && "Олег" in it.people } },
        Case("идея: сделать блок с ценами без «от»") { n -> n.single().let { it.thought && it.title.contains("цен") } },
        Case("купить корм коту, позвонить маме и записаться к барберу") { n -> n.size == 3 },
        Case("купить хлеб и молоко") { n -> n.size == 1 },
        Case("лена пришлёт график до пятницы") { n -> n.single().let { it.waitingFor == "Лена" && it.dueDate == next(DayOfWeek.FRIDAY) } },
        Case("ждём от Маши ключи от квартиры") { n -> n.single().waitingFor == "Маша" },
        Case("Маша вернёт 3000 в субботу") { n -> n.single().let { it.waitingFor == "Маша" && it.dueDate == next(DayOfWeek.SATURDAY) } },
        Case("отправить счёт клиенту до 10-го") { n -> n.single().dueDate == dom(10) },
        Case("к 15:30 подготовить презентацию") { n -> n.single().time == "15:30" },
        Case("в следующий вторник обед с Катей") { n -> n.single().let { it.dueDate == today.with(TemporalAdjusters.next(DayOfWeek.MONDAY)).plusDays(1) && "Катя" in it.people } },
        Case("на следующей неделе обновить резюме") { n -> n.single().let { it.dueDate == today.with(TemporalAdjusters.next(DayOfWeek.MONDAY)).plusDays(6) } },
        Case("забрать посылку послезавтра") { n -> n.single().dueDate == today.plusDays(2) },
        Case("вечером погулять с собакой") { n -> n.single().dayPart == "evening" },
        Case("стрижка 25 октября в 11") { n -> n.single().let { it.dueDate == on(25, 10) && it.time == "11:00" } },
        Case("до конца месяца сдать отчёт") { n -> n.single().dueDate == today.with(TemporalAdjusters.lastDayOfMonth()) },
        Case("в выходные съездить на дачу") { n -> n.single().dueDate == next(DayOfWeek.SATURDAY) },
        Case("оплатить интернет 5.11") { n -> n.single().dueDate == on(5, 11) },
        Case("напомни купить подарок Кате") { n -> n.single().let { it.title.startsWith("Купить подарок") && "Катя" in it.people } },
        Case("позвонить в ЖЭК насчёт счётчиков") { n -> n.single().title.startsWith("Позвонить в ЖЭК") },
        Case("каждый день пить витамины утром") { n -> n.single().repeat?.freq == "daily" },
        Case("Лёше скинуть исходники до четверга") { n -> n.single().let { it.dueDate == next(DayOfWeek.THURSDAY) && ("Лёша" in it.people || it.promisedTo == "Лёша") } },
        Case("Ирина должна прислать тексты до среды") { n -> n.single().let { it.waitingFor == "Ирина" && it.dueDate == next(DayOfWeek.WEDNESDAY) } },
        Case("в среду в 14:00 созвон с бухгалтерией") { n -> n.single().let { it.dueDate == next(DayOfWeek.WEDNESDAY) && it.time == "14:00" } },
        Case("Сапсан 24 октября в 7:40") { n -> n.single().let { it.dueDate == on(24, 10) && it.time == "07:40" } },
        Case("купить билеты в кино на субботу") { n -> n.single().dueDate == next(DayOfWeek.SATURDAY) },
        Case("во вторник и в пятницу звонить маме") { n -> n.single().slots.size == 2 },
        Case("по понедельникам и средам бассейн в 8 утра") { n -> n.single().let { it.repeat?.days == listOf(1, 3) && it.time == "08:00" } },
        Case("Ирина пришлёт варианты квартир завтра") { n -> n.single().let { it.waitingFor == "Ирина" && it.dueDate == tomorrow } },
        Case("должна Кате 500 рублей") { n -> n.single().promisedTo == "Катя" },
        Case("записаться к стоматологу на Тверской 12") { n -> n.single().let { it.place == "Тверской, 12" && it.title.startsWith("Записаться к стоматологу") } },
        Case("Купить молоко\nЗабрать костюм из химчистки в субботу\nПозвонить Олегу завтра в 11") { n -> n.size == 3 && n.any { it.dueDate == tomorrow && it.time == "11:00" && "Олег" in it.people } },
        Case("сегодня вечером тренировка; завтра в 10 стоматолог") { n -> n.size == 2 && n.any { it.dueDate == tomorrow && it.time == "10:00" } },
        Case("подготовить бриф для кофейни к четвергу") { n -> n.single().dueDate == next(DayOfWeek.THURSDAY) },
        Case("в субботу день рождения Кати, купить подарок") { n -> n.isNotEmpty() && n.any { it.dueDate == next(DayOfWeek.SATURDAY) } },
    )

    /** What should go to the AI when there is a network: the phone must say it isn't sure. */
    private val forAi = listOf(
        "созвонились с Игорем, приказ будет через две недели",
        "перенеси мастера на послезавтра",
        "лена до пятницы сдаёт график смен я жду ещё обещал виктору арендодателю оплату до десятого сломалась тестомесилка на садовой вызвать мастера завтра утром купить кассовую ленту",
        "мне кажется, на главной нужен калькулятор",
    )

    /** Phrases written earlier for the AI tests, not for this parser: (phrase, check or null when it must go to the AI). */
    private val foreign: List<Pair<String, ((List<Task>) -> Boolean)?>> = listOf(
        "На этой неделе разобраться с vuzburo и запустить. Дима сделает дизайн, на мне тексты и оплата." to { n -> n.any { it.dueDate == next(DayOfWeek.SUNDAY) } },
        "для vuzburo ещё купить домен и настроить почту" to { n -> n.any { it.title.contains("домен") } && n.any { it.title.contains("почт") } && n.none { it.title.length < 14 } },
        "Саша обещал прислать смету, через пару дней напомнить. Ещё почитать про новый Claude, купить корм коту и подумать над главной vuzburo — мне кажется, нужен калькулятор стоимости и отзывы" to null,
        "созвониться с Анной по договору" to { n -> n.single().people.contains("Анна") },
        "завтра в 18:00 стоматолог" to { n -> n.single().let { it.dueDate == tomorrow && it.time == "18:00" } },
        "Дима должен прислать макет в среду" to { n -> n.single().waitingFor == "Дима" },
        "Дима — наш дизайнер, его телефон +7 999 123-45-67" to null,
        "идея: на главной vuzburo сделать блок с ценами без «от»" to { n -> n.single().thought },
        "Обещал Анне показать калькулятор до пятницы" to { n -> n.single().promisedTo == "Анна" },
        "Каждый вторник в 10:00 планёрка по vuzburo" to { n -> n.single().let { it.repeat?.days == listOf(2) && it.time == "10:00" } },
        "кажд понедельник заказать муку у тимура" to { n -> n.single().let { it.repeat?.days == listOf(1) && it.waitingFor == null } },
        "аренда первого числа каждый месяц" to { n -> n.single().repeat?.freq == "monthly" },
        "заказать упаковку у ПакетПро до среды" to { n -> n.single().let { it.dueDate == next(DayOfWeek.WEDNESDAY) && it.waitingFor == null } },
        "Дента+: Катя делает макеты главной к четвергу, Артём верстает после макетов, Ирина должна прислать тексты до среды" to null,
        "Созвонились. Приказ о зачислении в работе, будет через 2 недели. Нужно отправить документы об оплате в бухгалтерию." to null,
    )

    @Test fun foreignPhrases() {
        L.lang = Lang.Ru
        var right = 0; var toAi = 0; var wrongSure = 0
        foreign.forEach { (text, check) ->
            val r = LocalParser.parse(text, base)
            if (!r.sure) { toAi++; println("  → ИИ   «$text» (${r.why})"); if (check == null) right++; return@forEach }
            val after = Topics.track(base, Assistant.apply(base, LocalParser.plan(r, Scope.Main, source = text), text, strict = true).data)
            val new = after.tasks.filter { t -> base.tasks.none { it.id == t.id } }
            val pass = check != null && runCatching { check(new) }.getOrDefault(false)
            if (pass) right++ else wrongSure++
            println((if (pass) "  ok   " else "  ✗    ") + "«$text» → " + new.joinToString(" | ") { "${it.title} [due=${it.due} t=${it.time} wait=${it.waitingFor} prom=${it.promisedTo} ppl=${it.people}]" })
        }
        println("Чужие фразы: верно ${right}/${foreign.size}, отдано ИИ $toAi, ошибся уверенно $wrongSure")
    }

    @Test fun coverage() {
        L.lang = Lang.Ru
        var ok = 0; var sure = 0
        val misses = mutableListOf<String>()
        cases.forEach { c ->
            val r = LocalParser.parse(c.text, base)
            if (r.sure) sure++
            val after = Topics.track(base, Assistant.apply(base, LocalParser.plan(r, Scope.Main, source = c.text), c.text, strict = true).data)
            val new = after.tasks.filter { t -> base.tasks.none { it.id == t.id } }
            val pass = runCatching { c.check(new) }.getOrDefault(false)
            if (pass) ok++ else misses += "  ✗ «${c.text.replace("\n", " / ")}» → " + new.joinToString(" | ") {
                "${it.title} [due=${it.due} t=${it.time} part=${it.dayPart} rep=${it.repeat?.freq}${it.repeat?.days} days=${it.slots.map { s -> s.date + " " + s.time }} wait=${it.waitingFor} prom=${it.promisedTo} ppl=${it.people} place=${it.place} thought=${it.thought}]"
            }
        }
        val flagged = forAi.count { !LocalParser.parse(it, base).sure }
        println("Разбор в телефоне: $ok/${cases.size} фраз верно, уверен в $sure; отдаёт ИИ ${flagged}/${forAi.size} сложных")
        misses.forEach { println(it) }
        assertTrue("too many misses: $ok/${cases.size}", ok >= cases.size * 8 / 10)
        assertTrue(flagged == forAi.size)
    }
}
