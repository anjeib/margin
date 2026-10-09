package ru.anjeib.margin.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * The top of a card (owner's call, 10 окт): first when, how often, where, with whom — one quiet line, each value a tap
 * that changes it; then a line, and under it the card's stream: the matter's (or idea's) own words, then the comments,
 * each with its day on the margin.
 */

/** One value of the card's top line. */
class MetaItem(val text: AnnotatedString, val style: TextStyle, val onLong: (() -> Unit)? = null, val onClick: () -> Unit)

/** «Пн 12 окт · 18:00 · ↻ каждая пт · ● Личное · Вам от Анны · Ленина, 5» — the same quiet line as under a matter in the lists. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun MetaLine(items: List<MetaItem>, modifier: Modifier = Modifier) {
    if (items.isEmpty()) return
    val c = M.c
    FlowRow(modifier, itemVerticalAlignment = Alignment.CenterVertically) {
        items.forEachIndexed { i, it ->
            if (i > 0) Text("  ·  ", style = Type.small.copy(color = c.faint, fontSize = 14.5.sp))
            Text(it.text, style = it.style, modifier = Modifier.clip(RoundedCornerShape(6.dp))
                .combinedClickable(onClick = it.onClick, onLongClick = it.onLong).padding(vertical = 4.dp, horizontal = 1.dp))
        }
    }
}

/** The styles of the top line: dates and hours in ink (terracotta when late), links petrol, the rest graphite. */
object Meta {
    @Composable fun date(late: Boolean = false) = dateStyle(14.5.sp, late)
    @Composable fun link() = Type.small.copy(color = M.c.link, fontSize = 14.5.sp)
    @Composable fun quiet() = Type.small.copy(color = M.c.graphite, fontSize = 14.5.sp)
    fun plain(t: String) = AnnotatedString(t)
    /** «● Личное» / «● Работа» — the sphere with its coloured dot; a tap switches it. */
    @Composable fun sphere(work: Boolean): AnnotatedString {
        val c = M.c
        return buildAnnotatedString {
            withStyle(SpanStyle(color = if (work) c.link else c.ok, fontSize = 9.sp)) { append("●") }
            append("  ")
            append(if (work) M.s.sphereWork else M.s.spherePersonal)
        }
    }
    /** «Вам от Анны» / «Анне от вас» with the name petrol. */
    @Composable fun promise(parts: Triple<String, String, String>): AnnotatedString {
        val c = M.c
        return buildAnnotatedString {
            append(parts.first)
            withStyle(SpanStyle(color = c.link)) { append(parts.second) }
            append(parts.third)
        }
    }
}
