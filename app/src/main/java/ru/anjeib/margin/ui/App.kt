package ru.anjeib.margin.ui

import ru.anjeib.margin.data.dowFull

import androidx.compose.ui.input.pointer.pointerInput

import androidx.compose.foundation.gestures.detectHorizontalDragGestures

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import ru.anjeib.margin.data.listTasks
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import ru.anjeib.margin.data.dayMonth
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.PeopleOutline
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.DynamicFeed
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import ru.anjeib.margin.ai.Scope
import ru.anjeib.margin.data.AppData
import ru.anjeib.margin.data.isOverdue
import ru.anjeib.margin.data.longTitle
import java.time.LocalDate
import java.time.LocalTime

private const val TASKS = "tasks"
private const val NOTES = "notes"
private const val PEOPLE = "people"
private const val SETTINGS = "settings"
private const val HELP = "help"
private const val INBOX = "inbox"
private const val PROJECTS = "projects"
/** «Темы» — what «Обзор» held of topics, a page of the contents (owner, 14 окт: no «Обзор»). */
private const val TOPICS = "topics"
private const val CALENDAR = "calendar"
private const val ARCHIVE = "archive"
private const val MATERIALS = "materials"
/** «Дневник» (owner, 17 окт): the free notes, a feed of its own — nothing comes into it from the diary, nothing goes out. */
private const val JOURNAL = "journal"

