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
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.layout.layout
import androidx.compose.foundation.gestures.scrollBy
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.anjeib.margin.data.dayMonth
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
    // The name and its line turn with the day only where they say the day — «Сегодня»'s weekday and summary; «Заметки» and
    // «Темы» stand still while their days turn under them (owner, 17 окт).
    val headKey = if (page.section == 0) page else page.copy(day = LocalDate.ofEpochDay(0))
    Column(Modifier.fillMaxWidth().sectionSwipe()) {
      Box(Modifier.fillMaxWidth()) {
        // How the sections turn (owner, 17 окт): a quiet hint level with the calendar, in the middle like «дни» over the field.
        Box(Modifier.fillMaxWidth().height(40.dp), contentAlignment = Alignment.Center) { NavHint(s.navSections) }
        Margined(margin = {
            // The date stays where it is, level with the name under the hint; only its figures change with the day.
            Column(Modifier.padding(top = 40.dp), horizontalAlignment = Alignment.End) {
                AnimatedContent(page.day, transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(140)) }, label = "frame-day") { d ->
                    MarginDay(d.dayOfMonth, d.dayMonthShort().substringAfter(' '))
                }
                // Another day: the way back stands under the date, on the margin — the head keeps its height (owner, 17 окт).
                val away = page.section != 3 && page.day != LocalDate.now()
                // It takes no room: drawn under the date, the margin's column keeps the date's own height.
                androidx.compose.animation.AnimatedVisibility(away, Modifier.layout { m, cs -> val p = m.measure(cs); layout(p.width, 0) { p.place(0, 0) } },
                    enter = fadeIn(tween(200)), exit = fadeOut(tween(120))) {
                    androidx.compose.material3.Text(s.todayWordLink, style = Type.small.copy(color = c.link, fontSize = subSize, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium),
                        modifier = Modifier.offset(y = (-6).dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(6.dp)).clickable { vm.pageDay.value = null; vm.feedJump.value++ }.padding(vertical = 4.dp, horizontal = 2.dp))
                }
            }
        }) {
            // The name and its line on the left; on the right the four marks in one even block (owner, 16 окт: «иконки
            // некрасиво расположены и разные по цвету/размеру») — calendar · search over personal · work.
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    // Room for the sections' hint, level with the calendar (drawn across the whole head, below).
                    Box(Modifier.height(40.dp))
                    ContentTurn(headKey, { it.order() }) { p ->
                        val name = when (p.section) { 0 -> p.day.dowFull(); 1 -> s.diaryTitle; 2 -> s.topicsPage; else -> s.contentsTitle }
                        BasicText(name, style = Type.pageTitle.copy(color = c.ink), maxLines = 1, modifier = Modifier.fillMaxWidth(),
                            autoSize = TextAutoSize.StepBased(minFontSize = 26.sp, maxFontSize = 44.sp, stepSize = 1.sp))
                    }
                    // Room for two lines, so a shorter or longer line doesn't move the rule and the rows under it.
                    Box(Modifier.padding(top = 2.dp).heightIn(min = 42.dp)) { ContentTurn(headKey, { it.order() }) { p -> Column { sub(p) } } }
                }
                // «Содержание» needs none of them (owner, 16 окт): they fade, but keep their room — nothing in the head moves.
                val shownTools by androidx.compose.animation.core.animateFloatAsState(if (page.section != 3) 1f else 0f, tween(200), label = "tools")
                Box(Modifier.graphicsLayer { alpha = shownTools }) { FrameTools(sphere, enabled = page.section != 3) { vm.diarySphere.value = it } }
            }
        }
      }
        DoubleRule(Modifier.padding(top = 14.dp, bottom = 4.dp))
    }
}

/** «‹  дни  ›», «‹  разделы  ›»: which way things turn here — a hint, quiet, not a button. */
@Composable
fun NavHint(word: String, modifier: Modifier = Modifier) = androidx.compose.material3.Text("‹   $word   ›",
    style = Type.small.copy(color = M.c.graphite.copy(alpha = 0.8f), fontSize = subSize, letterSpacing = 0.6.sp), modifier = modifier)

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

/** A list that counts its rows as they are laid out — to know where each day of a feed begins. */
class CountingScope(private val inner: androidx.compose.foundation.lazy.LazyListScope) : androidx.compose.foundation.lazy.LazyListScope by inner {
    var n = 0
    /** Keys already laid out: a row met twice (the same thing reached by two ways) is shown once — a twice-used key would end the app. */
    private val used = HashSet<Any>()
    override fun item(key: Any?, contentType: Any?, content: @Composable androidx.compose.foundation.lazy.LazyItemScope.() -> Unit) {
        if (key != null && !used.add(key)) { android.util.Log.w("Margin", "feed: key $key twice, shown once"); return }
        n++; inner.item(key, contentType, content)
    }
    override fun items(count: Int, key: ((Int) -> Any)?, contentType: (Int) -> Any?, itemContent: @Composable androidx.compose.foundation.lazy.LazyItemScope.(Int) -> Unit) {
        n += count; inner.items(count, key, contentType, itemContent)
    }
}

