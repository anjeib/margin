package ru.anjeib.margin.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.layout.layout
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.anjeib.margin.data.dayMonthShort
import ru.anjeib.margin.data.dowFull
import java.time.LocalDate

/**
 * The diary's four sections share one frame (owner, 16 окт: «страница остаётся на месте, а листаются только отдельные
 * элементы»): the date on the margin, the page's name level with the calendar and the search, «личное / работа» always in the
 * same place, the double rule and the margin's line stay put; only what changes — the name, its line, the rows — turns.
 * Inside the frame the pages don't draw their own head or rule.
 */
val LocalFramed = androidx.compose.runtime.staticCompositionLocalOf { false }

/** Which page of the frame is in front, and on which day: what the name and the rows turn by. */
data class FramePage(val section: Int, val day: LocalDate) {
    /** Further in the book: the sections in their order, then the days (all but «Содержание» turn by days). */
    fun order(): Long = SectionOrder.indexOf(section) * 1_000_000L + (if (section != 3) day.toEpochDay() else 0L)
}

/** The frame's head. [sub] — the line under the name for a section (it turns with the name). */
@Composable
fun FrameHead(page: FramePage, vm: MainVm, sub: @Composable (FramePage) -> Unit) {
    val c = M.c
    val s = M.s
    val sphere by vm.diarySphere.collectAsState()
    Column(Modifier.fillMaxWidth().sectionSwipe()) {
        Margined(margin = {
            // The date stays where it is; only its figures change with the day.
            Column(horizontalAlignment = Alignment.End) {
                AnimatedContent(page.day, transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(140)) }, label = "frame-day") { d ->
                    MarginDay(d.dayOfMonth, d.dayMonthShort().substringAfter(' '))
                }
                // Another day: the way back stands under the date, on the margin — the head keeps its height (owner, 17 окт).
                val away = page.section != 3 && page.day != LocalDate.now()
                // It takes no room: drawn under the date, the margin's column keeps the date's own height.
                androidx.compose.animation.AnimatedVisibility(away, Modifier.layout { m, cs -> val p = m.measure(cs); layout(p.width, 0) { p.place(0, 0) } },
                    enter = fadeIn(tween(200)), exit = fadeOut(tween(120))) {
                    androidx.compose.material3.Text(s.todayWordLink, style = Type.small.copy(color = c.link, fontSize = subSize, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium),
                        modifier = Modifier.offset(y = (-6).dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(6.dp)).clickable { vm.pageDay.value = null }.padding(vertical = 4.dp, horizontal = 2.dp))
                }
            }
        }) {
            // The name and its line on the left; on the right the four marks in one even block (owner, 16 окт: «иконки
            // некрасиво расположены и разные по цвету/размеру») — calendar · search over personal · work.
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f).padding(top = 2.dp)) {
                    ContentTurn(page, { it.order() }) { p ->
                        val name = when (p.section) { 0 -> p.day.dowFull(); 1 -> s.diaryTitle; 2 -> s.topicsPage; else -> s.contentsTitle }
                        BasicText(name, style = Type.pageTitle.copy(color = c.ink), maxLines = 1, modifier = Modifier.fillMaxWidth(),
                            autoSize = TextAutoSize.StepBased(minFontSize = 26.sp, maxFontSize = 50.sp, stepSize = 1.sp))
                    }
                    // Room for two lines, so a shorter or longer line doesn't move the rule and the rows under it.
                    Box(Modifier.padding(top = 2.dp).heightIn(min = 42.dp)) { ContentTurn(page, { it.order() }) { p -> Column { sub(p) } } }
                }
                // «Содержание» needs none of them (owner, 16 окт): they fade, but keep their room — nothing in the head moves.
                val shownTools by androidx.compose.animation.core.animateFloatAsState(if (page.section != 3) 1f else 0f, tween(200), label = "tools")
                Box(Modifier.graphicsLayer { alpha = shownTools }) { FrameTools(sphere, enabled = page.section != 3) { vm.diarySphere.value = it } }
            }
        }
        DoubleRule(Modifier.padding(top = 14.dp, bottom = 4.dp))
    }
}

/** The frame's marks, one size and one ink: the chosen one in petrol on a soft round ground. */
@Composable
private fun FrameTools(sphere: String?, enabled: Boolean = true, onSphere: (String?) -> Unit) {
    val t = LocalPageTools.current
    val c = M.c
    val s = M.s
    @Composable fun mark(label: String, on: Boolean, onClick: () -> Unit, glyph: @Composable (androidx.compose.ui.graphics.Color) -> Unit) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(if (on) c.linkSoft else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(enabled = enabled, onClickLabel = label, onClick = onClick), contentAlignment = Alignment.Center) {
            glyph(if (on) c.link else c.ink.copy(alpha = 0.7f))
        }
    }
    Column(Modifier.padding(start = 6.dp)) {
        if (t != null) Row {
            mark(s.calendarTitle, t.monthOpen, t.calendar) { ink ->
                // Something just went to another day: its number stands on the calendar, a tap opens the month there.
                if (t.landed != null && !t.monthOpen) androidx.compose.material3.Text(t.landed.dayOfMonth.toString(),
                    style = Type.small.copy(color = c.accentText, fontSize = 15.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold))
                else CalendarGlyph(ink, 21.dp)
            }
            mark(s.search, t.searchOn, t.search) { ink -> SearchGlyph(ink, 21.dp) }
        }
        Row {
            mark(s.spherePersonal, sphere == "personal", { onSphere(if (sphere == "personal") null else "personal") }) { ink -> HomeGlyph(ink, 21.dp) }
            mark(s.sphereWork, sphere == "work", { onSphere(if (sphere == "work") null else "work") }) { ink -> WorkGlyph(ink, 21.dp) }
        }
    }
}

/**
 * What turns inside the frame: the new comes in a little from the side it lies on and the old fades out — the page itself
 * doesn't move (owner, 16 окт). The ground is clear, so the frame's rule shows through.
 */
@Composable
fun <T> ContentTurn(target: T, order: (T) -> Long, modifier: Modifier = Modifier, content: @Composable (T) -> Unit) {
    val motion = motionOn()
    AnimatedContent(target, modifier, transitionSpec = {
        val fwd = order(targetState) >= order(initialState)
        // One after the other, never over each other (owner, 16 окт: the words «выпрыгивают»): the old goes in a breath, then the new
        // comes in from a little to the side.
        val out = if (motion) 110 else 0
        val inn = if (motion) 240 else 0
        (slideInHorizontally(tween(inn, delayMillis = out, easing = FastOutSlowInEasing)) { (if (fwd) it else -it) / 10 } +
            fadeIn(tween(inn, delayMillis = out))) togetherWith
            (slideOutHorizontally(tween(out, easing = FastOutSlowInEasing)) { (if (fwd) -it else it) / 16 } + fadeOut(tween(out))) using
            androidx.compose.animation.SizeTransform(clip = false) { _, _ -> androidx.compose.animation.core.snap() }
    }, label = "turn") { key -> content(key) }
}

/** The frame's pages: a page in front, under the head, in the frame's own ground. */
@Composable
fun FramedPages(page: FramePage, content: @Composable (FramePage) -> Unit) {
    androidx.compose.runtime.CompositionLocalProvider(LocalFramed provides true) {
        ContentTurn(page, { it.order() }, Modifier.fillMaxSize()) { p -> Box(Modifier.fillMaxSize()) { content(p) } }
    }
}

/** Room at the top of a framed page's list: the first row a little under the double rule. */
val FrameListTop = 6.dp
