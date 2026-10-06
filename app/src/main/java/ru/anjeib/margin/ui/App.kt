package ru.anjeib.margin.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.outlined.PeopleOutline
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.TaskAlt
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
private const val INBOX = "inbox"

@Composable
fun App(vm: MainVm, shared: String?, onSharedConsumed: () -> Unit) {
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

    var screen by rememberSaveable { mutableStateOf(TASKS) }
    var view by rememberSaveable { mutableStateOf(Views.TODAY) }
    var openTask by rememberSaveable { mutableStateOf<String?>(null) }
    var openTaskDialog by rememberSaveable { mutableStateOf<String?>(null) }
    var openNote by rememberSaveable { mutableStateOf<String?>(null) }
    var openPerson by rememberSaveable { mutableStateOf<String?>(null) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var typing by rememberSaveable { mutableStateOf(false) }
    var prefill by rememberSaveable { mutableStateOf<String?>(null) }
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    LaunchedEffect(shared) { if (!shared.isNullOrBlank()) { prefill = shared; screen = TASKS; onSharedConsumed() } }
    // The assistant tray is for the latest exchange — moving elsewhere tucks it away.
    LaunchedEffect(view, screen) { if (pending == null && busy == null) vm.dismissTray() }
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
            Drawer(data, screen, view, onGo = ::go)
        },
    ) {
        Box(Modifier.fillMaxSize().background(c.bg)) {
            Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
                TopBar(screen, view, data, onMenu = { scope.launch { drawer.open() } }, onTab = { i -> view = listOf(Views.TODAY, Views.WEEK, Views.ALL)[i] },
                    onSearch = { searching = true })
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    val pad = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp)
                    Crossfade(screen, label = "screen") { sc ->
                        when (sc) {
                            TASKS -> Crossfade(view, label = "view") { v ->
                                TaskList(
                                    v, data, settings, working, vm, events,
                                    briefQuiet = typing || tray != null || pending != null || busy == "main",
                                    onOpen = { openTask = it }, onView = { view = it }, onOpenNote = { openNote = it },
                                    onOpenNotes = { screen = NOTES }, onOpenInbox = { screen = INBOX }, contentPadding = pad,
                                )
                            }
                            NOTES -> NotesList(data, onOpen = { openNote = it }, contentPadding = pad)
                            PEOPLE -> PeopleList(data, onOpen = { openPerson = it }, contentPadding = pad)
                            SETTINGS -> SettingsScreen(settings, vm, pad)
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
                            onOpenTask = { openTask = it },
                            onToggleTask = vm::toggleTask, onToggleSub = vm::toggleSubtask,
                            onOption = { vm.send(it) },
                            onOpenSettings = { screen = SETTINGS },
                            onDismiss = vm::dismissTray,
                            onConfirm = { vm.confirm() },
                            onEdit = {
                                // Edit opens what was made: the task, or the note when it was a note.
                                val ids = vm.confirm()
                                if (ids.isNotEmpty()) openTask = ids.first()
                                else vm.landed.value.firstOrNull { id -> vm.data.value.notes.any { it.id == id } }?.let { openNote = it }
                            },
                            onReject = vm::rejectPending,
                            onHold = vm::holdPending,
                            onSeparate = vm::pendingAsNewNote,
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
                    // «Перенесено: ср 7 окт · Отменить» for a few seconds after a swipe.
                    AnimatedVisibility(undoBar != null, enter = fadeIn(), exit = fadeOut()) {
                        val ub = undoBar
                        if (ub != null) Row(Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(14.dp)).background(c.bubble)
                            .padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(ub.text, style = Type.small.copy(color = c.onBubble, fontSize = 14.sp), modifier = Modifier.weight(1f))
                            Text(s.undo, style = Type.small.copy(color = c.accent, fontSize = 14.sp),
                                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { vm.undoBar() }.padding(12.dp))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    SmartComposer(
                        vm,
                        hint = if (screen == NOTES) s.notesHint else s.composerHint,
                        busy = busy == (if (screen == NOTES) "notes" else "main"),
                        onSend = { t, f -> vm.send(t, scope = if (screen == NOTES) Scope.Notes else Scope.Main, files = f) },
                        keepAudio = screen == NOTES,
                        prefill = prefill, onPrefillUsed = { prefill = null },
                        onTyping = { typing = it },
                    )
                }
            }
            if (searching) SearchScreen(data, vm, onBack = { searching = false },
                onTask = { openTask = it }, onNote = { openNote = it }, onPerson = { openPerson = it })
            openPerson?.let { id ->
                val p = data.persons.find { it.id == id }
                if (p == null) openPerson = null
                else PersonScreen(p, data, vm, onBack = { openPerson = null }, onOpenTask = { openTask = it }, onOpenNote = { openNote = it })
            }
            openNote?.let { id ->
                val note = data.notes.find { it.id == id }
                if (note == null) openNote = null
                else NoteEditor(note, data, vm, onBack = { openNote = null }, onOpenTask = { openTask = it })
            }
        }
    }

    if (!settings.onboarded) Onboarding(vm, onDone = { vm.updateSettings { it.copy(onboarded = true) }; vm.startSetup() })

    openTask?.let { id ->
        val t = data.tasks.find { it.id == id }
        if (t == null) openTask = null
        else TaskSheet(
            t, data, vm, onClose = { openTask = null; openTaskDialog = null },
            startDialog = openTaskDialog, onStartDialogUsed = { openTaskDialog = null },
            onOpenNote = { openTask = null; openNote = it },
            onOpenPerson = { openTask = null; openPerson = it },
            onOpenTask = { openTask = it },
        )
    }
}

