package com.example.bilibili.ui.personal.collect

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest

class CollectViewModel : ViewModel() {
    // 当前用户ID
    private val _userId = MutableStateFlow("")
    val userId: StateFlow<String> = _userId

    // 分页数据流，动态响应用户ID变化
    val collectVideos: Flow<androidx.paging.PagingData<com.example.bilibili.data.model.CollectVideo>> =
        _userId.flatMapLatest { userId ->
            Pager(
                config = PagingConfig(
                    pageSize = 20,
                    enablePlaceholders = false,
                    initialLoadSize = 20
                ),
                pagingSourceFactory = { CollectPagingSource(userId) }
            ).flow.cachedIn(viewModelScope)
        }

    /**
     * 设置用户ID
     */
    fun setUserId(userId: String) {
        if (_userId.value != userId) {
            _userId.value = userId
        }
    }
}