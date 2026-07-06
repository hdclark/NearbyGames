package com.nearbygames.ui.tictactoe

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.nearbygames.R
import com.nearbygames.data.TicTacToeState
import com.nearbygames.databinding.FragmentTictactoeBinding

class TicTacToeFragment : Fragment() {

    private var _binding: FragmentTictactoeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: TicTacToeViewModel by viewModels()

    /** The 9 cell buttons in reading order (top-left → bottom-right). */
    private val cellButtons: List<Button> by lazy {
        with(binding) {
            listOf(btn00, btn01, btn02, btn10, btn11, btn12, btn20, btn21, btn22)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTictactoeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Wire up cell clicks
        cellButtons.forEachIndexed { index, button ->
            button.setOnClickListener { viewModel.makeMove(index) }
        }

        binding.btnReset.setOnClickListener { viewModel.resetGame() }

        viewModel.statusMessage.observe(viewLifecycleOwner) { msg ->
            binding.tvStatus.text = msg
        }

        viewModel.connectedCount.observe(viewLifecycleOwner) { count ->
            binding.tvConnectionStatus.text =
                if (count == 0) "No devices connected"
                else "$count device(s) connected"
        }

        viewModel.gameState.observe(viewLifecycleOwner) { state ->
            if (state == null) {
                cellButtons.forEach { it.text = ""; it.isEnabled = false }
                binding.btnReset.isEnabled = false
                return@observe
            }

            binding.btnReset.isEnabled = state.winner != null || !state.gameActive

            cellButtons.forEachIndexed { index, button ->
                val cell = state.board[index]
                button.text = cell ?: ""
                button.setTextColor(
                    when (cell) {
                        "X" -> Color.parseColor("#E53935")
                        "O" -> Color.parseColor("#1E88E5")
                        else -> Color.BLACK
                    }
                )
                button.isEnabled = state.gameActive && cell == null && state.winner == null
            }

            // Highlight winning cells
            highlightWinner(state)
        }
    }

    private fun highlightWinner(state: TicTacToeState) {
        // Reset background first
        cellButtons.forEach { it.setBackgroundResource(R.drawable.cell_default) }

        if (state.winner == null || state.winner == "DRAW") return

        val lines = listOf(
            listOf(0, 1, 2), listOf(3, 4, 5), listOf(6, 7, 8),
            listOf(0, 3, 6), listOf(1, 4, 7), listOf(2, 5, 8),
            listOf(0, 4, 8), listOf(2, 4, 6)
        )
        for (line in lines) {
            val (a, b, c) = line
            if (state.board[a] == state.winner &&
                state.board[b] == state.winner &&
                state.board[c] == state.winner
            ) {
                listOf(a, b, c).forEach { cellButtons[it].setBackgroundResource(R.drawable.cell_winner) }
                break
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
