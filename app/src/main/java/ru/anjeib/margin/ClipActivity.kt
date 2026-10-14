package ru.anjeib.margin

import android.content.ClipboardManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import ru.anjeib.margin.ai.Processor
import ru.anjeib.margin.data.Files
import ru.anjeib.margin.data.Prefs
import ru.anjeib.margin.data.Queued
import ru.anjeib.margin.i18n.L
import ru.anjeib.margin.i18n.resolveLang
import ru.anjeib.margin.i18n.stringsFor

/**
 * The tile's blink of a window: reads what is copied — words, a link, a picture — the moment it is in front (only then may an
 * app read the clipboard), puts it in «Входящие» as it is and is gone.
 */
class ClipActivity : ComponentActivity() {
    private var done = false

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus || done) return
        done = true
        val strings = stringsFor(resolveLang(Prefs.get(this).settings.value.lang)).also { L.lang = resolveLang(Prefs.get(this).settings.value.lang) }
        val clip = runCatching { getSystemService(ClipboardManager::class.java)?.primaryClip }.getOrNull()
        val item = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)
        val text = item?.text?.toString()?.trim().orEmpty()
        val uri = item?.uri
        if (text.isBlank() && uri == null) { Toast.makeText(this, strings.clipEmpty, Toast.LENGTH_SHORT).show(); finish(); return }
        lifecycleScope.launch {
            val files = listOfNotNull(uri?.let { runCatching { Files.import(this@ClipActivity, it) }.getOrNull() })
            Processor.toInbox(this@ClipActivity, Queued(text = text, files = files))
            Toast.makeText(this@ClipActivity, strings.inboxSaved, Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
