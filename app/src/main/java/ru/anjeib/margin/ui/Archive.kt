package ru.anjeib.margin.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.anjeib.margin.data.AppData
import ru.anjeib.margin.data.Subtask
import ru.anjeib.margin.i18n.L
import java.time.LocalDate

/**
 * One thing in an archive (owner, 16 окт: nothing is deleted outright — it goes to «Архив», and only there it can be deleted).
 * [at] — when it was put away or written (yyyy-MM-dd…), shown on the margin; [open] — a tap opens it (else the menu).
 */
class ArchItem(val key: String, val title: String, val at: String?, val sub: String? = null, val open: (() -> Unit)? = null,
               val restore: () -> Unit, val purge: () -> Unit)

/** «Архив · N ▸» at the end of a page, folded like «Сделано · N»; open — the archived things, each with «Вернуть / Удалить насовсем». */
fun LazyListScope.archiveFold(key: String, items: List<ArchItem>, open: Boolean, onFold: () -> Unit) {
    if (items.isEmpty()) return
    item(key) { Margined(Modifier.padding(top = 18.dp)) { QuietLink(L.s.archiveCount(items.size) + if (open) " ▾" else " ▸", onFold) } }
    if (open) items.forEach { a -> item(key + ":" + a.key) { ArchiveRow(a) } }
}

/** The same, inside a card's column (a matter's archived notes). */
@Composable
fun ArchiveBlock(items: List<ArchItem>) {
    if (items.isEmpty()) return
    var open by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Margined(Modifier.padding(top = 6.dp)) { QuietLink(L.s.archiveCount(items.size) + if (open) " ▾" else " ▸") { open = !open } }
        if (open) items.forEach { ArchiveRow(it) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ArchiveRow(a: ArchItem) {
    val c = M.c
    val s = M.s
    var menu by remember(a.key) { mutableStateOf(false) }
    val day = a.at?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() }
    Margined(margin = day?.let { d -> { RowMargin(dayWord(d)) } }) {
        Box(Modifier.fillMaxWidth()) {
            Box(Modifier.align(Alignment.TopEnd).padding(top = 8.dp)) {
                SoftMenu(menu, { menu = false }) {
                    a.open?.let { f -> SoftMenuItem(s.openWord) { menu = false; f() } }
                    SoftMenuItem(s.bringBack) { menu = false; a.restore() }
                    SoftMenuItem(s.purgeForGood, danger = true) { menu = false; a.purge() }
                }
            }
            Column(Modifier.fillMaxWidth().combinedClickable(onClick = { a.open?.invoke() ?: run { menu = true } }, onLongClick = { menu = true })
                .padding(top = RowTop, bottom = RowTop, end = 14.dp)) {
                Text(a.title, style = Type.body.copy(fontFamily = rowFamily, color = c.graphite, fontSize = rowSize, lineHeight = rowLine), maxLines = 2, overflow = TextOverflow.Ellipsis)
                a.sub?.let { Text(it, style = Type.small.copy(color = c.graphite, fontSize = 13.sp), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp)) }
            }
        }
    }
}

/** Notes put into the archive of where they live: topics' and matters' records, matters' notes (a topic's matters too). */
fun AppData.archivedNotes(vm: MainVm, onOpenNote: (String) -> Unit, sphereOk: (String?) -> Boolean = { true }): List<ArchItem> {
    fun flat(l: List<Subtask>): List<Subtask> = l.flatMap { listOf(it) + flat(it.children) }
    val out = mutableListOf<Pair<String, ArchItem>>()
    fun add(at: String, key: String, text: String, link: String) {
        out += at to ArchItem(key, text.ifBlank { "…" }, at, link, open = { onOpenNote(key) }, restore = { vm.restoreNotePiece(key) }, purge = { vm.purgeNotePiece(key) })
    }
    tasks.filter { !it.thought && !(it.topic && it.done) && sphereOk(it.sphere) }.forEach { t ->
        val own = (if (t.topic) "# " else "○ ") + t.title
        t.removedEntries.forEach { e -> add(e.at, entryKey(t.id, e.id), e.text.ifBlank { e.attachments.firstOrNull()?.let { a -> a.caption ?: a.name } ?: "" }, own) }
        t.removedComments.forEach { cm -> add(cm.at, commentKey(t.id, cm.id), cm.text, own) }
        if (t.topic) flat(t.subtasks).forEach { q ->
            q.removedComments.forEach { cm -> add(cm.at, commentKey("q:${t.id}:${q.id}", cm.id), cm.text, "○ " + q.title + "  ·  # " + t.title) }
        }
    }
    return out.sortedByDescending { it.first }.map { it.second }
}

/** How much lies in «Архив» (the menu shows it by the word): topics, matters, notes of matters and topics, journal entries, contacts. */
fun AppData.archiveCount(): Int {
    fun flat(l: List<Subtask>): List<Subtask> = l.flatMap { listOf(it) + flat(it.children) }
    val pieces = tasks.filter { !it.thought && !(it.topic && it.done) }.sumOf { t ->
        t.removedEntries.size + t.removedComments.size + (if (t.topic) flat(t.subtasks).sumOf { it.removedComments.size } else 0)
    }
    return tasks.count { it.topic && it.done && !it.thought } + archived.size + pieces +
        tasks.count { it.thought && it.done && it.subtasks.isEmpty() } + notes.count { it.archived } + persons.count { it.archived }
}
