package ru.anjeib.margin.ui

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
        listOf(s.tasks, s.notes, s.peopleTitle, s.projects.lowercase().replaceFirstChar { it.uppercase() }).forEachIndexed { i, l ->
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
                    style = Type.small.copy(color = if (late > 0) c.accent else c.graphite, fontSize = 13.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** «Календарь»: a month with how many open tasks fall on each day; a tap lists that day below. */
@Composable
fun CalendarScreen(data: AppData, vm: MainVm, onOpenTask: (String) -> Unit, contentPadding: PaddingValues) {
    val c = M.c
    val s = M.s
    val today = LocalDate.now()
    var month by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    var picked by rememberSaveable { mutableStateOf(today.toString()) }
    val ym = YearMonth.parse(month)
    val day = LocalDate.parse(picked)
    val open = data.tasks.filter { !it.done }
    fun on(t: Task, d: LocalDate): Boolean {
        val due = t.dueDate; val st = t.startDate
        // A task counts on its deadline (or its start when it has none) — a week-long window doesn't fill seven days.
        return if (due != null) d == due else st == d
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item("h") {
            Row(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 8.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
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
        item("grid") {
            Column {
                Row(Modifier.fillMaxWidth()) {
                    (1..7).forEach { d ->
                        Text(java.time.DayOfWeek.of(d).getDisplayName(TextStyle.SHORT, if (L.lang == ru.anjeib.margin.i18n.Lang.Ru) java.util.Locale.forLanguageTag("ru") else java.util.Locale.ENGLISH).lowercase(),
                            style = Type.small.copy(color = c.faint, fontSize = 12.sp), modifier = Modifier.weight(1f).padding(bottom = 6.dp),
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
                                if (n in 1..ym.lengthOfMonth()) {
                                    val d = ym.atDay(n)
                                    val count = open.count { on(it, d) }
                                    val sel = d == day
                                    Column(Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp))
                                        .background(if (sel) c.link else Color.Transparent)
                                        .then(if (d == today && !sel) Modifier.border(1.dp, c.link, RoundedCornerShape(12.dp)) else Modifier)
                                        .clickable { picked = d.toString() }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                        Text("$n", style = Type.body.copy(color = when { sel -> c.bg; d.isBefore(today) -> c.faint; else -> c.ink }, fontSize = 15.sp))
                                        // How busy the day is — a number, quiet, never a colour per project.
                                        Text(if (count > 0) "$count" else " ", style = Type.small.copy(color = if (sel) c.bg else if (d.isBefore(today)) c.faint else c.link, fontSize = 11.sp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        item("day") {
            Text(day.shortLabel(today).replaceFirstChar { it.uppercase() }, style = Type.cardTitle.copy(color = c.ink), modifier = Modifier.padding(top = 18.dp, bottom = 4.dp, start = 4.dp))
        }
        val list = open.filter { on(it, day) } + data.tasks.filter { it.done && it.doneAt == day.toString() }
        if (list.isEmpty()) item("none") { Text(s.freeDay, style = Type.body.copy(color = c.graphite), modifier = Modifier.padding(start = 4.dp)) }
        items(list, key = { "c" + it.id }) { t -> TaskRow(t, today, true, onToggle = { vm.completeWithUndo(t.id) }, onOpen = { onOpenTask(t.id) }) }
    }
}

/** «Архив»: notes and people put away by a swipe — each comes back with «Вернуть». */
@Composable
fun ArchiveScreen(data: AppData, vm: MainVm, onOpenNote: (String) -> Unit, onOpenPerson: (String) -> Unit, contentPadding: PaddingValues) {
    val c = M.c
    val s = M.s
    val notes = data.notes.filter { it.archived }
    val people = data.persons.filter { it.archived }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item("h") { Text(s.archiveTitle, style = Type.display.copy(color = c.ink), modifier = Modifier.padding(top = 18.dp, bottom = 12.dp, start = 4.dp)) }
        if (notes.isEmpty() && people.isEmpty()) item("empty") { Empty(s.archiveTitle, s.archiveEmpty) }
        if (notes.isNotEmpty()) item("nh") { SectionLabel(s.notes.uppercase(), Modifier.padding(start = 6.dp, top = 6.dp)) }
        items(notes, key = { "n" + it.id }) { n ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { NoteCard(n, data, onClick = { onOpenNote(n.id) }) }
                Spacer(Modifier.width(8.dp))
                Pill(s.restoreWord, onClick = { vm.archiveNote(n.id, false) })
            }
        }
        if (people.isNotEmpty()) item("ph") { SectionLabel(s.peopleTitle.uppercase(), Modifier.padding(start = 6.dp, top = 12.dp)) }
        items(people, key = { "p" + it.id }) { p ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.card).clickable { onOpenPerson(p.id) }.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Monogram(p.name, 36.dp)
                Spacer(Modifier.width(12.dp))
                Text(p.name, style = Type.cardTitle.copy(color = c.ink), modifier = Modifier.weight(1f))
                Pill(s.restoreWord, onClick = { vm.archivePerson(p.id, false) })
            }
        }
    }
}
