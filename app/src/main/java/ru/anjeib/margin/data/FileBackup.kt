package ru.anjeib.margin.data

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * A copy of everything in one .zip the user keeps where they like (owner, 18 окт, for RuStore: «всё на телефоне» needs a way
 * to a new phone). Inside: «margin.json» — the records as the app keeps them — and «att/…», the files of notes and matters.
 * Nothing leaves the phone by itself: the user picks the place (Files, Drive, a card) in the system's own window.
 */
object FileBackup {
    private const val DATA = "margin.json"
    private const val ATT = "att/"

    fun write(ctx: Context, to: Uri, d: AppData) {
        val out = ctx.contentResolver.openOutputStream(to, "wt") ?: error("no stream")
        ZipOutputStream(out.buffered()).use { z ->
            z.putNextEntry(ZipEntry(DATA))
            z.write(AppJson.encodeToString(AppData.serializer(), d.copy(v = Store.SCHEMA)).toByteArray())
            z.closeEntry()
            Files.dir(ctx).listFiles()?.filter { it.isFile }?.forEach { f ->
                z.putNextEntry(ZipEntry(ATT + f.name)); f.inputStream().use { it.copyTo(z) }; z.closeEntry()
            }
        }
    }

    /** The records of a copy, its files already in place; null — not a copy of Margin (nothing is touched then). */
    fun read(ctx: Context, from: Uri): AppData? {
        // Into a side folder first: a broken or foreign file must not leave half its files among the user's.
        val side = File(ctx.cacheDir, "restore").apply { deleteRecursively(); mkdirs() }
        var data: AppData? = null
        runCatching {
            ctx.contentResolver.openInputStream(from)?.use { input ->
                ZipInputStream(input.buffered()).use { z ->
                    while (true) {
                        val e = z.nextEntry ?: break
                        val name = e.name
                        when {
                            name == DATA -> data = AppJson.decodeFromString(AppData.serializer(), z.readBytes().decodeToString())
                            name.startsWith(ATT) && !e.isDirectory -> {
                                val base = File(name.removePrefix(ATT)).name  // no «../» out of the folder
                                if (base.isNotBlank()) File(side, base).outputStream().use { z.copyTo(it) }
                            }
                        }
                    }
                }
            }
        }
        val d = data ?: run { side.deleteRecursively(); return null }
        val att = Files.dir(ctx)
        side.listFiles()?.forEach { f -> f.copyTo(File(att, f.name), overwrite = true) }
        side.deleteRecursively()
        return d.migrated()
    }
}
