package com.example.bilibili.ui.front

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.cachedIn
import com.example.bilibili.util.PagingDefaults
import kotlinx.coroutines.flow.Flow

class CategoryVideoViewModel(private val categoryId: Int) : ViewModel() {

    // 分页数据流
    val videoList: Flow<androidx.paging.PagingData<com.example.bilibili.data.model.VideoItem>> =
        Pager(
            config = PagingDefaults.videoListConfig(),
            pagingSourceFactory = { FrontPagePagingSource(categoryId) }
        ).flow.cachedIn(viewModelScope)
}