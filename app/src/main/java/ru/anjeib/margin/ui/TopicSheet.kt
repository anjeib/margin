package ru.anjeib.margin.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.rounded.Check
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
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
import ru.anjeib.margin.data.dowShort
import ru.anjeib.margin.data.dayMonth
import ru.anjeib.margin.data.isWait
import ru.anjeib.margin.data.materials
import ru.anjeib.margin.data.noNextStep
import ru.anjeib.margin.data.sentence
import ru.anjeib.margin.data.shortLabel
import java.time.LocalDate
import java.time.LocalDateTime

private fun dateOfStamp(at: String): LocalDate? = runCatching { LocalDate.parse(at.take(10)) }.getOrNull()
private fun timeOfStamp(at: String): String = at.drop(11).take(5)

/** One line of the feed: the user's words (with what came of them under it) or a line of its own. */
private sealed interface FeedItem {
    val at: String
    val key: String
    data class Said(val e: Entry, val under: List<TopicEvent>) : FeedItem { override val at = e.at; override val key = "n:" + e.id }
    data class Line(val ev: TopicEvent) : FeedItem { override val at = ev.at; override val key = "e:" + ev.id }
}

private fun feedOf(t: Task): List<FeedItem> {
    val byEntry = t.events.filter { ev -> ev.entry != null && t.entries.any { it.id == ev.entry } }.groupBy { it.entry }
    val lines = t.events.filter { ev -> ev.entry == null || t.entries.none { it.id == ev.entry } }.map { FeedItem.Line(it) }
    return (t.entries.map { FeedItem.Said(it, byEntry[it.id].orEmpty()) } + lines).sortedBy { it.at.take(16) }
}

