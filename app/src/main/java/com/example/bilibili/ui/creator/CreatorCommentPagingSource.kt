package com.example.bilibili.ui.creator

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.example.bilibili.data.api.UcenterService
import com.example.bilibili.data.model.CreatorCommentItem
import com.example.bilibili.util.ApiJson.errorMessage
import com.example.bilibili.util.ApiJson.isSuccess
import com.example.bilibili.util.PagingDefaults
import com.example.bilibili.util.RetrofitClient
import org.json.JSONObject

class CreatorCommentPagingSource(
    private val videoId: String?,
) : PagingSource<Int, CreatorCommentItem>() {

    private val service = RetrofitClient.create(UcenterService::class.java)

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, CreatorCommentItem> {
        return try {
            val page = params.key ?: 1
            val response = JSONObject(service.loadComment(pageNo = page, videoId = videoId))
            if (!response.isSuccess()) {
                return LoadResult.Error(Exception(response.errorMessage()))
            }
            val data = response.getJSONObject("data")
            val listArray = data.getJSONArray("list")
            val list = mutableListOf<CreatorCommentItem>()
            for (i in 0 until listArray.length()) {
                val item = listArray.getJSONObject(i)
                list.add(
                    CreatorCommentItem(
                        commentId = item.optInt("commentId"),
                        content = item.optString("content"),
                        videoId = item.optString("videoId"),
                        videoName = item.optString("videoName", "未知视频"),
                        userId = item.optString("userId"),
                        nickName = item.optString("nickName", "用户"),
                        postTime = item.optString("postTime"),
                        avatar = item.optString("avatar"),
                        videoCover = item.optString("videoCover"),
                    ),
                )
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

    override fun getRefreshKey(state: PagingState<Int, CreatorCommentItem>): Int? =
        state.anchorPosition?.let { anchor ->
            state.closestPageToPosition(anchor)?.prevKey?.plus(1)
                ?: state.closestPageToPosition(anchor)?.nextKey?.minus(1)
        }
}
