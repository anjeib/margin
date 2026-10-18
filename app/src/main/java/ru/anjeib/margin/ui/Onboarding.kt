package ru.anjeib.margin.ui

import androidx.compose.foundation.layout.heightIn

import androidx.compose.foundation.verticalScroll

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Search

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.layout
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import kotlinx.coroutines.launch
import ru.anjeib.margin.data.questionRow
import ru.anjeib.margin.data.shortLabel

/**
 * Getting to know Margin (owner, 18 окт: «все необходимое покажи на нескольких экранах … короткими анимациями по базовым
 * вещам»): five short scenes drawn in the app's own look — a phrase becoming a matter, the voice, the knot, «ждём», a topic —
 * then the permissions and the morning summary. A swipe or «Дальше» turns the page; «Пропустить» goes to the last one.
 * The scenes loop while their page is shown; with the system's animations off they stand in their final frame.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun Onboarding(vm: MainVm, onDone: () -> Unit) {
    val c = M.c
    val s = M.s
    val scenes = 6
    val pager = androidx.compose.foundation.pager.rememberPagerState { scenes + 1 }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val last = pager.currentPage == scenes
    Column(Modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Margin", style = Type.label.copy(color = c.accentText, fontSize = 13.sp), modifier = Modifier.weight(1f))
            if (!last) Text(s.obSkip, style = Type.small.copy(color = c.graphite, fontSize = 14.sp),
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { scope.launch { pager.animateScrollToPage(scenes) } }.padding(12.dp))
        }
        androidx.compose.foundation.pager.HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth()) { page ->
            if (page < scenes) ScenePage(page, pager.currentPage == page)
            else SetupPage(vm)
        }
        // Where you are: one dot per page, the current one in sea.
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                repeat(scenes + 1) { i ->
                    Box(Modifier.size(if (i == pager.currentPage) 8.dp else 6.dp).clip(CircleShape).background(if (i == pager.currentPage) c.sea else c.hairline))
                }
            }
            Pill(if (last) s.begin else s.obNext, filled = true, onClick = {
                if (last) onDone() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
            })
        }
    }
}

/** A scene: its title, one line under it, and the little animated page. */
@Composable
private fun ScenePage(i: Int, shown: Boolean) {
    val c = M.c
    val s = M.s
    val (title, line) = s.obScenes[i]
    val motion = motionOn()
    // The scene's clock, 0..1, looping while the page is in front; without motion — the last frame.
    val t = remember { androidx.compose.animation.core.Animatable(1f) }
    androidx.compose.runtime.LaunchedEffect(shown, motion) {
        if (!shown || !motion) { t.snapTo(1f); return@LaunchedEffect }
        while (true) {
            t.snapTo(0f)
            t.animateTo(1f, androidx.compose.animation.core.tween(SceneMs[i], easing = androidx.compose.animation.core.LinearEasing))
            kotlinx.coroutines.delay(1400)
        }
    }
    // The words and the scene stand together in the middle of the page (18 окт: a gap under the scene).
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp), verticalArrangement = Arrangement.Center) {
        Text(title, style = Type.pageTitle.copy(color = c.ink, fontSize = 32.sp, lineHeight = 36.sp))
        Spacer(Modifier.height(10.dp))
        Text(line, style = Type.body.copy(color = c.graphite, fontSize = 16.sp, lineHeight = 23.sp))
        Spacer(Modifier.height(28.dp))
        Box(Modifier.fillMaxWidth().height(370.dp).clip(RoundedCornerShape(22.dp)).background(c.card)
            .then(Modifier.padding(vertical = 18.dp))) {
            when (i) {
                0 -> SceneTour(t.value)
                1 -> SceneVoiceDeals(t.value)
                2 -> SceneVoiceTopic(t.value)
                3 -> SceneWait(t.value)
                4 -> SceneDiary(t.value)
                else -> ScenePeople(t.value)
            }
        }
    }
}

private val SceneMs = intArrayOf(12000, 17000, 14000, 7500, 8000, 4800)

/** [t] mapped onto [a]..[b] of the scene's time, clamped to 0..1. */
private fun seg(t: Float, a: Float, b: Float) = ((t - a) / (b - a)).coerceIn(0f, 1f)

