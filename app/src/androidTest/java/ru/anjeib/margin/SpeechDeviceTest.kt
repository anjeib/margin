package ru.anjeib.margin

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import ru.anjeib.margin.voice.LocalSpeech
import java.io.File

/**
 * GigaAM on the device: the model (files/speech-model) and test recordings (files/speech-set) are copied in beforehand
 * with «adb shell run-as <package>»; each recording (WAV or a voice file of another app) is read and timed. Logs under «Speech».
 */
@RunWith(AndroidJUnit4::class)
class SpeechDeviceTest {
    @Test fun readsTheRecordings() { runBlocking {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        // Put there beforehand with «adb shell run-as <package>»: the model files into files/speech-model, the WAVs into files/speech-set;
        // here they are installed as the built-in version (its manifest and «current»), as a download would leave them.
        val m = LocalSpeech.builtIn
        val dst = File(ctx.filesDir, "models/speech/${m.version}").apply { mkdirs() }
        File(ctx.filesDir, "speech-model").listFiles()?.forEach { it.copyTo(File(dst, it.name), overwrite = true) }
        File(dst, "manifest.json").writeText(ru.anjeib.margin.data.AppJson.encodeToString(ru.anjeib.margin.voice.SpeechManifest.serializer(), m))
        File(ctx.filesDir, "models/speech/current").writeText(m.version)
        LocalSpeech.refresh(ctx)
        assumeTrue(LocalSpeech.state.value is LocalSpeech.State.Ready)
        // Our own WAVs and voice files of other apps: Telegram/WhatsApp .ogg/.opus, .m4a, .mp3, WAV of other rates.
        val wavs = File(ctx.filesDir, "speech-set").listFiles { f -> f.extension.lowercase() in setOf("wav", "ogg", "oga", "opus", "m4a", "aac", "mp3", "flac", "amr") }!!.sortedBy { it.name }
        var total = 0L; var audio = 0.0
        wavs.forEach { w ->
            val t0 = System.currentTimeMillis()
            var steps = 0
            val text = runCatching { LocalSpeech.transcribe(ctx, w, "ru") { steps++ } }.getOrElse { "<${it.javaClass.simpleName}: ${it.message}>" }
            val ms = System.currentTimeMillis() - t0
            val secs = runCatching { android.media.MediaMetadataRetriever().run { try { setDataSource(w.absolutePath); extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L } finally { release() } } }.getOrDefault(0L) / 1000.0
            total += ms; audio += secs
            Log.i("Speech", "${w.name}\t${"%.1f".format(secs)}s\t${ms}ms\tprogress×$steps\t$text")
        }
        Log.i("Speech", "TOTAL ${wavs.size} files, ${"%.1f".format(audio)} s audio, ${total} ms, RTF ${"%.3f".format(total / 1000.0 / audio)}")
    } }

    /**
     * The update path: the phone holds an older version (made beforehand: files/models/speech/current → «gigaam-old»);
     * ensure() asks the door, fetches its version next to the old one while it still reads, switches, and clears the old.
     */
    @Test fun updatesToTheDoorsVersion() { runBlocking {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(ctx.filesDir, "models/speech")
        assumeTrue(File(root, "current").takeIf { it.exists() }?.readText()?.trim() == "gigaam-old")
        LocalSpeech.refresh(ctx)
        val seen = mutableListOf<Float>()
        val watch = kotlinx.coroutines.GlobalScope.launch { LocalSpeech.updating.collect { p -> if (p != null) seen += p } }
        LocalSpeech.ensure(ctx)
        watch.cancel()
        val now = File(root, "current").readText().trim()
        Log.i("Speech", "UPDATE: current=$now, progress seen ${seen.size} times (last ${seen.lastOrNull()}), dirs=${root.list()?.toList()}")
        org.junit.Assert.assertEquals(LocalSpeech.builtIn.version, now)
        org.junit.Assert.assertFalse(File(root, "gigaam-old").exists())
        org.junit.Assert.assertTrue(seen.isNotEmpty())
        org.junit.Assert.assertTrue(LocalSpeech.transcribe(ctx, File(ctx.filesDir, "speech-set").listFiles()!!.first(), "ru").isNotBlank())
    } }
}
