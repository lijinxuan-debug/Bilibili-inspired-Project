package com.example.bilibili.ui.personal.home

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.View.GONE
import android.view.View.VISIBLE
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.paging.LoadState
import com.example.bilibili.databinding.FragmentHomeBinding
import com.example.bilibili.ui.playVideo.PlayVideoActivity
import com.example.bilibili.util.PagingUiHelper
import com.example.bilibili.util.SPUtils
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HomeViewModel by viewModels()
    private lateinit var videoAdapter: HomeVideoAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    private var currentUserId: String = ""

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 初始化 Adapter，并实现点击回调
        videoAdapter = HomeVideoAdapter { video ->
            val intent = Intent(requireContext(), PlayVideoActivity::class.java).apply {
                putExtra("video_id", video.videoId)
            }
            startActivity(intent)
        }

        PagingUiHelper.setupGridWithLoadStateFooter(
            recyclerView = binding.rvHomeVideo,
            spanCount = 2,
            contentAdapter = videoAdapter,
            onRetry = { videoAdapter.retry() }
        )

        // 设置用户ID - 优先使用传入的参数，否则使用当前登录用户ID
        currentUserId = arguments?.getString("user_id") ?: SPUtils.getUserId()
        viewModel.setUserId(currentUserId)

        // 查看更多按钮点击事件
        binding.llMore.setOnClickListener {
            // 跳转到个人主页的投稿tab
            val mainActivity = requireActivity()
            if (mainActivity is com.example.bilibili.MainActivity) {
                mainActivity.switchToContributeTab()
            }
        }

        // 设置下拉刷新
        binding.swipeRefresh.setColorSchemeColors(android.graphics.Color.parseColor("#FB7299"))
        binding.swipeRefresh.setOnRefreshListener {
            videoAdapter.refresh()
        }

        // 监听分页数据流（collectLatest 避免刷新时旧数据覆盖新数据）
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.videoList.collectLatest { pagingData ->
                    videoAdapter.submitData(pagingData)
                }
            }
        }

        PagingUiHelper.bindEmptyState(
            viewLifecycleOwner,
            binding.emptyState.llEmpty,
            binding.rvHomeVideo,
            videoAdapter,
        ) { loadState ->
            binding.swipeRefresh.isRefreshing = loadState.refresh is LoadState.Loading
            if (loadState.refresh !is LoadState.Loading) {
                binding.llMore.visibility = if (videoAdapter.itemCount == 0) GONE else VISIBLE
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val newUserId = arguments?.getString("user_id") ?: SPUtils.getUserId()
        if (newUserId != currentUserId) {
            currentUserId = newUserId
            viewModel.setUserId(currentUserId)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        @JvmStatic
        fun newInstance() = HomeFragment()
    }
}