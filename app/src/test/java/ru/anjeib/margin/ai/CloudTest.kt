package ru.anjeib.margin.ai

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/** When Margin stops asking the cloud and works on the phone — and when it asks again. */
class CloudTest {
    @Before fun fresh() { Cloud.reached(0) }

    @Test fun oneMissIsNotEnough() {
        Cloud.miss()
        assertEquals(Cloud.State.Ok, Cloud.state.value)
    }

    @Test fun twoMissesInARowGoToThePhone() {
        Cloud.miss(); Cloud.miss()
        assertEquals(Cloud.State.Down, Cloud.state.value)
    }

    @Test fun aSlowAnswerCountsAsAMiss() {
        Cloud.reached(20_000); Cloud.reached(16_000)
        assertEquals(Cloud.State.Down, Cloud.state.value)
    }

    @Test fun aLongReviewIsNotASlowLine() {
        Cloud.reached(40_000, Long.MAX_VALUE); Cloud.reached(40_000, Long.MAX_VALUE)
        assertEquals(Cloud.State.Ok, Cloud.state.value)
    }

    @Test fun aQuickAnswerBetweenMissesResetsTheCount() {
        Cloud.miss(); Cloud.reached(800); Cloud.miss()
        assertEquals(Cloud.State.Ok, Cloud.state.value)
    }

    @Test fun anAnswerBringsItBack() {
        Cloud.miss(); Cloud.miss(); Cloud.reached(500)
        assertEquals(Cloud.State.Ok, Cloud.state.value)
    }

    @Test fun anIoErrorInsideWatchIsCounted() {
        repeat(2) { runCatching { Cloud.watch<Unit> { throw java.net.SocketTimeoutException() } } }
        assertEquals(Cloud.State.Down, Cloud.state.value)
    }
}
