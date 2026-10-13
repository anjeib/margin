package ru.anjeib.margin

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Test
import ru.anjeib.margin.ai.LocalParser
import ru.anjeib.margin.ui.continueList

/** A list goes on by itself from its first marker, wherever Enter is pressed (owner, 13 окт); the word that says what it is. */
class ListsTest {
    /** Types Enter at [at] in [text]; returns the text and where the cursor ends up. */
    private fun enter(text: String, at: Int = text.length): Pair<String, Int> {
        val old = TextFieldValue(text, TextRange(at))
        val new = TextFieldValue(text.substring(0, at) + "\n" + text.substring(at), TextRange(at + 1))
        return continueList(old, new).let { it.text to it.selection.start }
    }

    @Test fun numbersLettersAndDashes() {
        assertEquals("1. хлеб\n2. ", enter("1. хлеб").first)
        assertEquals("1) хлеб\n2) ", enter("1) хлеб").first)
        assertEquals("а) хлеб\nб) ", enter("а) хлеб").first)
        assertEquals("a. bread\nb. ", enter("a. bread").first)
        assertEquals("— хлеб\n— ", enter("— хлеб").first)
        assertEquals("• хлеб\n• ", enter("• хлеб").first)
        assertEquals("9. хлеб\n10. ", enter("9. хлеб").first)
    }

    @Test fun inTheMiddleToo() {
        val (t, cursor) = enter("1. хлеб\n2. молоко", at = 7)
        assertEquals("1. хлеб\n2. \n2. молоко", t)
        assertEquals(11, cursor)
    }

    @Test fun anEmptyMarkerEndsTheList() {
        assertEquals("1. хлеб\n", enter("1. хлеб\n2. ").first)
    }

    @Test fun plainTextStaysPlain() {
        assertEquals("купить хлеб\n", enter("купить хлеб").first)
    }

    @Test fun theLeadingWord() {
        assertEquals("idea" to "Блог о дизайне", LocalParser.lead("Идея. Блог о дизайне"))
        assertEquals("task" to "купить хлеб", LocalParser.lead("дело купить хлеб"))
        assertEquals(null to "Купить хлеб", LocalParser.lead("Купить хлеб"))
    }
}
