package ru.anjeib.margin.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.anjeib.margin.data.dayMonthShort
import ru.anjeib.margin.data.dowFull
import java.time.LocalDate

// ---------------- the cards as pages of the planner (owner, 14 окт; design/v9/v9-card-deal|note|topic.png) ----------------

/**
 * A card — a matter, a note, a topic — opened as a page of the same book: it lies over the diary's page, the index stays on
 * the right; back on the margin at the top, [trailing] (a topic's ⋯) on the right. No sheet rising from below.
 */
@Composable
fun CardPage(onClose: () -> Unit, trailing: (@Composable () -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val c = M.c
    val s = M.s
    BackHandler(onBack = onClose)
    Column(Modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding().imePadding()) {
        // Under the printed frame's top line («MARGIN»), as a page of the diary starts.
        Row(Modifier.fillMaxWidth().screenRule().padding(top = 18.dp).height(40.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(MarginW), contentAlignment = Alignment.Center) {
                Box(Modifier.size(40.dp).clip(CircleShape).clickable(onClickLabel = s.backWord, onClick = onClose), contentAlignment = Alignment.Center) {
                    BackGlyph(c.ink.copy(alpha = 0.8f), 21.dp)
                }
            }
            Spacer(Modifier.weight(1f))
            trailing?.invoke()
        }
        content()
    }
}

/**
 * The card's «когда» on its margin, the same as a day of the diary (owner, 14 окт): the day's number large, the month in
 * spaced capitals under it, then a quiet line — the hour, or the weekday. Late — a terracotta highlighter over the day.
 * [prefix] stands over it in italic («с», «9 –»). Each part is a tap when it can be changed.
 */
@Composable
fun CardMargin(day: LocalDate?, hour: String? = null, late: Boolean = false, prefix: String? = null, under: String? = null,
               onDay: (() -> Unit)? = null, onHour: (() -> Unit)? = null) {
    val c = M.c
    val s = M.s
    val ink = pencilInk
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 2.dp)) {
        if (prefix != null) Text(prefix, style = Type.italic.copy(color = c.ink.copy(alpha = 0.75f), fontSize = 14.sp, lineHeight = 16.sp))
        if (day == null) MarginNote(s.noDateShort, modifier = Modifier.clip(RoundedCornerShape(6.dp)).then(if (onDay != null) Modifier.clickable(onClickLabel = s.dateWord, onClick = onDay) else Modifier).padding(vertical = 4.dp))
        else Box(Modifier.clip(RoundedCornerShape(6.dp)).then(if (onDay != null) Modifier.clickable(onClickLabel = s.dateWord, onClick = onDay) else Modifier)
            .drawBehind { if (late) markerBand(4.dp.toPx(), size.width - 4.dp.toPx(), 4.dp.toPx(), size.height - 2.dp.toPx(), ink, day.hashCode()) }
            .padding(horizontal = 2.dp)) {
            MarginDay(day.dayOfMonth, day.dayMonthShort().substringAfter(' '))
        }
        val quiet = hour ?: under
        if (quiet != null || onHour != null) MarginNote(quiet ?: s.addTime,
            modifier = Modifier.padding(top = 2.dp).clip(RoundedCornerShape(6.dp)).then(if (onHour != null) Modifier.clickable(onClickLabel = s.fieldTime, onClick = onHour) else Modifier).padding(vertical = 3.dp))
    }
}

/** A window of days on the margin: «9 –» over the last day. */
fun windowPrefix(from: LocalDate?, due: LocalDate?): String? =
    if (from != null && due != null && from.isBefore(due)) "${from.dayOfMonth}${if (from.month != due.month) " " + from.dayMonthShort().substringAfter(' ') else ""} –" else null

/** The weekday under a day on the margin: «суббота». */
fun LocalDate.weekdayWord(): String = dowFull().lowercase()
