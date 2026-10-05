package ru.anjeib.margin

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import ru.anjeib.margin.voice.LocalWhisper
import java.io.File

/** Downloads the base Whisper model on the device and transcribes a Russian sample. */
@RunWith(AndroidJUnit4::class)
class WhisperDeviceTest {
    @Test
    fun transcribesRussian() = runBlocking {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue("native lib", LocalWhisper.supported)
        val want = if (InstrumentationRegistry.getArguments().getString("model") == "small") LocalWhisper.Model.Small else LocalWhisper.Model.Base
        if ((LocalWhisper.state.value as? LocalWhisper.State.Ready)?.model != want || !LocalWhisper.ready(ctx)) LocalWhisper.download(ctx, want)
        assertTrue("model: ${LocalWhisper.state.value}", LocalWhisper.ready(ctx))
        val wav = File(ctx.cacheDir, "dict.wav")
        InstrumentationRegistry.getInstrumentation().context.assets.open("dict.wav").use { i -> wav.outputStream().use { i.copyTo(it) } }
        val t0 = System.currentTimeMillis()
        val text = LocalWhisper.transcribe(ctx, wav, "ru", listOf("Дима", "vuzburo"))
        Log.i("WhisperTest", "(${System.currentTimeMillis() - t0} ms) $text")
        println("WHISPER (${System.currentTimeMillis() - t0} ms): $text")
        assertTrue(text.isNotBlank())
    }
}
