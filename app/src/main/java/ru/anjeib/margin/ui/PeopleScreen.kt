package ru.anjeib.margin.ui

import androidx.compose.foundation.layout.offset

import androidx.compose.foundation.layout.wrapContentWidth

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import ru.anjeib.margin.data.isOverdue
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.combinedClickable
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
import ru.anjeib.margin.data.withNames
import java.time.LocalDate
import ru.anjeib.margin.data.dayMonthShort

private fun Task.involves(p: Person) = p.involvedIn(this)

@Composable
fun PeopleList(data: AppData, vm: MainVm, onOpen: (String) -> Unit, contentPadding: PaddingValues) {
    val c = M.c
    val s = M.s
    var tab by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("people") }
    val people = data.persons.filter { it.isContact }.sortedByDescending { p -> data.tasks.count { !it.done && it.involves(p) } }
    Box(Modifier.fillMaxSize().screenRule()) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding.vertical()) {
        item("h") { PageHead(s.peopleTitle, s.peopleSub) }
        if (people.isEmpty()) item("empty") { Margined { Empty(s.peopleTitle, s.emptyPeople) } }
        // People and companies on two tabs, as «Темы · Дела · Идеи» in «Обзор».
        val orgs = people.filter { it.org }
        val humans = people.filterNot { it.org }
        if (people.isNotEmpty()) item("tabs") { Margined(Modifier.padding(bottom = 6.dp)) { SubTabs(listOf("people" to s.peopleBadge, "orgs" to s.orgsBadge), tab, { tab = it }) } }
        listOf(if (tab == "orgs") orgs else humans).forEach { group ->
        items(group, key = { it.id }) { p ->
            val open = data.tasks.count { !it.done && it.involves(p) }
            SwipeActions(left = s.toArchive to { vm.archivePerson(p.id) }, right = s.delete to { vm.removePerson(p.id) }, modifier = Modifier.animateItem()) {
            Column(Modifier.fillMaxWidth().background(c.bg).clickable { onOpen(p.id) }) {
            // «Поля»: the face on the margin, who they are and what's open with them right of the rule.
            Margined(Modifier.padding(top = 12.dp, bottom = 12.dp), margin = { Monogram(p.name, 34.dp) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // What's open with them right now beats a static description.
                val wait = data.tasks.firstOrNull { !it.done && it.waitingFor?.let { w -> p.matches(w) } == true }
                val promise = data.tasks.firstOrNull { !it.done && it.promisedTo?.let { w -> p.matches(w) } == true }
                Column(Modifier.weight(1f)) {
                    Text(p.name, style = Type.cardTitle.copy(color = c.ink))
                    // Nothing known yet → no line at all, not a dash.
                    // Mid-sentence the title starts small, but a name stays a name: «ждём: Маша вернёт 3 000 ₽».
                    fun mid(t: Task) = t.title.replaceFirstChar { c -> c.lowercase() }
                        .withNames(listOfNotNull(p.name, t.waitingFor, t.promisedTo) + p.aliases + t.people)
                    val line = listOfNotNull(p.about, p.company, wait?.let { s.waitLine(mid(it)) }, promise?.let { s.promiseLine(mid(it)) })
                        .joinToString(" · ").ifBlank { listOfNotNull(p.phone, p.email).joinToString(" · ") }
                    if (line.isNotBlank()) Text(line, style = Type.small.copy(color = c.graphite), maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                if (open > listOfNotNull(wait, promise).size) Text(s.tasksCount(open), style = Type.small.copy(color = c.graphite, fontSize = 12.5.sp, fontFeatureSettings = "tnum"))
            }
            }
            Hairline(Modifier.padding(start = MarginW + MarginGap))
            }
            }
        }
        }
    }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
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
    val tasks = data.tasks.filter { it.involves(person) }
    // One list: the hourglass and «вы обещали» on the row already say which way it goes.
    val open = tasks.filter { !it.done }.sortedWith(compareBy({ !it.isOverdue() }, { it.dueDate ?: LocalDate.MAX }))
    val done = tasks.filter { it.done }.sortedByDescending { it.doneAt }.take(5)
    val notes = data.notes.filter { n -> listOf(person.name).plus(person.aliases).any { n.title.contains(it, true) || n.text.contains(it, true) } }

    Column(Modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding().screenRule()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, null, tint = c.ink)
            }
            Spacer(Modifier.weight(1f))
            Box {
                Box(Modifier.size(44.dp).clip(CircleShape).clickable { menu = true }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.MoreHoriz, null, tint = c.graphite)
                }
                SoftMenu(menu, { menu = false }) {
                    SoftMenuItem(s.rename, onClick = { menu = false; dialog = "rename" })
                    if (data.persons.size > 1) SoftMenuItem(s.mergeWith, onClick = { menu = false; dialog = "merge" })
                    SoftMenuItem(if (person.org) s.markPerson else s.markOrg, onClick = { menu = false; vm.setOrg(person.id, !person.org) })
                    SoftMenuItem(s.delete, danger = true, onClick = { menu = false; vm.deletePerson(person.id); onBack() })
                }
            }
        }
        // «Поля»: the face on the margin, everything else right of the rule.
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = MarginW + MarginGap, end = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(0.dp)) {
                    Box(Modifier.wrapContentWidth(Alignment.Start, unbounded = true).offset(x = -(MarginW + MarginGap) + 4.dp)) { Monogram(person.name, 46.dp) }
                }
                Column(Modifier.weight(1f)) {
                    Text(person.name, style = Type.display.copy(color = c.ink, fontSize = 36.sp))
                    // Who they are: role · company · the projects they show up in.
                    val projects = tasks.mapNotNull { it.project }.distinct().take(2).map { projectLabel(it) }
                    listOfNotNull(person.about, person.company, projects.joinToString(", ").ifBlank { null }).joinToString(" · ").takeIf { it.isNotBlank() }?.let {
                        Text(it, style = Type.small.copy(color = c.graphite, fontSize = 14.sp), maxLines = 2, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { dialog = if (person.about == null) "company" else "about" })
                    }
                }
            }
            // Ways to reach them: a tap calls / writes / opens, a long press changes it.
            fun reach(kind: String) {
                val i = when (kind) {
                    "phone" -> Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + person.phone))
                    "email" -> Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + person.email))
                    "telegram" -> person.telegram?.removePrefix("@")?.let { h -> Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/" + (if (h.all { it.isDigit() || it == '+' }) "+" + h.trimStart('+') else h))) }
                    "address" -> Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(person.address)))
                    else -> null
                }
                if (i != null) runCatching { ctx.startActivity(i) }
                else (ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager)?.let { cb ->
                    // MAX has no public link format yet: the handle goes to the clipboard.
                    cb.setPrimaryClip(android.content.ClipData.newPlainText("MAX", person.max)); vm.say(s.copied)
                }
            }
            val contacts = listOfNotNull(
                person.phone?.let { Triple(Icons.Outlined.Call, it, "phone") },
                person.email?.let { Triple(Icons.Outlined.Email, it, "email") },
                person.telegram?.let { Triple(Icons.AutoMirrored.Outlined.Send, it, "telegram") },
                person.max?.let { Triple(Icons.Outlined.ChatBubbleOutline, "MAX · $it", "max") },
                person.address?.let { Triple(Icons.Outlined.Place, it, "address") },
            )
            if (contacts.isNotEmpty()) FlowRow(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                contacts.forEach { (icon, label, kind) -> ContactChip(icon, label, onClick = { reach(kind) }, onLong = { dialog = kind }) }
            }
            val missing = listOfNotNull(
                (s.fieldRole to "about").takeIf { person.about == null }, (s.fieldCompany to "company").takeIf { person.company == null },
                (s.fieldPhone to "phone").takeIf { person.phone == null }, (s.fieldEmail to "email").takeIf { person.email == null },
                (s.fieldAddress to "address").takeIf { person.address == null }, ("MAX" to "max").takeIf { person.max == null },
                ("Telegram" to "telegram").takeIf { person.telegram == null },
            )
            if (missing.isNotEmpty()) FlowRow(Modifier.padding(top = 10.dp)) {
                missing.forEachIndexed { i, (label, d) ->
                    if (i > 0) Text("  ·  ", style = Type.small.copy(color = c.faint))
                    Text("+ " + (if (label.first().isUpperCase() && label.length <= 8 && label.any { it in 'A'..'Z' }) label else label.lowercase()),
                        style = Type.small.copy(color = c.graphite), modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { dialog = d }.padding(2.dp))
                }
            }
            // «Личная информация»: what the user told about them as a person — birthday, family, what they like.
            Column(Modifier.padding(top = 22.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { dialog = "personal" }.padding(vertical = 4.dp)) {
                GroupBadge(s.personalTitle, Modifier.padding(bottom = 8.dp))
                if (person.personal.isEmpty()) Text("+ " + s.personalHint, style = Type.small.copy(color = c.graphite), modifier = Modifier.padding(start = 4.dp))
                else person.personal.forEach { Text(it, style = Type.body.copy(color = c.ink, fontSize = 15.sp), modifier = Modifier.padding(start = 4.dp, top = 2.dp, bottom = 2.dp)) }
            }
            Spacer(Modifier.height(18.dp))
            val today = LocalDate.now()
            if (open.isNotEmpty()) {
                GroupBadge(s.dealsBadge, Modifier.padding(top = 24.dp, bottom = 4.dp))
                open.forEach { t -> TaskRow(t, today, true, onToggle = { vm.completeWithUndo(t.id) }, onOpen = { onOpenTask(t.id) }) }
            }
            // A dated history, each line saying where it came from.
            val history = personHistory(person, data)
            if (history.isNotEmpty()) {
                GroupBadge(s.historyTitle, Modifier.padding(top = 24.dp, bottom = 6.dp))
                history.forEach { (d, text, src) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp, horizontal = 4.dp)) {
                        Text(d.dayMonthShort(), style = dateStyle(13.sp), modifier = Modifier.width(56.dp))
                        Text(text, style = Type.small.copy(color = c.ink, fontSize = 14.sp), modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("  ($src)", style = Type.small.copy(color = c.faint, fontSize = 12.sp), maxLines = 1)
                    }
                }
            }
            if (notes.isNotEmpty()) {
                GroupBadge(s.notes, Modifier.padding(top = 24.dp, bottom = 8.dp))
                // Say why a note is here when the name isn't in its words — only their contact card is.
                notes.forEach { n ->
                    val byCard = n.entries.any { e -> e.attachments.any { a -> a.contact?.name?.let { person.matches(it) || it.equals(person.name, true) } == true } } &&
                        listOf(person.name).plus(person.aliases).none { n.title.contains(it, true) || n.entries.any { e -> e.text.contains(it, true) } }
                    NoteCard(n, data, onClick = { onOpenNote(n.id) }, modifier = Modifier.padding(bottom = 8.dp), why = if (byCard) s.contactInNote else null)
                }
            }
            if (done.isNotEmpty()) {
                GroupBadge(s.done, Modifier.padding(top = 24.dp, bottom = 4.dp))
                done.forEach { t -> TaskRow(t, today, true, onToggle = { vm.completeWithUndo(t.id) }, onOpen = { onOpenTask(t.id) }) }
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    when (dialog) {
        "about", "company", "phone", "email", "address", "max", "telegram" -> {
            val k = dialog!!
            val (title, value) = when (k) {
                "about" -> s.fieldRole to person.about; "company" -> s.fieldCompany to person.company; "phone" -> s.fieldPhone to person.phone
                "email" -> s.fieldEmail to person.email; "address" -> s.fieldAddress to person.address; "max" -> "MAX" to person.max
                else -> "Telegram" to person.telegram
            }
            TextDialog(title, value.orEmpty(), if (k == "telegram") "@username" else "", onDismiss = { dialog = null }) { v -> vm.setPersonField(person.id, k, v); dialog = null }
        }
        "personal" -> TextDialog(s.personalTitle.lowercase().replaceFirstChar { it.uppercase() }, person.personal.joinToString("\n"), s.personalHint, onDismiss = { dialog = null }) { v ->
            vm.setPersonField(person.id, "personal", v); dialog = null
        }
        "rename" -> TextDialog(s.rename, person.name, "", onDismiss = { dialog = null }) { v -> vm.renamePerson(person.id, v); dialog = null }
        "merge" -> AlertDialog(
            onDismissRequest = { dialog = null },
            containerColor = c.card,
            title = { Text(s.mergeWith, style = Type.cardTitle.copy(color = c.ink)) },
            text = {
                Column {
                    data.persons.filter { it.id != person.id && it.isContact }.forEach { o ->
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

/** A way to reach the person: tap to use it, hold to change it. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ContactChip(icon: ImageVector, label: String, onClick: () -> Unit, onLong: () -> Unit) {
    val c = M.c
    Row(Modifier.clip(RoundedCornerShape(50)).background(c.chip).combinedClickable(onClick = onClick, onLongClick = onLong).padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = c.graphite, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, style = Type.small.copy(color = c.ink, fontSize = 14.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
