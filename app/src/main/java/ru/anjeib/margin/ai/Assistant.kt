package ru.anjeib.margin.ai

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import ru.anjeib.margin.data.AppData
import ru.anjeib.margin.data.AppJson
import ru.anjeib.margin.data.Entry
import ru.anjeib.margin.data.Note
import ru.anjeib.margin.data.Person
import ru.anjeib.margin.data.Repeat
import ru.anjeib.margin.data.next
import ru.anjeib.margin.data.nextOccurrence
import ru.anjeib.margin.data.Settings
import ru.anjeib.margin.data.Subtask
import ru.anjeib.margin.data.Task
import ru.anjeib.margin.data.findSub
import ru.anjeib.margin.data.mapTree
import ru.anjeib.margin.data.nowStamp
import ru.anjeib.margin.data.properName
import ru.anjeib.margin.data.sentence
import ru.anjeib.margin.data.withNames
import ru.anjeib.margin.data.text
import ru.anjeib.margin.data.weekEnd
import ru.anjeib.margin.data.weekStart
import ru.anjeib.margin.data.withDone
import ru.anjeib.margin.i18n.L
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale

data class Question(val text: String, val options: List<String>)

data class AiPlan(val reply: String?, val actions: List<JsonObject>, val question: Question?)

data class Applied(
    val data: AppData,
    val touchedTasks: List<String>,
    val touchedNotes: List<String>,
    val caption: String?,
    val created: Int,
)

/** Where the user typed: the main feed, inside one task, or in the notes section. */
sealed interface Scope {
    data object Main : Scope
    data class InTask(val taskId: String) : Scope
    data object Notes : Scope
}

/** Builds prompts for OpenRouter and turns the model's JSON into validated changes to [AppData]. */
object Assistant {

    // ---------------- prompts ----------------

    // Compact on purpose: every token here is paid on every message (it is the cached prefix).
    // Explicit JSON shapes (small models need them) but no "|null" noise: unknown fields are simply left out.
    private val schema = """
        Reply with ONE JSON object only: {"reply":"…","actions":[…],"question":{"text":"…","options":["…"]}}
        Leave out every field you don't know or that is empty — never write null, [] or "". reply and question are optional too.
        Each action is an object with "type". Shapes (all fields except type/ids/title/text are optional):
        {"type":"create_task","title":"…","start":"YYYY-MM-DD","due":"YYYY-MM-DD","time":"HH:MM","remind_at":"YYYY-MM-DDTHH:MM","people":["…"],"project":"…","entry":"…","waiting_for":"…","promised_to":"…","note_id":"…","repeat":{"freq":"weekly","interval":1,"days":[2]},"subtasks":[{"title":"…","assignee":"…","children":[…]}]}
        {"type":"add_subtasks","task_id":"…","parent_subtask_id":"…","subtasks":[{"title":"…"}]}
        {"type":"update_task","task_id":"…", + only the changed fields of create_task}
        {"type":"add_entry","task_id":"…","text":"…"}   — a dated record on a task
        {"type":"complete_task","task_id":"…"}  {"type":"complete_subtask","task_id":"…","subtask_id":"…"}  {"type":"delete_task","task_id":"…"}
        {"type":"create_note","title":"…","text":"…","project":"…"}  {"type":"append_note","note_id":"…","text":"…"}  {"type":"update_note","note_id":"…","title":"…","project":"…"}
        {"type":"person","name":"…","about":"…","phone":"…","email":"…"}
        repeat.freq: daily|weekly|monthly|yearly; days: 1–7, Monday=1.
    """.trimIndent()

    private fun rules(s: Settings) = buildString {
        val ideas = if (L.s.aiLanguage == "English") "ideas" else "идеи"
        appendLine("Rules:")
        appendLine("- Turn the user's free text into actions. Titles in the user's language: short, imperative, no final period.")
        appendLine("- Dates: take exact dates from the Calendar line. \"на этой неделе\" → start=today, due=this Sunday; \"next week\" → next Mon..Sun; \"this month\" → due=month end; \"к пятнице\" → due=that Friday; a weekday = its nearest future date. Times: \"в 15:00\" → time; утром≈09:00, после обеда≈14:00, вечером≈19:00. Never invent precise deadlines; \"когда будет время\" = no dates.")
        appendLine("- Recurring (\"каждый вторник\", \"по будням\"=days 1–5) → repeat, due = first occurrence.")
        appendLine("- Phone calendar entries are busy time: never plan tasks over them.")
        appendLine("- remind_at only when the user asks to be reminded.")
        appendLine("- people: others involved; add \"${L.s.me}\" only when work is split with others.")
        appendLine("- project: when the text names a product, site, client or area (e.g. \"vuzburo\"), that name in lowercase is the project — even if it's new. Never attach an unrelated known project; no clear project → leave it out.")
        appendLine("- One message may hold several things: one action per thing. Concrete steps → subtasks (dictated steps verbatim, assignee if stated).")
        appendLine("- Ideas, thoughts, opinions (\"идея:\", \"мне кажется\"), facts, phones, links are NOTES, never tasks or subtasks. append_note only to a listed note on the SAME topic/project; otherwise create_note with a short topical title (project ideas → \"<project> · $ideas\"). Never invent ids.")
        appendLine("- Someone owes the user something (\"Дима должен прислать макет в среду\") → a NEW task: title = what is expected, waiting_for = person, due = when expected (else +2 days). Never put waiting_for on an existing task.")
        appendLine("- \"заказать/купить/спросить у X\" is the user's own errand: X goes to people, not waiting_for.")
        appendLine("- A step with its own person AND its own deadline (\"Катя — макеты к четвергу\") → its own create_task (people, due, same project), not a subtask.")
        appendLine("- The user promised something to someone (\"обещал Анне показать до пятницы\", \"договорились, что я пришлю Диме тексты\") → a task for the user with promised_to = that person (and due if said).")
        appendLine("- Facts about a person → person action: about = their role in 2–4 words (\"дизайнер vuzburo\"), phone/email if given.")
        appendLine("- More work for an EXISTING listed task (same topic, \"ещё\", \"для …\") → add_subtasks to it; changed dates/people → update_task; done → complete. Don't duplicate tasks.")
        appendLine(if (s.autoSubtasks) "- A clearly big new task with no steps given → add 3–5 short subtasks." else "- Don't invent subtasks.")
        appendLine(if (s.askWhenUnclear) "- Timing essential but missing (a call, a meeting) → create the task without dates AND ask when, 3–4 short options like \"${L.s.today} 16:00\". At most one question." else "- Never ask questions.")
        appendLine("- An answer to your earlier question → update_task on that task, no duplicate.")
        appendLine("- Change only what the user asked about: never touch other tasks, never add or alter dates, times or repeats nobody mentioned.")
        appendLine("- A question or a request for advice → answer in reply (2–4 short sentences, no markdown), actions only if asked. \"Что горит / что сорвётся\" → the 3–5 riskiest items (overdue, due within 3 days, waiting on others, your promises), each with why — not a plain list.")
        appendLine("- reply/question/options in ${L.s.aiLanguage}: brief, calm, no emoji, no exclamation marks.")
    }

