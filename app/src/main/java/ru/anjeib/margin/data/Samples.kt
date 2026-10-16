package ru.anjeib.margin.data

import android.content.Context
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * The first page (owner, 16 окт: «пустота напрягает при первом знакомстве»): a few things that are at once examples and a short
 * story of Margin — a topic «Знакомство с Margin» whose notes tell how it works, and matters each showing one thing (the knot,
 * the long press, a wait and a promise, a repeat with a place, several days, a window, no date, done, «не буду») in its own
 * words; a note of the diary about the diary. They come once, on a fresh start, as ordinary records — not marked, nothing in
 * Settings takes them away or brings them back: they are put away or archived like anything else. Russian only.
 */
object Samples {
    /** The set as data; [sheet] and [plan] are the course's files (drawn on the phone — [withFiles]; none in the tests). */
    fun build(today: LocalDate = LocalDate.now(), sheet: Attachment? = null, plan: Attachment? = null, photo: Attachment? = null): AppData {
        fun d(n: Long) = today.plusDays(n).toString()
        fun at(n: Long, hm: String) = "${today.plusDays(n)}T$hm"
        fun ev(n: Long, hm: String, ref: String, wait: Boolean = false, entry: String? = null) =
            TopicEvent(at = at(n, hm), kind = if (wait) "wait" else "q", ref = ref, entry = entry)

        // The course is itself a topic: lessons as its matters (one with steps under it), its notes as lessons in words, its files.
        val course = Task(
            id = "ex-course", title = "Курс освоения Margin", topic = true, sphere = "personal", createdAt = d(-2), people = listOf("Катя"),
            entries = listOf(
                Entry(id = "ex-c-e1", at = at(-2, "10:00"), text = "Это курс из пяти уроков — и заодно пример темы. Тема собирает одно большое дело: " +
                    "его дела, поддела, заметки и файлы. Уроки — дела темы сверху, заметки — по дням ниже.", attachments = listOfNotNull(plan?.copy(caption = "План курса"))),
                Entry(id = "ex-c-e2", at = at(-1, "18:30"), text = "Урок 1 · Как писать. В поле внизу — как говорите: «завтра в 15 созвон с Катей», " +
                    "«Маша пришлёт договор до пятницы». День, время, люди и кто кому должен встанут сами. Голосом — то же: держите микрофон.",
                    attachments = listOfNotNull(sheet)),
                Entry(id = "ex-c-e4", at = at(-1, "19:40"), text = "Урок 5 · Файлы. Фото, PDF и голос кладутся в заметку скрепкой в поле и все вместе видны во «Файлах» темы. " +
                    "Вот фото с первого занятия.", attachments = listOfNotNull(photo?.copy(caption = "Первое занятие"))),
                Entry(id = "ex-c-e3", at = at(0, "08:30"), text = "Урок 3 · Как писать в тему. Скажите «Добавь в тему Курс освоения Margin …» — слова лягут сюда. " +
                    "Так же «Добавь в дело …»."),
            ),
            subtasks = listOf(
                Subtask(id = "ex-c1", title = "Урок 0 · Установить Margin", done = true, doneAt = d(-1)),
                Subtask(id = "ex-c2", title = "Урок 1 · Нажать на кружок на линии — дело сделано", due = d(0), time = "10:00"),
                Subtask(id = "ex-c3", title = "Урок 2 · Домашнее задание от Кати — то, что ждём, видно сверху «Сегодня»", from = "Катя", due = d(1)),
                Subtask(id = "ex-c4", title = "Урок 3 · Сказать голосом: «Добавь в тему Курс освоения Margin…»", due = d(2), parent = "ex-c-e3"),
                Subtask(id = "ex-c5", title = "Урок 4 · Поддела: разбить дело на шаги", due = d(3)),
                Subtask(id = "ex-c6", title = "Шаг · поддело — маленькое кольцо под своим делом", parent = "ex-c5"),
                Subtask(id = "ex-c7", title = "Шаг · отметить его этим кольцом", parent = "ex-c5"),
                Subtask(id = "ex-c8", title = "Урок 5 · Открыть шпаргалку во «Файлах» темы", due = d(5)),
            ),
            events = listOf(ev(-1, "18:00", "ex-c1"), ev(-1, "21:15", "ex-c2"), ev(-1, "18:31", "ex-c3", wait = true, entry = "ex-c-e2"),
                ev(0, "08:31", "ex-c4", entry = "ex-c-e3"), ev(-1, "09:30", "ex-c5"), ev(-1, "09:31", "ex-c6"), ev(-1, "09:32", "ex-c7"), ev(-1, "09:33", "ex-c8")),
        )

        fun deal(id: String, title: String, born: Long, note: String? = null) = Task(id = "ex-$id", title = title, sphere = "personal", createdAt = d(born),
            comments = listOfNotNull(note?.let { Comment(id = "ex-$id-n", at = at(born.coerceAtMost(0), "09:00"), text = it) }))
        val classDay = (0L..6L).map { today.plusDays(it) }.first { it.dayOfWeek in setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY) }
        val deals = listOf(
            deal("hold", "Удержите строку — откроется меню: отложить, время, в тему", 0,
                "Час стоит на полях. В карточке нажмите на него — поменяете время.").copy(due = d(0), time = "19:00"),
            deal("late", "Просроченное помнит, с какого дня ждёт", -3).copy(due = d(-2)),
            deal("class", "Занятие курса: повтор и место", -20,
                "«Каждый понедельник, среду и пятницу в 18» — повтор понятен из слов. Адрес нажимается — откроется карта.")
                .copy(due = classDay.toString(), time = "18:00", place = "Покровка, 21", people = listOf("Катя"), repeat = Repeat("weekly", days = listOf(1, 3, 5))),
            deal("practice", "Практика: одно дело в несколько дней", -2)
                .copy(slots = listOf(Slot(id = "ex-s1", date = d(1), time = "19:00"), Slot(id = "ex-s2", date = d(3), time = "19:00"))).syncSlots(),
            deal("promise", "Конспект для Лёши — мы обещали, ладонь к нему", -1).copy(promisedTo = "Лёша", due = d(1)),
            deal("wait", "Оплата за курс от Маши — ждём, «напомнить» напишет ей", -4).copy(waitingFor = "Маша", due = d(0)),
            deal("break", "Каникулы курса: дело на период, стоит в каждом его дне", -1).copy(start = d(4), due = d(7)),
            deal("nodate", "Без даты — тоже нормально: такие дела ждут внизу «Сегодня»", -5),
            deal("done", "Сделанное зачёркнуто; нажмите на кружок — вернётся", -3).copy(due = d(-1), done = true, doneAt = d(-1)),
            deal("drop", "«Не буду» — закрыто без выполнения, честно", -6).copy(done = true, dropped = true, doneAt = d(-2)),
        )

