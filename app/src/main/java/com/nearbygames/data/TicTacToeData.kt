package com.nearbygames.data

/**
 * Full state of a Tic-Tac-Toe game between two devices.
 *
 * [board] is a 9-element list (null = empty, "X" or "O" = played).
 * [winner] is null while the game is ongoing, "X", "O", or "DRAW" when finished.
 */
data class TicTacToeState(
    val board: List<String?> = List(9) { null },
    val currentPlayer: String = "X",
    val winner: String? = null,
    val xDeviceId: String = "",
    val oDeviceId: String = "",
    val xScore: Int = 0,
    val oScore: Int = 0,
    val gameActive: Boolean = false
)

/** Payload for a TICTACTOE_MOVE message. */
data class TicTacToeMove(
    val position: Int,
    val symbol: String
)

/** Payload for a TICTACTOE_INIT message. */
data class TicTacToeInit(
    val xDeviceId: String,
    val oDeviceId: String
)
