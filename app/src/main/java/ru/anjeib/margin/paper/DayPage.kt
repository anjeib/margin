package ru.anjeib.margin.paper

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ru.anjeib.margin.book.Book
import ru.anjeib.margin.book.Kind
import ru.anjeib.margin.book.Line
import ru.anjeib.margin.book.State
import ru.anjeib.margin.book.page
import ru.anjeib.margin.book.summary
import ru.anjeib.margin.book.DayStats
import ru.anjeib.margin.book.toDecide
import ru.anjeib.margin.book.ritualDay
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
    /** The day whose stamp is being pressed right now. */
    stamping: LocalDate? = null,
    /** Meetings from the phone's calendar for this day: read only, among the hours. */
    calendar: List<Line> = emptyList(),
    /** To the next page (after the stamp). */
    onNext: () -> Unit = {},
    /** «на полях: …» tapped: those lines over the field. */
    onExplain: (LocalDate) -> Unit = {},
) {
    val c = P.c
    val w = P.w
    val page = remember(book, date, today, calendar) { book.page(date, today).let { p ->
        if (calendar.isEmpty()) p else p.copy(timed = (p.timed + calendar.filter { it.time != null }).sortedBy { it.time },
            rest = calendar.filter { it.time == null } + p.rest)
    } }
    val sum = remember(book, date, today) { book.summary(date, today) }
    var showDone by rememberSaveable(date) { mutableStateOf(false) }
    val doneRest = page.rest.filter { it.state != State.Open && it.isTodo }
    val folded = doneRest.size > 5 && !showDone
    val rest = if (folded) page.rest - doneRest.toSet() else page.rest

    // The hour now, refreshed every half minute and at once when the notebook is opened again — for the tick among the
    // hours and the evening's way to close the day.
    var clock by remember { mutableStateOf(LocalTime.now().let { "%02d:%02d".format(it.hour, it.minute) }) }
    LaunchedEffect(date) {
        while (date == today) {
            kotlinx.coroutines.delay(30_000)
            clock = LocalTime.now().let { "%02d:%02d".format(it.hour, it.minute) }
        }
    }
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(owner) {
        val o = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) clock = LocalTime.now().let { "%02d:%02d".format(it.hour, it.minute) }
        }
        owner.lifecycle.addObserver(o)
        onDispose { owner.lifecycle.removeObserver(o) }
    }
    // Words just written land on this page: it scrolls to them if they would be out of sight.
    val list = androidx.compose.foundation.lazy.rememberLazyListState()
    LaunchedEffect(fresh) {
        if (fresh.isEmpty()) return@LaunchedEffect
        val shown = page.tails + page.timed + rest
        val i = shown.indexOfFirst { it.id in fresh }
        if (i >= 0) runCatching { list.animateScrollToItem(i + 1) }
    }
    LazyColumn(Modifier.fillMaxSize().rule(c.rule), state = list, contentPadding = PaddingValues(bottom = bottom)) {
        item("head") {
            val near = if (date != today) w.near(date, today).takeIf { it != w.dayMonth(date) && it != w.dow(date) } else null
            Box {
                // While the stamp lands: what the next page holds, the way there.
                val closing = stamping == date
                val next = remember(book, date, closing) {
                    if (!closing) emptyList() else book.page(date.plusDays(1), today).let { p -> (p.timed + p.rest).filter { (it.isTodo && it.open) || it.kind == Kind.Event } }
                }
                PageHead(
                    reserve = if (book.closed.containsKey(date.toString())) 118.dp else 0.dp,
                    marginTop = date.dayOfMonth.toString(), marginBottom = w.mon(date.monthValue) + " ▾",
                    title = w.weekday(date),
                    line = if (closing) w.nextPage(next) else listOfNotNull(near, w.summary(sum).ifBlank { null }).joinToString(" · ").ifBlank { null },
                    onMargin = onMonth,
                    onLine = if (closing) onNext else if (!sum.empty) ({ onExplain(date) }) else null,
                    lineColor = if (closing) c.link else c.graphite,
                )
                // A day closed in the evening keeps its stamp in the corner: the hour, what was done, what moved on.
                book.closed[date.toString()]?.let { close ->
                    val st = DayStats(close.done, close.dropped, close.moved)
                    DayStamp(w.stampTitle, close.at, st, w.stampTitle + ". " + w.stampLine(close.at, st), fresh = stamping == date,
                        modifier = Modifier.align(Alignment.TopEnd).padding(top = 14.dp, end = 22.dp))
                }
            }
        }
        // The evening: the way to close the day stands on top, where it is seen — every open line gets a decision.
        val hour = clock.take(2).toIntOrNull() ?: LocalTime.now().hour
        val undecided = if (date == today && hour.let { it >= eveningHour - 3 || it < 4 }) book.toDecide(ritualDay()).size else 0
        if (undecided > 0) item("turn") { MarginLink(w.turnDayCount(undecided), Modifier.padding(bottom = 6.dp), onTurn) }
        items(page.tails, key = { "t" + it.id }) { l -> LineOnPage(l, date, today, book, Seen.Tail, l.id in fresh, onMark, onOpen) }
        // Today, among the hours: a red tick on the rule where «now» is — the page shows where the day stands.
        // At night the day not yet turned is still today, and «now» is past all its hours: the tick goes after them.
        val night = LocalDate.now().isAfter(date)
        val nowAt = if (date == today && page.timed.isNotEmpty()) (if (night) page.timed.size else page.timed.indexOfFirst { (it.time ?: "") > clock }.let { if (it < 0) page.timed.size else it }) else -1
        page.timed.forEachIndexed { i, l ->
            if (i == nowAt) item("now") { NowTick(clock) }
            item("h" + l.id) { LineOnPage(l, date, today, book, Seen.Page, l.id in fresh, onMark, onOpen) }
        }
        if (nowAt == page.timed.size && nowAt > 0) item("now") { NowTick(clock) }
        items(rest, key = { "r" + it.id }) { l -> LineOnPage(l, date, today, book, Seen.Page, l.id in fresh, onMark, onOpen) }
        if (folded) item("folded") { MarginLink(w.doneMore(doneRest.size)) { showDone = true } }
        items(page.traces, key = { "m" + it.id }) { l -> LineOnPage(l, date, today, book, Seen.Trace, false, onMark, onOpen) }
        if (page.all.isEmpty() && page.traces.isEmpty()) item("empty") {
            MarginSays(if (date == today) w.emptyToday else w.emptyDay, Modifier.padding(top = 8.dp))
        }

    }
}

