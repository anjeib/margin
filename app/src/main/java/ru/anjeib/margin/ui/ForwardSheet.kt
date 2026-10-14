package ru.anjeib.margin.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.anjeib.margin.ai.Forward
import ru.anjeib.margin.data.Attachment

/**
 * «В Margin»: where it came from (Gmail, Telegram…), who wrote it with «+ контакт», the words as a quote —
 * and the usual live field below for what to do with it (typed, dictated, with files). Sending puts it in «Входящие».
 */
@Composable
fun ForwardSheet(
    vm: MainVm,
    from: String,
    sender: Forward.Sender?,
    isContact: Boolean,
    text: String,
    files: Int,
    loading: Boolean,
    onAddContact: () -> Unit,
    onSend: (comment: String, files: List<Attachment>) -> Unit,
    onDismiss: () -> Unit,
    /** Read right here while online: «Разбираю…», then what it became and the way to it. */
    sorting: Boolean = false,
    result: ru.anjeib.margin.ai.Processor.Sorted? = null,
    onOpen: () -> Unit = {},
) {
    val c = M.c
    val s = M.s
    Box(
        Modifier.fillMaxSize().background(M.c.overlay.copy(alpha = 0.32f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                .background(c.bg)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .imePadding().navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp)) {
                Spark(); Spacer(Modifier.width(8.dp))
                Text(s.forwardTitle, style = Type.cardTitle.copy(color = c.ink), modifier = Modifier.weight(1f))
                Box(Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onDismiss), contentAlignment = Alignment.Center) {
                    CloseGlyph(c.graphite, 22.dp)
                }
            }
            Spacer(Modifier.height(8.dp))
            // Where from and who: the app by name, the sender with their address — and a way to keep them.
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.card).padding(start = 14.dp, end = 10.dp, top = 10.dp, bottom = 12.dp)) {
                Text(from.uppercase(), style = Type.label.copy(color = c.graphite, letterSpacing = 1.5.sp))
                if (sender != null) {
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(sender.title, 36.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(sender.title, style = Type.body.copy(color = c.ink, fontSize = 15.5.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val line = listOfNotNull(sender.company?.takeIf { sender.name != null }, sender.address?.takeIf { it != sender.title })
                            if (line.isNotEmpty()) Text(line.joinToString(" · "), style = Type.small.copy(color = c.graphite, fontSize = 13.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Spacer(Modifier.width(8.dp))
                        if (isContact) Text("✓ " + s.inContacts, style = Type.small.copy(color = c.graphite, fontSize = 13.5.sp))
                        else Pill(s.addContact, accent = true, onClick = onAddContact)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                    Box(Modifier.width(2.dp).fillMaxHeight().background(c.link))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        if (text.isNotBlank()) Text(text.trim(), style = Type.body.copy(color = c.ink, fontSize = 15.sp), maxLines = 7, overflow = TextOverflow.Ellipsis)
                        if (files > 0 || loading) Text(if (loading) s.thinking else s.forwardFiles(files), style = Type.small.copy(color = c.graphite))
                    }
                }
            }
            if (result != null) {
                // What the words became, the way the result bubble says it in the app — and a door to it.
                Column(Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(16.dp)).background(c.card)
                    .padding(horizontal = 14.dp, vertical = 12.dp)) {
                    Text(s.forwardResult.uppercase(), style = Type.label.copy(color = c.graphite, letterSpacing = 1.5.sp))
                    Spacer(Modifier.height(6.dp))
                    Text(result.line, style = Type.body.copy(color = c.ink, fontSize = 15.sp, lineHeight = 21.sp))
                }
                Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)) {
                    Pill(s.close, onClick = onDismiss)
                    if (result.open != null) Pill(s.forwardOpen, filled = true, onClick = onOpen)
                }
                return@Column
            }
            Text(if (sorting) s.forwardSorting else s.forwardHint, style = Type.italic.copy(color = if (sorting) c.link else c.graphite, fontSize = 14.5.sp), modifier = Modifier.padding(start = 6.dp, top = 8.dp, bottom = 8.dp))
            // The same live field as everywhere: words, voice, «+» — what to do with it, or just send.
            SmartComposer(
                vm,
                hint = s.forwardCommentHint,
                busy = loading || sorting,
                onSend = { t, f -> onSend(t.trim(), f) },
                focusKey = Unit,
                sendEmpty = true,
                ruled = false,
            )
        }
    }
}
