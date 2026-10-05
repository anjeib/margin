package ru.anjeib.margin.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import java.io.File

val AppJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = false
    explicitNulls = false
    isLenient = true
}

/** Whole-app state kept in a single JSON file; every change is written atomically in the background. */
class Store private constructor(context: Context) {
    private val file = File(context.filesDir, "margin.json")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeLock = Mutex()

    private val _data = MutableStateFlow(load())
    val data: StateFlow<AppData> = _data

    private fun load(): AppData = runCatching {
        if (file.exists()) AppJson.decodeFromString(AppData.serializer(), file.readText()).migrated() else AppData()
    }.getOrElse { AppData() }

    fun update(f: (AppData) -> AppData) {
        val next = synchronized(this) { f(_data.value).also { _data.value = it } }
        persist(next)
    }

    fun replace(d: AppData) = update { d }

    private fun persist(d: AppData) {
        scope.launch {
            writeLock.withLock {
                val tmp = File(file.parentFile, file.name + ".tmp")
                tmp.writeText(AppJson.encodeToString(AppData.serializer(), d))
                tmp.renameTo(file)
            }
        }
    }

    companion object {
        @Volatile private var instance: Store? = null
        fun get(context: Context): Store =
            instance ?: synchronized(this) { instance ?: Store(context.applicationContext).also { instance = it } }
    }
}
