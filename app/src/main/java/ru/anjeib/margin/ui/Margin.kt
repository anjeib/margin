package ru.anjeib.margin.ui

import androidx.compose.foundation.layout.PaddingValues

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * «Поля тетради» — the signature of Margin 2: a thin terracotta rule runs down the screen like the margin line of an
 * exercise book. Dates and hours sit on the margin, left of it; what is to be done sits right of it. In a topic the same
 * rule is the thread its events hang on.
 */

/** Width of the margin column; the rule runs at this x. */
// The margin grows with the phone's font size: at 1.3× «завтра» broke into «завтр / а» (Fable, 0.35).
// Set once per configuration by [MarginTheme]; 10 dp of air to the rule, the words' column scales with the font.
var MarginW: Dp = 74.dp
    internal set
internal fun marginFor(fontScale: Float): Dp = 10.dp + 64.dp * fontScale.coerceIn(1f, 2f)
/** Gap between the rule and the content. */
val MarginGap = 14.dp

/** A row on the page: [margin] (a date, an hour, a glyph) left of the rule, [content] right of it; [node] — a dot on the rule. */
@Composable
fun Margined(
    modifier: Modifier = Modifier,
    margin: (@Composable () -> Unit)? = null,
    node: Color? = null,
    nodeY: Dp = 13.dp,
    nodeSize: Dp = 9.dp,
    content: @Composable () -> Unit,
) {
    Row(
        modifier.fillMaxWidth().then(if (node != null) Modifier.drawBehind {
            drawCircle(node, radius = nodeSize.toPx() / 2, center = Offset(MarginW.toPx(), nodeY.toPx()))
        } else Modifier),
    ) {
        Box(Modifier.width(MarginW).padding(end = 10.dp), contentAlignment = Alignment.TopEnd) { margin?.invoke() }
        Box(Modifier.weight(1f).padding(start = MarginGap, end = 16.dp)) { content() }
    }
}

/** The rule itself, drawn behind a whole screen; [fraction] < 1 while it grows (a task becoming a topic). */
fun Modifier.marginRule(color: Color, fraction: Float = 1f, top: Dp = 0.dp, over: Boolean = false): Modifier = drawWithContent {
    // [over]: on lists the rows paint their own ground (for swipes), so the rule goes on top of them.
    if (over) drawContent()
    val x = MarginW.toPx()
    val y0 = top.toPx()
    drawLine(color, Offset(x, y0), Offset(x, y0 + (size.height - y0) * fraction.coerceIn(0f, 1f)), strokeWidth = 1.2.dp.toPx())
    if (!over) drawContent()
}

/** Small text on the margin: an hour, «завтра», «~21 окт» — Margin's grey italic (v8); [late] — terracotta; [petrol] — a date someone owes. */
@Composable
fun MarginText(text: String, late: Boolean = false, petrol: Boolean = false, faint: Boolean = false) {
    val c = M.c
    Text(text, style = Type.italic.copy(color = when { late -> c.accentText; petrol -> c.link; faint -> c.faint; else -> c.ink.copy(alpha = 0.8f) }, fontSize = 15.5.sp,
        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium, fontFeatureSettings = "tnum", textAlign = TextAlign.End, lineHeight = 19.sp), modifier = Modifier.padding(top = 3.dp))
}

/** The one look of a date or an hour inside a line: ink, medium, figures of one width. */
@Composable
fun dateSpan(): androidx.compose.ui.text.SpanStyle = androidx.compose.ui.text.SpanStyle(color = M.c.ink, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium, fontFeatureSettings = "tnum")

/** A date or an hour on its own: the same look as [dateSpan]. */
@Composable
fun dateStyle(size: androidx.compose.ui.unit.TextUnit = 13.sp, late: Boolean = false) =
    Type.small.copy(color = if (late) M.c.accentText else M.c.ink, fontSize = size, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium, fontFeatureSettings = "tnum")

/** The day on the margin: a big serif number and the month under it. */
@Composable
fun MarginDay(day: Int, month: String) {
    val c = M.c
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // On the planner page the day number is pressed into the paper: a pale edge under it, as letterpress leaves.
        Text(day.toString(), style = Type.display.copy(color = c.ink, fontSize = 40.sp, lineHeight = 42.sp, fontWeight = if (planner) FontWeight(600) else null,
            shadow = if (planner && !c.dark) androidx.compose.ui.graphics.Shadow(Color.White.copy(alpha = 0.9f), androidx.compose.ui.geometry.Offset(0f, 2.5f), 0f) else null))
        Text(if (planner) month.uppercase() else month, style = if (planner) Type.small.copy(fontFamily = Book, color = c.ink.copy(alpha = 0.8f), fontSize = 12.sp,
            fontWeight = FontWeight.Medium, letterSpacing = 2.4.sp) else Type.small.copy(color = c.graphite, fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.6.sp))
    }
}

// ---------------- glyphs: one hand, one stroke ----------------

private fun DrawScope.stroke(w: Dp = 1.5.dp) = Stroke(width = w.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)

