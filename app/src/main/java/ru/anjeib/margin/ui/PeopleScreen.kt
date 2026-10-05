package ru.anjeib.margin.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.anjeib.margin.data.AppData
import ru.anjeib.margin.data.Person
import ru.anjeib.margin.data.Task
import ru.anjeib.margin.data.text
import java.time.LocalDate
import ru.anjeib.margin.data.dayMonthShort

private fun Task.involves(p: Person) = p.involvedIn(this)

@Composable
fun PeopleList(data: AppData, onOpen: (String) -> Unit, contentPadding: PaddingValues) {
    val c = M.c
    val s = M.s
    val people = data.persons.sortedByDescending { p -> data.tasks.count { !it.done && it.involves(p) } }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item("h") { Text(s.peopleTitle, style = Type.display.copy(color = c.ink), modifier = Modifier.padding(top = 18.dp, bottom = 12.dp, start = 4.dp)) }
        if (people.isEmpty()) item("empty") { Empty(s.peopleTitle, s.emptyPeople) }
        items(people, key = { it.id }) { p ->
            val open = data.tasks.count { !it.done && it.involves(p) }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.card).clickable { onOpen(p.id) }
                    .padding(horizontal = 16.dp, vertical = 14.dp).animateItem(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Monogram(p.name, 40.dp)
                Spacer(Modifier.width(14.dp))
                // What's open with them right now beats a static description.
                val wait = data.tasks.firstOrNull { !it.done && it.waitingFor?.let { w -> p.matches(w) } == true }
                val promise = data.tasks.firstOrNull { !it.done && it.promisedTo?.let { w -> p.matches(w) } == true }
                Column(Modifier.weight(1f)) {
                    Text(p.name, style = Type.cardTitle.copy(color = c.ink))
                    Text(listOfNotNull(p.about, wait?.let { s.waitLine(it.title.replaceFirstChar { c -> c.lowercase() }) }, promise?.let { s.promiseLine(it.title.replaceFirstChar { c -> c.lowercase() }) })
                        .joinToString(" · ").ifBlank { listOfNotNull(p.phone, p.email).joinToString(" · ").ifBlank { "—" } },
                        style = Type.small.copy(color = c.graphite), maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                if (open > listOfNotNull(wait, promise).size) Text(s.tasksCount(open), style = Type.small.copy(color = c.graphite, fontSize = 12.5.sp))
            }
        }
    }
}

