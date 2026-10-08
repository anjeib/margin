package ru.anjeib.margin.ui

import ru.anjeib.margin.data.dayOf

import ru.anjeib.margin.data.undatedTopicMatters

import androidx.compose.ui.text.withLink

import androidx.compose.ui.text.SpanStyle

import androidx.compose.animation.animateContentSize

import kotlinx.coroutines.launch

import androidx.compose.ui.text.withStyle

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.anjeib.margin.data.AppData
import ru.anjeib.margin.data.CalEvent
import ru.anjeib.margin.data.Settings
import ru.anjeib.margin.data.Task
import ru.anjeib.margin.data.dowShort
import ru.anjeib.margin.data.isOverdue
import ru.anjeib.margin.data.longTitle
import ru.anjeib.margin.data.progress
import ru.anjeib.margin.data.rangeLabel
import ru.anjeib.margin.data.shortLabel
import ru.anjeib.margin.data.weekEnd
import ru.anjeib.margin.data.weekStart
import ru.anjeib.margin.data.dowFull
import ru.anjeib.margin.data.dayMonthShort
import ru.anjeib.margin.data.listTasks
import ru.anjeib.margin.data.materials
import ru.anjeib.margin.data.noNextStep
import ru.anjeib.margin.data.openQuestions
import ru.anjeib.margin.data.openWaits
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import ru.anjeib.margin.i18n.L
import java.time.LocalDate
import java.time.LocalTime

/** Which slice of tasks is on screen. Encoded as a string so it survives rotation. */
object Views {
    const val TODAY = "today"
    const val WEEK = "week"
    const val ALL = "all"
    const val DONE = "done"
    fun project(p: String) = "p:$p"
}

// ---------------- selection logic ----------------

private fun Task.span(): Pair<LocalDate, LocalDate>? {
    val s = startDate ?: dueDate ?: return null
    val e = dueDate ?: startDate ?: return null
    return s to e
}

private fun sortKey(t: Task): String = (t.dueDate ?: t.startDate)?.toString().orEmpty().ifEmpty { "9999" } + (t.time ?: "99:99")

/** Due today, overdue, or starting today. */
fun isToday(t: Task, today: LocalDate = LocalDate.now()): Boolean {
    if (t.done) return false
    val s = t.startDate; val d = t.dueDate
    return when {
        d != null -> !d.isAfter(today) || s == today
        s != null -> s == today
        else -> false
    }
}

fun isThisWeek(t: Task, today: LocalDate = LocalDate.now()): Boolean {
    if (t.done) return false
    val (s, e) = t.span() ?: return false
    return !s.isAfter(today.weekEnd()) && (!e.isBefore(today) || t.isOverdue(today))
}

/** A window running now that isn't "for today" — shown as «В работе на неделе». */
fun isRunning(t: Task, today: LocalDate = LocalDate.now()) = isThisWeek(t, today) && !isToday(t, today)

/** Counters for the top tabs: today, week, all open. */
fun tabCounts(d: AppData): Triple<Int, Int, Int> {
    val today = LocalDate.now()
    val l = d.listTasks()
    return Triple(l.count { isToday(it, today) }, l.count { isThisWeek(it, today) }, d.tasks.count { !it.done && !(it.thought && it.subtasks.isEmpty()) })
}

/** Short right-hand label for compact rows: «вчера», «вт 18:00», «до вс», «20 окт». */
private fun Task.rowLabel(today: LocalDate): String? {
    val s = startDate; val d = dueDate
    val day = when {
        d != null && s != null && s != d -> L.s.until(if (!d.isAfter(today.weekEnd())) d.dowShort().lowercase() else d.shortLabel(today).lowercase())
        d != null -> if (d == today && time != null) null else d.shortLabel(today).lowercase()
        s != null -> s.shortLabel(today).lowercase()
        else -> null
    }
    return listOfNotNull(day, time ?: dayPart?.let { L.s.dayPart(it) }).joinToString(" ").ifEmpty { null }
}

private val setSaver = Saver<MutableState<Set<String>>, ArrayList<String>>(
    save = { ArrayList(it.value) }, restore = { mutableStateOf(it.toSet()) },
)

// ---------------- screen ----------------

