package ru.anjeib.margin.ui

import ru.anjeib.margin.data.questionRef
import ru.anjeib.margin.data.deleteTask
import ru.anjeib.margin.data.removeDeal
import ru.anjeib.margin.data.intoTopic
import ru.anjeib.margin.data.dayMonth
import ru.anjeib.margin.data.properName
import ru.anjeib.margin.data.editDeal
import ru.anjeib.margin.data.withSamples
import ru.anjeib.margin.data.hasSamples
import ru.anjeib.margin.data.withoutSamples
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.anjeib.margin.ai.AiException
import ru.anjeib.margin.ai.AiPlan
import ru.anjeib.margin.ai.Command
import ru.anjeib.margin.ai.Commands
import ru.anjeib.margin.ai.Applied
import ru.anjeib.margin.ai.Assistant
import ru.anjeib.margin.ai.LocalParser
import ru.anjeib.margin.ai.ModelInfo
import ru.anjeib.margin.ai.OpenRouter
import ru.anjeib.margin.ai.Processor
import ru.anjeib.margin.ai.Scope
import ru.anjeib.margin.data.*
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

    private val _net = MutableStateFlow(ru.anjeib.margin.ai.Cloud.network(ctx))
    /** A quiet word by the day's summary while the AI can't be reached: «без интернета» or «без облака» — Margin reads on the phone then. */
    val away: StateFlow<String?> = kotlinx.coroutines.flow.combine(_net, ru.anjeib.margin.ai.Cloud.state, prefs.settings) { net, st, s ->
        when {
            !Processor.ready(s) -> null
            !net -> L.s.noNetMark
            st == ru.anjeib.margin.ai.Cloud.State.Down -> L.s.noCloudMark
            else -> null
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    /** The saved file could not be read as is (see [Store.load]); told once. */
    val storeProblem: StateFlow<ru.anjeib.margin.data.LoadProblem?> = store.problem
    fun dismissStoreProblem() = store.dismissProblem()

    private val _busy = MutableStateFlow<String?>(null) // scope key in flight
    val busy: StateFlow<String?> = _busy

    /** Note/person/AI operations in flight, by key. */
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
        /** Bumped when the examples change: 2 — the owner's edits after the first test (0.26); 3 — «Примеры» from the designer's demo in place of «Знакомство с Margin» (0.29). */
        const val INTRO_VERSION = 3
        const val AUTO_CONFIRM_MS = 5000L
        fun key(scope: Scope) = when (scope) { Scope.Main -> "main"; Scope.Notes -> "notes"; is Scope.InTask -> "task:" + scope.taskId }
    }

    init {
        // The demo copy opens with the sample already in it, once.
        if (ru.anjeib.margin.BuildConfig.DEMO && !ru.anjeib.margin.data.Demo.asked(ctx)) loadDemo()
        refreshSamples()
        viewModelScope.launch { store.data.collect { Reminders.syncTasks(getApplication(), it.tasks, it.notes) } }
        // A quiet line under the field says what happened — and then leaves.
        viewModelScope.launch {
            _notice.collect { n -> if (n != null) { kotlinx.coroutines.delay(7000); if (_notice.value == n) _notice.value = null } }
        }
        refreshCalendar()
        Processor.schedule(ctx)
        ru.anjeib.margin.voice.LocalSpeech.refresh(ctx)
        ru.anjeib.margin.voice.ModelWorker.schedule(ctx)
        // Back online → let the queue worker run right away.
        runCatching {
            ctx.getSystemService(android.net.ConnectivityManager::class.java)?.registerDefaultNetworkCallback(object : android.net.ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: android.net.Network) { Processor.schedule(ctx) }
                override fun onCapabilitiesChanged(network: android.net.Network, caps: android.net.NetworkCapabilities) {
                    _net.value = caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) && caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                }
                override fun onLost(network: android.net.Network) { _net.value = ru.anjeib.margin.ai.Cloud.network(ctx) }
            })
        }
    }

    // ---------------- forwarding: Telegram bot, mail, «В Margin», a copied message ----------------

    val forward = ru.anjeib.margin.ai.Forward.state
    private var forwardJob: kotlinx.coroutines.Job? = null

    /** While the app is open the bot is asked continuously (a forward shows up in seconds), mail every two minutes. */
    fun foreground(on: Boolean) {
        forwardJob?.cancel()
        cloudJob?.cancel()
        if (!on) return
        watchCloud()
        // «Телефон»: whatever waited in «Входящие» for the AI is read on the phone now.
        if (settings.value.parseMode == "local" && data.value.queue.isNotEmpty()) sortForwarded()
        val F = ru.anjeib.margin.ai.Forward
        F.load(ctx)
        forwardJob = viewModelScope.launch {
            var mailAt = 0L
            while (true) {
                val st = F.state.value
                if (!st.telegram && !st.mail) break
                var n = 0
                if (st.mail && System.currentTimeMillis() - mailAt > 120_000) { mailAt = System.currentTimeMillis(); n += F.pollMail(ctx) }
                n += if (st.telegram && ru.anjeib.margin.ai.Cloud.network(ctx)) F.pollTelegram(ctx, waitSec = 25) else { kotlinx.coroutines.delay(30_000); 0 }
                if (n > 0) sortForwarded()
                if (F.state.value.error != null) kotlinx.coroutines.delay(30_000)
            }
        }
    }

    private var cloudJob: kotlinx.coroutines.Job? = null

    /** While the app is in front Margin knows whether its cloud answers (a knock every half-minute); once it answers again, what waited is sorted. */
    private fun watchCloud() {
        cloudJob = viewModelScope.launch {
            ru.anjeib.margin.ai.Cloud.keepWatch(ctx, { settings.value }) { sortForwarded(); Processor.schedule(ctx) }
        }
    }

    /** Why the AI can't be asked now, in the user's words: no network at all, or Margin's cloud not answering. */
    private fun whyOff() = if (ru.anjeib.margin.ai.Cloud.network(ctx)) L.s.cloudOff else L.s.errNet

    /** Sorts what came in right here, and says what it became under the field instead of a notification. */
    private fun sortForwarded() {
        if (settings.value.parseMode != "local" && (!Processor.ready(settings.value) || !Processor.online(ctx))) return
        viewModelScope.launch {
            val lines = runCatching { Processor.drain(ctx) }.getOrDefault(emptyList())
            if (lines.isNotEmpty()) _notice.value = Notice("main", lines.joinToString("\n"), null)
        }
    }

    /** A copied message (MAX, Telegram «Копировать») or anything else handed in while the app is open. */
    fun forwardIn(text: String, via: String, comment: String?, files: List<Attachment> = emptyList(), from: String? = null) {
        if (text.isBlank()) return
        Processor.enqueue(ctx, ru.anjeib.margin.data.Queued(text = text, via = via, comment = comment?.ifBlank { null }, files = files, from = from))
        sortForwarded()
    }

    fun connectTelegram(token: String, done: (String?) -> Unit) = viewModelScope.launch {
        val err = ru.anjeib.margin.ai.Forward.connectTelegram(ctx, token)
        done(err)
        if (err == null) foreground(true)
    }

    fun connectMail(address: String, password: String, done: (String?) -> Unit) = viewModelScope.launch {
        val err = ru.anjeib.margin.ai.Forward.connectMail(ctx, address, password)
        done(err)
        if (err == null) foreground(true)
    }

    fun disconnectTelegram() { ru.anjeib.margin.ai.Forward.disconnectTelegram(ctx); ru.anjeib.margin.ai.Forward.schedule(ctx) }
    fun disconnectMail() { ru.anjeib.margin.ai.Forward.disconnectMail(ctx); ru.anjeib.margin.ai.Forward.schedule(ctx) }

    fun toastShown() { _toast.value = null }
    fun dismissTray() { _trayFrom.value = null }
    fun clearNotice() { _notice.value = null }

    // ---------------- assistant ----------------

    /**
     * Sends what the user wrote (plus attachments) to the assistant.
     * Main scope goes through the preview card; inside a task or in notes changes apply at once with an Undo line.
     */
    // ---------------- first run ----------------

    /**
     * The morning summary is chosen on the last tour page; the name is never asked — it can be said any time
     * («называй меня Аня»). Asking in the chat used to swallow the first short matter («Купить хлеб») as a name.
     */
    /** The day picked in «Календарь» (not today): what is written then goes to that day unless the words name another. */
    val calendarDay = kotlinx.coroutines.flow.MutableStateFlow<LocalDate?>(null)
    fun pickCalendarDay(d: LocalDate?) { calendarDay.value = d }
    /** The day the calendar should open on — «Завтра — 3 дела ›» opens it on tomorrow (Fable, 0.35); taken once. */
    val calendarOpenOn = kotlinx.coroutines.flow.MutableStateFlow<LocalDate?>(null)

    /** Adds the picked calendar day in words when the phrase has no date of its own — the parser and the AI then read it as said. */
    private fun onPickedDay(t: String): String {
        val day = calendarDay.value ?: return t
        // Ideas, topics, records and «#тема» have no day of their own.
        if (Regex("(?<!\\p{L})/(иде|тем|запис|idea|topic|note)|#", RegexOption.IGNORE_CASE).containsMatchIn(t)) return t
        val items = runCatching { ru.anjeib.margin.ai.LocalParser.parse(t, data.value).items }.getOrDefault(emptyList())
        val dated = items.any { it.due != null || it.start != null || it.days.isNotEmpty() || it.repeat != null }
        return if (dated) t else "$t ${day.dayMonth()}"
    }

    fun finishSetup() { if (settings.value.setupStep != 3) prefs.update { it.copy(setupStep = 3) } }

    fun send(text: String, smart: Boolean = false, label: String? = null, scope: Scope = Scope.Main, files: List<Attachment> = emptyList(), asQuestion: Boolean = false, movedHere: Boolean = false) {
        // Whether the cloud answers is known before asking it: usually from the knock while typing; if not, one knock — at most a second.
        val C = ru.anjeib.margin.ai.Cloud
        val s = settings.value
        if (Processor.ready(s) && C.network(ctx) && !C.down && !C.fresh()) viewModelScope.launch { C.check(s); sendNow(text, smart, label, scope, files, asQuestion, movedHere) }
        else sendNow(text, smart, label, scope, files, asQuestion, movedHere)
    }

    /** «+ тема» in the Overview: an empty topic by its name, as a project to fill — its id, to open it at once. */
    fun newTopic(name: String): String? {
        val title = name.trim().trim('«', '»', '"').takeIf { it.isNotBlank() }?.replaceFirstChar { it.uppercaseChar() } ?: return null
        val t = Task(title = title, topic = true, history = listOf(ru.anjeib.margin.data.Change(kind = "created", by = "you")))
        store.update { it.copy(tasks = it.tasks + t) }
        land(listOf(t.id))
        return t.id
    }

    /**
     * After dictation (owner, 13 окт): the words that say what it is and where it goes become marks over the field and leave
     * the text — «в тему Ремонт ванной купить плитку» → [#Ремонт ванной] «Купить плитку», «Идея. Блог о дизайне» → [идея] «Блог о
     * дизайне», «тема переезд» → [тема] «Переезд». Returns the marks (as the field keeps them) and what is left.
     */
    fun marksOf(text: String, inTopic: Boolean = false): Pair<List<String>, String> {
        fun cap(s: String) = s.trim().replaceFirstChar { it.uppercaseChar() }
        val t = text.trim()
        if (!inTopic) Commands.intoTopic(t, data.value.tasks.filter { it.topic && !it.done }.map { it.id to it.title })?.let { (id, said) ->
            val (m, rest) = LocalParser.marks(said)
            return listOf("#[$id]") + m.map(::markToken) to cap(rest)
        }
        if (!inTopic) Commands.newTopic(t)?.let { return listOf("/" + L.s.tagTopic) to cap(it) }
        Commands.parse(t)?.takeIf { it.what == "task" || it.what == "note" }?.takeIf { it.value.isNotBlank() }?.let { c ->
            return listOf("/" + if (c.what == "note") L.s.tagIdea else L.s.tagDeal) to cap(c.value)
        }
        val (m, rest) = LocalParser.marks(t)
        if (m.isNotEmpty() && rest.isNotBlank()) return m.map(::markToken) to cap(rest)
        return emptyList<String>() to text
    }

    /** «idea» → «/идея»: a mark as the field writes it. */
    private fun markToken(k: String) = "/" + when (k) { "idea" -> L.s.tagIdea; "task" -> L.s.tagDeal; "work" -> L.s.tagWork; else -> L.s.tagPersonal }

    /**
     * Words that say what it is, typed or dictated in any form and even misheard («В идеи посмотреть кино», «в идее…», «слеш
     * дело…», «по работе…»), count as the marks themselves (owner, 13 окт): the same as «/идея Посмотреть кино».
     */
    fun withSaidMarks(text: String): String {
        val (m, rest) = runCatching { marksOf(text) }.getOrNull() ?: return text
        return if (m.isEmpty()) text else (m.joinToString(" ") + " " + rest).trim()
    }

    /**
     * «Телефон» and the phone found nothing to make: a review or a question gets an honest word; in a topic or a matter the
     * words become a record; elsewhere a matter titled by them — never lost, never sent anywhere.
     */
    private fun keepOnPhone(t: String, scope: Scope, files: List<Attachment>, userId: String?, source: String, smart: Boolean, movedHere: Boolean) {
        fun o(vararg p: Pair<String, String>) = kotlinx.serialization.json.JsonObject(p.associate { (k, v) -> k to kotlinx.serialization.json.JsonPrimitive(v) })
        if (smart || LocalParser.parse(t, data.value).why == "question") {
            if (scope == Scope.Main) addAssistant(ChatMsg(fromUser = false, text = L.s.askLocal)) else _toast.value = L.s.askLocal
            return
        }
        val plan = when (scope) {
            is Scope.InTask -> AiPlan(null, listOf(o("type" to "add_entry", "task_id" to scope.taskId, "text" to t)), null, local = true)
            Scope.Notes -> AiPlan(null, listOf(o("type" to "create_note", "text" to t)), null, local = true)
            else -> AiPlan(null, listOf(o("type" to "create_task", "title" to t.sentence())), null, local = true)
        }
        if (scope == Scope.Main) propose(plan, files, userId, false, source) else applyScoped(plan, scope, files, source, movedHere)
    }

    private val moveCue = Regex("^(?:перенес\\p{L}*|перенос\\p{L}*|сдвин\\p{L}*|передвин\\p{L}*|отлож\\p{L}*|поставь|move|postpone)\\s+(?=(?:на|в|к|to|on)\\s)", RegexOption.IGNORE_CASE)

    /**
     * «перенеси на пятницу», «сдвинь на завтра в 15», «отложи на неделю» said on a matter's page: the phone reads the day and
     * the hour, the matter (or the topic's only open one) moves, with undo. False when the words aren't that.
     */
    fun changeDay(taskId: String, text: String, subId: String? = null): Boolean {
        val m = moveCue.find(text.trim()) ?: return false
        // «отложи на неделю», «на два дня» — a move by so long is «через …».
        val rest = text.trim().substring(m.range.last + 1).replace(Regex("^на\\s+(?=(?:\\d+|два|две|три|пару)?\\s*(?:недел|месяц|дн|день|час))", RegexOption.IGNORE_CASE), "через ")
        val item = LocalParser.parse(rest, data.value).items.singleOrNull() ?: return false
        if (item.due == null && item.time == null) return false
        val before = data.value
        val task = before.tasks.find { it.id == taskId } ?: return false
        if (task.topic) {
            // A topic moves the matter whose page this is, or its only open one; with several, the words don't say which.
            val q = (if (subId != null) ru.anjeib.margin.ai.Assistant.flatSubs(task.subtasks).find { it.id == subId } else task.subtasks.filter { !it.done }.singleOrNull()) ?: return false
            store.update { it.setQuestionDue(taskId, q.id, item.due?.toString() ?: q.due, item.time ?: q.time) }
        } else store.update { it.editTask(taskId) { t -> t.copy(due = item.due?.toString() ?: t.due, start = if (item.due != null) null else t.start, time = item.time ?: t.time) } }
        offerUndo(before, L.s.movedTo(listOfNotNull(item.due?.let { it.dowShort().lowercase() + " " + it.dayMonthShort() }, item.time).joinToString(" ")))
        land(listOf(taskId))
        return true
    }

    /** Words from a matter's page: is this a request to act on, or the user's comment? */
    fun isRequest(text: String) = Commands.isRequest(text)

    private fun sendNow(text: String, smart: Boolean, label: String?, scope: Scope, files: List<Attachment>, asQuestion: Boolean, movedHere: Boolean) {
        // «в тему Ремонт ванной купить плитку» — into that topic, as «#тема» would send it (on the phone, offline too).
        if (!smart && scope == Scope.Main) Commands.intoTopic(text, data.value.tasks.filter { it.topic && !it.done }.map { it.id to it.title })?.let { (id, said) ->
            data.value.tasks.find { it.id == id }?.let { _toast.value = L.s.movedToTopic(it.title); land(listOf(it.id)) }
            // «купить плитку» (a verb first) is a matter of the topic; «хозяйка согласна» — a record in its feed.
            val deal = Regex("^\\p{L}+(?:ть|ти|чь|ться|тись)(?![\\p{L}])", RegexOption.IGNORE_CASE).containsMatchIn(said)
            return sendNow(said, false, label, Scope.InTask(id), files, asQuestion = deal, movedHere = true)
        }
        // «Сделай тему Правки к приложению» — a topic now, by its name, on the phone (as «/тема Правки к приложению»).
        if (!smart && scope == Scope.Main) Commands.newTopic(text)?.let { name -> return sendNow("/" + L.s.tagTopic + " " + name, false, label ?: text, scope, files, asQuestion, movedHere) }
        // «Дело: купить хлеб», «добавь дело позвонить маме» — a matter, read on the phone (dates and people too), not left to the AI.
        if (!smart && scope == Scope.Main) Commands.parse(text)?.takeIf { it.what == "task" && it.value.isNotBlank() }?.let { c ->
            return sendNow("/" + L.s.tagDeal + " " + c.value, false, label ?: text, scope, files, asQuestion, movedHere)
        }
        // «/дело», «/тема», «/идея», «/работа», «/личное» — said by the user, so they decide; the AI only fills in the rest.
        val tg = parseTags(if (scope == Scope.Main && !smart) onPickedDay(withSaidMarks(text)) else text)
        lastSaid = tg.text to text
        val t = tg.text
        val tagKind = tg.kind
        val tagSphere = tg.sphere
        if ((t.isEmpty() && files.isEmpty()) || _busy.value != null) return
        if (tg.done) { doneByWords(tg, scope); return }
        // «#тема» from the main field: the words go into that topic, as if written there.
        if (tg.topicId != null && scope == Scope.Main) {
            data.value.tasks.find { it.id == tg.topicId }?.let { _toast.value = L.s.movedToTopic(it.title); land(listOf(it.id)) }
            val rest = text.replace(hashRx, " ").replace(Regex("\\s{2,}"), " ").trim()
            send(rest, smart, label, Scope.InTask(tg.topicId), files, asQuestion = asQuestion || tg.kind == "task", movedHere = true)
            return
        }
        // «/запись»: only the words, into the feed — nothing made of them.
        if (tg.record && t.isNotEmpty()) {
            when (scope) {
                is Scope.InTask -> applyScoped(AiPlan(null, listOf(kotlinx.serialization.json.JsonObject(mapOf(
                    "type" to kotlinx.serialization.json.JsonPrimitive("add_entry"), "task_id" to kotlinx.serialization.json.JsonPrimitive(scope.taskId),
                    "text" to kotlinx.serialization.json.JsonPrimitive(t)))), null, local = true), scope, files, t, true)
                else -> runCommand(Command("note", t), scope, files, null)
            }
            return
        }
        if (settings.value.setupStep in 1..2) finishSetup() // left over from the old chat questions: words are words
        if (_pending.value != null) confirm() // typing on means the preview was fine
        val s = settings.value

        // Files without words need no AI: they just get filed.
        if (t.isEmpty()) { fileAway(scope, files, data.value, null); return }

        // An answer to a question («В четверг в 14:00») belongs with what was asked about — the task keeps both.
        val chatBefore = data.value.chat
        val asked = scope == Scope.Main && chatBefore.lastOrNull()?.let { !it.fromUser && it.options.isNotEmpty() } == true
        val source = if (asked) chatBefore.dropLast(1).lastOrNull { it.fromUser }?.text?.let { "$it — $t" } ?: t else t
        val cmd = if (smart) null else (tagKind?.takeIf { it != "topic" }?.let { Command(it, t) }
            ?: (if (scope is Scope.InTask) Commands.numbered(t) else null) ?: Commands.parse(t))

        var userId: String? = null
        if (scope == Scope.Main) {
            val user = ChatMsg(fromUser = true, text = (label ?: t) + if (files.isNotEmpty()) "  📎${files.size}" else "")
            store.update { it.copy(chat = (it.chat + user).takeLast(60)) }
            _trayFrom.value = user.id
            userId = user.id
        }
        // Said in so many words («добавь подзадачу …», «место: …») — done here, no AI, works offline too.
        if (cmd != null && runCommand(cmd, scope, files, userId)) return
        // «перенеси на пятницу», «на завтра в 15» on a matter's page — its day changes, read on the phone.
        if (!smart && scope is Scope.InTask && changeDay(scope.taskId, t)) return
        // Read on the phone: always offline or without a key, in «Телефон» mode, in «Авто» when the phone is sure,
        // and whenever the marks said it outright (#, @, /жду, /обещал, /дело…) — then they are what counts.
        var phone: LocalParser.Result? = null
        if (!smart && scope != Scope.Notes && (cmd == null || tagKind != null)) {
            // A slip in the phone's reading must never lose the words: then they go the usual way (the AI or the queue).
            val who = tg.people.firstOrNull()
            val local = runCatching { LocalParser.parse(t, data.value, kind = when (tagKind) { "note" -> "idea"; "topic" -> "topic"; "task" -> "task"; else -> null }, sphere = tagSphere,
                people = tg.people, waitFor = who.takeIf { tg.wait }, promiseTo = who.takeIf { tg.promise }, wait = tg.wait, promise = tg.promise) }
                .getOrElse { LocalParser.Result(emptyList(), false, "error") }
            phone = local.takeIf { it.items.isNotEmpty() && (it.sure || tg.explicit) }
            val mode = s.parseMode
            val offline = !Processor.online(ctx) || !Processor.ready(s)
            // A question is for the assistant only: without it, say so rather than file the question as a matter.
            if (local.why == "question" && offline) {
                val why = if (s.parseMode == "local") L.s.askLocal else if (Processor.ready(s) && ru.anjeib.margin.ai.Cloud.network(ctx)) L.s.askCloudOff else L.s.askOffline
                if (scope == Scope.Main) addAssistant(ChatMsg(fromUser = false, text = why)) else _toast.value = why
                return
            }
            // «Авто» without the internet: what the phone is unsure of waits in «Входящие» for the AI, as that screen promises —
            // a long phrase made into one matter with the whole phrase for a title is worse than a short wait (Fable, 0.35).
            if (local.items.isNotEmpty() && !Processor.online(ctx) && Processor.ready(s) && mode == "auto" && !local.sure && !tg.explicit) {
                queue(t, scope, files)
                return
            }
            if (local.items.isNotEmpty() && (offline || mode == "local" || tg.explicit || (mode == "auto" && local.sure))) {
                val plan = LocalParser.plan(local, scope, asQuestion, source = t)
                count("parse:phone")
                if (scope == Scope.Main) propose(plan, files, userId, false, source) else applyScoped(plan, scope, files, source, movedHere)
                return
            }
        }
        // Without the AI («Телефон»): nothing is left unsaid — what the phone couldn't sort is kept as written.
        if (s.parseMode == "local") { keepOnPhone(t, scope, files, userId, source, smart, movedHere); return }
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
                val (plan0, cost) = Processor.askPlan(
                    s,
                    Assistant.systemPrompt(before, s, scope, query = if (smart) null else t + " " + history.joinToString(" ") { it.second }, recent = recent),
                    history + ("user" to Assistant.userMessage(t) + (cmd?.let { Commands.hint(it) }?.let { "\n$it" } ?: "") +
                        (if (asQuestion) "\n[Written on this topic's questions page: a new question → add_subtasks (due/time/from only when said), no add_entry.]" else "") +
                        (if (movedHere) "\n[The user moved these words into this topic: add_entry with them; questions or answers only for THIS topic.]" else "") +
                        (if (tagKind == "topic") "\n[The user marked this as a TOPIC: exactly one create_task named by the matter, its steps as subtasks.]" else "")),
                    smart = smart, complex = Assistant.isComplex(t, scope), images = images,
                )
                prefs.addSpend(cost)
                if (!smart) count("parse:ai")
                // «Исправить …» changes what exists — whatever the model thought, nothing new is made.
                val plan = when (cmd?.what) {
                    "fix" -> plan0.copy(actions = plan0.actions.filterNot { a -> a["type"]?.toString()?.trim('"')?.startsWith("create_") == true })
                    // Asked for in so many words: a contact at once, not a draft.
                    "contact" -> plan0.copy(actions = plan0.actions.map { a -> if (a["type"]?.toString()?.trim('"') == "person") kotlinx.serialization.json.JsonObject(a + ("keep" to kotlinx.serialization.json.JsonPrimitive("true"))) else a })
                    else -> plan0
                }.let { p0 ->
                    // The tags win over the model: the sphere on what is created, a topic when asked for one.
                    if (tagSphere == null && tagKind != "topic") p0 else p0.copy(actions = p0.actions.map { a ->
                        if (a["type"]?.toString()?.trim('"') != "create_task") a
                        else kotlinx.serialization.json.JsonObject(a + listOfNotNull(
                            tagSphere?.let { "sphere" to kotlinx.serialization.json.JsonPrimitive(it) },
                            if (tagKind == "topic") "topic" to kotlinx.serialization.json.JsonPrimitive("true") else null,
                        ))
                    })
                }
                when {
                    scope != Scope.Main -> applyScoped(plan, scope, files, source, movedHere)
                    plan.actions.isEmpty() -> { applyPlan(plan, files, source = source, strict = !smart); }
                    else -> propose(plan, files, userId, smart, source)
                }
            } catch (e: Exception) {
                val msg = (e as? AiException)?.message ?: e.message ?: e.toString()
                // Lost connection mid-way: what the phone read for sure is made now, the rest is kept for later — never lost.
                val sure = phone
                if ((msg == L.s.errNet || !Processor.online(ctx)) && sure != null) {
                    val plan = LocalParser.plan(sure, scope, asQuestion, source = t)
                    count("parse:phone")
                    if (scope == Scope.Main) propose(plan, files, userId, false, source) else applyScoped(plan, scope, files, source, movedHere)
                }
                else if (msg == L.s.errNet || !Processor.online(ctx)) queue(t, scope, files)
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
        // «итог дня в 21», «утреннюю сводку в 9» (Commands.daily) — the app's own setting, at once and offline.
        if (c.what == "settings") {
            val k = c.value.substringBefore('='); val v = c.value.substringAfter('=')
            applySettings(mapOf(k to v))
            val said = L.s.dailySet(k == "evening", v.takeIf { it != "off" })
            if (scope == Scope.Main) addAssistant(ChatMsg(fromUser = false, text = said)) else _toast.value = said
            return true
        }
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
                    // «Добавь заметку …», «запись: …» inside a topic or a matter: the words go on its page, as written.
                    "entry", "note" -> listOf(o("type" to "add_entry", "task_id" to id, "text" to c.value))
                    // «Добавь дело …» there: a matter of this topic.
                    "task" -> listOf(o("type" to "add_subtasks", "task_id" to id, "subtasks" to Commands.items(c.value).map { o("title" to it) }))
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

    /** The visit going on in a note or a task card: what is written now groups under one date line. */
    private val sessions = mutableMapOf<String, String>()
    fun startSession(owner: String) { sessions[owner] = newId() }
    private fun stamp(owner: String, e: Entry) = if (e.session == null) e.copy(session = sessions[owner]) else e

    /** Puts [e] right after [afterId] (at the end when null or gone). Where a block lives: «note:<id>» or «task:<id>». */
    fun insertEntry(owner: String, afterId: String?, e0: Entry) = stamp(owner, e0).let { e -> store.update { it.insertEntry(owner, afterId, e) } }

    fun moveEntry(owner: String, id: String, delta: Int) = store.update { it.moveEntry(owner, id, delta) }

    fun deleteEntry(owner: String, id: String) {
        data.value.entriesOf(owner).find { it.id == id }?.attachments?.forEach { Files.delete(ctx, it) }
        store.update { it.removeEntry(owner, id) }
    }

    fun editEntryText(owner: String, id: String, text: String) {
        store.update { it.editEntryText(owner, id, text) }
        linkTitles(owner, id)
    }

    private fun updateAttachment(owner: String, entryId: String, attId: String, f: (Attachment) -> Attachment) = store.update { it.editAttachment(owner, entryId, attId, f) }

    fun setCaption(owner: String, entryId: String, attId: String, text: String) = store.update { it.setCaption(owner, entryId, attId, text) }

    // ---------------- «Скопировать», «Переместить» → «Вставить сюда» (owner, 13 окт) ----------------

    /**
     * What was copied or is being moved: a record (with its photos and files) or one photo/file of it ([attId]).
     * Pasted on any page with «Вставить сюда»; a move leaves its old place only then.
     */
    data class Clip(val owner: String, val entryId: String, val attId: String?, val preview: String, val cut: Boolean)
    private val _clip = MutableStateFlow<Clip?>(null)
    val clip: StateFlow<Clip?> = _clip

    /** The line of marks («/дело /тема /идея») the bar offers, shown over the page's writing line while a page is written on. */
    val pageMarks = androidx.compose.runtime.mutableStateOf<(@androidx.compose.runtime.Composable () -> Unit)?>(null)

    private fun clipOf(owner: String, entryId: String, attId: String?, cut: Boolean): Clip? {
        val e = data.value.entriesOf(owner).find { it.id == entryId } ?: return null
        val preview = if (attId != null) e.attachments.find { it.id == attId }?.let { it.caption ?: it.name.ifBlank { it.kind } } ?: return null
            else e.text.ifBlank { e.attachments.firstOrNull()?.let { it.caption ?: it.name } ?: "" }
        return Clip(owner, entryId, attId, preview.lineSequence().first().take(40), cut)
    }

    fun copyRecord(owner: String, entryId: String, attId: String? = null) {
        val c = clipOf(owner, entryId, attId, cut = false) ?: return
        // The words go to the phone's clipboard too — to paste anywhere, in Margin or not.
        if (attId == null) data.value.entriesOf(owner).find { it.id == entryId }?.text?.takeIf { it.isNotBlank() }?.let { t ->
            ctx.getSystemService(android.content.ClipboardManager::class.java)?.setPrimaryClip(android.content.ClipData.newPlainText("Margin", t))
        }
        _clip.value = c
        _toast.value = L.s.copiedHint
    }

    fun moveRecord(owner: String, entryId: String, attId: String? = null) {
        _clip.value = clipOf(owner, entryId, attId, cut = true) ?: return
        _toast.value = L.s.movingHint
    }

    fun clearClip() { _clip.value = null }

    /**
     * «Вставить сюда»: into a topic's or an idea's feed («task:<id>»), a matter's comments («comment:<ref>», words only),
     * or a simple matter («deal:<id>»: words become a comment, with files — a record). A moved one leaves where it was.
     */
    fun pasteInto(target: String) {
        val c = _clip.value ?: return
        val src = data.value.entriesOf(c.owner).find { it.id == c.entryId } ?: run { _clip.value = null; return }
        val atts = if (c.attId != null) src.attachments.filter { it.id == c.attId } else src.attachments
        val text = if (c.attId != null) "" else src.text
        val files = if (c.cut) atts else atts.map { Files.duplicate(ctx, it) }
        val asComment = target.startsWith("comment:") || (target.startsWith("deal:") && files.isEmpty())
        when {
            asComment -> { if (text.isBlank()) { _toast.value = L.s.pasteNoFilesHere; return }; addComment(target.substringAfter(':'), text, nowStamp()) }
            else -> store.update { it.insertEntry("task:" + target.substringAfter(':'), null,
                Entry(at = if (c.cut) src.at else nowStamp(), text = text, attachments = files, items = if (c.attId == null) src.items else emptyList())) }
        }
        if (c.cut) store.update { d -> if (c.attId != null) d.dropAttachment(c.owner, c.entryId, c.attId) else d.removeEntry(c.owner, c.entryId) }
        _clip.value = null
    }

    /** «Удалить» on a photo or a file: it goes, with its file; a record left empty goes too. */
    fun removeAttachment(owner: String, entryId: String, attId: String) {
        data.value.entriesOf(owner).find { it.id == entryId }?.attachments?.find { it.id == attId }?.let { Files.delete(ctx, it) }
        store.update { it.dropAttachment(owner, entryId, attId) }
    }

    /** A text block after [afterId]: a written thought in the middle of the stream. */
    fun insertText(owner: String, afterId: String?, text: String) {
        val t = text.trim().ifBlank { return }
        val e = Entry(text = t.sentence())
        insertEntry(owner, afterId, e)
        linkTitles(owner, e.id)
    }

    fun insertList(owner: String, afterId: String?): String = Entry(isList = true).also { insertEntry(owner, afterId, it) }.id

    /** «молоко, хлеб и сыр» → the items; the whole text when nothing splits it. */
    private fun listItems(text: String) = Commands.items(text).ifEmpty { listOf(text.trim()) }

    fun addListItems(owner: String, entryId: String, text: String) = store.update { it.addListItems(owner, entryId, listItems(text)) }

    fun toggleItem(owner: String, entryId: String, itemId: String) = store.update { it.toggleItem(owner, entryId, itemId) }

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
    /** «Распознать» in the photo viewer: the text is kept on the photo itself (searchable, seen by the AI) and shown under it. */
    fun recognize(a: Attachment) {
        val s = settings.value
        if (s.parseMode == "local") { _toast.value = L.s.ocrLocal; return }
        if (!Processor.ready(s)) { _toast.value = L.s.noKey; return }
        if (!Processor.online(ctx)) { _toast.value = whyOff(); return }
        viewModelScope.launch {
            _working.value += "ocr:${a.id}"
            try {
                val jpeg = Files.jpeg(ctx, a) ?: return@launch
                val url = Files.imageDataUrl(ctx, a) ?: return@launch
                val r = ru.anjeib.margin.ai.Llm.ocr(s, jpeg, url)
                prefs.addSpend(r.rub)
                val text = r.content.trim()
                if (text.isBlank()) _toast.value = L.s.noTextOnPhoto else store.update { it.setOcr(a.id, text) }
            } catch (e: Exception) {
                _toast.value = (e as? AiException)?.message ?: e.message
            } finally {
                _working.value -= "ocr:${a.id}"
            }
        }
    }

    fun setOcr(attachmentId: String, text: String) = store.update { it.setOcr(attachmentId, text) }

    fun photoText(owner: String, entryId: String, a: Attachment, action: String) {
        val s = settings.value
        if (!Processor.ready(s)) { _toast.value = L.s.noKey; return }
        if (a.ocr == null && !Processor.online(ctx)) { _toast.value = whyOff(); return }
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
            ("type" to kotlinx.serialization.json.JsonPrimitive("person")) + ("keep" to kotlinx.serialization.json.JsonPrimitive("true")))), null, local = true)
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
        val e = data.value.entriesOf(owner).find { it.id == entryId } ?: return
        val urls = Regex("https?://[^\\s)>\\]]+").findAll(e.text).map { it.value.trimEnd('.', ',') }.filter { u -> e.attachments.none { it.url == u } }.toList().take(3)
        if (urls.isEmpty() || !Processor.online(ctx)) return
        viewModelScope.launch {
            urls.forEach { u ->
                val title = withContext(Dispatchers.IO) { pageTitle(u) } ?: android.net.Uri.parse(u).host ?: u
                store.update { it.addLink(owner, entryId, u, title) }
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
            "place" -> n.copy(place = v?.address())
            "project" -> n.copy(project = v?.removePrefix("#")?.lowercase())
            "remind" -> n.copy(remindAt = v)
            else -> n
        }
    }

    fun clearNotePeople(id: String) = updateNote(id) { it.copy(people = emptyList()) }

    /** «Список»: a new list block at the end of the note; [items] — what was already said («молоко, хлеб»). */
    fun addNoteList(id: String, items: List<String> = emptyList()) = store.update { it.addNoteList(id, items) }

    /** Items go to the last list of the note (a new one if there is none). */
    fun addNoteItems(id: String, text: String) = store.update { it.addNoteItems(id, listItems(text)) }

    fun toggleNoteItem(noteId: String, entryId: String, itemId: String) = store.update { it.toggleNoteItem(noteId, entryId, itemId) }

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
        val net = ru.anjeib.margin.ai.Cloud.network(ctx)
        val text = if (net) L.s.cloudSaved else L.s.offlineSaved
        if (scope == Scope.Main) addAssistant(ChatMsg(fromUser = false, text = text, caption = if (net) L.s.cloudOff else L.s.offline))
        else _notice.value = Notice(key(scope), text, null)
    }

    /** «В тему…»: the matter becomes one of the topic's matters, with a moment to take it back. */
    fun intoTopic(ref: String, topicId: String) {
        val before = data.value
        val title = before.tasks.find { it.id == topicId }?.title ?: return
        store.update { it.intoTopic(ref, topicId) }
        if (data.value != before) offerUndo(before, L.s.movedToTopic(title))
    }

    /** Removed from a list or a card: a simple matter or a topic's one — with «Отменить», like a swipe. */
    fun removeMatter(ref: String) {
        val before = data.value
        val q = questionRef(ref)
        store.update { d -> if (q != null) d.removeDeal(q.first, q.second) else d.deleteTask(ref) }
        offerUndo(before, L.s.deletedOne)
    }

    /** Taken out of the queue — with the same few seconds to take it back as everything else removed by a swipe. */
    fun dropQueued(id: String) {
        val before = data.value
        store.update { it.dropQueued(id) }
        offerUndo(before, L.s.deletedOne)
    }

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
        // Owner, 13 окт: something that went to another day — the calendar up top shows that day for a moment, a tap opens it there.
        val today = LocalDate.now()
        data.value.tasks.filter { it.id in ids }.mapNotNull { it.anchor }.filter { it.isAfter(today) }.minOrNull()?.let { day ->
            _landedDay.value = day
            viewModelScope.launch { kotlinx.coroutines.delay(4000); if (_landedDay.value == day) _landedDay.value = null }
        }
    }
    private val _landedDay = MutableStateFlow<LocalDate?>(null)
    /** The day something just went to, when it isn't today — the calendar up top shows it for a moment. */
    val landedDay: StateFlow<LocalDate?> = _landedDay

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
        if (_pending.value?.plan?.local == false) { count("ai:main"); count("undo:main") } else if (_pending.value != null) count("undo:phone")
        autoJob?.cancel()
        val p = _pending.value
        _pending.value = null
        _trayFrom.value = null
        p?.files?.takeIf { it.isNotEmpty() }?.let { fileAway(Scope.Main, it, data.value, null) }
    }

    /**
     * «В другую тему» / «В тему…»: the words were meant for [topicId]. The preview is dropped and the same words
     * are read again inside that topic, where they become its feed, questions and answers.
     */
    fun moveToTopic(topicId: String) {
        val p = _pending.value ?: return
        val title = data.value.tasks.find { it.id == topicId }?.title ?: return
        rejectPending()
        send(p.source, scope = Scope.InTask(topicId), files = p.files, movedHere = true)
        _toast.value = L.s.movedToTopic(title)
        land(listOf(topicId))
    }

    /** «Входящие» → «В тему…»: the block leaves the inbox and lands in the topic's feed, at its own time. */
    fun inboxToTopic(entryId: String, topicId: String) {
        val inbox = data.value.inboxNote() ?: return
        val e = inbox.entries.find { it.id == entryId } ?: return
        val title = data.value.tasks.find { it.id == topicId }?.title ?: return
        store.update { it.inboxToTopic(inbox.id, e.id, topicId) }
        _toast.value = L.s.movedToTopic(title)
        land(listOf(topicId))
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
        if (applied.caption != null) { snapshots[msg.id] = current; if (!plan.local) aiChanged(msg.id, "main") }
        store.replace(withFiles.copy(chat = (withFiles.chat + msg).takeLast(60)))
        tidyNewNotes(applied, current)
        nameNewTopics(current)
        if (silentUser) _trayFrom.value = msg.id
        return applied.touchedTasks
    }

    private fun applyScoped(plan: AiPlan, scope: Scope, files: List<Attachment>, source: String = "", movedHere: Boolean = false) {
        val current = data.value
        val applied = Assistant.apply(current, plan, source, strict = true, focus = (scope as? Scope.InTask)?.taskId)
        applySettings(applied.settings)
        var next = attachTo(applied, scope, files, current)
        // «В другую тему»: the words were moved here on purpose — they land in its feed whatever the model made of them.
        val fid = (scope as? Scope.InTask)?.taskId
        if (movedHere && fid != null && source.isNotBlank() && next.tasks.find { it.id == fid }?.entries?.size == current.tasks.find { it.id == fid }?.entries?.size)
            next = next.copy(tasks = next.tasks.map { if (it.id == fid) it.copy(entries = it.entries + Entry(text = source.sentence())) else it })
        val undoId = newId()
        snapshots[undoId] = current
        if (!plan.local && (applied.touchedTasks.isNotEmpty() || applied.touchedNotes.isNotEmpty())) aiChanged(undoId, if (scope is Scope.InTask) "task" else "notes")
        store.replace(next)
        tidyNewNotes(applied, current)
        nameNewTopics(current)
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

    // ---------------- how often the AI gets it wrong: an undo right after its change ----------------

    /** Undo ids of AI changes → where they happened (main / task / notes). */
    private val aiChanges = mutableMapOf<String, String>()
    private val stats get() = ctx.getSharedPreferences("stats", android.content.Context.MODE_PRIVATE)
    private fun count(key: String) { stats.edit().putInt(key, stats.getInt(key, 0) + 1).apply() }
    private fun aiChanged(id: String, where: String) { aiChanges[id] = where; count("ai:$where") }

    /** For beta reports: how much the phone read itself, how much went to the AI, how often each was undone, the AI's cost. */
    fun betaStats(): String {
        val st = stats
        val spent = settings.value.let { if (it.spentMonth == java.time.YearMonth.now().toString()) it.spentRub else 0.0 }
        return "parse: phone ${st.getInt("parse:phone", 0)} (undo ${st.getInt("undo:phone", 0)}) · ai ${st.getInt("parse:ai", 0)} (undo ${st.getInt("undo:main", 0)})" +
            " · misparsed ${Feedback.misparsedCount(ctx)} · ai ${"%.2f".format(spent)} ₽/month"
    }

    /** «Разобрало не так» on the result bubble: the words and what came of them go to the log for the developer. */
    fun misparsed(text: String, by: String, got: String) {
        Feedback.misparsed(ctx, Feedback.Misparse(at = java.time.LocalDateTime.now().toString().take(16), text = text, by = by, got = got))
        count("misparsed")
        _toast.value = L.s.misparsedThanks
    }

    /** The same from a result bubble: the words exactly as typed (marks included), not as the tags left them. */
    fun misparsed(p: Pending, got: String) =
        misparsed(lastSaid?.takeIf { p.source.endsWith(it.first) }?.second ?: p.source, if (p.plan.local) "phone" else "ai", got)

    /** The last words sent: without marks (what the parsers read) → as typed. */
    private var lastSaid: Pair<String, String>? = null

    /** «ИИ: 120 изменений, отменено 3» per place — read in the developer section. */
    fun aiStats(): List<Triple<String, Int, Int>> = listOf("main", "task", "notes").map { w -> Triple(w, stats.getInt("ai:$w", 0), stats.getInt("undo:$w", 0)) }

    fun undo(id: String) {
        aiChanges.remove(id)?.let { count("undo:$it") }
        val snap = snapshots.remove(id) ?: return
        store.restore { d ->
            snap.copy(chat = d.chat.map { if (it.id == id) it.copy(caption = L.s.undone, taskIds = emptyList(), options = emptyList()) else it })
        }
        if (_notice.value?.undoId == id) _notice.value = _notice.value?.copy(text = L.s.undone, undoId = null)
    }

    fun canUndo(id: String) = snapshots.containsKey(id)

    // ---------------- dictation ----------------

    /**
     * Turns a WAV recording into text. In the cloud (SpeechKit) while it answers — fast and accurate; on the phone (Whisper,
     * downloaded by itself over Wi-Fi) without a network, while the cloud is down, when the cloud fails on this very recording,
     * or always if the user keeps their voice on the phone. Calls back on the main thread.
     */
    fun transcribe(audio: Attachment, onText: (String) -> Unit) {
        val C = ru.anjeib.margin.ai.Cloud
        if (Processor.ready(settings.value) && C.network(ctx) && !C.down && !C.fresh()) viewModelScope.launch { C.check(settings.value); transcribeNow(audio, onText) }
        else transcribeNow(audio, onText)
    }

    private fun transcribeNow(audio: Attachment, onText: (String) -> Unit) {
        val s = settings.value
        val local = ru.anjeib.margin.voice.LocalSpeech
        val russian = L.lang == ru.anjeib.margin.i18n.Lang.Ru
        // GigaAM on the phone reads Russian better than the cloud did and in a fraction of a second, without the network:
        // it is the first choice whenever it is here. The cloud — for English, or while the model is still on its way.
        val canLocal = russian && local.ready(ctx)
        val cloud = Processor.online(ctx) && Processor.ready(s) && !s.whisperAlways
        if (!canLocal && !cloud) {
            _toast.value = when {
                local.state.value is ru.anjeib.margin.voice.LocalSpeech.State.Downloading -> L.s.whisperLoading
                russian && local.supported -> L.s.whisperOfferOffline
                else -> whyOff()
            }
            return
        }
        _working.value += "voice"
        viewModelScope.launch {
            try {
                val d = data.value
                val hints = (d.persons.map { it.name } + d.projects).distinct().take(40)
                val text = if (canLocal) Files.file(ctx, audio)?.let { local.transcribe(ctx, it, "ru") } ?: "" else {
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

    fun downloadSpeech() {
        viewModelScope.launch { ru.anjeib.margin.voice.LocalSpeech.ensure(ctx) }
    }

    // ---------------- tasks ----------------

    private fun updateTask(id: String, by: String = "you", f: (Task) -> Task) = store.update { it.editTask(id, by, f) }

    /** Done or open again; a repeating task leaves its next occurrence behind. */
    fun toggleTask(id: String) = store.update { it.toggleTask(id) }

    /** «Не буду делать»: closed without doing it — off the lists, honest in the history and reviews. A repeating one comes back as usual. */
    fun dropTask(id: String) {
        val before = data.value
        if (before.tasks.none { it.id == id && !it.done }) return
        store.update { it.dropTask(id) }
        offerUndo(before, L.s.droppedDone)
    }

    /**
     * «Сделать заметкой»: the reverse of «идея → задача» — for a thought the AI took for a task.
     * It joins its project's ideas note when there is one, else becomes a note of its own; the task goes away (with undo).
     */
    fun taskToNote(id: String): String? {
        val before = data.value
        if (before.tasks.none { it.id == id }) return null
        val ideas = if (L.s.aiLanguage == "English") "ideas" else "идеи"
        var noteId: String? = null
        store.update { d -> d.taskToNote(id, ideas)?.let { (next, nid) -> noteId = nid; next } ?: d }
        offerUndo(before, L.s.madeNote)
        return noteId
    }

    fun toggleSubtask(taskId: String, subId: String) = store.update { it.toggleSubtask(taskId, subId) }

    /** A topic's question from the lists or the questions page: settled (or back), with a moment to take it back. */
    fun toggleQuestion(taskId: String, subId: String) {
        val before = data.value
        val q = before.tasks.find { it.id == taskId }?.subtasks?.find { it.id == subId } ?: return
        toggleSubtask(taskId, subId)
        offerUndo(before, if (q.done) L.s.reopened else if (q.from != null) L.s.gotIt else L.s.solvedWord)
    }

    /** The hour of a task, one of its days or a topic's question («q:…»), set from the clock that opens on its time. */
    fun setTime(id: String, time: String?) = store.update { it.setTime(id, time) }

    /** The user's comments on a matter (or a topic's matter, «q:…»): written in the card, kept as is. */
    fun addComment(ref: String, text: String, at: String) = store.update { it.addComment(ref, text, at) }
    fun editComment(ref: String, id: String, text: String) = store.update { it.editComment(ref, id, text) }

    fun setQuestionDue(taskId: String, subId: String, due: String?, time: String? = null, start: String? = null) = store.update { it.setQuestionDue(taskId, subId, due, time, start) }

    /** «Не буду делать» on a topic's matter, with a moment to take it back. */
    fun dropDeal(topicId: String, subId: String) {
        val before = data.value
        store.update { it.dropQuestion(topicId, subId) }
        offerUndo(before, L.s.droppedDone)
    }

    fun setDealPromised(topicId: String, subId: String, who: String?) = store.update { it.setDealPromised(topicId, subId, who) }

    /** «В идеи» on a topic's matter: it leaves the topic and becomes an idea of its own, its comments its records. */
    fun dealToIdea(topicId: String, subId: String): String? {
        val before = data.value
        val topic = before.tasks.find { it.id == topicId } ?: return null
        fun flat(l: List<Subtask>): List<Subtask> = l.flatMap { listOf(it) + flat(it.children) }
        val q = flat(topic.subtasks).find { it.id == subId } ?: return null
        val idea = Task(title = q.title, topic = true, thought = true, sphere = topic.sphere, entries = q.comments.map { Entry(at = it.at, text = it.text) },
            history = listOf(ru.anjeib.margin.data.Change(kind = "created", by = "you")))
        store.update { d -> d.removeDeal(topicId, subId).purgeDeal(topicId, subId).let { it.copy(tasks = it.tasks + idea) } }
        offerUndo(before, L.s.madeNote)
        return idea.id
    }

    /** A topic's matter, the same «+ …» as a simple one: repeat, place, people — set by hand, no AI. */
    fun setDealRepeat(topicId: String, subId: String, r: ru.anjeib.margin.data.Repeat?) = store.update { it.setDealRepeat(topicId, subId, r) }
    fun setDealPlace(topicId: String, subId: String, place: String?) = store.update { it.setDealPlace(topicId, subId, place) }
    /** «Ждём от …» set or taken off by hand on a topic's matter. */
    fun setDealFrom(topicId: String, subId: String, who: String?) = store.update { d -> d.editDeal(topicId, subId) { it.copy(from = who?.trim()?.ifBlank { null }?.properName()) } }
    fun addDealPeople(topicId: String, subId: String, text: String) = store.update { it.addDealPeople(topicId, subId, ru.anjeib.margin.ai.Commands.items(text)) }

    /** «Решено · в архив»: the topic with its materials leaves the lists; news in it brings it back. */
    fun archiveTopic(id: String) {
        val before = data.value
        val t = before.tasks.find { it.id == id } ?: return
        store.update { it.archiveTopic(id) }
        offerUndo(before, L.s.archivedTopic(t.title))
    }

    fun reviveTopic(id: String) = store.update { it.reviveTopic(id) }

    /** «+ шаг» on a matter: the step becomes its first question, the matter grows into a topic. */
    fun addStep(id: String, text: String) {
        if (text.trim().isEmpty()) return
        store.update { it.addStep(id, text) }
    }

    /** What the marks in the words say: «/дело», «#тема», «@Маша», «/жду», «/обещал», «/готово», «/запись», «/работа». */
    data class Tags(
        val text: String, val kind: String? = null, val sphere: String? = null, val topicId: String? = null,
        val people: List<String> = emptyList(), val wait: Boolean = false, val promise: Boolean = false, val done: Boolean = false, val record: Boolean = false,
    ) {
        val explicit get() = kind != null || topicId != null || people.isNotEmpty() || wait || promise || record
    }

    private val slashRx = Regex("(?:^|\\s)/(дело|тема|идея|работа|личное|жду|обеща[лю]\\p{L}*|готово|запись|todo|topic|idea|work|personal|wait|promised|done|note)(?=\\s|$)", RegexOption.IGNORE_CASE)
    private val hashRx = Regex("(?:^|\\s)#(\\[[^\\]]+]|[\\p{L}\\d_-]+)")
    private val atRx = Regex("(?:^|\\s)@(\\[[^\\]]+]|[\\p{L}\\d_-]+)")

    fun parseTags(text: String): Tags {
        val d = data.value
        val tags = slashRx.findAll(text).map { it.groupValues[1].lowercase() }.toSet()
        fun has(vararg w: String) = tags.any { t -> w.any { t.startsWith(it) } }
        val topic = hashRx.find(text)?.groupValues?.get(1)?.let { d.topicFor(it) }
        val people = atRx.findAll(text).map { d.personFor(it.groupValues[1]) }.toList()
        // «позвонить @ЖЭК» keeps the name in the words; with «/жду» / «/обещал» the person is the other side, not the title.
        val sides = has("жду", "wait") || has("обеща", "promised")
        var t = text.replace(slashRx, " ").replace(hashRx) { m -> if (d.topicFor(m.groupValues[1]) != null) " " else m.value }
            .replace(atRx) { m -> if (sides) " " else " " + d.personFor(m.groupValues[1]) }
        t = t.replace(Regex("\\s{2,}"), " ").trim()
        return Tags(
            text = t,
            kind = when { has("дело", "todo") -> "task"; has("идея", "idea") -> "note"; has("тема", "topic") -> "topic"; else -> null },
            sphere = when { has("работа", "work") -> "work"; has("личное", "personal") -> "personal"; else -> null } ?: topic?.sphere,
            topicId = topic?.id, people = people, wait = has("жду", "wait"), promise = has("обеща", "promised"), done = has("готово", "done"), record = has("запись", "note"),
        )
    }

    /** «/готово»: the matter is ticked, words after «:» go into its topic's feed — no AI, works offline. */
    private fun doneByWords(tg: Tags, scope: Scope) {
        val topicId = tg.topicId ?: (scope as? Scope.InTask)?.taskId
        val what = tg.text.substringBefore(':')
        val said = tg.text.substringAfter(':', "").trim()
        val hit = data.value.let { d -> d.matterFor(what, topicId) ?: d.matterFor(what, null) }
        if (hit == null) { _toast.value = L.s.doneNotFound; return }
        completeWithUndo(hit.first)
        if (said.isNotEmpty()) {
            val owner = questionRef(hit.first)?.first ?: hit.first
            store.update { it.addRecord(owner, said) }
        }
        _toast.value = L.s.doneMarked(hit.second)
    }

    /** The line under the field while typing: what the phone understood, and whether the AI will refine it. */
    fun previewOf(text0: String): String? {
        val text = Commands.newTopic(text0)?.let { "/" + L.s.tagTopic + " " + it } ?: text0
        // Typing has begun: find out now whether the cloud answers — by the time the words are sent, it's known.
        if (text.isNotBlank() && Processor.ready(settings.value) && ru.anjeib.margin.ai.Cloud.network(ctx)) ru.anjeib.margin.ai.Cloud.warm(settings.value)
        val tg = parseTags(withSaidMarks(text))
        val t = tg.text
        val kind = tg.kind
        if (t.isBlank() || Commands.parse(t) != null) return null
        if (tg.done) return data.value.matterFor(t.substringBefore(':'), tg.topicId)?.let { L.s.previewDone(it.second) } ?: L.s.doneNotFound
        val topic = tg.topicId?.let { id -> data.value.tasks.find { it.id == id } }
        if (tg.record) return listOfNotNull(L.s.previewRecord, topic?.let { L.s.intoTopic(it.title) }).joinToString("  ·  ")
        val who = tg.people.firstOrNull()
        val r = runCatching { LocalParser.parse(t, data.value, kind = when (kind) { "note" -> "idea"; "topic" -> "topic"; "task" -> "task"; else -> null }, sphere = tg.sphere,
            people = tg.people, waitFor = who.takeIf { tg.wait }, promiseTo = who.takeIf { tg.promise }, wait = tg.wait, promise = tg.promise) }.getOrNull() ?: return null
        val line = (runCatching { LocalParser.describe(r) }.getOrNull() ?: return null) + (topic?.let { "  ·  " + L.s.intoTopic(it.title) } ?: "")
        if (tg.explicit) return line
        val s = settings.value
        val ai = s.parseMode != "local" && Processor.online(ctx) && Processor.ready(s) && (s.parseMode == "cloud" || (!r.sure && kind == null))
        return if (ai) line + "  ·  " + L.s.previewAi else line
    }

    /** The phone's own reading of words the AI is still working on — the same line as the live hint, without «уточнит ИИ». */
    fun draftOf(text: String): String? = runCatching { previewOf(text) }.getOrNull()
        ?.removeSuffix("  ·  " + L.s.previewAi)?.takeIf { it.isNotBlank() }

    // ---------------- «Пример данных» ----------------

    /** The example over the user's data; theirs is put aside first (once) and comes back with [restoreMine]. */
    fun loadDemo() {
        ru.anjeib.margin.data.Demo.markAsked(ctx)
        ru.anjeib.margin.data.Demo.keepMine(ctx, data.value)
        val demo = ru.anjeib.margin.data.Demo.build(ctx)
        store.restore { demo.copy(chat = emptyList()) }
    }

    fun restoreMine() {
        val mine = ru.anjeib.margin.data.Demo.takeMine(ctx) ?: return
        ru.anjeib.margin.data.Demo.dropFiles(ctx)
        store.restore { mine }
    }

    /** «Примеры» are Russian, like the demo they come from: an English interface gets none. */
    fun samplesHere() = L.lang == ru.anjeib.margin.i18n.Lang.Ru

    /** «Примеры»: one of each thing the app does, from the designer's demo — once, on a fresh start (not in the demo copy). */
    fun addIntroOnce() {
        val ui = ctx.getSharedPreferences("ui", android.content.Context.MODE_PRIVATE)
        if (ui.getBoolean("introAdded", false) || ru.anjeib.margin.BuildConfig.DEMO) return
        ui.edit().putBoolean("introAdded", true).apply()
        ui.edit().putInt("introVersion", INTRO_VERSION).apply()
        if (samplesHere() && data.value.tasks.none { !it.sample }) putSamples()
    }

    /** The topic's two files are drawn off the main thread; the set goes in as one change. */
    private fun putSamples() = viewModelScope.launch(kotlinx.coroutines.Dispatchers.Default) {
        val set = ru.anjeib.margin.data.Samples.withFiles(ctx)
        store.update { it.withSamples(set) }
    }

    /** Examples left from an older version (the old «Знакомство с Margin») are replaced with today's set. */
    fun refreshSamples() {
        val ui = ctx.getSharedPreferences("ui", android.content.Context.MODE_PRIVATE)
        if (ui.getInt("introVersion", 1) >= INTRO_VERSION) return
        ui.edit().putInt("introVersion", INTRO_VERSION).apply()
        if (data.value.hasSamples()) { if (samplesHere()) putSamples() else store.update { it.withoutSamples() } }
    }

    fun removeSamples() = store.update { it.withoutSamples() }
    fun bringSamples() { if (samplesHere()) putSamples() }

    fun demoAsked() = ru.anjeib.margin.data.Demo.asked(ctx)
    fun demoSkip() = ru.anjeib.margin.data.Demo.markAsked(ctx)
    fun hasMine() = ru.anjeib.margin.data.Demo.hasMine(ctx)

    // ---------------- the topic feed: «+ − 🔧» on a line ----------------

    /** «+» on a line of the feed: a new matter under it (another matter or a record), shown indented. */
    fun addChildDeal(topicId: String, parentId: String, text: String) {
        if (text.trim().isEmpty()) return
        store.update { it.addChildDeal(topicId, parentId, text) }
    }

    /** «−» on a matter: it leaves the counts and the lists but stays in the feed struck through (with what hangs under it). */
    fun removeDeal(topicId: String, subId: String) = store.update { it.removeDeal(topicId, subId) }
    fun restoreDeal(topicId: String, subId: String) = store.update { it.restoreDeal(topicId, subId) }
    fun purgeDeal(topicId: String, subId: String) = store.update { it.purgeDeal(topicId, subId) }

    /** «−» on a record of the feed: struck through, its files kept until removed for good. */
    fun removeRecord(topicId: String, entryId: String) = store.update { it.removeRecord(topicId, entryId) }
    fun restoreRecord(topicId: String, entryId: String) = store.update { it.restoreRecord(topicId, entryId) }

    fun purgeRecord(topicId: String, entryId: String) {
        data.value.tasks.find { it.id == topicId }?.removedEntries?.find { it.id == entryId }?.attachments?.forEach { Files.delete(ctx, it) }
        store.update { it.purgeRecord(topicId, entryId) }
    }

    fun setSphere(id: String, sphere: String?) = store.update { it.setSphere(id, sphere) }

    fun setOrg(personId: String, org: Boolean) = store.update { it.setOrg(personId, org) }

    fun setTopicLabel(id: String, label: String?) = store.update { it.setTopicLabel(id, label) }

    fun addSubtask(taskId: String, title: String, parent: String? = null) {
        if (title.sentence().isEmpty()) return
        store.update { it.addSubtask(taskId, title, parent) }
    }

    fun renameSubtask(taskId: String, subId: String, title: String) = store.update { it.renameSubtask(taskId, subId, title) }

    fun deleteSubtask(taskId: String, subId: String) = store.update { it.deleteSubtask(taskId, subId) }

    fun saveTask(t0: Task) {
        val t = data.value.namesFixed(t0, L.s.me)
        updateTask(t.id) { Assistant.withAutoReminder(t) }
        store.update { it.withMentionsOf(t, L.s.me) }
    }

    fun deleteTask(id: String) = store.update { it.deleteTask(id) }

    fun editTaskEntry(taskId: String, entryId: String, text: String) = store.update { it.editTaskEntry(taskId, entryId, text) }

    fun deleteTaskEntry(taskId: String, entryId: String) {
        data.value.tasks.find { it.id == taskId }?.entries?.find { it.id == entryId }?.attachments?.forEach { Files.delete(ctx, it) }
        store.update { it.deleteTaskEntry(taskId, entryId) }
    }

    /** «Отложить» to a chosen day: the task moves there whole (a range keeps its length). */
    fun postponeTo(id: String, day: LocalDate) {
        val before = data.value
        if (slotRef(id) == null && questionRef(id) == null && before.tasks.none { it.id == id }) return
        store.update { it.postponeTo(id, day) }
        offerUndo(before, L.s.movedTo(day.shortLabel().lowercase()))
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

    // ---------------- several days of one matter («<row>@<slot>») ----------------

    /** Ticks one day of a matter done on several days; all ticked — the matter is done. */
    fun toggleSlot(base: String, slotId: String) {
        val before = data.value
        store.update { it.toggleSlot(base, slotId) }
        offerUndo(before, L.s.markedDone)
    }

    /** «+ день»: one more day for the matter; its single date becomes the first of the days. */
    fun addSlot(taskId: String, date: String) = store.update { it.addSlot(taskId, date) }

    fun removeSlot(taskId: String, slotId: String) = store.update { it.removeSlot(taskId, slotId) }

    /** Swipe-right: done, with a few seconds to take it back. */
    fun completeWithUndo(id: String) {
        slotRef(id)?.let { (base, sid) -> toggleSlot(base, sid); return }
        questionRef(id)?.let { (tid, qid) -> toggleQuestion(tid, qid); return }
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
        store.restore { d -> snap.copy(chat = d.chat) }
    }
    private var undoJob: kotlinx.coroutines.Job? = null

    fun moveOverdue() {
        val before = data.value
        val today = LocalDate.now()
        val n = before.overdueCount(today)
        if (n == 0) { _toast.value = L.s.nothingOverdue; return }
        store.update { it.moveOverdue(today) }
        offerUndo(before, L.s.movedOverdue(n))
    }

    // ---------------- notes ----------------

    fun newEmptyNote(): String {
        val n = Note(title = "")
        store.update { it.copy(notes = listOf(n) + it.notes) }
        return n.id
    }

    private fun updateNote(id: String, f: (Note) -> Note) = store.update { it.editNote(id, f) }

    fun renameNote(id: String, title: String) = store.update { it.renameNote(id, title) }
    fun setNoteProject(id: String, project: String?) = store.update { it.setNoteProject(id, project) }

    /** Adds a dated entry to a note directly (inside the note the user is the author, no AI needed). */
    fun appendToNote(id: String, text: String, files: List<Attachment> = emptyList()) {
        if (text.isBlank() && files.isEmpty()) return
        val e = stamp("note:$id", Entry(text = text.sentence(), attachments = files))
        store.update { it.appendToNote(id, e) }
        // The new block is the last one: give its links titles, its photos and voice notes their text.
        data.value.notes.find { it.id == id }?.entries?.lastOrNull()?.let { e -> linkTitles("note:$id", e.id); e.attachments.forEach { enrich("note:$id", e.id, it) } }
    }

    fun editNoteEntry(noteId: String, entryId: String, text: String) = store.update { it.editNoteEntry(noteId, entryId, text) }

    fun deleteNoteEntry(noteId: String, entryId: String) {
        data.value.notes.find { it.id == noteId }?.entries?.find { it.id == entryId }?.attachments?.forEach { Files.delete(ctx, it) }
        store.update { it.deleteNoteEntry(noteId, entryId) }
    }

    // ---------------- swipes: archive and delete, each with «Отменить» ----------------

    fun archiveNote(id: String, on: Boolean = true) {
        val before = data.value
        store.update { it.archiveNote(id, on) }
        offerUndo(before, if (on) L.s.archivedOne else L.s.restoredOne)
    }

    /** «+ контакт»: a draft (or a bare name) becomes a contact. */
    fun keepContact(name: String) {
        store.update { it.keepContact(name) }
        _contactOffer.value = null
        _toast.value = L.s.contactMade(name)
    }

    private val _openPerson = MutableStateFlow<String?>(null)
    /** A contact's name was tapped somewhere: the app opens their page. */
    val openPersonReq: StateFlow<String?> = _openPerson
    fun personOpened() { _openPerson.value = null }
    fun nameTapped(name: String) {
        val p = data.value.contact(name)
        if (p != null) _openPerson.value = p.id else offerContact(name)
    }

    private val _contactOffer = MutableStateFlow<String?>(null)
    /** A name that isn't a contact was tapped: a quiet bar offers «+ контакт». */
    val contactOffer: StateFlow<String?> = _contactOffer
    fun offerContact(name: String?) {
        _contactOffer.value = name
        if (name != null) viewModelScope.launch { kotlinx.coroutines.delay(6000); if (_contactOffer.value == name) _contactOffer.value = null }
    }

    fun archivePerson(id: String, on: Boolean = true) {
        val before = data.value
        store.update { it.archivePerson(id, on) }
        offerUndo(before, if (on) L.s.archivedOne else L.s.restoredOne)
    }

    /** Deleting by swipe keeps the files until the undo window has passed — so «Отменить» brings everything back. */
    fun removeNote(id: String) {
        val before = data.value
        store.update { it.removeNote(id) }
        offerUndo(before, L.s.deletedOne)
        val files = before.notes.find { it.id == id }?.entries?.flatMap { it.attachments }.orEmpty()
        viewModelScope.launch { kotlinx.coroutines.delay(9000); if (data.value.notes.none { it.id == id }) files.forEach { Files.delete(ctx, it) } }
    }

    fun removePerson(id: String) {
        val before = data.value
        store.update { it.removePerson(id) }
        offerUndo(before, L.s.deletedOne)
    }

    fun removeEntry(owner: String, id: String) {
        val before = data.value
        val files = data.value.entriesOf(owner).find { it.id == id }?.attachments.orEmpty()
        store.update { it.removeEntry(owner, id) }
        offerUndo(before, L.s.deletedOne)
        viewModelScope.launch { kotlinx.coroutines.delay(9000); if (data.value.entriesOf(owner).none { it.id == id }) files.forEach { Files.delete(ctx, it) } }
    }

    fun deleteNote(id: String) {
        data.value.notes.find { it.id == id }?.entries?.flatMap { it.attachments }?.forEach { Files.delete(ctx, it) }
        store.update { it.removeNote(id) }
    }

    /** Removes notes left completely empty (e.g. "new note" opened and closed). */
    fun dropEmptyNote(id: String) = store.update { it.dropEmptyNote(id) }

    // ---------------- people ----------------

    private fun updatePerson(id: String, f: (Person) -> Person) = store.update { it.editPerson(id, f) }

    /** One contact or fact of a person, typed by hand. «personal» takes one fact per line. */
    fun setPersonField(id: String, field: String, value: String) = store.update { it.setPersonField(id, field, value) }

    fun say(text: String) { _toast.value = text }

    private fun tidyNewNotes(applied: Applied, before: AppData) =
        applied.touchedNotes.filter { id -> before.notes.none { it.id == id } }.forEach { tidyNote(it) }

    /** A note that is all title gets its spelling and capitals fixed once, by the quick model. */
    fun tidyNote(id: String) {
        val s = settings.value
        // v2: a thought is a topic now — its title is tidied the same way.
        data.value.tasks.find { it.id == id && it.thought }?.let { t -> tidyThought(t); return }
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

    private fun tidyThought(t: Task) {
        val s = settings.value
        val id = t.id
        if (t.titleFixed || t.title.isBlank() || t.title.length > 60 || !Processor.ready(s) || !Processor.online(ctx) || "tidy:$id" in _working.value) return
        _working.value += "tidy:$id"
        viewModelScope.launch {
            try {
                val r = ru.anjeib.margin.ai.Llm.complete(s, Assistant.tidyPrompt(), listOf("user" to t.title))
                prefs.addSpend(r.rub)
                val clean = (Assistant.field(Assistant.parseObject(r.content), "text") ?: r.content).trim().trim('"').trim()
                val ok = clean.length in (t.title.length * 8 / 10)..(t.title.length * 13 / 10 + 8) && !clean.contains('{')
                updateTask(id) { it.copy(title = if (ok) clean else it.title, titleFixed = true) }
            } catch (e: Exception) {
                android.util.Log.w("Margin", "tidy thought failed: ${e.message}")
            } finally {
                _working.value -= "tidy:$id"
            }
        }
    }

    /**
     * A simple task just grew into a topic and the model didn't name the matter: the quick model names it,
     * once, from the task and the words that turned it («Позвонить Игорю насчёт приказа» → «Приказ о зачислении»).
     */
    private fun nameNewTopics(before: AppData) {
        val s = settings.value
        val grown = data.value.tasks.filter { t -> t.topic && !t.thought && before.tasks.find { it.id == t.id }?.let { o -> !o.topic && o.title == t.title } == true }
        grown.forEach { t ->
            if (!Processor.ready(s) || !Processor.online(ctx) || "name:${t.id}" in _working.value) return@forEach
            _working.value += "name:${t.id}"
            viewModelScope.launch {
                try {
                    val words = t.entries.lastOrNull { it.text.isNotBlank() }?.text.orEmpty()
                    val r = ru.anjeib.margin.ai.Llm.complete(s, Assistant.topicNamePrompt(), listOf("user" to "Task: ${t.title}\nNews: $words"))
                    prefs.addSpend(r.rub)
                    // JSON as asked, or — from a model that answered in plain words — its first line.
                    val name = (Assistant.field(Assistant.parseObject(r.content), "title") ?: r.content.lineSequence().firstOrNull { it.isNotBlank() } ?: "")
                        .trim().trim('"', '«', '»', '.', '{', '}').trim()
                    if (name.length in 3..48 && !name.contains('{')) updateTask(t.id) { cur -> if (cur.title == t.title) cur.copy(title = name.sentence()) else cur }
                } catch (e: Exception) {
                    android.util.Log.w("Margin", "topic name failed: ${e.message}")
                } finally {
                    _working.value -= "name:${t.id}"
                }
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
        if (newName.properName().isEmpty()) return
        store.update { it.renamePerson(id, newName) }
    }

    /** Merges [fromId] into [intoId]: one person, all mentions point to the survivor. */
    fun mergePersons(fromId: String, intoId: String) = store.update { it.mergePersons(fromId, intoId) }

    fun deletePerson(id: String) = store.update { it.removePerson(id) }

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
                    listOf("user" to Assistant.ideaPrompt(noteId, e.text, note.title.takeIf { entryId != null })))
                prefs.addSpend(cost)
                val current = data.value
                val applied = Assistant.apply(current, plan.copy(question = null), e.text)
                val created = applied.touchedTasks.filter { tid -> current.tasks.none { it.id == tid } }
                store.replace(applied.data.copy(tasks = applied.data.tasks.map {
                    if (it.id in created) it.copy(noteId = noteId, sourceEntryId = entryId, source = e.text.take(240)).let { t -> t.bareFromNote(applied.data)?.let { b -> t.copy(title = b) } ?: t } else it
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
        store.update { it.rememberContact(c) }
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
