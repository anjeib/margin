package ru.anjeib.margin.ui

import ru.anjeib.margin.data.attachment

import androidx.compose.material.icons.outlined.TextFields

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.MediaPlayer
import android.net.Uri
import android.provider.ContactsContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Message
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import ru.anjeib.margin.data.fullText
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Download
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.anjeib.margin.data.Attachment
import ru.anjeib.margin.data.Entry
import ru.anjeib.margin.data.Files
import ru.anjeib.margin.data.WavRecorder
import ru.anjeib.margin.data.dayMonth
import ru.anjeib.margin.data.durationLabel
import java.io.File
import java.time.LocalDateTime

/**
 * What the composer under a stream is filling right now, set from a block:
 * «after» — a text block after [entryId]; «items» — items of the list [entryId];
 * «caption» — the caption of [attId] in [entryId]; «edit» — new words for [entryId].
 */
data class Fill(val kind: String, val entryId: String, val attId: String? = null, val prefill: String? = null)

/**
 * A note's stream or a task's records as blocks: text, list, photo, video, voice note, contact, file, link.
 * A tap makes a block active; its quiet «+» opens a row of things to put right after it.
 * Hold for: edit, caption, move up / down, delete.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BlockStream(
    owner: String,
    entries: List<Entry>,
    vm: MainVm,
    onFill: (Fill) -> Unit,
    trailing: (Entry) -> (@Composable () -> Unit)? = { null },
) {
    val c = M.c
    val s = M.s
    var active by remember(owner) { mutableStateOf<String?>(null) }
    var inserting by remember(owner) { mutableStateOf<String?>(null) }
    var menuFor by remember(owner) { mutableStateOf<String?>(null) }
    var viewing by remember(owner) { mutableStateOf<Attachment?>(null) }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        entries.forEachIndexed { i, e ->
            val on = active == e.id
            // One visit — one record: a hairline and the date only where a new visit starts.
            val newSession = i == 0 || e.session == null || e.session != entries[i - 1].session
            if (newSession && i > 0) Hairline(Modifier.padding(top = 6.dp, bottom = 2.dp))
            // Swipe a block left to delete it (with «Отменить»).
            SwipeActions(left = s.delete to { if (active == e.id) active = null; vm.removeEntry(owner, e.id) }, right = null, radius = 12) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(if (on) c.chip.copy(alpha = 0.45f) else c.bg)
                    .combinedClickable(onClick = { active = if (on) null else e.id; if (!on) inserting = null }, onLongClick = { active = e.id; menuFor = e.id })
                    .padding(horizontal = 6.dp, vertical = 4.dp),
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Box(Modifier.weight(1f)) { Block(owner, e, vm, onFill, on, newSession, onView = { viewing = it }) }
                    trailing(e)?.let { t -> Spacer(Modifier.width(4.dp)); Box(Modifier.padding(top = 6.dp)) { t() } }
                    // The quiet «+» of the active block: things to put right after it.
                    if (on) Box(Modifier.padding(top = 2.dp).size(36.dp).clip(CircleShape).background(if (inserting == e.id) c.link else c.linkSoft)
                        .clickable { inserting = if (inserting == e.id) null else e.id }, contentAlignment = Alignment.Center) {
                        Icon(if (inserting == e.id) Icons.Rounded.Close else Icons.Rounded.Add, s.insertBelow, tint = if (inserting == e.id) c.bg else c.link, modifier = Modifier.size(18.dp))
                    }
                }
                if (inserting == e.id) InsertRow(
                    vm,
                    onText = { inserting = null; onFill(Fill("after", e.id)) },
                    onList = { inserting = null; onFill(Fill("items", vm.insertList(owner, e.id))) },
                    onAttachment = { a -> inserting = null; vm.insertAttachment(owner, e.id, a) },
                )
                SoftMenu(menuFor == e.id, { menuFor = null }) {
                    if (e.text.isNotBlank() || (!e.isList && e.attachments.isEmpty()))
                        DropdownMenuItem(text = { Text(s.edit, style = Type.body) }, onClick = { menuFor = null; onFill(Fill("edit", e.id, prefill = e.text)) })
                    if (e.isList) DropdownMenuItem(text = { Text(s.listItem, style = Type.body) }, onClick = { menuFor = null; onFill(Fill("items", e.id)) })
                    e.attachments.firstOrNull { it.kind == "photo" || it.kind == "video" }?.let { a ->
                        DropdownMenuItem(text = { Text(s.captionWord, style = Type.body) }, onClick = { menuFor = null; onFill(Fill("caption", e.id, a.id, a.caption)) })
                    }
                    // «Сохранить / Переслать»: the file, or the words of the block.
                    val file = e.attachments.firstOrNull { it.file != null }
                    if (file != null) DropdownMenuItem(text = { Text(s.saveToPhone, style = Type.body) }, onClick = {
                        menuFor = null; scope.launch { vm.say(if (Files.saveToPhone(ctx, file)) s.savedToPhone else s.saveFailed) }
                    })
                    DropdownMenuItem(text = { Text(s.shareWord, style = Type.body) }, onClick = {
                        menuFor = null
                        val i0 = file?.let { Files.shareIntent(ctx, it) } ?: Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain")
                            .putExtra(Intent.EXTRA_TEXT, e.fullText), null)
                        runCatching { ctx.startActivity(i0) }
                    })
                    DropdownMenuItem(text = { Text(s.insertBelow, style = Type.body) }, onClick = { menuFor = null; active = e.id; inserting = e.id })
                    if (i > 0) DropdownMenuItem(text = { Text(s.moveUp, style = Type.body) }, onClick = { menuFor = null; vm.moveEntry(owner, e.id, -1) })
                    if (i < entries.lastIndex) DropdownMenuItem(text = { Text(s.moveDown, style = Type.body) }, onClick = { menuFor = null; vm.moveEntry(owner, e.id, 1) })
                    DropdownMenuItem(text = { Text(s.delete, style = Type.body.copy(color = c.accentText)) }, onClick = { menuFor = null; active = null; vm.removeEntry(owner, e.id) })
                }
            }
            }
        }
    }
    viewing?.let { a -> Viewer(a, vm, onClose = { viewing = null }) }
}

/** One block: its date, its words, its list, its attachments — each shown for what it is. */
@Composable
private fun Block(owner: String, e: Entry, vm: MainVm, onFill: (Fill) -> Unit, active: Boolean, showDate: Boolean, onView: (Attachment) -> Unit) {
    val c = M.c
    val s = M.s
    val at = runCatching { LocalDateTime.parse(e.at) }.getOrNull()
    Column {
        if (showDate) Text(at?.let { it.toLocalDate().dayMonth() + " · " + it.toLocalTime().toString().take(5) }?.uppercase() ?: "",
            style = Type.label.copy(color = c.faint, fontSize = 10.5.sp))
        if (e.text.isNotBlank()) {
            Spacer(Modifier.height(3.dp))
            ListAwareText(e.text, Type.body.copy(color = c.ink, fontSize = 15.5.sp))
        }
        if (e.isList) {
            Spacer(Modifier.height(4.dp))
            if (e.items.isEmpty()) Text("—", style = Type.body.copy(color = c.faint))
            e.items.forEach { it0 ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CheckCircle(it0.done, size = 18.dp) { vm.toggleItem(owner, e.id, it0.id) }
                    Text(it0.title, style = Type.body.copy(fontSize = 15.5.sp, color = if (it0.done) c.graphite else c.ink,
                        textDecoration = if (it0.done) TextDecoration.LineThrough else null), modifier = Modifier.padding(vertical = 3.dp))
                }
            }
        }
        e.attachments.forEach { a ->
            Spacer(Modifier.height(8.dp))
            // A quiet word over media says what it is, the thing itself is a thumbnail that opens on a tap.
            when (a.kind) { "photo" -> s.photoWord; "video" -> s.video; "file" -> s.attachFile; else -> null }?.let {
                Text(it.uppercase(), style = Type.label.copy(color = c.faint, fontSize = 10.sp), modifier = Modifier.padding(bottom = 4.dp))
            }
            when (a.kind) {
                "photo" -> PhotoBlock(a, vm, active, onView = { onView(a) }, onCaption = { onFill(Fill("caption", e.id, a.id, a.caption)) }, onText = { act -> vm.photoText(owner, e.id, a, act) })
                "video" -> VideoBlock(a, onView = { onView(a) }, onCaption = { onFill(Fill("caption", e.id, a.id, a.caption)) })
                "audio" -> VoiceBlock(a, vm)
                "contact" -> ContactBlock(a)
                "link" -> LinkBlock(a)
                else -> FileBlock(a, onView = { onView(a) })
            }
        }
    }
}

