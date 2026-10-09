package ru.anjeib.margin.paper

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import ru.anjeib.margin.book.Book
import ru.anjeib.margin.book.Dir
import ru.anjeib.margin.book.Kind
import ru.anjeib.margin.book.Line
import ru.anjeib.margin.book.Repeat
import ru.anjeib.margin.book.State
import ru.anjeib.margin.book.retext
import ru.anjeib.margin.book.addFiles
import ru.anjeib.margin.book.removeFile
import kotlinx.coroutines.launch
import androidx.compose.foundation.horizontalScroll
import ru.anjeib.margin.book.setPlace
import ru.anjeib.margin.book.setRepeat
import ru.anjeib.margin.book.setThread
import ru.anjeib.margin.book.setWhen
import ru.anjeib.margin.book.setWho
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.temporal.TemporalAdjusters

/*
 * A line's sheet (DESIGN.md §7): the same page, about one line. The line itself on top (the mark, the words edited in
 * place); then what Margin knows — every note whole, one per row, its value on the margin; the empty ones quiet
 * («+ когда»). Under the rule what was written under the line, and Margin's offers: move, not doing, a thread, delete.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun LineSheet(id: String, page: LocalDate, vm: BookVm, book: Book, today: LocalDate, onClose: () -> Unit,
              onThread: (String) -> Unit = {}, onPerson: (String) -> Unit = {}) {
    val c = P.c
    val w = P.w
    val l = book.line(id)
    if (l == null) { LaunchedEffect(Unit) { onClose() }; return }
    var text by remember(id) { mutableStateOf(l.text) }
    var ask by remember { mutableStateOf<String?>(null) }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var photo by remember { mutableStateOf<ru.anjeib.margin.data.Attachment?>(null) }
    val pickFile = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { u ->
        if (u != null) scope.launch { ru.anjeib.margin.data.Files.import(ctx, u)?.let { f -> vm.edit { it.addFiles(id, listOf(f)) } } }
    }
    fun commit() { if (text.isNotBlank() && text.trim() != l.text) vm.edit { it.retext(id, text) } }
    DisposableEffect(id) { onDispose { commit() } }

    ModalBottomSheet(onDismissRequest = { commit(); onClose() }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.paper, scrimColor = c.scrim,
        dragHandle = { Box(Modifier.padding(top = 10.dp, bottom = 6.dp).size(36.dp, 4.dp).clip(CircleShape).background(c.hairline)) }) {
        Column(Modifier.fillMaxWidth().imePadding().navigationBarsPadding().rule(c.rule).verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
            // The line itself.
            MarginRow(notes = emptyList(), mark = markOf(l), markLabel = w.a11yMark(l.state == State.Done),
                onMark = if (l.kind == Kind.Todo) ({ vm.toggle(id) }) else null) {
                BasicTextField(text, { text = it }, textStyle = Voice.words.copy(color = if (l.state == State.Open) c.ink else c.graphite,
                    textDecoration = if (l.state != State.Open && l.kind == Kind.Todo) TextDecoration.LineThrough else null),
                    cursorBrush = SolidColor(c.link), modifier = Modifier.weight(1f))
            }
            // What Margin knows: the value on the margin, the name of it in Margin's voice; a tap changes it.
            val notes = notesOf(l, null, today, book, w, Seen.Sheet)
            // Set properties one per row (the value on the margin); the unset ones — one quiet line of offers.
            val props = listOf(
                Triple("when", whenOf(l, today, w), w.propWhen),
                Triple("repeat", l.repeat?.let { w.repeat(it) }, w.propRepeat),
                Triple("who", l.who.firstOrNull()?.let { who -> when (who.dir) { Dir.To -> w.to(who.name); Dir.From -> w.from(who.name); Dir.With -> w.with(who.name) } }, w.propWho),
                Triple("place", l.place, w.propPlace),
                Triple("thread", book.thread(l.threadId)?.let { w.threadPrefix + it.title }, w.propThread),
            )
            props.filter { it.second != null }.forEach { (k, v, name) ->
                // A person or a thread on the margin is a link: its page; the name of the property changes it.
                val go: (() -> Unit)? = when (k) {
                    "thread" -> l.threadId?.let { t -> { commit(); onThread(t) } }
                    "who" -> l.who.firstOrNull()?.name?.let { n -> { commit(); onPerson(n) } }
                    else -> null
                }
                Prop(v, name, onValue = go) { ask = k }
            }
            // Files kept with the line: photos as themselves, the rest by name; a tap opens.
            if (l.files.isNotEmpty()) Row(Modifier.fillMaxWidth().padding(start = Grid.text - 4.dp, end = Grid.right, top = 4.dp, bottom = 8.dp)
                .horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                l.files.forEach { f -> FileChip(f, 72.dp, onClick = { openFile(ctx, f) { photo = it } }) }
            }
            val unset = props.filter { it.second == null && (it.first != "when" || l.kind != Kind.Note) }.map { it.first to it.third } + ("file" to w.propFile)
            FlowRow(Modifier.fillMaxWidth().padding(start = Grid.text, end = Grid.right), verticalArrangement = Arrangement.Center) {
                unset.forEachIndexed { i, (k, name) ->
                    if (i > 0) Text(" · ", style = Voice.reply.copy(color = c.pencil), modifier = Modifier.padding(vertical = 12.dp))
                    Text("+ $name", style = Voice.reply.copy(color = c.link),
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { if (k == "file") pickFile.launch(arrayOf("*/*")) else ask = k }
                            .padding(vertical = 12.dp, horizontal = 2.dp))
                }
            }
            if (notes.any { it.tone == Tone.Attention }) Row(Modifier.padding(top = 4.dp)) {
                Spacer(Modifier.width(Grid.text))
                Column { notes.filter { it.tone == Tone.Attention }.forEach { NoteText(it, androidx.compose.ui.text.style.TextAlign.Start) } }
            }
            // Margin's offers for this line.
            FlowRow(Modifier.fillMaxWidth().padding(start = Grid.text, end = Grid.right, top = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (l.kind == Kind.Todo && l.state == State.Open) {
                    Offer(w.offerTomorrow) { vm.move(id, today.plusDays(1)); onClose() }
                    Offer(w.offerLater) { vm.move(id, null); onClose() }
                    Offer(w.dropped) { vm.drop(id); onClose() }
                }
                Offer(w.offerDelete, danger = true) { vm.delete(id); onClose() }
            }
            // What was written under the line.
            val kids = book.children(id)
            if (kids.isNotEmpty()) {
                androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 8.dp))
                kids.sortedBy { it.writtenAt }.forEach { k ->
                    MarginRow(notes = listOf(Note(w.stamp(k.written, today), Tone.Quiet)), mark = markOf(k),
                        onMark = if (k.kind == Kind.Todo) ({ vm.toggle(k.id) }) else null) {
                        Text(k.text, style = Voice.words.copy(color = if (k.state == State.Open) c.ink else c.graphite,
                            textDecoration = if (k.state != State.Open && k.kind == Kind.Todo) TextDecoration.LineThrough else null))
                    }
                }
            }
            // Write more under it: a step if it says what to do, otherwise a comment.
            var more by remember(id) { mutableStateOf("") }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.width(Grid.text - 6.dp))
                val shape = RoundedCornerShape(22.dp)
                Box(Modifier.weight(1f).heightIn(min = 44.dp).clip(shape).background(c.sheet).border(1.dp, c.hairline, shape).padding(horizontal = 14.dp, vertical = 10.dp)) {
                    if (more.isEmpty()) Text(w.writeUnder, style = Voice.words.copy(color = c.graphite))
                    BasicTextField(more, { more = it }, textStyle = Voice.words.copy(color = c.ink), cursorBrush = SolidColor(c.link), modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { vm.writeUnder(id, more); more = "" }))
                }
            }
        }
    }

    photo?.let { f -> PhotoViewer(f, onClose = { photo = null }, onRemove = { vm.edit { it.removeFile(id, f.id) } }) }

    when (ask) {
        "when" -> WhenDialog(l, today, w, onDismiss = { ask = null }) { d, t, later ->
            if (later) vm.move(id, null) else vm.edit { it.setWhen(id, d, t, today = today) }
            ask = null
        }
        "repeat" -> RepeatDialog(l, today, w, onDismiss = { ask = null }) { r -> vm.edit { it.setRepeat(id, r) }; ask = null }
        "who" -> WhoDialog(l, w, onDismiss = { ask = null }) { name, dir -> vm.edit { it.setWho(id, name, dir) }; ask = null }
        "place" -> TextAsk(w.propPlace, l.place.orEmpty(), onDismiss = { ask = null }) { v -> vm.edit { it.setPlace(id, v) }; ask = null }
        "thread" -> ThreadDialog(book, l, w, onDismiss = { ask = null }) { t -> vm.edit { it.setThread(id, t) }; ask = null }
    }
}

