package ru.anjeib.margin.ui

import androidx.compose.foundation.layout.Spacer


import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.anjeib.margin.data.AppData
import ru.anjeib.margin.data.Task
import ru.anjeib.margin.data.dayMonthShort
import ru.anjeib.margin.data.dowFull
import ru.anjeib.margin.data.questionRef
import java.time.LocalDate

/** A mention in written words: @[Имя] — a person or company, @[p:место] — a place, @[d:<id>] — a matter, #[<id>] — a topic. */
private val mentionRx = Regex("@\\[([^\\]]+)]|#\\[([^\\]]+)]")

/** The title of a matter by its id — a simple one or a topic's («q:<topic>:<matter>»). */
fun AppData.dealTitle(ref: String): String? {
    questionRef(ref)?.let { (tid, qid) ->
        fun flat(l: List<ru.anjeib.margin.data.Subtask>): List<ru.anjeib.margin.data.Subtask> = l.flatMap { listOf(it) + flat(it.children) }
        return tasks.find { it.id == tid }?.subtasks?.let(::flat)?.find { it.id == qid }?.title
    }
    return tasks.find { it.id == ref }?.title
}

/** What a mention reads as: «Маша», «⌖ Ленина, 5», «○ Позвонить в банк», «# Ремонт кухни». */
fun AppData.mentionLabel(token: String): String {
    val m = mentionRx.matchEntire(token) ?: return token
    m.groupValues[2].takeIf { it.isNotEmpty() }?.let { id -> return "# " + (tasks.find { it.id == id }?.title ?: id) }
    val v = m.groupValues[1]
    return when {
        v.startsWith("p:") -> "⌖ " + v.drop(2)
        v.startsWith("d:") -> "○ " + (dealTitle(v.drop(2)) ?: v.drop(2))
        else -> v
    }
}

/** The words with every mention spelled out — for a title, search, the AI. */
fun AppData.mentionsPlain(text: String): String = mentionRx.replace(text) { mentionLabel(it.value).removePrefix("# ").removePrefix("⌖ ").removePrefix("○ ") }

/**
 * The words of a note with its mentions as petrol links (owner, 14 окт): a person opens their card, a matter or a topic opens
 * itself, a place — the map.
 */
@Composable
fun mentionsText(text: String, data: AppData, onPerson: (String) -> Unit, onOpen: (String) -> Unit): AnnotatedString {
    val c = M.c
    val ctx = androidx.compose.ui.platform.LocalContext.current
    return buildAnnotatedString {
        var pos = 0
        mentionRx.findAll(text).forEach { m ->
            append(text.substring(pos, m.range.first))
            val label = data.mentionLabel(m.value)
            val v = m.groupValues[1]
            val click: () -> Unit = when {
                m.groupValues[2].isNotEmpty() -> { { onOpen(m.groupValues[2]) } }
                v.startsWith("d:") -> { { onOpen(v.drop(2)) } }
                v.startsWith("p:") -> { {
                    runCatching { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("geo:0,0?q=" + android.net.Uri.encode(v.drop(2))))) }
                } }
                else -> { { onPerson(v) } }
            }
            withLink(LinkAnnotation.Clickable("m${m.range.first}", TextLinkStyles(SpanStyle(color = c.link))) { click() }) { append(label) }
            pos = m.range.last + 1
        }
        append(text.substring(pos))
    }
}

/** When a note was begun: its first record, else the day it was made. */
private fun Task.bornAt(): String = entries.minOfOrNull { it.at } ?: history.firstOrNull { it.kind == "created" }?.at ?: (createdAt.take(10) + "T00:00")

/**
 * «Заметки» — the diary (owner, 14 окт: ideas became notes). Day by day, newest first: the date on the margin as on a day's page,
 * the weekday in the planner's italic; each note written straight on the page — its hour on the margin, its words, mentions as
 * links — no panels, as in a notebook. Notes from the main field land here too; the field below writes here directly.
 */
/**
 * A note written inside a matter or a topic, shown in «Заметки» too (owner, 16 окт): its words, its files, and a link to where it
 * lives — «# Тема», «○ Дело», «○ Дело · # Тема». [open] — what a tap opens.
 */
internal class LinkedNote(val key: String, val at: String, val text: String, val files: List<ru.anjeib.margin.data.Attachment>,
                         val link: String, val open: String, val sphere: String?,
                         /** The note's own card ([NoteCard]): a tap opens the note itself, the link — where it lives. */
                         val card: String)