@Composable
fun App(vm: MainVm, shared: String?, onSharedConsumed: () -> Unit,
        launch: Pair<String, Long>? = null, onLaunchUsed: () -> Unit = {},
        copied: Boolean = false, readCopied: () -> String? = { null }, onCopiedSeen: () -> Unit = {}) {
    val c = M.c
    val s = M.s
    val ctx = LocalContext.current
    val data by vm.data.collectAsState()
    val settings by vm.settings.collectAsState()
    val busy by vm.busy.collectAsState()
    val working by vm.working.collectAsState()
    val tray by vm.trayFrom.collectAsState()
    val pending by vm.pending.collectAsState()
    val notice by vm.notice.collectAsState()
    val toast by vm.toast.collectAsState()
    val events by vm.events.collectAsState()
    val undoBar by vm.undoBarState.collectAsState()
    val contactOffer by vm.contactOffer.collectAsState()
    val personReq by vm.openPersonReq.collectAsState()

    var screen by rememberSaveable { mutableStateOf(TASKS) }
    var view by rememberSaveable { mutableStateOf(Views.TODAY) }
    // «Лента» shows everything; the funnel narrows it to matters, topics or ideas.
    var feedFilter by rememberSaveable { mutableStateOf("all") }
    var openTask by rememberSaveable { mutableStateOf<String?>(null) }
    var openTaskDialog by rememberSaveable { mutableStateOf<String?>(null) }
    var openTab by rememberSaveable { mutableStateOf(0) }
    var openHl by rememberSaveable { mutableStateOf<String?>(null) }
    var viewing by remember { mutableStateOf<ru.anjeib.margin.data.Attachment?>(null) }
    var openDeal by rememberSaveable { mutableStateOf<String?>(null) }
    var fullIdea by rememberSaveable { mutableStateOf<String?>(null) }
    /** A task, or a topic's question from the lists: then the topic opens on «Вопросы» with that one lit. */
    fun openId(id0: String) {
        // A day of a matter done on several days opens the matter itself.
        val id = ru.anjeib.margin.data.slotRef(id0)?.first ?: id0
        val q = ru.anjeib.margin.data.questionRef(id)
        // A topic's matter opens its own card (the topic is one tap further, from the card).
        if (q != null) openDeal = id else { openTask = id; openTab = 0; openHl = null }
    }
    var openNote by rememberSaveable { mutableStateOf<String?>(null) }
    /** A note of a matter or a topic opened from «Заметки» in its own card (owner, 16 окт): its [NoteCard] key. */
    var openPiece by rememberSaveable { mutableStateOf<String?>(null) }
    var openPerson by rememberSaveable { mutableStateOf<String?>(null) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var typing by rememberSaveable { mutableStateOf(false) }
    var prefill by rememberSaveable { mutableStateOf<String?>(null) }
    var copiedText by remember { mutableStateOf<String?>(null) }
    var feedback by remember { mutableStateOf(false) }
    // «Планер»: the menu is the diary's contents page, turned to from the ≡ tab (owner, 14 окт).
    var contents by rememberSaveable { mutableStateOf(false) }
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // «Записать» / «Надиктовать» (tile, icon shortcut): back to the main field, keyboard up or the microphone on.
    var focusKey by remember { mutableStateOf<Long?>(null) }
    // Shared into the main field: the words stand there with the cursor after them, ready to be put right (Fable, 0.35).
    LaunchedEffect(shared) { if (!shared.isNullOrBlank()) { prefill = shared; screen = TASKS; focusKey = System.currentTimeMillis(); onSharedConsumed() } }
    var micKey by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(launch) {
        val (what, at) = launch ?: return@LaunchedEffect
        searching = false; openTask = null; openPerson = null; openNote = null; screen = TASKS
        scope.launch { drawer.close() }
        if (what.startsWith("open:")) openId(what.removePrefix("open:")) else if (what == "dictate") micKey = at else focusKey = at
        onLaunchUsed()
    }
    // Margin closed with an error last time: offer once to send what happened.
    var crashOffer by remember { mutableStateOf(ru.anjeib.margin.data.Feedback.newCrash(ctx)) }
    // The assistant tray is for the latest exchange — moving elsewhere tucks it away.
    LaunchedEffect(view, screen) { if (pending == null && busy == null) vm.dismissTray() }
    LaunchedEffect(personReq) { personReq?.let { openTask = null; openDeal = null; openPerson = it; vm.personOpened() } }
    // Persona test, 15 окт: no Android toast over the keyboard — the app's own quiet bar, as «Сделано · Отменить», for a few seconds.
    LaunchedEffect(toast) { if (toast != null) { kotlinx.coroutines.delay(2600); vm.toastShown() } }

    fun go(newScreen: String, newView: String? = null) {
        contents = false
        screen = newScreen
        if (newView != null) view = newView
        scope.launch { drawer.close() }
    }

    /** Back from a page over the diary: the menu, «Дневник», «Контакты» — to the diary; what the menu opened — to the menu. */
    fun backFromSection() {
        when {
            contents -> contents = false
            screen == PEOPLE || screen == JOURNAL || screen == TOPICS -> { screen = TASKS; view = Views.TODAY }
            else -> { screen = TASKS; contents = true }
        }
    }

    BackHandler(drawer.isOpen) { scope.launch { drawer.close() } }
    BackHandler(contents) { contents = false }
    // Find on the page (owner, 14 окт): the search tab slides a field down over the page instead of opening a screen.
    val find = remember { FindState() }
    var allFor by remember { mutableStateOf("") }
    val searchReq by vm.searchReq.collectAsState()
    LaunchedEffect(searchReq) { searchReq?.let { allFor = it; searching = true; vm.searchOpened() } }
    fun closeFind() { keepSearch(ctx, find.query); find.open = false; find.query = ""; find.cur = 0 }
    BackHandler(find.open && !searching) { closeFind() }
    // Turned to another day with the tabs: back is today's page.
    BackHandler(!drawer.isOpen && vm.pageDay.collectAsState().value != null && !contents && (screen == TOPICS || screen == TASKS && (view == Views.TODAY || view == Views.NOTES))) { vm.pageDay.value = null }
    val plannerLook = planner
    BackHandler(!drawer.isOpen && openNote == null && openPerson == null && !searching && (screen != TASKS || view != Views.TODAY)) {
        // A section goes back to the contents it was opened from (owner, 14 окт); «Темы» and «Заметки» — to «Сегодня».
        if (screen != TASKS) { val section = screen !in setOf(TOPICS, PEOPLE, JOURNAL); screen = TASKS; if (plannerLook && section) contents = true else view = Views.TODAY } else view = Views.TODAY
    }

    // «В тему…» and «Удалить» from a matter's long press or card — one picker for every screen.
    var topicFor by remember { mutableStateOf<String?>(null) }
    // «#метка» under a row opens its topic, or — when there is no such topic — the search for it (owner, 14 окт).
    val peopleLinks = LocalPeopleLinks.current
    val tagLinks = peopleLinks.copy(onTag = { label ->
        data.tasks.find { it.topic && !it.thought && it.title.equals(label, true) }?.let { openId(it.id) } ?: run { allFor = label; searching = true }
    })
    // The calendar and the search at the top of the diary's pages (owner, 14 окт): moved up from the index on the right.
    val toolsMonth by vm.monthOpen.collectAsState()
    val toolsLanded = vm.landedDay.collectAsState().value
    val pageTools = PageTools(
        searchOn = find.open, monthOpen = toolsMonth, landed = toolsLanded, menuOn = contents,
        menu = { searching = false; openPerson = null; openNote = null; openTask = null; fullIdea = null; if (find.open) closeFind(); contents = !contents },
        search = {
            openTask = null; fullIdea = null; openPerson = null; openNote = null; searching = false
            if (find.open) closeFind() else find.open = true
        },
        calendar = {
            // Owner, 16 окт: the calendar on every section — on «Темы» and «Содержание» it turns to the day's page and opens the month there.
            val dayPage = !contents && (screen == TOPICS || screen == TASKS && (view == Views.TODAY || view == Views.NOTES))
            if (!dayPage) { contents = false; screen = TASKS; view = Views.TODAY; vm.monthOpen.value = true }
            // Closed, the calendar leaves the diary on the day it shows — the one it was opened on, or the one picked in it, or in
            // the feed the day at its top (owner, 17 окт).
            else if (toolsMonth) {
                vm.feedDay.value?.let { d -> vm.pageDay.value = d.takeIf { it != java.time.LocalDate.now() } }
                vm.feedDay.value = null
                vm.monthOpen.value = false
            }
            else { toolsLanded?.let { d -> vm.pageDay.value = d.takeIf { it != java.time.LocalDate.now() } }; vm.monthOpen.value = true }
        },
        section = when { contents -> 3; screen == TOPICS -> 2; screen == TASKS && view == Views.NOTES -> 1; else -> 0 },
        onSection = { p ->
            openTask = null; fullIdea = null; openPerson = null; openNote = null; searching = false
            when (p) { 3 -> contents = true; 0 -> { contents = false; screen = TASKS; view = Views.TODAY }; 1 -> { contents = false; screen = TASKS; view = Views.NOTES }
                else -> { contents = false; screen = TOPICS } }
        })
    // Prototype «inline»: what is unfolded in its row; «назад» folds it.
    val expandedKey by vm.expanded.collectAsState()
    BackHandler(expandedKey != null) { vm.expanded.value = null }
    androidx.compose.runtime.CompositionLocalProvider(LocalRowActions provides RowActions(toTopic = { topicFor = it }, remove = { vm.archiveMatter(it) }), LocalFind provides find,
        LocalPeopleLinks provides tagLinks, LocalPageTools provides pageTools.takeIf { planner },
        LocalVm provides vm) {
    ModalNavigationDrawer(
        drawerState = drawer,
        // On the planner page the menu is a page of its own, not a drawer pulled from the side.
        gesturesEnabled = !planner,
        scrimColor = c.scrim,
        drawerContent = {
            Drawer(data, screen, view, onGo = ::go, onFeedback = { feedback = true; scope.launch { drawer.close() } },
                onLook = { scope.launch { drawer.close() }; vm.setPaper(4) },
                onBuildLongPress = {
                    val on = !settings.devMode
                    vm.updateSettings { it.copy(devMode = on) }
                    if (on) android.widget.Toast.makeText(ctx, s.devUnlocked, android.widget.Toast.LENGTH_SHORT).show()
                })
        },
    ) {
        // «Планер» (owner, 14 окт): on the diary's pages the index on the right edge holds the week and, under Вс, the calendar,
        // the overview, the search and the menu — the top bar is gone, the page starts with its day.
        val pageDay by vm.pageDay.collectAsState()
        val monthOpen by vm.monthOpen.collectAsState()
        val todayNow = java.time.LocalDate.now()
        // There is no calendar page any more: whatever asked for it (a day something went to, «Завтра — N дел») opens the month on «Сегодня».
        LaunchedEffect(view) {
            if (view == Views.WEEK) { vm.calendarOpenOn.value?.let { d -> vm.pageDay.value = d.takeIf { it != todayNow }; vm.calendarOpenOn.value = null }; vm.monthOpen.value = true; view = Views.TODAY }
        }
        val plannerPage = planner
        val landed = vm.landedDay.collectAsState().value
        // A topic, the search, a person or a note lies open on the page: the printed frame steps aside, the index stays.
        // A card (a matter, a note, a topic) is a page of the book (owner, 14 окт): the frame stays over it.
        val overlay = searching || openPerson != null || openNote != null
        // Owner, 14 окт: no tabs at the foot — the sections are in the contents, opened by ≡ at the top by the search.
        // A card's window open: the page under it out of focus.
        val cardOver = openDeal != null || openPiece != null ||
            openTask?.let { id -> data.tasks.find { it.id == id }?.let { !it.topic || (it.thought && fullIdea != id) } } == true
        PageSheet(frame = !overlay) {
            Column(Modifier.fillMaxSize().frosted(cardOver).statusBarsPadding().imePadding()) {
                fun bare(t: ru.anjeib.margin.data.Task) = t.thought && t.subtasks.isEmpty()
                val shown = remember(data, feedFilter) {
                    when (feedFilter) {
                        "deals" -> data.copy(tasks = data.tasks.filter { !it.topic })
                        "topics" -> data.copy(tasks = data.tasks.filter { it.topic && !bare(it) })
                        "ideas" -> data.copy(tasks = data.tasks.filter { bare(it) })
                        else -> data
                    }
                }
                // «личное / работа» (owner, 16 окт): one choice for the whole diary — «Сегодня», «Заметки», «Темы».
                val sphereNow by vm.diarySphere.collectAsState()
                val onPlanner = planner
                val sphereShown = remember(shown, sphereNow, onPlanner) {
                    val sp = sphereNow
                    if (sp == null || !onPlanner) shown else shown.copy(tasks = shown.tasks.filter { if (sp == "work") it.sphere == "work" else it.sphere != "work" })
                }
                // The printed page's head: just the room under the frame's top rule.
                if (plannerPage) {
                    Spacer(Modifier.height(22.dp))
                    // The page in front, by the find bar's name for it — its matches are the ones the arrows go through.
                    val findPageNow = when { contents -> "contents"; screen == TOPICS -> "topics"; screen == TASKS && view == Views.NOTES -> "notes"; screen == TASKS && view == Views.TODAY -> "today"; else -> screen }
                    LaunchedEffect(findPageNow) { find.page = findPageNow; find.cur = 0 }
                    androidx.compose.animation.AnimatedVisibility(find.open,
                        enter = androidx.compose.animation.expandVertically(tween(220)) + androidx.compose.animation.fadeIn(tween(220)),
                        exit = androidx.compose.animation.shrinkVertically(tween(180)) + androidx.compose.animation.fadeOut(tween(180))) {
                        FindBar(find, data, rememberRecentSearches(), onClose = { closeFind() },
                            onPage = { p ->
                                contents = false; openTask = null; openPerson = null; openNote = null; vm.monthOpen.value = false
                                if (p == "topics") screen = TOPICS else { screen = TASKS; view = Views.NOTES }
                            },
                            onAll = { allFor = find.query; closeFind(); searching = true })
                    }
                } else
                TopBar(screen, view, shown, onMenu = { scope.launch { drawer.open() } }, onTab = { i -> view = listOf(Views.TODAY, Views.WEEK, Views.ALL)[i] },
                    onSearch = { searching = true }, filter = feedFilter, onFilter = { feedFilter = it },
                    landedDay = vm.landedDay.collectAsState().value, onLandedDay = { vm.calendarOpenOn.value = it })
                // v2: no section row — almost everything happens in «Дела»; materials and contacts live in the side menu.
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    val pad = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp)
                    @Composable fun otherScreen(sc: String) {
                        when (sc) {
                            PEOPLE -> PeopleList(data, vm, onOpen = { openPerson = it }, contentPadding = pad)
                            TOPICS -> TaskList(Views.ALL, sphereShown, settings, working, vm, events, onOpen = { openId(it) }, onView = { view = it }, onOpenNote = { openNote = it },
                                contentPadding = pad, topicsOnly = true, findPage = "topics")
                            CALENDAR -> CalendarScreen(data, vm, onOpenTask = { openId(it) }, contentPadding = pad, events = events)
                            ARCHIVE -> ArchiveScreen(data, vm, onOpenNote = { openNote = it }, onOpenPerson = { openPerson = it }, contentPadding = pad, onOpenTask = { openId(it) })
                            MATERIALS -> MaterialsGrid(data.tasks.filter { !(it.topic && it.done) }, onView = { viewing = it }, onTopic = { openId(it) }) { t -> PageHead(s.materialsTitle, s.materialsSub, trailing = t) }
                            SETTINGS -> SettingsScreen(settings, vm, pad)
                            HELP -> HelpScreen(pad)
                            INBOX -> InboxScreen(data, vm, onOpenNote = { openNote = it }, contentPadding = pad, onParse = { e ->
                                // «Разобрать»: the words into the main field on «Сегодня», read there with the live line over it; sent — gone from «Входящие».
                                vm.inboxDraft.value = e.id to e.attachments
                                contents = false; screen = TASKS; view = Views.TODAY; prefill = e.text; focusKey = System.currentTimeMillis()
                            })
                        }
                    }
                    // «Планер»: every move about the diary turns a page — a day, the calendar, the overview, the contents, a section.
                    val pageKey = PageKey(if (contents) MENU else screen, view, pageDay)
                    @Composable fun page(k: PageKey) {
                        when (k.screen) {
                            MENU -> ContentsPage(data, screen, onGo = ::go, onFeedback = { feedback = true },
                                onLook = { contents = false; vm.setPaper(0) },
                                onBuildLongPress = {
                                    val on = !settings.devMode
                                    vm.updateSettings { it.copy(devMode = on) }
                                    if (on) android.widget.Toast.makeText(ctx, s.devUnlocked, android.widget.Toast.LENGTH_SHORT).show()
                                })
                            TASKS -> if (k.view == Views.NOTES) DiaryPage(data, vm, day = k.day ?: LocalDate.now(), events = events, onOpen = { openId(it) }, onPerson = { n -> data.persons.find { it.matches(n) }?.let { openPerson = it.id } }, contentPadding = pad,
                                onOpenNote = { openPiece = it })
                                else if (k.view == Views.WEEK) CalendarScreen(shown, vm, onOpenTask = { openId(it) }, contentPadding = pad, events = events)
                                else TaskList(
                                    k.view, sphereShown, settings, working, vm, if (feedFilter == "all" || feedFilter == "deals") events else emptyList(),
                                    onOpen = { openId(it) }, onView = { view = it }, onOpenNote = { openNote = it },
                                    onOpenNotes = { screen = NOTES }, onOpenInbox = { screen = INBOX },
                                    onTry = { prefill = it; focusKey = System.currentTimeMillis() }, contentPadding = pad, pageDay = k.day,
                                    findPage = if (k.view == Views.TODAY) "today" else null,
                                    onOpenPiece = { openPiece = it }, onPerson = { n -> data.persons.find { it.matches(n) }?.let { openPerson = it.id } },
                                )
                            JOURNAL -> DiaryPage(data, vm, onOpen = { openId(it) }, onPerson = { n -> data.persons.find { it.matches(n) }?.let { openPerson = it.id } }, contentPadding = pad,
                                onOpenNote = { openPiece = it }, journal = true)
                            // «Темы» turn by days too (owner, 17 окт): the topics that stand on that day.
                            TOPICS -> TaskList(Views.ALL, sphereShown, settings, working, vm, events, onOpen = { openId(it) }, onView = { view = it }, onOpenNote = { openNote = it },
                                contentPadding = pad, topicsOnly = true, findPage = "topics", pageDay = k.day)
                            else -> otherScreen(k.screen)
                        }
                    }
                    // The diary's three pages lie side by side — Содержание · Сегодня · Обзор — and turn under the finger (owner, 14 окт);
                    // a day of «Сегодня» turns within its own page; the other sections are pages turned to from the contents.
                    // Owner, 16 окт: a swipe across a day's page (Сегодня, Заметки) turns the day; the sections turn from the head.
                    @Composable fun daySwipe(content: @Composable () -> Unit) = Box(Modifier.fillMaxSize().pointerInput(Unit) {
                        var dx = 0f
                        detectHorizontalDragGestures(onDragStart = { dx = 0f }, onDragEnd = {
                            val step = when { dx < -72.dp.toPx() -> 1L; dx > 72.dp.toPx() -> -1L; else -> 0L }
                            if (step != 0L) { val now = LocalDate.now(); val d = (vm.pageDay.value ?: now).plusDays(step); vm.pageDay.value = d.takeIf { it != now } }
                        }) { ch, d -> dx += d; ch.consume() }
                    }) { content() }
                    // One screen (owner, 17 окт: «сделать Margin ещё проще»): the head with the marks and the week, under it the feed of
                    // every day; no sections to turn. «Дневник», «Контакты» and the menu ≡ open from the head.
                    // «Дневник» lies under the same head (owner, 17 окт: «оставь ту же стабильную шапку… чтобы она не перелистывалась»):
                    // only the feed under it changes; its mark is lit while it is open.
                    if (planner && (screen == TASKS || screen == JOURNAL) && !contents) {
                        val todayF = LocalDate.now()
                        val feedDay by vm.feedDay.collectAsState()
                        val inJournal = screen == JOURNAL
                        Column(Modifier.fillMaxSize().screenRule()) {
                            PlannerHead(sphereShown, vm, events, top = feedDay ?: pageDay ?: todayF, journalOn = inJournal,
                                onPeople = { go(PEOPLE) }, onJournal = { if (inJournal) { screen = TASKS; view = Views.TODAY } else go(JOURNAL) }, onMenu = { contents = true })
                            Box(Modifier.weight(1f).fillMaxWidth()) {
                                androidx.compose.runtime.CompositionLocalProvider(LocalFramed provides true) {
                                    androidx.compose.animation.Crossfade(inJournal, animationSpec = tween(220), label = "journal") { j ->
                                        if (j) page(PageKey(JOURNAL, view, pageDay)) else page(PageKey(TASKS, Views.TODAY, pageDay))
                                    }
                                }
                            }
                        }
                    }
                    // A section (settings, inbox, materials, contacts, archive, help) goes back to the contents with a swipe to the right
                    // (owner, 14 окт: one couldn't tell how to leave it).
                    else if (planner) Box(Modifier.fillMaxSize().pointerInput(screen, contents) {
                        var dx = 0f
                        detectHorizontalDragGestures(onDragStart = { dx = 0f }, onDragEnd = { if (dx > 90.dp.toPx()) backFromSection() }) { _, d -> dx += d }
                    }) { PageTurn(pageKey, order = { it.order() }) { k -> page(k) } }
                    else Crossfade(screen, animationSpec = tween(260, easing = androidx.compose.animation.core.FastOutSlowInEasing), label = "screen") { sc ->
                        when (sc) {
                            TASKS -> Crossfade(view, animationSpec = tween(220, easing = androidx.compose.animation.core.FastOutSlowInEasing), label = "view") { v ->
                                // «Сегодня» — what now, «Календарь» — what when, «Обзор» — what there is.
                                if (v == Views.WEEK) CalendarScreen(shown, vm, onOpenTask = { openId(it) }, contentPadding = pad, events = events)
                                else TaskList(
                                    v, shown, settings, working, vm, if (feedFilter == "all" || feedFilter == "deals") events else emptyList(),
                                    onOpen = { openId(it) }, onView = { view = it }, onOpenNote = { openNote = it },
                                    onOpenNotes = { screen = NOTES }, onOpenInbox = { screen = INBOX },
                                    onTry = { prefill = it; focusKey = System.currentTimeMillis() }, contentPadding = pad,
                                )
                            }
                            else -> otherScreen(sc)
                        }
                    }
                    Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(20.dp).background(Brush.verticalGradient(listOf(Color.Transparent, c.bg))).fieldRule())
                    // The way out, on the margin at the top as on every card (owner, 14 окт): from the menu, «Дневник» and «Контакты»
                    // — to the diary; from what the menu opens — back to the menu.
                    if (planner && (contents || screen != TASKS && screen != JOURNAL)) Box(Modifier.align(Alignment.TopStart).width(MarginW).padding(top = 4.dp), contentAlignment = Alignment.Center) {
                        Box(Modifier.size(40.dp).clip(CircleShape).clickable(onClickLabel = s.backWord) { backFromSection() },
                            contentAlignment = Alignment.Center) { BackGlyph(c.ink.copy(alpha = 0.8f), 21.dp) }
                    }
                }
                if ((screen == TASKS || screen == NOTES || screen == TOPICS || screen == JOURNAL) && !contents && !find.open) Column(
                    // The planner (design/v9): the field wide on the page, the margin's line ending just under it, the frame's rule below.
                    // A band of tinted paper with a double rule over it (owner, 14 окт): the place to write is unmistakable on a full page.
                    // Owner, 14 окт: the field floats over the stickers — one block: the hour, the words with the paperclip and the
                    // microphone, «send».
                    if (planner) Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 10.dp)
                    else Modifier.fillMaxWidth().background(c.bg).fieldRule().navigationBarsPadding().padding(horizontal = 14.dp).padding(bottom = 10.dp),
                ) {
                    if (screen == TASKS) {
                        // Keep the last exchange on screen while it fades out, so it leaves gracefully.
                        val trayShown = tray != null || pending != null || busy == "main"
                        var lastTray by androidx.compose.runtime.remember { mutableStateOf<Triple<String?, Pending?, Boolean>>(Triple(null, null, false)) }
                        if (trayShown) lastTray = Triple(tray, pending, busy == "main")
                        // The exchange over the field stands on the page itself: the margin rule stops under it, never crosses its words.
                        AnimatedVisibility(trayShown, modifier = Modifier.background(c.bg), enter = fadeIn(), exit = fadeOut(androidx.compose.animation.core.tween(350)) + androidx.compose.animation.shrinkVertically(androidx.compose.animation.core.tween(350))) {
                        Tray(
                            data, lastTray.first, lastTray.second, lastTray.third,
                            canUndo = vm::canUndo, onUndo = vm::undo,
                            onOpenTask = { openId(it) },
                            onToggleTask = vm::toggleTask, onToggleSub = vm::toggleSubtask,
                            onOption = { vm.send(it) },
                            onOpenSettings = { screen = SETTINGS },
                            onDismiss = vm::dismissTray,
                            onConfirm = { vm.confirm() },
                            onEdit = {
                                // Edit opens what was made: the task, or the note when it was a note.
                                val ids = vm.confirm()
                                if (ids.isNotEmpty()) openId(ids.first())
                                else vm.landed.value.firstOrNull { id -> vm.data.value.notes.any { it.id == id } }?.let { openNote = it }
                            },
                            onReject = vm::rejectPending,
                            onHold = vm::holdPending,
                            onSeparate = vm::pendingAsNewNote,
                            onKeepContact = { vm.keepContact(it) },
                            onMoveTo = { vm.moveToTopic(it) },
                            onMisparse = { pd, got -> vm.misparsed(pd, got) },
                            draftOf = { vm.draftOf(it) },
                            onAskWhen = { _, what ->
                                // The preview's ids are not the saved ones: take the new task without a time from what was saved.
                                val ids = vm.confirm()
                                val id = ids.firstOrNull { i -> vm.data.value.tasks.find { it.id == i }?.let { it.time == null && it.waitingFor == null } == true } ?: ids.firstOrNull()
                                if (id != null) { openTaskDialog = what; openTask = id }
                            },
                        )
                        }
                    } else {
                        notice?.takeIf { it.scopeKey == "notes" }?.let { Box(Modifier.padding(start = FieldLead)) { NoticeLine(it, vm) } }
                    }
                    Spacer(Modifier.height(8.dp))
                    // Copied a message in MAX or Telegram a moment ago → one tap hands it in, with who and what.
                    // Fable, 15 окт: the offer goes away on its own after half a minute — it hung over the field as long as the clipboard held text.
                    LaunchedEffect(copied) { if (copied) { kotlinx.coroutines.delay(30_000); onCopiedSeen() } }
                    if (copied && !typing && busy == null && pending == null && (screen == TASKS || screen == NOTES)) {
                        Row(Modifier.fillMaxWidth().padding(start = FieldLead, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Pill(s.pasteCopied, accent = true, onClick = { copiedText = readCopied(); if (copiedText == null) onCopiedSeen() })
                            Box(Modifier.size(32.dp).clip(CircleShape).clickable { onCopiedSeen() }, contentAlignment = Alignment.Center) {
                                CloseGlyph(c.graphite, 18.dp)
                            }
                        }
                    }
                    val calDay by vm.calendarDay.collectAsState()
                    // «Inline»: the field writes into what is unfolded; what is done with it is in its long-press menu.
                    val inlineHint = data.inlineHint(expandedKey).takeIf { screen == TASKS }
                    SmartComposer(
                        vm,
                        hint = inlineHint ?: if (screen == JOURNAL) s.journalHint else if (screen == TOPICS) s.topicsHint else if (screen == NOTES) s.notesHint else if (screen == TASKS && view == Views.NOTES && !contents) s.diaryHint else calDay?.takeIf { screen == TASKS }?.let { s.dealOnHint(it.dayMonth()) } ?: s.composerHint,
                        busy = busy == (if (screen == NOTES) "notes" else "main"),
                        // «Темы»: the words start a topic — its name, and a list under it becomes its matters.
                        onSend = { t, f -> if (screen == JOURNAL) vm.addDiaryNote(t, f) else if (screen == TASKS && inlineHint != null && vm.inlineSend(expandedKey, t, f)) Unit else if (screen == TOPICS) vm.send("/" + s.tagTopic + " " + t, files = f) else if (screen == TASKS && view == Views.NOTES) vm.addDiaryNote(t, f) else {
                            // Words taken from «Входящие» to be read here: their files go with them, and the thing leaves «Входящие».
                            val fromInbox = vm.inboxDraft.value.takeIf { screen == TASKS }
                            vm.send(t, scope = if (screen == NOTES) Scope.Notes else Scope.Main, files = f + (fromInbox?.second ?: emptyList()))
                            if (fromInbox != null) vm.inboxSent()
                        } },
                        prefill = prefill, onPrefillUsed = { prefill = null },
                        onTyping = { typing = it }, slash = screen != NOTES && screen != JOURNAL && view != Views.NOTES, preview = if (view == Views.NOTES || screen == JOURNAL) null else ({ vm.previewOf(it) }),
                        mentions = screen == JOURNAL || screen == TASKS && view == Views.NOTES,
                        focusKey = focusKey, micKey = micKey, floating = planner,
                    )
                }
            }
            // The planner: every page that opens over the diary is a sheet of the same book.
            if (searching) Box(Modifier.fillMaxSize().background(c.bg).paperGrain()) { SearchScreen(data, vm, initial = allFor, onBack = { searching = false },
                onTask = { openId(it) }, onNote = { openNote = it }, onPerson = { openPerson = it }) }
            openPerson?.let { id ->
                val p = data.persons.find { it.id == id }
                if (p == null) openPerson = null
                else Box(Modifier.fillMaxSize().background(c.bg).paperGrain()) { PersonScreen(p, data, vm, onBack = { openPerson = null }, onOpenTask = { openTask = it }, onOpenNote = { openNote = it }) }
            }
            openNote?.let { id ->
                val note = data.notes.find { it.id == id }
                // v2: a note became a thought — it opens as a topic.
                if (note == null) { openNote = null; if (data.tasks.any { it.id == id }) openId(id) }
                else Box(Modifier.fillMaxSize().background(c.bg).paperGrain()) { NoteEditor(note, data, vm, onBack = { openNote = null }, onOpenTask = { openTask = it }) }
            }
            // v2: a topic on the whole screen, sliding in from the side; when a task grows into one, its sheet gives way to it.
            // An idea opens as a half-page notebook card; «Открыть целиком» gives it the whole screen like any topic.
            val cardKey0 = openDeal ?: openPiece
            val topicId = openTask?.takeIf { id -> data.tasks.find { it.id == id }?.let { it.topic && (!it.thought || fullIdea == id) } == true }
            var lastTopic by remember { mutableStateOf<String?>(null) }
            if (topicId != null) lastTopic = topicId
            AnimatedVisibility(topicId != null,
                enter = androidx.compose.animation.slideInHorizontally(androidx.compose.animation.core.tween(260)) { it / 3 } + fadeIn(androidx.compose.animation.core.tween(220)),
                exit = androidx.compose.animation.slideOutHorizontally(androidx.compose.animation.core.tween(220)) { it / 3 } + fadeOut(androidx.compose.animation.core.tween(180))) {
                data.tasks.find { it.id == (topicId ?: lastTopic) }?.let { t ->
                    Box(Modifier.fillMaxSize().background(c.bg).paperGrain().frosted(cardKey0 != null)) { TopicScreen(t, data, vm, openTab, openHl, onClose = { openTask = null; openHl = null; openTab = 0; fullIdea = null },
                        onOpenPerson = { openTask = null; openPerson = it }) }
                }
            }
            // A matter, a topic's matter, an idea or a note opens as a window over the page (owner, 16 окт, «core»; see CardPage).
            val cardKey = openDeal ?: openTask?.takeIf { id -> data.tasks.find { it.id == id }?.let { !it.topic || (it.thought && fullIdea != id) } == true }
            var lastCard by remember { mutableStateOf<String?>(null) }
            if (cardKey != null) lastCard = cardKey
            AnimatedVisibility(cardKey != null,
                enter = fadeIn(androidx.compose.animation.core.tween(200)) + androidx.compose.animation.scaleIn(androidx.compose.animation.core.tween(220), initialScale = 0.96f),
                exit = fadeOut(androidx.compose.animation.core.tween(160)) + androidx.compose.animation.scaleOut(androidx.compose.animation.core.tween(160), targetScale = 0.97f)) {
                val k = cardKey ?: lastCard
                val qr = k?.let { ru.anjeib.margin.data.questionRef(it) }
                if (qr != null) {
                    val topic = data.tasks.find { it.id == qr.first }
                    if (topic == null) LaunchedEffect(Unit) { openDeal = null }
                    else DealSheet(topic, qr.second, vm, onClose = { openDeal = null }, onOpenParent = { pid -> openDeal = "q:${topic.id}:$pid" },
                        // Owner, 10 окт: who goes to the topic wants the topic itself — from its start, not at this matter.
                        onOpenTopic = { openDeal = null; openTask = topic.id; openTab = 0; openHl = null },
                        onOpenPerson = { openDeal = null; openPerson = it })
                } else data.tasks.find { it.id == k }?.let { t ->
                    if (t.topic && t.thought) IdeaSheet(t, vm, onClose = { openTask = null }, onOpenFull = { fullIdea = t.id })
                    else TaskSheet(
                        t, data, vm, onClose = { openTask = null; openTaskDialog = null; openHl = null; openTab = 0 },
                        startTab = openTab, startHl = openHl,
                        startDialog = openTaskDialog, onStartDialogUsed = { openTaskDialog = null },
                        onOpenNote = { openTask = null; openNote = it },
                        onOpenPerson = { openTask = null; openPerson = it },
                        onOpenTask = { openTask = it },
                    )
                }
            }
            // A note of a matter or a topic, opened from «Заметки»: a page of its own; its link goes where it lives.
            var lastPiece by remember { mutableStateOf<String?>(null) }
            if (openPiece != null) lastPiece = openPiece
            AnimatedVisibility(openPiece != null,
                enter = fadeIn(androidx.compose.animation.core.tween(200)) + androidx.compose.animation.scaleIn(androidx.compose.animation.core.tween(220), initialScale = 0.96f),
                exit = fadeOut(androidx.compose.animation.core.tween(160)) + androidx.compose.animation.scaleOut(androidx.compose.animation.core.tween(160), targetScale = 0.97f)) {
                (openPiece ?: lastPiece)?.let { k -> NoteCard(k, data, vm, onClose = { openPiece = null }, onOpen = { id -> openPiece = null; openId(id) }) }
            }
            // A name that isn't a contact was tapped: «Марина · + контакт», quietly, for a few seconds.
            AnimatedVisibility(contactOffer != null && undoBar == null, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(start = 14.dp, end = 14.dp, bottom = 84.dp)) {
                val n = contactOffer
                val barBg = if (planner) c.card else c.bubble
                val barFg = if (planner) c.ink else c.onBubble
                val barLink = if (planner) c.sea else c.linkOnBubble
                if (n != null) Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(barBg)
                    .then(if (planner) Modifier.border(1.dp, c.hairline, RoundedCornerShape(14.dp)) else Modifier)
                    .padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(s.notAContact(n), style = Type.small.copy(color = barFg, fontSize = 14.sp), modifier = Modifier.weight(1f))
                    Text(s.addContact, style = Type.small.copy(color = barLink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { vm.keepContact(n) }.padding(12.dp))
                }
            }
            // A word from the app («Перенесено в тему…», «Контакт заведён») — the same bar, no button.
            AnimatedVisibility(toast != null && undoBar == null && contactOffer == null, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().imePadding().padding(start = 14.dp, end = 14.dp, bottom = 84.dp)) {
                val tt = toast
                if (tt != null) Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (planner) c.card else c.bubble)
                    .then(if (planner) Modifier.border(1.dp, c.hairline, RoundedCornerShape(14.dp)) else Modifier)
                    .padding(horizontal = 16.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(tt, style = Type.small.copy(color = if (planner) c.ink else c.onBubble, fontSize = 14.sp), modifier = Modifier.weight(1f))
                }
            }
            // «Удалено · Отменить» for a few seconds after a swipe — above whatever screen is open.
            AnimatedVisibility(undoBar != null, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(start = 14.dp, end = 14.dp, bottom = 84.dp)) {
                val ub = undoBar
                val barBg = if (planner) c.card else c.bubble
                val barFg = if (planner) c.ink else c.onBubble
                val barLink = if (planner) c.sea else c.linkOnBubble
                if (ub != null) Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(barBg)
                    .then(if (planner) Modifier.border(1.dp, c.hairline, RoundedCornerShape(14.dp)) else Modifier)
                    .padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(ub.text, style = Type.small.copy(color = barFg, fontSize = 14.sp), modifier = Modifier.weight(1f))
                    Text(s.undo, style = Type.small.copy(color = barLink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { vm.undoBar() }.padding(12.dp))
                }
            }
        }
    }
    }
    topicFor?.let { ref -> TopicPickDialog(vm.data.value, ref, onDismiss = { topicFor = null },
        onCreate = { name -> vm.newTopic(name)?.let { tid -> vm.intoTopic(ref, tid) }; topicFor = null }) { tid -> vm.intoTopic(ref, tid); topicFor = null } }

    // «Отправить отзыв»: an e-mail to the developer with the crash and misunderstanding logs — never the records.
    LaunchedEffect(feedback) { if (feedback) { sendFeedback(ctx, vm); feedback = false } }
    if (crashOffer) androidx.compose.material3.AlertDialog(
        onDismissRequest = { crashOffer = false; ru.anjeib.margin.data.Feedback.crashSeen(ctx) },
        containerColor = c.card,
        title = { Text(s.crashTitle, style = Type.cardTitle.copy(color = c.ink)) },
        text = { Text(s.crashText, style = Type.body.copy(color = c.graphite)) },
        confirmButton = { androidx.compose.material3.TextButton({ crashOffer = false; ru.anjeib.margin.data.Feedback.crashSeen(ctx); sendFeedback(ctx, vm) }) { Text(s.crashSend, color = c.link) } },
        dismissButton = { androidx.compose.material3.TextButton({ crashOffer = false; ru.anjeib.margin.data.Feedback.crashSeen(ctx) }) { Text(s.crashLater, color = c.graphite) } },
    )

    val storeProblem by vm.storeProblem.collectAsState()
    storeProblem?.let { p ->
        val (title, text) = when (p.kind) {
            ru.anjeib.margin.data.LoadProblem.Kind.RESTORED -> s.storeRestoredTitle to s.storeRestoredText
            ru.anjeib.margin.data.LoadProblem.Kind.LOST -> s.storeLostTitle to s.storeLostText
            ru.anjeib.margin.data.LoadProblem.Kind.NEWER -> s.storeNewerTitle to s.storeNewerText
        }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { vm.dismissStoreProblem() },
            containerColor = c.card,
            title = { Text(title, style = Type.cardTitle.copy(color = c.ink)) },
            text = { Text(text, style = Type.body.copy(color = c.graphite)) },
            confirmButton = { androidx.compose.material3.TextButton({ vm.dismissStoreProblem() }) { Text(s.close, color = c.link) } },
        )
    }

    copiedText?.let { t ->
        BackHandler { copiedText = null; onCopiedSeen() }
        val sender = remember(t) { ru.anjeib.margin.ai.Forward.sender(t) }
        ForwardSheet(
            vm, from = s.forwardFrom(s.viaCopy).replaceFirstChar { it.uppercase() }, sender = sender,
            isContact = ru.anjeib.margin.ai.Forward.isContact(data, sender), text = t, files = 0, loading = false,
            onAddContact = { sender?.let { ru.anjeib.margin.ai.Forward.addContact(ctx, it) } },
            onSend = { comment, f -> vm.forwardIn(t, "copy", comment, f, sender?.let { it.name ?: it.company ?: it.address }); copiedText = null; onCopiedSeen() },
            onDismiss = { copiedText = null; onCopiedSeen() },
        )
    }

    viewing?.let { a -> Viewer(a, vm, onClose = { viewing = null }) }

    if (!settings.onboarded) Onboarding(vm, onDone = { vm.updateSettings { it.copy(onboarded = true) }; vm.addIntroOnce(); vm.finishSetup() })
    // Once per install: offer the sample data (the user's own is kept aside and comes back from Settings).
    // Only where the designer's months are the point (the demo build loads them by itself); others get «Знакомство с Margin».
    var demoOffer by remember { mutableStateOf(ru.anjeib.margin.BuildConfig.DEMO && !vm.demoAsked()) }
    if (settings.onboarded && demoOffer) androidx.compose.material3.AlertDialog(
        onDismissRequest = { demoOffer = false; vm.demoSkip() }, containerColor = c.card,
        title = { Text(s.demoAskTitle, style = Type.cardTitle.copy(color = c.ink)) },
        text = { Text(s.demoAskText, style = Type.body.copy(color = c.graphite)) },
        confirmButton = { androidx.compose.material3.TextButton({ demoOffer = false; vm.loadDemo() }) { Text(s.demoLoad, color = c.link) } },
        dismissButton = { androidx.compose.material3.TextButton({ demoOffer = false; vm.demoSkip() }) { Text(s.demoLater, color = c.graphite) } },
    )

    // The cards get the same «В тему…» / «Удалить» as the rows (their sheets live outside the drawer).
    androidx.compose.runtime.CompositionLocalProvider(LocalRowActions provides RowActions(toTopic = { topicFor = it }, remove = { vm.archiveMatter(it) })) {
    // The cards are pages now (owner, 14 окт) — opened over the diary, above (see «cardKey»).
    openTask?.let { id -> if (data.tasks.none { it.id == id }) openTask = null }
    }
}

