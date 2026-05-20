package com.example.bilibili.util

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
        onRetry: () -> Unit
    ) {
        val footerAdapter = LoadStateAdapter(onRetry)
        recyclerView.layoutManager = LinearLayoutManager(recyclerView.context)
        recyclerView.adapter = contentAdapter.withLoadStateFooter(footerAdapter)
    }
}
