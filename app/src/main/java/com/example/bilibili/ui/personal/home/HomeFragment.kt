package com.example.bilibili.ui.personal.home

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.paging.LoadState
import com.example.bilibili.databinding.FragmentHomeBinding
import com.example.bilibili.ui.playVideo.PlayVideoActivity
import com.example.bilibili.util.SPUtils
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

    private var isFirstLoad = true

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 初始化 Adapter，并实现点击回调
        videoAdapter = HomeVideoAdapter { video ->
            val intent = Intent(requireContext(), PlayVideoActivity::class.java).apply {
                putExtra("video_id", video.videoId)
            }
            startActivity(intent)
        }

        binding.rvHomeVideo.adapter = videoAdapter

        // 设置用户ID
        val userId = SPUtils.getUserId()
        viewModel.setUserId(userId)

        // 设置下拉刷新
        binding.swipeRefresh.setColorSchemeColors(android.graphics.Color.parseColor("#FB7299"))
        binding.swipeRefresh.setOnRefreshListener {
            videoAdapter.refresh()
        }

        // 监听分页数据流
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.videoList.collect { pagingData ->
                videoAdapter.submitData(pagingData)
            }
        }

        // 监听加载状态
        viewLifecycleOwner.lifecycleScope.launch {
            videoAdapter.loadStateFlow.collect { loadState ->
                // 处理下拉刷新状态
                binding.swipeRefresh.isRefreshing = loadState.refresh is LoadState.Loading

                // 可以在这里显示加载状态
                when (loadState.refresh) {
                    is LoadState.Loading -> {
                        // 显示加载中
                    }
                    is LoadState.NotLoading -> {
                        // 加载完成
                    }
                    is LoadState.Error -> {
                        // 显示错误信息
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 在Fragment可见时刷新数据
        if (!isFirstLoad) {
            videoAdapter.refresh()
        } else {
            isFirstLoad = false
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