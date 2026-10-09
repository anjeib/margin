package ru.anjeib.margin.paper

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp



/*
 * The paper: four roles of colour and two voices of type (DESIGN.md §12–13). Nothing on a screen picks a colour or a
 * size of its own — it takes a role from here.
 */

@Immutable
data class Ink(
    val dark: Boolean,
    /** The page. */
    val paper: Color,
    /** A sheet over the page: a line's sheet, the field. */
    val sheet: Color,
    /** Your words. */
    val ink: Color,
    /** Margin's quiet voice; what is done. */
    val graphite: Color,
    /** Only what is gone (struck, «не делаем») and hairlines. */
    val pencil: Color,
    /** Attention: a tail, a question. */
    val red: Color,
    /** A fill for an action (send, recording) — white sign on it. */
    val redFill: Color,
    /** A link: a person, a thread, a place; what is chosen. */
    val link: Color,
    /** A soft wash of the link colour: the chosen day, a pressed reply. */
    val linkWash: Color,
    /** The margin rule — the border of two voices, pale on purpose. */
    val rule: Color,
    val hairline: Color,
    val scrim: Color,
) {
    /** A sheet over the page: the same paper by day; at night a little lighter, as paper lifted toward the lamp. */
    val lifted: Color get() = if (dark) sheet else paper
}

val PaperLight = Ink(
    dark = false,
    paper = Color(0xFFF6F3EE), sheet = Color(0xFFFCFAF7),
    ink = Color(0xFF1A1A1A), graphite = Color(0xFF6B665E), pencil = Color(0xFF9A9388),
    red = Color(0xFFA9502C), redFill = Color(0xFFC8643B),
    link = Color(0xFF1C6480), linkWash = Color(0xFFDFECF1),
    rule = Color(0xFFDFAC94), hairline = Color(0xFFE6E1D8), scrim = Color(0x66000000),
)

val PaperDark = Ink(
    dark = true,
    paper = Color(0xFF141210), sheet = Color(0xFF1E1B18),
    ink = Color(0xFFEDE7DD), graphite = Color(0xFFADA596), pencil = Color(0xFF7E776C),
    red = Color(0xFFE08A62), redFill = Color(0xFFD9774E),
    link = Color(0xFF7FBFD6), linkWash = Color(0xFF16262C),
    rule = Color(0xFF76442F), hairline = Color(0xFF2B2723), scrim = Color(0x99000000),
)

/** Two voices: yours (Inter) and Margin's (Playfair, italic on the margin). Six sizes in all. */
object Voice {
    /** A page's title: the weekday, a thread, a person, «Оглавление». */
    val title = TextStyle(fontFamily = Serif, fontWeight = FontWeight(500), fontSize = 34.sp, lineHeight = 40.sp, letterSpacing = (-0.3).sp)
    /** A section in the index; a sheet's title. */
    val section = TextStyle(fontFamily = Serif, fontWeight = FontWeight(500), fontSize = 22.sp, lineHeight = 28.sp)
    /** Your words. */
    val words = TextStyle(fontFamily = Sans, fontWeight = FontWeight(400), fontSize = 16.sp, lineHeight = 24.sp)
    /** Margin says something longer: an answer, a hint over the field. */
    val reply = TextStyle(fontFamily = Serif, fontStyle = FontStyle.Italic, fontWeight = FontWeight(400), fontSize = 15.sp, lineHeight = 22.sp,
        fontFeatureSettings = "lnum")
    /** A note on the margin. */
    val note = TextStyle(fontFamily = Serif, fontStyle = FontStyle.Italic, fontWeight = FontWeight(400), fontSize = 13.sp, lineHeight = 17.sp,
        fontFeatureSettings = "tnum, lnum")
    /** The system's own small words: weekdays in the month, settings. */
    val small = TextStyle(fontFamily = Sans, fontWeight = FontWeight(400), fontSize = 13.sp, lineHeight = 18.sp, fontFeatureSettings = "tnum")
}

/** The grid of a page (DESIGN.md §3). */
object Grid {
    /** Width of the margin; the rule runs here. */
    val margin = 80.dp
    /** Notes end this far before the rule — clear of the mark's touch area. */
    val noteGap = 10.dp
    /** The mark's touch area: 40 dp across the rule, the whole height of the line. */
    val markTouch = 40.dp
    /** Your words begin here: right after the mark's touch area. */
    val text = 100.dp
    val right = 20.dp
    /** A line is never lower — the whole line is a touch target. */
    val line = 48.dp
    /** The mark's node on the rule. */
    val node = 16.dp
}

val LocalInk = staticCompositionLocalOf { PaperLight }
val LocalWords = staticCompositionLocalOf<Words> { WordsRu }
/** The paper under the marks right here: the page, or a sheet lifted over it — the mark's node is cut out of it. */
val LocalGround = androidx.compose.runtime.compositionLocalOf<Color?> { null }

object P {
    val c: Ink @Composable get() = LocalInk.current
    val w: Words @Composable get() = LocalWords.current
}

@Composable
fun PaperTheme(dark: Boolean = isSystemInDarkTheme(), words: Words, content: @Composable () -> Unit) {
    val c = if (dark) PaperDark else PaperLight
    // Material only for what we borrow (dialogs, pickers); its colours are the paper's.
    val scheme = if (dark) darkColorScheme(primary = c.link, onPrimary = c.paper, background = c.paper, onBackground = c.ink,
        surface = c.sheet, onSurface = c.ink, surfaceVariant = c.sheet, onSurfaceVariant = c.graphite, outline = c.hairline,
        surfaceContainer = c.sheet, surfaceContainerHigh = c.sheet, surfaceContainerHighest = c.sheet, surfaceContainerLow = c.paper,
        primaryContainer = c.linkWash, onPrimaryContainer = c.ink, secondaryContainer = c.linkWash, onSecondaryContainer = c.ink)
    else lightColorScheme(primary = c.link, onPrimary = c.paper, background = c.paper, onBackground = c.ink,
        surface = c.sheet, onSurface = c.ink, surfaceVariant = c.sheet, onSurfaceVariant = c.graphite, outline = c.hairline,
        surfaceContainer = c.sheet, surfaceContainerHigh = c.sheet, surfaceContainerHighest = c.sheet, surfaceContainerLow = c.paper,
        primaryContainer = c.linkWash, onPrimaryContainer = c.ink, secondaryContainer = c.linkWash, onSecondaryContainer = c.ink)
    CompositionLocalProvider(LocalInk provides c, LocalWords provides words) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
