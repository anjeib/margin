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
    val scenes = 7
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
        Box(Modifier.fillMaxWidth().height(340.dp).clip(RoundedCornerShape(22.dp)).background(c.card)
            .then(Modifier.padding(vertical = 18.dp))) {
            when (i) {
                0 -> SceneVoice(t.value)
                1 -> SceneAttach(t.value)
                2 -> SceneKnot(t.value)
                3 -> SceneWait(t.value)
                4 -> SceneTopic(t.value)
                5 -> SceneDiary(t.value)
                else -> ScenePeople(t.value)
            }
        }
    }
}

private val SceneMs = intArrayOf(6400, 6000, 3200, 3600, 3800, 4200, 4200)

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
            if (mic) MicGlyph(c.bg, 18.dp) else Text("↑", style = Type.body.copy(color = c.bg, fontSize = 18.sp))
        }
    }
}

/** A finger over the scene: where it is (in the scene's px), and how pressed (0..1). */
@Composable
private fun Finger(x: androidx.compose.ui.unit.Dp, y: androidx.compose.ui.unit.Dp, show: Float, press: Float) {
    val c = M.c
    if (show <= 0f) return
    Box(Modifier.offset(x - 22.dp, y - 22.dp).size(44.dp).graphicsLayer { alpha = show; scaleX = 1f - 0.18f * press; scaleY = 1f - 0.18f * press }
        .clip(CircleShape).background(c.ink.copy(alpha = 0.16f + 0.12f * press)))
}

/** A press of [at]..[at]+0.06 of the scene: 0 → 1 → 0. */
private fun pressAt(t: Float, at: Float) = (1f - kotlin.math.abs((t - at - 0.03f) / 0.03f)).coerceIn(0f, 1f)

/**
 * The voice — the heart of Margin (owner, 18 окт): a finger presses the microphone, the words said appear over the field as
 * they are heard, the finger sends them, Margin shows how it read them, and the matter lands in its day.
 */
@Composable
private fun SceneVoice(t: Float) {
    val c = M.c
    val s = M.s
    val said = s.obSaid
    val rec = seg(t, 0.12f, 0.55f)
    val words = said.take((said.length * seg(t, 0.18f, 0.52f)).toInt())
    val preview = seg(t, 0.62f, 0.68f) * (1f - seg(t, 0.8f, 0.84f))
    val land = seg(t, 0.8f, 0.9f)
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = maxWidth; val h = maxHeight
        Column(Modifier.fillMaxSize()) {
            SceneRule {
                Column {
                    Text(s.obTomorrow, style = Type.meta.copy(color = c.graphite), modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    SceneRow("09:30", s.obRow1)
                    SceneRow("10:00", s.obVoiceRow, sub = s.obVoiceRowSub, subColor = c.link, alpha = land, offsetY = (1f - land) * 30f, grow = land)
                    SceneRow("18:30", s.obRow3)
                }
            }
            Spacer(Modifier.weight(1f))
            // The words heard so far, over the field, as the phone reads them while you speak.
            if (words.isNotEmpty() && land == 0f && preview == 0f) Text(words + if (rec < 1f) "…" else "", style = Type.body.copy(fontFamily = rowFamily, fontSize = 15.sp, lineHeight = 21.sp, color = c.ink),
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.bg).padding(horizontal = 12.dp, vertical = 8.dp))
            // How Margin read it: the result card, as after a send.
            if (preview > 0f) Column(Modifier.padding(horizontal = 18.dp, vertical = 8.dp).fillMaxWidth().graphicsLayer { alpha = preview }
                .clip(RoundedCornerShape(12.dp)).background(c.bg).padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(s.obVoiceRow, style = Type.body.copy(fontFamily = rowFamily, fontSize = 15.sp, color = c.ink))
                Text(s.obVoicePreview, style = Type.meta.copy(color = c.graphite), modifier = Modifier.padding(top = 2.dp))
            }
            val recording = rec in 0.001f..0.999f
            SceneField(if (recording || land > 0f || preview > 0f) "" else words, caret = false, mic = words.isEmpty() || recording || land > 0f || preview > 0f,
                micPulse = if (recording) (t * 10f) % 1f else 0f, hint = s.obHint, rec = if (recording) rec else -1f)
        }
        // The finger: to the microphone, press; after the words, the same button sends.
        val atMic = 0.08f; val atSend = 0.57f
        val show = seg(t, 0.02f, 0.08f) * (1f - seg(t, 0.64f, 0.7f))
        Finger(w - 14.dp - 5.dp - 19.dp, h - 5.dp - 19.dp, show, maxOf(pressAt(t, atMic), pressAt(t, atSend)))
    }
}