private fun open(ctx: android.content.Context, a: Attachment) { Files.openIntent(ctx, a)?.let { runCatching { ctx.startActivity(it) } } }

/** A document as a small card: what it is, how big; a tap opens the quick look. */
@Composable
private fun FileBlock(a: Attachment, onView: () -> Unit) {
    val c = M.c
    val ext = a.name.substringAfterLast('.', "").uppercase().take(4)
    Row(Modifier.clip(RoundedCornerShape(14.dp)).background(c.chip).clickable(onClick = onView).padding(end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(width = 52.dp, height = 64.dp).clip(RoundedCornerShape(topStart = 14.dp, bottomStart = 14.dp)).background(c.linkSoft), contentAlignment = Alignment.Center) {
            Text(ext.ifBlank { "•" }, style = Type.label.copy(color = c.link, fontSize = 11.sp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.widthIn(max = 220.dp)) {
            Text(a.name.ifBlank { M.s.attachFile }, style = Type.body.copy(color = c.ink, fontSize = 14.5.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (a.size > 0) Text(android.text.format.Formatter.formatShortFileSize(LocalContext.current, a.size), style = Type.small.copy(color = c.graphite, fontSize = 12.sp))
        }
    }
}

/**
 * The quick look over the screen: a photo (pinch to zoom), a video that plays here, a text file or a PDF read here.
 * Anything else opens in the app that knows it. Save and share from the top bar.
 */
@Composable
fun Viewer(a: Attachment, vm: MainVm, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val s = M.s
    val scope = rememberCoroutineScope()
    if (a.kind == "file" && !Files.isText(a) && !Files.isPdf(a)) { LaunchedEffect(a.id) { open(ctx, a); onClose() }; return }
    androidx.compose.ui.window.Dialog(onDismissRequest = onClose, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(M.c.overlay)) {
            when {
                a.kind == "photo" -> {
                    val bmp by produceState<Bitmap?>(null, a.id) { value = Files.thumb(ctx, a, 2400) }
                    var zoom by remember { mutableFloatStateOf(1f) }
                    var pan by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
                    // The recognised text, when there is some, sits under the photo as a field one can correct.
                    val live = vm.data.collectAsState().value.attachment(a.id) ?: a
                    Column(Modifier.fillMaxSize()) {
                        Box(Modifier.weight(1f).fillMaxWidth()) {
                            bmp?.let {
                                Image(it.asImageBitmap(), null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize()
                                    .pointerInput(Unit) { detectTransformGestures { _, p, z, _ -> zoom = (zoom * z).coerceIn(1f, 5f); pan = if (zoom == 1f) androidx.compose.ui.geometry.Offset.Zero else pan + p } }
                                    .graphicsLayer(scaleX = zoom, scaleY = zoom, translationX = pan.x, translationY = pan.y))
                            }
                        }
                        live.ocr?.let { ocr ->
                            var text by remember(a.id, ocr) { mutableStateOf(ocr) }
                            LaunchedEffect(text) { if (text != ocr) { kotlinx.coroutines.delay(800); vm.setOcr(a.id, text) } }
                            Box(Modifier.fillMaxWidth().heightIn(max = 260.dp).background(M.c.card).padding(16.dp).verticalScroll(rememberScrollState())) {
                                androidx.compose.foundation.text.BasicTextField(text, { text = it }, textStyle = Type.body.copy(color = M.c.ink, fontSize = 15.sp),
                                    cursorBrush = androidx.compose.ui.graphics.SolidColor(M.c.link), modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                }
                a.kind == "video" -> {
                    val f = Files.file(ctx, a)
                    if (f != null) androidx.compose.ui.viewinterop.AndroidView(factory = { c0 ->
                        android.widget.VideoView(c0).apply {
                            setVideoPath(f.absolutePath)
                            setMediaController(android.widget.MediaController(c0).also { it.setAnchorView(this) })
                            setOnPreparedListener { start() }
                        }
                    }, modifier = Modifier.fillMaxSize().padding(vertical = 56.dp))
                }
                Files.isPdf(a) -> {
                    val pages by produceState<List<Bitmap>?>(null, a.id) { value = Files.pdfPages(ctx, a) }
                    Column(Modifier.fillMaxSize().padding(top = 56.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        pages?.forEach { Image(it.asImageBitmap(), null, contentScale = ContentScale.FillWidth, modifier = Modifier.fillMaxWidth()) }
                    }
                }
                else -> {
                    val text by produceState<String?>(null, a.id) { value = Files.text(ctx, a) }
                    Text(text.orEmpty(), style = Type.small.copy(color = M.c.onAccent, fontSize = 14.sp), modifier = Modifier.fillMaxSize().padding(top = 64.dp, start = 16.dp, end = 16.dp).verticalScroll(rememberScrollState()))
                }
            }
            Row(Modifier.fillMaxWidth().background(M.c.overlay.copy(alpha = 0.5f)).padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                ViewerAction(Icons.Rounded.Close, s.closeWord2, onClose)
                Spacer(Modifier.weight(1f))
                // A photo's words, once: then they stay under it.
                if (a.kind == "photo") {
                    val working by vm.working.collectAsState()
                    val has = vm.data.collectAsState().value.attachment(a.id)?.ocr != null
                    if ("ocr:${a.id}" in working) Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                        androidx.compose.material3.CircularProgressIndicator(color = M.c.onAccent, strokeWidth = 1.6.dp, modifier = Modifier.size(18.dp))
                    } else if (!has) ViewerAction(Icons.Outlined.TextFields, s.readTextWord) { vm.recognize(a) }
                }
                if (a.file != null) ViewerAction(Icons.Outlined.Download, s.saveToPhone) { scope.launch { vm.say(if (Files.saveToPhone(ctx, a)) s.savedToPhone else s.saveFailed) } }
                ViewerAction(Icons.Outlined.Share, s.shareWord) { Files.shareIntent(ctx, a)?.let { runCatching { ctx.startActivity(it) } } }
            }
        }
    }
}

@Composable
private fun ViewerAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, label, tint = M.c.onAccent, modifier = Modifier.size(22.dp))
    }
}

/** A photo wide, its caption, and — when there is text on it — that text, folded to three lines. */
@Composable
private fun PhotoBlock(a: Attachment, vm: MainVm, active: Boolean, onView: () -> Unit, onCaption: () -> Unit, onText: (String) -> Unit) {
    val c = M.c
    val s = M.s
    val ctx = LocalContext.current
    val working by vm.working.collectAsState()
    val bmp by produceState<Bitmap?>(null, a.id) { value = Files.thumb(ctx, a, 600) }
    Column {
        Box(Modifier.size(width = 200.dp, height = 150.dp).clip(RoundedCornerShape(14.dp)).background(c.chip).clickable(onClick = onView)) {
            bmp?.let { Image(it.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.size(width = 200.dp, height = 150.dp)) }
        }
        Caption(a.caption, onCaption)
        // The text on a photo only on request: what to do with it decides whether it is worth reading.
        var menu by remember { mutableStateOf(false) }
        if ("ocr:${a.id}" in working) Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(color = c.link, strokeWidth = 1.5.dp, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(8.dp))
            Text(s.readingPhoto, style = Type.small.copy(color = c.faint, fontSize = 12.5.sp))
        } else if (active) Box(Modifier.padding(top = 6.dp)) {
            Row(Modifier.clip(RoundedCornerShape(50)).background(c.linkSoft).clickable { menu = true }.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.DocumentScanner, null, tint = c.link, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp))
                Text(s.photoTextAction, style = Type.small.copy(color = c.link, fontSize = 13.5.sp))
            }
            SoftMenu(menu, { menu = false }) {
                DropdownMenuItem(text = { Text(s.insertAsText, style = Type.body) }, onClick = { menu = false; onText("insert") })
                DropdownMenuItem(text = { Text(s.makeContact, style = Type.body) }, onClick = { menu = false; onText("contact") })
                DropdownMenuItem(text = { Text(s.makeTasks, style = Type.body) }, onClick = { menu = false; onText("tasks") })
            }
        }
    }
}

@Composable
private fun VideoBlock(a: Attachment, onView: () -> Unit, onCaption: () -> Unit) {
    val c = M.c
    val ctx = LocalContext.current
    val bmp by produceState<Bitmap?>(null, a.id) { value = Files.thumb(ctx, a, 600) }
    Column {
        Box(Modifier.size(width = 200.dp, height = 150.dp).clip(RoundedCornerShape(14.dp)).background(c.chip).clickable(onClick = onView), contentAlignment = Alignment.Center) {
            bmp?.let { Image(it.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.size(width = 200.dp, height = 150.dp)) }
            Box(Modifier.size(48.dp).clip(CircleShape).background(M.c.overlay.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.PlayArrow, null, tint = M.c.onAccent, modifier = Modifier.size(28.dp))
            }
            Text(a.durationLabel(), style = Type.small.copy(color = M.c.onAccent, fontSize = 12.5.sp),
                modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp).clip(RoundedCornerShape(6.dp)).background(M.c.overlay.copy(alpha = 0.45f)).padding(horizontal = 6.dp, vertical = 2.dp))
        }
        Caption(a.caption, onCaption)
    }
}

