package ru.anjeib.margin.paper

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ru.anjeib.margin.book.Book
import ru.anjeib.margin.book.Kind
import ru.anjeib.margin.book.Line
import ru.anjeib.margin.book.State
import ru.anjeib.margin.book.page
import ru.anjeib.margin.book.summary
import java.time.LocalDate
import java.time.LocalTime

/*
 * A page of the notebook: one day (DESIGN.md §3). Tails on top (today only), then by the hour, then the rest as written;
 * what left the page stays as a trace. The rule runs down the whole page, the date sits on its margin.
 */
@Composable
fun DayPage(
    date: LocalDate,
    book: Book,
    today: LocalDate,
    fresh: Set<String>,
    bottom: Dp,
    onMark: (String) -> Unit,
    onOpen: (String) -> Unit,
    onMonth: () -> Unit,
    onTurn: () -> Unit,
    eveningHour: Int = 21,
) {
    val c = P.c
    val w = P.w
    val page = remember(book, date, today) { book.page(date, today) }
    val sum = remember(book, date, today) { book.summary(date, today) }
    var showDone by rememberSaveable(date) { mutableStateOf(false) }
    val doneRest = page.rest.filter { it.state != State.Open && it.isTodo }
    val folded = doneRest.size > 5 && !showDone
    val rest = if (folded) page.rest - doneRest.toSet() else page.rest

    LazyColumn(Modifier.fillMaxSize().rule(c.rule), contentPadding = PaddingValues(bottom = bottom)) {
        item("head") {
            val near = if (date != today) w.near(date, today).takeIf { it != w.dayMonth(date) && it != w.dow(date) } else null
            PageHead(
                marginTop = date.dayOfMonth.toString(), marginBottom = w.mon(date.monthValue),
                title = w.weekday(date),
                line = listOfNotNull(near, w.summary(sum).ifBlank { null }).joinToString(" · ").ifBlank { null },
                onMargin = onMonth,
            )
        }
        items(page.tails, key = { "t" + it.id }) { l -> LineOnPage(l, date, today, book, Seen.Tail, l.id in fresh, onMark, onOpen) }
        items(page.timed, key = { "h" + it.id }) { l -> LineOnPage(l, date, today, book, Seen.Page, l.id in fresh, onMark, onOpen) }
        items(rest, key = { "r" + it.id }) { l -> LineOnPage(l, date, today, book, Seen.Page, l.id in fresh, onMark, onOpen) }
        if (folded) item("folded") { MarginLink(w.doneMore(doneRest.size)) { showDone = true } }
        items(page.traces, key = { "m" + it.id }) { l -> LineOnPage(l, date, today, book, Seen.Trace, false, onMark, onOpen) }
        if (page.all.isEmpty() && page.traces.isEmpty()) item("empty") {
            MarginSays(if (date == today) w.emptyToday else w.emptyDay, Modifier.padding(top = 8.dp))
        }
        // The evening: one quiet way to close the day — every open line gets a decision.
        if (date == today && LocalTime.now().hour.let { it >= eveningHour - 3 || it < 4 } && page.open.isNotEmpty()) item("turn") {
            MarginLink(w.turnDay, Modifier.padding(top = 8.dp), onTurn)
        }
    }
}

/** One line on a page: its notes, its mark, your words; a fresh one is written in with the pen. */
@Composable
fun LineOnPage(l: Line, page: LocalDate?, today: LocalDate, book: Book, seen: Seen, fresh: Boolean,
               onMark: (String) -> Unit, onOpen: (String) -> Unit, lead: Note? = null, hideThread: String? = null, hidePerson: String? = null) {
    val c = P.c
    val w = P.w
    val notes = remember(l, page, today, book.threads, seen, w, lead) {
        (listOfNotNull(lead) + notesOf(l, page, today, book, w, seen, hideThread, hidePerson)).take(3)
    }
    val trace = seen == Seen.Trace
    val ink = when {
        trace -> c.graphite
        l.state == State.Dropped -> c.pencil
        l.state == State.Done -> c.graphite
        else -> c.ink
    }
    val pen = remember(l.id) { Animatable(if (fresh) 0f else 1f) }
    LaunchedEffect(fresh) { if (fresh) pen.animateTo(1f, tween(420)) }
    MarginRow(
        notes = notes, mark = markOf(l, trace),
        modifier = Modifier.graphicsLayer { alpha = pen.value; translationX = (1f - pen.value) * -24.dp.toPx() },
        markLabel = w.a11yMark(l.state == State.Done),
        onMark = if (l.kind == Kind.Todo && !trace) ({ onMark(l.id) }) else null,
        onOpen = { onOpen(l.id) },
    ) {
        Text(l.text, style = Voice.words.copy(color = ink,
            textDecoration = if (!trace && l.state != State.Open && l.kind == Kind.Todo) TextDecoration.LineThrough else null))
    }
}
