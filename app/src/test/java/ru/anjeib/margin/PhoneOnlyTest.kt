package ru.anjeib.margin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.anjeib.margin.ai.Assistant
import ru.anjeib.margin.ai.LocalParser
import ru.anjeib.margin.ai.Processor
import ru.anjeib.margin.ai.Scope
import ru.anjeib.margin.data.AppData
import ru.anjeib.margin.data.Person
import ru.anjeib.margin.data.Queued
import ru.anjeib.margin.i18n.L
import ru.anjeib.margin.i18n.Lang

/** «Телефон» (owner, 13 окт): no cloud AI — what used to wait for it is read on the phone. */
class PhoneOnlyTest {
    init { L.lang = Lang.Ru }

    @Test fun aForwardedMessage() {
        val d = AppData(persons = listOf(Person(name = "Марина Петрова")))
        val q = Queued(text = "Привет! Правки по макету пришлю в среду. А ты скинь, пожалуйста, до пятницы презентацию для клиента.", via = "share", from = "Марина Петрова")
        val plan = Processor.localPlan(q, d, Scope.Main)
        val r = Assistant.apply(d, plan, ru.anjeib.margin.ai.Forward.source(q), strict = true).data
        val wait = r.tasks.single { it.waitingFor != null }
        val mine = r.tasks.single { it.promisedTo != null }
        assertEquals("Марина Петрова", wait.waitingFor)
        assertTrue(wait.title, wait.title.contains("правк", true))
        assertEquals("Марина Петрова", mine.promisedTo)
        assertTrue(mine.title, mine.title.contains("презентац", true))
        assertEquals(2, r.tasks.size)
    }

    @Test fun aQueuedPhrase() {
        val plan = Processor.localPlan(Queued(text = "завтра в 15 созвон с Олегом"), AppData(), Scope.Main)
        val t = Assistant.apply(AppData(), plan, "", strict = false).data.tasks.single()
        assertEquals("15:00", t.time)
    }

    @Test fun daysForAMove() {
        listOf("на пятницу", "на завтра в 15", "через неделю", "через два дня").forEach { w ->
            val i = LocalParser.parse(w, AppData()).items.singleOrNull()
            assertTrue(w, i != null && (i.due != null || i.time != null))
        }
    }

    @Test fun aSharedMessageWithItsSenderInFront() {
        val d = AppData()
        val q = Queued(text = "Марина Петрова: Привет! Правки по макету пришлю в среду. А ты скинь, пожалуйста, до пятницы презентацию для клиента.", via = "share")
        val r = Assistant.apply(d, Processor.localPlan(q, d, Scope.Main), ru.anjeib.margin.ai.Forward.source(q), strict = true).data
        assertEquals(listOf("Марина Петрова"), r.tasks.mapNotNull { it.waitingFor })
        assertEquals(listOf("Марина Петрова"), r.tasks.mapNotNull { it.promisedTo })
    }
}
