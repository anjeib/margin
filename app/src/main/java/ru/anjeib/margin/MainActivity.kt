package ru.anjeib.margin

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import ru.anjeib.margin.data.AppData
import ru.anjeib.margin.data.Store
import ru.anjeib.margin.data.ThemeMode
import ru.anjeib.margin.data.Subtask
import ru.anjeib.margin.data.whenLabel
import ru.anjeib.margin.i18n.L
import ru.anjeib.margin.i18n.resolveLang
import ru.anjeib.margin.i18n.stringsFor
import ru.anjeib.margin.notify.Reminders
import ru.anjeib.margin.ui.App
import ru.anjeib.margin.ui.MainVm
import ru.anjeib.margin.ui.MarginTheme
import java.io.File

class MainActivity : ComponentActivity() {
    private val vm: MainVm by viewModels()
    private val shared = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        readShare(intent)
        // Permissions are asked in onboarding (first run) — no system dialog on top of it.
        Reminders.scheduleDaily(this)
        ru.anjeib.margin.data.Backup.schedule(this)

        setContent {
            val settings by vm.settings.collectAsState()
            val lang = resolveLang(settings.lang)
            L.lang = lang
            val strings = remember(lang) { stringsFor(lang) }
            MarginTheme(settings.theme, strings) {
                val dark = when (settings.theme) {
                    ThemeMode.Light -> false
                    ThemeMode.Dark -> true
                    ThemeMode.Auto -> isSystemInDarkTheme()
                }
                SideEffect {
                    WindowCompat.getInsetsController(window, window.decorView).apply {
                        isAppearanceLightStatusBars = !dark
                        isAppearanceLightNavigationBars = !dark
                    }
                }
                App(vm, shared.value, onSharedConsumed = { shared.value = null }, onExport = ::export)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        readShare(intent)
    }

    private fun readShare(i: Intent?) {
        if (i?.action == Intent.ACTION_SEND) shared.value = i.getStringExtra(Intent.EXTRA_TEXT)
    }

    private fun export() {
        val md = toMarkdown(Store.get(this).data.value)
        val dir = File(cacheDir, "export").apply { mkdirs() }
        val f = File(dir, "margin.md").apply { writeText(md) }
        val uri = FileProvider.getUriForFile(this, "$packageName.files", f)
        startActivity(Intent.createChooser(
            Intent(Intent.ACTION_SEND).setType("text/markdown").putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Margin",
        ))
    }

    private fun toMarkdown(d: AppData): String = buildString {
        val s = L.s
        appendLine("# Margin")
        appendLine()
        appendLine("## ${s.tasks}")
        fun subs(list: List<Subtask>, depth: Int) {
            list.forEach { st ->
                appendLine("  ".repeat(depth + 1) + "- [${if (st.done) "x" else " "}] ${st.title}" + (st.assignee?.let { " — $it" } ?: ""))
                subs(st.children, depth + 1)
            }
        }
        d.tasks.sortedBy { it.done }.forEach { t ->
            val meta = listOfNotNull(t.whenLabel(), t.project?.let { "#$it" }, t.people.takeIf { it.isNotEmpty() }?.joinToString(),
                t.waitingFor?.let { s.waitingFor(it) }).joinToString(" · ")
            appendLine("- [${if (t.done) "x" else " "}] ${t.title}" + if (meta.isNotEmpty()) " ($meta)" else "")
            subs(t.subtasks, 0)
            t.entries.filter { it.text.isNotBlank() }.forEach { appendLine("  > ${it.at.take(10)} — ${it.text}") }
        }
        appendLine()
        appendLine("## ${s.notes}")
        d.notes.forEach { n ->
            appendLine()
            appendLine("### ${n.title.ifBlank { s.untitled }}")
            n.project?.let { appendLine("#$it") }
            n.aiSummary?.let { appendLine("_${it}_"); appendLine() }
            n.entries.forEach { e -> if (e.text.isNotBlank()) { appendLine("**${e.at.take(10)}** ${e.text}"); appendLine() } }
        }
    }
}
