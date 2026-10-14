package ru.anjeib.margin.ui

import androidx.compose.ui.draw.drawBehind

import androidx.compose.foundation.layout.wrapContentWidth

import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * «Поля и панели» (design v8, 10 окт): one way to set every list in the app.
 *
 * - The margin keeps only «когда» — an hour, a day, «с 8 окт» when late — in small grey italic, right against the rule,
 *   on the line of the row's first words. Nothing else goes there.
 * - Right of the rule, a section is a quiet caps label over one soft panel; the panel's rows are divided by hairlines.
 *   No badges, no pills, no tinted blocks: a panel is the only ground a list stands on.
 * - Each row: one mark in one column (the circle, an arrow of a promise, the bulb of an idea, a calendar for a meeting),
 *   the words, and at most one quiet line under them.
 */

/** The ground a row stands on — the panel — so a swipe paints the same colour under it. */
val LocalRowGround = androidx.compose.runtime.compositionLocalOf<Color?> { null }

/** Where a row sits in its panel: the panel is drawn by its rows, so a list can be lazy and still look like one block. */
enum class Seat { Single, First, Middle, Last }

fun seatOf(i: Int, n: Int): Seat = when {
    n <= 1 -> Seat.Single
    i == 0 -> Seat.First
    i == n - 1 -> Seat.Last
    else -> Seat.Middle
}

private val PanelR = 16.dp

fun Seat.shape(r: Dp = PanelR): Shape = when (this) {
    Seat.Single -> RoundedCornerShape(r)
    Seat.First -> RoundedCornerShape(topStart = r, topEnd = r)
    Seat.Middle -> RoundedCornerShape(0.dp)
    Seat.Last -> RoundedCornerShape(bottomStart = r, bottomEnd = r)
}

/** The planner's panels: a printed box, its corners rounded less (design/v9). */
private val PlannerR = 10.dp

/** How far a panel reaches left of the words' line, into the gap by the rule. */
val PanelBleed = 6.dp

/** The mark column inside a panel. */
val MarkCol = 40.dp

/** Lays the content [d] wider and further left than its slot — a ground that starts before the words do. */
fun Modifier.bleed(d: Dp): Modifier = this.layout { m, c ->
    val px = d.roundToPx()
    if (!c.hasBoundedWidth) { val p = m.measure(c); return@layout layout(p.width, p.height) { p.place(0, 0) } }
    val p = m.measure(c.copy(minWidth = (c.minWidth + px).coerceAtMost(c.maxWidth + px), maxWidth = c.maxWidth + px))
    layout((p.width - px).coerceAtLeast(0), p.height) { p.place(-px, 0) }
}

/** A piece of a panel: its ground in the seat's shape and the hairline above every row but the first. */
@Composable
fun PanelPiece(seat: Seat, modifier: Modifier = Modifier, bleed: Boolean = true, dividerStart: Dp = MarkCol + 12.dp,
               /** false — no line above this piece: it belongs to the one above (a matter under a matter). */
               divider: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    val c = M.c
    CompositionLocalProvider(LocalRowGround provides c.card) {
        val outline = if (planner) c.graphite.copy(alpha = 0.28f) else null
        // The planner's panels are the page's own paper behind a printed hairline — barely lighter, never a card on top.
        val ground = if (outline != null) androidx.compose.ui.graphics.lerp(c.bg, c.card, 0.45f) else c.card
        Column(modifier.then(if (bleed) Modifier.bleed(if (outline != null) 2.dp else PanelBleed) else Modifier).fillMaxWidth()
            .clip(seat.shape(if (outline != null) PlannerR else PanelR)).background(ground)
            .then(if (outline != null) Modifier.panelOutline(seat, outline) else Modifier)) {
            if ((seat == Seat.Middle || seat == Seat.Last) && divider) Box(Modifier.padding(start = if (outline != null) 8.dp else dividerStart, end = if (outline != null) 4.dp else 0.dp)
                .fillMaxWidth().height(1.dp).background(if (outline != null) c.graphite.copy(alpha = 0.2f) else c.hairline))
            content()
        }
    }
}

/**
 * The planner's hairline around a panel, drawn piece by piece: each piece draws its sides, and its top or bottom only when
 * it is the panel's first or last — the outline reads as one, with no lines between rows.
 */
