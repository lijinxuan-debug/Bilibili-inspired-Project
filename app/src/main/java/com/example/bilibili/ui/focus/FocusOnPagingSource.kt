package com.example.bilibili.ui.focus

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.example.bilibili.data.api.PostService
import com.example.bilibili.data.model.UserFriend
import com.example.bilibili.util.RetrofitClient
import org.json.JSONObject

class FocusOnPagingSource : PagingSource<Int, UserFriend>() {
    private val service = RetrofitClient.create(PostService::class.java)

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, UserFriend> {
        return try {
            // 获取页码，默认从第1页开始
            val page = params.key ?: 1

            // 调用接口获取数据
            val response = service.loadFocusList(page)
            val jsonObject = JSONObject(response)

            if (jsonObject.optString("status") == "success") {
                val dataObj = jsonObject.getJSONObject("data")
                val jsonArray = dataObj.getJSONArray("list")
                val list = mutableListOf<UserFriend>()

                for (i in 0 until jsonArray.length()) {
                    val item = jsonArray.getJSONObject(i)
                    list.add(UserFriend(
                        userId = item.optString("userId"),
                        otherUserId = item.optString("otherUserId"),
                        otherNickName = item.optString("otherNickName", "未知用户"),
                        otherAvatar = item.optString("otherAvatar"),
                        otherPersonalIntroduction = item.optString("otherPersonalIntroduction"),
                        focusType = item.optInt("focusType"),
                        focusTime = item.optString("focusTime")
                    ))
                }

                // 判断是否还有下一页
                val hasMore = list.size >= params.loadSize
                val nextPage = if (hasMore) page + 1 else null

                LoadResult.Page(
                    data = list,
                    prevKey = if (page == 1) null else page - 1,
                    nextKey = nextPage
                )
            } else {
                LoadResult.Error(Exception(jsonObject.optString("message", "加载失败")))
            }
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, UserFriend>): Int? {
        // 返回刷新时的页码
        return state.anchorPosition?.let { anchorPosition ->
            state.closestPageToPosition(anchorPosition)?.prevKey?.plus(1)
                ?: state.closestPageToPosition(anchorPosition)?.nextKey?.minus(1)
        }
    }
}