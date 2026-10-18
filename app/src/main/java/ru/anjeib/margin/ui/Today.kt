package ru.anjeib.margin.ui

import androidx.compose.foundation.combinedClickable
import androidx.compose.runtime.collectAsState

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
import androidx.compose.ui.layout.layout
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
 * The feed (owner, 18 окт: «слева оставить только время»): the margin holds hours alone; the rest of «когда» — late since, a
 * window, how long it lies — goes into the quiet line under the words.
 */
val LocalHoursOnly = androidx.compose.runtime.staticCompositionLocalOf { false }

/** [note] parted for the feed: the hour for the margin, the other words for the line under the row. */
fun hoursAndRest(note: Pair<String?, Boolean>): Pair<String?, String?> {
    val lines = note.first?.split("\n")?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
    val hour = lines.lastOrNull { isHour(it) }
    val rest = lines.filter { it != hour }.joinToString(" ").replace("– ", "–").ifBlank { null }
    // Late is told by colour alone (the knot is terracotta too): «просрочено» under every row was a red wall (18 окт).
    return hour to rest
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
    val hoursOnly = LocalHoursOnly.current
    val (marginText, whenSub) = if (hoursOnly) hoursAndRest(note) else note.first to null
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
                        TaskLine(t, whenSub, showProject = true, today = today, tappable = true, hidePerson = hidePerson,
                            topic = t.topicTitle?.takeIf { showTopic }, extra = listOfNotNull(days, extra).joinToString("  ·  ").ifBlank { null },
                            onTopic = t.topicId?.let { tid -> onOpenTopic?.let { f -> { f(tid) } } })
                    }
                }
            }
        }
    }
    // «Полоса набора» (owner, 15 окт): the hour on the margin is the row's «done»; a row with no hour has only the knot (owner, 16 окт: no dash).
    val marginSlot: (@Composable () -> Unit)? = if (planner) ({ MarginTap(::tick, late = note.second && !t.done && !ticking, done = t.done || ticking) { RowMargin(marginText, note.second && !t.done && !ticking && !hoursOnly) } })
        else marginText?.let { m -> { RowMargin(m, note.second) } }
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
    val hoursOnly = LocalHoursOnly.current
    PaperRow(seat, margin = if (e.allDay) (if (hoursOnly) null else s.allDay.lowercase()) else e.start.toLocalTime().toString().take(5),
        mark = { CalendarGlyph(c.graphite, 20.dp) },
        onClick = {
            val ms = e.start.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            runCatching { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("content://com.android.calendar/time/$ms"))) }
        }) {
        RowTitle(e.title)
        val sub = listOfNotNull(if (e.allDay) s.allDay.lowercase().takeIf { hoursOnly } else e.start.toLocalTime().toString().take(5) + "–" + e.end.toLocalTime().toString().take(5), e.location).joinToString("  ·  ")
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
    // Owner, 18 окт: matters with no hour first — what is to be done some time today — then the day by its hours.
    return late.map { DayItem.Matter(it) } + allDay.map { DayItem.Event(it) } + loose.map { DayItem.Matter(it) } + timed +
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
enum class BlockPos { Head, Mid, Last, Single }

/**
 * A topic in the day (owner, 16 окт): «# Тема ▸», under it how many of its matters stand here (how many late) and «открыть»;
 * on the margin the nearest of them — late since, or its hour. A tap unfolds its matters right here, on the block's tint.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun TopicFoldRow(title: String, rows: List<Task>, today: LocalDate, note: Pair<String?, Boolean>, open: Boolean, onToggle: () -> Unit, onOpen: () -> Unit,
                 lit: Boolean = false, /** The topic itself: a long press offers what its page did. */ topicId: String? = null,
                 /** Its notes and done matters on the day, counted with its open matters. */ notes: Int = 0, done: Int = 0,
                 /** Unfolded, the margin still says [note] — a window its matters share and don't repeat. */ marginOpen: Boolean = false) {
    val c = M.c
    val s = M.s
    var hold by remember { mutableStateOf(false) }
    topicId?.let { TopicHold(it, hold) { hold = false } }
    val late = rows.count { !it.done && it.dueDate?.isBefore(today) == true }
    // In the feed only an hour stays on the margin; a window or «с 14 окт» leads the line under the title.
    val (hour, lead) = if (LocalHoursOnly.current) hoursAndRest(note.first to false) else note.first to null
    // Unfolded, its matters carry their own hours — the head doesn't repeat one.
    Margined(Modifier.landing(lit, 0).padding(top = if (open) 0.dp else 4.dp, bottom = if (open) 0.dp else 8.dp), margin = { if (!open || marginOpen) RowMargin(hour, note.second) }) {
        // Owner, 16 окт («темы теряются в ленте»): a topic always lies on the block's light tint — folded it is a small block of
        // its own, unfolded the tint runs on under its matters.
        Box(Modifier.blockTint(if (open) BlockPos.Head else BlockPos.Single)) {
            // The press lights the whole tint, not a smaller box inside it (owner, 18 окт: a second rectangle in the block).
            Column(Modifier.fillMaxWidth().bleedLeft(BlockBleed).clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomStart = if (open) 0.dp else 12.dp, bottomEnd = if (open) 0.dp else 12.dp))
                .combinedClickable(onClickLabel = if (open) s.collapse else s.expandWord, onClick = onToggle,
                onLongClick = if (topicId != null) ({ hold = true }) else null)
                .padding(start = BlockBleed, top = RowTop - 2.dp, bottom = RowTop, end = 12.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    WordText(androidx.compose.ui.text.buildAnnotatedString {
                        withStyle(androidx.compose.ui.text.SpanStyle(color = c.link)) { append("# ") }
                        append(title)
                    }, style = Type.body.copy(fontFamily = rowFamily, color = c.ink, fontSize = rowSize, lineHeight = rowLine), maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(top = 2.dp))
                    // A chevron the size of the words (owner, 18 окт: the tiny ▸ in the corner read as a stray dot).
                    Text(if (open) "⌃" else "⌄", style = Type.body.copy(color = c.graphite, fontSize = 20.sp, lineHeight = 20.sp), modifier = Modifier.padding(start = 8.dp, top = if (open) 6.dp else 0.dp))
                }
                Row(Modifier.padding(top = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    val what = listOfNotNull(lead, rows.size.takeIf { it > 0 }?.let { s.summaryDeals(it) }, notes.takeIf { it > 0 }?.let { s.nEntries(it) },
                        done.takeIf { it > 0 }?.let { s.doneCountWord(it) }).joinToString(", ")
                    Text((what + (if (late > 0) ", " + s.lateOfDay(late) else "")).replaceFirst(", ", if (lead != null) "  ·  " else ", ") + "  ·  ", style = Type.small.copy(color = subInk, fontSize = subSize))
                    Text(s.openTopicLink, style = Type.small.copy(color = c.link, fontSize = subSize, fontWeight = FontWeight.Medium),
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onOpen).padding(vertical = 2.dp))
                }
            }
        }
    }
}

