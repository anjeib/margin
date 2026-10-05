package ru.anjeib.margin.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Four quiet pages on first launch; skippable at any point. The last one asks for permissions. */
@Composable
fun Onboarding(vm: MainVm, onDone: () -> Unit) {
    val c = M.c
    val s = M.s
    var page by rememberSaveable { mutableIntStateOf(0) }
    val pages = listOf(s.ob1Title to s.ob1Text, s.ob2Title to s.ob2Text, s.ob3Title to s.ob3Text, s.ob4Title to s.ob4Text)
    val ctx = androidx.compose.ui.platform.LocalContext.current
    fun has(p: String) = androidx.core.content.ContextCompat.checkSelfPermission(ctx, p) == android.content.pm.PackageManager.PERMISSION_GRANTED
    // A pressed button shows what happened — otherwise nobody knows whether it worked.
    var notifOk by remember { mutableStateOf(Build.VERSION.SDK_INT >= 33 && has(Manifest.permission.POST_NOTIFICATIONS)) }
    var calOk by remember { mutableStateOf(has(Manifest.permission.READ_CALENDAR)) }
    val notif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> notifOk = ok }
    val cal = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        calOk = ok; vm.updateSettings { it.copy(calendar = ok) }; vm.refreshCalendar()
    }

    Column(Modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding().padding(horizontal = 28.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
            if (page < pages.lastIndex) QuietLink(s.skip, onDone)
        }
        Spacer(Modifier.weight(1f))
        AnimatedContent(page, label = "ob") { p ->
            Column {
                Text("Margin", style = Type.label.copy(color = c.accent, fontSize = 13.sp))
                Spacer(Modifier.height(14.dp))
                Text(pages[p].first, style = Type.display.copy(color = c.ink, fontSize = 38.sp, lineHeight = 44.sp))
                Spacer(Modifier.height(16.dp))
                Text(pages[p].second, style = Type.body.copy(color = c.graphite, fontSize = 17.sp, lineHeight = 26.sp))
                if (p == pages.lastIndex) {
                    Spacer(Modifier.height(24.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (Build.VERSION.SDK_INT >= 33) Pill(if (notifOk) "✓ " + s.notifications + " · " + s.granted else s.notifications, accent = !notifOk,
                            onClick = if (notifOk) null else ({ notif.launch(Manifest.permission.POST_NOTIFICATIONS) }))
                        Pill(if (calOk) "✓ " + s.calendar + " · " + s.granted else s.calendar, accent = !calOk,
                            onClick = if (calOk) null else ({ cal.launch(Manifest.permission.READ_CALENDAR) }))
                    }
                }
            }
        }
        Spacer(Modifier.weight(1.3f))
        Row(Modifier.fillMaxWidth().padding(bottom = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                pages.indices.forEach { i ->
                    Box(Modifier.size(if (i == page) 18.dp else 6.dp, 6.dp).clip(CircleShape).background(if (i == page) c.accent else c.hairline))
                }
            }
            Pill(if (page < pages.lastIndex) s.next else s.begin, filled = true, onClick = { if (page < pages.lastIndex) page++ else onDone() })
            Spacer(Modifier.width(2.dp))
        }
    }
}
