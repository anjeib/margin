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
    /** A message copied in another app a moment ago (MAX, Telegram «Копировать»): offered as «Вставить скопированное». */
    private val copied = mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        readShare(intent)
        // Permissions are asked in onboarding (first run) — no system dialog on top of it.
        Reminders.scheduleDaily(this)
        // The daily file copy is gone (3.10): drop its job where earlier versions scheduled it.
        androidx.work.WorkManager.getInstance(this).cancelUniqueWork("backup")
        ru.anjeib.margin.ai.Forward.load(this)
        ru.anjeib.margin.ai.Forward.schedule(this)

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
                App(vm, shared.value, onSharedConsumed = { shared.value = null },
                    copied = copied.value != null,
                    readCopied = { readCopied() },
                    onCopiedSeen = { copied.value?.let { seenClip(it) }; copied.value = null })
            }
        }
    }

    override fun onResume() { super.onResume(); vm.foreground(true) }
    override fun onPause() { vm.foreground(false); super.onPause() }

    // Only the clip's description is looked at (no «вставлено из буфера» message); the text is read on a tap.
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) return
        val cm = getSystemService(android.content.ClipboardManager::class.java) ?: return
        val d = runCatching { cm.primaryClipDescription }.getOrNull() ?: return
        val at = d.timestamp
        val fresh = System.currentTimeMillis() - at < 10 * 60_000
        val text = d.hasMimeType("text/*")
        val seen = getSharedPreferences("forward", MODE_PRIVATE).getLong("clipSeen", 0)
        copied.value = if (fresh && text && at != seen && at > 0) at else null
    }

    private fun readCopied(): String? {
        val cm = getSystemService(android.content.ClipboardManager::class.java) ?: return null
        return runCatching { cm.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString() }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    private fun seenClip(at: Long) = getSharedPreferences("forward", MODE_PRIVATE).edit().putLong("clipSeen", at).apply()

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        readShare(intent)
    }

    private fun readShare(i: Intent?) {
        if (i?.action == Intent.ACTION_SEND) shared.value = i.getStringExtra(Intent.EXTRA_TEXT)
    }
}
