package com.example.bilibili.ui.personal.collect

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.example.bilibili.data.model.CollectVideo
import com.example.bilibili.databinding.ItemVideoCollectGridBinding
import com.example.bilibili.util.GlideEngine
import com.example.bilibili.util.RetrofitClient

class CollectAdapter(private val onClick: (CollectVideo) -> Unit) :
    PagingDataAdapter<CollectVideo, CollectAdapter.ViewHolder>(CollectVideoDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemVideoCollectGridBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val video = getItem(position) ?: return
        holder.binding.apply {
            tvVideoTitle.text = video.videoName
            tvUpName.text = "来自 UID: ${video.videoUserId}"
            tvActionDate.text = video.actionTime.substringBefore(" ")
            tvActionCount.text = "收藏热度: ${video.actionCount}"
            GlideEngine.loadVideoCover(ivVideoCover.context, video.videoCover, ivVideoCover)
            root.setOnClickListener { onClick(video) }
        }
    }

    class ViewHolder(val binding: ItemVideoCollectGridBinding) : RecyclerView.ViewHolder(binding.root)

    class CollectVideoDiffCallback : DiffUtil.ItemCallback<CollectVideo>() {
        override fun areItemsTheSame(oldItem: CollectVideo, newItem: CollectVideo): Boolean {
            return oldItem.actionId == newItem.actionId
        }

        override fun areContentsTheSame(oldItem: CollectVideo, newItem: CollectVideo): Boolean {
            return oldItem == newItem
        }
    }
}