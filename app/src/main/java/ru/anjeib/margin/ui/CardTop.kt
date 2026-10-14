package ru.anjeib.margin.ui

import androidx.compose.foundation.layout.widthIn
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
/** [way] — a link to another card: one line, cut short with «…» when long, and «›» after it (owner, 14 окт). */
class MetaItem(val text: AnnotatedString, val style: TextStyle, val onLong: (() -> Unit)? = null, val way: Boolean = false, val onClick: () -> Unit)

/** «Пн 12 окт · 18:00 · ↻ каждая пт · ● Личное · Вам от Анны · Ленина, 5» — the same quiet line as under a matter in the lists. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun MetaLine(items: List<MetaItem>, modifier: Modifier = Modifier) {
    if (items.isEmpty()) return
    val c = M.c
    // Each value is a tap of a finger's height; the dots stay between values on one line (DotFlow).
    DotFlow(items.size, modifier, separator = { Text("  ·  ", style = if (planner) Type.italic.copy(color = c.graphite, fontSize = 17.sp) else Type.small.copy(color = c.graphite, fontSize = 14.5.sp)) }) { i ->
        val it = items[i]
        if (it.way) androidx.compose.foundation.layout.Row(Modifier.clip(RoundedCornerShape(6.dp))
            .combinedClickable(onClick = it.onClick, onLongClick = it.onLong).padding(vertical = 9.dp, horizontal = 1.dp)) {
            Text(it.text, style = it.style, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            Text(" ›", style = it.style)
        }
        else Text(it.text, style = it.style, modifier = Modifier.clip(RoundedCornerShape(6.dp))
            .combinedClickable(onClick = it.onClick, onLongClick = it.onLong).padding(vertical = 9.dp, horizontal = 1.dp))
    }
}

/** The styles of the top line: dates and hours in ink (terracotta when late), links petrol, the rest graphite. */
object Meta {
    @Composable fun date(late: Boolean = false) = dateStyle(14.5.sp, late)
    // The planner (owner, 14 окт): the card's line under the title is Margin's own voice — the book's italic.
    @Composable fun link() = if (planner) Type.italic.copy(color = M.c.link, fontSize = 17.sp) else Type.small.copy(color = M.c.link, fontSize = 14.5.sp)
    @Composable fun quiet() = if (planner) Type.italic.copy(color = M.c.graphite, fontSize = 17.sp) else Type.small.copy(color = M.c.graphite, fontSize = 14.5.sp)
    fun plain(t: String) = AnnotatedString(t)
    @Composable fun faint() = if (planner) Type.italic.copy(color = M.c.faint, fontSize = 17.sp) else Type.small.copy(color = M.c.faint, fontSize = 14.5.sp)
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

/* ---------------- the card in v8 «Поля и панели»: when on the margin, the mark and the title, one petrol line, the tools ---------------- */

/** The ⋯ in the card's top right corner: what is done with the matter as a whole; [context] — where it belongs, at the left. */
@Composable
fun CardMenu(context: (@Composable () -> Unit)? = null, items: @Composable androidx.compose.foundation.layout.ColumnScope.(close: () -> Unit) -> Unit) {
    val c = M.c
    var open by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    androidx.compose.foundation.layout.Row(Modifier.fillMaxWidthCompat(), verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.foundation.layout.Box(Modifier.weight(1f)) { context?.invoke() }
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

/** «≡ Кофейня «Зерно» ›» — the topic a matter belongs to, a way to it. */
@Composable
fun TopicLink(title: String, onClick: () -> Unit) {
    val c = M.c
    androidx.compose.foundation.layout.Row(Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(vertical = 10.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically) {
        TopicGlyph(c.link, 15.dp)
        androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
        Text("$title  ›", style = Type.small.copy(color = c.link, fontSize = 15.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium), maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
    }
}

/** Always the weekday and the date — «Сб, 10 окт» (the year only when it is another one); a late day — «Просрочено с 9 окт». */
fun cardDay(d: java.time.LocalDate, today: java.time.LocalDate = java.time.LocalDate.now(), done: Boolean = false): String = when {
    d.isBefore(today) && !done -> ru.anjeib.margin.i18n.L.s.lateSince(d.dayMonthShort()).replaceFirstChar { it.uppercase() }
    else -> d.dowShort() + ", " + d.dayMonthYear(today)
}

/**
 * The card's «когда» on its margin, as on «Сегодня»: the day in words («сегодня», «11 окт», «с 8 окт» when late) and the
 * hour under it; each line a tap — the day, the hour. Without a day — a quiet «без даты».
 */
@Composable
fun CardWhen(due: java.time.LocalDate?, hour: String?, late: Boolean, onDay: () -> Unit, onHour: (() -> Unit)?, from: java.time.LocalDate? = null) {
    val c = M.c
    val s = M.s
    androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(top = 4.dp)) {
        // A window is said as one: «9–11 окт», not its last day (owner, 13 окт).
        val day = due?.let { if (late) s.sinceWord + " " + it.dayMonthShort() else if (from != null && from.isBefore(it)) spanWord(from, it) else dayWord(it) } ?: s.noDateShort
        MarginNote(day, late = late, modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClickLabel = s.dateWord, onClick = onDay).padding(vertical = 4.dp))
        if (onHour != null || hour != null) MarginNote(hour ?: s.addTime, late = late && hour != null,
            modifier = Modifier.clip(RoundedCornerShape(6.dp)).then(if (onHour != null) Modifier.clickable(onClickLabel = s.fieldTime, onClick = onHour) else Modifier).padding(vertical = 4.dp))
    }
}

/**
 * The head of a card (owner, 14 окт; design/v9/v9-card-*.png): «когда» on the margin, the mark (or none) and the title in the
 * book's serif, edited where it stands; under it one italic line in Margin's voice — what it is ([kind]: «дело», «заметка»),
 * its sphere («работа» / «личное», a tap switches it), then its values, each a tap.
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
fun CardHead(title: String, onTitle: (String) -> Unit, done: Boolean, onToggle: () -> Unit,
             margin: (@Composable () -> Unit)?, meta: List<MetaItem>, mark: (@Composable () -> Unit)? = null, hint: String? = null,
             sphere: String? = null, onSphere: ((String) -> Unit)? = null,
             /** What the card is, first in the line under the title; null — no mark column either (a note has none). */
             kind: String? = null, noMark: Boolean = false,
             /** Where it belongs, over the title in one line (owner, 14 окт): «# Тема   ○ Дело», each cut short to fit. */
             trail: List<MetaItem> = emptyList()) {
    val c = M.c
    val s = M.s
    val titleStyle = Type.display.copy(color = if (done) c.graphite else c.ink, fontSize = 27.sp, lineHeight = 32.sp)
    Margined(margin = margin) {
        androidx.compose.foundation.layout.Column {
            if (trail.isNotEmpty()) androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth().padding(start = if (noMark) 0.dp else MarkCol), verticalAlignment = Alignment.CenterVertically) {
                val st = Type.italic.copy(color = c.link, fontSize = 15.5.sp)
                trail.forEachIndexed { i, it ->
                    if (i > 0) androidx.compose.foundation.layout.Spacer(Modifier.size(10.dp))
                    // Owner, 14 окт: no «›» after it — petrol already says it is a way.
                    Text(it.text, style = st, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false).clip(RoundedCornerShape(6.dp)).clickable(onClick = it.onClick).padding(vertical = 6.dp))
                }
            }
            androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.Top) {
                if (!noMark) {
                    androidx.compose.foundation.layout.Box(Modifier.size(MarkCol - PanelBleed, 34.dp), contentAlignment = Alignment.CenterStart) {
                        if (mark != null) mark() else CheckCircle(done, size = 22.dp, onToggle = onToggle)
                    }
                    androidx.compose.foundation.layout.Spacer(Modifier.size(6.dp))
                }
                androidx.compose.foundation.layout.Box(Modifier.weight(1f)) {
                    if (title.isEmpty() && hint != null) Text(hint, style = titleStyle.copy(color = c.graphite))
                    androidx.compose.foundation.text.BasicTextField(title, onTitle, textStyle = titleStyle,
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(c.link), modifier = Modifier.fillMaxWidth())
                }
            }
            val line = buildList {
                kind?.let { add(MetaItem(Meta.plain(it), Meta.quiet()) {}) }
                addAll(meta)
            }
            // The sphere as its two words at the end of the line (owner, 14 окт), the chosen one in petrol.
            // The line flows on: its words, then «личное работа» where there is room, else on the next line — never squeezed.
            // The house and the briefcase at the right of the line's first row (owner, 14 окт); a long line wraps beside them.
            androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth().padding(start = if (noMark) 0.dp else MarkCol), verticalAlignment = Alignment.Top) {
                androidx.compose.foundation.layout.Box(Modifier.weight(1f)) { if (line.isNotEmpty()) MetaLine(line) }
                if (onSphere != null) SphereIcons(sphere, { k -> k?.let(onSphere) }, Modifier.padding(start = 6.dp))
            }
        }
    }
}

/** At the very bottom, in Margin's voice: «создано 10 окт, 18:05» — when the matter appeared, never mistaken for its day. */
@Composable
fun CreatedLine(stamp: String?, onClick: (() -> Unit)? = null) {
    val at = stamp?.let { runCatching { java.time.LocalDateTime.parse(it.take(16)) }.getOrNull() }
    val d = at?.toLocalDate() ?: stamp?.let { runCatching { java.time.LocalDate.parse(it.take(10)) }.getOrNull() } ?: return
    // Made from words written in the field: a tap shows them as they were written (owner, 14 окт: no «↳ из сообщения» line).
    Voice(M.s.createdOn(d.dayMonthYear() + (at?.let { ", " + it.toLocalTime().toString().take(5) } ?: "")) + if (onClick != null) "  ›" else "",
        Modifier.padding(top = 22.dp, bottom = 6.dp), onClick = onClick)
}
