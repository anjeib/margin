package ru.anjeib.margin.ui

import ru.anjeib.margin.data.properName
import ru.anjeib.margin.data.shortLabel

import androidx.compose.foundation.layout.offset

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import ru.anjeib.margin.data.dayMonthShort
import androidx.compose.ui.draw.rotate
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Place
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
import ru.anjeib.margin.data.contact
import ru.anjeib.margin.data.Entry
import ru.anjeib.margin.data.Subtask
import ru.anjeib.margin.data.Task
import ru.anjeib.margin.data.sentence
import ru.anjeib.margin.data.dayMonth
import ru.anjeib.margin.data.dowShort
import ru.anjeib.margin.data.isOverdue
import ru.anjeib.margin.data.label
import ru.anjeib.margin.data.next
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
fun TaskSheet(task: Task, data: AppData, vm: MainVm, onClose: () -> Unit, onOpenNote: (String) -> Unit, onOpenPerson: (String) -> Unit, onOpenTask: (String) -> Unit = {},
              startDialog: String? = null, onStartDialogUsed: () -> Unit = {}, startTab: Int = 0, startHl: String? = null) {
    val c = M.c
    val s = M.s
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var title by remember(task.id) { mutableStateOf(task.title) }
    var dialog by remember(task.id) { mutableStateOf<String?>(null) }
    // Opened from «Уточнить срок / время?» — straight to the picker.
    androidx.compose.runtime.LaunchedEffect(task.id, startDialog) { if (startDialog != null) { dialog = startDialog; onStartDialogUsed() } }
    androidx.compose.runtime.LaunchedEffect(task.id) { vm.startSession("task:${task.id}") }
    // «+ подзадача», «+ место»…: the field below fills that one thing — typed or said, no little windows.
    var mode by remember(task.id) { mutableStateOf<String?>(null) }
    var fill by remember(task.id) { mutableStateOf<Fill?>(null) }
    var modeTick by remember { mutableStateOf(0) }
    // The page is written on (owner, 12 окт): these are its words, the bar below sends them.
    val pageDraft = remember(task.id) { mutableStateOf("") }
    val pageFiles = remember(task.id) { androidx.compose.runtime.mutableStateListOf<ru.anjeib.margin.data.Attachment>() }
    val pageSend = remember(task.id) { PageSend() }
    androidx.compose.runtime.DisposableEffect(task.id) { onDispose { pageSend.send() } }
    var writeTick by remember { mutableStateOf(0) }
    fun fill(what: String) { mode = what; fill = null; modeTick++ }
    var menu by remember { mutableStateOf(false) }
    var subMenu by remember { mutableStateOf<Subtask?>(null) }
    var renameSub by remember { mutableStateOf<Subtask?>(null) }
    var showDone by remember { mutableStateOf(false) }
    var showAllSubs by remember { mutableStateOf(false) }
    var showAllEntries by remember { mutableStateOf(false) }
    var showHistory by remember(task.id) { mutableStateOf(false) }
    val busy by vm.busy.collectAsState()
    val notice by vm.notice.collectAsState()
    val scopeKey = MainVm.key(Scope.InTask(task.id))
    var savedAt by remember { mutableStateOf(0L) }
    /** Every manual edit saves at once; a quiet «Сохранено» confirms it. */
    fun save(t: Task) { vm.saveTask(t); savedAt = System.currentTimeMillis() }
    androidx.compose.runtime.LaunchedEffect(savedAt) { if (savedAt > 0) { kotlinx.coroutines.delay(1600); savedAt = 0 } }

    // The typed title belongs to the task it was typed in: when «Связи» switch the sheet to another task,
    // the old title must land on the old task, never on the new one.
    fun commitTitle(id: String = task.id) {
        val cur = vm.data.value.tasks.find { it.id == id } ?: return
        if (title.isNotBlank() && title.trim() != cur.title) { vm.saveTask(cur.copy(title = title.trim())); savedAt = System.currentTimeMillis() }
    }
    DisposableEffect(task.id) { val id = task.id; onDispose { commitTitle(id); vm.clearNotice() } }
    // The AI may rename the task while the sheet is open.
    androidx.compose.runtime.LaunchedEffect(task.title) { if (task.title != title) title = task.title }

    ModalBottomSheet(
        onDismissRequest = { commitTitle(); onClose() },
        sheetState = sheet,
        containerColor = c.bg,
        scrimColor = c.scrim,
        // The handle, and by it a quiet «✓ Сохранено» after a change by hand.
        dragHandle = {
            Box(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 2.dp)) {
                Box(Modifier.align(Alignment.Center).size(36.dp, 4.dp).clip(CircleShape).background(c.hairline))
                androidx.compose.animation.AnimatedVisibility(savedAt > 0, Modifier.align(Alignment.CenterEnd).padding(end = 18.dp),
                    enter = androidx.compose.animation.fadeIn(), exit = androidx.compose.animation.fadeOut()) {
                    Text("✓ " + s.savedTick, style = Type.small.copy(color = c.ok, fontSize = 12.5.sp))
                }
            }
        },
    ) {
        val maxH = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp * 0.92f).dp
        // The sheet itself lifts above the keyboard, so the content gives up exactly the keyboard's height.
        val ime = androidx.compose.foundation.layout.WindowInsets.ime.asPaddingValues().calculateBottomPadding()

        // Opens on half the screen (owner's call), grows with what is in it up to nearly all of it; the input at the bottom.
        val half = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp * 0.6f).dp
        Column(Modifier.fillMaxWidth().heightIn(max = (maxH - ime).coerceAtLeast(200.dp)).imePadding().navigationBarsPadding()) {
            // «Поля»: the rule down the card, the day and hour on the margin, everything else right of it.
            // At least half the screen with the field right under it; a longer card grows, the field stays below.
            Column(Modifier.weight(1f, fill = false).heightIn(min = (half - 76.dp).coerceAtLeast(120.dp)).screenRule().verticalScroll(rememberScrollState())) {
                // The card (owner's call, 10 окт): ⋯ for the matter as a whole; the title with its bagel in the sphere's colour;
                // «когда» as a plaque; three tools (place, people, repeat) and ⋯ for the rest; then the comments.
                val ctxOpen = androidx.compose.ui.platform.LocalContext.current
                val rowActs = LocalRowActions.current
                var more by remember(task.id) { mutableStateOf(false) }
                var postponing by remember(task.id) { mutableStateOf(false) }
                if (postponing) PostponeDialog(onDismiss = { postponing = false }) { d -> vm.postponeTo(task.id, d); postponing = false }
                // Owner, 14 окт: no ⋯ at the top — everything the matter can do is under «Ещё».
                Spacer(Modifier.height(12.dp))
                // v8: «когда» on the margin as on «Сегодня», the mark and the title, one line of values under it, the tools.
                val today = LocalDate.now()
                val due = task.dueDate ?: task.startDate
                val late = task.dueDate?.isBefore(today) == true && !task.done
                val owes = task.waitingFor?.let { s.promiseToYouParts(it) } ?: task.promisedTo?.let { s.promiseFromYouParts(it) }
                val own = task.dueDate?.let { d -> task.time?.let { "${d}T$it" } }
                val remind = task.remindAt?.takeIf { it.take(16) != own }?.let { s.reminderAt + " " + it.drop(11).take(5) }
                val window = task.startDate != null && task.dueDate != null && task.startDate != task.dueDate
                val others = task.people.filter { p -> p != task.waitingFor && p != task.promisedTo }
                val meta = buildList {
                    if (window) task.whenLabel()?.substringBefore(" · ${task.time}")?.let { add(MetaItem(Meta.plain(it), Meta.quiet()) { dialog = "dates" }) }
                    owes?.let { add(MetaItem(Meta.promise(it), Meta.quiet()) { dialog = if (task.waitingFor != null) "waiting" else "promised" }) }
                    others.forEach { p -> add(MetaItem(Meta.plain(p), Meta.link()) { data.contact(p)?.let { pp -> onOpenPerson(pp.id) } ?: fill("people") }) }
                    task.place?.let { add(MetaItem(Meta.plain(it), Meta.link(), onLong = { fill("place") }) {
                        runCatching { ctxOpen.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("geo:0,0?q=" + android.net.Uri.encode(it)))) }
                    }) }
                    task.repeat?.let { add(MetaItem(Meta.plain("↻ " + it.label()), Meta.quiet()) { dialog = "repeat" }) }
                    remind?.let { add(MetaItem(Meta.plain(it), Meta.quiet()) { dialog = "time" }) }
                }
                CardHead(title, { title = it }, task.done, onToggle = { vm.toggleTask(task.id) }, sphere = task.sphere, onSphere = { vm.setSphere(task.id, it) },
                    margin = if (task.slots.isEmpty()) ({ CardWhen(due, task.time, late, onDay = { dialog = "dates" }, onHour = if (due != null) ({ dialog = "time" }) else null, from = task.startDate.takeIf { task.dueDate != null }) }) else null,
                    meta = meta,
                    mark = if (!task.done && (task.waitingFor != null || task.promisedTo != null)) ({
                        Box(Modifier.clip(CircleShape).clickable { vm.toggleTask(task.id) }) { PromiseMark(incoming = task.waitingFor != null, size = 32.dp) }
                    }) else null)
                if (task.slots.isNotEmpty()) Margined(Modifier.padding(top = 4.dp)) { DaysProp(task.slots, onToggle = { vm.toggleSlot(task.id, it) }, onRemove = { vm.removeSlot(task.id, it) }) }
                Margined(Modifier.padding(top = 10.dp)) {
                    Column {
                        ActionBar(listOf(
                            Action(s.actWhen, due != null, { dialog = "dates" }) { CalendarGlyph(it, 22.dp) },
                            Action(s.fieldRepeat, task.repeat != null, { dialog = "repeat" }) { RepeatGlyph(it, 22.dp) },
                            Action(s.fieldPeople, others.isNotEmpty(), { fill("people") }) { PeopleGlyph(it, 22.dp) },
                            Action(s.fieldPlace, task.place != null, { fill("place") }) { Icon(Icons.Outlined.Place, null, tint = it, modifier = Modifier.size(22.dp)) },
                            Action(s.actMore, more, { more = !more }) { Icon(Icons.Outlined.MoreHoriz, null, tint = it, modifier = Modifier.size(22.dp)) },
                        ))
                        androidx.compose.animation.AnimatedVisibility(more) {
                            MoreActions(listOfNotNull(
                                Action(s.actWait, task.waitingFor != null, { dialog = "waiting" }) { ArrowGlyph(it, 20.dp, incoming = true) },
                                Action(s.actPromised, task.promisedTo != null, { dialog = "promised" }) { ArrowGlyph(it, 20.dp) },
                                if (!task.done && task.repeat == null) Action(s.actDay, false, { dialog = "day" }) { CalendarGlyph(it, 20.dp) } else null,
                                if (!task.done) Action(s.actStep, false, { fill("step") }) { PlusGlyph(it, 18.dp) } else null,
                                if (!task.done) Action(s.actPostpone, false, { postponing = true }) { Icon(Icons.Outlined.Schedule, null, tint = it, modifier = Modifier.size(21.dp)) } else null,
                                if (!task.done) rowActs?.let { r -> Action(s.actToTopic, false, { commitTitle(); onClose(); r.toTopic(task.id) }) { TopicGlyph(it, 19.dp) } } else null,
                                Action(s.actToIdea, false, { commitTitle(); vm.taskToNote(task.id)?.let(onOpenNote) }) { ThoughtGlyph(it, 20.dp) },
                                if (!task.done) Action(s.actWontDo, false, { commitTitle(); vm.dropTask(task.id); onClose() }) { Icon(Icons.Outlined.Block, null, tint = it, modifier = Modifier.size(20.dp)) } else null,
                                Action(s.delete, false, { dialog = "delete" }) { Icon(Icons.Outlined.DeleteOutline, null, tint = it, modifier = Modifier.size(21.dp)) },
                            ))
                        }
                    }
                }
                WrittenLane(task.comments.map { LaneItem(it.id, it.at, it.text) }, s.commentHint, Modifier.padding(top = 4.dp), after = null, label = s.commentsTitle,
                    draft = pageDraft, focusKey = writeTick.takeIf { it > 0 }, vm = vm, files = pageFiles, onKeep = { pageSend.send() }, pasteTo = "deal:${task.id}", plus = Plus.Marks,
                    onEdit = { id, t -> vm.editComment(task.id, id, t); savedAt = System.currentTimeMillis() },
                    onAdd = { t, at -> vm.addComment(task.id, t, at); savedAt = System.currentTimeMillis() })
                // Where it came from — below the shared part of every matter's card, quietly.
                Margined(Modifier.padding(top = 6.dp)) {
                    Column {
                        // From a note without a matching idea: one quiet line.
                        if (task.noteId != null) originLine(task, data)?.let { o ->
                            Voice("↳ $o", Modifier.padding(top = 8.dp), link = true, onClick = { task.noteId?.let(onOpenNote) })
                        }
                        // Where it came from: «↳ из сообщения» — the words open on a tap.
                        if (task.source != null && task.noteId == null) {
                            androidx.compose.runtime.LaunchedEffect(task.id) { vm.tidySource(task.id) }
                            Voice("↳ " + s.fromMessageShort, Modifier.padding(top = 8.dp), link = true, onClick = { dialog = "source" })
                        }
                    }
                }

                // Their day has come (or passed): offer to nudge the person (SMS if we know the number, else share).
                if (task.waitingFor != null && !task.done && task.dueDate?.isAfter(LocalDate.now()) == false) {
                    val ctx = androidx.compose.ui.platform.LocalContext.current
                    val who = task.waitingFor
                    val phone = data.person(who)?.phone
                    Margined(Modifier.padding(top = 16.dp)) {
                        Panel {
                            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 10.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(s.overdueWaiting, style = Type.body.copy(color = c.ink, fontSize = 15.sp), modifier = Modifier.weight(1f))
                                // The same message as «напомнить» on «Сегодня»: «Привет, Дима. Напоминаю про макет…», not the to-do's own title.
                                Pill(s.remindPerson(who), filled = true, onClick = { remindAbout(ctx, data, task) })
                            }
                        }
                    }
                }

                // «Связи»: the idea it grew from, what we wait for around it, what was promised around it.
                val links = taskLinks(task, data)
                if (links.isNotEmpty()) {
                    SectionHead(s.linksTitle, count = links.size)
                    links.forEachIndexed { i, l ->
                        PaperRow(seatOf(i, links.size), mark = {
                            when (l.kind) { "idea" -> IdeaMark(24.dp); "wait" -> PromiseMark(incoming = true, size = 28.dp); else -> PromiseMark(incoming = false, size = 28.dp) }
                        }, onClick = if (l.noteId != null || l.taskId != null) ({ l.noteId?.let(onOpenNote) ?: l.taskId?.let(onOpenTask) }) else null,
                            trailing = if (l.noteId != null || l.taskId != null) ({ Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = c.graphite) }) else null) {
                            RowTitle(l.title, maxLines = 2)
                            if (l.meta.isNotBlank()) RowSub(l.meta)
                        }
                    }
                }

                // Old subtasks (a v1 task kept its steps): one panel under its label.
                if (task.subtasks.isNotEmpty()) {
                    val (d, n) = task.subtasks.progress()
                    SectionHead(s.subtasks, action = "$d/$n" to { showDone = !showDone })
                    Margined {
                        Panel {
                            Column(Modifier.padding(vertical = 6.dp)) {
                                val open = task.subtasks.filter { !it.done }
                                val done = task.subtasks.filter { it.done }
                                val visible = if (showAllSubs || open.size <= 7) open else open.take(7)
                                Box {
                                    Column { SubtaskTree(visible, 0, onToggle = { vm.toggleSubtask(task.id, it) }, onLong = { subMenu = it }, number = { s0 -> task.subtasks.indexOfFirst { it.id == s0.id }.takeIf { it >= 0 }?.plus(1) }) }
                                    SoftMenu(subMenu != null, { subMenu = null }) {
                                        subMenu?.let { sub ->
                                            SoftMenuItem(s.edit) { renameSub = sub; subMenu = null }
                                            SoftMenuItem(s.delete, danger = true) { vm.deleteSubtask(task.id, sub.id); subMenu = null }
                                        }
                                    }
                                }
                                if (open.size > visible.size) QuietLink(s.more(open.size - visible.size)) { showAllSubs = true }
                                if (done.isNotEmpty()) {
                                    QuietLink(s.doneCount(done.size) + if (showDone) " ▾" else " ▸") { showDone = !showDone }
                                    if (showDone) SubtaskTree(done, 0, onToggle = { vm.toggleSubtask(task.id, it) }, onLong = { subMenu = it }, number = { s0 -> task.subtasks.indexOfFirst { it.id == s0.id }.takeIf { it >= 0 }?.plus(1) })
                                }
                            }
                        }
                    }
                }

                // Records: dated blocks — a photo of the receipt, a voice note after the call, the contract.
                if (task.entries.isNotEmpty()) {
                    SectionHead(s.records, count = task.entries.size)
                    val shown = if (showAllEntries) task.entries else task.entries.takeLast(3)
                    Margined {
                        Column {
                            if (shown.size < task.entries.size) QuietLink(s.allRecords(task.entries.size)) { showAllEntries = true }
                            BlockStream("task:${task.id}", shown, vm, onFill = { fill = it; mode = null; modeTick++ })
                        }
                    }
                }
                // «История»: what changed and who changed it — folded; the dates on the margin.
                if (task.history.size > 1) {
                    SectionHead(s.historyWord, count = task.history.size, open = showHistory, onFold = { showHistory = !showHistory })
                    androidx.compose.animation.AnimatedVisibility(showHistory, enter = androidx.compose.animation.expandVertically(androidx.compose.animation.core.tween(240)) + androidx.compose.animation.fadeIn(),
                        exit = androidx.compose.animation.shrinkVertically(androidx.compose.animation.core.tween(200)) + androidx.compose.animation.fadeOut()) {
                        Column {
                            val hist = task.history.reversed()
                            hist.forEachIndexed { i, h ->
                                PlainRow(seatOf(i, hist.size), margin = dateOf(h.at)?.let { d -> { RowMargin(dayWord(d)) } }) {
                                    Text(s.change(h.kind, h.value, h.by), style = Type.small.copy(color = c.ink, fontSize = 14.sp))
                                }
                            }
                        }
                    }
                }
                Margined { CreatedLine(task.history.firstOrNull { it.kind == "created" }?.at ?: task.createdAt) }
                Spacer(Modifier.height(8.dp))
            }

            // Scoped composer — everything about this task is said here.
            Column(Modifier.fieldRule().padding(horizontal = 14.dp).padding(bottom = 10.dp)) {
                notice?.takeIf { it.scopeKey == scopeKey }?.let { nt -> Box(Modifier.padding(start = FieldLead)) { NoticeLine(nt, vm) } }
                // A name here that isn't a contact was tapped: the same quiet offer as on the main screen.
                val offer by vm.contactOffer.collectAsState()
                offer?.let { n ->
                    Row(Modifier.fillMaxWidth().padding(start = FieldLead, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(s.notAContact(n), style = Type.small.copy(color = c.graphite), modifier = Modifier.weight(1f))
                        QuietLink(s.addContact) { vm.keepContact(n) }
                    }
                }
                val modeLabel = fill?.label() ?: when (mode) { "step" -> s.stepOne; "place" -> s.fieldPlace; "people" -> s.fieldPeople; else -> null }
                SmartComposer(
                    vm, if (mode == "step") s.stepHint else modeLabel?.let { s.fillHint(it) } ?: s.writeOnPage, busy == scopeKey,
                    onSend = { t, f ->
                        val m = mode
                        val fl = fill
                        when {
                            fl != null -> { if (t.isNotBlank()) vm.fillBlock("task:${task.id}", fl, t); f.forEach { a -> vm.insertAttachment("task:${task.id}", fl.entryId, a) }; if (fl.kind != "items") fill = null }
                            m == "step" -> { vm.addStep(task.id, t); mode = null }
                            m != null -> { vm.sendAs(m, t, Scope.InTask(task.id), f); mode = null }
                            // Words on the page: a request («перенеси на пятницу», a mark) is done; anything else is a comment.
                            f.isEmpty() && !vm.isRequest(t) -> vm.addComment(task.id, t.trim(), ru.anjeib.margin.data.nowStamp())
                            else -> vm.send(t, scope = Scope.InTask(task.id), files = f)
                        }
                    },
                    prefill = fill?.prefill, onPrefillUsed = {},
                    modeLabel = modeLabel, onModeClose = { mode = null; fill = null }, focusKey = modeTick.takeIf { it > 0 },
                    // A mode (a step, a place…) is a one-line answer in the bar; otherwise the words are on the page.
                    pageText = pageDraft.takeIf { modeLabel == null }, onWrite = { writeTick++ }, slash = true, pageSend = pageSend, pageFiles = pageFiles.takeIf { modeLabel == null },
                    marksOnPlus = true,
                )
            }
        }
    }

    when (dialog) {
        "dates" -> DatesDialog(task, onDismiss = { dialog = null }) { st, du -> save(task.copy(start = st, due = du, remindAt = null)); dialog = null }
        "day" -> OneDayDialog(onDismiss = { dialog = null }) { d -> vm.addSlot(task.id, d.toString()); dialog = null }
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
        // Empty words take the side off; a name sets it (and the other side goes — one matter, one direction).
        "waiting" -> TextDialog(s.waiting, task.waitingFor.orEmpty(), s.sideHint, onDismiss = { dialog = null }) { v ->
            val who = v.trim().ifBlank { null }?.properName()
            save(task.copy(waitingFor = who, promisedTo = if (who != null) null else task.promisedTo)); dialog = null
        }
        "promised" -> TextDialog(s.promisedQ, task.promisedTo.orEmpty(), s.sideHint, onDismiss = { dialog = null }) { v ->
            val who = v.trim().ifBlank { null }?.properName()
            save(task.copy(promisedTo = who, waitingFor = if (who != null) null else task.waitingFor)); dialog = null
        }
        "source" -> AlertDialog(
            onDismissRequest = { dialog = null }, containerColor = c.card,
            title = { Text(s.youWrote, style = Type.cardTitle.copy(color = c.ink)) },
            text = { Text(task.source.orEmpty().sentence(), style = Type.body.copy(color = c.ink)) },
            confirmButton = { TextButton({ dialog = null }) { Text(s.closeWord2, color = c.graphite) } },
            // Beta: what was written and what it became, for the developer's log.
            dismissButton = { TextButton({ vm.misparsed(task.source.orEmpty(), "card", listOfNotNull(task.title, task.whenLabel()).joinToString(" · ")); dialog = null }) { Text(s.misparsed, color = c.link) } },
        )
        "repeat" -> RepeatDialog(task, onDismiss = { dialog = null }) { r ->
            val due = task.due ?: if (r != null) LocalDate.now().toString() else null
            save(task.copy(repeat = r, due = due)); dialog = null
        }
        "delete" -> AlertDialog(
            onDismissRequest = { dialog = null },
            containerColor = c.card,
            title = { Text(s.deleteTaskQ, style = Type.cardTitle.copy(color = c.ink)) },
            confirmButton = { TextButton({ dialog = null; onClose(); vm.deleteTask(task.id) }) { Text(s.delete, color = c.accentText) } },
            dismissButton = { TextButton({ dialog = null }) { Text(s.cancel, color = c.graphite) } },
        )
    }
    renameSub?.let { sub ->
        TextDialog(s.edit, sub.title, "", onDismiss = { renameSub = null }) { v -> vm.renameSubtask(task.id, sub.id, v); renameSub = null }
    }
}

