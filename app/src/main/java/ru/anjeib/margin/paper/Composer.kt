package ru.anjeib.margin.paper

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.anjeib.margin.book.Book
import ru.anjeib.margin.ui.SendGlyph
import java.time.LocalDate

/*
 * The one field (DESIGN.md §6). It has a margin too: while you write, Margin already pencils there what it understood
 * («завтра 15:00 · с Олегом») — the live preview stands exactly where the notes will stand.
 */
@Composable
fun Composer(vm: BookVm, target: BookVm.Target, book: Book, today: LocalDate, modifier: Modifier = Modifier) {
    val page = target.page
    val c = P.c
    val w = P.w
    var text by rememberSaveable { mutableStateOf("") }
    val said by vm.said.collectAsState()
    val handed by vm.prefill.collectAsState()
    androidx.compose.runtime.LaunchedEffect(handed) { handed?.let { text = it; vm.prefillTaken() } }
    val draft = remember(text, target, book) { if (text.isBlank()) null else vm.preview(text, target) }
    val first = draft?.lines?.firstOrNull()
    val pencil = when {
        draft == null -> emptyList()
        draft.question -> listOf(Note("?", Tone.Attention))
        draft.lines.size > 1 -> listOf(Note(w.linesN(draft.lines.size), Tone.Quiet))
        first != null -> notesOf(first, page, today, book, w) + listOfNotNull(draft.threadTitle?.let { Note(w.threadPrefix + it, Tone.Link) })
        else -> emptyList()
    }.take(3)

    fun send() {
        if (text.isBlank()) return
        if (vm.write(text, target)) text = ""
    }

    Column(modifier.fillMaxWidth().background(c.paper).rule(c.rule)) {
        // Margin's sentence over the field: where the words went, with a way back.
        AnimatedVisibility(said != null, enter = fadeIn(), exit = fadeOut()) {
            var last by remember { mutableStateOf<BookVm.Said?>(null) }
            if (said != null) last = said
            last?.let { s ->
                Row(Modifier.fillMaxWidth().padding(end = Grid.right, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.width(Grid.text))
                    Text(s.text, style = Voice.reply.copy(color = c.graphite), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (s.undo != null) Text("  ·  " + w.undo, style = Voice.reply.copy(color = c.link),
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { vm.undo() }.padding(vertical = 12.dp, horizontal = 4.dp))
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.Bottom) {
            // The field's own margin: what Margin has understood so far.
            Column(Modifier.width(Grid.margin - Grid.noteGap).padding(start = 4.dp, bottom = 13.dp), horizontalAlignment = Alignment.End) {
                pencil.forEach { NoteText(it) }
            }
            Spacer(Modifier.width(Grid.noteGap + 6.dp))
            val shape = RoundedCornerShape(22.dp)
            Row(Modifier.weight(1f).padding(end = 12.dp).heightIn(min = 44.dp).clip(shape).background(c.sheet).border(1.dp, c.hairline, shape)
                .padding(start = 14.dp, end = 4.dp), verticalAlignment = Alignment.Bottom) {
                Box(Modifier.weight(1f).padding(vertical = 10.dp)) {
                    if (text.isEmpty()) Text(w.composerHint, style = Voice.words.copy(color = c.graphite), maxLines = 1)
                    BasicTextField(text, { text = it }, textStyle = Voice.words.copy(color = c.ink), cursorBrush = SolidColor(c.link), maxLines = 6,
                        modifier = Modifier.fillMaxWidth())
                }
                val can = text.isNotBlank()
                Box(Modifier.size(42.dp).clip(CircleShape).clickable(enabled = can) { send() }.semantics { contentDescription = w.a11ySend },
                    contentAlignment = Alignment.Center) {
                    Box(Modifier.size(32.dp).clip(CircleShape).background(if (can) c.redFill else c.redFill.copy(alpha = 0.35f)), contentAlignment = Alignment.Center) {
                        SendGlyph(androidx.compose.ui.graphics.Color.White, 15.dp)
                    }
                }
            }
        }
    }
}
