package ru.anjeib.margin.ui

import androidx.compose.foundation.combinedClickable

import ru.anjeib.margin.data.likelyTopic

import androidx.compose.ui.text.withStyle

import androidx.compose.ui.text.buildAnnotatedString

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.anjeib.margin.data.AppData
import ru.anjeib.margin.data.Attachment
import ru.anjeib.margin.data.Entry
import ru.anjeib.margin.data.dayMonthShort
import ru.anjeib.margin.i18n.L
import java.time.LocalDate

/** The inbox note (files and words that couldn't be sorted) — never shown among ordinary notes. */
fun AppData.inboxNote() = notes.find { it.title == L.s.inboxNote }

/** How many things wait in «Входящие»: typed offline (not sorted yet) plus what was saved without sorting. */
fun AppData.inboxCount() = queue.size + (inboxNote()?.entries?.size ?: 0)

/** «7 окт / 18:40» on the margin. */
@Composable
private fun StampMargin(at: String) {
    val day = runCatching { LocalDate.parse(at.take(10)) }.getOrNull()?.dayMonthShort() ?: ""
    MarginText(listOf(day, at.drop(11).take(5)).filter { it.isNotBlank() }.joinToString("\n"))
}

/**
 * «Входящие»: whatever hasn't been sorted into matters and topics yet — written without internet, saved without words.
 * As on «Сегодня»: the day on the margin, one quiet row per thing; a tap opens a small menu — open, to a topic, read the text, remove.
 */
@Composable
fun InboxScreen(data: AppData, vm: MainVm, onOpenNote: (String) -> Unit, contentPadding: PaddingValues) {
    val c = M.c
    val s = M.s
    val note = data.inboxNote()
    var editing by remember { mutableStateOf<ru.anjeib.margin.data.Entry?>(null) }
    var viewing by remember { mutableStateOf<Attachment?>(null) }
    val working by vm.working.collectAsState()
    val topics = remember(data.tasks) {
        data.tasks.filter { it.topic && !it.done && !(it.thought && it.subtasks.isEmpty()) }
            .sortedByDescending { t -> maxOf(t.entries.maxOfOrNull { it.at } ?: "", t.events.maxOfOrNull { it.at } ?: "", t.createdAt) }.take(8)
    }
    val today = LocalDate.now()
    fun dayOf(at: String) = runCatching { LocalDate.parse(at.take(10)) }.getOrNull()
    Box(Modifier.fillMaxSize().screenRule()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding.vertical()) {
            item("h") { PageHead(s.inboxNote, s.inboxHint) }
            if (data.queue.isEmpty() && note?.entries.isNullOrEmpty()) item("empty") { Margined { Empty(s.inboxEmpty, s.inboxEmptyHint) } }
            if (data.queue.isNotEmpty()) {
                item("q-h") { Margined(Modifier.padding(top = 10.dp, bottom = 2.dp)) { GroupBadge(s.waitingNet) } }
                var last: LocalDate? = null
                data.queue.forEach { q ->
                    val d = dayOf(q.at); val first = d != last; last = d
                    item("q" + q.id) {
                        InboxRow(day = if (first) d else null, today = today, icon = { Icon(Icons.Outlined.CloudOff, null, tint = c.graphite, modifier = Modifier.size(15.dp)) },
                            title = q.text.ifBlank { attachmentsSummary(q.files) ?: "" }, meta = listOfNotNull(q.at.drop(11).take(5), s.sortsWhenOnline).joinToString("  ·  "),
                            thumb = null, onThumb = {}, onRemove = { vm.dropQueued(q.id) },
                            menu = { close, _ -> SoftMenuItem(s.removeWord, danger = true) { close(); vm.dropQueued(q.id) } })
                    }
                }
            }
            if (note != null && note.entries.isNotEmpty()) {
                // Without a badge: everything here is «unplaced» — only what waits for the internet is set apart.
                if (data.queue.isNotEmpty()) item("n-h") { Spacer(Modifier.height(10.dp)) }
                var last: LocalDate? = null
                note.entries.reversed().forEach { e ->
                    val d = dayOf(e.at); val first = d != last; last = d
                    val a = e.attachments.firstOrNull()
                    item("e" + e.id) {
                        InboxRow(day = if (first) d else null, today = today,
                            icon = { Icon(kindIcon(a?.kind ?: "text"), null, tint = c.graphite, modifier = Modifier.size(15.dp)) },
                            // A photo without words is «Фото без подписи», not its file name; the kind is the mark on the left.
                            title = e.text.ifBlank { a?.caption ?: a?.let { if (it.kind == "photo") s.photoNoCaption else attachmentLabel(it).ifBlank { it.name } } ?: "" },
                            meta = listOfNotNull(e.at.drop(11).take(5), attachmentsSummary(e.attachments)?.takeIf { e.attachments.size > 1 || e.text.isNotBlank() },
                                if (a != null && "ocr:${a.id}" in working) "…" else null).joinToString("  ·  "),
                            thumb = a?.takeIf { it.kind == "photo" || it.kind == "video" }, onThumb = { viewing = it },
                            onOpen = { if (a != null) viewing = a else editing = e },
                            onRemove = { vm.removeEntry("note:${note.id}", e.id) }, canPick = topics.isNotEmpty(),
                            menu = { close, pick ->
                                var picking by remember(pick) { mutableStateOf(pick) }
                                if (!picking) {
                                    if (a != null) SoftMenuItem(s.openWord) { close(); viewing = a } else SoftMenuItem(s.openWord) { close(); onOpenNote(note.id) }
                                    // The topic it most likely belongs to, by name — then the full list.
                                    val likely = remember(e.id) { data.likelyTopic(e.text + " " + e.attachments.joinToString(" ") { it.caption ?: it.name }) }
                                    likely?.let { lt -> SoftMenuItem(s.likelyTopic(lt.title).replaceFirstChar { it.uppercase() }) { close(); vm.inboxToTopic(e.id, lt.id) } }
                                    if (topics.isNotEmpty()) SoftMenuItem(s.toTopicPick) { picking = true }
                                    e.attachments.firstOrNull { it.kind == "photo" && it.ocr == null }?.let { ph -> SoftMenuItem(s.readTextWord) { close(); vm.photoText("note:${note.id}", e.id, ph, "insert") } }
                                    SoftMenuItem(s.removeWord, danger = true) { close(); vm.removeEntry("note:${note.id}", e.id) }
                                } else {
                                    // The likely topic first, then the rest.
                                    val likely = remember(e.id) { data.likelyTopic(e.text + " " + e.attachments.joinToString(" ") { it.caption ?: it.name }) }
                                    (listOfNotNull(likely) + topics.filter { it.id != likely?.id }).forEach { t -> SoftMenuItem(t.title) { close(); vm.inboxToTopic(e.id, t.id) } }
                                }
                            })
                    }
                }
            }
        }
    }
    viewing?.let { a -> Viewer(a, vm, onClose = { viewing = null }) }
    // A text without a file: a tap opens it to read and correct.
    editing?.let { e -> data.inboxNote()?.let { n -> TextDialog(s.edit, e.text, "", onDismiss = { editing = null }) { v -> vm.editEntryText("note:${n.id}", e.id, v); editing = null } } }
}

