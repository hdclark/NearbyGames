package com.nearbygames.ui.numbersscrap

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.nearbygames.data.NumbersScrapState
import com.nearbygames.databinding.FragmentNumbersScrapBinding

class NumbersScrapFragment : Fragment() {

    private var _binding: FragmentNumbersScrapBinding? = null
    private val binding get() = _binding!!

    private val viewModel: NumbersScrapViewModel by viewModels()

    /** The 10 digit buttons in order 0..9. */
    private val digitButtons: List<Button> by lazy {
        with(binding) {
            listOf(
                btnDigit0, btnDigit1, btnDigit2, btnDigit3, btnDigit4,
                btnDigit5, btnDigit6, btnDigit7, btnDigit8, btnDigit9
            )
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNumbersScrapBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        digitButtons.forEachIndexed { digit, button ->
            button.setOnClickListener { viewModel.choose(digit) }
        }

        viewModel.statusMessage.observe(viewLifecycleOwner) { msg ->
            binding.tvStatus.text = msg
        }

        viewModel.connectedCount.observe(viewLifecycleOwner) { count ->
            binding.tvConnectionStatus.text =
                if (count == 0) "No devices connected"
                else "$count device(s) connected"
        }

        viewModel.gameState.observe(viewLifecycleOwner) { state ->
            binding.tvScore.text = "You ${state.myScore} – ${state.opponentScore} Opponent"
            updateDigitButtons(state)
        }
    }

    private fun updateDigitButtons(state: NumbersScrapState) {
        val canChoose = state.myChoice == null && state.gameResultMessage == null
        digitButtons.forEachIndexed { digit, button ->
            button.isEnabled = canChoose && digit !in state.usedDigits
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
