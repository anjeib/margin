package ru.anjeib.margin.paper

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ru.anjeib.margin.R

/*
 * The two faces (Playfair for Margin, Inter for you) and the few drawn signs of the field and the top line — one stroke
 * weight, round ends, the same hand as the marks on the rule.
 */

@OptIn(ExperimentalTextApi::class)
private fun playfair(w: Int, italic: Boolean = false) = Font(
    if (italic) R.font.playfair_italic else R.font.playfair,
    FontWeight(w),
    if (italic) FontStyle.Italic else FontStyle.Normal,
    variationSettings = FontVariation.Settings(FontVariation.weight(w)),
)

@OptIn(ExperimentalTextApi::class)
private fun inter(w: Int) = Font(R.font.inter, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))

val Serif = FontFamily(playfair(400), playfair(500), playfair(600), playfair(400, true), playfair(500, true))
val Sans = FontFamily(inter(400), inter(500), inter(600))

private fun DrawScope.stroke(w: Dp = 1.5.dp) = Stroke(width = w.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)

/** The index: three lines of a contents page, each shorter. */
@Composable
fun IndexGlyph(color: Color, size: Dp = 18.dp) = Canvas(Modifier.size(size)) {
    val w = this.size.width; val h = this.size.height; val sw = 1.5.dp.toPx()
    listOf(0.28f to 1f, 0.5f to 0.78f, 0.72f to 0.55f).forEach { (y, len) ->
        drawLine(color, Offset(w * 0.1f, h * y), Offset(w * (0.1f + 0.8f * len), h * y), strokeWidth = sw, cap = StrokeCap.Round)
    }
}

/** «+» in the field: two thin strokes. */
@Composable
fun PlusGlyph(color: Color, size: Dp = 18.dp) = Canvas(Modifier.size(size)) {
    val w = this.size.width; val h = this.size.height
    drawPath(Path().apply { moveTo(w * 0.5f, h * 0.2f); lineTo(w * 0.5f, h * 0.8f); moveTo(w * 0.2f, h * 0.5f); lineTo(w * 0.8f, h * 0.5f) }, color, style = stroke(1.5.dp))
}

/** Dictation: a slim capsule on its stand. */
@Composable
fun MicGlyph(color: Color, size: Dp = 20.dp) = Canvas(Modifier.size(size)) {
    val w = this.size.width; val h = this.size.height
    drawRoundRect(color, topLeft = Offset(w * 0.37f, h * 0.1f), size = Size(w * 0.26f, h * 0.5f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.13f), style = stroke(1.5.dp))
    drawArc(color, startAngle = 0f, sweepAngle = 180f, useCenter = false, topLeft = Offset(w * 0.24f, h * 0.3f), size = Size(w * 0.52f, h * 0.46f), style = stroke(1.5.dp))
    drawPath(Path().apply { moveTo(w * 0.5f, h * 0.76f); lineTo(w * 0.5f, h * 0.9f); moveTo(w * 0.38f, h * 0.9f); lineTo(w * 0.62f, h * 0.9f) }, color, style = stroke(1.5.dp))
}

/** «Записать»: a thin arrow up, inside the small terracotta circle. */
@Composable
fun SendGlyph(color: Color, size: Dp = 16.dp) = Canvas(Modifier.size(size)) {
    val w = this.size.width; val h = this.size.height
    drawPath(Path().apply { moveTo(w * 0.5f, h * 0.86f); lineTo(w * 0.5f, h * 0.16f); moveTo(w * 0.22f, h * 0.43f); lineTo(w * 0.5f, h * 0.15f); lineTo(w * 0.78f, h * 0.43f) }, color, style = stroke(1.8.dp))
}

// ---- the line sheet's row of signs: what a line can have, drawn like the marks ----

/** When: a clock face. */
@Composable
fun ClockGlyph(color: Color, size: Dp = 20.dp) = Canvas(Modifier.size(size)) {
    val w = this.size.width; val h = this.size.height
    drawCircle(color, radius = w * 0.38f, center = Offset(w / 2, h / 2), style = stroke(1.5.dp))
    drawPath(Path().apply { moveTo(w * 0.5f, h * 0.28f); lineTo(w * 0.5f, h * 0.5f); lineTo(w * 0.66f, h * 0.6f) }, color, style = stroke(1.5.dp))
}

