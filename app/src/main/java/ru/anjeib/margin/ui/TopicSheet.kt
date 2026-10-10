package ru.anjeib.margin.ui

import androidx.compose.animation.animateContentSize
import ru.anjeib.margin.data.dayMonthYear
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.drawBehind

import androidx.compose.material.icons.outlined.CalendarToday

import androidx.compose.ui.focus.focusRequester

import androidx.compose.ui.text.withLink

import ru.anjeib.margin.data.questionRow
import ru.anjeib.margin.data.whenLabel

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
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Add
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

/**
 * One line of the feed (design E): a record — the user's words, plain ink — or a matter — a coloured line with a dash.
 * Each matter appears once, with its state on the line itself; [depth] — how deep it hangs under the line it was added to.
 */
private sealed interface FeedLine {
    val at: String
    val key: String
    val depth: Int
    data class Said(val e: Entry, val removed: Boolean) : FeedLine { override val at = e.at; override val key = "n:" + e.id; override val depth = 0 }
    data class Deal(val q: Subtask, override val at: String, val removed: Boolean, override val depth: Int) : FeedLine { override val key = "d:" + q.id }
}

private fun norm(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

private fun feedOf(t: Task): List<FeedLine> {
    val deals = t.subtasks.map { it to false } + t.removed.map { it to true }
    val notes = t.entries.map { it to false } + t.removedEntries.map { it to true }
    val born = t.events.filter { it.kind == "q" || it.kind == "wait" }.associateBy { it.ref }
    fun bornAt(q: Subtask) = born[q.id]?.at ?: (t.createdAt + "T00:00")
    fun fromEntry(q: Subtask) = born[q.id]?.entry
    val noteIds = notes.map { it.first.id }.toSet()
    val dealIds = deals.map { it.first.id }.toSet()
    val out = mutableListOf<FeedLine>()
    fun deal(q: Subtask, rm: Boolean, depth: Int) {
        out += FeedLine.Deal(q, bornAt(q), rm, depth)
        deals.filter { it.first.parent == q.id }.sortedBy { bornAt(it.first) }.forEach { (c, r) -> deal(c, r || rm, depth + 1) }
    }
    val tops: List<Pair<String, () -> Unit>> = notes.map { (e, rm) ->
        e.at to {
            val made = deals.filter { (q, _) -> q.parent == null && fromEntry(q) == e.id }
            // Words that only named the matter («Установить RClone») are not repeated: the matter's line says it.
            val echo = e.attachments.isEmpty() && !e.isList && made.any { (q, _) -> norm(e.text).let { w -> w == norm(q.title) || (w.contains(norm(q.title)) && w.length <= norm(q.title).length + 12) } }
            if (!echo) out += FeedLine.Said(e, rm)
            // The record's matters — what its words made and what was added under it with «+» — are one block at one level.
            (deals.filter { it.first.parent == e.id }.map { it.first to (it.second || rm) } + made).sortedBy { bornAt(it.first) }.forEach { (q, r) -> deal(q, r, 0) }
        }
    } + deals.filter { (q, _) -> (q.parent == null || (q.parent !in noteIds && q.parent !in dealIds)) && fromEntry(q).let { it == null || it !in noteIds } }
        .map { (q, rm) -> bornAt(q) to { deal(q, rm, 0) } }
    tops.sortedBy { it.first.take(16) }.forEach { it.second() }
    return out
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
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val motion = motionOn()
    val scope = rememberCoroutineScope()
    // One feed; the paperclip narrows it to materials, a tap on the progress — to the matters only.
    var materialsMode by remember(task.id) { mutableStateOf(startTab == 2) }
    var dealsOnly by remember(task.id) { mutableStateOf(false) }
    /** A matter opened in its own card, over the feed. */
    var dealQ by remember(task.id) { mutableStateOf<String?>(null) }
    /** Only the records (what was written), as «5 записей» in the summary asks. */
    var saidOnly by remember(task.id) { mutableStateOf(false) }
    val feedState = rememberLazyListState()
    // A matter opened from the lists lights up in the feed («d:<id>»).
    var hl by remember(task.id) { mutableStateOf(startHl?.let { if (it.contains(':')) it else "d:$it" }) }
    var dialog by remember(task.id) { mutableStateOf<String?>(null) }
    var menu by remember { mutableStateOf(false) }
    var editQ by remember { mutableStateOf<Subtask?>(null) }
    var dateQ by remember { mutableStateOf<Subtask?>(null) }
    var timeQ by remember { mutableStateOf<Subtask?>(null) }
    var viewing by remember { mutableStateOf<Attachment?>(null) }
    // The feed: the chosen line shows «+ − 🔧»; «+» turns the field into «дело под строкой».
    var selected by remember(task.id) { mutableStateOf<String?>(null) }
    var addUnder by remember(task.id) { mutableStateOf<Pair<String, String>?>(null) }
    var editNote by remember { mutableStateOf<Entry?>(null) }
    var focusTick by remember { mutableStateOf(0) }
    val busy by vm.busy.collectAsState()
    val notice by vm.notice.collectAsState()
    val working by vm.working.collectAsState()
    val scopeKey = MainVm.key(Scope.InTask(task.id))
    val feedAll = remember(task) { feedOf(task) }
    val feed = when { dealsOnly -> feedAll.filter { it is FeedLine.Deal }; saidOnly -> feedAll.filter { it is FeedLine.Said }; else -> feedAll }

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
    LaunchedEffect(task.id) {
        val i = hl?.let { k -> feed.indexOfFirst { it.key == k } } ?: -1
        if (i >= 0) feedState.scrollToItem(i + 1) else if (feed.isNotEmpty()) feedState.scrollToItem(feed.size)
    }
    LaunchedEffect(feed.size) { if (feed.isNotEmpty() && hl == null) feedState.animateScrollToItem(feed.size) }
    fun archive() {
        scope.launch {
            view.confirmTick()
            if (motion) fold.animateTo(1f, tween(340, easing = FastOutSlowInEasing))
            vm.archiveTopic(task.id)
            onClose()
        }
    }
    BackHandler { onClose() }
    BackHandler(dealsOnly || saidOnly || materialsMode) { dealsOnly = false; saidOnly = false; materialsMode = false }

    val total = task.subtasks.size
    val doneCount = task.subtasks.count { it.done }
    Column(
        Modifier.fillMaxSize().graphicsLayer {
            scaleY = 1f - 0.92f * fold.value; alpha = 1f - fold.value; transformOrigin = TransformOrigin(0.5f, 1f)
        }.background(c.bg).statusBarsPadding().navigationBarsPadding().imePadding().marginRule(c.accent.copy(alpha = 0.85f), rule.value),
    ) {
        // ---- header: back on the margin, ⋯ on the right; nothing else up there ----
        Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(MarginW), contentAlignment = Alignment.Center) {
                Box(Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, s.backWord, tint = c.ink, modifier = Modifier.size(22.dp))
                }
            }
            Spacer(Modifier.weight(1f))
            Box(Modifier.padding(end = 8.dp)) {
                Box(Modifier.size(44.dp).clip(CircleShape).clickable { menu = true }, contentAlignment = Alignment.Center) { Icon(Icons.Outlined.MoreHoriz, s.moreActions, tint = c.graphite) }
                // An open topic goes to the archive (it keeps everything); deleting is for one already there.
                SoftMenu(menu, { menu = false }) {
                    SoftMenuItem(s.edit) { menu = false; dialog = "rename" }
                    // «что сделано и что ждём» as plain text — for a client, a colleague, a note to self.
                    SoftMenuItem(s.shareWord) { menu = false; shareTopic(ctx, task) }
                    if (!task.done) SoftMenuItem(s.toArchive) { menu = false; dialog = "archive" }
                    else {
                        SoftMenuItem(s.bringBack) { menu = false; vm.reviveTopic(task.id) }
                        SoftMenuItem(s.delete, danger = true) { menu = false; dialog = "delete" }
                    }
                }
            }
        }
        val open = task.subtasks.count { !it.done }
        // Scrolled into the feed, the header folds to one line — the title — and gives the feed the screen; back at the start it opens again.
        val folded by remember { androidx.compose.runtime.derivedStateOf { feedState.firstVisibleItemIndex > 0 || feedState.firstVisibleItemScrollOffset > 60 } }
        Margined {
            Column(Modifier.animateContentSize(tween(220))) {
                Text(task.title, style = if (folded) Type.cardTitle.copy(color = c.ink, fontSize = 19.sp, lineHeight = 24.sp) else Type.display.copy(color = c.ink, fontSize = 32.sp, lineHeight = 38.sp),
                    maxLines = if (folded) 1 else Int.MAX_VALUE, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = if (folded) 8.dp else 0.dp).clip(RoundedCornerShape(8.dp)).clickable { dialog = "rename" })
                if (!folded) {
                // The sphere and age; the materials are a part of the summary below (not a second switch up here).
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    val since = runCatching { LocalDate.parse(task.createdAt) }.getOrNull()?.dayMonthYear()
                    SphereBadge(task.sphere, Modifier.padding(end = 8.dp)) { vm.setSphere(task.id, nextSphere(task.sphere)) }
                    val sinceText = since?.let { if (task.thought && task.subtasks.isEmpty()) s.thoughtSince(it) else s.topicSince(it) } ?: ""
                    val dateSp = dateSpan()
                    Text(buildAnnotatedString {
                        // «тема с 17 авг»: the words quiet, the date in ink.
                        val at = since?.let { sinceText.lastIndexOf(it) } ?: -1
                        if (at >= 0) { append(sinceText.substring(0, at)); withStyle(dateSp) { append(since!!) }; append(sinceText.substring(at + since!!.length)) } else append(sinceText)
                    }, style = Type.small.copy(color = c.graphite, fontSize = 13.sp), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f))
                }
                // The same soft card as the summary on «Сегодня»: how far, how much, who owes what. A tap — the matters only.
                TopicSummary(task, dealsOnly, saidOnly, materialsMode,
                    onDeals = { dealsOnly = !dealsOnly; saidOnly = false; materialsMode = false },
                    onSaid = { saidOnly = !saidOnly; dealsOnly = false; materialsMode = false },
                    onMaterials = { materialsMode = !materialsMode; dealsOnly = false; saidOnly = false })
                if (task.done) Row(Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(RoundedCornerShape(12.dp)).background(c.chip).padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Text(s.inArchiveSince(task.doneAt?.let { runCatching { LocalDate.parse(it).dayMonthShort() }.getOrNull() } ?: ""), style = Type.small.copy(color = c.graphite), modifier = Modifier.weight(1f))
                    Text(s.bringBack, style = Type.small.copy(color = c.link), modifier = Modifier.clickable { vm.reviveTopic(task.id) })
                }
                }
            }
        }
        Hairline(Modifier.padding(start = MarginW))
        Box(Modifier.weight(1f).fillMaxWidth()) {
            // The feed opens at its end; once scrolled away from the start, one quiet way back to it.
            val awayFromStart by remember { androidx.compose.runtime.derivedStateOf { feedState.firstVisibleItemIndex > 2 } }
            run {
                androidx.compose.animation.Crossfade(materialsMode, animationSpec = tween(240), label = "materials") { m ->
                    if (m) MaterialsFeed(task, onView = { viewing = it })
                    else FeedPage(task, feed, feedState, hl, seenAtOpen, working, selected, vm, FeedActions(
                        select = { k -> selected = if (k.isBlank() || selected == k) null else k },
                        addUnder = { id, title -> addUnder = id to title; selected = null; focusTick++ },
                        editDeal = { editQ = it }, dateDeal = { dateQ = it }, editNote = { editNote = it }, view = { viewing = it }, timeDeal = { timeQ = it },
                        openDeal = { dealQ = it.id },
                    ), onArchive = { dialog = "archive" }, onSource = { dialog = "source" })
                }
            }
            // The feed fades in under the header instead of being cut through a line of words.
            Box(Modifier.fillMaxWidth().height(18.dp).background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(c.bg, c.bg.copy(alpha = 0f)))))
            androidx.compose.animation.AnimatedVisibility(awayFromStart && !materialsMode, modifier = Modifier.align(Alignment.TopEnd).padding(top = 6.dp, end = 12.dp),
                enter = androidx.compose.animation.fadeIn(tween(180)), exit = androidx.compose.animation.fadeOut(tween(180))) {
                Text(s.toStart, style = Type.small.copy(color = c.link, fontSize = 13.sp),
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(c.card).clickable { scope.launch { feedState.animateScrollToItem(0) } }
                        .padding(horizontal = 12.dp, vertical = 6.dp))
            }
        }
        // ---- composer: words go to the feed, on «Вопросы» they become a question ----
        Column(Modifier.background(c.bg).fieldRule().padding(horizontal = 14.dp).padding(top = 6.dp, bottom = 10.dp)) {
            notice?.takeIf { it.scopeKey == scopeKey }?.let { nt -> Box(Modifier.padding(start = FieldLead)) { NoticeLine(nt, vm) } }
            val under = addUnder
            SmartComposer(
                vm, if (under != null) "«" + under.second.take(40) + "»" else if (dealsOnly) s.newQuestionHint else s.writeInTopic, busy == scopeKey,
                onSend = { t, f ->
                    if (under != null) { vm.addChildDeal(task.id, under.first, t); addUnder = null }
                    else vm.send(t, scope = Scope.InTask(task.id), files = f, asQuestion = dealsOnly)
                },
                modeLabel = under?.let { s.underLine }, onModeClose = { addUnder = null }, focusKey = focusTick.takeIf { it > 0 }, slash = true,
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
            confirmButton = { TextButton({ dialog = null; onClose(); vm.deleteTask(task.id) }) { Text(s.delete, color = c.accentText) } },
            dismissButton = { TextButton({ dialog = null }) { Text(s.cancel, color = c.graphite) } },
        )
        "source" -> AlertDialog(
            onDismissRequest = { dialog = null }, containerColor = c.card,
            title = { Text(s.youWrote, style = Type.cardTitle.copy(color = c.ink)) },
            text = { Text(task.source.orEmpty().sentence(), style = Type.body.copy(color = c.ink)) },
            confirmButton = { TextButton({ dialog = null }) { Text(s.closeWord2, color = c.graphite) } },
            // Beta: what was written and what it became, for the developer's log.
            dismissButton = { TextButton({ vm.misparsed(task.source.orEmpty(), "card", listOfNotNull(task.title, task.whenLabel()).joinToString(" · ")); dialog = null }) { Text(s.misparsed, color = c.link) } },
        )
    }
    dealQ?.let { id -> DealSheet(task, id, vm, onClose = { dealQ = null }, onOpenTopic = null, onOpenPerson = { dealQ = null; onOpenPerson(it) }) }
    editNote?.let { e -> TextDialog(s.edit, e.text, "", onDismiss = { editNote = null }) { v -> vm.editEntryText("task:${task.id}", e.id, v); editNote = null } }
    editQ?.let { q -> TextDialog(s.edit, q.title, "", onDismiss = { editQ = null }) { v -> vm.renameSubtask(task.id, q.id, v); editQ = null } }
    timeQ?.let { q -> TimeDialog(q.time, onDismiss = { timeQ = null }) { tm -> vm.setTime("q:${task.id}:${q.id}", tm); timeQ = null } }
    dateQ?.let { q -> QuestionDateDialog(q, onDismiss = { dateQ = null }) { d -> vm.setQuestionDue(task.id, q.id, d); dateQ = null } }
}

