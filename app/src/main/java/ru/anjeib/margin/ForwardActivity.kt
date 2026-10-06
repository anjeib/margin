package ru.anjeib.margin

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import ru.anjeib.margin.ai.Forward
import ru.anjeib.margin.ai.Processor
import ru.anjeib.margin.data.Attachment
import ru.anjeib.margin.data.Files
import ru.anjeib.margin.data.Prefs
import ru.anjeib.margin.data.Queued
import ru.anjeib.margin.i18n.L
import ru.anjeib.margin.i18n.resolveLang
import ru.anjeib.margin.i18n.stringsFor
import ru.anjeib.margin.ui.ForwardSheet
import ru.anjeib.margin.ui.MarginTheme

/**
 * «Поделиться → Margin» and «В Margin» in the text-selection menu: a sheet over the app the user is in
 * (Telegram, MAX, mail, a browser). The words go to «Входящие» to be sorted; the user stays where they were.
 */
class ForwardActivity : ComponentActivity() {
    private val files = mutableStateListOf<Attachment>()
    private val loading = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val prefs = Prefs.get(this)
        val i = intent
        val text = when (i.action) {
            Intent.ACTION_PROCESS_TEXT -> i.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
            else -> i.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
        }.orEmpty()
        val subject = i.getStringExtra(Intent.EXTRA_SUBJECT)?.takeIf { it.isNotBlank() && !text.startsWith(it) }
        val via = Forward.viaOf(sourcePackage())
        val uris: List<Uri> = when (i.action) {
            Intent.ACTION_SEND -> listOfNotNull(stream(i))
            Intent.ACTION_SEND_MULTIPLE -> streams(i)
            else -> emptyList()
        }
        if (text.isBlank() && uris.isEmpty()) { finish(); return }
        // The links to shared files only live while this screen does — copy them in now.
        if (uris.isNotEmpty() && savedInstanceState == null) {
            loading.value = true
            lifecycleScope.launch {
                uris.take(10).forEach { u -> Files.import(this@ForwardActivity, u)?.let { files += it } }
                loading.value = false
            }
        }

        setContent {
            val settings by prefs.settings.collectAsState()
            val lang = resolveLang(settings.lang)
            L.lang = lang
            val strings = remember(lang) { stringsFor(lang) }
            MarginTheme(settings.theme, strings) {
                ForwardSheet(
                    from = strings.forwardFrom(Forward.viaName(via)),
                    text = listOfNotNull(subject, text).joinToString("\n"),
                    files = files.size,
                    loading = loading.value,
                    onSend = { comment ->
                        Processor.enqueue(this, Queued(
                            text = text, files = files.toList(), via = via, comment = comment.ifBlank { null }, subject = subject,
                        ))
                        Toast.makeText(this, strings.forwardDone, Toast.LENGTH_SHORT).show()
                        finish()
                    },
                    onDismiss = {
                        files.forEach { Files.delete(this, it) }
                        finish()
                    },
                )
            }
        }
    }

    /** Which app sent it: the text-selection menu tells it directly; a share goes through the system chooser. */
    private fun sourcePackage(): String? {
        callingPackage?.let { return it }
        if (Build.VERSION.SDK_INT >= 34) launchedFromPackage?.let { if (it != "android") return it }
        return referrer?.takeIf { it.scheme == "android-app" }?.host
    }

    @Suppress("DEPRECATION")
    private fun stream(i: Intent): Uri? =
        if (Build.VERSION.SDK_INT >= 33) i.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java) else i.getParcelableExtra(Intent.EXTRA_STREAM)

    @Suppress("DEPRECATION")
    private fun streams(i: Intent): List<Uri> =
        (if (Build.VERSION.SDK_INT >= 33) i.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java) else i.getParcelableArrayListExtra(Intent.EXTRA_STREAM)).orEmpty()
}
