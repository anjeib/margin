package ru.anjeib.margin.ui

import androidx.compose.ui.text.withLink

import ru.anjeib.margin.data.shortLabel

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import ru.anjeib.margin.data.AppData
import ru.anjeib.margin.data.CalEvent
import ru.anjeib.margin.data.Task
import ru.anjeib.margin.data.dative
import ru.anjeib.margin.data.dayMonthShort
import ru.anjeib.margin.data.dayOf
import ru.anjeib.margin.i18n.L
import java.time.LocalDate

/*
 * The rows of the day in v8 «Поля и панели»: one mark, the words, one quiet line; «когда» on the margin only.
 * Today, a calendar day, a person's page and the overview all set their matters with [MatterRow].
 */

/** A day in words for the margin: «сегодня», «завтра», «11 окт». */
/** A day on the margin — always its number (owner, 14 окт: «числами», as a printed planner; no «сегодня» / «завтра» there). */
@Suppress("UNUSED_PARAMETER")
fun dayWord(d: LocalDate, today: LocalDate = LocalDate.now()): String = d.dayMonthShort()

/** A day said inside a line or a heading — the same as everywhere in words: «сегодня», «завтра», «вчера», else «вс 11 окт». */
fun dayTalk(d: LocalDate, today: LocalDate = LocalDate.now()): String = d.shortLabel(today).replaceFirstChar { it.lowercase() }

/**
 * A window for the narrow margin, said as one (owner, 13 окт): «9–11 окт», «13–31 окт»; across months two lines «28 сен –\n3 окт».
 */
fun spanWord(a: LocalDate, b: LocalDate, today: LocalDate = LocalDate.now()): String {
    val yr = if (b.year != today.year) " ${b.year}" else ""
    return if (a.year == b.year && a.month == b.month) "${a.dayOfMonth}–${b.dayMonthShort()}$yr" else "${a.dayMonthShort()} –\n${b.dayMonthShort()}$yr"
}

/**
 * «Когда» of a matter for the margin: late — «с 8 окт» in terracotta; under a day ([underDay]) — only its hour;
 * elsewhere — its day in words and the hour under it.
 */
fun whenNote(t: Task, today: LocalDate, underDay: Boolean): Pair<String?, Boolean> {
    val s = L.s
    val due = t.dueDate
    val start = t.startDate
    if (!t.done && due != null && due.isBefore(today)) return (s.sinceWord + " " + due.dayMonthShort()) to true
    val hour = t.time ?: t.dayPart?.let { s.dayPart(it) }
    // A window standing on today is not due today: its span says so (owner, 13 окт).
    if (underDay && start != null && due != null && start.isBefore(due) && !due.isBefore(today)) return listOfNotNull(spanWord(start, due, today), hour).joinToString("\n") to false
    if (underDay) return hour to false
    val d = due ?: start ?: return hour to false
    val day = if (start != null && due != null && start.isBefore(due)) spanWord(start, due, today) else dayWord(d, today)
    return listOfNotNull(day, hour).joinToString("\n") to false
}

