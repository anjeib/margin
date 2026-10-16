package ru.anjeib.margin.ui

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
private class LinkedNote(val key: String, val at: String, val text: String, val files: List<ru.anjeib.margin.data.Attachment>,
                         val link: String, val open: String, val sphere: String?,
                         /** The note's own card ([NoteCard]): a tap opens the note itself, the link — where it lives. */
                         val card: String)

/** Every note written in a matter or a topic: the topics' records, the matters' comments and records, a topic's matters' comments. */
private fun AppData.linkedNotes(): List<LinkedNote> {
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
            out += LinkedNote("e" + e.id, e.at, e.text, e.attachments, own, t.id, t.sphere, entryKey(t.id, e.id))
        }
        t.comments.filter { it.text.isNotBlank() }.forEach { cm -> out += LinkedNote("c" + cm.id, cm.at, cm.text, emptyList(), own, t.id, t.sphere, commentKey(t.id, cm.id)) }
        if (t.topic) flat(t.subtasks).forEach { q ->
            q.comments.filter { it.text.isNotBlank() }.forEach { cm ->
                out += LinkedNote("c" + cm.id, cm.at, cm.text, emptyList(), "○ " + q.title + "  ·  # " + t.title, "q:${t.id}:${q.id}", t.sphere, commentKey("q:${t.id}:${q.id}", cm.id))
            }
        }
    }
    return out
}

