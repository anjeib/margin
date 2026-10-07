package ru.anjeib.margin.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import ru.anjeib.margin.ai.Scope
import ru.anjeib.margin.data.AppData
import ru.anjeib.margin.data.Attachment
import ru.anjeib.margin.data.Entry
import ru.anjeib.margin.data.Subtask
import ru.anjeib.margin.data.Task
import ru.anjeib.margin.data.TopicEvent
import ru.anjeib.margin.data.dayMonthShort
import ru.anjeib.margin.data.isWait
import ru.anjeib.margin.data.materials
import ru.anjeib.margin.data.noNextStep
import ru.anjeib.margin.data.sentence
import ru.anjeib.margin.data.shortLabel
import java.time.LocalDate
import java.time.LocalDateTime

private fun dateOfStamp(at: String): LocalDate? = runCatching { LocalDate.parse(at.take(10)) }.getOrNull()
private fun timeOfStamp(at: String): String = at.drop(11).take(5)
/** Topics whose «grows into a topic» moment was already shown in this run of the app. */
private val grownTopics = mutableSetOf<String>()

private fun secondsSince(at: String): Long = runCatching { java.time.Duration.between(LocalDateTime.parse(at.take(16)), LocalDateTime.now()).seconds }.getOrDefault(Long.MAX_VALUE)

/** One line of the feed, flattened for the thread: the user's words, then what came of them, each on its own node. */
private sealed interface FeedLine {
    val at: String
    val key: String
    data class Said(val e: Entry) : FeedLine { override val at = e.at; override val key = "n:" + e.id }
    data class Event(val ev: TopicEvent, val under: Boolean) : FeedLine { override val at = ev.at; override val key = "e:" + ev.id }
}

private fun feedOf(t: Task): List<FeedLine> {
    val byEntry = t.events.filter { ev -> ev.entry != null && t.entries.any { it.id == ev.entry } }.groupBy { it.entry }
    val standalone = t.events.filter { ev -> ev.entry == null || t.entries.none { it.id == ev.entry } }
    val blocks: List<Pair<String, List<FeedLine>>> = t.entries.map { e -> e.at to (listOf<FeedLine>(FeedLine.Said(e)) + byEntry[e.id].orEmpty().map { FeedLine.Event(it, true) }) } +
        standalone.map { it.at to listOf<FeedLine>(FeedLine.Event(it, false)) }
    return blocks.sortedBy { it.first.take(16) }.flatMap { it.second }
}

