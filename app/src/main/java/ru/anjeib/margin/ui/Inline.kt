package ru.anjeib.margin.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.anjeib.margin.data.AppData
import ru.anjeib.margin.data.Attachment
import ru.anjeib.margin.data.Comment
import ru.anjeib.margin.data.Entry
import ru.anjeib.margin.data.Subtask
import ru.anjeib.margin.data.Task
import ru.anjeib.margin.data.questionRef
import ru.anjeib.margin.data.questionRow
import ru.anjeib.margin.data.slotRef
import ru.anjeib.margin.data.whenLabel
import ru.anjeib.margin.data.label
import ru.anjeib.margin.ai.Scope

/*
 * Prototype «inline» (owner, 16 окт: «а что если отказаться от карточек дела и заметки?»): a tap on a matter or a note opens it
 * right in its row — its marks, its notes, its words to edit; one at a time; «назад» folds it. What can be done with it is one row
 * of tools over the field at the bottom, changing with what is open, and the field writes into what is open. A topic keeps its page.
 */

/** What is unfolded on the page: a matter's id (a topic's «q:…», a day of one «…@…»), a note's id, or a note piece's key («e|…», «c|…»). */
class InlineState(val open: String?, val toggle: (String) -> Unit)

val LocalInline = androidx.compose.runtime.staticCompositionLocalOf<InlineState?> { null }
/** The screen's model for the rows that unfold (they are drawn deep in lists that don't carry it). */
val LocalVm = androidx.compose.runtime.staticCompositionLocalOf<MainVm?> { null }

/** The matter a row stands for: a day of several («id@slot») is the matter itself. */
fun inlineRef(id: String) = slotRef(id)?.first ?: id

private fun flat(l: List<Subtask>): List<Subtask> = l.flatMap { listOf(it) + flat(it.children) }

/** What is unfolded, read from the data: a simple matter, a topic's matter, a note of the diary, or a note of a matter/topic. */
sealed class Opened {
    class Matter(val t: Task) : Opened()
    class Deal(val topic: Task, val q: Subtask, val ref: String) : Opened()
    class Note(val t: Task) : Opened()
    class Piece(val p: NotePiece) : Opened()
}

fun AppData.opened(key: String?): Opened? {
    key ?: return null
    if ('|' in key) return notePiece(key)?.let { Opened.Piece(it) }
    val ref = inlineRef(key)
    questionRef(ref)?.let { (tid, qid) ->
        val topic = tasks.find { it.id == tid } ?: return null
        return flat(topic.subtasks).find { it.id == qid }?.let { Opened.Deal(topic, it, ref) }
    }
    val t = tasks.find { it.id == ref } ?: return null
    return if (t.thought) Opened.Note(t) else if (!t.topic) Opened.Matter(t) else null
}

/** A piece of words edited in place: saved a moment after typing stops and when it leaves. */
@Composable
fun InlineText(key: String, text: String, size: Float = 15.5f, onSave: (String) -> Unit) {
    val c = M.c
    var v by remember(key) { mutableStateOf(text) }
    LaunchedEffect(v) { if (v != text && v.isNotBlank()) { kotlinx.coroutines.delay(800); onSave(v.trim()) } }
    DisposableEffect(key) { onDispose { if (v.trim() != text.trim() && v.isNotBlank()) onSave(v.trim()) } }
    BasicTextField(v, { v = it }, textStyle = Type.body.copy(color = c.ink, fontSize = size.sp, lineHeight = (size * 1.45f).sp),
        cursorBrush = SolidColor(c.link), modifier = Modifier.fillMaxWidth())
}

