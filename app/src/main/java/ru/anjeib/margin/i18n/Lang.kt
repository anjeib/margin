package ru.anjeib.margin.i18n

import ru.anjeib.margin.data.LangMode

/** The interface speaks one language at a time (DESIGN.md §15): Russian or English, never mixed. Words live in paper/Words. */
enum class Lang { Ru, En }

object L {
    @Volatile var lang: Lang = Lang.Ru
}

fun resolveLang(mode: LangMode): Lang = when (mode) {
    LangMode.Ru -> Lang.Ru
    LangMode.En -> Lang.En
    LangMode.Auto -> if (java.util.Locale.getDefault().language in setOf("ru", "uk", "be", "kk")) Lang.Ru else Lang.En
}
