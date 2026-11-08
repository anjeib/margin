package ru.anjeib.margin.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import ru.anjeib.margin.data.*

/** Notes are written directly on the same paper as matters, one editable row at a time. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun IdeaSheet(task: Task, vm: MainVm, onClose: () -> Unit, onOpenFull: () -> Unit) {
    val c = M.c
    val s = M.s
    val ctx = LocalContext.current
    val focus = LocalFocusManager.current
    var fieldBounds by remember { mutableStateOf<Rect?>(null) }
    val activeBounds by rememberUpdatedState(fieldBounds)
    var rootBounds by remember { mutableStateOf(Rect.Zero) }
    val rowActs = LocalRowActions.current
    val data by vm.data.collectAsState()
    val relatedTopics = remember(task, data.tasks) { SavedMaterials.relatedTopics(task, data.tasks) }
    var viewing by remember { mutableStateOf<Attachment?>(null) }
    var tagsFor by remember { mutableStateOf<Entry?>(null) }
    var readingIds by remember { mutableStateOf(setOf<String>()) }
    val bornAt = task.entries.minOfOrNull { it.at } ?: task.createdAt
    val day = runCatching { java.time.LocalDate.parse(bornAt.take(10)) }.getOrNull()
    fun blur() { focus.clearFocus() }
    fun copyText(text: String) { ctx.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Margin", text)) }
    fun update(e: Entry, f: (Entry) -> Entry) = vm.updateDiaryEntry(task.id, e.id, f)
    fun addQuote(e: Entry, quote: String) {
        val q = "«" + quote.trim().trim('«', '»', '"') + "»"
        update(e) { cur ->
            val next = if (cur.text.isBlank()) q else cur.text.trimEnd() + "\n" + q
            SavedMaterials.prepare(cur.copy(text = next))
        }
        vm.say("Цитата добавлена в заметку")
    }
    fun copyFile(e: Entry, a: Attachment) {
        vm.copyRecord("task:${task.id}", e.id, a.id)
        val file = Files.file(ctx, a)
        if (file != null) {
            val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", file)
            ctx.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newUri(ctx.contentResolver, a.name, uri))
        } else copyText(a.url ?: a.name)
    }
    CardPage(onClose = { blur(); onClose() }) {
        Column(Modifier.weight(1f, fill = cardFill).fillMaxWidth().screenRule()
            .onGloballyPositioned { rootBounds = it.boundsInRoot() }
            .pointerInput(Unit) { awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val rect = activeBounds
                if (rect != null && !rect.contains(rootBounds.topLeft + down.position)) focus.clearFocus()
            } }) {
            Column(Modifier.weight(1f, fill = cardFill).fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
                Spacer(Modifier.height(4.dp))
                NoteHead(day, emptyList(), {}, task.sphere, { blur(); vm.setSphere(task.id, it) }, title = {
                    var selected by remember { mutableStateOf(task.title) }
                    NoteRowMenu(onDelete = { vm.renameDiaryTitle(task.id, "") }, onCopy = { copyText(selected) },
                        onList = { vm.renameDiaryTitle(task.id, numberedNoteLine(selected)) }) { open ->
                        NoteInlineText("title:${task.id}", task.title, Type.display.copy(color = c.ink, fontSize = 27.sp, lineHeight = 32.sp),
                            onSave = { vm.renameDiaryTitle(task.id, it); true }, onLong = { selected = it; open() }, onBounds = { fieldBounds = it })
                    }
                }, tools = {
                    ActionBar(listOfNotNull(
                        Action(s.actToMatter, false, { blur(); vm.ideaToMatter(task.id); onClose() }) { DealGlyph(it, 22.dp) },
                        rowActs?.let { r -> Action(s.actToTopic, false, { blur(); onClose(); r.toTopic(task.id) }) { TopicGlyph(it, 19.dp) } },
                        Action(s.shareWord, false, { blur(); shareNote(ctx, task, data) }) { ShareGlyph(it, 20.dp) },
                        if (!task.done) Action(s.toArchive, false, { blur(); vm.archiveTopic(task.id); onClose() }) { ArchiveGlyph(it, 22.dp) }
                        else Action(s.bringBack, false, { vm.reviveTopic(task.id) }) { ArchiveGlyph(it, 22.dp) },
                        Action(s.deleteNow, false, { blur(); onClose(); vm.deleteNow(task.id) }) { TrashGlyph(it, 22.dp) },
                    ), slots = 5)
                })
                CardRule()
                if (relatedTopics.isNotEmpty()) {
                    Margined(Modifier.padding(top = 6.dp, bottom = 2.dp)) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            relatedTopics.forEach { tp ->
                                Row(
                                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(c.linkSoft.copy(alpha = 0.55f))
                                        .clickable { blur(); vm.intoTopic(task.id, tp.id); onClose() }
                                        .padding(horizontal = 12.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text("↳ Похоже на тему «# ${tp.title}»", style = Type.small.copy(color = c.ink, fontSize = 13.5.sp), modifier = Modifier.weight(1f))
                                    Text("В тему ›", style = Type.small.copy(color = c.link, fontSize = 13.5.sp))
                                }
                            }
                        }
                    }
                }
                task.entries.forEach { e -> key(e.id) {
                    Margined(margin = { Stamp(e.at) }) {
                        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                if (e.tags.isNotEmpty()) e.tags.joinToString("  ") { "#$it" } else "+ тег",
                                style = Type.small.copy(color = if (e.tags.isNotEmpty()) c.link else c.graphite),
                                modifier = Modifier.fillMaxWidth().clickable { blur(); tagsFor = e }.padding(vertical = 4.dp),
                            )
                            val sources = e.attachments.mapNotNull { it.url }.toSet()
                            e.text.split('\n').forEachIndexed { index, line ->
                                if (line.trim() !in sources && (line.isNotEmpty() || e.attachments.isEmpty())) key("line:$index") {
                                    var selected by remember { mutableStateOf(line) }
                                    NoteRowMenu(onDelete = { update(e) { it.replaceNoteLine(index, null) } }, onCopy = { copyText(selected) },
                                        onList = { update(e) { current -> current.replaceNoteLine(index, numberedNoteLine(current.text.split('\n').getOrElse(index) { selected })) } }) { open ->
                                        NoteInlineText("${e.id}:$index", line, Type.body.copy(color = c.ink, fontSize = 16.sp, lineHeight = 24.sp),
                                            onSave = { value -> update(e) { SavedMaterials.prepare(it.replaceNoteLine(index, value)) }; true },
                                            onLong = { selected = it; open() }, onBounds = { fieldBounds = it })
                                    }
                                }
                            }
                            if (e.description.isNotEmpty()) {
                                var selected by remember { mutableStateOf(e.description) }
                                NoteRowMenu(onDelete = { update(e) { it.copy(description = "") } }, onCopy = { copyText(selected) },
                                    onList = { update(e) { it.copy(description = numberedNoteLine(it.description)) } }) { open ->
                                    NoteInlineText("desc:${e.id}", e.description, Type.body.copy(color = c.graphite, fontSize = 16.sp, lineHeight = 24.sp),
                                        onSave = { value -> update(e) { it.copy(description = value) }; true }, onLong = { selected = it; open() }, onBounds = { fieldBounds = it })
                                }
                            }
                            e.attachments.forEach { a -> key(a.id) {
                                val reading = a.id in readingIds
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    NoteRowMenu(onDelete = { update(e) { current ->
                                        val changed = current.removeCardAttachment(a.id)
                                        val site = a.url?.let(SavedMaterials::site)
                                        changed.copy(tags = changed.tags.filterNot { it == site && changed.attachments.none { other -> other.url?.let(SavedMaterials::site) == site } })
                                    } }, onCopy = { if (a.kind == "link") copyText(a.url.orEmpty()) else copyFile(e, a) },
                                        onList = { update(e) { it.copy(attachments = it.attachments.map { item -> if (item.id == a.id) item.copy(numbered = true) else item }) } },
                                        onShare = if (a.kind in setOf("photo", "video", "file", "audio")) ({ Files.shareIntent(ctx, a)?.let { runCatching { ctx.startActivity(it) } } }) else null) { open ->
                                        Column(Modifier.fillMaxWidth().background(if (a.kind == "link") c.chip else c.bg, RoundedCornerShape(12.dp))
                                            .combinedClickable(onClick = { blur(); if (a.kind == "photo" || a.kind == "video") viewing = a else if (a.kind != "link" && a.kind != "audio") Files.openIntent(ctx, a)?.let { runCatching { ctx.startActivity(it) } } }, onLongClick = open)
                                            .padding(if (a.kind == "link") 12.dp else 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            if (a.kind == "audio") {
                                                VoicePlayerPill(a, Modifier.fillMaxWidth())
                                                if (!a.transcript.isNullOrBlank()) {
                                                    Text(
                                                        a.transcript!!,
                                                        style = Type.body.copy(fontFamily = Literata, color = c.ink, fontSize = 15.sp, lineHeight = 22.sp),
                                                        modifier = Modifier.padding(top = 2.dp, start = 2.dp)
                                                    )
                                                }
                                            } else {
                                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                                                    if (a.numbered) Text("1.", style = Type.body.copy(color = c.ink), modifier = Modifier.padding(top = 4.dp))
                                                    if (a.kind in setOf("photo", "video") || (a.kind == "link" && a.file != null)) Thumb(a, if (a.kind == "link") 64.dp else 84.dp)
                                                    Column(Modifier.weight(1f)) {
                                                        if (a.kind == "link") {
                                                            val site = a.siteName ?: a.url?.let(SavedMaterials::site).orEmpty()
                                                            val mins = (a.duration ?: 0).takeIf { it > 0 }?.let { "$it мин чтения" }
                                                            val metaLine = listOfNotNull(site.takeIf { it.isNotBlank() }, mins).joinToString("  ·  ")
                                                            if (metaLine.isNotEmpty()) {
                                                                Text(metaLine, style = Type.small.copy(color = c.graphite, fontSize = 12.5.sp), modifier = Modifier.padding(bottom = 2.dp))
                                                            }
                                                        }
                                                        NoteInlineText("name:${a.id}", a.name.ifEmpty { "Файл" }, Type.body.copy(color = c.ink, fontSize = 16.sp, lineHeight = 23.sp),
                                                            onSave = { value -> update(e) { it.copy(attachments = it.attachments.map { item -> if (item.id == a.id) item.copy(name = value, titleFromUser = a.kind == "link") else item }) }; true },
                                                            onLong = { open() }, onBounds = { fieldBounds = it })
                                                        if (a.kind == "link") NoteInlineText("url:${a.id}", a.url.orEmpty(), Type.small.copy(color = c.link, fontSize = 13.5.sp),
                                                            onSave = { vm.editDiarySource(task.id, e.id, a.id, it) }, onLong = { open() }, onBounds = { fieldBounds = it })
                                                    }
                                                }
                                            }
                                            if (a.kind == "link") {
                                                Row(
                                                    Modifier.fillMaxWidth().padding(top = 2.dp),
                                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                ) {
                                                    if (!a.transcript.isNullOrBlank()) {
                                                        Text(
                                                            if (reading) "Свернуть статью ▴" else "Читать здесь ▾",
                                                            style = Type.small.copy(color = c.link, fontSize = 13.5.sp),
                                                            modifier = Modifier.clickable {
                                                                blur()
                                                                readingIds = if (reading) readingIds - a.id else readingIds + a.id
                                                            }.padding(vertical = 2.dp),
                                                        )
                                                    }
                                                    Text(
                                                        "Открыть сайт ↗",
                                                        style = Type.small.copy(color = c.link, fontSize = 13.5.sp),
                                                        modifier = Modifier.clickable {
                                                            blur()
                                                            Files.openIntent(ctx, a)?.let { runCatching { ctx.startActivity(it) } }
                                                        }.padding(vertical = 2.dp),
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    if (a.kind == "link" && reading && !a.transcript.isNullOrBlank()) {
                                        val paragraphs = remember(a.transcript) {
                                            a.transcript.orEmpty().split("\n\n").map { it.trim() }.filter { it.isNotEmpty() }
                                        }
                                        Column(
                                            Modifier.fillMaxWidth()
                                                .background(c.card, RoundedCornerShape(12.dp))
                                                .border(1.dp, c.hairline, RoundedCornerShape(12.dp))
                                                .padding(horizontal = 14.dp, vertical = 12.dp),
                                            verticalArrangement = Arrangement.spacedBy(10.dp),
                                        ) {
                                            Text(
                                                "Текст статьи · нажмите на абзац, чтобы вынести в цитату",
                                                style = Type.small.copy(color = c.graphite, fontSize = 12.sp),
                                            )
                                            paragraphs.forEachIndexed { pIdx, para ->
                                                var paraMenu by remember(a.id, pIdx) { mutableStateOf(false) }
                                                Box {
                                                    Text(
                                                        para,
                                                        style = Type.body.copy(
                                                            fontFamily = Literata,
                                                            color = c.ink,
                                                            fontSize = 15.5.sp,
                                                            lineHeight = 24.sp,
                                                        ),
                                                        modifier = Modifier.fillMaxWidth()
                                                            .clip(RoundedCornerShape(6.dp))
                                                            .clickable { paraMenu = true }
                                                            .padding(vertical = 2.dp),
                                                    )
                                                    SoftMenu(paraMenu, { paraMenu = false }) {
                                                        SoftMenuItem("В цитату заметки") {
                                                            paraMenu = false
                                                            addQuote(e, para)
                                                        }
                                                        SoftMenuItem("Скопировать абзац") {
                                                            paraMenu = false
                                                            copyText(para)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            } }
                            e.items.forEach { item ->
                                NoteRowMenu(onDelete = { update(e) { it.copy(items = it.items.filterNot { x -> x.id == item.id }) } }, onCopy = { copyText(item.title) },
                                    onList = { update(e) { it.copy(items = it.items.map { x -> if (x.id == item.id) x.copy(title = numberedNoteLine(x.title)) else x }) } }) { open ->
                                    NoteInlineText("item:${item.id}", item.title, Type.body.copy(color = c.ink, fontSize = 16.sp),
                                        onSave = { value -> update(e) { it.copy(items = it.items.map { x -> if (x.id == item.id) x.copy(title = value) else x }) }; true }, onLong = { open() }, onBounds = { fieldBounds = it })
                                }
                            }
                        }
                    }
                } }
            }
            Column(Modifier.fillMaxWidth().background(c.bg).padding(bottom = 10.dp).fieldBand().fieldRule().padding(start = 14.dp, end = 4.dp).padding(vertical = 8.dp)) {
                SmartComposer(vm, s.cardNoteHint, false, onSend = { t, f ->
                    blur()
                    if (t.isNotBlank() || f.isNotEmpty()) {
                        val entry = SavedMaterials.prepare(Entry(text = t.trim(), attachments = f))
                        vm.insertEntry("task:${task.id}", null, entry)
                        if (entry.attachments.any { it.kind == "link" }) MaterialPreviewWorker.schedule(ctx, entry.id)
                    }
                }, slash = false, floating = planner)
            }
        }
    }
    viewing?.let { Viewer(it, vm, onClose = { viewing = null }) }
    tagsFor?.let { e -> TextDialog("Теги", e.tags.joinToString(", "), "Через запятую", onDismiss = { tagsFor = null }) { value ->
        update(e) { it.copy(tags = SavedMaterials.tags(value)) }; tagsFor = null
    } }
}
