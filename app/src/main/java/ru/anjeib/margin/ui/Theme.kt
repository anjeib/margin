package ru.anjeib.margin.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import ru.anjeib.margin.R
import ru.anjeib.margin.data.ThemeMode
import ru.anjeib.margin.i18n.Strings

@Immutable
data class Palette(
    val dark: Boolean,
    val bg: Color,
    val card: Color,
    val ink: Color,
    val graphite: Color,
    val faint: Color,
    val hairline: Color,
    /** Terracotta: attention and action only — late, send, record, the spark, delete, errors. */
    val accent: Color,
    val accentSoft: Color,
    /** Petrol blue: links and information — contacts, places, badges, selection, «Сохранить». */
    val link: Color,
    val linkSoft: Color,
    val chip: Color,
    val bubble: Color,
    val onBubble: Color,
    val scrim: Color,
    val ok: Color,
    /** Sea teal (owner, 14 окт): the margin's rule, its beads and the field's buttons — red is kept for what is late. */
    val sea: Color = Color(0xFF2B6E72),
    /** Amber: what we wait for from someone, in the topic feed. */
    val wait: Color = Color(0xFFA8701A),
    /** A link on the dark/light bubble (undo bar, «+ контакт»): the other theme's petrol, so it reads at ≥4.5:1. */
    val linkOnBubble: Color = Color(0xFF7FBFD6),
    /** Terracotta for small text (late dates): the line colour is too light for words (3.5:1). */
    val accentText: Color = Color(0xFFA4492F),
    /** On the terracotta button and over photos — the same in both themes. */
    val onAccent: Color = Color.White,
    /** Behind a photo or video in the viewer, under a sheet. */
    val overlay: Color = Color.Black,
)

val LightPalette = Palette(
    dark = false,
    bg = Color(0xFFF6F5F1),
    card = Color(0xFFFFFFFF),
    ink = Color(0xFF1C1E1F),
    graphite = Color(0xFF6E716F),
    faint = Color(0xFF9A9388),
    hairline = Color(0xFFCFCCC4),
    accent = Color(0xFFC8643B),
    accentSoft = Color(0xFFF2E6DC),
    link = Color(0xFF1C6480),
    linkSoft = Color(0xFFDFECF1),
    chip = Color(0xFFE8E6E0),
    bubble = Color(0xFF1C1B1A),
    onBubble = Color(0xFFF6F3EE),
    scrim = Color(0x661A1A1A),
    ok = Color(0xFF7D8B6A),
)

val DarkPalette = Palette(
    dark = true,
    bg = Color(0xFF131516),
    card = Color(0xFF1C2022),
    ink = Color(0xFFE8E6E1),
    graphite = Color(0xFF9A9D9A),
    faint = Color(0xFF7E776C),
    hairline = Color(0xFF34383A),
    accent = Color(0xFFD9774E),
    accentSoft = Color(0xFF2C221C),
    link = Color(0xFF7FBFD6),
    linkSoft = Color(0xFF16262C),
    chip = Color(0xFF23272A),
    bubble = Color(0xFFEDE7DD),
    onBubble = Color(0xFF141210),
    scrim = Color(0x99000000),
    ok = Color(0xFF9AA886),
    wait = Color(0xFFD9A24A),
    linkOnBubble = Color(0xFF1C6480),
    accentText = Color(0xFFE58F72),
    sea = Color(0xFF4F9A9E),
)

@OptIn(ExperimentalTextApi::class)
private fun playfair(w: Int, italic: Boolean = false) = Font(
    if (italic) R.font.playfair_italic else R.font.playfair,
    FontWeight(w),
    if (italic) FontStyle.Italic else FontStyle.Normal,
    variationSettings = FontVariation.Settings(FontVariation.weight(w)),
)

@OptIn(ExperimentalTextApi::class)
private fun inter(w: Int) = Font(
    R.font.inter, FontWeight(w),
    variationSettings = FontVariation.Settings(FontVariation.weight(w)),
)

