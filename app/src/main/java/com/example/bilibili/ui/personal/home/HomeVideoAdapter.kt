package com.example.bilibili.ui.personal.home

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.example.bilibili.data.model.VideoItem
import com.example.bilibili.databinding.ItemVideoHomeGridBinding
import com.example.bilibili.util.GlideEngine
import com.example.bilibili.util.VideoDataUtils

class HomeVideoAdapter(private val onClick: (VideoItem) -> Unit) :
    PagingDataAdapter<VideoItem, HomeVideoAdapter.VideoViewHolder>(VideoItemDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoViewHolder {
        val binding = ItemVideoHomeGridBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VideoViewHolder(binding, onClick)
    }

    override fun onBindViewHolder(holder: VideoViewHolder, position: Int) {
        val video = getItem(position) ?: return
        holder.bind(video)
    }

    class VideoViewHolder(
        val binding: ItemVideoHomeGridBinding,
        private val onClick: (VideoItem) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        private var currentVideo: VideoItem? = null

        init {
            binding.root.setOnClickListener {
                currentVideo?.let { onClick(it) }
            }
        }

        @SuppressLint("DefaultLocale")
        fun bind(video: VideoItem) {
            this.currentVideo = video

            binding.apply {
                tvVideoTitle.text = video.videoName

                tvPlayCount.text = VideoDataUtils.formatCount(video.playCount)

                tvCommentCount.text = VideoDataUtils.formatCount(video.danmuCount)

                tvDuration.text = VideoDataUtils.formatDuration(video.duration)

                GlideEngine.loadVideoCover(root.context, video.videoCover, ivVideoCover)
            }
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