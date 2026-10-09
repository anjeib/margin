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
