package ru.anjeib.margin.ui

import androidx.compose.foundation.clickable
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
                     val ownerId: String, val trail: List<Pair<String, String>>, val topicOwned: Boolean)

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
            NotePiece(key, e.at, e.text, e.attachments, live == null, t.id, listOf(t.id to ((if (t.topic) "# " else "○ ") + t.title)), t.topic)
        }
        "c" -> {
            val q = questionRef(ref)
            if (q != null) {
                val t = tasks.find { it.id == q.first } ?: return null
                val sub = flat(t.subtasks).find { it.id == q.second } ?: return null
                val cm = sub.comments.find { it.id == id } ?: return null
                NotePiece(key, cm.at, cm.text, emptyList(), false, ref, listOf(t.id to "# " + t.title, ref to "○ " + sub.title), false)
            } else {
                val t = tasks.find { it.id == ref } ?: return null
                val cm = t.comments.find { it.id == id } ?: return null
                NotePiece(key, cm.at, cm.text, emptyList(), false, t.id, listOf(t.id to "○ " + t.title), false)
            }
        }
        else -> null
    }
}

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
        // An emptied note isn't deleted by accident: only «Удалить» removes it.
        if (v.isBlank()) return
        if (kind == "e") vm.editEntryText("task:$ref", id, v.trim()) else vm.editComment(ref, id, v.trim())
    }
    LaunchedEffect(text.text) { if (text.text != n.text) { kotlinx.coroutines.delay(800); save() } }
    DisposableEffect(key) { onDispose { save() } }
    fun remove() { vm.removeNotePiece(key); onClose() }
    CardPage(onClose = { save(); onClose() }) {
        Column(Modifier.weight(1f).fillMaxWidth().screenRule().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            val dt = runCatching { java.time.LocalDateTime.parse(n.at.take(16)) }.getOrNull()
            Spacer(Modifier.size(4.dp))
            Margined(margin = { CardMargin(dt?.toLocalDate(), under = dt?.toLocalTime()?.toString()?.take(5)) }) {
                Column {
                    // Where it lives, over the words — a tap goes there.
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        n.trail.forEachIndexed { i, (to, label) ->
                            if (i > 0) Spacer(Modifier.size(10.dp))
                            Text(label, style = Type.italic.copy(color = c.link, fontSize = 15.5.sp), maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false).clip(RoundedCornerShape(6.dp)).clickable { save(); onOpen(to) }.padding(vertical = 6.dp))
                        }
                    }
                    Text(s.cardKindNote + if (n.removed) " · " + s.removedWord else "", style = Type.italic.copy(color = c.graphite, fontSize = 16.sp), modifier = Modifier.padding(top = 2.dp))
                }
            }
            Margined(Modifier.padding(top = 10.dp)) {
                ActionBar(listOfNotNull(
                    Action(if (n.trail.last().second.startsWith("#")) s.toTheTopic else s.toTheMatter, false, { save(); onOpen(n.ownerId) }) {
                        if (n.trail.last().second.startsWith("#")) TopicGlyph(it, 20.dp) else DealGlyph(it, 22.dp)
                    },
                    if (!n.removed) Action(s.delete, false, { remove() }) { TrashGlyph(it, 22.dp) } else null,
                ), slots = 5)
            }
            // The words, written right here, as on every page.
            Margined(Modifier.padding(top = 8.dp)) {
                Column {
                    if (n.text.isNotBlank() || n.files.isEmpty())
                        BasicTextField(text, { text = continueList(text, it) }, readOnly = n.removed,
                            textStyle = Type.body.copy(color = c.ink, fontSize = 17.sp, lineHeight = 25.sp), cursorBrush = SolidColor(c.link),
                            modifier = Modifier.fillMaxWidth().heightIn(min = if (n.files.isEmpty()) 120.dp else 0.dp))
                    if (n.files.isNotEmpty()) Box(Modifier.padding(top = 10.dp)) { AttachmentsView(n.files) }
                }
            }
        }
    }
}

/** Removes a note by its card key: a topic's note goes to its «Архив», a matter's record and note are deleted (with «Отменить» for records). */
fun MainVm.removeNotePiece(key: String) {
    val n = data.value.notePiece(key) ?: return
    val (kind, ref, id) = key.split('|')
    when {
        kind == "c" -> editComment(ref, id, "")
        n.topicOwned -> removeRecord(ref, id)
        else -> removeEntry("task:$ref", id)
    }
}