private fun Modifier.panelOutline(seat: Seat, color: Color): Modifier = drawWithContent {
    drawContent()
    val w = 1.dp.toPx(); val r = PlannerR.toPx()
    val first = seat == Seat.Single || seat == Seat.First
    val last = seat == Seat.Single || seat == Seat.Last
    val top = if (first) w / 2 else -r * 2
    val bottom = if (last) size.height - w / 2 else size.height + r * 2
    drawRoundRect(color, androidx.compose.ui.geometry.Offset(w / 2, top), androidx.compose.ui.geometry.Size(size.width - w, bottom - top),
        androidx.compose.ui.geometry.CornerRadius(r, r), style = androidx.compose.ui.graphics.drawscope.Stroke(w))
}

/** A whole panel at once, for short lists that are not lazy. */
@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val c = M.c
    CompositionLocalProvider(LocalRowGround provides c.card) {
        Column(modifier.bleed(PanelBleed).fillMaxWidth().clip(RoundedCornerShape(PanelR)).background(c.card)
            .then(if (planner) Modifier.panelOutline(Seat.Single, c.graphite.copy(alpha = 0.28f)) else Modifier), content = content)
    }
}

/** A hairline between two rows of a [Panel], from the words' line. */
@Composable
fun PanelDivider() = Box(Modifier.padding(start = MarkCol + 12.dp).fillMaxWidth().height(1.dp).background(M.c.hairline))

/** The top of a row's words, so the margin note can stand on their first line. */
val RowTop = 13.dp

/** «когда» on the margin: small grey italic; [late] — terracotta; [link] — petrol (a day someone owes). */
@Composable
fun MarginNote(text: String, late: Boolean = false, link: Boolean = false, modifier: Modifier = Modifier) {
    val c = M.c
    // Owner, 10 окт: «красиво, но мелко и бледно» — larger and in ink (a touch softer than the words), medium italic.
    // Owner, 14 окт: the margin's dates and hours more telling — in full ink, a size up, pressed into the paper like the day's
    // number (a pale edge under each letter, as letterpress leaves).
    PencilText(AnnotatedString(text), style = Type.italic.copy(color = when { link -> c.link; else -> if (planner) c.ink else c.ink.copy(alpha = 0.8f) },
        fontSize = if (planner) 15.5.sp else 15.5.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum", textAlign = TextAlign.End,
        shadow = if (planner && !c.dark) androidx.compose.ui.graphics.Shadow(Color.White.copy(alpha = 0.9f), androidx.compose.ui.geometry.Offset(0f, 1.6f), 0f) else null),
        // …and set on a small label of the same card as the tabs on the right, so the margin reads as the book's own index.
        modifier = if (planner && !late) modifier.marginLabel() else modifier, whole = late)
}

/** The margin of a panel row: the note on the line of the row's first words. */
@Composable
fun RowMargin(text: String?, late: Boolean = false, link: Boolean = false) {
    if (text == null) return
    MarginNote(text, late, link, Modifier.padding(top = RowTop + 2.dp))
}

/**
 * The one row of every list: the mark in its column, the words, one quiet line under them, an optional end.
 * The margin (when) is set by the caller around it — see [PaperRow].
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RowBody(
    mark: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onLong: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 48.dp)
            .then(if (onClick != null || onLong != null) Modifier.combinedClickable(onClick = onClick ?: {}, onLongClick = onLong) else Modifier)
            .padding(end = 14.dp, top = RowTop - 2.dp, bottom = RowTop),
        verticalAlignment = Alignment.Top,
    ) {
        // Owner, 14 окт: the mark stands on the middle of the first line of words, and its larger touch area
        // (the circle's 37 dp) doesn't make the row taller — every row is as tall as its words.
        // The planner (design/v9) sets the mark a little further in and the words closer to it.
        Box(Modifier.width(if (planner) 46.dp else MarkCol).height(if (planner) 22.dp else 26.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.wrapContentSize(unbounded = true)) { mark?.invoke() }
        }
        Spacer(Modifier.width(if (planner) 10.dp else 12.dp))
        Column(Modifier.weight(1f).padding(top = 2.dp), content = content)
        if (trailing != null) { Spacer(Modifier.width(8.dp)); Box(Modifier.padding(top = 2.dp)) { trailing() } }
    }
}

/** A row in its seat of a panel, with «когда» on the margin. */
@Composable
fun PaperRow(
    seat: Seat,
    margin: String? = null,
    late: Boolean = false,
    mark: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onLong: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Margined(modifier, margin = margin?.let { m -> { RowMargin(m, late) } }) {
        PanelPiece(seat) { RowBody(mark, onClick = onClick, onLong = onLong, trailing = trailing, content = content) }
    }
}

