package ru.anjeib.margin.ui

import androidx.compose.foundation.border

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
                onEdit: (String, String) -> Unit, onAdd: (String, String) -> Unit) {
    val c = M.c
    val text = Type.body.copy(color = c.ink, fontSize = 15.5.sp, lineHeight = 22.sp)
    // v8: the pieces stand on one panel under their caps label; the last row of the panel is a field that looks like one.
    Column(modifier.fillMaxWidth()) {
        label?.let { SectionHead(it, count = items.size.takeIf { n -> n > 0 }, top = 14.dp) }
        val n = items.size + 1
        // The day is written once: a piece of the same day as the one above it shows only its hour ([after] — what stands above the lane).
        items.forEachIndexed { i, item ->
            val above = if (i == 0) after else items[i - 1].at
            androidx.compose.runtime.key(item.id) {
                var t by remember(item.id) { mutableStateOf(item.text) }
                val edit by rememberUpdatedState(onEdit)
                // Saved a moment after typing stops, and on leaving the page whatever is left.
                LaunchedEffect(t) { if (t != item.text) { kotlinx.coroutines.delay(800); edit(item.id, t) } }
                DisposableEffect(item.id) { onDispose { if (t.trim() != item.text.trim()) edit(item.id, t) } }
                Margined(margin = { Box(Modifier.padding(top = 12.dp)) { Stamp(item.at, sameDay = above?.take(10) == item.at.take(10)) } }) {
                    PanelPiece(seatOf(i, n), dividerStart = 16.dp) {
                        BasicTextField(t, { t = it }, textStyle = text, cursorBrush = SolidColor(c.link), modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp))
                    }
                }
            }
        }
        var draft by remember { mutableStateOf("") }
        var started by remember { mutableStateOf<String?>(null) }
        var focused by remember { mutableStateOf(false) }
        val add by rememberUpdatedState(onAdd)
        fun commit() { if (draft.isNotBlank()) add(draft.trim(), started ?: nowStamp()); draft = ""; started = null }
        DisposableEffect(Unit) { onDispose { commit() } }
        Margined(margin = { started?.let { st -> Box(Modifier.padding(top = 18.dp)) { Stamp(st, sameDay = (items.lastOrNull()?.at ?: after)?.take(10) == st.take(10)) } } }) {
            PanelPiece(seatOf(n - 1, n), dividerStart = 16.dp) {
                Box(Modifier.fillMaxWidth().padding(10.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(20.dp))
                    .border(1.dp, if (focused) c.link.copy(alpha = 0.5f) else c.hairline, androidx.compose.foundation.shape.RoundedCornerShape(20.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp)) {
                    if (draft.isEmpty()) Text(hint + "…", style = text.copy(color = c.graphite))
                    BasicTextField(draft, { v -> draft = v; if (started == null && v.isNotBlank()) started = nowStamp() },
                        textStyle = text, cursorBrush = SolidColor(c.link),
                        modifier = Modifier.fillMaxWidth().onFocusChanged { f -> if (focused && !f.isFocused) commit(); focused = f.isFocused })
                }
            }
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
