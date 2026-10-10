package ru.anjeib.margin.paper

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import ru.anjeib.margin.book.Book
import ru.anjeib.margin.book.personLines
import ru.anjeib.margin.book.hasSamples
import ru.anjeib.margin.book.Dir
import ru.anjeib.margin.book.Kind
import ru.anjeib.margin.book.Line
import ru.anjeib.margin.book.Repeat
import ru.anjeib.margin.book.State
import ru.anjeib.margin.book.Who
import java.time.LocalDate

/*
 * Search, settings and «Как читать поля» — the same paper and grid, so nothing in the notebook looks like another app.
 */

/** Search: lines, threads, people — by any words; results by day, as the notebook keeps them. */
@Composable
fun SearchPage(book: Book, today: LocalDate, onMark: (String) -> Unit, onOpen: (String) -> Unit, onThread: (String) -> Unit, onPerson: (String) -> Unit) {
    val c = P.c
    val w = P.w
    var q by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val key = q.trim().lowercase().replace('ё', 'е')
    fun has(s: String?) = s != null && key.isNotEmpty() && s.lowercase().replace('ё', 'е').contains(key)
    // «Протокол» finds «Protokol_pedsoveta.pdf»: a file's name is often written in Latin letters.
    val latin = translit(key)
    fun hasName(s: String?) = has(s) || (s != null && latin.length >= 3 && latin != key && s.lowercase().contains(latin))
    // A line is found by its words, place, people, the phrase it came from — and by what its files say:
    // a name, a caption, words recognised on a photo, a voice note's transcript.
    val lines = if (key.length < 2) emptyList() else book.lines.filter { l ->
        has(l.text) || has(l.place) || l.who.any { has(it.name) } || has(l.source) ||
            l.files.any { f -> hasName(f.name) || has(f.caption) || has(f.ocr) || has(f.transcript) }
    }
    val threads = if (key.length < 2) emptyList() else book.threads.filter { has(it.title) }
    // Only people the notebook has something about: a name met once in a phrase and gone is not a person to find.
    val people = if (key.length < 2) emptyList() else book.persons.filter { p ->
        (has(p.name) || has(p.about)) && (p.kept || book.personLines(p.name).let { pl -> pl.promised.size + pl.waited.size + pl.other.size } > 0)
    }
    LazyColumn(Modifier.fillMaxSize().rule(c.rule), contentPadding = PaddingValues(bottom = 40.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp, end = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.width(Grid.text - 6.dp))
                val shape = RoundedCornerShape(22.dp)
                Box(Modifier.weight(1f).heightIn(min = 44.dp).clip(shape).background(c.sheet).border(1.dp, c.hairline, shape).padding(horizontal = 14.dp, vertical = 10.dp)) {
                    if (q.isEmpty()) Text(w.searchHint, style = Voice.words.copy(color = c.graphite))
                    BasicTextField(q, { q = it }, textStyle = Voice.words.copy(color = c.ink), cursorBrush = SolidColor(c.link),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus), singleLine = true)
                }
            }
        }
        if (threads.isNotEmpty()) {
            item { SectionHead(w.threads) }
            items(threads, key = { "t" + it.id }) { t -> IndexRow(emptyList(), t.title, null, { onThread(t.id) }) }
        }
        if (people.isNotEmpty()) {
            item { SectionHead(w.people) }
            items(people, key = { "p" + it.id }) { p -> IndexRow(emptyList(), p.name, p.about, { onPerson(p.name) }) }
        }
        if (lines.isNotEmpty()) {
            val groups = lines.groupBy { it.date ?: it.written.toLocalDate() }.toSortedMap(compareByDescending { it }).map { (d, l) -> d to l }
            linesByDay(groups, book, today, w, w.noDay, onMark, onOpen)
        }
        if (key.length >= 2 && lines.isEmpty() && threads.isEmpty() && people.isEmpty()) item { MarginSays(w.nothingFound, Modifier.padding(top = 12.dp)) }
    }
}

