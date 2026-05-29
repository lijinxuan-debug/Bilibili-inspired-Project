package com.example.bilibili.util

import android.view.View
import androidx.paging.CombinedLoadStates
import androidx.paging.LoadState
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

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

    /** 列表无数据且非刷新中时显示空状态（与首页分类空列表一致） */
    fun updateEmptyState(
        emptyView: View,
        listView: View,
        contentAdapter: PagingDataAdapter<*, *>,
        loadState: CombinedLoadStates
    ) {
        val isEmpty = contentAdapter.itemCount == 0 && loadState.refresh !is LoadState.Loading
        emptyView.visibility = if (isEmpty) View.VISIBLE else View.GONE
        listView.visibility = if (isEmpty) View.GONE else View.VISIBLE
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