/**
 * «Планер» (owner, 14 окт, design/v9/v9-2-planner.png): the whole page set in one Times-like book face — Tinos (OFL), metric
 * twin of Times, with a full Cyrillic. [PlannerType] is on while that page is chosen; [Serif] and [Book] follow it.
 */
val Tinos = FontFamily(
    Font(R.font.tinos_regular, FontWeight.Normal), Font(R.font.tinos_italic, FontWeight.Normal, FontStyle.Italic),
    Font(R.font.tinos_bold, FontWeight.Bold), Font(R.font.tinos_bolditalic, FontWeight.Bold, FontStyle.Italic),
)
val PlannerType = androidx.compose.runtime.mutableStateOf(false)

/**
 * The planner's book face (owner, 15 окт, «полоса набора»): Literata (TypeTogether, OFL; variable — weight and optical size)
 * for the words of the page — the sections and every entry. With [Commissioner] for what serves them and [Cormorant] for the
 * day's head; all three with a full Cyrillic.
 */
@OptIn(ExperimentalTextApi::class)
private fun literata(w: Int, italic: Boolean = false, opsz: Float = 12f) = Font(
    if (italic) R.font.literata_italic else R.font.literata, FontWeight(w), if (italic) FontStyle.Italic else FontStyle.Normal,
    variationSettings = FontVariation.Settings(FontVariation.weight(w), FontVariation.Setting("opsz", opsz)),
)
val Literata = FontFamily(literata(400), literata(450), literata(500), literata(600), literata(400, true), literata(500, true))
/** The day's number and name, set large: the display cut of the optical-size axis. */
val LiterataDisplay = FontFamily(literata(400, opsz = 60f), literata(450, opsz = 60f), literata(500, opsz = 60f), literata(400, true, 48f), literata(500, true, 48f))
/**
 * Owner, 15 окт (three builds compared on the emulator): what serves the words — hours, dates, the lines under an entry, the
 * field — in Commissioner (OFL, full Cyrillic), softer and finer than a plain grotesque; the day's number and every page's
 * name in Cormorant Garamond (OFL) — thin, high-contrast, the one luxurious note on the page. The entries stay in Literata.
 */
@OptIn(ExperimentalTextApi::class)
private fun commissioner(w: Int) = Font(R.font.commissioner, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
val Commissioner = FontFamily(commissioner(400), commissioner(500), commissioner(600))
@OptIn(ExperimentalTextApi::class)
private fun cormorant(w: Int, italic: Boolean = false) = Font(if (italic) R.font.cormorant_italic else R.font.cormorant, FontWeight(w),
    if (italic) FontStyle.Italic else FontStyle.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(w)))
val Cormorant = FontFamily(cormorant(500), cormorant(600), cormorant(500, true), cormorant(600, true))

/** The planner's printed voice: headings, section caps, tabs, the margin's italic — one serif (Fable, 14 окт). */
val Book: FontFamily get() = Literata

private val Playfair = FontFamily(playfair(400), playfair(500), playfair(600), playfair(400, true), playfair(500, true))
/** Headings and Margin's italic voice: Playfair — or, on the planner page, the same book face as everything else. */
val Serif: FontFamily get() = if (PlannerType.value) Literata else Playfair
private val Inter = FontFamily(inter(400), inter(500), inter(600))
/**
 * The text face: Inter on both pages (Fable, 14 окт: Times at text size greys out, and the margin's italic must stand apart
 * from the words to be scanned as its own column). The planner keeps its serif for headings and the margin.
 */
val Sans: FontFamily get() = if (PlannerType.value) Commissioner else Inter
private fun sz(inter: Float, @Suppress("UNUSED_PARAMETER") planner: Float) = inter.sp

