package ru.anjeib.margin.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import ru.anjeib.margin.data.dowShort
import ru.anjeib.margin.data.dayMonthYear
import ru.anjeib.margin.data.dayMonthShort
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * The top of a card (owner's call, 10 окт): first when, how often, where, with whom — one quiet line, each value a tap
 * that changes it; then a line, and under it the card's stream: the matter's (or idea's) own words, then the comments,
 * each with its day on the margin.
 */

/** One value of the card's top line. */
class MetaItem(val text: AnnotatedString, val style: TextStyle, val onLong: (() -> Unit)? = null, val onClick: () -> Unit)

/** «Пн 12 окт · 18:00 · ↻ каждая пт · ● Личное · Вам от Анны · Ленина, 5» — the same quiet line as under a matter in the lists. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun MetaLine(items: List<MetaItem>, modifier: Modifier = Modifier) {
    if (items.isEmpty()) return
    val c = M.c
    // Each value is a tap of a finger's height; the dots stay between values on one line (DotFlow).
    DotFlow(items.size, modifier, separator = { Text("  ·  ", style = Type.small.copy(color = c.graphite, fontSize = 14.5.sp)) }) { i ->
        val it = items[i]
        Text(it.text, style = it.style, modifier = Modifier.clip(RoundedCornerShape(6.dp))
            .combinedClickable(onClick = it.onClick, onLongClick = it.onLong).padding(vertical = 9.dp, horizontal = 1.dp))
    }
}

/** The styles of the top line: dates and hours in ink (terracotta when late), links petrol, the rest graphite. */
object Meta {
    @Composable fun date(late: Boolean = false) = dateStyle(14.5.sp, late)
    @Composable fun link() = Type.small.copy(color = M.c.link, fontSize = 14.5.sp)
    @Composable fun quiet() = Type.small.copy(color = M.c.graphite, fontSize = 14.5.sp)
    fun plain(t: String) = AnnotatedString(t)
    /** «● Личное» / «● Работа» — the sphere with its coloured dot; a tap switches it. */
    @Composable fun sphere(work: Boolean): AnnotatedString {
        val c = M.c
        return buildAnnotatedString {
            withStyle(SpanStyle(color = if (work) c.link else c.ok, fontSize = 9.sp)) { append("●") }
            append("  ")
            append(if (work) M.s.sphereWork else M.s.spherePersonal)
        }
    }
    /** «Вам от Анны» / «Анне от вас» with the name petrol. */
    @Composable fun promise(parts: Triple<String, String, String>): AnnotatedString {
        val c = M.c
        return buildAnnotatedString {
            append(parts.first)
            withStyle(SpanStyle(color = c.link)) { append(parts.second) }
            append(parts.third)
        }
    }
}

/* ---------------- the card, redone (owner's call, 10 окт): title on top, «когда» as a plaque, three tools, ⋯ for the rest ---------------- */

/** A matter's colour by its sphere: work — petrol, personal (and none) — sage. The bagel wears it everywhere. */
@Composable
fun sphereColor(sphere: String?): androidx.compose.ui.graphics.Color = if (sphere == "work") M.c.link else M.c.ok

/** The ⋯ in the card's top right corner: what is done with the matter as a whole. */
@Composable
fun CardMenu(items: @Composable androidx.compose.foundation.layout.ColumnScope.(close: () -> Unit) -> Unit) {
    val c = M.c
    var open by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    androidx.compose.foundation.layout.Row(Modifier.fillMaxWidthCompat(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End) {
        androidx.compose.foundation.layout.Box {
            androidx.compose.foundation.layout.Box(Modifier.size(44.dp).clip(androidx.compose.foundation.shape.CircleShape)
                .clickable(onClickLabel = M.s.moreActions) { open = true }, contentAlignment = Alignment.Center) {
                androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Outlined.MoreHoriz, M.s.moreActions, tint = c.graphite)
            }
            SoftMenu(open, { open = false }) { items { open = false } }
        }
    }
}

private fun Modifier.fillMaxWidthCompat() = this.fillMaxWidth()

/** The title with its bagel in the sphere's colour: serif, large, edited where it stands. */
@Composable
fun CardTitle(title: String, onTitle: (String) -> Unit, done: Boolean, sphere: String?, onToggle: () -> Unit, mark: (@Composable () -> Unit)? = null) {
    val c = M.c
    androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.Top) {
        androidx.compose.foundation.layout.Box(Modifier.padding(top = 2.dp).size(32.dp), contentAlignment = Alignment.Center) {
            if (mark != null) mark() else CheckCircle(done, size = 22.dp, tint = sphereColor(sphere), onToggle = onToggle)
        }
        androidx.compose.foundation.layout.Spacer(Modifier.size(10.dp))
        androidx.compose.foundation.text.BasicTextField(title, onTitle,
            textStyle = Type.cardTitle.copy(color = if (done) c.graphite else c.ink, fontSize = 23.sp, lineHeight = 29.sp),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(c.link), modifier = Modifier.weight(1f).padding(top = 3.dp))
    }
}

/**
 * «Когда» as a plaque of its own: «Сегодня, 19:30», «Пн, 12 окт, 10:00», «Просрочено с 9 окт», or «Без даты · задать».
 * The day and the hour are two taps; a quiet line under — who owes it, a reminder set apart from the hour.
 */
@Composable
fun WhenPlaque(day: String?, hour: String?, late: Boolean, sub: String?, onDay: () -> Unit, onHour: () -> Unit) {
    val c = M.c
    val s = M.s
    val col = if (late) c.accentText else c.ink
    androidx.compose.foundation.layout.Row(Modifier.fillMaxWidthCompat().clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp)).background(c.card)
        .border(1.dp, c.hairline, androidx.compose.foundation.shape.RoundedCornerShape(14.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        if (hour != null) ClockGlyph(if (late) c.accentText else c.graphite, 20.dp) else CalendarGlyph(if (late) c.accentText else c.graphite, 20.dp)
        androidx.compose.foundation.layout.Spacer(Modifier.size(12.dp))
        androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
            androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                Text(day ?: s.noDateSet, style = Type.body.copy(color = if (day == null) c.graphite else col, fontSize = 17.sp,
                    fontWeight = if (day == null) null else androidx.compose.ui.text.font.FontWeight.Medium, fontFeatureSettings = "tnum"),
                    modifier = Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(6.dp)).clickable(onClick = onDay).padding(vertical = 4.dp))
                if (day != null) {
                    Text(if (hour != null) ", " else "  ·  ", style = Type.body.copy(color = c.graphite, fontSize = 17.sp))
                    Text(hour ?: s.fieldTime.lowercase(), style = Type.body.copy(color = if (hour == null) c.graphite else col, fontSize = 17.sp,
                        fontWeight = if (hour == null) null else androidx.compose.ui.text.font.FontWeight.Medium, fontFeatureSettings = "tnum"),
                        modifier = Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(6.dp)).clickable(onClick = onHour).padding(vertical = 4.dp))
                }
            }
            sub?.let { Text(it, style = Type.small.copy(color = c.graphite, fontSize = 13.5.sp)) }
        }
    }
}

