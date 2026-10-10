package ru.anjeib.margin.paper

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ru.anjeib.margin.book.splitOccurrence
import ru.anjeib.margin.book.setOrg

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/*
 * The notebook (DESIGN.md §11): pages of days turned by a swipe, the field under them; the index and the other pages
 * open over it and go back with the system gesture. One field everywhere — it writes where you are.
 */

private const val MIDDLE = 5000

@Composable
fun BookScreen(vm: BookVm) {
    val welcomed by vm.welcomed.collectAsState()
    if (!welcomed) {
        WelcomePage(withExamples = vm.samplesShown(), onExamples = { vm.welcome(keepExamples = true) }, onClean = { vm.welcome(keepExamples = false) })
        return
    }
    Notebook(vm)
}

@Composable
private fun Notebook(vm: BookVm) {
    val c = P.c
    val w = P.w
    val ctx = LocalContext.current
    val book by vm.book.collectAsState()
    val today by vm.today.collectAsState()
    val fresh by vm.fresh.collectAsState()
    val stamping by vm.stamping.collectAsState()
    val cal by vm.calendar.collectAsState()
    val pager = rememberPagerState(initialPage = MIDDLE) { MIDDLE * 2 }
    val scope = rememberCoroutineScope()
    fun dateOf(i: Int): LocalDate = today.plusDays((i - MIDDLE).toLong())
    fun go(d: LocalDate) = scope.launch { pager.animateScrollToPage(MIDDLE + ChronoUnit.DAYS.between(today, d).toInt()) }
    val current = dateOf(pager.currentPage)

    // Where we are: the book of days, or a page over it («index», «thread:<id>», «person:<name>», «later», «search», …).
    var stack by rememberSaveable { mutableStateOf(listOf<String>()) }
    val route = stack.lastOrNull()
    fun push(r: String) { stack = stack + r }
    fun pop() { stack = stack.dropLast(1) }
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    var month by rememberSaveable { mutableStateOf(false) }
    var ritual by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = route != null || ritual) { if (ritual) ritual = false else pop() }

    val target = when {
        route == null -> BookVm.Target(current)
        route.startsWith("thread:") -> BookVm.Target(today, threadId = route.removePrefix("thread:"))
        route.startsWith("person:") -> BookVm.Target(today, person = route.removePrefix("person:"))
        route == "later" -> BookVm.Target(today, later = true)
        route.startsWith("week:") -> BookVm.Target(today)
        else -> null
    }

    Box(Modifier.fillMaxSize().background(c.paper)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
            TopLine(
                back = route != null || ritual,
                onBack = { if (ritual) ritual = false else pop() },
                onIndex = { push("index") },
                awayFromToday = route == null && !ritual && current != today,
                onToday = { go(today) },
                onSearch = if (route != "search") ({ push("search") }) else null,
            )
            Box(Modifier.weight(1f)) {
                AnimatedContent(targetState = route to ritual, transitionSpec = { fadeIn(tween(180, delayMillis = 100)) togetherWith fadeOut(tween(100)) }, label = "page") { (r, rit) ->
                    val onOpen: (String) -> Unit = { id ->
                        if (id.startsWith("cal:")) cal.values.flatten().find { it.id == id }?.let { l -> openCalendar(ctx, l) } else open = id
                    }
                    when {
                        // The ritual ends on today's page: the stamp is pressed, a breath, then the page turns.
                        rit -> RitualPage(book, today, vm, bottom = 24.dp, onDone = { day, asked ->
                            vm.closeDay(day, asked); ritual = false
                            scope.launch {
                                pager.scrollToPage(MIDDLE + ChronoUnit.DAYS.between(today, day).toInt())
                                kotlinx.coroutines.delay(3200)
                                // Still looking at the closed page (not gone elsewhere meanwhile): turn it.
                                if (dateOf(pager.currentPage) == day && stack.isEmpty()) go(day.plusDays(1))
                            }
                        })
                        r == null -> HorizontalPager(pager, Modifier.fillMaxSize(), beyondViewportPageCount = 1, key = { it }) { i ->
                            DayPage(dateOf(i), book, today, fresh, bottom = 24.dp, onMark = vm::toggle, onOpen = onOpen,
                                onMonth = { month = true }, onTurn = { ritual = true }, eveningHour = vm.eveningHour(), stamping = stamping,
                                calendar = cal[dateOf(i)].orEmpty(), onNext = { go(dateOf(i).plusDays(1)) }, onExplain = vm::explain)
                        }
                        r == "index" -> IndexPage(book, today, onThread = { push("thread:$it") }, onPerson = { push("person:$it") }, onLater = { push("later") },
                            onSettings = { push("settings") }, onHelp = { push("help") }, onSearch = { push("search") },
                            onFeedback = { sendFeedback(ctx, w) }, onWeek = { push("week:$current") },
                            onAllThreads = { push("threads") }, onAllPeople = { push("people") })
                        r == "threads" -> AllThreadsPage(book, today, onThread = { push("thread:$it") })
                        r == "people" -> AddressBookPage(book, onPerson = { push("person:$it") })
                        r.startsWith("week:") -> WeekPage(book, today, LocalDate.parse(r.removePrefix("week:")), vm::toggle, onOpen,
                            onDay = { d -> stack = emptyList(); go(d) }, bottom = 24.dp)
                        r.startsWith("thread:") -> ThreadPage(book, r.removePrefix("thread:"), today, vm::toggle, onOpen, bottom = 24.dp,
                            onClose = { close -> vm.closeThread(r.removePrefix("thread:"), close) })
                        r.startsWith("person:") -> PersonPage(book, r.removePrefix("person:"), today, vm::toggle, onOpen,
                            onOrg = { org -> vm.edit { it.setOrg(r.removePrefix("person:"), org) } },
                            onCall = { ph -> runCatching { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_DIAL, android.net.Uri.parse("tel:$ph"))) } },
                            bottom = 24.dp)
                        r == "later" -> LaterPage(book, today, vm::toggle, onOpen, bottom = 24.dp)
                        r == "search" -> SearchPage(book, today, vm::toggle, onOpen, onThread = { push("thread:$it") }, onPerson = { push("person:$it") })
                        r == "settings" -> SettingsPage(vm, onExamples = { vm.toggleExamples() })
                        r == "help" -> HelpPage(book, today)
                        else -> Unit
                    }
                }
            }
            if (target != null && !ritual) Composer(vm, target, book, today, Modifier.navigationBarsPadding(),
                onOpen = { open = it }, onPerson = { push("person:$it") }, onThread = { push("thread:$it") },
                onDay = { d -> stack = emptyList(); go(d) }, onSearch = { push("search") })
            else Spacer(Modifier.navigationBarsPadding())
        }
        open?.let { id ->
            val baseId = splitOccurrence(id)?.first ?: id
            LineSheet(baseId, current, vm, book, today, onClose = { open = null },
                onThread = { open = null; push("thread:$it") }, onPerson = { open = null; push("person:$it") })
        }
        if (month) MonthSheet(book, today, current, onPick = { d -> month = false; stack = emptyList(); go(d) }, onClose = { month = false },
            onWeek = { d -> month = false; push("week:$d") })
    }

    // Opened from a notification: its line, the ritual, or today.
    val launch by vm.launch.collectAsState()
    LaunchedEffect(launch) {
        val l = launch ?: return@LaunchedEffect
        vm.launchTaken()
        when {
            l == "ritual" -> { stack = emptyList(); ritual = true }
            l == "today" -> { stack = emptyList(); go(today) }
            l.startsWith("line:") -> { stack = emptyList(); open = l.removePrefix("line:") }
        }
    }

    // A new day while the app was open: today moves, the pager follows.
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(60_000)
            if (LocalDate.now() != vm.today.value) vm.refreshToday()
        }
    }
}