/** Every note written in a matter or a topic: the topics' records, the matters' comments and records, a topic's matters' comments. */
internal fun AppData.linkedNotes(): List<LinkedNote> {
    val out = mutableListOf<LinkedNote>()
    fun flat(l: List<ru.anjeib.margin.data.Subtask>): List<ru.anjeib.margin.data.Subtask> = l.flatMap { listOf(it) + flat(it.children) }
    tasks.filter { !it.thought }.forEach { t ->
        // An archived topic is put away with what is in it.
        if (t.topic && t.done) return@forEach
        val own = if (t.topic) "# " + t.title else "○ " + t.title
        // Words that only named a matter of the topic («Уточнить шрифт вывески») are that matter, not a note — as in the topic itself.
        fun norm(x: String) = x.lowercase().filter { it.isLetterOrDigit() }
        val titles = flat(t.subtasks).map { norm(it.title) }.filter { it.isNotEmpty() }
        fun echo(e: ru.anjeib.margin.data.Entry) = e.attachments.isEmpty() && norm(e.text).let { w -> titles.any { q -> w == q || (w.contains(q) && w.length <= q.length + 12) } }
        t.entries.filter { (it.text.isNotBlank() || it.attachments.isNotEmpty()) && !echo(it) }.forEach { e ->
            out += LinkedNote(entryKey(t.id, e.id), e.at, e.text, e.attachments, own, t.id, t.sphere, entryKey(t.id, e.id))
        }
        t.comments.filter { it.text.isNotBlank() }.forEach { cm -> out += LinkedNote(commentKey(t.id, cm.id), cm.at, cm.text, emptyList(), own, t.id, t.sphere, commentKey(t.id, cm.id)) }
        if (t.topic) flat(t.subtasks).forEach { q ->
            q.comments.filter { it.text.isNotBlank() }.forEach { cm ->
                out += LinkedNote(commentKey("q:${t.id}:${q.id}", cm.id), cm.at, cm.text, emptyList(), "○ " + q.title + "  ·  # " + t.title, "q:${t.id}:${q.id}", t.sphere, commentKey("q:${t.id}:${q.id}", cm.id))
            }
        }
    }
    // A note copied into the next occurrence of a repeating matter (before 0.77) is one note: shown once, under the first.
    return out.distinctBy { it.card.substringAfterLast('|') + "|" + it.at + "|" + it.text }
}