@Composable
fun TaskList(
    view: String,
    data: AppData,
    settings: Settings,
    working: Set<String>,
    vm: MainVm,
    events: List<CalEvent>,
    briefQuiet: Boolean,
    onOpen: (String) -> Unit,
    onView: (String) -> Unit,
    onOpenNote: (String) -> Unit,
    onOpenNotes: () -> Unit = {},
    onOpenInbox: () -> Unit = {},
    contentPadding: PaddingValues,
) {
    val landed by vm.landed.collectAsState()
    val c = M.c
    val s = M.s
    val today = LocalDate.now()
    var byProject by rememberSaveable { mutableStateOf(false) }
    var label by rememberSaveable { mutableStateOf<String?>(null) }
    var weekDay by rememberSaveable { mutableStateOf<String?>(null) }
    // Swipe left asks «на когда?» instead of quietly moving a day.
    var postponing by remember { mutableStateOf<String?>(null) }
    postponing?.let { id -> PostponeDialog(onDismiss = { postponing = null }) { day -> vm.postponeTo(id, day); postponing = null } }
    // «На неделе», «Без даты», «Выполнено» start folded; what the user opens or folds stays so, across launches.
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val ui = remember { ctx.getSharedPreferences("ui", android.content.Context.MODE_PRIVATE) }
    val collapsed = rememberSaveable(saver = setSaver) {
        mutableStateOf(ui.getStringSet("folded", null) ?: setOf("s-done", "s-pdone", "s-running", "s-undated", "s-done-today", "s-nostep", "a-idle"))
    }
    fun toggle(k: String) {
        collapsed.value = if (k in collapsed.value) collapsed.value - k else collapsed.value + k
        ui.edit().putStringSet("folded", collapsed.value).apply()
    }
    fun isOpen(k: String) = k !in collapsed.value
    // Which days are open on the timeline: today by default; what the user opens stays open while the screen lives.
    val openDays = rememberSaveable(saver = setSaver) { mutableStateOf(setOf(today.toString())) }
    fun isDayOpen(d: LocalDate) = d.toString() in openDays.value
    fun toggleDay(d: LocalDate) { openDays.value = if (d.toString() in openDays.value) openDays.value - d.toString() else openDays.value + d.toString() }
    // The hour, tapped: the clock opens for it.
    var timeFor by remember { mutableStateOf<String?>(null) }
    timeFor?.let { id ->
        val cur = ru.anjeib.margin.data.questionRef(id)?.let { (tid, qid) -> data.tasks.find { it.id == tid }?.subtasks?.find { it.id == qid }?.time } ?: data.tasks.find { it.id == id }?.time
        TimeDialog(cur, onDismiss = { timeFor = null }) { tm -> vm.setTime(id, tm); timeFor = null }
    }

    // v2: simple tasks and, for topics, their dated questions — the next steps.
    val tasks = remember(data) { data.listTasks() }
    // «Поля»: the rule runs behind the whole list; each row puts its date or hour on the margin.
    val pad = PaddingValues(top = contentPadding.calculateTopPadding(), bottom = contentPadding.calculateBottomPadding())
    Box(Modifier.fillMaxSize().marginRule(c.accent.copy(alpha = 0.85f), over = true)) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = pad, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        fun header(title: String, subtitle: String?, day: LocalDate? = null, trailing: (@Composable () -> Unit)? = null) {
            item("header") {
                Margined(Modifier.padding(top = 14.dp, bottom = 10.dp), margin = day?.let { d -> { MarginDay(d.dayOfMonth, d.dayMonthShort().substringAfter(' ')) } }) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Column(Modifier.weight(1f).padding(top = if (day != null) 2.dp else 0.dp)) {
                            // Long titles shrink to one line instead of wrapping.
                            BasicText(title, style = Type.display.copy(color = c.ink), maxLines = 1,
                                autoSize = TextAutoSize.StepBased(minFontSize = 24.sp, maxFontSize = 40.sp, stepSize = 1.sp))
                            if (subtitle != null) {
                                Spacer(Modifier.height(4.dp))
                                Text(subtitle, style = Type.small.copy(color = c.graphite, fontSize = 15.sp))
                            }
                        }
                        trailing?.invoke()
                    }
                }
            }
        }
        fun LazyListScope.plain(key: String, margin: (@Composable () -> Unit)? = null, body: @Composable () -> Unit) = item(key) { Margined(margin = margin) { body() } }

        fun LazyListScope.cards(list: List<Task>, timeColumn: Boolean = false, showProject: Boolean = true, showIdea: Boolean = false) {
            items(list, key = { it.id }) { t ->
                SwipeTask(t, onDone = { vm.completeWithUndo(t.id) }, onLater = { postponing = t.id }, modifier = Modifier.animateItem().landing(t.id in landed, 18)) {
                    TaskCard(
                        t, onToggle = { vm.completeWithUndo(t.id) }, onOpen = { onOpen(t.id) },
                        onToggleSub = { vm.toggleSubtask(t.id, it) },
                        timeColumn = if (timeColumn) t.time ?: "" else null,
                        showProject = showProject, showIdea = showIdea,
                    )
                }
            }
        }

        fun LazyListScope.rows(list: List<Task>, key: String, showProject: Boolean = true, todayBlock: Boolean = false) {
            items(list, key = { key + it.id }) { t ->
                SwipeTask(t, onDone = { vm.completeWithUndo(t.id) }, onLater = { postponing = t.id }, modifier = Modifier.animateItem().landing(t.id in landed, 0), radius = 0) {
                    TaskRow(t, today, showProject, onToggle = { vm.completeWithUndo(t.id) }, onOpen = { onOpen(t.id) }, todayBlock = todayBlock, marginLayout = true)
                }
            }
        }

        /** Small-caps section header with a count; tap folds it. */
        fun LazyListScope.section(label: String, key: String, count: Int? = null, collapsible: Boolean = false, action: Pair<String, () -> Unit>? = null) {
            item(key) {
                val rot by animateFloatAsState(if (isOpen(key)) 0f else -90f, label = "sec")
                Margined {
                Row(
                    Modifier.fillMaxWidth().padding(top = 14.dp).clip(RoundedCornerShape(8.dp))
                        .then(if (collapsible) Modifier.clickable { toggle(key) } else Modifier)
                        .padding(end = 4.dp, top = 6.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SectionLabel(label)
                    if (count != null) Text("  $count", style = Type.label.copy(color = c.faint))
                    Spacer(Modifier.weight(1f))
                    // «Просрочено · Перенести»: the action sits with what it acts on, not over the list.
                    action?.let { (text, act) -> Text(text, style = Type.small.copy(color = c.link, fontSize = 13.5.sp),
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = act).padding(horizontal = 8.dp, vertical = 2.dp)) }
                    if (collapsible) Icon(Icons.Rounded.KeyboardArrowDown, null, tint = c.faint, modifier = Modifier.size(18.dp).rotate(rot))
                }
                }
            }
        }

        when {
            view == Views.TODAY -> {
                // v2: a timeline of days. Today open — «Дела» and «Темы»; the next days with something in them, closed to one line each.
                val plans = (0L..13L).map { dayPlan(data, today.plusDays(it), today, events) }.filter { it.date == today || !it.empty }
                // Undated matters — simple ones and topics' — live at the end of today, oldest last.
                val undatedAll = (tasks.filter { !it.done && it.dueDate == null && it.startDate == null && it.waitingFor == null } + data.undatedTopicMatters())
                    .sortedByDescending { it.createdAt }
                plans.forEach { p ->
                    item("day" + p.date) {
                        DayBlock(p, today, open = isDayOpen(p.date), heading = p.date == today, onToggle = { toggleDay(p.date) },
                            onOpen = onOpen, onDone = { vm.completeWithUndo(it) }, onLater = { postponing = it }, onTime = { timeFor = it }, landed = landed,
                            undated = if (p.date == today) undatedAll else emptyList())
                    }
                }
                // Written without internet: one quiet line here, the items themselves live in «Входящие».
                if (data.queue.isNotEmpty()) plain("queue") {
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onOpenInbox).padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.CloudOff, null, tint = c.graphite, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(s.queuedLine(data.queue.size) + " →", style = Type.small.copy(color = c.graphite, fontSize = 14.sp))
                    }
                }
                // Topics with nothing left to do or wait for: one tap to see what's next or to put them away.
                val settled = data.tasks.filter { it.noNextStep }
                if (settled.isNotEmpty()) {
                    section(s.noNextStepCaps, "s-nostep", settled.size, collapsible = true)
                    if (isOpen("s-nostep")) items(settled, key = { "ns" + it.id }) { t -> TopicRow(t, onOpen = { onOpen(t.id) }, lit = t.id in landed) }
                }
                // Notes never mix into task lists — one quiet line says what was written today.
                val notesToday = data.tasks.filter { t -> t.thought && !t.done && (t.createdAt == today.toString() || t.entries.any { it.at.startsWith(today.toString()) }) }
                if (notesToday.isNotEmpty()) plain("notes-today") {
                    Row(
                        Modifier.fillMaxWidth().padding(top = 14.dp).clip(RoundedCornerShape(14.dp))
                            .clickable { if (notesToday.size == 1) onOpen(notesToday[0].id) else onView(Views.ALL) }
                            .landing(notesToday.any { it.id in landed }, 14).padding(horizontal = 10.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("✎", style = Type.body.copy(color = c.graphite))
                        Spacer(Modifier.width(10.dp))
                        Text(s.thoughtsToday + ": " + notesToday.take(3).joinToString(", ") { it.title.ifBlank { s.untitled } } + if (notesToday.size > 3) "…" else "",
                            style = Type.small.copy(color = c.graphite, fontSize = 14.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            
            }

            view == Views.WEEK -> {
                // The week as seven dates on the margin: past days say what got done, today is open, the rest closed.
                val days = (0L..6L).map { today.weekStart().plusDays(it) }
                header(s.thisWeekSoft, rangeLabel(today.weekStart(), today.weekEnd()))
                days.forEach { d ->
                    val p = dayPlan(data, d, today, events)
                    item("wday" + d) {
                        DayBlock(p, today, open = isDayOpen(d), heading = false, onToggle = { toggleDay(d) },
                            onOpen = onOpen, onDone = { vm.completeWithUndo(it) }, onLater = { postponing = it }, onTime = { timeFor = it }, landed = landed)
                    }
                }
            }

            view == Views.ALL -> {
                // v2: everything at once — topics in progress, simple tasks, thoughts, and topics with no next step; labels filter it.
                fun bare(t: Task) = t.thought && t.subtasks.isEmpty()
                val openTopics = data.tasks.filter { it.topic && !it.done && !bare(it) }
                val simple = data.tasks.filter { !it.topic && !it.done }
                val thoughts = data.tasks.filter { !it.done && bare(it) }.sortedByDescending { t -> t.entries.maxOfOrNull { it.at } ?: t.createdAt }
                // v2: «Личное» / «Работа» instead of free labels.
                val labels = (openTopics + simple + thoughts).mapNotNull { it.sphere }.distinct().sortedBy { if (it == "personal") 0 else 1 }
                val lb = label?.takeIf { it in labels }
                // No «N задач» under it: the tab already counts, and here are topics and ideas too.
                header(s.all, null)
                if (labels.isNotEmpty()) plain("labels") {
                    androidx.compose.foundation.layout.FlowRow(Modifier.padding(bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        (listOf<String?>(null) + labels).forEach { l ->
                            val on = lb == l
                            Text(l?.let { if (it == "work") s.sphereWork else s.spherePersonal } ?: s.all, style = Type.small.copy(color = if (on) c.link else c.graphite, fontSize = 13.5.sp),
                                modifier = Modifier.clip(RoundedCornerShape(50)).background(if (on) c.linkSoft else c.chip).clickable { label = l }.padding(horizontal = 12.dp, vertical = 6.dp))
                        }
                    }
                }
                fun ok(p: String?) = lb == null || p == lb
                val nextOf = { t: Task -> t.subtasks.filter { !it.done }.mapNotNull { it.due }.minOrNull() ?: "9999" }
                val working = openTopics.filter { ok(it.sphere) && !it.noNextStep }.sortedBy(nextOf)
                val idle = openTopics.filter { ok(it.sphere) && it.noNextStep }
                val plainOpen = simple.filter { ok(it.sphere) }
                val thoughtsShown = thoughts.filter { ok(it.sphere) }
                if (working.isEmpty() && plainOpen.isEmpty() && thoughtsShown.isEmpty() && idle.isEmpty()) plain("empty") { Empty(s.emptyAll, s.emptyHint) }
                if (working.isNotEmpty()) {
                    section(s.topicsInWork, "a-topics", working.size)
                    items(working, key = { "at" + it.id }) { t -> TopicRow(t, onOpen = { onOpen(t.id) }, lit = t.id in landed) }
                }
                if (plainOpen.isNotEmpty()) {
                    section(s.simpleTasks, "a-simple", plainOpen.size)
                    rows(plainOpen.sortedWith(compareBy({ !it.isOverdue(today) }, { sortKey(it) })), "as")
                }
                if (thoughtsShown.isNotEmpty()) {
                    section(s.thoughts, "a-thoughts", thoughtsShown.size, collapsible = true)
                    if (isOpen("a-thoughts")) items(thoughtsShown, key = { "an" + it.id }) { t -> TopicRow(t, onOpen = { onOpen(t.id) }, lit = t.id in landed) }
                }
                if (idle.isNotEmpty()) {
                    section(s.noNextStepCaps, "a-idle", idle.size, collapsible = true)
                    if (isOpen("a-idle")) items(idle, key = { "ai" + it.id }) { t -> TopicRow(t, onOpen = { onOpen(t.id) }, lit = t.id in landed) }
                }
                val recent = data.tasks.filter { !it.topic && it.done && it.doneAt?.let { d -> LocalDate.parse(d).isAfter(today.minusDays(8)) } == true }
                    .sortedByDescending { it.doneAt }
                if (recent.isNotEmpty()) {
                    section(s.done, "s-done", recent.size, collapsible = true)
                    if (isOpen("s-done")) {
                        rows(recent, "d")
                        plain("all-done") {
                            Text(s.showAll + " →", style = Type.small.copy(color = c.graphite),
                                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { onView(Views.DONE) }.padding(8.dp))
                        }
                    }
                }
            }

            view == Views.DONE -> {
                val list = data.tasks.filter { it.done && !it.topic }.sortedByDescending { it.doneAt }
                header(s.completed, s.tasksCount(list.size))
                list.groupBy { it.doneAt }.forEach { (day, l) ->
                    section(day?.let { LocalDate.parse(it).shortLabel(today) } ?: "—", "dd-$day", l.size)
                    rows(l, "dd$day")
                }
            }

            view.startsWith("p:") -> {
                val p = view.removePrefix("p:")
                val openTasks = data.tasks.filter { !it.done && it.project == p }.sortedBy { sortKey(it) }
                val done = data.tasks.filter { it.done && it.project == p }
                val chains = ideaChains(p, data)
                val ideas = chains.filter { it.tasks.isEmpty() }
                header(projectLabel(p), s.tasksCount(openTasks.size))
                // One fact once: who we wait for is in the summary and on the task row, the idea a task came from is a quiet «✎».
                if (openTasks.isNotEmpty()) { section(s.tasksCaps, "s-ptasks", openTasks.size); cards(openTasks, showProject = false, showIdea = true) }
                if (ideas.isNotEmpty()) {
                    section(s.ideasOpen, "s-ideas", ideas.size, collapsible = true)
                    if (isOpen("s-ideas")) items(ideas, key = { "ch" + it.entry.id }) { ch ->
                        IdeaRow(ch, "idea:${ch.entry.id}" in working, onOpen = { onOpenNote(ch.note.id) }, onMake = { vm.ideaToTask(ch.note.id, ch.entry.id) })
                    }
                }
                val notes = data.notes.filter { it.project == p }
                if (notes.isNotEmpty()) {
                    section(s.notes, "s-notes", notes.size)
                    items(notes, key = { "n" + it.id }) { n -> NoteCard(n, data, onClick = { onOpenNote(n.id) }) }
                }
                if (done.isNotEmpty()) {
                    section(s.done, "s-pdone", done.size, collapsible = true)
                    if (isOpen("s-pdone")) rows(done, "pd", showProject = false)
                }
            }
        }
    }
    }
}

/** An idea that has not become a task yet: its text and the same «make a task» button as in notes. */
@Composable
private fun IdeaRow(ch: Chain, busy: Boolean, onOpen: () -> Unit, onMake: () -> Unit) {
    val c = M.c
    val s = M.s
    Column {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClick = onOpen).padding(start = 6.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.EditNote, null, tint = c.faint, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(12.dp))
            Text(ch.entry.text.trim(), style = Type.body.copy(color = c.ink, fontSize = 15.sp), maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            IdeaTaskButton(ch.tasks, busy, onOpenTask = {}, onMake = onMake)
        }
        Hairline(Modifier.padding(start = 36.dp, top = 4.dp))
    }
}

