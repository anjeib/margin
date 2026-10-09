package ru.anjeib.margin.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
private const val CALENDAR = "calendar"
private const val ARCHIVE = "archive"
private const val MATERIALS = "materials"

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
    /** A task, or a topic's question from the lists: then the topic opens on «Вопросы» with that one lit. */
    fun openId(id0: String) {
        // A day of a matter done on several days opens the matter itself.
        val id = ru.anjeib.margin.data.slotRef(id0)?.first ?: id0
        val q = ru.anjeib.margin.data.questionRef(id)
        // A topic's matter opens its own card (the topic is one tap further, from the card).
        if (q != null) openDeal = id else { openTask = id; openTab = 0; openHl = null }
    }
    var openNote by rememberSaveable { mutableStateOf<String?>(null) }
    var openPerson by rememberSaveable { mutableStateOf<String?>(null) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var typing by rememberSaveable { mutableStateOf(false) }
    var prefill by rememberSaveable { mutableStateOf<String?>(null) }
    var copiedText by remember { mutableStateOf<String?>(null) }
    var feedback by remember { mutableStateOf(false) }
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    LaunchedEffect(shared) { if (!shared.isNullOrBlank()) { prefill = shared; screen = TASKS; onSharedConsumed() } }
    // «Записать» / «Надиктовать» (tile, icon shortcut): back to the main field, keyboard up or the microphone on.
    var focusKey by remember { mutableStateOf<Long?>(null) }
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
    LaunchedEffect(personReq) { personReq?.let { openTask = null; openPerson = it; vm.personOpened() } }
    LaunchedEffect(toast) { toast?.let { Toast.makeText(ctx, it, Toast.LENGTH_SHORT).show(); vm.toastShown() } }

    fun go(newScreen: String, newView: String? = null) {
        screen = newScreen
        if (newView != null) view = newView
        scope.launch { drawer.close() }
    }

    BackHandler(drawer.isOpen) { scope.launch { drawer.close() } }
    BackHandler(!drawer.isOpen && openNote == null && openPerson == null && !searching && (screen != TASKS || view != Views.TODAY)) {
        if (screen != TASKS) screen = TASKS else view = Views.TODAY
    }

    ModalNavigationDrawer(
        drawerState = drawer,
        scrimColor = c.scrim,
        drawerContent = {
            Drawer(data, screen, view, onGo = ::go, onFeedback = { feedback = true; scope.launch { drawer.close() } },
                onBuildLongPress = {
                    val on = !settings.devMode
                    vm.updateSettings { it.copy(devMode = on) }
                    if (on) android.widget.Toast.makeText(ctx, s.devUnlocked, android.widget.Toast.LENGTH_SHORT).show()
                })
        },
    ) {
        Box(Modifier.fillMaxSize().background(c.bg)) {
            Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
                fun bare(t: ru.anjeib.margin.data.Task) = t.thought && t.subtasks.isEmpty()
                val shown = remember(data, feedFilter) {
                    when (feedFilter) {
                        "deals" -> data.copy(tasks = data.tasks.filter { !it.topic })
                        "topics" -> data.copy(tasks = data.tasks.filter { it.topic && !bare(it) })
                        "ideas" -> data.copy(tasks = data.tasks.filter { bare(it) })
                        else -> data
                    }
                }
                TopBar(screen, view, shown, onMenu = { scope.launch { drawer.open() } }, onTab = { i -> view = listOf(Views.TODAY, Views.WEEK, Views.ALL)[i] },
                    onSearch = { searching = true }, filter = feedFilter, onFilter = { feedFilter = it })
                // v2: no section row — almost everything happens in «Дела»; materials and contacts live in the side menu.
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    val pad = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp)
                    Crossfade(screen, animationSpec = tween(260, easing = androidx.compose.animation.core.FastOutSlowInEasing), label = "screen") { sc ->
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
                            NOTES -> NotesList(data, vm, onOpen = { openNote = it }, contentPadding = pad)
                            PEOPLE -> PeopleList(data, vm, onOpen = { openPerson = it }, contentPadding = pad)
                            PROJECTS -> ProjectsScreen(data, onOpen = { go(TASKS, Views.project(it)) }, contentPadding = pad)
                            CALENDAR -> CalendarScreen(data, vm, onOpenTask = { openId(it) }, contentPadding = pad, events = events)
                            ARCHIVE -> ArchiveScreen(data, vm, onOpenNote = { openNote = it }, onOpenPerson = { openPerson = it }, contentPadding = pad, onOpenTask = { openId(it) })
                            MATERIALS -> MaterialsGrid(data.tasks.filter { !(it.topic && it.done) }, onView = { viewing = it }, onTopic = { openId(it) }) { t -> PageHead(s.materialsTitle, s.materialsSub, trailing = t) }
                            SETTINGS -> SettingsScreen(settings, vm, pad)
                            HELP -> HelpScreen(pad)
                            INBOX -> InboxScreen(data, vm, onOpenNote = { openNote = it }, contentPadding = pad)
                        }
                    }
                    Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(20.dp).background(Brush.verticalGradient(listOf(Color.Transparent, c.bg))))
                }
                if (screen == TASKS || screen == NOTES) Column(
                    Modifier.fillMaxWidth().background(c.bg).navigationBarsPadding().padding(horizontal = 14.dp).padding(bottom = 10.dp),
                ) {
                    if (screen == TASKS) {
                        // Keep the last exchange on screen while it fades out, so it leaves gracefully.
                        val trayShown = tray != null || pending != null || busy == "main"
                        var lastTray by androidx.compose.runtime.remember { mutableStateOf<Triple<String?, Pending?, Boolean>>(Triple(null, null, false)) }
                        if (trayShown) lastTray = Triple(tray, pending, busy == "main")
                        AnimatedVisibility(trayShown, enter = fadeIn(), exit = fadeOut(androidx.compose.animation.core.tween(350)) + androidx.compose.animation.shrinkVertically(androidx.compose.animation.core.tween(350))) {
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
                            onAskWhen = { _, what ->
                                // The preview's ids are not the saved ones: take the new task without a time from what was saved.
                                val ids = vm.confirm()
                                val id = ids.firstOrNull { i -> vm.data.value.tasks.find { it.id == i }?.let { it.time == null && it.waitingFor == null } == true } ?: ids.firstOrNull()
                                if (id != null) { openTaskDialog = what; openTask = id }
                            },
                        )
                        }
                    } else {
                        notice?.takeIf { it.scopeKey == "notes" }?.let { NoticeLine(it, vm) }
                    }
                    Spacer(Modifier.height(8.dp))
                    // Copied a message in MAX or Telegram a moment ago → one tap hands it in, with who and what.
                    if (copied && !typing && busy == null && pending == null && (screen == TASKS || screen == NOTES)) {
                        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Pill(s.pasteCopied, accent = true, onClick = { copiedText = readCopied(); if (copiedText == null) onCopiedSeen() })
                            Box(Modifier.size(32.dp).clip(CircleShape).clickable { onCopiedSeen() }, contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.Close, s.dismiss, tint = c.faint, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                    SmartComposer(
                        vm,
                        hint = if (screen == NOTES) s.notesHint else s.composerHint,
                        busy = busy == (if (screen == NOTES) "notes" else "main"),
                        onSend = { t, f -> vm.send(t, scope = if (screen == NOTES) Scope.Notes else Scope.Main, files = f) },
                        prefill = prefill, onPrefillUsed = { prefill = null },
                        onTyping = { typing = it }, slash = screen != NOTES, preview = { vm.previewOf(it) },
                        focusKey = focusKey, micKey = micKey,
                    )
                }
            }
            if (searching) SearchScreen(data, vm, onBack = { searching = false },
                onTask = { openId(it) }, onNote = { openNote = it }, onPerson = { openPerson = it })
            openPerson?.let { id ->
                val p = data.persons.find { it.id == id }
                if (p == null) openPerson = null
                else PersonScreen(p, data, vm, onBack = { openPerson = null }, onOpenTask = { openTask = it }, onOpenNote = { openNote = it })
            }
            openNote?.let { id ->
                val note = data.notes.find { it.id == id }
                // v2: a note became a thought — it opens as a topic.
                if (note == null) { openNote = null; if (data.tasks.any { it.id == id }) openId(id) }
                else NoteEditor(note, data, vm, onBack = { openNote = null }, onOpenTask = { openTask = it })
            }
            // v2: a topic on the whole screen, sliding in from the side; when a task grows into one, its sheet gives way to it.
            val topicId = openTask?.takeIf { id -> data.tasks.find { it.id == id }?.topic == true }
            var lastTopic by remember { mutableStateOf<String?>(null) }
            if (topicId != null) lastTopic = topicId
            AnimatedVisibility(topicId != null,
                enter = androidx.compose.animation.slideInHorizontally(androidx.compose.animation.core.tween(260)) { it / 3 } + fadeIn(androidx.compose.animation.core.tween(220)),
                exit = androidx.compose.animation.slideOutHorizontally(androidx.compose.animation.core.tween(220)) { it / 3 } + fadeOut(androidx.compose.animation.core.tween(180))) {
                data.tasks.find { it.id == (topicId ?: lastTopic) }?.let { t ->
                    TopicScreen(t, data, vm, openTab, openHl, onClose = { openTask = null; openHl = null; openTab = 0 },
                        onOpenPerson = { openTask = null; openPerson = it })
                }
            }
            // A name that isn't a contact was tapped: «Марина · + контакт», quietly, for a few seconds.
            AnimatedVisibility(contactOffer != null && undoBar == null, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(start = 14.dp, end = 14.dp, bottom = 84.dp)) {
                val n = contactOffer
                if (n != null) Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.bubble)
                    .padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(s.notAContact(n), style = Type.small.copy(color = c.onBubble, fontSize = 14.sp), modifier = Modifier.weight(1f))
                    Text(s.addContact, style = Type.small.copy(color = c.link, fontSize = 14.sp),
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { vm.keepContact(n) }.padding(12.dp))
                }
            }
            // «Удалено · Отменить» for a few seconds after a swipe — above whatever screen is open.
            AnimatedVisibility(undoBar != null, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(start = 14.dp, end = 14.dp, bottom = 84.dp)) {
                val ub = undoBar
                if (ub != null) Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.bubble)
                    .padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(ub.text, style = Type.small.copy(color = c.onBubble, fontSize = 14.sp), modifier = Modifier.weight(1f))
                    Text(s.undo, style = Type.small.copy(color = c.link, fontSize = 14.sp),
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { vm.undoBar() }.padding(12.dp))
                }
            }
        }
    }

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

    if (!settings.onboarded) Onboarding(vm, onDone = { vm.updateSettings { it.copy(onboarded = true) }; vm.addIntroOnce(); vm.startSetup() })
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

    openDeal?.let { ref ->
        val qr = ru.anjeib.margin.data.questionRef(ref)
        val topic = qr?.let { (tid, _) -> data.tasks.find { it.id == tid } }
        if (qr == null || topic == null) openDeal = null
        else DealSheet(topic, qr.second, vm, onClose = { openDeal = null },
            onOpenTopic = { openDeal = null; openTask = topic.id; openTab = 1; openHl = qr.second })
    }

    openTask?.let { id ->
        val t = data.tasks.find { it.id == id }
        if (t == null) openTask = null
        // v2: a topic opens on the whole screen (below); a simple task keeps its sheet.
        else if (!t.topic) TaskSheet(
            t, data, vm, onClose = { openTask = null; openTaskDialog = null; openHl = null; openTab = 0 },
            startTab = openTab, startHl = openHl,
            startDialog = openTaskDialog, onStartDialogUsed = { openTaskDialog = null },
            onOpenNote = { openTask = null; openNote = it },
            onOpenPerson = { openTask = null; openPerson = it },
            onOpenTask = { openTask = it },
        )
    }
}

