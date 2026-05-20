package com.example.bilibili.ui.playVideo.comment

import android.graphics.PorterDuff
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.bilibili.R
import com.example.bilibili.data.model.CommentItem
import com.example.bilibili.databinding.ItemVideoCommentThreadBinding
import com.example.bilibili.util.GlideEngine

class CommentThreadAdapter(
    private val onAvatarClick: (String) -> Unit,
    private val onReplyClick: (CommentItem) -> Unit,
    private val onLikeClick: (CommentItem) -> Unit,
    private val onDislikeClick: (CommentItem) -> Unit,
    private val onImageClick: (String) -> Unit  // 图片点击回调
) : RecyclerView.Adapter<CommentThreadAdapter.ThreadViewHolder>() {

    private var comments = listOf<CommentItem>()
    private var isOriginalComment = false // 是否为原主评论

    fun submitList(newComments: List<CommentItem>, isOriginalComment: Boolean = false) {
        comments = newComments
        this.isOriginalComment = isOriginalComment
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ThreadViewHolder {
        val binding = ItemVideoCommentThreadBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ThreadViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ThreadViewHolder, position: Int) {
        holder.bind(comments[position], isOriginalComment)
    }

    override fun getItemCount(): Int = comments.size

    inner class ThreadViewHolder(private val binding: ItemVideoCommentThreadBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: CommentItem, isOriginalComment: Boolean) {
            // 用户名
            binding.tvUserName.text = item.nickName

            // 回复信息（如果是子评论且不是原主评论）
            if (!item.replyNickName.isNullOrEmpty() && !isOriginalComment) {
                binding.tvReplyInfo.visibility = View.VISIBLE
                binding.tvReplyInfo.text = "回复 @${item.replyNickName}"
            } else {
                binding.tvReplyInfo.visibility = View.GONE
            }

            // 时间 - 只显示日期部分
            val dateOnly = item.postTime.split(" ").getOrNull(0) ?: item.postTime
            binding.tvTimePlace.text = dateOnly

            // 评论内容
            binding.tvCommentText.text = item.content

            // 图片显示和点击预览
            if (!item.imgPath.isNullOrEmpty()) {
                binding.ivCommentImage.visibility = View.VISIBLE
                // 使用正确的图片加载方式
                GlideEngine.loadCommentImage(binding.root.context, item.imgPath, binding.ivCommentImage)
                binding.ivCommentImage.setOnClickListener {
                    onImageClick(item.imgPath)
                }
            } else {
                binding.ivCommentImage.visibility = View.GONE
                binding.ivCommentImage.setOnClickListener(null)
            }

            // 整个评论区域点击事件 - 用于回复
            binding.commentContentArea.setOnClickListener {
                onReplyClick(item)
            }

            // 头像加载和点击
            GlideEngine.loadUserAvatar(binding.root.context, item.avatar, binding.ivAvatar)
            binding.ivAvatar.setOnClickListener {
                onAvatarClick(item.userId)
            }

            // 点赞数
            binding.tvLikeCount.text = item.likeCount.toString()

            // 踩数
            binding.tvDislikeCount.text = item.hateCount.toString()

            // 回复数（子评论）
            if (!item.children.isNullOrEmpty()) {
                binding.tvReplyCount.text = "${item.children.size}条回复"
                binding.llReply.visibility = View.VISIBLE
            } else {
                binding.llReply.visibility = View.GONE
            }

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
            binding.llReply.setOnClickListener {
                onReplyClick(item)
            }

            binding.llLike.setOnClickListener { view ->
                onLikeClick(item)
            }

            binding.llDislike.setOnClickListener { view ->
                onDislikeClick(item)
            }
        }
    }
}