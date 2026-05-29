package com.example.bilibili.ui.focus

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.paging.LoadState
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.bilibili.R
import com.example.bilibili.data.model.UserFriend
import com.example.bilibili.databinding.FragmentFriendListTabBinding
import com.example.bilibili.ui.user.UserProfileActivity
import com.example.bilibili.util.PagingUiHelper
import kotlinx.coroutines.launch

/** 我的好友 - 关注 Tab */
class FollowingListFragment : Fragment() {

    private var _binding: FragmentFriendListTabBinding? = null
    private val binding get() = _binding!!

    private val viewModel: FocusOnViewModel by viewModels()
    private lateinit var adapter: FocusOnPagingAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentFriendListTabBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        updateFollowingSummary(0)

        adapter = FocusOnPagingAdapter(
            onActionClick = { user -> showCancelFollowDialog(user) },
            onUserClick = { user -> openUserProfile(user.otherUserId) },
        )

        binding.rvList.layoutManager = LinearLayoutManager(requireContext())
        binding.rvList.adapter = adapter

        binding.swipeRefresh.setColorSchemeColors(Color.parseColor("#FB7299"))
        binding.swipeRefresh.setOnRefreshListener { adapter.refresh() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.focusList.collect { pagingData ->
                adapter.submitData(pagingData)
            }
        }

        PagingUiHelper.bindEmptyState(
            viewLifecycleOwner,
            binding.emptyState.llEmpty,
            binding.rvList,
            adapter,
        ) { loadState ->
            binding.swipeRefresh.isRefreshing = loadState.refresh is LoadState.Loading
            if (loadState.refresh is LoadState.NotLoading) {
                updateFollowingSummary(adapter.itemCount)
            }
        }
    }

    private fun updateFollowingSummary(count: Int) {
        val label = getString(R.string.friend_summary_following_label)
        val full = getString(R.string.friend_summary_following, count)
        val spannable = SpannableString(full)
        spannable.setSpan(
            ForegroundColorSpan(Color.parseColor("#212121")),
            0,
            label.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
        binding.tvSummary.text = spannable
    }

    private fun openUserProfile(userId: String) {
        if (userId.isEmpty()) return
        startActivity(
            Intent(requireContext(), UserProfileActivity::class.java).apply {
                putExtra("user_id", userId)
            },
        )
    }

    private fun showCancelFollowDialog(user: UserFriend) {
        AlertDialog.Builder(requireContext(), R.style.PinkDialogTheme)
            .setTitle("取消关注")
            .setMessage("确定要取消关注 ${user.otherNickName} 吗？")
            .setPositiveButton("确定") { _, _ ->
                viewModel.cancelFollow(user.otherUserId)
                adapter.refresh()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
