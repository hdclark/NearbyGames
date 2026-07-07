package com.nearbygames.ui.rps

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.nearbygames.data.RpsChoiceValue
import com.nearbygames.databinding.FragmentRpsBinding

class RockPaperScissorsFragment : Fragment() {

    private var _binding: FragmentRpsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: RockPaperScissorsViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRpsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnRock.setOnClickListener { viewModel.choose(RpsChoiceValue.ROCK) }
        binding.btnPaper.setOnClickListener { viewModel.choose(RpsChoiceValue.PAPER) }
        binding.btnScissors.setOnClickListener { viewModel.choose(RpsChoiceValue.SCISSORS) }

        viewModel.statusMessage.observe(viewLifecycleOwner) { msg ->
            binding.tvStatus.text = msg
        }

        viewModel.connectedCount.observe(viewLifecycleOwner) { count ->
            binding.tvConnectionStatus.text =
                if (count == 0) "No devices connected"
                else "$count device(s) connected"
        }

        viewModel.gameState.observe(viewLifecycleOwner) { state ->
            // Buttons are disabled once a choice has been made and re-enabled once the
            // round fully resets (myChoice == null again).
            val canChoose = state.myChoice == null
            binding.btnRock.isEnabled = canChoose
            binding.btnPaper.isEnabled = canChoose
            binding.btnScissors.isEnabled = canChoose
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
