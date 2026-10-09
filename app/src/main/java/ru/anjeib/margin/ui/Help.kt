package ru.anjeib.margin.ui

import androidx.compose.runtime.setValue

import androidx.compose.runtime.getValue

import androidx.compose.ui.graphics.asImageBitmap

import androidx.compose.foundation.clickable

import androidx.compose.foundation.border

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

/**
 * «Помощь»: an index of six groups and a search over all of it; a group opens its sections in the app's own look —
 * the rule, sections under petrol badges, screenshots, legends.
 */
@Composable
fun HelpScreen(contentPadding: PaddingValues) {
    val s = M.s
    val c = M.c
    var zoom by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    var group by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf<String?>(null) }
    var q by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf("") }
    val named = s.helpGroups.flatMap { it.second }.toSet()
    // Every section belongs somewhere: those no group names go to the last one.
    fun sectionsOf(g: String): List<HelpSection> {
        val titles = s.helpGroups.firstOrNull { it.first == g }?.second.orEmpty()
        val listed = titles.mapNotNull { t -> s.helpSections.firstOrNull { it.title == t } }
        return if (g == s.helpGroups.last().first) listed + s.helpSections.filter { it.title !in named } else listed
    }
    val words = q.lowercase().split(' ').filter { it.isNotBlank() }
    fun HelpSection.hay() = (title + " " + text + " " + examples.joinToString(" ") + " " + pairs.joinToString(" ") { it.first + " " + it.second }).lowercase()
    androidx.activity.compose.BackHandler(group != null || q.isNotEmpty()) { if (q.isNotEmpty()) q = "" else group = null }
    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().screenRule()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding.vertical()) {
            val g = group
            if (g != null) {
                item("h") {
                    Column {
                        Margined(Modifier.padding(top = 8.dp)) { PaperLink("← " + s.helpTitle) { group = null } }
                        PageHead(g, null)
                    }
                }
                items(sectionsOf(g), key = { it.title }) { HelpCard(it) { zoom = it } }
            } else {
                item("h") { PageHead(s.helpTitle, s.helpIntro) }
                item("q") {
                    Margined(Modifier.padding(bottom = 8.dp)) {
                        androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.chip).padding(horizontal = 16.dp, vertical = 12.dp)) {
                            if (q.isEmpty()) Text(s.helpSearch, style = Type.body.copy(color = c.graphite))
                            androidx.compose.foundation.text.BasicTextField(q, { q = it }, singleLine = true, textStyle = Type.body.copy(color = c.ink),
                                cursorBrush = androidx.compose.ui.graphics.SolidColor(c.link), modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
                if (words.isNotEmpty()) {
                    val found = s.helpSections.filter { h -> words.all { it in h.hay() } }
                    if (found.isEmpty()) item("none") { Margined { Empty(s.nothing, "") } }
                    items(found, key = { "f" + it.title }) { HelpCard(it) { zoom = it } }
                } else items(s.helpGroups, key = { "g" + it.first }) { (title, _) ->
                    // A group: its name and, quietly, what is inside.
                    Margined(Modifier.clickable { group = title }.padding(vertical = 12.dp)) {
                        Column {
                            Text(title, style = Type.cardTitle.copy(color = c.ink))
                            Text(sectionsOf(title).joinToString(" · ") { it.title }, style = Type.small.copy(color = c.graphite, fontSize = 13.sp), maxLines = 2,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
                        }
                    }
                    Hairline(Modifier.padding(start = MarginW))
                }
            }
            item("end") { Spacer(Modifier.height(24.dp)) }
        }
    }
    // A screenshot full size over everything; a tap or «назад» closes it.
    zoom?.let { name ->
        androidx.activity.compose.BackHandler { zoom = null }
        androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().background(M.c.scrim).clickable { zoom = null }, contentAlignment = Alignment.Center) {
            helpBitmap(name)?.let { androidx.compose.foundation.Image(it, null, Modifier.fillMaxSize().padding(24.dp), contentScale = androidx.compose.ui.layout.ContentScale.Fit) }
        }
    }
}