@Composable
internal fun RepeatDialog(task: Task, onDismiss: () -> Unit, onPick: (ru.anjeib.margin.data.Repeat?) -> Unit) {
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
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if (sel) c.linkSoft else c.card)
                        .clickable { onPick(r) }.padding(horizontal = 12.dp, vertical = 12.dp)) {
                        Text(r?.takeIf { it.freq == "weekly" && it.days.size == 1 }?.label()?.replaceFirstChar { it.uppercase() } ?: label,
                            style = Type.body.copy(color = if (sel) c.link else c.ink))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onDismiss) { Text(s.cancel, color = c.graphite) } },
    )
}

/** The days of a matter done on several days: each a small chip with its own tick; a long press takes the day away. */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
internal fun DaysProp(slots: List<ru.anjeib.margin.data.Slot>, onToggle: (String) -> Unit, onRemove: (String) -> Unit) {
    val c = M.c
    val today = LocalDate.now()
    Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.width(22.dp).padding(top = 7.dp), contentAlignment = Alignment.CenterStart) { CalendarGlyph(c.graphite, 15.dp) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            slots.forEach { sl ->
                val d = runCatching { LocalDate.parse(sl.date) }.getOrNull()
                val late = !sl.done && d != null && d.isBefore(today)
                val fg = when { sl.done -> c.ok; late -> c.accentText; else -> c.link }
                Row(Modifier.clip(RoundedCornerShape(50)).border(1.dp, if (sl.done) c.ok.copy(alpha = 0.4f) else c.hairline, RoundedCornerShape(50))
                    .combinedClickable(onClick = { onToggle(sl.id) }, onLongClick = { onRemove(sl.id) }).padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    if (sl.done) { TickGlyph(c.ok, 13.dp); Spacer(Modifier.width(5.dp)) }
                    Text(listOfNotNull(d?.let { it.dowShort().lowercase() + " " + it.dayMonthShort() }, sl.time).joinToString(", "),
                        style = Type.small.copy(color = fg, fontSize = 14.sp, textDecoration = if (sl.done) androidx.compose.ui.text.style.TextDecoration.LineThrough else null))
                }
            }
        }
    }
}

