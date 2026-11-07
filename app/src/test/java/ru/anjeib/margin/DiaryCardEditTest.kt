package ru.anjeib.margin

import org.junit.Assert.*
import org.junit.Test
import ru.anjeib.margin.data.*

class DiaryCardEditTest {
    private val photo = Attachment(id = "photo", kind = "photo", file = "photo.jpg")
    private val source = Attachment(id = "source", kind = "link", url = "https://old.example/page", name = "Old")
    private val entry = Entry(id = "entry", text = "Text\nhttps://old.example/page", attachments = listOf(photo, source), tags = listOf("old.example", "work"))
    private val note = Task(id = "note", title = "Title", thought = true, topic = true, entries = listOf(entry))
    private fun data(t: Task = note) = AppData(tasks = listOf(t))

    @Test fun editsAllFieldsWithoutChangingIdentityOrDate() {
        val file = Attachment(id = "file", kind = "file", file = "brief.pdf")
        val changed = entry.changeSource("source", "https://new.example/read").removeCardAttachment("photo").copy(description = "Important", attachments = listOf(source.copy(url = "https://new.example/read"), file))
        val draft = DiaryCardDraft.of(note).copy(title = "New title", tags = "work, research", entries = listOf(changed))
        val saved = data().saveDiaryCard(draft).tasks.single()
        val e = saved.entries.single()
        assertEquals("note", saved.id)
        assertEquals("New title", saved.title)
        assertEquals(entry.at, e.at)
        assertEquals("Important", e.description)
        assertEquals(listOf("work", "research", "new.example"), e.tags)
        assertTrue(e.text.contains("https://new.example/read"))
        assertFalse(e.attachments.any { it.id == "photo" })
        assertEquals("brief.pdf", e.attachments.last().file)
        assertNotEquals("source", e.attachments.first().id)
        assertFalse(e.attachments.first().previewLoaded)
    }

    @Test fun removingOnlyPhotoKeepsTheNoteAndItsLabels() {
        val t = note.copy(entries = listOf(Entry(id = "image", attachments = listOf(photo), tags = listOf("idea"))))
        val draft = DiaryCardDraft.of(t).copy(entries = t.entries.map { it.removeCardAttachment("photo") })
        val saved = data(t).saveDiaryCard(draft).tasks.single()
        assertEquals("note", saved.id)
        assertEquals("image", saved.entries.single().id)
        assertTrue(saved.entries.single().attachments.isEmpty())
        assertEquals(listOf("idea"), saved.entries.single().tags)
    }

    @Test fun cancelDoesNotChangeTheOriginalOrItsFiles() {
        val original = data()
        DiaryCardDraft.of(note).copy(title = "Draft", entries = listOf(entry.removeCardAttachment("photo")))
        assertEquals(note, original.tasks.single())
        assertEquals("photo.jpg", original.tasks.single().entries.single().attachments.first().file)
    }

    @Test fun keepsPreviewAndNewRecordsThatArrivedDuringEditing() {
        val draft = DiaryCardDraft.of(note).copy(title = "My title")
        val ready = source.copy(name = "Fetched title", file = "preview.jpg", previewLoaded = true)
        val extra = Entry(id = "new", text = "New record")
        val latest = note.copy(entries = listOf(entry.copy(attachments = listOf(photo, ready)), extra))
        val saved = data(latest).saveDiaryCard(draft).tasks.single()
        assertEquals("My title", saved.title)
        assertEquals(ready, saved.entries.first().attachments.last())
        assertEquals(extra, saved.entries.last())
    }

    @Test fun manualLinkTitleKeepsThePreviewThatFinishedWhileEditing() {
        val renamed = source.copy(name = "My label", titleFromUser = true)
        val draft = DiaryCardDraft.of(note).copy(entries = listOf(entry.copy(attachments = listOf(photo, renamed))))
        val ready = source.copy(name = "Fetched", file = "preview.jpg", previewLoaded = true)
        val latest = note.copy(entries = listOf(entry.copy(attachments = listOf(photo, ready))))
        val saved = data(latest).saveDiaryCard(draft).tasks.single().entries.single().attachments.last()
        assertEquals("My label", saved.name)
        assertEquals("preview.jpg", saved.file)
        assertTrue(saved.titleFromUser)
    }

