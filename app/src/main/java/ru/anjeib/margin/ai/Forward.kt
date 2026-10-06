package ru.anjeib.margin.ai

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import ru.anjeib.margin.data.AppData
import ru.anjeib.margin.data.Attachment
import ru.anjeib.margin.data.Files
import ru.anjeib.margin.data.Queued
import ru.anjeib.margin.data.newId
import ru.anjeib.margin.data.whenLabel
import ru.anjeib.margin.i18n.L
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Forwarding into Margin from elsewhere: the user's own Telegram bot and their own mailbox.
 * What arrives goes to the offline queue («Входящие») with who wrote it, and is sorted like anything typed —
 * except that the words are someone else's ([prompt]). Secrets live in their own prefs file, outside the Google backup.
 */
object Forward {

    data class State(
        val tgToken: String = "",
        val tgBot: String = "",
        val tgOwner: Long = 0,
        val tgOffset: Long = 0,
        val mailAddress: String = "",
        val mailPassword: String = "",
        val mailHost: String = "",
        val mailUid: Long = 0,
        val mailUidValidity: Long = 0,
        /** The last thing that went wrong, shown in settings; cleared by the next success. */
        val error: String? = null,
    ) {
        val telegram get() = tgToken.isNotBlank() && tgBot.isNotBlank()
        val mail get() = mailAddress.isNotBlank() && mailPassword.isNotBlank()
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state
    private var loaded = false

    private fun sp(ctx: Context) = ctx.getSharedPreferences("forward", Context.MODE_PRIVATE)

    fun load(ctx: Context): State {
        if (!loaded) {
            val p = sp(ctx)
            _state.value = State(
                tgToken = p.getString("tgToken", "") ?: "", tgBot = p.getString("tgBot", "") ?: "",
                tgOwner = p.getLong("tgOwner", 0), tgOffset = p.getLong("tgOffset", 0),
                mailAddress = p.getString("mailAddress", "") ?: "", mailPassword = p.getString("mailPassword", "") ?: "",
                mailHost = p.getString("mailHost", "") ?: "", mailUid = p.getLong("mailUid", 0), mailUidValidity = p.getLong("mailUidValidity", 0),
            )
            loaded = true
        }
        return _state.value
    }

    private fun save(ctx: Context, f: (State) -> State) {
        val s = f(load(ctx))
        sp(ctx).edit()
            .putString("tgToken", s.tgToken).putString("tgBot", s.tgBot).putLong("tgOwner", s.tgOwner).putLong("tgOffset", s.tgOffset)
            .putString("mailAddress", s.mailAddress).putString("mailPassword", s.mailPassword).putString("mailHost", s.mailHost)
            .putLong("mailUid", s.mailUid).putLong("mailUidValidity", s.mailUidValidity)
            .apply()
        _state.value = s
    }

    private fun failed(ctx: Context, msg: String?) { _state.value = load(ctx).copy(error = msg) }

    // ---------------- what the AI is given ----------------

    /** The app something was shared from → telegram | max | mail | share. */
    fun viaOf(pkg: String?): String {
        val p = pkg?.lowercase() ?: return "share"
        return when {
            p.startsWith("org.telegram") || p == "org.thunderdog.challegram" || p.contains("nekogram") -> "telegram"
            p == "ru.oneme.app" || p.startsWith("ru.oneme") -> "max"
            p == "com.google.android.gm" || p.contains(".mail") || p.contains("email") || p.contains("outlook") -> "mail"
            else -> "share"
        }
    }

    /** Who wrote it, as far as the words tell: a letter's «От: Марина <m@x.ru>», a Telegram copy's «Марина Петрова, [6 окт. в 17:42]». */
    data class Sender(val name: String?, val address: String?, val company: String? = null) {
        val title get() = name ?: company ?: address ?: ""
    }

    private val mailHeader = Regex("(?im)^\\s*(?:From|От|От кого)\\s*:\\s*\"?([^\"<\\n]*?)\"?\\s*<([^>\\s]+@[^>\\s]+)>")
    private val mailBare = Regex("(?im)^\\s*(?:From|От|От кого)\\s*:\\s*([^\\s<]+@[^\\s>]+)")
    private val yandexHeader = Regex("(?m)^\\s*\\d{2}\\.\\d{2}\\.\\d{4},\\s*\\d{1,2}:\\d{2},\\s*\"([^\"]+)\"\\s*<([^>]+)>")
    private val tgAndroid = Regex("(?m)^([^\\n,\\[]{2,40}),\\s*\\[[^\\]]{4,40}\\]:?\\s*$")
    private val tgDesktop = Regex("(?m)^\\[\\d{1,2}\\.\\d{1,2}\\.\\d{2,4},?\\s+\\d{1,2}:\\d{2}(?::\\d{2})?\\]\\s*([^:\\n]{2,40}):")
    private val company = Regex("(?m)((?:ООО|АО|ПАО|ЗАО|ИП)\\s+«?[^\\n»,]{2,40}»?)")
    private val email = Regex("[\\w.+-]+@[\\w-]+\\.[\\w.-]+")

    fun sender(text: String): Sender? {
        mailHeader.find(text)?.let { m -> return Sender(m.groupValues[1].trim().ifBlank { null }, m.groupValues[2], company.find(text)?.value?.trim()) }
        yandexHeader.find(text)?.let { m -> return Sender(m.groupValues[1].trim(), m.groupValues[2], company.find(text)?.value?.trim()) }
        mailBare.find(text)?.let { m -> return Sender(null, m.groupValues[1], company.find(text)?.value?.trim()) }
        (tgAndroid.find(text) ?: tgDesktop.find(text))?.let { m -> return Sender(m.groupValues[1].trim(), null) }
        val co = company.find(text)?.value?.trim()
        val mail = email.find(text)?.value
        return if (co != null || mail != null) Sender(null, mail, co) else null
    }

    /** The app a share came from, by its own name («Gmail», «Telegram», «MAX»); null for the system chooser. */
    fun appName(ctx: Context, pkg: String?): String? {
        if (pkg == null || pkg == "android") return null
        return runCatching { ctx.packageManager.getApplicationLabel(ctx.packageManager.getApplicationInfo(pkg, 0)).toString() }.getOrNull()
    }

    /** «+ контакт»: the sender into «Контакты» — new, or the known one filled in where it was empty. */
    fun addContact(ctx: Context, s: Sender) {
        val name = s.name ?: s.company ?: s.address ?: return
        val tg = s.address?.takeIf { it.startsWith("@") }
        val mail = s.address?.takeIf { it.contains('@') && !it.startsWith("@") }
        ru.anjeib.margin.data.Store.get(ctx).update { d ->
            val known = d.persons.find { it.matches(name) || (mail != null && it.email.equals(mail, true)) }
            if (known != null) d.copy(persons = d.persons.map {
                if (it.id != known.id) it else it.copy(draft = false, kept = true, email = it.email ?: mail, telegram = it.telegram ?: tg, company = it.company ?: s.company?.takeIf { c -> c != name }, archived = false)
            })
            else d.copy(persons = d.persons + ru.anjeib.margin.data.Person(name = name, email = mail, telegram = tg, company = s.company?.takeIf { it != name }, kept = true))
        }
    }

    fun isContact(d: AppData, s: Sender?): Boolean {
        if (s == null) return false
        val name = s.name ?: s.company ?: s.address ?: return false
        return d.persons.any { it.isContact && (it.matches(name) || (s.address != null && it.email.equals(s.address, true))) }
    }

    /** Where it came from, in words: «Telegram», «почта», «MAX». */
    fun viaName(via: String?): String = when (via) {
        "telegram" -> "Telegram"
        "max" -> "MAX"
        "mail" -> L.s.viaMail
        "copy" -> L.s.viaCopy
        else -> L.s.viaOther
    }

    /**
     * The forwarded words, framed so the AI knows they are the author's, not the user's:
     * «пришлю к среде» from Марина is something the user waits for, not the user's own promise.
     */
    fun prompt(q: Queued): String = buildString {
        q.comment?.takeIf { it.isNotBlank() }?.let { appendLine(it.trim()); appendLine() }
        append("[Forwarded ").append(viaName(q.via)).append(" message")
        when {
            q.from == L.s.me -> append(", written by the user themself")
            q.from != null -> append(", written by ").append(q.from)
            else -> append(", author unknown")
        }
        q.sentAt?.let { append(", sent ").append(it.replace('T', ' ')) }
        q.subject?.takeIf { it.isNotBlank() }?.let { append(", subject «").append(it).append("»") }
        appendLine(". The author's words, not the user's:]")
        append(q.text.trim().take(6000))
    }

    /** The «↳ из сообщения» text kept on what it became: who said it and the words. */
    fun source(q: Queued): String = listOfNotNull(
        q.comment?.takeIf { it.isNotBlank() },
        (q.from?.let { "$it, " + L.s.forwardFrom(viaName(q.via)) } ?: L.s.forwardFrom(viaName(q.via)).replaceFirstChar { it.uppercase() }) + ": " + q.text.trim().take(1500),
    ).joinToString("\n")

    /** One line per thing it became — said back in Telegram and in the notification. */
    fun summary(before: AppData, applied: Applied, plan: AiPlan): String {
        val lines = mutableListOf<String>()
        applied.touchedTasks.forEach { id ->
            val t = applied.data.tasks.find { it.id == id } ?: return@forEach
            val fresh = before.tasks.none { it.id == id }
            lines += (if (fresh) "✓ " else "↻ ") + t.title + listOfNotNull(
                t.waitingFor?.let { L.s.waitingShort(it) }, t.promisedTo?.let { L.s.promisedShort(it) }, t.whenLabel(),
            ).joinToString("") { " · $it" }
        }
        applied.touchedNotes.forEach { id -> applied.data.notes.find { it.id == id }?.let { lines += "✎ " + it.title } }
        if (lines.isEmpty()) plan.reply?.takeIf { it.isNotBlank() }?.let { lines += it }
        return lines.joinToString("\n").ifBlank { L.s.forwardKept }
    }

    // ---------------- Telegram: the user's own bot ----------------

    private const val TG = "https://api.telegram.org/bot"
    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Mutex()

    private fun http(url: String, body: String? = null, timeoutSec: Int = 20): Pair<Int, String> {
        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000; readTimeout = (timeoutSec + 10) * 1000
            if (body != null) { requestMethod = "POST"; doOutput = true; setRequestProperty("Content-Type", "application/json") }
        }
        return try {
            body?.let { c.outputStream.use { o -> o.write(it.toByteArray()) } }
            val code = c.responseCode
            code to ((if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.readText() ?: "")
        } finally { c.disconnect() }
    }

    private fun tg(token: String, method: String, params: String = "", timeoutSec: Int = 20): JsonObject? {
        val (code, text) = http("$TG$token/$method$params", timeoutSec = timeoutSec)
        val o = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
        if (code == 401 || code == 404) throw AiException(L.s.tgBadToken)
        if (code == 409) return null // another poll is running (the worker and the app at once) — skip this round
        if (o?.get("ok")?.jsonPrimitive?.contentOrNull != "true") throw AiException(o?.get("description")?.jsonPrimitive?.contentOrNull ?: "Telegram $code")
        return o
    }

    /** Checks the token from @BotFather and remembers the bot; the first person to write to it becomes its owner. */
    suspend fun connectTelegram(ctx: Context, token: String): String? = withContext(Dispatchers.IO) {
        val t = token.trim()
        try {
            val me = tg(t, "getMe")?.get("result")?.jsonObject ?: return@withContext L.s.tgBadToken
            val name = me["username"]?.jsonPrimitive?.contentOrNull ?: return@withContext L.s.tgBadToken
            // Old updates (written before connecting) are skipped.
            val last = tg(t, "getUpdates", "?offset=-1&timeout=0")?.get("result")?.jsonArray?.lastOrNull()
                ?.jsonObject?.get("update_id")?.jsonPrimitive?.longOrNull
            save(ctx) { it.copy(tgToken = t, tgBot = name, tgOwner = 0, tgOffset = (last ?: -1) + 1, error = null) }
            schedule(ctx)
            null
        } catch (e: Exception) { e.message ?: L.s.errNet }
    }

    fun disconnectTelegram(ctx: Context) = save(ctx) { it.copy(tgToken = "", tgBot = "", tgOwner = 0, tgOffset = 0, error = null) }

    fun reply(ctx: Context, to: String, text: String) {
        val s = load(ctx)
        if (!to.startsWith("tg:") || s.tgToken.isBlank()) return
        val chat = to.removePrefix("tg:")
        runCatching {
            val body = kotlinx.serialization.json.buildJsonObject {
                put("chat_id", kotlinx.serialization.json.JsonPrimitive(chat.toLongOrNull() ?: 0))
                put("text", kotlinx.serialization.json.JsonPrimitive(text.take(3500)))
            }.toString()
            http("$TG${s.tgToken}/sendMessage", body)
        }
    }

    private fun JsonObject.str(k: String) = this[k]?.jsonPrimitive?.contentOrNull
    private fun JsonObject.obj(k: String) = this[k] as? JsonObject
    private fun JsonObject.long(k: String) = this[k]?.jsonPrimitive?.longOrNull

    private fun personName(u: JsonObject?) = u?.let { listOfNotNull(it.str("first_name"), it.str("last_name")).joinToString(" ").ifBlank { null } }

    private fun stamp(unix: Long?) = unix?.let { LocalDateTime.ofInstant(Instant.ofEpochSecond(it), ZoneId.systemDefault()).withSecond(0).withNano(0).toString() }

    /** Forwarded → (author, sent at); the user's own forwarded words get «Я». null when not a forward. */
    private fun origin(m: JsonObject, owner: Long): Pair<String?, Long?>? {
        m.obj("forward_origin")?.let { o ->
            val who = when (o.str("type")) {
                "user" -> o.obj("sender_user")?.let { u -> if (u.long("id") == owner) L.s.me else personName(u) }
                "hidden_user" -> o.str("sender_user_name")
                "chat" -> o.str("author_signature") ?: o.obj("sender_chat")?.str("title")
                "channel" -> o.str("author_signature")?.let { s -> o.obj("chat")?.str("title")?.let { "$s ($it)" } ?: s } ?: o.obj("chat")?.str("title")
                else -> null
            }
            return who to o.long("date")
        }
        val legacy = m.obj("forward_from")?.let { u -> if (u.long("id") == owner) L.s.me else personName(u) }
            ?: m.str("forward_sender_name") ?: m.obj("forward_from_chat")?.str("title")
        return if (legacy != null || m.long("forward_date") != null) legacy to m.long("forward_date") else null
    }

    private fun textOf(m: JsonObject): String {
        val t = m.str("text") ?: m.str("caption") ?: ""
        val contact = m.obj("contact")?.let { c ->
            listOfNotNull(personName(c), c.str("phone_number")).joinToString(", ")
        }
        return listOfNotNull(t.ifBlank { null }, contact).joinToString("\n")
    }

    /** Photo, document, voice, video, audio — fetched into app storage (Telegram gives bots files up to 20 MB). */
    private fun download(ctx: Context, token: String, m: JsonObject): Attachment? {
        val photo = (m["photo"] as? JsonArray)?.lastOrNull()?.jsonObject
        val (f, kind, name, mime) = when {
            photo != null -> Quad(photo, "photo", "photo.jpg", "image/jpeg")
            m.obj("document") != null -> m.obj("document")!!.let { d ->
                val mt = d.str("mime_type")
                Quad(d, if (mt?.startsWith("image/") == true) "photo" else if (mt?.startsWith("video/") == true) "video" else "file", d.str("file_name") ?: "file", mt)
            }
            m.obj("video") != null -> Quad(m.obj("video")!!, "video", m.obj("video")!!.str("file_name") ?: "video.mp4", m.obj("video")!!.str("mime_type") ?: "video/mp4")
            m.obj("voice") != null -> Quad(m.obj("voice")!!, "audio", "voice.ogg", m.obj("voice")!!.str("mime_type") ?: "audio/ogg")
            m.obj("audio") != null -> Quad(m.obj("audio")!!, "audio", m.obj("audio")!!.str("file_name") ?: "audio.mp3", m.obj("audio")!!.str("mime_type") ?: "audio/mpeg")
            m.obj("video_note") != null -> Quad(m.obj("video_note")!!, "video", "video.mp4", "video/mp4")
            else -> return null
        }
        if ((f.long("file_size") ?: 0) > 20_000_000) return null
        val id = f.str("file_id") ?: return null
        val path = runCatching { tg(token, "getFile", "?file_id=" + URLEncoder.encode(id, "UTF-8")) }.getOrNull()
            ?.obj("result")?.str("file_path") ?: return null
        val ext = name.substringAfterLast('.', path.substringAfterLast('.', "bin"))
        val aid = newId()
        val out = File(Files.dir(ctx), "$aid.$ext")
        val ok = runCatching {
            val c = URL("https://api.telegram.org/file/bot$token/$path").openConnection() as HttpURLConnection
            c.connectTimeout = 15_000; c.readTimeout = 60_000
            try { c.inputStream.use { i -> out.outputStream().use { i.copyTo(it) } } } finally { c.disconnect() }
        }.isSuccess
        if (!ok) { out.delete(); return null }
        return Attachment(id = aid, kind = kind, file = out.name, name = name, mime = mime, size = out.length(),
            duration = f["duration"]?.jsonPrimitive?.intOrNull ?: 0)
    }

    private data class Quad(val f: JsonObject, val kind: String, val name: String, val mime: String?)

    /**
     * Fetches what was sent to the bot and queues it. Messages forwarded in one go (same moment) become one item;
     * a plain message right before them is the user's comment on them. Returns how many items were queued.
     */
    suspend fun pollTelegram(ctx: Context, waitSec: Int = 0): Int = withContext(Dispatchers.IO) {
        if (!load(ctx).telegram) return@withContext 0
        lock.withLock {
            val token = load(ctx).tgToken
            try {
                fun fetch(wait: Int): List<JsonObject> =
                    tg(token, "getUpdates", "?offset=${load(ctx).tgOffset}&timeout=$wait&allowed_updates=%5B%22message%22%5D", wait)
                        ?.get("result")?.jsonArray?.map { it.jsonObject }.orEmpty()
                var updates = fetch(waitSec)
                if (updates.isEmpty()) { failed(ctx, null); return@withLock 0 }
                // A forward of several messages arrives as several updates: let the rest come in.
                delay(1500)
                updates = updates + runCatching {
                    val next = (updates.last().long("update_id") ?: 0) + 1
                    tg(token, "getUpdates", "?offset=$next&timeout=0")?.get("result")?.jsonArray?.map { it.jsonObject }.orEmpty()
                }.getOrDefault(emptyList())
                val lastId = updates.mapNotNull { it.long("update_id") }.maxOrNull() ?: return@withLock 0
                var owner = load(ctx).tgOwner
                val messages = updates.mapNotNull { it.obj("message") }.filter { it.obj("chat")?.str("type") == "private" }
                if (owner == 0L) messages.firstOrNull()?.obj("from")?.long("id")?.let { id ->
                    owner = id
                    save(ctx) { it.copy(tgOwner = id) }
                    messages.first().obj("chat")?.long("id")?.let { reply(ctx, "tg:$it", L.s.tgWelcome) }
                }
                val mine = messages.filter { it.obj("from")?.long("id") == owner }
                messages.filter { it.obj("from")?.long("id") != owner }.mapNotNull { it.obj("chat")?.long("id") }.distinct()
                    .forEach { reply(ctx, "tg:$it", L.s.tgNotYours) }

                var queued = 0
                var comment: String? = null
                var i = 0
                while (i < mine.size) {
                    val m = mine[i]
                    val chat = m.obj("chat")?.long("id")
                    if (chat == null) { i++; continue }
                    val text = textOf(m)
                    if (text.trim().startsWith("/")) { if (text.trim() == "/start" && i > 0) reply(ctx, "tg:$chat", L.s.tgWelcome); i++; continue }
                    val fwd = origin(m, owner)
                    if (fwd == null) {
                        // The user's own words: a comment on the forward right after, or something to sort on its own.
                        val next = mine.getOrNull(i + 1)
                        val nextFwd = next != null && origin(next, owner) != null && ((next.long("date") ?: 0) - (m.long("date") ?: 0)) in 0..5
                        val att = download(ctx, token, m)
                        if (nextFwd && att == null) comment = text
                        else if (text.isNotBlank() || att != null) {
                            Processor.enqueue(ctx, Queued(text = text, files = listOfNotNull(att), reply = "tg:$chat"))
                            queued++
                        }
                        i++; continue
                    }
                    // One forward = the messages sent at the same moment (and an album's parts).
                    val group = mutableListOf(m)
                    var j = i + 1
                    while (j < mine.size) {
                        val n = mine[j]
                        val sameMoment = kotlin.math.abs((n.long("date") ?: 0) - (m.long("date") ?: 0)) <= 3
                        val sameAlbum = m.str("media_group_id") != null && n.str("media_group_id") == m.str("media_group_id")
                        if (origin(n, owner) == null || !(sameMoment || sameAlbum)) break
                        group += n; j++
                    }
                    val parts = group.map { origin(it, owner)!! to textOf(it) }
                    val authors = parts.mapNotNull { it.first.first }.distinct()
                    val body = if (authors.size > 1 || authors.contains(L.s.me))
                        parts.filter { it.second.isNotBlank() }.joinToString("\n") { (o, t) -> (o.first ?: "?") + ": " + t }
                    else parts.map { it.second }.filter { it.isNotBlank() }.joinToString("\n")
                    val files = group.mapNotNull { download(ctx, token, it) }
                    if (body.isNotBlank() || files.isNotEmpty()) {
                        Processor.enqueue(ctx, Queued(
                            text = body, files = files, via = "telegram",
                            from = authors.singleOrNull() ?: authors.filter { it != L.s.me }.joinToString(", ").ifBlank { null },
                            sentAt = stamp(parts.mapNotNull { it.first.second }.minOrNull()),
                            comment = comment, reply = "tg:$chat",
                        ))
                        queued++
                    }
                    comment = null
                    i = j
                }
                save(ctx) { it.copy(tgOffset = lastId + 1, error = null) }
                queued
            } catch (e: Exception) {
                failed(ctx, e.message)
                0
            }
        }
    }

    // ---------------- mail: letters the user forwards to their own address ----------------

    /** The usual IMAP server for an address; others get «imap.<domain>». */
    fun imapHost(address: String): String {
        val d = address.substringAfter('@').lowercase().trim()
        return when (d) {
            "yandex.ru", "ya.ru", "yandex.com", "yandex.by", "yandex.kz", "narod.ru" -> "imap.yandex.ru"
            "gmail.com", "googlemail.com" -> "imap.gmail.com"
            "mail.ru", "bk.ru", "inbox.ru", "list.ru", "internet.ru", "vk.com" -> "imap.mail.ru"
            "rambler.ru", "lenta.ru", "autorambler.ru", "myrambler.ru", "ro.ru" -> "imap.rambler.ru"
            "icloud.com", "me.com", "mac.com" -> "imap.mail.me.com"
            else -> "imap.$d"
        }
    }

    private fun session(): javax.mail.Session = javax.mail.Session.getInstance(java.util.Properties().apply {
        put("mail.store.protocol", "imaps")
        put("mail.imaps.connectiontimeout", "15000")
        put("mail.imaps.timeout", "30000")
        put("mail.imaps.ssl.checkserveridentity", "true")
    })

    /** Signs in once to check the app password; only letters that arrive from now on are taken. */
    suspend fun connectMail(ctx: Context, address: String, password: String): String? = withContext(Dispatchers.IO) {
        val a = address.trim().lowercase()
        val host = imapHost(a)
        try {
            val store = session().getStore("imaps")
            store.connect(host, a, password.replace(" ", ""))
            try {
                val box = store.getFolder("INBOX") as com.sun.mail.imap.IMAPFolder
                box.open(javax.mail.Folder.READ_ONLY)
                val next = box.uidNext
                val validity = box.uidValidity
                box.close(false)
                save(ctx) { it.copy(mailAddress = a, mailPassword = password.replace(" ", ""), mailHost = host, mailUid = next - 1, mailUidValidity = validity, error = null) }
            } finally { store.close() }
            schedule(ctx)
            null
        } catch (e: javax.mail.AuthenticationFailedException) {
            L.s.mailBadLogin
        } catch (e: Exception) {
            e.message ?: L.s.errNet
        }
    }

    fun disconnectMail(ctx: Context) = save(ctx) { it.copy(mailAddress = "", mailPassword = "", mailHost = "", mailUid = 0, mailUidValidity = 0, error = null) }

    private val forwardedFrom = listOf(
        Regex("(?im)^\\s*(?:From|От|От кого)\\s*:\\s*\"?([^\"<\\n]+?)\"?\\s*(?:<[^>]+>)?\\s*$"),
        // Yandex: «06.10.2026, 17:42, "Марина" <m@x.ru>:»
        Regex("(?m)^\\s*\\d{2}\\.\\d{2}\\.\\d{4},\\s*\\d{1,2}:\\d{2},\\s*\"([^\"]+)\"\\s*<[^>]+>:?\\s*$"),
    )

    private fun plain(p: javax.mail.Part, files: MutableList<Pair<String, javax.mail.Part>>): String? {
        return when {
            p.isMimeType("text/plain") && p.disposition?.equals(javax.mail.Part.ATTACHMENT, true) != true -> p.content as? String
            p.isMimeType("text/html") && p.disposition?.equals(javax.mail.Part.ATTACHMENT, true) != true ->
                (p.content as? String)?.let { androidx.core.text.HtmlCompat.fromHtml(it, androidx.core.text.HtmlCompat.FROM_HTML_MODE_LEGACY).toString() }
            p.isMimeType("multipart/alternative") -> {
                val mp = p.content as javax.mail.Multipart
                val parts = (0 until mp.count).map { mp.getBodyPart(it) }
                (parts.firstOrNull { it.isMimeType("text/plain") } ?: parts.lastOrNull())?.let { plain(it, files) }
            }
            p.isMimeType("multipart/*") -> {
                val mp = p.content as javax.mail.Multipart
                (0 until mp.count).mapNotNull { plain(mp.getBodyPart(it), files) }.joinToString("\n").ifBlank { null }
            }
            p.isMimeType("message/rfc822") -> (p.content as? javax.mail.Message)?.let { plain(it, files) }
            else -> { if (p.fileName != null || p.disposition != null) files += (p.fileName ?: "file") to p; null }
        }
    }

    /**
     * New letters the user sent to their own address (forwarded to themself, or to «+margin»).
     * Read-only: nothing is marked or moved in the mailbox.
     */
    suspend fun pollMail(ctx: Context): Int = withContext(Dispatchers.IO) {
        val s = load(ctx)
        if (!s.mail) return@withContext 0
        lock.withLock {
            var queued = 0
            try {
                val store = session().getStore("imaps")
                store.connect(s.mailHost, s.mailAddress, s.mailPassword)
                try {
                    val box = store.getFolder("INBOX") as com.sun.mail.imap.IMAPFolder
                    box.open(javax.mail.Folder.READ_ONLY)
                    try {
                        // The mailbox was rebuilt: start from now rather than re-reading everything.
                        if (box.uidValidity != s.mailUidValidity) {
                            save(ctx) { it.copy(mailUid = box.uidNext - 1, mailUidValidity = box.uidValidity) }
                            return@withLock 0
                        }
                        var top = s.mailUid
                        val me = s.mailAddress
                        val local = me.substringBefore('@')
                        val domain = me.substringAfter('@')
                        box.getMessagesByUID(s.mailUid + 1, javax.mail.UIDFolder.LASTUID).filterNotNull().take(30).forEach { m ->
                            val uid = box.getUID(m)
                            if (uid <= s.mailUid) return@forEach
                            top = maxOf(top, uid)
                            val from = (m.from?.firstOrNull() as? javax.mail.internet.InternetAddress)?.address?.lowercase()
                            val to = (m.getRecipients(javax.mail.Message.RecipientType.TO).orEmpty().toList() + m.getRecipients(javax.mail.Message.RecipientType.CC).orEmpty().toList())
                                .mapNotNull { a -> (a as? javax.mail.internet.InternetAddress)?.address?.lowercase() }
                            val plus = to.any { it.startsWith("$local+") && it.endsWith("@$domain") }
                            if (!plus && !(from == me && to.contains(me))) return@forEach
                            val atts = mutableListOf<Pair<String, javax.mail.Part>>()
                            val body = (plain(m, atts) ?: "").replace(Regex("\\n{3,}"), "\n\n").trim()
                            val subject = m.subject?.replace(Regex("^(?i)(fwd?|пересл\\.?)\\s*:\\s*"), "")?.trim()
                            val author = forwardedFrom.firstNotNullOfOrNull { r -> r.find(body)?.groupValues?.get(1)?.trim() }
                                ?.takeIf { it.isNotBlank() && !it.equals(me, true) }
                            val files = atts.take(5).mapNotNull { (name, p) -> runCatching {
                                if (p.size > 10_000_000) return@runCatching null
                                val ext = name.substringAfterLast('.', "bin").lowercase()
                                val aid = newId()
                                val out = File(Files.dir(ctx), "$aid.$ext")
                                p.inputStream.use { i -> out.outputStream().use { i.copyTo(it) } }
                                val mime = p.contentType?.substringBefore(';')?.lowercase()
                                Attachment(id = aid, kind = if (mime?.startsWith("image/") == true) "photo" else if (mime?.startsWith("video/") == true) "video" else "file",
                                    file = out.name, name = name, mime = mime, size = out.length())
                            }.getOrNull() }
                            if (body.isBlank() && files.isEmpty()) return@forEach
                            Processor.enqueue(ctx, Queued(
                                text = body.take(6000), files = files, via = "mail", from = author,
                                sentAt = m.sentDate?.let { stamp(it.time / 1000) }, subject = subject,
                            ))
                            queued++
                        }
                        save(ctx) { it.copy(mailUid = top, error = null) }
                    } finally { runCatching { box.close(false) } }
                } finally { runCatching { store.close() } }
            } catch (e: javax.mail.AuthenticationFailedException) {
                failed(ctx, L.s.mailBadLogin)
            } catch (e: Exception) {
                failed(ctx, e.message)
            }
            queued
        }
    }

    // ---------------- when ----------------

    /** Both channels, then sorting what came. */
    suspend fun pollAll(ctx: Context, waitSec: Int = 0): Int {
        val n = pollTelegram(ctx, waitSec) + pollMail(ctx)
        if (n > 0) Processor.schedule(ctx)
        return n
    }

    /** Every 15 minutes in the background (Android's shortest period); the app polls by itself while open. */
    fun schedule(ctx: Context) {
        val s = load(ctx)
        val wm = WorkManager.getInstance(ctx)
        if (!s.telegram && !s.mail) { wm.cancelUniqueWork("forward"); return }
        wm.enqueueUniquePeriodicWork(
            "forward", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<ForwardWorker>(15, java.util.concurrent.TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build(),
        )
    }
}

class ForwardWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        ru.anjeib.margin.i18n.L.lang = ru.anjeib.margin.i18n.resolveLang(ru.anjeib.margin.data.Prefs.get(applicationContext).settings.value.lang)
        Forward.pollAll(applicationContext)
        return Result.success()
    }
}
