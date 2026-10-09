package ru.anjeib.margin.data

import kotlinx.serialization.json.Json

/** One JSON for every file the app keeps: tolerant to fields it doesn't know, quiet about the defaults. */
val AppJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = false
    explicitNulls = false
    isLenient = true
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