@Composable
private fun TopBar(screen: String, view: String, data: AppData, onMenu: () -> Unit, onTab: (Int) -> Unit, onSearch: () -> Unit, filter: String = "all", onFilter: (String) -> Unit = {},
                   /** A day something just went to: the calendar shows its number, a tap opens it there. */
                   landedDay: java.time.LocalDate? = null, onLandedDay: (java.time.LocalDate) -> Unit = {}) {
    val s = M.s
    val c = M.c
    var filterMenu by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
        // The day is home: from «Календарь» and «Обзор» an arrow leads back to it, from the day — the menu.
        val away = screen == TASKS && (view == Views.WEEK || view == Views.ALL)
        if (away) Box(Modifier.align(Alignment.CenterStart).size(44.dp).clip(CircleShape).clickable(onClickLabel = s.backWord) { onTab(0) }
            .semantics { contentDescription = s.backWord }, contentAlignment = Alignment.Center) {
            BackGlyph(M.c.ink, 23.dp)
        }
        else Box(Modifier.align(Alignment.CenterStart).size(44.dp).clip(CircleShape).clickable(onClickLabel = s.a11yMenu, onClick = onMenu)
            .semantics { contentDescription = s.a11yMenu }, contentAlignment = Alignment.Center) {
            MenuGlyph()
        }
        // One row of controls (v8): from the day — the calendar and the overview beside the search; elsewhere only the search.
        Row(Modifier.align(Alignment.CenterEnd), verticalAlignment = Alignment.CenterVertically) {
            // Icons alone, one style for all four (owner, 11 окт): what they are is said in the tour, in detail — in «Помощь».
            @Composable fun tool(label: String, onClick: () -> Unit, glyph: @Composable () -> Unit) =
                Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClickLabel = label, onClick = onClick).semantics { contentDescription = label },
                    contentAlignment = Alignment.Center) { glyph() }
            if (screen == TASKS && !away) {
                tool(s.calendarTitle, { landedDay?.let(onLandedDay); onTab(1) }) {
                    // Something just went to another day: the icon makes way for that day's number in a terracotta ring, breathing a moment.
                    androidx.compose.animation.Crossfade(landedDay, label = "landedDay") { day ->
                        if (day == null) CalendarGlyph(c.ink, 23.dp)
                        else {
                            val pulse = androidx.compose.animation.core.rememberInfiniteTransition(label = "pulse")
                            val k by pulse.animateFloat(1f, 1.15f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(500), androidx.compose.animation.core.RepeatMode.Reverse), label = "k")
                            Box(Modifier.size(30.dp).graphicsLayer { scaleX = k; scaleY = k }.clip(CircleShape).background(c.accent), contentAlignment = Alignment.Center) {
                                Text(day.dayOfMonth.toString(), style = Type.small.copy(color = c.onAccent, fontSize = 13.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold))
                            }
                        }
                    }
                }
                tool(s.overviewWord, { onTab(2) }) { OverviewGlyph(c.ink, 23.dp) }
            }
            if (screen == TASKS || screen == PEOPLE) tool(s.search, onSearch) { SearchGlyph(c.ink, 23.dp) }
        }
    }
}

