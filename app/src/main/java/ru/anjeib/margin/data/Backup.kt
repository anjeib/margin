package ru.anjeib.margin.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Full backup as a .zip: data (tasks, notes, people), settings without the API key, and every attachment.
 * Written to a document the user picked once (e.g. on Google Drive) — daily and on demand.
 */
object Backup {
    private const val DATA = "margin.json"
    private const val SETTINGS = "settings.json"

    suspend fun write(ctx: Context, out: OutputStream) = withContext(Dispatchers.IO) {
        val d = Store.get(ctx).data.value
        val s = Prefs.get(ctx).settings.value
        ZipOutputStream(out.buffered()).use { z ->
            z.putNextEntry(ZipEntry(DATA)); z.write(AppJson.encodeToString(AppData.serializer(), d.copy(chat = emptyList())).toByteArray()); z.closeEntry()
            val settings = kotlinx.serialization.json.buildJsonObject {
                put("mainModel", kotlinx.serialization.json.JsonPrimitive(s.mainModel))
                put("fastModel", kotlinx.serialization.json.JsonPrimitive(s.fastModel))
                put("voiceModel", kotlinx.serialization.json.JsonPrimitive(s.voiceModel))
                put("userName", kotlinx.serialization.json.JsonPrimitive(s.userName))
                put("theme", kotlinx.serialization.json.JsonPrimitive(s.theme.name))
                put("lang", kotlinx.serialization.json.JsonPrimitive(s.lang.name))
            }
            z.putNextEntry(ZipEntry(SETTINGS)); z.write(settings.toString().toByteArray()); z.closeEntry()
            Files.dir(ctx).listFiles()?.filter { it.isFile }?.forEach { f ->
                z.putNextEntry(ZipEntry("att/" + f.name)); f.inputStream().use { it.copyTo(z) }; z.closeEntry()
            }
        }
    }

    /** Writes to the saved backup document. Returns null on success or an error message. */
    suspend fun toSavedTarget(ctx: Context): String? {
        val p = Prefs.get(ctx)
        val uri = p.settings.value.backupUri.ifBlank { return "no target" }
        return try {
            ctx.contentResolver.openOutputStream(Uri.parse(uri), "wt")?.use { write(ctx, it) } ?: return "no stream"
            p.update { it.copy(lastBackup = nowStamp()) }
            null
        } catch (e: Exception) { e.message ?: e.toString() }
    }

    /** Replaces everything with the archive's content (the API key and device settings stay). */
    suspend fun restore(ctx: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val dir = Files.dir(ctx)
        val tmp = File(ctx.cacheDir, "restore").apply { deleteRecursively(); mkdirs() }
        var data: AppData? = null
        var settings: kotlinx.serialization.json.JsonObject? = null
        runCatching {
            ctx.contentResolver.openInputStream(uri)?.use { input ->
                ZipInputStream(input.buffered()).use { z ->
                    while (true) {
                        val e = z.nextEntry ?: break
                        when {
                            e.name == DATA -> data = AppJson.decodeFromString(AppData.serializer(), z.readBytes().decodeToString()).migrated()
                            e.name == SETTINGS -> settings = AppJson.parseToJsonElement(z.readBytes().decodeToString()) as? kotlinx.serialization.json.JsonObject
                            e.name.startsWith("att/") && !e.name.contains("..") -> File(tmp, e.name.removePrefix("att/")).outputStream().use { z.copyTo(it) }
                        }
                    }
                }
            }
        }
        val d = data ?: return@withContext false
        dir.listFiles()?.forEach { it.delete() }
        tmp.listFiles()?.forEach { it.renameTo(File(dir, it.name)) }
        Store.get(ctx).replace(d)
        settings?.let { o ->
            fun str(k: String) = (o[k] as? kotlinx.serialization.json.JsonPrimitive)?.content
            Prefs.get(ctx).update { s ->
                s.copy(
                    mainModel = str("mainModel") ?: s.mainModel, fastModel = str("fastModel") ?: s.fastModel,
                    voiceModel = str("voiceModel") ?: s.voiceModel, userName = str("userName") ?: s.userName,
                    theme = runCatching { ThemeMode.valueOf(str("theme")!!) }.getOrDefault(s.theme),
                    lang = runCatching { LangMode.valueOf(str("lang")!!) }.getOrDefault(s.lang),
                )
            }
        }
        true
    }

    fun persist(ctx: Context, uri: Uri) {
        runCatching {
            ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
        Prefs.get(ctx).update { it.copy(backupUri = uri.toString()) }
        schedule(ctx)
    }

    fun schedule(ctx: Context) {
        val wm = WorkManager.getInstance(ctx)
        if (Prefs.get(ctx).settings.value.backupUri.isBlank()) { wm.cancelUniqueWork("backup"); return }
        wm.enqueueUniquePeriodicWork("backup", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<BackupWorker>(1, TimeUnit.DAYS).build())
    }
}

class BackupWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result = if (Backup.toSavedTarget(applicationContext) == null) Result.success() else Result.retry()
}
