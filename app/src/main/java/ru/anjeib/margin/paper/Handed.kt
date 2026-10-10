package ru.anjeib.margin.paper

import ru.anjeib.margin.book.addFiles

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/*
 * Words handed in from another app («Поделиться → Margin»): the notebook's own field on a sheet over that app, the words
 * already in it and Margin's notes beside them. After «записать» — the lines as they now stand on their pages.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HandedSheet(vm: BookVm, from: String?, onDone: () -> Unit, onOpen: (String?) -> Unit) {
    val c = P.c
    val w = P.w
    val book by vm.book.collectAsState()
    val today by vm.today.collectAsState()
    val written by vm.written.collectAsState()
    // A big file can still be copying in when the words are already written: it goes to the first line they became.
    val late by vm.handedFiles.collectAsState()
    LaunchedEffect(late, written) {
        val id = written.firstOrNull()
        if (id != null && late.isNotEmpty()) { vm.edit { it.addFiles(id, late) }; vm.handedFilesTaken() }
    }
    ModalBottomSheet(onDismissRequest = onDone, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.lifted, scrimColor = c.scrim,
        dragHandle = { Box(Modifier.padding(top = 10.dp, bottom = 6.dp).size(36.dp, 4.dp).clip(CircleShape).background(c.hairline)) }) {
        androidx.compose.runtime.CompositionLocalProvider(LocalGround provides c.lifted) {
        Column(Modifier.fillMaxWidth().imePadding().navigationBarsPadding().rule(c.rule)) {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 10.dp, end = Grid.right), verticalAlignment = Alignment.Bottom) {
                Box(Modifier.width(Grid.margin - Grid.noteGap).padding(start = 4.dp, bottom = 4.dp), contentAlignment = Alignment.CenterEnd) {
                    from?.let { NoteText(Note(w.handedFrom(it), Tone.Quiet)) }
                }
                Spacer(Modifier.width(Grid.text - Grid.margin + Grid.noteGap))
                Text(w.handedTitle, style = Voice.section.copy(color = c.ink))
            }
            if (written.isEmpty()) {
                Composer(vm, BookVm.Target(today), book, today, onOpen = { onOpen(it) }, onPerson = { onOpen(null) }, onThread = { onOpen(null) },
                    onDay = { onOpen(null) }, onSearch = { onOpen(null) })
            } else {
                // What became of the words: the lines as they stand on their pages — the day on the margin if it is not today.
                written.mapNotNull { book.line(it) }.forEach { l -> LineOnPage(l, today, today, book, Seen.Page, true, vm::toggle, { onOpen(it) }) }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp, end = Grid.right), verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.width(Grid.text))
                    Text(w.openInMargin, style = Voice.reply.copy(color = c.link),
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { onOpen(written.firstOrNull()) }.padding(vertical = 12.dp, horizontal = 2.dp))
                    Spacer(Modifier.weight(1f))
                    Text(w.ok, style = Voice.reply.copy(color = c.link),
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onDone).padding(vertical = 12.dp, horizontal = 8.dp))
                }
            }
        }
        }
    }
}