/** The words of a row: ink, 16 sp, up to three lines. */
@Composable
fun RowTitle(text: String, faded: Boolean = false, maxLines: Int = 3) {
    val c = M.c
    var layout by remember { mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }
    Text(text, style = Type.body.copy(fontFamily = rowFamily, color = if (faded) c.graphite else c.ink, fontSize = rowSize, lineHeight = rowLine), maxLines = maxLines, overflow = TextOverflow.Ellipsis,
        onTextLayout = { layout = it }, modifier = Modifier.foundMarks(text) { layout })
}

@Composable
fun RowTitle(text: AnnotatedString, faded: Boolean = false, maxLines: Int = 3) {
    val c = M.c
    var layout by remember { mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }
    Text(text, style = Type.body.copy(fontFamily = rowFamily, color = if (faded) c.graphite else c.ink, fontSize = rowSize, lineHeight = rowLine), maxLines = maxLines, overflow = TextOverflow.Ellipsis,
        onTextLayout = { layout = it }, modifier = Modifier.foundMarks(text.text) { layout })
}

/** The one quiet line under a row's words. */
@Composable
fun RowSub(text: String, link: Boolean = false, late: Boolean = false, maxLines: Int = 1) {
    val c = M.c
    PencilText(AnnotatedString(text), whole = late, style = Type.small.copy(fontFamily = rowFamily, color = when { link -> c.link; else -> subInk }, fontSize = subSize),
        maxLines = maxLines, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
}

/** A section: the caps label over its panel, its count, one action at the right, a fold. */
@Composable
fun SectionHead(
    label: String,
    modifier: Modifier = Modifier,
    count: Int? = null,
    action: Pair<String, () -> Unit>? = null,
    open: Boolean? = null,
    onFold: (() -> Unit)? = null,
    top: Dp = 22.dp,
) {
    val c = M.c
    Margined(modifier.padding(top = if (planner) top - 4.dp else top, bottom = if (planner) 5.dp else 8.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 24.dp).clip(RoundedCornerShape(6.dp))
            .then(if (onFold != null) Modifier.clickable(onClick = onFold) else Modifier), verticalAlignment = Alignment.CenterVertically) {
            if (planner) {
                // «ОБЕЩАНИЯ ———»: a printed heading, wider letters, a rule running to the edge.
                Text(label.uppercase() + (count?.let { "  ·  $it" } ?: ""), style = Type.label.copy(fontFamily = Book, fontSize = 11.sp,
                    fontWeight = FontWeight.Normal, color = c.ink.copy(alpha = 0.92f), letterSpacing = 2.9.sp))
                Box(Modifier.weight(1f).padding(start = 9.dp, end = if (action != null || open != null) 8.dp else 0.dp).height(0.8.dp).background(c.ink.copy(alpha = 0.45f)))
            } else
            Text(label.uppercase() + (count?.let { "  ·  $it" } ?: ""), style = Type.label.copy(color = c.graphite, letterSpacing = 1.5.sp), modifier = Modifier.weight(1f))
            action?.let { (text, act) ->
                Text(text, style = Type.small.copy(color = c.link, fontSize = 13.5.sp, fontWeight = FontWeight.Medium),
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = act).padding(horizontal = 6.dp, vertical = 4.dp))
            }
            if (open != null) {
                val rot by animateFloatAsState(if (open) 0f else -90f, label = "fold")
                Icon(Icons.Rounded.KeyboardArrowDown, null, tint = c.graphite, modifier = Modifier.padding(start = 4.dp).size(20.dp).rotate(rot))
            }
        }
    }
}

/** Margin's own voice right of the rule: serif italic; [link] — petrol and a tap. */
@Composable
fun Voice(text: AnnotatedString, modifier: Modifier = Modifier, link: Boolean = false, onClick: (() -> Unit)? = null) {
    val c = M.c
    PencilText(text, style = Type.italic.copy(color = if (link) c.link else c.graphite, fontSize = if (planner) 18.5.sp else 16.sp, lineHeight = if (planner) 23.sp else 22.sp),
        modifier = modifier.clip(RoundedCornerShape(6.dp)).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = if (onClick != null) 6.dp else 0.dp))
}

