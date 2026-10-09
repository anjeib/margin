package ru.anjeib.margin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.anjeib.margin.data.AppData
import ru.anjeib.margin.data.Attachment
import ru.anjeib.margin.data.Entry
import ru.anjeib.margin.data.Subtask
import ru.anjeib.margin.data.Task
import ru.anjeib.margin.data.attachment
import ru.anjeib.margin.data.questionRow
import ru.anjeib.margin.data.addComment
import ru.anjeib.margin.data.editComment
import ru.anjeib.margin.data.setOcr
import ru.anjeib.margin.data.Person
import ru.anjeib.margin.data.setPersonField
import ru.anjeib.margin.ui.Reach

/** 0.18: what a tap opens in the words, the comment on a matter, the text recognised on a photo. */
class TouchTest {
    private fun kinds(text: String) = Reach.spans(text).map { (r, k) -> k to text.substring(r) }

    @Test fun phonesMailsLinksHandles() {
        assertEquals(listOf("phone" to "+7 903 111-22-33"), kinds("позвонить +7 903 111-22-33 вечером"))
        assertEquals(listOf("phone" to "8 (912) 345-67-89"), kinds("тел 8 (912) 345-67-89"))
        assertEquals(listOf("email" to "oleg@zerno.ru"), kinds("смета на oleg@zerno.ru"))
        assertEquals(listOf("url" to "t.me/oleg_zerno"), kinds("канал t.me/oleg_zerno"))
        assertEquals(listOf("url" to "https://instagram.com/zerno.coffee"), kinds("смотри https://instagram.com/zerno.coffee"))
        assertEquals(listOf("handle" to "@lesha_art"), kinds("написать @lesha_art"))
        // An address in mail is not a handle; dates and sums are not phones.
        assertEquals(listOf("email" to "masha@mail.ru"), kinds("masha@mail.ru"))
        assertEquals(emptyList<Pair<String, String>>(), kinds("вернуть 3000 рублей к 15.10"))
    }

    @Test fun commentsOnAMatterAndOnATopicsMatter() {
        val q = Subtask(id = "q", title = "Спецификация API", from = "Дима")
        val d = AppData(tasks = listOf(Task(id = "t", title = "Купить хлеб"), Task(id = "z", title = "Редизайн", topic = true, subtasks = listOf(q))))
        val a = d.addComment("t", "  бородинский  ", "2026-10-09T17:50").addComment("q:z:q", "в Notion", "2026-10-09T18:00").addComment("t", "   ", "2026-10-09T18:01")
        val mine = a.tasks.first { it.id == "t" }.comments
        assertEquals(listOf("бородинский"), mine.map { it.text })
        assertEquals("2026-10-09T17:50", mine.single().at)
        val topic = a.tasks.first { it.id == "z" }
        assertEquals("в Notion", topic.subtasks.single().comments.single().text)
        assertEquals("в Notion", topic.questionRow(topic.subtasks.single()).comments.single().text)
        val edited = a.editComment("t", mine.single().id, "белый")
        assertEquals("белый", edited.tasks.first { it.id == "t" }.comments.single().text)
        assertTrue(a.editComment("t", mine.single().id, " ").tasks.first { it.id == "t" }.comments.isEmpty())
    }

    /** 0.26: the one old comment becomes the first dated comment. */
    @Test fun oldCommentMovesIntoTheLane() {
        val d = AppData(tasks = listOf(Task(id = "t", title = "Купить хлеб", note = "бородинский", createdAt = "2026-10-05"),
            Task(id = "z", title = "Редизайн", topic = true, subtasks = listOf(Subtask(id = "q", title = "Макет", note = "в Figma"))))).migrated()
        val t = d.tasks.first { it.id == "t" }
        assertEquals(null, t.note); assertEquals("бородинский", t.comments.single().text); assertEquals("2026-10-05T09:00", t.comments.single().at)
        assertEquals("в Figma", d.tasks.first { it.id == "z" }.subtasks.single().comments.single().text)
    }

    @Test fun textOnAPhotoWhereverItIs() {
        val ph = Attachment(id = "ph", kind = "photo", file = "a.jpg")
        val d = AppData(tasks = listOf(Task(id = "t", title = "Чек", entries = listOf(Entry(id = "e", text = "", attachments = listOf(ph))))))
        val a = d.setOcr("ph", "ИНН 7701234567")
        assertEquals("ИНН 7701234567", a.attachment("ph")?.ocr)
        assertNull(a.setOcr("ph", " ").attachment("ph")?.ocr)
    }

    @Test fun oneFieldForEveryNetwork() {
        assertEquals("Instagram" to "zerno.coffee", Reach.socialName("instagram.com/zerno.coffee"))
        assertEquals("VK" to "olegzerno", Reach.socialName("vk.com/olegzerno"))
        assertEquals("zerno.ru" to "", Reach.socialName("zerno.ru"))
        var d = AppData(persons = listOf(Person(id = "p", name = "Олег")))
        d = d.setPersonField("p", "social", "https://www.instagram.com/zerno.coffee/").setPersonField("p", "social", "vk.com/olegzerno")
        assertEquals(listOf("instagram.com/zerno.coffee", "vk.com/olegzerno"), d.persons.single().social)
        assertEquals(listOf("vk.com/olegzerno"), d.setPersonField("p", "social:0", " ").persons.single().social)
    }
}