/** A topic: three lines of a feed, the last shorter. */
@Composable
fun TopicGlyph(color: Color, size: Dp = 18.dp) = Canvas(Modifier.size(size)) {
    val w = this.size.width; val h = this.size.height; val sw = 1.5.dp.toPx()
    listOf(0.28f to 1f, 0.5f to 0.78f, 0.72f to 0.55f).forEach { (y, len) ->
        drawLine(color, Offset(w * 0.1f, h * y), Offset(w * (0.1f + 0.8f * len), h * y), strokeWidth = sw, cap = StrokeCap.Round)
    }
}

/** A thought: a quiet speech bubble. */
@Composable
fun ThoughtGlyph(color: Color, size: Dp = 18.dp, line: Dp = 1.5.dp) = Canvas(Modifier.size(size)) {
    // An idea: a light bulb — the glass, its neck, two turns of the base and a filament inside.
    val w = this.size.width; val h = this.size.height
    val glass = Path().apply {
        moveTo(w * 0.38f, h * 0.66f)
        cubicTo(w * 0.38f, h * 0.56f, w * 0.2f, h * 0.5f, w * 0.2f, h * 0.36f)
        cubicTo(w * 0.2f, h * 0.18f, w * 0.34f, h * 0.07f, w * 0.5f, h * 0.07f)
        cubicTo(w * 0.66f, h * 0.07f, w * 0.8f, h * 0.18f, w * 0.8f, h * 0.36f)
        cubicTo(w * 0.8f, h * 0.5f, w * 0.62f, h * 0.56f, w * 0.62f, h * 0.66f)
        close()
    }
    drawPath(glass, color, style = stroke(line))
    drawPath(Path().apply {
        moveTo(w * 0.39f, h * 0.78f); lineTo(w * 0.61f, h * 0.78f)
        moveTo(w * 0.43f, h * 0.9f); lineTo(w * 0.57f, h * 0.9f)
        moveTo(w * 0.44f, h * 0.46f); lineTo(w * 0.5f, h * 0.36f); lineTo(w * 0.56f, h * 0.46f)
    }, color, style = stroke(line))
}

/** An idea in a list: the bulb in the graphite line of every mark (v8: no sage round). */
@Composable
fun IdeaMark(size: Dp = 28.dp) {
    Box(Modifier.size(size), contentAlignment = Alignment.Center) { ThoughtGlyph(M.c.graphite, size * 0.82f, 1.5.dp) }
}

/** «Обещания»: two people, one a little behind the other. */
@Composable
fun PeopleGlyph(color: Color, size: Dp = 16.dp) = Canvas(Modifier.size(size)) {
    val w = this.size.width; val h = this.size.height
    drawCircle(color, radius = w * 0.14f, center = Offset(w * 0.38f, h * 0.32f), style = stroke(1.4.dp))
    drawPath(Path().apply { moveTo(w * 0.12f, h * 0.84f); cubicTo(w * 0.12f, h * 0.6f, w * 0.64f, h * 0.6f, w * 0.64f, h * 0.84f) }, color, style = stroke(1.4.dp))
    drawPath(Path().apply { moveTo(w * 0.62f, h * 0.2f); cubicTo(w * 0.8f, h * 0.16f, w * 0.86f, h * 0.44f, w * 0.66f, h * 0.47f) }, color, style = stroke(1.4.dp))
    drawPath(Path().apply { moveTo(w * 0.74f, h * 0.6f); cubicTo(w * 0.84f, h * 0.64f, w * 0.9f, h * 0.72f, w * 0.9f, h * 0.84f) }, color, style = stroke(1.4.dp))
}

/** Settled: a light tick. */
@Composable
fun TickGlyph(color: Color, size: Dp = 18.dp) = Canvas(Modifier.size(size)) {
    val w = this.size.width; val h = this.size.height
    drawPath(Path().apply { moveTo(w * 0.2f, h * 0.52f); lineTo(w * 0.42f, h * 0.72f); lineTo(w * 0.8f, h * 0.3f) }, color, style = stroke(1.7.dp))
}

/** A short confirming tick in the hand — for «решено», «получено», «в архив». Silent where the phone can't. */
fun View.confirmTick() {
    performHapticFeedback(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.KEYBOARD_TAP)
}

/** Is motion allowed? The system's «Убрать анимацию» turns every flourish into an instant change. */
@Composable
fun motionOn(): Boolean {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    return remember { android.provider.Settings.Global.getFloat(ctx.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f }
}

/** The way new lines arrive: a short rise and fade, one after another. */
@Composable
fun Modifier.arrive(fresh: Boolean, index: Int = 0): Modifier {
    if (!fresh || !motionOn()) return this
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(70L * index); a.animateTo(1f, tween(280, easing = FastOutSlowInEasing)) }
    return this.graphicsLayer { alpha = a.value; translationY = (1f - a.value) * 14.dp.toPx() }
}