/**
 * v2: the topic's sheet — «Лента · Вопросы · Материалы» over one composer. Swiping between the three keeps each where it was;
 * a link in the feed opens the question, «назад» brings the feed back to the same line.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ColumnScope.TopicBody(task: Task, data: AppData, vm: MainVm, startTab: Int, startHl: String?, onClose: () -> Unit, onOpenPerson: (String) -> Unit) {
    val c = M.c
    val s = M.s
    val scope = rememberCoroutineScope()
    val pager = rememberPagerState(initialPage = startTab.coerceIn(0, 2)) { 3 }
    val feedState = rememberLazyListState()
    var hl by remember(task.id) { mutableStateOf(startHl) }
    var dialog by remember(task.id) { mutableStateOf<String?>(null) }
    var menu by remember { mutableStateOf(false) }
    var editQ by remember { mutableStateOf<Subtask?>(null) }
    var dateQ by remember { mutableStateOf<Subtask?>(null) }
    var viewing by remember { mutableStateOf<Attachment?>(null) }
    // Came to a question from the feed: «назад» goes back to the feed, to the same line.
    var cameFromFeed by remember { mutableStateOf(false) }
    val busy by vm.busy.collectAsState()
    val notice by vm.notice.collectAsState()
    val scopeKey = MainVm.key(Scope.InTask(task.id))
    val feed = remember(task) { feedOf(task) }
    LaunchedEffect(hl) { if (hl != null) { kotlinx.coroutines.delay(2200); hl = null } }
    LaunchedEffect(task.id) { if (startTab == 0) feedState.scrollToItem((feed.size).coerceAtLeast(0)) }
    // New lines in the feed: show the latest.
    LaunchedEffect(feed.size) { if (pager.currentPage == 0 && feed.isNotEmpty()) feedState.animateScrollToItem(feed.size + 1) }

    fun toQuestion(id: String) { hl = id; cameFromFeed = pager.currentPage == 0; scope.launch { pager.animateScrollToPage(1) } }
    fun toFeedLine(key: String) {
        val i = feed.indexOfFirst { it.key == key || (it is FeedItem.Said && it.under.any { ev -> "e:" + ev.id == key }) }
        hl = key
        scope.launch { pager.animateScrollToPage(0); if (i >= 0) feedState.animateScrollToItem(i + 1) }
    }
    BackHandler(pager.currentPage != 0) { scope.launch { pager.animateScrollToPage(0) }; cameFromFeed = false }

    // ---- header ----
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.weight(1f))
        if (!task.done) Box(Modifier.size(40.dp).clip(CircleShape).clickable { dialog = "archive" }, contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.CheckCircle, s.toArchive, tint = if (task.noNextStep) c.link else c.graphite, modifier = Modifier.size(22.dp))
        }
        Box {
            Box(Modifier.size(40.dp).clip(CircleShape).clickable { menu = true }, contentAlignment = Alignment.Center) { Icon(Icons.Outlined.MoreHoriz, null, tint = c.graphite) }
            DropdownMenu(menu, { menu = false }, containerColor = c.card) {
                DropdownMenuItem(text = { Text(s.edit, style = Type.body) }, onClick = { menu = false; dialog = "rename" })
                DropdownMenuItem(text = { Text(s.labelWord, style = Type.body) }, onClick = { menu = false; dialog = "label" })
                DropdownMenuItem(text = { Text(s.delete, style = Type.body.copy(color = c.accent)) }, onClick = { menu = false; dialog = "delete" })
            }
        }
    }
    Text(task.title, style = Type.title.copy(color = c.ink, fontSize = 28.sp, lineHeight = 34.sp),
        modifier = Modifier.padding(horizontal = 20.dp).clip(RoundedCornerShape(8.dp)).clickable { dialog = "rename" })
    val since = runCatching { LocalDate.parse(task.createdAt) }.getOrNull()?.dayMonthShort()
    Text(listOfNotNull(task.project?.let { "#" + projectLabel(it) }, since?.let { s.topicSince(it) }).joinToString("  ·  "),
        style = Type.small.copy(color = c.graphite, fontSize = 13.sp), modifier = Modifier.padding(start = 20.dp, top = 6.dp).clip(RoundedCornerShape(6.dp)).clickable { dialog = "label" })
    if (task.done) Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp).clip(RoundedCornerShape(12.dp)).background(c.chip).padding(horizontal = 14.dp, vertical = 10.dp)) {
        Text(s.inArchiveSince(task.doneAt?.let { runCatching { LocalDate.parse(it).dayMonthShort() }.getOrNull() } ?: ""), style = Type.small.copy(color = c.graphite), modifier = Modifier.weight(1f))
        Text(s.bringBack, style = Type.small.copy(color = c.link), modifier = Modifier.clickable { vm.reviveTopic(task.id) })
    }
    // ---- three views ----
    val open = task.subtasks.count { !it.done }
    val files = task.materials().size
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, top = 14.dp), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
        listOf(s.feedTab to 0, s.questionsTab to open, s.materialsTab to files).forEachIndexed { i, (label, n) ->
            val on = pager.currentPage == i
            Column(Modifier.clip(RoundedCornerShape(6.dp)).clickable { cameFromFeed = false; scope.launch { pager.animateScrollToPage(i) } }) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(label, style = Type.tab.copy(color = if (on) c.link else c.graphite, fontSize = 14.5.sp))
                    if (i > 0 && n > 0) Text(" $n", style = Type.small.copy(color = if (on) c.link else c.faint, fontSize = 12.5.sp))
                }
                Spacer(Modifier.height(8.dp))
                Box(Modifier.height(2.dp).width(if (on) 40.dp else 0.dp).background(c.link))
            }
        }
    }
    Hairline()
    HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth(), beyondViewportPageCount = 2, verticalAlignment = Alignment.Top) { page ->
        when (page) {
            0 -> FeedPage(task, feed, feedState, hl, onQuestion = ::toQuestion, onView = { viewing = it }, onArchive = { dialog = "archive" }, onSource = { dialog = "source" })
            1 -> QuestionsPage(task, hl, vm, onFeed = ::toFeedLine, onEdit = { editQ = it }, onDate = { dateQ = it })
            else -> MaterialsPage(task, onView = { viewing = it })
        }
    }
    // ---- composer: words go to the feed, on «Вопросы» they become a question ----
    Column(Modifier.padding(horizontal = 14.dp).padding(bottom = 10.dp)) {
        notice?.takeIf { it.scopeKey == scopeKey }?.let { nt -> NoticeLine(nt, vm) }
        SmartComposer(
            vm, if (pager.currentPage == 1) s.newQuestionHint else s.writeInTopic, busy == scopeKey,
            onSend = { t, f -> vm.send(t, scope = Scope.InTask(task.id), files = f, asQuestion = pager.currentPage == 1) },
        )
    }

    viewing?.let { a -> Viewer(a, vm, onClose = { viewing = null }) }
    when (dialog) {
        "archive" -> AlertDialog(
            onDismissRequest = { dialog = null }, containerColor = c.card,
            title = { Text(s.archiveQ, style = Type.cardTitle.copy(color = c.ink)) },
            text = { Text(s.archiveText(task.title), style = Type.body.copy(color = c.graphite)) },
            confirmButton = { TextButton({ dialog = null; vm.archiveTopic(task.id); onClose() }) { Text(s.toArchive, color = c.link) } },
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

// ---------------- the feed ----------------

@Composable
private fun FeedPage(task: Task, feed: List<FeedItem>, state: androidx.compose.foundation.lazy.LazyListState, hl: String?,
                     onQuestion: (String) -> Unit, onView: (Attachment) -> Unit, onArchive: () -> Unit, onSource: () -> Unit) {
    val c = M.c
    val s = M.s
    LazyColumn(Modifier.fillMaxSize(), state = state, contentPadding = PaddingValues(bottom = 24.dp)) {
        item("origin") {
            if (task.source != null) {
                val born = task.history.firstOrNull { it.kind == "created" }?.at?.let { runCatching { LocalDateTime.parse(it.take(16)) }.getOrNull() }
                val stamp = born?.let { it.toLocalDate().dayMonthShort() + ", " + it.toLocalTime().toString().take(5) } ?: LocalDate.parse(task.createdAt).dayMonthShort()
                Text("↳ " + s.fromMessage(stamp), style = Type.small.copy(color = c.graphite, fontSize = 13.sp),
                    modifier = Modifier.padding(start = 20.dp, top = 12.dp).clip(RoundedCornerShape(6.dp)).clickable(onClick = onSource).padding(2.dp))
            } else Spacer(Modifier.height(4.dp))
        }
        var lastDay: LocalDate? = null
        feed.forEach { item ->
            val day = dateOfStamp(item.at)
            val head = if (day != lastDay) day else null
            lastDay = day
            item(item.key) {
                Column(Modifier.landing(hl == item.key, 10)) {
                    head?.let { d -> Text((d.dowShort() + ", " + d.dayMonth()).uppercase(), style = Type.label.copy(color = c.graphite),
                        modifier = Modifier.padding(start = 20.dp, top = 18.dp, bottom = 6.dp)) }
                    when (item) {
                        is FeedItem.Said -> SaidBlock(task, item, hl, onQuestion, onView)
                        is FeedItem.Line -> EventLine(task, item.ev, nested = false, hl = hl, onQuestion = onQuestion)
                    }
                }
            }
        }
        if (task.noNextStep) item("end") {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 18.dp).clip(RoundedCornerShape(14.dp)).border(1.dp, c.hairline, RoundedCornerShape(14.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(s.allSettled, style = Type.small.copy(color = c.graphite, fontSize = 14.sp), modifier = Modifier.weight(1f))
                Text(s.toArchive, style = Type.small.copy(color = c.link, fontSize = 14.sp), modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onArchive).padding(4.dp))
            }
        }
    }
}

/** The user's words as a quiet bubble; what came of them hangs under it on a thin line. */
@Composable
private fun SaidBlock(task: Task, item: FeedItem.Said, hl: String?, onQuestion: (String) -> Unit, onView: (Attachment) -> Unit) {
    val c = M.c
    val e = item.e
    val bubble = RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 16.dp)
    Column(Modifier.fillMaxWidth().padding(start = 54.dp, end = 20.dp, top = 6.dp, bottom = 4.dp)) {
        if (e.text.isNotBlank() || e.isList) Column(Modifier.clip(bubble).background(c.card).border(1.dp, c.hairline, bubble).padding(horizontal = 14.dp, vertical = 10.dp)) {
            if (e.text.isNotBlank()) Text(e.text, style = Type.body.copy(color = c.ink, fontSize = 15.5.sp))
            e.items.forEach { Text((if (it.done) "✓ " else "· ") + it.title, style = Type.body.copy(color = if (it.done) c.graphite else c.ink, fontSize = 15.sp)) }
            Text(timeOfStamp(e.at), style = Type.small.copy(color = c.faint, fontSize = 12.sp), modifier = Modifier.padding(top = 4.dp))
        }
        if (e.attachments.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            MaterialRow(e.attachments, onView)
        }
    }
    if (item.under.isNotEmpty()) Column(Modifier.padding(start = 41.dp).drawBehind {
        drawLine(c.hairline, Offset(0f, 0f), Offset(0f, size.height), strokeWidth = 1.dp.toPx())
    }) {
        item.under.forEach { EventLine(task, it, nested = true, hl = hl, onQuestion = onQuestion) }
    }
}

