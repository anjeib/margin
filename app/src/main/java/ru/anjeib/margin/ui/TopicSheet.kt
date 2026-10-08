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
            // What was added under the words with «+» hangs right under them; what the words made follows at their level.
            deals.filter { it.first.parent == e.id }.sortedBy { bornAt(it.first) }.forEach { (q, r) -> deal(q, r || rm, 1) }
            made.sortedBy { bornAt(it.first) }.forEach { (q, r) -> deal(q, r, 0) }
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
    val motion = motionOn()
    val scope = rememberCoroutineScope()
    // One feed; the paperclip narrows it to materials, a tap on the progress — to the matters only.
    var materialsMode by remember(task.id) { mutableStateOf(startTab == 2) }
    var dealsOnly by remember(task.id) { mutableStateOf(false) }
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
    val feed = if (dealsOnly) feedAll.filter { it is FeedLine.Deal } else feedAll

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
    BackHandler(dealsOnly || materialsMode) { dealsOnly = false; materialsMode = false }

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
                Box(Modifier.size(40.dp).clip(CircleShape).clickable { menu = true }, contentAlignment = Alignment.Center) { Icon(Icons.Outlined.MoreHoriz, null, tint = c.graphite) }
                // An open topic goes to the archive (it keeps everything); deleting is for one already there.
                SoftMenu(menu, { menu = false }) {
                    SoftMenuItem(s.edit) { menu = false; dialog = "rename" }
                    if (!task.done) SoftMenuItem(s.toArchive) { menu = false; dialog = "archive" }
                    else {
                        SoftMenuItem(s.bringBack) { menu = false; vm.reviveTopic(task.id) }
                        SoftMenuItem(s.delete, danger = true) { menu = false; dialog = "delete" }
                    }
                }
            }
        }
        val open = task.subtasks.count { !it.done }
        val files = task.materials().size
        Margined {
            Column {
                Text(task.title, style = Type.title.copy(color = c.ink, fontSize = 28.sp, lineHeight = 34.sp),
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { dialog = "rename" })
                // The label and age on the left; materials and «в архив» as two quiet icons on the right of the same line.
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    val since = runCatching { LocalDate.parse(task.createdAt) }.getOrNull()?.dayMonthShort()
                    SphereBadge(task.sphere, Modifier.padding(end = 8.dp)) { vm.setSphere(task.id, nextSphere(task.sphere)) }
                    Text(since?.let { if (task.thought && task.subtasks.isEmpty()) s.thoughtSince(it) else s.topicSince(it) } ?: "",
                        style = Type.small.copy(color = c.graphite, fontSize = 13.sp), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f))
                    HeaderIcon(Icons.Outlined.AttachFile, s.materialsTab, count = files, on = materialsMode) {
                        materialsMode = !materialsMode; dealsOnly = false
                    }
                }
                // The progress; a tap shows the matters only («только дела»), another tap — the whole feed again.
                if (total > 0) Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp).clip(RoundedCornerShape(8.dp))
                    .clickable { dealsOnly = !dealsOnly; materialsMode = false }.padding(vertical = 2.dp)) {
                    TopicProgress(doneCount, total)
                    Text(s.progressSolved(doneCount, total) + if (dealsOnly) "  ·  " + s.dealsOnlyWord + "  ✕" else "",
                        style = Type.small.copy(color = if (dealsOnly) c.link else c.graphite, fontSize = 12.5.sp, fontFeatureSettings = "tnum"), modifier = Modifier.padding(top = 5.dp))
                } else Spacer(Modifier.height(12.dp))
                if (task.done) Row(Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(RoundedCornerShape(12.dp)).background(c.chip).padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Text(s.inArchiveSince(task.doneAt?.let { runCatching { LocalDate.parse(it).dayMonthShort() }.getOrNull() } ?: ""), style = Type.small.copy(color = c.graphite), modifier = Modifier.weight(1f))
                    Text(s.bringBack, style = Type.small.copy(color = c.link), modifier = Modifier.clickable { vm.reviveTopic(task.id) })
                }
            }
        }
        Hairline(Modifier.padding(start = MarginW))
        Box(Modifier.weight(1f).fillMaxWidth()) {
            run {
                androidx.compose.animation.Crossfade(materialsMode, animationSpec = tween(240), label = "materials") { m ->
                    if (m) MaterialsFeed(task, onView = { viewing = it })
                    else FeedPage(task, feed, feedState, hl, seenAtOpen, working, selected, vm, FeedActions(
                        select = { k -> selected = if (k.isBlank() || selected == k) null else k },
                        addUnder = { id, title -> addUnder = id to title; selected = null; focusTick++ },
                        editDeal = { editQ = it }, dateDeal = { dateQ = it }, editNote = { editNote = it }, view = { viewing = it }, timeDeal = { timeQ = it },
                    ), onArchive = { dialog = "archive" }, onSource = { dialog = "source" })
                }
            }
        }
        // ---- composer: words go to the feed, on «Вопросы» they become a question ----
        Column(Modifier.background(c.bg).padding(horizontal = 14.dp).padding(top = 6.dp, bottom = 10.dp)) {
            notice?.takeIf { it.scopeKey == scopeKey }?.let { nt -> NoticeLine(nt, vm) }
            val under = addUnder
            SmartComposer(
                vm, if (under != null) "«" + under.second.take(40) + "»" else if (dealsOnly) s.newQuestionHint else s.writeInTopic, busy == scopeKey,
                onSend = { t, f ->
                    if (under != null) { vm.addChildDeal(task.id, under.first, t); addUnder = null }
                    else vm.send(t, scope = Scope.InTask(task.id), files = f, asQuestion = dealsOnly)
                },
                modeLabel = under?.let { s.underLine }, onModeClose = { addUnder = null }, focusKey = focusTick.takeIf { it > 0 },
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
    editNote?.let { e -> TextDialog(s.edit, e.text, "", onDismiss = { editNote = null }) { v -> vm.editEntryText("task:${task.id}", e.id, v); editNote = null } }
    editQ?.let { q -> TextDialog(s.edit, q.title, "", onDismiss = { editQ = null }) { v -> vm.renameSubtask(task.id, q.id, v); editQ = null } }
    timeQ?.let { q -> TimeDialog(q.time, onDismiss = { timeQ = null }) { tm -> vm.setTime("q:${task.id}:${q.id}", tm); timeQ = null } }
    dateQ?.let { q -> QuestionDateDialog(q, onDismiss = { dateQ = null }) { d -> vm.setQuestionDue(task.id, q.id, d); dateQ = null } }
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
                            if (q.isEmpty()) Text(s.searchMaterials, style = Type.body.copy(color = c.faint, fontSize = 15.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
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
        var lastDay: LocalDate? = null
        feed.forEach { line ->
            val day = dateOfStamp(line.at)
            val firstOfDay = line.depth == 0 && day != lastDay
            if (firstOfDay) lastDay = day
            // The margin: the hour of every top line, the date with the first one of a day.
            val stamp = if (line.depth > 0) null else listOfNotNull(if (firstOfDay) day?.dayMonthShort() else null, timeOfStamp(line.at).takeIf { it.isNotBlank() && it != "00:00" }).joinToString("\n").ifBlank { null }
            item(line.key) {
                val fresh = line.key in freshKeys
                Box(Modifier.animateItem().arrive(fresh, freshKeys.indexOf(line.key)).landing(hl == line.key, 10)) {
                    when (line) {
                        is FeedLine.Said -> NoteLine(task, line.e, line.removed, stamp, selected == line.key, fresh, working, vm, act)
                        is FeedLine.Deal -> DealLine(task, line.q, line.removed, line.depth, stamp, selected == line.key, vm, act)
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
private fun NoteLine(task: Task, e: Entry, removed: Boolean, stamp: String?, selected: Boolean, fresh: Boolean, working: Set<String>, vm: MainVm, act: FeedActions) {
    val c = M.c
    val s = M.s
    var menu by remember(e.id) { mutableStateOf(false) }
    val sel by androidx.compose.animation.animateColorAsState(if (selected) c.chip else Color.Transparent, tween(180), label = "sel")
    Margined(Modifier.padding(top = 10.dp, bottom = 2.dp), margin = stamp?.let { m -> { MarginText(m) } }) {
        Box {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(sel)
                .clickable { if (removed) menu = true else act.select("n:" + e.id) }.padding(vertical = 3.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f).padding(top = 4.dp)) {
                        if (e.text.isNotBlank()) Text(e.text, style = Type.body.copy(color = if (removed) c.faint else c.ink, fontSize = 16.sp,
                            textDecoration = if (removed) androidx.compose.ui.text.style.TextDecoration.LineThrough else null))
                        e.items.forEach { Text((if (it.done) "✓ " else "— ") + it.title, style = Type.body.copy(color = if (it.done || removed) c.faint else c.ink, fontSize = 15.5.sp)) }
                    }
                    if (removed) Text(s.removedWord, style = Type.small.copy(color = c.faint, fontSize = 12.sp), modifier = Modifier.padding(start = 8.dp, top = 6.dp))
                    else if (selected) LineTools(c.graphite, onAdd = { act.addUnder(e.id, e.text.ifBlank { e.attachments.firstOrNull()?.let { attachmentLabel(it) }?.ifBlank { null } ?: "…" }) },
                        onRemove = { act.select(""); vm.removeRecord(task.id, e.id) }, onEdit = { act.editNote(e) })
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
private fun DealLine(task: Task, q: Subtask, removed: Boolean, depth: Int, stamp: String?, selected: Boolean, vm: MainVm, act: FeedActions) {
    val c = M.c
    val s = M.s
    val view = LocalView.current
    val motion = motionOn()
    var menu by remember(q.id) { mutableStateOf(false) }
    var tools by remember(q.id) { mutableStateOf(false) }
    val base = when { removed -> c.faint; q.done -> c.ok; q.isWait -> c.wait; else -> c.link }
    val color = if (depth > 0 && !removed && !q.done) base.copy(alpha = 0.78f) else base
    val strike = remember(q.id) { Animatable(if (q.done || removed) 1f else 0f) }
    LaunchedEffect(q.done, removed) { strike.animateTo(if (q.done || removed) 1f else 0f, tween(if (motion) 320 else 0, easing = FastOutSlowInEasing)) }
    val sel by androidx.compose.animation.animateColorAsState(if (selected) c.chip else Color.Transparent, tween(180), label = "sel")
    val due = q.due?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    Margined(Modifier.padding(top = if (depth == 0) 10.dp else 2.dp), margin = stamp?.let { m -> { Box(Modifier.padding(top = if (!q.done && !removed && isSoon(due)) SoonLift + 6.dp else 0.dp)) { MarginText(m) } } }) {
        Box {
            Column(Modifier.padding(start = 24.dp * depth)) {
                if (!q.done && !removed) SoonBadge(due, Modifier.padding(top = 2.dp))
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(sel), verticalAlignment = Alignment.Top) {
                    // The dash is the switch: tapped, it turns into a tick.
                    Box(Modifier.size(30.dp, 30.dp).clip(CircleShape).clickable(enabled = !removed) {
                        if (!q.done) view.confirmTick()
                        val next = q.slots.firstOrNull { !it.done }
                        if (next != null) vm.toggleSlot("q:${task.id}:${q.id}", next.id) else vm.toggleQuestion(task.id, q.id)
                    },
                        contentAlignment = Alignment.Center) {
                        androidx.compose.animation.Crossfade(q.done, animationSpec = tween(220), label = "mark") { d ->
                            if (d) TickGlyph(c.ok, 16.dp)
                            else Text("—", style = Type.body.copy(color = color, fontSize = 17.sp, fontWeight = FontWeight.Medium))
                        }
                    }
                    Column(Modifier.weight(1f).clickable { if (removed) menu = true else act.select("d:" + q.id) }.padding(top = 4.dp, bottom = 4.dp)) {
                        // As on «Сегодня»: the hour first, a petrol link to the clock; the words at the same size.
                        Row(verticalAlignment = Alignment.Top) {
                            if (q.time != null && !q.done && !removed) Text(q.time!!, style = Type.body.copy(color = if (due != null && due.isBefore(LocalDate.now())) c.accent else c.link,
                                fontSize = 15.5.sp, fontWeight = FontWeight.Medium), modifier = Modifier.padding(end = 10.dp).clip(RoundedCornerShape(6.dp)).clickable { act.timeDeal(q) })
                            StrikeText(q.title, Type.body.copy(color = color, fontSize = 15.5.sp,
                                fontWeight = if (depth == 0) FontWeight.Medium else FontWeight.Normal), strike.value, color)
                        }
                        // Due and who owes it, quietly; a day that's today or tomorrow is the badge above instead.
                        // Several days: each one, ticked ones in green.
                        if (q.slots.isNotEmpty() && !removed) Text(androidx.compose.ui.text.buildAnnotatedString {
                            q.slots.forEachIndexed { i, sl ->
                                if (i > 0) append("  ·  ")
                                val d = runCatching { LocalDate.parse(sl.date) }.getOrNull()
                                withStyle(SpanStyle(color = if (sl.done) c.ok else c.graphite, textDecoration = if (sl.done) androidx.compose.ui.text.style.TextDecoration.LineThrough else null)) {
                                    append((if (sl.done) "✓ " else "") + (d?.dayMonthShort() ?: sl.date) + (sl.time?.let { ", $it" } ?: ""))
                                }
                            }
                        }, style = Type.small.copy(fontSize = 13.sp), modifier = Modifier.padding(top = 1.dp))
                        val meta = if (q.slots.isNotEmpty()) listOfNotNull(q.from, q.assignee) else listOfNotNull(
                            due?.takeIf { !isSoon(it) && !q.done }?.let { (if (q.isWait) "~" else "") + it.dayMonthShort() }, q.from, q.assignee,
                            if (q.done) q.doneAt?.let { d -> LocalDate.parse(d).dayMonthShort().let { if (q.isWait) s.gotOn(it) else s.solvedOn(it) } } else null,
                        )
                        if (meta.isNotEmpty() && !removed) Text(meta.joinToString("  ·  "), style = Type.small.copy(color = c.graphite, fontSize = 13.sp), modifier = Modifier.padding(top = 1.dp))
                    }
                    if (removed) Text(s.removedWord, style = Type.small.copy(color = c.faint, fontSize = 12.sp), modifier = Modifier.padding(start = 8.dp, top = 7.dp))
                    else if (selected) Box {
                        LineTools(color, onAdd = { act.addUnder(q.id, q.title) }, onRemove = { act.select(""); vm.removeDeal(task.id, q.id) }, onEdit = { tools = true })
                        SoftMenu(tools, { tools = false }) {
                            SoftMenuItem(s.edit) { tools = false; act.editDeal(q) }
                            SoftMenuItem(s.dueWord) { tools = false; act.dateDeal(q) }
                        }
                    }
                }
            }
            RemovedMenu(menu, { menu = false }, onRestore = { vm.restoreDeal(task.id, q.id) }, onPurge = { vm.purgeDeal(task.id, q.id) })
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
                                if (parts.isNotEmpty()) Text("  ·  ", style = Type.small.copy(color = c.faint))
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

/**
 * Photos, files and voice notes of [topics] on the thread, newest first: the day on the margin, the topic over its files,
 * and a search that reads the text on photos and in voice notes too.
 */
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
    val groups = shown.groupBy { (t, e, _) -> t.id + "/" + e.id }.values.sortedByDescending { it.first().second.at }
    Box(Modifier.fillMaxSize().screenRule()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            header?.let { h -> item("head") { h() } }
            if (all.isEmpty()) item("none") { Margined { Text(s.materialsEmpty, style = Type.body.copy(color = c.graphite), modifier = Modifier.padding(top = 10.dp)) } }
            else item("search") {
                Margined {
                    Column {
                        Row(Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(14.dp)).background(c.card).border(1.dp, c.hairline, RoundedCornerShape(14.dp))
                            .padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Search, null, tint = c.graphite, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(10.dp))
                            Box(Modifier.weight(1f)) {
                                if (q.isEmpty()) Text(s.searchMaterials, style = Type.body.copy(color = c.faint, fontSize = 15.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
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
            if (all.isNotEmpty() && shown.isEmpty()) item("nf") { Margined { Text(s.noMaterialsFound, style = Type.body.copy(color = c.graphite), modifier = Modifier.padding(top = 14.dp)) } }
            groups.forEach { g ->
                val (t, e, _) = g.first()
                item(t.id + "/" + e.id) {
                    Margined(Modifier.animateItem().padding(top = 14.dp), node = c.accent.copy(alpha = 0.8f), nodeY = 10.dp,
                        margin = { MarginText(dateOfStamp(e.at)?.dayMonthShort() ?: "") }) {
                        Column {
                            if (onTopic != null) Row(Modifier.clip(RoundedCornerShape(6.dp)).clickable { onTopic(t.id) }.padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                TopicGlyph(c.link, 14.dp)
                                Spacer(Modifier.width(6.dp))
                                Text(t.title, style = Type.small.copy(color = c.link, fontSize = 13.5.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                g.forEach { (_, _, a) ->
                                    Column(Modifier.width(120.dp).clip(RoundedCornerShape(10.dp)).clickable { onView(a) }) {
                                        if (a.kind == "photo" || a.kind == "video") Thumb(a, 120.dp)
                                        else Box(Modifier.size(120.dp).clip(RoundedCornerShape(12.dp)).background(c.card).border(1.dp, c.hairline, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                                            Icon(kindIcon(a.kind), null, tint = c.graphite, modifier = Modifier.size(26.dp))
                                        }
                                        Text(a.caption ?: attachmentLabel(a).ifBlank { a.name }, style = Type.small.copy(color = c.graphite, fontSize = 12.5.sp), maxLines = 2, overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.padding(top = 4.dp))
                                        // Found by the words inside, not the name: say what matched.
                                        if (needle.isNotEmpty() && !a.name.lowercase().replace(" ", "").contains(needle)) (a.ocr ?: a.transcript)?.let {
                                            Text("«" + it.take(60) + "»", style = Type.small.copy(color = c.faint, fontSize = 11.5.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