/** «пт 15:00», «до пт», «потом» — the whole of «when» for the sheet's margin. */
private fun whenOf(l: Line, today: LocalDate, w: Words): String? = listOfNotNull(
    listOfNotNull(l.date?.takeIf { l.until == null }?.let { w.near(it, today) }, l.untilDate?.let { w.until(it, today) }).joinToString(" ").ifBlank { null },
    l.time ?: l.part?.let { w.part(it) },
).joinToString("\n").ifBlank { if (l.day == null && l.until == null && l.parentId == null && l.kind == Kind.Todo) w.later else null }

/** One property: its value on the margin (or a quiet dash), its name in Margin's voice right of the rule; a tap asks. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Prop(value: String?, name: String, onValue: (() -> Unit)? = null, onClick: () -> Unit) {
    val c = P.c
    Row(Modifier.fillMaxWidth().heightIn(min = Grid.line).combinedClickable(onClick = onClick).padding(end = Grid.right), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(Grid.margin - Grid.noteGap).heightIn(min = Grid.line).then(if (onValue != null) Modifier.combinedClickable(onClick = onValue) else Modifier)
            .padding(start = 4.dp), contentAlignment = Alignment.CenterEnd) {
            // The whole value: on the sheet it may take two lines (day over hour).
            val link = value != null && (value.startsWith("≡") || value.startsWith("→") || value.startsWith("←") || value.startsWith("с ") || value.startsWith("with"))
            if (value != null) Text(value, style = Voice.note.copy(color = if (link) c.link else c.graphite,
                textAlign = androidx.compose.ui.text.style.TextAlign.End), maxLines = 3)
        }
        Spacer(Modifier.width(Grid.text - Grid.margin + Grid.noteGap))
        Text(if (value == null) "+ $name" else name, style = Voice.reply.copy(color = if (value == null) c.link else c.graphite))
    }
}

/** An offer in Margin's voice: a word, a full line high. */
@Composable
private fun Offer(text: String, danger: Boolean = false, onClick: () -> Unit) {
    val c = P.c
    Text(text, style = Voice.reply.copy(color = if (danger) c.red else c.link),
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 12.dp))
}

