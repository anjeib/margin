package ru.anjeib.margin

import android.app.Application
import ru.anjeib.margin.data.Feedback

/** Only here so a crash anywhere — a screen, a worker, an alarm — is written down for «Отправить отзыв». */
class MarginApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Feedback.installCrashLog(this)
        // «Поля»: every save keeps the bell in step with the lines; the day's bells are set once per start.
        ru.anjeib.margin.book.BookStore.onSaved = {
            ru.anjeib.margin.notify.Bell.sync(this, ru.anjeib.margin.book.BookStore.get(this).book.value)
            ru.anjeib.margin.widget.TodayWidget.refresh(this)
        }
        ru.anjeib.margin.notify.Bell.daily(this)
    }
}
