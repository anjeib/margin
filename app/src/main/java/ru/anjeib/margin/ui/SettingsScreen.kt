package ru.anjeib.margin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.anjeib.margin.data.LangMode
import ru.anjeib.margin.data.Settings
import ru.anjeib.margin.data.ThemeMode
import java.time.LocalTime
import java.time.YearMonth

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun SettingsScreen(settings: Settings, vm: MainVm, contentPadding: PaddingValues) {
    val c = M.c
    val s = M.s
    val keyStatus by vm.keyStatus.collectAsState()
    var keyDraft by remember { mutableStateOf<String?>(null) }
    var picker by remember { mutableStateOf<String?>(null) } // "main" | "fast"

    LaunchedEffect(Unit) { if (settings.apiKey.isNotBlank()) vm.checkKey() }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val whisper by ru.anjeib.margin.voice.LocalWhisper.state.collectAsState()
    val calPerm = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { ok ->
        vm.updateSettings { it.copy(calendar = ok) }; vm.refreshCalendar()
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(contentPadding)) {
        Text(s.settings, style = Type.display.copy(color = c.ink), modifier = Modifier.padding(top = 18.dp, bottom = 18.dp, start = 4.dp))

        Group(s.voiceSection) {
            // One switch for people: speech on the phone (Whisper) or in the cloud. Models live in the developer section.
            if (ru.anjeib.margin.voice.LocalWhisper.supported) {
                ToggleRow(s.onDevice, s.onDeviceHint, settings.whisperAlways) { v ->
                    if (v && whisper !is ru.anjeib.margin.voice.LocalWhisper.State.Ready && whisper !is ru.anjeib.margin.voice.LocalWhisper.State.Downloading) vm.downloadWhisper(ru.anjeib.margin.voice.LocalWhisper.Model.Small)
                    vm.updateSettings { it.copy(whisperAlways = v) }
                }
                val st = whisper
                if (settings.whisperAlways || st is ru.anjeib.margin.voice.LocalWhisper.State.Downloading) {
                    Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
                        Text(
                            when (st) {
                                is ru.anjeib.margin.voice.LocalWhisper.State.Missing -> s.whisperMissing
                                is ru.anjeib.margin.voice.LocalWhisper.State.Downloading -> s.whisperDownloading((st.progress * 100).toInt())
                                is ru.anjeib.margin.voice.LocalWhisper.State.Ready -> s.whisperReady(if (st.model == ru.anjeib.margin.voice.LocalWhisper.Model.Small) s.whisperSmall else s.whisperBase)
                                is ru.anjeib.margin.voice.LocalWhisper.State.Failed -> st.message
                            },
                            style = Type.small.copy(color = c.graphite, fontSize = 12.5.sp),
                        )
                        if (st is ru.anjeib.margin.voice.LocalWhisper.State.Downloading) {
                            Spacer(Modifier.height(8.dp))
                            androidx.compose.material3.LinearProgressIndicator(progress = { st.progress }, color = c.accent, trackColor = c.hairline,
                                modifier = Modifier.fillMaxWidth().height(3.dp), drawStopIndicator = {}, gapSize = 0.dp)
                        }
                    }
                    when (st) {
                        is ru.anjeib.margin.voice.LocalWhisper.State.Downloading -> { Divider(); ValueRow(s.cancelDownload, null, "", chevron = false) { ru.anjeib.margin.voice.LocalWhisper.cancelDownload() } }
                        is ru.anjeib.margin.voice.LocalWhisper.State.Ready -> { Divider(); ValueRow(s.deleteModel, null, "", chevron = false) { ru.anjeib.margin.voice.LocalWhisper.delete(ctx); vm.updateSettings { it.copy(whisperAlways = false) } } }
                        is ru.anjeib.margin.voice.LocalWhisper.State.Failed, is ru.anjeib.margin.voice.LocalWhisper.State.Missing -> { Divider(); ValueRow(s.whisperSmall, null, "↓") { vm.downloadWhisper(ru.anjeib.margin.voice.LocalWhisper.Model.Small) } }
                    }
                }
                Divider()
            }
            ToggleRow(s.voiceSendNow, null, settings.voiceSendNow) { v -> vm.updateSettings { it.copy(voiceSendNow = v) } }
        }

        Group(s.calendarSection) {
            ToggleRow(s.calendarRead, s.calendarHint, settings.calendar && ru.anjeib.margin.data.PhoneCalendar.allowed(ctx)) { v ->
                if (v && !ru.anjeib.margin.data.PhoneCalendar.allowed(ctx)) calPerm.launch(android.Manifest.permission.READ_CALENDAR)
                else { vm.updateSettings { it.copy(calendar = v) }; vm.refreshCalendar() }
            }
        }

        Group(s.appearance) {
            Segmented(
                listOf(s.themeLight, s.themeDark, s.themeAuto),
                listOf(ThemeMode.Light, ThemeMode.Dark, ThemeMode.Auto).indexOf(settings.theme),
            ) { i -> vm.updateSettings { it.copy(theme = listOf(ThemeMode.Light, ThemeMode.Dark, ThemeMode.Auto)[i]) } }
            Divider()
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(s.language, style = Type.body.copy(color = c.ink))
                Spacer(Modifier.height(10.dp))
            }
            Segmented(
                listOf("Русский", "English", s.langAuto),
                listOf(LangMode.Ru, LangMode.En, LangMode.Auto).indexOf(settings.lang),
            ) { i -> vm.updateSettings { it.copy(lang = listOf(LangMode.Ru, LangMode.En, LangMode.Auto)[i]) } }
        }

        Group(s.backupSection) {
            // First the main switch and what it means, then the optional file copy.
            ToggleRow(s.googleBackup, if (settings.googleBackup) s.googleBackupHint else s.googleBackupOff, settings.googleBackup) { on ->
                vm.updateSettings { it.copy(googleBackup = on) }
                runCatching { android.app.backup.BackupManager(ctx).dataChanged() }
            }
        }
        if (settings.devMode) {
        Group(s.devSection) {
            val ya = settings.provider == "yandex"
            Segmented(listOf("Яндекс", "OpenRouter"), if (ya) 0 else 1) { i -> vm.updateSettings { it.copy(provider = if (i == 0) "yandex" else "openrouter") }; vm.checkKey() }
            Divider()
            val key = if (ya) settings.yaKey else settings.apiKey
            Row(Modifier.fillMaxWidth().clickable { keyDraft = key }.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(s.apiKey, style = Type.body.copy(color = c.ink), modifier = Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    Text(maskKey(key).ifEmpty { "—" }, style = Type.small.copy(color = c.graphite))
                    val ok = keyStatus?.let { it.startsWith(s.connected) } == true
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(6.dp).clip(CircleShape).background(if (ok) c.ok else c.faint))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (key.isBlank()) s.notConnected else keyStatus ?: s.check,
                            style = Type.small.copy(color = if (ok) c.ok else c.graphite, fontSize = 12.sp),
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Divider()
            if (ya) {
                // Which model parses what the user writes; briefs and reviews always use the stronger one.
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(s.parsingMode, style = Type.body.copy(color = c.ink))
                    Text(when (settings.yaMode) { "economy" -> s.modeEconomyHint; "precise" -> s.modePreciseHint; else -> s.modeHybridHint },
                        style = Type.small.copy(color = c.graphite, fontSize = 12.5.sp))
                }
                Segmented(listOf(s.modeHybrid, s.modeEconomy, s.modePrecise), listOf("hybrid", "economy", "precise").indexOf(settings.yaMode).coerceAtLeast(0)) { i ->
                    vm.updateSettings { it.copy(yaMode = listOf("hybrid", "economy", "precise")[i]) }
                }
            } else {
                ValueRow(s.mainModel, s.mainModelHint, settings.mainModel) { picker = "main"; vm.loadModels() }
                Divider()
                ValueRow(s.fastModel, s.fastModelHint, settings.fastModel) { picker = "fast"; vm.loadModels() }
            }
            Divider()
            val spent = if (settings.spentMonth == YearMonth.now().toString()) settings.spentRub else 0.0
            ValueRow(s.spentMonth, null, (if (ya) "" else "≈ ") + "%.2f ₽".format(java.util.Locale.US, spent), chevron = false) {}
                    Divider()
            ToggleRow(s.voiceAi, s.voiceAiHint, settings.voiceAi) { v -> vm.updateSettings { it.copy(voiceAi = v) } }
            Divider()
            if (settings.provider == "yandex") ValueRow(s.voiceModel, null, "Yandex SpeechKit", chevron = false) {}
            else ValueRow(s.voiceModel, null, settings.voiceModel) { picker = "voice"; vm.loadModels() }
            if (ru.anjeib.margin.voice.LocalWhisper.supported) {
                Divider()
                val st = whisper
                val other = if ((st as? ru.anjeib.margin.voice.LocalWhisper.State.Ready)?.model == ru.anjeib.margin.voice.LocalWhisper.Model.Small) ru.anjeib.margin.voice.LocalWhisper.Model.Base else ru.anjeib.margin.voice.LocalWhisper.Model.Small
                ValueRow(s.onDeviceModel, null, if (other == ru.anjeib.margin.voice.LocalWhisper.Model.Small) s.whisperSmall else s.whisperBase) { vm.downloadWhisper(other) }
            }
        }
        }
        // Long-press the version to open/close the developer section.
        Text(
            s.version(ru.anjeib.margin.BuildConfig.VERSION_NAME),
            style = Type.small.copy(color = c.faint, fontSize = 12.sp),
            modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp)
                .combinedClickable(onClick = {}, onLongClick = {
                    vm.updateSettings { it.copy(devMode = !it.devMode) }
                    if (!settings.devMode) android.widget.Toast.makeText(ctx, s.devUnlocked, android.widget.Toast.LENGTH_SHORT).show()
                }),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
    }

    keyDraft?.let { draft ->
        var v by remember { mutableStateOf(draft) }
        AlertDialog(
            onDismissRequest = { keyDraft = null },
            containerColor = c.card,
            title = { Text(s.apiKey, style = Type.cardTitle.copy(color = c.ink)) },
            text = {
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.chip).padding(14.dp)) {
                    if (v.isEmpty()) Text(s.apiKeyHint, style = Type.body.copy(color = c.graphite))
                    BasicTextField(v, { v = it.trim() }, singleLine = true, textStyle = Type.body.copy(color = c.ink),
                        visualTransformation = PasswordVisualTransformation(), cursorBrush = SolidColor(c.accent),
                        modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton({ vm.updateSettings { if (it.provider == "yandex") it.copy(yaKey = v) else it.copy(apiKey = v) }; keyDraft = null; vm.checkKey() }) { Text(s.save, color = c.accent) }
            },
            dismissButton = { TextButton({ keyDraft = null }) { Text(s.cancel, color = c.graphite) } },
        )
    }

    picker?.let { which ->
        ModelPicker(
            current = when (which) { "main" -> settings.mainModel; "voice" -> settings.voiceModel; else -> settings.fastModel },
            vm = vm,
            audioOnly = which == "voice",
            onPick = { id ->
                vm.updateSettings { when (which) { "main" -> it.copy(mainModel = id); "voice" -> it.copy(voiceModel = id); else -> it.copy(fastModel = id) } }
                picker = null
            },
            onDismiss = { picker = null },
        )
    }
}

