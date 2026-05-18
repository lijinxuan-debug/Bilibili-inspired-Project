package com.example.bilibili.ui.front

import android.content.Intent
import android.os.Bundle
import android.util.TypedValue
import androidx.fragment.app.Fragment
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.bilibili.data.api.CategoryInfoService
import com.example.bilibili.data.api.VideoService
import com.example.bilibili.data.model.CategoryItem
import com.example.bilibili.databinding.FragmentFrontPageBinding
import com.example.bilibili.ui.search.SearchActivity
import com.example.bilibili.util.GlideEngine
import com.example.bilibili.util.RetrofitClient
import com.example.bilibili.util.SPUtils
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.math.abs

class FrontPageFragment : Fragment() {
    private var _binding: FragmentFrontPageBinding? = null
    private val binding get() = _binding!!

    private var hotWordsArray: Array<String> = arrayOf()
    private val categoryService = RetrofitClient.create(CategoryInfoService::class.java)
    private val videoService = RetrofitClient.create(VideoService::class.java)

    // 存储分类列表
    private val categoryList = mutableListOf<CategoryItem>()

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
            // 这样就解决了你说的"双重 Padding"导致高度变厚的问题
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

        // 监听搜索框
        binding.llSearchBar.setOnClickListener {
            val intent = Intent(requireContext(), SearchActivity::class.java)
            intent.putExtra("hot_words", hotWordsArray)
            startActivity(intent)
        }

        // 用户头像点击，跳转到个人主页
        binding.ivAvatar.setOnClickListener {
            // 切换到个人主页Tab
            val mainActivity = requireActivity()
            if (mainActivity is com.example.bilibili.MainActivity) {
                mainActivity.switchToPersonalTab()
            }
        }

        // 加载热门搜索词
        loadHotWords()

        // 加载分类并初始化 ViewPager2
        loadTabsAndInitViewPager()
    }

    private fun initScrollAnimation() {
        binding.appBarLayout.addOnOffsetChangedListener { appBarLayout, verticalOffset ->
            val totalRange = appBarLayout.totalScrollRange
            val fraction = abs(verticalOffset).toFloat() / totalRange

            // 这种计算方式会让搜索栏在滑到一半时就几乎看不见了，
            // 给人一种"在刘海前隐去"的视觉错觉，而不是"钻进刘海"
            binding.top.alpha = (1f - fraction * 1.2f).coerceAtLeast(0f)
        }
    }

    private fun loadHotWords() {
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
    }

    private fun loadTabsAndInitViewPager() {
        lifecycleScope.launch(Dispatchers.Main) {
            try {
                // 加载个人头像
                GlideEngine.loadUserAvatar(requireContext(), SPUtils.getAvatar(), binding.ivAvatar)

                // 加载分类列表
                val response = withContext(Dispatchers.IO) { categoryService.loadAllCategoryInfo() }
                val categories = parseCategoryJson(response)

                // 添加"全部"分类
                categoryList.clear()
                categoryList.add(CategoryItem(-1, "全部"))
                categoryList.addAll(categories)

                // 初始化 ViewPager2
                initViewPager()

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun initViewPager() {
        // 设置 ViewPager2 适配器
        val viewPagerAdapter = CategoryPagerAdapter(this, categoryList)
        binding.viewPager.adapter = viewPagerAdapter

        // 绑定 TabLayout 和 ViewPager2
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = categoryList[position].categoryName
        }.attach()

        // 设置 TabLayout 的模式为可滚动
        binding.tabLayout.tabMode = TabLayout.MODE_SCROLLABLE

        // 设置用户可见页面的缓存数量（优化性能）
        binding.viewPager.offscreenPageLimit = 2
    }

    // ViewPager2 适配器
    class CategoryPagerAdapter(fragment: Fragment, private val categories: List<CategoryItem>) :
        FragmentStateAdapter(fragment) {

        override fun getItemCount(): Int = categories.size

        override fun createFragment(position: Int): Fragment {
            return CategoryVideoFragment.newInstance(categories[position].categoryId)
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
        _binding = null
    }
}