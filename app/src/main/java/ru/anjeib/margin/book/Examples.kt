package ru.anjeib.margin.book

import java.time.DayOfWeek
import java.time.LocalDate

/*
 * «Примеры»: a few days of the young designer from the demo, one example of each thing a page can show — so a new
 * notebook is never a blank form, and each line teaches by being what it is. All are [Line.sample] and leave in one go.
 * In Russian, and in English with the words translated.
 */
object Examples {
    /** The same days in English — the words translated, the shape the same (one line of each kind). */
    private val en = mapOf(
        "Кофейня «Зерно»" to "«Bean» coffee shop", "Идеи" to "Ideas",
        "Олег выбрал второй вариант, просит сделать зерно чуть круглее" to "Oliver picked the second option, asks to make the bean a bit rounder",
        "Показать финальный логотип Олегу" to "Show the final logo to Oliver", "Фидбек по упаковке" to "Packaging feedback",
        "Макет упаковки для типографии" to "Packaging layout for the printer", "Спортзал" to "Gym", "Покровка, 17" to "12 Bridge St",
        "Купить молоко и хлеб" to "Buy milk and bread", "Созвон со студией" to "Call with the studio", "Купить лампу в коридор" to "Buy a lamp for the hallway",
        "Сдать макеты лендинга" to "Hand in the landing page layouts",
        "Трекер привычек для дизайнеров: каждый день одна маленькая визуальная задача" to "Habit tracker for designers: one small visual task a day",
        "Продлить подписку Figma" to "Renew the Figma subscription", "Исходники баннеров" to "Banner source files", "3 000 ₽ за билеты" to "$30 for the tickets",
        "Английский с репетитором" to "Spanish with a tutor", "Записаться к барберу" to "Book a barber", "Курс по 3D в Blender" to "Blender 3D course",
        "Разобрать фото с отпуска" to "Sort the holiday photos",
        "Олег" to "Oliver", "Лёша" to "Alex", "Маша" to "Mary",
        "владелец кофейни «Зерно»" to "owner of the «Bean» coffee shop", "арт-директор" to "art director", "подруга" to "friend",
    )

    fun build(today: LocalDate = LocalDate.now(), ru: Boolean = true): Book {
        val b = buildRu(today)
        if (ru) return b
        fun t(x: String) = en[x] ?: x
        return b.copy(
            lines = b.lines.map { l -> l.copy(text = t(l.text), place = l.place?.let(::t), who = l.who.map { it.copy(name = t(it.name)) }) },
            threads = b.threads.map { it.copy(title = t(it.title)) },
            persons = b.persons.map { it.copy(name = t(it.name), about = it.about?.let(::t), phone = it.phone?.let { "+1 555 012 4040" }) },
        )
    }

