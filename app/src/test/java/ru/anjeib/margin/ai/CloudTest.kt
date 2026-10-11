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

    // The knock tries every address of the door: a network may give out IPv6 addresses that lead nowhere.
    private val v4 = java.net.InetAddress.getByAddress("door", byteArrayOf(158.toByte(), 160.toByte(), 54, 1))
    private val v6 = java.net.InetAddress.getByAddress("door", ByteArray(16).also { it[0] = 0x2a; it[15] = 1 })
    private fun dead(ms: Int): Nothing { Thread.sleep(ms.toLong()); throw java.net.SocketTimeoutException() }

    @Test fun aDeadIpv6DoesNotHideALiveIpv4() = kotlinx.coroutines.runBlocking {
        val ok = Cloud.reach(listOf(v6, v4), 1000) { a, ms -> if (a == v6) dead(ms) }
        assertEquals(true, ok)
    }

    @Test fun theFirstAnswerDoesNotWaitForTheDeadOne() = kotlinx.coroutines.runBlocking {
        val t0 = System.currentTimeMillis()
        Cloud.reach(listOf(v6, v4), 1000) { a, ms -> if (a == v6) dead(ms) }
        assert(System.currentTimeMillis() - t0 < 600) { "waited for the dead address" }
    }

    @Test fun noAddressAnswersMeansNoCloud() = kotlinx.coroutines.runBlocking {
        assertEquals(false, Cloud.reach(listOf(v6, v4), 200) { _, ms -> dead(ms) })
        assertEquals(false, Cloud.reach(emptyList(), 200) { _, _ -> })
    }
}
