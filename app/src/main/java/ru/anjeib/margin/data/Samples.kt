package ru.anjeib.margin.data

import android.content.Context
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * The first page (owner, 16 окт: «пустота напрягает при первом знакомстве»): the course — one topic. It comes once, on a fresh
 * start, as an ordinary record — not marked; nothing in Settings takes it away or brings it back: it is archived like any topic.
 */
object Samples {
    /**
     * Owner, 18 окт: the examples are one topic, «Курс освоения Margin», on the day of the first launch — its lessons are its
     * matters for that day (no hours, so no reminders), its notes say the rest, with the course's files. Archived like any topic.
     * [sheet] and [plan] are the course's files (drawn on the phone — [withFiles]; none in the tests).
     */
    fun build(today: LocalDate = LocalDate.now(), sheet: Attachment? = null, plan: Attachment? = null, photo: Attachment? = null): AppData {
        val d0 = today.toString()
        fun at(hm: String) = "${today}T$hm"
        fun ev(hm: String, ref: String, wait: Boolean = false, entry: String? = null) =
            TopicEvent(at = at(hm), kind = if (wait) "wait" else "q", ref = ref, entry = entry)
        fun lesson(id: String, title: String, parent: String? = null, from: String? = null, done: Boolean = false) =
            Subtask(id = "ex-c$id", title = title, due = if (parent == null || parent.startsWith("ex-c-e")) d0 else null, parent = parent, from = from,
                done = done, doneAt = if (done) d0 else null)
        val course = Task(
            id = "ex-course", title = "Курс освоения Margin", topic = true, sphere = "personal", createdAt = d0, people = listOf("Катя"),
            entries = listOf(
                Entry(id = "ex-c-e1", at = at("09:00"), text = "Это курс и заодно пример темы: большое дело со своими делами, заметками и файлами. " +
                    "Отмечайте уроки кружком на линии. Освоитесь — удержите строку темы и уберите её в архив.",
                    attachments = listOfNotNull(plan?.copy(caption = "План курса"))),
                Entry(id = "ex-c-e2", at = at("09:01"), text = "Пишите и диктуйте как говорите: «завтра в 15 созвон с Катей», «Маша пришлёт договор до пятницы». " +
                    "День, время, люди и кто кому должен встанут сами. Голосовые из Telegram и WhatsApp — «Поделиться → Margin», они станут текстом.",
                    attachments = listOfNotNull(sheet)),
                Entry(id = "ex-c-e3", at = at("09:02"), text = "Заметки живут в деле или теме — как эта. Файлы и фото кладутся скрепкой в поле внизу.",
                    attachments = listOfNotNull(photo?.copy(caption = "Первое занятие"))),
            ),
            subtasks = listOf(
                lesson("1", "1. Нажмите на кружок на линии — дело сделано"),
                lesson("2", "2. Скажите в микрофон: «завтра в 10 позвонить маме»"),
                lesson("3", "3. Домашнее задание от Кати — «ждём» под делом", from = "Катя"),
                lesson("4", "4. Удержите строку дела — отложить, время, в тему"),
                lesson("5", "5. Разбейте дело на шаги"),
                lesson("6", "Шаг — маленькое кольцо под своим делом", parent = "ex-c5"),
                lesson("7", "Шаг — отметьте его этим кольцом", parent = "ex-c5"),
                lesson("8", "6. Напишите что-нибудь для себя в «Дневник» — книжка в шапке"),
            ),
            events = listOf(ev("09:00", "ex-c1"), ev("09:01", "ex-c2", entry = "ex-c-e2"), ev("09:01", "ex-c3", wait = true, entry = "ex-c-e2"),
                ev("09:02", "ex-c4"), ev("09:02", "ex-c5"), ev("09:02", "ex-c6"), ev("09:02", "ex-c7"), ev("09:03", "ex-c8")),
        )
        val persons = listOf(Person(id = "ex-p-katya", name = "Катя", about = "ведёт курс Margin", kept = true, aboutFromUser = true))
        return AppData(tasks = listOf(course), persons = persons)
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

/**
 * The examples' words from before the one screen (17 окт): «сверху «Сегодня»», «внизу «Сегодня»», the page «Заметки». Where an
 * example still has those very words, they become the new ones; anything the user changed is left as it is.
 */
fun AppData.retextSamples(): AppData {
    val words = mapOf(
        "Урок 2 · Домашнее задание от Кати — то, что ждём, видно сверху «Сегодня»" to "Урок 2 · Домашнее задание от Кати — то, что ждём, помечено «ждём» под делом",
        "Без даты — тоже нормально: такие дела ждут внизу «Сегодня»" to "Без даты — тоже нормально: такие дела стоят под сегодняшним днём, пока не сделаны",
        "Заметки — мысли без срока" to "Дневник — только для себя",
        "Это курс из пяти уроков — и заодно пример темы. Тема собирает одно большое дело: его дела, поддела, заметки и файлы. Уроки — дела темы сверху, заметки — по дням ниже."
            to "Это курс из пяти уроков — и заодно пример темы. Тема собирает одно большое дело: его дела, поддела, заметки и файлы. «Вся тема ›» откроет её окном — все уроки и заметки по дням.",
        "Урок 5 · Файлы. Фото, PDF и голос кладутся в заметку скрепкой в поле и все вместе видны во «Файлах» темы. Вот фото с первого занятия."
            to "Урок 5 · Файлы. Фото, PDF и голос кладутся в заметку скрепкой в поле и все вместе видны в «Материалах» (меню ≡). Вот фото с первого занятия.",
        "Урок 5 · Открыть шпаргалку во «Файлах» темы" to "Урок 5 · Открыть шпаргалку в «Материалах»",
        "Заметки — мысли без срока. Пишите их на странице «Заметки». Заметки, написанные в деле или теме, тоже собираются там, со ссылкой на своё место. «@» отметит человека: @[Катя]."
            to "Это дневник: записи только для себя — мысли, впечатления, итоги дня. Он отдельный: дела и темы сюда не попадают, а то, что здесь, не появляется в «Делах». «@» отметит человека: @[Катя].",
        "Это курс из пяти уроков — и заодно пример темы. Тема собирает одно большое дело: его дела, поддела, заметки и файлы. «Вся тема ›» покажет в ленте только её — уроки и заметки по дням."
            to "Это курс из пяти уроков — и заодно пример темы. Тема собирает одно большое дело: его дела, поддела, заметки и файлы. «Вся тема ›» откроет её окном — все уроки и заметки по дням.",
        "Каникулы курса: дело на период, стоит в каждом его дне" to "Каникулы курса: дело на период — в ленте стоит один раз, в первый его день",
    )
    fun w(x: String) = words[x] ?: x
    if (tasks.none { it.id.startsWith("ex-") }) return this
    return copy(tasks = tasks.map { t ->
        if (!t.id.startsWith("ex-")) t
        else t.copy(title = w(t.title), entries = t.entries.map { it.copy(text = w(it.text)) },
            subtasks = t.subtasks.map { it.copy(title = w(it.title)) })
    })
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
