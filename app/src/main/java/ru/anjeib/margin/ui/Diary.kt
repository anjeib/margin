package ru.anjeib.margin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
@Composable
fun DiaryPage(data: AppData, vm: MainVm, onOpen: (String) -> Unit, onPerson: (String) -> Unit, contentPadding: PaddingValues) {
    val c = M.c
    val s = M.s
    val today = LocalDate.now()
    val notes = remember(data) {
        data.tasks.filter { it.thought && it.subtasks.isEmpty() && !it.done }.sortedBy { it.bornAt() }
    }
    val byDay = remember(notes) { notes.groupBy { runCatching { LocalDate.parse(it.bornAt().take(10)) }.getOrDefault(today) }.toSortedMap(compareByDescending { it }) }
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
          findable(found, find, { k -> data.taskOfKey(k)?.let { t -> find != null && findHit(find.words, t.title + " " + data.mentionsPlain(t.entries.joinToString(" ") { it.text })) } == true }) {
            if (plannerLook) stickyHeader("tools") { PinnedTools() }
            item("head") {
                Margined(Modifier.padding(top = if (planner) 0.dp else 8.dp)) {
                    Column {
                        Row(verticalAlignment = Alignment.Top) {
                            Text(s.diaryTitle, style = Type.pageTitle.copy(color = c.ink), modifier = Modifier.weight(1f))
                            if (!planner) PageToolIcons()
                        }
                        Voice(s.diarySub, Modifier.padding(top = 2.dp))
                    }
                }
                if (planner) DoubleRule(Modifier.padding(top = 18.dp, bottom = 6.dp))
            }
            if (notes.isEmpty()) item("empty") { Margined(Modifier.padding(top = 18.dp)) { Empty(s.diaryEmpty, s.diaryEmptyHint) } }
            // Owner, 14 окт: no «Сегодня» / «Суббота» line — a day is one block (its number large on the margin by its first note),
            // and the notes in it are parted by a hairline; each note's hour on the margin by it.
            byDay.forEach { (day, list) ->
                // Owner, 15 окт: the day's number is a heading of its own — a row over the day's notes, the weekday quietly by it —
                // so every note's hour stands on the note's own first line (the first note's hour used to hang under the number).
                item("d" + day) {
                    Margined(Modifier.padding(top = 18.dp, bottom = 2.dp), margin = { MarginDay(day.dayOfMonth, day.dayMonthShort().substringAfter(' ')) }) {
                        Box(Modifier.fillMaxWidth().height(58.dp), contentAlignment = Alignment.BottomStart) {
                            Text(day.dowFull().lowercase(), style = Type.small.copy(color = c.graphite, fontSize = 14.5.sp), modifier = Modifier.padding(bottom = 14.dp))
                        }
                    }
                }
                list.forEachIndexed { i, n ->
                    item("n" + n.id) {
                        val at = n.bornAt()
                        val body = n.entries.filter { it.text.isNotBlank() }.joinToString("\n\n") { it.text }
                        val plainBody = data.mentionsPlain(body)
                        // The title only when the words don't already begin with it (a note written here is its own first line).
                        val showTitle = n.title.isNotBlank() && !plainBody.trimStart().startsWith(n.title.trimEnd('…').take(40))
                        val hour = at.drop(11).take(5)
                        Margined(Modifier.landing(n.id in landed, 0), margin = {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 10.dp)) { MarginNote(hour) }
                        }) {
                            PanelPiece(seatOf(i, list.size)) {
                                Column(Modifier.fillMaxWidth().clickable { onOpen(n.id) }.padding(horizontal = if (planner) 0.dp else 16.dp, vertical = 12.dp)) {
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
            item("end") { PageEnd(pageLines) }
            item("tail") { Box(Modifier.width(1.dp).height(40.dp)) }
          }
        }
    }
}
