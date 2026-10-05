package ru.anjeib.margin.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddTask
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import ru.anjeib.margin.data.AppData
import ru.anjeib.margin.data.Entry
import ru.anjeib.margin.data.Note
import ru.anjeib.margin.data.dayMonth
import ru.anjeib.margin.data.text
import java.time.LocalDate
import java.time.LocalDateTime

@Composable
fun NotesList(data: AppData, onOpen: (String) -> Unit, contentPadding: PaddingValues) {
    val c = M.c
    val s = M.s
    var q by rememberSaveable { mutableStateOf("") }
    var searching by rememberSaveable { mutableStateOf(false) }
    var filter by rememberSaveable { mutableStateOf<String?>(null) }
    val words = q.lowercase().split(' ').filter { it.isNotBlank() }
    val notes = data.notes.filter { n -> n.title != s.inboxNote }.filter { n ->
        val hay = (n.title + " " + n.text + " " + n.aiSummary.orEmpty() + " " + n.entries.flatMap { it.attachments }.joinToString(" ") { it.name + " " + it.transcript.orEmpty() }).lowercase()
        words.all { it in hay } && (filter == null || n.project == filter)
    }
    val projects = data.notes.mapNotNull { it.project }.distinct().sorted()

    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item("h") {
            Row(Modifier.padding(top = 18.dp, bottom = 10.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(s.notes, style = Type.display.copy(color = c.ink), modifier = Modifier.weight(1f))
                Box(Modifier.size(44.dp).clip(CircleShape).clickable { searching = !searching; if (!searching) q = "" }, contentAlignment = Alignment.Center) {
                    Icon(if (searching) Icons.Rounded.Close else Icons.Outlined.Search, null, tint = c.graphite)
                }
            }
        }
        if (searching) item("search") {
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.chip).padding(horizontal = 16.dp, vertical = 12.dp)) {
                if (q.isEmpty()) Text(s.searchNotes, style = Type.body.copy(color = c.graphite))
                BasicTextField(q, { q = it }, singleLine = true, textStyle = Type.body.copy(color = c.ink), cursorBrush = SolidColor(c.accent), modifier = Modifier.fillMaxWidth())
            }
        }
        if (projects.isNotEmpty()) item("filters") {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill(s.allFilter, filled = filter == null, onClick = { filter = null })
                projects.forEach { p -> Pill(projectLabel(p), filled = filter == p, onClick = { filter = if (filter == p) null else p }) }
            }
        }
        if (notes.isEmpty()) item("empty") { Empty(s.emptyNotes, s.notesHint) }
        val groups = notes.groupBy { it.project }
        (groups.keys.filterNotNull().sorted() + listOf(null)).forEach { p ->
            val l = groups[p] ?: return@forEach
            if (filter == null && groups.size > 1) item("g-$p") { SectionLabel(p ?: s.noProject, Modifier.padding(start = 6.dp, top = 8.dp)) }
            items(l, key = { it.id }) { n -> NoteCard(n, data, onClick = { onOpen(n.id) }, modifier = Modifier.animateItem()) }
        }
    }
}