/** A matter unfolded in its row: its marks in one quiet line, then its notes, each editable in place. */
@Composable
fun InlineMatterBody(rowId: String, vm: MainVm,
                     /** The line over it already says them (a topic's own line): no marks repeated. */ marks: Boolean = true) {
    val c = M.c
    val s = M.s
    val data by vm.data.collectAsState()
    val o = data.opened(rowId) ?: return
    val (row, ref, comments) = when (o) {
        is Opened.Matter -> Triple(o.t, o.t.id, o.t.comments)
        is Opened.Deal -> Triple(o.topic.questionRow(o.q), o.ref, o.q.comments)
        else -> return
    }
    Column(Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 10.dp)) {
        // Owner, 16 окт: no day or hour here — the row and its margin already say them.
        val shown = if (!marks) emptyList() else listOfNotNull(
            row.repeat?.let { "↻ " + it.label() }, row.place,
            row.people.takeIf { it.isNotEmpty() }?.joinToString(", "),
            row.waitingFor?.let { s.promiseToYou(it) }, row.promisedTo?.let { "обещали " + it },
        )
        if (shown.isNotEmpty()) Text(shown.joinToString("  ·  "), style = Type.small.copy(color = c.graphite, fontSize = 13.5.sp))
        if (comments.isEmpty()) Text(s.inlineNoNotes, style = Type.small.copy(color = c.faint, fontSize = 13.5.sp), modifier = Modifier.padding(top = 6.dp))
        comments.forEach { cm -> InlineComment(cm, ref, vm) }
    }
}

/** A note of the matter: read here; a hold — change it or put it away (owner, 16 окт: changes only from the long press). */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun InlineComment(cm: Comment, ref: String, vm: MainVm) {
    val c = M.c
    val s = M.s
    var menu by remember(cm.id) { mutableStateOf(false) }
    var edit by remember(cm.id) { mutableStateOf(false) }
    androidx.compose.foundation.layout.Box {
        Column(Modifier.fillMaxWidth().padding(top = 8.dp).combinedClickable(onClick = {}, onLongClick = { menu = true })) {
            Text(stampWords(cm.at), style = Type.small.copy(color = c.graphite, fontSize = 12.5.sp))
            Text(cm.text, style = Type.body.copy(color = c.ink, fontSize = 15.5.sp, lineHeight = 22.sp))
        }
        SoftMenu(menu, { menu = false }) {
            SoftMenuItem(s.edit) { menu = false; edit = true }
            SoftMenuItem(s.toArchive) { menu = false; vm.archiveComment(ref, cm.id) }
            SoftMenuItem(s.deleteNow) { menu = false; vm.deleteNotePiece("c|$ref|${cm.id}") }
        }
    }
    if (edit) TextDialog(s.edit, cm.text, "", onDismiss = { edit = false }) { v -> if (v.isNotBlank()) vm.editComment(ref, cm.id, v.trim()); edit = false }
}

/** «16 окт, 11:00» — when a piece was written, said inside the row. */
fun stampWords(at: String): String {
    val dt = runCatching { java.time.LocalDateTime.parse(at.take(16)) }.getOrNull() ?: return at.take(10)
    return dayWord(dt.toLocalDate()) + ", " + dt.toLocalTime().toString().take(5)
}

/** A note of the diary unfolded: all its words, each piece editable in place, its files. */
@Composable
fun InlineNoteBody(t: Task, vm: MainVm) {
    Column(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 10.dp)) {
        t.entries.filter { it.text.isNotBlank() || it.attachments.isNotEmpty() }.forEach { e ->
            Column(Modifier.padding(top = 4.dp)) {
                if (e.text.isNotBlank()) Text(vm.data.value.mentionsPlain(e.text), style = Type.body.copy(color = M.c.ink, fontSize = 15.5.sp, lineHeight = 22.sp))
                if (e.attachments.isNotEmpty()) androidx.compose.foundation.layout.Box(Modifier.padding(top = 6.dp)) { AttachmentsView(e.attachments) }
            }
        }
    }
}

/** A note of a matter or a topic unfolded: its words editable in place, its files. */
@Composable
fun InlinePieceBody(key: String, vm: MainVm) {
    val data by vm.data.collectAsState()
    val p = data.notePiece(key) ?: return
    val (kind, ref, id) = key.split('|')
    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        if (p.text.isNotBlank()) Text(data.mentionsPlain(p.text), style = Type.body.copy(color = M.c.ink, fontSize = 15.5.sp, lineHeight = 22.sp))
        if (p.files.isNotEmpty()) androidx.compose.foundation.layout.Box(Modifier.padding(top = 6.dp)) { AttachmentsView(p.files) }
    }
}

