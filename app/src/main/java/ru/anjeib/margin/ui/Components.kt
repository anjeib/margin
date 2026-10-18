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
fun CheckCircle(done: Boolean, size: Dp = 24.dp, onLong: (() -> Unit)? = null, interactive: Boolean = true,
                /** Ignored since v8 (the ring is one colour); kept so old call sites compile. */
                tint: Color? = null, onToggle: () -> Unit) {
    val c = M.c
    val d = size * 0.9f
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
        // v8: a thin ring that reads as something to tap; done — it fills with petrol and a tick comes up on it.
        val ring = androidx.compose.ui.graphics.lerp(if (planner) c.ink.copy(alpha = 0.62f) else c.graphite.copy(alpha = 0.7f), c.link, p)
        val tick = c.bg
        androidx.compose.foundation.Canvas(Modifier.size(d)) {
            val w = this.size.width; val h = this.size.height
            val r = w * 0.46f
            val sw = 1.5.dp.toPx()
            if (p > 0f) drawCircle(c.link.copy(alpha = p), radius = r, center = androidx.compose.ui.geometry.Offset(w / 2, h / 2))
            drawCircle(ring, radius = r - sw / 2, center = androidx.compose.ui.geometry.Offset(w / 2, h / 2), style = androidx.compose.ui.graphics.drawscope.Stroke(width = sw))
            val q = ((p - 0.35f) / 0.65f).coerceIn(0f, 1f)
            if (q > 0f) drawPath(androidx.compose.ui.graphics.Path().apply { moveTo(w * 0.3f, h * 0.52f); lineTo(w * 0.44f, h * 0.65f); lineTo(w * 0.7f, h * 0.38f) },
                tick.copy(alpha = q), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.8.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round,
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
    if (planner) {
        Column(modifier.clip(RoundedCornerShape(6.dp)).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text, style = Type.small.copy(color = if (filled || accent) c.ink else c.graphite, fontSize = 14.5.sp,
                fontWeight = if (filled || accent) androidx.compose.ui.text.font.FontWeight.Medium else null), maxLines = 1)
            Box(Modifier.padding(top = 4.dp).width(16.dp).height(1.5.dp).background(if (filled || accent) c.sea else Color.Transparent))
        }
        return
    }
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
    Text(text.uppercase(), style = Type.label.copy(color = M.c.graphite, letterSpacing = 1.5.sp), modifier = modifier)
}

@Composable
fun Hairline(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(M.c.hairline))
}

// ---------------- top tabs ----------------

// ---------------- task card ----------------

/** Who is a contact and what a tap on a name does — given by the app, so rows anywhere can link names. */
/** What a mark in a line opens (owner, 14 окт: every mark its own card): a name — the person, «#метка» — its topic or the search. */
data class PeopleLinks(val isContact: (String) -> Boolean = { false }, val onName: (String) -> Unit = {}, val onTag: (String) -> Unit = {})
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
             hidePerson: String? = null,
             /** v8: the topic a matter belongs to, first and petrol — «# Кофейня «Зерно»». */
             topic: String? = null,
             /** v8: a last quiet word — «с 25 сен» for a matter lying without a day. */
             extra: String? = null,
             /** The topic opens on its own tap (Fable, 0.35: it looked like a link and opened the matter). */
             onTopic: (() -> Unit)? = null) {
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
        topic?.let { tp ->
            next()
            if (onTopic != null) withLink(androidx.compose.ui.text.LinkAnnotation.Clickable("topic", androidx.compose.ui.text.TextLinkStyles(androidx.compose.ui.text.SpanStyle(color = c.link))) { onTopic() }) { append("# $tp") }
            else withStyle(androidx.compose.ui.text.SpanStyle(color = c.link)) { append("# $tp") }
        }
        // When first, darker than the rest — the eye finds it without looking; late in accent.
        whenText?.let { w -> next()
            val st = androidx.compose.ui.text.SpanStyle(color = c.ink, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
            if (t.dueDate?.isBefore(today) == true) pencil { withStyle(st) { append(w) } } else withStyle(st) { append(w) } }
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
        if (showProject) t.project?.let { pr -> next(); link("tag", "#" + projectLabel(pr), c.link) { links.onTag(projectLabel(pr)) } }
        // The user left a comment: a small mark says it is there (the words are in the card).
        if (t.comments.isNotEmpty()) { next(); appendInlineContent("note", "≡") }
        extra?.let { next(); append(it) }
    }
    val inline = mapOf("note" to androidx.compose.foundation.text.InlineTextContent(
        androidx.compose.ui.text.Placeholder(14.sp, 14.sp, androidx.compose.ui.text.PlaceholderVerticalAlign.TextCenter)) {
        Icon(Icons.Outlined.ChatBubbleOutline, s.commentHint, tint = c.graphite, modifier = Modifier.fillMaxSize())
    }, "clip" to androidx.compose.foundation.text.InlineTextContent(
        androidx.compose.ui.text.Placeholder(13.sp, 13.sp, androidx.compose.ui.text.PlaceholderVerticalAlign.TextCenter)) {
        ClipGlyph(c.graphite, 13.dp)
    })
    if (text.isNotEmpty()) PencilText(text, style = Type.small.copy(color = subInk, fontSize = subSize), maxLines = 2, overflow = TextOverflow.Ellipsis,
        inlineContent = inline, modifier = Modifier.padding(top = 3.dp))
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
    // v8: the empty page in Margin's voice — a quiet serif line and its explanation in italic.
    Column(Modifier.fillMaxWidth().padding(vertical = 28.dp)) {
        Text(title, style = Type.cardTitle.copy(color = c.ink))
        if (hint.isNotBlank()) Voice(hint, Modifier.padding(top = 6.dp))
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
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(radius.dp),
    /** Off — just the content (on the planner page rows stand on the paper; what a swipe did is in the card). */
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val c = M.c
    if (!enabled) { Box(modifier) { content() }; return }
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
            Box(Modifier.fillMaxSize().clip(shape)
                // At rest nothing under the row — on the planner the row stands on the paper (18 окт: a white plate showed in «Входящие»).
                .background(if (dir == SwipeToDismissBoxValue.Settled) androidx.compose.ui.graphics.Color.Transparent else if (toEnd) c.accent else c.ink).padding(horizontal = 22.dp),
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
    // «Мама», «Начальник» are written small inside a phrase («позвонить маме») — compare those without case.
    val opts = if (ru.anjeib.margin.data.isCommonName(name)) setOf(RegexOption.IGNORE_CASE) else emptySet()
    return Regex("(?<!\\p{L})" + Regex.escape(stem), opts).containsMatchIn(title)
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
