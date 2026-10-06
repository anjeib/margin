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

// ---------------- names in cases: «Виктору от вас», «вам от Марины» ----------------

private val maleOnA = setOf("никита", "илья", "кузьма", "фома", "лука", "савва", "даня", "ваня", "саша", "женя", "миша", "дима", "паша", "петя", "вова", "коля", "толя", "лёша", "леша", "серёжа", "сережа", "гоша", "юра", "слава", "костя", "витя", "боря", "гриша", "федя", "стёпа", "степа", "тёма", "тема", "лёва", "лева")
private val hush = "гкхжшчщ"

/** One Russian word in the dative (кому) or genitive (кого); [female] — for surnames and soft-sign names. */
private fun word(w: String, dative: Boolean, female: Boolean, surname: Boolean): String {
    if (w.length < 2 || !w.first().isLetter() || w.any { it.isDigit() } || w.all { it.isUpperCase() }) return w
    val low = w.lowercase()
    fun cut(n: Int, end: String) = w.dropLast(n) + end
    // Names that lose a vowel: Пётр → Петру, Павел → Павла, Лев → Льву.
    mapOf("пётр" to "Петр", "петр" to "Петр", "павел" to "Павл", "лев" to "Льв")[low]?.let { if (!surname) return it + if (dative) "у" else "а" }
    return when {
        surname && (low.endsWith("ова") || low.endsWith("ева") || low.endsWith("ёва") || low.endsWith("ина") || low.endsWith("ына")) -> cut(1, "ой")
        surname && (low.endsWith("ая")) -> cut(2, "ой")
        surname && (low.endsWith("ий") || low.endsWith("ой")) && !female -> cut(2, if (dative) "ому" else "ого")
        surname && (low.endsWith("ов") || low.endsWith("ев") || low.endsWith("ёв") || low.endsWith("ин") || low.endsWith("ын")) && !female -> w + (if (dative) "у" else "а")
        surname && female -> w
        low.endsWith("ия") -> cut(1, "и")
        low.endsWith("а") -> cut(1, if (dative) "е" else if (low.dropLast(1).last() in hush) "и" else "ы")
        low.endsWith("я") -> cut(1, if (dative) "е" else "и")
        low.endsWith("й") -> cut(1, if (dative) "ю" else "я")
        low.endsWith("ь") -> if (female) cut(1, "и") else cut(1, if (dative) "ю" else "я")
        low.last() in "бвгджзклмнпрстфхцчшщ" && !female -> w + (if (dative) "у" else "а")
        else -> w // «Мари», «Кармен», foreign and indeclinable names stay as they are
    }
}

/** A person's name in a case. Companies («ООО ПакетПро», «Дента+»), handles and English names are left alone. */
private fun inCase(name: String, dative: Boolean): String {
    val n = name.trim()
    if (n.isEmpty() || n.any { it in "«»\"+@&0123456789" } || n.split(' ').size > 3 || !n.first().let { it in 'А'..'Я' || it == 'Ё' } ||
        Regex("(?i)^(ооо|оао|зао|ао|ип|пао)\\b").containsMatchIn(n)) return n
    val parts = n.split(' ')
    val first = parts.first().lowercase()
    val female = ((first.endsWith("а") || first.endsWith("я")) && first !in maleOnA) || first in setOf("любовь", "нинель", "адель", "рахиль", "эсфирь")
    return parts.mapIndexed { i, p -> word(p, dative, female, surname = i > 0) }.joinToString(" ")
}

fun String.dative(): String = inCase(this, dative = true)
fun String.genitive(): String = inCase(this, dative = false)

/** A thing named after a verb («Посмотреть …»): «Одиссея» → «Одиссею»; other words stay as they are. */
fun String.accusative(): String {
    val w = trim()
    if (w.contains(' ') || w.length < 3 || !w.first().isLetter()) return w
    val low = w.lowercase()
    return when {
        low.endsWith("ия") -> w.dropLast(1) + "ю"
        low.endsWith("а") -> w.dropLast(1) + "у"
        low.endsWith("я") -> w.dropLast(1) + "ю"
        else -> w
    }
}
