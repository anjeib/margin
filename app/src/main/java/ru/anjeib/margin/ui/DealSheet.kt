package ru.anjeib.margin.ui

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import ru.anjeib.margin.ai.Scope
import androidx.compose.runtime.collectAsState
import androidx.compose.material3.Icon
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Place
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
    fun fill(what: String) { mode = what; modeTick++ }
    val busy by vm.busy.collectAsState()
    val data by vm.data.collectAsState()
    val ctxOpen = androidx.compose.ui.platform.LocalContext.current
    fun commit() { if (title.isNotBlank() && title.trim() != q.title) vm.renameSubtask(topic.id, q.id, title.trim()) }
    DisposableEffect(qid) { onDispose { commit() } }

    ModalBottomSheet(
        onDismissRequest = { commit(); onClose() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.bg,
        scrimColor = c.scrim,
        dragHandle = { Box(Modifier.padding(top = 10.dp, bottom = 2.dp).size(36.dp, 4.dp).clip(CircleShape).background(c.hairline)) },
    ) {
        // One design for every matter (owner's choice, 9 окт): the topic above, the title, the day, who owes it,
        // «+ …» for what is not set, the comments written right here — and a small field below for changes in words.
        val screen = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp
        val ime = androidx.compose.foundation.layout.WindowInsets.ime.asPaddingValues().calculateBottomPadding()
        val maxH = ((screen * 0.92f).dp - ime).coerceAtLeast(200.dp)
        Column(Modifier.fillMaxWidth().heightIn(max = maxH).imePadding().navigationBarsPadding()) {
            // At least half the screen with the field right under it — the same as a simple matter's card.
            Column(Modifier.weight(1f, fill = false).heightIn(min = ((screen * 0.6f).dp - 76.dp).coerceAtLeast(120.dp)).screenRule().verticalScroll(rememberScrollState()).padding(bottom = 12.dp)) {
                // Its topic above it — a way back; the few other things are on a long press of the tick.
                if (onOpenTopic != null) Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.width(MarginW + MarginGap))
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f).clip(RoundedCornerShape(6.dp))) {
                        TopicGlyph(c.link, 13.dp)
                        Spacer(Modifier.width(7.dp))
                        PaperLink(topic.title + "  ›") { commit(); onOpenTopic() }
                    }
                }
                // The top: when, how often, where, with whom — one quiet line, as on a simple matter; then what is not set yet.
                val row = topic.questionRow(q)
                val due = q.due?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                val late = !q.done && due != null && due.isBefore(LocalDate.now())
                val people = row.people.filter { it != q.from }
                Margined(Modifier.padding(top = if (onOpenTopic != null) 0.dp else 8.dp)) {
                    Column {
                        if (q.slots.isNotEmpty()) DaysProp(q.slots, onToggle = { vm.toggleSlot(ref, it) }, onRemove = { vm.removeSlot(ref, it) })
                        fun person(n: String) { data.contact(n)?.let { pp -> onOpenPerson?.invoke(pp.id) } ?: vm.offerContact(n) }
                        MetaLine(buildList {
                            if (q.slots.isEmpty()) {
                                due?.let { add(MetaItem(Meta.plain(if (late) s.lateSince(it.dayMonthShort()) else row.whenLabel()?.substringBefore(" · ${q.time}") ?: it.dayMonthShort()), Meta.date(late)) { dialog = "date" }) }
                                q.time?.let { add(MetaItem(Meta.plain(it), Meta.date(late)) { dialog = "time" }) }
                            }
                            q.repeat?.let { add(MetaItem(Meta.plain(it.label()), Meta.quiet()) { dialog = "repeat" }) }
                            q.from?.let { w -> add(MetaItem(Meta.promise(s.promiseToYouParts(w)), Meta.quiet(), onLong = { person(w) }) { dialog = "waiting" }) }
                            people.forEach { p -> add(MetaItem(Meta.plain(p), Meta.link()) { person(p) }) }
                            q.place?.let { pl -> add(MetaItem(Meta.plain(pl), Meta.link(), onLong = { fill("place") }) {
                                runCatching { ctxOpen.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("geo:0,0?q=" + android.net.Uri.encode(pl)))) }
                            }) }
                        }, Modifier.padding(top = if (q.slots.isNotEmpty()) 6.dp else 0.dp))
                        // What is not set yet — the same list as on a simple matter.
                        val missing = buildList {
                            if (due == null && q.slots.isEmpty()) add(s.dateWord to "date")
                            if (q.time == null && q.slots.isEmpty()) add(s.fieldTime to "time")
                            // Another day for the same matter («и в четверг»).
                            if ((due != null || q.slots.isNotEmpty()) && q.repeat == null && !q.done) add(s.addDay to "day")
                            if (q.repeat == null) add(s.fieldRepeat to "repeat")
                            if (q.from == null && !q.done) add(s.fieldWait to "waiting")
                            if (people.isEmpty() && q.from == null) add(s.fieldPeople to "people")
                            if (q.place == null) add(s.fieldPlace to "place")
                            if (!q.done) add(s.stepOne to "step")
                        }
                        if (missing.isNotEmpty()) DotFlow(missing.size, Modifier.padding(top = 2.dp), separator = { Text(" · ", style = Type.small.copy(color = c.graphite)) }) { i ->
                            val (label, d) = missing[i]
                            PaperLink("+ " + label.lowercase(), quiet = true) { if (d in setOf("step", "place", "people")) fill(d) else dialog = d }
                        }
                    }
                }
                Hairline(Modifier.padding(start = MarginW, top = 12.dp))  // the stream begins under this line
                // The stream: the matter's own words first, the day it appeared on the margin; then the comments.
                val born = topic.events.firstOrNull { it.ref == q.id && (it.kind == "q" || it.kind == "wait") }?.at ?: topic.createdAt
                Margined(Modifier.padding(top = 12.dp), margin = { Stamp(born) }) {
                    // The same line as on the main screen: what kind of matter, its bagel, the words — not a heading.
                    Row(verticalAlignment = Alignment.Top) {
                        KindMark(row)
                        Box(Modifier.size(26.dp, 24.dp), contentAlignment = Alignment.CenterStart) {
                            CheckCircle(q.done, size = 18.dp, onLong = { menu = true }) {
                                val next = q.slots.firstOrNull { !it.done }
                                if (next != null) vm.toggleSlot(ref, next.id) else vm.toggleQuestion(topic.id, q.id)
                            }
                            SoftMenu(menu, { menu = false }) {
                                if (onOpenTopic != null) SoftMenuItem(s.openTopic) { menu = false; commit(); onOpenTopic() }
                                SoftMenuItem(s.delete, danger = true) { menu = false; vm.removeDeal(topic.id, q.id); onClose() }
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        BasicTextField(title, { title = it },
                            textStyle = Type.body.copy(color = if (q.done) c.graphite else c.ink, fontSize = 17.sp, lineHeight = 24.sp),
                            cursorBrush = SolidColor(c.link), modifier = Modifier.weight(1f))
                    }
                }
                WrittenLane(q.comments.map { LaneItem(it.id, it.at, it.text) }, s.commentHint, Modifier.padding(top = 4.dp), after = born,
                    onEdit = { id, t -> vm.editComment(ref, id, t) }, onAdd = { t, at -> vm.addComment(ref, t, at) })
                // The same visible actions as on a simple matter's card (the long press of the bagel still has them).
                Margined(Modifier.padding(top = 10.dp)) {
                    val today = LocalDate.now()
                    val rowActs = LocalRowActions.current
                    val acts = buildList<Triple<String, Boolean, () -> Unit>> {
                        rowActs?.let { r -> if (!q.done) add(Triple(s.toOtherTopic, false) { commit(); onClose(); r.toTopic(ref) }) }
                        if (!q.done) {
                            add(Triple(s.swipeTomorrow, false) { vm.postponeTo(ref, today.plusDays(1)) })
                            add(Triple(s.plusWeek, false) { vm.postponeTo(ref, (due?.takeIf { !it.isBefore(today) } ?: today).plusDays(7)) })
                        }
                        if (onOpenTopic != null) add(Triple(s.openTopic, false) { commit(); onOpenTopic() })
                        add(Triple(s.delete, true) { vm.removeDeal(topic.id, q.id); onClose() })
                    }
                    DotFlow(acts.size, separator = { Text(" · ", style = Type.small.copy(color = c.graphite)) }) { i ->
                        val (label, danger, act) = acts[i]
                        PaperLink(label.lowercase(), quiet = !danger, danger = danger, onClick = act)
                    }
                }
                // Their day has come: «напомнить» here too, the same as on a simple matter (one behaviour for every wait).
                if (q.from != null && !q.done && due?.isAfter(LocalDate.now()) == false) Margined(Modifier.padding(top = 14.dp)) {
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.linkSoft).padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(s.overdueWaiting, style = Type.body.copy(color = c.ink, fontSize = 15.sp), modifier = Modifier.weight(1f))
                        Pill(s.remindPerson(q.from), filled = true, onClick = { remindAbout(ctxOpen, data, row) })
                    }
                }
            }
            // Changes in words go to the topic, said about this matter; «+ дело» puts a matter under it.
            val scopeKey = MainVm.key(Scope.InTask(topic.id))
            val modeLabel = when (mode) { "step" -> s.stepOne; "place" -> s.fieldPlace; "people" -> s.fieldPeople; else -> null }
            Column(Modifier.fieldRule().padding(horizontal = 14.dp).padding(bottom = 10.dp)) { SmartComposer(
                vm, if (mode == "step") s.stepHint else modeLabel?.let { s.fillHint(it) } ?: s.addOrChange, busy == scopeKey,
                onSend = { t, f ->
                    when (mode) {
                        "step" -> if (t.isNotBlank()) vm.addChildDeal(topic.id, q.id, t)
                        "place" -> vm.setDealPlace(topic.id, q.id, t)
                        "people" -> vm.addDealPeople(topic.id, q.id, t)
                        else -> vm.send(s.aboutDeal(q.title, t), scope = Scope.InTask(topic.id), files = f)
                    }
                    mode = null
                },
                modeLabel = modeLabel, onModeClose = { mode = null }, focusKey = modeTick.takeIf { it > 0 }?.toLong(),
            ) }
        }
    }
    when (dialog) {
        "date" -> QuestionDateDialog(q, onDismiss = { dialog = null }) { d -> vm.setQuestionDue(topic.id, q.id, d); dialog = null }
        "time" -> TimeDialog(q.time, onDismiss = { dialog = null }) { tm -> vm.setTime(ref, tm); dialog = null }
        "day" -> OneDayDialog(onDismiss = { dialog = null }) { d -> vm.addSlot(ref, d.toString()); dialog = null }
        "repeat" -> RepeatDialog(topic.questionRow(q), onDismiss = { dialog = null }) { r -> vm.setDealRepeat(topic.id, q.id, r); dialog = null }
        "waiting" -> TextDialog(s.waiting, q.from.orEmpty(), s.sideHint, onDismiss = { dialog = null }) { v -> vm.setDealFrom(topic.id, q.id, v); dialog = null }
    }
}