@Composable
private fun MaterialRow(list: List<Attachment>, onView: (Attachment) -> Unit) {
    val c = M.c
    Row(Modifier.fillMaxWidth().horizontalScrollCompat(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        list.forEach { a ->
            if (a.kind == "photo" || a.kind == "video") Column(Modifier.width(104.dp).clickable { onView(a) }) {
                Thumb(a, 104.dp)
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

@Composable
private fun Modifier.horizontalScrollCompat(): Modifier = this.then(Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()))

/** «Вопрос: …», «Решено: …», «Ждём от Игоря: …» — each a link to the question itself. */
@Composable
private fun EventLine(task: Task, ev: TopicEvent, nested: Boolean, hl: String?, onQuestion: (String) -> Unit) {
    val c = M.c
    val s = M.s
    val q = ev.ref?.let { r -> task.subtasks.find { it.id == r } }
    val pad = if (nested) Modifier.padding(start = 12.dp, end = 20.dp, top = 5.dp, bottom = 5.dp) else Modifier.padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 6.dp)
    when (ev.kind) {
        "became", "revived" -> Text(if (ev.kind == "became") s.becameTopic else s.revivedTopic,
            style = Type.small.copy(color = c.graphite, fontStyle = FontStyle.Italic, fontSize = 12.5.sp), modifier = pad.padding(start = if (nested) 0.dp else 34.dp))
        else -> {
            if (q == null) return
            val faded = (ev.kind == "q" || ev.kind == "wait") && q.done
            val icon: ImageVector = when (ev.kind) { "q" -> Icons.Outlined.HelpOutline; "wait" -> Icons.Outlined.HourglassEmpty; else -> Icons.Rounded.Check }
            val who = q.from ?: ""
            val prefix = when (ev.kind) { "q" -> s.questionPrefix; "solved" -> s.solvedPrefix; "wait" -> s.waitPrefix(who); else -> s.gotPrefix(who) }
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).landing(hl == "e:" + ev.id, 8).clickable { onQuestion(q.id) }.then(pad), verticalAlignment = Alignment.Top) {
                Icon(icon, null, tint = if (faded) c.faint else c.graphite, modifier = Modifier.padding(top = 2.dp).size(16.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = if (faded) c.faint else c.ink)) { append(prefix) }
                        append(" ")
                        withStyle(SpanStyle(color = if (faded) c.faint else if (ev.kind == "q" || ev.kind == "wait") c.link else c.ink)) { append(q.title) }
                    }, style = Type.body.copy(fontSize = 15.sp))
                    val meta = when {
                        ev.kind == "q" && q.done -> q.doneAt?.let { s.solvedOn(LocalDate.parse(it).dayMonthShort()) }
                        ev.kind == "wait" && q.done -> q.doneAt?.let { s.gotOn(LocalDate.parse(it).dayMonthShort()) }
                        (ev.kind == "q" || ev.kind == "wait") -> q.due?.let { d -> (if (q.isWait) "~" else "") + LocalDate.parse(d).shortLabel().lowercase() + (q.time?.let { ", $it" } ?: "") }
                        else -> null
                    }
                    if (meta != null) Text(meta, style = Type.small.copy(color = if (faded) c.faint else c.graphite, fontSize = 13.sp), modifier = Modifier.padding(top = 2.dp))
                }
                if (!nested) Text(timeOfStamp(ev.at), style = Type.small.copy(color = c.faint, fontSize = 12.sp), modifier = Modifier.padding(top = 3.dp))
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
        if (task.subtasks.isEmpty()) item("empty") { Text(s.noQuestions, style = Type.body.copy(color = c.graphite), modifier = Modifier.padding(20.dp)) }
        if (open.isNotEmpty()) item("h-open") { QHead(s.openCaps, open.size) }
        open.forEach { q -> item(q.id) { QuestionRow(task, q, hl == q.id, vm, onFeed, onEdit, onDate) } }
        if (waits.isNotEmpty()) item("h-wait") { QHead(s.waitingCaps, waits.size) }
        waits.forEach { q -> item(q.id) { QuestionRow(task, q, hl == q.id, vm, onFeed, onEdit, onDate) } }
        if (done.isNotEmpty()) item("h-done") {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { showDone = !showDone }, verticalAlignment = Alignment.CenterVertically) {
                QHead(s.solvedCaps, done.size)
                Icon(Icons.Rounded.KeyboardArrowDown, null, tint = c.faint, modifier = Modifier.padding(top = 12.dp).size(18.dp).rotate(if (showDone) 0f else -90f))
            }
        }
        if (showDone) done.forEach { q -> item(q.id) { QuestionRow(task, q, hl == q.id, vm, onFeed, onEdit, onDate) } }
    }
}

