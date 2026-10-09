package ru.anjeib.margin

import org.junit.Assert.assertTrue
import org.junit.Test
import ru.anjeib.margin.ai.LocalParser
import ru.anjeib.margin.data.AppData
import ru.anjeib.margin.data.Person
import ru.anjeib.margin.i18n.L
import ru.anjeib.margin.i18n.Lang
import java.time.LocalDate
import java.time.LocalTime

/**
 * ~200 everyday phrases of a freelancer / designer / small-business owner, with what a careful human assistant would
 * expect — written from the meaning, not from the parser's output. Fixed base: Thursday 8 Oct 2026, 10:00.
 * Prints «corpus: X/Y correct, Z confident errors, W unnecessary AI» and every miss; guards against regressions.
 *
 * Unspecified due / time / part / repeat / days / waitingFor / promisedTo / place must be empty (a made-up date or a
 * false «ждём» is an error); «*» means «not checked». Fields are checked on item [Exp.at] (default the first).
 */
class ParseCorpusTest {
    private val today = LocalDate.of(2026, 10, 8) // Thursday
    private val now = LocalTime.of(10, 0)
    private val base = AppData(persons = listOf("Олег", "Маша", "Дима", "Анна", "Лёша").map { Person(name = it, kept = true) } +
        Person(name = "ЖЭК", org = true, kept = true))

    private fun o(d: Int) = "2026-10-%02d".format(d)
    private fun nv(d: Int) = "2026-11-%02d".format(d)

    /** repeat: "daily", "monthly", "weekly:1,4", "weekly/2:3" (interval 2). days: "2026-10-12" or "2026-10-12 19:00". */
    data class Exp(val n: Int = 1, val due: String? = null, val time: String? = null, val part: String? = null, val repeat: String? = null,
                   val days: List<String>? = null, val wait: String? = null, val promise: String? = null, val person: String? = null,
                   val idea: Boolean = false, val place: String? = null, val word: String? = null, val ai: Boolean = false, val at: Int = 0)

