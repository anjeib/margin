package ru.anjeib.margin.paper

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ru.anjeib.margin.book.Book
import ru.anjeib.margin.book.Kind
import ru.anjeib.margin.book.lastDay
import ru.anjeib.margin.book.later
import ru.anjeib.margin.book.page
import java.time.LocalDate
import java.time.YearMonth

/*
 * «Календарь» (DESIGN.md §11): where the lines written for other days are. On top — the month with its dots (a tap opens
 * the day's page); under it — the days ahead, each under its date on the margin with its lines, as on their pages.
 * Opened from the calendar sign at the top: a line just sent to another day is shown there, written in.
 */
@Composable
fun CalendarPage(book: Book, today: LocalDate, focus: BookVm.Flight?, onDay: (LocalDate) -> Unit, onMark: (String) -> Unit,
                 onOpen: (String) -> Unit, onLater: () -> Unit, bottom: Dp, from: LocalDate? = null, onWeek: (LocalDate) -> Unit = {}) {
    val c = P.c
    val w = P.w
    var ym by rememberSaveable { mutableStateOf(YearMonth.from(focus?.day ?: from ?: today).toString()) }
    // Six weeks ahead, only the days that hold something. A line with a window («до пн») is on its pages from the first
    // day to the last; here it stands once — on the first of them.
    val days = remember(book, today) {
        val seen = HashSet<String>()
        (0L..41L).map { today.plusDays(it) }.map { d ->
            d to book.page(d, today).let { p -> (if (d == today) p.tails else emptyList()) + p.timed + p.rest }.filter { seen.add(it.id) }
        }.filter { it.second.isNotEmpty() }
    }
    // This week in numbers — counted as the week's spread counts them, so the two never disagree.
    val monday = today.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
    val week = remember(book, today) {
        val seen = HashSet<String>()
        (0L..6L).map { monday.plusDays(it) }.flatMap { d -> book.page(d, today).let { p -> (if (d == today) p.tails else emptyList()) + p.timed + p.rest }
            .filter { (it.isTodo || it.kind == Kind.Event || it.kind == Kind.Note) && seen.add(it.id) } }
    }
    val later = remember(book) { book.later().size }
    val state = rememberLazyListState()
    // A line just sent here: its day scrolled into view (head, month, then each day's head and lines).
    LaunchedEffect(focus?.id) {
        val f = focus ?: return@LaunchedEffect
        var i = 2
        for ((d, lines) in days) { if (!d.isBefore(f.day)) break; i += 1 + lines.size }
        kotlinx.coroutines.delay(250)
        state.animateScrollToItem(i)
    }
    LazyColumn(state = state, modifier = Modifier.fillMaxSize().rule(c.rule), contentPadding = PaddingValues(bottom = bottom)) {
        item("head") {
            PageHead(marginTop = "", marginBottom = null, title = w.calendarTitle,
                line = w.thisWeek + w.weekLine(week.count { it.isTodo && it.open }, week.count { it.isTodo && !it.open }, week.count { it.kind == Kind.Event && it.state == ru.anjeib.margin.book.State.Open }))
        }
        item("month") {
            val shown = YearMonth.parse(ym)
            Column(Modifier.fillMaxWidth().padding(start = Grid.text - 10.dp, end = 10.dp, bottom = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(w.monthName(shown), style = Voice.section.copy(color = c.ink), modifier = Modifier.padding(start = 10.dp))
                    // The week's spread, from the same page.
                    Text(w.weekLink, style = Voice.reply.copy(color = c.link), modifier = Modifier.padding(start = 12.dp)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(6.dp)).clickable { onWeek(from ?: today) }.padding(horizontal = 4.dp, vertical = 10.dp))
                    Spacer(Modifier.weight(1f))
                    listOf("‹" to -1L, "›" to 1L).forEach { (sign, step) ->
                        Text(sign, style = Voice.section.copy(color = c.graphite), modifier = Modifier.clip(CircleShape)
                            .clickable { ym = shown.plusMonths(step).toString() }.semantics { contentDescription = sign }.padding(horizontal = 14.dp, vertical = 6.dp))
                    }
                }
                MonthGrid(book, today, shown, focus?.day, onPick = onDay)
            }
        }
        if (days.isEmpty()) item("empty") { MarginSays(w.calendarEmpty, Modifier.padding(top = 12.dp)) }
        days.forEach { (d, lines) ->
            item("d$d") {
                // The day under its date on the margin: «пт 16»; today in the link's colour. A tap opens the day's page.
                Row(Modifier.fillMaxWidth().heightIn(min = 40.dp).clickable { onDay(d) }.padding(top = 14.dp, end = Grid.right),
                    verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(Grid.margin - Grid.noteGap).padding(start = 4.dp), contentAlignment = Alignment.CenterEnd) {
                        Text(w.dow(d) + " " + d.dayOfMonth, style = Voice.reply.copy(color = if (d == today) c.link else c.ink))
                    }
                    Spacer(Modifier.width(Grid.text - Grid.margin + Grid.noteGap))
                    Text(w.dayAhead(d, today), style = Voice.note.copy(color = c.graphite))
                }
            }
            lines.forEach { l ->
                item("l$d${l.id}") {
                    LineOnPage(l, d, today, book, if (d == today && l.lastDay()?.isBefore(today) == true) Seen.Tail else Seen.Page,
                        fresh = focus?.ids?.contains(l.id) == true, onMark = onMark, onOpen = onOpen)
                }
            }
        }
        if (later > 0) item("later") { MarginLink(w.laterLink(later), Modifier.padding(top = 16.dp), onClick = onLater) }
    }
}
