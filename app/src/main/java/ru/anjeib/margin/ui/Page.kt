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
import androidx.compose.ui.draw.shadow
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
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.navigationBars
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

/** The ruling's ink (owner, 14 окт): a faint cool grey, a little of the margin's sea in it — a printed notebook's lines. */
val ruleInk: Color @Composable get() = androidx.compose.ui.graphics.lerp(M.c.ink, M.c.sea, 0.45f).copy(alpha = if (M.c.dark) 0.22f else 0.17f)

/** Pitch of the ruling: a notebook's ~8 mm. */
private val RulePitch = 32.dp

/** Ruled lines behind the empty part of the page, from a little under [lines]'s end to the bottom. Variant ≥ 1 only. */
@Composable
fun Modifier.ruledPage(lines: PageLines): Modifier {
    // «Полоса набора» (owner, 15 окт): no ruling on the planner page.
    if (LocalPaper.current < 1 || planner) return this
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
    // Owner, 14 окт: specks read as cheap unbleached paper. A matte sheet has an even tooth instead: every point a hair
    // lighter or darker, no single one standing out.
    val px = IntArray(n * n) {
        val v = r.nextGaussian().toFloat().coerceIn(-2.5f, 2.5f) / 2.5f
        if (v >= 0) android.graphics.Color.argb((v * 255).toInt(), 255, 255, 255) else android.graphics.Color.argb((-v * 255).toInt(), 40, 38, 34)
    }
    android.graphics.Bitmap.createBitmap(px, n, n, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()
}

/**
 * Paper over a sheet (owner, 14 окт: «ощущение дорогого ежедневника», «матовость»): a warm white, as a good planner's paper
 * is — no clouds, no darkened edges, only an even matte tooth, felt rather than seen. Not in the dark.
 */
private fun DrawScope.paperOver(dark: Boolean) {
    if (dark) return
    drawRect(ShaderBrush(ImageShader(grain, TileMode.Repeated, TileMode.Repeated)), alpha = 0.06f)
}

/** The «Планер» page is on. */
val planner: Boolean @Composable get() = LocalPaper.current >= 4

/** The rows' words: the book face on the planner page, Inter otherwise. */
val rowFamily: androidx.compose.ui.text.font.FontFamily @Composable get() = if (planner) Literata else Sans
/** Times reads small at Inter's size: a touch larger, a touch tighter on the planner page. */
val rowSize: androidx.compose.ui.unit.TextUnit @Composable get() = if (planner) 17.sp else 16.sp
val rowLine: androidx.compose.ui.unit.TextUnit @Composable get() = if (planner) 24.sp else 22.sp
/** Grey words under a row: Times is thinner than Inter, so on the planner page they are a tone darker. */
val subInk: Color @Composable get() = M.c.graphite
val subSize: androidx.compose.ui.unit.TextUnit @Composable get() = if (planner) 13.sp else 13.5.sp

/** The calendar and the search at the top right of a diary page (owner, 14 окт); null off the planner. */
class PageTools(val searchOn: Boolean, val monthOpen: Boolean, val landed: java.time.LocalDate?, val menuOn: Boolean,
                val search: () -> Unit, val calendar: () -> Unit, val menu: () -> Unit)
val LocalPageTools = compositionLocalOf<PageTools?> { null }

/** The page's tools as drawn marks — the search and ≡ (the contents); [calendar] — on a day's page, where the month unfolds. */
@Composable
fun PageToolIcons(calendar: Boolean = false) {
    val t = LocalPageTools.current ?: return
    val c = M.c
    val s = M.s
    @Composable fun tool(label: String, on: Boolean, onClick: () -> Unit, glyph: @Composable (Color) -> Unit) {
        Box(Modifier.size(42.dp).clip(androidx.compose.foundation.shape.CircleShape).background(if (on) c.linkSoft else Color.Transparent)
            .clickable(onClickLabel = label, onClick = onClick).semanticsLabel(label), contentAlignment = androidx.compose.ui.Alignment.Center) {
            glyph(if (on) c.link else c.ink.copy(alpha = 0.7f))
        }
    }
    androidx.compose.foundation.layout.Row {
        if (calendar) tool(s.calendarTitle, t.monthOpen, t.calendar) { ink ->
            // Something just went to another day: its number stands on the calendar, a tap opens the month there.
            if (t.landed != null && !t.monthOpen) androidx.compose.material3.Text(t.landed.dayOfMonth.toString(),
                style = Type.small.copy(color = c.accentText, fontSize = 15.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold))
            else CalendarGlyph(ink, 21.dp)
        }
        tool(s.search, t.searchOn, t.search) { ink -> SearchGlyph(ink, 21.dp) }
        // ≡ — the contents, where every section is (owner, 14 окт: instead of the tabs at the foot)
        tool(s.contentsTitle, t.menuOn, t.menu) { ink -> MenuGlyph(ink) }
    }
}

/**
 * The page's tools in a line of their own above its name (owner, 15 окт: «Понедельник» ran into them) — the name gets the
 * whole width.
 */
@Composable
fun PageToolsRow(calendar: Boolean = false) {
    if (LocalPageTools.current == null) return
    androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth().padding(end = 6.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End) {
        PageToolIcons(calendar)
    }
}

/** The page edges and cover beyond the sheet. */
private val EdgeW = 11.dp

private fun Modifier.semanticsLabel(label: String) = this.then(Modifier.semantics { contentDescription = label })

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
    // the right hook's upright: in line with the panels' right edge, not at the sheet's cut (owner, 14 окт — the tabs are gone)
    val r = size.width - 16f * dp
    val arm = 11f * dp
    // the rules
    val labelEnd = r - 19f * dp
    val labelStart = label?.let { labelEnd - it.size.width } ?: labelEnd
    // Owner, 14 окт: no rules between them — only the corner hooks and «MARGIN» frame the page.
    fun seg(x0: Float, y0: Float, x1: Float, y1: Float) = drawLine(mark, Offset(x0, y0), Offset(x1, y1), heavy, cap = androidx.compose.ui.graphics.StrokeCap.Square)
    // Owner, 14 окт: one hook only — the top right «┐», by «MARGIN».
    seg(r - 7.5f * dp, top - 3f * dp, r, top - 3f * dp); seg(r, top - 3f * dp, r, top + 5.5f * dp)
    label?.let { drawText(it, topLeft = Offset(labelStart, top - it.size.height / 2f)) }
}

