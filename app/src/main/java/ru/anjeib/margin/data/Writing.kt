package ru.anjeib.margin.data

/** Every record starts with a capital letter, however it was typed or dictated («купить хлеб» → «Купить хлеб»). */
fun String.sentence(): String {
    val s = trim()
    val i = s.indexOfFirst { it.isLetter() }
    // Leave links, numbers-first and already-capital text alone.
    if (i < 0 || s.startsWith("http") || s[i].isUpperCase() || s.take(i).any { it.isDigit() }) return s
    return s.substring(0, i) + s[i].uppercaseChar() + s.substring(i + 1)
}

/**
 * Names of the people involved get their capital back inside the text: «заказать муку у тимура» → «… у Тимура».
 * Only words that look like a form of that very name (same stem, a couple of letters of ending) — so «Вера» never turns «вёрстку» into «Вёрстку».
 */
fun String.withNames(names: Collection<String>): String {
    val stems = names.map { it.trim() }.filter { it.length >= 3 && it.first().isLetter() && !it.contains(' ') }
        .map { n -> n.lowercase().let { it.take((it.length - 1).coerceAtLeast(3)) } to n.length }
    if (stems.isEmpty()) return this
    return Regex("\\p{L}+").replace(this) { m ->
        val w = m.value
        val low = w.lowercase()
        if (w.first().isLowerCase() && stems.any { (st, len) -> low.startsWith(st) && w.length <= len + 2 }) w.replaceFirstChar { it.uppercaseChar() } else w
    }
}

/** A person's name as it should read: «тимур» → «Тимур», «анна-мария» → «Анна-Мария». */
fun String.properName(): String = trim().split('-').joinToString("-") { p -> p.replaceFirstChar { it.uppercaseChar() } }

/**
 * An address as it is written: «Ленина 5» → «Ленина, 5», «ул Мира 12к2» → «ул Мира, 12к2».
 * Only the house number gets its comma; a city or anything already punctuated is left as said.
 */
fun String.address(): String {
    val s = trim().replace(Regex("\\s+"), " ")
    if (s.isEmpty()) return s
    return s.replace(Regex("(\\p{L}[\\p{L}.\\-]*)\\s+(д\\.?\\s*)?(\\d+[\\p{L}]?(?:[/к-]\\s?\\d+\\p{L}?)?)(?=$|[\\s,;])")) { m ->
        val word = m.groupValues[1]
        // «5 мая», «2 этаж», «10 мин» are not addresses; a word right before a number is a street.
        if (m.range.first > 0 && s[m.range.first - 1] == ',') m.value else "$word, ${m.groupValues[2]}${m.groupValues[3]}"
    }.replace(",,", ",").sentence()
}
