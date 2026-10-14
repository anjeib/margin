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
    /** Amber: what we wait for from someone, in the topic feed. */
    val wait: Color = Color(0xFFA8701A),
    /** A link on the dark/light bubble (undo bar, «+ контакт»): the other theme's petrol, so it reads at ≥4.5:1. */
    val linkOnBubble: Color = Color(0xFF7FBFD6),
    /** Terracotta for small text (late dates): the line colour is too light for words (3.5:1). */
    val accentText: Color = Color(0xFFA84E2A),
    /** On the terracotta button and over photos — the same in both themes. */
    val onAccent: Color = Color.White,
    /** Behind a photo or video in the viewer, under a sheet. */
    val overlay: Color = Color.Black,
)

val LightPalette = Palette(
    dark = false,
    bg = Color(0xFFF6F3EE),
    card = Color(0xFFFCFAF7),
    ink = Color(0xFF1A1A1A),
    graphite = Color(0xFF6B665E),
    faint = Color(0xFF9A9388),
    hairline = Color(0xFFE6E1D8),
    accent = Color(0xFFC8643B),
    accentSoft = Color(0xFFF2E6DC),
    link = Color(0xFF1C6480),
    linkSoft = Color(0xFFDFECF1),
    chip = Color(0xFFEFEAE2),
    bubble = Color(0xFF1C1B1A),
    onBubble = Color(0xFFF6F3EE),
    scrim = Color(0x661A1A1A),
    ok = Color(0xFF7D8B6A),
)

val DarkPalette = Palette(
    dark = true,
    bg = Color(0xFF141210),
    card = Color(0xFF1E1B18),
    ink = Color(0xFFEDE7DD),
    graphite = Color(0xFFADA596),
    faint = Color(0xFF7E776C),
    hairline = Color(0xFF2B2723),
    accent = Color(0xFFD9774E),
    accentSoft = Color(0xFF2C221C),
    link = Color(0xFF7FBFD6),
    linkSoft = Color(0xFF16262C),
    chip = Color(0xFF25211D),
    bubble = Color(0xFFEDE7DD),
    onBubble = Color(0xFF141210),
    scrim = Color(0x99000000),
    ok = Color(0xFF9AA886),
    wait = Color(0xFFD9A24A),
    linkOnBubble = Color(0xFF1C6480),
    accentText = Color(0xFFE08A63),
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

/** The planner's printed voice: headings, section caps, tabs, the margin's italic — one serif (Fable, 14 окт). */
val Book: FontFamily get() = Tinos

private val Playfair = FontFamily(playfair(400), playfair(500), playfair(600), playfair(400, true), playfair(500, true))
/** Headings and Margin's italic voice: Playfair — or, on the planner page, the same book face as everything else. */
val Serif: FontFamily get() = if (PlannerType.value) Tinos else Playfair
private val Inter = FontFamily(inter(400), inter(500), inter(600))
/**
 * The text face: Inter on both pages (Fable, 14 окт: Times at text size greys out, and the margin's italic must stand apart
 * from the words to be scanned as its own column). The planner keeps its serif for headings and the margin.
 */
val Sans: FontFamily get() = Inter
private fun sz(inter: Float, @Suppress("UNUSED_PARAMETER") planner: Float) = inter.sp

object Type {
    // The planner's headings (design/v9): the regular cut, the ink spread a little as letterpress leaves it.
    val display get() = TextStyle(fontFamily = Serif, fontWeight = FontWeight(500),
        shadow = if (PlannerType.value) androidx.compose.ui.graphics.Shadow(Color(0xFF1A1A1A).copy(alpha = 0.55f), androidx.compose.ui.geometry.Offset(0f, 0.6f), 1.6f) else null, fontSize = 40.sp, lineHeight = 46.sp, letterSpacing = (-0.5).sp)
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
    MarginW = marginFor(androidx.compose.ui.platform.LocalDensity.current.fontScale) - if (PlannerType.value) 4.dp else 0.dp
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
