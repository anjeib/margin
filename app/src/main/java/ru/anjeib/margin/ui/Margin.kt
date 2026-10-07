package ru.anjeib.margin.ui

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
