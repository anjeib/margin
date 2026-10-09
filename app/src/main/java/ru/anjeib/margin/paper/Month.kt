package ru.anjeib.margin.paper

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ru.anjeib.margin.book.Book
import ru.anjeib.margin.book.marks
import java.time.LocalDate
import java.time.YearMonth

/*
 * The month (DESIGN.md §11): opened by a tap on the date on the margin. Under each number — dots: • for open to-dos,
 * ○ for meetings (up to three), a red dot when an old day still holds open lines; a closed day gets a quiet tick.
 * The numbers come from the same function as the page, so they never disagree.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthSheet(book: Book, today: LocalDate, shown: LocalDate, onPick: (LocalDate) -> Unit, onClose: () -> Unit) {
    val c = P.c
    val w = P.w
    var ym by remember { mutableStateOf(YearMonth.from(shown)) }
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.lifted, scrimColor = c.scrim,
        dragHandle = { Box(Modifier.padding(top = 10.dp, bottom = 6.dp).size(36.dp, 4.dp).clip(CircleShape).background(c.hairline)) }) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp).padding(bottom = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(w.monthName(ym), style = Voice.section.copy(color = c.ink), modifier = Modifier.weight(1f).padding(start = 8.dp))
                Text("‹", style = Voice.section.copy(color = c.graphite), modifier = Modifier.clip(CircleShape).clickable { ym = ym.minusMonths(1) }
                    .semantics { contentDescription = "‹" }.padding(horizontal = 16.dp, vertical = 8.dp))
                Text("›", style = Voice.section.copy(color = c.graphite), modifier = Modifier.clip(CircleShape).clickable { ym = ym.plusMonths(1) }
                    .semantics { contentDescription = "›" }.padding(horizontal = 16.dp, vertical = 8.dp))
            }
            Row(Modifier.fillMaxWidth().padding(top = 10.dp)) {
                (1..7).forEach { d ->
                    Text(w.dow(LocalDate.of(2024, 1, d)), style = Voice.small.copy(color = if (d >= 6) c.pencil else c.graphite, textAlign = TextAlign.Center),
                        modifier = Modifier.weight(1f))
                }
            }
            val first = ym.atDay(1)
            val lead = first.dayOfWeek.value - 1
            val days = ym.lengthOfMonth()
            val cells = (0 until ((lead + days + 6) / 7) * 7).map { i -> (i - lead + 1).takeIf { it in 1..days }?.let { ym.atDay(it) } }
            cells.chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    week.forEach { d ->
                        Box(Modifier.weight(1f).aspectRatio(0.9f), contentAlignment = Alignment.TopCenter) {
                            if (d != null) DayCell(book, d, today, d == shown) { onPick(d) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(book: Book, d: LocalDate, today: LocalDate, selected: Boolean, onClick: () -> Unit) {
    val c = P.c
    val m = remember(book, d, today) { book.marks(d, today) }
    val stamped = book.closed.containsKey(d.toString())
    val shape = RoundedCornerShape(12.dp)
    Column(Modifier.fillMaxWidth().padding(2.dp).clip(shape)
        .then(if (selected) Modifier.background(c.linkWash) else Modifier)
        .then(if (d == today) Modifier.border(1.5.dp, c.link, shape) else Modifier)
        .clickable(onClick = onClick).padding(top = 8.dp, bottom = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text(d.dayOfMonth.toString(), style = Voice.words.copy(color = when {
            d.isBefore(today) -> c.graphite
            else -> c.ink
        }, textAlign = TextAlign.Center))
        Spacer(Modifier.height(4.dp))
        // The day's dots, drawn in the same alphabet as the marks.
        Canvas(Modifier.fillMaxWidth().height(8.dp)) {
            val r = 2.2.dp.toPx()
            val gap = 7.dp.toPx()
            val dots = buildList {
                repeat(minOf(m.open, 3)) { add(0) }
                repeat(minOf(m.events, 3 - size.coerceAtMost(3))) { add(1) }
                if (m.tail) add(2)
            }.take(4)
            val start = size.width / 2 - (dots.size - 1) * gap / 2
            dots.forEachIndexed { i, k ->
                val o = Offset(start + i * gap, size.height / 2)
                when (k) {
                    0 -> drawCircle(c.ink, r, o)
                    1 -> drawCircle(c.graphite, r, o, style = Stroke(1.2.dp.toPx()))
                    else -> drawCircle(c.red, r, o)
                }
            }
            if (stamped && dots.isEmpty()) {
                // Closed with the evening ritual: the page's stamp in miniature — a small red frame, a little askew.
                val cx = size.width / 2; val cy = size.height / 2
                rotate(-8f, Offset(cx, cy)) {
                    drawRoundRect(c.red.copy(alpha = 0.8f), topLeft = Offset(cx - 7.dp.toPx(), cy - 3.5.dp.toPx()),
                        size = androidx.compose.ui.geometry.Size(14.dp.toPx(), 7.dp.toPx()), cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.5.dp.toPx()),
                        style = Stroke(1.1.dp.toPx()))
                }
            } else if (m.closed && dots.isEmpty()) {
                val a = 3.dp.toPx()
                val cx = size.width / 2; val cy = size.height / 2
                drawLine(c.pencil, Offset(cx - a, cy), Offset(cx - a / 3, cy + a * 0.7f), 1.2.dp.toPx())
                drawLine(c.pencil, Offset(cx - a / 3, cy + a * 0.7f), Offset(cx + a, cy - a * 0.7f), 1.2.dp.toPx())
            }
        }
    }
}
