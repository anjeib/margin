package ru.anjeib.margin.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.anjeib.margin.data.AppData
import ru.anjeib.margin.i18n.L

/** The inbox note (files and words that couldn't be sorted) — never shown among ordinary notes. */
fun AppData.inboxNote() = notes.find { it.title == L.s.inboxNote }

/** How many things wait in «Входящие»: typed offline (not sorted yet) plus what was saved without sorting. */
fun AppData.inboxCount() = queue.size + (inboxNote()?.entries?.size ?: 0)

/**
 * «Входящие»: whatever hasn't been sorted into tasks and notes yet — written without internet,
 * saved without words; later, things from mail.
 */
@Composable
fun InboxScreen(data: AppData, vm: MainVm, onOpenNote: (String) -> Unit, contentPadding: PaddingValues) {
    val c = M.c
    val s = M.s
    val note = data.inboxNote()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item("h") {
            androidx.compose.foundation.layout.Column(Modifier.padding(top = 18.dp, bottom = 10.dp, start = 4.dp)) {
                Text(s.inboxNote, style = Type.display.copy(color = c.ink))
                Spacer(Modifier.height(4.dp))
                Text(s.inboxHint, style = Type.small.copy(color = c.graphite, fontSize = 15.sp))
            }
        }
        if (data.queue.isEmpty() && note?.entries.isNullOrEmpty()) item("empty") { Empty(s.inboxEmpty, s.inboxEmptyHint) }
        if (data.queue.isNotEmpty()) {
            item("q-h") { SectionLabel(s.waitingNet + "  " + data.queue.size, Modifier.padding(start = 6.dp, top = 12.dp)) }
            items(data.queue, key = { "q" + it.id }) { q -> QueuedRow(q.text, q.files.size, onDrop = { vm.dropQueued(q.id) }) }
        }
        if (note != null && note.entries.isNotEmpty()) {
            item("n-h") { SectionLabel(s.savedUnsorted + "  " + note.entries.size, Modifier.padding(start = 6.dp, top = 16.dp)) }
            items(note.entries.reversed(), key = { "e" + it.id }) { e ->
                Row(Modifier.padding(horizontal = 6.dp, vertical = 4.dp)) { EntryView(e, onLong = { onOpenNote(note.id) }) }
            }
        }
    }
}

