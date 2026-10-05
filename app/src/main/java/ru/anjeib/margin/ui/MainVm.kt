package ru.anjeib.margin.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.anjeib.margin.ai.AiException
import ru.anjeib.margin.ai.AiPlan
import ru.anjeib.margin.ai.Applied
import ru.anjeib.margin.ai.Assistant
import ru.anjeib.margin.ai.ModelInfo
import ru.anjeib.margin.ai.OpenRouter
import ru.anjeib.margin.ai.Processor
import ru.anjeib.margin.ai.Scope
import ru.anjeib.margin.data.AppData
import ru.anjeib.margin.data.Attachment
import ru.anjeib.margin.data.Brief
import ru.anjeib.margin.data.ChatMsg
import ru.anjeib.margin.data.ContactInfo
import ru.anjeib.margin.data.Entry
import ru.anjeib.margin.data.Files
import ru.anjeib.margin.data.Note
import ru.anjeib.margin.data.Person
import ru.anjeib.margin.data.Prefs
import ru.anjeib.margin.data.Settings
import ru.anjeib.margin.data.Store
import ru.anjeib.margin.data.Subtask
import ru.anjeib.margin.data.Task
import ru.anjeib.margin.data.mapTree
import ru.anjeib.margin.data.nextOccurrence
import ru.anjeib.margin.data.postponed
import ru.anjeib.margin.data.shortLabel
import ru.anjeib.margin.data.newId
import ru.anjeib.margin.data.nowStamp
import ru.anjeib.margin.data.text
import ru.anjeib.margin.data.withDone
import ru.anjeib.margin.i18n.L
import ru.anjeib.margin.notify.Reminders
import java.time.LocalDate

/** An assistant result waiting for confirmation: shown as a compact preview above the composer. */
data class Pending(
    val id: String, val plan: AiPlan, val preview: Applied, val auto: Boolean, val files: List<Attachment>,
    /** The user's message this result grew out of — its bubble turns into the result. */
    val userMsgId: String? = null,
    val smart: Boolean = false,
    val source: String = "",
)

/** Result line shown under a scoped composer (inside a task or the notes section). */
data class Notice(val scopeKey: String, val text: String, val undoId: String?)

class MainVm(app: Application) : AndroidViewModel(app) {
    private val store = Store.get(app)
    private val prefs = Prefs.get(app)
    private val ctx get() = getApplication<Application>()

    val data: StateFlow<AppData> = store.data
    val settings: StateFlow<Settings> = prefs.settings

    private val _busy = MutableStateFlow<String?>(null) // scope key in flight
    val busy: StateFlow<String?> = _busy

    /** Note/brief/person operations in flight, by key. */
    private val _working = MutableStateFlow<Set<String>>(emptySet())
    val working: StateFlow<Set<String>> = _working

    /** The latest assistant exchange shown above the main composer; null when dismissed. */
    private val _trayFrom = MutableStateFlow<String?>(null)
    val trayFrom: StateFlow<String?> = _trayFrom

    private val _pending = MutableStateFlow<Pending?>(null)
    val pending: StateFlow<Pending?> = _pending
    private var autoJob: kotlinx.coroutines.Job? = null

    /** Items that just landed in the lists — briefly highlighted so the eye finds them. */
    private val _landed = MutableStateFlow<Set<String>>(emptySet())
    val landed: StateFlow<Set<String>> = _landed

    /** «Перенесено на ср 7 окт · Отменить» after a swipe. */
    private val _undoBar = MutableStateFlow<Notice?>(null)
    val undoBarState: StateFlow<Notice?> = _undoBar

    private val _notice = MutableStateFlow<Notice?>(null)
    val notice: StateFlow<Notice?> = _notice

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast

    /** State before each assistant change, so "Undo" can roll it back. */
    private val snapshots = mutableMapOf<String, AppData>()

    private val _models = MutableStateFlow<List<ModelInfo>>(emptyList())
    val models: StateFlow<List<ModelInfo>> = _models

    private val _events = MutableStateFlow<List<ru.anjeib.margin.data.CalEvent>>(emptyList())
    val events: StateFlow<List<ru.anjeib.margin.data.CalEvent>> = _events

    private val _keyStatus = MutableStateFlow<String?>(null)
    val keyStatus: StateFlow<String?> = _keyStatus

    companion object {
        const val AUTO_CONFIRM_MS = 2600L
        fun key(scope: Scope) = when (scope) { Scope.Main -> "main"; Scope.Notes -> "notes"; is Scope.InTask -> "task:" + scope.taskId }
    }

