package com.example.bilibili.ui.creator

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.paging.LoadState
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.bilibili.R
import com.example.bilibili.databinding.ActivityCreatorDanmuManageBinding
import com.example.bilibili.databinding.LayoutPopupCreatorVideoFilterBinding
import com.example.bilibili.ui.playVideo.PlayVideoActivity
import com.example.bilibili.ui.user.UserProfileActivity
import com.example.bilibili.util.PagingUiHelper
import com.example.bilibili.util.ToastUtils
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class CreatorDanmuManageActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCreatorDanmuManageBinding
    private val viewModel: CreatorInteractionManageViewModel by viewModels()
    private val adapter = CreatorDanmuAdapter(
        onDelete = { item -> confirmDelete(item.danmuId) },
        onUserClick = { item -> openUserProfile(item.userId) },
        onVideoClick = { item -> openPlayVideo(item.videoId) },
    )
    private var videoFilterPopup: PopupWindow? = null
    private var filterDimView: View? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCreatorDanmuManageBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.ivBack.setOnClickListener { finish() }
        setupList()
        setupVideoFilter()
        observeDanmus()
        viewModel.loadVideoOptions()
    }

    private fun setupVideoFilter() {
        binding.layoutVideoFilter.setOnClickListener {
            if (videoFilterPopup?.isShowing == true) {
                dismissVideoFilterPopup()
            } else {
                showVideoFilterPopup()
            }
        }
        viewModel.selectedVideoOption.observe(this) { option ->
            binding.tvVideoFilter.text = option.title
        }
    }

    private fun showVideoFilterPopup() {
        val options = viewModel.videoOptions.value.orEmpty()
        if (options.isEmpty()) return

        val popupBinding = LayoutPopupCreatorVideoFilterBinding.inflate(layoutInflater)
        val listView = popupBinding.rvVideoOptions
        listView.isVerticalScrollBarEnabled = false
        listView.overScrollMode = View.OVER_SCROLL_NEVER

        val popupAdapter = CreatorVideoFilterPopupAdapter { option ->
            dismissVideoFilterPopup()
            viewModel.selectVideo(option)
            adapter.refresh()
        }
        popupAdapter.submitList(options, viewModel.selectedVideoOption.value?.videoId)
        listView.layoutManager = LinearLayoutManager(this)
        listView.adapter = popupAdapter

        val screenWidth = resources.displayMetrics.widthPixels
        val maxPopupHeight = (resources.displayMetrics.heightPixels * 0.55f).toInt()
        popupBinding.root.measure(
            View.MeasureSpec.makeMeasureSpec(screenWidth, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(maxPopupHeight, View.MeasureSpec.AT_MOST),
        )
        val popupHeight = popupBinding.root.measuredHeight.coerceAtMost(maxPopupHeight)
        if (popupBinding.root.measuredHeight > maxPopupHeight) {
            listView.layoutParams = listView.layoutParams.apply {
                height = maxPopupHeight - listView.paddingTop - listView.paddingBottom
            }
        }

        videoFilterPopup = PopupWindow(
            popupBinding.root,
            ViewGroup.LayoutParams.MATCH_PARENT,
            popupHeight,
            true,
        ).apply {
            isOutsideTouchable = true
            isFocusable = true
            setBackgroundDrawable(ColorDrawable(Color.WHITE))
            setOnDismissListener {
                hideFilterDim()
                updateFilterArrow(expanded = false)
            }
        }

        val location = IntArray(2)
        binding.layoutVideoFilter.getLocationInWindow(location)
        val y = location[1] + binding.layoutVideoFilter.height
        updateFilterArrow(expanded = true)
        showFilterDim()
        videoFilterPopup?.showAtLocation(binding.root, Gravity.TOP or Gravity.START, 0, y)
    }

    private fun showFilterDim() {
        if (filterDimView != null) return
        val container = window.decorView.findViewById<ViewGroup>(android.R.id.content) ?: return
        val dimView = View(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            setBackgroundColor(Color.parseColor("#66000000"))
            alpha = 0f
            isClickable = true
            setOnClickListener { dismissVideoFilterPopup() }
        }
        container.addView(dimView)
        dimView.animate().alpha(1f).setDuration(120L).start()
        filterDimView = dimView
    }

    private fun hideFilterDim() {
        val dimView = filterDimView ?: return
        (dimView.parent as? ViewGroup)?.removeView(dimView)
        filterDimView = null
    }

    private fun dismissVideoFilterPopup() {
        videoFilterPopup?.dismiss()
    }

    private fun updateFilterArrow(expanded: Boolean) {
        binding.ivFilterArrow.animate()
            .rotation(if (expanded) 180f else 0f)
            .setDuration(180L)
            .start()
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

    private fun observeDanmus() {
        lifecycleScope.launch {
            viewModel.danmus.collectLatest { adapter.submitData(it) }
        }
    }

    private fun openUserProfile(userId: String) {
        if (userId.isBlank()) {
            ToastUtils.showShort(this, "用户信息不可用")
            return
        }
        startActivity(
            Intent(this, UserProfileActivity::class.java).apply {
                putExtra("user_id", userId)
            },
        )
    }

    private fun openPlayVideo(videoId: String) {
        if (videoId.isBlank()) {
            ToastUtils.showShort(this, "视频信息不可用")
            return
        }
        startActivity(
            Intent(this, PlayVideoActivity::class.java).apply {
                putExtra("video_id", videoId)
            },
        )
    }

    private fun confirmDelete(danmuId: Int) {
        AlertDialog.Builder(this)
            .setMessage(R.string.creator_delete_danmu_confirm)
            .setPositiveButton(R.string.confirm) { _, _ ->
                viewModel.deleteDanmu(danmuId) { ok, msg ->
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

    override fun onDestroy() {
        dismissVideoFilterPopup()
        hideFilterDim()
        videoFilterPopup = null
        super.onDestroy()
    }
}