private fun maskKey(k: String) = if (k.length < 12) k else k.take(6) + "••••••" + k.takeLast(4)

@Composable
private fun Group(label: String, content: @Composable () -> Unit) {
    val c = M.c
    SectionLabel(label, Modifier.padding(start = 8.dp, bottom = 8.dp, top = 6.dp))
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.card)) { content() }
    Spacer(Modifier.height(22.dp))
}

@Composable
private fun Divider() = Hairline(Modifier.padding(start = 16.dp))

@Composable
private fun ValueRow(label: String, hint: String?, value: String, chevron: Boolean = true, onClick: () -> Unit) {
    val c = M.c
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = Type.body.copy(color = c.ink))
            hint?.let { Text(it, style = Type.small.copy(color = c.graphite, fontSize = 12.5.sp)) }
        }
        Text(value, style = Type.small.copy(color = c.graphite, fontSize = 14.sp), maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 12.dp).fillMaxWidth(0.55f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
        if (chevron) Icon(Icons.Rounded.KeyboardArrowRight, null, tint = c.faint, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun ToggleRow(label: String, hint: String?, value: Boolean, onLabel: (() -> Unit)? = null, onChange: (Boolean) -> Unit) {
    val c = M.c
    Row(Modifier.fillMaxWidth().clickable { if (onLabel != null) onLabel() else onChange(!value) }.padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = Type.body.copy(color = c.ink))
            hint?.let { Text(it, style = Type.small.copy(color = c.graphite, fontSize = 12.5.sp)) }
        }
        Switch(
            checked = value, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = c.accent, checkedThumbColor = c.card, checkedBorderColor = c.accent,
                uncheckedTrackColor = c.chip, uncheckedThumbColor = c.card, uncheckedBorderColor = c.hairline,
            ),
        )
    }
}