/** How far a block's tint reaches left of the words, into the gap by the rule; on the right it ends with the day's line. */
private val BlockBleed = 10.dp

/** Lays the content [d] wider, reaching further left than its slot — over the tint's bleed. */
private fun Modifier.bleedLeft(d: androidx.compose.ui.unit.Dp): Modifier = this.layout { m, cs ->
    val px = d.roundToPx()
    if (!cs.hasBoundedWidth) { val p = m.measure(cs); return@layout layout(p.width, p.height) { p.place(0, 0) } }
    val p = m.measure(cs.copy(minWidth = cs.minWidth + px, maxWidth = cs.maxWidth + px))
    layout(p.width - px, p.height) { p.place(-px, 0) }
}

/**
 * A topic's block on «Сегодня» (owner, 16 окт: the bracket was «не очень» — a light tint instead): one soft sea tint under the
 * head and its matters, a little wider than the words, no line and no shadow. Each row draws its own slice of it.
 */
fun Modifier.blockTint(pos: BlockPos?): Modifier = if (pos == null) this else composed {
    val c = M.c
    val tint = c.sea.copy(alpha = if (c.dark) 0.12f else 0.06f)
    drawBehind {
        val r = 12.dp.toPx()
        val top = if (pos == BlockPos.Head || pos == BlockPos.Single) androidx.compose.ui.geometry.CornerRadius(r) else androidx.compose.ui.geometry.CornerRadius.Zero
        val bottom = if (pos == BlockPos.Last || pos == BlockPos.Single) androidx.compose.ui.geometry.CornerRadius(r) else androidx.compose.ui.geometry.CornerRadius.Zero
        val path = androidx.compose.ui.graphics.Path().apply {
            addRoundRect(androidx.compose.ui.geometry.RoundRect(-BlockBleed.toPx(), 0f, size.width, size.height, top, top, bottom, bottom))
        }
        drawPath(path, tint)
    }
}

