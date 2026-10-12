package ru.anjeib.margin.ai

/**
 * Times and days said in words become digits before the phone reads a phrase: «в пятнадцать» → «в 15», «к трём часам дня» →
 * «к 3 часам дня», «в 14 часов 30 минут» → «в 14:30», «двадцатого октября» → «20 октября». Dictation through the keyboard
 * (and older on-phone speech) writes numbers as words, and the parser reads digits (speech benchmark, 12 окт: Whisper's
 * «в пятнадцать», «в одиннадцать», «в 14 часов, 30 минут» lost the hour). Only after «в / к / до / с» or before a month —
 * «купить две пачки» stays as said.
 */
object SpokenNumbers {
    private val units = mapOf(
        0 to "ноль|ноля|нолю", 1 to "один|одного|одному", 2 to "два|двух|двум", 3 to "три|тр[её]х|тр[её]м", 4 to "четыре|четыр[её]х|четыр[её]м",
        5 to "пять|пяти", 6 to "шесть|шести", 7 to "семь|семи", 8 to "восемь|восьми", 9 to "девять|девяти",
        10 to "десять|десяти", 11 to "одиннадцать|одиннадцати", 12 to "двенадцать|двенадцати", 13 to "тринадцать|тринадцати",
        14 to "четырнадцать|четырнадцати", 15 to "пятнадцать|пятнадцати", 16 to "шестнадцать|шестнадцати", 17 to "семнадцать|семнадцати",
        18 to "восемнадцать|восемнадцати", 19 to "девятнадцать|девятнадцати",
    )
    private val tens = mapOf(20 to "двадцать|двадцати", 30 to "тридцать|тридцати", 40 to "сорок|сорока", 50 to "пятьдесят|пятидесяти")
    private val ordinals = listOf(
        "перв", "втор", "трет", "четв[её]рт", "пят", "шест", "седьм", "восьм", "девят", "десят",
        "одиннадцат", "двенадцат", "тринадцат", "четырнадцат", "пятнадцат", "шестнадцат", "семнадцат", "восемнадцат", "девятнадцат", "двадцат",
    )

    private fun alt(m: Map<Int, String>) = m.values.joinToString("|")
    /** One number in words, 0–59: «пятнадцать», «двадцать три», «сорок пять». */
    private val num = "(?:(?:${alt(tens)})(?:\\s+(?:${alt(units.filterKeys { it in 1..9 })}))?|${alt(units)})"
    private fun value(words: String): Int? {
        var n = 0; var any = false
        for (w in words.lowercase().split(Regex("\\s+"))) {
            val v = (tens.entries + units.entries).firstOrNull { (_, f) -> Regex("^(?:$f)$").matches(w) }?.key ?: return null
            n += v; any = true
        }
        return n.takeIf { any }
    }

    private const val B = "(?<![\\p{L}\\d])"
    private const val E = "(?![\\p{L}\\d])"
    private val ic = RegexOption.IGNORE_CASE
    private const val PREP = "(в|к|до|с|со)"
    private const val PART = "(?:\\s+(утра|дня|вечера|ночи))?"
    private val months = "(январ\\p{L}*|феврал\\p{L}*|март\\p{L}*|апрел\\p{L}*|ма[яюе]|июн\\p{L}*|июл\\p{L}*|август\\p{L}*|сентябр\\p{L}*|октябр\\p{L}*|ноябр\\p{L}*|декабр\\p{L}*|числа)"

    fun normalize(text: String): String {
        var t = text
        // «в 14 часов 30 минут», «в 14 часов, 30 минут» (digits already): one clock time.
        t = Regex("$B$PREP\\s+(\\d{1,2})\\s+час\\p{L}*,?\\s+(\\d{1,2})\\s+минут\\p{L}*$E", ic).replace(t) { m ->
            "${m.groupValues[1]} ${m.groupValues[2]}:${m.groupValues[3].padStart(2, '0')}"
        }
        // «в пятнадцать», «к трём часам дня», «в девять тридцать», «в семь часов пятнадцать минут вечера».
        t = Regex("$B$PREP\\s+($num)(\\s+час\\p{L}*)?(?:,?\\s+($num|\\d{1,2})(\\s+минут\\p{L}*)?)?$PART$E", ic).replace(t) { m ->
            val h = value(m.groupValues[2]) ?: return@replace m.value
            val minWords = m.groupValues[4]
            var mi = if (minWords.isBlank()) null else minWords.toIntOrNull() ?: value(minWords) ?: return@replace m.value
            // Minutes need «час…» or «минут» around them, or to look like minutes («девять тридцать»): «в два три» is no time.
            if (mi != null && m.groupValues[3].isBlank() && m.groupValues[5].isBlank() && mi < 10) mi = null
            if (h !in 0..23 || (mi != null && mi !in 0..59)) return@replace m.value
            val clock = if (mi != null) "$h:%02d".format(mi) else h.toString()
            val hours = if (mi == null && m.groupValues[3].isNotBlank()) m.groupValues[3] else ""
            val rest = if (mi == null && minWords.isNotBlank()) " " + minWords + m.groupValues[5] else ""
            listOf(m.groupValues[1], clock + hours + rest, m.groupValues[6]).filter { it.isNotBlank() }.joinToString(" ")
        }
        // «в час дня», «в час ночи».
        t = Regex("${B}в\\s+час\\s+(дня|ночи)$E", ic).replace(t) { m -> "в 1 ${m.groupValues[1]}" }
        // «двадцатого октября», «двадцать пятого марта», «первого числа» → digits before the month.
        val ord = "((?:двадцат\\p{L}*|тридцат\\p{L}*)\\s+)?(${ordinals.joinToString("|")})(?:ого|его|ое|ье|ий|ый|ое)$E"
        t = Regex("$B$ord\\s+$months$E", ic).replace(t) { m ->
            val tensPart = m.groupValues[1].trim().lowercase()
            val base = ordinals.indexOfFirst { Regex("^(?:$it)$", ic).matches(m.groupValues[2]) } + 1
            val day = (if (tensPart.startsWith("двадцат")) 20 else if (tensPart.startsWith("тридцат")) 30 else 0) + base
            if (base == 0 || day !in 1..31) m.value else "$day ${m.groupValues[3]}"
        }
        return t
    }
}