/** «Отложить»: tomorrow, in a week, in a month — or a day from the calendar. */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun PostponeDialog(onDismiss: () -> Unit, onPick: (LocalDate) -> Unit) {
    val c = M.c
    val s = M.s
    val today = LocalDate.now()
    var custom by remember { mutableStateOf(false) }
    if (custom) {
        val st = androidx.compose.material3.rememberDatePickerState(initialSelectedDateMillis = today.plusDays(1).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli())
        androidx.compose.material3.DatePickerDialog(onDismissRequest = onDismiss,
            confirmButton = { androidx.compose.material3.TextButton({ st.selectedDateMillis?.let { onPick(java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneOffset.UTC).toLocalDate()) } ?: onDismiss() }) { Text(s.save, color = c.link) } },
            dismissButton = { androidx.compose.material3.TextButton(onDismiss) { Text(s.cancel, color = c.graphite) } },
            colors = androidx.compose.material3.DatePickerDefaults.colors(containerColor = c.card),
        ) { androidx.compose.material3.DatePicker(st, colors = androidx.compose.material3.DatePickerDefaults.colors(containerColor = c.card, selectedDayContainerColor = c.link, todayDateBorderColor = c.link)) }
        return
    }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss, containerColor = c.card,
        title = { Text(s.postponeTitle, style = Type.cardTitle.copy(color = c.ink)) },
        text = {
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill(s.quickTomorrow, onClick = { onPick(today.plusDays(1)) })
                Pill(s.inAWeek, onClick = { onPick(today.plusWeeks(1)) })
                Pill(s.inAMonth, onClick = { onPick(today.plusMonths(1)) })
                Pill(s.pickDay, accent = true, onClick = { custom = true })
            }
        },
        confirmButton = {},
        dismissButton = { androidx.compose.material3.TextButton(onDismiss) { Text(s.cancel, color = c.graphite) } },
    )
}

/** Is [t] on [day]: within its window, or — when late — on today. */
private fun onDay(t: Task, day: LocalDate, today: LocalDate): Boolean {
    if (t.isOverdue(today) && t.dueDate?.isBefore(today) == true) return day == today
    val (s, e) = t.span() ?: return false
    return !day.isBefore(s) && !day.isAfter(e)
}

/** Seven days of this week: weekday, number, a dot when something is planned; today in accent, the picked day filled. */
@Composable
private fun WeekStrip(today: LocalDate, picked: LocalDate?, tasks: List<Task>, onPick: (LocalDate) -> Unit) {
    val c = M.c
    Row(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
        (0L..6L).map { today.weekStart().plusDays(it) }.forEach { d ->
            val on = d == picked
            val busy = tasks.any { onDay(it, d, today) }
            Column(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).clickable { onPick(d) }.padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(d.dowShort().lowercase(), style = Type.small.copy(color = if (d.isBefore(today)) c.faint else c.graphite, fontSize = 12.5.sp))
                Spacer(Modifier.height(4.dp))
                Box(Modifier.size(34.dp).clip(CircleShape).background(if (on) c.link else Color.Transparent)
                    .then(if (d == today && !on) Modifier.border(1.dp, c.link, CircleShape) else Modifier), contentAlignment = Alignment.Center) {
                    Text(d.dayOfMonth.toString(), style = Type.body.copy(color = when { on -> c.bg; d == today -> c.link; d.isBefore(today) -> c.faint; else -> c.ink }, fontSize = 16.sp))
                }
                Spacer(Modifier.height(4.dp))
                Box(Modifier.size(4.dp).clip(CircleShape).background(if (busy) (if (d.isBefore(today)) c.faint else c.link) else Color.Transparent))
            }
        }
    }
}

