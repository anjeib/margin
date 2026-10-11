package ru.anjeib.margin.ui

import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.rounded.Close
import ru.anjeib.margin.data.dayMonthShort

import androidx.compose.foundation.layout.PaddingValues

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.outlined.History
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
import ru.anjeib.margin.data.materials
import androidx.compose.ui.unit.sp
import java.time.LocalDate

/**
 * Plain, instant search across matters (with their sub-matters and records), topics, files, notes and people.
 * With nothing typed: the last searches and three kinds to browse — people, topics, files.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(data: AppData, vm: MainVm, onBack: () -> Unit, onTask: (String) -> Unit, onNote: (String) -> Unit, onPerson: (String) -> Unit) {
    val c = M.c
    val s = M.s
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var q by rememberSaveable { mutableStateOf("") }
    /** null — everything; "people" | "topics" | "files" — one kind, all of it when nothing is typed. */
    var kind by rememberSaveable { mutableStateOf<String?>(null) }
    var viewing by remember { mutableStateOf<ru.anjeib.margin.data.Attachment?>(null) }
    val prefs = remember { ctx.getSharedPreferences("ui", android.content.Context.MODE_PRIVATE) }
    // One list for the whole run: a search left a moment ago is there when the screen opens again.
    val recentState = RecentSearches.state
    if (recentState.value == null) recentState.value = prefs.getString("searchRecent", "").orEmpty().split('\n').filter { it.isNotBlank() }
    val recent = recentState.value.orEmpty()
    // A search is remembered when something it found is opened, and when the user leaves it with words typed
    // (Fable, 9 окт: looking without opening left «Недавние» empty).
    fun keep() {
        val t = q.trim().takeIf { it.length >= 2 } ?: return
        val now = (listOf(t) + recentState.value.orEmpty().filterNot { it.equals(t, true) }).take(5)
        recentState.value = now
        prefs.edit().putString("searchRecent", now.joinToString("\n")).apply()
    }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { keep() } }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    BackHandler { if (viewing != null) viewing = null else onBack() }
    // «ё» is «е», and a longer word is matched by its stem: «Маше» finds «Маша», «макеты» — «макет».
    fun norm(x: String) = x.lowercase().replace('ё', 'е')
    val words = norm(q).split(' ').filter { it.isNotBlank() }.map { if (it.length >= 5) it.dropLast(if (it.length >= 7) 2 else 1) else it }
    val browsing = words.isEmpty() && kind != null
    fun hit(hay: String) = browsing || (words.isNotEmpty() && norm(hay).let { h -> words.all { it in h } })
    fun wants(k: String) = kind == null || kind == k
    fun subs(l: List<Subtask>): String = l.joinToString(" ") { it.title + " " + (it.assignee ?: "") + " " + subs(it.children) }
    fun flat(l: List<Subtask>): List<Subtask> = l.flatMap { listOf(it) + flat(it.children) }
    val tasks = if (kind != null && kind != "topics") emptyList() else data.tasks
        .filter { t -> if (browsing) t.topic && !t.done else hit(listOf(t.title, t.project.orEmpty(), t.people.joinToString(" "), t.waitingFor.orEmpty(), subs(t.subtasks), t.entries.joinToString(" ") { it.fullText }).joinToString(" ")) }
        .filter { kind == null || it.topic }
        .sortedBy { it.done }
    val notes = if (kind != null) emptyList() else data.notes.filter { !it.archived }.filter { hit(it.title + " " + it.text + " " + it.project.orEmpty() + " " + it.aiSummary.orEmpty()) }
    val people = if (!wants("people")) emptyList() else data.persons.filter { it.isContact }.filter { hit(it.name + " " + it.aliases.joinToString(" ") + " " + it.about.orEmpty() + " " + it.phone.orEmpty()) }
    val files = if (!wants("files")) emptyList() else data.tasks.flatMap { t -> t.materials().map { (e, a) -> Triple(t, e, a) } }
        .filter { (_, _, a) -> hit(listOfNotNull(a.name, a.caption, a.ocr, a.transcript, a.contact?.name).joinToString(" ")) }
        .sortedByDescending { it.second.at }

    Column(Modifier.fillMaxSize().background(c.bg).statusBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, s.cancel, tint = c.ink)
            }
            Box(Modifier.weight(1f).clip(RoundedCornerShape(24.dp)).background(c.card).border(1.dp, c.hairline, RoundedCornerShape(24.dp)).padding(horizontal = 18.dp, vertical = 13.dp)) {
                if (q.isEmpty()) Text(s.searchHint, style = Type.body.copy(color = c.graphite))
                BasicTextField(q, { q = it }, singleLine = true, textStyle = Type.body.copy(color = c.ink), cursorBrush = SolidColor(c.link),
                    modifier = Modifier.fillMaxWidth().padding(end = if (q.isNotEmpty()) 28.dp else 0.dp).focusRequester(focus))
                // Cleared in one tap.
                if (q.isNotEmpty()) Box(Modifier.align(Alignment.CenterEnd).offset(x = 10.dp).size(40.dp).clip(CircleShape).clickable { q = ""; runCatching { focus.requestFocus() } },
                    contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Close, s.clearWord, tint = c.graphite, modifier = Modifier.size(18.dp)) }
            }
            Spacer(Modifier.width(8.dp))
        }
        // Three kinds to narrow the search to — or, with nothing typed, to look through.
        androidx.compose.foundation.layout.FlowRow(Modifier.fillMaxWidth().padding(start = MarginW + MarginGap - PanelBleed, end = 16.dp, top = 4.dp, bottom = 4.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
            listOf("people", "topics", "files").zip(s.searchTry).forEach { (k, label) -> Chip(label.replaceFirstChar { it.uppercase() }, kind == k, onClick = { kind = if (kind == k) null else k }) }
        }
        // «Поля и панели» under the search line: each kind its own panel, «когда» on the margin.
        val topics = tasks.filter { it.topic }
        val deals = tasks.filterNot { it.topic }
        val today = LocalDate.now()
        LazyColumn(Modifier.fillMaxSize().screenRule(), contentPadding = PaddingValues(bottom = 24.dp)) {
            if (words.isEmpty() && kind == null && recent.isNotEmpty()) {
                item("rh") { SectionHead(s.searchRecent, top = 16.dp) }
                recent.forEachIndexed { i, r ->
                    item("r$r") { PaperRow(seatOf(i, recent.size), mark = { Icon(Icons.Outlined.History, null, tint = c.graphite, modifier = Modifier.size(19.dp)) }, onClick = { q = r }) { RowTitle(r, maxLines = 1) } }
                }
            }
            if ((words.isNotEmpty() || browsing) && tasks.isEmpty() && notes.isEmpty() && people.isEmpty() && files.isEmpty()) item("none") { Margined(Modifier.padding(top = 20.dp)) { Voice(s.nothing) } }
            if (deals.isNotEmpty()) {
                item("dh") { SectionHead(s.dealsBadge, count = deals.size, top = 16.dp) }
                deals.forEachIndexed { i, t ->
                    item("t" + t.id) {
                        MatterRow(t, seatOf(i, deals.size), today, whenNote(t, today, underDay = false),
                            onToggle = { vm.completeWithUndo(t.id) }, onOpen = { keep(); onTask(t.id) }, onTime = { keep(); onTask(t.id) }, onLater = null, swipe = false,
                            onOpenTopic = { keep(); onTask(it) })
                    }
                }
            }
            if (topics.isNotEmpty()) {
                item("th") { SectionHead(s.topicsBadge, count = topics.size) }
                topics.forEachIndexed { i, t ->
                    item("t" + t.id) {
                        // Found in one of its matters: that matter, which opens lit in the topic's feed.
                        val sub = if (words.isNotEmpty() && !words.all { it in norm(t.title) })
                            flat(t.subtasks).firstOrNull { q -> words.all { it in norm(q.title + " " + q.assignee.orEmpty()) } } else null
                        TopicPaperRow(t, seatOf(i, topics.size), onOpen = { keep(); onTask(sub?.let { "q:${t.id}:${it.id}" } ?: t.id) },
                            found = sub?.title)
                    }
                }
            }
            if (files.isNotEmpty()) {
                item("fh") { SectionHead(s.searchTry[2], count = files.size) }
                val shown = files.take(60)
                shown.forEachIndexed { i, (t, e, a) ->
                    item("f" + a.id) {
                        PaperRow(seatOf(i, shown.size), margin = runCatching { LocalDate.parse(e.at.take(10)) }.getOrNull()?.let { dayWord(it, today) },
                            mark = { Icon(kindIcon(a.kind), null, tint = c.graphite, modifier = Modifier.size(19.dp)) }, onClick = { keep(); viewing = a }) {
                            RowTitle(a.caption ?: a.name.ifBlank { a.kind }, maxLines = 1)
                            RowSub("≡ " + t.title, link = true)
                        }
                    }
                }
            }
            if (notes.isNotEmpty()) {
                item("nh") { SectionHead(s.notes, count = notes.size) }
                notes.forEachIndexed { i, n -> item("n" + n.id) { NoteCard(n, data, onClick = { keep(); onNote(n.id) }, seat = seatOf(i, notes.size)) } }
            }
            if (people.isNotEmpty()) {
                item("ph") { SectionHead(s.peopleTitle, count = people.size) }
                people.forEachIndexed { i, p ->
                    item("p" + p.id) {
                        PaperRow(seatOf(i, people.size), mark = { Avatar(p.name, 32.dp) }, onClick = { keep(); onPerson(p.id) }) {
                            RowTitle(p.name, maxLines = 1)
                            p.about?.let { RowSub(it) }
                        }
                    }
                }
            }
        }
    }
    viewing?.let { a -> Viewer(a, vm, onClose = { viewing = null }) }
}

/** The last searches, shared by every opening of the search screen (kept in prefs «ui» / searchRecent). */
private object RecentSearches { val state = mutableStateOf<List<String>?>(null) }
