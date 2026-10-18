package ru.anjeib.margin.data

import android.content.Context
import kotlinx.serialization.Serializable
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.LocalDateTime

/**
 * What a beta tester can send back without a server: crashes and phrases the phone or the AI understood wrong.
 * Both live as small text files in filesDir/feedback and go out by e-mail from «Отправить отзыв».
 */
object Feedback {
    /** Where tester reports go (the owner's choice, 9 Oct 2026). */
    const val EMAIL = "post@marginapp.ru"

    private const val MAX_BYTES = 200_000L

    fun dir(ctx: Context) = File(ctx.filesDir, "feedback").apply { mkdirs() }
    fun crashFile(ctx: Context) = File(dir(ctx), "crashes.txt")
    fun parseFile(ctx: Context) = File(dir(ctx), "misparsed.jsonl")
    private fun seenFile(ctx: Context) = File(dir(ctx), "crash-seen")

    /** Chains in front of the system handler: the crash is written down, then the app dies as before. */
    fun installCrashLog(ctx: Context) {
        val app = ctx.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        if (previous is Handler) return
        Thread.setDefaultUncaughtExceptionHandler(Handler(app, previous))
    }

    private class Handler(val ctx: Context, val next: Thread.UncaughtExceptionHandler?) : Thread.UncaughtExceptionHandler {
        override fun uncaughtException(t: Thread, e: Throwable) {
            runCatching {
                val sw = StringWriter().also { e.printStackTrace(PrintWriter(it)) }
                append(crashFile(ctx), "=== ${LocalDateTime.now().toString().take(19)} · ${t.name} · ${version()}\n$sw\n")
            }
            next?.uncaughtException(t, e)
        }
    }

    /** A crash happened since the user last saw the offer to send it. */
    fun newCrash(ctx: Context): Boolean {
        val f = crashFile(ctx)
        return f.exists() && f.lastModified() > seenFile(ctx).let { if (it.exists()) it.lastModified() else 0L }
    }

    fun crashSeen(ctx: Context) { runCatching { seenFile(ctx).writeText(LocalDateTime.now().toString()) } }

    @Serializable
    data class Misparse(
        val at: String,
        val text: String,
        /** "phone" — LocalParser, "ai" — the model. */
        val by: String,
        /** What came out, in the words of the result bubble. */
        val got: String,
        val version: String = version(),
    )

    fun misparsed(ctx: Context, m: Misparse) {
        runCatching { append(parseFile(ctx), AppJson.encodeToString(Misparse.serializer(), m) + "\n") }
    }

    fun misparsedCount(ctx: Context): Int = runCatching { parseFile(ctx).readLines().count { it.isNotBlank() } }.getOrDefault(0)

    /** Files to attach to the e-mail, only the ones that have something in them. */
    fun attachments(ctx: Context): List<File> = listOf(crashFile(ctx), parseFile(ctx)).filter { it.exists() && it.length() > 0 }

    // Old lines go first when a file grows too big: the newest problems matter most.
    private fun append(f: File, text: String) {
        f.appendText(text)
        if (f.length() > MAX_BYTES) {
            val keep = f.readText().takeLast((MAX_BYTES / 2).toInt())
            f.writeText(keep.substring(keep.indexOf('\n') + 1))
        }
    }

    private fun version() = ru.anjeib.margin.BuildConfig.VERSION_NAME + " (" + ru.anjeib.margin.BuildConfig.VERSION_CODE + ")"
}