/** A soft accent glow that fades out — "this just arrived here". */
@Composable
fun Modifier.landing(active: Boolean, radius: Int): Modifier {
    val c = M.c
    val a by androidx.compose.animation.core.animateFloatAsState(if (active) 1f else 0f, androidx.compose.animation.core.tween(if (active) 250 else 1200), label = "land")
    if (a == 0f) return this
    val tint = c.link.copy(alpha = 0.16f * a)
    return this.clip(RoundedCornerShape(radius.dp)).drawWithContent { drawContent(); drawRect(tint) }
}

@Composable
private fun EventRow(e: CalEvent, margin: Boolean = false) {
    val c = M.c
    val allDay = M.s.allDay
    if (margin) {
        // On the margin: the hour; right of the rule: a calendar mark instead of a circle — nothing to tick.
        Margined(margin = { MarginText(if (e.allDay) allDay else e.start.toLocalTime().toString().take(5)) }) {
            Column {
                Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.Top) {
                    Box(Modifier.size(24.dp, 24.dp), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Event, null, tint = c.faint, modifier = Modifier.size(17.dp)) }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(e.title, style = Type.body.copy(fontSize = 15.5.sp, color = c.graphite), maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 1.dp))
                        if (!e.allDay || e.location != null) Text(listOfNotNull(if (e.allDay) null else e.start.toLocalTime().toString().take(5) + "–" + e.end.toLocalTime().toString().take(5), e.location).joinToString("  ·  "),
                            style = Type.small.copy(color = c.graphite, fontSize = 13.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Hairline()
            }
        }
        return
    }
    // A meeting from the phone calendar looks like a task row — a calendar mark instead of the circle: nothing to tick.
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(end = 6.dp, top = 9.dp, bottom = 9.dp), verticalAlignment = Alignment.Top) {
            Box(Modifier.size(40.dp, 26.dp), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Event, null, tint = c.faint, modifier = Modifier.size(17.dp)) }
            Column(Modifier.weight(1f)) {
                Text(e.title, style = Type.body.copy(fontSize = 15.5.sp, color = c.graphite), maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
                Text(androidx.compose.ui.text.buildAnnotatedString {
                    withStyle(androidx.compose.ui.text.SpanStyle(color = c.ink, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)) {
                        append(if (e.allDay) allDay else e.start.toLocalTime().toString().take(5) + "–" + e.end.toLocalTime().toString().take(5))
                    }
                    e.location?.let { append("  ·  $it") }
                }, style = Type.small.copy(color = c.graphite, fontSize = 13.5.sp), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
            }
        }
        Hairline(Modifier.padding(start = 40.dp))
    }
}

@Composable
fun QueuedRow(text: String, files: Int, onDrop: () -> Unit) {
    val c = M.c
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.card).padding(start = 14.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.CloudOff, null, tint = c.graphite, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(10.dp))
        Text(text.ifBlank { "📎 $files" }, style = Type.body.copy(color = c.ink, fontSize = 15.sp), maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Box(Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onDrop), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Close, M.s.delete, tint = c.faint, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun ToggleText(label: String, selected: Boolean, onClick: () -> Unit) {
    val c = M.c
    Text(label, style = Type.small.copy(color = if (selected) c.ink else c.graphite, fontSize = 14.sp),
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onClick).padding(horizontal = 2.dp, vertical = 4.dp))
}

/** One-line task row for overviews: circle · title · tag · progress · date. */
@Composable
fun TaskRow(t: Task, today: LocalDate, showProject: Boolean, onToggle: () -> Unit, onOpen: () -> Unit, todayBlock: Boolean = false, marginLayout: Boolean = false) {
    if (marginLayout) { MarginTaskRow(t, today, showProject, onToggle, onOpen, todayBlock); return }
    val c = M.c
    val overdue = t.isOverdue(today)
    val (d, n) = t.subtasks.progress()
    Column(Modifier.fillMaxWidth().background(c.bg).clickable(onClick = onOpen)) {
        Row(Modifier.fillMaxWidth().padding(end = 6.dp, top = 9.dp, bottom = 9.dp), verticalAlignment = Alignment.Top) {
            // Waiting on someone: the hourglass closes it too («получено»), like the circle does for the user's own tasks.
            if (t.waitingFor != null && !t.done) Box(Modifier.size(40.dp, 26.dp).clip(CircleShape).clickable(onClick = onToggle), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.HourglassEmpty, null, tint = c.graphite, modifier = Modifier.size(18.dp))
            } else Box(Modifier.size(40.dp, 26.dp), contentAlignment = Alignment.Center) { CheckCircle(t.done, size = 20.dp, onToggle = onToggle) }
            Column(Modifier.weight(1f)) {
                // The whole title — what the task is, not its first words.
                Text(
                    // «Договор от Саши» already says who — don't add «— Саша».
                    t.title + (if (t.dropped) " · " + M.s.droppedMark else ""),
                    // «Не делали» is not crossed out — it wasn't done, it was let go.
                    style = Type.body.copy(fontSize = 15.5.sp, color = if (t.done) c.graphite else c.ink,
                        textDecoration = if (t.done && !t.dropped) TextDecoration.LineThrough else null),
                    maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp),
                )
                // The last thing written about it, faint — no need to open the card to remember.
                if (!t.done) t.entries.lastOrNull { it.text.isNotBlank() }?.let { e ->
                    Text(e.text, style = Type.small.copy(color = c.faint, fontSize = 13.5.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (!t.done) {
                    // Under «Сегодня» the day goes without saying: only the hour (or «утром»), and late ones keep their date.
                    val whenText = if (todayBlock && !t.isOverdue(today) && (t.anchor == today || t.anchor == null)) t.time ?: t.dayPart?.let { L.s.dayPart(it) } else t.rowLabel(today)
                    // One quiet line: whose promise, when, who, where — names and places are links.
                    TaskLine(t, whenText, showProject && t.topicTitle == null, today = today)
                    // A topic's question: which matter it moves forward.
                    t.topicTitle?.let { Text("↳ $it", style = Type.small.copy(color = c.link, fontSize = 13.5.sp), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp)) }
                }
            }
        }
        Hairline(Modifier.padding(start = 40.dp))
    }
}

/** What a row shows on the margin: the hour on «Сегодня», a short day elsewhere; late — the day it was due. */
private fun marginWhen(t: Task, today: LocalDate, todayBlock: Boolean): String? {
    val day = t.dueDate ?: t.startDate
    val hour = t.time ?: t.dayPart?.let { L.s.dayPart(it) }
    if ((todayBlock || day == today) && (day == today || day == null) && hour != null) return hour
    if (todayBlock && !t.isOverdue(today) && (day == today || day == null)) return hour
    val d = day ?: return hour
    // Only dates on the margin; «сегодня» / «завтра» is a badge over the row (SoonBadge).
    val dayText = d.dayMonthShort()
    return listOfNotNull(dayText, hour).joinToString("\n")
}

