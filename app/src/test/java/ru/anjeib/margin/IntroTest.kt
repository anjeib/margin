package ru.anjeib.margin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.anjeib.margin.ai.LocalParser
import ru.anjeib.margin.data.AppData
import ru.anjeib.margin.data.Task
import ru.anjeib.margin.data.hasSamples
import ru.anjeib.margin.data.likelyTopic
import ru.anjeib.margin.data.withSamples
import ru.anjeib.margin.data.withoutSamples
import ru.anjeib.margin.i18n.Lang
import ru.anjeib.margin.i18n.L

/** «Знакомство с Margin»: examples come in marked, leave in one go, and never mix with the user's own. */
class IntroTest {
    @Test fun examplesComeAndGoWithoutTouchingTheUsers() {
        L.lang = Lang.Ru
        val mine = Task(id = "m", title = "Купить хлеб")
        val d = AppData(tasks = listOf(mine)).withSamples(L.s.intro)
        assertTrue(d.hasSamples())
        assertTrue(d.tasks.count { it.sample } >= 9)
        assertTrue(d.tasks.any { it.sample && it.topic && !it.thought && it.subtasks.any { q -> q.from != null } })
        assertTrue(d.tasks.any { it.sample && it.promisedTo != null })
        assertTrue(d.tasks.any { it.sample && it.repeat != null })
        // Back again: fresh ones in place of the old, never twice.
        assertEquals(d.tasks.size, d.withSamples(L.s.intro).tasks.size)
        val gone = d.withoutSamples()
        assertFalse(gone.hasSamples())
        assertEquals(listOf(mine), gone.tasks)
        // The AI does not see the examples that only teach — it copied «Спросите помощника…» instead of answering.
        val ctx = ru.anjeib.margin.ai.Assistant.context(d, ru.anjeib.margin.data.Settings())
        assertFalse(ctx.contains("Спросите помощника"))
        assertTrue(ctx.contains("Полив цветов"))
        // «к теме …?» never points into the examples.
        assertNull(d.likelyTopic("Список гостей от Анны на праздник", listOf("Анна")))
    }

    @Test fun aQuestionIsNotAMatter() {
        L.lang = Lang.Ru
        val d = AppData()
        val q = LocalParser.parse("что горит на этой неделе?", d)
        assertEquals("question", q.why); assertTrue(q.items.isEmpty())
        assertEquals("question", LocalParser.parse("Когда я обещал Лёше макет?", d).why)
        // An order stays an order, even the one about a contract.
        assertEquals(1, LocalParser.parse("Подготовь шаблон договора", d).items.size)
        LocalParser.parse("Когда приедет Дима, отдать ключи", d).let { assertTrue(it.items.isNotEmpty() && it.why != "question") }
    }

    @Test fun noIdsInWhatTheAssistantSays() {
        assertEquals("1) «Список гостей» ждёт ответа от Анны, срок 11 октября.",
            ru.anjeib.margin.ai.Assistant.clean("1) «Список гостей» (subtask_id=intro-q2) ждёт ответа от Анны, срок 11 октября."))
        assertEquals("Перенесла «Стоматолог».", ru.anjeib.margin.ai.Assistant.clean("Перенесла «Стоматолог» [task_id: demo-d7]."))
    }

    /** «что я обещал Анне?» — the promise and the topic waiting on Anna reach the AI (they were left out before). */
    @Test fun aPersonsPromisesReachTheAssistant() {
        L.lang = Lang.Ru
        val d = AppData().withSamples(L.s.intro)
        val ctx = ru.anjeib.margin.ai.Assistant.context(d, ru.anjeib.margin.data.Settings(), query = "что я обещал Анне?")
        assertTrue(ctx, ctx.contains("Прислать фото с отпуска") && ctx.contains("promised to Анна"))
        assertTrue(ctx, ctx.contains("Знакомство с Margin"))
    }
}