/**
 * A day in the feed (owner, 18 окт: «а нужны ли полосы?… по центру разделять датами дни, слева оставить только время»): no line —
 * the day in words, small, in the middle of the page's column, with air above it. Today in petrol; an empty today says so.
 */
@Composable
fun FeedDayHead(day: LocalDate, today: LocalDate, /** Nothing on it: «· свободный день» after the date. */ free: Boolean = false) {
    val c = M.c
    val s = M.s
    val ink = when {
        day == today -> c.link
        day.dayOfWeek.value >= 6 -> c.graphite.copy(alpha = 0.75f)
        else -> c.graphite
    }
    // Today needs no weekday — the week above has it lit; so «сегодня, 18 октября · свободный день» fits one line.
    val words = (if (day == today) s.today.lowercase() else day.dowFull().lowercase()) + ", " + day.dayMonth() +
        (if (day.year != today.year) " ${day.year}" else "") + (if (free) "  ·  " + s.freeDay.lowercase() else "")
    Margined(Modifier.padding(top = 22.dp, bottom = 6.dp)) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            androidx.compose.material3.Text(words, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                style = Type.meta.copy(color = ink, fontWeight = androidx.compose.ui.text.font.FontWeight(if (day == today) 600 else 500)))
        }
    }
}

/**
 * The calendar's feed (owner, 17 окт): every day in one list under the pinned month. A day picked in the month — or «сегодня ›»
 * ([jump] counts the asks) — brings its first row up under the month; scrolled, the day at the top is told to [onTop].
 */
@Composable
fun FeedFollow(on: Boolean, list: androidx.compose.foundation.lazy.LazyListState, starts: Map<LocalDate, Int>, target: LocalDate, jump: Int,
               onTop: (LocalDate?) -> Unit) {
    val tell by androidx.compose.runtime.rememberUpdatedState(onTop)
    // The pinned month's height, kept: once pinned, the list may not report it among its items.
    val monthPx = remember { intArrayOf(0) }
    val slack = with(androidx.compose.ui.platform.LocalDensity.current) { 24.dp.roundToPx() }
    fun month(info: androidx.compose.foundation.lazy.LazyListLayoutInfo): Int {
        info.visibleItemsInfo.firstOrNull { it.key == "month" }?.let { m -> monthPx[0] = maxOf(monthPx[0], m.size + maxOf(0, m.offset)) }
        return monthPx[0]
    }
    androidx.compose.runtime.LaunchedEffect(on, target, jump) {
        if (!on) return@LaunchedEffect
        androidx.compose.runtime.withFrameNanos { }
        val i = starts[target] ?: starts.entries.filter { !it.key.isAfter(target) }.maxByOrNull { it.key }?.value ?: return@LaunchedEffect
        val m = month(list.layoutInfo)
        list.scrollToItem(i)
        if (m > 0) list.scrollBy(-m.toFloat())
    }
    androidx.compose.runtime.LaunchedEffect(on) {
        if (!on) { tell(null); return@LaunchedEffect }
        androidx.compose.runtime.snapshotFlow {
            val info = list.layoutInfo
            // A row showing only a sliver under the month doesn't make its day the top one.
            val top = month(info) + slack
            info.visibleItemsInfo.firstOrNull { it.key != "month" && it.offset + it.size > top }?.index
        }.collect { idx ->
            if (idx != null) tell(starts.entries.filter { it.value <= idx }.maxByOrNull { it.value }?.key ?: starts.keys.minOrNull())
        }
    }
}

/**
 * The diary's one head (owner, 17 окт: «один экран без заголовка»): a line of marks — personal · work, matters · topics · notes
 * (each a filter: chosen — only that, again — everything), then contacts · search · journal · the menu ≡; under it the week
 * of the day at the top of the feed. A pull down (or a tap on the month on the margin) unfolds the whole month; a day picked
 * in either brings the feed to it. The double rule parts what stands from what scrolls.
 */
