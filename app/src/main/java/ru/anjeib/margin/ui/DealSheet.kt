package ru.anjeib.margin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
        Column(Modifier.fillMaxWidth().imePadding().navigationBarsPadding().screenRule().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
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
                        textStyle = Type.title.copy(color = if (q.done) c.graphite else c.ink, fontSize = 26.sp, lineHeight = 32.sp),
                        cursorBrush = SolidColor(c.link), modifier = Modifier.weight(1f).padding(top = 4.dp))
                }
            }
            Margined(Modifier.padding(top = 8.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    val due = q.due?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                    val late = !q.done && due != null && due.isBefore(LocalDate.now())
                    Prop({ CalendarGlyph(c.graphite, 15.dp) }, due?.dayMonthShort() ?: ("+ " + s.dateWord.lowercase()), late) { dialog = "date" }
                    Prop({ ClockGlyph(c.graphite, 15.dp) }, q.time ?: ("+ " + s.fieldTime.lowercase())) { dialog = "time" }
                    q.from?.let { Text(s.promiseToYou(it), style = Type.small.copy(color = c.graphite, fontSize = 14.sp), modifier = Modifier.padding(top = 6.dp)) }
                }
            }
            // The user's own comment, as on a simple matter.
            Margined(Modifier.padding(top = 12.dp)) {
                var note by remember(qid) { mutableStateOf(q.note.orEmpty()) }
                LaunchedEffect(note) { if (note.trim() != q.note.orEmpty().trim()) { kotlinx.coroutines.delay(700); vm.setNote(ref, note) } }
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.chip).padding(horizontal = 12.dp, vertical = 10.dp)) {
                    if (note.isEmpty()) Text(s.commentHint, style = Type.body.copy(color = c.graphite, fontSize = 15.sp))
                    BasicTextField(note, { note = it }, textStyle = Type.body.copy(color = c.ink, fontSize = 15.sp), cursorBrush = SolidColor(c.link), modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
    when (dialog) {
        "date" -> QuestionDateDialog(q, onDismiss = { dialog = null }) { d -> vm.setQuestionDue(topic.id, q.id, d); dialog = null }
        "time" -> TimeDialog(q.time, onDismiss = { dialog = null }) { tm -> vm.setTime(ref, tm); dialog = null }
    }
}
