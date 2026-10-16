package ru.anjeib.margin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.anjeib.margin.i18n.L
import ru.anjeib.margin.i18n.Lang
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle

/**
 * Margin's own month grid (persona test, 15 окт: the Material picker — «October 2026», a week from Sunday, a blue disc — was the
 * one foreign screen). The same drawing as the month on «Сегодня»: the month's name in the page's serif, пн…вс, weekends grey,
 * today ringed, the chosen day filled; a range is a soft band between its two ends. A tap on a day is the choice.
 */
@Composable
fun DayGrid(month: YearMonth, onMonth: (YearMonth) -> Unit, selected: LocalDate?, rangeStart: LocalDate? = null, rangeEnd: LocalDate? = null,
            /**
             * Owner, 16 окт: a tap on a number is that day; a press held and drawn across the days — a window (or held on one day,
             * then a tap on another). Null — days only.
             */
            onRange: ((LocalDate, LocalDate) -> Unit)? = null, onDay: (LocalDate) -> Unit) {
    val c = M.c
    val s = M.s
    val today = LocalDate.now()
    val loc = if (L.lang == Lang.Ru) java.util.Locale.forLanguageTag("ru") else java.util.Locale.ENGLISH
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(month.month.getDisplayName(TextStyle.FULL_STANDALONE, loc).replaceFirstChar { it.uppercase() } + if (month.year != today.year) " ${month.year}" else "",
                style = Type.title.copy(color = c.ink, fontSize = 18.sp), modifier = Modifier.weight(1f))
            Box(Modifier.size(34.dp).clip(CircleShape).clickable { onMonth(month.minusMonths(1)) }, contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, s.prevMonth, tint = c.graphite)
            }
            Box(Modifier.size(34.dp).clip(CircleShape).clickable { onMonth(month.plusMonths(1)) }, contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, s.nextMonth, tint = c.graphite)
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            (1..7).forEach { d ->
                Text(java.time.DayOfWeek.of(d).getDisplayName(TextStyle.SHORT, loc).lowercase(), style = Type.small.copy(color = c.graphite, fontSize = 12.sp, lineHeight = 14.sp),
                    modifier = Modifier.weight(1f).padding(bottom = 2.dp), textAlign = TextAlign.Center)
            }
        }
        val first = month.atDay(1)
        val lead = first.dayOfWeek.value - 1
        val cells = lead + month.lengthOfMonth()
        // The window being drawn: its first day (held) and where the finger is now.
        var held by remember(month) { mutableStateOf<LocalDate?>(null) }
        var over by remember(month) { mutableStateOf<LocalDate?>(null) }
        val rowPx = with(androidx.compose.ui.platform.LocalDensity.current) { 38.dp.toPx() }
        fun dayAt(o: androidx.compose.ui.geometry.Offset, width: Int): LocalDate? {
            val col = (o.x / (width / 7f)).toInt().coerceIn(0, 6)
            val n = (o.y / rowPx).toInt() * 7 + col - lead + 1
            return if (n in 1..month.lengthOfMonth()) month.atDay(n) else null
        }
        fun done(a: LocalDate, b: LocalDate) { held = null; over = null; if (a == b) onDay(a) else onRange?.invoke(minOf(a, b), maxOf(a, b)) }
        @Suppress("NAME_SHADOWING") val rangeStart = held?.let { h -> minOf(h, over ?: h) } ?: rangeStart
        @Suppress("NAME_SHADOWING") val rangeEnd = held?.let { h -> maxOf(h, over ?: h) } ?: rangeEnd
        @Suppress("NAME_SHADOWING") val selected = if (held != null) null else selected
        Column(Modifier.fillMaxWidth().then(if (onRange == null) Modifier else Modifier
            .pointerInput(month) {
                detectTapGestures(onTap = { o -> dayAt(o, size.width)?.let { d -> val h = held; if (h != null) done(h, d) else onDay(d) } })
            }
            .pointerInput(month) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { o -> dayAt(o, size.width)?.let { held = it; over = it } },
                    onDrag = { ch, _ -> dayAt(ch.position, size.width)?.let { over = it } },
                    // Held on one day and let go: it waits for a tap on the window's other end.
                    onDragEnd = { val h = held; val o = over; if (h != null && o != null && o != h) done(h, o) },
                    onDragCancel = { held = null; over = null },
                )
            })) {
        (0 until (cells + 6) / 7).forEach { w ->
            Row(Modifier.fillMaxWidth()) {
                (0..6).forEach { i ->
                    val n = w * 7 + i - lead + 1
                    Box(Modifier.weight(1f).height(38.dp).padding(vertical = 1.dp), contentAlignment = Alignment.Center) {
                        if (n in 1..month.lengthOfMonth()) {
                            val d = month.atDay(n)
                            val sel = d == selected || d == rangeStart || d == rangeEnd
                            val inRange = rangeStart != null && rangeEnd != null && !d.isBefore(rangeStart) && !d.isAfter(rangeEnd)
                            val weekend = d.dayOfWeek.value >= 6
                            Box(Modifier.fillMaxSize().background(if (inRange && !sel) c.linkSoft else Color.Transparent)
                                .padding(horizontal = 2.dp).clip(RoundedCornerShape(8.dp)).background(if (sel) c.link else Color.Transparent)
                                .then(if (d == today && !sel) Modifier.border(1.dp, c.link, RoundedCornerShape(8.dp)) else Modifier)
                                .then(if (onRange == null) Modifier.clickable { onDay(d) } else Modifier), contentAlignment = Alignment.Center) {
                                Text("$n", style = Type.body.copy(color = when { sel -> c.bg; d.isBefore(today) -> c.graphite; weekend -> c.graphite; else -> c.ink }, fontSize = 15.sp, lineHeight = 17.sp,
                                    fontWeight = if (sel || d == today) FontWeight.SemiBold else FontWeight.Normal, fontFeatureSettings = "tnum"))
                            }
                        }
                    }
                }
            }
        }
        }
    }
}

/** One day, picked with a tap: [quick] — the pills over the grid («Завтра», «Через неделю»…); nothing to confirm. */
@Composable
fun DayDialog(title: String, initial: LocalDate?, onDismiss: () -> Unit, quick: (@Composable () -> Unit)? = null, dismissLabel: String? = null, onDismissAction: (() -> Unit)? = null, onPick: (LocalDate) -> Unit) {
    val c = M.c
    val s = M.s
    var month by remember { mutableStateOf(YearMonth.from(initial ?: LocalDate.now())) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss, containerColor = c.card,
        title = { Text(title, style = Type.cardTitle.copy(color = c.ink)) },
        text = {
            Column {
                quick?.invoke()
                DayGrid(month, { month = it }, selected = initial, onDay = onPick)
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onDismissAction ?: onDismiss) { Text(dismissLabel ?: s.cancel, color = c.graphite) } },
    )
}
