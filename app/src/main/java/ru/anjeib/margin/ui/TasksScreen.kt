package ru.anjeib.margin.ui

import ru.anjeib.margin.data.topicOn

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
    /** A note of a matter or a topic in the diary's feed: its own card. */
    onOpenPiece: (String) -> Unit = {},
    onPerson: (String) -> Unit = {},
) {
    val framed = LocalFramed.current
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
    // Owner, 16 окт: topics in the day are folded rows; these are unfolded (for as long as the screen lives).
    val openTopics = rememberSaveable(saver = setSaver) { mutableStateOf(emptySet<String>()) }
    fun foldTopic(id: String) { openTopics.value = if (id in openTopics.value) openTopics.value - id else openTopics.value + id }
    if (askTopic) TextDialog(s.newTopicTitle, "", s.newTopicHint, onDismiss = { askTopic = false }) { v -> askTopic = false; vm.newTopic(v)?.let(onOpen) }
    // «Поля и панели» (v8): the rule runs behind the whole list; rows sit on panels right of it, «когда» on the margin.
    val pad = PaddingValues(top = contentPadding.calculateTopPadding(), bottom = contentPadding.calculateBottomPadding())
    val pageLines = rememberPageLines()
    val plannerLook = planner
    Box(Modifier.fillMaxSize().ruledPage(pageLines).screenRule()) {
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val listScope = androidx.compose.runtime.rememberCoroutineScope()
    // The month unfolds at the very top: the page goes up to show it.
    // One screen (owner, 17 окт): the diary is a feed of every day — its matters, the notes of matters and topics written on it,
    // and a topic on the days something happens to it (made, begins, ends, its deadline). The head (App) holds the week.
    val feed = framed && view == Views.TODAY
    val kind by vm.kindFilter.collectAsState()
    // A topic chosen (no topic page, owner 17 окт): the feed is that topic's — its matters, its notes, its days; unfolded.
    val topicF by vm.topicFilter.collectAsState()
    val feedDays = if (feed) remember(data, events, today, kind, topicF) {
        val data = topicF?.let { id -> data.copy(tasks = data.tasks.filter { it.id == id }) } ?: data
        fun dayOf(x: String?) = x?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() }
        val notesByDay = data.linkedNotes().groupBy { dayOf(it.at) ?: today }
        val topics = data.tasks.filter { it.topic && !it.thought && !it.done }
        (-120L..120L).map { today.plusDays(it) }.mapNotNull { d ->
            // Inside a topic its matters stand as plain rows — the topic's own row over them each day would only repeat the head.
            val p = dayPlan(data, d, today, events).let { p0 -> if (topicF == null) p0 else
                p0.copy(deals = p0.deals.map { it.copy(topicId = null, topicTitle = null) }, done = p0.done.map { it.copy(topicId = null, topicTitle = null) }) }
            val notes = notesByDay[d].orEmpty()
            val inPlan = p.deals.mapNotNull { it.topicId }.toSet()
            val inNotes = notes.map { it.open.removePrefix("q:").substringBefore(':') }.toSet()
            val tops = topics.mapNotNull { t ->
                val word = when {
                    t.dueDate == d -> if (t.startDate != null) s.topicEndsWord else s.topicDueWord
                    t.startDate == d -> s.topicStartsWord
                    dayOf(t.createdAt) == d -> s.topicBornWord
                    else -> null
                }
                when {
                    // Only topics: every topic that has something on the day.
                    kind == "topics" -> if (word != null || t.id in inPlan || t.id in inNotes) t to word else null
                    // Everything: a topic stands as its own row only where it isn't there already by its matters or notes.
                    word != null && t.id !in inPlan && t.id !in inNotes -> t to word
                    else -> null
                }
            }
            val has = when (kind) {
                "deals" -> p.deals.isNotEmpty() || p.done.isNotEmpty()
                "topics" -> tops.isNotEmpty()
                "notes" -> notes.isNotEmpty()
                else -> p.deals.isNotEmpty() || p.events.isNotEmpty() || p.done.isNotEmpty() || notes.isNotEmpty() || tops.isNotEmpty()
            }
            if (d == today || has) FeedDay(d, p, notes, tops) else null
        }
    } else emptyList()
    val feedStarts = remember { HashMap<LocalDate, Int>() }
    val feedJump by vm.feedJump.collectAsState()
    FeedFollow(feed, listState, feedStarts, pageDay ?: today, feedJump) { vm.feedDay.value = it }
    androidx.compose.runtime.LaunchedEffect(monthOpen) { if (monthOpen && !feed) listState.animateScrollToItem(0) }
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
                // Owner, 16 окт («максимально эффективный core»): one page per day and nothing but the day — its matters, then what has
                // no day (on today and the days ahead, until it is done). No «Ждём и обещали», «Сегодня», «Дальше» or «Без даты»
                // sections; a swipe across the page turns the day (App), the month unfolds under the pinned head.
                val day = pageDay ?: today
                fun androidx.compose.foundation.lazy.LazyListScope.undatedRows(undated: List<Task>) {
                        fun born(t: Task) = runCatching { LocalDate.parse(t.createdAt.take(10)) }.getOrNull()
                        // A topic is one folded row here too; unfolded, its matters stand under it on the block's tint.
                        val blocks = withTopicHeads(undated, 2) { t -> t.topicId?.let { it to (t.topicTitle ?: "") } }
                        val inBlock = blocks.filterIsInstance<TopicHead>().map { it.id }.toSet()
                        val pos = blockPositions(blocks) { (it as? Task)?.topicId }
                        var i = -1
                        blocks.forEachIndexed { bi, b ->
                            if (b is TopicHead) {
                                val rows = undated.filter { it.topicId == b.id }
                                val since = rows.mapNotNull { born(it) }.minOrNull()?.takeIf { it.isBefore(today) }?.let { s.sinceWord + " " + it.dayMonthShort() }
                                item("ug" + b.id) { Box(Modifier.animateItem()) {
                                    TopicFoldRow(b.title, rows, today, since to false, ("u|" + b.id) !in openTopics.value || b.id == topicF, { foldTopic("u|" + b.id) }, { onOpen(b.id) }, lit = b.id in landed, topicId = b.id)
                                } }
                                return@forEachIndexed
                            }
                            val t = b as Task
                            if (t.topicId != null && t.topicId in inBlock && ("u|" + t.topicId) in openTopics.value && t.topicId != topicF) return@forEachIndexed
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
                val monthItem: () -> Unit = {
                    if (monthOpen) item("month") {
                        // No fade (owner, 14 окт): a fading month lay under the matters sliding up over it — it goes at once, they close up.
                        Box(Modifier.animateItem(fadeInSpec = null, placementSpec = null, fadeOutSpec = null)) { MonthPanel(data, vm, day, events) { d -> vm.pageDay.value = d.takeIf { it != today } } }
                    }
                }
                val plan = dayPlan(data, day, today, events)
                // What has no day stands in every day from today on, at the day's end, newest first — gone once done.
                val undated = if (day.isBefore(today) && !feed) emptyList() else if (feed && topicF != null) data.undatedTopicMatters().filter { it.topicId == topicF }.map { it.copy(topicId = null, topicTitle = null) }
                    else (tasks.filter { !it.done && it.dueDate == null && it.startDate == null } + data.undatedTopicMatters())
                    .sortedByDescending { it.createdAt }
                // In the diary's frame the head is the frame's (Frame.kt).
                if (!framed) pinnedHead("head", plannerLook, calendar = true) {
                    val away by vm.away.collectAsState()
                    // «2 просрочены» leads to them (ChatGPT's review, owner, 14 окт): the late ones light up.
                    val lateIds = plan.deals.filter { it.dueDate?.isBefore(today) == true }.map { it.id }
                    val summary: @Composable () -> Unit = {
                        Voice(daySummary(plan, today, away.takeIf { day == today }), Modifier.padding(top = 2.dp), onClick = if (lateIds.isEmpty()) null else ({
                            listScope.launch { listState.animateScrollToItem(if (monthOpen) 2 else 1) }
                            vm.lightUp(lateIds)
                        }))
                        if (day != today) Voice(s.backToToday, Modifier.padding(top = 4.dp), link = true, onClick = { vm.pageDay.value = null })
                    }
                    Margined(Modifier.padding(top = if (plannerLook) 0.dp else 8.dp), margin = { MarginDay(day.dayOfMonth, day.dayMonthShort().substringAfter(' ')) }) {
                        Column {
                            BasicText(day.dowFull(), style = Type.pageTitle.copy(color = c.ink), maxLines = 1, modifier = Modifier.fillMaxWidth(),
                                autoSize = TextAutoSize.StepBased(minFontSize = 24.sp, maxFontSize = if (planner) 52.sp else 40.sp, stepSize = 1.sp))
                            summary()
                        }
                    }
                    if (planner) DoubleRule(Modifier.padding(top = 16.dp, bottom = 4.dp))
                }
                if (feed) {
                    // Every day at once; a day picked in the head's week or month comes up to the top.
                    feedStarts.clear()
                    val cs = CountingScope(this)
                    feedDays.forEach { f ->
                        val d = f.day
                        feedStarts[d] = cs.n
                        cs.item("fd$d") { FeedDayHead(d, today) }
                        // Everything: the day's notes go in among its matters by their hour.
                        // A note of a topic (or of a topic's matter) belongs to that topic's row on the day.
                        val noteRows: List<DayExtra> = if (kind == null) f.notes.map { n ->
                            val topic = when {
                                n.open.startsWith("q:") -> n.open.removePrefix("q:").substringBefore(':')
                                n.link.startsWith("# ") -> n.open
                                else -> null
                            }
                            DayExtra(n.at.drop(11).take(5), "l" + n.key, topic, n.link.substringAfterLast("# ").takeIf { topic != null }) { block ->
                                item("l" + n.key) { LinkedNoteRow(n, data, vm, 0, 1, block, onOpen, onOpenPiece, onPerson) }
                            }
                        } else emptyList()
                        // Today a topic's row is open, other days folded — a tap turns it the other way, for that day.
                        val dayTopics = (f.plan.deals.mapNotNull { it.topicId } + noteRows.mapNotNull { it.topicId }).toSet()
                        val openHere = dayTopics.filter { (d == today) != ("$d|$it" in openTopics.value) }.toSet() + listOfNotNull(topicF)
                        if (kind == null || kind == "deals") cs.dayPanel(f.plan, today, "f$d", landed, onOpen, { vm.completeWithUndo(it) }, { postponing = it }, { timeFor = it },
                            doneOpen = isOpen("s-done-$d"), onDoneFold = { toggle("s-done-$d") }, openTopics = openHere, onTopicFold = { foldTopic("$d|$it") }, extras = noteRows)
                        if (kind == null || kind == "topics") f.topics.forEachIndexed { i, (t, word) ->
                            cs.item("ft$d" + t.id) { TopicPaperRow(t, seatOf(i, f.topics.size), onOpen = { onOpen(t.id) }, margin = word ?: "") }
                        }
                        if (kind == "notes") cs.linkedNotesOfDay(d, f.notes, data, vm, onOpen, onOpenPiece, onPerson)
                        if (d == today) {
                            if ((kind == null || kind == "deals") && undated.isNotEmpty()) cs.undatedRows(undated)
                            if (kind == null) {
                                // Shared or saved without a place: one line while there is something to put away.
                                val unplaced = data.inboxNote()?.entries?.size ?: 0
                                if (unplaced > 0) cs.item("inbox") { VoiceLink(s.inboxLine(unplaced), onOpenInbox) }
                                if (data.queue.isNotEmpty()) cs.item("queue") { VoiceLink(s.queuedLine(data.queue.size), onOpenInbox, quiet = true) }
                            }
                        }
                    }
                    // Room after the last day, so even it can come up under the month.
                    item("feed-end") { Box(Modifier.fillParentMaxHeight()) }
                } else {
                monthItem()
                val dayEmpty = plan.deals.isEmpty() && plan.events.isEmpty() && plan.done.isEmpty()
                if (!dayEmpty) dayPanel(plan, today, "t", landed, onOpen, { vm.completeWithUndo(it) }, { postponing = it }, { timeFor = it },
                    doneOpen = isOpen("s-done-today"), onDoneFold = { toggle("s-done-today") }, openTopics = openTopics.value, onTopicFold = ::foldTopic)
                if (undated.isNotEmpty()) undatedRows(undated)
                if (day == today) {
                    // Sunday: the week in one quiet panel — gathered by code, gone until next Sunday.
                    val wk = today.minusDays(today.dayOfWeek.value - 1L).toString()
                    val weekShown = (today.dayOfWeek == java.time.DayOfWeek.SUNDAY || settings.devMode) && weekSeen != wk
                    if (weekShown) {
                        item("w-head") { SectionHead(s.weekTitle, action = s.dismiss.lowercase() to { weekSeen = wk; ui.edit().putString("weekSeen", wk).apply() }) }
                        item("week") { Margined { WeekCard(data.weekReview(today), data.inboxNote()?.entries?.size ?: 0, onOpen, onOpenInbox) } }
                    }
                    // Shared or saved without a place: one line while there is something to put away.
                    val unplaced = data.inboxNote()?.entries?.size ?: 0
                    if (unplaced > 0 && !weekShown) item("inbox") { VoiceLink(s.inboxLine(unplaced), onOpenInbox) }
                    if (data.queue.isNotEmpty()) item("queue") { VoiceLink(s.queuedLine(data.queue.size), onOpenInbox, quiet = true) }
                    // Owner, 16 окт: the page's archive at its end — matters put away, folded; «Удалить» only here.
                    archiveFold("arch", data.archived.sortedByDescending { it.doneAt }.map { t ->
                        ArchItem(t.id, t.title, t.doneAt, restore = { vm.restoreMatter(t.id) }, purge = { vm.purgeMatter(t.id) })
                    }, archOpen) { archOpen = !archOpen }
                }
                }
            }

            view == Views.ALL -> {
                // «Обзор» — what there is: topics, matters, ideas, each in its own panel. Words as tabs say which; chips narrow by
                // who owes whom and by sphere.
                fun bare(t: Task) = t.thought && t.subtasks.isEmpty()
                fun sOk(t: Task) = monthSphere == null || (if (monthSphere == "work") t.sphere == "work" else t.sphere != "work")
                // Owner, 16 окт: the head stays put; only the list scrolls.
                if (!(framed && topicsOnly)) pinnedHead("o-head", plannerLook) {
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
                // On «Темы» the page is a day (owner, 17 окт): the topics that stand on it — from their start or birth, to their period's end or being done.
                val topicDay = pageDay ?: today
                val topics = if (owe == null && wants("topics")) data2.tasks.filter { it.topic && !bare(it) && (if (topicsOnly) it.topicOn(topicDay) else !it.done) }
                    .sortedByDescending { it.lastActivity() ?: born(it) } else emptyList()
                if (topicsOnly && monthOpen) item("month") {
                    Box(Modifier.animateItem(fadeInSpec = null, placementSpec = null, fadeOutSpec = null)) { MonthPanel(data, vm, topicDay, events) { d -> vm.pageDay.value = d.takeIf { it != today } } }
                }
                val ideas = if (owe == null && wants("ideas")) data2.tasks.filter { bare(it) && !it.done }.sortedByDescending { it.lastActivity() ?: born(it) } else emptyList()
                // Every open matter, with a day or without: the dated ones by their day (late first), then the undated, newest first.
                val deals = if (wants("deals")) (data2.listTasks().filter { !it.done && !it.topic || it.topicId != null } + data2.undatedTopicMatters())
                    .filter { !it.done }.distinctBy { (slotRef(it.id)?.first ?: it.id) }
                    .filter { when (owe) { "wait" -> it.waitingFor != null; "promise" -> it.promisedTo != null; else -> true } }
                    .let { l -> l.filter { it.anchor != null }.sortedWith(compareBy({ it.anchor }, { it.time ?: "99" })) + l.filter { it.anchor == null }.sortedByDescending { it.createdAt } }
                    else emptyList()
                if (topics.isEmpty() && ideas.isEmpty() && deals.isEmpty()) item("o-none") {
                    // Owner, 14 окт: one plain line, no examples — the field below starts a topic.
                    Margined(Modifier.padding(top = 18.dp)) { Voice(if (topicsOnly) (if (topicDay != today && data.tasks.any { it.topic }) s.topicsNoneThisDay else s.topicsEmpty) else s.emptyAll) }
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
                if (topicsOnly && topicDay == today) archiveFold("arch", data.tasks.filter { it.topic && it.done && !it.thought && sOk(it) }.sortedByDescending { it.doneAt }.map { t ->
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
    // A long press: rename, share as PDF, archive (owner, 17 окт: what the topic's page did).
    var hold by remember { mutableStateOf(false) }
    TopicHold(t.id, hold) { hold = false }
    PaperRow(seat, margin = margin, mark = { TopicGlyph(c.graphite, 20.dp) }, onClick = onOpen, onLong = { hold = true },
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
                            WordText(t.title, style = Type.body.copy(color = c.ink, fontSize = 15.5.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
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

/** A day of the diary's feed: its plan, the notes written on it, the topics with something on it (and what — «начало», «срок»…). */
internal class FeedDay(val day: LocalDate, val plan: DayPlan, val notes: List<LinkedNote>, val topics: List<Pair<Task, String?>>)
