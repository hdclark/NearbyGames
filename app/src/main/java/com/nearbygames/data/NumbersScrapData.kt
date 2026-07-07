package com.nearbygames.data

/** Payload for a NUMBERS_SCRAP_CHOICE message: the sender's private pick for this round. */
data class NumbersScrapChoice(
    val digit: Int
)

/**
 * Local (per-device) state of a "Numbers Scrap" match against a single opponent.
 *
 * Each player has the digits 0-9 available, one use each, across 10 rounds. Whoever
 * picks the higher digit wins that round (equal digits are a tied round, worth nothing
 * to either player). After all 10 rounds have been played, whoever won the most rounds
 * wins the game.
 */
data class NumbersScrapState(
    val usedDigits: Set<Int> = emptySet(),
    val myChoice: Int? = null,
    val opponentChoice: Int? = null,
    val round: Int = 0,
    val myScore: Int = 0,
    val opponentScore: Int = 0,
    val roundResultMessage: String? = null,
    val gameResultMessage: String? = null
)