/**
 * v2: a topic on the whole screen. The margin rule is its thread: dates on the margin, the user's words and what came of
 * them hang on it. «Лента · Вопросы · Материалы» over one composer; a link in the feed opens the question, «назад» comes back.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TopicScreen(task: Task, data: AppData, vm: MainVm, startTab: Int, startHl: String?, onClose: () -> Unit, onOpenPerson: (String) -> Unit) {
    val c = M.c
    val s = M.s
    val view = LocalView.current
    val motion = motionOn()
    val scope = rememberCoroutineScope()
    val pager = rememberPagerState(initialPage = startTab.coerceIn(0, 2)) { 3 }
    val feedState = rememberLazyListState()
    var hl by remember(task.id) { mutableStateOf(startHl) }
    var dialog by remember(task.id) { mutableStateOf<String?>(null) }
    var menu by remember { mutableStateOf(false) }
    var editQ by remember { mutableStateOf<Subtask?>(null) }
    var dateQ by remember { mutableStateOf<Subtask?>(null) }
    var viewing by remember { mutableStateOf<Attachment?>(null) }
    val busy by vm.busy.collectAsState()
    val notice by vm.notice.collectAsState()
    val working by vm.working.collectAsState()
    val scopeKey = MainVm.key(Scope.InTask(task.id))
    val feed = remember(task) { feedOf(task) }

    // The task just grew into a topic: the thread grows down the margin and its lines arrive one by one.
    // Played once per topic in a run of the app (event times are kept to the minute, so «just now» is a two-minute window).
    val becameNow = remember(task.id) {
        val fresh = task.events.lastOrNull { it.kind == "became" }?.let { secondsSince(it.at) < 150 } == true && task.id !in grownTopics
        if (fresh) grownTopics += task.id
        fresh
    }
    val rule = remember(task.id) { Animatable(if (becameNow && motion) 0f else 1f) }
    LaunchedEffect(task.id) { rule.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }
    // Lines that were here when the screen opened stay still; new ones (or all, for a newborn topic) arrive.
    val seenAtOpen = remember(task.id) { if (becameNow) emptySet() else feed.map { it.key }.toSet() }
    // «В архив»: the topic folds down like a closed notebook.
    val fold = remember(task.id) { Animatable(0f) }

    LaunchedEffect(hl) { if (hl != null) { kotlinx.coroutines.delay(2200); hl = null } }
    LaunchedEffect(task.id) { if (startTab == 0 && feed.isNotEmpty()) feedState.scrollToItem(feed.size) }
    LaunchedEffect(feed.size) { if (pager.currentPage == 0 && feed.isNotEmpty()) feedState.animateScrollToItem(feed.size) }

    fun toQuestion(id: String) { hl = id; scope.launch { pager.animateScrollToPage(1) } }
    fun toFeedLine(key: String) {
        val i = feed.indexOfFirst { it.key == key }
        hl = key
        scope.launch { pager.animateScrollToPage(0); if (i >= 0) feedState.animateScrollToItem(i + 1) }
    }
    fun archive() {
        scope.launch {
            view.confirmTick()
            if (motion) fold.animateTo(1f, tween(340, easing = FastOutSlowInEasing))
            vm.archiveTopic(task.id)
            onClose()
        }
    }
    BackHandler { onClose() }
    BackHandler(pager.currentPage != 0) { scope.launch { pager.animateScrollToPage(0) } }

    val total = task.subtasks.size
    val doneCount = task.subtasks.count { it.done }
    Column(
        Modifier.fillMaxSize().graphicsLayer {
            scaleY = 1f - 0.92f * fold.value; alpha = 1f - fold.value; transformOrigin = TransformOrigin(0.5f, 1f)
        }.background(c.bg).statusBarsPadding().navigationBarsPadding().imePadding().marginRule(c.accent.copy(alpha = 0.85f), rule.value),
    ) {
        // ---- header: back on the margin, the title right of the rule ----
        Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(MarginW), contentAlignment = Alignment.Center) {
                Box(Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, s.backWord, tint = c.ink, modifier = Modifier.size(22.dp))
                }
            }
            Spacer(Modifier.weight(1f))
            if (!task.done) Box(Modifier.size(40.dp).clip(CircleShape).clickable { dialog = "archive" }, contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.CheckCircle, s.toArchive, tint = if (task.noNextStep) c.link else c.graphite, modifier = Modifier.size(22.dp))
            }
            Box(Modifier.padding(end = 8.dp)) {
                Box(Modifier.size(40.dp).clip(CircleShape).clickable { menu = true }, contentAlignment = Alignment.Center) { Icon(Icons.Outlined.MoreHoriz, null, tint = c.graphite) }
                DropdownMenu(menu, { menu = false }, containerColor = c.card) {
                    DropdownMenuItem(text = { Text(s.edit, style = Type.body) }, onClick = { menu = false; dialog = "rename" })
                    DropdownMenuItem(text = { Text(s.labelWord, style = Type.body) }, onClick = { menu = false; dialog = "label" })
                    DropdownMenuItem(text = { Text(s.delete, style = Type.body.copy(color = c.accent)) }, onClick = { menu = false; dialog = "delete" })
                }
            }
        }
        Margined {
            Column {
                Text(task.title, style = Type.title.copy(color = c.ink, fontSize = 28.sp, lineHeight = 34.sp),
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { dialog = "rename" })
                val since = runCatching { LocalDate.parse(task.createdAt) }.getOrNull()?.dayMonthShort()
                Text(listOfNotNull(task.project?.let { "#" + projectLabel(it) }, since?.let { if (task.thought && task.subtasks.isEmpty()) s.thoughtSince(it) else s.topicSince(it) }).joinToString("  ·  "),
                    style = Type.small.copy(color = c.graphite, fontSize = 13.sp), modifier = Modifier.padding(top = 6.dp).clip(RoundedCornerShape(6.dp)).clickable { dialog = "label" })
                if (total > 0) Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    TopicProgress(doneCount, total, Modifier.weight(1f))
                    Spacer(Modifier.width(12.dp))
                    Text(s.progressSolved(doneCount, total), style = Type.small.copy(color = c.graphite, fontSize = 12.5.sp, fontFeatureSettings = "tnum"))
                }
                if (task.done) Row(Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(12.dp)).background(c.chip).padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Text(s.inArchiveSince(task.doneAt?.let { runCatching { LocalDate.parse(it).dayMonthShort() }.getOrNull() } ?: ""), style = Type.small.copy(color = c.graphite), modifier = Modifier.weight(1f))
                    Text(s.bringBack, style = Type.small.copy(color = c.link), modifier = Modifier.clickable { vm.reviveTopic(task.id) })
                }
                // ---- three views ----
                val open = task.subtasks.count { !it.done }
                val files = task.materials().size
                Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                    listOf(s.feedTab to 0, s.questionsTab to open, s.materialsTab to files).forEachIndexed { i, (label, n) ->
                        val on = pager.currentPage == i
                        val bar by animateFloatAsState(if (on) 1f else 0f, tween(220), label = "tab")
                        Column(Modifier.clip(RoundedCornerShape(6.dp)).clickable { scope.launch { pager.animateScrollToPage(i) } }) {
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(label, style = Type.tab.copy(color = if (on) c.link else c.graphite, fontSize = 14.5.sp))
                                if (i > 0 && n > 0) Text(" $n", style = Type.small.copy(color = if (on) c.link else c.faint, fontSize = 12.5.sp))
                            }
                            Spacer(Modifier.height(8.dp))
                            Box(Modifier.height(2.dp).width(40.dp * bar).background(c.link))
                        }
                    }
                }
            }
        }
        Hairline(Modifier.padding(start = MarginW))
        HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth(), beyondViewportPageCount = 1, verticalAlignment = Alignment.Top) { page ->
            when (page) {
                0 -> FeedPage(task, feed, feedState, hl, seenAtOpen, working, onQuestion = ::toQuestion, onView = { viewing = it }, onArchive = { dialog = "archive" }, onSource = { dialog = "source" })
                1 -> QuestionsPage(task, hl, vm, onFeed = ::toFeedLine, onEdit = { editQ = it }, onDate = { dateQ = it })
                else -> MaterialsGrid(listOf(task), onView = { viewing = it }, onTopic = null, inset = MarginW + MarginGap)
            }
        }
        // ---- composer: words go to the feed, on «Вопросы» they become a question ----
        Column(Modifier.background(c.bg).padding(horizontal = 14.dp).padding(top = 6.dp, bottom = 10.dp)) {
            notice?.takeIf { it.scopeKey == scopeKey }?.let { nt -> NoticeLine(nt, vm) }
            SmartComposer(
                vm, if (pager.currentPage == 1) s.newQuestionHint else s.writeInTopic, busy == scopeKey,
                onSend = { t, f -> vm.send(t, scope = Scope.InTask(task.id), files = f, asQuestion = pager.currentPage == 1) },
            )
        }
    }

    viewing?.let { a -> Viewer(a, vm, onClose = { viewing = null }) }
    when (dialog) {
        "archive" -> AlertDialog(
            onDismissRequest = { dialog = null }, containerColor = c.card,
            title = { Text(s.archiveQ, style = Type.cardTitle.copy(color = c.ink)) },
            text = { Text(s.archiveText(task.title), style = Type.body.copy(color = c.graphite)) },
            confirmButton = { TextButton({ dialog = null; archive() }) { Text(s.toArchive, color = c.link) } },
            dismissButton = { TextButton({ dialog = null }) { Text(s.cancel, color = c.graphite) } },
        )
        "rename" -> TextDialog(s.edit, task.title, "", onDismiss = { dialog = null }) { v -> if (v.isNotBlank()) vm.saveTask(task.copy(title = v.trim().sentence())); dialog = null }
        "label" -> TextDialog(s.labelWord, task.project.orEmpty(), "дом", onDismiss = { dialog = null }) { v -> vm.setTopicLabel(task.id, v); dialog = null }
        "delete" -> AlertDialog(
            onDismissRequest = { dialog = null }, containerColor = c.card,
            title = { Text(s.deleteTaskQ, style = Type.cardTitle.copy(color = c.ink)) },
            confirmButton = { TextButton({ dialog = null; onClose(); vm.deleteTask(task.id) }) { Text(s.delete, color = c.accent) } },
            dismissButton = { TextButton({ dialog = null }) { Text(s.cancel, color = c.graphite) } },
        )
        "source" -> AlertDialog(
            onDismissRequest = { dialog = null }, containerColor = c.card,
            title = { Text(s.youWrote, style = Type.cardTitle.copy(color = c.ink)) },
            text = { Text(task.source.orEmpty().sentence(), style = Type.body.copy(color = c.ink)) },
            confirmButton = { TextButton({ dialog = null }) { Text(s.closeWord2, color = c.graphite) } },
        )
    }
    editQ?.let { q -> TextDialog(s.edit, q.title, "", onDismiss = { editQ = null }) { v -> vm.renameSubtask(task.id, q.id, v); editQ = null } }
    dateQ?.let { q -> QuestionDateDialog(q, onDismiss = { dateQ = null }) { d -> vm.setQuestionDue(task.id, q.id, d); dateQ = null } }
}

// ---------------- the feed: a thread on the margin ----------------

/** What sits on the margin next to an event: when we expect it, when the question is due. */
private fun eventNote(q: Subtask?, kind: String): String? {
    if (q == null || q.done || (kind != "wait" && kind != "q")) return null
    val d = q.due?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
    return (if (q.isWait) "~" else "") + d.shortLabel().lowercase().let { if (it.length > 8) d.dayMonthShort() else it }
}

