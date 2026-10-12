package ru.anjeib.margin

import org.junit.Assume.assumeTrue
import org.junit.Test
import ru.anjeib.margin.ai.LocalParser
import ru.anjeib.margin.data.AppData
import ru.anjeib.margin.i18n.L
import ru.anjeib.margin.i18n.Lang
import java.io.File

/**
 * Speech models compared by what matters without the AI: does the phone read the recognised words as it reads what was said?
 * ASR_TSV=<file> lines «reference \t model \t c|n \t recognised» (made by the speech benchmark). Prints a table; never fails.
 */
class AsrParseTest {
    private fun sig(text: String): Pair<String, String> {
        val r = LocalParser.parse(text, AppData())
        val meaning = r.items.joinToString(" ‖ ") { i ->
            listOf(i.kind, i.due, i.start, i.time, i.part, i.days.joinToString(","), i.repeat?.toString(), i.waitingFor, i.promisedTo, i.people.sorted().joinToString(","))
                .joinToString("|") { it?.toString().orEmpty() }
        } + " sure=" + r.sure
        val titles = r.items.joinToString(" ‖ ") { it.title.lowercase().replace('ё', 'е').replace(Regex("[^\\p{L}\\d ]"), "").trim() }
        return meaning to titles
    }

    @Test fun compare() {
        val path = System.getenv("ASR_TSV")
        assumeTrue(path != null && File(path).exists())
        L.lang = Lang.Ru
        data class Score(var n: Int = 0, var meaning: Int = 0, var both: Int = 0)
        val scores = linkedMapOf<String, Score>()
        File(path!!).readLines().filter { it.isNotBlank() }.forEach { line ->
            val (ref, model, cond, hyp) = line.split('\t').let { it + List(4 - it.size) { "" } }
            val want = sig(ref); val got = sig(hyp)
            val s = scores.getOrPut("$model $cond") { Score() }
            s.n++
            if (want.first == got.first) s.meaning++ else println("  ✗ $model/$cond «$hyp»\n      want ${want.first}\n      got  ${got.first}")
            if (want == got) s.both++
        }
        println("\nмодель/условие      смысл (даты, кто, что)   смысл+заголовок")
        scores.forEach { (k, s) -> println("%-20s %3d/%-3d               %3d/%-3d".format(k, s.meaning, s.n, s.both, s.n)) }
    }
}