/**
 * The main screen's sheet. Variant 2+: a desk a shade darker shows at the right edge, the sheet casts a soft shadow onto it
 * and two hairlines are the pages under it. Variant 3: the grain over all of it, too faint to touch the words.
 */
@Composable
fun PageSheet(modifier: Modifier = Modifier,
              /** The printed frame: on the diary's pages; a page with its own top bar (a topic, the search) goes without it. */
              frame: Boolean = true, content: @Composable BoxScope.() -> Unit) {
    val c = M.c
    val v = LocalPaper.current
    if (v < 2) { Box(modifier.fillMaxSize().background(c.bg), content = content); return }
    if (v >= 4) { PlannerSheet(modifier, frame, content); return }
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
/**
 * «Планер», after «полоса набора» (owner, 15 окт): the sheet is plain paper — no frame, no «MARGIN», no corner hook, no grain,
 * no edges of pages under it. What gives the page its form is the type, the margin's rule and the double rule under each head.
 */
@Composable
private fun PlannerSheet(modifier: Modifier, @Suppress("UNUSED_PARAMETER") frame: Boolean, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.fillMaxSize().background(M.c.bg), content = content)
}

@Composable
private fun Modifier.statusBarsPaddingCompat(): Modifier = this.then(Modifier.statusBarsPadding())

/**
 * A page turned like paper (owner, 14 окт): forward — the open page lifts from its right edge and turns over the spine on the
 * left, the next one is already lying under it in a little shadow; back — the earlier page comes over and lies down. [order]
 * says which page is further in the book. With animations off — just the next page.
 */
@Composable
fun <T> PageTurn(target: T, order: (T) -> Long, modifier: Modifier = Modifier, content: @Composable (T) -> Unit) {
    // Owner, 14 окт: a plain horizontal slide — the next day (or the page further in the book) comes in from the right.
    val c = M.c
    val motion = motionOn()
    androidx.compose.animation.AnimatedContent(target, modifier, transitionSpec = {
        val fwd = order(targetState) >= order(initialState)
        val spec = androidx.compose.animation.core.tween<androidx.compose.ui.unit.IntOffset>(if (motion) 320 else 0, easing = androidx.compose.animation.core.FastOutSlowInEasing)
        androidx.compose.animation.slideInHorizontally(spec) { if (fwd) it else -it } togetherWith
            androidx.compose.animation.slideOutHorizontally(spec) { if (fwd) -it else it }
    }, label = "page") { key -> Box(Modifier.fillMaxSize().background(c.bg)) { content(key) } }
}