/** Words with a line drawn through them as [strike] goes 0→1 — like a pen, not a switch. */
@Composable
fun StrikeText(text: String, style: androidx.compose.ui.text.TextStyle, strike: Float, lineColor: Color, modifier: Modifier = Modifier, maxLines: Int = Int.MAX_VALUE) {
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    Text(text, style = style, maxLines = maxLines, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, onTextLayout = { layout = it },
        modifier = modifier.drawWithContent {
            drawContent()
            val l = layout ?: return@drawWithContent
            if (strike <= 0f) return@drawWithContent
            // Line by line, the way a pen goes: the first line fills, then the next.
            val n = l.lineCount
            for (i in 0 until n) {
                val part = ((strike * n) - i).coerceIn(0f, 1f)
                if (part <= 0f) break
                val y = (l.getLineTop(i) + l.getLineBottom(i)) / 2
                val x0 = l.getLineLeft(i); val x1 = l.getLineRight(i)
                drawLine(lineColor, Offset(x0, y), Offset(x0 + (x1 - x0) * part, y), strokeWidth = 1.3.dp.toPx())
            }
        })
}

// ---------------- the soft menu: rounded, grows from its corner, fades away ----------------

/** Below the anchor, its right edges lined up; above it when there is no room below. */
private class MenuPosition(private val gap: Int) : androidx.compose.ui.window.PopupPositionProvider {
    override fun calculatePosition(anchorBounds: androidx.compose.ui.unit.IntRect, windowSize: androidx.compose.ui.unit.IntSize,
                                   layoutDirection: androidx.compose.ui.unit.LayoutDirection, popupContentSize: androidx.compose.ui.unit.IntSize): androidx.compose.ui.unit.IntOffset {
        val x = (anchorBounds.right - popupContentSize.width).coerceIn(gap, (windowSize.width - popupContentSize.width - gap).coerceAtLeast(gap))
        val below = anchorBounds.bottom + gap
        val y = if (below + popupContentSize.height <= windowSize.height - gap) below else (anchorBounds.top - popupContentSize.height - gap).coerceAtLeast(gap)
        return androidx.compose.ui.unit.IntOffset(x, y)
    }
}

/** Margin's menu: 16 dp corners, a soft shadow, grows from the corner it opens at in ~200 ms and fades out. */
@Composable
fun SoftMenu(expanded: Boolean, onDismissRequest: () -> Unit, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    val c = M.c
    val state = remember { androidx.compose.animation.core.MutableTransitionState(false) }
    state.targetState = expanded
    if (!state.currentState && !state.targetState) return
    val gap = with(androidx.compose.ui.platform.LocalDensity.current) { 6.dp.roundToPx() }
    androidx.compose.ui.window.Popup(popupPositionProvider = remember(gap) { MenuPosition(gap) }, onDismissRequest = onDismissRequest,
        properties = androidx.compose.ui.window.PopupProperties(focusable = true)) {
        androidx.compose.animation.AnimatedVisibility(state,
            enter = androidx.compose.animation.fadeIn(tween(180)) + androidx.compose.animation.scaleIn(tween(220, easing = FastOutSlowInEasing), initialScale = 0.9f,
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(1f, 0f)),
            exit = androidx.compose.animation.fadeOut(tween(140)) + androidx.compose.animation.scaleOut(tween(160), targetScale = 0.95f,
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(1f, 0f))) {
            androidx.compose.material3.Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp), color = c.card, shadowElevation = 10.dp,
                border = androidx.compose.foundation.BorderStroke(1.dp, c.hairline)) {
                Column(Modifier.width(androidx.compose.foundation.layout.IntrinsicSize.Max).padding(vertical = 6.dp), content = content)
            }
        }
    }
}

/** One line of [SoftMenu]; [danger] — in terracotta («Удалить»); [selected] — petrol with a tick at the end (a chosen filter). */
@Composable
fun SoftMenuItem(text: String, danger: Boolean = false, selected: Boolean = false, onClick: () -> Unit) {
    val c = M.c
    Row(Modifier.fillMaxWidth().clickableSoft(onClick).padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = Type.body.copy(color = when { danger -> c.accentText; selected -> c.link; else -> c.ink }, fontSize = 15.5.sp), modifier = Modifier.weight(1f, fill = false))
        if (selected) { androidx.compose.foundation.layout.Spacer(Modifier.width(16.dp)); TickGlyph(c.link, 16.dp) }
    }
}

private fun Modifier.clickableSoft(onClick: () -> Unit): Modifier = this.then(Modifier.clickable(onClick = onClick))

/** One rhythm for every switch: quick, with a soft landing. */
val SoftTween = tween<Float>(240, easing = FastOutSlowInEasing)

/** «Обещал»: a small arrow going out. */
@Composable
fun ArrowGlyph(color: Color, size: Dp = 14.dp, incoming: Boolean = false) = Canvas(Modifier.size(size)) {
    val w = this.size.width; val h = this.size.height
    // → I owe someone; ← someone owes me: one pair of arrows for both directions of a promise.
    fun x(f: Float) = if (incoming) w * (1f - f) else w * f
    drawPath(Path().apply { moveTo(x(0.15f), h * 0.5f); lineTo(x(0.85f), h * 0.5f); moveTo(x(0.58f), h * 0.24f); lineTo(x(0.85f), h * 0.5f); lineTo(x(0.58f), h * 0.76f) }, color, style = stroke(1.4.dp))
}

