package com.example.bilibili.ui.personal.contribute

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.example.bilibili.data.model.VideoItem
import com.example.bilibili.databinding.ItemVideoContributeGridBinding
import com.example.bilibili.util.GlideEngine
import com.example.bilibili.util.RetrofitClient
import com.example.bilibili.util.VideoDataUtils.formatDuration

class ContributeVideoAdapter(private val onClick: (VideoItem) -> Unit) :
    PagingDataAdapter<VideoItem, ContributeVideoAdapter.VideoViewHolder>(VideoItemDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoViewHolder {
        val binding = ItemVideoContributeGridBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return VideoViewHolder(binding)
    }

    override fun onBindViewHolder(holder: VideoViewHolder, position: Int) {
        val item = getItem(position) ?: return
        holder.bind(item)
        holder.itemView.setOnClickListener { onClick(item) }
    }

    class VideoViewHolder(private val binding: ItemVideoContributeGridBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(video: VideoItem) {
            binding.tvVideoTitle.text = video.videoName
            binding.tvPlayCount.text = formatCount(video.playCount)
            binding.tvCommentCount.text = formatCount(video.danmuCount)

            binding.tvDuration.text = formatDuration(video.duration)

            GlideEngine.loadVideoCover(binding.root.context, video.videoCover, binding.ivVideoCover)
        }

        private fun formatCount(count: Int): String =
            if (count >= 10000) "${count / 10000}万" else count.toString()

        private fun formatDuration(seconds: Int): String {
            val min = seconds / 60
            val sec = seconds % 60
            return String.format("%02d:%02d", min, sec)
        }
    }

    class VideoItemDiffCallback : DiffUtil.ItemCallback<VideoItem>() {
        override fun areItemsTheSame(oldItem: VideoItem, newItem: VideoItem): Boolean {
            return oldItem.videoId == newItem.videoId
        }

        override fun areContentsTheSame(oldItem: VideoItem, newItem: VideoItem): Boolean {
            return oldItem == newItem
        }
    }
}