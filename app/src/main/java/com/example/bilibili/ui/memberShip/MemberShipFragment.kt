package com.example.bilibili.ui.memberShip

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.paging.LoadState
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.bilibili.R
import com.example.bilibili.data.model.UserFriend
import com.example.bilibili.databinding.FragmentMemberShipBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MemberShipFragment : Fragment() {
    private var _binding: FragmentMemberShipBinding? = null
    private val binding get() = _binding!!

    private val viewModel: FansViewModel by viewModels()
    private lateinit var fansAdapter: FansPagingAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMemberShipBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. 初始化适配器
        fansAdapter = FansPagingAdapter { user ->
            if (user.focusType == 0) {
                showFollowConfirmDialog(user)
            } else {
                showCancelFollowDialog(user)
            }
        }

        // 2. 设置 RecyclerView 和 SwipeRefresh
        binding.rvFans.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = fansAdapter
        }

        binding.swipeRefresh.setColorSchemeColors(android.graphics.Color.parseColor("#FB7299"))
        binding.swipeRefresh.setOnRefreshListener { fansAdapter.refresh() }

        // 3. 监听数据流 (使用 collectLatest 保证及时响应刷新)
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.fansList.collectLatest { pagingData ->
                fansAdapter.submitData(pagingData)
            }
        }

        // 4. 监听加载状态
        viewLifecycleOwner.lifecycleScope.launch {
            fansAdapter.loadStateFlow.collect { loadState ->
                binding.swipeRefresh.isRefreshing = loadState.refresh is LoadState.Loading

                if (loadState.refresh is LoadState.NotLoading) {
                    binding.tvCountNumber.text = "${fansAdapter.itemCount}人"
                }
            }
        }
    }

    /**
     * 关注确认弹窗
     */
    private fun showFollowConfirmDialog(user: UserFriend) {
        AlertDialog.Builder(requireContext(), R.style.PinkDialogTheme)
            .setTitle("关注提示")
            .setMessage("确定要关注 ${user.otherNickName} 吗？")
            .setPositiveButton("确定") { _, _ ->
                // 在协程中按顺序执行：请求 -> 成功 -> 刷新
                viewLifecycleOwner.lifecycleScope.launch {
                    val success = viewModel.followUser(user.otherUserId)
                    if (success) {
                        fansAdapter.refresh()
                    } else {
                        Toast.makeText(context, "关注失败，请重试", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /**
     * 取消关注确认弹窗
     */
    private fun showCancelFollowDialog(user: UserFriend) {
        AlertDialog.Builder(requireContext(), R.style.PinkDialogTheme)
            .setTitle("取消关注")
            .setMessage("确定要取消关注 ${user.otherNickName} 吗？")
            .setPositiveButton("确定") { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    val success = viewModel.cancelFollow(user.otherUserId)
                    if (success) {
                        fansAdapter.refresh()
                    } else {
                        Toast.makeText(context, "操作失败", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        @JvmStatic
        fun newInstance() = MemberShipFragment()
    }
}