/**
 * One matter in a panel. A tap opens it, a long press offers the hour, the topic and removing it; swipes — done and later.
 * Someone's promise wears its arrow instead of the circle, and the arrow closes it the same way («получено»).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MatterRow(
    t: Task, seat: Seat, today: LocalDate, note: Pair<String?, Boolean>,
    onToggle: () -> Unit, onOpen: () -> Unit, onTime: () -> Unit, onLater: (() -> Unit)?,
    modifier: Modifier = Modifier, faded: Boolean = false, hidePerson: String? = null, extra: String? = null,
    showTopic: Boolean = true, lit: Boolean = false, swipe: Boolean = true,
    /** Its place in a topic's block (owner, 16 окт): the block lies on one light tint, corners at its ends. */
    block: BlockPos? = null,
    /** A tap on «# Тема» under the words opens the topic itself. */
    onOpenTopic: ((String) -> Unit)? = null,
) {
    val c = M.c
    val s = M.s
    val view = androidx.compose.ui.platform.LocalView.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val motion = motionOn()
    val strike = remember(t.id) { androidx.compose.animation.core.Animatable(if (t.done && !t.dropped) 1f else 0f) }
    var ticking by remember(t.id) { mutableStateOf(false) }
    fun tick() {
        view.confirmTick()
        if (t.done || !motion) { onToggle(); return }
        scope.launch { ticking = true; strike.animateTo(1f, androidx.compose.animation.core.tween(320)); kotlinx.coroutines.delay(220); onToggle() }
    }
    var quick by remember(t.id) { mutableStateOf(false) }
    var dlg by remember(t.id) { mutableStateOf<String?>(null) }
    MatterDialog(t.id, dlg) { dlg = null }
    // «Inline» (owner, 16 окт): a tap unfolds the matter in its row instead of opening its card.
    val inline = LocalInline.current
    val unfolded = inline?.open == t.id
    val tap: () -> Unit = if (inline != null) ({ inline.toggle(t.id) }) else onOpen
    val acts = LocalRowActions.current
    val tomorrowAct = LocalTomorrow.current
    val promise = !t.done && (t.waitingFor != null || t.promisedTo != null)
    val body: @Composable () -> Unit = {
        PanelPiece(seat, bleed = !swipe || planner) {
            Box {
                SoftMenu(quick, { quick = false }) {
                    // Everything a row can do (owner, 16 окт: all of it in the hold — no tools under the page).
                    onLater?.let { f -> SoftMenuItem(s.postponeMenu) { quick = false; f() } }
                    if (inline != null) SoftMenuItem(s.actWhen) { quick = false; dlg = "dates" }
                    SoftMenuItem(s.fieldTime) { quick = false; onTime() }
                    if (inline != null) SoftMenuItem(s.edit) { quick = false; dlg = "title" }
                    if (acts != null) {
                        SoftMenuItem(if (t.topicId != null) s.toOtherTopic else s.toTopicPick) { quick = false; acts.toTopic(t.id) }
                        SoftMenuItem(s.toArchive) { quick = false; acts.remove(t.id) }
                    }
                }
                RowBody(
                    mark = {
                        if (promise) Box(Modifier.clip(CircleShape).clickable(onClickLabel = s.markDone, onClick = ::tick)
                            .semantics { contentDescription = if (t.waitingFor != null) s.a11yWaiting else s.a11yPromised }) {
                            PromiseMark(incoming = t.waitingFor != null)
                        } else CheckCircle(t.done || ticking, size = 21.dp, onToggle = ::tick)
                    },
                    modifier = Modifier.landing(lit, 0).semantics {
                        customActions = listOfNotNull(
                            androidx.compose.ui.semantics.CustomAccessibilityAction(s.markDone) { onToggle(); true },
                            tomorrowAct?.let { f -> androidx.compose.ui.semantics.CustomAccessibilityAction(s.swipeTomorrow) { f(t.id); true } },
                            onLater?.let { f -> androidx.compose.ui.semantics.CustomAccessibilityAction(s.dateWord) { f(); true } },
                            androidx.compose.ui.semantics.CustomAccessibilityAction(s.fieldTime) { onTime(); true },
                        )
                    },
                    onClick = tap, onLong = { view.confirmTick(); quick = true },
                ) {
                    StrikeText(t.title + (if (t.dropped) " · " + s.droppedMark else ""),
                        Type.body.copy(fontFamily = rowFamily, fontSize = rowSize, lineHeight = rowLine, color = if (t.done || ticking || faded) c.graphite else c.ink),
                        strike.value, c.faint, maxLines = 3, marked = note.second && !t.done && !ticking)
                    if (unfolded) { val vm = LocalVm.current; if (vm != null) InlineMatterBody(t.id, vm) }
                    else if (!t.done) {
                        val days = t.dayOf()?.let { (i, n) -> s.dayOfDays(i, n) }
                        TaskLine(t, null, showProject = true, today = today, tappable = true, hidePerson = hidePerson,
                            topic = t.topicTitle?.takeIf { showTopic }, extra = listOfNotNull(days, extra).joinToString("  ·  ").ifBlank { null },
                            onTopic = t.topicId?.let { tid -> onOpenTopic?.let { f -> { f(tid) } } })
                    }
                }
            }
        }
    }
    // «Полоса набора» (owner, 15 окт): the hour on the margin is the row's «done»; a row with no hour has only the knot (owner, 16 окт: no dash).
    val marginSlot: (@Composable () -> Unit)? = if (planner) ({ MarginTap(::tick, late = note.second && !t.done && !ticking, done = t.done || ticking) { RowMargin(note.first, note.second && !t.done && !ticking) } })
        else note.first?.let { m -> { RowMargin(m, note.second) } }
    val last = block == BlockPos.Last
    Margined(if (last) modifier.padding(bottom = 8.dp) else modifier, margin = marginSlot) {
      Box(Modifier.blockTint(block).then(if (last) Modifier.padding(bottom = 4.dp) else Modifier)) {
        // Owner, 14 окт: no swipes on rows — on the planner a swipe turns the page; done is the circle, the rest is in the long press.
        if (swipe && !planner) SwipeTask(t, onDone = onToggle, onLater = onLater ?: {}, modifier = Modifier.bleed(PanelBleed), shape = seat.shape(), content = body)
        else body()
      }
    }
}