    private fun buildRu(today: LocalDate): Book {
        fun d(n: Long) = today.plusDays(n).toString()
        fun at(n: Long, hm: String) = "${today.plusDays(n)}T$hm"
        val coffee = Thread(id = "ex-coffee", title = "Кофейня «Зерно»", sphere = "work", createdAt = at(-12, "11:20"), sample = true)
        val ideas = Thread(id = "ex-ideas", title = "Идеи", sphere = "personal", createdAt = at(-20, "22:10"), sample = true)
        val gym = (0L..6L).map { today.plusDays(it) }.first { it.dayOfWeek in setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY) }
        fun l(id: String, text: String, day: String?, born: String, kind: Kind = Kind.Todo) =
            Line(id = "ex-$id", text = text, kind = kind, day = day, writtenAt = born, sample = true)
        val lines = listOf(
            // A thread: a record, a meeting with a time, a wait, a step ahead.
            l("c1", "Олег выбрал второй вариант, просит сделать зерно чуть круглее", d(-2), at(-2, "19:40"), Kind.Note).copy(threadId = coffee.id),
            l("c2", "Показать финальный логотип Олегу", d(0), at(-1, "21:15")).copy(time = "15:00", threadId = coffee.id, who = listOf(Who("Олег"))),
            l("c3", "Фидбек по упаковке", d(1), at(-2, "12:10")).copy(threadId = coffee.id, who = listOf(Who("Олег", Dir.From))),
            l("c4", "Макет упаковки для типографии", d(2), at(-2, "12:12")).copy(threadId = coffee.id, until = d(4)),
            // The day: an hour, a repeat with a place, a meeting, a promise, a question, done, a record.
            l("gym", "Спортзал", gym.toString(), at(-30, "09:00")).copy(time = "08:00", repeat = Repeat("weekly", 1, listOf(1, 3, 5)), place = "Покровка, 17"),
            l("milk", "Купить молоко и хлеб", d(0), at(0, "08:10")).copy(time = "19:30"),
            l("call", "Созвон со студией", d(0), at(-3, "10:00"), Kind.Event).copy(time = "17:00"),
            l("lamp", "Купить лампу в коридор", d(0), at(0, "08:12")),
            l("land", "Сдать макеты лендинга", d(0), at(-4, "11:00")).copy(state = State.Done, doneAt = at(0, "10:40")),
            l("idea", "Трекер привычек для дизайнеров: каждый день одна маленькая визуальная задача", d(0), at(0, "08:40"), Kind.Note).copy(threadId = ideas.id),
            // A tail from before, a promise each way, a thing on two pages, one put off three times.
            l("figma", "Продлить подписку Figma", d(-2), at(-9, "18:00")),
            l("banners", "Исходники баннеров", d(1), at(-2, "16:00")).copy(who = listOf(Who("Лёша", Dir.To))),
            l("money", "3 000 ₽ за билеты", d(4), at(-5, "20:00")).copy(who = listOf(Who("Маша", Dir.From))),
            l("eng1", "Английский с репетитором", d(1), at(-6, "21:00")).copy(time = "19:00"),
            l("eng2", "Английский с репетитором", d(3), at(-6, "21:00")).copy(time = "19:00"),
            l("barber", "Записаться к барберу", d(2), at(-6, "09:00")).copy(trail = listOf(
                Move(from = d(-5), to = d(-3), at = at(-5, "21:30")), Move(from = d(-3), to = d(-1), at = at(-3, "21:30")),
                Move(from = d(-1), to = d(2), at = at(-1, "21:40")))),
            l("blender", "Курс по 3D в Blender", d(-3), at(-30, "22:00")).copy(state = State.Dropped, doneAt = at(-3, "21:30")),
            // «Потом»: put off on purpose.
            l("photos", "Разобрать фото с отпуска", null, at(-21, "22:00")),
        )
        val persons = listOf(
            Person(id = "ex-oleg", name = "Олег", about = "владелец кофейни «Зерно»", phone = "+7 916 555-12-40", kept = true, sample = true),
            Person(id = "ex-lesha", name = "Лёша", about = "арт-директор", telegram = "@lesha_art", kept = true, sample = true),
            Person(id = "ex-masha", name = "Маша", about = "подруга", kept = true, sample = true),
        )
        return Book(lines = lines, threads = listOf(coffee, ideas), persons = persons)
    }
}

fun Book.hasSamples() = lines.any { it.sample } || threads.any { it.sample } || persons.any { it.sample }

/** «Убрать примеры»: every example goes; the user's own lines stay — and so do threads they wrote into. */
fun Book.withoutSamples(): Book {
    val keptThreads = lines.filter { !it.sample }.mapNotNull { it.threadId }.toSet()
    return copy(lines = lines.filterNot { it.sample },
        threads = threads.filterNot { it.sample && it.id !in keptThreads },
        persons = persons.filterNot { it.sample })
}

/** «Вернуть примеры»: a fresh set in place of any left; a person the user already has by that name is not doubled. */
fun Book.withSamples(set: Book): Book {
    val mine = withoutSamples()
    return mine.copy(lines = mine.lines + set.lines, threads = mine.threads + set.threads.filter { t -> mine.threads.none { it.id == t.id } },
        persons = mine.persons + set.persons.filter { p -> mine.persons.none { it.matches(p.name) } })
}
