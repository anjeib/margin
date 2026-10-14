package ru.anjeib.margin.ui

import androidx.compose.ui.draw.shadow

import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.SideEffect
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
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
    /** Changes when dictation should start by itself («Надиктовать» on the app icon). */
    micKey: Any? = null,
    /** The send button works with nothing typed (forwarding: the words are already there). */
    sendEmpty: Boolean = false,
    /** «/» offers what it is (дело / тема / идея) and where it belongs (работа / личное) — the main field only. */
    slash: Boolean = false,
    /** What the phone understood of the words so far — one quiet line over the field. */
    preview: ((String) -> String?)? = null,
    /** «Поля»: the field right of the rule. [edge] — how far the caller already moved it from the screen's edge. */
    ruled: Boolean = true,
    edge: Dp = 14.dp,
    /**
     * Owner, 12 окт: in a topic or a matter one writes on the page itself. Then the words live in [pageText] — the page's
     * writing line edits them — and down here stay only «+», the microphone and «send»: dictation lands on the page, is read
     * there, and goes when sent. [onWrite] — a tap on the empty bar: take me to the line.
     */
    pageText: androidx.compose.runtime.MutableState<String>? = null,
    onWrite: (() -> Unit)? = null,
    /** On a page, the photos and files picked with the page's own «+» (they show there, under the words). */
    pageFiles: androidx.compose.runtime.snapshots.SnapshotStateList<Attachment>? = null,
    /** On a page there is no «send»: the page asks for what is written to be kept (keyboard hidden, page left) through this. */
    pageSend: PageSend? = null,
    /**
     * Owner, 14 окт: in a matter's card the marks are chosen with the page's «+» (MainVm.markPicker), not typed — only «@»
     * still works in the words; files come with the paperclip down here.
     */
    marksOnPlus: Boolean = false,
) {
    val c = M.c
    val s = M.s
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by vm.settings.collectAsState()
    val working by vm.working.collectAsState()
    val ownText = rememberSaveable { mutableStateOf("") }
    var text by (pageText ?: ownText)
    // The text as the user last left it in the field, and where their cursor was.
    var typedText by remember { mutableStateOf("") }
    var typedSel by remember { mutableStateOf(androidx.compose.ui.text.TextRange(0)) }
    // Owner, 13 окт: chosen marks live in the words themselves («/идея Посмотреть кино»), shown there as a soft petrol word
    // (MarkLook) and erased whole; nothing rides separately over the field.
    fun tagged(t: String) = t.trim()
    // After «/дело» is picked the line offers where it belongs (работа / личное) — one more tap, not a hunt.
    var sphereNext by remember { mutableStateOf<String?>(null) }
    val ownFiles = remember { mutableStateListOf<Attachment>() }
    val files = pageFiles ?: ownFiles
    var recorder by remember { mutableStateOf<WavRecorder?>(null) }
    var level by remember { mutableFloatStateOf(0f) }
    val transcribing = "voice" in working

    // Text put in for a mode (a caption to edit) leaves with the mode, unless the user changed it into something else.
    var fromMode by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(prefill) { if (!prefill.isNullOrBlank()) { text = prefill; if (modeLabel != null) fromMode = prefill; onPrefillUsed() } }
    LaunchedEffect(modeLabel) { if (modeLabel == null) { if (fromMode != null && text == fromMode) text = ""; fromMode = null } }
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    // Asked again a moment later: opened from the quick-settings tile, the window gets its focus only after the shade closes.
    LaunchedEffect(focusKey) {
        if (focusKey == null) return@LaunchedEffect
        repeat(2) { delay(if (it == 0) 150L else 450L); runCatching { focus.requestFocus() }; keyboard?.show() }
    }
    LaunchedEffect(text, files.size, recorder) { onTyping(text.isNotEmpty() || files.isNotEmpty() || recorder != null) }

    val systemVoice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { text = (text.trimEnd() + " " + it).trim() }
    }

    // Dictation is words, not a recording. The recording stays only when asked for: «голосовое: …» / «voice note …».
    val voiceWord = remember { Regex("^\\s*(голосов\\p{L}*|аудио|voice\\s*note|voice\\s*message)\\s*[:,.—-]?\\s*", RegexOption.IGNORE_CASE) }
    fun gotSpeech(t: String, audio: Attachment) {
        if (voiceWord.containsMatchIn(t)) {
            // One block: the player with what was said under it — not the same words twice.
            val said = t.replace(voiceWord, "").trim().replaceFirstChar { it.uppercaseChar() }
            onSend(tagged(text.trim()), files.toList() + audio.copy(transcript = said.ifBlank { null })); text = ""; files.clear()
            return
        }
        Files.delete(ctx, audio)
        // What it is and where it goes, said in words, becomes marks in the field — shown as marks, not as words (owner, 13 окт).
        val (marks, words) = if (slash && text.isBlank()) vm.marksOf(t) else emptyList<String>() to t
        val full = (marks.joinToString(" ") + " " + text.trimEnd() + " " + words).replace(Regex("\\s{2,}"), " ").trim()
        // On a page the dictated words are shown there first — sent when the user has seen them.
        if (settings.voiceSendNow && pageText == null) { onSend(tagged(full), files.toList()); text = ""; files.clear() } else { text = full; onWrite?.invoke() }
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
        if (recorder != null) return
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

    LaunchedEffect(micKey) { if (micKey != null) { delay(300); mic() } }

    // Everything over the field starts where the field does — right of the rule.
    val lead = if (ruled) MarginW - edge + FieldInset else 0.dp
    Column(modifier.fillMaxWidth()) {
      Column(Modifier.padding(start = lead)) {
        // «/…», «#…», «@…» at the end of the words: a line of choices right over the field, words without a box (owner, 13 окт).
        val trigger = if (slash && modeLabel == null) Regex(if (marksOnPlus) "(?:^|\\s)(@)([\\p{L}\\d_-]*)$" else "(?:^|\\s)([/#@])([\\p{L}\\d_-]*)$").find(text) else null
        // The card's «+»: every mark at once («/» — what and where, «#» — the topic list), until something is typed or picked.
        var picker by remember { mutableStateOf<String?>(null) }
        var pickerText by remember { mutableStateOf("") }
        if (marksOnPlus) {
            val tick = vm.markPicker.value
            val since = remember { tick }
            LaunchedEffect(tick) { if (tick > since) { picker = if (picker == null) "/" else null; pickerText = text } }
            LaunchedEffect(text) { if (picker != null && text != pickerText) picker = null }
        }
        val query = if (picker != null) null else trigger?.groupValues?.get(2)?.lowercase()?.replace('ё', 'е')
        val kinds = listOf(s.tagDeal, s.tagTopic, s.tagIdea)
        val spheres = listOf(s.tagWork, s.tagPersonal)
        // «/готово» and «/запись» still work when typed, but are not offered — words say the same (owner's call, 10 окт).
        val actions = listOf(s.tagWait, s.tagPromise)
        // Just picked a kind: where it belongs is offered next, until the user types on.
        LaunchedEffect(text) { if (sphereNext != null && text != sphereNext) sphereNext = null }
        val sign = picker ?: trigger?.groupValues?.get(1) ?: if (sphereNext != null && text == sphereNext) "+" else null
        val d = vm.data.collectAsState().value
        // One choice: (the mark it adds, what the line shows, its group, a small sign before it).
        data class Pick(val token: String, val label: String, val group: String, val dot: Color? = null, val topic: Boolean = false)
        fun sphereDot(w: String) = if (w == s.tagWork) c.link else c.ok
        val offer: List<Pick> = when (sign) {
            // Only what it is at first; the rest is there when its first letters are typed (owner, 13 окт).
            "/" -> if (picker != null) kinds.map { Pick("/$it", "/$it", "kind") } + spheres.map { Pick("/$it", "/$it", "sphere", sphereDot(it)) } +
                    actions.map { Pick("/$it", "/$it", "action") } + Pick("#", "# " + s.tagTopic + "…", "topicPick")
                else if (query.isNullOrEmpty()) kinds.map { Pick("/$it", "/$it", "kind") }
                else (kinds.map { Pick("/$it", "/$it", "kind") } + spheres.map { Pick("/$it", "/$it", "sphere", sphereDot(it)) } +
                    actions.map { Pick("/$it", "/$it", "action") }).filter { it.label.drop(1).startsWith(query) }
            "+" -> spheres.map { Pick("/$it", "/$it", "sphere", sphereDot(it)) }
            "#" -> d.tasks.filter { it.topic && !it.done }.sortedByDescending { t -> maxOf(t.entries.maxOfOrNull { it.at } ?: "", t.createdAt) }
                .filter { query.isNullOrEmpty() || it.title.lowercase().replace('ё', 'е').contains(query) }.take(8).map { Pick("#[${it.id}]", it.title, "topic", topic = true) }
            "@" -> d.persons.filter { !it.archived && !it.draft }.filter { query.isNullOrEmpty() || it.name.lowercase().replace('ё', 'е').startsWith(query) || it.name.lowercase().replace('ё', 'е').contains(" $query") }
                .take(8).map { Pick("@[${it.name}]", it.name, "person", if (it.org) c.graphite else null) } +
                (if (!query.isNullOrBlank() && d.persons.none { it.name.equals(query, true) }) listOf(Pick("@[" + query.replaceFirstChar { it.uppercase() } + "]", "+ " + query.replaceFirstChar { it.uppercase() }, "person")) else emptyList())
            else -> emptyList()
        }
        fun groupOf(t: String) = when {
            t.startsWith("#") -> "topic"; t.startsWith("@") -> "person"
            t.drop(1).lowercase() in kinds -> "kind"; t.drop(1).lowercase() in spheres -> "sphere"; else -> "action"
        }
        val present = remember(text) { MarkLook.tokens(text) }
        fun pick(p: Pick) {
            if (p.group == "topicPick") { picker = "#"; return }
            picker = null
            // One of each kind of mark: a new choice replaces the old one of its group; a chosen one again — taken off.
            var t = if (trigger != null) text.replace(Regex("(?:^|\\s)[/#@][\\p{L}\\d_-]*$"), "") else text
            val again = present.any { it.equals(p.token, true) }
            present.filter { it.equals(p.token, true) || (groupOf(it) == p.group && p.group != "person") }.forEach { old ->
                t = t.replace(Regex("(?:^|(?<=\\s))" + Regex.escape(old) + "(?:\\s|$)"), "")
            }
            t = t.replace(Regex("\\s{2,}"), " ").trimEnd()
            text = if (again) (if (t.isEmpty()) "" else "$t ") else (if (t.isEmpty()) "" else "$t ") + p.token + " "
            sphereNext = if (!again && p.group == "kind" && p.token != "/" + s.tagTopic && present.none { groupOf(it) == "sphere" }) text else null
            onWrite?.invoke()
        }
        // The line of choices: words, no box. In the main field it stands over the field; on a page — over the page's own
        // writing line, where the words are (owner, 13 окт).
        val line: @Composable (List<Pick>, Modifier) -> Unit = { shown, mod ->
            Row(mod.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                shown.forEach { p ->
                    val on = present.any { it.equals(p.token, true) }
                    Row(Modifier.clip(RoundedCornerShape(8.dp)).clickable { pick(p) }.padding(horizontal = 10.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                        when {
                            p.topic -> { TopicGlyph(c.link, 12.dp); Spacer(Modifier.width(6.dp)) }
                            p.dot != null -> { Box(Modifier.size(6.dp).clip(CircleShape).background(p.dot)); Spacer(Modifier.width(6.dp)) }
                        }
                        Text(p.label, style = Type.body.copy(color = if (on) c.graphite else c.link, fontSize = 16.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium),
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 200.dp))
                        if (on) { Spacer(Modifier.width(5.dp)); TickGlyph(c.graphite, 13.dp) }
                    }
                }
            }
        }
        if (pageText != null) {
            SideEffect { vm.pageMarks.value = if (offer.isNotEmpty()) ({ line(offer, Modifier.offset(x = (-10).dp)) }) else null }
            androidx.compose.runtime.DisposableEffect(Unit) { onDispose { vm.pageMarks.value = null } }
        } else androidx.compose.animation.AnimatedVisibility(offer.isNotEmpty(), enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(140)),
            exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(100))) {
            var shown by remember { mutableStateOf(offer) }
            if (offer.isNotEmpty()) shown = offer
            // No box: the words stand on the page's own paper, faded in under them so the page behind doesn't fight them.
            line(shown, Modifier.background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(c.bg.copy(alpha = 0f), c.bg.copy(alpha = 0.92f), c.bg)))
                .padding(start = 6.dp, end = 8.dp, top = 6.dp, bottom = 4.dp))
        }
        // The live reading: «дело · завтра 15:00 · Маша · работа» — what will be made, before it is sent.
        val read = preview?.takeIf { text.isNotBlank() && offer.isEmpty() }?.invoke(text.trim())
        androidx.compose.animation.AnimatedVisibility(read != null, enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(160)) + androidx.compose.animation.expandVertically(),
            exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(120)) + androidx.compose.animation.shrinkVertically()) {
            var last by remember { mutableStateOf("") }
            if (read != null) last = read
            Text(last, style = Type.small.copy(color = c.link, fontSize = 13.sp), maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 18.dp, end = 12.dp, bottom = 6.dp))
        }
        if (modeLabel != null) Row(Modifier.padding(start = 6.dp, bottom = 6.dp).clip(RoundedCornerShape(50)).background(c.linkSoft)
            .clickable(onClick = onModeClose).padding(start = 12.dp, end = 8.dp, top = 5.dp, bottom = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(modeLabel, style = Type.small.copy(color = c.link, fontSize = 13.5.sp))
            Spacer(Modifier.width(6.dp))
            CloseGlyph(c.link, 16.dp)
        }
        // On a page the picked files show there, under the words; here only for the field itself.
        if (files.isNotEmpty() && pageFiles == null) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                files.toList().forEach { a -> AttachmentChip(a, onRemove = { files.remove(a); if (a.file != null) Files.delete(ctx, a) }) }
            }
        }
      }
        val rec = recorder
        val canSend = (text.isNotBlank() || files.isNotEmpty() || sendEmpty) && !busy
        fun send() { onSend(tagged(text), files.toList()); text = ""; files.clear() }
        // Owner, 14 окт: files are a paperclip everywhere — «+» means marks.
        val attach: @Composable () -> Unit = { AttachPlus(vm, glyph = { ClipGlyph(c.graphite, 19.dp) }) { files += it } }
        // Owner, 13 окт: on a page the bar holds only the microphone — no field, no «send». Held down it records and on release
        // the words appear on the page, at the cursor, to be looked over; a short tap starts, a second one stops. What is written
        // is kept by itself (PageSend: keyboard hidden, page left).
        if (pageText != null) {
            SideEffect { pageSend?.send = { if (text.isNotBlank() || files.isNotEmpty()) send() } }
            fun finish() {
                val r = recorder ?: return
                recorder = null
                val audio = r.stop()
                if (audio != null) vm.transcribe(audio) { gotSpeech(it, audio) }
            }
            Row(Modifier.fillMaxWidth().padding(start = if (ruled) MarginW - edge + FieldInset else 0.dp), verticalAlignment = Alignment.CenterVertically) {
                when {
                    rec != null -> {
                        Box(Modifier.size(FieldH - 2.dp).clip(CircleShape).clickable { recorder = null; rec.cancel() }, contentAlignment = Alignment.Center) {
                            CloseGlyph(c.graphite, 20.dp)
                        }
                        Box(Modifier.size(8.dp).clip(CircleShape).background(c.accent.copy(alpha = 0.4f + level * 0.6f)))
                        Spacer(Modifier.width(10.dp))
                        LevelBars(level, Modifier.weight(1f))
                    }
                    transcribing -> Text(s.transcribing, style = Type.small.copy(color = c.graphite), modifier = Modifier.weight(1f).padding(start = 6.dp))
                    else -> Spacer(Modifier.weight(1f))
                }
                // The card's files: a paperclip by the microphone; what it adds shows on the page, under the words.
                if (marksOnPlus && rec == null && !transcribing) AttachPlus(vm, size = FieldH + 6.dp, glyph = { ClipGlyph(c.graphite, 20.dp) }) { files += it }
                if (transcribing) Box(Modifier.size(FieldH + 6.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = c.link, strokeWidth = 1.5.dp, modifier = Modifier.size(18.dp))
                } else Box(Modifier.size(FieldH + 6.dp).clip(CircleShape).background(if (rec != null) c.accent else Color.Transparent)
                    .semantics { contentDescription = s.a11yMic }
                    .pointerInput(Unit) {
                        detectTapGestures(onPress = {
                            val wasRecording = recorder != null
                            if (wasRecording) { finish(); return@detectTapGestures }
                            mic()
                            val t0 = System.currentTimeMillis()
                            tryAwaitRelease()
                            // Held: released means done. A short tap leaves it recording until the next tap.
                            if (System.currentTimeMillis() - t0 > 450) finish()
                        })
                    }, contentAlignment = Alignment.Center) {
                    MicGlyph(if (rec != null) M.c.onAccent else c.accent, 24.dp)
                }
            }
            return@Column
        }
        // A light line, a little taller than the words, a hairline around it.
        val shape = RoundedCornerShape(FieldH / 2)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            // The planner (owner, 14 окт; design/v9/v9-field-*.png): a printed «графа» — «запись» on the margin, the words on a ruled line.
            if (ruled && planner) Box(Modifier.width(MarginW - edge).heightIn(min = FieldH), contentAlignment = Alignment.CenterEnd) {
                Text(s.fieldLabel, style = Type.italic.copy(color = c.ink.copy(alpha = 0.75f), fontSize = 15.sp), modifier = Modifier.padding(end = 10.dp))
            } else if (ruled) Spacer(Modifier.width(MarginW - edge))
            Row(
                // v8: the field floats a little over the page — a soft shadow, the one raised thing besides menus.
                if (planner) Modifier.weight(1f).padding(start = if (ruled) FieldInset else 0.dp).heightIn(min = FieldH).padding(end = 2.dp)
                else Modifier.weight(1f).padding(start = if (ruled) FieldInset else 0.dp).heightIn(min = FieldH)
                    .shadow(6.dp, shape, ambientColor = c.ink.copy(alpha = 0.10f), spotColor = c.ink.copy(alpha = 0.14f))
                    .clip(shape).background(c.card).border(1.dp, c.hairline, shape).padding(start = 2.dp, end = 5.dp),
                // Long text grows upwards; the buttons stay where the thumb is.
                verticalAlignment = Alignment.Bottom,
            ) {
                if (rec != null) {
                    // Recording: cancel · the pulsing dot and the level · done.
                    Box(Modifier.size(FieldH - 2.dp).clip(CircleShape).clickable { recorder = null; rec.cancel() }, contentAlignment = Alignment.Center) {
                        CloseGlyph(c.graphite, 20.dp)
                    }
                    Row(Modifier.weight(1f).height(FieldH - 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(c.accent.copy(alpha = 0.4f + level * 0.6f)))
                        Spacer(Modifier.width(10.dp))
                        LevelBars(level, Modifier.weight(1f))
                    }
                    FieldButton(filled = true, label = s.a11ySend, onClick = {
                        recorder = null
                        val audio = rec.stop()
                        if (audio != null) vm.transcribe(audio) { gotSpeech(it, audio) }
                    }) { TickGlyph(M.c.onAccent, 18.dp) }
                    return@Row
                }
                attach()
                val baseline = c.ink.copy(alpha = 0.35f)
                Box(Modifier.weight(1f).heightIn(min = FieldH - 2.dp)
                    .then(if (planner) Modifier.drawBehind { drawLine(baseline, androidx.compose.ui.geometry.Offset(0f, size.height - 6.dp.toPx()), androidx.compose.ui.geometry.Offset(size.width, size.height - 6.dp.toPx()), 1.dp.toPx()) } else Modifier)
                    .padding(vertical = 9.dp, horizontal = 2.dp), contentAlignment = Alignment.CenterStart) {
                    if (text.isEmpty()) Text(if (transcribing) s.transcribing else hint, style = if (planner) Type.italic.copy(color = c.graphite, fontSize = 17.sp)
                        else Type.body.copy(fontFamily = rowFamily, color = c.graphite), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    // Words put in from outside (shared, dictated, a mode's prefill) get the cursor after them, not before
                    // (Fable, 0.35); what the user types keeps the cursor where they put it.
                    val value = if (typedText == text) androidx.compose.ui.text.input.TextFieldValue(text, typedSel)
                        else androidx.compose.ui.text.input.TextFieldValue(text, androidx.compose.ui.text.TextRange(text.length))
                    BasicTextField(
                        value = value, onValueChange = { v ->
                            val next = continueList(value, MarkLook.eraseWhole(value, v))
                            text = next.text; typedText = next.text; typedSel = next.selection
                        },
                        textStyle = Type.body.copy(fontFamily = rowFamily, color = c.ink), cursorBrush = SolidColor(c.link), maxLines = 6,
                        visualTransformation = rememberMarkLook(vm),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                }
                // The microphone while nothing is typed; «send» always at the end — pale until there is something to send.
                if (text.isBlank() && !busy) {
                    if (transcribing) Box(Modifier.size(FieldH - 2.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = c.link, strokeWidth = 1.5.dp, modifier = Modifier.size(16.dp))
                    } else FieldButton(label = s.a11yMic, onClick = ::mic) { MicGlyph(c.accent, 21.dp) }
                }
                FieldButton(filled = true, enabled = canSend, busy = busy, label = s.a11ySend, onClick = ::send) {
                    if (busy) CircularProgressIndicator(color = M.c.onAccent, strokeWidth = 1.5.dp, modifier = Modifier.size(14.dp))
                    else SendGlyph(M.c.onAccent, 15.dp)
                }
            }
        }
    }
}

