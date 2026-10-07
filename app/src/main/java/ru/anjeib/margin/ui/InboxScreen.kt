package ru.anjeib.margin.ui

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
 * «Входящие»: whatever hasn't been sorted into matters and topics yet — written without internet,
 * saved without words; later, things from mail. On the margin, when each one came.
 */
@Composable
fun InboxScreen(data: AppData, vm: MainVm, onOpenNote: (String) -> Unit, contentPadding: PaddingValues) {
    val c = M.c
    val s = M.s
    val note = data.inboxNote()
    var viewing by remember { mutableStateOf<Attachment?>(null) }
    val working by vm.working.collectAsState()
    val topics = remember(data.tasks) {
        data.tasks.filter { it.topic && !it.done }
            .sortedByDescending { t -> maxOf(t.entries.maxOfOrNull { it.at } ?: "", t.events.maxOfOrNull { it.at } ?: "", t.createdAt) }.take(8)
    }
    Box(Modifier.fillMaxSize().marginRule(c.accent.copy(alpha = 0.85f), over = true)) {
        // The rule is the left edge here: only the vertical part of the shared padding.
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = contentPadding.calculateTopPadding(), bottom = contentPadding.calculateBottomPadding())) {
            item("h") {
                Margined(Modifier.padding(top = 14.dp, bottom = 6.dp)) {
                    Column {
                        Text(s.inboxNote, style = Type.display.copy(color = c.ink))
                        Spacer(Modifier.height(4.dp))
                        Text(s.inboxHint, style = Type.small.copy(color = c.graphite, fontSize = 15.sp))
                    }
                }
            }
            if (data.queue.isEmpty() && note?.entries.isNullOrEmpty()) item("empty") { Margined { Empty(s.inboxEmpty, s.inboxEmptyHint) } }
            if (data.queue.isNotEmpty()) {
                item("q-h") { Margined(Modifier.padding(top = 18.dp, bottom = 4.dp)) { GroupBadge(s.waitingNet) } }
                items(data.queue, key = { "q" + it.id }) { q ->
                    Margined(Modifier.animateItem().padding(top = 10.dp, bottom = 4.dp), margin = { StampMargin(q.at) }) {
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(Icons.Outlined.CloudOff, null, tint = c.graphite, modifier = Modifier.padding(top = 2.dp).size(18.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(q.text.ifBlank { attachmentsSummary(q.files) ?: "" }, style = Type.body.copy(color = c.ink, fontSize = 16.sp), maxLines = 4, overflow = TextOverflow.Ellipsis)
                                Text(listOfNotNull(s.sortsWhenOnline, attachmentsSummary(q.files)?.takeIf { q.text.isNotBlank() }).joinToString("  ·  "),
                                    style = Type.small.copy(color = c.graphite, fontSize = 13.sp), modifier = Modifier.padding(top = 2.dp))
                                Row(Modifier.padding(top = 2.dp)) { PaperLink(s.removeWord, quiet = true) { vm.dropQueued(q.id) } }
                            }
                        }
                    }
                }
            }
            if (note != null && note.entries.isNotEmpty()) {
                item("n-h") { Margined(Modifier.padding(top = 22.dp, bottom = 4.dp)) { GroupBadge(s.unplacedBadge) } }
                items(note.entries.reversed(), key = { "e" + it.id }) { e ->
                    Margined(Modifier.animateItem().padding(top = 12.dp, bottom = 6.dp), margin = { StampMargin(e.at) }) {
                        UnplacedEntry(e, topics, working = working,
                            onView = { viewing = it },
                            onTopic = { vm.inboxToTopic(e.id, it) },
                            onRead = { a -> vm.photoText("note:${note.id}", e.id, a, "insert") },
                            onOpen = { onOpenNote(note.id) },
                            onRemove = { vm.removeEntry("note:${note.id}", e.id) })
                    }
                }
            }
        }
    }
    viewing?.let { a -> Viewer(a, vm, onClose = { viewing = null }) }
}

/** One thing saved without a place: its words, its files at full size, and where it could go. */
@Composable
private fun UnplacedEntry(
    e: Entry, topics: List<ru.anjeib.margin.data.Task>, working: Set<String>,
    onView: (Attachment) -> Unit, onTopic: (String) -> Unit, onRead: (Attachment) -> Unit, onOpen: () -> Unit, onRemove: () -> Unit,
) {
    val c = M.c
    val s = M.s
    var picking by remember(e.id) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        if (e.text.isNotBlank()) Text(e.text, style = Type.body.copy(color = c.ink, fontSize = 16.sp), modifier = Modifier.clickable(onClick = onOpen))
        e.items.forEach { i -> Text((if (i.done) "☑ " else "☐ ") + i.title, style = Type.body.copy(color = c.ink, fontSize = 15.sp)) }
        e.attachments.forEach { a ->
            Spacer(Modifier.height(if (e.text.isBlank()) 0.dp else 8.dp))
            if (a.kind == "photo" || a.kind == "video") Column {
                BoxWithConstraints(Modifier.widthIn(max = 220.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { onView(a) }) { Thumb(a, maxWidth) }
                Text(a.caption ?: a.ocr?.lineSequence()?.firstOrNull()?.take(60) ?: s.photoNoCaption,
                    style = Type.small.copy(color = c.graphite, fontSize = 13.5.sp), modifier = Modifier.padding(top = 6.dp))
            } else BigMaterial(a, onView)
        }
        Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (topics.isNotEmpty()) PaperLink(s.toTopicPick) { picking = !picking }
            val photo = e.attachments.firstOrNull { it.kind == "photo" && it.ocr == null }
            if (photo != null) {
                Dot()
                if ("ocr:${photo.id}" in working) Text("…", style = Type.small.copy(color = c.graphite, fontSize = 14.sp))
                else PaperLink(s.recognizeWord) { onRead(photo) }
            }
            Dot()
            PaperLink(s.removeWord, quiet = true, onClick = onRemove)
        }
        androidx.compose.animation.AnimatedVisibility(picking, enter = androidx.compose.animation.expandVertically(androidx.compose.animation.core.tween(240)) + androidx.compose.animation.fadeIn(SoftTween),
            exit = androidx.compose.animation.shrinkVertically(androidx.compose.animation.core.tween(200)) + androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(160))) {
            Column(Modifier.padding(top = 4.dp).widthIn(max = 320.dp).clip(RoundedCornerShape(16.dp)).background(c.card).border(BorderStroke(1.dp, c.hairline), RoundedCornerShape(16.dp))) {
                topics.forEach { t ->
                    Row(Modifier.fillMaxWidth().clickable { picking = false; onTopic(t.id) }.padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                        TopicGlyph(c.graphite)
                        Spacer(Modifier.width(10.dp))
                        Text(t.title, style = Type.body.copy(color = c.ink, fontSize = 15.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun Dot() = Text("  ·  ", style = Type.small.copy(color = M.c.faint, fontSize = 14.sp))