/** A file and a person go with the words: the paperclip adds the file, «@» the person; sent — one matter with both. */
@Composable
private fun SceneAttach(t: Float) {
    val c = M.c
    val s = M.s
    val file = seg(t, 0.16f, 0.22f)
    val phrase = s.obAttachSaid
    val typed = phrase.take((phrase.length * seg(t, 0.28f, 0.55f)).toInt())
    val land = seg(t, 0.74f, 0.86f)
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
        val h = maxHeight
        Column(Modifier.fillMaxSize()) {
            SceneRule {
                Column {
                    Text(s.obFridayDay, style = Type.meta.copy(color = c.graphite), modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    SceneRow("11:00", s.obAttachOther)
                    SceneRow(null, s.obAttachRow, sub = s.obAttachRowSub, subColor = c.link, alpha = land, offsetY = (1f - land) * 30f, grow = land)
                }
            }
            Spacer(Modifier.weight(1f))
            // What goes with the words, as plaques over the field: the file, then the person picked with «@».
            Row(Modifier.padding(start = 18.dp, bottom = 8.dp).graphicsLayer { alpha = file * (1f - land) }, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.clip(RoundedCornerShape(12.dp)).background(c.bg).padding(horizontal = 9.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    ClipGlyph(c.graphite, 12.dp); Spacer(Modifier.width(4.dp)); Text(s.obAttachFile, style = Type.meta.copy(color = c.ink))
                }
                if (typed.length > phrase.indexOf('@') + 3) Row(Modifier.clip(RoundedCornerShape(12.dp)).background(c.bg).padding(horizontal = 9.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    PersonGlyph(c.link, 13.dp); Spacer(Modifier.width(4.dp)); Text(s.obAttachPerson, style = Type.meta.copy(color = c.link))
                }
            }
            SceneField(if (land > 0f) "" else typed, caret = t in 0.28f..0.62f, mic = typed.isEmpty() || land > 0f, hint = s.obHint)
        }
        val show = seg(t, 0.03f, 0.08f) * (1f - seg(t, 0.7f, 0.74f))
        // To the paperclip, press; then over to «send».
        val toSend = seg(t, 0.58f, 0.64f)
        val x = (14.dp + 12.dp + 8.dp) * (1f - toSend) + (maxWidth - 14.dp - 5.dp - 19.dp) * toSend
        Finger(x, h - 5.dp - 19.dp, show, maxOf(pressAt(t, 0.12f), pressAt(t, 0.66f)))
    }
}

/** «Дневник — для себя»: the book in the head opens the diary; a few words land under the day with their hour. */
@Composable
private fun SceneDiary(t: Float) {
    val c = M.c
    val s = M.s
    val line = s.obDiaryNew
    val typed = line.take((line.length * seg(t, 0.12f, 0.5f)).toInt())
    val land = seg(t, 0.62f, 0.74f)
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = 18.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(s.journalTitle, style = Type.pageTitle.copy(color = c.ink, fontSize = 22.sp), modifier = Modifier.weight(1f))
            Box(Modifier.size(34.dp).clip(CircleShape).background(c.linkSoft), contentAlignment = Alignment.Center) { DiaryGlyph(c.link, 18.dp) }
        }
        Spacer(Modifier.height(6.dp))
        SceneRule {
            Column {
                Text(s.obDiaryDay, style = Type.meta.copy(color = c.link), modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                SceneNote("08:05", s.obDiary1)
                SceneNote("09:40", line, alpha = land, grow = land)
            }
        }
        Spacer(Modifier.weight(1f))
        SceneField(if (land > 0f) "" else typed, caret = t in 0.12f..0.58f, mic = typed.isEmpty() || land > 0f, hint = s.obDiaryHint)
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
    val card = seg(t, 0.3f, 0.45f)
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
        // The finger taps «Маши» under the matter.
        Finger(54.dp + 14.dp + 12.dp + 120.dp, 50.dp, seg(t, 0.04f, 0.1f) * (1f - seg(t, 0.32f, 0.38f)), pressAt(t, 0.2f))
    }
}