/** One row of the page, as in the feed: the hour on the margin, the knot on the rule, the words, a quiet line under them. */
@Composable
private fun SceneRow(hour: String?, title: String, sub: String? = null, subColor: androidx.compose.ui.graphics.Color? = null,
                     done: Float = 0f, late: Boolean = false, alpha: Float = 1f, offsetY: Float = 0f, knotPulse: Float = 0f,
                     /** 0..1 — how much of its height the row takes: a new row parts the ones around it as it comes. */ grow: Float = 1f) {
    val c = M.c
    val ring = if (late && done < 1f) c.accent else c.sea
    Row(Modifier.fillMaxWidth().layout { m, cs -> val p = m.measure(cs); layout(p.width, (p.height * grow).toInt()) { p.place(0, 0) } }
        .graphicsLayerCompat(alpha, offsetY).padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.width(54.dp).padding(end = 8.dp, top = 3.dp), contentAlignment = Alignment.TopEnd) {
            if (hour != null) Text(hour, style = Type.markL.copy(color = c.ink, fontSize = 15.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Normal))
        }
        // The knot on the rule (the rule itself is drawn by the scene behind the rows).
        androidx.compose.foundation.Canvas(Modifier.padding(top = 6.dp).size(14.dp)) {
            val r = size.minDimension / 2 - 1.dp.toPx()
            if (knotPulse > 0f) drawCircle(c.sea.copy(alpha = 0.25f * (1f - knotPulse)), radius = r + 10.dp.toPx() * knotPulse)
            drawCircle(c.card, radius = r + 2.dp.toPx())
            if (done > 0f) drawCircle(ring.copy(alpha = done), radius = r)
            drawCircle(ring, radius = r, style = androidx.compose.ui.graphics.drawscope.Stroke(1.3.dp.toPx()))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).padding(end = 16.dp)) {
            Box {
                Text(title, style = Type.body.copy(fontFamily = rowFamily, fontSize = rowSize, lineHeight = rowLine, color = if (done >= 1f) c.graphite else c.ink))
                if (done > 0f) androidx.compose.foundation.Canvas(Modifier.matchParentSize()) {
                    val y = 23.sp.toPx() / 2 + 1.dp.toPx()
                    drawLine(c.graphite, androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Offset(size.width * done, y), 1.3.dp.toPx())
                }
            }
            if (sub != null) Text(sub, style = Type.meta.copy(color = subColor ?: c.graphite), modifier = Modifier.padding(top = 2.dp))
        }
    }
}

private fun Modifier.graphicsLayerCompat(alpha: Float, dy: Float) = this.then(androidx.compose.ui.Modifier.graphicsLayer { this.alpha = alpha; translationY = dy })

/** The margin's rule behind a scene's rows. */
@Composable
private fun SceneRule(content: @Composable () -> Unit) {
    val c = M.c
    Box(Modifier.fillMaxWidth().drawBehind {
        val x = 54.dp.toPx() + 7.dp.toPx()
        drawLine(c.ink.copy(alpha = 0.55f), androidx.compose.ui.geometry.Offset(x, 0f), androidx.compose.ui.geometry.Offset(x, size.height), 1.dp.toPx())
    }) { content() }
}

/** The field at the bottom of a scene: the words typed so far, a caret, the send or the microphone. */
@Composable
private fun SceneField(text: String, caret: Boolean, mic: Boolean, micPulse: Float = 0f, hint: String = "",
                       /** Recording: the timer and a little wave in place of the words. */ rec: Float = -1f) {
    // While recording the button is «✓» — it stops and puts the words into the field, as in the app.
    val c = M.c
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp).clip(RoundedCornerShape(24.dp)).background(c.bg).padding(start = 12.dp, end = 5.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically) {
        ClipGlyph(c.graphite, 16.dp)
        Spacer(Modifier.width(10.dp))
        if (rec >= 0f) {
            Text("● 0:0" + (1 + (rec * 4).toInt()), style = Type.small.copy(color = c.accentText, fontSize = 14.sp))
            Spacer(Modifier.width(10.dp))
            androidx.compose.foundation.Canvas(Modifier.weight(1f).height(20.dp)) {
                val n = 22; val w = size.width / n
                for (k in 0 until n) {
                    val h = (0.2f + 0.8f * kotlin.math.abs(kotlin.math.sin(k * 1.3f + rec * 40f))) * size.height
                    drawRoundRect(c.sea.copy(alpha = 0.7f), androidx.compose.ui.geometry.Offset(k * w + w * 0.25f, (size.height - h) / 2),
                        androidx.compose.ui.geometry.Size(w * 0.5f, h), androidx.compose.ui.geometry.CornerRadius(w))
                }
            }
        } else
        Text(if (text.isEmpty()) hint else text + if (caret) "|" else "", maxLines = 2,
            style = Type.small.copy(color = if (text.isEmpty()) c.graphite else c.ink, fontSize = 14.5.sp), modifier = Modifier.weight(1f))
        Box(Modifier.size(38.dp).drawBehind {
            if (micPulse > 0f) drawCircle(c.sea.copy(alpha = 0.3f * (1f - micPulse)), radius = size.minDimension / 2 + 14.dp.toPx() * micPulse)
        }.clip(CircleShape).background(c.sea), contentAlignment = Alignment.Center) {
            if (rec >= 0f) Text("✓", style = Type.body.copy(color = c.bg, fontSize = 17.sp))
            else if (mic) MicGlyph(c.bg, 18.dp) else Text("↑", style = Type.body.copy(color = c.bg, fontSize = 18.sp))
        }
    }
}