/** A task on the page: its when on the margin, the circle and the words right of the rule. */
@Composable
private fun MarginTaskRow(t: Task, today: LocalDate, showProject: Boolean, onToggle: () -> Unit, onOpen: () -> Unit, todayBlock: Boolean) {
    val c = M.c
    val view = androidx.compose.ui.platform.LocalView.current
    val late = t.isOverdue(today)
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val motion = motionOn()
    // Ticked here: the circle fills and the pen draws through before the row leaves the list.
    val strike = remember(t.id) { androidx.compose.animation.core.Animatable(if (t.done && !t.dropped) 1f else 0f) }
    var ticking by remember(t.id) { mutableStateOf(false) }
    fun tick() {
        if (t.done || !motion) { if (!t.done) view.confirmTick(); onToggle(); return }
        view.confirmTick()
        scope.launch {
            ticking = true
            strike.animateTo(1f, androidx.compose.animation.core.tween(320))
            kotlinx.coroutines.delay(220)
            onToggle()
        }
    }
    val lift = if (!t.done && !todayBlock && isSoon(t.dueDate ?: t.startDate)) SoonLift + 6.dp else 0.dp
    Margined(Modifier.background(c.bg).clickable(onClick = onOpen), margin = { if (!t.done) marginWhen(t, today, todayBlock)?.let { Box(Modifier.padding(top = lift)) { MarginText(it, late = late) } } }) {
        Column {
            if (!t.done && !todayBlock) SoonBadge(t.dueDate ?: t.startDate, Modifier.padding(top = 6.dp))
            Row(Modifier.fillMaxWidth().padding(top = 9.dp, bottom = 9.dp), verticalAlignment = Alignment.Top) {
                // One circle for everything; what kind of thing it is — a small mark in the narrow column.
                KindMark(if (t.done) null else t)
                Spacer(Modifier.width(4.dp))
                Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                    CheckCircle(t.done || ticking, size = 19.dp, onToggle = ::tick)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    StrikeText(t.title + (if (t.dropped) " · " + M.s.droppedMark else ""),
                        Type.body.copy(fontSize = 15.5.sp, color = if (t.done || ticking) c.graphite else c.ink), strike.value, c.faint,
                        maxLines = 3, modifier = Modifier.padding(top = 1.dp))
                    if (!t.done) {
                        t.entries.lastOrNull { it.text.isNotBlank() }?.let { e -> Text(e.text, style = Type.small.copy(color = c.faint, fontSize = 13.5.sp), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        TaskLine(t, null, showProject && t.topicTitle == null, today = today)
                        t.topicTitle?.let { Text("↳ $it", style = Type.small.copy(color = c.link, fontSize = 13.5.sp), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp)) }
                    }
                }
            }
            Hairline(Modifier.padding(start = 36.dp))
        }
    }
}

