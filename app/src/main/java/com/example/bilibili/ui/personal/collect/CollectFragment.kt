package com.example.bilibili.ui.personal.collect

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.paging.LoadState
import com.example.bilibili.databinding.FragmentCollectBinding
import com.example.bilibili.ui.playVideo.PlayVideoActivity
import com.example.bilibili.util.PagingUiHelper
import com.example.bilibili.util.SPUtils
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class CollectFragment : Fragment() {

    private var _binding: FragmentCollectBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CollectViewModel by viewModels()
    private lateinit var collectAdapter: CollectAdapter

    private var currentUserId: String = ""

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCollectBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()

        currentUserId = arguments?.getString("user_id") ?: SPUtils.getUserId()
        viewModel.setUserId(currentUserId)

        // 设置下拉刷新
        binding.swipeRefresh.setColorSchemeColors(android.graphics.Color.parseColor("#FB7299"))
        binding.swipeRefresh.setOnRefreshListener {
            collectAdapter.refresh()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.collectVideos.collectLatest { pagingData ->
                    collectAdapter.submitData(pagingData)
                }
            }
        }

        PagingUiHelper.bindEmptyState(
            viewLifecycleOwner,
            binding.emptyState.llEmpty,
            binding.recyclerView,
            collectAdapter,
        ) { loadState ->
            binding.swipeRefresh.isRefreshing = loadState.refresh is LoadState.Loading
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

    private fun setupRecyclerView() {
        collectAdapter = CollectAdapter { video ->
            val intent = Intent(requireContext(), PlayVideoActivity::class.java).apply {
                putExtra("video_id", video.videoId)
            }
            startActivity(intent)
        }

        PagingUiHelper.setupListWithLoadStateFooter(
            recyclerView = binding.recyclerView,
            contentAdapter = collectAdapter,
            onRetry = { collectAdapter.retry() }
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        @JvmStatic
        fun newInstance() = CollectFragment()
    }
}