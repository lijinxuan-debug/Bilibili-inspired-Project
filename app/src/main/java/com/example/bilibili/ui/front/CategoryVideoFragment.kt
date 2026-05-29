package com.example.bilibili.ui.front

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.paging.LoadState
import com.example.bilibili.databinding.FragmentCategoryVideoBinding
import com.example.bilibili.util.PagingUiHelper
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class CategoryVideoFragment : Fragment() {
    private var _binding: FragmentCategoryVideoBinding? = null
    private val binding get() = _binding!!

    private var categoryId: Int = -1
    private val viewModel: CategoryVideoViewModel by lazy {
        ViewModelProvider(this, CategoryVideoViewModelFactory(categoryId))[CategoryVideoViewModel::class.java]
    }

    private lateinit var videoAdapter: VideoAdapter
    private var scrollToTopAfterRefresh = false

    companion object {
        private const val ARG_CATEGORY_ID = "category_id"

        fun newInstance(categoryId: Int): CategoryVideoFragment {
            return CategoryVideoFragment().apply {
                arguments = Bundle().apply {
                    putInt(ARG_CATEGORY_ID, categoryId)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        categoryId = arguments?.getInt(ARG_CATEGORY_ID, -1) ?: -1
    }

    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: android.view.ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCategoryVideoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 初始化 RecyclerView 和 Adapter
        videoAdapter = VideoAdapter()
        PagingUiHelper.setupGridWithLoadStateFooter(
            recyclerView = binding.recyclerView,
            spanCount = 2,
            contentAdapter = videoAdapter,
            onRetry = { videoAdapter.retry() }
        )

        // 设置下拉刷新
        binding.swipeRefreshLayout.setColorSchemeColors(android.graphics.Color.parseColor("#FB7299"))
        binding.swipeRefreshLayout.setOnRefreshListener {
            scrollToTopAfterRefresh = true
            videoAdapter.refresh()
        }

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
            binding.recyclerView,
            videoAdapter,
        ) { loadState ->
            binding.swipeRefreshLayout.isRefreshing = loadState.refresh is LoadState.Loading

            if (loadState.refresh is LoadState.Error) {
                val error = (loadState.refresh as LoadState.Error).error
                Log.e("CategoryVideoFragment", "加载失败: ${error.message}")
                binding.swipeRefreshLayout.isRefreshing = false
                scrollToTopAfterRefresh = false
            }

            if (scrollToTopAfterRefresh && loadState.refresh is LoadState.NotLoading) {
                scrollToTopAfterRefresh = false
                binding.recyclerView.post {
                    PagingUiHelper.scrollContentToTop(binding.recyclerView)
                    (parentFragment as? FrontPageFragment)?.expandAppBar()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        scrollToTopAfterRefresh = false
        _binding = null
    }
}

class CategoryVideoViewModelFactory(
    private val categoryId: Int
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CategoryVideoViewModel::class.java)) {
            return CategoryVideoViewModel(categoryId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}