/** «Обещания» on the margin: who owes what, the day on the margin, closed right here. */
@Composable
private fun MarginPromises(list: List<Task>, today: LocalDate, onOpen: (String) -> Unit, onToggle: (String) -> Unit) {
    val c = M.c
    val s = M.s
    val view = androidx.compose.ui.platform.LocalView.current
    Column(Modifier.padding(bottom = 6.dp)) {
        list.take(5).forEachIndexed { i, t ->
            val first = i == 0; val last = i == list.take(5).lastIndex
            val shape = RoundedCornerShape(topStart = if (first) 16.dp else 0.dp, topEnd = if (first) 16.dp else 0.dp, bottomStart = if (last) 16.dp else 0.dp, bottomEnd = if (last) 16.dp else 0.dp)
            Margined(margin = { marginWhen(t, today, false)?.let { MarginText(it, late = t.isOverdue(today)) } }) {
                Column(Modifier.fillMaxWidth().clip(shape).background(c.linkSoft).clickable { onOpen(t.id) }
                    .padding(start = 14.dp, end = 12.dp, top = if (first) 12.dp else 6.dp, bottom = if (last) 12.dp else 6.dp)) {
                    if (first) Text(s.promisesTitle.uppercase(), style = Type.label.copy(color = c.link), modifier = Modifier.padding(bottom = 6.dp))
                    Row(verticalAlignment = Alignment.Top) {
                        Box(Modifier.size(24.dp).clip(CircleShape).clickable { view.confirmTick(); onToggle(t.id) }, contentAlignment = Alignment.Center) {
                            if (t.waitingFor != null) HourglassGlyph(c.link, 18.dp) else CheckCircle(false, size = 17.dp, onToggle = { view.confirmTick(); onToggle(t.id) })
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(t.title, style = Type.body.copy(color = c.ink, fontSize = 15.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(t.waitingFor?.let { s.promiseToYou(it) } ?: t.promisedTo?.let { s.promiseFromYou(it) } ?: "",
                                style = Type.small.copy(color = c.graphite, fontSize = 13.sp), maxLines = 1)
                            t.topicTitle?.let { Text("↳ $it", style = Type.small.copy(color = c.link, fontSize = 13.sp), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        }
                    }
                }
            }
        }
    }
}

/** A topic in «Все»: a feed mark instead of a circle — it isn't ticked off, it is settled and put away. */
@Composable
fun TopicRow(t: Task, onOpen: () -> Unit, lit: Boolean = false) {
    val c = M.c
    val s = M.s
    val today = LocalDate.now()
    val bare = t.thought && t.subtasks.isEmpty()
    val files = t.materials().size
    val total = t.subtasks.size
    val done = t.subtasks.count { it.done }
    // The next step in one grey line: who we wait for and until when, or the nearest question and its hour.
    val next = t.subtasks.filter { !it.done }.minByOrNull { it.due ?: "9999" }
    val nextLine = when {
        bare -> listOfNotNull(t.entries.size.takeIf { it > 0 }?.let { s.nEntries(it) }, files.takeIf { it > 0 }?.let { s.nMaterials(it) }).joinToString("  ·  ")
        next == null -> s.allDoneShort
        next.from != null -> s.waitPrefix(next.from).trimEnd(':') + (next.due?.let { "  ·  ~" + LocalDate.parse(it).shortLabel(today).lowercase() } ?: "")
        else -> next.title + listOfNotNull(next.due?.let { d -> if (d == today.toString()) null else LocalDate.parse(d).shortLabel(today).lowercase() }, next.time).joinToString(" ").let { if (it.isBlank()) "" else "  ·  $it" }
    } + (if (!bare && t.entries.isNotEmpty()) "  ·  " + s.nEntries(t.entries.size) else "")
    Margined(Modifier.background(c.bg).landing(lit, 0).clickable(onClick = onOpen), margin = {
        Box(Modifier.padding(top = 3.dp)) { if (bare) ThoughtGlyph(c.graphite, 19.dp) else TopicGlyph(c.graphite, 19.dp) }
    }) {
        Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
            TitleWithSphere(t.title, t.sphere, Type.body.copy(fontSize = 15.5.sp, color = c.ink))
            if (!bare && total > 0) Row(Modifier.padding(top = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                TopicProgress(done, total, Modifier.weight(1f))
                Spacer(Modifier.width(12.dp))
                Text(s.progressOf(done, total), style = Type.small.copy(color = c.graphite, fontSize = 12.5.sp, fontFeatureSettings = "tnum"))
            }
            Text(nextLine, style = Type.small.copy(color = c.graphite, fontSize = 13.sp), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 5.dp, bottom = 10.dp))
            Hairline()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeTask(t: Task, onDone: () -> Unit, onLater: () -> Unit, modifier: Modifier, radius: Int = 18, content: @Composable () -> Unit) {
    val c = M.c
    val s = M.s
    val state = rememberSwipeToDismissBoxState(confirmValueChange = { v ->
        when (v) {
            SwipeToDismissBoxValue.StartToEnd -> onDone()
            SwipeToDismissBoxValue.EndToStart -> onLater()
            else -> {}
        }
        false // always spring back; the list itself reflects the change
    })
    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        enableDismissFromEndToStart = !t.done,
        backgroundContent = {
            val dir = state.dismissDirection
            val toEnd = dir == SwipeToDismissBoxValue.StartToEnd
            Box(
                Modifier.fillMaxSize().clip(RoundedCornerShape(radius.dp))
                    .background(if (dir == SwipeToDismissBoxValue.Settled) c.bg else if (toEnd) c.ink else c.accent)
                    .padding(horizontal = 22.dp),
                contentAlignment = if (toEnd) Alignment.CenterStart else Alignment.CenterEnd,
            ) {
                if (dir != SwipeToDismissBoxValue.Settled)
                    // Show where the task will land, not an abstract «tomorrow».
                    Text(if (toEnd) (if (t.done) s.undo else s.markDone)
                        else s.postponeTitle + " →",
                        style = Type.tab.copy(color = c.bg))
            }
        },
    ) { content() }
}

/**
 * «Обещания» on the main screen: what others promised you and what you promised them, due within three days or late.
 * Folds to one line while the user types.
 */
@Composable
private fun PromisesCard(list: List<Task>, today: LocalDate, quiet: Boolean, onOpen: (String) -> Unit, onToggle: (String) -> Unit) {
    val c = M.c
    val s = M.s
    var all by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(18.dp)).background(c.linkSoft)
        .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = if (quiet) 12.dp else 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spark(); Spacer(Modifier.width(8.dp))
            Text(s.promisesTitle, style = Type.small.copy(color = c.link, fontSize = 14.sp))
            Text("  " + list.size, style = Type.small.copy(color = c.graphite, fontSize = 13.sp))
        }
        if (quiet) return@Column
        Spacer(Modifier.height(4.dp))
        val shown = if (all) list else list.take(5)
        shown.forEach { t ->
            val late = t.isOverdue(today)
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onOpen(t.id) }.padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
              // Closed right here: the hourglass — «получено», the circle — «сделано».
              Box(Modifier.size(32.dp, 24.dp).clip(CircleShape).clickable { onToggle(t.id) }, contentAlignment = Alignment.CenterStart) {
                  if (t.waitingFor != null) Icon(Icons.Outlined.HourglassEmpty, null, tint = c.graphite, modifier = Modifier.size(18.dp))
                  else CheckCircle(false, size = 18.dp, onToggle = { onToggle(t.id) })
              }
              Column(Modifier.weight(1f)) {
                Text(t.title, style = Type.body.copy(color = c.ink, fontSize = 15.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                TaskLine(t, t.rowLabel(today), showProject = false, today = today)
              }
            }
        }
        if (list.size > 5 && !all) Text(s.more(list.size - 5), style = Type.small.copy(color = c.link, fontSize = 13.5.sp),
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { all = true }.padding(vertical = 6.dp))
    }
}

/**
 * Morning brief: full in the morning, then a single line. It folds as soon as the user starts typing
 * or the assistant answers ([quiet]); × hides it until tomorrow.
 */
@Composable
private fun BriefCard(data: AppData, settings: Settings, loading: Boolean, quiet: Boolean, vm: MainVm, onFocus: () -> Unit) {
    val c = M.c
    val s = M.s
    val today = LocalDate.now().toString()
    val brief = data.brief?.takeIf { it.date == today && it.lang == L.lang.name }
    var userOpen by rememberSaveable { mutableStateOf(false) }
    val autoFold = settings.briefCollapsed == today || LocalTime.now().hour >= 12
    val folded = brief != null && (quiet || (autoFold && !userOpen))
    LaunchedEffect(quiet) { if (quiet) { userOpen = false; vm.collapseBrief() } }

    run {
        val openNow = data.tasks.any { isToday(it) || isRunning(it) }
        val late = (brief?.at ?: LocalTime.now().toString().take(5)) >= "18:00"
        if (!openNow && !late && data.tasks.none { it.done && it.doneAt == today }) return
    }
    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(18.dp)).background(c.linkSoft)) {
        // Nothing left for today → say so without asking the AI.
        val openToday = data.tasks.any { isToday(it) || isRunning(it) }
        val evening = (brief?.at ?: LocalTime.now().toString().take(5)) >= "18:00"
        val live = openToday || evening
        // Rebuilt by itself when the day changes; only after the daily limit does it say so.
        if (folded || (brief != null && !live)) {
            Row(
                Modifier.fillMaxWidth().clickable { userOpen = true }.padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spark(); Spacer(Modifier.width(10.dp))
                val head = brief?.headline ?: brief?.text?.substringBefore('.')
                Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
                    Text(
                        if (!live) (if (data.tasks.any { it.done && it.doneAt == today }) s.allDoneToday else s.nothingToday)
                        else s.mainThing + ": " + (head ?: ""),
                        style = Type.small.copy(color = c.ink, fontSize = 14.5.sp), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    if (live) Text(
                        brief?.at?.let { s.briefAt(it) }.orEmpty(),
                        style = Type.small.copy(color = c.graphite, fontSize = 11.5.sp),
                    )
                }
                if (live) Box(Modifier.size(40.dp).clip(CircleShape).clickable(enabled = !loading) { vm.makeBrief(force = true) }, contentAlignment = Alignment.Center) {
                    if (loading) CircularProgressIndicator(color = c.link, strokeWidth = 1.5.dp, modifier = Modifier.size(16.dp))
                    else Icon(Icons.Outlined.Refresh, s.refresh, tint = c.graphite, modifier = Modifier.size(18.dp))
                }
            }
            return@Column
        }
        Column(Modifier.padding(start = 18.dp, end = 6.dp, top = 8.dp, bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spark(); Spacer(Modifier.width(8.dp))
                Text((if (evening) s.eveningBrief else s.morningBrief) + (brief?.at?.let { " · " + s.briefAt(it) } ?: ""), style = Type.small.copy(color = c.link, fontSize = 14.sp), modifier = Modifier.weight(1f))
                Box(Modifier.size(36.dp).clip(CircleShape).clickable(enabled = !loading) { vm.makeBrief(force = true) }, contentAlignment = Alignment.Center) {
                    if (loading) CircularProgressIndicator(color = c.link, strokeWidth = 1.5.dp, modifier = Modifier.size(16.dp))
                    else Icon(Icons.Outlined.Refresh, s.refresh, tint = c.graphite, modifier = Modifier.size(18.dp))
                }
                Box(Modifier.size(36.dp).clip(CircleShape).clickable { vm.hideBrief() }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Close, s.dismiss, tint = c.graphite, modifier = Modifier.size(18.dp))
                }
            }
            Column(Modifier.padding(end = 12.dp)) {
                if (brief == null) Text(if (loading) s.thinking else s.makeBrief, style = Type.body.copy(color = c.graphite),
                    modifier = Modifier.clickable(enabled = !loading) { vm.makeBrief(force = true) })
                else {
                    Text(brief.text, style = Type.body.copy(color = c.ink))
                    brief.focus?.let {
                        Spacer(Modifier.height(6.dp))
                        Text("${s.focusWindow}: $it", style = Type.small.copy(color = c.graphite))
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill(s.startFocus, filled = true, onClick = onFocus)
                        Pill(s.collapse, onClick = { userOpen = false; vm.collapseBrief() })
                    }
                }
            }
        }
    }
}

// ---------------- the day timeline: dates on the margin, one block per day ----------------

/** A topic's step that falls on a day. */
data class TopicStep(val topic: Task, val q: ru.anjeib.margin.data.Subtask)

/** What a day holds: single things to do, topic steps, meetings — and, for a past day, what got done. */
data class DayPlan(val date: LocalDate, val deals: List<Task>, val steps: List<TopicStep>, val events: List<CalEvent>, val doneCount: Int) {
    val empty get() = deals.isEmpty() && steps.isEmpty() && events.isEmpty()
}

/** The day a thing belongs to on the timeline: late ones gather on today. */
private fun dayOf(due: LocalDate?, start: LocalDate?, today: LocalDate): LocalDate? {
    val d = due ?: start ?: return null
    return if (d.isBefore(today)) today else d
}

fun dayPlan(data: AppData, date: LocalDate, today: LocalDate, events: List<CalEvent>): DayPlan {
    // v2: a topic shows on a day only through its matters for that day — ordinary rows, grouped under one link to the topic.
    val deals = data.listTasks().filter { !it.done && dayOf(it.dueDate, it.startDate, today) == date }
        .sortedWith(compareBy({ !it.isOverdue(today) }, { it.time ?: "99" }, { it.title }))
    val steps = emptyList<TopicStep>()
    val evs = events.filter { !it.allDay && it.start.toLocalDate() == date || it.allDay && it.start.toLocalDate() == date }
    val done = if (date.isBefore(today)) data.tasks.count { it.doneAt == date.toString() && !it.topic } +
        data.tasks.sumOf { t -> t.subtasks.count { it.doneAt == date.toString() } } else 0
    return DayPlan(date, deals, steps, evs, done)
}