/** A screenshot from assets/help, decoded once. */
@Composable
private fun helpBitmap(name: String): androidx.compose.ui.graphics.ImageBitmap? {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    return androidx.compose.runtime.remember(name) {
        runCatching { ctx.assets.open("help/$name.webp").use { android.graphics.BitmapFactory.decodeStream(it).asImageBitmap() } }.getOrNull()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HelpCard(h: HelpSection, onZoom: (String) -> Unit) {
    val c = M.c
    Column {
        Margined(Modifier.padding(top = 14.dp, bottom = 14.dp)) {
            Column {
                GroupBadge(h.title, Modifier.padding(bottom = 8.dp))
                if (h.text.isNotBlank()) Text(h.text, style = Type.body.copy(color = c.ink, fontSize = 15.sp))
                h.image?.let { name ->
                    helpBitmap(name)?.let { bmp ->
                        androidx.compose.foundation.Image(bmp, null, contentScale = androidx.compose.ui.layout.ContentScale.FillWidth,
                            modifier = Modifier.padding(top = 10.dp).width(210.dp).clip(RoundedCornerShape(14.dp))
                                .border(1.dp, c.hairline, RoundedCornerShape(14.dp)).clickable { onZoom(name) })
                    }
                }
                when (h.legend) {
                    "icons" -> IconLegend()
                    "badges" -> BadgeLegend()
                }
                if (h.examples.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    // Things to say look like what you'd type: quotes on a soft ground.
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        h.examples.forEach { Said(it) }
                    }
                }
                if (h.pairs.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    h.pairs.forEach { (what, does) -> HelpPair(what, does) }
                }
            }
        }
        Hairline(Modifier.padding(start = MarginW))
    }
}

/** One line of a legend or a list: what you see or type on the left, what it means on the right. */
@Composable
private fun HelpPair(what: String, does: String, mark: (@Composable () -> Unit)? = null) {
    val c = M.c
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
        // A mark alone needs only its own narrow column; words share the line.
        Row(if (what.isBlank()) Modifier.width(30.dp) else Modifier.weight(0.42f), verticalAlignment = Alignment.CenterVertically) {
            mark?.let { androidx.compose.foundation.layout.Box(Modifier.width(30.dp), contentAlignment = Alignment.CenterStart) { it() } }
            // What to type or tap — in ink; marks («/», «#», «@») in petrol, as in the field.
            if (what.isNotBlank()) Text(what, style = Type.body.copy(color = if (what.firstOrNull() in setOf('/', '#', '@')) c.link else c.ink, fontSize = 14.5.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium))
        }
        Spacer(Modifier.width(12.dp))
        Text(does, style = Type.small.copy(color = if (what.isBlank()) c.ink else c.graphite, fontSize = 14.sp), modifier = Modifier.weight(if (what.isBlank()) 1f else 0.58f))
    }
}

/** The marks of a to-do row, drawn by the same code as the lists. */
@Composable
private fun IconLegend() {
    val c = M.c
    val ru = ru.anjeib.margin.i18n.L.lang == ru.anjeib.margin.i18n.Lang.Ru
    fun t(r: String, e: String) = if (ru) r else e
    Column(Modifier.padding(top = 6.dp)) {
        HelpPair("", t("дело — нажатие отмечает сделанным", "a to-do — a tap marks it done")) { CheckCircle(false, size = 16.dp) {} }
        HelpPair("", t("сделано", "done")) { CheckCircle(true, size = 16.dp) {} }
        HelpPair("", t("у дела есть время; само время — петрольная ссылка, нажатие открывает часы", "it has an hour; the hour is a link to the clock")) { ClockGlyph(c.graphite, 15.dp) }
        HelpPair("", t("ждём от кого-то — нам должны", "we wait from someone")) { ArrowGlyph(c.graphite, 15.dp, incoming = true) }
        HelpPair("", t("вы обещали кому-то", "you promised someone")) { ArrowGlyph(c.graphite, 15.dp) }
        HelpPair("", t("повторяется", "repeats")) { RepeatGlyph(c.graphite, 15.dp) }
        HelpPair("", t("встреча из календаря телефона", "a meeting from the phone's calendar")) { CalendarGlyph(c.graphite, 15.dp) }
        HelpPair("", t("тема — в ссылках «↳ Тема» и списках", "a topic — in «↳ Topic» links and lists")) { TopicGlyph(c.graphite, 15.dp) }
        HelpPair("", t("идея", "an idea")) { ThoughtGlyph(c.graphite, 15.dp) }
        HelpPair("", t("сделано в ленте темы", "done, in a topic's feed")) { TickGlyph(c.ok, 15.dp) }
    }
}

