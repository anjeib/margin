package ru.anjeib.margin.ui

import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import ru.anjeib.margin.data.likelyTopic
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
import androidx.compose.ui.focus.focusRequester
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
    /** «Заметки» — the diary page (owner, 14 окт). */
    const val NOTES = "notes"
    const val DONE = "done"
    fun project(p: String) = "p:$p"
}

// ---------------- selection logic ----------------

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
    /** The day this page shows (the planner's tabs; null — today): a page keeps its own day while it is being turned. */
    pageDay: LocalDate? = vm.pageDay.collectAsState().value,
    /** «Темы» from the contents: the topics alone, under their own title (owner, 14 окт: «Обзор» is gone). */
    topicsOnly: Boolean = false,
    /** The find bar's name for this page («today», «topics»): its matches are counted and scrolled to; null — just marked. */
    findPage: String? = null,
) {
    val landed by vm.landed.collectAsState()
    val monthOpen by vm.monthOpen.collectAsState()
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
    var overviewKinds by androidx.compose.runtime.saveable.rememberSaveable(stateSaver = androidx.compose.runtime.saveable.listSaver<Set<String>, String>(save = { it.toList() }, restore = { it.toSet() })) { mutableStateOf(emptySet()) }
    var weekDay by rememberSaveable { mutableStateOf<String?>(null) }
    // Swipe left asks «на когда?» instead of quietly moving a day.
    var postponing by remember { mutableStateOf<String?>(null) }
    postponing?.let { id -> PostponeDialog(onDismiss = { postponing = null }, from = data.tasks.find { it.id == id }?.dueDate) { day -> vm.postponeTo(id, day); postponing = null } }
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
    var allPromises by rememberSaveable { mutableStateOf(false) }
    var undatedOpen by rememberSaveable { mutableStateOf(ui.getBoolean("undatedOpen", true)) }
    var overviewTab by rememberSaveable { mutableStateOf("all") }
    // «+ тема»: a topic by its name at once — a project to fill (owner, 12 окт).
    var askTopic by remember { mutableStateOf(false) }
    var tomorrowOpen by rememberSaveable { mutableStateOf(false) }
    var archOpen by rememberSaveable { mutableStateOf(false) }
    if (askTopic) TextDialog(s.newTopicTitle, "", s.newTopicHint, onDismiss = { askTopic = false }) { v -> askTopic = false; vm.newTopic(v)?.let(onOpen) }
    // «Поля и панели» (v8): the rule runs behind the whole list; rows sit on panels right of it, «когда» on the margin.
    val pad = PaddingValues(top = contentPadding.calculateTopPadding(), bottom = contentPadding.calculateBottomPadding())
    val pageLines = rememberPageLines()
    val plannerLook = planner
    Box(Modifier.fillMaxSize().ruledPage(pageLines).screenRule()) {
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val listScope = androidx.compose.runtime.rememberCoroutineScope()
    // The month unfolds at the very top: the page goes up to show it.
    androidx.compose.runtime.LaunchedEffect(monthOpen) { if (monthOpen) listState.animateScrollToItem(0) }
    val find = LocalFind.current
    val found = remember { mutableListOf<FindHit>() }
    // A row's key ends with its matter's id — a matter of its own or one of a topic's («q:…»).
    val findable = remember(tasks, data) { (data.tasks + tasks).distinctBy { it.id }.sortedByDescending { it.id.length } }
    ReportFound(findPage, found, listState, data, monthOpen, pageDay)
    // The month unfolding or folding sets the whole page anew (owner, 14 окт): rows sliding while the section heads jumped
    // ran over each other and over the fading month — now everything simply stands in its new place.
    androidx.compose.runtime.key(monthOpen) {
    LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = pad) {
      findable(found, find?.takeIf { findPage != null }, { k -> (k as? String)?.let { ks -> findable.firstOrNull { ks.endsWith(it.id) } }?.let { t -> find != null && findHit(find.words, t.findText()) } == true }) {
        when {
            view == Views.TODAY -> {
                // The calendar tab unfolds the month over the page (owner, 14 окт); a tap on a day turns the page to it.
                // Fable, 15 окт: the tools stay where they are; the month unfolds under them, not over them.
                // Persona test, 15 окт: the tools stay pinned at the top while the page scrolls (two of three couldn't find them).
                // Owner, 16 окт: the whole head — tools, the day, its summary, the double rule — stays put; only the list scrolls.
                // The month unfolds under it.
                val monthItem: () -> Unit = {
                    if (monthOpen) item("month") {
                        // No fade (owner, 14 окт): a fading month lay under the matters sliding up over it — it goes at once, they close up.
                        Box(Modifier.animateItem(fadeInSpec = null, placementSpec = null, fadeOutSpec = null)) { MonthPanel(data, vm, pageDay ?: today, events) { d -> vm.pageDay.value = d.takeIf { it != today } } }
                    }
                }
                // The planner's tabs turned the page to another day: that day alone — its name, its summary, its matters.
                val other = pageDay?.takeIf { it != today }
                if (other != null) {
                    val p = dayPlan(data, other, today, events)
                    pinnedHead("o-head", plannerLook, calendar = true) {
                        Margined(Modifier.padding(top = if (plannerLook) 0.dp else 8.dp), margin = { MarginDay(other.dayOfMonth, other.dayMonthShort().substringAfter(' ')) }) {
                            Column {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                                    BasicText(other.dowFull(), style = Type.pageTitle.copy(color = c.ink), maxLines = 1, modifier = Modifier.weight(1f),
                                        autoSize = TextAutoSize.StepBased(minFontSize = 24.sp, maxFontSize = if (planner) 52.sp else 40.sp, stepSize = 1.sp))
                                }
                                Voice(daySummary(p, other), Modifier.padding(top = 2.dp))
                                Voice(s.backToToday, Modifier.padding(top = 6.dp), link = true, onClick = { vm.pageDay.value = null })
                            }
                        }
                        if (planner) DoubleRule(Modifier.padding(top = 16.dp, bottom = 4.dp))
                    }
                    monthItem()
                    if (!p.quiet) {
                        item("o-sec") { SectionHead(dayTalk(other, today), rule = false, count = (p.deals.size + p.events.size).takeIf { it > 0 }) }
                        dayPanel(p, today, "o", landed, onOpen, { vm.completeWithUndo(it) }, { postponing = it }, { timeFor = it })
                    }
                    item("page-end-o") { PageEnd(pageLines) }
                    return@findable
                }
                val plan = dayPlan(data, today, today, events)
                val tomorrowPlan = dayPlan(data, today.plusDays(1), today, events)
                // Who owes what: late, within three days, or without a day — gathered here, still in their days below.
                val topicWaits = data.tasks.filter { it.topic && !it.done }.flatMap { t -> t.subtasks.filter { !it.done && it.due == null && it.from != null }.map { t.questionRow(it) } }
                val promises = (tasks + topicWaits).filter { !it.done && (it.waitingFor != null || it.promisedTo != null) }
                    .filter { t -> (t.dueDate ?: t.startDate).let { it == null || !it.isAfter(today.plusDays(3)) } }
                    .distinctBy { ru.anjeib.margin.data.slotRef(it.id)?.first ?: it.id }
                    .sortedWith(compareBy({ (it.dueDate ?: it.startDate) ?: LocalDate.MAX }, { it.title }))
                // Undated matters — simple ones and topics' — live at the end of today, newest first.
                val undated = (tasks.filter { !it.done && it.dueDate == null && it.startDate == null } + data.undatedTopicMatters())
                    .sortedByDescending { it.createdAt }
                pinnedHead("head", plannerLook, calendar = true) {
                    Margined(Modifier.padding(top = if (plannerLook) 0.dp else 8.dp), margin = { MarginDay(today.dayOfMonth, today.dayMonthShort().substringAfter(' ')) }) {
                        Column {
                          Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                            BasicText(today.dowFull(), style = Type.pageTitle.copy(color = c.ink), maxLines = 1, modifier = Modifier.weight(1f),
                                autoSize = TextAutoSize.StepBased(minFontSize = 24.sp, maxFontSize = if (planner) 52.sp else 40.sp, stepSize = 1.sp))
                            // Owner, 14 окт: the calendar right by the day too, under «MARGIN» — the month unfolds above.
                            // With the month open the tab on the right folds it; the icon by the day steps aside (owner, 14 окт).
                            if (!planner && !monthOpen) Box(Modifier.size(40.dp).clip(CircleShape).background(if (monthOpen) c.linkSoft else Color.Transparent)
                                .clickable(onClickLabel = s.calendarTitle) { vm.monthOpen.value = !monthOpen }
                                .semantics { contentDescription = s.calendarTitle }, contentAlignment = Alignment.Center) {
                                CalendarGlyph(if (monthOpen) c.link else c.ink.copy(alpha = 0.7f), 21.dp)
                            }
                          }
                            val away by vm.away.collectAsState()
                            // «2 просрочены» leads to them (ChatGPT's review, owner, 14 окт): the list turns to today's matters, the late ones light up.
                            val lateIds = plan.deals.filter { it.dueDate?.isBefore(today) == true }.map { it.id }
                            Voice(daySummary(plan, today, away), Modifier.padding(top = 2.dp), onClick = if (lateIds.isEmpty()) null else ({
                                // «Сегодня» stands after the head and the promises (their head and rows).
                                val i = (if (monthOpen) 2 else 1) + (if (promises.isNotEmpty()) 1 + (if (allPromises) promises.size else minOf(3, promises.size)) else 0)
                                listScope.launch { listState.animateScrollToItem(i) }
                                vm.lightUp(lateIds)
                            }))
                        }
                    }
                    if (planner) DoubleRule(Modifier.padding(top = 16.dp, bottom = 4.dp))
                }
                monthItem()
                if (promises.isNotEmpty()) {
                    val shownP = if (allPromises) promises else promises.take(3)
                    item("p-head") {
                        SectionHead(s.promisesTitle, top = 16.dp, rule = false,
                            action = if (promises.size > 3) (if (allPromises) s.collapse else s.more(promises.size - 3)) to { allPromises = !allPromises } else null)
                    }
                    shownP.forEachIndexed { i, t ->
                        item("p" + t.id) {
                            Box(Modifier.animateItem()) { PromisePaperRow(t, seatOf(i, shownP.size), today, data, onOpen = { onOpen(t.id) }, onDone = { vm.completeWithUndo(t.id) }) }
                        }
                    }
                }
                // Owner, 14 окт: what «Ждём и обещали» shows at the top is not repeated in the day below (the day's summary still counts it).
                fun base(id: String) = ru.anjeib.margin.data.slotRef(id)?.first ?: id
                val above = promises.map { base(it.id) }.toSet()
                val dayPlanShown = plan.copy(deals = plan.deals.filter { base(it.id) !in above })
                // Owner, 15 окт: no «всё на сегодня» by the section's name — a late matter is moved by itself (long press · «Отложить…»).
                val dayCount = dayPlanShown.deals.size + dayPlanShown.events.size
                // Owner, 14 окт: an empty day says «Свободный день» once — under its name; no empty «Сегодня» repeating it.
                val dayEmpty = dayPlanShown.deals.isEmpty() && dayPlanShown.events.isEmpty() && dayPlanShown.done.isEmpty()
                if (!dayEmpty) item("t-head") { SectionHead(s.today, count = dayCount.takeIf { it > 0 }, rule = promises.isNotEmpty()) }
                if (!dayEmpty) dayPanel(dayPlanShown, today, "t", landed, onOpen, { vm.completeWithUndo(it) }, { postponing = it }, { timeFor = it },
                    doneOpen = isOpen("s-done-today"), onDoneFold = { toggle("s-done-today") })
                if (undated.isNotEmpty()) {
                    item("u-head") {
                        SectionHead(s.noDates, count = undated.size, open = undatedOpen,
                            onFold = { undatedOpen = !undatedOpen; ui.edit().putBoolean("undatedOpen", undatedOpen).apply() })
                    }
                    fun born(t: Task) = runCatching { LocalDate.parse(t.createdAt.take(10)) }.getOrNull()
                    // Owner, 16 окт: a topic's matters together, under its name.
                    val blocks = withTopicHeads(undated) { t -> t.topicId?.let { it to (t.topicTitle ?: "") } }
                    val inBlock = blocks.filterIsInstance<TopicHead>().map { it.id }.toSet()
                    val pos = blockPositions(blocks) { (it as? Task)?.topicId }
                    var i = -1
                    if (undatedOpen) blocks.forEachIndexed { bi, b ->
                        if (b is TopicHead) { item("ug" + b.id) { Box(Modifier.animateItem()) { TopicBlockHead(b.title) { onOpen(b.id) } } }; return@forEachIndexed }
                        val t = b as Task
                        i++
                        val seat = seatOf(i, undated.size)
                        item("u" + t.id) {
                            val since = born(t)?.takeIf { it.isBefore(today) }?.let { s.sinceWord + " " + it.dayMonthShort() }
                            MatterRow(t, seat, today, (if (planner) since else null) to false, modifier = Modifier.animateItem(),
                                onToggle = { vm.completeWithUndo(t.id) }, onOpen = { onOpen(t.id) }, onTime = { timeFor = t.id }, onLater = { postponing = t.id },
                                faded = born(t)?.isBefore(today.minusDays(7)) == true, lit = t.id in landed, onOpenTopic = onOpen,
                                extra = since.takeIf { !planner }, showTopic = t.topicId !in inBlock, block = pos[bi])
                        }
                    }
                }
                // Owner, 16 окт: no «Записано сегодня» — what was written lives in its topic, its matter and «Заметки».
                // Sunday: the week in one quiet panel — gathered by code, gone until next Sunday.
                val wk = today.minusDays(today.dayOfWeek.value - 1L).toString()
                val weekShown = (today.dayOfWeek == java.time.DayOfWeek.SUNDAY || settings.devMode) && weekSeen != wk
                if (weekShown) {
                    item("w-head") { SectionHead(s.weekTitle, action = s.dismiss.lowercase() to { weekSeen = wk; ui.edit().putString("weekSeen", wk).apply() }) }
                    item("week") { Margined { WeekCard(data.weekReview(today), data.inboxNote()?.entries?.size ?: 0, onOpen, onOpenInbox) } }
                }
                // Owner, 14 окт: no examples on a clean page — not even phrases to try (the tour shows how to write).
                // Shared or saved without a place: one line while there is something to put away (the week's panel says it too).
                val unplaced = data.inboxNote()?.entries?.size ?: 0
                if (unplaced > 0 && !weekShown) item("inbox") { VoiceLink(s.inboxLine(unplaced), onOpenInbox) }
                if (data.queue.isNotEmpty()) item("queue") { VoiceLink(s.queuedLine(data.queue.size), onOpenInbox, quiet = true) }
                // One look ahead for the evening: tomorrow's count, a tap opens the calendar.
                val tomorrowParts = listOfNotNull(tomorrowPlan.deals.size.takeIf { it > 0 }?.let { s.summaryDeals(it) },
                    tomorrowPlan.events.size.takeIf { it > 0 }?.let { s.summaryMeetings(it) })
                // Owner, 14 окт: a tap opens tomorrow right here, under it — not the calendar.
                if (tomorrowParts.isNotEmpty()) {
                    item("tomorrow") {
                        VoiceLink(s.quickTomorrow + " — " + tomorrowParts.joinToString(" · "), { tomorrowOpen = !tomorrowOpen }, quiet = true, arrow = if (tomorrowOpen) "‹" else "›")
                    }
                    if (tomorrowOpen) dayPanel(tomorrowPlan.copy(doneCount = 0, done = emptyList()), today, "tm", landed, onOpen, { vm.completeWithUndo(it) }, { postponing = it }, { timeFor = it })
                }
                // Owner, 16 окт: the page's archive at its end — matters put away, folded; «Удалить» only here.
                archiveFold("arch", data.archived.sortedByDescending { it.doneAt }.map { t ->
                    ArchItem(t.id, t.title, t.doneAt, restore = { vm.restoreMatter(t.id) }, purge = { vm.purgeMatter(t.id) })
                }, archOpen) { archOpen = !archOpen }
            }

            view == Views.ALL -> {
                // «Обзор» — what there is: topics, matters, ideas, each in its own panel. Words as tabs say which; chips narrow by
                // who owes whom and by sphere.
                fun bare(t: Task) = t.thought && t.subtasks.isEmpty()
                fun sOk(t: Task) = monthSphere == null || (if (monthSphere == "work") t.sphere == "work" else t.sphere != "work")
                // Owner, 16 окт: the head stays put; only the list scrolls.
                pinnedHead("o-head", plannerLook) {
                    Margined(Modifier.padding(top = if (plannerLook) 0.dp else 8.dp)) {
                        Column {
                            Row(verticalAlignment = Alignment.Top) {
                                Text(if (topicsOnly) s.topicsPage else s.overviewWord, style = Type.pageTitle.copy(color = c.ink), modifier = Modifier.weight(1f))
                                if (!plannerLook) PageToolIcons()
                            }
                            if (topicsOnly) Voice(s.topicsSub, Modifier.padding(top = 2.dp))
                            else TextTabs(listOf("all" to s.allTab, "topics" to s.topicsBadge, "deals" to s.dealsBadge, "ideas" to s.thoughts), overviewTab,
                                { overviewTab = it; if (it != "deals" && it != "all") monthOwe = null }, Modifier.padding(top = 10.dp))
                            // Owner, 14 окт: the filters as drawn marks — ждём ←, обещали →, работа, личное.
                            Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                fun owe(k: String) { monthOwe = if (monthOwe == k) null else k; if (monthOwe != null) overviewTab = "deals" }
                                if (!topicsOnly) {
                                    IconChip(s.a11yWaiting.replaceFirstChar { it.uppercase() }, monthOwe == "wait", { owe("wait") }) { HandGlyph(it, 23.dp, incoming = true) }
                                    IconChip(s.a11yPromised.replaceFirstChar { it.uppercase() }, monthOwe == "promise", { owe("promise") }) { HandGlyph(it, 23.dp) }
                                    Spacer(Modifier.width(6.dp))
                                }
                                // Owner, 14 окт: the spheres as words — the chosen one petrol; chosen again — all of them.
                                SphereIcons(monthSphere, { monthSphere = it }, clearable = true)
                            }
                        }
                    }
                    if (planner) DoubleRule(Modifier.padding(top = 18.dp, bottom = 6.dp))
                }
                val data2 = if (monthSphere == null) data else data.copy(tasks = data.tasks.filter { sOk(it) })
                val owe = if (topicsOnly) null else monthOwe
                val tab = if (topicsOnly) "topics" else overviewTab
                fun wants(k: String) = tab == "all" || tab == k
                fun born(t: Task) = runCatching { LocalDate.parse(t.createdAt.take(10)) }.getOrNull()
                val topics = if (owe == null && wants("topics")) data2.tasks.filter { it.topic && !it.done && !bare(it) }.sortedByDescending { it.lastActivity() ?: born(it) } else emptyList()
                val ideas = if (owe == null && wants("ideas")) data2.tasks.filter { bare(it) && !it.done }.sortedByDescending { it.lastActivity() ?: born(it) } else emptyList()
                // Every open matter, with a day or without: the dated ones by their day (late first), then the undated, newest first.
                val deals = if (wants("deals")) (data2.listTasks().filter { !it.done && !it.topic || it.topicId != null } + data2.undatedTopicMatters())
                    .filter { !it.done }.distinctBy { (slotRef(it.id)?.first ?: it.id) }
                    .filter { when (owe) { "wait" -> it.waitingFor != null; "promise" -> it.promisedTo != null; else -> true } }
                    .let { l -> l.filter { it.anchor != null }.sortedWith(compareBy({ it.anchor }, { it.time ?: "99" })) + l.filter { it.anchor == null }.sortedByDescending { it.createdAt } }
                    else emptyList()
                if (topics.isEmpty() && ideas.isEmpty() && deals.isEmpty()) item("o-none") {
                    // Owner, 14 окт: one plain line, no examples — the field below starts a topic.
                    Margined(Modifier.padding(top = 18.dp)) { Voice(if (topicsOnly) s.topicsEmpty else s.emptyAll) }
                }
                if (topics.isNotEmpty() || (owe == null && tab == "topics" && !topicsOnly)) {
                    // Owner, 14 окт: no «+ тема» here — a new topic is written in the field below («Новая тема…»).
                    // On the page «Темы» its one list needs no second «Темы» over it.
                    if (!topicsOnly || !plannerLook) item("ot-head") { SectionHead(s.topicsBadge, count = topics.size.takeIf { it > 0 }) }
                    topics.forEachIndexed { i, t ->
                        item("ot" + t.id) { Box(Modifier.animateItem()) { TopicPaperRow(t, seatOf(i, topics.size), onOpen = { onOpen(t.id) }, onArchive = { vm.archiveTopic(t.id) }) } }
                    }
                }
                if (ideas.isNotEmpty()) {
                    item("oi-head") { SectionHead(s.thoughts, count = ideas.size) }
                    ideas.forEachIndexed { i, t ->
                        item("oi" + t.id) {
                            val grown = data.tasks.count { it.id != t.id && it.noteId == t.id }
                            PaperRow(seatOf(i, ideas.size), margin = (t.lastActivity() ?: born(t))?.let { dayWord(it, today) }, mark = { IdeaMark(26.dp) },
                                modifier = Modifier.animateItem(), onClick = { onOpen(t.id) }) {
                                RowTitle(t.title, maxLines = 2)
                                val sub = listOfNotNull(t.entries.size.takeIf { it > 0 }?.let { s.nEntries(it) }, grown.takeIf { it > 0 }?.let { "↳ " + s.summaryDeals(it) })
                                if (sub.isNotEmpty()) RowSub(sub.joinToString("  ·  "))
                            }
                        }
                    }
                }
                if (deals.isNotEmpty()) {
                    item("od-head") { SectionHead(when (owe) { "wait" -> s.a11yWaiting; "promise" -> s.a11yPromised; else -> s.dealsBadge }, count = deals.size) }
                    deals.forEachIndexed { i, t ->
                        item("od" + t.id) {
                            MatterRow(t, seatOf(i, deals.size), today, whenNote(t, today, underDay = false), modifier = Modifier.animateItem(),
                                onToggle = { vm.completeWithUndo(t.id) }, onOpen = { onOpen(t.id) }, onTime = { timeFor = t.id }, onLater = { postponing = t.id },
                                lit = t.id in landed, onOpenTopic = onOpen, extra = if (t.anchor == null) born(t)?.takeIf { it.isBefore(today) }?.let { s.sinceWord + " " + it.dayMonthShort() } else null)
                        }
                    }
                }
                // Owner, 16 окт: archived topics at the end of «Темы», folded; a tap opens one («Вернуть», «Удалить» are there).
                if (topicsOnly) archiveFold("arch", data.tasks.filter { it.topic && it.done && !it.thought && sOk(it) }.sortedByDescending { it.doneAt }.map { t ->
                    ArchItem(t.id, t.title, t.doneAt, open = { onOpen(t.id) }, restore = { vm.reviveTopic(t.id) }, purge = { vm.removeMatter(t.id) })
                }, archOpen) { archOpen = !archOpen }
            }
        }
        item("page-end") { PageEnd(pageLines) }
      }
    }
    }
    }
}