/** One day picked for «+ день». */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OneDayDialog(onDismiss: () -> Unit, onPick: (LocalDate) -> Unit) {
    val c = M.c
    val s = M.s
    val st = androidx.compose.material3.rememberDatePickerState()
    val colors = DatePickerDefaults.colors(containerColor = c.card, selectedDayContainerColor = c.link, todayDateBorderColor = c.link, todayContentColor = c.link)
    androidx.compose.material3.DatePickerDialog(onDismissRequest = onDismiss, colors = colors,
        confirmButton = { TextButton({ st.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) } }) { Text(s.save, color = c.link) } },
        dismissButton = { TextButton(onDismiss) { Text(s.cancel, color = c.graphite) } }) {
        androidx.compose.material3.DatePicker(state = st, colors = colors, showModeToggle = false, title = null, headline = null)
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
fun EntryView(e: Entry, onLong: () -> Unit, trailing: (@Composable () -> Unit)? = null, onToggleItem: ((String) -> Unit)? = null) {
    val c = M.c
    val at = runCatching { LocalDateTime.parse(e.at) }.getOrNull()
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
    Column(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).combinedClickable(onClick = {}, onLongClick = onLong).padding(vertical = 2.dp)) {
        Text(at?.let { it.toLocalDate().dayMonth() + " · " + it.toLocalTime().toString().take(5) }?.uppercase() ?: "",
            style = Type.label.copy(color = c.graphite, fontSize = 10.5.sp))
        if (e.text.isNotBlank()) {
            Spacer(Modifier.height(3.dp))
            Text(e.text, style = Type.body.copy(color = c.ink, fontSize = 15.5.sp))
        }
        // «Список»: items with ticks; a tap on the circle ticks one off.
        if (e.isList) {
            Spacer(Modifier.height(4.dp))
            if (e.items.isEmpty()) Text("—", style = Type.body.copy(color = c.faint))
            e.items.forEach { it0 ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CheckCircle(it0.done, size = 18.dp) { onToggleItem?.invoke(it0.id) }
                    Text(it0.title, style = Type.body.copy(fontSize = 15.5.sp, color = if (it0.done) c.graphite else c.ink,
                        textDecoration = if (it0.done) androidx.compose.ui.text.style.TextDecoration.LineThrough else null), modifier = Modifier.padding(vertical = 3.dp))
                }
            }
        }
        if (e.attachments.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            AttachmentsView(e.attachments)
        }
    }
    if (trailing != null) { Spacer(Modifier.width(4.dp)); Box(Modifier.padding(top = 6.dp)) { trailing() } }
    }
}

