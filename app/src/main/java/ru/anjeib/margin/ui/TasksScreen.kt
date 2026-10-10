package ru.anjeib.margin.ui

import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.text.buildAnnotatedString

import ru.anjeib.margin.data.slotRef

import ru.anjeib.margin.data.questionRow
import ru.anjeib.margin.data.Subtask

import ru.anjeib.margin.data.dative

import androidx.compose.foundation.layout.offset

import ru.anjeib.margin.data.dayOf

import ru.anjeib.margin.data.undatedTopicMatters
import ru.anjeib.margin.data.lastActivity
import ru.anjeib.margin.data.weekReview
import ru.anjeib.margin.data.accusative

import androidx.compose.ui.text.withLink

import androidx.compose.ui.text.SpanStyle

import androidx.compose.animation.animateContentSize

import kotlinx.coroutines.launch

import androidx.compose.ui.text.withStyle

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
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
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
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
import ru.anjeib.margin.data.dayMonthYear
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

/** Counters for the top tabs: today, week, all open. */
fun tabCounts(d: AppData): Triple<Int, Int, Int> {
    val today = LocalDate.now()
    val l = d.listTasks()
    // «Месяц»: open matters with a day in this month.
    val month = l.count { t -> !t.done && (t.dueDate ?: t.startDate)?.let { it.year == today.year && it.month == today.month && !it.isBefore(today) } == true }
    return Triple(l.count { isToday(it, today) }, l.count { isThisWeek(it, today) }, month)
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
    onOpen: (String) -> Unit,
    onView: (String) -> Unit,
    onOpenNote: (String) -> Unit,
    onOpenNotes: () -> Unit = {},
    onOpenInbox: () -> Unit = {},
    /** A phrase into the main field, not sent («Попробуйте» on an empty Today). */
    onTry: (String) -> Unit = {},
    contentPadding: PaddingValues,
) {
    val landed by vm.landed.collectAsState()
    val c = M.c
    val s = M.s
    val today = LocalDate.now()
    var byProject by rememberSaveable { mutableStateOf(false) }
    var label by rememberSaveable { mutableStateOf<String?>(null) }
    // «Месяц»: which list, and «личное» / «работа» or both.
    var monthTab by rememberSaveable { mutableStateOf("topics") }
    var monthSphere by rememberSaveable { mutableStateOf<String?>(null) }
    // «Обзор · Дела»: only what we wait for, or only what we promised — the whole list of who owes whom, past three days too.
    var monthOwe by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }
    var overviewKinds by androidx.compose.runtime.saveable.rememberSaveable(stateSaver = androidx.compose.runtime.saveable.listSaver<Set<String>, String>(save = { it.toList() }, restore = { it.toSet() })) { mutableStateOf(setOf("topics", "deals", "ideas")) }
    var weekDay by rememberSaveable { mutableStateOf<String?>(null) }
    // Swipe left asks «на когда?» instead of quietly moving a day.
    var postponing by remember { mutableStateOf<String?>(null) }
    postponing?.let { id -> PostponeDialog(onDismiss = { postponing = null }) { day -> vm.postponeTo(id, day); postponing = null } }
    // «На неделе», «Без даты», «Выполнено» start folded; what the user opens or folds stays so, across launches.
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val ui = remember { ctx.getSharedPreferences("ui", android.content.Context.MODE_PRIVATE) }
    var weekSeen by remember { mutableStateOf(ui.getString("weekSeen", null)) }
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
                    if (count != null) Text("  $count", style = Type.label.copy(color = c.graphite))
                    Spacer(Modifier.weight(1f))
                    // «Просрочено · Перенести»: the action sits with what it acts on, not over the list.
                    action?.let { (text, act) -> Text(text, style = Type.small.copy(color = c.link, fontSize = 13.5.sp),
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = act).padding(horizontal = 8.dp, vertical = 2.dp)) }
                    if (collapsible) Icon(Icons.Rounded.KeyboardArrowDown, null, tint = c.graphite, modifier = Modifier.size(18.dp).rotate(rot))
                }
                }
            }
        }

        when {
            view == Views.TODAY -> {
                // v2: a timeline of days. Today open — «Дела» and «Темы»; the next days with something in them, closed to one line each.
                // «Сегодня» is today only: the next days live in «Неделя» and «Все».
                val plans = listOf(dayPlan(data, today, today, events))
                val tomorrowPlan = dayPlan(data, today.plusDays(1), today, events)
                // Who owes what: late, within three days, or without a day — shown in the summary, still in their days below.
                val topicWaits = data.tasks.filter { it.topic && !it.done }.flatMap { t -> t.subtasks.filter { !it.done && it.due == null && it.from != null }.map { t.questionRow(it) } }
                val promises = (tasks + topicWaits).filter { !it.done && (it.waitingFor != null || it.promisedTo != null) }
                    .filter { t -> (t.dueDate ?: t.startDate).let { it == null || !it.isAfter(today.plusDays(3)) } }
                    .distinctBy { ru.anjeib.margin.data.slotRef(it.id)?.first ?: it.id }
                    .sortedWith(compareBy({ (it.dueDate ?: it.startDate) ?: LocalDate.MAX }, { it.title }))
                // Undated matters — simple ones and topics' — live at the end of today, oldest last.
                // Waits without a day too: the summary only gathers them, nothing leaves the day's list (owner's rule).
                val undatedAll = (tasks.filter { !it.done && it.dueDate == null && it.startDate == null } + data.undatedTopicMatters())
                    .sortedByDescending { it.createdAt }
                plans.forEach { p ->
                    item("day" + p.date) {
                        DayBlock(p, today, open = true, heading = true, onToggle = {}, topicOf = { id -> data.tasks.find { it.id == id } },
                            headActions = { HomeDoors(ui, onCalendar = { onView(Views.WEEK) }, onOverview = { onView(Views.ALL) }) },
                            onOpen = onOpen, onDone = { vm.completeWithUndo(it) }, onLater = { postponing = it }, onTime = { timeFor = it }, landed = landed,
                            undated = if (p.date == today) undatedAll else emptyList(),
                            top = if (p.date == today) ({
                                TodaySummary(p, promises, today, onOpen, data, onMoveOverdue = { vm.moveOverdue() })
                                TodayHints(p, data, ui, onRemoveSamples = { vm.removeSamples() })
                            }) else null)
                    }
                }
                // Sunday: the week in one quiet card — gathered by code, gone with «×» until next Sunday.
                val wk = today.minusDays(today.dayOfWeek.value - 1L).toString()
                val weekShown = (today.dayOfWeek == java.time.DayOfWeek.SUNDAY || settings.devMode) && weekSeen != wk
                run {
                    if (weekShown) plain("week") { WeekCard(data.weekReview(today), data.inboxNote()?.entries?.size ?: 0, onOpen, onOpenInbox) { weekSeen = wk; ui.edit().putString("weekSeen", wk).apply() } }
                }
                // Nothing written yet: three phrases to try — a tap puts one into the field, the user sends it.
                if (data.tasks.isEmpty() && data.queue.isEmpty()) plain("try") {
                    Column(Modifier.padding(top = 12.dp)) {
                        Text(s.tryHint, style = Type.small.copy(color = c.graphite, fontSize = 14.sp))
                        androidx.compose.foundation.layout.FlowRow(Modifier.padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            s.tryExamples.forEach { ex ->
                                Text(ex, style = Type.small.copy(color = c.link, fontSize = 14.sp),
                                    modifier = Modifier.clip(RoundedCornerShape(50)).background(c.linkSoft).clickable { onTry(ex) }
                                        .padding(horizontal = 12.dp, vertical = 7.dp))
                            }
                        }
                    }
                }
                // Shared or saved without a place: one line here while there is something to put away.
                val unplaced = data.inboxNote()?.entries?.size ?: 0
                // While the week's card is up it already says this — once on the screen is enough.
                if (unplaced > 0 && !weekShown) plain("inbox") {
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onOpenInbox).padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Inbox, null, tint = c.link, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(s.inboxLine(unplaced) + "  ›", style = Type.small.copy(color = c.link, fontSize = 14.sp))
                    }
                }
                // Written without internet: one quiet line here, the items themselves live in «Входящие».
                if (data.queue.isNotEmpty()) plain("queue") {
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onOpenInbox).padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.CloudOff, null, tint = c.graphite, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(s.queuedLine(data.queue.size) + "  ›", style = Type.small.copy(color = c.graphite, fontSize = 14.sp))
                    }
                }
                // «Без следующего шага» lives in «Обзор · Темы» now: «Сегодня» is about today.
                // Ideas written today: each a full line in ink by its bulb — the bulb says «идея», no badge over them.
                val notesToday = data.tasks.filter { t -> t.thought && !t.done && (t.createdAt == today.toString() || t.entries.any { it.at.startsWith(today.toString()) }) }
                if (notesToday.isNotEmpty()) plain("notes-today") {
                    Column(Modifier.fillMaxWidth().padding(top = 14.dp)) {
                        notesToday.forEach { t ->
                            Tile {
                                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onOpen(t.id) }.landing(t.id in landed, 10)
                                    .padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.width(46.dp), contentAlignment = Alignment.CenterStart) { IdeaMark() }
                                    Spacer(Modifier.width(8.dp))
                                    Text(t.title.ifBlank { s.untitled }, style = Type.body.copy(color = c.ink, fontSize = 15.5.sp), modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            
                // One quiet look ahead for the evening: tomorrow's count, a tap opens the week.
                // Matters and meetings are counted apart — «5 дел» must not be three matters and two meetings.
                val tomorrowParts = listOfNotNull(tomorrowPlan.deals.size.takeIf { it > 0 }?.let { s.summaryDeals(it) },
                    tomorrowPlan.events.size.takeIf { it > 0 }?.let { s.summaryMeetings(it) })
                if (tomorrowParts.isNotEmpty()) plain("tomorrow") {
                    Text(s.quickTomorrow + " — " + tomorrowParts.joinToString(" · ") + "  ›", style = Type.small.copy(color = c.graphite, fontSize = 14.sp),
                        modifier = Modifier.padding(top = 12.dp).clip(RoundedCornerShape(8.dp)).clickable { onView(Views.WEEK) }.padding(vertical = 6.dp))
                }
            }

            view == Views.WEEK -> {
                // The week as seven dates on the margin: past days say what got done, today is open, the rest closed.
                val days = (0L..6L).map { today.weekStart().plusDays(it) }
                header(s.thisWeekSoft, rangeLabel(today.weekStart(), today.weekEnd()))
                days.forEach { d ->
                    val p = dayPlan(data, d, today, events)
                    item("wday" + d) {
                        DayBlock(p, today, open = isDayOpen(d), heading = false, onToggle = { toggleDay(d) }, topicOf = { id -> data.tasks.find { it.id == id } },
                            onOpen = onOpen, onDone = { vm.completeWithUndo(it) }, onLater = { postponing = it }, onTime = { timeFor = it }, landed = landed)
                    }
                }
            }

            view == Views.ALL -> {
                // «Обзор» — what there is, as one feed by the last thing that happened: topics, ideas and the matters
                // without a day (those with a day live in «Календарь»). A row of marks above narrows it; all are on by default.
                fun bare(t: Task) = t.thought && t.subtasks.isEmpty()
                fun sOk(t: Task) = monthSphere == null || (if (monthSphere == "work") t.sphere == "work" else t.sphere != "work")
                plain("o-head") {
                    Column(Modifier.padding(top = 14.dp)) {
                        Text(s.overviewWord, style = Type.display.copy(color = c.ink), modifier = Modifier.padding(bottom = 10.dp))
                        // One row of marks — what kind, who owes whom, which sphere; a quiet line under it says what is shown.
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            @Composable fun mark(on: Boolean, label: String, onClick: () -> Unit, glyph: @Composable (Color) -> Unit) =
                                Box(Modifier.size(40.dp).clip(CircleShape).background(if (on) c.linkSoft else Color.Transparent)
                                    .semantics { selected = on; contentDescription = label }.clickable(onClick = onClick), contentAlignment = Alignment.Center) { glyph(if (on) c.link else c.graphite) }
                            @Composable fun gap() = Box(Modifier.padding(horizontal = 4.dp).size(1.dp, 22.dp).background(c.hairline))
                            fun flip(k: String) { overviewKinds = if (k in overviewKinds) overviewKinds - k else overviewKinds + k; if (overviewKinds.isEmpty()) overviewKinds = setOf("topics", "deals", "ideas") }
                            val kindsOn = monthOwe == null
                            mark(kindsOn && "topics" in overviewKinds, s.topicsBadge, { monthOwe = null; flip("topics") }) { TopicGlyph(it, 17.dp) }
                            mark(kindsOn && "deals" in overviewKinds, s.dealsBadge, { monthOwe = null; flip("deals") }) { Box(Modifier.size(13.dp).border(2.5.dp, it, CircleShape)) }
                            mark(kindsOn && "ideas" in overviewKinds, s.thoughts, { monthOwe = null; flip("ideas") }) { ThoughtGlyph(it, 18.dp) }
                            gap()
                            listOf("wait" to true, "promise" to false).forEach { (k, incoming) ->
                                mark(monthOwe == k, if (incoming) s.a11yWaiting else s.a11yPromised, { monthOwe = if (monthOwe == k) null else k }) { ArrowGlyph(it, 16.dp, incoming = incoming) }
                            }
                            gap()
                            listOf("personal" to c.ok, "work" to c.link).forEach { (k, col) ->
                                mark(monthSphere == k, if (k == "work") s.sphereWork else s.spherePersonal, { monthSphere = if (monthSphere == k) null else k }) {
                                    Box(Modifier.size(11.dp).clip(CircleShape).background(col))
                                }
                            }
                        }
                        val shown = buildList {
                            when (monthOwe) {
                                "wait" -> add(s.a11yWaiting)
                                "promise" -> add(s.a11yPromised)
                                else -> listOf("topics" to s.topicsBadge, "deals" to s.dealsBadge + " " + s.noDates.lowercase(), "ideas" to s.thoughts)
                                    .filter { it.first in overviewKinds }.forEach { add(it.second.lowercase()) }
                            }
                            monthSphere?.let { add(if (it == "work") s.sphereWork.lowercase() else s.spherePersonal.lowercase()) }
                        }
                        Text(shown.joinToString(" · "), style = Type.small.copy(color = c.graphite, fontSize = 13.sp), modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 8.dp))
                    }
                }
                val data2 = if (monthSphere == null) data else data.copy(tasks = data.tasks.filter { sOk(it) })
                // «ждём / обещали» is about matters only — and then all of them, with a day or without.
                val owe = monthOwe
                val topics = if (owe == null && "topics" in overviewKinds) data2.tasks.filter { it.topic && !it.done && !bare(it) } else emptyList()
                val ideas = if (owe == null && "ideas" in overviewKinds) data2.tasks.filter { bare(it) && !it.done } else emptyList()
                val deals = if (owe != null || "deals" in overviewKinds) (data2.listTasks().filter { !it.done && !it.topic || it.topicId != null } + data2.undatedTopicMatters())
                    .filter { !it.done }.distinctBy { it.id }
                    .filter { when (owe) { "wait" -> it.waitingFor != null; "promise" -> it.promisedTo != null; else -> (it.dueDate ?: it.startDate) == null } }
                    else emptyList()
                fun born(t: Task) = runCatching { LocalDate.parse(t.createdAt.take(10)) }.getOrNull()
                data class Row0(val key: String, val at: LocalDate, val topic: Task? = null, val idea: Task? = null, val deal: Task? = null)
                val feed = (topics.map { Row0("t" + it.id, it.lastActivity() ?: born(it) ?: today, topic = it) } +
                    ideas.map { Row0("i" + it.id, it.lastActivity() ?: born(it) ?: today, idea = it) } +
                    deals.map { Row0("d" + it.id, (if (owe != null) it.dueDate ?: it.startDate else null) ?: born(it) ?: today, deal = it) })
                    .sortedWith(if (owe != null) compareBy { it.at } else compareByDescending { it.at })
                if (feed.isEmpty()) plain("o-none") { Empty(s.emptyAll, s.emptyHint) }
                // The day on the margin once — rows of the same day go under it without repeating it.
                itemsIndexed(feed, key = { _, r -> r.key }) { i, r ->
                    val day = r.at.takeIf { i == 0 || feed[i - 1].at != r.at }
                    when {
                        r.topic != null -> MonthTopicRow(r.topic, onOpen, onArchive = { vm.archiveTopic(r.topic.id) }, day = day)
                        r.idea != null -> MonthIdeaRow(r.idea, data.tasks.filter { it.id != r.idea.id && it.noteId == r.idea.id }, onOpen, day = day)
                        r.deal != null -> Column(Modifier.animateItem()) {
                            Margined(Modifier.padding(top = 4.dp), margin = { day?.let { DayMargin(it, today, false, strong = true) } }) {
                                Column(Modifier.padding(top = 4.dp)) {
                                    GroupedDeals(listOf(r.deal), today, landed, onOpen, { vm.completeWithUndo(it) }, { postponing = it }, { timeFor = it },
                                        age = { t -> if (owe == null) born(t)?.takeIf { it.isBefore(today) }?.let { s.sinceWord + " " + it.dayMonthShort() } else null },
                                        topicOf = { id -> data.tasks.find { it.id == id } })
                                }
                            }
                            Hairline(Modifier.padding(start = MarginW, top = 6.dp))
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
                Text(d.dowShort().lowercase(), style = Type.small.copy(color = if (d.isBefore(today)) c.graphite else c.graphite, fontSize = 12.5.sp))
                Spacer(Modifier.height(4.dp))
                Box(Modifier.size(34.dp).clip(CircleShape).background(if (on) c.link else Color.Transparent)
                    .then(if (d == today && !on) Modifier.border(1.dp, c.link, CircleShape) else Modifier), contentAlignment = Alignment.Center) {
                    Text(d.dayOfMonth.toString(), style = Type.body.copy(color = when { on -> c.bg; d == today -> c.link; d.isBefore(today) -> c.graphite; else -> c.ink }, fontSize = 16.sp))
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
            Icon(Icons.Rounded.Close, M.s.delete, tint = c.graphite, modifier = Modifier.size(16.dp))
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
                ArrowGlyph(c.graphite, 17.dp, incoming = true)
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
                    Text(e.text, style = Type.small.copy(color = c.graphite, fontSize = 13.5.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                        t.entries.lastOrNull { it.text.isNotBlank() }?.let { e -> Text(e.text, style = Type.small.copy(color = c.graphite, fontSize = 13.5.sp), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        TaskLine(t, null, showProject && t.topicTitle == null, today = today)
                        t.topicTitle?.let { Text("↳ $it", style = Type.small.copy(color = c.link, fontSize = 13.5.sp), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp)) }
                    }
                }
            }
            Hairline(Modifier.padding(start = 36.dp))
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

/** «На завтра» in one move — the most frequent postponing; another day is in the long-press menu («Дата»). */
val LocalTomorrow = androidx.compose.runtime.staticCompositionLocalOf<((String) -> Unit)?> { null }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeTask(t: Task, onDone: () -> Unit, onLater: () -> Unit, modifier: Modifier, radius: Int = 18, content: @Composable () -> Unit) {
    val c = M.c
    val s = M.s
    val tomorrow = LocalTomorrow.current
    val state = rememberSwipeToDismissBoxState(confirmValueChange = { v ->
        when (v) {
            SwipeToDismissBoxValue.StartToEnd -> onDone()
            SwipeToDismissBoxValue.EndToStart -> if (tomorrow != null) tomorrow(t.id) else onLater()
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
                    .background(if (dir == SwipeToDismissBoxValue.Settled) (LocalRowGround.current ?: c.bg) else if (toEnd) c.ink else c.accent)
                    .padding(horizontal = 22.dp),
                contentAlignment = if (toEnd) Alignment.CenterStart else Alignment.CenterEnd,
            ) {
                if (dir != SwipeToDismissBoxValue.Settled)
                    // Show where the task will land, not an abstract «tomorrow».
                    Text(if (toEnd) (if (t.done) s.undo else s.markDone)
                        else (if (tomorrow != null) s.swipeTomorrow else s.postponeTitle) + " →",
                        style = Type.tab.copy(color = c.bg))
            }
        },
    ) { content() }
}

// ---------------- the day timeline: dates on the margin, one block per day ----------------

/** A topic's step that falls on a day. */
data class TopicStep(val topic: Task, val q: ru.anjeib.margin.data.Subtask)

/** What a day holds: single things to do, topic steps, meetings — and, for a past day, what got done. */
data class DayPlan(val date: LocalDate, val deals: List<Task>, val steps: List<TopicStep>, val events: List<CalEvent>, val doneCount: Int, val done: List<Task> = emptyList(),
                   /** «Месяц»: what else happened that day — ideas written, files attached — by time. */
                   val feed: List<DayNote> = emptyList()) {
    val empty get() = deals.isEmpty() && steps.isEmpty() && events.isEmpty()
    val quiet get() = empty && done.isEmpty() && feed.isEmpty()
}

/** The day a thing belongs to on the timeline: late ones gather on today. */
private fun dayOf(due: LocalDate?, start: LocalDate?, today: LocalDate): LocalDate? {
    val d = due ?: start ?: return null
    return if (d.isBefore(today)) today else d
}

/** An idea or a file of a day in the «Месяц» feed: [owner] — the idea or the topic it belongs to. */
data class DayNote(val at: String, val owner: Task, val idea: Boolean, val file: ru.anjeib.margin.data.Attachment? = null)

/** Ideas written and files attached on [date], in the order they came. */
fun dayNotes(data: AppData, date: LocalDate): List<DayNote> {
    val d = date.toString()
    val ideas = data.tasks.filter { it.thought && it.subtasks.isEmpty() && !it.done }.mapNotNull { t ->
        val at = t.entries.filter { it.at.startsWith(d) }.minOfOrNull { it.at } ?: if (t.createdAt == d) d + "T00:00" else null
        at?.let { DayNote(it, t, idea = true) }
    }
    val files = data.tasks.filter { !(it.thought && it.subtasks.isEmpty()) }.flatMap { t ->
        t.entries.filter { it.at.startsWith(d) }.flatMap { e -> e.attachments.map { DayNote(e.at, t, idea = false, file = it) } }
    }
    return (ideas + files).sortedBy { it.at }
}

fun dayPlan(data: AppData, date: LocalDate, today: LocalDate, events: List<CalEvent>): DayPlan {
    // v2: a topic shows on a day only through its matters for that day — ordinary rows, grouped under one link to the topic.
    val deals = data.listTasks().filter { !it.done && dayOf(it.dueDate, it.startDate, today) == date }
        // A past day's leftovers first; today's passed hours keep their place in the day's order.
        .sortedWith(compareBy({ it.dueDate?.isBefore(today) != true }, { it.time ?: "99" }, { it.title }))
    val steps = emptyList<TopicStep>()
    // An event shows on every day it covers (a three-day trip, a holiday); all-day ends are exclusive midnights.
    val evs = events.filter { e ->
        val first = e.start.toLocalDate()
        val last = if (e.allDay || e.end.toLocalTime() == java.time.LocalTime.MIDNIGHT) e.end.toLocalDate().minusDays(1) else e.end.toLocalDate()
        !date.isBefore(first) && !date.isAfter(maxOf(first, last))
    }
    // A past day shows what got done on it, struck through — the matters themselves, not a count.
    val doneList = if (date.isBefore(today)) data.tasks.filter { it.doneAt == date.toString() && !it.topic && !it.dropped } +
        data.tasks.filter { it.topic }.flatMap { t -> t.subtasks.filter { it.done && it.doneAt == date.toString() }.map { t.questionRow(it).copy(done = true) } } else emptyList()
    return DayPlan(date, deals, steps, evs, doneList.size, doneList)
}

/** The date on the margin: the number in serif, the month and the weekday small under it; today in petrol. */
@Composable
internal fun DayMargin(date: LocalDate, today: LocalDate, big: Boolean, strong: Boolean = false, todayMark: Boolean = !strong) {
    val c = M.c
    // [strong]: a day that marks where something began or was written (a topic, an idea, a record) — never faded.
    val past = date.isBefore(today) && !strong
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = if (big) 2.dp else 8.dp)) {
        Text(date.dayOfMonth.toString(), style = Type.display.copy(color = when { date == today && !big && todayMark -> c.link; past -> c.graphite; else -> c.ink },
            fontSize = if (big) 40.sp else 26.sp, lineHeight = if (big) 42.sp else 28.sp))
        Text(date.dayMonthShort().substringAfter(' '), style = Type.small.copy(color = if (past) c.graphite else c.ink, fontSize = 11.5.sp, fontWeight = FontWeight.Medium))
        if (!big) Text(if (date.year != today.year) "’" + "%02d".format(date.year % 100) else date.dowShort().lowercase(), style = Type.small.copy(color = c.graphite, fontSize = 11.sp, fontWeight = FontWeight.Medium))
    }
}

/** The little column right of the rule: what kind of thing it is — waiting, promised, repeating, a meeting, a topic. */
@Composable
internal fun KindMark(t: Task?) {
    val c = M.c
    Box(Modifier.size(20.dp, 24.dp), contentAlignment = Alignment.Center) {
        when {
            t == null -> {}
            t.waitingFor != null -> ArrowGlyph(c.graphite, 14.dp, incoming = true)
            t.promisedTo != null -> ArrowGlyph(c.graphite, 14.dp)
            t.repeat != null -> RepeatGlyph(c.graphite, 14.dp)
            // The clock beside an hour: a small accent the owner asked to keep, even though the hour is right there.
            t.time != null -> ClockGlyph(c.graphite, 14.dp)
        }
    }
}

/** The hour before the words, coloured by [hourColor]; not a separate target — a tap anywhere opens the matter (the hour — by a long press). */
private fun androidx.compose.ui.text.AnnotatedString.Builder.timeLink(time: String, color: Color) {
    withStyle(SpanStyle(color = color, fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum")) { append(time) }
    append("   ")
}

/** One thing to do on an open day: kind mark, the circle, the hour as a link, the words, a quiet line under. */
@Composable
internal fun DealRow(t: Task, today: LocalDate, onToggle: () -> Unit, onOpen: () -> Unit, onTime: () -> Unit, age: String? = null, faded: Boolean = false, onLater: (() -> Unit)? = null,
                     /** Off the day lists (the tour) the day is not around the row — it is said in it. */
                     whenText: String? = null,
                     /** On a person's page their own name is not repeated in each row. */
                     hidePerson: String? = null) {
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
    var quick by remember(t.id) { mutableStateOf(false) }
    // A tap anywhere opens the matter; a long press — the few things done most: done, the day, the hour.
    Box {
    SoftMenu(quick, { quick = false }) {
        val tomorrow = LocalTomorrow.current
        SoftMenuItem(s.markDone) { quick = false; tick() }
        if (tomorrow != null && t.dueDate != today.plusDays(1)) SoftMenuItem(s.swipeTomorrow) { quick = false; tomorrow(t.id) }
        if (onLater != null) SoftMenuItem(s.dateWord + "…") { quick = false; onLater() }
        SoftMenuItem(s.fieldTime) { quick = false; onTime() }
    }
    // TalkBack gets the swipes and the long press as named actions — gestures alone are invisible to it.
    val tomorrowAct = LocalTomorrow.current
    Row(Modifier.fillMaxWidth().background(LocalRowGround.current ?: c.bg).combinedClickable(onClick = onOpen, onLongClick = { view.confirmTick(); quick = true })
        .semantics {
            customActions = listOfNotNull(
                androidx.compose.ui.semantics.CustomAccessibilityAction(s.markDone) { onToggle(); true },
                tomorrowAct?.let { f -> androidx.compose.ui.semantics.CustomAccessibilityAction(s.swipeTomorrow) { f(t.id); true } },
                onLater?.let { f -> androidx.compose.ui.semantics.CustomAccessibilityAction(s.dateWord) { f(); true } },
                androidx.compose.ui.semantics.CustomAccessibilityAction(s.fieldTime) { onTime(); true },
            )
        }.padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
        KindMark(t)
        Box(Modifier.size(26.dp, 24.dp), contentAlignment = Alignment.CenterStart) { CheckCircle(ticking, size = 18.dp, onToggle = ::tick) }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            val hour = hourColor(t.dueDate, t.time, today)
            val title = androidx.compose.ui.text.buildAnnotatedString {
                t.time?.let { timeLink(it, hour) }
                append(t.title)
            }
            Text(title, style = Type.body.copy(fontSize = 15.5.sp, color = if (ticking || faded) c.graphite else c.ink), maxLines = 3, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 1.dp).drawStrike(strike.value, c.faint))
            if (late && t.dueDate?.isBefore(today) == true) Text(s.lateSince(t.dueDate!!.dayMonthShort()), style = Type.small.copy(color = c.accentText, fontSize = 12.5.sp))
            t.dayOf()?.let { (i, n) -> Text(s.dayOfDays(i, n), style = Type.small.copy(color = c.graphite, fontSize = 12.5.sp)) }
            TaskLine(t, whenText, true, today = today, tappable = false, hidePerson = hidePerson)
        }
        age?.let { Text(it, style = Type.small.copy(color = c.graphite, fontSize = 12.5.sp, fontFeatureSettings = "tnum"), modifier = Modifier.padding(start = 8.dp, top = 3.dp)) }
    }
    }
}

/**
 * Deal rows; the matters of one topic stay together as one block under the topic's name (owner's choice in 0.18),
 * with a quiet line under them: when it began, how much is written, its other matters.
 */
@Composable
internal fun GroupedDeals(list: List<Task>, today: LocalDate, landed: Set<String>, onOpen: (String) -> Unit, onDone: (String) -> Unit, onLater: (String) -> Unit, onTime: (String) -> Unit,
                         age: ((Task) -> String?)? = null, faded: (Task) -> Boolean = { false }, topicOf: (String) -> Task? = { null },
                         /** Topics' matters stay folded under the topic's name (no day of their own to show them on). */
                         foldTopics: Boolean = false) {
    val c = M.c
    val s = M.s
    val seen = mutableSetOf<String>()
    // Topics whose other matters are unfolded under their link.
    val unfolded = androidx.compose.runtime.saveable.rememberSaveable(saver = androidx.compose.runtime.saveable.listSaver<androidx.compose.runtime.MutableState<Set<String>>, String>(
        save = { it.value.toList() }, restore = { mutableStateOf(it.toSet()) })) { mutableStateOf(emptySet<String>()) }
    @Composable
    fun row(g: Task, tid: String?, depth: Int, pale: Boolean, keyPrefix: String = "") = androidx.compose.runtime.key(keyPrefix + g.id) {
        // One level for every matter; only a matter put under another matter steps in.
        SwipeTask(g, onDone = { onDone(g.id) }, onLater = { onLater(g.id) }, modifier = Modifier.padding(start = (depth * 22).dp)
            .landing(keyPrefix.isEmpty() && (g.id in landed || (tid != null && tid in landed)), 0), radius = 0) {
            DealRow(g, today, onToggle = { onDone(g.id) }, onOpen = { onOpen(g.id) }, onTime = { onTime(g.id) }, age = if (pale) null else age?.invoke(g), faded = pale || faded(g), onLater = { onLater(g.id) },
                // Off the day: its own day is said in the row («11 окт», «завтра»).
                whenText = if (pale) g.dueDate?.let { if (it == today.plusDays(1)) s.quickTomorrow.lowercase() else it.dayMonthShort() } else null)
        }
    }
    list.forEach { t ->
        val tid = t.topicId
        if (tid != null && tid in seen) return@forEach
        val group = if (tid == null) listOf(t) else if (foldTopics) emptyList() else list.filter { it.topicId == tid }
        if (tid != null) seen += tid
        // Each matter on its own soft block; a topic's matters share one.
        Tile {
            // Several or one: the topic's matters come as one block under its name — whose they are is seen before what they are.
            if (tid != null) Row(Modifier.fillMaxWidth().heightIn(min = 40.dp).clip(RoundedCornerShape(6.dp)).clickable { onOpen(tid) }.padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically) {
                TopicGlyph(c.link, 13.dp)
                Spacer(Modifier.width(7.dp))
                Text((t.topicTitle ?: "") + "  ›", style = Type.small.copy(color = c.link, fontSize = 14.sp, fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            // The day's own matters of the topic (overdue or today) stand open; under another of them — a step in.
            val groupIds = group.map { (slotRef(it.id)?.first ?: it.id).substringAfterLast(':') }.toSet()
            val topic = tid?.let(topicOf)
            group.forEach { g ->
                val parent = topic?.subtasks?.find { it.id == (slotRef(g.id)?.first ?: g.id).substringAfterLast(':') }?.parent
                row(g, tid, if (parent != null && parent in groupIds) 1 else 0, false)
            }
            if (tid != null && topic != null) {
                // Small under it: when the topic began, how much is written, and its other matters — folded; a tap unfolds them here.
                val pool = topic.subtasks.filter { !it.done && it.id !in groupIds }
                val ids = pool.map { it.id }.toSet()
                fun under(p: String, d: Int): List<Pair<Subtask, Int>> = pool.filter { it.parent == p }.flatMap { listOf(it to d) + under(it.id, d + 1) }
                val others = pool.filter { it.parent == null || it.parent !in ids }.flatMap { listOf(it to 0) + under(it.id, 1) }
                val born = runCatching { LocalDate.parse(topic.createdAt) }.getOrNull()
                val open = tid in unfolded.value
                val rest = listOfNotNull(topic.entries.size.takeIf { it > 0 }?.let { s.nEntries(it) },
                    others.size.takeIf { it > 0 }?.let { (if (group.isEmpty()) s.summaryDeals(it) else s.moreDeals(it)) + if (open) " ▴" else " ▾" })
                // «тема с 18 авг» in the grey of the words around it — a date in ink here read as the matter's own date.
                Text(listOfNotNull(born?.let { s.topicSince(it.dayMonthYear(today)) }).plus(rest).joinToString("  ·  "),
                    style = Type.small.copy(color = c.graphite, fontSize = 12.5.sp),
                    modifier = Modifier.padding(start = 54.dp, bottom = 6.dp).offset(y = (-4).dp).clip(RoundedCornerShape(6.dp))
                        .clickable(enabled = others.isNotEmpty()) { unfolded.value = if (open) unfolded.value - tid else unfolded.value + tid }.padding(vertical = 2.dp))
                androidx.compose.animation.AnimatedVisibility(open && others.isNotEmpty(), enter = androidx.compose.animation.expandVertically(androidx.compose.animation.core.tween(240)) + androidx.compose.animation.fadeIn(),
                    exit = androidx.compose.animation.shrinkVertically(androidx.compose.animation.core.tween(200)) + androidx.compose.animation.fadeOut()) {
                    // The topic's other matters, on the same level and paler — they belong to the topic, not to this day.
                    Column { others.forEach { (q, d) -> row(topic.questionRow(q), tid, d, true, "o") } }
                }
            }

        }
    }
}

/**
 * The top of today: «7 дел · 2 темы · 1 встреча», then who owes what — the matters stay in their days below,
 * this only gathers them so they are seen at once.
 */
@Composable
private fun TodaySummary(plan: DayPlan, promises: List<Task>, today: LocalDate, onOpen: (String) -> Unit, data: AppData = AppData(), onMoveOverdue: () -> Unit = {}) {
    val c = M.c
    val s = M.s
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val topics = plan.deals.mapNotNull { it.topicId }.distinct().size
    // An empty day says «Свободный день» under it — a card with «0 дел» would only repeat it.
    if (plan.deals.isEmpty() && plan.events.isEmpty() && promises.isEmpty()) return
    // How much the day holds — a quiet line of its own: in the promises' card it read as if they were counted.
    Column(Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
        Text(listOfNotNull(s.summaryDeals(plan.deals.size), topics.takeIf { it > 0 }?.let { s.summaryTopics(it) }, plan.events.size.takeIf { it > 0 }?.let { s.summaryMeetings(it) })
            .joinToString("  ·  "), style = Type.small.copy(color = c.graphite, fontSize = 14.sp), modifier = Modifier.padding(start = 2.dp, bottom = 8.dp))
        // Several own matters left from past days: one tap puts them all on today (others' promises keep their dates).
        val ownLate = plan.deals.count { it.dueDate?.isBefore(today) == true && it.waitingFor == null && it.promisedTo == null }
        if (ownLate >= 2) Row(Modifier.padding(start = 2.dp, bottom = 6.dp).offset(y = (-6).dp), verticalAlignment = Alignment.CenterVertically) {
            Text(s.lateLeft(ownLate), style = Type.small.copy(color = c.accentText, fontSize = 14.sp))
            Text("  ·  ", style = Type.small.copy(color = c.graphite, fontSize = 14.sp))
            PaperLink(s.allToToday, onClick = onMoveOverdue)
        }
        // A soft petrol card for who owes whom, set apart from the matters under it.
        if (promises.isNotEmpty()) Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.linkSoft.copy(alpha = if (c.dark) 0.5f else 0.6f))
            .padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(Modifier.padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(34.dp), contentAlignment = Alignment.CenterStart) { Box(Modifier.padding(start = 4.dp)) { PeopleGlyph(c.link, 18.dp) } }
                Text(s.promisesTitle.uppercase(), style = Type.label.copy(color = c.link))
            }
            var allPromises by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
            // Three lines, the most pressing first (overdue, today); the rest behind «ещё N ›» so the day's matters stay in sight.
            val ordered = promises.sortedBy { it.dueDate ?: it.startDate ?: LocalDate.MAX }
            (if (allPromises) ordered else ordered.take(3)).forEach { t ->
                val d = t.dueDate ?: t.startDate
                val late = d?.isBefore(today) == true
                // The whole promise is read, never cut: a long one wraps under itself; the dates stand in one column on the right.
                val incoming = t.waitingFor != null
                val who = t.waitingFor ?: t.promisedTo!!
                // From their day on (or a week without one), a wait offers «напомнить» — the words ready, the user sends.
                val nudge = incoming && (d?.let { !it.isAfter(today) } ?: (runCatching { LocalDate.parse(t.createdAt.take(10)) }.getOrNull()?.isBefore(today.minusDays(7)) == true))
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { onOpen(t.id) }.padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
                    Box(Modifier.width(34.dp).semantics { contentDescription = if (incoming) s.a11yWaiting else s.a11yPromised }, contentAlignment = Alignment.CenterStart) {
                        SoftRound(c.link) { ArrowGlyph(c.link, 14.dp, incoming = incoming) }
                    }
                    Column(Modifier.weight(1f).padding(top = 3.dp)) {
                        Text(androidx.compose.ui.text.buildAnnotatedString {
                            // «от Димы · макет» / «Лёше · исходники» — and no name at all when the title already says it.
                            if (!namedIn(t.title, who)) withStyle(SpanStyle(color = c.link)) { append((if (incoming) s.fromWho(who) else who.dative()) + "  ·  ") }
                            withStyle(SpanStyle(color = c.ink)) { append(t.title) }
                        }, style = Type.small.copy(fontSize = 14.5.sp, lineHeight = 20.sp))
                        if (nudge) Box(Modifier.heightIn(min = 36.dp).clip(RoundedCornerShape(6.dp)).clickable { remindAbout(ctx, data, t) }.padding(end = 8.dp), contentAlignment = Alignment.CenterStart) {
                            Text(s.remindLink, style = Type.small.copy(color = c.link, fontSize = 13.sp, fontWeight = FontWeight.Medium))
                        }
                    }
                    Text(d?.let { if (it == today) s.quickToday.lowercase() else it.dayMonthShort() } ?: "", style = dateStyle(12.5.sp, late),
                        textAlign = androidx.compose.ui.text.style.TextAlign.End, modifier = Modifier.width(58.dp).padding(start = 8.dp, top = 5.dp))
                }
            }
            if (promises.size > 3) Text(if (allPromises) s.collapse + " ▴" else s.more(promises.size - 3) + " ▾", style = Type.small.copy(color = c.link, fontSize = 13.sp),
                modifier = Modifier.padding(start = 34.dp, top = 2.dp).clip(RoundedCornerShape(6.dp)).clickable { allPromises = !allPromises }.padding(vertical = 4.dp, horizontal = 2.dp))
        }
    }
}

/** «Месяц · Темы»: a topic with when it began; «N записей ▾» unfolds its records right here. */
@Composable
private fun MonthTopicRow(t: Task, onOpen: (String) -> Unit, onArchive: () -> Unit = {}, day: LocalDate? = t.lastActivity()) {
    val c = M.c
    val s = M.s
    var open by androidx.compose.runtime.saveable.rememberSaveable(t.id) { mutableStateOf(false) }
    val born = runCatching { LocalDate.parse(t.createdAt) }.getOrNull()
    // On the margin — when something last happened in it; when it began is a quiet word under the title.
    val last = t.lastActivity()
    Column(Modifier.fillMaxWidth().animateContentSize(androidx.compose.animation.core.tween(240))) {
        Margined(Modifier.padding(top = 4.dp), margin = { day?.let { DayMargin(it, LocalDate.now(), false, strong = true) } }) {
            Column(Modifier.padding(top = 8.dp)) {
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { onOpen(t.id) }, verticalAlignment = Alignment.Top) {
                    Box(Modifier.padding(top = 3.dp)) { TopicGlyph(c.graphite, 16.dp) }
                    Spacer(Modifier.width(10.dp))
                    Text(t.title, style = Type.body.copy(fontSize = 16.sp, color = c.ink, fontWeight = FontWeight.Medium), modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                val openDeals = t.subtasks.count { !it.done }
                // Always one line under the title: the sphere, how much is in it, since when.
                // The parts wrap whole onto a second line — «начата 19 сен» is never cut to «начата 19…».
                val parts = buildList<Pair<String, Boolean>> {
                    add(s.nEntries(t.entries.size) + (if (t.entries.isNotEmpty()) (if (open) " ▴" else " ▾") else "") to t.entries.isNotEmpty())
                    if (openDeals > 0) add(s.summaryDeals(openDeals) to false)
                    born?.takeIf { it != last }?.let { add(s.startedOn(it.dayMonthYear()) to false) }
                }
                Row(Modifier.padding(start = 26.dp, top = 4.dp), verticalAlignment = Alignment.Top) {
                    if (t.sphere != null) { Box(Modifier.padding(top = 1.dp)) { SphereBadge(t.sphere) }; Spacer(Modifier.width(8.dp)) }
                    DotFlow(parts.size, separator = { Text("  ·  ", style = Type.small.copy(color = c.graphite, fontSize = 13.sp)) }) { i ->
                        val (text, tap) = parts[i]
                        Text(text, style = Type.small.copy(color = c.graphite, fontSize = 13.sp),
                            modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(enabled = tap) { open = !open }.padding(vertical = 2.dp))
                    }
                }
                // Nothing left to do or wait for: say so, and offer the archive — quietly, here and not on «Сегодня».
                if (t.noNextStep) Row(Modifier.padding(start = 26.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(s.allDoneShort, style = Type.small.copy(color = c.ok, fontSize = 13.sp))
                    Text("  ·  ", style = Type.small.copy(color = c.graphite))
                    PaperLink(s.toArchive, onClick = onArchive)
                }
                if (open) Column(Modifier.padding(start = 26.dp, top = 6.dp)) {
                    t.entries.forEach { e ->
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).clickable { onOpen(t.id) }.padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
                            Text(runCatching { LocalDate.parse(e.at.take(10)).dayMonthShort() }.getOrDefault(""), style = dateStyle(12.5.sp),
                                modifier = Modifier.width(52.dp).padding(top = 1.dp))
                            Column(Modifier.weight(1f)) {
                                if (e.text.isNotBlank()) Text(e.text, style = Type.small.copy(color = c.ink, fontSize = 14.sp), maxLines = 3, overflow = TextOverflow.Ellipsis)
                                attachmentsSummary(e.attachments)?.let { Text(it, style = Type.small.copy(color = c.link, fontSize = 12.5.sp)) }
                            }
                        }
                    }
                }
            }
        }
        Hairline(Modifier.padding(start = MarginW, top = 10.dp))
    }
}

/** «Обзор · Идеи»: an idea, the day it began on the margin, «N записей ▾» unfolding its records, and what grew from it. */
@Composable
private fun MonthIdeaRow(t: Task, grown: List<Task>, onOpen: (String) -> Unit, day: LocalDate? = t.lastActivity()) {
    val c = M.c
    val s = M.s
    var open by androidx.compose.runtime.saveable.rememberSaveable(t.id) { mutableStateOf(false) }
    val born = runCatching { LocalDate.parse(t.createdAt) }.getOrNull()
    // The same as a topic in the list: on the margin — when it was last written in; under the title one line — sphere, records, since when.
    val last = t.lastActivity()
    Column(Modifier.fillMaxWidth().animateContentSize(androidx.compose.animation.core.tween(240))) {
        Margined(Modifier.padding(top = 4.dp), margin = { day?.let { DayMargin(it, LocalDate.now(), false, strong = true) } }) {
            Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { onOpen(t.id) }, verticalAlignment = Alignment.Top) {
                    // An idea is the same mark everywhere: the bulb on its soft sage round.
                    Box(Modifier.offset(x = (-4).dp)) { IdeaMark(22.dp) }
                    Spacer(Modifier.width(10.dp))
                    Text(t.title, style = Type.body.copy(fontSize = 16.sp, color = c.ink, fontWeight = FontWeight.Medium), modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Row(Modifier.padding(start = 26.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (t.sphere != null) { SphereBadge(t.sphere); Spacer(Modifier.width(8.dp)) }
                    val parts = listOfNotNull(t.entries.size.takeIf { it > 0 }?.let { (s.nEntries(it) + if (open) " ▴" else " ▾") to true },
                        born?.takeIf { it != last }?.let { s.startedOn(it.dayMonthYear()) to false })
                    DotFlow(parts.size, separator = { Text("  ·  ", style = Type.small.copy(color = c.graphite, fontSize = 13.sp)) }) { i ->
                        val (text, tap) = parts[i]
                        Text(text, style = Type.small.copy(color = c.graphite, fontSize = 13.sp),
                            modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(enabled = tap) { open = !open }.padding(vertical = 2.dp))
                    }
                }
                if (open) Column(Modifier.padding(start = 26.dp, top = 6.dp)) {
                    t.entries.forEach { e ->
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).clickable { onOpen(t.id) }.padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
                            Text(runCatching { LocalDate.parse(e.at.take(10)).dayMonthShort() }.getOrDefault(""), style = dateStyle(12.5.sp),
                                modifier = Modifier.width(52.dp).padding(top = 1.dp))
                            Column(Modifier.weight(1f)) {
                                if (e.text.isNotBlank()) Text(e.text, style = Type.small.copy(color = c.ink, fontSize = 14.sp), maxLines = 3, overflow = TextOverflow.Ellipsis)
                                e.items.forEach { Text("•  " + it.title, style = Type.small.copy(color = c.ink, fontSize = 14.sp)) }
                                attachmentsSummary(e.attachments)?.let { Text(it, style = Type.small.copy(color = c.link, fontSize = 12.5.sp)) }
                            }
                        }
                    }
                }
                grown.forEach { g ->
                    Row(Modifier.padding(start = 24.dp, top = 4.dp).clip(RoundedCornerShape(6.dp)).clickable { onOpen(g.id) }.padding(vertical = 2.dp, horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("↳", style = Type.small.copy(color = c.link, fontSize = 13.sp))
                        Spacer(Modifier.width(5.dp))
                        if (g.topic) TopicGlyph(c.link, 12.dp) else if (g.done) TickGlyph(c.ok, 12.dp) else ArrowGlyph(c.link, 12.dp)
                        Spacer(Modifier.width(5.dp))
                        Text(g.title, style = Type.small.copy(color = c.link, fontSize = 13.5.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        Hairline(Modifier.padding(start = MarginW, top = 10.dp))
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
                Box(Modifier.size(20.dp, 24.dp), contentAlignment = Alignment.Center) { if (q.from != null) ArrowGlyph(c.graphite, 14.dp, incoming = true) }
                Box(Modifier.size(26.dp, 24.dp), contentAlignment = Alignment.CenterStart) { CheckCircle(false, size = 16.dp) { view.confirmTick(); onSettle(qid) } }
                Spacer(Modifier.width(8.dp))
                val hour = hourColor(q.due?.let { runCatching { LocalDate.parse(it) }.getOrNull() }, q.time, today)
                val text = androidx.compose.ui.text.buildAnnotatedString {
                    q.time?.let { timeLink(it, hour) }
                    append(if (q.from != null) M.s.waitPrefix(q.from).trimEnd(':') + ": " + q.title else q.title)
                }
                Text(text, style = Type.small.copy(color = c.graphite, fontSize = 14.sp), maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(top = 2.dp))
            }
        }
    }
}

/** A closed day: one line per thing, no hours except a meeting's, a chevron. */
@Composable
private fun CompactLine(icon: (@Composable () -> Unit)?, text: String, faint: Boolean = false, struck: Boolean = false) {
    val c = M.c
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(20.dp, 20.dp), contentAlignment = Alignment.Center) { icon?.invoke() }
        Spacer(Modifier.width(8.dp))
        Text(text, style = Type.body.copy(fontSize = 15.sp, color = if (faint || struck) c.graphite else c.ink,
            textDecoration = if (struck) androidx.compose.ui.text.style.TextDecoration.LineThrough else null), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * One day on the timeline. Open: «Дела» and «Темы» with hours, people and places. Closed: titles only.
 * A tap on the date (or on a closed day) opens and closes it — smoothly, the height following the content.
 */
@Composable
fun DayBlock(
    plan: DayPlan, today: LocalDate, open: Boolean, heading: Boolean, onToggle: () -> Unit,
    /** Beside the day's name on «Сегодня»: the ways to «Календарь» and «Обзор» (no tabs above — the day is the home). */
    headActions: (@Composable () -> Unit)? = null,
    onOpen: (String) -> Unit, onDone: (String) -> Unit, onLater: (String) -> Unit, onTime: (String) -> Unit, landed: Set<String>,
    undated: List<Task> = emptyList(),
    /** Over today's matters: a short summary — how much, and who owes what. */
    top: (@Composable () -> Unit)? = null,
    /** The topic behind a matter row, for the small line under its link. */
    topicOf: (String) -> Task? = { null },
) {
    val c = M.c
    val s = M.s
    val past = plan.date.isBefore(today)
    // «Без даты»: open unless the user folded it — that choice is kept (the owner's call, 0.18).
    val uiPrefs = androidx.compose.ui.platform.LocalContext.current.getSharedPreferences("ui", android.content.Context.MODE_PRIVATE)
    var undatedOpen by androidx.compose.runtime.saveable.rememberSaveable {
        mutableStateOf(uiPrefs.getBoolean("undatedOpen", true))
    }
    Column(Modifier.fillMaxWidth().animateContentSize(androidx.compose.animation.core.tween(260, easing = androidx.compose.animation.core.FastOutSlowInEasing))) {
        Margined(Modifier.padding(top = 10.dp, bottom = 10.dp), margin = {
            Box(Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onToggle)) { DayMargin(plan.date, today, heading) }
        }) {
            Column {
                if (heading) Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(plan.date.dowFull(), style = Type.display.copy(color = c.ink), modifier = Modifier.weight(1f))
                    headActions?.invoke()
                }
                if (heading && open) top?.invoke()
                if (!open) {
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClick = onToggle).padding(top = 8.dp), verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f)) {
                            val lines = buildList<@Composable () -> Unit> {
                                plan.events.forEach { e -> add { CompactLine({ CalendarGlyph(c.graphite) }, (if (e.allDay) s.allDay else e.start.toLocalTime().toString().take(5)) + "  " + e.title) } }
                                plan.deals.forEach { t -> add { CompactLine({ KindMark(t) }, t.title + (t.topicTitle?.let { "  ·  $it" } ?: "")) } }
                                plan.steps.forEach { st -> add { CompactLine({ if (st.q.from != null) ArrowGlyph(c.graphite, 14.dp, incoming = true) else TopicGlyph(c.graphite, 14.dp) },
                                    (if (st.q.from != null) s.waitPrefix(st.q.from).trimEnd(':') + ": " else "") + st.q.title + "  ·  " + st.topic.title) } }
                            }
                            when {
                                plan.empty && plan.feed.isNotEmpty() && plan.done.isEmpty() -> plan.feed.take(4).forEach { n -> CompactLine({ if (n.idea) ThoughtGlyph(c.graphite, 14.dp) else Icon(kindIcon(n.file!!.kind), null, tint = c.graphite, modifier = Modifier.size(14.dp)) },
                                    if (n.idea) n.owner.title else (n.file!!.caption ?: attachmentLabel(n.file).ifBlank { n.file.name }) + "  ·  " + n.owner.title) }
                                past && plan.empty && plan.done.isNotEmpty() -> {
                                    plan.done.take(4).forEach { t -> CompactLine({ TickGlyph(c.ok, 13.dp) }, t.title, struck = true) }
                                    if (plan.done.size > 4) CompactLine(null, s.more(plan.done.size - 4), faint = true)
                                }
                                past && plan.empty -> CompactLine(null, s.freeWord, faint = true)
                                plan.empty -> CompactLine(null, s.freeWord, faint = true)
                                else -> { lines.take(4).forEach { it() }; if (lines.size > 4) CompactLine(null, s.more(lines.size - 4), faint = true) }
                            }
                        }
                        if (!plan.empty) Icon(Icons.Rounded.KeyboardArrowDown, null, tint = c.graphite, modifier = Modifier.padding(top = 4.dp).size(18.dp))
                    }
                } else {
                    val dealsAll = plan.events.isNotEmpty() || plan.deals.isNotEmpty()
                    if (plan.quiet) Text(if (plan.date == today) s.freeDay else s.freeWord, style = Type.body.copy(color = c.graphite), modifier = Modifier.padding(top = 6.dp))
                    if (dealsAll) {
                        val ctx = androidx.compose.ui.platform.LocalContext.current
                        plan.events.forEach { e ->
                            Tile {
                                // A tap opens the phone calendar at that hour — the event itself lives there.
                                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable {
                                    val ms = e.start.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                                    runCatching { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("content://com.android.calendar/time/$ms"))) }
                                }.padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
                                    Box(Modifier.size(20.dp, 24.dp), contentAlignment = Alignment.Center) { CalendarGlyph(c.graphite) }
                                    Spacer(Modifier.width(34.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text((if (e.allDay) s.allDay else e.start.toLocalTime().toString().take(5)) + "   " + e.title, style = Type.body.copy(fontSize = 15.5.sp, color = c.ink))
                                        e.location?.let { Text(it, style = Type.small.copy(color = c.graphite, fontSize = 13.sp)) }
                                    }
                                }
                            }
                        }
                        GroupedDeals(plan.deals, today, landed, onOpen, onDone, onLater, onTime, topicOf = topicOf)
                    }
                    // «Месяц»: ideas written and files attached that day, by time — a tap opens the idea or the topic.
                    plan.feed.forEach { n ->
                        Tile {
                            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { onOpen(n.owner.id) }.padding(vertical = 7.dp), verticalAlignment = Alignment.Top) {
                                if (n.idea) { Box(Modifier.width(46.dp), contentAlignment = Alignment.CenterStart) { IdeaMark() }; Spacer(Modifier.width(8.dp)) }
                                else {
                                    Box(Modifier.size(20.dp, 22.dp), contentAlignment = Alignment.Center) { Icon(kindIcon(n.file!!.kind), null, tint = c.graphite, modifier = Modifier.size(15.dp)) }
                                    Spacer(Modifier.width(34.dp))
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(if (n.idea) n.owner.title else (n.file!!.caption ?: attachmentLabel(n.file).ifBlank { n.file.name }), style = Type.body.copy(fontSize = 15.sp, color = c.ink), maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text(listOfNotNull(n.at.drop(11).take(5).takeIf { it != "00:00" && it.isNotBlank() }, if (n.idea) s.thoughtWord else n.owner.title).joinToString("  ·  "),
                                        style = Type.small.copy(color = if (n.idea) c.graphite else c.link, fontSize = 12.5.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                    // What got done that day: struck through, a tap opens it (and the tick there can take it back).
                    plan.done.forEach { t ->
                        Tile {
                            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { onOpen(t.topicId ?: t.id) }.padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(20.dp, 20.dp))
                                Box(Modifier.size(26.dp, 20.dp), contentAlignment = Alignment.CenterStart) { TickGlyph(c.ok, 15.dp) }
                                Spacer(Modifier.width(8.dp))
                                Text(t.title + (t.topicTitle?.let { "  ·  $it" } ?: ""), style = Type.body.copy(fontSize = 15.sp, color = c.graphite,
                                    textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough), maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    // Matters with no day wait at the end of today, with how long they've been lying there; older ones paler.
                    // A topic's undated matters fold under its name; a topic already in the day keeps them under «ещё N дел» there.
                    val undatedShown = undated.filter { u -> u.topicId == null || plan.deals.none { it.topicId == u.topicId } }
                    if (undatedShown.isNotEmpty()) {
                        Row(Modifier.fillMaxWidth().padding(top = 14.dp).clip(RoundedCornerShape(8.dp)).clickable { undatedOpen = !undatedOpen; uiPrefs.edit().putBoolean("undatedOpen", undatedOpen).apply() }.padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(1f)) { GroupBadge(s.noDates + " · " + undatedShown.size) }
                            val rot by androidx.compose.animation.core.animateFloatAsState(if (undatedOpen) 0f else -90f, label = "ud")
                            Icon(Icons.Rounded.KeyboardArrowDown, null, tint = c.graphite, modifier = Modifier.size(18.dp).rotate(rot))
                        }
                        androidx.compose.animation.AnimatedVisibility(undatedOpen, enter = androidx.compose.animation.expandVertically(androidx.compose.animation.core.tween(240)) + androidx.compose.animation.fadeIn(),
                            exit = androidx.compose.animation.shrinkVertically(androidx.compose.animation.core.tween(200)) + androidx.compose.animation.fadeOut()) {
                            Column {
                                fun born(t: Task) = runCatching { LocalDate.parse(t.createdAt.take(10)) }.getOrNull()
                                GroupedDeals(undatedShown, today, landed, onOpen, onDone, onLater, onTime, topicOf = topicOf,
                                    age = { t -> born(t)?.takeIf { it.isBefore(today) }?.let { s.sinceWord + " " + it.dayMonthShort() } },
                                    // Each undated matter is its own row (under its topic's link) — the number in the heading is the rows you see.
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

/**
 * «Напомнить»: a ready message to the one who owes it — by SMS when their phone is known, otherwise through «Поделиться»
 * (Telegram, MAX, mail). The app never sends anything by itself.
 */
fun remindAbout(ctx: android.content.Context, data: AppData, t: Task) {
    val s = ru.anjeib.margin.i18n.L.s
    val who = t.waitingFor ?: return
    val person = data.person(who)
    // «Получить макет от Димы» → «макет».
    val what = t.title.replace(Regex("^(?:Получить|Get)\\s+", RegexOption.IGNORE_CASE), "")
        .replace(Regex("\\s+(?:от|from)\\s+\\S+$", RegexOption.IGNORE_CASE), "").replaceFirstChar { it.lowercase() }
        // «про спецификацию API»: after «про» the first word goes to the accusative.
        .let { w -> w.substringBefore(' ').let { f -> f.accusative() } + w.removePrefix(w.substringBefore(' ')) }
    val text = s.remindText(who.substringBefore(' '), what, (t.dueDate ?: t.startDate)?.dayMonthShort())
    val phone = person?.phone?.filter { it.isDigit() || it == '+' }
    val i = if (!phone.isNullOrBlank()) android.content.Intent(android.content.Intent.ACTION_SENDTO, android.net.Uri.parse("smsto:$phone")).putExtra("sms_body", text)
        else android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, text), s.remindLink)
            // Not back into Margin itself.
            .putExtra(android.content.Intent.EXTRA_EXCLUDE_COMPONENTS, arrayOf(android.content.ComponentName(ctx, ru.anjeib.margin.ForwardActivity::class.java)))
    runCatching { ctx.startActivity(i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/** The week's card on Sunday: done · owed to you · you owe, and the topics that stood still (a tap opens one). */
@Composable
private fun WeekCard(w: ru.anjeib.margin.data.WeekReview, inbox: Int, onOpen: (String) -> Unit, onInbox: () -> Unit, onClose: () -> Unit) {
    val c = M.c
    val s = M.s
    Column(Modifier.fillMaxWidth().padding(top = 12.dp).clip(RoundedCornerShape(16.dp)).background(c.card).padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(s.weekTitle.uppercase(), style = Type.label.copy(color = c.link), modifier = Modifier.weight(1f))
            Box(Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onClose), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Close, M.s.dismiss, tint = c.graphite, modifier = Modifier.size(18.dp)) }
        }
        Text(s.weekLine(w.done, w.owedToYou, w.youOwe), style = Type.small.copy(color = c.ink, fontSize = 14.sp, fontFeatureSettings = "tnum"), modifier = Modifier.padding(end = 10.dp))
        if (w.still.isNotEmpty()) {
            Text(s.weekStill, style = Type.small.copy(color = c.graphite, fontSize = 13.sp), modifier = Modifier.padding(top = 8.dp))
            w.still.take(4).forEach { t -> PaperLink("↳ " + t.title) { onOpen(t.id) } }
        }
        // The week is a good moment to put away what came in unsorted.
        if (inbox > 0) PaperLink(s.inboxLine(inbox) + " ›", onClick = onInbox)
    }
}


/**
 * Two quiet lines over the day, each until it is not needed: the swipes (seen in the tour once — said here for the
 * first few days), and that the matters around are examples, with a way to clear them right here.
 */
@Composable
private fun TodayHints(plan: DayPlan, data: AppData, ui: android.content.SharedPreferences, onRemoveSamples: () -> Unit) {
    val c = M.c
    val s = M.s
    var swipeSeen by remember { mutableStateOf(ui.getInt("swipeHint", 0)) }
    androidx.compose.runtime.LaunchedEffect(Unit) { if (swipeSeen < 3) ui.edit().putInt("swipeHint", swipeSeen + 1).apply() }
    val samples = data.tasks.any { it.sample }
    if (samples) Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(s.samplesHere, style = Type.small.copy(color = c.graphite, fontSize = 13.5.sp), modifier = Modifier.weight(1f, fill = false))
        Text("  ·  ", style = Type.small.copy(color = c.graphite, fontSize = 13.5.sp))
        PaperLink(s.removeSamplesShort, onClick = onRemoveSamples)
    }
    if (swipeSeen < 3 && plan.deals.isNotEmpty()) Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(s.swipeHintLine, style = Type.small.copy(color = c.graphite, fontSize = 13.5.sp), modifier = Modifier.weight(1f))
        Box(Modifier.size(40.dp).clip(CircleShape).clickable { swipeSeen = 3; ui.edit().putInt("swipeHint", 3).apply() }, contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Close, s.dismiss, tint = c.graphite, modifier = Modifier.size(16.dp))
        }
    }
}


/**
 * The two doors from the day: «Календарь» and «Обзор», soft petrol rounds beside the weekday. For the first few days
 * a small word sits under each — an icon alone is easy to miss (prefs `ui/doorsSeen`).
 */
@Composable
private fun HomeDoors(ui: android.content.SharedPreferences, onCalendar: () -> Unit, onOverview: () -> Unit) {
    val c = M.c
    val s = M.s
    val seen = remember { ui.getInt("doorsSeen", 0) }
    androidx.compose.runtime.LaunchedEffect(Unit) { if (seen < 5) ui.edit().putInt("doorsSeen", seen + 1).apply() }
    @Composable fun door(label: String, onClick: () -> Unit, glyph: @Composable () -> Unit) =
        Column(Modifier.padding(start = 6.dp).clip(RoundedCornerShape(12.dp)).clickable(onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label }.padding(2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(42.dp).clip(CircleShape).background(c.linkSoft), contentAlignment = Alignment.Center) { glyph() }
            if (seen < 5) Text(label.lowercase(), style = Type.small.copy(color = c.link, fontSize = 11.sp), modifier = Modifier.padding(top = 2.dp))
        }
    Row(verticalAlignment = Alignment.Top) {
        door(s.calendarTitle, onCalendar) { CalendarGlyph(c.link, 20.dp) }
        door(s.overviewWord, onOverview) { OverviewGlyph(c.link, 20.dp) }
    }
}
