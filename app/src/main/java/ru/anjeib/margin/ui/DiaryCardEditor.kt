package ru.anjeib.margin.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.anjeib.margin.data.*

private val DiaryDraftSaver = Saver<DiaryCardDraft, String>(
    save = { AppJson.encodeToString(DiaryCardDraft.serializer(), it) },
    restore = { AppJson.decodeFromString(DiaryCardDraft.serializer(), it) },
)

/** The same paper, date margin and title as a matter card; one writing area for all note content. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DiaryCardEditor(task: Task, vm: MainVm, onDone: () -> Unit) {
    var draft by rememberSaveable(task.id, stateSaver = DiaryDraftSaver) { mutableStateOf(DiaryCardDraft.of(task)) }
    var body by rememberSaveable(task.id) { mutableStateOf(draft.mainText()) }
    var tagDialog by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }
    val current by rememberUpdatedState(draft)
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val c = M.c
    fun cancel() { vm.discardCardImports(draft); finished = true; onDone() }
    BackHandler { cancel() }
    DisposableEffect(task.id) { onDispose {
        if (!finished && (ctx as? android.app.Activity)?.isChangingConfigurations != true) vm.discardCardImports(current)
    } }
    val day = runCatching { java.time.LocalDate.parse((task.entries.minOfOrNull { it.at } ?: task.createdAt).take(10)) }.getOrNull()
    val tint = c.link
    val highlight = remember(tint) { VisualTransformation { text ->
        val styled = AnnotatedString.Builder(text)
        Regex("https?://\\S+|(?<!\\S)#[\\p{L}\\p{N}_.-]+").findAll(text.text).forEach { m -> styled.addStyle(SpanStyle(color = tint), m.range.first, m.range.last + 1) }
        TransformedText(styled.toAnnotatedString(), OffsetMapping.Identity)
    } }
    Column(Modifier.fillMaxWidth()) {
        NoteHead(day, emptyList(), {}, task.sphere, null, title = {
            Box(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                if (draft.title.isEmpty()) Text("Заголовок", style = Type.display.copy(color = c.graphite, fontSize = 27.sp, lineHeight = 32.sp))
                BasicTextField(draft.title, { draft = draft.copy(title = it.replace('\n', ' ')) },
                    textStyle = Type.display.copy(color = c.ink, fontSize = 27.sp, lineHeight = 32.sp), cursorBrush = SolidColor(c.link), modifier = Modifier.fillMaxWidth())
            }
        }, tools = {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                PaperLink("Отмена", onClick = { cancel() })
                Spacer(Modifier.weight(1f))
                Pill("Сохранить", filled = true, onClick = {
                    if (vm.saveDiaryCard(draft.withMainText(body))) { finished = true; onDone() }
                })
            }
        })
        CardRule()
        Margined {
            Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                BasicTextField(body, { body = it }, minLines = 5,
                    textStyle = Type.body.copy(color = c.ink, fontSize = 16.sp, lineHeight = 24.sp), cursorBrush = SolidColor(c.link), visualTransformation = highlight,
                    decorationBox = { field -> Box(Modifier.fillMaxWidth()) {
                        if (body.isEmpty()) Text("Напишите заметку, вставьте ссылку…", style = Type.body.copy(color = c.graphite, fontSize = 16.sp, lineHeight = 24.sp))
                        field()
                    } }, modifier = Modifier.fillMaxWidth())
                draft.entries.forEach { e ->
                    e.attachments.filter { it.kind != "link" }.forEach { a ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(1f)) { AttachmentsView(listOf(a)) }
                            Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).clickable(onClickLabel = "Удалить вложение") {
                                if (draft.baseEntries.flatMap { it.attachments }.none { it.id == a.id }) Files.delete(ctx, a)
                                draft = draft.copy(entries = draft.entries.map { if (it.id == e.id) it.removeCardAttachment(a.id) else it })
                            }, contentAlignment = Alignment.Center) { CloseGlyph(c.graphite, 18.dp) }
                        }
                    }
                }
                val tags = SavedMaterials.tags(draft.tags)
                if (tags.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    tags.forEach { tag -> Text("#$tag  ×", style = Type.small.copy(color = c.link), modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(c.chip)
                        .clickable(onClickLabel = "Удалить тег $tag") { draft = draft.copy(tags = tags.filterNot { it == tag }.joinToString(", ")) }
                        .padding(horizontal = 10.dp, vertical = 10.dp)) }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AttachPlus(vm, tint = c.link, glyph = { ClipGlyph(c.link, 21.dp) }, onAdd = { a ->
                        val first = draft.entries.first()
                        draft = draft.copy(entries = draft.entries.map { if (it.id == first.id) it.copy(attachments = it.attachments + a) else it })
                    })
                    PaperLink("# Тег", onClick = { tagDialog = true })
                }
            }
        }
    }
    if (tagDialog) TextDialog("Тег", "", "Например: идеи", onDismiss = { tagDialog = false }) {
        draft = draft.copy(tags = (SavedMaterials.tags(draft.tags) + SavedMaterials.tags(it)).distinct().joinToString(", ")); tagDialog = false
    }
}
