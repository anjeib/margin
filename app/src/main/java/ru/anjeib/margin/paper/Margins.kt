package ru.anjeib.margin.paper

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * The three zones of a page (DESIGN.md §3): the margin with Margin's notes, the mark on the rule, your words.
 * Everything that shows a line goes through [MarginRow], so every screen keeps the same grid.
 */

/** The margin rule down the whole height of what it is drawn behind. */
fun Modifier.rule(color: Color): Modifier = drawBehind {
    val x = Grid.margin.toPx()
    drawLine(color, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.5.dp.toPx())
}

/** What a mark on the rule shows. */
enum class MarkKind { Open, Done, Dropped, Moved, Event, Note, None }

/**
 * A note of Margin's and the role of its colour. [struck] — the old value of a correction («~~чт~~ пт»).
 * [under] — written under your words rather than on the side margin: the side keeps «when», the rest goes beneath.
 */
data class Note(val text: String, val tone: Tone = Tone.Quiet, val struck: String? = null, val under: Boolean = false)

enum class Tone { Quiet, Attention, Link, Gone }

/**
 * A line of a page: notes right-aligned on the margin (their baseline on your words' first baseline), the mark on the
 * rule, then the words. The whole line opens; the mark has its own touch across the rule, the line's full height.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MarginRow(
    notes: List<Note>,
    mark: MarkKind,
    modifier: Modifier = Modifier,
    markLabel: String? = null,
    onMark: (() -> Unit)? = null,
    onOpen: (() -> Unit)? = null,
    onLong: (() -> Unit)? = null,
    indent: Dp = 0.dp,
    /** The next empty line: its mark in pencil until something is written. */
    pale: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    val c = P.c
    Box(modifier.fillMaxWidth().heightIn(min = Grid.line)
        .then(if (onOpen != null || onLong != null) Modifier.combinedClickable(onClick = { onOpen?.invoke() }, onLongClick = onLong) else Modifier)) {
        // The side margin keeps «when»; who, where, the thread and the rest are written under the words —
        // the margin stays light and the line reads as one thing.
        val side = notes.filter { !it.under }
        val under = notes.filter { it.under }
        Row(Modifier.fillMaxWidth().padding(end = Grid.right)) {
            Column(Modifier.width(Grid.margin - Grid.noteGap).padding(start = 4.dp, top = 15.dp, bottom = 8.dp), horizontalAlignment = Alignment.End) {
                side.forEach { NoteText(it) }
            }
            Spacer(Modifier.width(Grid.text - Grid.margin + Grid.noteGap + indent))
            Column(Modifier.weight(1f).padding(top = 12.dp, bottom = 12.dp)) {
                Row(Modifier.fillMaxWidth(), content = content)
                if (under.isNotEmpty()) UnderNotes(under, Modifier.padding(top = 2.dp))
            }
        }
        // The mark sits on the rule; its touch is the rule's strip across the whole line.
        val touch = Modifier.align(Alignment.TopStart).padding(start = Grid.margin - Grid.markTouch / 2 + indent).width(Grid.markTouch)
        Box(touch.heightIn(min = Grid.line).then(
            if (onMark != null) Modifier.toggleable(value = mark == MarkKind.Done, role = Role.Checkbox, onValueChange = { onMark() },
                interactionSource = remember { MutableInteractionSource() }, indication = null)
                .semantics { markLabel?.let { contentDescription = it } } else Modifier,
        ), contentAlignment = Alignment.TopCenter) {
            Mark(mark, Modifier.padding(top = 16.dp), pale = pale)
        }
    }
}