/** A meeting from the phone's calendar: its hour on the margin, a calendar mark — nothing to tick; a tap opens the calendar. */
@Composable
fun EventPaperRow(e: CalEvent, seat: Seat) {
    val c = M.c
    val s = M.s
    val ctx = androidx.compose.ui.platform.LocalContext.current
    PaperRow(seat, margin = if (e.allDay) s.allDay.lowercase() else e.start.toLocalTime().toString().take(5),
        mark = { CalendarGlyph(c.graphite, 20.dp) },
        onClick = {
            val ms = e.start.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            runCatching { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("content://com.android.calendar/time/$ms"))) }
        }) {
        RowTitle(e.title)
        val sub = listOfNotNull(if (e.allDay) null else e.start.toLocalTime().toString().take(5) + "–" + e.end.toLocalTime().toString().take(5), e.location).joinToString("  ·  ")
        if (sub.isNotBlank()) RowSub(sub)
    }
}

/** What got done: the filled circle, the words struck through; a tap on the circle takes it back. */
@Composable
fun DonePaperRow(t: Task, seat: Seat, onToggle: () -> Unit, onOpen: () -> Unit) {
    val c = M.c
    // The planner (Fable, 15 окт): no circle in the words' column — the filled knot on the rule is the mark, and a tap on the margin takes it back.
    PaperRow(seat, margin = if (planner) "" else null, done = true, onMargin = if (planner) onToggle else null,
        mark = if (planner) null else ({ CheckCircle(true, size = 21.dp, onToggle = onToggle) }), onClick = onOpen) {
        StrikeText(t.title, Type.body.copy(fontFamily = rowFamily, fontSize = rowSize, lineHeight = rowLine, color = c.graphite), 1f, c.faint, maxLines = 2)
        t.topicTitle?.let { RowSub("# $it") }
    }
}

/** An idea, or a file, of a day: the bulb or the file's mark; a tap opens the idea or the topic. */
@Composable
fun NotePaperRow(n: DayNote, seat: Seat, onOpen: (String) -> Unit) {
    val c = M.c
    val s = M.s
    val hour = n.at.drop(11).take(5).takeIf { it != "00:00" && it.isNotBlank() }
    PaperRow(seat, margin = hour, mark = {
        if (n.idea) IdeaMark(26.dp) else Icon(kindIcon(n.file!!.kind), null, tint = c.graphite, modifier = Modifier.size(19.dp))
    }, onClick = { onOpen(n.owner.id) }) {
        RowTitle(if (n.idea) n.owner.title else (n.file!!.caption ?: attachmentLabel(n.file).ifBlank { n.file.name }), maxLines = 2)
        if (!n.idea) RowSub("# " + n.owner.title, link = true)
    }
}

