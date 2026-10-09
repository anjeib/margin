package ru.anjeib.margin

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import ru.anjeib.margin.data.Prefs
import ru.anjeib.margin.data.ThemeMode
import ru.anjeib.margin.i18n.Lang
import ru.anjeib.margin.i18n.resolveLang
import ru.anjeib.margin.paper.BookScreen
import ru.anjeib.margin.paper.BookVm
import ru.anjeib.margin.paper.P
import ru.anjeib.margin.paper.PaperTheme
import ru.anjeib.margin.paper.Voice
import ru.anjeib.margin.paper.WordsEn
import ru.anjeib.margin.paper.WordsRu

/** Margin «Поля»: one activity, the notebook in it (docs/polya/DESIGN.md). */
class MainActivity : androidx.fragment.app.FragmentActivity() {
    private val vm: BookVm by viewModels()
    /** The optional lock (Settings → privacy): closed on start and after a minute away. */
    private val locked = mutableStateOf(false)
    private var awayAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val settings = Prefs.get(this).settings.value
        if (settings.lock && Lock.available(this)) locked.value = true
        if (savedInstanceState == null) take(intent)
        askBellOnce()
        publishShortcuts()
        setContent {
            val s by Prefs.get(this).settings.collectAsState()
            val words = if (resolveLang(s.lang) == Lang.Ru) WordsRu else WordsEn
            vm.words = words
            val dark = when (s.theme) { ThemeMode.Light -> false; ThemeMode.Dark -> true; ThemeMode.Auto -> isSystemInDarkTheme() }
            PaperTheme(dark, words) {
                SideEffect {
                    WindowCompat.getInsetsController(window, window.decorView).apply {
                        isAppearanceLightStatusBars = !dark
                        isAppearanceLightNavigationBars = !dark
                    }
                }
                if (locked.value) Locked { unlock() } else BookScreen(vm)
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        take(intent)
    }

    /** Text handed in («Поделиться» → Margin) waits in the field; a notification opens its line or the ritual. Taken once. */
    private fun take(i: android.content.Intent?) {
        if (i?.action == ACTION_WRITE || i?.action == ACTION_DICTATE) {
            if (i.action == ACTION_DICTATE) vm.dictateNow() else vm.focusNow()
            vm.launchWith("today")
            setIntent(android.content.Intent(i).setAction(android.content.Intent.ACTION_MAIN))
            return
        }
        if (i?.action == ACTION_OPEN) {
            vm.launchWith(i.getStringExtra(EXTRA_ID))
            setIntent(android.content.Intent(i).setAction(android.content.Intent.ACTION_MAIN))
            return
        }
        if (i?.action != android.content.Intent.ACTION_SEND) return
        vm.handIn(i.getStringExtra(android.content.Intent.EXTRA_TEXT))
        setIntent(android.content.Intent(i).apply { action = android.content.Intent.ACTION_MAIN; removeExtra(android.content.Intent.EXTRA_TEXT) })
    }

    /** «Записать» and «Надиктовать» on a long press of the icon. */
    private fun publishShortcuts() = runCatching {
        val w = if (resolveLang(Prefs.get(this).settings.value.lang) == Lang.Ru) WordsRu else WordsEn
        fun one(id: String, label: String, action: String, icon: Int) =
            androidx.core.content.pm.ShortcutInfoCompat.Builder(this, id).setShortLabel(label)
                .setIcon(androidx.core.graphics.drawable.IconCompat.createWithResource(this, icon))
                .setIntent(android.content.Intent(this, MainActivity::class.java).setAction(action)).build()
        androidx.core.content.pm.ShortcutManagerCompat.setDynamicShortcuts(this, listOf(
            one("write", w.a11ySend, ACTION_WRITE, R.drawable.ic_shortcut_write),
            one("dictate", w.a11yMic, ACTION_DICTATE, R.drawable.ic_shortcut_dictate),
        ))
    }

    private val bellAsk = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { }

    /** The bell needs the right to notify (Android 13+): asked once, on the first start. */
    private fun askBellOnce() {
        if (android.os.Build.VERSION.SDK_INT < 33) return
        val sp = getSharedPreferences("polya", MODE_PRIVATE)
        if (sp.getBoolean("bellAsked", false)) return
        sp.edit().putBoolean("bellAsked", true).apply()
        bellAsk.launch(android.Manifest.permission.POST_NOTIFICATIONS)
    }

    override fun onResume() {
        super.onResume()
        vm.refreshToday()
        if (Prefs.get(this).settings.value.lock && awayAt > 0 && System.currentTimeMillis() - awayAt > Lock.GRACE_MS && Lock.available(this)) locked.value = true
        if (locked.value) unlock()
    }

    override fun onStop() { super.onStop(); if (!isChangingConfigurations) awayAt = System.currentTimeMillis() }

    private fun unlock() = Lock.ask(this, "Margin", if (resolveLang(Prefs.get(this).settings.value.lang) == Lang.Ru) "Отмена" else "Cancel") {
        locked.value = false; awayAt = 0
    }

    companion object {
        const val ACTION_WRITE = "ru.anjeib.margin.WRITE"
        const val ACTION_DICTATE = "ru.anjeib.margin.DICTATE"
        const val ACTION_OPEN = "ru.anjeib.margin.OPEN"
        const val EXTRA_ID = "id"
    }
}

/** A locked notebook: the paper and one way in. */
@Composable
private fun Locked(onOpen: () -> Unit) {
    val c = P.c
    Box(Modifier.fillMaxSize().background(c.paper), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Margin", style = Voice.title.copy(color = c.ink))
            TextButton(onOpen) { Text(if (P.w.ru) "Открыть" else "Open", color = c.link) }
        }
    }
}