/** Badges, colours and quiet lines — drawn as they are on screen. */
@Composable
private fun BadgeLegend() {
    val c = M.c
    val s = M.s
    val ru = ru.anjeib.margin.i18n.L.lang == ru.anjeib.margin.i18n.Lang.Ru
    fun t(r: String, e: String) = if (ru) r else e
    Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        @Composable fun row(does: String, mark: @Composable () -> Unit) = Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.foundation.layout.Box(Modifier.weight(0.42f)) { mark() }
            Spacer(Modifier.width(12.dp))
            Text(does, style = Type.small.copy(color = c.graphite, fontSize = 14.sp), modifier = Modifier.weight(0.58f))
        }
        row(t("сфера дела или темы", "the sphere")) { Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { SphereBadge("personal"); SphereBadge("work") } }
        row(t("заголовок блока: дела, контакты, история…", "a block's heading")) { GroupBadge(s.dealsBadge + " · 2") }
        row(t("ваши слова в ленте темы и время", "your words in a topic's feed")) {
            Text(s.recordBadge.uppercase() + "  19:40", style = Type.label.copy(color = c.graphite, fontSize = 10.5.sp, letterSpacing = 1.1.sp),
                modifier = Modifier.clip(RoundedCornerShape(50)).background(c.chip).padding(horizontal = 8.dp, vertical = 2.dp))
        }
        row(t("срок сегодня или завтра — над строкой", "due today or tomorrow")) { Row { SoonBadge(java.time.LocalDate.now(), Modifier.width(80.dp)) } }
        row(t("ссылка на тему под делом; нажатие — открыть", "a link to the topic")) { Text("↳ " + t("Ремонт ванной", "Bathroom"), style = Type.small.copy(color = c.link, fontSize = 13.5.sp)) }
        row(t("дата и время — чёрным; просрочено — терракотой", "dates in ink; late — terracotta")) {
            Text(androidx.compose.ui.text.buildAnnotatedString {
                pushStyle(dateSpan()); append("10 окт"); pop(); append("   ")
                pushStyle(dateSpan().copy(color = c.accent)); append("с 6 окт"); pop()
            }, style = Type.small.copy(fontSize = 13.sp))
        }
        row(t("петроль — ссылки и выбранное: люди, адреса, время", "petrol — links and what is chosen")) { Text(t("Вам от Димы", "From Dima"), style = Type.small.copy(color = c.link, fontSize = 13.5.sp)) }
        row(t("удалено — зачёркнуто; нажатие — вернуть", "removed — struck through")) {
            Text(t("Придумать маскота", "A mascot"), style = Type.small.copy(color = c.faint, fontSize = 13.5.sp, textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough))
        }
        row(t("на полях — день; число серифом", "the day on the margin")) { DayMargin(java.time.LocalDate.now(), java.time.LocalDate.now(), false, strong = true) }
    }
}

/** A phrase as the user would write it: «…» on the chip ground. */
@Composable
fun Said(text: String) {
    val c = M.c
    Text(if (ru.anjeib.margin.i18n.L.lang == ru.anjeib.margin.i18n.Lang.En) "“$text”" else "«$text»", style = Type.small.copy(color = c.ink, fontSize = 14.sp),
        modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(c.chip).padding(horizontal = 10.dp, vertical = 6.dp))
}