/** The topic's summary: «2 из 9 сделано · 5 записей · 3 файла», the progress, and who owes what inside it. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun TopicSummary(task: Task, dealsOnly: Boolean, saidOnly: Boolean, materials: Boolean, onDeals: () -> Unit, onSaid: () -> Unit, onMaterials: () -> Unit) {
    val c = M.c
    val s = M.s
    val total = task.subtasks.size
    val done = task.subtasks.count { it.done }
    val files = task.materials().size
    // Each part of the summary is its own filter: the matters, the records, the materials; the chosen one in petrol with «✕».
    @Composable fun part(text: String, on: Boolean, onClick: () -> Unit) = Text(if (on) "$text  ✕" else text,
        style = Type.small.copy(color = if (on) c.link else c.graphite, fontSize = 14.sp, fontFeatureSettings = "tnum"),
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).semantics { selected = on }.clickable(onClick = onClick).padding(vertical = 9.dp, horizontal = 2.dp))
    Column(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 12.dp).clip(RoundedCornerShape(16.dp)).background(c.linkSoft.copy(alpha = if (c.dark) 0.5f else 0.6f))
        .padding(horizontal = 12.dp, vertical = 8.dp)) {
        // The parts wrap whole onto a second line, never a word of one.
        val parts = listOfNotNull(
            (if (total > 0) Triple(s.progressSolved(done, total), dealsOnly, onDeals) else null),
            task.entries.size.takeIf { it > 0 }?.let { Triple(s.nEntries(it), saidOnly, onSaid) },
            files.takeIf { it > 0 }?.let { Triple(s.nMaterials(it), materials, onMaterials) },
        )
        DotFlow(parts.size, separator = { Text("  ·  ", style = Type.small.copy(color = c.graphite, fontSize = 14.sp)) }) { i -> val (t, on, f) = parts[i]; part(t, on, f) }
        // The bar is 3 dp to look at, but the finger gets the whole strip around it.
        if (total > 0) Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).clickable(onClickLabel = s.progressSolved(done, total), onClick = onDeals).padding(vertical = 8.dp)) {
            TopicProgress(done, total)
        }
    }
}

/** A quiet icon in the topic's header with an optional raised count; [on] — filled petrol. */
@Composable
private fun HeaderIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, count: Int = 0, on: Boolean, tint: Color = M.c.graphite, onClick: () -> Unit) {
    val c = M.c
    val bg by androidx.compose.animation.animateColorAsState(if (on) c.link else Color.Transparent, tween(200), label = "hi")
    Row(Modifier.padding(start = 4.dp).clip(RoundedCornerShape(50)).background(bg).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.Top) {
        Icon(icon, label, tint = if (on) c.bg else tint, modifier = Modifier.size(19.dp))
        if (count > 0) Text(count.toString(), style = Type.small.copy(color = if (on) c.bg else c.graphite, fontSize = 10.sp), modifier = Modifier.padding(start = 1.dp))
    }
}

