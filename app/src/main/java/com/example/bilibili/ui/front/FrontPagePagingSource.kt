package com.example.bilibili.ui.front

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.example.bilibili.data.api.VideoService
import com.example.bilibili.data.model.VideoItem
import com.example.bilibili.util.PagingDefaults
import com.example.bilibili.util.RetrofitClient
import org.json.JSONObject

class FrontPagePagingSource(private val pCategoryId: Int = 0) : PagingSource<Int, VideoItem>() {

    private val service = RetrofitClient.create(VideoService::class.java)

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, VideoItem> {
        return try {
            val page = params.key ?: 1

            val response = service.loadVideo(
                pageNo = page,
                pCategoryId = if (pCategoryId == -1) null else pCategoryId
            )
            val jsonObject = JSONObject(response)

            if (jsonObject.optInt("code") == 200) {
                val list = mutableListOf<VideoItem>()
                val dataObject = jsonObject.getJSONObject("data")
                val dataArray = dataObject.optJSONArray("list")

                if (dataArray != null && dataArray.length() > 0) {
                    for (i in 0 until dataArray.length()) {
                        val item = dataArray.getJSONObject(i)
                        list.add(
                            VideoItem(
                                videoId = item.optString("videoId"),
                                videoName = item.optString("videoName"),
                                videoCover = item.optString("videoCover"),
                                userId = item.optString("userId"),
                                avatar = item.optString("avatar"),
                                playCount = item.optInt("playCount", 0),
                                commentCount = item.optInt("commentCount", 0),
                                duration = item.optInt("duration", 0),
                                createTime = item.optString("createTime"),
                                danmuCount = item.optInt("danmuCount", 0),
                                nickName = item.optString("nickName")
                            )
                        )
                    }
                }

                // 使用后端分页元数据判断下一页（勿用 list.size >= loadSize，后端固定每页 15 条）
                val nextKey = PagingDefaults.nextPageKey(dataObject, page)

                LoadResult.Page(
                    data = list,
                    prevKey = if (page == 1) null else page - 1,
                    nextKey = nextKey
                )
            } else {
                val errorMsg = jsonObject.optString("message", "加载失败")
                LoadResult.Error(Exception("API错误: $errorMsg (code: ${jsonObject.optInt("code")})"))
            }
        } catch (e: Exception) {
            LoadResult.Error(Exception("网络异常: ${e.message}"))
        }
    }

    override fun getRefreshKey(state: PagingState<Int, VideoItem>): Int? {
        // 返回刷新时的页码
        return state.anchorPosition?.let { anchorPosition ->
            state.closestPageToPosition(anchorPosition)?.prevKey?.plus(1)
                ?: state.closestPageToPosition(anchorPosition)?.nextKey?.minus(1)
        }
    }
}