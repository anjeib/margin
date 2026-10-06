package ru.anjeib.margin.notify

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import ru.anjeib.margin.MainActivity
import ru.anjeib.margin.R
import ru.anjeib.margin.data.Prefs
import ru.anjeib.margin.data.Store
import ru.anjeib.margin.data.Task
import ru.anjeib.margin.data.logged
import ru.anjeib.margin.data.nextOccurrence
import ru.anjeib.margin.data.whenLabel
import ru.anjeib.margin.i18n.L
import ru.anjeib.margin.i18n.resolveLang
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

object Reminders {
    private const val CHANNEL = "reminders"
    private const val KIND = "kind"
    private const val TASK = "taskId"
    private const val REQ_MORNING = 1_000_001
    private const val REQ_EVENING = 1_000_002

    private fun am(ctx: Context) = ctx.getSystemService(AlarmManager::class.java)
    private fun scheduledPrefs(ctx: Context) = ctx.getSharedPreferences("alarms", Context.MODE_PRIVATE)

    private fun intent(ctx: Context, kind: String, taskId: String? = null) =
        Intent(ctx, AlarmReceiver::class.java).putExtra(KIND, kind).apply { taskId?.let { putExtra(TASK, it) } }
            .setAction("margin.$kind.${taskId ?: ""}")

    private fun pi(ctx: Context, req: Int, i: Intent, flags: Int = 0) =
        PendingIntent.getBroadcast(ctx, req, i, PendingIntent.FLAG_IMMUTABLE or flags)

