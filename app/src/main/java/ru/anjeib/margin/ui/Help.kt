package ru.anjeib.margin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.anjeib.margin.i18n.HelpSection

/** «Помощь»: how Margin is arranged — short cards with things to say and what they do. */
@Composable
fun HelpScreen(contentPadding: PaddingValues) {
    val c = M.c
    val s = M.s
    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item("h") {
            Column(Modifier.padding(top = 18.dp, bottom = 8.dp, start = 4.dp)) {
                Text(s.helpTitle, style = Type.display.copy(color = c.ink))
                Spacer(Modifier.height(4.dp))
                Text(s.helpIntro, style = Type.small.copy(color = c.graphite, fontSize = 15.sp))
            }
        }
        items(s.helpSections, key = { it.title }) { HelpCard(it) }
        item("end") { Spacer(Modifier.height(24.dp)) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HelpCard(h: HelpSection) {
    val c = M.c
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.card).padding(16.dp)) {
        Text(h.title, style = Type.cardTitle.copy(color = c.ink))
        if (h.text.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(h.text, style = Type.body.copy(color = c.graphite, fontSize = 15.sp))
        }
        if (h.examples.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            // Things to say look like what you'd type: quotes on a soft ground.
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                h.examples.forEach { Said(it) }
            }
        }
        if (h.pairs.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            h.pairs.forEach { (what, does) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
                    Text(what, style = Type.small.copy(color = c.ink, fontSize = 14.5.sp), modifier = Modifier.weight(0.45f))
                    Spacer(Modifier.width(10.dp))
                    // «Обещание · Вам от Марины» is shown as the badge itself — the way it looks in the lists.
                    val badge = M.s.promiseWord + " · "
                    if (does.startsWith(badge)) Row(Modifier.weight(0.55f)) {
                        Column(Modifier.clip(RoundedCornerShape(12.dp)).background(c.chip).padding(horizontal = 9.dp, vertical = 4.dp)) {
                            Text(M.s.promiseWord, style = Type.small.copy(color = c.accent, fontSize = 12.5.sp))
                            Text(does.removePrefix(badge), style = Type.small.copy(color = c.ink, fontSize = 13.5.sp))
                        }
                    }
                    else Text(does, style = Type.small.copy(color = c.graphite, fontSize = 14.5.sp), modifier = Modifier.weight(0.55f))
                }
            }
        }
    }
}

/** A phrase as the user would write it: «…» on the chip ground. */
@Composable
fun Said(text: String) {
    val c = M.c
    Text(if (ru.anjeib.margin.i18n.L.lang == ru.anjeib.margin.i18n.Lang.En) "“$text”" else "«$text»", style = Type.small.copy(color = c.ink, fontSize = 14.sp),
        modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(c.chip).padding(horizontal = 10.dp, vertical = 6.dp))
}
