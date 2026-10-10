package ru.anjeib.margin.paper

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.anjeib.margin.book.Book
import ru.anjeib.margin.book.Line
import ru.anjeib.margin.data.Attachment
import java.time.LocalDate
import java.time.YearMonth

/*
 * «Файлы»: everything kept with the lines, in one place — newest first, by month, each under the line it belongs to,
 * the day on the margin. A file opens; the line's words open its sheet. A quiet filter: all · photos · documents.
 */
@Composable
fun FilesPage(book: Book, today: LocalDate, onOpen: (String) -> Unit) {
    val c = P.c
    val w = P.w
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var kind by rememberSaveable { mutableIntStateOf(0) }
    var photo by remember { mutableStateOf<Attachment?>(null) }
    fun seen(f: Attachment) = when (kind) { 1 -> f.isPicture; 2 -> !f.isPicture; else -> true }
    val all = remember(book) { book.withFiles() }
    val shown = all.map { (l, d) -> Triple(l, d, l.files.filter(::seen)) }.filter { it.third.isNotEmpty() }
    LazyColumn(Modifier.fillMaxSize().rule(c.rule), contentPadding = PaddingValues(bottom = 40.dp)) {
        item {
            val files = all.flatMap { it.first.files }
            PageHead(marginTop = "", marginBottom = null, title = w.filesTitle, line = w.filesLine(files.count { it.isPicture }, files.count { !it.isPicture }))
        }
        if (all.isEmpty()) item { MarginSays(w.filesEmpty) }
        else item {
            Row(Modifier.fillMaxWidth().padding(start = Grid.text - 4.dp, end = Grid.right)) {
                listOf(w.filterAll, w.photo, w.documents).forEachIndexed { i, t ->
                    if (i > 0) Text(" · ", style = Voice.reply.copy(color = c.pencil), modifier = Modifier.padding(vertical = 12.dp))
                    Text(t, style = Voice.reply.copy(color = if (kind == i) c.ink else c.link),
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { kind = i }.padding(vertical = 12.dp, horizontal = 4.dp))
                }
            }
        }
        var month: YearMonth? = null
        shown.forEach { (l, d, files) ->
            val ym = YearMonth.from(d)
            if (ym != month) { month = ym; item("m$ym") { SectionHead(w.monthName(ym).replaceFirstChar(Char::uppercaseChar)) } }
            item("l" + l.id) { FileLine(l, d, files, today, w, onOpen = { onOpen(l.id) }, onFile = { f -> openFile(ctx, f) { photo = it } }) }
        }
    }
    photo?.let { f -> PhotoViewer(f, onClose = { photo = null }) }
}

/** One line with its files: the day on the margin, the words as a link to the line, the files in a row under them. */
@Composable
private fun FileLine(l: Line, d: LocalDate, files: List<Attachment>, today: LocalDate, w: Words, onOpen: () -> Unit, onFile: (Attachment) -> Unit) {
    val c = P.c
    Row(Modifier.fillMaxWidth().padding(end = Grid.right, top = 6.dp, bottom = 10.dp)) {
        Box(Modifier.width(Grid.margin - Grid.noteGap).padding(start = 4.dp, top = 3.dp), contentAlignment = Alignment.TopEnd) {
            NoteText(Note(w.near(d, today), Tone.Quiet))
        }
        Spacer(Modifier.width(Grid.text - Grid.margin + Grid.noteGap))
        Column(Modifier.weight(1f)) {
            Text(l.text, style = Voice.words.copy(color = c.ink), maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onOpen).padding(vertical = 2.dp))
            Row(Modifier.fillMaxWidth().padding(top = 8.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                files.forEach { f -> FileChip(f, 96.dp, onClick = { onFile(f) }) }
            }
        }
    }
}

private val Attachment.isPicture get() = kind == "photo" || kind == "video"

/** Lines with files, newest first, each with the day it belongs to (its page, or the day it was written). */
fun Book.withFiles(): List<Pair<Line, LocalDate>> = lines.filter { it.files.isNotEmpty() }
    .map { l -> l to (l.date ?: runCatching { LocalDate.parse(l.writtenAt.take(10)) }.getOrDefault(LocalDate.now())) }
    .sortedWith(compareByDescending<Pair<Line, LocalDate>> { it.second }.thenByDescending { it.first.writtenAt })