        val note = Task(
            id = "ex-note", title = "Заметки — мысли без срока", topic = true, thought = true, sphere = "personal", createdAt = d(0),
            entries = listOf(Entry(id = "ex-n-e1", at = at(0, "09:00"), text = "Заметки — мысли без срока. Пишите их на странице «Заметки». " +
                "Заметки, написанные в деле или теме, тоже собираются там, со ссылкой на своё место. «@» отметит человека: @[Катя].")),
        )

        // People: a contact with a card and her company; those only mentioned wait under «Упомянуты» in «Контакты».
        val persons = listOf(
            Person(id = "ex-p-katya", name = "Катя", about = "ведёт курс Margin", company = "Коворкинг «Поля»", phone = "+7 916 555-12-40", kept = true, aboutFromUser = true),
            Person(id = "ex-p-place", name = "Коворкинг «Поля»", org = true, address = "Покровка, 21", kept = true),
            Person(id = "ex-p-masha", name = "Маша", draft = true),
            Person(id = "ex-p-lesha", name = "Лёша", draft = true),
        )
        return AppData(tasks = listOf(course) + deals + note, persons = persons)
    }

    /** The set with the course's files: the plan and the cheat sheet drawn on the phone, the class photo from the app's assets. */
    fun withFiles(ctx: Context, today: LocalDate = LocalDate.now()): AppData {
        val art = DemoArt(Files.dir(ctx))
        val photo = runCatching {
            val f = java.io.File(Files.dir(ctx), "ex_class.jpg")
            if (!f.exists()) ctx.assets.open("samples/class.jpg").use { i -> f.outputStream().use { i.copyTo(it) } }
            Attachment(kind = "photo", file = f.name, name = f.name, mime = "image/jpeg", size = f.length())
        }.getOrNull()
        return build(today, runCatching { art.cheatSheet }.getOrNull(), runCatching { art.coursePlan }.getOrNull(), photo)
    }
}

/** The examples are in the app (even ticked off). */
fun AppData.hasSamples() = tasks.any { it.sample } || persons.any { it.sample }

/** «Убрать примеры»: every example goes — matters, the idea, the contacts; the user's own stay as they are. */
fun AppData.withoutSamples() = copy(tasks = tasks.filterNot { it.sample }, persons = persons.filterNot { it.sample })

/** «Вернуть примеры»: a fresh set in place of any left; a contact the user already has by that name is not doubled. */
fun AppData.withSamples(set: AppData): AppData {
    val mine = withoutSamples()
    return mine.copy(tasks = set.tasks + mine.tasks, persons = mine.persons + set.persons.filter { p -> mine.persons.none { it.matches(p.name) } })
}