@Composable
private fun FeedPage(task: Task, feed: List<FeedLine>, state: androidx.compose.foundation.lazy.LazyListState, hl: String?, seen: Set<String>, working: Set<String>,
                     onQuestion: (String) -> Unit, onView: (Attachment) -> Unit, onArchive: () -> Unit, onSource: () -> Unit) {
    val c = M.c
    val s = M.s
    // New lines arrive one after another, in the order they were written.
    val freshKeys = feed.filter { it.key !in seen }.map { it.key }
    LazyColumn(Modifier.fillMaxSize(), state = state, contentPadding = PaddingValues(top = 6.dp, bottom = 24.dp)) {
        item("origin") {
            if (task.source != null) {
                val born = task.history.firstOrNull { it.kind == "created" }?.at?.let { runCatching { LocalDateTime.parse(it.take(16)) }.getOrNull() }
                val stamp = born?.let { it.toLocalDate().dayMonthShort() + ", " + it.toLocalTime().toString().take(5) } ?: LocalDate.parse(task.createdAt).dayMonthShort()
                Margined {
                    Text("↳ " + s.fromMessage(stamp), style = Type.small.copy(color = c.graphite, fontSize = 13.sp),
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp).clip(RoundedCornerShape(6.dp)).clickable(onClick = onSource).padding(2.dp))
                }
            } else Spacer(Modifier.height(4.dp))
        }
        var lastDay: LocalDate? = null
        feed.forEachIndexed { idx, line ->
            val day = dateOfStamp(line.at)
            val firstOfDay = day != lastDay && !(line is FeedLine.Event && line.under)
            if (firstOfDay) lastDay = day
            val dayText = if (firstOfDay) day?.dayMonthShort() else null
            val isLast = idx == feed.lastIndex || feed.drop(idx + 1).none { it is FeedLine.Said }
            item(line.key) {
                val fresh = line.key in freshKeys
                Box(Modifier.arrive(fresh, freshKeys.indexOf(line.key)).landing(hl == line.key, 10)) {
                    when (line) {
                        is FeedLine.Said -> SaidLine(line.e, dayText, current = isLast && line is FeedLine.Said, fresh = fresh, working = working, onView = onView)
                        is FeedLine.Event -> EventLine(task, line.ev, dayText, hl = hl, onQuestion = onQuestion)
                    }
                }
            }
        }
        if (task.noNextStep) item("end") {
            Margined(Modifier.padding(top = 18.dp)) {
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).border(1.dp, c.hairline, RoundedCornerShape(14.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(s.allSettled, style = Type.small.copy(color = c.graphite, fontSize = 14.sp), modifier = Modifier.weight(1f))
                    Text(s.toArchive, style = Type.small.copy(color = c.link, fontSize = 14.sp), modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onArchive).padding(4.dp))
                }
            }
        }
    }
}

