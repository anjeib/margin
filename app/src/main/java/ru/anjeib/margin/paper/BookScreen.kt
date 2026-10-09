package ru.anjeib.margin.paper

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ru.anjeib.margin.ui.TopicGlyph
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/*
 * The notebook: pages of days turned by a swipe (DESIGN.md §11), the field under them. Today is the middle page of a
 * long book; «к сегодня ›» brings it back from anywhere.
 */

private const val MIDDLE = 5000

@Composable
fun BookScreen(vm: BookVm) {
    val c = P.c
    val w = P.w
    val book by vm.book.collectAsState()
    val today by vm.today.collectAsState()
    val fresh by vm.fresh.collectAsState()
    val pager = rememberPagerState(initialPage = MIDDLE) { MIDDLE * 2 }
    val scope = rememberCoroutineScope()
    fun dateOf(i: Int): LocalDate = today.plusDays((i - MIDDLE).toLong())
    val current = dateOf(pager.currentPage)
    var open by rememberSaveable { mutableStateOf<String?>(null) }

    Box(Modifier.fillMaxSize().background(c.paper)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
            // The top: the index on the margin, the way home when away, search at the right.
            Row(Modifier.fillMaxWidth().height(52.dp).rule(c.rule).padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(Grid.margin - Grid.noteGap), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(44.dp).clip(CircleShape).clickable { }.semantics { contentDescription = w.a11yIndex }, contentAlignment = Alignment.Center) {
                        TopicGlyph(c.ink, 20.dp)
                    }
                }
                Spacer(Modifier.width(Grid.text - Grid.margin + Grid.noteGap))
                Box(Modifier.weight(1f)) {
                    if (current != today) Text(w.toToday, style = Voice.reply.copy(color = c.link),
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { scope.launch { pager.animateScrollToPage(MIDDLE) } }.padding(vertical = 12.dp))
                }
                Box(Modifier.size(44.dp).clip(CircleShape).clickable { }.semantics { contentDescription = w.a11ySearch }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Search, null, tint = c.ink)
                }
            }
            HorizontalPager(pager, Modifier.weight(1f), beyondViewportPageCount = 1, key = { it }) { i ->
                val d = dateOf(i)
                DayPage(d, book, today, fresh, bottom = 24.dp,
                    onMark = vm::toggle, onOpen = { open = it }, onMonth = { }, onTurn = { })
            }
            Composer(vm, current, book, today, Modifier.navigationBarsPadding())
        }
        open?.let { id ->
            val baseId = ru.anjeib.margin.book.splitOccurrence(id)?.first ?: id
            LineSheet(baseId, current, vm, book, today, onClose = { open = null })
        }
    }

    // A new day while the app was open: today moves, the pager follows.
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(60_000)
            if (LocalDate.now() != vm.today.value) vm.refreshToday()
        }
    }
}

/** How far a date is from today in pages. */
fun pageOf(date: LocalDate, today: LocalDate): Int = MIDDLE + ChronoUnit.DAYS.between(today, date).toInt()
