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
import ru.anjeib.margin.data.fullText
import ru.anjeib.margin.data.Note
import ru.anjeib.margin.data.Person
import ru.anjeib.margin.data.Repeat
import ru.anjeib.margin.data.logged
import ru.anjeib.margin.data.autoReminded
import ru.anjeib.margin.data.next
import ru.anjeib.margin.data.nextOccurrence
import ru.anjeib.margin.data.Settings
import ru.anjeib.margin.data.Subtask
import ru.anjeib.margin.data.Task
import ru.anjeib.margin.data.findSub
import ru.anjeib.margin.data.mapTree
import ru.anjeib.margin.data.nowStamp
import ru.anjeib.margin.data.address
import ru.anjeib.margin.data.properName
import ru.anjeib.margin.data.sentence
import ru.anjeib.margin.data.withNames
import ru.anjeib.margin.data.text
import ru.anjeib.margin.data.weekEnd
import ru.anjeib.margin.data.weekStart
import ru.anjeib.margin.data.withDone
import ru.anjeib.margin.data.isWait
import ru.anjeib.margin.i18n.L
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale

data class Question(val text: String, val options: List<String>)

/** [said] — the user's words with spelling and punctuation fixed by the model; shown as the task's origin. */
data class AiPlan(val reply: String?, val actions: List<JsonObject>, val question: Question?, val said: String? = null, val local: Boolean = false)