@Composable
fun DiaryPage(data: AppData, vm: MainVm, onOpen: (String) -> Unit, onPerson: (String) -> Unit, contentPadding: PaddingValues,
              /** A note of a matter or a topic: its own card ([NoteCard]). */
              onOpenNote: (String) -> Unit = {},
              /** Owner, 16 окт («core»): the page holds one day's notes; a swipe across it turns the day (App). */
              day: LocalDate = LocalDate.now(), events: List<ru.anjeib.margin.data.CalEvent> = emptyList(),
              /**
               * «Дневник» (owner, 17 окт): only the free notes, every day in one feed, newest first; nothing of the diary's
               * matters and topics comes here, and nothing written here goes there.
               */
              journal: Boolean = false) {
    val c = M.c
    val s = M.s
    val today = LocalDate.now()
    // «личное / работа» (owner, 16 окт): the same two marks as on «Темы»; chosen again — all of them.
    val sphere by vm.diarySphere.collectAsState()
    var archOpen by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    fun sOk(x: String?) = journal || sphere == null || (if (sphere == "work") x == "work" else x != "work")
    val notes = remember(data, sphere) {
        data.tasks.filter { it.thought && it.subtasks.isEmpty() && !it.done && sOk(it.sphere) }.sortedBy { it.bornAt() }
    }
    val linked = remember(data, sphere, journal) { if (journal) emptyList() else data.linkedNotes().filter { sOk(it.sphere) } }
    // Both kinds in one diary, by the time they were written.
    val lines: List<Pair<String, Any>> = remember(notes, linked) { (notes.map { it.bornAt() to (it as Any) } + linked.map { it.at to (it as Any) }).sortedBy { it.first } }
    val byDay = remember(lines) { lines.groupBy { runCatching { LocalDate.parse(it.first.take(10)) }.getOrDefault(today) }.mapValues { e -> e.value.map { it.second } }.toSortedMap(compareByDescending { it }) }
    val linkedText = remember(linked) { linked.associate { "l" + it.key to data.mentionsPlain(it.text) + " " + it.link } }
    val pageLines = rememberPageLines()
    val landed = vm.landed.collectAsState().value
    // Found on this page by the find bar: a note by its words.
    val list = androidx.compose.foundation.lazy.rememberLazyListState()
    val find = LocalFind.current
    val found = remember { mutableListOf<FindHit>() }
    ReportFound("notes", found, list, data)
    // The calendar on the diary's frame: all days' notes in one feed (owner, 17 окт).
    // Under the diary's head «Дневник» is a feed the head's calendar moves too.
    val journalFeed = journal && LocalFramed.current
    val feed = LocalFramed.current && vm.monthOpen.collectAsState().value && !journal
    val feedStarts = remember { HashMap<LocalDate, Int>() }
    val feedJump by vm.feedJump.collectAsState()
    FeedFollow(feed || journalFeed, list, feedStarts, day, feedJump) { vm.feedDay.value = it }
    val plannerLook = planner
    val framed = LocalFramed.current
    val monthOpen by vm.monthOpen.collectAsState()
    Box(Modifier.fillMaxSize().ruledPage(pageLines).screenRule()) {
        LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(top = contentPadding.calculateTopPadding(), bottom = contentPadding.calculateBottomPadding())) {
          findable(found, find, { k -> (k as? String)?.let { linkedText[it] }?.let { find != null && findHit(find.words, it) }
              ?: (data.taskOfKey(k)?.let { t -> find != null && findHit(find.words, t.title + " " + data.mentionsPlain(t.entries.joinToString(" ") { it.text })) } == true) }) {
            // Owner, 16 окт: the head stays put; only the diary scrolls.
            // In the diary's frame the head is the frame's (Frame.kt).
            // «Дневник»: its name and line, the way back on the margin (App); no calendar, no spheres.
            // Under the diary's head the journal's name stands in the head itself (Frame.kt).
            if (journalFeed) Unit
            else if (journal) pageHeadItem("head") {
                Column {
                    Margined(Modifier.padding(top = 40.dp)) {
                        Column {
                            Text(s.journalTitle, style = Type.pageTitle.copy(color = c.ink))
                            Voice(s.journalSub, Modifier.padding(top = 2.dp))
                        }
                    }
                    DoubleRule(Modifier.padding(top = 14.dp, bottom = 4.dp))
                }
            }
            else if (!framed) pinnedHead("head", plannerLook, calendar = true) {
                Margined(Modifier.padding(top = if (planner) 0.dp else 8.dp), margin = { MarginDay(day.dayOfMonth, day.dayMonthShort().substringAfter(' ')) }) {
                    Column {
                        Row(verticalAlignment = Alignment.Top) {
                            Text(s.diaryTitle, style = Type.pageTitle.copy(color = c.ink), modifier = Modifier.weight(1f))
                            if (!planner) PageToolIcons()
                        }
                        Row(Modifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            Voice(s.diarySub, Modifier.weight(1f))
                            SphereIcons(sphere, { vm.diarySphere.value = it }, clearable = true)
                        }
                        if (day != today) Voice(s.backToToday, Modifier.padding(top = 4.dp), link = true, onClick = { vm.pageDay.value = null })
                    }
                }
                if (planner) DoubleRule(Modifier.padding(top = 18.dp, bottom = 6.dp))
            }
            /** One day's notes: notes of one matter or topic together, under its link. */
            fun androidx.compose.foundation.lazy.LazyListScope.dayNotes(d: LocalDate, list: List<Any>) {
                // Owner, 16 окт: notes of one matter or topic in a day stand together, under its link, on one light tint.
                val blocks = withTopicHeads(list) { (it as? LinkedNote)?.let { l -> l.open to l.link } }
                val pos = blockPositions(blocks) { (it as? LinkedNote)?.open }
                blocks.forEachIndexed { i, x ->
                    if (x is TopicHead) { item("lg" + d + x.id) { TopicBlockHead(x.title, raw = true, top = if (i == 0) 4.dp else 14.dp) { onOpen(x.id) } }; return@forEachIndexed }
                    if (x is LinkedNote) { item("l" + x.key) { LinkedNoteRow(x, data, vm, i, blocks.size, pos[i], onOpen, onOpenNote, onPerson, first = i == 0) }; return@forEachIndexed }
                    val n = x as Task
                    item("n" + n.id) {
                        val at = n.bornAt()
                        val body = n.entries.filter { it.text.isNotBlank() }.joinToString("\n\n") { it.text }
                        val plainBody = data.mentionsPlain(body)
                        // The title only when the words don't already begin with it (a note written here is its own first line).
                        val showTitle = n.title.isNotBlank() && !plainBody.trimStart().startsWith(n.title.trimEnd('…').take(40))
                        val hour = at.drop(11).take(5)
                        val first = i == 0
                        Margined(Modifier.landing(n.id in landed, 0), margin = {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = (if (first) 4.dp else 8.dp) + KnotDrop)) { MarginNote(hour) }
                        }) {
                            var menu by remember(n.id) { mutableStateOf(false) }
                            var edit by remember(n.id) { mutableStateOf(false) }
                            val firstWords = n.entries.firstOrNull { it.text.isNotBlank() }
                            if (edit && firstWords != null) TextDialog(s.edit, firstWords.text, "", onDismiss = { edit = false }) { v ->
                                if (v.isNotBlank()) vm.editEntryText("task:${n.id}", firstWords.id, v.trim()); edit = false
                            }
                            val rowActs = LocalRowActions.current
                            // Owner, 16 окт: a long press offers what the note's card does.
                            Box(Modifier.fillMaxWidth()) { Box(Modifier.align(Alignment.TopEnd).padding(top = 8.dp)) { SoftMenu(menu, { menu = false }) {
                                // Owner, 16 окт: changes from the hold; a tap only unfolds it.
                                if (firstWords != null) SoftMenuItem(s.edit) { menu = false; edit = true }
                                SoftMenuItem(s.actToMatter) { menu = false; vm.ideaToMatter(n.id) }
                                rowActs?.let { r -> SoftMenuItem(s.actToTopic) { menu = false; r.toTopic(n.id) } }
                                SoftMenuItem(s.toArchive) { menu = false; vm.archiveTopic(n.id) }
                            } } }
                            PanelPiece(seatOf(i, list.size)) {
                                // Prototype «inline»: a tap unfolds the note right here — all of it, written on in place.
                                val inline = LocalInline.current
                                val unfolded = inline?.open == n.id
                                Column(Modifier.fillMaxWidth().combinedClickable(onClick = { if (inline != null) inline.toggle(n.id) else onOpen(n.id) }, onLongClick = { menu = true }).padding(horizontal = if (planner) 0.dp else 16.dp).padding(top = if (first) 4.dp else 8.dp, bottom = 8.dp)) {
                                    var titleLayout by remember { mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }
                                    var bodyLayout by remember { mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }
                                    // Three lines of a note at most (owner, 14 окт) — the whole of it is in its card.
                                    if (showTitle) WordText(n.title, style = if (planner) Type.body.copy(fontFamily = Literata, color = c.ink, fontSize = 17.sp, lineHeight = 24.sp)
                                        else Type.body.copy(color = c.ink, fontSize = 16.5.sp, lineHeight = 22.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium),
                                        maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                        onTextLayout = { titleLayout = it }, modifier = Modifier.foundMarks(n.title) { titleLayout })
                                    if (unfolded) InlineNoteBody(n, vm)
                                    else if (body.isNotBlank()) {
                                        val shown = mentionsText(body, data, onPerson, onOpen)
                                        WordText(shown, style = Type.body.copy(fontFamily = rowFamily, color = c.ink.copy(alpha = 0.9f), fontSize = rowSize, lineHeight = rowLine), onTextLayout = { bodyLayout = it },
                                            maxLines = if (showTitle) 2 else 3, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                            modifier = Modifier.padding(top = if (showTitle) 3.dp else 0.dp).foundMarks(shown.text) { bodyLayout })
                                    }
                                    if (!unfolded) n.entries.flatMap { it.attachments }.takeIf { it.isNotEmpty() }?.let { Box(Modifier.padding(top = 8.dp)) { AttachmentsView(it) } }
                                    // Who and what the note touches, quietly — when it was made in the field with marks.
                                    val tags = (n.people.map { it } + listOfNotNull(n.place?.let { "⌖ $it" })).filter { t -> !plainBody.contains(t.removePrefix("⌖ ")) }
                                    if (tags.isNotEmpty()) Text(tags.joinToString("  ·  "), style = Type.small.copy(color = c.link, fontSize = subSize), modifier = Modifier.padding(top = 4.dp))
                                }
                            }
                        }
                    }
                }
            }
            if (journalFeed) {
                // Under the diary's head: every day with notes, in the order of days like the diary (today always), so the
                // head's week and month move it the same way.
                feedStarts.clear()
                val cs = CountingScope(this)
                if (byDay.isEmpty()) cs.item("empty") { Margined(Modifier.padding(top = 18.dp)) { Voice(s.journalEmpty) } }
                // The day picked in the head's week stands too, even empty (owner, 18 окт: a tap on a day with no notes did nothing).
                (byDay.keys + today + day).toSortedSet().forEach { d ->
                    feedStarts[d] = cs.n
                    cs.item("jd$d") { FeedDayHead(d, today, free = byDay[d].isNullOrEmpty(), freeWord = s.noEntriesWord) }
                    byDay[d]?.let { cs.dayNotes(d, it) }
                }
                val archivedOwn = data.tasks.filter { it.thought && it.done && it.subtasks.isEmpty() }.map { t ->
                    ArchItem(t.id, t.title.ifBlank { t.entries.firstOrNull()?.text.orEmpty() }, t.doneAt, open = { onOpen(t.id) }, restore = { vm.reviveTopic(t.id) }, purge = { vm.removeMatter(t.id) })
                }
                archiveFold("arch", archivedOwn, archOpen) { archOpen = !archOpen }
                // A whole screen of room after the last day, so a day picked near the end still comes up to the top.
                item("feed-end") { Box(Modifier.fillParentMaxHeight()) }
            } else if (journal) {
                // Every day's notes, the newest day first, its number large on the margin.
                if (byDay.isEmpty()) item("empty") { Margined(Modifier.padding(top = 18.dp)) { Voice(s.journalEmpty) } }
                byDay.forEach { (d, l) ->
                    item("jd$d") { FeedDayHead(d, today) }
                    dayNotes(d, l)
                }
                val archivedOwn = data.tasks.filter { it.thought && it.done && it.subtasks.isEmpty() }.map { t ->
                    ArchItem(t.id, t.title.ifBlank { t.entries.firstOrNull()?.text.orEmpty() }, t.doneAt, open = { onOpen(t.id) }, restore = { vm.reviveTopic(t.id) }, purge = { vm.removeMatter(t.id) })
                }
                archiveFold("arch", archivedOwn, archOpen) { archOpen = !archOpen }
            } else if (feed) {
                // The calendar open: every day's notes in one feed under the pinned month (owner, 17 окт).
                feedStarts.clear()
                val cs = CountingScope(this)
                cs.stickyHeader("month") {
                    Box(Modifier.background(c.bg).marginRule(marginInk)) {
                        MonthPanel(data, vm, day, events) { d -> vm.pageDay.value = d.takeIf { it != today }; vm.feedJump.value++ }
                    }
                }
                cs.n++
                (byDay.keys + today).toSortedSet().forEach { d ->
                    feedStarts[d] = cs.n
                    cs.item("fd$d") { FeedDayHead(d, today) }
                    val l = byDay[d].orEmpty()
                    if (l.isEmpty()) cs.item("fn$d") { Margined(Modifier.padding(top = 6.dp)) { Voice(s.notesNoneThisDay) } } else cs.dayNotes(d, l)
                }
                item("feed-end") { Box(Modifier.fillParentMaxHeight()) }
            } else {
            if (monthOpen) item("month") { MonthPanel(data, vm, day, events) { d -> vm.pageDay.value = d.takeIf { it != today } } }
            val dayLines = byDay[day].orEmpty()
            if (lines.isEmpty() && day == today) item("empty") { Margined(Modifier.padding(top = 18.dp)) { Empty(s.diaryEmpty, s.diaryEmptyHint) } }
            else if (dayLines.isEmpty()) item("none") { Margined(Modifier.padding(top = 18.dp)) { Voice(s.notesNoneThisDay) } }
            dayNotes(day, dayLines)
            // Owner, 16 окт: archived notes at the end of the page, folded — the diary's own and those of matters and topics.
            val archived = data.tasks.filter { it.thought && it.done && it.subtasks.isEmpty() && sOk(it.sphere) }.map { t ->
                ArchItem(t.id, t.title.ifBlank { t.entries.firstOrNull()?.text.orEmpty() }, t.doneAt, open = { onOpen(t.id) }, restore = { vm.reviveTopic(t.id) }, purge = { vm.removeMatter(t.id) })
            } + data.archivedNotes(vm, onOpenNote) { sOk(it) }
            if (day == today) archiveFold("arch", archived, archOpen) { archOpen = !archOpen }
            }
            item("end") { PageEnd(pageLines) }
            item("tail") { Box(Modifier.width(1.dp).height(40.dp)) }
          }
        }
    }
}

