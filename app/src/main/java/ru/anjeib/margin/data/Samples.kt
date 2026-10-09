package ru.anjeib.margin.data

import android.content.Context
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * «Примеры» (owner's call, 10 окт — in place of «Знакомство с Margin»): a few days of the young designer from the demo,
 * one example of each thing the app does and no more — a work topic with records, files, a wait, a matter from a record
 * and a matter under a matter; a matter with an hour, a late one, a repeating one with a place, one on several days,
 * a promise each way, one without a date, one done and one not done; an idea; a person and a business.
 * All are marked [Task.sample] / [Person.sample] and leave with «Убрать примеры» — nothing of them stays among the
 * user's own. Russian only, like the demo they come from.
 */
object Samples {
    /** The set as data; [brief] and [logos] are the topic's files (drawn on the phone — [withFiles]; none in the tests). */
    fun build(today: LocalDate = LocalDate.now(), brief: Attachment? = null, logos: Attachment? = null): AppData {
        fun d(n: Long) = today.plusDays(n).toString()
        fun at(n: Long, hm: String) = "${today.plusDays(n)}T$hm"
        fun ev(n: Long, hm: String, ref: String, wait: Boolean = false, entry: String? = null) =
            TopicEvent(at = at(n, hm), kind = if (wait) "wait" else "q", ref = ref, entry = entry)

        // A topic: its records with a brief and the logos, a matter done, one for today, a wait, one from a record, one under another.
        val topic = Task(
            id = "ex-coffee", title = "Айдентика для кофейни «Зерно»", topic = true, sphere = "work", createdAt = d(-12), people = listOf("Олег"), sample = true,
            entries = listOf(
                Entry(id = "ex-c-e1", at = at(-12, "11:20"), text = "Новый клиент — кофейня «Зерно» на Покровке. Нужны логотип, вывеска и упаковка для зерна.",
                    attachments = listOfNotNull(brief)),
                Entry(id = "ex-c-e2", at = at(-5, "19:40"), text = "Олег выбрал второй вариант, просит сделать зерно чуть круглее.",
                    attachments = listOfNotNull(logos?.copy(caption = "Три варианта логотипа"))),
                Entry(id = "ex-c-e3", at = at(-2, "12:10"), text = "Созвонились: упаковку печатает типография «Оттиск», им нужен макет до пятницы."),
            ),
            subtasks = listOf(
                Subtask(id = "ex-c1", title = "Сделать три варианта логотипа", done = true, doneAt = d(-6)),
                Subtask(id = "ex-c2", title = "Показать финальный логотип Олегу", due = d(0), time = "15:00"),
                Subtask(id = "ex-c3", title = "Фидбек по упаковке", from = "Олег", due = d(1)),
                Subtask(id = "ex-c4", title = "Макет упаковки для типографии", due = d(2), parent = "ex-c-e3"),
                Subtask(id = "ex-c5", title = "Подготовить брендбук", due = d(6)),
                Subtask(id = "ex-c6", title = "Типографика", parent = "ex-c5"),
            ),
            events = listOf(ev(-12, "11:25", "ex-c1", entry = "ex-c-e1"), ev(-1, "21:15", "ex-c2"), ev(-2, "12:12", "ex-c3", wait = true, entry = "ex-c-e3"),
                ev(-2, "12:13", "ex-c4"), ev(-1, "09:30", "ex-c5"), ev(-1, "09:31", "ex-c6")),
        )

        fun deal(id: String, title: String, sphere: String, born: Long) = Task(id = "ex-$id", title = title, sphere = sphere, createdAt = d(born), sample = true)
        val gymDay = (0L..6L).map { today.plusDays(it) }.first { it.dayOfWeek in setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY) }
        val deals = listOf(
            deal("milk", "Купить молоко и хлеб", "personal", 0).copy(due = d(0), time = "19:30"),
            deal("figma", "Продлить подписку Figma", "work", -9).copy(due = d(-2)),
            deal("gym", "Спортзал", "personal", -50).copy(due = gymDay.toString(), time = "08:00", place = "Покровка, 17",
                repeat = Repeat("weekly", days = listOf(1, 3, 5))),
            deal("english", "Английский с репетитором", "personal", -6)
                .copy(slots = listOf(Slot(id = "ex-s1", date = d(1), time = "19:00"), Slot(id = "ex-s2", date = d(3), time = "19:00"))).syncSlots(),
            deal("banners", "Исходники баннеров", "work", -2).copy(promisedTo = "Лёша", due = d(1)),
            deal("money", "3 000 ₽ за билеты", "personal", -5).copy(waitingFor = "Маша", due = d(4)),
            deal("cv", "Обновить резюме", "work", -15),
            deal("landing", "Сдать макеты лендинга", "work", -10).copy(due = d(-3), done = true, doneAt = d(-3)),
            deal("blender", "Курс по 3D в Blender", "work", -30).copy(done = true, dropped = true, doneAt = d(-7)),
        )

        val idea = Task(
            id = "ex-idea", title = "Пет-проект: трекер привычек для дизайнеров", topic = true, thought = true, sphere = "personal", createdAt = d(0), sample = true,
            entries = listOf(Entry(id = "ex-i-e1", at = at(0, "08:40"), text = "Каждый день — одна маленькая визуальная задача, как в Daily UI, но с напоминаниями.")),
        )

        val persons = listOf(
            Person(id = "ex-p-oleg", name = "Олег", about = "владелец кофейни", company = "Кофейня «Зерно»", phone = "+7 916 555-12-40", kept = true, sample = true),
            Person(id = "ex-p-zerno", name = "Кофейня «Зерно»", org = true, address = "Покровка, 21", kept = true, sample = true),
        )
        return AppData(tasks = listOf(topic) + deals + idea, persons = persons)
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
