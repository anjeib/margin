package ru.anjeib.margin.ui

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
fun WrittenLane(items: List<LaneItem>, hint: String, modifier: Modifier = Modifier, onEdit: (String, String) -> Unit, onAdd: (String, String) -> Unit) {
    val c = M.c
    val text = Type.body.copy(color = c.ink, fontSize = 15.5.sp, lineHeight = 22.sp)
    Column(modifier.fillMaxWidth()) {
        items.forEach { item ->
            androidx.compose.runtime.key(item.id) {
                var t by remember(item.id) { mutableStateOf(item.text) }
                val edit by rememberUpdatedState(onEdit)
                // Saved a moment after typing stops, and on leaving the page whatever is left.
                LaunchedEffect(t) { if (t != item.text) { kotlinx.coroutines.delay(800); edit(item.id, t) } }
                DisposableEffect(item.id) { onDispose { if (t.trim() != item.text.trim()) edit(item.id, t) } }
                Margined(Modifier.padding(top = 6.dp), margin = { Stamp(item.at) }) {
                    BasicTextField(t, { t = it }, textStyle = text, cursorBrush = SolidColor(c.link), modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp))
                }
            }
        }
        var draft by remember { mutableStateOf("") }
        var started by remember { mutableStateOf<String?>(null) }
        var focused by remember { mutableStateOf(false) }
        val add by rememberUpdatedState(onAdd)
        fun commit() { if (draft.isNotBlank()) add(draft.trim(), started ?: nowStamp()); draft = ""; started = null }
        DisposableEffect(Unit) { onDispose { commit() } }
        Margined(Modifier.padding(top = 6.dp), margin = { started?.let { Stamp(it) } }) {
            Box(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                if (draft.isEmpty() && !focused) Text(hint, style = text.copy(color = c.faint))
                BasicTextField(draft, { v -> draft = v; if (started == null && v.isNotBlank()) started = nowStamp() },
                    textStyle = text, cursorBrush = SolidColor(c.link),
                    modifier = Modifier.fillMaxWidth().onFocusChanged { f -> if (focused && !f.isFocused) commit(); focused = f.isFocused })
            }
        }
    }
}

/** The day and the hour a piece was written, on the margin. */
@Composable
private fun Stamp(at: String) {
    val c = M.c
    val dt = runCatching { java.time.LocalDateTime.parse(at.take(16)) }.getOrNull() ?: return
    Column {
        Text(dt.toLocalDate().dayMonthShort(), style = dateStyle(12.5.sp))
        Text(dt.toLocalTime().toString().take(5), style = Type.small.copy(color = c.graphite, fontSize = 12.sp, fontFeatureSettings = "tnum"))
    }
}