/**
 * «+»: a photo or a video from the gallery, the camera, a file, a contact — each handed to [onAdd]. In the main field it
 * sits at the field's start; on a page (owner, 13 окт) on the margin of the writing line, so what is added lands right there.
 */
@Composable
fun AttachPlus(vm: MainVm, size: Dp = 40.dp, tint: Color = M.c.graphite, glyph: (@Composable () -> Unit)? = null, onAdd: (Attachment) -> Unit) {
    val s = M.s
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
    var cameraFile by remember { mutableStateOf<File?>(null) }
    fun add(a: Attachment?) { if (a != null) onAdd(a) }
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
    Box {
        Box(Modifier.size(size).clip(CircleShape).clickable(onClickLabel = s.a11yAttach, role = androidx.compose.ui.semantics.Role.Button) { menu = true }
            .semantics { contentDescription = s.a11yAttach }, contentAlignment = Alignment.Center) {
            if (glyph != null) glyph() else PlusGlyph(tint, 18.dp)
        }
        SoftMenu(menu, { menu = false }) {
            MenuItem({ PhotoGlyph(it, 21.dp) }, s.attachPhoto) { menu = false; pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) }
            MenuItem({ CameraGlyph(it, 21.dp) }, s.attachCamera) {
                menu = false
                val (f, uri) = Files.newPhotoFile(ctx); cameraFile = f
                runCatching { takePhoto.launch(uri) }
            }
            MenuItem({ ClipGlyph(it, 21.dp) }, s.attachFile) { menu = false; pickFile.launch(arrayOf("*/*")) }
            MenuItem({ PersonGlyph(it, 21.dp) }, s.attachContact) {
                menu = false
                runCatching { pickContact.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)) }
            }
        }
    }
}