    private val corpus: List<Pair<String, Exp>> = listOf(
        // ---- simple to-dos and days ----
        "купить хлеб" to Exp(word = "хлеб"),
        "заказать картриджи для принтера" to Exp(word = "картридж"),
        "обновить резюме" to Exp(word = "резюме"),
        "завтра в 15 созвон с Олегом" to Exp(due = o(9), time = "15:00", person = "Олег"),
        "позвонить маме в пятницу" to Exp(due = o(9), person = "Мама"),
        "сдать макет логотипа к среде" to Exp(due = o(14), word = "логотип"),
        "отправить счёт клиенту до 15-го" to Exp(due = o(15), word = "счёт"),
        "оплатить аренду мастерской 12 октября" to Exp(due = o(12), word = "аренд"),
        "через неделю проверить, пришла ли оплата" to Exp(due = o(15), word = "оплат"),
        "на следующей неделе обновить портфолио на Behance" to Exp(due = o(18), word = "портфолио"),
        "в выходные разобрать архив фотографий" to Exp(due = o(10), word = "архив"),
        "до конца месяца сдать декларацию" to Exp(due = o(31), word = "декларац"),
        "послезавтра забрать документы из МФЦ" to Exp(due = o(10), word = "МФЦ"),
        "сегодня вечером накидать мудборд для кофейни" to Exp(due = o(8), part = "evening", word = "мудборд"),
        "завтра утром отправить правки по баннерам" to Exp(due = o(9), part = "morning", word = "правки"),
        "завтра в 9 утра йога" to Exp(due = o(9), time = "09:00", word = "йога"),
        "сегодня в 7 вечера ужин с Анной" to Exp(due = o(8), time = "19:00", person = "Анна"),
        "к 18:00 подготовить коммерческое предложение" to Exp(due = "*", time = "18:00", word = "предложени"),
        "в понедельник в 11 планёрка с командой" to Exp(due = o(12), time = "11:00", word = "планёрка"),
        "во вторник отвезти ноутбук в ремонт" to Exp(due = o(13), word = "ноутбук"),
        "в среду в 14:30 встреча с заказчиком" to Exp(due = o(14), time = "14:30", word = "заказчик"),
        "в субботу в 12 дня бранч с Машей" to Exp(due = o(10), time = "12:00", person = "Маша"),
        "в воскресенье съездить к родителям" to Exp(due = o(11), word = "родител"),
        "5 ноября продлить домен" to Exp(due = nv(5), word = "домен"),
        "продлить хостинг до 1 ноября" to Exp(due = nv(1), word = "хостинг"),
        "25.10 день рождения Олега" to Exp(due = o(25), person = "Олег"),
        "оплатить интернет 20.10" to Exp(due = o(20), word = "интернет"),
        "через 2 дня напомнить Диме про макет" to Exp(due = o(10), person = "Дима"),
        "через месяц продлить подписку на Figma" to Exp(due = nv(8), word = "подписк"),
        "через три дня отправить отчёт" to Exp(due = o(11), word = "отчёт"),
        "на этой неделе разобрать почту" to Exp(due = o(11), word = "почт"),
        "в следующий вторник обед с Катей" to Exp(due = o(13), person = "Катя"),
        "в следующую пятницу сдать финальную версию сайта" to Exp(due = o(16), word = "сайт"),
        "к понедельнику подготовить смету" to Exp(due = o(12), word = "смет"),
        "до пятницы согласовать бюджет" to Exp(due = o(9), word = "бюджет"),
        "к 20 октября подготовить презентацию для инвесторов" to Exp(due = o(20), word = "презентац"),
        "до 31 октября подать заявку на грант" to Exp(due = o(31), word = "грант"),
        "сегодня отправить инвойс" to Exp(due = o(8), word = "инвойс"),
        "завтра купить подарок Насте" to Exp(due = o(9), person = "Настя", word = "подарок"),
        "в эту субботу генеральная уборка" to Exp(due = o(10), word = "уборка"),
        "до 10-го оплатить налоги" to Exp(due = o(10), word = "налог"),
        "к первому числу подготовить акт" to Exp(due = nv(1), word = "акт"),
        "15 октября в 10:00 вебинар по продажам" to Exp(due = o(15), time = "10:00", word = "вебинар"),
        "в пятницу к 17:00 отправить финальные файлы" to Exp(due = o(9), time = "17:00", word = "файлы"),
        "завтра днём заехать в типографию" to Exp(due = o(9), part = "day", word = "типограф"),
        "в пятницу после обеда созвон с бухгалтером" to Exp(due = o(9), part = "afternoon", word = "бухгалтер"),
        "сегодня ночью залить обновление на сервер" to Exp(due = o(8), part = "night", word = "сервер"),
        "в четверг в 8 созвон с Лёшей" to Exp(due = o(15), time = "08:00", person = "Лёша"),
        "сегодня в 16 забрать сына из школы" to Exp(due = o(8), time = "16:00", word = "сына"),
        "в 15:30 позвонить в банк" to Exp(due = "*", time = "15:30", word = "банк"),
        "завтра в 10 часов стоматолог" to Exp(due = o(9), time = "10:00", word = "стоматолог"),
        "в пятницу в 19.00 кино" to Exp(due = o(9), time = "19:00", word = "кино"),
        "завтра в 6 вечера тренировка" to Exp(due = o(9), time = "18:00", word = "тренировк"),
        "в понедельник в полдень созвон с клиентом" to Exp(due = o(12), time = "12:00", word = "клиент"),
        "в среду в 3 дня встреча в коворкинге" to Exp(due = o(14), time = "15:00", word = "коворкинг"),
        "завтра в 8:30 сдать кровь" to Exp(due = o(9), time = "08:30", word = "кровь"),
        "в субботу в 11 стрижка" to Exp(due = o(10), time = "11:00", word = "стрижка"),
        "13 октября в 18:30 мастер-класс по керамике" to Exp(due = o(13), time = "18:30", word = "керамик"),
        "в понедельник утром отправить договор на подпись" to Exp(due = o(12), part = "morning", word = "договор"),
        "к вечеру пятницы закрыть все правки" to Exp(due = o(9), part = "evening", word = "правки"),
        "до среды принять работу у верстальщика" to Exp(due = o(14), word = "верстальщик"),
        "16 октября сдать проект" to Exp(due = o(16), word = "проект"),
        "в пятницу 16-го презентация у клиента" to Exp(due = o(16), word = "презентац"),
        "забронировать переговорку на вторник на 14:00" to Exp(due = o(13), time = "14:00", word = "переговорк"),
        "отправить резюме до 20.10" to Exp(due = o(20), word = "резюме"),
        "записаться к врачу на 22 октября" to Exp(due = o(22), word = "врач"),
        "через пару дней напомнить про смету" to Exp(due = o(10), word = "смет"),
        "к концу недели доделать лендинг" to Exp(due = o(11), word = "лендинг"),

        // ---- repeats ----
        "каждый вторник в 10:00 планёрка" to Exp(repeat = "weekly:2", due = o(13), time = "10:00", word = "планёрка"),
        "по пн и чт бассейн в 8 утра" to Exp(repeat = "weekly:1,4", due = o(12), time = "08:00", word = "бассейн"),
        "каждый день пить витамины" to Exp(repeat = "daily", word = "витамин"),
        "каждое утро зарядка" to Exp(repeat = "daily", part = "morning", word = "зарядка"),
        "ежемесячно 5-го оплачивать аренду" to Exp(repeat = "monthly", due = nv(5), word = "аренд"),
        "каждую пятницу отправлять отчёт заказчику" to Exp(repeat = "weekly:5", due = o(9), word = "отчёт"),
        "по понедельникам и средам английский в 19:00" to Exp(repeat = "weekly:1,3", due = o(12), time = "19:00", word = "английск"),
        "каждый месяц 10-го платить за телефон" to Exp(repeat = "monthly", due = o(10), word = "телефон"),
        "раз в неделю созвон с Димой по проекту" to Exp(repeat = "weekly", person = "Дима"),
        "по будням в 9 разбирать почту" to Exp(repeat = "weekly:1,2,3,4,5", due = o(9), time = "09:00", word = "почт"),
        "каждую субботу уборка" to Exp(repeat = "weekly:6", due = o(10), word = "уборка"),
        "каждый год 12 марта поздравить Олега" to Exp(repeat = "yearly", due = "2027-03-12", person = "Олег"),
        "каждые две недели по средам созвон с Анной" to Exp(repeat = "weekly/2:3", due = o(14), person = "Анна"),
        "по выходным пробежка" to Exp(repeat = "weekly:6,7", due = o(10), word = "пробежка"),
        "ежедневно в 22:00 читать" to Exp(repeat = "daily", due = o(8), time = "22:00", word = "читать"),
        "по вторникам и четвергам в 18 теннис" to Exp(repeat = "weekly:2,4", due = o(8), time = "18:00", word = "теннис"),
        "каждый вечер гулять с собакой" to Exp(repeat = "daily", part = "evening", word = "собак"),
        "первого числа каждый месяц выставлять счета" to Exp(repeat = "monthly", due = nv(1), word = "счета"),
        "каждый понедельник в 9 планировать неделю" to Exp(repeat = "weekly:1", due = o(12), time = "09:00", word = "планировать"),
        "раз в месяц бэкап сайта" to Exp(repeat = "monthly", word = "бэкап"),
        "по средам забирать овощи у фермера" to Exp(repeat = "weekly:3", due = o(14), word = "овощи"),
        "каждый день в 9 зарядка" to Exp(repeat = "daily", due = o(9), time = "09:00", word = "зарядка"),
        "ежегодно 1 декабря продлевать страховку" to Exp(repeat = "yearly", due = "2026-12-01", word = "страховк"),
        "каждый четверг в 16 отчёт по рекламе" to Exp(repeat = "weekly:4", due = o(8), time = "16:00", word = "реклам"),

        // ---- several days ----
        "в пн и ср тренировка" to Exp(days = listOf(o(12), o(14)), word = "тренировк"),
        "во вторник и в пятницу звонить маме" to Exp(days = listOf(o(9), o(13)), person = "Мама"),
        "в понедельник и среду в 19:00 курс по UX" to Exp(days = listOf("${o(12)} 19:00", "${o(14)} 19:00"), word = "курс"),
        "12 и 19 октября полить цветы у соседки" to Exp(days = listOf(o(12), o(19)), word = "цветы"),
        "полить цветы 12, 15 и 18 октября" to Exp(days = listOf(o(12), o(15), o(18)), word = "цветы"),
        "в сб и вс дежурство на выставке" to Exp(days = listOf(o(10), o(11)), word = "выставк"),
        "консультации во вторник и среду в 11" to Exp(days = listOf("${o(13)} 11:00", "${o(14)} 11:00"), word = "консультац"),
        "в пятницу и понедельник созвоны с клиентами" to Exp(days = listOf(o(9), o(12)), word = "созвон"),
        "забрать ребёнка из сада во вторник и среду" to Exp(days = listOf(o(13), o(14)), word = "сада"),

        // ---- people in different cases ----
        "позвонить маме" to Exp(person = "Мама"),
        "написать Диме про исходники" to Exp(person = "Дима"),
        "встреча с Олегом в кафе" to Exp(person = "Олег"),
        "отдать долг Лёше" to Exp(person = "Лёша", promise = "*"),
        "поздравить Катю с днём рождения" to Exp(person = "Катя"),
        "созвониться с Ириной насчёт текстов" to Exp(person = "Ирина"),
        "забрать ключи у Наташи" to Exp(person = "Наташа"),
        "отправить Сергею договор" to Exp(person = "Сергей"),
        "обсудить с Андреем логотип" to Exp(person = "Андрей"),
        "спросить у Игоря про доступы к хостингу" to Exp(person = "Игорь"),
        "показать Артёму новые эскизы" to Exp(person = "Артём"),
        "записать Никиту на стрижку" to Exp(person = "Никита"),
        "встретиться с Юлей в субботу" to Exp(due = o(10), person = "Юля"),
        "купить папе лекарства" to Exp(person = "Папа"),
        "передать Ольге флешку" to Exp(person = "Ольга"),
        "позвонить в ЖЭК насчёт счётчиков" to Exp(person = "ЖЭК"),
        "съездить к Маше за коробками" to Exp(person = "Маша"),
        "напомнить Павлу про оплату" to Exp(person = "Павел"),
        "отправить Петру фото объекта" to Exp(person = "Пётр"),
        "поблагодарить Илью за рекомендацию" to Exp(person = "Илья"),
        "созвон с Аней по бренд-буку" to Exp(person = "Аня"),
        "проверить отчёт Евгения" to Exp(person = "Евгений"),
        "отвезти Любе торт" to Exp(person = "Люба"),
        "обсудить с Анной Сергеевной ставку" to Exp(person = "Анна"),
        "пригласить Машу и Олега на ужин в пятницу" to Exp(due = o(9), person = "Олег"),
        "Лёше скинуть исходники до четверга" to Exp(due = o(15), person = "Лёша", promise = "*"),
        "отправить Анне финальный счёт" to Exp(person = "Анна"),
        "заказать упаковку у ПакетПро до среды" to Exp(due = o(14), word = "упаковк"),
        "заказать муку у Тимура каждый понедельник" to Exp(repeat = "weekly:1", due = o(12), person = "Тимур"),

        // ---- waiting ----
        "Дима пришлёт макет к пятнице" to Exp(due = o(9), wait = "Дима", word = "макет"),
        "жду от Маши ключи" to Exp(wait = "Маша", word = "ключи"),
        "Олег должен вернуть 5000 до конца месяца" to Exp(due = o(31), wait = "Олег"),
        "Анна скинет тексты завтра" to Exp(due = o(9), wait = "Анна", word = "текст"),
        "Ирина пришлёт варианты логотипа в понедельник" to Exp(due = o(12), wait = "Ирина", word = "логотип"),
        "ждём от Лёши исходники до среды" to Exp(due = o(14), wait = "Лёша", word = "исходники"),
        "Сергей обещал прислать договор во вторник" to Exp(due = o(13), wait = "Сергей", word = "договор"),
        "Катя сдаст тексты для лендинга к 20-му" to Exp(due = o(20), wait = "Катя", word = "текст"),
        "жду ответа от Олега по смете" to Exp(wait = "Олег", word = "смет"),
        "Дима должен прислать правки завтра до 12" to Exp(due = o(9), time = "12:00", wait = "Дима", word = "правки"),
        "Олег передаст пропуск на охрану в понедельник" to Exp(due = o(12), wait = "Олег", word = "пропуск"),
        "Настя подготовит смету к среде" to Exp(due = o(14), wait = "Настя", word = "смет"),
        "Маша вернёт 3000 в субботу" to Exp(due = o(10), wait = "Маша"),
        "жду от клиента предоплату до понедельника" to Exp(due = o(12), wait = "*", word = "предоплат"),
        "Анна должна подписать акт до 15-го" to Exp(due = o(15), wait = "Анна", word = "акт"),

        // ---- promises ----
        "обещал Анне отправить счёт" to Exp(promise = "Анна", word = "счёт"),
        "обещала Олегу прислать презентацию до пятницы" to Exp(due = o(9), promise = "Олег", word = "презентац"),
        "пообещал Диме посмотреть его портфолио на выходных" to Exp(due = o(10), promise = "Дима", word = "портфолио"),
        "должна Кате 500 рублей" to Exp(promise = "Катя"),
        "обещал маме заехать в воскресенье" to Exp(due = o(11), promise = "Мама", word = "заехать"),
        "я обещал Лёше скинуть референсы завтра" to Exp(due = o(9), promise = "Лёша", word = "референс"),
        "должен Олегу 3000 до 15-го" to Exp(due = o(15), promise = "Олег"),
        "обещал Маше помочь с переездом в субботу" to Exp(due = o(10), promise = "Маша", word = "переезд"),

        // ---- ideas ----
        "идея: сделать блок с ценами без «от»" to Exp(idea = true, word = "цен"),
        "идея — запустить рассылку для старых клиентов" to Exp(idea = true, word = "рассылк"),
        "мысль: портфолио в виде журнала" to Exp(idea = true, word = "журнал"),
        "Идея: подкаст про фриланс" to Exp(idea = true, word = "подкаст"),
        "идея для кофейни: стаканы с иллюстрациями" to Exp(idea = true, word = "стакан"),

        // ---- places ----
        "забрать заказ на Ленина 5" to Exp(place = "Ленина, 5", word = "заказ"),
        "записаться к стоматологу на Тверской 12" to Exp(place = "Тверской, 12", word = "стоматолог"),
        "завтра в 11 встреча на Садовой 14" to Exp(due = o(9), time = "11:00", place = "Садовой, 14", word = "встреча"),
        "отвезти документы по адресу Мира 21" to Exp(place = "Мира, 21", word = "документ"),
        "фотосессия в субботу в 10 на Пушкина 3" to Exp(due = o(10), time = "10:00", place = "Пушкина, 3", word = "фотосесси"),
        "ул. Гагарина, 8 — забрать посылку" to Exp(place = "Гагарина, 8", word = "посылк"),
        "встреча с Олегом в кафе на Арбате 10 в пятницу в 13" to Exp(due = o(9), time = "13:00", person = "Олег", place = "Арбате, 10"),

        // ---- several matters in one message ----
        "купить хлеб, ещё позвонить в ЖЭК" to Exp(n = 2, word = "хлеб"),
        "купить корм коту, позвонить маме и записаться к барберу" to Exp(n = 3, word = "корм"),
        "купить хлеб и молоко" to Exp(word = "молоко"),
        "Купить молоко\nЗабрать костюм из химчистки в субботу\nПозвонить Олегу завтра в 11" to Exp(n = 3, at = 2, due = o(9), time = "11:00", person = "Олег"),
        "1. Отправить счёт\n2. Позвонить Диме\n3. Купить бумагу для принтера" to Exp(n = 3, word = "счёт"),
        "- забрать посылку\n- оплатить свет до 10-го" to Exp(n = 2, at = 1, due = o(10), word = "свет"),
        "сегодня вечером тренировка; завтра в 10 стоматолог" to Exp(n = 2, at = 1, due = o(9), time = "10:00", word = "стоматолог"),
        "отправить макет Диме, а ещё заказать визитки" to Exp(n = 2, person = "Дима"),
        "Позвонить в банк. Записаться к врачу на пятницу" to Exp(n = 2, at = 1, due = o(9), word = "врач"),
        "купить картридж; оплатить хостинг; ответить на письма" to Exp(n = 3, word = "картридж"),
        "забрать куртку из химчистки, плюс купить батарейки" to Exp(n = 2, word = "куртк"),
        "1. Купить молоко\n2. Забрать посылку завтра\n3. Позвонить Олегу" to Exp(n = 3, at = 1, due = o(9), word = "посылк"),
        "помыть машину, заправиться и купить омывайку" to Exp(n = 3, word = "машин"),
        "написать пост в телеграм и сделать сторис" to Exp(n = 2, word = "пост"),
        "сделать бэкап и обновить плагины на сайте" to Exp(n = 2, word = "бэкап"),
        "завтра в 12 созвон с Олегом\nв пятницу отправить Анне счёт" to Exp(n = 2, at = 1, due = o(9), person = "Анна"),
        "1) купить бумагу\n2) заказать печать визиток к среде" to Exp(n = 2, at = 1, due = o(14), word = "визит"),

        // ---- colloquial, lowercase, typos, no punctuation ----
        "созвон с олегом завтра в 12" to Exp(due = o(9), time = "12:00", person = "Олег"),
        "стрижка в сб" to Exp(due = o(10), word = "стрижка"),
        "днюха у Кати в субботу" to Exp(due = o(10), person = "Катя"),
        "пазвонить маме завтра" to Exp(due = o(9), person = "Мама"),
        "встреча с машей в пн в 10" to Exp(due = o(12), time = "10:00", person = "Маша"),
        "заказать пиццу на пятницу на 19" to Exp(due = o(9), time = "19:00", word = "пицц"),
        "пт отправить отчет" to Exp(due = o(9), word = "отчет"),
        "оплатить свет до 10го" to Exp(due = o(10), word = "свет"),
        "к 5му числу закрыть акты" to Exp(due = nv(5), word = "акты"),
        "завтра к 9 в налоговую" to Exp(due = o(9), time = "09:00", word = "налогов"),
        "дима пришлет макет в пт" to Exp(due = o(9), wait = "Дима", word = "макет"),
        "обещал анне скинуть фотки" to Exp(promise = "Анна", word = "фотки"),
        "напомни купить корм коту" to Exp(word = "корм"),
        "сходить в зал вечерком" to Exp(part = "evening", word = "зал"),
        "завтра с утра в банк" to Exp(due = o(9), part = "morning", word = "банк"),
        "встреча в 11 30 завтра" to Exp(due = o(9), time = "11:30", word = "встреча"),
        "послезавра забрать заказ" to Exp(due = o(10), word = "заказ"),
        "в суботу на дачу" to Exp(due = o(10), word = "дач"),
        "звонок лёше в среду" to Exp(due = o(14), person = "Лёша"),
        "отправить кп олегу до пятн" to Exp(due = o(9), person = "Олег"),
        "зубной завтра в 18" to Exp(due = o(9), time = "18:00", word = "зубной"),
        "корпоратив в пятницу в 19" to Exp(due = o(9), time = "19:00", word = "корпоратив"),
        "ДР мамы 20 октября" to Exp(due = o(20), person = "Мама"),
        "сб в 10 мастер по стиралке" to Exp(due = o(10), time = "10:00", word = "стиралк"),
        "завтра надо кровь сдать натощак" to Exp(due = o(9), word = "кровь"),
        "узнать у Олега че там с договором" to Exp(person = "Олег", word = "договор"),
        "го в кино с таней в вс" to Exp(due = o(11), person = "Таня", word = "кино"),
        "купить билеты в кино на субботу" to Exp(due = o(10), word = "билет"),
        "в 10 вечера созвон с америкой" to Exp(due = "*", time = "22:00", word = "созвон"),
        "забрать посылку на почте завтра после 18" to Exp(due = o(9), time = "*", word = "посылк"),

        // ---- should go to the AI (sure = false) ----
        "созвонились с Игорем, приказ будет через две недели" to Exp(ai = true),
        "перенеси мастера на послезавтра" to Exp(ai = true),
        "перенеси созвон с Олегом на 16" to Exp(ai = true),
        "исправь: встреча не в среду, а в четверг" to Exp(ai = true),
        "измени время стоматолога на 17" to Exp(ai = true),
        "удали задачу про хлеб" to Exp(ai = true),
        "отмени встречу с Анной" to Exp(ai = true),
        "сделал правки по баннерам, отправил Диме" to Exp(ai = true),
        "договорились с Машей, что она пришлёт ключи в субботу" to Exp(ai = true),
        "Олег сказал, что оплата будет на следующей неделе" to Exp(ai = true),
        "Дима — наш дизайнер, его телефон +7 999 123-45-67" to Exp(ai = true),
        "почта Анны anna.k@studio.ru" to Exp(ai = true),
        "телефон мастера 8 916 555 12 34" to Exp(ai = true),
        "WhatsApp Олега +7 (912) 345-67-89" to Exp(ai = true),
        "лена до пятницы сдаёт график смен я жду ещё обещал виктору арендодателю оплату до десятого сломалась тестомесилка на садовой вызвать мастера завтра утром купить кассовую ленту" to Exp(ai = true),
        "значит так завтра с утра надо заехать в типографию забрать визитки потом к двум на встречу с заказчиком в центре а вечером ещё доделать презентацию" to Exp(ai = true),
        "мне кажется, на главной нужен калькулятор" to Exp(ai = true),
        "Дента+: Катя делает макеты главной к четвергу, Артём верстает после макетов, Ирина должна прислать тексты до среды" to Exp(ai = true),
        "Саша обещал прислать смету, через пару дней напомнить" to Exp(ai = true),
        "Катя к четвергу сдаёт макеты, Артём до среды верстает" to Exp(ai = true),
        "получил аванс от студии" to Exp(ai = true),
        "ответил клиенту по смете" to Exp(ai = true),
        "готово: отправил счёт Анне" to Exp(ai = true),
        "разбей ремонт на шаги" to Exp(ai = true),
        "поменяй стрижку на субботу" to Exp(ai = true),
        "сдвинь дедлайн по сайту на неделю" to Exp(ai = true),
        "Маша не сможет в пятницу, встречу надо перенести" to Exp(ai = true),
        "клиент отказался от второго этапа, убери задачи по нему" to Exp(ai = true),
        "уже не надо покупать подарок Кате" to Exp(ai = true),
        "вчера встречались с Олегом, обсудили новый проект, он пришлёт ТЗ в понедельник" to Exp(ai = true),
        "встреча с Олегом переносится на понедельник" to Exp(ai = true),
        "в среду я не смогу, давай всё на четверг" to Exp(ai = true),
        "выполнил задачу по логотипу" to Exp(ai = true),
        "сайт для Олега: Дима делает дизайн до 15-го, я верстаю к 20-му, Маша пишет тексты к пятнице" to Exp(ai = true),
    )