/** A repeating task: a quiet loop. */
@Composable
fun RepeatGlyph(color: Color, size: Dp = 14.dp) = Canvas(Modifier.size(size)) {
    val w = this.size.width; val h = this.size.height
    drawArc(color, startAngle = 200f, sweepAngle = 280f, useCenter = false, topLeft = Offset(w * 0.16f, h * 0.16f), size = Size(w * 0.68f, h * 0.68f), style = stroke(1.4.dp))
    drawPath(Path().apply { moveTo(w * 0.12f, h * 0.3f); lineTo(w * 0.2f, h * 0.48f); lineTo(w * 0.38f, h * 0.4f) }, color, style = stroke(1.4.dp))
}

/** Files: a paperclip in the same thin line — instead of the coloured 📎 among the drawn marks. */
@Composable
fun ClipGlyph(color: Color, size: Dp = 14.dp, modifier: Modifier = Modifier) = Canvas(modifier.size(size)) {
    val w = this.size.width; val h = this.size.height
    drawPath(Path().apply {
        moveTo(w * 0.62f, h * 0.3f); lineTo(w * 0.62f, h * 0.7f)
        arcTo(androidx.compose.ui.geometry.Rect(w * 0.38f, h * 0.58f, w * 0.62f, h * 0.82f), 0f, 180f, false)
        lineTo(w * 0.38f, h * 0.22f)
        arcTo(androidx.compose.ui.geometry.Rect(w * 0.38f, h * 0.1f, w * 0.74f, h * 0.34f), 180f, 180f, false)
        lineTo(w * 0.74f, h * 0.74f)
    }, color, style = stroke(1.3.dp))
}

/** «+» of the field, on the margin: two thin strokes. */
@Composable
fun PlusGlyph(color: Color, size: Dp = 18.dp) = Canvas(Modifier.size(size)) {
    val w = this.size.width; val h = this.size.height
    drawPath(Path().apply { moveTo(w * 0.5f, h * 0.2f); lineTo(w * 0.5f, h * 0.8f); moveTo(w * 0.2f, h * 0.5f); lineTo(w * 0.8f, h * 0.5f) }, color, style = stroke(1.5.dp))
}

/** Dictation: a slim capsule on its stand, the same line as the clock and the calendar. */
@Composable
fun MicGlyph(color: Color, size: Dp = 20.dp) = Canvas(Modifier.size(size)) {
    val w = this.size.width; val h = this.size.height
    drawRoundRect(color, topLeft = Offset(w * 0.37f, h * 0.1f), size = Size(w * 0.26f, h * 0.5f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.13f), style = stroke(1.5.dp))
    drawArc(color, startAngle = 0f, sweepAngle = 180f, useCenter = false, topLeft = Offset(w * 0.24f, h * 0.3f), size = Size(w * 0.52f, h * 0.46f), style = stroke(1.5.dp))
    drawPath(Path().apply { moveTo(w * 0.5f, h * 0.76f); lineTo(w * 0.5f, h * 0.9f); moveTo(w * 0.38f, h * 0.9f); lineTo(w * 0.62f, h * 0.9f) }, color, style = stroke(1.5.dp))
}

/** Send: a thin arrow up, drawn inside the small terracotta circle. */
@Composable
fun SendGlyph(color: Color, size: Dp = 16.dp) = Canvas(Modifier.size(size)) {
    val w = this.size.width; val h = this.size.height
    drawPath(Path().apply { moveTo(w * 0.5f, h * 0.86f); lineTo(w * 0.5f, h * 0.16f); moveTo(w * 0.22f, h * 0.43f); lineTo(w * 0.5f, h * 0.15f); lineTo(w * 0.78f, h * 0.43f) }, color, style = stroke(1.8.dp))
}

/** A meeting from the phone's calendar. */
@Composable
fun CalendarGlyph(color: Color, size: Dp = 14.dp) = Canvas(Modifier.size(size)) {
    val w = this.size.width; val h = this.size.height
    drawRoundRect(color, topLeft = Offset(w * 0.14f, h * 0.22f), size = Size(w * 0.72f, h * 0.64f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.12f), style = stroke(1.4.dp))
    drawLine(color, Offset(w * 0.14f, h * 0.42f), Offset(w * 0.86f, h * 0.42f), strokeWidth = 1.4.dp.toPx())
    drawLine(color, Offset(w * 0.34f, h * 0.12f), Offset(w * 0.34f, h * 0.3f), strokeWidth = 1.4.dp.toPx(), cap = StrokeCap.Round)
    drawLine(color, Offset(w * 0.66f, h * 0.12f), Offset(w * 0.66f, h * 0.3f), strokeWidth = 1.4.dp.toPx(), cap = StrokeCap.Round)
}

/** «ДЕЛА», «ТЕМЫ»: the quiet caps label over a group — the same as [SectionHead], no pill (v8). */
@Composable
fun GroupBadge(text: String, modifier: Modifier = Modifier) {
    val c = M.c
    Text(text.uppercase(), style = Type.label.copy(color = c.graphite, letterSpacing = 1.5.sp), modifier = modifier.padding(vertical = 2.dp))
}