/** The planner's paper grain over a sheet that is not a [PageSheet] (a card rising from below). */
@Composable
fun Modifier.paperGrain(): Modifier {
    // «Полоса набора» (owner, 15 окт): plain paper, no grain.
    if (planner || M.c.dark) return this
    val dark = M.c.dark
    return this.drawWithContent {
        drawContent()
        drawRect(ShaderBrush(ImageShader(grain, TileMode.Repeated, TileMode.Repeated)), alpha = if (dark) 0.04f else 0.05f)
    }
}

/**
 * The diary's three pages side by side — the contents, «Сегодня», the overview — turned under the finger like paper (owner,
 * 14 окт): the page being turned rises from its right edge and goes over the spine on the left, the next one lies under it
 * in its shadow; back — the earlier page comes over and lies down. [page] is where the diary is open; a tab moves it, a swipe
 * tells [onPage].
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun PlannerPager(page: Int, onPage: (Int) -> Unit, count: Int = 3, content: @Composable (Int) -> Unit) {
    val c = M.c
    val state = androidx.compose.foundation.pager.rememberPagerState(initialPage = page) { count }
    val motion = motionOn()
    // A tab moved the diary: turn to it (animated, as if by hand).
    androidx.compose.runtime.LaunchedEffect(page) {
        if (state.currentPage != page && !state.isScrollInProgress) { if (motion) state.animateScrollToPage(page, animationSpec = androidx.compose.animation.core.tween(380, easing = androidx.compose.animation.core.FastOutSlowInEasing)) else state.scrollToPage(page) }
    }
    // A swipe settled on a page: the diary is open there.
    val curPage by androidx.compose.runtime.rememberUpdatedState(page)
    val tell by androidx.compose.runtime.rememberUpdatedState(onPage)
    androidx.compose.runtime.LaunchedEffect(state) {
        androidx.compose.runtime.snapshotFlow { state.settledPage }.collect { if (it != curPage) tell(it) }
    }
    androidx.compose.foundation.pager.HorizontalPager(state, Modifier.fillMaxSize(), beyondViewportPageCount = 1) { p ->
        // Owner, 14 окт: the 3D leaf looked clumsy — the pages simply slide side by side.
        Box(Modifier.fillMaxSize().background(c.bg)) { content(p) }
    }
}

/** The writing band at the foot of the planner page: paper tinted towards terracotta, a double rule across its top. */
@Composable
fun Modifier.fieldBand(): Modifier {
    val c = M.c
    // «Полоса набора» (owner, 15 окт): no tinted band — the field's pieces stand on the page.
    if (planner) return this.background(c.bg)
    val band = androidx.compose.ui.graphics.lerp(c.bg, c.accentSoft, if (c.dark) 0.55f else 0.6f)
    val rule = c.accent.copy(alpha = 0.55f)
    return this.background(band).drawBehind {
        val w = 0.8.dp.toPx()
        drawLine(rule, Offset(0f, w / 2), Offset(size.width, w / 2), w)
        drawLine(rule, Offset(0f, 3.dp.toPx()), Offset(size.width, 3.dp.toPx()), w)
    }
}

// ---------------- the pencil (owner, 14 окт): what is late is not red — it is underlined, as if by hand in pencil ----------------

/** The annotation that asks for a pencil line under its words (see [PencilText]). */
const val PENCIL = "pencil"

/** Words to be underlined in pencil inside an annotated string. */
inline fun androidx.compose.ui.text.AnnotatedString.Builder.pencil(block: androidx.compose.ui.text.AnnotatedString.Builder.() -> Unit) {
    pushStringAnnotation(PENCIL, "1"); block(); pop()
}

/** Saturday and Sunday in pale terracotta (owner, 14 окт) — a tone lighter than anything late. */
// Fable, 15 окт: grey, not terracotta — on this page terracotta means «просрочено», and a weekend is not late.
val weekendInk: Color @Composable get() = M.c.graphite

/** The pencil's colour: our terracotta (owner, 14 окт) — the mark of «late» as before, now as a stroke under the words. */
val pencilInk: Color @Composable get() = M.c.accent.copy(alpha = 0.9f)

/**
 * A highlighter stroke over [x0]..[x1] of a line [top]..[bottom]: a translucent band of the marker's width, its ends cut on a
 * slant as a chisel tip leaves them, the edges not quite straight, a second thinner pass where the pen went over again.
 */
