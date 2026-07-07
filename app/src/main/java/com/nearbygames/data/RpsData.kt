package com.nearbygames.data

/** The three possible Rock-Paper-Scissors picks. */
object RpsChoiceValue {
    const val ROCK = "ROCK"
    const val PAPER = "PAPER"
    const val SCISSORS = "SCISSORS"
}

/** Payload for an RPS_CHOICE message: the sender's private pick for this round. */
data class RpsChoice(
    val choice: String
)

/**
 * Local (per-device) state of a Rock-Paper-Scissors match against a single opponent.
 *
 * [countdown] counts 3, 2, 1 once both players have picked, then null again once the
 * result is revealed. [resultMessage] / [opponentChoiceRevealed] are shown for a few
 * seconds before the round automatically resets.
 */
data class RpsState(
    val myChoice: String? = null,
    val opponentChoice: String? = null,
    val countdown: Int? = null,
    val resultMessage: String? = null,
    val opponentChoiceRevealed: String? = null
)