/** A page's way to keep what is written on it without a «send» button: the bar fills [send] in, the page calls it. */
class PageSend { var send: () -> Unit = {} }

/** Height of the field: one line of words and a little air. */
private val FieldH: Dp @Composable get() = if (planner) 37.dp else 42.dp
/** The field keeps this much clear of the rule; its words then line up with the page's text. */
private val FieldInset = 4.dp

/** A sign at the end of the field; [filled] — the small terracotta circle of «send», pale while there is nothing to send. */
@Composable
private fun FieldButton(filled: Boolean = false, enabled: Boolean = true, busy: Boolean = false, label: String? = null, onClick: () -> Unit, content: @Composable () -> Unit) {
    // A drawn glyph says nothing to TalkBack: the button carries its name and role itself.
    Box(Modifier.size(FieldH - 2.dp).clip(CircleShape).clickable(enabled = enabled, onClickLabel = label, role = androidx.compose.ui.semantics.Role.Button, onClick = onClick)
        .then(if (label != null) Modifier.semantics { contentDescription = label } else Modifier), contentAlignment = Alignment.Center) {
        if (filled) Box(Modifier.size(if (planner) 29.dp else 30.dp).clip(CircleShape).background(if (enabled || busy) M.c.accent else M.c.accent.copy(alpha = if (planner) 0.7f else 0.35f)), contentAlignment = Alignment.Center) { content() }
        else content()
    }
}