fun androidx.compose.ui.graphics.drawscope.DrawScope.markerBand(x0: Float, x1: Float, top: Float, bottom: Float, color: Color, seed: Int) {
    val r = java.util.Random(seed.toLong())
    val d = density
    val h = bottom - top
    // Owner, 14 окт: «чуть шире надписи, как в реальности» — past both ends of the words, and a little taller.
    val y0 = top + h * 0.12f; val y1 = bottom - h * 0.04f
    val slant = (y1 - y0) * 0.35f
    val l = x0 - (5f + r.nextFloat() * 2f) * d; val rr = x1 + (6f + r.nextFloat() * 2.5f) * d
    fun j() = (r.nextFloat() - 0.5f) * 1.2f * d
    val band = androidx.compose.ui.graphics.Path().apply {
        moveTo(l + slant, y0 + j()); lineTo((l + rr) / 2, y0 + j()); lineTo(rr, y0 + j())
        lineTo(rr - slant, y1 + j()); lineTo((l + rr) / 2, y1 + j()); lineTo(l, y1 + j()); close()
    }
    drawPath(band, color.copy(alpha = color.alpha * 0.28f))
    // the second pass, narrower, a little lower — highlighters overlap like this
    val band2 = androidx.compose.ui.graphics.Path().apply {
        val a = y0 + (y1 - y0) * 0.35f
        moveTo(l + slant + 4f * d, a + j()); lineTo(rr - 6f * d, a + j()); lineTo(rr - 6f * d - slant * 0.6f, y1 - 1f * d + j()); lineTo(l + 4f * d, y1 - 1f * d + j()); close()
    }
    drawPath(band2, color.copy(alpha = color.alpha * 0.1f))
}

/**
 * A hand line under [x0]..[x1] at [y]: not ruler-straight — it wobbles a little and climbs a hair, and a second, fainter pass
 * gives it the grain of graphite. [seed] keeps one line the same from frame to frame.
 */
fun androidx.compose.ui.graphics.drawscope.DrawScope.pencilLine(x0: Float, x1: Float, y: Float, color: Color, seed: Int) {
    val r = java.util.Random(seed.toLong())
    val d = density
    val start = x0 - 1.5f * d; val end = x1 + 2.5f * d
    val climb = (0.6f + r.nextFloat() * 0.8f) * d
    val path = androidx.compose.ui.graphics.Path()
    var x = start
    path.moveTo(x, y + (r.nextFloat() - 0.5f) * 0.8f * d)
    while (x < end) {
        x = minOf(end, x + (5f + r.nextFloat() * 4f) * d)
        val t = (x - start) / (end - start).coerceAtLeast(1f)
        path.lineTo(x, y - climb * t + (r.nextFloat() - 0.5f) * 0.9f * d)
    }
    drawPath(path, color, style = androidx.compose.ui.graphics.drawscope.Stroke(1.25f * d, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
    translate(0.4f * d, 0.7f * d) { drawPath(path, color.copy(alpha = color.alpha * 0.35f), style = androidx.compose.ui.graphics.drawscope.Stroke(0.6f * d, cap = androidx.compose.ui.graphics.StrokeCap.Round)) }
}

/**
 * Text whose [PENCIL]-annotated words get a pencil line under them (each line of a wrapped run its own stroke); [whole] —
 * the whole text is underlined (a late day on the margin).
 */
@Composable
fun PencilText(text: androidx.compose.ui.text.AnnotatedString, style: androidx.compose.ui.text.TextStyle, modifier: Modifier = Modifier, whole: Boolean = false,
               maxLines: Int = Int.MAX_VALUE, overflow: androidx.compose.ui.text.style.TextOverflow = androidx.compose.ui.text.style.TextOverflow.Clip,
               inlineContent: Map<String, androidx.compose.foundation.text.InlineTextContent> = emptyMap()) {
    // «Полоса набора» (owner, 15 окт): what is late in a line of words — «1 просрочено», «с 12 окт» — is set in terracotta;
    // the highlighter is kept for a late entry's own words.
    val late = M.c.accentText
    val shown = if (whole) text else androidx.compose.ui.text.buildAnnotatedString {
        append(text)
        text.getStringAnnotations(PENCIL, 0, text.length).forEach { addStyle(androidx.compose.ui.text.SpanStyle(color = late), it.start, it.end) }
    }
    androidx.compose.material3.Text(shown, style = if (whole) style.copy(color = late) else style, maxLines = maxLines, overflow = overflow, inlineContent = inlineContent, modifier = modifier)
}