/** A topic in a panel: its feed mark, the title, how much is open and written; settled — «в архив». */
/** A topic's own day or window («Когда», owner 16 окт) as the margin says it: «20 окт», «9–11 окт»; null — none set. */
fun Task.topicWhen(): String? {
    val d = dueDate; val st = startDate
    return when {
        d != null && st != null && st.isBefore(d) -> spanWord(st, d)
        else -> (d ?: st)?.let { dayWord(it) }
    }
}

@Composable
fun TopicPaperRow(t: Task, seat: Seat, onOpen: () -> Unit, onArchive: (() -> Unit)? = null, margin: String? = t.topicWhen() ?: (t.lastActivity() ?: runCatching { LocalDate.parse(t.createdAt.take(10)) }.getOrNull())?.let { dayWord(it) },
                  /** In search: the matter of the topic the words were found in. */
                  found: String? = null, trailing: (@Composable () -> Unit)? = null) {
    val c = M.c
    val s = M.s
    val open = t.subtasks.count { !it.done }
    val waits = t.subtasks.count { !it.done && it.from != null }
    PaperRow(seat, margin = margin, mark = { TopicGlyph(c.graphite, 20.dp) }, onClick = onOpen,
        trailing = trailing ?: if (t.noNextStep && onArchive != null) ({ PaperLink(s.toArchive, onClick = onArchive) }) else null) {
        RowTitle(t.title, maxLines = 2)
        val sub = listOfNotNull(
            if (t.noNextStep) s.allDoneShort else open.takeIf { it > 0 }?.let { s.summaryDeals(it) },
            waits.takeIf { it > 0 }?.let { s.a11yWaiting + " " + it },
            t.entries.size.takeIf { it > 0 }?.let { s.nEntries(it) },
        )
        if (sub.isNotEmpty()) RowSub(sub.joinToString("  ·  "))
        found?.let { RowSub("↳ $it", link = true) }
    }
}

