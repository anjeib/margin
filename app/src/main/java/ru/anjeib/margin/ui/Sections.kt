package ru.anjeib.margin.ui

import androidx.compose.ui.text.font.FontWeight

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import ru.anjeib.margin.data.dayMonthShort
import ru.anjeib.margin.data.dowFull
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.anjeib.margin.data.AppData
import ru.anjeib.margin.data.Task
import ru.anjeib.margin.data.isOverdue
import ru.anjeib.margin.data.shortLabel
import ru.anjeib.margin.i18n.L
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle

/** «Календарь»: a month with how many open tasks fall on each day; a tap lists that day below. */
@Composable
fun CalendarScreen(data: AppData, vm: MainVm, onOpenTask: (String) -> Unit, contentPadding: PaddingValues, events: List<ru.anjeib.margin.data.CalEvent> = emptyList()) {
    val c = M.c
    val s = M.s
    val today = LocalDate.now()
    var month by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    var picked by rememberSaveable { mutableStateOf(today.toString()) }
    // Opened from «Завтра — N дел ›»: on that day, its month shown.
    // …and from the planner's day tabs while the calendar is open: they pick a day here, they don't leave it (Fable, 14 окт).
    val openOn by vm.calendarOpenOn.collectAsState()
    androidx.compose.runtime.LaunchedEffect(openOn) {
        openOn?.let { d -> picked = d.toString(); month = YearMonth.from(d).toString(); vm.calendarOpenOn.value = null }
    }
    val ym = YearMonth.parse(month)
    val day = LocalDate.parse(picked)
    // A picked day other than today is where the next written matter goes (the field says so).
    androidx.compose.runtime.LaunchedEffect(picked) { vm.pickCalendarDay(day.takeIf { it != today }) }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { vm.pickCalendarDay(null) } }
    // The phone calendar for the whole visible grid (the AI context only needs a week ahead).
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val calOn = vm.settings.collectAsState().value.calendar
    val monthEvents by androidx.compose.runtime.produceState(events, month, calOn) {
        value = if (!calOn) emptyList() else kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val from = ym.atDay(1).minusDays(ym.atDay(1).dayOfWeek.value - 1L)
            ru.anjeib.margin.data.PhoneCalendar.events(ctx, from, 42)
        }
    }
    // Each cell counts exactly the rows its day lists below: today with what is late, a past day with what got done.
    val plans = remember(data, month, today, monthEvents) {
        val from = ym.atDay(1).minusDays(ym.atDay(1).dayOfWeek.value - 1L)
        (0L until 42L).associate { val d = from.plusDays(it); d to dayPlan(data, d, today, monthEvents) }
    }
    var postponing by remember { mutableStateOf<String?>(null) }
    postponing?.let { id -> PostponeDialog(onDismiss = { postponing = null }) { d -> vm.postponeTo(id, d); postponing = null } }
    var timeFor by remember { mutableStateOf<String?>(null) }
    timeFor?.let { id ->
        val cur = ru.anjeib.margin.data.questionRef(id)?.let { (tid, qid) -> data.tasks.find { it.id == tid }?.subtasks?.find { it.id == qid }?.time } ?: data.tasks.find { it.id == id }?.time
        TimeDialog(cur, onDismiss = { timeFor = null }) { tm -> vm.setTime(id, tm); timeFor = null }
    }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    // Once the month has scrolled away, the picked day's week stays pinned on top — the month folds into a week.
    val folded by remember { androidx.compose.runtime.derivedStateOf { listState.firstVisibleItemIndex >= 2 } }
    // One day of the grid: the number, how many matters; a dot for meetings; the picked day in petrol, today outlined.
    @Composable fun cell(d: LocalDate, mod: Modifier) {
        val plan = plans[d] ?: dayPlan(data, d, today, monthEvents)
        val past = d.isBefore(today)
        val count = if (past) plan.done.size else plan.deals.size
        val late = d == today && plan.deals.any { it.isOverdue(today) }
        val meets = plan.events.isNotEmpty()
        val sel = d == day
        Column(mod.clip(RoundedCornerShape(12.dp))
            .background(if (sel) c.link else Color.Transparent)
            .then(if (d == today && !sel) Modifier.border(1.dp, c.link, RoundedCornerShape(12.dp)) else Modifier)
            .clickable { picked = d.toString(); if (YearMonth.from(d) != ym) month = YearMonth.from(d).toString() }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text("${d.dayOfMonth}", style = Type.body.copy(color = when { sel -> c.bg; past -> c.graphite; else -> c.ink }, fontSize = 15.sp, fontWeight = if (sel || d == today) FontWeight.SemiBold else FontWeight.Normal))
            // How busy the day is — a number, never a colour per project: open matters ahead (terracotta when today has late ones),
            // done ones on a past day with a tick; a meeting from the phone calendar — a small dot beside it.
            // Dots, not a second number under the date: one per matter up to three; petrol ahead, terracotta when today
            // has late ones, sage for what got done on a past day; a meeting — a small grey ring beside them.
            Row(Modifier.height(10.dp).padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.5.dp)) {
                val dot = when { sel -> c.bg; past -> c.ok; late -> c.accent; else -> c.link }
                repeat(count.coerceAtMost(3)) { Box(Modifier.size(5.dp).clip(CircleShape).background(dot)) }
                if (meets) Box(Modifier.size(5.dp).border(1.dp, if (sel) c.bg else c.graphite, CircleShape))
            }
        }
    }
    Box(Modifier.fillMaxSize().screenRule()) {
    LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = contentPadding.vertical()) {
        item("h") {
            Margined(Modifier.padding(top = 14.dp, bottom = 10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(ym.month.getDisplayName(TextStyle.FULL_STANDALONE, if (L.lang == ru.anjeib.margin.i18n.Lang.Ru) java.util.Locale.forLanguageTag("ru") else java.util.Locale.ENGLISH)
                    .replaceFirstChar { it.uppercase() } + if (ym.year != today.year) " ${ym.year}" else "",
                    style = Type.display.copy(color = c.ink), modifier = Modifier.weight(1f))
                Box(Modifier.size(44.dp).clip(CircleShape).clickable { month = ym.minusMonths(1).toString() }, contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, s.prevMonth, tint = c.graphite)
                }
                Box(Modifier.size(44.dp).clip(CircleShape).clickable { month = ym.plusMonths(1).toString() }, contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, s.nextMonth, tint = c.graphite)
                }
            }
            }
        }
        item("grid") {
            Margined {
            Column {
                Row(Modifier.fillMaxWidth()) {
                    (1..7).forEach { d ->
                        Text(java.time.DayOfWeek.of(d).getDisplayName(TextStyle.SHORT, if (L.lang == ru.anjeib.margin.i18n.Lang.Ru) java.util.Locale.forLanguageTag("ru") else java.util.Locale.ENGLISH).lowercase(),
                            style = Type.small.copy(color = c.graphite, fontSize = 12.sp, fontWeight = FontWeight.Medium), modifier = Modifier.weight(1f).padding(bottom = 6.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
                val first = ym.atDay(1)
                val lead = first.dayOfWeek.value - 1
                val cells = lead + ym.lengthOfMonth()
                (0 until (cells + 6) / 7).forEach { w ->
                    Row(Modifier.fillMaxWidth()) {
                        (0..6).forEach { i ->
                            val n = w * 7 + i - lead + 1
                            Box(Modifier.weight(1f).aspectRatio(0.9f).padding(2.dp), contentAlignment = Alignment.Center) {
                                if (n in 1..ym.lengthOfMonth()) cell(ym.atDay(n), Modifier.fillMaxSize())
                            }
                        }
                    }
                }
            }
            }
        }
        // The picked day, the same panel as on «Сегодня»: its hours on the margin, done ones struck through.
        val plan = plans[day] ?: dayPlan(data, day, today, monthEvents)
        item("day-head") {
            SectionHead(if (day == today) s.today else day.dowFull() + ", " + day.dayMonthShort(), top = 18.dp,
                count = (plan.deals.size + plan.events.size).takeIf { it > 0 })
        }
        if (plan.quiet) item("day-free") { Margined { Voice(if (day == today) s.freeDay else s.freeWord.replaceFirstChar { it.uppercase() }) } }
        else dayPanel(plan, today, "c" + day, emptySet(), onOpenTask, { vm.completeWithUndo(it) }, { postponing = it }, { timeFor = it })
        // Topics and ideas begun that day (owner, 13 окт): a day in the calendar shows what was started on it, not only what is due.
        val begun = data.tasks.filter { t -> t.topic && !t.sample && t.createdAt == day.toString() }
        if (begun.isNotEmpty()) {
            item("day-begun") { SectionHead(s.begunThatDay, top = 16.dp) }
            begun.forEachIndexed { i, t ->
                item("b" + t.id) {
                    val seat = seatOf(i, begun.size)
                    if (t.thought) PaperRow(seat, mark = { IdeaMark(26.dp) }, onClick = { onOpenTask(t.id) }) { RowTitle(t.title.ifBlank { s.untitled }, maxLines = 2) }
                    else TopicPaperRow(t, seat, onOpen = { onOpenTask(t.id) })
                }
            }
        }
    }
    androidx.compose.animation.AnimatedVisibility(folded, enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(160)) + androidx.compose.animation.expandVertically(androidx.compose.animation.core.tween(200)),
        exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(160)) + androidx.compose.animation.shrinkVertically(androidx.compose.animation.core.tween(200))) {
        Column(Modifier.fillMaxWidth().background(c.bg)) {
            Margined(Modifier.padding(top = 6.dp, bottom = 4.dp)) {
                val monday = day.minusDays(day.dayOfWeek.value - 1L)
                Row(Modifier.fillMaxWidth()) { (0L..6L).forEach { i -> cell(monday.plusDays(i), Modifier.weight(1f).height(52.dp).padding(2.dp)) } }
            }
            Hairline(Modifier.padding(start = MarginW))
        }
    }
    }
}

