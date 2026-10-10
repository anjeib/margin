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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import ru.anjeib.margin.ui.MarginTheme
import java.io.File

class MainActivity : androidx.fragment.app.FragmentActivity() {
    private val vm: MainVm by viewModels()
    private val shared = mutableStateOf<String?>(null)
    /** «Записать» / «Надиктовать» from the quick-settings tile or a long press on the icon; the time makes each one new. */
    private val launch = mutableStateOf<Pair<String, Long>?>(null)
    /** The optional lock (Settings → Приватность): closed on start and after a minute away. */
    private val locked = mutableStateOf(false)
    private var awayAt = 0L
    /** A message copied in another app a moment ago (MAX, Telegram «Копировать»): offered as «Вставить скопированное». */
    private val copied = mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Recreated (rotation, theme) or reopened from recents: the shared text was already taken in.
        if (savedInstanceState == null && intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY == 0) readShare(intent)
        publishShortcuts()
        if (vm.settings.value.lock && Lock.available(this)) locked.value = true
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
                val d by vm.data.collectAsState()
                androidx.compose.runtime.CompositionLocalProvider(ru.anjeib.margin.ui.LocalPeopleLinks provides ru.anjeib.margin.ui.PeopleLinks(
                    isContact = { n -> d.persons.any { it.isContact && it.matches(n) } }, onName = vm::nameTapped,
                ), ru.anjeib.margin.ui.LocalTomorrow provides { id -> vm.postponeTo(id, java.time.LocalDate.now().plusDays(1)) }) {
                if (locked.value) LockScreen(strings.lockOpen) { unlock() } else
                App(vm, shared.value, onSharedConsumed = { shared.value = null },
                    launch = launch.value, onLaunchUsed = { launch.value = null },
                    copied = copied.value != null,
                    readCopied = { readCopied() },
                    onCopiedSeen = { copied.value?.let { seenClip(it) }; copied.value = null })
                }
            }
        }
    }

    override fun onResume() {
        super.onResume(); vm.foreground(true)
        if (vm.settings.value.lock && awayAt > 0 && System.currentTimeMillis() - awayAt > Lock.GRACE_MS && Lock.available(this)) locked.value = true
        if (locked.value) unlock()
    }
    override fun onPause() { vm.foreground(false); super.onPause() }
    override fun onStop() { super.onStop(); if (!isChangingConfigurations) awayAt = System.currentTimeMillis() }

    private fun unlock() {
        val s = stringsFor(resolveLang(vm.settings.value.lang))
        Lock.ask(this, "Margin", s.cancel) { locked.value = false; awayAt = 0 }
    }

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
        when (i?.action) {
            ACTION_WRITE -> launch.value = "write" to System.currentTimeMillis()
            ACTION_DICTATE -> launch.value = "dictate" to System.currentTimeMillis()
            // «Открыть в Margin» from the «В Margin» sheet: straight to what the shared words became.
            ACTION_OPEN -> i.getStringExtra(EXTRA_ID)?.let { launch.value = "open:$it" to System.currentTimeMillis() }
        }
        if (i?.action == ACTION_WRITE || i?.action == ACTION_DICTATE || i?.action == ACTION_OPEN) setIntent(Intent(i).setAction(Intent.ACTION_MAIN))
        if (i?.action != Intent.ACTION_SEND) return
        shared.value = i.getStringExtra(Intent.EXTRA_TEXT)
        // Taken once: whatever recreates the activity later gets an intent with nothing to share.
        setIntent(Intent(i).apply { action = Intent.ACTION_MAIN; removeExtra(Intent.EXTRA_TEXT) })
    }

    /** Long press on the icon: «Записать» and «Надиктовать», in the app's own language. */
    private fun publishShortcuts() = runCatching {
        val s = stringsFor(resolveLang(vm.settings.value.lang))
        fun one(id: String, label: String, action: String, icon: Int) =
            androidx.core.content.pm.ShortcutInfoCompat.Builder(this, id).setShortLabel(label)
                .setIcon(androidx.core.graphics.drawable.IconCompat.createWithResource(this, icon))
                .setIntent(Intent(this, MainActivity::class.java).setAction(action)).build()
        androidx.core.content.pm.ShortcutManagerCompat.setDynamicShortcuts(this, listOf(
            one("write", s.tileWrite, ACTION_WRITE, R.drawable.ic_shortcut_write),
            one("dictate", s.shortcutDictate, ACTION_DICTATE, R.drawable.ic_shortcut_dictate),
        ))
    }

    companion object {
        const val ACTION_WRITE = "ru.anjeib.margin.WRITE"
        const val ACTION_DICTATE = "ru.anjeib.margin.DICTATE"
        const val ACTION_OPEN = "ru.anjeib.margin.OPEN"
        const val EXTRA_ID = "id"
    }
}

/** What a locked Margin shows: the paper and one way in. */
@androidx.compose.runtime.Composable
private fun LockScreen(open: String, onOpen: () -> Unit) {
    val c = ru.anjeib.margin.ui.M.c
    androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize().background(c.bg), contentAlignment = androidx.compose.ui.Alignment.Center) {
        androidx.compose.foundation.layout.Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
            androidx.compose.material3.Text("Margin", style = ru.anjeib.margin.ui.Type.display.copy(color = c.ink))
            androidx.compose.material3.TextButton(onOpen) { androidx.compose.material3.Text(open, color = c.link) }
        }
    }
}