object Type {
    // The planner's headings (design/v9): the regular cut, the ink spread a little as letterpress leaves it.
    val display get() = TextStyle(fontFamily = Serif, fontWeight = FontWeight(500),
        fontSize = 40.sp, lineHeight = 46.sp, letterSpacing = (-0.5).sp)
    /** A page's name — «Среда», «Заметки», «Содержание»: Cormorant, upright (Fable, 15 окт: the italic read as a boutique, not a strict book). */
    val pageTitle get() = if (PlannerType.value) TextStyle(fontFamily = Cormorant, fontWeight = FontWeight(600), fontSize = 50.sp, lineHeight = 54.sp)
        else display
    val title get() = TextStyle(fontFamily = Serif, fontWeight = FontWeight(500), fontSize = 26.sp, lineHeight = 32.sp)
    val cardTitle get() = TextStyle(fontFamily = Serif, fontWeight = FontWeight(500), fontSize = 19.sp, lineHeight = 25.sp)
    val body get() = TextStyle(fontFamily = Sans, fontWeight = FontWeight(400), fontSize = sz(16f, 16.5f), lineHeight = 23.sp)
    val task get() = TextStyle(fontFamily = Sans, fontWeight = FontWeight(400), fontSize = sz(16.5f, 17f), lineHeight = 22.sp)
    val small get() = TextStyle(fontFamily = Sans, fontWeight = FontWeight(400), fontSize = sz(13.5f, 14f), lineHeight = 18.sp)
    val label get() = TextStyle(fontFamily = Sans, fontWeight = FontWeight(500), fontSize = 11.5.sp, letterSpacing = 1.2.sp)
    val tab get() = TextStyle(fontFamily = Sans, fontWeight = FontWeight(500), fontSize = 17.sp)
    val italic get() = TextStyle(fontFamily = Serif, fontStyle = FontStyle.Italic, fontSize = 15.sp, lineHeight = 21.sp)
}

val LocalPalette = staticCompositionLocalOf { LightPalette }
val LocalStr = staticCompositionLocalOf<Strings> { ru.anjeib.margin.i18n.Ru }

object M {
    val c: Palette @Composable get() = LocalPalette.current
    val s: Strings @Composable get() = LocalStr.current
}

@Composable
fun MarginTheme(mode: ThemeMode, strings: Strings, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
        ThemeMode.Auto -> isSystemInDarkTheme()
    }
    val p = if (dark) DarkPalette else LightPalette
    // The planner's margin is narrower — room for the words (ChatGPT's review, owner, 14 окт).
    // «Полоса набора» (owner, 15 окт): the margin a little wider — the dates stand in it from the left, the day's number on its right.
    MarginW = marginFor(androidx.compose.ui.platform.LocalDensity.current.fontScale) + if (PlannerType.value) 12.dp else 0.dp
    val scheme = if (dark) darkColorScheme(
        primary = p.accent, onPrimary = Color.White, background = p.bg, onBackground = p.ink,
        surface = p.card, onSurface = p.ink, surfaceVariant = p.chip, onSurfaceVariant = p.graphite,
        surfaceContainer = p.card, surfaceContainerHigh = p.card, surfaceContainerHighest = p.chip,
        surfaceContainerLow = p.bg, outline = p.hairline, outlineVariant = p.hairline,
        secondaryContainer = p.accentSoft, onSecondaryContainer = p.ink, primaryContainer = p.accentSoft,
        onPrimaryContainer = p.ink, scrim = Color.Black,
    ) else lightColorScheme(
        primary = p.accent, onPrimary = Color.White, background = p.bg, onBackground = p.ink,
        surface = p.card, onSurface = p.ink, surfaceVariant = p.chip, onSurfaceVariant = p.graphite,
        surfaceContainer = p.card, surfaceContainerHigh = p.card, surfaceContainerHighest = p.chip,
        surfaceContainerLow = p.bg, outline = p.hairline, outlineVariant = p.hairline,
        secondaryContainer = p.accentSoft, onSecondaryContainer = p.ink, primaryContainer = p.accentSoft,
        onPrimaryContainer = p.ink, scrim = Color.Black,
    )
    CompositionLocalProvider(LocalPalette provides p, LocalStr provides strings) {
        // Dialogs, pickers and buttons of Material speak in the page's own face too.
        val base = androidx.compose.material3.Typography()
        val typo = base
        MaterialTheme(colorScheme = scheme, typography = typo, content = content)
    }
}