/** Only the vertical part of a screen's padding: on «Поля» screens the rule is the left edge. */
fun PaddingValues.vertical() = PaddingValues(top = calculateTopPadding(), bottom = calculateBottomPadding())

/** A screen's title right of the rule, a quiet line under it, an optional control at the right. */
@Composable
fun PageHead(title: String, subtitle: String? = null, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    val c = M.c
    Margined(modifier.padding(top = 14.dp, bottom = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = Type.display.copy(color = c.ink), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                if (subtitle != null) Voice(subtitle, Modifier.padding(top = 2.dp))
            }
            trailing?.invoke()
        }
    }
}

/** The rule behind a whole screen, the same on every one. */
@Composable
fun Modifier.screenRule(): Modifier = marginRule(M.c.accent.copy(alpha = 0.85f), over = true)

/** The same rule under the field at the bottom: drawn behind, so bubbles and pills over it keep their ground. */
@Composable
fun Modifier.fieldRule(): Modifier = marginRule(M.c.accent.copy(alpha = 0.85f))

/** Where the field's words begin when its column is padded 14 dp from the edge: just right of the rule. */
val FieldLead: Dp get() = MarginW - 14.dp + 4.dp

/** A petrol word, medium — a link as everywhere in the app (no underline); [quiet] — graphite. */
@Composable
fun PaperLink(text: String, quiet: Boolean = false, danger: Boolean = false, onClick: () -> Unit) {
    val c = M.c
    // A finger's height (≈40 dp) for a few words: the padding is the target, the words stay small.
    Text(text, style = Type.small.copy(color = when { danger -> c.accentText; quiet -> c.graphite; else -> c.link }, fontSize = 14.sp,
        fontWeight = if (quiet) null else androidx.compose.ui.text.font.FontWeight.Medium),
        modifier = Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(6.dp)).clickable(onClick = onClick).padding(vertical = 10.dp, horizontal = 2.dp))
}

/** «Личное» / «Работа» — a quiet outlined pill with a dot of its own colour; null with [onClick] — an offer to choose. */
@Composable
fun SphereBadge(sphere: String?, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val c = M.c
    val s = M.s
    if (sphere == null && onClick == null) return
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(50)
    Row(modifier.clip(shape).then(if (sphere != null) Modifier.border(1.dp, c.hairline, shape) else Modifier)
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically) {
        if (sphere != null) {
            Box(Modifier.size(6.dp).clip(androidx.compose.foundation.shape.CircleShape).background(if (sphere == "work") c.link else c.ok))
            Text((if (sphere == "work") s.sphereWork else s.spherePersonal), style = Type.small.copy(color = c.graphite, fontSize = 12.sp), modifier = Modifier.padding(start = 5.dp))
        } else Text("+ " + s.sphereAdd, style = Type.small.copy(color = c.graphite, fontSize = 12.5.sp))
    }
}

/** The sphere chip that offers both at once — «Личное» and «Работа» — instead of switching round (owner, 13 окт). */
@Composable
fun SpherePick(sphere: String?, modifier: Modifier = Modifier, onPick: (String) -> Unit) {
    val s = M.s
    var open by remember { androidx.compose.runtime.mutableStateOf(false) }
    Box(modifier) {
        SphereBadge(sphere) { open = true }
        SoftMenu(open, { open = false }) {
            SoftMenuItem(s.spherePersonal, selected = sphere == "personal") { open = false; onPick("personal") }
            SoftMenuItem(s.sphereWork, selected = sphere == "work") { open = false; onPick("work") }
        }
    }
}

/** personal → work → personal: one tap switches. */
fun nextSphere(cur: String?) = if (cur == "personal") "work" else "personal"

/** The menu one step under the title («Люди · Компании»): words as tabs, as everywhere (v8). */
@Composable
fun SubTabs(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) =
    TextTabs(options, selected, onSelect, modifier)

/** A line that starts a list: «1.», «2)», «-», «–», «—», «•», «*». */
val listMarker = Regex("^\\s*(\\d{1,3}[.)]|[a-zа-я][.)]|[-–—•*·])\\s+")

/**
 * Text that knows lists: lines starting with «1.» or «-» / «•» are set as a list — the mark in its own column,
 * the words hanging after it; other lines stay paragraphs.
 */
@Composable
fun ListAwareText(text: String, style: androidx.compose.ui.text.TextStyle, modifier: Modifier = Modifier) {
    val lines = text.lines()
    if (lines.count { listMarker.containsMatchIn(it) } < 2) { Text(linked(text), style = style, modifier = modifier); return }
    Column(modifier) {
        lines.forEach { line ->
            val m = listMarker.find(line)
            if (m == null) { if (line.isNotBlank()) Text(linked(line), style = style) }
            else Row(Modifier.padding(vertical = 1.dp)) {
                val mark = m.groupValues[1].let { if (it.first().isDigit()) it.trimEnd(')', '.') + "." else "•" }
                Text(mark, style = style.copy(color = M.c.graphite, fontFeatureSettings = "tnum"), modifier = Modifier.width(22.dp))
                Text(linked(line.substring(m.range.last + 1)), style = style, modifier = Modifier.weight(1f))
            }
        }
    }
}

