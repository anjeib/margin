package ru.anjeib.margin.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

val AppJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = false
    explicitNulls = false
    isLenient = true
}

/**
 * Whole-app state kept in a single JSON file.
 *
 * Nothing the user wrote is ever overwritten by a failed read: an unreadable file is moved aside
 * (margin.broken-<time>.json) and the previous copy (margin.json.bak) is used instead. Writes go
 * tmp → rename, one writer at a time, always the newest state (fast changes fold into one write).
 */
class Store internal constructor(private val file: File) {
    private val dir: File = file.absoluteFile.parentFile
    private val bak = File(dir, file.name + ".bak")
    private val tmp = File(dir, file.name + ".tmp")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _problem = MutableStateFlow<LoadProblem?>(null)
    /** Set when the saved file could not be read as is; the UI tells the user once. */
    val problem: StateFlow<LoadProblem?> = _problem

    private val _data = MutableStateFlow(load())
    val data: StateFlow<AppData> = _data

    // Every update bumps [changed]; the writer saves the newest state and reports it in [saved].
    @Volatile private var changed = 0L
    private val saved = MutableStateFlow(0L)
    private val dirty = Channel<Unit>(Channel.CONFLATED)

    /** The last failed write, for the developer section; the next change tries again. */
    @Volatile var lastWriteError: Throwable? = null
        private set

    init {
        scope.launch {
            for (u in dirty) {
                val upTo = changed
                runCatching { write(_data.value) }.onFailure { lastWriteError = it }
                saved.value = upTo
            }
        }
    }

    fun update(f: (AppData) -> AppData) {
        // Every change passes one place, so a topic's feed lines are written the same way whoever made the change.
        synchronized(this) { val cur = _data.value; _data.value = Topics.track(cur, f(cur)); changed++ }
        dirty.trySend(Unit)
    }

    fun replace(d: AppData) = update { d }

    /** Back to a saved state (undo): nothing new happened, so no feed lines either. */
    fun restore(f: (AppData) -> AppData) {
        synchronized(this) { _data.value = f(_data.value); changed++ }
        dirty.trySend(Unit)
    }

    /** Waits until everything changed so far is on disk (receivers that may be killed right after, tests). */
    suspend fun flush() {
        val target = changed
        saved.first { it >= target }
    }

    fun dismissProblem() { _problem.value = null }

    private fun load(): AppData {
        // A write may have been cut between «margin.json → .bak» and «.tmp → margin.json».
        if (!file.exists() && !bak.exists()) return AppData()
        if (file.exists()) {
            val read = runCatching { decode(file) }
            read.getOrNull()?.let { d ->
                if (d.v > SCHEMA) {
                    // Written by a newer build: this one would drop what it does not know. Keep a copy.
                    val copy = File(dir, "margin.newer-${stamp()}.json")
                    runCatching { file.copyTo(copy, overwrite = true) }
                    _problem.value = LoadProblem(LoadProblem.Kind.NEWER, copy.name)
                }
                return d.migrated()
            }
            val aside = File(dir, "margin.broken-${stamp()}.json")
            if (!file.renameTo(aside)) runCatching { file.copyTo(aside, overwrite = true); file.delete() }
            val fromBak = readBak()
            _problem.value = LoadProblem(if (fromBak != null) LoadProblem.Kind.RESTORED else LoadProblem.Kind.LOST, aside.name)
            return fromBak?.migrated() ?: AppData()
        }
        readBak()?.let { return it.migrated() }
        _problem.value = LoadProblem(LoadProblem.Kind.LOST, "margin.broken-bak-${stamp()}.json")
        return AppData()
    }

    /** The previous copy, or null; an unreadable one is moved aside so the next write cannot delete it. */
    private fun readBak(): AppData? {
        if (!bak.exists()) return null
        return runCatching { decode(bak) }.getOrElse {
            bak.renameTo(File(dir, "margin.broken-bak-${stamp()}.json"))
            null
        }
    }

    private fun decode(f: File): AppData = AppJson.decodeFromString(AppData.serializer(), f.readText())

    private fun write(d: AppData) {
        val bytes = AppJson.encodeToString(AppData.serializer(), d.copy(v = SCHEMA)).toByteArray()
        FileOutputStream(tmp).use { it.write(bytes); it.fd.sync() }
        // The previous file becomes the backup; if that fails, the old backup stays — still a good copy.
        if (file.exists()) { bak.delete(); file.renameTo(bak) }
        if (!tmp.renameTo(file)) {
            tmp.copyTo(file, overwrite = true)
            tmp.delete()
        }
    }

    private fun stamp() = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))

    companion object {
        /** Bump with every change of [AppData] that an older build would read wrongly; migrate by number. */
        const val SCHEMA = 1

        @Volatile private var instance: Store? = null
        fun get(context: Context): Store =
            instance ?: synchronized(this) {
                instance ?: Store(File(context.applicationContext.filesDir, "margin.json")).also { instance = it }
            }
    }
}

/** What went wrong reading the saved file; [file] is the copy kept aside in filesDir. */
data class LoadProblem(val kind: Kind, val file: String) {
    enum class Kind {
        /** The file was unreadable; the previous copy was used — the last change or so may be missing. */
        RESTORED,
        /** Neither the file nor its backup could be read; both are kept, the app starts empty. */
        LOST,
        /** The file came from a newer version of the app; a copy is kept. */
        NEWER,
    }
}
