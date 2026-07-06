package com.nearbygames.ui.announcements

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.nearbygames.data.Announcement
import com.nearbygames.databinding.ItemAnnouncementBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AnnouncementsAdapter : ListAdapter<Announcement, AnnouncementsAdapter.ViewHolder>(DIFF) {

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    inner class ViewHolder(private val binding: ItemAnnouncementBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(announcement: Announcement) {
            binding.tvSender.text = announcement.senderName
            binding.tvMessage.text = announcement.text
            binding.tvTime.text = timeFormat.format(Date(announcement.timestamp))
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAnnouncementBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Announcement>() {
            override fun areItemsTheSame(a: Announcement, b: Announcement) = a.id == b.id
            override fun areContentsTheSame(a: Announcement, b: Announcement) = a == b
        }
    }
}