/** A meeting from the phone's calendar opens there, at its hour. */
private fun openCalendar(ctx: android.content.Context, l: ru.anjeib.margin.book.Line) {
    val at = runCatching { java.time.LocalDateTime.parse(l.writtenAt) }.getOrNull() ?: return
    val ms = at.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
    val uri = android.provider.CalendarContract.CONTENT_URI.buildUpon().appendPath("time").appendPath(ms.toString()).build()
    runCatching { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, uri).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/** The line above every page: the index (or back) on the margin, the way to today, search at the right. */
@Composable
private fun TopLine(back: Boolean, onBack: () -> Unit, onIndex: () -> Unit, awayFromToday: Boolean, onToday: () -> Unit, onSearch: (() -> Unit)?) {
    val c = P.c
    val w = P.w
    Row(Modifier.fillMaxWidth().height(52.dp).rule(c.rule).padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(Grid.margin - Grid.noteGap), contentAlignment = Alignment.Center) {
            Box(Modifier.size(44.dp).clip(CircleShape).clickable { if (back) onBack() else onIndex() }
                .semantics { contentDescription = if (back) w.back else w.a11yIndex }, contentAlignment = Alignment.Center) {
                if (back) Icon(Icons.AutoMirrored.Outlined.ArrowBack, null, tint = c.ink) else IndexGlyph(c.ink, 20.dp)
            }
        }
        Spacer(Modifier.width(Grid.text - Grid.margin + Grid.noteGap))
        Box(Modifier.weight(1f)) {
            if (awayFromToday) Text(w.toToday, style = Voice.reply.copy(color = c.link),
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onToday).padding(vertical = 12.dp))
        }
        if (onSearch != null) Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onSearch).semantics { contentDescription = w.a11ySearch },
            contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Search, null, tint = c.ink) }
    }
}