@Composable
private fun TopBar(screen: String, view: String, data: AppData, onMenu: () -> Unit, onTab: (Int) -> Unit, onSearch: () -> Unit, filter: String = "all", onFilter: (String) -> Unit = {}) {
    val s = M.s
    val c = M.c
    var filterMenu by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
        Box(Modifier.align(Alignment.CenterStart).size(44.dp).clip(CircleShape).clickable(onClickLabel = s.a11yMenu, onClick = onMenu)
            .semantics { contentDescription = s.a11yMenu }, contentAlignment = Alignment.Center) {
            MenuGlyph()
        }
        Row(Modifier.align(Alignment.CenterEnd), verticalAlignment = Alignment.CenterVertically) {
            if (screen == TASKS || screen == PEOPLE) Box(
                Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onSearch),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.Search, s.search, tint = M.c.graphite, modifier = Modifier.size(21.dp)) }
        }
        // The tabs start where the content starts, leaving room on the right for the funnel and the search.
        if (screen == TASKS) Box(Modifier.align(Alignment.CenterStart).padding(start = 48.dp)) {
            // No numbers on the tabs: how much is today is said in its summary.
            QuietTabs(listOf(s.today, s.calendarTitle, s.overviewWord), listOf(Views.TODAY, Views.WEEK, Views.ALL).indexOf(view), onTab)
        }
    }
}

@Composable
private fun Drawer(data: AppData, screen: String, view: String, onGo: (String, String?) -> Unit, onFeedback: () -> Unit = {}, onBuildLongPress: () -> Unit = {}) {
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
                style = Type.small.copy(color = c.faint, fontSize = 12.sp, fontFeatureSettings = "tnum"),
                modifier = Modifier.padding(start = 26.dp, top = 10.dp)
                    .combinedClickable(onClick = {}, onLongClick = onBuildLongPress))
        }
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
        Text(label, style = (if (small) Type.body.copy(fontSize = 15.sp) else Type.cardTitle).copy(color = c.ink), modifier = Modifier.weight(1f))
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
