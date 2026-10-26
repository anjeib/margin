package ru.anjeib.margin.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.anjeib.margin.data.AppData
import ru.anjeib.margin.data.Attachment
import ru.anjeib.margin.data.Subtask
import ru.anjeib.margin.data.questionRef

/**
 * A note written in a topic or a matter, as a card of its own (owner, 16 окт: a tap on such a note — in the topic or in
 * «Заметки» — opens the note, not the place it lives in). The key says which: «e|<task>|<entry>» — a record of a topic or a
 * matter; «c|<matter ref>|<comment>» — a matter's note (the ref may be a topic's matter, «q:<тема>:<дело>»).
 */
data class NotePiece(val key: String, val at: String, val text: String, val files: List<Attachment>, val removed: Boolean,
                     /** Where it lives: what opens it, and its links over the title — «# Тема», «○ Дело». */
                     val ownerId: String, val trail: List<Pair<String, String>>, val topicOwned: Boolean,
                     /** Whose «личное / работа» the note shows: the topic's or the matter's. */
                     val sphereOf: String = ownerId, val sphere: String? = null)

fun entryKey(taskId: String, entryId: String) = "e|$taskId|$entryId"
fun commentKey(ref: String, commentId: String) = "c|$ref|$commentId"

fun AppData.notePiece(key: String): NotePiece? {
    val p = key.split('|')
    if (p.size != 3) return null
    val (kind, ref, id) = p
    fun flat(l: List<Subtask>): List<Subtask> = l.flatMap { listOf(it) + flat(it.children) }
    return when (kind) {
        "e" -> {
            val t = tasks.find { it.id == ref } ?: return null
            val live = t.entries.find { it.id == id }
            val e = live ?: t.removedEntries.find { it.id == id } ?: return null
            NotePiece(key, e.at, e.text, e.attachments, live == null, t.id, listOf(t.id to ((if (t.topic) "# " else "○ ") + t.title)), t.topic, t.id, t.sphere)
        }
        "c" -> {
            val q = questionRef(ref)
            if (q != null) {
                val t = tasks.find { it.id == q.first } ?: return null
                val sub = flat(t.subtasks).find { it.id == q.second } ?: return null
                val live = sub.comments.find { it.id == id }
                val cm = live ?: sub.removedComments.find { it.id == id } ?: return null
                NotePiece(key, cm.at, cm.text, emptyList(), live == null, ref, listOf(t.id to "# " + t.title, ref to "○ " + sub.title), false, t.id, t.sphere)
            } else {
                val t = tasks.find { it.id == ref } ?: return null
                val live = t.comments.find { it.id == id }
                val cm = live ?: t.removedComments.find { it.id == id } ?: return null
                NotePiece(key, cm.at, cm.text, emptyList(), live == null, t.id, listOf(t.id to "○ " + t.title), false, t.id, t.sphere)
            }
        }
        else -> null
    }
}

/**
 * The head of every note's card (owner, 16 окт: «Одиссея» and «МАП» looked different — one look, after the diary's own note): the
 * day large on the margin with its weekday; right of the rule where it lives («# Тема  ○ Дело»), a title only when it is not
 * the words' own beginning, «заметка» and «личное / работа». The words themselves are under the tools, never repeated up here.
 */
@Composable
fun NoteHead(day: java.time.LocalDate?, trail: List<Pair<String, String>>, onTrail: (String) -> Unit, sphere: String?, onSphere: ((String) -> Unit)?,
             title: (@Composable () -> Unit)? = null,
             /** The card's tools, under the marks — beside the day on the margin, so no gap opens under a short head. */
             tools: (@Composable () -> Unit)? = null) {
    val c = M.c
    val s = M.s
    Margined(margin = { CardMargin(day, under = day?.weekdayWord()) }) {
        Column {
            if (trail.isNotEmpty()) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                trail.forEachIndexed { i, (to, label) ->
                    if (i > 0) Spacer(Modifier.size(10.dp))
                    Text(label, style = Type.italic.copy(color = c.link, fontSize = 15.5.sp), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false).clip(RoundedCornerShape(6.dp)).clickable { onTrail(to) }.padding(vertical = 6.dp))
                }
            }
            title?.invoke()
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(s.cardKindNote, style = Type.italic.copy(color = c.graphite, fontSize = 16.sp), modifier = Modifier.weight(1f))
                if (onSphere != null) SphereIcons(sphere, { k -> k?.let(onSphere) })
            }
            tools?.let { Box(Modifier.padding(top = 12.dp)) { it() } }
        }
    }
}

/** The line between a card's tools and its words. */
@Composable
fun CardRule() = Margined(Modifier.padding(top = 6.dp, bottom = 4.dp)) { Box(Modifier.fillMaxWidth().height(1.dp).background(M.c.hairline)) }

