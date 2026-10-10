package ru.anjeib.margin.ui

import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.foundation.text.appendInlineContent

import androidx.compose.ui.text.font.FontWeight

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
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.material.icons.outlined.Person
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.ExperimentalMaterial3Api
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CheckCircle(done: Boolean, size: Dp = 24.dp, onLong: (() -> Unit)? = null, interactive: Boolean = true, onToggle: () -> Unit) {
    val c = M.c
    // A bullet the size of the other glyphs — a small «bagel» (owner's call, 10 окт): a thick ring;
    // done — the hole closes into a sage disc and a white tick comes up on it.
    val d = size * 0.8f
    val p by androidx.compose.animation.core.animateFloatAsState(if (done) 1f else 0f, androidx.compose.animation.core.tween(280), label = "bagel")
    Box(
        Modifier
            .size(size + 16.dp)
            .clip(CircleShape)
            // In a card the long press holds what the corner menu used to: tomorrow, a week, not doing it, delete.
            // A picture of the bagel (legends, the tour) is not a control: nothing to focus or tap.
            .then(if (!interactive) Modifier else if (onLong == null) Modifier.toggleable(value = done, role = androidx.compose.ui.semantics.Role.Checkbox, onValueChange = { onToggle() })
                else Modifier.combinedClickable(role = androidx.compose.ui.semantics.Role.Checkbox, onLongClick = onLong, onClick = onToggle)),
        contentAlignment = Alignment.Center,
    ) {
        val ring = androidx.compose.ui.graphics.lerp(c.graphite.copy(alpha = 0.78f), c.ok, p)
        val tick = c.bg
        androidx.compose.foundation.Canvas(Modifier.size(d)) {
            val w = this.size.width; val h = this.size.height
            val r = w * 0.42f
            val t = r * (0.46f + 0.54f * p)
            drawCircle(ring, radius = r - t / 2, center = androidx.compose.ui.geometry.Offset(w / 2, h / 2), style = androidx.compose.ui.graphics.drawscope.Stroke(width = t))
            val q = ((p - 0.45f) / 0.55f).coerceIn(0f, 1f)
            if (q > 0f) drawPath(androidx.compose.ui.graphics.Path().apply { moveTo(w * 0.31f, h * 0.51f); lineTo(w * 0.45f, h * 0.64f); lineTo(w * 0.7f, h * 0.37f) },
                tick.copy(alpha = q), style = androidx.compose.ui.graphics.drawscope.Stroke(width = (w * 0.09f).coerceAtLeast(1.4.dp.toPx()), cap = androidx.compose.ui.graphics.StrokeCap.Round,
                    join = androidx.compose.ui.graphics.StrokeJoin.Round))
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
    val bg = when { filled -> c.ink; accent -> c.linkSoft; else -> Color.Transparent }
    val fg = when { filled -> c.bg; accent -> c.link; else -> c.ink }
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
        style = Type.small.copy(color = c.link, fontSize = 12.5.sp),
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(c.linkSoft)
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
                    Text(l, style = Type.tab.copy(color = if (i == selected) c.ink else c.graphite, fontWeight = if (i == selected) FontWeight.SemiBold else FontWeight.Medium))
                    counts?.getOrNull(i)?.takeIf { it > 0 }?.let { n ->
                        Text(" $n", style = Type.small.copy(fontSize = 14.sp, color = if (i == 0 && alertFirst) c.link else c.graphite))
                    }
                }
                Spacer(Modifier.height(5.dp))
                Box(Modifier.size(4.dp).clip(CircleShape).background(if (i == selected) c.link else Color.Transparent))
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
        Icon(if (timed) Icons.Outlined.Schedule else Icons.Outlined.CalendarToday, null, tint = if (late) c.accentText else c.graphite, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(5.dp))
        Text(text, style = Type.small.copy(color = if (late) c.accentText else c.ink, fontSize = 13.5.sp), maxLines = 1)
    }
}

/** Who is a contact and what a tap on a name does — given by the app, so rows anywhere can link names. */
data class PeopleLinks(val isContact: (String) -> Boolean = { false }, val onName: (String) -> Unit = {})
val LocalPeopleLinks = androidx.compose.runtime.compositionLocalOf { PeopleLinks() }

/**
 * The one quiet line under a task: «Вам от Марины · завтра · Ленина, 5 · #сайт».
 * Links are petrol blue: a contact opens their page, the place opens the map; a name that isn't a contact
 * stays grey and a tap offers «+ контакт». Late dates — terracotta, the one colour of attention. No pills — they live in the card.
 */
