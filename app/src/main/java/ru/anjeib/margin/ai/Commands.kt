package ru.anjeib.margin.ai

/**
 * «Добавь подзадачу купить полис», «место: Ленина 5», «новая заметка — посмотреть Одиссею».
 * Said in so many words, so recognised in code: instant, free, exact. Everything else still goes to the AI.
 * [what] — subtask, task, note, place, time, date, phone, email, telegram, people, project, repeat, remind, entry, settings.
 */
data class Command(val what: String, val value: String)

object Commands {
    private const val VERB = "(?:добав\\p{L}*|запиш\\p{L}*|созда\\p{L}*|сдела\\p{L}*|постав\\p{L}*|укаж\\p{L}*|указа\\p{L}*|впиш\\p{L}*|внес\\p{L}*|" +
        "нов(?:ая|ый|ую|ое|ые)|add|create|new|set|put)"

    // Order matters: «подзадачу» before «задачу».
    private val objects = listOf(
        "subtask" to "подзадач\\p{L}*|шаг(?:и|а|ов)?|пункт(?:ы|а|ов)?|subtasks?|steps?",
        "task" to "задач\\p{L}*|дел[оау]|tasks?|to-?dos?",
        "note" to "заметк\\p{L}*|идею|идея|мысль|notes?|ideas?",
        "place" to "мест[оау]|адрес\\p{L}*|place|address|location",
        "time" to "время|времени|time",
        "date" to "дат[уаы]|срок\\p{L}*|дедлайн|date|deadline|due",
        "phone" to "телефон\\p{L}*|номер(?:а)?|phone",
        "email" to "почт[уаы]|e-?mail|имейл",
        "telegram" to "телеграм\\p{L}*|telegram|тг",
        "contact" to "контакт\\p{L}*|contacts?",
        "people" to "участник\\p{L}*|человек\\p{L}*|people|person",
        "project" to "проект\\p{L}*|project",
        "repeat" to "повтор\\p{L}*|repeat",
        "remind" to "напоминани\\p{L}*|reminder",
        "entry" to "запис[ьи]|комментари\\p{L}*|comment|record|note to task",
        "list" to "спис[оа]к\\p{L}*|чек-?лист\\p{L}*|list|checklist",
    )

