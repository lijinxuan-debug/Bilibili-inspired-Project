package com.example.bilibili.ui.personal.contribute

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.paging.LoadState
import com.example.bilibili.databinding.FragmentContributeBinding
import com.example.bilibili.ui.playVideo.PlayVideoActivity
import com.example.bilibili.util.PagingUiHelper
import com.example.bilibili.util.SPUtils
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ContributeFragment : Fragment() {
    private var _binding: FragmentContributeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ContributeViewModel by viewModels()
    private lateinit var adapter: ContributeVideoAdapter

    private var currentOrderType = 0
    private var currentUserId: String = ""
    private var shouldScrollToTop = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentContributeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 现在这些函数在类里了，可以被正常调用
        setupRecyclerView()
        setupSwipeRefresh()
        setupSortClick()
        observeData()

        // 设置用户ID - 优先使用传入的参数，否则使用当前登录用户ID
        currentUserId = arguments?.getString("user_id") ?: SPUtils.getUserId()
        viewModel.setParams(currentUserId, currentOrderType)
    }

    override fun onResume() {
        super.onResume()
        val newUserId = arguments?.getString("user_id") ?: SPUtils.getUserId()
        if (newUserId != currentUserId) {
            currentUserId = newUserId
            viewModel.setParams(currentUserId, currentOrderType)
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setColorSchemeColors(Color.parseColor("#FB7299"))
        binding.swipeRefresh.setOnRefreshListener {
            adapter.refresh()
        }
    } // <--- 这里之前漏掉了反括号，导致后面的函数全掉进来了

    private fun setupRecyclerView() {
        adapter = ContributeVideoAdapter { video ->
            val intent = Intent(requireContext(), PlayVideoActivity::class.java).apply {
                putExtra("video_id", video.videoId)
            }
            startActivity(intent)
        }
        PagingUiHelper.setupListWithLoadStateFooter(
            recyclerView = binding.rvVideoList,
            contentAdapter = adapter,
            onRetry = { adapter.retry() }
        )
    }

    private fun setupSortClick() {
        binding.New.setOnClickListener {
            // 循环切换排序状态: 0 -> 1 -> 2 -> 0
            currentOrderType = (currentOrderType + 1) % 3

            // 更新文本，保持灰色
            when (currentOrderType) {
                0 -> binding.NewText.text = "最新发布"
                1 -> binding.NewText.text = "最多播放"
                2 -> binding.NewText.text = "最多收藏"
            }

            viewModel.setParams(currentUserId, currentOrderType)
            shouldScrollToTop = true
        }
    }

    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.videoList.collectLatest { pagingData ->
                    adapter.submitData(pagingData)
                }
            }
        }

        PagingUiHelper.bindEmptyState(
            viewLifecycleOwner,
            binding.emptyState.llEmpty,
            binding.rvVideoList,
            adapter,
        ) { loadState ->
            binding.swipeRefresh.isRefreshing = loadState.refresh is LoadState.Loading
            if (loadState.refresh is LoadState.NotLoading && shouldScrollToTop) {
                binding.rvVideoList.scrollToPosition(0)
                shouldScrollToTop = false
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        @JvmStatic
        fun newInstance() = ContributeFragment()
    }
}