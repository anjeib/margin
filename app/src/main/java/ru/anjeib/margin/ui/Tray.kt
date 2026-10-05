package ru.anjeib.margin.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.anjeib.margin.data.AppData
import ru.anjeib.margin.data.ChatMsg
import ru.anjeib.margin.data.progress
import ru.anjeib.margin.data.whenLabel

private val bubbleShape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 6.dp)

/**
 * The latest exchange, floating above the composer. The user's bubble turns into the result in place
 * («задача», «заметка»…) with Edit · Cancel for a few seconds, then leaves — the item settles into its list.
 * Only real conversation stays visible: answers, questions with quick replies, errors.
 */
@Composable
fun Tray(
    data: AppData,
    fromId: String?,
    pending: Pending?,
    busy: Boolean,
    canUndo: (String) -> Boolean,
    onUndo: (String) -> Unit,
    onOpenTask: (String) -> Unit,
    onToggleTask: (String) -> Unit,
    onToggleSub: (String, String) -> Unit,
    onOption: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    onEdit: () -> Unit,
    onReject: () -> Unit,
    onHold: () -> Unit,
    onSeparate: () -> Unit = {},
) {
    val c = M.c
    val s = M.s
    val idx = fromId?.let { id -> data.chat.indexOfFirst { it.id == id } } ?: -1
    // Bare confirmations ("Сохранено…") aren't conversation — the result itself says it.
    val msgs = (if (idx >= 0) data.chat.drop(idx).takeLast(6) else emptyList())
        .filter { m -> m.fromUser || m.error || m.text.isNotBlank() || m.options.isNotEmpty() }
    if (msgs.isEmpty() && pending == null && !busy) return
    val lastUser = msgs.lastOrNull { it.fromUser }?.id
    val scroll = rememberScrollState()
    LaunchedEffect(msgs.size, pending?.id, busy) { scroll.animateScrollTo(scroll.maxValue) }

    Column(Modifier.fillMaxWidth()) {
        val talking = pending == null && !busy
        Row(Modifier.fillMaxWidth().height(if (talking) 30.dp else 10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (talking) {
                Hairline(Modifier.weight(1f))
                Box(
                    Modifier.padding(start = 8.dp).size(30.dp).clip(CircleShape).clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Close, s.dismiss, tint = c.graphite, modifier = Modifier.size(16.dp)) }
            }
        }
        Column(
            Modifier.fillMaxWidth().heightIn(max = 300.dp).verticalScroll(scroll).padding(bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            msgs.forEach { m ->
                if (m.fromUser) {
                    val mine = pending?.takeIf { it.userMsgId == m.id }
                    // Same slot, new content: the words become the task / note.
                    AnimatedContent(
                        targetState = mine,
                        transitionSpec = { (fadeIn(tween(260, delayMillis = 80)) togetherWith fadeOut(tween(160))).using(SizeTransform(clip = false)) },
                        contentKey = { it?.id ?: "raw" },
                        label = "bubble",
                    ) { p ->
                        if (p != null) ResultBubble(p, data, onConfirm, onEdit, onReject, onHold, onSeparate)
                        else UserBubble(m, working = busy && m.id == lastUser)
                    }
                } else AssistantMsg(m, canUndo(m.id), onUndo = { onUndo(m.id) }, onOpenTask = onOpenTask, onOption = onOption, onOpenSettings = onOpenSettings)
            }
            // A result without a user bubble (e.g. from a suggestion) still gets its own bubble.
            if (pending != null && msgs.none { it.id == pending.userMsgId }) ResultBubble(pending, data, onConfirm, onEdit, onReject, onHold, onSeparate)
        }
    }
}

@Composable
private fun UserBubble(m: ChatMsg, working: Boolean) {
    val c = M.c
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Column(
            Modifier.padding(start = 48.dp).widthIn(max = 320.dp).clip(bubbleShape).background(c.bubble)
                .padding(horizontal = 16.dp, vertical = 11.dp),
        ) {
            Text(m.text, style = Type.body.copy(color = c.onBubble))
            Box(Modifier.align(Alignment.End)) {
                if (working) Dots(c.onBubble)
                else Text(m.at, style = Type.small.copy(color = c.onBubble.copy(alpha = 0.5f), fontSize = 11.sp))
            }
        }
    }
}

/** Three softly breathing dots — the AI is reading. */
@Composable
private fun Dots(color: androidx.compose.ui.graphics.Color) {
    val t = rememberInfiniteTransition(label = "dots")
    Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(3) { i ->
            val a by t.animateFloat(0.2f, 0.8f, infiniteRepeatable(tween(600, delayMillis = i * 150), RepeatMode.Reverse), label = "d$i")
            Box(Modifier.size(5.dp).alpha(a).clip(CircleShape).background(color))
        }
    }
}

