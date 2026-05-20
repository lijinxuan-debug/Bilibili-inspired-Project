package com.example.bilibili.ui.search

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.bilibili.R
import com.example.bilibili.data.api.VideoService
import com.example.bilibili.data.model.VideoItem
import com.example.bilibili.databinding.ActivitySearchBinding
import com.example.bilibili.ui.playVideo.PlayVideoActivity
import com.example.bilibili.util.RetrofitClient
import com.example.bilibili.util.SPUtils
import com.example.bilibili.util.ToastUtils
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipDrawable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class SearchActivity : AppCompatActivity() {

    // 1. 定义变量
    private lateinit var binding: ActivitySearchBinding

    private var hotWords: Array<String> = arrayOf()

    private lateinit var hotAdapter: HotSearchAdapter

    private lateinit var searchResultAdapter: SearchResultAdapter

    private val videoService = RetrofitClient.create(VideoService::class.java)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 2. 初始化 Binding
        binding = ActivitySearchBinding.inflate(layoutInflater)
        enableEdgeToEdge()
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.llSearchHeader) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            v.setPadding(v.paddingLeft, systemBars.top, v.paddingRight, v.paddingBottom)
            insets
        }

        val received = intent.getStringArrayExtra("hot_words")
        if (received != null) {
            hotWords = received
        }

        hotAdapter = HotSearchAdapter { keyword ->
            // 这里处理点击热搜词的逻辑，比如直接搜索
            binding.etSearch.setText(keyword)
            performSearch(keyword)
        }
        hotAdapter.submitList(hotWords.toList())

        // 搜索结果适配器
        searchResultAdapter = SearchResultAdapter { video ->
            // 这里处理视频点击跳转详情页
            val intent = Intent(this, PlayVideoActivity::class.java).apply {
                // 关键：将 videoId 传递给下一个 Activity
                putExtra("video_id", video.videoId)
            }
            startActivity(intent)
        }

        // 2. 使用数据
        if (hotWords.isNotEmpty()) {
            // 设置搜索框内的热搜词
            binding.etSearch.hint = hotWords[0]
        }

        binding.rvSearchResult.apply {
            layoutManager = LinearLayoutManager(this@SearchActivity)
            adapter = searchResultAdapter
        }

        binding.rvHotSearch.apply {
            layoutManager = GridLayoutManager(this@SearchActivity, 2)
            adapter = hotAdapter
        }

        // 4. 简单的返回键逻辑
        binding.ivBack.setOnClickListener {
            finish()
        }

        // 刷新历史记录
        renderHistoryChips()
        setupDeleteHistoryButton()

        initView()
    }

    private fun performSearch(keyWord: String) {
        if (keyWord.isBlank()) return

        // --- 1. 解除光标和收起软键盘 ---
        binding.etSearch.clearFocus() // 清除焦点，光标消失
        val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.hideSoftInputFromWindow(binding.etSearch.windowToken, 0) // 收起键盘

        // 隐藏历史记录和搜索热词
        binding.nsvSearchSuggestions.visibility = View.GONE
        binding.llHistory.visibility = View.GONE
        binding.llHotSearch.visibility = View.GONE
        // 搜索结果显示
        binding.rvSearchResult.visibility = View.VISIBLE

        // 2. 发起异步请求
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = videoService.search(keyword = keyWord)
                val jsonObject = JSONObject(response)

                // 按照你提供的 JSON 结构解析：data -> list
                val dataObj = jsonObject.getJSONObject("data")
                val listArray = dataObj.getJSONArray("list")

                val searchResults = mutableListOf<VideoItem>()
                for (i in 0 until listArray.length()) {
                    val item = listArray.getJSONObject(i)
                    searchResults.add(VideoItem(
                        videoId = item.getString("videoId"),
                        videoName = item.getString("videoName"),
                        videoCover = item.getString("videoCover"),
                        nickName = item.getString("nickName"),
                        playCount = item.optInt("playCount", 0),
                        danmuCount = item.optInt("danmuCount", 0)
                    ))
                }

                withContext(Dispatchers.Main) {
                    // 提交给适配器
                    searchResultAdapter.submitList(searchResults)

                    // 存入历史并刷新 Chip 显示
                    SPUtils.saveSearchHistory(keyWord)
//                    renderHistoryChips()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    ToastUtils.showShort(this@SearchActivity,"搜索失败")
                }
            }
        }
    }

    private fun renderHistoryChips() {
        val historyList = SPUtils.getSearchHistory()

        if (historyList.isEmpty()) {
            // 没有历史记录直接不显示
            binding.llHistory.visibility = View.GONE
            return
        }

        binding.llHistory.visibility = View.VISIBLE
        binding.cgHistory.removeAllViews()

        for (word in historyList) {
            // 动态创建 Chip
            val chip = Chip(this).apply {
                text = word
                // 应用上面定义的样式
                setChipDrawable(
                    ChipDrawable.createFromAttributes(
                    context, null, 0, R.style.SearchHistoryChipStyle
                ))
                // 解决图中看到的文字内边距
                shapeAppearanceModel = shapeAppearanceModel.toBuilder()
                    .setAllCornerSizes(6f) // 对应 4-6dp
                    .build()

                // 点击历史词触发搜索
                setOnClickListener {
                    binding.etSearch.setText(word)
                    performSearch(word)
                }
            }
            binding.cgHistory.addView(chip)
        }
    }

    private fun setupDeleteHistoryButton() {
        binding.ivDeleteHistory.setOnClickListener {
            showClearHistoryDialog()
        }
    }

    private fun showClearHistoryDialog() {
        MaterialAlertDialogBuilder(this, R.style.PinkMaterialDialogTheme)
            .setMessage("确定要清除所有历史吗？")
            .setPositiveButton("确定") { _, _ ->
                SPUtils.clearSearchHistory()
                binding.cgHistory.removeAllViews()
                binding.llHistory.visibility = View.GONE
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun initView() {
        // 1. 让输入框获取焦点
        binding.etSearch.isFocusable = true
        binding.etSearch.isFocusableInTouchMode = true
        binding.etSearch.requestFocus()

        // 2. 弹出软键盘
        // 使用 postDelayed 是为了确保 View 已经绘制完成，否则键盘可能弹不出来
        binding.etSearch.postDelayed({
            val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.showSoftInput(binding.etSearch, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }, 200) // 延迟 200ms 是经验值，足以应对绝大多数机型

        // 3. 顺便监听键盘上的“搜索”键
        binding.etSearch.setOnEditorActionListener { v, actionId, event ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                performSearch(binding.etSearch.text.toString())
                true
            } else {
                false
            }
        }

        // 4. 搜索按钮点击逻辑
        binding.tvSearchBtn.setOnClickListener {
            performSearch(binding.etSearch.text.toString())
        }
    }
}