@Composable
fun TaskLine(t: Task, whenText: String?, showProject: Boolean = true, showIdea: Boolean = false, today: java.time.LocalDate = java.time.LocalDate.now(),
             /** false in the day lists: names and places are coloured words, the whole row is the one target. */
             tappable: Boolean = true,
             /** On a person's page their own name is not repeated in each row. */
             hidePerson: String? = null) {
    val c = M.c
    val s = M.s
    val links = LocalPeopleLinks.current
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val (d, n) = t.subtasks.progress()
    val sep = "  ·  "
    val text = androidx.compose.ui.text.buildAnnotatedString {
        var first = true
        fun next() { if (!first) append(sep); first = false }
        fun link(tag: String, shown: String, color: Color, onClick: () -> Unit) {
            if (!tappable) { withStyle(androidx.compose.ui.text.SpanStyle(color = color)) { append(shown) }; return }
            withLink(androidx.compose.ui.text.LinkAnnotation.Clickable(tag, androidx.compose.ui.text.TextLinkStyles(androidx.compose.ui.text.SpanStyle(color = color))) { onClick() }) { append(shown) }
        }
        fun name(n0: String, shown: String = n0) = link("p:$n0", shown, if (links.isContact(n0)) c.link else c.graphite) { links.onName(n0) }
        // When first, darker than the rest — the eye finds it without looking; late in accent.
        whenText?.let { next(); withStyle(androidx.compose.ui.text.SpanStyle(color = if (t.dueDate?.isBefore(today) == true) c.accentText else c.ink, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)) { append(it) } }
        // A name already said in the title («Скинуть Лёше исходники») is not repeated under it.
        if (!t.done) {
            val parts = t.waitingFor?.let { s.promiseToYouParts(it) to it } ?: t.promisedTo?.let { s.promiseFromYouParts(it) to it }
            parts?.let { (p, who) -> next(); if (namedIn(t.title, who)) append(if (t.waitingFor != null) s.a11yWaiting else s.a11yPromised) else { append(p.first); name(who, p.second); append(p.third) } }
        }
        t.people.filter { p -> p != t.waitingFor && p != t.promisedTo && p != s.me && !namedIn(t.title, p) && p != hidePerson }.take(2).forEach { next(); name(it) }
        t.place?.let { pl ->
            next()
            link("place", pl, c.link) {
                runCatching { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("geo:0,0?q=" + android.net.Uri.encode(pl)))) }
            }
        }
        if (n > 0) { next(); append(s.progressOf(d, n)) }
        // The loop itself is in the column on the left; here only the words.
        t.repeat?.let { next(); append(it.label()) }
        val files = t.entries.sumOf { it.attachments.size }
        if (files > 0) { next(); appendInlineContent("clip", "📎"); append(" $files") }
        if (showIdea && t.noteId != null) { next(); append("✎ " + s.ideaMark) }
        if (showProject) t.project?.let { next(); append("#" + projectLabel(it)) }
        // The user left a comment: a small mark says it is there (the words are in the card).
        if (t.comments.isNotEmpty()) { next(); appendInlineContent("note", "≡") }
    }
    val inline = mapOf("note" to androidx.compose.foundation.text.InlineTextContent(
        androidx.compose.ui.text.Placeholder(14.sp, 14.sp, androidx.compose.ui.text.PlaceholderVerticalAlign.TextCenter)) {
        Icon(Icons.Outlined.ChatBubbleOutline, s.commentHint, tint = c.graphite, modifier = Modifier.fillMaxSize())
    }, "clip" to androidx.compose.foundation.text.InlineTextContent(
        androidx.compose.ui.text.Placeholder(13.sp, 13.sp, androidx.compose.ui.text.PlaceholderVerticalAlign.TextCenter)) {
        ClipGlyph(c.graphite, 13.dp)
    })
    if (text.isNotEmpty()) Text(text, style = Type.small.copy(color = c.graphite, fontSize = 13.5.sp), maxLines = 2, overflow = TextOverflow.Ellipsis,
        inlineContent = inline, modifier = Modifier.padding(top = 3.dp))
}

/** A person as a badge with the whole name — «Марина Петрова», not «М». */
@Composable
fun PersonBadge(name: String, big: Boolean = false, onClick: (() -> Unit)? = null) {
    val c = M.c
    Row(Modifier.clip(RoundedCornerShape(50)).background(c.chip).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
        .padding(start = if (big) 10.dp else 7.dp, end = if (big) 12.dp else 9.dp, top = if (big) 6.dp else 3.dp, bottom = if (big) 6.dp else 3.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.Person, null, tint = c.graphite, modifier = Modifier.size(if (big) 15.dp else 13.dp))
        Spacer(Modifier.width(4.dp))
        Text(name, style = Type.small.copy(color = c.ink, fontSize = if (big) 14.sp else 13.sp), maxLines = 1)
    }
}

