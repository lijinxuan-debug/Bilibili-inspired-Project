package com.example.bilibili.ui.creator

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.example.bilibili.data.model.CreatorCommentItem
import com.example.bilibili.databinding.ItemCreatorCommentBinding
import com.example.bilibili.util.GlideEngine

class CreatorCommentAdapter(
    private val onDelete: (CreatorCommentItem) -> Unit,
    private val onUserClick: (CreatorCommentItem) -> Unit,
    private val onVideoClick: (CreatorCommentItem) -> Unit,
) : PagingDataAdapter<CreatorCommentItem, CreatorCommentAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCreatorCommentBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false,
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position) ?: return
        holder.bind(item)
        holder.binding.btnDelete.setOnClickListener { onDelete(item) }
        holder.binding.ivAvatar.setOnClickListener { onUserClick(item) }
        holder.binding.tvNickname.setOnClickListener { onUserClick(item) }
        holder.binding.ivVideoCover.setOnClickListener { onVideoClick(item) }
    }

    class ViewHolder(
        val binding: ItemCreatorCommentBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: CreatorCommentItem) {
            binding.tvNickname.text = item.nickName
            binding.tvTime.text = item.postTime
            binding.tvContent.text = item.content
            GlideEngine.loadUserAvatar(binding.root.context, item.avatar, binding.ivAvatar)
            GlideEngine.loadVideoCover(
                binding.root.context,
                item.videoCover,
                binding.ivVideoCover,
                cornerRadius = 6,
            )
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<CreatorCommentItem>() {
        override fun areItemsTheSame(oldItem: CreatorCommentItem, newItem: CreatorCommentItem) =
            oldItem.commentId == newItem.commentId

        override fun areContentsTheSame(oldItem: CreatorCommentItem, newItem: CreatorCommentItem) =
            oldItem == newItem
    }
}
