package com.example.bilibili.ui.personal.collect

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.example.bilibili.data.api.PostService
import com.example.bilibili.data.model.CollectVideo
import com.example.bilibili.util.RetrofitClient
import org.json.JSONObject

class CollectPagingSource(private val userId: String) : PagingSource<Int, CollectVideo>() {
    private val service = RetrofitClient.create(PostService::class.java)

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, CollectVideo> {
        return try {
            val page = params.key ?: 1
            val response = service.loadUserCollection(userId, pageNo = page)
            val jsonObject = JSONObject(response)

            if (jsonObject.optString("status") == "success") {
                val list = mutableListOf<CollectVideo>()
                val dataObj = jsonObject.getJSONObject("data")
                val jsonArray = dataObj.getJSONArray("list")

                for (i in 0 until jsonArray.length()) {
                    val item = jsonArray.getJSONObject(i)
                    list.add(
                        CollectVideo(
                            actionId = item.getInt("actionId"),
                            videoId = item.getString("videoId"),
                            videoUserId = item.getString("videoUserId"),
                            commentId = item.getInt("commentId"),
                            actionType = item.getInt("actionType"),
                            actionCount = item.getInt("actionCount"),
                            userId = item.getString("userId"),
                            actionTime = item.getString("actionTime"),
                            videoCover = item.getString("videoCover"),
                            videoName = item.getString("videoName")
                        )
                    )
                }

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

    override fun getRefreshKey(state: PagingState<Int, CollectVideo>): Int? {
        return state.anchorPosition?.let { anchorPosition ->
            state.closestPageToPosition(anchorPosition)?.prevKey?.plus(1)
                ?: state.closestPageToPosition(anchorPosition)?.nextKey?.minus(1)
        }
    }
}
