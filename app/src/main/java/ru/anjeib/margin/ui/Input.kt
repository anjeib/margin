package ru.anjeib.margin.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.provider.ContactsContract
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.anjeib.margin.data.Attachment
import ru.anjeib.margin.data.Files
import ru.anjeib.margin.data.WavRecorder
import ru.anjeib.margin.data.durationLabel
import ru.anjeib.margin.i18n.L
import ru.anjeib.margin.i18n.Lang
import java.io.File

/**
 * The one input of the app: text, voice (AI transcription), and attachments behind "+".
 */
@Composable
fun SmartComposer(
    vm: MainVm,
    hint: String,
    busy: Boolean,
    onSend: (text: String, files: List<Attachment>) -> Unit,
    modifier: Modifier = Modifier,
    prefill: String? = null,
    onPrefillUsed: () -> Unit = {},
    onTyping: (Boolean) -> Unit = {},
    /** «Подзадача», «Место»…: the field is filling one thing; × goes back to free text. */
    modeLabel: String? = null,
    onModeClose: () -> Unit = {},
    /** Changes when the field should take focus (and the keyboard come up) by itself. */
    focusKey: Any? = null,
    /** The send button works with nothing typed (forwarding: the words are already there). */
    sendEmpty: Boolean = false,
) {
    val c = M.c
    val s = M.s
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by vm.settings.collectAsState()
    val working by vm.working.collectAsState()
    var text by rememberSaveable { mutableStateOf("") }
    val files = remember { mutableStateListOf<Attachment>() }
    var menu by remember { mutableStateOf(false) }
    var recorder by remember { mutableStateOf<WavRecorder?>(null) }
    var level by remember { mutableFloatStateOf(0f) }
    var cameraFile by remember { mutableStateOf<File?>(null) }
    val transcribing = "voice" in working

    // Text put in for a mode (a caption to edit) leaves with the mode, unless the user changed it into something else.
    var fromMode by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(prefill) { if (!prefill.isNullOrBlank()) { text = prefill; if (modeLabel != null) fromMode = prefill; onPrefillUsed() } }
    LaunchedEffect(modeLabel) { if (modeLabel == null) { if (fromMode != null && text == fromMode) text = ""; fromMode = null } }
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(focusKey) { if (focusKey != null) { delay(150); runCatching { focus.requestFocus() } } }
    LaunchedEffect(text, files.size, recorder) { onTyping(text.isNotEmpty() || files.isNotEmpty() || recorder != null) }

    fun add(a: Attachment?) { if (a != null) files += a }
    val pickMedia = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(6)) { uris ->
        uris.forEach { u -> scope.launch { add(Files.import(ctx, u)) } }
    }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val f = cameraFile
        if (ok && f != null && f.length() > 0) add(Files.fromCamera(f)) else f?.delete()
    }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u: Uri? ->
        if (u != null) scope.launch { add(Files.import(ctx, u)) }
    }
    val pickContact = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        r.data?.data?.let { u -> Files.readContact(ctx, u)?.let { add(vm.contactAttachment(it)) } }
    }
    val systemVoice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { text = (text.trimEnd() + " " + it).trim() }
    }

    // Dictation is words, not a recording. The recording stays only when asked for: «голосовое: …» / «voice note …».
    val voiceWord = remember { Regex("^\\s*(голосов\\p{L}*|аудио|voice\\s*note|voice\\s*message)\\s*[:,.—-]?\\s*", RegexOption.IGNORE_CASE) }
    fun gotSpeech(t: String, audio: Attachment) {
        if (voiceWord.containsMatchIn(t)) {
            // One block: the player with what was said under it — not the same words twice.
            val said = t.replace(voiceWord, "").trim().replaceFirstChar { it.uppercaseChar() }
            onSend(text.trim(), files.toList() + audio.copy(transcript = said.ifBlank { null })); text = ""; files.clear()
            return
        }
        Files.delete(ctx, audio)
        val full = (text.trimEnd() + " " + t).trim()
        if (settings.voiceSendNow) { onSend(full, files.toList()); text = ""; files.clear() } else text = full
    }

    fun startRecording() {
        val r = WavRecorder(ctx)
        if (!r.start()) { Toast.makeText(ctx, s.voiceUnavailable, Toast.LENGTH_SHORT).show(); return }
        recorder = r
        scope.launch { while (recorder === r) { delay(100); level = r.level } }
    }
    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) startRecording() else Toast.makeText(ctx, s.micPermission, Toast.LENGTH_SHORT).show()
    }

    fun mic() {
        if (!settings.voiceAi) {
            val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (L.lang == Lang.Ru) "ru-RU" else "en-US")
            runCatching { systemVoice.launch(i) }.onFailure { Toast.makeText(ctx, s.voiceUnavailable, Toast.LENGTH_SHORT).show() }
            return
        }
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startRecording()
        else askMic.launch(Manifest.permission.RECORD_AUDIO)
    }

    Column(modifier.fillMaxWidth()) {
        if (modeLabel != null) Row(Modifier.padding(start = 6.dp, bottom = 6.dp).clip(RoundedCornerShape(50)).background(c.linkSoft)
            .clickable(onClick = onModeClose).padding(start = 12.dp, end = 8.dp, top = 5.dp, bottom = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(modeLabel, style = Type.small.copy(color = c.link, fontSize = 13.5.sp))
            Spacer(Modifier.width(6.dp))
            Icon(Icons.Rounded.Close, s.dismiss, tint = c.link, modifier = Modifier.size(14.dp))
        }
        if (files.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                files.toList().forEach { a -> AttachmentChip(a, onRemove = { files.remove(a); if (a.file != null) Files.delete(ctx, a) }) }
            }
        }
        val rec = recorder
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(c.card).border(1.dp, c.hairline, RoundedCornerShape(28.dp))
                .padding(start = 4.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            // Long text grows upwards; the buttons stay where the thumb is.
            verticalAlignment = Alignment.Bottom,
        ) {
            if (rec != null) {
                // Recording bar: cancel · pulsing dot + level · confirm
                RoundIcon(Icons.Rounded.Close, c.graphite) { recorder = null; rec.cancel() }
                Spacer(Modifier.width(6.dp))
                Box(Modifier.size(10.dp).clip(CircleShape).background(c.accent.copy(alpha = 0.4f + level * 0.6f)))
                Spacer(Modifier.width(10.dp))
                LevelBars(level, Modifier.weight(1f))
                Spacer(Modifier.width(6.dp))
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(c.accent).clickable {
                        recorder = null
                        val audio = rec.stop()
                        if (audio != null) vm.transcribe(audio) { gotSpeech(it, audio) }
                    },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Check, null, tint = Color.White) }
                return@Row
            }
            Box {
                RoundIcon(Icons.Rounded.Add, c.graphite, s.a11yAttach) { menu = true }
                SoftMenu(menu, { menu = false }) {
                    MenuItem(Icons.Outlined.PhotoLibrary, s.attachPhoto) { menu = false; pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) }
                    MenuItem(Icons.Outlined.PhotoCamera, s.attachCamera) {
                        menu = false
                        val (f, uri) = Files.newPhotoFile(ctx); cameraFile = f
                        runCatching { takePhoto.launch(uri) }
                    }
                    MenuItem(Icons.Outlined.AttachFile, s.attachFile) { menu = false; pickFile.launch(arrayOf("*/*")) }
                    MenuItem(Icons.Outlined.PersonOutline, s.attachContact) {
                        menu = false
                        runCatching { pickContact.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)) }
                    }
                }
            }
            Box(Modifier.weight(1f).padding(vertical = 10.dp, horizontal = 4.dp)) {
                if (text.isEmpty()) Text(if (transcribing) s.transcribing else hint, style = Type.body.copy(color = c.graphite), maxLines = 1, overflow = TextOverflow.Ellipsis)
                BasicTextField(
                    value = text, onValueChange = { text = it },
                    textStyle = Type.body.copy(color = c.ink), cursorBrush = SolidColor(c.link), maxLines = 6,
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            }
            if (text.isBlank() && !busy) {
                if (transcribing) Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = c.link, strokeWidth = 1.6.dp, modifier = Modifier.size(18.dp))
                } else RoundIcon(Icons.Outlined.Mic, c.graphite, s.a11yMic, onClick = ::mic)
            }
            Spacer(Modifier.width(4.dp))
            val enabled = (text.isNotBlank() || files.isNotEmpty() || sendEmpty) && !busy
            Box(
                Modifier.size(44.dp).clip(CircleShape)
                    .background(if (enabled || busy) c.accent else c.accent.copy(alpha = 0.35f))
                    .clickable(enabled = enabled) { onSend(text, files.toList()); text = ""; files.clear() },
                contentAlignment = Alignment.Center,
            ) {
                if (busy) CircularProgressIndicator(color = Color.White, strokeWidth = 1.8.dp, modifier = Modifier.size(18.dp))
                else Icon(Icons.AutoMirrored.Outlined.Send, s.a11ySend, tint = Color.White, modifier = Modifier.size(19.dp))
            }
        }
    }
}

