package ru.anjeib.margin.ui

import androidx.compose.foundation.layout.heightIn

import androidx.compose.foundation.verticalScroll

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Search

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import kotlinx.coroutines.launch
import ru.anjeib.margin.data.questionRow
import ru.anjeib.margin.data.shortLabel

/**
 * One page on first launch (owner, 16 окт: «тур до одного экрана — приветствие и разрешения, остальное расскажет курс»): a word
 * of welcome, where to begin — «Курс освоения Margin» on the first page — and the permissions the phone asks for itself
 * (notifications with the morning summary's hour, the calendar). Everything else is learnt on living matters, not described.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun Onboarding(vm: MainVm, onDone: () -> Unit) {
    val c = M.c
    val s = M.s
    val ctx = androidx.compose.ui.platform.LocalContext.current
    fun has(p: String) = androidx.core.content.ContextCompat.checkSelfPermission(ctx, p) == android.content.pm.PackageManager.PERMISSION_GRANTED
    // A pressed button shows what happened — otherwise nobody knows whether it worked.
    var notifOk by remember { mutableStateOf(Build.VERSION.SDK_INT >= 33 && has(Manifest.permission.POST_NOTIFICATIONS)) }
    var calOk by remember { mutableStateOf(has(Manifest.permission.READ_CALENDAR)) }
    val notif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> notifOk = ok }
    val cal = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        calOk = ok; vm.updateSettings { it.copy(calendar = ok) }; vm.refreshCalendar()
    }
    Column(Modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp)) {
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(top = 72.dp, bottom = 16.dp)) {
            Text("Margin", style = Type.label.copy(color = c.accentText, fontSize = 13.sp))
            Spacer(Modifier.height(12.dp))
            Text(s.welcomeTitle, style = Type.display.copy(color = c.ink, fontSize = 36.sp, lineHeight = 42.sp))
            Spacer(Modifier.height(12.dp))
            Text(s.welcomeText, style = Type.body.copy(color = c.graphite, fontSize = 17.sp, lineHeight = 25.sp))
            Spacer(Modifier.height(28.dp))
            Text(s.ob4Text, style = Type.small.copy(color = c.graphite, fontSize = 14.sp, lineHeight = 20.sp))
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (Build.VERSION.SDK_INT >= 33) Pill(if (notifOk) "✓ " + s.notifications else s.notifications, accent = !notifOk,
                    onClick = if (notifOk) null else ({ notif.launch(Manifest.permission.POST_NOTIFICATIONS) }))
                Pill(if (calOk) "✓ " + s.calendar else s.calendar, accent = !calOk,
                    onClick = if (calOk) null else ({ cal.launch(Manifest.permission.READ_CALENDAR) }))
            }
            // The morning summary is chosen here, with the notifications it needs.
            Spacer(Modifier.height(22.dp))
            Text(s.obBrief, style = Type.small.copy(color = c.graphite, fontSize = 14.sp))
            Spacer(Modifier.height(8.dp))
            val st by vm.settings.collectAsState()
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // The time already set (8:30 by default) is shown chosen, even when it is not one of the offered hours.
                val offered = s.setupBriefOptions.dropLast(1).map { "%02d:00".format(it.substringBefore(':').toInt()) }
                if (st.morningBrief && st.morningTime !in offered) Pill("✓ " + st.morningTime.removePrefix("0"), accent = true)
                s.setupBriefOptions.forEachIndexed { i, o ->
                    val off = i == s.setupBriefOptions.lastIndex
                    val time = if (off) null else "%02d:00".format(o.substringBefore(':').toInt())
                    val on = if (off) !st.morningBrief else st.morningBrief && st.morningTime == time
                    Pill(if (on) "✓ $o" else o, accent = on, onClick = {
                        vm.updateSettings { it.copy(morningBrief = !off, morningTime = time ?: it.morningTime) }
                    })
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(bottom = 24.dp), horizontalArrangement = Arrangement.End) {
            Pill(s.begin, filled = true, onClick = onDone)
            Spacer(Modifier.width(2.dp))
        }
    }
}
