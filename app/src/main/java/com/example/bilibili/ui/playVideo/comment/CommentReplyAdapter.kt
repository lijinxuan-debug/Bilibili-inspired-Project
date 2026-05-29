package com.example.bilibili.ui.playVideo.comment

import android.graphics.PorterDuff
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.bilibili.R
import com.example.bilibili.data.model.CommentItem
import com.example.bilibili.databinding.ItemVideoCommentBinding
import com.example.bilibili.util.GlideEngine

class CommentReplyAdapter(
    private val onAvatarClick: (String) -> Unit,
    private val onCommentClick: (CommentItem) -> Unit,
    private val onLikeClick: (CommentItem) -> Unit,
    private val onDislikeClick: (CommentItem) -> Unit,
) : RecyclerView.Adapter<CommentReplyAdapter.CommentReplyViewHolder>() {

    private var comments = listOf<CommentItem>()
    private var highlightedCommentId: Int? = null

    fun setHighlightedCommentId(commentId: Int?) {
        highlightedCommentId = commentId
        notifyDataSetChanged()
    }

    fun submitList(newComments: List<CommentItem>) {
        comments = newComments
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommentReplyViewHolder {
        val binding = ItemVideoCommentBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return CommentReplyViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CommentReplyViewHolder, position: Int) {
        holder.bind(comments[position])
    }

    override fun getItemCount(): Int = comments.size

    inner class CommentReplyViewHolder(private val binding: ItemVideoCommentBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: CommentItem) {
            binding.root.setBackgroundResource(
                if (item.commentId == highlightedCommentId) {
                    R.drawable.bg_comment_anchor_highlight
                } else {
                    android.R.color.white
                },
            )

            binding.tvUserName.text = item.nickName
            binding.tvCommentText.text = item.content
            binding.tvTimePlace.text = item.postTime
            binding.tvLikeCount.text = item.likeCount.toString()
            binding.tvDislikeCount.text = item.hateCount.toString()

            // 头像加载和点击
            GlideEngine.loadUserAvatar(binding.root.context, item.avatar, binding.ivAvatar)
            binding.ivAvatar.setOnClickListener {
                onAvatarClick(item.userId)
            }

            // 图片显示
            if (!item.imgPath.isNullOrEmpty()) {
                binding.ivCommentImage.visibility = View.VISIBLE
                GlideEngine.loadVideoCover(binding.root.context, item.imgPath, binding.ivCommentImage)
            } else {
                binding.ivCommentImage.visibility = View.GONE
            }

            // 隐藏回复预览（楼中楼不需要）
            binding.llReplyPreview.visibility = View.GONE

            // 点赞状态
            if (item.isLiked) {
                binding.ivLike.setImageResource(R.drawable.ic_like_solid)
                binding.ivLike.setColorFilter(
                    ContextCompat.getColor(binding.root.context, R.color.bilibili_pink),
                    android.graphics.PorterDuff.Mode.SRC_IN
                )
                binding.tvLikeCount.setTextColor(
                    ContextCompat.getColor(binding.root.context, R.color.bilibili_pink)
                )
            } else {
                binding.ivLike.setImageResource(R.drawable.ic_like_outline)
                binding.ivLike.setColorFilter(
                    ContextCompat.getColor(binding.root.context, R.color.sl_comment_item_color_default),
                    android.graphics.PorterDuff.Mode.SRC_IN
                )
                binding.tvLikeCount.setTextColor(
                    ContextCompat.getColor(binding.root.context, R.color.sl_comment_item_color_default)
                )
            }

            // 踩状态
            if (item.isHated) {
                binding.ivDislike.setImageResource(R.drawable.ic_dislike_solid)
                binding.ivDislike.setColorFilter(
                    ContextCompat.getColor(binding.root.context, R.color.bilibili_pink),
                    android.graphics.PorterDuff.Mode.SRC_IN
                )
                binding.tvDislikeCount.setTextColor(
                    ContextCompat.getColor(binding.root.context, R.color.bilibili_pink)
                )
            } else {
                binding.ivDislike.setImageResource(R.drawable.ic_dislike_outline)
                binding.ivDislike.setColorFilter(
                    ContextCompat.getColor(binding.root.context, R.color.sl_comment_item_color_default),
                    android.graphics.PorterDuff.Mode.SRC_IN
                )
                binding.tvDislikeCount.setTextColor(
                    ContextCompat.getColor(binding.root.context, R.color.sl_comment_item_color_default)
                )
            }

            // 点击事件
            binding.root.setOnClickListener {
                onCommentClick(item)
            }

            binding.ivLike.setOnClickListener { view ->
                view.isClickable = false
                view.post { view.isClickable = true }
                onLikeClick(item)
            }

            binding.ivDislike.setOnClickListener { view ->
                view.isClickable = false
                view.post { view.isClickable = true }
                onDislikeClick(item)
            }
        }
    }
}