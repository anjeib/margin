package ru.anjeib.margin.ui

import android.content.Context
import android.content.Intent
import android.content.ClipData
import androidx.core.content.FileProvider
import ru.anjeib.margin.data.Files
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.foundation.layout.Arrangement
import ru.anjeib.margin.data.durationLabel
import ru.anjeib.margin.data.materialBody
import ru.anjeib.margin.data.fullText

import androidx.compose.foundation.layout.Spacer


import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.anjeib.margin.data.AppData
import ru.anjeib.margin.data.Task
import ru.anjeib.margin.data.dayMonthShort
import ru.anjeib.margin.data.dowFull
import ru.anjeib.margin.data.questionRef
import java.time.LocalDate

/** A mention in written words: @[Имя] — a person or company, @[p:место] — a place, @[d:<id>] — a matter, #[<id>] — a topic. */
private val mentionRx = Regex("@\\[([^\\]]+)]|#\\[([^\\]]+)]")

/** The title of a matter by its id — a simple one or a topic's («q:<topic>:<matter>»). */
fun AppData.dealTitle(ref: String): String? {
    questionRef(ref)?.let { (tid, qid) ->
        fun flat(l: List<ru.anjeib.margin.data.Subtask>): List<ru.anjeib.margin.data.Subtask> = l.flatMap { listOf(it) + flat(it.children) }
        return tasks.find { it.id == tid }?.subtasks?.let(::flat)?.find { it.id == qid }?.title
    }
    return tasks.find { it.id == ref }?.title
}

/** What a mention reads as: «Маша», «⌖ Ленина, 5», «○ Позвонить в банк», «# Ремонт кухни». */
fun AppData.mentionLabel(token: String): String {
    val m = mentionRx.matchEntire(token) ?: return token
    m.groupValues[2].takeIf { it.isNotEmpty() }?.let { id -> return "# " + (tasks.find { it.id == id }?.title ?: id) }
    val v = m.groupValues[1]
    return when {
        v.startsWith("p:") -> "⌖ " + v.drop(2)
        v.startsWith("d:") -> "○ " + (dealTitle(v.drop(2)) ?: v.drop(2))
        else -> v
    }
}

/** The words with every mention spelled out — for a title, search, the AI. */
fun AppData.mentionsPlain(text: String): String = mentionRx.replace(text) { mentionLabel(it.value).removePrefix("# ").removePrefix("⌖ ").removePrefix("○ ") }

/**
 * The words of a note with its mentions as petrol links (owner, 14 окт): a person opens their card, a matter or a topic opens
 * itself, a place — the map.
 */
@Composable
fun mentionsText(text: String, data: AppData, onPerson: (String) -> Unit, onOpen: (String) -> Unit): AnnotatedString {
    val c = M.c
    val ctx = androidx.compose.ui.platform.LocalContext.current
    return buildAnnotatedString {
        var pos = 0
        mentionRx.findAll(text).forEach { m ->
            append(text.substring(pos, m.range.first))
            val label = data.mentionLabel(m.value)
            val v = m.groupValues[1]
            val click: () -> Unit = when {
                m.groupValues[2].isNotEmpty() -> { { onOpen(m.groupValues[2]) } }
                v.startsWith("d:") -> { { onOpen(v.drop(2)) } }
                v.startsWith("p:") -> { {
                    runCatching { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("geo:0,0?q=" + android.net.Uri.encode(v.drop(2))))) }
                } }
                else -> { { onPerson(v) } }
            }
            withLink(LinkAnnotation.Clickable("m${m.range.first}", TextLinkStyles(SpanStyle(color = c.link))) { click() }) { append(label) }
            pos = m.range.last + 1
        }
        append(text.substring(pos))
    }
}

/** When a note was begun: its first record, else the day it was made. */
private fun Task.bornAt(): String = entries.minOfOrNull { it.at } ?: history.firstOrNull { it.kind == "created" }?.at ?: (createdAt.take(10) + "T00:00")