@Composable
fun PlannerHead(data: ru.anjeib.margin.data.AppData, vm: MainVm, events: List<ru.anjeib.margin.data.CalEvent>, top: LocalDate,
                /** «Дневник» is open under the head: its mark lit, a tap on it goes back to the diary. */
                journalOn: Boolean = false,
                onPeople: () -> Unit, onJournal: () -> Unit, onMenu: () -> Unit) {
    val c = M.c
    val s = M.s
    val t = LocalPageTools.current
    val sphere by vm.diarySphere.collectAsState()
    val kind by vm.kindFilter.collectAsState()
    val monthOpen by vm.monthOpen.collectAsState()
    val topicF by vm.topicFilter.collectAsState()
    val topicTitle = topicF?.let { id -> data.tasks.find { it.id == id }?.title }
    val today = LocalDate.now()
    fun pick(d: LocalDate) { vm.pageDay.value = d.takeIf { it != today }; vm.feedJump.value++; vm.monthOpen.value = false }
    @Composable fun mark(label: String, on: Boolean, onClick: () -> Unit, glyph: @Composable (androidx.compose.ui.graphics.Color) -> Unit) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(if (on) c.linkSoft else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(onClickLabel = label, onClick = onClick), contentAlignment = Alignment.Center) {
            glyph(if (on) c.link else c.ink.copy(alpha = 0.7f))
        }
    }
    fun sphereTap(k: String) { vm.diarySphere.value = if (sphere == k) null else k }
    fun kindTap(k: String) { vm.kindFilter.value = if (kind == k) null else k }
    Column(Modifier.fillMaxWidth().pointerInput(Unit) {
        var dy = 0f
        detectVerticalDragGestures(onDragStart = { dy = 0f }, onDragEnd = {
            if (dy > 36.dp.toPx()) vm.monthOpen.value = true else if (dy < -36.dp.toPx()) vm.monthOpen.value = false
        }) { ch, d -> dy += d; ch.consume() }
    }) {
        // On the paper's own ground: the margin's rule starts under the marks, at the week. Owner, 17 окт («длинный ряд иконок
        // не очень красиво»): the five filters live in one mark, «фильтр», with its menu; it is petrol while something is filtered.
        // The paper's ground reaches a little below the name, so the margin's rule starts clear of «Дневник»'s line (owner, 17 окт).
        Row(Modifier.fillMaxWidth().background(c.bg).padding(start = 4.dp, end = 4.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            // What is open, by the marks on the left (owner, 17 окт): «Дела» — «простые и сложные», or «Дневник» — «только для себя».
            Box(Modifier.weight(1f).height(44.dp).padding(start = 10.dp), contentAlignment = Alignment.CenterStart) {
                androidx.compose.animation.Crossfade(Triple(journalOn, topicTitle, topicF), animationSpec = tween(200), label = "what") { (j, tt, _) ->
                    Column {
                        // A topic chosen: its name here, and the way back to the whole feed under it.
                        androidx.compose.material3.Text(if (j) s.journalTitle else if (tt != null) "# $tt" else s.kindDeals, maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            style = Type.pageTitle.copy(color = c.ink, fontSize = 25.sp, lineHeight = 27.sp))
                        if (!j && tt != null) androidx.compose.material3.Text(s.topicFilterSub, maxLines = 1,
                            style = Type.meta.copy(color = c.link, lineHeight = 15.sp), modifier = Modifier.clickable { vm.topicFilter.value = null })
                        else androidx.compose.material3.Text(if (j) s.journalSub else s.dealsSub, maxLines = 1,
                            style = Type.meta.copy(color = c.graphite, lineHeight = 15.sp))
                    }
                }
            }
            var filters by remember { androidx.compose.runtime.mutableStateOf(false) }
            Box {
                mark(s.filterMark, sphere != null || kind != null, { filters = true }) { FilterGlyph(it, 21.dp) }
                SoftMenu(filters, { filters = false }) {
                    SoftMenuItem(s.spherePersonal, selected = sphere == "personal") { sphereTap("personal") }
                    SoftMenuItem(s.sphereWork, selected = sphere == "work") { sphereTap("work") }
                    Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp).height(1.dp).background(c.hairline))
                    SoftMenuItem(s.kindDeals, selected = kind == "deals") { kindTap("deals") }
                    SoftMenuItem(s.kindTopics, selected = kind == "topics") { kindTap("topics") }
                    SoftMenuItem(s.kindNotes, selected = kind == "notes") { kindTap("notes") }
                    if (sphere != null || kind != null) {
                        Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp).height(1.dp).background(c.hairline))
                        SoftMenuItem(s.showEverything) { vm.diarySphere.value = null; vm.kindFilter.value = null; filters = false }
                    }
                }
            }
            mark(s.peopleTitle, false, onPeople) { PersonGlyph(it, 21.dp) }
            if (t != null) mark(s.search, t.searchOn, t.search) { SearchGlyph(it, 21.dp) }
            // «Дела» and «Дневник» side by side (owner, 17 окт: «как из Дневника вернуться?»): the one open is lit.
            mark(s.kindDeals, !journalOn, { if (journalOn) onJournal() }) { DealGlyph(it, 22.dp) }
            mark(s.journalTitle, journalOn, { if (!journalOn) onJournal() }) { DiaryGlyph(it, 21.dp) }
            mark(s.contentsTitle, false, onMenu) { MenuGlyph(it) }
        }
        AnimatedContent(monthOpen, transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(120)) }, label = "calendar") { open ->
            // The month folds back by «ОКТ ▴» on its margin (owner, 17 окт: «как свернуть?»), by a pull up, or by picking a day.
            if (open) Box(Modifier.padding(top = 4.dp)) {
                MonthPanel(data, vm, top, events, margin = { MonthFold(top, open = true) { vm.monthOpen.value = false } }) { pick(it) }
            }
            else WeekStrip(top, today, { pick(it) }) { vm.monthOpen.value = true }
        }
        // One hairline under the week (owner, 18 окт: the double rule was the heaviest thing on the screen once the days lost theirs).
        Box(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp).padding(horizontal = 14.dp).height(1.dp).background(c.ink.copy(alpha = 0.18f)))
    }
}

