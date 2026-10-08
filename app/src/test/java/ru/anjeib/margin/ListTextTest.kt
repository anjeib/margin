package ru.anjeib.margin

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.anjeib.margin.ui.continueList

/** Typing a list: Enter continues it («2.» after «1.», «•» after «•»), Enter on an empty item ends it. */
class ListTextTest {
    @Test fun numbersGoOn() = assertEquals("1. хлеб\n2. ", continueList("1. хлеб", "1. хлеб\n"))
    @Test fun bracketsGoOn() = assertEquals("План:\n1) раз\n2) ", continueList("План:\n1) раз", "План:\n1) раз\n"))
    @Test fun bulletsGoOn() = assertEquals("- раз\n- ", continueList("- раз", "- раз\n"))
    @Test fun emptyItemEnds() = assertEquals("1. хлеб\n", continueList("1. хлеб\n2. ", "1. хлеб\n2. \n"))
    @Test fun plainTextUntouched() = assertEquals("привет\n", continueList("привет", "привет\n"))
}
