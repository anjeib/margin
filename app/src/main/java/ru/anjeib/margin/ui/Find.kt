package ru.anjeib.margin.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.anjeib.margin.data.AppData
import ru.anjeib.margin.data.Subtask
import ru.anjeib.margin.data.Task
import ru.anjeib.margin.data.fullText
import ru.anjeib.margin.data.text

// ---------------- find on the page (owner, 14 окт): a field slides down over the page, the page's own words are marked ----------------

/** «ё» is «е», and a longer word is matched by its stem: «Маше» finds «Маша», «макеты» — «макет» (as the full search does). */
fun findNorm(x: String) = x.lowercase().replace('ё', 'е')
fun findWords(q: String): List<String> =
    findNorm(q).split(' ', ',', '.').filter { it.isNotBlank() }.map { if (it.length >= 5) it.dropLast(if (it.length >= 7) 2 else 1) else it }
fun findHit(words: List<String>, hay: String): Boolean = words.isNotEmpty() && findNorm(hay).let { h -> words.all { it in h } }

/** Where each word stands in [text] — the stretches the highlighter goes over. */
fun findRanges(text: String, words: List<String>): List<IntRange> {
    if (words.isEmpty()) return emptyList()
    val h = findNorm(text)
    val out = mutableListOf<IntRange>()
    words.forEach { w ->
        var i = h.indexOf(w)
        while (i >= 0) {
            // A stem found marks its whole word: «макет» in «макеты» — all of «макеты».
            var e = i + w.length
            while (e < h.length && h[e].isLetterOrDigit()) e++
            out += i until e; i = h.indexOf(w, e)
        }
    }
    return out.sortedBy { it.first }
}

/** One match on a page: its row's place in the page's list and the row's key. */
data class FindHit(val index: Int, val key: Any)

/** The find bar's state, shared by the bar, the pages and every row that marks its words. */
class FindState {
    var open by mutableStateOf(false)
    var query by mutableStateOf("")
    /** The match the arrows stand at, on the page in front. */
    var cur by mutableIntStateOf(0)
    /** Which page is in front: «today» · «notes» · «topics» — or another screen, with no matches of its own. */
    var page by mutableStateOf("")
    /** What each page found, in its own order (reported after the page is laid out). */
    val pages = mutableStateMapOf<String, List<FindHit>>()
    val words: List<String> get() = if (open) findWords(query) else emptyList()
    val hits: List<FindHit> get() = pages[page].orEmpty()
    val current: FindHit? get() = hits.getOrNull(cur)
    fun step(by: Int) { val n = hits.size; if (n > 0) cur = ((cur + by) % n + n) % n }
}

val LocalFind = staticCompositionLocalOf<FindState?> { null }
/** True inside the row the arrows stand at — its words are marked stronger. */
val LocalFindHere = staticCompositionLocalOf { false }

/** The words to mark right now: nothing unless the bar is open with something typed. */
val findWordsNow: List<String> @Composable get() = LocalFind.current?.words.orEmpty()

/**
 * The highlighter over the found words of a text laid out as [layout]: petrol — not terracotta, which is «late»; the row the
 * arrows stand at a stronger pass.
 */
@Composable
fun Modifier.foundMarks(text: String, layout: () -> TextLayoutResult?): Modifier {
    val words = findWordsNow
    if (words.isEmpty()) return this
    val ranges = remember(text, words) { findRanges(text, words) }
    if (ranges.isEmpty()) return this
    val here = LocalFindHere.current
    val ink = if (here) M.c.link else M.c.link.copy(alpha = 0.55f)
    val strong = here
    return this.drawBehind {
        val l = layout() ?: return@drawBehind
        ranges.forEachIndexed { k, rg ->
            val a = rg.first.coerceAtMost(l.layoutInput.text.length); val b = (rg.last + 1).coerceAtMost(l.layoutInput.text.length)
            if (b <= a) return@forEachIndexed
            for (line in l.getLineForOffset(a)..l.getLineForOffset(b - 1)) {
                val s0 = maxOf(a, l.getLineStart(line)); val e0 = minOf(b, l.getLineEnd(line, visibleEnd = true))
                if (e0 <= s0) continue
                val x0 = l.getHorizontalPosition(s0, true); val x1 = l.getHorizontalPosition(e0, true)
                markerBand(minOf(x0, x1), maxOf(x0, x1), l.getLineTop(line), l.getLineBottom(line), ink, text.hashCode() * 17 + k * 5 + line)
                if (strong) markerBand(minOf(x0, x1), maxOf(x0, x1), l.getLineTop(line), l.getLineBottom(line), ink, text.hashCode() * 13 + k * 3 + line)
            }
        }
    }
}