/**
 * A touch, drawn small and exact (owner, 18 окт: «тап аккуратнее, а не серым большим кругом»): a sea dot where the finger
 * lands and a thin ring spreading from it, gone in a third of a second. [times] — the scene's moments of the taps.
 */
@Composable
private fun Tap(x: androidx.compose.ui.unit.Dp, y: androidx.compose.ui.unit.Dp, t: Float, vararg times: Float) {
    val c = M.c
    val k = times.map { (t - it) / 0.03f }.firstOrNull { it in 0f..1f } ?: return
    androidx.compose.foundation.Canvas(Modifier.offset(x - 24.dp, y - 24.dp).size(48.dp)) {
        val o = center
        drawCircle(c.sea.copy(alpha = 0.85f * (1f - k)), radius = 6.dp.toPx(), center = o)
        drawCircle(c.sea.copy(alpha = 0.9f * (1f - k)), radius = (8f + 14f * k).dp.toPx(), center = o, style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx()))
    }
}

/**
 * One saying, as the app does it: a tap on the microphone, the words heard appear over the field as you speak, a tap on «✓»
 * puts them into the field, a tap on «↑» sends; Margin shows how it read them, and the row lands. Everything inside [a]..[b].
 */
private class Say(t: Float, a: Float, b: Float, val text: String) {
    val u = if (t < a) -1f else seg(t, a, b)
    val on = t in a..b
    val tapMic = a + (b - a) * 0.04f
    val tapStop = a + (b - a) * 0.5f
    val tapSend = a + (b - a) * 0.64f
    val rec = if (u in 0.06f..0.5f) seg(u, 0.06f, 0.5f) else -1f
    val heard = if (rec >= 0f) text.take((text.length * seg(u, 0.1f, 0.46f)).toInt()) else ""
    val inField = if (u in 0.5f..0.66f) text else ""
    val preview = if (u < 0f) 0f else seg(u, 0.68f, 0.72f) * (1f - seg(u, 0.86f, 0.9f))
    /** The row in the day: 0 before, 1 from the end of the saying on. */
    val land = if (t > b) 1f else if (u < 0f) 0f else seg(u, 0.86f, 0.96f)
}

/** The bottom of a voice scene: the words heard, or how Margin read them, over the field; the field itself. */
@Composable
private fun VoiceBottom(say: Say?, previewTitle: String, previewLine: String, hint: String, chips: @Composable (() -> Unit)? = null) {
    val c = M.c
    Column(Modifier.fillMaxWidth()) {
        chips?.invoke()
        if (say != null && say.heard.isNotEmpty()) Text(say.heard + "…", style = Type.body.copy(fontFamily = rowFamily, fontSize = 15.sp, lineHeight = 21.sp, color = c.ink),
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.bg).padding(horizontal = 12.dp, vertical = 8.dp))
        if (say != null && say.preview > 0f) Column(Modifier.padding(horizontal = 18.dp, vertical = 8.dp).fillMaxWidth().graphicsLayer { alpha = say.preview }
            .clip(RoundedCornerShape(12.dp)).border(1.dp, c.sea.copy(alpha = 0.4f), RoundedCornerShape(12.dp)).background(c.bg).padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(previewTitle, style = Type.body.copy(fontFamily = rowFamily, fontSize = 15.sp, color = c.ink))
            Text(previewLine, style = Type.meta.copy(color = c.graphite), modifier = Modifier.padding(top = 2.dp))
        }
        val rec = say?.rec ?: -1f
        val field = say?.inField.orEmpty()
        SceneField(field, caret = false, mic = field.isEmpty(), micPulse = if (rec >= 0f) (rec * 18f) % 1f else 0f, hint = hint, rec = rec)
    }
}