/** The date on the margin: the number in serif, the month and the weekday small under it; today in petrol. */
@Composable
private fun DayMargin(date: LocalDate, today: LocalDate, big: Boolean) {
    val c = M.c
    val past = date.isBefore(today)
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = if (big) 2.dp else 8.dp)) {
        Text(date.dayOfMonth.toString(), style = Type.display.copy(color = when { date == today && !big -> c.link; past -> c.faint; else -> c.ink },
            fontSize = if (big) 40.sp else 26.sp, lineHeight = if (big) 42.sp else 28.sp))
        Text(date.dayMonthShort().substringAfter(' '), style = Type.small.copy(color = if (past) c.faint else c.graphite, fontSize = 11.5.sp, fontWeight = FontWeight.Medium))
        if (!big) Text(date.dowShort().lowercase(), style = Type.small.copy(color = c.faint, fontSize = 11.sp))
    }
}

/** The little column right of the rule: what kind of thing it is — waiting, promised, repeating, a meeting, a topic. */
@Composable
private fun KindMark(t: Task?) {
    val c = M.c
    Box(Modifier.size(20.dp, 24.dp), contentAlignment = Alignment.Center) {
        when {
            t == null -> {}
            t.waitingFor != null -> HourglassGlyph(c.graphite, 14.dp)
            t.promisedTo != null -> ArrowGlyph(c.graphite, 14.dp)
            t.repeat != null -> RepeatGlyph(c.graphite, 14.dp)
            t.time != null -> ClockGlyph(c.graphite, 14.dp)
        }
    }
}

/** The hour as a petrol link that opens the clock; late in terracotta. */
private fun androidx.compose.ui.text.AnnotatedString.Builder.timeLink(time: String, late: Boolean, link: Color, accent: Color, onTime: () -> Unit) {
    withLink(androidx.compose.ui.text.LinkAnnotation.Clickable("time", androidx.compose.ui.text.TextLinkStyles(SpanStyle(color = if (late) accent else link, fontWeight = FontWeight.Medium))) { onTime() }) { append(time) }
    append("   ")
}

/** One thing to do on an open day: kind mark, the circle, the hour as a link, the words, a quiet line under. */
@Composable
private fun DealRow(t: Task, today: LocalDate, onToggle: () -> Unit, onOpen: () -> Unit, onTime: () -> Unit, age: String? = null, faded: Boolean = false) {
    val c = M.c
    val s = M.s
    val view = androidx.compose.ui.platform.LocalView.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val motion = motionOn()
    val strike = remember(t.id) { androidx.compose.animation.core.Animatable(0f) }
    var ticking by remember(t.id) { mutableStateOf(false) }
    fun tick() {
        view.confirmTick()
        if (!motion) { onToggle(); return }
        scope.launch { ticking = true; strike.animateTo(1f, androidx.compose.animation.core.tween(320)); kotlinx.coroutines.delay(220); onToggle() }
    }
    val late = t.isOverdue(today)
    Row(Modifier.fillMaxWidth().background(c.bg).clickable(onClick = onOpen).padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
        KindMark(t)
        Box(Modifier.size(26.dp, 24.dp), contentAlignment = Alignment.CenterStart) { CheckCircle(ticking, size = 18.dp, onToggle = ::tick) }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            val title = androidx.compose.ui.text.buildAnnotatedString {
                t.time?.let { timeLink(it, late && t.dueDate == today, c.link, c.accent, onTime) }
                append(t.title)
            }
            Text(title, style = Type.body.copy(fontSize = 15.5.sp, color = if (ticking || faded) c.graphite else c.ink), maxLines = 3, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 1.dp).drawStrike(strike.value, c.faint))
            if (late && t.dueDate?.isBefore(today) == true) Text(s.lateSince(t.dueDate!!.dayMonthShort()), style = Type.small.copy(color = c.accent, fontSize = 12.5.sp))
            t.dayOf()?.let { (i, n) -> Text(s.dayOfDays(i, n), style = Type.small.copy(color = c.graphite, fontSize = 12.5.sp)) }
            TaskLine(t, null, true, today = today)
        }
        age?.let { Text(it, style = Type.small.copy(color = c.faint, fontSize = 12.5.sp), modifier = Modifier.padding(start = 8.dp, top = 3.dp)) }
    }
}

/**
 * Deal rows with the matters of one topic kept together under a single «↳ Тема» link — the topic is named once,
 * its matters follow as ordinary rows.
 */