/** «Вопросы² ›» — the door to the questions page, at the end of the progress line. */
@Composable
private fun QuestionsPill(n: Int, on: Boolean, onClick: () -> Unit) {
    val c = M.c
    val bg by androidx.compose.animation.animateColorAsState(if (on) c.link else Color.Transparent, tween(220), label = "qp")
    val fg = if (on) c.bg else c.link
    Row(Modifier.clip(RoundedCornerShape(50)).background(bg).border(1.dp, c.link, RoundedCornerShape(50)).clickable(onClick = onClick)
        .padding(start = 14.dp, end = 10.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        // The count as on «Сегодня 3»: on the same line, smaller and quieter than the word.
        Text(buildAnnotatedString {
            append(M.s.questionsTab)
            if (n > 0) withStyle(SpanStyle(fontSize = 13.sp, fontWeight = FontWeight.Normal, color = if (on) c.bg.copy(alpha = 0.75f) else c.graphite)) { append(" $n") }
        }, style = Type.small.copy(color = fg, fontSize = 14.sp, fontWeight = FontWeight.Medium))
        Text(if (on) " ‹" else " ›", style = Type.small.copy(color = fg, fontSize = 15.sp))
    }
}

/** The feed with only what was attached: a search that reads text on photos and in voice notes, kinds, still on the thread. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MaterialsFeed(task: Task, onView: (Attachment) -> Unit) {
    val c = M.c
    val s = M.s
    var q by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf("all") }
    fun Attachment.words() = listOfNotNull(name, caption, ocr, transcript, contact?.name).joinToString(" ").lowercase().replace(" ", "")
    val needle = q.trim().lowercase().replace(" ", "")
    fun ok(a: Attachment) = (kind == "all" || (kind == "photo" && (a.kind == "photo" || a.kind == "video")) || (kind == "file" && a.kind == "file") || (kind == "audio" && a.kind == "audio")) &&
        (needle.isEmpty() || a.words().contains(needle))
    val entries = task.entries.filter { e -> e.attachments.any(::ok) }.reversed()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp)) {
        item("search") {
            Margined {
                Column {
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.card).border(1.dp, c.hairline, RoundedCornerShape(14.dp))
                        .padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Search, null, tint = c.graphite, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Box(Modifier.weight(1f)) {
                            if (q.isEmpty()) Text(s.searchMaterials, style = Type.body.copy(color = c.graphite, fontSize = 15.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            BasicTextField(q, { q = it }, singleLine = true, textStyle = Type.body.copy(color = c.ink, fontSize = 15.sp), cursorBrush = SolidColor(c.link), modifier = Modifier.fillMaxWidth())
                        }
                    }
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 10.dp, bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("all" to s.all, "photo" to s.kindPhotos, "file" to s.kindDocs, "audio" to s.kindVoice).forEach { (k, l) ->
                            val on = kind == k
                            Text(l, style = Type.small.copy(color = if (on) c.link else c.graphite, fontSize = 13.sp),
                                modifier = Modifier.clip(RoundedCornerShape(50)).background(if (on) c.linkSoft else c.chip).clickable { kind = k }.padding(horizontal = 11.dp, vertical = 6.dp))
                        }
                    }
                }
            }
        }
        if (entries.isEmpty()) item("none") { Margined { Text(if (task.materials().isEmpty()) s.materialsEmpty else s.noMaterialsFound, style = Type.body.copy(color = c.graphite), modifier = Modifier.padding(top = 16.dp)) } }
        entries.forEach { e ->
            item(e.id) {
                Margined(Modifier.animateItem().padding(top = 10.dp), node = c.accent.copy(alpha = 0.8f), nodeY = 12.dp, margin = { MarginText(dateOfStamp(e.at)?.dayMonthShort() ?: "") }) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        e.attachments.filter(::ok).forEach { a -> BigMaterial(a, onView) }
                    }
                }
            }
        }
    }
}

/** One material at full width: a photo large, a file or a voice note as a quiet card with its transcript. */
@Composable
fun BigMaterial(a: Attachment, onView: (Attachment) -> Unit) {
    val c = M.c
    if (a.kind == "photo" || a.kind == "video") Column(Modifier.fillMaxWidth().clickable { onView(a) }) {
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(14.dp))) { Thumb(a, maxWidth) }
        a.caption?.let { Text(it, style = Type.small.copy(color = c.graphite, fontSize = 13.sp), modifier = Modifier.padding(top = 5.dp)) }
    } else Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.card).border(1.dp, c.hairline, RoundedCornerShape(14.dp)).clickable { onView(a) }
        .padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(c.chip), contentAlignment = Alignment.Center) { Icon(kindIcon(a.kind), null, tint = c.graphite, modifier = Modifier.size(20.dp)) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(attachmentLabel(a).ifBlank { a.name }, style = Type.body.copy(color = c.ink, fontSize = 15.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            (a.transcript ?: a.ocr)?.takeIf { it.isNotBlank() }?.let { Text(it, style = Type.small.copy(color = c.graphite, fontSize = 13.sp), maxLines = 2, overflow = TextOverflow.Ellipsis) }
        }
    }
}

