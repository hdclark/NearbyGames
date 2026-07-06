package com.nearbygames

import com.nearbygames.ui.tictactoe.TicTacToeViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for the Tic-Tac-Toe winner-detection logic.
 * These run on the JVM without an Android device.
 */
class TicTacToeWinnerTest {

    private fun board(vararg cells: String?): List<String?> = cells.toList()

    @Test
    fun `no winner on empty board`() {
        assertNull(TicTacToeViewModel.checkWinner(board(*arrayOfNulls(9))))
    }

    @Test
    fun `X wins top row`() {
        assertEquals("X", TicTacToeViewModel.checkWinner(board("X","X","X",null,null,null,null,null,null)))
    }

    @Test
    fun `O wins middle row`() {
        assertEquals("O", TicTacToeViewModel.checkWinner(board(null,null,null,"O","O","O",null,null,null)))
    }

    @Test
    fun `X wins first column`() {
        assertEquals("X", TicTacToeViewModel.checkWinner(board("X",null,null,"X",null,null,"X",null,null)))
    }

    @Test
    fun `X wins main diagonal`() {
        assertEquals("X", TicTacToeViewModel.checkWinner(board("X",null,null,null,"X",null,null,null,"X")))
    }

    @Test
    fun `draw on full board with no winner`() {
        // X O X
        // X X O
        // O X O
        assertEquals("DRAW", TicTacToeViewModel.checkWinner(board("X","O","X","X","X","O","O","X","O")))
    }

    @Test
    fun `partial board with no winner returns null`() {
        assertNull(TicTacToeViewModel.checkWinner(board("X","O",null,null,null,null,null,null,null)))
    }
}
