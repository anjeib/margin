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

    @Test fun extractsArticleParagraphsAndReadingTimeForOfflineReaderAndSearch() {
        val html = """
            <html>
            <head>
                <meta property="og:title" content="Типографика бумажных заметок &mdash; руководство">
                <meta property="og:description" content="Как устроена полоса набора и почему засечки помогают читать длинные тексты.">
                <meta property="og:site_name" content="Журнал">
            </head>
            <body>
                <nav><p>Меню навигации по сайту которое не должно попасть в статью вообще никогда</p></nav>
                <article>
                    <h2>Полоса набора и поля</h2>
                    <p>Хорошая книжная полоса держится на ритме строк, спокойном цвете бумаги и достаточном воздухе на полях слева и справа.</p>
                    <blockquote>Бумага терпит всё, но любит порядок и ясную иерархию заголовков в каждом разделе.</blockquote>
                </article>
            </body>
            </html>
        """.trimIndent()
        val meta = PageMetadata.parse(html, "https://example.com/typography")
        assertEquals("Типографика бумажных заметок — руководство", meta.title)
        assertEquals("Как устроена полоса набора и почему засечки помогают читать длинные тексты.", meta.description)
        assertNotNull(meta.articleText)
        assertTrue(meta.articleText!!.contains("Полоса набора и поля"))
        assertTrue(meta.articleText!!.contains("Хорошая книжная полоса держится на ритме строк"))
        assertTrue(meta.articleText!!.contains("«Бумага терпит всё"))
        assertFalse(meta.articleText!!.contains("Меню навигации"))
    }

    @Test fun categorizesNotesAndFindsRelatedTopicsOnDevice() {
        val linkNote = Task(id = "n1", title = "Выбор плитки", topic = true, thought = true,
            entries = listOf(SavedMaterials.prepare(Entry(text = "https://example.com/tiles Керамогранит для ремонта кухни"))))
        val photoNote = Task(id = "n2", title = "Снимок", topic = true, thought = true,
            entries = listOf(Entry(attachments = listOf(Attachment(kind = "photo", file = "a.jpg")))))
        val thoughtNote = Task(id = "n3", title = "Мысль о шрифтах", topic = true, thought = true,
            entries = listOf(Entry(text = "Засечки лучше читаются вечером")))
        assertEquals("links", SavedMaterials.categoryOf(linkNote))
        assertEquals("media", SavedMaterials.categoryOf(photoNote))
        assertEquals("notes", SavedMaterials.categoryOf(thoughtNote))

        val kitchenTopic = Task(id = "t1", title = "Ремонт кухни", topic = true, thought = false)
        val otherTopic = Task(id = "t2", title = "Отпуск в августе", topic = true, thought = false)
        val related = SavedMaterials.relatedTopics(linkNote, listOf(kitchenTopic, otherTopic, linkNote))
        assertEquals(listOf(kitchenTopic), related)
    }

    @Test fun summarizesOtherOpenMattersBeforeContact() {
        val masha = Person(id = "p1", name = "Маша")
        val current = Task(id = "t1", title = "Позвонить Маше по смете", people = listOf("Маша"), who = mapOf("маша" to "p1"))
        val wait = Task(id = "t2", title = "Правки по договору", waitingFor = "Маша", who = mapOf("маша" to "p1"))
        val promise = Task(id = "t3", title = "Прислать презентацию", promisedTo = "Маша", who = mapOf("маша" to "p1"))
        val data = AppData(tasks = listOf(current, wait, promise), persons = listOf(masha))
        val lines = ru.anjeib.margin.ui.beforeContactLines(current.people, current.who, current.id, data)
        assertEquals(1, lines.size)
        assertEquals("Маша", lines.single().first)
        assertTrue(lines.single().second.contains("ждём 1"))
        assertTrue(lines.single().second.contains("обещано 1"))
    }
}
