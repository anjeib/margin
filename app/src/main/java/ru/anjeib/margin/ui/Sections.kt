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

/** The sections, always one tap away under the top bar: Задачи · Заметки · Люди · Проекты. */
@Composable
fun SectionRow(selected: Int, onSelect: (Int) -> Unit) {
    val c = M.c
    val s = M.s
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        // v2: notes became thoughts among the topics, projects became labels.
        listOf(s.casesTitle, s.materialsTitle, s.peopleTitle).forEachIndexed { i, l ->
            val on = i == selected
            Text(l, style = Type.small.copy(color = if (on) c.ink else c.graphite, fontSize = 14.sp),
                modifier = Modifier.clip(RoundedCornerShape(50)).background(if (on) c.chip else Color.Transparent)
                    .clickable { onSelect(i) }.padding(horizontal = 12.dp, vertical = 6.dp))
        }
    }
}

/** «Проекты»: every project with what is open in it; a tap opens its page. */
@Composable
fun ProjectsScreen(data: AppData, onOpen: (String) -> Unit, contentPadding: PaddingValues) {
    val c = M.c
    val s = M.s
    val projects = (data.projects + data.tasks.mapNotNull { it.project } + data.notes.filter { !it.archived }.mapNotNull { it.project }).distinct()
        .filter { p -> data.tasks.any { it.project == p } || data.notes.any { !it.archived && it.project == p } }
        .sortedByDescending { p -> data.tasks.count { !it.done && it.project == p } }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item("h") { Text(s.projects.lowercase().replaceFirstChar { it.uppercase() }, style = Type.display.copy(color = c.ink), modifier = Modifier.padding(top = 18.dp, bottom = 12.dp, start = 4.dp)) }
        if (projects.isEmpty()) item("empty") { Empty(s.projectsEmpty, s.projectsHint) }
        items(projects, key = { it }) { p ->
            val open = data.tasks.filter { !it.done && it.project == p }
            val notes = data.notes.count { !it.archived && it.project == p }
            val late = open.count { it.isOverdue() }
            val next = open.mapNotNull { it.dueDate }.filter { !it.isBefore(LocalDate.now()) }.minOrNull()
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.card).clickable { onOpen(p) }.padding(horizontal = 18.dp, vertical = 16.dp)) {
                Text(projectLabel(p), style = Type.cardTitle.copy(color = c.ink))
                Spacer(Modifier.height(3.dp))
                Text(listOfNotNull(s.projectStats(open.size, notes), late.takeIf { it > 0 }?.let { s.overdue.lowercase() + " " + it },
                    next?.let { s.nextDue(it.shortLabel().lowercase()) }).joinToString(" · "),
                    style = Type.small.copy(color = if (late > 0) c.accentText else c.graphite, fontSize = 13.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** «Календарь»: a month with how many open tasks fall on each day; a tap lists that day below. */
@Composable
fun CalendarScreen(data: AppData, vm: MainVm, onOpenTask: (String) -> Unit, contentPadding: PaddingValues, events: List<ru.anjeib.margin.data.CalEvent> = emptyList()) {
    val c = M.c
    val s = M.s
    val today = LocalDate.now()
    var month by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    var picked by rememberSaveable { mutableStateOf(today.toString()) }
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(when { count == 0 -> " "; past -> "✓$count"; else -> "$count" },
                    style = Type.small.copy(color = when { sel -> c.bg; past -> c.graphite; late -> c.accentText; else -> c.link }, fontSize = 11.sp, fontFeatureSettings = "tnum"))
                if (meets) Box(Modifier.padding(start = 3.dp).size(4.dp).clip(CircleShape).background(if (sel) c.bg else c.graphite))
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
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, null, tint = c.graphite)
                }
                Box(Modifier.size(44.dp).clip(CircleShape).clickable { month = ym.plusMonths(1).toString() }, contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = c.graphite)
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
        // The picked day, the same block as on «Сегодня» and «Неделя»: its date on the margin, «Дела» and «Темы».
        item("day") {
            DayBlock(plans[day] ?: dayPlan(data, day, today, monthEvents), today, open = true, heading = false, onToggle = {},
                onOpen = onOpenTask, onDone = { vm.completeWithUndo(it) }, onLater = { postponing = it }, onTime = { timeFor = it }, landed = emptySet())
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
            Margined { Text(s.archiveEmpty, style = Type.body.copy(color = c.graphite), modifier = Modifier.padding(top = 8.dp)) }
        }
        // v2: settled topics with their materials; news in one brings it back. The day it was settled on the margin.
        if (topics.isNotEmpty()) item("th") { Margined(Modifier.padding(top = 10.dp, bottom = 6.dp)) { GroupBadge(s.topicsBadge) } }
        items(topics, key = { "t" + it.id }) { t ->
            Column(Modifier.animateItem()) {
                Margined(Modifier.padding(vertical = 4.dp), margin = { t.doneAt?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() }?.let { DayMargin(it, LocalDate.now(), false, strong = true) } }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) { TopicRow(t, onOpen = { onOpenTask(t.id) }) }
                        Spacer(Modifier.width(8.dp))
                        PaperLink(s.bringBack) { vm.reviveTopic(t.id) }
                    }
                }
                Hairline(Modifier.padding(start = MarginW + MarginGap))
            }
        }
        if (notes.isNotEmpty()) item("nh") { Margined(Modifier.padding(top = 18.dp, bottom = 6.dp)) { GroupBadge(s.notes) } }
        items(notes, key = { "n" + it.id }) { n ->
            Margined(Modifier.animateItem().padding(vertical = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { NoteCard(n, data, onClick = { onOpenNote(n.id) }) }
                    Spacer(Modifier.width(8.dp))
                    PaperLink(s.restoreWord) { vm.archiveNote(n.id, false) }
                }
            }
        }
        if (people.isNotEmpty()) item("ph") { Margined(Modifier.padding(top = 18.dp, bottom = 6.dp)) { GroupBadge(s.peopleTitle) } }
        items(people, key = { "p" + it.id }) { p ->
            Margined(Modifier.animateItem().padding(vertical = 10.dp).clickable { onOpenPerson(p.id) }, margin = { Monogram(p.name, 30.dp) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(p.name, style = Type.cardTitle.copy(color = c.ink), modifier = Modifier.weight(1f))
                    PaperLink(s.restoreWord) { vm.archivePerson(p.id, false) }
                }
            }
        }
    }
    }
}