@Composable
private fun GroupedDeals(list: List<Task>, today: LocalDate, landed: Set<String>, onOpen: (String) -> Unit, onDone: (String) -> Unit, onLater: (String) -> Unit, onTime: (String) -> Unit,
                         age: ((Task) -> String?)? = null, faded: (Task) -> Boolean = { false }) {
    val c = M.c
    val seen = mutableSetOf<String>()
    list.forEach { t ->
        val tid = t.topicId
        if (tid != null && tid in seen) return@forEach
        val group = if (tid != null) list.filter { it.topicId == tid } else listOf(t)
        if (tid != null) {
            seen += tid
            Row(Modifier.padding(top = 8.dp, bottom = 0.dp).clip(RoundedCornerShape(6.dp)).clickable { onOpen(tid) }.padding(vertical = 2.dp, horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically) {
                TopicGlyph(c.link, 13.dp)
                Spacer(Modifier.width(6.dp))
                Text(t.topicTitle ?: "", style = Type.small.copy(color = c.link, fontSize = 13.5.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        // A topic's matters sit a step in from its link, so a plain matter after the group reads as its own.
        Column(Modifier.padding(start = if (tid != null) 16.dp else 0.dp, bottom = if (tid != null) 6.dp else 0.dp)) {
            group.forEach { g ->
                androidx.compose.runtime.key(g.id) {
                    SwipeTask(g, onDone = { onDone(g.id) }, onLater = { onLater(g.id) }, modifier = Modifier.landing(g.id in landed || (tid != null && tid in landed), 0), radius = 0) {
                        DealRow(g, today, onToggle = { onDone(g.id) }, onOpen = { onOpen(g.id) }, onTime = { onTime(g.id) }, age = age?.invoke(g), faded = faded(g))
                    }
                }
            }
        }
    }
}

/** A line through the whole box as [strike] goes 0→1 (for rows whose text mixes links). */
private fun Modifier.drawStrike(strike: Float, color: Color): Modifier = if (strike <= 0f) this else this.drawWithContent {
    drawContent()
    val y = 11.dp.toPx()
    drawLine(color, androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Offset(size.width * strike, y), strokeWidth = 1.3.dp.toPx())
}

/** A topic on a day: its title and progress, then its steps for that day — each with the circle and its hour. */
@Composable
private fun TopicDayRow(topic: Task, steps: List<ru.anjeib.margin.data.Subtask>, today: LocalDate, onOpen: (String) -> Unit, onSettle: (String) -> Unit, onTime: (String) -> Unit) {
    val c = M.c
    val view = androidx.compose.ui.platform.LocalView.current
    val total = topic.subtasks.size
    val done = topic.subtasks.count { it.done }
    Column(Modifier.fillMaxWidth().background(c.bg).clickable { onOpen(topic.id) }.padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Box(Modifier.size(20.dp, 24.dp), contentAlignment = Alignment.Center) { TopicGlyph(c.graphite, 15.dp) }
            Spacer(Modifier.width(6.dp))
            Column(Modifier.weight(1f)) {
                TitleWithSphere(topic.title, topic.sphere, Type.body.copy(fontSize = 15.5.sp, color = c.ink, fontWeight = FontWeight.Medium))
                // How much has been written in it — a topic lives by its feed.
                val n = topic.entries.size
                val files = topic.materials().size
                if (n > 0 || files > 0) Text(listOfNotNull(n.takeIf { it > 0 }?.let { M.s.nEntries(it) }, files.takeIf { it > 0 }?.let { M.s.nMaterials(it) }).joinToString("  ·  "),
                    style = Type.small.copy(color = c.graphite, fontSize = 12.5.sp), modifier = Modifier.padding(top = 1.dp))
            }
            if (total > 0) {
                Spacer(Modifier.width(10.dp))
                Column(horizontalAlignment = Alignment.End, modifier = Modifier.width(64.dp)) {
                    Text(M.s.progressOf(done, total), style = Type.small.copy(color = c.graphite, fontSize = 11.5.sp, fontFeatureSettings = "tnum"))
                    TopicProgress(done, total, Modifier.padding(top = 3.dp))
                }
            }
        }
        steps.forEach { q ->
            val qid = "q:${topic.id}:${q.id}"
            val late = q.due?.let { LocalDate.parse(it).isBefore(today) } == true
            Row(Modifier.fillMaxWidth().clickable { onOpen(qid) }.padding(top = 6.dp), verticalAlignment = Alignment.Top) {
                Box(Modifier.size(20.dp, 24.dp), contentAlignment = Alignment.Center) { if (q.from != null) HourglassGlyph(c.graphite, 14.dp) }
                Box(Modifier.size(26.dp, 24.dp), contentAlignment = Alignment.CenterStart) { CheckCircle(false, size = 16.dp) { view.confirmTick(); onSettle(qid) } }
                Spacer(Modifier.width(8.dp))
                val text = androidx.compose.ui.text.buildAnnotatedString {
                    q.time?.let { timeLink(it, late || q.due == today.toString() && it < java.time.LocalTime.now().toString().take(5), c.link, c.accent) { onTime(qid) } }
                    append(if (q.from != null) M.s.waitPrefix(q.from).trimEnd(':') + ": " + q.title else q.title)
                }
                Text(text, style = Type.small.copy(color = c.graphite, fontSize = 14.sp), maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(top = 2.dp))
            }
        }
    }
}

/** A closed day: one line per thing, no hours except a meeting's, a chevron. */
@Composable
private fun CompactLine(icon: (@Composable () -> Unit)?, text: String, faint: Boolean = false) {
    val c = M.c
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(20.dp, 20.dp), contentAlignment = Alignment.Center) { icon?.invoke() }
        Spacer(Modifier.width(8.dp))
        Text(text, style = Type.body.copy(fontSize = 15.sp, color = if (faint) c.graphite else c.ink), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * One day on the timeline. Open: «Дела» and «Темы» with hours, people and places. Closed: titles only.
 * A tap on the date (or on a closed day) opens and closes it — smoothly, the height following the content.
 */
@Composable
fun DayBlock(
    plan: DayPlan, today: LocalDate, open: Boolean, heading: Boolean, onToggle: () -> Unit,
    onOpen: (String) -> Unit, onDone: (String) -> Unit, onLater: (String) -> Unit, onTime: (String) -> Unit, landed: Set<String>,
    undated: List<Task> = emptyList(),
) {
    val c = M.c
    val s = M.s
    val past = plan.date.isBefore(today)
    var undatedOpen by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(true) }
    Column(Modifier.fillMaxWidth().animateContentSize(androidx.compose.animation.core.tween(260, easing = androidx.compose.animation.core.FastOutSlowInEasing))) {
        Margined(Modifier.padding(top = 10.dp, bottom = 10.dp), margin = {
            Box(Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onToggle)) { DayMargin(plan.date, today, heading) }
        }) {
            Column {
                if (heading) Text(plan.date.dowFull(), style = Type.display.copy(color = c.ink), modifier = Modifier.padding(bottom = 8.dp))
                if (!open) {
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClick = onToggle).padding(top = 8.dp), verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f)) {
                            val lines = buildList<@Composable () -> Unit> {
                                plan.events.forEach { e -> add { CompactLine({ CalendarGlyph(c.graphite) }, e.start.toLocalTime().toString().take(5) + "  " + e.title) } }
                                plan.deals.forEach { t -> add { CompactLine({ KindMark(t) }, t.title + (t.topicTitle?.let { "  ·  $it" } ?: "")) } }
                                plan.steps.forEach { st -> add { CompactLine({ if (st.q.from != null) HourglassGlyph(c.graphite, 14.dp) else TopicGlyph(c.graphite, 14.dp) },
                                    (if (st.q.from != null) s.waitPrefix(st.q.from).trimEnd(':') + ": " else "") + st.q.title + "  ·  " + st.topic.title) } }
                            }
                            when {
                                past && plan.empty -> CompactLine(null, if (plan.doneCount > 0) s.doneCountWord(plan.doneCount) else s.freeWord, faint = true)
                                plan.empty -> CompactLine(null, s.freeWord, faint = true)
                                else -> { lines.take(4).forEach { it() }; if (lines.size > 4) CompactLine(null, s.more(lines.size - 4), faint = true) }
                            }
                        }
                        if (!plan.empty) Icon(Icons.Rounded.KeyboardArrowDown, null, tint = c.faint, modifier = Modifier.padding(top = 4.dp).size(18.dp))
                    }
                } else {
                    val dealsAll = plan.events.isNotEmpty() || plan.deals.isNotEmpty()
                    if (plan.empty) Text(if (plan.date == today) s.freeDay else s.freeWord, style = Type.body.copy(color = c.graphite), modifier = Modifier.padding(top = 6.dp))
                    if (dealsAll) {
                        plan.events.forEach { e ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
                                Box(Modifier.size(20.dp, 24.dp), contentAlignment = Alignment.Center) { CalendarGlyph(c.graphite) }
                                Spacer(Modifier.width(34.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(e.start.toLocalTime().toString().take(5) + "   " + e.title, style = Type.body.copy(fontSize = 15.5.sp, color = c.ink))
                                    e.location?.let { Text(it, style = Type.small.copy(color = c.graphite, fontSize = 13.sp)) }
                                }
                            }
                        }
                        GroupedDeals(plan.deals, today, landed, onOpen, onDone, onLater, onTime)
                    }
                    // Matters with no day wait at the end of today, with how long they've been lying there; older ones paler.
                    if (undated.isNotEmpty()) {
                        Row(Modifier.fillMaxWidth().padding(top = 14.dp).clip(RoundedCornerShape(8.dp)).clickable { undatedOpen = !undatedOpen }.padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text(s.noDates.uppercase() + " · " + undated.size, style = Type.label.copy(color = c.graphite), modifier = Modifier.weight(1f))
                            val rot by androidx.compose.animation.core.animateFloatAsState(if (undatedOpen) 0f else -90f, label = "ud")
                            Icon(Icons.Rounded.KeyboardArrowDown, null, tint = c.faint, modifier = Modifier.size(18.dp).rotate(rot))
                        }
                        androidx.compose.animation.AnimatedVisibility(undatedOpen, enter = androidx.compose.animation.expandVertically(androidx.compose.animation.core.tween(240)) + androidx.compose.animation.fadeIn(),
                            exit = androidx.compose.animation.shrinkVertically(androidx.compose.animation.core.tween(200)) + androidx.compose.animation.fadeOut()) {
                            Column {
                                fun born(t: Task) = runCatching { LocalDate.parse(t.createdAt.take(10)) }.getOrNull()
                                GroupedDeals(undated, today, landed, onOpen, onDone, onLater, onTime,
                                    age = { t -> born(t)?.takeIf { it.isBefore(today) }?.let { s.sinceWord + " " + it.dayMonthShort() } },
                                    faded = { t -> born(t)?.isBefore(today.minusDays(7)) == true })
                            }
                        }
                    }
                    if (plan.steps.isNotEmpty()) {
                        if (dealsAll) Spacer(Modifier.height(10.dp))
                        plan.steps.groupBy { it.topic.id }.values.forEach { group ->
                            androidx.compose.runtime.key(group.first().topic.id) {
                                Box(Modifier.landing(group.first().topic.id in landed, 0)) {
                                    TopicDayRow(group.first().topic, group.map { it.q }, today, onOpen = onOpen, onSettle = onDone, onTime = onTime)
                                }
                            }
                        }
                    }
                }
            }
        }
        Hairline(Modifier.padding(start = MarginW))
    }
}
