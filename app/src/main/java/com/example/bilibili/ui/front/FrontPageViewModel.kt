package com.example.bilibili.ui.front

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import androidx.paging.map
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest

class FrontPageViewModel : ViewModel() {

    // 当前选中的分类ID，初始为-1（全部）
    private val _currentCategoryId = MutableStateFlow(-1)

    // 分页数据流，动态响应分类变化
    val videoList: Flow<androidx.paging.PagingData<com.example.bilibili.data.model.VideoItem>> =
        _currentCategoryId
            .flatMapLatest { categoryId ->
                Pager(
                    config = PagingConfig(
                        pageSize = 20,
                        enablePlaceholders = false,
                        initialLoadSize = 20
                    ),
                    pagingSourceFactory = { FrontPagePagingSource(categoryId) }
                ).flow
            }
            .cachedIn(viewModelScope)

    // 切换分类
    fun switchCategory(categoryId: Int) {
        if (_currentCategoryId.value != categoryId) {
            _currentCategoryId.value = categoryId
        }
    }
}