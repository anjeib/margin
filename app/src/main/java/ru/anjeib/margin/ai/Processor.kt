package ru.anjeib.margin.ai

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
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
import ru.anjeib.margin.data.Store
import ru.anjeib.margin.data.nowStamp
import ru.anjeib.margin.i18n.L
import ru.anjeib.margin.i18n.resolveLang
import ru.anjeib.margin.notify.Reminders

/** Shared plumbing: where attachments land, the Inbox note, and the offline queue. */
object Processor {

    fun online(ctx: Context): Boolean {
        val cm = ctx.getSystemService(ConnectivityManager::class.java) ?: return true
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

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
        WorkManager.getInstance(ctx).enqueueUniqueWork(
            "queue", ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<QueueWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build(),
        )
    }

    /** Sorts everything typed offline, oldest first. Returns how many items were processed. */
    suspend fun drain(ctx: Context): Int {
        val store = Store.get(ctx)
        val prefs = Prefs.get(ctx)
        L.lang = resolveLang(prefs.settings.value.lang)
        var done = 0
        while (true) {
            val q = store.data.value.queue.firstOrNull() ?: break
            val s = prefs.settings.value
            if (!Llm.ready(s)) break
            val scope = scopeFrom(q.scope)
            val before = store.data.value
            val images = q.files.filter { it.kind == "photo" }.take(3).mapNotNull { Files.imageDataUrl(ctx, it) }
            val (plan, cost) = askPlan(s, Assistant.systemPrompt(before, s.copy(askWhenUnclear = false), scope, query = q.text),
                listOf("user" to Assistant.userMessage(q.text)), complex = Assistant.isComplex(q.text, scope), images = images)
            prefs.addSpend(cost)
            val current = store.data.value
            val applied = Assistant.apply(current, plan.copy(question = null), q.text, strict = true)
            val next = attachTo(applied, scope, q.files, current)
            store.replace(next.copy(queue = next.queue.filterNot { it.id == q.id }))
            done++
        }
        return done
    }
}

class QueueWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result = try {
        val n = Processor.drain(applicationContext)
        if (n > 0) Reminders.show(applicationContext, 4242, L.s.queueDoneTitle, L.s.queueDoneText(n))
        Result.success()
    } catch (e: Exception) {
        if (runAttemptCount < 5) Result.retry() else Result.failure()
    }
}
