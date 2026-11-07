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
    var viewing by remember { mutableStateOf<Attachment?>(null) }
    var tagsFor by remember { mutableStateOf<Entry?>(null) }
    val bornAt = task.entries.minOfOrNull { it.at } ?: task.createdAt
    val day = runCatching { java.time.LocalDate.parse(bornAt.take(10)) }.getOrNull()
    fun blur() { focus.clearFocus() }
    fun copyText(text: String) { ctx.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Margin", text)) }
    fun update(e: Entry, f: (Entry) -> Entry) = vm.updateDiaryEntry(task.id, e.id, f)
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
                        if (!task.done) Action(s.toArchive, false, { blur(); vm.archiveTopic(task.id); onClose() }) { ArchiveGlyph(it, 22.dp) }
                        else Action(s.bringBack, false, { vm.reviveTopic(task.id) }) { ArchiveGlyph(it, 22.dp) },
                        Action(s.deleteNow, false, { blur(); onClose(); vm.deleteNow(task.id) }) { TrashGlyph(it, 22.dp) },
                    ), slots = 4)
                })
                CardRule()
                task.entries.forEach { e -> key(e.id) {
                    Margined(margin = { Stamp(e.at) }) {
                        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (e.tags.isNotEmpty()) Text(e.tags.joinToString("  ") { "#$it" }, style = Type.small.copy(color = c.link),
                                modifier = Modifier.fillMaxWidth().clickable { blur(); tagsFor = e }.padding(vertical = 6.dp))
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
                                NoteRowMenu(onDelete = { update(e) { current ->
                                    val changed = current.removeCardAttachment(a.id)
                                    val site = a.url?.let(SavedMaterials::site)
                                    changed.copy(tags = changed.tags.filterNot { it == site && changed.attachments.none { other -> other.url?.let(SavedMaterials::site) == site } })
                                } }, onCopy = { if (a.kind == "link") copyText(a.url.orEmpty()) else copyFile(e, a) },
                                    onList = { update(e) { it.copy(attachments = it.attachments.map { item -> if (item.id == a.id) item.copy(numbered = true) else item }) } },
                                    onShare = if (a.kind in setOf("photo", "video", "file", "audio")) ({ Files.shareIntent(ctx, a)?.let { runCatching { ctx.startActivity(it) } } }) else null) { open ->
                                    Row(Modifier.fillMaxWidth().background(if (a.kind == "link") c.chip else c.bg, RoundedCornerShape(12.dp))
                                        .combinedClickable(onClick = { blur(); if (a.kind == "photo" || a.kind == "video") viewing = a else if (a.kind != "link") Files.openIntent(ctx, a)?.let { runCatching { ctx.startActivity(it) } } }, onLongClick = open)
                                        .padding(if (a.kind == "link") 12.dp else 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                                        if (a.numbered) Text("1.", style = Type.body.copy(color = c.ink), modifier = Modifier.padding(top = 4.dp))
                                        if (a.kind in setOf("photo", "video") || (a.kind == "link" && a.file != null)) Thumb(a, if (a.kind == "link") 56.dp else 84.dp)
                                        Column(Modifier.weight(1f)) {
                                            NoteInlineText("name:${a.id}", a.name.ifEmpty { "Файл" }, Type.body.copy(color = c.ink, fontSize = 16.sp, lineHeight = 23.sp),
                                                onSave = { value -> update(e) { it.copy(attachments = it.attachments.map { item -> if (item.id == a.id) item.copy(name = value, titleFromUser = a.kind == "link") else item }) }; true },
                                                onLong = { open() }, onBounds = { fieldBounds = it })
                                            if (a.kind == "link") NoteInlineText("url:${a.id}", a.url.orEmpty(), Type.small.copy(color = c.link, fontSize = 14.sp),
                                                onSave = { vm.editDiarySource(task.id, e.id, a.id, it) }, onLong = { open() }, onBounds = { fieldBounds = it })
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