/** One line on a page: its notes, its mark, your words; a fresh one is written in with the pen. */
@Composable
fun LineOnPage(l: Line, page: LocalDate?, today: LocalDate, book: Book, seen: Seen, fresh: Boolean,
               onMark: (String) -> Unit, onOpen: (String) -> Unit, lead: Note? = null, hideThread: String? = null, hidePerson: String? = null,
               /** A thread's or a person's page: every open step stands under its line (no day's page shows the dated ones there). */
               allSteps: Boolean = false) {
    val c = P.c
    val w = P.w
    val notes = remember(l, page, today, book.threads, book.steps(l.id), seen, w, lead) {
        // Two notes at most on the side (a day and an hour); under the words — all the rest, in one quiet line.
        (listOfNotNull(lead) + notesOf(l, page, today, book, w, seen, hideThread, hidePerson)).let { n -> n.filter { !it.under }.take(2) + n.filter { it.under } }
    }
    val trace = seen == Seen.Trace
    val ink = when {
        trace -> c.graphite
        l.state == State.Dropped -> c.pencil
        l.state == State.Done -> c.graphite
        else -> c.ink
    }
    val pen = remember(l.id) { Animatable(if (fresh) 0f else 1f) }
    // A line just written glows for a moment in the link's wash — easy to find where it landed.
    val wash = remember(l.id) { Animatable(0f) }
    LaunchedEffect(fresh) {
        if (fresh) {
            wash.snapTo(1f)
            pen.animateTo(1f, tween(420))
            kotlinx.coroutines.delay(700)
            wash.animateTo(0f, tween(1400))
        } else {
            // «Fresh» may end before the wash has faded (a slow phone, a page redrawn): it fades all the same.
            if (pen.value < 1f) pen.snapTo(1f)
            if (wash.value > 0f) wash.animateTo(0f, tween(600))
        }
    }
    // A long note shows its first lines; the rest is a tap away, and the margin says so.
    var cut by remember(l.id) { mutableStateOf(false) }
    MarginRow(
        notes = if (cut) notes + Note(w.whole, Tone.Link, under = true) else notes, mark = markOf(l, trace),
        modifier = Modifier.drawBehind { if (wash.value > 0f) drawRect(c.linkWash.copy(alpha = wash.value)) }
            .graphicsLayer { alpha = pen.value; translationX = (1f - pen.value) * -24.dp.toPx() },
        markLabel = w.a11yMark(l.state == State.Done),
        onMark = if (l.kind == Kind.Todo && !trace) ({ onMark(l.id) }) else null,
        onOpen = { onOpen(l.id) },
    ) {
        PenText(l.text, Voice.words.copy(color = ink), struck = !trace && l.state != State.Open && l.kind == Kind.Todo, lineColor = c.graphite,
            maxLines = 5, onCut = { if (it != cut) cut = it }, rich = if (hasLink(l.text)) linked(l.text, c.link) else null)
    }
    // What is to be done under the line — a list's items, a note's steps — stands under it on the page, as written in a
    // notebook: open ones, a few at most (the rest in the sheet). Steps with a day of their own stand on that day instead.
    if (!trace && l.parentId == null && l.state == State.Open) {
        // Ticked today stays, struck through — one sees what is bought, not only what is left.
        val todayIs = today.toString()
        val open = remember(book, l.id, allSteps) { book.children(l.id).filter { it.isTodo && (allSteps || it.day == null) &&
            (it.open || (it.state == State.Done && it.doneAt?.take(10) == todayIs)) }.sortedBy { it.writtenAt } }
        open.take(6).forEach { k -> StepRow(k, ink, onMark = { onMark(k.id) }, onOpen = { onOpen(l.id) }) }
        if (open.size > 6) MarginLink(w.moreSteps(open.size - 6), Modifier.padding(start = 22.dp)) { onOpen(l.id) }
    }
}

