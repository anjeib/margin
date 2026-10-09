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
import ru.anjeib.margin.book.Book
import ru.anjeib.margin.book.BookStore
import ru.anjeib.margin.book.Dir
import ru.anjeib.margin.book.Kind
import ru.anjeib.margin.book.page
import ru.anjeib.margin.book.setWhen
import ru.anjeib.margin.book.toggle
import ru.anjeib.margin.book.toDecide
import ru.anjeib.margin.data.Prefs
import ru.anjeib.margin.i18n.Lang
import ru.anjeib.margin.i18n.resolveLang
import ru.anjeib.margin.paper.Words
import ru.anjeib.margin.paper.WordsEn
import ru.anjeib.margin.paper.WordsRu
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/*
 * The bell (DESIGN.md §10): a line with an hour rings at it («Сделано» / «Через час» on the notification); a promise to you
 * reminds in the morning of its day; the evening asks to turn the page when something is still open. Nothing rings for
 * an empty day.
 */
object Bell {
    private const val LINES = "lines"
    private const val DAY = "day"
    private const val KIND = "kind"
    private const val LINE = "line"
    private const val REQ_EVENING = 2_000_001
    private const val REQ_MORNING = 2_000_002
    private const val PROMISE_HOUR = 10

    fun words(ctx: Context): Words = if (resolveLang(Prefs.get(ctx).settings.value.lang) == Lang.Ru) WordsRu else WordsEn