/** Repeat: the margin's ↻ — an arc that comes back with its arrow. */
@Composable
fun RepeatGlyph(color: Color, size: Dp = 20.dp) = Canvas(Modifier.size(size)) {
    val w = this.size.width; val h = this.size.height
    drawArc(color, startAngle = -60f, sweepAngle = 300f, useCenter = false, topLeft = Offset(w * 0.16f, h * 0.16f), size = Size(w * 0.68f, h * 0.68f), style = stroke(1.5.dp))
    drawPath(Path().apply { moveTo(w * 0.62f, h * 0.1f); lineTo(w * 0.69f, h * 0.24f); lineTo(w * 0.55f, h * 0.3f) }, color, style = stroke(1.5.dp))
}

/** Who: a head and shoulders. */
@Composable
fun PersonGlyph(color: Color, size: Dp = 20.dp) = Canvas(Modifier.size(size)) {
    val w = this.size.width; val h = this.size.height
    drawCircle(color, radius = w * 0.16f, center = Offset(w / 2, h * 0.34f), style = stroke(1.5.dp))
    drawArc(color, startAngle = 200f, sweepAngle = 140f, useCenter = false, topLeft = Offset(w * 0.2f, h * 0.58f), size = Size(w * 0.6f, h * 0.56f), style = stroke(1.5.dp))
}

/** Where: a pin. */
@Composable
fun PinGlyph(color: Color, size: Dp = 20.dp) = Canvas(Modifier.size(size)) {
    val w = this.size.width; val h = this.size.height
    drawPath(Path().apply {
        moveTo(w * 0.5f, h * 0.9f)
        cubicTo(w * 0.3f, h * 0.66f, w * 0.22f, h * 0.52f, w * 0.22f, h * 0.4f)
        cubicTo(w * 0.22f, h * 0.22f, w * 0.35f, h * 0.1f, w * 0.5f, h * 0.1f)
        cubicTo(w * 0.65f, h * 0.1f, w * 0.78f, h * 0.22f, w * 0.78f, h * 0.4f)
        cubicTo(w * 0.78f, h * 0.52f, w * 0.7f, h * 0.66f, w * 0.5f, h * 0.9f)
        close()
    }, color, style = stroke(1.5.dp))
    drawCircle(color, radius = w * 0.08f, center = Offset(w / 2, h * 0.4f), style = stroke(1.4.dp))
}

/** Thread: the margin's ≡ — three equal lines. */
@Composable
fun ThreadGlyph(color: Color, size: Dp = 20.dp) = Canvas(Modifier.size(size)) {
    val w = this.size.width; val h = this.size.height; val sw = 1.5.dp.toPx()
    listOf(0.3f, 0.5f, 0.7f).forEach { y -> drawLine(color, Offset(w * 0.2f, h * y), Offset(w * 0.8f, h * y), strokeWidth = sw, cap = StrokeCap.Round) }
}

/** A file: a paperclip, a little aslant. */
@Composable
fun ClipGlyph(color: Color, size: Dp = 20.dp) = Canvas(Modifier.size(size)) {
    val w = this.size.width; val h = this.size.height
    rotate(40f) {
        drawPath(Path().apply {
            moveTo(w * 0.58f, h * 0.32f)
            lineTo(w * 0.58f, h * 0.64f)
            arcTo(androidx.compose.ui.geometry.Rect(w * 0.42f, h * 0.56f, w * 0.58f, h * 0.72f), 0f, 180f, false)
            lineTo(w * 0.42f, h * 0.24f)
            arcTo(androidx.compose.ui.geometry.Rect(w * 0.42f, h * 0.1f, w * 0.7f, h * 0.38f), 180f, 180f, false)
            lineTo(w * 0.7f, h * 0.7f)
            arcTo(androidx.compose.ui.geometry.Rect(w * 0.3f, h * 0.5f, w * 0.7f, h * 0.9f), 0f, 180f, false)
            lineTo(w * 0.3f, h * 0.36f)
        }, color, style = stroke(1.5.dp))
    }
}

/** Delete: a bin. */
@Composable
fun BinGlyph(color: Color, size: Dp = 20.dp) = Canvas(Modifier.size(size)) {
    val w = this.size.width; val h = this.size.height
    drawLine(color, Offset(w * 0.2f, h * 0.27f), Offset(w * 0.8f, h * 0.27f), strokeWidth = 1.5.dp.toPx(), cap = StrokeCap.Round)
    drawLine(color, Offset(w * 0.4f, h * 0.17f), Offset(w * 0.6f, h * 0.17f), strokeWidth = 1.5.dp.toPx(), cap = StrokeCap.Round)
    drawPath(Path().apply { moveTo(w * 0.27f, h * 0.27f); lineTo(w * 0.32f, h * 0.84f); lineTo(w * 0.68f, h * 0.84f); lineTo(w * 0.73f, h * 0.27f) }, color, style = stroke(1.5.dp))
}