@Composable
private fun MenuItem(glyph: @Composable (Color) -> Unit, label: String, onClick: () -> Unit) {
    DropdownMenuItem(
        leadingIcon = { glyph(M.c.graphite) },
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
    Row(modifier.height(22.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        history.forEach { v ->
            Box(Modifier.width(3.dp).height((3 + v * 18).dp).clip(RoundedCornerShape(2.dp)).background(c.graphite.copy(alpha = 0.6f)))
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
        ) { CloseGlyph(c.bg, 14.dp) }
    }
}

@Composable
fun Thumb(a: Attachment, size: Dp, modifier: Modifier = Modifier) {
    val c = M.c
    val ctx = LocalContext.current
    val bmp by produceState<Bitmap?>(null, a.id) { value = Files.thumb(ctx, a) }
    Box(modifier.size(size).clip(RoundedCornerShape(12.dp)).background(c.chip), contentAlignment = Alignment.Center) {
        bmp?.let { Image(it.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.size(size)) }
        if (a.kind == "video") Box(Modifier.size(24.dp).clip(CircleShape).background(M.c.overlay.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.PlayArrow, null, tint = M.c.onAccent, modifier = Modifier.size(16.dp))
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


/**
 * Marks written into the words («/идея», «#[id]», «@[Маша]») drawn as marks: a soft petrol word — «идея», «≡ Ремонт», «@Маша» —
 * right in the line (owner, 13 окт), and erased as one piece.
 */
object MarkLook {
    val rx = Regex("(?<![\\p{L}\\d/])(/(?:дело|тема|идея|работа|личное|жду|обеща[лю]\\p{L}*|готово|запись|todo|topic|idea|work|personal|wait|promised|done|note)|#\\[[^\\]]+]|@\\[[^\\]]+])(?![\\p{L}\\d])", RegexOption.IGNORE_CASE)

    fun tokens(text: String): List<String> = rx.findAll(text).map { it.value }.toList()

    /** A single character taken out of a mark takes the whole mark with it (and the space after it). */
    // The keyboard still holds the mark it was erasing: a held «⌫» sends a few more deletions against its stale copy, and
    // they come back as a scrap of the mark («/д»). For a moment after a mark is erased, such scraps are dropped.
    private var erased: Triple<String, String, Long>? = null

    // Under quick deletions the field hands in the value of its last drawing, not the last one it was given: the last answer
    // (and when) is the truer «before».
    private var lastOut: Pair<androidx.compose.ui.text.input.TextFieldValue, Long>? = null

    fun eraseWhole(old0: androidx.compose.ui.text.input.TextFieldValue, new: androidx.compose.ui.text.input.TextFieldValue): androidx.compose.ui.text.input.TextFieldValue {
        val t0 = System.currentTimeMillis()
        val old = lastOut?.takeIf { (v, at) -> t0 - at < 300 && v.text != old0.text }?.first ?: old0
        return erase(old, new).also { lastOut = it to t0 }
    }

    private fun erase(old: androidx.compose.ui.text.input.TextFieldValue, new: androidx.compose.ui.text.input.TextFieldValue): androidx.compose.ui.text.input.TextFieldValue {
        erased?.let { (left, mark, at) ->
            val now = System.currentTimeMillis()
            // Not «old == left»: under quick deletions [old] itself may be one of those scraps.
            if (now - at < 1000 && new.text != left) {
                val from = (0 until minOf(left.length, new.text.length)).firstOrNull { left[it] != new.text[it] } ?: minOf(left.length, new.text.length)
                val tail = left.length - from
                val put = if (new.text.length >= tail) new.text.substring(from, new.text.length - tail) else ""
                if (new.text.length - tail >= from && new.text.endsWith(left.substring(from)) && put.isNotEmpty() && mark.startsWith(put.lowercase())) {
                    erased = Triple(left, mark, now)
                    return androidx.compose.ui.text.input.TextFieldValue(left, androidx.compose.ui.text.TextRange(from.coerceAtMost(left.length)))
                }
            }
        }
        if (new.text.length != old.text.length - 1) return new
        val at = (0 until new.text.length).firstOrNull { old.text[it] != new.text[it] } ?: new.text.length
        if (old.text.removeRange(at, at + 1) != new.text) return new
        val m = rx.findAll(old.text).firstOrNull { at in it.range } ?: return new
        val end = if (m.range.last + 1 < old.text.length && old.text[m.range.last + 1] == ' ') m.range.last + 2 else m.range.last + 1
        val t = old.text.removeRange(m.range.first, end)
        erased = Triple(t, m.value.lowercase(), System.currentTimeMillis())
        return androidx.compose.ui.text.input.TextFieldValue(t, androidx.compose.ui.text.TextRange(m.range.first.coerceAtMost(t.length)))
    }
}

/** The field's look for marks: each one a petrol word on soft petrol; [vm] names a topic's id by its title. */
@Composable
fun rememberMarkLook(vm: MainVm?): androidx.compose.ui.text.input.VisualTransformation {
    val c = M.c
    val ink = c.link
    val ground = c.linkSoft
    return remember(ink, ground, vm) {
        androidx.compose.ui.text.input.VisualTransformation { src ->
            val raw = src.text
            val hits = MarkLook.rx.findAll(raw).toList()
            if (hits.isEmpty()) return@VisualTransformation androidx.compose.ui.text.input.TransformedText(src, androidx.compose.ui.text.input.OffsetMapping.Identity)
            // (where the mark is in the words, where it is in what is shown)
            val origStarts = IntArray(hits.size); val origEnds = IntArray(hits.size); val outStarts = IntArray(hits.size); val outEnds = IntArray(hits.size)
            val out = androidx.compose.ui.text.buildAnnotatedString {
                var pos = 0
                hits.forEachIndexed { i, m ->
                    append(raw.substring(pos, m.range.first))
                    val v = m.value
                    val label = when {
                        v.startsWith("#[") -> "≡ " + (vm?.data?.value?.tasks?.find { it.id == v.drop(2).dropLast(1) }?.title ?: v.drop(2).dropLast(1))
                        v.startsWith("@[") -> "@" + v.drop(2).dropLast(1)
                        else -> v.drop(1).lowercase()
                    }
                    origStarts[i] = m.range.first; origEnds[i] = m.range.last + 1
                    outStarts[i] = length
                    pushStyle(androidx.compose.ui.text.SpanStyle(color = ink, background = ground, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium))
                    append("\u2009$label\u2009")
                    pop()
                    outEnds[i] = length
                    pos = m.range.last + 1
                }
                append(raw.substring(pos))
            }
            val map = object : androidx.compose.ui.text.input.OffsetMapping {
                override fun originalToTransformed(offset: Int): Int {
                    var shift = 0
                    for (i in hits.indices) {
                        if (offset <= origStarts[i]) return offset + shift
                        if (offset < origEnds[i]) return outEnds[i]
                        shift = outEnds[i] - origEnds[i]
                    }
                    return (offset + shift).coerceIn(0, out.length)
                }
                override fun transformedToOriginal(offset: Int): Int {
                    var shift = 0
                    for (i in hits.indices) {
                        if (offset <= outStarts[i]) return offset - shift
                        if (offset < outEnds[i]) return origEnds[i]
                        shift = outEnds[i] - origEnds[i]
                    }
                    return (offset - shift).coerceIn(0, raw.length)
                }
            }
            androidx.compose.ui.text.input.TransformedText(out, map)
        }
    }
}