/** Who owes what: the arrow, the words, the person under them in petrol; «напомнить» when their day has come. */
@Composable
fun PromisePaperRow(t: Task, seat: Seat, today: LocalDate, data: AppData, onOpen: () -> Unit, onDone: () -> Unit) {
    val c = M.c
    val links = LocalPeopleLinks.current
    val s = M.s
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val view = androidx.compose.ui.platform.LocalView.current
    val d = t.dueDate ?: t.startDate
    val late = d?.isBefore(today) == true
    val incoming = t.waitingFor != null
    val who = t.waitingFor ?: t.promisedTo ?: ""
    val nudge = incoming && (d?.let { !it.isAfter(today) } ?: (runCatching { LocalDate.parse(t.createdAt.take(10)) }.getOrNull()?.isBefore(today.minusDays(7)) == true))
    val arrow = if (planner) (if (incoming) " ←" else " →") else ""
    var menu by remember(t.id) { mutableStateOf(false) }
    var dlg by remember(t.id) { mutableStateOf<String?>(null) }
    MatterDialog(t.id, dlg) { dlg = null }
    PaperRow(seat, margin = (d?.let { if (late) s.sinceWord + " " + it.dayMonthShort() else dayWord(it, today) } ?: (if (planner) "" else null))?.plus(arrow)?.trim(), late = late,
        onMargin = { view.confirmTick(); onDone() },
        mark = {
            Box(Modifier.clip(CircleShape).clickable(onClickLabel = s.markDone) { view.confirmTick(); onDone() }
                .semantics { contentDescription = if (incoming) s.a11yWaiting else s.a11yPromised }) { PromiseMark(incoming) }
        }, onClick = LocalInline.current?.let { il -> { il.toggle(t.id) } } ?: onOpen, onLong = { menu = true }) {
        // The hold (owner, 16 окт): what is done with it.
        Box { SoftMenu(menu, { menu = false }) {
            SoftMenuItem(s.actWhen) { menu = false; dlg = "dates" }
            SoftMenuItem(s.fieldTime) { menu = false; dlg = "time" }
            SoftMenuItem(s.edit) { menu = false; dlg = "title" }
            LocalRowActions.current?.let { r -> SoftMenuItem(s.toArchive) { menu = false; r.remove(t.id) } }
        } }
        RowTitle(t.title, marked = late)
        // Words never broken: at a large font «напомнить» goes to the next line whole (FlowRow).
        @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
        androidx.compose.foundation.layout.FlowRow(itemVerticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            // The arrow alone was a riddle (Fable, 0.35): the words say it — «ждём от Олега», «обещали Лёше», as in «Обзор».
            Text(buildAnnotatedString {
                val (a, name, b) = if (incoming) s.promiseToYouParts(who) else s.promiseFromYouParts(who)
                // The name is its person's card (owner, 14 окт), the rest of the row opens the matter.
                // On the planner page the name keeps the italic's grey — still a tap to the person (owner's sample, 15 окт).
                append(a); withLink(androidx.compose.ui.text.LinkAnnotation.Clickable("p", androidx.compose.ui.text.TextLinkStyles(SpanStyle(color = if (planner) c.graphite else c.link))) { links.onName(who) }) { append(name) }; append(b)
            }, style = if (planner) androidx.compose.ui.text.TextStyle(fontFamily = Literata, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, color = c.graphite, fontSize = 15.sp, lineHeight = 20.sp)
                else Type.small.copy(fontFamily = rowFamily, color = subInk, fontSize = subSize), modifier = Modifier.padding(top = 2.dp))
            if (nudge) {
                Text("  ·  ", style = Type.small.copy(color = subInk, fontSize = subSize), modifier = Modifier.padding(top = 3.dp))
                Text(s.remindLink, style = Type.small.copy(color = c.link, fontSize = subSize, fontWeight = FontWeight.Medium),
                    modifier = Modifier.padding(top = 3.dp).clip(RoundedCornerShape(6.dp)).clickable { remindAbout(ctx, data, t) })
            }
        }
        if (LocalInline.current?.open == t.id) LocalVm.current?.let { InlineMatterBody(t.id, it) }
    }
}

/** One thing of a day, in the order the day is read: late first, then by the hour, then the rest; done at the end. */
sealed class DayItem(val key: String) {
    class Matter(val t: Task) : DayItem("m" + t.id)
    class Event(val e: CalEvent) : DayItem("e" + e.title + e.start)
    class Done(val t: Task) : DayItem("d" + t.id)
    class Note(val n: DayNote) : DayItem("n" + n.owner.id + n.at + (n.file?.id ?: ""))
}

