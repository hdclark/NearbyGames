package com.nearbygames.data

/** Identifies every network message exchanged between devices. */
object NearbyMessageType {
    // Announcements game
    const val ANNOUNCEMENT = "ANNOUNCEMENT"
    const val ANNOUNCEMENT_SYNC = "ANNOUNCEMENT_SYNC"

    // Tic-Tac-Toe game
    const val TICTACTOE_INIT = "TICTACTOE_INIT"
    const val TICTACTOE_MOVE = "TICTACTOE_MOVE"
    const val TICTACTOE_RESET = "TICTACTOE_RESET"

    // Drawing canvas
    const val DRAWING_STROKE = "DRAWING_STROKE"
    const val DRAWING_CLEAR = "DRAWING_CLEAR"
    const val DRAWING_SYNC = "DRAWING_SYNC"

    // Rock-Paper-Scissors game
    const val RPS_CHOICE = "RPS_CHOICE"

    // Numbers Scrap game
    const val NUMBERS_SCRAP_CHOICE = "NUMBERS_SCRAP_CHOICE"
}