/** An item under a line, written a little in from the rule and closer together than lines: its own dot to tick. */
@Composable
private fun StepRow(k: Line, ink: androidx.compose.ui.graphics.Color, onMark: () -> Unit, onOpen: () -> Unit) {
    val w = P.w
    androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth().heightIn(min = 38.dp).clickable(onClick = onOpen).padding(end = Grid.right),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        androidx.compose.foundation.layout.Spacer(Modifier.width(Grid.text - 4.dp))
        androidx.compose.foundation.layout.Box(Modifier.size(34.dp).clickable(onClick = onMark).semantics { contentDescription = w.a11yMark(k.state == State.Done) },
            contentAlignment = androidx.compose.ui.Alignment.Center) { Mark(markOf(k), size = Grid.node * 0.8f) }
        // (a tap on a ticked item takes the tick back, as on the line itself)
        val done = k.state == State.Done
        Text(k.text, style = Voice.words.copy(color = if (done) P.c.graphite else ink, fontSize = Voice.words.fontSize * 0.94f,
            textDecoration = if (done) androidx.compose.ui.text.style.TextDecoration.LineThrough else null), maxLines = 2,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.padding(start = 2.dp))
    }
}

/** «Сейчас»: the hour on the margin in red pen and a short stroke across the rule — no line of its own to read. */
@Composable
private fun NowTick(clock: String) {
    val c = P.c
    androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().height(18.dp).semantics { contentDescription = clock }) {
        androidx.compose.foundation.layout.Box(Modifier.width(Grid.margin - Grid.noteGap).padding(start = 4.dp).align(androidx.compose.ui.Alignment.CenterStart),
            contentAlignment = androidx.compose.ui.Alignment.CenterEnd) {
            Text(clock, style = Voice.note.copy(color = c.red, fontSize = 11.sp))
        }
        androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(18.dp)) {
            val x = Grid.margin.toPx(); val y = size.height / 2
            drawLine(c.red, androidx.compose.ui.geometry.Offset(x - 6.dp.toPx(), y), androidx.compose.ui.geometry.Offset(x + 6.dp.toPx(), y), 1.6.dp.toPx(),
                cap = androidx.compose.ui.graphics.StrokeCap.Round)
        }
    }
}
