package ru.anjeib.margin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.drawText
import ru.anjeib.margin.data.dowShort

/**
 * The screen as a page of a paper diary (owner, 14 окт, variants to choose from — Settings → developer → «Страница»):
 * 0 — as before; 1 — ruled lines on the empty part of a page; 2 — and the sheet's edge on a desk; 3 — and a faint paper grain;
 * 4 — «Планер» (owner's choice, 14 окт, after design/v9/v9-2-planner.png): a printed planner page — a frame with corner marks
 * and «MARGIN», day tabs on the right edge, the rows' words in a book serif, section heads with a printed rule, panels outlined.
 * None of it goes under the words: lines start below the last row, the grain is ~2 % and has no pattern.
 */
val LocalPaper = compositionLocalOf { 0 }

/** Where a page's content ends (root y), set by [PageEnd] — the ruling starts below it. Null: the content fills the page. */
class PageLines { var end by mutableStateOf<Float?>(null) }

@Composable
fun rememberPageLines() = remember { PageLines() }

/** A zero-high mark after a page's last row. */
@Composable
fun PageEnd(lines: PageLines) {
    DisposableEffect(lines) { onDispose { lines.end = null } }
    Spacer(Modifier.fillMaxWidth().height(0.dp).onGloballyPositioned { lines.end = it.positionInRoot().y })
}

/** Pitch of the ruling: a notebook's ~8 mm. */
private val RulePitch = 32.dp

/** Ruled lines behind the empty part of the page, from a little under [lines]'s end to the bottom. Variant ≥ 1 only. */
@Composable
fun Modifier.ruledPage(lines: PageLines): Modifier {
    if (LocalPaper.current < 1) return this
    val line = M.c.hairline
    var top by remember { mutableFloatStateOf(0f) }
    return this.onGloballyPositioned { top = it.positionInRoot().y }.drawBehind {
        val end = lines.end ?: return@drawBehind
        val pitch = RulePitch.toPx()
        var y = end - top + 22.dp.toPx()
        if (y < 0) y += ((-y / pitch).toInt() + 1) * pitch
        val w = 1.dp.toPx()
        while (y < size.height) {
            drawLine(line, Offset(0f, y), Offset(size.width, y), strokeWidth = w)
            y += pitch
        }
    }
}

/** A tile of quiet paper grain: random specks, no pattern a tile repeat would show at this size. */
private val grain: ImageBitmap by lazy {
    val n = 160
    val r = java.util.Random(14_10_2026)
    val px = IntArray(n * n) {
        val v = r.nextInt(256)
        // dark and light specks, mostly nothing — paper, not noise.
        when {
            v < 26 -> android.graphics.Color.argb(255, 60, 45, 30)
            v > 236 -> android.graphics.Color.argb(255, 255, 255, 250)
            else -> android.graphics.Color.TRANSPARENT
        }
    }
    android.graphics.Bitmap.createBitmap(px, n, n, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()
}

/** The «Планер» page is on. */
val planner: Boolean @Composable get() = LocalPaper.current >= 4

/** The rows' words: the book face on the planner page, Inter otherwise. */
val rowFamily: androidx.compose.ui.text.font.FontFamily @Composable get() = if (planner) Book else Sans
/** Times reads small at Inter's size: a touch larger, a touch tighter on the planner page. */
val rowSize: androidx.compose.ui.unit.TextUnit @Composable get() = if (planner) 15.5.sp else 16.sp
val rowLine: androidx.compose.ui.unit.TextUnit @Composable get() = if (planner) 19.5.sp else 22.sp
/** Grey words under a row: Times is thinner than Inter, so on the planner page they are a tone darker. */
val subInk: Color @Composable get() = if (planner) androidx.compose.ui.graphics.lerp(M.c.graphite, M.c.ink, 0.3f) else M.c.graphite
val subSize: androidx.compose.ui.unit.TextUnit @Composable get() = if (planner) 13.5.sp else 13.5.sp

/** Width of the planner's index tabs, and of the page edges and cover beyond them. */
val TabsW = 25.dp
private val EdgeW = 11.dp

/** One tab of the planner's index: a word (a weekday) or a glyph; [on] — terracotta, the page open at it. */
class IndexTab(val label: String, val on: Boolean, val mark: Boolean = false, val accentWord: Boolean = false,
               val glyph: (@Composable (Color) -> Unit)? = null, val onClick: () -> Unit)

/**
 * The planner's index down the right edge (owner, 14 окт, design/v9/v9-2-planner.png): the page itself goes on past its
 * edge and is cut into tabs by hairlines — no boxes; the open one is a terracotta tab standing a little out. The page's week
 * Пн…Вс in the book italic (today's word terracotta, a dot where there are matters), and under Вс, after a gap, the
 * calendar, the overview, the search and the menu.
 */
@Composable
fun PlannerIndex(days: List<IndexTab>, tools: List<IndexTab>) {
    val c = M.c
    val line = c.graphite.copy(alpha = 0.22f)
    @Composable fun tab(t: IndexTab, h: androidx.compose.ui.unit.Dp, first: Boolean) {
        Box(Modifier.size(TabsW + 3.dp, h)
            .drawBehind { if (!first) drawLine(line, Offset(0f, 0f), Offset(TabsW.toPx(), 0f), 1.dp.toPx()) }
            .clickable(onClickLabel = t.label, onClick = t.onClick, indication = null,
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() })
            .semanticsLabel(t.label)) {
            if (t.on) Box(Modifier.padding(vertical = 3.dp).fillMaxSize().clip(androidx.compose.foundation.shape.RoundedCornerShape(topEnd = 7.dp, bottomEnd = 7.dp))
                .background(c.accent))
            val ink = if (t.on) c.onAccent else if (t.accentWord) c.accentText else c.ink.copy(alpha = 0.78f)
            Box(Modifier.size(TabsW, h), contentAlignment = androidx.compose.ui.Alignment.Center) {
                if (t.glyph != null) t.glyph.invoke(if (t.on) c.onAccent else c.graphite)
                else androidx.compose.foundation.layout.Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                    androidx.compose.material3.Text(t.label, style = androidx.compose.ui.text.TextStyle(fontFamily = Tinos, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                        fontSize = 14.sp, color = ink))
                    Box(Modifier.size(3.dp).clip(androidx.compose.foundation.shape.CircleShape)
                        .background(if (t.mark && !t.on) c.graphite.copy(alpha = 0.5f) else Color.Transparent))
                }
            }
        }
    }
    androidx.compose.foundation.layout.Column {
        days.forEachIndexed { i, t -> tab(t, 43.dp, i == 0) }
        Box(Modifier.size(TabsW, 1.dp).background(line))
        Spacer(Modifier.height(18.dp))
        tools.forEachIndexed { i, t -> tab(t, 44.dp, i == 0) }
    }
}

