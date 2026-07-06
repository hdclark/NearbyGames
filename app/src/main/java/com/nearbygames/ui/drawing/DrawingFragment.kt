package com.nearbygames.ui.drawing

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.nearbygames.R
import com.nearbygames.databinding.FragmentDrawingBinding

class DrawingFragment : Fragment() {

    private var _binding: FragmentDrawingBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DrawingViewModel by viewModels()

    // Available colours
    private val colours = listOf(
        Color.BLACK,
        Color.RED,
        Color.BLUE,
        Color.GREEN,
        Color.parseColor("#FFC107"), // amber/yellow
        Color.WHITE
    )

    // Available brush sizes in dp (converted to px in onViewCreated)
    private val brushSizesDp = listOf(4f, 12f, 28f)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDrawingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val density = resources.displayMetrics.density

        // Wire colour buttons
        val colourButtons: List<ImageButton> = listOf(
            binding.btnColorBlack,
            binding.btnColorRed,
            binding.btnColorBlue,
            binding.btnColorGreen,
            binding.btnColorYellow,
            binding.btnColorWhite
        )
        colourButtons.forEachIndexed { i, btn ->
            btn.setOnClickListener {
                binding.drawingView.currentColor = colours[i]
                updateColourSelection(colourButtons, i)
            }
        }
        // Default: black selected
        updateColourSelection(colourButtons, 0)

        // Wire brush size buttons
        val brushButtons: List<ImageButton> = listOf(
            binding.btnBrushSmall,
            binding.btnBrushMedium,
            binding.btnBrushLarge
        )
        brushButtons.forEachIndexed { i, btn ->
            btn.setOnClickListener {
                binding.drawingView.currentBrushSize = brushSizesDp[i] * density
                updateBrushSelection(brushButtons, i)
            }
        }
        // Default: medium
        binding.drawingView.currentBrushSize = brushSizesDp[1] * density
        updateBrushSelection(brushButtons, 1)

        // Clear button
        binding.btnClear.setOnClickListener { viewModel.clearCanvas() }

        // Hook up drawing callbacks
        binding.drawingView.onStrokeComplete = { stroke ->
            viewModel.addStroke(stroke)
        }

        // Observe strokes
        viewModel.strokes.observe(viewLifecycleOwner) { strokes ->
            binding.drawingView.updateStrokes(strokes)
        }

        // Connection status
        viewModel.connectedCount.observe(viewLifecycleOwner) { count ->
            binding.tvConnectionStatus.text =
                if (count == 0) "No devices connected"
                else "$count device(s) connected"
        }
    }

    private fun updateColourSelection(buttons: List<ImageButton>, selectedIndex: Int) {
        buttons.forEachIndexed { i, btn ->
            btn.alpha = if (i == selectedIndex) 1.0f else 0.4f
            btn.scaleX = if (i == selectedIndex) 1.2f else 1.0f
            btn.scaleY = if (i == selectedIndex) 1.2f else 1.0f
        }
    }

    private fun updateBrushSelection(buttons: List<ImageButton>, selectedIndex: Int) {
        buttons.forEachIndexed { i, btn ->
            btn.alpha = if (i == selectedIndex) 1.0f else 0.4f
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