data class Applied(
    val data: AppData,
    val touchedTasks: List<String>,
    val touchedNotes: List<String>,
    val caption: String?,
    val created: Int,
    /** App settings the user asked for in words: name / morning / evening («HH:MM» or «off»). */
    val settings: Map<String, String> = emptyMap(),
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
        {"type":"create_task","title":"…","start":"YYYY-MM-DD","due":"YYYY-MM-DD","time":"HH:MM","part":"morning|day|afternoon|evening","remind_at":"YYYY-MM-DDTHH:MM","people":["…"],"project":"…","sphere":"personal|work","dates":[{"date":"YYYY-MM-DD","time":"HH:MM"}],"entry":"…","waiting_for":"…","promised_to":"…","place":"…","note_id":"…","repeat":{"freq":"weekly","interval":1,"days":[2]},"subtasks":[{"title":"…","due":"YYYY-MM-DD","time":"HH:MM","from":"…","assignee":"…","children":[…]}]}
        {"type":"add_subtasks","task_id":"…","parent_subtask_id":"…","subtasks":[{"title":"…","due":"YYYY-MM-DD","from":"…"}]}
        {"type":"update_task","task_id":"…","topic_title":"…", + only the changed fields of create_task}
        {"type":"add_entry","task_id":"…","text":"…"}   — a dated record on a task
        {"type":"complete_task","task_id":"…"}  {"type":"complete_subtask","task_id":"…","subtask_id":"…"}  {"type":"update_subtask","task_id":"…","subtask_id":"…","title":"…"}  {"type":"delete_subtask","task_id":"…","subtask_id":"…"}  {"type":"delete_task","task_id":"…"}
        {"type":"create_note","title":"…","text":"…","project":"…"}  {"type":"append_note","note_id":"…","text":"…"}  {"type":"update_note","note_id":"…","title":"…","project":"…"}
        {"type":"person","name":"…","org":true,"about":"…","company":"…","phone":"…","email":"…","telegram":"…","address":"…","personal":"…"}
        {"type":"settings","name":"…","morning":"HH:MM|off","evening":"HH:MM|off"}  — only when the user asks about the app itself
        repeat.freq: daily|weekly|monthly|yearly; days: 1–7, Monday=1.
    """.trimIndent()

    private fun rules(s: Settings) = buildString {
        val ideas = if (L.s.aiLanguage == "English") "ideas" else "идеи"
        appendLine("Rules:")
        appendLine("- Turn the user's free text into actions. Titles in the user's language: short, imperative, no final period.")
        appendLine("- Dates: take exact dates from the Calendar line. \"на этой неделе\" → start=today, due=this Sunday; \"next week\" → next Mon..Sun; \"this month\" → due=month end; \"к пятнице\" → due=that Friday; a weekday = its nearest future date. Times: \"в 15:00\" → time; \"утром / днём / после обеда / вечером\" without a clock time → part (morning/day/afternoon/evening), never a made-up time. Never invent precise deadlines; \"когда будет время\" = no dates.")
        appendLine("- Recurring (\"каждый вторник\", \"по будням\"=days 1–5) → repeat, due = first occurrence.")
        appendLine("- Phone calendar entries are busy time: never plan tasks over them.")
        appendLine("- remind_at only when the user asks to be reminded.")
        appendLine("- people: others involved; add \"${L.s.me}\" only when work is split with others.")
        appendLine("- dates: ONE matter on several separate days this time («тренировка в пн и чт», «звонить маме 12 и 15 октября», «во вторник и в пятницу в 10») → one create_task with dates (each day, its hour if said), not several tasks and not a repeat. «каждый пн и чт» / «по понедельникам» is a repeat. One day → due as usual.")
        appendLine("- sphere: work — the job, clients, colleagues, the user's business; personal — home, family, health, study, shopping, leisure. Set it on every create_task; change it only when the user says so.")
        appendLine("- project: when the text names a product, site, client or area (e.g. \"vuzburo\"), that name in lowercase is the project — even if it's new. Never attach an unrelated known project; no clear project → leave it out.")
        appendLine("- One message may hold several things: one action per thing. Concrete steps → subtasks (dictated steps verbatim, assignee if stated).")
        appendLine("- A matter named with its steps («Ремонт ванной: купить плитку до пятницы и вызвать плиточника») → ONE create_task titled by the matter, the steps as its subtasks, each with its own due if said; the matter is not a project.")
        appendLine("- Ideas, thoughts, opinions (\"идея:\", \"мне кажется\"), facts, phones, links are NOTES, never tasks or subtasks. append_note only to a listed note on the SAME topic/project; otherwise create_note with a short topical title (project ideas → \"<project> · $ideas\"). Never invent ids.")
        appendLine("- Someone owes the user something (\"Дима должен прислать макет в среду\") → a NEW task: title = what is expected, waiting_for = person, due = when expected (else +2 days). Never put waiting_for on an existing task.")
        appendLine("- \"заказать/купить/спросить у X\" is the user's own errand: X goes to people, not waiting_for.")
        appendLine("- A step with its own person AND its own deadline (\"Катя — макеты к четвергу\") → its own create_task (people, due, same project). Work split without its own deadline (\"Дима сделает дизайн\") stays a subtask with assignee.")
        appendLine("- The user promised something to someone (\"обещал Анне показать до пятницы\", \"договорились, что я пришлю Диме тексты\") → a task for the user with promised_to = that person (and due if said).")
        appendLine("- [Forwarded … message, written by X]: X's words, not the user's (lines may start with their author; «${L.s.me}» is the user). What X will do or send (\"пришлю\", \"сделаю к среде\") → a NEW task, title = what is expected, waiting_for = X, due = when X said (counted from the sent date). What X asks the user to do → a task for the user with people = [X]; promised_to = X only if the user agreed (their own line or words above the forward). The user's own lines («${L.s.me}: пришлю в пятницу») → promised_to = the other author. Plain information (addresses, links, prices, details) → a note, or person facts about X. Author unknown → never invent one.")
        appendLine("- Facts about a person → person action: about = their role in 2–4 words (\"дизайнер\"), company, phone/email/telegram/address if given; personal = a private fact (birthday, family, likes) in a few words. org = true when it is a business or an office, not a person (\"ЖЭК\", \"Сбербанк\", \"клиника на Ленина\", \"плиточная мастерская\").")
        appendLine("- place: where a task happens (address, venue) when said; not in the title.")
        appendLine("- News about an EXISTING listed task or topic (the same matter or person) → add_entry on it with the user's words, plus complete_subtask for every listed subtask the words settle (\"Игорь сказал, приказ подписан\" settles «Приказ (waiting for Игорь)»; \"документы отправил\" settles «Отправить документы»), add_subtasks for what is still to do (\"from\" = the person when someone else owes it). Never a new task for it. If that task is marked [simple], also update_task with \"topic_title\": the matter in 2–4 words, a noun phrase, not an action.")
        appendLine("- More work for an EXISTING listed task (same topic, \"ещё\", \"для …\") → add_subtasks to it; changed dates/people → update_task; done → complete. Don't duplicate tasks.")
        appendLine(if (s.autoSubtasks) "- A clearly big new task with no steps given → add 3–5 short subtasks." else "- Don't invent subtasks.")
        appendLine("- Never ask questions. A missing time or date is fine: create the task without it (the app offers to set it).")
        appendLine("- An answer to your earlier question → update_task on that task, no duplicate.")
        appendLine("- Change only what the user asked about: never touch other tasks, never add or alter dates, times or repeats nobody mentioned.")
        appendLine("- A question or a request for advice → answer in reply (2–4 short sentences, no markdown), actions only if asked. \"Что горит / что сорвётся\" → the 3–5 riskiest items (overdue, due within 3 days, waiting on others, your promises), each with why — not a plain list.")
        appendLine("- reply/question/options in ${L.s.aiLanguage}: brief, calm, no emoji, no exclamation marks.")
    }

    private fun scopeRule(scope: Scope) = when (scope) {
        is Scope.InTask -> "SCOPE: the user is inside task_id=${scope.taskId}. It is a topic: a matter that develops, a feed of what happened. Everything is about THIS task. " +
            "Words reporting what happened («Созвонились. Приказ будет через 2 недели, нужно отправить документы в бухгалтерию») → add_entry with the words (tidied, facts verbatim), then: " +
            "what the user still has to do → add_subtasks (a question; due/time only when said); what someone else will do or send → add_subtasks with \"from\" = that person and due = when expected (\"через 2 недели\" → that date); " +
            "a listed open subtask the words settle → complete_subtask; the task's own action happened (\"созвонились\" for «Позвонить …», \"отправил\" for «Отправить …») → complete_task (the app keeps the topic open). " +
            "If the task is marked [simple], also give update_task with \"topic_title\": what the matter is about in 2–4 words, a noun phrase («Приказ о зачислении»), never an action. " +
            "A change to a step («подзадача 2», «второй вопрос») → update_subtask / delete_subtask / complete_subtask with that subtask_id, never the task's own title; \"разбей на шаги\" alone → add_subtasks with 3–6 concrete steps. No other tasks unless asked."
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
        sc += 2 * (stems(t.title + " " + t.subtasks.joinToString(" ") { it.allText() } + " " + t.entries.takeLast(3).joinToString(" ") { it.fullText }) intersect q).size
        if (t.id in recent) sc += 3
        return sc
    }

    private fun StringBuilder.taskLine(t: Task) {
        append("[task_id=${t.id}] ${if (t.topic) "[topic] " else "[simple] "}${t.title}")
        val meta = listOfNotNull(t.start?.let { "start $it" }, t.due?.let { "due $it" }, t.time?.let { "at $it" }, t.project?.let { "#$it" },
            t.people.takeIf { it.isNotEmpty() }?.joinToString(), t.waitingFor?.let { "waiting for $it" }, t.promisedTo?.let { "promised to $it" }, t.noteId?.let { "note_id=$it" },
            t.repeat?.let { "repeats ${it.freq}" + (if (it.days.isNotEmpty()) " ${it.days}" else "") }, if (t.postponed >= 2) "postponed ${t.postponed}×" else null,
            t.slots.takeIf { it.isNotEmpty() }?.joinToString(prefix = "days ") { it.date + (it.time?.let { h -> " $h" } ?: "") + if (it.done) " done" else "" })
        if (meta.isNotEmpty()) append(" | " + meta.joinToString(", "))
        appendLine()
    }

    private fun StringBuilder.taskDetail(t: Task, entries: Int) {
        taskLine(t)
        t.entries.takeLast(entries).forEach { e -> e.fullText.takeIf { it.isNotBlank() }?.let { x -> appendLine("    · ${e.at.take(10)}: ${x.replace('\n', ' ').take(160)}") } }
        fun sub(list: List<Subtask>, depth: Int) {
            list.forEachIndexed { i, st ->
                // Top-level steps carry the number the user sees in the card («подзадача 2»).
                append("  ".repeat(depth + 1) + "- [subtask_id=${st.id}] " + (if (depth == 0) "${i + 1}. " else "") + st.title)
                st.assignee?.let { append(" ($it)") }
                st.from?.let { append(" (waiting for $it)") }
                st.due?.let { append(" due $it") }
                st.time?.let { append(" at $it") }
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
    fun context(d0: AppData, s: Settings, now: LocalDateTime = LocalDateTime.now(), focusTask: String? = null, query: String? = null, recent: Set<String> = emptySet()): String {
        // What is in the archive is put away: the AI doesn't append to it or bring it up.
        // v2: thoughts are shown to the model as the notes it knows (all but the one the user is inside).
        val d = ru.anjeib.margin.data.Topics.asNotes(d0, focusTask).let { x -> x.copy(notes = x.notes.filter { !it.archived }, tasks = x.tasks.filter { !(it.topic && it.done) }) }
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
                    val gist = (n.aiSummary ?: n.entries.takeLast(4).joinToString(" / ") { it.fullText }).replace('\n', ' ').take(260)
                    appendLine("[note_id=${n.id}] ${n.title.ifBlank { "—" }}${n.project?.let { " #$it" } ?: ""}: $gist")
                } else {
                    val q = stems(query)
                    val related = d.notes.map { n -> n to ((if (n.project != null && low!!.contains(n.project)) 5 else 0) + 2 * (stems(n.title + " " + n.text.take(600)) intersect q).size) }
                        .filter { it.second >= 2 }.sortedByDescending { it.second }.take(3).map { it.first }
                    related.forEach { n ->
                        appendLine("[note_id=${n.id}] ${n.title.ifBlank { "—" }}${n.project?.let { " #$it" } ?: ""}: " +
                            (n.aiSummary ?: n.entries.takeLast(3).joinToString(" / ") { it.fullText }).replace('\n', ' ').take(200))
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

    fun personPrompt(d: AppData, p: Person): String {
        val n = p.name
        val mentions = buildString {
            d.tasks.filter { t -> t.people.any { p.matches(it) } || t.waitingFor?.let { p.matches(it) } == true || t.promisedTo?.let { p.matches(it) } == true ||
                t.subtasks.any { s -> s.assignee?.let { p.matches(it) } == true } || t.title.contains(n, true) || t.entries.any { it.text.contains(n, true) } }
                .take(30).forEach { t ->
                    appendLine("- task: ${t.title}${if (t.dropped) " [dropped, not done]" else if (t.done) " [done ${t.doneAt}]" else t.due?.let { " [due $it]" } ?: ""}" +
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

    /** A business card's text → who it is. Only what the card says. */
    fun cardPrompt() = "This is the text of a business card. Reply with JSON only: {\"name\": full name, \"about\": job title, \"company\": …, \"phone\": …, \"email\": …, \"telegram\": …, \"address\": …}. " +
        "Leave out what the card doesn't say; never guess."

    /** The words a task was born from, tidied up for the card — a separate tiny request, once per task. */
    fun tidyPrompt() = "Fix spelling, capital letters (names, places) and punctuation in the user's note. Keep the words and their order, " +
        "do not add anything, keep the language. Reply with JSON only: {\"text\": string}"

    /** v2: a simple task grew into a topic — what is the matter, in a few words. */
    fun topicNamePrompt() = "A to-do grew into an ongoing matter. Name the matter in 2–4 words in the language of the text: a noun phrase about WHAT it is " +
        "(«Позвонить Игорю насчёт приказа о зачислении» + news → «Приказ о зачислении»; «Позвонить в ЖЭК насчёт счётчиков» → «Счётчики в ЖЭК»), never an action, no quotes. " +
        "Reply with JSON only: {\"title\": string}"

    /** One idea → exactly one task linked to its note. */
    fun ideaPrompt(noteId: String, text: String, noteTitle: String? = null) =
        "Turn this idea into exactly ONE concrete task (create_task with note_id=$noteId; add subtasks only if obvious, people/dates only if stated). Do not create notes. " +
            "The title must make sense on its own: when the idea doesn't name its subject, take it from the note title, in the right case (idea «Посмотреть» in the note «Одиссея» → «Посмотреть «Одиссею»»)." +
            "\n\n" + (noteTitle?.let { "Note: $it\n" } ?: "") + "Idea: $text"

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

    private val actionTypes = setOf("create_task", "add_subtasks", "update_task", "add_entry", "complete_task", "complete_subtask", "update_subtask", "delete_subtask",
        "delete_task", "create_note", "append_note", "update_note", "person", "settings")

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
        return AiPlan(o.str("reply"), actions, q, o.str("said"))
    }

    fun field(o: JsonObject?, k: String): String? = o?.str(k)

    // ---------------- applying ----------------

    /**
     * [source] — what the user actually wrote; used to recover a project the model forgot to name.
     * [strict] — the user typed this: the model may change only what the words are about ([focus] — the open task,
     * [recent] — tasks of the last exchanges, for «перенеси его»). Unasked dates, times, repeats and other tasks stay as they were.
     */
    fun apply(data: AppData, plan: AiPlan, source: String = "", strict: Boolean = false, focus: String? = null, recent: Set<String> = emptySet()): Applied {
        // Thoughts go through the note logic the model was taught, then come back as thoughts.
        var d = ru.anjeib.margin.data.Topics.asNotes(data, focus)
        val touched = linkedSetOf<String>()
        val touchedNotes = linkedSetOf<String>()
        var created = 0; var updated = 0; var subAdded = 0; var notes = 0; var entries = 0; var completed = 0
        val prefs = mutableMapOf<String, String>()
        // Who did it, for the task's history: a typed command is the user's own hand; the rest is the AI.
        val by = if (plan.local) "you" else "ai"
        // The weekday check only makes sense when one dated thing was said; several things share the words.
        val oneDate = plan.actions.count { it.str("due") != null || it.str("start") != null } == 1
        val noteActs = plan.actions.count { it.str("type") == "create_note" || it.str("type") == "append_note" }
        val me = setOf("я", "me", L.s.me.lowercase())
        val said = stems(source)
        val low = source.lowercase()
        val saysWhen = strict.not() || whenCue.containsMatchIn(low)
        val saysTime = strict.not() || timeCue.containsMatchIn(low)
        // A clock time was said («в 15», «15:30», «в 9 утра») — not only «утром».
        val saysClock = strict.not() || clockCue.containsMatchIn(low)
        val saysRepeat = strict.not() || repeatCue.containsMatchIn(low)
        val saysOwe = strict.not() || oweCue.containsMatchIn(low)
        val bulk = strict.not() || bulkCue.containsMatchIn(low)
        /** Is this existing task what the user is talking about? */
        // Inside a topic only that topic changes: a phrase that happens to match another one's question is still about this one.
        fun asked(t: Task): Boolean = if (strict && focus != null) t.id == focus else bulk || t.id == focus || t.id in recent ||
            (stems(t.title + " " + t.people.joinToString(" ") + " " + listOfNotNull(t.waitingFor, t.promisedTo, t.project).joinToString(" ") +
                // A topic is also named by its open questions: «документы отправил» is about «Отправить документы».
                (if (t.topic) " " + t.subtasks.filter { !it.done }.joinToString(" ") { it.title + " " + (it.from ?: "") } else "")) intersect said).isNotEmpty()
        /** One spelling per person: the known one, or the name with a capital letter. */
        fun who(n: String?): String? = n?.trim()?.takeIf { it.isNotEmpty() }?.let { x -> if (x.lowercase() in me) x else d.person(x)?.name ?: x.properName() }
        val origin = source.trim().take(240).ifBlank { null }?.let { raw ->
            val clean = plan.said?.trim()?.takeIf { s0 -> s0.length in (raw.length * 6 / 10)..(raw.length * 16 / 10 + 10) && (stems(s0) intersect stems(raw)).size * 2 >= stems(raw).size }
            (clean ?: raw.sentence()).withNames(d.persons.map { it.name })
        }
        fun subs(l: List<Subtask>, names: List<String> = emptyList()): List<Subtask> = l.map { it.copy(title = it.title.withNames(names + d.persons.map { p -> p.name }), assignee = who(it.assignee), children = subs(it.children, names)) }

        fun learn(people: List<String>, project: String?) {
            val newPeople = people.distinct().filter { it.lowercase() !in me && d.person(it) == null }.map { Person(name = it.properName(), draft = true) }
            val newProject = project?.takeIf { p -> d.projects.none { it.equals(p, true) } }
            if (newPeople.isNotEmpty() || newProject != null)
                d = d.copy(persons = d.persons + newPeople, projects = d.projects + listOfNotNull(newProject))
        }
        fun validNote(id: String?) = id?.takeIf { n -> d.notes.any { it.id == n } }

        // «Done» goes last: adding a step reopens a task, and «созвонились, теперь нужно…» means the call is done all the same.
        var wordsKept = false
        // «Приказ о зачислении: позвонить Игорю завтра и отправить документы» — a matter named before the colon, its steps after:
        // one topic with those steps as questions, even when the model made separate tasks of them.
        val matter = Regex("^\\s*([^:\\d\\n]{3,40}?)\\s*:\\s*\\S").find(source)?.groupValues?.get(1)?.trim()
            ?.takeIf { m -> !Commands.isCommandWord(m) && Regex("(?i)^(идея|мысль|заметка|note|idea|важно|напоминание)").find(m) == null }
        val steps = plan.actions.filter { it.str("type") == "create_task" }
        val actions0 = if (strict && focus == null && matter != null && steps.size >= 2 && steps.none { it.str("title").equals(matter, true) } &&
            steps.none { it.str("waiting_for") != null || it.str("promised_to") != null }) {
            val merged = JsonObject(mapOf(
                "type" to JsonPrimitive("create_task"), "title" to JsonPrimitive(matter.sentence()),
                "subtasks" to JsonArray(steps.map { st -> JsonObject(listOfNotNull("title" to (st["title"] ?: JsonPrimitive("")),
                    st["due"]?.let { "due" to it }, st["time"]?.let { "time" to it }).toMap()) }),
            ) + listOfNotNull(steps.firstNotNullOfOrNull { it["project"] }?.let { "project" to it }, steps.firstNotNullOfOrNull { it["people"] }?.let { "people" to it }))
            plan.actions.filterNot { it in steps } + merged
        } else plan.actions
        for (a in actions0.sortedBy { if (it.str("type") == "complete_task") 1 else 0 }) {
            when (a.str("type")) {
                "create_task" -> {
                    val title = a.str("title")?.trim()?.trimEnd('.')?.cap() ?: continue
                    // Inside a topic a «new task» is its next question, unless the user asked for a separate one.
                    if (strict && focus != null && !newTaskCue.containsMatchIn(low) && d.tasks.any { it.id == focus }) {
                        val q = Subtask(title = title, due = a.date("due") ?: a.date("start"), time = a.time("time"), slots = a.slots(),
                            from = a.str("waiting_for")?.let { who(it) }?.takeIf { it.lowercase() !in me })
                        d = d.copy(tasks = d.tasks.map { if (it.id == focus) it.copy(subtasks = it.subtasks + q, done = false) else it })
                        // The words themselves belong in the feed even when the model forgot them.
                        if (!wordsKept && plan.actions.none { it.str("type") == "add_entry" } && origin != null) {
                            d = d.copy(tasks = d.tasks.map { if (it.id == focus) it.copy(entries = it.entries + Entry(text = origin)) else it })
                            wordsKept = true
                        }
                        touched += focus; subAdded++
                        continue
                    }
                    // The same open task already exists — small models re-create what they see in the context.
                    // Same words with a new date or a new promise is a new thing, not a copy.
                    if (strict && d.tasks.any { o -> !o.done && o.title.trim().trimEnd('.').equals(title, true) &&
                            (a.date("due") == null || a.date("due") == o.due) && a.str("promised_to") == null && a.str("waiting_for") == null }) continue
                    val entryText = a.str("entry") ?: a.str("comment")
                    val raw = withAutoReminder(
                        Task(
                            title = title,
                            start = a.date("start"),
                            due = if (strict && oneDate) onSaidWeekday(a.date("due"), source.lowercase()) else a.date("due"),
                            time = a.time("time"),
                            remindAt = a.dateTime("remind_at"),
                            people = a.strList("people"),
                            project = a.project(),
                            sphere = a.sphere(),
                            topic = a.str("topic") == "true",
                            // Days and a repeat both sent: the words decide — «каждый / по понедельникам» is a repeat, otherwise these days.
                            slots = if (everyCue.containsMatchIn(low)) emptyList() else a.slots(),
                            entries = listOfNotNull(entryText?.let { Entry(text = it.sentence()) }),
                            waitingFor = a.str("waiting_for"),
                            promisedTo = a.str("promised_to"),
                            place = a.str("place")?.address(),
                            source = origin,
                            // The model links tasks to whatever note it sees in the context; keep the link only when they share words.
                            noteId = validNote(a.str("note_id"))?.takeIf { nid -> !strict || d.notes.first { it.id == nid }.let { n ->
                                (stems(n.title + " " + n.text.take(600)) intersect stems(title + " " + source)).isNotEmpty() } },
                            repeat = repeat(a["repeat"])?.takeIf { a.slots().isEmpty() || everyCue.containsMatchIn(low) },
                            subtasks = subtasks(a["subtasks"]),
                        ).normalized()
                    )
                    // «заказать муку у Тимура» is the user's own errand, not something Тимур owes.
                    val errand = raw.waitingFor != null && !saysOwe && errandCue.containsMatchIn(low)
                    val involved = (raw.people + listOfNotNull(raw.waitingFor, raw.promisedTo)).mapNotNull { who(it) }
                    val t = raw.copy(
                        title = raw.title.withNames(involved),
                        time = raw.time?.takeIf { saysTime && saysClock }, remindAt = raw.remindAt?.takeIf { raw.time == null || (saysTime && saysClock) || "remind_at" in a },
                        dayPart = if (saysClock) null else (a.str("part")?.takeIf { it in dayParts } ?: partOf(low)),
                        people = ((raw.people + listOfNotNull(raw.waitingFor.takeIf { errand })).mapNotNull { who(it) }).distinct(),
                        waitingFor = if (errand) null else who(raw.waitingFor), promisedTo = who(raw.promisedTo), subtasks = subs(raw.subtasks, involved),
                    ).onSchedule(source).let { if (it.time == null && raw.time != null) it.copy(remindAt = null) else it }
                    learn(t.people + t.subtasks.mapNotNull { it.assignee } + listOfNotNull(t.waitingFor, t.promisedTo), t.project)
                    d = d.copy(tasks = d.tasks + t.logged(null, by))
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
                    val timed = has("time", saysTime && saysClock)
                    val parted = !saysClock && (a.str("part") in dayParts || partOf(low) != null) && saysTime
                    var next = withAutoReminder(
                        old.copy(
                            // «измени подзадачу…» is about a step: the task keeps its name.
                            title = a.str("topic_title")?.takeIf { !old.topic }?.trim()?.trimEnd('.')?.sentence()
                                ?: if ("title" in a && !(strict && stepTalk.containsMatchIn(low))) a.str("title")?.trim()?.trimEnd('.')?.sentence()?.withNames(old.people + listOfNotNull(old.waitingFor, old.promisedTo)) ?: old.title else old.title,
                            start = if (has("start", saysWhen)) a.date("start") else old.start,
                            due = if (has("due", saysWhen)) (if (strict && oneDate) onSaidWeekday(a.date("due"), low) else a.date("due")) else old.due,
                            time = if (timed) a.time("time") else if (parted) null else old.time,
                            dayPart = if (timed) null else if (parted) (a.str("part")?.takeIf { it in dayParts } ?: partOf(low)) else old.dayPart,
                            remindAt = if ("remind_at" in a) a.dateTime("remind_at") else if (dated || timed) null else old.remindAt,
                            people = if ("people" in a) a.strList("people").mapNotNull { who(it) }.distinct() else old.people,
                            project = if ("project" in a) a.project() else old.project,
                            sphere = if ("sphere" in a) (a.sphere() ?: old.sphere) else old.sphere,
                            // New days replace the old ones; a day already ticked stays ticked.
                            slots = if ("dates" in a) a.slots().map { n -> old.slots.find { it.date == n.date && it.time == n.time } ?: n } else old.slots,
                            waitingFor = newWait,
                            promisedTo = newPromise,
                            place = if ("place" in a) a.str("place")?.address() else old.place,
                            noteId = if ("note_id" in a) validNote(a.str("note_id")) else old.noteId,
                            repeat = if (has("repeat", saysRepeat)) repeat(a["repeat"]) else old.repeat,
                        ).normalized()
                    )
                    // A topic has no date of its own: a new date moves the one matter the words are about — the one owed by the person
                    // named, or the one whose words they repeat, or the only open one. Several and none named: nothing moves
                    // (review 0.13, defect 4: «спецификацию пришлёт в понедельник» moved another matter of the topic).
                    if (old.topic && (next.due != old.due || next.time != old.time || next.start != old.start)) {
                        val q = matterOf(old, low, a.str("waiting_for"), listOfNotNull(a.str("topic_title"), a.str("title")))
                        val moved = next
                        next = next.copy(due = null, start = null, time = null, remindAt = null)
                        if (q != null) next = next.copy(subtasks = next.subtasks.mapTree(q.id) { it.copy(due = moved.due ?: moved.start ?: it.due, time = if (moved.time != old.time) moved.time else it.time) })
                    }
                    // «Дима пришлёт…» about a topic: the wait lives on its matter, not on the topic.
                    if (old.topic && next.waitingFor != old.waitingFor && old.subtasks.any { !it.done && it.from != null && next.waitingFor?.let { w -> sameName(it.from, w) } == true })
                        next = next.copy(waitingFor = old.waitingFor)
                    // A new promise or expectation is a thing of its own: it never turns a recurring task into a one-off.
                    val split = old.repeat != null && ((newPromise != null && old.promisedTo == null) || (newWait != null && old.waitingFor == null))
                    if (split) {
                        next = next.copy(id = ru.anjeib.margin.data.newId(), repeat = null, entries = emptyList(), subtasks = emptyList(), createdAt = LocalDate.now().toString(), source = origin)
                        d = d.copy(tasks = d.tasks + next.copy(history = emptyList()).logged(null, by))
                        created++
                    } else {
                        if (dated && next.repeat != null) next = next.onSchedule(source)
                        d = d.copy(tasks = d.tasks.map { if (it.id == id) next.logged(old, by) else it })
                        updated++
                    }
                    // Small models put steps on update_task itself instead of add_subtasks.
                    subtasks(a["subtasks"]).takeIf { it.isNotEmpty() && !split }?.let { st ->
                        // A step that already exists, sent again with a date the words gave: that step moves (it is not new).
                        if (saysWhen) st.forEach { n ->
                            val nd = n.due ?: return@forEach
                            val same = d.tasks.first { it.id == id }.subtasks.firstOrNull { !it.done && it.title.equals(n.title, true) } ?: return@forEach
                            d = d.copy(tasks = d.tasks.map { t -> if (t.id != id) t else t.copy(subtasks = t.subtasks.mapTree(same.id) { it.copy(due = nd, time = n.time ?: it.time) }) })
                        }
                        val have = d.tasks.first { it.id == id }.subtasks.map { it.title.lowercase() }.toSet()
                        val fresh = subs(st, next.people).filter { it.title.lowercase() !in have }
                        if (fresh.isNotEmpty()) d = d.copy(tasks = d.tasks.map { if (it.id == id) it.copy(subtasks = it.subtasks + fresh, done = false) else it })
                    }
                    // Small models put «не забыть полис» on update_task itself — it is a record of that task.
                    listOfNotNull(a.str("entry"), a.str("text")).distinct().filter { it.isNotBlank() && !split }.forEach { x ->
                        d = d.copy(tasks = d.tasks.map { if (it.id == id) it.copy(entries = it.entries + Entry(text = x.sentence())) else it })
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
                    // A topic is not ticked off by words: they settle its one open question; the archive is the user's call.
                    if (target.topic) {
                        val only = target.subtasks.filter { !it.done }.singleOrNull() ?: continue
                        d = d.copy(tasks = d.tasks.map { if (it.id == id) it.copy(subtasks = it.subtasks.mapTree(only.id) { s -> s.withDone(true).copy(doneAt = LocalDate.now().toString()) }) else it })
                        touched += id; updated++
                        continue
                    }
                    val next = target.nextOccurrence()
                    d = d.copy(tasks = d.tasks.map { if (it.id == id) it.copy(done = true, doneAt = LocalDate.now().toString()).logged(it, by) else it } + listOfNotNull(next?.copy(history = emptyList())?.logged(null, by)))
                    touched += id; completed++
                }
                "update_subtask", "delete_subtask" -> {
                    val id = a.str("task_id") ?: continue
                    val sid = a.str("subtask_id") ?: continue
                    val target = d.tasks.find { it.id == id } ?: continue
                    if (target.subtasks.findSub(sid) == null || (strict && !asked(target))) continue
                    val title = a.str("title")?.trim()?.trimEnd('.')?.sentence()
                    if (a.str("type") == "update_subtask" && title == null) continue
                    d = d.copy(tasks = d.tasks.map { t0 -> if (t0.id != id) t0 else t0.copy(subtasks = t0.subtasks.mapTree(sid) { s -> if (title != null) s.copy(title = title) else null }) })
                    touched += id; updated++
                }
                "complete_subtask" -> {
                    val id = a.str("task_id") ?: continue
                    val sid = a.str("subtask_id") ?: continue
                    if (strict && d.tasks.find { it.id == id }?.let { asked(it) } != true) continue
                    d = d.copy(tasks = d.tasks.map { if (it.id == id) it.copy(subtasks = it.subtasks.mapTree(sid) { s -> s.withDone(true).copy(doneAt = LocalDate.now().toString()) }) else it })
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
                        // A short note is its own title («Посмотреть Одиссею»); a long one gets a title and keeps its text inside.
                        val said = (Commands.parse(source)?.takeIf { it.what == "note" }?.value ?: source.replace(noteMarkers, "")).trim().trimStart(':', '—', '-', ' ').trim()
                        val whole = when {
                            lazyTitle && project != null -> null // a project idea opens that project's ideas note
                            noteActs == 1 && said.length in 2..60 && '\n' !in said -> said
                            text.length in 2..60 && '\n' !in text && lazyTitle -> text
                            else -> null
                        }
                        if (whole != null) {
                            val n = Note(title = whole.sentence(), project = project)
                            learn(emptyList(), n.project)
                            d = d.copy(notes = listOf(n) + d.notes)
                            touchedNotes += n.id; notes++
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
                "settings" -> {
                    a.str("name")?.let { prefs["name"] = it.trim() }
                    a.str("morning")?.let { prefs["morning"] = it.trim().lowercase() }
                    a.str("evening")?.let { prefs["evening"] = it.trim().lowercase() }
                }
                "person" -> {
                    val name = a.str("name")?.properName() ?: continue
                    if (name.lowercase() in me) continue
                    val existing = d.person(name)
                    // Learned in passing → a draft the bubble offers to keep; «Добавить контакт» (keep) → a contact at once.
                    val keep = a.str("keep") == "true"
                    val p = (existing ?: Person(name = name, draft = true)).let {
                        it.copy(
                            draft = if (keep) false else it.draft,
                            kept = it.kept || keep,
                            about = a.str("about") ?: it.about,
                            aboutFromUser = it.aboutFromUser || a.str("about") != null,
                            phone = a.str("phone") ?: it.phone,
                            email = a.str("email") ?: it.email,
                            company = a.str("company") ?: it.company,
                            org = if ("org" in a) a.str("org") == "true" else it.org,
                            telegram = a.str("telegram")?.trim()?.removePrefix("https://t.me/")?.removePrefix("@")?.let { t0 -> "@$t0" } ?: it.telegram,
                            address = a.str("address")?.address() ?: it.address,
                            personal = (it.personal + listOfNotNull(a.str("personal")?.sentence())).distinctBy { p0 -> p0.lowercase() },
                            updatedAt = nowStamp(),
                        )
                    }
                    d = d.copy(persons = if (existing == null) d.persons + p else d.persons.map { if (it.id == p.id) p else it })
                }
            }
        }
        // Inside a task the model settled or renamed it but kept no words: the report itself is the record (and grows the topic).
        if (strict && focus != null && origin != null && plan.actions.none { it.str("type") == "add_entry" && it.str("task_id") == focus } &&
            plan.actions.any { (it.str("type") == "complete_task" || it.str("topic_title") != null || it.str("type") == "add_subtasks") && it.str("task_id") == focus }) {
            val t = d.tasks.find { it.id == focus }
            val o = data.tasks.find { it.id == focus }
            if (t != null && o != null && t.entries.size == o.entries.size && !wordsKept)
                d = d.copy(tasks = d.tasks.map { if (it.id == focus) it.copy(entries = it.entries + Entry(text = origin)) else it })
        }
        // «Сказали прийти…», «Созвонились…» inside a simple task: its own call or message has happened, even if the model didn't say so.
        if (strict && focus != null && plan.actions.none { it.str("type") == "complete_task" }) {
            val t = d.tasks.find { it.id == focus }
            val o = data.tasks.find { it.id == focus }
            // Only the words that report THIS kind of action: a call is settled by «созвонились / сказали», a sending only by «отправил».
            val happened = t != null && reportFor.any { (action, report) -> action.containsMatchIn(t.title.lowercase()) && report.containsMatchIn(low) }
            if (t != null && o != null && happened && !t.topic && !t.done && t.repeat == null && (t.entries.size > o.entries.size || t.subtasks.size > o.subtasks.size))
                d = d.copy(tasks = d.tasks.map { if (it.id == focus) it.copy(done = true, doneAt = LocalDate.now().toString()) else it })
        }
        // News from the main screen that settled or added a topic's question: the words go into its feed too.
        if (strict && focus == null && origin != null) {
            val moved = plan.actions.filter { it.str("type") in setOf("complete_subtask", "add_subtasks") }.mapNotNull { it.str("task_id") }.toSet()
            val written = plan.actions.filter { it.str("type") == "add_entry" }.mapNotNull { it.str("task_id") }.toSet()
            (moved - written).filter { id -> id in touched && d.tasks.any { it.id == id && it.topic } }.forEach { id ->
                d = d.copy(tasks = d.tasks.map { if (it.id == id) it.copy(entries = it.entries + Entry(text = origin)) else it })
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
            prefs.isNotEmpty() -> s.settingsChanged
            else -> null
        }
        d = ru.anjeib.margin.data.Topics.thoughts(d, data)
        val thoughtIds = touchedNotes.filter { id -> d.tasks.any { it.id == id } }
        return Applied(d, (touched + thoughtIds).toList().filter { id -> d.tasks.any { it.id == id } }, touchedNotes.toList(), caption, created, prefs)
    }

    // What the user's words are about — so the model can't quietly change what wasn't mentioned.
    private val whenCue = Regex("сегодн|завтра|вчера|понедельн|вторник|сред[аеуы]|четверг|пятниц|суббот|воскресен|\\b(пн|вт|ср|чт|пт|сб|вс)\\b|числ|недел|месяц|январ|феврал|март|апрел|ма[йя]\\b|июн|июл|август|сентябр|октябр|ноябр|декабр|через|после|\\bдо\\b|\\bк\\s|перенес|отлож|сдвин|срок|дат|\\d{1,2}[./]\\d{1,2}|\\d{1,2}\\s*-?\\s*(го|е|ого)\\b|\\d{1,2}\\s+(янв|фев|мар|апр|мая|июн|июл|авг|сен|окт|ноя|дек)|today|tomorrow|monday|tuesday|wednesday|thursday|friday|saturday|sunday|week|month|next|until|\\bby\\b|move|postpone|date|deadline")
    private val clockCue = Regex("\\d{1,2}[:.]\\d{2}|\\bв\\s?\\d{1,2}\\b|\\b\\d{1,2}\\s*(ч|час)|полдень|полноч|\\b\\d{1,2}\\s?(am|pm)\\b|\\bat\\s?\\d|o'clock")
    /** The one weekday named in the words («до среды», «в пятницу», «by Friday»), if exactly one is. */
    private val weekdayWords = listOf("понедельник|monday" to 1, "вторник|tuesday" to 2, "сред[аеуы]|wednesday" to 3, "четверг|thursday" to 4,
        "пятниц|friday" to 5, "суббот|saturday" to 6, "воскресен|sunday" to 7).map { (r, d) -> Regex(r) to d }
    private fun weekdayOf(low: String): Int? {
        if (Regex("следующ|через\\s+недел|next\\s+week|каждый|кажд|по\\s+(понедельн|вторник|сред|четверг|пятниц|суббот|воскресен)").containsMatchIn(low)) return null
        return weekdayWords.filter { it.first.containsMatchIn(low) }.map { it.second }.distinct().singleOrNull()
    }
    /** Small models miscount weekdays: the date has to fall on the weekday the user said. */
    /** Same person in any case: «Дима» / «Димой» / «Диме» — by the stem. */
    private fun sameName(a: String, b: String): Boolean {
        val x = a.lowercase().trim(); val y = b.lowercase().trim()
        val n = minOf(x.length, y.length).coerceAtMost(4) - 1
        return n >= 2 && x.take(n) == y.take(n)
    }

    /** The matter of [topic] these words are about (see update_task), or null when it can't be told. */
    private fun matterOf(topic: Task, low: String, waitFor: String?, titles: List<String>): ru.anjeib.margin.data.Subtask? {
        val open = flat(topic.subtasks).filter { !it.done }
        if (open.isEmpty()) return null
        // Owed by the person the words name.
        val named = listOfNotNull(waitFor) + Regex("\\p{Lu}\\p{L}{2,}").findAll(titles.joinToString(" ") + " " + low.replaceFirstChar { it.uppercase() }).map { it.value }
        open.filter { q -> q.from != null && (named.any { sameName(q.from, it) } || low.contains(q.from.lowercase().take(3))) }.singleOrNull()?.let { return it }
        // The words repeat its words («спецификацию» → «Спецификация API»).
        fun stems(t: String) = Regex("\\p{L}{4,}").findAll(t.lowercase()).map { it.value.take(if (it.value.length > 6) 6 else it.value.length - 1) }.toSet()
        val said = stems(low + " " + titles.joinToString(" "))
        val scored = open.map { it to stems(it.title).count { st -> st in said } }.filter { it.second > 0 }
        val best = scored.maxOfOrNull { it.second }
        scored.filter { it.second == best }.singleOrNull()?.let { return it.first }
        return open.filter { !it.isWait }.singleOrNull() ?: open.singleOrNull()
    }

    private fun onSaidWeekday(date: String?, low: String): String? {
        val d = date?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return date
        val wd = weekdayOf(low) ?: return date
        if (d.dayOfWeek.value == wd) return date
        return LocalDate.now().with(java.time.temporal.TemporalAdjusters.nextOrSame(java.time.DayOfWeek.of(wd))).toString()
    }
    /** What a task asks to do → the words that say it was done. */
    private val reportFor = listOf(
        Regex("^(позвонить|созвониться|набрать|перезвонить|связаться|call|phone)") to Regex("созвонил|дозвонил|поговорил|сказал|ответил|перезвонил|звонил|called|talked|spoke|they said"),
        Regex("^(спросить|узнать|уточнить|выяснить|ask|find out|check)") to Regex("сказал|ответил|узнал|выяснил|уточнил|said|answered|found out|told"),
        Regex("^(написать|email|text|write|message)") to Regex("написал|ответил|wrote|replied|texted|emailed"),
        Regex("^(отправить|выслать|переслать|send)") to Regex("отправил|выслал|переслал|\\bsent\\b"),
        Regex("^(оплатить|заплатить|pay)") to Regex("оплатил|заплатил|\\bpaid\\b"),
        Regex("^(встретиться|meet)") to Regex("встретил|\\bmet\\b"),
        Regex("^(сходить|съездить|записаться|go to|visit)") to Regex("сходил|съездил|записал|\\bwent\\b|visited|были у|был у"),
        Regex("^(договориться|agree)") to Regex("договорил|agreed"),
        Regex("^(сдать|отдать|hand)") to Regex("сдал|отдал|handed"),
    )
    private val newTaskCue = Regex("отдельн|нов(ую|ая) задач|созда(й|ть) задач|ещё задач|еще задач|separate|new task|create a task")
    private val stepTalk = Regex("подзадач|шаг[аеиу]?\\b|пункт|subtask|step")
    private val dayParts = setOf("morning", "day", "afternoon", "evening", "night")
    private fun partOf(low: String): String? = when {
        Regex("утр|morning").containsMatchIn(low) -> "morning"
        Regex("после обеда|после обед|afternoon").containsMatchIn(low) -> "afternoon"
        Regex("вечер|evening").containsMatchIn(low) -> "evening"
        Regex("ночь|ночью|night").containsMatchIn(low) -> "night"
        Regex("днём|днем|during the day").containsMatchIn(low) -> "day"
        else -> null
    }
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
                    Subtask(title = title.cap(), assignee = e.str("assignee")?.takeIf { it.isNotBlank() }, children = subtasks(e["children"]),
                        due = e.date("due"), time = e.time("time"), slots = e.slots(), from = (e.str("from") ?: e.str("waiting_for"))?.takeIf { it.isNotBlank() && it.lowercase() !in setOf("я", "me") }?.properName())
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
    fun withAutoReminder(t: Task): Task = t.autoReminded()

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

    /** Words that make several weekdays a repeat, not this week's days. */
    private val everyCue = Regex("кажд|ежедневн|еженедел|по (понедельник|вторник|сред|четверг|пятниц|суббот|воскресень)\\p{L}*ам|every|daily|weekly|on (mondays|tuesdays|wednesdays|thursdays|fridays|saturdays|sundays)", RegexOption.IGNORE_CASE)

    /** «dates»: two or more days of one matter; a single day stays a plain due date. */
    private fun JsonObject.slots(): List<ru.anjeib.margin.data.Slot> = ((this["dates"] as? JsonArray)?.mapNotNull { e ->
        when (e) {
            is JsonObject -> e.date("date")?.let { ru.anjeib.margin.data.Slot(date = it, time = e.time("time")) }
            is JsonPrimitive -> e.contentOrNull?.let { runCatching { LocalDate.parse(it.take(10)).toString() }.getOrNull() }?.let { ru.anjeib.margin.data.Slot(date = it) }
            else -> null
        }
    } ?: emptyList()).distinctBy { it.date + "|" + it.time }.takeIf { it.size >= 2 } ?: emptyList()

    private fun JsonObject.sphere(): String? = str("sphere")?.trim()?.lowercase()?.takeIf { it == "personal" || it == "work" }

    private fun JsonObject.project(): String? =
        str("project")?.trim()?.removePrefix("#")?.lowercase()?.takeIf { it.isNotBlank() }
}
