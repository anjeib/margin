package ru.anjeib.margin.ai

import android.content.Context
import kotlinx.coroutines.sync.withLock
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import ru.anjeib.margin.data.AppData
import ru.anjeib.margin.data.Attachment
import ru.anjeib.margin.data.Entry
import ru.anjeib.margin.data.Files
import ru.anjeib.margin.data.Note
import ru.anjeib.margin.data.Prefs
import ru.anjeib.margin.data.Queued
import ru.anjeib.margin.data.sentence
import ru.anjeib.margin.data.Store
import ru.anjeib.margin.data.nowStamp
import ru.anjeib.margin.i18n.L
import ru.anjeib.margin.i18n.resolveLang
import ru.anjeib.margin.notify.Reminders

/** Shared plumbing: where attachments land, the Inbox note, and the offline queue. */
object Processor {

    /** The AI can be asked now: the phone has a network and Margin's cloud answers (see [Cloud]). */
    fun online(ctx: Context): Boolean = Cloud.network(ctx) && !Cloud.down

    fun ready(s: ru.anjeib.margin.data.Settings) = Llm.ready(s)

    fun scopeOf(s: Scope) = when (s) { Scope.Main -> "main"; Scope.Notes -> "notes"; is Scope.InTask -> "task:" + s.taskId }
    fun scopeFrom(k: String): Scope = when {
        k == "notes" -> Scope.Notes
        k.startsWith("task:") -> Scope.InTask(k.removePrefix("task:"))
        else -> Scope.Main
    }

    fun inbox(d: AppData, e: Entry): AppData {
        val title = L.s.inboxNote
        val existing = d.notes.find { it.title == title }
        return if (existing != null) d.copy(notes = listOf(existing.copy(entries = existing.entries + e, updatedAt = nowStamp())) + d.notes.filterNot { it.id == existing.id })
        else d.copy(notes = listOf(Note(title = title, entries = listOf(e))) + d.notes)
    }

    /**
     * Without a cloud AI (owner, 14 окт): whatever comes in from another app — shared, picked from a selection, copied — goes to
     * «Входящие» as it is, with where it came from; it is sorted there by hand («В дело», «В заметку», «В тему», «Разобрать»).
     */
    fun toInbox(ctx: Context, q: Queued) {
        val words = listOfNotNull(q.comment?.takeIf { it.isNotBlank() }, q.text.trim().takeIf { it.isNotBlank() }).joinToString("\n\n")
        val from = listOfNotNull(q.from, q.via?.let { L.s.forwardFrom(Forward.viaName(it)) }).joinToString(", ").ifBlank { null }
        Store.get(ctx).update { inbox(it, Entry(text = words.take(4000), attachments = q.files, from = from)) }
    }

    /** Puts attachments where the AI's changes landed: the touched task / note, else the scope's default place. */
    fun attachTo(applied: Applied, scope: Scope, files: List<Attachment>, before: AppData): AppData {
        if (files.isEmpty()) return applied.data
        var d = applied.data
        val taskId = (scope as? Scope.InTask)?.taskId ?: applied.touchedTasks.firstOrNull()
        val noteId = if (taskId == null) applied.touchedNotes.firstOrNull() else null
        when {
            taskId != null && d.tasks.any { it.id == taskId } -> d = d.copy(tasks = d.tasks.map { t ->
                if (t.id != taskId) t else {
                    val old = before.tasks.find { it.id == t.id }?.entries?.map { it.id }?.toSet() ?: emptySet()
                    val fresh = t.entries.lastOrNull { it.id !in old }
                    if (fresh != null) t.copy(entries = t.entries.map { if (it.id == fresh.id) it.copy(attachments = it.attachments + files) else it })
                    else t.copy(entries = t.entries + Entry(attachments = files))
                }
            })
            noteId != null -> d = d.copy(notes = d.notes.map { n ->
                if (n.id != noteId) n else {
                    val old = before.notes.find { it.id == n.id }?.entries?.map { it.id }?.toSet() ?: emptySet()
                    val fresh = n.entries.lastOrNull { it.id !in old }
                    if (fresh != null) n.copy(entries = n.entries.map { if (it.id == fresh.id) it.copy(attachments = it.attachments + files) else it })
                    else n.copy(entries = n.entries + Entry(attachments = files))
                }
            })
            else -> d = inbox(d, Entry(attachments = files))
        }
        return d
    }

