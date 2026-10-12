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

    @Test fun names() {
        assertEquals("Позвонить Маше", one("позвонить маше завтра").second)
        assertEquals("Позвонить в ЖЭК насчёт счётчиков", one("позвонить в жэк насчёт счётчиков").second)
        assertEquals("Встреча с Иваном Петровым", one("встреча с иваном петровым в пятницу").second)
    }
}
