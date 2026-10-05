package ru.anjeib.margin.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.anjeib.margin.ai.Scope
import ru.anjeib.margin.data.AppData
import ru.anjeib.margin.data.Entry
import ru.anjeib.margin.data.Subtask
import ru.anjeib.margin.data.Task
import ru.anjeib.margin.data.dayMonth
import ru.anjeib.margin.data.dowShort
import ru.anjeib.margin.data.isOverdue
import ru.anjeib.margin.data.label
import ru.anjeib.margin.data.progress
import ru.anjeib.margin.data.text
import ru.anjeib.margin.data.whenLabel
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TaskSheet(task: Task, data: AppData, vm: MainVm, onClose: () -> Unit, onOpenNote: (String) -> Unit, onOpenPerson: (String) -> Unit, onOpenTask: (String) -> Unit = {}) {
    val c = M.c
    val s = M.s
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var title by remember(task.id) { mutableStateOf(task.title) }
    var dialog by remember { mutableStateOf<String?>(null) }
    var menu by remember { mutableStateOf(false) }
    var subMenu by remember { mutableStateOf<Subtask?>(null) }
    var renameSub by remember { mutableStateOf<Subtask?>(null) }
    var entryMenu by remember { mutableStateOf<Entry?>(null) }
    var editEntry by remember { mutableStateOf<Entry?>(null) }
    var showDone by remember { mutableStateOf(false) }
    var showAllSubs by remember { mutableStateOf(false) }
    var showAllEntries by remember { mutableStateOf(false) }
    val busy by vm.busy.collectAsState()
    val notice by vm.notice.collectAsState()
    val latest by rememberUpdatedState(task)
    val scopeKey = MainVm.key(Scope.InTask(task.id))
    var savedAt by remember { mutableStateOf(0L) }
    /** Every manual edit saves at once; a quiet «Сохранено» confirms it. */
    fun save(t: Task) { vm.saveTask(t); savedAt = System.currentTimeMillis() }
    androidx.compose.runtime.LaunchedEffect(savedAt) { if (savedAt > 0) { kotlinx.coroutines.delay(1600); savedAt = 0 } }

    fun commitTitle() { if (title.isNotBlank() && title.trim() != latest.title) { vm.saveTask(latest.copy(title = title.trim())); savedAt = System.currentTimeMillis() } }
    DisposableEffect(task.id) { onDispose { commitTitle(); vm.clearNotice() } }
    // The AI may rename the task while the sheet is open.
    androidx.compose.runtime.LaunchedEffect(task.title) { if (task.title != title) title = task.title }

    ModalBottomSheet(
        onDismissRequest = { commitTitle(); onClose() },
        sheetState = sheet,
        containerColor = c.bg,
        scrimColor = c.scrim,
        dragHandle = { Box(Modifier.padding(top = 10.dp, bottom = 2.dp).size(36.dp, 4.dp).clip(CircleShape).background(c.hairline)) },
    ) {
        val maxH = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp * 0.92f).dp
        // The sheet itself lifts above the keyboard, so the content gives up exactly the keyboard's height.
        val ime = androidx.compose.foundation.layout.WindowInsets.ime.asPaddingValues().calculateBottomPadding()
        // Always the same height: the fields sit in the same place for every task, the input stays at the bottom.
        Column(Modifier.fillMaxWidth().height((maxH - ime).coerceAtLeast(200.dp)).imePadding().navigationBarsPadding()) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
                // ⋯ menu
                Box(Modifier.fillMaxWidth()) {
                    androidx.compose.animation.AnimatedVisibility(savedAt > 0, Modifier.align(Alignment.CenterStart),
                        enter = androidx.compose.animation.fadeIn(), exit = androidx.compose.animation.fadeOut()) {
                        Text("✓ " + s.savedTick, style = Type.small.copy(color = c.ok, fontSize = 12.5.sp), modifier = Modifier.padding(start = 8.dp))
                    }
                    Box(Modifier.align(Alignment.CenterEnd)) {
                        Box(Modifier.size(40.dp).clip(CircleShape).clickable { menu = true }, contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.MoreHoriz, null, tint = c.graphite)
                        }
                        DropdownMenu(menu, { menu = false }, containerColor = c.card) {
                            if (!task.done) {
                                DropdownMenuItem(text = { Text(s.swipeTomorrow, style = Type.body) }, onClick = { menu = false; vm.postpone(task.id, 1) })
                                DropdownMenuItem(text = { Text(s.plusWeek, style = Type.body) }, onClick = { menu = false; vm.postpone(task.id, 7) })
                            }
                            DropdownMenuItem(text = { Text(s.delete, style = Type.body.copy(color = c.accent)) }, onClick = { menu = false; dialog = "delete" })
                        }
                    }
                }
                Row(verticalAlignment = Alignment.Top) {
                    CheckCircle(task.done, size = 26.dp) { vm.toggleTask(task.id) }
                    BasicTextField(
                        value = title, onValueChange = { title = it },
                        textStyle = Type.title.copy(color = if (task.done) c.graphite else c.ink, fontSize = 28.sp, lineHeight = 34.sp),
                        cursorBrush = SolidColor(c.accent),
                        modifier = Modifier.weight(1f).padding(top = 6.dp, start = 2.dp),
                    )
                }
                // Where the task came from: an idea in a note, or something said in a conversation.
                originLine(task, data)?.let { o ->
                    Text("↳ $o", style = Type.small.copy(color = c.graphite, fontSize = 13.5.sp), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 48.dp, top = 2.dp).clip(RoundedCornerShape(6.dp))
                            .clickable(enabled = task.noteId != null) { task.noteId?.let(onOpenNote) })
                }

                // Standard fields, always the same rows in the same order: what is filled and what is not is seen at a glance.
                Spacer(Modifier.height(16.dp))
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.card)) {
                    val dates = task.whenLabel()?.substringBefore(" · ${task.time}")?.takeIf { task.dueDate != null || task.startDate != null }
                    FieldRow(Icons.Outlined.CalendarToday, s.fieldDue, dates, accent = task.isOverdue()) { dialog = "dates" }
                    FieldRow(Icons.Outlined.Schedule, s.fieldTime, task.time) { dialog = "time" }
                    FieldRow(Icons.Outlined.Repeat, s.fieldRepeat, task.repeat?.label()) { dialog = "repeat" }
                    FieldRow(Icons.Outlined.PersonOutline, s.fieldPeople, task.people.joinToString(", ").ifBlank { null }, onClick = { dialog = "people" }, content = {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            task.people.forEach { p ->
                                Row(Modifier.clip(RoundedCornerShape(50)).clickable { data.person(p)?.let { onOpenPerson(it.id) } ?: run { dialog = "people" } },
                                    verticalAlignment = Alignment.CenterVertically) {
                                    Monogram(p, 22.dp); Spacer(Modifier.width(6.dp))
                                    Text(p, style = Type.body.copy(color = c.ink, fontSize = 15.sp))
                                }
                            }
                        }
                    })
                    task.waitingFor?.let { FieldRow(Icons.Outlined.HourglassEmpty, s.fieldWaiting, it) { dialog = "waiting" } }
                    task.promisedTo?.let { w -> FieldRow(Icons.AutoMirrored.Outlined.ArrowForward, s.fieldPromised, w) { data.person(w)?.let { p -> onOpenPerson(p.id) } } }
                    FieldRow(Icons.Outlined.Tag, s.project, task.project?.let { "#$it" }, accent = true, last = true) { dialog = "project" }
                }

                // Follow-up is late: offer to nudge the person (SMS if we know the number, else share).
                if (task.waitingFor != null && task.isOverdue()) {
                    val ctx = androidx.compose.ui.platform.LocalContext.current
                    val who = task.waitingFor
                    val phone = data.person(who)?.phone
                    Spacer(Modifier.height(14.dp))
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.accentSoft).padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(s.overdueWaiting, style = Type.body.copy(color = c.ink, fontSize = 15.sp), modifier = Modifier.weight(1f))
                        Pill(s.remindPerson(who), filled = true, onClick = {
                            val text = s.nudgeText(task.title)
                            val i = if (phone != null) android.content.Intent(android.content.Intent.ACTION_SENDTO, android.net.Uri.parse("smsto:" + phone.filter { it.isDigit() || it == '+' })).putExtra("sms_body", text)
                            else android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, text), null)
                            runCatching { ctx.startActivity(i) }
                        })
                    }
                }

                // «Связи»: the idea it grew from, what we wait for around it, what was promised around it.
                val links = taskLinks(task, data)
                if (links.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    SectionLabel(s.linksTitle, Modifier.padding(start = 6.dp, bottom = 2.dp))
                    links.forEach { l ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                .clickable(enabled = l.noteId != null || l.taskId != null) { l.noteId?.let(onOpenNote) ?: l.taskId?.let(onOpenTask) }
                                .padding(horizontal = 6.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(when (l.kind) { "idea" -> Icons.Outlined.EditNote; "wait" -> Icons.Outlined.HourglassEmpty; else -> Icons.AutoMirrored.Outlined.ArrowForward },
                                null, tint = c.graphite, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(l.title, style = Type.body.copy(color = c.ink, fontSize = 15.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (l.meta.isNotBlank()) Text(l.meta, style = Type.small.copy(color = c.graphite, fontSize = 13.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            if (l.noteId != null || l.taskId != null) Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = c.faint)
                        }
                        Hairline(Modifier.padding(start = 36.dp))
                    }
                }

                // Subtasks
                if (task.subtasks.isNotEmpty()) {
                    Spacer(Modifier.height(20.dp))
                    val (d, n) = task.subtasks.progress()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(s.subtasks, style = Type.cardTitle.copy(color = c.ink))
                        Text("  $d/$n", style = Type.small.copy(color = c.graphite))
                    }
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { d.toFloat() / n }, color = c.accent, trackColor = c.hairline,
                        modifier = Modifier.fillMaxWidth().height(2.dp), drawStopIndicator = {}, gapSize = 0.dp,
                    )
                    Spacer(Modifier.height(4.dp))
                    val open = task.subtasks.filter { !it.done }
                    val done = task.subtasks.filter { it.done }
                    val visible = if (showAllSubs || open.size <= 7) open else open.take(7)
                    Box {
                        Column { SubtaskTree(visible, 0, onToggle = { vm.toggleSubtask(task.id, it) }, onLong = { subMenu = it }) }
                        DropdownMenu(subMenu != null, { subMenu = null }, containerColor = c.card) {
                            subMenu?.let { sub ->
                                DropdownMenuItem(text = { Text(s.edit, style = Type.body) }, onClick = { renameSub = sub; subMenu = null })
                                DropdownMenuItem(text = { Text(s.delete, style = Type.body.copy(color = c.accent)) }, onClick = { vm.deleteSubtask(task.id, sub.id); subMenu = null })
                            }
                        }
                    }
                    if (open.size > visible.size) QuietLink(s.more(open.size - visible.size)) { showAllSubs = true }
                    if (done.isNotEmpty()) {
                        QuietLink(s.doneCount(done.size) + if (showDone) " ▾" else " ▸") { showDone = !showDone }
                        if (showDone) SubtaskTree(done, 0, onToggle = { vm.toggleSubtask(task.id, it) }, onLong = { subMenu = it })
                    }
                }

                // Records: dated log + attachments
                if (task.entries.isNotEmpty()) {
                    Spacer(Modifier.height(20.dp))
                    Text(s.records, style = Type.cardTitle.copy(color = c.ink))
                    Spacer(Modifier.height(6.dp))
                    val shown = if (showAllEntries) task.entries else task.entries.takeLast(3)
                    if (shown.size < task.entries.size) QuietLink(s.allRecords(task.entries.size)) { showAllEntries = true }
                    Box {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            shown.forEach { e -> EntryView(e, onLong = { entryMenu = e }) }
                        }
                        DropdownMenu(entryMenu != null, { entryMenu = null }, containerColor = c.card) {
                            entryMenu?.let { e ->
                                DropdownMenuItem(text = { Text(s.edit, style = Type.body) }, onClick = { editEntry = e; entryMenu = null })
                                DropdownMenuItem(text = { Text(s.delete, style = Type.body.copy(color = c.accent)) }, onClick = { vm.deleteTaskEntry(task.id, e.id); entryMenu = null })
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            // Scoped composer — everything about this task is said here.
            Column(Modifier.padding(horizontal = 14.dp).padding(bottom = 10.dp)) {
                notice?.takeIf { it.scopeKey == scopeKey }?.let { nt -> NoticeLine(nt, vm) }
                SmartComposer(
                    vm, s.addOrChange, busy == scopeKey,
                    onSend = { t, f -> vm.send(t, scope = Scope.InTask(task.id), files = f) },
                )
            }
        }
    }

    when (dialog) {
        "dates" -> DatesDialog(task, onDismiss = { dialog = null }) { st, du -> save(task.copy(start = st, due = du, remindAt = null)); dialog = null }
        "time" -> TimeDialog(task.time, onDismiss = { dialog = null }) { tm ->
            val due = task.due ?: if (tm != null && task.start == null) LocalDate.now().toString() else task.due
            save(task.copy(time = tm, due = due, remindAt = null)); dialog = null
        }
        "people" -> TextDialog(s.responsible, task.people.joinToString(", "), s.peopleHint, onDismiss = { dialog = null }) { v ->
            save(task.copy(people = v.split(',').map { it.trim() }.filter { it.isNotEmpty() }.distinct())); dialog = null
        }
        "project" -> TextDialog(s.project, task.project.orEmpty(), "vuzburo", onDismiss = { dialog = null }) { v ->
            save(task.copy(project = v.trim().removePrefix("#").lowercase().ifBlank { null })); dialog = null
        }
        "waiting" -> TextDialog(s.waiting, task.waitingFor.orEmpty(), "", onDismiss = { dialog = null }) { v ->
            save(task.copy(waitingFor = v.trim().ifBlank { null })); dialog = null
        }
        "repeat" -> RepeatDialog(task, onDismiss = { dialog = null }) { r ->
            val due = task.due ?: if (r != null) LocalDate.now().toString() else null
            save(task.copy(repeat = r, due = due)); dialog = null
        }
        "delete" -> AlertDialog(
            onDismissRequest = { dialog = null },
            containerColor = c.card,
            title = { Text(s.deleteTaskQ, style = Type.cardTitle.copy(color = c.ink)) },
            confirmButton = { TextButton({ dialog = null; onClose(); vm.deleteTask(task.id) }) { Text(s.delete, color = c.accent) } },
            dismissButton = { TextButton({ dialog = null }) { Text(s.cancel, color = c.graphite) } },
        )
    }
    renameSub?.let { sub ->
        TextDialog(s.edit, sub.title, "", onDismiss = { renameSub = null }) { v -> vm.renameSubtask(task.id, sub.id, v); renameSub = null }
    }
    editEntry?.let { e ->
        TextDialog(s.edit, e.text, "", onDismiss = { editEntry = null }) { v -> vm.editTaskEntry(task.id, e.id, v); editEntry = null }
    }
}

@Composable
private fun RepeatDialog(task: Task, onDismiss: () -> Unit, onPick: (ru.anjeib.margin.data.Repeat?) -> Unit) {
    val c = M.c
    val s = M.s
    val dow = (task.dueDate ?: LocalDate.now()).dayOfWeek.value
    val options = listOf(
        s.noRepeat to null,
        s.everyDay to ru.anjeib.margin.data.Repeat("daily"),
        s.weekdays to ru.anjeib.margin.data.Repeat("weekly", days = listOf(1, 2, 3, 4, 5)),
        s.everyWeek to ru.anjeib.margin.data.Repeat("weekly", days = listOf(dow)),
        s.everyMonth to ru.anjeib.margin.data.Repeat("monthly"),
        s.everyYear to ru.anjeib.margin.data.Repeat("yearly"),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.card,
        title = { Text(s.repeat, style = Type.cardTitle.copy(color = c.ink)) },
        text = {
            Column {
                options.forEach { (label, r) ->
                    val sel = task.repeat == r
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if (sel) c.accentSoft else c.card)
                        .clickable { onPick(r) }.padding(horizontal = 12.dp, vertical = 12.dp)) {
                        Text(label + (r?.let { if (it.freq == "weekly" && it.days.size == 1) " · " + it.label() else "" } ?: ""),
                            style = Type.body.copy(color = if (sel) c.accent else c.ink))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onDismiss) { Text(s.cancel, color = c.graphite) } },
    )
}

@Composable
private fun MetaChip(icon: ImageVector, text: String?, alert: Boolean = false, onClick: () -> Unit) {
    val c = M.c
    if (text == null) return
    Row(Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = if (alert) c.accent else c.graphite, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(5.dp))
        Text(text, style = Type.small.copy(color = if (alert) c.accent else c.ink, fontSize = 14.sp))
    }
}

@Composable
fun QuietLink(text: String, onClick: () -> Unit) {
    Text(text, style = Type.small.copy(color = M.c.graphite),
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 8.dp))
}