@Composable
private fun RoundIcon(icon: ImageVector, tint: Color, label: String? = null, onClick: () -> Unit) {
    Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClickLabel = label, onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, label, tint = tint)
    }
}

@Composable
private fun MenuItem(icon: ImageVector, label: String, onClick: () -> Unit) {
    DropdownMenuItem(
        leadingIcon = { Icon(icon, null, tint = M.c.graphite, modifier = Modifier.size(20.dp)) },
        text = { Text(label, style = Type.body.copy(color = M.c.ink)) },
        onClick = onClick,
    )
}

/** A calm moving level meter for the recording bar. */
@Composable
private fun LevelBars(level: Float, modifier: Modifier) {
    val c = M.c
    val history = remember { mutableStateListOf<Float>() }
    LaunchedEffect(level) { history += level; while (history.size > 40) history.removeAt(0) }
    Row(modifier.height(28.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        history.forEach { v ->
            Box(Modifier.width(3.dp).height((4 + v * 24).dp).clip(RoundedCornerShape(2.dp)).background(c.graphite.copy(alpha = 0.6f)))
        }
    }
}

// ---------------- attachments ----------------

@Composable
fun AttachmentChip(a: Attachment, onRemove: (() -> Unit)? = null) {
    val c = M.c
    Box {
        when (a.kind) {
            "photo", "video" -> Thumb(a, 64.dp)
            else -> Row(
                Modifier.height(64.dp).clip(RoundedCornerShape(14.dp)).background(c.chip).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(kindIcon(a.kind), null, tint = c.graphite, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(attachmentLabel(a), style = Type.small.copy(color = c.ink), maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(110.dp))
            }
        }
        if (onRemove != null) Box(
            Modifier.align(Alignment.TopEnd).padding(2.dp).size(20.dp).clip(CircleShape).background(c.ink.copy(alpha = 0.65f)).clickable(onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.Close, null, tint = c.bg, modifier = Modifier.size(12.dp)) }
    }
}

@Composable
fun Thumb(a: Attachment, size: Dp, modifier: Modifier = Modifier) {
    val c = M.c
    val ctx = LocalContext.current
    val bmp by produceState<Bitmap?>(null, a.id) { value = Files.thumb(ctx, a) }
    Box(modifier.size(size).clip(RoundedCornerShape(12.dp)).background(c.chip), contentAlignment = Alignment.Center) {
        bmp?.let { Image(it.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.size(size)) }
        if (a.kind == "video") Box(Modifier.size(24.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.PlayArrow, null, tint = Color.White, modifier = Modifier.size(16.dp))
        }
    }
}

/** Attachments inside an entry: photos as a row of thumbnails, the rest as quiet lines. Tap opens. */
@Composable
fun AttachmentsView(list: List<Attachment>) {
    if (list.isEmpty()) return
    val c = M.c
    val ctx = LocalContext.current
    fun open(a: Attachment) {
        if (a.kind == "contact") {
            a.contact?.phone?.let { ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$it"))) }
            return
        }
        Files.openIntent(ctx, a)?.let { runCatching { ctx.startActivity(it) } }
    }
    val media = list.filter { it.kind == "photo" || it.kind == "video" }
    val other = list - media.toSet()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (media.isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            media.forEach { Thumb(it, 76.dp, Modifier.clickable { open(it) }) }
        }
        other.forEach { a ->
            Row(
                Modifier.clip(RoundedCornerShape(10.dp)).background(c.chip).clickable { open(a) }.padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(kindIcon(a.kind), null, tint = c.graphite, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(attachmentLabel(a), style = Type.small.copy(color = c.ink, fontSize = 13.5.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    a.transcript?.takeIf { it.isNotBlank() }?.let { Text(it, style = Type.small.copy(color = c.graphite, fontSize = 12.5.sp), maxLines = 2, overflow = TextOverflow.Ellipsis) }
                }
            }
        }
    }
}

fun kindIcon(kind: String): ImageVector = when (kind) {
    "audio" -> Icons.Outlined.Mic
    "contact" -> Icons.Outlined.PersonOutline
    "photo" -> Icons.Outlined.PhotoLibrary
    "video" -> Icons.Outlined.PlayArrow
    else -> Icons.Outlined.AttachFile
}

fun attachmentLabel(a: Attachment): String = when (a.kind) {
    "audio" -> "${L.s.voiceNote} · ${a.durationLabel()}"
    "contact" -> listOfNotNull(a.contact?.name ?: a.name, a.contact?.phone).joinToString(" · ")
    "video" -> "${L.s.video} · ${a.durationLabel()}"
    else -> a.name
}

/** «2 фото · 1 файл» for compact rows. */
fun attachmentsSummary(list: List<Attachment>): String? {
    if (list.isEmpty()) return null
    val s = L.s
    val parts = listOfNotNull(
        list.count { it.kind == "photo" || it.kind == "video" }.takeIf { it > 0 }?.let { s.nPhotos(it) },
        list.count { it.kind == "file" }.takeIf { it > 0 }?.let { s.nFiles(it) },
        list.count { it.kind == "audio" }.takeIf { it > 0 }?.let { s.nVoice(it) },
        list.count { it.kind == "contact" }.takeIf { it > 0 }?.let { s.nContacts(it) },
    )
    return "📎 " + parts.joinToString(" · ")
}
