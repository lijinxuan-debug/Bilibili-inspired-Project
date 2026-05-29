package com.example.bilibili.ui.playVideo.comment

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.bilibili.R
import com.example.bilibili.data.api.CommentService
import android.app.Activity
import com.example.bilibili.data.model.CommentItem
import com.example.bilibili.data.model.UserMessageItem
import com.example.bilibili.databinding.ActivityCommentReplyBinding
import com.example.bilibili.ui.playVideo.CommentAnchor
import com.example.bilibili.util.RetrofitClient
import com.example.bilibili.util.SPUtils
import com.example.bilibili.util.ToastUtils
import com.example.bilibili.util.UserInfoText
import com.google.android.material.bottomsheet.BottomSheetDialog
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CommentReplyActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCommentReplyBinding
    private val commentService = RetrofitClient.create(CommentService::class.java)
    private var adapter: CommentReplyAdapter? = null
    private var commentId: Int = -1
    private var replyUserId: String? = null
    private var replyNickName: String? = null
    private var videoId: String? = null
    private val comments = mutableListOf<CommentItem>()
    private val allRootComments = mutableListOf<CommentItem>()
    private var pageNo = 1
    private var isLoading = false
    private var hasMore = true
    private var anchorSendUserId: String? = null
    private var anchorContent: String? = null
    private var anchorHighlightApplied = false

    companion object {
        private const val ARG_COMMENT_ID = "comment_id"
        private const val ARG_REPLY_USER_ID = "reply_user_id"
        private const val ARG_REPLY_NICK_NAME = "reply_nick_name"
        private const val ARG_VIDEO_ID = "video_id"
        private const val ARG_ANCHOR_SEND_USER_ID = "anchor_send_user_id"
        private const val ARG_ANCHOR_CONTENT = "anchor_content"

        fun startFromMessage(activity: Activity, item: UserMessageItem) {
            val intent = Intent(activity, CommentReplyActivity::class.java).apply {
                putExtra(ARG_COMMENT_ID, item.commentId)
                putExtra(ARG_VIDEO_ID, item.videoId)
                putExtra(ARG_ANCHOR_SEND_USER_ID, item.sendUserId)
                putExtra(ARG_ANCHOR_CONTENT, item.messageContent)
            }
            activity.startActivity(intent)
        }

        fun start(
            activity: FragmentActivity,
            commentId: Int,
            replyUserId: String?,
            replyNickName: String?,
            videoId: String
        ) {
            val intent = Intent(activity, CommentReplyActivity::class.java).apply {
                putExtra(ARG_COMMENT_ID, commentId)
                putExtra(ARG_REPLY_USER_ID, replyUserId)
                putExtra(ARG_REPLY_NICK_NAME, replyNickName)
                putExtra(ARG_VIDEO_ID, videoId)
            }
            activity.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityCommentReplyBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 获取参数
        commentId = intent.getIntExtra(ARG_COMMENT_ID, -1)
        replyUserId = intent.getStringExtra(ARG_REPLY_USER_ID)
        replyNickName = intent.getStringExtra(ARG_REPLY_NICK_NAME)
        videoId = intent.getStringExtra(ARG_VIDEO_ID)
        anchorSendUserId = intent.getStringExtra(ARG_ANCHOR_SEND_USER_ID)
        anchorContent = intent.getStringExtra(ARG_ANCHOR_CONTENT)

        if (commentId == -1) {
            ToastUtils.showShort(this, "评论信息错误")
            finish()
            return
        }

        setupViews()
        loadReplies()
        setupRecyclerViewScrollListener()
    }

    private fun setupViews() {
        // 设置标题
        binding.tvTitle.text = when {
            !anchorSendUserId.isNullOrBlank() -> getString(R.string.message_comment_detail_title)
            replyNickName != null -> "回复 @$replyNickName"
            else -> "全部回复"
        }

        // 返回按钮
        binding.ivBack.setOnClickListener { finish() }

        // 底部输入框
        binding.tvFakeInput.setOnClickListener {
            showCommentDialog()
        }

        // 初始化RecyclerView
        adapter = CommentReplyAdapter(
            onAvatarClick = { userId ->
                // 跳转到用户主页
                val intent = Intent(this, com.example.bilibili.ui.user.UserProfileActivity::class.java).apply {
                    putExtra("user_id", userId)
                }
                startActivity(intent)
            },
            onCommentClick = { comment ->
                // 回复评论
                showCommentDialog(comment.nickName, comment.userId)
            },
            onLikeClick = { comment ->
                handleLikeAction(comment)
            },
            onDislikeClick = { comment ->
                handleDislikeAction(comment)
            }
        )

        binding.rvReplies.adapter = adapter
        binding.rvReplies.layoutManager = LinearLayoutManager(this)
    }

    private fun setupRecyclerViewScrollListener() {
        binding.rvReplies.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)

                if (!isLoading && hasMore && dy > 0) {
                    val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                    val visibleItemCount = layoutManager.childCount
                    val totalItemCount = layoutManager.itemCount
                    val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()

                    if (visibleItemCount + firstVisibleItemPosition >= totalItemCount - 5) {
                        loadMoreReplies()
                    }
                }
            }
        })
    }

    private fun loadReplies() {
        if (isLoading) return
        isLoading = true

        lifecycleScope.launch {
            try {
                val pageResult = withContext(Dispatchers.IO) {
                    commentService.loadComment(
                        videoId = videoId ?: "",
                        pageNo = pageNo,
                        orderType = 0,
                    )
                }.let { CommentResponseParser.parsePage(it) }

                if (pageResult == null) {
                    showEmptyIfNeeded()
                    hasMore = false
                    return@launch
                }

                allRootComments.addAll(pageResult.comments)
                hasMore = pageResult.hasMore

                val replies = CommentTreeHelper.directReplies(allRootComments, commentId)
                if (replies.isNotEmpty()) {
                    bindReplies(replies)
                } else if (hasMore) {
                    pageNo++
                    isLoading = false
                    loadReplies()
                    return@launch
                } else {
                    bindReplies(replies)
                }
            } catch (e: Exception) {
                Log.e("CommentReplyActivity", "加载回复失败", e)
                ToastUtils.showShort(this@CommentReplyActivity, "加载失败")
            } finally {
                isLoading = false
            }
        }
    }

    private fun bindReplies(replies: List<CommentItem>) {
        comments.clear()
        comments.addAll(replies)
        if (comments.isEmpty()) {
            binding.tvEmpty.visibility = View.VISIBLE
            binding.rvReplies.visibility = View.GONE
        } else {
            binding.tvEmpty.visibility = View.GONE
            binding.rvReplies.visibility = View.VISIBLE
            adapter?.submitList(comments.toList())
            applyAnchorHighlight()
        }
    }

    private fun showEmptyIfNeeded() {
        if (comments.isEmpty()) {
            binding.tvEmpty.visibility = View.VISIBLE
            binding.rvReplies.visibility = View.GONE
        }
    }

    private fun loadMoreReplies() {
        if (!hasMore || isLoading) return
        pageNo++
        loadReplies()
    }

    private fun applyAnchorHighlight() {
        if (anchorHighlightApplied) return
        val userId = anchorSendUserId.orEmpty()
        val content = anchorContent.orEmpty()
        if (userId.isBlank() && content.isBlank()) return
        val anchor = CommentAnchor(sendUserId = userId, content = content, parentCommentId = commentId)
        val result = CommentLocator.findInFlatList(comments, anchor) ?: run {
            ToastUtils.showShort(this, getString(R.string.message_comment_not_found))
            return
        }
        anchorHighlightApplied = true
        adapter?.setHighlightedCommentId(result.highlightCommentId)
        binding.rvReplies.post {
            val position = comments.indexOfFirst { it.commentId == result.highlightCommentId }
            if (position >= 0) {
                binding.rvReplies.smoothScrollToPosition(position)
            }
            ToastUtils.showShort(this, getString(R.string.message_comment_located))
        }
    }

    private fun showCommentDialog(replyToName: String? = null, replyToUserId: String? = null) {
        val currentUserId = SPUtils.getUserId()
        if (currentUserId.isEmpty()) {
            ToastUtils.showShort(this, "请先登录")
            return
        }

        val bottomSheetDialog = BottomSheetDialog(this, R.style.TransparentBottomSheetStyle)
        val dialogBinding = com.example.bilibili.databinding.DialogCommentInputBinding.inflate(layoutInflater)
        bottomSheetDialog.setContentView(dialogBinding.root)

        // 设置回复提示
        dialogBinding.etCommentInput.hint = if (replyToName != null) {
            "回复 @$replyToName"
        } else {
            "尊重是评论打动人心的入场券"
        }

        // 字数统计和输入监听
        dialogBinding.etCommentInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val length = s?.length ?: 0
                dialogBinding.tvSend.isEnabled = length > 0
                dialogBinding.tvSend.alpha = if (length > 0) 1f else 0.5f
            }

            override fun afterTextChanged(s: android.text.Editable?) {}
        })
        // 图片功能已移除

        // 发送按钮
        dialogBinding.tvSend.setOnClickListener {
            val content = dialogBinding.etCommentInput.text.toString().trim()
            if (content.isEmpty()) {
                ToastUtils.showShort(this, "请输入评论内容")
                return@setOnClickListener
            }

            if (videoId.isNullOrEmpty()) {
                ToastUtils.showShort(this, "视频信息加载中，请稍后")
                return@setOnClickListener
            }

            // 这里需要调用发布评论的接口
            // 暂时只是模拟
            ToastUtils.showShort(this, "评论已提交")

            // 隐藏键盘和对话框
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(dialogBinding.etCommentInput.windowToken, 0)
            bottomSheetDialog.dismiss()
        }

        bottomSheetDialog.show()

        // 延迟弹出键盘并聚焦输入框
        dialogBinding.etCommentInput.postDelayed({
            dialogBinding.etCommentInput.requestFocus()
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(dialogBinding.etCommentInput, InputMethodManager.SHOW_IMPLICIT)
        }, 150)
    }

    private fun handleLikeAction(commentItem: CommentItem) {
        if (SPUtils.getUserId().isEmpty()) {
            ToastUtils.showShort(this, "请先登录")
            return
        }
        CommentActionHelper.applyLikeToggle(commentItem)
        notifyReplyItemChanged(commentItem)
    }

    private fun handleDislikeAction(commentItem: CommentItem) {
        if (SPUtils.getUserId().isEmpty()) {
            ToastUtils.showShort(this, "请先登录")
            return
        }
        CommentActionHelper.applyDislikeToggle(commentItem)
        notifyReplyItemChanged(commentItem)
    }

    private fun notifyReplyItemChanged(commentItem: CommentItem) {
        val position = comments.indexOfFirst { it.commentId == commentItem.commentId }
        if (position >= 0) {
            adapter?.notifyItemChanged(position)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        adapter = null
    }
}