/**
 * A page's list that keeps count of its rows as they are laid out, so a match can be scrolled to: [matches] says, by a row's
 * key, whether that row is one; the hits go to [into] (read after the frame — see [ReportFound]).
 */
class FindScope(private val inner: LazyListScope, private val into: MutableList<FindHit>, private val matches: (Any) -> Boolean,
                private val state: FindState?) : LazyListScope by inner {
    private var n = 0
    private fun wrap(key: Any?, content: @Composable LazyItemScope.() -> Unit): @Composable LazyItemScope.() -> Unit {
        val i = n++
        if (key == null || state == null || !matches(key)) return content
        into += FindHit(i, key)
        val st = state
        return { CompositionLocalProvider(LocalFindHere provides (st.current?.key == key)) { content() } }
    }
    override fun item(key: Any?, contentType: Any?, content: @Composable LazyItemScope.() -> Unit) = inner.item(key, contentType, wrap(key, content))
    override fun items(count: Int, key: ((Int) -> Any)?, contentType: (Int) -> Any?, itemContent: @Composable LazyItemScope.(Int) -> Unit) {
        // Not used by the pages that find (they lay out item by item) — counted all the same.
        n += count
        inner.items(count, key, contentType, itemContent)
    }
}

/** Lays out a page's rows through a [FindScope]: what matches is counted and can be scrolled to. */
fun LazyListScope.findable(into: MutableList<FindHit>, state: FindState?, matches: (Any) -> Boolean, block: LazyListScope.() -> Unit) {
    into.clear()
    if (state == null || state.words.isEmpty()) { block(); return }
    FindScope(this, into, matches, state).block()
}

/** A row's key ends with the matter's id (8 characters): «p…», «u…», «w…», «ot…», «n…». */
fun AppData.taskOfKey(key: Any): Task? = (key as? String)?.let { k -> tasks.firstOrNull { k.length > it.id.length && k.endsWith(it.id) } }

/** What a matter shows on a page, for finding on it: its words and, for a topic, its own matters. */
fun Task.findText(): String {
    fun flat(l: List<Subtask>): List<Subtask> = l.flatMap { listOf(it) + flat(it.children) }
    return title + " " + (if (topic) flat(subtasks).joinToString(" ") { it.title } else "")
}

/**
 * After a page is laid out its hits are handed to the bar; and when the arrows move on this page, the list goes to the match
 * (a row above it left in sight).
 */
@Composable
fun ReportFound(page: String?, hits: MutableList<FindHit>, list: androidx.compose.foundation.lazy.LazyListState, vararg keys: Any?) {
    val f = LocalFind.current ?: return
    if (page == null) return
    val words = f.words
    androidx.compose.runtime.LaunchedEffect(words, *keys) {
        androidx.compose.runtime.withFrameNanos { }
        f.pages[page] = hits.toList()
        if (f.page == page) f.cur = 0
    }
    val target = if (f.page == page) f.current else null
    androidx.compose.runtime.LaunchedEffect(target, f.page) {
        target ?: return@LaunchedEffect
        runCatching { list.animateScrollToItem((target.index - 1).coerceAtLeast(0)) }
    }
}

/** Matches elsewhere, by where they live — for the line under the bar. */
class Elsewhere(val notes: Int, val topics: Int, val deals: Int, val rest: Int) {
    val any get() = notes + topics + deals + rest > 0
}

fun AppData.elsewhere(words: List<String>, here: Set<String>): Elsewhere {
    if (words.isEmpty()) return Elsewhere(0, 0, 0, 0)
    fun subs(l: List<Subtask>): String = l.joinToString(" ") { it.title + " " + (it.assignee ?: "") + " " + subs(it.children) }
    val found = tasks.filter { it.id !in here }.filter { t ->
        findHit(words, listOf(t.title, t.project.orEmpty(), t.people.joinToString(" "), t.waitingFor.orEmpty(), subs(t.subtasks), t.entries.joinToString(" ") { it.fullText }).joinToString(" "))
    }
    val diary = found.count { it.thought && it.subtasks.isEmpty() && !it.done }
    val topics = found.count { it.topic && !(it.thought && it.subtasks.isEmpty()) && !it.done }
    val deals = found.count { !it.topic && !it.thought && !it.done }
    val done = found.count { it.done }
    val people = persons.filter { it.isContact }.count { findHit(words, it.name + " " + it.aliases.joinToString(" ") + " " + it.about.orEmpty()) }
    val notes = this.notes.filter { !it.archived }.count { findHit(words, it.title + " " + it.text) }
    return Elsewhere(diary, topics, deals, done + people + notes)
}