/** A horizontal scroll for a row of chips that may not fit a narrow phone. */
@Composable
private fun Modifier.horizontalScrollCompat(): Modifier = this.horizontalScroll(androidx.compose.foundation.rememberScrollState())

/** «Отложить»: tomorrow, in a week, in a month — or a day from the calendar. */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun PostponeDialog(onDismiss: () -> Unit, /** The matter's own day, when it is still ahead: «через неделю» counts from it (persona test, 15 окт). */ from: LocalDate? = null, onPick: (LocalDate) -> Unit) {
    val c = M.c
    val s = M.s
    val today = LocalDate.now()
    val base = from?.takeIf { it.isAfter(today) } ?: today
    var custom by remember { mutableStateOf(false) }
    if (custom) {
        // Margin's own grid, not the Material picker (persona test, 15 окт).
        DayDialog(s.postponeTitle, from ?: today.plusDays(1), onDismiss = onDismiss, onPick = onPick)
        return
    }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss, containerColor = c.card,
        title = { Text(s.postponeTitle, style = Type.cardTitle.copy(color = c.ink)) },
        text = {
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill(s.quickTomorrow, onClick = { onPick(today.plusDays(1)) })
                Pill(s.inAWeek, onClick = { onPick(base.plusWeeks(1)) })
                Pill(s.pickDay, accent = true, onClick = { custom = true })
            }
        },
        confirmButton = {},
        dismissButton = { androidx.compose.material3.TextButton(onDismiss) { Text(s.cancel, color = c.graphite) } },
    )
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