@Composable
fun NoticeLine(nt: Notice, vm: MainVm) {
    val c = M.c
    val s = M.s
    Row(Modifier.fillMaxWidth().padding(start = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Spark(12.dp); Spacer(Modifier.width(8.dp))
        Text(nt.text, style = Type.small.copy(color = c.graphite), modifier = Modifier.weight(1f), maxLines = 4, overflow = TextOverflow.Ellipsis)
        nt.undoId?.takeIf { vm.canUndo(it) }?.let { id -> QuietLink(s.undo) { vm.undo(id) } }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun DatesDialog(task: Task, onDismiss: () -> Unit, onSave: (String?, String?) -> Unit) {
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
        containerColor = c.card, selectedDayContainerColor = c.link, todayDateBorderColor = c.link,
        todayContentColor = c.link, dayInSelectionRangeContainerColor = c.linkSoft,
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
            }) { Text(s.save, color = c.link) }
        },
        dismissButton = { TextButton(onDismiss) { Text(s.cancel, color = c.graphite) } },
    ) {
        Column {
            FlowRow(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill(s.quickToday, onClick = { onSave(null, today.toString()) })
                Pill(s.quickTomorrow, onClick = { onSave(null, today.plusDays(1).toString()) })
                Pill(friday.dowShort(), onClick = { onSave(null, friday.toString()) })
                Pill(nextMonday.dowShort(), onClick = { onSave(null, nextMonday.toString()) })
                Pill(s.quickNextWeek, onClick = { onSave(nextMonday.toString(), nextMonday.plusDays(6).toString()) })
                // A repeating task: skip this one and go straight to the next time.
                task.repeat?.let { r -> val cur = task.dueDate ?: today; Pill(s.skipOnce, onClick = { onSave(null, r.next(cur).let { n -> if (n.isBefore(today)) r.next(today) else n }.toString()) }) }
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
                clockDialColor = c.chip, selectorColor = c.link, timeSelectorSelectedContainerColor = c.linkSoft,
                timeSelectorUnselectedContainerColor = c.chip, timeSelectorSelectedContentColor = c.ink,
                timeSelectorUnselectedContentColor = c.ink, clockDialSelectedContentColor = c.bg, clockDialUnselectedContentColor = c.ink,
            ))
        },
        confirmButton = { TextButton({ onSave("%02d:%02d".format(st.hour, st.minute)) }) { Text(s.save, color = c.link) } },
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
                BasicTextField(v, { v = it }, textStyle = Type.body.copy(color = c.ink), cursorBrush = SolidColor(c.link), modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton({ onSave(v) }) { Text(s.save, color = c.link) } },
        dismissButton = { TextButton(onDismiss) { Text(s.cancel, color = c.graphite) } },
    )
}