    private val ANY = "*"

    private fun describe(r: LocalParser.Result) = (if (r.sure) "" else "sure=false(${r.why}) ") + r.items.joinToString(" | ") { i ->
        listOfNotNull("«${i.title}»", i.kind.takeIf { it != "task" }, i.due?.let { "due=$it" }, i.time?.let { "t=$it" }, i.part?.let { "part=$it" },
            i.repeat?.let { (f, n, ds) -> "rep=$f${if (n != 1) "/$n" else ""}$ds" }, i.days.takeIf { it.isNotEmpty() }?.let { "days=" + it.joinToString(",") { (d, t) -> "$d${t?.let { " $it" } ?: ""}" } },
            i.people.takeIf { it.isNotEmpty() }?.let { "ppl=$it" }, i.waitingFor?.let { "wait=$it" }, i.promisedTo?.let { "prom=$it" }, i.place?.let { "place=$it" }).joinToString(" ")
    }

    /** What differs between the expectation and the parse; empty — all right. */
    private fun check(e: Exp, r: LocalParser.Result): List<String> {
        val bad = mutableListOf<String>()
        if (r.items.size != e.n) bad += "n=${r.items.size} (want ${e.n})"
        val i = r.items.getOrNull(e.at) ?: return bad + "no item #${e.at}"
        fun eq(name: String, want: String?, got: String?, strict: Boolean = true) {
            if (want == ANY || (want == null && !strict)) return
            if (want != got) bad += "$name=$got (want $want)"
        }
        eq("due", e.due, i.due?.toString(), strict = e.repeat == null && e.days == null)
        eq("time", e.time, i.time, strict = e.days == null)
        eq("part", e.part, i.part)
        eq("wait", e.wait, i.waitingFor)
        eq("promise", e.promise, i.promisedTo)
        // repeat
        val rep = i.repeat
        if (e.repeat == null) { if (rep != null) bad += "repeat=${rep.first}${rep.third} (want none)" }
        else {
            val kind = e.repeat.substringBefore('/').substringBefore(':')
            val interval = e.repeat.substringAfter('/', "").substringBefore(':').toIntOrNull()
            val wds = e.repeat.substringAfter(':', "").takeIf { it.isNotBlank() }?.split(',')?.map { it.trim().toInt() }
            if (rep == null || rep.first != kind || (interval != null && rep.second != interval) || (wds != null && rep.third.sorted() != wds.sorted()))
                bad += "repeat=${rep?.let { "${it.first}/${it.second}${it.third}" }} (want ${e.repeat})"
        }
        // days
        if (e.days == null) { if (i.days.isNotEmpty()) bad += "days=${i.days.map { it.first }} (want none)" }
        else {
            val ok = i.days.size == e.days.size && e.days.zip(i.days).all { (w, g) ->
                w.substringBefore(' ') == g.first.toString() && (!w.contains(' ') || w.substringAfter(' ') == g.second)
            }
            if (!ok) bad += "days=${i.days.map { "${it.first}${it.second?.let { t -> " $t" } ?: ""}" }} (want ${e.days})"
        }
        if (e.person != null && e.person !in i.people + listOfNotNull(i.waitingFor, i.promisedTo)) bad += "person ${e.person} missing (ppl=${i.people})"
        if ((i.kind == "idea") != e.idea) bad += "kind=${i.kind} (want ${if (e.idea) "idea" else "task"})"
        if (e.place == null) { if (i.place != null) bad += "place=${i.place} (want none)" }
        else if (i.place?.contains(e.place, ignoreCase = true) != true) bad += "place=${i.place} (want ${e.place})"
        if (e.word != null && !i.title.contains(e.word, ignoreCase = true)) bad += "title «${i.title}» lacks «${e.word}»"
        return bad
    }