private fun Modifier.semanticsLabel(label: String) = this.then(Modifier.semantics { contentDescription = label })

/** The page's week as index tabs; [open] — the day the page shows (null on another view: no day is open). */
fun weekTabs(open: java.time.LocalDate?, base: java.time.LocalDate, today: java.time.LocalDate, busy: (java.time.LocalDate) -> Boolean,
             onPick: (java.time.LocalDate) -> Unit): List<IndexTab> {
    val monday = base.minusDays(base.dayOfWeek.value - 1L)
    return (0L until 7L).map { i ->
        val d = monday.plusDays(i)
        IndexTab(d.dowShort().replaceFirstChar { it.uppercase() }, on = d == open, mark = busy(d), accentWord = d == today) { onPick(d) }
    }
}

/**
 * The printed frame of a planner page, measured off design/v9/v9-2-planner.png: a hairline at the top and at the bottom and,
 * off their ends, small hooks like a printer's crop marks — «┘» and «┐» at the top, «┐» and «┘» at the bottom, each sitting a
 * little across its rule's line without touching it; «MARGIN» set wide at the top right.
 */
fun Modifier.plannerFrame(ink: Color, mark: Color, label: androidx.compose.ui.text.TextLayoutResult?): Modifier = drawWithContent {
    drawContent()
    val dp = density
    val hair = 0.8f * dp; val heavy = 1.2f * dp
    val top = 14f * dp; val bottom = size.height - 10f * dp
    val l = 28.5f * dp                 // the left hooks' upright
    val r = size.width - 0.6f * dp     // the right hooks' upright: the sheet's edge
    val arm = 11f * dp
    // the rules
    val labelEnd = r - 19f * dp
    val labelStart = label?.let { labelEnd - it.size.width } ?: labelEnd
    drawLine(ink, Offset(l + 3f * dp, top), Offset(label?.let { labelStart - 13f * dp } ?: (r - 8f * dp), top), hair)
    drawLine(ink, Offset(l + 3.5f * dp, bottom), Offset(r - 12f * dp, bottom), hair)
    fun seg(x0: Float, y0: Float, x1: Float, y1: Float) = drawLine(mark, Offset(x0, y0), Offset(x1, y1), heavy, cap = androidx.compose.ui.graphics.StrokeCap.Square)
    // top left «┘»: an upright crossing the rule's line, an arm out to the left at its foot
    seg(l, top - 5.5f * dp, l, top + 5f * dp); seg(l - arm, top + 5f * dp, l, top + 5f * dp)
    // top right «┐»: an arm in from the corner above the line, an upright down from it
    seg(r - 7.5f * dp, top - 3f * dp, r, top - 3f * dp); seg(r, top - 3f * dp, r, top + 5.5f * dp)
    // bottom left «┐»: an arm out to the left above the line, an upright down across it
    seg(l - arm, bottom - 6.5f * dp, l, bottom - 6.5f * dp); seg(l, bottom - 6.5f * dp, l, bottom + 4.5f * dp)
    // bottom right «┘»: an upright up from below the line, an arm in at its foot
    seg(r, bottom - 6.5f * dp, r, bottom + 4f * dp); seg(r - 7.5f * dp, bottom + 4f * dp, r, bottom + 4f * dp)
    label?.let { drawText(it, topLeft = Offset(labelStart, top - it.size.height / 2f)) }
}