/** Where the field's button and paperclip are, in a scene [w] × [h]. */
private fun micX(w: androidx.compose.ui.unit.Dp) = w - 14.dp - 5.dp - 19.dp
private fun micY(h: androidx.compose.ui.unit.Dp) = h - 5.dp - 19.dp
private val ClipX = 14.dp + 12.dp + 8.dp
/** The knot on the rule, its x. */
private val KnotX = 54.dp + 7.dp

/**
 * The first page — the app at a glance (owner, 18 окт: «всё быстро мелькает, сделай экраном с подсказками при тапе»): the head,
 * the week, the feed and the field stand still; a small sea dot marks each thing one can tap. A tap says under it what the
 * thing does and does it here — the diary opens, a day is picked, the month unfolds.
 */
@Composable
private fun SceneTour(@Suppress("UNUSED_PARAMETER") t: Float) {
    val c = M.c
    val s = M.s
    val steps = s.obTour
    var sel by remember { mutableStateOf(-1) }
    var seen by remember { mutableStateOf(emptySet<Int>()) }
    var journal by remember { mutableStateOf(false) }
    var picked by remember { mutableStateOf(6) }
    var month by remember { mutableStateOf(false) }
    fun tap(i: Int) { sel = i; seen = seen + i }
    val monthH by androidx.compose.animation.core.animateFloatAsState(if (month) 1f else 0f, androidx.compose.animation.core.tween(260), label = "month")
    @Composable fun Dot(i: Int, m: Modifier = Modifier) {
        if (i !in seen) Box(m.size(7.dp).clip(CircleShape).background(c.sea))
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().height(40.dp).padding(start = 14.dp, end = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (journal) s.journalTitle else s.kindDeals, style = Type.pageTitle.copy(color = c.ink, fontSize = 19.sp, lineHeight = 20.sp))
                Text(if (journal) s.journalSub else s.dealsSub, style = Type.meta.copy(color = c.graphite, fontSize = 10.sp, lineHeight = 12.sp))
            }
            val marks = listOf<Pair<Int, @Composable (androidx.compose.ui.graphics.Color) -> Unit>>(
                0 to { FilterGlyph(it, 17.dp) }, 1 to { PersonGlyph(it, 17.dp) }, 2 to { SearchGlyph(it, 17.dp) },
                3 to { DealGlyph(it, 18.dp) }, 3 to { DiaryGlyph(it, 17.dp) }, 4 to { Box(Modifier.size(18.dp), contentAlignment = Alignment.Center) { MenuGlyph(it) } })
            marks.forEachIndexed { k, (i, g) ->
                val lit = when (k) { 3 -> !journal; 4 -> journal; else -> sel == i }
                Box(Modifier.size(32.dp)) {
                    Box(Modifier.matchParentSize().clip(CircleShape).background(if (lit) c.linkSoft else androidx.compose.ui.graphics.Color.Transparent)
                        .clickable { tap(i); if (k == 3) journal = false; if (k == 4) journal = true }, contentAlignment = Alignment.Center) { g(if (lit) c.link else c.ink.copy(alpha = 0.7f)) }
                    if (k != 3) Dot(i, Modifier.align(Alignment.TopEnd))
                }
            }
        }
        Row(Modifier.fillMaxWidth().height(32.dp).padding(start = 14.dp, end = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(50.dp)) {
                Text("ОКТ " + if (month) "▴" else "▾", style = Type.meta.copy(color = c.ink, fontSize = 11.sp, letterSpacing = 1.sp),
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { tap(6); month = !month }.padding(4.dp))
                Dot(6, Modifier.align(Alignment.TopEnd).padding(end = 6.dp))
            }
            Box(Modifier.weight(1f)) {
                Row(Modifier.fillMaxWidth()) {
                    listOf("пн", "вт", "ср", "чт", "пт", "сб", "вс").forEachIndexed { k, d ->
                        Box(Modifier.weight(1f).padding(horizontal = 1.dp).clip(RoundedCornerShape(6.dp)).background(if (k == picked) c.link else androidx.compose.ui.graphics.Color.Transparent)
                            .clickable { tap(5); picked = k; month = false }.padding(vertical = 3.dp), contentAlignment = Alignment.Center) {
                            Text(d, style = Type.meta.copy(color = if (k == picked) c.bg else c.ink, fontSize = 11.sp))
                            if (k == 2) Dot(5, Modifier.align(Alignment.TopEnd))
                        }
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp).height(1.dp).background(c.ink.copy(alpha = 0.18f)))
        Column(Modifier.fillMaxWidth().clipToBounds().layout { m, cs -> val p = m.measure(cs); layout(p.width, (p.height * monthH).toInt()) { p.place(0, 0) } }
            .padding(start = 64.dp, end = 10.dp, top = 2.dp, bottom = 4.dp)) {
            (0 until 3).forEach { r ->
                Row { (0 until 7).forEach { k -> val d = 12 + r * 7 + k; Text(if (d > 31) "" else "$d", style = Type.meta.copy(color = if (d == 12 + picked) c.link else c.ink, fontSize = 10.sp), modifier = Modifier.weight(1f).clickable { if (d <= 31 && d < 19) { picked = d - 12; month = false } }, textAlign = androidx.compose.ui.text.style.TextAlign.Center) } }
            }
        }
        SceneRule {
            Column {
                Text(if (picked == 6) s.obToday else s.obDayOf(picked), style = Type.meta.copy(color = c.link, fontSize = 11.sp), modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                if (journal) SceneNote("08:05", s.obDiary1) else {
                    SceneRow(null, s.obTourRow1, sub = s.obTourRow1Sub, late = true, subColor = c.accentText)
                    SceneRow("11:00", s.obTourRow2)
                }
            }
        }
        Spacer(Modifier.weight(1f))
        // What the tapped thing does; before any tap — where to begin.
        androidx.compose.animation.Crossfade(sel, label = "hint") { k ->
            Text(if (k < 0) s.obTourStart else steps[k], style = Type.small.copy(color = if (k < 0) c.graphite else c.ink, fontSize = 13.5.sp, lineHeight = 18.sp),
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp).fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp)).background(if (k < 0) androidx.compose.ui.graphics.Color.Transparent else c.linkSoft).padding(horizontal = 12.dp, vertical = 8.dp))
        }
        Box(Modifier.fillMaxWidth().clickable { tap(7) }) {
            SceneField("", caret = false, mic = true, hint = s.obHint)
            Dot(7, Modifier.align(Alignment.TopEnd).padding(end = 18.dp))
        }
    }
}

