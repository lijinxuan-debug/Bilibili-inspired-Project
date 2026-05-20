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
import com.example.bilibili.data.model.CommentItem
import com.example.bilibili.databinding.ActivityCommentReplyBinding
import com.example.bilibili.util.RetrofitClient
import com.example.bilibili.util.SPUtils
import com.example.bilibili.util.ToastUtils
import com.google.android.material.bottomsheet.BottomSheetDialog
import org.json.JSONArray
import org.json.JSONObject
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
    private var pageNo = 1
    private var isLoading = false
    private var hasMore = true

    companion object {
        private const val ARG_COMMENT_ID = "comment_id"
        private const val ARG_REPLY_USER_ID = "reply_user_id"
        private const val ARG_REPLY_NICK_NAME = "reply_nick_name"
        private const val ARG_VIDEO_ID = "video_id"

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
        binding.tvTitle.text = if (replyNickName != null) "回复 @$replyNickName" else "全部回复"

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
                val result = withContext(Dispatchers.IO) {
                    commentService.loadComment(
                        videoId = videoId ?: "",
                        pageNo = pageNo,
                        orderType = 0 // 热度排序
                    )
                }

                val jsonObject = JSONObject(result)
                val data = jsonObject.optJSONArray("data")

                if (data != null && data.length() > 0) {
                    for (i in 0 until data.length()) {
                        val commentObj = data.getJSONObject(i)
                        val comment = parseCommentItem(commentObj)

                        // 只添加当前评论的子回复
                        if (comment.pCommentId == commentId) {
                            comments.add(comment)
                        }
                    }

                    if (comments.isEmpty()) {
                        binding.tvEmpty.visibility = View.VISIBLE
                        binding.rvReplies.visibility = View.GONE
                    } else {
                        binding.tvEmpty.visibility = View.GONE
                        binding.rvReplies.visibility = View.VISIBLE
                        adapter?.submitList(comments.toList())
                    }

                    hasMore = data.length() >= 10 // 假设每页10条
                } else {
                    hasMore = false
                    if (pageNo == 1) {
                        binding.tvEmpty.visibility = View.VISIBLE
                        binding.rvReplies.visibility = View.GONE
                    }
                }
            } catch (e: Exception) {
                Log.e("CommentReplyActivity", "加载回复失败", e)
                ToastUtils.showShort(this@CommentReplyActivity, "加载失败")
            } finally {
                isLoading = false
            }
        }
    }

    private fun loadMoreReplies() {
        pageNo++
        loadReplies()
    }

    private fun parseCommentItem(obj: JSONObject): CommentItem {
        // 解析子评论
        val childrenArray = obj.optJSONArray("children")
        val children = mutableListOf<CommentItem>()
        if (childrenArray != null) {
            for (i in 0 until childrenArray.length()) {
                children.add(parseCommentItem(childrenArray.getJSONObject(i)))
            }
        }

        return CommentItem(
            commentId = obj.optInt("commentId"),
            pCommentId = obj.optInt("pCommentId"),
            videoId = obj.optString("videoId"),
            videoUserId = obj.optString("videoUserId"),
            content = obj.optString("content"),
            imgPath = if (obj.has("imgPath") && !obj.isNull("imgPath")) obj.optString("imgPath") else null,
            userId = obj.optString("userId"),
            replyUserId = if (obj.has("replyUserId") && !obj.isNull("replyUserId")) obj.optString("replyUserId") else null,
            topType = obj.optInt("topType"),
            postTime = obj.optString("postTime"),
            likeCount = obj.optInt("likeCount"),
            hateCount = obj.optInt("hateCount"),
            avatar = obj.optString("avatar"),
            nickName = obj.optString("nickName"),
            replyAvatar = if (obj.has("replyAvatar") && !obj.isNull("replyAvatar")) obj.optString("replyAvatar") else null,
            replyNickName = if (obj.has("replyNickName") && !obj.isNull("replyNickName")) obj.optString("replyNickName") else null,
            children = if (children.isEmpty()) null else children
        )
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
        val currentUserId = SPUtils.getUserId()
        if (currentUserId.isEmpty()) {
            ToastUtils.showShort(this, "请先登录")
            return
        }

        // 乐观更新 UI
        val oldIsLiked = commentItem.isLiked
        commentItem.isLiked = !oldIsLiked

        // 互斥：点赞后取消踩
        if (commentItem.isLiked) {
            commentItem.isHated = false
        }

        // 更新 UI
        val position = comments.indexOf(commentItem)
        if (position >= 0) {
            adapter?.notifyItemChanged(position)
        }

        // 这里需要调用点赞接口
        // 暂时只是模拟
    }

    private fun handleDislikeAction(commentItem: CommentItem) {
        val currentUserId = SPUtils.getUserId()
        if (currentUserId.isEmpty()) {
            ToastUtils.showShort(this, "请先登录")
            return
        }

        // 乐观更新 UI
        val oldIsHated = commentItem.isHated
        commentItem.isHated = !oldIsHated

        // 互斥：踩后取消点赞
        if (commentItem.isHated) {
            commentItem.isLiked = false
        }

        // 更新 UI
        val position = comments.indexOf(commentItem)
        if (position >= 0) {
            adapter?.notifyItemChanged(position)
        }

        // 这里需要调用踩接口
        // 暂时只是模拟
    }

    override fun onDestroy() {
        super.onDestroy()
        adapter = null
    }
}