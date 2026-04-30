package com.example.bilibili.ui.focus

import android.os.Bundle
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
import com.example.bilibili.databinding.FragmentFocusOnBinding
import kotlinx.coroutines.launch

class FocusOnFragment : Fragment() {
    private var _binding: FragmentFocusOnBinding? = null
    private val binding get() = _binding!!

    private val viewModel: FocusOnViewModel by viewModels()
    private lateinit var focusAdapter: FocusOnPagingAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentFocusOnBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. 初始化适配器
        focusAdapter = FocusOnPagingAdapter { user ->
            // 在关注页面中，所有用户都是已关注的
            // 点击按钮直接显示取消关注确认弹窗
            showCancelFollowDialog(user)
        }

        // 2. 设置 RecyclerView
        binding.rvFriends.layoutManager = LinearLayoutManager(requireContext())
        binding.rvFriends.adapter = focusAdapter

        // 3. 监听数据流
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.focusList.collect { pagingData ->
                focusAdapter.submitData(pagingData)
            }
        }

        // 4. 设置下拉刷新
        binding.swipeRefresh.setColorSchemeColors(android.graphics.Color.parseColor("#FB7299"))
        binding.swipeRefresh.setOnRefreshListener {
            focusAdapter.refresh()
        }

        // 5. 监听加载状态
        viewLifecycleOwner.lifecycleScope.launch {
            focusAdapter.loadStateFlow.collect { loadState ->
                // 处理下拉刷新状态
                binding.swipeRefresh.isRefreshing = loadState.refresh is LoadState.Loading

                // 可以在这里显示加载状态
                when (loadState.refresh) {
                    is LoadState.Loading -> {
                        // 显示加载中
                    }
                    is LoadState.NotLoading -> {
                        // 加载完成
                        // 更新顶部人数显示（注意：Paging3的总数可能不准确）
                        binding.tvCountNumber.text = "${focusAdapter.itemCount}人"
                    }
                    is LoadState.Error -> {
                        // 显示错误信息
                    }
                }
            }
        }
    }

    /**
     * 显示取消关注确认弹窗
     */
    private fun showCancelFollowDialog(user: UserFriend) {
        AlertDialog.Builder(requireContext(), R.style.PinkDialogTheme)
            .setTitle("取消关注")
            .setMessage("确定要取消关注 ${user.otherNickName} 吗？")
            .setPositiveButton("确定") { _, _ ->
                viewModel.cancelFollow(user.otherUserId)
                // 注意：Paging3中需要手动刷新数据
                focusAdapter.refresh()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}