    /** Exact when the system allows it (reminders must not drift), inexact otherwise. */
    private fun at(ctx: Context, millis: Long, p: PendingIntent) {
        val m = am(ctx)
        val exact = Build.VERSION.SDK_INT < 31 || m.canScheduleExactAlarms()
        if (exact) m.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, p)
        else m.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, p)
    }

    private fun LocalDateTime.millis() = atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    /** Schedules a reminder for every open task (and note) with a future remindAt; cancels the rest. Notes go as «note:<id>». */
    fun syncTasks(ctx: Context, tasks: List<Task>, notes: List<ru.anjeib.margin.data.Note> = Store.get(ctx).data.value.notes) {
        val now = LocalDateTime.now()
        val wanted = (tasks.mapNotNull { t ->
            val at = t.remindAt?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() } ?: return@mapNotNull null
            if (t.done || at.isBefore(now)) null else t.id to at
        } + notes.mapNotNull { n ->
            val at = n.remindAt?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() } ?: return@mapNotNull null
            if (at.isBefore(now)) null else "note:${n.id}" to at
        }).toMap()
        val sp = scheduledPrefs(ctx)
        val before = sp.all.mapValues { it.value as? String }
        // cancel removed / changed
        before.forEach { (id, whenStr) ->
            if (wanted[id]?.toString() != whenStr) {
                pi(ctx, id.hashCode(), intent(ctx, "task", id), PendingIntent.FLAG_UPDATE_CURRENT).let { am(ctx).cancel(it) }
            }
        }
        wanted.forEach { (id, time) ->
            if (before[id] != time.toString())
                at(ctx, time.millis(), pi(ctx, id.hashCode(), intent(ctx, "task", id), PendingIntent.FLAG_UPDATE_CURRENT))
        }
        sp.edit { clear(); wanted.forEach { (id, t) -> putString(id, t.toString()) } }
    }

    fun scheduleDaily(ctx: Context) {
        val s = Prefs.get(ctx).settings.value
        fun next(time: String): LocalDateTime {
            val t = runCatching { LocalTime.parse(time) }.getOrDefault(LocalTime.of(8, 30))
            val today = LocalDate.now().atTime(t)
            return if (today.isAfter(LocalDateTime.now())) today else today.plusDays(1)
        }
        val m = pi(ctx, REQ_MORNING, intent(ctx, "morning"), PendingIntent.FLAG_UPDATE_CURRENT)
        if (s.morningBrief) at(ctx, next(s.morningTime).millis(), m) else am(ctx).cancel(m)
        val e = pi(ctx, REQ_EVENING, intent(ctx, "evening"), PendingIntent.FLAG_UPDATE_CURRENT)
        if (s.eveningReview) at(ctx, next(s.eveningTime).millis(), e) else am(ctx).cancel(e)
    }

    fun rescheduleAll(ctx: Context) {
        scheduledPrefs(ctx).edit { clear() }
        syncTasks(ctx, Store.get(ctx).data.value.tasks)
        scheduleDaily(ctx)
    }

    fun ensureChannel(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, L.s.notifChannel, NotificationManager.IMPORTANCE_DEFAULT))
    }

    fun show(ctx: Context, id: Int, title: String, text: String?, taskId: String? = null) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        ensureChannel(ctx)
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_notify)
            .setColor(0xFFC8643B.toInt())
            .setContentTitle(title)
            .apply { if (!text.isNullOrBlank()) setContentText(text).setStyle(NotificationCompat.BigTextStyle().bigText(text)) }
            .setContentIntent(open)
            .setAutoCancel(true)
            .apply {
                if (taskId != null) {
                    fun act(kind: String, req: Int) = PendingIntent.getBroadcast(ctx, taskId.hashCode() + req,
                        intent(ctx, kind, taskId), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                    addAction(0, L.s.markDone, act("done", 1))
                    addAction(0, L.s.snoozeHour, act("snooze", 2))
                }
            }
            .build()
        NotificationManagerCompat.from(ctx).notify(id, n)
    }

    internal fun fire(ctx: Context, i: Intent) {
        L.lang = resolveLang(Prefs.get(ctx).settings.value.lang)
        val data = Store.get(ctx).data.value
        val today = LocalDate.now()
        when (i.getStringExtra(KIND)) {
            "task" -> {
                val key = i.getStringExtra(TASK) ?: return
                // A note's reminder: its title and the last thing written in it.
                if (key.startsWith("note:")) {
                    val n = data.notes.find { it.id == key.removePrefix("note:") } ?: return
                    show(ctx, key.hashCode(), n.title.ifBlank { L.s.untitled }, n.entries.lastOrNull { it.text.isNotBlank() }?.text?.take(120).orEmpty())
                    return
                }
                val t = data.tasks.find { it.id == key } ?: return
                if (t.done) return
                show(ctx, t.id.hashCode(), t.title, listOfNotNull(t.whenLabel(), t.waitingFor?.let { L.s.waitingFor(it) }).joinToString(" · "), t.id)
            }
            "done" -> {
                val id = i.getStringExtra(TASK) ?: return
                Store.get(ctx).update { d ->
                    val t = d.tasks.find { it.id == id && !it.done } ?: return@update d
                    d.copy(tasks = d.tasks.map { if (it.id == id) it.copy(done = true, doneAt = today.toString()).logged(it, "you") else it } + listOfNotNull(t.nextOccurrence()))
                }
                NotificationManagerCompat.from(ctx).cancel(id.hashCode())
                syncTasks(ctx, Store.get(ctx).data.value.tasks)
            }
            "snooze" -> {
                val id = i.getStringExtra(TASK) ?: return
                val at = LocalDateTime.now().plusHours(1).withSecond(0).withNano(0)
                Store.get(ctx).update { d -> d.copy(tasks = d.tasks.map { if (it.id == id) it.copy(remindAt = at.toString().take(16)).logged(it, "you") else it }) }
                NotificationManagerCompat.from(ctx).cancel(id.hashCode())
                syncTasks(ctx, Store.get(ctx).data.value.tasks)
            }
            "morning" -> {
                val todays = data.tasks.filter { !it.done && (it.dueDate == today || (it.startDate?.let { s -> !s.isAfter(today) } == true && it.dueDate?.let { d -> !d.isBefore(today) } == true)) }
                val overdue = data.tasks.count { !it.done && it.dueDate?.isBefore(today) == true }
                show(ctx, REQ_MORNING, L.s.notifMorningTitle, L.s.notifMorningText(todays.size, overdue))
                scheduleDaily(ctx)
            }
            "evening" -> {
                val done = data.tasks.count { it.doneAt == today.toString() }
                val left = data.tasks.count { !it.done && it.dueDate?.let { d -> !d.isAfter(today) } == true }
                show(ctx, REQ_EVENING, L.s.notifEveningTitle, L.s.notifEveningText(done, left))
                scheduleDaily(ctx)
            }
        }
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = Reminders.fire(context, intent)
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) Reminders.rescheduleAll(context)
    }
}