@Composable
fun TopicBlockHead(title: String, raw: Boolean = false, top: androidx.compose.ui.unit.Dp = 14.dp, onOpen: () -> Unit) {
    val c = M.c
    Margined(Modifier.padding(top = top)) {
        Box(Modifier.fillMaxWidth().blockTint(BlockPos.Head).padding(top = 4.dp)) {
            // As a topic's row in the day (owner, 16 окт: one size for one kind of thing): the book face, the mark in petrol.
            val full = if (raw) title else "# $title"
            val mark = listOf("# ", "○ ").firstOrNull { full.startsWith(it) }
            WordText(androidx.compose.ui.text.buildAnnotatedString {
                if (mark != null) { withStyle(androidx.compose.ui.text.SpanStyle(color = c.link)) { append(mark) }; append(full.removePrefix(mark)) } else append(full)
            }, style = Type.body.copy(fontFamily = rowFamily, color = c.ink, fontSize = rowSize, lineHeight = rowLine), maxLines = 1,
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
     * Owner, 16–17 окт: a topic with two or more things on the day (matters and notes) stands as one row — unfolded, they are
     * under it by the hour; a topic with one thing has no row of its own, the thing stands plain with «# Тема» under it. Which
     * topics are unfolded on this day; null — the older blocks.
     */
    openTopics: Set<String>? = null, onTopicFold: (String) -> Unit = {},
    /**
     * Owner, 17 окт («почему события в ленте идут не по времени?»): other rows of the day with their hour — the notes of
     * matters and topics — laid in among the matters by that hour, or into their topic's row.
     */
    extras: List<DayExtra> = emptyList(),
) {
    val all = dayItems(plan, today)
    val doneAll = all.filterIsInstance<DayItem.Done>()
    // More than three done: folded into one row «сделано N ›»; open, the same row at the end folds them back.
    val foldable = onDoneFold != null && doneAll.size > 3
    val foldDone = foldable && !doneOpen
    val shown = if (foldDone) all.filter { it !is DayItem.Done } else all
    val n = shown.size + (if (foldable) 1 else 0)
    val fold = openTopics != null
    val matters = shown.filterIsInstance<DayItem.Matter>()
    val notesOf = extras.filter { it.topicId != null }.groupBy { it.topicId!! }
    // Two or more things of one topic on the day make its row.
    val doneOf = shown.filterIsInstance<DayItem.Done>().map { it.t }.filter { it.topicId != null }
    val grouped: Set<String> = if (!fold) emptySet() else (matters.mapNotNull { it.t.topicId } + extras.mapNotNull { it.topicId } + doneOf.mapNotNull { it.topicId })
        .groupingBy { it }.eachCount().filterValues { it >= 2 }.keys + notesOf.keys  // a topic's note never stands apart from its topic (owner, 17 окт)
    val titles = matters.mapNotNull { m -> m.t.topicId?.let { it to (m.t.topicTitle ?: "") } }.toMap() +
        extras.mapNotNull { e -> e.topicId?.let { it to (e.topicTitle ?: "") } }.toMap()
    val withMatters = matters.mapNotNull { it.t.topicId }.toSet()
    val titlesDone = doneOf.associate { it.topicId!! to (it.topicTitle ?: "") }

    /** A topic's row, and — unfolded — its matters and notes under it by the hour. */
    fun LazyListScope.group(id: String) {
        val ms = matters.map { it.t }.filter { it.topicId == id }
        val ns = notesOf[id].orEmpty()
        val ds = doneOf.filter { it.topicId == id }
        val open = id in openTopics!!
        // Owner, 18 окт: «19–25 окт» on the margin of each of its seven matters said nothing. A window all of them share is said
        // once, by the topic's row (open or folded); its matters keep only their hours.
        val span = ms.map { it.startDate to it.dueDate }.distinct().singleOrNull()
            ?.takeIf { (a, b) -> a != null && b != null && a.isBefore(b) && !b.isBefore(today) }
        fun noteOf(x: Task) = whenNote(x, today, underDay = true).let { n -> if (span == null || n.second) n else (x.time ?: x.dayPart?.let { L.s.dayPart(it) }) to false }
        val first = span?.let { (a, b) -> spanWord(a!!, b!!, today) to false }
            ?: ms.firstOrNull()?.let { whenNote(it, today, underDay = true) } ?: (ns.minOfOrNull { it.hour } to false)
        item(keyPrefix + "g" + id) { Box(Modifier.animateItem()) {
            TopicFoldRow(titles[id] ?: titlesDone[id] ?: "", ms, today, first, open, { onTopicFold(id) }, { onOpen(id) }, lit = id in landed, topicId = id, notes = ns.size, done = ds.size,
                marginOpen = span != null)
        } }
        if (!open) return
        // Inside a topic the same order: its matters with no hour first, then matters and notes by the hour.
        val members: List<Pair<String, Any>> = (ms.map { (it.time?.take(5) ?: "") to (it as Any) } + ns.map { it.hour to (it as Any) }).sortedBy { it.first } +
            ds.map { "99" to (DayItem.Done(it) as Any) }
        members.forEachIndexed { k, (_, x) ->
            val pos = if (k == members.lastIndex) BlockPos.Last else BlockPos.Mid
            when (x) {
                is Task -> item(keyPrefix + x.id) { Box(Modifier.animateItem()) {
                    MatterRow(x, seatOf(0, 1), today, noteOf(x),
                        onToggle = { onDone(x.id) }, onOpen = { onOpen(x.id) }, onTime = { onTime(x.id) }, onLater = { onLater(x.id) },
                        lit = x.id in landed, onOpenTopic = onOpen, showTopic = false, block = pos)
                } }
                is DayExtra -> x.emit(this, pos)
                is DayItem.Done -> item(keyPrefix + x.key) { Box(Modifier.animateItem()) {
                    DonePaperRow(x.t, seatOf(0, 1), onToggle = { onDone(x.t.id) }, onOpen = { onOpen(x.t.id) })
                } }
            }
        }
    }

    // What waits to go in by the hour: notes standing alone, and topics that are only notes on this day (their row).
    val waiting = (extras.filter { it.topicId == null || it.topicId !in grouped } +
        grouped.filter { it !in withMatters }.map { id -> DayExtra(notesOf[id]?.minOf { it.hour } ?: "99", "ng$id", null, null) { _ -> group(id) } })
        .sortedBy { it.hour }.toMutableList()
    var timedSeen = false
    fun flush(before: String?) {
        while (waiting.isNotEmpty() && (before == null || waiting.first().hour < before)) waiting.removeAt(0).emit(this, null)
    }

    val blocks = if (fold) withTopicHeads(shown, 1) { d -> (d as? DayItem.Matter)?.t?.topicId?.takeIf { it in grouped }?.let { it to (titles[it] ?: "") } }
        else withTopicHeads(shown, 2) { d -> (d as? DayItem.Matter)?.t?.let { t -> t.topicId?.let { it to (t.topicTitle ?: "") } } }
    val inBlock = blocks.filterIsInstance<TopicHead>().map { it.id }.toSet()
    val pos = blockPositions(blocks) { (it as? DayItem.Matter)?.t?.topicId }
    var i = -1
    blocks.forEachIndexed { bi, b ->
        if (b is TopicHead) {
            // The topic's row stands by the earliest hour of what is in it — a matter's or a note's.
            (matters.mapNotNull { m -> m.t.time?.take(5)?.takeIf { m.t.topicId == b.id } } + notesOf[b.id].orEmpty().map { it.hour }).minOrNull()
                ?.let { timedSeen = true; flush(it) }
            if (fold) group(b.id)
            else item(keyPrefix + "g" + b.id) { Box(Modifier.animateItem()) { TopicBlockHead(b.title) { onOpen(b.id) } } }
            return@forEachIndexed
        }
        val it = b as DayItem
        // The extras go in by the hour: before the first later timed row; all of them before the untimed rows after the timed ones.
        val hour = when (it) {
            is DayItem.Matter -> it.t.time?.take(5)
            is DayItem.Event -> if (it.e.allDay) null else it.e.start.toLocalTime().toString().take(5)
            else -> null
        }
        if (hour != null) { timedSeen = true; flush(hour) } else if (timedSeen || it is DayItem.Done) flush(null)
        // A topic's row holds its matters.
        if (fold && it is DayItem.Matter && it.t.topicId != null && it.t.topicId in grouped) return@forEachIndexed
        if (fold && it is DayItem.Done && it.t.topicId != null && it.t.topicId in grouped) return@forEachIndexed
        i++
        val seat = seatOf(i, n)
        item(keyPrefix + it.key) {
            Box(Modifier.animateItem()) {
                when (it) {
                    is DayItem.Matter -> MatterRow(it.t, seat, today, whenNote(it.t, today, underDay = true),
                        onToggle = { onDone(it.t.id) }, onOpen = { onOpen(it.t.id) }, onTime = { onTime(it.t.id) }, onLater = { onLater(it.t.id) },
                        lit = it.t.id in landed || (it.t.topicId != null && it.t.topicId in landed), onOpenTopic = onOpen, showTopic = it.t.topicId !in inBlock, block = if (fold) null else pos[bi])
                    is DayItem.Event -> EventPaperRow(it.e, seat)
                    is DayItem.Done -> DonePaperRow(it.t, seat, onToggle = { onDone(it.t.id) }, onOpen = { onOpen(it.t.topicId ?: it.t.id) })
                    is DayItem.Note -> NotePaperRow(it.n, seat, onOpen)
                }
            }
        }
    }
    flush(null)
    if (foldable) item(keyPrefix + "done-fold") {
        PaperRow(seatOf(n - 1, n), mark = { TickGlyph(M.c.graphite, 16.dp) }, onClick = onDoneFold) {
            RowTitle(if (foldDone) M.s.doneCountWord(doneAll.size) + "  ›" else M.s.collapse + "  ‹", faded = true)
        }
    }
}

/** A row of the day besides its matters (a note), with its hour and, when it is a topic's, the topic. */
class DayExtra(val hour: String, val key: String, val topicId: String?, val topicTitle: String?, val emit: LazyListScope.(BlockPos?) -> Unit)

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

/**
 * What a topic's page used to offer, now on a long press of its row (owner, 17 окт: no topic page): rename it, share it as a
 * PDF, put it into the archive with all that is in it.
 */
@Composable
fun TopicHold(topicId: String, open: Boolean, onDismiss: () -> Unit) {
    val vm = LocalVm.current ?: return
    val c = M.c
    val s = M.s
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val t = vm.data.collectAsState().value.tasks.find { it.id == topicId } ?: return
    var dialog by remember(topicId) { mutableStateOf<String?>(null) }
    // On the right, as every row's long-press menu.
    Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.TopEnd) { Box { SoftMenu(open, onDismiss) {
        SoftMenuItem(s.rename) { onDismiss(); dialog = "rename" }
        SoftMenuItem(s.sharePdf) { onDismiss(); shareTopic(ctx, t) }
        SoftMenuItem(s.toArchive) { onDismiss(); dialog = "archive" }
    } } }
    when (dialog) {
        "rename" -> TextDialog(s.rename, t.title, "", onDismiss = { dialog = null }) { v ->
            if (v.isNotBlank()) vm.saveTask(t.copy(title = v.trim().replaceFirstChar { it.uppercase() })); dialog = null
        }
        "archive" -> androidx.compose.material3.AlertDialog(
            onDismissRequest = { dialog = null }, containerColor = c.card,
            title = { Text(s.archiveQ, style = Type.cardTitle.copy(color = c.ink)) },
            text = { Text(s.archiveText(t.title), style = Type.body.copy(color = c.graphite)) },
            confirmButton = { androidx.compose.material3.TextButton({ dialog = null; vm.archiveTopic(t.id); if (vm.topicFilter.value == t.id) vm.topicFilter.value = null }) { Text(s.toArchive, color = c.link) } },
            dismissButton = { androidx.compose.material3.TextButton({ dialog = null }) { Text(s.cancel, color = c.graphite) } },
        )
    }
}
