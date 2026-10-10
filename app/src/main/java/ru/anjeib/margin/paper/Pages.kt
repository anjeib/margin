package ru.anjeib.margin.paper

import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.height
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.text.BasicTextField
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
fun IndexRow(margin: List<Note>, title: String, sub: String?, onClick: () -> Unit, faded: Boolean = false, tab: String? = null) {
    val c = P.c
    Row(Modifier.fillMaxWidth().heightIn(min = Grid.line).combinedClickable(onClick = onClick).padding(end = Grid.right)) {
        Box(Modifier.width(Grid.margin - Grid.noteGap)) {
            // An address book's tab: the letter at the margin's edge, on the first name that begins with it.
            tab?.let { Text(it, style = Voice.section.copy(color = c.red, fontSize = 20.sp, lineHeight = 24.sp), modifier = Modifier.padding(start = 10.dp, top = 10.dp)) }
            Column(Modifier.fillMaxWidth().padding(start = 4.dp, top = 15.dp, bottom = 8.dp), horizontalAlignment = Alignment.End) {
                margin.forEach { NoteText(it) }
            }
        }
        Spacer(Modifier.width(Grid.text - Grid.margin + Grid.noteGap))
        Column(Modifier.weight(1f).padding(top = 12.dp, bottom = 12.dp)) {
            Text(title, style = Voice.words.copy(color = if (faded) c.graphite else c.ink), maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (!sub.isNullOrBlank()) Text(sub, style = Voice.note.copy(color = c.graphite))
        }
    }
}

/** «Оглавление»: what is alive now — a few threads, a few people — and the way to all of them, «Неделя», «Потом». */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun IndexPage(book: Book, today: LocalDate, onThread: (String) -> Unit, onPerson: (String) -> Unit, onLater: () -> Unit,
              onSettings: () -> Unit, onHelp: () -> Unit, onSearch: () -> Unit, onFeedback: () -> Unit, onWeek: () -> Unit = {},
              onAllThreads: () -> Unit = {}, onAllPeople: () -> Unit = {}, onFiles: () -> Unit = {}) {
    val c = P.c
    val w = P.w
    val open = book.threads.filter { it.closedAt == null }.map { it to book.threadStats(it.id, today) }.sortedByDescending { it.second.last }
    // Alive: something open in it, or something happened in the last two weeks. Six at most — the rest is one tap away.
    val alive = open.filter { (_, st) -> st.open + st.waiting > 0 || !st.last.isBefore(today.minusDays(14)) }.take(6)
    val people = peopleOf(book)
    val livePeople = people.filter { (_, pl, _) -> pl.promised.isNotEmpty() || pl.waited.isNotEmpty() || pl.other.any { it.open } }.take(6)
    val later = book.later()
    LazyColumn(Modifier.fillMaxSize().rule(c.rule), contentPadding = PaddingValues(bottom = 40.dp)) {
        item { PageHead(marginTop = "", marginBottom = null, title = w.indexTitle,
            line = w.indexLine(book.threads.size, 0).substringBefore(" · ") + " · " + w.peopleLine(people.count { !it.first.org }, people.count { it.first.org })) }
        item { SectionHead(w.threads) }
        if (open.isEmpty()) item { MarginSays(w.noThreads) }
        items(alive, key = { "t" + it.first.id }) { (t, st) ->
            IndexRow(listOf(Note(w.near(st.last, today), Tone.Quiet)), t.title, w.threadLine(st), { onThread(t.id) })
        }
        if (book.threads.size > alive.size) item { MarginLink(w.allThreads(book.threads.size), onClick = onAllThreads) }
        item { SectionHead(if (people.any { it.first.org }) w.addressBook else w.people) }
        if (people.isEmpty()) item { MarginSays(w.noPeople) }
        items(livePeople, key = { "p" + it.first.id }) { (p, pl, n) -> PersonRow(p, pl, n, w) { onPerson(p.name) } }
        if (people.size > livePeople.size) item { MarginLink(w.allPeople(people.size), onClick = onAllPeople) }
        item { Spacer(Modifier.padding(top = 14.dp)) }
        // The calendar: the month and the days ahead (the week spread stays a tap away from the month).
        item { IndexRow(emptyList(), w.calendarTitle, w.calendarSub, onWeek) }
        item { IndexRow(listOf(Note(later.size.toString(), Tone.Quiet)), w.laterTitle, w.laterLine, onLater) }
        // Everything attached, in one place — only once there is something to show.
        val files = book.lines.sumOf { it.files.size }
        if (files > 0) item { IndexRow(listOf(Note(files.toString(), Tone.Quiet)), w.filesTitle, w.filesSub, onFiles) }
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

/** Everyone the notebook knows (named on a line, or kept), with their lines: open promises first, then by name. */
private fun peopleOf(book: Book): List<Triple<ru.anjeib.margin.book.Person, ru.anjeib.margin.book.PersonLines, Int>> = book.persons.mapNotNull { p ->
    val pl = book.personLines(p.name)
    val n = pl.promised.size + pl.waited.size + pl.other.size
    if (n == 0 && !p.kept) null else Triple(p, pl, n)
}.sortedWith(compareByDescending<Triple<ru.anjeib.margin.book.Person, ru.anjeib.margin.book.PersonLines, Int>> { it.second.promised.size + it.second.waited.size }
    .thenByDescending { t -> (t.second.promised + t.second.waited + t.second.other).maxOfOrNull { it.writtenAt } ?: "" }.thenBy { it.first.name })

/** A person in a list: → and ← counts on the margin, the name, what they are and how many lines. */
@Composable
private fun PersonRow(p: ru.anjeib.margin.book.Person, pl: ru.anjeib.margin.book.PersonLines, n: Int, w: Words, tab: String? = null, onClick: () -> Unit) {
    // Promises in words under the name: an arrow with a number alone does not say who owes whom.
    IndexRow(emptyList(), p.name, listOfNotNull(p.about ?: if (p.org) w.company else null,
        w.owes(pl.promised.size, pl.waited.size) ?: w.linesN(n).takeIf { n > 0 }).joinToString(" · "), onClick, tab = tab)
}

/** All threads, by the last thing that happened; the month on the margin once; the closed ones at the end, faded. */
@Composable
fun AllThreadsPage(book: Book, today: LocalDate, onThread: (String) -> Unit) {
    val c = P.c
    val w = P.w
    val open = book.threads.filter { it.closedAt == null }.map { it to book.threadStats(it.id, today) }.sortedByDescending { it.second.last }
    val closed = book.threads.filter { it.closedAt != null }.sortedByDescending { it.closedAt }
    LazyColumn(Modifier.fillMaxSize().rule(c.rule), contentPadding = PaddingValues(bottom = 40.dp)) {
        item { PageHead(marginTop = "≡", marginBottom = null, title = w.threads, line = w.indexLine(book.threads.size, 0).substringBefore(" · ")) }
        var month: java.time.YearMonth? = null
        open.forEach { (t, st) ->
            val ym = java.time.YearMonth.from(st.last)
            val mark = if (ym != month) { month = ym; w.monthName(ym).lowercase() } else null
            item("t" + t.id) { IndexRow(listOfNotNull(mark?.let { Note(it, Tone.Quiet) }), t.title, w.threadLine(st).ifBlank { w.since(st.since) }, { onThread(t.id) }) }
        }
        if (closed.isNotEmpty()) {
            item { SectionHead(w.closedThreads) }
            items(closed, key = { "c" + it.id }) { t -> IndexRow(emptyList(), t.title, null, { onThread(t.id) }, faded = true) }
        }
    }
}

/**
 * The address book: everyone and every company by the alphabet, the letter on the margin where it begins — as in a paper
 * one; a field on top finds by any part of a name or of what they are; «все · люди · компании».
 */
@Composable
fun AddressBookPage(book: Book, onPerson: (String) -> Unit) {
    val c = P.c
    val w = P.w
    var q by rememberSaveable { mutableStateOf("") }
    var kind by rememberSaveable { mutableStateOf(0) } // 0 all, 1 people, 2 companies
    val key = q.trim().lowercase().replace('ё', 'е')
    val all = peopleOf(book).sortedBy { it.first.name.lowercase().replace('ё', 'е') }
    val shown = all.filter { (p, _, _) ->
        (kind == 0 || (kind == 1 && !p.org) || (kind == 2 && p.org)) &&
            (key.isEmpty() || (p.name + " " + (p.about ?: "") + " " + p.aliases.joinToString(" ")).lowercase().replace('ё', 'е').contains(key))
    }
    LazyColumn(Modifier.fillMaxSize().rule(c.rule), contentPadding = PaddingValues(bottom = 40.dp)) {
        item { PageHead(marginTop = "А", marginBottom = "Я", title = w.addressBook, line = w.peopleLine(all.count { !it.first.org }, all.count { it.first.org })) }
        item {
            Row(Modifier.fillMaxWidth().padding(end = 16.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.width(Grid.text - 6.dp))
                val shape = RoundedCornerShape(22.dp)
                Box(Modifier.weight(1f).heightIn(min = 44.dp).clip(shape).background(c.sheet).border(1.dp, c.hairline, shape).padding(horizontal = 14.dp, vertical = 10.dp)) {
                    if (q.isEmpty()) Text(w.findPerson, style = Voice.words.copy(color = c.graphite))
                    BasicTextField(q, { q = it }, textStyle = Voice.words.copy(color = c.ink), cursorBrush = SolidColor(c.link), singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(start = Grid.text - 6.dp)) {
                listOf(w.filterAll, w.filterPeople, w.filterOrgs).forEachIndexed { i, t ->
                    if (i > 0) Text(" · ", style = Voice.reply.copy(color = c.pencil), modifier = Modifier.padding(vertical = 12.dp))
                    Text(t, style = Voice.reply.copy(color = if (kind == i) c.ink else c.link),
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { kind = i }.padding(vertical = 12.dp, horizontal = 4.dp))
                }
            }
        }
        var letter: Char? = null
        shown.forEach { (p, pl, n) ->
            val first = p.name.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar() ?: '#'
            val tab = if (first != letter) first.toString().also { letter = first } else null
            item("p" + p.id) { PersonRow(p, pl, n, w, tab) { onPerson(p.name) } }
        }
        if (shown.isEmpty()) item { MarginSays(w.nothingFound, Modifier.padding(top = 12.dp)) }
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
fun ThreadPage(book: Book, id: String, today: LocalDate, onMark: (String) -> Unit, onOpen: (String) -> Unit, bottom: androidx.compose.ui.unit.Dp,
               onClose: (Boolean) -> Unit = {}) {
    val c = P.c
    val w = P.w
    val t = book.thread(id) ?: return
    val st = book.threadStats(id)
    LazyColumn(Modifier.fillMaxSize().rule(c.rule), contentPadding = PaddingValues(bottom = bottom)) {
        item {
            PageHead(marginTop = "≡", marginBottom = null, title = t.title,
                line = (if (t.closedAt != null) w.threadClosed + " · " else "") + w.threadLine(st).let { if (it.isBlank()) "" else "$it · " } + w.since(st.since))
        }
        linesByDay(book.threadLines(id), book, today, w, w.noDay, onMark, onOpen, hideThread = id)
        if (book.threadLines(id).isEmpty()) item { MarginSays(w.emptyThread) }
        // A thread that has run its course is closed: it goes to the end of the index; its lines stay where they are.
        item { MarginLink(if (t.closedAt == null) w.closeThread else w.reopenThread, Modifier.padding(top = 12.dp)) { onClose(t.closedAt == null) } }
    }
}

/** A person's page: what you promised them, what they promised you, everything else with them. */
@Composable
fun PersonPage(book: Book, name: String, today: LocalDate, onMark: (String) -> Unit, onOpen: (String) -> Unit, onCall: (String) -> Unit,
               bottom: androidx.compose.ui.unit.Dp, onOrg: (Boolean) -> Unit = {}) {
    val c = P.c
    val w = P.w
    val p = book.person(name)
    val pl = book.personLines(name)
    LazyColumn(Modifier.fillMaxSize().rule(c.rule), contentPadding = PaddingValues(bottom = bottom)) {
        item {
            // On the margin under the letter: when they were last on a page.
            val last = (pl.promised + pl.waited + pl.other).maxOfOrNull { it.written.toLocalDate() }
            PageHead(marginTop = name.take(1).uppercase(), marginBottom = last?.let { w.near(it, today) }, title = p?.name ?: name,
                line = p?.about, bottomNote = true)
            // Reaching them: in their own app, from here.
            val ctx = androidx.compose.ui.platform.LocalContext.current
            fun go(i: android.content.Intent) = runCatching { ctx.startActivity(i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
            androidx.compose.foundation.layout.FlowRow(Modifier.fillMaxWidth().padding(start = Grid.text - 6.dp, end = Grid.right)) {
                p?.phone?.let { ph ->
                    Reply(w.call) { onCall(ph) }
                    Reply(w.message) { go(android.content.Intent(android.content.Intent.ACTION_SENDTO, android.net.Uri.parse("smsto:" + ph.filter { it.isDigit() || it == '+' }))) }
                }
                p?.telegram?.let { tg -> Reply("Telegram") { go(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://t.me/" + tg.trimStart('@')))) } }
                p?.email?.let { em -> Reply(w.mail) { go(android.content.Intent(android.content.Intent.ACTION_SENDTO, android.net.Uri.parse("mailto:$em"))) } }
            }
        }
        if (pl.promised.isNotEmpty()) {
            item { SectionHead(w.youPromised) }
            items(pl.promised, key = { "a" + it.id }) { l -> LineOnPage(l, null, today, book, Seen.Page, false, onMark, onOpen, hidePerson = name) }
        }
        if (pl.waited.isNotEmpty()) {
            item { SectionHead(w.theyPromised) }
            items(pl.waited, key = { "b" + it.id }) { l -> LineOnPage(l, null, today, book, Seen.Page, false, onMark, onOpen, hidePerson = name) }
        }
        // Open lines with them — «Вместе»; what is closed (done, not doing — a promise let go too) — under «Было», not
        // passed off as something together.
        val withOpen = pl.other.filter { it.open || (it.kind != ru.anjeib.margin.book.Kind.Todo && it.state == ru.anjeib.margin.book.State.Open) }
        val past = pl.other - withOpen.toSet()
        if (withOpen.isNotEmpty()) {
            item { SectionHead(w.withThem) }
            items(withOpen, key = { "c" + it.id }) { l -> LineOnPage(l, null, today, book, Seen.Page, false, onMark, onOpen, hidePerson = name) }
        }
        if (past.isNotEmpty()) {
            item { SectionHead(w.pastWithThem) }
            items(past, key = { "p" + it.id }) { l -> LineOnPage(l, null, today, book, Seen.Page, false, onMark, onOpen, hidePerson = name) }
        }
        if (pl.promised.isEmpty() && pl.waited.isEmpty() && pl.other.isEmpty()) item { MarginSays(w.emptyPerson) }
        // A company or a person — how the address book files them.
        if (p != null) item { MarginLink(if (p.org) w.markPerson else w.markOrg, Modifier.padding(top = 16.dp)) { onOrg(!p.org) } }
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
            // Since when it waits here: the day it was put off (or written, if it was born here).
            val since = l.trail.lastOrNull { it.to == null }?.at?.let { runCatching { java.time.LocalDateTime.parse(it).toLocalDate() }.getOrNull() } ?: l.written.toLocalDate()
            LineOnPage(l, null, today, book, Seen.Page, false, onMark, onOpen, lead = Note(w.since(since), Tone.Quiet))
        }
        if (ls.isEmpty()) item { MarginSays(w.emptyLater) }
    }
}