@Composable
private fun ResultBubble(p: Pending, current: AppData, onConfirm: () -> Unit, onEdit: () -> Unit, onReject: () -> Unit, onHold: () -> Unit, onSeparate: () -> Unit = {}) {
    val c = M.c
    val s = M.s
    val after = p.preview.data
    var created = 0; var changed = 0; var notes = 0; var people = 0; var appended = false
    val lines = buildList {
        p.preview.touchedTasks.forEach { id ->
            val t = after.tasks.find { it.id == id } ?: return@forEach
            if (current.tasks.none { it.id == id }) created++ else changed++
            val (_, n) = t.subtasks.progress()
            add(t.title to listOfNotNull(
                t.whenLabel(), t.waitingFor?.let { s.waitingFor(it) },
                t.people.filterNot { it.equals(t.waitingFor, true) }.takeIf { it.isNotEmpty() }?.joinToString(", "), if (n > 0) s.stepsCount(n) else null,
            ).joinToString(" · "))
        }
        after.notes.forEach { n ->
            val old = current.notes.find { it.id == n.id }
            if (old == null || old.entries != n.entries) {
                notes++
                if (old != null) appended = true
                add(n.title to (n.entries.lastOrNull()?.text?.takeIf { it != n.title }.orEmpty()))
            }
        }
        after.persons.forEach { pr ->
            val old = current.persons.find { it.id == pr.id }
            if (old == null && pr.about != null || old != null && (old.about != pr.about || old.phone != pr.phone)) {
                people++
                add(pr.name to listOfNotNull(pr.about, pr.phone).joinToString(" · "))
            }
        }
        if (isEmpty() && p.preview.caption != null) add(p.preview.caption to "")
    }
    val kind = listOfNotNull(
        when { created == 1 -> s.kindTask; created > 1 -> s.kindTasks(created); else -> null },
        if (changed > 0 && created == 0) s.kindChanged else null,
        if (notes > 0) s.kindNote else null,
        if (people > 0) s.kindContact else null,
    ).joinToString(" · ")

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
        Column(
            Modifier.padding(start = 48.dp).widthIn(max = 320.dp).clip(bubbleShape).background(c.bubble).clickable(onClick = onHold),
        ) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp)) {
                lines.forEachIndexed { i, (title, meta) ->
                    if (i > 0) Spacer(Modifier.height(8.dp))
                    Text(title, style = Type.cardTitle.copy(fontSize = 17.sp, color = c.onBubble), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (meta.isNotBlank()) Text(meta, style = Type.small.copy(color = c.onBubble.copy(alpha = 0.6f)), maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                if (p.smart) p.plan.reply?.takeIf { it.isNotBlank() }?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = Type.small.copy(color = c.onBubble.copy(alpha = 0.75f)))
                }
                if (kind.isNotBlank()) Text(kind, style = Type.label.copy(color = c.onBubble.copy(alpha = 0.55f), fontSize = 10.5.sp),
                    modifier = Modifier.align(Alignment.End).padding(top = 6.dp))
            }
            if (p.auto) Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 8.dp)) { BubbleCountdown(p.id) }
        }
        Row(Modifier.padding(top = 4.dp, end = 4.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            if (!p.auto) QuietLink(s.create, onConfirm)
            if (appended) QuietLink(s.toSeparateNote, onSeparate)
            QuietLink(s.edit, onEdit)
            QuietLink(s.undo, onReject)
        }
    }
}

/** A hairline that empties while the result waits to be saved. */
@Composable
private fun BubbleCountdown(key: Any) {
    val c = M.c
    var started by remember(key) { mutableStateOf(false) }
    val f by androidx.compose.animation.core.animateFloatAsState(
        if (started) 0f else 1f, tween(MainVm.AUTO_CONFIRM_MS.toInt(), easing = androidx.compose.animation.core.LinearEasing), label = "cd",
    )
    LaunchedEffect(key) { started = true }
    Box(Modifier.fillMaxWidth().height(2.dp).clip(CircleShape).background(c.onBubble.copy(alpha = 0.12f))) {
        Box(Modifier.fillMaxWidth(f).height(2.dp).background(c.onBubble.copy(alpha = 0.45f)))
    }
}

@Composable
private fun AssistantMsg(
    m: ChatMsg,
    canUndo: Boolean,
    onUndo: () -> Unit,
    onOpenTask: (String) -> Unit,
    onOption: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val c = M.c
    val s = M.s
    Column(Modifier.fillMaxWidth().padding(end = 24.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Box(Modifier.padding(top = 5.dp)) { Spark() }
            Spacer(Modifier.width(10.dp))
            // Long answers fold to six lines; tap to read the rest.
            var full by rememberSaveable(m.id) { mutableStateOf(false) }
            var overflow by remember(m.id) { mutableStateOf(false) }
            Column {
                Text(
                    m.text, style = Type.body.copy(color = if (m.error) c.accent else c.ink),
                    maxLines = if (full) Int.MAX_VALUE else 6, overflow = TextOverflow.Ellipsis,
                    onTextLayout = { if (!full) overflow = it.hasVisualOverflow },
                    modifier = Modifier.clickable(enabled = overflow || full) { full = !full },
                )
                if (overflow && !full) Text(s.readMore, style = Type.small.copy(color = c.graphite), modifier = Modifier.clickable { full = true }.padding(top = 2.dp))
            }
        }
        val chips = buildList<Pair<String, () -> Unit>> {
            m.options.forEach { o -> add(o to { onOption(o) }) }
            if (m.options.isNotEmpty() && m.taskIds.isNotEmpty()) add(s.edit to { onOpenTask(m.taskIds.first()) })
            if (canUndo && m.options.isNotEmpty()) add(s.undo to onUndo)
            if (m.error && m.text == s.noKey) add(s.openSettings to onOpenSettings)
        }
        if (chips.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Row(Modifier.padding(start = 24.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                chips.forEach { (label, act) -> Pill(label, onClick = act) }
            }
        }
    }
}