/**
 * The main screen's sheet. Variant 2+: a desk a shade darker shows at the right edge, the sheet casts a soft shadow onto it
 * and two hairlines are the pages under it. Variant 3: the grain over all of it, too faint to touch the words.
 */
@Composable
fun PageSheet(modifier: Modifier = Modifier, tabs: (@Composable () -> Unit)? = null,
              /** The printed frame: on the diary's pages; a page with its own top bar (a topic, the search) goes without it. */
              frame: Boolean = true, content: @Composable BoxScope.() -> Unit) {
    val c = M.c
    val v = LocalPaper.current
    if (v < 2) { Box(modifier.fillMaxSize().background(c.bg), content = content); return }
    if (v >= 4) { PlannerSheet(modifier, tabs, frame, content); return }
    val desk = if (c.dark) Color(0xFF0B0A09) else Color(0xFFE6DFD3)
    val shadow = if (c.dark) Color.Black.copy(alpha = 0.55f) else Color(0xFF3A2E22).copy(alpha = 0.16f)
    val edge = 8.dp
    Box(modifier.fillMaxSize().background(desk).drawBehind {
        val e = edge.toPx()
        val x = size.width - e
        // The pages under this one: two hairlines a step apart, the sheet's shadow falling across them.
        drawLine(c.hairline, Offset(x + 2.5.dp.toPx(), 0f), Offset(x + 2.5.dp.toPx(), size.height), 1.dp.toPx())
        drawLine(c.hairline, Offset(x + 5.dp.toPx(), 0f), Offset(x + 5.dp.toPx(), size.height), 1.dp.toPx())
        drawRect(Brush.horizontalGradient(listOf(shadow, Color.Transparent), startX = x, endX = x + e), Offset(x, 0f),
            androidx.compose.ui.geometry.Size(e, size.height))
    }) {
        Box(Modifier.fillMaxSize().padding(end = edge).background(c.bg).then(
            if (v >= 3) Modifier.drawWithContent {
                drawContent()
                drawRect(ShaderBrush(ImageShader(grain, TileMode.Repeated, TileMode.Repeated)), alpha = if (c.dark) 0.035f else 0.03f)
            } else Modifier), content = content)
    }
}

/**
 * «Планер»: a page of a bound planner — the sheet with its index tabs, rounded at its outer corners; behind it the edges of
 * the pages under it, each a little wider, then the cover. The printed frame and a faint grain over the paper.
 */