@Composable
fun PersonScreen(person: Person, data: AppData, vm: MainVm, onBack: () -> Unit, onOpenTask: (String) -> Unit, onOpenNote: (String) -> Unit) {
    val c = M.c
    val s = M.s
    val ctx = LocalContext.current
    val working by vm.working.collectAsState()
    var menu by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<String?>(null) }
    BackHandler(onBack = onBack)
    // A fresh summary when there's none for today and the person shows up in things.
    LaunchedEffect(person.id) {
        val fresh = person.summaryAt?.startsWith(LocalDate.now().toString()) == true
        if (!fresh && data.tasks.any { it.involves(person) }) vm.refreshPerson(person.id)
    }

    val tasks = data.tasks.filter { it.involves(person) }
    val waiting = tasks.filter { !it.done && it.waitingFor?.let { w -> person.matches(w) } == true }
    val promised = tasks.filter { !it.done && it.promisedTo?.let { w -> person.matches(w) } == true }
    val open = tasks.filter { !it.done } - waiting.toSet() - promised.toSet()
    val done = tasks.filter { it.done }.sortedByDescending { it.doneAt }.take(5)
    val notes = data.notes.filter { n -> listOf(person.name).plus(person.aliases).any { n.title.contains(it, true) || n.text.contains(it, true) } }

    Column(Modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, null, tint = c.ink)
            }
            Spacer(Modifier.weight(1f))
            Box {
                Box(Modifier.size(44.dp).clip(CircleShape).clickable { menu = true }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.MoreHoriz, null, tint = c.graphite)
                }
                DropdownMenu(menu, { menu = false }, containerColor = c.card) {
                    DropdownMenuItem(text = { Text(s.rename, style = Type.body) }, onClick = { menu = false; dialog = "rename" })
                    DropdownMenuItem(text = { Text(s.editContacts, style = Type.body) }, onClick = { menu = false; dialog = "phone" })
                    if (data.persons.size > 1) DropdownMenuItem(text = { Text(s.mergeWith, style = Type.body) }, onClick = { menu = false; dialog = "merge" })
                    DropdownMenuItem(text = { Text(s.delete, style = Type.body.copy(color = c.accent)) }, onClick = { menu = false; vm.deletePerson(person.id); onBack() })
                }
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 22.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Monogram(person.name, 64.dp)
                Spacer(Modifier.width(18.dp))
                Column(Modifier.weight(1f)) {
                    Text(person.name, style = Type.display.copy(color = c.ink, fontSize = 36.sp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val projects = data.tasks.filter { it.involves(person) }.mapNotNull { it.project }.distinct().take(2)
                        Text(listOfNotNull(person.about, projects.joinToString(", ").ifBlank { null }, person.phone).joinToString(" · "),
                            style = Type.small.copy(color = c.graphite, fontSize = 14.sp), modifier = Modifier.weight(1f).clickable { dialog = "about" },
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                        person.phone?.let { ph ->
                            Box(Modifier.size(40.dp).clip(CircleShape).clickable { runCatching { ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$ph"))) } },
                                contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Call, null, tint = c.graphite, modifier = Modifier.size(20.dp)) }
                        }
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            // «Свежая сводка»: what's going on with this person now.
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.accentSoft).padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spark(12.dp); Spacer(Modifier.width(6.dp))
                    SectionLabel(s.freshSummary + (person.summaryAt?.let { " · " + (dateOf(it)?.let { d -> if (d == LocalDate.now()) it.drop(11).take(5) else d.dayMonthShort() } ?: "") } ?: ""), Modifier.weight(1f))
                    val busy = "person:${person.id}" in working
                    Box(Modifier.size(36.dp).clip(CircleShape).clickable(enabled = !busy) { vm.refreshPerson(person.id) }, contentAlignment = Alignment.Center) {
                        if (busy) CircularProgressIndicator(color = c.accent, strokeWidth = 1.5.dp, modifier = Modifier.size(14.dp))
                        else Icon(Icons.Outlined.Refresh, s.refresh, tint = c.graphite, modifier = Modifier.size(17.dp))
                    }
                }
                Text(person.summary ?: person.about ?: s.aboutHint, style = Type.body.copy(color = if (person.summary != null) c.ink else c.graphite, fontSize = 15.5.sp),
                    modifier = Modifier.padding(end = 10.dp))
            }
            person.email?.let { em ->
                Spacer(Modifier.height(10.dp))
                ContactButton(Icons.Outlined.Email, em) { runCatching { ctx.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$em"))) } }
            }
            val today = LocalDate.now()
            if (waiting.isNotEmpty()) {
                SectionLabel(s.promisedToYou, Modifier.padding(start = 4.dp, top = 24.dp, bottom = 4.dp))
                waiting.forEach { t -> TaskRow(t, today, true, onToggle = { vm.toggleTask(t.id) }, onOpen = { onOpenTask(t.id) }) }
            }
            if (promised.isNotEmpty()) {
                SectionLabel(s.youPromised, Modifier.padding(start = 4.dp, top = 24.dp, bottom = 4.dp))
                promised.forEach { t -> TaskRow(t, today, true, onToggle = { vm.toggleTask(t.id) }, onOpen = { onOpenTask(t.id) }) }
            }
            if (open.isNotEmpty()) {
                SectionLabel(s.tasksCaps, Modifier.padding(start = 4.dp, top = 24.dp, bottom = 4.dp))
                open.forEach { t -> TaskRow(t, today, true, onToggle = { vm.toggleTask(t.id) }, onOpen = { onOpenTask(t.id) }) }
            }
            // A dated history, each line saying where it came from.
            val history = personHistory(person, data)
            if (history.isNotEmpty()) {
                SectionLabel(s.historyTitle, Modifier.padding(start = 4.dp, top = 24.dp, bottom = 6.dp))
                history.forEach { (d, text, src) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp, horizontal = 4.dp)) {
                        Text(d.dayMonthShort(),
                            style = Type.small.copy(color = c.graphite), modifier = Modifier.width(56.dp))
                        Text(text, style = Type.small.copy(color = c.ink, fontSize = 14.sp), modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("  ($src)", style = Type.small.copy(color = c.faint, fontSize = 12.sp), maxLines = 1)
                    }
                }
            }
            if (notes.isNotEmpty()) {
                SectionLabel(s.notes, Modifier.padding(start = 4.dp, top = 24.dp, bottom = 8.dp))
                notes.forEach { n -> NoteCard(n, data, onClick = { onOpenNote(n.id) }, modifier = Modifier.padding(bottom = 8.dp)) }
            }
            if (done.isNotEmpty()) {
                SectionLabel(s.done, Modifier.padding(start = 4.dp, top = 24.dp, bottom = 4.dp))
                done.forEach { t -> TaskRow(t, today, true, onToggle = { vm.toggleTask(t.id) }, onOpen = { onOpenTask(t.id) }) }
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    when (dialog) {
        "about" -> TextDialog(s.whoIs, person.about.orEmpty(), s.aboutHint, onDismiss = { dialog = null }) { v -> vm.editPerson(person.id, v, person.phone, person.email); dialog = null }
        "rename" -> TextDialog(s.rename, person.name, "", onDismiss = { dialog = null }) { v -> vm.renamePerson(person.id, v); dialog = null }
        "phone" -> TextDialog(s.editContacts, listOfNotNull(person.phone, person.email).joinToString(", "), s.contactsHint, onDismiss = { dialog = null }) { v ->
            val parts = v.split(',', ';').map { it.trim() }.filter { it.isNotEmpty() }
            vm.editPerson(person.id, person.about, parts.firstOrNull { '@' !in it }, parts.firstOrNull { '@' in it }); dialog = null
        }
        "merge" -> AlertDialog(
            onDismissRequest = { dialog = null },
            containerColor = c.card,
            title = { Text(s.mergeWith, style = Type.cardTitle.copy(color = c.ink)) },
            text = {
                Column {
                    data.persons.filter { it.id != person.id }.forEach { o ->
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { vm.mergePersons(person.id, o.id); dialog = null; onBack() }
                            .padding(vertical = 10.dp, horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Monogram(o.name, 26.dp); Spacer(Modifier.width(10.dp))
                            Text(o.name, style = Type.body.copy(color = c.ink))
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton({ dialog = null }) { Text(s.cancel, color = c.graphite) } },
        )
    }
}

@Composable
private fun ContactButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    val c = M.c
    Row(Modifier.clip(RoundedCornerShape(50)).background(c.chip).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = c.graphite, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, style = Type.small.copy(color = c.ink, fontSize = 14.sp))
    }
}
