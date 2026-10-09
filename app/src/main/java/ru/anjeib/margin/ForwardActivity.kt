package ru.anjeib.margin

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import ru.anjeib.margin.data.Files
import ru.anjeib.margin.data.Prefs
import ru.anjeib.margin.data.ThemeMode
import ru.anjeib.margin.i18n.Lang
import ru.anjeib.margin.i18n.resolveLang
import ru.anjeib.margin.paper.BookVm
import ru.anjeib.margin.paper.HandedSheet
import ru.anjeib.margin.paper.PaperTheme
import ru.anjeib.margin.paper.WordsEn
import ru.anjeib.margin.paper.WordsRu

/**
 * «Поделиться → Margin» and «В Margin» in the text-selection menu: a sheet over the app the person is in (Telegram,
 * mail, a browser). The same field as in the notebook, with the words in it and Margin's notes beside them; «записать»
 * puts the lines on their pages and shows them — the person stays where they were.
 */
class ForwardActivity : ComponentActivity() {
    private val vm: BookVm by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val i = intent
        val text = when (i.action) {
            Intent.ACTION_PROCESS_TEXT -> i.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
            else -> i.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
        }.orEmpty().trim()
        val subject = i.getStringExtra(Intent.EXTRA_SUBJECT)?.takeIf { it.isNotBlank() && !text.startsWith(it) }
        val uris: List<Uri> = when (i.action) {
            Intent.ACTION_SEND -> listOfNotNull(stream(i))
            Intent.ACTION_SEND_MULTIPLE -> streams(i)
            else -> emptyList()
        }
        if (text.isBlank() && uris.isEmpty()) { finish(); return }
        if (savedInstanceState == null) {
            vm.handIn(listOfNotNull(subject, text.ifBlank { null }).joinToString("\n"))
            // The links to shared files only live while this screen does — copy them in now.
            if (uris.isNotEmpty()) lifecycleScope.launch {
                vm.handInFiles(uris.take(10).mapNotNull { u -> Files.import(this@ForwardActivity, u) })
            }
        }
        val from = sourceName()
        setContent {
            val s by Prefs.get(this).settings.collectAsState()
            val words = if (resolveLang(s.lang) == Lang.Ru) WordsRu else WordsEn
            vm.words = words
            val dark = when (s.theme) { ThemeMode.Light -> false; ThemeMode.Dark -> true; ThemeMode.Auto -> isSystemInDarkTheme() }
            PaperTheme(dark, words) {
                HandedSheet(vm, from, onDone = { finish() }, onOpen = { id ->
                    startActivity(Intent(this, MainActivity::class.java).setAction(MainActivity.ACTION_OPEN).putExtra(MainActivity.EXTRA_ID, id?.let { "line:$it" } ?: "today")
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
                    finish()
                })
            }
        }
    }

    /** The app the words came from, by its own name («Telegram», «Gmail»); null when the system does not say. */
    private fun sourceName(): String? {
        val pkg = callingPackage ?: (if (Build.VERSION.SDK_INT >= 34) launchedFromPackage?.takeIf { it != "android" } else null)
            ?: referrer?.takeIf { it.scheme == "android-app" }?.host ?: return null
        if (pkg == packageName || pkg == "com.android.shell") return null
        return runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString() }.getOrNull()
    }

    @Suppress("DEPRECATION")
    private fun stream(i: Intent): Uri? =
        if (Build.VERSION.SDK_INT >= 33) i.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java) else i.getParcelableExtra(Intent.EXTRA_STREAM)

    @Suppress("DEPRECATION")
    private fun streams(i: Intent): List<Uri> =
        (if (Build.VERSION.SDK_INT >= 33) i.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java) else i.getParcelableArrayListExtra(Intent.EXTRA_STREAM)).orEmpty()
}