@Composable
private fun Drawer(data: AppData, screen: String, view: String, onGo: (String, String?) -> Unit, onFeedback: () -> Unit = {}, onLook: () -> Unit = {}, onBuildLongPress: () -> Unit = {}) {
    val c = M.c
    val s = M.s
    val today = LocalDate.now()
    ModalDrawerSheet(drawerContainerColor = c.bg, drawerShape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp), modifier = Modifier.width(310.dp)) {
        Column(Modifier.fillMaxHeight().padding(vertical = 20.dp)) {
          Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Column(Modifier.padding(horizontal = 26.dp)) {
                Text("Margin", style = Type.display.copy(color = c.ink, fontSize = 38.sp))
                Text(today.longTitle(), style = Type.small.copy(color = c.graphite, fontSize = 14.sp))
            }
            Spacer(Modifier.height(22.dp))
            NavRow(Icons.Outlined.Inbox, s.inboxNote, data.inboxCount().takeIf { it > 0 }?.toString() ?: "", screen == INBOX) { onGo(INBOX, null) }
            NavRow(Icons.Outlined.DynamicFeed, s.feedTitle, "", screen == TASKS && !view.startsWith("p:")) { onGo(TASKS, Views.TODAY) }
            NavRow(Icons.Outlined.PhotoLibrary, s.materialsTitle, "", screen == MATERIALS) { onGo(MATERIALS, null) }
            NavRow(Icons.Outlined.PeopleOutline, s.peopleTitle, "", screen == PEOPLE) { onGo(PEOPLE, null) }
            Hairline(Modifier.padding(horizontal = 26.dp, vertical = 14.dp))
            NavRow(Icons.Outlined.Archive, s.archiveTitle, (data.notes.count { it.archived } + data.persons.count { it.archived } + data.tasks.count { it.topic && it.done }).takeIf { it > 0 }?.toString() ?: "", screen == ARCHIVE) { onGo(ARCHIVE, null) }
          }
            // Settings at the bottom, out of the way.
            Spacer(Modifier.height(8.dp))
            NavRow(Icons.Outlined.Settings, s.settings, "", screen == SETTINGS, small = true) { onGo(SETTINGS, null) }
            NavRow(Icons.AutoMirrored.Outlined.HelpOutline, s.helpTitle, "", screen == HELP, small = true) { onGo(HELP, null) }
            NavRow(Icons.Outlined.ChatBubbleOutline, s.sendFeedback, "", false, small = true) { onFeedback() }
            // Which build this is — so a screenshot or a complaint says what it is about. The only place the version is shown;
            // a long press opens the developer section.
            Text(s.buildLabel(ru.anjeib.margin.BuildConfig.VERSION_NAME, ru.anjeib.margin.BuildConfig.VERSION_CODE, ru.anjeib.margin.BuildConfig.DEBUG),
                style = Type.small.copy(color = c.graphite, fontSize = 12.sp, fontFeatureSettings = "tnum"),
                modifier = Modifier.padding(start = 26.dp, top = 10.dp)
                    .combinedClickable(onClick = {}, onLongClick = onBuildLongPress))
            // Owner, 12 окт: how far the speech model has come, then «Готово» — next to the build, where things about the app live.
            speechStatus()?.let { (line, retry) ->
                val ctx = LocalContext.current
                val scope = rememberCoroutineScope()
                Text(s.speechModel + " · " + line, style = Type.small.copy(color = c.graphite, fontSize = 12.sp, fontFeatureSettings = "tnum"),
                    modifier = Modifier.padding(start = 26.dp, top = 4.dp)
                        .then(if (retry) Modifier.clickable { scope.launch { ru.anjeib.margin.voice.LocalSpeech.ensure(ctx) } } else Modifier))
            }
        }
    }
}