/**
 * The find bar (owner, 14 окт): slides down over the page — the words, how many on this page and the arrows through them
 * («2/7 ‹ ›»); under it one quiet line of where else the words are, each a link: a page of the diary turns to it, the rest
 * opens the full list.
 */
@Composable
fun FindBar(state: FindState, data: AppData, recent: List<String>, onClose: () -> Unit, onPage: (String) -> Unit, onAll: () -> Unit) {
    val c = M.c
    val s = M.s
    val focus = remember { FocusRequester() }
    androidx.compose.runtime.LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val words = state.words
    val hits = state.hits
    val here = remember(hits, data) { hits.mapNotNull { data.taskOfKey(it.key)?.id }.toSet() }
    val more = remember(words, here, data) { data.elsewhere(words, here) }
    Column(Modifier.fillMaxWidth().padding(start = MarginW + MarginGap - 6.dp, end = 6.dp, top = 2.dp, bottom = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).padding(vertical = 8.dp).drawBehind {
                // A ruled line to write the words on, like the page's own field.
                drawLine(c.ink.copy(alpha = 0.35f), Offset(0f, size.height + 4.dp.toPx()), Offset(size.width, size.height + 4.dp.toPx()), 0.8.dp.toPx())
            }) {
                if (state.query.isEmpty()) Text(s.findHint, style = Type.italic.copy(color = c.graphite, fontSize = 18.sp))
                BasicTextField(state.query, { state.query = it; state.cur = 0 }, singleLine = true, cursorBrush = SolidColor(c.link),
                    textStyle = Type.body.copy(color = c.ink, fontSize = 17.sp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { state.step(1) }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus))
            }
            if (words.isNotEmpty()) {
                Text(if (hits.isEmpty()) "0" else "${state.cur + 1}/${hits.size}", style = Type.small.copy(color = if (hits.isEmpty()) c.graphite else c.ink, fontSize = 14.sp),
                    modifier = Modifier.padding(start = 10.dp, end = 2.dp))
                Arrow(up = true, on = hits.size > 1, label = s.findPrev) { state.step(-1) }
                Arrow(up = false, on = hits.size > 1, label = s.findNext) { state.step(1) }
            }
            Box(Modifier.size(40.dp).clip(CircleShape).clickable(onClickLabel = s.close, onClick = onClose), contentAlignment = Alignment.Center) { CloseGlyph(c.graphite, 18.dp) }
        }
        // Where else the words are — or, with nothing typed, the last searches.
        @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
        androidx.compose.foundation.layout.FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            val small = Type.small.copy(fontSize = 13.5.sp)
            @Composable fun link(t: String, on: () -> Unit) = Text(t, style = small.copy(color = c.link), modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = on).padding(vertical = 3.dp))
            if (words.isEmpty()) {
                recent.take(4).forEach { r -> link(r) { state.query = r; state.cur = 0 } }
                link(s.findEverywhere) { onAll() }
            } else if (!more.any) {
                if (hits.isEmpty()) Text(s.nothing, style = small.copy(color = c.graphite), modifier = Modifier.padding(vertical = 3.dp))
            } else {
                Text(if (hits.isEmpty()) s.findNotHere else s.findAlso, style = small.copy(color = c.graphite), modifier = Modifier.padding(vertical = 3.dp))
                if (more.notes > 0) link(s.findInNotes(more.notes)) { onPage("notes") }
                if (more.topics > 0) link(s.findInTopics(more.topics)) { onPage("topics") }
                if (more.deals > 0) link(s.findInDeals(more.deals)) { onAll() }
                if (more.rest > 0) link(s.findElsewhere(more.rest)) { onAll() }
            }
        }
    }
}

@Composable
private fun Arrow(up: Boolean, on: Boolean, label: String, onClick: () -> Unit) {
    val c = M.c
    val ink = if (on) c.ink.copy(alpha = 0.8f) else c.faint
    Box(Modifier.size(36.dp).clip(CircleShape).clickable(enabled = on, onClickLabel = label, onClick = onClick), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Canvas(Modifier.size(14.dp)) {
            val w = size.width; val h = size.height; val sw = 1.5.dp.toPx()
            val (y0, y1) = if (up) h * 0.68f to h * 0.32f else h * 0.32f to h * 0.68f
            drawLine(ink, Offset(w * 0.18f, y0), Offset(w * 0.5f, y1), sw, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            drawLine(ink, Offset(w * 0.5f, y1), Offset(w * 0.82f, y0), sw, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        }
    }
}
