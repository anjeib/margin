package ru.anjeib.margin.data

/*
 * A matter remembers the person, not only the name (tester, 26 окт: two different Машас were one contact).
 * Each name in a matter is tied to a person id ([Task.who], [Subtask.who]): at once when only one person is called so,
 * by the user's answer to «Кто это — Маша?» when there are several.
 */

/** People a name could mean: called so, or so by first name («Маша» — «Маша Петрова» and «Маша Иванова»). */
fun AppData.peopleCalled(n: String): List<Person> =
    persons.filter { !it.archived && (it.matches(n) || it.name.substringBefore(' ').equals(n, true)) }

private fun Map<String, String>.tie(n: String) = this[n.lowercase()]?.takeIf { it.isNotEmpty() }

/** Does the name [n] in a matter tied as [who] mean this person? Untied — by the name, as before. */
fun Person.isIn(n: String, who: Map<String, String>) = who.tie(n)?.let { it == id } ?: matches(n)

/** The person a name in a matter stands for: the one it was tied to, else the one called so. */
fun AppData.personIn(n: String, who: Map<String, String>): Person? =
    who.tie(n)?.let { id -> persons.find { it.id == id } } ?: persons.find { it.matches(n) } ?: peopleCalled(n).singleOrNull()

/** «Кто это — Маша?»: which matter ([ref] — a matter's id or «q:тема:дело»), which name, and who it could be. */
data class WhoAsk(val ref: String, val title: String, val name: String, val ids: List<String>)

private val Task.names get() = (people + listOfNotNull(waitingFor, promisedTo)).distinct()
private val Subtask.names get() = (people + listOfNotNull(assignee, from, promisedTo)).distinct()

/** Ties every name of an open matter that only one person carries; several — left for [whoQuestion]. */
fun AppData.tieNames(): AppData {
    fun tied(who: Map<String, String>, names: List<String>): Map<String, String> {
        val add = names.filter { it.lowercase() !in who }.mapNotNull { n -> peopleCalled(n).singleOrNull()?.let { n.lowercase() to it.id } }
        return if (add.isEmpty()) who else who + add
    }
    fun subs(l: List<Subtask>): List<Subtask> {
        var any = false
        val out = l.map { q ->
            val w = if (q.done) q.who else tied(q.who, q.names)
            val ch = subs(q.children)
            if (w !== q.who || ch !== q.children) { any = true; q.copy(who = w, children = ch) } else q
        }
        return if (any) out else l
    }
    val ts = tasks.map { t ->
        if (t.done || t.sample) t
        else { val w = tied(t.who, t.names); val qs = subs(t.subtasks); if (w !== t.who || qs !== t.subtasks) t.copy(who = w, subtasks = qs) else t }
    }
    return if (ts.indices.any { ts[it] !== tasks[it] }) copy(tasks = ts) else this
}

/** The first open matter with a name several people carry and nobody said which: the user is asked once. */
fun AppData.whoQuestion(): WhoAsk? {
    fun ask(ref: String, title: String, who: Map<String, String>, names: List<String>) =
        names.firstOrNull { it.lowercase() !in who && peopleCalled(it).size > 1 }?.let { n -> WhoAsk(ref, title, n, peopleCalled(n).map { it.id }) }
    for (t in tasks) {
        if (t.done || t.sample) continue
        if (!t.topic) ask(t.id, t.title, t.who, t.names)?.let { return it }
        else for (q in t.subtasks) if (!q.done) ask("q:${t.id}:${q.id}", q.title, q.who, q.names)?.let { return it }
    }
    return null
}

/** The answer: [name] in the matter [ref] means [personId] ("" — the user doesn't say; not asked again). */
fun AppData.tieName(ref: String, name: String, personId: String): AppData {
    val k = name.lowercase()
    if (ref.startsWith("q:")) {
        val (tid, qid) = ref.removePrefix("q:").split(':').let { it[0] to it.getOrElse(1) { "" } }
        fun subs(l: List<Subtask>): List<Subtask> = l.map { q -> if (q.id == qid) q.copy(who = q.who + (k to personId)) else q.copy(children = subs(q.children)) }
        return copy(tasks = tasks.map { if (it.id == tid) it.copy(subtasks = subs(it.subtasks)) else it })
    }
    return copy(tasks = tasks.map { if (it.id == ref) it.copy(who = it.who + (k to personId)) else it })
}
