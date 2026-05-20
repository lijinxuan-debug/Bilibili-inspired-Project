package com.example.bilibili.ui.personal.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest

class HomeViewModel : ViewModel() {
    // 当前用户ID
    private val _userId = MutableStateFlow("")
    val userId: StateFlow<String> = _userId

    // 分页数据流，动态响应用户ID变化
    val videoList: Flow<androidx.paging.PagingData<com.example.bilibili.data.model.VideoItem>> =
        _userId.flatMapLatest { userId ->
            Pager(
                config = PagingConfig(
                    pageSize = 20,
                    enablePlaceholders = false,
                    initialLoadSize = 20
                ),
                pagingSourceFactory = { HomeVideoPagingSource(userId) }
            ).flow.cachedIn(viewModelScope)
        }

    /**
     * 设置用户ID并刷新数据
     */
    fun setUserId(userId: String) {
        if (_userId.value != userId) {
            _userId.value = userId
        }
    }
}