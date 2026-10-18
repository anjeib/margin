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
fun InboxScreen(data: AppData, vm: MainVm, onOpenNote: (String) -> Unit, contentPadding: PaddingValues,
                /** «Разобрать»: the words to the main field, read there as everything typed is. */
                onParse: (Entry) -> Unit = {}) {
    val c = M.c
    val s = M.s
    val note = data.inboxNote()
    var editing by remember { mutableStateOf<ru.anjeib.margin.data.Entry?>(null) }
    var viewing by remember { mutableStateOf<Attachment?>(null) }
    val working by vm.working.collectAsState()
    val heardWaiting by ru.anjeib.margin.voice.Transcripts.waiting.collectAsState()
    val heardProgress by ru.anjeib.margin.voice.Transcripts.progress.collectAsState()
    val heardUnreadable by ru.anjeib.margin.voice.Transcripts.unreadable.collectAsState()
    val topics = remember(data.tasks) {
        data.tasks.filter { it.topic && !it.done && !(it.thought && it.subtasks.isEmpty()) }
            .sortedByDescending { t -> maxOf(t.entries.maxOfOrNull { it.at } ?: "", t.events.maxOfOrNull { it.at } ?: "", t.createdAt) }.take(8)
    }
    val today = LocalDate.now()
    fun dayOf(at: String) = runCatching { LocalDate.parse(at.take(10)) }.getOrNull()
    Box(Modifier.fillMaxSize().screenRule()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding.vertical()) {
            pageHeadItem("h") { PageHead(s.inboxNote, s.inboxHint) }
            if (data.queue.isEmpty() && note?.entries.isNullOrEmpty()) item("empty") { Margined { Empty(s.inboxEmpty, s.inboxEmptyHint) } }
            if (data.queue.isNotEmpty()) {
                item("q-h") { SectionHead(s.waitingNet, count = data.queue.size, top = 10.dp, rule = false) }
                data.queue.forEachIndexed { i, q ->
                    item("q" + q.id) {
                        InboxRow(seat = seatOf(i, data.queue.size), hour = q.at.drop(11).take(5), icon = { Icon(Icons.Outlined.CloudOff, null, tint = c.graphite, modifier = Modifier.size(19.dp)) },
                            title = q.text.ifBlank { attachmentsSummary(q.files) ?: "" }, meta = s.sortsWhenOnline,
                            thumb = null, onThumb = {}, onRemove = { vm.dropQueued(q.id) },
                            menu = { close, _ -> SoftMenuItem(s.removeWord, danger = true) { close(); vm.dropQueued(q.id) } })
                    }
                }
            }
            if (note != null && note.entries.isNotEmpty()) {
                // v8: by day — the day as a section's label, the hour on the margin of each row.
                val byDay = note.entries.reversed().groupBy { dayOf(it.at) }
                var firstDay = data.queue.isEmpty()
                byDay.forEach { (d, list) ->
                val f = firstDay; firstDay = false
                item("d" + d) { SectionHead(d?.let { dayTalk(it, today) } ?: "—", count = list.size, rule = !f) }
                list.forEachIndexed { i, e ->
                    val a = e.attachments.firstOrNull()
                    item("e" + e.id) {
                        InboxRow(seat = seatOf(i, list.size), hour = e.at.drop(11).take(5),
                            icon = { Icon(kindIcon(a?.kind ?: "text"), null, tint = c.graphite, modifier = Modifier.size(19.dp)) },
                            // A photo without words is «Фото без подписи», not its file name; the kind is the mark on the left.
                            // A voice without words shows what was said in it, once the phone has read it.
                            title = e.text.ifBlank { a?.caption ?: a?.transcript?.takeIf { a.kind == "audio" && it.isNotBlank() }
                                ?: a?.let { if (it.kind == "photo") s.photoNoCaption else attachmentLabel(it).ifBlank { it.name } } ?: "" },
                            meta = listOfNotNull(e.from, attachmentsSummary(e.attachments)?.takeIf { e.attachments.size > 1 || e.text.isNotBlank() },
                                a?.takeIf { it.kind == "audio" && e.text.isBlank() && !it.transcript.isNullOrBlank() }?.let { attachmentLabel(it) },
                                if (a != null && "ocr:${a.id}" in working) "…" else null,
                                a?.takeIf { it.kind == "audio" }?.let { voiceState(it, heardWaiting, heardProgress, heardUnreadable) }).joinToString("  ·  "),
                            thumb = a?.takeIf { it.kind == "photo" || it.kind == "video" }, onThumb = { viewing = it },
                            onOpen = { if (a != null) viewing = a else editing = e },
                            onRemove = { vm.removeEntry("note:${note.id}", e.id) }, canPick = topics.isNotEmpty(),
                            menu = { close, pick ->
                                var picking by remember(pick) { mutableStateOf(pick) }
                                if (!picking) {
                                    if (a != null) SoftMenuItem(s.openWord) { close(); viewing = a } else SoftMenuItem(s.openWord) { close(); onOpenNote(note.id) }
                                    // Sorted by hand (owner, 14 окт): a matter, a note, or — for words — read by the phone in the main field first.
                                    SoftMenuItem(s.inboxToDealWord) { close(); vm.inboxToDeal(e.id) }
                                    SoftMenuItem(s.inboxToNoteWord) { close(); vm.inboxToNote(e.id) }
                                    if (e.text.isNotBlank()) SoftMenuItem(s.inboxParseWord) { close(); onParse(e) }
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
    }
    viewing?.let { a -> Viewer(a, vm, onClose = { viewing = null }) }
    // A text without a file: a tap opens it to read and correct.
    editing?.let { e -> data.inboxNote()?.let { n -> TextDialog(s.edit, e.text, "", onDismiss = { editing = null }) { v -> vm.editEntryText("note:${n.id}", e.id, v); editing = null } } }
}

/** One thing in «Входящие»: its hour on the margin, the kind mark, the words, a quiet line; a tap opens it, a hold — its menu. */
/** «Распознаю · 40%» / «Запись не читается» for a voice in the inbox; null when there is nothing to say. */
private fun voiceState(a: Attachment, waiting: Set<String>, progress: Map<String, Float>, unreadable: Set<String>): String? = when {
    progress[a.id] != null -> ru.anjeib.margin.i18n.L.s.transcribingAt((progress[a.id]!! * 100).toInt())
    a.id in waiting -> ru.anjeib.margin.i18n.L.s.transcribing
    a.id in unreadable -> ru.anjeib.margin.i18n.L.s.voiceUnreadable
    a.transcript == "" -> ru.anjeib.margin.i18n.L.s.voiceNoWords
    else -> null
}

@Composable
private fun InboxRow(seat: Seat, hour: String, icon: @Composable () -> Unit, title: String, meta: String, thumb: Attachment?, onThumb: (Attachment) -> Unit,
                     onOpen: (() -> Unit)? = null, onRemove: (() -> Unit)? = null, canPick: Boolean = false,
                     menu: @Composable (close: () -> Unit, pick: Boolean) -> Unit) {
    val s = M.s
    var open by remember { mutableStateOf(false) }
    // The swipe to the right asks «в какую тему» at once.
    var pick by remember { mutableStateOf(false) }
    Margined(margin = hour.takeIf { it.isNotBlank() && it != "00:00" }?.let { h -> { RowMargin(h) } }) {
        Box {
            // A tap opens the thing itself; a long press — what can be done with it; swipes — away or into a topic.
            SwipeActions(left = onRemove?.let { s.removeWord to it }, right = if (canPick) (s.toTopicPick to { pick = true; open = true }) else null,
                modifier = Modifier.bleed(PanelBleed), shape = seat.shape()) {
                PanelPiece(seat, bleed = false) {
                    RowBody(mark = icon, onClick = { if (onOpen != null) onOpen() else open = true }, onLong = { open = true },
                        trailing = thumb?.let { a -> { Box(Modifier.clip(RoundedCornerShape(10.dp)).clickable { onThumb(a) }) { Thumb(a, 52.dp) } } }) {
                        RowTitle(title)
                        if (meta.isNotBlank()) RowSub(meta)
                    }
                }
            }
            SoftMenu(open, { open = false; pick = false }) { menu({ open = false; pick = false }, pick) }
        }
    }
}
