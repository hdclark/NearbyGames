package com.nearbygames

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Tests for the random player assignment logic used in Tic-Tac-Toe.
 *
 * The real assignment happens in [com.nearbygames.ui.tictactoe.TicTacToeViewModel]
 * which is coupled to Android (AndroidViewModel). This test validates the same coin-flip
 * algorithm in isolation:
 *
 *   val weAreX = Random.nextBoolean()
 *   val xDeviceId = if (weAreX) myId else opponentId
 *   val oDeviceId = if (weAreX) opponentId else myId
 */
class TicTacToeAssignmentTest {

    private val myId = "device-aaa"
    private val opponentId = "device-bbb"

    private fun assignSymbols(random: Random): Pair<String, String> {
        val weAreX = random.nextBoolean()
        val xDeviceId = if (weAreX) myId else opponentId
        val oDeviceId = if (weAreX) opponentId else myId
        return Pair(xDeviceId, oDeviceId)
    }

    @Test
    fun `assignment always yields one X and one O`() {
        repeat(100) {
            val (xId, oId) = assignSymbols(Random.Default)
            assertTrue("xId must be one of the two devices", xId == myId || xId == opponentId)
            assertTrue("oId must be one of the two devices", oId == myId || oId == opponentId)
            assertTrue("X and O must be different devices", xId != oId)
        }
    }

    @Test
    fun `both outcomes are reachable over many trials`() {
        var myWasX = 0
        var opponentWasX = 0
        val trials = 1000
        repeat(trials) {
            val (xId, _) = assignSymbols(Random.Default)
            if (xId == myId) myWasX++ else opponentWasX++
        }
        // With a fair coin over 1000 trials each outcome should appear at least once
        assertTrue("myId should be assigned X at least once", myWasX > 0)
        assertTrue("opponentId should be assigned X at least once", opponentWasX > 0)
    }

    @Test
    fun `with seeded random the result is deterministic`() {
        val seed = 42L
        val (x1, o1) = assignSymbols(Random(seed))
        val (x2, o2) = assignSymbols(Random(seed))
        assertTrue("Same seed must yield same X device", x1 == x2)
        assertTrue("Same seed must yield same O device", o1 == o2)
    }
}
