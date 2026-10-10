package ru.anjeib.margin.paper

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * The pen's own gestures (DESIGN.md §14): what is done is struck through the way a hand does it — the line goes across
 * the words, line by line; a day closed in the evening gets a stamp, pressed onto the page.
 */

/** Words with a pen line drawn through them when [struck]; it is drawn, not switched on — unless it was there already. */
@Composable
fun PenText(text: String, style: TextStyle, struck: Boolean, lineColor: Color, modifier: Modifier = Modifier,
            /** A long note shows its beginning; [onCut] says whether the rest was left out. */
            maxLines: Int = Int.MAX_VALUE, onCut: ((Boolean) -> Unit)? = null) {
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val pen = remember { Animatable(if (struck) 1f else 0f) }
    LaunchedEffect(struck) {
        if (struck && pen.value < 1f) pen.animateTo(1f, tween(380, easing = FastOutSlowInEasing))
        else if (!struck) pen.snapTo(0f)
    }
    Text(text, style = style, maxLines = maxLines, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        onTextLayout = { layout = it; onCut?.invoke(it.hasVisualOverflow) }, modifier = modifier.drawWithContent {
        drawContent()
        val l = layout ?: return@drawWithContent
        val p = pen.value
        if (p <= 0f) return@drawWithContent
        // Line by line, as a pen goes: the first fills, then the next.
        val n = l.lineCount
        for (i in 0 until n) {
            val part = ((p * n) - i).coerceIn(0f, 1f)
            if (part <= 0f) break
            val y = (l.getLineTop(i) + l.getLineBottom(i)) / 2 + 1.dp.toPx()
            val x0 = l.getLineLeft(i); val x1 = l.getLineRight(i)
            drawLine(lineColor, Offset(x0, y), Offset(x0 + (x1 - x0) * part, y), strokeWidth = 1.4.dp.toPx())
        }
    })
}

/**
 * The stamp of a closed day: «день закрыт», the hour, and what came of it in the notebook's own marks — × done, › moved
 * on, – not doing. Red, a little askew, as a rubber stamp lands. [fresh]: it is being pressed right now.
 */
@Composable
fun DayStamp(title: String, at: String, s: ru.anjeib.margin.book.DayStats, said: String, fresh: Boolean, modifier: Modifier = Modifier) {
    val c = P.c
    val press = remember { Animatable(if (fresh) 0f else 1f) }
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(fresh) {
        if (fresh) {
            kotlinx.coroutines.delay(350)
            press.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow))
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }
    val ink = c.red.copy(alpha = 0.82f)
    Box(modifier
        .semantics { contentDescription = said }
        .graphicsLayer {
            val v = press.value
            alpha = v.coerceIn(0f, 1f)
            val k = 1.7f - 0.7f * v
            scaleX = k; scaleY = k
        }
        .rotate(-7f)
        .border(1.5.dp, c.red.copy(alpha = 0.75f), RoundedCornerShape(6.dp))
        .padding(3.dp)
        .border(0.8.dp, c.red.copy(alpha = 0.45f), RoundedCornerShape(4.dp))
        .padding(horizontal = 10.dp, vertical = 4.dp)) {
        Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
            Text(title.uppercase(), style = TextStyle(fontFamily = Voice.small.fontFamily, fontWeight = FontWeight(600), fontSize = 10.sp,
                letterSpacing = 1.8.sp, color = ink))
            Text(at, style = Voice.reply.copy(color = ink, fontSize = 17.sp, lineHeight = 20.sp))
            val marks = listOfNotNull(s.done.takeIf { it > 0 }?.let { "× $it" }, s.moved.takeIf { it > 0 }?.let { "› $it" },
                s.dropped.takeIf { it > 0 }?.let { "– $it" }).joinToString("   ")
            if (marks.isNotEmpty()) Text(marks, style = TextStyle(fontFamily = Voice.small.fontFamily, fontSize = 11.sp, letterSpacing = 0.5.sp, color = ink))
        }
    }
}