/** One thing in «Входящие»: the day on the margin (the first of the day), the kind mark, the words, a quiet line; a tap — its menu. */
@Composable
private fun InboxRow(day: LocalDate?, today: LocalDate, icon: @Composable () -> Unit, title: String, meta: String, thumb: Attachment?, onThumb: (Attachment) -> Unit,
                     onOpen: (() -> Unit)? = null, onRemove: (() -> Unit)? = null, canPick: Boolean = false,
                     menu: @Composable (close: () -> Unit, pick: Boolean) -> Unit) {
    val c = M.c
    val s = M.s
    var open by remember { mutableStateOf(false) }
    // The swipe to the right asks «в какую тему» at once.
    var pick by remember { mutableStateOf(false) }
    Column {
        if (day != null) Hairline(Modifier.padding(start = MarginW, top = 6.dp))
        Margined(margin = day?.let { d -> { DayMargin(d, today, false, strong = true) } }) {
            Box {
                // A tap opens the thing itself; a long press — what can be done with it; swipes — away or into a topic.
                SwipeActions(left = onRemove?.let { s.removeWord to it }, right = if (canPick) (s.toTopicPick to { pick = true; open = true }) else null, radius = 10) {
                Row(Modifier.fillMaxWidth().background(c.bg).clip(RoundedCornerShape(10.dp)).combinedClickable(onClick = { if (onOpen != null) onOpen() else open = true }, onLongClick = { open = true })
                    .padding(vertical = 10.dp), verticalAlignment = Alignment.Top) {
                    Box(Modifier.size(20.dp, 22.dp), contentAlignment = Alignment.Center) { icon() }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(title, style = Type.body.copy(color = c.ink, fontSize = 15.5.sp), maxLines = 3, overflow = TextOverflow.Ellipsis)
                        // The hour first in ink, what else quiet after it.
                        val dateSp = dateSpan()
                        if (meta.isNotBlank()) Text(buildAnnotatedString {
                            val hour = Regex("^\\d{2}:\\d{2}").find(meta)?.value
                            if (hour != null) { withStyle(dateSp) { append(hour) }; append(meta.removePrefix(hour)) } else append(meta)
                        }, style = Type.small.copy(color = c.graphite, fontSize = 13.sp), modifier = Modifier.padding(top = 1.dp))
                    }
                    thumb?.let { a -> Box(Modifier.padding(start = 10.dp).clip(RoundedCornerShape(10.dp)).clickable { onThumb(a) }) { Thumb(a, 56.dp) } }
                }
                }
                SoftMenu(open, { open = false; pick = false }) { menu({ open = false; pick = false }, pick) }
            }
        }
    }
}
