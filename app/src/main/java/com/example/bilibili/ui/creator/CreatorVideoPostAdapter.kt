package com.example.bilibili.ui.creator

import android.content.Intent
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.example.bilibili.R
import com.example.bilibili.data.model.CreatorVideoPost
import com.example.bilibili.databinding.ItemCreatorVideoPostBinding
import com.example.bilibili.ui.playVideo.PlayVideoActivity
import com.example.bilibili.util.GlideEngine
import com.example.bilibili.util.ToastUtils
import com.example.bilibili.util.VideoDataUtils

class CreatorVideoPostAdapter(
    private val onEdit: (CreatorVideoPost) -> Unit,
    private val onDelete: (CreatorVideoPost) -> Unit,
) : PagingDataAdapter<CreatorVideoPost, CreatorVideoPostAdapter.ViewHolder>(DiffCallback) {

    companion object {
        /** 审核成功，可播放 */
        private const val STATUS_PASS = 3
        /** 审核不通过 */
        private const val STATUS_REJECT = 4
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCreatorVideoPostBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false,
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position) ?: return
        holder.bind(item)
        holder.binding.btnEdit.setOnClickListener { onEdit(item) }
        holder.binding.btnDelete.setOnClickListener { onDelete(item) }
    }

    class ViewHolder(
        val binding: ItemCreatorVideoPostBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: CreatorVideoPost) {
            val context = binding.root.context
            binding.tvTitle.text = item.videoName
            binding.tvTime.text = item.createTime
            binding.tvDuration.text = VideoDataUtils.formatDuration(item.duration)
            binding.tvPlayCount.text = formatStat(item.playCount)
            binding.tvLikeCount.text = formatStat(item.likeCount)
            binding.tvCommentCount.text = formatStat(item.commentCount)
            binding.tvDanmuCount.text = formatStat(item.danmuCount)
            GlideEngine.loadVideoCover(context, item.videoCover, binding.ivCover)

            bindStatusBadge(item)
            bindVideoPreviewClick(item)
        }

        private fun bindStatusBadge(item: CreatorVideoPost) {
            val context = binding.root.context
            when (item.status) {
                STATUS_PASS -> {
                    binding.tvStatusBadge.isVisible = false
                }
                STATUS_REJECT -> {
                    binding.tvStatusBadge.isVisible = true
                    binding.tvStatusBadge.text = context.getString(R.string.creator_status_rejected)
                    binding.tvStatusBadge.setTextColor(0xFFFF4D4F.toInt())
                }
                else -> {
                    binding.tvStatusBadge.isVisible = true
                    binding.tvStatusBadge.text = context.getString(R.string.creator_status_pending)
                    binding.tvStatusBadge.setTextColor(0xFFFF8C00.toInt())
                }
            }
        }

        private fun bindVideoPreviewClick(item: CreatorVideoPost) {
            val context = binding.root.context
            binding.video.foreground = null
            binding.video.isClickable = true
            if (item.status == STATUS_PASS) {
                binding.video.setOnClickListener {
                    context.startActivity(
                        Intent(context, PlayVideoActivity::class.java).apply {
                            putExtra("video_id", item.videoId)
                        },
                    )
                }
            } else {
                binding.video.setOnClickListener {
                    val msg = if (item.status == STATUS_REJECT) {
                        context.getString(R.string.creator_status_rejected)
                    } else {
                        context.getString(R.string.creator_video_play_after_pass)
                    }
                    ToastUtils.showShort(context, msg)
                }
            }
        }

        private fun formatStat(count: Int): String {
            return if (count > 0) VideoDataUtils.formatCount(count) else "-"
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<CreatorVideoPost>() {
        override fun areItemsTheSame(oldItem: CreatorVideoPost, newItem: CreatorVideoPost) =
            oldItem.videoId == newItem.videoId

        override fun areContentsTheSame(oldItem: CreatorVideoPost, newItem: CreatorVideoPost) =
            oldItem == newItem
    }
}
