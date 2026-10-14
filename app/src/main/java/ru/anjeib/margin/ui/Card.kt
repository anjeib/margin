package ru.anjeib.margin.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.anjeib.margin.data.dayMonthShort
import ru.anjeib.margin.data.dowFull
import java.time.LocalDate

// ---------------- the cards as pages of the planner (owner, 14 окт; design/v9/v9-card-deal|note|topic.png) ----------------

/**
 * A card — a matter, a note, a topic — opened as a page of the same book: it lies over the diary's page, the index stays on
 * the right; back on the margin at the top, [trailing] (a topic's ⋯) on the right. No sheet rising from below.
 */
@Composable
fun CardPage(onClose: () -> Unit, trailing: (@Composable () -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val c = M.c
    val s = M.s
    BackHandler(onBack = onClose)
    Column(Modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding().imePadding()) {
        // Under the printed frame's top line («MARGIN»), as a page of the diary starts.
        Row(Modifier.fillMaxWidth().screenRule().padding(top = 18.dp).height(40.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(MarginW), contentAlignment = Alignment.Center) {
                Box(Modifier.size(40.dp).clip(CircleShape).clickable(onClickLabel = s.backWord, onClick = onClose), contentAlignment = Alignment.Center) {
                    BackGlyph(c.ink.copy(alpha = 0.8f), 21.dp)
                }
            }
            Spacer(Modifier.weight(1f))
            trailing?.invoke()
        }
        content()
    }
}

/**
 * The card's «когда» on its margin, the same as a day of the diary (owner, 14 окт): the day's number large, the month in
 * spaced capitals under it, then a quiet line — the hour, or the weekday. Late — a terracotta highlighter over the day.
 * [prefix] stands over it in italic («с», «9 –»). Each part is a tap when it can be changed.
 */
@Composable
fun CardMargin(day: LocalDate?, hour: String? = null, late: Boolean = false, prefix: String? = null, under: String? = null,
               onDay: (() -> Unit)? = null, onHour: (() -> Unit)? = null) {
    val c = M.c
    val s = M.s
    val ink = pencilInk
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 2.dp)) {
        if (prefix != null) Text(prefix, style = Type.italic.copy(color = c.ink.copy(alpha = 0.75f), fontSize = 14.sp, lineHeight = 16.sp))
        if (day == null) MarginNote(s.noDateShort, modifier = Modifier.clip(RoundedCornerShape(6.dp)).then(if (onDay != null) Modifier.clickable(onClickLabel = s.dateWord, onClick = onDay) else Modifier).padding(vertical = 4.dp))
        else Box(Modifier.clip(RoundedCornerShape(6.dp)).then(if (onDay != null) Modifier.clickable(onClickLabel = s.dateWord, onClick = onDay) else Modifier)
            // Late: a highlighter stroke across the number (owner, 14 окт: along the line, as everywhere — not up the whole date).
            .drawBehind { if (late) markerBand(2.dp.toPx(), size.width - 2.dp.toPx(), 14.dp.toPx(), 40.dp.toPx(), ink, day.hashCode()) }
            .padding(horizontal = 2.dp)) {
            MarginDay(day.dayOfMonth, day.dayMonthShort().substringAfter(' '))
        }
        val quiet = hour ?: under
        if (quiet != null || onHour != null) MarginNote(quiet ?: s.addTime,
            modifier = Modifier.padding(top = 2.dp).clip(RoundedCornerShape(6.dp)).then(if (onHour != null) Modifier.clickable(onClickLabel = s.fieldTime, onClick = onHour) else Modifier).padding(vertical = 3.dp))
    }
}

/** A window of days on the margin: «9 –» over the last day. */
fun windowPrefix(from: LocalDate?, due: LocalDate?): String? =
    if (from != null && due != null && from.isBefore(due)) "${from.dayOfMonth}${if (from.month != due.month) " " + from.dayMonthShort().substringAfter(' ') else ""} –" else null

/** The weekday under a day on the margin: «суббота». */
fun LocalDate.weekdayWord(): String = dowFull().lowercase()

/**
 * «Личное» / «Работа» as their two drawn marks, a house and a briefcase, bare on the page at the right under the title
 * (owner, 14 окт): the chosen one in petrol, the other faint — nothing else marks the choice. A tap picks; with [clearable]
 * a tap on the chosen one lets it go (a filter). A long press says the word.
 */
@Composable
fun SphereIcons(sphere: String?, onSphere: (String?) -> Unit, modifier: Modifier = Modifier, clearable: Boolean = false) {
    val c = M.c
    val s = M.s
    Row(modifier, horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(2.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
        listOf("personal" to s.spherePersonal, "work" to s.sphereWork).forEach { (k, word) ->
            val on = sphere == k
            Box(Modifier.size(38.dp).clip(CircleShape).androidxSemantics(on).semantics { contentDescription = word }
                .clickable(onClickLabel = word) { onSphere(if (on && clearable) null else k) }, contentAlignment = Alignment.Center) {
                val ink = if (on) c.link else c.graphite.copy(alpha = 0.45f)
                if (k == "work") WorkGlyph(ink, 20.dp) else HomeGlyph(ink, 20.dp)
            }
        }
    }
}

private fun Modifier.androidxSemantics(on: Boolean): Modifier = this.then(Modifier.semantics { selected = on })

/** A matter's mark as a drawn sign: the empty circle. */
@Composable
fun DealGlyph(color: androidx.compose.ui.graphics.Color, size: androidx.compose.ui.unit.Dp = 20.dp) = androidx.compose.foundation.Canvas(Modifier.size(size)) {
    drawCircle(color, this.size.minDimension * 0.36f, style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx()))
}


