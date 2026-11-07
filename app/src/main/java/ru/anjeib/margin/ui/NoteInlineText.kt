package ru.anjeib.margin.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.*
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp

/** A tap places the caret where the finger was; blur and card dismissal commit exactly this field. */
@Composable
fun NoteInlineText(id: String, value: String, style: TextStyle, onSave: (String) -> Boolean,
                   onLong: (String) -> Unit, onBounds: (Rect?) -> Unit, modifier: Modifier = Modifier) {
    var editing by remember(id) { mutableStateOf(false) }
    var field by remember(id) { mutableStateOf(TextFieldValue(value)) }
    var wasFocused by remember(id) { mutableStateOf(false) }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    var bounds by remember { mutableStateOf<Rect?>(null) }
    val focus = remember { FocusRequester() }
    val save by rememberUpdatedState(onSave)
    val long by rememberUpdatedState(onLong)
    val baseline by rememberUpdatedState(value)
    val latest by rememberUpdatedState(field)
    val active by rememberUpdatedState(editing)
    fun commit(): Boolean = if (field.text == baseline) true else save(field.text)
    LaunchedEffect(value) { if (!editing) field = TextFieldValue(value) }
    LaunchedEffect(editing) { if (editing) focus.requestFocus() }
    DisposableEffect(id) { onDispose { if (active && latest.text != baseline) save(latest.text) } }
    val toolbar = remember { object : TextToolbar {
        override val status: TextToolbarStatus get() = TextToolbarStatus.Hidden
        override fun hide() {}
        override fun showMenu(rect: Rect, onCopyRequested: (() -> Unit)?, onPasteRequested: (() -> Unit)?, onCutRequested: (() -> Unit)?, onSelectAllRequested: (() -> Unit)?) { long(latest.text) }
    } }
    if (editing) CompositionLocalProvider(LocalTextToolbar provides toolbar) {
        BasicTextField(field, { field = continueList(field, it) }, textStyle = style, cursorBrush = SolidColor(M.c.link),
            modifier = modifier.fillMaxWidth().padding(vertical = 4.dp).focusRequester(focus)
                .onGloballyPositioned { bounds = it.boundsInRoot(); if (wasFocused) onBounds(bounds) }
                .onFocusChanged { state ->
                    if (state.isFocused) { wasFocused = true; onBounds(bounds) }
                    else if (wasFocused) {
                        wasFocused = false; onBounds(null)
                        if (commit()) editing = false else focus.requestFocus()
                    }
                })
    } else Text(value.ifEmpty { " " }, style = style, onTextLayout = { layout = it }, modifier = modifier.fillMaxWidth().padding(vertical = 4.dp)
        .pointerInput(value) { detectTapGestures(
            onTap = { position -> field = TextFieldValue(value, TextRange(layout?.getOffsetForPosition(position) ?: value.length)); editing = true },
            onLongPress = { long(value) },
        ) })
}

@Composable
fun NoteRowMenu(onDelete: () -> Unit, onCopy: () -> Unit, onList: () -> Unit, onShare: (() -> Unit)? = null,
                content: @Composable (open: () -> Unit) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    Box {
        content { focus.clearFocus(); menu = true }
        SoftMenu(menu, { menu = false }) {
            SoftMenuItem("Удалить", danger = true) { menu = false; onDelete() }
            SoftMenuItem("Скопировать") { menu = false; onCopy() }
            SoftMenuItem("Список") { menu = false; onList() }
            if (onShare != null) SoftMenuItem("Поделиться") { menu = false; onShare() }
        }
    }
}