/** When: quick days, a day from the calendar, an hour, or «потом». */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun WhenDialog(l: Line, today: LocalDate, w: Words, onDismiss: () -> Unit, onPick: (LocalDate?, String?, Boolean) -> Unit) {
    val c = P.c
    var day by remember { mutableStateOf(l.date ?: today) }
    var time by remember { mutableStateOf(l.time) }
    var calendar by remember { mutableStateOf(false) }
    var clock by remember { mutableStateOf(false) }
    val monday = today.with(TemporalAdjusters.next(DayOfWeek.MONDAY))
    AlertDialog(onDismissRequest = onDismiss, containerColor = c.sheet,
        title = { Text(w.propWhen, style = Voice.section.copy(color = c.ink)) },
        text = {
            Column {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(today, today.plusDays(1), monday).distinct().forEach { d ->
                        Chip(w.near(d, today) + if (d == monday && d != today.plusDays(1)) "" else "", d == day) { day = d }
                    }
                    Chip(w.pickDay, false) { calendar = true }
                }
                Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(w.near(day, today) + (time?.let { " · $it" } ?: ""), style = Voice.reply.copy(color = c.ink), modifier = Modifier.weight(1f))
                    Text(if (time == null) w.addTime else w.noTime, style = Voice.reply.copy(color = c.link),
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { if (time == null) clock = true else time = null }.padding(8.dp))
                }
            }
        },
        confirmButton = { TextButton({ onPick(day, time, false) }) { Text(w.ok, color = c.link) } },
        dismissButton = { TextButton({ onPick(null, null, true) }) { Text(w.later.replaceFirstChar { it.uppercase() }, color = c.graphite) } },
    )
    if (calendar) {
        val st = rememberDatePickerState(initialSelectedDateMillis = day.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
        DatePickerDialog(onDismissRequest = { calendar = false },
            confirmButton = { TextButton({ st.selectedDateMillis?.let { day = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }; calendar = false }) { Text(w.ok, color = c.link) } }) {
            DatePicker(st, showModeToggle = false)
        }
    }
    if (clock) {
        val now = l.hour ?: LocalTime.of(10, 0)
        val st = rememberTimePickerState(now.hour, now.minute, true)
        AlertDialog(onDismissRequest = { clock = false }, containerColor = c.sheet,
            text = { TimePicker(st) },
            confirmButton = { TextButton({ time = "%02d:%02d".format(st.hour, st.minute); clock = false }) { Text(w.ok, color = c.link) } })
    }
}

