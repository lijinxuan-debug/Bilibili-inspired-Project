package com.example.bilibili.ui.playVideo.comment

import android.graphics.PorterDuff
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.example.bilibili.R
import com.example.bilibili.data.model.CommentItem
import com.example.bilibili.databinding.ItemVideoCommentBinding
import com.example.bilibili.util.GlideEngine

class CommentAdapter : RecyclerView.Adapter<CommentAdapter.CommentViewHolder>() {

    // 1. 定义 DiffUtil.ItemCallback
    private val diffCallback = object : DiffUtil.ItemCallback<CommentItem>() {
        override fun areItemsTheSame(oldItem: CommentItem, newItem: CommentItem): Boolean {
            return oldItem.commentId == newItem.commentId
        }

        override fun areContentsTheSame(oldItem: CommentItem, newItem: CommentItem): Boolean {
            return oldItem == newItem
        }
    }

    // 2. 使用 AsyncListDiffer 管理数据
    val differ = AsyncListDiffer(this, diffCallback)

    fun setData(newList: List<CommentItem>, commitCallback: Runnable? = null) {
        differ.submitList(newList, commitCallback)
    }

    // 3. 函数式回调接口
    var onCommentClick: ((CommentItem) -> Unit)? = null
    var onLikeClick: ((CommentItem) -> Unit)? = null
    var onDislikeClick: ((CommentItem) -> Unit)? = null
    var onAvatarClick: ((String) -> Unit)? = null  // 头像点击回调，参数是用户ID
    var onImageClick: ((String) -> Unit)? = null   // 图片点击预览回调

    // 展开的评论ID集合
    private val expandedCommentIds = mutableSetOf<Int>()

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

    // 获取评论在列表中的位置
    fun getItemPosition(commentItem: CommentItem): Int {
        return differ.currentList.indexOf(commentItem)
    }

    // 切换展开状态
    fun toggleExpand(commentId: Int) {
        if (expandedCommentIds.contains(commentId)) {
            expandedCommentIds.remove(commentId)
        } else {
            expandedCommentIds.add(commentId)
        }
        // 查找并刷新该项
        val position = differ.currentList.indexOfFirst { it.commentId == commentId }
        if (position >= 0) {
            notifyItemChanged(position)
        }
    }

    inner class CommentViewHolder(private val binding: ItemVideoCommentBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: CommentItem) {
            // 1. 基础信息绑定
            binding.tvUserName.text = item.nickName
            binding.tvCommentText.text = item.content
            // 只显示日期部分，去掉时间
            val dateOnly = item.postTime.split(" ").getOrNull(0) ?: item.postTime
            binding.tvTimePlace.text = "${dateOnly} 回复"
            binding.tvLikeCount.text = item.likeCount.toString()
            binding.tvDislikeCount.text = item.hateCount.toString()

            // 2. 图片加载
            GlideEngine.loadUserAvatar(binding.root.context, item.avatar, binding.ivAvatar)
            binding.ivAvatar.setOnClickListener {
                onAvatarClick?.invoke(item.userId)
            }

            // 评论图片加载和点击预览
            if (!item.imgPath.isNullOrEmpty()) {
                binding.ivCommentImage.visibility = View.VISIBLE
                // 使用正确的图片加载方式
                GlideEngine.loadCommentImage(binding.root.context, item.imgPath, binding.ivCommentImage)
                binding.ivCommentImage.setOnClickListener {
                    onImageClick?.invoke(item.imgPath)
                }
            } else {
                binding.ivCommentImage.visibility = View.GONE
                binding.ivCommentImage.setOnClickListener(null)
            }

            // 3. 根据状态设置图标和颜色
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

            // 4. 函数式点击事件处理
            setupClickListeners(item)

            // 5. 设置回复预览和展开逻辑
            setupReplySection(item)
        }

        private fun setupClickListeners(item: CommentItem) {
            // 点击整个评论项（用于回复）
            binding.root.setOnClickListener {
                onCommentClick?.invoke(item)
            }

            // 点赞点击
            binding.ivLike.setOnClickListener { view ->
                // 阻止事件冒泡到 root
                view.isClickable = false
                view.post { view.isClickable = true }

                onLikeClick?.invoke(item)
            }

            // 踩点击
            binding.ivDislike.setOnClickListener { view ->
                // 阻止事件冒泡到 root
                view.isClickable = false
                view.post { view.isClickable = true }

                onDislikeClick?.invoke(item)
            }
        }

        private fun setupReplySection(item: CommentItem) {
            val children = item.children
            val isExpanded = expandedCommentIds.contains(item.commentId)

            // 如果有子评论
            if (!children.isNullOrEmpty()) {
                if (isExpanded) {
                    // 展开状态：显示子评论列表
                    binding.llReplyPreview.visibility = View.GONE
                    binding.llExpandedReplies.visibility = View.VISIBLE

                    // 设置收起按钮点击
                    binding.tvCollapseReplies.setOnClickListener {
                        toggleExpand(item.commentId)
                    }

                    // 设置子评论适配器
                    if (binding.rvChildReplies.adapter == null) {
                        binding.rvChildReplies.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(binding.root.context)
                        binding.rvChildReplies.adapter = CommentThreadAdapter(
                            onAvatarClick = { userId ->
                                onAvatarClick?.invoke(userId)
                            },
                            onReplyClick = { replyItem ->
                                // 子评论点击回复，传递到外层的onCommentClick
                                onCommentClick?.invoke(replyItem)
                            },
                            onLikeClick = { replyItem ->
                                // 子评论点击点赞，传递到外层的onLikeClick
                                onLikeClick?.invoke(replyItem)
                            },
                            onDislikeClick = { replyItem ->
                                // 子评论点击踩，传递到外层的onDislikeClick
                                onDislikeClick?.invoke(replyItem)
                            },
                            onImageClick = { imagePath ->
                                // 子评论图片点击预览
                                onImageClick?.invoke(imagePath)
                            }
                        )
                    }

                    // 每次都更新数据，确保显示最新状态
                    (binding.rvChildReplies.adapter as CommentThreadAdapter).submitList(children, isOriginalComment = false)
                } else {
                    // 收起状态：显示回复预览
                    binding.llReplyPreview.visibility = View.VISIBLE
                    binding.llExpandedReplies.visibility = View.GONE

                    val firstReply = children[0]
                    binding.tvFirstReply.text = "${firstReply.nickName}: ${firstReply.content}"

                    // 点击回复预览展开
                    binding.llReplyPreview.setOnClickListener {
                        toggleExpand(item.commentId)
                    }

                    if (children.size > 1) {
                        binding.tvReplyCountLink.visibility = View.VISIBLE
                        binding.tvReplyCountLink.text = "共${children.size}条回复 >"

                        // 点击"共X条回复 >"也展开
                        binding.tvReplyCountLink.setOnClickListener {
                            toggleExpand(item.commentId)
                        }
                    } else {
                        binding.tvReplyCountLink.visibility = View.GONE
                    }
                }
            } else {
                // 没有子评论
                binding.llReplyPreview.visibility = View.GONE
                binding.llExpandedReplies.visibility = View.GONE
            }
        }
    }
}