/** Shares a diary note via the system share sheet (text + attachments if present). */
fun shareNote(ctx: Context, task: Task, data: AppData) {
    val title = task.title.trim()
    val isGenericTitle = title.isBlank() || title.equals("Голосовая заметка", ignoreCase = true) ||
        title.equals("Голосовое", ignoreCase = true) || title.equals("Voice note", ignoreCase = true)

    val parts = mutableListOf<String>()
    if (!isGenericTitle && title.isNotEmpty()) {
        parts.add(title)
    }

    val allAttachments = task.entries.flatMap { it.attachments }
    val audioTranscripts = allAttachments.filter { it.kind == "audio" }.mapNotNull { it.transcript?.takeIf { t -> t.isNotBlank() } }

    task.entries.forEach { e ->
        val plain = data.mentionsPlain(e.materialBody.ifBlank { e.text }).trim()
        if (plain.isNotEmpty()) parts.add(plain)
        if (e.description.isNotBlank() && !plain.contains(e.description.trim())) {
            parts.add(e.description.trim())
        }
        if (e.tags.isNotEmpty()) {
            parts.add(e.tags.joinToString(" ") { "#$it" })
        }
    }
    if (parts.isEmpty() && audioTranscripts.isNotEmpty()) {
        parts.addAll(audioTranscripts)
    }

    val links = allAttachments.filter { it.kind == "link" }.mapNotNull { it.url?.takeIf { u -> u.isNotBlank() } }
    links.forEach { url ->
        if (parts.none { it.contains(url) }) {
            parts.add(url)
        }
    }

    val shareText = parts.joinToString("\n\n").ifBlank { title.ifBlank { "Заметка" } }

    val fileAtt = allAttachments.firstOrNull { it.kind in setOf("photo", "video", "audio", "file") }
    val f = fileAtt?.let { Files.file(ctx, it) }

    val intent = Intent(Intent.ACTION_SEND).apply {
        if (f != null) {
            val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
            type = fileAtt?.mime ?: "*/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newUri(ctx.contentResolver, fileAtt?.name ?: f.name, uri)
            putExtra(Intent.EXTRA_TEXT, shareText)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } else {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
        if (title.isNotEmpty() && !isGenericTitle) {
            putExtra(Intent.EXTRA_SUBJECT, title)
        }
    }
    val chooser = Intent.createChooser(intent, "Поделиться заметкой").apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { ctx.startActivity(chooser) }
}

/** Shares a linked note of a matter or topic via the system share sheet. */
internal fun shareLinkedNote(ctx: Context, l: LinkedNote, data: AppData) {
    val parts = mutableListOf<String>()
    val plain = data.mentionsPlain(l.text).trim()
    if (plain.isNotEmpty()) parts.add(plain)
    if (l.link.isNotBlank() && !plain.contains(l.link)) parts.add(l.link)
    val shareText = parts.joinToString("\n\n").ifBlank { "Заметка" }

    val fileAtt = l.files.firstOrNull()
    val f = fileAtt?.let { Files.file(ctx, it) }

    val intent = Intent(Intent.ACTION_SEND).apply {
        if (f != null) {
            val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
            type = fileAtt?.mime ?: "*/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newUri(ctx.contentResolver, fileAtt?.name ?: f.name, uri)
            putExtra(Intent.EXTRA_TEXT, shareText)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } else {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
    }
    val chooser = Intent.createChooser(intent, "Поделиться заметкой").apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { ctx.startActivity(chooser) }
}

/** Calculates the initial scroll index so the feed is filled ~2/3 with the latest entries. */
internal fun calculateDiaryFeedTargetIndex(
    daysToShow: Collection<LocalDate>,
    byDay: Map<LocalDate, List<Any>>,
    today: LocalDate,
    showMemory: Boolean,
): Int {
    var count = 0
    var lastNoteIdx = 0
    var hasNotes = false
    daysToShow.forEach { d ->
        count++ // "jd$d" FeedDayHead
        if (d == today && showMemory) {
            count++ // "memory_rediscover"
            lastNoteIdx = count - 1
            hasNotes = true
        }
        val dayList = byDay[d]
        if (!dayList.isNullOrEmpty()) {
            val blocks = withTopicHeads(dayList) { (it as? LinkedNote)?.let { l -> l.open to l.link } }
            blocks.forEach { _ ->
                count++
                lastNoteIdx = count - 1
                hasNotes = true
            }
        }
    }
    if (!hasNotes || count == 0) return 0
    // Keep ~3-4 items visible above the end to fill ~2/3 of the viewport
    return maxOf(0, lastNoteIdx - 3)
}

/**
 * «Заметки» — the diary (owner, 14 окт: ideas became notes). Day by day, newest first: the date on the margin as on a day's page,
 * the weekday in the planner's italic; each note written straight on the page — its hour on the margin, its words, mentions as
 * links — no panels, as in a notebook. Notes from the main field land here too; the field below writes here directly.
 */
/**
 * A note written inside a matter or a topic, shown in «Заметки» too (owner, 16 окт): its words, its files, and a link to where it
 * lives — «# Тема», «○ Дело», «○ Дело · # Тема». [open] — what a tap opens.
 */
internal class LinkedNote(val key: String, val at: String, val text: String, val files: List<ru.anjeib.margin.data.Attachment>,
                         val link: String, val open: String, val sphere: String?,
                         /** The note's own card ([NoteCard]): a tap opens the note itself, the link — where it lives. */
                         val card: String)

/** Every note written in a matter or a topic: the topics' records, the matters' comments and records, a topic's matters' comments. */
internal fun AppData.linkedNotes(): List<LinkedNote> {
    val out = mutableListOf<LinkedNote>()
    fun flat(l: List<ru.anjeib.margin.data.Subtask>): List<ru.anjeib.margin.data.Subtask> = l.flatMap { listOf(it) + flat(it.children) }
    tasks.filter { !it.thought }.forEach { t ->
        // An archived topic is put away with what is in it.
        if (t.topic && t.done) return@forEach
        val own = if (t.topic) "# " + t.title else "○ " + t.title
        // Words that only named a matter of the topic («Уточнить шрифт вывески») are that matter, not a note — as in the topic itself.
        fun norm(x: String) = x.lowercase().filter { it.isLetterOrDigit() }
        val titles = flat(t.subtasks).map { norm(it.title) }.filter { it.isNotEmpty() }
        fun echo(e: ru.anjeib.margin.data.Entry) = e.attachments.isEmpty() && norm(e.text).let { w -> titles.any { q -> w == q || (w.contains(q) && w.length <= q.length + 12) } }
        t.entries.filter { (it.text.isNotBlank() || it.attachments.isNotEmpty()) && !echo(it) }.forEach { e ->
            out += LinkedNote(entryKey(t.id, e.id), e.at, e.text, e.attachments, own, t.id, t.sphere, entryKey(t.id, e.id))
        }
        t.comments.filter { it.text.isNotBlank() }.forEach { cm -> out += LinkedNote(commentKey(t.id, cm.id), cm.at, cm.text, emptyList(), own, t.id, t.sphere, commentKey(t.id, cm.id)) }
        if (t.topic) flat(t.subtasks).forEach { q ->
            q.comments.filter { it.text.isNotBlank() }.forEach { cm ->
                out += LinkedNote(commentKey("q:${t.id}:${q.id}", cm.id), cm.at, cm.text, emptyList(), "○ " + q.title + "  ·  # " + t.title, "q:${t.id}:${q.id}", t.sphere, commentKey("q:${t.id}:${q.id}", cm.id))
            }
        }
    }
    // A note copied into the next occurrence of a repeating matter (before 0.77) is one note: shown once, under the first.
    return out.distinctBy { it.card.substringAfterLast('|') + "|" + it.at + "|" + it.text }
}

@Composable
fun DiaryPage(data: AppData, vm: MainVm, onOpen: (String) -> Unit, onPerson: (String) -> Unit, contentPadding: PaddingValues,
              /** A note of a matter or a topic: its own card ([NoteCard]). */
              onOpenNote: (String) -> Unit = {},
              /** Owner, 16 окт («core»): the page holds one day's notes; a swipe across it turns the day (App). */
              day: LocalDate = LocalDate.now(), events: List<ru.anjeib.margin.data.CalEvent> = emptyList(),
              /**
               * «Дневник» (owner, 17 окт): only the free notes, every day in one feed, newest first; nothing of the diary's
               * matters and topics comes here, and nothing written here goes there.
               */
              journal: Boolean = false) {
    val c = M.c
    val s = M.s
    val today = LocalDate.now()
    // «личное / работа» (owner, 16 окт): the same two marks as on «Темы»; chosen again — all of them.
    val sphere by vm.diarySphere.collectAsState()
    val cat by vm.diaryCategory.collectAsState()
    val tagFilter by vm.diaryTag.collectAsState()
    val gridMode by vm.diaryGrid.collectAsState()
    var archOpen by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var memoryDismissedDay by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }
    val memoryDismissed = memoryDismissedDay == today.toString()
    fun sOk(x: String?) = journal || sphere == null || (if (sphere == "work") x == "work" else x != "work")
    val allNotes = remember(data, sphere, journal) {
        data.tasks.filter { it.thought && it.subtasks.isEmpty() && !it.done && sOk(it.sphere) }.sortedBy { it.bornAt() }
    }
    val memoryCandidates = remember(allNotes, today) {
        val cutoff = today.minusDays(7).toString()
        allNotes.filter { n ->
            n.bornAt() <= cutoff && (n.title.isNotBlank() || n.entries.any { it.text.isNotBlank() || it.attachments.isNotEmpty() })
        }
    }
    val memoryNote = remember(memoryCandidates, today) {
        if (memoryCandidates.isEmpty()) null
        else {
            val hash = kotlin.math.abs((today.toString() + "margin_rediscover").hashCode())
            memoryCandidates[hash % memoryCandidates.size]
        }
    }
    val showMemory = journal && memoryNote != null && !memoryDismissed && cat == null && tagFilter == null

    val notes = remember(allNotes, cat, tagFilter, journal) {
        if (!journal) allNotes
        else allNotes.filter { n ->
            (cat == null || ru.anjeib.margin.data.SavedMaterials.categoryOf(n) == cat) &&
                (tagFilter == null || n.entries.any { e -> e.tags.any { t -> t.equals(tagFilter, ignoreCase = true) } })
        }
    }
    val topTags = remember(allNotes) {
        allNotes.flatMap { it.entries }.flatMap { it.tags }
            .groupingBy { it }.eachCount().entries
            .sortedByDescending { it.value }.map { it.key }.take(8)
    }
    val linked = remember(data, sphere, journal) { if (journal) emptyList() else data.linkedNotes().filter { sOk(it.sphere) } }
    // Both kinds in one diary, by the time they were written.
    val lines: List<Pair<String, Any>> = remember(notes, linked) { (notes.map { it.bornAt() to (it as Any) } + linked.map { it.at to (it as Any) }).sortedBy { it.first } }
    val byDay = remember(lines) { lines.groupBy { runCatching { LocalDate.parse(it.first.take(10)) }.getOrDefault(today) }.mapValues { e -> e.value.map { it.second } }.toSortedMap(compareByDescending { it }) }
    val linkedText = remember(linked) { linked.associate { "l" + it.key to data.mentionsPlain(it.text) + " " + it.link } }
    val pageLines = rememberPageLines()
    val landed = vm.landed.collectAsState().value
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val filtered = cat != null || tagFilter != null
    val daysToShow = remember(filtered, byDay, today, day) {
        if (filtered) byDay.keys.toSortedSet() else (byDay.keys + today + day).toSortedSet()
    }
    val initialDiaryIndex = remember(daysToShow, byDay, today, showMemory) {
        calculateDiaryFeedTargetIndex(daysToShow, byDay, today, showMemory)
    }
    // Found on this page by the find bar: a note by its words.
    val list = androidx.compose.foundation.lazy.rememberLazyListState(initialFirstVisibleItemIndex = initialDiaryIndex)
    val find = LocalFind.current
    val found = remember { mutableListOf<FindHit>() }
    ReportFound("notes", found, list, data)
    // The calendar on the diary's frame: all days' notes in one feed (owner, 17 окт).
    // Under the diary's head «Дневник» is a feed the head's calendar moves too.
    val journalFeed = journal && LocalFramed.current
    val monthOpen by vm.monthOpen.collectAsState()
    val feed = LocalFramed.current && monthOpen && !journal
    val feedStarts = remember { HashMap<LocalDate, Int>() }
    val feedJump by vm.feedJump.collectAsState()
    FeedFollow((feed || journalFeed) && !(journal && gridMode), list, feedStarts, day, feedJump, initialTargetIndex = initialDiaryIndex) { d ->
        if (d != null && vm.feedDay.value != d) {
            vm.feedDay.value = d
            vm.pageDay.value = d.takeIf { it != today }
        }
    }
    val plannerLook = planner
    val framed = LocalFramed.current
    Column(Modifier.fillMaxSize().ruledPage(pageLines).screenRule()) {
        if (journal && allNotes.isNotEmpty()) {
            DiaryFilterBar(
                allNotes = allNotes,
                cat = cat,
                onCat = { vm.diaryCategory.value = it },
                tagFilter = tagFilter,
                topTags = topTags,
                onTag = { vm.diaryTag.value = if (tagFilter == it) null else it },
                gridMode = gridMode,
                onGrid = { vm.diaryGrid.value = it },
            )
        }
        LazyColumn(if (monthOpen && framed) Modifier.weight(1f).fillMaxWidth().calendarDayHorizontalScroll(vm) else Modifier.weight(1f).fillMaxWidth(), state = list, contentPadding = PaddingValues(top = contentPadding.calculateTopPadding(), bottom = contentPadding.calculateBottomPadding())) {
          findable(found, find, { k -> (k as? String)?.let { linkedText[it] }?.let { find != null && findHit(find.words, it) }
              ?: (data.taskOfKey(k)?.let { t -> find != null && findHit(find.words, t.title + " " + data.mentionsPlain(t.entries.joinToString(" ") { it.fullText })) } == true) }) {
            // Owner, 16 окт: the head stays put; only the diary scrolls.
            // In the diary's frame the head is the frame's (Frame.kt).
            // «Дневник»: its name and line, the way back on the margin (App); no calendar, no spheres.
            // Under the diary's head the journal's name stands in the head itself (Frame.kt).
            if (journalFeed) Unit
            else if (journal) pageHeadItem("head") {
                Column {
                    Margined(Modifier.padding(top = 40.dp)) {
                        Column {
                            Text(s.journalTitle, style = Type.pageTitle.copy(color = c.ink))
                            Voice(s.journalSub, Modifier.padding(top = 2.dp))
                        }
                    }
                    DoubleRule(Modifier.padding(top = 14.dp, bottom = 4.dp))
                }
            }
            else if (!framed) pinnedHead("head", plannerLook, calendar = true) {
                Margined(Modifier.padding(top = if (planner) 0.dp else 8.dp), margin = { MarginDay(day.dayOfMonth, day.dayMonthShort().substringAfter(' ')) }) {
                    Column {
                        Row(verticalAlignment = Alignment.Top) {
                            Text(s.diaryTitle, style = Type.pageTitle.copy(color = c.ink), modifier = Modifier.weight(1f))
                            if (!planner) PageToolIcons()
                        }
                        Row(Modifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            Voice(s.diarySub, Modifier.weight(1f))
                            SphereIcons(sphere, { vm.diarySphere.value = it }, clearable = true)
                        }
                        if (day != today) Voice(s.backToToday, Modifier.padding(top = 4.dp), link = true, onClick = { vm.pageDay.value = null })
                    }
                }
                if (planner) DoubleRule(Modifier.padding(top = 18.dp, bottom = 6.dp))
            }
            /** One day's notes: notes of one matter or topic together, under its link. */
            fun androidx.compose.foundation.lazy.LazyListScope.dayNotes(d: LocalDate, list: List<Any>) {
                // Owner, 16 окт: notes of one matter or topic in a day stand together, under its link, on one light tint.
                val blocks = withTopicHeads(list) { (it as? LinkedNote)?.let { l -> l.open to l.link } }
                val pos = blockPositions(blocks) { (it as? LinkedNote)?.open }
                blocks.forEachIndexed { i, x ->
                    if (x is TopicHead) { item("lg" + d + x.id) { TopicBlockHead(x.title, raw = true, top = if (i == 0) 4.dp else 14.dp) { onOpen(x.id) } }; return@forEachIndexed }
                    if (x is LinkedNote) { item("l" + x.key) { LinkedNoteRow(x, data, vm, i, blocks.size, pos[i], onOpen, onOpenNote, onPerson, first = i == 0) }; return@forEachIndexed }
                    val n = x as Task
                    item("n" + n.id) {
                        val at = n.bornAt()
                        val body = n.entries.filter { it.materialBody.isNotBlank() }.joinToString("\n\n") { it.materialBody }
                        val audioAtts = n.entries.flatMap { it.attachments }.filter { it.kind == "audio" }
                        val audioTranscripts = audioAtts.mapNotNull { it.transcript?.takeIf { t -> t.isNotBlank() } }
                        val effectiveBody = if (body.isNotBlank()) body else audioTranscripts.joinToString("\n\n")
                        val plainBody = data.mentionsPlain(effectiveBody)
                        val linkTitles = n.entries.flatMap { it.attachments }.filter { it.kind == "link" }.map { it.name.trim() }.filter { it.isNotEmpty() }
                        val titlePrefix = n.title.trimEnd('…').take(36)
                        val isGenericTitle = n.title.isBlank() || n.title.equals("Голосовая заметка", ignoreCase = true) || n.title.equals("Голосовое", ignoreCase = true) || n.title.equals("Voice note", ignoreCase = true)
                        // The title only when the words or a SourceCard don't already begin with it.
                        val showTitle = !isGenericTitle && n.title.isNotBlank() &&
                            !plainBody.trimStart().startsWith(titlePrefix) &&
                            linkTitles.none { it.startsWith(titlePrefix, ignoreCase = true) } &&
                            audioTranscripts.none { it.startsWith(titlePrefix, ignoreCase = true) }
                        val hour = at.drop(11).take(5)
                        val first = i == 0
                        val related = remember(n, data.tasks) { ru.anjeib.margin.data.SavedMaterials.relatedTopics(n, data.tasks) }
                        Margined(Modifier.landing(n.id in landed, 0), margin = {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = (if (first) 4.dp else 8.dp) + KnotDrop)) { MarginNote(hour) }
                        }) {
                            var menu by remember(n.id) { mutableStateOf(false) }
                            val rowActs = LocalRowActions.current
                            // Owner, 16 окт: a long press offers what the note's card does.
                            Box(Modifier.fillMaxWidth()) { Box(Modifier.align(Alignment.TopEnd).padding(top = 8.dp)) { SoftMenu(menu, { menu = false }) {
                                // Editing and reading use the same complete note card.
                                SoftMenuItem(s.edit) { menu = false; onOpen(n.id) }
                                SoftMenuItem(s.shareWord) { menu = false; shareNote(ctx, n, data) }
                                related.firstOrNull()?.let { tp ->
                                    SoftMenuItem("↳ В тему «# ${tp.title}»") { menu = false; vm.intoTopic(n.id, tp.id) }
                                }
                                SoftMenuItem(s.actToMatter) { menu = false; vm.ideaToMatter(n.id) }
                                rowActs?.let { r -> SoftMenuItem(s.actToTopic) { menu = false; r.toTopic(n.id) } }
                                SoftMenuItem(s.toArchive) { menu = false; vm.archiveTopic(n.id) }
                                SoftMenuItem(s.deleteNow) { menu = false; vm.deleteNow(n.id) }
                            } } }
                            Column(Modifier.padding(vertical = 4.dp).fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                .background(c.card).border(1.dp, c.hairline, RoundedCornerShape(10.dp))) {
                                // Each note is a separate card; a tap opens its complete editor.
                                Column(Modifier.fillMaxWidth().combinedClickable(onClick = { onOpen(n.id) }, onLongClick = { menu = true }).padding(horizontal = 12.dp).padding(top = if (first) 4.dp else 7.dp, bottom = 7.dp)) {
                                    var titleLayout by remember { mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }
                                    var bodyLayout by remember { mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }
                                    // Three lines of a note at most (owner, 14 окт) — the whole of it is in its card.
                                    if (showTitle) WordText(n.title, style = if (planner) Type.body.copy(fontFamily = Literata, color = c.ink, fontSize = 17.sp, lineHeight = 24.sp)
                                        else Type.body.copy(color = c.ink, fontSize = 16.5.sp, lineHeight = 22.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium),
                                        maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                        onTextLayout = { titleLayout = it }, modifier = Modifier.foundMarks(n.title) { titleLayout })
                                    if (effectiveBody.isNotBlank()) {
                                        val shown = mentionsText(effectiveBody, data, onPerson, onOpen)
                                        WordText(shown, style = Type.body.copy(fontFamily = Literata, color = c.ink.copy(alpha = 0.95f), fontSize = rowSize, lineHeight = rowLine), onTextLayout = { bodyLayout = it },
                                            maxLines = if (showTitle) 2 else 4, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                            modifier = Modifier.padding(top = if (showTitle) 3.dp else 0.dp).foundMarks(shown.text) { bodyLayout })
                                    }
                                    n.entries.filter { it.isSavedMaterial }.forEach { entry ->
                                        if (entry.tags.isNotEmpty()) Text(entry.tags.joinToString("  ") { "#$it" }, style = Type.small.copy(color = c.link), modifier = Modifier.padding(top = 6.dp))
                                        if (entry.description.isNotBlank()) Text(entry.description, style = Type.small.copy(color = c.graphite), maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                    }
                                    n.entries.flatMap { it.attachments }.takeIf { it.isNotEmpty() }?.let { Box(Modifier.padding(top = 8.dp)) { AttachmentsView(it, showVoiceTranscript = false) } }
                                    // Who and what the note touches, quietly — when it was made in the field with marks.
                                    val tags = (n.people.map { it } + listOfNotNull(n.place?.let { "⌖ $it" })).filter { t -> !plainBody.contains(t.removePrefix("⌖ ")) }
                                    if (tags.isNotEmpty()) Text(tags.joinToString("  ·  "), style = Type.small.copy(color = c.link, fontSize = subSize), modifier = Modifier.padding(top = 4.dp))
                                }
                            }
                        }
                    }
                }
            }
            if (journal && gridMode) {
                feedStarts.clear()
                val sortedNotes = notes.sortedByDescending { it.bornAt() }
                if (sortedNotes.isEmpty()) {
                    item("empty-grid") { Margined(Modifier.padding(top = 18.dp)) { Voice(s.journalEmpty) } }
                } else {
                    if (showMemory) item("memory_rediscover_grid") {
                        DiaryMemoryCard(
                            note = memoryNote!!,
                            today = today,
                            data = data,
                            vm = vm,
                            onOpen = onOpen,
                            onDismiss = { memoryDismissedDay = today.toString() }
                        )
                    }
                    val pairs = sortedNotes.chunked(2)
                    pairs.forEachIndexed { rowIdx, pair ->
                        item("grid-row-${pair.first().id}") {
                            Margined(Modifier.padding(top = if (rowIdx == 0) 6.dp else 0.dp)) {
                                Row(
                                    Modifier.fillMaxWidth().padding(vertical = 5.dp),
                                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.Top,
                                ) {
                                    pair.forEach { n ->
                                        Box(Modifier.weight(1f)) {
                                            DiaryBinderCard(n = n, data = data, vm = vm, onOpen = onOpen)
                                        }
                                    }
                                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
                val archivedOwn = data.tasks.filter { it.thought && it.done && it.subtasks.isEmpty() }.map { t ->
                    ArchItem(t.id, t.title.ifBlank { t.entries.firstOrNull()?.text.orEmpty() }, t.doneAt, open = { onOpen(t.id) }, restore = { vm.reviveTopic(t.id) }, purge = { vm.removeMatter(t.id) })
                }
                archiveFold("arch", archivedOwn, archOpen) { archOpen = !archOpen }
            } else if (journalFeed) {
                // Under the diary's head: every day with notes, in the order of days like the diary (today always), so the
                // head's week and month move it the same way.
                feedStarts.clear()
                val cs = CountingScope(this)
                if (byDay.isEmpty()) cs.item("empty") { Margined(Modifier.padding(top = 18.dp)) { Voice(s.journalEmpty) } }
                daysToShow.forEach { d ->
                    feedStarts[d] = cs.n
                    cs.item("jd$d") { FeedDayHead(d, today, free = byDay[d].isNullOrEmpty(), freeWord = s.noEntriesWord) }
                    if (d == today && showMemory) {
                        cs.item("memory_rediscover") {
                            DiaryMemoryCard(
                                note = memoryNote!!,
                                today = today,
                                data = data,
                                vm = vm,
                                onOpen = onOpen,
                                onDismiss = { memoryDismissedDay = today.toString() }
                            )
                        }
                    }
                    byDay[d]?.let { cs.dayNotes(d, it) }
                }
                val archivedOwn = data.tasks.filter { it.thought && it.done && it.subtasks.isEmpty() }.map { t ->
                    ArchItem(t.id, t.title.ifBlank { t.entries.firstOrNull()?.text.orEmpty() }, t.doneAt, open = { onOpen(t.id) }, restore = { vm.reviveTopic(t.id) }, purge = { vm.removeMatter(t.id) })
                }
                archiveFold("arch", archivedOwn, archOpen) { archOpen = !archOpen }
                // A whole screen of room after the last day, so a day picked near the end still comes up to the top.
                item("feed-end") { Box(Modifier.fillParentMaxHeight()) }
            } else if (journal) {
                // Every day's notes, the newest day first, its number large on the margin.
                if (byDay.isEmpty()) item("empty") { Margined(Modifier.padding(top = 18.dp)) { Voice(s.journalEmpty) } }
                byDay.forEach { (d, l) ->
                    item("jd$d") { FeedDayHead(d, today) }
                    if (d == today && showMemory) {
                        item("memory_rediscover") {
                            DiaryMemoryCard(
                                note = memoryNote!!,
                                today = today,
                                data = data,
                                vm = vm,
                                onOpen = onOpen,
                                onDismiss = { memoryDismissedDay = today.toString() }
                            )
                        }
                    }
                    dayNotes(d, l)
                }
                val archivedOwn = data.tasks.filter { it.thought && it.done && it.subtasks.isEmpty() }.map { t ->
                    ArchItem(t.id, t.title.ifBlank { t.entries.firstOrNull()?.text.orEmpty() }, t.doneAt, open = { onOpen(t.id) }, restore = { vm.reviveTopic(t.id) }, purge = { vm.removeMatter(t.id) })
                }
                archiveFold("arch", archivedOwn, archOpen) { archOpen = !archOpen }
            } else if (feed) {
                // The calendar open: every day's notes in one feed under the pinned month (owner, 17 окт).
                feedStarts.clear()
                val cs = CountingScope(this)
                cs.stickyHeader("month") {
                    Box(Modifier.background(c.bg).marginRule(marginInk)) {
                        MonthPanel(data, vm, day, events) { d -> vm.pageDay.value = d.takeIf { it != today }; vm.feedJump.value++ }
                    }
                }
                cs.n++
                (byDay.keys + today).toSortedSet().forEach { d ->
                    feedStarts[d] = cs.n
                    cs.item("fd$d") { FeedDayHead(d, today) }
                    val l = byDay[d].orEmpty()
                    if (l.isEmpty()) cs.item("fn$d") { Margined(Modifier.padding(top = 6.dp)) { Voice(s.notesNoneThisDay) } } else cs.dayNotes(d, l)
                }
                item("feed-end") { Box(Modifier.fillParentMaxHeight()) }
            } else {
            if (monthOpen) item("month") { MonthPanel(data, vm, day, events) { d -> vm.pageDay.value = d.takeIf { it != today } } }
            val dayLines = byDay[day].orEmpty()
            if (lines.isEmpty() && day == today) item("empty") { Margined(Modifier.padding(top = 18.dp)) { Empty(s.diaryEmpty, s.diaryEmptyHint) } }
            else if (dayLines.isEmpty()) item("none") { Margined(Modifier.padding(top = 18.dp)) { Voice(s.notesNoneThisDay) } }
            dayNotes(day, dayLines)
            // Owner, 16 окт: archived notes at the end of the page, folded — the diary's own and those of matters and topics.
            val archived = data.tasks.filter { it.thought && it.done && it.subtasks.isEmpty() && sOk(it.sphere) }.map { t ->
                ArchItem(t.id, t.title.ifBlank { t.entries.firstOrNull()?.text.orEmpty() }, t.doneAt, open = { onOpen(t.id) }, restore = { vm.reviveTopic(t.id) }, purge = { vm.removeMatter(t.id) })
            } + data.archivedNotes(vm, onOpenNote) { sOk(it) }
            if (day == today) archiveFold("arch", archived, archOpen) { archOpen = !archOpen }
            }
            item("end") { PageEnd(pageLines) }
            item("tail") { Box(Modifier.width(1.dp).height(40.dp)) }
          }
        }
    }
}

/**
 * A note written in a matter or a topic: the hour on the margin, its words (three lines at most), its files, where it lives.
 * A tap opens the note's own card; the link under it — the matter or topic; a long press — a small menu (owner, 16 окт).
 * In a block ([block]) the link is the block's head, not repeated under each note.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun LinkedNoteRow(x: LinkedNote, data: AppData, vm: MainVm, i: Int, n: Int, block: BlockPos?, onOpen: (String) -> Unit, onOpenNote: (String) -> Unit, onPerson: (String) -> Unit,
                          /** The day's first: close under the day's number. */ first: Boolean = false) {
    val c = M.c
    val s = M.s
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var menu by remember(x.key) { mutableStateOf(false) }
    var edit by remember(x.key) { mutableStateOf(false) }
    if (edit) TextDialog(M.s.edit, x.text, "", onDismiss = { edit = false }) { v ->
        val (kind, ref, id) = x.card.split('|')
        if (v.isNotBlank()) { if (kind == "e") vm.editEntryText("task:$ref", id, v.trim()) else vm.editComment(ref, id, v.trim()) }
        edit = false
    }
    val toTopic = x.link.startsWith("#")
    Margined(if (block == BlockPos.Last) Modifier.padding(bottom = 8.dp) else Modifier, margin = {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = (if (first) 4.dp else 8.dp) + KnotDrop)) { MarginNote(x.at.drop(11).take(5)) }
    }) {
        Box(Modifier.blockTint(block)) {
            Box(Modifier.align(Alignment.TopEnd).padding(top = 8.dp)) { SoftMenu(menu, { menu = false }) {
                SoftMenuItem(s.edit) { menu = false; edit = true }
                SoftMenuItem(s.shareWord) { menu = false; shareLinkedNote(ctx, x, data) }
                SoftMenuItem(if (toTopic) s.toTheTopic else s.toTheMatter) { menu = false; onOpen(x.open) }
                SoftMenuItem(s.toArchive) { menu = false; vm.removeNotePiece(x.card) }
                SoftMenuItem(s.deleteNow) { menu = false; vm.deleteNotePiece(x.card) }
            } }
            PanelPiece(seatOf(i, n)) {
                val inline = LocalInline.current
                val unfolded = inline?.open == x.card
                Column(Modifier.fillMaxWidth().combinedClickable(onClick = { if (inline != null) inline.toggle(x.card) else onOpenNote(x.card) }, onLongClick = { menu = true })
                    .padding(horizontal = if (planner) 0.dp else 16.dp).padding(top = if (first) 4.dp else 8.dp, bottom = 8.dp).then(if (block == BlockPos.Last) Modifier.padding(bottom = 2.dp) else Modifier)) {
                    var layout by remember { mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }
                    if (unfolded) InlinePieceBody(x.card, vm)
                    else {
                    if (x.text.isNotBlank()) {
                        val shown = mentionsText(x.text, data, onPerson, onOpen)
                        WordText(shown, style = Type.body.copy(fontFamily = rowFamily, color = c.ink.copy(alpha = 0.9f), fontSize = rowSize, lineHeight = rowLine),
                            onTextLayout = { layout = it }, maxLines = 3, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.foundMarks(shown.text) { layout })
                    }
                    if (x.files.isNotEmpty()) Box(Modifier.padding(top = 8.dp)) { AttachmentsView(x.files) }
                    }
                    // Where it was written — a link to it (owner, 16 окт); in a block the head says it.
                    if (block == null) WordText(x.link, style = Type.small.copy(color = c.link, fontSize = subSize), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp).clip(RoundedCornerShape(6.dp)).clickable { onOpen(x.open) }.padding(vertical = 2.dp))
                }
            }
        }
    }
}

/**
 * The notes of matters and topics written on [d], for the diary's feed (owner, 17 окт): those of one matter or topic together
 * under its link, on one light tint — as they stood in «Заметки».
 */
internal fun androidx.compose.foundation.lazy.LazyListScope.linkedNotesOfDay(d: LocalDate, list: List<LinkedNote>, data: AppData, vm: MainVm,
    onOpen: (String) -> Unit, onOpenNote: (String) -> Unit, onPerson: (String) -> Unit) {
    val blocks = withTopicHeads(list) { (it as? LinkedNote)?.let { l -> l.open to l.link } }
    val pos = blockPositions(blocks) { (it as? LinkedNote)?.open }
    blocks.forEachIndexed { i, x ->
        if (x is TopicHead) { item("lg" + d + x.id) { TopicBlockHead(x.title, raw = true, top = 14.dp) { onOpen(x.id) } }; return@forEachIndexed }
        val n = x as LinkedNote
        item("l" + n.key) { LinkedNoteRow(n, data, vm, i, blocks.size, pos[i], onOpen, onOpenNote, onPerson) }
    }
}

/** Timeline / stream view: 3 horizontal reading lines (book notebook text lines). */
@Composable
fun FeedGlyph(color: Color, size: androidx.compose.ui.unit.Dp = 18.dp) = Canvas(Modifier.size(size)) {
    val w = this.size.width; val h = this.size.height; val sw = 1.3.dp.toPx()
    drawLine(color, Offset(w * 0.15f, h * 0.28f), Offset(w * 0.85f, h * 0.28f), sw, StrokeCap.Round)
    drawLine(color, Offset(w * 0.15f, h * 0.50f), Offset(w * 0.85f, h * 0.50f), sw, StrokeCap.Round)
    drawLine(color, Offset(w * 0.15f, h * 0.72f), Offset(w * 0.55f, h * 0.72f), sw, StrokeCap.Round)
}

/** 2-column visual binder / card layout: two book page cards side-by-side. */
@Composable
fun GridGlyph(color: Color, size: androidx.compose.ui.unit.Dp = 18.dp) = Canvas(Modifier.size(size)) {
    val w = this.size.width; val h = this.size.height; val st = 1.25.dp.toPx()
    val r = CornerRadius(w * 0.08f)
    drawRoundRect(color, topLeft = Offset(w * 0.14f, h * 0.18f),
        size = Size(w * 0.32f, h * 0.64f), cornerRadius = r,
        style = Stroke(st))
    drawRoundRect(color, topLeft = Offset(w * 0.54f, h * 0.18f),
        size = Size(w * 0.32f, h * 0.64f), cornerRadius = r,
        style = Stroke(st))
}

/** An open book / reading spread for articles and saved web links. */
@Composable
fun ArticleGlyph(color: Color, size: androidx.compose.ui.unit.Dp = 15.dp) = Canvas(Modifier.size(size)) {
    val w = this.size.width; val h = this.size.height; val sw = 1.3.dp.toPx()
    val path = androidx.compose.ui.graphics.Path().apply {
        moveTo(w * 0.5f, h * 0.82f)
        cubicTo(w * 0.38f, h * 0.74f, w * 0.24f, h * 0.76f, w * 0.12f, h * 0.80f)
        lineTo(w * 0.12f, h * 0.24f)
        cubicTo(w * 0.24f, h * 0.20f, w * 0.38f, h * 0.18f, w * 0.5f, h * 0.26f)
        cubicTo(w * 0.62f, h * 0.18f, w * 0.76f, h * 0.20f, w * 0.88f, h * 0.24f)
        lineTo(w * 0.88f, h * 0.80f)
        cubicTo(w * 0.76f, h * 0.76f, w * 0.62f, h * 0.74f, w * 0.5f, h * 0.82f)
        close()
    }
    drawPath(path, color, style = Stroke(sw))
    drawLine(color, Offset(w * 0.5f, h * 0.26f), Offset(w * 0.5f, h * 0.82f), sw)
}

/** All notes: two neat stacked paper sheets. */
@Composable
fun StackGlyph(color: Color, size: androidx.compose.ui.unit.Dp = 15.dp) = Canvas(Modifier.size(size)) {
    val w = this.size.width; val h = this.size.height; val sw = 1.25.dp.toPx()
    val r = CornerRadius(w * 0.08f)
    // Back sheet
    drawRoundRect(color.copy(alpha = 0.55f), topLeft = Offset(w * 0.24f, h * 0.12f),
        size = Size(w * 0.60f, h * 0.68f), cornerRadius = r, style = Stroke(sw))
    // Front sheet
    drawRoundRect(color, topLeft = Offset(w * 0.14f, h * 0.22f),
        size = Size(w * 0.60f, h * 0.68f), cornerRadius = r, style = Stroke(sw))
}

private data class DiaryCatInfo(
    val key: String,
    val title: String,
    val color: Color,
    val count: Int,
    val glyph: @Composable (Color) -> Unit,
)

/**
 * Reading spectrum infographic bar and view mode toggle at the top of «Дневник».
 * Replaces bulky boxed filter buttons with an elegant proportional content spectrum
 * and iconic micro-infographic counters in Margin's book aesthetic.
 */
@Composable
internal fun DiaryFilterBar(
    allNotes: List<Task>,
    cat: String?,
    onCat: (String?) -> Unit,
    tagFilter: String?,
    topTags: List<String>,
    onTag: (String) -> Unit,
    gridMode: Boolean,
    onGrid: (Boolean) -> Unit,
) {
    val c = M.c
    val counts = remember(allNotes) {
        allNotes.groupingBy { ru.anjeib.margin.data.SavedMaterials.categoryOf(it) }.eachCount()
    }
    val hasAudioOnly = remember(allNotes) {
        val fileNotes = allNotes.filter { ru.anjeib.margin.data.SavedMaterials.categoryOf(it) == "files" }
        val atts = fileNotes.flatMap { it.entries }.flatMap { it.attachments }
        atts.isNotEmpty() && atts.all { it.kind == "audio" }
    }
    val allCats = remember(counts, hasAudioOnly, c) {
        listOf(
            DiaryCatInfo("links", "Статьи", c.link, counts["links"] ?: 0) { ArticleGlyph(it, 15.dp) },
            DiaryCatInfo("media", "Снимки", c.accent, counts["media"] ?: 0) { PhotoGlyph(it, 15.dp) },
            DiaryCatInfo("files", if (hasAudioOnly) "Аудио" else "Файлы", c.sea, counts["files"] ?: 0) {
                if (hasAudioOnly) MicGlyph(it, 15.dp) else ClipGlyph(it, 15.dp)
            },
            DiaryCatInfo("notes", "Мысли", if (c.dark) Color(0xFF7E8488) else Color(0xFF9E9B93), counts["notes"] ?: 0) { NoteGlyph(it, 15.dp) },
        )
    }
    val activeCats = remember(allCats) { allCats.filter { it.count > 0 } }
    if (activeCats.isEmpty()) return

    var showTags by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }

    Margined(Modifier.padding(top = 4.dp, bottom = 4.dp)) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(end = 14.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            // 1. Proportional Spectrum Bar (инфографическая полоска-срез)
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(3.5.dp)
                    .clip(RoundedCornerShape(2.dp)),
                horizontalArrangement = Arrangement.spacedBy(1.5.dp)
            ) {
                activeCats.forEach { cInfo ->
                    val isSelected = cat == cInfo.key
                    val alpha = if (cat == null || isSelected) 1f else 0.22f
                    Box(
                        Modifier
                            .weight(cInfo.count.toFloat())
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(2.dp))
                            .background(cInfo.color.copy(alpha = alpha))
                            .clickable { onCat(if (isSelected) null else cInfo.key) }
                    )
                }
            }

            // 2. Infographic Micro-Counters + View Mode Switcher
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Interactive micro-infographics
                Row(
                    Modifier
                        .weight(1f, fill = false)
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val allSelected = cat == null && tagFilter == null
                    // "Все" item
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (allSelected) c.linkSoft else Color.Transparent)
                            .clickable(role = androidx.compose.ui.semantics.Role.Tab) {
                                onCat(null)
                                if (tagFilter != null) onTag(tagFilter)
                            }
                            .semantics {
                                contentDescription = "Все: ${allNotes.size}"
                                selected = allSelected
                            }
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        StackGlyph(if (allSelected) c.link else c.graphite.copy(alpha = 0.8f), 15.dp)
                        Text(
                            allNotes.size.toString(),
                            style = Type.markM.copy(
                                color = if (allSelected) c.link else c.graphite,
                                fontSize = 13.sp,
                                fontWeight = if (allSelected) FontWeight.SemiBold else FontWeight.Medium
                            )
                        )
                    }

                    // Divider dot
                    Text("·", style = Type.meta.copy(color = c.hairline, fontSize = 12.sp))

                    // Categories with their custom glyphs and numbers
                    activeCats.forEach { cInfo ->
                        val isSelected = cat == cInfo.key
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSelected) cInfo.color.copy(alpha = if (c.dark) 0.25f else 0.12f) else Color.Transparent)
                                .clickable(
                                    onClickLabel = "${cInfo.title}: ${cInfo.count}",
                                    role = androidx.compose.ui.semantics.Role.Tab
                                ) { onCat(if (isSelected) null else cInfo.key) }
                                .semantics {
                                    contentDescription = "${cInfo.title}: ${cInfo.count}"
                                    selected = isSelected
                                }
                                .padding(horizontal = 5.dp, vertical = 3.dp)
                        ) {
                            cInfo.glyph(if (isSelected) cInfo.color else c.graphite.copy(alpha = 0.85f))
                            Text(
                                cInfo.count.toString(),
                                style = Type.markM.copy(
                                    color = if (isSelected) cInfo.color else c.graphite,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                                )
                            )
                            if (isSelected) {
                                Spacer(Modifier.width(1.dp))
                                Text("✕", style = Type.small.copy(color = cInfo.color, fontSize = 10.sp))
                            }
                        }
                    }

                    // If a tag is active, show the tag badge alongside
                    if (tagFilter != null) {
                        Text("·", style = Type.meta.copy(color = c.hairline, fontSize = 12.sp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(c.linkSoft)
                                .clickable { onTag(tagFilter) }
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                        ) {
                            Text(
                                "#$tagFilter",
                                style = Type.meta.copy(
                                    color = c.link,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                            Text(
                                "✕",
                                style = Type.meta.copy(color = c.graphite, fontSize = 10.sp)
                            )
                        }
                    }
                }

                Spacer(Modifier.width(8.dp))

                // View Mode & Tags Tools (Теги, Лента, Подшивка)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    if (topTags.isNotEmpty()) {
                        Box(
                            Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (showTags || tagFilter != null) c.linkSoft else Color.Transparent)
                                .clickable { showTags = !showTags }
                                .semantics {
                                    contentDescription = "Теги"
                                    selected = showTags || tagFilter != null
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            TopicsGlyph(
                                color = if (showTags || tagFilter != null) c.link else c.graphite.copy(alpha = 0.5f),
                                size = 15.dp
                            )
                        }
                    }
                    Box(
                        Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (!gridMode) c.linkSoft else Color.Transparent)
                            .clickable(role = androidx.compose.ui.semantics.Role.Tab) { onGrid(false) }
                            .semantics {
                                contentDescription = "Лента"
                                selected = !gridMode
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        FeedGlyph(
                            color = if (!gridMode) c.link else c.graphite.copy(alpha = 0.5f),
                            size = 16.dp
                        )
                    }
                    Box(
                        Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (gridMode) c.linkSoft else Color.Transparent)
                            .clickable(role = androidx.compose.ui.semantics.Role.Tab) { onGrid(true) }
                            .semantics {
                                contentDescription = "Подшивка"
                                selected = gridMode
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        GridGlyph(
                            color = if (gridMode) c.link else c.graphite.copy(alpha = 0.5f),
                            size = 16.dp
                        )
                    }
                }
            }

            // 3. Smooth expandable tags strip
            AnimatedVisibility(
                visible = (showTags || tagFilter != null) && topTags.isNotEmpty(),
                enter = fadeIn(tween(150)) + expandVertically(tween(150)),
                exit = fadeOut(tween(100)) + shrinkVertically(tween(100))
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(top = 1.dp, bottom = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    topTags.forEach { tg ->
                        val isTagActive = tagFilter != null && tagFilter.equals(tg, ignoreCase = true)
                        Text(
                            "#$tg",
                            style = Type.small.copy(
                                color = if (isTagActive) c.link else c.graphite,
                                fontSize = 12.sp,
                                fontWeight = if (isTagActive) FontWeight.SemiBold else FontWeight.Normal
                            ),
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isTagActive) c.linkSoft else Color.Transparent)
                                .clickable { onTag(tg) }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * «Из архива» (Rediscover card): an elegant, quiet morning excerpt from past notes older than 7 days.
 */
@Composable
internal fun DiaryMemoryCard(
    note: Task,
    today: LocalDate,
    data: AppData,
    vm: MainVm,
    onOpen: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val c = M.c
    val s = M.s
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val born = runCatching { LocalDate.parse(note.bornAt().take(10)) }.getOrNull()
    val daysAgo = if (born != null) java.time.temporal.ChronoUnit.DAYS.between(born, today).toInt() else 7
    val timeLabel = when {
        daysAgo >= 365 -> "${daysAgo / 365} г. назад"
        daysAgo >= 30 -> "${daysAgo / 30} мес. назад"
        else -> "$daysAgo дн. назад"
    }
    val dateLabel = born?.dayMonthShort() ?: ""
    val label = if (dateLabel.isNotBlank()) "Из архива · $dateLabel ($timeLabel)" else "Из архива · $timeLabel"

    val audioAtt = note.entries.flatMap { it.attachments }.firstOrNull { it.kind == "audio" }
    val visualAtt = note.entries.flatMap { it.attachments }.firstOrNull { it.kind == "photo" || (it.kind == "link" && it.file != null) }
    val body = note.entries.filter { it.materialBody.isNotBlank() }.joinToString("\n\n") { it.materialBody }
    val audioTranscript = audioAtt?.transcript?.takeIf { it.isNotBlank() }
    val effectiveText = if (body.isNotBlank()) body else audioTranscript.orEmpty()
    val plainText = data.mentionsPlain(effectiveText)
    val isGenericTitle = note.title.isBlank() || note.title.equals("Голосовая заметка", ignoreCase = true) || note.title.equals("Голосовое", ignoreCase = true)
    val titlePrefix = note.title.trimEnd('…').take(36)
    val showTitle = !isGenericTitle && !plainText.startsWith(titlePrefix)
    val related = remember(note, data.tasks) { ru.anjeib.margin.data.SavedMaterials.relatedTopics(note, data.tasks) }

    Margined(Modifier.padding(vertical = 4.dp), margin = {
        MarginNote("архив", late = false)
    }) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(c.card)
                .border(1.dp, c.sea.copy(alpha = if (c.dark) 0.35f else 0.20f), RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // 1. Compact header line: Archive label + ✕ dismiss
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(Modifier.size(5.dp).clip(CircleShape).background(c.sea))
                    Text(
                        label,
                        style = Type.small.copy(
                            fontFamily = Literata,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                            color = c.sea,
                            fontSize = 12.sp
                        ),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
                Box(
                    Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .clickable(onClickLabel = s.dismiss) { onDismiss() }
                        .semantics { contentDescription = s.dismiss },
                    contentAlignment = Alignment.Center
                ) {
                    Text("✕", style = Type.small.copy(color = c.graphite.copy(alpha = 0.6f), fontSize = 11.sp))
                }
            }

            // 2. Content snippet: split layout if image is present
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onOpen(note.id) },
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    if (showTitle) {
                        Text(
                            note.title,
                            style = Type.body.copy(
                                fontFamily = Literata,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                                color = c.ink,
                                fontSize = 15.sp,
                                lineHeight = 20.sp
                            ),
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                    if (plainText.isNotBlank()) {
                        Text(
                            plainText,
                            style = Type.body.copy(
                                fontFamily = Literata,
                                color = c.ink.copy(alpha = 0.88f),
                                fontSize = 14.sp,
                                lineHeight = 19.sp
                            ),
                            maxLines = if (showTitle) 2 else 3,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                    if (audioAtt != null) {
                        VoicePlayerPill(audioAtt, Modifier.fillMaxWidth().padding(top = 2.dp))
                    }
                }
                if (visualAtt != null) {
                    Box(
                        Modifier
                            .size(54.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(c.chip)
                    ) {
                        Thumb(visualAtt, 54.dp)
                    }
                }
            }

            // 3. Compact quick actions row
            Row(
                Modifier.fillMaxWidth().padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "→ в дело",
                    style = Type.small.copy(color = c.link, fontSize = 12.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium),
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { vm.ideaToMatter(note.id) }
                        .padding(vertical = 2.dp, horizontal = 2.dp)
                )
                if (related.isNotEmpty()) {
                    val tp = related.first()
                    Text("·", style = Type.small.copy(color = c.hairline))
                    Text(
                        "↳ в тему «# ${tp.title.take(14)}»",
                        style = Type.small.copy(color = c.link, fontSize = 12.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { vm.intoTopic(note.id, tp.id) }
                            .padding(vertical = 2.dp, horizontal = 2.dp)
                    )
                }
                Text("·", style = Type.small.copy(color = c.hairline))
                Text(
                    "в архив",
                    style = Type.small.copy(color = c.graphite, fontSize = 12.sp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { vm.archiveTopic(note.id) }
                        .padding(vertical = 2.dp, horizontal = 2.dp)
                )
                Text("·", style = Type.small.copy(color = c.hairline))
                Text(
                    "поделиться",
                    style = Type.small.copy(color = c.graphite, fontSize = 12.sp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { shareNote(ctx, note, data) }
                        .padding(vertical = 2.dp, horizontal = 2.dp)
                )
            }
        }
    }
}

/**
 * A visual paper card in the 2-column «Подшивка» view of «Дневник» (Fabric-style visual knowledge base on paper).
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun DiaryBinderCard(n: Task, data: AppData, vm: MainVm, onOpen: (String) -> Unit) {
    val c = M.c
    val s = M.s
    val at = n.bornAt()
    val dayLabel = runCatching { LocalDate.parse(at.take(10)).dayMonthShort() }.getOrDefault("")
    val atts = remember(n.entries) { n.entries.flatMap { it.attachments } }
    val linkAtt = remember(atts) { atts.firstOrNull { it.kind == "link" } }
    val mediaAtt = remember(atts) { atts.firstOrNull { it.kind == "photo" || it.kind == "video" } }
    val visualAtt = mediaAtt ?: linkAtt?.takeIf { it.file != null }
    val audioAtt = remember(atts) { atts.firstOrNull { it.kind == "audio" } }
    val cat = remember(n) { ru.anjeib.margin.data.SavedMaterials.categoryOf(n) }
    val body = remember(n.entries) { n.entries.filter { it.materialBody.isNotBlank() }.joinToString("\n\n") { it.materialBody } }
    val audioTranscript = remember(audioAtt) { audioAtt?.transcript?.takeIf { it.isNotBlank() } }
    val plainBody = remember(body, data) { data.mentionsPlain(body) }
    val effectivePlain = if (plainBody.isNotBlank()) plainBody else audioTranscript.orEmpty()
    val desc = remember(n.entries) { n.entries.firstNotNullOfOrNull { it.description.takeIf { d -> d.isNotBlank() } }.orEmpty() }
    val tags = remember(n.entries) { n.entries.flatMap { it.tags }.distinct().take(2) }
    val showTitle = n.title.isNotBlank() && !plainBody.trimStart().startsWith(n.title.trimEnd('…').take(40))
    val related = remember(n, data.tasks) { ru.anjeib.margin.data.SavedMaterials.relatedTopics(n, data.tasks) }

    var menu by remember(n.id) { mutableStateOf(false) }
    val rowActs = LocalRowActions.current
    val ctx = androidx.compose.ui.platform.LocalContext.current
    Box(Modifier.fillMaxWidth()) {
        Box(Modifier.align(Alignment.TopEnd).padding(top = 6.dp)) {
            SoftMenu(menu, { menu = false }) {
                SoftMenuItem(s.edit) { menu = false; onOpen(n.id) }
                SoftMenuItem(s.shareWord) { menu = false; shareNote(ctx, n, data) }
                related.firstOrNull()?.let { tp ->
                    SoftMenuItem("↳ В тему «# ${tp.title}»") { menu = false; vm.intoTopic(n.id, tp.id) }
                }
                SoftMenuItem(s.actToMatter) { menu = false; vm.ideaToMatter(n.id) }
                rowActs?.let { r -> SoftMenuItem(s.actToTopic) { menu = false; r.toTopic(n.id) } }
                SoftMenuItem(s.toArchive) { menu = false; vm.archiveTopic(n.id) }
                SoftMenuItem(s.deleteNow) { menu = false; vm.deleteNow(n.id) }
            }
        }
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(c.card)
                .border(1.dp, c.hairline, RoundedCornerShape(14.dp))
                .combinedClickable(onClick = { onOpen(n.id) }, onLongClick = { menu = true }),
        ) {
            if (visualAtt != null) {
                CardBanner(visualAtt, height = if (mediaAtt != null) 114.dp else 88.dp)
            }
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(5.dp),
            ) {
                val metaText = when {
                    linkAtt != null -> {
                        val site = linkAtt.siteName ?: linkAtt.url?.let(ru.anjeib.margin.data.SavedMaterials::site).orEmpty()
                        val mins = (linkAtt.duration ?: 0).takeIf { it > 0 }?.let { "$it мин" }
                        listOfNotNull(site.takeIf { it.isNotBlank() }, mins).joinToString(" · ")
                    }
                    audioAtt != null -> listOfNotNull("Голосовое", audioAtt.durationLabel().takeIf { it.isNotBlank() }).joinToString(" · ")
                    cat == "media" -> listOfNotNull("Снимок", dayLabel.takeIf { it.isNotBlank() }).joinToString(" · ")
                    cat == "files" -> listOfNotNull("Файл", dayLabel.takeIf { it.isNotBlank() }).joinToString(" · ")
                    else -> dayLabel
                }
                if (metaText.isNotEmpty()) {
                    Text(
                        metaText,
                        style = Type.small.copy(
                            color = if (linkAtt != null) c.link else c.graphite,
                            fontSize = 11.5.sp,
                        ),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
                val isGenericTitle = n.title.isBlank() || n.title.equals("Голосовая заметка", ignoreCase = true) || n.title.equals("Голосовое", ignoreCase = true)
                if (audioAtt != null && effectivePlain.isNotBlank()) {
                    Text(
                        effectivePlain,
                        style = Type.body.copy(fontFamily = Literata, color = c.ink, fontSize = 14.5.sp, lineHeight = 20.sp),
                        maxLines = 4,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                } else if (cat == "notes" && !showTitle && plainBody.isNotBlank()) {
                    Text(
                        plainBody,
                        style = Type.body.copy(fontFamily = Literata, color = c.ink, fontSize = 15.sp, lineHeight = 21.sp),
                        maxLines = 5,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                } else {
                    val cardTitle = (linkAtt?.name?.takeIf { it.isNotBlank() } ?: n.title.takeIf { !isGenericTitle }).orEmpty().ifBlank { effectivePlain.take(60) }
                    if (cardTitle.isNotBlank()) {
                        Text(
                            cardTitle,
                            style = Type.body.copy(
                                fontFamily = Literata,
                                color = c.ink,
                                fontSize = 15.sp,
                                lineHeight = 20.sp,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                            ),
                            maxLines = 3,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        )
                    }
                    val snippet = effectivePlain.takeIf { it.isNotBlank() && showTitle } ?: desc
                    if (snippet.isNotBlank()) {
                        Text(
                            snippet,
                            style = Type.small.copy(color = c.graphite, fontSize = 13.sp, lineHeight = 18.sp),
                            maxLines = 3,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        )
                    }
                }
                if (tags.isNotEmpty()) {
                    Text(
                        tags.joinToString("  ") { "#$it" },
                        style = Type.small.copy(color = c.link, fontSize = 11.5.sp),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun CardBanner(a: ru.anjeib.margin.data.Attachment, height: androidx.compose.ui.unit.Dp) {
    val c = M.c
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val bmp by androidx.compose.runtime.produceState<android.graphics.Bitmap?>(null, a.id, a.file) {
        value = ru.anjeib.margin.data.Files.thumb(ctx, a)
    }
    Box(
        Modifier
            .fillMaxWidth()
            .height(height)
            .background(c.chip),
        contentAlignment = Alignment.Center,
    ) {
        bmp?.let {
            androidx.compose.foundation.Image(
                bitmap = it.asImageBitmap(),
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
