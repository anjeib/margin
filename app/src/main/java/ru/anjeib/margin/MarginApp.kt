package ru.anjeib.margin

import android.app.Application
import ru.anjeib.margin.data.Feedback

/** Only here so a crash anywhere — a screen, a worker, an alarm — is written down for «Отправить отзыв». */
class MarginApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Feedback.installCrashLog(this)
        ru.anjeib.margin.data.Store.onSaved = { ru.anjeib.margin.widget.TodayWidget.refresh(this) }
        // Alarms die with a force-stop and drift with a new time zone: every start of the process sets them again, off the main thread.
        Thread { runCatching { ru.anjeib.margin.notify.Reminders.rescheduleAll(this) } }.start()
    }
}
