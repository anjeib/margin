package ru.anjeib.margin.ui

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
    ModalBottomSheet(
        onDismissRequest = { commitTitle(); onClose() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.bg,
        scrimColor = c.scrim,
        dragHandle = { Box(Modifier.padding(top = 10.dp, bottom = 2.dp).size(36.dp, 4.dp).clip(CircleShape).background(c.hairline)) },
    ) {
        val half = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp * 0.5f).dp
        Column(Modifier.fillMaxWidth().heightIn(min = half).imePadding().navigationBarsPadding().screenRule().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            // The same card as a matter (owner's call, 10 окт): ⋯ for the idea as a whole, its bulb and title in serif;
            // under a line — what was written in it, each piece with its day and hour.
            Margined {
                CardMenu { close ->
                    SoftMenuItem(s.openWhole) { close(); commitTitle(); onOpenFull() }
                    SoftMenuItem(if (task.sphere == "work") s.makePersonal else s.makeWork) { close(); vm.setSphere(task.id, nextSphere(task.sphere)) }
                    SoftMenuItem(s.delete, danger = true) { close(); vm.removeMatter(task.id); onClose() }
                }
            }
            Margined(Modifier.padding(top = 0.dp)) {
                CardTitle(title, { title = it }, false, task.sphere, onToggle = {}, mark = { IdeaMark(30.dp) })
            }
            Hairline(Modifier.padding(start = MarginW, top = 14.dp))
            val born = task.history.firstOrNull { it.kind == "created" }?.at ?: task.createdAt
            WrittenLane(task.entries.filter { it.text.isNotBlank() }.map { LaneItem(it.id, it.at, it.text) }, s.ideaHint, Modifier.padding(top = 4.dp), after = null,
                onEdit = { id, t -> if (t.isBlank()) vm.removeRecord(task.id, id) else vm.editEntryText(owner, id, t.trim()) },
                onAdd = { t, at -> vm.insertEntry(owner, null, Entry(at = at, text = t)) })
            // Files, matters and all the rest of it — on the full page.
            Margined(Modifier.padding(top = 18.dp)) { PaperLink(s.openWhole + "  ›", quiet = true) { commitTitle(); onOpenFull() } }
            Margined { CreatedLine(born) }
        }
    }
}