    private fun scopeRule(scope: Scope) = when (scope) {
        is Scope.InTask -> "SCOPE: the user is inside task_id=${scope.taskId}. Everything is about THIS task: changes → update_task; steps → add_subtasks (their steps verbatim; \"разбей на шаги\" alone → 3–6 concrete steps); done → complete; any other info → add_entry, keeping facts (places, addresses, names, numbers) verbatim even if you also add a subtask. No other tasks unless asked."
        Scope.Notes -> "SCOPE: Notes section. The text is a note: append_note to the related listed note or create_note with a fitting title and project. A task only if explicitly asked."
        Scope.Main -> ""
    }

    /** Phone-calendar lines (set by the app before each request when the user allowed it). */
    @Volatile var calendar: String = ""

    // ---------------- context: only what matters for this message ----------------

    private val word = Regex("[\\p{L}\\p{Nd}]{4,}")

    /** Crude Russian/English stems: enough to match «Бахчисарай/Бахчисарае», «договор/договору». */
    private fun stems(text: String): Set<String> =
        word.findAll(text.lowercase()).map { m -> val w = m.value; w.take(if (w.length >= 7) 5 else w.length - 1) }.toSet()

    private fun Subtask.allText(): String = title + " " + (assignee ?: "") + " " + children.joinToString(" ") { it.allText() }

    /** How strongly a task relates to what the user just wrote. */
    private fun score(t: Task, q: Set<String>, text: String, recent: Set<String>): Int {
        var sc = 0
        val low = text.lowercase()
        if (t.project != null && low.contains(t.project)) sc += 5
        (t.people + listOfNotNull(t.waitingFor)).forEach { p -> if (p.length >= 3 && low.contains(p.lowercase().take(p.length - 1))) sc += 3 }
        sc += 2 * (stems(t.title + " " + t.subtasks.joinToString(" ") { it.allText() } + " " + t.entries.takeLast(3).joinToString(" ") { it.text }) intersect q).size
        if (t.id in recent) sc += 3
        return sc
    }

    private fun StringBuilder.taskLine(t: Task) {
        append("[task_id=${t.id}] ${t.title}")
        val meta = listOfNotNull(t.start?.let { "start $it" }, t.due?.let { "due $it" }, t.time?.let { "at $it" }, t.project?.let { "#$it" },
            t.people.takeIf { it.isNotEmpty() }?.joinToString(), t.waitingFor?.let { "waiting for $it" }, t.promisedTo?.let { "promised to $it" }, t.noteId?.let { "note_id=$it" },
            t.repeat?.let { "repeats ${it.freq}" + (if (it.days.isNotEmpty()) " ${it.days}" else "") }, if (t.postponed >= 2) "postponed ${t.postponed}×" else null)
        if (meta.isNotEmpty()) append(" | " + meta.joinToString(", "))
        appendLine()
    }

    private fun StringBuilder.taskDetail(t: Task, entries: Int) {
        taskLine(t)
        t.entries.takeLast(entries).forEach { e -> if (e.text.isNotBlank()) appendLine("    · ${e.at.take(10)}: ${e.text.replace('\n', ' ').take(160)}") }
        fun sub(list: List<Subtask>, depth: Int) {
            list.forEach { st ->
                append("  ".repeat(depth + 1) + "- [subtask_id=${st.id}] ${st.title}")
                st.assignee?.let { append(" ($it)") }
                if (st.done) append(" [done]")
                appendLine()
                sub(st.children, depth + 1)
            }
        }
        sub(t.subtasks, 0)
    }