/** While typing: Enter after a list line starts the next one («2.» after «1.», «•» after «•»); Enter on an empty item ends the list. */
fun continueList(old: String, new: String): String = continueList(
    androidx.compose.ui.text.input.TextFieldValue(old, androidx.compose.ui.text.TextRange(old.length)),
    androidx.compose.ui.text.input.TextFieldValue(new, androidx.compose.ui.text.TextRange(new.length))).text

/** The next marker after [mark]: «3.» → «4.», «б)» → «в)», «c.» → «d.»; «—», «•» stay. */
private fun nextMark(mark: String): String {
    val end = mark.last()
    val body = mark.dropLast(1)
    body.toIntOrNull()?.let { return (it + 1).toString() + end }
    if (mark.length == 2 && end in ".)") {
        val ru = "абвгдежзиклмнопрстуфхцчшщэюя"
        val i = ru.indexOf(body[0]); if (i >= 0 && i + 1 < ru.length) return "${ru[i + 1]}$end"
        if (body[0] in 'a'..'y') return "${body[0] + 1}$end"
    }
    return mark
}

/**
 * A list goes on by itself (owner, 13 окт): Enter after a line that starts with a marker — «1.», «1)», «а)», «a.», «—», «•» —
 * starts the next line with the next one, wherever the cursor was; Enter on a line that holds only a marker ends the list.
 */
fun continueList(old: androidx.compose.ui.text.input.TextFieldValue, new: androidx.compose.ui.text.input.TextFieldValue): androidx.compose.ui.text.input.TextFieldValue {
    val o = old.text; val n = new.text
    if (n.length != o.length + 1) return new
    val p = new.selection.start - 1
    if (p < 0 || p >= n.length || n[p] != '\n' || n.removeRange(p, p + 1) != o) return new
    val lineStart = n.lastIndexOf('\n', p - 1) + 1
    val line = n.substring(lineStart, p)
    val m = listMarker.find(line) ?: return new
    if (line.substring(m.range.last + 1).isBlank()) {
        // Only the marker on that line: the list ends — the marker and the new line go.
        val t = n.removeRange(lineStart, p + 1)
        return androidx.compose.ui.text.input.TextFieldValue(t, androidx.compose.ui.text.TextRange(lineStart))
    }
    val mark = m.groupValues[1]
    val insert = m.value.substringBefore(mark) + nextMark(mark) + " "
    val t = n.substring(0, p + 1) + insert + n.substring(p + 1)
    return androidx.compose.ui.text.input.TextFieldValue(t, androidx.compose.ui.text.TextRange(p + 1 + insert.length))
}

/** Hours and dates on the margin of lists — the same everywhere: today stays ink in lists of things (not a timeline). */

/**
 * The colour of a matter's hour: ink, digits of one width. A passed hour today is quiet graphite — it is not an alarm,
 * the evening must not turn into a red wall; only a past day is late, in terracotta text.
 */
@Composable
fun hourColor(due: java.time.LocalDate?, time: String?, today: java.time.LocalDate = java.time.LocalDate.now()): androidx.compose.ui.graphics.Color {
    val c = M.c
    return when {
        due != null && due.isBefore(today) -> c.accentText
        due == today && time != null && today == java.time.LocalDate.now() && time < java.time.LocalTime.now().toString().take(5) -> c.graphite
        else -> c.ink
    }
}


// ---------------- the planner's pen (owner, 14 окт): every icon drawn in the same thin line, no Material shapes ----------------

/** Search: a lens and its handle. */
@Composable
fun SearchGlyph(color: Color, size: Dp = 20.dp) = Canvas(Modifier.size(size)) {
    val w = this.size.width; val h = this.size.height
    drawCircle(color, radius = w * 0.27f, center = Offset(w * 0.43f, h * 0.43f), style = stroke(1.4.dp))
    drawLine(color, Offset(w * 0.63f, h * 0.63f), Offset(w * 0.86f, h * 0.86f), strokeWidth = 1.6.dp.toPx(), cap = StrokeCap.Round)
}

/** The overview: four small squares, like the index of a planner's sections. */
@Composable
fun OverviewGlyph(color: Color, size: Dp = 20.dp) = Canvas(Modifier.size(size)) {
    val w = this.size.width; val h = this.size.height
    val s = w * 0.3f; val r = androidx.compose.ui.geometry.CornerRadius(w * 0.05f)
    for ((x, y) in listOf(0.16f to 0.16f, 0.54f to 0.16f, 0.16f to 0.54f, 0.54f to 0.54f))
        drawRoundRect(color, topLeft = Offset(w * x, h * y), size = Size(s, s), cornerRadius = r, style = stroke(1.3.dp))
}

