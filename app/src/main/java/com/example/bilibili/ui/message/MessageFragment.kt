package com.example.bilibili.ui.message

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.paging.LoadState
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.bilibili.R
import com.example.bilibili.data.api.MessageService
import com.example.bilibili.databinding.FragmentMessageBinding
import com.example.bilibili.databinding.ItemMessageQuickActionBinding
import com.example.bilibili.util.ApiJson.isSuccess
import com.example.bilibili.util.PagingUiHelper
import com.example.bilibili.util.RetrofitClient
import com.example.bilibili.util.ToastUtils
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.json.JSONObject

class MessageFragment : Fragment() {

    private var _binding: FragmentMessageBinding? = null
    private val binding get() = _binding!!

    private val inboxAdapter = MessageInboxAdapter { item ->
        markMessageRead(item.messageId)
    }
    private val messageService = RetrofitClient.create(MessageService::class.java)
    private val sseClient = MessageSseClient()

    private lateinit var quickReplyBinding: ItemMessageQuickActionBinding
    private lateinit var quickLikeBinding: ItemMessageQuickActionBinding
    private lateinit var quickFansBinding: ItemMessageQuickActionBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentMessageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupInsets()
        setupToolbar()
        setupQuickActions()
        setupInboxList()
        observeBadges()
        MessageUnreadCenter.onInboxShouldRefresh = { inboxAdapter.refresh() }
    }

    override fun onStart() {
        super.onStart()
        sseClient.start { payload ->
            activity?.runOnUiThread {
                MessageUnreadCenter.applySsePayload(payload)
            }
        }
    }

    override fun onStop() {
        sseClient.stop()
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        refreshUnreadCounts()
        inboxAdapter.refresh()
    }

    override fun onDestroyView() {
        MessageUnreadCenter.onInboxShouldRefresh = null
        super.onDestroyView()
        _binding = null
    }

    private fun setupInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.toolbar) { v, insets ->
            val top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            v.updatePadding(top = top)
            insets
        }
    }

    private fun setupToolbar() {
        binding.btnMarkAllRead.setOnClickListener { confirmMarkAllRead() }
    }

    private fun setupQuickActions() {
        quickReplyBinding = ItemMessageQuickActionBinding.bind(binding.quickReply.root)
        quickLikeBinding = ItemMessageQuickActionBinding.bind(binding.quickLike.root)
        quickFansBinding = ItemMessageQuickActionBinding.bind(binding.quickFans.root)

        bindQuickAction(
            quickReplyBinding,
            R.drawable.ic_message_quick_reply,
            getString(R.string.message_quick_reply),
        )
        bindQuickAction(
            quickLikeBinding,
            R.drawable.ic_message_quick_like,
            getString(R.string.message_quick_like),
        )
        bindQuickAction(
            quickFansBinding,
            R.drawable.ic_message_quick_fans,
            getString(R.string.message_quick_fans),
        )

        binding.quickReply.root.setOnClickListener {
            markCategoryRead(MessageCategoryActivity.MODE_REPLY) {
                MessageUnreadCenter.clearReply()
                MessageCategoryActivity.start(requireContext(), MessageCategoryActivity.MODE_REPLY)
            }
        }
        binding.quickLike.root.setOnClickListener {
            markCategoryRead(MessageCategoryActivity.MODE_LIKE) {
                MessageUnreadCenter.clearLike()
                MessageCategoryActivity.start(requireContext(), MessageCategoryActivity.MODE_LIKE)
            }
        }
        binding.quickFans.root.setOnClickListener {
            markCategoryRead(MessageCategoryActivity.MODE_FANS) {
                MessageUnreadCenter.clearFans()
                MessageCategoryActivity.start(requireContext(), MessageCategoryActivity.MODE_FANS)
            }
        }
    }

    private fun bindQuickAction(
        itemBinding: ItemMessageQuickActionBinding,
        iconRes: Int,
        label: String,
    ) {
        itemBinding.viewIconBg.setBackgroundResource(R.drawable.bg_message_quick_grey)
        itemBinding.ivIcon.setImageResource(iconRes)
        itemBinding.tvLabel.text = label
    }

    private fun observeBadges() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                MessageUnreadCenter.badges.collectLatest { badges ->
                    bindBadge(quickReplyBinding, badges.reply)
                    bindBadge(quickLikeBinding, badges.like)
                    bindBadge(quickFansBinding, badges.fans)
                }
            }
        }
    }

    private fun bindBadge(itemBinding: ItemMessageQuickActionBinding, count: Int) {
        if (count <= 0) {
            itemBinding.tvBadge.isVisible = false
            return
        }
        itemBinding.tvBadge.isVisible = true
        itemBinding.tvBadge.text = if (count > 99) {
            getString(R.string.message_badge_max)
        } else {
            count.toString()
        }
    }

    private fun setupInboxList() {
        binding.rvMessages.layoutManager = LinearLayoutManager(requireContext())
        binding.rvMessages.adapter = inboxAdapter
        binding.swipeRefresh.setColorSchemeResources(R.color.bili_pink)
        binding.swipeRefresh.setOnRefreshListener { inboxAdapter.refresh() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    androidx.paging.Pager(
                        config = com.example.bilibili.util.PagingDefaults.videoListConfig(),
                        pagingSourceFactory = { AllMessagePagingSource() },
                    ).flow.collectLatest { pagingData ->
                        inboxAdapter.submitData(pagingData)
                    }
                }
                launch {
                    PagingUiHelper.bindOverlayEmptyState(
                        viewLifecycleOwner,
                        binding.tvEmpty,
                        inboxAdapter,
                    ) { state ->
                        binding.swipeRefresh.isRefreshing = state.refresh is LoadState.Loading
                    }
                }
            }
        }
    }

    private fun refreshUnreadCounts() {
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching { MessageUnreadCenter.refreshFromApi(messageService) }
        }
    }

    private fun markCategoryRead(mode: Int, onSuccess: () -> Unit) {
        viewLifecycleOwner.lifecycleScope.launch {
            val ok = runCatching {
                when (mode) {
                    MessageCategoryActivity.MODE_LIKE -> {
                        JSONObject(messageService.readAll(MessageTypes.LIKE)).isSuccess() &&
                            JSONObject(messageService.readAll(MessageTypes.COLLECTION)).isSuccess()
                    }
                    MessageCategoryActivity.MODE_FANS ->
                        JSONObject(messageService.readAll(MessageTypes.FANS)).isSuccess()
                    else ->
                        JSONObject(messageService.readAll(MessageTypes.COMMENT)).isSuccess()
                }
            }.getOrDefault(false)
            if (ok) {
                onSuccess()
                inboxAdapter.refresh()
            } else {
                ToastUtils.showShort(requireContext(), "操作失败")
            }
        }
    }

    private fun markMessageRead(messageId: Int) {
        if (messageId <= 0) return
        MessageUnreadCenter.markMessageReadLocally(messageId)
        val position = inboxAdapter.snapshot().items.indexOfFirst { it.messageId == messageId }
        if (position >= 0) {
            inboxAdapter.notifyItemChanged(position)
        }
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching { messageService.readMessage(messageId) }
        }
    }

    private fun confirmMarkAllRead() {
        AlertDialog.Builder(requireContext())
            .setMessage(R.string.message_clear_all_confirm)
            .setPositiveButton(android.R.string.ok) { _, _ -> markAllRead() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun markAllRead() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val types = listOf(
                    MessageTypes.LIKE,
                    MessageTypes.COLLECTION,
                    MessageTypes.COMMENT,
                    MessageTypes.FANS,
                )
                val ok = types.all { type ->
                    JSONObject(messageService.readAll(type)).isSuccess()
                }
                if (ok) {
                    MessageUnreadCenter.clearAll()
                    ToastUtils.showShort(requireContext(), getString(R.string.message_clear_done))
                    inboxAdapter.refresh()
                } else {
                    ToastUtils.showShort(requireContext(), "操作失败")
                }
            } catch (e: Exception) {
                ToastUtils.showShort(requireContext(), e.message ?: "操作失败")
            }
        }
    }
}