/** «Кружок на линии — сделано»: a tap on the knot fills it and strikes the words. */
@Composable
private fun SceneKnot(t: Float) {
    val s = M.s
    val tap = seg(t, 0.25f, 0.45f)
    val done = seg(t, 0.35f, 0.6f)
    SceneRule {
        Column {
            SceneRow("09:00", s.obKnot1, done = 1f)
            SceneRow("11:00", s.obKnot2, done = done, knotPulse = if (tap in 0.01f..0.99f) tap else 0f)
            SceneRow(null, s.obKnot3, sub = s.obKnot3Sub)
            SceneRow(null, s.obKnot4, sub = s.obKnot4Sub, late = true, subColor = M.c.accentText)
        }
    }
}

/** «Ждём и обещали»: «Маша пришлёт договор до пятницы» becomes a wait; on its day a reminder comes. */
@Composable
private fun SceneWait(t: Float) {
    val c = M.c
    val s = M.s
    val row = seg(t, 0.05f, 0.25f)
    val bell = seg(t, 0.45f, 0.65f)
    Column(Modifier.fillMaxSize()) {
        SceneRule {
            Column {
                SceneRow(null, s.obWait1, sub = s.obWait1Sub, alpha = row, offsetY = (1f - row) * 30f, grow = row)
                SceneRow(null, s.obWait2, sub = s.obWait2Sub)
            }
        }
        Spacer(Modifier.weight(1f))
        // The reminder on its day, sliding down as the phone shows it.
        Column(Modifier.padding(horizontal = 14.dp).fillMaxWidth().graphicsLayer { alpha = bell; translationY = (1f - bell) * -30f }
            .clip(RoundedCornerShape(16.dp)).background(c.bg).padding(14.dp)) {
            Text("Margin · " + s.obFriday, style = Type.meta.copy(color = c.graphite))
            Text(s.obRemind, style = Type.body.copy(fontSize = 15.sp, color = c.ink), modifier = Modifier.padding(top = 4.dp))
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Text(s.obGot, style = Type.meta.copy(color = c.link)); Text(s.obTomorrowBtn, style = Type.meta.copy(color = c.link))
            }
        }
    }
}

/** «Большое дело — тема»: its row in the day unfolds into its matters. */
@Composable
private fun SceneTopic(t: Float) {
    val c = M.c
    val s = M.s
    val open = seg(t, 0.3f, 0.55f)
    SceneRule {
        Column {
            SceneRow("10:00", s.obRow1)
            Row(Modifier.fillMaxWidth()) {
                Spacer(Modifier.width(54.dp + 14.dp + 12.dp - 10.dp))
                Column(Modifier.weight(1f).padding(end = 12.dp).clip(RoundedCornerShape(12.dp)).background(c.sea.copy(alpha = 0.07f)).padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("# ", style = Type.body.copy(fontFamily = rowFamily, fontSize = rowSize, color = c.link))
                        Text(s.obTopic, style = Type.body.copy(fontFamily = rowFamily, fontSize = rowSize, color = c.ink), modifier = Modifier.weight(1f))
                        Text(if (open > 0.5f) "⌃" else "⌄", style = Type.body.copy(color = c.graphite, fontSize = 18.sp))
                    }
                    Text(s.obTopicSub, style = Type.meta.copy(color = c.graphite), modifier = Modifier.padding(top = 2.dp))
                    Column(Modifier.layout { m, cs -> val p = m.measure(cs.copy(maxHeight = androidx.compose.ui.unit.Constraints.Infinity)); layout(p.width, (p.height * open).toInt()) { p.place(0, 0) } }
                        .clipToBounds().graphicsLayer { alpha = open }) {
                        s.obTopicDeals.forEach { d ->
                            Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(11.dp).clip(CircleShape).background(c.card)) {
                                    androidx.compose.foundation.Canvas(Modifier.matchParentSize()) { drawCircle(c.sea, style = androidx.compose.ui.graphics.drawscope.Stroke(1.2.dp.toPx())) }
                                }
                                Spacer(Modifier.width(10.dp))
                                Text(d, style = Type.body.copy(fontFamily = rowFamily, fontSize = 15.sp, color = c.ink))
                            }
                        }
                    }
                }
            }
            SceneRow("18:30", s.obRow3)
        }
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