/** «Архив»: notes and people put away by a swipe — each comes back with «Вернуть». */
@Composable
fun ArchiveScreen(data: AppData, vm: MainVm, onOpenNote: (String) -> Unit, onOpenPerson: (String) -> Unit, contentPadding: PaddingValues, onOpenTask: (String) -> Unit = {}) {
    val c = M.c
    val s = M.s
    val notes = data.notes.filter { it.archived }
    val people = data.persons.filter { it.archived }
    val topics = data.tasks.filter { it.topic && it.done }.sortedByDescending { it.doneAt }
    Box(Modifier.fillMaxSize().screenRule()) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding.vertical()) {
        item("h") { PageHead(s.archiveTitle, s.archiveSub) }
        if (notes.isEmpty() && people.isEmpty() && topics.isEmpty()) item("empty") {
            Margined(Modifier.padding(top = 8.dp)) { Voice(s.archiveEmpty) }
        }
        // v2: settled topics with their materials; news in one brings it back. The day it was settled on the margin.
        if (topics.isNotEmpty()) item("th") { SectionHead(s.topicsBadge, count = topics.size, top = 12.dp) }
        topics.forEachIndexed { i, t ->
            item("t" + t.id) {
                Box(Modifier.animateItem()) {
                    TopicPaperRow(t, seatOf(i, topics.size), onOpen = { onOpenTask(t.id) },
                        margin = t.doneAt?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() }?.let { dayWord(it) },
                        trailing = { PaperLink(s.bringBack) { vm.reviveTopic(t.id) } })
                }
            }
        }
        if (notes.isNotEmpty()) item("nh") { SectionHead(s.notes, count = notes.size) }
        notes.forEachIndexed { i, n ->
            item("n" + n.id) {
                Box(Modifier.animateItem()) {
                    NoteCard(n, data, onClick = { onOpenNote(n.id) }, seat = seatOf(i, notes.size), trailing = { PaperLink(s.restoreWord) { vm.archiveNote(n.id, false) } })
                }
            }
        }
        if (people.isNotEmpty()) item("ph") { SectionHead(s.peopleTitle, count = people.size) }
        people.forEachIndexed { i, p ->
            item("p" + p.id) {
                PaperRow(seatOf(i, people.size), mark = { Avatar(p.name, 32.dp) }, modifier = Modifier.animateItem(), onClick = { onOpenPerson(p.id) },
                    trailing = { PaperLink(s.restoreWord) { vm.archivePerson(p.id, false) } }) {
                    RowTitle(p.name, maxLines = 1)
                }
            }
        }
    }
    }
}