fun dayItems(plan: DayPlan, today: LocalDate): List<DayItem> {
    val late = plan.deals.filter { it.dueDate?.isBefore(today) == true }
    val rest = plan.deals - late.toSet()
    val allDay = plan.events.filter { it.allDay }
    // Hours of matters and meetings in one order: a meeting at 12 stands between the matters at 11 and 13.
    val timed = (rest.filter { it.time != null }.map { it.time!! to DayItem.Matter(it) } +
        plan.events.filter { !it.allDay }.map { it.start.toLocalTime().toString().take(5) to DayItem.Event(it) }).sortedBy { it.first }.map { it.second }
    val loose = rest.filter { it.time == null }
    return late.map { DayItem.Matter(it) } + allDay.map { DayItem.Event(it) } + timed + loose.map { DayItem.Matter(it) } +
        plan.feed.map { DayItem.Note(it) } + plan.done.map { DayItem.Done(it) }
}

/** The head of a topic's block in a list: «# Тема ›» in petrol — a tap opens the topic. */
data class TopicHead(val id: String, val title: String)

/**
 * Owner, 16 окт: a topic's matters standing in one list are one block under the topic's name, not scattered rows each with its
 * own «# Тема». The block stands where the topic's first matter stood; a topic with one matter keeps the link under its words.
 */
fun <T : Any> withTopicHeads(items: List<T>, min: Int = 2, topicOf: (T) -> Pair<String, String>?): List<Any> {
    val counts = items.mapNotNull { topicOf(it)?.first }.groupingBy { it }.eachCount()
    val placed = mutableSetOf<String>()
    val out = mutableListOf<Any>()
    items.forEach { x ->
        val tp = topicOf(x)
        if (tp == null || (counts[tp.first] ?: 0) < min) out.add(x)
        else if (placed.add(tp.first)) { out.add(TopicHead(tp.first, tp.second)); out.addAll(items.filter { topicOf(it)?.first == tp.first }) }
    }
    return out
}

/** Where a row stands in a topic's block: the head (round top corners), inside, or the last (round bottom corners). */
enum class BlockPos { Head, Mid, Last }

/**
 * A topic in the day (owner, 16 окт): «# Тема ▸», under it how many of its matters stand here (how many late) and «открыть»;
 * on the margin the nearest of them — late since, or its hour. A tap unfolds its matters right here, on the block's tint.
 */
@Composable
fun TopicFoldRow(title: String, rows: List<Task>, today: LocalDate, note: Pair<String?, Boolean>, open: Boolean, onToggle: () -> Unit, onOpen: () -> Unit,
                 lit: Boolean = false) {
    val c = M.c
    val s = M.s
    val late = rows.count { !it.done && it.dueDate?.isBefore(today) == true }
    // Unfolded, its matters carry their own hours — the head doesn't repeat one.
    Margined(Modifier.landing(lit, 0), margin = { if (!open) RowMargin(note.first, note.second) }) {
        Box(Modifier.blockTint(if (open) BlockPos.Head else null)) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(onClickLabel = if (open) s.collapse else s.expandWord, onClick = onToggle)
                .padding(top = RowTop - 2.dp, bottom = RowTop, end = 14.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Text("# $title", style = Type.body.copy(fontFamily = rowFamily, color = c.ink, fontSize = rowSize, lineHeight = rowLine), maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(top = 2.dp))
                    Text(if (open) "▾" else "▸", style = Type.body.copy(color = c.graphite, fontSize = 15.sp), modifier = Modifier.padding(start = 8.dp, top = 4.dp))
                }
                Row(Modifier.padding(top = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(s.summaryDeals(rows.size) + (if (late > 0) ", " + s.lateOfDay(late) else "") + "  ·  ", style = Type.small.copy(color = subInk, fontSize = subSize))
                    Text(s.openTopicLink, style = Type.small.copy(color = c.link, fontSize = subSize, fontWeight = FontWeight.Medium),
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onOpen).padding(vertical = 2.dp))
                }
            }
        }
    }
}

/** How far a block's tint reaches past the words on each side. */
private val BlockBleed = 10.dp

/**
 * A topic's block on «Сегодня» (owner, 16 окт: the bracket was «не очень» — a light tint instead): one soft sea tint under the
 * head and its matters, a little wider than the words, no line and no shadow. Each row draws its own slice of it.
 */