/** «Как читать поля»: the page explains itself — each mark and note shown as it looks, with what it means. */
@Composable
fun HelpPage(book: Book, today: LocalDate) {
    val c = P.c
    val w = P.w
    fun line(text: String, kind: Kind = Kind.Todo, state: State = State.Open) = Line(id = "help-$text", text = text, kind = kind, state = state, day = today.toString())
    val rows: List<Pair<String, Line>> = w.helpRows().map { (lineText, note, k) ->
        val l = when (k) {
            "done" -> line(lineText, state = State.Done)
            "event" -> line(lineText, Kind.Event)
            "note" -> line(lineText, Kind.Note)
            "time" -> line(lineText).copy(time = "15:00", who = listOf(Who(if (w.ru) "Олег" else "Oliver")), place = if (w.ru) "Покровка, 17" else "12 High St")
            "tail" -> line(lineText).copy(day = today.minusDays(2).toString())
            "repeat" -> line(lineText).copy(repeat = Repeat("weekly", 1, listOf(1, 3, 5)), time = "08:00")
            "to" -> line(lineText).copy(who = listOf(Who("Лёша", Dir.To)))
            "from" -> line(lineText).copy(who = listOf(Who("Дима", Dir.From)))
            "ask" -> line(lineText).copy(ask = listOf("when"))
            else -> line(lineText)
        }
        note to l
    }
    LazyColumn(Modifier.fillMaxSize().rule(c.rule), contentPadding = PaddingValues(bottom = 40.dp)) {
        item { PageHead(marginTop = "", marginBottom = null, title = w.helpTitle, line = w.helpLine) }
        items(rows, key = { it.second.id }) { (explain, l) ->
            Column {
                LineOnPage(l, today, today, book, if (l.day == today.minusDays(2).toString()) Seen.Tail else Seen.Page, false, {}, {})
                Row(Modifier.fillMaxWidth().padding(end = Grid.right, bottom = 10.dp)) {
                    Spacer(Modifier.width(Grid.text))
                    Text(withPlainSigns(explain), style = Voice.reply.copy(color = c.graphite))
                }
            }
        }
        item { SectionHead(w.helpWriting) }
        item { MarginSays(w.helpWritingText) }
        item { SectionHead(w.helpEvening) }
        item { MarginSays(w.helpEveningText) }
    }
}