/**
 * The month at the top of «Сегодня» (owner, 14 окт: no calendar page of its own): the calendar tab unfolds it over the day,
 * on the day the page shows; a tap on a cell turns the page to that day, ‹ › go by months. Folded again by the same tab.
 */
@Composable
fun MonthPanel(data: AppData, vm: MainVm, day: LocalDate, events: List<ru.anjeib.margin.data.CalEvent>, onPick: (LocalDate) -> Unit) {
    val c = M.c
    val s = M.s
    val today = LocalDate.now()
    var month by remember(day) { mutableStateOf(YearMonth.from(day)) }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val calOn = vm.settings.collectAsState().value.calendar
    val monthEvents by androidx.compose.runtime.produceState(events, month, calOn) {
        value = if (!calOn) emptyList() else kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val from = month.atDay(1).minusDays(month.atDay(1).dayOfWeek.value - 1L)
            ru.anjeib.margin.data.PhoneCalendar.events(ctx, from, 42)
        }
    }
    val plans = remember(data, month, today, monthEvents) {
        val from = month.atDay(1).minusDays(month.atDay(1).dayOfWeek.value - 1L)
        (0L until 42L).associate { val d = from.plusDays(it); d to dayPlan(data, d, today, monthEvents) }
    }
    val loc = if (L.lang == ru.anjeib.margin.i18n.Lang.Ru) java.util.Locale.forLanguageTag("ru") else java.util.Locale.ENGLISH
    Margined(Modifier.padding(top = 4.dp, bottom = 6.dp)) {
        Column {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(month.month.getDisplayName(TextStyle.FULL_STANDALONE, loc).replaceFirstChar { it.uppercase() } + if (month.year != today.year) " ${month.year}" else "",
                    style = Type.title.copy(color = c.ink, fontSize = 22.sp), modifier = Modifier.weight(1f))
                Box(Modifier.size(40.dp).clip(CircleShape).clickable { month = month.minusMonths(1) }, contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, s.prevMonth, tint = c.graphite)
                }
                Box(Modifier.size(40.dp).clip(CircleShape).clickable { month = month.plusMonths(1) }, contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, s.nextMonth, tint = c.graphite)
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 2.dp)) {
                (1..7).forEach { d ->
                    Text(java.time.DayOfWeek.of(d).getDisplayName(TextStyle.SHORT, loc).lowercase(), style = Type.italic.copy(color = c.graphite, fontSize = 13.sp),
                        modifier = Modifier.weight(1f).padding(bottom = 4.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
            val first = month.atDay(1)
            val lead = first.dayOfWeek.value - 1
            val cells = lead + month.lengthOfMonth()
            (0 until (cells + 6) / 7).forEach { w ->
                Row(Modifier.fillMaxWidth()) {
                    (0..6).forEach { i ->
                        val n = w * 7 + i - lead + 1
                        Box(Modifier.weight(1f).height(44.dp).padding(1.5.dp), contentAlignment = Alignment.Center) {
                            if (n in 1..month.lengthOfMonth()) {
                                val d = month.atDay(n)
                                val plan = plans[d] ?: dayPlan(data, d, today, monthEvents)
                                val past = d.isBefore(today)
                                val count = if (past) plan.done.size else plan.deals.size
                                val late = d == today && plan.deals.any { it.isOverdue(today) }
                                val sel = d == day
                                Column(Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)).background(if (sel) c.link else Color.Transparent)
                                    .then(if (d == today && !sel) Modifier.border(1.dp, c.link, RoundedCornerShape(10.dp)) else Modifier)
                                    .clickable { onPick(d) }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                    Text("$n", style = Type.body.copy(color = when { sel -> c.bg; past -> c.graphite; else -> c.ink }, fontSize = 15.sp,
                                        fontWeight = if (sel || d == today) FontWeight.SemiBold else FontWeight.Normal))
                                    Row(Modifier.height(7.dp).padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                        val dot = when { sel -> c.bg; past -> c.ok; late -> c.accent; else -> c.link }
                                        repeat(count.coerceAtMost(3)) { Box(Modifier.size(4.dp).clip(CircleShape).background(dot)) }
                                        if (plan.events.isNotEmpty()) Box(Modifier.size(4.dp).border(1.dp, if (sel) c.bg else c.graphite, CircleShape))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
