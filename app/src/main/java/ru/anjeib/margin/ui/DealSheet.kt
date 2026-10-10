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
                // The same card as a simple matter (owner's call, 10 окт), with its topic above the title.
                val row = topic.questionRow(q)
                val due = q.due?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                val today = LocalDate.now()
                val late = !q.done && due != null && due.isBefore(today)
                val people = row.people.filter { it != q.from }
                val rowActs = LocalRowActions.current
                var more by remember(qid) { mutableStateOf(false) }
                var postponing by remember(qid) { mutableStateOf(false) }
                if (postponing) PostponeDialog(onDismiss = { postponing = false }) { d -> vm.postponeTo(ref, d); postponing = false }
                fun person(n: String) { data.contact(n)?.let { pp -> onOpenPerson?.invoke(pp.id) } ?: vm.offerContact(n) }
                Margined {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Its topic — a way back to it.
                        if (onOpenTopic != null) Row(Modifier.weight(1f).clip(RoundedCornerShape(6.dp)), verticalAlignment = Alignment.CenterVertically) {
                            TopicGlyph(c.link, 13.dp)
                            Spacer(Modifier.width(7.dp))
                            PaperLink(topic.title + "  ›") { commit(); onOpenTopic() }
                        } else Spacer(Modifier.weight(1f))
                        Box(Modifier.width(48.dp)) {
                            CardMenu { close ->
                                if (!q.done) SoftMenuItem(s.postponeMenu) { close(); postponing = true }
                                rowActs?.let { r -> if (!q.done) SoftMenuItem(s.toOtherTopic) { close(); commit(); onClose(); r.toTopic(ref) } }
                                if (onOpenTopic != null) SoftMenuItem(s.openTopic) { close(); commit(); onOpenTopic() }
                                SoftMenuItem(s.delete, danger = true) { close(); vm.removeDeal(topic.id, q.id); onClose() }
                            }
                        }
                    }
                }
                Margined(Modifier.padding(top = 2.dp), margin = { if (q.slots.isEmpty()) CardDay(due, late) { dialog = "date" } }) {
                    CardBlock(title, { title = it }, q.done, topic.sphere, onToggle = {
                        val next = q.slots.firstOrNull { !it.done }
                        if (next != null) vm.toggleSlot(ref, next.id) else vm.toggleQuestion(topic.id, q.id)
                    }, hour = q.time?.takeIf { q.slots.isEmpty() }, onHour = if (q.slots.isEmpty() && due != null) ({ dialog = "time" }) else null,
                        quiet = q.from?.let { s.promiseToYou(it) }, due = due)
                }
                if (q.slots.isNotEmpty()) Margined(Modifier.padding(top = 4.dp)) { DaysProp(q.slots, onToggle = { vm.toggleSlot(ref, it) }, onRemove = { vm.removeSlot(ref, it) }) }
                Margined(Modifier.padding(top = 2.dp)) {
                    Column {
                        CardTools(listOf(
                            CardTool(s.fieldPlace, q.place, { fill("place") }) { Icon(Icons.Outlined.Place, null, tint = it, modifier = Modifier.size(16.dp)) },
                            CardTool(s.fieldPeople, people.takeIf { it.isNotEmpty() }?.joinToString(", "), { if (people.size == 1) person(people[0]) else fill("people") }) { PeopleGlyph(it, 16.dp) },
                            CardTool(s.fieldRepeat, q.repeat?.label(), { dialog = "repeat" }) { RepeatGlyph(it, 16.dp) },
                            CardTool(s.moreWord, null, { more = !more }, bare = true) { Icon(Icons.Outlined.MoreHoriz, null, tint = it, modifier = Modifier.size(16.dp)) },
                        ))
                        androidx.compose.animation.AnimatedVisibility(more) {
                            CardTools(listOf(CardTool(s.fieldWait, q.from?.let { s.promiseToYou(it) }, { dialog = "waiting" }) { ArrowGlyph(it, 15.dp, incoming = true) }) +
                                (if (!q.done && q.repeat == null) listOf(CardTool(s.addDay, null, { dialog = "day" }) { CalendarGlyph(it, 15.dp) }) else emptyList()) +
                                (if (!q.done) listOf(CardTool(s.stepOne, null, { fill("step") }) { TopicGlyph(it, 15.dp) }) else emptyList()))
                        }
                    }
                }
                Hairline(Modifier.padding(start = MarginW, top = 6.dp))
                val born = topic.events.firstOrNull { it.ref == q.id && (it.kind == "q" || it.kind == "wait") }?.at ?: topic.createdAt
                WrittenLane(q.comments.map { LaneItem(it.id, it.at, it.text) }, s.commentHint, Modifier.padding(top = 4.dp), after = null,
                    onEdit = { id, t -> vm.editComment(ref, id, t) }, onAdd = { t, at -> vm.addComment(ref, t, at) })
                // Their day has come: «напомнить» here too, the same as on a simple matter (one behaviour for every wait).
                if (q.from != null && !q.done && due?.isAfter(LocalDate.now()) == false) Margined(Modifier.padding(top = 14.dp)) {
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.linkSoft).padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(s.overdueWaiting, style = Type.body.copy(color = c.ink, fontSize = 15.sp), modifier = Modifier.weight(1f))
                        Pill(s.remindPerson(q.from), filled = true, onClick = { remindAbout(ctxOpen, data, row) })
                    }
                }
                Margined { CreatedLine(born) }
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
