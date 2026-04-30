package com.example.bilibili.ui.playVideo.comment

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.example.bilibili.data.model.CommentItem
import com.example.bilibili.databinding.ItemVideoCommentBinding
import com.example.bilibili.util.GlideEngine

class CommentAdapter : RecyclerView.Adapter<CommentAdapter.CommentViewHolder>() {

    // 1. 定义 DiffUtil.ItemCallback
    private val diffCallback = object : DiffUtil.ItemCallback<CommentItem>() {
        override fun areItemsTheSame(oldItem: CommentItem, newItem: CommentItem): Boolean {
            // 通常比较 ID
            return oldItem.commentId == newItem.commentId
        }

        override fun areContentsTheSame(oldItem: CommentItem, newItem: CommentItem): Boolean {
            // 比较内容是否发生变化（点赞数、内容、回复数等）
            // 因为 CommentItem 是 Data Class，直接用 == 比较所有字段
            return oldItem == newItem
        }
    }

    // 2. 使用 AsyncListDiffer 管理数据
    private val differ = AsyncListDiffer(this, diffCallback)

    fun setData(newList: List<CommentItem>) {
        differ.submitList(newList)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommentViewHolder {
        val binding = ItemVideoCommentBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return CommentViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CommentViewHolder, position: Int) {
        holder.bind(differ.currentList[position])
    }

    override fun getItemCount(): Int = differ.currentList.size

    inner class CommentViewHolder(private val binding: ItemVideoCommentBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: CommentItem) {
            // 1. 基础信息绑定 (保持不变)
            binding.tvUserName.text = item.nickName
            binding.tvCommentText.text = item.content
            binding.tvTimePlace.text = "${item.postTime} 回复"
            binding.tvLikeCount.text = item.likeCount.toString()
            binding.tvDislikeCount.text = item.hateCount.toString()

            // 2. 图片加载 (保持不变)
            GlideEngine.loadUserAvatar(binding.root.context, item.avatar, binding.ivAvatar)
            if (!item.imgPath.isNullOrEmpty()) {
                binding.ivCommentImage.visibility = View.VISIBLE
                GlideEngine.loadVideoCover(binding.root.context, item.imgPath, binding.ivCommentImage)
            } else {
                binding.ivCommentImage.visibility = View.GONE
            }

            // 3. 核心变色逻辑：设置 Activated 状态
            // 记得在 XML 里把 app:tint 和 textColor 都设置成 @color/sl_comment_item_color
            binding.ivLike.isActivated = item.isLiked
            binding.tvLikeCount.isActivated = item.isLiked

            binding.ivDislike.isActivated = item.isHated
            binding.tvDislikeCount.isActivated = item.isHated

            // 4. 点击事件处理
            setupClickListeners(item)

            setupReplyPreview(item.children)
        }

        private fun setupClickListeners(item: CommentItem) {
            // 点赞点击
            binding.ivLike.setOnClickListener {
                val oldIsLiked = item.isLiked
                item.isLiked = !oldIsLiked

                // 互斥：如果点赞了，就自动取消“踩”
                if (item.isLiked) {
                    item.isHated = false
                    // item.likeCount++ // 可选：本地即时增加数值
                } else {
                    // item.likeCount-- // 可选：本地即时减少数值
                }

                // 使用 DiffUtil 刷新该行 (submitList 会自动处理，或者手动局部刷新)
                notifyItemChanged(adapterPosition)

                // 这里通常要调用 ViewModel 的方法去请求后端接口
                // viewModel.doAction(item.commentId, actionType = 2)
            }

            // 踩点击
            binding.ivDislike.setOnClickListener {
                val oldIsHated = item.isHated
                item.isHated = !oldIsHated

                // 互斥：如果踩了，就自动取消“点赞”
                if (item.isHated) {
                    item.isLiked = false
                }

                notifyItemChanged(adapterPosition)

                // viewModel.doAction(item.commentId, actionType = 3)
            }
        }

        private fun setupReplyPreview(children: List<CommentItem>?) {
            if (!children.isNullOrEmpty()) {
                binding.llReplyPreview.visibility = View.VISIBLE

                val firstReply = children[0]
                binding.tvFirstReply.text = "${firstReply.nickName}: ${firstReply.content}"

                if (children.size > 1) {
                    binding.tvReplyCountLink.visibility = View.VISIBLE
                    binding.tvReplyCountLink.text = "共${children.size}条回复 >"
                } else {
                    binding.tvReplyCountLink.visibility = View.GONE
                }
            } else {
                binding.llReplyPreview.visibility = View.GONE
            }
        }
    }
}