@Composable
fun NoteCard(key: String, data: AppData, vm: MainVm, onClose: () -> Unit, onOpen: (String) -> Unit) {
    val c = M.c
    val s = M.s
    val n = data.notePiece(key)
    if (n == null) { LaunchedEffect(key) { onClose() }; return }
    val (kind, ref, id) = key.split('|')
    var text by remember(key) { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(n.text)) }
    fun save() {
        val v = text.text
        if (v.trim() == n.text.trim() || n.removed) return
        // An emptied note isn't deleted by accident: only «В архив» puts it away.
        if (v.isBlank()) return
        if (kind == "e") vm.editEntryText("task:$ref", id, v.trim()) else vm.editComment(ref, id, v.trim())
    }
    LaunchedEffect(text.text) { if (text.text != n.text) { kotlinx.coroutines.delay(800); save() } }
    DisposableEffect(key) { onDispose { save() } }
    fun remove() { vm.removeNotePiece(key); onClose() }
    val dt = runCatching { java.time.LocalDateTime.parse(n.at.take(16)) }.getOrNull()
    CardPage(onClose = { save(); onClose() }) {
        Column(Modifier.weight(1f, fill = cardFill).fillMaxWidth()) {
            Column(Modifier.weight(1f, fill = cardFill).fillMaxWidth().screenRule()) {
                Spacer(Modifier.size(4.dp))
                NoteHead(dt?.toLocalDate(), n.trail, { to -> save(); onOpen(to) }, n.sphere, if (n.removed) null else ({ k -> vm.setSphere(n.sphereOf, k) }), tools = {
                    ActionBar(listOfNotNull(
                        Action(if (n.trail.last().second.startsWith("#")) s.toTheTopic else s.toTheMatter, false, { save(); onOpen(n.ownerId) }) {
                            if (n.trail.last().second.startsWith("#")) TopicGlyph(it, 20.dp) else DealGlyph(it, 22.dp)
                        },
                        // Owner, 16 окт: «В архив»; «Удалить» only once it is there (with «Вернуть»).
                        if (!n.removed) Action(s.toArchive, false, { remove() }) { ArchiveGlyph(it, 22.dp) } else null,
                        if (n.removed) Action(s.bringBack, false, { vm.restoreNotePiece(key); onClose() }) { ArchiveGlyph(it, 22.dp) } else null,
                        if (n.removed) Action(s.delete, false, { vm.purgeNotePiece(key); onClose() }) { TrashGlyph(it, 22.dp) }
                        else Action(s.deleteNow, false, { onClose(); vm.deleteNotePiece(key) }) { TrashGlyph(it, 22.dp) },
                    ), slots = 4)
                })
                // Owner, 16 окт: the head (day, where it lives, marks, tools) stays put; only the words under it scroll.
                Column(Modifier.weight(1f, fill = cardFill).fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
                    CardRule()
                    // The words, written right here, their day and hour on the margin — as in the diary's own note.
                    Margined(margin = { Box(Modifier.padding(top = 9.dp)) { Stamp(n.at) } }) {
                        Column {
                            if (n.text.isNotBlank() || n.files.isEmpty())
                                BasicTextField(text, { text = continueList(text, it) }, readOnly = n.removed,
                                    textStyle = Type.body.copy(color = c.ink, fontSize = 16.sp, lineHeight = 23.sp), cursorBrush = SolidColor(c.link),
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
                            if (n.files.isNotEmpty()) Box(Modifier.padding(top = 4.dp, bottom = 8.dp)) { AttachmentsView(n.files) }
                        }
                    }
                }
            }
            // The same field as in the diary's note: what is written goes on in this note.
            if (!n.removed) Column(Modifier.fillMaxWidth().background(c.bg).padding(bottom = 10.dp).fieldBand().fieldRule().padding(start = 14.dp, end = 4.dp).padding(top = 8.dp, bottom = 8.dp)) {
                SmartComposer(vm, s.cardNoteHint, false, onSend = { t, f ->
                    if (t.isNotBlank()) {
                        val next = (text.text.trimEnd() + "\n" + t.trim()).trim()
                        text = androidx.compose.ui.text.input.TextFieldValue(next, androidx.compose.ui.text.TextRange(next.length))
                        if (kind == "e") vm.editEntryText("task:$ref", id, next) else vm.editComment(ref, id, next)
                    }
                    if (kind == "e") f.forEach { a -> vm.insertAttachment("task:$ref", id, a) }
                }, slash = false, floating = planner)
            }
        }
    }
}

/** A note into the «Архив» of where it lives (owner, 16 окт: nothing is deleted outright). */
fun MainVm.removeNotePiece(key: String) {
    data.value.notePiece(key) ?: return
    val (kind, ref, id) = key.split('|')
    if (kind == "c") archiveComment(ref, id) else removeRecord(ref, id)
}

fun MainVm.restoreNotePiece(key: String) {
    val (kind, ref, id) = key.split('|')
    if (kind == "c") restoreComment(ref, id) else restoreRecord(ref, id)
}

/** Gone for good — only a note already in the archive. */
fun MainVm.purgeNotePiece(key: String) {
    if (data.value.notePiece(key)?.removed != true) return
    val (kind, ref, id) = key.split('|')
    if (kind == "c") purgeComment(ref, id) else purgeRecord(ref, id)
}
