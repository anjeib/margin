package ru.anjeib.margin.ui

import ru.anjeib.margin.data.dayMonthShort

import androidx.compose.foundation.layout.PaddingValues

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.anjeib.margin.data.AppData
import ru.anjeib.margin.data.fullText
import ru.anjeib.margin.data.Subtask
import ru.anjeib.margin.data.text
import java.time.LocalDate

/** Plain, instant search across tasks (with subtasks and records), notes and people. */
@Composable
fun SearchScreen(data: AppData, vm: MainVm, onBack: () -> Unit, onTask: (String) -> Unit, onNote: (String) -> Unit, onPerson: (String) -> Unit) {
    val c = M.c
    val s = M.s
    var q by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    BackHandler(onBack = onBack)
    val words = q.lowercase().split(' ').filter { it.isNotBlank() }
    fun hit(hay: String) = words.isNotEmpty() && words.all { it in hay.lowercase() }
    fun subs(l: List<Subtask>): String = l.joinToString(" ") { it.title + " " + (it.assignee ?: "") + " " + subs(it.children) }
    val tasks = data.tasks.filter { t -> hit(listOf(t.title, t.project.orEmpty(), t.people.joinToString(" "), t.waitingFor.orEmpty(), subs(t.subtasks), t.entries.joinToString(" ") { it.fullText }).joinToString(" ")) }
        .sortedBy { it.done }
    val notes = data.notes.filter { !it.archived }.filter { hit(it.title + " " + it.text + " " + it.project.orEmpty() + " " + it.aiSummary.orEmpty()) }
    val people = data.persons.filter { it.isContact }.filter { hit(it.name + " " + it.aliases.joinToString(" ") + " " + it.about.orEmpty() + " " + it.phone.orEmpty()) }

    Column(Modifier.fillMaxSize().background(c.bg).statusBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, s.cancel, tint = c.ink)
            }
            Box(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(c.chip).padding(horizontal = 16.dp, vertical = 12.dp)) {
                if (q.isEmpty()) Text(s.searchHint, style = Type.body.copy(color = c.graphite))
                BasicTextField(q, { q = it }, singleLine = true, textStyle = Type.body.copy(color = c.ink), cursorBrush = SolidColor(c.link),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus))
            }
            Spacer(Modifier.width(8.dp))
        }
        // «Поля» under the search line: matters with their date on the margin, topics with their progress, people with their face.
        val topics = tasks.filter { it.topic }
        val deals = tasks.filterNot { it.topic }
        LazyColumn(Modifier.fillMaxSize().screenRule(), contentPadding = PaddingValues(bottom = 24.dp)) {
            if (words.isNotEmpty() && tasks.isEmpty() && notes.isEmpty() && people.isEmpty()) item { Margined { Empty(s.nothing, "") } }
            if (deals.isNotEmpty()) {
                item { Margined(Modifier.padding(top = 16.dp, bottom = 4.dp)) { GroupBadge(s.dealsBadge) } }
                items(deals, key = { "t" + it.id }) { t ->
                    Margined(margin = { (t.dueDate ?: t.startDate)?.let { MarginText(it.dayMonthShort(), faint = t.done) } }) {
                        TaskRow(t, LocalDate.now(), true, onToggle = { vm.completeWithUndo(t.id) }, onOpen = { onTask(t.id) })
                    }
                }
            }
            if (topics.isNotEmpty()) {
                item { Margined(Modifier.padding(top = 16.dp, bottom = 4.dp)) { GroupBadge(s.topicsBadge) } }
                items(topics, key = { "t" + it.id }) { t -> Margined { TopicRow(t, onOpen = { onTask(t.id) }) } }
            }
            if (notes.isNotEmpty()) {
                item { Margined(Modifier.padding(top = 20.dp, bottom = 8.dp)) { GroupBadge(s.notes) } }
                items(notes, key = { "n" + it.id }) { n -> Margined { NoteCard(n, data, onClick = { onNote(n.id) }, modifier = Modifier.padding(bottom = 8.dp)) } }
            }
            if (people.isNotEmpty()) {
                item { Margined(Modifier.padding(top = 20.dp, bottom = 8.dp)) { GroupBadge(s.peopleTitle) } }
                items(people, key = { "p" + it.id }) { p ->
                    Margined(Modifier.clickable { onPerson(p.id) }.padding(vertical = 10.dp), margin = { Monogram(p.name, 30.dp) }) {
                        Column {
                            Text(p.name, style = Type.body.copy(color = c.ink))
                            p.about?.let { Text(it, style = Type.small.copy(color = c.graphite), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        }
                    }
                }
            }
        }
    }
}
