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
import ru.anjeib.margin.ai.Command
import ru.anjeib.margin.ai.Commands
import ru.anjeib.margin.ai.Applied
import ru.anjeib.margin.ai.Assistant
import ru.anjeib.margin.ai.ModelInfo
import ru.anjeib.margin.ai.OpenRouter
import ru.anjeib.margin.ai.Processor
import ru.anjeib.margin.ai.Scope
import ru.anjeib.margin.data.AppData
import ru.anjeib.margin.data.logged
import ru.anjeib.margin.data.properName
import ru.anjeib.margin.data.sentence
import ru.anjeib.margin.data.withNames
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
        const val AUTO_CONFIRM_MS = 5000L
        fun key(scope: Scope) = when (scope) { Scope.Main -> "main"; Scope.Notes -> "notes"; is Scope.InTask -> "task:" + scope.taskId }
    }

    init {
        viewModelScope.launch { store.data.collect { Reminders.syncTasks(getApplication(), it.tasks, it.notes); watchBrief(it) } }
        // A quiet line under the field says what happened — and then leaves.
        viewModelScope.launch {
            _notice.collect { n -> if (n != null) { kotlinx.coroutines.delay(7000); if (_notice.value == n) _notice.value = null } }
        }
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
    // ---------------- first run: two questions in the chat instead of a settings page ----------------

    fun startSetup() {
        if (settings.value.setupStep != 0) return
        prefs.update { it.copy(setupStep = 1) }
        ask(L.s.setupBriefQ, L.s.setupBriefOptions)
    }

    private fun ask(text: String, options: List<String>) {
        val m = ChatMsg(fromUser = false, text = text, options = options)
        addAssistant(m)
        _trayFrom.value = _trayFrom.value ?: m.id
    }

    /** Answers to the first-run questions are handled here; anything else ends the setup and goes to the AI as usual. */
    private fun setupAnswer(t: String): Boolean {
        val step = settings.value.setupStep
        if (step != 1 && step != 2) return false
        val low = t.lowercase()
        when (step) {
            1 -> {
                val off = Regex("не нужн|не надо|^нет|no thanks|^no\\b|not needed|^skip|пропуст").containsMatchIn(low)
                val m = Regex("^(\\d{1,2})(?::(\\d{2}))?$").find(low.replace(" ", ""))
                if (!off && m == null) { prefs.update { it.copy(setupStep = 3) }; return false }
                val time = m?.let { "%02d:%02d".format(it.groupValues[1].toInt().coerceIn(0, 23), it.groupValues[2].ifEmpty { "0" }.toInt().coerceIn(0, 59)) }
                you(t)
                updateSettings { it.copy(morningBrief = !off, morningTime = time ?: it.morningTime, setupStep = 2) }
                ask(L.s.setupNameQ, listOf(L.s.skip))
            }
            2 -> {
                val skip = Regex("^(пропуст|skip|не важно|нет$)").containsMatchIn(low)
                // A long phrase is a task, not a name: stop asking and let the AI take it.
                if (!skip && (t.length > 30 || t.trim().split(Regex("\\s+")).size > 3)) { prefs.update { it.copy(setupStep = 3) }; return false }
                you(t)
                val name = if (skip) "" else t.properName()
                prefs.update { it.copy(userName = name.ifBlank { it.userName }, setupStep = 3) }
                addAssistant(ChatMsg(fromUser = false, text = L.s.setupDone(name)))
            }
        }
        return true
    }

    private fun you(t: String) {
        val user = ChatMsg(fromUser = true, text = t)
        store.update { it.copy(chat = (it.chat + user).takeLast(60)) }
        _trayFrom.value = _trayFrom.value ?: user.id
    }

    fun send(text: String, smart: Boolean = false, label: String? = null, scope: Scope = Scope.Main, files: List<Attachment> = emptyList()) {
        val t = text.trim()
        if ((t.isEmpty() && files.isEmpty()) || _busy.value != null) return
        if (scope == Scope.Main && files.isEmpty() && !smart && setupAnswer(t)) return
        if (_pending.value != null) confirm() // typing on means the preview was fine
        val s = settings.value

        // Files without words need no AI: they just get filed.
        if (t.isEmpty()) { fileAway(scope, files, data.value, null); return }

        // An answer to a question («В четверг в 14:00») belongs with what was asked about — the task keeps both.
        val chatBefore = data.value.chat
        val asked = scope == Scope.Main && chatBefore.lastOrNull()?.let { !it.fromUser && it.options.isNotEmpty() } == true
        val source = if (asked) chatBefore.dropLast(1).lastOrNull { it.fromUser }?.text?.let { "$it — $t" } ?: t else t
        val cmd = if (smart) null else (if (scope is Scope.InTask) Commands.numbered(t) else null) ?: Commands.parse(t)

        var userId: String? = null
        if (scope == Scope.Main) {
            val user = ChatMsg(fromUser = true, text = (label ?: t) + if (files.isNotEmpty()) "  📎${files.size}" else "")
            store.update { it.copy(chat = (it.chat + user).takeLast(60)) }
            _trayFrom.value = user.id
            userId = user.id
        }
        // Said in so many words («добавь подзадачу …», «место: …») — done here, no AI, works offline too.
        if (cmd != null && runCommand(cmd, scope, files, userId)) return
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
                    history + ("user" to Assistant.userMessage(t) + (cmd?.let { Commands.hint(it) }?.let { "\n$it" } ?: "")),
                    smart = smart, complex = Assistant.isComplex(t, scope), images = images,
                )
                prefs.addSpend(cost)
                when {
                    scope != Scope.Main -> applyScoped(plan, scope, files, source)
                    plan.actions.isEmpty() -> { applyPlan(plan, files, source = source, strict = !smart); }
                    else -> propose(plan, files, userId, smart, source)
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

    /**
     * Runs a command locally when its value says everything; false → it needs the AI (dates, «which task»), which then gets a hint.
     * Goes through the same plan → apply → bubble/undo path as the AI, so nothing looks different.
     */
    private fun runCommand(c: Command, scope: Scope, files: List<Attachment>, userId: String?): Boolean {
        if (c.value.isBlank()) return false
        fun o(vararg p: Pair<String, Any>) = kotlinx.serialization.json.JsonObject(p.associate { (k, v) ->
            k to when (v) {
                is List<*> -> kotlinx.serialization.json.JsonArray(v.map { x -> if (x is kotlinx.serialization.json.JsonObject) x else kotlinx.serialization.json.JsonPrimitive(x.toString()) })
                else -> kotlinx.serialization.json.JsonPrimitive(v.toString())
            }
        })
        val d = data.value
        val actions: List<kotlinx.serialization.json.JsonObject> = when (scope) {
            is Scope.InTask -> {
                val task = d.tasks.find { it.id == scope.taskId } ?: return false
                val id = task.id
                // A contact said inside a task goes to its one person; with nobody (or several) it stays as a record.
                val who = (task.people + listOfNotNull(task.waitingFor, task.promisedTo)).distinct().singleOrNull()
                when (c.what) {
                    "subtask" -> listOf(o("type" to "add_subtasks", "task_id" to id, "subtasks" to Commands.items(c.value).map { o("title" to it) }))
                    "place" -> listOf(o("type" to "update_task", "task_id" to id, "place" to c.value))
                    "people" -> listOf(o("type" to "update_task", "task_id" to id, "people" to (task.people + Commands.items(c.value)).distinct()))
                    "project" -> listOf(o("type" to "update_task", "task_id" to id, "project" to c.value))
                    "entry" -> listOf(o("type" to "add_entry", "task_id" to id, "text" to c.value))
                    "time" -> Commands.time(c.value)?.let { listOf(o("type" to "update_task", "task_id" to id, "time" to it)) } ?: return false
                    // «подзадача 2 …»: by the number shown in the card.
                    "sub_edit", "sub_delete", "sub_done" -> {
                        val n = c.value.substringBefore('|').toIntOrNull() ?: return false
                        val sub = task.subtasks.getOrNull(n - 1) ?: run { _notice.value = Notice(key(scope), L.s.noSubtask(n), null); return true }
                        val rest = c.value.substringAfter('|').trim()
                        when (c.what) {
                            "sub_edit" -> if (rest.isBlank()) return false else listOf(o("type" to "update_subtask", "task_id" to id, "subtask_id" to sub.id, "title" to rest))
                            "sub_delete" -> listOf(o("type" to "delete_subtask", "task_id" to id, "subtask_id" to sub.id))
                            else -> listOf(o("type" to "complete_subtask", "task_id" to id, "subtask_id" to sub.id))
                        }
                    }
                    "phone", "email", "telegram" -> if (who != null) listOf(o("type" to "person", "name" to who, c.what to c.value))
                        else listOf(o("type" to "add_entry", "task_id" to id, "text" to (if (c.what == "phone") L.s.fieldPhone else if (c.what == "email") L.s.fieldEmail else "Telegram") + ": " + c.value))
                    else -> return false
                }
            }
            else -> when (c.what) {
                "note" -> listOf(o("type" to "create_note", "text" to c.value))
                else -> return false
            }
        }
        val plan = AiPlan(null, actions, null, local = true)
        if (scope == Scope.Main) propose(plan, files, userId, false, c.value) else applyScoped(plan, scope, files, c.value)
        return true
    }

    // ---------------- blocks: a note's stream or a task's records, edited anywhere ----------------

    /** Where a block lives: «note:<id>» or «task:<id>». */
    private fun editEntries(owner: String, f: (List<Entry>) -> List<Entry>) {
        val id = owner.substringAfter(':')
        if (owner.startsWith("note:")) updateNote(id) { it.copy(entries = f(it.entries), updatedAt = nowStamp()) }
        else updateTask(id) { it.copy(entries = f(it.entries)) }
    }

    private fun entriesOf(owner: String): List<Entry> {
        val id = owner.substringAfter(':')
        return if (owner.startsWith("note:")) data.value.notes.find { it.id == id }?.entries.orEmpty() else data.value.tasks.find { it.id == id }?.entries.orEmpty()
    }

    /** The visit going on in a note or a task card: what is written now groups under one date line. */
    private val sessions = mutableMapOf<String, String>()
    fun startSession(owner: String) { sessions[owner] = newId() }
    private fun stamp(owner: String, e: Entry) = if (e.session == null) e.copy(session = sessions[owner]) else e

    /** Puts [e] right after [afterId] (at the end when null or gone). */
    fun insertEntry(owner: String, afterId: String?, e0: Entry) = stamp(owner, e0).let { e -> editEntries(owner) { l ->
        val i = l.indexOfFirst { it.id == afterId }
        if (i < 0) l + e else l.take(i + 1) + e + l.drop(i + 1)
    } }

    fun moveEntry(owner: String, id: String, delta: Int) = editEntries(owner) { l ->
        val i = l.indexOfFirst { it.id == id }
        val j = i + delta
        if (i < 0 || j !in l.indices) l else l.toMutableList().apply { add(j, removeAt(i)) }
    }

    fun deleteEntry(owner: String, id: String) {
        entriesOf(owner).find { it.id == id }?.attachments?.forEach { Files.delete(ctx, it) }
        editEntries(owner) { l -> l.filterNot { it.id == id } }
    }

    fun editEntryText(owner: String, id: String, text: String) {
        editEntries(owner) { l -> l.mapNotNull { if (it.id != id) it else if (text.isBlank() && it.attachments.isEmpty() && !it.isList) null else it.copy(text = text.sentence()) } }
        linkTitles(owner, id)
    }

    private fun updateAttachment(owner: String, entryId: String, attId: String, f: (Attachment) -> Attachment) = editEntries(owner) { l ->
        l.map { e -> if (e.id != entryId) e else e.copy(attachments = e.attachments.map { if (it.id == attId) f(it) else it }) }
    }

    fun setCaption(owner: String, entryId: String, attId: String, text: String) =
        updateAttachment(owner, entryId, attId) { it.copy(caption = text.trim().ifBlank { null }?.sentence()) }

    /** A text block after [afterId]: a written thought in the middle of the stream. */
    fun insertText(owner: String, afterId: String?, text: String) {
        val t = text.trim().ifBlank { return }
        val e = Entry(text = t.sentence())
        insertEntry(owner, afterId, e)
        linkTitles(owner, e.id)
    }

    fun insertList(owner: String, afterId: String?): String = Entry(isList = true).also { insertEntry(owner, afterId, it) }.id

    fun addListItems(owner: String, entryId: String, text: String) = editEntries(owner) { l ->
        val items = Commands.items(text).ifEmpty { listOf(text.trim()) }.filter { it.isNotBlank() }.map { Subtask(title = it.sentence()) }
        l.map { if (it.id == entryId) it.copy(items = it.items + items) else it }
    }

    fun toggleItem(owner: String, entryId: String, itemId: String) = editEntries(owner) { l ->
        l.map { e -> if (e.id != entryId) e else e.copy(items = e.items.map { if (it.id == itemId) it.copy(done = !it.done) else it }) }
    }

    /**
     * A photo, video, voice note, file or contact as its own block after [afterId] — then made meaningful:
     * a voice note gets its transcript, a photo the text written on it.
     */
    fun insertAttachment(owner: String, afterId: String?, a: Attachment) {
        val e = Entry(attachments = listOf(a))
        insertEntry(owner, afterId, e)
        enrich(owner, e.id, a)
    }

    private fun enrich(owner: String, entryId: String, a: Attachment) {
        val s = settings.value
        when (a.kind) {
            "audio" -> if (a.transcript.isNullOrBlank()) transcribe(a) { text -> updateAttachment(owner, entryId, a.id) { it.copy(transcript = text.sentence()) } }
        }
    }

    /**
     * Text on a photo, only when asked («Текст с фото»): read once (Yandex Vision), remembered, then used —
     * put in as a text block, turned into a contact (a business card), or into tasks (a whiteboard, a paper list).
     */
    fun photoText(owner: String, entryId: String, a: Attachment, action: String) {
        val s = settings.value
        if (!Processor.ready(s)) { _toast.value = L.s.noKey; return }
        if (a.ocr == null && !Processor.online(ctx)) { _toast.value = L.s.errNet; return }
        viewModelScope.launch {
            _working.value += "ocr:${a.id}"
            try {
                val text = a.ocr ?: run {
                    val jpeg = Files.jpeg(ctx, a) ?: return@launch
                    val url = Files.imageDataUrl(ctx, a) ?: return@launch
                    val r = ru.anjeib.margin.ai.Llm.ocr(s, jpeg, url)
                    prefs.addSpend(r.rub)
                    r.content.trim().also { x -> updateAttachment(owner, entryId, a.id) { it.copy(ocr = x) } }
                }
                if (text.isBlank()) { _toast.value = L.s.noTextOnPhoto; return@launch }
                when (action) {
                    "insert" -> insertText(owner, entryId, text)
                    "contact" -> contactFromCard(owner, entryId, text)
                    "tasks" -> tasksFromText(owner, text)
                }
            } catch (e: Exception) {
                _toast.value = (e as? AiException)?.message ?: e.message
            } finally {
                _working.value -= "ocr:${a.id}"
            }
        }
    }

    /** A business card's words → a person with what the card says, and a contact card right after the photo. */
    private suspend fun contactFromCard(owner: String, afterId: String, text: String) {
        val s = settings.value
        val r = ru.anjeib.margin.ai.Llm.complete(s, Assistant.cardPrompt(), listOf("user" to text))
        prefs.addSpend(r.rub)
        val o = Assistant.parseObject(r.content) ?: run { _toast.value = L.s.errEmpty; return }
        val name = Assistant.field(o, "name")?.properName() ?: run { _toast.value = L.s.noTextOnPhoto; return }
        val plan = AiPlan(null, listOf(kotlinx.serialization.json.JsonObject(o.filterKeys { it in setOf("name", "about", "company", "phone", "email", "telegram", "address") } +
            ("type" to kotlinx.serialization.json.JsonPrimitive("person")))), null, local = true)
        store.replace(Assistant.apply(data.value, plan, text).data)
        insertAttachment(owner, afterId, Attachment(kind = "contact", name = name,
            contact = ContactInfo(name, Assistant.field(o, "phone"), Assistant.field(o, "email"))))
        _toast.value = L.s.contactMade(name)
    }

    /** Words from a photo of a whiteboard or a paper list → tasks, linked to the note they came from. */
    private suspend fun tasksFromText(owner: String, text: String) {
        val s = settings.value
        val noteId = owner.takeIf { it.startsWith("note:") }?.substringAfter(':')
        val scope = if (owner.startsWith("task:")) Scope.InTask(owner.substringAfter(':')) else Scope.Main
        val (plan, cost) = Processor.askPlan(s, Assistant.systemPrompt(data.value, s.copy(askWhenUnclear = false), scope, query = text),
            listOf("user" to text + "\n[Text from a photo. Make a task of each actionable item${noteId?.let { " (note_id=$it)" } ?: ""}; in a task scope — subtasks. No notes.]"), complex = true)
        prefs.addSpend(cost)
        val before = data.value
        val applied = Assistant.apply(before, plan.copy(question = null), text, strict = true, focus = (scope as? Scope.InTask)?.taskId)
        store.replace(applied.data)
        _toast.value = applied.caption ?: L.s.nothingFound
    }

    /** Addresses in a text block get their page title as a quiet line under it. */
    fun linkTitles(owner: String, entryId: String) {
        val e = entriesOf(owner).find { it.id == entryId } ?: return
        val urls = Regex("https?://[^\\s)>\\]]+").findAll(e.text).map { it.value.trimEnd('.', ',') }.filter { u -> e.attachments.none { it.url == u } }.toList().take(3)
        if (urls.isEmpty() || !Processor.online(ctx)) return
        viewModelScope.launch {
            urls.forEach { u ->
                val title = withContext(Dispatchers.IO) { pageTitle(u) } ?: android.net.Uri.parse(u).host ?: u
                editEntries(owner) { l -> l.map { x -> if (x.id == entryId && x.attachments.none { it.url == u }) x.copy(attachments = x.attachments + Attachment(kind = "link", name = title, url = u)) else x } }
            }
        }
    }

    private fun pageTitle(u: String): String? = runCatching {
        val c = (java.net.URL(u).openConnection() as java.net.HttpURLConnection).apply { connectTimeout = 8000; readTimeout = 8000; setRequestProperty("User-Agent", "Mozilla/5.0 Margin") }
        try {
            val head = c.inputStream.bufferedReader().use { r -> CharArray(64_000).let { buf -> String(buf, 0, r.read(buf).coerceAtLeast(0)) } }
            Regex("<title[^>]*>([^<]{1,200})</title>", RegexOption.IGNORE_CASE).find(head)?.groupValues?.get(1)?.trim()
                ?.let { android.text.Html.fromHtml(it, android.text.Html.FROM_HTML_MODE_LEGACY).toString().trim() }?.ifBlank { null }
        } finally { c.disconnect() }
    }.getOrNull()

    // ---------------- notes: properties and lists ----------------

    /** People / place / project / reminder of a note, set by hand or by a command. */
    fun setNoteField(id: String, what: String, value: String?) = updateNote(id) { n ->
        val v = value?.trim()?.ifBlank { null }
        when (what) {
            "people" -> n.copy(people = (n.people + Commands.items(v.orEmpty()).map { it.properName() }).distinct())
            "place" -> n.copy(place = v)
            "project" -> n.copy(project = v?.removePrefix("#")?.lowercase())
            "remind" -> n.copy(remindAt = v)
            else -> n
        }
    }

    fun clearNotePeople(id: String) = updateNote(id) { it.copy(people = emptyList()) }

    /** «Список»: a new list block at the end of the note; [items] — what was already said («молоко, хлеб»). */
    fun addNoteList(id: String, items: List<String> = emptyList()) = updateNote(id) { n ->
        n.copy(entries = n.entries + Entry(isList = true, items = items.map { Subtask(title = it.sentence()) }), updatedAt = nowStamp())
    }

    /** Items go to the last list of the note (a new one if there is none). */
    fun addNoteItems(id: String, text: String) = updateNote(id) { n ->
        val items = Commands.items(text).ifEmpty { listOf(text.trim()) }.filter { it.isNotBlank() }.map { Subtask(title = it.sentence()) }
        val last = n.entries.lastOrNull()
        if (last?.isList == true) n.copy(entries = n.entries.dropLast(1) + last.copy(items = last.items + items), updatedAt = nowStamp())
        else n.copy(entries = n.entries + Entry(isList = true, items = items), updatedAt = nowStamp())
    }

    fun toggleNoteItem(noteId: String, entryId: String, itemId: String) = updateNote(noteId) { n ->
        n.copy(entries = n.entries.map { e -> if (e.id != entryId) e else e.copy(items = e.items.map { if (it.id == itemId) it.copy(done = !it.done) else it }) })
    }

    /**
     * What is typed in a note: a fill mode («Список», «Место»…) or a command («добавь список: …») is done here;
     * anything else is a new entry, as before.
     */
    fun writeInNote(id: String, text: String, files: List<Attachment>, mode: String?) {
        val t = text.trim()
        val c = mode?.let { Command(it, t) } ?: Commands.parse(t)
        when (c?.what) {
            "item", "list" -> if (c.value.isBlank()) addNoteList(id) else addNoteItems(id, c.value)
            "people", "place", "project" -> if (c.value.isNotBlank()) setNoteField(id, c.what, c.value) else appendToNote(id, t, files)
            else -> appendToNote(id, t, files)
        }
        if (files.isNotEmpty() && c?.what in setOf("item", "list", "people", "place", "project")) appendToNote(id, "", files)
    }

    /** The composer is filling one thing («Подзадача», «Место»…): done locally, else it goes to the AI as a command. */
    fun sendAs(what: String, text: String, scope: Scope, files: List<Attachment> = emptyList()) {
        val t = text.trim()
        if (t.isEmpty() && files.isEmpty()) return
        if (!runCommand(Command(what, t), scope, files, null)) send(t, scope = scope, files = files)
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
        val p = Pending(newId(), plan, Assistant.apply(data.value, plan, source, strict = !smart, recent = recentTasks()), settings.value.autoConfirm, files, userMsgId, smart, source)
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
        val ids = applyPlan(p.plan, p.files, source = p.source, strict = !p.smart)
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
        _pending.value = p.copy(plan = plan, preview = Assistant.apply(data.value, plan, p.source, strict = !p.smart, recent = recentTasks()), auto = false)
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

    /** «называй меня Аня», «сводку присылай в 9», «вечерний обзор не нужен» — the app's own settings, set by words. */
    private fun applySettings(m: Map<String, String>) {
        if (m.isEmpty()) return
        fun time(v: String) = Regex("^(\\d{1,2}):(\\d{2})$").find(v)?.let { "%02d:%02d".format(it.groupValues[1].toInt().coerceIn(0, 23), it.groupValues[2].toInt().coerceIn(0, 59)) }
        updateSettings { s0 ->
            var s1 = s0
            m["name"]?.let { s1 = s1.copy(userName = it.properName()) }
            m["morning"]?.let { v -> s1 = if (v == "off") s1.copy(morningBrief = false) else time(v)?.let { s1.copy(morningBrief = true, morningTime = it) } ?: s1 }
            m["evening"]?.let { v -> s1 = if (v == "off") s1.copy(eveningReview = false) else time(v)?.let { s1.copy(eveningReview = true, eveningTime = it) } ?: s1 }
            s1
        }
    }

    /** Tasks of the last exchanges — «перенеси его на пятницу» is about them even without naming them. */
    private fun recentTasks() = data.value.chat.takeLast(5).flatMap { it.taskIds }.toSet()

    private fun applyPlan(plan: AiPlan, files: List<Attachment> = emptyList(), silentUser: Boolean = false, source: String = "", strict: Boolean = false): List<String> {
        val current = data.value // the user may have edited something while we waited
        val applied = Assistant.apply(current, plan, source, strict = strict, recent = recentTasks())
        applySettings(applied.settings)
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
        tidyNewNotes(applied, current)
        if (silentUser) _trayFrom.value = msg.id
        return applied.touchedTasks
    }

    private fun applyScoped(plan: AiPlan, scope: Scope, files: List<Attachment>, source: String = "") {
        val current = data.value
        val applied = Assistant.apply(current, plan, source, strict = true, focus = (scope as? Scope.InTask)?.taskId)
        applySettings(applied.settings)
        val next = attachTo(applied, scope, files, current)
        val undoId = newId()
        snapshots[undoId] = current
        store.replace(next)
        tidyNewNotes(applied, current)
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
        // After 18:00 the brief turns to tomorrow — one rebuild if the morning one is still up.
        val evening = java.time.LocalTime.now().hour >= 18
        if (!force && prev?.headline != null && !(evening && (prev.at ?: "") < "18:00")) return
        _working.value += "brief"
        viewModelScope.launch {
            try {
                val r = ru.anjeib.margin.ai.Llm.complete(s, Assistant.briefPrompt(data.value, s, evening), listOf("user" to "brief"), smart = true)
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

    /** Today's and tomorrow's open tasks with their dates — compared with what the brief was built on. */
    fun briefFingerprint(d: AppData): String {
        val tomorrow = LocalDate.now().plusDays(1)
        return d.tasks.filter { !it.done && (isToday(it) || isRunning(it) || it.dueDate == tomorrow) }
            .map { it.id + it.due + it.time }.sorted().joinToString(",")
    }

    /**
     * Keeps «Главное» honest: when the task it names is done, moved off today or deleted, the brief is rebuilt
     * (at most 4 times a day — each rebuild costs a request).
     */
    private fun watchBrief(d: AppData) {
        val b = d.brief?.takeIf { it.date == LocalDate.now().toString() && it.lang == L.lang.name } ?: return
        val t = b.mainTaskId?.let { main -> d.tasks.find { it.id == main } }
        val stillOn = b.mainTaskId == null || (t != null && !t.done && (isToday(t) || isRunning(t) || t.dueDate == LocalDate.now().plusDays(1)))
        // The day changed under the brief: rebuild it quietly — at once when «главное» is gone,
        // otherwise at most every half hour, so a busy morning doesn't burn the day's rebuilds in minutes.
        val changed = b.fingerprint != null && b.fingerprint != briefFingerprint(d)
        val age = runCatching { java.time.Duration.between(java.time.LocalTime.parse(b.at), java.time.LocalTime.now()).toMinutes() }.getOrDefault(999)
        if ((!stillOn || (changed && age >= 30)) && b.revisions < 8 && d.tasks.any { !it.done }) {
            briefJob?.cancel()
            briefJob = viewModelScope.launch { kotlinx.coroutines.delay(4000); makeBrief(force = true) }
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

    // ---------------- tasks ----------------

    private fun updateTask(id: String, by: String = "you", f: (Task) -> Task) =
        store.update { d -> d.copy(tasks = d.tasks.map { if (it.id == id) f(it).logged(it, by) else it }) }

    fun toggleTask(id: String) = store.update { d ->
        val t = d.tasks.find { it.id == id } ?: return@update d
        if (t.done) d.copy(tasks = d.tasks.map { if (it.id == id) it.copy(done = false, doneAt = null, dropped = false).logged(it, "you") else it })
        else {
            // A repeating task leaves its next occurrence behind.
            val next = t.nextOccurrence()
            d.copy(tasks = d.tasks.map { if (it.id == id) it.copy(done = true, doneAt = LocalDate.now().toString()).logged(it, "you") else it } + listOfNotNull(next))
        }
    }

    /** «Не буду делать»: closed without doing it — off the lists, honest in the history and reviews. A repeating one comes back as usual. */
    fun dropTask(id: String) {
        val before = data.value
        val t = before.tasks.find { it.id == id && !it.done } ?: return
        store.update { d -> d.copy(tasks = d.tasks.map { if (it.id == id) it.copy(done = true, dropped = true, doneAt = LocalDate.now().toString()).logged(it, "you") else it } + listOfNotNull(t.nextOccurrence())) }
        offerUndo(before, L.s.droppedDone)
    }

    /**
     * «Сделать заметкой»: the reverse of «идея → задача» — for a thought the AI took for a task.
     * It joins its project's ideas note when there is one, else becomes a note of its own; the task goes away (with undo).
     */
    fun taskToNote(id: String): String? {
        val before = data.value
        val t = before.tasks.find { it.id == id } ?: return null
        val text = (listOf(t.title) + t.entries.map { it.text }.filter { it.isNotBlank() }).joinToString("\n")
        val ideas = if (L.s.aiLanguage == "English") "ideas" else "идеи"
        val home = t.project?.let { p -> before.notes.find { it.project == p && it.title.endsWith(ideas) } } ?: t.noteId?.let { nid -> before.notes.find { it.id == nid } }
        // Its steps stay with it, as a list.
        fun flat(l: List<Subtask>): List<Subtask> = l.flatMap { listOf(it.copy(children = emptyList())) + flat(it.children) }
        val steps = flat(t.subtasks).takeIf { it.isNotEmpty() }?.let { Entry(isList = true, items = it) }
        val fresh = Note(title = t.title, project = t.project, people = t.people, place = t.place,
            entries = t.entries.filter { it.text.isNotBlank() || it.attachments.isNotEmpty() } + listOfNotNull(steps))
        store.update { d ->
            val rest = d.tasks.filterNot { it.id == id }
            if (home != null) d.copy(tasks = rest, notes = d.notes.map { if (it.id == home.id) it.copy(entries = it.entries + Entry(text = text) + listOfNotNull(steps), updatedAt = nowStamp()) else it })
            else d.copy(tasks = rest, notes = listOf(fresh) + d.notes)
        }
        val noteId = home?.id ?: fresh.id
        offerUndo(before, L.s.madeNote)
        return noteId
    }

    fun toggleSubtask(taskId: String, subId: String) = updateTask(taskId) { t ->
        t.copy(subtasks = t.subtasks.mapTree(subId) { it.withDone(!it.done) })
    }

    fun addSubtask(taskId: String, title: String, parent: String? = null) {
        val st = Subtask(title = title.sentence()).takeIf { it.title.isNotEmpty() } ?: return
        updateTask(taskId) { t ->
            if (parent == null) t.copy(subtasks = t.subtasks + st, done = false)
            else t.copy(subtasks = t.subtasks.mapTree(parent) { it.copy(children = it.children + st, done = false) })
        }
    }

    fun renameSubtask(taskId: String, subId: String, title: String) = updateTask(taskId) { t ->
        t.copy(subtasks = t.subtasks.mapTree(subId) { if (title.isBlank()) null else it.copy(title = title.sentence()) })
    }

    fun deleteSubtask(taskId: String, subId: String) = updateTask(taskId) { t ->
        t.copy(subtasks = t.subtasks.mapTree(subId) { null })
    }

    fun saveTask(t0: Task) {
        val t = t0.copy(title = t0.title.sentence().withNames(t0.people + listOfNotNull(t0.waitingFor, t0.promisedTo)),
            people = t0.people.map { if (it == L.s.me) it else data.value.person(it)?.name ?: it.properName() },
            waitingFor = t0.waitingFor?.let { data.value.person(it)?.name ?: it.properName() }, promisedTo = t0.promisedTo?.let { data.value.person(it)?.name ?: it.properName() })
        updateTask(t.id) { Assistant.withAutoReminder(t) }
        store.update { d ->
            val newPeople = (t.people + listOfNotNull(t.waitingFor, t.promisedTo)).filter { p -> p != L.s.me && d.person(p) == null }.distinct().map { Person(name = it) }
            val newProject = t.project?.takeIf { p -> d.projects.none { it.equals(p, true) } }
            d.copy(persons = d.persons + newPeople, projects = d.projects + listOfNotNull(newProject))
        }
    }

    fun deleteTask(id: String) = store.update { d -> d.copy(tasks = d.tasks.filterNot { it.id == id }) }

    fun editTaskEntry(taskId: String, entryId: String, text: String) = updateTask(taskId) { t ->
        t.copy(entries = t.entries.mapNotNull { if (it.id != entryId) it else if (text.isBlank() && it.attachments.isEmpty()) null else it.copy(text = text.sentence()) })
    }

    fun deleteTaskEntry(taskId: String, entryId: String) = updateTask(taskId) { t ->
        t.entries.find { it.id == entryId }?.attachments?.forEach { Files.delete(ctx, it) }
        t.copy(entries = t.entries.filterNot { it.id == entryId })
    }

    /** Moves a task [days] later; overdue and undated tasks count from today. Ranges keep their length. */
    /** «Отложить» to a chosen day: the task moves there whole (a range keeps its length). */
    fun postponeTo(id: String, day: LocalDate) {
        val before = data.value
        val t = before.tasks.find { it.id == id } ?: return
        val base = t.dueDate ?: t.startDate ?: LocalDate.now()
        val shift = java.time.temporal.ChronoUnit.DAYS.between(base, day)
        val moved = t.copy(due = (t.dueDate?.plusDays(shift) ?: day).toString(), start = t.startDate?.plusDays(shift)?.toString()?.takeIf { t.dueDate != null },
            remindAt = t.time?.let { "${day}T$it" }, postponed = t.postponed + 1)
        updateTask(id) { moved }
        offerUndo(before, L.s.movedTo(day.shortLabel().lowercase()))
    }

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
        val t = before.tasks.find { it.id == id } ?: return
        toggleTask(id)
        // A repeating task comes back — say when, or it looks like the tap didn't work.
        val next = if (!t.done) t.nextOccurrence()?.let { n -> (n.dueDate ?: n.startDate)?.shortLabel()?.lowercase() } else null
        offerUndo(before, when { t.done -> L.s.reopened; next != null -> L.s.doneNext(next); else -> L.s.markedDone })
    }

    private fun offerUndo(before: AppData, text: String) {
        val id = newId()
        snapshots[id] = before
        _undoBar.value = Notice("bar", text, id)
        undoJob?.cancel()
        undoJob = viewModelScope.launch { kotlinx.coroutines.delay(8000); if (_undoBar.value?.undoId == id) _undoBar.value = null }
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
                    Assistant.withAutoReminder(t.copy(due = today.toString(), start = null, remindAt = null, postponed = t.postponed + 1)).logged(t, "you")
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

    fun renameNote(id: String, title: String) = updateNote(id) { val x = title.sentence(); if (it.title == x) it else it.copy(title = x) }
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
            val e = stamp("note:$id", Entry(text = text.sentence(), attachments = files))
            val upd = n.copy(entries = n.entries + e, updatedAt = nowStamp())
            d.copy(notes = listOf(upd) + d.notes.filterNot { it.id == id })
        }
        // The new block is the last one: give its links titles, its photos and voice notes their text.
        data.value.notes.find { it.id == id }?.entries?.lastOrNull()?.let { e -> linkTitles("note:$id", e.id); e.attachments.forEach { enrich("note:$id", e.id, it) } }
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

    // ---------------- people ----------------

    private fun updatePerson(id: String, f: (Person) -> Person) =
        store.update { d -> d.copy(persons = d.persons.map { if (it.id == id) f(it) else it }) }

    /** One contact or fact of a person, typed by hand. «personal» takes one fact per line. */
    fun setPersonField(id: String, field: String, value: String) = updatePerson(id) { p ->
        val v = value.trim().ifBlank { null }
        when (field) {
            "about" -> p.copy(about = v, aboutFromUser = v != null)
            "company" -> p.copy(company = v)
            "phone" -> p.copy(phone = v)
            "email" -> p.copy(email = v)
            "address" -> p.copy(address = v)
            "max" -> p.copy(max = v)
            "telegram" -> p.copy(telegram = v?.removePrefix("https://t.me/")?.removePrefix("@")?.let { "@$it" })
            "personal" -> p.copy(personal = value.lines().map { it.trim() }.filter { it.isNotEmpty() }.map { it.sentence() })
            else -> p
        }
    }

    fun say(text: String) { _toast.value = text }

    private fun tidyNewNotes(applied: Applied, before: AppData) =
        applied.touchedNotes.filter { id -> before.notes.none { it.id == id } }.forEach { tidyNote(it) }

    /** A note that is all title gets its spelling and capitals fixed once, by the quick model. */
    fun tidyNote(id: String) {
        val s = settings.value
        val n = data.value.notes.find { it.id == id } ?: return
        if (n.titleFixed || n.title.isBlank() || n.title.length > 60 || !Processor.ready(s) || !Processor.online(ctx) || "tidy:$id" in _working.value) return
        _working.value += "tidy:$id"
        viewModelScope.launch {
            try {
                val r = ru.anjeib.margin.ai.Llm.complete(s, Assistant.tidyPrompt(), listOf("user" to n.title))
                prefs.addSpend(r.rub)
                val clean = (Assistant.field(Assistant.parseObject(r.content), "text") ?: r.content).trim().trim('"').trim()
                val ok = clean.length in (n.title.length * 8 / 10)..(n.title.length * 13 / 10 + 8) && !clean.contains('{')
                updateNote(id) { it.copy(title = if (ok) clean else it.title, titleFixed = true) }
            } catch (e: Exception) {
                android.util.Log.w("Margin", "tidy note failed: ${e.message}")
            } finally {
                _working.value -= "tidy:$id"
            }
        }
    }

    /** «Вы написали» in the card: spelling and capitals fixed once, by the quick model; the words stay the user's. */
    fun tidySource(id: String) {
        val s = settings.value
        val t0 = data.value.tasks.find { it.id == id } ?: return
        val raw = t0.source ?: return
        if (t0.sourceFixed || !Processor.ready(s) || !Processor.online(ctx) || "tidy:$id" in _working.value) return
        _working.value += "tidy:$id"
        viewModelScope.launch {
            try {
                val r = ru.anjeib.margin.ai.Llm.complete(s, Assistant.tidyPrompt(), listOf("user" to raw))
                prefs.addSpend(r.rub)
                val clean = (Assistant.field(Assistant.parseObject(r.content), "text") ?: r.content).trim().trim('«', '»', '"').trim()
                // Only a cleaner copy of the same words is accepted — never a rewrite.
                val ok = clean.length in (raw.length * 8 / 10)..(raw.length * 13 / 10 + 8) && !clean.contains('{')
                updateTask(id) { it.copy(source = if (ok) clean else it.source, sourceFixed = true) }
            } catch (e: Exception) {
                android.util.Log.w("Margin", "tidy failed: ${e.message}")
            } finally {
                _working.value -= "tidy:$id"
            }
        }
    }

    /** Renames a person everywhere their name is used. */
    fun renamePerson(id: String, newName: String) {
        val n = newName.properName().ifEmpty { return }
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
    fun personFingerprint(p: Person, d: AppData): String = d.tasks.filter { t ->
        t.people.any { p.matches(it) } || t.waitingFor?.let { p.matches(it) } == true || t.promisedTo?.let { p.matches(it) } == true
    }.map { it.id + it.title + it.done + it.due + it.waitingFor + it.promisedTo }.sorted().joinToString(",")

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
                        summaryAt = nowStamp(), summaryFp = personFingerprint(it, data.value), updatedAt = nowStamp())
                }
            } catch (e: Exception) {
                _toast.value = e.message
            } finally {
                _working.value -= "person:$id"
            }
        }
    }

    /** «Сводка недели» for a project page. */
    /** What the project summary is about: its tasks' state and its ideas — a change means the summary is out of date. */
    fun projectFingerprint(project: String, d: AppData): String =
        d.tasks.filter { it.project == project }.map { it.id + it.done + it.due + it.waitingFor + it.promisedTo }.sorted().joinToString(",") +
            "|" + d.notes.filter { it.project == project }.sumOf { it.entries.size }

    fun refreshProject(project: String) {
        val s = settings.value
        if (!Processor.ready(s) || "project:$project" in _working.value) return
        _working.value += "project:$project"
        viewModelScope.launch {
            try {
                val r = ru.anjeib.margin.ai.Llm.complete(s, Assistant.projectPrompt(data.value, project), listOf("user" to "summary"), smart = true)
                prefs.addSpend(r.rub)
                val text = Assistant.field(Assistant.parseObject(r.content), "summary") ?: return@launch
                store.update { it.copy(projectSummaries = it.projectSummaries + (project to ru.anjeib.margin.data.ProjectSummary(text, nowStamp(), projectFingerprint(project, it)))) }
            } catch (e: Exception) {
                _toast.value = e.message
            } finally {
                _working.value -= "project:$project"
            }
        }
    }

    /** «В задачу» on an idea: exactly one task, linked back to the idea it came from. */
    /** [entryId] null — a note that is all title («Посмотреть Одиссею»): the title is the idea. */
    fun ideaToTask(noteId: String, entryId: String?) {
        val s = settings.value
        val note = data.value.notes.find { it.id == noteId } ?: return
        val e = (if (entryId == null) Entry(id = "title:$noteId", text = note.title) else note.entries.find { it.id == entryId }) ?: return
        if (!Processor.ready(s)) { _toast.value = L.s.noKey; return }
        _working.value += "idea:${e.id}"
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
                // The note screen has no list to light up — say it in words.
                data.value.tasks.firstOrNull { it.id in created }?.let { _toast.value = L.s.ideaBecameTask(it.title) }
            } catch (ex: Exception) {
                _toast.value = ex.message
            } finally {
                _working.value -= "idea:${e.id}"
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
