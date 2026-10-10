package ru.anjeib.margin.paper

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.anjeib.margin.data.Files
import ru.anjeib.margin.data.WavRecorder

/*
 * Words said instead of typed — the same everywhere there is somewhere to write (the field, a line's sheet): a recording
 * read into words through the AI's door or by Whisper on the phone; the phone's own recogniser when neither can.
 */
@Stable
class Dictation internal constructor() {
    internal var recorder by mutableStateOf<WavRecorder?>(null)
    /** How loud, 0..1 — for the pencil strokes while listening. */
    var level by mutableFloatStateOf(0f)
        internal set
    /** The recording is being read into words. */
    var hearing by mutableStateOf(false)
        internal set
    val listening: Boolean get() = recorder != null
    internal var begin: () -> Unit = {}
    internal var end: (Boolean) -> Unit = {}

    /** Start listening, or stop and read what was said. */
    fun toggle() = if (listening) end(true) else begin()
    fun stop(keep: Boolean = true) = end(keep)
}

@Composable
fun rememberDictation(vm: BookVm, onWords: (String?) -> Unit): Dictation {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val w = P.w
    val d = remember { Dictation() }
    val words by rememberUpdatedState(onWords)
    val system = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        d.hearing = false
        words(r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull())
    }
    fun record() {
        val r = WavRecorder(ctx)
        if (!r.start()) { vm.tell(w.voiceOff); return }
        d.recorder = r
        scope.launch { while (d.recorder === r) { delay(80); d.level = r.level } }
    }
    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok) record() else vm.tell(w.micDenied) }
    d.end = { keep ->
        val r = d.recorder
        if (r != null) {
            d.recorder = null
            val audio = r.stop()
            if (audio != null) {
                if (!keep) Files.delete(ctx, audio)
                else { d.hearing = true; vm.transcribe(audio) { t -> d.hearing = false; words(t) } }
            }
        }
    }
    d.begin = {
        if (!vm.canHear()) {
            val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (w.ru) "ru-RU" else "en-US")
            runCatching { system.launch(i) }.onFailure { vm.tell(w.voiceOff) }
        } else if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) record()
        else askMic.launch(Manifest.permission.RECORD_AUDIO)
    }
    return d
}