    private fun am(ctx: Context) = ctx.getSystemService(AlarmManager::class.java)
    private fun registry(ctx: Context) = ctx.getSharedPreferences("bell", Context.MODE_PRIVATE)
    private fun intent(ctx: Context, kind: String, id: String? = null) =
        Intent(ctx, BellReceiver::class.java).putExtra(KIND, kind).apply { id?.let { putExtra(LINE, it) } }.setAction("polya.$kind.${id ?: ""}")
    private fun pi(ctx: Context, req: Int, i: Intent) = PendingIntent.getBroadcast(ctx, req, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    private fun LocalDateTime.millis() = atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    /** Exact when the system allows it — a reminder must not drift. */
    private fun at(ctx: Context, millis: Long, p: PendingIntent) {
        val m = am(ctx)
        if (Build.VERSION.SDK_INT < 31 || m.canScheduleExactAlarms()) m.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, p)
        else m.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, p)
    }

    /** Every open line with an hour ahead rings at it; a promise to you — at 10:00 of its day. The rest are cancelled. */
    fun sync(ctx: Context, book: Book) {
        val now = LocalDateTime.now()
        val wanted = HashMap<String, LocalDateTime>()
        book.lines.forEach { l ->
            if (l.parentId != null || !l.open || l.kind == Kind.Note) return@forEach
            val d = l.date ?: return@forEach
            val t = l.hour
            val atTime = when {
                t != null -> d.atTime(t)
                l.who.any { it.dir == Dir.From } -> d.atTime(PROMISE_HOUR, 0)
                else -> null
            } ?: return@forEach
            if (atTime.isAfter(now)) wanted[l.id] = atTime
        }
        val reg = registry(ctx)
        reg.all.forEach { (id, v) -> if (wanted[id]?.toString() != v) am(ctx).cancel(pi(ctx, id.hashCode(), intent(ctx, "line", id))) }
        reg.edit {
            clear()
            wanted.forEach { (id, t) -> at(ctx, t.millis(), pi(ctx, id.hashCode(), intent(ctx, "line", id))); putString(id, t.toString()) }
        }
    }

    /** The evening («перелистнуть день») and the morning, every day. */
    fun daily(ctx: Context) {
        val now = LocalDateTime.now()
        val evening = ctx.getSharedPreferences("polya", Context.MODE_PRIVATE).getInt("eveningHour", 21)
        fun next(h: Int, m: Int) = LocalDate.now().atTime(h, m).let { if (it.isAfter(now)) it else it.plusDays(1) }
        at(ctx, next(evening, 0).millis(), pi(ctx, REQ_EVENING, intent(ctx, "evening")))
        at(ctx, next(8, 30).millis(), pi(ctx, REQ_MORNING, intent(ctx, "morning")))
    }

    private fun channels(ctx: Context, w: Words) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(LINES, w.bellLines, NotificationManager.IMPORTANCE_HIGH))
        nm.createNotificationChannel(NotificationChannel(DAY, w.bellDay, NotificationManager.IMPORTANCE_DEFAULT))
    }

    private fun open(ctx: Context, extra: String, req: Int): PendingIntent = PendingIntent.getActivity(ctx, req,
        Intent(ctx, MainActivity::class.java).setAction(MainActivity.ACTION_OPEN).putExtra(MainActivity.EXTRA_ID, extra)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    private fun allowed(ctx: Context) = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun ring(ctx: Context, kind: String, id: String?) {
        val w = words(ctx)
        channels(ctx, w)
        val book = BookStore.get(ctx).book.value
        val today = LocalDate.now()
        val nm = NotificationManagerCompat.from(ctx)
        if (!allowed(ctx)) { if (kind != "line") daily(ctx); return }
        when (kind) {
            "line" -> {
                val l = book.line(id ?: return) ?: return
                if (!l.open) return
                val from = l.who.firstOrNull { it.dir == Dir.From }?.name
                val title = listOfNotNull(l.time, from?.let { w.from(it) }).joinToString(" · ").ifBlank { w.today }
                val n = NotificationCompat.Builder(ctx, LINES).setSmallIcon(R.drawable.ic_write)
                    .setContentTitle(l.text).setContentText(title).setAutoCancel(true)
                    .setContentIntent(open(ctx, "line:${l.id}", l.id.hashCode()))
                    .addAction(0, w.bellDone, pi(ctx, ("done" + l.id).hashCode(), intent(ctx, "done", l.id)))
                    .addAction(0, w.bellHour, pi(ctx, ("hour" + l.id).hashCode(), intent(ctx, "hour", l.id)))
                    .build()
                runCatching { nm.notify(l.id.hashCode(), n) }
            }
            "done" -> {
                BookStore.get(ctx).update { it.toggle(id ?: return@update it, today) }
                nm.cancel(id.hashCode())
            }
            "hour" -> {
                val l = book.line(id ?: return) ?: return
                val t = (l.hour ?: LocalTime.now()).plusHours(1).withSecond(0).withNano(0)
                BookStore.get(ctx).update { it.setWhen(l.id, l.date ?: today, "%02d:%02d".format(t.hour, t.minute), by = "you", today = today) }
                nm.cancel(id.hashCode())
            }
            "evening" -> {
                val open = book.toDecide(today).size
                if (open > 0) runCatching {
                    nm.notify(REQ_EVENING, NotificationCompat.Builder(ctx, DAY).setSmallIcon(R.drawable.ic_write)
                        .setContentTitle(w.ritualTitle).setContentText(w.ritualLine(open)).setAutoCancel(true)
                        .setContentIntent(open(ctx, "ritual", REQ_EVENING)).build())
                }
                daily(ctx)
            }
            "morning" -> {
                val p = book.page(today, today)
                val n = p.open.size
                if (n > 0) runCatching {
                    nm.notify(REQ_MORNING, NotificationCompat.Builder(ctx, DAY).setSmallIcon(R.drawable.ic_write)
                        .setContentTitle(w.weekday(today)).setContentText(w.morningLine(n, p.tails.size)).setAutoCancel(true)
                        .setContentIntent(open(ctx, "today", REQ_MORNING)).build())
                }
                daily(ctx)
            }
        }
    }
}

class BellReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, i: Intent) {
        val kind = i.getStringExtra("kind") ?: return
        Bell.ring(ctx, kind, i.getStringExtra("line"))
    }
}

/** After a restart the alarms are gone: set them again. */
class BellBoot : BroadcastReceiver() {
    override fun onReceive(ctx: Context, i: Intent) {
        Bell.daily(ctx)
        Bell.sync(ctx, BookStore.get(ctx).book.value)
    }
}
