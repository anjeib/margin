package ru.anjeib.margin.paper

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.anjeib.margin.book.Book
import ru.anjeib.margin.book.Dir
import ru.anjeib.margin.book.Line
import ru.anjeib.margin.book.later
import ru.anjeib.margin.book.personLines
import ru.anjeib.margin.book.threadLines
import ru.anjeib.margin.book.threadStats
import java.time.LocalDate
import java.time.LocalDateTime

/*
 * The other pages of the notebook (DESIGN.md §8, §9, §11): the index, a thread, a person, «Потом». The same grid as a
 * day: what Margin knows on the margin (dates, counts), the names and your lines right of the rule.
 */

/** A section of the index: Playfair 22 right of the rule. */
@Composable
fun SectionHead(text: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(top = 26.dp, bottom = 6.dp, end = Grid.right)) {
        Spacer(Modifier.width(Grid.text))
        Text(text, style = Voice.section.copy(color = P.c.ink))
    }
}

/** A row of the index: Margin's number or date on the margin, the name right of the rule, a quiet line under it. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun IndexRow(margin: List<Note>, title: String, sub: String?, onClick: () -> Unit, faded: Boolean = false) {
    val c = P.c
    Row(Modifier.fillMaxWidth().heightIn(min = Grid.line).combinedClickable(onClick = onClick).padding(end = Grid.right)) {
        Column(Modifier.width(Grid.margin - Grid.noteGap).padding(start = 4.dp, top = 15.dp, bottom = 8.dp), horizontalAlignment = Alignment.End) {
            margin.forEach { NoteText(it) }
        }
        Spacer(Modifier.width(Grid.text - Grid.margin + Grid.noteGap))
        Column(Modifier.weight(1f).padding(top = 12.dp, bottom = 12.dp)) {
            Text(title, style = Voice.words.copy(color = if (faded) c.graphite else c.ink), maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (!sub.isNullOrBlank()) Text(sub, style = Voice.note.copy(color = c.graphite))
        }
    }
}

/** «Оглавление»: threads, people, «Потом», and the rest of the notebook. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun IndexPage(book: Book, today: LocalDate, onThread: (String) -> Unit, onPerson: (String) -> Unit, onLater: () -> Unit,
              onSettings: () -> Unit, onHelp: () -> Unit, onSearch: () -> Unit, onFeedback: () -> Unit) {
    val c = P.c
    val w = P.w
    val open = book.threads.filter { it.closedAt == null }.map { it to book.threadStats(it.id) }.sortedByDescending { it.second.last }
    val closed = book.threads.filter { it.closedAt != null }
    val people = book.persons.mapNotNull { p ->
        val pl = book.personLines(p.name)
        val n = pl.promised.size + pl.waited.size + pl.other.size
        if (n == 0 && !p.kept) null else Triple(p, pl, n)
    }.sortedWith(compareByDescending<Triple<ru.anjeib.margin.book.Person, ru.anjeib.margin.book.PersonLines, Int>> { it.second.promised.size + it.second.waited.size }.thenBy { it.first.name })
    val later = book.later()
    LazyColumn(Modifier.fillMaxSize().rule(c.rule), contentPadding = PaddingValues(bottom = 40.dp)) {
        item { PageHead(marginTop = "", marginBottom = null, title = w.indexTitle, line = w.indexLine(open.size, people.size)) }
        item { SectionHead(w.threads) }
        if (open.isEmpty()) item { MarginSays(w.noThreads) }
        items(open, key = { "t" + it.first.id }) { (t, st) ->
            IndexRow(listOf(Note(w.near(st.last, today), Tone.Quiet)), t.title, w.threadLine(st), { onThread(t.id) })
        }
        item { SectionHead(w.people) }
        if (people.isEmpty()) item { MarginSays(w.noPeople) }
        items(people, key = { "p" + it.first.id }) { (p, pl, n) ->
            val m = listOfNotNull(
                pl.promised.size.takeIf { it > 0 }?.let { Note("→ $it", Tone.Link) },
                pl.waited.size.takeIf { it > 0 }?.let { Note("← $it", Tone.Link) },
            )
            IndexRow(m, p.name, listOfNotNull(p.about, w.linesN(n)).joinToString(" · "), { onPerson(p.name) })
        }
        item { Spacer(Modifier.padding(top = 14.dp)) }
        item { IndexRow(listOf(Note(later.size.toString(), Tone.Quiet)), w.laterTitle, w.laterLine, onLater) }
        if (closed.isNotEmpty()) {
            item { SectionHead(w.closedThreads) }
            items(closed, key = { "c" + it.id }) { t ->
                IndexRow(emptyList(), t.title, null, { onThread(t.id) }, faded = true)
            }
        }
        item {
            androidx.compose.foundation.layout.FlowRow(Modifier.fillMaxWidth().padding(top = 30.dp, start = Grid.text - 6.dp, end = Grid.right)) {
                listOf(w.search to onSearch, w.settings to onSettings, w.help to onHelp, w.feedback to onFeedback).forEachIndexed { i, (t, f) ->
                    if (i > 0) Text(" · ", style = Voice.reply.copy(color = c.pencil), modifier = Modifier.padding(vertical = 12.dp))
                    Text(t, style = Voice.reply.copy(color = c.link), modifier = Modifier.combinedClickableTap(f).padding(vertical = 12.dp, horizontal = 4.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
fun Modifier.combinedClickableTap(onClick: () -> Unit): Modifier = this.then(Modifier.combinedClickable(onClick = onClick))

/** Lines grouped by day: the day on the margin once (as on a day's page), the lines under it. */
fun LazyListScope.linesByDay(groups: List<Pair<LocalDate?, List<Line>>>, book: Book, today: LocalDate, w: Words, noDay: String,
                             onMark: (String) -> Unit, onOpen: (String) -> Unit, hideThread: String? = null) {
    groups.forEach { (d, ls) ->
        item(key = "d" + (d?.toString() ?: "none")) {
            Row(Modifier.fillMaxWidth().padding(top = 18.dp, end = Grid.right)) {
                Box(Modifier.width(Grid.margin - Grid.noteGap).padding(start = 4.dp), contentAlignment = Alignment.TopEnd) {
                    Text(d?.let { w.dayMonth(it) } ?: noDay, style = Voice.small.copy(color = if (d == today) P.c.link else P.c.ink, textAlign = TextAlign.End))
                }
                Spacer(Modifier.width(Grid.text - Grid.margin + Grid.noteGap))
                Text(d?.let { w.near(it, today).takeIf { n -> n != w.dayMonth(it) } ?: w.dow(it) } ?: "", style = Voice.note.copy(color = P.c.graphite))
            }
        }
        items(ls, key = { "l" + it.id }) { l -> LineOnPage(l, d ?: today, today, book, Seen.Page, false, onMark, onOpen, hideThread = hideThread) }
    }
}

