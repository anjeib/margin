package ru.anjeib.margin.ui

import ru.anjeib.margin.data.dayMonthShort

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
    val rowActs = LocalRowActions.current
    var more by remember(task.id) { mutableStateOf(false) }
    // A page of the planner, as every card (owner, 14 окт; design/v9/v9-card-note.png).
    CardPage(onClose = { commitTitle(); onClose() }) {
        Column(Modifier.weight(1f).fillMaxWidth()) {
            val scroll = rememberScrollState()
            val pageLines = rememberPageLines()
            Column(Modifier.weight(1f).fillMaxWidth().ruledPage(pageLines).screenRule().verticalScroll(scroll).padding(bottom = 24.dp)) {
                // When it was begun — its first record — on the margin as a day of the diary, the weekday under it.
                val bornAt = task.entries.minOfOrNull { it.at } ?: task.history.firstOrNull { it.kind == "created" }?.at ?: task.createdAt
                val bornDay = runCatching { java.time.LocalDate.parse(bornAt.take(10)) }.getOrNull()
                androidx.compose.foundation.layout.Spacer(Modifier.size(4.dp))
                CardHead(title, { title = it }, false, onToggle = {}, margin = { CardMargin(bornDay, under = bornDay?.weekdayWord()) }, meta = emptyList(),
                    noMark = true, kind = s.cardKindNote, sphere = task.sphere, onSphere = { vm.setSphere(task.id, it) })
                Margined(Modifier.padding(top = 10.dp)) {
                    Column {
                        // Owner, 14 окт: the same row of tools as every card; three things, so no «Ещё».
                        ActionBar(listOfNotNull(
                            Action(s.actToMatter, false, { commitTitle(); vm.ideaToMatter(task.id); onClose() }) { DealGlyph(it, 22.dp) },
                            rowActs?.let { r -> Action(s.actToTopic, false, { commitTitle(); onClose(); r.toTopic(task.id) }) { TopicGlyph(it, 19.dp) } },
                            // Owner, 14 окт: put away (it stays in «Архив», with «Отменить») before deleting.
                            Action(s.toArchive, false, { commitTitle(); vm.archiveTopic(task.id); onClose() }) { ArchiveGlyph(it, 22.dp) },
                            Action(s.delete, false, { vm.removeMatter(task.id); onClose() }) { TrashGlyph(it, 22.dp) },
                        ), slots = 5)
                    }
                }
                // Everything of the note is here (owner, 14 окт: no second, «whole» view of it) — each file in its own record.
                WrittenLane(task.entries.filter { it.text.isNotBlank() || it.attachments.isNotEmpty() }.map { LaneItem(it.id, it.at, it.text, it.attachments) }, s.ideaHint, Modifier.padding(top = 10.dp), after = null, label = s.records,
                    canAdd = false, vm = vm,
                    onEdit = { id, t -> if (t.isBlank()) vm.removeRecord(task.id, id) else vm.editEntryText(owner, id, t.trim()) },
                    onAdd = { t, at -> vm.insertEntry(owner, null, Entry(at = at, text = t)) })
                // Begun when its first words were written (owner, 14 окт: it said «создано» on the day it was last touched).
                bornDay?.let { d -> Margined { Voice(s.begunOn(d.dayMonthShort()), Modifier.padding(top = 22.dp, bottom = 6.dp)) } }
                PageEnd(pageLines)
            }
            Column(Modifier.fillMaxWidth().background(c.bg).padding(bottom = 10.dp).fieldBand().fieldRule().padding(start = 14.dp, end = 4.dp).padding(top = 8.dp, bottom = 8.dp)) {
                SmartComposer(vm, s.cardNoteHint, false, onSend = { t, f ->
                    // An idea's words stay words: a record with its photos and files, nothing parsed out of them.
                    if (t.isNotBlank() || f.isNotEmpty()) vm.insertEntry(owner, null, Entry(text = t.trim(), attachments = f))
                }, slash = false, floating = planner)
            }
        }
    }
}