    @Test fun rejectsInvalidLinksAndNeverResurrectsDeletedNotes() {
        val invalid = DiaryCardDraft.of(note).copy(entries = listOf(entry.changeSource("source", "javascript:bad")))
        assertEquals(data(), data().saveDiaryCard(invalid))
        assertTrue(AppData().saveDiaryCard(DiaryCardDraft.of(note)).tasks.isEmpty())
    }

    @Test fun clearingTextAndTagsDoesNotDeleteTheNote() {
        val draft = DiaryCardDraft.of(note).copy(tags = "", entries = listOf(entry.copy(text = "", attachments = emptyList(), description = "")))
        val saved = data().saveDiaryCard(draft).tasks.single()
        assertTrue(saved.entries.single().text.isEmpty())
        assertTrue(saved.entries.single().tags.isEmpty())
        assertEquals("Title", saved.title)
    }

    @Test fun anEmptyNoteCanReceiveFirstAttachment() {
        val empty = note.copy(entries = emptyList())
        val initial = DiaryCardDraft.of(empty)
        val draft = initial.copy(tags = "test", entries = listOf(initial.entries.single().copy(attachments = listOf(photo))))
        val saved = data(empty).saveDiaryCard(draft).tasks.single().entries.single()
        assertEquals(listOf(photo), saved.attachments)
        assertEquals(listOf("test"), saved.tags)
    }

    @Test fun changedSourceDoesNotLeaveTheOldAddressInText() {
        val changed = entry.changeSource("source", "https://new.example").removeCardAttachment("source")
        assertFalse(changed.text.contains("old.example"))
        assertFalse(changed.text.contains("new.example"))
        assertEquals("Text", changed.text)
    }

    @Test fun unifiedFieldFindsLinksAndTagsAndKeepsFiles() {
        val draft = DiaryCardDraft.of(note).withMainText("A new note\nhttps://new.example/read\n#research #ideas")
        val saved = data().saveDiaryCard(draft).tasks.single().entries.single()
        assertEquals("A new note\nhttps://new.example/read", saved.text)
        assertEquals(listOf("work", "research", "ideas", "new.example"), saved.tags)
        assertTrue(saved.attachments.any { it.id == photo.id })
        assertEquals(listOf("https://new.example/read"), saved.attachments.filter { it.kind == "link" }.map { it.url })
    }

    @Test fun deletingLinkFromUnifiedFieldRemovesItsCardAndSourceTag() {
        val saved = data().saveDiaryCard(DiaryCardDraft.of(note).withMainText("Only text")).tasks.single().entries.single()
        assertTrue(saved.attachments.none { it.kind == "link" })
        assertEquals(listOf("work"), saved.tags)
        assertEquals(listOf(photo), saved.attachments)
    }

    @Test fun untouchedUnifiedFieldKeepsRecordStructureAndDescription() {
        val t = note.copy(entries = listOf(entry.copy(description = "Description"), Entry(text = "Second record")))
        val draft = DiaryCardDraft.of(t)
        assertEquals(draft, draft.withMainText(draft.mainText()))
        assertEquals(t, data(t).saveDiaryCard(draft).tasks.single())
    }

    @Test fun inlineLineEditAndDeletionKeepOtherLinesAndAttachments() {
        val original = entry.copy(text = "First\nSecond\nThird")
        val changed = original.replaceNoteLine(1, "Edited").replaceNoteLine(0, null)
        assertEquals("Edited\nThird", changed.text)
        assertEquals(original.attachments, changed.attachments)
        assertEquals(original.tags, changed.tags)
        assertEquals(original.id, changed.id)
        assertEquals(changed, changed.replaceNoteLine(20, null))
    }

    @Test fun listStartsAtOneAndNeverAddsADuplicateNumber() {
        assertEquals("1. Read the page", numberedNoteLine("Read the page"))
        assertEquals("1. Read the page", numberedNoteLine("1. Read the page"))
        assertEquals("2) Next", numberedNoteLine("2) Next"))
        val numbered = source.copy(numbered = true)
        assertEquals(numbered, AppJson.decodeFromString(Attachment.serializer(), AppJson.encodeToString(Attachment.serializer(), numbered)))
    }
}
