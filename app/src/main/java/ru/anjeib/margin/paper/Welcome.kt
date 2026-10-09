package ru.anjeib.margin.paper

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/*
 * The notebook's title page, seen once (DESIGN.md §11): the page explains itself in its own grid — Margin's notes on
 * the margin, the mark on the rule, the words on the right. The marks can be tried right here. Then: the examples, or
 * a clean first page.
 */
@Composable
fun WelcomePage(withExamples: Boolean, onExamples: () -> Unit, onClean: () -> Unit) {
    val c = P.c
    val w = P.w
    var tried by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().background(c.paper)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().rule(c.rule).verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            Spacer(Modifier.height(36.dp))
            PageHead(marginTop = "", marginBottom = null, title = "Margin", line = w.welcomeLine)
            Spacer(Modifier.height(12.dp))
            // Each row is what it says: the note, the mark, the words.
            w.welcomeRows().forEachIndexed { i, (note, mark, text) ->
                val k = when (mark) { "open" -> MarkKind.Open; "done" -> MarkKind.Done; "event" -> MarkKind.Event; "moved" -> MarkKind.Moved; "note" -> MarkKind.Note; else -> MarkKind.None }
                var m by remember { mutableStateOf(k) }
                Appear(i) {
                    MarginRow(notes = listOfNotNull(note?.let { Note(it, if (i == 0) Tone.Link else Tone.Quiet) }), mark = m,
                        onMark = if (k == MarkKind.Open || k == MarkKind.Done) ({ m = if (m == MarkKind.Done) MarkKind.Open else MarkKind.Done; tried = true }) else null,
                        markLabel = w.a11yMark(m == MarkKind.Done)) {
                        PenText(text, Voice.words.copy(color = c.ink), struck = m == MarkKind.Done && k == MarkKind.Open, lineColor = c.graphite)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            MarginSays(if (tried) w.welcomeTried else w.welcomeTry)
            Spacer(Modifier.height(20.dp))
            if (withExamples) {
                MarginLink(w.welcomeExamples, onClick = onExamples)
                MarginLink(w.welcomeClean, onClick = onClean)
            } else MarginLink(w.welcomeBegin, onClick = onClean)
        }
    }
}

/** Rows come in one after another, as if written now. */
@Composable
private fun Appear(i: Int, content: @Composable () -> Unit) {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) { delay(180L + 140L * i); a.animateTo(1f, tween(360)) }
    Box(Modifier.graphicsLayer { alpha = a.value; translationX = (1f - a.value) * -18.dp.toPx() }) { content() }
}