@Composable
fun DiaryPage(data: AppData, vm: MainVm, onOpen: (String) -> Unit, onPerson: (String) -> Unit, contentPadding: PaddingValues,
              /** A note of a matter or a topic: its own card ([NoteCard]). */
              onOpenNote: (String) -> Unit = {}) {
    val c = M.c
    val s = M.s
    val today = LocalDate.now()
    // «личное / работа» (owner, 16 окт): the same two marks as on «Темы»; chosen again — all of them.
    val sphere by vm.diarySphere.collectAsState()
    var archOpen by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    fun sOk(x: String?) = sphere == null || (if (sphere == "work") x == "work" else x != "work")
    val notes = remember(data, sphere) {
        data.tasks.filter { it.thought && it.subtasks.isEmpty() && !it.done && sOk(it.sphere) }.sortedBy { it.bornAt() }
    }
    val linked = remember(data, sphere) { data.linkedNotes().filter { sOk(it.sphere) } }
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
    val plannerLook = planner
    Box(Modifier.fillMaxSize().ruledPage(pageLines).screenRule()) {
        LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(top = contentPadding.calculateTopPadding(), bottom = contentPadding.calculateBottomPadding())) {
          findable(found, find, { k -> (k as? String)?.let { linkedText[it] }?.let { find != null && findHit(find.words, it) }
              ?: (data.taskOfKey(k)?.let { t -> find != null && findHit(find.words, t.title + " " + data.mentionsPlain(t.entries.joinToString(" ") { it.text })) } == true) }) {
            // Owner, 16 окт: the head stays put; only the diary scrolls.
            pinnedHead("head", plannerLook) {
                Margined(Modifier.padding(top = if (planner) 0.dp else 8.dp)) {
                    Column {
                        Row(verticalAlignment = Alignment.Top) {
                            Text(s.diaryTitle, style = Type.pageTitle.copy(color = c.ink), modifier = Modifier.weight(1f))
                            if (!planner) PageToolIcons()
                        }
                        Row(Modifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            Voice(s.diarySub, Modifier.weight(1f))
                            SphereIcons(sphere, { vm.diarySphere.value = it }, clearable = true)
                        }
                    }
                }
                if (planner) DoubleRule(Modifier.padding(top = 18.dp, bottom = 6.dp))
            }
            if (lines.isEmpty()) item("empty") { Margined(Modifier.padding(top = 18.dp)) { Empty(s.diaryEmpty, s.diaryEmptyHint) } }
            // Owner, 14 окт: no «Сегодня» / «Суббота» line — a day is one block (its number large on the margin by its first note),
            // and the notes in it are parted by a hairline; each note's hour on the margin by it.
            byDay.forEach { (day, list) ->
                // Owner, 15 окт: the day's number is a heading of its own — a row over the day's notes, the weekday quietly by it —
                // so every note's hour stands on the note's own first line (the first note's hour used to hang under the number).
                item("d" + day) {
                    // Owner, 16 окт («огромный отступ от даты до первой записи»): the month under the number is pulled up by an offset
                    // that leaves its old room empty — the head gives that room back, and the day's first note stands close under it.
                    Margined(Modifier.padding(top = 18.dp).layout { m, cs -> val p = m.measure(cs); val cut = 4.dp.roundToPx(); layout(p.width, (p.height - cut).coerceAtLeast(0)) { p.place(0, 0) } },
                        margin = { MarginDay(day.dayOfMonth, day.dayMonthShort().substringAfter(' ')) }) {
                        Box(Modifier.fillMaxWidth().height(58.dp), contentAlignment = Alignment.BottomStart) {
                            Text(day.dowFull().lowercase(), style = Type.small.copy(color = c.graphite, fontSize = 14.5.sp), modifier = Modifier.padding(bottom = 14.dp))
                        }
                    }
                }
                // Owner, 16 окт: notes of one matter or topic in a day stand together, under its link, on one light tint.
                val blocks = withTopicHeads(list) { (it as? LinkedNote)?.let { l -> l.open to l.link } }
                val pos = blockPositions(blocks) { (it as? LinkedNote)?.open }
                blocks.forEachIndexed { i, x ->
                    if (x is TopicHead) { item("lg" + day + x.id) { TopicBlockHead(x.title, raw = true, top = if (i == 0) 4.dp else 14.dp) { onOpen(x.id) } }; return@forEachIndexed }
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
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = if (first) 6.dp else 10.dp)) { MarginNote(hour) }
                        }) {
                            var menu by remember(n.id) { mutableStateOf(false) }
                            val rowActs = LocalRowActions.current
                            // Owner, 16 окт: a long press offers what the note's card does.
                            Box(Modifier.fillMaxWidth()) { Box(Modifier.align(Alignment.TopEnd).padding(top = 8.dp)) { SoftMenu(menu, { menu = false }) {
                                SoftMenuItem(s.openWord) { menu = false; onOpen(n.id) }
                                SoftMenuItem(s.actToMatter) { menu = false; vm.ideaToMatter(n.id) }
                                rowActs?.let { r -> SoftMenuItem(s.actToTopic) { menu = false; r.toTopic(n.id) } }
                                SoftMenuItem(s.toArchive) { menu = false; vm.archiveTopic(n.id) }
                            } } }
                            PanelPiece(seatOf(i, list.size)) {
                                Column(Modifier.fillMaxWidth().combinedClickable(onClick = { onOpen(n.id) }, onLongClick = { menu = true }).padding(horizontal = if (planner) 0.dp else 16.dp).padding(top = if (first) 8.dp else 12.dp, bottom = 12.dp)) {
                                    var titleLayout by remember { mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }
                                    var bodyLayout by remember { mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }
                                    // Three lines of a note at most (owner, 14 окт) — the whole of it is in its card.
                                    if (showTitle) Text(n.title, style = if (planner) Type.body.copy(fontFamily = Literata, color = c.ink, fontSize = 17.sp, lineHeight = 24.sp)
                                        else Type.body.copy(color = c.ink, fontSize = 16.5.sp, lineHeight = 22.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium),
                                        maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                        onTextLayout = { titleLayout = it }, modifier = Modifier.foundMarks(n.title) { titleLayout })
                                    if (body.isNotBlank()) {
                                        val shown = mentionsText(body, data, onPerson, onOpen)
                                        Text(shown, style = Type.body.copy(color = c.ink.copy(alpha = 0.9f), fontSize = if (planner) 15.sp else 16.sp, lineHeight = if (planner) 22.sp else 23.sp), onTextLayout = { bodyLayout = it },
                                            maxLines = if (showTitle) 2 else 3, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                            modifier = Modifier.padding(top = if (showTitle) 3.dp else 0.dp).foundMarks(shown.text) { bodyLayout })
                                    }
                                    n.entries.flatMap { it.attachments }.takeIf { it.isNotEmpty() }?.let { Box(Modifier.padding(top = 8.dp)) { AttachmentsView(it) } }
                                    // Who and what the note touches, quietly — when it was made in the field with marks.
                                    val tags = (n.people.map { it } + listOfNotNull(n.place?.let { "⌖ $it" })).filter { t -> !plainBody.contains(t.removePrefix("⌖ ")) }
                                    if (tags.isNotEmpty()) Text(tags.joinToString("  ·  "), style = Type.small.copy(color = c.link, fontSize = 13.5.sp), modifier = Modifier.padding(top = 4.dp))
                                }
                            }
                        }
                    }
                }
            }
            // Owner, 16 окт: archived notes at the end of the page, folded — the diary's own and those of matters and topics.
            val archived = data.tasks.filter { it.thought && it.done && it.subtasks.isEmpty() && sOk(it.sphere) }.map { t ->
                ArchItem(t.id, t.title.ifBlank { t.entries.firstOrNull()?.text.orEmpty() }, t.doneAt, open = { onOpen(t.id) }, restore = { vm.reviveTopic(t.id) }, purge = { vm.removeMatter(t.id) })
            } + data.archivedNotes(vm, onOpenNote) { sOk(it) }
            archiveFold("arch", archived, archOpen) { archOpen = !archOpen }
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
private fun LinkedNoteRow(x: LinkedNote, data: AppData, vm: MainVm, i: Int, n: Int, block: BlockPos?, onOpen: (String) -> Unit, onOpenNote: (String) -> Unit, onPerson: (String) -> Unit,
                          /** The day's first: close under the day's number. */ first: Boolean = false) {
    val c = M.c
    val s = M.s
    var menu by remember(x.key) { mutableStateOf(false) }
    val toTopic = x.link.startsWith("#")
    Margined(if (block == BlockPos.Last) Modifier.padding(bottom = 8.dp) else Modifier, margin = {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = if (first) 6.dp else 10.dp)) { MarginNote(x.at.drop(11).take(5)) }
    }) {
        Box(Modifier.blockTint(block)) {
            Box(Modifier.align(Alignment.TopEnd).padding(top = 8.dp)) { SoftMenu(menu, { menu = false }) {
                SoftMenuItem(s.openWord) { menu = false; onOpenNote(x.card) }
                SoftMenuItem(if (toTopic) s.toTheTopic else s.toTheMatter) { menu = false; onOpen(x.open) }
                SoftMenuItem(s.toArchive) { menu = false; vm.removeNotePiece(x.card) }
            } }
            PanelPiece(seatOf(i, n)) {
                Column(Modifier.fillMaxWidth().combinedClickable(onClick = { onOpenNote(x.card) }, onLongClick = { menu = true })
                    .padding(horizontal = if (planner) 0.dp else 16.dp).padding(top = if (first) 8.dp else 12.dp, bottom = 12.dp).then(if (block == BlockPos.Last) Modifier.padding(bottom = 2.dp) else Modifier)) {
                    var layout by remember { mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }
                    if (x.text.isNotBlank()) {
                        val shown = mentionsText(x.text, data, onPerson, onOpen)
                        Text(shown, style = Type.body.copy(color = c.ink.copy(alpha = 0.9f), fontSize = if (planner) 15.sp else 16.sp, lineHeight = if (planner) 22.sp else 23.sp),
                            onTextLayout = { layout = it }, maxLines = 3, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.foundMarks(shown.text) { layout })
                    }
                    if (x.files.isNotEmpty()) Box(Modifier.padding(top = 8.dp)) { AttachmentsView(x.files) }
                    // Where it was written — a link to it (owner, 16 окт); in a block the head says it.
                    if (block == null) Text(x.link, style = Type.small.copy(color = c.link, fontSize = 13.5.sp), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp).clip(RoundedCornerShape(6.dp)).clickable { onOpen(x.open) }.padding(vertical = 2.dp))
                }
            }
        }
    }
}
