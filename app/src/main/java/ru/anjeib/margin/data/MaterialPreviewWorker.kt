package ru.anjeib.margin.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.text.Html
import androidx.work.*
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URI
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Small public-page previews; no cookies, scripts, account credentials or uploaded screenshots. */
class MaterialPreviewWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val id = inputData.getString("entry") ?: return@withContext Result.success()
        val store = Store.get(applicationContext)
        store.flush() // The shared original is durable before starting network requests.
        fun entry() = (store.data.value.tasks.flatMap { it.entries } + store.data.value.notes.flatMap { it.entries }).find { it.id == id }
        val initial = entry() ?: return@withContext Result.success()
        var failed = false
        for (a in initial.attachments.filter { it.kind == "link" && !it.previewLoaded }) {
            val url = a.url ?: continue
            try {
                val page = fetch(url, 512 * 1024, "text/html")
                val meta = PageMetadata.parse(page.first.toString(Charsets.UTF_8), page.second)
                var imageFile: String? = null
                meta.image?.let { image ->
                    runCatching {
                        val bytes = fetch(image, 3 * 1024 * 1024, "image/").first
                        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                        require(bounds.outWidth in 1..20000 && bounds.outHeight in 1..20000)
                        var sample = 1
                        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1200) sample *= 2
                        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: error("image")
                        val name = "${a.id}-preview.jpg"
                        try { File(Files.dir(applicationContext), name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 82, it) } }
                        finally { bitmap.recycle() }
                        imageFile = name
                    }
                }
                val updated = a.copy(
                    name = meta.title ?: a.name,
                    siteName = meta.site ?: a.siteName,
                    file = imageFile ?: a.file,
                    transcript = meta.articleText ?: a.transcript,
                    duration = if (meta.readMinutes > 0) meta.readMinutes else a.duration,
                    previewLoaded = true,
                )
                var retained = false
                store.update { d ->
                    fun change(e: Entry): Entry {
                        if (e.id != id) return e
                        val nextDesc = if (e.description.isBlank() && !meta.description.isNullOrBlank()) meta.description else e.description
                        return e.copy(description = nextDesc, attachments = e.attachments.map { current ->
                            if (current.id != a.id || current.url != url) current else {
                                retained = true
                                updated.copy(name = if (current.titleFromUser) current.name else updated.name, titleFromUser = current.titleFromUser, caption = current.caption, numbered = current.numbered)
                            }
                        })
                    }
                    d.copy(tasks = d.tasks.map { t ->
                        val changed = t.entries.map(::change)
                        val autoTitle = t.title == SavedMaterials.title(initial)
                        t.copy(entries = changed, title = if (autoTitle && t.thought && changed.any { it.id == id }) SavedMaterials.title(changed.first { it.id == id }) else t.title)
                    }, notes = d.notes.map { it.copy(entries = it.entries.map(::change)) })
                }
                if (!retained) imageFile?.let { File(Files.dir(applicationContext), it).delete() }
            } catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
            catch (_: Exception) { failed = true }
        }
        store.flush()
        if (failed && runAttemptCount < 3) Result.retry() else Result.success()
    }

    private fun fetch(raw: String, max: Int, type: String): Pair<ByteArray, String> {
        var url = raw
        repeat(5) {
            val uri = URI(url)
            require(SavedMaterials.site(url) != null && uri.port in listOf(-1, 80, 443))
            val addresses = InetAddress.getAllByName(uri.host)
            require(addresses.isNotEmpty() && addresses.none { it.isAnyLocalAddress || it.isLoopbackAddress || it.isLinkLocalAddress || it.isSiteLocalAddress || it.isMulticastAddress || (it.address.size == 16 && (it.address[0].toInt() and 0xfe) == 0xfc) })
            val conn = uri.toURL().openConnection() as HttpURLConnection
            try {
                conn.instanceFollowRedirects = false
                conn.connectTimeout = 8000; conn.readTimeout = 8000
                conn.setRequestProperty("User-Agent", "Margin/1.0 (saved-link preview)")
                conn.setRequestProperty("Accept", if (type == "text/html") "text/html,application/xhtml+xml" else "image/*")
                if (conn.responseCode in 300..399) {
                    url = uri.resolve(conn.getHeaderField("Location") ?: error("redirect")).toString()
                } else {
                    require(conn.responseCode in 200..299)
                    val mime = conn.contentType.orEmpty().lowercase()
                    require(mime.startsWith(type) || (type == "text/html" && mime.startsWith("application/xhtml+xml")))
                    require(conn.contentLengthLong <= max)
                    val bytes = conn.inputStream.use { input ->
                        val out = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (true) {
                            val n = input.read(buffer); if (n < 0) break
                            require(out.size() + n <= max)
                            out.write(buffer, 0, n)
                        }
                        out.toByteArray()
                    }
                    return bytes to url
                }
            } finally { conn.disconnect() }
        }
        error("redirect limit")
    }

    companion object {
        fun schedule(ctx: Context, entryId: String) {
            WorkManager.getInstance(ctx).enqueueUniqueWork("material:$entryId", ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<MaterialPreviewWorker>()
                    .setInputData(workDataOf("entry" to entryId))
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build())
        }
    }
}

internal data class PageMetadata(
    val title: String?,
    val site: String?,
    val image: String?,
    val description: String? = null,
    val articleText: String? = null,
    val readMinutes: Int = 0,
) {
    companion object {
        fun parse(html: String, url: String): PageMetadata {
            fun decode(raw: String): String =
                runCatching { Html.fromHtml(raw, Html.FROM_HTML_MODE_LEGACY)?.toString() }.getOrNull() ?: SavedMaterials.cleanHtmlText(raw)
            fun clean(s: String?) = s?.let { decode(it).replace(Regex("\\s+"), " ").trim().take(300) }?.takeIf { it.isNotBlank() }
            val values = mutableMapOf<String, String>()
            val attr = Regex("([\\w:-]+)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s>]+))")
            Regex("<meta\\b[^>]*>", RegexOption.IGNORE_CASE).findAll(html).forEach { tag ->
                val attrs = attr.findAll(tag.value).associate { it.groupValues[1].lowercase() to it.groupValues.drop(2).firstOrNull { v -> v.isNotEmpty() }.orEmpty() }
                val key = (attrs["property"] ?: attrs["name"])?.lowercase()
                if (key != null) attrs["content"]?.let { values.putIfAbsent(key, it) }
            }
            val title = clean(values["og:title"] ?: values["twitter:title"] ?: Regex("<title\\b[^>]*>(.*?)</title>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).find(html)?.groupValues?.get(1))
            val description = clean(values["og:description"] ?: values["twitter:description"] ?: values["description"])
            val image = (values["og:image"] ?: values["twitter:image"])?.let { raw -> runCatching {
                URI(url).resolve(decode(raw)).toString().takeIf { SavedMaterials.site(it) != null }
            }.getOrNull() }
            val articleText = SavedMaterials.extractArticle(html)
            val readMinutes = SavedMaterials.readingMinutes(articleText ?: description)
            return PageMetadata(title, clean(values["og:site_name"]), image, description, articleText, readMinutes)
        }
    }
}