/**
 * A note written in a matter or a topic: the hour on the margin, its words (three lines at most), its files, where it lives.
 * A tap opens the note's own card; the link under it — the matter or topic; a long press — a small menu (owner, 16 окт).
 * In a block ([block]) the link is the block's head, not repeated under each note.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun LinkedNoteRow(x: LinkedNote, data: AppData, vm: MainVm, i: Int, n: Int, block: BlockPos?, onOpen: (String) -> Unit, onOpenNote: (String) -> Unit, onPerson: (String) -> Unit,
                          /** The day's first: close under the day's number. */ first: Boolean = false) {
    val c = M.c
    val s = M.s
    var menu by remember(x.key) { mutableStateOf(false) }
    var edit by remember(x.key) { mutableStateOf(false) }
    if (edit) TextDialog(M.s.edit, x.text, "", onDismiss = { edit = false }) { v ->
        val (kind, ref, id) = x.card.split('|')
        if (v.isNotBlank()) { if (kind == "e") vm.editEntryText("task:$ref", id, v.trim()) else vm.editComment(ref, id, v.trim()) }
        edit = false
    }
    val toTopic = x.link.startsWith("#")
    Margined(if (block == BlockPos.Last) Modifier.padding(bottom = 8.dp) else Modifier, margin = {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = (if (first) 4.dp else 8.dp) + KnotDrop)) { MarginNote(x.at.drop(11).take(5)) }
    }) {
        Box(Modifier.blockTint(block)) {
            Box(Modifier.align(Alignment.TopEnd).padding(top = 8.dp)) { SoftMenu(menu, { menu = false }) {
                SoftMenuItem(s.edit) { menu = false; edit = true }
                SoftMenuItem(if (toTopic) s.toTheTopic else s.toTheMatter) { menu = false; onOpen(x.open) }
                SoftMenuItem(s.toArchive) { menu = false; vm.removeNotePiece(x.card) }
            } }
            PanelPiece(seatOf(i, n)) {
                val inline = LocalInline.current
                val unfolded = inline?.open == x.card
                Column(Modifier.fillMaxWidth().combinedClickable(onClick = { if (inline != null) inline.toggle(x.card) else onOpenNote(x.card) }, onLongClick = { menu = true })
                    .padding(horizontal = if (planner) 0.dp else 16.dp).padding(top = if (first) 4.dp else 8.dp, bottom = 8.dp).then(if (block == BlockPos.Last) Modifier.padding(bottom = 2.dp) else Modifier)) {
                    var layout by remember { mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }
                    if (unfolded) InlinePieceBody(x.card, vm)
                    else {
                    if (x.text.isNotBlank()) {
                        val shown = mentionsText(x.text, data, onPerson, onOpen)
                        WordText(shown, style = Type.body.copy(fontFamily = rowFamily, color = c.ink.copy(alpha = 0.9f), fontSize = rowSize, lineHeight = rowLine),
                            onTextLayout = { layout = it }, maxLines = 3, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.foundMarks(shown.text) { layout })
                    }
                    if (x.files.isNotEmpty()) Box(Modifier.padding(top = 8.dp)) { AttachmentsView(x.files) }
                    }
                    // Where it was written — a link to it (owner, 16 окт); in a block the head says it.
                    if (block == null) WordText(x.link, style = Type.small.copy(color = c.link, fontSize = subSize), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp).clip(RoundedCornerShape(6.dp)).clickable { onOpen(x.open) }.padding(vertical = 2.dp))
                }
            }
        }
    }
}

/**
 * The notes of matters and topics written on [d], for the diary's feed (owner, 17 окт): those of one matter or topic together
 * under its link, on one light tint — as they stood in «Заметки».
 */
internal fun androidx.compose.foundation.lazy.LazyListScope.linkedNotesOfDay(d: LocalDate, list: List<LinkedNote>, data: AppData, vm: MainVm,
    onOpen: (String) -> Unit, onOpenNote: (String) -> Unit, onPerson: (String) -> Unit) {
    val blocks = withTopicHeads(list) { (it as? LinkedNote)?.let { l -> l.open to l.link } }
    val pos = blockPositions(blocks) { (it as? LinkedNote)?.open }
    blocks.forEachIndexed { i, x ->
        if (x is TopicHead) { item("lg" + d + x.id) { TopicBlockHead(x.title, raw = true, top = 14.dp) { onOpen(x.id) } }; return@forEachIndexed }
        val n = x as LinkedNote
        item("l" + n.key) { LinkedNoteRow(n, data, vm, i, blocks.size, pos[i], onOpen, onOpenNote, onPerson) }
    }
}
