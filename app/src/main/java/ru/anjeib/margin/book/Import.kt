package ru.anjeib.margin.book

import ru.anjeib.margin.data.AppData
import ru.anjeib.margin.data.Entry
import ru.anjeib.margin.data.Subtask
import ru.anjeib.margin.data.Task
import java.time.LocalDate

/*
 * The notebook kept by Margin 0.29 («Темы вместо задач»), read into «Поля» once, the first time the new version opens
 * (DESIGN.md §16): a matter → a line on its page; a topic → a thread with its matters and records; a record → a note
 * line; an idea → a note in the thread «Идеи»; a promise → a margin note (→ / ←); a comment → a line under its line.
 * The old file is left as it was — nothing is lost if anything here reads it wrong.
 */
object Import {
    private fun stamp(s: String?, fallback: String): String = when {
        s == null -> fallback
        s.length == 10 -> s + "T09:00"
        else -> s.take(16)
    }

    private fun day(s: String?) = s?.take(10)?.takeIf { runCatching { LocalDate.parse(it) }.isSuccess }

    private fun repeat(r: ru.anjeib.margin.data.Repeat?) = r?.let { Repeat(it.freq, it.interval, it.days) }

    private fun who(waiting: String?, promised: String?, people: List<String>): List<Who> =
        listOfNotNull(promised?.let { Who(it, Dir.To) }, waiting?.let { Who(it, Dir.From) }) +
            people.filter { it != waiting && it != promised }.distinct().map { Who(it, Dir.With) }

    /** What a record says, with its list items and what is recognised on its files. */
    private fun words(e: Entry): String = listOfNotNull(
        e.text.trim().takeIf { it.isNotEmpty() },
        e.items.takeIf { it.isNotEmpty() }?.joinToString("\n") { (if (it.done) "× " else "• ") + it.title },
    ).joinToString("\n")

