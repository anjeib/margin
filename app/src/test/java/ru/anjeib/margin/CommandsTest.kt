package ru.anjeib.margin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ru.anjeib.margin.ai.Commands

/** Commands said in so many words are recognised in code — and free text is left alone. */
class CommandsTest {
    private fun what(t: String) = Commands.parse(t)?.let { it.what to it.value }

    @Test fun verbsAndObjects() {
        assertEquals("subtask" to "купить полис", what("добавь подзадачу купить полис"))
        assertEquals("subtask" to "купить полис, взять паспорт", what("Добавим подзадачи: купить полис, взять паспорт"))
        assertEquals("note" to "посмотреть Одиссею Нолана", what("Добавить заметку посмотреть Одиссею Нолана"))
        assertEquals("place" to "Ленина 5", what("добавь место Ленина 5"))
        assertEquals("fix" to "перенеси созвон на 16:00", what("Исправить: перенеси созвон на 16:00"))
        assertEquals("fix" to "время созвона 16:00", what("исправь время созвона 16:00"))
        assertEquals("contact" to "Ирина Соколова, дизайнер", what("Добавить контакт: Ирина Соколова, дизайнер"))
        assertEquals("place" to "Тверская 12", what("Укажи адрес: Тверская 12"))
        assertEquals("time" to "15:30", what("поставь время 15:30"))
        assertEquals("phone" to "+7 999 123-45-67", what("добавь телефон +7 999 123-45-67"))
        assertEquals("task" to "купить хлеб завтра", what("Новая задача: купить хлеб завтра"))
        assertEquals("list" to "молоко, хлеб", what("добавь список молоко, хлеб"))
        assertEquals("place" to "кафе Зерно", what("Место: кафе Зерно"))
    }

    @Test fun freeTextStaysFree() {
        assertNull(Commands.parse("Завтра встреча с Марией в 15 в Кофемании на Тверской"))
        assertNull(Commands.parse("дело в том, что сайт не готов"))
        assertNull(Commands.parse("сделать сайт до пятницы"))
        assertNull(Commands.parse("место встречи изменить нельзя"))
    }

    @Test fun itemsAndTimes() {
        assertEquals(listOf("Купить полис", "взять паспорт", "позвонить"), Commands.items("Купить полис, взять паспорт и позвонить"))
        assertEquals("15:00", Commands.time("в 15"))
        assertEquals("09:30", Commands.time("9.30"))
        assertEquals("19:00", Commands.time("в 7 вечера"))
        assertNull(Commands.time("после обеда"))
    }
}
