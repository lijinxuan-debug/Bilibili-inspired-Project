package com.example.bilibili.ui.personal.contribute

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.paging.LoadState
import com.example.bilibili.databinding.FragmentContributeBinding
import com.example.bilibili.ui.playVideo.PlayVideoActivity
import com.example.bilibili.util.SPUtils
import kotlinx.coroutines.launch

class ContributeFragment : Fragment() {
    private var _binding: FragmentContributeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ContributeViewModel by viewModels()
    private lateinit var adapter: ContributeVideoAdapter

    private var currentOrderType = 0
    private var isFirstLoad = true

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

        val userId = SPUtils.getUserId()
        viewModel.setParams(userId, currentOrderType)
    }

    override fun onResume() {
        super.onResume()
        // 在Fragment可见时刷新数据
        if (!isFirstLoad) {
            adapter.refresh()
        } else {
            isFirstLoad = false
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
        binding.rvVideoList.adapter = adapter
    }

    private fun setupSortClick() {
        binding.New.setOnClickListener { view ->
            val popup = PopupMenu(requireContext(), view)
            popup.menu.add(0, 0, 0, "最新发布")
            popup.menu.add(0, 1, 1, "最多播放")
            popup.menu.add(0, 2, 2, "最多收藏")

            popup.setOnMenuItemClickListener { item ->
                currentOrderType = item.itemId
                binding.NewText.text = item.title
                binding.NewText.setTextColor(Color.parseColor("#FB7299"))
                viewModel.setParams(SPUtils.getUserId(), currentOrderType)
                true
            }
            popup.show()
        }
    }

    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.videoList.collect { pagingData ->
                adapter.submitData(pagingData)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            adapter.loadStateFlow.collect { loadState ->
                binding.swipeRefresh.isRefreshing = loadState.refresh is LoadState.Loading
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