package ru.anjeib.margin.paper

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import ru.anjeib.margin.book.Book
import ru.anjeib.margin.book.lastDay
import ru.anjeib.margin.book.page
import ru.anjeib.margin.book.toDecide
import ru.anjeib.margin.book.ritualDay
import java.time.LocalDate

/*
 * «Перелистнуть день» (DESIGN.md §10): the evening ritual. Only the open to-dos of today, each with Margin's offers
 * under it; the mark still means «done». When every line has its decision, the page turns to tomorrow.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun RitualPage(book: Book, today: LocalDate, vm: BookVm, bottom: Dp, onDone: (LocalDate, Set<String>) -> Unit) {
    val c = P.c
    val w = P.w
    // The day being closed: until four at night still yesterday — and «завтра» is the day that has begun.
    val day = remember { ritualDay() }
    val next = day.plusDays(1)
    val open = book.toDecide(day)
    // What the evening was asked to decide — the stamp counts what came of it.
    val asked = remember { open.map { it.id }.toSet() }
    var picking by remember { mutableStateOf<String?>(null) }

    LazyColumn(Modifier.fillMaxSize().rule(c.rule), contentPadding = PaddingValues(bottom = bottom)) {
        item("head") {
            PageHead(marginTop = day.dayOfMonth.toString(), marginBottom = w.mon(day.monthValue), title = w.ritualTitle,
                line = if (open.isEmpty()) null else w.ritualLine(open.size))
        }
        items(open, key = { it.id }) { l ->
            Column(Modifier.animateItem()) {
                val tail = l.lastDay()?.isBefore(day) == true
                LineOnPage(l, day, today, book, if (tail) Seen.Tail else Seen.Page, false, onMark = vm::toggle, onOpen = {})
                FlowRow(Modifier.fillMaxWidth().offset(y = (-14).dp).padding(start = Grid.text - 6.dp, end = Grid.right)) {
                    Reply(w.doneReply) { vm.toggle(l.id) }
                    Reply(w.offerTomorrow) { vm.move(l.id, next, quiet = true) }
                    Reply(w.pickDayShort) { picking = l.id }
                    Reply(w.offerLater) { vm.move(l.id, null, quiet = true) }
                    Reply(w.dropped) { vm.drop(l.id, quiet = true) }
                }
            }
        }
        if (open.isNotEmpty()) item("rest") {
            MarginLink(w.restTomorrow, Modifier.padding(top = 10.dp)) { vm.carryAll(day, next) }
        } else item("done") {
            // Every line has its decision: a breath, then the page turns.
            LaunchedEffect(Unit) { delay(900); onDone(day, asked) }
            MarginSays(w.ritualDone, Modifier.padding(top = 8.dp))
        }
    }

    // Another day: the notebook's own month, the days' load in dots — a choice made with the week in view.
    picking?.let { id ->
        MonthSheet(book, today, next, onPick = { d -> vm.move(id, d, quiet = true); picking = null }, onClose = { picking = null })
    }
}

/** An answer in Margin's voice under a line: a word, a full touch high. */
@Composable
fun Reply(text: String, onClick: () -> Unit) {
    val c = P.c
    Text(text, style = Voice.reply.copy(color = c.link),
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 12.dp))
}
