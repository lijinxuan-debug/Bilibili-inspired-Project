package com.example.bilibili.ui.search

import android.os.Build
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.bilibili.data.model.VideoItem
import com.example.bilibili.databinding.ItemVideoSearchGridBinding
import com.example.bilibili.util.GlideEngine
import com.example.bilibili.util.VideoDataUtils

/**
 * 视频搜索结果适配器
 */
class SearchResultAdapter(
    private val onVideoClick: (VideoItem) -> Unit
) : ListAdapter<VideoItem, SearchResultAdapter.VideoViewHolder>(VideoDiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoViewHolder {
        val binding = ItemVideoSearchGridBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VideoViewHolder(binding)
    }

    override fun onBindViewHolder(holder: VideoViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class VideoViewHolder(private val binding: ItemVideoSearchGridBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: VideoItem) {
            binding.apply {
                // --- 1. 关键词高亮处理 ---
                val rawTitle = item.videoName ?: ""

                val formattedTitle = rawTitle
                    .replace("<span class='highlight'>", "<font color='#FB7299'>")
                    .replace("</span>", "</font>")

                // 使用 Html.fromHtml 渲染颜色
                tvVideoTitle.text = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    android.text.Html.fromHtml(formattedTitle, android.text.Html.FROM_HTML_MODE_LEGACY)
                } else {
                    @Suppress("DEPRECATION")
                    android.text.Html.fromHtml(formattedTitle)
                }

                tvUpName.text = item.nickName

                tvPlayCount.text = VideoDataUtils.formatCount(item.playCount)
                tvCommentCount.text = VideoDataUtils.formatCount(item.danmuCount)

                GlideEngine.loadVideoCover(root.context, item.videoCover, ivVideoCover)

                root.setOnClickListener { onVideoClick(item) }
            }
        }
    }
}

/**
 * 差异计算回调：用于 ListAdapter 的高效局部刷新
 */
object VideoDiffCallback : DiffUtil.ItemCallback<VideoItem>() {
    override fun areItemsTheSame(oldItem: VideoItem, newItem: VideoItem): Boolean {
        // 使用唯一标识符判断是否是同一个条目
        return oldItem.videoId == newItem.videoId
    }

    override fun areContentsTheSame(oldItem: VideoItem, newItem: VideoItem): Boolean {
        // 判断内容是否发生变化（VideoItem 建议定义为 data class）
        return oldItem == newItem
    }
}