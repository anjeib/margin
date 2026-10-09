package ru.anjeib.margin.ui

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
fun DealSheet(topic: Task, qid: String, vm: MainVm, onClose: () -> Unit, onOpenTopic: (() -> Unit)?) {
    val c = M.c
    val s = M.s
    fun flat(l: List<Subtask>): List<Subtask> = l.flatMap { listOf(it) + flat(it.children) }
    val q = flat(topic.subtasks).find { it.id == qid }
    if (q == null) { LaunchedEffect(Unit) { onClose() }; return }
    val ref = "q:${topic.id}:${q.id}"
    var title by remember(qid) { mutableStateOf(q.title) }
    var dialog by remember { mutableStateOf<String?>(null) }
    fun commit() { if (title.isNotBlank() && title.trim() != q.title) vm.renameSubtask(topic.id, q.id, title.trim()) }
    DisposableEffect(qid) { onDispose { commit() } }

    ModalBottomSheet(
        onDismissRequest = { commit(); onClose() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.bg,
        scrimColor = c.scrim,
        dragHandle = { Box(Modifier.padding(top = 10.dp, bottom = 2.dp).size(36.dp, 4.dp).clip(CircleShape).background(c.hairline)) },
    ) {
        // Opens on half the screen like a simple matter's card, and looks like it: the same title, the same badges, the same comments.
        val half = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp * 0.5f).dp
        Column(Modifier.fillMaxWidth().heightIn(min = half).imePadding().navigationBarsPadding().screenRule().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            // Whose it is, first and quiet: a tap goes to the topic.
            if (onOpenTopic != null) Margined(Modifier.padding(top = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clip(RoundedCornerShape(6.dp))) {
                    TopicGlyph(c.link, 13.dp)
                    Spacer(Modifier.width(7.dp))
                    PaperLink(topic.title + "  ›") { commit(); onOpenTopic() }
                }
            }
            Margined(Modifier.padding(top = 8.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Box(Modifier.offset(x = (-8).dp)) {
                        CheckCircle(q.done, size = 24.dp) {
                            val next = q.slots.firstOrNull { !it.done }
                            if (next != null) vm.toggleSlot(ref, next.id) else vm.toggleQuestion(topic.id, q.id)
                        }
                    }
                    BasicTextField(title, { title = it },
                        textStyle = Type.title.copy(color = if (q.done) c.graphite else c.ink, fontSize = 28.sp, lineHeight = 34.sp),
                        cursorBrush = SolidColor(c.link), modifier = Modifier.weight(1f).padding(top = 4.dp))
                }
            }
            Margined {
                Column {
                    val due = q.due?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                    val late = !q.done && due != null && due.isBefore(LocalDate.now())
                    Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        due?.let { Prop({ CalendarGlyph(c.graphite, 15.dp) }, topic.questionRow(q).whenLabel()?.substringBefore(" · ${q.time}") ?: it.dayMonthShort(), late) { dialog = "date" } }
                        q.time?.let { Prop({ ClockGlyph(c.graphite, 15.dp) }, it, late) { dialog = "time" } }
                    }
                    // Who owes it: the same badge as on a simple matter («Вам от Анны»).
                    if (q.from != null) Row(Modifier.padding(top = 8.dp)) { PromiseBadge(topic.questionRow(q), big = true) {} }
                    val missing = listOfNotNull((s.dateWord to "date").takeIf { due == null }, (s.fieldTime to "time").takeIf { q.time == null })
                    if (missing.isNotEmpty()) Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        missing.forEachIndexed { i, (label, d) ->
                            if (i > 0) Text(" · ", style = Type.small.copy(color = c.faint))
                            PaperLink("+ " + label.lowercase(), quiet = true) { dialog = d }
                        }
                    }
                }
            }
            // The user's comments, written right here — each with its day and hour on the margin.
            WrittenLane(q.comments.map { LaneItem(it.id, it.at, it.text) }, s.commentHint, Modifier.padding(top = 10.dp),
                onEdit = { id, t -> vm.editComment(ref, id, t) }, onAdd = { t, at -> vm.addComment(ref, t, at) })
        }
    }
    when (dialog) {
        "date" -> QuestionDateDialog(q, onDismiss = { dialog = null }) { d -> vm.setQuestionDue(topic.id, q.id, d); dialog = null }
        "time" -> TimeDialog(q.time, onDismiss = { dialog = null }) { tm -> vm.setTime(ref, tm); dialog = null }
    }
}