    @Test fun corpus() {
        L.lang = Lang.Ru
        var correct = 0; var confident = 0; var unnecessaryAi = 0; var shouldAsk = 0
        val lines = mutableListOf<String>()
        corpus.forEach { (text, e) ->
            val phrase = text.replace("\n", " / ")
            val r = runCatching { LocalParser.parse(text, base, today = today, now = now) }.getOrElse {
                confident++; lines += "MISS [$phrase] crashed: $it"; return@forEach
            }
            if (e.ai) {
                if (!r.sure) { correct++; return@forEach }
                // A report or an edit filed as a new matter is wrong by itself: no expected fields to match.
                shouldAsk++; confident++
                lines += "MISS should have asked AI [$phrase] got ${describe(r)}"
                return@forEach
            }
            if (!r.sure) {
                unnecessaryAi++
                val bad = check(e, r)
                lines += "MISS unnecessary AI (${r.why}) [$phrase] expected $e" + (if (bad.isEmpty()) " — local result was right" else " — local: ${bad.joinToString("; ")}") + " got ${describe(r)}"
                return@forEach
            }
            val bad = check(e, r)
            if (bad.isEmpty()) correct++
            else { confident++; lines += "MISS [$phrase] expected ${bad.joinToString("; ")} got ${describe(r)}" }
        }
        val share = correct.toDouble() / corpus.size
        println("corpus: $correct/${corpus.size} correct, $confident confident errors, $unnecessaryAi unnecessary AI, $shouldAsk should have asked AI")
        lines.forEach { println(it) }
        assertTrue("correct share fell: $correct/${corpus.size}", share >= THRESHOLD)
        assertTrue("confident errors grew: $confident", confident <= MAX_CONFIDENT)
    }

    companion object {
        /** Measured on 9 Oct 2026 (211/246 correct, 35 confident errors); raise as the parser improves. */
        const val THRESHOLD = 0.95 // 9 Oct 2026: 246/246 after the parser fixes; room for real phrases added from the beta
        const val MAX_CONFIDENT = 0
    }
}