/** One of the card's tools: a round with a drawn mark; filled — petrol. */
@Composable
fun ToolRound(on: Boolean, label: String, onClick: () -> Unit, glyph: @Composable (androidx.compose.ui.graphics.Color) -> Unit) {
    val c = M.c
    androidx.compose.foundation.layout.Box(Modifier.size(44.dp).clip(androidx.compose.foundation.shape.CircleShape)
        .background(if (on) c.linkSoft else androidx.compose.ui.graphics.Color.Transparent)
        .border(1.dp, if (on) androidx.compose.ui.graphics.Color.Transparent else c.hairline, androidx.compose.foundation.shape.CircleShape)
        .semantics { contentDescription = label }.clickable(onClickLabel = label, onClick = onClick), contentAlignment = Alignment.Center) {
        glyph(if (on) c.link else c.graphite)
    }
}

/** Always the weekday and the date — «Сб, 10 окт» (the year only when it is another one); a late day — «Просрочено с 9 окт».
 *  «сегодня / завтра» is a small badge in the row of tools, not a word in place of the date (owner's call). */
fun cardDay(d: java.time.LocalDate, today: java.time.LocalDate = java.time.LocalDate.now(), done: Boolean = false): String = when {
    d.isBefore(today) && !done -> ru.anjeib.margin.i18n.L.s.lateSince(d.dayMonthShort()).replaceFirstChar { it.uppercase() }
    else -> d.dowShort() + ", " + d.dayMonthYear(today)
}

/** «сегодня» (terracotta) / «завтра» (petrol) at the end of the tools' row — the same badge as over a row in the lists. */
@Composable
fun SoonPill(d: java.time.LocalDate?) {
    val c = M.c
    val today = java.time.LocalDate.now()
    val (text, fg, bg) = when (d) {
        today -> Triple(M.s.quickToday, c.accentText, c.accentSoft)
        today.plusDays(1) -> Triple(M.s.quickTomorrow, c.link, c.linkSoft)
        else -> return
    }
    Text(text.lowercase(), style = Type.small.copy(color = fg, fontSize = 13.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium),
        modifier = Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(50)).background(bg).padding(horizontal = 12.dp, vertical = 6.dp))
}

/** At the very bottom: «создано 10 окт, 18:05» — when the matter appeared, never mistaken for its day. */
@Composable
fun CreatedLine(stamp: String?) {
    val at = stamp?.let { runCatching { java.time.LocalDateTime.parse(it.take(16)) }.getOrNull() }
    val d = at?.toLocalDate() ?: stamp?.let { runCatching { java.time.LocalDate.parse(it.take(10)) }.getOrNull() } ?: return
    Text(M.s.createdOn(d.dayMonthYear() + (at?.let { ", " + it.toLocalTime().toString().take(5) } ?: "")),
        style = Type.small.copy(color = M.c.graphite, fontSize = 12.5.sp), modifier = Modifier.padding(top = 18.dp, bottom = 6.dp))
}
