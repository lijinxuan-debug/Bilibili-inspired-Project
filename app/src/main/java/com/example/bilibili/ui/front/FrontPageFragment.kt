package com.example.bilibili.ui.front

import android.content.Intent
import android.os.Bundle
import android.util.TypedValue
import androidx.fragment.app.Fragment
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.paging.LoadState
import androidx.recyclerview.widget.GridLayoutManager
import com.example.bilibili.data.api.CategoryInfoService
import com.example.bilibili.data.api.VideoService
import com.example.bilibili.data.model.CategoryItem
import com.example.bilibili.databinding.FragmentFrontPageBinding
import com.example.bilibili.ui.search.SearchActivity
import com.example.bilibili.util.GlideEngine
import com.example.bilibili.util.RetrofitClient
import com.example.bilibili.util.SPUtils
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.math.abs

class FrontPageFragment : Fragment() {
    private var _binding: FragmentFrontPageBinding? = null
    private val binding get() = _binding!!

    private val viewModel: FrontPageViewModel by viewModels()
    private lateinit var videoAdapter: VideoAdapter

    private var hotWordsArray: Array<String> = arrayOf()

    private val categoryService = RetrofitClient.create(CategoryInfoService::class.java)

    private val videoService = RetrofitClient.create(VideoService::class.java)

    private var dataCollectionJob: Job? = null

    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: android.view.ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFrontPageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 适配水滴屏和刘海屏
        ViewCompat.setOnApplyWindowInsetsListener(binding.appBarLayout) { _, insets ->
            val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top

            // 1. 给 AppBarLayout 整体加一个顶部内边距，就像给整个顶棚加了厚垫子
            // 这样无论里面的 top 怎么滑，它最高只能滑到状态栏底部，不会钻进去
            binding.appBarLayout.setPadding(0, statusBarHeight, 0, 0)

            // 2. 既然最外层已经加了 Padding，内部的 top 和 tabContainer 就不需要单独加顶部 Padding 了
            // 这样就解决了你说的“双重 Padding”导致高度变厚的问题
            binding.top.setPadding(binding.top.paddingLeft, 0, binding.top.paddingRight, 0)
            binding.container.setPadding(0, 0, 0, 0)

            // 3. 设置刹车：折叠后保留 TabLayout 的高度即可
            val tabHeightPx = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 44f, resources.displayMetrics
            ).toInt()
            binding.appBarLayout.minimumHeight = tabHeightPx

            insets
        }

        // 动画监听
        initScrollAnimation()

        // 1. 初始化 RecyclerView 和 Adapter
        videoAdapter = VideoAdapter()
        binding.recyclerView.apply {
            layoutManager = GridLayoutManager(requireContext(), 2)
            adapter = videoAdapter
        }

        // 2. 设置下拉刷新
        binding.swipeRefreshLayout.setColorSchemeColors(android.graphics.Color.parseColor("#FB7299"))
        binding.swipeRefreshLayout.setOnRefreshListener {
            videoAdapter.refresh()
        }

        // 监听搜索框
        binding.llSearchBar.setOnClickListener {
            val intent = Intent(requireContext(), SearchActivity::class.java)
            intent.putExtra("hot_words", hotWordsArray)
            startActivity(intent)
        }

        // 3. 监听分页数据流
        collectVideoList()

        // 任务 1：监听加载状态 (它会一直运行)
        viewLifecycleOwner.lifecycleScope.launch {
            videoAdapter.loadStateFlow.collect { loadState ->
                binding.swipeRefreshLayout.isRefreshing = loadState.refresh is LoadState.Loading
                val isEmpty = videoAdapter.itemCount == 0 && loadState.refresh !is LoadState.Loading
                binding.llEmpty.visibility = if (isEmpty) View.VISIBLE else View.GONE
                binding.recyclerView.visibility = if (isEmpty) View.GONE else View.VISIBLE
            }
        }

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val hotWordTop = videoService.getHotWordTop()
                val data = JSONObject(hotWordTop).getJSONArray("data")
                val hotWords = Array(data.length()) { i -> data.getString(i) }

                // 记得在这里初始化你的 hotWordsArray
                hotWordsArray = hotWords

                withContext(Dispatchers.Main) {
                    if (hotWordsArray.isNotEmpty()) {
                        binding.hotWord.text = hotWordsArray[0]
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 加载分类
        loadTabs()
    }

    private fun initScrollAnimation() {
        binding.appBarLayout.addOnOffsetChangedListener { appBarLayout, verticalOffset ->
            val totalRange = appBarLayout.totalScrollRange
            val fraction = abs(verticalOffset).toFloat() / totalRange

            // 这种计算方式会让搜索栏在滑到一半时就几乎看不见了，
            // 给人一种“在刘海前隐去”的视觉错觉，而不是“钻进刘海”
            binding.top.alpha = (1f - fraction * 1.2f).coerceAtLeast(0f)

            // 同时处理右侧小搜索图标
            if (fraction > 0.7f) {
                binding.ivSmallSearch.alpha = (fraction - 0.7f) * 3.3f
                binding.ivSmallSearch.visibility = View.VISIBLE
            } else {
                binding.ivSmallSearch.alpha = 0f
                binding.ivSmallSearch.visibility = View.GONE
            }
        }
    }

    private fun loadTabs() {
        lifecycleScope.launch(Dispatchers.Main) {
            try {
                // 1. 首先添加"全部"tab（不选中）
                val allTab = binding.tabLayout.newTab().apply {
                    text = "全部"
                    tag = -1 // 使用-1表示全部
                }
                binding.tabLayout.addTab(allTab)

                // 加载个人头像
                GlideEngine.loadUserAvatar(requireContext(), SPUtils.getAvatar(), binding.ivAvatar)

                // 2. 加载分类列表
                val response = withContext(Dispatchers.IO) { categoryService.loadAllCategoryInfo() }
                val categories = parseCategoryJson(response)

                // 3. 添加其他分类tab
                categories.forEach { category ->
                    val tab = binding.tabLayout.newTab().apply {
                        text = category.categoryName
                        tag = category.categoryId
                    }
                    binding.tabLayout.addTab(tab)
                }

                // 4. 设置tab选择监听
                binding.tabLayout.addOnTabSelectedListener(object :
                    TabLayout.OnTabSelectedListener {
                    override fun onTabSelected(tab: TabLayout.Tab?) {
                        val categoryId = tab?.tag as? Int ?: -1
                        viewModel.switchCategory(categoryId)
                        // 重新收集数据流
                        collectVideoList()
                    }

                    override fun onTabUnselected(tab: TabLayout.Tab?) {}
                    override fun onTabReselected(tab: TabLayout.Tab?) {
                        videoAdapter.refresh() // 重新点击时刷新
                    }
                })

                // 5. 手动选中第一个tab（全部）
                binding.tabLayout.selectTab(binding.tabLayout.getTabAt(0))

            } catch (e: Exception) {
                e.printStackTrace()
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

    // 手动解析分类 JSON
    private fun parseCategoryJson(json: String): List<CategoryItem> {
        val list = mutableListOf<CategoryItem>()
        val dataArray = JSONObject(json).getJSONArray("data")
        for (i in 0 until dataArray.length()) {
            val item = dataArray.getJSONObject(i)
            list.add(
                CategoryItem(
                    categoryId = item.getInt("categoryId"),
                    categoryName = item.getString("categoryName")
                )
            )
        }
        return list
    }

    override fun onDestroyView() {
        super.onDestroyView()
        dataCollectionJob?.cancel()
        _binding = null
    }
}