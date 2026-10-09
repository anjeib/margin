package ru.anjeib.margin.paper

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.anjeib.margin.book.Ask
import ru.anjeib.margin.book.Book
import ru.anjeib.margin.data.Attachment
import ru.anjeib.margin.data.Files
import ru.anjeib.margin.data.WavRecorder



import java.io.File
import java.time.LocalDate

/*
 * The one field (DESIGN.md §6). It has a margin too: while you write, Margin already pencils there what it understood
 * («завтра 15:00 · с Олегом») — the live preview stands exactly where the notes will stand. Inside the field: «+» for a
 * photo, the camera, a file or a contact; the microphone; «записать». A question gets its answer over the field.
 */
@Composable
fun Composer(vm: BookVm, target: BookVm.Target, book: Book, today: LocalDate, modifier: Modifier = Modifier,
             onOpen: (String) -> Unit = {}, onPerson: (String) -> Unit = {}, onThread: (String) -> Unit = {}, onDay: (LocalDate) -> Unit = {},
             onSearch: () -> Unit = {}) {
    val page = target.page
    val c = P.c
    val w = P.w
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var text by rememberSaveable { mutableStateOf("") }
    val files = remember { mutableStateListOf<Attachment>() }
    val said by vm.said.collectAsState()
    val asked by vm.asked.collectAsState()
    val handed by vm.prefill.collectAsState()
    LaunchedEffect(handed) { handed?.let { text = it; vm.prefillTaken() } }
    val handedFiles by vm.handedFiles.collectAsState()
    LaunchedEffect(handedFiles) { if (handedFiles.isNotEmpty()) { files += handedFiles; vm.handedFilesTaken() } }
    val draft = remember(text, target, book) { if (text.isBlank()) null else vm.preview(text, target) }
    val first = draft?.lines?.firstOrNull()
    val pencil = when {
        draft == null -> emptyList()
        draft.question -> listOf(Note("?", Tone.Link))
        draft.lines.size > 1 -> listOf(Note(w.linesN(draft.lines.size), Tone.Quiet))
        first != null -> notesOf(first, page, today, book, w) + listOfNotNull(draft.threadTitle?.let { Note(w.threadPrefix + it, Tone.Link) })
        else -> emptyList()
    }.take(3)

    fun send() {
        if (text.isBlank() && files.isEmpty()) return
        if (vm.write(text, target, files.toList())) { text = ""; files.clear() }
    }

    // ---- voice: a recording read into words; the system's recogniser when nothing here can read it ----
    var recorder by remember { mutableStateOf<WavRecorder?>(null) }
    var level by remember { mutableFloatStateOf(0f) }
    var hearing by remember { mutableStateOf(false) }
    val sendNow = remember { ru.anjeib.margin.data.Prefs.get(ctx).settings.value.voiceSendNow }
    fun gotWords(t: String?) {
        hearing = false
        if (t.isNullOrBlank()) return
        text = (text.trimEnd() + " " + t).trim()
        if (sendNow) send()
    }
    val systemVoice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        gotWords(r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull())
    }
    fun startRecording() {
        val r = WavRecorder(ctx)
        if (!r.start()) { vm.tell(w.voiceOff); return }
        recorder = r
        scope.launch { while (recorder === r) { delay(80); level = r.level } }
    }
    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok) startRecording() else vm.tell(w.micDenied) }
    fun stopRecording(andSend: Boolean = true) {
        val r = recorder ?: return
        recorder = null
        val audio = r.stop() ?: return
        if (!andSend) { Files.delete(ctx, audio); return }
        hearing = true
        vm.transcribe(audio) { gotWords(it) }
    }
    fun mic() {
        if (recorder != null) { stopRecording(); return }
        if (!vm.canHear()) {
            val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (w.ru) "ru-RU" else "en-US")
            runCatching { systemVoice.launch(i) }.onFailure { vm.tell(w.voiceOff) }
            return
        }
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startRecording()
        else askMic.launch(Manifest.permission.RECORD_AUDIO)
    }
    val dictate by vm.dictate.collectAsState()
    LaunchedEffect(dictate) { if (dictate != null) { delay(300); mic(); vm.dictateTaken() } }
    // «Записать» from the tile: asked twice — the window gets its focus only after the shade closes.
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val focusAsk by vm.focus.collectAsState()
    LaunchedEffect(focusAsk) {
        if (focusAsk == null) return@LaunchedEffect
        repeat(2) { delay(if (it == 0) 150L else 450L); runCatching { focus.requestFocus() }; keyboard?.show() }
        vm.focusTaken()
    }

    // ---- «+»: a photo, the camera, a file, a contact ----
    var menu by remember { mutableStateOf(false) }
    var cameraFile by remember { mutableStateOf<File?>(null) }
    val pickMedia = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(6)) { uris ->
        uris.forEach { u -> scope.launch { Files.import(ctx, u)?.let { files += it } } }
    }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val f = cameraFile
        if (ok && f != null && f.length() > 0) files += Files.fromCamera(f) else f?.delete()
    }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        if (u != null) scope.launch { Files.import(ctx, u)?.let { files += it } }
    }
    val pickContact = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        r.data?.data?.let { u -> Files.readContact(ctx, u)?.let { info -> val name = vm.addContact(info); text = (text.trimEnd() + " " + name + " ").trimStart() } }
    }

    Column(modifier.fillMaxWidth().background(c.paper).rule(c.rule).animateContentSize()) {
        asked?.let { a ->
            AnswerPanel(a, book, today, onClose = vm::closeAnswer, onMark = vm::toggle, onOpen = onOpen,
                onPerson = { vm.closeAnswer(); onPerson(it) }, onThread = { vm.closeAnswer(); onThread(it) },
                onDay = { vm.closeAnswer(); onDay(it) }, onSearch = { vm.closeAnswer(); onSearch() })
        }
        // Margin's sentence over the field: where the words went, with a way back.
        AnimatedVisibility(said != null && asked == null, enter = fadeIn(), exit = fadeOut()) {
            var last by remember { mutableStateOf<BookVm.Said?>(null) }
            if (said != null) last = said
            last?.let { s ->
                Row(Modifier.fillMaxWidth().padding(end = Grid.right, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.width(Grid.text))
                    Text(s.text, style = Voice.reply.copy(color = c.graphite), maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (s.undo != null) Text("  ·  " + w.undo, style = Voice.reply.copy(color = c.link),
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { vm.undo() }.padding(vertical = 12.dp, horizontal = 4.dp))
                }
            }
        }
        // What goes with the words: thumbnails, a tap on × takes one back.
        if (files.isNotEmpty()) Row(Modifier.fillMaxWidth().padding(start = Grid.text - 6.dp, end = 12.dp, bottom = 6.dp).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            files.forEach { a -> FileChip(a, 56.dp, onRemove = { files.remove(a); Files.delete(ctx, a) }) }
        }
        Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.Bottom) {
            // The field's own margin: what Margin has understood so far — or that it is listening.
            Column(Modifier.width(Grid.margin - Grid.noteGap).padding(start = 4.dp, bottom = 13.dp), horizontalAlignment = Alignment.End) {
                when {
                    recorder != null -> NoteText(Note(w.listening, Tone.Attention))
                    hearing -> NoteText(Note(w.transcribing, Tone.Quiet))
                    else -> pencil.forEach { NoteText(it) }
                }
            }
            Spacer(Modifier.width(Grid.noteGap + 6.dp))
            val shape = RoundedCornerShape(22.dp)
            Row(Modifier.weight(1f).padding(end = 12.dp).heightIn(min = 44.dp).clip(shape).background(c.sheet).border(1.dp, c.hairline, shape)
                .padding(start = 2.dp, end = 3.dp), verticalAlignment = Alignment.Bottom) {
                Box {
                    FieldButton(w.a11yAttach, { menu = true }) { PlusGlyph(c.graphite, 18.dp) }
                    DropdownMenu(menu, { menu = false }, containerColor = c.sheet) {
                        listOf(w.attachPhoto, w.attachCamera, w.attachFile, w.attachContact).forEachIndexed { i, label ->
                            DropdownMenuItem(text = { Text(label, style = Voice.words.copy(color = c.ink)) }, onClick = {
                                menu = false
                                when (i) {
                                    0 -> pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                                    1 -> runCatching { val (f, uri) = Files.newPhotoFile(ctx); cameraFile = f; takePhoto.launch(uri) }
                                    2 -> pickFile.launch(arrayOf("*/*"))
                                    else -> runCatching { pickContact.launch(Intent(Intent.ACTION_PICK, android.provider.ContactsContract.Contacts.CONTENT_URI)) }
                                }
                            })
                        }
                    }
                }
                Box(Modifier.weight(1f).padding(vertical = 10.dp)) {
                    if (recorder != null) Level(level, Modifier.fillMaxWidth().height(24.dp))
                    else {
                        if (text.isEmpty()) Text(w.composerHint, style = Voice.words.copy(color = c.graphite), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        BasicTextField(text, { text = it }, textStyle = Voice.words.copy(color = c.ink), cursorBrush = SolidColor(c.link), maxLines = 6,
                            modifier = Modifier.fillMaxWidth().focusRequester(focus))
                    }
                }
                // The microphone gives its place to the words once there are any: dictation starts a thought, it doesn't finish one.
                if (text.isEmpty() || recorder != null) FieldButton(if (recorder != null) w.stop else w.a11yMic, ::mic) {
                    if (recorder != null) Box(Modifier.size(14.dp).clip(RoundedCornerShape(3.dp)).background(c.redFill))
                    else MicGlyph(c.graphite, 20.dp)
                }
                val can = text.isNotBlank() || files.isNotEmpty() || recorder != null
                Box(Modifier.size(42.dp).clip(CircleShape).clickable(enabled = can) { if (recorder != null) stopRecording() else send() }
                    .semantics { contentDescription = w.a11ySend }, contentAlignment = Alignment.Center) {
                    Box(Modifier.size(32.dp).clip(CircleShape).background(if (can) c.redFill else c.redFill.copy(alpha = 0.35f)), contentAlignment = Alignment.Center) {
                        SendGlyph(Color.White, 15.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun FieldButton(label: String, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(Modifier.size(40.dp, 44.dp).clip(CircleShape).clickable(onClick = onClick).semantics { contentDescription = label },
        contentAlignment = Alignment.Center) { content() }
}

/** The voice as a pencil line: strokes that rise with how loud you speak. */
@Composable
private fun Level(level: Float, modifier: Modifier) {
    val c = P.c
    val history = remember { ArrayDeque<Float>() }
    history.addLast(level); while (history.size > 40) history.removeFirst()
    Canvas(modifier) {
        val gap = 6.dp.toPx()
        val n = (size.width / gap).toInt().coerceAtLeast(1)
        val shown = history.toList().takeLast(n)
        shown.forEachIndexed { i, v ->
            val h = (size.height * (0.12f + 0.88f * kotlin.math.sqrt(v.coerceIn(0f, 1f))))
            val x = size.width - (shown.size - i) * gap + gap / 2
            drawLine(c.graphite, Offset(x, (size.height - h) / 2), Offset(x, (size.height + h) / 2), 2.dp.toPx(), StrokeCap.Round)
        }
    }
}

/** A file going with the words, or kept on a line: a photo as itself, anything else as its name. */
@Composable
fun FileChip(a: Attachment, size: Dp, onRemove: (() -> Unit)? = null, onClick: (() -> Unit)? = null) {
    val c = P.c
    val w = P.w
    val ctx = LocalContext.current
    val visual = a.kind == "photo" || a.kind == "video"
    Box(Modifier.then(if (onClick != null) Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick) else Modifier)) {
        if (visual) {
            val bmp by produceState<android.graphics.Bitmap?>(null, a.id) { value = Files.thumb(ctx, a) }
            Box(Modifier.size(size).clip(RoundedCornerShape(10.dp)).background(c.hairline)) {
                bmp?.let { Image(it.asImageBitmap(), a.name, contentScale = ContentScale.Crop, modifier = Modifier.size(size)) }
            }
        } else {
            Text(a.name.ifBlank { w.file }, style = Voice.small.copy(color = c.ink), maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.heightIn(min = size).width(size * 2.2f).clip(RoundedCornerShape(10.dp)).background(c.sheet)
                    .border(1.dp, c.hairline, RoundedCornerShape(10.dp)).padding(10.dp))
        }
        if (onRemove != null) Box(Modifier.align(Alignment.TopEnd).padding(2.dp).size(22.dp).clip(CircleShape).background(c.ink.copy(alpha = 0.6f))
            .clickable(onClick = onRemove).semantics { contentDescription = w.close }, contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Close, null, tint = c.paper, modifier = Modifier.size(13.dp))
        }
    }
}

/**
 * The answer over the field: the question as asked (small), Margin's sentence (its name, thread or day a link), and the
 * lines it is about — the same rows as on a page: the mark is done, the words open the line.
 */
@Composable
private fun AnswerPanel(a: BookVm.Asked, book: Book, today: LocalDate, onClose: () -> Unit, onMark: (String) -> Unit, onOpen: (String) -> Unit,
                        onPerson: (String) -> Unit, onThread: (String) -> Unit, onDay: (LocalDate) -> Unit, onSearch: () -> Unit) {
    val c = P.c
    val w = P.w
    val ans = a.answer
    val go: (() -> Unit)? = ans?.let { x ->
        when (x.about) {
            Ask.About.Promised, Ask.About.Waited, Ask.About.Person -> x.name?.let { n -> { onPerson(n) } }
            Ask.About.Thread -> x.threadId?.let { t -> { onThread(t) } }
            Ask.About.Day -> x.from?.let { d -> { onDay(d) } }
            else -> null
        }
    }
    Column(Modifier.fillMaxWidth().heightIn(max = 360.dp).verticalScroll(rememberScrollState()).padding(bottom = 6.dp)) {
        // A hairline from the rule: what follows is Margin's answer, not the page.
        Box(Modifier.padding(start = Grid.margin).fillMaxWidth().height(1.dp).background(c.hairline))
        Row(Modifier.fillMaxWidth().padding(top = 4.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(Grid.margin - Grid.noteGap).padding(start = 4.dp), contentAlignment = Alignment.CenterEnd) { NoteText(Note("?", Tone.Link)) }
            Spacer(Modifier.width(Grid.text - Grid.margin + Grid.noteGap))
            Text(a.q, style = Voice.small.copy(color = c.graphite), maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onClose).semantics { contentDescription = w.close }, contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Close, null, tint = c.graphite, modifier = Modifier.size(18.dp))
            }
        }
        Row(Modifier.fillMaxWidth().padding(end = Grid.right, bottom = 4.dp)) {
            Spacer(Modifier.width(Grid.text))
            if (a.thinking || ans == null) Text(w.thinking, style = Voice.reply.copy(color = c.graphite))
            else {
                val (pre, link, post) = w.answer(ans, today)
                Text(buildAnnotatedString {
                    append(pre)
                    if (link != null) {
                        if (go != null) withLink(LinkAnnotation.Clickable("go", TextLinkStyles(SpanStyle(color = c.link))) { go() }) { append(link) }
                        else append(link)
                    }
                    append(post)
                }, style = Voice.reply.copy(color = c.ink))
            }
        }
        val shown = ans?.lines.orEmpty().map { l -> book.line(l.id) ?: l }
        // An answer about one day shows its lines as on that page: the day is in the sentence, not on every margin.
        val pageOf = ans?.takeIf { it.about == Ask.About.Day && it.from == it.to }?.from ?: today
        shown.take(5).forEach { l -> LineOnPage(l, pageOf, today, book, Seen.Page, false, onMark, onOpen) }
        if (shown.size > 5) MarginLink(w.more(shown.size - 5)) { (go ?: onSearch)() }
    }
}
