package ru.anjeib.margin.ui

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import ru.anjeib.margin.ai.Scope
import androidx.compose.runtime.collectAsState
import androidx.compose.material3.Icon
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.DeleteOutline
import ru.anjeib.margin.data.contact
import ru.anjeib.margin.data.label
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.anjeib.margin.data.Subtask
import ru.anjeib.margin.data.Task
import ru.anjeib.margin.data.dayMonthShort
import ru.anjeib.margin.data.questionRow
import ru.anjeib.margin.data.whenLabel
import java.time.LocalDate

/**
 * A topic's matter on its own — opened by a tap in the day lists or in the topic's feed, the same way a simple matter
 * opens its card: the tick, the words (edited in place), the day and the hour, who owes it, the user's comment,
 * and the way to the topic it belongs to.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DealSheet(topic: Task, qid: String, vm: MainVm, onClose: () -> Unit, onOpenTopic: (() -> Unit)?, onOpenPerson: ((String) -> Unit)? = null) {
    val c = M.c
    val s = M.s
    fun flat(l: List<Subtask>): List<Subtask> = l.flatMap { listOf(it) + flat(it.children) }
    val q = flat(topic.subtasks).find { it.id == qid }
    if (q == null) { LaunchedEffect(Unit) { onClose() }; return }
    val ref = "q:${topic.id}:${q.id}"
    var title by remember(qid) { mutableStateOf(q.title) }
    var dialog by remember { mutableStateOf<String?>(null) }
    var menu by remember { mutableStateOf(false) }
    // «+ дело», «+ место», «+ контакты» turn the field below into that one thing — typed or said, no little windows.
    var mode by remember(qid) { mutableStateOf<String?>(null) }
    var modeTick by remember { mutableStateOf(0) }
    val pageDraft = remember(qid) { mutableStateOf("") }
    val pageFiles = remember(qid) { androidx.compose.runtime.mutableStateListOf<ru.anjeib.margin.data.Attachment>() }
    val pageSend = remember(qid) { PageSend() }
    androidx.compose.runtime.DisposableEffect(qid) { onDispose { pageSend.send() } }
    var writeTick by remember { mutableStateOf(0) }
    fun fill(what: String) { mode = what; modeTick++ }
    val busy by vm.busy.collectAsState()
    val data by vm.data.collectAsState()
    val ctxOpen = androidx.compose.ui.platform.LocalContext.current
    // The same quiet «✓ Сохранено» by the handle as a simple matter's card.
    var savedAt by remember { mutableStateOf(0L) }
    fun saved() { savedAt = System.currentTimeMillis() }
    LaunchedEffect(savedAt) { if (savedAt > 0) { kotlinx.coroutines.delay(1600); savedAt = 0 } }
    fun commit() { if (title.isNotBlank() && title.trim() != q.title) { vm.renameSubtask(topic.id, q.id, title.trim()); saved() } }
    DisposableEffect(qid) { onDispose { commit() } }

    ModalBottomSheet(
        modifier = Modifier.paperGrain(),
        onDismissRequest = { commit(); onClose() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.bg,
        scrimColor = c.scrim,
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
        // One design for every matter (owner's choice, 9 окт): the topic above, the title, the day, who owes it,
        // «+ …» for what is not set, the comments written right here — and a small field below for changes in words.
        val screen = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp
        val ime = androidx.compose.foundation.layout.WindowInsets.ime.asPaddingValues().calculateBottomPadding()
        val maxH = ((screen * 0.92f).dp - ime).coerceAtLeast(200.dp)
        Column(Modifier.fillMaxWidth().heightIn(max = maxH).imePadding().navigationBarsPadding()) {
            // At least half the screen with the field right under it — the same as a simple matter's card.
            val pageLines = rememberPageLines()
            Column(Modifier.weight(1f, fill = false).heightIn(min = ((screen * 0.6f).dp - 76.dp).coerceAtLeast(120.dp)).ruledPage(pageLines).screenRule().verticalScroll(rememberScrollState()).padding(bottom = 12.dp)) {
                // The same card as a simple matter (owner's call, 10 окт), with its topic above the title.
                val row = topic.questionRow(q)
                val due = q.due?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                val start = q.start?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                val window = start != null && due != null && start != due
                val today = LocalDate.now()
                val late = !q.done && due != null && due.isBefore(today)
                val people = row.people.filter { it != q.from && it != q.promisedTo }
                val rowActs = LocalRowActions.current
                var more by remember(qid) { mutableStateOf(false) }
                var postponing by remember(qid) { mutableStateOf(false) }
                if (postponing) PostponeDialog(onDismiss = { postponing = false }) { d -> vm.postponeTo(ref, d); postponing = false }
                fun person(n: String) { data.contact(n)?.let { pp -> onOpenPerson?.invoke(pp.id) } ?: vm.offerContact(n) }
                // Its topic — a way back to it — at the top; no ⋯ (owner, 14 окт): what it can do is under «Ещё».
                if (onOpenTopic != null) Margined { TopicLink(topic.title) { commit(); onOpenTopic() } } else Spacer(Modifier.height(12.dp))
                fun tick() {
                    val next = q.slots.firstOrNull { !it.done }
                    if (next != null) vm.toggleSlot(ref, next.id) else vm.toggleQuestion(topic.id, q.id)
                }
                val meta = buildList {
                    if (window) row.whenLabel()?.substringBefore(" · ${q.time}")?.let { add(MetaItem(Meta.plain(it), Meta.quiet()) { dialog = "date" }) }
                    q.from?.let { add(MetaItem(Meta.promise(s.promiseToYouParts(it)), Meta.quiet()) { dialog = "waiting" }) }
                    q.promisedTo?.let { add(MetaItem(Meta.promise(s.promiseFromYouParts(it)), Meta.quiet()) { dialog = "promised" }) }
                    people.forEach { p -> add(MetaItem(Meta.plain(p), Meta.link()) { person(p) }) }
                    q.place?.let { add(MetaItem(Meta.plain(it), Meta.link(), onLong = { fill("place") }) {
                        runCatching { ctxOpen.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("geo:0,0?q=" + android.net.Uri.encode(it)))) }
                    }) }
                    q.repeat?.let { add(MetaItem(Meta.plain("↻ " + it.label()), Meta.quiet()) { dialog = "repeat" }) }
                }
                CardHead(title, { title = it }, q.done, onToggle = ::tick,
                    margin = if (q.slots.isEmpty()) ({ CardWhen(due, q.time, late, onDay = { dialog = "date" }, onHour = if (due != null) ({ dialog = "time" }) else null, from = start) }) else null,
                    meta = meta,
                    mark = if ((q.from != null || q.promisedTo != null) && !q.done) ({ Box(Modifier.clip(CircleShape).clickable(onClick = ::tick)) { PromiseMark(incoming = q.from != null, size = 32.dp) } }) else null)
                if (q.slots.isNotEmpty()) Margined(Modifier.padding(top = 4.dp)) { DaysProp(q.slots, onToggle = { vm.toggleSlot(ref, it) }, onRemove = { vm.removeSlot(ref, it) }) }
                Margined(Modifier.padding(top = 10.dp)) {
                    Column {
                        ActionBar(listOf(
                            Action(s.actWhen, due != null, { dialog = "date" }) { CalendarGlyph(it, 22.dp) },
                            Action(s.fieldRepeat, q.repeat != null, { dialog = "repeat" }) { RepeatGlyph(it, 22.dp) },
                            Action(s.fieldPeople, people.isNotEmpty(), { fill("people") }) { PeopleGlyph(it, 22.dp) },
                            Action(s.fieldPlace, q.place != null, { fill("place") }) { PinGlyph(it, 23.dp) },
                            Action(s.actMore, more, { more = !more }) { MoreGlyph(it, 23.dp) },
                        ))
                        androidx.compose.animation.AnimatedVisibility(more) {
                            MoreActions(listOfNotNull(
                                Action(s.actWait, q.from != null, { dialog = "waiting" }) { HandGlyph(it, 23.dp, incoming = true) },
                                Action(s.actPromised, q.promisedTo != null, { dialog = "promised" }) { HandGlyph(it, 23.dp) },
                                if (!q.done && q.repeat == null) Action(s.actDay, false, { dialog = "day" }) { CalendarGlyph(it, 20.dp) } else null,
                                if (!q.done) Action(s.actStep, false, { fill("step") }) { PlusGlyph(it, 18.dp) } else null,
                                if (!q.done) Action(s.actPostpone, false, { postponing = true }) { ClockGlyph(it, 22.dp) } else null,
                                if (!q.done) rowActs?.let { r -> Action(s.actToTopic, false, { commit(); onClose(); r.toTopic(ref) }) { TopicGlyph(it, 19.dp) } } else null,
                                Action(s.actToIdea, false, { commit(); vm.dealToIdea(topic.id, q.id); onClose() }) { ThoughtGlyph(it, 20.dp) },
                                if (!q.done) Action(s.actWontDo, false, { commit(); vm.dropDeal(topic.id, q.id); onClose() }) { NotGlyph(it, 21.dp) } else null,
                                Action(s.delete, false, { vm.removeDeal(topic.id, q.id); onClose() }) { TrashGlyph(it, 22.dp) },
                            ))
                        }
                    }
                }
                val born = topic.events.firstOrNull { it.ref == q.id && (it.kind == "q" || it.kind == "wait") }?.at ?: topic.createdAt
                WrittenLane(q.comments.map { LaneItem(it.id, it.at, it.text) }, s.commentHint, Modifier.padding(top = 4.dp), after = null, label = s.commentsTitle,
                    draft = pageDraft, focusKey = writeTick.takeIf { it > 0 }, vm = vm, files = pageFiles, onKeep = { pageSend.send() }, pasteTo = "comment:$ref", plus = Plus.Marks,
                    onEdit = { id, t -> vm.editComment(ref, id, t); saved() }, onAdd = { t, at -> vm.addComment(ref, t, at); saved() })
                // Their day has come: «напомнить» here too, the same as on a simple matter (one behaviour for every wait).
                if (q.from != null && !q.done && due?.isAfter(LocalDate.now()) == false) Margined(Modifier.padding(top = 16.dp)) {
                    Panel {
                        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 10.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(s.overdueWaiting, style = Type.body.copy(color = c.ink, fontSize = 15.sp), modifier = Modifier.weight(1f))
                            Pill(s.remindPerson(q.from), filled = true, onClick = { remindAbout(ctxOpen, data, row) })
                        }
                    }
                }
                Margined { CreatedLine(born) }
                PageEnd(pageLines)
            }
            // Changes in words go to the topic, said about this matter; «+ дело» puts a matter under it.
            val scopeKey = MainVm.key(Scope.InTask(topic.id))
            val modeLabel = when (mode) { "step" -> s.stepOne; "place" -> s.fieldPlace; "people" -> s.fieldPeople; else -> null }
            val notice by vm.notice.collectAsState()
            Column(Modifier.fieldRule().padding(horizontal = 14.dp).padding(bottom = 10.dp)) {
              // What became of the words (or that they wait for the cloud) — said here too, not only in the topic.
              notice?.takeIf { it.scopeKey == scopeKey }?.let { nt -> Box(Modifier.padding(start = FieldLead)) { NoticeLine(nt, vm) } }
              // A name here that isn't a contact was tapped: the same quiet offer as on a simple matter.
              val offer by vm.contactOffer.collectAsState()
              offer?.let { n ->
                  Row(Modifier.fillMaxWidth().padding(start = FieldLead, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                      Text(s.notAContact(n), style = Type.small.copy(color = c.graphite), modifier = Modifier.weight(1f))
                      QuietLink(s.addContact) { vm.keepContact(n) }
                  }
              }
              SmartComposer(
                vm, if (mode == "step") s.stepHint else modeLabel?.let { s.fillHint(it) } ?: s.writeOnPage, busy == scopeKey,
                onSend = { t, f ->
                    when {
                        mode == "step" -> if (t.isNotBlank()) vm.addChildDeal(topic.id, q.id, t)
                        mode == "place" -> vm.setDealPlace(topic.id, q.id, t)
                        mode == "people" -> vm.addDealPeople(topic.id, q.id, t)
                        // «перенеси на пятницу» — this matter's day, read on the phone.
                        f.isEmpty() && vm.changeDay(topic.id, t, q.id) -> {}
                        // Words on the page: a request is done, anything else is a comment on this matter.
                        f.isEmpty() && !vm.isRequest(t) -> vm.addComment(ref, t.trim(), ru.anjeib.margin.data.nowStamp())
                        else -> vm.send(s.aboutDeal(q.title, t), scope = Scope.InTask(topic.id), files = f)
                    }
                    mode = null
                },
                modeLabel = modeLabel, onModeClose = { mode = null }, focusKey = modeTick.takeIf { it > 0 }?.toLong(),
                pageText = pageDraft.takeIf { modeLabel == null }, onWrite = { writeTick++ }, slash = true, pageSend = pageSend, pageFiles = pageFiles.takeIf { modeLabel == null },
                marksOnPlus = true,
              )
            }
        }
    }
    when (dialog) {
        // The same choice as a simple matter's: a day, or a window «с 9 по 11 окт».
        "date" -> DatesDialog(topic.questionRow(q), onDismiss = { dialog = null }) { st, du -> vm.setQuestionDue(topic.id, q.id, du ?: st, start = st.takeIf { du != null }); saved(); dialog = null }
        "time" -> TimeDialog(q.time, onDismiss = { dialog = null }) { tm -> vm.setTime(ref, tm); dialog = null }
        "day" -> OneDayDialog(onDismiss = { dialog = null }) { d -> vm.addSlot(ref, d.toString()); dialog = null }
        "repeat" -> RepeatDialog(topic.questionRow(q), onDismiss = { dialog = null }) { r -> vm.setDealRepeat(topic.id, q.id, r); dialog = null }
        // One matter, one direction: setting a side takes the other one off, as on a simple matter.
        "waiting" -> TextDialog(s.waiting, q.from.orEmpty(), s.sideHint, onDismiss = { dialog = null }) { v ->
            vm.setDealFrom(topic.id, q.id, v); if (v.isNotBlank()) vm.setDealPromised(topic.id, q.id, null); saved(); dialog = null }
        "promised" -> TextDialog(s.promisedQ, q.promisedTo.orEmpty(), s.sideHint, onDismiss = { dialog = null }) { v ->
            vm.setDealPromised(topic.id, q.id, v); if (v.isNotBlank()) vm.setDealFrom(topic.id, q.id, null); saved(); dialog = null }
    }
}
