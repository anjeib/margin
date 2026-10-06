package ru.anjeib.margin

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.anjeib.margin.data.dative
import ru.anjeib.margin.data.genitive

/** «Обещание · Виктору от вас», «вам от Марины»: names in the right case, companies untouched. */
class NamesTest {
    @Test fun dativeAndGenitive() {
        val cases = listOf(
            Triple("Виктор", "Виктору", "Виктора"),
            Triple("Марина", "Марине", "Марины"),
            Triple("Мария", "Марии", "Марии"),
            Triple("Ольга", "Ольге", "Ольги"),
            Triple("Андрей", "Андрею", "Андрея"),
            Triple("Игорь", "Игорю", "Игоря"),
            Triple("Дима", "Диме", "Димы"),
            Triple("Саша", "Саше", "Саши"),
            Triple("Таня", "Тане", "Тани"),
            Triple("Любовь", "Любови", "Любови"),
            Triple("Марина Петрова", "Марине Петровой", "Марины Петровой"),
            Triple("Виктор Петров", "Виктору Петрову", "Виктора Петрова"),
            Triple("Никита Смирнов", "Никите Смирнову", "Никиты Смирнова"),
            Triple("Анна Шевчук", "Анне Шевчук", "Анны Шевчук"),
            Triple("Олег Шевчук", "Олегу Шевчуку", "Олега Шевчука"),
            Triple("Пётр Толстой", "Петру Толстому", "Петра Толстого"),
            Triple("ООО ПакетПро", "ООО ПакетПро", "ООО ПакетПро"),
            Triple("Дента+", "Дента+", "Дента+"),
            Triple("John", "John", "John"),
        )
        cases.forEach { (n, d, g) -> assertEquals(n, d, n.dative()); assertEquals(n, g, n.genitive()) }
    }
}