/** A thread's page: its name, Margin's line about it, its lines by day; the field writes into it. */
@Composable
fun ThreadPage(book: Book, id: String, today: LocalDate, onMark: (String) -> Unit, onOpen: (String) -> Unit, bottom: androidx.compose.ui.unit.Dp) {
    val c = P.c
    val w = P.w
    val t = book.thread(id) ?: return
    val st = book.threadStats(id)
    LazyColumn(Modifier.fillMaxSize().rule(c.rule), contentPadding = PaddingValues(bottom = bottom)) {
        item { PageHead(marginTop = "≡", marginBottom = null, title = t.title, line = w.threadLine(st) + " · " + w.since(st.since)) }
        linesByDay(book.threadLines(id), book, today, w, w.noDay, onMark, onOpen, hideThread = id)
        if (book.threadLines(id).isEmpty()) item { MarginSays(w.emptyThread) }
    }
}

/** A person's page: what you promised them, what they promised you, everything else with them. */
@Composable
fun PersonPage(book: Book, name: String, today: LocalDate, onMark: (String) -> Unit, onOpen: (String) -> Unit, onCall: (String) -> Unit,
               bottom: androidx.compose.ui.unit.Dp) {
    val c = P.c
    val w = P.w
    val p = book.person(name)
    val pl = book.personLines(name)
    LazyColumn(Modifier.fillMaxSize().rule(c.rule), contentPadding = PaddingValues(bottom = bottom)) {
        item {
            PageHead(marginTop = name.take(1).uppercase(), marginBottom = null, title = p?.name ?: name,
                line = listOfNotNull(p?.about, p?.phone, p?.telegram).joinToString(" · ").ifBlank { null },
                onLine = p?.phone?.let { ph -> { onCall(ph) } }, lineColor = if (p?.phone != null) c.link else c.graphite)
        }
        if (pl.promised.isNotEmpty()) {
            item { SectionHead(w.youPromised) }
            items(pl.promised, key = { "a" + it.id }) { l -> LineOnPage(l, null, today, book, Seen.Page, false, onMark, onOpen, hidePerson = name) }
        }
        if (pl.waited.isNotEmpty()) {
            item { SectionHead(w.theyPromised) }
            items(pl.waited, key = { "b" + it.id }) { l -> LineOnPage(l, null, today, book, Seen.Page, false, onMark, onOpen, hidePerson = name) }
        }
        if (pl.other.isNotEmpty()) {
            item { SectionHead(w.withThem) }
            items(pl.other, key = { "c" + it.id }) { l -> LineOnPage(l, null, today, book, Seen.Page, false, onMark, onOpen, hidePerson = name) }
        }
        if (pl.promised.isEmpty() && pl.waited.isEmpty() && pl.other.isEmpty()) item { MarginSays(w.emptyPerson) }
    }
}

/** «Потом»: what was put off on purpose; each line can come back to a day. */
@Composable
fun LaterPage(book: Book, today: LocalDate, onMark: (String) -> Unit, onOpen: (String) -> Unit, bottom: androidx.compose.ui.unit.Dp) {
    val c = P.c
    val w = P.w
    val ls = book.later()
    LazyColumn(Modifier.fillMaxSize().rule(c.rule), contentPadding = PaddingValues(bottom = bottom)) {
        item { PageHead(marginTop = "", marginBottom = null, title = w.laterTitle, line = w.laterLine) }
        items(ls, key = { it.id }) { l ->
            LineOnPage(l, null, today, book, Seen.Page, false, onMark, onOpen, lead = Note(w.since(l.written.toLocalDate()), Tone.Quiet))
        }
        if (ls.isEmpty()) item { MarginSays(w.emptyLater) }
    }
}