/** Several matters by voice (owner, 18 окт): a plain one, one with a file and a person, then one is done. */
@Composable
private fun SceneVoiceDeals(t: Float) {
    val c = M.c
    val s = M.s
    val one = Say(t, 0.02f, 0.36f, s.obSay1)
    val clip = 0.4f
    val two = Say(t, 0.43f, 0.8f, s.obSay2)
    val done = seg(t, 0.86f, 0.92f)
    val fileChip = if (t in clip + 0.02f..0.43f + (0.8f - 0.43f) * 0.66f) 1f else 0f
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = maxWidth; val h = maxHeight
        Column(Modifier.fillMaxSize()) {
            SceneRule {
                Column {
                    Text(s.obToday, style = Type.meta.copy(color = c.link), modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    SceneRow(null, s.obRow2a, sub = s.obRow2aSub, subColor = c.link, alpha = two.land, offsetY = (1f - two.land) * 24f, grow = two.land)
                    SceneRow("09:30", s.obRow1, done = done)
                    SceneRow("11:00", s.obRow1a, sub = s.obRow1aSub, subColor = c.link, alpha = one.land, offsetY = (1f - one.land) * 24f, grow = one.land)
                }
            }
            Spacer(Modifier.weight(1f))
            val say = if (two.u >= 0f && t <= 0.8f) two else if (one.on) one else null
            VoiceBottom(say, if (say === two) s.obRow2a else s.obRow1a, if (say === two) s.obPrev2 else s.obPrev1, s.obHint, chips = {
                Row(Modifier.padding(start = 18.dp, bottom = 6.dp).graphicsLayer { alpha = fileChip }, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.clip(RoundedCornerShape(12.dp)).background(c.bg).padding(horizontal = 9.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        ClipGlyph(c.graphite, 12.dp); Spacer(Modifier.width(4.dp)); Text(s.obAttachFile, style = Type.meta.copy(color = c.ink))
                    }
                }
            })
        }
        Tap(micX(w), micY(h), t, one.tapMic, one.tapStop, one.tapSend, two.tapMic, two.tapStop, two.tapSend)
        Tap(ClipX, micY(h), t, clip)
        // «Планёрка» is the second row by then: under the day's line and the new matter with its line under it.
        Tap(KnotX, 22.dp + 53.dp + 13.dp, t, 0.86f)
    }
}