fun Modifier.blockTint(pos: BlockPos?): Modifier = if (pos == null) this else composed {
    val c = M.c
    val tint = c.sea.copy(alpha = if (c.dark) 0.12f else 0.06f)
    drawBehind {
        val r = 12.dp.toPx()
        val top = if (pos == BlockPos.Head) androidx.compose.ui.geometry.CornerRadius(r) else androidx.compose.ui.geometry.CornerRadius.Zero
        val bottom = if (pos == BlockPos.Last) androidx.compose.ui.geometry.CornerRadius(r) else androidx.compose.ui.geometry.CornerRadius.Zero
        val path = androidx.compose.ui.graphics.Path().apply {
            addRoundRect(androidx.compose.ui.geometry.RoundRect(-BlockBleed.toPx(), 0f, size.width + BlockBleed.toPx(), size.height, top, top, bottom, bottom))
        }
        drawPath(path, tint)
    }
}

@Composable
fun TopicBlockHead(title: String, raw: Boolean = false, top: androidx.compose.ui.unit.Dp = 14.dp, onOpen: () -> Unit) {
    val c = M.c
    Margined(Modifier.padding(top = top)) {
        Box(Modifier.fillMaxWidth().blockTint(BlockPos.Head).padding(top = 4.dp)) {
            Text(if (raw) title else "# $title", style = Type.small.copy(color = c.link, fontSize = 14.5.sp, fontWeight = FontWeight.Medium), maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onOpen).padding(vertical = 6.dp))
        }
    }
}

/** The place of each item of [blocks] (from [withTopicHeads]) in its topic's block; null — not in a block. */
fun blockPositions(blocks: List<Any>, topicOf: (Any) -> String?): List<BlockPos?> {
    val inBlock = blocks.filterIsInstance<TopicHead>().map { it.id }.toSet()
    return blocks.mapIndexed { i, b ->
        when {
            b is TopicHead -> BlockPos.Head
            topicOf(b)?.let { it in inBlock } != true -> null
            blocks.getOrNull(i + 1)?.let { n -> n !is TopicHead && topicOf(n) == topicOf(b) } == true -> BlockPos.Mid
            else -> BlockPos.Last
        }
    }
}

