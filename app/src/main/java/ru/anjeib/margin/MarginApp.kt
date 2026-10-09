package ru.anjeib.margin

import android.app.Application
import ru.anjeib.margin.data.Feedback

/** Only here so a crash anywhere — a screen, a worker, an alarm — is written down for «Отправить отзыв». */
class MarginApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Feedback.installCrashLog(this)
    }
}