    init {
        viewModelScope.launch { store.data.collect { Reminders.syncTasks(getApplication(), it.tasks); watchBrief(it) } }
        refreshCalendar()
        Processor.schedule(ctx)
        ru.anjeib.margin.voice.LocalWhisper.refresh(ctx)
        // Back online → let the queue worker run right away.
        runCatching {
            ctx.getSystemService(android.net.ConnectivityManager::class.java)?.registerDefaultNetworkCallback(object : android.net.ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: android.net.Network) { Processor.schedule(ctx) }
            })
        }
    }

    fun toastShown() { _toast.value = null }
    fun dismissTray() { _trayFrom.value = null }
    fun clearNotice() { _notice.value = null }

    // ---------------- assistant ----------------

    /**
     * Sends what the user wrote (plus attachments) to the assistant.
     * Main scope goes through the preview card; inside a task or in notes changes apply at once with an Undo line.
     */
    fun send(text: String, smart: Boolean = false, label: String? = null, scope: Scope = Scope.Main, files: List<Attachment> = emptyList()) {
        val t = text.trim()
        if ((t.isEmpty() && files.isEmpty()) || _busy.value != null) return
        if (_pending.value != null) confirm() // typing on means the preview was fine
        val s = settings.value

        // Files without words need no AI: they just get filed.
        if (t.isEmpty()) { fileAway(scope, files, data.value, null); return }

        var userId: String? = null
        if (scope == Scope.Main) {
            val user = ChatMsg(fromUser = true, text = (label ?: t) + if (files.isNotEmpty()) "  📎${files.size}" else "")
            store.update { it.copy(chat = (it.chat + user).takeLast(60)) }
            _trayFrom.value = user.id
            userId = user.id
        }
        if (!Processor.ready(s)) {
            if (scope == Scope.Main) addAssistant(ChatMsg(fromUser = false, text = L.s.noKey, error = true)) else _toast.value = L.s.noKey
            return
        }
        if (!Processor.online(ctx)) { queue(t, scope, files); return }
        refreshCalendar()
        _busy.value = key(scope)
        viewModelScope.launch {
            try {
                val before = data.value
                // Short memory: the last two exchanges are enough to answer "в пятницу" after a question.
                val history = if (scope == Scope.Main) before.chat.takeLast(5).dropLast(1).map { m ->
                    (if (m.fromUser) "user" else "assistant") to (m.text.ifBlank { m.caption ?: "" } +
                        if (m.options.isNotEmpty()) " (options: ${m.options.joinToString(" / ")})" else "")
                }.filter { it.second.isNotBlank() } else emptyList()
                val recent = before.chat.takeLast(4).flatMap { it.taskIds }.toSet()
                val images = files.filter { it.kind == "photo" }.take(3).mapNotNull { Files.imageDataUrl(ctx, it) }
                // Smart requests (reviews, "what next") need the overview; everything else gets focused context.
                val (plan, cost) = Processor.askPlan(
                    s,
                    Assistant.systemPrompt(before, s, scope, query = if (smart) null else t + " " + history.joinToString(" ") { it.second }, recent = recent),
                    history + ("user" to Assistant.userMessage(t)),
                    smart = smart, complex = Assistant.isComplex(t, scope), images = images,
                )
                prefs.addSpend(cost)
                when {
                    scope != Scope.Main -> applyScoped(plan, scope, files, t)
                    plan.actions.isEmpty() -> { applyPlan(plan, files, source = t); }
                    else -> propose(plan, files, userId, smart, t)
                }
            } catch (e: Exception) {
                val msg = (e as? AiException)?.message ?: e.message ?: e.toString()
                // Lost connection mid-way: keep the words for later instead of losing them.
                if (msg == L.s.errNet || !Processor.online(ctx)) queue(t, scope, files)
                else {
                    if (scope == Scope.Main) addAssistant(ChatMsg(fromUser = false, text = msg, error = true)) else _toast.value = msg
                    if (files.isNotEmpty()) fileAway(scope, files, data.value, null) // never lose what was attached
                }
            } finally {
                _busy.value = null
            }
        }
    }

    /** Offline: park the input in the queue (shown as «Входящие») and sort it once the network is back. */
    private fun queue(text: String, scope: Scope, files: List<Attachment>) {
        Processor.enqueue(ctx, ru.anjeib.margin.data.Queued(text = text, scope = Processor.scopeOf(scope), files = files))
        if (scope == Scope.Main) addAssistant(ChatMsg(fromUser = false, text = L.s.offlineSaved, caption = L.s.offline))
        else _notice.value = Notice(key(scope), L.s.offlineSaved, null)
    }

    fun dropQueued(id: String) = store.update { d -> d.copy(queue = d.queue.filterNot { it.id == id }) }

    /** Reads the phone calendar into the AI context (only when the user turned it on and allowed it). */
    fun refreshCalendar() {
        val on = settings.value.calendar && ru.anjeib.margin.data.PhoneCalendar.allowed(ctx)
        val ev = if (on) ru.anjeib.margin.data.PhoneCalendar.events(ctx) else emptyList()
        _events.value = ev
        Assistant.calendar = ru.anjeib.margin.data.PhoneCalendar.contextLines(ev)
    }

    private fun propose(plan: AiPlan, files: List<Attachment>, userMsgId: String? = null, smart: Boolean = false, source: String = "") {
        val p = Pending(newId(), plan, Assistant.apply(data.value, plan, source), settings.value.autoConfirm, files, userMsgId, smart, source)
        _pending.value = p
        autoJob?.cancel()
        if (p.auto) autoJob = viewModelScope.launch {
            kotlinx.coroutines.delay(AUTO_CONFIRM_MS)
            if (_pending.value?.id == p.id) confirm()
        }
    }

    /** Applies the pending plan; returns ids of the tasks it touched (for "Edit"). */
    fun confirm(): List<String> {
        val p = _pending.value ?: return emptyList()
        autoJob?.cancel()
        _pending.value = null
        val before = data.value
        val ids = applyPlan(p.plan, p.files, source = p.source)
        // Nothing left to say → the bubble leaves and the result settles into its list.
        val talk = p.plan.question != null || (p.smart && !p.plan.reply.isNullOrBlank())
        if (!talk) {
            _trayFrom.value = null
            val newNotes = data.value.notes.filter { n -> before.notes.none { it.id == n.id && it.entries == n.entries } }.map { it.id }
            land(ids + newNotes)
        }
        return ids
    }

    private fun land(ids: List<String>) {
        if (ids.isEmpty()) return
        _landed.value = ids.toSet()
        viewModelScope.launch { kotlinx.coroutines.delay(1800); if (_landed.value == ids.toSet()) _landed.value = emptySet() }
    }

    /** «в отдельную заметку»: what the AI appended to an existing note becomes a note of its own. */
    fun pendingAsNewNote() {
        val p = _pending.value ?: return
        autoJob?.cancel()
        val actions = p.plan.actions.map { a ->
            val type = (a["type"] as? kotlinx.serialization.json.JsonPrimitive)?.content
            if (type == "append_note" || type == "create_note") kotlinx.serialization.json.JsonObject(
                a - "note_id" + ("type" to kotlinx.serialization.json.JsonPrimitive("create_note")) + ("force_new" to kotlinx.serialization.json.JsonPrimitive("true")),
            ) else a
        }
        val plan = p.plan.copy(actions = actions)
        _pending.value = p.copy(plan = plan, preview = Assistant.apply(data.value, plan, p.source), auto = false)
    }

    /** Cancel before anything was saved: the bubble simply goes away. */
    fun rejectPending() {
        autoJob?.cancel()
        val p = _pending.value
        _pending.value = null
        _trayFrom.value = null
        p?.files?.takeIf { it.isNotEmpty() }?.let { fileAway(Scope.Main, it, data.value, null) }
    }

    /** Stops the auto-confirm countdown (the user started looking at the preview). */
    fun holdPending() {
        autoJob?.cancel()
        _pending.value = _pending.value?.copy(auto = false)
    }

    private fun applyPlan(plan: AiPlan, files: List<Attachment> = emptyList(), silentUser: Boolean = false, source: String = ""): List<String> {
        val current = data.value // the user may have edited something while we waited
        val applied = Assistant.apply(current, plan, source)
        val withFiles = if (files.isNotEmpty()) attachTo(applied, Scope.Main, files, current) else applied.data
        val msg = ChatMsg(
            fromUser = false,
            text = plan.reply ?: plan.question?.text ?: "",
            caption = applied.caption,
            taskIds = applied.touchedTasks,
            options = plan.question?.options ?: emptyList(),
        ).let { if (plan.reply != null && plan.question != null) it.copy(text = plan.reply + "\n" + plan.question.text) else it }
        if (applied.caption != null) snapshots[msg.id] = current
        store.replace(withFiles.copy(chat = (withFiles.chat + msg).takeLast(60)))
        if (silentUser) _trayFrom.value = msg.id
        return applied.touchedTasks
    }

    private fun applyScoped(plan: AiPlan, scope: Scope, files: List<Attachment>, source: String = "") {
        val current = data.value
        val applied = Assistant.apply(current, plan, source)
        val next = attachTo(applied, scope, files, current)
        val undoId = newId()
        snapshots[undoId] = current
        store.replace(next)
        val text = listOfNotNull(applied.caption, plan.reply, plan.question?.text).joinToString(" · ").ifBlank { L.s.saved }
        _notice.value = Notice(key(scope), text, undoId)
    }

    /** Files sent without text (or when AI failed): task scope → task log, otherwise the Inbox note. */
    private fun fileAway(scope: Scope, files: List<Attachment>, d: AppData, text: String?) {
        val e = Entry(text = text.orEmpty(), attachments = files)
        val next = when (scope) {
            is Scope.InTask -> d.copy(tasks = d.tasks.map { if (it.id == scope.taskId) it.copy(entries = it.entries + e) else it })
            else -> inbox(d, e)
        }
        store.replace(next)
        if (scope !is Scope.InTask) _toast.value = L.s.savedToInbox
    }

    private fun inbox(d: AppData, e: Entry) = Processor.inbox(d, e)
    private fun attachTo(applied: Applied, scope: Scope, files: List<Attachment>, before: AppData) = Processor.attachTo(applied, scope, files, before)

    private fun addAssistant(m: ChatMsg) {
        store.update { it.copy(chat = (it.chat + m).takeLast(60)) }
    }

    fun undo(id: String) {
        val snap = snapshots.remove(id) ?: return
        store.update { d ->
            snap.copy(
                chat = d.chat.map { if (it.id == id) it.copy(caption = L.s.undone, taskIds = emptyList(), options = emptyList()) else it },
                brief = d.brief,
            )
        }
        if (_notice.value?.undoId == id) _notice.value = _notice.value?.copy(text = L.s.undone, undoId = null)
    }

    fun canUndo(id: String) = snapshots.containsKey(id)

    fun makeBrief(force: Boolean = false) {
        val s = settings.value
        val today = LocalDate.now().toString()
        if (!Processor.ready(s) || "brief" in _working.value) return
        refreshCalendar()
        val prev = data.value.brief?.takeIf { it.date == today && it.lang == L.lang.name }
        if (!force && prev?.headline != null) return
        _working.value += "brief"
        viewModelScope.launch {
            try {
                val r = ru.anjeib.margin.ai.Llm.complete(s, Assistant.briefPrompt(data.value, s), listOf("user" to "brief"), smart = true)
                prefs.addSpend(r.rub)
                val o = Assistant.parseObject(r.content)
                val text = Assistant.field(o, "text") ?: r.content.trim()
                val main = Assistant.field(o, "main_task_id")?.takeIf { id -> data.value.tasks.any { it.id == id } }
                store.update {
                    it.copy(brief = Brief(today, text, Assistant.field(o, "focus"), L.lang.name, Assistant.field(o, "headline"),
                        mainTaskId = main, at = java.time.LocalTime.now().toString().take(5), fingerprint = briefFingerprint(it),
                        revisions = (prev?.revisions ?: -1) + 1))
                }
            } catch (e: Exception) {
                if (force) _toast.value = e.message
            } finally {
                _working.value -= "brief"
            }
        }
    }

    /** Today's open tasks as a set — compared with the one the brief was built on. */
    fun briefFingerprint(d: AppData): String = d.tasks.filter { isToday(it) || isRunning(it) }.map { it.id }.sorted().joinToString(",")

    /**
     * Keeps «Главное» honest: when the task it names is done, moved off today or deleted, the brief is rebuilt
     * (at most 4 times a day — each rebuild costs a request).
     */
    private fun watchBrief(d: AppData) {
        val b = d.brief?.takeIf { it.date == LocalDate.now().toString() && it.lang == L.lang.name } ?: return
        val main = b.mainTaskId ?: return
        val t = d.tasks.find { it.id == main }
        val stillOn = t != null && !t.done && (isToday(t) || isRunning(t))
        if (!stillOn && b.revisions < 4 && d.tasks.any { isToday(it) || isRunning(it) }) {
            briefJob?.cancel()
            briefJob = viewModelScope.launch { kotlinx.coroutines.delay(2500); makeBrief(force = true) }
        }
    }
    private var briefJob: kotlinx.coroutines.Job? = null

    fun collapseBrief() { if (settings.value.briefCollapsed != LocalDate.now().toString()) prefs.update { it.copy(briefCollapsed = LocalDate.now().toString()) } }
    fun expandBrief() = prefs.update { it.copy(briefCollapsed = "") }
    fun hideBrief() = prefs.update { it.copy(briefHidden = LocalDate.now().toString()) }

    // ---------------- dictation ----------------

    /**
     * Turns a WAV recording into text: the AI voice model when online, the on-device Whisper model
     * when offline (or always, if the user prefers). Calls back on the main thread.
     */
    fun transcribe(audio: Attachment, onText: (String) -> Unit) {
        val s = settings.value
        val local = ru.anjeib.margin.voice.LocalWhisper
        val useLocal = local.ready(ctx) && (s.whisperAlways || !Processor.online(ctx) || !Processor.ready(s))
        if (!useLocal && (!Processor.online(ctx) || !Processor.ready(s))) {
            _toast.value = if (local.supported) L.s.whisperOfferOffline else L.s.errNet
            return
        }
        _working.value += "voice"
        viewModelScope.launch {
            try {
                val d = data.value
                val hints = (d.persons.map { it.name } + d.projects).distinct().take(40)
                val text = if (useLocal) {
                    val f = Files.file(ctx, audio) ?: return@launch
                    local.transcribe(ctx, f, if (L.lang == ru.anjeib.margin.i18n.Lang.Ru) "ru" else "en", hints)
                } else {
                    val bytes = withContext(Dispatchers.IO) { Files.file(ctx, audio)?.readBytes() } ?: return@launch
                    val r = ru.anjeib.margin.ai.Llm.transcribe(s, bytes, hints)
                    prefs.addSpend(r.rub)
                    r.content
                }
                if (text.isNotBlank()) onText(text)
            } catch (e: Exception) {
                _toast.value = e.message
            } finally {
                _working.value -= "voice"
            }
        }
    }

    fun downloadWhisper(m: ru.anjeib.margin.voice.LocalWhisper.Model) {
        viewModelScope.launch { ru.anjeib.margin.voice.LocalWhisper.download(ctx, m) }
    }

    // ---------------- backup ----------------

    fun exportTo(uri: android.net.Uri) = viewModelScope.launch {
        runCatching { ctx.contentResolver.openOutputStream(uri, "wt")?.use { ru.anjeib.margin.data.Backup.write(ctx, it) } }
            .onSuccess { _toast.value = L.s.backupSaved }.onFailure { _toast.value = it.message }
    }

    fun restoreFrom(uri: android.net.Uri) = viewModelScope.launch {
        _toast.value = if (ru.anjeib.margin.data.Backup.restore(ctx, uri)) L.s.restored else L.s.restoreFailed
    }

    fun setBackupTarget(uri: android.net.Uri) {
        ru.anjeib.margin.data.Backup.persist(ctx, uri)
        backupNow()
    }

    fun backupNow() = viewModelScope.launch {
        val err = ru.anjeib.margin.data.Backup.toSavedTarget(ctx)
        _toast.value = if (err == null) L.s.backupSaved else L.s.backupFailed + ": " + err
    }

    fun stopAutoBackup() {
        prefs.update { it.copy(backupUri = "") }
        ru.anjeib.margin.data.Backup.schedule(ctx)
    }

    // ---------------- tasks ----------------

    private fun updateTask(id: String, f: (Task) -> Task) =
        store.update { d -> d.copy(tasks = d.tasks.map { if (it.id == id) f(it) else it }) }

    fun toggleTask(id: String) = store.update { d ->
        val t = d.tasks.find { it.id == id } ?: return@update d
        if (t.done) d.copy(tasks = d.tasks.map { if (it.id == id) it.copy(done = false, doneAt = null) else it })
        else {
            // A repeating task leaves its next occurrence behind.
            val next = t.nextOccurrence()
            d.copy(tasks = d.tasks.map { if (it.id == id) it.copy(done = true, doneAt = LocalDate.now().toString()) else it } + listOfNotNull(next))
        }
    }

    fun toggleSubtask(taskId: String, subId: String) = updateTask(taskId) { t ->
        t.copy(subtasks = t.subtasks.mapTree(subId) { it.withDone(!it.done) })
    }

    fun addSubtask(taskId: String, title: String, parent: String? = null) {
        val st = Subtask(title = title.trim()).takeIf { it.title.isNotEmpty() } ?: return
        updateTask(taskId) { t ->
            if (parent == null) t.copy(subtasks = t.subtasks + st, done = false)
            else t.copy(subtasks = t.subtasks.mapTree(parent) { it.copy(children = it.children + st, done = false) })
        }
    }

    fun renameSubtask(taskId: String, subId: String, title: String) = updateTask(taskId) { t ->
        t.copy(subtasks = t.subtasks.mapTree(subId) { if (title.isBlank()) null else it.copy(title = title.trim()) })
    }

    fun deleteSubtask(taskId: String, subId: String) = updateTask(taskId) { t ->
        t.copy(subtasks = t.subtasks.mapTree(subId) { null })
    }

    fun saveTask(t: Task) {
        updateTask(t.id) { Assistant.withAutoReminder(t) }
        store.update { d ->
            val newPeople = (t.people + listOfNotNull(t.waitingFor, t.promisedTo)).filter { p -> p != L.s.me && d.person(p) == null }.distinct().map { Person(name = it) }
            val newProject = t.project?.takeIf { p -> d.projects.none { it.equals(p, true) } }
            d.copy(persons = d.persons + newPeople, projects = d.projects + listOfNotNull(newProject))
        }
    }

    fun deleteTask(id: String) = store.update { d -> d.copy(tasks = d.tasks.filterNot { it.id == id }) }

    fun editTaskEntry(taskId: String, entryId: String, text: String) = updateTask(taskId) { t ->
        t.copy(entries = t.entries.mapNotNull { if (it.id != entryId) it else if (text.isBlank() && it.attachments.isEmpty()) null else it.copy(text = text.trim()) })
    }

    fun deleteTaskEntry(taskId: String, entryId: String) = updateTask(taskId) { t ->
        t.entries.find { it.id == entryId }?.attachments?.forEach { Files.delete(ctx, it) }
        t.copy(entries = t.entries.filterNot { it.id == entryId })
    }

    /** Moves a task [days] later; overdue and undated tasks count from today. Ranges keep their length. */
    fun postpone(id: String, days: Long) {
        val before = data.value
        val t = before.tasks.find { it.id == id } ?: return
        val moved = postponed(t, days)
        updateTask(id) { moved }
        val target = moved.dueDate ?: moved.startDate
        offerUndo(before, L.s.movedTo(target?.shortLabel()?.lowercase().orEmpty()))
    }

    /** Swipe-right: done, with a few seconds to take it back. */
    fun completeWithUndo(id: String) {
        val before = data.value
        val wasDone = before.tasks.find { it.id == id }?.done ?: return
        toggleTask(id)
        offerUndo(before, if (wasDone) L.s.reopened else L.s.markedDone)
    }

    private fun offerUndo(before: AppData, text: String) {
        val id = newId()
        snapshots[id] = before
        _undoBar.value = Notice("bar", text, id)
        undoJob?.cancel()
        undoJob = viewModelScope.launch { kotlinx.coroutines.delay(6000); if (_undoBar.value?.undoId == id) _undoBar.value = null }
    }

    fun undoBar() {
        val n = _undoBar.value ?: return
        _undoBar.value = null
        val snap = n.undoId?.let { snapshots.remove(it) } ?: return
        store.update { d -> snap.copy(chat = d.chat, brief = d.brief) }
    }
    private var undoJob: kotlinx.coroutines.Job? = null

    fun moveOverdue() {
        val before = data.value
        val today = LocalDate.now()
        val n = data.value.tasks.count { !it.done && it.dueDate?.isBefore(today) == true }
        if (n == 0) { _toast.value = L.s.nothingOverdue; return }
        store.update { d ->
            d.copy(tasks = d.tasks.map { t ->
                if (!t.done && t.dueDate?.isBefore(today) == true)
                    Assistant.withAutoReminder(t.copy(due = today.toString(), start = null, remindAt = null, postponed = t.postponed + 1))
                else t
            })
        }
        offerUndo(before, L.s.movedOverdue(n))
    }

    // ---------------- notes ----------------

    fun newEmptyNote(): String {
        val n = Note(title = "")
        store.update { it.copy(notes = listOf(n) + it.notes) }
        return n.id
    }

    private fun updateNote(id: String, f: (Note) -> Note) =
        store.update { d -> d.copy(notes = d.notes.map { if (it.id == id) f(it) else it }) }

    fun renameNote(id: String, title: String) = updateNote(id) { if (it.title == title) it else it.copy(title = title) }
    fun setNoteProject(id: String, project: String?) = store.update { d ->
        val p = project?.trim()?.removePrefix("#")?.lowercase()?.ifBlank { null }
        d.copy(notes = d.notes.map { if (it.id == id) it.copy(project = p) else it },
            projects = d.projects + listOfNotNull(p?.takeIf { x -> d.projects.none { it == x } }))
    }

    /** Adds a dated entry to a note directly (inside the note the user is the author, no AI needed). */
    fun appendToNote(id: String, text: String, files: List<Attachment> = emptyList()) {
        if (text.isBlank() && files.isEmpty()) return
        store.update { d ->
            val n = d.notes.find { it.id == id } ?: return@update d
            val upd = n.copy(entries = n.entries + Entry(text = text.trim(), attachments = files), updatedAt = nowStamp())
            d.copy(notes = listOf(upd) + d.notes.filterNot { it.id == id })
        }
    }

    fun editNoteEntry(noteId: String, entryId: String, text: String) = updateNote(noteId) { n ->
        n.copy(entries = n.entries.mapNotNull { if (it.id != entryId) it else if (text.isBlank() && it.attachments.isEmpty()) null else it.copy(text = text.trim()) })
    }

    fun deleteNoteEntry(noteId: String, entryId: String) = updateNote(noteId) { n ->
        n.entries.find { it.id == entryId }?.attachments?.forEach { Files.delete(ctx, it) }
        n.copy(entries = n.entries.filterNot { it.id == entryId })
    }

    fun deleteNote(id: String) = store.update { d ->
        d.notes.find { it.id == id }?.entries?.flatMap { it.attachments }?.forEach { Files.delete(ctx, it) }
        d.copy(notes = d.notes.filterNot { it.id == id }, tasks = d.tasks.map { if (it.noteId == id) it.copy(noteId = null) else it })
    }

    /** Removes notes left completely empty (e.g. "new note" opened and closed). */
    fun dropEmptyNote(id: String) = store.update { d ->
        d.copy(notes = d.notes.filterNot { it.id == id && it.title.isBlank() && it.entries.isEmpty() })
    }

    fun noteToTasks(id: String) {
        val s = settings.value
        val note = data.value.notes.find { it.id == id } ?: return
        if (!Processor.ready(s)) { _toast.value = L.s.noKey; return }
        _working.value += "tasks:$id"
        viewModelScope.launch {
            try {
                val prompt = "Extract every actionable task from this note (note_id=${note.id}); set note_id=${note.id} on each create_task. " +
                    "Include dates, people and subtasks when stated. Skip tasks that already exist with this note_id. Do not create notes.\n\n" +
                    note.title + "\n" + note.text
                val (plan, cost) = Processor.askPlan(s,
                    Assistant.systemPrompt(data.value, s.copy(askWhenUnclear = false), query = note.title + " " + note.text), listOf("user" to prompt), smart = true)
                prefs.addSpend(cost)
                val current = data.value
                val applied = Assistant.apply(current, plan.copy(question = null), note.title)
                val created = applied.touchedTasks.filter { tid -> current.tasks.none { it.id == tid } }
                snapshots[id] = current
                store.replace(applied.data.copy(tasks = applied.data.tasks.map { if (it.id in created) it.copy(noteId = id) else it }))
                _toast.value = applied.caption ?: L.s.nothingFound
            } catch (e: Exception) {
                _toast.value = e.message
            } finally {
                _working.value -= "tasks:$id"
            }
        }
    }

    fun summarize(id: String) {
        val s = settings.value
        val note = data.value.notes.find { it.id == id } ?: return
        if (!Processor.ready(s)) { _toast.value = L.s.noKey; return }
        _working.value += "sum:$id"
        viewModelScope.launch {
            try {
                val r = ru.anjeib.margin.ai.Llm.complete(s, Assistant.summaryPrompt(), listOf("user" to note.title + "\n" + note.text), smart = true)
                prefs.addSpend(r.rub)
                val text = Assistant.field(Assistant.parseObject(r.content), "summary") ?: r.content.trim()
                updateNote(id) { it.copy(aiSummary = text) }
            } catch (e: Exception) {
                _toast.value = e.message
            } finally {
                _working.value -= "sum:$id"
            }
        }
    }

    // ---------------- people ----------------

    private fun updatePerson(id: String, f: (Person) -> Person) =
        store.update { d -> d.copy(persons = d.persons.map { if (it.id == id) f(it) else it }) }

    fun editPerson(id: String, about: String?, phone: String?, email: String?) = updatePerson(id) {
        it.copy(about = about?.trim()?.ifBlank { null }, aboutFromUser = !about.isNullOrBlank(), phone = phone?.trim()?.ifBlank { null }, email = email?.trim()?.ifBlank { null })
    }

    /** Renames a person everywhere their name is used. */
    fun renamePerson(id: String, newName: String) {
        val n = newName.trim().ifEmpty { return }
        store.update { d ->
            val p = d.persons.find { it.id == id } ?: return@update d
            d.copy(persons = d.persons.map { if (it.id == id) it.copy(name = n, aliases = (it.aliases + p.name).distinct()) else it }, tasks = d.tasks.map { it.renamed(p, n) })
        }
    }

    /** Merges [fromId] into [intoId]: one person, all mentions point to the survivor. */
    fun mergePersons(fromId: String, intoId: String) = store.update { d ->
        val from = d.persons.find { it.id == fromId } ?: return@update d
        val into = d.persons.find { it.id == intoId } ?: return@update d
        val merged = into.copy(
            aliases = (into.aliases + from.name + from.aliases).distinct().filterNot { it.equals(into.name, true) },
            about = into.about ?: from.about, phone = into.phone ?: from.phone, email = into.email ?: from.email,
        )
        d.copy(persons = d.persons.filterNot { it.id == fromId }.map { if (it.id == intoId) merged else it }, tasks = d.tasks.map { it.renamed(from, into.name) })
    }

    fun deletePerson(id: String) = store.update { d -> d.copy(persons = d.persons.filterNot { it.id == id }) }

    private fun Task.renamed(p: Person, n: String): Task {
        fun fix(x: String?) = if (x != null && p.matches(x)) n else x
        fun subs(l: List<Subtask>): List<Subtask> = l.map { it.copy(assignee = fix(it.assignee), children = subs(it.children)) }
        return copy(people = people.map { fix(it)!! }.distinct(), waitingFor = fix(waitingFor), promisedTo = fix(promisedTo), subtasks = subs(subtasks))
    }

    /** «Свежая сводка» about a person: who they are + what's going on now. */
    fun refreshPerson(id: String) {
        val s = settings.value
        val p = data.value.persons.find { it.id == id } ?: return
        if (!Processor.ready(s) || "person:$id" in _working.value) return
        _working.value += "person:$id"
        viewModelScope.launch {
            try {
                val r = ru.anjeib.margin.ai.Llm.complete(s, Assistant.personPrompt(data.value, p), listOf("user" to "profile"), smart = true)
                prefs.addSpend(r.rub)
                val o = Assistant.parseObject(r.content)
                updatePerson(id) {
                    // The user's own words about a person stay; an earlier AI guess may be replaced or dropped.
                    it.copy(about = if (it.aboutFromUser) it.about else Assistant.field(o, "about")?.trim()?.ifBlank { null },
                        summary = Assistant.field(o, "summary")?.replaceFirstChar { c -> c.uppercase() } ?: it.summary,
                        summaryAt = nowStamp(), updatedAt = nowStamp())
                }
            } catch (e: Exception) {
                _toast.value = e.message
            } finally {
                _working.value -= "person:$id"
            }
        }
    }

    /** «Сводка недели» for a project page. */
    fun refreshProject(project: String) {
        val s = settings.value
        if (!Processor.ready(s) || "project:$project" in _working.value) return
        _working.value += "project:$project"
        viewModelScope.launch {
            try {
                val r = ru.anjeib.margin.ai.Llm.complete(s, Assistant.projectPrompt(data.value, project), listOf("user" to "summary"), smart = true)
                prefs.addSpend(r.rub)
                val text = Assistant.field(Assistant.parseObject(r.content), "summary") ?: return@launch
                store.update { it.copy(projectSummaries = it.projectSummaries + (project to ru.anjeib.margin.data.ProjectSummary(text, nowStamp()))) }
            } catch (e: Exception) {
                _toast.value = e.message
            } finally {
                _working.value -= "project:$project"
            }
        }
    }

    /** «В задачу» on an idea: exactly one task, linked back to the idea it came from. */
    fun ideaToTask(noteId: String, entryId: String) {
        val s = settings.value
        val e = data.value.notes.find { it.id == noteId }?.entries?.find { it.id == entryId } ?: return
        if (!Processor.ready(s)) { _toast.value = L.s.noKey; return }
        _working.value += "idea:$entryId"
        viewModelScope.launch {
            try {
                val (plan, cost) = Processor.askPlan(s, Assistant.systemPrompt(data.value, s.copy(askWhenUnclear = false), query = e.text),
                    listOf("user" to Assistant.ideaPrompt(noteId, e.text)))
                prefs.addSpend(cost)
                val current = data.value
                val applied = Assistant.apply(current, plan.copy(question = null), e.text)
                val created = applied.touchedTasks.filter { tid -> current.tasks.none { it.id == tid } }
                store.replace(applied.data.copy(tasks = applied.data.tasks.map {
                    if (it.id in created) it.copy(noteId = noteId, sourceEntryId = entryId, source = e.text.take(240)) else it
                }))
                land(created)
            } catch (ex: Exception) {
                _toast.value = ex.message
            } finally {
                _working.value -= "idea:$entryId"
            }
        }
    }

    /** A picked phone contact: remembered on the person and returned as an attachment. */
    fun contactAttachment(c: ContactInfo): Attachment {
        store.update { d ->
            val first = c.name.substringBefore(' ')
            val existing = d.person(c.name) ?: d.person(first)
            if (existing != null) d.copy(persons = d.persons.map {
                if (it.id == existing.id) it.copy(phone = it.phone ?: c.phone, email = it.email ?: c.email, aliases = (it.aliases + c.name).distinct().filterNot { a -> a.equals(it.name, true) }) else it
            })
            else d.copy(persons = d.persons + Person(name = c.name, phone = c.phone, email = c.email))
        }
        return Attachment(kind = "contact", name = c.name, contact = c)
    }

    // ---------------- settings ----------------

    fun updateSettings(f: (Settings) -> Settings) {
        prefs.update(f)
        Reminders.scheduleDaily(getApplication())
    }

    fun checkKey() {
        _keyStatus.value = L.s.loading
        viewModelScope.launch {
            _keyStatus.value = try { ru.anjeib.margin.ai.Llm.checkKey(settings.value) } catch (e: Exception) { e.message }
        }
    }

    fun loadModels() {
        if (_models.value.isNotEmpty()) return
        viewModelScope.launch { _models.value = runCatching { OpenRouter.models() }.getOrDefault(emptyList()) }
    }
}
