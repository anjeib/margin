package ru.anjeib.margin.notify

import ru.anjeib.margin.data.listTasks

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
import ru.anjeib.margin.data.doneFromReminder
import ru.anjeib.margin.data.eveningCounts
import ru.anjeib.margin.data.postponeTo
import ru.anjeib.margin.data.snoozeFromReminder
import ru.anjeib.margin.data.todayToTomorrow
import ru.anjeib.margin.i18n.L
import ru.anjeib.margin.i18n.resolveLang
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

object Reminders {
    /** Reminders, high importance (pop up). */
    private const val CHANNEL = "remind"
    /** Morning, evening, «Разобрано N»: low importance. */
    private const val DIGEST = "digest"
    /** Before 0.29: one channel for everything, default importance. */
    private const val OLD_CHANNEL = "reminders"
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

    /**
     * Sets an alarm for everything that should ring ([alarmPlan]: tasks, their days, topics' matters, notes as «note:<id>»);
     * cancels the rest. Synchronized: the screen and the start of the process may both call it.
     */
    @Synchronized
    fun syncTasks(ctx: Context, tasks: List<Task>, notes: List<ru.anjeib.margin.data.Note> = Store.get(ctx).data.value.notes) {
        val morning = runCatching { LocalTime.parse(Prefs.get(ctx).settings.value.morningTime) }.getOrDefault(LocalTime.of(9, 0))
        val wanted = alarmPlan(tasks, notes, LocalDateTime.now(), morning)
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

    /** Everything set again from the data: after a reboot, a new time zone or clock, an update, a force-stop (every process start). */
    @Synchronized
    fun rescheduleAll(ctx: Context) {
        scheduledPrefs(ctx).edit { clear() }
        syncTasks(ctx, Store.get(ctx).data.value.tasks)
        scheduleDaily(ctx)
    }

    /**
     * Two channels: reminders pop up over the screen; the summaries (morning, evening, «Разобрано N») stay quiet and can be
     * turned off on their own. A channel's importance can't be raised once made, hence the new ids; the old one goes.
     */
    fun ensureChannel(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, L.s.notifChannel, NotificationManager.IMPORTANCE_HIGH))
        nm.createNotificationChannel(NotificationChannel(DIGEST, L.s.notifDigestChannel, NotificationManager.IMPORTANCE_LOW))
        if (nm.getNotificationChannel(OLD_CHANNEL) != null) nm.deleteNotificationChannel(OLD_CHANNEL)
    }

    /**
     * [taskId] — the key of what reminds (its buttons answer for it); [open] — what a tap opens (the matter itself);
     * [digest] — a summary, on the quiet channel.
     */
    fun show(ctx: Context, id: Int, title: String, text: String?, taskId: String? = null, evening: Boolean = false,
             digest: Boolean = false, open: String? = taskId, buttons: Buttons = Buttons.Hour) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        ensureChannel(ctx)
        val channel = if (digest) DIGEST else CHANNEL
        // A tap opens the matter it is about (MainActivity → App.openId); each its own request code, or they'd replace each other.
        val launch = Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .apply { if (open != null) setAction(MainActivity.ACTION_OPEN).putExtra(MainActivity.EXTRA_ID, open) }
        val openPi = PendingIntent.getActivity(ctx, open?.hashCode() ?: 0, launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(ctx, channel)
            .setSmallIcon(R.drawable.ic_notify)
            .setColor(0xFFC8643B.toInt())
            .setContentTitle(title)
            .apply { if (!text.isNullOrBlank()) setContentText(text).setStyle(NotificationCompat.BigTextStyle().bigText(text)) }
            .setContentIntent(openPi)
            .setAutoCancel(true)
            .setPriority(if (digest) NotificationCompat.PRIORITY_LOW else NotificationCompat.PRIORITY_HIGH)
            .apply { if (!digest) setCategory(NotificationCompat.CATEGORY_REMINDER) }
            // On the lock screen only «Напоминание»: what the user wrote stays behind the unlock.
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(
                NotificationCompat.Builder(ctx, channel)
                    .setSmallIcon(R.drawable.ic_notify)
                    .setColor(0xFFC8643B.toInt())
                    .setContentTitle(L.s.notifReminder)
                    .build()
            )
            .apply {
                if (taskId != null) {
                    fun act(kind: String, req: Int) = PendingIntent.getBroadcast(ctx, taskId.hashCode() + req,
                        intent(ctx, kind, taskId), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                    // Something awaited is «Получено», not «Готово»; «через час» means nothing for it — «завтра» does.
                    addAction(0, if (buttons == Buttons.Wait) L.s.gotIt else L.s.markDone, act("done", 1))
                    if (buttons == Buttons.Hour) addAction(0, L.s.snoozeHour, act("snooze", 2))
                    else addAction(0, L.s.tomorrow, act("later", 3))
                }
                // The end of the day: what's left of today moves to tomorrow in one tap.
                if (evening) addAction(0, L.s.toTomorrow, PendingIntent.getBroadcast(ctx, REQ_EVENING + 7,
                    Intent(ctx, AlarmReceiver::class.java).putExtra(KIND, "tomorrow"), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            }
            .build()
        NotificationManagerCompat.from(ctx).notify(id, n)
    }

    /** A change from a notification's button: through Store.update (so the reminder follows), then the alarms anew. */
    private fun answer(ctx: Context, key: String, f: (ru.anjeib.margin.data.AppData) -> ru.anjeib.margin.data.AppData) {
        Store.get(ctx).update(f)
        NotificationManagerCompat.from(ctx).cancel(key.hashCode())
        syncTasks(ctx, Store.get(ctx).data.value.tasks)
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
                    show(ctx, key.hashCode(), n.title.ifBlank { L.s.untitled }, n.entries.lastOrNull { it.text.isNotBlank() }?.text?.take(120).orEmpty(), open = n.id)
                    return
                }
                ringOf(data.tasks, key, today)?.let { r -> show(ctx, key.hashCode(), r.title, r.text, key, open = r.open, buttons = r.buttons) }
                // What rings next for it (its own hour after an earlier «напомни…», the next of its days) is set now, not at the next change.
                syncTasks(ctx, data.tasks)
            }
            // «Готово» / «Получено»: just the day it reminded of when the matter has several.
            "done" -> { val key = i.getStringExtra(TASK) ?: return; answer(ctx, key) { it.doneFromReminder(key, today) } }
            "snooze" -> {
                val key = i.getStringExtra(TASK) ?: return
                val at = LocalDateTime.now().plusHours(1).withSecond(0).withNano(0)
                answer(ctx, key) { it.snoozeFromReminder(key, at) }
            }
            "later" -> { val key = i.getStringExtra(TASK) ?: return; answer(ctx, key) { it.postponeTo(key, today.plusDays(1), today) } }
            "morning" -> {
                val todays = data.listTasks().filter { !it.done && (it.dueDate == today || (it.startDate?.let { s -> !s.isAfter(today) } == true && it.dueDate?.let { d -> !d.isBefore(today) } == true)) }
                val overdue = data.listTasks().count { !it.done && it.dueDate?.isBefore(today) == true }
                show(ctx, REQ_MORNING, L.s.notifMorningTitle, L.s.notifMorningText(todays.size, overdue), digest = true)
                scheduleDaily(ctx)
            }
            "evening" -> {
                val (done, left) = data.eveningCounts(today)
                // «На завтра» only when there is something it would move.
                val movable = data.todayToTomorrow(today) != data
                show(ctx, REQ_EVENING, L.s.notifEveningTitle, L.s.notifEveningText(done, left), evening = movable, digest = true)
                scheduleDaily(ctx)
            }
            "tomorrow" -> {
                Store.get(ctx).update { it.todayToTomorrow(today) }
                NotificationManagerCompat.from(ctx).cancel(REQ_EVENING)
                syncTasks(ctx, Store.get(ctx).data.value.tasks)
            }
        }
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Reminders.fire(context, intent)
        // «Готово» / «Через час» from a notification: keep the process alive until the change is on disk.
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { withTimeout(8_000) { Store.get(context).flush() } }
            pending.finish()
        }
    }
}

/**
 * Alarms are set in the phone's clock (RTC millis): after a reboot, a new time zone or a changed clock, and after an
 * update, they are set again from the data — «в 10:00» stays 10:00 where the user is now.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_MY_PACKAGE_REPLACED)) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { Reminders.rescheduleAll(context) }
            pending.finish()
        }
    }
}
