package ru.anjeib.margin.ui

import ru.anjeib.margin.data.dayMonthShort
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
                        Margined(Modifier.padding(top = 8.dp)) { Voice("‹  " + s.helpTitle, link = true, onClick = { group = null }) }
                        PageHead(g, null)
                    }
                }
                items(sectionsOf(g), key = { it.title }) { HelpCard(it) { zoom = it } }
            } else {
                item("h") { PageHead(s.helpTitle, s.helpIntro) }
                item("q") {
                    Margined(Modifier.padding(bottom = 12.dp)) { androidx.compose.foundation.layout.Box(Modifier.bleed(PanelBleed)) { SearchField(q, { q = it }, s.helpSearch) } }
                }
                if (words.isNotEmpty()) {
                    val found = s.helpSections.filter { h -> words.all { it in h.hay() } }
                    if (found.isEmpty()) item("none") { Margined(Modifier.padding(top = 8.dp)) { Voice(s.nothing) } }
                    items(found, key = { "f" + it.title }) { HelpCard(it) { zoom = it } }
                } else s.helpGroups.forEachIndexed { i, (title, _) ->
                    // A group: its name and, quietly, what is inside — one panel for the whole index.
                    item("g$title") {
                        PlainRow(seatOf(i, s.helpGroups.size), onClick = { group = title }) {
                            Text(title, style = Type.body.copy(color = c.ink, fontSize = 16.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium))
                            RowSub(sectionsOf(title).joinToString(" · ") { it.title }, maxLines = 2)
                        }
                    }
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
    // A section: its caps label over one panel with the words, the screenshot, the legend, what to say.
    SectionHead(h.title, top = 18.dp)
    Margined {
        Panel {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                if (h.text.isNotBlank()) Text(h.text, style = Type.body.copy(color = c.ink, fontSize = 15.sp, lineHeight = 22.sp))
                h.image?.let { name ->
                    helpBitmap(name)?.let { bmp ->
                        androidx.compose.foundation.Image(bmp, null, contentScale = androidx.compose.ui.layout.ContentScale.FillWidth,
                            modifier = Modifier.padding(top = 12.dp).width(210.dp).clip(RoundedCornerShape(14.dp))
                                .border(1.dp, c.hairline, RoundedCornerShape(14.dp)).clickable { onZoom(name) })
                    }
                }
                when (h.legend) {
                    "icons" -> IconLegend()
                    "badges" -> BadgeLegend()
                }
                if (h.examples.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    // Things to say look like what you'd type: quotes on the field's ground.
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

/** The marks of a row, drawn by the same code as the lists. */
@Composable
private fun IconLegend() {
    val c = M.c
    val ru = ru.anjeib.margin.i18n.L.lang == ru.anjeib.margin.i18n.Lang.Ru
    fun t(r: String, e: String) = if (ru) r else e
    Column(Modifier.padding(top = 6.dp)) {
        HelpPair("", t("дело — нажатие на кружок отмечает сделанным", "a to-do — a tap on the circle marks it done")) { CheckCircle(false, size = 18.dp, interactive = false) {} }
        HelpPair("", t("сделано — кружок закрашен, слова зачёркнуты; дело остаётся в своём дне", "done — the circle filled, the words struck; it stays on its day")) { CheckCircle(true, size = 18.dp, interactive = false) {} }
        HelpPair("", t("ждём от кого-то — нажатие на стрелку: «получено»", "waiting on someone — a tap on the arrow: received")) { PromiseMark(incoming = true, size = 26.dp) }
        HelpPair("", t("обещали кому-то", "promised to someone")) { PromiseMark(incoming = false, size = 26.dp) }
        HelpPair("", t("встреча из календаря телефона", "a meeting from the phone's calendar")) { CalendarGlyph(c.graphite, 18.dp) }
        HelpPair("", t("тема — в «Обзоре», поиске и архиве", "a topic — in the overview, search and archive")) { TopicGlyph(c.graphite, 18.dp) }
        HelpPair("", t("идея", "an idea")) { IdeaMark(24.dp) }
    }
}

/** The margin, the labels and the colours — drawn as they are on screen. */
@Composable
private fun BadgeLegend() {
    val c = M.c
    val s = M.s
    val ru = ru.anjeib.margin.i18n.L.lang == ru.anjeib.margin.i18n.Lang.Ru
    fun t(r: String, e: String) = if (ru) r else e
    val today = java.time.LocalDate.now()
    Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        @Composable fun row(does: String, mark: @Composable () -> Unit) = Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.foundation.layout.Box(Modifier.weight(0.42f)) { mark() }
            Spacer(Modifier.width(12.dp))
            Text(does, style = Type.small.copy(color = c.graphite, fontSize = 14.sp), modifier = Modifier.weight(0.58f))
        }
        row(t("на полях — только «когда»: час, день", "on the margin — only when: the hour, the day")) { MarginNote("15:00", modifier = Modifier.padding(end = 8.dp)) }
        row(t("с прошлых дней — терракотой", "left from past days — terracotta")) { MarginNote(s.sinceWord + " " + today.minusDays(2).dayMonthShort(), late = true) }
        row(t("подпись раздела над его панелью", "a section's label over its panel")) { GroupBadge(s.dealsBadge + "  ·  2") }
        row(t("тема дела — под его словами; нажатие — открыть", "a to-do's topic — under its words")) {
            Text("≡ " + t("Ремонт ванной", "Bathroom"), style = Type.small.copy(color = c.link, fontSize = 13.5.sp))
        }
        row(t("сфера темы — в «Обзоре» и в теме", "a topic's sphere — in the overview and the topic")) { Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { SphereBadge("personal"); SphereBadge("work") } }
        row(t("петроль — ссылки и выбранное: люди, адреса", "petrol — links and what is chosen")) { Text(s.promiseToYou(t("Дима", "Dima")), style = Type.small.copy(color = c.link, fontSize = 13.5.sp)) }
        row(t("курсивом — голос Margin: сводки и подсказки", "italic — Margin's own voice")) { Voice(t("3 дела · 1 встреча", "3 to-dos · 1 meeting")) }
        row(t("удалено — зачёркнуто; нажатие — вернуть", "removed — struck through")) {
            Text(t("Придумать маскота", "A mascot"), style = Type.small.copy(color = c.faint, fontSize = 13.5.sp, textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough))
        }
        row(t("на полях ленты темы — день записи", "the day of a record on a topic's margin")) { DayMargin(today, today, false, strong = true) }
    }
}

/** A phrase as the user would write it: «…» on the chip ground. */
@Composable
fun Said(text: String) {
    val c = M.c
    Text(if (ru.anjeib.margin.i18n.L.lang == ru.anjeib.margin.i18n.Lang.En) "“$text”" else "«$text»", style = Type.small.copy(color = c.ink, fontSize = 14.sp),
        modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(c.bg).border(1.dp, c.hairline, RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 6.dp))
}