/** One note: Margin's voice, small italic serif; a correction shows the old value struck before the new. */
@Composable
fun NoteText(n: Note, align: TextAlign = TextAlign.End) {
    val c = P.c
    val color = when (n.tone) { Tone.Quiet -> c.graphite; Tone.Attention -> c.red; Tone.Link -> c.link; Tone.Gone -> c.pencil }
    androidx.compose.foundation.layout.BoxWithConstraints {
        // The margin is narrow, and a word is never cut in two («Сидорово / й»): a note that does not fit breaks between
        // words, and if it still needs more than two lines, or a word is wider than the margin, it is written smaller.
        val measurer = androidx.compose.ui.text.rememberTextMeasurer()
        val room = constraints.maxWidth
        fun ink(t: String) = buildAnnotatedString {
            n.struck?.let { withStyle(SpanStyle(color = c.pencil, textDecoration = TextDecoration.LineThrough)) { append(it) }; append(" ") }
            // Signs (← → ↻ ≡) in the plain sans: the serif italic draws them as feathered ornaments.
            t.forEach { ch -> if (ch in SIGNS) withStyle(SpanStyle(fontFamily = Sans, fontStyle = androidx.compose.ui.text.font.FontStyle.Normal)) { append(ch) } else append(ch) }
        }
        val (text, size) = remember(n.text, n.struck, room) {
            if (!constraints.hasBoundedWidth) return@remember n.text to Voice.note.fontSize
            val spaced = n.text.replace('\u00A0', ' ')
            val base = Voice.note.fontSize.value
            fun sizes(down: Float) = generateSequence(base) { it - 0.5f }.takeWhile { it >= base * down }
            fun fits(t: String, sz: Float): Boolean {
                val a = ink(t)
                val r = measurer.measure(a, Voice.note.copy(fontSize = sz.sp, textAlign = align), maxLines = 2,
                    constraints = androidx.compose.ui.unit.Constraints(maxWidth = room))
                return !r.hasVisualOverflow && (0 until r.lineCount - 1).all { i ->
                    val e = r.getLineEnd(i, visibleEnd = true)
                    e >= a.length || a[e].isWhitespace() || !a[e - 1].isLetter() || !a[e].isLetter()
                }
            }
            // Most notes fit as they are. Then «→ Ковалёву» a little smaller, on one line; then broken between words.
            if (fits(n.text, base)) return@remember n.text to base.sp
            if (spaced != n.text) sizes(0.85f).drop(1).firstOrNull { fits(n.text, it) }?.let { return@remember n.text to it.sp }
            sizes(0.72f).firstOrNull { fits(spaced, it) }?.let { return@remember spaced to it.sp }
            spaced to (base * 0.72f).sp
        }
        Text(ink(text), style = Voice.note.copy(color = color, textAlign = align, fontSize = size), maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** Margin's notes under your words, in one quiet line: «→ Лёше · ≡ Зерно · 1 из 3». */
@Composable
fun UnderNotes(notes: List<Note>, modifier: Modifier = Modifier) {
    val c = P.c
    Text(buildAnnotatedString {
        notes.forEachIndexed { i, n ->
            if (i > 0) withStyle(SpanStyle(color = c.pencil)) { append("  ·  ") }
            val color = when (n.tone) { Tone.Quiet -> c.graphite; Tone.Attention -> c.red; Tone.Link -> c.link; Tone.Gone -> c.pencil }
            withStyle(SpanStyle(color = color)) {
                n.text.forEach { ch -> if (ch in SIGNS) withStyle(SpanStyle(fontFamily = Sans, fontStyle = androidx.compose.ui.text.font.FontStyle.Normal)) { append(ch) } else append(ch) }
            }
        }
    }, style = Voice.note, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = modifier)
}

private const val SIGNS = "←→↻≡›"

private val linkLike = Regex("(?:https?://\\S+|www\\.\\S+|\\b[\\w-]+\\.(?:ru|com|org|net|us|io|me|рф|su|app|dev)(?:/\\S*)?|\\+?\\d[\\d\\s()-]{8,}\\d)", RegexOption.IGNORE_CASE)

/** Your words with their addresses and phones as links — a meeting link or a number written under a line opens. */
fun linked(text: String, color: Color): androidx.compose.ui.text.AnnotatedString = buildAnnotatedString {
    var at = 0
    linkLike.findAll(text).forEach { m ->
        append(text.substring(at, m.range.first))
        val v = m.value.trimEnd('.', ',', ')')
        val url = when {
            v.first() == '+' || v.first().isDigit() -> "tel:" + v.filter { it.isDigit() || it == '+' }
            v.startsWith("http", true) -> v
            else -> "https://$v"
        }
        withLink(androidx.compose.ui.text.LinkAnnotation.Url(url, androidx.compose.ui.text.TextLinkStyles(SpanStyle(color = color)))) { append(v) }
        append(m.value.substring(v.length))
        at = m.range.last + 1
    }
    append(text.substring(at))
}

/** Margin's words with its signs (← → ↻ ≡ ›) in the plain sans: the serif italic draws them as feathered ornaments. */
fun withPlainSigns(text: String): androidx.compose.ui.text.AnnotatedString = buildAnnotatedString {
    text.forEach { ch -> if (ch in SIGNS) withStyle(SpanStyle(fontFamily = Sans, fontStyle = androidx.compose.ui.text.font.FontStyle.Normal)) { append(ch) } else append(ch) }
}

/** The mark on the rule: • to do, × done, › moved, ○ event, – note. A node of paper cuts the rule under it. */
@Composable
fun Mark(kind: MarkKind, modifier: Modifier = Modifier, size: Dp = Grid.node, pale: Boolean = false) {
    if (kind == MarkKind.None) { Spacer(modifier.size(size)); return }
    val c = P.c
    val ground = LocalGround.current ?: c.paper
    val done by animateFloatAsState(if (kind == MarkKind.Done || kind == MarkKind.Dropped) 1f else 0f, tween(250), label = "mark")
    Canvas(modifier.size(size)) {
        val w = this.size.width; val h = this.size.height
        val cx = w / 2; val cy = h / 2
        drawCircle(ground, radius = w / 2, center = Offset(cx, cy))
        val ink = if (kind == MarkKind.Dropped || pale) c.pencil else c.ink
        when (kind) {
            MarkKind.Open, MarkKind.Done, MarkKind.Dropped -> {
                // The dot shrinks as the cross is drawn over it — the same gesture as in a paper notebook.
                val dotR = 3.dp.toPx() * (1f - done)
                if (dotR > 0.2f) drawCircle(c.ink, radius = dotR, center = Offset(cx, cy))
                if (done > 0f) {
                    val a = 4.dp.toPx() * done
                    val col = if (kind == MarkKind.Dropped) c.pencil else c.graphite
                    val st = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round)
                    drawLine(col, Offset(cx - a, cy - a), Offset(cx + a, cy + a), strokeWidth = st.width, cap = StrokeCap.Round)
                    drawLine(col, Offset(cx + a, cy - a), Offset(cx - a, cy + a), strokeWidth = st.width, cap = StrokeCap.Round)
                }
            }
            MarkKind.Moved -> {
                val p = Path().apply { moveTo(cx - 2.5.dp.toPx(), cy - 4.dp.toPx()); lineTo(cx + 2.5.dp.toPx(), cy); lineTo(cx - 2.5.dp.toPx(), cy + 4.dp.toPx()) }
                drawPath(p, c.graphite, style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round))
            }
            MarkKind.Event -> drawCircle(ink, radius = 4.5.dp.toPx(), center = Offset(cx, cy), style = Stroke(width = 1.5.dp.toPx()))
            MarkKind.Note -> drawLine(ink, Offset(cx - 4.dp.toPx(), cy), Offset(cx + 4.dp.toPx(), cy), strokeWidth = 1.6.dp.toPx(), cap = StrokeCap.Round)
            MarkKind.None -> {}
        }
    }
}

/** A page's head: the date on the margin (a tap — the month), the title right of the rule, Margin's line under it. */
@Composable
fun PageHead(marginTop: String, marginBottom: String?, title: String, line: String?, onMargin: (() -> Unit)? = null, onLine: (() -> Unit)? = null,
             lineColor: Color = P.c.graphite) {
    val c = P.c
    Row(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 10.dp, end = Grid.right)) {
        Box(Modifier.width(Grid.margin - Grid.noteGap).then(if (onMargin != null) Modifier.combinedClickableCompat(onMargin) else Modifier),
            contentAlignment = Alignment.TopEnd) {
            Column(horizontalAlignment = Alignment.End) {
                Text(marginTop, style = Voice.title.copy(color = c.ink, textAlign = TextAlign.End))
                marginBottom?.let { Text(it, style = Voice.small.copy(color = c.graphite)) }
            }
        }
        Spacer(Modifier.width(Grid.text - Grid.margin + Grid.noteGap))
        Column(Modifier.weight(1f)) {
            Text(title, style = Voice.title.copy(color = c.ink))
            if (!line.isNullOrBlank()) Text(line, style = Voice.note.copy(color = lineColor),
                modifier = Modifier.padding(top = 4.dp).then(if (onLine != null) Modifier.combinedClickableCompat(onLine) else Modifier))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.combinedClickableCompat(onClick: () -> Unit) = this.combinedClickable(onClick = onClick)

/** Margin's voice in the body of a page: a quiet sentence right of the rule (an empty page, a hint). */
@Composable
fun MarginSays(text: String, modifier: Modifier = Modifier, color: Color = P.c.graphite) {
    Row(modifier.fillMaxWidth().padding(end = Grid.right)) {
        Spacer(Modifier.width(Grid.text))
        Text(withPlainSigns(text), style = Voice.reply.copy(color = color), modifier = Modifier.weight(1f))
    }
}

/** A quiet link in Margin's voice («перелистнуть день ›»), right of the rule, a full line high. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MarginLink(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(modifier.fillMaxWidth().heightIn(min = Grid.line).combinedClickable(onClick = onClick).padding(end = Grid.right),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Start) {
        Spacer(Modifier.width(Grid.text))
        Text(text, style = Voice.reply.copy(color = P.c.link))
    }
}
