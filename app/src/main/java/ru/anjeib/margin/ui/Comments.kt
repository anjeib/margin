package ru.anjeib.margin.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.anjeib.margin.data.dayMonthShort
import ru.anjeib.margin.data.nowStamp

/** One line of a lane: a comment, a record of an idea — what it says and when it was written. */
data class LaneItem(val id: String, val at: String, val text: String, val files: List<ru.anjeib.margin.data.Attachment> = emptyList())

/**
 * Text written right on the page, like in a notebook: each piece with its day and hour on the margin, edited in place.
 * Below them an empty line with a quiet [hint] that steps aside while the cursor is in it; what is typed there becomes
 * a new piece, stamped with when its writing began.
 */
@Composable
fun WrittenLane(items: List<LaneItem>, hint: String, modifier: Modifier = Modifier, after: String? = null, label: String? = null,
                draft: androidx.compose.runtime.MutableState<String>? = null, focusKey: Any? = null,
                vm: MainVm? = null, files: androidx.compose.runtime.snapshots.SnapshotStateList<ru.anjeib.margin.data.Attachment>? = null,
                pasteTo: String? = null, onKeep: (() -> Unit)? = null,
                /** A card: the line's «+» chooses marks (files come with the paperclip in the bar); null — no «+» at all. */
                plus: Plus = Plus.Files,
                onEdit: (String, String) -> Unit, onAdd: (String, String) -> Unit) {
    val c = M.c
    val text = Type.body.copy(color = c.ink, fontSize = 16.sp, lineHeight = 23.sp)
    // Owner, 10 окт: no field in a frame and no label over it — the card's own page is written on, like a text editor;
    // each piece gets its day and hour on the margin when its writing begins. [label] is kept for old call sites and not shown.
    Column(modifier.fillMaxWidth()) {
        Margined(Modifier.padding(top = 6.dp, bottom = 4.dp)) { Box(Modifier.fillMaxWidth().height(1.dp).background(c.hairline)) }
        // The day is written once: a piece of the same day as the one above it shows only its hour ([after] — what stands above the lane).
        items.forEachIndexed { i, item ->
            val above = if (i == 0) after else items[i - 1].at
            androidx.compose.runtime.key(item.id) {
                // Edited in place, a list in it going on by itself (continueList).
                var tv by remember(item.id) { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(item.text)) }
                val t = tv.text
                val edit by rememberUpdatedState(onEdit)
                // Saved a moment after typing stops, and on leaving the page whatever is left.
                LaunchedEffect(t) { if (t != item.text) { kotlinx.coroutines.delay(800); edit(item.id, t) } }
                DisposableEffect(item.id) { onDispose { if (tv.text.trim() != item.text.trim()) edit(item.id, tv.text) } }
                Margined(margin = { Box(Modifier.padding(top = 9.dp)) { Stamp(item.at, sameDay = above?.take(10) == item.at.take(10)) } }) {
                    Column {
                        if (item.text.isNotBlank() || item.files.isEmpty())
                            BasicTextField(tv, { tv = continueList(tv, it) }, textStyle = text, cursorBrush = SolidColor(c.link), modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
                        // A file stays in the piece it came with (owner, 14 окт), under its words.
                        if (item.files.isNotEmpty()) Box(Modifier.padding(top = 4.dp, bottom = 8.dp)) { AttachmentsView(item.files) }
                    }
                }
            }
        }
        // The page's writing line is the bar's words ([draft]): «send» down there decides what they become.
        if (draft != null) {
            val add by rememberUpdatedState(onAdd)
            // Leaving the page with words not kept keeps them as a piece rather than losing them (when the page has no keeper of its own).
            DisposableEffect(Unit) { onDispose { if (onKeep == null && draft.value.isNotBlank()) { add(draft.value.trim(), nowStamp()); draft.value = "" } } }
            PageLine(draft, if (items.isEmpty()) M.s.laneHint else "", focusKey, after = items.lastOrNull()?.at ?: after, vm = vm, files = files, pasteTo = pasteTo, onKeep = onKeep, plus = plus)
            return@Column
        }
        var draft by remember { mutableStateOf("") }
        var started by remember { mutableStateOf<String?>(null) }
        var focused by remember { mutableStateOf(false) }
        val focus = remember { androidx.compose.ui.focus.FocusRequester() }
        val add by rememberUpdatedState(onAdd)
        fun commit() { if (draft.isNotBlank()) add(draft.trim(), started ?: nowStamp()); draft = ""; started = null }
        DisposableEffect(Unit) { onDispose { commit() } }
        // The empty rest of the page is the next line: a tap anywhere below puts the cursor there.
        Margined(margin = { started?.let { st -> Box(Modifier.padding(top = 9.dp)) { Stamp(st, sameDay = (items.lastOrNull()?.at ?: after)?.take(10) == st.take(10)) } } }) {
            Box(Modifier.fillMaxWidth().heightIn(min = 88.dp).clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) {
                runCatching { focus.requestFocus() } }) {
                // A quiet word only on an empty page — once something is written, the page speaks for itself.
                if (draft.isEmpty() && items.isEmpty() && !focused) Text(M.s.laneHint, style = Type.italic.copy(color = c.faint, fontSize = 16.sp, lineHeight = 23.sp),
                    modifier = Modifier.padding(vertical = 8.dp))
                BasicTextField(draft, { v -> draft = v; if (started == null && v.isNotBlank()) started = nowStamp() },
                    textStyle = text, cursorBrush = SolidColor(c.link),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).focusRequester(focus).onFocusChanged { f -> if (focused && !f.isFocused) commit(); focused = f.isFocused })
            }
        }
    }
}

