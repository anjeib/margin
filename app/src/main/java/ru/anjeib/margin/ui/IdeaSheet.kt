package ru.anjeib.margin.ui

import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.outlined.DeleteOutline
import ru.anjeib.margin.data.Entry
import ru.anjeib.margin.data.Task

/**
 * An idea on half the screen, written like a notebook page: the title and the text are edited right where they stand,
 * each piece with its day and hour on the margin; an empty line at the end takes the next thought. The whole feed
 * (files, matters) is one tap further — «Открыть целиком».
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdeaSheet(task: Task, vm: MainVm, onClose: () -> Unit, onOpenFull: () -> Unit) {
    val c = M.c
    val s = M.s
    var title by remember(task.id) { mutableStateOf(task.title) }
    fun commitTitle() {
        val cur = vm.data.value.tasks.find { it.id == task.id } ?: return
        if (title.isNotBlank() && title.trim() != cur.title) vm.saveTask(cur.copy(title = title.trim()))
    }
    DisposableEffect(task.id) { onDispose { commitTitle() } }
    val owner = "task:${task.id}"
    // Written on the card itself (owner, 13 окт); the bar under it — the microphone and «send», «+» on the page.
    val pageDraft = remember(task.id) { mutableStateOf("") }
    val pageFiles = remember(task.id) { androidx.compose.runtime.mutableStateListOf<ru.anjeib.margin.data.Attachment>() }
    val pageSend = remember(task.id) { PageSend() }
    androidx.compose.runtime.DisposableEffect(task.id) { onDispose { pageSend.send() } }
    var writeTick by remember { mutableStateOf(0) }
    ModalBottomSheet(
        onDismissRequest = { commitTitle(); onClose() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.bg,
        scrimColor = c.scrim,
        dragHandle = { Box(Modifier.padding(top = 10.dp, bottom = 2.dp).size(36.dp, 4.dp).clip(CircleShape).background(c.hairline)) },
    ) {
        val half = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp * 0.5f).dp
        Column(Modifier.fillMaxWidth().heightIn(min = half).imePadding().navigationBarsPadding()) {
        val scroll = rememberScrollState()
            val pageLines = rememberPageLines()
        Column(Modifier.weight(1f, fill = false).fillMaxWidth().ruledPage(pageLines).screenRule().verticalScroll(scroll).padding(bottom = 24.dp)) {
            // The same card as a matter (owner's call, 10 окт): ⋯ for the idea as a whole, its bulb and title in serif;
            // under a line — what was written in it, each piece with its day and hour.
            // Owner, 14 окт: the same card as a matter — no ⋯, the sphere under the title, its tools in a row, a paperclip below.
            androidx.compose.foundation.layout.Spacer(Modifier.size(12.dp))
            CardHead(title, { title = it }, false, onToggle = {}, margin = null, meta = emptyList(), mark = { IdeaMark(28.dp) },
                sphere = task.sphere, onSphere = { vm.setSphere(task.id, it) })
            Margined(Modifier.padding(top = 10.dp)) {
                ActionBar(listOf(
                    Action(s.actToMatter, false, { commitTitle(); vm.ideaToMatter(task.id); onClose() }) { androidx.compose.foundation.layout.Box(Modifier.size(20.dp)) { CheckCircle(false, size = 16.dp, interactive = false) {} } },
                    Action(s.delete, false, { vm.removeMatter(task.id); onClose() }) {
                        androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Outlined.DeleteOutline, null, tint = it, modifier = Modifier.size(21.dp)) },
                ), slots = 5)
            }
            val born = task.history.firstOrNull { it.kind == "created" }?.at ?: task.createdAt
            WrittenLane(task.entries.filter { it.text.isNotBlank() }.map { LaneItem(it.id, it.at, it.text) }, s.ideaHint, Modifier.padding(top = 10.dp), after = null, label = s.records,
                draft = pageDraft, focusKey = writeTick.takeIf { it > 0 }, vm = vm, files = pageFiles, onKeep = { pageSend.send() }, pasteTo = "task:${task.id}", plus = Plus.None,
                onEdit = { id, t -> if (t.isBlank()) vm.removeRecord(task.id, id) else vm.editEntryText(owner, id, t.trim()) },
                onAdd = { t, at -> vm.insertEntry(owner, null, Entry(at = at, text = t)) })
            // Files, matters and all the rest of it — on the full page; offered only when the card doesn't show it all (owner, 13 окт).
            val hidden = task.entries.any { it.attachments.isNotEmpty() || it.text.isBlank() } || task.subtasks.isNotEmpty()
            if (hidden || scroll.maxValue > 0) Margined(Modifier.padding(top = 14.dp)) { Voice(s.openWhole + "  ›", link = true, onClick = { commitTitle(); onOpenFull() }) }
            Margined { CreatedLine(born) }
            PageEnd(pageLines)
        }
        Column(Modifier.fillMaxWidth().fieldRule().padding(horizontal = 14.dp).padding(bottom = 10.dp)) {
            SmartComposer(vm, s.writeOnPage, false, onSend = { t, f ->
                // An idea's words stay words: a record with its photos and files, nothing parsed out of them.
                if (t.isNotBlank() || f.isNotEmpty()) vm.insertEntry(owner, null, Entry(text = t.trim(), attachments = f))
            }, pageText = pageDraft, onWrite = { writeTick++ }, pageSend = pageSend, pageFiles = pageFiles, slash = false, marksOnPlus = true)
        }
        }
    }
}