@Composable
fun NoteCard(n: Note, data: AppData, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = M.c
    val s = M.s
    val date = runCatching { LocalDateTime.parse(n.updatedAt).toLocalDate().dayMonth() }.getOrDefault("")
    val files = n.entries.flatMap { it.attachments }
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.card).clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(n.title.ifBlank { s.untitled }, style = Type.cardTitle.copy(color = c.ink), maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(3.dp))
                Text(listOfNotNull(n.entries.size.takeIf { it > 1 }?.let { s.entriesCount(it) }, date, attachmentsSummary(files)).joinToString(" · "),
                    style = Type.small.copy(color = c.graphite, fontSize = 12.5.sp))
            }
        }
        val preview = n.entries.lastOrNull { it.text.isNotBlank() }?.text.orEmpty().trim().takeIf { it != n.title.trim() }.orEmpty()
        if (preview.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(preview, style = Type.small.copy(color = c.graphite, fontSize = 14.5.sp, lineHeight = 20.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun NoteEditor(note: Note, data: AppData, vm: MainVm, onBack: () -> Unit, onOpenTask: (String) -> Unit) {
    val c = M.c
    val s = M.s
    val working by vm.working.collectAsState()
    var title by remember(note.id) { mutableStateOf(note.title) }
    var menu by remember { mutableStateOf(false) }
    var projectDialog by remember { mutableStateOf(false) }
    var entryMenu by remember { mutableStateOf<Entry?>(null) }
    var editEntry by remember { mutableStateOf<Entry?>(null) }
    LaunchedEffect(title) { delay(400); vm.renameNote(note.id, title) }
    val close = { vm.renameNote(note.id, title); vm.dropEmptyNote(note.id); onBack() }
    BackHandler(onBack = close)
    val scroll = rememberScrollState()
    LaunchedEffect(note.entries.size) { scroll.animateScrollTo(scroll.maxValue) }

    Column(Modifier.fillMaxSize().background(c.bg).statusBarsPadding().imePadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClick = close), contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, null, tint = c.ink)
            }
            Spacer(Modifier.weight(1f))
            Box {
                Box(Modifier.size(44.dp).clip(CircleShape).clickable { menu = true }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.MoreHoriz, null, tint = c.graphite)
                }
                DropdownMenu(menu, { menu = false }, containerColor = c.card) {
                    DropdownMenuItem(text = { Text(s.project, style = Type.body) }, onClick = { menu = false; projectDialog = true })
                    DropdownMenuItem(text = { Text(s.delete, style = Type.body.copy(color = c.accent)) }, onClick = { menu = false; vm.deleteNote(note.id); onBack() })
                }
            }
        }
        Column(Modifier.weight(1f).verticalScroll(scroll).padding(horizontal = 24.dp)) {
            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.Top) {
                Box(Modifier.weight(1f)) {
                    if (title.isEmpty()) Text(s.noteTitleHint, style = Type.title.copy(color = c.faint, fontSize = 32.sp))
                    BasicTextField(title, { title = it }, textStyle = Type.title.copy(color = c.ink, fontSize = 32.sp, lineHeight = 38.sp),
                        cursorBrush = SolidColor(c.accent), modifier = Modifier.fillMaxWidth())
                }
                // A note that is all title is one idea: it gets the same «to a task» button as an entry.
                if (note.entries.isEmpty() && note.title.isNotBlank()) Box(Modifier.padding(top = 8.dp)) {
                    IdeaTaskButton(data.tasks.filter { it.noteId == note.id && it.sourceEntryId == null }, "idea:title:${note.id}" in working,
                        onOpenTask = onOpenTask, onMake = { vm.ideaToTask(note.id, null) })
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.clickable { projectDialog = true }) { if (note.project != null) Tag(note.project) else Text("+ " + s.project.lowercase(), style = Type.small.copy(color = c.graphite)) }
                val upd = runCatching { LocalDateTime.parse(note.updatedAt).toLocalDate().dayMonth() }.getOrNull()
                if (upd != null) Text("   " + s.updated(upd), style = Type.small.copy(color = c.graphite))
            }
            Spacer(Modifier.height(18.dp))
            Box {
                Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    // Each idea says for itself whether it is already a task, or becomes one with a tap.
                    note.entries.forEach { e ->
                        EntryView(e, onLong = { entryMenu = e }, trailing = if (e.text.isBlank()) null else ({
                            IdeaTaskButton(tasksOfEntry(note, e, data), "idea:${e.id}" in working, onOpenTask = onOpenTask, onMake = { vm.ideaToTask(note.id, e.id) })
                        }))
                    }
                }
                DropdownMenu(entryMenu != null, { entryMenu = null }, containerColor = c.card) {
                    entryMenu?.let { e ->
                        DropdownMenuItem(text = { Text(s.edit, style = Type.body) }, onClick = { editEntry = e; entryMenu = null })
                        DropdownMenuItem(text = { Text(s.delete, style = Type.body.copy(color = c.accent)) }, onClick = { vm.deleteNoteEntry(note.id, e.id); entryMenu = null })
                    }
                }
            }
            if (note.entries.isEmpty()) Text(s.noteBodyHint, style = Type.body.copy(color = c.faint, fontSize = 17.sp))
            Spacer(Modifier.height(24.dp))
        }
        SmartComposer(
            vm, s.appendHint, busy = false,
            onSend = { t, f -> vm.appendToNote(note.id, t, f) },
            keepAudio = true,
            modifier = Modifier.padding(horizontal = 14.dp).padding(bottom = 10.dp),
        )
    }
    if (projectDialog) TextDialog(s.project, note.project.orEmpty(), "vuzburo", onDismiss = { projectDialog = false }) { v ->
        vm.setNoteProject(note.id, v); projectDialog = false
    }
    editEntry?.let { e -> TextDialog(s.edit, e.text, "", onDismiss = { editEntry = null }) { v -> vm.editNoteEntry(note.id, e.id, v); editEntry = null } }
}

/** Next to an idea: «make a task» when it is still just an idea, a quiet check that opens the task when it already is one. */
@Composable
fun IdeaTaskButton(made: List<ru.anjeib.margin.data.Task>, busy: Boolean, onOpenTask: (String) -> Unit, onMake: () -> Unit) {
    val c = M.c
    val s = M.s
    Box(Modifier.size(40.dp).clip(CircleShape).clickable(enabled = !busy) { made.firstOrNull()?.let { onOpenTask(it.id) } ?: onMake() },
        contentAlignment = Alignment.Center) {
        when {
            busy -> CircularProgressIndicator(color = c.accent, strokeWidth = 1.5.dp, modifier = Modifier.size(16.dp))
            made.isNotEmpty() -> Icon(Icons.Outlined.TaskAlt, s.inTasks, tint = c.graphite, modifier = Modifier.size(20.dp))
            else -> Icon(Icons.Outlined.AddTask, s.toTaskPill, tint = c.accent, modifier = Modifier.size(20.dp))
        }
    }
}