/** A topic by voice, and a matter in it by voice (owner, 18 окт). */
@Composable
private fun SceneVoiceTopic(t: Float) {
    val c = M.c
    val s = M.s
    val one = Say(t, 0.02f, 0.4f, s.obSayTopic)
    val openAt = 0.48f
    val win = seg(t, openAt + 0.02f, openAt + 0.08f)
    val two = Say(t, 0.56f, 0.94f, s.obSayInTopic)
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = maxWidth; val h = maxHeight
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                SceneRule {
                    Column {
                        Text(s.obToday, style = Type.meta.copy(color = c.link), modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        SceneRow("09:30", s.obRow1)
                        // The topic's row: on the block's tint, «# Ремонт ванной», «тема · вся тема ›».
                        Row(Modifier.fillMaxWidth().layout { m, cs -> val p = m.measure(cs); layout(p.width, (p.height * one.land).toInt()) { p.place(0, 0) } }
                            .graphicsLayer { alpha = one.land }.padding(vertical = 4.dp)) {
                            Spacer(Modifier.width(KnotX + 10.dp))
                            Column(Modifier.weight(1f).padding(end = 12.dp).clip(RoundedCornerShape(12.dp)).background(c.sea.copy(alpha = 0.07f)).padding(horizontal = 10.dp, vertical = 8.dp)) {
                                Row { Text("# ", style = Type.body.copy(fontFamily = rowFamily, fontSize = rowSize, color = c.link)); Text(s.obTopic, style = Type.body.copy(fontFamily = rowFamily, fontSize = rowSize, color = c.ink)) }
                                Text(s.obTopicNewSub, style = Type.meta.copy(color = c.graphite), modifier = Modifier.padding(top = 2.dp))
                            }
                        }
                    }
                }
                // The topic's window over the feed, as in the app; its matter comes in by voice.
                if (win > 0f) Column(Modifier.fillMaxSize().padding(horizontal = 4.dp).background(c.card).graphicsLayer { alpha = win; translationY = (1f - win) * 40f }
                    .clip(RoundedCornerShape(16.dp)).border(1.dp, c.hairline, RoundedCornerShape(16.dp)).background(c.bg).padding(14.dp)) {
                    Text(s.obTopicHead(if (two.land >= 1f) 1 else 0), style = Type.italic.copy(color = c.graphite, fontSize = 13.sp))
                    Row(Modifier.padding(top = 6.dp)) { Text("# ", style = Type.body.copy(fontFamily = rowFamily, fontSize = 17.sp, color = c.link)); Text(s.obTopic, style = Type.body.copy(fontFamily = rowFamily, fontSize = 17.sp, color = c.ink)) }
                    Box(Modifier.padding(vertical = 8.dp).fillMaxWidth().height(1.dp).background(c.hairline))
                    Text(s.dealsBadge, style = Type.small.copy(color = c.ink, fontSize = 12.sp, letterSpacing = 1.sp))
                    Row(Modifier.padding(top = 8.dp).graphicsLayer { alpha = two.land; translationY = (1f - two.land) * 16f }, verticalAlignment = Alignment.Top) {
                        Box(Modifier.padding(top = 4.dp).size(12.dp).clip(CircleShape).border(1.3.dp, c.sea, CircleShape))
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(s.obInTopicRow, style = Type.body.copy(fontFamily = rowFamily, fontSize = 15.sp, color = c.ink))
                            Text(s.obInTopicSub, style = Type.meta.copy(color = c.graphite))
                        }
                    }
                }
            }
            val say = if (two.u >= 0f) two else if (one.on) one else null
            VoiceBottom(say, if (say === two) s.obInTopicRow else "# " + s.obTopic, if (say === two) s.obPrevInTopic else s.obPrevTopic,
                if (win > 0f) s.topicWriteHint else s.obHint)
        }
        Tap(micX(w), micY(h), t, one.tapMic, one.tapStop, one.tapSend, two.tapMic, two.tapStop, two.tapSend)
        // «вся тема ›» under the topic's name.
        Tap(KnotX + 10.dp + 10.dp + 110.dp, 22.dp + 35.dp + 34.dp, t, openAt)
    }
}