/** «Ещё»: three dots in a row. */
@Composable
fun MoreGlyph(color: Color, size: Dp = 20.dp) = Canvas(Modifier.size(size)) {
    val w = this.size.width; val h = this.size.height
    for (x in listOf(0.24f, 0.5f, 0.76f)) drawCircle(color, radius = w * 0.06f, center = Offset(w * x, h * 0.5f))
}

/** A place: a pin with a hollow head. */
@Composable
fun PinGlyph(color: Color, size: Dp = 20.dp) = Canvas(Modifier.size(size)) {
    val w = this.size.width; val h = this.size.height
    drawPath(Path().apply {
        moveTo(w * 0.5f, h * 0.9f)
        cubicTo(w * 0.28f, h * 0.62f, w * 0.2f, h * 0.5f, w * 0.2f, h * 0.38f)
        cubicTo(w * 0.2f, h * 0.2f, w * 0.34f, h * 0.08f, w * 0.5f, h * 0.08f)
        cubicTo(w * 0.66f, h * 0.08f, w * 0.8f, h * 0.2f, w * 0.8f, h * 0.38f)
        cubicTo(w * 0.8f, h * 0.5f, w * 0.72f, h * 0.62f, w * 0.5f, h * 0.9f)
    }, color, style = stroke(1.4.dp))
    drawCircle(color, radius = w * 0.09f, center = Offset(w * 0.5f, h * 0.37f), style = stroke(1.3.dp))
}

/** Back: a long thin arrow to the left. */
@Composable
fun BackGlyph(color: Color, size: Dp = 22.dp) = Canvas(Modifier.size(size)) {
    val w = this.size.width; val h = this.size.height
    drawPath(Path().apply { moveTo(w * 0.86f, h * 0.5f); lineTo(w * 0.14f, h * 0.5f); moveTo(w * 0.38f, h * 0.26f); lineTo(w * 0.14f, h * 0.5f); lineTo(w * 0.38f, h * 0.74f) }, color, style = stroke(1.5.dp))
}

/** Delete: a bin with its lid. */
@Composable
fun TrashGlyph(color: Color, size: Dp = 20.dp) = Canvas(Modifier.size(size)) {
    val w = this.size.width; val h = this.size.height
    drawLine(color, Offset(w * 0.16f, h * 0.26f), Offset(w * 0.84f, h * 0.26f), strokeWidth = 1.4.dp.toPx(), cap = StrokeCap.Round)
    drawPath(Path().apply { moveTo(w * 0.4f, h * 0.26f); lineTo(w * 0.42f, h * 0.14f); lineTo(w * 0.58f, h * 0.14f); lineTo(w * 0.6f, h * 0.26f) }, color, style = stroke(1.3.dp))
    drawPath(Path().apply { moveTo(w * 0.24f, h * 0.26f); lineTo(w * 0.3f, h * 0.88f); lineTo(w * 0.7f, h * 0.88f); lineTo(w * 0.76f, h * 0.26f) }, color, style = stroke(1.4.dp))
    drawLine(color, Offset(w * 0.44f, h * 0.42f), Offset(w * 0.45f, h * 0.74f), strokeWidth = 1.2.dp.toPx(), cap = StrokeCap.Round)
    drawLine(color, Offset(w * 0.56f, h * 0.42f), Offset(w * 0.55f, h * 0.74f), strokeWidth = 1.2.dp.toPx(), cap = StrokeCap.Round)
}

/** Later: a clock face, its hands at a quarter past. */
@Composable
fun ClockGlyph(color: Color, size: Dp = 20.dp) = Canvas(Modifier.size(size)) {
    val w = this.size.width; val h = this.size.height
    drawCircle(color, radius = w * 0.36f, center = Offset(w * 0.5f, h * 0.5f), style = stroke(1.4.dp))
    drawPath(Path().apply { moveTo(w * 0.5f, h * 0.28f); lineTo(w * 0.5f, h * 0.5f); lineTo(w * 0.66f, h * 0.6f) }, color, style = stroke(1.4.dp))
}

/** «Не буду»: a circle struck through. */
@Composable
fun NotGlyph(color: Color, size: Dp = 20.dp) = Canvas(Modifier.size(size)) {
    val w = this.size.width; val h = this.size.height
    drawCircle(color, radius = w * 0.36f, center = Offset(w * 0.5f, h * 0.5f), style = stroke(1.4.dp))
    drawLine(color, Offset(w * 0.25f, h * 0.75f), Offset(w * 0.75f, h * 0.25f), strokeWidth = 1.4.dp.toPx(), cap = StrokeCap.Round)
}

/** A photo: a frame with a hill and the sun. */
@Composable
fun PhotoGlyph(color: Color, size: Dp = 20.dp) = Canvas(Modifier.size(size)) {
    val w = this.size.width; val h = this.size.height
    drawRoundRect(color, topLeft = Offset(w * 0.12f, h * 0.18f), size = Size(w * 0.76f, h * 0.64f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.08f), style = stroke(1.4.dp))
    drawPath(Path().apply { moveTo(w * 0.14f, h * 0.72f); lineTo(w * 0.4f, h * 0.48f); lineTo(w * 0.58f, h * 0.64f); lineTo(w * 0.68f, h * 0.56f); lineTo(w * 0.86f, h * 0.72f) }, color, style = stroke(1.3.dp))
    drawCircle(color, radius = w * 0.06f, center = Offset(w * 0.66f, h * 0.36f), style = stroke(1.2.dp))
}