/** The archive: a box with its lid. */
@Composable
fun ArchiveGlyph(color: androidx.compose.ui.graphics.Color, size: androidx.compose.ui.unit.Dp = 20.dp) = androidx.compose.foundation.Canvas(Modifier.size(size)) {
    val w = this.size.width; val h = this.size.height; val st = androidx.compose.ui.graphics.drawscope.Stroke(1.4.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)
    drawRect(color, androidx.compose.ui.geometry.Offset(w * 0.14f, h * 0.2f), androidx.compose.ui.geometry.Size(w * 0.72f, h * 0.18f), style = st)
    drawRect(color, androidx.compose.ui.geometry.Offset(w * 0.2f, h * 0.38f), androidx.compose.ui.geometry.Size(w * 0.6f, h * 0.44f), style = st)
    drawLine(color, androidx.compose.ui.geometry.Offset(w * 0.4f, h * 0.53f), androidx.compose.ui.geometry.Offset(w * 0.6f, h * 0.53f), 1.4.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
}

/** Sending on: an arrow leaving a tray. */
@Composable
fun ShareGlyph(color: androidx.compose.ui.graphics.Color, size: androidx.compose.ui.unit.Dp = 20.dp) = androidx.compose.foundation.Canvas(Modifier.size(size)) {
    val w = this.size.width; val h = this.size.height; val sw = 1.4.dp.toPx(); val cap = androidx.compose.ui.graphics.StrokeCap.Round
    drawLine(color, androidx.compose.ui.geometry.Offset(w * 0.5f, h * 0.62f), androidx.compose.ui.geometry.Offset(w * 0.5f, h * 0.14f), sw, cap = cap)
    drawLine(color, androidx.compose.ui.geometry.Offset(w * 0.33f, h * 0.3f), androidx.compose.ui.geometry.Offset(w * 0.5f, h * 0.14f), sw, cap = cap)
    drawLine(color, androidx.compose.ui.geometry.Offset(w * 0.67f, h * 0.3f), androidx.compose.ui.geometry.Offset(w * 0.5f, h * 0.14f), sw, cap = cap)
    val p = androidx.compose.ui.graphics.Path().apply { moveTo(w * 0.22f, h * 0.48f); lineTo(w * 0.22f, h * 0.84f); lineTo(w * 0.78f, h * 0.84f); lineTo(w * 0.78f, h * 0.48f) }
    drawPath(p, color, style = androidx.compose.ui.graphics.drawscope.Stroke(sw, cap = cap, join = androidx.compose.ui.graphics.StrokeJoin.Round))
}

/** Writing something anew: a pencil. */
@Composable
fun EditGlyph(color: androidx.compose.ui.graphics.Color, size: androidx.compose.ui.unit.Dp = 20.dp) = androidx.compose.foundation.Canvas(Modifier.size(size)) {
    val w = this.size.width; val h = this.size.height; val sw = 1.4.dp.toPx()
    val p = androidx.compose.ui.graphics.Path().apply { moveTo(w * 0.2f, h * 0.8f); lineTo(w * 0.24f, h * 0.62f); lineTo(w * 0.66f, h * 0.2f); lineTo(w * 0.8f, h * 0.34f); lineTo(w * 0.38f, h * 0.76f); close() }
    drawPath(p, color, style = androidx.compose.ui.graphics.drawscope.Stroke(sw, join = androidx.compose.ui.graphics.StrokeJoin.Round))
    drawLine(color, androidx.compose.ui.geometry.Offset(w * 0.58f, h * 0.28f), androidx.compose.ui.geometry.Offset(w * 0.72f, h * 0.42f), sw)
}

/**
 * A line with something at its right end ([end] — «личное работа»): side by side when both fit, the end under the line
 * (still at the right) when they don't; the line's words are never squeezed.
 */
@Composable
fun LineWithEnd(modifier: Modifier = Modifier, gap: androidx.compose.ui.unit.Dp = 6.dp, start: @Composable () -> Unit, end: @Composable () -> Unit) {
    androidx.compose.ui.layout.Layout(content = { Box { start() }; Box { end() } }, modifier = modifier) { m, cons ->
        val loose = cons.copy(minWidth = 0, minHeight = 0)
        val a = m[0].measure(loose)
        val b = m[1].measure(loose)
        val w = cons.maxWidth
        val g = gap.roundToPx()
        if (a.width + g + b.width <= w) {
            val h = maxOf(a.height, b.height)
            layout(w, h) { a.placeRelative(0, (h - a.height) / 2); b.placeRelative(w - b.width, (h - b.height) / 2) }
        } else layout(w, a.height + b.height) { a.placeRelative(0, 0); b.placeRelative(w - b.width, a.height) }
    }
}
