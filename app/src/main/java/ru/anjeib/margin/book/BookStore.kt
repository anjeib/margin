package ru.anjeib.margin.book

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import ru.anjeib.margin.data.AppJson
import ru.anjeib.margin.data.LoadProblem
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * The notebook in one JSON file (filesDir/polya.json) — the same care as Margin 0.29: writes go tmp → rename, the
 * previous file stays as .bak, an unreadable file is moved aside and never overwritten.
 */
class BookStore internal constructor(private val file: File, private val legacy: (() -> Book?)? = null) {
    private val dir: File = file.absoluteFile.parentFile
    private val bak = File(dir, file.name + ".bak")
    private val tmp = File(dir, file.name + ".tmp")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _problem = MutableStateFlow<LoadProblem?>(null)
    val problem: StateFlow<LoadProblem?> = _problem

    /** The notebook came from Margin 0.29 on this start: it is written down at once. */
    var imported = false
        private set

    private val _book = MutableStateFlow(load())
    val book: StateFlow<Book> = _book

    @Volatile private var changed = 0L
    private val saved = MutableStateFlow(0L)
    private val dirty = Channel<Unit>(Channel.CONFLATED)

    init {
        if (imported) dirty.trySend(Unit)
        scope.launch {
            for (u in dirty) {
                val upTo = changed
                runCatching { write(_book.value) }
                saved.value = upTo
                onSaved?.invoke()
            }
        }
    }

    /** Every change passes here; the result is written soon after, the newest state only. */
    fun update(f: (Book) -> Book) {
        synchronized(this) { _book.value = f(_book.value); changed++ }
        dirty.trySend(Unit)
    }

    suspend fun flush() {
        val target = changed
        saved.first { it >= target }
    }

    fun dismissProblem() { _problem.value = null }

    private fun load(): Book {
        if (!file.exists() && !bak.exists()) {
            // The first start after Margin 0.29: its notebook is read in once; its own file stays untouched.
            val old = runCatching { legacy?.invoke() }.getOrNull()
            if (old != null && (old.lines.isNotEmpty() || old.persons.isNotEmpty())) { imported = true; return old }
            return Book()
        }
        if (file.exists()) {
            runCatching { decode(file) }.getOrNull()?.let { b ->
                if (b.v > SCHEMA) {
                    val copy = File(dir, "polya.newer-${stamp()}.json")
                    runCatching { file.copyTo(copy, overwrite = true) }
                    _problem.value = LoadProblem(LoadProblem.Kind.NEWER, copy.name)
                }
                return b
            }
            val aside = File(dir, "polya.broken-${stamp()}.json")
            if (!file.renameTo(aside)) runCatching { file.copyTo(aside, overwrite = true); file.delete() }
            val fromBak = readBak()
            _problem.value = LoadProblem(if (fromBak != null) LoadProblem.Kind.RESTORED else LoadProblem.Kind.LOST, aside.name)
            return fromBak ?: Book()
        }
        readBak()?.let { return it }
        _problem.value = LoadProblem(LoadProblem.Kind.LOST, "polya.broken-bak-${stamp()}.json")
        return Book()
    }

    private fun readBak(): Book? {
        if (!bak.exists()) return null
        return runCatching { decode(bak) }.getOrElse {
            bak.renameTo(File(dir, "polya.broken-bak-${stamp()}.json"))
            null
        }
    }

    private fun decode(f: File): Book = AppJson.decodeFromString(Book.serializer(), f.readText())

    private fun write(b: Book) {
        val bytes = AppJson.encodeToString(Book.serializer(), b.copy(v = SCHEMA)).toByteArray()
        FileOutputStream(tmp).use { it.write(bytes); it.fd.sync() }
        if (file.exists()) { bak.delete(); file.renameTo(bak) }
        if (!tmp.renameTo(file)) { tmp.copyTo(file, overwrite = true); tmp.delete() }
    }

    private fun stamp() = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))

    companion object {
        const val SCHEMA = 1
        @Volatile var onSaved: (() -> Unit)? = null
        @Volatile private var instance: BookStore? = null
        fun get(context: Context): BookStore = instance ?: synchronized(this) {
            instance ?: BookStore(File(context.applicationContext.filesDir, "polya.json")) { legacy(context.applicationContext.filesDir) }.also { instance = it }
        }

        /** Margin 0.29's notebook (margin.json, else its .bak) in the shape of «Поля»; null when there is none. */
        private fun legacy(dir: File): Book? {
            val f = listOf(File(dir, "margin.json"), File(dir, "margin.json.bak")).firstOrNull { it.exists() } ?: return null
            val old = AppJson.decodeFromString(ru.anjeib.margin.data.AppData.serializer(), f.readText()).migrated()
            return Import.from(old)
        }
    }
}
