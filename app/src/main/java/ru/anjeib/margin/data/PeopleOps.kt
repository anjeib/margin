package ru.anjeib.margin.data

/* People and companies: what the buttons do to them, as plain (AppData, args) -> AppData. */

fun AppData.editPerson(id: String, f: (Person) -> Person) = copy(persons = persons.map { if (it.id == id) f(it) else it })

/** «@маша» → the contact; «@[Кофейня «Зерно»]» — chosen; someone new keeps the name as written. */
fun AppData.personFor(token: String): String {
    val n = token.trim('[', ']').replace('_', ' ')
    val people = persons.filter { !it.archived }
    return people.find { it.matches(n) }?.name ?: people.firstOrNull { it.name.lowercase().startsWith(n.lowercase()) }?.name ?: n.properName()
}

/** «+ контакт»: a draft (or a bare name) becomes a contact. */
fun AppData.keepContact(name: String): AppData {
    val known = person(name)
    return if (known != null) copy(persons = persons.map { if (it.id == known.id) it.copy(draft = false, kept = true, archived = false) else it })
    else copy(persons = persons + Person(name = name.properName(), kept = true))
}

/** A picked phone contact is remembered on the person it names (or becomes one). */
fun AppData.rememberContact(c: ContactInfo): AppData {
    val first = c.name.substringBefore(' ')
    val existing = person(c.name) ?: person(first)
    return if (existing != null) copy(persons = persons.map {
        if (it.id == existing.id) it.copy(draft = false, kept = true, phone = it.phone ?: c.phone, email = it.email ?: c.email, aliases = (it.aliases + c.name).distinct().filterNot { a -> a.equals(it.name, true) }) else it
    })
    else copy(persons = persons + Person(name = c.name, phone = c.phone, email = c.email, kept = true))
}

fun AppData.archivePerson(id: String, on: Boolean = true) = editPerson(id) { it.copy(archived = on) }

fun AppData.removePerson(id: String) = copy(persons = persons.filterNot { it.id == id })

fun AppData.setOrg(personId: String, org: Boolean) = editPerson(personId) { it.copy(org = org) }

/** One contact or fact of a person, typed by hand. «personal» takes one fact per line. */
fun AppData.setPersonField(id: String, field: String, value: String) = editPerson(id) { p ->
    val v = value.trim().ifBlank { null }
    when (field) {
        "about" -> p.copy(about = v, aboutFromUser = v != null)
        "company" -> p.copy(company = v)
        "phone" -> p.copy(phone = v)
        "email" -> p.copy(email = v)
        "address" -> p.copy(address = v?.address())
        "max" -> p.copy(max = v)
        "telegram" -> p.copy(telegram = v?.removePrefix("https://t.me/")?.removePrefix("@")?.let { "@$it" })
        "personal" -> p.copy(personal = value.lines().map { it.trim() }.filter { it.isNotEmpty() }.map { it.sentence() })
        else -> p
    }
}

/** Renames a person everywhere their name is used; the old name stays as an alias. */
fun AppData.renamePerson(id: String, newName: String): AppData {
    val n = newName.properName().ifEmpty { return this }
    val p = persons.find { it.id == id } ?: return this
    return copy(persons = persons.map { if (it.id == id) it.copy(name = n, aliases = (it.aliases + p.name).distinct()) else it }, tasks = tasks.map { it.renamed(p, n) })
}

/** Merges [fromId] into [intoId]: one person, all mentions point to the survivor. */
fun AppData.mergePersons(fromId: String, intoId: String): AppData {
    val from = persons.find { it.id == fromId } ?: return this
    val into = persons.find { it.id == intoId } ?: return this
    val merged = into.copy(
        aliases = (into.aliases + from.name + from.aliases).distinct().filterNot { it.equals(into.name, true) },
        about = into.about ?: from.about, phone = into.phone ?: from.phone, email = into.email ?: from.email,
    )
    return copy(persons = persons.filterNot { it.id == fromId }.map { if (it.id == intoId) merged else it }, tasks = tasks.map { it.renamed(from, into.name) })
}

private fun Task.renamed(p: Person, n: String): Task {
    fun fix(x: String?) = if (x != null && p.matches(x)) n else x
    fun subs(l: List<Subtask>): List<Subtask> = l.map { it.copy(assignee = fix(it.assignee), children = subs(it.children)) }
    return copy(people = people.map { fix(it)!! }.distinct(), waitingFor = fix(waitingFor), promisedTo = fix(promisedTo), subtasks = subs(subtasks))
}

/** A task saved from its card: words with capitals and names, people as the contacts know them ([me] — the user, as is). */
fun AppData.namesFixed(t0: Task, me: String): Task =
    t0.copy(title = t0.title.sentence().withNames(t0.people + listOfNotNull(t0.waitingFor, t0.promisedTo)),
        people = t0.people.map { if (it == me) it else person(it)?.name ?: it.properName() },
        waitingFor = t0.waitingFor?.let { person(it)?.name ?: it.properName() }, promisedTo = t0.promisedTo?.let { person(it)?.name ?: it.properName() })

/** Names on [t] nobody knows yet become drafts, a new label joins the labels. */
fun AppData.withMentionsOf(t: Task, me: String): AppData {
    val newPeople = (t.people + listOfNotNull(t.waitingFor, t.promisedTo)).filter { p -> p != me && person(p) == null }.distinct().map { Person(name = it, draft = true) }
    val newProject = t.project?.takeIf { p -> projects.none { it.equals(p, true) } }
    return copy(persons = persons + newPeople, projects = projects + listOfNotNull(newProject))
}
