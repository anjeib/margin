package ru.anjeib.margin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * «В Margin»: what came from Telegram, MAX, mail or a copy — shown as a quote, with room for the user's
 * own words («это Марина, к пятнице»). Sending puts it in «Входящие»; Margin sorts it and says what it became.
 */
@Composable
fun ForwardSheet(
    from: String,
    text: String,
    files: Int,
    loading: Boolean,
    onSend: (comment: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = M.c
    val s = M.s
    var comment by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(250); runCatching { focus.requestFocus() } }
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.32f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                .background(c.bg)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .imePadding().navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spark(); Spacer(Modifier.width(8.dp))
                Text(s.forwardTitle, style = Type.cardTitle.copy(color = c.ink))
                Spacer(Modifier.width(8.dp))
                Text(from, style = Type.small.copy(color = c.graphite, fontSize = 13.sp))
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min)) {
                Box(Modifier.width(2.dp).fillMaxHeight().background(c.accent))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    if (text.isNotBlank()) Text(text.trim(), style = Type.body.copy(color = c.ink, fontSize = 15.sp), maxLines = 7, overflow = TextOverflow.Ellipsis)
                    if (files > 0 || loading) Text(if (loading) s.thinking else s.forwardFiles(files), style = Type.small.copy(color = c.graphite))
                }
            }
            Spacer(Modifier.height(14.dp))
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.chip).padding(horizontal = 14.dp, vertical = 12.dp)) {
                if (comment.isEmpty()) Text(s.forwardCommentHint, style = Type.body.copy(color = c.graphite, fontSize = 15.sp))
                BasicTextField(comment, { comment = it }, textStyle = Type.body.copy(color = c.ink, fontSize = 15.sp),
                    cursorBrush = SolidColor(c.accent), maxLines = 4, modifier = Modifier.fillMaxWidth().focusRequester(focus))
            }
            Spacer(Modifier.height(8.dp))
            Text(s.forwardHint, style = Type.small.copy(color = c.graphite, fontSize = 12.5.sp))
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                Pill(s.cancel, onClick = onDismiss)
                Pill(s.forwardSend, filled = true, onClick = { if (!loading) onSend(comment.trim()) })
            }
        }
    }
}
