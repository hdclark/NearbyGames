package com.nearbygames.ui.announcements

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.nearbygames.databinding.FragmentAnnouncementsBinding

class AnnouncementsFragment : Fragment() {

    private var _binding: FragmentAnnouncementsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AnnouncementsViewModel by viewModels()
    private val adapter = AnnouncementsAdapter()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAnnouncementsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.rvAnnouncements.layoutManager = LinearLayoutManager(requireContext()).also {
            it.stackFromEnd = true
        }
        binding.rvAnnouncements.adapter = adapter

        viewModel.announcements.observe(viewLifecycleOwner) { list ->
            adapter.submitList(list.sortedBy { it.timestamp })
            if (list.isNotEmpty()) binding.rvAnnouncements.scrollToPosition(adapter.itemCount - 1)
        }

        viewModel.connectedCount.observe(viewLifecycleOwner) { count ->
            binding.tvConnectionStatus.text =
                if (count == 0) "No devices connected"
                else "$count device(s) connected"
        }

        binding.btnSend.setOnClickListener { sendMessage() }

        binding.etMessage.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendMessage()
                true
            } else false
        }
    }

    private fun sendMessage() {
        val text = binding.etMessage.text?.toString()?.trim() ?: return
        if (text.isEmpty()) return
        viewModel.sendAnnouncement(text)
        binding.etMessage.setText("")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