/** «5 ОКТ» over the text, attachments under it. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EntryView(e: Entry, onLong: () -> Unit, trailing: (@Composable () -> Unit)? = null) {
    val c = M.c
    val at = runCatching { LocalDateTime.parse(e.at) }.getOrNull()
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
    Column(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).combinedClickable(onClick = {}, onLongClick = onLong).padding(vertical = 2.dp)) {
        Text(at?.let { it.toLocalDate().dayMonth() + " · " + it.toLocalTime().toString().take(5) }?.uppercase() ?: "",
            style = Type.label.copy(color = c.faint, fontSize = 10.5.sp))
        if (e.text.isNotBlank()) {
            Spacer(Modifier.height(3.dp))
            Text(e.text, style = Type.body.copy(color = c.ink, fontSize = 15.5.sp))
        }
        if (e.attachments.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            AttachmentsView(e.attachments)
        }
    }
    if (trailing != null) { Spacer(Modifier.width(4.dp)); Box(Modifier.padding(top = 6.dp)) { trailing() } }
    }
}

/** One row of the task's standard fields: icon, name, value — or a faint «не задано» that invites a tap. */
@Composable
private fun FieldRow(icon: ImageVector, label: String, value: String?, accent: Boolean = false, last: Boolean = false, content: (@Composable () -> Unit)? = null, onClick: () -> Unit) {
    val c = M.c
    val s = M.s
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 46.dp).padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = c.graphite, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, style = Type.body.copy(color = c.graphite, fontSize = 15.sp), modifier = Modifier.width(100.dp), maxLines = 1)
        Box(Modifier.weight(1f)) {
            if (value != null && content != null) content()
            else Text(value ?: s.notSet, style = Type.body.copy(color = when { value == null -> c.faint; accent -> c.accent; else -> c.ink }, fontSize = 15.sp),
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
    if (!last) Hairline(Modifier.padding(start = 44.dp))
}

@Composable
fun NoticeLine(nt: Notice, vm: MainVm) {
    val c = M.c
    val s = M.s
    Row(Modifier.fillMaxWidth().padding(start = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Spark(12.dp); Spacer(Modifier.width(8.dp))
        Text(nt.text, style = Type.small.copy(color = c.graphite), modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
        nt.undoId?.takeIf { vm.canUndo(it) }?.let { id -> QuietLink(s.undo) { vm.undo(id) } }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun DatesDialog(task: Task, onDismiss: () -> Unit, onSave: (String?, String?) -> Unit) {
    val c = M.c
    val s = M.s
    fun LocalDate.ms() = atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
    fun Long.date() = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
    // Most dates are one day: quick choices first, a single-day calendar, a range only on request.
    var range by remember { mutableStateOf(task.startDate != null && task.dueDate != null) }
    val today = LocalDate.now()
    val friday = today.with(java.time.temporal.TemporalAdjusters.nextOrSame(java.time.DayOfWeek.FRIDAY)).let { if (it == today) it.plusWeeks(1) else it }
    val nextMonday = today.with(java.time.temporal.TemporalAdjusters.next(java.time.DayOfWeek.MONDAY))
    val single = androidx.compose.material3.rememberDatePickerState(initialSelectedDateMillis = (task.dueDate ?: task.startDate)?.ms())
    val rng = rememberDateRangePickerState(
        initialSelectedStartDateMillis = (task.startDate ?: task.dueDate)?.ms(),
        initialSelectedEndDateMillis = task.startDate?.let { task.dueDate?.ms() },
    )
    val colors = DatePickerDefaults.colors(
        containerColor = c.card, selectedDayContainerColor = c.accent, todayDateBorderColor = c.accent,
        todayContentColor = c.accent, dayInSelectionRangeContainerColor = c.accentSoft,
        titleContentColor = c.graphite, headlineContentColor = c.ink,
    )
    androidx.compose.material3.DatePickerDialog(
        onDismissRequest = onDismiss,
        colors = colors,
        confirmButton = {
            TextButton({
                if (range) {
                    val a = rng.selectedStartDateMillis?.date(); val b = rng.selectedEndDateMillis?.date()
                    if (a != null && b != null) onSave(a.toString(), b.toString()) else onSave(null, a?.toString())
                } else onSave(null, single.selectedDateMillis?.date()?.toString())
            }) { Text(s.save, color = c.accent) }
        },
        dismissButton = { TextButton(onDismiss) { Text(s.cancel, color = c.graphite) } },
    ) {
        Column {
            FlowRow(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill(s.quickToday, onClick = { onSave(null, today.toString()) })
                Pill(s.quickTomorrow, onClick = { onSave(null, today.plusDays(1).toString()) })
                Pill(friday.dowShort(), onClick = { onSave(null, friday.toString()) })
                Pill(s.quickNextWeek, onClick = { onSave(nextMonday.toString(), nextMonday.plusDays(6).toString()) })
                Pill(s.quickNone, onClick = { onSave(null, null) })
                Pill(if (range) s.singleLink else s.rangeLink, accent = true, onClick = { range = !range })
            }
            if (range) DateRangePicker(state = rng, colors = colors, showModeToggle = false, title = null, headline = null, modifier = Modifier.height(400.dp))
            else androidx.compose.material3.DatePicker(state = single, colors = colors, showModeToggle = false, title = null, headline = null)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeDialog(time: String?, onDismiss: () -> Unit, onSave: (String?) -> Unit) {
    val c = M.c
    val s = M.s
    val t = time?.let { runCatching { LocalTime.parse(it) }.getOrNull() } ?: LocalTime.of(10, 0)
    val st = rememberTimePickerState(t.hour, t.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.card,
        text = {
            TimePicker(st, colors = TimePickerDefaults.colors(
                clockDialColor = c.chip, selectorColor = c.accent, timeSelectorSelectedContainerColor = c.accentSoft,
                timeSelectorUnselectedContainerColor = c.chip, timeSelectorSelectedContentColor = c.ink,
                timeSelectorUnselectedContentColor = c.ink, clockDialSelectedContentColor = c.bg, clockDialUnselectedContentColor = c.ink,
            ))
        },
        confirmButton = { TextButton({ onSave("%02d:%02d".format(st.hour, st.minute)) }) { Text(s.save, color = c.accent) } },
        dismissButton = {
            Row {
                TextButton({ onSave(null) }) { Text(s.clear, color = c.graphite) }
                TextButton(onDismiss) { Text(s.cancel, color = c.graphite) }
            }
        },
    )
}

@Composable
fun TextDialog(title: String, initial: String, hint: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    val c = M.c
    val s = M.s
    var v by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.card,
        title = { Text(title, style = Type.cardTitle.copy(color = c.ink)) },
        text = {
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.chip).padding(14.dp)) {
                if (v.isEmpty()) Text(hint, style = Type.body.copy(color = c.graphite))
                BasicTextField(v, { v = it }, textStyle = Type.body.copy(color = c.ink), cursorBrush = SolidColor(c.accent), modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton({ onSave(v) }) { Text(s.save, color = c.accent) } },
        dismissButton = { TextButton(onDismiss) { Text(s.cancel, color = c.graphite) } },
    )
}