/**
 * Where speech on the phone stands: «45%», «Готово», «скачается по Wi-Fi» or «не скачалось — повторить» (true — a tap
 * fetches it now). Null where it doesn't apply: a phone it can't run on, or English, which the model doesn't read.
 */
@Composable
internal fun speechStatus(): Pair<String, Boolean>? {
    val L = ru.anjeib.margin.voice.LocalSpeech
    if (!L.supported || ru.anjeib.margin.i18n.L.lang != ru.anjeib.margin.i18n.Lang.Ru) return null
    val s = M.s
    val update = L.updating.collectAsState().value
    return when (val st = L.state.collectAsState().value) {
        // A newer model on its way while this one keeps reading.
        is ru.anjeib.margin.voice.LocalSpeech.State.Ready -> (update?.let { s.speechUpdating((it * 100).toInt()) } ?: s.speechReady) to false
        is ru.anjeib.margin.voice.LocalSpeech.State.Downloading -> "${(st.progress * 100).toInt()}%" to false
        is ru.anjeib.margin.voice.LocalSpeech.State.Failed -> s.speechFailed to true
        is ru.anjeib.margin.voice.LocalSpeech.State.Missing -> s.speechWaitsWifi to true
    }
}

@Composable
private fun NavRow(icon: ImageVector?, label: String, count: String, active: Boolean, small: Boolean = false, onClick: () -> Unit) {
    val c = M.c
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp).clip(RoundedCornerShape(14.dp))
            .background(if (active) c.linkSoft else Color.Transparent).clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = if (small) 10.dp else 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(icon, null, tint = if (active) c.link else c.graphite, modifier = Modifier.size(if (small) 18.dp else 21.dp))
        else Box(Modifier.padding(horizontal = 6.dp).size(7.dp).clip(CircleShape).background(c.link.copy(alpha = 0.7f)))
        Spacer(Modifier.width(16.dp))
        // Playfair is for page and card titles; every list — the menu too — is set in Inter.
        Text(label, style = (if (small) Type.body.copy(fontSize = 15.sp) else Type.body.copy(fontSize = 16.5.sp, fontWeight = FontWeight.Medium)).copy(color = c.ink), modifier = Modifier.weight(1f))
        Text(count, style = Type.small.copy(color = c.graphite))
    }
}

