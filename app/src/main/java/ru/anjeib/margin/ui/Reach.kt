package ru.anjeib.margin.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink

/**
 * A phone, an e-mail, a link or an «@ник» anywhere in the words opens the app it belongs to: the dialer, the mail app,
 * and for a link — whatever app the phone gives that address to (Telegram, MAX, Instagram, VK…). A bare «@ник» is Telegram:
 * that is what it means in Russian chats, and a chooser of networks was noise.
 */
object Reach {
    private val url = Regex("https?://[^\\s«»\"<>]+|(?<![\\w.@/])(?:t\\.me|max\\.ru|instagram\\.com|x\\.com|twitter\\.com|facebook\\.com|fb\\.com|vk\\.com)/[\\w.\\-/?=&%#+]+", RegexOption.IGNORE_CASE)
    private val email = Regex("(?<![\\w.+-])[\\w.+-]+@[\\w-]+(?:\\.[\\w-]+)+")
    private val phone = Regex("(?<![\\w+])(?:\\+\\d{1,3}|8)[\\s\\-()]*\\d{3}[\\s\\-()]*\\d{3}[\\s\\-]*\\d{2}[\\s\\-]*\\d{2}(?!\\d)")
    private val handle = Regex("(?<![\\w@.])@[A-Za-z0-9_.]{3,32}")

    /** What in [text] can be opened: (range, intent maker or null for a handle). */
    fun spans(text: String): List<Pair<IntRange, String>> {
        val out = mutableListOf<Pair<IntRange, String>>()
        fun add(r: Regex, kind: String) = r.findAll(text).forEach { m ->
            if (out.none { (o, _) -> o.first <= m.range.last && m.range.first <= o.last }) out += m.range to kind
        }
        add(url, "url"); add(email, "email"); add(phone, "phone"); add(handle, "handle")
        return out.sortedBy { it.first.first }
    }

    fun intent(kind: String, value: String): Intent? = when (kind) {
        "phone" -> Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + value.filter { it.isDigit() || it == '+' }))
        "email" -> Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$value"))
        "url" -> Intent(Intent.ACTION_VIEW, Uri.parse(if (value.startsWith("http", true)) value else "https://$value"))
        "telegram" -> value.removePrefix("@").let { h -> Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/" + (if (h.all { it.isDigit() || it == '+' }) "+" + h.trimStart('+') else h))) }
        "max" -> if (value.startsWith("http")) Intent(Intent.ACTION_VIEW, Uri.parse(value)) else null
        else -> null
    }

    /** A page's network and who it is, for a contact chip: «instagram.com/zerno.coffee» → («Instagram», «zerno.coffee»). */
    fun socialName(link: String): Pair<String, String> {
        val host = link.substringBefore('/').lowercase().removePrefix("m.")
        val who = link.substringAfter('/', "").trimEnd('/').substringAfterLast('/').substringBefore('?').ifEmpty { host }
        val net = when (host.removeSuffix(".com").removeSuffix(".ru").removeSuffix(".net")) {
            "instagram" -> "Instagram"; "vk" -> "VK"; "ok" -> "OK"; "x", "twitter" -> "X"; "facebook", "fb" -> "Facebook"
            "youtube", "youtu.be" -> "YouTube"; "tiktok" -> "TikTok"; "linkedin" -> "LinkedIn"; "behance" -> "Behance"
            "dribbble" -> "Dribbble"; "pinterest" -> "Pinterest"; "dzen" -> "Дзен"; "t.me" -> "Telegram"; "max" -> "MAX"
            else -> return host to (if (who == host) "" else who)
        }
        return net to who
    }

    fun open(ctx: Context, kind: String, value: String): Boolean =
        intent(kind, value)?.let { runCatching { ctx.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess } ?: false
}

/** [text] with its phones, e-mails, links and handles as petrol links that open their apps. */
@Composable
fun linked(text: String): AnnotatedString {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val c = M.c
    return remember(text, c.link) {
        val spans = Reach.spans(text)
        if (spans.isEmpty()) return@remember AnnotatedString(text)
        buildAnnotatedString {
            var at = 0
            spans.forEach { (r, kind) ->
                append(text.substring(at, r.first))
                val v = text.substring(r)
                withLink(LinkAnnotation.Clickable(kind, TextLinkStyles(SpanStyle(color = c.link))) {
                    Reach.open(ctx, if (kind == "handle") "telegram" else kind, v)
                }) { append(v) }
                at = r.last + 1
            }
            append(text.substring(at))
        }
    }
}
