package ru.anjeib.margin.ui

import ru.anjeib.margin.data.personIn
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
fun DealSheet(topic: Task, qid: String, vm: MainVm, onClose: () -> Unit, onOpenTopic: (() -> Unit)?, onOpenPerson: ((String) -> Unit)? = null,
              /** A matter under another one: its parent's card opens from the line under the title (by the parent's id). */
              onOpenParent: ((String) -> Unit)? = null) {
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

    // A page of the planner, as every card (owner, 14 окт; design/v9/v9-card-deal.png).
    CardPage(onClose = { commit(); onClose() }, trailing = {
        androidx.compose.animation.AnimatedVisibility(savedAt > 0, Modifier.padding(end = 18.dp),
            enter = androidx.compose.animation.fadeIn(), exit = androidx.compose.animation.fadeOut()) {
            Text("✓ " + s.savedTick, style = Type.small.copy(color = c.ok, fontSize = 12.5.sp))
        }
    }) {
        Column(Modifier.weight(1f, fill = cardFill).fillMaxWidth()) {
            // At least half the screen with the field right under it — the same as a simple matter's card.
            val pageLines = rememberPageLines()
            Column(Modifier.weight(1f, fill = cardFill).screenRule()) {
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
                fun person(n: String) { vm.nameTapped(n, vm.data.value.personIn(n, q.who)?.id) }
                // Its topic — a way back to it — at the top; no ⋯ (owner, 14 окт): what it can do is under «Ещё».
                // The same card as a simple matter (owner, 14 окт): its topic is a mark in the line under the title, not a line above it.
                Spacer(Modifier.height(4.dp))
                fun tick() {
                    val next = q.slots.firstOrNull { !it.done }
                    if (next != null) vm.toggleSlot(ref, next.id) else vm.toggleQuestion(topic.id, q.id)
                }
                val meta = buildList {
                    if (window) row.whenLabel()?.substringBefore(" · ${q.time}")?.let { add(MetaItem(Meta.plain(it), Meta.quiet()) { dialog = "date" }) }
                    q.from?.let { add(MetaItem(Meta.promise(s.promiseToYouParts(it)), Meta.quiet()) { person(it) }) }
                    q.promisedTo?.let { add(MetaItem(Meta.promise(s.promiseFromYouParts(it)), Meta.quiet()) { person(it) }) }
                    people.forEach { p -> add(MetaItem(Meta.plain(p), Meta.link()) { person(p) }) }
                    q.place?.let { add(MetaItem(Meta.plain(it), Meta.link(), onLong = { fill("place") }) {
                        runCatching { ctxOpen.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("geo:0,0?q=" + android.net.Uri.encode(it)))) }
                    }) }
                    q.repeat?.let { add(MetaItem(Meta.plain("↻ " + it.label()), Meta.quiet()) { dialog = "repeat" }) }
                }
                CardHead(title, { title = it }, q.done, onToggle = ::tick, kind = s.cardKindDeal.takeIf { q.from == null && q.promisedTo == null },
                    // Where it belongs, over the title in one line (owner, 14 окт): its topic, then the matter it is part of.
                    trail = listOfNotNull(
                        MetaItem(Meta.plain("# " + topic.title), Meta.link()) { commit(); onOpenTopic?.invoke() ?: onClose() },
                        flat(topic.subtasks).find { it.id == q.parent }?.let { p -> MetaItem(Meta.plain("○ " + p.title), Meta.link()) { commit(); onOpenParent?.invoke(p.id) } },
                    ),
                    // A topic's matter is in its topic's sphere: the same two marks set it for the topic.
                    sphere = topic.sphere, onSphere = { vm.setSphere(topic.id, it) },
                    // Owner, 16 окт («как в заметках»): the head is about the matter; the matter itself is under the rule.
                    margin = if (q.slots.isEmpty()) ({ CardMargin(due, null, late, prefix = windowPrefix(start, due), strongPrefix = true, under = due?.weekdayWord(), onDay = { dialog = "date" }) }) else null,
                    meta = meta, showTitle = false, tools = {
                    Column {
                        // Owner, 14 окт: the tools are not lit by what is set — that is said on the margin and in the line above.
                        ActionBar(listOfNotNull(
                            if (planner) Action(s.markDone, q.done, ::tick) { TickGlyph(it, 22.dp) } else null,
                            Action(s.actWhen, false, { dialog = "date" }) { CalendarGlyph(it, 22.dp) },
                            Action(s.fieldRepeat, false, { dialog = "repeat" }) { RepeatGlyph(it, 22.dp) },
                            Action(s.actPeople, false, { fill("people") }) { PeopleGlyph(it, 22.dp) },
                            Action(s.fieldPlace, false, { fill("place") }) { PinGlyph(it, 23.dp) },
                            Action(s.actMore, more, { more = !more }) { MoreGlyph(it, 23.dp) },
                        ))
                        androidx.compose.animation.AnimatedVisibility(more) {
                            MoreActions(listOfNotNull(
                                Action(s.actWait, false, { dialog = "waiting" }) { HandGlyph(it, 23.dp, incoming = true) },
                                Action(s.actPromised, false, { dialog = "promised" }) { HandGlyph(it, 23.dp) },
                                if (!q.done) rowActs?.let { r -> Action(s.actToTopic, false, { commit(); onClose(); r.toTopic(ref) }) { TopicGlyph(it, 19.dp) } } else null,
                                Action(s.actToIdea, false, { commit(); vm.dealToIdea(topic.id, q.id); onClose() }) { NoteGlyph(it, 21.dp) },
                                // Owner, 16 окт: into the topic's «Архив», not deleted.
                                Action(s.toArchive, false, { vm.archiveMatter(ref); onClose() }) { ArchiveGlyph(it, 22.dp) },
                                Action(s.deleteNow, false, { onClose(); vm.deleteNow(ref) }) { TrashGlyph(it, 22.dp) },
                            ))
                        }
                    }
                })
                if (q.slots.isNotEmpty()) Margined(Modifier.padding(top = 4.dp)) { DaysProp(q.slots, onToggle = { vm.toggleSlot(ref, it) }, onRemove = { vm.removeSlot(ref, it) }) }
                // Owner, 16 окт: the head (day, title, marks, tools) stays put; only what is under it scrolls.
                Column(Modifier.weight(1f, fill = cardFill).fillMaxWidth().ruledPage(pageLines).verticalScroll(rememberScrollState()).padding(bottom = 12.dp)) {
                CardRule()
                MatterTitle(title, { title = it }, q.done, q.time, if (q.slots.isEmpty() && due != null) ({ dialog = "time" }) else null, late,
                    mark = if ((q.from != null || q.promisedTo != null) && !q.done) ({ Box(Modifier.clip(CircleShape).clickable(onClick = ::tick)) { PromiseMark(incoming = q.from != null, size = 28.dp) } }) else null)
                val born = topic.events.firstOrNull { it.ref == q.id && (it.kind == "q" || it.kind == "wait") }?.at ?: topic.createdAt
                WrittenLane(q.comments.map { LaneItem(it.id, it.at, it.text) }, s.commentHint, Modifier.padding(top = 4.dp), after = null, label = s.commentsTitle,
                    canAdd = false, heading = s.records, vm = vm,
                    onEdit = { id, t -> vm.editComment(ref, id, t); saved() }, onAdd = { t, at -> vm.addComment(ref, t, at); saved() })
                ArchiveBlock(q.removedComments.map { cm -> ArchItem(cm.id, cm.text, cm.at, restore = { vm.restoreComment(ref, cm.id) }, purge = { vm.purgeComment(ref, cm.id) }) })
                // Their day has come: «напомнить» here too, the same as on a simple matter (one behaviour for every wait).
                // Owner, 14 окт: no «напомнить» inside the card — it is on the row on «Сегодня».
                Margined { CreatedLine(born) }
                PageEnd(pageLines)
                }
            }
            // Changes in words go to the topic, said about this matter; «+ дело» puts a matter under it.
            val scopeKey = MainVm.key(Scope.InTask(topic.id))
            val modeLabel = when (mode) { "step" -> s.stepOne; "place" -> s.fieldPlace; "people" -> s.fieldPeople; else -> null }
            val notice by vm.notice.collectAsState()
            Column(Modifier.fillMaxWidth().background(c.bg).padding(bottom = 10.dp).fieldBand().fieldRule().padding(start = 14.dp, end = 4.dp).padding(top = 8.dp, bottom = 8.dp)) {
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
                vm, if (mode == "step") s.stepHint else modeLabel?.let { s.fillHint(it) } ?: s.cardDealHint, busy == scopeKey,
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
                // Owner, 16 окт: a real field — the only way to add to the matter — drawn as the main one.
                slash = true, floating = planner,
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