    /**
     * [query] = what the user wrote: then only a skeleton (this week's tasks in one line each, names, projects,
     * note titles) plus the items related to the text go to the model. [query] = null → full overview (briefs, reviews).
     */
    fun context(d: AppData, s: Settings, now: LocalDateTime = LocalDateTime.now(), focusTask: String? = null, query: String? = null, recent: Set<String> = emptySet()): String {
        val today = now.toLocalDate()
        return buildString {
            appendLine("Now: $today, ${now.toLocalTime().withSecond(0).withNano(0)}.")
            // Small models miscount weekdays: hand them a ready calendar instead.
            appendLine("Calendar: " + (0L..13L).joinToString(", ") { i -> val x = today.plusDays(i); x.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + " " + x + (if (i == 0L) " (today)" else if (i == 1L) " (tomorrow)" else "") } + ".")
            appendLine("Month ends ${today.withDayOfMonth(today.lengthOfMonth())}.")
            if (s.userName.isNotBlank()) appendLine("User's name: ${s.userName}.")
            val low = query?.lowercase()
            if (d.persons.isNotEmpty()) appendLine("Known people: " + d.persons.joinToString("; ") { p ->
                val mentioned = low == null || listOf(p.name).plus(p.aliases).any { n -> n.length >= 3 && low.contains(n.lowercase().take(n.length - 1)) }
                p.name + (if (mentioned) p.about?.let { " — ${it.take(80)}" } ?: "" else "")
            })
            if (d.projects.isNotEmpty()) appendLine("Known projects: ${d.projects.joinToString()}.")
            if (calendar.isNotBlank()) { appendLine("Phone calendar:"); appendLine(calendar) }

            val open = d.tasks.filter { !it.done }
            if (open.isEmpty()) appendLine("Open tasks: none.")
            else if (query == null) {
                appendLine("Open tasks:")
                open.take(80).forEach { t -> taskDetail(t, if (t.id == focusTask) 10 else 2) }
            } else {
                val q = stems(query)
                val ranked = open.map { it to score(it, q, query, recent) }.filter { it.second >= 2 || it.first.id == focusTask }
                    .sortedByDescending { it.second }.take(10).map { it.first }
                val weekEnd = today.weekEnd()
                val skeleton = open.filter { t -> t !in ranked && (t.dueDate ?: t.startDate)?.let { !it.isAfter(weekEnd) } == true }
                    .sortedBy { it.due ?: it.start }.take(12)
                if (ranked.isNotEmpty()) { appendLine("Related open tasks:"); ranked.forEach { taskDetail(it, if (it.id == focusTask) 10 else 3) } }
                if (skeleton.isNotEmpty()) { appendLine("Other open tasks this week:"); skeleton.forEach { taskLine(it) } }
                val hidden = open.size - ranked.size - skeleton.size
                if (hidden > 0) appendLine("(+$hidden more open tasks not shown — create new tasks freely, they are not duplicates of hidden ones unless named the same)")
            }

            if (d.notes.isNotEmpty()) {
                appendLine("Notes:")
                if (query == null) d.notes.take(25).forEach { n ->
                    val gist = (n.aiSummary ?: n.entries.takeLast(4).joinToString(" / ") { it.text }).replace('\n', ' ').take(260)
                    appendLine("[note_id=${n.id}] ${n.title.ifBlank { "—" }}${n.project?.let { " #$it" } ?: ""}: $gist")
                } else {
                    val q = stems(query)
                    val related = d.notes.map { n -> n to ((if (n.project != null && low!!.contains(n.project)) 5 else 0) + 2 * (stems(n.title + " " + n.text.take(600)) intersect q).size) }
                        .filter { it.second >= 2 }.sortedByDescending { it.second }.take(3).map { it.first }
                    related.forEach { n ->
                        appendLine("[note_id=${n.id}] ${n.title.ifBlank { "—" }}${n.project?.let { " #$it" } ?: ""}: " +
                            (n.aiSummary ?: n.entries.takeLast(3).joinToString(" / ") { it.text }).replace('\n', ' ').take(200))
                    }
                    (d.notes - related.toSet()).take(15).forEach { n -> appendLine("[note_id=${n.id}] ${n.title.ifBlank { "—" }}${n.project?.let { " #$it" } ?: ""}") }
                }
            }
            if (query == null) {
                val recentDone = d.tasks.filter { it.done && it.doneAt != null && LocalDate.parse(it.doneAt).isAfter(today.minusDays(7)) }
                if (recentDone.isNotEmpty()) appendLine("Done in the last 7 days: " + recentDone.take(30).joinToString("; ") { it.title })
            }
        }
    }

    private val noteMarkers = Regex("^\\s*(идея|мысль|заметка|запомни|в заметк|добавь в заметк|запиши в заметк|idea|note|remember)", RegexOption.IGNORE_CASE)

    /** The user's words as sent to the model, with a cheap local hint when they clearly marked a note. */
    fun userMessage(text: String): String =
        if (noteMarkers.containsMatchIn(text)) "$text\n[The user marked this as a NOTE: save it with create_note/append_note, not as a task or subtask.]" else text

    /**
     * Stable part first (rules + schema — the provider caches it and bills it cheaper), then what changes.
     * [query] — the user's text for focused context; null for a full overview (smart requests).
     */
    fun systemPrompt(d: AppData, s: Settings, scope: Scope = Scope.Main, query: String? = null, recent: Set<String> = emptySet()): String =
        "You are the assistant inside Margin, a calm, minimal planner app.\n\n" + rules(s) + "\n" + schema + "\n\n" +
            scopeRule(scope).let { if (it.isNotBlank()) it + "\n\n" else "" } +
            "Context:\n" + context(d, s, focusTask = (scope as? Scope.InTask)?.taskId, query = query, recent = recent)

    fun briefPrompt(d: AppData, s: Settings, evening: Boolean = false): String = """
        You are the assistant inside Margin, a calm planner. ${if (evening) "It is evening. Write the user's brief for TOMORROW (${LocalDate.now().plusDays(1)}) in ${L.s.aiLanguage}: in a few words what is still open today, then what tomorrow holds and the single most important thing for tomorrow." else "Write the user's brief for today in ${L.s.aiLanguage}: what today holds, anything overdue, the single most important thing."}
        At most 3 short sentences and 45 words, no markdown, no emoji, plain words (no jargon like «окно», «фокус-блок»). Never say "nothing urgent" when something is due today or overdue.
        If a promise to someone ("promised to …") is due today or overdue, or something you wait for ("waiting for …") is late, mention it.
        Also give "headline": the most important thing in 3–6 words (e.g. "тексты vuzburo"), and a realistic free time slot for it ${if (evening) "tomorrow" else "today"} avoiding timed tasks.
        "main_task_id": the task_id of that most important thing (from the context), if it is a task.
        Reply with JSON only: {"text": string, "headline": string, "focus": "HH:MM–HH:MM" | null, "main_task_id": string | null}

        Context:
        ${context(d, s)}
    """.trimIndent()

    fun personPrompt(d: AppData, p: Person): String {
        val n = p.name
        val mentions = buildString {
            d.tasks.filter { t -> t.people.any { p.matches(it) } || t.waitingFor?.let { p.matches(it) } == true || t.promisedTo?.let { p.matches(it) } == true ||
                t.subtasks.any { s -> s.assignee?.let { p.matches(it) } == true } || t.title.contains(n, true) || t.entries.any { it.text.contains(n, true) } }
                .take(30).forEach { t ->
                    appendLine("- task: ${t.title}${if (t.done) " [done ${t.doneAt}]" else t.due?.let { " [due $it]" } ?: ""}" +
                        (t.waitingFor?.let { " (waiting for $it)" } ?: "") + (t.promisedTo?.let { " (user promised to $it)" } ?: "") + "; " +
                        t.entries.filter { it.text.contains(n, true) }.joinToString(" / ") { it.at.take(10) + " " + it.text.take(120) })
                }
            d.notes.filter { it.title.contains(n, true) || it.text.contains(n, true) }.take(10)
                .forEach { appendLine("- note ${it.title}: " + it.entries.filter { e -> e.text.contains(n, true) }.joinToString(" / ") { e -> e.at.take(10) + " " + e.text.take(160) }) }
        }
        return """
            You keep a planner. Today is ${LocalDate.now()}. About the person "$n", in ${L.s.aiLanguage}, no markdown:
            "about": who they are to the user, max 6 words (e.g. "дизайнер vuzburo") — ONLY if the mentions or "Known so far" say it; never guess a role or a job. Unknown → "".
            "summary": what's going on now, 1–2 sentences, max 35 words, written TO the user in second person ("Ждёте от неё…", "Вы обещали…"), starting with a capital letter, short dates like "9 окт" (no year): what is expected from them, what the user promised them, when they last came up. Never say something was done or sent unless its task is marked [done]. Never say "пользователь".
            Known so far: ${p.about?.takeIf { p.aboutFromUser } ?: "—"}
            Mentions:
            $mentions
            Reply with JSON only: {"about": string, "summary": string}
        """.trimIndent()
    }

    fun projectPrompt(d: AppData, project: String): String {
        val tasks = d.tasks.filter { it.project == project }
        val notes = d.notes.filter { it.project == project }
        return """
            You keep a planner. Today is ${LocalDate.now()}. Write the weekly summary of project "$project" in ${L.s.aiLanguage}:
            1–2 short sentences, max 35 words, no markdown, written to the user ("вы"): what moved, what we wait for and from whom, what is on the user and by when.
            Say something moved ONLY if a task is marked [done] in the last 7 days; otherwise say what is next. Ideas below have no task yet — mention them only if they wait for a decision.
            Tasks:
            ${tasks.take(40).joinToString("\n") { t -> "- ${t.title}${if (t.done) " [done ${t.doneAt}]" else ""}${t.due?.let { " due $it" } ?: ""}${t.waitingFor?.let { " (waiting for $it)" } ?: ""}${t.promisedTo?.let { " (promised to $it)" } ?: ""}" }}
            Ideas (note entries):
            ${notes.flatMap { it.entries }.filter { e -> d.tasks.none { it.sourceEntryId == e.id } }.takeLast(20).joinToString("\n") { "- " + it.text.take(140) }}
            Reply with JSON only: {"summary": string}
        """.trimIndent()
    }

    /** One idea → exactly one task linked to its note. */
    fun ideaPrompt(noteId: String, text: String) =
        "Turn this idea into exactly ONE concrete task (create_task with note_id=$noteId; add subtasks only if obvious, people/dates only if stated). Do not create notes.\n\nIdea: $text"

    // ---------------- parsing ----------------

    /**
     * Pulls the JSON object out of a model reply: tolerates code fences, chatter, trailing garbage after the object
     * and typographic quotes used as JSON quotes. Null when nothing usable is there (the caller then asks again).
     */
    fun parseObject(content: String): JsonObject? {
        fun tryParse(x: String) = runCatching { AppJson.parseToJsonElement(x).jsonObject }.getOrNull()
        fun balanced(x: String): String? {
            val start = x.indexOf('{').takeIf { it >= 0 } ?: return null
            var depth = 0; var inStr = false; var esc = false
            for (i in start until x.length) {
                val ch = x[i]
                if (inStr) { if (esc) esc = false else if (ch == '\\') esc = true else if (ch == '"') inStr = false; continue }
                when (ch) { '"' -> inStr = true; '{' -> depth++; '}' -> { depth--; if (depth == 0) return x.substring(start, i + 1) } }
            }
            return null
        }
        val start = content.indexOf('{'); val end = content.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        tryParse(content.substring(start, end + 1))?.let { return it }
        balanced(content)?.let { b -> tryParse(b)?.let { return it } }
        // Typographic quotes in place of JSON ones, e.g. "question”: null
        val fixed = content.replace('“', '"').replace('”', '"')
        balanced(fixed)?.let { b -> tryParse(b)?.let { return it } }
        return null
    }

    private val actionTypes = setOf("create_task", "add_subtasks", "update_task", "add_entry", "complete_task", "complete_subtask",
        "delete_task", "create_note", "append_note", "update_note", "person")

    /** Actions in the shapes small models actually produce: a proper list, one bare action, or {"create_note": {...}}. */
    private fun actionsOf(o: JsonObject): List<JsonObject> {
        // Small models sometimes mangle the key itself: ".type", " type".
        fun fixed(x: JsonObject): JsonObject = if ("type" in x) x else x.entries.firstOrNull { it.key.trim().trimStart('.', '_').equals("type", true) }
            ?.let { (k, v) -> JsonObject(x - k + ("type" to v)) } ?: x
        (o["actions"] as? JsonArray)?.let { arr -> return arr.mapNotNull { (it as? JsonObject)?.let(::fixed) }.filter { it.str("type") in actionTypes } }
        if (o.str("type") in actionTypes) return listOf(o)
        return o.entries.filter { it.key in actionTypes }.mapNotNull { (k, v) ->
            (v as? JsonObject)?.let { JsonObject(it + ("type" to JsonPrimitive(k))) }
        }
    }

    /** True when the reply can't be used: unparseable, or "actions" holds garbage like [1, 2, 3]. Worth asking again. */
    fun isBroken(content: String): Boolean {
        val o = parseObject(content) ?: return content.trimStart().startsWith("{") || content.contains("\"actions\"")
        val arr = o["actions"] as? JsonArray ?: return false
        return arr.isNotEmpty() && arr.none { it is JsonObject }
    }

    /**
     * Phrases a small model gets wrong — several things at once, ideas/notes, step lists — go to the stronger model.
     * Decided on the phone, no extra request.
     */
    fun isComplex(text: String, scope: Scope): Boolean {
        val t = text.lowercase()
        val clauses = Regex("[.;!?]\\s|,\\s*(а|и|ещё|еще|потом|также|плюс)\\s|\\s(и ещё|а ещё|и еще|а также|потом)\\s").findAll(t).count()
        return scope == Scope.Notes || noteMarkers.containsMatchIn(t) || clauses >= 1 || t.length > 90 ||
            Regex("мне кажется|идея|разбей|шаг|план|заметк|должен прислать|обещал").containsMatchIn(t)
    }

    fun parsePlan(content: String): AiPlan {
        val o = parseObject(content) ?: return AiPlan(content.trim().ifBlank { null }, emptyList(), null)
        val actions = actionsOf(o)
        val q = (o["question"] as? JsonObject)?.let { qo ->
            val text = qo.str("text") ?: return@let null
            Question(text, (qo["options"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }?.take(4) ?: emptyList())
        }
        return AiPlan(o.str("reply"), actions, q)
    }

    fun field(o: JsonObject?, k: String): String? = o?.str(k)

    // ---------------- applying ----------------

    /**
     * [source] — what the user actually wrote; used to recover a project the model forgot to name.
     * [strict] — the user typed this: the model may change only what the words are about ([focus] — the open task,
     * [recent] — tasks of the last exchanges, for «перенеси его»). Unasked dates, times, repeats and other tasks stay as they were.
     */
    fun apply(data: AppData, plan: AiPlan, source: String = "", strict: Boolean = false, focus: String? = null, recent: Set<String> = emptySet()): Applied {
        var d = data
        val touched = linkedSetOf<String>()
        val touchedNotes = linkedSetOf<String>()
        var created = 0; var updated = 0; var subAdded = 0; var notes = 0; var entries = 0; var completed = 0
        val me = setOf("я", "me", L.s.me.lowercase())
        val said = stems(source)
        val low = source.lowercase()
        val saysWhen = strict.not() || whenCue.containsMatchIn(low)
        val saysTime = strict.not() || timeCue.containsMatchIn(low)
        val saysRepeat = strict.not() || repeatCue.containsMatchIn(low)
        val saysOwe = strict.not() || oweCue.containsMatchIn(low)
        val bulk = strict.not() || bulkCue.containsMatchIn(low)
        /** Is this existing task what the user is talking about? */
        fun asked(t: Task): Boolean = bulk || t.id == focus || t.id in recent ||
            (stems(t.title + " " + t.people.joinToString(" ") + " " + listOfNotNull(t.waitingFor, t.promisedTo, t.project).joinToString(" ")) intersect said).isNotEmpty()
        /** One spelling per person: the known one, or the name with a capital letter. */
        fun who(n: String?): String? = n?.trim()?.takeIf { it.isNotEmpty() }?.let { x -> if (x.lowercase() in me) x else d.person(x)?.name ?: x.properName() }
        fun subs(l: List<Subtask>, names: List<String> = emptyList()): List<Subtask> = l.map { it.copy(title = it.title.withNames(names + d.persons.map { p -> p.name }), assignee = who(it.assignee), children = subs(it.children, names)) }

        fun learn(people: List<String>, project: String?) {
            val newPeople = people.distinct().filter { it.lowercase() !in me && d.person(it) == null }.map { Person(name = it.properName()) }
            val newProject = project?.takeIf { p -> d.projects.none { it.equals(p, true) } }
            if (newPeople.isNotEmpty() || newProject != null)
                d = d.copy(persons = d.persons + newPeople, projects = d.projects + listOfNotNull(newProject))
        }
        fun validNote(id: String?) = id?.takeIf { n -> d.notes.any { it.id == n } }

        for (a in plan.actions) {
            when (a.str("type")) {
                "create_task" -> {
                    val title = a.str("title")?.trim()?.trimEnd('.')?.cap() ?: continue
                    // The same open task already exists — small models re-create what they see in the context.
                    // Same words with a new date or a new promise is a new thing, not a copy.
                    if (strict && d.tasks.any { o -> !o.done && o.title.trim().trimEnd('.').equals(title, true) &&
                            (a.date("due") == null || a.date("due") == o.due) && a.str("promised_to") == null && a.str("waiting_for") == null }) continue
                    val entryText = a.str("entry") ?: a.str("comment")
                    val raw = withAutoReminder(
                        Task(
                            title = title,
                            start = a.date("start"),
                            due = a.date("due"),
                            time = a.time("time"),
                            remindAt = a.dateTime("remind_at"),
                            people = a.strList("people"),
                            project = a.project(),
                            entries = listOfNotNull(entryText?.let { Entry(text = it.sentence()) }),
                            waitingFor = a.str("waiting_for"),
                            promisedTo = a.str("promised_to"),
                            source = source.trim().take(240).ifBlank { null },
                            noteId = validNote(a.str("note_id")),
                            repeat = repeat(a["repeat"]),
                            subtasks = subtasks(a["subtasks"]),
                        ).normalized()
                    )
                    // «заказать муку у Тимура» is the user's own errand, not something Тимур owes.
                    val errand = raw.waitingFor != null && !saysOwe && errandCue.containsMatchIn(low)
                    val involved = (raw.people + listOfNotNull(raw.waitingFor, raw.promisedTo)).mapNotNull { who(it) }
                    val t = raw.copy(
                        title = raw.title.withNames(involved),
                        time = raw.time?.takeIf { saysTime }, remindAt = raw.remindAt?.takeIf { raw.time == null || saysTime || "remind_at" in a },
                        people = ((raw.people + listOfNotNull(raw.waitingFor.takeIf { errand })).mapNotNull { who(it) }).distinct(),
                        waitingFor = if (errand) null else who(raw.waitingFor), promisedTo = who(raw.promisedTo), subtasks = subs(raw.subtasks, involved),
                    ).onSchedule(source).let { if (it.time == null && raw.time != null) it.copy(remindAt = null) else it }
                    learn(t.people + t.subtasks.mapNotNull { it.assignee } + listOfNotNull(t.waitingFor, t.promisedTo), t.project)
                    d = d.copy(tasks = d.tasks + t)
                    touched += t.id; created++
                }
                "add_subtasks" -> {
                    val id = a.str("task_id") ?: continue
                    val existing = d.tasks.find { it.id == id }?.subtasks?.let { flat(it) }?.map { it.title.lowercase() }?.toSet() ?: emptySet()
                    val subs = subs(subtasks(a["subtasks"]), d.tasks.first { it.id == id }.people).filter { it.title.lowercase() !in existing }
                    if (subs.isEmpty() || d.tasks.none { it.id == id }) continue
                    if (strict && !asked(d.tasks.first { it.id == id })) continue
                    val parent = a.str("parent_subtask_id")
                    d = d.copy(tasks = d.tasks.map { t ->
                        if (t.id != id) t
                        else if (parent != null && t.subtasks.findSub(parent) != null)
                            t.copy(subtasks = t.subtasks.mapTree(parent) { p ->
                                // Steps inherit the parent's assignee — don't repeat it on every line.
                                p.copy(children = p.children + subs.map { if (it.assignee.equals(p.assignee, true)) it.copy(assignee = null) else it }, done = false)
                            }, done = false)
                        else t.copy(subtasks = t.subtasks + subs, done = false)
                    })
                    learn(subs.mapNotNull { it.assignee }, null)
                    touched += id; subAdded += subs.size
                }
                "update_task" -> {
                    val id = a.str("task_id") ?: continue
                    val old = d.tasks.find { it.id == id } ?: continue
                    if (strict && !asked(old)) continue
                    // Only what the words are about: an unasked date, time, repeat or «ждём» keeps its old value.
                    fun has(k: String, ok: Boolean) = k in a && ok
                    val newWait = if (has("waiting_for", saysOwe || a.str("waiting_for") == null)) who(a.str("waiting_for")) else old.waitingFor
                    val newPromise = if (has("promised_to", saysOwe || a.str("promised_to") == null)) who(a.str("promised_to")) else old.promisedTo
                    val dated = has("due", saysWhen) || has("start", saysWhen)
                    val timed = has("time", saysTime)
                    var next = withAutoReminder(
                        old.copy(
                            title = if ("title" in a) a.str("title")?.trim()?.trimEnd('.')?.sentence()?.withNames(old.people + listOfNotNull(old.waitingFor, old.promisedTo)) ?: old.title else old.title,
                            start = if (has("start", saysWhen)) a.date("start") else old.start,
                            due = if (has("due", saysWhen)) a.date("due") else old.due,
                            time = if (timed) a.time("time") else old.time,
                            remindAt = if ("remind_at" in a) a.dateTime("remind_at") else if (dated || timed) null else old.remindAt,
                            people = if ("people" in a) a.strList("people").mapNotNull { who(it) }.distinct() else old.people,
                            project = if ("project" in a) a.project() else old.project,
                            waitingFor = newWait,
                            promisedTo = newPromise,
                            noteId = if ("note_id" in a) validNote(a.str("note_id")) else old.noteId,
                            repeat = if (has("repeat", saysRepeat)) repeat(a["repeat"]) else old.repeat,
                        ).normalized()
                    )
                    // A new promise or expectation is a thing of its own: it never turns a recurring task into a one-off.
                    val split = old.repeat != null && ((newPromise != null && old.promisedTo == null) || (newWait != null && old.waitingFor == null))
                    if (split) {
                        next = next.copy(id = ru.anjeib.margin.data.newId(), repeat = null, entries = emptyList(), subtasks = emptyList(), createdAt = LocalDate.now().toString(), source = source.trim().take(240).ifBlank { null })
                        d = d.copy(tasks = d.tasks + next)
                        created++
                    } else {
                        if (dated && next.repeat != null) next = next.onSchedule(source)
                        d = d.copy(tasks = d.tasks.map { if (it.id == id) next else it })
                        updated++
                    }
                    learn(next.people + listOfNotNull(next.waitingFor, next.promisedTo), next.project)
                    touched += next.id
                }
                "add_entry" -> {
                    val id = a.str("task_id") ?: continue
                    val text = a.str("text") ?: continue
                    val target = d.tasks.find { it.id == id } ?: continue
                    if (strict && !asked(target)) continue
                    d = d.copy(tasks = d.tasks.map { if (it.id == id) it.copy(entries = it.entries + Entry(text = text.sentence())) else it })
                    touched += id; entries++
                }
                "complete_task" -> {
                    val id = a.str("task_id") ?: continue
                    val target = d.tasks.find { it.id == id && !it.done } ?: continue
                    if (strict && !asked(target)) continue
                    val next = target.nextOccurrence()
                    d = d.copy(tasks = d.tasks.map { if (it.id == id) it.copy(done = true, doneAt = LocalDate.now().toString()) else it } + listOfNotNull(next))
                    touched += id; completed++
                }
                "complete_subtask" -> {
                    val id = a.str("task_id") ?: continue
                    val sid = a.str("subtask_id") ?: continue
                    if (strict && d.tasks.find { it.id == id }?.let { asked(it) } != true) continue
                    d = d.copy(tasks = d.tasks.map { if (it.id == id) it.copy(subtasks = it.subtasks.mapTree(sid) { s -> s.withDone(true) }) else it })
                    touched += id; updated++
                }
                "delete_task" -> {
                    val id = a.str("task_id") ?: continue
                    if (strict && d.tasks.find { it.id == id }?.let { asked(it) } != true) continue
                    d = d.copy(tasks = d.tasks.filterNot { it.id == id })
                    updated++
                }
                "create_note", "append_note" -> {
                    val text = (a.str("text") ?: a.str("body") ?: "").sentence()
                    // The user chose «в отдельную заметку»: no appending, no project-ideas redirect.
                    val forceNew = a.str("force_new") == "true"
                    // Only append where the note is really about the same thing; small models grab a random listed note.
                    val id = if (forceNew) null else validNote(a.str("note_id"))?.takeIf { nid ->
                        val n = d.notes.first { it.id == nid }
                        val words = stems(text + " " + source)
                        (n.project != null && (text + " " + source).contains(n.project, true)) ||
                            (stems(n.title).intersect(words).isNotEmpty()) || (n.project == null && stems(n.text.take(400)).intersect(words).size >= 2)
                    }
                    if (id != null) {
                        if (text.isBlank()) continue
                        // Touched notes float to the top.
                        val n = d.notes.first { it.id == id }.let { it.copy(entries = it.entries + Entry(text = text), updatedAt = nowStamp()) }
                        d = d.copy(notes = listOf(n) + d.notes.filterNot { it.id == id })
                        touchedNotes += id
                    } else {
                        // New note — also when the model aimed at a note that doesn't exist.
                        val given = a.str("title")
                        val project = a.project() ?: d.projects.firstOrNull { (text + " " + given.orEmpty() + " " + source).contains(it, true) }
                            ?: given?.substringBefore(" · ", "")?.trim()?.lowercase()?.takeIf { it.isNotBlank() && it.length <= 24 && !it.contains(' ') }
                        val ideas = if (L.s.aiLanguage == "English") "ideas" else "идеи"
                        // A title that just repeats the text is no title at all.
                        val lazyTitle = given == null || text.startsWith(given.trimEnd('.', '…'), ignoreCase = true) || given.length > 50
                        // A project idea joins that project's ideas note rather than starting a new one.
                        val home = if (!forceNew && lazyTitle && project != null) d.notes.find { it.project == project && it.title.endsWith(ideas) } else null
                        if (home != null) {
                            d = d.copy(notes = listOf(home.copy(entries = home.entries + Entry(text = text), updatedAt = nowStamp())) + d.notes.filterNot { it.id == home.id })
                            touchedNotes += home.id; notes++
                            continue
                        }
                        val title = ((if (lazyTitle) project?.let { "$it · $ideas" } else given) ?: given ?: text.lineSequence().firstOrNull()?.take(60) ?: continue).sentence()
                        val body = if (title == text) "" else text
                        val n = Note(title = title, project = project, entries = listOfNotNull(body.takeIf { it.isNotBlank() }?.let { Entry(text = it) }))
                        learn(emptyList(), n.project)
                        d = d.copy(notes = listOf(n) + d.notes)
                        touchedNotes += n.id
                    }
                    notes++
                }
                "update_note" -> {
                    val id = validNote(a.str("note_id")) ?: continue
                    d = d.copy(notes = d.notes.map { n ->
                        if (n.id != id) n else n.copy(
                            title = a.str("title")?.sentence() ?: n.title,
                            project = if ("project" in a) a.project() else n.project,
                        )
                    })
                    touchedNotes += id; notes++
                }
                "person" -> {
                    val name = a.str("name")?.properName() ?: continue
                    if (name.lowercase() in me) continue
                    val existing = d.person(name)
                    val p = (existing ?: Person(name = name)).let {
                        it.copy(
                            about = a.str("about") ?: it.about,
                            aboutFromUser = it.aboutFromUser || a.str("about") != null,
                            phone = a.str("phone") ?: it.phone,
                            email = a.str("email") ?: it.email,
                            updatedAt = nowStamp(),
                        )
                    }
                    d = d.copy(persons = if (existing == null) d.persons + p else d.persons.map { if (it.id == p.id) p else it })
                }
            }
        }
        val s = L.s
        val caption = when {
            created > 1 -> s.tasksCreated(created)
            created == 1 -> s.taskCreated
            subAdded > 0 -> s.subtasksAdded(subAdded)
            completed > 0 && updated == 0 -> s.markedDone
            updated > 0 -> s.taskUpdated
            entries > 0 -> s.entryAdded
            notes > 0 -> s.notesCreated
            else -> null
        }
        return Applied(d, touched.toList().filter { id -> d.tasks.any { it.id == id } }, touchedNotes.toList(), caption, created)
    }

    // What the user's words are about — so the model can't quietly change what wasn't mentioned.
    private val whenCue = Regex("сегодн|завтра|вчера|понедельн|вторник|сред[аеуы]|четверг|пятниц|суббот|воскресен|\\b(пн|вт|ср|чт|пт|сб|вс)\\b|числ|недел|месяц|январ|феврал|март|апрел|ма[йя]\\b|июн|июл|август|сентябр|октябр|ноябр|декабр|через|после|\\bдо\\b|\\bк\\s|перенес|отлож|сдвин|срок|дат|\\d{1,2}[./]\\d{1,2}|\\d{1,2}\\s*-?\\s*(го|е|ого)\\b|\\d{1,2}\\s+(янв|фев|мар|апр|мая|июн|июл|авг|сен|окт|ноя|дек)|today|tomorrow|monday|tuesday|wednesday|thursday|friday|saturday|sunday|week|month|next|until|\\bby\\b|move|postpone|date|deadline")
    private val timeCue = Regex("\\d{1,2}[:.]\\d{2}|\\bв\\s?\\d{1,2}\\b|утр|вечер|днём|днем|обед|ночь|ночи|полдень|полноч|час|время|\\bam\\b|\\bpm\\b|morning|evening|noon|night|o'clock|time|\\bat\\s?\\d")
    private val repeatCue = Regex("кажд|ежедн|еженед|ежемес|ежегод|по будн|по выходн|по (понедельн|вторн|сред|четверг|пятниц|суббот|воскресен)|раз в|повтор|every|daily|weekly|monthly|yearly|repeat")
    private val oweCue = Regex("жд[уёеа]|ждать|пришл|пришлёт|пришлет|должен|должна|должны|обещал|вернёт|вернет|ответит|сдаст|сдаёт|сдает|скинет|подготовит|сделает|отправит|привез|оплатит|wait|will send|owes?\\b|promis|expect")
    private val errandCue = Regex("заказ|куп|взять|забрать|спрос|узна|уточн|запрос|order|buy|ask|pick up")
    private val bulkCue = Regex("\\bвсе\\b|\\bвсё\\b|\\bвсех\\b|всю|остальн|просроч|everything|\\ball\\b|overdue")
    private val ordinals = mapOf("перв" to 1, "втор" to 2, "трет" to 3, "четв" to 4, "пят" to 5, "шест" to 6, "седьм" to 7, "восьм" to 8, "девят" to 9, "десят" to 10, "пятнадцат" to 15, "двадцат" to 20)

    /** A repeating task always stands on its nearest coming date (models put «первого числа» into the past). */
    private fun Task.onSchedule(source: String, today: LocalDate = LocalDate.now()): Task {
        val r = repeat ?: return this
        val due0 = dueDate
        val first: LocalDate? = when (r.freq) {
            "daily" -> if (due0 == null || due0.isBefore(today)) today else null
            "weekly" -> if (r.days.isEmpty()) { if (due0 == null) today else if (due0.isBefore(today)) generateSequence(due0) { r.next(it) }.first { !it.isBefore(today) } else null }
                else if (due0 == null || due0.isBefore(today) || due0.dayOfWeek.value !in r.days) generateSequence(today) { it.plusDays(1) }.first { it.dayOfWeek.value in r.days } else null
            "monthly", "yearly" -> {
                // «первого числа», «10-го числа»: the day of month the user named.
                val m = Regex("(\\d{1,2})\\s*-?\\s*(го|е|ого)?\\s*числ|(\\p{L}+?)(ого|ье)\\s+числ").find(source.lowercase())
                val day = m?.groupValues?.get(1)?.toIntOrNull() ?: m?.groupValues?.get(3)?.let { w -> ordinals.entries.firstOrNull { w.startsWith(it.key) }?.value }
                val base = due0 ?: day?.let { today.withDayOfMonth(it.coerceIn(1, today.lengthOfMonth())) } ?: today
                generateSequence(base) { r.next(it) }.first { !it.isBefore(today) }.takeIf { it != due0 }
            }
            else -> null
        } ?: return this
        val shift = due0?.let { java.time.temporal.ChronoUnit.DAYS.between(it, first) }
        return copy(due = first.toString(), start = if (shift != null) startDate?.plusDays(shift)?.toString() else null, remindAt = time?.let { "${first}T$it" })
    }

    private fun subtasks(el: JsonElement?): List<Subtask> =
        (el as? JsonArray)?.mapNotNull { e ->
            when (e) {
                is JsonObject -> {
                    val title = e.str("title")?.trim()?.trimEnd('.') ?: return@mapNotNull null
                    Subtask(title = title.cap(), assignee = e.str("assignee")?.takeIf { it.isNotBlank() }, children = subtasks(e["children"]))
                }
                is JsonPrimitive -> e.contentOrNull?.takeIf { it.isNotBlank() }?.let { Subtask(title = it.trim().cap()) }
                else -> null
            }
        }?.distinctBy { it.title.lowercase() } ?: emptyList()

    private fun repeat(el: JsonElement?): Repeat? {
        val o = el as? JsonObject ?: return null
        val freq = o.str("freq")?.lowercase()?.takeIf { it in setOf("daily", "weekly", "monthly", "yearly") } ?: return null
        val interval = (o["interval"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull()?.coerceIn(1, 365) ?: 1
        val days = (o["days"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.toIntOrNull()?.takeIf { d -> d in 1..7 } }?.distinct()?.sorted() ?: emptyList()
        return Repeat(freq, interval, if (freq == "weekly") days else emptyList())
    }

    private fun String.cap() = sentence()
    private fun flat(l: List<Subtask>): List<Subtask> = l.flatMap { listOf(it) + flat(it.children) }

    /** Keeps start <= due and drops a start equal to due. */
    private fun Task.normalized(): Task {
        var s = startDate; var e = dueDate
        if (s != null && e != null && s.isAfter(e)) { val x = s; s = e; e = x }
        if (s != null && s == e) s = null
        return copy(start = s?.toString(), due = e?.toString())
    }

    /** A timed task reminds at its time unless the user asked for something else. */
    fun withAutoReminder(t: Task): Task {
        if (t.remindAt != null || t.time == null) return t
        val day = t.dueDate ?: t.startDate ?: return t
        return t.copy(remindAt = "${day}T${t.time}")
    }

    // ---------------- json helpers ----------------

    private fun JsonObject.str(k: String): String? =
        (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull?.takeIf { it.isNotBlank() && it != "null" }

    private fun JsonObject.strList(k: String): List<String> = when (val v = this[k]) {
        is JsonArray -> v.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { s -> s.isNotEmpty() } }
        is JsonPrimitive -> v.contentOrNull?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
        else -> emptyList()
    }.distinct()

    private fun JsonObject.date(k: String): String? =
        str(k)?.take(10)?.let { runCatching { LocalDate.parse(it).toString() }.getOrNull() }

    private fun JsonObject.time(k: String): String? =
        str(k)?.let { runCatching { LocalTime.parse(it.padStart(5, '0').take(5)).toString() }.getOrNull() }

    private fun JsonObject.dateTime(k: String): String? =
        str(k)?.take(16)?.let { runCatching { LocalDateTime.parse(it).toString().take(16) }.getOrNull() }

    private fun JsonObject.project(): String? =
        str("project")?.trim()?.removePrefix("#")?.lowercase()?.takeIf { it.isNotBlank() }
}