@Composable
fun Voice(text: String, modifier: Modifier = Modifier, link: Boolean = false, onClick: (() -> Unit)? = null) =
    Voice(AnnotatedString(text), modifier, link, onClick)

/** Words as tabs: the chosen one in petrol with a short line under it. */
@Composable
fun TextTabs(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val c = M.c
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(22.dp), verticalAlignment = Alignment.Bottom) {
        options.forEach { (k, l) ->
            val on = k == selected
            Column(Modifier.clip(RoundedCornerShape(6.dp)).semantics { this.selected = on }.clickable { onSelect(k) }.padding(top = 6.dp),
                horizontalAlignment = Alignment.Start) {
                // The planner (owner, 14 окт): tabs in the book's italic, as the index on the right.
                Text(l, style = if (planner) Type.italic.copy(color = if (on) c.link else c.graphite, fontSize = 18.sp, fontWeight = if (on) FontWeight.SemiBold else null)
                    else Type.body.copy(color = if (on) c.link else c.graphite, fontSize = 16.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium))
                Spacer(Modifier.height(6.dp))
                Box(Modifier.width(if (on) 28.dp else 0.dp).height(2.dp).clip(RoundedCornerShape(1.dp)).background(if (on) c.link else Color.Transparent))
            }
        }
    }
}

/** A choice that narrows a list: an outlined pill, chosen — on soft petrol; [dot] — a sphere's colour before the word. */
@Composable
fun Chip(label: String, on: Boolean, onClick: () -> Unit, dot: Color? = null, modifier: Modifier = Modifier) {
    val c = M.c
    val shape = RoundedCornerShape(50)
    Row(modifier.heightIn(min = 36.dp).clip(shape).background(if (on) c.linkSoft else Color.Transparent)
        .border(1.dp, if (on) Color.Transparent else c.hairline, shape).semantics { selected = on }.clickable(onClick = onClick)
        .padding(horizontal = 14.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        if (dot != null) { Box(Modifier.size(8.dp).clip(CircleShape).background(dot)); Spacer(Modifier.width(8.dp)) }
        Text(label, style = Type.small.copy(color = if (on) c.link else c.ink, fontSize = 14.sp, fontWeight = if (on) FontWeight.Medium else null))
    }
}

/**
 * A filter as a drawn mark on a round (owner, 14 окт: icons, not words): outlined, chosen — soft petrol with the mark in
 * petrol; [label] is for TalkBack and a long press shows it.
 */
@Composable
fun IconChip(label: String, on: Boolean, onClick: () -> Unit, size: Dp = 44.dp, glyph: @Composable (Color) -> Unit) {
    val c = M.c
    var hint by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    Box {
        // Owner, 14 окт: drawn marks bare on the page, as the card's tools — no rounds; chosen — petrol with a short line under it.
        Box(Modifier.size(size).clip(CircleShape)
            .semantics { selected = on; contentDescription = label }
            .combinedClickable(onClick = onClick, onLongClick = { hint = true }), contentAlignment = Alignment.Center) {
            glyph(if (on) c.link else c.graphite)
            if (on) Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 3.dp).size(16.dp, 2.dp).clip(RoundedCornerShape(1.dp)).background(c.link))
        }
        if (hint) {
            androidx.compose.runtime.LaunchedEffect(Unit) { kotlinx.coroutines.delay(1400); hint = false }
            androidx.compose.ui.window.Popup(alignment = Alignment.TopCenter, offset = androidx.compose.ui.unit.IntOffset(0, -90)) {
                Text(label, style = Type.small.copy(color = c.onBubble, fontSize = 13.sp), modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(c.bubble).padding(horizontal = 10.dp, vertical = 5.dp))
            }
        }
    }
}

/** A person's initial on a soft petrol round. */
@Composable
fun Avatar(name: String, size: Dp = 34.dp) {
    val c = M.c
    Box(Modifier.size(size).clip(CircleShape).background(c.linkSoft), contentAlignment = Alignment.Center) {
        Text(name.trim().take(1).uppercase(), style = Type.body.copy(color = c.link, fontSize = (size.value * 0.44f).sp, fontWeight = FontWeight.Medium))
    }
}

