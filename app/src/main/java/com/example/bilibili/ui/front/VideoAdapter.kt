package com.example.bilibili.ui.front

import android.content.Intent
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.example.bilibili.data.model.VideoItem
import com.example.bilibili.databinding.ItemVideoCardBinding
import com.example.bilibili.ui.playVideo.PlayVideoActivity
import com.example.bilibili.util.GlideEngine
import com.example.bilibili.util.VideoDataUtils.formatDuration

class VideoAdapter :
    PagingDataAdapter<VideoItem, VideoAdapter.VideoViewHolder>(VideoItemDiffCallback()) {

    class VideoViewHolder(val binding: ItemVideoCardBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoViewHolder {
        val binding = ItemVideoCardBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VideoViewHolder(binding)
    }

    override fun onBindViewHolder(holder: VideoViewHolder, position: Int) {
        val item = getItem(position) ?: return
        val binding = holder.binding

        // 1. 设置标题
        binding.tvVideoTitle.text = item.videoName

        // 2. 设置播放量和弹幕 (简单格式化)
        binding.tvPlayCount.text = formatCount(item.playCount)
        binding.tvCommentCount.text = formatCount(item.danmuCount) // 对应你图片上的弹幕/评论位置

        // 3. 格式化时长 (Int -> 00:00)
        binding.tvDuration.text = formatDuration(item.duration)

        // 4. 加载图片 (需要引入 Glide 库)
        GlideEngine.loadVideoCover(
            binding.ivVideoCover.context,
            item.videoCover,
            binding.ivVideoCover
        )

        // 5. 设置 UP 主名字
        binding.tvUpName.text = item.nickName

        // 点击时间跳转播放页面
        binding.root.setOnClickListener {
            val context = it.context
            val intent = Intent(context, PlayVideoActivity::class.java).apply {
                // 关键：将 videoId 传递给下一个 Activity
                putExtra("video_id", item.videoId)
            }
            context.startActivity(intent)
        }
    }

    // 辅助工具：万级数字转换
    private fun formatCount(count: Int): String {
        return if (count >= 10000) {
            String.format("%.1f万", count / 10000.0)
        } else {
            count.toString()
        }
    }

    // 辅助工具：秒转 00:00
    private fun formatDuration(seconds: Int): String {
        val m = seconds / 60
        val s = seconds % 60
        return String.format("%02d:%02d", m, s)
    }

    // DiffCallback用于比较数据项是否相同
    class VideoItemDiffCallback : DiffUtil.ItemCallback<VideoItem>() {
        override fun areItemsTheSame(oldItem: VideoItem, newItem: VideoItem): Boolean {
            return oldItem.videoId == newItem.videoId
        }

        override fun areContentsTheSame(oldItem: VideoItem, newItem: VideoItem): Boolean {
            return oldItem == newItem
        }
    }
}