/**
 * The windows a matter's long-press menu opens (owner, 16 окт: no tools under the page — everything is in the hold): its day or
 * window, its hour, its words. [what] — "dates", "time", "title"; null — none.
 */
@Composable
fun MatterDialog(rowId: String, what: String?, onDismiss: () -> Unit) {
    val vm = LocalVm.current ?: return
    val s = M.s
    val data by vm.data.collectAsState()
    val o = data.opened(rowId) ?: return
    when (what) {
        "dates" -> {
            val row = when (o) { is Opened.Matter -> o.t; is Opened.Deal -> o.topic.questionRow(o.q); else -> null } ?: return
            DatesDialog(row, onDismiss = onDismiss) { st, du ->
                when (o) {
                    is Opened.Matter -> vm.saveTask(o.t.copy(start = st, due = du, remindAt = null))
                    is Opened.Deal -> vm.setQuestionDue(o.topic.id, o.q.id, du ?: st, start = st.takeIf { du != null })
                    else -> {}
                }
                onDismiss()
            }
        }
        "time" -> {
            val cur = when (o) { is Opened.Matter -> o.t.time; is Opened.Deal -> o.q.time; else -> null }
            TimeDialog(cur, onDismiss = onDismiss) { tm -> vm.setTime(if (o is Opened.Deal) o.ref else rowId, tm); onDismiss() }
        }
        "title" -> {
            val cur = when (o) { is Opened.Matter -> o.t.title; is Opened.Deal -> o.q.title; else -> "" }
            TextDialog(s.edit, cur, "", onDismiss = onDismiss) { v ->
                if (v.isNotBlank()) when (o) {
                    is Opened.Matter -> vm.saveTask(o.t.copy(title = v.trim()))
                    is Opened.Deal -> vm.renameSubtask(o.topic.id, o.q.id, v.trim())
                    else -> {}
                }
                onDismiss()
            }
        }
    }
}

/** The field's hint while something is unfolded: it writes into that. */
fun AppData.inlineHint(key: String?): String? = when (opened(key)) {
    is Opened.Matter, is Opened.Deal -> ru.anjeib.margin.i18n.L.s.cardDealHint
    is Opened.Note, is Opened.Piece -> ru.anjeib.margin.i18n.L.s.cardNoteHint
    null -> null
}

/** What the field's words become while something is unfolded: a note of the matter, more of the note. True when it took them. */
fun MainVm.inlineSend(key: String?, text: String, files: List<Attachment>): Boolean {
    val o = data.value.opened(key) ?: return false
    val t = text.trim()
    when (o) {
        is Opened.Matter -> if (files.isEmpty() && !isRequest(t)) addComment(o.t.id, t, ru.anjeib.margin.data.nowStamp())
            else send(t, scope = Scope.InTask(o.t.id), files = files)
        is Opened.Deal -> if (files.isEmpty() && changeDay(o.topic.id, t, o.q.id)) Unit
            else if (files.isEmpty() && !isRequest(t)) addComment(o.ref, t, ru.anjeib.margin.data.nowStamp())
            else send(ru.anjeib.margin.i18n.L.s.aboutDeal(o.q.title, t), scope = Scope.InTask(o.topic.id), files = files)
        is Opened.Note -> if (t.isNotEmpty() || files.isNotEmpty()) insertEntry("task:${o.t.id}", null, Entry(text = t, attachments = files))
        is Opened.Piece -> {
            val (kind, ref, id) = o.p.key.split('|')
            val next = (o.p.text.trimEnd() + "\n" + t).trim()
            if (t.isNotEmpty()) { if (kind == "e") editEntryText("task:$ref", id, next) else editComment(ref, id, next) }
            if (kind == "e") files.forEach { insertAttachment("task:$ref", id, it) }
        }
    }
    return true
}
