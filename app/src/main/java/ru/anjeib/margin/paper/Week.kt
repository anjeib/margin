package ru.anjeib.margin.paper

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ru.anjeib.margin.book.Book
import ru.anjeib.margin.book.Kind
import ru.anjeib.margin.book.page
import ru.anjeib.margin.book.lastDay
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/*
 * The week spread, as in a paper planner: seven days down one page, each day's lines under its date on the margin —
 * what stands ahead, what got done. Turned with ‹ ›; a day's date opens its page.
 */
@Composable
fun WeekPage(book: Book, today: LocalDate, start: LocalDate, onMark: (String) -> Unit, onOpen: (String) -> Unit, onDay: (LocalDate) -> Unit,
             bottom: androidx.compose.ui.unit.Dp) {
    val c = P.c
    val w = P.w
    var monday by rememberSaveable { mutableStateOf(start.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString()) }
    val mon = LocalDate.parse(monday)
    val days = (0L..6L).map { mon.plusDays(it) }
    val pages = remember(book, mon, today) {
        // A line with a window stands once in the week — on its first day here, not on each of its days.
        val seen = HashSet<String>()
        days.map { d -> d to book.page(d, today).let { p -> (if (d == today) p.tails else emptyList()) + p.timed + p.rest }
            .filter { (it.isTodo || it.kind == Kind.Event || it.kind == Kind.Note) && seen.add(it.id) } }
    }
    val all = pages.flatMap { it.second }
    LazyColumn(Modifier.fillMaxSize().rule(c.rule), contentPadding = PaddingValues(bottom = bottom)) {
        item("head") {
            Box {
                PageHead(marginTop = "${mon.dayOfMonth}–${mon.plusDays(6).dayOfMonth}", marginBottom = w.mon(mon.plusDays(6).monthValue),
                    title = w.weekTitle, line = w.weekLine(all.count { it.isTodo && it.open }, all.count { it.isTodo && !it.open }, all.count { it.kind == Kind.Event }))
                // ‹ › turn the spread.
                Row(Modifier.align(Alignment.TopEnd).padding(top = 22.dp, end = 8.dp)) {
                    listOf("‹" to -1L, "›" to 1L).forEach { (sign, step) ->
                        Text(sign, style = Voice.section.copy(color = c.graphite), modifier = Modifier.clip(CircleShape)
                            .clickable { monday = mon.plusWeeks(step).toString() }.semantics { contentDescription = sign }.padding(horizontal = 14.dp, vertical = 6.dp))
                    }
                }
            }
        }
        pages.forEach { (d, lines) ->
            item("d$d") {
                // The day under its date on the margin; today in the link's colour. A tap opens the day's page.
                Row(Modifier.fillMaxWidth().heightIn(min = 40.dp).clickable { onDay(d) }.padding(top = 10.dp, end = Grid.right), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(Grid.margin - Grid.noteGap).padding(start = 4.dp), contentAlignment = Alignment.CenterEnd) {
                        Text(w.dow(d) + " " + d.dayOfMonth, style = Voice.reply.copy(color = if (d == today) c.link else if (d.isBefore(today)) c.graphite else c.ink))
                    }
                    Spacer(Modifier.width(Grid.text - Grid.margin + Grid.noteGap))
                    if (lines.isEmpty()) Text(w.weekFree, style = Voice.note.copy(color = c.pencil))
                }
            }
            lines.forEach { l -> item("l$d${l.id}") { LineOnPage(l, d, today, book, if (d == today && l.lastDay()?.isBefore(today) == true) Seen.Tail else Seen.Page, false, onMark, onOpen) } }
        }
    }
}
