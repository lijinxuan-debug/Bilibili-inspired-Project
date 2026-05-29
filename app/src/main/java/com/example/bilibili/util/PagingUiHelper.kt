package com.example.bilibili.util

import android.view.View
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.paging.CombinedLoadStates
import androidx.paging.LoadState
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch

object PagingUiHelper {

    /**
     * 双列网格 + 分页 footer；footer（加载中 / 没有更多了）独占一整行
     */
    fun setupGridWithLoadStateFooter(
        recyclerView: RecyclerView,
        spanCount: Int,
        contentAdapter: PagingDataAdapter<*, *>,
        onRetry: () -> Unit
    ) {
        val footerAdapter = LoadStateAdapter(onRetry)
        val concatAdapter = contentAdapter.withLoadStateFooter(footerAdapter)
        val gridManager = GridLayoutManager(recyclerView.context, spanCount)
        gridManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                return if (position >= contentAdapter.itemCount) spanCount else 1
            }
        }
        recyclerView.layoutManager = gridManager
        recyclerView.adapter = concatAdapter
    }

    /**
     * 单列列表 + 分页 footer
     */
    fun setupListWithLoadStateFooter(
        recyclerView: RecyclerView,
        contentAdapter: PagingDataAdapter<*, *>,
        onRetry: () -> Unit,
        endMessage: String? = null,
        showEndOnlyWhenHasItems: Boolean = false,
    ) {
        val footerAdapter = LoadStateAdapter(
            retry = onRetry,
            endMessage = endMessage,
            showEndOnlyWhenHasItems = showEndOnlyWhenHasItems,
            itemCountProvider = { contentAdapter.itemCount },
        )
        recyclerView.layoutManager = LinearLayoutManager(recyclerView.context)
        recyclerView.adapter = contentAdapter.withLoadStateFooter(footerAdapter)
    }

    /** 刷新完成且确实无数据时才视为空态 */
    fun isStableEmpty(
        contentAdapter: PagingDataAdapter<*, *>,
        loadState: CombinedLoadStates,
    ): Boolean = loadState.refresh is LoadState.NotLoading && contentAdapter.itemCount == 0

    /**
     * 列表无数据且刷新已结束时显示空状态。
     * 刷新进行中不切换显隐，避免 empty ↔ list 闪烁。
     */
    fun updateEmptyState(
        emptyView: View,
        listView: View,
        contentAdapter: PagingDataAdapter<*, *>,
        loadState: CombinedLoadStates
    ) {
        if (loadState.refresh is LoadState.Loading) return

        val isEmpty = isStableEmpty(contentAdapter, loadState)
        emptyView.visibility = if (isEmpty) View.VISIBLE else View.GONE
        listView.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    /**
     * 绑定空态与列表显隐：同时监听 LoadState 与分页快照更新，
     * 避免 refresh 结束到 submitData 落地之间的空窗闪烁。
     */
    fun bindEmptyState(
        lifecycleOwner: LifecycleOwner,
        emptyView: View,
        listView: View,
        contentAdapter: PagingDataAdapter<*, *>,
        onLoadState: ((CombinedLoadStates) -> Unit)? = null,
    ) {
        var latestLoadState: CombinedLoadStates? = null

        val apply: (CombinedLoadStates) -> Unit = { loadState ->
            latestLoadState = loadState
            updateEmptyState(emptyView, listView, contentAdapter, loadState)
            onLoadState?.invoke(loadState)
        }

        contentAdapter.addOnPagesUpdatedListener {
            val state = latestLoadState ?: return@addOnPagesUpdatedListener
            if (state.refresh !is LoadState.Loading) {
                apply(state)
            }
        }

        lifecycleOwner.lifecycleScope.launch {
            contentAdapter.loadStateFlow.collect { loadState ->
                if (loadState.refresh is LoadState.Loading) {
                    latestLoadState = loadState
                    onLoadState?.invoke(loadState)
                } else {
                    apply(loadState)
                }
            }
        }
    }

    /**
     * 消息页等「空提示叠在列表上」的场景，刷新中保持当前空提示显隐不变。
     */
    fun bindOverlayEmptyState(
        lifecycleOwner: LifecycleOwner,
        emptyView: View,
        contentAdapter: PagingDataAdapter<*, *>,
        onLoadState: ((CombinedLoadStates) -> Unit)? = null,
    ) {
        var latestLoadState: CombinedLoadStates? = null

        val apply: (CombinedLoadStates) -> Unit = { loadState ->
            latestLoadState = loadState
            if (loadState.refresh !is LoadState.Loading) {
                emptyView.isVisible = isStableEmpty(contentAdapter, loadState)
            }
            onLoadState?.invoke(loadState)
        }

        contentAdapter.addOnPagesUpdatedListener {
            val state = latestLoadState ?: return@addOnPagesUpdatedListener
            if (state.refresh !is LoadState.Loading) {
                apply(state)
            }
        }

        lifecycleOwner.lifecycleScope.launch {
            contentAdapter.loadStateFlow.collect { loadState ->
                if (loadState.refresh is LoadState.Loading) {
                    latestLoadState = loadState
                    onLoadState?.invoke(loadState)
                } else {
                    apply(loadState)
                }
            }
        }
    }

    /** 刷新完成后将列表滚回顶部，避免数据已重置但视口仍停在原位置 */
    fun scrollContentToTop(recyclerView: RecyclerView) {
        when (val lm = recyclerView.layoutManager) {
            is GridLayoutManager -> lm.scrollToPositionWithOffset(0, 0)
            is LinearLayoutManager -> lm.scrollToPositionWithOffset(0, 0)
            else -> recyclerView.scrollToPosition(0)
        }
    }
}
