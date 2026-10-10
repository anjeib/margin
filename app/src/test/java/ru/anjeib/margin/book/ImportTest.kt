package ru.anjeib.margin.book

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.anjeib.margin.data.AppData
import ru.anjeib.margin.data.Comment
import ru.anjeib.margin.data.Samples
import java.time.LocalDate

/** Margin 0.29's notebook read into «Поля»: each kind of thing lands where the new notebook keeps it. */
class ImportTest {
    private val today = LocalDate.of(2026, 10, 10)
    // The 0.29 examples as if they were the user's own.
    private val old: AppData = Samples.build(today).let { d ->
        d.copy(tasks = d.tasks.map { it.copy(sample = false) }, persons = d.persons.map { it.copy(sample = false) })
    }
    private val book = Import.from(old, today)
    private fun line(id: String) = book.line(id)

    @Test fun aTopicIsAThreadWithItsMattersAndRecords() {
        val t = book.thread("ex-coffee")
        assertNotNull(t)
        assertEquals("Айдентика для кофейни «Зерно»", t!!.title)
        assertEquals(State.Done, line("ex-c1")!!.state)
        assertEquals("15:00", line("ex-c2")!!.time)
        assertEquals(today.toString(), line("ex-c2")!!.day)
        assertEquals(Dir.From, line("ex-c3")!!.who.single().dir)
        // A record is a note on its day; a matter added under it keeps that place.
        assertEquals(Kind.Note, line("ex-c-e3")!!.kind)
        assertEquals(today.minusDays(2).toString(), line("ex-c-e3")!!.day)
        assertEquals("ex-c-e3", line("ex-c4")!!.parentId)
        assertEquals("ex-c5", line("ex-c6")!!.parentId)
        assertTrue(book.lines.filter { it.threadId == "ex-coffee" }.size >= 9)
    }

    @Test fun mattersKeepTheirDaysPromisesAndRepeats() {
        assertEquals(today.minusDays(2).toString(), line("ex-figma")!!.day)
        assertEquals(Who("Лёша", Dir.To), line("ex-banners")!!.who.single())
        assertEquals(Who("Маша", Dir.From), line("ex-money")!!.who.single())
        assertEquals(listOf(1, 3, 5), line("ex-gym")!!.repeat!!.days)
        assertEquals("Покровка, 17", line("ex-gym")!!.place)
        assertNull("no day — «потом»", line("ex-cv")!!.day)
        assertEquals(State.Done, line("ex-landing")!!.state)
        assertEquals(State.Dropped, line("ex-blender")!!.state)
        // Several days — a line on each page.
        assertEquals(2, book.lines.count { it.text == "Английский с репетитором" })
        // The open tail is a tail on today's page.
        assertTrue(book.page(today, today).tails.any { it.id == "ex-figma" })
    }

    @Test fun anIdeaIsANoteInTheThreadOfIdeas() {
        val idea = line("ex-idea")!!
        assertEquals(Kind.Note, idea.kind)
        assertEquals("Идеи", book.thread(idea.threadId)!!.title)
        assertEquals("ex-idea", line("ex-i-e1")!!.parentId)
    }

    @Test fun peopleAndCommentsComeAlong() {
        assertTrue(book.persons.any { it.name == "Олег" && it.phone != null && it.kept })
        assertTrue("a name on a line becomes a person", book.persons.any { it.name == "Маша" })
        assertTrue("a company stays a company", book.persons.any { it.name == "Кофейня «Зерно»" && it.org })
        val withComment = Import.from(old.copy(tasks = old.tasks.map { if (it.id == "ex-milk") it.copy(comments = listOf(Comment(at = "${today}T10:00", text = "безлактозное"))) else it }), today)
        assertEquals("безлактозное", withComment.children("ex-milk").single().text)
    }

    @Test fun theOldExamplesStayBehind() {
        assertTrue(Import.from(Samples.build(today), today).lines.isEmpty())
    }
}
