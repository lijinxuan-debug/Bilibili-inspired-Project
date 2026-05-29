package com.example.bilibili.ui.message

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import androidx.paging.LoadState
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.bilibili.R
import com.example.bilibili.data.api.MessageService
import com.example.bilibili.data.api.PostService
import com.example.bilibili.data.model.UserMessageItem
import com.example.bilibili.databinding.ActivityMessageCategoryBinding
import com.example.bilibili.ui.user.UserProfileActivity
import com.example.bilibili.util.ApiJson.isSuccess
import com.example.bilibili.util.PagingUiHelper
import com.example.bilibili.util.RetrofitClient
import com.example.bilibili.util.ToastUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.json.JSONObject

class MessageCategoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMessageCategoryBinding
    private val messageService = RetrofitClient.create(MessageService::class.java)
    private val postService = RetrofitClient.create(PostService::class.java)

    private var pageMode: Int = MODE_REPLY
    private var pagingJob: Job? = null
    private lateinit var categoryAdapter: MessageCategoryAdapter
    private lateinit var likeAdapter: LikeMessageAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMessageCategoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        pageMode = intent.getIntExtra(EXTRA_MODE, MODE_REPLY)
        binding.tvTitle.text = when (pageMode) {
            MODE_LIKE -> getString(R.string.message_quick_like)
            MODE_FANS -> getString(R.string.message_quick_fans)
            else -> getString(R.string.message_quick_reply)
        }

        categoryAdapter = MessageCategoryAdapter(
            pageMode = pageMode,
            onFollowBack = { item -> followBack(item) },
            onDelete = { item -> confirmDeleteMessage(item) },
        )
        likeAdapter = LikeMessageAdapter(
            onDelete = { item -> confirmDeleteMessage(item) },
        )
        binding.rvList.layoutManager = LinearLayoutManager(this)
        binding.rvList.adapter = if (pageMode == MODE_LIKE) likeAdapter else categoryAdapter

        binding.btnBack.setOnClickListener { finish() }
        applyHeaderStyle()
        setupSwipeRefresh()
        collectPaging()
    }

    private fun applyHeaderStyle() {
        if (pageMode == MODE_REPLY) {
            binding.dividerHeader.isVisible = true
            return
        }
        binding.dividerHeader.isVisible = true
        binding.tvTitle.updateLayoutParams<ConstraintLayout.LayoutParams> {
            width = ConstraintLayout.LayoutParams.WRAP_CONTENT
            startToStart = ConstraintLayout.LayoutParams.PARENT_ID
            endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
            startToEnd = ConstraintLayout.LayoutParams.UNSET
            endToStart = ConstraintLayout.LayoutParams.UNSET
            marginStart = 0
            horizontalBias = 0.5f
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setColorSchemeResources(R.color.bili_pink)
        binding.swipeRefresh.setOnRefreshListener {
            if (pageMode == MODE_LIKE) likeAdapter.refresh() else categoryAdapter.refresh()
        }
        val listAdapter = if (pageMode == MODE_LIKE) likeAdapter else categoryAdapter
        PagingUiHelper.bindOverlayEmptyState(
            this,
            binding.tvEmpty,
            listAdapter,
        ) { state ->
            binding.swipeRefresh.isRefreshing = state.refresh is LoadState.Loading
        }
    }

    private fun collectPaging() {
        pagingJob?.cancel()
        pagingJob = lifecycleScope.launch {
            if (pageMode == MODE_LIKE) {
                createLikePagerFlow().collectLatest { pagingData ->
                    likeAdapter.submitData(pagingData)
                }
            } else {
                createCategoryPagerFlow().collectLatest { pagingData ->
                    categoryAdapter.submitData(pagingData)
                }
            }
        }
    }

    private fun createLikePagerFlow() = androidx.paging.Pager(
        config = com.example.bilibili.util.PagingDefaults.videoListConfig(),
        pagingSourceFactory = {
            MessagePagingSource(
                messageType = MessageTypes.LIKE,
                messageTypes = "${MessageTypes.LIKE},${MessageTypes.COLLECTION}",
            )
        },
    ).flow

    private fun createCategoryPagerFlow() = androidx.paging.Pager(
        config = com.example.bilibili.util.PagingDefaults.videoListConfig(),
        pagingSourceFactory = {
            when (pageMode) {
                MODE_FANS -> MessagePagingSource(messageType = MessageTypes.FANS)
                else -> MessagePagingSource(messageType = MessageTypes.COMMENT)
            }
        },
    ).flow

    private fun confirmDeleteMessage(item: UserMessageItem) {
        AlertDialog.Builder(this)
            .setMessage(R.string.message_delete_confirm)
            .setPositiveButton(android.R.string.ok) { _, _ -> deleteMessage(item) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun deleteMessage(item: UserMessageItem) {
        if (item.messageId <= 0) return
        lifecycleScope.launch {
            try {
                val ok = JSONObject(messageService.delMessage(item.messageId)).isSuccess()
                if (ok) {
                    ToastUtils.showShort(this@MessageCategoryActivity, getString(R.string.message_delete_done))
                    if (pageMode == MODE_LIKE) {
                        likeAdapter.refresh()
                    } else {
                        categoryAdapter.refresh()
                    }
                    runCatching { MessageUnreadCenter.refreshFromApi(messageService) }
                } else {
                    ToastUtils.showShort(this@MessageCategoryActivity, "删除失败")
                }
            } catch (e: Exception) {
                ToastUtils.showShort(this@MessageCategoryActivity, e.message ?: "删除失败")
            }
        }
    }

    private fun openUserProfile(userId: String) {
        if (userId.isBlank()) {
            ToastUtils.showShort(this, "用户信息不可用")
            return
        }
        startActivity(
            Intent(this, UserProfileActivity::class.java).apply {
                putExtra("user_id", userId)
            },
        )
    }

    private fun followBack(item: UserMessageItem) {
        if (item.sendUserId.isBlank()) return
        lifecycleScope.launch {
            try {
                val response = JSONObject(postService.focus(item.sendUserId))
                if (response.isSuccess()) {
                    ToastUtils.showShort(this@MessageCategoryActivity, getString(R.string.message_follow_back_success))
                } else {
                    ToastUtils.showShort(
                        this@MessageCategoryActivity,
                        response.optString("info", "回关失败"),
                    )
                }
            } catch (e: Exception) {
                ToastUtils.showShort(this@MessageCategoryActivity, e.message ?: "回关失败")
            }
        }
    }

    companion object {
        const val EXTRA_MODE = "extra_mode"
        const val MODE_REPLY = 1
        const val MODE_LIKE = 2
        const val MODE_FANS = 3

        fun start(context: Context, mode: Int) {
            context.startActivity(
                Intent(context, MessageCategoryActivity::class.java).putExtra(EXTRA_MODE, mode),
            )
        }
    }
}