/** «На завтра» in one move — kept for TalkBack's named actions; the swipe itself asks «Отложить». */
val LocalTomorrow = androidx.compose.runtime.staticCompositionLocalOf<((String) -> Unit)?> { null }

/** What a long press on a matter offers beyond the swipes: the topic it belongs in, and removing it. */
class RowActions(val toTopic: (String) -> Unit, val remove: (String) -> Unit)
val LocalRowActions = androidx.compose.runtime.staticCompositionLocalOf<RowActions?> { null }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SwipeTask(t: Task, onDone: () -> Unit, onLater: () -> Unit, modifier: Modifier, radius: Int = 18,
                       shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(radius.dp), content: @Composable () -> Unit) {
    val c = M.c
    val s = M.s
    val state = rememberSwipeToDismissBoxState(confirmValueChange = { v ->
        when (v) {
            SwipeToDismissBoxValue.StartToEnd -> onDone()
            // «Отложить» — a short choice (tomorrow, a week, a day) instead of guessing tomorrow (owner's call, 10 окт).
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
                Modifier.fillMaxSize().clip(shape)
                    .background(if (dir == SwipeToDismissBoxValue.Settled) (LocalRowGround.current ?: c.card) else if (toEnd) c.link else c.accent)
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

/** [day] lies inside the window, before its last day: start ≤ day < due (the last day has the matter anyway). */
fun Task.openWindow(day: LocalDate): Boolean {
    val s = startDate ?: return false; val d = dueDate ?: return false
    return s.isBefore(d) && !day.isBefore(s) && day.isBefore(d)
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
    // A window («9–11 окт», «до конца месяца») stands on every day of it until done — «Сегодня» and the calendar alike
    // (owner, 13 окт); days already gone are not filled back.
    val deals = data.listTasks().filter { !it.done && (dayOf(it.dueDate, it.startDate, today) == date || (!date.isBefore(today) && it.openWindow(date))) }
        // A past day's leftovers first; today's passed hours keep their place in the day's order.
        .sortedWith(compareBy({ it.dueDate?.isBefore(today) != true }, { it.time ?: "99" }, { it.title }))
    val steps = emptyList<TopicStep>()
    // An event shows on every day it covers (a three-day trip, a holiday); all-day ends are exclusive midnights.
    val evs = events.filter { e ->
        val first = e.start.toLocalDate()
        val last = if (e.allDay || e.end.toLocalTime() == java.time.LocalTime.MIDNIGHT) e.end.toLocalDate().minusDays(1) else e.end.toLocalDate()
        !date.isBefore(first) && !date.isAfter(maxOf(first, last))
    }
    // What got done on a day stays on it, struck through — today too (v8: the page is an honest diary of the day).
    val doneList = if (!date.isAfter(today)) data.tasks.filter { it.doneAt == date.toString() && !it.topic && !it.dropped } +
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

/** The week's panel on Sunday: done · owed to you · you owe, and the topics that stood still (a tap opens one). */
@Composable
private fun WeekCard(w: ru.anjeib.margin.data.WeekReview, inbox: Int, onOpen: (String) -> Unit, onInbox: () -> Unit) {
    val c = M.c
    val s = M.s
    Panel {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(s.weekLine(w.done, w.owedToYou, w.youOwe), style = Type.body.copy(color = c.ink, fontSize = 15.sp, fontFeatureSettings = "tnum"))
            if (w.still.isNotEmpty()) {
                Text(s.weekStill, style = Type.small.copy(color = c.graphite, fontSize = 13.5.sp), modifier = Modifier.padding(top = 8.dp))
                w.still.take(4).forEach { t -> PaperLink("# " + t.title) { onOpen(t.id) } }
            }
            // The week is a good moment to put away what came in unsorted.
            if (inbox > 0) PaperLink(s.inboxLine(inbox) + "  ›", onClick = onInbox)
        }
    }
}

/**
 * One quiet line under the day's name while the samples are there, the way out in the words themselves:
 * «Ниже — примеры · убрать». The swipes are told in the tour, not here (owner, 11 окт: four lines of italics before the first matter).
 */
/**
 * «В тему…»: where a matter goes — the topic it most likely belongs to first (by its words and people), then the topics
 * written in lately. A tap moves it there; «Отменить» brings it back.
 */
@Composable
fun TopicPickDialog(data: AppData, ref: String, onDismiss: () -> Unit, onCreate: ((String) -> Unit)? = null, onPick: (String) -> Unit) {
    val c = M.c
    val s = M.s
    // ChatGPT / persona test, 15 окт: with no topics yet the dialog was a dead end — a topic can be named right here.
    var naming by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    val matter = data.listTasks().find { it.id == ref } ?: data.undatedTopicMatters().find { it.id == ref } ?: data.tasks.find { it.id == ref }
    val own = matter?.topicId
    val likely = remember(ref) { matter?.let { data.likelyTopic(it.title, it.people + listOfNotNull(it.waitingFor, it.promisedTo), except = setOfNotNull(own)) } }
    val topics = remember(ref) {
        (listOfNotNull(likely) + data.tasks.filter { it.topic && !it.done && !(it.thought && it.subtasks.isEmpty()) && it.id != own && it.id != likely?.id }
            .sortedByDescending { it.lastActivity() ?: LocalDate.MIN }).take(9)
    }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss, containerColor = c.card,
        title = { Text(s.toTopicPick, style = Type.cardTitle.copy(color = c.ink)) },
        text = {
            Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                if (topics.isEmpty() && !naming) Text(s.noTopicsYet, style = Type.body.copy(color = c.graphite), modifier = Modifier.padding(bottom = 6.dp))
                if (onCreate != null) {
                    if (!naming) Text(s.newTopicRow, style = Type.body.copy(color = c.link, fontSize = 15.5.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium),
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { naming = true }.padding(vertical = 11.dp, horizontal = 4.dp))
                    else Row(Modifier.fillMaxWidth().padding(vertical = 6.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        val focus = remember { androidx.compose.ui.focus.FocusRequester() }
                        androidx.compose.runtime.LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
                        Box(Modifier.weight(1f).padding(end = 8.dp)) {
                            if (newName.isEmpty()) Text(s.newTopicNameHint, style = Type.body.copy(color = c.graphite, fontSize = 15.5.sp))
                            androidx.compose.foundation.text.BasicTextField(newName, { newName = it }, singleLine = true,
                                textStyle = Type.body.copy(color = c.ink, fontSize = 15.5.sp), cursorBrush = androidx.compose.ui.graphics.SolidColor(c.link),
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { newName.trim().takeIf { it.isNotBlank() }?.let(onCreate) }),
                                modifier = Modifier.fillMaxWidth().focusRequester(focus))
                            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(1.dp).background(c.hairline))
                        }
                        Box(Modifier.size(34.dp).clip(CircleShape).background(if (newName.isNotBlank()) c.sea else c.chip)
                            .clickable(enabled = newName.isNotBlank()) { onCreate(newName.trim()) }, contentAlignment = Alignment.Center) { TickGlyph(if (newName.isNotBlank()) M.c.onAccent else c.graphite, 16.dp) }
                    }
                }
                topics.forEach { t ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onPick(t.id) }.padding(vertical = 11.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        TopicGlyph(c.link, 16.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(t.title, style = Type.body.copy(color = c.ink, fontSize = 15.5.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                            if (t.id == likely?.id) Text(s.likelyWord, style = Type.small.copy(color = c.link, fontSize = 12.5.sp))
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { androidx.compose.material3.TextButton(onDismiss) { Text(s.cancel, color = c.graphite) } },
    )
}