/** «Ждём и обещали» by voice: the words make a wait; on its day the reminder comes. */
@Composable
private fun SceneWait(t: Float) {
    val c = M.c
    val s = M.s
    val one = Say(t, 0.02f, 0.62f, s.obSayWait)
    val bell = seg(t, 0.72f, 0.8f)
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = maxWidth; val h = maxHeight
        Column(Modifier.fillMaxSize()) {
            SceneRule {
                Column {
                    SceneRow(null, s.obWait1, sub = s.obWait1Sub, alpha = one.land, offsetY = (1f - one.land) * 24f, grow = one.land)
                    SceneRow(null, s.obWait2, sub = s.obWait2Sub)
                }
            }
            Spacer(Modifier.weight(1f))
            if (bell > 0f) Column(Modifier.padding(horizontal = 14.dp, vertical = 6.dp).fillMaxWidth().graphicsLayer { alpha = bell; translationY = (1f - bell) * -24f }
                .clip(RoundedCornerShape(16.dp)).background(c.bg).padding(14.dp)) {
                Text("Margin · " + s.obFriday, style = Type.meta.copy(color = c.graphite))
                Text(s.obRemind, style = Type.body.copy(fontSize = 15.sp, color = c.ink), modifier = Modifier.padding(top = 4.dp))
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    Text(s.obGot, style = Type.meta.copy(color = c.link)); Text(s.obTomorrowBtn, style = Type.meta.copy(color = c.link))
                }
            }
            else VoiceBottom(if (one.on) one else null, s.obWait1, s.obPrevWait, s.obHint)
        }
        Tap(micX(w), micY(h), t, one.tapMic, one.tapStop, one.tapSend)
    }
}

/** «Дневник» by voice: the book in the head opens it, the words said land under the day with their hour. */
@Composable
private fun SceneDiary(t: Float) {
    val c = M.c
    val s = M.s
    val openAt = 0.06f
    val diary = t > openAt + 0.02f
    val one = Say(t, 0.14f, 0.86f, s.obDiaryNew)
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = maxWidth; val h = maxHeight
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.padding(start = 18.dp, end = 10.dp).height(36.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (diary) s.journalTitle else s.kindDeals, style = Type.pageTitle.copy(color = c.ink, fontSize = 22.sp), modifier = Modifier.weight(1f))
                Box(Modifier.size(32.dp).clip(CircleShape).background(if (!diary) c.linkSoft else androidx.compose.ui.graphics.Color.Transparent), contentAlignment = Alignment.Center) { DealGlyph(if (!diary) c.link else c.ink.copy(alpha = 0.7f), 18.dp) }
                Box(Modifier.size(32.dp).clip(CircleShape).background(if (diary) c.linkSoft else androidx.compose.ui.graphics.Color.Transparent), contentAlignment = Alignment.Center) { DiaryGlyph(if (diary) c.link else c.ink.copy(alpha = 0.7f), 17.dp) }
            }
            Spacer(Modifier.height(6.dp))
            SceneRule {
                Column {
                    Text(s.obToday, style = Type.meta.copy(color = c.link), modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    if (diary) {
                        SceneNote("08:05", s.obDiary1)
                        SceneNote("09:40", s.obDiaryNew, alpha = one.land, grow = one.land)
                    } else {
                        SceneRow("09:30", s.obRow1)
                        SceneRow("11:00", s.obRow1a)
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            VoiceBottom(if (one.on) one else null, s.obDiaryNew.take(28) + "…", s.obPrevDiary, if (diary) s.obDiaryHint else s.obHint)
        }
        Tap(w - 10.dp - 16.dp, 18.dp, t, openAt)
        Tap(micX(w), micY(h), t, one.tapMic, one.tapStop, one.tapSend)
    }
}

/** A note of the diary: its hour on the margin, the words — no knot, nothing to tick. */
@Composable
private fun SceneNote(hour: String, text: String, alpha: Float = 1f, grow: Float = 1f) {
    val c = M.c
    Row(Modifier.fillMaxWidth().layout { m, cs -> val p = m.measure(cs); layout(p.width, (p.height * grow).toInt()) { p.place(0, 0) } }
        .graphicsLayer { this.alpha = alpha }.padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.width(54.dp).padding(end = 8.dp, top = 2.dp), contentAlignment = Alignment.TopEnd) {
            Text(hour, style = Type.markL.copy(color = c.ink, fontSize = 15.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Normal))
        }
        Spacer(Modifier.width(26.dp))
        Text(text, style = Type.body.copy(fontFamily = rowFamily, fontSize = rowSize, lineHeight = rowLine, color = c.ink), modifier = Modifier.weight(1f).padding(end = 16.dp))
    }
}

