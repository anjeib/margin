package ru.anjeib.margin.ui

import androidx.compose.ui.draw.shadow

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
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
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
    var titleTick by remember { mutableStateOf(0) }
    // The topic's page is written on at its end (owner, 12 окт); the bar below holds «+», the microphone and «send».
    val pageDraft = remember(task.id) { mutableStateOf("") }
    val pageFiles = remember(task.id) { androidx.compose.runtime.mutableStateListOf<Attachment>() }
    // What is written on the page is kept by itself — keyboard hidden or the topic closed (owner, 13 окт: no «send»).
    val pageSend = remember(task.id) { PageSend() }
    androidx.compose.runtime.DisposableEffect(task.id) { onDispose { pageSend.send() } }
    var writeTick by remember { mutableStateOf(0) }
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
    // A topic opens at its start — the title, the summary, the first days (owner, 10 окт); only a line asked for
    // (a search hit) is scrolled to. When something new is written here, the feed follows it to the end.
    LaunchedEffect(task.id) {
        val i = hl?.let { k -> feed.indexOfFirst { it.key == k } } ?: -1
        if (i >= 0) feedState.scrollToItem(i + 1)
    }
    val openedWith = remember(task.id) { feed.size }
    LaunchedEffect(feed.size) { if (feed.size > openedWith && hl == null) feedState.animateScrollToItem(feed.size) }
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
                    SoftMenuItem(s.rename) { menu = false; titleTick++ }
                    // «что сделано и что ждём» as plain text — for a client, a colleague, a note to self.
                    SoftMenuItem(s.sharePdf) { menu = false; shareTopic(ctx, task) }
                    if (!task.done) SoftMenuItem(s.toArchive) { menu = false; dialog = "archive" }
                    else {
                        SoftMenuItem(s.bringBack) { menu = false; vm.reviveTopic(task.id) }
                        SoftMenuItem(s.delete, danger = true) { menu = false; dialog = "delete" }
                    }
                }
            }
        }
        val open = task.subtasks.count { !it.done }
        var titleV by remember(task.id) { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(task.title)) }
        var titleHad by remember { mutableStateOf(false) }
        val titleFocus = remember { androidx.compose.ui.focus.FocusRequester() }
        val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
        // A new name from elsewhere (the assistant, a sync) shows unless the user is typing one.
        LaunchedEffect(task.title) { if (!titleHad && titleV.text != task.title) titleV = androidx.compose.ui.text.input.TextFieldValue(task.title) }
        LaunchedEffect(titleTick) { if (titleTick > 0) { kotlinx.coroutines.delay(120); runCatching { titleFocus.requestFocus() }; titleV = titleV.copy(selection = androidx.compose.ui.text.TextRange(titleV.text.length)) } }
        fun saveTitle() {
            val cur = vm.data.value.tasks.find { it.id == task.id } ?: return
            val t = titleV.text.trim()
            if (t.isBlank()) titleV = androidx.compose.ui.text.input.TextFieldValue(cur.title)
            else if (t != cur.title) vm.saveTask(cur.copy(title = t.sentence()))
        }
        // Scrolled into the feed, the header folds to one line — the title — and gives the feed the screen; back at the start it opens again.
        val folded by remember { androidx.compose.runtime.derivedStateOf { feedState.firstVisibleItemIndex > 0 || feedState.firstVisibleItemScrollOffset > 60 } }
        Margined {
            Column(Modifier.animateContentSize(tween(220))) {
                // The name is written right here (owner, 13 окт): a tap puts the cursor in it, «Готово» or leaving keeps it.
                if (folded) Text(task.title, style = Type.cardTitle.copy(color = c.ink, fontSize = 19.sp, lineHeight = 24.sp),
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(bottom = 8.dp))
                else androidx.compose.foundation.text.BasicTextField(titleV, { titleV = it.copy(text = it.text.replace("\n", " ")) },
                    textStyle = Type.display.copy(color = c.ink, fontSize = 32.sp, lineHeight = 38.sp), cursorBrush = androidx.compose.ui.graphics.SolidColor(c.link),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done, capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { saveTitle(); focusManager.clearFocus() }),
                    modifier = Modifier.fillMaxWidth().focusRequester(titleFocus).onFocusChanged { f -> if (f.isFocused) titleHad = true else if (titleHad) { titleHad = false; saveTitle() } })
                if (!folded) {
                // v8: under the title one line in Margin's voice — since when, how far — and the sphere as a chip;
                // then words as tabs, the same as in «Обзор»: everything, the matters, the records, the files.
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    val since = runCatching { LocalDate.parse(task.createdAt) }.getOrNull()?.dayMonthYear()
                    val sinceText = since?.let { if (task.thought && task.subtasks.isEmpty()) s.thoughtSince(it) else s.topicSince(it) }
                    Voice(listOfNotNull(sinceText, total.takeIf { it > 0 }?.let { s.progressSolved(doneCount, it) }).joinToString("  ·  "), Modifier.weight(1f))
                    SpherePick(task.sphere, Modifier.padding(start = 8.dp)) { vm.setSphere(task.id, it) }
                }
                val files = task.materials().size
                val tab = when { dealsOnly -> "deals"; saidOnly -> "said"; materialsMode -> "files"; else -> "all" }
                TextTabs(listOfNotNull("all" to s.allTab, total.takeIf { it > 0 }?.let { "deals" to s.dealsBadge + " " + it },
                    task.entries.size.takeIf { it > 0 }?.let { "said" to s.records + " " + it }, files.takeIf { it > 0 }?.let { "files" to s.filesWord + " " + it }),
                    tab, { k -> dealsOnly = k == "deals"; saidOnly = k == "said"; materialsMode = k == "files" }, Modifier.padding(top = 8.dp, bottom = 10.dp))
                if (task.done) Panel(Modifier.padding(bottom = 10.dp)) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(s.inArchiveSince(task.doneAt?.let { runCatching { LocalDate.parse(it).dayMonthShort() }.getOrNull() } ?: ""), style = Type.body.copy(color = c.graphite, fontSize = 15.sp), modifier = Modifier.weight(1f))
                        PaperLink(s.bringBack) { vm.reviveTopic(task.id) }
                    }
                }
                }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            // The feed opens at its end; once scrolled away from the start, one quiet way back to it.
            val awayFromStart by remember { androidx.compose.runtime.derivedStateOf { feedState.firstVisibleItemIndex > 2 } }
            run {
                androidx.compose.animation.Crossfade(materialsMode, animationSpec = tween(240), label = "materials") { m ->
                    if (m) MaterialsFeed(task, onView = { viewing = it })
                    else FeedPage(task, feed, feedState, hl, seenAtOpen, working, selected, vm, pageDraft.takeIf { addUnder == null }, pageFiles, writeTick.takeIf { it > 0 },
                        if (dealsOnly) s.newQuestionHint else s.writeInTopic, { pageSend.send() }, FeedActions(
                        select = { k -> selected = if (k.isBlank() || selected == k) null else k },
                        addUnder = { id, title -> addUnder = id to title; selected = null; focusTick++ },
                        editDeal = { editQ = it }, dateDeal = { dateQ = it }, editNote = { editNote = it }, view = { viewing = it }, timeDeal = { timeQ = it },
                        openDeal = { dealQ = it.id },
                    ), onArchive = { dialog = "archive" }, onSource = { dialog = "source" })
                }
            }
            // The feed fades in under the header instead of being cut through a line of words.
            Box(Modifier.fillMaxWidth().height(18.dp).background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(c.bg, c.bg.copy(alpha = 0f)))))
            // At the bottom right, by the thumb, floating over the feed — not over the first words under the title.
            androidx.compose.animation.AnimatedVisibility(awayFromStart && !materialsMode, modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 10.dp, end = 14.dp),
                enter = androidx.compose.animation.fadeIn(tween(180)), exit = androidx.compose.animation.fadeOut(tween(180))) {
                Text(s.toStart, style = Type.small.copy(color = c.link, fontSize = 13.sp),
                    modifier = Modifier.shadow(4.dp, RoundedCornerShape(50)).clip(RoundedCornerShape(50)).background(c.card).clickable { scope.launch { feedState.animateScrollToItem(0) } }
                        .padding(horizontal = 12.dp, vertical = 6.dp))
            }
        }
        // ---- composer: words go to the feed, on «Вопросы» they become a question ----
        Column(Modifier.background(c.bg).fieldRule().padding(horizontal = 14.dp).padding(top = 6.dp, bottom = 10.dp)) {
            notice?.takeIf { it.scopeKey == scopeKey }?.let { nt -> Box(Modifier.padding(start = FieldLead)) { NoticeLine(nt, vm) } }
            val under = addUnder
            SmartComposer(
                vm, if (under != null) "«" + under.second.take(40) + "»" else s.writeOnPage, busy == scopeKey,
                onSend = { t, f ->
                    if (under != null) { vm.addChildDeal(task.id, under.first, t); addUnder = null }
                    else vm.send(t, scope = Scope.InTask(task.id), files = f, asQuestion = dealsOnly)
                },
                modeLabel = under?.let { s.underLine }, onModeClose = { addUnder = null }, focusKey = focusTick.takeIf { it > 0 }, slash = true,
                // Owner, 14 окт: as in a card — marks with the page's «+», files with the paperclip, only «@» typed.
                marksOnPlus = under == null && !materialsMode,
                // A tap on the bar (or words just dictated): to the end of the page, the cursor in its line.
                pageText = pageDraft.takeIf { under == null && !materialsMode }, pageFiles = pageFiles.takeIf { under == null && !materialsMode }, pageSend = pageSend,
                // What the phone reads in the words being written, quietly over the microphone: «дело · пт 16 окт · ждём от Марины».
                // In a topic plain words become a record, so only a reading with something in it (a day, a person, a wait) is said.
                preview = { vm.previewOf(it)?.takeIf { p -> p.contains("·") } },
                onWrite = { scope.launch { feedState.layoutInfo.totalItemsCount.takeIf { it > 0 }?.let { feedState.animateScrollToItem(it - 1) }; writeTick++ } },
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
                        SearchGlyph(c.graphite, 19.dp)
                        Spacer(Modifier.width(10.dp))
                        Box(Modifier.weight(1f)) {
                            if (q.isEmpty()) Text(s.searchMaterials, style = Type.body.copy(color = c.graphite, fontSize = 15.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            BasicTextField(q, { q = it }, singleLine = true, textStyle = Type.body.copy(color = c.ink, fontSize = 15.sp), cursorBrush = SolidColor(c.link), modifier = Modifier.fillMaxWidth())
                        }
                    }
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 10.dp, bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("all" to s.all, "photo" to s.kindPhotos, "file" to s.kindDocs, "audio" to s.kindVoice).forEach { (k, l) -> Chip(l, kind == k, onClick = { kind = k }) }
                    }
                }
            }
        }
        if (entries.isEmpty()) item("none") { Margined(Modifier.padding(top = 16.dp)) { Voice(if (task.materials().isEmpty()) s.materialsEmpty else s.noMaterialsFound) } }
        entries.forEach { e ->
            item(e.id) {
                Margined(Modifier.animateItem().padding(top = 10.dp), margin = { MarginText(dateOfStamp(e.at)?.let { dayWord(it) } ?: "") }) {
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
                     selected: String?, vm: MainVm, draft: androidx.compose.runtime.MutableState<String>?,
                     files: androidx.compose.runtime.snapshots.SnapshotStateList<Attachment>, writeKey: Any?, writeHint: String, onKeep: () -> Unit,
                     act: FeedActions, onArchive: () -> Unit, onSource: () -> Unit) {
    val c = M.c
    val s = M.s
    // New lines arrive one after another, in the order they were written.
    val freshKeys = feed.filter { it.key !in seen }.map { it.key }
    LazyColumn(Modifier.fillMaxSize(), state = state, contentPadding = PaddingValues(top = 6.dp, bottom = 24.dp)) {
        item("origin") {
            if (task.source != null) {
                val born = task.history.firstOrNull { it.kind == "created" }?.at?.let { runCatching { LocalDateTime.parse(it.take(16)) }.getOrNull() }
                val stamp = born?.let { it.toLocalDate().dayMonthShort() + ", " + it.toLocalTime().toString().take(5) } ?: LocalDate.parse(task.createdAt).dayMonthShort()
                Margined(Modifier.padding(bottom = 8.dp)) { Voice("↳ " + s.fromMessage(stamp), link = true, onClick = onSource) }
            } else Spacer(Modifier.height(4.dp))
        }
        // v8: each day of the feed is one panel — its date big on the margin once, the hours of the records under it.
        val days = feed.map { dateOfStamp(it.at) }
        feed.forEachIndexed { idx, line ->
            val day = days[idx]
            val first = idx == 0 || days[idx - 1] != day
            val last = idx == feed.lastIndex || days[idx + 1] != day
            val seat = when { first && last -> Seat.Single; first -> Seat.First; last -> Seat.Last; else -> Seat.Middle }
            val hour = timeOfStamp(line.at).takeIf { line is FeedLine.Said && it.isNotBlank() && it != "00:00" }
            val margin: (@Composable () -> Unit)? = when {
                first && day != null -> ({ Column(horizontalAlignment = Alignment.CenterHorizontally) { DayMargin(day, LocalDate.now(), false, strong = true); hour?.let { MarginNote(it, modifier = Modifier.padding(top = 2.dp)) } } })
                hour != null -> ({ RowMargin(hour) })
                else -> null
            }
            item(line.key) {
                val fresh = line.key in freshKeys
                // Lines slide into place but never fade: a line fading out under «только дела» kept its day on the margin
                // on top of the next one's (review 0.13, defect 13). New lines have their own arrival.
                Column(Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null).arrive(fresh, freshKeys.indexOf(line.key)).landing(hl == line.key, 10)
                    .padding(top = if (first && idx > 0) 14.dp else 0.dp)) {
                    when (line) {
                        is FeedLine.Said -> NoteLine(task, line.e, line.removed, seat, margin, fresh, working, vm, act)
                        is FeedLine.Deal -> DealLine(task, line.q, line.removed, line.depth, seat, margin, vm, act)
                    }
                }
            }
        }
        if (task.noNextStep) item("end") {
            Margined(Modifier.padding(top = 18.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Voice(s.allSettled, Modifier.weight(1f))
                    PaperLink(s.toArchive, onClick = onArchive)
                }
            }
        }
        // The page goes on below the last day: write here, the bar sends it (as the old field did: records, matters, marks).
        if (draft != null) item("write") { PageLine(draft, writeHint, writeKey, Modifier.padding(top = 10.dp), after = feed.lastOrNull()?.at, vm = vm, files = files, pasteTo = "task:${task.id}", onKeep = onKeep, plus = Plus.Marks) }
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

/** The user's words on the day's panel: plain ink, no badge — the hour is on the margin; files under them. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NoteLine(task: Task, e: Entry, removed: Boolean, seat: Seat, margin: (@Composable () -> Unit)?, fresh: Boolean, working: Set<String>, vm: MainVm, act: FeedActions) {
    val c = M.c
    val s = M.s
    var menu by remember(e.id) { mutableStateOf(false) }
    var tools by remember(e.id) { mutableStateOf(false) }
    // A tap puts the cursor right into the record — edited on the page, not in a window.
    var editing by remember(e.id) { mutableStateOf(false) }
    var hadFocus by remember(e.id) { mutableStateOf(false) }
    var draftValue by remember(e.id, e.text) { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(e.text, androidx.compose.ui.text.TextRange(e.text.length))) }
    val draft = draftValue.text
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    // Reads the field as it is now (also when the page closes), not as it was when this line was first drawn.
    fun save() { val d = draftValue.text; if (d.isNotBlank() && d.trim() != e.text.trim()) vm.editEntryText("task:${task.id}", e.id, d.trim()) }
    LaunchedEffect(editing) { if (editing) runCatching { focus.requestFocus() } }
    LaunchedEffect(draft) { if (editing && draft != e.text) { kotlinx.coroutines.delay(900); save() } }
    androidx.compose.runtime.DisposableEffect(e.id) { onDispose { save() } }
    val sel by androidx.compose.animation.animateColorAsState(if (tools) c.chip else Color.Transparent, tween(180), label = "sel")
    Margined(margin = margin) {
        PanelPiece(seat, dividerStart = 16.dp) {
        Box {
            Column(Modifier.fillMaxWidth().background(sel)
                .combinedClickable(onClick = { if (removed) menu = true else editing = true }, onLongClick = { if (removed) menu = true else tools = true }).padding(horizontal = 16.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        if (editing) androidx.compose.foundation.text.BasicTextField(draftValue, { draftValue = continueList(draftValue, it) },
                            textStyle = Type.body.copy(color = c.ink, fontSize = 15.5.sp, lineHeight = 22.sp), cursorBrush = androidx.compose.ui.graphics.SolidColor(c.link),
                            modifier = Modifier.fillMaxWidth().focusRequester(focus).onFocusChanged { f ->
                                // The field reports «not focused» as it appears: leave editing only once the cursor has been in it.
                                if (f.isFocused) hadFocus = true else if (editing && hadFocus) { save(); editing = false; hadFocus = false }
                            })
                        else if (e.text.isNotBlank()) ListAwareText(e.text, Type.body.copy(color = if (removed) c.faint else c.ink, fontSize = 15.5.sp, lineHeight = 22.sp,
                            textDecoration = if (removed) androidx.compose.ui.text.style.TextDecoration.LineThrough else null))
                        // A list in a record is a list of thoughts, not matters: bullets, no ticks.
                        e.items.forEach { Row(Modifier.padding(vertical = 1.dp)) {
                            Text("•", style = Type.body.copy(color = c.graphite, fontSize = 15.5.sp), modifier = Modifier.width(22.dp))
                            Text(it.title, style = Type.body.copy(color = if (removed) c.faint else c.ink, fontSize = 15.5.sp), modifier = Modifier.weight(1f))
                        } }
                    }
                    if (removed) Text(s.removedWord, style = Type.small.copy(color = c.faint, fontSize = 12.sp), modifier = Modifier.padding(start = 8.dp, top = 3.dp))
                    // Long press (owner, 13 окт): copy, move, delete — a tap already edits it in place.
                    Box {
                        SoftMenu(tools, { tools = false }) {
                            ToolItem(Icons.Outlined.ContentCopy, s.copyWord) { tools = false; vm.copyRecord("task:${task.id}", e.id) }
                            ToolItem(Icons.AutoMirrored.Outlined.DriveFileMove, s.moveWord) { tools = false; vm.moveRecord("task:${task.id}", e.id) }
                            ToolItem(Icons.Rounded.Remove, s.delete, danger = true) { tools = false; vm.removeRecord(task.id, e.id) }
                        }
                    }
                }
                if (e.attachments.isNotEmpty() && !removed) {
                    Spacer(Modifier.height(10.dp))
                    MaterialRow(e.attachments, fresh, working, act.view, owner = "task:${task.id}", entryId = e.id, vm = vm)
                }
            }
            RemovedMenu(menu, { menu = false }, onRestore = { vm.restoreRecord(task.id, e.id) }, onPurge = { vm.purgeRecord(task.id, e.id) })
        }
        }
    }
}

/**
 * A matter on the day's panel: the circle (the arrow when we wait for someone), the words, one quiet line — when, who owes it,
 * when it was done; removed — grey, struck, «удалено». Matters added under it hang indented on a thin guide.
 */
@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun DealLine(task: Task, q: Subtask, removed: Boolean, depth: Int, seat: Seat, margin: (@Composable () -> Unit)?, vm: MainVm, act: FeedActions) {
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
    val late = !q.done && !removed && due != null && due.isBefore(today)
    fun tick() {
        if (removed) return
        if (!q.done) view.confirmTick()
        val next = q.slots.firstOrNull { !it.done }
        if (next != null) vm.toggleSlot("q:${task.id}:${q.id}", next.id) else vm.toggleQuestion(task.id, q.id)
    }
    // Nested matters hang from their parent's circle: a thin guide down from it and a short elbow to each child.
    val step = 26.dp
    val guideX = MarkCol / 2 + step * (depth - 1).coerceAtLeast(0)
    val indent = step * depth
    Margined(margin = margin) {
        PanelPiece(seat) {
        Box(Modifier.drawBehind {
            if (depth > 0) {
                val x = guideX.toPx(); val y = 26.dp.toPx(); val col = c.hairline
                drawLine(col, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.2.dp.toPx())
                drawLine(col, Offset(x, y), Offset((indent + 8.dp).toPx(), y), strokeWidth = 1.2.dp.toPx())
            }
        }) {
            Row(Modifier.fillMaxWidth().background(sel).padding(start = indent)
                .combinedClickable(onClick = { if (removed) menu = true else act.openDeal(q) }, onLongClick = { if (removed) menu = true else tools = true })
                .padding(end = 14.dp, top = RowTop - 2.dp, bottom = RowTop - 2.dp), verticalAlignment = Alignment.Top) {
                // A removed matter has no circle at all — three states read by shape: ring open, disc done, nothing removed;
                // something we wait for wears the arrow, which closes it as «получено».
                Box(Modifier.size(MarkCol, 26.dp), contentAlignment = Alignment.Center) {
                    if (!removed) {
                        if (q.from != null && !q.done) Box(Modifier.clip(CircleShape).clickable(onClick = ::tick)) { PromiseMark(incoming = true, size = if (depth == 0) 28.dp else 24.dp) }
                        else CheckCircle(q.done, size = if (depth == 0) 21.dp else 18.dp, onToggle = ::tick)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f).padding(top = 2.dp)) {
                    Text(q.title, style = Type.body.copy(fontSize = if (depth == 0) 16.sp else 15.sp, lineHeight = 22.sp, color = if (q.done || removed) c.graphite else c.ink), maxLines = 3, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.drawWithContent {
                            drawContent()
                            if (strike.value > 0f) { val y = 11.dp.toPx(); drawLine(c.faint, Offset(0f, y), Offset(size.width * strike.value, y), strokeWidth = 1.3.dp.toPx()) }
                        })
                    // Several days: each, ticked ones struck.
                    if (q.slots.isNotEmpty() && !removed) Text(buildAnnotatedString {
                        q.slots.forEachIndexed { i, sl ->
                            if (i > 0) append("  ·  ")
                            val d = runCatching { LocalDate.parse(sl.date) }.getOrNull()
                            withStyle(SpanStyle(color = if (sl.done) c.graphite else c.ink, fontFeatureSettings = "tnum", textDecoration = if (sl.done) androidx.compose.ui.text.style.TextDecoration.LineThrough else null)) {
                                append((d?.let { dayWord(it, today) } ?: sl.date) + (sl.time?.let { " $it" } ?: ""))
                            }
                        }
                    }, style = Type.small.copy(fontSize = 13.5.sp), modifier = Modifier.padding(top = 3.dp))
                    // The quiet line: when (late in terracotta) with its hour, who owes it, when it was done.
                    if (!removed) {
                        val whenText = if (q.slots.isEmpty() && !q.done) due?.let { (if (late) s.lateSince(it.dayMonthShort()) else dayWord(it, today)) + (q.time?.let { t -> " $t" } ?: "") } else null
                        val rest = listOfNotNull(q.from?.let { s.promiseToYou(it) }, q.assignee,
                            if (q.done) q.doneAt?.let { d -> LocalDate.parse(d).dayMonthShort().let { if (q.isWait) s.gotOn(it) else s.solvedOn(it) } } else null)
                        if (whenText != null || rest.isNotEmpty()) PencilText(buildAnnotatedString {
                            whenText?.let { w -> if (late) pencil { withStyle(SpanStyle(color = c.ink)) { append(w) } } else withStyle(SpanStyle(color = c.ink)) { append(w) } }
                            if (whenText != null && rest.isNotEmpty()) append("  ·  ")
                            append(rest.joinToString("  ·  "))
                        }, style = Type.small.copy(color = c.graphite, fontSize = 13.5.sp), modifier = Modifier.padding(top = 3.dp))
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
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MaterialRow(list: List<Attachment>, fresh: Boolean, working: Set<String>, onView: (Attachment) -> Unit,
                        owner: String? = null, entryId: String? = null, vm: MainVm? = null) {
    val c = M.c
    val s = M.s
    val motion = motionOn()
    // Long press on a photo or a file (owner, 13 окт): its caption, copy, move, delete.
    var menuFor by remember { mutableStateOf<String?>(null) }
    var captionFor by remember { mutableStateOf<String?>(null) }
    @Composable fun menu(a: Attachment) {
        if (vm == null || owner == null || entryId == null) return
        SoftMenu(menuFor == a.id, { menuFor = null }) {
            ToolItem(Icons.Outlined.Edit, s.captionWord) { menuFor = null; captionFor = a.id }
            ToolItem(Icons.Outlined.ContentCopy, s.copyWord) { menuFor = null; vm.copyRecord(owner, entryId, a.id) }
            ToolItem(Icons.AutoMirrored.Outlined.DriveFileMove, s.moveWord) { menuFor = null; vm.moveRecord(owner, entryId, a.id) }
            ToolItem(Icons.Rounded.Remove, s.delete, danger = true) { menuFor = null; vm.removeAttachment(owner, entryId, a.id) }
        }
    }
    /** The caption, written right under the photo or the file — a tap on it edits it too. */
    @Composable fun caption(a: Attachment, width: androidx.compose.ui.unit.Dp?) {
        val editingIt = captionFor == a.id
        if (!editingIt) {
            a.caption?.let { Text(it, style = Type.small.copy(color = c.graphite, fontSize = 12.5.sp), maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp).then(if (vm != null) Modifier.clickable { captionFor = a.id } else Modifier)) }
            return
        }
        var v by remember(a.id) { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(a.caption.orEmpty(), androidx.compose.ui.text.TextRange(a.caption.orEmpty().length))) }
        val focus = remember { androidx.compose.ui.focus.FocusRequester() }
        var had by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
        fun done() { if (owner != null && entryId != null && v.text.trim() != a.caption.orEmpty()) vm?.setCaption(owner, entryId, a.id, v.text); captionFor = null }
        Box(Modifier.padding(top = 4.dp).then(if (width != null) Modifier.width(width) else Modifier.width(160.dp))) {
            if (v.text.isEmpty()) Text(s.captionWord + "…", style = Type.small.copy(color = c.faint, fontSize = 12.5.sp))
            androidx.compose.foundation.text.BasicTextField(v, { v = it }, textStyle = Type.small.copy(color = c.ink, fontSize = 12.5.sp),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(c.link),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { done() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus).onFocusChanged { f -> if (f.isFocused) had = true else if (had) done() })
        }
    }
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        list.forEach { a ->
            if (a.kind == "photo" || a.kind == "video") Column(Modifier.width(132.dp).combinedClickable(onClick = { onView(a) }, onLongClick = { if (vm != null) menuFor = a.id })) {
                val sharp = remember(a.id) { Animatable(if (fresh && motion) 0f else 1f) }
                LaunchedEffect(a.id) { sharp.animateTo(1f, tween(700, easing = FastOutSlowInEasing)) }
                Box(Modifier.size(132.dp, 100.dp).clip(RoundedCornerShape(12.dp))) {
                    Box(Modifier.blur((14 * (1f - sharp.value)).dp).graphicsLayer { alpha = 0.4f + 0.6f * sharp.value }) { Thumb(a, 132.dp) }
                    if ("ocr:${a.id}" in working) ScanLine()
                }
                caption(a, 132.dp)
                Box { menu(a) }
            }
            else Column {
                Row(Modifier.clip(RoundedCornerShape(10.dp)).background(c.chip).combinedClickable(onClick = { onView(a) }, onLongClick = { if (vm != null) menuFor = a.id })
                    .padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(kindIcon(a.kind), null, tint = c.graphite, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(attachmentLabel(a), style = Type.small.copy(color = c.ink, fontSize = 13.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                caption(a, null)
                Box { menu(a) }
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
            if (all.isEmpty()) item("none") { Margined(Modifier.padding(top = 10.dp)) { Voice(s.materialsEmpty) } }
            else item("search") {
                Margined {
                    Column {
                        androidx.compose.animation.AnimatedVisibility(searching, enter = androidx.compose.animation.expandVertically(tween(220)) + androidx.compose.animation.fadeIn(),
                            exit = androidx.compose.animation.shrinkVertically(tween(180)) + androidx.compose.animation.fadeOut()) {
                            val focus = remember { androidx.compose.ui.focus.FocusRequester() }
                            LaunchedEffect(Unit) { kotlinx.coroutines.delay(150); runCatching { focus.requestFocus() } }
                            Box(Modifier.padding(bottom = 10.dp).bleed(PanelBleed)) { SearchField(q, { q = it }, s.searchMaterials, Modifier.focusRequester(focus)) }
                        }
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("all" to s.all, "photo" to s.kindPhotos, "file" to s.kindDocs, "audio" to s.kindVoice).forEach { (k, l) -> Chip(l, kind == k, onClick = { kind = k }) }
                        }
                    }
                }
            }
            if (all.isNotEmpty() && shown.isEmpty()) item("nf") { Margined(Modifier.padding(top = 14.dp)) { Voice(s.noMaterialsFound) } }
            days.forEach { (d, list) ->
                val date = runCatching { LocalDate.parse(d) }.getOrNull()
                item("day$d") {
                    Column(Modifier.animateItem()) {
                        Margined(Modifier.padding(top = 14.dp), margin = { date?.let { DayMargin(it, today, false, strong = true) } }) {
                            Panel { Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp)) {
                                // One link per topic or matter that day, its files under it.
                                list.groupBy { it.first.id }.values.forEach { g ->
                                    val t = g.first().first
                                    Row(Modifier.padding(top = 6.dp, bottom = 6.dp).clip(RoundedCornerShape(6.dp)).clickable(enabled = onTopic != null) { onTopic?.invoke(t.id) }.padding(vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically) {
                                        if (t.thought && t.subtasks.isEmpty()) NoteGlyph(c.link, 15.dp) else TopicGlyph(c.link, 14.dp)
                                        Spacer(Modifier.width(8.dp))
                                        Text(t.title + "  ›", style = Type.small.copy(color = c.link, fontSize = 14.sp, fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                                                // Only the picture has round corners — the caption under it must not lose its first letters to them.
                                                Column(Modifier.width(side).clickable { onView(a) }) {
                                                    Box(Modifier.clip(RoundedCornerShape(10.dp))) { Thumb(a, side) }
                                                    (a.caption ?: timeOfStamp(e.at).takeIf { it.isNotBlank() && it != "00:00" })?.let {
                                                        Text(it, style = Type.small.copy(color = c.graphite, fontSize = 12.sp), maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
                                                    }
                                                    matched(a)
                                                }
                                            }
                                        }
                                    }
                                    docs.forEach { (_, e, a) ->
                                        Row(Modifier.fillMaxWidth().padding(top = if (pics.isNotEmpty()) 8.dp else 0.dp).clip(RoundedCornerShape(10.dp)).background(c.bg)
                                            .clickable { onView(a) }.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.Top) {
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
                            } }
                        }
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
    // A PDF made here, on the phone, into the system's «Поделиться» — mail, a messenger, Drive (owner, 11 окт).
    // If the page can't be made, the topic goes as text, as before.
    val pdf = runCatching { ru.anjeib.margin.data.TopicPdf.make(ctx, t) }.getOrNull()
    val i = if (pdf != null) {
        val uri = androidx.core.content.FileProvider.getUriForFile(ctx, ctx.packageName + ".files", pdf)
        android.content.Intent(android.content.Intent.ACTION_SEND).setType("application/pdf")
            .putExtra(android.content.Intent.EXTRA_STREAM, uri).putExtra(android.content.Intent.EXTRA_SUBJECT, t.title)
            .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .apply { clipData = android.content.ClipData.newRawUri(t.title, uri) }
    } else android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain")
        .putExtra(android.content.Intent.EXTRA_SUBJECT, t.title).putExtra(android.content.Intent.EXTRA_TEXT, topicText(t))
    // Not back into Margin itself.
    runCatching { ctx.startActivity(android.content.Intent.createChooser(i, t.title)
        .putExtra(android.content.Intent.EXTRA_EXCLUDE_COMPONENTS, arrayOf(android.content.ComponentName(ctx, ru.anjeib.margin.ForwardActivity::class.java)))
        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
