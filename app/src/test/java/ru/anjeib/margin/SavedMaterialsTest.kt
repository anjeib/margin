package ru.anjeib.margin

import org.junit.Assert.*
import org.junit.Test
import ru.anjeib.margin.data.*

class SavedMaterialsTest {
    @Test fun sharedPageKeepsOriginalAndCreatesSearchableSource() {
        val original = Entry(id = "shared", text = "Прочитать\nhttps://www.example.com/article?q=one#section", description = "Для ремонта")
        val saved = SavedMaterials.prepare(original)
        assertEquals(original.text, saved.text)
        assertEquals(listOf("example.com"), saved.tags)
        assertEquals("https://www.example.com/article?q=one#section", saved.attachments.single().url)
        assertTrue(saved.fullText.contains("Для ремонта"))
        assertTrue(saved.fullText.contains("example.com"))
        assertEquals("Прочитать", saved.materialBody)
        assertEquals(saved, SavedMaterials.prepare(saved))
    }

    @Test fun screenshotsAreLocalNotesWithoutInventedSource() {
        val image = Attachment(id = "image", kind = "photo", file = "image.png")
        val entry = SavedMaterials.prepare(Entry(id = "screen", attachments = listOf(image)))
        val data = SavedMaterials.add(AppData(), entry)
        val note = data.tasks.single()
        assertTrue(note.thought && note.topic)
        assertEquals(entry, note.entries.single())
        assertTrue(entry.tags.isEmpty())
        assertEquals(listOf(image), note.entries.single().attachments)
    }

    @Test fun supportedAddressesAreLimitedAndDeduplicated() {
        assertEquals(listOf("https://example.com/page"), SavedMaterials.links("(https://example.com/page). https://example.com/page"))
        assertNull(SavedMaterials.site("file:///tmp/file"))
        assertNull(SavedMaterials.site("javascript:alert(1)"))
        assertNull(SavedMaterials.site("https://user:secret@example.com"))
    }

    @Test fun labelsAreIndependentOfTopicSyntaxAndSearchable() {
        assertEquals(listOf("Дизайн", "прочитать"), SavedMaterials.tags("#Дизайн, дизайн, прочитать"))
        val entry = Entry(tags = listOf("Дизайн"), description = "Шрифт для новой страницы")
        assertTrue(entry.fullText.contains("Дизайн"))
        assertTrue(entry.fullText.contains("Шрифт"))
    }

    @Test fun oldBackupsRemainReadableAndNewFieldsSurviveRoundtrip() {
        val old = AppJson.decodeFromString(Entry.serializer(), """{"id":"old","text":"Запись"}""")
        assertEquals(emptyList<String>(), old.tags)
        assertEquals("", old.description)
        val entry = SavedMaterials.prepare(old.copy(text = "https://example.com", description = "Полезная статья"))
        assertEquals(entry, AppJson.decodeFromString(Entry.serializer(), AppJson.encodeToString(Entry.serializer(), entry)))
    }

    @Test fun movingToTopicKeepsLabelsSourceDescriptionAndIdentity() {
        val entry = SavedMaterials.prepare(Entry(id = "shared", text = "https://example.com", tags = listOf("важное"), description = "Нужна для встречи"))
        val data = AppData(notes = listOf(Note(id = "inbox", title = "Входящие", entries = listOf(entry))), tasks = listOf(Task(id = "topic", title = "Работа", topic = true)))
        val moved = data.inboxToTopic("inbox", "shared", "topic")
        assertTrue(moved.notes.single().entries.isEmpty())
        assertEquals(entry, moved.tasks.single().entries.single())
    }
}
