package com.example.bilibili.ui.front

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.paging.LoadState
import com.example.bilibili.databinding.FragmentCategoryVideoBinding
import com.example.bilibili.util.PagingUiHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class CategoryVideoFragment : Fragment() {
    private var _binding: FragmentCategoryVideoBinding? = null
    private val binding get() = _binding!!

    private var categoryId: Int = -1
    private val viewModel: CategoryVideoViewModel by lazy {
        ViewModelProvider(this, CategoryVideoViewModelFactory(categoryId))[CategoryVideoViewModel::class.java]
    }

    private lateinit var videoAdapter: VideoAdapter
    private var dataCollectionJob: Job? = null

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
            videoAdapter.refresh()
        }

        // 收集视频列表数据
        collectVideoList()

        // 监听加载状态
        viewLifecycleOwner.lifecycleScope.launch {
            videoAdapter.loadStateFlow.collect { loadState ->
                // 只有在 refresh 状态下才显示下拉刷新
                binding.swipeRefreshLayout.isRefreshing = loadState.refresh is LoadState.Loading

                // 处理空数据显示
                val isEmpty = videoAdapter.itemCount == 0 && loadState.refresh !is LoadState.Loading
                binding.llEmpty.visibility = if (isEmpty) View.VISIBLE else View.GONE
                binding.recyclerView.visibility = if (isEmpty) View.GONE else View.VISIBLE

                // 处理错误情况
                if (loadState.refresh is LoadState.Error) {
                    val error = (loadState.refresh as LoadState.Error).error
                    Log.e("CategoryVideoFragment", "加载失败: ${error.message}")
                    // 确保错误时关闭刷新状态
                    binding.swipeRefreshLayout.isRefreshing = false
                }

                // 调试信息
                Log.d("CategoryVideoFragment", "LoadState: refresh=${loadState.refresh}, append=${loadState.append}, prepend=${loadState.prepend}")
            }
        }
    }

    private fun collectVideoList() {
        // 取消之前的数据收集
        dataCollectionJob?.cancel()
        // 启动新的数据收集
        dataCollectionJob = viewLifecycleOwner.lifecycleScope.launch {
            viewModel.videoList.collect { pagingData ->
                videoAdapter.submitData(pagingData)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        dataCollectionJob?.cancel()
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