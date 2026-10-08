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
val MarginW = 62.dp
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

/** Small text on the margin: an hour, «завтра», «~21 окт». [late] — terracotta; [petrol] — a date someone owes. */
@Composable
fun MarginText(text: String, late: Boolean = false, petrol: Boolean = false, faint: Boolean = false) {
    val c = M.c
    Text(text, style = Type.small.copy(color = when { late -> c.accent; petrol -> c.link; faint -> c.faint; else -> c.graphite }, fontSize = 12.5.sp,
        fontFeatureSettings = "tnum", textAlign = TextAlign.End, lineHeight = 15.sp), modifier = Modifier.padding(top = 3.dp))
}

/** The day on the margin: a big serif number and the month under it. */
@Composable
fun MarginDay(day: Int, month: String) {
    val c = M.c
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(day.toString(), style = Type.display.copy(color = c.ink, fontSize = 40.sp, lineHeight = 42.sp))
        Text(month, style = Type.small.copy(color = c.graphite, fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.6.sp))
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
fun ThoughtGlyph(color: Color, size: Dp = 18.dp) = Canvas(Modifier.size(size)) {
    val w = this.size.width; val h = this.size.height
    val p = Path().apply {
        moveTo(w * 0.18f, h * 0.2f); lineTo(w * 0.82f, h * 0.2f); quadraticTo(w * 0.9f, h * 0.2f, w * 0.9f, h * 0.3f)
        lineTo(w * 0.9f, h * 0.62f); quadraticTo(w * 0.9f, h * 0.72f, w * 0.82f, h * 0.72f); lineTo(w * 0.42f, h * 0.72f)
        lineTo(w * 0.24f, h * 0.88f); lineTo(w * 0.26f, h * 0.72f); lineTo(w * 0.18f, h * 0.72f)
        quadraticTo(w * 0.1f, h * 0.72f, w * 0.1f, h * 0.62f); lineTo(w * 0.1f, h * 0.3f); quadraticTo(w * 0.1f, h * 0.2f, w * 0.18f, h * 0.2f); close()
    }
    drawPath(p, color, style = stroke())
}

/** «Ждём»: an hourglass; [sand] — how much has run down, [turn] — 0..1 of the flip that turns it into «получено». */
@Composable
fun HourglassGlyph(color: Color, size: Dp = 18.dp, sand: Float = 0.35f, turn: Float = 0f) = Canvas(Modifier.size(size).graphicsLayer { rotationZ = 180f * turn }) {
    val w = this.size.width; val h = this.size.height
    drawLine(color, Offset(w * 0.22f, h * 0.12f), Offset(w * 0.78f, h * 0.12f), strokeWidth = 1.5.dp.toPx(), cap = StrokeCap.Round)
    drawLine(color, Offset(w * 0.22f, h * 0.88f), Offset(w * 0.78f, h * 0.88f), strokeWidth = 1.5.dp.toPx(), cap = StrokeCap.Round)
    val glass = Path().apply {
        moveTo(w * 0.3f, h * 0.12f); lineTo(w * 0.3f, h * 0.24f); lineTo(w * 0.5f, h * 0.5f); lineTo(w * 0.3f, h * 0.76f); lineTo(w * 0.3f, h * 0.88f)
        moveTo(w * 0.7f, h * 0.12f); lineTo(w * 0.7f, h * 0.24f); lineTo(w * 0.5f, h * 0.5f); lineTo(w * 0.7f, h * 0.76f); lineTo(w * 0.7f, h * 0.88f)
    }
    drawPath(glass, color, style = stroke())
    // The sand at the bottom: a small heap that grows.
    val s = sand.coerceIn(0f, 1f)
    if (s > 0f) {
        val top = h * (0.86f - 0.16f * s)
        drawPath(Path().apply { moveTo(w * 0.36f, h * 0.84f); lineTo(w * 0.5f, top); lineTo(w * 0.64f, h * 0.84f); close() }, color.copy(alpha = 0.55f))
    }
}

/** A question: a circle with a question mark. */
@Composable
fun QuestionGlyph(color: Color, size: Dp = 18.dp) = Canvas(Modifier.size(size)) {
    val w = this.size.width; val h = this.size.height
    drawCircle(color, radius = w * 0.42f, style = stroke())
    val q = Path().apply {
        moveTo(w * 0.38f, h * 0.4f); cubicTo(w * 0.38f, h * 0.27f, w * 0.62f, h * 0.27f, w * 0.62f, h * 0.4f)
        cubicTo(w * 0.62f, h * 0.49f, w * 0.5f, h * 0.5f, w * 0.5f, h * 0.6f)
    }
    drawPath(q, color, style = stroke(1.4.dp))
    drawCircle(color, radius = 1.dp.toPx(), center = Offset(w * 0.5f, h * 0.72f))
}

/** Settled: a light tick. */
@Composable
fun TickGlyph(color: Color, size: Dp = 18.dp) = Canvas(Modifier.size(size)) {
    val w = this.size.width; val h = this.size.height
    drawPath(Path().apply { moveTo(w * 0.2f, h * 0.52f); lineTo(w * 0.42f, h * 0.72f); lineTo(w * 0.8f, h * 0.3f) }, color, style = stroke(1.7.dp))
}

/** A thin progress bar for a topic's questions: «2 из 3». Animates when a question is settled. */
@Composable
fun TopicProgress(done: Int, total: Int, modifier: Modifier = Modifier) {
    val c = M.c
    val target = if (total == 0) 0f else done.toFloat() / total
    val a = remember { Animatable(target) }
    LaunchedEffect(target) { a.animateTo(target, tween(420, easing = FastOutSlowInEasing)) }
    Canvas(modifier.fillMaxWidth().size(width = 0.dp, height = 3.dp)) {
        val r = size.height / 2
        drawRoundRect(c.hairline, cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r))
        drawRoundRect(c.link, size = Size(size.width * a.value, size.height), cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r))
    }
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
        Text(text, style = Type.body.copy(color = when { danger -> c.accent; selected -> c.link; else -> c.ink }, fontSize = 15.5.sp), modifier = Modifier.weight(1f, fill = false))
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

