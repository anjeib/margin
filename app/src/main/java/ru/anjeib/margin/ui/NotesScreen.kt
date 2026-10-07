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
import ru.anjeib.margin.data.shortLabel
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import ru.anjeib.margin.data.AppData
import ru.anjeib.margin.data.Entry
import ru.anjeib.margin.data.Note
import ru.anjeib.margin.data.fullText
import ru.anjeib.margin.data.dayMonth
import ru.anjeib.margin.data.text
import java.time.LocalDate
import java.time.LocalDateTime

@Composable
fun NotesList(data: AppData, vm: MainVm, onOpen: (String) -> Unit, contentPadding: PaddingValues) {
    val c = M.c
    val s = M.s
    var q by rememberSaveable { mutableStateOf("") }
    var searching by rememberSaveable { mutableStateOf(false) }
    var filter by rememberSaveable { mutableStateOf<String?>(null) }
    val words = q.lowercase().split(' ').filter { it.isNotBlank() }
    val notes = data.notes.filter { n -> n.title != s.inboxNote && !n.archived }.filter { n ->
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
                BasicTextField(q, { q = it }, singleLine = true, textStyle = Type.body.copy(color = c.ink), cursorBrush = SolidColor(c.link), modifier = Modifier.fillMaxWidth())
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
            items(l, key = { it.id }) { n ->
                // Swipe left — into the archive, right — delete; both can be undone.
                SwipeActions(left = s.toArchive to { vm.archiveNote(n.id) }, right = s.delete to { vm.removeNote(n.id) }, modifier = Modifier.animateItem()) {
                    NoteCard(n, data, onClick = { onOpen(n.id) })
                }
            }
        }
    }
}