@Composable
private fun TopBar(screen: String, view: String, data: AppData, onMenu: () -> Unit, onTab: (Int) -> Unit, onSearch: () -> Unit) {
    val s = M.s
    Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
        Box(Modifier.align(Alignment.CenterStart).size(44.dp).clip(CircleShape).clickable(onClickLabel = s.a11yMenu, onClick = onMenu)
            .semantics { contentDescription = s.a11yMenu }, contentAlignment = Alignment.Center) {
            MenuGlyph()
        }
        if (screen == TASKS || screen == PEOPLE) Box(
            Modifier.align(Alignment.CenterEnd).size(44.dp).clip(CircleShape).clickable(onClick = onSearch),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Outlined.Search, s.search, tint = M.c.graphite, modifier = Modifier.size(21.dp)) }
        if (screen == TASKS) Box(Modifier.align(Alignment.Center)) {
            val (t, w, a) = tabCounts(data)
            QuietTabs(
                listOf(s.today, s.thisWeek, s.all), listOf(Views.TODAY, Views.WEEK, Views.ALL).indexOf(view), onTab,
                counts = listOf(t, w, a), alertFirst = data.tasks.any { it.isOverdue() },
            )
        }
    }
}

@Composable
private fun Drawer(data: AppData, screen: String, view: String, onGo: (String, String?) -> Unit) {
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
            NavRow(Icons.Outlined.TaskAlt, s.tasks, "", screen == TASKS && !view.startsWith("p:")) { onGo(TASKS, Views.TODAY) }
            NavRow(Icons.Outlined.Description, s.notes, "", screen == NOTES) { onGo(NOTES, null) }
            NavRow(Icons.Outlined.PeopleOutline, s.peopleTitle, "", screen == PEOPLE) { onGo(PEOPLE, null) }

            val projects = data.projects.filter { p -> data.tasks.any { it.project == p } || data.notes.any { it.project == p } }
            if (projects.isNotEmpty()) {
                Hairline(Modifier.padding(horizontal = 26.dp, vertical = 14.dp))
                SectionLabel(s.projects, Modifier.padding(start = 30.dp, bottom = 6.dp))
                projects.forEach { p ->
                    val n = data.tasks.count { !it.done && it.project == p }
                    NavRow(null, projectLabel(p), if (n > 0) n.toString() else "", screen == TASKS && view == Views.project(p)) { onGo(TASKS, Views.project(p)) }
                }
            }

          }
            // Settings at the bottom, out of the way.
            Spacer(Modifier.height(8.dp))
            NavRow(Icons.Outlined.Settings, s.settings, "", screen == SETTINGS, small = true) { onGo(SETTINGS, null) }
        }
    }
}

@Composable
private fun NavRow(icon: ImageVector?, label: String, count: String, active: Boolean, small: Boolean = false, onClick: () -> Unit) {
    val c = M.c
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp).clip(RoundedCornerShape(14.dp))
            .background(if (active) c.accentSoft else Color.Transparent).clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = if (small) 10.dp else 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(icon, null, tint = if (active) c.accent else c.graphite, modifier = Modifier.size(if (small) 18.dp else 21.dp))
        else Box(Modifier.padding(horizontal = 6.dp).size(7.dp).clip(CircleShape).background(c.accent.copy(alpha = 0.7f)))
        Spacer(Modifier.width(16.dp))
        Text(label, style = (if (small) Type.body.copy(fontSize = 15.sp) else Type.cardTitle).copy(color = c.ink), modifier = Modifier.weight(1f))
        Text(count, style = Type.small.copy(color = c.graphite))
    }
}