    /**
     * One model call that must yield a plan. A reply that isn't parseable JSON (it happens with small models)
     * is asked once more before giving up. Returns the plan and the cost in USD.
     */
    suspend fun askPlan(s: ru.anjeib.margin.data.Settings, system: String, messages: List<Pair<String, String>>,
                        smart: Boolean = false, complex: Boolean = false, images: List<String> = emptyList()): Pair<AiPlan, Double> {
        var rub = 0.0
        repeat(2) { attempt ->
            val r = try { Llm.complete(s, system, messages, smart = smart, complex = complex, images = images) } catch (e: AiException) {
                // An empty reply happens now and then (DeepSeek mostly): ask once more, then say it plainly.
                if (e.message == EMPTY) { if (attempt == 0) return@repeat else throw AiException(L.s.errEmpty) } else throw e
            }
            rub += r.rub
            if (!Assistant.isBroken(r.content) || attempt == 1) return Assistant.parsePlan(r.content) to rub
        }
        throw AiException(L.s.errEmpty)
    }

    fun enqueue(ctx: Context, q: Queued) {
        Store.get(ctx).update { it.copy(queue = it.queue + q) }
        schedule(ctx)
    }

    fun schedule(ctx: Context) {
        if (Store.get(ctx).data.value.queue.isEmpty()) return
        // Read on the phone («Телефон»): no network to wait for.
        val local = Prefs.get(ctx).settings.value.parseMode == "local"
        WorkManager.getInstance(ctx).enqueueUniqueWork(
            "queue", ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<QueueWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(if (local) NetworkType.NOT_REQUIRED else NetworkType.CONNECTED).build())
                .build(),
        )
    }

    private val theyWill = Regex("(?<![\\p{L}])(?:пришлю|пришлём|отправлю|скину|сделаю|сделаем|подготовлю|вышлю|передам|верну|отвечу|позвоню|наберу|напишу|принесу|привезу|оплачу|согласую|проверю|посмотрю)(?![\\p{L}])", RegexOption.IGNORE_CASE)
    private val theyAsk = Regex("(?<![\\p{L}])(?:скинь|скиньте|пришли|пришлите|отправь|отправьте|сделай|сделайте|подготовь|подготовьте|позвони|позвоните|напиши|напишите|посмотри|посмотрите|проверь|проверьте|оплати|оплатите|согласуй|верни|верните|передай|передайте|не\\s+забудь)(?![\\p{L}])", RegexOption.IGNORE_CASE)
    private val smallTalk = Regex("^(?:привет\\p{L}*|здравствуй\\p{L}*|добр\\p{L}+\\s+(?:утро|день|вечер)|спасибо\\p{L}*|пока|ок|окей|хорошо|отлично|договорились|супер|да|нет)[!.,)\\s]*$", RegexOption.IGNORE_CASE)

    /**
     * «Телефон»: a queued phrase or a forwarded message read on the phone. Someone else's words are read sentence by sentence:
     * what the writer says they'll do («правки пришлю в среду») is waited for from them; what they ask of the user
     * («скинь до пятницы презентацию») is the user's matter promised to them; greetings and thanks are left out.
     */
    fun localPlan(q0: Queued, d: AppData, scope: Scope): AiPlan {
        // «Марина Петрова: Привет! …» shared as plain text — the name in front is who wrote it.
        val named = if (q0.via != null && q0.from == null) Regex("^([А-ЯЁA-Z][\\p{L}-]+(?:\\s+[А-ЯЁA-Z][\\p{L}-]+)?)\\s*:\\s+(.+)$", RegexOption.DOT_MATCHES_ALL).find(q0.text.trim()) else null
        val q = named?.let { q0.copy(from = it.groupValues[1], text = it.groupValues[2]) } ?: q0
        val from = q.from
        if (q.via == null || from == null) {
            val r = LocalParser.parse(q.text, d)
            return if (r.items.isNotEmpty()) LocalParser.plan(r, scope, source = q.text)
            else AiPlan(null, listOf(kotlinx.serialization.json.JsonObject(mapOf("type" to kotlinx.serialization.json.JsonPrimitive("create_task"),
                "title" to kotlinx.serialization.json.JsonPrimitive(q.text.trim().sentence())))), null, local = true)
        }
        val items = q.text.split(Regex("(?<=[.!?])\\s+|\\n+")).map { it.trim() }.filter { it.isNotBlank() && !smallTalk.matches(it) }
            .flatMap { part ->
                val sentence = part.replace(Regex("^(?:а|и|но|ещё|еще|кстати)[,\\s]+(?:ты\\s+|вы\\s+)?", RegexOption.IGNORE_CASE), "").replace(Regex(",?\\s*пожалуйста,?", RegexOption.IGNORE_CASE), "").trim()
                when {
                    theyWill.containsMatchIn(sentence) -> LocalParser.parse(sentence, d, people = listOf(from), waitFor = from, wait = true).items
                    theyAsk.containsMatchIn(sentence) -> LocalParser.parse(sentence, d, people = listOf(from), promiseTo = from, promise = true).items
                    else -> emptyList()
                }
            }
        if (items.isEmpty()) return AiPlan(null, emptyList(), null, local = true)
        return LocalParser.plan(LocalParser.Result(items, true), scope, source = Forward.source(q))
    }

    /** Sorts everything typed offline or forwarded in, oldest first. Returns one line per item: what it became. */
    private val draining = kotlinx.coroutines.sync.Mutex()

    /** The app (forward while open) and the worker may both start sorting: one at a time, never the same item twice. */
    suspend fun drain(ctx: Context): List<String> = drainItems(ctx).map { it.line }

    /** What one queued item became: the line for the user and what to open (the matter or topic it touched). */
    data class Sorted(val queueId: String, val line: String, val open: String?)

    suspend fun drainItems(ctx: Context): List<Sorted> = draining.withLock { drainNow(ctx) }

    private suspend fun drainNow(ctx: Context): List<Sorted> {
        val store = Store.get(ctx)
        val prefs = Prefs.get(ctx)
        L.lang = resolveLang(prefs.settings.value.lang)
        val done = mutableListOf<Sorted>()
        while (true) {
            val q = store.data.value.queue.firstOrNull() ?: break
            val s = prefs.settings.value
            val local = s.parseMode == "local"
            if (!local && !Llm.ready(s)) break
            val scope = scopeFrom(q.scope)
            val before = store.data.value
            val images = q.files.filter { it.kind == "photo" }.take(3).mapNotNull { Files.imageDataUrl(ctx, it) }
            // Forwarded words are someone else's: the AI sees who wrote them; the task keeps who said it.
            val forwarded = q.via != null
            val said = if (forwarded) Forward.prompt(q) else q.text
            val source = if (forwarded) Forward.source(q) else q.text
            val (plan, cost) = if (local) localPlan(q, before, scope) to 0.0 else askPlan(s, Assistant.systemPrompt(before, s.copy(askWhenUnclear = false), scope, query = q.text + " " + (q.from ?: "") + " " + (q.comment ?: "")),
                listOf("user" to Assistant.userMessage(said)), complex = forwarded || Assistant.isComplex(q.text, scope), images = images)
            prefs.addSpend(cost)
            val current = store.data.value
            val applied = Assistant.apply(current, plan.copy(question = null), source, strict = true)
            // Nothing came of a forward with files → keep the files in «Входящие» rather than lose them.
            // (When the AI answered why nothing changed — «такая задача уже есть» — its answer is enough.)
            val next = if (applied.touchedTasks.isEmpty() && applied.touchedNotes.isEmpty() && (q.files.isNotEmpty() || (forwarded && plan.reply.isNullOrBlank())))
                inbox(applied.data, Entry(text = source.take(4000), attachments = q.files))
            else attachTo(applied, scope, q.files, current)
            store.replace(next.copy(queue = next.queue.filterNot { it.id == q.id }))
            val said2 = Forward.summary(current, applied, plan)
            q.reply?.let { Forward.reply(ctx, it, said2) }
            done += Sorted(q.id, said2, applied.touchedTasks.firstOrNull { id -> next.tasks.any { it.id == id } })
        }
        return done
    }
}

class QueueWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result = try {
        val done = Processor.drain(applicationContext)
        if (done.isNotEmpty()) Reminders.show(applicationContext, 4242, L.s.queueDoneText(done.size), done.joinToString("\n").take(600), digest = true)
        Result.success()
    } catch (e: Exception) {
        // The cloud may be down for hours while the phone is online: keep trying (WorkManager spaces the tries out).
        // Anything else (the service refused this item) — a few tries, as before.
        if (e.message == L.s.errNet || runAttemptCount < 5) Result.retry() else Result.failure()
    }
}
