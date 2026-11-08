package ru.anjeib.margin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.anjeib.margin.data.*

@Composable
fun SourceCard(a: Attachment) {
    val ctx = LocalContext.current
    val c = M.c
    val site = a.siteName ?: a.url?.let(SavedMaterials::site).orEmpty()
    val mins = (a.duration ?: 0).takeIf { it > 0 }?.let { "$it мин чтения" }
    val saved = if (!a.transcript.isNullOrBlank() && mins == null) "текст сохранён" else null
    val metaLine = listOfNotNull(site.takeIf { it.isNotBlank() }, mins, saved).joinToString("  ·  ")
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(c.chip)
        .clickable { Files.openIntent(ctx, a)?.let { runCatching { ctx.startActivity(it) } } }
        .padding(10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        if (a.file != null) Thumb(a, 52.dp)
        Column(Modifier.weight(1f)) {
            Text(a.name.ifBlank { a.url.orEmpty() }, style = Type.body.copy(color = c.ink, fontSize = 15.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (metaLine.isNotEmpty()) Text(metaLine, style = Type.small.copy(color = c.graphite, fontSize = 12.5.sp), modifier = Modifier.padding(top = 2.dp))
            a.url?.let { Text(it.removePrefix("https://").removePrefix("http://"), style = Type.small.copy(color = c.link, fontSize = 12.sp), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp)) }
        }
    }
}

/** Labels stay separate from #topic links and from the immutable source text. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MaterialDetails(e: Entry, owner: String, vm: MainVm, editable: Boolean = true) {
    val c = M.c
    var tags by remember { mutableStateOf(false) }
    var description by remember { mutableStateOf(false) }
    var source by remember { mutableStateOf(false) }
    var invalid by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            e.tags.forEach { tag ->
                Text("#$tag", style = Type.small.copy(color = c.link), modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(c.chip)
                    .clickable(enabled = editable) { tags = true }.padding(horizontal = 9.dp, vertical = 7.dp))
            }
            if (editable) PaperLink(if (e.tags.isEmpty()) "+ Тег" else "Изменить теги", onClick = { tags = true })
        }
        if (e.description.isNotBlank()) Text(e.description, style = Type.body.copy(color = c.ink, fontSize = 15.sp), modifier = Modifier.heightIn(min = 48.dp).clickable(enabled = editable) { description = true }.padding(vertical = 8.dp))
        if (editable && e.attachments.none { it.kind == "link" }) PaperLink("+ Ссылка на источник", onClick = { source = true })
        if (invalid) Text("Нужна полная ссылка: https://…", style = Type.small.copy(color = c.graphite))
        if (editable) PaperLink(if (e.description.isBlank()) "+ Описание для поиска" else "Изменить описание", onClick = { description = true })
    }
    if (source) TextDialog("Ссылка на источник", "", "https://…", onDismiss = { source = false }) {
        invalid = !vm.addMaterialLink(owner, e.id, it)
        source = false
    }
    if (tags) TextDialog("Теги через запятую", e.tags.joinToString(", "), "Например: дизайн, прочитать", onDismiss = { tags = false }) {
        vm.editMaterial(owner, e.id, it, e.description); tags = false
    }
    if (description) TextDialog("Описание для поиска", e.description, "Что здесь важно и зачем вы это сохранили", onDismiss = { description = false }) {
        vm.editMaterial(owner, e.id, e.tags.joinToString(", "), it); description = false
    }
}

val Entry.isSavedMaterial: Boolean get() = attachments.any { it.kind == "link" || it.kind == "photo" } || tags.isNotEmpty() || description.isNotBlank()