/** The week of [day]: the weekday small over each number, [day] filled, today ringed; the month on the margin opens the month. */
@Composable
private fun WeekStrip(day: LocalDate, today: LocalDate, onPick: (LocalDate) -> Unit, onMonth: () -> Unit) {
    val c = M.c
    val monday = day.minusDays(day.dayOfWeek.value - 1L)
    val loc = if (ru.anjeib.margin.i18n.L.lang == ru.anjeib.margin.i18n.Lang.Ru) java.util.Locale.forLanguageTag("ru") else java.util.Locale.ENGLISH
    val pick by androidx.compose.runtime.rememberUpdatedState(onPick)
    val now by androidx.compose.runtime.rememberUpdatedState(day)
    // «ОКТ ▾» on the line of the weekdays (owner, 17 окт).
    Margined(Modifier.padding(top = 4.dp), margin = { Box(Modifier.height(32.dp), contentAlignment = Alignment.CenterEnd) { MonthFold(day, open = false, onClick = onMonth, top = 0.dp) } }) {
        // A swipe across the week — the week before or after.
        Row(Modifier.fillMaxWidth().pointerInput(Unit) {
            var dx = 0f
            detectHorizontalDragGestures(onDragStart = { dx = 0f }, onDragEnd = {
                if (dx < -48.dp.toPx()) pick(now.plusDays(7)) else if (dx > 48.dp.toPx()) pick(now.minusDays(7))
            }) { ch, d -> dx += d; ch.consume() }
        }) {
            (0L..6L).forEach { i ->
                val d = monday.plusDays(i)
                val sel = d == day
                val weekend = d.dayOfWeek.value >= 6
                Column(Modifier.weight(1f).padding(horizontal = 1.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                    .background(if (sel) c.link else androidx.compose.ui.graphics.Color.Transparent)
                    .then(if (d == today && !sel) Modifier.border(1.dp, c.link, androidx.compose.foundation.shape.RoundedCornerShape(8.dp)) else Modifier)
                    .clickable { onPick(d) }.padding(vertical = 5.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    // Only the weekdays (owner, 17 окт): the dates are the feed's, on its margin.
                    androidx.compose.material3.Text(d.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, loc).lowercase(),
                        style = Type.markM.copy(color = if (sel) c.bg else if (weekend) weekendInk else c.ink, lineHeight = 22.sp,
                            fontWeight = if (sel || d == today) androidx.compose.ui.text.font.FontWeight.SemiBold else androidx.compose.ui.text.font.FontWeight.Normal))
                }
            }
        }
    }
}

/** «ОКТ ▾» / «ОКТ ▴» on the margin of the head's calendar: unfolds the month, or folds it back into the week. */
@Composable
private fun MonthFold(day: LocalDate, open: Boolean, top: androidx.compose.ui.unit.Dp = 4.dp, onClick: () -> Unit) = androidx.compose.material3.Text(
    day.dayMonthShort().substringAfter(' ').uppercase() + if (open) " ▴" else " ▾",
    style = Type.meta.copy(color = M.c.ink, letterSpacing = 1.5.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium),
    modifier = Modifier.padding(top = top).clip(androidx.compose.foundation.shape.RoundedCornerShape(6.dp)).clickable(onClick = onClick).padding(4.dp))

/** A filter: three lines, each shorter, as a funnel of words. */
@Composable
fun FilterGlyph(color: androidx.compose.ui.graphics.Color, size: androidx.compose.ui.unit.Dp = 20.dp) = androidx.compose.foundation.Canvas(Modifier.size(size)) {
    val w = this.size.width; val h = this.size.height; val st = 1.5.dp.toPx()
    listOf(0.3f to 0.78f, 0.5f to 0.54f, 0.7f to 0.3f).forEach { (y, len) ->
        drawLine(color, androidx.compose.ui.geometry.Offset(w * (0.5f - len / 2), h * y), androidx.compose.ui.geometry.Offset(w * (0.5f + len / 2), h * y), st,
            cap = androidx.compose.ui.graphics.StrokeCap.Round)
    }
}