@Composable
fun NoteCard(n: Note, data: AppData, onClick: () -> Unit, modifier: Modifier = Modifier, why: String? = null) {
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
                // How much is inside (one visit = one record), not its last line.
                val records = n.entries.map { it.session ?: it.id }.distinct().size
                Text(listOfNotNull(records.takeIf { it > 0 }?.let { s.entriesCount(it) }, date, attachmentsSummary(files)).joinToString(" · "),
                    style = Type.small.copy(color = c.graphite, fontSize = 12.5.sp))
                why?.let { Text(it, style = Type.small.copy(color = c.faint, fontSize = 12.5.sp), modifier = Modifier.padding(top = 2.dp)) }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun NoteEditor(note: Note, data: AppData, vm: MainVm, onBack: () -> Unit, onOpenTask: (String) -> Unit) {
    val c = M.c
    val s = M.s
    val working by vm.working.collectAsState()
    var title by remember(note.id) { mutableStateOf(note.title) }
    var menu by remember { mutableStateOf(false) }
    var projectDialog by remember { mutableStateOf(false) }
    // The field below fills one thing — a list item, people, a place, the project — like in a task.
    var mode by remember(note.id) { mutableStateOf<String?>(null) }
    var fill by remember(note.id) { mutableStateOf<Fill?>(null) }
    var modeTick by remember { mutableStateOf(0) }
    var remindDialog by remember { mutableStateOf(false) }
    fun fill(what: String) { mode = what; fill = null; modeTick++ }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(title) { delay(400); vm.renameNote(note.id, title) }
    // Everything written in this visit is one record under one date line.
    LaunchedEffect(note.id) { vm.startSession("note:${note.id}"); vm.tidyNote(note.id) }
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
                    DropdownMenuItem(text = { Text(s.listBlock, style = Type.body) }, onClick = { menu = false; vm.addNoteList(note.id); fill("item") })
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
                        cursorBrush = SolidColor(c.link), modifier = Modifier.fillMaxWidth())
                }
                // A note that is all title is one idea: it gets the same «to a task» button as an entry.
                if (note.entries.isEmpty() && note.title.isNotBlank()) Box(Modifier.padding(top = 8.dp)) {
                    IdeaTaskButton(data.tasks.filter { it.noteId == note.id && it.sourceEntryId == null }, "idea:title:${note.id}" in working,
                        onOpenTask = onOpenTask, onMake = { vm.ideaToTask(note.id, null) })
                }
            }
            Spacer(Modifier.height(6.dp))
            // The same properties a task has — what is filled in one quiet line, «+ …» for the rest.
            val remind = note.remindAt?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                remind?.let { r -> Row(Modifier.clip(RoundedCornerShape(8.dp)).clickable { remindDialog = true }, verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Notifications, null, tint = c.graphite, modifier = Modifier.size(15.dp)); Spacer(Modifier.width(5.dp))
                    Text(r.toLocalDate().shortLabel().replaceFirstChar { it.uppercase() } + " " + r.toLocalTime().toString().take(5), style = Type.small.copy(color = c.ink, fontSize = 14.sp))
                } }
                note.people.forEach { p -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Monogram(p, 22.dp); Spacer(Modifier.width(5.dp)); Text(p, style = Type.small.copy(color = c.ink, fontSize = 14.sp))
                } }
                note.place?.let { pl -> Row(Modifier.clip(RoundedCornerShape(8.dp)).combinedClickable(onClick = {
                    runCatching { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("geo:0,0?q=" + android.net.Uri.encode(pl)))) }
                }, onLongClick = { fill("place") }), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Place, null, tint = c.graphite, modifier = Modifier.size(15.dp)); Spacer(Modifier.width(5.dp))
                    Text(pl, style = Type.small.copy(color = c.ink, fontSize = 14.sp))
                } }
                note.project?.let { Box(Modifier.clickable { projectDialog = true }) { Tag(it) } }
                val upd = runCatching { LocalDateTime.parse(note.updatedAt).toLocalDate().dayMonth() }.getOrNull()
                if (upd != null) Text(s.updated(upd), style = Type.small.copy(color = c.graphite))
            }
            val missing = listOfNotNull(
                (s.project to "project").takeIf { note.project == null }, (s.fieldPeople to "people").takeIf { note.people.isEmpty() },
                (s.fieldPlace to "place").takeIf { note.place == null }, (s.remindWord to "remind").takeIf { note.remindAt == null },
            )
            if (missing.isNotEmpty()) FlowRow(Modifier.padding(top = 6.dp)) {
                missing.forEachIndexed { i, (label, d) ->
                    if (i > 0) Text("  ·  ", style = Type.small.copy(color = c.faint))
                    Text("+ " + label.lowercase(), style = Type.small.copy(color = c.graphite),
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { if (d == "remind") remindDialog = true else fill(d) }.padding(2.dp))
                }
            }
            Spacer(Modifier.height(18.dp))
            // The note as blocks: tap one for its «+» (insert right after it), hold for edit / caption / move / delete.
            BlockStream("note:${note.id}", note.entries, vm, onFill = { fill = it; modeTick++ }, trailing = { e ->
                if (e.text.isBlank() || e.isList) null else ({
                    IdeaTaskButton(tasksOfEntry(note, e, data), "idea:${e.id}" in working, onOpenTask = onOpenTask, onMake = { vm.ideaToTask(note.id, e.id) })
                })
            })
            if (note.entries.isEmpty()) Text(s.noteBodyHint, style = Type.body.copy(color = c.faint, fontSize = 17.sp))
            Spacer(Modifier.height(24.dp))
        }
        val modeLabel = fill?.label() ?: when (mode) { "item" -> s.listItem; "people" -> s.fieldPeople; "place" -> s.fieldPlace; "project" -> s.project; else -> null }
        SmartComposer(
            vm, modeLabel?.let { s.fillHint(it) } ?: s.appendHint, busy = false,
            onSend = { t, f ->
                val fl = fill
                // Filling a block: list items keep coming, anything else is one go.
                if (fl != null) { if (t.isNotBlank()) vm.fillBlock("note:${note.id}", fl, t); f.forEach { a -> vm.insertAttachment("note:${note.id}", fl.entryId, a) }; if (fl.kind != "items") fill = null }
                else { vm.writeInNote(note.id, t, f, mode); if (mode != "item") mode = null }
            },
            modifier = Modifier.padding(horizontal = 14.dp).padding(bottom = 10.dp),
            prefill = fill?.prefill, onPrefillUsed = {},
            modeLabel = modeLabel, onModeClose = { mode = null; fill = null }, focusKey = modeTick.takeIf { it > 0 },
        )
    }
    if (projectDialog) TextDialog(s.project, note.project.orEmpty(), "vuzburo", onDismiss = { projectDialog = false }) { v ->
        vm.setNoteProject(note.id, v); projectDialog = false
    }
    if (remindDialog) RemindDialog(note.remindAt, onDismiss = { remindDialog = false }) { v -> vm.setNoteField(note.id, "remind", v); remindDialog = false }
}