/** Settings as a page: the name of each on the right, its value in Margin's voice; a tap changes it. */
@Composable
fun SettingsPage(vm: BookVm, onExamples: () -> Unit) {
    val c = P.c
    val w = P.w
    val prefs = remember { ru.anjeib.margin.data.Prefs.get(vm.getApplication()) }
    var s by remember { mutableStateOf(prefs.settings.value) }
    fun set(f: (ru.anjeib.margin.data.Settings) -> ru.anjeib.margin.data.Settings) { prefs.update(f); s = prefs.settings.value }
    val evening = remember { mutableStateOf(vm.eveningHour()) }
    LazyColumn(Modifier.fillMaxSize().rule(c.rule), contentPadding = PaddingValues(bottom = 40.dp)) {
        item { PageHead(marginTop = "", marginBottom = null, title = w.settingsTitle, line = null) }
        item {
            SettingRow(w.theme, when (s.theme) { ru.anjeib.margin.data.ThemeMode.Light -> w.themeLight; ru.anjeib.margin.data.ThemeMode.Dark -> w.themeDark; else -> w.themeAuto }) {
                set { it.copy(theme = when (it.theme) { ru.anjeib.margin.data.ThemeMode.Auto -> ru.anjeib.margin.data.ThemeMode.Light; ru.anjeib.margin.data.ThemeMode.Light -> ru.anjeib.margin.data.ThemeMode.Dark; else -> ru.anjeib.margin.data.ThemeMode.Auto }) }
            }
        }
        item {
            SettingRow(w.language, when (s.lang) { ru.anjeib.margin.data.LangMode.Ru -> "Русский"; ru.anjeib.margin.data.LangMode.En -> "English"; else -> w.themeAuto }) {
                set { it.copy(lang = when (it.lang) { ru.anjeib.margin.data.LangMode.Auto -> ru.anjeib.margin.data.LangMode.Ru; ru.anjeib.margin.data.LangMode.Ru -> ru.anjeib.margin.data.LangMode.En; else -> ru.anjeib.margin.data.LangMode.Auto }) }
            }
        }
        item {
            SettingRow(w.eveningSetting, "%02d:00".format(evening.value)) {
                val next = if (evening.value >= 23) 18 else evening.value + 1
                vm.setEveningHour(next); evening.value = next
            }
        }
        item {
            // The phone's calendar: its meetings stand on the pages as ○ lines (read only).
            val ctx = androidx.compose.ui.platform.LocalContext.current
            val ask = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { ok ->
                vm.setCalendar(ok); s = prefs.settings.value
            }
            SettingRow(w.calendarSetting, if (s.calendar && ru.anjeib.margin.data.PhoneCalendar.allowed(ctx)) w.calendarOn else w.calendarOff) {
                if (s.calendar) { vm.setCalendar(false); s = prefs.settings.value }
                else if (ru.anjeib.margin.data.PhoneCalendar.allowed(ctx)) { vm.setCalendar(true); s = prefs.settings.value }
                else ask.launch(android.Manifest.permission.READ_CALENDAR)
            }
        }
        item {
            val b by vm.book.collectAsState()
            SettingRow(w.examples, if (b.hasSamples()) w.examplesRemove else w.examplesBack, onExamples)
        }
        // Reminders need the phone's permission: asked here again if it was refused.
        item {
            val ctx = androidx.compose.ui.platform.LocalContext.current
            fun granted() = android.os.Build.VERSION.SDK_INT < 33 ||
                androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
            var on by remember { mutableStateOf(granted()) }
            val ask = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { ok ->
                on = ok
                if (!ok) runCatching {
                    ctx.startActivity(android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, ctx.packageName).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
            SettingRow(w.bellSetting, if (on) w.bellOn else w.bellOff) {
                if (!on && android.os.Build.VERSION.SDK_INT >= 33) ask.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                else runCatching {
                    ctx.startActivity(android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, ctx.packageName).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
        }
        // Voice: written straight away, or into the field first; and words without a network (a model on the phone).
        item { SettingRow(w.voiceSetting, if (s.voiceSendNow) w.voiceNow else w.voiceField) { set { it.copy(voiceSendNow = !it.voiceSendNow) } } }
        item {
            val ctx = androidx.compose.ui.platform.LocalContext.current
            val whisper = ru.anjeib.margin.voice.LocalWhisper
            val st by whisper.state.collectAsState()
            androidx.compose.runtime.LaunchedEffect(Unit) { whisper.refresh(ctx) }
            if (whisper.supported) SettingRow(w.offlineVoice, when (val x = st) {
                is ru.anjeib.margin.voice.LocalWhisper.State.Ready -> w.offlineReady
                is ru.anjeib.margin.voice.LocalWhisper.State.Downloading -> "${(x.progress * 100).toInt()} %"
                is ru.anjeib.margin.voice.LocalWhisper.State.Failed -> w.offlineRetry
                else -> w.offlineGet(ru.anjeib.margin.voice.LocalWhisper.Model.Base.mb)
            }) {
                if (st is ru.anjeib.margin.voice.LocalWhisper.State.Missing || st is ru.anjeib.margin.voice.LocalWhisper.State.Failed)
                    vm.downloadWhisper()
            }
        }
        // The lock and the copy: who can open the notebook, and where it is kept besides the phone.
        item {
            val ctx = androidx.compose.ui.platform.LocalContext.current
            if (ru.anjeib.margin.Lock.available(ctx)) SettingRow(w.lockSetting, if (s.lock) w.on else w.off) { set { it.copy(lock = !it.lock) } }
        }
        item { SettingRow(w.backupSetting, if (s.googleBackup) w.on else w.off) { set { it.copy(googleBackup = !it.googleBackup) } } }
        // The invite code lets this phone through Margin's door to the AI; the shared one comes with the app.
        item {
            var asking by remember { mutableStateOf(false) }
            val own = s.invite.isNotBlank() && s.invite != ru.anjeib.margin.BuildConfig.ALPHA_CODE
            SettingRow(w.inviteSetting, if (own) w.inviteOwn else if (s.invite.isNotBlank()) w.inviteShared else w.inviteNone) { asking = true }
            if (asking) InviteDialog(if (own) s.invite else "", onDismiss = { asking = false }) { code ->
                set { it.copy(invite = code.trim().ifBlank { ru.anjeib.margin.BuildConfig.ALPHA_CODE }) }; asking = false
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(top = 24.dp, end = Grid.right)) {
                Spacer(Modifier.width(Grid.text))
                Text(w.settingsNote, style = Voice.reply.copy(color = c.graphite))
            }
        }
    }
}

@Composable
private fun InviteDialog(value: String, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val c = P.c
    val w = P.w
    var v by remember { mutableStateOf(value) }
    androidx.compose.material3.AlertDialog(onDismissRequest = onDismiss, containerColor = c.sheet,
        title = { Text(w.inviteSetting, style = Voice.section.copy(color = c.ink)) },
        text = {
            Column {
                Text(w.inviteHint, style = Voice.reply.copy(color = c.graphite))
                Box(Modifier.padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(c.paper).border(1.dp, c.hairline, RoundedCornerShape(10.dp)).padding(12.dp)) {
                    BasicTextField(v, { v = it }, textStyle = Voice.words.copy(color = c.ink), cursorBrush = SolidColor(c.link), singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = { androidx.compose.material3.TextButton({ onPick(v) }) { Text(w.ok, color = c.link) } },
        dismissButton = { androidx.compose.material3.TextButton(onDismiss) { Text(w.cancel, color = c.graphite) } })
}

@Composable
private fun SettingRow(name: String, value: String, onClick: () -> Unit) {
    val c = P.c
    Row(Modifier.fillMaxWidth().heightIn(min = Grid.line).clickable(onClick = onClick).padding(end = Grid.right), verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.width(Grid.text))
        Text(name, style = Voice.words.copy(color = c.ink), modifier = Modifier.weight(1f))
        Text(value, style = Voice.reply.copy(color = c.link))
    }
}

/** «Отзыв»: a letter to the developer with the crash log and the misread phrases attached; the notebook itself is not sent. */
fun sendFeedback(ctx: android.content.Context, w: Words) {
    val fb = ru.anjeib.margin.data.Feedback
    val version = ru.anjeib.margin.BuildConfig.VERSION_NAME + " (" + ru.anjeib.margin.BuildConfig.VERSION_CODE + ")"
    val device = android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL + " · Android " + android.os.Build.VERSION.RELEASE
    val files = fb.attachments(ctx)
    val uris = ArrayList(files.map { androidx.core.content.FileProvider.getUriForFile(ctx, ctx.packageName + ".files", it) })
    val i = android.content.Intent(if (uris.size > 1) android.content.Intent.ACTION_SEND_MULTIPLE else android.content.Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(android.content.Intent.EXTRA_EMAIL, arrayOf(fb.EMAIL))
        .putExtra(android.content.Intent.EXTRA_SUBJECT, w.feedbackSubject(version))
        .putExtra(android.content.Intent.EXTRA_TEXT, w.feedbackBody(version, device))
        .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        .apply {
            if (uris.size > 1) putParcelableArrayListExtra(android.content.Intent.EXTRA_STREAM, uris)
            else uris.firstOrNull()?.let { putExtra(android.content.Intent.EXTRA_STREAM, it) }
        }
    val mail = android.content.Intent(i).apply { selector = android.content.Intent(android.content.Intent.ACTION_SENDTO, android.net.Uri.parse("mailto:")) }
    runCatching { ctx.startActivity(mail) }
        .recoverCatching { ctx.startActivity(android.content.Intent.createChooser(i, w.feedback).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/** Russian letters as they are usually written in file names: «протокол» → «protokol», «смета» → «smeta». */
fun translit(s: String): String {
    val m = mapOf('а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d", 'е' to "e", 'ё' to "e", 'ж' to "zh", 'з' to "z", 'и' to "i",
        'й' to "y", 'к' to "k", 'л' to "l", 'м' to "m", 'н' to "n", 'о' to "o", 'п' to "p", 'р' to "r", 'с' to "s", 'т' to "t", 'у' to "u",
        'ф' to "f", 'х' to "h", 'ц' to "ts", 'ч' to "ch", 'ш' to "sh", 'щ' to "sch", 'ъ' to "", 'ы' to "y", 'ь' to "", 'э' to "e", 'ю' to "yu", 'я' to "ya")
    return s.lowercase().map { m[it] ?: it.toString() }.joinToString("")
}
