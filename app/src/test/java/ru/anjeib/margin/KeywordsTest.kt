package ru.anjeib.margin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ru.anjeib.margin.ai.Commands
import ru.anjeib.margin.ai.LocalParser
import ru.anjeib.margin.data.AppData
import ru.anjeib.margin.data.Person
import ru.anjeib.margin.data.Task
import ru.anjeib.margin.i18n.L
import ru.anjeib.margin.i18n.Lang

/** «дело / тема / идея» said any way, and names written right (owner, 12 окт). */
class KeywordsTest {
    init { L.lang = Lang.Ru }
    private val d = AppData(tasks = listOf(Task(id = "bath", title = "Ремонт ванной", topic = true)),
        persons = listOf(Person(name = "Маша"), Person(name = "ЖЭК", org = true)))
    private fun one(t: String) = LocalParser.parse(t, d).items.single().let { it.kind to it.title }

    @Test fun ideas() {
        listOf("Идея: блог о дизайне", "идея блог о дизайне", "Идея. Блог о дизайне", "запиши идею блог о дизайне", "новая идея — блог о дизайне")
            .forEach { assertEquals(it, "idea" to "Блог о дизайне", one(it)) }
        assertEquals("idea" to "Сделать блог о дизайне", one("у меня идея сделать блог о дизайне"))
        assertEquals("idea" to "Переставить диван", one("Есть идея, переставить диван."))
    }

    @Test fun matters() {
        listOf("дело купить хлеб", "Дело: купить хлеб", "Дело. Купить хлеб", "новое дело купить хлеб", "задача купить хлеб")
            .forEach { assertEquals(it, "task" to "Купить хлеб", one(it)) }
        assertEquals("купить хлеб", Commands.parse("добавь дело купить хлеб")?.value)
        // «дело в том, что…» is no command.
        assertEquals("task", one("дело в том, что сайт не готов").first)
    }

    @Test fun topics() {
        listOf("Тема: переезд", "тема переезд", "новая тема переезд", "создай тему переезд", "заведи проект переезд")
            .forEach { assertEquals(it, "Переезд", Commands.newTopic(it)) }
        assertNull(Commands.newTopic("тема для поста про кофе"))
        val topics = d.tasks.map { it.id to it.title }
        assertEquals("bath" to "купить плитку", Commands.intoTopic("в тему ремонт ванной купить плитку", topics))
        assertEquals("bath" to "купить плитку", Commands.intoTopic("В теме ремонт ванной: купить плитку", topics))
        assertEquals("bath" to "смету", Commands.intoTopic("к теме ремонт ванной добавь смету", topics))
        assertNull(Commands.intoTopic("в тему переезд купить коробки", topics))
    }

    /** Owner, 16 окт: «Добавь в тему/дело "Список дел по Дмитрову" …» said by voice lands exactly there. */
    @Test fun intoPlace() {
        val topics = listOf("dm" to "Список дел по Дмитрову", "bath" to "Ремонт ванной")
        val matters = listOf("bank" to "Позвонить в банк", "q:dm:keys" to "Забрать ключи")
        fun at(t: String) = Commands.intoPlace(t, topics, matters)?.let { Triple(it.id, it.said, it.topic) }
        assertEquals(Triple("dm", "купить краску", true), at("Добавь в тему \"Список дел по Дмитрову\" купить краску"))
        assertEquals(Triple("dm", "купить краску", true), at("добавь в тему «список дел по дмитрову»: купить краску"))
        assertEquals(Triple("dm", "забор красим в субботу", true), at("запиши в тему списка дел по Дмитрову забор красим в субботу"))
        // A topic called a matter is still found; a matter called a topic too.
        assertEquals(Triple("dm", "сосед дал телефон электрика", true), at("Добавь в дело Список дел по Дмитрову сосед дал телефон электрика"))
        assertEquals(Triple("bank", "номер договора 123", true.not()), at("Добавь в дело позвонить в банк: номер договора 123"))
        assertEquals(Triple("q:dm:keys", "у Сергея", false), at("в задачу забрать ключи — у Сергея"))
        assertEquals(Triple("bank", "спросить про кэшбэк", false), at("Добавь в тему позвонить в банк спросить про кэшбэк"))
        // Nothing after the name, or a name that isn't there — not this.
        assertNull(at("Добавь в тему Список дел по Дмитрову"))
        assertNull(at("добавь в тему переезд купить коробки"))
        assertNull(at("для дела нужен паспорт"))
    }

    @Test fun names() {
        assertEquals("Позвонить Маше", one("позвонить маше завтра").second)
        assertEquals("Позвонить в ЖЭК насчёт счётчиков", one("позвонить в жэк насчёт счётчиков").second)
        assertEquals("Встреча с Иваном Петровым", one("встреча с иваном петровым в пятницу").second)
    }
}
