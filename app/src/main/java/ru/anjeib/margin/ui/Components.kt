package ru.anjeib.margin.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.anjeib.margin.data.Subtask
import ru.anjeib.margin.data.Task
import ru.anjeib.margin.data.isOverdue
import ru.anjeib.margin.data.label
import ru.anjeib.margin.data.progress
import ru.anjeib.margin.data.whenLabel

// ---------------- primitives ----------------

/** Four-point spark — the assistant's mark. */
@Composable
fun Spark(size: Dp = 14.dp, color: Color = M.c.accent) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width; val h = this.size.height
        val p = Path().apply {
            moveTo(w / 2, 0f)
            quadraticTo(w * 0.56f, h * 0.44f, w, h / 2)
            quadraticTo(w * 0.56f, h * 0.56f, w / 2, h)
            quadraticTo(w * 0.44f, h * 0.56f, 0f, h / 2)
            quadraticTo(w * 0.44f, h * 0.44f, w / 2, 0f)
            close()
        }
        drawPath(p, color)
    }
}

/** Two-line menu glyph, lighter than the stock hamburger. */
@Composable
fun MenuGlyph(color: Color = M.c.ink) {
    Canvas(Modifier.size(22.dp)) {
        val y1 = size.height * 0.36f; val y2 = size.height * 0.64f
        drawLine(color, Offset(size.width * 0.12f, y1), Offset(size.width * 0.88f, y1), strokeWidth = 1.6.dp.toPx())
        drawLine(color, Offset(size.width * 0.12f, y2), Offset(size.width * 0.62f, y2), strokeWidth = 1.6.dp.toPx())
    }
}

@Composable
fun CheckCircle(done: Boolean, size: Dp = 24.dp, onToggle: () -> Unit) {
    val c = M.c
    val fill by animateColorAsState(if (done) c.ink else Color.Transparent, label = "check")
    Box(
        Modifier
            .size(size + 16.dp)
            .clip(CircleShape)
            .toggleable(value = done, role = androidx.compose.ui.semantics.Role.Checkbox, onValueChange = { onToggle() }),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(size)
                .clip(CircleShape)
                .background(fill)
                .border(1.4.dp, if (done) c.ink else c.faint, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (done) Icon(Icons.Rounded.Check, null, tint = c.bg, modifier = Modifier.size(size * 0.62f))
        }
    }
}

@Composable
fun Pill(
    text: String,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    accent: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val c = M.c
    val bg = when { filled -> c.ink; accent -> c.accentSoft; else -> Color.Transparent }
    val fg = when { filled -> c.bg; accent -> c.accent; else -> c.ink }
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .then(if (!filled && !accent) Modifier.border(1.dp, c.hairline, RoundedCornerShape(50)) else Modifier)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) { Text(text, style = Type.small.copy(color = fg, fontSize = 14.sp)) }
}

/** Projects are stored as typed by the model (lowercase); Russian names read better with a capital: «Садовая», «Дента+». */
fun projectLabel(p: String): String = if (p.firstOrNull()?.let { it in 'а'..'я' || it == 'ё' } == true) p.replaceFirstChar { it.uppercase() } else p

@Composable
fun Tag(text: String) {
    val c = M.c
    Text(
        "#" + projectLabel(text),
        style = Type.small.copy(color = c.accent, fontSize = 12.5.sp),
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(c.accentSoft)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

@Composable
fun Monogram(name: String, size: Dp = 22.dp) {
    val c = M.c
    Box(
        Modifier.size(size).clip(CircleShape).background(c.chip),
        contentAlignment = Alignment.Center,
    ) { Text(name.take(1).uppercase(), style = Type.small.copy(color = c.ink, fontSize = (size.value * 0.48f).sp)) }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = Type.label.copy(color = M.c.graphite), modifier = modifier)
}

@Composable
fun Hairline(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(M.c.hairline))
}

@Composable
fun MetaIcon(icon: ImageVector, tint: Color = M.c.graphite) {
    Icon(icon, null, tint = tint, modifier = Modifier.size(14.dp))
}