@Composable
private fun PlannerSheet(modifier: Modifier, tabs: (@Composable () -> Unit)?, frame: Boolean, content: @Composable BoxScope.() -> Unit) {
    val c = M.c
    val cover = if (c.dark) Color(0xFF050404) else Color(0xFF4A3A2E)
    val under = if (c.dark) Color(0xFF1B1815) else Color(0xFFEDE6DA)
    val r = 18.dp
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val label = remember(c) {
        measurer.measure("MARGIN", androidx.compose.ui.text.TextStyle(fontFamily = Tinos, fontSize = 9.5.sp, letterSpacing = 2.8.sp, color = c.ink.copy(alpha = 0.85f)))
    }
    Box(modifier.fillMaxSize().background(cover).drawBehind {
        // Three pages under this one, each a step wider, every edge a hairline.
        val rr = r.toPx()
        for (k in 3 downTo 1) {
            val w = size.width - EdgeW.toPx() + k * 2.2.dp.toPx()
            drawRoundRect(under, Offset(-rr, 0f), androidx.compose.ui.geometry.Size(w + rr, size.height), androidx.compose.ui.geometry.CornerRadius(rr, rr))
            drawRoundRect(c.graphite.copy(alpha = 0.3f), Offset(-rr, 0f), androidx.compose.ui.geometry.Size(w + rr, size.height), androidx.compose.ui.geometry.CornerRadius(rr, rr),
                style = androidx.compose.ui.graphics.drawscope.Stroke(0.7.dp.toPx()))
        }
    }) {
        Box(Modifier.fillMaxSize().padding(end = EdgeW).clip(androidx.compose.foundation.shape.RoundedCornerShape(topEnd = r, bottomEnd = r)).background(c.bg)) {
            Box(Modifier.fillMaxSize().padding(end = if (tabs != null) TabsW else 0.dp).drawBehind {
                if (tabs == null) return@drawBehind
                // The sheet's own edge, where the tabs begin.
                drawLine(c.graphite.copy(alpha = 0.3f), Offset(size.width - 0.5.dp.toPx(), 0f), Offset(size.width - 0.5.dp.toPx(), size.height), 1.dp.toPx())
            }, content = content)
            if (tabs != null) Box(Modifier.align(androidx.compose.ui.Alignment.TopEnd).statusBarsPadding().padding(top = 25.dp)) { tabs() }
            Box(Modifier.fillMaxSize().drawBehind {
                drawRect(ShaderBrush(ImageShader(grain, TileMode.Repeated, TileMode.Repeated)), alpha = if (c.dark) 0.04f else 0.05f)
            })
        }
        if (frame) Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(end = TabsW + EdgeW)
            .plannerFrame(c.ink.copy(alpha = 0.6f), c.ink.copy(alpha = 0.85f), label))
    }
}

@Composable
private fun Modifier.statusBarsPaddingCompat(): Modifier = this.then(Modifier.statusBarsPadding())

/**
 * A page turned like paper (owner, 14 окт): forward — the open page lifts from its right edge and turns over the spine on the
 * left, the next one is already lying under it in a little shadow; back — the earlier page comes over and lies down. [order]
 * says which page is further in the book. With animations off — just the next page.
 */
@OptIn(androidx.compose.animation.ExperimentalAnimationApi::class)
@Composable
fun <T> PageTurn(target: T, order: (T) -> Long, modifier: Modifier = Modifier, content: @Composable (T) -> Unit) {
    val c = M.c
    val forward = remember { mutableStateOf(true) }
    val motion = motionOn()
    androidx.compose.animation.AnimatedContent(target, modifier, transitionSpec = {
        val fwd = order(targetState) >= order(initialState)
        forward.value = fwd
        (androidx.compose.animation.EnterTransition.None togetherWith androidx.compose.animation.ExitTransition.KeepUntilTransitionsFinished)
            .apply { targetContentZIndex = if (fwd) -1f else 1f }
    }, label = "page") { key ->
        val fwd = forward.value
        val spec = androidx.compose.animation.core.tween<Float>(if (motion) 520 else 0, easing = androidx.compose.animation.core.FastOutSlowInEasing)
        val turn by transition.animateFloat({ spec }, label = "turn") { st ->
            when (st) {
                androidx.compose.animation.EnterExitState.Visible -> 0f
                androidx.compose.animation.EnterExitState.PreEnter -> if (fwd) 0f else 1f
                androidx.compose.animation.EnterExitState.PostExit -> if (fwd) 1f else 0f
            }
        }
        val under by transition.animateFloat({ spec }, label = "under") { st ->
            when (st) {
                androidx.compose.animation.EnterExitState.Visible -> 0f
                androidx.compose.animation.EnterExitState.PreEnter -> if (fwd) 1f else 0f
                androidx.compose.animation.EnterExitState.PostExit -> if (fwd) 0f else 1f
            }
        }
        Box(Modifier.fillMaxSize()
            .graphicsLayer {
                // Over the spine on the left: the right edge lifts toward the eye and goes over.
                rotationY = -100f * turn
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
                cameraDistance = 14f * density
            }
            .background(c.bg)
            .drawWithContent {
                drawContent()
                // The page lying under is in the turning page's shadow; the turning page darkens as it leaves the light.
                if (under > 0f) drawRect(Color.Black.copy(alpha = 0.16f * under))
                if (turn > 0f) drawRect(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.05f * turn), Color.Black.copy(alpha = 0.22f * turn))))
            }) { content(key) }
    }
}

/** The planner's paper grain over a sheet that is not a [PageSheet] (a card rising from below). */
@Composable
fun Modifier.paperGrain(): Modifier {
    if (!planner) return this
    val dark = M.c.dark
    return this.drawWithContent {
        drawContent()
        drawRect(ShaderBrush(ImageShader(grain, TileMode.Repeated, TileMode.Repeated)), alpha = if (dark) 0.04f else 0.05f)
    }
}