    fun from(old: AppData, today: LocalDate = LocalDate.now()): Book {
        val lines = ArrayList<Line>()
        val threads = ArrayList<Thread>()
        val now = nowStamp()

        /** Comments and records under a line — its continuation, each written when it was. */
        fun under(parent: Line, comments: List<ru.anjeib.margin.data.Comment>, entries: List<Entry>) {
            comments.forEach { c -> lines += Line(id = c.id, text = c.text, kind = Kind.Note, parentId = parent.id, threadId = parent.threadId, writtenAt = stamp(c.at, parent.writtenAt)) }
            entries.forEach { e ->
                val t = words(e)
                if (t.isNotBlank() || e.attachments.isNotEmpty())
                    lines += Line(id = e.id, text = t.ifBlank { e.attachments.firstOrNull()?.name ?: "" }, kind = Kind.Note, parentId = parent.id,
                        threadId = parent.threadId, files = e.attachments, writtenAt = stamp(e.at, parent.writtenAt))
            }
        }

        /** A matter of a topic (or a step of a matter), with its steps under it. */
        fun step(s: Subtask, threadId: String?, parentId: String?, born: String) {
            val base = Line(id = s.id, text = s.title, kind = Kind.Todo, state = if (s.done) State.Done else State.Open,
                day = day(s.due), time = s.time, repeat = repeat(s.repeat), threadId = threadId, parentId = parentId,
                who = who(s.from, null, s.people), place = s.place, writtenAt = born, doneAt = s.doneAt?.let { stamp(it, now) })
            if (s.slots.size >= 2) s.slots.forEach { sl ->
                lines += base.copy(id = s.id + "-" + sl.id, day = day(sl.date), time = sl.time ?: s.time, state = if (sl.done) State.Done else State.Open)
            } else lines += base
            under(base, s.comments, emptyList())
            s.children.forEach { k -> step(k, threadId, s.id, born) }
        }

        val kept = old.tasks.filter { !it.sample }
        // Ideas: notes in one thread «Идеи», as in the notebook's examples.
        val ideas = kept.filter { it.thought && it.subtasks.isEmpty() }
        val ideasThread = if (ideas.isEmpty()) null else Thread(title = "Идеи", sphere = "personal").also { threads += it }

        for (t in kept) {
            val born = stamp(t.createdAt, now)
            when {
                t in ideas -> {
                    val l = Line(id = t.id, text = t.title, kind = Kind.Note, day = day(t.createdAt), threadId = ideasThread?.id, writtenAt = born,
                        who = who(null, null, t.people), place = t.place, files = t.entries.flatMap { it.attachments }.take(12))
                    lines += l
                    under(l, t.comments, t.entries.filter { words(it).isNotBlank() && words(it) != t.title })
                }
                t.topic -> {
                    val th = Thread(id = t.id, title = t.title, sphere = t.sphere, createdAt = born,
                        closedAt = if (t.done) stamp(t.doneAt, now) else null)
                    threads += th
                    // Records first (matters may hang under them), each on the page of its day.
                    t.entries.forEach { e ->
                        val text = words(e)
                        if (text.isNotBlank() || e.attachments.isNotEmpty())
                            lines += Line(id = e.id, text = text.ifBlank { e.attachments.firstOrNull()?.name ?: "" }, kind = Kind.Note, day = day(e.at),
                                threadId = th.id, files = e.attachments, writtenAt = stamp(e.at, born))
                    }
                    val entryIds = t.entries.map { it.id }.toSet()
                    val stepIds = t.subtasks.map { it.id }.toSet()
                    t.subtasks.forEach { s ->
                        // A matter added under a record or another matter keeps that place.
                        val parent = s.parent?.takeIf { it in entryIds || it in stepIds }
                        step(s, th.id, parent, born)
                    }
                    // The topic's own comments are records of the thread, each on its day.
                    t.comments.forEach { c -> lines += Line(id = c.id, text = c.text, kind = Kind.Note, day = day(c.at), threadId = th.id, writtenAt = stamp(c.at, born)) }
                }
                else -> {
                    val state = when { !t.done -> State.Open; t.dropped -> State.Dropped; else -> State.Done }
                    val windowed = t.start != null && t.due != null && t.start != t.due
                    val base = Line(id = t.id, text = t.title, kind = Kind.Todo, state = state,
                        day = if (windowed) day(t.start) else day(t.due ?: t.start), until = if (windowed) day(t.due) else null,
                        time = t.time, part = t.dayPart, repeat = repeat(t.repeat), who = who(t.waitingFor, t.promisedTo, t.people), place = t.place,
                        files = t.entries.flatMap { it.attachments }.take(12), writtenAt = born, doneAt = t.doneAt?.let { stamp(it, now) }, source = t.source)
                    if (t.slots.size >= 2) t.slots.forEach { sl ->
                        lines += base.copy(id = t.id + "-" + sl.id, day = day(sl.date), time = sl.time ?: t.time, until = null,
                            state = if (sl.done) State.Done else if (t.done) state else State.Open)
                    } else lines += base
                    under(base, t.comments, t.entries.filter { words(it).isNotBlank() })
                    t.subtasks.forEach { s -> step(s, null, t.id, born) }
                }
            }
        }

        // What was kept as notes («Входящие») and what waited for the network: notes on their days.
        old.notes.filter { !it.archived }.forEach { n ->
            n.entries.forEach { e ->
                val text = words(e)
                if (text.isNotBlank() || e.attachments.isNotEmpty())
                    lines += Line(id = e.id, text = text.ifBlank { e.attachments.firstOrNull()?.name ?: n.title }, kind = Kind.Note, day = day(e.at),
                        files = e.attachments, writtenAt = stamp(e.at, now))
            }
        }
        old.queue.forEach { q ->
            lines += Line(id = q.id, text = q.text.ifBlank { q.files.firstOrNull()?.name ?: "" }, kind = Kind.Note, day = day(q.at) ?: today.toString(),
                files = q.files, writtenAt = stamp(q.at, now), source = q.from?.let { "$it: ${q.text}" })
        }

        val persons = old.persons.filter { !it.archived && !it.sample }.map { p ->
            Person(id = p.id, name = p.name, aliases = p.aliases,
                about = listOfNotNull(p.company, p.about?.takeIf { p.aboutFromUser }).joinToString(", ").ifBlank { null },
                phone = p.phone, email = p.email, telegram = p.telegram, kept = p.kept || !p.draft)
        }
        // A name on a line that the old notebook never kept as a person still becomes one (not kept).
        val named = lines.flatMap { l -> l.who.map { it.name } }.distinct().filter { n -> persons.none { it.matches(n) } }.map { Person(name = it) }
        return Book(lines = lines.filter { it.text.isNotBlank() || it.files.isNotEmpty() }.distinctBy { it.id }, threads = threads, persons = persons + named)
    }
}