/** A thing with its own hour. */
@Composable
fun ClockGlyph(color: Color, size: Dp = 14.dp) = Canvas(Modifier.size(size)) {
    val w = this.size.width; val h = this.size.height
    drawCircle(color, radius = w * 0.36f, center = Offset(w / 2, h / 2), style = stroke(1.4.dp))
    drawPath(Path().apply { moveTo(w * 0.5f, h * 0.3f); lineTo(w * 0.5f, h * 0.5f); lineTo(w * 0.64f, h * 0.6f) }, color, style = stroke(1.4.dp))
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

/** «ДЕЛА», «ТЕМЫ»: a quiet petrol badge over a group. */
@Composable
fun GroupBadge(text: String, modifier: Modifier = Modifier) {
    val c = M.c
    Text(text.uppercase(), style = Type.label.copy(color = c.link, fontSize = 10.5.sp, letterSpacing = 1.1.sp),
        modifier = modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(50)).background(c.linkSoft).padding(horizontal = 10.dp, vertical = 3.dp))
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
                if (subtitle != null) Text(subtitle, style = Type.small.copy(color = c.graphite, fontSize = 15.sp), modifier = Modifier.padding(top = 4.dp))
            }
            trailing?.invoke()
        }
    }
}

/** The rule behind a whole screen, the same on every one. */
@Composable
fun Modifier.screenRule(): Modifier = marginRule(M.c.accent.copy(alpha = 0.85f), over = true)

/** A petrol word with a thin underline, like a link on paper; [quiet] — graphite, no line. */
@Composable
fun PaperLink(text: String, quiet: Boolean = false, onClick: () -> Unit) {
    val c = M.c
    Text(text, style = Type.small.copy(color = if (quiet) c.graphite else c.link, fontSize = 14.sp,
        textDecoration = if (quiet) null else androidx.compose.ui.text.style.TextDecoration.Underline),
        modifier = Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(6.dp)).clickable(onClick = onClick).padding(vertical = 6.dp, horizontal = 2.dp))
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

/** personal → work → personal: one tap switches. */
fun nextSphere(cur: String?) = if (cur == "personal") "work" else "personal"

/** A title with its sphere riding at the end of the last line, so a long name wraps naturally instead of being pushed. */
@Composable
fun TitleWithSphere(title: String, sphere: String?, style: androidx.compose.ui.text.TextStyle, modifier: Modifier = Modifier, maxLines: Int = 2) {
    if (sphere == null) { Text(title, style = style, maxLines = maxLines, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = modifier); return }
    val text = androidx.compose.ui.text.buildAnnotatedString { append(title); append(" "); appendInlineContent("sphere", "·") }
    val inline = mapOf("sphere" to androidx.compose.foundation.text.InlineTextContent(
        androidx.compose.ui.text.Placeholder(76.sp, 20.sp, androidx.compose.ui.text.PlaceholderVerticalAlign.Center)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) { SphereBadge(sphere, Modifier.padding(start = 4.dp)) }
    })
    Text(text, style = style, inlineContent = inline, maxLines = maxLines, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = modifier)
}

/** «Сегодня» / «Завтра» over a row, at the right: the margin keeps only dates, this catches the eye. Nothing for other days. */
@Composable
fun SoonBadge(day: java.time.LocalDate?, modifier: Modifier = Modifier) {
    val c = M.c
    val today = java.time.LocalDate.now()
    val (text, fg, bg) = when (day) {
        today -> Triple(M.s.quickToday, c.accent, c.accentSoft)
        today.plusDays(1) -> Triple(M.s.quickTomorrow, c.link, c.linkSoft)
        else -> return
    }
    Row(modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End) {
        Text(text.uppercase(), style = Type.label.copy(color = fg, fontSize = 9.5.sp, letterSpacing = 1.sp),
            modifier = Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(50)).background(bg).padding(horizontal = 8.dp, vertical = 2.dp))
    }
}

/** Whether [SoonBadge] shows for [day] — rows push their margin and node down by [SoonLift] then. */
fun isSoon(day: java.time.LocalDate?): Boolean = day != null && day in setOf(java.time.LocalDate.now(), java.time.LocalDate.now().plusDays(1))
val SoonLift = 19.dp
