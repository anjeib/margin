package ru.anjeib.margin.ui

import androidx.compose.foundation.border
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
data class LaneItem(val id: String, val at: String, val text: String)

/**
 * Text written right on the page, like in a notebook: each piece with its day and hour on the margin, edited in place.
 * Below them an empty line with a quiet [hint] that steps aside while the cursor is in it; what is typed there becomes
 * a new piece, stamped with when its writing began.
 */
@Composable
fun WrittenLane(items: List<LaneItem>, hint: String, modifier: Modifier = Modifier, after: String? = null, label: String? = null,
                draft: androidx.compose.runtime.MutableState<String>? = null, focusKey: Any? = null,
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
                var t by remember(item.id) { mutableStateOf(item.text) }
                val edit by rememberUpdatedState(onEdit)
                // Saved a moment after typing stops, and on leaving the page whatever is left.
                LaunchedEffect(t) { if (t != item.text) { kotlinx.coroutines.delay(800); edit(item.id, t) } }
                DisposableEffect(item.id) { onDispose { if (t.trim() != item.text.trim()) edit(item.id, t) } }
                Margined(margin = { Box(Modifier.padding(top = 9.dp)) { Stamp(item.at, sameDay = above?.take(10) == item.at.take(10)) } }) {
                    BasicTextField(t, { t = it }, textStyle = text, cursorBrush = SolidColor(c.link), modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
                }
            }
        }
        // The page's writing line is the bar's words ([draft]): «send» down there decides what they become.
        if (draft != null) {
            val add by rememberUpdatedState(onAdd)
            // Leaving the page with words not sent keeps them as a piece rather than losing them.
            DisposableEffect(Unit) { onDispose { if (draft.value.isNotBlank()) { add(draft.value.trim(), nowStamp()); draft.value = "" } } }
            PageLine(draft, if (items.isEmpty()) M.s.laneHint else "", focusKey, after = items.lastOrNull()?.at ?: after)
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
fun PageLine(text: androidx.compose.runtime.MutableState<String>, hint: String, focusKey: Any?, modifier: Modifier = Modifier, after: String? = null) {
    val c = M.c
    val style = Type.body.copy(color = c.ink, fontSize = 16.sp, lineHeight = 23.sp)
    var t by text
    // What the user typed keeps their cursor; words put in from outside (dictation) get it after them.
    var typed by remember { mutableStateOf("") }
    var sel by remember { mutableStateOf(androidx.compose.ui.text.TextRange(0)) }
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(focusKey) { if (focusKey != null) { kotlinx.coroutines.delay(150); runCatching { focus.requestFocus() } } }
    val now = nowStamp()
    Margined(modifier, margin = { if (t.isNotBlank()) Box(Modifier.padding(top = 9.dp)) { Stamp(now, sameDay = after?.take(10) == now.take(10)) } }) {
        Box(Modifier.fillMaxWidth().heightIn(min = 96.dp).clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) {
            runCatching { focus.requestFocus() } }) {
            // The quiet word stays until something is written — a sheet may hand the line its focus by itself.
            if (t.isEmpty() && hint.isNotEmpty()) Text(hint, style = Type.italic.copy(color = c.faint, fontSize = 16.sp, lineHeight = 23.sp), modifier = Modifier.padding(vertical = 8.dp))
            val value = if (typed == t) androidx.compose.ui.text.input.TextFieldValue(t, sel) else androidx.compose.ui.text.input.TextFieldValue(t, androidx.compose.ui.text.TextRange(t.length))
            BasicTextField(value, { v -> val next = continueList(t, v.text); t = next; typed = next; sel = if (next == v.text) v.selection else androidx.compose.ui.text.TextRange(next.length) },
                textStyle = style, cursorBrush = SolidColor(c.link),
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).focusRequester(focus))
        }
    }
}

/** The day and the hour a piece was written, on the margin in Margin's italic; only the day when that is all that is known. */
@Composable
internal fun Stamp(at: String, sameDay: Boolean = false) {
    val dt = runCatching { java.time.LocalDateTime.parse(at.take(16)) }.getOrNull()
        ?: run { if (!sameDay) runCatching { java.time.LocalDate.parse(at.take(10)) }.getOrNull()?.let { MarginNote(dayWord(it)) }; return }
    MarginNote(listOfNotNull(if (sameDay) null else dayWord(dt.toLocalDate()), dt.toLocalTime().toString().take(5)).joinToString("\n"))
}