// ---------------- the feed: a typeset list on the margin (design E) ----------------

/** What a line of the feed can do, handed down from the screen. */
private class FeedActions(
    val select: (String) -> Unit,
    val addUnder: (id: String, title: String) -> Unit,
    val editDeal: (Subtask) -> Unit,
    val dateDeal: (Subtask) -> Unit,
    val editNote: (Entry) -> Unit,
    val view: (Attachment) -> Unit,
    val timeDeal: (Subtask) -> Unit = {},
    /** A tap on a matter: its own card. */
    val openDeal: (Subtask) -> Unit = {},
)

@Composable
private fun FeedPage(task: Task, feed: List<FeedLine>, state: androidx.compose.foundation.lazy.LazyListState, hl: String?, seen: Set<String>, working: Set<String>,
                     selected: String?, vm: MainVm, act: FeedActions, onArchive: () -> Unit, onSource: () -> Unit) {
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
        // As on «Сегодня»: the day on the margin (a number in serif, the month, the weekday) once for each day, a rule between days.
        var lastDay: LocalDate? = null
        feed.forEachIndexed { idx, line ->
            // Matters that follow one another are one block under one badge: «ДЕЛО» or «ДЕЛА · 3».
            // A block is one day's run of matters: a new day starts a new block with its own badge.
            val lineDay = dateOfStamp(line.at)
            val startsBlock = line is FeedLine.Deal && (feed.getOrNull(idx - 1) !is FeedLine.Deal || (line.depth == 0 && lineDay != lastDay))
            val badge = if (startsBlock) {
                val n = feed.drop(idx).withIndex().takeWhile { (i, it) -> it is FeedLine.Deal && (i == 0 || it.depth > 0 || dateOfStamp(it.at) == lineDay) }.count { it.value.depth == 0 }
                if (n == 1) s.stepOne.uppercase() else s.dealsBadge.uppercase() + " · " + n
            } else null
            val day = dateOfStamp(line.at)
            val firstOfDay = line.depth == 0 && day != null && day != lastDay
            val newDayAfter = firstOfDay && lastDay != null
            if (firstOfDay) lastDay = day
            item(line.key) {
                val fresh = line.key in freshKeys
                // Lines slide into place but never fade: a line fading out under «только дела» kept its day on the margin
                // on top of the next one's (review 0.13, defect 13). New lines have their own arrival.
                Column(Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null).arrive(fresh, freshKeys.indexOf(line.key)).landing(hl == line.key, 10)) {
                    if (newDayAfter) Hairline(Modifier.padding(start = MarginW, top = 8.dp, bottom = 4.dp))
                    val dayMark = if (firstOfDay) day else null
                    when (line) {
                        is FeedLine.Said -> NoteLine(task, line.e, line.removed, dayMark, fresh, working, vm, act)
                        is FeedLine.Deal -> DealLine(task, line.q, line.removed, line.depth, dayMark, badge, vm, act)
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

/** One action in the long-press bubble: a small icon and the words. */
@Composable
private fun ToolItem(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, danger: Boolean = false, onClick: () -> Unit) {
    val c = M.c
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = if (danger) c.accentText else c.graphite, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(12.dp))
        Text(text, style = Type.body.copy(color = if (danger) c.accentText else c.ink, fontSize = 15.sp))
    }
}

/** «+ − 🔧» at the end of the chosen line. */
@Composable
private fun LineTools(tint: Color, onAdd: (() -> Unit)?, onRemove: () -> Unit, onEdit: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        @Composable fun tool(icon: androidx.compose.ui.graphics.vector.ImageVector, f: () -> Unit) =
            Box(Modifier.size(34.dp).clip(CircleShape).clickable(onClick = f), contentAlignment = Alignment.Center) { Icon(icon, null, tint = tint, modifier = Modifier.size(19.dp)) }
        onAdd?.let { tool(Icons.Rounded.Add, it) }
        tool(Icons.Rounded.Remove, onRemove)
        tool(Icons.Outlined.Build, onEdit)
    }
}

/** A removed line, tapped: bring it back or delete it for good. */
@Composable
private fun RemovedMenu(open: Boolean, onDismiss: () -> Unit, onRestore: () -> Unit, onPurge: () -> Unit) {
    val s = M.s
    SoftMenu(open, onDismiss) {
        SoftMenuItem(s.restoreWord2) { onDismiss(); onRestore() }
        SoftMenuItem(s.purgeWord, danger = true) { onDismiss(); onPurge() }
    }
}

/** The user's words: plain ink on the paper, no card; files under them. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NoteLine(task: Task, e: Entry, removed: Boolean, day: LocalDate?, fresh: Boolean, working: Set<String>, vm: MainVm, act: FeedActions) {
    val c = M.c
    val s = M.s
    var menu by remember(e.id) { mutableStateOf(false) }
    var tools by remember(e.id) { mutableStateOf(false) }
    // A tap puts the cursor right into the record — edited on the page, not in a window.
    var editing by remember(e.id) { mutableStateOf(false) }
    var hadFocus by remember(e.id) { mutableStateOf(false) }
    var draft by remember(e.id, e.text) { mutableStateOf(e.text) }
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    fun save() { if (draft.isNotBlank() && draft.trim() != e.text.trim()) vm.editEntryText("task:${task.id}", e.id, draft.trim()) }
    LaunchedEffect(editing) { if (editing) runCatching { focus.requestFocus() } }
    LaunchedEffect(draft) { if (editing && draft != e.text) { kotlinx.coroutines.delay(900); save() } }
    androidx.compose.runtime.DisposableEffect(e.id) { onDispose { save() } }
    val sel by androidx.compose.animation.animateColorAsState(if (tools) c.chip else Color.Transparent, tween(180), label = "sel")
    Margined(Modifier.padding(top = 6.dp), margin = day?.let { d -> { DayMargin(d, LocalDate.now(), false, strong = true) } }) {
        Box {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(sel)
                .combinedClickable(onClick = { if (removed) menu = true else editing = true }, onLongClick = { if (removed) menu = true else tools = true }).padding(vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        // What it is and when: a record — the user's own words; the matters that came of them hang under it.
                        Row(Modifier.padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(s.recordBadge.uppercase(), style = Type.label.copy(color = c.graphite, fontSize = 10.5.sp, letterSpacing = 1.1.sp),
                                modifier = Modifier.clip(RoundedCornerShape(50)).background(c.chip).padding(horizontal = 8.dp, vertical = 2.dp))
                            timeOfStamp(e.at).takeIf { it.isNotBlank() && it != "00:00" }?.let { Text(it, style = dateStyle(12.5.sp), modifier = Modifier.padding(start = 8.dp)) }
                        }
                        if (editing) androidx.compose.foundation.text.BasicTextField(draft, { draft = it },
                            textStyle = Type.body.copy(color = c.ink, fontSize = 15.5.sp), cursorBrush = androidx.compose.ui.graphics.SolidColor(c.link),
                            modifier = Modifier.fillMaxWidth().focusRequester(focus).onFocusChanged { f ->
                                // The field reports «not focused» as it appears: leave editing only once the cursor has been in it.
                                if (f.isFocused) hadFocus = true else if (editing && hadFocus) { save(); editing = false; hadFocus = false }
                            })
                        else if (e.text.isNotBlank()) ListAwareText(e.text, Type.body.copy(color = if (removed) c.faint else c.ink, fontSize = 15.5.sp,
                            textDecoration = if (removed) androidx.compose.ui.text.style.TextDecoration.LineThrough else null))
                        // A list in a record is a list of thoughts, not matters: bullets, no ticks.
                        e.items.forEach { Row(Modifier.padding(vertical = 1.dp)) {
                            Text("•", style = Type.body.copy(color = c.graphite, fontSize = 15.5.sp), modifier = Modifier.width(22.dp))
                            Text(it.title, style = Type.body.copy(color = if (removed) c.faint else c.ink, fontSize = 15.5.sp), modifier = Modifier.weight(1f))
                        } }
                    }
                    if (removed) Text(s.removedWord, style = Type.small.copy(color = c.faint, fontSize = 12.sp), modifier = Modifier.padding(start = 8.dp, top = 6.dp))
                    // Long press: a small bubble — a matter under the record, edit, remove.
                    Box {
                        SoftMenu(tools, { tools = false }) {
                            ToolItem(Icons.Rounded.Add, s.underLine) { tools = false; act.addUnder(e.id, e.text.ifBlank { e.attachments.firstOrNull()?.let { attachmentLabel(it) }?.ifBlank { null } ?: "…" }) }
                            ToolItem(Icons.Outlined.Build, s.edit) { tools = false; editing = true }
                            ToolItem(Icons.Rounded.Remove, s.delete, danger = true) { tools = false; vm.removeRecord(task.id, e.id) }
                        }
                    }
                }
                if (e.attachments.isNotEmpty() && !removed) {
                    Spacer(Modifier.height(8.dp))
                    MaterialRow(e.attachments, fresh, working, act.view)
                }
            }
            RemovedMenu(menu, { menu = false }, onRestore = { vm.restoreRecord(task.id, e.id) }, onPurge = { vm.purgeRecord(task.id, e.id) })
        }
    }
}

/**
 * A matter: a coloured line with a dash — petrol to do, amber when we wait for someone; done, the dash becomes a tick and
 * the words are struck through in green; removed — grey, struck, «удалено». Lines added under it hang indented, lighter.
 */
@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun DealLine(task: Task, q: Subtask, removed: Boolean, depth: Int, day: LocalDate?, badge: String?, vm: MainVm, act: FeedActions) {
    // The same row as on «Сегодня»: the kind mark, the circle, the hour as a petrol link, the words in ink, a quiet line under.
    val c = M.c
    val s = M.s
    val view = LocalView.current
    val motion = motionOn()
    var menu by remember(q.id) { mutableStateOf(false) }
    var tools by remember(q.id) { mutableStateOf(false) }
    val strike = remember(q.id) { Animatable(if (q.done || removed) 1f else 0f) }
    LaunchedEffect(q.done, removed) { strike.animateTo(if (q.done || removed) 1f else 0f, tween(if (motion) 320 else 0, easing = FastOutSlowInEasing)) }
    val sel by androidx.compose.animation.animateColorAsState(if (tools) c.chip else Color.Transparent, tween(180), label = "sel")
    val due = q.due?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    val today = LocalDate.now()
    // Late as on «Сегодня»: a day gone, or today's hour already past.
    val late = !q.done && !removed && due != null && (due.isBefore(today) || (due == today && q.time != null && q.time!! < java.time.LocalTime.now().toString().take(5)))
    fun tick() {
        if (removed) return
        if (!q.done) view.confirmTick()
        val next = q.slots.firstOrNull { !it.done }
        if (next != null) vm.toggleSlot("q:${task.id}:${q.id}", next.id) else vm.toggleQuestion(task.id, q.id)
    }
    // Nested matters hang from their parent's circle: a thin guide down from it and a short elbow to each child.
    val guideX = 29.dp + 30.dp * (depth - 1).coerceAtLeast(0)
    val indent = if (depth == 0) 0.dp else guideX + 6.dp
    Margined(Modifier.padding(top = if (badge != null) 10.dp else 0.dp), margin = day?.let { d -> { DayMargin(d, LocalDate.now(), false, strong = true) } }) {
        Column {
        // The block's badge: petrol, as the matters are what is to be done.
        if (badge != null) Text(badge, style = Type.label.copy(color = c.link, fontSize = 10.5.sp, letterSpacing = 1.1.sp),
            modifier = Modifier.padding(bottom = 2.dp).clip(RoundedCornerShape(50)).background(c.linkSoft).padding(horizontal = 8.dp, vertical = 2.dp))
        Box(Modifier.drawBehind {
            if (depth > 0) {
                val x = guideX.toPx(); val y = 20.dp.toPx(); val col = c.hairline.copy(alpha = 1f)
                drawLine(col, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.2.dp.toPx())
                drawLine(col, Offset(x, y), Offset(indent.toPx() + 2.dp.toPx(), y), strokeWidth = 1.2.dp.toPx())
            }
        }) {
            Row(Modifier.fillMaxWidth().padding(start = indent).clip(RoundedCornerShape(10.dp)).background(sel)
                .combinedClickable(onClick = { if (removed) menu = true else act.openDeal(q) }, onLongClick = { if (removed) menu = true else tools = true }).padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
                if (depth == 0) KindMark(if (q.done || removed) null else task.questionRow(q))
                // A removed matter has no bagel at all — three states read by shape: ring open, disc done, nothing removed.
                Box(Modifier.size(if (depth == 0) 26.dp else 22.dp, 24.dp), contentAlignment = Alignment.CenterStart) { if (!removed) CheckCircle(q.done, size = if (depth == 0) 18.dp else 15.dp, onToggle = ::tick) }
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    val hour = hourColor(due, q.time, today)
                    val title = buildAnnotatedString {
                        // Several days carry their hours in the line of days below — not again before the words.
                        if (q.time != null && !q.done && !removed && q.slots.isEmpty()) {
                            withLink(androidx.compose.ui.text.LinkAnnotation.Clickable("time", androidx.compose.ui.text.TextLinkStyles(SpanStyle(color = hour, fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum"))) { act.timeDeal(q) }) { append(q.time!!) }
                            append("   ")
                        }
                        append(q.title)
                    }
                    Text(title, style = Type.body.copy(fontSize = if (depth == 0) 15.5.sp else 14.5.sp, color = if (q.done || removed) c.graphite else c.ink), maxLines = 3, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 1.dp).drawWithContent {
                            drawContent()
                            if (strike.value > 0f) { val y = 11.dp.toPx(); drawLine(c.faint, Offset(0f, y), Offset(size.width * strike.value, y), strokeWidth = 1.3.dp.toPx()) }
                        })
                    // Several days: each, ticked ones in green.
                    if (q.slots.isNotEmpty() && !removed) Text(buildAnnotatedString {
                        q.slots.forEachIndexed { i, sl ->
                            if (i > 0) append("  ·  ")
                            val d = runCatching { LocalDate.parse(sl.date) }.getOrNull()
                            withStyle(SpanStyle(color = if (sl.done) c.ok else c.ink, fontWeight = if (sl.done) null else FontWeight.Medium, fontFeatureSettings = "tnum", textDecoration = if (sl.done) androidx.compose.ui.text.style.TextDecoration.LineThrough else null)) {
                                append((if (sl.done) "✓ " else "") + (d?.dayMonthShort() ?: sl.date) + (sl.time?.let { ", $it" } ?: ""))
                            }
                        }
                    }, style = Type.small.copy(fontSize = 13.sp), modifier = Modifier.padding(top = 1.dp))
                    // The quiet line: when (late in terracotta), who owes it, when it was done.
                    if (!removed) {
                        val whenText = if (q.slots.isEmpty() && !q.done) due?.let { if (it.isBefore(today)) s.lateSince(it.dayMonthShort()) else (if (it == today) s.quickToday.lowercase() else if (it == today.plusDays(1)) s.quickTomorrow.lowercase() else it.dayMonthShort()) } else null
                        val rest = listOfNotNull(q.from?.let { s.promiseToYou(it) }, q.assignee,
                            if (q.done) q.doneAt?.let { d -> LocalDate.parse(d).dayMonthShort().let { if (q.isWait) s.gotOn(it) else s.solvedOn(it) } } else null)
                        val dateSp = dateSpan()
                        if (whenText != null || rest.isNotEmpty()) Text(buildAnnotatedString {
                            whenText?.let { withStyle(if (late) dateSp.copy(color = c.accentText) else dateSp) { append(it) } }
                            if (whenText != null && rest.isNotEmpty()) append("  ·  ")
                            append(rest.joinToString("  ·  "))
                        }, style = Type.small.copy(color = c.graphite, fontSize = 13.sp), modifier = Modifier.padding(top = 1.dp))
                    }
                }
                if (removed) Text(s.removedWord, style = Type.small.copy(color = c.faint, fontSize = 12.sp), modifier = Modifier.padding(start = 8.dp, top = 3.dp))
                // Long press: a small bubble — a matter under it, edit, the day, remove.
                Box {
                    SoftMenu(tools, { tools = false }) {
                        ToolItem(Icons.Rounded.Add, s.underLine) { tools = false; act.addUnder(q.id, q.title) }
                        ToolItem(Icons.Outlined.Build, s.edit) { tools = false; act.editDeal(q) }
                        ToolItem(Icons.Outlined.CalendarToday, s.dueWord) { tools = false; act.dateDeal(q) }
                        ToolItem(Icons.Rounded.Remove, s.delete, danger = true) { tools = false; vm.removeDeal(task.id, q.id) }
                    }
                }
            }
            RemovedMenu(menu, { menu = false }, onRestore = { vm.restoreDeal(task.id, q.id) }, onPurge = { vm.purgeDeal(task.id, q.id) })
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
                    Icon(Icons.Rounded.KeyboardArrowDown, null, tint = c.graphite, modifier = Modifier.padding(top = 14.dp).size(18.dp).rotate(if (showDone) 0f else -90f))
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
        Text("  $n", style = Type.label.copy(color = M.c.graphite))
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
        q.due != null -> (if (q.isWait) "~" else "") + LocalDate.parse(q.due).dayMonthShort() + (q.time?.let { "\n$it" } ?: "")
        else -> null
    }
    Box {
        Margined(Modifier.landing(lit, 0).combinedClickable(onClick = { onDate(q) }, onLongClick = { menu = true }),
            margin = marginText?.let { m -> { Box(Modifier.padding(top = if (!q.done && isSoon(q.due?.let { runCatching { LocalDate.parse(it) }.getOrNull() })) SoonLift + 6.dp else 0.dp)) { MarginText(m, late = late, petrol = q.isWait && !q.done, faint = q.done) } } }) {
            Column {
                if (!q.done) SoonBadge(q.due?.let { runCatching { LocalDate.parse(it) }.getOrNull() }, Modifier.padding(top = 6.dp))
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
                    Box(Modifier.size(28.dp).clip(CircleShape).clickable(onClick = ::settle), contentAlignment = Alignment.Center) {
                        if (q.isWait && !q.done) {
                            if (turn.value < 0.98f) Box(Modifier.graphicsLayer { alpha = 1f - turn.value; translationX = -12f * turn.value }) { ArrowGlyph(c.link, 19.dp, incoming = true) } else TickGlyph(c.ok, 20.dp)
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
                                if (parts.isNotEmpty()) Text("  ·  ", style = Type.small.copy(color = c.graphite))
                                Text(s.inFeed(dateOfStamp(ev.at)?.dayMonthShort() ?: ""), style = Type.small.copy(color = c.link, fontSize = 13.5.sp),
                                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { onFeed("d:" + q.id) })
                            }
                        }
                    }
                }
                Hairline(Modifier.padding(start = 38.dp))
            }
        }
        SoftMenu(menu, { menu = false }) {
            DropdownMenuItem(text = { Text(s.edit, style = Type.body) }, onClick = { menu = false; onEdit(q) })
            DropdownMenuItem(text = { Text(s.questionDateQ, style = Type.body) }, onClick = { menu = false; onDate(q) })
            DropdownMenuItem(text = { Text(s.delete, style = Type.body.copy(color = c.accentText)) }, onClick = { menu = false; vm.deleteSubtask(task.id, q.id) })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun QuestionDateDialog(q: Subtask, onDismiss: () -> Unit, onSave: (String?) -> Unit) {
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

/**
 * «Материалы»: photos, files and voice notes of every topic and matter, by day as on «Сегодня» — the day on the margin,
 * a link to where each came from, the files under it. Search hides behind the magnifier; kinds as quiet chips.
 */
@Composable
fun MaterialsGrid(topics: List<Task>, onView: (Attachment) -> Unit, onTopic: ((String) -> Unit)?, header: (@Composable (trailing: @Composable () -> Unit) -> Unit)? = null) {
    val c = M.c
    val s = M.s
    var q by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var kind by remember { mutableStateOf("all") }
    val all = topics.flatMap { t -> t.materials().map { (e, a) -> Triple(t, e, a) } }
    fun Attachment.words() = listOfNotNull(name, caption, ocr, transcript, contact?.name).joinToString(" ").lowercase().replace(" ", "")
    val needle = q.trim().lowercase().replace(" ", "")
    val shown = all.filter { (_, _, a) ->
        (kind == "all" || (kind == "photo" && (a.kind == "photo" || a.kind == "video")) || (kind == "file" && a.kind == "file") || (kind == "audio" && a.kind == "audio")) &&
            (needle.isEmpty() || a.words().contains(needle))
    }
    val days = shown.groupBy { (_, e, _) -> e.at.take(10) }.toSortedMap(compareByDescending { it })
    val today = LocalDate.now()
    Box(Modifier.fillMaxSize().screenRule()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            header?.let { h -> item("head") { h {
                Box(Modifier.size(40.dp).clip(CircleShape).background(if (searching) c.linkSoft else Color.Transparent).clickable { searching = !searching; if (!searching) q = "" },
                    contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Search, s.searchWord, tint = if (searching) c.link else c.graphite, modifier = Modifier.size(21.dp)) }
            } } }
            if (all.isEmpty()) item("none") { Margined { Text(s.materialsEmpty, style = Type.body.copy(color = c.graphite), modifier = Modifier.padding(top = 10.dp)) } }
            else item("search") {
                Margined {
                    Column {
                        androidx.compose.animation.AnimatedVisibility(searching, enter = androidx.compose.animation.expandVertically(tween(220)) + androidx.compose.animation.fadeIn(),
                            exit = androidx.compose.animation.shrinkVertically(tween(180)) + androidx.compose.animation.fadeOut()) {
                            val focus = remember { androidx.compose.ui.focus.FocusRequester() }
                            LaunchedEffect(Unit) { kotlinx.coroutines.delay(150); runCatching { focus.requestFocus() } }
                            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(14.dp)).background(c.card).border(1.dp, c.hairline, RoundedCornerShape(14.dp))
                                .padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.weight(1f)) {
                                    if (q.isEmpty()) Text(s.searchMaterials, style = Type.body.copy(color = c.graphite, fontSize = 15.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    BasicTextField(q, { q = it }, singleLine = true, textStyle = Type.body.copy(color = c.ink, fontSize = 15.sp), cursorBrush = SolidColor(c.link),
                                        modifier = Modifier.fillMaxWidth().focusRequester(focus))
                                }
                            }
                        }
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("all" to s.all, "photo" to s.kindPhotos, "file" to s.kindDocs, "audio" to s.kindVoice).forEach { (k, l) ->
                                val on = kind == k
                                Text(l, style = Type.small.copy(color = if (on) c.link else c.graphite, fontSize = 14.sp, fontWeight = if (on) FontWeight.Medium else FontWeight.Normal),
                                    modifier = Modifier.clip(RoundedCornerShape(50)).background(if (on) c.linkSoft else Color.Transparent)
                                        .border(1.dp, if (on) Color.Transparent else c.hairline, RoundedCornerShape(50)).clickable { kind = k }.padding(horizontal = 12.dp, vertical = 5.dp))
                            }
                        }
                    }
                }
            }
            if (all.isNotEmpty() && shown.isEmpty()) item("nf") { Margined { Text(s.noMaterialsFound, style = Type.body.copy(color = c.graphite), modifier = Modifier.padding(top = 14.dp)) } }
            days.forEach { (d, list) ->
                val date = runCatching { LocalDate.parse(d) }.getOrNull()
                item("day$d") {
                    Column(Modifier.animateItem()) {
                        Margined(Modifier.padding(top = 6.dp, bottom = 10.dp), margin = { date?.let { DayMargin(it, today, false, strong = true) } }) {
                            Column {
                                // One link per topic or matter that day, its files under it.
                                list.groupBy { it.first.id }.values.forEach { g ->
                                    val t = g.first().first
                                    Row(Modifier.padding(top = 8.dp, bottom = 6.dp).clip(RoundedCornerShape(6.dp)).clickable(enabled = onTopic != null) { onTopic?.invoke(t.id) }.padding(vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically) {
                                        Text("↳", style = Type.small.copy(color = c.link, fontSize = 13.sp))
                                        Spacer(Modifier.width(5.dp))
                                        if (t.topic && !(t.thought && t.subtasks.isEmpty())) TopicGlyph(c.link, 12.dp) else if (t.thought) ThoughtGlyph(c.link, 12.dp) else if (t.done) TickGlyph(c.ok, 12.dp) else ArrowGlyph(c.link, 12.dp)
                                        Spacer(Modifier.width(5.dp))
                                        Text(t.title, style = Type.small.copy(color = c.link, fontSize = 13.5.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    // Pictures in a grid of three (no empty two thirds of a line); documents and voice as rows with their name whole.
                                    val pics = g.filter { (_, _, a) -> a.kind == "photo" || a.kind == "video" }
                                    val docs = g - pics.toSet()
                                    @Composable fun matched(a: Attachment) {
                                        // Found by the words inside, not the name: say what matched.
                                        if (needle.isNotEmpty() && !a.name.lowercase().replace(" ", "").contains(needle)) (a.ocr ?: a.transcript)?.let {
                                            Text("«" + it.take(60) + "»", style = Type.small.copy(color = c.graphite, fontSize = 11.5.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                    if (pics.isNotEmpty()) androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
                                        val side = (maxWidth - 16.dp) / 3
                                        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(10.dp), maxItemsInEachRow = 3) {
                                            pics.forEach { (_, e, a) ->
                                                Column(Modifier.width(side).clip(RoundedCornerShape(10.dp)).clickable { onView(a) }) {
                                                    Thumb(a, side)
                                                    (a.caption ?: timeOfStamp(e.at).takeIf { it.isNotBlank() && it != "00:00" })?.let {
                                                        Text(it, style = Type.small.copy(color = c.graphite, fontSize = 12.sp), maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
                                                    }
                                                    matched(a)
                                                }
                                            }
                                        }
                                    }
                                    docs.forEach { (_, e, a) ->
                                        Row(Modifier.fillMaxWidth().padding(top = if (pics.isNotEmpty()) 8.dp else 0.dp).clip(RoundedCornerShape(10.dp)).background(c.card)
                                            .border(1.dp, c.hairline, RoundedCornerShape(10.dp)).clickable { onView(a) }.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.Top) {
                                            Icon(kindIcon(a.kind), null, tint = c.graphite, modifier = Modifier.padding(top = 1.dp).size(20.dp))
                                            Spacer(Modifier.width(10.dp))
                                            Column(Modifier.weight(1f)) {
                                                Text(a.caption ?: a.name.ifBlank { attachmentLabel(a) }, style = Type.small.copy(color = c.ink, fontSize = 14.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                                                Text(listOfNotNull(attachmentLabel(a).takeIf { it.isNotBlank() && it != a.name }, timeOfStamp(e.at).takeIf { it.isNotBlank() && it != "00:00" }).joinToString("  ·  "),
                                                    style = Type.small.copy(color = c.graphite, fontSize = 12.sp))
                                                matched(a)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        Hairline(Modifier.padding(start = MarginW))
                    }
                }
            }
        }
    }
}

/** A topic as text for someone else: done, waiting for, still to do, and the last records. */
fun topicText(t: Task): String {
    val s = ru.anjeib.margin.i18n.L.s
    fun flat(l: List<Subtask>): List<Subtask> = l.flatMap { listOf(it) + flat(it.children) }
    val all = flat(t.subtasks)
    fun day(d: String?) = d?.let { runCatching { LocalDate.parse(it.take(10)).dayMonthShort() }.getOrNull() }
    return buildString {
        appendLine(t.title)
        all.filter { it.done }.takeIf { it.isNotEmpty() }?.let { l -> appendLine(); appendLine(s.exportDone); l.forEach { appendLine("✓ " + it.title + (day(it.doneAt)?.let { d -> " ($d)" } ?: "")) } }
        all.filter { !it.done && it.from != null }.takeIf { it.isNotEmpty() }?.let { l -> appendLine(); appendLine(s.exportWaiting); l.forEach { appendLine("← " + it.title + " — " + it.from + (day(it.due)?.let { d -> ", $d" } ?: "")) } }
        all.filter { !it.done && it.from == null }.takeIf { it.isNotEmpty() }?.let { l -> appendLine(); appendLine(s.exportOpen); l.forEach { appendLine("— " + it.title + (day(it.due)?.let { d -> " ($d)" } ?: "")) } }
        t.entries.filter { it.text.isNotBlank() }.takeLast(3).takeIf { it.isNotEmpty() }?.let { l -> appendLine(); appendLine(s.exportNotes); l.forEach { appendLine((day(it.at)?.let { d -> "$d: " } ?: "") + it.text.trim()) } }
    }.trim()
}

private fun shareTopic(ctx: android.content.Context, t: Task) {
    val i = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain")
        .putExtra(android.content.Intent.EXTRA_SUBJECT, t.title).putExtra(android.content.Intent.EXTRA_TEXT, topicText(t))
    runCatching { ctx.startActivity(android.content.Intent.createChooser(i, t.title).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