@Composable
private fun Caption(text: String?, onEdit: () -> Unit) {
    val c = M.c
    val s = M.s
    if (text != null) Text(text, style = Type.body.copy(color = c.ink, fontSize = 15.sp), modifier = Modifier.padding(top = 6.dp).clickable(onClick = onEdit))
    else Text("+ " + s.captionWord.lowercase(), style = Type.small.copy(color = c.faint, fontSize = 13.sp), modifier = Modifier.padding(top = 4.dp).clickable(onClick = onEdit))
}

/** A voice note that plays in place, with what was said written under it. */
@Composable
private fun VoiceBlock(a: Attachment, vm: MainVm) {
    val c = M.c
    val s = M.s
    val ctx = LocalContext.current
    val working by vm.working.collectAsState()
    var player by remember(a.id) { mutableStateOf<MediaPlayer?>(null) }
    var playing by remember(a.id) { mutableStateOf(false) }
    var progress by remember(a.id) { mutableFloatStateOf(0f) }
    DisposableEffect(a.id) { onDispose { player?.release(); player = null } }
    LaunchedEffect(playing) {
        while (playing) {
            player?.let { p -> progress = if (p.duration > 0) p.currentPosition / p.duration.toFloat() else 0f }
            delay(200)
        }
    }
    fun toggle() {
        val p = player ?: run {
            val f = Files.file(ctx, a) ?: return
            runCatching { MediaPlayer().apply { setDataSource(f.absolutePath); prepare(); setOnCompletionListener { playing = false; progress = 0f } } }.getOrNull()
                ?.also { player = it } ?: return
        }
        if (p.isPlaying) { p.pause(); playing = false } else { p.start(); playing = true }
    }
    Column {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.chip).padding(start = 6.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(c.link).clickable { toggle() }, contentAlignment = Alignment.Center) {
                Icon(if (playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, s.voiceNote, tint = c.bg, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            LinearProgressIndicator(progress = { progress }, color = c.link, trackColor = c.hairline, modifier = Modifier.weight(1f).height(3.dp), drawStopIndicator = {}, gapSize = 0.dp)
            Spacer(Modifier.width(12.dp))
            Text(a.durationLabel(), style = Type.small.copy(color = c.graphite, fontSize = 13.sp))
        }
        when {
            !a.transcript.isNullOrBlank() -> Text(a.transcript, style = Type.body.copy(color = c.ink, fontSize = 15.sp), modifier = Modifier.padding(top = 6.dp, start = 4.dp))
            "voice" in working -> Text(s.transcribing, style = Type.small.copy(color = c.faint, fontSize = 12.5.sp), modifier = Modifier.padding(top = 4.dp, start = 4.dp))
        }
    }
}

/** A contact as a small card: name, phone, and the two things one does with it. */
@Composable
private fun ContactBlock(a: Attachment) {
    val c = M.c
    val ctx = LocalContext.current
    val ci = a.contact
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.chip).padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Monogram(ci?.name ?: a.name, 36.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(ci?.name ?: a.name, style = Type.body.copy(color = c.ink, fontSize = 15.5.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            listOfNotNull(ci?.phone, ci?.email).joinToString(" · ").takeIf { it.isNotBlank() }?.let {
                Text(it, style = Type.small.copy(color = c.graphite, fontSize = 13.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        ci?.phone?.let { ph ->
            RoundAction(Icons.Outlined.Call) { runCatching { ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$ph"))) } }
            RoundAction(Icons.AutoMirrored.Outlined.Message) { runCatching { ctx.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + ph.filter { it.isDigit() || it == '+' }))) } }
        }
    }
}

@Composable
private fun RoundAction(icon: ImageVector, onClick: () -> Unit) {
    Box(Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = M.c.graphite, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun LinkBlock(a: Attachment) {
    val c = M.c
    val ctx = LocalContext.current
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.chip.copy(alpha = 0.6f))
        .clickable { a.url?.let { runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it))) } } }.padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.Link, null, tint = c.graphite, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(a.name, style = Type.body.copy(color = c.ink, fontSize = 14.5.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
            a.url?.let { Uri.parse(it).host }?.let { Text(it.removePrefix("www."), style = Type.small.copy(color = c.faint, fontSize = 12.sp)) }
        }
    }
}

/**
 * What can go after a block: text, a list, a photo (gallery or camera), a video, a voice note recorded right here,
 * a file, a contact. Each lands right after the block it was opened from.
 */
@Composable
private fun InsertRow(vm: MainVm, onText: () -> Unit, onList: () -> Unit, onAttachment: (Attachment) -> Unit) {
    val c = M.c
    val s = M.s
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var cameraFile by remember { mutableStateOf<File?>(null) }
    var recorder by remember { mutableStateOf<WavRecorder?>(null) }
    var level by remember { mutableFloatStateOf(0f) }
    var seconds by remember { mutableStateOf(0) }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { u -> if (u != null) scope.launch { Files.import(ctx, u)?.let(onAttachment) } }
    val pickVideo = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { u -> if (u != null) scope.launch { Files.import(ctx, u, "video")?.let(onAttachment) } }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val f = cameraFile
        if (ok && f != null && f.length() > 0) onAttachment(Files.fromCamera(f)) else f?.delete()
    }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u: Uri? -> if (u != null) scope.launch { Files.import(ctx, u)?.let(onAttachment) } }
    val pickContact = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        r.data?.data?.let { u -> Files.readContact(ctx, u)?.let { onAttachment(vm.contactAttachment(it)) } }
    }
    fun record() {
        val r = WavRecorder(ctx)
        if (!r.start()) { Toast.makeText(ctx, s.voiceUnavailable, Toast.LENGTH_SHORT).show(); return }
        recorder = r; seconds = 0
        scope.launch { while (recorder === r) { delay(100); level = r.level } }
        scope.launch { while (recorder === r) { delay(1000); seconds++ } }
    }
    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) record() else Toast.makeText(ctx, s.micPermission, Toast.LENGTH_SHORT).show()
    }

    val rec = recorder
    if (rec != null) {
        // Recording in place: a breathing dot, the time, done or cancel.
        Row(Modifier.padding(top = 10.dp).fillMaxWidth().clip(RoundedCornerShape(50)).background(c.linkSoft).padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(CircleShape).clickable { recorder = null; rec.cancel() }, contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Close, s.cancel, tint = c.graphite, modifier = Modifier.size(20.dp))
            }
            Box(Modifier.size(10.dp).clip(CircleShape).background(c.accent.copy(alpha = 0.4f + level * 0.6f)))
            Spacer(Modifier.width(10.dp))
            Text("%d:%02d".format(seconds / 60, seconds % 60), style = Type.body.copy(color = c.ink, fontSize = 15.sp), modifier = Modifier.weight(1f))
            Box(Modifier.size(40.dp).clip(CircleShape).background(c.accent).clickable { recorder = null; rec.stop()?.let(onAttachment) }, contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Check, s.save, tint = c.bg, modifier = Modifier.size(20.dp))
            }
        }
        return
    }
    Row(Modifier.padding(top = 10.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        InsertIcon(Icons.AutoMirrored.Outlined.Notes, s.textWord, onText)
        InsertIcon(Icons.Outlined.Checklist, s.listBlock, onList)
        InsertIcon(Icons.Outlined.PhotoLibrary, s.photoWord) { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
        InsertIcon(Icons.Outlined.PhotoCamera, s.attachCamera) { val (f, uri) = Files.newPhotoFile(ctx); cameraFile = f; runCatching { takePhoto.launch(uri) } }
        InsertIcon(Icons.Outlined.Videocam, s.video) { pickVideo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) }
        InsertIcon(Icons.Outlined.Mic, s.voiceNote) {
            if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) record() else askMic.launch(Manifest.permission.RECORD_AUDIO)
        }
        InsertIcon(Icons.Outlined.AttachFile, s.attachFile) { pickFile.launch(arrayOf("*/*")) }
        InsertIcon(Icons.Outlined.PersonOutline, s.attachContact) { runCatching { pickContact.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)) } }
    }
}

@Composable
private fun InsertIcon(icon: ImageVector, label: String, onClick: () -> Unit) {
    val c = M.c
    Column(Modifier.width(62.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(c.card), contentAlignment = Alignment.Center) {
            Icon(icon, label, tint = c.ink, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(label, style = Type.small.copy(color = c.graphite, fontSize = 11.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** The composer's label for what a [Fill] is filling. */
fun Fill.label(): String = ru.anjeib.margin.i18n.L.s.let { s -> when (kind) { "after" -> s.textAfter; "items" -> s.listItem; "caption" -> s.captionWord; "edit" -> s.edit; else -> "" } }

/** Sends what was typed into the block the [Fill] points at. True when it was handled. */
fun MainVm.fillBlock(owner: String, f: Fill, text: String): Boolean {
    when (f.kind) {
        "after" -> insertText(owner, f.entryId, text)
        "items" -> addListItems(owner, f.entryId, text)
        "caption" -> f.attId?.let { setCaption(owner, f.entryId, it, text) }
        "edit" -> editEntryText(owner, f.entryId, text)
        else -> return false
    }
    return true
}