    private val withVerb = objects.map { (what, words) ->
        what to Regex("^$VERB\\s+(?:в\\s+|к\\s+|ещё\\s+|еще\\s+|одн[уа]\\s+)?(?:$words)(?=$|[\\s:—–\\-,.])\\s*[:—–\\-]?\\s*(.*)$", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    }
    // Without a verb only with a colon: «Подзадача: …», «Место: …» — «дело в том, что…» stays free text.
    private val withColon = objects.map { (what, words) ->
        what to Regex("^(?:$words)\\s*:\\s*(.*)$", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    }

    /**
     * A numbered step in an open task: «измени подзадачу 2 на …», «подзадача 2: …», «удали подзадачу 3», «подзадача 1 готова».
     * [what]: sub_edit / sub_delete / sub_done; [value] = "<n>|<new words>".
     */
    fun numbered(text: String): Command? {
        val t = text.trim()
        val n = "(?:подзадач\\p{L}*|шаг\\p{L}*|пункт\\p{L}*|subtask|step)\\s*(?:№|номер|#)?\\s*(\\d{1,2})"
        Regex("^(?:удали\\p{L}*|убери\\p{L}*|delete|remove)\\s+$n\\s*$", RegexOption.IGNORE_CASE).find(t)?.let { return Command("sub_delete", it.groupValues[1] + "|") }
        Regex("^(?:отметь\\p{L}*|закрой\\p{L}*|выполни\\p{L}*|mark|tick)?\\s*$n\\s*(?:готова|сделана|выполнена|done|готово)\\s*$", RegexOption.IGNORE_CASE).find(t)?.let { return Command("sub_done", it.groupValues[1] + "|") }
        Regex("^(?:измени\\p{L}*|поменяй\\p{L}*|исправь\\p{L}*|переименуй\\p{L}*|замени\\p{L}*|change|rename|edit)?\\s*(?:в\\s+)?$n\\s*(?:на|в|:|—|–|-|to)\\s*(.+)$", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).find(t)
            ?.let { return Command("sub_edit", it.groupValues[1] + "|" + it.groupValues[2].trim().trim('«', '»', '"')) }
        return null
    }

    /** «Исправить: перенеси созвон на пятницу» — change what exists, never make anything new. */
    private val fix = Regex("^(?:исправ\\p{L}*|поправ\\p{L}*|fix|correct)\\s*[:—–\\-]?\\s*(.+)$", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))

    /** «Подзадача», «Место», «Исправить» before a colon are commands, not the name of a matter. */
    fun isCommandWord(word: String): Boolean = parse("$word: x") != null

    // «итог дня в 21», «вечерний итог не нужен», «утреннюю сводку в 9»: the app's own summaries, set without the AI (works offline).
    private const val EVENING = "(?:итог(?:и)?\\s+дня|вечерн\\p{L}*\\s+(?:итог\\p{L}*|обзор\\p{L}*|сводк\\p{L}*)|итог\\p{L}*\\s+вечером|вечером\\s+итог\\p{L}*|evening\\s+(?:summary|review)|daily\\s+review)"
    private const val MORNING = "(?:утренн\\p{L}*\\s+сводк\\p{L}*|сводк[уа]|morning\\s+(?:summary|brief))"
    private const val ASK = "(?:присылай\\s+|присылать\\s+|пришли\\s+|ставь\\s+|поставь\\s+|давай\\s+|хочу\\s+|включи\\s+|send\\s+(?:the\\s+|me\\s+)?|set\\s+(?:the\\s+)?)?"
    private const val OFF = "(?:не\\s+нуж\\p{L}*|не\\s+надо|не\\s+присылай|выключи\\p{L}*|отключи\\p{L}*|off)"
    private const val STOP = "(?:выключи\\p{L}*|отключи\\p{L}*|не\\s+присылай|убери|turn\\s+off|no)"
    private val dailyOn = listOf("evening" to EVENING, "morning" to MORNING).map { (k, w) ->
        k to Regex("^$ASK$w\\s+(?:в|к|at)\\s+(\\d{1,2}(?:[:.]\\d{2})?(?:\\s*(?:утра|дня|вечера|am|pm))?)[.!]?$", RegexOption.IGNORE_CASE)
    }
    private val dailyOff = listOf("evening" to EVENING, "morning" to MORNING).map { (k, w) ->
        k to Regex("^(?:$w\\s+$OFF|$STOP\\s+$w)[.!]?$", RegexOption.IGNORE_CASE)
    }

    /** [what] "settings", [value] "evening=21:00" / "evening=off" / "morning=09:00" / "morning=off". */
    fun daily(text: String): Command? {
        val t = text.trim()
        dailyOff.forEach { (k, r) -> if (r.matches(t)) return Command("settings", "$k=off") }
        dailyOn.forEach { (k, r) ->
            val v = r.find(t)?.groupValues?.get(1) ?: return@forEach
            var hm = time(v) ?: return@forEach
            // «итог дня в 9» is nine in the evening; «в 9 утра» says otherwise.
            val h = hm.take(2).toInt()
            if (k == "evening" && h in 1..11 && !Regex("утра|am", RegexOption.IGNORE_CASE).containsMatchIn(v)) hm = "%02d".format(h + 12) + hm.drop(2)
            return Command("settings", "$k=$hm")
        }
        return null
    }

    fun parse(text: String): Command? {
        val t = text.trim()
        daily(t)?.let { return it }
        fix.find(t)?.let { return Command("fix", it.groupValues[1].trim()) }
        for ((what, r) in withVerb + withColon) {
            val m = r.find(t) ?: continue
            return Command(what, m.groupValues[1].trim().trim('«', '»', '"').trim())
        }
        return null
    }

    /** Several items in one go: «купить полис, взять паспорт; позвонить». */
    fun items(value: String): List<String> =
        value.split(Regex("[,;\\n]|\\s+и\\s+(?=\\p{L})")).map { it.trim().trimEnd('.') }.filter { it.length > 1 }

    /** «в 15», «15:30», «15.30», «в 9 утра» → HH:MM. */
    fun time(value: String): String? {
        val m = Regex("(?:^|\\s|в\\s?)(\\d{1,2})(?:[:.](\\d{2}))?(?:\\s*(утра|дня|вечера|ночи|am|pm))?\\s*$", RegexOption.IGNORE_CASE).find(value.trim()) ?: return null
        var h = m.groupValues[1].toInt()
        val min = m.groupValues[2].ifEmpty { "0" }.toInt()
        when (m.groupValues[3].lowercase()) { "вечера", "pm" -> if (h < 12) h += 12; "дня" -> if (h < 12 && h != 0) h += 12 }
        if (h !in 0..23 || min !in 0..59) return null
        return "%02d:%02d".format(h, min)
    }

    /** A hint for the AI when the command needs its understanding (dates, which task). */
    fun hint(c: Command): String? = when (c.what) {
        "fix" -> "[The user explicitly asked to CHANGE something that already exists: \"${c.value}\". Only update_task / update_subtask / append_note / person on the existing item they mean; never create a new task or note.]"
        "contact" -> "[The user explicitly asked to add a CONTACT (a person or a company): \"${c.value}\". One person action with everything said (name, about, company, phone, email, telegram, address); no tasks, no notes.]"
        "task" -> "[The user explicitly asked to create exactly ONE task: \"${c.value}\". No notes, no other changes.]"
        "subtask" -> "[The user explicitly asked to add subtasks (add_subtasks) to the task they mean: \"${c.value}\". Nothing else.]"
        "note" -> "[The user explicitly asked for a NOTE: \"${c.value}\". create_note/append_note only.]"
        "date" -> "[The user explicitly asked to set the date: \"${c.value}\". Change only the date.]"
        "repeat" -> "[The user explicitly asked to set a repeat: \"${c.value}\". Change only the repeat.]"
        "remind" -> "[The user explicitly asked for a reminder: \"${c.value}\". Set remind_at only.]"
        "time" -> "[The user explicitly asked to set the time: \"${c.value}\". Change only the time.]"
        "place" -> "[The user explicitly asked to set the place: \"${c.value}\". Change only the place.]"
        else -> null
    }
}
