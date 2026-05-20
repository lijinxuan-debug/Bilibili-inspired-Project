package com.example.bilibili.ui.personal.contribute

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest

class ContributeViewModel : ViewModel() {
    // 当前用户ID，初始为空
    private val _userId = MutableStateFlow("")
    val userId: StateFlow<String> = _userId

    // 当前排序类型，初始为0（最新发布）
    private val _orderType = MutableStateFlow(0)
    val orderType: StateFlow<Int> = _orderType

    // 分页数据流，动态响应用户ID和排序类型变化
    val videoList: Flow<androidx.paging.PagingData<com.example.bilibili.data.model.VideoItem>> =
        _userId.flatMapLatest { userId ->
            _orderType.flatMapLatest { orderType ->
                Pager(
                    config = PagingConfig(
                        pageSize = 20,
                        enablePlaceholders = false,
                        initialLoadSize = 20
                    ),
                    pagingSourceFactory = { ContributeVideoPagingSource(userId, orderType) }
                ).flow.cachedIn(viewModelScope)
            }
        }

    /**
     * 设置用户ID和排序类型
     */
    fun setParams(userId: String, orderType: Int = 0) {
        _userId.value = userId
        if (_orderType.value != orderType) {
            _orderType.value = orderType
        }
    }
}