// ---------------- top tabs ----------------

@Composable
fun QuietTabs(labels: List<String>, selected: Int, onSelect: (Int) -> Unit, counts: List<Int>? = null, alertFirst: Boolean = false) {
    val c = M.c
    Row(horizontalArrangement = Arrangement.spacedBy(22.dp), verticalAlignment = Alignment.CenterVertically) {
        labels.forEachIndexed { i, l ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onSelect(i) }
                    .padding(horizontal = 4.dp, vertical = 6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(l, style = Type.tab.copy(color = if (i == selected) c.ink else c.graphite))
                    counts?.getOrNull(i)?.takeIf { it > 0 }?.let { n ->
                        Text(" $n", style = Type.small.copy(fontSize = 13.sp, color = if (i == 0 && alertFirst) c.accent else c.faint))
                    }
                }
                Spacer(Modifier.height(5.dp))
                Box(Modifier.size(4.dp).clip(CircleShape).background(if (i == selected) c.accent else Color.Transparent))
            }
        }
    }
}

// ---------------- task card ----------------

/** When a task is: on its own soft ground with an icon, so the eye tells it from the title at once. Late — in accent. */
@Composable
fun DatePill(text: String, late: Boolean, timed: Boolean) {
    val c = M.c
    Row(Modifier.clip(RoundedCornerShape(50)).background(if (late) c.accentSoft else c.chip).padding(start = 8.dp, end = 10.dp, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(if (timed) Icons.Outlined.Schedule else Icons.Outlined.CalendarToday, null, tint = if (late) c.accent else c.graphite, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(5.dp))
        Text(text, style = Type.small.copy(color = if (late) c.accent else c.ink, fontSize = 13.5.sp), maxLines = 1)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TaskMeta(t: Task, showProject: Boolean = true, showIdea: Boolean = false) {
    val c = M.c
    val s = M.s
    val overdue = t.isOverdue()
    val (d, n) = t.subtasks.progress()
    val whenText = t.whenLabel()
    if (whenText == null && t.place == null && t.project == null && t.people.isEmpty() && t.waitingFor == null && t.promisedTo == null && n == 0 && t.repeat == null && t.entries.none { it.attachments.isNotEmpty() } && !(showIdea && t.noteId != null)) return
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        if (showProject) t.project?.let { Tag(it) }
        if (whenText != null) DatePill(whenText, overdue, t.time != null)
        t.promisedTo?.let { Text(s.promised(it), style = Type.small.copy(color = c.graphite)) }
        t.place?.let { Text(it, style = Type.small.copy(color = c.graphite, fontSize = 12.5.sp), maxLines = 1, overflow = TextOverflow.Ellipsis) }
        if (showIdea && t.noteId != null) Text("✎ " + s.ideaMark, style = Type.small.copy(color = c.graphite))
        t.waitingFor?.let {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MetaIcon(Icons.Outlined.HourglassEmpty)
                Spacer(Modifier.width(4.dp))
                Text(s.waitingFor(it), style = Type.small.copy(color = c.graphite))
            }
        }
        if (t.people.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy((-5).dp)) {
            t.people.take(3).forEach { Monogram(it, 20.dp) }
        }
        if (n > 0) Text("$d/$n", style = Type.small.copy(color = c.graphite))
        t.repeat?.let { Text("↻ " + it.label(), style = Type.small.copy(color = c.graphite)) }
        val files = t.entries.sumOf { it.attachments.size }
        if (files > 0) Text("📎 $files", style = Type.small.copy(color = c.graphite))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TaskCard(
    t: Task,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    onToggleSub: (String) -> Unit,
    modifier: Modifier = Modifier,
    timeColumn: String? = null,
    showProject: Boolean = true,
    showIdea: Boolean = false,
) {
    val c = M.c
    var expanded by rememberSaveable(t.id) { mutableStateOf(false) }
    val rot by animateFloatAsState(if (expanded) 180f else 0f, label = "chev")
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(c.card)
            .combinedClickable(onClick = onOpen)
            .padding(start = 4.dp, end = 10.dp, top = 6.dp, bottom = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (timeColumn != null) {
                Text(timeColumn, style = Type.small.copy(color = c.graphite), modifier = Modifier.padding(start = 12.dp).width(44.dp))
            }
            CheckCircle(t.done, onToggle = onToggle)
            Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                Text(
                    t.title,
                    style = Type.task.copy(
                        color = if (t.done) c.graphite else c.ink,
                        textDecoration = if (t.done) TextDecoration.LineThrough else null,
                    ),
                    maxLines = 3, overflow = TextOverflow.Ellipsis,
                )
                // The last thing written about it, faint — no need to open the card to remember.
                if (!t.done) t.entries.lastOrNull { it.text.isNotBlank() }?.let { e ->
                    Text(e.text, style = Type.small.copy(color = c.faint, fontSize = 13.5.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (!t.done) {
                    Spacer(Modifier.height(5.dp))
                    TaskMeta(t, showProject, showIdea)
                }
            }
            if (t.subtasks.isNotEmpty() && !t.done) {
                Box(
                    Modifier.size(36.dp).clip(CircleShape).clickable { expanded = !expanded },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.KeyboardArrowDown, null, tint = c.graphite, modifier = Modifier.rotate(rot)) }
            }
        }
        AnimatedVisibility(expanded && !t.done, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(Modifier.padding(start = if (timeColumn != null) 56.dp else 0.dp, bottom = 4.dp)) {
                SubtaskTree(t.subtasks, 0, onToggleSub)
            }
        }
    }
}

@Composable
fun SubtaskTree(list: List<Subtask>, depth: Int, onToggle: (String) -> Unit, onLong: ((Subtask) -> Unit)? = null, number: (Subtask) -> Int? = { null }) {
    list.forEach { s ->
        SubtaskRow(s, depth, { onToggle(s.id) }, onLong?.let { { it(s) } }, if (depth == 0) number(s) else null)
        if (s.children.isNotEmpty()) SubtaskTree(s.children, depth + 1, onToggle, onLong)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SubtaskRow(s: Subtask, depth: Int, onToggle: () -> Unit, onLong: (() -> Unit)? = null, number: Int? = null) {
    val c = M.c
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onLong != null) Modifier.combinedClickable(onClick = onLong, onLongClick = onLong) else Modifier)
            .padding(start = (8 + depth * 22).dp),
    ) {
        CheckCircle(s.done, size = 18.dp, onToggle = onToggle)
        // The number lets one say «подзадача 2» to the field below.
        number?.let { Text("$it.", style = Type.small.copy(color = c.faint, fontSize = 13.sp), modifier = Modifier.width(22.dp)) }
        Text(
            s.title + (s.assignee?.let { " — $it" } ?: ""),
            style = Type.body.copy(
                fontSize = 15.sp,
                color = if (s.done) c.graphite else c.ink,
                textDecoration = if (s.done) TextDecoration.LineThrough else null,
            ),
            modifier = Modifier.weight(1f).padding(vertical = 4.dp),
        )
    }
}

@Composable
fun Empty(title: String, hint: String) {
    val c = M.c
    Column(Modifier.fillMaxWidth().padding(vertical = 48.dp, horizontal = 8.dp)) {
        Text(title, style = Type.title.copy(color = c.ink))
        Spacer(Modifier.height(8.dp))
        Text(hint, style = Type.body.copy(color = c.graphite))
    }
}

/** A thin bar that fills over [durationMs] — the auto-confirm countdown. */
@Composable
fun Countdown(running: Boolean, durationMs: Long, key: Any) {
    val c = M.c
    var started by remember(key) { mutableStateOf(false) }
    val f by animateFloatAsState(
        if (started && running) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(if (running) durationMs.toInt() else 0, easing = androidx.compose.animation.core.LinearEasing),
        label = "countdown",
    )
    androidx.compose.runtime.LaunchedEffect(key) { started = true }
    Box(Modifier.fillMaxWidth().height(2.dp).background(c.hairline)) {
        if (running) Box(Modifier.fillMaxWidth(f).height(2.dp).background(c.accent))
    }
}
