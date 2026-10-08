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

/** «Помощь»: how Margin is arranged — in the app's own look: the rule, the title, sections under petrol badges. */
@Composable
fun HelpScreen(contentPadding: PaddingValues) {
    val c = M.c
    val s = M.s
    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().screenRule()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding.vertical()) {
            item("h") { PageHead(s.helpTitle, s.helpIntro) }
            items(s.helpSections, key = { it.title }) { HelpCard(it) }
            item("end") { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HelpCard(h: HelpSection) {
    val c = M.c
    Column {
        Margined(Modifier.padding(top = 14.dp, bottom = 14.dp)) {
            Column {
                GroupBadge(h.title, Modifier.padding(bottom = 8.dp))
                if (h.text.isNotBlank()) Text(h.text, style = Type.body.copy(color = c.ink, fontSize = 15.sp))
                if (h.examples.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    // Things to say look like what you'd type: quotes on a soft ground.
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        h.examples.forEach { Said(it) }
                    }
                }
                if (h.pairs.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    h.pairs.forEach { (what, does) ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
                            // What to type or tap — in ink; marks («/», «#», «@») in petrol, as in the field.
                            Text(what, style = Type.body.copy(color = if (what.firstOrNull() in setOf('/', '#', '@')) c.link else c.ink, fontSize = 14.5.sp,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium), modifier = Modifier.weight(0.42f))
                            Spacer(Modifier.width(12.dp))
                            Text(does, style = Type.small.copy(color = c.graphite, fontSize = 14.sp), modifier = Modifier.weight(0.58f))
                        }
                    }
                }
            }
        }
        Hairline(Modifier.padding(start = MarginW))
    }
}

/** A phrase as the user would write it: «…» on the chip ground. */
@Composable
fun Said(text: String) {
    val c = M.c
    Text(if (ru.anjeib.margin.i18n.L.lang == ru.anjeib.margin.i18n.Lang.En) "“$text”" else "«$text»", style = Type.small.copy(color = c.ink, fontSize = 14.sp),
        modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(c.chip).padding(horizontal = 10.dp, vertical = 6.dp))
}