/**
 * The page's own writing line (owner, 12 окт): no field and no frame — the rest of the page is written on. Its words are
 * [text], shared with the bar below (microphone, «send»): dictated words appear here, are looked over, and go when sent.
 * «/», «#», «@» at the end open the same window of marks as the main field (the bar shows it).
 */
@Composable
fun PageLine(text: androidx.compose.runtime.MutableState<String>, hint: String, focusKey: Any?, modifier: Modifier = Modifier, after: String? = null,
             vm: MainVm? = null, files: androidx.compose.runtime.snapshots.SnapshotStateList<ru.anjeib.margin.data.Attachment>? = null,
             /** Where «Вставить сюда» puts what was copied or is being moved: «task:<id>», «deal:<id>», «comment:<ref>». */
             pasteTo: String? = null,
             /** Keeps what is written (there is no «send»): called when the keyboard goes away after writing. */
             onKeep: (() -> Unit)? = null,
             plus: Plus = Plus.Files) {
    val c = M.c
    val style = Type.body.copy(color = c.ink, fontSize = 16.sp, lineHeight = 23.sp)
    var t by text
    // What the user typed keeps their cursor; words put in from outside (dictation) get it after them.
    var typed by remember { mutableStateOf("") }
    var sel by remember { mutableStateOf(androidx.compose.ui.text.TextRange(0)) }
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    // Owner, 13 окт: no «send» — writing done (the keyboard hidden) keeps it.
    @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
    val ime = androidx.compose.foundation.layout.WindowInsets.isImeVisible
    var hadIme by remember { mutableStateOf(false) }
    LaunchedEffect(ime) {
        if (ime) hadIme = true
        else if (hadIme) { hadIme = false; if (text.value.isNotBlank() || !files.isNullOrEmpty()) onKeep?.invoke() }
    }
    LaunchedEffect(focusKey) { if (focusKey != null) { kotlinx.coroutines.delay(150); runCatching { focus.requestFocus() } } }
    val now = nowStamp()
    // On the margin only the hour once writing begins — it is the dates' place; «+» stands just right of the rule, at the
    // start of the line, so a photo or a file lands right here, under these words (owner, 13 окт).
    Margined(modifier, margin = {
        if (t.isNotBlank() || !files.isNullOrEmpty()) Box(Modifier.padding(top = 9.dp)) { Stamp(now, sameDay = after?.take(10) == now.take(10)) }
    }) {
      androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.Top) {
        if (vm != null && files != null && plus == Plus.Files) Box(Modifier.offset(x = (-10).dp).padding(top = 1.dp)) { AttachPlus(vm, size = 36.dp, tint = M.c.graphite) { files += it } }
        // A card (owner, 14 окт): «+» offers the marks — what it is, where it belongs, who owes, which topic.
        if (vm != null && plus == Plus.Marks) Box(Modifier.offset(x = (-10).dp).padding(top = 1.dp).size(36.dp).clip(androidx.compose.foundation.shape.CircleShape)
            .clickable(onClickLabel = M.s.slashWhat) { runCatching { focus.requestFocus() }; vm.markPicker.value++ }, contentAlignment = androidx.compose.ui.Alignment.Center) {
            PlusGlyph(M.c.graphite, 18.dp)
        }
        Box(Modifier.weight(1f).heightIn(min = 96.dp).clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) {
            runCatching { focus.requestFocus() } }) {
            // The quiet word stays until something is written — a sheet may hand the line its focus by itself.
            if (t.isEmpty() && hint.isNotEmpty() && vm?.pageMarks?.value == null && (pasteTo == null || vm?.clip?.collectAsState()?.value == null)) Text(hint, style = Type.italic.copy(color = c.faint, fontSize = 16.sp, lineHeight = 23.sp), modifier = Modifier.padding(vertical = 8.dp))
            val value = if (typed == t) androidx.compose.ui.text.input.TextFieldValue(t, sel) else androidx.compose.ui.text.input.TextFieldValue(t, androidx.compose.ui.text.TextRange(t.length))
            Column {
                // Something copied or being moved (owner, 13 окт): one quiet line offers to put it here.
                val clip = vm?.clip?.collectAsState()?.value
                if (clip != null && pasteTo != null) androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth().padding(top = 6.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text("↓ " + M.s.pasteHere + (if (clip.preview.isNotBlank()) " · «" + clip.preview + "»" else ""),
                        style = Type.body.copy(color = c.link, fontSize = 15.sp), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).clip(androidx.compose.foundation.shape.RoundedCornerShape(6.dp)).clickable { vm.pasteInto(pasteTo) }.padding(vertical = 6.dp))
                    Text("×", style = Type.body.copy(color = c.graphite, fontSize = 18.sp),
                        modifier = Modifier.clip(androidx.compose.foundation.shape.CircleShape).clickable { vm.clearClip() }.padding(horizontal = 10.dp, vertical = 4.dp))
                }
                // «/» typed here: the choices stand right over these words.
                vm?.pageMarks?.value?.invoke()
                BasicTextField(value, { v -> val next = continueList(value, MarkLook.eraseWhole(value, v)); t = next.text; typed = next.text; sel = next.selection },
                    textStyle = style, cursorBrush = SolidColor(c.link), visualTransformation = rememberMarkLook(vm),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).focusRequester(focus))
                // What «+» added, before it is sent: right where it will be.
                if (!files.isNullOrEmpty()) androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth().horizontalScroll(androidx.compose.foundation.rememberScrollState()).padding(bottom = 8.dp),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                    val ctx = androidx.compose.ui.platform.LocalContext.current
                    files.toList().forEach { a -> AttachmentChip(a, onRemove = { files.remove(a); if (a.file != null) ru.anjeib.margin.data.Files.delete(ctx, a) }) }
                }
            }
        }
      }
    }
}

/** What the «+» at the start of a page's writing line does: add files (a topic), offer marks (a card), or nothing (an idea). */
enum class Plus { Files, Marks, None }

/** The day and the hour a piece was written, on the margin in Margin's italic; only the day when that is all that is known. */
@Composable
internal fun Stamp(at: String, sameDay: Boolean = false) {
    val dt = runCatching { java.time.LocalDateTime.parse(at.take(16)) }.getOrNull()
        ?: run { if (!sameDay) runCatching { java.time.LocalDate.parse(at.take(10)) }.getOrNull()?.let { MarginNote(dayWord(it)) }; return }
    MarginNote(listOfNotNull(if (sameDay) null else dayWord(dt.toLocalDate()), dt.toLocalTime().toString().take(5)).joinToString("\n"))
}