/** «Контакты»: a name under a matter is a link — its card holds the phone and every matter with that person. */
@Composable
private fun ScenePeople(t: Float) {
    val c = M.c
    val s = M.s
    val card = seg(t, 0.3f, 0.42f)
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
        SceneRule {
            Column {
                SceneRow(null, s.obWait1, sub = s.obWait1Sub)
                SceneRow("14:00", s.obPeopleRow, sub = s.obPeopleRowSub, subColor = c.link)
            }
        }
        // The person's card slides up over the day.
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 14.dp).graphicsLayer { alpha = card; translationY = (1f - card) * 120f }
            .clip(RoundedCornerShape(18.dp)).background(c.bg).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(38.dp).clip(CircleShape).background(c.linkSoft), contentAlignment = Alignment.Center) { Text("М", style = Type.body.copy(color = c.link, fontSize = 17.sp)) }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(s.obPeopleName, style = Type.pageTitle.copy(color = c.ink, fontSize = 22.sp))
                    Text(s.obPeopleOrg, style = Type.meta.copy(color = c.graphite))
                }
            }
            Text(s.obPeoplePhone, style = Type.small.copy(color = c.link, fontSize = 14.sp), modifier = Modifier.padding(top = 10.dp))
            s.obPeopleDeals.forEach { d -> Text("○  $d", style = Type.body.copy(fontFamily = rowFamily, fontSize = 15.sp, color = c.ink), modifier = Modifier.padding(top = 8.dp)) }
        }
        // A tap on «Маша» under the second matter.
        Tap(KnotX + 12.dp + 14.dp + 16.dp, 53.dp + 46.dp, t, 0.2f)
    }
}

/** The last page: the permissions the phone asks for itself, and the morning summary's hour. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun SetupPage(vm: MainVm) {
    val c = M.c
    val s = M.s
    val ctx = androidx.compose.ui.platform.LocalContext.current
    fun has(p: String) = androidx.core.content.ContextCompat.checkSelfPermission(ctx, p) == android.content.pm.PackageManager.PERMISSION_GRANTED
    // A pressed button shows what happened — otherwise nobody knows whether it worked.
    var notifOk by remember { mutableStateOf(Build.VERSION.SDK_INT >= 33 && has(Manifest.permission.POST_NOTIFICATIONS)) }
    var calOk by remember { mutableStateOf(has(Manifest.permission.READ_CALENDAR)) }
    var micOk by remember { mutableStateOf(has(Manifest.permission.RECORD_AUDIO)) }
    val notif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> notifOk = ok }
    val mic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> micOk = ok }
    val cal = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        calOk = ok; vm.updateSettings { it.copy(calendar = ok) }; vm.refreshCalendar()
    }
    Column(Modifier.fillMaxSize().verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(horizontal = 24.dp, vertical = 28.dp)) {
        Text(s.obSetupTitle, style = Type.pageTitle.copy(color = c.ink, fontSize = 32.sp, lineHeight = 36.sp))
        Spacer(Modifier.height(12.dp))
        Text(s.welcomeText, style = Type.body.copy(color = c.graphite, fontSize = 16.sp, lineHeight = 23.sp))
        Spacer(Modifier.height(26.dp))
        Text(s.ob4Text, style = Type.small.copy(color = c.graphite, fontSize = 14.sp, lineHeight = 20.sp))
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Pill(if (micOk) "✓ " + s.obMic else s.obMic, accent = !micOk, onClick = if (micOk) null else ({ mic.launch(Manifest.permission.RECORD_AUDIO) }))
            if (Build.VERSION.SDK_INT >= 33) Pill(if (notifOk) "✓ " + s.notifications else s.notifications, accent = !notifOk,
                onClick = if (notifOk) null else ({ notif.launch(Manifest.permission.POST_NOTIFICATIONS) }))
            Pill(if (calOk) "✓ " + s.calendar else s.calendar, accent = !calOk,
                onClick = if (calOk) null else ({ cal.launch(Manifest.permission.READ_CALENDAR) }))
        }
        // The morning summary is chosen here, with the notifications it needs.
        Spacer(Modifier.height(22.dp))
        Text(s.obBrief, style = Type.small.copy(color = c.graphite, fontSize = 14.sp))
        Spacer(Modifier.height(8.dp))
        val st by vm.settings.collectAsState()
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // The time already set (8:30 by default) is shown chosen, even when it is not one of the offered hours.
            val offered = s.setupBriefOptions.dropLast(1).map { "%02d:00".format(it.substringBefore(':').toInt()) }
            if (st.morningBrief && st.morningTime !in offered) Pill("✓ " + st.morningTime.removePrefix("0"), accent = true)
            s.setupBriefOptions.forEachIndexed { i, o ->
                val off = i == s.setupBriefOptions.lastIndex
                val time = if (off) null else "%02d:00".format(o.substringBefore(':').toInt())
                val on = if (off) !st.morningBrief else st.morningBrief && st.morningTime == time
                Pill(if (on) "✓ $o" else o, accent = on, onClick = {
                    vm.updateSettings { it.copy(morningBrief = !off, morningTime = time ?: it.morningTime) }
                })
            }
        }
    }
}