/** The camera: a body, its lens and the bump. */
@Composable
fun CameraGlyph(color: Color, size: Dp = 20.dp) = Canvas(Modifier.size(size)) {
    val w = this.size.width; val h = this.size.height
    drawPath(Path().apply {
        moveTo(w * 0.12f, h * 0.32f); lineTo(w * 0.34f, h * 0.32f); lineTo(w * 0.4f, h * 0.2f); lineTo(w * 0.6f, h * 0.2f); lineTo(w * 0.66f, h * 0.32f)
        lineTo(w * 0.88f, h * 0.32f); lineTo(w * 0.88f, h * 0.8f); lineTo(w * 0.12f, h * 0.8f); close()
    }, color, style = stroke(1.4.dp))
    drawCircle(color, radius = w * 0.15f, center = Offset(w * 0.5f, h * 0.55f), style = stroke(1.3.dp))
}

/** A person: a head and shoulders. */
@Composable
fun PersonGlyph(color: Color, size: Dp = 20.dp) = Canvas(Modifier.size(size)) {
    val w = this.size.width; val h = this.size.height
    drawCircle(color, radius = w * 0.16f, center = Offset(w * 0.5f, h * 0.32f), style = stroke(1.4.dp))
    drawPath(Path().apply { moveTo(w * 0.18f, h * 0.86f); cubicTo(w * 0.18f, h * 0.58f, w * 0.82f, h * 0.58f, w * 0.82f, h * 0.86f) }, color, style = stroke(1.4.dp))
}

/** Close: two thin strokes. */
@Composable
fun CloseGlyph(color: Color, size: Dp = 18.dp) = Canvas(Modifier.size(size)) {
    val w = this.size.width; val h = this.size.height
    drawPath(Path().apply { moveTo(w * 0.24f, h * 0.24f); lineTo(w * 0.76f, h * 0.76f); moveTo(w * 0.76f, h * 0.24f); lineTo(w * 0.24f, h * 0.76f) }, color, style = stroke(1.4.dp))
}

/** Work: a briefcase. */
@Composable
fun WorkGlyph(color: Color, size: Dp = 20.dp) = Canvas(Modifier.size(size)) {
    val w = this.size.width; val h = this.size.height
    drawRoundRect(color, topLeft = Offset(w * 0.12f, h * 0.32f), size = Size(w * 0.76f, h * 0.52f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.08f), style = stroke(1.4.dp))
    drawPath(Path().apply { moveTo(w * 0.36f, h * 0.32f); lineTo(w * 0.36f, h * 0.2f); lineTo(w * 0.64f, h * 0.2f); lineTo(w * 0.64f, h * 0.32f) }, color, style = stroke(1.4.dp))
    drawLine(color, Offset(w * 0.12f, h * 0.54f), Offset(w * 0.88f, h * 0.54f), strokeWidth = 1.2.dp.toPx())
}

/** Personal: a small house. */
@Composable
fun HomeGlyph(color: Color, size: Dp = 20.dp) = Canvas(Modifier.size(size)) {
    val w = this.size.width; val h = this.size.height
    drawPath(Path().apply { moveTo(w * 0.14f, h * 0.48f); lineTo(w * 0.5f, h * 0.16f); lineTo(w * 0.86f, h * 0.48f) }, color, style = stroke(1.4.dp))
    drawPath(Path().apply { moveTo(w * 0.24f, h * 0.4f); lineTo(w * 0.24f, h * 0.84f); lineTo(w * 0.76f, h * 0.84f); lineTo(w * 0.76f, h * 0.4f) }, color, style = stroke(1.4.dp))
    drawPath(Path().apply { moveTo(w * 0.43f, h * 0.84f); lineTo(w * 0.43f, h * 0.62f); lineTo(w * 0.57f, h * 0.62f); lineTo(w * 0.57f, h * 0.84f) }, color, style = stroke(1.2.dp))
}

/** Today: a sun — the day as it is now. */
@Composable
fun TodayGlyph(color: Color, size: Dp = 20.dp) = Canvas(Modifier.size(size)) {
    val w = this.size.width; val h = this.size.height
    drawCircle(color, radius = w * 0.17f, center = Offset(w * 0.5f, h * 0.5f), style = stroke(1.4.dp))
    for (i in 0 until 8) {
        val a = Math.toRadians(i * 45.0); val cx = w * 0.5f; val cy = h * 0.5f
        drawLine(color, Offset(cx + (w * 0.29f * kotlin.math.cos(a)).toFloat(), cy + (h * 0.29f * kotlin.math.sin(a)).toFloat()),
            Offset(cx + (w * 0.4f * kotlin.math.cos(a)).toFloat(), cy + (h * 0.4f * kotlin.math.sin(a)).toFloat()), strokeWidth = 1.3.dp.toPx(), cap = StrokeCap.Round)
    }
}