@Composable
private fun QHead(label: String, n: Int) {
    Row(Modifier.padding(start = 20.dp, top = 20.dp, bottom = 6.dp)) {
        SectionLabel(label)
        Text("  $n", style = Type.label.copy(color = M.c.faint))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QuestionRow(task: Task, q: Subtask, lit: Boolean, vm: MainVm, onFeed: (String) -> Unit, onEdit: (Subtask) -> Unit, onDate: (Subtask) -> Unit) {
    val c = M.c
    val s = M.s
    var menu by remember { mutableStateOf(false) }
    val made = task.events.firstOrNull { it.ref == q.id && (it.kind == "q" || it.kind == "wait") }
    Box {
        Column(Modifier.fillMaxWidth().landing(lit, 0).combinedClickable(onClick = { onDate(q) }, onLongClick = { menu = true })) {
            Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 20.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.Top) {
                if (q.isWait && !q.done) Box(Modifier.size(40.dp).clip(CircleShape).clickable { vm.toggleQuestion(task.id, q.id) }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.HourglassEmpty, s.gotIt, tint = c.graphite, modifier = Modifier.size(18.dp))
                } else CheckCircle(q.done, size = 20.dp) { vm.toggleQuestion(task.id, q.id) }
                Column(Modifier.weight(1f).padding(top = 8.dp)) {
                    Text(if (q.isWait) q.title.replaceFirstChar { it.uppercase() } + " — " + (q.from ?: "") else q.title,
                        style = Type.body.copy(fontSize = 16.sp, color = if (q.done) c.faint else c.ink, textDecoration = if (q.done) TextDecoration.LineThrough else null))
                    val parts = buildAnnotatedString {
                        val dateText = when {
                            q.done -> q.doneAt?.let { (if (q.isWait) s.gotOn(LocalDate.parse(it).dayMonthShort()) else s.solvedOn(LocalDate.parse(it).dayMonthShort())) }
                            q.due != null -> (if (q.isWait) "~" else "") + LocalDate.parse(q.due).shortLabel().lowercase() + (q.time?.let { ", $it" } ?: "")
                            else -> s.noDateQ
                        }
                        val late = !q.done && q.due != null && LocalDate.parse(q.due).isBefore(LocalDate.now())
                        withStyle(SpanStyle(color = if (late) c.accent else if (q.done) c.faint else c.ink, fontWeight = FontWeight.Medium)) { append(dateText ?: "") }
                        q.assignee?.let { append("  ·  $it") }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 3.dp)) {
                        Text(parts, style = Type.small.copy(color = c.graphite, fontSize = 13.5.sp))
                        made?.let { ev ->
                            Text("  ·  ", style = Type.small.copy(color = c.faint))
                            Text(s.inFeed(dateOfStamp(ev.at)?.dayMonthShort() ?: ""), style = Type.small.copy(color = c.link, fontSize = 13.5.sp),
                                modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { onFeed(if (ev.entry != null) "n:" + ev.entry else "e:" + ev.id) })
                        }
                    }
                }
            }
            Hairline(Modifier.padding(start = 56.dp))
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

@Composable
private fun MaterialsPage(task: Task, onView: (Attachment) -> Unit) {
    MaterialsGrid(listOf(task), onView, onTopic = null)
}

/** Photos, files and voice notes of [topics] in one grid with a search that reads the text on photos and in voice notes too. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MaterialsGrid(topics: List<Task>, onView: (Attachment) -> Unit, onTopic: ((String) -> Unit)?, header: (@Composable () -> Unit)? = null) {
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
        if (all.isEmpty()) { Text(s.materialsEmpty, style = Type.body.copy(color = c.graphite), modifier = Modifier.padding(20.dp)); return@Column }
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 14.dp).clip(RoundedCornerShape(14.dp)).background(c.card)
            .border(1.dp, c.hairline, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Search, null, tint = c.graphite, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) {
                if (q.isEmpty()) Text(s.searchMaterials, style = Type.body.copy(color = c.faint, fontSize = 15.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                BasicTextField(q, { q = it }, singleLine = true, textStyle = Type.body.copy(color = c.ink, fontSize = 15.sp), cursorBrush = SolidColor(c.link), modifier = Modifier.fillMaxWidth())
            }
        }
        FlowRow(Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("all" to s.all, "photo" to s.kindPhotos, "file" to s.kindDocs, "audio" to s.kindVoice).forEach { (k, l) ->
                val on = kind == k
                Text(l, style = Type.small.copy(color = if (on) c.link else c.graphite, fontSize = 13.5.sp),
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(if (on) c.linkSoft else c.chip).clickable { kind = k }.padding(horizontal = 12.dp, vertical = 6.dp))
            }
        }
        if (shown.isEmpty()) { Text(s.noMaterialsFound, style = Type.body.copy(color = c.graphite), modifier = Modifier.padding(20.dp)); return@Column }
        LazyVerticalGrid(GridCells.Adaptive(104.dp), Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 24.dp),
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