/** One tool of a card: a drawn mark over its word; set — petrol. */
class Action(val label: String, val on: Boolean, val onClick: () -> Unit, val glyph: @Composable (Color) -> Unit)

/** The card's row of tools: «Когда · Повтор · Люди · Место · Ещё», each a mark over its word; [slots] — columns, so a shorter second row stands under the first. */
@Composable
fun ActionBar(items: List<Action>, modifier: Modifier = Modifier, slots: Int = items.size) {
    val c = M.c
    Row(modifier.fillMaxWidth()) {
        items.forEach { a ->
            val col = if (a.on) c.link else c.graphite
            Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable(onClickLabel = a.label, onClick = a.onClick).padding(vertical = 8.dp, horizontal = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(26.dp), contentAlignment = Alignment.Center) { a.glyph(col) }
                Spacer(Modifier.height(4.dp))
                // A word is never cut to its first letter («В заметки» showed «В»): one line, a size smaller when long.
                // One size for every word (owner, 14 окт: smaller words look wrong) — a row holds only what fits, the rest is in «Ещё».
                Text(a.label, style = Type.small.copy(color = col, fontSize = 12.5.sp, fontWeight = if (a.on) FontWeight.Medium else null), maxLines = 1, softWrap = false)
            }
        }
        repeat((slots - items.size).coerceAtLeast(0)) { Spacer(Modifier.weight(1f)) }
    }
}

/** «Ещё» of a card: all it can do, five to a row (owner, 14 окт: the ⋯ menu moved here). */
@Composable
fun MoreActions(items: List<Action>, modifier: Modifier = Modifier) {
    // Four to a row: the longer words of «Ещё» («В заметки», «Объединить») fit whole at the one size.
    Column(modifier) { items.chunked(4).forEach { row -> ActionBar(row, Modifier.padding(top = 2.dp), slots = 4) } }
}

/**
 * The mark of «ждём» (←) and «обещал» (→): the open hand, bare on the page in the ink of the other marks (owner, 14 окт:
 * no round under it, not petrol) — the same weight as the circle of a matter beside it.
 */
@Composable
fun PromiseMark(incoming: Boolean, size: Dp = 30.dp) {
    val c = M.c
    Box(Modifier.size(size), contentAlignment = Alignment.Center) { HandGlyph(c.ink.copy(alpha = 0.62f), size * 0.9f, incoming = incoming) }
}

/** A row of words alone (no mark): a help group, a setting, a note — the words from the panel's own edge. */
@Composable
fun PlainRow(seat: Seat, modifier: Modifier = Modifier, margin: (@Composable () -> Unit)? = null, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Margined(modifier, margin = margin) {
        PanelPiece(seat, dividerStart = 16.dp) {
            Column(Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 16.dp, vertical = 13.dp), content = content)
        }
    }
}

/** A search line: the card's ground with a hairline, round ends — the same as the field below. */
@Composable
fun SearchField(value: String, onValue: (String) -> Unit, hint: String, modifier: Modifier = Modifier) {
    val c = M.c
    Box(modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(c.card).border(1.dp, c.hairline, RoundedCornerShape(24.dp)).padding(horizontal = 18.dp, vertical = 13.dp)) {
        if (value.isEmpty()) Text(hint, style = Type.body.copy(color = c.graphite))
        androidx.compose.foundation.text.BasicTextField(value, onValue, singleLine = true, textStyle = Type.body.copy(color = c.ink),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(c.link), modifier = Modifier.fillMaxWidth())
    }
}

/** The card of the planner's tabs under a note on the margin: a small rounded label a shade darker than the paper. */
@Composable
fun Modifier.marginLabel(): Modifier {
    val c = M.c
    val card = if (c.dark) androidx.compose.ui.graphics.lerp(c.bg, Color.White, 0.07f) else androidx.compose.ui.graphics.lerp(c.bg, Color(0xFF6E5A40), 0.08f)
    return this.then(androidx.compose.ui.Modifier.drawBehind {
        val padX = 5.dp.toPx(); val padY = 1.dp.toPx()
        drawRoundRect(card, androidx.compose.ui.geometry.Offset(-padX, -padY), androidx.compose.ui.geometry.Size(size.width + padX * 2, size.height + padY * 2),
            androidx.compose.ui.geometry.CornerRadius(5.dp.toPx(), 5.dp.toPx()))
    })
}
