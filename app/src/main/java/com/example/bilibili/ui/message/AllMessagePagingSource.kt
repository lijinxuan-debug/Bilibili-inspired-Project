package com.example.bilibili.ui.message

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.example.bilibili.data.api.MessageService
import com.example.bilibili.data.model.UserMessageItem
import com.example.bilibili.util.ApiJson.errorMessage
import com.example.bilibili.util.ApiJson.isSuccess
import com.example.bilibili.util.PagingDefaults
import com.example.bilibili.util.RetrofitClient
import org.json.JSONObject

class AllMessagePagingSource : PagingSource<Int, UserMessageItem>() {

    private val messageService = RetrofitClient.create(MessageService::class.java)

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, UserMessageItem> {
        val page = params.key ?: 1
        return try {
            val response = JSONObject(messageService.loadAllMessage(pageNo = page))
            if (!response.isSuccess()) {
                return LoadResult.Error(Exception(response.errorMessage()))
            }
            val data = response.getJSONObject("data")
            val listArray = data.getJSONArray("list")
            val list = mutableListOf<UserMessageItem>()
            for (i in 0 until listArray.length()) {
                list.add(UserMessageItem.fromJson(listArray.getJSONObject(i)))
            }
            LoadResult.Page(
                data = list,
                prevKey = if (page == 1) null else page - 1,
                nextKey = PagingDefaults.nextPageKey(data, page, list.size),
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, UserMessageItem>): Int? = null
}