@Composable
private fun Chip(text: String, on: Boolean, onClick: () -> Unit) {
    val c = P.c
    Text(text, style = Voice.small.copy(color = if (on) c.link else c.ink),
        modifier = Modifier.clip(RoundedCornerShape(50)).background(if (on) c.linkWash else c.paper).border(1.dp, if (on) c.linkWash else c.hairline, RoundedCornerShape(50))
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RepeatDialog(l: Line, today: LocalDate, w: Words, onDismiss: () -> Unit, onPick: (Repeat?) -> Unit) {
    val c = P.c
    val dow = (l.date ?: today).dayOfWeek.value
    val options = listOf(null, Repeat("daily"), Repeat("weekly", 1, listOf(1, 2, 3, 4, 5)), Repeat("weekly", 1, listOf(dow)), Repeat("monthly"), Repeat("yearly"))
    AlertDialog(onDismissRequest = onDismiss, containerColor = c.sheet,
        title = { Text(w.propRepeat, style = Voice.section.copy(color = c.ink)) },
        text = {
            Column {
                options.forEach { r ->
                    Text(r?.let { w.repeat(it).removePrefix("↻ ") } ?: w.noRepeat, style = Voice.words.copy(color = if (r == l.repeat) c.link else c.ink),
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { onPick(r) }.padding(vertical = 12.dp, horizontal = 4.dp))
                }
            }
        },
        confirmButton = {}, dismissButton = { TextButton(onDismiss) { Text(w.cancel, color = c.graphite) } })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WhoDialog(l: Line, w: Words, onDismiss: () -> Unit, onPick: (String, Dir) -> Unit) {
    val c = P.c
    val cur = l.who.firstOrNull()
    var name by remember { mutableStateOf(cur?.name.orEmpty()) }
    var dir by remember { mutableStateOf(cur?.dir ?: Dir.With) }
    AlertDialog(onDismissRequest = onDismiss, containerColor = c.sheet,
        title = { Text(w.propWho, style = Voice.section.copy(color = c.ink)) },
        text = {
            Column {
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(c.paper).border(1.dp, c.hairline, RoundedCornerShape(10.dp)).padding(12.dp)) {
                    if (name.isEmpty()) Text(w.whoHint, style = Voice.words.copy(color = c.graphite))
                    BasicTextField(name, { name = it }, textStyle = Voice.words.copy(color = c.ink), cursorBrush = SolidColor(c.link), modifier = Modifier.fillMaxWidth())
                }
                FlowRow(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip(w.dirFrom, dir == Dir.From) { dir = Dir.From }
                    Chip(w.dirTo, dir == Dir.To) { dir = Dir.To }
                    Chip(w.dirWith, dir == Dir.With) { dir = Dir.With }
                }
            }
        },
        confirmButton = { TextButton({ if (name.isNotBlank()) onPick(name.trim(), dir) }) { Text(w.ok, color = c.link) } },
        dismissButton = { TextButton(onDismiss) { Text(w.cancel, color = c.graphite) } })
}

@Composable
private fun TextAsk(title: String, value: String, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val c = P.c
    val w = P.w
    var v by remember { mutableStateOf(value) }
    AlertDialog(onDismissRequest = onDismiss, containerColor = c.sheet,
        title = { Text(title, style = Voice.section.copy(color = c.ink)) },
        text = {
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(c.paper).border(1.dp, c.hairline, RoundedCornerShape(10.dp)).padding(12.dp)) {
                BasicTextField(v, { v = it }, textStyle = Voice.words.copy(color = c.ink), cursorBrush = SolidColor(c.link), modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton({ onPick(v) }) { Text(w.ok, color = c.link) } },
        dismissButton = { TextButton(onDismiss) { Text(w.cancel, color = c.graphite) } })
}

@Composable
private fun ThreadDialog(book: Book, l: Line, w: Words, onDismiss: () -> Unit, onPick: (String?) -> Unit) {
    val c = P.c
    var v by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, containerColor = c.sheet,
        title = { Text(w.propThread, style = Voice.section.copy(color = c.ink)) },
        text = {
            Column {
                book.threads.filter { it.closedAt == null }.sortedBy { it.title }.forEach { t ->
                    Text(w.threadPrefix + t.title, style = Voice.words.copy(color = if (t.id == l.threadId) c.link else c.ink),
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { onPick(t.title) }.padding(vertical = 12.dp, horizontal = 4.dp))
                }
                Box(Modifier.padding(top = 8.dp).fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(c.paper).border(1.dp, c.hairline, RoundedCornerShape(10.dp)).padding(12.dp)) {
                    if (v.isEmpty()) Text(w.newThread, style = Voice.words.copy(color = c.graphite))
                    BasicTextField(v, { v = it }, textStyle = Voice.words.copy(color = c.ink), cursorBrush = SolidColor(c.link), modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = { TextButton({ if (v.isNotBlank()) onPick(v.trim()) }) { Text(w.ok, color = c.link) } },
        dismissButton = { TextButton({ if (l.threadId != null) onPick(null) else onDismiss() }) { Text(if (l.threadId != null) w.outOfThread else w.cancel, color = c.graphite) } })
}