/** Opens the mail app with the developer's address, the build, the phone and the logs attached (nothing of the user's records). */
fun sendFeedback(ctx: android.content.Context, vm: MainVm) {
    val s = ru.anjeib.margin.i18n.L.s
    val fb = ru.anjeib.margin.data.Feedback
    val version = ru.anjeib.margin.BuildConfig.VERSION_NAME + " (" + ru.anjeib.margin.BuildConfig.VERSION_CODE + ")"
    val device = android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL + " · Android " + android.os.Build.VERSION.RELEASE
    val files = fb.attachments(ctx)
    val uris = ArrayList(files.map { androidx.core.content.FileProvider.getUriForFile(ctx, ctx.packageName + ".files", it) })
    val body = s.feedbackBody(version, device, files.size) + "\n" + vm.betaStats()
    // Mail apps only (the mailto: selector), with the logs attached; no mail app — any app that takes files.
    val i = android.content.Intent(if (uris.size > 1) android.content.Intent.ACTION_SEND_MULTIPLE else android.content.Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(android.content.Intent.EXTRA_EMAIL, arrayOf(fb.EMAIL))
        .putExtra(android.content.Intent.EXTRA_SUBJECT, s.feedbackSubject(version))
        .putExtra(android.content.Intent.EXTRA_TEXT, body)
        .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        .apply {
            if (uris.size > 1) putParcelableArrayListExtra(android.content.Intent.EXTRA_STREAM, uris)
            else uris.firstOrNull()?.let { putExtra(android.content.Intent.EXTRA_STREAM, it) }
            if (uris.isNotEmpty()) clipData = android.content.ClipData.newRawUri("", uris.first()).also { c -> uris.drop(1).forEach { c.addItem(android.content.ClipData.Item(it)) } }
        }
    val mail = android.content.Intent(i).apply { selector = android.content.Intent(android.content.Intent.ACTION_SENDTO, android.net.Uri.parse("mailto:")) }
    runCatching { ctx.startActivity(mail) }
        .recoverCatching { ctx.startActivity(android.content.Intent.createChooser(i, s.sendFeedback).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
        .onFailure { Toast.makeText(ctx, s.feedbackNoMail, Toast.LENGTH_SHORT).show() }
}


/** The contents page of the planner, turned to like any other. */
private const val MENU = "menu"

/** Which page of the diary is open: its section, its view, its day. [order] — where it lies in the book. */
private data class PageKey(val screen: String, val view: String, val day: LocalDate?) {
    fun order(): Long = when {
        screen == MENU -> -1_000_000L
        screen == TASKS && view == Views.TODAY -> (day ?: LocalDate.now()).toEpochDay() - LocalDate.now().toEpochDay()
        screen == TASKS && view == Views.WEEK -> 1_000_000L
        screen == TASKS -> 2_000_000L
        else -> 3_000_000L + listOf(TOPICS, INBOX, MATERIALS, PEOPLE, ARCHIVE, SETTINGS, HELP).indexOf(screen)
    }
}

/**
 * The diary's contents (owner, 14 окт): the menu as the page at the front of the planner — the sections set in the book face,
 * each a line with its count at the end, the way a printed contents page lists its chapters.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ContentsPage(data: AppData, screen: String, onGo: (String, String?) -> Unit, onFeedback: () -> Unit, onLook: () -> Unit, onBuildLongPress: () -> Unit) {
    val c = M.c
    val s = M.s
    @Composable fun line(label: String, count: String, active: Boolean, small: Boolean = false, onClick: () -> Unit) {
        Margined {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(vertical = 10.dp),
                verticalAlignment = Alignment.Bottom) {
                // One size for one kind of thing (owner, 16 окт): a line of the contents is a row like any other.
                Text(label, style = Type.body.copy(fontFamily = rowFamily, fontSize = rowSize, lineHeight = rowLine, color = if (active) c.accentText else c.ink))
                // the dotted leader of a contents page
                Box(Modifier.weight(1f).padding(horizontal = 8.dp, vertical = 6.dp).height(1.dp).drawBehind {
                    var x = 0f; val step = 5.dp.toPx()
                    while (x < size.width) { drawCircle(c.graphite.copy(alpha = 0.45f), 0.8.dp.toPx(), Offset(x, size.height / 2)); x += step }
                })
                Text(count, style = Type.small.copy(fontSize = subSize, color = c.graphite))
            }
        }
    }
    Column(Modifier.fillMaxSize()) {
    // The head stays put and a swipe across it turns the sections, as on every page; in the diary's frame it is the frame's.
    // The menu ≡ (owner, 17 окт): what isn't the diary itself — its name, the way back on the margin (App).
    Box(Modifier.fillMaxWidth().padding(top = 40.dp).screenRule()) { PageHead(s.contentsTitle, null) }
    Column(Modifier.fillMaxSize().screenRule().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        Spacer(Modifier.height(8.dp))
        // The diary, «Дневник» and «Контакты» are in the diary's head; the menu keeps the rest (owner, 17 окт).
        line(s.inboxNote, data.inboxCount().takeIf { it > 0 }?.toString() ?: "", screen == INBOX) { onGo(INBOX, null) }
        line(s.materialsTitle, "", screen == MATERIALS) { onGo(MATERIALS, null) }
        line(s.archiveTitle, "", screen == ARCHIVE) { onGo(ARCHIVE, null) }
        Spacer(Modifier.height(18.dp))
        SectionHead(s.aboutApp, top = 6.dp)
        line(s.settings, "", screen == SETTINGS, small = true) { onGo(SETTINGS, null) }
        line(s.helpTitle, "", screen == HELP, small = true) { onGo(HELP, null) }
        line(s.sendFeedback, "", false, small = true) { onFeedback() }
        Margined(Modifier.padding(top = 14.dp)) {
            Column {
                Text(s.buildLabel(ru.anjeib.margin.BuildConfig.VERSION_NAME, ru.anjeib.margin.BuildConfig.VERSION_CODE, ru.anjeib.margin.BuildConfig.DEBUG),
                    style = Type.italic.copy(color = c.graphite, fontSize = 14.sp),
                    modifier = Modifier.combinedClickable(onClick = {}, onLongClick = onBuildLongPress))
                speechStatus()?.let { (l, retry) ->
                    val ctx = LocalContext.current
                    val scope = rememberCoroutineScope()
                    Text(s.speechModel + " · " + l, style = Type.italic.copy(color = c.graphite, fontSize = 14.sp),
                        modifier = Modifier.padding(top = 4.dp).then(if (retry) Modifier.clickable { scope.launch { ru.anjeib.margin.voice.LocalSpeech.ensure(ctx) } } else Modifier))
                }
            }
        }
    }
    }
}
