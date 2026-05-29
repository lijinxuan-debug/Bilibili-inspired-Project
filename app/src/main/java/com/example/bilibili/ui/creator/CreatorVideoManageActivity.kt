package com.example.bilibili.ui.creator

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.paging.LoadState
import com.example.bilibili.R
import com.example.bilibili.databinding.ActivityCreatorVideoManageBinding
import com.example.bilibili.ui.releaseVideo.ReleaseVideoActivity
import com.example.bilibili.util.PagingUiHelper
import com.example.bilibili.util.ToastUtils
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class CreatorVideoManageActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCreatorVideoManageBinding
    private val viewModel: CreatorVideoManageViewModel by viewModels()
    private val adapter = CreatorVideoPostAdapter(
        onEdit = { item -> openEditVideo(item.videoId) },
        onDelete = { item -> confirmDelete(item.videoId, item.videoName) },
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCreatorVideoManageBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.ivBack.setOnClickListener { finish() }
        setupSearch()
        setupList()
        observeVideos()
    }

    private fun setupSearch() {
        binding.etSearch.doAfterTextChanged { text ->
            viewModel.setSearchKeyword(text?.toString().orEmpty())
        }
    }

    private fun setupList() {
        PagingUiHelper.setupListWithLoadStateFooter(
            recyclerView = binding.rvList,
            contentAdapter = adapter,
            onRetry = { adapter.retry() },
            endMessage = getString(R.string.creator_list_end_hint),
            showEndOnlyWhenHasItems = true,
        )
        binding.swipeRefresh.setColorSchemeResources(R.color.bili_pink)
        binding.swipeRefresh.setOnRefreshListener { adapter.refresh() }
        adapter.addLoadStateListener { state ->
            binding.swipeRefresh.isRefreshing = state.refresh is LoadState.Loading
            binding.progress.isVisible = state.refresh is LoadState.Loading && adapter.itemCount == 0
            binding.tvEmpty.isVisible = state.refresh is LoadState.NotLoading && adapter.itemCount == 0
        }
    }

    private fun observeVideos() {
        lifecycleScope.launch {
            viewModel.videos.collectLatest { adapter.submitData(it) }
        }
    }

    private fun openEditVideo(videoId: String) {
        startActivity(
            Intent(this, ReleaseVideoActivity::class.java).apply {
                putExtra(ReleaseVideoActivity.EXTRA_VIDEO_ID, videoId)
            },
        )
    }

    private fun confirmDelete(videoId: String, title: String) {
        AlertDialog.Builder(this)
            .setMessage(getString(R.string.creator_delete_video_confirm) + "\n$title")
            .setPositiveButton(R.string.confirm) { _, _ ->
                viewModel.deleteVideo(videoId) { ok, msg ->
                    if (ok) {
                        ToastUtils.showShort(this, "已删除")
                        adapter.refresh()
                    } else {
                        ToastUtils.showShort(this, msg ?: "删除失败")
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
