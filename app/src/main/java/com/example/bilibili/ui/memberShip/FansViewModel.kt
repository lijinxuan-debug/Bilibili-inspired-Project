package com.example.bilibili.ui.memberShip

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import com.example.bilibili.data.api.PostService
import com.example.bilibili.util.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FansViewModel : ViewModel() {
    private val service = RetrofitClient.create(PostService::class.java)

    // 分页数据流
    val fansList = Pager(
        config = PagingConfig(
            pageSize = 20,
            enablePlaceholders = false,
            initialLoadSize = 20
        ),
        pagingSourceFactory = { FansPagingSource() }
    ).flow.cachedIn(viewModelScope)

    // 关注：改为 suspend 挂起函数，返回操作结果
    suspend fun followUser(userId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val response = service.focus(userId)
            // 根据你的接口返回判断，这里假设不抛异常即成功
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // 取消关注：改为 suspend 挂起函数
    suspend fun cancelFollow(userId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            service.cancelFocus(userId)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}