/** The task's line in a card: the same quiet line as in lists; [timed] — the time already stands on the left. */
@Composable
fun TaskMeta(t: Task, showProject: Boolean = true, showIdea: Boolean = false, timed: Boolean = false) {
    // On «Сегодня» with the hour on the left, «сегодня 16:00» would only repeat it.
    val whenText = if (timed && t.dueDate == java.time.LocalDate.now()) null else t.whenLabel()
    TaskLine(t, whenText, showProject, showIdea)
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
                    Text(e.text, style = Type.small.copy(color = c.graphite, fontSize = 13.5.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (!t.done) {
                    Spacer(Modifier.height(5.dp))
                    TaskMeta(t, showProject, showIdea, timed = timeColumn != null)
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
        number?.let { Text("$it.", style = Type.small.copy(color = c.graphite, fontSize = 13.sp), modifier = Modifier.width(22.dp)) }
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
        if (running) Box(Modifier.fillMaxWidth(f).height(2.dp).background(c.link))
    }
}

/**
 * A row that answers a swipe: to the left [left] (a soft action — «В архив»), to the right [right] («Удалить»).
 * The row springs back; the list itself shows what changed, and «Отменить» is offered below.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeActions(
    left: Pair<String, () -> Unit>?,
    right: Pair<String, () -> Unit>?,
    modifier: Modifier = Modifier,
    radius: Int = 18,
    content: @Composable () -> Unit,
) {
    val c = M.c
    val state = rememberSwipeToDismissBoxState(confirmValueChange = { v ->
        when (v) {
            SwipeToDismissBoxValue.EndToStart -> left?.second?.invoke()
            SwipeToDismissBoxValue.StartToEnd -> right?.second?.invoke()
            else -> {}
        }
        false
    })
    SwipeToDismissBox(
        state = state, modifier = modifier,
        enableDismissFromEndToStart = left != null, enableDismissFromStartToEnd = right != null,
        backgroundContent = {
            val dir = state.dismissDirection
            val toEnd = dir == SwipeToDismissBoxValue.StartToEnd
            Box(Modifier.fillMaxSize().clip(RoundedCornerShape(radius.dp))
                .background(if (dir == SwipeToDismissBoxValue.Settled) c.bg else if (toEnd) c.accent else c.ink).padding(horizontal = 22.dp),
                contentAlignment = if (toEnd) Alignment.CenterStart else Alignment.CenterEnd) {
                if (dir != SwipeToDismissBoxValue.Settled) Text((if (toEnd) right?.first else left?.first).orEmpty(), style = Type.tab.copy(color = c.bg))
            }
        },
    ) { content() }
}


/** Is [name] already said in [title], in any case form («Лёша» in «Скинуть Лёше исходники»)? Compares the stem. */
fun namedIn(title: String, name: String): Boolean {
    // Names are written with a capital in titles (data/Writing.kt), so the match is case-sensitive: «Маша» ≠ «машина».
    val stem = name.trim().split(' ').first().let { if (it.length > 4) it.dropLast(2) else it.take(3) }
    if (stem.length < 3) return false
    return Regex("(?<!\\p{L})" + Regex.escape(stem)).containsMatchIn(title)
}

/**
 * «a · b · c» that wraps like words: a separator stands only between two things on the same line — never hanging
 * at a line's end or starting the next one — and every part sits on one centre line.
 */
@Composable
fun DotFlow(count: Int, modifier: Modifier = Modifier, gap: androidx.compose.ui.unit.Dp = 0.dp, separator: @Composable () -> Unit, item: @Composable (Int) -> Unit) {
    androidx.compose.ui.layout.Layout(content = {
        for (i in 0 until count) {
            if (i > 0) separator()
            item(i)
        }
    }, modifier = modifier) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val placeables = measurables.map { it.measure(loose) }
        if (placeables.isEmpty()) return@Layout layout(constraints.minWidth, constraints.minHeight) {}
        val maxW = constraints.maxWidth
        val gapPx = gap.roundToPx()
        // Lines of (placeable, x); separators kept only between neighbours on one line.
        val lines = mutableListOf<MutableList<Pair<androidx.compose.ui.layout.Placeable, Int>>>(mutableListOf())
        var x = 0
        var i = 0
        while (i < placeables.size) {
            val itemP = placeables[i]
            if (i == 0) { lines.last().add(itemP to 0); x = itemP.width; i++; continue }
            val sep = placeables[i]; val next = placeables[i + 1]
            if (x + sep.width + next.width <= maxW || lines.last().isEmpty()) {
                lines.last().add(sep to x); x += sep.width
                lines.last().add(next to x); x += next.width
            } else {
                lines.add(mutableListOf(next to 0)); x = next.width
            }
            i += 2
        }
        val heights = lines.map { l -> l.maxOf { it.first.height } }
        val width = if (constraints.hasBoundedWidth) lines.maxOf { l -> l.maxOf { it.second + it.first.width } }.coerceAtMost(maxW) else lines.maxOf { l -> l.maxOf { it.second + it.first.width } }
        val height = heights.sum() + gapPx * (lines.size - 1).coerceAtLeast(0)
        layout(width.coerceAtLeast(constraints.minWidth), height.coerceAtLeast(constraints.minHeight)) {
            var y = 0
            lines.forEachIndexed { li, l ->
                l.forEach { (p, px) -> p.placeRelative(px, y + (heights[li] - p.height) / 2) }
                y += heights[li] + gapPx
            }
        }
    }
}