/** The user's words: a quiet card on the thread, the day and hour on the margin; files under it. */
@Composable
private fun SaidLine(e: Entry, dayText: String?, current: Boolean, fresh: Boolean, working: Set<String>, onView: (Attachment) -> Unit) {
    val c = M.c
    Margined(Modifier.padding(top = 8.dp, bottom = 4.dp), node = if (current) c.accent else c.graphite.copy(alpha = 0.6f), nodeY = 14.dp,
        margin = { MarginText(listOfNotNull(dayText, e.at.drop(11).take(5)).joinToString("\n")) }) {
        Column {
            if (e.text.isNotBlank() || e.isList) Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.card).border(1.dp, c.hairline, RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp, vertical = 11.dp)) {
                if (e.text.isNotBlank()) Text(e.text, style = Type.body.copy(color = c.ink, fontSize = 15.5.sp))
                e.items.forEach { Text((if (it.done) "✓ " else "· ") + it.title, style = Type.body.copy(color = if (it.done) c.graphite else c.ink, fontSize = 15.sp)) }
            }
            if (e.attachments.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                MaterialRow(e.attachments, fresh, working, onView)
            }
        }
    }
}

/** Photos and files of one record. A new photo comes into focus; one being read gets a passing scan line. */
@Composable
private fun MaterialRow(list: List<Attachment>, fresh: Boolean, working: Set<String>, onView: (Attachment) -> Unit) {
    val c = M.c
    val motion = motionOn()
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        list.forEach { a ->
            if (a.kind == "photo" || a.kind == "video") Column(Modifier.width(132.dp).clickable { onView(a) }) {
                val sharp = remember(a.id) { Animatable(if (fresh && motion) 0f else 1f) }
                LaunchedEffect(a.id) { sharp.animateTo(1f, tween(700, easing = FastOutSlowInEasing)) }
                Box(Modifier.size(132.dp, 100.dp).clip(RoundedCornerShape(12.dp))) {
                    Box(Modifier.blur((14 * (1f - sharp.value)).dp).graphicsLayer { alpha = 0.4f + 0.6f * sharp.value }) { Thumb(a, 132.dp) }
                    if ("ocr:${a.id}" in working) ScanLine()
                }
                a.caption?.let { Text(it, style = Type.small.copy(color = c.graphite, fontSize = 12.5.sp), maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp)) }
            }
            else Row(Modifier.clip(RoundedCornerShape(10.dp)).background(c.chip).clickable { onView(a) }.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(kindIcon(a.kind), null, tint = c.graphite, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(attachmentLabel(a), style = Type.small.copy(color = c.ink, fontSize = 13.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** A thin line passing down a photo while its text is read. */
@Composable
private fun ScanLine() {
    val c = M.c
    val t = rememberInfiniteTransition(label = "scan")
    val y by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart), label = "y")
    Box(Modifier.fillMaxSize().drawWithContent {
        drawContent()
        val yy = size.height * y
        drawRect(c.link.copy(alpha = 0.10f), topLeft = Offset(0f, 0f), size = androidx.compose.ui.geometry.Size(size.width, yy))
        drawLine(c.link, Offset(0f, yy), Offset(size.width, yy), strokeWidth = 2.dp.toPx())
    })
}

/** «Вопрос: …», «Решено: …», «Ждём от Игоря: …» — a node on the thread, the date we expect on the margin, a link to the question. */
@Composable
private fun EventLine(task: Task, ev: TopicEvent, dayText: String?, hl: String?, onQuestion: (String) -> Unit) {
    val c = M.c
    val s = M.s
    val q = ev.ref?.let { r -> task.subtasks.find { it.id == r } }
    when (ev.kind) {
        "became", "revived" -> Margined(Modifier.padding(vertical = 4.dp), node = c.faint, nodeSize = 7.dp, nodeY = 10.dp, margin = dayText?.let { d -> { MarginText(d) } }) {
            Text(if (ev.kind == "became") s.becameTopic else s.revivedTopic, style = Type.small.copy(color = c.graphite, fontStyle = FontStyle.Italic, fontSize = 13.sp))
        }
        else -> {
            if (q == null) return
            val faded = (ev.kind == "q" || ev.kind == "wait") && q.done
            val nodeColor = when (ev.kind) { "solved", "got" -> c.ok; "wait" -> if (q.done) c.faint else c.link; else -> if (q.done) c.faint else c.link }
            val who = q.from ?: ""
            val prefix = when (ev.kind) { "q" -> s.questionPrefix; "solved" -> s.solvedPrefix; "wait" -> s.waitPrefix(who); else -> s.gotPrefix(who) }
            val note = eventNote(q, ev.kind)
            Margined(Modifier.clip(RoundedCornerShape(8.dp)).landing(hl == "e:" + ev.id, 8).clickable { onQuestion(q.id) }.padding(vertical = 5.dp),
                node = nodeColor, nodeSize = 7.dp, nodeY = 11.dp,
                margin = (note ?: dayText)?.let { m -> { MarginText(m, petrol = note != null) } }) {
                Row(verticalAlignment = Alignment.Top) {
                    Box(Modifier.padding(top = 1.dp)) {
                        val tint = if (faded) c.faint else c.graphite
                        when (ev.kind) { "q" -> QuestionGlyph(tint, 17.dp); "wait" -> HourglassGlyph(tint, 17.dp, sand = if (q.done) 1f else 0.35f); else -> TickGlyph(c.ok, 17.dp) }
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(buildAnnotatedString {
                            withStyle(SpanStyle(fontWeight = FontWeight.Medium, color = if (faded) c.faint else c.ink)) { append(prefix) }
                            append(" ")
                            withStyle(SpanStyle(color = if (faded) c.faint else if (ev.kind == "q" || ev.kind == "wait") c.link else c.ink)) { append(q.title) }
                        }, style = Type.body.copy(fontSize = 15.sp))
                        val meta = when {
                            ev.kind == "q" && q.done -> q.doneAt?.let { s.solvedOn(LocalDate.parse(it).dayMonthShort()) }
                            ev.kind == "wait" && q.done -> q.doneAt?.let { s.gotOn(LocalDate.parse(it).dayMonthShort()) }
                            else -> null
                        }
                        if (meta != null) Text(meta, style = Type.small.copy(color = c.faint, fontSize = 13.sp), modifier = Modifier.padding(top = 2.dp))
                    }
                }
            }
        }
    }
}

// ---------------- questions ----------------

@Composable
private fun QuestionsPage(task: Task, hl: String?, vm: MainVm, onFeed: (String) -> Unit, onEdit: (Subtask) -> Unit, onDate: (Subtask) -> Unit) {
    val c = M.c
    val s = M.s
    val open = task.subtasks.filter { !it.done && !it.isWait }
    val waits = task.subtasks.filter { !it.done && it.isWait }
    val done = task.subtasks.filter { it.done }
    var showDone by remember(task.id) { mutableStateOf(done.any { it.id == hl }) }
    val state = rememberLazyListState()
    LaunchedEffect(hl) {
        val order = open + waits + (if (showDone) done else emptyList())
        val i = order.indexOfFirst { it.id == hl }
        if (i >= 0) state.animateScrollToItem(i)
    }
    LazyColumn(Modifier.fillMaxSize(), state = state, contentPadding = PaddingValues(bottom = 24.dp)) {
        if (task.subtasks.isEmpty()) item("empty") { Margined { Text(s.noQuestions, style = Type.body.copy(color = c.graphite), modifier = Modifier.padding(vertical = 20.dp)) } }
        if (open.isNotEmpty()) item("h-open") { QHead(s.openCaps, open.size) }
        open.forEach { q -> item(q.id) { QuestionRow(task, q, hl == q.id, vm, onFeed, onEdit, onDate) } }
        if (waits.isNotEmpty()) item("h-wait") { QHead(s.waitingCaps, waits.size) }
        waits.forEach { q -> item(q.id) { QuestionRow(task, q, hl == q.id, vm, onFeed, onEdit, onDate) } }
        if (done.isNotEmpty()) item("h-done") {
            Margined {
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { showDone = !showDone }, verticalAlignment = Alignment.CenterVertically) {
                    QHeadText(s.solvedCaps, done.size)
                    Icon(Icons.Rounded.KeyboardArrowDown, null, tint = c.faint, modifier = Modifier.padding(top = 14.dp).size(18.dp).rotate(if (showDone) 0f else -90f))
                }
            }
        }
        if (showDone) done.forEach { q -> item(q.id) { QuestionRow(task, q, hl == q.id, vm, onFeed, onEdit, onDate) } }
    }
}

@Composable
private fun QHead(label: String, n: Int) = Margined { QHeadText(label, n) }

@Composable
private fun QHeadText(label: String, n: Int) {
    Row(Modifier.padding(top = 20.dp, bottom = 6.dp)) {
        SectionLabel(label)
        Text("  $n", style = Type.label.copy(color = M.c.faint))
    }
}

/**
 * One question. Its day on the margin. Settling it fills the circle and draws the line through the words like a pen;
 * something we waited for turns its hourglass over before it becomes «получено».
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QuestionRow(task: Task, q: Subtask, lit: Boolean, vm: MainVm, onFeed: (String) -> Unit, onEdit: (Subtask) -> Unit, onDate: (Subtask) -> Unit) {
    val c = M.c
    val s = M.s
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val motion = motionOn()
    var menu by remember { mutableStateOf(false) }
    val made = task.events.firstOrNull { it.ref == q.id && (it.kind == "q" || it.kind == "wait") }
    val strike = remember(q.id) { Animatable(if (q.done) 1f else 0f) }
    LaunchedEffect(q.done) { strike.animateTo(if (q.done) 1f else 0f, tween(if (motion) 320 else 0, easing = FastOutSlowInEasing)) }
    val turn = remember(q.id) { Animatable(0f) }
    // While it settles the row stays put: the circle fills, the pen draws through, then it moves to «Решено».
    var settling by remember(q.id) { mutableStateOf(false) }
    fun settle() {
        if (q.done || !motion) { if (!q.done) view.confirmTick(); vm.toggleQuestion(task.id, q.id); return }
        view.confirmTick()
        scope.launch {
            settling = true
            if (q.isWait) turn.animateTo(1f, tween(360, easing = FastOutSlowInEasing))
            else strike.animateTo(1f, tween(340, easing = FastOutSlowInEasing))
            kotlinx.coroutines.delay(260)
            vm.toggleQuestion(task.id, q.id)
            settling = false; turn.snapTo(0f)
        }
    }
    val late = !q.done && q.due != null && LocalDate.parse(q.due).isBefore(LocalDate.now())
    val marginText = when {
        q.done -> q.doneAt?.let { LocalDate.parse(it).dayMonthShort() }
        q.due != null -> (if (q.isWait) "~" else "") + LocalDate.parse(q.due).shortLabel().lowercase().let { if (it.length > 8) LocalDate.parse(q.due).dayMonthShort() else it } + (q.time?.let { "\n$it" } ?: "")
        else -> null
    }
    Box {
        Margined(Modifier.landing(lit, 0).combinedClickable(onClick = { onDate(q) }, onLongClick = { menu = true }),
            margin = marginText?.let { m -> { MarginText(m, late = late, petrol = q.isWait && !q.done, faint = q.done) } }) {
            Column {
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
                    Box(Modifier.size(28.dp).clip(CircleShape).clickable(onClick = ::settle), contentAlignment = Alignment.Center) {
                        if (q.isWait && !q.done) {
                            if (turn.value < 0.98f) HourglassGlyph(c.link, 19.dp, sand = 0.35f + 0.65f * turn.value, turn = turn.value) else TickGlyph(c.ok, 20.dp)
                        } else CheckCircle(q.done || settling, size = 19.dp, onToggle = ::settle)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f).padding(top = 3.dp)) {
                        // The line through the words is drawn, not switched on.
                        StrikeText(if (q.isWait) q.title.replaceFirstChar { it.uppercase() } + " — " + (q.from ?: "") else q.title,
                            Type.body.copy(fontSize = 16.sp, color = if (q.done || settling) c.faint else c.ink), strike.value, c.faint)
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 3.dp)) {
                            val parts = listOfNotNull(if (q.done) (if (q.isWait) s.gotIt else s.solvedWord).lowercase() else if (q.due == null) s.noDateQ else null, q.assignee)
                            if (parts.isNotEmpty()) Text(parts.joinToString("  ·  "), style = Type.small.copy(color = c.graphite, fontSize = 13.5.sp))
                            made?.let { ev ->
                                if (parts.isNotEmpty()) Text("  ·  ", style = Type.small.copy(color = c.faint))
                                Text(s.inFeed(dateOfStamp(ev.at)?.dayMonthShort() ?: ""), style = Type.small.copy(color = c.link, fontSize = 13.5.sp),
                                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { onFeed(if (ev.entry != null) "n:" + ev.entry else "e:" + ev.id) })
                            }
                        }
                    }
                }
                Hairline(Modifier.padding(start = 38.dp))
            }
        }
        DropdownMenu(menu, { menu = false }, containerColor = c.card) {
            DropdownMenuItem(text = { Text(s.edit, style = Type.body) }, onClick = { menu = false; onEdit(q) })
            DropdownMenuItem(text = { Text(s.questionDateQ, style = Type.body) }, onClick = { menu = false; onDate(q) })
            DropdownMenuItem(text = { Text(s.delete, style = Type.body.copy(color = c.accent)) }, onClick = { menu = false; vm.deleteSubtask(task.id, q.id) })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun QuestionDateDialog(q: Subtask, onDismiss: () -> Unit, onSave: (String?) -> Unit) {
    val c = M.c
    val s = M.s
    val today = LocalDate.now()
    fun LocalDate.ms() = atStartOfDay().toInstant(java.time.ZoneOffset.UTC).toEpochMilli()
    val st = androidx.compose.material3.rememberDatePickerState(initialSelectedDateMillis = q.due?.let { LocalDate.parse(it).ms() })
    val colors = androidx.compose.material3.DatePickerDefaults.colors(containerColor = c.card, selectedDayContainerColor = c.link, todayDateBorderColor = c.link, todayContentColor = c.link)
    androidx.compose.material3.DatePickerDialog(
        onDismissRequest = onDismiss, colors = colors,
        confirmButton = { TextButton({ onSave(st.selectedDateMillis?.let { java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneOffset.UTC).toLocalDate().toString() }) }) { Text(s.save, color = c.link) } },
        dismissButton = { TextButton(onDismiss) { Text(s.cancel, color = c.graphite) } },
    ) {
        Column {
            FlowRow(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill(s.quickToday, onClick = { onSave(today.toString()) })
                Pill(s.quickTomorrow, onClick = { onSave(today.plusDays(1).toString()) })
                Pill(s.inAWeek, onClick = { onSave(today.plusWeeks(1).toString()) })
                Pill(s.quickNone, onClick = { onSave(null) })
            }
            androidx.compose.material3.DatePicker(state = st, colors = colors, showModeToggle = false, title = null, headline = null)
        }
    }
}

// ---------------- materials ----------------

/** Photos, files and voice notes of [topics] in one grid with a search that reads the text on photos and in voice notes too. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MaterialsGrid(topics: List<Task>, onView: (Attachment) -> Unit, onTopic: ((String) -> Unit)?, inset: Dp = 20.dp, header: (@Composable () -> Unit)? = null) {
    val c = M.c
    val s = M.s
    var q by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf("all") }
    val all = topics.flatMap { t -> t.materials().map { (e, a) -> Triple(t, e, a) } }
    fun Attachment.words() = listOfNotNull(name, caption, ocr, transcript, contact?.name).joinToString(" ").lowercase().replace(" ", "")
    val needle = q.trim().lowercase().replace(" ", "")
    val shown = all.filter { (_, _, a) ->
        (kind == "all" || (kind == "photo" && (a.kind == "photo" || a.kind == "video")) || (kind == "file" && a.kind == "file") || (kind == "audio" && a.kind == "audio")) &&
            (needle.isEmpty() || a.words().contains(needle))
    }
    Column(Modifier.fillMaxSize()) {
        header?.invoke()
        if (all.isEmpty()) { Text(s.materialsEmpty, style = Type.body.copy(color = c.graphite), modifier = Modifier.padding(start = inset, end = 20.dp, top = 20.dp)); return@Column }
        Row(Modifier.fillMaxWidth().padding(start = inset, end = 20.dp, top = 14.dp).clip(RoundedCornerShape(14.dp)).background(c.card)
            .border(1.dp, c.hairline, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Search, null, tint = c.graphite, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) {
                if (q.isEmpty()) Text(s.searchMaterials, style = Type.body.copy(color = c.faint, fontSize = 15.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                BasicTextField(q, { q = it }, singleLine = true, textStyle = Type.body.copy(color = c.ink, fontSize = 15.sp), cursorBrush = SolidColor(c.link), modifier = Modifier.fillMaxWidth())
            }
        }
        FlowRow(Modifier.padding(start = inset, end = 20.dp, top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("all" to s.all, "photo" to s.kindPhotos, "file" to s.kindDocs, "audio" to s.kindVoice).forEach { (k, l) ->
                val on = kind == k
                Text(l, style = Type.small.copy(color = if (on) c.link else c.graphite, fontSize = 13.5.sp),
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(if (on) c.linkSoft else c.chip).clickable { kind = k }.padding(horizontal = 12.dp, vertical = 6.dp))
            }
        }
        if (shown.isEmpty()) { Text(s.noMaterialsFound, style = Type.body.copy(color = c.graphite), modifier = Modifier.padding(start = inset, end = 20.dp, top = 20.dp)); return@Column }
        LazyVerticalGrid(GridCells.Adaptive(104.dp), Modifier.fillMaxSize(), contentPadding = PaddingValues(start = inset, end = 20.dp, top = 14.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(shown, key = { it.third.id }) { (t, e, a) ->
                Column(Modifier.clip(RoundedCornerShape(10.dp)).clickable { onView(a) }) {
                    if (a.kind == "photo" || a.kind == "video") Thumb(a, 104.dp)
                    else Box(Modifier.size(104.dp).clip(RoundedCornerShape(12.dp)).background(c.card).border(1.dp, c.hairline, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                        Icon(kindIcon(a.kind), null, tint = c.graphite, modifier = Modifier.size(26.dp))
                    }
                    Text(a.caption ?: attachmentLabel(a).ifBlank { a.name }, style = Type.small.copy(color = c.graphite, fontSize = 12.5.sp), maxLines = 2, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp))
                    if (onTopic != null) Text("↳ " + t.title, style = Type.small.copy(color = c.link, fontSize = 12.sp), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable { onTopic(t.id) })
                    // Found by the words inside, not the name: say what matched.
                    if (needle.isNotEmpty() && !a.name.lowercase().replace(" ", "").contains(needle)) (a.ocr ?: a.transcript)?.let {
                        Text("«" + it.take(60) + "»", style = Type.small.copy(color = c.faint, fontSize = 11.5.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}