@Composable
private fun Segmented(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val c = M.c
    Row(Modifier.fillMaxWidth().padding(12.dp).clip(RoundedCornerShape(12.dp)).background(c.chip).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        labels.forEachIndexed { i, l ->
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(if (i == selected) c.accentSoft else c.chip)
                    .clickable { onSelect(i) }.padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) { Text(l, style = Type.small.copy(color = if (i == selected) c.accent else c.graphite, fontSize = 14.sp)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelPicker(current: String, vm: MainVm, audioOnly: Boolean = false, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val c = M.c
    val s = M.s
    val models by vm.models.collectAsState()
    var q by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.bg, scrimColor = c.scrim) {
        Column(Modifier.fillMaxHeight(0.85f).padding(horizontal = 18.dp)) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.chip).padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Search, null, tint = c.graphite, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) {
                    if (q.isEmpty()) Text(s.searchModels, style = Type.body.copy(color = c.graphite))
                    BasicTextField(q, { q = it }, singleLine = true, textStyle = Type.body.copy(color = c.ink),
                        cursorBrush = SolidColor(c.accent), modifier = Modifier.fillMaxWidth())
                }
            }
            Spacer(Modifier.height(10.dp))
            if (models.isEmpty()) Box(Modifier.fillMaxWidth().padding(30.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = c.accent, strokeWidth = 1.5.dp, modifier = Modifier.size(20.dp))
            }
            val words = q.lowercase().split(' ').filter { it.isNotBlank() }
            val list = models.filter { m -> (!audioOnly || m.audio) && words.all { it in (m.id + " " + m.name).lowercase() } }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items(list, key = { it.id }) { m ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .background(if (m.id == current) c.accentSoft else c.bg)
                            .clickable { onPick(m.id) }.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(m.name, style = Type.body.copy(color = c.ink, fontSize = 15.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(m.id, style = Type.small.copy(color = c.graphite, fontSize = 12.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text(
                            "$" + fmt(m.promptPerM) + " / $" + fmt(m.completionPerM),
                            style = Type.small.copy(color = c.graphite, fontSize = 12.sp),
                        )
                    }
                }
            }
        }
    }
}

private fun fmt(v: Double) = if (v < 0.1) "%.3f".format(java.util.Locale.US, v) else if (v < 10) "%.2f".format(java.util.Locale.US, v) else "%.0f".format(java.util.Locale.US, v)