/** «Напомнить»: a day, then a time; «Убрать» clears it. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun RemindDialog(initial: String?, onDismiss: () -> Unit, onSave: (String?) -> Unit) {
    val c = M.c
    val s = M.s
    val start = initial?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() } ?: LocalDateTime.now().plusHours(1).withMinute(0)
    var day by remember { mutableStateOf<LocalDate?>(null) }
    if (day == null) {
        val st = androidx.compose.material3.rememberDatePickerState(initialSelectedDateMillis = start.toLocalDate().atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli())
        androidx.compose.material3.DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = { androidx.compose.material3.TextButton({ day = st.selectedDateMillis?.let { java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneOffset.UTC).toLocalDate() } ?: start.toLocalDate() }) { Text(s.next, color = c.link) } },
            dismissButton = { androidx.compose.material3.TextButton({ if (initial != null) onSave(null) else onDismiss() }) { Text(if (initial != null) s.removeWord else s.cancel, color = c.graphite) } },
            colors = androidx.compose.material3.DatePickerDefaults.colors(containerColor = c.card),
        ) { androidx.compose.material3.DatePicker(st, colors = androidx.compose.material3.DatePickerDefaults.colors(containerColor = c.card, selectedDayContainerColor = c.link, todayDateBorderColor = c.link)) }
    } else {
        val tp = androidx.compose.material3.rememberTimePickerState(start.hour, start.minute, is24Hour = true)
        androidx.compose.material3.AlertDialog(
            onDismissRequest = onDismiss, containerColor = c.card,
            text = { androidx.compose.material3.TimePicker(tp, colors = androidx.compose.material3.TimePickerDefaults.colors(clockDialColor = c.chip, selectorColor = c.link,
                timeSelectorSelectedContainerColor = c.linkSoft, timeSelectorUnselectedContainerColor = c.chip)) },
            confirmButton = { androidx.compose.material3.TextButton({ onSave(day!!.atTime(tp.hour, tp.minute).toString().take(16)) }) { Text(s.save, color = c.link) } },
            dismissButton = { androidx.compose.material3.TextButton(onDismiss) { Text(s.cancel, color = c.graphite) } },
        )
    }
}

/** Next to an idea: «make a task» when it is still just an idea, a quiet check that opens the task when it already is one. */
@Composable
fun IdeaTaskButton(made: List<ru.anjeib.margin.data.Task>, busy: Boolean, onOpenTask: (String) -> Unit, onMake: () -> Unit) {
    val c = M.c
    val s = M.s
    // In words, not an icon nobody reads: «В задачу», or «В задачах» once it is one.
    Row(Modifier.clip(RoundedCornerShape(50)).background(if (made.isEmpty()) c.linkSoft else Color.Transparent)
        .clickable(enabled = !busy) { made.firstOrNull()?.let { onOpenTask(it.id) } ?: onMake() }.padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        when {
            busy -> CircularProgressIndicator(color = c.link, strokeWidth = 1.5.dp, modifier = Modifier.size(14.dp))
            made.isNotEmpty() -> Text("✓ " + s.inTasks, style = Type.small.copy(color = c.graphite, fontSize = 13.sp))
            else -> Text(s.toTaskPill, style = Type.small.copy(color = c.link, fontSize = 13.sp))
        }
    }
}