/** The rows of a day as one panel; the done ones past [doneShown] fold into «ещё N сделано». */
fun LazyListScope.dayPanel(
    plan: DayPlan, today: LocalDate, keyPrefix: String, landed: Set<String>,
    onOpen: (String) -> Unit, onDone: (String) -> Unit, onLater: (String) -> Unit, onTime: (String) -> Unit,
    doneOpen: Boolean = true, onDoneFold: (() -> Unit)? = null,
    /**
     * Owner, 16 окт: a topic stands in the day as itself — one row, folded by default; its matters are not shown apart. Which
     * topics are unfolded; null — the older blocks.
     */
    openTopics: Set<String>? = null, onTopicFold: (String) -> Unit = {},
) {
    val all = dayItems(plan, today)
    val doneAll = all.filterIsInstance<DayItem.Done>()
    // More than three done: folded into one row «сделано N ›»; open, the same row at the end folds them back.
    val foldable = onDoneFold != null && doneAll.size > 3
    val foldDone = foldable && !doneOpen
    val shown = if (foldDone) all.filter { it !is DayItem.Done } else all
    val n = shown.size + (if (foldable) 1 else 0)
    val fold = openTopics != null
    val blocks = withTopicHeads(shown, if (fold) 1 else 2) { d -> (d as? DayItem.Matter)?.t?.let { t -> t.topicId?.let { it to (t.topicTitle ?: "") } } }
    val inBlock = blocks.filterIsInstance<TopicHead>().map { it.id }.toSet()
    val pos = blockPositions(blocks) { (it as? DayItem.Matter)?.t?.topicId }
    var i = -1
    blocks.forEachIndexed { bi, b ->
        if (b is TopicHead) {
            if (fold) {
                val rows = shown.filterIsInstance<DayItem.Matter>().map { it.t }.filter { it.topicId == b.id }
                val open = b.id in openTopics!!
                item(keyPrefix + "g" + b.id) { Box(Modifier.animateItem()) {
                    TopicFoldRow(b.title, rows, today, whenNote(rows.first(), today, underDay = true), open, { onTopicFold(b.id) }, { onOpen(b.id) }, lit = b.id in landed)
                } }
            } else item(keyPrefix + "g" + b.id) { Box(Modifier.animateItem()) { TopicBlockHead(b.title) { onOpen(b.id) } } }
            return@forEachIndexed
        }
        val it = b as DayItem
        // A folded topic keeps its matters inside it.
        if (fold && it is DayItem.Matter && it.t.topicId != null && it.t.topicId !in openTopics!!) return@forEachIndexed
        i++
        val seat = seatOf(i, n)
        item(keyPrefix + it.key) {
            Box(Modifier.animateItem()) {
                when (it) {
                    is DayItem.Matter -> MatterRow(it.t, seat, today, whenNote(it.t, today, underDay = true),
                        onToggle = { onDone(it.t.id) }, onOpen = { onOpen(it.t.id) }, onTime = { onTime(it.t.id) }, onLater = { onLater(it.t.id) },
                        lit = it.t.id in landed || (it.t.topicId != null && it.t.topicId in landed), onOpenTopic = onOpen, showTopic = it.t.topicId !in inBlock, block = pos[bi])
                    is DayItem.Event -> EventPaperRow(it.e, seat)
                    is DayItem.Done -> DonePaperRow(it.t, seat, onToggle = { onDone(it.t.id) }, onOpen = { onOpen(it.t.topicId ?: it.t.id) })
                    is DayItem.Note -> NotePaperRow(it.n, seat, onOpen)
                }
            }
        }
    }
    if (foldable) item(keyPrefix + "done-fold") {
        PaperRow(seatOf(n - 1, n), mark = { TickGlyph(M.c.graphite, 16.dp) }, onClick = onDoneFold) {
            RowTitle(if (foldDone) M.s.doneCountWord(doneAll.size) + "  ›" else M.s.collapse + "  ‹", faded = true)
        }
    }
}

/** Nothing written yet: phrases to try — a tap puts one into the field, the user sends it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TryLine(onTry: (String) -> Unit) {
    val s = M.s
    Margined(Modifier.padding(top = 18.dp)) {
        Column {
            Voice(s.tryHint)
            FlowRow(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                s.tryExamples.forEach { ex -> Chip(ex, on = false, onClick = { onTry(ex) }) }
            }
        }
    }
}

/** A line of Margin's voice under the lists: «Во «Входящих» ждут: 2 ›», «Завтра — 3 дела ›». */
@Composable
fun VoiceLink(text: String, onClick: () -> Unit, quiet: Boolean = false, arrow: String = "›") {
    Margined(Modifier.padding(top = 10.dp)) { Voice("$text  $arrow", link = !quiet, onClick = onClick, modifier = Modifier.heightIn(min = 40.dp)) }
}

/** The day's summary under its name, in Margin's voice: «4 дела · 1 встреча · с прошлых дней: 2». */
@Composable
fun daySummary(plan: DayPlan, today: LocalDate, away: String? = null): androidx.compose.ui.text.AnnotatedString {
    val c = M.c
    val s = M.s
    val late = plan.deals.count { it.dueDate?.isBefore(today) == true }
    // Owner, 10 окт: «с прошлых дней: 1» read as a riddle — say it in words, right after the count it belongs to:
    // «7 дел, 2 просрочены · 1 встреча».
    return buildAnnotatedString {
        if (plan.deals.isEmpty() && plan.events.isEmpty()) append(s.freeDay)
        if (plan.deals.isNotEmpty()) {
            append(s.summaryDeals(plan.deals.size))
            if (late > 0) { append(", "); pencil { append(s.lateOfDay(late)) } }
        }
        if (plan.events.isNotEmpty()) { if (plan.deals.isNotEmpty()) append("  ·  "); append(s.summaryMeetings(plan.events.size)) }
        // Without the cloud Margin reads on the phone and the unsure waits in «Входящие» — said once, quietly, here.
        away?.let { append("  ·  "); withStyle(SpanStyle(color = c.faint)) { append(it) } }
    }
}
